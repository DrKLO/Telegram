// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_SEQUENCE_MANAGER_TASK_QUEUE_H_
#define BASE_TASK_SEQUENCE_MANAGER_TASK_QUEUE_H_

#include <memory>

#include "base/macros.h"
#include "base/memory/weak_ptr.h"
#include "base/optional.h"
#include "base/single_thread_task_runner.h"
#include "base/task/common/checked_lock.h"
#include "base/task/sequence_manager/lazy_now.h"
#include "base/task/sequence_manager/tasks.h"
#include "base/task/task_observer.h"
#include "base/threading/platform_thread.h"
#include "base/time/time.h"

namespace base {

class TaskObserver;

namespace trace_event {
class BlameContext;
}

namespace sequence_manager {

namespace internal {
class AssociatedThreadId;
class SequenceManagerImpl;
class TaskQueueImpl;
}  // namespace internal

class TimeDomain;

// TODO(kraynov): Make TaskQueue to actually be an interface for TaskQueueImpl
// and stop using ref-counting because we're no longer tied to task runner
// lifecycle and there's no other need for ref-counting either.
// NOTE: When TaskQueue gets automatically deleted on zero ref-count,
// TaskQueueImpl gets gracefully shutdown. It means that it doesn't get
// unregistered immediately and might accept some last minute tasks until
// SequenceManager will unregister it at some point. It's done to ensure that
// task queue always gets unregistered on the main thread.
class BASE_EXPORT TaskQueue : public RefCountedThreadSafe<TaskQueue> {
 public:
  class Observer {
   public:
    virtual ~Observer() = default;

    // Notify observer that the time at which this queue wants to run
    // the next task has changed. |next_wakeup| can be in the past
    // (e.g. TimeTicks() can be used to notify about immediate work).
    // Can be called on any thread
    // All methods but SetObserver, SetTimeDomain and GetTimeDomain can be
    // called on |queue|.
    //
    // TODO(altimin): Make it Optional<TimeTicks> to tell
    // observer about cancellations.
    virtual void OnQueueNextWakeUpChanged(TimeTicks next_wake_up) = 0;
  };

  // Shuts down the queue. All tasks currently queued will be discarded.
  virtual void ShutdownTaskQueue();

  // Shuts down the queue when there are no more tasks queued.
  void ShutdownTaskQueueGracefully();

  // TODO(scheduler-dev): Could we define a more clear list of priorities?
  // See https://crbug.com/847858.
  enum QueuePriority : uint8_t {
    // Queues with control priority will run before any other queue, and will
    // explicitly starve other queues. Typically this should only be used for
    // private queues which perform control operations.
    kControlPriority = 0,

    // The selector will prioritize highest over high, normal and low; and
    // high over normal and low; and normal over low. However it will ensure
    // neither of the lower priority queues can be completely starved by higher
    // priority tasks. All three of these queues will always take priority over
    // and can starve the best effort queue.
    kHighestPriority = 1,

    kVeryHighPriority = 2,

    kHighPriority = 3,

    // Queues with normal priority are the default.
    kNormalPriority = 4,
    kLowPriority = 5,

    // Queues with best effort priority will only be run if all other queues are
    // empty. They can be starved by the other queues.
    kBestEffortPriority = 6,
    // Must be the last entry.
    kQueuePriorityCount = 7,
    kFirstQueuePriority = kControlPriority,
  };

  // Can be called on any thread.
  static const char* PriorityToString(QueuePriority priority);

  // Options for constructing a TaskQueue.
  struct Spec {
    explicit Spec(const char* name) : name(name) {}

    Spec SetShouldMonitorQuiescence(bool should_monitor) {
      should_monitor_quiescence = should_monitor;
      return *this;
    }

    Spec SetShouldNotifyObservers(bool run_observers) {
      should_notify_observers = run_observers;
      return *this;
    }

    // Delayed fences require Now() to be sampled when posting immediate tasks
    // which is not free.
    Spec SetDelayedFencesAllowed(bool allow_delayed_fences) {
      delayed_fence_allowed = allow_delayed_fences;
      return *this;
    }

    Spec SetTimeDomain(TimeDomain* domain) {
      time_domain = domain;
      return *this;
    }

    const char* name;
    bool should_monitor_quiescence = false;
    TimeDomain* time_domain = nullptr;
    bool should_notify_observers = true;
    bool delayed_fence_allowed = false;
  };

  // TODO(altimin): Make this private after TaskQueue/TaskQueueImpl refactoring.
  TaskQueue(std::unique_ptr<internal::TaskQueueImpl> impl,
            const TaskQueue::Spec& spec);

