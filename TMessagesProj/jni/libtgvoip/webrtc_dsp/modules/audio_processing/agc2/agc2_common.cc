/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/agc2_common.h"

#include <stdio.h>
#include <string>

#include "system_wrappers/include/field_trial.h"

namespace webrtc {

float GetInitialSaturationMarginDb() {
  constexpr char kForceInitialSaturationMarginFieldTrial[] =
      "WebRTC-Audio-Agc2ForceInitialSaturationMargin";

  const bool use_forced_initial_saturation_margin =
      webrtc::field_trial::IsEnabled(kForceInitialSaturationMarginFieldTrial);
  if (use_forced_initial_saturation_margin) {
    const std::string field_trial_string = webrtc::field_trial::FindFullName(
        kForceInitialSaturationMarginFieldTrial);
    float margin_db = -1;
    if (sscanf(field_trial_string.c_str(), "Enabled-%f", &margin_db) == 1 &&
        margin_db >= 12.f && margin_db <= 25.f) {
      return margin_db;
    }
  }
  constexpr float kDefaultInitialSaturationMarginDb = 20.f;
  return kDefaultInitialSaturationMarginDb;
}

float GetExtraSaturationMarginOffsetDb() {
  constexpr char kForceExtraSaturationMarginFieldTrial[] =
      "WebRTC-Audio-Agc2ForceExtraSaturationMargin";

  const bool use_forced_extra_saturation_margin =
      webrtc::field_trial::IsEnabled(kForceExtraSaturationMarginFieldTrial);
  if (use_forced_extra_saturation_margin) {
    const std::string field_trial_string = webrtc::field_trial::FindFullName(
        kForceExtraSaturationMarginFieldTrial);
    float margin_db = -1;
    if (sscanf(field_trial_string.c_str(), "Enabled-%f", &margin_db) == 1 &&
        margin_db >= 0.f && margin_db <= 10.f) {
      return margin_db;
    }
  }
  constexpr float kDefaultExtraSaturationMarginDb = 2.f;
  return kDefaultExtraSaturationMarginDb;
}
};  // namespace webrtc
