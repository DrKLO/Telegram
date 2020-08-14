/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/rate_statistics.h"

#include <algorithm>
#include <limits>
#include <memory>

#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"

namespace webrtc {

RateStatistics::Bucket::Bucket(int64_t timestamp)
    : sum(0), num_samples(0), timestamp(timestamp) {}

RateStatistics::RateStatistics(int64_t window_size_ms, float scale)
    : accumulated_count_(0),
      first_timestamp_(-1),
      num_samples_(0),
      scale_(scale),
      max_window_size_ms_(window_size_ms),
      current_window_size_ms_(max_window_size_ms_) {}

RateStatistics::RateStatistics(const RateStatistics& other)
    : buckets_(other.buckets_),
      accumulated_count_(other.accumulated_count_),
      first_timestamp_(other.first_timestamp_),
      overflow_(other.overflow_),
      num_samples_(other.num_samples_),
      scale_(other.scale_),
      max_window_size_ms_(other.max_window_size_ms_),
      current_window_size_ms_(other.current_window_size_ms_) {}

RateStatistics::RateStatistics(RateStatistics&& other) = default;

RateStatistics::~RateStatistics() {}

void RateStatistics::Reset() {
  accumulated_count_ = 0;
  overflow_ = false;
  num_samples_ = 0;
  first_timestamp_ = -1;
  current_window_size_ms_ = max_window_size_ms_;
  buckets_.clear();
}

void RateStatistics::Update(int64_t count, int64_t now_ms) {
  RTC_DCHECK_GE(count, 0);

  EraseOld(now_ms);
  if (first_timestamp_ == -1) {
    first_timestamp_ = now_ms;
  }

  if (buckets_.empty() || now_ms != buckets_.back().timestamp) {
    if (!buckets_.empty() && now_ms < buckets_.back().timestamp) {
      RTC_LOG(LS_WARNING) << "Timestamp " << now_ms
                          << " is before the last added "
                             "timestamp in the rate window: "
                          << buckets_.back().timestamp << ", aligning to that.";
      now_ms = buckets_.back().timestamp;
    }
    buckets_.emplace_back(now_ms);
  }
  Bucket& last_bucket = buckets_.back();
  last_bucket.sum += count;
  ++last_bucket.num_samples;

  if (std::numeric_limits<int64_t>::max() - accumulated_count_ > count) {
    accumulated_count_ += count;
  } else {
    overflow_ = true;
  }
  ++num_samples_;
}

absl::optional<int64_t> RateStatistics::Rate(int64_t now_ms) const {
  // Yeah, this const_cast ain't pretty, but the alternative is to declare most
  // of the members as mutable...
  const_cast<RateStatistics*>(this)->EraseOld(now_ms);

  int active_window_size = 0;
  if (first_timestamp_ != -1) {
    if (first_timestamp_ <= now_ms - current_window_size_ms_) {
      // Count window as full even if no data points currently in view, if the
      // data stream started before the window.
      active_window_size = current_window_size_ms_;
    } else {
      // Size of a single bucket is 1ms, so even if now_ms == first_timestmap_
      // the window size should be 1.
      active_window_size = now_ms - first_timestamp_ + 1;
    }
  }

  // If window is a single bucket or there is only one sample in a data set that
  // has not grown to the full window size, or if the accumulator has
  // overflowed, treat this as rate unavailable.
  if (num_samples_ == 0 || active_window_size <= 1 ||
      (num_samples_ <= 1 &&
       rtc::SafeLt(active_window_size, current_window_size_ms_)) ||
      overflow_) {
    return absl::nullopt;
  }

  float scale = static_cast<float>(scale_) / active_window_size;
  float result = accumulated_count_ * scale + 0.5f;

  // Better return unavailable rate than garbage value (undefined behavior).
  if (result > static_cast<float>(std::numeric_limits<int64_t>::max())) {
    return absl::nullopt;
  }
  return rtc::dchecked_cast<int64_t>(result);
}

void RateStatistics::EraseOld(int64_t now_ms) {
  // New oldest time that is included in data set.
  const int64_t new_oldest_time = now_ms - current_window_size_ms_ + 1;

  // Loop over buckets and remove too old data points.
  while (!buckets_.empty() && buckets_.front().timestamp < new_oldest_time) {
    const Bucket& oldest_bucket = buckets_.front();
    RTC_DCHECK_GE(accumulated_count_, oldest_bucket.sum);
    RTC_DCHECK_GE(num_samples_, oldest_bucket.num_samples);
    accumulated_count_ -= oldest_bucket.sum;
    num_samples_ -= oldest_bucket.num_samples;
    buckets_.pop_front();
    // This does not clear overflow_ even when counter is empty.
    // TODO(https://bugs.webrtc.org/11247): Consider if overflow_ can be reset.
  }
}

bool RateStatistics::SetWindowSize(int64_t window_size_ms, int64_t now_ms) {
  if (window_size_ms <= 0 || window_size_ms > max_window_size_ms_)
    return false;
  if (first_timestamp_ != -1) {
    // If the window changes (e.g. decreases - removing data point, then
    // increases again) we need to update the first timestamp mark as
    // otherwise it indicates the window coveres a region of zeros, suddenly
    // under-estimating the rate.
    first_timestamp_ = std::max(first_timestamp_, now_ms - window_size_ms + 1);
  }
  current_window_size_ms_ = window_size_ms;
  EraseOld(now_ms);
  return true;
}

}  // namespace webrtc
