// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/thread_pool.h"

#include "base/logging.h"
#include "base/task/scoped_set_task_priority_for_current_thread.h"
#include "base/task/task_traits.h"
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
    ThreadPool::PostTask(from_here, traits_, std::move(task));
    return true;
  }

  const TaskTraits traits_;
};

internal::ThreadPoolImpl* GetThreadPoolImpl() {
  auto* instance = ThreadPoolInstance::Get();
  DCHECK(instance)
      << "Ref. Prerequisite section of base/task/thread_pool.h.\n"
         "Hint: if this is in a unit test, you're likely merely missing a "
         "base::test::TaskEnvironment member in your fixture (or your fixture "
         "is using a base::test::SingleThreadTaskEnvironment and now needs a "
         "full base::test::TaskEnvironment).\n";
  return static_cast<internal::ThreadPoolImpl*>(instance);
}

}  // namespace

// static
bool ThreadPool::PostTask(const Location& from_here, OnceClosure task) {
  return ThreadPool::PostDelayedTask(from_here, std::move(task), TimeDelta());
}

// static
bool ThreadPool::PostDelayedTask(const Location& from_here,
                                 OnceClosure task,
                                 TimeDelta delay) {
  return ThreadPool::PostDelayedTask(from_here, {}, std::move(task), delay);
}

// static
bool ThreadPool::PostTaskAndReply(const Location& from_here,
                                  OnceClosure task,
                                  OnceClosure reply) {
  return ThreadPool::PostTaskAndReply(from_here, {}, std::move(task),
                                      std::move(reply));
}

// static
bool ThreadPool::PostTask(const Location& from_here,
                          const TaskTraits& traits,
                          OnceClosure task) {
  return ThreadPool::PostDelayedTask(from_here, traits, std::move(task),
                                     TimeDelta());
}

// static
bool ThreadPool::PostDelayedTask(const Location& from_here,
                                 const TaskTraits& traits,
                                 OnceClosure task,
                                 TimeDelta delay) {
  return GetThreadPoolImpl()->PostDelayedTask(from_here, traits,
                                              std::move(task), delay);
}

// static
bool ThreadPool::PostTaskAndReply(const Location& from_here,
                                  const TaskTraits& traits,
                                  OnceClosure task,
                                  OnceClosure reply) {
  return PostTaskAndReplyWithTraitsTaskRunner(traits).PostTaskAndReply(
      from_here, std::move(task), std::move(reply));
}

// static
scoped_refptr<TaskRunner> ThreadPool::CreateTaskRunner(
    const TaskTraits& traits) {
  return GetThreadPoolImpl()->CreateTaskRunner(traits);
}

// static
scoped_refptr<SequencedTaskRunner> ThreadPool::CreateSequencedTaskRunner(
    const TaskTraits& traits) {
  return GetThreadPoolImpl()->CreateSequencedTaskRunner(traits);
}

// static
scoped_refptr<UpdateableSequencedTaskRunner>
ThreadPool::CreateUpdateableSequencedTaskRunner(const TaskTraits& traits) {
  return GetThreadPoolImpl()->CreateUpdateableSequencedTaskRunner(traits);
}

// static
scoped_refptr<SingleThreadTaskRunner> ThreadPool::CreateSingleThreadTaskRunner(
    const TaskTraits& traits,
    SingleThreadTaskRunnerThreadMode thread_mode) {
  return GetThreadPoolImpl()->CreateSingleThreadTaskRunner(traits, thread_mode);
}

#if defined(OS_WIN)
// static
scoped_refptr<SingleThreadTaskRunner> ThreadPool::CreateCOMSTATaskRunner(
    const TaskTraits& traits,
    SingleThreadTaskRunnerThreadMode thread_mode) {
  return GetThreadPoolImpl()->CreateCOMSTATaskRunner(traits, thread_mode);
}
#endif  // defined(OS_WIN)

}  // namespace base
