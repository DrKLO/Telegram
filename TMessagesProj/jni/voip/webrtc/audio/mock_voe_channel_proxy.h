/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef AUDIO_MOCK_VOE_CHANNEL_PROXY_H_
#define AUDIO_MOCK_VOE_CHANNEL_PROXY_H_

#include <map>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "api/crypto/frame_decryptor_interface.h"
#include "api/test/mock_frame_encryptor.h"
#include "audio/channel_receive.h"
#include "audio/channel_send.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "test/gmock.h"

namespace webrtc {
namespace test {

class MockChannelReceive : public voe::ChannelReceiveInterface {
 public:
  MOCK_METHOD(void, SetNACKStatus, (bool enable, int max_packets), (override));
  MOCK_METHOD(void, SetNonSenderRttMeasurement, (bool enabled), (override));
  MOCK_METHOD(void,
              RegisterReceiverCongestionControlObjects,
              (PacketRouter*),
              (override));
  MOCK_METHOD(void, ResetReceiverCongestionControlObjects, (), (override));
  MOCK_METHOD(CallReceiveStatistics, GetRTCPStatistics, (), (const, override));
  MOCK_METHOD(NetworkStatistics,
              GetNetworkStatistics,
              (bool),
              (const, override));
  MOCK_METHOD(AudioDecodingCallStats,
              GetDecodingCallStatistics,
              (),
              (const, override));
  MOCK_METHOD(int, GetSpeechOutputLevelFullRange, (), (const, override));
  MOCK_METHOD(double, GetTotalOutputEnergy, (), (const, override));
  MOCK_METHOD(double, GetTotalOutputDuration, (), (const, override));
  MOCK_METHOD(uint32_t, GetDelayEstimate, (), (const, override));
  MOCK_METHOD(void, SetSink, (AudioSinkInterface*), (override));
  MOCK_METHOD(void, OnRtpPacket, (const RtpPacketReceived& packet), (override));
  MOCK_METHOD(void,
              ReceivedRTCPPacket,
              (const uint8_t*, size_t length),
              (override));
  MOCK_METHOD(void, SetChannelOutputVolumeScaling, (float scaling), (override));
  MOCK_METHOD(AudioMixer::Source::AudioFrameInfo,
              GetAudioFrameWithInfo,
              (int sample_rate_hz, AudioFrame*),
              (override));
  MOCK_METHOD(int, PreferredSampleRate, (), (const, override));
  MOCK_METHOD(void, SetSourceTracker, (SourceTracker*), (override));
  MOCK_METHOD(void,
              SetAssociatedSendChannel,
              (const voe::ChannelSendInterface*),
              (override));
  MOCK_METHOD(bool,
              GetPlayoutRtpTimestamp,
              (uint32_t*, int64_t*),
              (const, override));
  MOCK_METHOD(void,
              SetEstimatedPlayoutNtpTimestampMs,
              (int64_t ntp_timestamp_ms, int64_t time_ms),
              (override));
  MOCK_METHOD(absl::optional<int64_t>,
              GetCurrentEstimatedPlayoutNtpTimestampMs,
              (int64_t now_ms),
              (const, override));
  MOCK_METHOD(absl::optional<Syncable::Info>,
              GetSyncInfo,
              (),
              (const, override));
  MOCK_METHOD(bool, SetMinimumPlayoutDelay, (int delay_ms), (override));
  MOCK_METHOD(bool, SetBaseMinimumPlayoutDelayMs, (int delay_ms), (override));
  MOCK_METHOD(int, GetBaseMinimumPlayoutDelayMs, (), (const, override));
  MOCK_METHOD((absl::optional<std::pair<int, SdpAudioFormat>>),
              GetReceiveCodec,
              (),
              (const, override));
  MOCK_METHOD(void,
              SetReceiveCodecs,
              ((const std::map<int, SdpAudioFormat>& codecs)),
              (override));
  MOCK_METHOD(void, StartPlayout, (), (override));
  MOCK_METHOD(void, StopPlayout, (), (override));
  MOCK_METHOD(
      void,
      SetDepacketizerToDecoderFrameTransformer,
      (rtc::scoped_refptr<webrtc::FrameTransformerInterface> frame_transformer),
      (override));
  MOCK_METHOD(
      void,
      SetFrameDecryptor,
      (rtc::scoped_refptr<webrtc::FrameDecryptorInterface> frame_decryptor),
      (override));
  MOCK_METHOD(void, OnLocalSsrcChange, (uint32_t local_ssrc), (override));
  MOCK_METHOD(uint32_t, GetLocalSsrc, (), (const, override));
};

class MockChannelSend : public voe::ChannelSendInterface {
 public:
  MOCK_METHOD(void,
              SetEncoder,
              (int payload_type,
               const SdpAudioFormat& encoder_format,
               std::unique_ptr<AudioEncoder> encoder),
              (override));
  MOCK_METHOD(
      void,
      ModifyEncoder,
      (rtc::FunctionView<void(std::unique_ptr<AudioEncoder>*)> modifier),
      (override));
  MOCK_METHOD(void,
              CallEncoder,
              (rtc::FunctionView<void(AudioEncoder*)> modifier),
              (override));
  MOCK_METHOD(void, SetRTCP_CNAME, (absl::string_view c_name), (override));
  MOCK_METHOD(void,
              SetSendAudioLevelIndicationStatus,
              (bool enable, int id),
              (override));
  MOCK_METHOD(void,
              RegisterSenderCongestionControlObjects,
              (RtpTransportControllerSendInterface*),
              (override));
  MOCK_METHOD(void, ResetSenderCongestionControlObjects, (), (override));
  MOCK_METHOD(CallSendStatistics, GetRTCPStatistics, (), (const, override));
  MOCK_METHOD(std::vector<ReportBlockData>,
              GetRemoteRTCPReportBlocks,
              (),
              (const, override));
  MOCK_METHOD(ANAStats, GetANAStatistics, (), (const, override));
  MOCK_METHOD(void,
              RegisterCngPayloadType,
              (int payload_type, int payload_frequency),
              (override));
  MOCK_METHOD(void,
              SetSendTelephoneEventPayloadType,
              (int payload_type, int payload_frequency),
              (override));
  MOCK_METHOD(bool,
              SendTelephoneEventOutband,
              (int event, int duration_ms),
              (override));
  MOCK_METHOD(void,
              OnBitrateAllocation,
              (BitrateAllocationUpdate update),
              (override));
  MOCK_METHOD(void, SetInputMute, (bool muted), (override));
  MOCK_METHOD(void,
              ReceivedRTCPPacket,
              (const uint8_t*, size_t length),
              (override));
  MOCK_METHOD(void,
              ProcessAndEncodeAudio,
              (std::unique_ptr<AudioFrame>),
              (override));
  MOCK_METHOD(RtpRtcpInterface*, GetRtpRtcp, (), (const, override));
  MOCK_METHOD(int, GetTargetBitrate, (), (const, override));
  MOCK_METHOD(int64_t, GetRTT, (), (const, override));
  MOCK_METHOD(void, StartSend, (), (override));
  MOCK_METHOD(void, StopSend, (), (override));
  MOCK_METHOD(void,
              SetFrameEncryptor,
              (rtc::scoped_refptr<FrameEncryptorInterface> frame_encryptor),
              (override));
  MOCK_METHOD(
      void,
      SetEncoderToPacketizerFrameTransformer,
      (rtc::scoped_refptr<webrtc::FrameTransformerInterface> frame_transformer),
      (override));
};
}  // namespace test
}  // namespace webrtc

#endif  // AUDIO_MOCK_VOE_CHANNEL_PROXY_H_
