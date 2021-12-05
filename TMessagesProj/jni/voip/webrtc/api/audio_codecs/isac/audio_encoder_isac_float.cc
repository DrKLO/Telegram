/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/audio_codecs/isac/audio_encoder_isac_float.h"

#include <memory>

#include "absl/strings/match.h"
#include "modules/audio_coding/codecs/isac/main/include/audio_encoder_isac.h"
#include "rtc_base/string_to_number.h"

namespace webrtc {

absl::optional<AudioEncoderIsacFloat::Config>
AudioEncoderIsacFloat::SdpToConfig(const SdpAudioFormat& format) {
  if (absl::EqualsIgnoreCase(format.name, "ISAC") &&
      (format.clockrate_hz == 16000 || format.clockrate_hz == 32000) &&
      format.num_channels == 1) {
    Config config;
    config.sample_rate_hz = format.clockrate_hz;
    config.bit_rate = format.clockrate_hz == 16000 ? 32000 : 56000;
    if (config.sample_rate_hz == 16000) {
      // For sample rate 16 kHz, optionally use 60 ms frames, instead of the
      // default 30 ms.
      const auto ptime_iter = format.parameters.find("ptime");
      if (ptime_iter != format.parameters.end()) {
        const auto ptime = rtc::StringToNumber<int>(ptime_iter->second);
        if (ptime && *ptime >= 60) {
          config.frame_size_ms = 60;
        }
      }
    }
    return config;
  } else {
    return absl::nullopt;
  }
}

void AudioEncoderIsacFloat::AppendSupportedEncoders(
    std::vector<AudioCodecSpec>* specs) {
  for (int sample_rate_hz : {16000, 32000}) {
    const SdpAudioFormat fmt = {"ISAC", sample_rate_hz, 1};
    const AudioCodecInfo info = QueryAudioEncoder(*SdpToConfig(fmt));
    specs->push_back({fmt, info});
  }
}

AudioCodecInfo AudioEncoderIsacFloat::QueryAudioEncoder(
    const AudioEncoderIsacFloat::Config& config) {
  RTC_DCHECK(config.IsOk());
  constexpr int min_bitrate = 10000;
  const int max_bitrate = config.sample_rate_hz == 16000 ? 32000 : 56000;
  const int default_bitrate = max_bitrate;
  return {config.sample_rate_hz, 1, default_bitrate, min_bitrate, max_bitrate};
}

std::unique_ptr<AudioEncoder> AudioEncoderIsacFloat::MakeAudioEncoder(
    const AudioEncoderIsacFloat::Config& config,
    int payload_type,
    absl::optional<AudioCodecPairId> /*codec_pair_id*/) {
  RTC_DCHECK(config.IsOk());
  AudioEncoderIsacFloatImpl::Config c;
  c.payload_type = payload_type;
  c.sample_rate_hz = config.sample_rate_hz;
  c.frame_size_ms = config.frame_size_ms;
  c.bit_rate = config.bit_rate;
  return std::make_unique<AudioEncoderIsacFloatImpl>(c);
}

}  // namespace webrtc
