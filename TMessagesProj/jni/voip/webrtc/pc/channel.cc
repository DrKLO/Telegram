/*
 *  Copyright 2004 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/channel.h"

#include <iterator>
#include <utility>

#include "absl/algorithm/container.h"
#include "absl/memory/memory.h"
#include "api/call/audio_sink.h"
#include "media/base/media_constants.h"
#include "media/base/rtp_utils.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "p2p/base/packet_transport_internal.h"
#include "pc/channel_manager.h"
#include "pc/rtp_media_utils.h"
#include "rtc_base/bind.h"
#include "rtc_base/byte_order.h"
#include "rtc_base/checks.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/dscp.h"
#include "rtc_base/logging.h"
#include "rtc_base/network_route.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/synchronization/sequence_checker.h"
#include "rtc_base/trace_event.h"

namespace cricket {
using rtc::Bind;
using rtc::UniqueRandomIdGenerator;
using webrtc::SdpType;

namespace {

struct SendPacketMessageData : public rtc::MessageData {
  rtc::CopyOnWriteBuffer packet;
  rtc::PacketOptions options;
};

// Finds a stream based on target's Primary SSRC or RIDs.
// This struct is used in BaseChannel::UpdateLocalStreams_w.
struct StreamFinder {
  explicit StreamFinder(const StreamParams* target) : target_(target) {
    RTC_DCHECK(target);
  }

  bool operator()(const StreamParams& sp) const {
    if (target_->has_ssrcs() && sp.has_ssrcs()) {
      return sp.has_ssrc(target_->first_ssrc());
    }

    if (!target_->has_rids() && !sp.has_rids()) {
      return false;
    }

    const std::vector<RidDescription>& target_rids = target_->rids();
    const std::vector<RidDescription>& source_rids = sp.rids();
    if (source_rids.size() != target_rids.size()) {
      return false;
    }

    // Check that all RIDs match.
    return std::equal(source_rids.begin(), source_rids.end(),
                      target_rids.begin(),
                      [](const RidDescription& lhs, const RidDescription& rhs) {
                        return lhs.rid == rhs.rid;
                      });
  }

  const StreamParams* target_;
};

}  // namespace

enum {
  MSG_SEND_RTP_PACKET = 1,
  MSG_SEND_RTCP_PACKET,
  MSG_READYTOSENDDATA,
  MSG_DATARECEIVED,
  MSG_FIRSTPACKETRECEIVED,
};

static void SafeSetError(const std::string& message, std::string* error_desc) {
  if (error_desc) {
    *error_desc = message;
  }
}

template <class Codec>
void RtpParametersFromMediaDescription(
    const MediaContentDescriptionImpl<Codec>* desc,
    const RtpHeaderExtensions& extensions,
    bool is_stream_active,
    RtpParameters<Codec>* params) {
  params->is_stream_active = is_stream_active;
  params->codecs = desc->codecs();
  // TODO(bugs.webrtc.org/11513): See if we really need
  // rtp_header_extensions_set() and remove it if we don't.
  if (desc->rtp_header_extensions_set()) {
    params->extensions = extensions;
  }
  params->rtcp.reduced_size = desc->rtcp_reduced_size();
  params->rtcp.remote_estimate = desc->remote_estimate();
}

template <class Codec>
void RtpSendParametersFromMediaDescription(
    const MediaContentDescriptionImpl<Codec>* desc,
    const RtpHeaderExtensions& extensions,
    bool is_stream_active,
    RtpSendParameters<Codec>* send_params) {
  RtpParametersFromMediaDescription(desc, extensions, is_stream_active,
                                    send_params);
  send_params->max_bandwidth_bps = desc->bandwidth();
  send_params->extmap_allow_mixed = desc->extmap_allow_mixed();
}

BaseChannel::BaseChannel(rtc::Thread* worker_thread,
                         rtc::Thread* network_thread,
                         rtc::Thread* signaling_thread,
                         std::unique_ptr<MediaChannel> media_channel,
                         const std::string& content_name,
                         bool srtp_required,
                         webrtc::CryptoOptions crypto_options,
                         UniqueRandomIdGenerator* ssrc_generator)
    : worker_thread_(worker_thread),
      network_thread_(network_thread),
      signaling_thread_(signaling_thread),
      content_name_(content_name),
      srtp_required_(srtp_required),
      crypto_options_(crypto_options),
      media_channel_(std::move(media_channel)),
      ssrc_generator_(ssrc_generator) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  RTC_DCHECK(ssrc_generator_);
  demuxer_criteria_.mid = content_name;
  RTC_LOG(LS_INFO) << "Created channel: " << ToString();
}

BaseChannel::~BaseChannel() {
  TRACE_EVENT0("webrtc", "BaseChannel::~BaseChannel");
  RTC_DCHECK_RUN_ON(worker_thread_);

  // Eats any outstanding messages or packets.
  worker_thread_->Clear(&invoker_);
  worker_thread_->Clear(this);
  // We must destroy the media channel before the transport channel, otherwise
  // the media channel may try to send on the dead transport channel. NULLing
  // is not an effective strategy since the sends will come on another thread.
  media_channel_.reset();
  RTC_LOG(LS_INFO) << "Destroyed channel: " << ToString();
}

std::string BaseChannel::ToString() const {
  rtc::StringBuilder sb;
  sb << "{mid: " << content_name_;
  if (media_channel_) {
    sb << ", media_type: " << MediaTypeToString(media_channel_->media_type());
  }
  sb << "}";
  return sb.Release();
}

bool BaseChannel::ConnectToRtpTransport() {
  RTC_DCHECK(rtp_transport_);
  if (!RegisterRtpDemuxerSink()) {
    RTC_LOG(LS_ERROR) << "Failed to set up demuxing for " << ToString();
    return false;
  }
  rtp_transport_->SignalReadyToSend.connect(
      this, &BaseChannel::OnTransportReadyToSend);
  rtp_transport_->SignalNetworkRouteChanged.connect(
      this, &BaseChannel::OnNetworkRouteChanged);
  rtp_transport_->SignalWritableState.connect(this,
                                              &BaseChannel::OnWritableState);
  rtp_transport_->SignalSentPacket.connect(this,
                                           &BaseChannel::SignalSentPacket_n);
  return true;
}

void BaseChannel::DisconnectFromRtpTransport() {
  RTC_DCHECK(rtp_transport_);
  rtp_transport_->UnregisterRtpDemuxerSink(this);
  rtp_transport_->SignalReadyToSend.disconnect(this);
  rtp_transport_->SignalNetworkRouteChanged.disconnect(this);
  rtp_transport_->SignalWritableState.disconnect(this);
  rtp_transport_->SignalSentPacket.disconnect(this);
}

void BaseChannel::Init_w(webrtc::RtpTransportInternal* rtp_transport) {
  RTC_DCHECK_RUN_ON(worker_thread_);

  network_thread_->Invoke<void>(
      RTC_FROM_HERE, [this, rtp_transport] { SetRtpTransport(rtp_transport); });

  // Both RTP and RTCP channels should be set, we can call SetInterface on
  // the media channel and it can set network options.
  media_channel_->SetInterface(this);
}

void BaseChannel::Deinit() {
  RTC_DCHECK_RUN_ON(worker_thread());
  media_channel_->SetInterface(/*iface=*/nullptr);
  // Packets arrive on the network thread, processing packets calls virtual
  // functions, so need to stop this process in Deinit that is called in
  // derived classes destructor.
  network_thread_->Invoke<void>(RTC_FROM_HERE, [&] {
    FlushRtcpMessages_n();

    if (rtp_transport_) {
      DisconnectFromRtpTransport();
    }
    // Clear pending read packets/messages.
    network_thread_->Clear(&invoker_);
    network_thread_->Clear(this);
  });
}

