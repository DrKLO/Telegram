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
#include <string>
#include <type_traits>
#include <utility>

#include "absl/strings/string_view.h"
#include "api/rtp_parameters.h"
#include "api/sequence_checker.h"
#include "api/task_queue/pending_task_safety_flag.h"
#include "api/units/timestamp.h"
#include "media/base/codec.h"
#include "media/base/rid_description.h"
#include "media/base/rtp_utils.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "p2p/base/dtls_transport_internal.h"
#include "pc/rtp_media_utils.h"
#include "rtc_base/checks.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/logging.h"
#include "rtc_base/network_route.h"
#include "rtc_base/strings/string_format.h"
#include "rtc_base/trace_event.h"

namespace cricket {
namespace {

using ::rtc::StringFormat;
using ::rtc::UniqueRandomIdGenerator;
using ::webrtc::PendingTaskSafetyFlag;
using ::webrtc::SdpType;

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
    webrtc::RtpExtension::Filter extensions_filter,
    RtpSendParameters<Codec>* send_params) {
  RtpHeaderExtensions extensions =
      webrtc::RtpExtension::DeduplicateHeaderExtensions(
          desc->rtp_header_extensions(), extensions_filter);
  const bool is_stream_active =
      webrtc::RtpTransceiverDirectionHasRecv(desc->direction());
  RtpParametersFromMediaDescription(desc, extensions, is_stream_active,
                                    send_params);
  send_params->max_bandwidth_bps = desc->bandwidth();
  send_params->extmap_allow_mixed = desc->extmap_allow_mixed();
}

BaseChannel::BaseChannel(rtc::Thread* worker_thread,
                         rtc::Thread* network_thread,
                         rtc::Thread* signaling_thread,
                         std::unique_ptr<MediaChannel> media_channel,
                         absl::string_view mid,
                         bool srtp_required,
                         webrtc::CryptoOptions crypto_options,
                         UniqueRandomIdGenerator* ssrc_generator)
    : worker_thread_(worker_thread),
      network_thread_(network_thread),
      signaling_thread_(signaling_thread),
      alive_(PendingTaskSafetyFlag::Create()),
      srtp_required_(srtp_required),
      extensions_filter_(
          crypto_options.srtp.enable_encrypted_rtp_header_extensions
              ? webrtc::RtpExtension::kPreferEncryptedExtension
              : webrtc::RtpExtension::kDiscardEncryptedExtension),
      media_channel_(std::move(media_channel)),
      demuxer_criteria_(mid),
      ssrc_generator_(ssrc_generator) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  RTC_DCHECK(media_channel_);
  RTC_DCHECK(ssrc_generator_);
  RTC_DLOG(LS_INFO) << "Created channel: " << ToString();
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
  return StringFormat("{mid: %s, media_type: %s}", mid().c_str(),
                      MediaTypeToString(media_channel_->media_type()).c_str());
}

