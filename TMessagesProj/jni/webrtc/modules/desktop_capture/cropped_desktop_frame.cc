/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/cropped_desktop_frame.h"

#include <memory>
#include <utility>

#include "modules/desktop_capture/desktop_region.h"
#include "rtc_base/checks.h"
#include "rtc_base/constructor_magic.h"

namespace webrtc {

// A DesktopFrame that is a sub-rect of another DesktopFrame.
class CroppedDesktopFrame : public DesktopFrame {
 public:
  CroppedDesktopFrame(std::unique_ptr<DesktopFrame> frame,
                      const DesktopRect& rect);

 private:
  const std::unique_ptr<DesktopFrame> frame_;

  RTC_DISALLOW_COPY_AND_ASSIGN(CroppedDesktopFrame);
};

std::unique_ptr<DesktopFrame> CreateCroppedDesktopFrame(
    std::unique_ptr<DesktopFrame> frame,
    const DesktopRect& rect) {
  RTC_DCHECK(frame);

  if (!DesktopRect::MakeSize(frame->size()).ContainsRect(rect)) {
    return nullptr;
  }

  if (frame->size().equals(rect.size())) {
    return frame;
  }

  return std::unique_ptr<DesktopFrame>(
      new CroppedDesktopFrame(std::move(frame), rect));
}

CroppedDesktopFrame::CroppedDesktopFrame(std::unique_ptr<DesktopFrame> frame,
                                         const DesktopRect& rect)
    : DesktopFrame(rect.size(),
                   frame->stride(),
                   frame->GetFrameDataAtPos(rect.top_left()),
                   frame->shared_memory()),
      frame_(std::move(frame)) {
  MoveFrameInfoFrom(frame_.get());
  set_top_left(frame_->top_left().add(rect.top_left()));
  mutable_updated_region()->IntersectWith(rect);
  mutable_updated_region()->Translate(-rect.left(), -rect.top());
}

}  // namespace webrtc
