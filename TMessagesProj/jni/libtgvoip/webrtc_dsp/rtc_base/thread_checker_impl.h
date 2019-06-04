/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Borrowed from Chromium's src/base/threading/thread_checker_impl.h.

#ifndef RTC_BASE_THREAD_CHECKER_IMPL_H_
#define RTC_BASE_THREAD_CHECKER_IMPL_H_

#include "rtc_base/criticalsection.h"
#include "rtc_base/platform_thread_types.h"

namespace rtc {

// Real implementation of ThreadChecker, for use in debug mode, or
// for temporary use in release mode (e.g. to RTC_CHECK on a threading issue
// seen only in the wild).
//
// Note: You should almost always use the ThreadChecker class to get the
// right version for your build configuration.
class ThreadCheckerImpl {
 public:
  ThreadCheckerImpl();
  ~ThreadCheckerImpl();

  bool CalledOnValidThread() const;

  // Changes the thread that is checked for in CalledOnValidThread.  This may
  // be useful when an object may be created on one thread and then used
  // exclusively on another thread.
  void DetachFromThread();

 private:
  CriticalSection lock_;
  // This is mutable so that CalledOnValidThread can set it.
  // It's guarded by |lock_|.
  mutable PlatformThreadRef valid_thread_;
};

}  // namespace rtc

#endif  // RTC_BASE_THREAD_CHECKER_IMPL_H_
