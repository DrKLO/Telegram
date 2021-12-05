// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/message_loop/message_pump_win.h"

#include <algorithm>
#include <cstdint>
#include <type_traits>

#include "base/bind.h"
#include "base/debug/alias.h"
#include "base/metrics/histogram_macros.h"
#include "base/numerics/ranges.h"
#include "base/numerics/safe_conversions.h"
#include "base/trace_event/trace_event.h"

namespace base {

namespace {

enum MessageLoopProblems {
  MESSAGE_POST_ERROR,
  COMPLETION_POST_ERROR,
  SET_TIMER_ERROR,
  RECEIVED_WM_QUIT_ERROR,
  MESSAGE_LOOP_PROBLEM_MAX,
};

// Returns the number of milliseconds before |next_task_time|, clamped between
// zero and the biggest DWORD value (or INFINITE if |next_task_time.is_max()|).
// Optionally, a recent value of Now() may be passed in to avoid resampling it.
DWORD GetSleepTimeoutMs(TimeTicks next_task_time,
                        TimeTicks recent_now = TimeTicks()) {
  // Shouldn't need to sleep or install a timer when there's pending immediate
  // work.
  DCHECK(!next_task_time.is_null());

  if (next_task_time.is_max())
    return INFINITE;

  auto now = recent_now.is_null() ? TimeTicks::Now() : recent_now;
  auto timeout_ms = (next_task_time - now).InMillisecondsRoundedUp();

  // A saturated_cast with an unsigned destination automatically clamps negative
  // values at zero.
  static_assert(!std::is_signed<DWORD>::value, "DWORD is unexpectedly signed");
  return saturated_cast<DWORD>(timeout_ms);
}

}  // namespace

// Message sent to get an additional time slice for pumping (processing) another
// task (a series of such messages creates a continuous task pump).
static const int kMsgHaveWork = WM_USER + 1;

//-----------------------------------------------------------------------------
// MessagePumpWin public:

MessagePumpWin::MessagePumpWin() = default;
MessagePumpWin::~MessagePumpWin() = default;

void MessagePumpWin::Run(Delegate* delegate) {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_);

  RunState s;
  s.delegate = delegate;
  s.should_quit = false;
  s.run_depth = state_ ? state_->run_depth + 1 : 1;

  RunState* previous_state = state_;
  state_ = &s;

  DoRunLoop();

  state_ = previous_state;
}

void MessagePumpWin::Quit() {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_);

  DCHECK(state_);
  state_->should_quit = true;
}

//-----------------------------------------------------------------------------
// MessagePumpForUI public:

MessagePumpForUI::MessagePumpForUI() {
  bool succeeded = message_window_.Create(
      BindRepeating(&MessagePumpForUI::MessageCallback, Unretained(this)));
  DCHECK(succeeded);
}

MessagePumpForUI::~MessagePumpForUI() = default;

void MessagePumpForUI::ScheduleWork() {
  // This is the only MessagePumpForUI method which can be called outside of
  // |bound_thread_|.

  bool not_scheduled = false;
  if (!work_scheduled_.compare_exchange_strong(not_scheduled, true))
    return;  // Someone else continued the pumping.

  // Make sure the MessagePump does some work for us.
  BOOL ret = PostMessage(message_window_.hwnd(), kMsgHaveWork, 0, 0);
  if (ret)
    return;  // There was room in the Window Message queue.

  // We have failed to insert a have-work message, so there is a chance that we
  // will starve tasks/timers while sitting in a nested run loop.  Nested
  // loops only look at Windows Message queues, and don't look at *our* task
  // queues, etc., so we might not get a time slice in such. :-(
  // We could abort here, but the fear is that this failure mode is plausibly
  // common (queue is full, of about 2000 messages), so we'll do a near-graceful
  // recovery.  Nested loops are pretty transient (we think), so this will
  // probably be recoverable.

  // Clarify that we didn't really insert.
  work_scheduled_ = false;
  UMA_HISTOGRAM_ENUMERATION("Chrome.MessageLoopProblem", MESSAGE_POST_ERROR,
                            MESSAGE_LOOP_PROBLEM_MAX);
}

