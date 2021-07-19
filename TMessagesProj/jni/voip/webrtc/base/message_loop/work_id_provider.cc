// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/message_loop/work_id_provider.h"

#include <memory>

#include "base/memory/ptr_util.h"
#include "base/no_destructor.h"
#include "base/threading/thread_local.h"

namespace base {

// static
WorkIdProvider* WorkIdProvider::GetForCurrentThread() {
  static NoDestructor<ThreadLocalOwnedPointer<WorkIdProvider>> instance;
  if (!instance->Get())
    instance->Set(WrapUnique(new WorkIdProvider));
  return instance->Get();
}

// This function must support being invoked while other threads are suspended so
// must not take any locks, including indirectly through use of heap allocation,
// LOG, CHECK, or DCHECK.
unsigned int WorkIdProvider::GetWorkId() {
  return work_id_.load(std::memory_order_acquire);
}

WorkIdProvider::~WorkIdProvider() = default;

void WorkIdProvider::SetCurrentWorkIdForTesting(unsigned int id) {
  work_id_.store(id, std::memory_order_relaxed);
}

void WorkIdProvider::IncrementWorkIdForTesting() {
  IncrementWorkId();
}

WorkIdProvider::WorkIdProvider() : work_id_(0) {}

void WorkIdProvider::IncrementWorkId() {
  DCHECK_CALLED_ON_VALID_THREAD(thread_checker_);

  unsigned int next_id = work_id_.load(std::memory_order_relaxed) + 1;
  // Reserve 0 to mean no work items have been executed.
  if (next_id == 0)
    ++next_id;
  // Release order ensures this state is visible to other threads prior to the
  // following task/event execution.
  work_id_.store(next_id, std::memory_order_release);
}

}  // namespace base
