// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/message_loop/message_pump_glib.h"

#include <fcntl.h>
#include <math.h>

#include <glib.h>

#include "base/logging.h"
#include "base/no_destructor.h"
#include "base/numerics/safe_conversions.h"
#include "base/posix/eintr_wrapper.h"
#include "base/synchronization/lock.h"
#include "base/threading/platform_thread.h"

namespace base {

namespace {

// Priorities of event sources are important to let everything be processed.
// In particular, GTK event source should have the highest priority (because
// UI events come from it), then Wayland events (the ones coming from the FD
// watcher), and the lowest priority is GLib events (our base message pump).
//
// The g_source API uses ints to denote priorities, and the lower is its value,
// the higher is the priority (i.e., they are ordered backwards).
constexpr int kPriorityWork = G_PRIORITY_DEFAULT_IDLE;
constexpr int kPriorityFdWatch = G_PRIORITY_DEFAULT_IDLE - 10;

// See the explanation above.
static_assert(G_PRIORITY_DEFAULT < kPriorityFdWatch &&
                  kPriorityFdWatch < kPriorityWork,
              "Wrong priorities are set for event sources!");

// Return a timeout suitable for the glib loop according to |next_task_time|, -1
// to block forever, 0 to return right away, or a timeout in milliseconds from
// now.
int GetTimeIntervalMilliseconds(TimeTicks next_task_time) {
  if (next_task_time.is_null())
    return 0;
  else if (next_task_time.is_max())
    return -1;

  auto timeout_ms =
      (next_task_time - TimeTicks::Now()).InMillisecondsRoundedUp();

  return timeout_ms < 0 ? 0 : saturated_cast<int>(timeout_ms);
}

// A brief refresher on GLib:
//     GLib sources have four callbacks: Prepare, Check, Dispatch and Finalize.
// On each iteration of the GLib pump, it calls each source's Prepare function.
// This function should return TRUE if it wants GLib to call its Dispatch, and
// FALSE otherwise.  It can also set a timeout in this case for the next time
// Prepare should be called again (it may be called sooner).
//     After the Prepare calls, GLib does a poll to check for events from the
// system.  File descriptors can be attached to the sources.  The poll may block
// if none of the Prepare calls returned TRUE.  It will block indefinitely, or
// by the minimum time returned by a source in Prepare.
//     After the poll, GLib calls Check for each source that returned FALSE
// from Prepare.  The return value of Check has the same meaning as for Prepare,
// making Check a second chance to tell GLib we are ready for Dispatch.
//     Finally, GLib calls Dispatch for each source that is ready.  If Dispatch
// returns FALSE, GLib will destroy the source.  Dispatch calls may be recursive
// (i.e., you can call Run from them), but Prepare and Check cannot.
//     Finalize is called when the source is destroyed.
// NOTE: It is common for subsystems to want to process pending events while
// doing intensive work, for example the flash plugin. They usually use the
// following pattern (recommended by the GTK docs):
// while (gtk_events_pending()) {
//   gtk_main_iteration();
// }
//
// gtk_events_pending just calls g_main_context_pending, which does the
// following:
// - Call prepare on all the sources.
// - Do the poll with a timeout of 0 (not blocking).
// - Call check on all the sources.
// - *Does not* call dispatch on the sources.
// - Return true if any of prepare() or check() returned true.
//
// gtk_main_iteration just calls g_main_context_iteration, which does the whole
// thing, respecting the timeout for the poll (and block, although it is to if
// gtk_events_pending returned true), and call dispatch.
//
// Thus it is important to only return true from prepare or check if we
// actually have events or work to do. We also need to make sure we keep
// internal state consistent so that if prepare/check return true when called
// from gtk_events_pending, they will still return true when called right
// after, from gtk_main_iteration.
//
// For the GLib pump we try to follow the Windows UI pump model:
// - Whenever we receive a wakeup event or the timer for delayed work expires,
// we run DoSomeWork. That part will also run in the other event pumps.
// - We also run DoSomeWork, and possibly DoIdleWork, in the main loop,
// around event handling.

struct WorkSource : public GSource {
  MessagePumpGlib* pump;
};

gboolean WorkSourcePrepare(GSource* source,
                           gint* timeout_ms) {
  *timeout_ms = static_cast<WorkSource*>(source)->pump->HandlePrepare();
  // We always return FALSE, so that our timeout is honored.  If we were
  // to return TRUE, the timeout would be considered to be 0 and the poll
  // would never block.  Once the poll is finished, Check will be called.
  return FALSE;
}

gboolean WorkSourceCheck(GSource* source) {
  // Only return TRUE if Dispatch should be called.
  return static_cast<WorkSource*>(source)->pump->HandleCheck();
}

gboolean WorkSourceDispatch(GSource* source,
                            GSourceFunc unused_func,
                            gpointer unused_data) {
  static_cast<WorkSource*>(source)->pump->HandleDispatch();
  // Always return TRUE so our source stays registered.
  return TRUE;
}

// I wish these could be const, but g_source_new wants non-const.
GSourceFuncs WorkSourceFuncs = {WorkSourcePrepare, WorkSourceCheck,
                                WorkSourceDispatch, nullptr};

// The following is used to make sure we only run the MessagePumpGlib on one
// thread. X only has one message pump so we can only have one UI loop per
// process.
#ifndef NDEBUG

// Tracks the pump the most recent pump that has been run.
struct ThreadInfo {
  // The pump.
  MessagePumpGlib* pump;

