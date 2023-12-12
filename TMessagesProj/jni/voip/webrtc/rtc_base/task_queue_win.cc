/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/task_queue_win.h"

// clang-format off
// clang formating would change include order.

// Include winsock2.h before including <windows.h> to maintain consistency with
// win32.h. To include win32.h directly, it must be broken out into its own
// build target.
#include <winsock2.h>
#include <windows.h>
#include <sal.h>       // Must come after windows headers.
#include <mmsystem.h>  // Must come after windows headers.
// clang-format on
#include <string.h>

#include <algorithm>
#include <functional>
#include <memory>
#include <queue>
#include <utility>

#include "absl/functional/any_invocable.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/task_queue/task_queue_base.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "rtc_base/arraysize.h"
#include "rtc_base/checks.h"
#include "rtc_base/event.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/platform_thread.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/time_utils.h"

namespace webrtc {
namespace {
#define WM_QUEUE_DELAYED_TASK WM_USER + 2

void CALLBACK InitializeQueueThread(ULONG_PTR param) {
  MSG msg;
  ::PeekMessage(&msg, nullptr, WM_USER, WM_USER, PM_NOREMOVE);
  rtc::Event* data = reinterpret_cast<rtc::Event*>(param);
  data->Set();
}

rtc::ThreadPriority TaskQueuePriorityToThreadPriority(
    TaskQueueFactory::Priority priority) {
  switch (priority) {
    case TaskQueueFactory::Priority::HIGH:
      return rtc::ThreadPriority::kRealtime;
    case TaskQueueFactory::Priority::LOW:
      return rtc::ThreadPriority::kLow;
    case TaskQueueFactory::Priority::NORMAL:
      return rtc::ThreadPriority::kNormal;
  }
}

Timestamp CurrentTime() {
  static const UINT kPeriod = 1;
  bool high_res = (timeBeginPeriod(kPeriod) == TIMERR_NOERROR);
  Timestamp ret = Timestamp::Micros(rtc::TimeMicros());
  if (high_res)
    timeEndPeriod(kPeriod);
  return ret;
}

class DelayedTaskInfo {
 public:
  // Default ctor needed to support priority_queue::pop().
  DelayedTaskInfo() {}
  DelayedTaskInfo(TimeDelta delay, absl::AnyInvocable<void() &&> task)
      : due_time_(CurrentTime() + delay), task_(std::move(task)) {}
  DelayedTaskInfo(DelayedTaskInfo&&) = default;

  // Implement for priority_queue.
  bool operator>(const DelayedTaskInfo& other) const {
    return due_time_ > other.due_time_;
  }

  // Required by priority_queue::pop().
  DelayedTaskInfo& operator=(DelayedTaskInfo&& other) = default;

  // See below for why this method is const.
  void Run() const {
    RTC_DCHECK(task_);
    std::move(task_)();
  }

  Timestamp due_time() const { return due_time_; }

 private:
  Timestamp due_time_ = Timestamp::Zero();

  // `task` needs to be mutable because std::priority_queue::top() returns
  // a const reference and a key in an ordered queue must not be changed.
  // There are two basic workarounds, one using const_cast, which would also
  // make the key (`due_time`), non-const and the other is to make the non-key
  // (`task`), mutable.
  // Because of this, the `task` variable is made private and can only be
  // mutated by calling the `Run()` method.
  mutable absl::AnyInvocable<void() &&> task_;
};

class MultimediaTimer {
 public:
  // Note: We create an event that requires manual reset.
  MultimediaTimer() : event_(::CreateEvent(nullptr, true, false, nullptr)) {}

  ~MultimediaTimer() {
    Cancel();
    ::CloseHandle(event_);
  }

  MultimediaTimer(const MultimediaTimer&) = delete;
  MultimediaTimer& operator=(const MultimediaTimer&) = delete;

  bool StartOneShotTimer(UINT delay_ms) {
    RTC_DCHECK_EQ(0, timer_id_);
    RTC_DCHECK(event_ != nullptr);
    timer_id_ =
        ::timeSetEvent(delay_ms, 0, reinterpret_cast<LPTIMECALLBACK>(event_), 0,
                       TIME_ONESHOT | TIME_CALLBACK_EVENT_SET);
    return timer_id_ != 0;
  }

  void Cancel() {
    if (timer_id_) {
      ::timeKillEvent(timer_id_);
      timer_id_ = 0;
    }
    // Now that timer is killed and not able to set the event, reset the event.
    // Doing it in opposite order is racy because event may be set between
    // event was reset and timer is killed leaving MultimediaTimer in surprising
    // state where both event is set and timer is canceled.
    ::ResetEvent(event_);
  }

