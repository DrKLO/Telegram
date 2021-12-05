// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/message_loop/message_loop.h"

#include <utility>

#include "base/bind.h"
#include "base/logging.h"
#include "base/memory/ptr_util.h"
#include "base/message_loop/message_pump_default.h"
#include "base/message_loop/message_pump_for_io.h"
#include "base/message_loop/message_pump_for_ui.h"
#include "base/optional.h"
#include "base/run_loop.h"
#include "base/task/sequence_manager/sequence_manager.h"
#include "base/task/sequence_manager/sequence_manager_impl.h"
#include "base/task/sequence_manager/task_queue.h"
#include "build/build_config.h"

#if defined(OS_MACOSX)
#include "base/message_loop/message_pump_mac.h"
#endif

namespace base {

MessageLoop::MessageLoop(MessagePumpType type) : MessageLoop(type, nullptr) {
  // For TYPE_CUSTOM you must either use
  // MessageLoop(std::unique_ptr<MessagePump> pump) or
  // MessageLoop::CreateUnbound()
  DCHECK_NE(type_, MessagePumpType::CUSTOM);
  BindToCurrentThread();
}

MessageLoop::MessageLoop(std::unique_ptr<MessagePump> pump)
    : MessageLoop(MessagePumpType::CUSTOM, std::move(pump)) {
  BindToCurrentThread();
}

MessageLoop::~MessageLoop() {
  // Clean up any unprocessed tasks, but take care: deleting a task could
  // result in the addition of more tasks (e.g., via DeleteSoon). This is taken
  // care by the queue as it will prevent further tasks from being posted to its
  // associated TaskRunner instances.
  default_task_queue_->ShutdownTaskQueue();

  // If |pump_| is non-null, this message loop has been bound and should be the
  // current one on this thread. Otherwise, this loop is being destructed before
  // it was bound to a thread, so a different message loop (or no loop at all)
  // may be current.
  DCHECK((pump_ && IsBoundToCurrentThread()) ||
         (!pump_ && !IsBoundToCurrentThread()));

// iOS just attaches to the loop, it doesn't Run it.
// TODO(stuartmorgan): Consider wiring up a Detach().
#if !defined(OS_IOS)
  // There should be no active RunLoops on this thread, unless this MessageLoop
  // isn't bound to the current thread (see other condition at the top of this
  // method).
  DCHECK((!pump_ && !IsBoundToCurrentThread()) ||
         !RunLoop::IsRunningOnCurrentThread());
#endif  // !defined(OS_IOS)
}

bool MessageLoop::IsType(MessagePumpType type) const {
  return type_ == type;
}

// TODO(gab): Migrate TaskObservers to RunLoop as part of separating concerns
// between MessageLoop and RunLoop and making MessageLoop a swappable
// implementation detail. http://crbug.com/703346
void MessageLoop::AddTaskObserver(TaskObserver* task_observer) {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_checker_);
  sequence_manager_->AddTaskObserver(task_observer);
}

void MessageLoop::RemoveTaskObserver(TaskObserver* task_observer) {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_checker_);
  sequence_manager_->RemoveTaskObserver(task_observer);
}

bool MessageLoop::IsBoundToCurrentThread() const {
  return sequence_manager_->IsBoundToCurrentThread();
}

bool MessageLoop::IsIdleForTesting() {
  return sequence_manager_->IsIdleForTesting();
}

//------------------------------------------------------------------------------

// static
std::unique_ptr<MessageLoop> MessageLoop::CreateUnbound(MessagePumpType type) {
  return WrapUnique(new MessageLoop(type, nullptr));
}

// static
std::unique_ptr<MessageLoop> MessageLoop::CreateUnbound(
    std::unique_ptr<MessagePump> custom_pump) {
  return WrapUnique(
      new MessageLoop(MessagePumpType::CUSTOM, std::move(custom_pump)));
}

MessageLoop::MessageLoop(MessagePumpType type,
                         std::unique_ptr<MessagePump> custom_pump)
    : sequence_manager_(
          sequence_manager::internal::SequenceManagerImpl::CreateUnbound(
              sequence_manager::SequenceManager::Settings::Builder()
                  .SetMessagePumpType(type)
                  .Build())),
      default_task_queue_(CreateDefaultTaskQueue()),
      type_(type),
      custom_pump_(std::move(custom_pump)) {
  // Bound in BindToCurrentThread();
  DETACH_FROM_THREAD(bound_thread_checker_);
}

scoped_refptr<sequence_manager::TaskQueue>
MessageLoop::CreateDefaultTaskQueue() {
  auto default_task_queue = sequence_manager_->CreateTaskQueue(
      sequence_manager::TaskQueue::Spec("default_tq"));
  sequence_manager_->SetTaskRunner(default_task_queue->task_runner());
  return default_task_queue;
}

void MessageLoop::BindToCurrentThread() {
  DCHECK_CALLED_ON_VALID_THREAD(bound_thread_checker_);
  thread_id_ = PlatformThread::CurrentId();

  DCHECK(!pump_);

  std::unique_ptr<MessagePump> pump = CreateMessagePump();
  pump_ = pump.get();

  DCHECK(!MessageLoopCurrent::IsSet())
      << "should only have one message loop per thread";

  sequence_manager_->BindToCurrentThread(std::move(pump));
}

std::unique_ptr<MessagePump> MessageLoop::CreateMessagePump() {
  if (custom_pump_) {
    return std::move(custom_pump_);
  } else {
    return MessagePump::Create(type_);
  }
}

void MessageLoop::SetTimerSlack(TimerSlack timer_slack) {
  sequence_manager_->SetTimerSlack(timer_slack);
}

scoped_refptr<SingleThreadTaskRunner> MessageLoop::task_runner() const {
  return sequence_manager_->GetTaskRunner();
}

void MessageLoop::SetTaskRunner(
    scoped_refptr<SingleThreadTaskRunner> task_runner) {
  DCHECK(task_runner);
  sequence_manager_->SetTaskRunner(task_runner);
}

}  // namespace base
