/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/resolution_tracker.h"

namespace webrtc {

bool ResolutionTracker::SetResolution(DesktopSize size) {
  if (!initialized_) {
    initialized_ = true;
    last_size_ = size;
    return false;
  }

  if (last_size_.equals(size)) {
    return false;
  }

  last_size_ = size;
  return true;
}

void ResolutionTracker::Reset() {
  initialized_ = false;
}

}  // namespace webrtc