void MessagePumpForUI::ScheduleDelayedWork(const TimeTicks& delayed_work_time) {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_);

  // Since this is always called from |bound_thread_|, there is almost always
  // nothing to do as the loop is already running. When the loop becomes idle,
  // it will typically WaitForWork() in DoRunLoop() with the timeout provided by
  // DoSomeWork(). The only alternative to this is entering a native nested loop
  // (e.g. modal dialog) under a ScopedNestableTaskAllower, in which case
  // HandleWorkMessage() will be invoked when the system picks up kMsgHaveWork
  // and it will ScheduleNativeTimer() if it's out of immediate work. However,
  // in that alternate scenario : it's possible for a Windows native task (e.g.
  // https://docs.microsoft.com/en-us/windows/desktop/winmsg/using-hooks) to
  // wake the native nested loop and PostDelayedTask() to the current thread
  // from it. This is the only case where we must install/adjust the native
  // timer from ScheduleDelayedWork() because if we don't, the native loop will
  // go back to sleep, unaware of the new |delayed_work_time|.
  // TODO(gab): This could potentially be replaced by a ForegroundIdleProc hook
  // if Windows ends up being the only platform requiring ScheduleDelayedWork().
  if (in_native_loop_ && !work_scheduled_) {
    // TODO(gab): Consider passing a NextWorkInfo object to ScheduleDelayedWork
    // to take advantage of |recent_now| here too.
    ScheduleNativeTimer({delayed_work_time, TimeTicks::Now()});
  }
}

void MessagePumpForUI::EnableWmQuit() {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_);
  enable_wm_quit_ = true;
}

void MessagePumpForUI::AddObserver(Observer* observer) {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_);
  observers_.AddObserver(observer);
}

void MessagePumpForUI::RemoveObserver(Observer* observer) {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_);
  observers_.RemoveObserver(observer);
}

//-----------------------------------------------------------------------------
// MessagePumpForUI private:

bool MessagePumpForUI::MessageCallback(
    UINT message, WPARAM wparam, LPARAM lparam, LRESULT* result) {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_);
  switch (message) {
    case kMsgHaveWork:
      HandleWorkMessage();
      break;
    case WM_TIMER:
      if (wparam == reinterpret_cast<UINT_PTR>(this))
        HandleTimerMessage();
      break;
  }
  return false;
}

void MessagePumpForUI::DoRunLoop() {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_);

  // IF this was just a simple PeekMessage() loop (servicing all possible work
  // queues), then Windows would try to achieve the following order according
  // to MSDN documentation about PeekMessage with no filter):
  //    * Sent messages
  //    * Posted messages
  //    * Sent messages (again)
  //    * WM_PAINT messages
  //    * WM_TIMER messages
  //
  // Summary: none of the above classes is starved, and sent messages has twice
  // the chance of being processed (i.e., reduced service time).

  for (;;) {
    // If we do any work, we may create more messages etc., and more work may
    // possibly be waiting in another task group.  When we (for example)
    // ProcessNextWindowsMessage(), there is a good chance there are still more
    // messages waiting.  On the other hand, when any of these methods return
    // having done no work, then it is pretty unlikely that calling them again
    // quickly will find any work to do.  Finally, if they all say they had no
    // work, then it is a good time to consider sleeping (waiting) for more
    // work.

    in_native_loop_ = false;
    state_->delegate->BeforeDoInternalWork();
    DCHECK(!in_native_loop_);

    bool more_work_is_plausible = ProcessNextWindowsMessage();
    in_native_loop_ = false;
    if (state_->should_quit)
      break;

    Delegate::NextWorkInfo next_work_info = state_->delegate->DoSomeWork();
    in_native_loop_ = false;
    more_work_is_plausible |= next_work_info.is_immediate();
    if (state_->should_quit)
      break;

    if (installed_native_timer_) {
      // As described in ScheduleNativeTimer(), the native timer is only
      // installed and needed while in a nested native loop. If it is installed,
      // it means the above work entered such a loop. Having now resumed, the
      // native timer is no longer needed.
      KillNativeTimer();
    }

    if (more_work_is_plausible)
      continue;

    more_work_is_plausible = state_->delegate->DoIdleWork();
    // DoIdleWork() shouldn't end up in native nested loops and thus shouldn't
    // have any chance of reinstalling a native timer.
    DCHECK(!in_native_loop_);
    DCHECK(!installed_native_timer_);
    if (state_->should_quit)
      break;

    if (more_work_is_plausible)
      continue;

    // WaitForWork() does some work itself, so notify the delegate of it.
    state_->delegate->BeforeWait();
    WaitForWork(next_work_info);
  }
}

