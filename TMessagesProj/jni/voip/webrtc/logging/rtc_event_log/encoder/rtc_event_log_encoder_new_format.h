/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef LOGGING_RTC_EVENT_LOG_ENCODER_RTC_EVENT_LOG_ENCODER_NEW_FORMAT_H_
#define LOGGING_RTC_EVENT_LOG_ENCODER_RTC_EVENT_LOG_ENCODER_NEW_FORMAT_H_

#include <deque>
#include <map>
#include <memory>
#include <string>
#include <vector>

#include "api/array_view.h"
#include "api/field_trials_view.h"
#include "logging/rtc_event_log/encoder/rtc_event_log_encoder.h"

namespace webrtc {

namespace rtclog2 {
class EventStream;  // Auto-generated from protobuf.
}  // namespace rtclog2

class RtcEventAlrState;
class RtcEventRouteChange;
class RtcEventRemoteEstimate;
class RtcEventAudioNetworkAdaptation;
class RtcEventAudioPlayout;
class RtcEventAudioReceiveStreamConfig;
class RtcEventAudioSendStreamConfig;
class RtcEventBweUpdateDelayBased;
class RtcEventBweUpdateLossBased;
class RtcEventDtlsTransportState;
class RtcEventDtlsWritableState;
class RtcEventLoggingStarted;
class RtcEventLoggingStopped;
class RtcEventNetEqSetMinimumDelay;
class RtcEventProbeClusterCreated;
class RtcEventProbeResultFailure;
class RtcEventProbeResultSuccess;
class RtcEventRtcpPacketIncoming;
class RtcEventRtcpPacketOutgoing;
class RtcEventRtpPacketIncoming;
class RtcEventRtpPacketOutgoing;
class RtcEventVideoReceiveStreamConfig;
class RtcEventVideoSendStreamConfig;
class RtcEventIceCandidatePairConfig;
class RtcEventIceCandidatePair;
class RtpPacket;
class RtcEventFrameDecoded;
class RtcEventGenericAckReceived;
class RtcEventGenericPacketReceived;
class RtcEventGenericPacketSent;

class RtcEventLogEncoderNewFormat final : public RtcEventLogEncoder {
 public:
  explicit RtcEventLogEncoderNewFormat(const FieldTrialsView& field_trials);
  ~RtcEventLogEncoderNewFormat() override = default;

  std::string EncodeBatch(
      std::deque<std::unique_ptr<RtcEvent>>::const_iterator begin,
      std::deque<std::unique_ptr<RtcEvent>>::const_iterator end) override;

  std::string EncodeLogStart(int64_t timestamp_us,
                             int64_t utc_time_us) override;
  std::string EncodeLogEnd(int64_t timestamp_us) override;

