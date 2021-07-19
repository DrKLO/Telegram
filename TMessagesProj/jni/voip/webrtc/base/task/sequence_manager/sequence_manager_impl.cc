// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/sequence_manager/sequence_manager_impl.h"

#include <queue>
#include <vector>

#include "base/bind.h"
#include "base/bit_cast.h"
#include "base/compiler_specific.h"
#include "base/debug/crash_logging.h"
#include "base/debug/stack_trace.h"
#include "base/json/json_writer.h"
#include "base/memory/ptr_util.h"
#include "base/message_loop/message_loop_current.h"
#include "base/no_destructor.h"
#include "base/optional.h"
#include "base/rand_util.h"
#include "base/task/sequence_manager/real_time_domain.h"
#include "base/task/sequence_manager/task_time_observer.h"
#include "base/task/sequence_manager/thread_controller_impl.h"
#include "base/task/sequence_manager/thread_controller_with_message_pump_impl.h"
#include "base/task/sequence_manager/work_queue.h"
#include "base/task/sequence_manager/work_queue_sets.h"
#include "base/threading/thread_id_name_manager.h"
#include "base/threading/thread_local.h"
#include "base/time/default_tick_clock.h"
#include "base/time/tick_clock.h"
#include "base/trace_event/trace_event.h"
#include "build/build_config.h"

namespace base {
namespace sequence_manager {
namespace {

base::ThreadLocalPointer<internal::SequenceManagerImpl>*
GetTLSSequenceManagerImpl() {
  static NoDestructor<ThreadLocalPointer<internal::SequenceManagerImpl>>
      lazy_tls_ptr;
  return lazy_tls_ptr.get();
}

}  // namespace

// This controls how big the the initial for
// |MainThreadOnly::task_execution_stack| should be. We don't expect to see
// depths of more than 2 unless cooperative scheduling is used on Blink, where
// we might get up to 6. Anyway 10 was chosen because it's a round number
// greater than current anticipated usage.
static constexpr const size_t kInitialTaskExecutionStackReserveCount = 10;

std::unique_ptr<SequenceManager> CreateSequenceManagerOnCurrentThread(
    SequenceManager::Settings settings) {
  return internal::SequenceManagerImpl::CreateOnCurrentThread(
      std::move(settings));
}

std::unique_ptr<SequenceManager> CreateSequenceManagerOnCurrentThreadWithPump(
    std::unique_ptr<MessagePump> message_pump,
    SequenceManager::Settings settings) {
  std::unique_ptr<SequenceManager> sequence_manager =
      internal::SequenceManagerImpl::CreateUnbound(std::move(settings));
  sequence_manager->BindToMessagePump(std::move(message_pump));
  return sequence_manager;
}

std::unique_ptr<SequenceManager> CreateUnboundSequenceManager(
    SequenceManager::Settings settings) {
  return internal::SequenceManagerImpl::CreateUnbound(std::move(settings));
}

namespace internal {

using TimeRecordingPolicy =
    base::sequence_manager::TaskQueue::TaskTiming::TimeRecordingPolicy;

namespace {

constexpr TimeDelta kLongTaskTraceEventThreshold =
    TimeDelta::FromMilliseconds(50);
// Proportion of tasks which will record thread time for metrics.
const double kTaskSamplingRateForRecordingCPUTime = 0.01;
// Proprortion of SequenceManagers which will record thread time for each task,
// enabling advanced metrics.
const double kThreadSamplingRateForRecordingCPUTime = 0.0001;

// Magic value to protect against memory corruption and bail out
// early when detected.
constexpr int kMemoryCorruptionSentinelValue = 0xdeadbeef;

void ReclaimMemoryFromQueue(internal::TaskQueueImpl* queue,
                            std::map<TimeDomain*, TimeTicks>* time_domain_now) {
  TimeDomain* time_domain = queue->GetTimeDomain();
  if (time_domain_now->find(time_domain) == time_domain_now->end())
    time_domain_now->insert(std::make_pair(time_domain, time_domain->Now()));
  queue->ReclaimMemory(time_domain_now->at(time_domain));
  queue->delayed_work_queue()->RemoveAllCanceledTasksFromFront();
  queue->immediate_work_queue()->RemoveAllCanceledTasksFromFront();
}

SequenceManager::MetricRecordingSettings InitializeMetricRecordingSettings(
    bool randomised_sampling_enabled) {
  if (!randomised_sampling_enabled)
    return SequenceManager::MetricRecordingSettings(0);
  bool records_cpu_time_for_each_task =
      base::RandDouble() < kThreadSamplingRateForRecordingCPUTime;
  return SequenceManager::MetricRecordingSettings(
      records_cpu_time_for_each_task ? 1
                                     : kTaskSamplingRateForRecordingCPUTime);
}

// Writes |address| in hexadecimal ("0x11223344") form starting from |output|
// and moving backwards in memory. Returns a pointer to the first digit of the
// result. Does *not* NUL-terminate the number.
#if !defined(OS_NACL)
char* PrependHexAddress(char* output, const void* address) {
  uintptr_t value = reinterpret_cast<uintptr_t>(address);
  static const char kHexChars[] = "0123456789ABCDEF";
  do {
    *output-- = kHexChars[value % 16];
    value /= 16;
  } while (value);
  *output-- = 'x';
  *output = '0';
  return output;
}
#endif  // !defined(OS_NACL)

}  // namespace

class SequenceManagerImpl::NativeWorkHandleImpl : public NativeWorkHandle {
 public:
  NativeWorkHandleImpl(SequenceManagerImpl* sequence_manager,
                       TaskQueue::QueuePriority priority)
      : sequence_manager_(sequence_manager->GetWeakPtr()), priority_(priority) {
    TRACE_EVENT_NESTABLE_ASYNC_BEGIN1("sequence_manager", "NativeWork", this,
                                      "priority",
                                      TaskQueue::PriorityToString(priority_));
    sequence_manager_->main_thread_only().pending_native_work.insert(priority_);
  }

