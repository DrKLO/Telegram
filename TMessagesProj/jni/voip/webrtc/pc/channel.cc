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

#include <algorithm>
#include <cstdint>
#include <iterator>
#include <map>
#include <utility>

#include "absl/algorithm/container.h"
#include "absl/strings/string_view.h"
#include "api/rtp_parameters.h"
#include "api/sequence_checker.h"
#include "api/task_queue/queued_task.h"
#include "media/base/codec.h"
#include "media/base/rid_description.h"
#include "media/base/rtp_utils.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "pc/rtp_media_utils.h"
#include "rtc_base/checks.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/logging.h"
#include "rtc_base/network_route.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/task_utils/pending_task_safety_flag.h"
#include "rtc_base/task_utils/to_queued_task.h"
#include "rtc_base/trace_event.h"

namespace cricket {
namespace {

using ::rtc::UniqueRandomIdGenerator;
using ::webrtc::PendingTaskSafetyFlag;
using ::webrtc::SdpType;
using ::webrtc::ToQueuedTask;

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
      alive_(PendingTaskSafetyFlag::Create()),
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
  alive_->SetNotAlive();
  // The media channel is destroyed at the end of the destructor, since it
  // is a std::unique_ptr. The transport channel (rtp_transport) must outlive
  // the media channel.
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
  RTC_DCHECK(media_channel());

