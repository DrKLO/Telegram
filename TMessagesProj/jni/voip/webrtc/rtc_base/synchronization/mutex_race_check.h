/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_SYNCHRONIZATION_MUTEX_RACE_CHECK_H_
#define RTC_BASE_SYNCHRONIZATION_MUTEX_RACE_CHECK_H_

#include <atomic>

#include "absl/base/attributes.h"
#include "rtc_base/checks.h"
#include "rtc_base/system/unused.h"
#include "rtc_base/thread_annotations.h"

namespace webrtc {

// This implementation class is useful when a consuming project can guarantee
// that all WebRTC invocation is happening serially. Additionally, the consuming
// project cannot use WebRTC code that spawn threads or task queues.
//
// The class internally check fails on Lock() if it finds the consumer actually
// invokes WebRTC concurrently.
//
// To use the race check mutex, define WEBRTC_RACE_CHECK_MUTEX globally. This
// also adds a dependency to absl::Mutex from logging.cc because even though
// objects are invoked serially, the logging is static and invoked concurrently
// and hence needs protection.
class RTC_LOCKABLE MutexImpl final {
 public:
  MutexImpl() = default;
  MutexImpl(const MutexImpl&) = delete;
  MutexImpl& operator=(const MutexImpl&) = delete;

  void Lock() RTC_EXCLUSIVE_LOCK_FUNCTION() {
    bool was_free = free_.exchange(false, std::memory_order_acquire);
    RTC_CHECK(was_free)
        << "WEBRTC_RACE_CHECK_MUTEX: mutex locked concurrently.";
  }
  ABSL_MUST_USE_RESULT bool TryLock() RTC_EXCLUSIVE_TRYLOCK_FUNCTION(true) {
    bool was_free = free_.exchange(false, std::memory_order_acquire);
    return was_free;
  }
  void Unlock() RTC_UNLOCK_FUNCTION() {
    free_.store(true, std::memory_order_release);
  }

 private:
  // Release-acquire ordering is used.
  // - In the Lock methods we're guaranteeing that reads and writes happening
  // after the (Try)Lock don't appear to have happened before the Lock (acquire
  // ordering).
  // - In the Unlock method we're guaranteeing that reads and writes happening
  // before the Unlock don't appear to happen after it (release ordering).
  std::atomic<bool> free_{true};
};

}  // namespace webrtc

#endif  // RTC_BASE_SYNCHRONIZATION_MUTEX_RACE_CHECK_H_
