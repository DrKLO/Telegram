/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/timestamp_aligner.h"

#include <cstdlib>
#include <limits>

#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/time_utils.h"

namespace rtc {

TimestampAligner::TimestampAligner()
    : frames_seen_(0),
      offset_us_(0),
      clip_bias_us_(0),
      prev_translated_time_us_(std::numeric_limits<int64_t>::min()),
      prev_time_offset_us_(0) {}

TimestampAligner::~TimestampAligner() {}

int64_t TimestampAligner::TranslateTimestamp(int64_t capturer_time_us,
                                             int64_t system_time_us) {
  const int64_t translated_timestamp = ClipTimestamp(
      capturer_time_us + UpdateOffset(capturer_time_us, system_time_us),
      system_time_us);
  prev_time_offset_us_ = translated_timestamp - capturer_time_us;
  return translated_timestamp;
}

int64_t TimestampAligner::TranslateTimestamp(int64_t capturer_time_us) const {
  return capturer_time_us + prev_time_offset_us_;
}

int64_t TimestampAligner::UpdateOffset(int64_t capturer_time_us,
                                       int64_t system_time_us) {
  // Estimate the offset between system monotonic time and the capturer's
  // time. The capturer is assumed to provide more
  // accurate timestamps than we get from the system time. But the
  // capturer may use its own free-running clock with a large offset and
  // a small drift compared to the system clock. So the model is
  // basically
  //
  //   y_k = c_0 + c_1 * x_k + v_k
  //
  // where x_k is the capturer's timestamp, believed to be accurate in its
  // own scale. y_k is our reading of the system clock. v_k is the
  // measurement noise, i.e., the delay from frame capture until the
  // system clock was read.
  //
  // It's possible to do (weighted) least-squares estimation of both
  // c_0 and c_1. Then we get the constants as c_1 = Cov(x,y) /
  // Var(x), and c_0 = mean(y) - c_1 * mean(x). Substituting this c_0,
  // we can rearrange the model as
  //
  //   y_k = mean(y) + (x_k - mean(x)) + (c_1 - 1) * (x_k - mean(x)) + v_k
  //
  // Now if we use a weighted average which gradually forgets old
  // values, x_k - mean(x) is bounded, of the same order as the time
  // constant (and close to constant for a steady frame rate). In
  // addition, the frequency error |c_1 - 1| should be small. Cameras
  // with a frequency error up to 3000 ppm (3 ms drift per second)
  // have been observed, but frequency errors below 100 ppm could be
  // expected of any cheap crystal.
  //
  // Bottom line is that we ignore the c_1 term, and use only the estimator
  //
  //    x_k + mean(y-x)
  //
  // where mean is plain averaging for initial samples, followed by
  // exponential averaging.

  // The input for averaging, y_k - x_k in the above notation.
  int64_t diff_us = system_time_us - capturer_time_us;
  // The deviation from the current average.
  int64_t error_us = diff_us - offset_us_;

  // If the current difference is far from the currently estimated
  // offset, the filter is reset. This could happen, e.g., if the
  // capturer's clock is reset, cameras are plugged in and out, or
  // the application process is temporarily suspended. Expected to
  // happen for the very first timestamp (`frames_seen_` = 0). The
  // threshold of 300 ms should make this unlikely in normal
  // operation, and at the same time, converging gradually rather than
  // resetting the filter should be tolerable for jumps in capturer's time
  // below this threshold.
  static const int64_t kResetThresholdUs = 300000;
  if (std::abs(error_us) > kResetThresholdUs) {
    RTC_LOG(LS_INFO) << "Resetting timestamp translation after averaging "
                     << frames_seen_ << " frames. Old offset: " << offset_us_
                     << ", new offset: " << diff_us;
    frames_seen_ = 0;
    clip_bias_us_ = 0;
  }

  static const int kWindowSize = 100;
  if (frames_seen_ < kWindowSize) {
    ++frames_seen_;
  }
  offset_us_ += error_us / frames_seen_;
  return offset_us_;
}

int64_t TimestampAligner::ClipTimestamp(int64_t filtered_time_us,
                                        int64_t system_time_us) {
  // Clip to make sure we don't produce timestamps in the future.
  int64_t time_us = filtered_time_us - clip_bias_us_;
  if (time_us > system_time_us) {
    clip_bias_us_ += time_us - system_time_us;
    time_us = system_time_us;
  }
  // Make timestamps monotonic, with a minimum inter-frame interval of 1 ms.
  else if (time_us < prev_translated_time_us_ + kMinFrameIntervalUs) {
    time_us = prev_translated_time_us_ + kMinFrameIntervalUs;
    if (time_us > system_time_us) {
      // In the anomalous case that this function is called with values of
      // `system_time_us` less than `kMinFrameIntervalUs` apart, we may output
      // timestamps with with too short inter-frame interval. We may even return
      // duplicate timestamps in case this function is called several times with
      // exactly the same `system_time_us`.
      RTC_LOG(LS_WARNING) << "too short translated timestamp interval: "
                             "system time (us) = "
                          << system_time_us << ", interval (us) = "
                          << system_time_us - prev_translated_time_us_;
      time_us = system_time_us;
    }
  }
  RTC_DCHECK_GE(time_us, prev_translated_time_us_);
  RTC_DCHECK_LE(time_us, system_time_us);
  prev_translated_time_us_ = time_us;
  return time_us;
}

}  // namespace rtc
