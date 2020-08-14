/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_EXPERIMENTS_KEYFRAME_INTERVAL_SETTINGS_H_
#define RTC_BASE_EXPERIMENTS_KEYFRAME_INTERVAL_SETTINGS_H_

#include "absl/types/optional.h"
#include "api/transport/webrtc_key_value_config.h"
#include "rtc_base/experiments/field_trial_parser.h"

namespace webrtc {

class KeyframeIntervalSettings final {
 public:
  static KeyframeIntervalSettings ParseFromFieldTrials();

  // Sender side.
  // The encoded keyframe send rate is <= 1/MinKeyframeSendIntervalMs().
  absl::optional<int> MinKeyframeSendIntervalMs() const;

  // Receiver side.
  // The keyframe request send rate is
  //   - when we have not received a key frame at all:
  //       <= 1/MaxWaitForKeyframeMs()
  //   - when we have not received a frame recently:
  //       <= 1/MaxWaitForFrameMs()
  absl::optional<int> MaxWaitForKeyframeMs() const;
  absl::optional<int> MaxWaitForFrameMs() const;

 private:
  explicit KeyframeIntervalSettings(
      const WebRtcKeyValueConfig* key_value_config);

  FieldTrialOptional<int> min_keyframe_send_interval_ms_;
  FieldTrialOptional<int> max_wait_for_keyframe_ms_;
  FieldTrialOptional<int> max_wait_for_frame_ms_;
};

}  // namespace webrtc

#endif  // RTC_BASE_EXPERIMENTS_KEYFRAME_INTERVAL_SETTINGS_H_
