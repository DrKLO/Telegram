/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/full_screen_application_handler.h"
#include "rtc_base/logging.h"

namespace webrtc {

FullScreenApplicationHandler::FullScreenApplicationHandler(
    DesktopCapturer::SourceId sourceId)
    : source_id_(sourceId) {}

DesktopCapturer::SourceId FullScreenApplicationHandler::FindFullScreenWindow(
    const DesktopCapturer::SourceList&,
    int64_t) const {
  return 0;
}

DesktopCapturer::SourceId FullScreenApplicationHandler::GetSourceId() const {
  return source_id_;
}

}  // namespace webrtc
