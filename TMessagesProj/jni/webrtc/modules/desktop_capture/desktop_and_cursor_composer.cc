/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/desktop_and_cursor_composer.h"

#include <stdint.h>
#include <string.h>

#include <memory>
#include <utility>

#include "modules/desktop_capture/desktop_capturer.h"
#include "modules/desktop_capture/desktop_frame.h"
#include "modules/desktop_capture/mouse_cursor.h"
#include "modules/desktop_capture/mouse_cursor_monitor.h"
#include "rtc_base/checks.h"
#include "rtc_base/constructor_magic.h"

namespace webrtc {

namespace {

// Helper function that blends one image into another. Source image must be
// pre-multiplied with the alpha channel. Destination is assumed to be opaque.
void AlphaBlend(uint8_t* dest,
                int dest_stride,
                const uint8_t* src,
                int src_stride,
                const DesktopSize& size) {
  for (int y = 0; y < size.height(); ++y) {
    for (int x = 0; x < size.width(); ++x) {
      uint32_t base_alpha = 255 - src[x * DesktopFrame::kBytesPerPixel + 3];
      if (base_alpha == 255) {
        continue;
      } else if (base_alpha == 0) {
        memcpy(dest + x * DesktopFrame::kBytesPerPixel,
               src + x * DesktopFrame::kBytesPerPixel,
               DesktopFrame::kBytesPerPixel);
      } else {
        dest[x * DesktopFrame::kBytesPerPixel] =
            dest[x * DesktopFrame::kBytesPerPixel] * base_alpha / 255 +
            src[x * DesktopFrame::kBytesPerPixel];
        dest[x * DesktopFrame::kBytesPerPixel + 1] =
            dest[x * DesktopFrame::kBytesPerPixel + 1] * base_alpha / 255 +
            src[x * DesktopFrame::kBytesPerPixel + 1];
        dest[x * DesktopFrame::kBytesPerPixel + 2] =
            dest[x * DesktopFrame::kBytesPerPixel + 2] * base_alpha / 255 +
            src[x * DesktopFrame::kBytesPerPixel + 2];
      }
    }
    src += src_stride;
    dest += dest_stride;
  }
}

// DesktopFrame wrapper that draws mouse on a frame and restores original
// content before releasing the underlying frame.
class DesktopFrameWithCursor : public DesktopFrame {
 public:
  // Takes ownership of |frame|.
  DesktopFrameWithCursor(std::unique_ptr<DesktopFrame> frame,
                         const MouseCursor& cursor,
                         const DesktopVector& position,
                         const DesktopRect& previous_cursor_rect,
                         bool cursor_changed);
  ~DesktopFrameWithCursor() override;

  DesktopRect cursor_rect() const { return cursor_rect_; }

 private:
  const std::unique_ptr<DesktopFrame> original_frame_;

  DesktopVector restore_position_;
  std::unique_ptr<DesktopFrame> restore_frame_;
  DesktopRect cursor_rect_;