  // ID of the thread the pump was run on.
  PlatformThreadId thread_id;
};

// Used for accesing |thread_info|.
Lock& GetThreadInfoLock() {
  static NoDestructor<Lock> thread_info_lock;
  return *thread_info_lock;
}

// If non-null it means a MessagePumpGlib exists and has been Run. This is
// destroyed when the MessagePump is destroyed.
ThreadInfo* g_thread_info = nullptr;

void CheckThread(MessagePumpGlib* pump) {
  AutoLock auto_lock(GetThreadInfoLock());
  if (!g_thread_info) {
    g_thread_info = new ThreadInfo;
    g_thread_info->pump = pump;
    g_thread_info->thread_id = PlatformThread::CurrentId();
  }
  DCHECK_EQ(g_thread_info->thread_id, PlatformThread::CurrentId())
      << "Running MessagePumpGlib on two different threads; "
         "this is unsupported by GLib!";
}

void PumpDestroyed(MessagePumpGlib* pump) {
  AutoLock auto_lock(GetThreadInfoLock());
  if (g_thread_info && g_thread_info->pump == pump) {
    delete g_thread_info;
    g_thread_info = nullptr;
  }
}

#endif

struct FdWatchSource : public GSource {
  MessagePumpGlib* pump;
  MessagePumpGlib::FdWatchController* controller;
};

gboolean FdWatchSourcePrepare(GSource* source, gint* timeout_ms) {
  *timeout_ms = -1;
  return FALSE;
}

gboolean FdWatchSourceCheck(GSource* gsource) {
  auto* source = static_cast<FdWatchSource*>(gsource);
  return source->pump->HandleFdWatchCheck(source->controller) ? TRUE : FALSE;
}

gboolean FdWatchSourceDispatch(GSource* gsource,
                               GSourceFunc unused_func,
                               gpointer unused_data) {
  auto* source = static_cast<FdWatchSource*>(gsource);
  source->pump->HandleFdWatchDispatch(source->controller);
  return TRUE;
}

GSourceFuncs g_fd_watch_source_funcs = {
    FdWatchSourcePrepare, FdWatchSourceCheck, FdWatchSourceDispatch, nullptr};

}  // namespace

struct MessagePumpGlib::RunState {
  Delegate* delegate;

  // Used to flag that the current Run() invocation should return ASAP.
  bool should_quit;

  // Used to count how many Run() invocations are on the stack.
  int run_depth;

