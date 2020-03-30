/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC3_REVERB_MODEL_ESTIMATOR_H_
#define MODULES_AUDIO_PROCESSING_AEC3_REVERB_MODEL_ESTIMATOR_H_

#include <array>
#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/audio/echo_canceller3_config.h"
#include "modules/audio_processing/aec3/aec3_common.h"  // kFftLengthBy2Plus1
#include "modules/audio_processing/aec3/reverb_decay_estimator.h"
#include "modules/audio_processing/aec3/reverb_frequency_response.h"

namespace webrtc {

class ApmDataDumper;

// Class for estimating the model parameters for the reverberant echo.
class ReverbModelEstimator {
 public:
  explicit ReverbModelEstimator(const EchoCanceller3Config& config);
  ~ReverbModelEstimator();

  // Updates the estimates based on new data.
  void Update(rtc::ArrayView<const float> impulse_response,
              const std::vector<std::array<float, kFftLengthBy2Plus1>>&
                  frequency_response,
              const absl::optional<float>& linear_filter_quality,
              int filter_delay_blocks,
              bool usable_linear_estimate,
              bool stationary_block);

  // Returns the exponential decay of the reverberant echo.
  float ReverbDecay() const { return reverb_decay_estimator_.Decay(); }

  // Return the frequency response of the reverberant echo.
  rtc::ArrayView<const float> GetReverbFrequencyResponse() const {
    return reverb_frequency_response_.FrequencyResponse();
  }

  // Dumps debug data.
  void Dump(ApmDataDumper* data_dumper) const {
    reverb_decay_estimator_.Dump(data_dumper);
  }

 private:
  ReverbDecayEstimator reverb_decay_estimator_;
  ReverbFrequencyResponse reverb_frequency_response_;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AEC3_REVERB_MODEL_ESTIMATOR_H_
