/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/test/frame_generator_interface.h"

namespace webrtc {
namespace test {

// static
const char* FrameGeneratorInterface::OutputTypeToString(
    FrameGeneratorInterface::OutputType type) {
  switch (type) {
    case OutputType::kI420:
      return "I420";
    case OutputType::kI420A:
      return "I420A";
    case OutputType::kI010:
      return "I010";
    case OutputType::kNV12:
      return "NV12";
    default:
      RTC_DCHECK_NOTREACHED();
  }
}

}  // namespace test
}  // namespace webrtc
