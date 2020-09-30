// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/threading/sequenced_task_runner_handle.h"

#include <utility>

#include "base/lazy_instance.h"
#include "base/logging.h"
#include "base/threading/thread_local.h"

namespace base {

namespace {

LazyInstance<ThreadLocalPointer<SequencedTaskRunnerHandle>>::Leaky
    sequenced_task_runner_tls = LAZY_INSTANCE_INITIALIZER;

}  // namespace

// static
const scoped_refptr<SequencedTaskRunner>& SequencedTaskRunnerHandle::Get() {
  const SequencedTaskRunnerHandle* current =
      sequenced_task_runner_tls.Pointer()->Get();
  CHECK(current)
      << "Error: This caller requires a sequenced context (i.e. the current "
         "task needs to run from a SequencedTaskRunner). If you're in a test "
         "refer to //docs/threading_and_tasks_testing.md.";
  return current->task_runner_;
}

// static
bool SequencedTaskRunnerHandle::IsSet() {
  return !!sequenced_task_runner_tls.Pointer()->Get();
}

SequencedTaskRunnerHandle::SequencedTaskRunnerHandle(
    scoped_refptr<SequencedTaskRunner> task_runner)
    : task_runner_(std::move(task_runner)) {
  DCHECK(task_runner_->RunsTasksInCurrentSequence());
  DCHECK(!SequencedTaskRunnerHandle::IsSet());
  sequenced_task_runner_tls.Pointer()->Set(this);
}

SequencedTaskRunnerHandle::~SequencedTaskRunnerHandle() {
  DCHECK(task_runner_->RunsTasksInCurrentSequence());
  DCHECK_EQ(sequenced_task_runner_tls.Pointer()->Get(), this);
  sequenced_task_runner_tls.Pointer()->Set(nullptr);
}

}  // namespace base
