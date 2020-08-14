/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_DESKTOP_CAPTURE_DESKTOP_FRAME_WIN_H_
#define MODULES_DESKTOP_CAPTURE_DESKTOP_FRAME_WIN_H_

#include <windows.h>

#include <memory>

#include "modules/desktop_capture/desktop_frame.h"
#include "rtc_base/constructor_magic.h"

namespace webrtc {

// DesktopFrame implementation used by screen and window captures on Windows.
// Frame data is stored in a GDI bitmap.
class DesktopFrameWin : public DesktopFrame {
 public:
  ~DesktopFrameWin() override;

  static std::unique_ptr<DesktopFrameWin>
  Create(DesktopSize size, SharedMemoryFactory* shared_memory_factory, HDC hdc);

  HBITMAP bitmap() { return bitmap_; }

 private:
  DesktopFrameWin(DesktopSize size,
                  int stride,
                  uint8_t* data,
                  std::unique_ptr<SharedMemory> shared_memory,
                  HBITMAP bitmap);

  HBITMAP bitmap_;
  std::unique_ptr<SharedMemory> owned_shared_memory_;

  RTC_DISALLOW_COPY_AND_ASSIGN(DesktopFrameWin);
};

}  // namespace webrtc

#endif  // MODULES_DESKTOP_CAPTURE_DESKTOP_FRAME_WIN_H_