void MessagePumpForUI::WaitForWork(Delegate::NextWorkInfo next_work_info) {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_);

  // Wait until a message is available, up to the time needed by the timer
  // manager to fire the next set of timers.
  DWORD wait_flags = MWMO_INPUTAVAILABLE;
  for (DWORD delay = GetSleepTimeoutMs(next_work_info.delayed_run_time,
                                       next_work_info.recent_now);
       delay != 0; delay = GetSleepTimeoutMs(next_work_info.delayed_run_time)) {
    // Tell the optimizer to retain these values to simplify analyzing hangs.
    base::debug::Alias(&delay);
    base::debug::Alias(&wait_flags);
    DWORD result = MsgWaitForMultipleObjectsEx(0, nullptr, delay, QS_ALLINPUT,
                                               wait_flags);

    if (WAIT_OBJECT_0 == result) {
      // A WM_* message is available.
      // If a parent child relationship exists between windows across threads
      // then their thread inputs are implicitly attached.
      // This causes the MsgWaitForMultipleObjectsEx API to return indicating
      // that messages are ready for processing (Specifically, mouse messages
      // intended for the child window may appear if the child window has
      // capture).
      // The subsequent PeekMessages call may fail to return any messages thus
      // causing us to enter a tight loop at times.
      // The code below is a workaround to give the child window
      // some time to process its input messages by looping back to
      // MsgWaitForMultipleObjectsEx above when there are no messages for the
      // current thread.
      MSG msg = {0};
      bool has_pending_sent_message =
          (HIWORD(::GetQueueStatus(QS_SENDMESSAGE)) & QS_SENDMESSAGE) != 0;
      if (has_pending_sent_message ||
          ::PeekMessage(&msg, nullptr, 0, 0, PM_NOREMOVE)) {
        return;
      }

      // We know there are no more messages for this thread because PeekMessage
      // has returned false. Reset |wait_flags| so that we wait for a *new*
      // message.
      wait_flags = 0;
    }

    DCHECK_NE(WAIT_FAILED, result) << GetLastError();
  }
}

void MessagePumpForUI::HandleWorkMessage() {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_);

  // The kMsgHaveWork message was consumed by a native loop, we must assume
  // we're in one until DoRunLoop() gets control back.
  in_native_loop_ = true;

  // If we are being called outside of the context of Run, then don't try to do
  // any work.  This could correspond to a MessageBox call or something of that
  // sort.
  if (!state_) {
    // Since we handled a kMsgHaveWork message, we must still update this flag.
    work_scheduled_ = false;
    return;
  }

  // Let whatever would have run had we not been putting messages in the queue
  // run now.  This is an attempt to make our dummy message not starve other
  // messages that may be in the Windows message queue.
  ProcessPumpReplacementMessage();

  Delegate::NextWorkInfo next_work_info = state_->delegate->DoSomeWork();
  if (next_work_info.is_immediate()) {
    ScheduleWork();
  } else {
    ScheduleNativeTimer(next_work_info);
  }
}

