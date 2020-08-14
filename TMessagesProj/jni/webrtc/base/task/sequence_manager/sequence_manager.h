// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_SEQUENCE_MANAGER_SEQUENCE_MANAGER_H_
#define BASE_TASK_SEQUENCE_MANAGER_SEQUENCE_MANAGER_H_

#include <memory>
#include <utility>

#include "base/macros.h"
#include "base/message_loop/message_pump_type.h"
#include "base/message_loop/timer_slack.h"
#include "base/sequenced_task_runner.h"
#include "base/single_thread_task_runner.h"
#include "base/task/sequence_manager/task_queue_impl.h"
#include "base/task/sequence_manager/task_time_observer.h"
#include "base/time/default_tick_clock.h"

namespace base {

class MessagePump;
class TaskObserver;

namespace sequence_manager {

class TimeDomain;

// Represent outstanding work the sequence underlying a SequenceManager (e.g.,
// a native system task for drawing the UI). As long as this handle is alive,
// the work is considered to be pending.
class NativeWorkHandle {
 public:
  virtual ~NativeWorkHandle();
  NativeWorkHandle(const NativeWorkHandle&) = delete;

 protected:
  NativeWorkHandle() = default;
};

// SequenceManager manages TaskQueues which have different properties
// (e.g. priority, common task type) multiplexing all posted tasks into
// a single backing sequence (currently bound to a single thread, which is
// refererred as *main thread* in the comments below). SequenceManager
// implementation can be used in a various ways to apply scheduling logic.
class BASE_EXPORT SequenceManager {
 public:
  class Observer {
   public:
    virtual ~Observer() = default;
    // Called back on the main thread.
    virtual void OnBeginNestedRunLoop() = 0;
    virtual void OnExitNestedRunLoop() = 0;
  };

  struct MetricRecordingSettings {
    // This parameter will be updated for consistency on creation (setting
    // value to 0 when ThreadTicks are not supported).
    MetricRecordingSettings(double task_sampling_rate_for_recording_cpu_time);

    // The proportion of the tasks for which the cpu time will be
    // sampled or 0 if this is not enabled.
    // Since randomised sampling requires the use of Rand(), it is enabled only
    // on platforms which support it.
    // If it is 1 then cpu time is measured for each task, so the integral
    // metrics (as opposed to per-task metrics) can be recorded.
    double task_sampling_rate_for_recording_cpu_time = 0;

    bool records_cpu_time_for_some_tasks() const {
      return task_sampling_rate_for_recording_cpu_time > 0.0;
    }

    bool records_cpu_time_for_all_tasks() const {
      return task_sampling_rate_for_recording_cpu_time == 1.0;
    }
  };

  // Settings defining the desired SequenceManager behaviour: the type of the
  // MessageLoop and whether randomised sampling should be enabled.
  struct BASE_EXPORT Settings {
    class Builder;

    Settings();
    // In the future MessagePump (which is move-only) will also be a setting,
    // so we are making Settings move-only in preparation.
    Settings(Settings&& move_from) noexcept;

    MessagePumpType message_loop_type = MessagePumpType::DEFAULT;
    bool randomised_sampling_enabled = false;
    const TickClock* clock = DefaultTickClock::GetInstance();

    // If true, add the timestamp the task got queued to the task.
    bool add_queue_time_to_tasks = false;

#if DCHECK_IS_ON()
    // TODO(alexclarke): Consider adding command line flags to control these.
    enum class TaskLogging {
      kNone,
      kEnabled,
      kEnabledWithBacktrace,

      // Logs high priority tasks and the lower priority tasks they skipped
      // past.  Useful for debugging test failures caused by scheduler policy
      // changes.
      kReorderedOnly,
    };
    TaskLogging task_execution_logging = TaskLogging::kNone;

    // If true PostTask will emit a debug log.
    bool log_post_task = false;

    // If true debug logs will be emitted when a delayed task becomes eligible
    // to run.
    bool log_task_delay_expiry = false;

    // If true usages of the RunLoop API will be logged.
    bool log_runloop_quit_and_quit_when_idle = false;

    // Scheduler policy induced raciness is an area of concern. This lets us
    // apply an extra delay per priority for cross thread posting.
    std::array<TimeDelta, TaskQueue::kQueuePriorityCount>
        per_priority_cross_thread_task_delay;

    // Like the above but for same thread posting.
    std::array<TimeDelta, TaskQueue::kQueuePriorityCount>
        per_priority_same_thread_task_delay;

    // If not zero this seeds a PRNG used by the task selection logic to choose
    // a random TaskQueue for a given priority rather than the TaskQueue with
    // the oldest EnqueueOrder.
    int random_task_selection_seed = 0;

#endif  // DCHECK_IS_ON()