 private:
  // Encoding entry-point for the various RtcEvent subclasses.
  void EncodeAlrState(rtc::ArrayView<const RtcEventAlrState*> batch,
                      rtclog2::EventStream* event_stream);
  void EncodeAudioNetworkAdaptation(
      rtc::ArrayView<const RtcEventAudioNetworkAdaptation*> batch,
      rtclog2::EventStream* event_stream);
  void EncodeAudioPlayout(rtc::ArrayView<const RtcEventAudioPlayout*> batch,
                          rtclog2::EventStream* event_stream);
  void EncodeAudioRecvStreamConfig(
      rtc::ArrayView<const RtcEventAudioReceiveStreamConfig*> batch,
      rtclog2::EventStream* event_stream);
  void EncodeAudioSendStreamConfig(
      rtc::ArrayView<const RtcEventAudioSendStreamConfig*> batch,
      rtclog2::EventStream* event_stream);
  void EncodeBweUpdateDelayBased(
      rtc::ArrayView<const RtcEventBweUpdateDelayBased*> batch,
      rtclog2::EventStream* event_stream);
  void EncodeBweUpdateLossBased(
      rtc::ArrayView<const RtcEventBweUpdateLossBased*> batch,
      rtclog2::EventStream* event_stream);
  void EncodeDtlsTransportState(
      rtc::ArrayView<const RtcEventDtlsTransportState*> batch,
      rtclog2::EventStream* event_stream);
  void EncodeDtlsWritableState(
      rtc::ArrayView<const RtcEventDtlsWritableState*> batch,
      rtclog2::EventStream* event_stream);
  void EncodeFramesDecoded(
      rtc::ArrayView<const RtcEventFrameDecoded* const> batch,
      rtclog2::EventStream* event_stream);
  void EncodeGenericAcksReceived(
      rtc::ArrayView<const RtcEventGenericAckReceived*> batch,
      rtclog2::EventStream* event_stream);
  void EncodeGenericPacketsReceived(
      rtc::ArrayView<const RtcEventGenericPacketReceived*> batch,
      rtclog2::EventStream* event_stream);
  void EncodeGenericPacketsSent(
      rtc::ArrayView<const RtcEventGenericPacketSent*> batch,
      rtclog2::EventStream* event_stream);
  void EncodeIceCandidatePairConfig(
      rtc::ArrayView<const RtcEventIceCandidatePairConfig*> batch,
      rtclog2::EventStream* event_stream);
  void EncodeIceCandidatePairEvent(
      rtc::ArrayView<const RtcEventIceCandidatePair*> batch,
      rtclog2::EventStream* event_stream);
  void EncodeLoggingStarted(rtc::ArrayView<const RtcEventLoggingStarted*> batch,
                            rtclog2::EventStream* event_stream);
  void EncodeLoggingStopped(rtc::ArrayView<const RtcEventLoggingStopped*> batch,
                            rtclog2::EventStream* event_stream);
  void EncodeNetEqSetMinimumDelay(
      rtc::ArrayView<const RtcEventNetEqSetMinimumDelay*> batch,
      rtclog2::EventStream* event_stream);
  void EncodeProbeClusterCreated(
      rtc::ArrayView<const RtcEventProbeClusterCreated*> batch,
      rtclog2::EventStream* event_stream);
  void EncodeProbeResultFailure(
      rtc::ArrayView<const RtcEventProbeResultFailure*> batch,
      rtclog2::EventStream* event_stream);
  void EncodeProbeResultSuccess(
      rtc::ArrayView<const RtcEventProbeResultSuccess*> batch,
      rtclog2::EventStream* event_stream);
  void EncodeRouteChange(rtc::ArrayView<const RtcEventRouteChange*> batch,
                         rtclog2::EventStream* event_stream);
  void EncodeRemoteEstimate(rtc::ArrayView<const RtcEventRemoteEstimate*> batch,
                            rtclog2::EventStream* event_stream);
  void EncodeRtcpPacketIncoming(
      rtc::ArrayView<const RtcEventRtcpPacketIncoming*> batch,
      rtclog2::EventStream* event_stream);
  void EncodeRtcpPacketOutgoing(
      rtc::ArrayView<const RtcEventRtcpPacketOutgoing*> batch,
      rtclog2::EventStream* event_stream);
  void EncodeRtpPacketIncoming(
      const std::map<uint32_t, std::vector<const RtcEventRtpPacketIncoming*>>&
          batch,
      rtclog2::EventStream* event_stream);
  void EncodeRtpPacketOutgoing(
      const std::map<uint32_t, std::vector<const RtcEventRtpPacketOutgoing*>>&
          batch,
      rtclog2::EventStream* event_stream);
  void EncodeVideoRecvStreamConfig(
      rtc::ArrayView<const RtcEventVideoReceiveStreamConfig*> batch,
      rtclog2::EventStream* event_stream);
  void EncodeVideoSendStreamConfig(
      rtc::ArrayView<const RtcEventVideoSendStreamConfig*> batch,
      rtclog2::EventStream* event_stream);
  template <typename Batch, typename ProtoType>
  void EncodeRtpPacket(const Batch& batch, ProtoType* proto_batch);

  const bool encode_neteq_set_minimum_delay_kill_switch_;
  const bool encode_dependency_descriptor_;
};

}  // namespace webrtc

#endif  // LOGGING_RTC_EVENT_LOG_ENCODER_RTC_EVENT_LOG_ENCODER_NEW_FORMAT_H_
