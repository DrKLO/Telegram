/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/audio_codecs/builtin_audio_encoder_factory.h"

#include <memory>
#include <vector>

#include "api/audio_codecs/L16/audio_encoder_L16.h"
#include "api/audio_codecs/audio_encoder_factory_template.h"
#include "api/audio_codecs/g711/audio_encoder_g711.h"
#include "api/audio_codecs/g722/audio_encoder_g722.h"
#if WEBRTC_USE_BUILTIN_ILBC
#include "api/audio_codecs/ilbc/audio_encoder_ilbc.h"  // nogncheck
#endif
#include "api/audio_codecs/isac/audio_encoder_isac.h"
#if WEBRTC_USE_BUILTIN_OPUS
#include "api/audio_codecs/opus/audio_encoder_multi_channel_opus.h"
#include "api/audio_codecs/opus/audio_encoder_opus.h"  // nogncheck
#endif

namespace webrtc {

namespace {

// Modify an audio encoder to not advertise support for anything.
template <typename T>
struct NotAdvertised {
  using Config = typename T::Config;
  static absl::optional<Config> SdpToConfig(
      const SdpAudioFormat& audio_format) {
    return T::SdpToConfig(audio_format);
  }
  static void AppendSupportedEncoders(std::vector<AudioCodecSpec>* specs) {
    // Don't advertise support for anything.
  }
  static AudioCodecInfo QueryAudioEncoder(const Config& config) {
    return T::QueryAudioEncoder(config);
  }
  static std::unique_ptr<AudioEncoder> MakeAudioEncoder(
      const Config& config,
      int payload_type,
      absl::optional<AudioCodecPairId> codec_pair_id = absl::nullopt) {
    return T::MakeAudioEncoder(config, payload_type, codec_pair_id);
  }
};

}  // namespace

rtc::scoped_refptr<AudioEncoderFactory> CreateBuiltinAudioEncoderFactory() {
  return CreateAudioEncoderFactory<

#if WEBRTC_USE_BUILTIN_OPUS
      AudioEncoderOpus, NotAdvertised<AudioEncoderMultiChannelOpus>,
#endif

      AudioEncoderIsac, AudioEncoderG722,

#if WEBRTC_USE_BUILTIN_ILBC
      AudioEncoderIlbc,
#endif

      AudioEncoderG711, NotAdvertised<AudioEncoderL16>>();
}

}  // namespace webrtc
