// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/profiler/native_unwinder_win.h"

#include <winnt.h>

#include "base/profiler/native_unwinder.h"
#include "base/profiler/win32_stack_frame_unwinder.h"

namespace base {

bool NativeUnwinderWin::CanUnwindFrom(const Frame& current_frame) const {
  return current_frame.module && current_frame.module->IsNative();
}

// Attempts to unwind the frame represented by the context values. If
// successful appends frames onto the stack and returns true. Otherwise
// returns false.
UnwindResult NativeUnwinderWin::TryUnwind(RegisterContext* thread_context,
                                          uintptr_t stack_top,
                                          ModuleCache* module_cache,
                                          std::vector<Frame>* stack) const {
  // We expect the frame correponding to the |thread_context| register state to
  // exist within |stack|.
  DCHECK_GT(stack->size(), 0u);

  Win32StackFrameUnwinder frame_unwinder;
  for (;;) {
    if (!stack->back().module) {
      // There's no loaded module corresponding to the current frame. This can
      // be due to executing code not in a module (e.g. runtime-generated code
      // associated with third-party injected DLLs) or the module having been
      // unloaded since we recorded the stack. In the latter case the function
      // unwind information was part of the unloaded module, so it's not
      // possible to unwind further.
      //
      // NB: if a module was found it's still theoretically possible for the
      // detected module module to be different than the one that was loaded
      // when the stack was copied, if the module was unloaded and a different
      // module loaded in overlapping memory. This likely would cause a crash
      // but has not been observed in practice.
      return UnwindResult::ABORTED;
    }

    if (!stack->back().module->IsNative()) {
      // This is a non-native module associated with the auxiliary unwinder
      // (e.g. corresponding to a frame in V8 generated code). Report as
      // UNRECOGNIZED_FRAME to allow that unwinder to unwind the frame.
      return UnwindResult::UNRECOGNIZED_FRAME;
    }

    uintptr_t prev_stack_pointer = RegisterContextStackPointer(thread_context);
    if (!frame_unwinder.TryUnwind(stack->size() == 1u, thread_context,
                                  stack->back().module)) {
      return UnwindResult::ABORTED;
    }

    if (ContextPC(thread_context) == 0)
      return UnwindResult::COMPLETED;

    // Exclusive range of expected stack pointer values after the unwind.
    struct {
      uintptr_t start;
      uintptr_t end;
    } expected_stack_pointer_range = {prev_stack_pointer, stack_top};

    // Abort if the unwind produced an invalid stack pointer.
#if defined(ARCH_CPU_ARM64)
    // Leaf frames on Arm can re-use the stack pointer, so they can validly have
    // the same stack pointer as the previous frame.
    if (stack->size() == 1u) {
      expected_stack_pointer_range.start--;
    }
#endif
    if (RegisterContextStackPointer(thread_context) <=
            expected_stack_pointer_range.start ||
        RegisterContextStackPointer(thread_context) >=
            expected_stack_pointer_range.end) {
      return UnwindResult::ABORTED;
    }

    // Record the frame to which we just unwound.
    stack->emplace_back(
        ContextPC(thread_context),
        module_cache->GetModuleForAddress(ContextPC(thread_context)));
  }

  NOTREACHED();
  return UnwindResult::COMPLETED;
}

std::unique_ptr<Unwinder> CreateNativeUnwinder(ModuleCache* module_cache) {
  return std::make_unique<NativeUnwinderWin>();
}

}  // namespace base
