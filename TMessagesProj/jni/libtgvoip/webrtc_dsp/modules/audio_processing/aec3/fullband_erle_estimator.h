/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC3_FULLBAND_ERLE_ESTIMATOR_H_
#define MODULES_AUDIO_PROCESSING_AEC3_FULLBAND_ERLE_ESTIMATOR_H_

#include <memory>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"

namespace webrtc {

// Estimates the echo return loss enhancement using the energy of all the
// freuquency bands.
class FullBandErleEstimator {
 public:
  FullBandErleEstimator(float min_erle, float max_erle_lf);
  ~FullBandErleEstimator();
  // Resets the ERLE estimator.
  void Reset();

  // Updates the ERLE estimator.
  void Update(rtc::ArrayView<const float> X2,
              rtc::ArrayView<const float> Y2,
              rtc::ArrayView<const float> E2,
              bool converged_filter);

  // Returns the fullband ERLE estimates in log2 units.
  float FullbandErleLog2() const { return erle_time_domain_log2_; }

  // Returns an estimation of the current linear filter quality. It returns a
  // float number between 0 and 1 mapping 1 to the highest possible quality.
  absl::optional<float> GetInstLinearQualityEstimate() const {
    return instantaneous_erle_.GetQualityEstimate();
  }

  void Dump(const std::unique_ptr<ApmDataDumper>& data_dumper) const;

 private:
  class ErleInstantaneous {
   public:
    ErleInstantaneous();
    ~ErleInstantaneous();

    // Updates the estimator with a new point, returns true
    // if the instantaneous ERLE was updated due to having enough
    // points for performing the estimate.
    bool Update(const float Y2_sum, const float E2_sum);
    // Resets the instantaneous ERLE estimator to its initial state.
    void Reset();
    // Resets the members related with an instantaneous estimate.
    void ResetAccumulators();
    // Returns the instantaneous ERLE in log2 units.
    absl::optional<float> GetInstErleLog2() const { return erle_log2_; }
    // Gets an indication between 0 and 1 of the performance of the linear
    // filter for the current time instant.
    absl::optional<float> GetQualityEstimate() const {
      return erle_log2_ ? absl::optional<float>(inst_quality_estimate_)
                        : absl::nullopt;
    }
    void Dump(const std::unique_ptr<ApmDataDumper>& data_dumper) const;

   private:
    void UpdateMaxMin();
    void UpdateQualityEstimate();
    absl::optional<float> erle_log2_;
    float inst_quality_estimate_;
    float max_erle_log2_;
    float min_erle_log2_;
    float Y2_acum_;
    float E2_acum_;
    int num_points_;
  };

  int hold_counter_time_domain_;
  float erle_time_domain_log2_;
  const float min_erle_log2_;
  const float max_erle_lf_log2;
  ErleInstantaneous instantaneous_erle_;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AEC3_FULLBAND_ERLE_ESTIMATOR_H_
