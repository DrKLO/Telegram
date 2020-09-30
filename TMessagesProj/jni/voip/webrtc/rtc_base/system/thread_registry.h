/*
 *  Copyright 2019 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_SYSTEM_THREAD_REGISTRY_H_
#define RTC_BASE_SYSTEM_THREAD_REGISTRY_H_

#include "rtc_base/location.h"

namespace webrtc {

class ScopedRegisterThreadForDebugging {
 public:
#if defined(WEBRTC_ANDROID) && !defined(WEBRTC_CHROMIUM_BUILD)
  explicit ScopedRegisterThreadForDebugging(rtc::Location location);
  ~ScopedRegisterThreadForDebugging();
#else
  explicit ScopedRegisterThreadForDebugging(rtc::Location) {}
#endif

  // Not movable or copyable, because we can't duplicate the resource it owns,
  // and it needs a constant address.
  ScopedRegisterThreadForDebugging(const ScopedRegisterThreadForDebugging&) =
      delete;
  ScopedRegisterThreadForDebugging(ScopedRegisterThreadForDebugging&&) = delete;
  ScopedRegisterThreadForDebugging& operator=(
      const ScopedRegisterThreadForDebugging&) = delete;
  ScopedRegisterThreadForDebugging& operator=(
      ScopedRegisterThreadForDebugging&&) = delete;
};

#if defined(WEBRTC_ANDROID) && !defined(WEBRTC_CHROMIUM_BUILD)
void PrintStackTracesOfRegisteredThreads();
#else
inline void PrintStackTracesOfRegisteredThreads() {}
#endif

}  // namespace webrtc

#endif  // RTC_BASE_SYSTEM_THREAD_REGISTRY_H_
