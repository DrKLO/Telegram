/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/desktop_frame_win.h"

#include <utility>

#include "rtc_base/logging.h"

namespace webrtc {

DesktopFrameWin::DesktopFrameWin(DesktopSize size,
                                 int stride,
                                 uint8_t* data,
                                 std::unique_ptr<SharedMemory> shared_memory,
                                 HBITMAP bitmap)
    : DesktopFrame(size, stride, data, shared_memory.get()),
      bitmap_(bitmap),
      owned_shared_memory_(std::move(shared_memory)) {}

DesktopFrameWin::~DesktopFrameWin() {
  DeleteObject(bitmap_);
}

// static
std::unique_ptr<DesktopFrameWin> DesktopFrameWin::Create(
    DesktopSize size,
    SharedMemoryFactory* shared_memory_factory,
    HDC hdc) {
  int bytes_per_row = size.width() * kBytesPerPixel;
  int buffer_size = bytes_per_row * size.height();

  // Describe a device independent bitmap (DIB) that is the size of the desktop.
  BITMAPINFO bmi = {};
  bmi.bmiHeader.biHeight = -size.height();
  bmi.bmiHeader.biWidth = size.width();
  bmi.bmiHeader.biPlanes = 1;
  bmi.bmiHeader.biBitCount = DesktopFrameWin::kBytesPerPixel * 8;
  bmi.bmiHeader.biSize = sizeof(bmi.bmiHeader);
  bmi.bmiHeader.biSizeImage = bytes_per_row * size.height();

  std::unique_ptr<SharedMemory> shared_memory;
  HANDLE section_handle = nullptr;
  if (shared_memory_factory) {
    shared_memory = shared_memory_factory->CreateSharedMemory(buffer_size);
    section_handle = shared_memory->handle();
  }
  void* data = nullptr;
  HBITMAP bitmap =
      CreateDIBSection(hdc, &bmi, DIB_RGB_COLORS, &data, section_handle, 0);
  if (!bitmap) {
    RTC_LOG(LS_WARNING) << "Failed to allocate new window frame "
                        << GetLastError();
    return nullptr;
  }

  return std::unique_ptr<DesktopFrameWin>(
      new DesktopFrameWin(size, bytes_per_row, reinterpret_cast<uint8_t*>(data),
                          std::move(shared_memory), bitmap));
}

}  // namespace webrtc
