// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_POST_TASK_H_
#define BASE_TASK_POST_TASK_H_

#include <memory>
#include <utility>

#include "base/base_export.h"
#include "base/bind.h"
#include "base/callback.h"
#include "base/callback_helpers.h"
#include "base/location.h"
#include "base/memory/ref_counted.h"
#include "base/post_task_and_reply_with_result_internal.h"
#include "base/sequenced_task_runner.h"
#include "base/single_thread_task_runner.h"
#include "base/task/single_thread_task_runner_thread_mode.h"
#include "base/task/task_traits.h"
#include "base/task_runner.h"
#include "base/time/time.h"
#include "base/updateable_sequenced_task_runner.h"
#include "build/build_config.h"

namespace base {

// This is the interface to post tasks.
//
// Note: A migration is in-progress away from this API and in favor of explicit
// API-as-a-destination. thread_pool.h is now preferred to the
// base::ThreadPool() to post to the thread pool
//
// To post a simple one-off task with default traits:
//     PostTask(FROM_HERE, BindOnce(...));
// modern equivalent:
//     ThreadPool::PostTask(FROM_HERE, BindOnce(...));
//
// To post a high priority one-off task to respond to a user interaction:
//     PostTask(
//         FROM_HERE,
//         {ThreadPool(), TaskPriority::USER_BLOCKING},
//         BindOnce(...));
// modern equivalent:
//     ThreadPool::PostTask(
//         FROM_HERE,
//         {TaskPriority::USER_BLOCKING},
//         BindOnce(...));
//
// To post tasks that must run in sequence with default traits:
//     scoped_refptr<SequencedTaskRunner> task_runner =
//         CreateSequencedTaskRunner({ThreadPool()});
//     task_runner->PostTask(FROM_HERE, BindOnce(...));
//     task_runner->PostTask(FROM_HERE, BindOnce(...));
// modern equivalent:
//     scoped_refptr<SequencedTaskRunner> task_runner =
//         ThreadPool::CreateSequencedTaskRunner({});
//     task_runner->PostTask(FROM_HERE, BindOnce(...));
//     task_runner->PostTask(FROM_HERE, BindOnce(...));
//
// To post tasks that may block, must run in sequence and can be skipped on
// shutdown:
//     scoped_refptr<SequencedTaskRunner> task_runner =
//         CreateSequencedTaskRunner({ThreadPool(), MayBlock(),
//                                   TaskShutdownBehavior::SKIP_ON_SHUTDOWN});
//     task_runner->PostTask(FROM_HERE, BindOnce(...));
//     task_runner->PostTask(FROM_HERE, BindOnce(...));
// modern equivalent:
//     scoped_refptr<SequencedTaskRunner> task_runner =
//         ThreadPool::CreateSequencedTaskRunner(
//             {MayBlock(), TaskShutdownBehavior::SKIP_ON_SHUTDOWN});
//     task_runner->PostTask(FROM_HERE, BindOnce(...));
//     task_runner->PostTask(FROM_HERE, BindOnce(...));
//
// The default traits apply to tasks that:
//     (1) don't block (ref. MayBlock() and WithBaseSyncPrimitives()),
//     (2) prefer inheriting the current priority to specifying their own, and
//     (3) can either block shutdown or be skipped on shutdown
//         (implementation is free to choose a fitting default).
// Explicit traits must be specified for tasks for which these loose
// requirements are not sufficient.
//
// Tasks posted with only traits defined in base/task/task_traits.h run on
// threads owned by the registered ThreadPoolInstance (i.e. not on the main
// thread). An embedder (e.g. Chrome) can define additional traits to make tasks
// run on threads of their choosing.
//
// Tasks posted with the same traits will be scheduled in the order they were
// posted. IMPORTANT: Please note however that, unless the traits imply a
// single thread or sequence, this doesn't guarantee any *execution ordering*
// for tasks posted in a given order (being scheduled first doesn't mean it will
// run first -- could run in parallel or have its physical thread preempted).
//
// Prerequisite: A ThreadPoolInstance must have been registered for the current
// process via ThreadPoolInstance::Set() before the functions below are
// valid. This is typically done during the initialization phase in each
// process. If your code is not running in that phase, you most likely don't
// have to worry about this. You will encounter DCHECKs or nullptr dereferences
// if this is violated. For tests, prefer base::test::TaskEnvironment.

// Equivalent to calling PostTask with default TaskTraits.
BASE_EXPORT bool PostTask(const Location& from_here, OnceClosure task);
inline bool PostTask(OnceClosure task,
                     const Location& from_here = Location::Current()) {
  return PostTask(from_here, std::move(task));
}

// Equivalent to calling PostTaskAndReply with default TaskTraits.
BASE_EXPORT bool PostTaskAndReply(const Location& from_here,
                                  OnceClosure task,
                                  OnceClosure reply);

// Equivalent to calling PostTaskAndReplyWithResult with default TaskTraits.
template <typename TaskReturnType, typename ReplyArgType>
bool PostTaskAndReplyWithResult(const Location& from_here,
                                OnceCallback<TaskReturnType()> task,
                                OnceCallback<void(ReplyArgType)> reply) {
  return PostTaskAndReplyWithResult(from_here, {ThreadPool()}, std::move(task),
                                    std::move(reply));
}

// Posts |task| with specific |traits|. Returns false if the task definitely
// won't run because of current shutdown state.
BASE_EXPORT bool PostTask(const Location& from_here,
                          const TaskTraits& traits,
                          OnceClosure task);

// Posts |task| with specific |traits|. |task| will not run before |delay|
// expires. Returns false if the task definitely won't run because of current
// shutdown state.
//
// Specify a BEST_EFFORT priority via |traits| if the task doesn't have to run
// as soon as |delay| expires.
BASE_EXPORT bool PostDelayedTask(const Location& from_here,
                                 const TaskTraits& traits,
                                 OnceClosure task,
                                 TimeDelta delay);

// Posts |task| with specific |traits| and posts |reply| on the caller's
// execution context (i.e. same sequence or thread and same TaskTraits if
// applicable) when |task| completes. Returns false if the task definitely won't
// run because of current shutdown state. Can only be called when
// SequencedTaskRunnerHandle::IsSet().
BASE_EXPORT bool PostTaskAndReply(const Location& from_here,
                                  const TaskTraits& traits,
                                  OnceClosure task,
                                  OnceClosure reply);

// Posts |task| with specific |traits| and posts |reply| with the return value
// of |task| as argument on the caller's execution context (i.e. same sequence
// or thread and same TaskTraits if applicable) when |task| completes. Returns
// false if the task definitely won't run because of current shutdown state. Can
// only be called when SequencedTaskRunnerHandle::IsSet().
template <typename TaskReturnType, typename ReplyArgType>
bool PostTaskAndReplyWithResult(const Location& from_here,
                                const TaskTraits& traits,
                                OnceCallback<TaskReturnType()> task,
                                OnceCallback<void(ReplyArgType)> reply) {
  auto* result = new std::unique_ptr<TaskReturnType>();
  return PostTaskAndReply(
      from_here, traits,
      BindOnce(&internal::ReturnAsParamAdapter<TaskReturnType>, std::move(task),
               result),
      BindOnce(&internal::ReplyAdapter<TaskReturnType, ReplyArgType>,
               std::move(reply), Owned(result)));
}

// Returns a TaskRunner whose PostTask invocations result in scheduling tasks
// using |traits|. Tasks may run in any order and in parallel.
BASE_EXPORT scoped_refptr<TaskRunner> CreateTaskRunner(
    const TaskTraits& traits);

// Returns a SequencedTaskRunner whose PostTask invocations result in scheduling
// tasks using |traits|. Tasks run one at a time in posting order.
BASE_EXPORT scoped_refptr<SequencedTaskRunner> CreateSequencedTaskRunner(
    const TaskTraits& traits);

// Returns a task runner whose PostTask invocations result in scheduling tasks
// using |traits|. The priority in |traits| can be updated at any time via
// UpdateableSequencedTaskRunner::UpdatePriority(). An update affects all tasks
// posted to the task runner that aren't running yet. Tasks run one at a time in
// posting order.
//
// |traits| requirements:
// - base::ThreadPool() must be specified.
//     Note: Prefer the explicit (thread_pool.h) version of this API while we
//     migrate this one to it.
// - Extension traits (e.g. BrowserThread) cannot be specified.
// - base::ThreadPolicy must be specified if the priority of the task runner
//   will ever be increased from BEST_EFFORT.
BASE_EXPORT scoped_refptr<UpdateableSequencedTaskRunner>
CreateUpdateableSequencedTaskRunner(const TaskTraits& traits);

// Returns a SingleThreadTaskRunner whose PostTask invocations result in
// scheduling tasks using |traits| on a thread determined by |thread_mode|. See
// base/task/single_thread_task_runner_thread_mode.h for |thread_mode| details.
// If |traits| identifies an existing thread,
// SingleThreadTaskRunnerThreadMode::SHARED must be used. Tasks run on a single
// thread in posting order.
//
// If all you need is to make sure that tasks don't run concurrently (e.g.
// because they access a data structure which is not thread-safe), use
// CreateSequencedTaskRunner(). Only use this if you rely on a thread-affine API
// (it might be safer to assume thread-affinity when dealing with
// under-documented third-party APIs, e.g. other OS') or share data across tasks
// using thread-local storage.
BASE_EXPORT scoped_refptr<SingleThreadTaskRunner> CreateSingleThreadTaskRunner(
    const TaskTraits& traits,
    SingleThreadTaskRunnerThreadMode thread_mode =
        SingleThreadTaskRunnerThreadMode::SHARED);

#if defined(OS_WIN)
// Returns a SingleThreadTaskRunner whose PostTask invocations result in
// scheduling tasks using |traits| in a COM Single-Threaded Apartment on a
// thread determined by |thread_mode|. See
// base/task/single_thread_task_runner_thread_mode.h for |thread_mode| details.
// If |traits| identifies an existing thread,
// SingleThreadTaskRunnerThreadMode::SHARED must be used. Tasks run in the same
// Single-Threaded Apartment in posting order for the returned
// SingleThreadTaskRunner. There is not necessarily a one-to-one correspondence
// between SingleThreadTaskRunners and Single-Threaded Apartments. The
// implementation is free to share apartments or create new apartments as
// necessary. In either case, care should be taken to make sure COM pointers are
// not smuggled across apartments.
BASE_EXPORT scoped_refptr<SingleThreadTaskRunner> CreateCOMSTATaskRunner(
    const TaskTraits& traits,
    SingleThreadTaskRunnerThreadMode thread_mode =
        SingleThreadTaskRunnerThreadMode::SHARED);
#endif  // defined(OS_WIN)

// Helpers to send a Delete/ReleaseSoon to a new SequencedTaskRunner created
// from |traits|. The semantics match base::PostTask in that the deletion is
// guaranteed to be scheduled in order with other tasks using the same |traits|.
//
// Prefer using an existing SequencedTaskRunner's Delete/ReleaseSoon over this
// to encode execution order requirements when possible.
//
// Note: base::ThreadPool is not a valid destination as it'd result in a one-off
// parallel task which is generally ill-suited for deletion. Use an existing
// SequencedTaskRunner's DeleteSoon to post a safely ordered deletion.
template <class T>
bool DeleteSoon(const Location& from_here,
                const TaskTraits& traits,
                const T* object) {
  DCHECK(!traits.use_thread_pool());
  return CreateSequencedTaskRunner(traits)->DeleteSoon(from_here, object);
}
template <class T>
bool DeleteSoon(const Location& from_here,
                const TaskTraits& traits,
                std::unique_ptr<T> object) {
  return DeleteSoon(from_here, traits, object.release());
}
template <class T>
void ReleaseSoon(const Location& from_here,
                 const TaskTraits& traits,
                 scoped_refptr<T>&& object) {
  DCHECK(!traits.use_thread_pool());
  CreateSequencedTaskRunner(traits)->ReleaseSoon(from_here, std::move(object));
}

}  // namespace base

#endif  // BASE_TASK_POST_TASK_H_
