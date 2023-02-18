/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/codecs/vp8/vp8_scalability.h"

namespace webrtc {

bool VP8SupportsScalabilityMode(ScalabilityMode scalability_mode) {
  for (const auto& entry : kVP8SupportedScalabilityModes) {
    if (entry == scalability_mode) {
      return true;
    }
  }
  return false;
}

}  // namespace webrtc
