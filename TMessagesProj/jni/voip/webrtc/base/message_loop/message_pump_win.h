// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_MESSAGE_LOOP_MESSAGE_PUMP_WIN_H_
#define BASE_MESSAGE_LOOP_MESSAGE_PUMP_WIN_H_

#include <windows.h>

#include <atomic>
#include <list>
#include <memory>

#include "base/base_export.h"
#include "base/message_loop/message_pump.h"
#include "base/observer_list.h"
#include "base/optional.h"
#include "base/threading/thread_checker.h"
#include "base/time/time.h"
#include "base/win/message_window.h"
#include "base/win/scoped_handle.h"

namespace base {

// MessagePumpWin serves as the base for specialized versions of the MessagePump
// for Windows. It provides basic functionality like handling of observers and
// controlling the lifetime of the message pump.
class BASE_EXPORT MessagePumpWin : public MessagePump {
 public:
  MessagePumpWin();
  ~MessagePumpWin() override;

  // MessagePump methods:
  void Run(Delegate* delegate) override;
  void Quit() override;

 protected:
  struct RunState {
    Delegate* delegate;

    // Used to flag that the current Run() invocation should return ASAP.
    bool should_quit;

    // Used to count how many Run() invocations are on the stack.
    int run_depth;
  };

  virtual void DoRunLoop() = 0;

  // True iff:
  //   * MessagePumpForUI: there's a kMsgDoWork message pending in the Windows
  //     Message queue. i.e. when:
  //      a. The pump is about to wakeup from idle.
  //      b. The pump is about to enter a nested native loop and a
  //         ScopedNestableTaskAllower was instantiated to allow application
  //         tasks to execute in that nested loop (ScopedNestableTaskAllower
  //         invokes ScheduleWork()).
  //      c. While in a native (nested) loop : HandleWorkMessage() =>
  //         ProcessPumpReplacementMessage() invokes ScheduleWork() before
  //         processing a native message to guarantee this pump will get another
  //         time slice if it goes into native Windows code and enters a native
  //         nested loop. This is different from (b.) because we're not yet
  //         processing an application task at the current run level and
  //         therefore are expected to keep pumping application tasks without
  //         necessitating a ScopedNestableTaskAllower.
  //
  //   * MessagePumpforIO: there's a dummy IO completion item with |this| as an
  //     lpCompletionKey in the queue which is about to wakeup
  //     WaitForIOCompletion(). MessagePumpForIO doesn't support nesting so
  //     this is simpler than MessagePumpForUI.
  std::atomic_bool work_scheduled_{false};

  // State for the current invocation of Run.
  RunState* state_ = nullptr;

  THREAD_CHECKER(bound_thread_);
};

//-----------------------------------------------------------------------------
// MessagePumpForUI extends MessagePumpWin with methods that are particular to a
// MessageLoop instantiated with TYPE_UI.
//
// MessagePumpForUI implements a "traditional" Windows message pump. It contains
// a nearly infinite loop that peeks out messages, and then dispatches them.
// Intermixed with those peeks are callouts to DoSomeWork. When there are no
// events to be serviced, this pump goes into a wait state. In most cases, this
// message pump handles all processing.
//
// However, when a task, or windows event, invokes on the stack a native dialog
// box or such, that window typically provides a bare bones (native?) message
// pump.  That bare-bones message pump generally supports little more than a
// peek of the Windows message queue, followed by a dispatch of the peeked
// message.  MessageLoop extends that bare-bones message pump to also service
// Tasks, at the cost of some complexity.
//
// The basic structure of the extension (referred to as a sub-pump) is that a
// special message, kMsgHaveWork, is repeatedly injected into the Windows
// Message queue.  Each time the kMsgHaveWork message is peeked, checks are made
// for an extended set of events, including the availability of Tasks to run.
//
// After running a task, the special message kMsgHaveWork is again posted to the
// Windows Message queue, ensuring a future time slice for processing a future
// event.  To prevent flooding the Windows Message queue, care is taken to be
// sure that at most one kMsgHaveWork message is EVER pending in the Window's
// Message queue.
//
// There are a few additional complexities in this system where, when there are
// no Tasks to run, this otherwise infinite stream of messages which drives the
// sub-pump is halted.  The pump is automatically re-started when Tasks are
// queued.
//
// A second complexity is that the presence of this stream of posted tasks may
// prevent a bare-bones message pump from ever peeking a WM_PAINT or WM_TIMER.
// Such paint and timer events always give priority to a posted message, such as
// kMsgHaveWork messages.  As a result, care is taken to do some peeking in
// between the posting of each kMsgHaveWork message (i.e., after kMsgHaveWork is
// peeked, and before a replacement kMsgHaveWork is posted).
//
// NOTE: Although it may seem odd that messages are used to start and stop this
// flow (as opposed to signaling objects, etc.), it should be understood that
// the native message pump will *only* respond to messages.  As a result, it is
// an excellent choice.  It is also helpful that the starter messages that are
// placed in the queue when new task arrive also awakens DoRunLoop.
//
class BASE_EXPORT MessagePumpForUI : public MessagePumpWin {
 public:
  MessagePumpForUI();
  ~MessagePumpForUI() override;

  // MessagePump methods:
  void ScheduleWork() override;
  void ScheduleDelayedWork(const TimeTicks& delayed_work_time) override;

  // Make the MessagePumpForUI respond to WM_QUIT messages.
  void EnableWmQuit();

  // An observer interface to give the scheduler an opportunity to log
  // information about MSGs before and after they are dispatched.
  class BASE_EXPORT Observer {
   public:
    virtual void WillDispatchMSG(const MSG& msg) = 0;
    virtual void DidDispatchMSG(const MSG& msg) = 0;
  };

