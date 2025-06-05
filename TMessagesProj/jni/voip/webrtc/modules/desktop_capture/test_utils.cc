/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/test_utils.h"

#include <stdint.h>
#include <string.h>

#include "modules/desktop_capture/desktop_geometry.h"
#include "rtc_base/checks.h"

namespace webrtc {

void ClearDesktopFrame(DesktopFrame* frame) {
  RTC_DCHECK(frame);
  uint8_t* data = frame->data();
  for (int i = 0; i < frame->size().height(); i++) {
    memset(data, 0, frame->size().width() * DesktopFrame::kBytesPerPixel);
    data += frame->stride();
  }
}

bool DesktopFrameDataEquals(const DesktopFrame& left,
                            const DesktopFrame& right) {
  if (!left.size().equals(right.size())) {
    return false;
  }

  const uint8_t* left_array = left.data();
  const uint8_t* right_array = right.data();
  for (int i = 0; i < left.size().height(); i++) {
    if (memcmp(left_array, right_array,
               DesktopFrame::kBytesPerPixel * left.size().width()) != 0) {
      return false;
    }
    left_array += left.stride();
    right_array += right.stride();
  }

  return true;
}

}  // namespace webrtc