  ~NativeWorkHandleImpl() final {
    TRACE_EVENT_NESTABLE_ASYNC_END0("sequence_manager", "NativeWork", this);
    if (!sequence_manager_)
      return;
    TaskQueue::QueuePriority prev_priority = effective_priority();
    sequence_manager_->main_thread_only().pending_native_work.erase(priority_);
    // We should always have at least one instance of pending native work. By
    // default it is of the lowest priority, which doesn't cause SequenceManager
    // to yield.
    DCHECK_GE(sequence_manager_->main_thread_only().pending_native_work.size(),
              1u);
    if (prev_priority != effective_priority())
      sequence_manager_->ScheduleWork();
  }

  TaskQueue::QueuePriority effective_priority() const {
    return *sequence_manager_->main_thread_only().pending_native_work.begin();
  }

 private:
  WeakPtr<SequenceManagerImpl> sequence_manager_;
  const TaskQueue::QueuePriority priority_;
};

// static
SequenceManagerImpl* SequenceManagerImpl::GetCurrent() {
  return GetTLSSequenceManagerImpl()->Get();
}

SequenceManagerImpl::SequenceManagerImpl(
    std::unique_ptr<internal::ThreadController> controller,
    SequenceManager::Settings settings)
    : associated_thread_(controller->GetAssociatedThread()),
      controller_(std::move(controller)),
      settings_(std::move(settings)),
      metric_recording_settings_(InitializeMetricRecordingSettings(
          settings_.randomised_sampling_enabled)),
      add_queue_time_to_tasks_(settings_.add_queue_time_to_tasks),

      empty_queues_to_reload_(associated_thread_),
      memory_corruption_sentinel_(kMemoryCorruptionSentinelValue),
      main_thread_only_(associated_thread_, settings_) {
  TRACE_EVENT_OBJECT_CREATED_WITH_ID(
      TRACE_DISABLED_BY_DEFAULT("sequence_manager"), "SequenceManager", this);
  main_thread_only().selector.SetTaskQueueSelectorObserver(this);

  main_thread_only().next_time_to_reclaim_memory =
      settings_.clock->NowTicks() + kReclaimMemoryInterval;

  RegisterTimeDomain(main_thread_only().real_time_domain.get());

  controller_->SetSequencedTaskSource(this);
}

SequenceManagerImpl::~SequenceManagerImpl() {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  TRACE_EVENT_OBJECT_DELETED_WITH_ID(
      TRACE_DISABLED_BY_DEFAULT("sequence_manager"), "SequenceManager", this);

#if defined(OS_IOS)
  if (settings_.message_loop_type == MessagePumpType::UI &&
      associated_thread_->IsBound()) {
    controller_->DetachFromMessagePump();
  }
#endif

  // Make sure no Task is running as given that RunLoop does not support the
  // Delegate being destroyed from a Task and
  // ThreadControllerWithMessagePumpImpl does not support being destroyed from a
  // Task. If we are using a ThreadControllerImpl (i.e. no pump) destruction is
  // fine
  DCHECK(!controller_->GetBoundMessagePump() ||
         main_thread_only().task_execution_stack.empty());

  for (internal::TaskQueueImpl* queue : main_thread_only().active_queues) {
    main_thread_only().selector.RemoveQueue(queue);
    queue->UnregisterTaskQueue();
  }

  // TODO(altimin): restore default task runner automatically when
  // ThreadController is destroyed.
  controller_->RestoreDefaultTaskRunner();

  main_thread_only().active_queues.clear();
  main_thread_only().queues_to_gracefully_shutdown.clear();
  main_thread_only().selector.SetTaskQueueSelectorObserver(nullptr);

  // In some tests a NestingObserver may not have been registered.
  if (main_thread_only().nesting_observer_registered_)
    controller_->RemoveNestingObserver(this);

  // Let interested parties have one last shot at accessing this.
  for (auto& observer : main_thread_only().destruction_observers)
    observer.WillDestroyCurrentMessageLoop();

  // OK, now make it so that no one can find us.
  if (GetMessagePump()) {
    DCHECK_EQ(this, GetTLSSequenceManagerImpl()->Get());
    GetTLSSequenceManagerImpl()->Set(nullptr);
  }
}

SequenceManagerImpl::MainThreadOnly::MainThreadOnly(
    const scoped_refptr<AssociatedThreadId>& associated_thread,
    const SequenceManager::Settings& settings)
    : selector(associated_thread, settings),
      real_time_domain(new internal::RealTimeDomain()) {
  if (settings.randomised_sampling_enabled) {
    random_generator = std::mt19937_64(RandUint64());
    uniform_distribution = std::uniform_real_distribution<double>(0.0, 1.0);
  }
  task_execution_stack.reserve(kInitialTaskExecutionStackReserveCount);
}

SequenceManagerImpl::MainThreadOnly::~MainThreadOnly() = default;

// static
std::unique_ptr<ThreadControllerImpl>
SequenceManagerImpl::CreateThreadControllerImplForCurrentThread(
    const TickClock* clock) {
  auto* sequence_manager = GetTLSSequenceManagerImpl()->Get();
  return ThreadControllerImpl::Create(sequence_manager, clock);
}

// static
std::unique_ptr<SequenceManagerImpl> SequenceManagerImpl::CreateOnCurrentThread(
    SequenceManager::Settings settings) {
  std::unique_ptr<SequenceManagerImpl> manager(new SequenceManagerImpl(
      CreateThreadControllerImplForCurrentThread(settings.clock),
      std::move(settings)));
  manager->BindToCurrentThread();
  return manager;
}

// static
std::unique_ptr<SequenceManagerImpl> SequenceManagerImpl::CreateUnbound(
    SequenceManager::Settings settings) {
  return WrapUnique(new SequenceManagerImpl(
      ThreadControllerWithMessagePumpImpl::CreateUnbound(settings),
      std::move(settings)));
}

void SequenceManagerImpl::BindToMessagePump(std::unique_ptr<MessagePump> pump) {
  controller_->BindToCurrentThread(std::move(pump));
  CompleteInitializationOnBoundThread();

  // On Android attach to the native loop when there is one.
#if defined(OS_ANDROID)
  if (settings_.message_loop_type == MessagePumpType::UI ||
      settings_.message_loop_type == MessagePumpType::JAVA) {
    controller_->AttachToMessagePump();
  }
#endif

  // On iOS attach to the native loop when there is one.
#if defined(OS_IOS)
  if (settings_.message_loop_type == MessagePumpType::UI) {
    controller_->AttachToMessagePump();
  }
#endif
}

void SequenceManagerImpl::BindToCurrentThread() {
  associated_thread_->BindToCurrentThread();
  CompleteInitializationOnBoundThread();
}

void SequenceManagerImpl::BindToCurrentThread(
    std::unique_ptr<MessagePump> pump) {
  associated_thread_->BindToCurrentThread();
  BindToMessagePump(std::move(pump));
}

scoped_refptr<SequencedTaskRunner>
SequenceManagerImpl::GetTaskRunnerForCurrentTask() {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (main_thread_only().task_execution_stack.empty())
    return nullptr;
  return main_thread_only()
      .task_execution_stack.back()
      .pending_task.task_runner;
}

void SequenceManagerImpl::CompleteInitializationOnBoundThread() {
  controller_->AddNestingObserver(this);
  main_thread_only().nesting_observer_registered_ = true;
  if (GetMessagePump()) {
    DCHECK(!GetTLSSequenceManagerImpl()->Get())
        << "Can't register a second SequenceManagerImpl on the same thread.";
    GetTLSSequenceManagerImpl()->Set(this);
  }
}

void SequenceManagerImpl::RegisterTimeDomain(TimeDomain* time_domain) {
  main_thread_only().time_domains.insert(time_domain);
  time_domain->OnRegisterWithSequenceManager(this);
}

void SequenceManagerImpl::UnregisterTimeDomain(TimeDomain* time_domain) {
  main_thread_only().time_domains.erase(time_domain);
}

TimeDomain* SequenceManagerImpl::GetRealTimeDomain() const {
  return main_thread_only().real_time_domain.get();
}

std::unique_ptr<internal::TaskQueueImpl>
SequenceManagerImpl::CreateTaskQueueImpl(const TaskQueue::Spec& spec) {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  TimeDomain* time_domain = spec.time_domain
                                ? spec.time_domain
                                : main_thread_only().real_time_domain.get();
  DCHECK(main_thread_only().time_domains.find(time_domain) !=
         main_thread_only().time_domains.end());
  std::unique_ptr<internal::TaskQueueImpl> task_queue =
      std::make_unique<internal::TaskQueueImpl>(this, time_domain, spec);
  main_thread_only().active_queues.insert(task_queue.get());
  main_thread_only().selector.AddQueue(task_queue.get());
  return task_queue;
}

void SequenceManagerImpl::SetAddQueueTimeToTasks(bool enable) {
  base::subtle::NoBarrier_Store(&add_queue_time_to_tasks_, enable ? 1 : 0);
}

bool SequenceManagerImpl::GetAddQueueTimeToTasks() {
  return base::subtle::NoBarrier_Load(&add_queue_time_to_tasks_);
}

void SequenceManagerImpl::SetObserver(Observer* observer) {
  main_thread_only().observer = observer;
}

void SequenceManagerImpl::ShutdownTaskQueueGracefully(
    std::unique_ptr<internal::TaskQueueImpl> task_queue) {
  main_thread_only().queues_to_gracefully_shutdown[task_queue.get()] =
      std::move(task_queue);
}

void SequenceManagerImpl::UnregisterTaskQueueImpl(
    std::unique_ptr<internal::TaskQueueImpl> task_queue) {
  TRACE_EVENT1("sequence_manager", "SequenceManagerImpl::UnregisterTaskQueue",
               "queue_name", task_queue->GetName());
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);

