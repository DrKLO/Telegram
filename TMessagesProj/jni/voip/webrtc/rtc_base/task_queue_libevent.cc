/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/task_queue_libevent.h"

#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <signal.h>
#include <stdint.h>
#include <time.h>
#include <unistd.h>

#include <list>
#include <memory>
#include <type_traits>
#include <utility>

#include "absl/container/inlined_vector.h"
#include "absl/functional/any_invocable.h"
#include "absl/strings/string_view.h"
#include "api/task_queue/task_queue_base.h"
#include "api/units/time_delta.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/platform_thread.h"
#include "rtc_base/platform_thread_types.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/thread_annotations.h"
#include "rtc_base/time_utils.h"
#include "base/third_party/libevent/event.h"

namespace webrtc {
namespace {
constexpr char kQuit = 1;
constexpr char kRunTasks = 2;

using Priority = TaskQueueFactory::Priority;

// This ignores the SIGPIPE signal on the calling thread.
// This signal can be fired when trying to write() to a pipe that's being
// closed or while closing a pipe that's being written to.
// We can run into that situation so we ignore this signal and continue as
// normal.
// As a side note for this implementation, it would be great if we could safely
// restore the sigmask, but unfortunately the operation of restoring it, can
// itself actually cause SIGPIPE to be signaled :-| (e.g. on MacOS)
// The SIGPIPE signal by default causes the process to be terminated, so we
// don't want to risk that.
// An alternative to this approach is to ignore the signal for the whole
// process:
//   signal(SIGPIPE, SIG_IGN);
void IgnoreSigPipeSignalOnCurrentThread() {
  sigset_t sigpipe_mask;
  sigemptyset(&sigpipe_mask);
  sigaddset(&sigpipe_mask, SIGPIPE);
  pthread_sigmask(SIG_BLOCK, &sigpipe_mask, nullptr);
}

bool SetNonBlocking(int fd) {
  const int flags = fcntl(fd, F_GETFL);
  RTC_CHECK(flags != -1);
  return (flags & O_NONBLOCK) || fcntl(fd, F_SETFL, flags | O_NONBLOCK) != -1;
}

// TODO(tommi): This is a hack to support two versions of libevent that we're
// compatible with.  The method we really want to call is event_assign(),
// since event_set() has been marked as deprecated (and doesn't accept
// passing event_base__ as a parameter).  However, the version of libevent
// that we have in Chromium, doesn't have event_assign(), so we need to call
// event_set() there.
void EventAssign(struct event* ev,
                 struct event_base* base,
                 int fd,
                 short events,
                 void (*callback)(int, short, void*),
                 void* arg) {
#if defined(_EVENT2_EVENT_H_)
  RTC_CHECK_EQ(0, event_assign(ev, base, fd, events, callback, arg));
#else
  event_set(ev, fd, events, callback, arg);
  RTC_CHECK_EQ(0, event_base_set(base, ev));
#endif
}

rtc::ThreadPriority TaskQueuePriorityToThreadPriority(Priority priority) {
  switch (priority) {
    case Priority::HIGH:
      return rtc::ThreadPriority::kRealtime;
    case Priority::LOW:
      return rtc::ThreadPriority::kLow;
    case Priority::NORMAL:
      return rtc::ThreadPriority::kNormal;
  }
}

class TaskQueueLibevent final : public TaskQueueBase {
 public:
  TaskQueueLibevent(absl::string_view queue_name, rtc::ThreadPriority priority);

  void Delete() override;
  void PostTask(absl::AnyInvocable<void() &&> task) override;
  void PostDelayedTask(absl::AnyInvocable<void() &&> task,
                       TimeDelta delay) override;
  void PostDelayedHighPrecisionTask(absl::AnyInvocable<void() &&> task,
                                    TimeDelta delay) override;

 private:
  struct TimerEvent;

  void PostDelayedTaskOnTaskQueue(absl::AnyInvocable<void() &&> task,
                                  TimeDelta delay);

  ~TaskQueueLibevent() override = default;

  static void OnWakeup(int socket, short flags, void* context);  // NOLINT
  static void RunTimer(int fd, short flags, void* context);      // NOLINT

  bool is_active_ = true;
  int wakeup_pipe_in_ = -1;
  int wakeup_pipe_out_ = -1;
  event_base* event_base_;
  event wakeup_event_;
  rtc::PlatformThread thread_;
  Mutex pending_lock_;
  absl::InlinedVector<absl::AnyInvocable<void() &&>, 4> pending_
      RTC_GUARDED_BY(pending_lock_);
  // Holds a list of events pending timers for cleanup when the loop exits.
  std::list<TimerEvent*> pending_timers_;
};

struct TaskQueueLibevent::TimerEvent {
  TimerEvent(TaskQueueLibevent* task_queue, absl::AnyInvocable<void() &&> task)
      : task_queue(task_queue), task(std::move(task)) {}
  ~TimerEvent() { event_del(&ev); }

