// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_MESSAGE_LOOP_MESSAGE_LOOP_H_
#define BASE_MESSAGE_LOOP_MESSAGE_LOOP_H_

#include <memory>
#include <string>

#include "base/base_export.h"
#include "base/callback_forward.h"
#include "base/gtest_prod_util.h"
#include "base/macros.h"
#include "base/memory/scoped_refptr.h"
#include "base/message_loop/message_pump_type.h"
#include "base/message_loop/timer_slack.h"
#include "base/pending_task.h"
#include "base/run_loop.h"
#include "base/threading/thread_checker.h"
#include "base/time/time.h"
#include "build/build_config.h"

namespace base {

class MessagePump;
class TaskObserver;

namespace sequence_manager {
class TaskQueue;
namespace internal {
class SequenceManagerImpl;
}  // namespace internal
}  // namespace sequence_manager

// A MessageLoop is used to process events for a particular thread.  There is
// at most one MessageLoop instance per thread.
//
// Events include at a minimum Task instances submitted to the MessageLoop's
// TaskRunner. Depending on the Type of message pump used by the MessageLoop
// other events such as UI messages may be processed.  On Windows APC calls (as
// time permits) and signals sent to a registered set of HANDLEs may also be
// processed.
//
// The MessageLoop's API should only be used directly by its owner (and users
// which the owner opts to share a MessageLoop* with). Other ways to access
// subsets of the MessageLoop API:
//   - base::RunLoop : Drive the MessageLoop from the thread it's bound to.
//   - base::Thread/SequencedTaskRunnerHandle : Post back to the MessageLoop
//     from a task running on it.
//   - SequenceLocalStorageSlot : Bind external state to this MessageLoop.
//   - base::MessageLoopCurrent : Access statically exposed APIs of this
//     MessageLoop.
//   - Embedders may provide their own static accessors to post tasks on
//     specific loops (e.g. content::BrowserThreads).
//
// NOTE: Unless otherwise specified, a MessageLoop's methods may only be called
// on the thread where the MessageLoop's Run method executes.
//
// NOTE: MessageLoop has task reentrancy protection.  This means that if a
// task is being processed, a second task cannot start until the first task is
// finished.  Reentrancy can happen when processing a task, and an inner
// message pump is created.  That inner pump then processes native messages
// which could implicitly start an inner task.  Inner message pumps are created
// with dialogs (DialogBox), common dialogs (GetOpenFileName), OLE functions
// (DoDragDrop), printer functions (StartDoc) and *many* others.
//
// Sample workaround when inner task processing is needed:
//   HRESULT hr;
//   {
//     MessageLoopCurrent::ScopedNestableTaskAllower allow;
//     hr = DoDragDrop(...); // Implicitly runs a modal message loop.
//   }
//   // Process |hr| (the result returned by DoDragDrop()).
//
// Please be SURE your task is reentrant (nestable) and all global variables
// are stable and accessible before calling SetNestableTasksAllowed(true).
//
// DEPRECATED: Use a SingleThreadTaskExecutor instead or TaskEnvironment
// for tests. TODO(https://crbug.com/891670/) remove this class.
class BASE_EXPORT MessageLoop {
 public:
  // Normally, it is not necessary to instantiate a MessageLoop.  Instead, it
  // is typical to make use of the current thread's MessageLoop instance.
  explicit MessageLoop(MessagePumpType type = MessagePumpType::DEFAULT);
  // Creates a MessageLoop with the supplied MessagePump, which must be
  // non-null.
  explicit MessageLoop(std::unique_ptr<MessagePump> custom_pump);

  virtual ~MessageLoop();

  // Set the timer slack for this message loop.
  void SetTimerSlack(TimerSlack timer_slack);

  // Returns true if this loop's pump is |type|. This allows subclasses
  // (especially those in tests) to specialize how they are identified.
  virtual bool IsType(MessagePumpType type) const;

  // Returns the type passed to the constructor.
  MessagePumpType type() const { return type_; }

  // Sets a new TaskRunner for this message loop. If the message loop was
  // already bound, this must be called on the thread to which it is bound.
  void SetTaskRunner(scoped_refptr<SingleThreadTaskRunner> task_runner);

