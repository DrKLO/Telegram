// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/thread_pool/worker_thread_stack.h"

#include <algorithm>

#include "base/logging.h"
#include "base/stl_util.h"
#include "base/task/thread_pool/worker_thread.h"

namespace base {
namespace internal {

WorkerThreadStack::WorkerThreadStack() = default;

WorkerThreadStack::~WorkerThreadStack() = default;

void WorkerThreadStack::Push(WorkerThread* worker) {
  DCHECK(!Contains(worker)) << "WorkerThread already on stack";
  if (!IsEmpty())
    stack_.back()->BeginUnusedPeriod();
  stack_.push_back(worker);
}

WorkerThread* WorkerThreadStack::Pop() {
  if (IsEmpty())
    return nullptr;
  WorkerThread* const worker = stack_.back();
  stack_.pop_back();
  if (!IsEmpty())
    stack_.back()->EndUnusedPeriod();
  return worker;
}

WorkerThread* WorkerThreadStack::Peek() const {
  if (IsEmpty())
    return nullptr;
  return stack_.back();
}

bool WorkerThreadStack::Contains(const WorkerThread* worker) const {
  return base::Contains(stack_, worker);
}

void WorkerThreadStack::Remove(const WorkerThread* worker) {
  DCHECK(!IsEmpty());
  DCHECK_NE(worker, stack_.back());
  auto it = std::find(stack_.begin(), stack_.end(), worker);
  DCHECK(it != stack_.end());
  DCHECK_NE(TimeTicks(), (*it)->GetLastUsedTime());
  stack_.erase(it);
}

}  // namespace internal
}  // namespace base