  main_thread_only().selector.RemoveQueue(task_queue.get());

  // After UnregisterTaskQueue returns no new tasks can be posted.
  // It's important to call it first to avoid race condition between removing
  // the task queue from various lists here and adding it to the same lists
  // when posting a task.
  task_queue->UnregisterTaskQueue();

  // Add |task_queue| to |main_thread_only().queues_to_delete| so we can prevent
  // it from being freed while any of our structures hold hold a raw pointer to
  // it.
  main_thread_only().active_queues.erase(task_queue.get());
  main_thread_only().queues_to_delete[task_queue.get()] = std::move(task_queue);
}

AtomicFlagSet::AtomicFlag
SequenceManagerImpl::GetFlagToRequestReloadForEmptyQueue(
    TaskQueueImpl* task_queue) {
  return empty_queues_to_reload_.AddFlag(BindRepeating(
      &TaskQueueImpl::ReloadEmptyImmediateWorkQueue, Unretained(task_queue)));
}

void SequenceManagerImpl::ReloadEmptyWorkQueues() const {
  // There are two cases where a queue needs reloading.  First, it might be
  // completely empty and we've just posted a task (this method handles that
  // case). Secondly if the work queue becomes empty when calling
  // WorkQueue::TakeTaskFromWorkQueue (handled there).
  //
  // Invokes callbacks created by GetFlagToRequestReloadForEmptyQueue above.
  empty_queues_to_reload_.RunActiveCallbacks();
}

void SequenceManagerImpl::MoveReadyDelayedTasksToWorkQueues(LazyNow* lazy_now) {
  TRACE_EVENT0(TRACE_DISABLED_BY_DEFAULT("sequence_manager"),
               "SequenceManagerImpl::MoveReadyDelayedTasksToWorkQueues");

  for (TimeDomain* time_domain : main_thread_only().time_domains) {
    if (time_domain == main_thread_only().real_time_domain.get()) {
      time_domain->MoveReadyDelayedTasksToWorkQueues(lazy_now);
    } else {
      LazyNow time_domain_lazy_now = time_domain->CreateLazyNow();
      time_domain->MoveReadyDelayedTasksToWorkQueues(&time_domain_lazy_now);
    }
  }
}

void SequenceManagerImpl::OnBeginNestedRunLoop() {
  main_thread_only().nesting_depth++;
  if (main_thread_only().observer)
    main_thread_only().observer->OnBeginNestedRunLoop();
}

void SequenceManagerImpl::OnExitNestedRunLoop() {
  main_thread_only().nesting_depth--;
  DCHECK_GE(main_thread_only().nesting_depth, 0);
  if (main_thread_only().nesting_depth == 0) {
    // While we were nested some non-nestable tasks may have been deferred.
    // We push them back onto the *front* of their original work queues,
    // that's why we iterate |non_nestable_task_queue| in FIFO order.
    while (!main_thread_only().non_nestable_task_queue.empty()) {
      internal::TaskQueueImpl::DeferredNonNestableTask& non_nestable_task =
          main_thread_only().non_nestable_task_queue.back();
      non_nestable_task.task_queue->RequeueDeferredNonNestableTask(
          std::move(non_nestable_task));
      main_thread_only().non_nestable_task_queue.pop_back();
    }
  }
  if (main_thread_only().observer)
    main_thread_only().observer->OnExitNestedRunLoop();
}

void SequenceManagerImpl::ScheduleWork() {
  controller_->ScheduleWork();
}

void SequenceManagerImpl::SetNextDelayedDoWork(LazyNow* lazy_now,
                                               TimeTicks run_time) {
  controller_->SetNextDelayedDoWork(lazy_now, run_time);
}

namespace {

const char* RunTaskTraceNameForPriority(TaskQueue::QueuePriority priority) {
  switch (priority) {
    case TaskQueue::QueuePriority::kControlPriority:
      return "RunControlPriorityTask";
    case TaskQueue::QueuePriority::kHighestPriority:
      return "RunHighestPriorityTask";
    case TaskQueue::QueuePriority::kVeryHighPriority:
      return "RunVeryHighPriorityTask";
    case TaskQueue::QueuePriority::kHighPriority:
      return "RunHighPriorityTask";
    case TaskQueue::QueuePriority::kNormalPriority:
      return "RunNormalPriorityTask";
    case TaskQueue::QueuePriority::kLowPriority:
      return "RunLowPriorityTask";
    case TaskQueue::QueuePriority::kBestEffortPriority:
      return "RunBestEffortPriorityTask";
    case TaskQueue::QueuePriority::kQueuePriorityCount:
      NOTREACHED();
      return nullptr;
  }
}

}  // namespace

Task* SequenceManagerImpl::SelectNextTask() {
  Task* task = SelectNextTaskImpl();
  if (!task)
    return nullptr;

  ExecutingTask& executing_task =
      *main_thread_only().task_execution_stack.rbegin();

  // It's important that there are no active trace events here which will
  // terminate before we finish executing the task.
  TRACE_EVENT_BEGIN1("sequence_manager",
                     RunTaskTraceNameForPriority(executing_task.priority),
                     "task_type", executing_task.task_type);
  TRACE_EVENT_BEGIN0("sequence_manager", executing_task.task_queue_name);

  return task;
}

#if DCHECK_IS_ON() && !defined(OS_NACL)
void SequenceManagerImpl::LogTaskDebugInfo(
    const WorkQueue* selected_work_queue) const {
  const Task* task = selected_work_queue->GetFrontTask();
  switch (settings_.task_execution_logging) {
    case Settings::TaskLogging::kNone:
      break;

    case Settings::TaskLogging::kEnabled:
      LOG(INFO) << "#" << static_cast<uint64_t>(task->enqueue_order()) << " "
                << selected_work_queue->task_queue()->GetName()
                << (task->cross_thread_ ? " Run crossthread " : " Run ")
                << task->posted_from.ToString();
      break;

    case Settings::TaskLogging::kEnabledWithBacktrace: {
      std::array<const void*, PendingTask::kTaskBacktraceLength + 1> task_trace;
      task_trace[0] = task->posted_from.program_counter();
      std::copy(task->task_backtrace.begin(), task->task_backtrace.end(),
                task_trace.begin() + 1);
      size_t length = 0;
      while (length < task_trace.size() && task_trace[length])
        ++length;
      if (length == 0)
        break;
      LOG(INFO) << "#" << static_cast<uint64_t>(task->enqueue_order()) << " "
                << selected_work_queue->task_queue()->GetName()
                << (task->cross_thread_ ? " Run crossthread " : " Run ")
                << debug::StackTrace(task_trace.data(), length);
      break;
    }

    case Settings::TaskLogging::kReorderedOnly: {
      std::vector<const Task*> skipped_tasks;
      main_thread_only().selector.CollectSkippedOverLowerPriorityTasks(
          selected_work_queue, &skipped_tasks);

      if (skipped_tasks.empty())
        break;

      LOG(INFO) << "#" << static_cast<uint64_t>(task->enqueue_order()) << " "
                << selected_work_queue->task_queue()->GetName()
                << (task->cross_thread_ ? " Run crossthread " : " Run ")
                << task->posted_from.ToString();

      for (const Task* skipped_task : skipped_tasks) {
        LOG(INFO) << "# (skipped over) "
                  << static_cast<uint64_t>(skipped_task->enqueue_order()) << " "
                  << skipped_task->posted_from.ToString();
      }
    }
  }
}
#endif  // DCHECK_IS_ON() && !defined(OS_NACL)

Task* SequenceManagerImpl::SelectNextTaskImpl() {
  CHECK(Validate());

  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  TRACE_EVENT0(TRACE_DISABLED_BY_DEFAULT("sequence_manager"),
               "SequenceManagerImpl::SelectNextTask");

  ReloadEmptyWorkQueues();
  LazyNow lazy_now(controller_->GetClock());
  MoveReadyDelayedTasksToWorkQueues(&lazy_now);

  // If we sampled now, check if it's time to reclaim memory next time we go
  // idle.
  if (lazy_now.has_value() &&
      lazy_now.Now() >= main_thread_only().next_time_to_reclaim_memory) {
    main_thread_only().memory_reclaim_scheduled = true;
  }

  while (true) {
    internal::WorkQueue* work_queue =
        main_thread_only().selector.SelectWorkQueueToService();
    TRACE_EVENT_OBJECT_SNAPSHOT_WITH_ID(
        TRACE_DISABLED_BY_DEFAULT("sequence_manager.debug"), "SequenceManager",
        this, AsValueWithSelectorResult(work_queue, /* force_verbose */ false));

    if (!work_queue)
      return nullptr;

    // If the head task was canceled, remove it and run the selector again.
    if (UNLIKELY(work_queue->RemoveAllCanceledTasksFromFront()))
      continue;

    if (UNLIKELY(work_queue->GetFrontTask()->nestable ==
                     Nestable::kNonNestable &&
                 main_thread_only().nesting_depth > 0)) {
      // Defer non-nestable work. NOTE these tasks can be arbitrarily delayed so
      // the additional delay should not be a problem.
      // Note because we don't delete queues while nested, it's perfectly OK to
      // store the raw pointer for |queue| here.
      internal::TaskQueueImpl::DeferredNonNestableTask deferred_task{
          work_queue->TakeTaskFromWorkQueue(), work_queue->task_queue(),
          work_queue->queue_type()};
      main_thread_only().non_nestable_task_queue.push_back(
          std::move(deferred_task));
      continue;
    }

    if (UNLIKELY(!ShouldRunTaskOfPriority(
            work_queue->task_queue()->GetQueuePriority()))) {
      TRACE_EVENT0(TRACE_DISABLED_BY_DEFAULT("sequence_manager"),
                   "SequenceManager.YieldToNative");
      return nullptr;
    }

#if DCHECK_IS_ON() && !defined(OS_NACL)
    LogTaskDebugInfo(work_queue);
#endif  // DCHECK_IS_ON() && !defined(OS_NACL)

    main_thread_only().task_execution_stack.emplace_back(
        work_queue->TakeTaskFromWorkQueue(), work_queue->task_queue(),
        InitializeTaskTiming(work_queue->task_queue()));

    ExecutingTask& executing_task =
        *main_thread_only().task_execution_stack.rbegin();
    NotifyWillProcessTask(&executing_task, &lazy_now);

    return &executing_task.pending_task;
  }
}

bool SequenceManagerImpl::ShouldRunTaskOfPriority(
    TaskQueue::QueuePriority priority) const {
  return priority <= *main_thread_only().pending_native_work.begin();
}

void SequenceManagerImpl::DidRunTask() {
  LazyNow lazy_now(controller_->GetClock());
  ExecutingTask& executing_task =
      *main_thread_only().task_execution_stack.rbegin();

  TRACE_EVENT_END0("sequence_manager", executing_task.task_queue_name);
  TRACE_EVENT_END0("sequence_manager",
                   RunTaskTraceNameForPriority(executing_task.priority));

  NotifyDidProcessTask(&executing_task, &lazy_now);
  main_thread_only().task_execution_stack.pop_back();

  if (main_thread_only().nesting_depth == 0)
    CleanUpQueues();
}

TimeDelta SequenceManagerImpl::DelayTillNextTask(LazyNow* lazy_now) const {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);

