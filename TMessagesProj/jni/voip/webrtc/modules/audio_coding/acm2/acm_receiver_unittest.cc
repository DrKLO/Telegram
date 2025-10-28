/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/acm2/acm_receiver.h"

#include <algorithm>  // std::min
#include <memory>

#include "absl/types/optional.h"
#include "api/audio_codecs/builtin_audio_decoder_factory.h"
#include "api/audio_codecs/builtin_audio_encoder_factory.h"
#include "modules/audio_coding/codecs/cng/audio_encoder_cng.h"
#include "modules/audio_coding/include/audio_coding_module.h"
#include "modules/audio_coding/neteq/tools/rtp_generator.h"
#include "modules/include/module_common_types.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "system_wrappers/include/clock.h"
#include "test/gtest.h"
#include "test/testsupport/file_utils.h"

namespace webrtc {

namespace acm2 {

class AcmReceiverTestOldApi : public AudioPacketizationCallback,
                              public ::testing::Test {
 protected:
  AcmReceiverTestOldApi()
      : timestamp_(0),
        packet_sent_(false),
        last_packet_send_timestamp_(timestamp_),
        last_frame_type_(AudioFrameType::kEmptyFrame) {
    config_.decoder_factory = decoder_factory_;
  }

  ~AcmReceiverTestOldApi() {}

  void SetUp() override {
    acm_ = AudioCodingModule::Create();
    receiver_.reset(new AcmReceiver(config_));
    ASSERT_TRUE(receiver_.get() != NULL);
    ASSERT_TRUE(acm_.get() != NULL);
    acm_->RegisterTransportCallback(this);

    rtp_header_.sequenceNumber = 0;
    rtp_header_.timestamp = 0;
    rtp_header_.markerBit = false;
    rtp_header_.ssrc = 0x12345678;  // Arbitrary.
    rtp_header_.numCSRCs = 0;
    rtp_header_.payloadType = 0;
  }

  void TearDown() override {}

  AudioCodecInfo SetEncoder(int payload_type,
                            const SdpAudioFormat& format,
                            const std::map<int, int> cng_payload_types = {}) {
    // Create the speech encoder.
    absl::optional<AudioCodecInfo> info =
        encoder_factory_->QueryAudioEncoder(format);
    RTC_CHECK(info.has_value());
    std::unique_ptr<AudioEncoder> enc =
        encoder_factory_->MakeAudioEncoder(payload_type, format, absl::nullopt);

    // If we have a compatible CN specification, stack a CNG on top.
    auto it = cng_payload_types.find(info->sample_rate_hz);
    if (it != cng_payload_types.end()) {
      AudioEncoderCngConfig config;
      config.speech_encoder = std::move(enc);
      config.num_channels = 1;
      config.payload_type = it->second;
      config.vad_mode = Vad::kVadNormal;
      enc = CreateComfortNoiseEncoder(std::move(config));
    }

    // Actually start using the new encoder.
    acm_->SetEncoder(std::move(enc));
    return *info;
  }

  int InsertOnePacketOfSilence(const AudioCodecInfo& info) {
    // Frame setup according to the codec.
    AudioFrame frame;
    frame.sample_rate_hz_ = info.sample_rate_hz;
    frame.samples_per_channel_ = info.sample_rate_hz / 100;  // 10 ms.
    frame.num_channels_ = info.num_channels;
    frame.Mute();
    packet_sent_ = false;
    last_packet_send_timestamp_ = timestamp_;
    int num_10ms_frames = 0;
    while (!packet_sent_) {
      frame.timestamp_ = timestamp_;
      timestamp_ += rtc::checked_cast<uint32_t>(frame.samples_per_channel_);
      EXPECT_GE(acm_->Add10MsData(frame), 0);
      ++num_10ms_frames;
    }
    return num_10ms_frames;
  }

  int SendData(AudioFrameType frame_type,
               uint8_t payload_type,
               uint32_t timestamp,
               const uint8_t* payload_data,
               size_t payload_len_bytes,
               int64_t absolute_capture_timestamp_ms) override {
    if (frame_type == AudioFrameType::kEmptyFrame)
      return 0;

    rtp_header_.payloadType = payload_type;
    rtp_header_.timestamp = timestamp;

    int ret_val = receiver_->InsertPacket(
        rtp_header_,
        rtc::ArrayView<const uint8_t>(payload_data, payload_len_bytes));
    if (ret_val < 0) {
      RTC_DCHECK_NOTREACHED();
      return -1;
    }
    rtp_header_.sequenceNumber++;
    packet_sent_ = true;
    last_frame_type_ = frame_type;
    return 0;
  }

  const rtc::scoped_refptr<AudioEncoderFactory> encoder_factory_ =
      CreateBuiltinAudioEncoderFactory();
  const rtc::scoped_refptr<AudioDecoderFactory> decoder_factory_ =
      CreateBuiltinAudioDecoderFactory();
  acm2::AcmReceiver::Config config_;
  std::unique_ptr<AcmReceiver> receiver_;
  std::unique_ptr<AudioCodingModule> acm_;
  RTPHeader rtp_header_;
  uint32_t timestamp_;
  bool packet_sent_;  // Set when SendData is called reset when inserting audio.
  uint32_t last_packet_send_timestamp_;
  AudioFrameType last_frame_type_;
};

#if defined(WEBRTC_ANDROID)
#define MAYBE_SampleRate DISABLED_SampleRate
#else
#define MAYBE_SampleRate SampleRate
#endif
TEST_F(AcmReceiverTestOldApi, MAYBE_SampleRate) {
  const std::map<int, SdpAudioFormat> codecs = {{0, {"OPUS", 48000, 2}}};
  receiver_->SetCodecs(codecs);

  constexpr int kOutSampleRateHz = 8000;  // Different than codec sample rate.
  for (size_t i = 0; i < codecs.size(); ++i) {
    const int payload_type = rtc::checked_cast<int>(i);
    const int num_10ms_frames =
        InsertOnePacketOfSilence(SetEncoder(payload_type, codecs.at(i)));
    for (int k = 0; k < num_10ms_frames; ++k) {
      AudioFrame frame;
      bool muted;
      EXPECT_EQ(0, receiver_->GetAudio(kOutSampleRateHz, &frame, &muted));
    }
    EXPECT_EQ(encoder_factory_->QueryAudioEncoder(codecs.at(i))->sample_rate_hz,
              receiver_->last_output_sample_rate_hz());
  }
}

class AcmReceiverTestFaxModeOldApi : public AcmReceiverTestOldApi {
 protected:
  AcmReceiverTestFaxModeOldApi() {
    config_.neteq_config.for_test_no_time_stretching = true;
  }

