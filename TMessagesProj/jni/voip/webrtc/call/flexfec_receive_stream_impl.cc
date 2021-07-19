/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "call/flexfec_receive_stream_impl.h"

#include <stddef.h>

#include <cstdint>
#include <string>
#include <vector>

#include "api/array_view.h"
#include "api/call/transport.h"
#include "api/rtp_parameters.h"
#include "call/rtp_stream_receiver_controller_interface.h"
#include "modules/rtp_rtcp/include/flexfec_receiver.h"
#include "modules/rtp_rtcp/include/receive_statistics.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "modules/utility/include/process_thread.h"
#include "rtc_base/checks.h"
#include "rtc_base/location.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {

std::string FlexfecReceiveStream::Stats::ToString(int64_t time_ms) const {
  char buf[1024];
  rtc::SimpleStringBuilder ss(buf);
  ss << "FlexfecReceiveStream stats: " << time_ms
     << ", {flexfec_bitrate_bps: " << flexfec_bitrate_bps << "}";
  return ss.str();
}

std::string FlexfecReceiveStream::Config::ToString() const {
  char buf[1024];
  rtc::SimpleStringBuilder ss(buf);
  ss << "{payload_type: " << payload_type;
  ss << ", remote_ssrc: " << remote_ssrc;
  ss << ", local_ssrc: " << local_ssrc;
  ss << ", protected_media_ssrcs: [";
  size_t i = 0;
  for (; i + 1 < protected_media_ssrcs.size(); ++i)
    ss << protected_media_ssrcs[i] << ", ";
  if (!protected_media_ssrcs.empty())
    ss << protected_media_ssrcs[i];
  ss << "], transport_cc: " << (transport_cc ? "on" : "off");
  ss << ", rtp_header_extensions: [";
  i = 0;
  for (; i + 1 < rtp_header_extensions.size(); ++i)
    ss << rtp_header_extensions[i].ToString() << ", ";
  if (!rtp_header_extensions.empty())
    ss << rtp_header_extensions[i].ToString();
  ss << "]}";
  return ss.str();
}

bool FlexfecReceiveStream::Config::IsCompleteAndEnabled() const {
  // Check if FlexFEC is enabled.
  if (payload_type < 0)
    return false;
  // Do we have the necessary SSRC information?
  if (remote_ssrc == 0)
    return false;
  // TODO(brandtr): Update this check when we support multistream protection.
  if (protected_media_ssrcs.size() != 1u)
    return false;
  return true;
}

namespace {

// TODO(brandtr): Update this function when we support multistream protection.
std::unique_ptr<FlexfecReceiver> MaybeCreateFlexfecReceiver(
    Clock* clock,
    const FlexfecReceiveStream::Config& config,
    RecoveredPacketReceiver* recovered_packet_receiver) {
  if (config.payload_type < 0) {
    RTC_LOG(LS_WARNING)
        << "Invalid FlexFEC payload type given. "
           "This FlexfecReceiveStream will therefore be useless.";
    return nullptr;
  }
  RTC_DCHECK_GE(config.payload_type, 0);
  RTC_DCHECK_LE(config.payload_type, 127);
  if (config.remote_ssrc == 0) {
    RTC_LOG(LS_WARNING)
        << "Invalid FlexFEC SSRC given. "
           "This FlexfecReceiveStream will therefore be useless.";
    return nullptr;
  }
  if (config.protected_media_ssrcs.empty()) {
    RTC_LOG(LS_WARNING)
        << "No protected media SSRC supplied. "
           "This FlexfecReceiveStream will therefore be useless.";
    return nullptr;
  }

  if (config.protected_media_ssrcs.size() > 1) {
    RTC_LOG(LS_WARNING)
        << "The supplied FlexfecConfig contained multiple protected "
           "media streams, but our implementation currently only "
           "supports protecting a single media stream. "
           "To avoid confusion, disabling FlexFEC completely.";
    return nullptr;
  }
  RTC_DCHECK_EQ(1U, config.protected_media_ssrcs.size());
  return std::unique_ptr<FlexfecReceiver>(new FlexfecReceiver(
      clock, config.remote_ssrc, config.protected_media_ssrcs[0],
      recovered_packet_receiver));
}

std::unique_ptr<ModuleRtpRtcpImpl2> CreateRtpRtcpModule(
    Clock* clock,
    ReceiveStatistics* receive_statistics,
    const FlexfecReceiveStreamImpl::Config& config,
    RtcpRttStats* rtt_stats) {
  RtpRtcpInterface::Configuration configuration;
  configuration.audio = false;
  configuration.receiver_only = true;
  configuration.clock = clock;
  configuration.receive_statistics = receive_statistics;
  configuration.outgoing_transport = config.rtcp_send_transport;
  configuration.rtt_stats = rtt_stats;
  configuration.local_media_ssrc = config.local_ssrc;
  return ModuleRtpRtcpImpl2::Create(configuration);
}

}  // namespace

