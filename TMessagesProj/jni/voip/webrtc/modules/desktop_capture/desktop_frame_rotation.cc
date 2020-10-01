/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/desktop_frame_rotation.h"

#include "rtc_base/checks.h"
#include "third_party/libyuv/include/libyuv/rotate_argb.h"

namespace webrtc {

namespace {

libyuv::RotationMode ToLibyuvRotationMode(Rotation rotation) {
  switch (rotation) {
    case Rotation::CLOCK_WISE_0:
      return libyuv::kRotate0;
    case Rotation::CLOCK_WISE_90:
      return libyuv::kRotate90;
    case Rotation::CLOCK_WISE_180:
      return libyuv::kRotate180;
    case Rotation::CLOCK_WISE_270:
      return libyuv::kRotate270;
  }
  RTC_NOTREACHED();
  return libyuv::kRotate0;
}

DesktopRect RotateAndOffsetRect(DesktopRect rect,
                                DesktopSize size,
                                Rotation rotation,
                                DesktopVector offset) {
  DesktopRect result = RotateRect(rect, size, rotation);
  result.Translate(offset);
  return result;
}

}  // namespace

Rotation ReverseRotation(Rotation rotation) {
  switch (rotation) {
    case Rotation::CLOCK_WISE_0:
      return rotation;
    case Rotation::CLOCK_WISE_90:
      return Rotation::CLOCK_WISE_270;
    case Rotation::CLOCK_WISE_180:
      return Rotation::CLOCK_WISE_180;
    case Rotation::CLOCK_WISE_270:
      return Rotation::CLOCK_WISE_90;
  }
  RTC_NOTREACHED();
  return Rotation::CLOCK_WISE_0;
}

DesktopSize RotateSize(DesktopSize size, Rotation rotation) {
  switch (rotation) {
    case Rotation::CLOCK_WISE_0:
    case Rotation::CLOCK_WISE_180:
      return size;
    case Rotation::CLOCK_WISE_90:
    case Rotation::CLOCK_WISE_270:
      return DesktopSize(size.height(), size.width());
  }
  RTC_NOTREACHED();
  return DesktopSize();
}

DesktopRect RotateRect(DesktopRect rect, DesktopSize size, Rotation rotation) {
  switch (rotation) {
    case Rotation::CLOCK_WISE_0:
      return rect;
    case Rotation::CLOCK_WISE_90:
      return DesktopRect::MakeXYWH(size.height() - rect.bottom(), rect.left(),
                                   rect.height(), rect.width());
    case Rotation::CLOCK_WISE_180:
      return DesktopRect::MakeXYWH(size.width() - rect.right(),
                                   size.height() - rect.bottom(), rect.width(),
                                   rect.height());
    case Rotation::CLOCK_WISE_270:
      return DesktopRect::MakeXYWH(rect.top(), size.width() - rect.right(),
                                   rect.height(), rect.width());
  }
  RTC_NOTREACHED();
  return DesktopRect();
}

void RotateDesktopFrame(const DesktopFrame& source,
                        const DesktopRect& source_rect,
                        const Rotation& rotation,
                        const DesktopVector& target_offset,
                        DesktopFrame* target) {
  RTC_DCHECK(target);
  RTC_DCHECK(DesktopRect::MakeSize(source.size()).ContainsRect(source_rect));
  // The rectangle in |target|.
  const DesktopRect target_rect =
      RotateAndOffsetRect(source_rect, source.size(), rotation, target_offset);
  RTC_DCHECK(DesktopRect::MakeSize(target->size()).ContainsRect(target_rect));

  if (target_rect.is_empty()) {
    return;
  }

  int result = libyuv::ARGBRotate(
      source.GetFrameDataAtPos(source_rect.top_left()), source.stride(),
      target->GetFrameDataAtPos(target_rect.top_left()), target->stride(),
      source_rect.width(), source_rect.height(),
      ToLibyuvRotationMode(rotation));
  RTC_DCHECK_EQ(result, 0);
}

}  // namespace webrtc
