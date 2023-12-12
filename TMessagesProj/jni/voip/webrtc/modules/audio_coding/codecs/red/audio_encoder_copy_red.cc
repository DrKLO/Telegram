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

#include "absl/strings/string_view.h"
#include "rtc_base/byte_order.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
static constexpr const int kRedMaxPacketSize =
    1 << 10;  // RED packets must be less than 1024 bytes to fit the 10 bit
              // block length.
static constexpr const size_t kRedMaxTimestampDelta =
    1 << 14;  // RED packets can encode a timestamp delta of 14 bits.
static constexpr const size_t kAudioMaxRtpPacketLen =
    1200;  // The typical MTU is 1200 bytes.

static constexpr size_t kRedHeaderLength = 4;  // 4 bytes RED header.
static constexpr size_t kRedLastHeaderLength =
    1;  // reduced size for last RED header.

static constexpr size_t kRedNumberOfRedundantEncodings =
    1;  // The level of redundancy we support.

AudioEncoderCopyRed::Config::Config() = default;
AudioEncoderCopyRed::Config::Config(Config&&) = default;
AudioEncoderCopyRed::Config::~Config() = default;

size_t GetMaxRedundancyFromFieldTrial(const FieldTrialsView& field_trials) {
  const std::string red_trial =
      field_trials.Lookup("WebRTC-Audio-Red-For-Opus");
  size_t redundancy = 0;
  if (sscanf(red_trial.c_str(), "Enabled-%zu", &redundancy) != 1 ||
      redundancy > 9) {
    return kRedNumberOfRedundantEncodings;
  }
  return redundancy;
}