  if (auto priority = main_thread_only().selector.GetHighestPendingPriority()) {
    // If the selector has non-empty queues we trivially know there is immediate
    // work to be done. However we may want to yield to native work if it is
    // more important.
    if (UNLIKELY(!ShouldRunTaskOfPriority(*priority)))
      return GetDelayTillNextDelayedTask(lazy_now);
    return TimeDelta();
  }

  // There may be some incoming immediate work which we haven't accounted for.
  // NB ReloadEmptyWorkQueues involves a memory barrier, so it's fastest to not
  // do this always.
  ReloadEmptyWorkQueues();
  if (auto priority = main_thread_only().selector.GetHighestPendingPriority()) {
    if (UNLIKELY(!ShouldRunTaskOfPriority(*priority)))
      return GetDelayTillNextDelayedTask(lazy_now);
    return TimeDelta();
  }

  // Otherwise we need to find the shortest delay, if any.  NB we don't need to
  // call MoveReadyDelayedTasksToWorkQueues because it's assumed
  // DelayTillNextTask will return TimeDelta>() if the delayed task is due to
  // run now.
  return GetDelayTillNextDelayedTask(lazy_now);
}

TimeDelta SequenceManagerImpl::GetDelayTillNextDelayedTask(
    LazyNow* lazy_now) const {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);

