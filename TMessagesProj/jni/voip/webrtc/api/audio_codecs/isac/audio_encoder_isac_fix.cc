/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/audio_codecs/isac/audio_encoder_isac_fix.h"

#include <memory>

#include "absl/strings/match.h"
#include "modules/audio_coding/codecs/isac/fix/include/audio_encoder_isacfix.h"
#include "rtc_base/string_to_number.h"

namespace webrtc {

absl::optional<AudioEncoderIsacFix::Config> AudioEncoderIsacFix::SdpToConfig(
    const SdpAudioFormat& format) {
  if (absl::EqualsIgnoreCase(format.name, "ISAC") &&
      format.clockrate_hz == 16000 && format.num_channels == 1) {
    Config config;
    const auto ptime_iter = format.parameters.find("ptime");
    if (ptime_iter != format.parameters.end()) {
      const auto ptime = rtc::StringToNumber<int>(ptime_iter->second);
      if (ptime && *ptime >= 60) {
        config.frame_size_ms = 60;
      }
    }
    if (!config.IsOk()) {
      RTC_DCHECK_NOTREACHED();
      return absl::nullopt;
    }
    return config;
  } else {
    return absl::nullopt;
  }
}

void AudioEncoderIsacFix::AppendSupportedEncoders(
    std::vector<AudioCodecSpec>* specs) {
  const SdpAudioFormat fmt = {"ISAC", 16000, 1};
  const AudioCodecInfo info = QueryAudioEncoder(*SdpToConfig(fmt));
  specs->push_back({fmt, info});
}

AudioCodecInfo AudioEncoderIsacFix::QueryAudioEncoder(
    AudioEncoderIsacFix::Config config) {
  RTC_DCHECK(config.IsOk());
  return {16000, 1, 32000, 10000, 32000};
}

std::unique_ptr<AudioEncoder> AudioEncoderIsacFix::MakeAudioEncoder(
    AudioEncoderIsacFix::Config config,
    int payload_type,
    absl::optional<AudioCodecPairId> /*codec_pair_id*/,
    const FieldTrialsView* field_trials) {
  AudioEncoderIsacFixImpl::Config c;
  c.frame_size_ms = config.frame_size_ms;
  c.bit_rate = config.bit_rate;
  c.payload_type = payload_type;
  if (!config.IsOk()) {
    RTC_DCHECK_NOTREACHED();
    return nullptr;
  }
  return std::make_unique<AudioEncoderIsacFixImpl>(c);
}

}  // namespace webrtc
