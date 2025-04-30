/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/mac/desktop_frame_provider.h"

#include <utility>

#include "modules/desktop_capture/mac/desktop_frame_cgimage.h"
#include "modules/desktop_capture/mac/desktop_frame_iosurface.h"

namespace webrtc {

DesktopFrameProvider::DesktopFrameProvider(bool allow_iosurface)
    : allow_iosurface_(allow_iosurface) {
  thread_checker_.Detach();
}

DesktopFrameProvider::~DesktopFrameProvider() {
  RTC_DCHECK(thread_checker_.IsCurrent());

  Release();
}

std::unique_ptr<DesktopFrame> DesktopFrameProvider::TakeLatestFrameForDisplay(
    CGDirectDisplayID display_id) {
  RTC_DCHECK(thread_checker_.IsCurrent());

  if (!allow_iosurface_ || !io_surfaces_[display_id]) {
    // Regenerate a snapshot. If iosurface is on it will be empty until the
    // stream handler is called.
    return DesktopFrameCGImage::CreateForDisplay(display_id);
  }

  return io_surfaces_[display_id]->Share();
}

void DesktopFrameProvider::InvalidateIOSurface(CGDirectDisplayID display_id,
                                               rtc::ScopedCFTypeRef<IOSurfaceRef> io_surface) {
  RTC_DCHECK(thread_checker_.IsCurrent());

  if (!allow_iosurface_) {
    return;
  }

  std::unique_ptr<DesktopFrameIOSurface> desktop_frame_iosurface =
      DesktopFrameIOSurface::Wrap(io_surface);

  io_surfaces_[display_id] = desktop_frame_iosurface ?
      SharedDesktopFrame::Wrap(std::move(desktop_frame_iosurface)) :
      nullptr;
}

void DesktopFrameProvider::Release() {
  RTC_DCHECK(thread_checker_.IsCurrent());

  if (!allow_iosurface_) {
    return;
  }

  io_surfaces_.clear();
}

}  // namespace webrtc
