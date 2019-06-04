/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Borrowed from Chromium's src/base/threading/thread_checker_impl.cc.

#include "rtc_base/thread_checker_impl.h"

namespace rtc {

ThreadCheckerImpl::ThreadCheckerImpl() : valid_thread_(CurrentThreadRef()) {}

ThreadCheckerImpl::~ThreadCheckerImpl() {}

bool ThreadCheckerImpl::CalledOnValidThread() const {
  const PlatformThreadRef current_thread = CurrentThreadRef();
  CritScope scoped_lock(&lock_);
  if (!valid_thread_)  // Set if previously detached.
    valid_thread_ = current_thread;
  return IsThreadRefEqual(valid_thread_, current_thread);
}

void ThreadCheckerImpl::DetachFromThread() {
  CritScope scoped_lock(&lock_);
  valid_thread_ = 0;
}

}  // namespace rtc
