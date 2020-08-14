/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/linux/window_capturer_pipewire.h"

#include <memory>


namespace webrtc {

WindowCapturerPipeWire::WindowCapturerPipeWire()
    : BaseCapturerPipeWire(BaseCapturerPipeWire::CaptureSourceType::Window) {}
WindowCapturerPipeWire::~WindowCapturerPipeWire() {}

// static
std::unique_ptr<DesktopCapturer>
WindowCapturerPipeWire::CreateRawWindowCapturer(
    const DesktopCaptureOptions& options) {
  return std::make_unique<WindowCapturerPipeWire>();
}

}  // namespace webrtc
