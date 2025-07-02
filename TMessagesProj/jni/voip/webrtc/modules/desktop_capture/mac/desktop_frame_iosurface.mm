/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/mac/desktop_frame_iosurface.h"

#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

// static
std::unique_ptr<DesktopFrameIOSurface> DesktopFrameIOSurface::Wrap(
    rtc::ScopedCFTypeRef<IOSurfaceRef> io_surface) {
  if (!io_surface) {
    return nullptr;
  }

  IOSurfaceIncrementUseCount(io_surface.get());
  IOReturn status = IOSurfaceLock(io_surface.get(), kIOSurfaceLockReadOnly, nullptr);
  if (status != kIOReturnSuccess) {
    RTC_LOG(LS_ERROR) << "Failed to lock the IOSurface with status " << status;
    IOSurfaceDecrementUseCount(io_surface.get());
    return nullptr;
  }

  // Verify that the image has 32-bit depth.
  int bytes_per_pixel = IOSurfaceGetBytesPerElement(io_surface.get());
  if (bytes_per_pixel != DesktopFrame::kBytesPerPixel) {
    RTC_LOG(LS_ERROR) << "CGDisplayStream handler returned IOSurface with " << (8 * bytes_per_pixel)
                      << " bits per pixel. Only 32-bit depth is supported.";
    IOSurfaceUnlock(io_surface.get(), kIOSurfaceLockReadOnly, nullptr);
    IOSurfaceDecrementUseCount(io_surface.get());
    return nullptr;
  }

  return std::unique_ptr<DesktopFrameIOSurface>(new DesktopFrameIOSurface(io_surface));
}

DesktopFrameIOSurface::DesktopFrameIOSurface(rtc::ScopedCFTypeRef<IOSurfaceRef> io_surface)
    : DesktopFrame(
          DesktopSize(IOSurfaceGetWidth(io_surface.get()), IOSurfaceGetHeight(io_surface.get())),
          IOSurfaceGetBytesPerRow(io_surface.get()),
          static_cast<uint8_t*>(IOSurfaceGetBaseAddress(io_surface.get())),
          nullptr),
      io_surface_(io_surface) {
  RTC_DCHECK(io_surface_);
}

DesktopFrameIOSurface::~DesktopFrameIOSurface() {
  IOSurfaceUnlock(io_surface_.get(), kIOSurfaceLockReadOnly, nullptr);
  IOSurfaceDecrementUseCount(io_surface_.get());
}

}  // namespace webrtc
