/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_DESKTOP_CAPTURE_LINUX_SCREEN_CAPTURER_PIPEWIRE_H_
#define MODULES_DESKTOP_CAPTURE_LINUX_SCREEN_CAPTURER_PIPEWIRE_H_

#include <memory>

#include "modules/desktop_capture/linux/base_capturer_pipewire.h"

namespace webrtc {

class ScreenCapturerPipeWire : public BaseCapturerPipeWire {
 public:
  ScreenCapturerPipeWire();
  ~ScreenCapturerPipeWire() override;

  static std::unique_ptr<DesktopCapturer> CreateRawScreenCapturer(
      const DesktopCaptureOptions& options);

  RTC_DISALLOW_COPY_AND_ASSIGN(ScreenCapturerPipeWire);
};

}  // namespace webrtc

#endif  // MODULES_DESKTOP_CAPTURE_LINUX_SCREEN_CAPTURER_PIPEWIRE_H_
