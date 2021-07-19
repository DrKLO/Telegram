// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/message_loop/message_pump_libevent.h"

#include <errno.h>
#include <unistd.h>

#include <utility>

#include "base/auto_reset.h"
#include "base/compiler_specific.h"
#include "base/files/file_util.h"
#include "base/logging.h"
#include "base/posix/eintr_wrapper.h"
#include "base/third_party/libevent/event.h"
#include "base/time/time.h"
#include "base/trace_event/trace_event.h"
#include "build/build_config.h"

#if defined(OS_MACOSX)
#include "base/mac/scoped_nsautorelease_pool.h"
#endif

// Lifecycle of struct event
// Libevent uses two main data structures:
// struct event_base (of which there is one per message pump), and
// struct event (of which there is roughly one per socket).
// The socket's struct event is created in
// MessagePumpLibevent::WatchFileDescriptor(),
// is owned by the FdWatchController, and is destroyed in
// StopWatchingFileDescriptor().
// It is moved into and out of lists in struct event_base by
// the libevent functions event_add() and event_del().

namespace base {

MessagePumpLibevent::FdWatchController::FdWatchController(
    const Location& from_here)
    : FdWatchControllerInterface(from_here) {}

MessagePumpLibevent::FdWatchController::~FdWatchController() {
  if (event_) {
    CHECK(StopWatchingFileDescriptor());
  }
  if (was_destroyed_) {
    DCHECK(!*was_destroyed_);
    *was_destroyed_ = true;
  }
}

bool MessagePumpLibevent::FdWatchController::StopWatchingFileDescriptor() {
  std::unique_ptr<event> e = ReleaseEvent();
  if (!e)
    return true;

  // event_del() is a no-op if the event isn't active.
  int rv = event_del(e.get());
  pump_ = nullptr;
  watcher_ = nullptr;
  return (rv == 0);
}

void MessagePumpLibevent::FdWatchController::Init(std::unique_ptr<event> e) {
  DCHECK(e);
  DCHECK(!event_);

  event_ = std::move(e);
}

std::unique_ptr<event> MessagePumpLibevent::FdWatchController::ReleaseEvent() {
  return std::move(event_);
}

void MessagePumpLibevent::FdWatchController::OnFileCanReadWithoutBlocking(
    int fd,
    MessagePumpLibevent* pump) {
  // Since OnFileCanWriteWithoutBlocking() gets called first, it can stop
  // watching the file descriptor.
  if (!watcher_)
    return;
  watcher_->OnFileCanReadWithoutBlocking(fd);
}

void MessagePumpLibevent::FdWatchController::OnFileCanWriteWithoutBlocking(
    int fd,
    MessagePumpLibevent* pump) {
  DCHECK(watcher_);
  watcher_->OnFileCanWriteWithoutBlocking(fd);
}

MessagePumpLibevent::MessagePumpLibevent()
    : keep_running_(true),
      in_run_(false),
      processed_io_events_(false),
      event_base_(event_base_new()),
      wakeup_pipe_in_(-1),
      wakeup_pipe_out_(-1) {
  if (!Init())
    NOTREACHED();
}

MessagePumpLibevent::~MessagePumpLibevent() {
  DCHECK(wakeup_event_);
  DCHECK(event_base_);
  event_del(wakeup_event_);
  delete wakeup_event_;
  if (wakeup_pipe_in_ >= 0) {
    if (IGNORE_EINTR(close(wakeup_pipe_in_)) < 0)
      DPLOG(ERROR) << "close";
  }
  if (wakeup_pipe_out_ >= 0) {
    if (IGNORE_EINTR(close(wakeup_pipe_out_)) < 0)
      DPLOG(ERROR) << "close";
  }
  event_base_free(event_base_);
}

bool MessagePumpLibevent::WatchFileDescriptor(int fd,
                                              bool persistent,
                                              int mode,
                                              FdWatchController* controller,
                                              FdWatcher* delegate) {
  DCHECK_GE(fd, 0);
  DCHECK(controller);
  DCHECK(delegate);
  DCHECK(mode == WATCH_READ || mode == WATCH_WRITE || mode == WATCH_READ_WRITE);
  // WatchFileDescriptor should be called on the pump thread. It is not
  // threadsafe, and your watcher may never be registered.
  DCHECK(watch_file_descriptor_caller_checker_.CalledOnValidThread());

  TRACE_EVENT_WITH_FLOW1(TRACE_DISABLED_BY_DEFAULT("toplevel.flow"),
                         "MessagePumpLibevent::WatchFileDescriptor",
                         reinterpret_cast<uintptr_t>(controller) ^ fd,
                         TRACE_EVENT_FLAG_FLOW_OUT, "fd", fd);

  int event_mask = persistent ? EV_PERSIST : 0;
  if (mode & WATCH_READ) {
    event_mask |= EV_READ;
  }
  if (mode & WATCH_WRITE) {
    event_mask |= EV_WRITE;
  }

  std::unique_ptr<event> evt(controller->ReleaseEvent());
  if (!evt) {
    // Ownership is transferred to the controller.
    evt.reset(new event);
  } else {
    // Make sure we don't pick up any funky internal libevent masks.
    int old_interest_mask = evt->ev_events & (EV_READ | EV_WRITE | EV_PERSIST);

    // Combine old/new event masks.
    event_mask |= old_interest_mask;

    // Must disarm the event before we can reuse it.
    event_del(evt.get());

    // It's illegal to use this function to listen on 2 separate fds with the
    // same |controller|.
    if (EVENT_FD(evt.get()) != fd) {
      NOTREACHED() << "FDs don't match" << EVENT_FD(evt.get()) << "!=" << fd;
      return false;
    }
  }

  // Set current interest mask and message pump for this event.
  event_set(evt.get(), fd, event_mask, OnLibeventNotification, controller);

  // Tell libevent which message pump this socket will belong to when we add it.
  if (event_base_set(event_base_, evt.get())) {
    DPLOG(ERROR) << "event_base_set(fd=" << EVENT_FD(evt.get()) << ")";
    return false;
  }

  // Add this socket to the list of monitored sockets.
  if (event_add(evt.get(), nullptr)) {
    DPLOG(ERROR) << "event_add failed(fd=" << EVENT_FD(evt.get()) << ")";
    return false;
  }

  controller->Init(std::move(evt));
  controller->set_watcher(delegate);
  controller->set_pump(this);
  return true;
}

// Tell libevent to break out of inner loop.
static void timer_callback(int fd, short events, void* context) {
  event_base_loopbreak((struct event_base*)context);
}

// Reentrant!
void MessagePumpLibevent::Run(Delegate* delegate) {
  AutoReset<bool> auto_reset_keep_running(&keep_running_, true);
  AutoReset<bool> auto_reset_in_run(&in_run_, true);

  // event_base_loopexit() + EVLOOP_ONCE is leaky, see http://crbug.com/25641.
  // Instead, make our own timer and reuse it on each call to event_base_loop().
  std::unique_ptr<event> timer_event(new event);

  for (;;) {
#if defined(OS_MACOSX)
    mac::ScopedNSAutoreleasePool autorelease_pool;
#endif
    // Do some work and see if the next task is ready right away.
    Delegate::NextWorkInfo next_work_info = delegate->DoSomeWork();
    bool immediate_work_available = next_work_info.is_immediate();

    if (!keep_running_)
      break;

    // Process native events if any are ready. Do not block waiting for more.
    delegate->BeforeDoInternalWork();
    event_base_loop(event_base_, EVLOOP_NONBLOCK);

    bool attempt_more_work = immediate_work_available || processed_io_events_;
    processed_io_events_ = false;

    if (!keep_running_)
      break;

    if (attempt_more_work)
      continue;

    attempt_more_work = delegate->DoIdleWork();

    if (!keep_running_)
      break;

    if (attempt_more_work)
      continue;

    bool did_set_timer = false;

    // If there is delayed work.
    DCHECK(!next_work_info.delayed_run_time.is_null());
    if (!next_work_info.delayed_run_time.is_max()) {
      const TimeDelta delay = next_work_info.remaining_delay();

      // Setup a timer to break out of the event loop at the right time.
      struct timeval poll_tv;
      poll_tv.tv_sec = delay.InSeconds();
      poll_tv.tv_usec = delay.InMicroseconds() % Time::kMicrosecondsPerSecond;
      event_set(timer_event.get(), -1, 0, timer_callback, event_base_);
      event_base_set(event_base_, timer_event.get());
      event_add(timer_event.get(), &poll_tv);

      did_set_timer = true;
    }

    // Block waiting for events and process all available upon waking up. This
    // is conditionally interrupted to look for more work if we are aware of a
    // delayed task that will need servicing.
    delegate->BeforeWait();
    event_base_loop(event_base_, EVLOOP_ONCE);

    // We previously setup a timer to break out the event loop to look for more
    // work. Now that we're here delete the event.
    if (did_set_timer) {
      event_del(timer_event.get());
    }

    if (!keep_running_)
      break;
  }
}

void MessagePumpLibevent::Quit() {
  DCHECK(in_run_) << "Quit was called outside of Run!";
  // Tell both libevent and Run that they should break out of their loops.
  keep_running_ = false;
  ScheduleWork();
}

void MessagePumpLibevent::ScheduleWork() {
  // Tell libevent (in a threadsafe way) that it should break out of its loop.
  char buf = 0;
  int nwrite = HANDLE_EINTR(write(wakeup_pipe_in_, &buf, 1));
  DPCHECK(nwrite == 1 || errno == EAGAIN) << "nwrite:" << nwrite;
}

void MessagePumpLibevent::ScheduleDelayedWork(
    const TimeTicks& delayed_work_time) {
  // We know that we can't be blocked on Run()'s |timer_event| right now since
  // this method can only be called on the same thread as Run(). Hence we have
  // nothing to do here, this thread will sleep in Run() with the correct
  // timeout when it's out of immediate tasks.
}

bool MessagePumpLibevent::Init() {
  int fds[2];
  if (!CreateLocalNonBlockingPipe(fds)) {
    DPLOG(ERROR) << "pipe creation failed";
    return false;
  }
  wakeup_pipe_out_ = fds[0];
  wakeup_pipe_in_ = fds[1];

  wakeup_event_ = new event;
  event_set(wakeup_event_, wakeup_pipe_out_, EV_READ | EV_PERSIST,
            OnWakeup, this);
  event_base_set(event_base_, wakeup_event_);

  if (event_add(wakeup_event_, nullptr))
    return false;
  return true;
}

// static
void MessagePumpLibevent::OnLibeventNotification(int fd,
                                                 short flags,
                                                 void* context) {
  FdWatchController* controller = static_cast<FdWatchController*>(context);
  DCHECK(controller);
  TRACE_EVENT0("toplevel", "OnLibevent");
  TRACE_EVENT_WITH_FLOW1(TRACE_DISABLED_BY_DEFAULT("toplevel.flow"),
                         "MessagePumpLibevent::OnLibeventNotification",
                         reinterpret_cast<uintptr_t>(controller) ^ fd,
                         TRACE_EVENT_FLAG_FLOW_IN | TRACE_EVENT_FLAG_FLOW_OUT,
                         "fd", fd);

  TRACE_HEAP_PROFILER_API_SCOPED_TASK_EXECUTION heap_profiler_scope(
      controller->created_from_location().file_name());

  MessagePumpLibevent* pump = controller->pump();
  pump->processed_io_events_ = true;

  if ((flags & (EV_READ | EV_WRITE)) == (EV_READ | EV_WRITE)) {
    // Both callbacks will be called. It is necessary to check that |controller|
    // is not destroyed.
    bool controller_was_destroyed = false;
    controller->was_destroyed_ = &controller_was_destroyed;
    controller->OnFileCanWriteWithoutBlocking(fd, pump);
    if (!controller_was_destroyed)
      controller->OnFileCanReadWithoutBlocking(fd, pump);
    if (!controller_was_destroyed)
      controller->was_destroyed_ = nullptr;
  } else if (flags & EV_WRITE) {
    controller->OnFileCanWriteWithoutBlocking(fd, pump);
  } else if (flags & EV_READ) {
    controller->OnFileCanReadWithoutBlocking(fd, pump);
  }
}

// Called if a byte is received on the wakeup pipe.
// static
void MessagePumpLibevent::OnWakeup(int socket, short flags, void* context) {
  MessagePumpLibevent* that = static_cast<MessagePumpLibevent*>(context);
  DCHECK(that->wakeup_pipe_out_ == socket);

  // Remove and discard the wakeup byte.
  char buf;
  int nread = HANDLE_EINTR(read(socket, &buf, 1));
  DCHECK_EQ(nread, 1);
  that->processed_io_events_ = true;
  // Tell libevent to break out of inner loop.
  event_base_loopbreak(that->event_base_);
}

}  // namespace base