bool BaseChannel::SetRtpTransport(webrtc::RtpTransportInternal* rtp_transport) {
  if (rtp_transport == rtp_transport_) {
    return true;
  }

  if (!network_thread_->IsCurrent()) {
    return network_thread_->Invoke<bool>(RTC_FROM_HERE, [this, rtp_transport] {
      return SetRtpTransport(rtp_transport);
    });
  }

  if (rtp_transport_) {
    DisconnectFromRtpTransport();
  }

  rtp_transport_ = rtp_transport;
  if (rtp_transport_) {
    transport_name_ = rtp_transport_->transport_name();

    if (!ConnectToRtpTransport()) {
      RTC_LOG(LS_ERROR) << "Failed to connect to the new RtpTransport for "
                        << ToString() << ".";
      return false;
    }
    OnTransportReadyToSend(rtp_transport_->IsReadyToSend());
    UpdateWritableState_n();

    // Set the cached socket options.
    for (const auto& pair : socket_options_) {
      rtp_transport_->SetRtpOption(pair.first, pair.second);
    }
    if (!rtp_transport_->rtcp_mux_enabled()) {
      for (const auto& pair : rtcp_socket_options_) {
        rtp_transport_->SetRtcpOption(pair.first, pair.second);
      }
    }
  }
  return true;
}

bool BaseChannel::Enable(bool enable) {
  worker_thread_->Invoke<void>(
      RTC_FROM_HERE,
      Bind(enable ? &BaseChannel::EnableMedia_w : &BaseChannel::DisableMedia_w,
           this));
  return true;
}

bool BaseChannel::SetLocalContent(const MediaContentDescription* content,
                                  SdpType type,
                                  std::string* error_desc) {
  TRACE_EVENT0("webrtc", "BaseChannel::SetLocalContent");
  return InvokeOnWorker<bool>(
      RTC_FROM_HERE,
      Bind(&BaseChannel::SetLocalContent_w, this, content, type, error_desc));
}

bool BaseChannel::SetRemoteContent(const MediaContentDescription* content,
                                   SdpType type,
                                   std::string* error_desc) {
  TRACE_EVENT0("webrtc", "BaseChannel::SetRemoteContent");
  return InvokeOnWorker<bool>(
      RTC_FROM_HERE,
      Bind(&BaseChannel::SetRemoteContent_w, this, content, type, error_desc));
}

bool BaseChannel::SetPayloadTypeDemuxingEnabled(bool enabled) {
  TRACE_EVENT0("webrtc", "BaseChannel::SetPayloadTypeDemuxingEnabled");
  return InvokeOnWorker<bool>(
      RTC_FROM_HERE,
      Bind(&BaseChannel::SetPayloadTypeDemuxingEnabled_w, this, enabled));
}

bool BaseChannel::IsReadyToReceiveMedia_w() const {
  // Receive data if we are enabled and have local content,
  return enabled() &&
         webrtc::RtpTransceiverDirectionHasRecv(local_content_direction_);
}

bool BaseChannel::IsReadyToSendMedia_w() const {
  // Need to access some state updated on the network thread.
  return network_thread_->Invoke<bool>(
      RTC_FROM_HERE, Bind(&BaseChannel::IsReadyToSendMedia_n, this));
}

bool BaseChannel::IsReadyToSendMedia_n() const {
  // Send outgoing data if we are enabled, have local and remote content,
  // and we have had some form of connectivity.
  return enabled() &&
         webrtc::RtpTransceiverDirectionHasRecv(remote_content_direction_) &&
         webrtc::RtpTransceiverDirectionHasSend(local_content_direction_) &&
         was_ever_writable();
}

bool BaseChannel::SendPacket(rtc::CopyOnWriteBuffer* packet,
                             const rtc::PacketOptions& options) {
  return SendPacket(false, packet, options);
}

bool BaseChannel::SendRtcp(rtc::CopyOnWriteBuffer* packet,
                           const rtc::PacketOptions& options) {
  return SendPacket(true, packet, options);
}

int BaseChannel::SetOption(SocketType type,
                           rtc::Socket::Option opt,
                           int value) {
  return network_thread_->Invoke<int>(
      RTC_FROM_HERE, Bind(&BaseChannel::SetOption_n, this, type, opt, value));
}

int BaseChannel::SetOption_n(SocketType type,
                             rtc::Socket::Option opt,
                             int value) {
  RTC_DCHECK_RUN_ON(network_thread());
  RTC_DCHECK(rtp_transport_);
  switch (type) {
    case ST_RTP:
      socket_options_.push_back(
          std::pair<rtc::Socket::Option, int>(opt, value));
      return rtp_transport_->SetRtpOption(opt, value);
    case ST_RTCP:
      rtcp_socket_options_.push_back(
          std::pair<rtc::Socket::Option, int>(opt, value));
      return rtp_transport_->SetRtcpOption(opt, value);
  }
  return -1;
}

void BaseChannel::OnWritableState(bool writable) {
  RTC_DCHECK_RUN_ON(network_thread());
  if (writable) {
    ChannelWritable_n();
  } else {
    ChannelNotWritable_n();
  }
}

void BaseChannel::OnNetworkRouteChanged(
    absl::optional<rtc::NetworkRoute> network_route) {
  RTC_LOG(LS_INFO) << "Network route for " << ToString() << " was changed.";

  RTC_DCHECK_RUN_ON(network_thread());
  rtc::NetworkRoute new_route;
  if (network_route) {
    new_route = *(network_route);
  }
  // Note: When the RTCP-muxing is not enabled, RTCP transport and RTP transport
  // use the same transport name and MediaChannel::OnNetworkRouteChanged cannot
  // work correctly. Intentionally leave it broken to simplify the code and
  // encourage the users to stop using non-muxing RTCP.
  invoker_.AsyncInvoke<void>(RTC_FROM_HERE, worker_thread_, [=] {
    media_channel_->OnNetworkRouteChanged(transport_name_, new_route);
  });
}

sigslot::signal1<ChannelInterface*>& BaseChannel::SignalFirstPacketReceived() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  return SignalFirstPacketReceived_;
}

sigslot::signal1<const rtc::SentPacket&>& BaseChannel::SignalSentPacket() {
  // TODO(bugs.webrtc.org/11994): Uncomment this check once callers have been
  // fixed to access this variable from the correct thread.
  // RTC_DCHECK_RUN_ON(worker_thread_);
  return SignalSentPacket_;
}

