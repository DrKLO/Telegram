// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/thread_pool/environment_config.h"

#include "base/synchronization/lock.h"
#include "base/threading/platform_thread.h"
#include "build/build_config.h"

namespace base {
namespace internal {

namespace {

bool CanUseBackgroundPriorityForWorkerThreadImpl() {
  // When Lock doesn't handle multiple thread priorities, run all
  // WorkerThread with a normal priority to avoid priority inversion when a
  // thread running with a normal priority tries to acquire a lock held by a
  // thread running with a background priority.
  if (!Lock::HandlesMultipleThreadPriorities())
    return false;

#if !defined(OS_ANDROID)
  // When thread priority can't be increased to NORMAL, run all threads with a
  // NORMAL priority to avoid priority inversions on shutdown (ThreadPoolImpl
  // increases BACKGROUND threads priority to NORMAL on shutdown while resolving
  // remaining shutdown blocking tasks).
  //
  // This is ignored on Android, because it doesn't have a clean shutdown phase.
  if (!PlatformThread::CanIncreaseThreadPriority(ThreadPriority::NORMAL))
    return false;
#endif  // defined(OS_ANDROID)

  return true;
}

}  // namespace

bool CanUseBackgroundPriorityForWorkerThread() {
  static const bool can_use_background_priority_for_worker_thread =
      CanUseBackgroundPriorityForWorkerThreadImpl();
  return can_use_background_priority_for_worker_thread;
}

}  // namespace internal
}  // namespace base
