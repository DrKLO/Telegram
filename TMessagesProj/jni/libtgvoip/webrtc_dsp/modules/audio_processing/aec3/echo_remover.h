/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC3_ECHO_REMOVER_H_
#define MODULES_AUDIO_PROCESSING_AEC3_ECHO_REMOVER_H_

#include <vector>

#include "absl/types/optional.h"
#include "api/audio/echo_canceller3_config.h"
#include "api/audio/echo_control.h"
#include "modules/audio_processing/aec3/delay_estimate.h"
#include "modules/audio_processing/aec3/echo_path_variability.h"
#include "modules/audio_processing/aec3/render_buffer.h"

namespace webrtc {

// Class for removing the echo from the capture signal.
class EchoRemover {
 public:
  static EchoRemover* Create(const EchoCanceller3Config& config,
                             int sample_rate_hz);
  virtual ~EchoRemover() = default;

  // Get current metrics.
  virtual void GetMetrics(EchoControl::Metrics* metrics) const = 0;

  // Removes the echo from a block of samples from the capture signal. The
  // supplied render signal is assumed to be pre-aligned with the capture
  // signal.
  virtual void ProcessCapture(
      EchoPathVariability echo_path_variability,
      bool capture_signal_saturation,
      const absl::optional<DelayEstimate>& external_delay,
      RenderBuffer* render_buffer,
      std::vector<std::vector<float>>* capture) = 0;

  // Returns the internal delay estimate in blocks.
  virtual absl::optional<int> Delay() const = 0;

  // Updates the status on whether echo leakage is detected in the output of the
  // echo remover.
  virtual void UpdateEchoLeakageStatus(bool leakage_detected) = 0;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AEC3_ECHO_REMOVER_H_