  void RunVerifyAudioFrame(const SdpAudioFormat& codec) {
    // Make sure "fax mode" is enabled. This will avoid delay changes unless the
    // packet-loss concealment is made. We do this in order to make the
    // timestamp increments predictable; in normal mode, NetEq may decide to do
    // accelerate or pre-emptive expand operations after some time, offsetting
    // the timestamp.
    EXPECT_TRUE(config_.neteq_config.for_test_no_time_stretching);

    constexpr int payload_type = 17;
    receiver_->SetCodecs({{payload_type, codec}});

    const AudioCodecInfo info = SetEncoder(payload_type, codec);
    const int output_sample_rate_hz = info.sample_rate_hz;
    const size_t output_channels = info.num_channels;
    const size_t samples_per_ms = rtc::checked_cast<size_t>(
        rtc::CheckedDivExact(output_sample_rate_hz, 1000));

    // Expect the first output timestamp to be 5*fs/8000 samples before the
    // first inserted timestamp (because of NetEq's look-ahead). (This value is
    // defined in Expand::overlap_length_.)
    uint32_t expected_output_ts =
        last_packet_send_timestamp_ -
        rtc::CheckedDivExact(5 * output_sample_rate_hz, 8000);

    AudioFrame frame;
    bool muted;
    EXPECT_EQ(0, receiver_->GetAudio(output_sample_rate_hz, &frame, &muted));
    // Expect timestamp = 0 before first packet is inserted.
    EXPECT_EQ(0u, frame.timestamp_);
    for (int i = 0; i < 5; ++i) {
      const int num_10ms_frames = InsertOnePacketOfSilence(info);
      for (int k = 0; k < num_10ms_frames; ++k) {
        EXPECT_EQ(0,
                  receiver_->GetAudio(output_sample_rate_hz, &frame, &muted));
        EXPECT_EQ(expected_output_ts, frame.timestamp_);
        expected_output_ts += rtc::checked_cast<uint32_t>(10 * samples_per_ms);
        EXPECT_EQ(10 * samples_per_ms, frame.samples_per_channel_);
        EXPECT_EQ(output_sample_rate_hz, frame.sample_rate_hz_);
        EXPECT_EQ(output_channels, frame.num_channels_);
        EXPECT_EQ(AudioFrame::kNormalSpeech, frame.speech_type_);
        EXPECT_FALSE(muted);
      }
    }
  }
};

#if defined(WEBRTC_ANDROID)
#define MAYBE_VerifyAudioFramePCMU DISABLED_VerifyAudioFramePCMU
#else
#define MAYBE_VerifyAudioFramePCMU VerifyAudioFramePCMU
#endif
TEST_F(AcmReceiverTestFaxModeOldApi, MAYBE_VerifyAudioFramePCMU) {
  RunVerifyAudioFrame({"PCMU", 8000, 1});
}

#if defined(WEBRTC_ANDROID)
#define MAYBE_VerifyAudioFrameOpus DISABLED_VerifyAudioFrameOpus
#else
#define MAYBE_VerifyAudioFrameOpus VerifyAudioFrameOpus
#endif
TEST_F(AcmReceiverTestFaxModeOldApi, MAYBE_VerifyAudioFrameOpus) {
  RunVerifyAudioFrame({"opus", 48000, 2});
}

#if defined(WEBRTC_ANDROID)
#define MAYBE_LastAudioCodec DISABLED_LastAudioCodec
#else
#define MAYBE_LastAudioCodec LastAudioCodec
#endif
#if defined(WEBRTC_CODEC_OPUS)
TEST_F(AcmReceiverTestOldApi, MAYBE_LastAudioCodec) {
  const std::map<int, SdpAudioFormat> codecs = {
      {0, {"PCMU", 8000, 1}}, {1, {"PCMA", 8000, 1}}, {2, {"L16", 32000, 1}}};
  const std::map<int, int> cng_payload_types = {
      {8000, 100}, {16000, 101}, {32000, 102}};
  {
    std::map<int, SdpAudioFormat> receive_codecs = codecs;
    for (const auto& cng_type : cng_payload_types) {
      receive_codecs.emplace(std::make_pair(
          cng_type.second, SdpAudioFormat("CN", cng_type.first, 1)));
    }
    receiver_->SetCodecs(receive_codecs);
  }

  // No audio payload is received.
  EXPECT_EQ(absl::nullopt, receiver_->LastDecoder());

  // Start with sending DTX.
  packet_sent_ = false;
  InsertOnePacketOfSilence(
      SetEncoder(0, codecs.at(0), cng_payload_types));  // Enough to test
                                                        // with one codec.
  ASSERT_TRUE(packet_sent_);
  EXPECT_EQ(AudioFrameType::kAudioFrameCN, last_frame_type_);

  // Has received, only, DTX. Last Audio codec is undefined.
  EXPECT_EQ(absl::nullopt, receiver_->LastDecoder());
  EXPECT_EQ(absl::nullopt, receiver_->last_packet_sample_rate_hz());

  for (size_t i = 0; i < codecs.size(); ++i) {
    // Set DTX off to send audio payload.
    packet_sent_ = false;
    const int payload_type = rtc::checked_cast<int>(i);
    const AudioCodecInfo info_without_cng =
        SetEncoder(payload_type, codecs.at(i));
    InsertOnePacketOfSilence(info_without_cng);

    // Sanity check if Actually an audio payload received, and it should be
    // of type "speech."
    ASSERT_TRUE(packet_sent_);
    ASSERT_EQ(AudioFrameType::kAudioFrameSpeech, last_frame_type_);
    EXPECT_EQ(info_without_cng.sample_rate_hz,
              receiver_->last_packet_sample_rate_hz());

    // Set VAD on to send DTX. Then check if the "Last Audio codec" returns
    // the expected codec. Encode repeatedly until a DTX is sent.
    const AudioCodecInfo info_with_cng =
        SetEncoder(payload_type, codecs.at(i), cng_payload_types);
    while (last_frame_type_ != AudioFrameType::kAudioFrameCN) {
      packet_sent_ = false;
      InsertOnePacketOfSilence(info_with_cng);
      ASSERT_TRUE(packet_sent_);
    }
    EXPECT_EQ(info_with_cng.sample_rate_hz,
              receiver_->last_packet_sample_rate_hz());
    EXPECT_EQ(codecs.at(i), receiver_->LastDecoder()->second);
  }
}
#endif

// Check if the statistics are initialized correctly. Before any call to ACM
// all fields have to be zero.
#if defined(WEBRTC_ANDROID)
#define MAYBE_InitializedToZero DISABLED_InitializedToZero
#else
#define MAYBE_InitializedToZero InitializedToZero
#endif
TEST_F(AcmReceiverTestOldApi, MAYBE_InitializedToZero) {
  AudioDecodingCallStats stats;
  receiver_->GetDecodingCallStatistics(&stats);
  EXPECT_EQ(0, stats.calls_to_neteq);
  EXPECT_EQ(0, stats.calls_to_silence_generator);
  EXPECT_EQ(0, stats.decoded_normal);
  EXPECT_EQ(0, stats.decoded_cng);
  EXPECT_EQ(0, stats.decoded_neteq_plc);
  EXPECT_EQ(0, stats.decoded_plc_cng);
  EXPECT_EQ(0, stats.decoded_muted_output);
}

#if defined(WEBRTC_ANDROID)
#define MAYBE_VerifyOutputFrame DISABLED_VerifyOutputFrame
#else
#define MAYBE_VerifyOutputFrame VerifyOutputFrame
#endif
TEST_F(AcmReceiverTestOldApi, MAYBE_VerifyOutputFrame) {
  AudioFrame audio_frame;
  const int kSampleRateHz = 32000;
  bool muted;
  EXPECT_EQ(0, receiver_->GetAudio(kSampleRateHz, &audio_frame, &muted));
  ASSERT_FALSE(muted);
  EXPECT_EQ(0u, audio_frame.timestamp_);
  EXPECT_GT(audio_frame.num_channels_, 0u);
  EXPECT_EQ(static_cast<size_t>(kSampleRateHz / 100),
            audio_frame.samples_per_channel_);
  EXPECT_EQ(kSampleRateHz, audio_frame.sample_rate_hz_);
}

// Insert some packets and pull audio. Check statistics are valid. Then,
// simulate packet loss and check if PLC and PLC-to-CNG statistics are
// correctly updated.
#if defined(WEBRTC_ANDROID)
#define MAYBE_NetEqCalls DISABLED_NetEqCalls
#else
#define MAYBE_NetEqCalls NetEqCalls
#endif
TEST_F(AcmReceiverTestOldApi, MAYBE_NetEqCalls) {
  AudioDecodingCallStats stats;
  const int kNumNormalCalls = 10;
  const int kSampleRateHz = 16000;
  const int kNumSamples10ms = kSampleRateHz / 100;
  const int kFrameSizeMs = 10;  // Multiple of 10.
  const int kFrameSizeSamples = kFrameSizeMs / 10 * kNumSamples10ms;
  const int kPayloadSizeBytes = kFrameSizeSamples * sizeof(int16_t);
  const uint8_t kPayloadType = 111;
  RTPHeader rtp_header;
  AudioFrame audio_frame;
  bool muted;

  receiver_->SetCodecs(
      {{kPayloadType, SdpAudioFormat("L16", kSampleRateHz, 1)}});
  rtp_header.sequenceNumber = 0xABCD;
  rtp_header.timestamp = 0xABCDEF01;
  rtp_header.payloadType = kPayloadType;
  rtp_header.markerBit = false;
  rtp_header.ssrc = 0x1234;
  rtp_header.numCSRCs = 0;

  for (int num_calls = 0; num_calls < kNumNormalCalls; ++num_calls) {
    const uint8_t kPayload[kPayloadSizeBytes] = {0};
    ASSERT_EQ(0, receiver_->InsertPacket(rtp_header, kPayload));
    ++rtp_header.sequenceNumber;
    rtp_header.timestamp += kFrameSizeSamples;
    ASSERT_EQ(0, receiver_->GetAudio(-1, &audio_frame, &muted));
    EXPECT_FALSE(muted);
  }
  receiver_->GetDecodingCallStatistics(&stats);
  EXPECT_EQ(kNumNormalCalls, stats.calls_to_neteq);
  EXPECT_EQ(0, stats.calls_to_silence_generator);
  EXPECT_EQ(kNumNormalCalls, stats.decoded_normal);
  EXPECT_EQ(0, stats.decoded_cng);
  EXPECT_EQ(0, stats.decoded_neteq_plc);
  EXPECT_EQ(0, stats.decoded_plc_cng);
  EXPECT_EQ(0, stats.decoded_muted_output);

  const int kNumPlc = 3;
  const int kNumPlcCng = 5;

  // Simulate packet-loss. NetEq first performs PLC then PLC fades to CNG.
  for (int n = 0; n < kNumPlc + kNumPlcCng; ++n) {
    ASSERT_EQ(0, receiver_->GetAudio(-1, &audio_frame, &muted));
    EXPECT_FALSE(muted);
  }
  receiver_->GetDecodingCallStatistics(&stats);
  EXPECT_EQ(kNumNormalCalls + kNumPlc + kNumPlcCng, stats.calls_to_neteq);
  EXPECT_EQ(0, stats.calls_to_silence_generator);
  EXPECT_EQ(kNumNormalCalls, stats.decoded_normal);
  EXPECT_EQ(0, stats.decoded_cng);
  EXPECT_EQ(kNumPlc, stats.decoded_neteq_plc);
  EXPECT_EQ(kNumPlcCng, stats.decoded_plc_cng);
  EXPECT_EQ(0, stats.decoded_muted_output);
  // TODO(henrik.lundin) Add a test with muted state enabled.
}

}  // namespace acm2

}  // namespace webrtc
