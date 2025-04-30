/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/desktop_capturer_wrapper.h"

#include <utility>

#include "rtc_base/checks.h"

namespace webrtc {

DesktopCapturerWrapper::DesktopCapturerWrapper(
    std::unique_ptr<DesktopCapturer> base_capturer)
    : base_capturer_(std::move(base_capturer)) {
  RTC_DCHECK(base_capturer_);
}

DesktopCapturerWrapper::~DesktopCapturerWrapper() = default;

void DesktopCapturerWrapper::Start(Callback* callback) {
  base_capturer_->Start(callback);
}

void DesktopCapturerWrapper::SetSharedMemoryFactory(
    std::unique_ptr<SharedMemoryFactory> shared_memory_factory) {
  base_capturer_->SetSharedMemoryFactory(std::move(shared_memory_factory));
}

void DesktopCapturerWrapper::CaptureFrame() {
  base_capturer_->CaptureFrame();
}

void DesktopCapturerWrapper::SetExcludedWindow(WindowId window) {
  base_capturer_->SetExcludedWindow(window);
}

bool DesktopCapturerWrapper::GetSourceList(SourceList* sources) {
  return base_capturer_->GetSourceList(sources);
}

bool DesktopCapturerWrapper::SelectSource(SourceId id) {
  return base_capturer_->SelectSource(id);
}

bool DesktopCapturerWrapper::FocusOnSelectedSource() {
  return base_capturer_->FocusOnSelectedSource();
}

bool DesktopCapturerWrapper::IsOccluded(const DesktopVector& pos) {
  return base_capturer_->IsOccluded(pos);
}

}  // namespace webrtc
