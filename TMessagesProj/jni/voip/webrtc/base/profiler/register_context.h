// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
//
// This file provides the RegisterContext cross-platform typedef that represents
// the native register context for the platform, plus functions that provide
// access to key registers in the context.

#ifndef BASE_PROFILER_REGISTER_CONTEXT_H_
#define BASE_PROFILER_REGISTER_CONTEXT_H_

#include <cstdint>

#include "build/build_config.h"

#if defined(OS_WIN)
#include <windows.h>
#elif defined(OS_MACOSX)
#include <mach/machine/thread_status.h>
#elif defined(OS_ANDROID) || defined(OS_LINUX)
#include <sys/ucontext.h>
#endif

namespace base {

// Helper function to account for the fact that platform-specific register state
// types may be of the same size as uintptr_t, but not of the same type or
// signedness -- e.g. unsigned int vs. unsigned long on 32-bit Windows, unsigned
// long vs. unsigned long long on Mac, long long vs. unsigned long long on
// Linux.
template <typename T>
uintptr_t& AsUintPtr(T* value) {
  static_assert(sizeof(T) == sizeof(uintptr_t),
                "register state type must be of equivalent size to uintptr_t");
  return *reinterpret_cast<uintptr_t*>(value);
}

#if defined(OS_WIN)

using RegisterContext = ::CONTEXT;

inline uintptr_t& RegisterContextStackPointer(::CONTEXT* context) {
#if defined(ARCH_CPU_X86_64)
  return context->Rsp;
#elif defined(ARCH_CPU_ARM64)
  return context->Sp;
#else
  return AsUintPtr(&context->Esp);
#endif
}

inline uintptr_t& RegisterContextFramePointer(::CONTEXT* context) {
#if defined(ARCH_CPU_X86_64)
  return context->Rbp;
#elif defined(ARCH_CPU_ARM64)
  return context->Fp;
#else
  return AsUintPtr(&context->Ebp);
#endif
}

inline uintptr_t& RegisterContextInstructionPointer(::CONTEXT* context) {
#if defined(ARCH_CPU_X86_64)
  return context->Rip;
#elif defined(ARCH_CPU_ARM64)
  return context->Pc;
#else
  return AsUintPtr(&context->Eip);
#endif
}

#elif defined(OS_MACOSX) && !defined(OS_IOS)  // #if defined(OS_WIN)

using RegisterContext = x86_thread_state64_t;

inline uintptr_t& RegisterContextStackPointer(x86_thread_state64_t* context) {
  return AsUintPtr(&context->__rsp);
}

inline uintptr_t& RegisterContextFramePointer(x86_thread_state64_t* context) {
  return AsUintPtr(&context->__rbp);
}

inline uintptr_t& RegisterContextInstructionPointer(
    x86_thread_state64_t* context) {
  return AsUintPtr(&context->__rip);
}

#elif defined(OS_ANDROID) || defined(OS_LINUX)  // #if defined(OS_WIN)

using RegisterContext = mcontext_t;

#if defined(ARCH_CPU_ARM_FAMILY) && defined(ARCH_CPU_32_BITS)

inline uintptr_t& RegisterContextStackPointer(mcontext_t* context) {
  return AsUintPtr(&context->arm_sp);
}

inline uintptr_t& RegisterContextFramePointer(mcontext_t* context) {
  return AsUintPtr(&context->arm_fp);
}

inline uintptr_t& RegisterContextInstructionPointer(mcontext_t* context) {
  return AsUintPtr(&context->arm_ip);
}

#elif defined(ARCH_CPU_ARM_FAMILY) && defined(ARCH_CPU_64_BITS)

inline uintptr_t& RegisterContextStackPointer(mcontext_t* context) {
  return AsUintPtr(&context->sp);
}

inline uintptr_t& RegisterContextFramePointer(mcontext_t* context) {
  // r29 is the FP register on 64-bit ARM per the Procedure Call Standard,
  // section 5.1.1.
  return AsUintPtr(&context->regs[29]);
}

inline uintptr_t& RegisterContextInstructionPointer(mcontext_t* context) {
  return AsUintPtr(&context->pc);
}

#elif defined(ARCH_CPU_X86_FAMILY) && defined(ARCH_CPU_32_BITS)

inline uintptr_t& RegisterContextStackPointer(mcontext_t* context) {
  return AsUintPtr(&context->gregs[REG_ESP]);
}

inline uintptr_t& RegisterContextFramePointer(mcontext_t* context) {
  return AsUintPtr(&context->gregs[REG_EBP]);
}

inline uintptr_t& RegisterContextInstructionPointer(mcontext_t* context) {
  return AsUintPtr(&context->gregs[REG_EIP]);
}

#elif defined(ARCH_CPU_X86_FAMILY) && defined(ARCH_CPU_64_BITS)

inline uintptr_t& RegisterContextStackPointer(mcontext_t* context) {
  return AsUintPtr(&context->gregs[REG_RSP]);
}

inline uintptr_t& RegisterContextFramePointer(mcontext_t* context) {
  return AsUintPtr(&context->gregs[REG_RBP]);
}

inline uintptr_t& RegisterContextInstructionPointer(mcontext_t* context) {
  return AsUintPtr(&context->gregs[REG_RIP]);
}

#else  // #if defined(ARCH_CPU_ARM_FAMILY) && defined(ARCH_CPU_32_BITS)

// Placeholders for other POSIX platforms that just return the first
// three register slots in the context.
inline uintptr_t& RegisterContextStackPointer(mcontext_t* context) {
  return *reinterpret_cast<uintptr_t*>(context);
}

inline uintptr_t& RegisterContextFramePointer(mcontext_t* context) {
  return *(reinterpret_cast<uintptr_t*>(context) + 1);
}

inline uintptr_t& RegisterContextInstructionPointer(mcontext_t* context) {
  return *(reinterpret_cast<uintptr_t*>(context) + 2);
}

#endif  // #if defined(ARCH_CPU_ARM_FAMILY) && defined(ARCH_CPU_32_BITS)

#else  // #if defined(OS_WIN)

// Placeholders for other platforms.
struct RegisterContext {
  uintptr_t stack_pointer;
  uintptr_t frame_pointer;
  uintptr_t instruction_pointer;
};

inline uintptr_t& RegisterContextStackPointer(RegisterContext* context) {
  return context->stack_pointer;
}

inline uintptr_t& RegisterContextFramePointer(RegisterContext* context) {
  return context->frame_pointer;
}

inline uintptr_t& RegisterContextInstructionPointer(RegisterContext* context) {
  return context->instruction_pointer;
}

#endif  // #if defined(OS_WIN)

}  // namespace base

#endif  // BASE_PROFILER_REGISTER_CONTEXT_H_
