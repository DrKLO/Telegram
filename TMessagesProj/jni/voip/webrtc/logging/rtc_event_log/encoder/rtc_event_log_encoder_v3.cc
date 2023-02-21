/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/encoder/rtc_event_log_encoder_v3.h"

#include <string>
#include <vector>

#include "absl/types/optional.h"
#include "logging/rtc_event_log/encoder/rtc_event_log_encoder_common.h"
#include "logging/rtc_event_log/encoder/var_int.h"
#include "logging/rtc_event_log/events/rtc_event_alr_state.h"
#include "logging/rtc_event_log/events/rtc_event_audio_network_adaptation.h"
#include "logging/rtc_event_log/events/rtc_event_audio_playout.h"
#include "logging/rtc_event_log/events/rtc_event_audio_receive_stream_config.h"
#include "logging/rtc_event_log/events/rtc_event_audio_send_stream_config.h"
#include "logging/rtc_event_log/events/rtc_event_begin_log.h"
#include "logging/rtc_event_log/events/rtc_event_bwe_update_delay_based.h"
#include "logging/rtc_event_log/events/rtc_event_bwe_update_loss_based.h"
#include "logging/rtc_event_log/events/rtc_event_dtls_transport_state.h"
#include "logging/rtc_event_log/events/rtc_event_dtls_writable_state.h"
#include "logging/rtc_event_log/events/rtc_event_end_log.h"
#include "logging/rtc_event_log/events/rtc_event_frame_decoded.h"
#include "logging/rtc_event_log/events/rtc_event_generic_ack_received.h"
#include "logging/rtc_event_log/events/rtc_event_generic_packet_received.h"
#include "logging/rtc_event_log/events/rtc_event_generic_packet_sent.h"
#include "logging/rtc_event_log/events/rtc_event_ice_candidate_pair.h"
#include "logging/rtc_event_log/events/rtc_event_ice_candidate_pair_config.h"
#include "logging/rtc_event_log/events/rtc_event_probe_cluster_created.h"
#include "logging/rtc_event_log/events/rtc_event_probe_result_failure.h"
#include "logging/rtc_event_log/events/rtc_event_probe_result_success.h"
#include "logging/rtc_event_log/events/rtc_event_remote_estimate.h"
#include "logging/rtc_event_log/events/rtc_event_route_change.h"
#include "logging/rtc_event_log/events/rtc_event_rtcp_packet_incoming.h"
#include "logging/rtc_event_log/events/rtc_event_rtcp_packet_outgoing.h"
#include "logging/rtc_event_log/events/rtc_event_rtp_packet_incoming.h"
#include "logging/rtc_event_log/events/rtc_event_rtp_packet_outgoing.h"
#include "logging/rtc_event_log/events/rtc_event_video_receive_stream_config.h"
#include "logging/rtc_event_log/events/rtc_event_video_send_stream_config.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

std::string RtcEventLogEncoderV3::EncodeLogStart(int64_t timestamp_us,
                                                 int64_t utc_time_us) {
  std::unique_ptr<RtcEventBeginLog> begin_log =
      std::make_unique<RtcEventBeginLog>(Timestamp::Micros(timestamp_us),
                                         Timestamp::Micros(utc_time_us));
  std::vector<const RtcEvent*> batch;
  batch.push_back(begin_log.get());

  std::string encoded_event = RtcEventBeginLog::Encode(batch);

  return encoded_event;
}

std::string RtcEventLogEncoderV3::EncodeLogEnd(int64_t timestamp_us) {
  std::unique_ptr<RtcEventEndLog> end_log =
      std::make_unique<RtcEventEndLog>(Timestamp::Micros(timestamp_us));
  std::vector<const RtcEvent*> batch;
  batch.push_back(end_log.get());

  std::string encoded_event = RtcEventEndLog::Encode(batch);

  return encoded_event;
}

