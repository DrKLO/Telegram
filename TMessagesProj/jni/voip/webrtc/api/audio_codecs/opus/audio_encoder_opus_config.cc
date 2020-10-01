/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/audio_codecs/opus/audio_encoder_opus_config.h"

namespace webrtc {

namespace {

#if defined(WEBRTC_ANDROID) || defined(WEBRTC_IOS) || defined(WEBRTC_ARCH_ARM)
// If we are on Android, iOS and/or ARM, use a lower complexity setting by
// default, to save encoder complexity.
constexpr int kDefaultComplexity = 5;
#else
constexpr int kDefaultComplexity = 9;
#endif

constexpr int kDefaultLowRateComplexity =
    WEBRTC_OPUS_VARIABLE_COMPLEXITY ? 9 : kDefaultComplexity;

}  // namespace

constexpr int AudioEncoderOpusConfig::kDefaultFrameSizeMs;
constexpr int AudioEncoderOpusConfig::kMinBitrateBps;
constexpr int AudioEncoderOpusConfig::kMaxBitrateBps;

AudioEncoderOpusConfig::AudioEncoderOpusConfig()
    : frame_size_ms(kDefaultFrameSizeMs),
      sample_rate_hz(48000),
      num_channels(1),
      application(ApplicationMode::kVoip),
      bitrate_bps(32000),
      fec_enabled(false),
      cbr_enabled(false),
      max_playback_rate_hz(48000),
      complexity(kDefaultComplexity),
      low_rate_complexity(kDefaultLowRateComplexity),
      complexity_threshold_bps(12500),
      complexity_threshold_window_bps(1500),
      dtx_enabled(false),
      uplink_bandwidth_update_interval_ms(200),
      payload_type(-1) {}
AudioEncoderOpusConfig::AudioEncoderOpusConfig(const AudioEncoderOpusConfig&) =
    default;
AudioEncoderOpusConfig::~AudioEncoderOpusConfig() = default;
AudioEncoderOpusConfig& AudioEncoderOpusConfig::operator=(
    const AudioEncoderOpusConfig&) = default;

bool AudioEncoderOpusConfig::IsOk() const {
  if (frame_size_ms <= 0 || frame_size_ms % 10 != 0)
    return false;
  if (sample_rate_hz != 16000 && sample_rate_hz != 48000) {
    // Unsupported input sample rate. (libopus supports a few other rates as
    // well; we can add support for them when needed.)
    return false;
  }
  if (num_channels < 0 || num_channels >= 255) {
    return false;
  }
  if (!bitrate_bps)
    return false;
  if (*bitrate_bps < kMinBitrateBps || *bitrate_bps > kMaxBitrateBps)
    return false;
  if (complexity < 0 || complexity > 10)
    return false;
  if (low_rate_complexity < 0 || low_rate_complexity > 10)
    return false;
  return true;
}
}  // namespace webrtc
