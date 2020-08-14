/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * LEFT TO DO:
 * - WRITE TESTS for the stuff in this  file.
 * - Check the creation, maybe make it safer by returning an empty optional or
 *   unique_ptr. --- It looks OK, but RecreateEncoderInstance can perhaps crash
 *   on a valid config. Can run it in the fuzzer for some time. Should prbl also
 *   fuzz the config.
 */

#include "modules/audio_coding/codecs/opus/audio_encoder_multi_channel_opus_impl.h"

#include <algorithm>
#include <memory>
#include <string>
#include <vector>

#include "absl/strings/match.h"
#include "modules/audio_coding/codecs/opus/audio_coder_opus_common.h"
#include "rtc_base/arraysize.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/string_to_number.h"

namespace webrtc {

namespace {

// Recommended bitrates for one channel:
// 8-12 kb/s for NB speech,
// 16-20 kb/s for WB speech,
// 28-40 kb/s for FB speech,
// 48-64 kb/s for FB mono music, and
// 64-128 kb/s for FB stereo music.
// The current implementation multiplies these values by the number of channels.
constexpr int kOpusBitrateNbBps = 12000;
constexpr int kOpusBitrateWbBps = 20000;
constexpr int kOpusBitrateFbBps = 32000;

constexpr int kDefaultMaxPlaybackRate = 48000;
// These two lists must be sorted from low to high
#if WEBRTC_OPUS_SUPPORT_120MS_PTIME
constexpr int kOpusSupportedFrameLengths[] = {10, 20, 40, 60, 120};
#else
constexpr int kOpusSupportedFrameLengths[] = {10, 20, 40, 60};
#endif

int GetBitrateBps(const AudioEncoderMultiChannelOpusConfig& config) {
  RTC_DCHECK(config.IsOk());
  return config.bitrate_bps;
}
int GetMaxPlaybackRate(const SdpAudioFormat& format) {
  const auto param = GetFormatParameter<int>(format, "maxplaybackrate");
  if (param && *param >= 8000) {
    return std::min(*param, kDefaultMaxPlaybackRate);
  }
  return kDefaultMaxPlaybackRate;
}

int GetFrameSizeMs(const SdpAudioFormat& format) {
  const auto ptime = GetFormatParameter<int>(format, "ptime");
  if (ptime.has_value()) {
    // Pick the next highest supported frame length from
    // kOpusSupportedFrameLengths.
    for (const int supported_frame_length : kOpusSupportedFrameLengths) {
      if (supported_frame_length >= *ptime) {
        return supported_frame_length;
      }
    }
    // If none was found, return the largest supported frame length.
    return *(std::end(kOpusSupportedFrameLengths) - 1);
  }

  return AudioEncoderOpusConfig::kDefaultFrameSizeMs;
}

int CalculateDefaultBitrate(int max_playback_rate, size_t num_channels) {
  const int bitrate = [&] {
    if (max_playback_rate <= 8000) {
      return kOpusBitrateNbBps * rtc::dchecked_cast<int>(num_channels);
    } else if (max_playback_rate <= 16000) {
      return kOpusBitrateWbBps * rtc::dchecked_cast<int>(num_channels);
    } else {
      return kOpusBitrateFbBps * rtc::dchecked_cast<int>(num_channels);
    }
  }();
  RTC_DCHECK_GE(bitrate, AudioEncoderMultiChannelOpusConfig::kMinBitrateBps);
  return bitrate;
}

// Get the maxaveragebitrate parameter in string-form, so we can properly figure
// out how invalid it is and accurately log invalid values.
int CalculateBitrate(int max_playback_rate_hz,
                     size_t num_channels,
                     absl::optional<std::string> bitrate_param) {
  const int default_bitrate =
      CalculateDefaultBitrate(max_playback_rate_hz, num_channels);

  if (bitrate_param) {
    const auto bitrate = rtc::StringToNumber<int>(*bitrate_param);
    if (bitrate) {
      const int chosen_bitrate =
          std::max(AudioEncoderOpusConfig::kMinBitrateBps,
                   std::min(*bitrate, AudioEncoderOpusConfig::kMaxBitrateBps));
      if (bitrate != chosen_bitrate) {
        RTC_LOG(LS_WARNING) << "Invalid maxaveragebitrate " << *bitrate
                            << " clamped to " << chosen_bitrate;
      }
      return chosen_bitrate;
    }
    RTC_LOG(LS_WARNING) << "Invalid maxaveragebitrate \"" << *bitrate_param
                        << "\" replaced by default bitrate " << default_bitrate;
  }

  return default_bitrate;
}

}  // namespace

std::unique_ptr<AudioEncoder>
AudioEncoderMultiChannelOpusImpl::MakeAudioEncoder(
    const AudioEncoderMultiChannelOpusConfig& config,
    int payload_type) {
  if (!config.IsOk()) {
    return nullptr;
  }
  return std::make_unique<AudioEncoderMultiChannelOpusImpl>(config,
                                                            payload_type);
}

AudioEncoderMultiChannelOpusImpl::AudioEncoderMultiChannelOpusImpl(
    const AudioEncoderMultiChannelOpusConfig& config,
    int payload_type)
    : payload_type_(payload_type), inst_(nullptr) {
  RTC_DCHECK(0 <= payload_type && payload_type <= 127);

  RTC_CHECK(RecreateEncoderInstance(config));
}

AudioEncoderMultiChannelOpusImpl::~AudioEncoderMultiChannelOpusImpl() {
  RTC_CHECK_EQ(0, WebRtcOpus_EncoderFree(inst_));
}

size_t AudioEncoderMultiChannelOpusImpl::SufficientOutputBufferSize() const {
  // Calculate the number of bytes we expect the encoder to produce,
  // then multiply by two to give a wide margin for error.
  const size_t bytes_per_millisecond =
      static_cast<size_t>(GetBitrateBps(config_) / (1000 * 8) + 1);
  const size_t approx_encoded_bytes =
      Num10msFramesPerPacket() * 10 * bytes_per_millisecond;
  return 2 * approx_encoded_bytes;
}

void AudioEncoderMultiChannelOpusImpl::Reset() {
  RTC_CHECK(RecreateEncoderInstance(config_));
}

absl::optional<std::pair<TimeDelta, TimeDelta>>
AudioEncoderMultiChannelOpusImpl::GetFrameLengthRange() const {
  return {{TimeDelta::Millis(config_.frame_size_ms),
           TimeDelta::Millis(config_.frame_size_ms)}};
}

// If the given config is OK, recreate the Opus encoder instance with those
// settings, save the config, and return true. Otherwise, do nothing and return
// false.
bool AudioEncoderMultiChannelOpusImpl::RecreateEncoderInstance(
    const AudioEncoderMultiChannelOpusConfig& config) {
  if (!config.IsOk())
    return false;
  config_ = config;
  if (inst_)
    RTC_CHECK_EQ(0, WebRtcOpus_EncoderFree(inst_));
  input_buffer_.clear();
  input_buffer_.reserve(Num10msFramesPerPacket() * SamplesPer10msFrame());
  RTC_CHECK_EQ(
      0, WebRtcOpus_MultistreamEncoderCreate(
             &inst_, config.num_channels,
             config.application ==
                     AudioEncoderMultiChannelOpusConfig::ApplicationMode::kVoip
                 ? 0
                 : 1,
             config.num_streams, config.coupled_streams,
             config.channel_mapping.data()));
  const int bitrate = GetBitrateBps(config);
  RTC_CHECK_EQ(0, WebRtcOpus_SetBitRate(inst_, bitrate));
  RTC_LOG(LS_VERBOSE) << "Set Opus bitrate to " << bitrate << " bps.";
  if (config.fec_enabled) {
    RTC_CHECK_EQ(0, WebRtcOpus_EnableFec(inst_));
    RTC_LOG(LS_VERBOSE) << "Opus enable FEC";
  } else {
    RTC_CHECK_EQ(0, WebRtcOpus_DisableFec(inst_));
    RTC_LOG(LS_VERBOSE) << "Opus disable FEC";
  }
  RTC_CHECK_EQ(
      0, WebRtcOpus_SetMaxPlaybackRate(inst_, config.max_playback_rate_hz));
  RTC_LOG(LS_VERBOSE) << "Set Opus playback rate to "
                      << config.max_playback_rate_hz << " hz.";

  // Use the DEFAULT complexity.
  RTC_CHECK_EQ(
      0, WebRtcOpus_SetComplexity(inst_, AudioEncoderOpusConfig().complexity));
  RTC_LOG(LS_VERBOSE) << "Set Opus coding complexity to "
                      << AudioEncoderOpusConfig().complexity;

  if (config.dtx_enabled) {
    RTC_CHECK_EQ(0, WebRtcOpus_EnableDtx(inst_));
    RTC_LOG(LS_VERBOSE) << "Opus enable DTX";
  } else {
    RTC_CHECK_EQ(0, WebRtcOpus_DisableDtx(inst_));
    RTC_LOG(LS_VERBOSE) << "Opus disable DTX";
  }

  if (config.cbr_enabled) {
    RTC_CHECK_EQ(0, WebRtcOpus_EnableCbr(inst_));
    RTC_LOG(LS_VERBOSE) << "Opus enable CBR";
  } else {
    RTC_CHECK_EQ(0, WebRtcOpus_DisableCbr(inst_));
    RTC_LOG(LS_VERBOSE) << "Opus disable CBR";
  }
  num_channels_to_encode_ = NumChannels();
  next_frame_length_ms_ = config_.frame_size_ms;
  RTC_LOG(LS_VERBOSE) << "Set Opus frame length to " << config_.frame_size_ms
                      << " ms";
  return true;
}

absl::optional<AudioEncoderMultiChannelOpusConfig>
AudioEncoderMultiChannelOpusImpl::SdpToConfig(const SdpAudioFormat& format) {
  if (!absl::EqualsIgnoreCase(format.name, "multiopus") ||
      format.clockrate_hz != 48000) {
    return absl::nullopt;
  }

  AudioEncoderMultiChannelOpusConfig config;
  config.num_channels = format.num_channels;
  config.frame_size_ms = GetFrameSizeMs(format);
  config.max_playback_rate_hz = GetMaxPlaybackRate(format);
  config.fec_enabled = (GetFormatParameter(format, "useinbandfec") == "1");
  config.dtx_enabled = (GetFormatParameter(format, "usedtx") == "1");
  config.cbr_enabled = (GetFormatParameter(format, "cbr") == "1");
  config.bitrate_bps =
      CalculateBitrate(config.max_playback_rate_hz, config.num_channels,
                       GetFormatParameter(format, "maxaveragebitrate"));
  config.application =
      config.num_channels == 1
          ? AudioEncoderMultiChannelOpusConfig::ApplicationMode::kVoip
          : AudioEncoderMultiChannelOpusConfig::ApplicationMode::kAudio;

  config.supported_frame_lengths_ms.clear();
  std::copy(std::begin(kOpusSupportedFrameLengths),
            std::end(kOpusSupportedFrameLengths),
            std::back_inserter(config.supported_frame_lengths_ms));

  auto num_streams = GetFormatParameter<int>(format, "num_streams");
  if (!num_streams.has_value()) {
    return absl::nullopt;
  }
  config.num_streams = *num_streams;

  auto coupled_streams = GetFormatParameter<int>(format, "coupled_streams");
  if (!coupled_streams.has_value()) {
    return absl::nullopt;
  }
  config.coupled_streams = *coupled_streams;

  auto channel_mapping =
      GetFormatParameter<std::vector<unsigned char>>(format, "channel_mapping");
  if (!channel_mapping.has_value()) {
    return absl::nullopt;
  }
  config.channel_mapping = *channel_mapping;

  return config;
}

AudioCodecInfo AudioEncoderMultiChannelOpusImpl::QueryAudioEncoder(
    const AudioEncoderMultiChannelOpusConfig& config) {
  RTC_DCHECK(config.IsOk());
  AudioCodecInfo info(48000, config.num_channels, config.bitrate_bps,
                      AudioEncoderOpusConfig::kMinBitrateBps,
                      AudioEncoderOpusConfig::kMaxBitrateBps);
  info.allow_comfort_noise = false;
  info.supports_network_adaption = false;
  return info;
}

size_t AudioEncoderMultiChannelOpusImpl::Num10msFramesPerPacket() const {
  return static_cast<size_t>(rtc::CheckedDivExact(config_.frame_size_ms, 10));
}
size_t AudioEncoderMultiChannelOpusImpl::SamplesPer10msFrame() const {
  return rtc::CheckedDivExact(48000, 100) * config_.num_channels;
}
int AudioEncoderMultiChannelOpusImpl::SampleRateHz() const {
  return 48000;
}
size_t AudioEncoderMultiChannelOpusImpl::NumChannels() const {
  return config_.num_channels;
}
size_t AudioEncoderMultiChannelOpusImpl::Num10MsFramesInNextPacket() const {
  return Num10msFramesPerPacket();
}
size_t AudioEncoderMultiChannelOpusImpl::Max10MsFramesInAPacket() const {
  return Num10msFramesPerPacket();
}
int AudioEncoderMultiChannelOpusImpl::GetTargetBitrate() const {
  return GetBitrateBps(config_);
}

AudioEncoder::EncodedInfo AudioEncoderMultiChannelOpusImpl::EncodeImpl(
    uint32_t rtp_timestamp,
    rtc::ArrayView<const int16_t> audio,
    rtc::Buffer* encoded) {
  if (input_buffer_.empty())
    first_timestamp_in_buffer_ = rtp_timestamp;

  input_buffer_.insert(input_buffer_.end(), audio.cbegin(), audio.cend());
  if (input_buffer_.size() <
      (Num10msFramesPerPacket() * SamplesPer10msFrame())) {
    return EncodedInfo();
  }
  RTC_CHECK_EQ(input_buffer_.size(),
               Num10msFramesPerPacket() * SamplesPer10msFrame());

  const size_t max_encoded_bytes = SufficientOutputBufferSize();
  EncodedInfo info;
  info.encoded_bytes = encoded->AppendData(
      max_encoded_bytes, [&](rtc::ArrayView<uint8_t> encoded) {
        int status = WebRtcOpus_Encode(
            inst_, &input_buffer_[0],
            rtc::CheckedDivExact(input_buffer_.size(), config_.num_channels),
            rtc::saturated_cast<int16_t>(max_encoded_bytes), encoded.data());

        RTC_CHECK_GE(status, 0);  // Fails only if fed invalid data.

        return static_cast<size_t>(status);
      });
  input_buffer_.clear();

  // Will use new packet size for next encoding.
  config_.frame_size_ms = next_frame_length_ms_;

  info.encoded_timestamp = first_timestamp_in_buffer_;
  info.payload_type = payload_type_;
  info.send_even_if_empty = true;  // Allows Opus to send empty packets.

  info.speech = true;
  info.encoder_type = CodecType::kOther;

  return info;
}

}  // namespace webrtc