void MessagePumpForUI::HandleTimerMessage() {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_);

  // ::KillTimer doesn't remove pending WM_TIMER messages from the queue,
  // explicitly ignore the last WM_TIMER message in that case to avoid handling
  // work from here when DoRunLoop() is active (which could result in scheduling
  // work from two places at once). Note: we're still fine in the event that a
  // second native nested loop is entered before such a dead WM_TIMER message is
  // discarded because ::SetTimer merely resets the timer if invoked twice with
  // the same id.
  if (!installed_native_timer_)
    return;

  // We only need to fire once per specific delay, another timer may be
  // scheduled below but we're done with this one.
  KillNativeTimer();

  // If we are being called outside of the context of Run, then don't do
  // anything.  This could correspond to a MessageBox call or something of
  // that sort.
  if (!state_)
    return;

  Delegate::NextWorkInfo next_work_info = state_->delegate->DoSomeWork();
  if (next_work_info.is_immediate()) {
    ScheduleWork();
  } else {
    ScheduleNativeTimer(next_work_info);
  }
}

void MessagePumpForUI::ScheduleNativeTimer(
    Delegate::NextWorkInfo next_work_info) {
  DCHECK(!next_work_info.is_immediate());
  DCHECK(in_native_loop_);

  // Do not redundantly set the same native timer again if it was already set.
  // This can happen when a nested native loop goes idle with pending delayed
  // tasks, then gets woken up by an immediate task, and goes back to idle with
  // the same pending delay. No need to kill the native timer if there is
  // already one but the |delayed_run_time| has changed as ::SetTimer reuses the
  // same id and will replace and reset the existing timer.
  if (installed_native_timer_ &&
      *installed_native_timer_ == next_work_info.delayed_run_time) {
    return;
  }

  if (next_work_info.delayed_run_time.is_max())
    return;

  // We do not use native Windows timers in general as they have a poor, 10ms,
  // granularity. Instead we rely on MsgWaitForMultipleObjectsEx's
  // high-resolution timeout to sleep without timers in WaitForWork(). However,
  // when entering a nested native ::GetMessage() loop (e.g. native modal
  // windows) under a ScopedNestableTaskAllower, we have to rely on a native
  // timer when HandleWorkMessage() runs out of immediate work. Since
  // ScopedNestableTaskAllower invokes ScheduleWork() : we are guaranteed that
  // HandleWorkMessage() will be called after entering a nested native loop that
  // should process application tasks. But once HandleWorkMessage() is out of
  // immediate work, ::SetTimer() is used to guarantee we are invoked again
  // should the next delayed task expire before the nested native loop ends. The
  // native timer being unnecessary once we return to our DoRunLoop(), we
  // ::KillTimer when it resumes (nested native loops should be rare so we're
  // not worried about ::SetTimer<=>::KillTimer churn).
  // TODO(gab): The long-standing legacy dependency on the behavior of
  // ScopedNestableTaskAllower is unfortunate, would be nice to make this a
  // MessagePump concept (instead of requiring impls to invoke ScheduleWork()
  // one-way and no-op DoWork() the other way).

  UINT delay_msec = strict_cast<UINT>(GetSleepTimeoutMs(
      next_work_info.delayed_run_time, next_work_info.recent_now));
  if (delay_msec == 0) {
    ScheduleWork();
  } else {
    // TODO(gab): ::SetTimer()'s documentation claims it does this for us.
    // Consider removing this safety net.
    delay_msec = ClampToRange(delay_msec, UINT(USER_TIMER_MINIMUM),
                              UINT(USER_TIMER_MAXIMUM));

    // Tell the optimizer to retain the delay to simplify analyzing hangs.
    base::debug::Alias(&delay_msec);
    UINT_PTR ret =
        ::SetTimer(message_window_.hwnd(), reinterpret_cast<UINT_PTR>(this),
                   delay_msec, nullptr);
    installed_native_timer_ = next_work_info.delayed_run_time;

    if (ret)
      return;
    // If we can't set timers, we are in big trouble... but cross our fingers
    // for now.
    // TODO(jar): If we don't see this error, use a CHECK() here instead.
    UMA_HISTOGRAM_ENUMERATION("Chrome.MessageLoopProblem", SET_TIMER_ERROR,
                              MESSAGE_LOOP_PROBLEM_MAX);
  }
}

