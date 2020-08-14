// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_THREAD_POOL_TASK_SOURCE_H_
#define BASE_TASK_THREAD_POOL_TASK_SOURCE_H_

#include <stddef.h>

#include "base/base_export.h"
#include "base/compiler_specific.h"
#include "base/macros.h"
#include "base/memory/ref_counted.h"
#include "base/optional.h"
#include "base/sequence_token.h"
#include "base/task/common/checked_lock.h"
#include "base/task/common/intrusive_heap.h"
#include "base/task/task_traits.h"
#include "base/task/thread_pool/sequence_sort_key.h"
#include "base/task/thread_pool/task.h"
#include "base/threading/sequence_local_storage_map.h"

namespace base {
namespace internal {

class TaskTracker;

enum class TaskSourceExecutionMode {
  kParallel,
  kSequenced,
  kSingleThread,
  kJob,
  kMax = kJob,
};

struct BASE_EXPORT ExecutionEnvironment {
  SequenceToken token;
  SequenceLocalStorageMap* sequence_local_storage;
};

// A TaskSource is a virtual class that provides a series of Tasks that must be
// executed.
//
// A task source is registered when it's ready to be queued. A task source is
// ready to be queued when either:
// 1- It has new tasks that can run concurrently as a result of external
//    operations, e.g. posting a new task to an empty Sequence or increasing
//    max concurrency of a JobTaskSource;
// 2- A worker finished running a task from it and DidProcessTask() returned
//    true; or
// 3- A worker is about to run a task from it and WillRunTask() returned
//    kAllowedNotSaturated.
//
// A worker may perform the following sequence of operations on a
// RegisteredTaskSource after obtaining it from the queue:
// 1- Check whether a task can run with WillRunTask() (and register/enqueue the
//    task source again if not saturated).
// 2- (optional) Iff (1) determined that a task can run, access the next task
//    with TakeTask().
// 3- (optional) Execute the task.
// 4- Inform the task source that a task was processed with DidProcessTask(),
//    and re-enqueue the task source iff requested.
// When a task source is registered multiple times, many overlapping chains of
// operations may run concurrently, as permitted by WillRunTask(). This allows
// tasks from the same task source to run in parallel.
// However, the following invariants are kept:
// - The number of workers concurrently running tasks never goes over the
//   intended concurrency.
// - If the task source has more tasks that can run concurrently, it must be
//   queued.
//
// Note: there is a known refcounted-ownership cycle in the ThreadPool
// architecture: TaskSource -> TaskRunner -> TaskSource -> ... This is okay so
// long as the other owners of TaskSource (PriorityQueue and WorkerThread in
// alternation and ThreadGroupImpl::WorkerThreadDelegateImpl::GetWork()
// temporarily) keep running it (and taking Tasks from it as a result). A
// dangling reference cycle would only occur should they release their reference
// to it while it's not empty. In other words, it is only correct for them to
// release it when DidProcessTask() returns false.
//
// This class is thread-safe.
class BASE_EXPORT TaskSource : public RefCountedThreadSafe<TaskSource> {
 public:
  // Indicates whether WillRunTask() allows TakeTask() to be called on a
  // RegisteredTaskSource.
  enum class RunStatus {
    // TakeTask() cannot be called.
    kDisallowed,
    // TakeTask() may called, and the TaskSource has not reached its maximum
    // concurrency (i.e. the TaskSource still needs to be queued).
    kAllowedNotSaturated,
    // TakeTask() may called, and the TaskSource has reached its maximum
    // concurrency (i.e. the TaskSource no longer needs to be queued).
    kAllowedSaturated,
  };

  // A Transaction can perform multiple operations atomically on a
  // TaskSource. While a Transaction is alive, it is guaranteed that nothing
  // else will access the TaskSource; the TaskSource's lock is held for the
  // lifetime of the Transaction.
  class BASE_EXPORT Transaction {
   public:
    Transaction(Transaction&& other);
    ~Transaction();

    operator bool() const { return !!task_source_; }

    // Returns a SequenceSortKey representing the priority of the TaskSource.
    // Cannot be called on an empty TaskSource.
    SequenceSortKey GetSortKey() const;

    // Sets TaskSource priority to |priority|.
    void UpdatePriority(TaskPriority priority);

    // Returns the traits of all Tasks in the TaskSource.
    TaskTraits traits() const { return task_source_->traits_; }