  TimeDelta delay_till_next_task = TimeDelta::Max();
  for (TimeDomain* time_domain : main_thread_only().time_domains) {
    Optional<TimeDelta> delay = time_domain->DelayTillNextTask(lazy_now);
    if (!delay)
      continue;

    if (*delay < delay_till_next_task)
      delay_till_next_task = *delay;
  }
  return delay_till_next_task;
}

bool SequenceManagerImpl::HasPendingHighResolutionTasks() {
  for (TimeDomain* time_domain : main_thread_only().time_domains) {
    if (time_domain->HasPendingHighResolutionTasks())
      return true;
  }
  return false;
}

bool SequenceManagerImpl::OnSystemIdle() {
  bool have_work_to_do = false;
  for (TimeDomain* time_domain : main_thread_only().time_domains) {
    if (time_domain->MaybeFastForwardToNextTask(
            controller_->ShouldQuitRunLoopWhenIdle())) {
      have_work_to_do = true;
    }
  }
  if (!have_work_to_do)
    MaybeReclaimMemory();
  return have_work_to_do;
}

void SequenceManagerImpl::WillQueueTask(Task* pending_task,
                                        const char* task_queue_name) {
  controller_->WillQueueTask(pending_task, task_queue_name);
}

TaskQueue::TaskTiming SequenceManagerImpl::InitializeTaskTiming(
    internal::TaskQueueImpl* task_queue) {
  bool records_wall_time =
      ShouldRecordTaskTiming(task_queue) == TimeRecordingPolicy::DoRecord;
  bool records_thread_time = records_wall_time && ShouldRecordCPUTimeForTask();
  return TaskQueue::TaskTiming(records_wall_time, records_thread_time);
}

TimeRecordingPolicy SequenceManagerImpl::ShouldRecordTaskTiming(
    const internal::TaskQueueImpl* task_queue) {
  if (task_queue->RequiresTaskTiming())
    return TimeRecordingPolicy::DoRecord;
  if (main_thread_only().nesting_depth == 0 &&
      main_thread_only().task_time_observers.might_have_observers()) {
    return TimeRecordingPolicy::DoRecord;
  }
  return TimeRecordingPolicy::DoNotRecord;
}

