/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/audio_codecs/L16/audio_decoder_L16.h"

#include <memory>

#include "absl/strings/match.h"
#include "modules/audio_coding/codecs/pcm16b/audio_decoder_pcm16b.h"
#include "modules/audio_coding/codecs/pcm16b/pcm16b_common.h"
#include "rtc_base/numerics/safe_conversions.h"

namespace webrtc {

absl::optional<AudioDecoderL16::Config> AudioDecoderL16::SdpToConfig(
    const SdpAudioFormat& format) {
  Config config;
  config.sample_rate_hz = format.clockrate_hz;
  config.num_channels = rtc::checked_cast<int>(format.num_channels);
  return absl::EqualsIgnoreCase(format.name, "L16") && config.IsOk()
             ? absl::optional<Config>(config)
             : absl::nullopt;
}

void AudioDecoderL16::AppendSupportedDecoders(
    std::vector<AudioCodecSpec>* specs) {
  Pcm16BAppendSupportedCodecSpecs(specs);
}

std::unique_ptr<AudioDecoder> AudioDecoderL16::MakeAudioDecoder(
    const Config& config,
    absl::optional<AudioCodecPairId> /*codec_pair_id*/) {
  return config.IsOk() ? std::make_unique<AudioDecoderPcm16B>(
                             config.sample_rate_hz, config.num_channels)
                       : nullptr;
}

}  // namespace webrtc
