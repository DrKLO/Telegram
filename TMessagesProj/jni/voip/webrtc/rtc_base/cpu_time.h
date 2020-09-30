/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_CPU_TIME_H_
#define RTC_BASE_CPU_TIME_H_

#include <stdint.h>

namespace rtc {

// Returns total CPU time of a current process in nanoseconds.
// Time base is unknown, therefore use only to calculate deltas.
int64_t GetProcessCpuTimeNanos();

// Returns total CPU time of a current thread in nanoseconds.
// Time base is unknown, therefore use only to calculate deltas.
int64_t GetThreadCpuTimeNanos();

}  // namespace rtc

#endif  // RTC_BASE_CPU_TIME_H_