void MessagePumpForUI::KillNativeTimer() {
  DCHECK(installed_native_timer_);
  const bool success =
      ::KillTimer(message_window_.hwnd(), reinterpret_cast<UINT_PTR>(this));
  DPCHECK(success);
  installed_native_timer_.reset();
}

bool MessagePumpForUI::ProcessNextWindowsMessage() {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_);

  // If there are sent messages in the queue then PeekMessage internally
  // dispatches the message and returns false. We return true in this
  // case to ensure that the message loop peeks again instead of calling
  // MsgWaitForMultipleObjectsEx.
  bool sent_messages_in_queue = false;
  DWORD queue_status = ::GetQueueStatus(QS_SENDMESSAGE);
  if (HIWORD(queue_status) & QS_SENDMESSAGE)
    sent_messages_in_queue = true;

  MSG msg;
  if (::PeekMessage(&msg, nullptr, 0, 0, PM_REMOVE) != FALSE)
    return ProcessMessageHelper(msg);

  return sent_messages_in_queue;
}

bool MessagePumpForUI::ProcessMessageHelper(const MSG& msg) {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_);

  TRACE_EVENT1("base,toplevel", "MessagePumpForUI::ProcessMessageHelper",
               "message", msg.message);
  if (WM_QUIT == msg.message) {
    // WM_QUIT is the standard way to exit a ::GetMessage() loop. Our
    // MessageLoop has its own quit mechanism, so WM_QUIT should only terminate
    // it if |enable_wm_quit_| is explicitly set (and is generally unexpected
    // otherwise).
    if (enable_wm_quit_) {
      state_->should_quit = true;
      return false;
    }
    UMA_HISTOGRAM_ENUMERATION("Chrome.MessageLoopProblem",
                              RECEIVED_WM_QUIT_ERROR, MESSAGE_LOOP_PROBLEM_MAX);
    return true;
  }

  // While running our main message pump, we discard kMsgHaveWork messages.
  if (msg.message == kMsgHaveWork && msg.hwnd == message_window_.hwnd())
    return ProcessPumpReplacementMessage();

  for (Observer& observer : observers_)
    observer.WillDispatchMSG(msg);
  ::TranslateMessage(&msg);
  ::DispatchMessage(&msg);
  for (Observer& observer : observers_)
    observer.DidDispatchMSG(msg);

  return true;
}

bool MessagePumpForUI::ProcessPumpReplacementMessage() {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_);

  // When we encounter a kMsgHaveWork message, this method is called to peek and
  // process a replacement message. The goal is to make the kMsgHaveWork as non-
  // intrusive as possible, even though a continuous stream of such messages are
  // posted. This method carefully peeks a message while there is no chance for
  // a kMsgHaveWork to be pending, then resets the |have_work_| flag (allowing a
  // replacement kMsgHaveWork to possibly be posted), and finally dispatches
  // that peeked replacement. Note that the re-post of kMsgHaveWork may be
  // asynchronous to this thread!!

  MSG msg;
  const bool have_message =
      ::PeekMessage(&msg, nullptr, 0, 0, PM_REMOVE) != FALSE;

  // Expect no message or a message different than kMsgHaveWork.
  DCHECK(!have_message || kMsgHaveWork != msg.message ||
         msg.hwnd != message_window_.hwnd());

  // Since we discarded a kMsgHaveWork message, we must update the flag.
  DCHECK(work_scheduled_);
  work_scheduled_ = false;

  // We don't need a special time slice if we didn't |have_message| to process.
  if (!have_message)
    return false;

  if (WM_QUIT == msg.message) {
    // If we're in a nested ::GetMessage() loop then we must let that loop see
    // the WM_QUIT in order for it to exit. If we're in DoRunLoop then the re-
    // posted WM_QUIT will be either ignored, or handled, by
    // ProcessMessageHelper() called directly from ProcessNextWindowsMessage().
    ::PostQuitMessage(static_cast<int>(msg.wParam));
    // Note: we *must not* ScheduleWork() here as WM_QUIT is a low-priority
    // message on Windows (it is only returned by ::PeekMessage() when idle) :
    // https://blogs.msdn.microsoft.com/oldnewthing/20051104-33/?p=33453. As
    // such posting a kMsgHaveWork message via ScheduleWork() would cause an
    // infinite loop (kMsgHaveWork message handled first means we end up here
    // again and repost WM_QUIT+ScheduleWork() again, etc.). Not leaving a
    // kMsgHaveWork message behind however is also problematic as unwinding
    // multiple layers of nested ::GetMessage() loops can result in starving
    // application tasks. TODO(https://crbug.com/890016) : Fix this.

    // The return value is mostly irrelevant but return true like we would after
    // processing a QuitClosure() task.
    return true;
  }

  // Guarantee we'll get another time slice in the case where we go into native
  // windows code. This ScheduleWork() may hurt performance a tiny bit when
  // tasks appear very infrequently, but when the event queue is busy, the
  // kMsgHaveWork events get (percentage wise) rarer and rarer.
  ScheduleWork();
  return ProcessMessageHelper(msg);
}