RtcEventLogEncoderV3::RtcEventLogEncoderV3() {
  encoders_[RtcEvent::Type::AlrStateEvent] = RtcEventAlrState::Encode;
  encoders_[RtcEvent::Type::AudioNetworkAdaptation] =
      RtcEventAudioNetworkAdaptation::Encode;
  encoders_[RtcEvent::Type::AudioPlayout] = RtcEventAudioPlayout::Encode;
  encoders_[RtcEvent::Type::AudioReceiveStreamConfig] =
      RtcEventAudioReceiveStreamConfig::Encode;
  encoders_[RtcEvent::Type::AudioSendStreamConfig] =
      RtcEventAudioSendStreamConfig::Encode;
  encoders_[RtcEvent::Type::BweUpdateDelayBased] =
      RtcEventBweUpdateDelayBased::Encode;
  encoders_[RtcEvent::Type::BweUpdateLossBased] =
      RtcEventBweUpdateLossBased::Encode;
  encoders_[RtcEvent::Type::DtlsTransportState] =
      RtcEventDtlsTransportState::Encode;
  encoders_[RtcEvent::Type::DtlsWritableState] =
      RtcEventDtlsWritableState::Encode;
  encoders_[RtcEvent::Type::FrameDecoded] = RtcEventFrameDecoded::Encode;
  encoders_[RtcEvent::Type::GenericAckReceived] =
      RtcEventGenericAckReceived::Encode;
  encoders_[RtcEvent::Type::GenericPacketReceived] =
      RtcEventGenericPacketReceived::Encode;
  encoders_[RtcEvent::Type::GenericPacketSent] =
      RtcEventGenericPacketSent::Encode;
  encoders_[RtcEvent::Type::IceCandidatePairConfig] =
      RtcEventIceCandidatePairConfig::Encode;
  encoders_[RtcEvent::Type::IceCandidatePairEvent] =
      RtcEventIceCandidatePair::Encode;
  encoders_[RtcEvent::Type::ProbeClusterCreated] =
      RtcEventProbeClusterCreated::Encode;
  encoders_[RtcEvent::Type::ProbeResultFailure] =
      RtcEventProbeResultFailure::Encode;
  encoders_[RtcEvent::Type::ProbeResultSuccess] =
      RtcEventProbeResultSuccess::Encode;
  encoders_[RtcEvent::Type::RemoteEstimateEvent] =
      RtcEventRemoteEstimate::Encode;
  encoders_[RtcEvent::Type::RouteChangeEvent] = RtcEventRouteChange::Encode;
  encoders_[RtcEvent::Type::RtcpPacketIncoming] =
      RtcEventRtcpPacketIncoming::Encode;
  encoders_[RtcEvent::Type::RtcpPacketOutgoing] =
      RtcEventRtcpPacketOutgoing::Encode;
  encoders_[RtcEvent::Type::RtpPacketIncoming] =
      RtcEventRtpPacketIncoming::Encode;
  encoders_[RtcEvent::Type::RtpPacketOutgoing] =
      RtcEventRtpPacketOutgoing::Encode;
  encoders_[RtcEvent::Type::VideoReceiveStreamConfig] =
      RtcEventVideoReceiveStreamConfig::Encode;
  encoders_[RtcEvent::Type::VideoSendStreamConfig] =
      RtcEventVideoSendStreamConfig::Encode;
}

std::string RtcEventLogEncoderV3::EncodeBatch(
    std::deque<std::unique_ptr<RtcEvent>>::const_iterator begin,
    std::deque<std::unique_ptr<RtcEvent>>::const_iterator end) {
  struct EventGroupKey {
    // Events are grouped by event type. For compression efficiency,
    // events can optionally have a secondary key, in most cases the
    // SSRC.
    RtcEvent::Type type;
    uint32_t secondary_group_key;

    bool operator<(EventGroupKey other) const {
      return type < other.type ||
             (type == other.type &&
              secondary_group_key < other.secondary_group_key);
    }
  };

  std::map<EventGroupKey, std::vector<const RtcEvent*>> event_groups;

  for (auto it = begin; it != end; ++it) {
    event_groups[{(*it)->GetType(), (*it)->GetGroupKey()}].push_back(it->get());
  }

  std::string encoded_output;
  for (auto& kv : event_groups) {
    auto it = encoders_.find(kv.first.type);
    RTC_DCHECK(it != encoders_.end());
    if (it != encoders_.end()) {
      auto& encoder = it->second;
      // TODO(terelius): Use some "string builder" or preallocate?
      encoded_output += encoder(kv.second);
    }
  }

  return encoded_output;
}

}  // namespace webrtc