    DISALLOW_COPY_AND_ASSIGN(Settings);
  };

  virtual ~SequenceManager() = default;

  // Binds the SequenceManager and its TaskQueues to the current thread. Should
  // only be called once. Note that CreateSequenceManagerOnCurrentThread()
  // performs this initialization automatically.
  virtual void BindToCurrentThread() = 0;

  // Returns the task runner the current task was posted on. Returns null if no
  // task is currently running. Must be called on the bound thread.
  virtual scoped_refptr<SequencedTaskRunner> GetTaskRunnerForCurrentTask() = 0;

  // Finishes the initialization for a SequenceManager created via
  // CreateUnboundSequenceManager(). Must not be called in any other
  // circumstances. The ownership of the pump is transferred to SequenceManager.
  virtual void BindToMessagePump(std::unique_ptr<MessagePump> message_pump) = 0;

  // Must be called on the main thread.
  // Can be called only once, before creating TaskQueues.
  // Observer must outlive the SequenceManager.
  virtual void SetObserver(Observer* observer) = 0;

  // Must be called on the main thread.
  virtual void AddTaskTimeObserver(TaskTimeObserver* task_time_observer) = 0;
  virtual void RemoveTaskTimeObserver(TaskTimeObserver* task_time_observer) = 0;

  // Registers a TimeDomain with SequenceManager.
  // TaskQueues must only be created with a registered TimeDomain.
  // Conversely, any TimeDomain must remain registered until no
  // TaskQueues (using that TimeDomain) remain.
  virtual void RegisterTimeDomain(TimeDomain* time_domain) = 0;
  virtual void UnregisterTimeDomain(TimeDomain* time_domain) = 0;

  virtual TimeDomain* GetRealTimeDomain() const = 0;
  virtual const TickClock* GetTickClock() const = 0;
  virtual TimeTicks NowTicks() const = 0;

  // Sets the SingleThreadTaskRunner that will be returned by
  // ThreadTaskRunnerHandle::Get on the main thread.
  virtual void SetDefaultTaskRunner(
      scoped_refptr<SingleThreadTaskRunner> task_runner) = 0;

  // Removes all canceled delayed tasks, and considers resizing to fit all
  // internal queues.
  virtual void ReclaimMemory() = 0;

  // Returns true if no tasks were executed in TaskQueues that monitor
  // quiescence since the last call to this method.
  virtual bool GetAndClearSystemIsQuiescentBit() = 0;

  // Set the number of tasks executed in a single SequenceManager invocation.
  // Increasing this number reduces the overhead of the tasks dispatching
  // logic at the cost of a potentially worse latency. 1 by default.
  virtual void SetWorkBatchSize(int work_batch_size) = 0;

  // Requests desired timer precision from the OS.
  // Has no effect on some platforms.
  virtual void SetTimerSlack(TimerSlack timer_slack) = 0;

  // Enables crash keys that can be set in the scope of a task which help
  // to identify the culprit if upcoming work results in a crash.
  // Key names must be thread-specific to avoid races and corrupted crash dumps.
  virtual void EnableCrashKeys(const char* async_stack_crash_key) = 0;

  // Returns the metric recording configuration for the current SequenceManager.
  virtual const MetricRecordingSettings& GetMetricRecordingSettings() const = 0;

  // Creates a task queue with the given type, |spec| and args.
  // Must be called on the main thread.
  // TODO(scheduler-dev): SequenceManager should not create TaskQueues.
  template <typename TaskQueueType, typename... Args>
  scoped_refptr<TaskQueueType> CreateTaskQueueWithType(
      const TaskQueue::Spec& spec,
      Args&&... args) {
    return WrapRefCounted(new TaskQueueType(CreateTaskQueueImpl(spec), spec,
                                            std::forward<Args>(args)...));
  }

  // Creates a vanilla TaskQueue rather than a user type derived from it. This
  // should be used if you don't wish to sub class TaskQueue.
  // Must be called on the main thread.
  virtual scoped_refptr<TaskQueue> CreateTaskQueue(
      const TaskQueue::Spec& spec) = 0;

  // Returns true iff this SequenceManager has no immediate work to do. I.e.
  // there are no pending non-delayed tasks or delayed tasks that are due to
  // run. This method ignores any pending delayed tasks that might have become
  // eligible to run since the last task was executed. This is important because
  // if it did tests would become flaky depending on the exact timing of this
  // call. This is moderately expensive.
  virtual bool IsIdleForTesting() = 0;

  // The total number of posted tasks that haven't executed yet.
  virtual size_t GetPendingTaskCountForTesting() const = 0;

