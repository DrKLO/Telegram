/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/histogram.h"

#include <algorithm>
#include <cstdlib>
#include <numeric>

#include "absl/types/optional.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_conversions.h"

namespace webrtc {

Histogram::Histogram(size_t num_buckets,
                     int forget_factor,
                     absl::optional<double> start_forget_weight)
    : buckets_(num_buckets, 0),
      forget_factor_(0),
      base_forget_factor_(forget_factor),
      add_count_(0),
      start_forget_weight_(start_forget_weight) {
  RTC_DCHECK_LT(base_forget_factor_, 1 << 15);
}

Histogram::~Histogram() {}

// Each element in the vector is first multiplied by the forgetting factor
// `forget_factor_`. Then the vector element indicated by `iat_packets` is then
// increased (additive) by 1 - `forget_factor_`. This way, the probability of
// `value` is slightly increased, while the sum of the histogram remains
// constant (=1).
// Due to inaccuracies in the fixed-point arithmetic, the histogram may no
// longer sum up to 1 (in Q30) after the update. To correct this, a correction
// term is added or subtracted from the first element (or elements) of the
// vector.
// The forgetting factor `forget_factor_` is also updated. When the DelayManager
// is reset, the factor is set to 0 to facilitate rapid convergence in the
// beginning. With each update of the histogram, the factor is increased towards
// the steady-state value `base_forget_factor_`.
void Histogram::Add(int value) {
  RTC_DCHECK(value >= 0);
  RTC_DCHECK(value < static_cast<int>(buckets_.size()));
  int vector_sum = 0;  // Sum up the vector elements as they are processed.
  // Multiply each element in `buckets_` with `forget_factor_`.
  for (int& bucket : buckets_) {
    bucket = (static_cast<int64_t>(bucket) * forget_factor_) >> 15;
    vector_sum += bucket;
  }

  // Increase the probability for the currently observed inter-arrival time
  // by 1 - `forget_factor_`. The factor is in Q15, `buckets_` in Q30.
  // Thus, left-shift 15 steps to obtain result in Q30.
  buckets_[value] += (32768 - forget_factor_) << 15;
  vector_sum += (32768 - forget_factor_) << 15;  // Add to vector sum.

  // `buckets_` should sum up to 1 (in Q30), but it may not due to
  // fixed-point rounding errors.
  vector_sum -= 1 << 30;  // Should be zero. Compensate if not.
  if (vector_sum != 0) {
    // Modify a few values early in `buckets_`.
    int flip_sign = vector_sum > 0 ? -1 : 1;
    for (int& bucket : buckets_) {
      // Add/subtract 1/16 of the element, but not more than `vector_sum`.
      int correction = flip_sign * std::min(std::abs(vector_sum), bucket >> 4);
      bucket += correction;
      vector_sum += correction;
      if (std::abs(vector_sum) == 0) {
        break;
      }
    }
  }
  RTC_DCHECK(vector_sum == 0);  // Verify that the above is correct.

  ++add_count_;

  // Update `forget_factor_` (changes only during the first seconds after a
  // reset). The factor converges to `base_forget_factor_`.
  if (start_forget_weight_) {
    if (forget_factor_ != base_forget_factor_) {
      int old_forget_factor = forget_factor_;
      int forget_factor =
          (1 << 15) * (1 - start_forget_weight_.value() / (add_count_ + 1));
      forget_factor_ =
          std::max(0, std::min(base_forget_factor_, forget_factor));
      // The histogram is updated recursively by forgetting the old histogram
      // with `forget_factor_` and adding a new sample multiplied by |1 -
      // forget_factor_|. We need to make sure that the effective weight on the
      // new sample is no smaller than those on the old samples, i.e., to
      // satisfy the following DCHECK.
      RTC_DCHECK_GE((1 << 15) - forget_factor_,
                    ((1 << 15) - old_forget_factor) * forget_factor_ >> 15);
    }
  } else {
    forget_factor_ += (base_forget_factor_ - forget_factor_ + 3) >> 2;
  }
}

int Histogram::Quantile(int probability) {
  // Find the bucket for which the probability of observing an
  // inter-arrival time larger than or equal to `index` is larger than or
  // equal to `probability`. The sought probability is estimated using
  // the histogram as the reverse cumulant PDF, i.e., the sum of elements from
  // the end up until `index`. Now, since the sum of all elements is 1
  // (in Q30) by definition, and since the solution is often a low value for
  // `iat_index`, it is more efficient to start with `sum` = 1 and subtract
  // elements from the start of the histogram.
  int inverse_probability = (1 << 30) - probability;
  size_t index = 0;        // Start from the beginning of `buckets_`.
  int sum = 1 << 30;       // Assign to 1 in Q30.
  sum -= buckets_[index];

  while ((sum > inverse_probability) && (index < buckets_.size() - 1)) {
    // Subtract the probabilities one by one until the sum is no longer greater
    // than `inverse_probability`.
    ++index;
    sum -= buckets_[index];
  }
  return static_cast<int>(index);
}

// Set the histogram vector to an exponentially decaying distribution
// buckets_[i] = 0.5^(i+1), i = 0, 1, 2, ...
// buckets_ is in Q30.
void Histogram::Reset() {
  // Set temp_prob to (slightly more than) 1 in Q14. This ensures that the sum
  // of buckets_ is 1.
  uint16_t temp_prob = 0x4002;  // 16384 + 2 = 100000000000010 binary.
  for (int& bucket : buckets_) {
    temp_prob >>= 1;
    bucket = temp_prob << 16;
  }
  forget_factor_ = 0;  // Adapt the histogram faster for the first few packets.
  add_count_ = 0;
}

int Histogram::NumBuckets() const {
  return buckets_.size();
}

}  // namespace webrtc
