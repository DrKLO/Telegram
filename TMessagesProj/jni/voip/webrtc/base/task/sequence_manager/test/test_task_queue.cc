// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/sequence_manager/test/test_task_queue.h"

#include "base/task/sequence_manager/task_queue_impl.h"

namespace base {
namespace sequence_manager {

TestTaskQueue::TestTaskQueue(std::unique_ptr<internal::TaskQueueImpl> impl,
                             const TaskQueue::Spec& spec)
    : TaskQueue(std::move(impl), spec) {}

TestTaskQueue::~TestTaskQueue() = default;

WeakPtr<TestTaskQueue> TestTaskQueue::GetWeakPtr() {
  return weak_factory_.GetWeakPtr();
}

}  // namespace sequence_manager
}  // namespace base
