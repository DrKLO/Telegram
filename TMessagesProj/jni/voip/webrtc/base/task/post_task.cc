// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/post_task.h"

#include <utility>

#include "base/logging.h"
#include "base/task/scoped_set_task_priority_for_current_thread.h"
#include "base/task/task_executor.h"
#include "base/task/thread_pool/thread_pool_impl.h"
#include "base/task/thread_pool/thread_pool_instance.h"
#include "base/threading/post_task_and_reply_impl.h"

namespace base {

namespace {

class PostTaskAndReplyWithTraitsTaskRunner
    : public internal::PostTaskAndReplyImpl {
 public:
  explicit PostTaskAndReplyWithTraitsTaskRunner(const TaskTraits& traits)
      : traits_(traits) {}

 private:
  bool PostTask(const Location& from_here, OnceClosure task) override {
    ::base::PostTask(from_here, traits_, std::move(task));
    return true;
  }

  const TaskTraits traits_;
};

TaskExecutor* GetTaskExecutorForTraits(const TaskTraits& traits) {
  const bool has_extension =
      traits.extension_id() != TaskTraitsExtensionStorage::kInvalidExtensionId;
  DCHECK(has_extension ^ traits.use_thread_pool())
      << "A destination (e.g. ThreadPool or BrowserThread) must be specified "
         "to use the post_task.h API. However, you should prefer the direct "
         "thread_pool.h or browser_thread.h APIs in new code.";

  if (traits.use_thread_pool()) {
    DCHECK(ThreadPoolInstance::Get())
        << "Ref. Prerequisite section of post_task.h for base::ThreadPool "
           "usage.\n"
           "Hint: if this is in a unit test, you're likely merely missing a "
           "base::test::TaskEnvironment member in your fixture (or your "
           "fixture is using a base::test::SingleThreadTaskEnvironment and now "
           "needs a full base::test::TaskEnvironment).\n";
    return static_cast<internal::ThreadPoolImpl*>(ThreadPoolInstance::Get());
  }

  // Assume |has_extension| per above invariant.
  TaskExecutor* executor = GetRegisteredTaskExecutorForTraits(traits);
  DCHECK(executor)
      << "A TaskExecutor wasn't yet registered for this extension.\n"
         "Hint: if this is in a unit test, you're likely missing a "
         "content::BrowserTaskEnvironment member in your fixture.";
  return executor;
}

}  // namespace

bool PostTask(const Location& from_here, OnceClosure task) {
  // TODO(skyostil): Make task traits required here too.
  return PostDelayedTask(from_here, {ThreadPool()}, std::move(task),
                         TimeDelta());
}

bool PostTaskAndReply(const Location& from_here,
                      OnceClosure task,
                      OnceClosure reply) {
  return PostTaskAndReply(from_here, {ThreadPool()}, std::move(task),
                          std::move(reply));
}

bool PostTask(const Location& from_here,
              const TaskTraits& traits,
              OnceClosure task) {
  return PostDelayedTask(from_here, traits, std::move(task), TimeDelta());
}

bool PostDelayedTask(const Location& from_here,
                     const TaskTraits& traits,
                     OnceClosure task,
                     TimeDelta delay) {
  return GetTaskExecutorForTraits(traits)->PostDelayedTask(
      from_here, traits, std::move(task), delay);
}

bool PostTaskAndReply(const Location& from_here,
                      const TaskTraits& traits,
                      OnceClosure task,
                      OnceClosure reply) {
  return PostTaskAndReplyWithTraitsTaskRunner(traits).PostTaskAndReply(
      from_here, std::move(task), std::move(reply));
}

scoped_refptr<TaskRunner> CreateTaskRunner(const TaskTraits& traits) {
  return GetTaskExecutorForTraits(traits)->CreateTaskRunner(traits);
}

scoped_refptr<SequencedTaskRunner> CreateSequencedTaskRunner(
    const TaskTraits& traits) {
  return GetTaskExecutorForTraits(traits)->CreateSequencedTaskRunner(traits);
}

scoped_refptr<UpdateableSequencedTaskRunner>
CreateUpdateableSequencedTaskRunner(const TaskTraits& traits) {
  DCHECK(ThreadPoolInstance::Get())
      << "Ref. Prerequisite section of post_task.h.\n\n"
         "Hint: if this is in a unit test, you're likely merely missing a "
         "base::test::TaskEnvironment member in your fixture.\n";
  DCHECK(traits.use_thread_pool())
      << "The base::UseThreadPool() trait is mandatory with "
         "CreateUpdateableSequencedTaskRunner().";
  CHECK_EQ(traits.extension_id(),
           TaskTraitsExtensionStorage::kInvalidExtensionId)
      << "Extension traits cannot be used with "
         "CreateUpdateableSequencedTaskRunner().";
  return static_cast<internal::ThreadPoolImpl*>(ThreadPoolInstance::Get())
      ->CreateUpdateableSequencedTaskRunner(traits);
}

scoped_refptr<SingleThreadTaskRunner> CreateSingleThreadTaskRunner(
    const TaskTraits& traits,
    SingleThreadTaskRunnerThreadMode thread_mode) {
  return GetTaskExecutorForTraits(traits)->CreateSingleThreadTaskRunner(
      traits, thread_mode);
}

#if defined(OS_WIN)
scoped_refptr<SingleThreadTaskRunner> CreateCOMSTATaskRunner(
    const TaskTraits& traits,
    SingleThreadTaskRunnerThreadMode thread_mode) {
  return GetTaskExecutorForTraits(traits)->CreateCOMSTATaskRunner(traits,
                                                                  thread_mode);
}
#endif  // defined(OS_WIN)

}  // namespace base