  // Gets the TaskRunner associated with this message loop.
  scoped_refptr<SingleThreadTaskRunner> task_runner() const;

  // These functions can only be called on the same thread that |this| is
  // running on.
  // These functions must not be called from a TaskObserver callback.
  void AddTaskObserver(TaskObserver* task_observer);
  void RemoveTaskObserver(TaskObserver* task_observer);

  // Returns true if the message loop is idle (ignoring delayed tasks). This is
  // the same condition which triggers DoWork() to return false: i.e.
  // out of tasks which can be processed at the current run-level -- there might
  // be deferred non-nestable tasks remaining if currently in a nested run
  // level.
  // TODO(alexclarke): Make this const when MessageLoopImpl goes away.
  bool IsIdleForTesting();

  //----------------------------------------------------------------------------
 protected:
  // Returns true if this is the active MessageLoop for the current thread.
  bool IsBoundToCurrentThread() const;

  using MessagePumpFactoryCallback =
      OnceCallback<std::unique_ptr<MessagePump>()>;

  // Common protected constructor. Other constructors delegate the
  // initialization to this constructor.
  // A subclass can invoke this constructor to create a message_loop of a
  // specific type with a custom loop. The implementation does not call
  // BindToCurrentThread. If this constructor is invoked directly by a subclass,
  // then the subclass must subsequently bind the message loop.
  MessageLoop(MessagePumpType type, std::unique_ptr<MessagePump> pump);

  // Configure various members and bind this message loop to the current thread.
  void BindToCurrentThread();

  // A raw pointer to the MessagePump handed-off to |sequence_manager_|.
  // Valid for the lifetime of |sequence_manager_|.
  MessagePump* pump_ = nullptr;

  // TODO(crbug.com/891670): We shouldn't publicly expose all of
  // SequenceManagerImpl.
  const std::unique_ptr<sequence_manager::internal::SequenceManagerImpl>
      sequence_manager_;
  // SequenceManager requires an explicit initialisation of the default task
  // queue.
  const scoped_refptr<sequence_manager::TaskQueue> default_task_queue_;

 private:
  friend class MessageLoopTypedTest;
  friend class ScheduleWorkTest;
  friend class Thread;
  friend class sequence_manager::internal::SequenceManagerImpl;
  FRIEND_TEST_ALL_PREFIXES(MessageLoopTest, DeleteUnboundLoop);

  // Creates a MessageLoop without binding to a thread.
  //
  // It is valid to call this to create a new message loop on one thread,
  // and then pass it to the thread where the message loop actually runs.
  // The message loop's BindToCurrentThread() method must be called on the
  // thread the message loop runs on, before calling Run().
  // Before BindToCurrentThread() is called, only Post*Task() functions can
  // be called on the message loop.
  static std::unique_ptr<MessageLoop> CreateUnbound(MessagePumpType type);
  static std::unique_ptr<MessageLoop> CreateUnbound(
      std::unique_ptr<MessagePump> pump);

  scoped_refptr<sequence_manager::TaskQueue> CreateDefaultTaskQueue();

  std::unique_ptr<MessagePump> CreateMessagePump();

  sequence_manager::internal::SequenceManagerImpl* GetSequenceManagerImpl()
      const {
    return sequence_manager_.get();
  }

  const MessagePumpType type_;

  // If set this will be returned by the next call to CreateMessagePump().
  // This is only set if |type_| is TYPE_CUSTOM and |pump_| is null.
  std::unique_ptr<MessagePump> custom_pump_;

  // Id of the thread this message loop is bound to. Initialized once when the
  // MessageLoop is bound to its thread and constant forever after.
  PlatformThreadId thread_id_ = kInvalidThreadId;

  // Verifies that calls are made on the thread on which BindToCurrentThread()
  // was invoked.
  THREAD_CHECKER(bound_thread_checker_);

  DISALLOW_COPY_AND_ASSIGN(MessageLoop);
};

}  // namespace base

#endif  // BASE_MESSAGE_LOOP_MESSAGE_LOOP_H_