  void AddObserver(Observer* observer);
  void RemoveObserver(Observer* obseerver);

 private:
  bool MessageCallback(UINT message,
                       WPARAM wparam,
                       LPARAM lparam,
                       LRESULT* result);
  void DoRunLoop() override;
  void WaitForWork(Delegate::NextWorkInfo next_work_info);
  void HandleWorkMessage();
  void HandleTimerMessage();
  void ScheduleNativeTimer(Delegate::NextWorkInfo next_work_info);
  void KillNativeTimer();
  bool ProcessNextWindowsMessage();
  bool ProcessMessageHelper(const MSG& msg);
  bool ProcessPumpReplacementMessage();

  base::win::MessageWindow message_window_;

  // Whether MessagePumpForUI responds to WM_QUIT messages or not.
  // TODO(thestig): Remove when the Cloud Print Service goes away.
  bool enable_wm_quit_ = false;

  // Non-nullopt if there's currently a native timer installed. If so, it
  // indicates when the timer is set to fire and can be used to avoid setting
  // redundant timers.
  Optional<TimeTicks> installed_native_timer_;

  // This will become true when a native loop takes our kMsgHaveWork out of the
  // system queue. It will be reset to false whenever DoRunLoop regains control.
  // Used to decide whether ScheduleDelayedWork() should start a native timer.
  bool in_native_loop_ = false;

  ObserverList<Observer>::Unchecked observers_;
};

//-----------------------------------------------------------------------------
// MessagePumpForIO extends MessagePumpWin with methods that are particular to a
// MessageLoop instantiated with TYPE_IO. This version of MessagePump does not
// deal with Windows mesagges, and instead has a Run loop based on Completion
// Ports so it is better suited for IO operations.
//
class BASE_EXPORT MessagePumpForIO : public MessagePumpWin {
 public:
  struct BASE_EXPORT IOContext {
    IOContext();
    OVERLAPPED overlapped;
  };

  // Clients interested in receiving OS notifications when asynchronous IO
  // operations complete should implement this interface and register themselves
  // with the message pump.
  //
  // Typical use #1:
  //   class MyFile : public IOHandler {
  //     MyFile() {
  //       ...
  //       message_pump->RegisterIOHandler(file_, this);
  //     }
  //     // Plus some code to make sure that this destructor is not called
  //     // while there are pending IO operations.
  //     ~MyFile() {
  //     }
  //     virtual void OnIOCompleted(IOContext* context, DWORD bytes_transfered,
  //                                DWORD error) {
  //       ...
  //       delete context;
  //     }
  //     void DoSomeIo() {
  //       ...
  //       IOContext* context = new IOContext;
  //       ReadFile(file_, buffer, num_bytes, &read, &context);
  //     }
  //     HANDLE file_;
  //   };
  //
  // Typical use #2:
  // Same as the previous example, except that in order to deal with the
  // requirement stated for the destructor, the class calls WaitForIOCompletion
  // from the destructor to block until all IO finishes.
  //     ~MyFile() {
  //       while(pending_)
  //         message_pump->WaitForIOCompletion(INFINITE, this);
  //     }
  //
  class IOHandler {
   public:
    virtual ~IOHandler() {}
    // This will be called once the pending IO operation associated with
    // |context| completes. |error| is the Win32 error code of the IO operation
    // (ERROR_SUCCESS if there was no error). |bytes_transfered| will be zero
    // on error.
    virtual void OnIOCompleted(IOContext* context,
                               DWORD bytes_transfered,
                               DWORD error) = 0;
  };

  MessagePumpForIO();
  ~MessagePumpForIO() override;

  // MessagePump methods:
  void ScheduleWork() override;
  void ScheduleDelayedWork(const TimeTicks& delayed_work_time) override;

  // Register the handler to be used when asynchronous IO for the given file
  // completes. The registration persists as long as |file_handle| is valid, so
  // |handler| must be valid as long as there is pending IO for the given file.
  HRESULT RegisterIOHandler(HANDLE file_handle, IOHandler* handler);

  // Register the handler to be used to process job events. The registration
  // persists as long as the job object is live, so |handler| must be valid
  // until the job object is destroyed. Returns true if the registration
  // succeeded, and false otherwise.
  bool RegisterJobObject(HANDLE job_handle, IOHandler* handler);

  // Waits for the next IO completion that should be processed by |filter|, for
  // up to |timeout| milliseconds. Return true if any IO operation completed,
  // regardless of the involved handler, and false if the timeout expired. If
  // the completion port received any message and the involved IO handler
  // matches |filter|, the callback is called before returning from this code;
  // if the handler is not the one that we are looking for, the callback will
  // be postponed for another time, so reentrancy problems can be avoided.
  // External use of this method should be reserved for the rare case when the
  // caller is willing to allow pausing regular task dispatching on this thread.
  bool WaitForIOCompletion(DWORD timeout, IOHandler* filter);

 private:
  struct IOItem {
    IOHandler* handler;
    IOContext* context;
    DWORD bytes_transfered;
    DWORD error;
  };

  void DoRunLoop() override;
  void WaitForWork(Delegate::NextWorkInfo next_work_info);
  bool MatchCompletedIOItem(IOHandler* filter, IOItem* item);
  bool GetIOItem(DWORD timeout, IOItem* item);
  bool ProcessInternalIOItem(const IOItem& item);

  // The completion port associated with this thread.
  win::ScopedHandle port_;
  // This list will be empty almost always. It stores IO completions that have
  // not been delivered yet because somebody was doing cleanup.
  std::list<IOItem> completed_io_;
};

}  // namespace base

#endif  // BASE_MESSAGE_LOOP_MESSAGE_PUMP_WIN_H_
