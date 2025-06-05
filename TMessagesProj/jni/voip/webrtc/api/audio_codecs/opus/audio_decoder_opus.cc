/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/audio_codecs/opus/audio_decoder_opus.h"

#include <memory>
#include <utility>
#include <vector>

#include "absl/strings/match.h"
#include "modules/audio_coding/codecs/opus/audio_decoder_opus.h"

namespace webrtc {

bool AudioDecoderOpus::Config::IsOk() const {
  if (sample_rate_hz != 16000 && sample_rate_hz != 48000) {
    // Unsupported sample rate. (libopus supports a few other rates as
    // well; we can add support for them when needed.)
    return false;
  }
  if (num_channels != 1 && num_channels != 2) {
    return false;
  }
  return true;
}

absl::optional<AudioDecoderOpus::Config> AudioDecoderOpus::SdpToConfig(
    const SdpAudioFormat& format) {
  const auto num_channels = [&]() -> absl::optional<int> {
    auto stereo = format.parameters.find("stereo");
    if (stereo != format.parameters.end()) {
      if (stereo->second == "0") {
        return 1;
      } else if (stereo->second == "1") {
        return 2;
      } else {
        return absl::nullopt;  // Bad stereo parameter.
      }
    }
    return 1;  // Default to mono.
  }();
  if (absl::EqualsIgnoreCase(format.name, "opus") &&
      format.clockrate_hz == 48000 && format.num_channels == 2 &&
      num_channels) {
    Config config;
    config.num_channels = *num_channels;
    if (!config.IsOk()) {
      RTC_DCHECK_NOTREACHED();
      return absl::nullopt;
    }
    return config;
  } else {
    return absl::nullopt;
  }
}

void AudioDecoderOpus::AppendSupportedDecoders(
    std::vector<AudioCodecSpec>* specs) {
  AudioCodecInfo opus_info{48000, 1, 64000, 6000, 510000};
  opus_info.allow_comfort_noise = false;
  opus_info.supports_network_adaption = true;
  SdpAudioFormat opus_format(
      {"opus", 48000, 2, {{"minptime", "10"}, {"useinbandfec", "1"}}});
  specs->push_back({std::move(opus_format), opus_info});
}

std::unique_ptr<AudioDecoder> AudioDecoderOpus::MakeAudioDecoder(
    Config config,
    absl::optional<AudioCodecPairId> /*codec_pair_id*/,
    const FieldTrialsView* field_trials) {
  if (!config.IsOk()) {
    RTC_DCHECK_NOTREACHED();
    return nullptr;
  }
  return std::make_unique<AudioDecoderOpusImpl>(config.num_channels,
                                                config.sample_rate_hz);
}

}  // namespace webrtc