  HANDLE* event_for_wait() { return &event_; }

 private:
  HANDLE event_ = nullptr;
  MMRESULT timer_id_ = 0;
};

class TaskQueueWin : public TaskQueueBase {
 public:
  TaskQueueWin(absl::string_view queue_name, rtc::ThreadPriority priority);
  ~TaskQueueWin() override = default;

  void Delete() override;
  void PostTask(absl::AnyInvocable<void() &&> task) override;
  void PostDelayedTask(absl::AnyInvocable<void() &&> task,
                       TimeDelta delay) override;
  void PostDelayedHighPrecisionTask(absl::AnyInvocable<void() &&> task,
                                    TimeDelta delay) override;
  void RunPendingTasks();

 private:
  void RunThreadMain();
  bool ProcessQueuedMessages();
  void RunDueTasks();
  void ScheduleNextTimer();
  void CancelTimers();

  MultimediaTimer timer_;
  // Since priority_queue<> by defult orders items in terms of
  // largest->smallest, using std::less<>, and we want smallest->largest,
  // we would like to use std::greater<> here.
  std::priority_queue<DelayedTaskInfo,
                      std::vector<DelayedTaskInfo>,
                      std::greater<DelayedTaskInfo>>
      timer_tasks_;
  UINT_PTR timer_id_ = 0;
  rtc::PlatformThread thread_;
  Mutex pending_lock_;
  std::queue<absl::AnyInvocable<void() &&>> pending_
      RTC_GUARDED_BY(pending_lock_);
  HANDLE in_queue_;
};

TaskQueueWin::TaskQueueWin(absl::string_view queue_name,
                           rtc::ThreadPriority priority)
    : in_queue_(::CreateEvent(nullptr, true, false, nullptr)) {
  RTC_DCHECK(in_queue_);
  thread_ = rtc::PlatformThread::SpawnJoinable(
      [this] { RunThreadMain(); }, queue_name,
      rtc::ThreadAttributes().SetPriority(priority));

  rtc::Event event(false, false);
  RTC_CHECK(thread_.QueueAPC(&InitializeQueueThread,
                             reinterpret_cast<ULONG_PTR>(&event)));
  event.Wait(rtc::Event::kForever);
}

void TaskQueueWin::Delete() {
  RTC_DCHECK(!IsCurrent());
  RTC_CHECK(thread_.GetHandle() != absl::nullopt);
  while (
      !::PostThreadMessage(GetThreadId(*thread_.GetHandle()), WM_QUIT, 0, 0)) {
    RTC_CHECK_EQ(ERROR_NOT_ENOUGH_QUOTA, ::GetLastError());
    Sleep(1);
  }
  thread_.Finalize();
  ::CloseHandle(in_queue_);
  delete this;
}

void TaskQueueWin::PostTask(absl::AnyInvocable<void() &&> task) {
  MutexLock lock(&pending_lock_);
  pending_.push(std::move(task));
  ::SetEvent(in_queue_);
}

void TaskQueueWin::PostDelayedTask(absl::AnyInvocable<void() &&> task,
                                   TimeDelta delay) {
  if (delay <= TimeDelta::Zero()) {
    PostTask(std::move(task));
    return;
  }

  auto* task_info = new DelayedTaskInfo(delay, std::move(task));
  RTC_CHECK(thread_.GetHandle() != absl::nullopt);
  if (!::PostThreadMessage(GetThreadId(*thread_.GetHandle()),
                           WM_QUEUE_DELAYED_TASK, 0,
                           reinterpret_cast<LPARAM>(task_info))) {
    delete task_info;
  }
}

void TaskQueueWin::PostDelayedHighPrecisionTask(
    absl::AnyInvocable<void() &&> task,
    TimeDelta delay) {
  PostDelayedTask(std::move(task), delay);
}

void TaskQueueWin::RunPendingTasks() {
  while (true) {
    absl::AnyInvocable<void() &&> task;
    {
      MutexLock lock(&pending_lock_);
      if (pending_.empty())
        break;
      task = std::move(pending_.front());
      pending_.pop();
    }

    std::move(task)();
  }
}

void TaskQueueWin::RunThreadMain() {
  CurrentTaskQueueSetter set_current(this);
  HANDLE handles[2] = {*timer_.event_for_wait(), in_queue_};
  while (true) {
    // Make sure we do an alertable wait as that's required to allow APCs to run
    // (e.g. required for InitializeQueueThread and stopping the thread in
    // PlatformThread).
    DWORD result = ::MsgWaitForMultipleObjectsEx(
        arraysize(handles), handles, INFINITE, QS_ALLEVENTS, MWMO_ALERTABLE);
    RTC_CHECK_NE(WAIT_FAILED, result);
    if (result == (WAIT_OBJECT_0 + 2)) {
      // There are messages in the message queue that need to be handled.
      if (!ProcessQueuedMessages())
        break;
    }

    if (result == WAIT_OBJECT_0 ||
        (!timer_tasks_.empty() &&
         ::WaitForSingleObject(*timer_.event_for_wait(), 0) == WAIT_OBJECT_0)) {
      // The multimedia timer was signaled.
      timer_.Cancel();
      RunDueTasks();
      ScheduleNextTimer();
    }

    if (result == (WAIT_OBJECT_0 + 1)) {
      ::ResetEvent(in_queue_);
      RunPendingTasks();
    }
  }
}

bool TaskQueueWin::ProcessQueuedMessages() {
  MSG msg = {};
  // To protect against overly busy message queues, we limit the time
  // we process tasks to a few milliseconds. If we don't do that, there's
  // a chance that timer tasks won't ever run.
  static constexpr TimeDelta kMaxTaskProcessingTime = TimeDelta::Millis(500);
  Timestamp start = CurrentTime();
  while (::PeekMessage(&msg, nullptr, 0, 0, PM_REMOVE) &&
         msg.message != WM_QUIT) {
    if (!msg.hwnd) {
      switch (msg.message) {
        case WM_QUEUE_DELAYED_TASK: {
          std::unique_ptr<DelayedTaskInfo> info(
              reinterpret_cast<DelayedTaskInfo*>(msg.lParam));
          bool need_to_schedule_timers =
              timer_tasks_.empty() ||
              timer_tasks_.top().due_time() > info->due_time();
          timer_tasks_.push(std::move(*info));
          if (need_to_schedule_timers) {
            CancelTimers();
            ScheduleNextTimer();
          }
          break;
        }
        case WM_TIMER: {
          RTC_DCHECK_EQ(timer_id_, msg.wParam);
          ::KillTimer(nullptr, msg.wParam);
          timer_id_ = 0;
          RunDueTasks();
          ScheduleNextTimer();
          break;
        }
        default:
          RTC_DCHECK_NOTREACHED();
          break;
      }
    } else {
      ::TranslateMessage(&msg);
      ::DispatchMessage(&msg);
    }

    if (CurrentTime() > start + kMaxTaskProcessingTime)
      break;
  }
  return msg.message != WM_QUIT;
}

void TaskQueueWin::RunDueTasks() {
  RTC_DCHECK(!timer_tasks_.empty());
  Timestamp now = CurrentTime();
  do {
    const auto& top = timer_tasks_.top();
    if (top.due_time() > now)
      break;
    top.Run();
    timer_tasks_.pop();
  } while (!timer_tasks_.empty());
}

void TaskQueueWin::ScheduleNextTimer() {
  RTC_DCHECK_EQ(timer_id_, 0);
  if (timer_tasks_.empty())
    return;

  const auto& next_task = timer_tasks_.top();
  TimeDelta delay =
      std::max(TimeDelta::Zero(), next_task.due_time() - CurrentTime());
  uint32_t milliseconds = delay.RoundUpTo(TimeDelta::Millis(1)).ms<uint32_t>();
  if (!timer_.StartOneShotTimer(milliseconds))
    timer_id_ = ::SetTimer(nullptr, 0, milliseconds, nullptr);
}

void TaskQueueWin::CancelTimers() {
  timer_.Cancel();
  if (timer_id_) {
    ::KillTimer(nullptr, timer_id_);
    timer_id_ = 0;
  }
}

class TaskQueueWinFactory : public TaskQueueFactory {
 public:
  std::unique_ptr<TaskQueueBase, TaskQueueDeleter> CreateTaskQueue(
      absl::string_view name,
      Priority priority) const override {
    return std::unique_ptr<TaskQueueBase, TaskQueueDeleter>(
        new TaskQueueWin(name, TaskQueuePriorityToThreadPriority(priority)));
  }
};

}  // namespace

std::unique_ptr<TaskQueueFactory> CreateTaskQueueWinFactory() {
  return std::make_unique<TaskQueueWinFactory>();
}

}  // namespace webrtc
