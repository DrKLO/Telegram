/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/audio/echo_canceller3_factory.h"

#include <memory>

#include "modules/audio_processing/aec3/echo_canceller3.h"

namespace webrtc {

EchoCanceller3Factory::EchoCanceller3Factory() {}

EchoCanceller3Factory::EchoCanceller3Factory(const EchoCanceller3Config& config)
    : config_(config) {}

std::unique_ptr<EchoControl> EchoCanceller3Factory::Create(
    int sample_rate_hz,
    int num_render_channels,
    int num_capture_channels) {
  return std::make_unique<EchoCanceller3>(
      config_, /*multichannel_config=*/absl::nullopt, sample_rate_hz,
      num_render_channels, num_capture_channels);
}

}  // namespace webrtc
