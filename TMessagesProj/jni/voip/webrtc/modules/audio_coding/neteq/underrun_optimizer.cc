/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/underrun_optimizer.h"

#include <algorithm>

namespace webrtc {

namespace {

constexpr int kDelayBuckets = 100;
constexpr int kBucketSizeMs = 20;

}  // namespace

UnderrunOptimizer::UnderrunOptimizer(const TickTimer* tick_timer,
                                     int histogram_quantile,
                                     int forget_factor,
                                     absl::optional<int> start_forget_weight,
                                     absl::optional<int> resample_interval_ms)
    : tick_timer_(tick_timer),
      histogram_(kDelayBuckets, forget_factor, start_forget_weight),
      histogram_quantile_(histogram_quantile),
      resample_interval_ms_(resample_interval_ms) {}

void UnderrunOptimizer::Update(int relative_delay_ms) {
  absl::optional<int> histogram_update;
  if (resample_interval_ms_) {
    if (!resample_stopwatch_) {
      resample_stopwatch_ = tick_timer_->GetNewStopwatch();
    }
    if (static_cast<int>(resample_stopwatch_->ElapsedMs()) >
        *resample_interval_ms_) {
      histogram_update = max_delay_in_interval_ms_;
      resample_stopwatch_ = tick_timer_->GetNewStopwatch();
      max_delay_in_interval_ms_ = 0;
    }
    max_delay_in_interval_ms_ =
        std::max(max_delay_in_interval_ms_, relative_delay_ms);
  } else {
    histogram_update = relative_delay_ms;
  }
  if (!histogram_update) {
    return;
  }

  const int index = *histogram_update / kBucketSizeMs;
  if (index < histogram_.NumBuckets()) {
    // Maximum delay to register is 2000 ms.
    histogram_.Add(index);
  }
  int bucket_index = histogram_.Quantile(histogram_quantile_);
  optimal_delay_ms_ = (1 + bucket_index) * kBucketSizeMs;
}

void UnderrunOptimizer::Reset() {
  histogram_.Reset();
  resample_stopwatch_.reset();
  max_delay_in_interval_ms_ = 0;
  optimal_delay_ms_.reset();
}

}  // namespace webrtc
