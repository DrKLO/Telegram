// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/sequenced_task_runner.h"

#include <utility>

#include "base/bind.h"

namespace base {

bool SequencedTaskRunner::PostNonNestableTask(const Location& from_here,
                                              OnceClosure task) {
  return PostNonNestableDelayedTask(from_here, std::move(task),
                                    base::TimeDelta());
}

bool SequencedTaskRunner::DeleteOrReleaseSoonInternal(
    const Location& from_here,
    void (*deleter)(const void*),
    const void* object) {
  return PostNonNestableTask(from_here, BindOnce(deleter, object));
}

OnTaskRunnerDeleter::OnTaskRunnerDeleter(
    scoped_refptr<SequencedTaskRunner> task_runner)
    : task_runner_(std::move(task_runner)) {
}

OnTaskRunnerDeleter::~OnTaskRunnerDeleter() = default;

OnTaskRunnerDeleter::OnTaskRunnerDeleter(OnTaskRunnerDeleter&&) = default;

OnTaskRunnerDeleter& OnTaskRunnerDeleter::operator=(
    OnTaskRunnerDeleter&&) = default;

}  // namespace base