  RTC_DISALLOW_COPY_AND_ASSIGN(DesktopFrameWithCursor);
};

DesktopFrameWithCursor::DesktopFrameWithCursor(
    std::unique_ptr<DesktopFrame> frame,
    const MouseCursor& cursor,
    const DesktopVector& position,
    const DesktopRect& previous_cursor_rect,
    bool cursor_changed)
    : DesktopFrame(frame->size(),
                   frame->stride(),
                   frame->data(),
                   frame->shared_memory()),
      original_frame_(std::move(frame)) {
  MoveFrameInfoFrom(original_frame_.get());

  DesktopVector image_pos = position.subtract(cursor.hotspot());
  cursor_rect_ = DesktopRect::MakeSize(cursor.image()->size());
  cursor_rect_.Translate(image_pos);
  DesktopVector cursor_origin = cursor_rect_.top_left();
  cursor_rect_.IntersectWith(DesktopRect::MakeSize(size()));

  if (!previous_cursor_rect.equals(cursor_rect_)) {
    mutable_updated_region()->AddRect(cursor_rect_);
    mutable_updated_region()->AddRect(previous_cursor_rect);
  } else if (cursor_changed) {
    mutable_updated_region()->AddRect(cursor_rect_);
  }

  if (cursor_rect_.is_empty())
    return;

  // Copy original screen content under cursor to |restore_frame_|.
  restore_position_ = cursor_rect_.top_left();
  restore_frame_.reset(new BasicDesktopFrame(cursor_rect_.size()));
  restore_frame_->CopyPixelsFrom(*this, cursor_rect_.top_left(),
                                 DesktopRect::MakeSize(restore_frame_->size()));

  // Blit the cursor.
  uint8_t* cursor_rect_data =
      reinterpret_cast<uint8_t*>(data()) + cursor_rect_.top() * stride() +
      cursor_rect_.left() * DesktopFrame::kBytesPerPixel;
  DesktopVector origin_shift = cursor_rect_.top_left().subtract(cursor_origin);
  AlphaBlend(cursor_rect_data, stride(),
             cursor.image()->data() +
                 origin_shift.y() * cursor.image()->stride() +
                 origin_shift.x() * DesktopFrame::kBytesPerPixel,
             cursor.image()->stride(), cursor_rect_.size());
}

DesktopFrameWithCursor::~DesktopFrameWithCursor() {
  // Restore original content of the frame.
  if (restore_frame_) {
    DesktopRect target_rect = DesktopRect::MakeSize(restore_frame_->size());
    target_rect.Translate(restore_position_);
    CopyPixelsFrom(restore_frame_->data(), restore_frame_->stride(),
                   target_rect);
  }
}

}  // namespace

DesktopAndCursorComposer::DesktopAndCursorComposer(
    std::unique_ptr<DesktopCapturer> desktop_capturer,
    const DesktopCaptureOptions& options)
    : DesktopAndCursorComposer(desktop_capturer.release(),
                               MouseCursorMonitor::Create(options).release()) {}

DesktopAndCursorComposer::DesktopAndCursorComposer(
    DesktopCapturer* desktop_capturer,
    MouseCursorMonitor* mouse_monitor)
    : desktop_capturer_(desktop_capturer), mouse_monitor_(mouse_monitor) {
  RTC_DCHECK(desktop_capturer_);
}

DesktopAndCursorComposer::~DesktopAndCursorComposer() = default;

std::unique_ptr<DesktopAndCursorComposer>
DesktopAndCursorComposer::CreateWithoutMouseCursorMonitor(
    std::unique_ptr<DesktopCapturer> desktop_capturer) {
  return std::unique_ptr<DesktopAndCursorComposer>(
      new DesktopAndCursorComposer(desktop_capturer.release(), nullptr));
}

void DesktopAndCursorComposer::Start(DesktopCapturer::Callback* callback) {
  callback_ = callback;
  if (mouse_monitor_)
    mouse_monitor_->Init(this, MouseCursorMonitor::SHAPE_AND_POSITION);
  desktop_capturer_->Start(this);
}

void DesktopAndCursorComposer::SetSharedMemoryFactory(
    std::unique_ptr<SharedMemoryFactory> shared_memory_factory) {
  desktop_capturer_->SetSharedMemoryFactory(std::move(shared_memory_factory));
}

void DesktopAndCursorComposer::CaptureFrame() {
  if (mouse_monitor_)
    mouse_monitor_->Capture();
  desktop_capturer_->CaptureFrame();
}

void DesktopAndCursorComposer::SetExcludedWindow(WindowId window) {
  desktop_capturer_->SetExcludedWindow(window);
}

bool DesktopAndCursorComposer::GetSourceList(SourceList* sources) {
  return desktop_capturer_->GetSourceList(sources);
}

bool DesktopAndCursorComposer::SelectSource(SourceId id) {
  return desktop_capturer_->SelectSource(id);
}

bool DesktopAndCursorComposer::FocusOnSelectedSource() {
  return desktop_capturer_->FocusOnSelectedSource();
}

bool DesktopAndCursorComposer::IsOccluded(const DesktopVector& pos) {
  return desktop_capturer_->IsOccluded(pos);
}

void DesktopAndCursorComposer::OnCaptureResult(
    DesktopCapturer::Result result,
    std::unique_ptr<DesktopFrame> frame) {
  if (frame && cursor_) {
    if (frame->rect().Contains(cursor_position_) &&
        !desktop_capturer_->IsOccluded(cursor_position_)) {
      DesktopVector relative_position =
          cursor_position_.subtract(frame->top_left());
#if defined(WEBRTC_MAC)
      // On OSX, the logical(DIP) and physical coordinates are used mixingly.
      // For example, the captured cursor has its size in physical pixels(2x)
      // and location in logical(DIP) pixels on Retina monitor. This will cause
      // problem when the desktop is mixed with Retina and non-Retina monitors.
      // So we use DIP pixel for all location info and compensate with the scale
      // factor of current frame to the |relative_position|.
      const float scale = frame->scale_factor();
      relative_position.set(relative_position.x() * scale,
                            relative_position.y() * scale);
#endif
      auto frame_with_cursor = std::make_unique<DesktopFrameWithCursor>(
          std::move(frame), *cursor_, relative_position, previous_cursor_rect_,
          cursor_changed_);
      previous_cursor_rect_ = frame_with_cursor->cursor_rect();
      cursor_changed_ = false;
      frame = std::move(frame_with_cursor);
    }
  }

  callback_->OnCaptureResult(result, std::move(frame));
}

void DesktopAndCursorComposer::OnMouseCursor(MouseCursor* cursor) {
  cursor_changed_ = true;
  cursor_.reset(cursor);
}

void DesktopAndCursorComposer::OnMouseCursorPosition(
    const DesktopVector& position) {
  cursor_position_ = position;
}

}  // namespace webrtc
