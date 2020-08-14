/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/experiments/keyframe_interval_settings.h"

#include "api/transport/field_trial_based_config.h"

namespace webrtc {

namespace {

constexpr char kFieldTrialName[] = "WebRTC-KeyframeInterval";

}  // namespace

KeyframeIntervalSettings::KeyframeIntervalSettings(
    const WebRtcKeyValueConfig* const key_value_config)
    : min_keyframe_send_interval_ms_("min_keyframe_send_interval_ms"),
      max_wait_for_keyframe_ms_("max_wait_for_keyframe_ms"),
      max_wait_for_frame_ms_("max_wait_for_frame_ms") {
  ParseFieldTrial({&min_keyframe_send_interval_ms_, &max_wait_for_keyframe_ms_,
                   &max_wait_for_frame_ms_},
                  key_value_config->Lookup(kFieldTrialName));
}

KeyframeIntervalSettings KeyframeIntervalSettings::ParseFromFieldTrials() {
  FieldTrialBasedConfig field_trial_config;
  return KeyframeIntervalSettings(&field_trial_config);
}

absl::optional<int> KeyframeIntervalSettings::MinKeyframeSendIntervalMs()
    const {
  return min_keyframe_send_interval_ms_.GetOptional();
}

absl::optional<int> KeyframeIntervalSettings::MaxWaitForKeyframeMs() const {
  return max_wait_for_keyframe_ms_.GetOptional();
}

absl::optional<int> KeyframeIntervalSettings::MaxWaitForFrameMs() const {
  return max_wait_for_frame_ms_.GetOptional();
}

}  // namespace webrtc