void SequenceManagerImpl::NotifyWillProcessTask(ExecutingTask* executing_task,
                                                LazyNow* time_before_task) {
  TRACE_EVENT0(TRACE_DISABLED_BY_DEFAULT("sequence_manager"),
               "SequenceManagerImpl::NotifyWillProcessTaskObservers");

  RecordCrashKeys(executing_task->pending_task);

  if (executing_task->task_queue->GetQuiescenceMonitored())
    main_thread_only().task_was_run_on_quiescence_monitored_queue = true;

  TimeRecordingPolicy recording_policy =
      ShouldRecordTaskTiming(executing_task->task_queue);
  if (recording_policy == TimeRecordingPolicy::DoRecord)
    executing_task->task_timing.RecordTaskStart(time_before_task);

  if (!executing_task->task_queue->GetShouldNotifyObservers())
    return;

  const bool was_blocked_or_low_priority =
      executing_task->task_queue->WasBlockedOrLowPriority(
          executing_task->pending_task.enqueue_order());

  {
    TRACE_EVENT0(TRACE_DISABLED_BY_DEFAULT("sequence_manager"),
                 "SequenceManager.WillProcessTaskObservers");
    for (auto& observer : main_thread_only().task_observers) {
      observer.WillProcessTask(executing_task->pending_task,
                               was_blocked_or_low_priority);
    }
  }

  {
    TRACE_EVENT0(TRACE_DISABLED_BY_DEFAULT("sequence_manager"),
                 "SequenceManager.QueueNotifyWillProcessTask");
    executing_task->task_queue->NotifyWillProcessTask(
        executing_task->pending_task, was_blocked_or_low_priority);
  }

  if (recording_policy != TimeRecordingPolicy::DoRecord)
    return;

  if (main_thread_only().nesting_depth == 0) {
    TRACE_EVENT0(TRACE_DISABLED_BY_DEFAULT("sequence_manager"),
                 "SequenceManager.WillProcessTaskTimeObservers");
    for (auto& observer : main_thread_only().task_time_observers)
      observer.WillProcessTask(executing_task->task_timing.start_time());
  }

  {
    TRACE_EVENT0(TRACE_DISABLED_BY_DEFAULT("sequence_manager"),
                 "SequenceManager.QueueOnTaskStarted");
    executing_task->task_queue->OnTaskStarted(executing_task->pending_task,
                                              executing_task->task_timing);
  }
}

void SequenceManagerImpl::NotifyDidProcessTask(ExecutingTask* executing_task,
                                               LazyNow* time_after_task) {
  TRACE_EVENT0(TRACE_DISABLED_BY_DEFAULT("sequence_manager"),
               "SequenceManagerImpl::NotifyDidProcessTaskObservers");
  if (!executing_task->task_queue->GetShouldNotifyObservers())
    return;

  TaskQueue::TaskTiming& task_timing = executing_task->task_timing;

  {
    TRACE_EVENT0(TRACE_DISABLED_BY_DEFAULT("sequence_manager"),
                 "SequenceManager.QueueOnTaskCompleted");
    if (task_timing.has_wall_time()) {
      executing_task->task_queue->OnTaskCompleted(
          executing_task->pending_task, &task_timing, time_after_task);
    }
  }

  TimeRecordingPolicy recording_policy =
      ShouldRecordTaskTiming(executing_task->task_queue);
  // Record end time ASAP to avoid bias due to the overhead of observers.
  if (recording_policy == TimeRecordingPolicy::DoRecord)
    task_timing.RecordTaskEnd(time_after_task);

  if (task_timing.has_wall_time() && main_thread_only().nesting_depth == 0) {
    TRACE_EVENT0(TRACE_DISABLED_BY_DEFAULT("sequence_manager"),
                 "SequenceManager.DidProcessTaskTimeObservers");
    for (auto& observer : main_thread_only().task_time_observers) {
      observer.DidProcessTask(task_timing.start_time(), task_timing.end_time());
    }
  }

  {
    TRACE_EVENT0(TRACE_DISABLED_BY_DEFAULT("sequence_manager"),
                 "SequenceManager.DidProcessTaskObservers");
    for (auto& observer : main_thread_only().task_observers)
      observer.DidProcessTask(executing_task->pending_task);
  }

  {
    TRACE_EVENT0(TRACE_DISABLED_BY_DEFAULT("sequence_manager"),
                 "SequenceManager.QueueNotifyDidProcessTask");
    executing_task->task_queue->NotifyDidProcessTask(
        executing_task->pending_task);
  }

  // TODO(altimin): Move this back to blink.
  if (task_timing.has_wall_time() &&
      recording_policy == TimeRecordingPolicy::DoRecord &&
      task_timing.wall_duration() > kLongTaskTraceEventThreshold &&
      main_thread_only().nesting_depth == 0) {
    TRACE_EVENT_INSTANT1("blink", "LongTask", TRACE_EVENT_SCOPE_THREAD,
                         "duration", task_timing.wall_duration().InSecondsF());
  }
}

void SequenceManagerImpl::SetWorkBatchSize(int work_batch_size) {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  DCHECK_GE(work_batch_size, 1);
  controller_->SetWorkBatchSize(work_batch_size);
}

void SequenceManagerImpl::SetTimerSlack(TimerSlack timer_slack) {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  controller_->SetTimerSlack(timer_slack);
}

void SequenceManagerImpl::AddTaskObserver(TaskObserver* task_observer) {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  main_thread_only().task_observers.AddObserver(task_observer);
}

void SequenceManagerImpl::RemoveTaskObserver(TaskObserver* task_observer) {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  main_thread_only().task_observers.RemoveObserver(task_observer);
}

void SequenceManagerImpl::AddTaskTimeObserver(
    TaskTimeObserver* task_time_observer) {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  main_thread_only().task_time_observers.AddObserver(task_time_observer);
}

void SequenceManagerImpl::RemoveTaskTimeObserver(
    TaskTimeObserver* task_time_observer) {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  main_thread_only().task_time_observers.RemoveObserver(task_time_observer);
}

