/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/numerics/moving_average.h"

#include <algorithm>

#include "rtc_base/checks.h"

namespace rtc {

MovingAverage::MovingAverage(size_t window_size) : history_(window_size, 0) {
  // Limit window size to avoid overflow.
  RTC_DCHECK_LE(window_size, (int64_t{1} << 32) - 1);
}
MovingAverage::~MovingAverage() = default;

void MovingAverage::AddSample(int sample) {
  count_++;
  size_t index = count_ % history_.size();
  if (count_ > history_.size())
    sum_ -= history_[index];
  sum_ += sample;
  history_[index] = sample;
}

absl::optional<int> MovingAverage::GetAverageRoundedDown() const {
  if (count_ == 0)
    return absl::nullopt;
  return sum_ / Size();
}

absl::optional<int> MovingAverage::GetAverageRoundedToClosest() const {
  if (count_ == 0)
    return absl::nullopt;
  return (sum_ + Size() / 2) / Size();
}

absl::optional<double> MovingAverage::GetUnroundedAverage() const {
  if (count_ == 0)
    return absl::nullopt;
  return sum_ / static_cast<double>(Size());
}

void MovingAverage::Reset() {
  count_ = 0;
  sum_ = 0;
}

size_t MovingAverage::Size() const {
  return std::min(count_, history_.size());
}
}  // namespace rtc
