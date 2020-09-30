/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/audio_network_adaptor/include/audio_network_adaptor_config.h"

namespace webrtc {

AudioEncoderRuntimeConfig::AudioEncoderRuntimeConfig() = default;

AudioEncoderRuntimeConfig::AudioEncoderRuntimeConfig(
    const AudioEncoderRuntimeConfig& other) = default;

AudioEncoderRuntimeConfig::~AudioEncoderRuntimeConfig() = default;

AudioEncoderRuntimeConfig& AudioEncoderRuntimeConfig::operator=(
    const AudioEncoderRuntimeConfig& other) = default;

bool AudioEncoderRuntimeConfig::operator==(
    const AudioEncoderRuntimeConfig& other) const {
  return bitrate_bps == other.bitrate_bps &&
         frame_length_ms == other.frame_length_ms &&
         uplink_packet_loss_fraction == other.uplink_packet_loss_fraction &&
         enable_fec == other.enable_fec && enable_dtx == other.enable_dtx &&
         num_channels == other.num_channels;
}

}  // namespace webrtc