  // Information about task execution.
  //
  // Wall-time related methods (start_time, end_time, wall_duration) can be
  // called only when |has_wall_time()| is true.
  // Thread-time related mehtods (start_thread_time, end_thread_time,
  // thread_duration) can be called only when |has_thread_time()| is true.
  //
  // start_* should be called after RecordTaskStart.
  // end_* and *_duration should be called after RecordTaskEnd.
  class BASE_EXPORT TaskTiming {
   public:
    enum class State { NotStarted, Running, Finished };
    enum class TimeRecordingPolicy { DoRecord, DoNotRecord };

    TaskTiming(bool has_wall_time, bool has_thread_time);

    bool has_wall_time() const { return has_wall_time_; }
    bool has_thread_time() const { return has_thread_time_; }

    base::TimeTicks start_time() const {
      DCHECK(has_wall_time());
      return start_time_;
    }
    base::TimeTicks end_time() const {
      DCHECK(has_wall_time());
      return end_time_;
    }
    base::TimeDelta wall_duration() const {
      DCHECK(has_wall_time());
      return end_time_ - start_time_;
    }
    base::ThreadTicks start_thread_time() const {
      DCHECK(has_thread_time());
      return start_thread_time_;
    }
    base::ThreadTicks end_thread_time() const {
      DCHECK(has_thread_time());
      return end_thread_time_;
    }
    base::TimeDelta thread_duration() const {
      DCHECK(has_thread_time());
      return end_thread_time_ - start_thread_time_;
    }

    State state() const { return state_; }

    void RecordTaskStart(LazyNow* now);
    void RecordTaskEnd(LazyNow* now);

    // Protected for tests.
   protected:
    State state_ = State::NotStarted;

    bool has_wall_time_;
    bool has_thread_time_;

    base::TimeTicks start_time_;
    base::TimeTicks end_time_;
    base::ThreadTicks start_thread_time_;
    base::ThreadTicks end_thread_time_;
  };

  // An interface that lets the owner vote on whether or not the associated
  // TaskQueue should be enabled.
  class BASE_EXPORT QueueEnabledVoter {
   public:
    ~QueueEnabledVoter();

    QueueEnabledVoter(const QueueEnabledVoter&) = delete;
    const QueueEnabledVoter& operator=(const QueueEnabledVoter&) = delete;

    // Votes to enable or disable the associated TaskQueue. The TaskQueue will
    // only be enabled if all the voters agree it should be enabled, or if there
    // are no voters.
    // NOTE this must be called on the thread the associated TaskQueue was
    // created on.
    void SetVoteToEnable(bool enabled);

    bool IsVotingToEnable() const { return enabled_; }

   private:
    friend class TaskQueue;
    explicit QueueEnabledVoter(scoped_refptr<TaskQueue> task_queue);

    scoped_refptr<TaskQueue> const task_queue_;
    bool enabled_;
  };

  // Returns an interface that allows the caller to vote on whether or not this
  // TaskQueue is enabled. The TaskQueue will be enabled if there are no voters
  // or if all agree it should be enabled.
  // NOTE this must be called on the thread this TaskQueue was created by.
  std::unique_ptr<QueueEnabledVoter> CreateQueueEnabledVoter();

  // NOTE this must be called on the thread this TaskQueue was created by.
  bool IsQueueEnabled() const;

  // Returns true if the queue is completely empty.
  bool IsEmpty() const;

  // Returns the number of pending tasks in the queue.
  size_t GetNumberOfPendingTasks() const;

  // Returns true if the queue has work that's ready to execute now.
  // NOTE: this must be called on the thread this TaskQueue was created by.
  bool HasTaskToRunImmediately() const;

  // Returns requested run time of next scheduled wake-up for a delayed task
  // which is not ready to run. If there are no such tasks (immediate tasks
  // don't count) or the queue is disabled it returns nullopt.
  // NOTE: this must be called on the thread this TaskQueue was created by.
  Optional<TimeTicks> GetNextScheduledWakeUp();

  // Can be called on any thread.
  virtual const char* GetName() const;

  // Set the priority of the queue to |priority|. NOTE this must be called on
  // the thread this TaskQueue was created by.
  void SetQueuePriority(QueuePriority priority);

  // Returns the current queue priority.
  QueuePriority GetQueuePriority() const;

  // These functions can only be called on the same thread that the task queue
  // manager executes its tasks on.
  void AddTaskObserver(TaskObserver* task_observer);
  void RemoveTaskObserver(TaskObserver* task_observer);

  // Set the blame context which is entered and left while executing tasks from
  // this task queue. |blame_context| must be null or outlive this task queue.
  // Must be called on the thread this TaskQueue was created by.
  void SetBlameContext(trace_event::BlameContext* blame_context);

  // Removes the task queue from the previous TimeDomain and adds it to
  // |domain|.  This is a moderately expensive operation.
  void SetTimeDomain(TimeDomain* domain);

  // Returns the queue's current TimeDomain.  Can be called from any thread.
  TimeDomain* GetTimeDomain() const;

