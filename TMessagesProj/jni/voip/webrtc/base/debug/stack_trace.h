// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_DEBUG_STACK_TRACE_H_
#define BASE_DEBUG_STACK_TRACE_H_

#include <stddef.h>

#include <iosfwd>
#include <string>

#include "base/base_export.h"
#include "base/debug/debugging_buildflags.h"
#include "base/macros.h"
#include "build/build_config.h"

#if defined(OS_POSIX)
#if !defined(OS_NACL)
#include <signal.h>
#endif
#include <unistd.h>
#endif

#if defined(OS_WIN)
struct _EXCEPTION_POINTERS;
struct _CONTEXT;
#endif

namespace base {
namespace debug {

// Enables stack dump to console output on exception and signals.
// When enabled, the process will quit immediately. This is meant to be used in
// unit_tests only! This is not thread-safe: only call from main thread.
// In sandboxed processes, this has to be called before the sandbox is turned
// on.
// Calling this function on Linux opens /proc/self/maps and caches its
// contents. In non-official builds, this function also opens the object files
// that are loaded in memory and caches their file descriptors (this cannot be
// done in official builds because it has security implications).
BASE_EXPORT bool EnableInProcessStackDumping();

#if defined(OS_POSIX) && !defined(OS_NACL)
// Sets a first-chance callback for the stack dump signal handler. This callback
// is called at the beginning of the signal handler to handle special kinds of
// signals, like out-of-bounds memory accesses in WebAssembly (WebAssembly Trap
// Handler).
// {SetStackDumpFirstChanceCallback} returns {true} if the callback
// has been set correctly. It returns {false} if the stack dump signal handler
// has not been registered with the OS, e.g. because of ASAN.
BASE_EXPORT bool SetStackDumpFirstChanceCallback(bool (*handler)(int,
                                                                 siginfo_t*,
                                                                 void*));
#endif

// Returns end of the stack, or 0 if we couldn't get it.
#if BUILDFLAG(CAN_UNWIND_WITH_FRAME_POINTERS)
BASE_EXPORT uintptr_t GetStackEnd();
#endif

// A stacktrace can be helpful in debugging. For example, you can include a
// stacktrace member in a object (probably around #ifndef NDEBUG) so that you
// can later see where the given object was created from.
class BASE_EXPORT StackTrace {
 public:
  // Creates a stacktrace from the current location.
  StackTrace();

  // Creates a stacktrace from the current location, of up to |count| entries.
  // |count| will be limited to at most |kMaxTraces|.
  explicit StackTrace(size_t count);

  // Creates a stacktrace from an existing array of instruction
  // pointers (such as returned by Addresses()).  |count| will be
  // limited to at most |kMaxTraces|.
  StackTrace(const void* const* trace, size_t count);

#if defined(OS_WIN)
  // Creates a stacktrace for an exception.
  // Note: this function will throw an import not found (StackWalk64) exception
  // on system without dbghelp 5.1.
  StackTrace(_EXCEPTION_POINTERS* exception_pointers);
  StackTrace(const _CONTEXT* context);
#endif

  // Copying and assignment are allowed with the default functions.

  // Gets an array of instruction pointer values. |*count| will be set to the
  // number of elements in the returned array. Addresses()[0] will contain an
  // address from the leaf function, and Addresses()[count-1] will contain an
  // address from the root function (i.e.; the thread's entry point).
  const void* const* Addresses(size_t* count) const;

  // Prints the stack trace to stderr.
  void Print() const;

  // Prints the stack trace to stderr, prepending the given string before
  // each output line.
  void PrintWithPrefix(const char* prefix_string) const;

#if !defined(__UCLIBC__) & !defined(_AIX)
  // Resolves backtrace to symbols and write to stream.
  void OutputToStream(std::ostream* os) const;
  // Resolves backtrace to symbols and write to stream, with the provided
  // prefix string prepended to each line.
  void OutputToStreamWithPrefix(std::ostream* os,
                                const char* prefix_string) const;
#endif

  // Resolves backtrace to symbols and returns as string.
  std::string ToString() const;

  // Resolves backtrace to symbols and returns as string, prepending the
  // provided prefix string to each line.
  std::string ToStringWithPrefix(const char* prefix_string) const;