bool BaseChannel::ConnectToRtpTransport_n() {
  RTC_DCHECK(rtp_transport_);
  RTC_DCHECK(media_channel());

  // We don't need to call OnDemuxerCriteriaUpdatePending/Complete because
  // there's no previous criteria to worry about.
  if (!rtp_transport_->RegisterRtpDemuxerSink(demuxer_criteria_, this)) {
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

void BaseChannel::DisconnectFromRtpTransport_n() {
  RTC_DCHECK(rtp_transport_);
  RTC_DCHECK(media_channel());
  rtp_transport_->UnregisterRtpDemuxerSink(this);
  rtp_transport_->SignalReadyToSend.disconnect(this);
  rtp_transport_->SignalNetworkRouteChanged.disconnect(this);
  rtp_transport_->SignalWritableState.disconnect(this);
  rtp_transport_->SignalSentPacket.disconnect(this);
  rtp_transport_ = nullptr;
  media_channel_->SetInterface(nullptr);
}

bool BaseChannel::SetRtpTransport(webrtc::RtpTransportInternal* rtp_transport) {
  TRACE_EVENT0("webrtc", "BaseChannel::SetRtpTransport");
  RTC_DCHECK_RUN_ON(network_thread());
  if (rtp_transport == rtp_transport_) {
    return true;
  }

  if (rtp_transport_) {
    DisconnectFromRtpTransport_n();
    // Clear the cached header extensions on the worker.
    worker_thread_->PostTask(SafeTask(alive_, [this] {
      RTC_DCHECK_RUN_ON(worker_thread());
      rtp_header_extensions_.clear();
    }));
  }

  rtp_transport_ = rtp_transport;
  if (rtp_transport_) {
    if (!ConnectToRtpTransport_n()) {
      return false;
    }

    RTC_DCHECK(!media_channel_->HasNetworkInterface());
    media_channel_->SetInterface(this);

    media_channel_->OnReadyToSend(rtp_transport_->IsReadyToSend());
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

  worker_thread_->PostTask(SafeTask(alive_, [this, enable] {
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
                                  std::string& error_desc) {
  RTC_DCHECK_RUN_ON(worker_thread());
  TRACE_EVENT0("webrtc", "BaseChannel::SetLocalContent");
  return SetLocalContent_w(content, type, error_desc);
}

bool BaseChannel::SetRemoteContent(const MediaContentDescription* content,
                                   SdpType type,
                                   std::string& error_desc) {
  RTC_DCHECK_RUN_ON(worker_thread());
  TRACE_EVENT0("webrtc", "BaseChannel::SetRemoteContent");
  return SetRemoteContent_w(content, type, error_desc);
}

bool BaseChannel::SetPayloadTypeDemuxingEnabled(bool enabled) {
  // TODO(bugs.webrtc.org/11993): The demuxer state needs to be managed on the
  // network thread. At the moment there's a workaround for inconsistent state
  // between the worker and network thread because of this (see
  // OnDemuxerCriteriaUpdatePending elsewhere in this file) and
  // SetPayloadTypeDemuxingEnabled_w has a BlockingCall over to the network
  // thread to apply state updates.
  RTC_DCHECK_RUN_ON(worker_thread());
  TRACE_EVENT0("webrtc", "BaseChannel::SetPayloadTypeDemuxingEnabled");
  return SetPayloadTypeDemuxingEnabled_w(enabled);
}

bool BaseChannel::IsReadyToSendMedia_w() const {
  // Send outgoing data if we are enabled, have local and remote content,
  // and we have had some form of connectivity.
  return enabled_ &&
         webrtc::RtpTransceiverDirectionHasRecv(remote_content_direction_) &&
         webrtc::RtpTransceiverDirectionHasSend(local_content_direction_) &&
         was_ever_writable_;
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
  RTC_DCHECK(network_initialized());
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
  RTC_DCHECK(network_initialized());
  if (writable) {
    ChannelWritable_n();
  } else {
    ChannelNotWritable_n();
  }
}

void BaseChannel::OnNetworkRouteChanged(
    absl::optional<rtc::NetworkRoute> network_route) {
  RTC_DCHECK_RUN_ON(network_thread());
  RTC_DCHECK(network_initialized());

  RTC_LOG(LS_INFO) << "Network route changed for " << ToString();

  rtc::NetworkRoute new_route;
  if (network_route) {
    new_route = *(network_route);
  }
  // Note: When the RTCP-muxing is not enabled, RTCP transport and RTP transport
  // use the same transport name and MediaChannel::OnNetworkRouteChanged cannot
  // work correctly. Intentionally leave it broken to simplify the code and
  // encourage the users to stop using non-muxing RTCP.
  media_channel_->OnNetworkRouteChanged(transport_name(), new_route);
}

void BaseChannel::SetFirstPacketReceivedCallback(
    std::function<void()> callback) {
  RTC_DCHECK_RUN_ON(network_thread());
  RTC_DCHECK(!on_first_packet_received_ || !callback);

  // TODO(bugs.webrtc.org/11992): Rename SetFirstPacketReceivedCallback to
  // something that indicates network thread initialization/uninitialization and
  // call Init_n() / Deinit_n() respectively.
  // if (!callback)
  //   Deinit_n();

  on_first_packet_received_ = std::move(callback);
}

void BaseChannel::OnTransportReadyToSend(bool ready) {
  RTC_DCHECK_RUN_ON(network_thread());
  RTC_DCHECK(network_initialized());
  media_channel_->OnReadyToSend(ready);
}

bool BaseChannel::SendPacket(bool rtcp,
                             rtc::CopyOnWriteBuffer* packet,
                             const rtc::PacketOptions& options) {
  RTC_DCHECK_RUN_ON(network_thread());
  RTC_DCHECK(network_initialized());
  TRACE_EVENT0("webrtc", "BaseChannel::SendPacket");

  // Until all the code is migrated to use RtpPacketType instead of bool.
  RtpPacketType packet_type = rtcp ? RtpPacketType::kRtcp : RtpPacketType::kRtp;

  // Ensure we have a place to send this packet before doing anything. We might
  // get RTCP packets that we don't intend to send. If we've negotiated RTCP
  // mux, send RTCP over the RTP transport.
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
      // However, there shouldn't be any RTP packets sent before SRTP is set
      // up (and SetSend(true) is called).
      RTC_DCHECK(rtcp) << "Can't send outgoing RTP packet for " << ToString()
                       << " when SRTP is inactive and crypto is required";
      return false;
    }

    RTC_DLOG(LS_WARNING) << "Sending an " << (rtcp ? "RTCP" : "RTP")
                         << " packet without encryption for " << ToString()
                         << ".";
  }

  return rtcp ? rtp_transport_->SendRtcpPacket(packet, options, PF_SRTP_BYPASS)
              : rtp_transport_->SendRtpPacket(packet, options, PF_SRTP_BYPASS);
}

void BaseChannel::OnRtpPacket(const webrtc::RtpPacketReceived& parsed_packet) {
  RTC_DCHECK_RUN_ON(network_thread());
  RTC_DCHECK(network_initialized());

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
  if (media_channel_) {
    media_channel_->OnPacketReceived(
            parsed_packet.Buffer(),
            packet_time.IsMinusInfinity() ? -1 : packet_time.us());
  }
}

bool BaseChannel::MaybeUpdateDemuxerAndRtpExtensions_w(
    bool update_demuxer,
    absl::optional<RtpHeaderExtensions> extensions,
    std::string& error_desc) {
  if (extensions) {
    if (rtp_header_extensions_ == extensions) {
      extensions.reset();  // No need to update header extensions.
    } else {
      rtp_header_extensions_ = *extensions;
    }
  }

  if (!update_demuxer && !extensions)
    return true;  // No update needed.

  // TODO(bugs.webrtc.org/13536): See if we can do this asynchronously.

  if (update_demuxer)
    media_channel()->OnDemuxerCriteriaUpdatePending();

  bool success = network_thread()->BlockingCall([&]() mutable {
    RTC_DCHECK_RUN_ON(network_thread());
    // NOTE: This doesn't take the BUNDLE case in account meaning the RTP header
    // extension maps are not merged when BUNDLE is enabled. This is fine
    // because the ID for MID should be consistent among all the RTP transports.
    if (extensions)
      rtp_transport_->UpdateRtpHeaderExtensionMap(*extensions);

    if (!update_demuxer)
      return true;

    if (!rtp_transport_->RegisterRtpDemuxerSink(demuxer_criteria_, this)) {
      error_desc =
          StringFormat("Failed to apply demuxer criteria for '%s': '%s'.",
                       mid().c_str(), demuxer_criteria_.ToString().c_str());
      return false;
    }
    return true;
  });

  if (update_demuxer)
    media_channel()->OnDemuxerCriteriaUpdateComplete();

  return success;
}

bool BaseChannel::RegisterRtpDemuxerSink_w() {
  media_channel_->OnDemuxerCriteriaUpdatePending();
  // Copy demuxer criteria, since they're a worker-thread variable
  // and we want to pass them to the network thread
  bool ret = network_thread_->BlockingCall(
      [this, demuxer_criteria = demuxer_criteria_] {
        RTC_DCHECK_RUN_ON(network_thread());
        if (!rtp_transport_) {
          // Transport was disconnected before attempting to update the
          // criteria. This can happen while setting the remote description.
          // See chromium:1295469 for an example.
          return false;
        }
        // Note that RegisterRtpDemuxerSink first unregisters the sink if
        // already registered. So this will change the state of the class
        // whether the call succeeds or not.
        return rtp_transport_->RegisterRtpDemuxerSink(demuxer_criteria, this);
      });

  media_channel_->OnDemuxerCriteriaUpdateComplete();

  return ret;
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
  TRACE_EVENT0("webrtc", "BaseChannel::UpdateWritableState_n");
  if (rtp_transport_->IsWritable(/*rtcp=*/true) &&
      rtp_transport_->IsWritable(/*rtcp=*/false)) {
    ChannelWritable_n();
  } else {
    ChannelNotWritable_n();
  }
}

void BaseChannel::ChannelWritable_n() {
  TRACE_EVENT0("webrtc", "BaseChannel::ChannelWritable_n");
  if (writable_) {
    return;
  }
  writable_ = true;
  RTC_LOG(LS_INFO) << "Channel writable (" << ToString() << ")"
                   << (was_ever_writable_n_ ? "" : " for the first time");
  // We only have to do this PostTask once, when first transitioning to
  // writable.
  if (!was_ever_writable_n_) {
    worker_thread_->PostTask(SafeTask(alive_, [this] {
      RTC_DCHECK_RUN_ON(worker_thread());
      was_ever_writable_ = true;
      UpdateMediaSendRecvState_w();
    }));
  }
  was_ever_writable_n_ = true;
}

void BaseChannel::ChannelNotWritable_n() {
  TRACE_EVENT0("webrtc", "BaseChannel::ChannelNotWritable_n");
  if (!writable_) {
    return;
  }
  writable_ = false;
  RTC_LOG(LS_INFO) << "Channel not writable (" << ToString() << ")";
}

bool BaseChannel::SetPayloadTypeDemuxingEnabled_w(bool enabled) {
  RTC_LOG_THREAD_BLOCK_COUNT();

  if (enabled == payload_type_demuxing_enabled_) {
    return true;
  }

  payload_type_demuxing_enabled_ = enabled;

  bool config_changed = false;

  if (!enabled) {
    // TODO(crbug.com/11477): This will remove *all* unsignaled streams (those
    // without an explicitly signaled SSRC), which may include streams that
    // were matched to this channel by MID or RID. Ideally we'd remove only the
    // streams that were matched based on payload type alone, but currently
    // there is no straightforward way to identify those streams.
    media_channel()->ResetUnsignaledRecvStream();
    if (!demuxer_criteria_.payload_types().empty()) {
      config_changed = true;
      demuxer_criteria_.payload_types().clear();
    }
  } else if (!payload_types_.empty()) {
    for (const auto& type : payload_types_) {
      if (demuxer_criteria_.payload_types().insert(type).second) {
        config_changed = true;
      }
    }
  } else {
    RTC_DCHECK(demuxer_criteria_.payload_types().empty());
  }

  RTC_DCHECK_BLOCK_COUNT_NO_MORE_THAN(0);

  if (!config_changed)
    return true;

  // Note: This synchronously hops to the network thread.
  return RegisterRtpDemuxerSink_w();
}

bool BaseChannel::UpdateLocalStreams_w(const std::vector<StreamParams>& streams,
                                       SdpType type,
                                       std::string& error_desc) {
  // In the case of RIDs (where SSRCs are not negotiated), this method will
  // generate an SSRC for each layer in StreamParams. That representation will
  // be stored internally in `local_streams_`.
  // In subsequent offers, the same stream can appear in `streams` again
  // (without the SSRCs), so it should be looked up using RIDs (if available)
  // and then by primary SSRC.
  // In both scenarios, it is safe to assume that the media channel will be
  // created with a StreamParams object with SSRCs. However, it is not safe to
  // assume that `local_streams_` will always have SSRCs as there are scenarios
  // in which niether SSRCs or RIDs are negotiated.

  // Check for streams that have been removed.
  bool ret = true;
  for (const StreamParams& old_stream : local_streams_) {
    if (!old_stream.has_ssrcs() ||
        GetStream(streams, StreamFinder(&old_stream))) {
      continue;
    }
    if (!media_channel()->RemoveSendStream(old_stream.first_ssrc())) {
      error_desc = StringFormat(
          "Failed to remove send stream with ssrc %u from m-section with "
          "mid='%s'.",
          old_stream.first_ssrc(), mid().c_str());
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
      error_desc = StringFormat(
          "Failed to add send stream: %u into m-section with mid='%s'. Stream "
          "has both SSRCs and RIDs.",
          new_stream.first_ssrc(), mid().c_str());
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
      error_desc = StringFormat(
          "Failed to add send stream ssrc: %u into m-section with mid='%s'",
          new_stream.first_ssrc(), mid().c_str());
      ret = false;
    }
  }
  local_streams_ = all_streams;
  return ret;
}

bool BaseChannel::UpdateRemoteStreams_w(const MediaContentDescription* content,
                                        SdpType type,
                                        std::string& error_desc) {
  RTC_LOG_THREAD_BLOCK_COUNT();
  bool needs_re_registration = false;
  if (!webrtc::RtpTransceiverDirectionHasSend(content->direction())) {
    RTC_DLOG(LS_VERBOSE) << "UpdateRemoteStreams_w: remote side will not send "
                            "- disable payload type demuxing for "
                         << ToString();
    if (ClearHandledPayloadTypes()) {
      needs_re_registration = payload_type_demuxing_enabled_;
    }
  }

  const std::vector<StreamParams>& streams = content->streams();
  const bool new_has_unsignaled_ssrcs = HasStreamWithNoSsrcs(streams);
  const bool old_has_unsignaled_ssrcs = HasStreamWithNoSsrcs(remote_streams_);

  // Check for streams that have been removed.
  for (const StreamParams& old_stream : remote_streams_) {
    // If we no longer have an unsignaled stream, we would like to remove
    // the unsignaled stream params that are cached.
    if (!old_stream.has_ssrcs() && !new_has_unsignaled_ssrcs) {
      media_channel()->ResetUnsignaledRecvStream();
      RTC_LOG(LS_INFO) << "Reset unsignaled remote stream for " << ToString()
                       << ".";
    } else if (old_stream.has_ssrcs() &&
               !GetStreamBySsrc(streams, old_stream.first_ssrc())) {
      if (media_channel()->RemoveRecvStream(old_stream.first_ssrc())) {
        RTC_LOG(LS_INFO) << "Remove remote ssrc: " << old_stream.first_ssrc()
                         << " from " << ToString() << ".";
      } else {
        error_desc = StringFormat(
            "Failed to remove remote stream with ssrc %u from m-section with "
            "mid='%s'.",
            old_stream.first_ssrc(), mid().c_str());
        return false;
      }
    }
  }

  // Check for new streams.
  webrtc::flat_set<uint32_t> ssrcs;
  for (const StreamParams& new_stream : streams) {
    // We allow a StreamParams with an empty list of SSRCs, in which case the
    // MediaChannel will cache the parameters and use them for any unsignaled
    // stream received later.
    if ((!new_stream.has_ssrcs() && !old_has_unsignaled_ssrcs) ||
        !GetStreamBySsrc(remote_streams_, new_stream.first_ssrc())) {
      if (media_channel()->AddRecvStream(new_stream)) {
        RTC_LOG(LS_INFO) << "Add remote ssrc: "
                         << (new_stream.has_ssrcs()
                                 ? std::to_string(new_stream.first_ssrc())
                                 : "unsignaled")
                         << " to " << ToString();
      } else {
        error_desc =
            StringFormat("Failed to add remote stream ssrc: %s to %s",
                         new_stream.has_ssrcs()
                             ? std::to_string(new_stream.first_ssrc()).c_str()
                             : "unsignaled",
                         ToString().c_str());
        return false;
      }
    }
    // Update the receiving SSRCs.
    ssrcs.insert(new_stream.ssrcs.begin(), new_stream.ssrcs.end());
  }

  if (demuxer_criteria_.ssrcs() != ssrcs) {
    demuxer_criteria_.ssrcs() = std::move(ssrcs);
    needs_re_registration = true;
  }

  RTC_DCHECK_BLOCK_COUNT_NO_MORE_THAN(0);

  // Re-register the sink to update after changing the demuxer criteria.
  if (needs_re_registration && !RegisterRtpDemuxerSink_w()) {
    error_desc = StringFormat("Failed to set up audio demuxing for mid='%s'.",
                              mid().c_str());
    return false;
  }

  remote_streams_ = streams;

  set_remote_content_direction(content->direction());
  UpdateMediaSendRecvState_w();

  RTC_DCHECK_BLOCK_COUNT_NO_MORE_THAN(1);

  return true;
}

RtpHeaderExtensions BaseChannel::GetDeduplicatedRtpHeaderExtensions(
    const RtpHeaderExtensions& extensions) {
  return webrtc::RtpExtension::DeduplicateHeaderExtensions(extensions,
                                                           extensions_filter_);
}

bool BaseChannel::MaybeAddHandledPayloadType(int payload_type) {
  bool demuxer_criteria_modified = false;
  if (payload_type_demuxing_enabled_) {
    demuxer_criteria_modified = demuxer_criteria_.payload_types()
                                    .insert(static_cast<uint8_t>(payload_type))
                                    .second;
  }
  // Even if payload type demuxing is currently disabled, we need to remember
  // the payload types in case it's re-enabled later.
  payload_types_.insert(static_cast<uint8_t>(payload_type));
  return demuxer_criteria_modified;
}

bool BaseChannel::ClearHandledPayloadTypes() {
  const bool was_empty = demuxer_criteria_.payload_types().empty();
  demuxer_criteria_.payload_types().clear();
  payload_types_.clear();
  return !was_empty;
}

void BaseChannel::SignalSentPacket_n(const rtc::SentPacket& sent_packet) {
  RTC_DCHECK_RUN_ON(network_thread());
  RTC_DCHECK(network_initialized());
  media_channel()->OnPacketSent(sent_packet);
}

VoiceChannel::VoiceChannel(rtc::Thread* worker_thread,
                           rtc::Thread* network_thread,
                           rtc::Thread* signaling_thread,
                           std::unique_ptr<VoiceMediaChannel> media_channel,
                           absl::string_view mid,
                           bool srtp_required,
                           webrtc::CryptoOptions crypto_options,
                           UniqueRandomIdGenerator* ssrc_generator)
    : BaseChannel(worker_thread,
                  network_thread,
                  signaling_thread,
                  std::move(media_channel),
                  mid,
                  srtp_required,
                  crypto_options,
                  ssrc_generator) {}

VoiceChannel::~VoiceChannel() {
  TRACE_EVENT0("webrtc", "VoiceChannel::~VoiceChannel");
  // this can't be done in the base class, since it calls a virtual
  DisableMedia_w();
}

void VoiceChannel::UpdateMediaSendRecvState_w() {
  // Render incoming data if we're the active call, and we have the local
  // content. We receive data on the default channel and multiplexed streams.
  bool ready_to_receive = enabled() && webrtc::RtpTransceiverDirectionHasRecv(
                                           local_content_direction());
  media_channel()->SetPlayout(ready_to_receive);

  // Send outgoing data if we're the active call, we have the remote content,
  // and we have had some form of connectivity.
  bool send = IsReadyToSendMedia_w();
  media_channel()->SetSend(send);

  RTC_LOG(LS_INFO) << "Changing voice state, recv=" << ready_to_receive
                   << " send=" << send << " for " << ToString();
}

bool VoiceChannel::SetLocalContent_w(const MediaContentDescription* content,
                                     SdpType type,
                                     std::string& error_desc) {
  TRACE_EVENT0("webrtc", "VoiceChannel::SetLocalContent_w");
  RTC_DLOG(LS_INFO) << "Setting local voice description for " << ToString();

  RTC_LOG_THREAD_BLOCK_COUNT();

  RtpHeaderExtensions header_extensions =
      GetDeduplicatedRtpHeaderExtensions(content->rtp_header_extensions());
  bool update_header_extensions = true;
  media_channel()->SetExtmapAllowMixed(content->extmap_allow_mixed());

  AudioRecvParameters recv_params = last_recv_params_;
  RtpParametersFromMediaDescription(
      content->as_audio(), header_extensions,
      webrtc::RtpTransceiverDirectionHasRecv(content->direction()),
      &recv_params);

  if (!media_channel()->SetRecvParameters(recv_params)) {
    error_desc = StringFormat(
        "Failed to set local audio description recv parameters for m-section "
        "with mid='%s'.",
        mid().c_str());
    return false;
  }

  bool criteria_modified = false;
  if (webrtc::RtpTransceiverDirectionHasRecv(content->direction())) {
    for (const AudioCodec& codec : content->as_audio()->codecs()) {
      if (MaybeAddHandledPayloadType(codec.id)) {
        criteria_modified = true;
      }
    }
  }

  last_recv_params_ = recv_params;

  if (!UpdateLocalStreams_w(content->as_audio()->streams(), type, error_desc)) {
    RTC_DCHECK(!error_desc.empty());
    return false;
  }

  set_local_content_direction(content->direction());
  UpdateMediaSendRecvState_w();

  RTC_DCHECK_BLOCK_COUNT_NO_MORE_THAN(0);

  bool success = MaybeUpdateDemuxerAndRtpExtensions_w(
      criteria_modified,
      update_header_extensions
          ? absl::optional<RtpHeaderExtensions>(std::move(header_extensions))
          : absl::nullopt,
      error_desc);

  RTC_DCHECK_BLOCK_COUNT_NO_MORE_THAN(1);

  return success;
}

bool VoiceChannel::SetRemoteContent_w(const MediaContentDescription* content,
                                      SdpType type,
                                      std::string& error_desc) {
  TRACE_EVENT0("webrtc", "VoiceChannel::SetRemoteContent_w");
  RTC_LOG(LS_INFO) << "Setting remote voice description for " << ToString();

  AudioSendParameters send_params = last_send_params_;
  RtpSendParametersFromMediaDescription(content->as_audio(),
                                        extensions_filter(), &send_params);
  send_params.mid = mid();

  bool parameters_applied = media_channel()->SetSendParameters(send_params);
  if (!parameters_applied) {
    error_desc = StringFormat(
        "Failed to set remote audio description send parameters for m-section "
        "with mid='%s'.",
        mid().c_str());
    return false;
  }
  last_send_params_ = send_params;

  return UpdateRemoteStreams_w(content, type, error_desc);
}

VideoChannel::VideoChannel(rtc::Thread* worker_thread,
                           rtc::Thread* network_thread,
                           rtc::Thread* signaling_thread,
                           std::unique_ptr<VideoMediaChannel> media_channel,
                           absl::string_view mid,
                           bool srtp_required,
                           webrtc::CryptoOptions crypto_options,
                           UniqueRandomIdGenerator* ssrc_generator)
    : BaseChannel(worker_thread,
                  network_thread,
                  signaling_thread,
                  std::move(media_channel),
                  mid,
                  srtp_required,
                  crypto_options,
                  ssrc_generator) {}

VideoChannel::~VideoChannel() {
  TRACE_EVENT0("webrtc", "VideoChannel::~VideoChannel");
  // this can't be done in the base class, since it calls a virtual
  DisableMedia_w();
}

void VideoChannel::UpdateMediaSendRecvState_w() {
  // Send outgoing data if we're the active call, we have the remote content,
  // and we have had some form of connectivity.
  bool send = IsReadyToSendMedia_w();
  media_channel()->SetSend(send);
  RTC_LOG(LS_INFO) << "Changing video state, send=" << send << " for "
                   << ToString();
}

bool VideoChannel::SetLocalContent_w(const MediaContentDescription* content,
                                     SdpType type,
                                     std::string& error_desc) {
  TRACE_EVENT0("webrtc", "VideoChannel::SetLocalContent_w");
  RTC_DLOG(LS_INFO) << "Setting local video description for " << ToString();

  RTC_LOG_THREAD_BLOCK_COUNT();

  RtpHeaderExtensions header_extensions =
      GetDeduplicatedRtpHeaderExtensions(content->rtp_header_extensions());
  bool update_header_extensions = true;
  media_channel()->SetExtmapAllowMixed(content->extmap_allow_mixed());

  VideoRecvParameters recv_params = last_recv_params_;

  RtpParametersFromMediaDescription(
      content->as_video(), header_extensions,
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
          error_desc = StringFormat(
              "Failed to set local answer due to invalid codec packetization "
              "specified in m-section with mid='%s'.",
              mid().c_str());
          return false;
        }
      }
    }
  }

  if (!media_channel()->SetRecvParameters(recv_params)) {
    error_desc = StringFormat(
        "Failed to set local video description recv parameters for m-section "
        "with mid='%s'.",
        mid().c_str());
    return false;
  }

  bool criteria_modified = false;
  if (webrtc::RtpTransceiverDirectionHasRecv(content->direction())) {
    for (const VideoCodec& codec : content->as_video()->codecs()) {
      if (MaybeAddHandledPayloadType(codec.id))
        criteria_modified = true;
    }
  }

  last_recv_params_ = recv_params;

  if (needs_send_params_update) {
    if (!media_channel()->SetSendParameters(send_params)) {
      error_desc = StringFormat(
          "Failed to set send parameters for m-section with mid='%s'.",
          mid().c_str());
      return false;
    }
    last_send_params_ = send_params;
  }

  if (!UpdateLocalStreams_w(content->as_video()->streams(), type, error_desc)) {
    RTC_DCHECK(!error_desc.empty());
    return false;
  }

  set_local_content_direction(content->direction());
  UpdateMediaSendRecvState_w();

  RTC_DCHECK_BLOCK_COUNT_NO_MORE_THAN(0);

  bool success = MaybeUpdateDemuxerAndRtpExtensions_w(
      criteria_modified,
      update_header_extensions
          ? absl::optional<RtpHeaderExtensions>(std::move(header_extensions))
          : absl::nullopt,
      error_desc);

  RTC_DCHECK_BLOCK_COUNT_NO_MORE_THAN(1);

  return success;
}

bool VideoChannel::SetRemoteContent_w(const MediaContentDescription* content,
                                      SdpType type,
                                      std::string& error_desc) {
  TRACE_EVENT0("webrtc", "VideoChannel::SetRemoteContent_w");
  RTC_LOG(LS_INFO) << "Setting remote video description for " << ToString();

  const VideoContentDescription* video = content->as_video();

  VideoSendParameters send_params = last_send_params_;
  RtpSendParametersFromMediaDescription(video, extensions_filter(),
                                        &send_params);
  send_params.mid = mid();
  send_params.conference_mode = video->conference_mode();

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
          error_desc = StringFormat(
              "Failed to set remote answer due to invalid codec packetization "
              "specifid in m-section with mid='%s'.",
              mid().c_str());
          return false;
        }
      }
    }
  }

  if (!media_channel()->SetSendParameters(send_params)) {
    error_desc = StringFormat(
        "Failed to set remote video description send parameters for m-section "
        "with mid='%s'.",
        mid().c_str());
    return false;
  }
  last_send_params_ = send_params;

  if (needs_recv_params_update) {
    if (!media_channel()->SetRecvParameters(recv_params)) {
      error_desc = StringFormat(
          "Failed to set recv parameters for m-section with mid='%s'.",
          mid().c_str());
      return false;
    }
    last_recv_params_ = recv_params;
  }

  return UpdateRemoteStreams_w(content, type, error_desc);
}

}  // namespace cricket