  event ev;
  TaskQueueLibevent* task_queue;
  absl::AnyInvocable<void() &&> task;
};

TaskQueueLibevent::TaskQueueLibevent(absl::string_view queue_name,
                                     rtc::ThreadPriority priority)
    : event_base_(event_base_new()) {
  int fds[2];
  RTC_CHECK(pipe(fds) == 0);
  SetNonBlocking(fds[0]);
  SetNonBlocking(fds[1]);
  wakeup_pipe_out_ = fds[0];
  wakeup_pipe_in_ = fds[1];

  EventAssign(&wakeup_event_, event_base_, wakeup_pipe_out_,
              EV_READ | EV_PERSIST, OnWakeup, this);
  event_add(&wakeup_event_, 0);
  thread_ = rtc::PlatformThread::SpawnJoinable(
      [this] {
        {
          CurrentTaskQueueSetter set_current(this);
          while (is_active_)
            event_base_loop(event_base_, 0);
        }

        for (TimerEvent* timer : pending_timers_)
          delete timer;
      },
      queue_name, rtc::ThreadAttributes().SetPriority(priority));
}

void TaskQueueLibevent::Delete() {
  RTC_DCHECK(!IsCurrent());
  struct timespec ts;
  char message = kQuit;
  while (write(wakeup_pipe_in_, &message, sizeof(message)) != sizeof(message)) {
    // The queue is full, so we have no choice but to wait and retry.
    RTC_CHECK_EQ(EAGAIN, errno);
    ts.tv_sec = 0;
    ts.tv_nsec = 1000000;
    nanosleep(&ts, nullptr);
  }

  thread_.Finalize();

  event_del(&wakeup_event_);

  IgnoreSigPipeSignalOnCurrentThread();

  close(wakeup_pipe_in_);
  close(wakeup_pipe_out_);
  wakeup_pipe_in_ = -1;
  wakeup_pipe_out_ = -1;

  event_base_free(event_base_);
  delete this;
}

void TaskQueueLibevent::PostTask(absl::AnyInvocable<void() &&> task) {
  {
    MutexLock lock(&pending_lock_);
    bool had_pending_tasks = !pending_.empty();
    pending_.push_back(std::move(task));

    // Only write to the pipe if there were no pending tasks before this one
    // since the thread could be sleeping. If there were already pending tasks
    // then we know there's either a pending write in the pipe or the thread has
    // not yet processed the pending tasks. In either case, the thread will
    // eventually wake up and process all pending tasks including this one.
    if (had_pending_tasks) {
      return;
    }
  }

  // Note: This behvior outlined above ensures we never fill up the pipe write
  // buffer since there will only ever be 1 byte pending.
  char message = kRunTasks;
  RTC_CHECK_EQ(write(wakeup_pipe_in_, &message, sizeof(message)),
               sizeof(message));
}

void TaskQueueLibevent::PostDelayedTaskOnTaskQueue(
    absl::AnyInvocable<void() &&> task,
    TimeDelta delay) {
  // libevent api is not thread safe by default, thus event_add need to be
  // called on the `thread_`.
  RTC_DCHECK(IsCurrent());

  TimerEvent* timer = new TimerEvent(this, std::move(task));
  EventAssign(&timer->ev, event_base_, -1, 0, &TaskQueueLibevent::RunTimer,
              timer);
  pending_timers_.push_back(timer);
  timeval tv = {.tv_sec = rtc::dchecked_cast<int>(delay.us() / 1'000'000),
                .tv_usec = rtc::dchecked_cast<int>(delay.us() % 1'000'000)};
  event_add(&timer->ev, &tv);
}

void TaskQueueLibevent::PostDelayedTask(absl::AnyInvocable<void() &&> task,
                                        TimeDelta delay) {
  if (IsCurrent()) {
    PostDelayedTaskOnTaskQueue(std::move(task), delay);
  } else {
    int64_t posted_us = rtc::TimeMicros();
    PostTask([posted_us, delay, task = std::move(task), this]() mutable {
      // Compensate for the time that has passed since the posting.
      TimeDelta post_time = TimeDelta::Micros(rtc::TimeMicros() - posted_us);
      PostDelayedTaskOnTaskQueue(
          std::move(task), std::max(delay - post_time, TimeDelta::Zero()));
    });
  }
}

void TaskQueueLibevent::PostDelayedHighPrecisionTask(
    absl::AnyInvocable<void() &&> task,
    TimeDelta delay) {
  PostDelayedTask(std::move(task), delay);
}

// static
void TaskQueueLibevent::OnWakeup(int socket,
                                 short flags,  // NOLINT
                                 void* context) {
  TaskQueueLibevent* me = static_cast<TaskQueueLibevent*>(context);
  RTC_DCHECK(me->wakeup_pipe_out_ == socket);
  char buf;
  RTC_CHECK(sizeof(buf) == read(socket, &buf, sizeof(buf)));
  switch (buf) {
    case kQuit:
      me->is_active_ = false;
      event_base_loopbreak(me->event_base_);
      break;
    case kRunTasks: {
      absl::InlinedVector<absl::AnyInvocable<void() &&>, 4> tasks;
      {
        MutexLock lock(&me->pending_lock_);
        tasks.swap(me->pending_);
      }
      RTC_DCHECK(!tasks.empty());
      for (auto& task : tasks) {
        std::move(task)();
        // Prefer to delete the `task` before running the next one.
        task = nullptr;
      }
      break;
    }
    default:
      RTC_DCHECK_NOTREACHED();
      break;
  }
}

// static
void TaskQueueLibevent::RunTimer(int fd,
                                 short flags,  // NOLINT
                                 void* context) {
  TimerEvent* timer = static_cast<TimerEvent*>(context);
  std::move(timer->task)();
  timer->task_queue->pending_timers_.remove(timer);
  delete timer;
}

class TaskQueueLibeventFactory final : public TaskQueueFactory {
 public:
  std::unique_ptr<TaskQueueBase, TaskQueueDeleter> CreateTaskQueue(
      absl::string_view name,
      Priority priority) const override {
    return std::unique_ptr<TaskQueueBase, TaskQueueDeleter>(
        new TaskQueueLibevent(name,
                              TaskQueuePriorityToThreadPriority(priority)));
  }
};

}  // namespace

std::unique_ptr<TaskQueueFactory> CreateTaskQueueLibeventFactory() {
  return std::make_unique<TaskQueueLibeventFactory>();
}

}  // namespace webrtc
