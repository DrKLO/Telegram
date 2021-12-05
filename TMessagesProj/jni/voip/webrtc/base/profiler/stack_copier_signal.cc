// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/profiler/stack_copier_signal.h"

#include <linux/futex.h>
#include <signal.h>
#include <sys/ucontext.h>
#include <syscall.h>

#include <atomic>

#include "base/profiler/register_context.h"
#include "base/profiler/stack_buffer.h"
#include "base/profiler/suspendable_thread_delegate.h"
#include "base/trace_event/trace_event.h"
#include "build/build_config.h"

namespace base {

namespace {

// Waitable event implementation with futex and without DCHECK(s), since signal
// handlers cannot allocate memory or use pthread api.
class AsyncSafeWaitableEvent {
 public:
  AsyncSafeWaitableEvent() { futex_.store(0, std::memory_order_release); }
  ~AsyncSafeWaitableEvent() {}

  bool Wait() {
    // futex() can wake up spuriously if this memory address was previously used
    // for a pthread mutex. So, also check the condition.
    while (true) {
      int res =
          syscall(SYS_futex, futex_int_ptr(), FUTEX_WAIT | FUTEX_PRIVATE_FLAG,
                  0, nullptr, nullptr, 0);
      if (futex_.load(std::memory_order_acquire) != 0)
        return true;
      if (res != 0)
        return false;
    }
  }

  void Signal() {
    futex_.store(1, std::memory_order_release);
    syscall(SYS_futex, futex_int_ptr(), FUTEX_WAKE | FUTEX_PRIVATE_FLAG, 1,
            nullptr, nullptr, 0);
  }

 private:
  // Provides a pointer to the atomic's storage. std::atomic_int has standard
  // layout so its address can be used for the pointer as long as it only
  // contains the int.
  int* futex_int_ptr() {
    static_assert(sizeof(futex_) == sizeof(int),
                  "Expected std::atomic_int to be the same size as int");
    return reinterpret_cast<int*>(&futex_);
  }

  std::atomic_int futex_{0};
};

// Scoped signal event that calls Signal on the AsyncSafeWaitableEvent at
// destructor.
class ScopedEventSignaller {
 public:
  ScopedEventSignaller(AsyncSafeWaitableEvent* event) : event_(event) {}
  ~ScopedEventSignaller() { event_->Signal(); }

 private:
  AsyncSafeWaitableEvent* event_;
};

// Struct to store the arguments to the signal handler.
struct HandlerParams {
  uintptr_t stack_base_address;

  // The event is signalled when signal handler is done executing.
  AsyncSafeWaitableEvent* event;

  // Return values:

  // Successfully copied the stack segment.
  bool* success;

  // The thread context of the leaf function.
  mcontext_t* context;

  // Buffer to copy the stack segment.
  StackBuffer* stack_buffer;
  const uint8_t** stack_copy_bottom;

  // The timestamp when the stack was copied.
  TimeTicks* timestamp;

  // The delegate provided to the StackCopier.
  StackCopier::Delegate* stack_copier_delegate;
};

// Pointer to the parameters to be "passed" to the CopyStackSignalHandler() from
// the sampling thread to the sampled (stopped) thread. This value is set just
// before sending the signal to the thread and reset when the handler is done.
std::atomic<HandlerParams*> g_handler_params;

// CopyStackSignalHandler is invoked on the stopped thread and records the
// thread's stack and register context at the time the signal was received. This
// function may only call reentrant code.
void CopyStackSignalHandler(int n, siginfo_t* siginfo, void* sigcontext) {
  HandlerParams* params = g_handler_params.load(std::memory_order_acquire);

  // TimeTicks::Now() is implemented in terms of clock_gettime on Linux, which
  // is signal safe per the signal-safety(7) man page.
  *params->timestamp = TimeTicks::Now();

  ScopedEventSignaller e(params->event);
  *params->success = false;

  const ucontext_t* ucontext = static_cast<ucontext_t*>(sigcontext);
  memcpy(params->context, &ucontext->uc_mcontext, sizeof(mcontext_t));

  const uintptr_t bottom = RegisterContextStackPointer(params->context);
  const uintptr_t top = params->stack_base_address;
  if ((top - bottom) > params->stack_buffer->size()) {
    // The stack exceeds the size of the allocated buffer. The buffer is sized
    // such that this shouldn't happen under typical execution so we can safely
    // punt in this situation.
    return;
  }

  params->stack_copier_delegate->OnStackCopy();

  *params->stack_copy_bottom =
      StackCopierSignal::CopyStackContentsAndRewritePointers(
          reinterpret_cast<uint8_t*>(bottom), reinterpret_cast<uintptr_t*>(top),
          StackBuffer::kPlatformStackAlignment, params->stack_buffer->buffer());

  *params->success = true;
}

// Sets the global handler params for the signal handler function.
class ScopedSetSignalHandlerParams {
 public:
  ScopedSetSignalHandlerParams(HandlerParams* params) {
    g_handler_params.store(params, std::memory_order_release);
  }