    TaskSource* task_source() const { return task_source_; }

   protected:
    explicit Transaction(TaskSource* task_source);

   private:
    friend class TaskSource;

    TaskSource* task_source_;

    DISALLOW_COPY_AND_ASSIGN(Transaction);
  };

  // |traits| is metadata that applies to all Tasks in the TaskSource.
  // |task_runner| is a reference to the TaskRunner feeding this TaskSource.
  // |task_runner| can be nullptr only for tasks with no TaskRunner, in which
  // case |execution_mode| must be kParallel. Otherwise, |execution_mode| is the
  // execution mode of |task_runner|.
  TaskSource(const TaskTraits& traits,
             TaskRunner* task_runner,
             TaskSourceExecutionMode execution_mode);

  // Begins a Transaction. This method cannot be called on a thread which has an
  // active TaskSource::Transaction.
  Transaction BeginTransaction() WARN_UNUSED_RESULT;

  virtual ExecutionEnvironment GetExecutionEnvironment() = 0;

  // Thread-safe but the returned value may immediately be obsolete. As such
  // this should only be used as a best-effort guess of how many more workers
  // are needed. This may be called on an empty task source.
  virtual size_t GetRemainingConcurrency() const = 0;

  // Support for IntrusiveHeap.
  void SetHeapHandle(const HeapHandle& handle);
  void ClearHeapHandle();
  HeapHandle GetHeapHandle() const { return heap_handle_; }

  HeapHandle heap_handle() const { return heap_handle_; }

  // Returns the shutdown behavior of all Tasks in the TaskSource. Can be
  // accessed without a Transaction because it is never mutated.
  TaskShutdownBehavior shutdown_behavior() const {
    return traits_.shutdown_behavior();
  }
  // Returns a racy priority of the TaskSource. Can be accessed without a
  // Transaction but may return an outdated result.
  TaskPriority priority_racy() const {
    return priority_racy_.load(std::memory_order_relaxed);
  }
  // Returns the thread policy of the TaskSource. Can be accessed without a
  // Transaction because it is never mutated.
  ThreadPolicy thread_policy() const { return traits_.thread_policy(); }

  // A reference to TaskRunner is only retained between PushTask() and when
  // DidProcessTask() returns false, guaranteeing it is safe to dereference this
  // pointer. Otherwise, the caller should guarantee such TaskRunner still
  // exists before dereferencing.
  TaskRunner* task_runner() const { return task_runner_; }

  TaskSourceExecutionMode execution_mode() const { return execution_mode_; }

 protected:
  virtual ~TaskSource();

  virtual RunStatus WillRunTask() = 0;

  // Implementations of TakeTask(), DidProcessTask() and Clear() must ensure
  // proper synchronization iff |transaction| is nullptr.
  virtual Task TakeTask(TaskSource::Transaction* transaction) = 0;
  virtual bool DidProcessTask(TaskSource::Transaction* transaction) = 0;

  // This may be called for each outstanding RegisteredTaskSource that's ready.
  // The implementation needs to support this being called multiple times;
  // unless it guarantees never to hand-out multiple RegisteredTaskSources that
  // are concurrently ready.
  virtual Task Clear(TaskSource::Transaction* transaction) = 0;

  virtual SequenceSortKey GetSortKey() const = 0;

  // Sets TaskSource priority to |priority|.
  void UpdatePriority(TaskPriority priority);

  // The TaskTraits of all Tasks in the TaskSource.
  TaskTraits traits_;

  // The cached priority for atomic access.
  std::atomic<TaskPriority> priority_racy_;

  // Synchronizes access to all members.
  mutable CheckedLock lock_{UniversalPredecessor()};

 private:
  friend class RefCountedThreadSafe<TaskSource>;
  friend class RegisteredTaskSource;

  // The TaskSource's position in its current PriorityQueue. Access is protected
  // by the PriorityQueue's lock.
  HeapHandle heap_handle_;

  // A pointer to the TaskRunner that posts to this TaskSource, if any. The
  // derived class is responsible for calling AddRef() when a TaskSource from
  // which no Task is executing becomes non-empty and Release() when
  // it becomes empty again (e.g. when DidProcessTask() returns false).
  TaskRunner* task_runner_;

  TaskSourceExecutionMode execution_mode_;

