/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/shared_desktop_frame.h"

#include <memory>
#include <type_traits>
#include <utility>

namespace webrtc {

SharedDesktopFrame::~SharedDesktopFrame() {}

// static
std::unique_ptr<SharedDesktopFrame> SharedDesktopFrame::Wrap(
    std::unique_ptr<DesktopFrame> desktop_frame) {
  return std::unique_ptr<SharedDesktopFrame>(
      new SharedDesktopFrame(new Core(std::move(desktop_frame))));
}

SharedDesktopFrame* SharedDesktopFrame::Wrap(DesktopFrame* desktop_frame) {
  return Wrap(std::unique_ptr<DesktopFrame>(desktop_frame)).release();
}

DesktopFrame* SharedDesktopFrame::GetUnderlyingFrame() {
  return core_->get();
}

bool SharedDesktopFrame::ShareFrameWith(const SharedDesktopFrame& other) const {
  return core_->get() == other.core_->get();
}

std::unique_ptr<SharedDesktopFrame> SharedDesktopFrame::Share() {
  std::unique_ptr<SharedDesktopFrame> result(new SharedDesktopFrame(core_));
  result->CopyFrameInfoFrom(*this);
  return result;
}

bool SharedDesktopFrame::IsShared() {
  return !core_->HasOneRef();
}

SharedDesktopFrame::SharedDesktopFrame(rtc::scoped_refptr<Core> core)
    : DesktopFrame((*core)->size(),
                   (*core)->stride(),
                   (*core)->data(),
                   (*core)->shared_memory()),
      core_(core) {
  CopyFrameInfoFrom(*(core_->get()));
}

}  // namespace webrtc