  // The information of the next task available at this run-level. Stored in
  // RunState because different set of tasks can be accessible at various
  // run-levels (e.g. non-nestable tasks).
  Delegate::NextWorkInfo next_work_info;
};

MessagePumpGlib::MessagePumpGlib()
    : state_(nullptr),
      context_(g_main_context_default()),
      wakeup_gpollfd_(new GPollFD) {
  // Create our wakeup pipe, which is used to flag when work was scheduled.
  int fds[2];
  int ret = pipe(fds);
  DCHECK_EQ(ret, 0);
  (void)ret;  // Prevent warning in release mode.

  wakeup_pipe_read_  = fds[0];
  wakeup_pipe_write_ = fds[1];
  wakeup_gpollfd_->fd = wakeup_pipe_read_;
  wakeup_gpollfd_->events = G_IO_IN;

  work_source_ = g_source_new(&WorkSourceFuncs, sizeof(WorkSource));
  static_cast<WorkSource*>(work_source_)->pump = this;
  g_source_add_poll(work_source_, wakeup_gpollfd_.get());
  g_source_set_priority(work_source_, kPriorityWork);
  // This is needed to allow Run calls inside Dispatch.
  g_source_set_can_recurse(work_source_, TRUE);
  g_source_attach(work_source_, context_);
}

MessagePumpGlib::~MessagePumpGlib() {
#ifndef NDEBUG
  PumpDestroyed(this);
#endif
  g_source_destroy(work_source_);
  g_source_unref(work_source_);
  close(wakeup_pipe_read_);
  close(wakeup_pipe_write_);
}

MessagePumpGlib::FdWatchController::FdWatchController(const Location& location)
    : FdWatchControllerInterface(location) {}

MessagePumpGlib::FdWatchController::~FdWatchController() {
  if (IsInitialized()) {
    CHECK(StopWatchingFileDescriptor());
  }
  if (was_destroyed_) {
    DCHECK(!*was_destroyed_);
    *was_destroyed_ = true;
  }
}

bool MessagePumpGlib::FdWatchController::StopWatchingFileDescriptor() {
  if (!IsInitialized())
    return false;

  g_source_destroy(source_);
  g_source_unref(source_);
  source_ = nullptr;
  watcher_ = nullptr;
  return true;
}

bool MessagePumpGlib::FdWatchController::IsInitialized() const {
  return !!source_;
}

bool MessagePumpGlib::FdWatchController::InitOrUpdate(int fd,
                                                      int mode,
                                                      FdWatcher* watcher) {
  gushort event_flags = 0;
  if (mode & WATCH_READ) {
    event_flags |= G_IO_IN;
  }
  if (mode & WATCH_WRITE) {
    event_flags |= G_IO_OUT;
  }

  if (!IsInitialized()) {
    poll_fd_ = std::make_unique<GPollFD>();
    poll_fd_->fd = fd;
  } else {
    if (poll_fd_->fd != fd)
      return false;
    // Combine old/new event masks.
    event_flags |= poll_fd_->events;
    // Destroy previous source
    bool stopped = StopWatchingFileDescriptor();
    DCHECK(stopped);
  }
  poll_fd_->events = event_flags;
  poll_fd_->revents = 0;

  source_ = g_source_new(&g_fd_watch_source_funcs, sizeof(FdWatchSource));
  DCHECK(source_);
  g_source_add_poll(source_, poll_fd_.get());
  g_source_set_can_recurse(source_, TRUE);
  g_source_set_callback(source_, nullptr, nullptr, nullptr);
  g_source_set_priority(source_, kPriorityFdWatch);

  watcher_ = watcher;
  return true;
}

bool MessagePumpGlib::FdWatchController::Attach(MessagePumpGlib* pump) {
  DCHECK(pump);
  if (!IsInitialized()) {
    return false;
  }
  auto* source = static_cast<FdWatchSource*>(source_);
  source->controller = this;
  source->pump = pump;
  g_source_attach(source_, pump->context_);
  return true;
}

void MessagePumpGlib::FdWatchController::NotifyCanRead() {
  if (!watcher_)
    return;
  DCHECK(poll_fd_);
  watcher_->OnFileCanReadWithoutBlocking(poll_fd_->fd);
}

void MessagePumpGlib::FdWatchController::NotifyCanWrite() {
  if (!watcher_)
    return;
  DCHECK(poll_fd_);
  watcher_->OnFileCanWriteWithoutBlocking(poll_fd_->fd);
}

bool MessagePumpGlib::WatchFileDescriptor(int fd,
                                          bool persistent,
                                          int mode,
                                          FdWatchController* controller,
                                          FdWatcher* watcher) {
  DCHECK_GE(fd, 0);
  DCHECK(controller);
  DCHECK(watcher);
  DCHECK(mode == WATCH_READ || mode == WATCH_WRITE || mode == WATCH_READ_WRITE);
  // WatchFileDescriptor should be called on the pump thread. It is not
  // threadsafe, so the watcher may never be registered.
  DCHECK_CALLED_ON_VALID_THREAD(watch_fd_caller_checker_);

  if (!controller->InitOrUpdate(fd, mode, watcher)) {
    DPLOG(ERROR) << "FdWatchController init failed (fd=" << fd << ")";
    return false;
  }
  return controller->Attach(this);
}

// Return the timeout we want passed to poll.
int MessagePumpGlib::HandlePrepare() {
  // |state_| may be null during tests.
  if (!state_)
    return 0;

  return GetTimeIntervalMilliseconds(state_->next_work_info.delayed_run_time);
}

bool MessagePumpGlib::HandleCheck() {
  if (!state_)  // state_ may be null during tests.
    return false;

  // We usually have a single message on the wakeup pipe, since we are only
  // signaled when the queue went from empty to non-empty, but there can be
  // two messages if a task posted a task, hence we read at most two bytes.
  // The glib poll will tell us whether there was data, so this read
  // shouldn't block.
  if (wakeup_gpollfd_->revents & G_IO_IN) {
    char msg[2];
    const int num_bytes = HANDLE_EINTR(read(wakeup_pipe_read_, msg, 2));
    if (num_bytes < 1) {
      NOTREACHED() << "Error reading from the wakeup pipe.";
    }
    DCHECK((num_bytes == 1 && msg[0] == '!') ||
           (num_bytes == 2 && msg[0] == '!' && msg[1] == '!'));
    // Since we ate the message, we need to record that we have immediate work,
    // because HandleCheck() may be called without HandleDispatch being called
    // afterwards.
    state_->next_work_info = {TimeTicks()};
    return true;
  }

  // As described in the summary at the top : Check is a second-chance to
  // Prepare, verify whether we have work ready again.
  if (GetTimeIntervalMilliseconds(state_->next_work_info.delayed_run_time) ==
      0) {
    return true;
  }

  return false;
}

void MessagePumpGlib::HandleDispatch() {
  state_->next_work_info = state_->delegate->DoSomeWork();
}

void MessagePumpGlib::Run(Delegate* delegate) {
#ifndef NDEBUG
  CheckThread(this);
#endif

  RunState state;
  state.delegate = delegate;
  state.should_quit = false;
  state.run_depth = state_ ? state_->run_depth + 1 : 1;

  RunState* previous_state = state_;
  state_ = &state;

  // We really only do a single task for each iteration of the loop.  If we
  // have done something, assume there is likely something more to do.  This
  // will mean that we don't block on the message pump until there was nothing
  // more to do.  We also set this to true to make sure not to block on the
  // first iteration of the loop, so RunUntilIdle() works correctly.
  bool more_work_is_plausible = true;

  // We run our own loop instead of using g_main_loop_quit in one of the
  // callbacks.  This is so we only quit our own loops, and we don't quit
  // nested loops run by others.  TODO(deanm): Is this what we want?
  for (;;) {
    // Don't block if we think we have more work to do.
    bool block = !more_work_is_plausible;

    more_work_is_plausible = g_main_context_iteration(context_, block);
    if (state_->should_quit)
      break;

    state_->next_work_info = state_->delegate->DoSomeWork();
    more_work_is_plausible |= state_->next_work_info.is_immediate();
    if (state_->should_quit)
      break;

    if (more_work_is_plausible)
      continue;

    more_work_is_plausible = state_->delegate->DoIdleWork();
    if (state_->should_quit)
      break;
  }

  state_ = previous_state;
}

void MessagePumpGlib::Quit() {
  if (state_) {
    state_->should_quit = true;
  } else {
    NOTREACHED() << "Quit called outside Run!";
  }
}

void MessagePumpGlib::ScheduleWork() {
  // This can be called on any thread, so we don't want to touch any state
  // variables as we would then need locks all over.  This ensures that if
  // we are sleeping in a poll that we will wake up.
  char msg = '!';
  if (HANDLE_EINTR(write(wakeup_pipe_write_, &msg, 1)) != 1) {
    NOTREACHED() << "Could not write to the UI message loop wakeup pipe!";
  }
}

void MessagePumpGlib::ScheduleDelayedWork(const TimeTicks& delayed_work_time) {
  // We need to wake up the loop in case the poll timeout needs to be
  // adjusted.  This will cause us to try to do work, but that's OK.
  ScheduleWork();
}

bool MessagePumpGlib::HandleFdWatchCheck(FdWatchController* controller) {
  DCHECK(controller);
  gushort flags = controller->poll_fd_->revents;
  return (flags & G_IO_IN) || (flags & G_IO_OUT);
}

void MessagePumpGlib::HandleFdWatchDispatch(FdWatchController* controller) {
  DCHECK(controller);
  DCHECK(controller->poll_fd_);
  gushort flags = controller->poll_fd_->revents;
  if ((flags & G_IO_IN) && (flags & G_IO_OUT)) {
    // Both callbacks will be called. It is necessary to check that
    // |controller| is not destroyed.
    bool controller_was_destroyed = false;
    controller->was_destroyed_ = &controller_was_destroyed;
    controller->NotifyCanWrite();
    if (!controller_was_destroyed)
      controller->NotifyCanRead();
    if (!controller_was_destroyed)
      controller->was_destroyed_ = nullptr;
  } else if (flags & G_IO_IN) {
    controller->NotifyCanRead();
  } else if (flags & G_IO_OUT) {
    controller->NotifyCanWrite();
  }
}

bool MessagePumpGlib::ShouldQuit() const {
  CHECK(state_);
  return state_->should_quit;
}

}  // namespace base