  enum class InsertFencePosition {
    kNow,  // Tasks posted on the queue up till this point further may run.
           // All further tasks are blocked.
    kBeginningOfTime,  // No tasks posted on this queue may run.
  };

  // Inserts a barrier into the task queue which prevents tasks with an enqueue
  // order greater than the fence from running until either the fence has been
  // removed or a subsequent fence has unblocked some tasks within the queue.
  // Note: delayed tasks get their enqueue order set once their delay has
  // expired, and non-delayed tasks get their enqueue order set when posted.
  //
  // Fences come in three flavours:
  // - Regular (InsertFence(NOW)) - all tasks posted after this moment
  //   are blocked.
  // - Fully blocking (InsertFence(kBeginningOfTime)) - all tasks including
  //   already posted are blocked.
  // - Delayed (InsertFenceAt(timestamp)) - blocks all tasks posted after given
  //   point in time (must be in the future).
  //
  // Only one fence can be scheduled at a time. Inserting a new fence
  // will automatically remove the previous one, regardless of fence type.
  void InsertFence(InsertFencePosition position);

  // Delayed fences are only allowed for queues created with
  // SetDelayedFencesAllowed(true) because this feature implies sampling Now()
  // (which isn't free) for every PostTask, even those with zero delay.
  void InsertFenceAt(TimeTicks time);

  // Removes any previously added fence and unblocks execution of any tasks
  // blocked by it.
  void RemoveFence();

  // Returns true if the queue has a fence but it isn't necessarily blocking
  // execution of tasks (it may be the case if tasks enqueue order hasn't
  // reached the number set for a fence).
  bool HasActiveFence();

  // Returns true if the queue has a fence which is blocking execution of tasks.
  bool BlockedByFence() const;

  // Returns an EnqueueOrder generated at the last transition to unblocked. A
  // queue is unblocked when it is enabled and no fence prevents the front task
  // from running. If the EnqueueOrder of a task is greater than this when it
  // starts running, it means that is was never blocked.
  EnqueueOrder GetEnqueueOrderAtWhichWeBecameUnblocked() const;

  void SetObserver(Observer* observer);

  // Controls whether or not the queue will emit traces events when tasks are
  // posted to it while disabled. This only applies for the current or next
  // period during which the queue is disabled. When the queue is re-enabled
  // this will revert back to the default value of false.
  void SetShouldReportPostedTasksWhenDisabled(bool should_report);

  // Create a task runner for this TaskQueue which will annotate all
  // posted tasks with the given task type.
  // May be called on any thread.
  // NOTE: Task runners don't hold a reference to a TaskQueue, hence,
  // it's required to retain that reference to prevent automatic graceful
  // shutdown. Unique ownership of task queues will fix this issue soon.
  scoped_refptr<SingleThreadTaskRunner> CreateTaskRunner(TaskType task_type);

  // Default task runner which doesn't annotate tasks with a task type.
  scoped_refptr<SingleThreadTaskRunner> task_runner() const {
    return default_task_runner_;
  }

 protected:
  virtual ~TaskQueue();

  internal::TaskQueueImpl* GetTaskQueueImpl() const { return impl_.get(); }

 private:
  friend class RefCountedThreadSafe<TaskQueue>;
  friend class internal::SequenceManagerImpl;
  friend class internal::TaskQueueImpl;

  void AddQueueEnabledVoter(bool voter_is_enabled);
  void RemoveQueueEnabledVoter(bool voter_is_enabled);
  bool AreAllQueueEnabledVotersEnabled() const;
  void OnQueueEnabledVoteChanged(bool enabled);

  bool IsOnMainThread() const;

  // TaskQueue has ownership of an underlying implementation but in certain
  // cases (e.g. detached frames) their lifetime may diverge.
  // This method should be used to take away the impl for graceful shutdown.
  // TaskQueue will disregard any calls or posting tasks thereafter.
  std::unique_ptr<internal::TaskQueueImpl> TakeTaskQueueImpl();

  // |impl_| can be written to on the main thread but can be read from
  // any thread.
  // |impl_lock_| must be acquired when writing to |impl_| or when accessing
  // it from non-main thread. Reading from the main thread does not require
  // a lock.
  mutable base::internal::CheckedLock impl_lock_{
      base::internal::UniversalPredecessor{}};
  std::unique_ptr<internal::TaskQueueImpl> impl_;

  const WeakPtr<internal::SequenceManagerImpl> sequence_manager_;

  scoped_refptr<internal::AssociatedThreadId> associated_thread_;
  scoped_refptr<SingleThreadTaskRunner> default_task_runner_;

  int enabled_voter_count_ = 0;
  int voter_count_ = 0;
  const char* name_;

  DISALLOW_COPY_AND_ASSIGN(TaskQueue);
};

}  // namespace sequence_manager
}  // namespace base

#endif  // BASE_TASK_SEQUENCE_MANAGER_TASK_QUEUE_H_
