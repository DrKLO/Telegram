/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/optionally_built_submodule_creators.h"

#include <memory>

#include "modules/audio_processing/transient/transient_suppressor_impl.h"

namespace webrtc {

std::unique_ptr<TransientSuppressor> CreateTransientSuppressor(
    const ApmSubmoduleCreationOverrides& overrides,
    TransientSuppressor::VadMode vad_mode,
    int sample_rate_hz,
    int detection_rate_hz,
    int num_channels) {
#ifdef WEBRTC_EXCLUDE_TRANSIENT_SUPPRESSOR
  return nullptr;
#else
  if (overrides.transient_suppression) {
    return nullptr;
  }
  return std::make_unique<TransientSuppressorImpl>(
      vad_mode, sample_rate_hz, detection_rate_hz, num_channels);
#endif
}

}  // namespace webrtc
