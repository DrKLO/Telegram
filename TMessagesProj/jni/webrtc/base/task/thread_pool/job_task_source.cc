// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/thread_pool/job_task_source.h"

#include <utility>

#include "base/bind.h"
#include "base/bind_helpers.h"
#include "base/logging.h"
#include "base/memory/ptr_util.h"
#include "base/task/task_features.h"
#include "base/task/thread_pool/pooled_task_runner_delegate.h"
#include "base/threading/thread_restrictions.h"
#include "base/time/time.h"
#include "base/time/time_override.h"

namespace base {
namespace internal {

// Memory ordering on |state_| operations
//
// The write operation on |state_| in WillRunTask() uses
// std::memory_order_release, matched by std::memory_order_acquire on read
// operations (in DidProcessTask()) to establish a
// Release-Acquire ordering. When a call to WillRunTask() is caused by an
// increase of max concurrency followed by an associated
// NotifyConcurrencyIncrease(), the priority queue lock guarantees an
// happens-after relation with NotifyConcurrencyIncrease(). This ensures that an
// increase of max concurrency that happened-before NotifyConcurrencyIncrease()
// is visible to a read operation that happens-after WillRunTask().
//
// In DidProcessTask(), this is necessary to
// ensure that the task source is always re-enqueued when it needs to. When the
// task source needs to be queued, either because the current task yielded or
// because of NotifyConcurrencyIncrease(), one of the following is true:
//   A) DidProcessTask() happens-after WillRunTask():
//    T1: Current task returns (because it is done) or yields.
//    T2: Increases the value returned by GetMaxConcurrency()
//        NotifyConcurrencyIncrease() enqueues the task source
//    T3: WillRunTask(), in response to the concurrency increase - Release
//        Does not keep the TaskSource in PriorityQueue because it is at max
//        concurrency
//    T1: DidProcessTask() - Acquire - Because of memory barrier, sees the same
//        (or newer) max concurrency as T2
//        Re-enqueues the TaskSource because no longer at max concurrency
//      Without the memory barrier, T1 may see an outdated max concurrency that
//      is lower than the actual max concurrency and won't re-enqueue the
//      task source, because it thinks it's already saturated.
//      The task source often needs to be re-enqueued if its task
//      completed because it yielded and |max_concurrency| wasn't decreased.
//   B) DidProcessTask() happens-before WillRunTask():
//    T1: Current task returns (because it is done) or yields
//    T2: Increases the value returned by GetMaxConcurrency()
//        NotifyConcurrencyIncrease() enqueues the task source
//    T1: DidProcessTask() - Acquire (ineffective)
//      Since the task source is already in the queue, it doesn't matter
//      whether T1 re-enqueues the task source or not.
// Note that stale values the other way around can cause incorrectly
// re-enqueuing this task_source, which is not an issue because the queues
// support empty task sources.

JobTaskSource::State::State() = default;
JobTaskSource::State::~State() = default;

JobTaskSource::State::Value JobTaskSource::State::Cancel() {
  return {value_.fetch_or(kCanceledMask, std::memory_order_relaxed)};
}

JobTaskSource::State::Value
JobTaskSource::State::TryIncrementWorkerCountFromWorkerRelease(
    size_t max_concurrency) {
  uint32_t value_before_add = value_.load(std::memory_order_relaxed);

  // std::memory_order_release on success to establish Release-Acquire ordering
  // with DecrementWorkerCountAcquire()  (see Memory Ordering comment at top of
  // the file).
  while (!(value_before_add & kCanceledMask) &&
         (value_before_add >> kWorkerCountBitOffset) < max_concurrency &&
         !value_.compare_exchange_weak(
             value_before_add, value_before_add + kWorkerCountIncrement,
             std::memory_order_release, std::memory_order_relaxed)) {
  }
  return {value_before_add};
}

JobTaskSource::State::Value
JobTaskSource::State::DecrementWorkerCountFromWorkerAcquire() {
  const size_t value_before_sub =
      value_.fetch_sub(kWorkerCountIncrement, std::memory_order_acquire);
  DCHECK((value_before_sub >> kWorkerCountBitOffset) > 0);
  return {value_before_sub};
}

JobTaskSource::State::Value
JobTaskSource::State::IncrementWorkerCountFromJoiningThread() {
  size_t value_before_add =
      value_.fetch_add(kWorkerCountIncrement, std::memory_order_relaxed);
  return {value_before_add};
}

JobTaskSource::State::Value
JobTaskSource::State::DecrementWorkerCountFromJoiningThread() {
  const size_t value_before_sub =
      value_.fetch_sub(kWorkerCountIncrement, std::memory_order_relaxed);
  DCHECK((value_before_sub >> kWorkerCountBitOffset) > 0);
  return {value_before_sub};
}

JobTaskSource::State::Value JobTaskSource::State::Load() const {
  return {value_.load(std::memory_order_relaxed)};
}

JobTaskSource::JoinFlag::JoinFlag() = default;
JobTaskSource::JoinFlag::~JoinFlag() = default;

void JobTaskSource::JoinFlag::SetWaiting() {
  const auto previous_value =
      value_.exchange(kWaitingForWorkerToYield, std::memory_order_relaxed);
  DCHECK(previous_value == kNotWaiting);
}

bool JobTaskSource::JoinFlag::ShouldWorkerYield() {
  // The fetch_and() sets the state to kWaitingForWorkerToSignal if it was
  // previously kWaitingForWorkerToYield, otherwise it leaves it unchanged.
  return value_.fetch_and(kWaitingForWorkerToSignal,
                          std::memory_order_relaxed) ==
         kWaitingForWorkerToYield;
}

bool JobTaskSource::JoinFlag::ShouldWorkerSignal() {
  return value_.exchange(kNotWaiting, std::memory_order_relaxed) != kNotWaiting;
}

JobTaskSource::JobTaskSource(
    const Location& from_here,
    const TaskTraits& traits,
    RepeatingCallback<void(JobDelegate*)> worker_task,
    RepeatingCallback<size_t()> max_concurrency_callback,
    PooledTaskRunnerDelegate* delegate)
    : TaskSource(traits, nullptr, TaskSourceExecutionMode::kJob),
      from_here_(from_here),
      max_concurrency_callback_(std::move(max_concurrency_callback)),
      worker_task_(std::move(worker_task)),
      primary_task_(base::BindRepeating(
          [](JobTaskSource* self) {
            // Each worker task has its own delegate with associated state.
            JobDelegate job_delegate{self, self->delegate_};
            self->worker_task_.Run(&job_delegate);
          },
          base::Unretained(this))),
      queue_time_(TimeTicks::Now()),
      delegate_(delegate) {
  DCHECK(delegate_);
}

JobTaskSource::~JobTaskSource() {
  // Make sure there's no outstanding active run operation left.
  DCHECK_EQ(state_.Load().worker_count(), 0U);
}

ExecutionEnvironment JobTaskSource::GetExecutionEnvironment() {
  return {SequenceToken::Create(), nullptr};
}

bool JobTaskSource::WillJoin() {
  {
    CheckedAutoLock auto_lock(lock_);
    DCHECK(!worker_released_condition_);  // This may only be called once.
    worker_released_condition_ = lock_.CreateConditionVariable();
  }
  // std::memory_order_relaxed on |worker_count_| is sufficient because call to
  // GetMaxConcurrency() is used for a best effort early exit. Stale values will
  // only cause WaitForParticipationOpportunity() to be called.
  const auto state_before_add = state_.IncrementWorkerCountFromJoiningThread();

  if (!state_before_add.is_canceled() &&
      state_before_add.worker_count() < GetMaxConcurrency()) {
    return true;
  }
  return WaitForParticipationOpportunity();
}

bool JobTaskSource::RunJoinTask() {
  JobDelegate job_delegate{this, nullptr};
  worker_task_.Run(&job_delegate);

  // std::memory_order_relaxed on |worker_count_| is sufficient because the call
  // to GetMaxConcurrency() is used for a best effort early exit. Stale values
  // will only cause WaitForParticipationOpportunity() to be called.
  const auto state = state_.Load();
  if (!state.is_canceled() && state.worker_count() <= GetMaxConcurrency())
    return true;

  return WaitForParticipationOpportunity();
}

void JobTaskSource::Cancel(TaskSource::Transaction* transaction) {
  // Sets the kCanceledMask bit on |state_| so that further calls to
  // WillRunTask() never succeed. std::memory_order_relaxed is sufficient
  // because this task source never needs to be re-enqueued after Cancel().
  state_.Cancel();

#if DCHECK_IS_ON()
  {
    AutoLock auto_lock(version_lock_);
    ++increase_version_;
    version_condition_.Broadcast();
  }
#endif  // DCHECK_IS_ON()
}

bool JobTaskSource::WaitForParticipationOpportunity() {
  CheckedAutoLock auto_lock(lock_);

  // std::memory_order_relaxed is sufficient because no other state is
  // synchronized with |state_| outside of |lock_|.
  auto state = state_.Load();
  size_t max_concurrency = GetMaxConcurrency();

  // Wait until either:
  //  A) |worker_count| is below or equal to max concurrency and state is not
  //  canceled.
  //  B) All other workers returned and |worker_count| is 1.
  while (!((state.worker_count() <= max_concurrency && !state.is_canceled()) ||
           state.worker_count() == 1)) {
    // std::memory_order_relaxed is sufficient because no other state is
    // synchronized with |join_flag_| outside of |lock_|.
    join_flag_.SetWaiting();

    // To avoid unnecessarily waiting, if either condition A) or B) change
    // |lock_| is taken and |worker_released_condition_| signaled if necessary:
    // 1- In DidProcessTask(), after worker count is decremented.
    // 2- In NotifyConcurrencyIncrease(), following a max_concurrency increase.
    worker_released_condition_->Wait();
    state = state_.Load();
    max_concurrency = GetMaxConcurrency();
  }
  // Case A:
  if (state.worker_count() <= max_concurrency && !state.is_canceled())
    return true;
  // Case B:
  // Only the joining thread remains.
  DCHECK_EQ(state.worker_count(), 1U);
  DCHECK(state.is_canceled() || max_concurrency == 0U);
  state_.DecrementWorkerCountFromJoiningThread();
  return false;
}

TaskSource::RunStatus JobTaskSource::WillRunTask() {
  const size_t max_concurrency = GetMaxConcurrency();
  // std::memory_order_release on success to establish Release-Acquire ordering
  // with read operations (see Memory Ordering comment at top of the file).
  const auto state_before_add =
      state_.TryIncrementWorkerCountFromWorkerRelease(max_concurrency);

  // Don't allow this worker to run the task if either:
  //   A) |state_| was canceled.
  //   B) |worker_count| is already at |max_concurrency|.
  //   C) |max_concurrency| was lowered below or to |worker_count|.
  // Case A:
  if (state_before_add.is_canceled())
    return RunStatus::kDisallowed;
  const size_t worker_count_before_add = state_before_add.worker_count();
  // Case B) or C):
  if (worker_count_before_add >= max_concurrency)
    return RunStatus::kDisallowed;

  DCHECK_LT(worker_count_before_add, max_concurrency);
  return max_concurrency == worker_count_before_add + 1
             ? RunStatus::kAllowedSaturated
             : RunStatus::kAllowedNotSaturated;
}

size_t JobTaskSource::GetRemainingConcurrency() const {
  // std::memory_order_relaxed is sufficient because no other state is
  // synchronized with GetRemainingConcurrency().
  const auto state = state_.Load();
  const size_t max_concurrency = GetMaxConcurrency();
  // Avoid underflows.
  if (state.is_canceled() || state.worker_count() > max_concurrency)
    return 0;
  return max_concurrency - state.worker_count();
}

void JobTaskSource::NotifyConcurrencyIncrease() {
#if DCHECK_IS_ON()
  {
    AutoLock auto_lock(version_lock_);
    ++increase_version_;
    version_condition_.Broadcast();
  }
#endif  // DCHECK_IS_ON()

  // Avoid unnecessary locks when NotifyConcurrencyIncrease() is spuriously
  // called.
  if (GetRemainingConcurrency() == 0)
    return;

  {
    // Lock is taken to access |join_flag_| below and signal
    // |worker_released_condition_|.
    CheckedAutoLock auto_lock(lock_);
    if (join_flag_.ShouldWorkerSignal())
      worker_released_condition_->Signal();
  }

  // Make sure the task source is in the queue if not already.
  // Caveat: it's possible but unlikely that the task source has already reached
  // its intended concurrency and doesn't need to be enqueued if there
  // previously were too many worker. For simplicity, the task source is always
  // enqueued and will get discarded if already saturated when it is popped from
  // the priority queue.
  delegate_->EnqueueJobTaskSource(this);
}

size_t JobTaskSource::GetMaxConcurrency() const {
  return max_concurrency_callback_.Run();
}

bool JobTaskSource::ShouldYield() {
  // It is safe to read |join_flag_| without a lock since this
  // variable is atomic, keeping in mind that threads may not immediately see
  // the new value when it is updated.
  return TS_UNCHECKED_READ(join_flag_).ShouldWorkerYield() ||
         state_.Load().is_canceled();
}

#if DCHECK_IS_ON()

size_t JobTaskSource::GetConcurrencyIncreaseVersion() const {
  AutoLock auto_lock(version_lock_);
  return increase_version_;
}

bool JobTaskSource::WaitForConcurrencyIncreaseUpdate(size_t recorded_version) {
  AutoLock auto_lock(version_lock_);
  constexpr TimeDelta timeout = TimeDelta::FromSeconds(1);
  const base::TimeTicks start_time = subtle::TimeTicksNowIgnoringOverride();
  do {
    DCHECK_LE(recorded_version, increase_version_);
    const auto state = state_.Load();
    if (recorded_version != increase_version_ || state.is_canceled())
      return true;
    // Waiting is acceptable because it is in DCHECK-only code.
    ScopedAllowBaseSyncPrimitivesOutsideBlockingScope
        allow_base_sync_primitives;
    version_condition_.TimedWait(timeout);
  } while (subtle::TimeTicksNowIgnoringOverride() - start_time < timeout);
  return false;
}

#endif  // DCHECK_IS_ON()

Task JobTaskSource::TakeTask(TaskSource::Transaction* transaction) {
  // JobTaskSource members are not lock-protected so no need to acquire a lock
  // if |transaction| is nullptr.
  DCHECK_GT(state_.Load().worker_count(), 0U);
  DCHECK(primary_task_);
  return Task(from_here_, primary_task_, TimeDelta());
}

bool JobTaskSource::DidProcessTask(TaskSource::Transaction* transaction) {
  // Lock is needed to access |join_flag_| below and signal
  // |worker_released_condition_|. If |transaction|, then |lock_| is already
  // taken.
  CheckedAutoLockMaybe auto_lock(transaction ? nullptr : &lock_);
  AnnotateAcquiredLockAlias annotate(lock_, lock_);

  // std::memory_order_acquire to establish Release-Acquire ordering with
  // WillRunTask() (see Memory Ordering comment at top of the file).
  const auto state_before_sub = state_.DecrementWorkerCountFromWorkerAcquire();

  if (join_flag_.ShouldWorkerSignal())
    worker_released_condition_->Signal();

  // A canceled task source should never get re-enqueued.
  if (state_before_sub.is_canceled())
    return false;

  DCHECK_GT(state_before_sub.worker_count(), 0U);

  // Re-enqueue the TaskSource if the task ran and the worker count is below the
  // max concurrency.
  return state_before_sub.worker_count() <= GetMaxConcurrency();
}

SequenceSortKey JobTaskSource::GetSortKey() const {
  return SequenceSortKey(traits_.priority(), queue_time_);
}

Task JobTaskSource::Clear(TaskSource::Transaction* transaction) {
  Cancel();
  // Nothing is cleared since other workers might still racily run tasks. For
  // simplicity, the destructor will take care of it once all references are
  // released.
  return Task(from_here_, DoNothing(), TimeDelta());
}

}  // namespace internal
}  // namespace base