 private:
#if defined(OS_WIN)
  void InitTrace(const _CONTEXT* context_record);
#endif

#if defined(OS_ANDROID)
  // TODO(https://crbug.com/925525): Testing indicates that Android has issues
  // with a larger value here, so leave Android at 62.
  static constexpr int kMaxTraces = 62;
#else
  // For other platforms, use 250. This seems reasonable without
  // being huge.
  static constexpr int kMaxTraces = 250;
#endif

  void* trace_[kMaxTraces];

  // The number of valid frames in |trace_|.
  size_t count_;
};

// Forwards to StackTrace::OutputToStream().
BASE_EXPORT std::ostream& operator<<(std::ostream& os, const StackTrace& s);

// Record a stack trace with up to |count| frames into |trace|. Returns the
// number of frames read.
BASE_EXPORT size_t CollectStackTrace(void** trace, size_t count);

#if BUILDFLAG(CAN_UNWIND_WITH_FRAME_POINTERS)
// Traces the stack by using frame pointers. This function is faster but less
// reliable than StackTrace. It should work for debug and profiling builds,
// but not for release builds (although there are some exceptions).
//
// Writes at most |max_depth| frames (instruction pointers) into |out_trace|
// after skipping |skip_initial| frames. Note that the function itself is not
// added to the trace so |skip_initial| should be 0 in most cases.
// Returns number of frames written.
BASE_EXPORT size_t TraceStackFramePointers(const void** out_trace,
                                           size_t max_depth,
                                           size_t skip_initial);

// Links stack frame |fp| to |parent_fp|, so that during stack unwinding
// TraceStackFramePointers() visits |parent_fp| after visiting |fp|.
// Both frame pointers must come from __builtin_frame_address().
// Destructor restores original linkage of |fp| to avoid corrupting caller's
// frame register on return.
//
// This class can be used to repair broken stack frame chain in cases
// when execution flow goes into code built without frame pointers:
//
// void DoWork() {
//   Call_SomeLibrary();
// }
// static __thread void*  g_saved_fp;
// void Call_SomeLibrary() {
//   g_saved_fp = __builtin_frame_address(0);
//   some_library_call(...); // indirectly calls SomeLibrary_Callback()
// }
// void SomeLibrary_Callback() {
//   ScopedStackFrameLinker linker(__builtin_frame_address(0), g_saved_fp);
//   ...
//   TraceStackFramePointers(...);
// }
//
// This produces the following trace:
//
// #0 SomeLibrary_Callback()
// #1 <address of the code inside SomeLibrary that called #0>
// #2 DoWork()
// ...rest of the trace...
//
// SomeLibrary doesn't use frame pointers, so when SomeLibrary_Callback()
// is called, stack frame register contains bogus value that becomes callback'
// parent frame address. Without ScopedStackFrameLinker unwinding would've
// stopped at that bogus frame address yielding just two first frames (#0, #1).
// ScopedStackFrameLinker overwrites callback's parent frame address with
// Call_SomeLibrary's frame, so unwinder produces full trace without even
// noticing that stack frame chain was broken.
class BASE_EXPORT ScopedStackFrameLinker {
 public:
  ScopedStackFrameLinker(void* fp, void* parent_fp);
  ~ScopedStackFrameLinker();

 private:
  void* fp_;
  void* parent_fp_;
  void* original_parent_fp_;

  DISALLOW_COPY_AND_ASSIGN(ScopedStackFrameLinker);
};

#endif  // BUILDFLAG(CAN_UNWIND_WITH_FRAME_POINTERS)

namespace internal {

#if defined(OS_POSIX) && !defined(OS_ANDROID)
// POSIX doesn't define any async-signal safe function for converting
// an integer to ASCII. We'll have to define our own version.
// itoa_r() converts a (signed) integer to ASCII. It returns "buf", if the
// conversion was successful or NULL otherwise. It never writes more than "sz"
// bytes. Output will be truncated as needed, and a NUL character is always
// appended.
BASE_EXPORT char *itoa_r(intptr_t i,
                         char *buf,
                         size_t sz,
                         int base,
                         size_t padding);
#endif  // defined(OS_POSIX) && !defined(OS_ANDROID)

}  // namespace internal

}  // namespace debug
}  // namespace base

#endif  // BASE_DEBUG_STACK_TRACE_H_
