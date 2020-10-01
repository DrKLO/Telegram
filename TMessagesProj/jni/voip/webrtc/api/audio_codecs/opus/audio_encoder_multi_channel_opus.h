/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_AUDIO_CODECS_OPUS_AUDIO_ENCODER_MULTI_CHANNEL_OPUS_H_
#define API_AUDIO_CODECS_OPUS_AUDIO_ENCODER_MULTI_CHANNEL_OPUS_H_

#include <memory>
#include <vector>

#include "absl/types/optional.h"
#include "api/audio_codecs/audio_codec_pair_id.h"
#include "api/audio_codecs/audio_encoder.h"
#include "api/audio_codecs/audio_format.h"
#include "api/audio_codecs/opus/audio_encoder_multi_channel_opus_config.h"
#include "rtc_base/system/rtc_export.h"

namespace webrtc {

// Opus encoder API for use as a template parameter to
// CreateAudioEncoderFactory<...>().
struct RTC_EXPORT AudioEncoderMultiChannelOpus {
  using Config = AudioEncoderMultiChannelOpusConfig;
  static absl::optional<Config> SdpToConfig(const SdpAudioFormat& audio_format);
  static void AppendSupportedEncoders(std::vector<AudioCodecSpec>* specs);
  static AudioCodecInfo QueryAudioEncoder(const Config& config);
  static std::unique_ptr<AudioEncoder> MakeAudioEncoder(
      const Config& config,
      int payload_type,
      absl::optional<AudioCodecPairId> codec_pair_id = absl::nullopt);
};

}  // namespace webrtc

#endif  // API_AUDIO_CODECS_OPUS_AUDIO_ENCODER_MULTI_CHANNEL_OPUS_H_