FlexfecReceiveStreamImpl::FlexfecReceiveStreamImpl(
    Clock* clock,
    RtpStreamReceiverControllerInterface* receiver_controller,
    const Config& config,
    RecoveredPacketReceiver* recovered_packet_receiver,
    RtcpRttStats* rtt_stats,
    ProcessThread* process_thread)
    : config_(config),
      receiver_(MaybeCreateFlexfecReceiver(clock,
                                           config_,
                                           recovered_packet_receiver)),
      rtp_receive_statistics_(ReceiveStatistics::Create(clock)),
      rtp_rtcp_(CreateRtpRtcpModule(clock,
                                    rtp_receive_statistics_.get(),
                                    config_,
                                    rtt_stats)),
      process_thread_(process_thread) {
  RTC_LOG(LS_INFO) << "FlexfecReceiveStreamImpl: " << config_.ToString();

  // RTCP reporting.
  rtp_rtcp_->SetRTCPStatus(config_.rtcp_mode);
  process_thread_->RegisterModule(rtp_rtcp_.get(), RTC_FROM_HERE);

  // Register with transport.
  // TODO(nisse): OnRtpPacket in this class delegates all real work to
  // |receiver_|. So maybe we don't need to implement RtpPacketSinkInterface
  // here at all, we'd then delete the OnRtpPacket method and instead register
  // |receiver_| as the RtpPacketSinkInterface for this stream.
  // TODO(nisse): Passing |this| from the constructor to the RtpDemuxer, before
  // the object is fully initialized, is risky. But it works in this case
  // because locking in our caller, Call::CreateFlexfecReceiveStream, ensures
  // that the demuxer doesn't call OnRtpPacket before this object is fully
  // constructed. Registering |receiver_| instead of |this| would solve this
  // problem too.
  rtp_stream_receiver_ =
      receiver_controller->CreateReceiver(config_.remote_ssrc, this);
}

FlexfecReceiveStreamImpl::~FlexfecReceiveStreamImpl() {
  RTC_LOG(LS_INFO) << "~FlexfecReceiveStreamImpl: " << config_.ToString();
  process_thread_->DeRegisterModule(rtp_rtcp_.get());
}

void FlexfecReceiveStreamImpl::OnRtpPacket(const RtpPacketReceived& packet) {
  if (!receiver_)
    return;

  receiver_->OnRtpPacket(packet);

  // Do not report media packets in the RTCP RRs generated by |rtp_rtcp_|.
  if (packet.Ssrc() == config_.remote_ssrc) {
    rtp_receive_statistics_->OnRtpPacket(packet);
  }
}

// TODO(brandtr): Implement this member function when we have designed the
// stats for FlexFEC.
FlexfecReceiveStreamImpl::Stats FlexfecReceiveStreamImpl::GetStats() const {
  return FlexfecReceiveStream::Stats();
}

const FlexfecReceiveStream::Config& FlexfecReceiveStreamImpl::GetConfig()
    const {
  return config_;
}

}  // namespace webrtc
