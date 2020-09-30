/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/desktop_geometry.h"

#include <algorithm>
#include <cmath>

namespace webrtc {

bool DesktopRect::Contains(const DesktopVector& point) const {
  return point.x() >= left() && point.x() < right() && point.y() >= top() &&
         point.y() < bottom();
}

bool DesktopRect::ContainsRect(const DesktopRect& rect) const {
  return rect.left() >= left() && rect.right() <= right() &&
         rect.top() >= top() && rect.bottom() <= bottom();
}

void DesktopRect::IntersectWith(const DesktopRect& rect) {
  left_ = std::max(left(), rect.left());
  top_ = std::max(top(), rect.top());
  right_ = std::min(right(), rect.right());
  bottom_ = std::min(bottom(), rect.bottom());
  if (is_empty()) {
    left_ = 0;
    top_ = 0;
    right_ = 0;
    bottom_ = 0;
  }
}

void DesktopRect::UnionWith(const DesktopRect& rect) {
  if (is_empty()) {
    *this = rect;
    return;
  }

  if (rect.is_empty()) {
    return;
  }

  left_ = std::min(left(), rect.left());
  top_ = std::min(top(), rect.top());
  right_ = std::max(right(), rect.right());
  bottom_ = std::max(bottom(), rect.bottom());
}

void DesktopRect::Translate(int32_t dx, int32_t dy) {
  left_ += dx;
  top_ += dy;
  right_ += dx;
  bottom_ += dy;
}

void DesktopRect::Extend(int32_t left_offset,
                         int32_t top_offset,
                         int32_t right_offset,
                         int32_t bottom_offset) {
  left_ -= left_offset;
  top_ -= top_offset;
  right_ += right_offset;
  bottom_ += bottom_offset;
}

void DesktopRect::Scale(double horizontal, double vertical) {
  right_ += static_cast<int>(std::round(width() * (horizontal - 1)));
  bottom_ += static_cast<int>(std::round(height() * (vertical - 1)));
}

}  // namespace webrtc
