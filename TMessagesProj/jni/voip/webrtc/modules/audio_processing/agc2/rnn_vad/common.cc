/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/rnn_vad/common.h"

#include "rtc_base/system/arch.h"
#include "system_wrappers/include/cpu_features_wrapper.h"

namespace webrtc {
namespace rnn_vad {

Optimization DetectOptimization() {
#if defined(WEBRTC_ARCH_X86_FAMILY)
  if (WebRtc_GetCPUInfo(kSSE2) != 0) {
    return Optimization::kSse2;
  }
#endif

#if defined(WEBRTC_HAS_NEON)
  return Optimization::kNeon;
#endif

  return Optimization::kNone;
}

}  // namespace rnn_vad
}  // namespace webrtc
