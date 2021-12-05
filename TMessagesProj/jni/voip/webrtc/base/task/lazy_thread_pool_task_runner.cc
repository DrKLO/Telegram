// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/lazy_thread_pool_task_runner.h"

#include <utility>

#include "base/lazy_instance_helpers.h"
#include "base/logging.h"
#include "base/task/thread_pool.h"

namespace base {
namespace internal {

namespace {
ScopedLazyTaskRunnerListForTesting* g_scoped_lazy_task_runner_list_for_testing =
    nullptr;
}  // namespace

template <typename TaskRunnerType, bool com_sta>
void LazyThreadPoolTaskRunner<TaskRunnerType, com_sta>::Reset() {
  subtle::AtomicWord state = subtle::Acquire_Load(&state_);

  DCHECK_NE(state, kLazyInstanceStateCreating) << "Race: all threads should be "
                                                  "unwound in unittests before "
                                                  "resetting TaskRunners.";

  // Return if no reference is held by this instance.
  if (!state)
    return;

  // Release the reference acquired in Get().
  SequencedTaskRunner* task_runner = reinterpret_cast<TaskRunnerType*>(state);
  task_runner->Release();

  // Clear the state.
  subtle::NoBarrier_Store(&state_, 0);
}

template <>
scoped_refptr<SequencedTaskRunner>
LazyThreadPoolTaskRunner<SequencedTaskRunner, false>::Create() {
  // It is invalid to specify a SingleThreadTaskRunnerThreadMode with a
  // LazyThreadPoolSequencedTaskRunner.
  DCHECK_EQ(thread_mode_, SingleThreadTaskRunnerThreadMode::SHARED);

  return ThreadPool::CreateSequencedTaskRunner(traits_);
}

template <>
scoped_refptr<SingleThreadTaskRunner>
LazyThreadPoolTaskRunner<SingleThreadTaskRunner, false>::Create() {
  return ThreadPool::CreateSingleThreadTaskRunner(traits_, thread_mode_);
}

#if defined(OS_WIN)
template <>
scoped_refptr<SingleThreadTaskRunner>
LazyThreadPoolTaskRunner<SingleThreadTaskRunner, true>::Create() {
  return ThreadPool::CreateCOMSTATaskRunner(traits_, thread_mode_);
}
#endif

// static
template <typename TaskRunnerType, bool com_sta>
TaskRunnerType* LazyThreadPoolTaskRunner<TaskRunnerType, com_sta>::CreateRaw(
    void* void_self) {
  auto self =
      reinterpret_cast<LazyThreadPoolTaskRunner<TaskRunnerType, com_sta>*>(
          void_self);

  scoped_refptr<TaskRunnerType> task_runner = self->Create();

  // Acquire a reference to the TaskRunner. The reference will either
  // never be released or be released in Reset(). The reference is not
  // managed by a scoped_refptr because adding a scoped_refptr member to
  // LazyThreadPoolTaskRunner would prevent its static initialization.
  task_runner->AddRef();

  // Reset this instance when the current
  // ScopedLazyTaskRunnerListForTesting is destroyed, if any.
  if (g_scoped_lazy_task_runner_list_for_testing) {
    g_scoped_lazy_task_runner_list_for_testing->AddCallback(
        BindOnce(&LazyThreadPoolTaskRunner<TaskRunnerType, com_sta>::Reset,
                 Unretained(self)));
  }

  return task_runner.get();
}

template <typename TaskRunnerType, bool com_sta>
scoped_refptr<TaskRunnerType>
LazyThreadPoolTaskRunner<TaskRunnerType, com_sta>::Get() {
  return WrapRefCounted(subtle::GetOrCreateLazyPointer(
      &state_, &LazyThreadPoolTaskRunner<TaskRunnerType, com_sta>::CreateRaw,
      reinterpret_cast<void*>(this), nullptr, nullptr));
}

template class LazyThreadPoolTaskRunner<SequencedTaskRunner, false>;
template class LazyThreadPoolTaskRunner<SingleThreadTaskRunner, false>;

#if defined(OS_WIN)
template class LazyThreadPoolTaskRunner<SingleThreadTaskRunner, true>;
#endif

ScopedLazyTaskRunnerListForTesting::ScopedLazyTaskRunnerListForTesting() {
  DCHECK(!g_scoped_lazy_task_runner_list_for_testing);
  g_scoped_lazy_task_runner_list_for_testing = this;
}

ScopedLazyTaskRunnerListForTesting::~ScopedLazyTaskRunnerListForTesting() {
  internal::CheckedAutoLock auto_lock(lock_);
  for (auto& callback : callbacks_)
    std::move(callback).Run();
  g_scoped_lazy_task_runner_list_for_testing = nullptr;
}

void ScopedLazyTaskRunnerListForTesting::AddCallback(OnceClosure callback) {
  internal::CheckedAutoLock auto_lock(lock_);
  callbacks_.push_back(std::move(callback));
}

}  // namespace internal
}  // namespace base