bool SequenceManagerImpl::GetAndClearSystemIsQuiescentBit() {
  bool task_was_run =
      main_thread_only().task_was_run_on_quiescence_monitored_queue;
  main_thread_only().task_was_run_on_quiescence_monitored_queue = false;
  return !task_was_run;
}

EnqueueOrder SequenceManagerImpl::GetNextSequenceNumber() {
  return enqueue_order_generator_.GenerateNext();
}

std::unique_ptr<trace_event::ConvertableToTraceFormat>
SequenceManagerImpl::AsValueWithSelectorResult(
    internal::WorkQueue* selected_work_queue,
    bool force_verbose) const {
  auto state = std::make_unique<trace_event::TracedValue>();
  AsValueWithSelectorResultInto(state.get(), selected_work_queue,
                                force_verbose);
  return std::move(state);
}

void SequenceManagerImpl::AsValueWithSelectorResultInto(
    trace_event::TracedValue* state,
    internal::WorkQueue* selected_work_queue,
    bool force_verbose) const {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  TimeTicks now = NowTicks();
  state->BeginArray("active_queues");
  for (auto* const queue : main_thread_only().active_queues)
    queue->AsValueInto(now, state, force_verbose);
  state->EndArray();
  state->BeginArray("queues_to_gracefully_shutdown");
  for (const auto& pair : main_thread_only().queues_to_gracefully_shutdown)
    pair.first->AsValueInto(now, state, force_verbose);
  state->EndArray();
  state->BeginArray("queues_to_delete");
  for (const auto& pair : main_thread_only().queues_to_delete)
    pair.first->AsValueInto(now, state, force_verbose);
  state->EndArray();
  state->BeginDictionary("selector");
  main_thread_only().selector.AsValueInto(state);
  state->EndDictionary();
  if (selected_work_queue) {
    state->SetString("selected_queue",
                     selected_work_queue->task_queue()->GetName());
    state->SetString("work_queue_name", selected_work_queue->name());
  }
  state->SetString("native_work_priority",
                   TaskQueue::PriorityToString(
                       *main_thread_only().pending_native_work.begin()));

  state->BeginArray("time_domains");
  for (auto* time_domain : main_thread_only().time_domains)
    time_domain->AsValueInto(state);
  state->EndArray();
}

void SequenceManagerImpl::OnTaskQueueEnabled(internal::TaskQueueImpl* queue) {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  DCHECK(queue->IsQueueEnabled());
  // Only schedule DoWork if there's something to do.
  if (queue->HasTaskToRunImmediately() && !queue->BlockedByFence())
    ScheduleWork();
}

void SequenceManagerImpl::MaybeReclaimMemory() {
  if (!main_thread_only().memory_reclaim_scheduled)
    return;

  TRACE_EVENT0("sequence_manager", "SequenceManagerImpl::MaybeReclaimMemory");
  ReclaimMemory();

  // To avoid performance regressions we only want to do this every so often.
  main_thread_only().next_time_to_reclaim_memory =
      NowTicks() + kReclaimMemoryInterval;
  main_thread_only().memory_reclaim_scheduled = false;
}

void SequenceManagerImpl::ReclaimMemory() {
  std::map<TimeDomain*, TimeTicks> time_domain_now;
  for (auto* const queue : main_thread_only().active_queues)
    ReclaimMemoryFromQueue(queue, &time_domain_now);
  for (const auto& pair : main_thread_only().queues_to_gracefully_shutdown)
    ReclaimMemoryFromQueue(pair.first, &time_domain_now);
}

void SequenceManagerImpl::CleanUpQueues() {
  for (auto it = main_thread_only().queues_to_gracefully_shutdown.begin();
       it != main_thread_only().queues_to_gracefully_shutdown.end();) {
    if (it->first->IsEmpty()) {
      UnregisterTaskQueueImpl(std::move(it->second));
      main_thread_only().active_queues.erase(it->first);
      main_thread_only().queues_to_gracefully_shutdown.erase(it++);
    } else {
      ++it;
    }
  }
  main_thread_only().queues_to_delete.clear();
}

void SequenceManagerImpl::RemoveAllCanceledTasksFromFrontOfWorkQueues() {
  for (internal::TaskQueueImpl* queue : main_thread_only().active_queues) {
    queue->delayed_work_queue()->RemoveAllCanceledTasksFromFront();
    queue->immediate_work_queue()->RemoveAllCanceledTasksFromFront();
  }
}

WeakPtr<SequenceManagerImpl> SequenceManagerImpl::GetWeakPtr() {
  return weak_factory_.GetWeakPtr();
}

void SequenceManagerImpl::SetDefaultTaskRunner(
    scoped_refptr<SingleThreadTaskRunner> task_runner) {
  controller_->SetDefaultTaskRunner(task_runner);
}

const TickClock* SequenceManagerImpl::GetTickClock() const {
  return controller_->GetClock();
}

TimeTicks SequenceManagerImpl::NowTicks() const {
  return controller_->GetClock()->NowTicks();
}

bool SequenceManagerImpl::ShouldRecordCPUTimeForTask() {
  DCHECK(ThreadTicks::IsSupported() ||
         !metric_recording_settings_.records_cpu_time_for_some_tasks());
  return metric_recording_settings_.records_cpu_time_for_some_tasks() &&
         main_thread_only().uniform_distribution(
             main_thread_only().random_generator) <
             metric_recording_settings_
                 .task_sampling_rate_for_recording_cpu_time;
}

const SequenceManager::MetricRecordingSettings&
SequenceManagerImpl::GetMetricRecordingSettings() const {
  return metric_recording_settings_;
}

// TODO(altimin): Ensure that this removes all pending tasks.
void SequenceManagerImpl::DeletePendingTasks() {
  DCHECK(main_thread_only().task_execution_stack.empty())
      << "Tasks should be deleted outside RunLoop";

  for (TaskQueueImpl* task_queue : main_thread_only().active_queues)
    task_queue->DeletePendingTasks();
  for (const auto& it : main_thread_only().queues_to_gracefully_shutdown)
    it.first->DeletePendingTasks();
  for (const auto& it : main_thread_only().queues_to_delete)
    it.first->DeletePendingTasks();
}

