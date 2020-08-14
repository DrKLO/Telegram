// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/profiler/native_unwinder_mac.h"

#include <mach-o/compact_unwind_encoding.h>
#include <mach/mach.h>
#include <mach/vm_map.h>
#include <sys/ptrace.h>

#include "base/logging.h"
#include "base/profiler/module_cache.h"
#include "base/profiler/native_unwinder.h"
#include "base/profiler/profile_builder.h"

extern "C" {
void _sigtramp(int, int, struct sigset*);
}

namespace base {

namespace {

// Extracts the "frame offset" for a given frame from the compact unwind info.
// A frame offset indicates the location of saved non-volatile registers in
// relation to the frame pointer. See |mach-o/compact_unwind_encoding.h| for
// details.
uint32_t GetFrameOffset(int compact_unwind_info) {
  // The frame offset lives in bytes 16-23. This shifts it down by the number of
  // leading zeroes in the mask, then masks with (1 << number of one bits in the
  // mask) - 1, turning 0x00FF0000 into 0x000000FF. Adapted from |EXTRACT_BITS|
  // in libunwind's CompactUnwinder.hpp.
  return (
      (compact_unwind_info >> __builtin_ctz(UNWIND_X86_64_RBP_FRAME_OFFSET)) &
      (((1 << __builtin_popcount(UNWIND_X86_64_RBP_FRAME_OFFSET))) - 1));
}

// True if the unwind from |leaf_frame_module| may trigger a crash bug in
// unw_init_local. If so, the stack walk should be aborted at the leaf frame.
bool MayTriggerUnwInitLocalCrash(const ModuleCache::Module* leaf_frame_module) {
  // The issue here is a bug in unw_init_local that, in some unwinds, results in
  // attempts to access memory at the address immediately following the address
  // range of the library. When the library is the last of the mapped libraries
  // that address is in a different memory region. Starting with 10.13.4 beta
  // releases it appears that this region is sometimes either unmapped or mapped
  // without read access, resulting in crashes on the attempted access. It's not
  // clear what circumstances result in this situation; attempts to reproduce on
  // a 10.13.4 beta did not trigger the issue.
  //
  // The workaround is to check if the memory address that would be accessed is
  // readable, and if not, abort the stack walk before calling unw_init_local.
  // As of 2018/03/19 about 0.1% of non-idle stacks on the UI and GPU main
  // threads have a leaf frame in the last library. Since the issue appears to
  // only occur some of the time it's expected that the quantity of lost samples
  // will be lower than 0.1%, possibly significantly lower.
  //
  // TODO(lgrey): Add references above to LLVM/Radar bugs on unw_init_local once
  // filed.
  uint64_t unused;
  vm_size_t size = sizeof(unused);
  return vm_read_overwrite(
             current_task(),
             leaf_frame_module->GetBaseAddress() + leaf_frame_module->GetSize(),
             sizeof(unused), reinterpret_cast<vm_address_t>(&unused),
             &size) != 0;
}

// Check if the cursor contains a valid-looking frame pointer for frame pointer
// unwinds. If the stack frame has a frame pointer, stepping the cursor will
// involve indexing memory access off of that pointer. In that case,
// sanity-check the frame pointer register to ensure it's within bounds.
//
// Additionally, the stack frame might be in a prologue or epilogue, which can
// cause a crash when the unwinder attempts to access non-volatile registers
// that have not yet been pushed, or have already been popped from the
// stack. libwunwind will try to restore those registers using an offset from
// the frame pointer. However, since we copy the stack from RSP up, any
// locations below the stack pointer are before the beginning of the stack
// buffer. Account for this by checking that the expected location is above the
// stack pointer, and rejecting the sample if it isn't.
bool HasValidRbp(unw_cursor_t* unwind_cursor, uintptr_t stack_top) {
  unw_proc_info_t proc_info;
  unw_get_proc_info(unwind_cursor, &proc_info);
  if ((proc_info.format & UNWIND_X86_64_MODE_MASK) ==
      UNWIND_X86_64_MODE_RBP_FRAME) {
    unw_word_t rsp, rbp;
    unw_get_reg(unwind_cursor, UNW_X86_64_RSP, &rsp);
    unw_get_reg(unwind_cursor, UNW_X86_64_RBP, &rbp);
    uint32_t offset = GetFrameOffset(proc_info.format) * sizeof(unw_word_t);
    if (rbp < offset || (rbp - offset) < rsp || rbp > stack_top)
      return false;
  }
  return true;
}

const ModuleCache::Module* GetLibSystemKernelModule(ModuleCache* module_cache) {
  const ModuleCache::Module* module =
      module_cache->GetModuleForAddress(reinterpret_cast<uintptr_t>(ptrace));
  DCHECK(module);
  DCHECK_EQ(FilePath("libsystem_kernel.dylib"), module->GetDebugBasename());
  return module;
}

void GetSigtrampRange(uintptr_t* start, uintptr_t* end) {
  auto address = reinterpret_cast<uintptr_t>(&_sigtramp);
  DCHECK(address != 0);

  *start = address;

  unw_context_t context;
  unw_cursor_t cursor;
  unw_proc_info_t info;

  unw_getcontext(&context);
  // Set the context's RIP to the beginning of sigtramp,
  // +1 byte to work around a bug in 10.11 (crbug.com/764468).
  context.data[16] = address + 1;
  unw_init_local(&cursor, &context);
  unw_get_proc_info(&cursor, &info);

  DCHECK_EQ(info.start_ip, address);
  *end = info.end_ip;
}

}  // namespace

NativeUnwinderMac::NativeUnwinderMac(ModuleCache* module_cache)
    : libsystem_kernel_module_(GetLibSystemKernelModule(module_cache)) {
  GetSigtrampRange(&sigtramp_start_, &sigtramp_end_);
}

bool NativeUnwinderMac::CanUnwindFrom(const Frame& current_frame) const {
  return current_frame.module && current_frame.module->IsNative();
}

UnwindResult NativeUnwinderMac::TryUnwind(x86_thread_state64_t* thread_context,
                                          uintptr_t stack_top,
                                          ModuleCache* module_cache,
                                          std::vector<Frame>* stack) const {
  // We expect the frame correponding to the |thread_context| register state to
  // exist within |stack|.
  DCHECK_GT(stack->size(), 0u);

  // There isn't an official way to create a unw_context other than to create it
  // from the current state of the current thread's stack. Since we're walking a
  // different thread's stack we must forge a context. The unw_context is just a
  // copy of the 16 main registers followed by the instruction pointer, nothing
  // more.  Coincidentally, the first 17 items of the x86_thread_state64_t type
  // are exactly those registers in exactly the same order, so just bulk copy
  // them over.
  unw_context_t unwind_context;
  memcpy(&unwind_context, thread_context, sizeof(uintptr_t) * 17);

  // Avoid an out-of-bounds read bug in libunwind that can crash us in some
  // circumstances. If we're subject to that case, just record the first frame
  // and bail. See MayTriggerUnwInitLocalCrash for details.
  if (stack->back().module && MayTriggerUnwInitLocalCrash(stack->back().module))
    return UnwindResult::ABORTED;

  unw_cursor_t unwind_cursor;
  unw_init_local(&unwind_cursor, &unwind_context);

  for (;;) {
    Optional<UnwindResult> result =
        CheckPreconditions(&stack->back(), &unwind_cursor, stack_top);
    if (result.has_value())
      return *result;

    unw_word_t prev_rsp;
    unw_get_reg(&unwind_cursor, UNW_REG_SP, &prev_rsp);

    int step_result = UnwindStep(&unwind_context, &unwind_cursor,
                                 stack->size() == 1, module_cache);

    unw_word_t rip;
    unw_get_reg(&unwind_cursor, UNW_REG_IP, &rip);
    unw_word_t rsp;
    unw_get_reg(&unwind_cursor, UNW_REG_SP, &rsp);

    bool successfully_unwound;
    result = CheckPostconditions(step_result, prev_rsp, rsp, stack_top,
                                 &successfully_unwound);

    if (successfully_unwound) {
      stack->emplace_back(rip, module_cache->GetModuleForAddress(rip));

      // Save the relevant register state back into the thread context.
      unw_word_t rbp;
      unw_get_reg(&unwind_cursor, UNW_X86_64_RBP, &rbp);
      thread_context->__rip = rip;
      thread_context->__rsp = rsp;
      thread_context->__rbp = rbp;
    }

    if (result.has_value())
      return *result;
  }

  NOTREACHED();
  return UnwindResult::COMPLETED;
}

// Checks preconditions for attempting an unwind. If any conditions fail,
// returns corresponding UnwindResult. Otherwise returns nullopt.
Optional<UnwindResult> NativeUnwinderMac::CheckPreconditions(
    const Frame* current_frame,
    unw_cursor_t* unwind_cursor,
    uintptr_t stack_top) const {
  if (!current_frame->module) {
    // There's no loaded module containing the instruction pointer. This is
    // due to either executing code that is not in a module (e.g. V8
    // runtime-generated code), or to a previous bad unwind.
    //
    // The bad unwind scenario can occur in frameless (non-DWARF) unwinding,
    // which works by fetching the function's stack size from the unwind
    // encoding or stack, and adding it to the stack pointer to determine the
    // function's return address.
    //
    // If we're in a function prologue or epilogue, the actual stack size may
    // be smaller than it will be during the normal course of execution. When
    // libunwind adds the expected stack size, it will look for the return
    // address in the wrong place. This check ensures we don't continue trying
    // to unwind using the resulting bad IP value.
    return UnwindResult::ABORTED;
  }

  if (!current_frame->module->IsNative()) {
    // This is a non-native module associated with the auxiliary unwinder
    // (e.g. corresponding to a frame in V8 generated code). Report as
    // UNRECOGNIZED_FRAME to allow that unwinder to unwind the frame.
    return UnwindResult::UNRECOGNIZED_FRAME;
  }

  // Don't continue if we're in sigtramp. Unwinding this from another thread
  // is very fragile. It's a complex DWARF unwind that needs to restore the
  // entire thread context which was saved by the kernel when the interrupt
  // occurred.
  if (current_frame->instruction_pointer >= sigtramp_start_ &&
      current_frame->instruction_pointer < sigtramp_end_) {
    return UnwindResult::ABORTED;
  }

  // Don't continue if rbp appears to be invalid (due to a previous bad
  // unwind).
  if (!HasValidRbp(unwind_cursor, stack_top))
    return UnwindResult::ABORTED;

  return nullopt;
}

// Attempts to unwind the current frame using unw_step, and returns its return
// value.
int NativeUnwinderMac::UnwindStep(unw_context_t* unwind_context,
                                  unw_cursor_t* unwind_cursor,
                                  bool at_first_frame,
                                  ModuleCache* module_cache) const {
  int step_result = unw_step(unwind_cursor);

  if (step_result == 0 && at_first_frame) {
    // libunwind is designed to be triggered by user code on their own thread,
    // if it hits a library that has no unwind info for the function that is
    // being executed, it just stops. This isn't a problem in the normal case,
    // but in the case where this is the first frame unwind, it's quite
    // possible that the stack being walked is stopped in a function that
    // bridges to the kernel and thus is missing the unwind info.

    // For now, just unwind the single case where the thread is stopped in a
    // function in libsystem_kernel.
    uint64_t& rsp = unwind_context->data[7];
    uint64_t& rip = unwind_context->data[16];
    if (module_cache->GetModuleForAddress(rip) == libsystem_kernel_module_) {
      rip = *reinterpret_cast<uint64_t*>(rsp);
      rsp += 8;
      // Reset the cursor.
      unw_init_local(unwind_cursor, unwind_context);
      // Mock a successful step_result.
      return 1;
    }
  }

  return step_result;
}

// Checks postconditions after attempting an unwind. If any conditions fail,
// returns corresponding UnwindResult. Otherwise returns nullopt. Sets
// *|successfully_unwound| if the unwind succeeded (and hence the frame should
// be recorded).
Optional<UnwindResult> NativeUnwinderMac::CheckPostconditions(
    int step_result,
    unw_word_t prev_rsp,
    unw_word_t rsp,
    uintptr_t stack_top,
    bool* successfully_unwound) const {
  const bool stack_pointer_was_moved_and_is_valid =
      rsp > prev_rsp && rsp < stack_top;

  *successfully_unwound =
      step_result > 0 ||
      // libunwind considers the unwind complete and returns 0 if no unwind
      // info was found for the current instruction pointer. It performs this
      // check both before *and* after stepping the cursor. In the former case
      // no action is taken, but in the latter case an unwind was successfully
      // performed prior to the check. Distinguish these cases by checking
      // whether the stack pointer was moved by unw_step. If so, record the
      // new frame to enable non-native unwinders to continue the unwinding.
      (step_result == 0 && stack_pointer_was_moved_and_is_valid);

  if (step_result < 0)
    return UnwindResult::ABORTED;

  // libunwind returns 0 if it can't continue because no unwind info was found
  // for the current instruction pointer. This could be due to unwinding past
  // the entry point, in which case the unwind would be complete. It could
  // also be due to unwinding to a function that simply doesn't have unwind
  // info, in which case the unwind should be aborted. Or it could be due to
  // unwinding to code not in a module, in which case the unwind might be
  // continuable by a non-native unwinder. We don't have a good way to
  // distinguish these cases, so return UNRECOGNIZED_FRAME to at least
  // signify that we couldn't unwind further.
  if (step_result == 0)
    return UnwindResult::UNRECOGNIZED_FRAME;

  // If we succeeded but didn't advance the stack pointer, or got an invalid
  // new stack pointer, abort.
  if (!stack_pointer_was_moved_and_is_valid)
    return UnwindResult::ABORTED;

  return nullopt;
}

std::unique_ptr<Unwinder> CreateNativeUnwinder(ModuleCache* module_cache) {
  return std::make_unique<NativeUnwinderMac>(module_cache);
}

}  // namespace base