void BaseChannel::OnTransportReadyToSend(bool ready) {
  invoker_.AsyncInvoke<void>(RTC_FROM_HERE, worker_thread_,
                             [=] { media_channel_->OnReadyToSend(ready); });
}

bool BaseChannel::SendPacket(bool rtcp,
                             rtc::CopyOnWriteBuffer* packet,
                             const rtc::PacketOptions& options) {
  // Until all the code is migrated to use RtpPacketType instead of bool.
  RtpPacketType packet_type = rtcp ? RtpPacketType::kRtcp : RtpPacketType::kRtp;
  // SendPacket gets called from MediaEngine, on a pacer or an encoder thread.
  // If the thread is not our network thread, we will post to our network
  // so that the real work happens on our network. This avoids us having to
  // synchronize access to all the pieces of the send path, including
  // SRTP and the inner workings of the transport channels.
  // The only downside is that we can't return a proper failure code if
  // needed. Since UDP is unreliable anyway, this should be a non-issue.
  if (!network_thread_->IsCurrent()) {
    // Avoid a copy by transferring the ownership of the packet data.
    int message_id = rtcp ? MSG_SEND_RTCP_PACKET : MSG_SEND_RTP_PACKET;
    SendPacketMessageData* data = new SendPacketMessageData;
    data->packet = std::move(*packet);
    data->options = options;
    network_thread_->Post(RTC_FROM_HERE, this, message_id, data);
    return true;
  }

  TRACE_EVENT0("webrtc", "BaseChannel::SendPacket");

  // Now that we are on the correct thread, ensure we have a place to send this
  // packet before doing anything. (We might get RTCP packets that we don't
  // intend to send.) If we've negotiated RTCP mux, send RTCP over the RTP
  // transport.
  if (!rtp_transport_ || !rtp_transport_->IsWritable(rtcp)) {
    return false;
  }

  // Protect ourselves against crazy data.
  if (!IsValidRtpPacketSize(packet_type, packet->size())) {
    RTC_LOG(LS_ERROR) << "Dropping outgoing " << ToString() << " "
                      << RtpPacketTypeToString(packet_type)
                      << " packet: wrong size=" << packet->size();
    return false;
  }

  if (!srtp_active()) {
    if (srtp_required_) {
      // The audio/video engines may attempt to send RTCP packets as soon as the
      // streams are created, so don't treat this as an error for RTCP.
      // See: https://bugs.chromium.org/p/webrtc/issues/detail?id=6809
      if (rtcp) {
        return false;
      }
      // However, there shouldn't be any RTP packets sent before SRTP is set up
      // (and SetSend(true) is called).
      RTC_LOG(LS_ERROR) << "Can't send outgoing RTP packet for " << ToString()
                        << " when SRTP is inactive and crypto is required";
      RTC_NOTREACHED();
      return false;
    }

    std::string packet_type = rtcp ? "RTCP" : "RTP";
    RTC_DLOG(LS_WARNING) << "Sending an " << packet_type
                         << " packet without encryption for " << ToString()
                         << ".";
  }

  // Bon voyage.
  return rtcp ? rtp_transport_->SendRtcpPacket(packet, options, PF_SRTP_BYPASS)
              : rtp_transport_->SendRtpPacket(packet, options, PF_SRTP_BYPASS);
}

void BaseChannel::OnRtpPacket(const webrtc::RtpPacketReceived& parsed_packet) {
  // Take packet time from the |parsed_packet|.
  // RtpPacketReceived.arrival_time_ms = (timestamp_us + 500) / 1000;
  int64_t packet_time_us = -1;
  if (parsed_packet.arrival_time_ms() > 0) {
    packet_time_us = parsed_packet.arrival_time_ms() * 1000;
  }

  if (!has_received_packet_) {
    has_received_packet_ = true;
    signaling_thread()->Post(RTC_FROM_HERE, this, MSG_FIRSTPACKETRECEIVED);
  }

  if (!srtp_active() && srtp_required_) {
    // Our session description indicates that SRTP is required, but we got a
    // packet before our SRTP filter is active. This means either that
    // a) we got SRTP packets before we received the SDES keys, in which case
    //    we can't decrypt it anyway, or
    // b) we got SRTP packets before DTLS completed on both the RTP and RTCP
    //    transports, so we haven't yet extracted keys, even if DTLS did
    //    complete on the transport that the packets are being sent on. It's
    //    really good practice to wait for both RTP and RTCP to be good to go
    //    before sending  media, to prevent weird failure modes, so it's fine
    //    for us to just eat packets here. This is all sidestepped if RTCP mux
    //    is used anyway.
    RTC_LOG(LS_WARNING) << "Can't process incoming RTP packet when "
                           "SRTP is inactive and crypto is required "
                        << ToString();
    return;
  }

  auto packet_buffer = parsed_packet.Buffer();

  invoker_.AsyncInvoke<void>(
      RTC_FROM_HERE, worker_thread_, [this, packet_buffer, packet_time_us] {
        RTC_DCHECK_RUN_ON(worker_thread());
        media_channel_->OnPacketReceived(packet_buffer, packet_time_us);
      });
}

void BaseChannel::UpdateRtpHeaderExtensionMap(
    const RtpHeaderExtensions& header_extensions) {
  RTC_DCHECK(rtp_transport_);
  // Update the header extension map on network thread in case there is data
  // race.
  // TODO(zhihuang): Add an rtc::ThreadChecker make sure to RtpTransport won't
  // be accessed from different threads.
  //
  // NOTE: This doesn't take the BUNDLE case in account meaning the RTP header
  // extension maps are not merged when BUNDLE is enabled. This is fine because
  // the ID for MID should be consistent among all the RTP transports.
  network_thread_->Invoke<void>(RTC_FROM_HERE, [this, &header_extensions] {
    rtp_transport_->UpdateRtpHeaderExtensionMap(header_extensions);
  });
}

bool BaseChannel::RegisterRtpDemuxerSink() {
  RTC_DCHECK(rtp_transport_);
  return network_thread_->Invoke<bool>(RTC_FROM_HERE, [this] {
    return rtp_transport_->RegisterRtpDemuxerSink(demuxer_criteria_, this);
  });
}

void BaseChannel::EnableMedia_w() {
  RTC_DCHECK(worker_thread_ == rtc::Thread::Current());
  if (enabled_)
    return;

  RTC_LOG(LS_INFO) << "Channel enabled: " << ToString();
  enabled_ = true;
  UpdateMediaSendRecvState_w();
}

void BaseChannel::DisableMedia_w() {
  RTC_DCHECK(worker_thread_ == rtc::Thread::Current());
  if (!enabled_)
    return;

  RTC_LOG(LS_INFO) << "Channel disabled: " << ToString();
  enabled_ = false;
  UpdateMediaSendRecvState_w();
}

void BaseChannel::UpdateWritableState_n() {
  if (rtp_transport_->IsWritable(/*rtcp=*/true) &&
      rtp_transport_->IsWritable(/*rtcp=*/false)) {
    ChannelWritable_n();
  } else {
    ChannelNotWritable_n();
  }
}

