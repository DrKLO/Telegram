/*
 *  Copyright 2012 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_CRC32_H_
#define RTC_BASE_CRC32_H_

#include <stddef.h>
#include <stdint.h>

#include <string>

#include "absl/strings/string_view.h"

namespace rtc {

// Updates a CRC32 checksum with `len` bytes from `buf`. `initial` holds the
// checksum result from the previous update; for the first call, it should be 0.
uint32_t UpdateCrc32(uint32_t initial, const void* buf, size_t len);

// Computes a CRC32 checksum using `len` bytes from `buf`.
inline uint32_t ComputeCrc32(const void* buf, size_t len) {
  return UpdateCrc32(0, buf, len);
}
inline uint32_t ComputeCrc32(absl::string_view str) {
  return ComputeCrc32(str.data(), str.size());
}

}  // namespace rtc

#endif  // RTC_BASE_CRC32_H_
