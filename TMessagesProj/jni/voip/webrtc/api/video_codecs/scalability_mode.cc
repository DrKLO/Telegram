/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video_codecs/scalability_mode.h"

#include "rtc_base/checks.h"

namespace webrtc {

absl::string_view ScalabilityModeToString(ScalabilityMode scalability_mode) {
  switch (scalability_mode) {
    case ScalabilityMode::kL1T1:
      return "L1T1";
    case ScalabilityMode::kL1T2:
      return "L1T2";
    case ScalabilityMode::kL1T3:
      return "L1T3";
    case ScalabilityMode::kL2T1:
      return "L2T1";
    case ScalabilityMode::kL2T1h:
      return "L2T1h";
    case ScalabilityMode::kL2T1_KEY:
      return "L2T1_KEY";
    case ScalabilityMode::kL2T2:
      return "L2T2";
    case ScalabilityMode::kL2T2h:
      return "L2T2h";
    case ScalabilityMode::kL2T2_KEY:
      return "L2T2_KEY";
    case ScalabilityMode::kL2T2_KEY_SHIFT:
      return "L2T2_KEY_SHIFT";
    case ScalabilityMode::kL2T3:
      return "L2T3";
    case ScalabilityMode::kL2T3h:
      return "L2T3h";
    case ScalabilityMode::kL2T3_KEY:
      return "L2T3_KEY";
    case ScalabilityMode::kL3T1:
      return "L3T1";
    case ScalabilityMode::kL3T1h:
      return "L3T1h";
    case ScalabilityMode::kL3T1_KEY:
      return "L3T1_KEY";
    case ScalabilityMode::kL3T2:
      return "L3T2";
    case ScalabilityMode::kL3T2h:
      return "L3T2h";
    case ScalabilityMode::kL3T2_KEY:
      return "L3T2_KEY";
    case ScalabilityMode::kL3T3:
      return "L3T3";
    case ScalabilityMode::kL3T3h:
      return "L3T3h";
    case ScalabilityMode::kL3T3_KEY:
      return "L3T3_KEY";
    case ScalabilityMode::kS2T1:
      return "S2T1";
    case ScalabilityMode::kS2T1h:
      return "S2T1h";
    case ScalabilityMode::kS2T2:
      return "S2T2";
    case ScalabilityMode::kS2T2h:
      return "S2T2h";
    case ScalabilityMode::kS2T3:
      return "S2T3";
    case ScalabilityMode::kS2T3h:
      return "S2T3h";
    case ScalabilityMode::kS3T1:
      return "S3T1";
    case ScalabilityMode::kS3T1h:
      return "S3T1h";
    case ScalabilityMode::kS3T2:
      return "S3T2";
    case ScalabilityMode::kS3T2h:
      return "S3T2h";
    case ScalabilityMode::kS3T3:
      return "S3T3";
    case ScalabilityMode::kS3T3h:
      return "S3T3h";
  }
  RTC_CHECK_NOTREACHED();
}

}  // namespace webrtc