void BaseChannel::ChannelWritable_n() {
  RTC_DCHECK_RUN_ON(network_thread());
  if (writable_) {
    return;
  }

  RTC_LOG(LS_INFO) << "Channel writable (" << ToString() << ")"
                   << (was_ever_writable_ ? "" : " for the first time");

  was_ever_writable_ = true;
  writable_ = true;
  UpdateMediaSendRecvState();
}

void BaseChannel::ChannelNotWritable_n() {
  RTC_DCHECK_RUN_ON(network_thread());
  if (!writable_)
    return;

  RTC_LOG(LS_INFO) << "Channel not writable (" << ToString() << ")";
  writable_ = false;
  UpdateMediaSendRecvState();
}

bool BaseChannel::AddRecvStream_w(const StreamParams& sp) {
  RTC_DCHECK(worker_thread() == rtc::Thread::Current());
  return media_channel()->AddRecvStream(sp);
}

bool BaseChannel::RemoveRecvStream_w(uint32_t ssrc) {
  RTC_DCHECK(worker_thread() == rtc::Thread::Current());
  return media_channel()->RemoveRecvStream(ssrc);
}

void BaseChannel::ResetUnsignaledRecvStream_w() {
  RTC_DCHECK(worker_thread() == rtc::Thread::Current());
  media_channel()->ResetUnsignaledRecvStream();
}

bool BaseChannel::SetPayloadTypeDemuxingEnabled_w(bool enabled) {
  RTC_DCHECK_RUN_ON(worker_thread());
  if (enabled == payload_type_demuxing_enabled_) {
    return true;
  }
  payload_type_demuxing_enabled_ = enabled;
  if (!enabled) {
    // TODO(crbug.com/11477): This will remove *all* unsignaled streams (those
    // without an explicitly signaled SSRC), which may include streams that
    // were matched to this channel by MID or RID. Ideally we'd remove only the
    // streams that were matched based on payload type alone, but currently
    // there is no straightforward way to identify those streams.
    media_channel()->ResetUnsignaledRecvStream();
    demuxer_criteria_.payload_types.clear();
    if (!RegisterRtpDemuxerSink()) {
      RTC_LOG(LS_ERROR) << "Failed to disable payload type demuxing for "
                        << ToString();
      return false;
    }
  } else if (!payload_types_.empty()) {
    demuxer_criteria_.payload_types.insert(payload_types_.begin(),
                                           payload_types_.end());
    if (!RegisterRtpDemuxerSink()) {
      RTC_LOG(LS_ERROR) << "Failed to enable payload type demuxing for "
                        << ToString();
      return false;
    }
  }
  return true;
}

bool BaseChannel::UpdateLocalStreams_w(const std::vector<StreamParams>& streams,
                                       SdpType type,
                                       std::string* error_desc) {
  // In the case of RIDs (where SSRCs are not negotiated), this method will
  // generate an SSRC for each layer in StreamParams. That representation will
  // be stored internally in |local_streams_|.
  // In subsequent offers, the same stream can appear in |streams| again
  // (without the SSRCs), so it should be looked up using RIDs (if available)
  // and then by primary SSRC.
  // In both scenarios, it is safe to assume that the media channel will be
  // created with a StreamParams object with SSRCs. However, it is not safe to
  // assume that |local_streams_| will always have SSRCs as there are scenarios
  // in which niether SSRCs or RIDs are negotiated.

  // Check for streams that have been removed.
  bool ret = true;
  for (const StreamParams& old_stream : local_streams_) {
    if (!old_stream.has_ssrcs() ||
        GetStream(streams, StreamFinder(&old_stream))) {
      continue;
    }
    if (!media_channel()->RemoveSendStream(old_stream.first_ssrc())) {
      rtc::StringBuilder desc;
      desc << "Failed to remove send stream with ssrc "
           << old_stream.first_ssrc() << " from m-section with mid='"
           << content_name() << "'.";
      SafeSetError(desc.str(), error_desc);
      ret = false;
    }
  }
  // Check for new streams.
  std::vector<StreamParams> all_streams;
  for (const StreamParams& stream : streams) {
    StreamParams* existing = GetStream(local_streams_, StreamFinder(&stream));
    if (existing) {
      // Parameters cannot change for an existing stream.
      all_streams.push_back(*existing);
      continue;
    }

    all_streams.push_back(stream);
    StreamParams& new_stream = all_streams.back();

    if (!new_stream.has_ssrcs() && !new_stream.has_rids()) {
      continue;
    }

    RTC_DCHECK(new_stream.has_ssrcs() || new_stream.has_rids());
    if (new_stream.has_ssrcs() && new_stream.has_rids()) {
      rtc::StringBuilder desc;
      desc << "Failed to add send stream: " << new_stream.first_ssrc()
           << " into m-section with mid='" << content_name()
           << "'. Stream has both SSRCs and RIDs.";
      SafeSetError(desc.str(), error_desc);
      ret = false;
      continue;
    }

    // At this point we use the legacy simulcast group in StreamParams to
    // indicate that we want multiple layers to the media channel.
    if (!new_stream.has_ssrcs()) {
      // TODO(bugs.webrtc.org/10250): Indicate if flex is desired here.
      new_stream.GenerateSsrcs(new_stream.rids().size(), /* rtx = */ true,
                               /* flex_fec = */ false, ssrc_generator_);
    }

    if (media_channel()->AddSendStream(new_stream)) {
      RTC_LOG(LS_INFO) << "Add send stream ssrc: " << new_stream.ssrcs[0]
                       << " into " << ToString();
    } else {
      rtc::StringBuilder desc;
      desc << "Failed to add send stream ssrc: " << new_stream.first_ssrc()
           << " into m-section with mid='" << content_name() << "'";
      SafeSetError(desc.str(), error_desc);
      ret = false;
    }
  }
  local_streams_ = all_streams;
  return ret;
}