AudioEncoderCopyRed::AudioEncoderCopyRed(Config&& config,
                                         const FieldTrialsView& field_trials)
    : speech_encoder_(std::move(config.speech_encoder)),
      primary_encoded_(0, kAudioMaxRtpPacketLen),
      max_packet_length_(kAudioMaxRtpPacketLen),
      red_payload_type_(config.payload_type) {
  RTC_CHECK(speech_encoder_) << "Speech encoder not provided.";

  auto number_of_redundant_encodings =
      GetMaxRedundancyFromFieldTrial(field_trials);
  for (size_t i = 0; i < number_of_redundant_encodings; i++) {
    std::pair<EncodedInfo, rtc::Buffer> redundant;
    redundant.second.EnsureCapacity(kAudioMaxRtpPacketLen);
    redundant_encodings_.push_front(std::move(redundant));
  }
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

AudioEncoder::EncodedInfo AudioEncoderCopyRed::EncodeImpl(
    uint32_t rtp_timestamp,
    rtc::ArrayView<const int16_t> audio,
    rtc::Buffer* encoded) {
  primary_encoded_.Clear();
  EncodedInfo info =
      speech_encoder_->Encode(rtp_timestamp, audio, &primary_encoded_);
  RTC_CHECK(info.redundant.empty()) << "Cannot use nested redundant encoders.";
  RTC_DCHECK_EQ(primary_encoded_.size(), info.encoded_bytes);

  if (info.encoded_bytes == 0 || info.encoded_bytes >= kRedMaxPacketSize) {
    return info;
  }
  RTC_DCHECK_GT(max_packet_length_, info.encoded_bytes);

  size_t header_length_bytes = kRedLastHeaderLength;
  size_t bytes_available = max_packet_length_ - info.encoded_bytes;
  auto it = redundant_encodings_.begin();

  // Determine how much redundancy we can fit into our packet by
  // iterating forward. This is determined both by the length as well
  // as the timestamp difference. The latter can occur with opus DTX which
  // has timestamp gaps of 400ms which exceeds REDs timestamp delta field size.
  for (; it != redundant_encodings_.end(); it++) {
    if (bytes_available < kRedHeaderLength + it->first.encoded_bytes) {
      break;
    }
    if (it->first.encoded_bytes == 0) {
      break;
    }
    if (rtp_timestamp - it->first.encoded_timestamp >= kRedMaxTimestampDelta) {
      break;
    }
    bytes_available -= kRedHeaderLength + it->first.encoded_bytes;
    header_length_bytes += kRedHeaderLength;
  }

  // Allocate room for RFC 2198 header.
  encoded->SetSize(header_length_bytes);

  // Iterate backwards and append the data.
  size_t header_offset = 0;
  while (it-- != redundant_encodings_.begin()) {
    encoded->AppendData(it->second);

    const uint32_t timestamp_delta =
        info.encoded_timestamp - it->first.encoded_timestamp;
    encoded->data()[header_offset] = it->first.payload_type | 0x80;
    rtc::SetBE16(static_cast<uint8_t*>(encoded->data()) + header_offset + 1,
                 (timestamp_delta << 2) | (it->first.encoded_bytes >> 8));
    encoded->data()[header_offset + 3] = it->first.encoded_bytes & 0xff;
    header_offset += kRedHeaderLength;
    info.redundant.push_back(it->first);
  }

  // `info` will be implicitly cast to an EncodedInfoLeaf struct, effectively
  // discarding the (empty) vector of redundant information. This is
  // intentional.
  if (header_length_bytes > kRedHeaderLength) {
    info.redundant.push_back(info);
    RTC_DCHECK_EQ(info.speech,
                  info.redundant[info.redundant.size() - 1].speech);
  }

  encoded->AppendData(primary_encoded_);
  RTC_DCHECK_EQ(header_offset, header_length_bytes - 1);
  encoded->data()[header_offset] = info.payload_type;

  // Shift the redundant encodings.
  auto rit = redundant_encodings_.rbegin();
  for (auto next = std::next(rit); next != redundant_encodings_.rend();
       rit++, next = std::next(rit)) {
    rit->first = next->first;
    rit->second.SetData(next->second);
  }
  it = redundant_encodings_.begin();
  if (it != redundant_encodings_.end()) {
    it->first = info;
    it->second.SetData(primary_encoded_);
  }

  // Update main EncodedInfo.
  info.payload_type = red_payload_type_;
  info.encoded_bytes = encoded->size();
  return info;
}

void AudioEncoderCopyRed::Reset() {
  speech_encoder_->Reset();
  auto number_of_redundant_encodings = redundant_encodings_.size();
  redundant_encodings_.clear();
  for (size_t i = 0; i < number_of_redundant_encodings; i++) {
    std::pair<EncodedInfo, rtc::Buffer> redundant;
    redundant.second.EnsureCapacity(kAudioMaxRtpPacketLen);
    redundant_encodings_.push_front(std::move(redundant));
  }
}

bool AudioEncoderCopyRed::SetFec(bool enable) {
  return speech_encoder_->SetFec(enable);
}

bool AudioEncoderCopyRed::SetDtx(bool enable) {
  return speech_encoder_->SetDtx(enable);
}

bool AudioEncoderCopyRed::GetDtx() const {
  return speech_encoder_->GetDtx();
}

bool AudioEncoderCopyRed::SetApplication(Application application) {
  return speech_encoder_->SetApplication(application);
}

void AudioEncoderCopyRed::SetMaxPlaybackRate(int frequency_hz) {
  speech_encoder_->SetMaxPlaybackRate(frequency_hz);
}

bool AudioEncoderCopyRed::EnableAudioNetworkAdaptor(
    const std::string& config_string,
    RtcEventLog* event_log) {
  return speech_encoder_->EnableAudioNetworkAdaptor(config_string, event_log);
}

void AudioEncoderCopyRed::DisableAudioNetworkAdaptor() {
  speech_encoder_->DisableAudioNetworkAdaptor();
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

void AudioEncoderCopyRed::OnReceivedUplinkAllocation(
    BitrateAllocationUpdate update) {
  speech_encoder_->OnReceivedUplinkAllocation(update);
}

absl::optional<std::pair<TimeDelta, TimeDelta>>
AudioEncoderCopyRed::GetFrameLengthRange() const {
  return speech_encoder_->GetFrameLengthRange();
}

void AudioEncoderCopyRed::OnReceivedRtt(int rtt_ms) {
  speech_encoder_->OnReceivedRtt(rtt_ms);
}

void AudioEncoderCopyRed::OnReceivedOverhead(size_t overhead_bytes_per_packet) {
  max_packet_length_ = kAudioMaxRtpPacketLen - overhead_bytes_per_packet;
  return speech_encoder_->OnReceivedOverhead(overhead_bytes_per_packet);
}

void AudioEncoderCopyRed::SetReceiverFrameLengthRange(int min_frame_length_ms,
                                                      int max_frame_length_ms) {
  return speech_encoder_->SetReceiverFrameLengthRange(min_frame_length_ms,
                                                      max_frame_length_ms);
}

ANAStats AudioEncoderCopyRed::GetANAStats() const {
  return speech_encoder_->GetANAStats();
}

rtc::ArrayView<std::unique_ptr<AudioEncoder>>
AudioEncoderCopyRed::ReclaimContainedEncoders() {
  return rtc::ArrayView<std::unique_ptr<AudioEncoder>>(&speech_encoder_, 1);
}

}  // namespace webrtc
