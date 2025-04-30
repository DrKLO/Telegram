/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/audio_codecs/opus/audio_encoder_multi_channel_opus_config.h"

namespace webrtc {

namespace {
constexpr int kDefaultComplexity = 9;
}  // namespace

AudioEncoderMultiChannelOpusConfig::AudioEncoderMultiChannelOpusConfig()
    : frame_size_ms(kDefaultFrameSizeMs),
      num_channels(1),
      application(ApplicationMode::kVoip),
      bitrate_bps(32000),
      fec_enabled(false),
      cbr_enabled(false),
      dtx_enabled(false),
      max_playback_rate_hz(48000),
      complexity(kDefaultComplexity),
      num_streams(-1),
      coupled_streams(-1) {}
AudioEncoderMultiChannelOpusConfig::AudioEncoderMultiChannelOpusConfig(
    const AudioEncoderMultiChannelOpusConfig&) = default;
AudioEncoderMultiChannelOpusConfig::~AudioEncoderMultiChannelOpusConfig() =
    default;
AudioEncoderMultiChannelOpusConfig&
AudioEncoderMultiChannelOpusConfig::operator=(
    const AudioEncoderMultiChannelOpusConfig&) = default;

bool AudioEncoderMultiChannelOpusConfig::IsOk() const {
  if (frame_size_ms <= 0 || frame_size_ms % 10 != 0)
    return false;
  if (num_channels >= 255) {
    return false;
  }
  if (bitrate_bps < kMinBitrateBps || bitrate_bps > kMaxBitrateBps)
    return false;
  if (complexity < 0 || complexity > 10)
    return false;

  // Check the lengths:
  if (num_streams < 0 || coupled_streams < 0) {
    return false;
  }
  if (num_streams < coupled_streams) {
    return false;
  }
  if (channel_mapping.size() != static_cast<size_t>(num_channels)) {
    return false;
  }

  // Every mono stream codes one channel, every coupled stream codes two. This
  // is the total coded channel count:
  const int max_coded_channel = num_streams + coupled_streams;
  for (const auto& x : channel_mapping) {
    // Coded channels >= max_coded_channel don't exist. Except for 255, which
    // tells Opus to ignore input channel x.
    if (x >= max_coded_channel && x != 255) {
      return false;
    }
  }

  // Inverse mapping.
  constexpr int kNotSet = -1;
  std::vector<int> coded_channels_to_input_channels(max_coded_channel, kNotSet);
  for (size_t i = 0; i < num_channels; ++i) {
    if (channel_mapping[i] == 255) {
      continue;
    }

    // If it's not ignored, put it in the inverted mapping. But first check if
    // we've told Opus to use another input channel for this coded channel:
    const int coded_channel = channel_mapping[i];
    if (coded_channels_to_input_channels[coded_channel] != kNotSet) {
      // Coded channel `coded_channel` comes from both input channels
      // `coded_channels_to_input_channels[coded_channel]` and `i`.
      return false;
    }

    coded_channels_to_input_channels[coded_channel] = i;
  }

  // Check that we specified what input the encoder should use to produce
  // every coded channel.
  for (int i = 0; i < max_coded_channel; ++i) {
    if (coded_channels_to_input_channels[i] == kNotSet) {
      // Coded channel `i` has unspecified input channel.
      return false;
    }
  }

  if (num_channels > 255 || max_coded_channel >= 255) {
    return false;
  }
  return true;
}

}  // namespace webrtc