bool BaseChannel::UpdateRemoteStreams_w(
    const std::vector<StreamParams>& streams,
    SdpType type,
    std::string* error_desc) {
  // Check for streams that have been removed.
  bool ret = true;
  for (const StreamParams& old_stream : remote_streams_) {
    // If we no longer have an unsignaled stream, we would like to remove
    // the unsignaled stream params that are cached.
    if (!old_stream.has_ssrcs() && !HasStreamWithNoSsrcs(streams)) {
      ResetUnsignaledRecvStream_w();
      RTC_LOG(LS_INFO) << "Reset unsignaled remote stream for " << ToString()
                       << ".";
    } else if (old_stream.has_ssrcs() &&
               !GetStreamBySsrc(streams, old_stream.first_ssrc())) {
      if (RemoveRecvStream_w(old_stream.first_ssrc())) {
        RTC_LOG(LS_INFO) << "Remove remote ssrc: " << old_stream.first_ssrc()
                         << " from " << ToString() << ".";
      } else {
        rtc::StringBuilder desc;
        desc << "Failed to remove remote stream with ssrc "
             << old_stream.first_ssrc() << " from m-section with mid='"
             << content_name() << "'.";
        SafeSetError(desc.str(), error_desc);
        ret = false;
      }
    }
  }
  demuxer_criteria_.ssrcs.clear();
  // Check for new streams.
  for (const StreamParams& new_stream : streams) {
    // We allow a StreamParams with an empty list of SSRCs, in which case the
    // MediaChannel will cache the parameters and use them for any unsignaled
    // stream received later.
    if ((!new_stream.has_ssrcs() && !HasStreamWithNoSsrcs(remote_streams_)) ||
        !GetStreamBySsrc(remote_streams_, new_stream.first_ssrc())) {
      if (AddRecvStream_w(new_stream)) {
        RTC_LOG(LS_INFO) << "Add remote ssrc: "
                         << (new_stream.has_ssrcs()
                                 ? std::to_string(new_stream.first_ssrc())
                                 : "unsignaled")
                         << " to " << ToString();
      } else {
        rtc::StringBuilder desc;
        desc << "Failed to add remote stream ssrc: "
             << (new_stream.has_ssrcs()
                     ? std::to_string(new_stream.first_ssrc())
                     : "unsignaled")
             << " to " << ToString();
        SafeSetError(desc.str(), error_desc);
        ret = false;
      }
    }
    // Update the receiving SSRCs.
    demuxer_criteria_.ssrcs.insert(new_stream.ssrcs.begin(),
                                   new_stream.ssrcs.end());
  }
  // Re-register the sink to update the receiving ssrcs.
  if (!RegisterRtpDemuxerSink()) {
    RTC_LOG(LS_ERROR) << "Failed to set up demuxing for " << ToString();
    ret = false;
  }
  remote_streams_ = streams;
  return ret;
}

RtpHeaderExtensions BaseChannel::GetFilteredRtpHeaderExtensions(
    const RtpHeaderExtensions& extensions) {
  RTC_DCHECK(rtp_transport_);
  if (crypto_options_.srtp.enable_encrypted_rtp_header_extensions) {
    RtpHeaderExtensions filtered;
    absl::c_copy_if(extensions, std::back_inserter(filtered),
                    [](const webrtc::RtpExtension& extension) {
                      return !extension.encrypt;
                    });
    return filtered;
  }

  return webrtc::RtpExtension::FilterDuplicateNonEncrypted(extensions);
}

void BaseChannel::OnMessage(rtc::Message* pmsg) {
  TRACE_EVENT0("webrtc", "BaseChannel::OnMessage");
  switch (pmsg->message_id) {
    case MSG_SEND_RTP_PACKET:
    case MSG_SEND_RTCP_PACKET: {
      RTC_DCHECK_RUN_ON(network_thread());
      SendPacketMessageData* data =
          static_cast<SendPacketMessageData*>(pmsg->pdata);
      bool rtcp = pmsg->message_id == MSG_SEND_RTCP_PACKET;
      SendPacket(rtcp, &data->packet, data->options);
      delete data;
      break;
    }
    case MSG_FIRSTPACKETRECEIVED: {
      RTC_DCHECK_RUN_ON(signaling_thread_);
      SignalFirstPacketReceived_(this);
      break;
    }
  }
}

void BaseChannel::MaybeAddHandledPayloadType(int payload_type) {
  if (payload_type_demuxing_enabled_) {
    demuxer_criteria_.payload_types.insert(static_cast<uint8_t>(payload_type));
  }
  // Even if payload type demuxing is currently disabled, we need to remember
  // the payload types in case it's re-enabled later.
  payload_types_.insert(static_cast<uint8_t>(payload_type));
}

void BaseChannel::ClearHandledPayloadTypes() {
  demuxer_criteria_.payload_types.clear();
  payload_types_.clear();
}

void BaseChannel::FlushRtcpMessages_n() {
  // Flush all remaining RTCP messages. This should only be called in
  // destructor.
  RTC_DCHECK_RUN_ON(network_thread());
  rtc::MessageList rtcp_messages;
  network_thread_->Clear(this, MSG_SEND_RTCP_PACKET, &rtcp_messages);
  for (const auto& message : rtcp_messages) {
    network_thread_->Send(RTC_FROM_HERE, this, MSG_SEND_RTCP_PACKET,
                          message.pdata);
  }
}

void BaseChannel::SignalSentPacket_n(const rtc::SentPacket& sent_packet) {
  RTC_DCHECK_RUN_ON(network_thread());
  invoker_.AsyncInvoke<void>(RTC_FROM_HERE, worker_thread_,
                             [this, sent_packet] {
                               RTC_DCHECK_RUN_ON(worker_thread());
                               SignalSentPacket()(sent_packet);
                             });
}

VoiceChannel::VoiceChannel(rtc::Thread* worker_thread,
                           rtc::Thread* network_thread,
                           rtc::Thread* signaling_thread,
                           std::unique_ptr<VoiceMediaChannel> media_channel,
                           const std::string& content_name,
                           bool srtp_required,
                           webrtc::CryptoOptions crypto_options,
                           UniqueRandomIdGenerator* ssrc_generator)
    : BaseChannel(worker_thread,
                  network_thread,
                  signaling_thread,
                  std::move(media_channel),
                  content_name,
                  srtp_required,
                  crypto_options,
                  ssrc_generator) {}

VoiceChannel::~VoiceChannel() {
  TRACE_EVENT0("webrtc", "VoiceChannel::~VoiceChannel");
  // this can't be done in the base class, since it calls a virtual
  DisableMedia_w();
  Deinit();
}

void BaseChannel::UpdateMediaSendRecvState() {
  RTC_DCHECK_RUN_ON(network_thread());
  invoker_.AsyncInvoke<void>(RTC_FROM_HERE, worker_thread_,
                             [this] { UpdateMediaSendRecvState_w(); });
}

void VoiceChannel::Init_w(webrtc::RtpTransportInternal* rtp_transport) {
  BaseChannel::Init_w(rtp_transport);
}

void VoiceChannel::UpdateMediaSendRecvState_w() {
  // Render incoming data if we're the active call, and we have the local
  // content. We receive data on the default channel and multiplexed streams.
  bool recv = IsReadyToReceiveMedia_w();
  media_channel()->SetPlayout(recv);

  // Send outgoing data if we're the active call, we have the remote content,
  // and we have had some form of connectivity.
  bool send = IsReadyToSendMedia_w();
  media_channel()->SetSend(send);

  RTC_LOG(LS_INFO) << "Changing voice state, recv=" << recv << " send=" << send
                   << " for " << ToString();
}

