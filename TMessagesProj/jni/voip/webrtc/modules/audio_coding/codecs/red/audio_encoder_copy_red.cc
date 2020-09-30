/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/red/audio_encoder_copy_red.h"

#include <string.h>

#include <utility>
#include <vector>

#include "rtc_base/byte_order.h"
#include "rtc_base/checks.h"

namespace webrtc {
// RED packets must be less than 1024 bytes to fit the 10 bit block length.
static constexpr const int kRedMaxPacketSize = 1 << 10;
// The typical MTU is 1200 bytes.
static constexpr const size_t kAudioMaxRtpPacketLen = 1200;

AudioEncoderCopyRed::Config::Config() = default;
AudioEncoderCopyRed::Config::Config(Config&&) = default;
AudioEncoderCopyRed::Config::~Config() = default;

AudioEncoderCopyRed::AudioEncoderCopyRed(Config&& config)
    : speech_encoder_(std::move(config.speech_encoder)),
      max_packet_length_(kAudioMaxRtpPacketLen),
      red_payload_type_(config.payload_type) {
  RTC_CHECK(speech_encoder_) << "Speech encoder not provided.";
}

AudioEncoderCopyRed::~AudioEncoderCopyRed() = default;

int AudioEncoderCopyRed::SampleRateHz() const {
  return speech_encoder_->SampleRateHz();
}

size_t AudioEncoderCopyRed::NumChannels() const {
  return speech_encoder_->NumChannels();
}

int AudioEncoderCopyRed::RtpTimestampRateHz() const {
  return speech_encoder_->RtpTimestampRateHz();
}

size_t AudioEncoderCopyRed::Num10MsFramesInNextPacket() const {
  return speech_encoder_->Num10MsFramesInNextPacket();
}

size_t AudioEncoderCopyRed::Max10MsFramesInAPacket() const {
  return speech_encoder_->Max10MsFramesInAPacket();
}

int AudioEncoderCopyRed::GetTargetBitrate() const {
  return speech_encoder_->GetTargetBitrate();
}

size_t AudioEncoderCopyRed::CalculateHeaderLength(size_t encoded_bytes) const {
  size_t header_size = 1;
  size_t bytes_available = max_packet_length_ - encoded_bytes;
  if (secondary_info_.encoded_bytes > 0 &&
      secondary_info_.encoded_bytes < bytes_available) {
    header_size += 4;
    bytes_available -= secondary_info_.encoded_bytes;
  }
  if (tertiary_info_.encoded_bytes > 0 &&
      tertiary_info_.encoded_bytes < bytes_available) {
    header_size += 4;
  }
  return header_size > 1 ? header_size : 0;
}

AudioEncoder::EncodedInfo AudioEncoderCopyRed::EncodeImpl(
    uint32_t rtp_timestamp,
    rtc::ArrayView<const int16_t> audio,
    rtc::Buffer* encoded) {
  rtc::Buffer primary_encoded;
  EncodedInfo info =
      speech_encoder_->Encode(rtp_timestamp, audio, &primary_encoded);
  RTC_CHECK(info.redundant.empty()) << "Cannot use nested redundant encoders.";
  RTC_DCHECK_EQ(primary_encoded.size(), info.encoded_bytes);

  if (info.encoded_bytes == 0 || info.encoded_bytes > kRedMaxPacketSize) {
    return info;
  }
  RTC_DCHECK_GT(max_packet_length_, info.encoded_bytes);

  // Allocate room for RFC 2198 header if there is redundant data.
  // Otherwise this will send the primary payload type without
  // wrapping in RED.
  const size_t header_length_bytes = CalculateHeaderLength(info.encoded_bytes);
  encoded->SetSize(header_length_bytes);

  size_t header_offset = 0;
  size_t bytes_available = max_packet_length_ - info.encoded_bytes;
  if (tertiary_info_.encoded_bytes > 0 &&
      tertiary_info_.encoded_bytes + secondary_info_.encoded_bytes <
          bytes_available) {
    encoded->AppendData(tertiary_encoded_);

    const uint32_t timestamp_delta =
        info.encoded_timestamp - tertiary_info_.encoded_timestamp;

    encoded->data()[header_offset] = tertiary_info_.payload_type | 0x80;
    rtc::SetBE16(static_cast<uint8_t*>(encoded->data()) + header_offset + 1,
                 (timestamp_delta << 2) | (tertiary_info_.encoded_bytes >> 8));
    encoded->data()[header_offset + 3] = tertiary_info_.encoded_bytes & 0xff;
    header_offset += 4;
    bytes_available -= tertiary_info_.encoded_bytes;
  }

  if (secondary_info_.encoded_bytes > 0 &&
      secondary_info_.encoded_bytes < bytes_available) {
    encoded->AppendData(secondary_encoded_);

    const uint32_t timestamp_delta =
        info.encoded_timestamp - secondary_info_.encoded_timestamp;

    encoded->data()[header_offset] = secondary_info_.payload_type | 0x80;
    rtc::SetBE16(static_cast<uint8_t*>(encoded->data()) + header_offset + 1,
                 (timestamp_delta << 2) | (secondary_info_.encoded_bytes >> 8));
    encoded->data()[header_offset + 3] = secondary_info_.encoded_bytes & 0xff;
    header_offset += 4;
    bytes_available -= secondary_info_.encoded_bytes;
  }

  encoded->AppendData(primary_encoded);
  if (header_length_bytes > 0) {
    RTC_DCHECK_EQ(header_offset, header_length_bytes - 1);
    encoded->data()[header_offset] = info.payload_type;
  }

  // |info| will be implicitly cast to an EncodedInfoLeaf struct, effectively
  // discarding the (empty) vector of redundant information. This is
  // intentional.
  info.redundant.push_back(info);
  RTC_DCHECK_EQ(info.redundant.size(), 1);
  RTC_DCHECK_EQ(info.speech, info.redundant[0].speech);
  if (secondary_info_.encoded_bytes > 0) {
    info.redundant.push_back(secondary_info_);
    RTC_DCHECK_EQ(info.redundant.size(), 2);
  }
  if (tertiary_info_.encoded_bytes > 0) {
    info.redundant.push_back(tertiary_info_);
    RTC_DCHECK_EQ(info.redundant.size(),
                  2 + (secondary_info_.encoded_bytes > 0 ? 1 : 0));
  }

  // Save secondary to tertiary.
  tertiary_encoded_.SetData(secondary_encoded_);
  tertiary_info_ = secondary_info_;

  // Save primary to secondary.
  secondary_encoded_.SetData(primary_encoded);
  secondary_info_ = info;

  // Update main EncodedInfo.
  if (header_length_bytes > 0) {
    info.payload_type = red_payload_type_;
  }
  info.encoded_bytes = encoded->size();
  return info;
}

void AudioEncoderCopyRed::Reset() {
  speech_encoder_->Reset();
  secondary_encoded_.Clear();
  secondary_info_.encoded_bytes = 0;
}

bool AudioEncoderCopyRed::SetFec(bool enable) {
  return speech_encoder_->SetFec(enable);
}

bool AudioEncoderCopyRed::SetDtx(bool enable) {
  return speech_encoder_->SetDtx(enable);
}

bool AudioEncoderCopyRed::SetApplication(Application application) {
  return speech_encoder_->SetApplication(application);
}

void AudioEncoderCopyRed::SetMaxPlaybackRate(int frequency_hz) {
  speech_encoder_->SetMaxPlaybackRate(frequency_hz);
}

rtc::ArrayView<std::unique_ptr<AudioEncoder>>
AudioEncoderCopyRed::ReclaimContainedEncoders() {
  return rtc::ArrayView<std::unique_ptr<AudioEncoder>>(&speech_encoder_, 1);
}

void AudioEncoderCopyRed::OnReceivedUplinkPacketLossFraction(
    float uplink_packet_loss_fraction) {
  speech_encoder_->OnReceivedUplinkPacketLossFraction(
      uplink_packet_loss_fraction);
}

void AudioEncoderCopyRed::OnReceivedUplinkBandwidth(
    int target_audio_bitrate_bps,
    absl::optional<int64_t> bwe_period_ms) {
  speech_encoder_->OnReceivedUplinkBandwidth(target_audio_bitrate_bps,
                                             bwe_period_ms);
}

absl::optional<std::pair<TimeDelta, TimeDelta>>
AudioEncoderCopyRed::GetFrameLengthRange() const {
  return speech_encoder_->GetFrameLengthRange();
}

void AudioEncoderCopyRed::OnReceivedOverhead(size_t overhead_bytes_per_packet) {
  max_packet_length_ = kAudioMaxRtpPacketLen - overhead_bytes_per_packet;
  return speech_encoder_->OnReceivedOverhead(overhead_bytes_per_packet);
}

}  // namespace webrtc
