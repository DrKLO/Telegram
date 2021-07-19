/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/g711/audio_encoder_pcm.h"

#include <cstdint>

#include "modules/audio_coding/codecs/g711/g711_interface.h"
#include "rtc_base/checks.h"

namespace webrtc {

bool AudioEncoderPcm::Config::IsOk() const {
  return (frame_size_ms % 10 == 0) && (num_channels >= 1);
}

AudioEncoderPcm::AudioEncoderPcm(const Config& config, int sample_rate_hz)
    : sample_rate_hz_(sample_rate_hz),
      num_channels_(config.num_channels),
      payload_type_(config.payload_type),
      num_10ms_frames_per_packet_(
          static_cast<size_t>(config.frame_size_ms / 10)),
      full_frame_samples_(config.num_channels * config.frame_size_ms *
                          sample_rate_hz / 1000),
      first_timestamp_in_buffer_(0) {
  RTC_CHECK_GT(sample_rate_hz, 0) << "Sample rate must be larger than 0 Hz";
  RTC_CHECK_EQ(config.frame_size_ms % 10, 0)
      << "Frame size must be an integer multiple of 10 ms.";
  speech_buffer_.reserve(full_frame_samples_);
}

AudioEncoderPcm::~AudioEncoderPcm() = default;

int AudioEncoderPcm::SampleRateHz() const {
  return sample_rate_hz_;
}

size_t AudioEncoderPcm::NumChannels() const {
  return num_channels_;
}

size_t AudioEncoderPcm::Num10MsFramesInNextPacket() const {
  return num_10ms_frames_per_packet_;
}

size_t AudioEncoderPcm::Max10MsFramesInAPacket() const {
  return num_10ms_frames_per_packet_;
}

int AudioEncoderPcm::GetTargetBitrate() const {
  return static_cast<int>(8 * BytesPerSample() * SampleRateHz() *
                          NumChannels());
}

AudioEncoder::EncodedInfo AudioEncoderPcm::EncodeImpl(
    uint32_t rtp_timestamp,
    rtc::ArrayView<const int16_t> audio,
    rtc::Buffer* encoded) {
  if (speech_buffer_.empty()) {
    first_timestamp_in_buffer_ = rtp_timestamp;
  }
  speech_buffer_.insert(speech_buffer_.end(), audio.begin(), audio.end());
  if (speech_buffer_.size() < full_frame_samples_) {
    return EncodedInfo();
  }
  RTC_CHECK_EQ(speech_buffer_.size(), full_frame_samples_);
  EncodedInfo info;
  info.encoded_timestamp = first_timestamp_in_buffer_;
  info.payload_type = payload_type_;
  info.encoded_bytes = encoded->AppendData(
      full_frame_samples_ * BytesPerSample(),
      [&](rtc::ArrayView<uint8_t> encoded) {
        return EncodeCall(&speech_buffer_[0], full_frame_samples_,
                          encoded.data());
      });
  speech_buffer_.clear();
  info.encoder_type = GetCodecType();
  return info;
}

void AudioEncoderPcm::Reset() {
  speech_buffer_.clear();
}

absl::optional<std::pair<TimeDelta, TimeDelta>>
AudioEncoderPcm::GetFrameLengthRange() const {
  return {{TimeDelta::Millis(num_10ms_frames_per_packet_ * 10),
           TimeDelta::Millis(num_10ms_frames_per_packet_ * 10)}};
}

size_t AudioEncoderPcmA::EncodeCall(const int16_t* audio,
                                    size_t input_len,
                                    uint8_t* encoded) {
  return WebRtcG711_EncodeA(audio, input_len, encoded);
}

size_t AudioEncoderPcmA::BytesPerSample() const {
  return 1;
}

AudioEncoder::CodecType AudioEncoderPcmA::GetCodecType() const {
  return AudioEncoder::CodecType::kPcmA;
}

size_t AudioEncoderPcmU::EncodeCall(const int16_t* audio,
                                    size_t input_len,
                                    uint8_t* encoded) {
  return WebRtcG711_EncodeU(audio, input_len, encoded);
}

size_t AudioEncoderPcmU::BytesPerSample() const {
  return 1;
}

AudioEncoder::CodecType AudioEncoderPcmU::GetCodecType() const {
  return AudioEncoder::CodecType::kPcmU;
}

}  // namespace webrtc
