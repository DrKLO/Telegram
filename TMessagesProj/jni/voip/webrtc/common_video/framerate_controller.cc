/*
 *  Copyright 2021 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_video/framerate_controller.h"

#include <limits>

#include "rtc_base/time_utils.h"

namespace webrtc {
namespace {
constexpr double kMinFramerate = 0.5;
}  // namespace

FramerateController::FramerateController()
    : FramerateController(std::numeric_limits<double>::max()) {}

FramerateController::FramerateController(double max_framerate)
    : max_framerate_(max_framerate) {}

FramerateController::~FramerateController() {}

void FramerateController::SetMaxFramerate(double max_framerate) {
  max_framerate_ = max_framerate;
}

double FramerateController::GetMaxFramerate() const {
  return max_framerate_;
}

bool FramerateController::ShouldDropFrame(int64_t in_timestamp_ns) {
  if (max_framerate_ < kMinFramerate)
    return true;

  // If `max_framerate_` is not set (i.e. maxdouble), `frame_interval_ns` is
  // rounded to 0.
  int64_t frame_interval_ns = rtc::kNumNanosecsPerSec / max_framerate_;
  if (frame_interval_ns <= 0) {
    // Frame rate throttling not enabled.
    return false;
  }

  if (next_frame_timestamp_ns_) {
    // Time until next frame should be outputted.
    const int64_t time_until_next_frame_ns =
        (*next_frame_timestamp_ns_ - in_timestamp_ns);
    // Continue if timestamp is within expected range.
    if (std::abs(time_until_next_frame_ns) < 2 * frame_interval_ns) {
      // Drop if a frame shouldn't be outputted yet.
      if (time_until_next_frame_ns > 0)
        return true;
      // Time to output new frame.
      *next_frame_timestamp_ns_ += frame_interval_ns;
      return false;
    }
  }

  // First timestamp received or timestamp is way outside expected range, so
  // reset. Set first timestamp target to just half the interval to prefer
  // keeping frames in case of jitter.
  next_frame_timestamp_ns_ = in_timestamp_ns + frame_interval_ns / 2;
  return false;
}

void FramerateController::Reset() {
  max_framerate_ = std::numeric_limits<double>::max();
  next_frame_timestamp_ns_ = absl::nullopt;
}

void FramerateController::KeepFrame(int64_t in_timestamp_ns) {
  if (ShouldDropFrame(in_timestamp_ns)) {
    if (max_framerate_ < kMinFramerate)
      return;

    int64_t frame_interval_ns = rtc::kNumNanosecsPerSec / max_framerate_;
    if (next_frame_timestamp_ns_)
      *next_frame_timestamp_ns_ += frame_interval_ns;
  }
}

}  // namespace webrtc
