/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/typing_detection.h"

namespace webrtc {

TypingDetection::TypingDetection()
    : time_active_(0),
      time_since_last_typing_(0),
      penalty_counter_(0),
      counter_since_last_detection_update_(0),
      detection_to_report_(false),
      new_detection_to_report_(false),
      time_window_(10),
      cost_per_typing_(100),
      reporting_threshold_(300),
      penalty_decay_(1),
      type_event_delay_(2),
      report_detection_update_period_(1) {}

TypingDetection::~TypingDetection() {}

bool TypingDetection::Process(bool key_pressed, bool vad_activity) {
  if (vad_activity)
    time_active_++;
  else
    time_active_ = 0;

  // Keep track if time since last typing event
  if (key_pressed)
    time_since_last_typing_ = 0;
  else
    ++time_since_last_typing_;

  if (time_since_last_typing_ < type_event_delay_ && vad_activity &&
      time_active_ < time_window_) {
    penalty_counter_ += cost_per_typing_;
    if (penalty_counter_ > reporting_threshold_)
      new_detection_to_report_ = true;
  }

  if (penalty_counter_ > 0)
    penalty_counter_ -= penalty_decay_;

  if (++counter_since_last_detection_update_ ==
      report_detection_update_period_) {
    detection_to_report_ = new_detection_to_report_;
    new_detection_to_report_ = false;
    counter_since_last_detection_update_ = 0;
  }

  return detection_to_report_;
}

int TypingDetection::TimeSinceLastDetectionInSeconds() {
  // Round to whole seconds.
  return (time_since_last_typing_ + 50) / 100;
}

void TypingDetection::SetParameters(int time_window,
                                    int cost_per_typing,
                                    int reporting_threshold,
                                    int penalty_decay,
                                    int type_event_delay,
                                    int report_detection_update_period) {
  if (time_window)
    time_window_ = time_window;

  if (cost_per_typing)
    cost_per_typing_ = cost_per_typing;

  if (reporting_threshold)
    reporting_threshold_ = reporting_threshold;

  if (penalty_decay)
    penalty_decay_ = penalty_decay;

  if (type_event_delay)
    type_event_delay_ = type_event_delay;

  if (report_detection_update_period)
    report_detection_update_period_ = report_detection_update_period;
}

}  // namespace webrtc
