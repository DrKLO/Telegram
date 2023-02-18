/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_AUDIO_CODECS_ISAC_AUDIO_ENCODER_ISAC_FIX_H_
#define API_AUDIO_CODECS_ISAC_AUDIO_ENCODER_ISAC_FIX_H_

#include <memory>
#include <vector>

#include "absl/types/optional.h"
#include "api/audio_codecs/audio_codec_pair_id.h"
#include "api/audio_codecs/audio_encoder.h"
#include "api/audio_codecs/audio_format.h"
#include "api/field_trials_view.h"
#include "rtc_base/system/rtc_export.h"

namespace webrtc {

// iSAC encoder API (fixed-point implementation) for use as a template
// parameter to CreateAudioEncoderFactory<...>().
struct RTC_EXPORT AudioEncoderIsacFix {
  struct Config {
    bool IsOk() const {
      if (frame_size_ms != 30 && frame_size_ms != 60) {
        return false;
      }
      if (bit_rate < 10000 || bit_rate > 32000) {
        return false;
      }
      return true;
    }
    int frame_size_ms = 30;
    int bit_rate = 32000;  // Limit on short-term average bit rate, in bits/s.
  };
  static absl::optional<Config> SdpToConfig(const SdpAudioFormat& audio_format);
  static void AppendSupportedEncoders(std::vector<AudioCodecSpec>* specs);
  static AudioCodecInfo QueryAudioEncoder(Config config);
  static std::unique_ptr<AudioEncoder> MakeAudioEncoder(
      Config config,
      int payload_type,
      absl::optional<AudioCodecPairId> codec_pair_id = absl::nullopt,
      const FieldTrialsView* field_trials = nullptr);
};

}  // namespace webrtc

#endif  // API_AUDIO_CODECS_ISAC_AUDIO_ENCODER_ISAC_FIX_H_
