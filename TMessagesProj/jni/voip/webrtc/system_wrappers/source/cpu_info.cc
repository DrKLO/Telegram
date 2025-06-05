/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "system_wrappers/include/cpu_info.h"

#if defined(WEBRTC_WIN)
#include <windows.h>
#elif defined(WEBRTC_LINUX)
#include <unistd.h>
#elif defined(WEBRTC_MAC)
#include <sys/sysctl.h>
#elif defined(WEBRTC_FUCHSIA)
#include <zircon/syscalls.h>
#endif

#include "rtc_base/logging.h"

namespace internal {
static int DetectNumberOfCores() {
  int number_of_cores;

#if defined(WEBRTC_WIN)
  SYSTEM_INFO si;
  GetNativeSystemInfo(&si);
  number_of_cores = static_cast<int>(si.dwNumberOfProcessors);
#elif defined(WEBRTC_LINUX) || defined(WEBRTC_ANDROID)
  number_of_cores = static_cast<int>(sysconf(_SC_NPROCESSORS_ONLN));
  if (number_of_cores <= 0) {
    RTC_LOG(LS_ERROR) << "Failed to get number of cores";
    number_of_cores = 1;
  }
#elif defined(WEBRTC_MAC) || defined(WEBRTC_IOS)
  int name[] = {CTL_HW, HW_AVAILCPU};
  size_t size = sizeof(number_of_cores);
  if (0 != sysctl(name, 2, &number_of_cores, &size, NULL, 0)) {
    RTC_LOG(LS_ERROR) << "Failed to get number of cores";
    number_of_cores = 1;
  }
#elif defined(WEBRTC_FUCHSIA)
  number_of_cores = zx_system_get_num_cpus();
#else
  RTC_LOG(LS_ERROR) << "No function to get number of cores";
  number_of_cores = 1;
#endif

  RTC_LOG(LS_INFO) << "Available number of cores: " << number_of_cores;

  RTC_CHECK_GT(number_of_cores, 0);
  return number_of_cores;
}
}  // namespace internal

namespace webrtc {

uint32_t CpuInfo::DetectNumberOfCores() {
  // Statically cache the number of system cores available since if the process
  // is running in a sandbox, we may only be able to read the value once (before
  // the sandbox is initialized) and not thereafter.
  // For more information see crbug.com/176522.
  static const uint32_t logical_cpus =
      static_cast<uint32_t>(internal::DetectNumberOfCores());
  return logical_cpus;
}

}  // namespace webrtc
