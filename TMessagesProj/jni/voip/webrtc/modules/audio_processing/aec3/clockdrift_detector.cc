/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/audio_processing/aec3/clockdrift_detector.h"

namespace webrtc {

ClockdriftDetector::ClockdriftDetector()
    : level_(Level::kNone), stability_counter_(0) {
  delay_history_.fill(0);
}

ClockdriftDetector::~ClockdriftDetector() = default;

void ClockdriftDetector::Update(int delay_estimate) {
  if (delay_estimate == delay_history_[0]) {
    // Reset clockdrift level if delay estimate is stable for 7500 blocks (30
    // seconds).
    if (++stability_counter_ > 7500)
      level_ = Level::kNone;
    return;
  }

  stability_counter_ = 0;
  const int d1 = delay_history_[0] - delay_estimate;
  const int d2 = delay_history_[1] - delay_estimate;
  const int d3 = delay_history_[2] - delay_estimate;

  // Patterns recognized as positive clockdrift:
  // [x-3], x-2, x-1, x.
  // [x-3], x-1, x-2, x.
  const bool probable_drift_up =
      (d1 == -1 && d2 == -2) || (d1 == -2 && d2 == -1);
  const bool drift_up = probable_drift_up && d3 == -3;

  // Patterns recognized as negative clockdrift:
  // [x+3], x+2, x+1, x.
  // [x+3], x+1, x+2, x.
  const bool probable_drift_down = (d1 == 1 && d2 == 2) || (d1 == 2 && d2 == 1);
  const bool drift_down = probable_drift_down && d3 == 3;

  // Set clockdrift level.
  if (drift_up || drift_down) {
    level_ = Level::kVerified;
  } else if ((probable_drift_up || probable_drift_down) &&
             level_ == Level::kNone) {
    level_ = Level::kProbable;
  }

  // Shift delay history one step.
  delay_history_[2] = delay_history_[1];
  delay_history_[1] = delay_history_[0];
  delay_history_[0] = delay_estimate;
}
}  // namespace webrtc
