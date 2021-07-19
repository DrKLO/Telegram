// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/thread_pool/task.h"

#include <utility>

#include "base/atomic_sequence_num.h"

namespace base {
namespace internal {

namespace {

AtomicSequenceNumber g_sequence_nums_for_tracing;

}  // namespace

Task::Task() = default;

Task::Task(const Location& posted_from, OnceClosure task, TimeDelta delay)
    : PendingTask(posted_from,
                  std::move(task),
                  delay.is_zero() ? TimeTicks() : TimeTicks::Now() + delay,
                  Nestable::kNonNestable) {
  // ThreadPoolImpl doesn't use |sequence_num| but tracing (toplevel.flow)
  // relies on it being unique. While this subtle dependency is a bit
  // overreaching, ThreadPoolImpl is the only task system that doesn't use
  // |sequence_num| and the dependent code rarely changes so this isn't worth a
  // big change and faking it here isn't too bad for now (posting tasks is full
  // of atomic ops already).
  this->sequence_num = g_sequence_nums_for_tracing.GetNext();
}

// This should be "= default but MSVC has trouble with "noexcept = default" in
// this case.
Task::Task(Task&& other) noexcept : PendingTask(std::move(other)) {}

Task::~Task() = default;

Task& Task::operator=(Task&& other) = default;

}  // namespace internal
}  // namespace base
