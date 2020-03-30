/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC3_SKEW_ESTIMATOR_H_
#define MODULES_AUDIO_PROCESSING_AEC3_SKEW_ESTIMATOR_H_

#include <stddef.h>
#include <vector>

#include "absl/types/optional.h"
#include "rtc_base/constructormagic.h"

namespace webrtc {

// Estimator of API call skew between render and capture.
class SkewEstimator {
 public:
  explicit SkewEstimator(size_t skew_history_size_log2);
  ~SkewEstimator();

  // Resets the estimation.
  void Reset();

  // Updates the skew data for a render call.
  void LogRenderCall() { ++skew_; }

  // Updates and computes the skew at a capture call. Returns an optional which
  // is non-null if a reliable skew has been found.
  absl::optional<int> GetSkewFromCapture();

 private:
  const int skew_history_size_log2_;
  std::vector<float> skew_history_;
  int skew_ = 0;
  int skew_sum_ = 0;
  size_t next_index_ = 0;
  bool sufficient_skew_stored_ = false;

  RTC_DISALLOW_COPY_AND_ASSIGN(SkewEstimator);
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AEC3_SKEW_ESTIMATOR_H_