bool VoiceChannel::SetLocalContent_w(const MediaContentDescription* content,
                                     SdpType type,
                                     std::string* error_desc) {
  TRACE_EVENT0("webrtc", "VoiceChannel::SetLocalContent_w");
  RTC_DCHECK_RUN_ON(worker_thread());
  RTC_LOG(LS_INFO) << "Setting local voice description for " << ToString();

  RTC_DCHECK(content);
  if (!content) {
    SafeSetError("Can't find audio content in local description.", error_desc);
    return false;
  }

  const AudioContentDescription* audio = content->as_audio();

  RtpHeaderExtensions rtp_header_extensions =
      GetFilteredRtpHeaderExtensions(audio->rtp_header_extensions());
  UpdateRtpHeaderExtensionMap(rtp_header_extensions);
  media_channel()->SetExtmapAllowMixed(audio->extmap_allow_mixed());

  AudioRecvParameters recv_params = last_recv_params_;
  RtpParametersFromMediaDescription(
      audio, rtp_header_extensions,
      webrtc::RtpTransceiverDirectionHasRecv(audio->direction()), &recv_params);
  if (!media_channel()->SetRecvParameters(recv_params)) {
    SafeSetError(
        "Failed to set local audio description recv parameters for m-section "
        "with mid='" +
            content_name() + "'.",
        error_desc);
    return false;
  }

  if (webrtc::RtpTransceiverDirectionHasRecv(audio->direction())) {
    for (const AudioCodec& codec : audio->codecs()) {
      MaybeAddHandledPayloadType(codec.id);
    }
    // Need to re-register the sink to update the handled payload.
    if (!RegisterRtpDemuxerSink()) {
      RTC_LOG(LS_ERROR) << "Failed to set up audio demuxing for " << ToString();
      return false;
    }
  }

  last_recv_params_ = recv_params;

  // TODO(pthatcher): Move local streams into AudioSendParameters, and
  // only give it to the media channel once we have a remote
  // description too (without a remote description, we won't be able
  // to send them anyway).
  if (!UpdateLocalStreams_w(audio->streams(), type, error_desc)) {
    SafeSetError(
        "Failed to set local audio description streams for m-section with "
        "mid='" +
            content_name() + "'.",
        error_desc);
    return false;
  }

  set_local_content_direction(content->direction());
  UpdateMediaSendRecvState_w();
  return true;
}

bool VoiceChannel::SetRemoteContent_w(const MediaContentDescription* content,
                                      SdpType type,
                                      std::string* error_desc) {
  TRACE_EVENT0("webrtc", "VoiceChannel::SetRemoteContent_w");
  RTC_DCHECK_RUN_ON(worker_thread());
  RTC_LOG(LS_INFO) << "Setting remote voice description for " << ToString();

  RTC_DCHECK(content);
  if (!content) {
    SafeSetError("Can't find audio content in remote description.", error_desc);
    return false;
  }

  const AudioContentDescription* audio = content->as_audio();

  RtpHeaderExtensions rtp_header_extensions =
      GetFilteredRtpHeaderExtensions(audio->rtp_header_extensions());

  AudioSendParameters send_params = last_send_params_;
  RtpSendParametersFromMediaDescription(
      audio, rtp_header_extensions,
      webrtc::RtpTransceiverDirectionHasRecv(audio->direction()), &send_params);
  send_params.mid = content_name();

  bool parameters_applied = media_channel()->SetSendParameters(send_params);
  if (!parameters_applied) {
    SafeSetError(
        "Failed to set remote audio description send parameters for m-section "
        "with mid='" +
            content_name() + "'.",
        error_desc);
    return false;
  }
  last_send_params_ = send_params;

  if (!webrtc::RtpTransceiverDirectionHasSend(content->direction())) {
    RTC_DLOG(LS_VERBOSE) << "SetRemoteContent_w: remote side will not send - "
                            "disable payload type demuxing for "
                         << ToString();
    ClearHandledPayloadTypes();
    if (!RegisterRtpDemuxerSink()) {
      RTC_LOG(LS_ERROR) << "Failed to update audio demuxing for " << ToString();
      return false;
    }
  }

  // TODO(pthatcher): Move remote streams into AudioRecvParameters,
  // and only give it to the media channel once we have a local
  // description too (without a local description, we won't be able to
  // recv them anyway).
  if (!UpdateRemoteStreams_w(audio->streams(), type, error_desc)) {
    SafeSetError(
        "Failed to set remote audio description streams for m-section with "
        "mid='" +
            content_name() + "'.",
        error_desc);
    return false;
  }

  set_remote_content_direction(content->direction());
  UpdateMediaSendRecvState_w();
  return true;
}

VideoChannel::VideoChannel(rtc::Thread* worker_thread,
                           rtc::Thread* network_thread,
                           rtc::Thread* signaling_thread,
                           std::unique_ptr<VideoMediaChannel> media_channel,
                           const std::string& content_name,
                           bool srtp_required,
                           webrtc::CryptoOptions crypto_options,
                           UniqueRandomIdGenerator* ssrc_generator)
    : BaseChannel(worker_thread,
                  network_thread,
                  signaling_thread,
                  std::move(media_channel),
                  content_name,
                  srtp_required,
                  crypto_options,
                  ssrc_generator) {}

VideoChannel::~VideoChannel() {
  TRACE_EVENT0("webrtc", "VideoChannel::~VideoChannel");
  // this can't be done in the base class, since it calls a virtual
  DisableMedia_w();
  Deinit();
}

void VideoChannel::UpdateMediaSendRecvState_w() {
  // Send outgoing data if we're the active call, we have the remote content,
  // and we have had some form of connectivity.
  bool send = IsReadyToSendMedia_w();
  if (!media_channel()->SetSend(send)) {
    RTC_LOG(LS_ERROR) << "Failed to SetSend on video channel: " + ToString();
    // TODO(gangji): Report error back to server.
  }

  RTC_LOG(LS_INFO) << "Changing video state, send=" << send << " for "
                   << ToString();
}

void VideoChannel::FillBitrateInfo(BandwidthEstimationInfo* bwe_info) {
  InvokeOnWorker<void>(RTC_FROM_HERE, Bind(&VideoMediaChannel::FillBitrateInfo,
                                           media_channel(), bwe_info));
}

