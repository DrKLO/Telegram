/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/desktop_frame.h"

#include <string.h>

#include <cmath>
#include <memory>
#include <utility>

#include "modules/desktop_capture/desktop_capture_types.h"
#include "modules/desktop_capture/desktop_geometry.h"
#include "rtc_base/checks.h"

namespace webrtc {

DesktopFrame::DesktopFrame(DesktopSize size,
                           int stride,
                           uint8_t* data,
                           SharedMemory* shared_memory)
    : data_(data),
      shared_memory_(shared_memory),
      size_(size),
      stride_(stride),
      capture_time_ms_(0),
      capturer_id_(DesktopCapturerId::kUnknown) {
  RTC_DCHECK(size_.width() >= 0);
  RTC_DCHECK(size_.height() >= 0);
}

DesktopFrame::~DesktopFrame() = default;

void DesktopFrame::CopyPixelsFrom(const uint8_t* src_buffer,
                                  int src_stride,
                                  const DesktopRect& dest_rect) {
  RTC_CHECK(DesktopRect::MakeSize(size()).ContainsRect(dest_rect));

  uint8_t* dest = GetFrameDataAtPos(dest_rect.top_left());
  for (int y = 0; y < dest_rect.height(); ++y) {
    memcpy(dest, src_buffer, DesktopFrame::kBytesPerPixel * dest_rect.width());
    src_buffer += src_stride;
    dest += stride();
  }
}

void DesktopFrame::CopyPixelsFrom(const DesktopFrame& src_frame,
                                  const DesktopVector& src_pos,
                                  const DesktopRect& dest_rect) {
  RTC_CHECK(DesktopRect::MakeSize(src_frame.size())
                .ContainsRect(
                    DesktopRect::MakeOriginSize(src_pos, dest_rect.size())));

  CopyPixelsFrom(src_frame.GetFrameDataAtPos(src_pos), src_frame.stride(),
                 dest_rect);
}

bool DesktopFrame::CopyIntersectingPixelsFrom(const DesktopFrame& src_frame,
                                              double horizontal_scale,
                                              double vertical_scale) {
  const DesktopVector& origin = top_left();
  const DesktopVector& src_frame_origin = src_frame.top_left();

  DesktopVector src_frame_offset = src_frame_origin.subtract(origin);

  // Determine the intersection, first adjusting its origin to account for any
  // DPI scaling.
  DesktopRect intersection_rect = src_frame.rect();
  if (horizontal_scale != 1.0 || vertical_scale != 1.0) {
    DesktopVector origin_adjustment(
        static_cast<int>(
            std::round((horizontal_scale - 1.0) * src_frame_offset.x())),
        static_cast<int>(
            std::round((vertical_scale - 1.0) * src_frame_offset.y())));

    intersection_rect.Translate(origin_adjustment);

    src_frame_offset = src_frame_offset.add(origin_adjustment);
  }

  intersection_rect.IntersectWith(rect());
  if (intersection_rect.is_empty()) {
    return false;
  }

  // Translate the intersection rect to be relative to the outer rect.
  intersection_rect.Translate(-origin.x(), -origin.y());

  // Determine source position for the copy (offsets of outer frame from
  // source origin, if positive).
  int32_t src_pos_x = std::max(0, -src_frame_offset.x());
  int32_t src_pos_y = std::max(0, -src_frame_offset.y());

  CopyPixelsFrom(src_frame, DesktopVector(src_pos_x, src_pos_y),
                 intersection_rect);
  return true;
}

DesktopRect DesktopFrame::rect() const {
  const float scale = scale_factor();
  // Only scale the size.
  return DesktopRect::MakeXYWH(top_left().x(), top_left().y(),
                               size().width() / scale, size().height() / scale);
}

float DesktopFrame::scale_factor() const {
  float scale = 1.0f;

#if defined(WEBRTC_MAC)
  // At least on Windows the logical and physical pixel are the same
  // See http://crbug.com/948362.
  if (!dpi().is_zero() && dpi().x() == dpi().y())
    scale = dpi().x() / kStandardDPI;
#endif

  return scale;
}

uint8_t* DesktopFrame::GetFrameDataAtPos(const DesktopVector& pos) const {
  return data() + stride() * pos.y() + DesktopFrame::kBytesPerPixel * pos.x();
}

void DesktopFrame::CopyFrameInfoFrom(const DesktopFrame& other) {
  set_dpi(other.dpi());
  set_capture_time_ms(other.capture_time_ms());
  set_capturer_id(other.capturer_id());
  *mutable_updated_region() = other.updated_region();
  set_top_left(other.top_left());
  set_icc_profile(other.icc_profile());
}

void DesktopFrame::MoveFrameInfoFrom(DesktopFrame* other) {
  set_dpi(other->dpi());
  set_capture_time_ms(other->capture_time_ms());
  set_capturer_id(other->capturer_id());
  mutable_updated_region()->Swap(other->mutable_updated_region());
  set_top_left(other->top_left());
  set_icc_profile(other->icc_profile());
}

BasicDesktopFrame::BasicDesktopFrame(DesktopSize size)
    : DesktopFrame(size,
                   kBytesPerPixel * size.width(),
                   new uint8_t[kBytesPerPixel * size.width() * size.height()](),
                   nullptr) {}

BasicDesktopFrame::~BasicDesktopFrame() {
  delete[] data_;
}

// static
DesktopFrame* BasicDesktopFrame::CopyOf(const DesktopFrame& frame) {
  DesktopFrame* result = new BasicDesktopFrame(frame.size());
  for (int y = 0; y < frame.size().height(); ++y) {
    memcpy(result->data() + y * result->stride(),
           frame.data() + y * frame.stride(),
           frame.size().width() * kBytesPerPixel);
  }
  result->CopyFrameInfoFrom(frame);
  return result;
}

// static
std::unique_ptr<DesktopFrame> SharedMemoryDesktopFrame::Create(
    DesktopSize size,
    SharedMemoryFactory* shared_memory_factory) {
  RTC_DCHECK(shared_memory_factory);

  size_t buffer_size = size.height() * size.width() * kBytesPerPixel;
  std::unique_ptr<SharedMemory> shared_memory =
      shared_memory_factory->CreateSharedMemory(buffer_size);
  if (!shared_memory)
    return nullptr;

  return std::make_unique<SharedMemoryDesktopFrame>(
      size, size.width() * kBytesPerPixel, std::move(shared_memory));
}

SharedMemoryDesktopFrame::SharedMemoryDesktopFrame(DesktopSize size,
                                                   int stride,
                                                   SharedMemory* shared_memory)
    : DesktopFrame(size,
                   stride,
                   reinterpret_cast<uint8_t*>(shared_memory->data()),
                   shared_memory) {}

SharedMemoryDesktopFrame::SharedMemoryDesktopFrame(
    DesktopSize size,
    int stride,
    std::unique_ptr<SharedMemory> shared_memory)
    : SharedMemoryDesktopFrame(size, stride, shared_memory.release()) {}

SharedMemoryDesktopFrame::~SharedMemoryDesktopFrame() {
  delete shared_memory_;
}

}  // namespace webrtc
