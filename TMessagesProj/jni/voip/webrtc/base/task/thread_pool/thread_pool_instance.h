// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_THREAD_POOL_THREAD_POOL_INSTANCE_H_
#define BASE_TASK_THREAD_POOL_THREAD_POOL_INSTANCE_H_

#include <memory>
#include <vector>

#include "base/base_export.h"
#include "base/callback.h"
#include "base/gtest_prod_util.h"
#include "base/memory/ref_counted.h"
#include "base/sequenced_task_runner.h"
#include "base/single_thread_task_runner.h"
#include "base/strings/string_piece.h"
#include "base/task/single_thread_task_runner_thread_mode.h"
#include "base/task/task_traits.h"
#include "base/task_runner.h"
#include "base/time/time.h"
#include "build/build_config.h"

namespace gin {
class V8Platform;
}

namespace content {
// Can't use the FRIEND_TEST_ALL_PREFIXES macro because the test is in a
// different namespace.
class BrowserMainLoopTest_CreateThreadsInSingleProcess_Test;
}  // namespace content

namespace base {

class WorkerThreadObserver;
class ThreadPoolTestHelpers;

// Interface for a thread pool and static methods to manage the instance used
// by the post_task.h API.
//
// The thread pool doesn't create threads until Start() is called. Tasks can
// be posted at any time but will not run until after Start() is called.
//
// The instance methods of this class are thread-safe.
//
// Note: All thread pool users should go through base/task/post_task.h instead
// of this interface except for the one callsite per process which manages the
// process's instance.
class BASE_EXPORT ThreadPoolInstance {
 public:
  struct BASE_EXPORT InitParams {
    enum class CommonThreadPoolEnvironment {
      // Use the default environment (no environment).
      DEFAULT,
#if defined(OS_WIN)
      // Place the pool's workers in a COM MTA.
      COM_MTA,
      // Place the pool's *foreground* workers in a COM STA. This exists to
      // mimic the behavior of SequencedWorkerPool and BrowserThreadImpl that
      // ThreadPool has replaced. Tasks that need a COM STA should use
      // CreateCOMSTATaskRunner() instead of Create(Sequenced)TaskRunner() +
      // this init param.
      DEPRECATED_COM_STA_IN_FOREGROUND_GROUP,
#endif  // defined(OS_WIN)
    };

    InitParams(int max_num_foreground_threads_in);
    ~InitParams();

    // Maximum number of unblocked tasks that can run concurrently in the
    // foreground thread group.
    int max_num_foreground_threads;

    // Whether COM is initialized when running sequenced and parallel tasks.
    CommonThreadPoolEnvironment common_thread_pool_environment =
        CommonThreadPoolEnvironment::DEFAULT;

    // An experiment conducted in July 2019 revealed that on Android, changing
    // the reclaim time from 30 seconds to 5 minutes:
    // - Reduces jank by 5% at 99th percentile
    // - Reduces first input delay by 5% at 99th percentile
    // - Reduces input delay by 3% at 50th percentile
    // - Reduces navigation to first contentful paint by 2-3% at 25-95th
    //   percentiles
    // On Windows and Mac, we instead see no impact or small regressions.
    //
    // TODO(scheduler-dev): Conduct experiments to find the optimal value for
    // each process type on each platform. In particular, due to regressions at
    // high percentiles for *HeartbeatLatencyMicroseconds.Renderer* histograms,
    // it was suggested that we might want a different reclaim time in
    // renderers. Note that the regression is not present in
    // *TaskLatencyMicroseconds.Renderer* histograms.
    TimeDelta suggested_reclaim_time =
#if defined(OS_ANDROID)
        TimeDelta::FromMinutes(5);
#else
        TimeDelta::FromSeconds(30);
#endif
  };

  // A Scoped(BestEffort)ExecutionFence prevents new tasks of any/BEST_EFFORT
  // priority from being scheduled in ThreadPoolInstance within its scope.
  // Multiple fences can exist at the same time. Upon destruction of all
  // Scoped(BestEffort)ExecutionFences, tasks that were preeempted are released.
  // Note: the constructor of Scoped(BestEffort)ExecutionFence will not wait for
  // currently running tasks (as they were posted before entering this scope and
  // do not violate the contract; some of them could be CONTINUE_ON_SHUTDOWN and
  // waiting for them to complete is ill-advised).
  class BASE_EXPORT ScopedExecutionFence {
   public:
    ScopedExecutionFence();
    ~ScopedExecutionFence();

   private:
    DISALLOW_COPY_AND_ASSIGN(ScopedExecutionFence);
  };

  class BASE_EXPORT ScopedBestEffortExecutionFence {
   public:
    ScopedBestEffortExecutionFence();
    ~ScopedBestEffortExecutionFence();

   private:
    DISALLOW_COPY_AND_ASSIGN(ScopedBestEffortExecutionFence);
  };

  // Destroying a ThreadPoolInstance is not allowed in production; it is always
  // leaked. In tests, it should only be destroyed after JoinForTesting() has
  // returned.
  virtual ~ThreadPoolInstance() = default;

  // Allows the thread pool to create threads and run tasks following the
  // |init_params| specification.
  //
  // If specified, |worker_thread_observer| will be notified when a worker
  // enters and exits its main function. It must not be destroyed before
  // JoinForTesting() has returned (must never be destroyed in production).
  //
  // CHECKs on failure.
  virtual void Start(
      const InitParams& init_params,
      WorkerThreadObserver* worker_thread_observer = nullptr) = 0;