  DISALLOW_COPY_AND_ASSIGN(TaskSource);
};

// Wrapper around TaskSource to signify the intent to queue and run it.
// RegisteredTaskSource can only be created with TaskTracker and may only be
// used by a single worker at a time. However, the same task source may be
// registered several times, spawning multiple RegisteredTaskSources. A
// RegisteredTaskSource resets to its initial state when WillRunTask() fails
// or after DidProcessTask(), so it can be used again.
class BASE_EXPORT RegisteredTaskSource {
 public:
  RegisteredTaskSource();
  RegisteredTaskSource(std::nullptr_t);
  RegisteredTaskSource(RegisteredTaskSource&& other) noexcept;
  ~RegisteredTaskSource();

  RegisteredTaskSource& operator=(RegisteredTaskSource&& other);

  operator bool() const { return task_source_ != nullptr; }
  TaskSource* operator->() const { return task_source_.get(); }
  TaskSource* get() const { return task_source_.get(); }

  static RegisteredTaskSource CreateForTesting(
      scoped_refptr<TaskSource> task_source,
      TaskTracker* task_tracker = nullptr);

  // Can only be called if this RegisteredTaskSource is in its initial state.
  // Returns the underlying task source. An Optional is used in preparation for
  // the merge between ThreadPool and TaskQueueManager (in Blink).
  // https://crbug.com/783309
  scoped_refptr<TaskSource> Unregister();

  // Informs this TaskSource that the current worker would like to run a Task
  // from it. Can only be called if in its initial state. Returns a RunStatus
  // that indicates if the operation is allowed (TakeTask() can be called).
  TaskSource::RunStatus WillRunTask();

  // Returns the next task to run from this TaskSource. This should be called
  // only after WillRunTask() returned RunStatus::kAllowed*. |transaction| is
  // optional and should only be provided if this operation is already part of
  // a transaction.
  Task TakeTask(TaskSource::Transaction* transaction = nullptr)
      WARN_UNUSED_RESULT;

  // Must be called after WillRunTask() or once the task was run if TakeTask()
  // was called. This resets this RegisteredTaskSource to its initial state so
  // that WillRunTask() may be called again. |transaction| is optional and
  // should only be provided if this operation is already part of a transaction.
  // Returns true if the TaskSource should be queued after this operation.
  bool DidProcessTask(TaskSource::Transaction* transaction = nullptr);

  // Returns a task that clears this TaskSource to make it empty. |transaction|
  // is optional and should only be provided if this operation is already part
  // of a transaction.
  Task Clear(TaskSource::Transaction* transaction = nullptr) WARN_UNUSED_RESULT;

 private:
  friend class TaskTracker;
  RegisteredTaskSource(scoped_refptr<TaskSource> task_source,
                       TaskTracker* task_tracker);

#if DCHECK_IS_ON()
  // Indicates the step of a task execution chain.
  enum class State {
    kInitial,       // WillRunTask() may be called.
    kReady,         // After WillRunTask() returned a valid RunStatus.
  };

  State run_step_ = State::kInitial;
#endif  // DCHECK_IS_ON()

  scoped_refptr<TaskSource> task_source_;
  TaskTracker* task_tracker_ = nullptr;

  DISALLOW_COPY_AND_ASSIGN(RegisteredTaskSource);
};

// A pair of Transaction and RegisteredTaskSource. Useful to carry a
// RegisteredTaskSource with an associated Transaction.
// TODO(crbug.com/839091): Rename to RegisteredTaskSourceAndTransaction.
struct BASE_EXPORT TransactionWithRegisteredTaskSource {
 public:
  TransactionWithRegisteredTaskSource(RegisteredTaskSource task_source_in,
                                      TaskSource::Transaction transaction_in);

  TransactionWithRegisteredTaskSource(
      TransactionWithRegisteredTaskSource&& other) = default;
  ~TransactionWithRegisteredTaskSource() = default;

  static TransactionWithRegisteredTaskSource FromTaskSource(
      RegisteredTaskSource task_source_in);

  RegisteredTaskSource task_source;
  TaskSource::Transaction transaction;

  DISALLOW_COPY_AND_ASSIGN(TransactionWithRegisteredTaskSource);
};

}  // namespace internal
}  // namespace base

#endif  // BASE_TASK_THREAD_POOL_TASK_SOURCE_H_