bool VideoChannel::SetLocalContent_w(const MediaContentDescription* content,
                                     SdpType type,
                                     std::string* error_desc) {
  TRACE_EVENT0("webrtc", "VideoChannel::SetLocalContent_w");
  RTC_DCHECK_RUN_ON(worker_thread());
  RTC_LOG(LS_INFO) << "Setting local video description for " << ToString();

  RTC_DCHECK(content);
  if (!content) {
    SafeSetError("Can't find video content in local description.", error_desc);
    return false;
  }

  const VideoContentDescription* video = content->as_video();

  RtpHeaderExtensions rtp_header_extensions =
      GetFilteredRtpHeaderExtensions(video->rtp_header_extensions());
  UpdateRtpHeaderExtensionMap(rtp_header_extensions);
  media_channel()->SetExtmapAllowMixed(video->extmap_allow_mixed());

  VideoRecvParameters recv_params = last_recv_params_;
  RtpParametersFromMediaDescription(
      video, rtp_header_extensions,
      webrtc::RtpTransceiverDirectionHasRecv(video->direction()), &recv_params);

  VideoSendParameters send_params = last_send_params_;

  bool needs_send_params_update = false;
  if (type == SdpType::kAnswer || type == SdpType::kPrAnswer) {
    for (auto& send_codec : send_params.codecs) {
      auto* recv_codec = FindMatchingCodec(recv_params.codecs, send_codec);
      if (recv_codec) {
        if (!recv_codec->packetization && send_codec.packetization) {
          send_codec.packetization.reset();
          needs_send_params_update = true;
        } else if (recv_codec->packetization != send_codec.packetization) {
          SafeSetError(
              "Failed to set local answer due to invalid codec packetization "
              "specified in m-section with mid='" +
                  content_name() + "'.",
              error_desc);
          return false;
        }
      }
    }
  }

  if (!media_channel()->SetRecvParameters(recv_params)) {
    SafeSetError(
        "Failed to set local video description recv parameters for m-section "
        "with mid='" +
            content_name() + "'.",
        error_desc);
    return false;
  }

  if (webrtc::RtpTransceiverDirectionHasRecv(video->direction())) {
    for (const VideoCodec& codec : video->codecs()) {
      MaybeAddHandledPayloadType(codec.id);
    }
    // Need to re-register the sink to update the handled payload.
    if (!RegisterRtpDemuxerSink()) {
      RTC_LOG(LS_ERROR) << "Failed to set up video demuxing for " << ToString();
      return false;
    }
  }

  last_recv_params_ = recv_params;

  if (needs_send_params_update) {
    if (!media_channel()->SetSendParameters(send_params)) {
      SafeSetError("Failed to set send parameters for m-section with mid='" +
                       content_name() + "'.",
                   error_desc);
      return false;
    }
    last_send_params_ = send_params;
  }

  // TODO(pthatcher): Move local streams into VideoSendParameters, and
  // only give it to the media channel once we have a remote
  // description too (without a remote description, we won't be able
  // to send them anyway).
  if (!UpdateLocalStreams_w(video->streams(), type, error_desc)) {
    SafeSetError(
        "Failed to set local video description streams for m-section with "
        "mid='" +
            content_name() + "'.",
        error_desc);
    return false;
  }

  set_local_content_direction(content->direction());
  UpdateMediaSendRecvState_w();
  return true;
}

bool VideoChannel::SetRemoteContent_w(const MediaContentDescription* content,
                                      SdpType type,
                                      std::string* error_desc) {
  TRACE_EVENT0("webrtc", "VideoChannel::SetRemoteContent_w");
  RTC_DCHECK_RUN_ON(worker_thread());
  RTC_LOG(LS_INFO) << "Setting remote video description for " << ToString();

  RTC_DCHECK(content);
  if (!content) {
    SafeSetError("Can't find video content in remote description.", error_desc);
    return false;
  }

  const VideoContentDescription* video = content->as_video();

  RtpHeaderExtensions rtp_header_extensions =
      GetFilteredRtpHeaderExtensions(video->rtp_header_extensions());

  VideoSendParameters send_params = last_send_params_;
  RtpSendParametersFromMediaDescription(
      video, rtp_header_extensions,
      webrtc::RtpTransceiverDirectionHasRecv(video->direction()), &send_params);
  if (video->conference_mode()) {
    send_params.conference_mode = true;
  }
  send_params.mid = content_name();

  VideoRecvParameters recv_params = last_recv_params_;

  bool needs_recv_params_update = false;
  if (type == SdpType::kAnswer || type == SdpType::kPrAnswer) {
    for (auto& recv_codec : recv_params.codecs) {
      auto* send_codec = FindMatchingCodec(send_params.codecs, recv_codec);
      if (send_codec) {
        if (!send_codec->packetization && recv_codec.packetization) {
          recv_codec.packetization.reset();
          needs_recv_params_update = true;
        } else if (send_codec->packetization != recv_codec.packetization) {
          SafeSetError(
              "Failed to set remote answer due to invalid codec packetization "
              "specifid in m-section with mid='" +
                  content_name() + "'.",
              error_desc);
          return false;
        }
      }
    }
  }

  if (!media_channel()->SetSendParameters(send_params)) {
    SafeSetError(
        "Failed to set remote video description send parameters for m-section "
        "with mid='" +
            content_name() + "'.",
        error_desc);
    return false;
  }
  last_send_params_ = send_params;

  if (needs_recv_params_update) {
    if (!media_channel()->SetRecvParameters(recv_params)) {
      SafeSetError("Failed to set recv parameters for m-section with mid='" +
                       content_name() + "'.",
                   error_desc);
      return false;
    }
    last_recv_params_ = recv_params;
  }

  if (!webrtc::RtpTransceiverDirectionHasSend(content->direction())) {
    RTC_DLOG(LS_VERBOSE) << "SetRemoteContent_w: remote side will not send - "
                            "disable payload type demuxing for "
                         << ToString();
    ClearHandledPayloadTypes();
    if (!RegisterRtpDemuxerSink()) {
      RTC_LOG(LS_ERROR) << "Failed to update video demuxing for " << ToString();
      return false;
    }
  }

  // TODO(pthatcher): Move remote streams into VideoRecvParameters,
  // and only give it to the media channel once we have a local
  // description too (without a local description, we won't be able to
  // recv them anyway).
  if (!UpdateRemoteStreams_w(video->streams(), type, error_desc)) {
    SafeSetError(
        "Failed to set remote video description streams for m-section with "
        "mid='" +
            content_name() + "'.",
        error_desc);
    return false;
  }
  set_remote_content_direction(content->direction());
  UpdateMediaSendRecvState_w();
  return true;
}

RtpDataChannel::RtpDataChannel(rtc::Thread* worker_thread,
                               rtc::Thread* network_thread,
                               rtc::Thread* signaling_thread,
                               std::unique_ptr<DataMediaChannel> media_channel,
                               const std::string& content_name,
                               bool srtp_required,
                               webrtc::CryptoOptions crypto_options,
                               UniqueRandomIdGenerator* ssrc_generator)
    : BaseChannel(worker_thread,
                  network_thread,
                  signaling_thread,
                  std::move(media_channel),
                  content_name,
                  srtp_required,
                  crypto_options,
                  ssrc_generator) {}

RtpDataChannel::~RtpDataChannel() {
  TRACE_EVENT0("webrtc", "RtpDataChannel::~RtpDataChannel");
  // this can't be done in the base class, since it calls a virtual
  DisableMedia_w();
  Deinit();
}

void RtpDataChannel::Init_w(webrtc::RtpTransportInternal* rtp_transport) {
  BaseChannel::Init_w(rtp_transport);
  media_channel()->SignalDataReceived.connect(this,
                                              &RtpDataChannel::OnDataReceived);
  media_channel()->SignalReadyToSend.connect(
      this, &RtpDataChannel::OnDataChannelReadyToSend);
}

bool RtpDataChannel::SendData(const SendDataParams& params,
                              const rtc::CopyOnWriteBuffer& payload,
                              SendDataResult* result) {
  return InvokeOnWorker<bool>(
      RTC_FROM_HERE, Bind(&DataMediaChannel::SendData, media_channel(), params,
                          payload, result));
}

