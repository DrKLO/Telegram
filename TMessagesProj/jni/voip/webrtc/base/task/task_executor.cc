// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/task_executor.h"

#include <type_traits>

#include "base/no_destructor.h"
#include "base/task/task_traits.h"
#include "base/task/task_traits_extension.h"
#include "base/threading/thread_local.h"

namespace base {

namespace {

// Maps TaskTraits extension IDs to registered TaskExecutors. Index |n|
// corresponds to id |n - 1|.
using TaskExecutorMap =
    std::array<TaskExecutor*, TaskTraitsExtensionStorage::kMaxExtensionId>;
TaskExecutorMap* GetTaskExecutorMap() {
  static_assert(std::is_trivially_destructible<TaskExecutorMap>::value,
                "TaskExecutorMap not trivially destructible");
  static TaskExecutorMap executors{};
  return &executors;
}

static_assert(
    TaskTraitsExtensionStorage::kInvalidExtensionId == 0,
    "TaskExecutorMap depends on 0 being an invalid TaskTraits extension ID");

}  // namespace

ThreadLocalPointer<TaskExecutor>* GetTLSForCurrentTaskExecutor() {
  static NoDestructor<ThreadLocalPointer<TaskExecutor>> instance;
  return instance.get();
}

void SetTaskExecutorForCurrentThread(TaskExecutor* task_executor) {
  DCHECK(!task_executor || !GetTLSForCurrentTaskExecutor()->Get() ||
         GetTLSForCurrentTaskExecutor()->Get() == task_executor);
  GetTLSForCurrentTaskExecutor()->Set(task_executor);
}

TaskExecutor* GetTaskExecutorForCurrentThread() {
  return GetTLSForCurrentTaskExecutor()->Get();
}

void RegisterTaskExecutor(uint8_t extension_id, TaskExecutor* task_executor) {
  DCHECK_NE(extension_id, TaskTraitsExtensionStorage::kInvalidExtensionId);
  DCHECK_LE(extension_id, TaskTraitsExtensionStorage::kMaxExtensionId);
  DCHECK_EQ((*GetTaskExecutorMap())[extension_id - 1], nullptr);

  (*GetTaskExecutorMap())[extension_id - 1] = task_executor;
}

void UnregisterTaskExecutorForTesting(uint8_t extension_id) {
  DCHECK_NE(extension_id, TaskTraitsExtensionStorage::kInvalidExtensionId);
  DCHECK_LE(extension_id, TaskTraitsExtensionStorage::kMaxExtensionId);
  DCHECK_NE((*GetTaskExecutorMap())[extension_id - 1], nullptr);

  (*GetTaskExecutorMap())[extension_id - 1] = nullptr;
}

TaskExecutor* GetRegisteredTaskExecutorForTraits(const TaskTraits& traits) {
  uint8_t extension_id = traits.extension_id();
  if (extension_id != TaskTraitsExtensionStorage::kInvalidExtensionId) {
    TaskExecutor* executor = (*GetTaskExecutorMap())[extension_id - 1];
    DCHECK(executor);
    return executor;
  }

  return nullptr;
}

}  // namespace base
