/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/quality_threshold.h"

#include "rtc_base/checks.h"

namespace webrtc {

QualityThreshold::QualityThreshold(int low_threshold,
                                   int high_threshold,
                                   float fraction,
                                   int max_measurements)
    : buffer_(new int[max_measurements]),
      max_measurements_(max_measurements),
      fraction_(fraction),
      low_threshold_(low_threshold),
      high_threshold_(high_threshold),
      until_full_(max_measurements),
      next_index_(0),
      sum_(0),
      count_low_(0),
      count_high_(0),
      num_high_states_(0),
      num_certain_states_(0) {
  RTC_CHECK_GT(fraction, 0.5f);
  RTC_CHECK_GT(max_measurements, 1);
  RTC_CHECK_LT(low_threshold, high_threshold);
}

QualityThreshold::~QualityThreshold() = default;

void QualityThreshold::AddMeasurement(int measurement) {
  int prev_val = until_full_ > 0 ? 0 : buffer_[next_index_];
  buffer_[next_index_] = measurement;
  next_index_ = (next_index_ + 1) % max_measurements_;

  sum_ += measurement - prev_val;

  if (until_full_ == 0) {
    if (prev_val <= low_threshold_) {
      --count_low_;
    } else if (prev_val >= high_threshold_) {
      --count_high_;
    }
  }

  if (measurement <= low_threshold_) {
    ++count_low_;
  } else if (measurement >= high_threshold_) {
    ++count_high_;
  }

  float sufficient_majority = fraction_ * max_measurements_;
  if (count_high_ >= sufficient_majority) {
    is_high_ = true;
  } else if (count_low_ >= sufficient_majority) {
    is_high_ = false;
  }

  if (until_full_ > 0)
    --until_full_;

  if (is_high_) {
    if (*is_high_)
      ++num_high_states_;
    ++num_certain_states_;
  }
}

absl::optional<bool> QualityThreshold::IsHigh() const {
  return is_high_;
}

absl::optional<double> QualityThreshold::CalculateVariance() const {
  if (until_full_ > 0) {
    return absl::nullopt;
  }

  double variance = 0;
  double mean = static_cast<double>(sum_) / max_measurements_;
  for (int i = 0; i < max_measurements_; ++i) {
    variance += (buffer_[i] - mean) * (buffer_[i] - mean);
  }
  return variance / (max_measurements_ - 1);
}

absl::optional<double> QualityThreshold::FractionHigh(
    int min_required_samples) const {
  RTC_DCHECK_GT(min_required_samples, 0);
  if (num_certain_states_ < min_required_samples)
    return absl::nullopt;

  return static_cast<double>(num_high_states_) / num_certain_states_;
}

}  // namespace webrtc