//-----------------------------------------------------------------------------
// MessagePumpForIO public:

MessagePumpForIO::IOContext::IOContext() {
  memset(&overlapped, 0, sizeof(overlapped));
}

MessagePumpForIO::MessagePumpForIO() {
  port_.Set(::CreateIoCompletionPort(INVALID_HANDLE_VALUE, nullptr,
                                     reinterpret_cast<ULONG_PTR>(nullptr), 1));
  DCHECK(port_.IsValid());
}

MessagePumpForIO::~MessagePumpForIO() = default;

void MessagePumpForIO::ScheduleWork() {
  // This is the only MessagePumpForIO method which can be called outside of
  // |bound_thread_|.

  bool not_scheduled = false;
  if (!work_scheduled_.compare_exchange_strong(not_scheduled, true))
    return;  // Someone else continued the pumping.

  // Make sure the MessagePump does some work for us.
  BOOL ret = ::PostQueuedCompletionStatus(port_.Get(), 0,
                                          reinterpret_cast<ULONG_PTR>(this),
                                          reinterpret_cast<OVERLAPPED*>(this));
  if (ret)
    return;  // Post worked perfectly.

  // See comment in MessagePumpForUI::ScheduleWork() for this error recovery.

  work_scheduled_ = false;  // Clarify that we didn't succeed.
  UMA_HISTOGRAM_ENUMERATION("Chrome.MessageLoopProblem", COMPLETION_POST_ERROR,
                            MESSAGE_LOOP_PROBLEM_MAX);
}

void MessagePumpForIO::ScheduleDelayedWork(const TimeTicks& delayed_work_time) {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_);

  // Since this is always called from |bound_thread_|, there is nothing to do as
  // the loop is already running. It will WaitForWork() in
  // DoRunLoop() with the correct timeout when it's out of immediate tasks.
}

HRESULT MessagePumpForIO::RegisterIOHandler(HANDLE file_handle,
                                            IOHandler* handler) {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_);

  HANDLE port = ::CreateIoCompletionPort(
      file_handle, port_.Get(), reinterpret_cast<ULONG_PTR>(handler), 1);
  return (port != nullptr) ? S_OK : HRESULT_FROM_WIN32(GetLastError());
}

bool MessagePumpForIO::RegisterJobObject(HANDLE job_handle,
                                         IOHandler* handler) {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_);

  JOBOBJECT_ASSOCIATE_COMPLETION_PORT info;
  info.CompletionKey = handler;
  info.CompletionPort = port_.Get();
  return ::SetInformationJobObject(job_handle,
                                   JobObjectAssociateCompletionPortInformation,
                                   &info, sizeof(info)) != FALSE;
}

//-----------------------------------------------------------------------------
// MessagePumpForIO private:

