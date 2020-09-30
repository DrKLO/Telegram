/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_TYPING_DETECTION_H_
#define MODULES_AUDIO_PROCESSING_TYPING_DETECTION_H_

#include "rtc_base/system/rtc_export.h"

namespace webrtc {

class RTC_EXPORT TypingDetection {
 public:
  TypingDetection();
  virtual ~TypingDetection();

  // Run the detection algortihm. Shall be called every 10 ms. Returns true if
  // typing is detected, or false if not, based on the update period as set with
  // SetParameters(). See |report_detection_update_period_| description below.
  bool Process(bool key_pressed, bool vad_activity);

  // Gets the time in seconds since the last detection.
  int TimeSinceLastDetectionInSeconds();

  // Sets the algorithm parameters. A parameter value of 0 leaves it unchanged.
  // See the correspondning member variables below for descriptions.
  void SetParameters(int time_window,
                     int cost_per_typing,
                     int reporting_threshold,
                     int penalty_decay,
                     int type_event_delay,
                     int report_detection_update_period);

 private:
  int time_active_;
  int time_since_last_typing_;
  int penalty_counter_;

  // Counter since last time the detection status reported by Process() was
  // updated. See also |report_detection_update_period_|.
  int counter_since_last_detection_update_;

  // The detection status to report. Updated every
  // |report_detection_update_period_| call to Process().
  bool detection_to_report_;

  // What |detection_to_report_| should be set to next time it is updated.
  bool new_detection_to_report_;

  // Settable threshold values.

  // Number of 10 ms slots accepted to count as a hit.
  int time_window_;

  // Penalty added for a typing + activity coincide.
  int cost_per_typing_;

  // Threshold for |penalty_counter_|.
  int reporting_threshold_;

  // How much we reduce |penalty_counter_| every 10 ms.
  int penalty_decay_;

  // How old typing events we allow.
  int type_event_delay_;

  // Settable update period.

  // Number of 10 ms slots between each update of the detection status returned
  // by Process(). This inertia added to the algorithm is usually desirable and
  // provided so that consumers of the class don't have to implement that
  // themselves if they don't wish.
  // If set to 1, each call to Process() will return the detection status for
  // that 10 ms slot.
  // If set to N (where N > 1), the detection status returned from Process()
  // will remain the same until Process() has been called N times. Then, if none
  // of the last N calls to Process() has detected typing for each respective
  // 10 ms slot, Process() will return false. If at least one of the last N
  // calls has detected typing, Process() will return true. And that returned
  // status will then remain the same until the next N calls have been done.
  int report_detection_update_period_;
};

}  // namespace webrtc

#endif  // #ifndef MODULES_AUDIO_PROCESSING_TYPING_DETECTION_H_