bool RtpDataChannel::CheckDataChannelTypeFromContent(
    const MediaContentDescription* content,
    std::string* error_desc) {
  if (!content->as_rtp_data()) {
    if (content->as_sctp()) {
      SafeSetError("Data channel type mismatch. Expected RTP, got SCTP.",
                   error_desc);
    } else {
      SafeSetError("Data channel is not RTP or SCTP.", error_desc);
    }
    return false;
  }
  return true;
}

bool RtpDataChannel::SetLocalContent_w(const MediaContentDescription* content,
                                       SdpType type,
                                       std::string* error_desc) {
  TRACE_EVENT0("webrtc", "RtpDataChannel::SetLocalContent_w");
  RTC_DCHECK_RUN_ON(worker_thread());
  RTC_LOG(LS_INFO) << "Setting local data description for " << ToString();

  RTC_DCHECK(content);
  if (!content) {
    SafeSetError("Can't find data content in local description.", error_desc);
    return false;
  }

  if (!CheckDataChannelTypeFromContent(content, error_desc)) {
    return false;
  }
  const RtpDataContentDescription* data = content->as_rtp_data();

  RtpHeaderExtensions rtp_header_extensions =
      GetFilteredRtpHeaderExtensions(data->rtp_header_extensions());

  DataRecvParameters recv_params = last_recv_params_;
  RtpParametersFromMediaDescription(
      data, rtp_header_extensions,
      webrtc::RtpTransceiverDirectionHasRecv(data->direction()), &recv_params);
  if (!media_channel()->SetRecvParameters(recv_params)) {
    SafeSetError(
        "Failed to set remote data description recv parameters for m-section "
        "with mid='" +
            content_name() + "'.",
        error_desc);
    return false;
  }
  for (const DataCodec& codec : data->codecs()) {
    MaybeAddHandledPayloadType(codec.id);
  }
  // Need to re-register the sink to update the handled payload.
  if (!RegisterRtpDemuxerSink()) {
    RTC_LOG(LS_ERROR) << "Failed to set up data demuxing for " << ToString();
    return false;
  }

  last_recv_params_ = recv_params;

  // TODO(pthatcher): Move local streams into DataSendParameters, and
  // only give it to the media channel once we have a remote
  // description too (without a remote description, we won't be able
  // to send them anyway).
  if (!UpdateLocalStreams_w(data->streams(), type, error_desc)) {
    SafeSetError(
        "Failed to set local data description streams for m-section with "
        "mid='" +
            content_name() + "'.",
        error_desc);
    return false;
  }

  set_local_content_direction(content->direction());
  UpdateMediaSendRecvState_w();
  return true;
}

bool RtpDataChannel::SetRemoteContent_w(const MediaContentDescription* content,
                                        SdpType type,
                                        std::string* error_desc) {
  TRACE_EVENT0("webrtc", "RtpDataChannel::SetRemoteContent_w");
  RTC_DCHECK_RUN_ON(worker_thread());
  RTC_LOG(LS_INFO) << "Setting remote data description for " << ToString();

  RTC_DCHECK(content);
  if (!content) {
    SafeSetError("Can't find data content in remote description.", error_desc);
    return false;
  }

  if (!CheckDataChannelTypeFromContent(content, error_desc)) {
    return false;
  }

  const RtpDataContentDescription* data = content->as_rtp_data();

  // If the remote data doesn't have codecs, it must be empty, so ignore it.
  if (!data->has_codecs()) {
    return true;
  }

  RtpHeaderExtensions rtp_header_extensions =
      GetFilteredRtpHeaderExtensions(data->rtp_header_extensions());

  RTC_LOG(LS_INFO) << "Setting remote data description for " << ToString();
  DataSendParameters send_params = last_send_params_;
  RtpSendParametersFromMediaDescription<DataCodec>(
      data, rtp_header_extensions,
      webrtc::RtpTransceiverDirectionHasRecv(data->direction()), &send_params);
  if (!media_channel()->SetSendParameters(send_params)) {
    SafeSetError(
        "Failed to set remote data description send parameters for m-section "
        "with mid='" +
            content_name() + "'.",
        error_desc);
    return false;
  }
  last_send_params_ = send_params;

  // TODO(pthatcher): Move remote streams into DataRecvParameters,
  // and only give it to the media channel once we have a local
  // description too (without a local description, we won't be able to
  // recv them anyway).
  if (!UpdateRemoteStreams_w(data->streams(), type, error_desc)) {
    SafeSetError(
        "Failed to set remote data description streams for m-section with "
        "mid='" +
            content_name() + "'.",
        error_desc);
    return false;
  }

  set_remote_content_direction(content->direction());
  UpdateMediaSendRecvState_w();
  return true;
}

void RtpDataChannel::UpdateMediaSendRecvState_w() {
  // Render incoming data if we're the active call, and we have the local
  // content. We receive data on the default channel and multiplexed streams.
  bool recv = IsReadyToReceiveMedia_w();
  if (!media_channel()->SetReceive(recv)) {
    RTC_LOG(LS_ERROR) << "Failed to SetReceive on data channel: " << ToString();
  }

  // Send outgoing data if we're the active call, we have the remote content,
  // and we have had some form of connectivity.
  bool send = IsReadyToSendMedia_w();
  if (!media_channel()->SetSend(send)) {
    RTC_LOG(LS_ERROR) << "Failed to SetSend on data channel: " << ToString();
  }

  // Trigger SignalReadyToSendData asynchronously.
  OnDataChannelReadyToSend(send);

  RTC_LOG(LS_INFO) << "Changing data state, recv=" << recv << " send=" << send
                   << " for " << ToString();
}

void RtpDataChannel::OnMessage(rtc::Message* pmsg) {
  switch (pmsg->message_id) {
    case MSG_READYTOSENDDATA: {
      DataChannelReadyToSendMessageData* data =
          static_cast<DataChannelReadyToSendMessageData*>(pmsg->pdata);
      ready_to_send_data_ = data->data();
      SignalReadyToSendData(ready_to_send_data_);
      delete data;
      break;
    }
    case MSG_DATARECEIVED: {
      DataReceivedMessageData* data =
          static_cast<DataReceivedMessageData*>(pmsg->pdata);
      SignalDataReceived(data->params, data->payload);
      delete data;
      break;
    }
    default:
      BaseChannel::OnMessage(pmsg);
      break;
  }
}

void RtpDataChannel::OnDataReceived(const ReceiveDataParams& params,
                                    const char* data,
                                    size_t len) {
  DataReceivedMessageData* msg = new DataReceivedMessageData(params, data, len);
  signaling_thread()->Post(RTC_FROM_HERE, this, MSG_DATARECEIVED, msg);
}

void RtpDataChannel::OnDataChannelReadyToSend(bool writable) {
  // This is usded for congestion control to indicate that the stream is ready
  // to send by the MediaChannel, as opposed to OnReadyToSend, which indicates
  // that the transport channel is ready.
  signaling_thread()->Post(RTC_FROM_HERE, this, MSG_READYTOSENDDATA,
                           new DataChannelReadyToSendMessageData(writable));
}

}  // namespace cricket
