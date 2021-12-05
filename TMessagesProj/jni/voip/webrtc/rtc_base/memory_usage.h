/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef RTC_BASE_MEMORY_USAGE_H_
#define RTC_BASE_MEMORY_USAGE_H_

#include <stdint.h>

namespace rtc {

// Returns current memory used by the process in bytes (working set size on
// Windows and resident set size on other platforms).
// Returns -1 on failure.
int64_t GetProcessResidentSizeBytes();

}  // namespace rtc

#endif  // RTC_BASE_MEMORY_USAGE_H_
