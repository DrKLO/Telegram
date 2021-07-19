// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/thread_pool/thread_group_native_mac.h"

#include "base/task/thread_pool/task_tracker.h"

namespace base {
namespace internal {

ThreadGroupNativeMac::ThreadGroupNativeMac(
    TrackedRef<TaskTracker> task_tracker,
    TrackedRef<Delegate> delegate,
    ThreadGroup* predecessor_thread_group)
    : ThreadGroupNative(std::move(task_tracker),
                        std::move(delegate),
                        predecessor_thread_group) {}

ThreadGroupNativeMac::~ThreadGroupNativeMac() {}

void ThreadGroupNativeMac::StartImpl() {
  queue_.reset(dispatch_queue_create("org.chromium.base.ThreadPool.ThreadGroup",
                                     DISPATCH_QUEUE_CONCURRENT));
  group_.reset(dispatch_group_create());
}

void ThreadGroupNativeMac::JoinImpl() {
  dispatch_group_wait(group_, DISPATCH_TIME_FOREVER);
}

void ThreadGroupNativeMac::SubmitWork() {
  // TODO(adityakeerthi): Handle priorities by having multiple dispatch queues
  // with different qualities-of-service.
  dispatch_group_async(group_, queue_, ^{
    RunNextTaskSourceImpl();
  });
}

}  // namespace internal
}  // namespace base
