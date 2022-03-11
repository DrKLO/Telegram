/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/reorder_optimizer.h"

#include <algorithm>
#include <limits>
#include <vector>

namespace webrtc {

namespace {

constexpr int kDelayBuckets = 100;
constexpr int kBucketSizeMs = 20;

}  // namespace

ReorderOptimizer::ReorderOptimizer(int forget_factor,
                                   int ms_per_loss_percent,
                                   absl::optional<int> start_forget_weight)
    : histogram_(kDelayBuckets, forget_factor, start_forget_weight),
      ms_per_loss_percent_(ms_per_loss_percent) {}

void ReorderOptimizer::Update(int relative_delay_ms,
                              bool reordered,
                              int base_delay_ms) {
  const int index = reordered ? relative_delay_ms / kBucketSizeMs : 0;
  if (index < histogram_.NumBuckets()) {
    // Maximum delay to register is 2000 ms.
    histogram_.Add(index);
  }
  int bucket_index = MinimizeCostFunction(base_delay_ms);
  optimal_delay_ms_ = (1 + bucket_index) * kBucketSizeMs;
}

void ReorderOptimizer::Reset() {
  histogram_.Reset();
  optimal_delay_ms_.reset();
}

int ReorderOptimizer::MinimizeCostFunction(int base_delay_ms) const {
  const std::vector<int>& buckets = histogram_.buckets();

  // Values are calculated in Q30.
  int64_t loss_probability = 1 << 30;
  int64_t min_cost = std::numeric_limits<int64_t>::max();
  int min_bucket = 0;
  for (int i = 0; i < static_cast<int>(buckets.size()); ++i) {
    loss_probability -= buckets[i];
    int64_t delay_ms =
        static_cast<int64_t>(std::max(0, i * kBucketSizeMs - base_delay_ms))
        << 30;
    int64_t cost = delay_ms + 100 * ms_per_loss_percent_ * loss_probability;

    if (cost < min_cost) {
      min_cost = cost;
      min_bucket = i;
    }
    if (loss_probability == 0) {
      break;
    }
  }

  return min_bucket;
}

}  // namespace webrtc
