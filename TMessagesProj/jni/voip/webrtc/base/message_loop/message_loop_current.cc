// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/message_loop/message_loop_current.h"

#include "base/bind.h"
#include "base/message_loop/message_pump_for_io.h"
#include "base/message_loop/message_pump_for_ui.h"
#include "base/message_loop/message_pump_type.h"
#include "base/no_destructor.h"
#include "base/task/sequence_manager/sequence_manager_impl.h"
#include "base/threading/thread_local.h"
#include "base/threading/thread_task_runner_handle.h"

namespace base {

//------------------------------------------------------------------------------
// MessageLoopCurrent

// static
sequence_manager::internal::SequenceManagerImpl*
MessageLoopCurrent::GetCurrentSequenceManagerImpl() {
  return sequence_manager::internal::SequenceManagerImpl::GetCurrent();
}

// static
MessageLoopCurrent MessageLoopCurrent::Get() {
  return MessageLoopCurrent(GetCurrentSequenceManagerImpl());
}

// static
MessageLoopCurrent MessageLoopCurrent::GetNull() {
  return MessageLoopCurrent(nullptr);
}

// static
bool MessageLoopCurrent::IsSet() {
  return !!GetCurrentSequenceManagerImpl();
}

void MessageLoopCurrent::AddDestructionObserver(
    DestructionObserver* destruction_observer) {
  DCHECK(current_->IsBoundToCurrentThread());
  current_->AddDestructionObserver(destruction_observer);
}

void MessageLoopCurrent::RemoveDestructionObserver(
    DestructionObserver* destruction_observer) {
  DCHECK(current_->IsBoundToCurrentThread());
  current_->RemoveDestructionObserver(destruction_observer);
}

void MessageLoopCurrent::SetTaskRunner(
    scoped_refptr<SingleThreadTaskRunner> task_runner) {
  DCHECK(current_->IsBoundToCurrentThread());
  current_->SetTaskRunner(std::move(task_runner));
}

bool MessageLoopCurrent::IsBoundToCurrentThread() const {
  return current_ == GetCurrentSequenceManagerImpl();
}

bool MessageLoopCurrent::IsIdleForTesting() {
  DCHECK(current_->IsBoundToCurrentThread());
  return current_->IsIdleForTesting();
}

void MessageLoopCurrent::AddTaskObserver(TaskObserver* task_observer) {
  DCHECK(current_->IsBoundToCurrentThread());
  current_->AddTaskObserver(task_observer);
}

void MessageLoopCurrent::RemoveTaskObserver(TaskObserver* task_observer) {
  DCHECK(current_->IsBoundToCurrentThread());
  current_->RemoveTaskObserver(task_observer);
}

void MessageLoopCurrent::SetAddQueueTimeToTasks(bool enable) {
  DCHECK(current_->IsBoundToCurrentThread());
  current_->SetAddQueueTimeToTasks(enable);
}

void MessageLoopCurrent::SetNestableTasksAllowed(bool allowed) {
  DCHECK(current_->IsBoundToCurrentThread());
  current_->SetTaskExecutionAllowed(allowed);
}

bool MessageLoopCurrent::NestableTasksAllowed() const {
  return current_->IsTaskExecutionAllowed();
}

MessageLoopCurrent::ScopedNestableTaskAllower::ScopedNestableTaskAllower()
    : sequence_manager_(GetCurrentSequenceManagerImpl()),
      old_state_(sequence_manager_->IsTaskExecutionAllowed()) {
  sequence_manager_->SetTaskExecutionAllowed(true);
}

MessageLoopCurrent::ScopedNestableTaskAllower::~ScopedNestableTaskAllower() {
  sequence_manager_->SetTaskExecutionAllowed(old_state_);
}

bool MessageLoopCurrent::operator==(const MessageLoopCurrent& other) const {
  return current_ == other.current_;
}

#if !defined(OS_NACL)

//------------------------------------------------------------------------------
// MessageLoopCurrentForUI

// static
MessageLoopCurrentForUI MessageLoopCurrentForUI::Get() {
  auto* sequence_manager = GetCurrentSequenceManagerImpl();
  DCHECK(sequence_manager);
#if defined(OS_ANDROID)
  DCHECK(sequence_manager->IsType(MessagePumpType::UI) ||
         sequence_manager->IsType(MessagePumpType::JAVA));
#else   // defined(OS_ANDROID)
  DCHECK(sequence_manager->IsType(MessagePumpType::UI));
#endif  // defined(OS_ANDROID)
  return MessageLoopCurrentForUI(sequence_manager);
}

// static
bool MessageLoopCurrentForUI::IsSet() {
  sequence_manager::internal::SequenceManagerImpl* sequence_manager =
      GetCurrentSequenceManagerImpl();
  return sequence_manager &&
#if defined(OS_ANDROID)
         (sequence_manager->IsType(MessagePumpType::UI) ||
          sequence_manager->IsType(MessagePumpType::JAVA));
#else   // defined(OS_ANDROID)
         sequence_manager->IsType(MessagePumpType::UI);
#endif  // defined(OS_ANDROID)
}

MessagePumpForUI* MessageLoopCurrentForUI::GetMessagePumpForUI() const {
  return static_cast<MessagePumpForUI*>(current_->GetMessagePump());
}

#if defined(USE_OZONE) && !defined(OS_FUCHSIA) && !defined(OS_WIN)
bool MessageLoopCurrentForUI::WatchFileDescriptor(
    int fd,
    bool persistent,
    MessagePumpForUI::Mode mode,
    MessagePumpForUI::FdWatchController* controller,
    MessagePumpForUI::FdWatcher* delegate) {
  DCHECK(current_->IsBoundToCurrentThread());
  return GetMessagePumpForUI()->WatchFileDescriptor(fd, persistent, mode,
                                                    controller, delegate);
}
#endif

#if defined(OS_IOS)
void MessageLoopCurrentForUI::Attach() {
  current_->AttachToMessagePump();
}
#endif  // defined(OS_IOS)

#if defined(OS_ANDROID)
void MessageLoopCurrentForUI::Abort() {
  GetMessagePumpForUI()->Abort();
}
#endif  // defined(OS_ANDROID)

#if defined(OS_WIN)
void MessageLoopCurrentForUI::AddMessagePumpObserver(
    MessagePumpForUI::Observer* observer) {
  GetMessagePumpForUI()->AddObserver(observer);
}

void MessageLoopCurrentForUI::RemoveMessagePumpObserver(
    MessagePumpForUI::Observer* observer) {
  GetMessagePumpForUI()->RemoveObserver(observer);
}
#endif  // defined(OS_WIN)

#endif  // !defined(OS_NACL)

//------------------------------------------------------------------------------
// MessageLoopCurrentForIO

// static
MessageLoopCurrentForIO MessageLoopCurrentForIO::Get() {
  auto* sequence_manager = GetCurrentSequenceManagerImpl();
  DCHECK(sequence_manager);
  DCHECK(sequence_manager->IsType(MessagePumpType::IO));
  return MessageLoopCurrentForIO(sequence_manager);
}

// static
bool MessageLoopCurrentForIO::IsSet() {
  auto* sequence_manager = GetCurrentSequenceManagerImpl();
  return sequence_manager && sequence_manager->IsType(MessagePumpType::IO);
}

MessagePumpForIO* MessageLoopCurrentForIO::GetMessagePumpForIO() const {
  return static_cast<MessagePumpForIO*>(current_->GetMessagePump());
}

#if !defined(OS_NACL_SFI)

#if defined(OS_WIN)
HRESULT MessageLoopCurrentForIO::RegisterIOHandler(
    HANDLE file,
    MessagePumpForIO::IOHandler* handler) {
  DCHECK(current_->IsBoundToCurrentThread());
  return GetMessagePumpForIO()->RegisterIOHandler(file, handler);
}

bool MessageLoopCurrentForIO::RegisterJobObject(
    HANDLE job,
    MessagePumpForIO::IOHandler* handler) {
  DCHECK(current_->IsBoundToCurrentThread());
  return GetMessagePumpForIO()->RegisterJobObject(job, handler);
}

bool MessageLoopCurrentForIO::WaitForIOCompletion(
    DWORD timeout,
    MessagePumpForIO::IOHandler* filter) {
  DCHECK(current_->IsBoundToCurrentThread());
  return GetMessagePumpForIO()->WaitForIOCompletion(timeout, filter);
}
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
bool MessageLoopCurrentForIO::WatchFileDescriptor(
    int fd,
    bool persistent,
    MessagePumpForIO::Mode mode,
    MessagePumpForIO::FdWatchController* controller,
    MessagePumpForIO::FdWatcher* delegate) {
  DCHECK(current_->IsBoundToCurrentThread());
  return GetMessagePumpForIO()->WatchFileDescriptor(fd, persistent, mode,
                                                    controller, delegate);
}
#endif  // defined(OS_WIN)

#if defined(OS_MACOSX) && !defined(OS_IOS)
bool MessageLoopCurrentForIO::WatchMachReceivePort(
    mach_port_t port,
    MessagePumpForIO::MachPortWatchController* controller,
    MessagePumpForIO::MachPortWatcher* delegate) {
  DCHECK(current_->IsBoundToCurrentThread());
  return GetMessagePumpForIO()->WatchMachReceivePort(port, controller,
                                                     delegate);
}
#endif

#endif  // !defined(OS_NACL_SFI)

#if defined(OS_FUCHSIA)
// Additional watch API for native platform resources.
bool MessageLoopCurrentForIO::WatchZxHandle(
    zx_handle_t handle,
    bool persistent,
    zx_signals_t signals,
    MessagePumpForIO::ZxHandleWatchController* controller,
    MessagePumpForIO::ZxHandleWatcher* delegate) {
  DCHECK(current_->IsBoundToCurrentThread());
  return GetMessagePumpForIO()->WatchZxHandle(handle, persistent, signals,
                                              controller, delegate);
}
#endif

}  // namespace base
