/*
 *  Copyright (c) 2023 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/mac/desktop_frame_utils.h"

#include <memory>

namespace webrtc {

std::unique_ptr<DesktopFrame> CreateDesktopFrameFromCGImage(
    rtc::ScopedCFTypeRef<CGImageRef> cg_image) {
  return DesktopFrameCGImage::CreateFromCGImage(cg_image);
}

}  // namespace webrtc
