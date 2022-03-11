/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/encoder/rtc_event_log_encoder_legacy.h"

#include <string.h>

#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/network_state_predictor.h"
#include "api/rtp_headers.h"
#include "api/rtp_parameters.h"
#include "api/transport/network_types.h"
#include "logging/rtc_event_log/events/rtc_event_alr_state.h"
#include "logging/rtc_event_log/events/rtc_event_audio_network_adaptation.h"
#include "logging/rtc_event_log/events/rtc_event_audio_playout.h"
#include "logging/rtc_event_log/events/rtc_event_audio_receive_stream_config.h"
#include "logging/rtc_event_log/events/rtc_event_audio_send_stream_config.h"
#include "logging/rtc_event_log/events/rtc_event_bwe_update_delay_based.h"
#include "logging/rtc_event_log/events/rtc_event_bwe_update_loss_based.h"
#include "logging/rtc_event_log/events/rtc_event_ice_candidate_pair.h"
#include "logging/rtc_event_log/events/rtc_event_ice_candidate_pair_config.h"
#include "logging/rtc_event_log/events/rtc_event_probe_cluster_created.h"
#include "logging/rtc_event_log/events/rtc_event_probe_result_failure.h"
#include "logging/rtc_event_log/events/rtc_event_probe_result_success.h"
#include "logging/rtc_event_log/events/rtc_event_rtcp_packet_incoming.h"
#include "logging/rtc_event_log/events/rtc_event_rtcp_packet_outgoing.h"
#include "logging/rtc_event_log/events/rtc_event_rtp_packet_incoming.h"
#include "logging/rtc_event_log/events/rtc_event_rtp_packet_outgoing.h"
#include "logging/rtc_event_log/events/rtc_event_video_receive_stream_config.h"
#include "logging/rtc_event_log/events/rtc_event_video_send_stream_config.h"
#include "logging/rtc_event_log/rtc_stream_config.h"
#include "modules/audio_coding/audio_network_adaptor/include/audio_network_adaptor_config.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/rtp_rtcp/source/rtcp_packet/app.h"
#include "modules/rtp_rtcp/source/rtcp_packet/bye.h"
#include "modules/rtp_rtcp/source/rtcp_packet/common_header.h"
#include "modules/rtp_rtcp/source/rtcp_packet/extended_jitter_report.h"
#include "modules/rtp_rtcp/source/rtcp_packet/extended_reports.h"
#include "modules/rtp_rtcp/source/rtcp_packet/psfb.h"
#include "modules/rtp_rtcp/source/rtcp_packet/receiver_report.h"
#include "modules/rtp_rtcp/source/rtcp_packet/rtpfb.h"
#include "modules/rtp_rtcp/source/rtcp_packet/sdes.h"
#include "modules/rtp_rtcp/source/rtcp_packet/sender_report.h"
#include "modules/rtp_rtcp/source/rtp_packet.h"
#include "rtc_base/checks.h"
#include "rtc_base/ignore_wundef.h"
#include "rtc_base/logging.h"

// *.pb.h files are generated at build-time by the protobuf compiler.
RTC_PUSH_IGNORING_WUNDEF()
#ifdef WEBRTC_ANDROID_PLATFORM_BUILD
#include "external/webrtc/webrtc/logging/rtc_event_log/rtc_event_log.pb.h"
#else
#include "logging/rtc_event_log/rtc_event_log.pb.h"
#endif
RTC_POP_IGNORING_WUNDEF()