  ~ScopedSetSignalHandlerParams() {
    g_handler_params.store(nullptr, std::memory_order_release);
  }
};

class ScopedSigaction {
 public:
  ScopedSigaction(int signal,
                  struct sigaction* action,
                  struct sigaction* original_action)
      : signal_(signal),
        action_(action),
        original_action_(original_action),
        succeeded_(sigaction(signal, action, original_action) == 0) {}

  bool succeeded() const { return succeeded_; }

  ~ScopedSigaction() {
    if (!succeeded_)
      return;

    bool reset_succeeded = sigaction(signal_, original_action_, action_) == 0;
    DCHECK(reset_succeeded);
  }

 private:
  const int signal_;
  struct sigaction* const action_;
  struct sigaction* const original_action_;
  const bool succeeded_;
};

}  // namespace

StackCopierSignal::StackCopierSignal(
    std::unique_ptr<ThreadDelegate> thread_delegate)
    : thread_delegate_(std::move(thread_delegate)) {}

StackCopierSignal::~StackCopierSignal() = default;

bool StackCopierSignal::CopyStack(StackBuffer* stack_buffer,
                                  uintptr_t* stack_top,
                                  TimeTicks* timestamp,
                                  RegisterContext* thread_context,
                                  Delegate* delegate) {
  AsyncSafeWaitableEvent wait_event;
  bool copied = false;
  const uint8_t* stack_copy_bottom = nullptr;
  const uintptr_t stack_base_address = thread_delegate_->GetStackBaseAddress();
  HandlerParams params = {stack_base_address, &wait_event,  &copied,
                          thread_context,     stack_buffer, &stack_copy_bottom,
                          timestamp,          delegate};
  {
    ScopedSetSignalHandlerParams scoped_handler_params(&params);

    // Set the signal handler for the thread to the stack copy function.
    struct sigaction action;
    struct sigaction original_action;
    memset(&action, 0, sizeof(action));
    action.sa_sigaction = CopyStackSignalHandler;
    action.sa_flags = SA_RESTART | SA_SIGINFO;
    sigemptyset(&action.sa_mask);
    TRACE_EVENT_BEGIN0(TRACE_DISABLED_BY_DEFAULT("cpu_profiler.debug"),
                       "StackCopierSignal copy stack");
    // SIGURG is chosen here because we observe no crashes with this signal and
    // neither Chrome or the AOSP sets up a special handler for this signal.
    ScopedSigaction scoped_sigaction(SIGURG, &action, &original_action);
    if (!scoped_sigaction.succeeded())
      return false;

    if (syscall(SYS_tgkill, getpid(), thread_delegate_->GetThreadId(),
                SIGURG) != 0) {
      NOTREACHED();
      return false;
    }
    bool finished_waiting = wait_event.Wait();
    TRACE_EVENT_END0(TRACE_DISABLED_BY_DEFAULT("cpu_profiler.debug"),
                     "StackCopierSignal copy stack");
    if (!finished_waiting) {
      NOTREACHED();
      return false;
    }
  }

  delegate->OnThreadResume();

  const uintptr_t bottom = RegisterContextStackPointer(params.context);
  for (uintptr_t* reg :
       thread_delegate_->GetRegistersToRewrite(thread_context)) {
    *reg = StackCopierSignal::RewritePointerIfInOriginalStack(
        reinterpret_cast<uint8_t*>(bottom),
        reinterpret_cast<uintptr_t*>(stack_base_address), stack_copy_bottom,
        *reg);
  }

  *stack_top = reinterpret_cast<uintptr_t>(stack_copy_bottom) +
               (stack_base_address - bottom);

  return copied;
}

}  // namespace base
