/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/numerics/histogram_percentile_counter.h"

#include <algorithm>
#include <cmath>

#include "rtc_base/checks.h"

namespace rtc {
HistogramPercentileCounter::HistogramPercentileCounter(
    uint32_t long_tail_boundary)
    : histogram_low_(size_t{long_tail_boundary}),
      long_tail_boundary_(long_tail_boundary),
      total_elements_(0),
      total_elements_low_(0) {}

HistogramPercentileCounter::~HistogramPercentileCounter() = default;

void HistogramPercentileCounter::Add(const HistogramPercentileCounter& other) {
  for (uint32_t value = 0; value < other.long_tail_boundary_; ++value) {
    Add(value, other.histogram_low_[value]);
  }
  for (const auto& it : histogram_high_) {
    Add(it.first, it.second);
  }
}

void HistogramPercentileCounter::Add(uint32_t value, size_t count) {
  if (value < long_tail_boundary_) {
    histogram_low_[value] += count;
    total_elements_low_ += count;
  } else {
    histogram_high_[value] += count;
  }
  total_elements_ += count;
}

void HistogramPercentileCounter::Add(uint32_t value) {
  Add(value, 1);
}

absl::optional<uint32_t> HistogramPercentileCounter::GetPercentile(
    float fraction) {
  RTC_CHECK_LE(fraction, 1.0);
  RTC_CHECK_GE(fraction, 0.0);
  if (total_elements_ == 0)
    return absl::nullopt;
  size_t elements_to_skip = static_cast<size_t>(
      std::max(0.0f, std::ceil(total_elements_ * fraction) - 1));
  if (elements_to_skip >= total_elements_)
    elements_to_skip = total_elements_ - 1;
  if (elements_to_skip < total_elements_low_) {
    for (uint32_t value = 0; value < long_tail_boundary_; ++value) {
      if (elements_to_skip < histogram_low_[value])
        return value;
      elements_to_skip -= histogram_low_[value];
    }
  } else {
    elements_to_skip -= total_elements_low_;
    for (const auto& it : histogram_high_) {
      if (elements_to_skip < it.second)
        return it.first;
      elements_to_skip -= it.second;
    }
  }
  RTC_NOTREACHED();
  return absl::nullopt;
}

}  // namespace rtc
