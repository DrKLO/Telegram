/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/audio_codecs/opus/audio_encoder_opus.h"

#include "modules/audio_coding/codecs/opus/audio_encoder_opus.h"

namespace webrtc {

absl::optional<AudioEncoderOpusConfig> AudioEncoderOpus::SdpToConfig(
    const SdpAudioFormat& format) {
  return AudioEncoderOpusImpl::SdpToConfig(format);
}

void AudioEncoderOpus::AppendSupportedEncoders(
    std::vector<AudioCodecSpec>* specs) {
  AudioEncoderOpusImpl::AppendSupportedEncoders(specs);
}

AudioCodecInfo AudioEncoderOpus::QueryAudioEncoder(
    const AudioEncoderOpusConfig& config) {
  return AudioEncoderOpusImpl::QueryAudioEncoder(config);
}

std::unique_ptr<AudioEncoder> AudioEncoderOpus::MakeAudioEncoder(
    const AudioEncoderOpusConfig& config,
    int payload_type,
    absl::optional<AudioCodecPairId> /*codec_pair_id*/,
    const FieldTrialsView* field_trials) {
  if (!config.IsOk()) {
    RTC_DCHECK_NOTREACHED();
    return nullptr;
  }
  return AudioEncoderOpusImpl::MakeAudioEncoder(config, payload_type);
}

}  // namespace webrtc
