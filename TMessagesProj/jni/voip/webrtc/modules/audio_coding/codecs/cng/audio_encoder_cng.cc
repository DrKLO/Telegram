/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/cng/audio_encoder_cng.h"

#include <cstdint>
#include <memory>
#include <utility>

#include "absl/types/optional.h"
#include "api/units/time_delta.h"
#include "modules/audio_coding/codecs/cng/webrtc_cng.h"
#include "rtc_base/checks.h"

namespace webrtc {

namespace {

const int kMaxFrameSizeMs = 60;

class AudioEncoderCng final : public AudioEncoder {
 public:
  explicit AudioEncoderCng(AudioEncoderCngConfig&& config);
  ~AudioEncoderCng() override;

  // Not copyable or moveable.
  AudioEncoderCng(const AudioEncoderCng&) = delete;
  AudioEncoderCng(AudioEncoderCng&&) = delete;
  AudioEncoderCng& operator=(const AudioEncoderCng&) = delete;
  AudioEncoderCng& operator=(AudioEncoderCng&&) = delete;

  int SampleRateHz() const override;
  size_t NumChannels() const override;
  int RtpTimestampRateHz() const override;
  size_t Num10MsFramesInNextPacket() const override;
  size_t Max10MsFramesInAPacket() const override;
  int GetTargetBitrate() const override;
  EncodedInfo EncodeImpl(uint32_t rtp_timestamp,
                         rtc::ArrayView<const int16_t> audio,
                         rtc::Buffer* encoded) override;
  void Reset() override;
  bool SetFec(bool enable) override;
  bool SetDtx(bool enable) override;
  bool SetApplication(Application application) override;
  void SetMaxPlaybackRate(int frequency_hz) override;
  rtc::ArrayView<std::unique_ptr<AudioEncoder>> ReclaimContainedEncoders()
      override;
  void OnReceivedUplinkPacketLossFraction(
      float uplink_packet_loss_fraction) override;
  void OnReceivedUplinkBandwidth(
      int target_audio_bitrate_bps,
      absl::optional<int64_t> bwe_period_ms) override;
  absl::optional<std::pair<TimeDelta, TimeDelta>> GetFrameLengthRange()
      const override;

 private:
  EncodedInfo EncodePassive(size_t frames_to_encode, rtc::Buffer* encoded);
  EncodedInfo EncodeActive(size_t frames_to_encode, rtc::Buffer* encoded);
  size_t SamplesPer10msFrame() const;

