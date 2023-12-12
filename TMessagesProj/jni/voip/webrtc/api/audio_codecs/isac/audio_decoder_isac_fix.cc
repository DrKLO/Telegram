/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/audio_codecs/isac/audio_decoder_isac_fix.h"

#include <memory>

#include "absl/strings/match.h"
#include "modules/audio_coding/codecs/isac/fix/include/audio_decoder_isacfix.h"

namespace webrtc {

absl::optional<AudioDecoderIsacFix::Config> AudioDecoderIsacFix::SdpToConfig(
    const SdpAudioFormat& format) {
  if (absl::EqualsIgnoreCase(format.name, "ISAC") &&
      format.clockrate_hz == 16000 && format.num_channels == 1) {
    return Config();
  }
  return absl::nullopt;
}

void AudioDecoderIsacFix::AppendSupportedDecoders(
    std::vector<AudioCodecSpec>* specs) {
  specs->push_back({{"ISAC", 16000, 1}, {16000, 1, 32000, 10000, 32000}});
}

std::unique_ptr<AudioDecoder> AudioDecoderIsacFix::MakeAudioDecoder(
    Config config,
    absl::optional<AudioCodecPairId> /*codec_pair_id*/,
    const FieldTrialsView* field_trials) {
  AudioDecoderIsacFixImpl::Config c;
  c.sample_rate_hz = 16000;
  return std::make_unique<AudioDecoderIsacFixImpl>(c);
}

}  // namespace webrtc
