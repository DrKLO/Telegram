/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/audio_processing/aec3/skew_estimator.h"

#include <algorithm>

namespace webrtc {

SkewEstimator::SkewEstimator(size_t skew_history_size_log2)
    : skew_history_size_log2_(static_cast<int>(skew_history_size_log2)),
      skew_history_(1ULL << skew_history_size_log2_, 0) {}

SkewEstimator::~SkewEstimator() = default;

void SkewEstimator::Reset() {
  skew_ = 0;
  skew_sum_ = 0;
  next_index_ = 0;
  sufficient_skew_stored_ = false;
  std::fill(skew_history_.begin(), skew_history_.end(), 0);
}

absl::optional<int> SkewEstimator::GetSkewFromCapture() {
  --skew_;

  skew_sum_ += skew_ - skew_history_[next_index_];
  skew_history_[next_index_] = skew_;
  if (++next_index_ == skew_history_.size()) {
    next_index_ = 0;
    sufficient_skew_stored_ = true;
  }

  const int bias = static_cast<int>(skew_history_.size()) >> 1;
  const int average = (skew_sum_ + bias) >> skew_history_size_log2_;
  return sufficient_skew_stored_ ? absl::optional<int>(average) : absl::nullopt;
}

}  // namespace webrtc
