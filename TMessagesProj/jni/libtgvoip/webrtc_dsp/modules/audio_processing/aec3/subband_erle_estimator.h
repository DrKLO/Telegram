/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC3_SUBBAND_ERLE_ESTIMATOR_H_
#define MODULES_AUDIO_PROCESSING_AEC3_SUBBAND_ERLE_ESTIMATOR_H_

#include <stddef.h>
#include <array>
#include <memory>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "modules/audio_processing/aec3/aec3_common.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"

namespace webrtc {

// Estimates the echo return loss enhancement for each frequency subband.
class SubbandErleEstimator {
 public:
  SubbandErleEstimator(float min_erle, float max_erle_lf, float max_erle_hf);
  ~SubbandErleEstimator();

  // Resets the ERLE estimator.
  void Reset();

  // Updates the ERLE estimate.
  void Update(rtc::ArrayView<const float> X2,
              rtc::ArrayView<const float> Y2,
              rtc::ArrayView<const float> E2,
              bool converged_filter,
              bool onset_detection);

  // Returns the ERLE estimate.
  const std::array<float, kFftLengthBy2Plus1>& Erle() const { return erle_; }

  // Returns the ERLE estimate at onsets.
  const std::array<float, kFftLengthBy2Plus1>& ErleOnsets() const {
    return erle_onsets_;
  }

  void Dump(const std::unique_ptr<ApmDataDumper>& data_dumper) const;

 private:
  void UpdateBands(rtc::ArrayView<const float> X2,
                   rtc::ArrayView<const float> Y2,
                   rtc::ArrayView<const float> E2,
                   size_t start,
                   size_t stop,
                   float max_erle,
                   bool onset_detection);
  void DecreaseErlePerBandForLowRenderSignals();

  class ErleInstantaneous {
   public:
    ErleInstantaneous();
    ~ErleInstantaneous();
    // Updates the ERLE for a band with a new block. Returns absl::nullopt
    // if not enough points were accumulated for doing the estimation,
    // otherwise, it returns the ERLE. When the ERLE is returned, the
    // low_render_energy flag contains information on whether the estimation was
    // done using low level render signals.
    absl::optional<float> Update(float X2,
                                 float Y2,
                                 float E2,
                                 size_t band,
                                 bool* low_render_energy);
    // Resets the ERLE estimator to its initial state.
    void Reset();

   private:
    std::array<float, kFftLengthBy2Plus1> Y2_acum_;
    std::array<float, kFftLengthBy2Plus1> E2_acum_;
    std::array<bool, kFftLengthBy2Plus1> low_render_energy_;
    std::array<int, kFftLengthBy2Plus1> num_points_;
  };

  ErleInstantaneous instantaneous_erle_;
  std::array<float, kFftLengthBy2Plus1> erle_;
  std::array<float, kFftLengthBy2Plus1> erle_onsets_;
  std::array<bool, kFftLengthBy2Plus1> coming_onset_;
  std::array<int, kFftLengthBy2Plus1> hold_counters_;
  const float min_erle_;
  const float max_erle_lf_;
  const float max_erle_hf_;
  const bool adapt_on_low_render_;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AEC3_SUBBAND_ERLE_ESTIMATOR_H_
