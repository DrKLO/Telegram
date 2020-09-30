// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/profiler/stack_copier_suspend.h"

#include "base/profiler/stack_buffer.h"
#include "base/profiler/suspendable_thread_delegate.h"

namespace base {

StackCopierSuspend::StackCopierSuspend(
    std::unique_ptr<SuspendableThreadDelegate> thread_delegate)
    : thread_delegate_(std::move(thread_delegate)) {}

StackCopierSuspend::~StackCopierSuspend() = default;

// Suspends the thread, copies the stack state, and resumes the thread. The
// copied stack state includes the stack itself, the top address of the stack
// copy, and the register context. Returns true on success, and returns the
// copied state via the params.
//
// NO HEAP ALLOCATIONS within the ScopedSuspendThread scope.
bool StackCopierSuspend::CopyStack(StackBuffer* stack_buffer,
                                   uintptr_t* stack_top,
                                   TimeTicks* timestamp,
                                   RegisterContext* thread_context,
                                   Delegate* delegate) {
  const uintptr_t top = thread_delegate_->GetStackBaseAddress();
  uintptr_t bottom = 0;
  const uint8_t* stack_copy_bottom = nullptr;
  {
    // Allocation of the ScopedSuspendThread object itself is OK since it
    // necessarily occurs before the thread is suspended by the object.
    std::unique_ptr<SuspendableThreadDelegate::ScopedSuspendThread>
        suspend_thread = thread_delegate_->CreateScopedSuspendThread();

    // TimeTicks::Now() is implemented in terms of reads to the timer tick
    // counter or TSC register on x86/x86_64 so is reentrant.
    *timestamp = TimeTicks::Now();

    if (!suspend_thread->WasSuccessful())
      return false;

    if (!thread_delegate_->GetThreadContext(thread_context))
      return false;

    bottom = RegisterContextStackPointer(thread_context);

    // The StackBuffer allocation is expected to be at least as large as the
    // largest stack region allocation on the platform, but check just in case
    // it isn't *and* the actual stack itself exceeds the buffer allocation
    // size.
    if ((top - bottom) > stack_buffer->size())
      return false;

    if (!thread_delegate_->CanCopyStack(bottom))
      return false;

    delegate->OnStackCopy();

    stack_copy_bottom = CopyStackContentsAndRewritePointers(
        reinterpret_cast<uint8_t*>(bottom), reinterpret_cast<uintptr_t*>(top),
        StackBuffer::kPlatformStackAlignment, stack_buffer->buffer());
  }

  delegate->OnThreadResume();

  *stack_top = reinterpret_cast<uintptr_t>(stack_copy_bottom) + (top - bottom);

  for (uintptr_t* reg :
       thread_delegate_->GetRegistersToRewrite(thread_context)) {
    *reg = RewritePointerIfInOriginalStack(reinterpret_cast<uint8_t*>(bottom),
                                           reinterpret_cast<uintptr_t*>(top),
                                           stack_copy_bottom, *reg);
  }

  return true;
}

}  // namespace base