  // Returns a JSON string which describes all pending tasks.
  virtual std::string DescribeAllPendingTasks() const = 0;

  // Indicates that the underlying sequence (e.g., the message pump) has pending
  // work at priority |priority|. If the priority of the work in this
  // SequenceManager is lower, it will yield to let the native work run. The
  // native work is assumed to remain pending while the returned handle is
  // valid.
  //
  // Must be called on the main thread, and the returned handle must also be
  // deleted on the main thread.
  virtual std::unique_ptr<NativeWorkHandle> OnNativeWorkPending(
      TaskQueue::QueuePriority priority) = 0;

  // Adds an observer which reports task execution. Can only be called on the
  // same thread that |this| is running on.
  virtual void AddTaskObserver(TaskObserver* task_observer) = 0;

  // Removes an observer which reports task execution. Can only be called on the
  // same thread that |this| is running on.
  virtual void RemoveTaskObserver(TaskObserver* task_observer) = 0;

 protected:
  virtual std::unique_ptr<internal::TaskQueueImpl> CreateTaskQueueImpl(
      const TaskQueue::Spec& spec) = 0;
};

class BASE_EXPORT SequenceManager::Settings::Builder {
 public:
  Builder();
  ~Builder();

  // Sets the MessagePumpType which is used to create a MessagePump.
  Builder& SetMessagePumpType(MessagePumpType message_loop_type);

  Builder& SetRandomisedSamplingEnabled(bool randomised_sampling_enabled);

  // Sets the TickClock the SequenceManager uses to obtain Now.
  Builder& SetTickClock(const TickClock* clock);

  // Whether or not queueing timestamp will be added to tasks.
  Builder& SetAddQueueTimeToTasks(bool add_queue_time_to_tasks);

#if DCHECK_IS_ON()
  // Controls task execution logging.
  Builder& SetTaskLogging(TaskLogging task_execution_logging);

  // Whether or not PostTask will emit a debug log.
  Builder& SetLogPostTask(bool log_post_task);

  // Whether or not debug logs will be emitted when a delayed task becomes
  // eligible to run.
  Builder& SetLogTaskDelayExpiry(bool log_task_delay_expiry);

  // Whether or not usages of the RunLoop API will be logged.
  Builder& SetLogRunloopQuitAndQuitWhenIdle(
      bool log_runloop_quit_and_quit_when_idle);

  // Scheduler policy induced raciness is an area of concern. This lets us
  // apply an extra delay per priority for cross thread posting.
  Builder& SetPerPriorityCrossThreadTaskDelay(
      std::array<TimeDelta, TaskQueue::kQueuePriorityCount>
          per_priority_cross_thread_task_delay);

  // Scheduler policy induced raciness is an area of concern. This lets us
  // apply an extra delay per priority for same thread posting.
  Builder& SetPerPrioritySameThreadTaskDelay(
      std::array<TimeDelta, TaskQueue::kQueuePriorityCount>
          per_priority_same_thread_task_delay);

  // If not zero this seeds a PRNG used by the task selection logic to choose a
  // random TaskQueue for a given priority rather than the TaskQueue with the
  // oldest EnqueueOrder.
  Builder& SetRandomTaskSelectionSeed(int random_task_selection_seed);

#endif  // DCHECK_IS_ON()

  Settings Build();

 private:
  Settings settings_;
};

// Create SequenceManager using MessageLoop on the current thread.
// Implementation is located in sequence_manager_impl.cc.
// TODO(scheduler-dev): Remove after every thread has a SequenceManager.
BASE_EXPORT std::unique_ptr<SequenceManager>
CreateSequenceManagerOnCurrentThread(SequenceManager::Settings settings);

// Create a SequenceManager using the given MessagePump on the current thread.
// MessagePump instances can be created with
// MessagePump::CreateMessagePumpForType().
BASE_EXPORT std::unique_ptr<SequenceManager>
CreateSequenceManagerOnCurrentThreadWithPump(
    std::unique_ptr<MessagePump> message_pump,
    SequenceManager::Settings settings = SequenceManager::Settings());

// Create an unbound SequenceManager (typically for a future thread or because
// additional setup is required before binding). The SequenceManager can be
// initialized on the current thread and then needs to be bound and initialized
// on the target thread by calling one of the Bind*() methods.
BASE_EXPORT std::unique_ptr<SequenceManager> CreateUnboundSequenceManager(
    SequenceManager::Settings settings = SequenceManager::Settings());

}  // namespace sequence_manager
}  // namespace base

#endif  // BASE_TASK_SEQUENCE_MANAGER_SEQUENCE_MANAGER_H_
