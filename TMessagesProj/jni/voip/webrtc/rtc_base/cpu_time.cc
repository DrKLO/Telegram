/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/cpu_time.h"

#include "rtc_base/logging.h"
#include "rtc_base/time_utils.h"

#if defined(WEBRTC_LINUX)
#include <time.h>
#elif defined(WEBRTC_MAC)
#include <mach/mach_init.h>
#include <mach/mach_port.h>
#include <mach/thread_act.h>
#include <mach/thread_info.h>
#include <sys/resource.h>
#include <sys/times.h>
#include <sys/types.h>
#include <unistd.h>
#elif defined(WEBRTC_WIN)
#include <windows.h>
#elif defined(WEBRTC_FUCHSIA)
#include <lib/zx/process.h>
#include <lib/zx/thread.h>
#include <zircon/status.h>
#endif

#if defined(WEBRTC_WIN)
namespace {
// FILETIME resolution is 100 nanosecs.
const int64_t kNanosecsPerFiletime = 100;
}  // namespace
#endif

namespace rtc {

int64_t GetProcessCpuTimeNanos() {
#if defined(WEBRTC_FUCHSIA)
  zx_info_task_runtime_t runtime_info;
  zx_status_t status =
      zx::process::self()->get_info(ZX_INFO_TASK_RUNTIME, &runtime_info,
                                    sizeof(runtime_info), nullptr, nullptr);
  if (status == ZX_OK) {
    return runtime_info.cpu_time;
  } else {
    RTC_LOG_ERR(LS_ERROR) << "get_info() failed: "
                          << zx_status_get_string(status);
  }
#elif defined(WEBRTC_LINUX)
  struct timespec ts;
  if (clock_gettime(CLOCK_PROCESS_CPUTIME_ID, &ts) == 0) {
    return ts.tv_sec * kNumNanosecsPerSec + ts.tv_nsec;
  } else {
    RTC_LOG_ERR(LS_ERROR) << "clock_gettime() failed.";
  }
#elif defined(WEBRTC_MAC)
  struct rusage rusage;
  if (getrusage(RUSAGE_SELF, &rusage) == 0) {
    return rusage.ru_utime.tv_sec * kNumNanosecsPerSec +
           rusage.ru_utime.tv_usec * kNumNanosecsPerMicrosec;
  } else {
    RTC_LOG_ERR(LS_ERROR) << "getrusage() failed.";
  }
#elif defined(WEBRTC_WIN)
  FILETIME createTime;
  FILETIME exitTime;
  FILETIME kernelTime;
  FILETIME userTime;
  if (GetProcessTimes(GetCurrentProcess(), &createTime, &exitTime, &kernelTime,
                      &userTime) != 0) {
    return ((static_cast<uint64_t>(userTime.dwHighDateTime) << 32) +
            userTime.dwLowDateTime) *
           kNanosecsPerFiletime;
  } else {
    RTC_LOG_ERR(LS_ERROR) << "GetProcessTimes() failed.";
  }
#else
  // Not implemented yet.
  static_assert(
      false, "GetProcessCpuTimeNanos() platform support not yet implemented.");
#endif
  return -1;
}

int64_t GetThreadCpuTimeNanos() {
#if defined(WEBRTC_FUCHSIA)
  zx_info_task_runtime_t runtime_info;
  zx_status_t status =
      zx::thread::self()->get_info(ZX_INFO_TASK_RUNTIME, &runtime_info,
                                   sizeof(runtime_info), nullptr, nullptr);
  if (status == ZX_OK) {
    return runtime_info.cpu_time;
  } else {
    RTC_LOG_ERR(LS_ERROR) << "get_info() failed: "
                          << zx_status_get_string(status);
  }
#elif defined(WEBRTC_LINUX)
  struct timespec ts;
  if (clock_gettime(CLOCK_THREAD_CPUTIME_ID, &ts) == 0) {
    return ts.tv_sec * kNumNanosecsPerSec + ts.tv_nsec;
  } else {
    RTC_LOG_ERR(LS_ERROR) << "clock_gettime() failed.";
  }
#elif defined(WEBRTC_MAC)
  mach_port_t thread_port = mach_thread_self();
  thread_basic_info_data_t info;
  mach_msg_type_number_t count = THREAD_BASIC_INFO_COUNT;
  kern_return_t kr =
      thread_info(thread_port, THREAD_BASIC_INFO, (thread_info_t)&info, &count);
  mach_port_deallocate(mach_task_self(), thread_port);
  if (kr == KERN_SUCCESS) {
    return info.user_time.seconds * kNumNanosecsPerSec +
           info.user_time.microseconds * kNumNanosecsPerMicrosec;
  } else {
    RTC_LOG_ERR(LS_ERROR) << "thread_info() failed.";
  }
#elif defined(WEBRTC_WIN)
  FILETIME createTime;
  FILETIME exitTime;
  FILETIME kernelTime;
  FILETIME userTime;
  if (GetThreadTimes(GetCurrentThread(), &createTime, &exitTime, &kernelTime,
                     &userTime) != 0) {
    return ((static_cast<uint64_t>(userTime.dwHighDateTime) << 32) +
            userTime.dwLowDateTime) *
           kNanosecsPerFiletime;
  } else {
    RTC_LOG_ERR(LS_ERROR) << "GetThreadTimes() failed.";
  }
#else
  // Not implemented yet.
  static_assert(
      false, "GetThreadCpuTimeNanos() platform support not yet implemented.");
#endif
  return -1;
}

}  // namespace rtc
