/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_DESKTOP_CAPTURE_DESKTOP_CAPTURE_TYPES_H_
#define MODULES_DESKTOP_CAPTURE_DESKTOP_CAPTURE_TYPES_H_

#include <stdint.h>

namespace webrtc {

// Type used to identify windows on the desktop. Values are platform-specific:
//   - On Windows: HWND cast to intptr_t.
//   - On Linux (with X11): X11 Window (unsigned long) type cast to intptr_t.
//   - On OSX: integer window number.
typedef intptr_t WindowId;

const WindowId kNullWindowId = 0;

// Type used to identify screens on the desktop. Values are platform-specific:
//   - On Windows: integer display device index.
//   - On OSX: CGDirectDisplayID cast to intptr_t.
//   - On Linux (with X11): TBD.
// On Windows, ScreenId is implementation dependent: sending a ScreenId from one
// implementation to another usually won't work correctly.
typedef intptr_t ScreenId;

// The screen id corresponds to all screen combined together.
const ScreenId kFullDesktopScreenId = -1;

const ScreenId kInvalidScreenId = -2;

// An integer to attach to each DesktopFrame to differentiate the generator of
// the frame.
namespace DesktopCapturerId {
constexpr uint32_t CreateFourCC(char a, char b, char c, char d) {
  return ((static_cast<uint32_t>(a)) | (static_cast<uint32_t>(b) << 8) |
          (static_cast<uint32_t>(c) << 16) | (static_cast<uint32_t>(d) << 24));
}

constexpr uint32_t kUnknown = 0;
constexpr uint32_t kScreenCapturerWinGdi = CreateFourCC('G', 'D', 'I', ' ');
constexpr uint32_t kScreenCapturerWinDirectx = CreateFourCC('D', 'X', 'G', 'I');
}  // namespace DesktopCapturerId

}  // namespace webrtc

#endif  // MODULES_DESKTOP_CAPTURE_DESKTOP_CAPTURE_TYPES_H_