  // Synchronously shuts down the thread pool. Once this is called, only tasks
  // posted with the BLOCK_SHUTDOWN behavior will be run. When this returns:
  // - All SKIP_ON_SHUTDOWN tasks that were already running have completed their
  //   execution.
  // - All posted BLOCK_SHUTDOWN tasks have completed their execution.
  // - CONTINUE_ON_SHUTDOWN tasks might still be running.
  // Note that an implementation can keep threads and other resources alive to
  // support running CONTINUE_ON_SHUTDOWN after this returns. This can only be
  // called once.
  virtual void Shutdown() = 0;

  // Waits until there are no pending undelayed tasks. May be called in tests
  // to validate that a condition is met after all undelayed tasks have run.
  //
  // Does not wait for delayed tasks. Waits for undelayed tasks posted from
  // other threads during the call. Returns immediately when shutdown completes.
  virtual void FlushForTesting() = 0;

  // Returns and calls |flush_callback| when there are no incomplete undelayed
  // tasks. |flush_callback| may be called back on any thread and should not
  // perform a lot of work. May be used when additional work on the current
  // thread needs to be performed during a flush. Only one
  // FlushAsyncForTesting() may be pending at any given time.
  virtual void FlushAsyncForTesting(OnceClosure flush_callback) = 0;

  // Joins all threads. Tasks that are already running are allowed to complete
  // their execution. This can only be called once. Using this thread pool
  // instance to create task runners or post tasks is not permitted during or
  // after this call.
  virtual void JoinForTesting() = 0;

  // CreateAndStartWithDefaultParams(), Create(), and SetInstance() register a
  // ThreadPoolInstance to handle tasks posted through the post_task.h API for
  // this process.
  //
  // Processes that need to initialize ThreadPoolInstance with custom params or
  // that need to allow tasks to be posted before the ThreadPoolInstance creates
  // its threads should use Create() followed by Start(). Other processes can
  // use CreateAndStartWithDefaultParams().
  //
  // A registered ThreadPoolInstance is only deleted when a new
  // ThreadPoolInstance is registered. The last registered ThreadPoolInstance is
  // leaked on shutdown. The methods below must not be called when TaskRunners
  // created by a previous ThreadPoolInstance are still alive. The methods are
  // not thread-safe; proper synchronization is required to use the post_task.h
  // API after registering a new ThreadPoolInstance.

#if !defined(OS_NACL)
  // Creates and starts a thread pool using default params. |name| is used to
  // label histograms, it must not be empty. It should identify the component
  // that calls this. Start() is called by this method; it is invalid to call it
  // again afterwards. CHECKs on failure. For tests, prefer
  // base::test::TaskEnvironment (ensures isolation).
  static void CreateAndStartWithDefaultParams(StringPiece name);

  // Same as CreateAndStartWithDefaultParams() but allows callers to split the
  // Create() and StartWithDefaultParams() calls.
  void StartWithDefaultParams();
#endif  // !defined(OS_NACL)

  // Creates a ready to start thread pool. |name| is used to label histograms,
  // it must not be empty. It should identify the component that creates the
  // ThreadPoolInstance. The thread pool doesn't create threads until Start() is
  // called. Tasks can be posted at any time but will not run until after
  // Start() is called. For tests, prefer base::test::TaskEnvironment
  // (ensures isolation).
  static void Create(StringPiece name);

  // Registers |thread_pool| to handle tasks posted through the post_task.h
  // API for this process. For tests, prefer base::test::TaskEnvironment
  // (ensures isolation).
  static void Set(std::unique_ptr<ThreadPoolInstance> thread_pool);

  // Retrieve the ThreadPoolInstance set via SetInstance() or Create(). This
  // should be used very rarely; most users of the thread pool should use the
  // post_task.h API. In particular, refrain from doing
  //   if (!ThreadPoolInstance::Get()) {
  //     ThreadPoolInstance::Set(...);
  //     base::PostTask(...);
  //   }
  // instead make sure to SetInstance() early in one determinstic place in the
  // process' initialization phase.
  // In doubt, consult with //base/task/thread_pool/OWNERS.
  static ThreadPoolInstance* Get();

 private:
  friend class ThreadPoolTestHelpers;
  friend class gin::V8Platform;
  friend class content::BrowserMainLoopTest_CreateThreadsInSingleProcess_Test;

  // Returns the maximum number of non-single-threaded non-blocked tasks posted
  // with |traits| that can run concurrently in this thread pool. |traits|
  // can't contain TaskPriority::BEST_EFFORT.
  //
  // Do not use this method. To process n items, post n tasks that each process
  // 1 item rather than GetMaxConcurrentNonBlockedTasksWithTraitsDeprecated()
  // tasks that each process
  // n/GetMaxConcurrentNonBlockedTasksWithTraitsDeprecated() items.
  //
  // TODO(fdoray): Remove this method. https://crbug.com/687264
  virtual int GetMaxConcurrentNonBlockedTasksWithTraitsDeprecated(
      const TaskTraits& traits) const = 0;

  // Starts/stops a fence that prevents execution of tasks of any / BEST_EFFORT
  // priority.
  virtual void BeginFence() = 0;
  virtual void EndFence() = 0;
  virtual void BeginBestEffortFence() = 0;
  virtual void EndBestEffortFence() = 0;
};

}  // namespace base

#endif  // BASE_TASK_THREAD_POOL_THREAD_POOL_INSTANCE_H_
