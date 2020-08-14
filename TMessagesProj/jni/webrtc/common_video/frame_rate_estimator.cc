/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_video/frame_rate_estimator.h"

#include "rtc_base/time_utils.h"

namespace webrtc {

FrameRateEstimator::FrameRateEstimator(TimeDelta averaging_window)
    : averaging_window_(averaging_window) {}

void FrameRateEstimator::OnFrame(Timestamp time) {
  CullOld(time);
  frame_times_.push_back(time);
}

absl::optional<double> FrameRateEstimator::GetAverageFps() const {
  if (frame_times_.size() < 2) {
    return absl::nullopt;
  }
  TimeDelta time_span = frame_times_.back() - frame_times_.front();
  if (time_span < TimeDelta::Micros(1)) {
    return absl::nullopt;
  }
  TimeDelta avg_frame_interval = time_span / (frame_times_.size() - 1);

  return static_cast<double>(rtc::kNumMicrosecsPerSec) /
         avg_frame_interval.us();
}

absl::optional<double> FrameRateEstimator::GetAverageFps(Timestamp now) {
  CullOld(now);
  return GetAverageFps();
}

void FrameRateEstimator::Reset() {
  frame_times_.clear();
}

void FrameRateEstimator::CullOld(Timestamp now) {
  while (!frame_times_.empty() &&
         frame_times_.front() + averaging_window_ < now) {
    frame_times_.pop_front();
  }
}

}  // namespace webrtc
