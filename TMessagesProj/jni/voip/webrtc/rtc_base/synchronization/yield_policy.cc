/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "rtc_base/synchronization/yield_policy.h"

#include "absl/base/attributes.h"
#include "absl/base/config.h"
#include "rtc_base/checks.h"
#if !defined(ABSL_HAVE_THREAD_LOCAL) && defined(WEBRTC_POSIX)
#include <pthread.h>
#endif

namespace rtc {
namespace {

#if defined(ABSL_HAVE_THREAD_LOCAL)

ABSL_CONST_INIT thread_local YieldInterface* current_yield_policy = nullptr;

YieldInterface* GetCurrentYieldPolicy() {
  return current_yield_policy;
}

void SetCurrentYieldPolicy(YieldInterface* ptr) {
  current_yield_policy = ptr;
}

#elif defined(WEBRTC_POSIX)

// Emscripten does not support the C++11 thread_local keyword but does support
// the pthread thread-local storage API.
// https://github.com/emscripten-core/emscripten/issues/3502

ABSL_CONST_INIT pthread_key_t g_current_yield_policy_tls = 0;

void InitializeTls() {
  RTC_CHECK_EQ(pthread_key_create(&g_current_yield_policy_tls, nullptr), 0);
}

pthread_key_t GetCurrentYieldPolicyTls() {
  static pthread_once_t init_once = PTHREAD_ONCE_INIT;
  RTC_CHECK_EQ(pthread_once(&init_once, &InitializeTls), 0);
  return g_current_yield_policy_tls;
}

YieldInterface* GetCurrentYieldPolicy() {
  return static_cast<YieldInterface*>(
      pthread_getspecific(GetCurrentYieldPolicyTls()));
}

void SetCurrentYieldPolicy(YieldInterface* ptr) {
  pthread_setspecific(GetCurrentYieldPolicyTls(), ptr);
}

#else
#error Unsupported platform
#endif

}  // namespace

ScopedYieldPolicy::ScopedYieldPolicy(YieldInterface* policy)
    : previous_(GetCurrentYieldPolicy()) {
  SetCurrentYieldPolicy(policy);
}

ScopedYieldPolicy::~ScopedYieldPolicy() {
  SetCurrentYieldPolicy(previous_);
}

void ScopedYieldPolicy::YieldExecution() {
  YieldInterface* current = GetCurrentYieldPolicy();
  if (current)
    current->YieldExecution();
}

}  // namespace rtc
