/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/ilbc/audio_encoder_ilbc.h"

#include <algorithm>
#include <cstdint>

#include "modules/audio_coding/codecs/ilbc/ilbc.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_conversions.h"

namespace webrtc {

namespace {

const int kSampleRateHz = 8000;

int GetIlbcBitrate(int ptime) {
  switch (ptime) {
    case 20:
    case 40:
      // 38 bytes per frame of 20 ms => 15200 bits/s.
      return 15200;
    case 30:
    case 60:
      // 50 bytes per frame of 30 ms => (approx) 13333 bits/s.
      return 13333;
    default:
      FATAL();
  }
}

}  // namespace

AudioEncoderIlbcImpl::AudioEncoderIlbcImpl(const AudioEncoderIlbcConfig& config,
                                           int payload_type)
    : frame_size_ms_(config.frame_size_ms),
      payload_type_(payload_type),
      num_10ms_frames_per_packet_(
          static_cast<size_t>(config.frame_size_ms / 10)),
      encoder_(nullptr) {
  RTC_CHECK(config.IsOk());
  Reset();
}

AudioEncoderIlbcImpl::~AudioEncoderIlbcImpl() {
  RTC_CHECK_EQ(0, WebRtcIlbcfix_EncoderFree(encoder_));
}

int AudioEncoderIlbcImpl::SampleRateHz() const {
  return kSampleRateHz;
}

size_t AudioEncoderIlbcImpl::NumChannels() const {
  return 1;
}

size_t AudioEncoderIlbcImpl::Num10MsFramesInNextPacket() const {
  return num_10ms_frames_per_packet_;
}

size_t AudioEncoderIlbcImpl::Max10MsFramesInAPacket() const {
  return num_10ms_frames_per_packet_;
}

int AudioEncoderIlbcImpl::GetTargetBitrate() const {
  return GetIlbcBitrate(rtc::dchecked_cast<int>(num_10ms_frames_per_packet_) *
                        10);
}

AudioEncoder::EncodedInfo AudioEncoderIlbcImpl::EncodeImpl(
    uint32_t rtp_timestamp,
    rtc::ArrayView<const int16_t> audio,
    rtc::Buffer* encoded) {
  // Save timestamp if starting a new packet.
  if (num_10ms_frames_buffered_ == 0)
    first_timestamp_in_buffer_ = rtp_timestamp;

  // Buffer input.
  std::copy(audio.cbegin(), audio.cend(),
            input_buffer_ + kSampleRateHz / 100 * num_10ms_frames_buffered_);

  // If we don't yet have enough buffered input for a whole packet, we're done
  // for now.
  if (++num_10ms_frames_buffered_ < num_10ms_frames_per_packet_) {
    return EncodedInfo();
  }

  // Encode buffered input.
  RTC_DCHECK_EQ(num_10ms_frames_buffered_, num_10ms_frames_per_packet_);
  num_10ms_frames_buffered_ = 0;
  size_t encoded_bytes = encoded->AppendData(
      RequiredOutputSizeBytes(), [&](rtc::ArrayView<uint8_t> encoded) {
        const int r = WebRtcIlbcfix_Encode(
            encoder_, input_buffer_,
            kSampleRateHz / 100 * num_10ms_frames_per_packet_, encoded.data());
        RTC_CHECK_GE(r, 0);

        return static_cast<size_t>(r);
      });

  RTC_DCHECK_EQ(encoded_bytes, RequiredOutputSizeBytes());

  EncodedInfo info;
  info.encoded_bytes = encoded_bytes;
  info.encoded_timestamp = first_timestamp_in_buffer_;
  info.payload_type = payload_type_;
  info.encoder_type = CodecType::kIlbc;
  return info;
}

void AudioEncoderIlbcImpl::Reset() {
  if (encoder_)
    RTC_CHECK_EQ(0, WebRtcIlbcfix_EncoderFree(encoder_));
  RTC_CHECK_EQ(0, WebRtcIlbcfix_EncoderCreate(&encoder_));
  const int encoder_frame_size_ms =
      frame_size_ms_ > 30 ? frame_size_ms_ / 2 : frame_size_ms_;
  RTC_CHECK_EQ(0, WebRtcIlbcfix_EncoderInit(encoder_, encoder_frame_size_ms));
  num_10ms_frames_buffered_ = 0;
}

absl::optional<std::pair<TimeDelta, TimeDelta>>
AudioEncoderIlbcImpl::GetFrameLengthRange() const {
  return {{TimeDelta::Millis(num_10ms_frames_per_packet_ * 10),
           TimeDelta::Millis(num_10ms_frames_per_packet_ * 10)}};
}

size_t AudioEncoderIlbcImpl::RequiredOutputSizeBytes() const {
  switch (num_10ms_frames_per_packet_) {
    case 2:
      return 38;
    case 3:
      return 50;
    case 4:
      return 2 * 38;
    case 6:
      return 2 * 50;
    default:
      FATAL();
  }
}

}  // namespace webrtc
