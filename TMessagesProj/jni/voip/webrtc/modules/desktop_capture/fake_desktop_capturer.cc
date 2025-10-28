/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/fake_desktop_capturer.h"

#include <utility>

#include "modules/desktop_capture/desktop_capture_types.h"
#include "modules/desktop_capture/desktop_frame.h"

namespace webrtc {

FakeDesktopCapturer::FakeDesktopCapturer() = default;
FakeDesktopCapturer::~FakeDesktopCapturer() = default;

void FakeDesktopCapturer::set_result(DesktopCapturer::Result result) {
  result_ = result;
}

int FakeDesktopCapturer::num_frames_captured() const {
  return num_frames_captured_;
}

int FakeDesktopCapturer::num_capture_attempts() const {
  return num_capture_attempts_;
}

// Uses the `generator` provided as DesktopFrameGenerator, FakeDesktopCapturer
// does
// not take the ownership of `generator`.
void FakeDesktopCapturer::set_frame_generator(
    DesktopFrameGenerator* generator) {
  generator_ = generator;
}

void FakeDesktopCapturer::Start(DesktopCapturer::Callback* callback) {
  callback_ = callback;
}

void FakeDesktopCapturer::CaptureFrame() {
  num_capture_attempts_++;
  if (generator_) {
    if (result_ != DesktopCapturer::Result::SUCCESS) {
      callback_->OnCaptureResult(result_, nullptr);
      return;
    }

    std::unique_ptr<DesktopFrame> frame(
        generator_->GetNextFrame(shared_memory_factory_.get()));
    if (frame) {
      num_frames_captured_++;
      callback_->OnCaptureResult(result_, std::move(frame));
    } else {
      callback_->OnCaptureResult(DesktopCapturer::Result::ERROR_TEMPORARY,
                                 nullptr);
    }
    return;
  }
  callback_->OnCaptureResult(DesktopCapturer::Result::ERROR_PERMANENT, nullptr);
}

void FakeDesktopCapturer::SetSharedMemoryFactory(
    std::unique_ptr<SharedMemoryFactory> shared_memory_factory) {
  shared_memory_factory_ = std::move(shared_memory_factory);
}

bool FakeDesktopCapturer::GetSourceList(DesktopCapturer::SourceList* sources) {
  sources->push_back({kWindowId, "A-Fake-DesktopCapturer-Window"});
  sources->push_back({kScreenId});
  return true;
}

bool FakeDesktopCapturer::SelectSource(DesktopCapturer::SourceId id) {
  return id == kWindowId || id == kScreenId || id == kFullDesktopScreenId;
}

}  // namespace webrtc
