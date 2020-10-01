/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_NUMERICS_SAMPLE_COUNTER_H_
#define RTC_BASE_NUMERICS_SAMPLE_COUNTER_H_

#include <stdint.h>

#include "absl/types/optional.h"

namespace rtc {

// Simple utility class for counting basic statistics (max./avg./variance) on
// stream of samples.
class SampleCounter {
 public:
  SampleCounter();
  ~SampleCounter();
  void Add(int sample);
  absl::optional<int> Avg(int64_t min_required_samples) const;
  absl::optional<int> Max() const;
  absl::optional<int64_t> Sum(int64_t min_required_samples) const;
  int64_t NumSamples() const;
  void Reset();
  // Adds all the samples from the |other| SampleCounter as if they were all
  // individually added using |Add(int)| method.
  void Add(const SampleCounter& other);

 protected:
  int64_t sum_ = 0;
  int64_t num_samples_ = 0;
  absl::optional<int> max_;
};

class SampleCounterWithVariance : public SampleCounter {
 public:
  SampleCounterWithVariance();
  ~SampleCounterWithVariance();
  void Add(int sample);
  absl::optional<int64_t> Variance(int64_t min_required_samples) const;
  void Reset();
  // Adds all the samples from the |other| SampleCounter as if they were all
  // individually added using |Add(int)| method.
  void Add(const SampleCounterWithVariance& other);

 private:
  int64_t sum_squared_ = 0;
};

}  // namespace rtc
#endif  // RTC_BASE_NUMERICS_SAMPLE_COUNTER_H_
