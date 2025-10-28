/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/desktop_capturer.h"
#include "modules/desktop_capture/desktop_frame.h"
#include "rtc_base/checks.h"

namespace webrtc {

namespace {

class WindowCapturerNull : public DesktopCapturer {
 public:
  WindowCapturerNull();
  ~WindowCapturerNull() override;

  WindowCapturerNull(const WindowCapturerNull&) = delete;
  WindowCapturerNull& operator=(const WindowCapturerNull&) = delete;

  // DesktopCapturer interface.
  void Start(Callback* callback) override;
  void CaptureFrame() override;
  bool GetSourceList(SourceList* sources) override;
  bool SelectSource(SourceId id) override;

 private:
  Callback* callback_ = nullptr;
};

WindowCapturerNull::WindowCapturerNull() {}
WindowCapturerNull::~WindowCapturerNull() {}

bool WindowCapturerNull::GetSourceList(SourceList* sources) {
  // Not implemented yet.
  return false;
}

bool WindowCapturerNull::SelectSource(SourceId id) {
  // Not implemented yet.
  return false;
}

void WindowCapturerNull::Start(Callback* callback) {
  RTC_DCHECK(!callback_);
  RTC_DCHECK(callback);

  callback_ = callback;
}

void WindowCapturerNull::CaptureFrame() {
  // Not implemented yet.
  callback_->OnCaptureResult(Result::ERROR_TEMPORARY, nullptr);
}

}  // namespace

// static
std::unique_ptr<DesktopCapturer> DesktopCapturer::CreateRawWindowCapturer(
    const DesktopCaptureOptions& options) {
  return std::unique_ptr<DesktopCapturer>(new WindowCapturerNull());
}

}  // namespace webrtc
