/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef VIDEO_QUALITY_THRESHOLD_H_
#define VIDEO_QUALITY_THRESHOLD_H_

#include <memory>

#include "absl/types/optional.h"

namespace webrtc {

class QualityThreshold {
 public:
  // Both thresholds are inclusive, i.e. measurement >= high signifies a high
  // state, while measurement <= low signifies a low state.
  QualityThreshold(int low_threshold,
                   int high_threshold,
                   float fraction,
                   int max_measurements);
  ~QualityThreshold();

  void AddMeasurement(int measurement);
  absl::optional<bool> IsHigh() const;
  absl::optional<double> CalculateVariance() const;
  absl::optional<double> FractionHigh(int min_required_samples) const;

 private:
  const std::unique_ptr<int[]> buffer_;
  const int max_measurements_;
  const float fraction_;
  const int low_threshold_;
  const int high_threshold_;
  int until_full_;
  int next_index_;
  absl::optional<bool> is_high_;
  int sum_;
  int count_low_;
  int count_high_;
  int num_high_states_;
  int num_certain_states_;
};

}  // namespace webrtc

#endif  // VIDEO_QUALITY_THRESHOLD_H_
