/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/audio_codecs/isac/audio_decoder_isac_float.h"

#include <memory>

#include "absl/strings/match.h"
#include "modules/audio_coding/codecs/isac/main/include/audio_decoder_isac.h"

namespace webrtc {

absl::optional<AudioDecoderIsacFloat::Config>
AudioDecoderIsacFloat::SdpToConfig(const SdpAudioFormat& format) {
  if (absl::EqualsIgnoreCase(format.name, "ISAC") &&
      (format.clockrate_hz == 16000 || format.clockrate_hz == 32000) &&
      format.num_channels == 1) {
    Config config;
    config.sample_rate_hz = format.clockrate_hz;
    if (!config.IsOk()) {
      RTC_DCHECK_NOTREACHED();
      return absl::nullopt;
    }
    return config;
  } else {
    return absl::nullopt;
  }
}

void AudioDecoderIsacFloat::AppendSupportedDecoders(
    std::vector<AudioCodecSpec>* specs) {
  specs->push_back({{"ISAC", 16000, 1}, {16000, 1, 32000, 10000, 32000}});
  specs->push_back({{"ISAC", 32000, 1}, {32000, 1, 56000, 10000, 56000}});
}

std::unique_ptr<AudioDecoder> AudioDecoderIsacFloat::MakeAudioDecoder(
    Config config,
    absl::optional<AudioCodecPairId> /*codec_pair_id*/) {
  AudioDecoderIsacFloatImpl::Config c;
  c.sample_rate_hz = config.sample_rate_hz;
  if (!config.IsOk()) {
    RTC_DCHECK_NOTREACHED();
    return nullptr;
  }
  return std::make_unique<AudioDecoderIsacFloatImpl>(c);
}

}  // namespace webrtc
