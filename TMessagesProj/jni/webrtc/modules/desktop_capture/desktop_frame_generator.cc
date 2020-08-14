/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/desktop_frame_generator.h"

#include <stdint.h>
#include <string.h>

#include <memory>

#include "modules/desktop_capture/rgba_color.h"
#include "rtc_base/checks.h"
#include "rtc_base/random.h"
#include "rtc_base/time_utils.h"

namespace webrtc {

namespace {

// Sets |updated_region| to |frame|. If |enlarge_updated_region| is
// true, this function will randomly enlarge each DesktopRect in
// |updated_region|. But the enlarged DesktopRegion won't excceed the
// frame->size(). If |add_random_updated_region| is true, several random
// rectangles will also be included in |frame|.
void SetUpdatedRegion(DesktopFrame* frame,
                      const DesktopRegion& updated_region,
                      bool enlarge_updated_region,
                      int enlarge_range,
                      bool add_random_updated_region) {
  const DesktopRect screen_rect = DesktopRect::MakeSize(frame->size());
  Random random(rtc::TimeMicros());
  frame->mutable_updated_region()->Clear();
  for (DesktopRegion::Iterator it(updated_region); !it.IsAtEnd();
       it.Advance()) {
    DesktopRect rect = it.rect();
    if (enlarge_updated_region && enlarge_range > 0) {
      rect.Extend(random.Rand(enlarge_range), random.Rand(enlarge_range),
                  random.Rand(enlarge_range), random.Rand(enlarge_range));
      rect.IntersectWith(screen_rect);
    }
    frame->mutable_updated_region()->AddRect(rect);
  }

  if (add_random_updated_region) {
    for (int i = random.Rand(10); i >= 0; i--) {
      // At least a 1 x 1 updated region.
      const int left = random.Rand(0, frame->size().width() - 2);
      const int top = random.Rand(0, frame->size().height() - 2);
      const int right = random.Rand(left + 1, frame->size().width());
      const int bottom = random.Rand(top + 1, frame->size().height());
      frame->mutable_updated_region()->AddRect(
          DesktopRect::MakeLTRB(left, top, right, bottom));
    }
  }
}

// Paints pixels in |rect| of |frame| to |color|.
void PaintRect(DesktopFrame* frame, DesktopRect rect, RgbaColor rgba_color) {
  static_assert(DesktopFrame::kBytesPerPixel == sizeof(uint32_t),
                "kBytesPerPixel should be 4.");
  RTC_DCHECK_GE(frame->size().width(), rect.right());
  RTC_DCHECK_GE(frame->size().height(), rect.bottom());
  uint32_t color = rgba_color.ToUInt32();
  uint8_t* row = frame->GetFrameDataAtPos(rect.top_left());
  for (int i = 0; i < rect.height(); i++) {
    uint32_t* column = reinterpret_cast<uint32_t*>(row);
    for (int j = 0; j < rect.width(); j++) {
      column[j] = color;
    }
    row += frame->stride();
  }
}

// Paints pixels in |region| of |frame| to |color|.
void PaintRegion(DesktopFrame* frame,
                 DesktopRegion* region,
                 RgbaColor rgba_color) {
  region->IntersectWith(DesktopRect::MakeSize(frame->size()));
  for (DesktopRegion::Iterator it(*region); !it.IsAtEnd(); it.Advance()) {
    PaintRect(frame, it.rect(), rgba_color);
  }
}

}  // namespace

DesktopFrameGenerator::DesktopFrameGenerator() {}
DesktopFrameGenerator::~DesktopFrameGenerator() {}

DesktopFramePainter::DesktopFramePainter() {}
DesktopFramePainter::~DesktopFramePainter() {}

PainterDesktopFrameGenerator::PainterDesktopFrameGenerator()
    : size_(1024, 768),
      return_frame_(true),
      provide_updated_region_hints_(false),
      enlarge_updated_region_(false),
      enlarge_range_(20),
      add_random_updated_region_(false),
      painter_(nullptr) {}
PainterDesktopFrameGenerator::~PainterDesktopFrameGenerator() {}

std::unique_ptr<DesktopFrame> PainterDesktopFrameGenerator::GetNextFrame(
    SharedMemoryFactory* factory) {
  if (!return_frame_) {
    return nullptr;
  }

  std::unique_ptr<DesktopFrame> frame = std::unique_ptr<DesktopFrame>(
      factory ? SharedMemoryDesktopFrame::Create(size_, factory).release()
              : new BasicDesktopFrame(size_));
  if (painter_) {
    DesktopRegion updated_region;
    if (!painter_->Paint(frame.get(), &updated_region)) {
      return nullptr;
    }

    if (provide_updated_region_hints_) {
      SetUpdatedRegion(frame.get(), updated_region, enlarge_updated_region_,
                       enlarge_range_, add_random_updated_region_);
    } else {
      frame->mutable_updated_region()->SetRect(
          DesktopRect::MakeSize(frame->size()));
    }
  }

  return frame;
}

DesktopSize* PainterDesktopFrameGenerator::size() {
  return &size_;
}

void PainterDesktopFrameGenerator::set_return_frame(bool return_frame) {
  return_frame_ = return_frame;
}

void PainterDesktopFrameGenerator::set_provide_updated_region_hints(
    bool provide_updated_region_hints) {
  provide_updated_region_hints_ = provide_updated_region_hints;
}

void PainterDesktopFrameGenerator::set_enlarge_updated_region(
    bool enlarge_updated_region) {
  enlarge_updated_region_ = enlarge_updated_region;
}

void PainterDesktopFrameGenerator::set_enlarge_range(int enlarge_range) {
  enlarge_range_ = enlarge_range;
}

void PainterDesktopFrameGenerator::set_add_random_updated_region(
    bool add_random_updated_region) {
  add_random_updated_region_ = add_random_updated_region;
}

void PainterDesktopFrameGenerator::set_desktop_frame_painter(
    DesktopFramePainter* painter) {
  painter_ = painter;
}

BlackWhiteDesktopFramePainter::BlackWhiteDesktopFramePainter() {}
BlackWhiteDesktopFramePainter::~BlackWhiteDesktopFramePainter() {}

DesktopRegion* BlackWhiteDesktopFramePainter::updated_region() {
  return &updated_region_;
}

bool BlackWhiteDesktopFramePainter::Paint(DesktopFrame* frame,
                                          DesktopRegion* updated_region) {
  RTC_DCHECK(updated_region->is_empty());
  memset(frame->data(), 0, frame->stride() * frame->size().height());
  PaintRegion(frame, &updated_region_, RgbaColor(0xFFFFFFFF));
  updated_region_.Swap(updated_region);
  return true;
}

}  // namespace webrtc
