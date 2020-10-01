/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/native_api/stacktrace/stacktrace.h"

#include <dlfcn.h>
#include <errno.h>
#include <linux/futex.h>
#include <sys/ptrace.h>
#include <sys/ucontext.h>
#include <syscall.h>
#include <ucontext.h>
#include <unistd.h>
#include <unwind.h>
#include <atomic>

// ptrace.h is polluting the namespace. Clean up to avoid conflicts with rtc.
#if defined(DS)
#undef DS
#endif

#include "absl/base/attributes.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/synchronization/mutex.h"

namespace webrtc {

namespace {

// Maximum stack trace depth we allow before aborting.
constexpr size_t kMaxStackSize = 100;
// Signal that will be used to interrupt threads. SIGURG ("Urgent condition on
// socket") is chosen because Android does not set up a specific handler for
// this signal.
constexpr int kSignal = SIGURG;

// Note: This class is only meant for use within this file, and for the
// simplified use case of a single Wait() and a single Signal(), followed by
// discarding the object (never reused).
// This is a replacement of rtc::Event that is async-safe and doesn't use
// pthread api. This is necessary since signal handlers cannot allocate memory
// or use pthread api. This class is ported from Chromium.
class AsyncSafeWaitableEvent {
 public:
  AsyncSafeWaitableEvent() {
    std::atomic_store_explicit(&futex_, 0, std::memory_order_release);
  }

  ~AsyncSafeWaitableEvent() {}

  // Returns false in the event of an error and errno is set to indicate the
  // cause of the error.
  bool Wait() {
    // futex() can wake up spuriously if this memory address was previously used
    // for a pthread mutex. So, also check the condition.
    while (true) {
      int res = syscall(SYS_futex, &futex_, FUTEX_WAIT | FUTEX_PRIVATE_FLAG, 0,
                        nullptr, nullptr, 0);
      if (std::atomic_load_explicit(&futex_, std::memory_order_acquire) != 0)
        return true;
      if (res != 0)
        return false;
    }
  }

  void Signal() {
    std::atomic_store_explicit(&futex_, 1, std::memory_order_release);
    syscall(SYS_futex, &futex_, FUTEX_WAKE | FUTEX_PRIVATE_FLAG, 1, nullptr,
            nullptr, 0);
  }