  std::unique_ptr<AudioEncoder> speech_encoder_;
  const int cng_payload_type_;
  const int num_cng_coefficients_;
  const int sid_frame_interval_ms_;
  std::vector<int16_t> speech_buffer_;
  std::vector<uint32_t> rtp_timestamps_;
  bool last_frame_active_;
  std::unique_ptr<Vad> vad_;
  std::unique_ptr<ComfortNoiseEncoder> cng_encoder_;
};

AudioEncoderCng::AudioEncoderCng(AudioEncoderCngConfig&& config)
    : speech_encoder_((static_cast<void>([&] {
                         RTC_CHECK(config.IsOk()) << "Invalid configuration.";
                       }()),
                       std::move(config.speech_encoder))),
      cng_payload_type_(config.payload_type),
      num_cng_coefficients_(config.num_cng_coefficients),
      sid_frame_interval_ms_(config.sid_frame_interval_ms),
      last_frame_active_(true),
      vad_(config.vad ? std::unique_ptr<Vad>(config.vad)
                      : CreateVad(config.vad_mode)),
      cng_encoder_(new ComfortNoiseEncoder(SampleRateHz(),
                                           sid_frame_interval_ms_,
                                           num_cng_coefficients_)) {}

AudioEncoderCng::~AudioEncoderCng() = default;

int AudioEncoderCng::SampleRateHz() const {
  return speech_encoder_->SampleRateHz();
}

size_t AudioEncoderCng::NumChannels() const {
  return 1;
}

int AudioEncoderCng::RtpTimestampRateHz() const {
  return speech_encoder_->RtpTimestampRateHz();
}

size_t AudioEncoderCng::Num10MsFramesInNextPacket() const {
  return speech_encoder_->Num10MsFramesInNextPacket();
}

size_t AudioEncoderCng::Max10MsFramesInAPacket() const {
  return speech_encoder_->Max10MsFramesInAPacket();
}

int AudioEncoderCng::GetTargetBitrate() const {
  return speech_encoder_->GetTargetBitrate();
}

AudioEncoder::EncodedInfo AudioEncoderCng::EncodeImpl(
    uint32_t rtp_timestamp,
    rtc::ArrayView<const int16_t> audio,
    rtc::Buffer* encoded) {
  const size_t samples_per_10ms_frame = SamplesPer10msFrame();
  RTC_CHECK_EQ(speech_buffer_.size(),
               rtp_timestamps_.size() * samples_per_10ms_frame);
  rtp_timestamps_.push_back(rtp_timestamp);
  RTC_DCHECK_EQ(samples_per_10ms_frame, audio.size());
  speech_buffer_.insert(speech_buffer_.end(), audio.cbegin(), audio.cend());
  const size_t frames_to_encode = speech_encoder_->Num10MsFramesInNextPacket();
  if (rtp_timestamps_.size() < frames_to_encode) {
    return EncodedInfo();
  }
  RTC_CHECK_LE(frames_to_encode * 10, kMaxFrameSizeMs)
      << "Frame size cannot be larger than " << kMaxFrameSizeMs
      << " ms when using VAD/CNG.";

  // Group several 10 ms blocks per VAD call. Call VAD once or twice using the
  // following split sizes:
  // 10 ms = 10 + 0 ms; 20 ms = 20 + 0 ms; 30 ms = 30 + 0 ms;
  // 40 ms = 20 + 20 ms; 50 ms = 30 + 20 ms; 60 ms = 30 + 30 ms.
  size_t blocks_in_first_vad_call =
      (frames_to_encode > 3 ? 3 : frames_to_encode);
  if (frames_to_encode == 4)
    blocks_in_first_vad_call = 2;
  RTC_CHECK_GE(frames_to_encode, blocks_in_first_vad_call);
  const size_t blocks_in_second_vad_call =
      frames_to_encode - blocks_in_first_vad_call;

  // Check if all of the buffer is passive speech. Start with checking the first
  // block.
  Vad::Activity activity = vad_->VoiceActivity(
      &speech_buffer_[0], samples_per_10ms_frame * blocks_in_first_vad_call,
      SampleRateHz());
  if (activity == Vad::kPassive && blocks_in_second_vad_call > 0) {
    // Only check the second block if the first was passive.
    activity = vad_->VoiceActivity(
        &speech_buffer_[samples_per_10ms_frame * blocks_in_first_vad_call],
        samples_per_10ms_frame * blocks_in_second_vad_call, SampleRateHz());
  }

  EncodedInfo info;
  switch (activity) {
    case Vad::kPassive: {
      info = EncodePassive(frames_to_encode, encoded);
      last_frame_active_ = false;
      break;
    }
    case Vad::kActive: {
      info = EncodeActive(frames_to_encode, encoded);
      last_frame_active_ = true;
      break;
    }
    default: {
      RTC_CHECK_NOTREACHED();
    }
  }

  speech_buffer_.erase(
      speech_buffer_.begin(),
      speech_buffer_.begin() + frames_to_encode * samples_per_10ms_frame);
  rtp_timestamps_.erase(rtp_timestamps_.begin(),
                        rtp_timestamps_.begin() + frames_to_encode);
  return info;
}

void AudioEncoderCng::Reset() {
  speech_encoder_->Reset();
  speech_buffer_.clear();
  rtp_timestamps_.clear();
  last_frame_active_ = true;
  vad_->Reset();
  cng_encoder_.reset(new ComfortNoiseEncoder(
      SampleRateHz(), sid_frame_interval_ms_, num_cng_coefficients_));
}

bool AudioEncoderCng::SetFec(bool enable) {
  return speech_encoder_->SetFec(enable);
}

bool AudioEncoderCng::SetDtx(bool enable) {
  return speech_encoder_->SetDtx(enable);
}

bool AudioEncoderCng::SetApplication(Application application) {
  return speech_encoder_->SetApplication(application);
}

void AudioEncoderCng::SetMaxPlaybackRate(int frequency_hz) {
  speech_encoder_->SetMaxPlaybackRate(frequency_hz);
}

rtc::ArrayView<std::unique_ptr<AudioEncoder>>
AudioEncoderCng::ReclaimContainedEncoders() {
  return rtc::ArrayView<std::unique_ptr<AudioEncoder>>(&speech_encoder_, 1);
}

void AudioEncoderCng::OnReceivedUplinkPacketLossFraction(
    float uplink_packet_loss_fraction) {
  speech_encoder_->OnReceivedUplinkPacketLossFraction(
      uplink_packet_loss_fraction);
}

void AudioEncoderCng::OnReceivedUplinkBandwidth(
    int target_audio_bitrate_bps,
    absl::optional<int64_t> bwe_period_ms) {
  speech_encoder_->OnReceivedUplinkBandwidth(target_audio_bitrate_bps,
                                             bwe_period_ms);
}

absl::optional<std::pair<TimeDelta, TimeDelta>>
AudioEncoderCng::GetFrameLengthRange() const {
  return speech_encoder_->GetFrameLengthRange();
}

AudioEncoder::EncodedInfo AudioEncoderCng::EncodePassive(
    size_t frames_to_encode,
    rtc::Buffer* encoded) {
  bool force_sid = last_frame_active_;
  bool output_produced = false;
  const size_t samples_per_10ms_frame = SamplesPer10msFrame();
  AudioEncoder::EncodedInfo info;

  for (size_t i = 0; i < frames_to_encode; ++i) {
    // It's important not to pass &info.encoded_bytes directly to
    // WebRtcCng_Encode(), since later loop iterations may return zero in
    // that value, in which case we don't want to overwrite any value from
    // an earlier iteration.
    size_t encoded_bytes_tmp =
        cng_encoder_->Encode(rtc::ArrayView<const int16_t>(
                                 &speech_buffer_[i * samples_per_10ms_frame],
                                 samples_per_10ms_frame),
                             force_sid, encoded);

    if (encoded_bytes_tmp > 0) {
      RTC_CHECK(!output_produced);
      info.encoded_bytes = encoded_bytes_tmp;
      output_produced = true;
      force_sid = false;
    }
  }

  info.encoded_timestamp = rtp_timestamps_.front();
  info.payload_type = cng_payload_type_;
  info.send_even_if_empty = true;
  info.speech = false;
  return info;
}

AudioEncoder::EncodedInfo AudioEncoderCng::EncodeActive(size_t frames_to_encode,
                                                        rtc::Buffer* encoded) {
  const size_t samples_per_10ms_frame = SamplesPer10msFrame();
  AudioEncoder::EncodedInfo info;
  for (size_t i = 0; i < frames_to_encode; ++i) {
    info =
        speech_encoder_->Encode(rtp_timestamps_.front(),
                                rtc::ArrayView<const int16_t>(
                                    &speech_buffer_[i * samples_per_10ms_frame],
                                    samples_per_10ms_frame),
                                encoded);
    if (i + 1 == frames_to_encode) {
      RTC_CHECK_GT(info.encoded_bytes, 0) << "Encoder didn't deliver data.";
    } else {
      RTC_CHECK_EQ(info.encoded_bytes, 0)
          << "Encoder delivered data too early.";
    }
  }
  return info;
}

size_t AudioEncoderCng::SamplesPer10msFrame() const {
  return rtc::CheckedDivExact(10 * SampleRateHz(), 1000);
}

}  // namespace

AudioEncoderCngConfig::AudioEncoderCngConfig() = default;
AudioEncoderCngConfig::AudioEncoderCngConfig(AudioEncoderCngConfig&&) = default;
AudioEncoderCngConfig::~AudioEncoderCngConfig() = default;

bool AudioEncoderCngConfig::IsOk() const {
  if (num_channels != 1)
    return false;
  if (!speech_encoder)
    return false;
  if (num_channels != speech_encoder->NumChannels())
    return false;
  if (sid_frame_interval_ms <
      static_cast<int>(speech_encoder->Max10MsFramesInAPacket() * 10))
    return false;
  if (num_cng_coefficients > WEBRTC_CNG_MAX_LPC_ORDER ||
      num_cng_coefficients <= 0)
    return false;
  return true;
}

std::unique_ptr<AudioEncoder> CreateComfortNoiseEncoder(
    AudioEncoderCngConfig&& config) {
  return std::make_unique<AudioEncoderCng>(std::move(config));
}

}  // namespace webrtc