  // We don't need to call OnDemuxerCriteriaUpdatePending/Complete because
  // there's no previous criteria to worry about.
  bool result = rtp_transport_->RegisterRtpDemuxerSink(demuxer_criteria_, this);
  if (result) {
    previous_demuxer_criteria_ = demuxer_criteria_;
  } else {
    previous_demuxer_criteria_ = {};
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
  RTC_DCHECK(media_channel());
  rtp_transport_->UnregisterRtpDemuxerSink(this);
  rtp_transport_->SignalReadyToSend.disconnect(this);
  rtp_transport_->SignalNetworkRouteChanged.disconnect(this);
  rtp_transport_->SignalWritableState.disconnect(this);
  rtp_transport_->SignalSentPacket.disconnect(this);
}

void BaseChannel::Init_w(webrtc::RtpTransportInternal* rtp_transport) {
  RTC_DCHECK_RUN_ON(worker_thread());

  network_thread_->Invoke<void>(RTC_FROM_HERE, [this, rtp_transport] {
    SetRtpTransport(rtp_transport);
    // Both RTP and RTCP channels should be set, we can call SetInterface on
    // the media channel and it can set network options.
    media_channel_->SetInterface(this);
  });
}

void BaseChannel::Deinit() {
  RTC_DCHECK_RUN_ON(worker_thread());
  // Packets arrive on the network thread, processing packets calls virtual
  // functions, so need to stop this process in Deinit that is called in
  // derived classes destructor.
  network_thread_->Invoke<void>(RTC_FROM_HERE, [&] {
    RTC_DCHECK_RUN_ON(network_thread());
    media_channel_->SetInterface(/*iface=*/nullptr);

    if (rtp_transport_) {
      DisconnectFromRtpTransport();
    }
  });
}

bool BaseChannel::SetRtpTransport(webrtc::RtpTransportInternal* rtp_transport) {
  RTC_DCHECK_RUN_ON(network_thread());
  if (rtp_transport == rtp_transport_) {
    return true;
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

void BaseChannel::Enable(bool enable) {
  RTC_DCHECK_RUN_ON(signaling_thread());

  if (enable == enabled_s_)
    return;

  enabled_s_ = enable;

  worker_thread_->PostTask(ToQueuedTask(alive_, [this, enable] {
    RTC_DCHECK_RUN_ON(worker_thread());
    // Sanity check to make sure that enabled_ and enabled_s_
    // stay in sync.
    RTC_DCHECK_NE(enabled_, enable);
    if (enable) {
      EnableMedia_w();
    } else {
      DisableMedia_w();
    }
  }));
}

bool BaseChannel::SetLocalContent(const MediaContentDescription* content,
                                  SdpType type,
                                  std::string* error_desc) {
  RTC_DCHECK_RUN_ON(worker_thread());
  TRACE_EVENT0("webrtc", "BaseChannel::SetLocalContent");
  return SetLocalContent_w(content, type, error_desc);
}

bool BaseChannel::SetRemoteContent(const MediaContentDescription* content,
                                   SdpType type,
                                   std::string* error_desc) {
  RTC_DCHECK_RUN_ON(worker_thread());
  TRACE_EVENT0("webrtc", "BaseChannel::SetRemoteContent");
  return SetRemoteContent_w(content, type, error_desc);
}

bool BaseChannel::SetPayloadTypeDemuxingEnabled(bool enabled) {
  RTC_DCHECK_RUN_ON(worker_thread());
  TRACE_EVENT0("webrtc", "BaseChannel::SetPayloadTypeDemuxingEnabled");
  return SetPayloadTypeDemuxingEnabled_w(enabled);
}

bool BaseChannel::IsReadyToReceiveMedia_w() const {
  // Receive data if we are enabled and have local content,
  return enabled_ &&
         webrtc::RtpTransceiverDirectionHasRecv(local_content_direction_);
}

bool BaseChannel::IsReadyToSendMedia_w() const {
  // Send outgoing data if we are enabled, have local and remote content,
  // and we have had some form of connectivity.
  return enabled_ &&
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
  RTC_LOG(LS_INFO) << "Network route changed for " << ToString();

  RTC_DCHECK_RUN_ON(network_thread());
  rtc::NetworkRoute new_route;
  if (network_route) {
    new_route = *(network_route);
  }
  // Note: When the RTCP-muxing is not enabled, RTCP transport and RTP transport
  // use the same transport name and MediaChannel::OnNetworkRouteChanged cannot
  // work correctly. Intentionally leave it broken to simplify the code and
  // encourage the users to stop using non-muxing RTCP.
  media_channel_->OnNetworkRouteChanged(transport_name_, new_route);
}

void BaseChannel::SetFirstPacketReceivedCallback(
    std::function<void()> callback) {
  RTC_DCHECK_RUN_ON(network_thread());
  RTC_DCHECK(!on_first_packet_received_ || !callback);
  on_first_packet_received_ = std::move(callback);
}

void BaseChannel::OnTransportReadyToSend(bool ready) {
  RTC_DCHECK_RUN_ON(network_thread());
  media_channel_->OnReadyToSend(ready);
}

bool BaseChannel::SendPacket(bool rtcp,
                             rtc::CopyOnWriteBuffer* packet,
                             const rtc::PacketOptions& options) {
  RTC_DCHECK_RUN_ON(network_thread());
  // Until all the code is migrated to use RtpPacketType instead of bool.
  RtpPacketType packet_type = rtcp ? RtpPacketType::kRtcp : RtpPacketType::kRtp;
  // SendPacket gets called from MediaEngine, on a pacer or an encoder thread.
  // If the thread is not our network thread, we will post to our network
  // so that the real work happens on our network. This avoids us having to
  // synchronize access to all the pieces of the send path, including
  // SRTP and the inner workings of the transport channels.
  // The only downside is that we can't return a proper failure code if
  // needed. Since UDP is unreliable anyway, this should be a non-issue.

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
  RTC_DCHECK_RUN_ON(network_thread());

  if (on_first_packet_received_) {
    on_first_packet_received_();
    on_first_packet_received_ = nullptr;
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

  webrtc::Timestamp packet_time = parsed_packet.arrival_time();
  media_channel_->OnPacketReceived(
      parsed_packet.Buffer(),
      packet_time.IsMinusInfinity() ? -1 : packet_time.us());
}

void BaseChannel::UpdateRtpHeaderExtensionMap(
    const RtpHeaderExtensions& header_extensions) {
  // Update the header extension map on network thread in case there is data
  // race.
  //
  // NOTE: This doesn't take the BUNDLE case in account meaning the RTP header
  // extension maps are not merged when BUNDLE is enabled. This is fine because
  // the ID for MID should be consistent among all the RTP transports.
  network_thread_->Invoke<void>(RTC_FROM_HERE, [this, &header_extensions] {
    RTC_DCHECK_RUN_ON(network_thread());
    rtp_transport_->UpdateRtpHeaderExtensionMap(header_extensions);
  });
}

bool BaseChannel::RegisterRtpDemuxerSink_w() {
  if (demuxer_criteria_ == previous_demuxer_criteria_) {
    return true;
  }
  media_channel_->OnDemuxerCriteriaUpdatePending();
  // Copy demuxer criteria, since they're a worker-thread variable
  // and we want to pass them to the network thread
  return network_thread_->Invoke<bool>(
      RTC_FROM_HERE, [this, demuxer_criteria = demuxer_criteria_] {
        RTC_DCHECK_RUN_ON(network_thread());
        RTC_DCHECK(rtp_transport_);
        bool result =
            rtp_transport_->RegisterRtpDemuxerSink(demuxer_criteria, this);
        if (result) {
          previous_demuxer_criteria_ = demuxer_criteria;
        } else {
          previous_demuxer_criteria_ = {};
        }
        media_channel_->OnDemuxerCriteriaUpdateComplete();
        return result;
      });
}

void BaseChannel::EnableMedia_w() {
  if (enabled_)
    return;

  RTC_LOG(LS_INFO) << "Channel enabled: " << ToString();
  enabled_ = true;
  UpdateMediaSendRecvState_w();
}

void BaseChannel::DisableMedia_w() {
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
  if (writable_) {
    return;
  }
  writable_ = true;
  RTC_LOG(LS_INFO) << "Channel writable (" << ToString() << ")"
                   << (was_ever_writable_n_ ? "" : " for the first time");
  // We only have to do this PostTask once, when first transitioning to
  // writable.
  if (!was_ever_writable_n_) {
    worker_thread_->PostTask(ToQueuedTask(alive_, [this] {
      RTC_DCHECK_RUN_ON(worker_thread());
      was_ever_writable_ = true;
      UpdateMediaSendRecvState_w();
    }));
  }
  was_ever_writable_n_ = true;
}

void BaseChannel::ChannelNotWritable_n() {
  if (!writable_) {
    return;
  }
  writable_ = false;
  RTC_LOG(LS_INFO) << "Channel not writable (" << ToString() << ")";
}

bool BaseChannel::AddRecvStream_w(const StreamParams& sp) {
  return media_channel()->AddRecvStream(sp);
}

bool BaseChannel::RemoveRecvStream_w(uint32_t ssrc) {
  return media_channel()->RemoveRecvStream(ssrc);
}

void BaseChannel::ResetUnsignaledRecvStream_w() {
  media_channel()->ResetUnsignaledRecvStream();
}

bool BaseChannel::SetPayloadTypeDemuxingEnabled_w(bool enabled) {
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
    if (!RegisterRtpDemuxerSink_w()) {
      RTC_LOG(LS_ERROR) << "Failed to disable payload type demuxing for "
                        << ToString();
      return false;
    }
  } else if (!payload_types_.empty()) {
    demuxer_criteria_.payload_types.insert(payload_types_.begin(),
                                           payload_types_.end());
    if (!RegisterRtpDemuxerSink_w()) {
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
  if (!RegisterRtpDemuxerSink_w()) {
    RTC_LOG(LS_ERROR) << "Failed to set up demuxing for " << ToString();
    ret = false;
  }
  remote_streams_ = streams;
  return ret;
}

RtpHeaderExtensions BaseChannel::GetFilteredRtpHeaderExtensions(
    const RtpHeaderExtensions& extensions) {
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

void BaseChannel::SignalSentPacket_n(const rtc::SentPacket& sent_packet) {
  RTC_DCHECK_RUN_ON(network_thread());
  media_channel()->OnPacketSent(sent_packet);
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

void VoiceChannel::UpdateMediaSendRecvState_w() {
  // Render incoming data if we're the active call, and we have the local
  // content. We receive data on the default channel and multiplexed streams.
  RTC_DCHECK_RUN_ON(worker_thread());
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

  RtpHeaderExtensions rtp_header_extensions =
      GetFilteredRtpHeaderExtensions(content->rtp_header_extensions());
  // TODO(tommi): There's a hop to the network thread here.
  // some of the below is also network thread related.
  UpdateRtpHeaderExtensionMap(rtp_header_extensions);
  media_channel()->SetExtmapAllowMixed(content->extmap_allow_mixed());

  AudioRecvParameters recv_params = last_recv_params_;
  RtpParametersFromMediaDescription(
      content->as_audio(), rtp_header_extensions,
      webrtc::RtpTransceiverDirectionHasRecv(content->direction()),
      &recv_params);

  if (!media_channel()->SetRecvParameters(recv_params)) {
    SafeSetError(
        "Failed to set local audio description recv parameters for m-section "
        "with mid='" +
            content_name() + "'.",
        error_desc);
    return false;
  }

  if (webrtc::RtpTransceiverDirectionHasRecv(content->direction())) {
    for (const AudioCodec& codec : content->as_audio()->codecs()) {
      MaybeAddHandledPayloadType(codec.id);
    }
    // Need to re-register the sink to update the handled payload.
    if (!RegisterRtpDemuxerSink_w()) {
      RTC_LOG(LS_ERROR) << "Failed to set up audio demuxing for " << ToString();
      return false;
    }
  }

  last_recv_params_ = recv_params;

  // TODO(pthatcher): Move local streams into AudioSendParameters, and
  // only give it to the media channel once we have a remote
  // description too (without a remote description, we won't be able
  // to send them anyway).
  if (!UpdateLocalStreams_w(content->as_audio()->streams(), type, error_desc)) {
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
    if (!RegisterRtpDemuxerSink_w()) {
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
  RTC_DCHECK_RUN_ON(worker_thread());
  bool send = IsReadyToSendMedia_w();
  if (!media_channel()->SetSend(send)) {
    RTC_LOG(LS_ERROR) << "Failed to SetSend on video channel: " + ToString();
    // TODO(gangji): Report error back to server.
  }

  RTC_LOG(LS_INFO) << "Changing video state, send=" << send << " for "
                   << ToString();
}

void VideoChannel::FillBitrateInfo(BandwidthEstimationInfo* bwe_info) {
  RTC_DCHECK_RUN_ON(worker_thread());
  VideoMediaChannel* mc = media_channel();
  mc->FillBitrateInfo(bwe_info);
}

bool VideoChannel::SetLocalContent_w(const MediaContentDescription* content,
                                     SdpType type,
                                     std::string* error_desc) {
  TRACE_EVENT0("webrtc", "VideoChannel::SetLocalContent_w");
  RTC_DCHECK_RUN_ON(worker_thread());
  RTC_LOG(LS_INFO) << "Setting local video description for " << ToString();

  RtpHeaderExtensions rtp_header_extensions =
      GetFilteredRtpHeaderExtensions(content->rtp_header_extensions());
  UpdateRtpHeaderExtensionMap(rtp_header_extensions);
  media_channel()->SetExtmapAllowMixed(content->extmap_allow_mixed());

  VideoRecvParameters recv_params = last_recv_params_;

  RtpParametersFromMediaDescription(
      content->as_video(), rtp_header_extensions,
      webrtc::RtpTransceiverDirectionHasRecv(content->direction()),
      &recv_params);

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

  if (webrtc::RtpTransceiverDirectionHasRecv(content->direction())) {
    for (const VideoCodec& codec : content->as_video()->codecs()) {
      MaybeAddHandledPayloadType(codec.id);
    }
    // Need to re-register the sink to update the handled payload.
    if (!RegisterRtpDemuxerSink_w()) {
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
  if (!UpdateLocalStreams_w(content->as_video()->streams(), type, error_desc)) {
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
    if (!RegisterRtpDemuxerSink_w()) {
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

}  // namespace cricket
