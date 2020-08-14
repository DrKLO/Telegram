/*
 *  Copyright 2019 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/system/thread_registry.h"

#include <map>
#include <utility>

#include "absl/base/attributes.h"
#include "rtc_base/logging.h"
#include "rtc_base/platform_thread_types.h"
#include "rtc_base/synchronization/mutex.h"
#include "sdk/android/native_api/stacktrace/stacktrace.h"

namespace webrtc {

namespace {

struct ThreadData {
  const rtc::PlatformThreadId thread_id;
  const rtc::Location location;
};

// The map of registered threads, and the lock that protects it. We create the
// map on first use, and never destroy it.
ABSL_CONST_INIT GlobalMutex g_thread_registry_lock(absl::kConstInit);
ABSL_CONST_INIT std::map<const ScopedRegisterThreadForDebugging*, ThreadData>*
    g_registered_threads = nullptr;

}  // namespace

ScopedRegisterThreadForDebugging::ScopedRegisterThreadForDebugging(
    rtc::Location location) {
  GlobalMutexLock gls(&g_thread_registry_lock);
  if (g_registered_threads == nullptr) {
    g_registered_threads =
        new std::map<const ScopedRegisterThreadForDebugging*, ThreadData>();
  }
  const auto result = g_registered_threads->insert(
      std::make_pair(this, ThreadData{rtc::CurrentThreadId(), location}));
  RTC_DCHECK(result.second);  // Insertion succeeded without collisions.
}

ScopedRegisterThreadForDebugging::~ScopedRegisterThreadForDebugging() {
  GlobalMutexLock gls(&g_thread_registry_lock);
  RTC_DCHECK(g_registered_threads != nullptr);
  const int num_erased = g_registered_threads->erase(this);
  RTC_DCHECK_EQ(num_erased, 1);
}

void PrintStackTracesOfRegisteredThreads() {
  GlobalMutexLock gls(&g_thread_registry_lock);
  if (g_registered_threads == nullptr) {
    return;
  }
  for (const auto& e : *g_registered_threads) {
    const ThreadData& td = e.second;
    RTC_LOG(LS_WARNING) << "Thread " << td.thread_id << " registered at "
                        << td.location.ToString() << ":";
    RTC_LOG(LS_WARNING) << StackTraceToString(GetStackTrace(td.thread_id));
  }
}

}  // namespace webrtc
