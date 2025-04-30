/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video/video_content_type.h"

#include "rtc_base/checks.h"

namespace webrtc {
namespace videocontenttypehelpers {

namespace {
static constexpr uint8_t kScreenshareBitsSize = 1;
static constexpr uint8_t kScreenshareBitsMask =
    (1u << kScreenshareBitsSize) - 1;
}  // namespace

bool IsScreenshare(const VideoContentType& content_type) {
  // Ensure no bits apart from the screenshare bit is set.
  // This CHECK is a temporary measure to detect code that introduces
  // values according to old versions.
  RTC_CHECK((static_cast<uint8_t>(content_type) & !kScreenshareBitsMask) == 0);
  return (static_cast<uint8_t>(content_type) & kScreenshareBitsMask) > 0;
}

bool IsValidContentType(uint8_t value) {
  // Only the screenshare bit is allowed.
  // However, due to previous usage of the next 5 bits, we allow
  // the lower 6 bits to be set.
  return value < (1 << 6);
}

const char* ToString(const VideoContentType& content_type) {
  return IsScreenshare(content_type) ? "screen" : "realtime";
}
}  // namespace videocontenttypehelpers
}  // namespace webrtc
