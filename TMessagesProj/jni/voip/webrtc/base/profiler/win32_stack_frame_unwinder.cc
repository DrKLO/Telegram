// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/profiler/win32_stack_frame_unwinder.h"

#include <windows.h>

#include <utility>

#include "base/macros.h"
#include "build/build_config.h"

namespace base {

// Win32UnwindFunctions -------------------------------------------------------

namespace {

// Implements the UnwindFunctions interface for the corresponding Win32
// functions.
class Win32UnwindFunctions : public Win32StackFrameUnwinder::UnwindFunctions {
 public:
  Win32UnwindFunctions();
  ~Win32UnwindFunctions() override;

  PRUNTIME_FUNCTION LookupFunctionEntry(DWORD64 program_counter,
                                        PDWORD64 image_base) override;

  void VirtualUnwind(DWORD64 image_base,
                     DWORD64 program_counter,
                     PRUNTIME_FUNCTION runtime_function,
                     CONTEXT* context) override;

 private:
  DISALLOW_COPY_AND_ASSIGN(Win32UnwindFunctions);
};

Win32UnwindFunctions::Win32UnwindFunctions() {}
Win32UnwindFunctions::~Win32UnwindFunctions() {}

PRUNTIME_FUNCTION Win32UnwindFunctions::LookupFunctionEntry(
    DWORD64 program_counter,
    PDWORD64 image_base) {
#ifdef _WIN64
  return ::RtlLookupFunctionEntry(program_counter, image_base, nullptr);
#else
  NOTREACHED();
  return nullptr;
#endif
}

void Win32UnwindFunctions::VirtualUnwind(DWORD64 image_base,
                                         DWORD64 program_counter,
                                         PRUNTIME_FUNCTION runtime_function,
                                         CONTEXT* context) {
#ifdef _WIN64
  void* handler_data = nullptr;
  ULONG64 establisher_frame;
  KNONVOLATILE_CONTEXT_POINTERS nvcontext = {};
  ::RtlVirtualUnwind(UNW_FLAG_NHANDLER, image_base, program_counter,
                     runtime_function, context, &handler_data,
                     &establisher_frame, &nvcontext);
#else
  NOTREACHED();
#endif
}

}  // namespace

// Win32StackFrameUnwinder ----------------------------------------------------

Win32StackFrameUnwinder::UnwindFunctions::~UnwindFunctions() = default;
Win32StackFrameUnwinder::UnwindFunctions::UnwindFunctions() = default;

Win32StackFrameUnwinder::Win32StackFrameUnwinder()
    : Win32StackFrameUnwinder(std::make_unique<Win32UnwindFunctions>()) {}

Win32StackFrameUnwinder::~Win32StackFrameUnwinder() {}

bool Win32StackFrameUnwinder::TryUnwind(
    bool at_top_frame,
    CONTEXT* context,
    // The module parameter, while not directly used, is still passed because it
    // represents an implicit dependency for this function. Having the Module
    // ensures that we have incremented the HMODULE reference count, which is
    // critical to ensuring that the module is not unloaded during the
    // unwinding. Otherwise the module could be unloaded between the
    // LookupFunctionEntry and VirtualUnwind calls, resulting in crashes
    // accessing unwind information from the unloaded module.
    const ModuleCache::Module* module) {
#ifdef _WIN64
  // Ensure we found a valid module for the program counter.
  DCHECK(module);
  ULONG64 image_base;
  // Try to look up unwind metadata for the current function.
  PRUNTIME_FUNCTION runtime_function =
      unwind_functions_->LookupFunctionEntry(ContextPC(context), &image_base);
  DCHECK_EQ(module->GetBaseAddress(), image_base);

  if (runtime_function) {
    unwind_functions_->VirtualUnwind(image_base, ContextPC(context),
                                     runtime_function, context);
    return true;
  }

  if (at_top_frame) {
    // This is a leaf function (i.e. a function that neither calls a function,
    // nor allocates any stack space itself).
#if defined(ARCH_CPU_X86_64)
    // For X64, return address is at RSP.
    context->Rip = *reinterpret_cast<DWORD64*>(context->Rsp);
    context->Rsp += 8;
#elif defined(ARCH_CPU_ARM64)
    // For leaf function on Windows ARM64, return address is at LR(X30).  Add
    // CONTEXT_UNWOUND_TO_CALL flag to avoid unwind ambiguity for tailcall on
    // ARM64, because padding after tailcall is not guaranteed.
    context->Pc = context->Lr;
    context->ContextFlags |= CONTEXT_UNWOUND_TO_CALL;
#else
#error Unsupported Windows 64-bit Arch
#endif
    return true;
  }

  // In theory we shouldn't get here, as it means we've encountered a function
  // without unwind information below the top of the stack, which is forbidden
  // by the Microsoft x64 calling convention.
  //
  // The one known case in Chrome code that executes this path occurs because
  // of BoringSSL unwind information inconsistent with the actual function
  // code. See https://crbug.com/542919.
  return false;
#else
  NOTREACHED();
  return false;
#endif
}

Win32StackFrameUnwinder::Win32StackFrameUnwinder(
    std::unique_ptr<UnwindFunctions> unwind_functions)
    : unwind_functions_(std::move(unwind_functions)) {}

}  // namespace base
