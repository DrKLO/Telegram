/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/audio/echo_detector_creator.h"

#include "modules/audio_processing/residual_echo_detector.h"
#include "rtc_base/ref_counted_object.h"

namespace webrtc {

rtc::scoped_refptr<EchoDetector> CreateEchoDetector() {
  return new rtc::RefCountedObject<ResidualEchoDetector>();
}

}  // namespace webrtc
