/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/linux/screen_capturer_pipewire.h"

#include <memory>


namespace webrtc {

ScreenCapturerPipeWire::ScreenCapturerPipeWire()
    : BaseCapturerPipeWire(BaseCapturerPipeWire::CaptureSourceType::Screen) {}
ScreenCapturerPipeWire::~ScreenCapturerPipeWire() {}

// static
std::unique_ptr<DesktopCapturer>
ScreenCapturerPipeWire::CreateRawScreenCapturer(
    const DesktopCaptureOptions& options) {
  return std::make_unique<ScreenCapturerPipeWire>();
}

}  // namespace webrtc
