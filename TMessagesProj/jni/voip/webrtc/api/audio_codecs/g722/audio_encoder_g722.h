/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_AUDIO_CODECS_G722_AUDIO_ENCODER_G722_H_
#define API_AUDIO_CODECS_G722_AUDIO_ENCODER_G722_H_

#include <memory>
#include <vector>

#include "absl/types/optional.h"
#include "api/audio_codecs/audio_codec_pair_id.h"
#include "api/audio_codecs/audio_encoder.h"
#include "api/audio_codecs/audio_format.h"
#include "api/audio_codecs/g722/audio_encoder_g722_config.h"
#include "api/field_trials_view.h"
#include "rtc_base/system/rtc_export.h"

namespace webrtc {

// G722 encoder API for use as a template parameter to
// CreateAudioEncoderFactory<...>().
struct RTC_EXPORT AudioEncoderG722 {
  using Config = AudioEncoderG722Config;
  static absl::optional<AudioEncoderG722Config> SdpToConfig(
      const SdpAudioFormat& audio_format);
  static void AppendSupportedEncoders(std::vector<AudioCodecSpec>* specs);
  static AudioCodecInfo QueryAudioEncoder(const AudioEncoderG722Config& config);
  static std::unique_ptr<AudioEncoder> MakeAudioEncoder(
      const AudioEncoderG722Config& config,
      int payload_type,
      absl::optional<AudioCodecPairId> codec_pair_id = absl::nullopt,
      const FieldTrialsView* field_trials = nullptr);
};

}  // namespace webrtc

#endif  // API_AUDIO_CODECS_G722_AUDIO_ENCODER_G722_H_
