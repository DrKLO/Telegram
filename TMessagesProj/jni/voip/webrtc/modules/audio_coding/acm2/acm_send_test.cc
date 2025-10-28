/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/acm2/acm_send_test.h"

#include <stdio.h>
#include <string.h>

#include "absl/strings/match.h"
#include "absl/strings/string_view.h"
#include "api/audio_codecs/audio_encoder.h"
#include "api/audio_codecs/builtin_audio_decoder_factory.h"
#include "api/audio_codecs/builtin_audio_encoder_factory.h"
#include "modules/audio_coding/include/audio_coding_module.h"
#include "modules/audio_coding/neteq/tools/input_audio_file.h"
#include "modules/audio_coding/neteq/tools/packet.h"
#include "rtc_base/checks.h"
#include "rtc_base/string_encode.h"
#include "test/gtest.h"

namespace webrtc {
namespace test {

AcmSendTestOldApi::AcmSendTestOldApi(InputAudioFile* audio_source,
                                     int source_rate_hz,
                                     int test_duration_ms)
    : clock_(0),
      acm_(webrtc::AudioCodingModule::Create()),
      audio_source_(audio_source),
      source_rate_hz_(source_rate_hz),
      input_block_size_samples_(
          static_cast<size_t>(source_rate_hz_ * kBlockSizeMs / 1000)),
      codec_registered_(false),
      test_duration_ms_(test_duration_ms),
      frame_type_(AudioFrameType::kAudioFrameSpeech),
      payload_type_(0),
      timestamp_(0),
      sequence_number_(0) {
  input_frame_.sample_rate_hz_ = source_rate_hz_;
  input_frame_.num_channels_ = 1;
  input_frame_.samples_per_channel_ = input_block_size_samples_;
  RTC_DCHECK_LE(input_block_size_samples_ * input_frame_.num_channels_,
                AudioFrame::kMaxDataSizeSamples);
  acm_->RegisterTransportCallback(this);
}

AcmSendTestOldApi::~AcmSendTestOldApi() = default;

bool AcmSendTestOldApi::RegisterCodec(absl::string_view payload_name,
                                      int clockrate_hz,
                                      int num_channels,
                                      int payload_type,
                                      int frame_size_samples) {
  SdpAudioFormat format(payload_name, clockrate_hz, num_channels);
  if (absl::EqualsIgnoreCase(payload_name, "g722")) {
    RTC_CHECK_EQ(16000, clockrate_hz);
    format.clockrate_hz = 8000;
  } else if (absl::EqualsIgnoreCase(payload_name, "opus")) {
    RTC_CHECK(num_channels == 1 || num_channels == 2);
    if (num_channels == 2) {
      format.parameters["stereo"] = "1";
    }
    format.num_channels = 2;
  }
  format.parameters["ptime"] = rtc::ToString(rtc::CheckedDivExact(
      frame_size_samples, rtc::CheckedDivExact(clockrate_hz, 1000)));
  auto factory = CreateBuiltinAudioEncoderFactory();
  acm_->SetEncoder(
      factory->MakeAudioEncoder(payload_type, format, absl::nullopt));
  codec_registered_ = true;
  input_frame_.num_channels_ = num_channels;
  RTC_DCHECK_LE(input_block_size_samples_ * input_frame_.num_channels_,
                AudioFrame::kMaxDataSizeSamples);
  return codec_registered_;
}

void AcmSendTestOldApi::RegisterExternalCodec(
    std::unique_ptr<AudioEncoder> external_speech_encoder) {
  input_frame_.num_channels_ = external_speech_encoder->NumChannels();
  acm_->SetEncoder(std::move(external_speech_encoder));
  RTC_DCHECK_LE(input_block_size_samples_ * input_frame_.num_channels_,
                AudioFrame::kMaxDataSizeSamples);
  codec_registered_ = true;
}

std::unique_ptr<Packet> AcmSendTestOldApi::NextPacket() {
  RTC_DCHECK(codec_registered_);
  if (filter_.test(static_cast<size_t>(payload_type_))) {
    // This payload type should be filtered out. Since the payload type is the
    // same throughout the whole test run, no packet at all will be delivered.
    // We can just as well signal that the test is over by returning NULL.
    return nullptr;
  }
  // Insert audio and process until one packet is produced.
  while (clock_.TimeInMilliseconds() < test_duration_ms_) {
    clock_.AdvanceTimeMilliseconds(kBlockSizeMs);
    RTC_CHECK(audio_source_->Read(
        input_block_size_samples_ * input_frame_.num_channels_,
        input_frame_.mutable_data()));
    data_to_send_ = false;
    RTC_CHECK_GE(acm_->Add10MsData(input_frame_), 0);
    input_frame_.timestamp_ += static_cast<uint32_t>(input_block_size_samples_);
    if (data_to_send_) {
      // Encoded packet received.
      return CreatePacket();
    }
  }
  // Test ended.
  return nullptr;
}

// This method receives the callback from ACM when a new packet is produced.
int32_t AcmSendTestOldApi::SendData(AudioFrameType frame_type,
                                    uint8_t payload_type,
                                    uint32_t timestamp,
                                    const uint8_t* payload_data,
                                    size_t payload_len_bytes,
                                    int64_t absolute_capture_timestamp_ms) {
  // Store the packet locally.
  frame_type_ = frame_type;
  payload_type_ = payload_type;
  timestamp_ = timestamp;
  last_payload_vec_.assign(payload_data, payload_data + payload_len_bytes);
  RTC_DCHECK_EQ(last_payload_vec_.size(), payload_len_bytes);
  data_to_send_ = true;
  return 0;
}

std::unique_ptr<Packet> AcmSendTestOldApi::CreatePacket() {
  const size_t kRtpHeaderSize = 12;
  rtc::CopyOnWriteBuffer packet_buffer(last_payload_vec_.size() +
                                       kRtpHeaderSize);
  uint8_t* packet_memory = packet_buffer.MutableData();
  // Populate the header bytes.
  packet_memory[0] = 0x80;
  packet_memory[1] = static_cast<uint8_t>(payload_type_);
  packet_memory[2] = (sequence_number_ >> 8) & 0xFF;
  packet_memory[3] = (sequence_number_)&0xFF;
  packet_memory[4] = (timestamp_ >> 24) & 0xFF;
  packet_memory[5] = (timestamp_ >> 16) & 0xFF;
  packet_memory[6] = (timestamp_ >> 8) & 0xFF;
  packet_memory[7] = timestamp_ & 0xFF;
  // Set SSRC to 0x12345678.
  packet_memory[8] = 0x12;
  packet_memory[9] = 0x34;
  packet_memory[10] = 0x56;
  packet_memory[11] = 0x78;

  ++sequence_number_;

  // Copy the payload data.
  memcpy(packet_memory + kRtpHeaderSize, &last_payload_vec_[0],
         last_payload_vec_.size());
  auto packet = std::make_unique<Packet>(std::move(packet_buffer),
                                         clock_.TimeInMilliseconds());
  RTC_DCHECK(packet);
  RTC_DCHECK(packet->valid_header());
  return packet;
}

}  // namespace test
}  // namespace webrtc
