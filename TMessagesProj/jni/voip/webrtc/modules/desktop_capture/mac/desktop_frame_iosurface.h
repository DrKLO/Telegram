/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_DESKTOP_CAPTURE_MAC_DESKTOP_FRAME_IOSURFACE_H_
#define MODULES_DESKTOP_CAPTURE_MAC_DESKTOP_FRAME_IOSURFACE_H_

#include <CoreGraphics/CoreGraphics.h>
#include <IOSurface/IOSurface.h>

#include <memory>

#include "modules/desktop_capture/desktop_frame.h"
#include "sdk/objc/helpers/scoped_cftyperef.h"

namespace webrtc {

class DesktopFrameIOSurface final : public DesktopFrame {
 public:
  // Lock an IOSurfaceRef containing a snapshot of a display. Return NULL if
  // failed to lock.
  static std::unique_ptr<DesktopFrameIOSurface> Wrap(
      rtc::ScopedCFTypeRef<IOSurfaceRef> io_surface);

  ~DesktopFrameIOSurface() override;

  DesktopFrameIOSurface(const DesktopFrameIOSurface&) = delete;
  DesktopFrameIOSurface& operator=(const DesktopFrameIOSurface&) = delete;

 private:
  // This constructor expects `io_surface` to hold a non-null IOSurfaceRef.
  explicit DesktopFrameIOSurface(rtc::ScopedCFTypeRef<IOSurfaceRef> io_surface);

  const rtc::ScopedCFTypeRef<IOSurfaceRef> io_surface_;
};

}  // namespace webrtc

#endif  // MODULES_DESKTOP_CAPTURE_MAC_DESKTOP_FRAME_IOSURFACE_H_