 private:
  std::atomic<int> futex_;
};

// Struct to store the arguments to the signal handler.
struct SignalHandlerOutputState {
  // This event is signalled when signal handler is done executing.
  AsyncSafeWaitableEvent signal_handler_finish_event;
  // Running counter of array index below.
  size_t stack_size_counter = 0;
  // Array storing the stack trace.
  uintptr_t addresses[kMaxStackSize];
};

// Global lock to ensure only one thread gets interrupted at a time.
ABSL_CONST_INIT GlobalMutex g_signal_handler_lock(absl::kConstInit);
// Argument passed to the ThreadSignalHandler() from the sampling thread to the
// sampled (stopped) thread. This value is set just before sending signal to the
// thread and reset when handler is done.
SignalHandlerOutputState* volatile g_signal_handler_output_state;

// This function is called iteratively for each stack trace element and stores
// the element in the array from |unwind_output_state|.
_Unwind_Reason_Code UnwindBacktrace(struct _Unwind_Context* unwind_context,
                                    void* unwind_output_state) {
  SignalHandlerOutputState* const output_state =
      static_cast<SignalHandlerOutputState*>(unwind_output_state);

  // Abort if output state is corrupt.
  if (output_state == nullptr)
    return _URC_END_OF_STACK;

  // Avoid overflowing the stack trace array.
  if (output_state->stack_size_counter >= kMaxStackSize)
    return _URC_END_OF_STACK;

  // Store the instruction pointer in the array. Subtract 2 since we want to get
  // the call instruction pointer, not the return address which is the
  // instruction after.
  output_state->addresses[output_state->stack_size_counter] =
      _Unwind_GetIP(unwind_context) - 2;
  ++output_state->stack_size_counter;

  return _URC_NO_REASON;
}

// This signal handler is exectued on the interrupted thread.
void SignalHandler(int signum, siginfo_t* info, void* ptr) {
  // This should have been set by the thread requesting the stack trace.
  SignalHandlerOutputState* signal_handler_output_state =
      g_signal_handler_output_state;
  if (signal_handler_output_state != nullptr) {
    _Unwind_Backtrace(&UnwindBacktrace, signal_handler_output_state);
    signal_handler_output_state->signal_handler_finish_event.Signal();
  }
}

// Temporarily change the signal handler to a function that records a raw stack
// trace and interrupt the given tid. This function will block until the output
// thread stack trace has been stored in |params|. The return value is an error
// string on failure and null on success.
const char* CaptureRawStacktrace(int pid,
                                 int tid,
                                 SignalHandlerOutputState* params) {
  // This function is under a global lock since we are changing the signal
  // handler and using global state for the output. The lock is to ensure only
  // one thread at a time gets captured. The lock also means we need to be very
  // careful with what statements we put in this function, and we should even
  // avoid logging here.
  struct sigaction act;
  struct sigaction old_act;
  memset(&act, 0, sizeof(act));
  act.sa_sigaction = &SignalHandler;
  act.sa_flags = SA_RESTART | SA_SIGINFO;
  sigemptyset(&act.sa_mask);

  GlobalMutexLock ls(&g_signal_handler_lock);
  g_signal_handler_output_state = params;

  if (sigaction(kSignal, &act, &old_act) != 0)
    return "Failed to change signal action";

  // Interrupt the thread which will execute SignalHandler() on the given
  // thread.
  if (tgkill(pid, tid, kSignal) != 0)
    return "Failed to interrupt thread";

  // Wait until the thread is done recording its stack trace.
  if (!params->signal_handler_finish_event.Wait())
    return "Failed to wait for thread to finish stack trace";

  // Restore previous signal handler.
  sigaction(kSignal, &old_act, /* old_act= */ nullptr);

  return nullptr;
}

// Translate addresses into symbolic information using dladdr().
std::vector<StackTraceElement> FormatStackTrace(
    const SignalHandlerOutputState& params) {
  std::vector<StackTraceElement> stack_trace;
  for (size_t i = 0; i < params.stack_size_counter; ++i) {
    const uintptr_t address = params.addresses[i];

    Dl_info dl_info = {};
    if (!dladdr(reinterpret_cast<void*>(address), &dl_info)) {
      RTC_LOG(LS_WARNING)
          << "Could not translate address to symbolic information for address "
          << address << " at stack depth " << i;
      continue;
    }

    StackTraceElement stack_trace_element;
    stack_trace_element.shared_object_path = dl_info.dli_fname;
    stack_trace_element.relative_address = static_cast<uint32_t>(
        address - reinterpret_cast<uintptr_t>(dl_info.dli_fbase));
    stack_trace_element.symbol_name = dl_info.dli_sname;

    stack_trace.push_back(stack_trace_element);
  }

  return stack_trace;
}

}  // namespace

std::vector<StackTraceElement> GetStackTrace(int tid) {
  // Only a thread itself can unwind its stack, so we will interrupt the given
  // tid with a custom signal handler in order to unwind its stack. The stack
  // will be recorded to |params| through the use of the global pointer
  // |g_signal_handler_param|.
  SignalHandlerOutputState params;

  const char* error_string = CaptureRawStacktrace(getpid(), tid, &params);
  if (error_string != nullptr) {
    RTC_LOG(LS_ERROR) << error_string << ". tid: " << tid
                      << ". errno: " << errno;
    return {};
  }
  if (params.stack_size_counter >= kMaxStackSize) {
    RTC_LOG(LS_WARNING) << "Stack trace for thread " << tid << " was truncated";
  }
  return FormatStackTrace(params);
}

std::vector<StackTraceElement> GetStackTrace() {
  SignalHandlerOutputState params;
  _Unwind_Backtrace(&UnwindBacktrace, &params);
  if (params.stack_size_counter >= kMaxStackSize) {
    RTC_LOG(LS_WARNING) << "Stack trace was truncated";
  }
  return FormatStackTrace(params);
}

std::string StackTraceToString(
    const std::vector<StackTraceElement>& stack_trace) {
  rtc::StringBuilder string_builder;

  for (size_t i = 0; i < stack_trace.size(); ++i) {
    const StackTraceElement& stack_trace_element = stack_trace[i];
    string_builder.AppendFormat(
        "#%02zu pc %08x %s", i,
        static_cast<uint32_t>(stack_trace_element.relative_address),
        stack_trace_element.shared_object_path);
    // The symbol name is only available for unstripped .so files.
    if (stack_trace_element.symbol_name != nullptr)
      string_builder.AppendFormat(" %s", stack_trace_element.symbol_name);

    string_builder.AppendFormat("\n");
  }

  return string_builder.Release();
}

}  // namespace webrtc
