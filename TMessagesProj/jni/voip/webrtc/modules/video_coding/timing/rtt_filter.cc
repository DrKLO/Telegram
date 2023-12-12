/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/timing/rtt_filter.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>

#include <algorithm>

#include "absl/algorithm/container.h"
#include "absl/container/inlined_vector.h"
#include "api/units/time_delta.h"

namespace webrtc {

namespace {

constexpr TimeDelta kMaxRtt = TimeDelta::Seconds(3);
constexpr uint32_t kFilterFactorMax = 35;
constexpr double kJumpStddev = 2.5;
constexpr double kDriftStdDev = 3.5;

}  // namespace

RttFilter::RttFilter()
    : avg_rtt_(TimeDelta::Zero()),
      var_rtt_(0),
      max_rtt_(TimeDelta::Zero()),
      jump_buf_(kMaxDriftJumpCount, TimeDelta::Zero()),
      drift_buf_(kMaxDriftJumpCount, TimeDelta::Zero()) {
  Reset();
}

void RttFilter::Reset() {
  got_non_zero_update_ = false;
  avg_rtt_ = TimeDelta::Zero();
  var_rtt_ = 0;
  max_rtt_ = TimeDelta::Zero();
  filt_fact_count_ = 1;
  absl::c_fill(jump_buf_, TimeDelta::Zero());
  absl::c_fill(drift_buf_, TimeDelta::Zero());
}

void RttFilter::Update(TimeDelta rtt) {
  if (!got_non_zero_update_) {
    if (rtt.IsZero()) {
      return;
    }
    got_non_zero_update_ = true;
  }

  // Sanity check
  if (rtt > kMaxRtt) {
    rtt = kMaxRtt;
  }

  double filt_factor = 0;
  if (filt_fact_count_ > 1) {
    filt_factor = static_cast<double>(filt_fact_count_ - 1) / filt_fact_count_;
  }
  filt_fact_count_++;
  if (filt_fact_count_ > kFilterFactorMax) {
    // This prevents filt_factor from going above
    // (_filt_fact_max - 1) / filt_fact_max_,
    // e.g., filt_fact_max_ = 50 => filt_factor = 49/50 = 0.98
    filt_fact_count_ = kFilterFactorMax;
  }
  TimeDelta old_avg = avg_rtt_;
  int64_t old_var = var_rtt_;
  avg_rtt_ = filt_factor * avg_rtt_ + (1 - filt_factor) * rtt;
  int64_t delta_ms = (rtt - avg_rtt_).ms();
  var_rtt_ = filt_factor * var_rtt_ + (1 - filt_factor) * (delta_ms * delta_ms);
  max_rtt_ = std::max(rtt, max_rtt_);
  if (!JumpDetection(rtt) || !DriftDetection(rtt)) {
    // In some cases we don't want to update the statistics
    avg_rtt_ = old_avg;
    var_rtt_ = old_var;
  }
}

bool RttFilter::JumpDetection(TimeDelta rtt) {
  TimeDelta diff_from_avg = avg_rtt_ - rtt;
  // Unit of var_rtt_ is ms^2.
  TimeDelta jump_threshold = TimeDelta::Millis(kJumpStddev * sqrt(var_rtt_));
  if (diff_from_avg.Abs() > jump_threshold) {
    bool positive_diff = diff_from_avg >= TimeDelta::Zero();
    if (!jump_buf_.empty() && positive_diff != last_jump_positive_) {
      // Since the signs differ the samples currently
      // in the buffer is useless as they represent a
      // jump in a different direction.
      jump_buf_.clear();
    }
    if (jump_buf_.size() < kMaxDriftJumpCount) {
      // Update the buffer used for the short time statistics.
      // The sign of the diff is used for updating the counter since
      // we want to use the same buffer for keeping track of when
      // the RTT jumps down and up.
      jump_buf_.push_back(rtt);
      last_jump_positive_ = positive_diff;
    }
    if (jump_buf_.size() >= kMaxDriftJumpCount) {
      // Detected an RTT jump
      ShortRttFilter(jump_buf_);
      filt_fact_count_ = kMaxDriftJumpCount + 1;
      jump_buf_.clear();
    } else {
      return false;
    }
  } else {
    jump_buf_.clear();
  }
  return true;
}

bool RttFilter::DriftDetection(TimeDelta rtt) {
  // Unit of sqrt of var_rtt_ is ms.
  TimeDelta drift_threshold = TimeDelta::Millis(kDriftStdDev * sqrt(var_rtt_));
  if (max_rtt_ - avg_rtt_ > drift_threshold) {
    if (drift_buf_.size() < kMaxDriftJumpCount) {
      // Update the buffer used for the short time statistics.
      drift_buf_.push_back(rtt);
    }
    if (drift_buf_.size() >= kMaxDriftJumpCount) {
      // Detected an RTT drift
      ShortRttFilter(drift_buf_);
      filt_fact_count_ = kMaxDriftJumpCount + 1;
      drift_buf_.clear();
    }
  } else {
    drift_buf_.clear();
  }
  return true;
}

void RttFilter::ShortRttFilter(const BufferList& buf) {
  RTC_DCHECK_EQ(buf.size(), kMaxDriftJumpCount);
  max_rtt_ = TimeDelta::Zero();
  avg_rtt_ = TimeDelta::Zero();
  for (const TimeDelta& rtt : buf) {
    if (rtt > max_rtt_) {
      max_rtt_ = rtt;
    }
    avg_rtt_ += rtt;
  }
  avg_rtt_ = avg_rtt_ / static_cast<double>(buf.size());
}

TimeDelta RttFilter::Rtt() const {
  return max_rtt_;
}

}  // namespace webrtc