void MessagePumpForIO::DoRunLoop() {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_);

  for (;;) {
    // If we do any work, we may create more messages etc., and more work may
    // possibly be waiting in another task group.  When we (for example)
    // WaitForIOCompletion(), there is a good chance there are still more
    // messages waiting.  On the other hand, when any of these methods return
    // having done no work, then it is pretty unlikely that calling them
    // again quickly will find any work to do.  Finally, if they all say they
    // had no work, then it is a good time to consider sleeping (waiting) for
    // more work.

    Delegate::NextWorkInfo next_work_info = state_->delegate->DoSomeWork();
    bool more_work_is_plausible = next_work_info.is_immediate();
    if (state_->should_quit)
      break;

    state_->delegate->BeforeWait();
    more_work_is_plausible |= WaitForIOCompletion(0, nullptr);
    if (state_->should_quit)
      break;

    if (more_work_is_plausible)
      continue;

    more_work_is_plausible = state_->delegate->DoIdleWork();
    if (state_->should_quit)
      break;

    if (more_work_is_plausible)
      continue;

    state_->delegate->BeforeWait();
    WaitForWork(next_work_info);
  }
}

// Wait until IO completes, up to the time needed by the timer manager to fire
// the next set of timers.
void MessagePumpForIO::WaitForWork(Delegate::NextWorkInfo next_work_info) {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_);

  // We do not support nested IO message loops. This is to avoid messy
  // recursion problems.
  DCHECK_EQ(1, state_->run_depth) << "Cannot nest an IO message loop!";

  DWORD timeout = GetSleepTimeoutMs(next_work_info.delayed_run_time,
                                    next_work_info.recent_now);

  // Tell the optimizer to retain these values to simplify analyzing hangs.
  base::debug::Alias(&timeout);
  WaitForIOCompletion(timeout, nullptr);
}

bool MessagePumpForIO::WaitForIOCompletion(DWORD timeout, IOHandler* filter) {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_);

  IOItem item;
  if (completed_io_.empty() || !MatchCompletedIOItem(filter, &item)) {
    // We have to ask the system for another IO completion.
    if (!GetIOItem(timeout, &item))
      return false;

    if (ProcessInternalIOItem(item))
      return true;
  }

  if (filter && item.handler != filter) {
    // Save this item for later
    completed_io_.push_back(item);
  } else {
    item.handler->OnIOCompleted(item.context, item.bytes_transfered,
                                item.error);
  }
  return true;
}

// Asks the OS for another IO completion result.
bool MessagePumpForIO::GetIOItem(DWORD timeout, IOItem* item) {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_);

  memset(item, 0, sizeof(*item));
  ULONG_PTR key = reinterpret_cast<ULONG_PTR>(nullptr);
  OVERLAPPED* overlapped = nullptr;
  if (!::GetQueuedCompletionStatus(port_.Get(), &item->bytes_transfered, &key,
                                   &overlapped, timeout)) {
    if (!overlapped)
      return false;  // Nothing in the queue.
    item->error = GetLastError();
    item->bytes_transfered = 0;
  }

  item->handler = reinterpret_cast<IOHandler*>(key);
  item->context = reinterpret_cast<IOContext*>(overlapped);
  return true;
}

bool MessagePumpForIO::ProcessInternalIOItem(const IOItem& item) {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_);

  if (reinterpret_cast<void*>(this) == reinterpret_cast<void*>(item.context) &&
      reinterpret_cast<void*>(this) == reinterpret_cast<void*>(item.handler)) {
    // This is our internal completion.
    DCHECK(!item.bytes_transfered);
    work_scheduled_ = false;
    return true;
  }
  return false;
}

// Returns a completion item that was previously received.
bool MessagePumpForIO::MatchCompletedIOItem(IOHandler* filter, IOItem* item) {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_);

  DCHECK(!completed_io_.empty());
  for (std::list<IOItem>::iterator it = completed_io_.begin();
       it != completed_io_.end(); ++it) {
    if (!filter || it->handler == filter) {
      *item = *it;
      completed_io_.erase(it);
      return true;
    }
  }
  return false;
}

}  // namespace base