namespace webrtc {

namespace {
rtclog::DelayBasedBweUpdate::DetectorState ConvertDetectorState(
    BandwidthUsage state) {
  switch (state) {
    case BandwidthUsage::kBwNormal:
      return rtclog::DelayBasedBweUpdate::BWE_NORMAL;
    case BandwidthUsage::kBwUnderusing:
      return rtclog::DelayBasedBweUpdate::BWE_UNDERUSING;
    case BandwidthUsage::kBwOverusing:
      return rtclog::DelayBasedBweUpdate::BWE_OVERUSING;
    case BandwidthUsage::kLast:
      RTC_DCHECK_NOTREACHED();
  }
  RTC_DCHECK_NOTREACHED();
  return rtclog::DelayBasedBweUpdate::BWE_NORMAL;
}

rtclog::BweProbeResult::ResultType ConvertProbeResultType(
    ProbeFailureReason failure_reason) {
  switch (failure_reason) {
    case ProbeFailureReason::kInvalidSendReceiveInterval:
      return rtclog::BweProbeResult::INVALID_SEND_RECEIVE_INTERVAL;
    case ProbeFailureReason::kInvalidSendReceiveRatio:
      return rtclog::BweProbeResult::INVALID_SEND_RECEIVE_RATIO;
    case ProbeFailureReason::kTimeout:
      return rtclog::BweProbeResult::TIMEOUT;
    case ProbeFailureReason::kLast:
      RTC_DCHECK_NOTREACHED();
  }
  RTC_DCHECK_NOTREACHED();
  return rtclog::BweProbeResult::SUCCESS;
}

rtclog::VideoReceiveConfig_RtcpMode ConvertRtcpMode(RtcpMode rtcp_mode) {
  switch (rtcp_mode) {
    case RtcpMode::kCompound:
      return rtclog::VideoReceiveConfig::RTCP_COMPOUND;
    case RtcpMode::kReducedSize:
      return rtclog::VideoReceiveConfig::RTCP_REDUCEDSIZE;
    case RtcpMode::kOff:
      RTC_DCHECK_NOTREACHED();
  }
  RTC_DCHECK_NOTREACHED();
  return rtclog::VideoReceiveConfig::RTCP_COMPOUND;
}

rtclog::IceCandidatePairConfig::IceCandidatePairConfigType
ConvertIceCandidatePairConfigType(IceCandidatePairConfigType type) {
  switch (type) {
    case IceCandidatePairConfigType::kAdded:
      return rtclog::IceCandidatePairConfig::ADDED;
    case IceCandidatePairConfigType::kUpdated:
      return rtclog::IceCandidatePairConfig::UPDATED;
    case IceCandidatePairConfigType::kDestroyed:
      return rtclog::IceCandidatePairConfig::DESTROYED;
    case IceCandidatePairConfigType::kSelected:
      return rtclog::IceCandidatePairConfig::SELECTED;
    case IceCandidatePairConfigType::kNumValues:
      RTC_DCHECK_NOTREACHED();
  }
  RTC_DCHECK_NOTREACHED();
  return rtclog::IceCandidatePairConfig::ADDED;
}

rtclog::IceCandidatePairConfig::IceCandidateType ConvertIceCandidateType(
    IceCandidateType type) {
  switch (type) {
    case IceCandidateType::kUnknown:
      return rtclog::IceCandidatePairConfig::UNKNOWN_CANDIDATE_TYPE;
    case IceCandidateType::kLocal:
      return rtclog::IceCandidatePairConfig::LOCAL;
    case IceCandidateType::kStun:
      return rtclog::IceCandidatePairConfig::STUN;
    case IceCandidateType::kPrflx:
      return rtclog::IceCandidatePairConfig::PRFLX;
    case IceCandidateType::kRelay:
      return rtclog::IceCandidatePairConfig::RELAY;
    case IceCandidateType::kNumValues:
      RTC_DCHECK_NOTREACHED();
  }
  RTC_DCHECK_NOTREACHED();
  return rtclog::IceCandidatePairConfig::UNKNOWN_CANDIDATE_TYPE;
}

rtclog::IceCandidatePairConfig::Protocol ConvertIceCandidatePairProtocol(
    IceCandidatePairProtocol protocol) {
  switch (protocol) {
    case IceCandidatePairProtocol::kUnknown:
      return rtclog::IceCandidatePairConfig::UNKNOWN_PROTOCOL;
    case IceCandidatePairProtocol::kUdp:
      return rtclog::IceCandidatePairConfig::UDP;
    case IceCandidatePairProtocol::kTcp:
      return rtclog::IceCandidatePairConfig::TCP;
    case IceCandidatePairProtocol::kSsltcp:
      return rtclog::IceCandidatePairConfig::SSLTCP;
    case IceCandidatePairProtocol::kTls:
      return rtclog::IceCandidatePairConfig::TLS;
    case IceCandidatePairProtocol::kNumValues:
      RTC_DCHECK_NOTREACHED();
  }
  RTC_DCHECK_NOTREACHED();
  return rtclog::IceCandidatePairConfig::UNKNOWN_PROTOCOL;
}

rtclog::IceCandidatePairConfig::AddressFamily
ConvertIceCandidatePairAddressFamily(
    IceCandidatePairAddressFamily address_family) {
  switch (address_family) {
    case IceCandidatePairAddressFamily::kUnknown:
      return rtclog::IceCandidatePairConfig::UNKNOWN_ADDRESS_FAMILY;
    case IceCandidatePairAddressFamily::kIpv4:
      return rtclog::IceCandidatePairConfig::IPV4;
    case IceCandidatePairAddressFamily::kIpv6:
      return rtclog::IceCandidatePairConfig::IPV6;
    case IceCandidatePairAddressFamily::kNumValues:
      RTC_DCHECK_NOTREACHED();
  }
  RTC_DCHECK_NOTREACHED();
  return rtclog::IceCandidatePairConfig::UNKNOWN_ADDRESS_FAMILY;
}

rtclog::IceCandidatePairConfig::NetworkType ConvertIceCandidateNetworkType(
    IceCandidateNetworkType network_type) {
  switch (network_type) {
    case IceCandidateNetworkType::kUnknown:
      return rtclog::IceCandidatePairConfig::UNKNOWN_NETWORK_TYPE;
    case IceCandidateNetworkType::kEthernet:
      return rtclog::IceCandidatePairConfig::ETHERNET;
    case IceCandidateNetworkType::kLoopback:
      return rtclog::IceCandidatePairConfig::LOOPBACK;
    case IceCandidateNetworkType::kWifi:
      return rtclog::IceCandidatePairConfig::WIFI;
    case IceCandidateNetworkType::kVpn:
      return rtclog::IceCandidatePairConfig::VPN;
    case IceCandidateNetworkType::kCellular:
      return rtclog::IceCandidatePairConfig::CELLULAR;
    case IceCandidateNetworkType::kNumValues:
      RTC_DCHECK_NOTREACHED();
  }
  RTC_DCHECK_NOTREACHED();
  return rtclog::IceCandidatePairConfig::UNKNOWN_NETWORK_TYPE;
}

rtclog::IceCandidatePairEvent::IceCandidatePairEventType
ConvertIceCandidatePairEventType(IceCandidatePairEventType type) {
  switch (type) {
    case IceCandidatePairEventType::kCheckSent:
      return rtclog::IceCandidatePairEvent::CHECK_SENT;
    case IceCandidatePairEventType::kCheckReceived:
      return rtclog::IceCandidatePairEvent::CHECK_RECEIVED;
    case IceCandidatePairEventType::kCheckResponseSent:
      return rtclog::IceCandidatePairEvent::CHECK_RESPONSE_SENT;
    case IceCandidatePairEventType::kCheckResponseReceived:
      return rtclog::IceCandidatePairEvent::CHECK_RESPONSE_RECEIVED;
    case IceCandidatePairEventType::kNumValues:
      RTC_DCHECK_NOTREACHED();
  }
  RTC_DCHECK_NOTREACHED();
  return rtclog::IceCandidatePairEvent::CHECK_SENT;
}

}  // namespace

std::string RtcEventLogEncoderLegacy::EncodeLogStart(int64_t timestamp_us,
                                                     int64_t utc_time_us) {
  rtclog::Event rtclog_event;
  rtclog_event.set_timestamp_us(timestamp_us);
  rtclog_event.set_type(rtclog::Event::LOG_START);
  return Serialize(&rtclog_event);
}

std::string RtcEventLogEncoderLegacy::EncodeLogEnd(int64_t timestamp_us) {
  rtclog::Event rtclog_event;
  rtclog_event.set_timestamp_us(timestamp_us);
  rtclog_event.set_type(rtclog::Event::LOG_END);
  return Serialize(&rtclog_event);
}

std::string RtcEventLogEncoderLegacy::EncodeBatch(
    std::deque<std::unique_ptr<RtcEvent>>::const_iterator begin,
    std::deque<std::unique_ptr<RtcEvent>>::const_iterator end) {
  std::string encoded_output;
  for (auto it = begin; it != end; ++it) {
    // TODO(terelius): Can we avoid the slight inefficiency of reallocating the
    // string?
    RTC_CHECK(it->get() != nullptr);
    encoded_output += Encode(**it);
  }
  return encoded_output;
}

std::string RtcEventLogEncoderLegacy::Encode(const RtcEvent& event) {
  switch (event.GetType()) {
    case RtcEvent::Type::AudioNetworkAdaptation: {
      auto& rtc_event =
          static_cast<const RtcEventAudioNetworkAdaptation&>(event);
      return EncodeAudioNetworkAdaptation(rtc_event);
    }

    case RtcEvent::Type::AlrStateEvent: {
      auto& rtc_event = static_cast<const RtcEventAlrState&>(event);
      return EncodeAlrState(rtc_event);
    }

    case RtcEvent::Type::AudioPlayout: {
      auto& rtc_event = static_cast<const RtcEventAudioPlayout&>(event);
      return EncodeAudioPlayout(rtc_event);
    }

    case RtcEvent::Type::AudioReceiveStreamConfig: {
      auto& rtc_event =
          static_cast<const RtcEventAudioReceiveStreamConfig&>(event);
      return EncodeAudioReceiveStreamConfig(rtc_event);
    }

    case RtcEvent::Type::AudioSendStreamConfig: {
      auto& rtc_event =
          static_cast<const RtcEventAudioSendStreamConfig&>(event);
      return EncodeAudioSendStreamConfig(rtc_event);
    }

    case RtcEvent::Type::BweUpdateDelayBased: {
      auto& rtc_event = static_cast<const RtcEventBweUpdateDelayBased&>(event);
      return EncodeBweUpdateDelayBased(rtc_event);
    }

    case RtcEvent::Type::BweUpdateLossBased: {
      auto& rtc_event = static_cast<const RtcEventBweUpdateLossBased&>(event);
      return EncodeBweUpdateLossBased(rtc_event);
    }

    case RtcEvent::Type::DtlsTransportState: {
      return "";
    }

    case RtcEvent::Type::DtlsWritableState: {
      return "";
    }

    case RtcEvent::Type::IceCandidatePairConfig: {
      auto& rtc_event =
          static_cast<const RtcEventIceCandidatePairConfig&>(event);
      return EncodeIceCandidatePairConfig(rtc_event);
    }

    case RtcEvent::Type::IceCandidatePairEvent: {
      auto& rtc_event = static_cast<const RtcEventIceCandidatePair&>(event);
      return EncodeIceCandidatePairEvent(rtc_event);
    }

    case RtcEvent::Type::ProbeClusterCreated: {
      auto& rtc_event = static_cast<const RtcEventProbeClusterCreated&>(event);
      return EncodeProbeClusterCreated(rtc_event);
    }

    case RtcEvent::Type::ProbeResultFailure: {
      auto& rtc_event = static_cast<const RtcEventProbeResultFailure&>(event);
      return EncodeProbeResultFailure(rtc_event);
    }

    case RtcEvent::Type::ProbeResultSuccess: {
      auto& rtc_event = static_cast<const RtcEventProbeResultSuccess&>(event);
      return EncodeProbeResultSuccess(rtc_event);
    }

    case RtcEvent::Type::RtcpPacketIncoming: {
      auto& rtc_event = static_cast<const RtcEventRtcpPacketIncoming&>(event);
      return EncodeRtcpPacketIncoming(rtc_event);
    }

    case RtcEvent::Type::RtcpPacketOutgoing: {
      auto& rtc_event = static_cast<const RtcEventRtcpPacketOutgoing&>(event);
      return EncodeRtcpPacketOutgoing(rtc_event);
    }

    case RtcEvent::Type::RtpPacketIncoming: {
      auto& rtc_event = static_cast<const RtcEventRtpPacketIncoming&>(event);
      return EncodeRtpPacketIncoming(rtc_event);
    }

    case RtcEvent::Type::RtpPacketOutgoing: {
      auto& rtc_event = static_cast<const RtcEventRtpPacketOutgoing&>(event);
      return EncodeRtpPacketOutgoing(rtc_event);
    }

    case RtcEvent::Type::VideoReceiveStreamConfig: {
      auto& rtc_event =
          static_cast<const RtcEventVideoReceiveStreamConfig&>(event);
      return EncodeVideoReceiveStreamConfig(rtc_event);
    }

    case RtcEvent::Type::VideoSendStreamConfig: {
      auto& rtc_event =
          static_cast<const RtcEventVideoSendStreamConfig&>(event);
      return EncodeVideoSendStreamConfig(rtc_event);
    }
    case RtcEvent::Type::BeginV3Log:
    case RtcEvent::Type::EndV3Log:
      // These special events are written as part of starting
      // and stopping the log, and only as part of version 3 of the format.
      RTC_DCHECK_NOTREACHED();
      break;
    case RtcEvent::Type::RouteChangeEvent:
    case RtcEvent::Type::RemoteEstimateEvent:
    case RtcEvent::Type::GenericPacketReceived:
    case RtcEvent::Type::GenericPacketSent:
    case RtcEvent::Type::GenericAckReceived:
    case RtcEvent::Type::FrameDecoded:
      // These are unsupported in the old format, but shouldn't crash.
      return "";
  }

  int event_type = static_cast<int>(event.GetType());
  RTC_DCHECK_NOTREACHED() << "Unknown event type (" << event_type << ")";
  return "";
}

std::string RtcEventLogEncoderLegacy::EncodeAlrState(
    const RtcEventAlrState& event) {
  rtclog::Event rtclog_event;
  rtclog_event.set_timestamp_us(event.timestamp_us());
  rtclog_event.set_type(rtclog::Event::ALR_STATE_EVENT);

  auto* alr_state = rtclog_event.mutable_alr_state();
  alr_state->set_in_alr(event.in_alr());
  return Serialize(&rtclog_event);
}

std::string RtcEventLogEncoderLegacy::EncodeAudioNetworkAdaptation(
    const RtcEventAudioNetworkAdaptation& event) {
  rtclog::Event rtclog_event;
  rtclog_event.set_timestamp_us(event.timestamp_us());
  rtclog_event.set_type(rtclog::Event::AUDIO_NETWORK_ADAPTATION_EVENT);

  auto* audio_network_adaptation =
      rtclog_event.mutable_audio_network_adaptation();
  if (event.config().bitrate_bps)
    audio_network_adaptation->set_bitrate_bps(*event.config().bitrate_bps);
  if (event.config().frame_length_ms)
    audio_network_adaptation->set_frame_length_ms(
        *event.config().frame_length_ms);
  if (event.config().uplink_packet_loss_fraction) {
    audio_network_adaptation->set_uplink_packet_loss_fraction(
        *event.config().uplink_packet_loss_fraction);
  }
  if (event.config().enable_fec)
    audio_network_adaptation->set_enable_fec(*event.config().enable_fec);
  if (event.config().enable_dtx)
    audio_network_adaptation->set_enable_dtx(*event.config().enable_dtx);
  if (event.config().num_channels)
    audio_network_adaptation->set_num_channels(*event.config().num_channels);

  return Serialize(&rtclog_event);
}

std::string RtcEventLogEncoderLegacy::EncodeAudioPlayout(
    const RtcEventAudioPlayout& event) {
  rtclog::Event rtclog_event;
  rtclog_event.set_timestamp_us(event.timestamp_us());
  rtclog_event.set_type(rtclog::Event::AUDIO_PLAYOUT_EVENT);

  auto* playout_event = rtclog_event.mutable_audio_playout_event();
  playout_event->set_local_ssrc(event.ssrc());

  return Serialize(&rtclog_event);
}

std::string RtcEventLogEncoderLegacy::EncodeAudioReceiveStreamConfig(
    const RtcEventAudioReceiveStreamConfig& event) {
  rtclog::Event rtclog_event;
  rtclog_event.set_timestamp_us(event.timestamp_us());
  rtclog_event.set_type(rtclog::Event::AUDIO_RECEIVER_CONFIG_EVENT);

  rtclog::AudioReceiveConfig* receiver_config =
      rtclog_event.mutable_audio_receiver_config();
  receiver_config->set_remote_ssrc(event.config().remote_ssrc);
  receiver_config->set_local_ssrc(event.config().local_ssrc);

  for (const auto& e : event.config().rtp_extensions) {
    rtclog::RtpHeaderExtension* extension =
        receiver_config->add_header_extensions();
    extension->set_name(e.uri);
    extension->set_id(e.id);
  }

  return Serialize(&rtclog_event);
}

std::string RtcEventLogEncoderLegacy::EncodeAudioSendStreamConfig(
    const RtcEventAudioSendStreamConfig& event) {
  rtclog::Event rtclog_event;
  rtclog_event.set_timestamp_us(event.timestamp_us());
  rtclog_event.set_type(rtclog::Event::AUDIO_SENDER_CONFIG_EVENT);

  rtclog::AudioSendConfig* sender_config =
      rtclog_event.mutable_audio_sender_config();

  sender_config->set_ssrc(event.config().local_ssrc);

  for (const auto& e : event.config().rtp_extensions) {
    rtclog::RtpHeaderExtension* extension =
        sender_config->add_header_extensions();
    extension->set_name(e.uri);
    extension->set_id(e.id);
  }

  return Serialize(&rtclog_event);
}

std::string RtcEventLogEncoderLegacy::EncodeBweUpdateDelayBased(
    const RtcEventBweUpdateDelayBased& event) {
  rtclog::Event rtclog_event;
  rtclog_event.set_timestamp_us(event.timestamp_us());
  rtclog_event.set_type(rtclog::Event::DELAY_BASED_BWE_UPDATE);

  auto* bwe_event = rtclog_event.mutable_delay_based_bwe_update();
  bwe_event->set_bitrate_bps(event.bitrate_bps());
  bwe_event->set_detector_state(ConvertDetectorState(event.detector_state()));

  return Serialize(&rtclog_event);
}

std::string RtcEventLogEncoderLegacy::EncodeBweUpdateLossBased(
    const RtcEventBweUpdateLossBased& event) {
  rtclog::Event rtclog_event;
  rtclog_event.set_timestamp_us(event.timestamp_us());
  rtclog_event.set_type(rtclog::Event::LOSS_BASED_BWE_UPDATE);

  auto* bwe_event = rtclog_event.mutable_loss_based_bwe_update();
  bwe_event->set_bitrate_bps(event.bitrate_bps());
  bwe_event->set_fraction_loss(event.fraction_loss());
  bwe_event->set_total_packets(event.total_packets());

  return Serialize(&rtclog_event);
}

std::string RtcEventLogEncoderLegacy::EncodeIceCandidatePairConfig(
    const RtcEventIceCandidatePairConfig& event) {
  rtclog::Event encoded_rtc_event;
  encoded_rtc_event.set_timestamp_us(event.timestamp_us());
  encoded_rtc_event.set_type(rtclog::Event::ICE_CANDIDATE_PAIR_CONFIG);

  auto* encoded_ice_event =
      encoded_rtc_event.mutable_ice_candidate_pair_config();
  encoded_ice_event->set_config_type(
      ConvertIceCandidatePairConfigType(event.type()));
  encoded_ice_event->set_candidate_pair_id(event.candidate_pair_id());
  const auto& desc = event.candidate_pair_desc();
  encoded_ice_event->set_local_candidate_type(
      ConvertIceCandidateType(desc.local_candidate_type));
  encoded_ice_event->set_local_relay_protocol(
      ConvertIceCandidatePairProtocol(desc.local_relay_protocol));
  encoded_ice_event->set_local_network_type(
      ConvertIceCandidateNetworkType(desc.local_network_type));
  encoded_ice_event->set_local_address_family(
      ConvertIceCandidatePairAddressFamily(desc.local_address_family));
  encoded_ice_event->set_remote_candidate_type(
      ConvertIceCandidateType(desc.remote_candidate_type));
  encoded_ice_event->set_remote_address_family(
      ConvertIceCandidatePairAddressFamily(desc.remote_address_family));
  encoded_ice_event->set_candidate_pair_protocol(
      ConvertIceCandidatePairProtocol(desc.candidate_pair_protocol));
  return Serialize(&encoded_rtc_event);
}

std::string RtcEventLogEncoderLegacy::EncodeIceCandidatePairEvent(
    const RtcEventIceCandidatePair& event) {
  rtclog::Event encoded_rtc_event;
  encoded_rtc_event.set_timestamp_us(event.timestamp_us());
  encoded_rtc_event.set_type(rtclog::Event::ICE_CANDIDATE_PAIR_EVENT);

  auto* encoded_ice_event =
      encoded_rtc_event.mutable_ice_candidate_pair_event();
  encoded_ice_event->set_event_type(
      ConvertIceCandidatePairEventType(event.type()));
  encoded_ice_event->set_candidate_pair_id(event.candidate_pair_id());
  return Serialize(&encoded_rtc_event);
}

std::string RtcEventLogEncoderLegacy::EncodeProbeClusterCreated(
    const RtcEventProbeClusterCreated& event) {
  rtclog::Event rtclog_event;
  rtclog_event.set_timestamp_us(event.timestamp_us());
  rtclog_event.set_type(rtclog::Event::BWE_PROBE_CLUSTER_CREATED_EVENT);

  auto* probe_cluster = rtclog_event.mutable_probe_cluster();
  probe_cluster->set_id(event.id());
  probe_cluster->set_bitrate_bps(event.bitrate_bps());
  probe_cluster->set_min_packets(event.min_probes());
  probe_cluster->set_min_bytes(event.min_bytes());

  return Serialize(&rtclog_event);
}

std::string RtcEventLogEncoderLegacy::EncodeProbeResultFailure(
    const RtcEventProbeResultFailure& event) {
  rtclog::Event rtclog_event;
  rtclog_event.set_timestamp_us(event.timestamp_us());
  rtclog_event.set_type(rtclog::Event::BWE_PROBE_RESULT_EVENT);

  auto* probe_result = rtclog_event.mutable_probe_result();
  probe_result->set_id(event.id());
  probe_result->set_result(ConvertProbeResultType(event.failure_reason()));

  return Serialize(&rtclog_event);
}

std::string RtcEventLogEncoderLegacy::EncodeProbeResultSuccess(
    const RtcEventProbeResultSuccess& event) {
  rtclog::Event rtclog_event;
  rtclog_event.set_timestamp_us(event.timestamp_us());
  rtclog_event.set_type(rtclog::Event::BWE_PROBE_RESULT_EVENT);

  auto* probe_result = rtclog_event.mutable_probe_result();
  probe_result->set_id(event.id());
  probe_result->set_result(rtclog::BweProbeResult::SUCCESS);
  probe_result->set_bitrate_bps(event.bitrate_bps());

  return Serialize(&rtclog_event);
}

std::string RtcEventLogEncoderLegacy::EncodeRtcpPacketIncoming(
    const RtcEventRtcpPacketIncoming& event) {
  return EncodeRtcpPacket(event.timestamp_us(), event.packet(), true);
}

std::string RtcEventLogEncoderLegacy::EncodeRtcpPacketOutgoing(
    const RtcEventRtcpPacketOutgoing& event) {
  return EncodeRtcpPacket(event.timestamp_us(), event.packet(), false);
}

std::string RtcEventLogEncoderLegacy::EncodeRtpPacketIncoming(
    const RtcEventRtpPacketIncoming& event) {
  return EncodeRtpPacket(event.timestamp_us(), event.RawHeader(),
                         event.packet_length(), PacedPacketInfo::kNotAProbe,
                         true);
}

std::string RtcEventLogEncoderLegacy::EncodeRtpPacketOutgoing(
    const RtcEventRtpPacketOutgoing& event) {
  return EncodeRtpPacket(event.timestamp_us(), event.RawHeader(),
                         event.packet_length(), event.probe_cluster_id(),
                         false);
}

std::string RtcEventLogEncoderLegacy::EncodeVideoReceiveStreamConfig(
    const RtcEventVideoReceiveStreamConfig& event) {
  rtclog::Event rtclog_event;
  rtclog_event.set_timestamp_us(event.timestamp_us());
  rtclog_event.set_type(rtclog::Event::VIDEO_RECEIVER_CONFIG_EVENT);

  rtclog::VideoReceiveConfig* receiver_config =
      rtclog_event.mutable_video_receiver_config();
  receiver_config->set_remote_ssrc(event.config().remote_ssrc);
  receiver_config->set_local_ssrc(event.config().local_ssrc);

  // TODO(perkj): Add field for rsid.
  receiver_config->set_rtcp_mode(ConvertRtcpMode(event.config().rtcp_mode));
  receiver_config->set_remb(event.config().remb);

  for (const auto& e : event.config().rtp_extensions) {
    rtclog::RtpHeaderExtension* extension =
        receiver_config->add_header_extensions();
    extension->set_name(e.uri);
    extension->set_id(e.id);
  }

  for (const auto& d : event.config().codecs) {
    rtclog::DecoderConfig* decoder = receiver_config->add_decoders();
    decoder->set_name(d.payload_name);
    decoder->set_payload_type(d.payload_type);
    if (d.rtx_payload_type != 0) {
      rtclog::RtxMap* rtx = receiver_config->add_rtx_map();
      rtx->set_payload_type(d.payload_type);
      rtx->mutable_config()->set_rtx_ssrc(event.config().rtx_ssrc);
      rtx->mutable_config()->set_rtx_payload_type(d.rtx_payload_type);
    }
  }

  return Serialize(&rtclog_event);
}

std::string RtcEventLogEncoderLegacy::EncodeVideoSendStreamConfig(
    const RtcEventVideoSendStreamConfig& event) {
  rtclog::Event rtclog_event;
  rtclog_event.set_timestamp_us(event.timestamp_us());
  rtclog_event.set_type(rtclog::Event::VIDEO_SENDER_CONFIG_EVENT);

  rtclog::VideoSendConfig* sender_config =
      rtclog_event.mutable_video_sender_config();

  // TODO(perkj): rtclog::VideoSendConfig should only contain one SSRC.
  sender_config->add_ssrcs(event.config().local_ssrc);
  if (event.config().rtx_ssrc != 0) {
    sender_config->add_rtx_ssrcs(event.config().rtx_ssrc);
  }

  for (const auto& e : event.config().rtp_extensions) {
    rtclog::RtpHeaderExtension* extension =
        sender_config->add_header_extensions();
    extension->set_name(e.uri);
    extension->set_id(e.id);
  }

  // TODO(perkj): rtclog::VideoSendConfig should contain many possible codec
  // configurations.
  for (const auto& codec : event.config().codecs) {
    sender_config->set_rtx_payload_type(codec.rtx_payload_type);
    rtclog::EncoderConfig* encoder = sender_config->mutable_encoder();
    encoder->set_name(codec.payload_name);
    encoder->set_payload_type(codec.payload_type);

    if (event.config().codecs.size() > 1) {
      RTC_LOG(LS_WARNING)
          << "LogVideoSendStreamConfig currently only supports one "
             "codec. Logging codec :"
          << codec.payload_name;
      break;
    }
  }

  return Serialize(&rtclog_event);
}

std::string RtcEventLogEncoderLegacy::EncodeRtcpPacket(
    int64_t timestamp_us,
    const rtc::Buffer& packet,
    bool is_incoming) {
  rtclog::Event rtclog_event;
  rtclog_event.set_timestamp_us(timestamp_us);
  rtclog_event.set_type(rtclog::Event::RTCP_EVENT);
  rtclog_event.mutable_rtcp_packet()->set_incoming(is_incoming);

  rtcp::CommonHeader header;
  const uint8_t* block_begin = packet.data();
  const uint8_t* packet_end = packet.data() + packet.size();
  std::vector<uint8_t> buffer(packet.size());
  uint32_t buffer_length = 0;
  while (block_begin < packet_end) {
    if (!header.Parse(block_begin, packet_end - block_begin)) {
      break;  // Incorrect message header.
    }
    const uint8_t* next_block = header.NextPacket();
    uint32_t block_size = next_block - block_begin;
    switch (header.type()) {
      case rtcp::Bye::kPacketType:
      case rtcp::ExtendedJitterReport::kPacketType:
      case rtcp::ExtendedReports::kPacketType:
      case rtcp::Psfb::kPacketType:
      case rtcp::ReceiverReport::kPacketType:
      case rtcp::Rtpfb::kPacketType:
      case rtcp::SenderReport::kPacketType:
        // We log sender reports, receiver reports, bye messages
        // inter-arrival jitter, third-party loss reports, payload-specific
        // feedback and extended reports.
        memcpy(buffer.data() + buffer_length, block_begin, block_size);
        buffer_length += block_size;
        break;
      case rtcp::App::kPacketType:
      case rtcp::Sdes::kPacketType:
      default:
        // We don't log sender descriptions, application defined messages
        // or message blocks of unknown type.
        break;
    }

    block_begin += block_size;
  }
  rtclog_event.mutable_rtcp_packet()->set_packet_data(buffer.data(),
                                                      buffer_length);

  return Serialize(&rtclog_event);
}

std::string RtcEventLogEncoderLegacy::EncodeRtpPacket(
    int64_t timestamp_us,
    rtc::ArrayView<const uint8_t> header,
    size_t packet_length,
    int probe_cluster_id,
    bool is_incoming) {
  rtclog::Event rtclog_event;
  rtclog_event.set_timestamp_us(timestamp_us);
  rtclog_event.set_type(rtclog::Event::RTP_EVENT);

  rtclog_event.mutable_rtp_packet()->set_incoming(is_incoming);
  rtclog_event.mutable_rtp_packet()->set_packet_length(packet_length);
  rtclog_event.mutable_rtp_packet()->set_header(header.data(), header.size());
  if (probe_cluster_id != PacedPacketInfo::kNotAProbe) {
    RTC_DCHECK(!is_incoming);
    rtclog_event.mutable_rtp_packet()->set_probe_cluster_id(probe_cluster_id);
  }

  return Serialize(&rtclog_event);
}

std::string RtcEventLogEncoderLegacy::Serialize(rtclog::Event* event) {
  // Even though we're only serializing a single event during this call, what
  // we intend to get is a list of events, with a tag and length preceding
  // each actual event. To produce that, we serialize a list of a single event.
  // If we later concatenate several results from this function, the result will
  // be a proper concatenation of all those events.

  rtclog::EventStream event_stream;
  event_stream.add_stream();

  // As a tweak, we swap the new event into the event-stream, write that to
  // file, then swap back. This saves on some copying, while making sure that
  // the caller wouldn't be surprised by Serialize() modifying the object.
  rtclog::Event* output_event = event_stream.mutable_stream(0);
  output_event->Swap(event);

  std::string output_string = event_stream.SerializeAsString();
  RTC_DCHECK(!output_string.empty());

  // When the function returns, the original Event will be unchanged.
  output_event->Swap(event);

  return output_string;
}

}  // namespace webrtc
