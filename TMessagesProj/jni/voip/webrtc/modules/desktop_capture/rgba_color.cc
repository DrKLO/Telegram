/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/rgba_color.h"

#include "rtc_base/system/arch.h"

namespace webrtc {

namespace {

bool AlphaEquals(uint8_t i, uint8_t j) {
  // On Linux and Windows 8 or early version, '0' was returned for alpha channel
  // from capturer APIs, on Windows 10, '255' was returned. So a workaround is
  // to treat 0 as 255.
  return i == j || ((i == 0 || i == 255) && (j == 0 || j == 255));
}

}  // namespace

RgbaColor::RgbaColor(uint8_t blue, uint8_t green, uint8_t red, uint8_t alpha) {
  this->blue = blue;
  this->green = green;
  this->red = red;
  this->alpha = alpha;
}

RgbaColor::RgbaColor(uint8_t blue, uint8_t green, uint8_t red)
    : RgbaColor(blue, green, red, 0xff) {}

RgbaColor::RgbaColor(const uint8_t* bgra)
    : RgbaColor(bgra[0], bgra[1], bgra[2], bgra[3]) {}

RgbaColor::RgbaColor(uint32_t bgra)
    : RgbaColor(reinterpret_cast<uint8_t*>(&bgra)) {}

bool RgbaColor::operator==(const RgbaColor& right) const {
  return blue == right.blue && green == right.green && red == right.red &&
         AlphaEquals(alpha, right.alpha);
}

bool RgbaColor::operator!=(const RgbaColor& right) const {
  return !(*this == right);
}

uint32_t RgbaColor::ToUInt32() const {
#if defined(WEBRTC_ARCH_LITTLE_ENDIAN)
  return blue | (green << 8) | (red << 16) | (alpha << 24);
#else
  return (blue << 24) | (green << 16) | (red << 8) | alpha;
#endif
}

}  // namespace webrtc
