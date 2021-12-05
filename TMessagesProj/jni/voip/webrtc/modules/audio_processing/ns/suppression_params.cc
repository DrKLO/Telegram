/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/ns/suppression_params.h"

#include "rtc_base/checks.h"

namespace webrtc {

SuppressionParams::SuppressionParams(
    NsConfig::SuppressionLevel suppression_level) {
  switch (suppression_level) {
    case NsConfig::SuppressionLevel::k6dB:
      over_subtraction_factor = 1.f;
      // 6 dB attenuation.
      minimum_attenuating_gain = 0.5f;
      use_attenuation_adjustment = false;
      break;
    case NsConfig::SuppressionLevel::k12dB:
      over_subtraction_factor = 1.f;
      // 12 dB attenuation.
      minimum_attenuating_gain = 0.25f;
      use_attenuation_adjustment = true;
      break;
    case NsConfig::SuppressionLevel::k18dB:
      over_subtraction_factor = 1.1f;
      // 18 dB attenuation.
      minimum_attenuating_gain = 0.125f;
      use_attenuation_adjustment = true;
      break;
    case NsConfig::SuppressionLevel::k21dB:
      over_subtraction_factor = 1.25f;
      // 20.9 dB attenuation.
      minimum_attenuating_gain = 0.09f;
      use_attenuation_adjustment = true;
      break;
    default:
      RTC_NOTREACHED();
  }
}

}  // namespace webrtc