bool SequenceManagerImpl::HasTasks() {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  RemoveAllCanceledTasksFromFrontOfWorkQueues();

  for (TaskQueueImpl* task_queue : main_thread_only().active_queues) {
    if (task_queue->HasTasks())
      return true;
  }
  for (const auto& it : main_thread_only().queues_to_gracefully_shutdown) {
    if (it.first->HasTasks())
      return true;
  }
  for (const auto& it : main_thread_only().queues_to_delete) {
    if (it.first->HasTasks())
      return true;
  }
  return false;
}

MessagePumpType SequenceManagerImpl::GetType() const {
  return settings_.message_loop_type;
}

void SequenceManagerImpl::SetTaskExecutionAllowed(bool allowed) {
  controller_->SetTaskExecutionAllowed(allowed);
}

bool SequenceManagerImpl::IsTaskExecutionAllowed() const {
  return controller_->IsTaskExecutionAllowed();
}

#if defined(OS_IOS)
void SequenceManagerImpl::AttachToMessagePump() {
  return controller_->AttachToMessagePump();
}
#endif

bool SequenceManagerImpl::IsIdleForTesting() {
  ReloadEmptyWorkQueues();
  RemoveAllCanceledTasksFromFrontOfWorkQueues();
  return !main_thread_only().selector.GetHighestPendingPriority().has_value();
}

size_t SequenceManagerImpl::GetPendingTaskCountForTesting() const {
  size_t total = 0;
  for (internal::TaskQueueImpl* task_queue : main_thread_only().active_queues) {
    total += task_queue->GetNumberOfPendingTasks();
  }
  return total;
}

scoped_refptr<TaskQueue> SequenceManagerImpl::CreateTaskQueue(
    const TaskQueue::Spec& spec) {
  return WrapRefCounted(new TaskQueue(CreateTaskQueueImpl(spec), spec));
}

std::string SequenceManagerImpl::DescribeAllPendingTasks() const {
  trace_event::TracedValueJSON value;
  AsValueWithSelectorResultInto(&value, nullptr, /* force_verbose */ true);
  return value.ToJSON();
}

std::unique_ptr<NativeWorkHandle> SequenceManagerImpl::OnNativeWorkPending(
    TaskQueue::QueuePriority priority) {
  return std::make_unique<NativeWorkHandleImpl>(this, priority);
}

void SequenceManagerImpl::AddDestructionObserver(
    MessageLoopCurrent::DestructionObserver* destruction_observer) {
  main_thread_only().destruction_observers.AddObserver(destruction_observer);
}

void SequenceManagerImpl::RemoveDestructionObserver(
    MessageLoopCurrent::DestructionObserver* destruction_observer) {
  main_thread_only().destruction_observers.RemoveObserver(destruction_observer);
}

void SequenceManagerImpl::SetTaskRunner(
    scoped_refptr<SingleThreadTaskRunner> task_runner) {
  controller_->SetDefaultTaskRunner(task_runner);
}

scoped_refptr<SingleThreadTaskRunner> SequenceManagerImpl::GetTaskRunner() {
  return controller_->GetDefaultTaskRunner();
}

bool SequenceManagerImpl::IsBoundToCurrentThread() const {
  return associated_thread_->IsBoundToCurrentThread();
}

MessagePump* SequenceManagerImpl::GetMessagePump() const {
  return controller_->GetBoundMessagePump();
}

bool SequenceManagerImpl::IsType(MessagePumpType type) const {
  return settings_.message_loop_type == type;
}

NOINLINE bool SequenceManagerImpl::Validate() {
  return memory_corruption_sentinel_ == kMemoryCorruptionSentinelValue;
}

void SequenceManagerImpl::EnableCrashKeys(const char* async_stack_crash_key) {
  DCHECK(!main_thread_only().async_stack_crash_key);
#if !defined(OS_NACL)
  main_thread_only().async_stack_crash_key = debug::AllocateCrashKeyString(
      async_stack_crash_key, debug::CrashKeySize::Size64);
  static_assert(sizeof(main_thread_only().async_stack_buffer) ==
                    static_cast<size_t>(debug::CrashKeySize::Size64),
                "Async stack buffer size must match crash key size.");
#endif  // OS_NACL
}

void SequenceManagerImpl::RecordCrashKeys(const PendingTask& pending_task) {
#if !defined(OS_NACL)
  // SetCrashKeyString is a no-op even if the crash key is null, but we'd still
  // have construct the StringPiece that is passed in.
  if (!main_thread_only().async_stack_crash_key)
    return;

  // Write the async stack trace onto a crash key as whitespace-delimited hex
  // addresses. These will be symbolized by the crash reporting system. With
  // 63 characters we can fit the address of the task that posted the current
  // task and its predecessor. Avoid HexEncode since it incurs a memory
  // allocation and snprintf because it's about 3.5x slower on Android this
  // this.
  //
  // See
  // https://chromium.googlesource.com/chromium/src/+/master/docs/debugging_with_crash_keys.md
  // for instructions for symbolizing these crash keys.
  //
  // TODO(skyostil): Find a way to extract the destination function address
  // from the task.
  size_t max_size = main_thread_only().async_stack_buffer.size();
  char* const buffer = &main_thread_only().async_stack_buffer[0];
  char* const buffer_end = &buffer[max_size - 1];
  char* pos = buffer_end;
  // Leave space for the NUL terminator.
  pos = PrependHexAddress(pos - 1, pending_task.task_backtrace[0]);
  *(--pos) = ' ';
  pos = PrependHexAddress(pos - 1, pending_task.posted_from.program_counter());
  DCHECK_GE(pos, buffer);
  debug::SetCrashKeyString(main_thread_only().async_stack_crash_key,
                           StringPiece(pos, buffer_end - pos));
#endif  // OS_NACL
}

internal::TaskQueueImpl* SequenceManagerImpl::currently_executing_task_queue()
    const {
  if (main_thread_only().task_execution_stack.empty())
    return nullptr;
  return main_thread_only().task_execution_stack.rbegin()->task_queue;
}

constexpr TimeDelta SequenceManagerImpl::kReclaimMemoryInterval;

}  // namespace internal
}  // namespace sequence_manager
}  // namespace base
