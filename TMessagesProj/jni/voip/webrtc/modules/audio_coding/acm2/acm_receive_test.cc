/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/acm2/acm_receive_test.h"

#include <stdio.h>

#include <memory>

#include "api/audio_codecs/builtin_audio_decoder_factory.h"
#include "modules/audio_coding/include/audio_coding_module.h"
#include "modules/audio_coding/neteq/tools/audio_sink.h"
#include "modules/audio_coding/neteq/tools/packet.h"
#include "modules/audio_coding/neteq/tools/packet_source.h"
#include "test/gtest.h"

namespace webrtc {
namespace test {

namespace {
acm2::AcmReceiver::Config MakeAcmConfig(
    Clock& clock,
    rtc::scoped_refptr<AudioDecoderFactory> decoder_factory) {
  acm2::AcmReceiver::Config config;
  config.clock = clock;
  config.decoder_factory = std::move(decoder_factory);
  return config;
}
}  // namespace

AcmReceiveTestOldApi::AcmReceiveTestOldApi(
    PacketSource* packet_source,
    AudioSink* audio_sink,
    int output_freq_hz,
    NumOutputChannels exptected_output_channels,
    rtc::scoped_refptr<AudioDecoderFactory> decoder_factory)
    : clock_(0),
      acm_receiver_(std::make_unique<acm2::AcmReceiver>(
          MakeAcmConfig(clock_, std::move(decoder_factory)))),
      packet_source_(packet_source),
      audio_sink_(audio_sink),
      output_freq_hz_(output_freq_hz),
      exptected_output_channels_(exptected_output_channels) {}

AcmReceiveTestOldApi::~AcmReceiveTestOldApi() = default;

void AcmReceiveTestOldApi::RegisterDefaultCodecs() {
  acm_receiver_->SetCodecs({{103, {"ISAC", 16000, 1}},
                            {104, {"ISAC", 32000, 1}},
                            {107, {"L16", 8000, 1}},
                            {108, {"L16", 16000, 1}},
                            {109, {"L16", 32000, 1}},
                            {111, {"L16", 8000, 2}},
                            {112, {"L16", 16000, 2}},
                            {113, {"L16", 32000, 2}},
                            {0, {"PCMU", 8000, 1}},
                            {110, {"PCMU", 8000, 2}},
                            {8, {"PCMA", 8000, 1}},
                            {118, {"PCMA", 8000, 2}},
                            {102, {"ILBC", 8000, 1}},
                            {9, {"G722", 8000, 1}},
                            {119, {"G722", 8000, 2}},
                            {120, {"OPUS", 48000, 2, {{"stereo", "1"}}}},
                            {13, {"CN", 8000, 1}},
                            {98, {"CN", 16000, 1}},
                            {99, {"CN", 32000, 1}}});
}

// Remaps payload types from ACM's default to those used in the resource file
// neteq_universal_new.rtp.
void AcmReceiveTestOldApi::RegisterNetEqTestCodecs() {
  acm_receiver_->SetCodecs({{103, {"ISAC", 16000, 1}},
                            {104, {"ISAC", 32000, 1}},
                            {93, {"L16", 8000, 1}},
                            {94, {"L16", 16000, 1}},
                            {95, {"L16", 32000, 1}},
                            {0, {"PCMU", 8000, 1}},
                            {8, {"PCMA", 8000, 1}},
                            {102, {"ILBC", 8000, 1}},
                            {9, {"G722", 8000, 1}},
                            {120, {"OPUS", 48000, 2}},
                            {13, {"CN", 8000, 1}},
                            {98, {"CN", 16000, 1}},
                            {99, {"CN", 32000, 1}}});
}

void AcmReceiveTestOldApi::Run() {
  for (std::unique_ptr<Packet> packet(packet_source_->NextPacket()); packet;
       packet = packet_source_->NextPacket()) {
    // Pull audio until time to insert packet.
    while (clock_.TimeInMilliseconds() < packet->time_ms()) {
      AudioFrame output_frame;
      bool muted;
      EXPECT_EQ(
          0, acm_receiver_->GetAudio(output_freq_hz_, &output_frame, &muted));
      ASSERT_EQ(output_freq_hz_, output_frame.sample_rate_hz_);
      ASSERT_FALSE(muted);
      const size_t samples_per_block =
          static_cast<size_t>(output_freq_hz_ * 10 / 1000);
      EXPECT_EQ(samples_per_block, output_frame.samples_per_channel_);
      if (exptected_output_channels_ != kArbitraryChannels) {
        if (output_frame.speech_type_ == webrtc::AudioFrame::kPLC) {
          // Don't check number of channels for PLC output, since each test run
          // usually starts with a short period of mono PLC before decoding the
          // first packet.
        } else {
          EXPECT_EQ(exptected_output_channels_, output_frame.num_channels_);
        }
      }
      ASSERT_TRUE(audio_sink_->WriteAudioFrame(output_frame));
      clock_.AdvanceTimeMilliseconds(10);
      AfterGetAudio();
    }

    EXPECT_EQ(0, acm_receiver_->InsertPacket(
                     packet->header(),
                     rtc::ArrayView<const uint8_t>(
                         packet->payload(), packet->payload_length_bytes())))
        << "Failure when inserting packet:" << std::endl
        << "  PT = " << static_cast<int>(packet->header().payloadType)
        << std::endl
        << "  TS = " << packet->header().timestamp << std::endl
        << "  SN = " << packet->header().sequenceNumber;
  }
}

AcmReceiveTestToggleOutputFreqOldApi::AcmReceiveTestToggleOutputFreqOldApi(
    PacketSource* packet_source,
    AudioSink* audio_sink,
    int output_freq_hz_1,
    int output_freq_hz_2,
    int toggle_period_ms,
    NumOutputChannels exptected_output_channels)
    : AcmReceiveTestOldApi(packet_source,
                           audio_sink,
                           output_freq_hz_1,
                           exptected_output_channels,
                           CreateBuiltinAudioDecoderFactory()),
      output_freq_hz_1_(output_freq_hz_1),
      output_freq_hz_2_(output_freq_hz_2),
      toggle_period_ms_(toggle_period_ms),
      last_toggle_time_ms_(clock_.TimeInMilliseconds()) {}

void AcmReceiveTestToggleOutputFreqOldApi::AfterGetAudio() {
  if (clock_.TimeInMilliseconds() >= last_toggle_time_ms_ + toggle_period_ms_) {
    output_freq_hz_ = (output_freq_hz_ == output_freq_hz_1_)
                          ? output_freq_hz_2_
                          : output_freq_hz_1_;
    last_toggle_time_ms_ = clock_.TimeInMilliseconds();
  }
}

}  // namespace test
}  // namespace webrtc
