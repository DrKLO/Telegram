/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/echo_path_variability.h"

namespace webrtc {

EchoPathVariability::EchoPathVariability(bool gain_change,
                                         DelayAdjustment delay_change,
                                         bool clock_drift)
    : gain_change(gain_change),
      delay_change(delay_change),
      clock_drift(clock_drift) {}

}  // namespace webrtc
