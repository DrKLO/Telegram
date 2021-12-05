// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/profiler/stack_copier.h"

#include "base/compiler_specific.h"

namespace base {

StackCopier::~StackCopier() = default;

// static
uintptr_t StackCopier::RewritePointerIfInOriginalStack(
    const uint8_t* original_stack_bottom,
    const uintptr_t* original_stack_top,
    const uint8_t* stack_copy_bottom,
    uintptr_t pointer) {
  auto original_stack_bottom_uint =
      reinterpret_cast<uintptr_t>(original_stack_bottom);
  auto original_stack_top_uint =
      reinterpret_cast<uintptr_t>(original_stack_top);
  auto stack_copy_bottom_uint = reinterpret_cast<uintptr_t>(stack_copy_bottom);

  if (pointer < original_stack_bottom_uint ||
      pointer >= original_stack_top_uint)
    return pointer;

  return stack_copy_bottom_uint + (pointer - original_stack_bottom_uint);
}

// static
NO_SANITIZE("address")
const uint8_t* StackCopier::CopyStackContentsAndRewritePointers(
    const uint8_t* original_stack_bottom,
    const uintptr_t* original_stack_top,
    int platform_stack_alignment,
    uintptr_t* stack_buffer_bottom) {
  const uint8_t* byte_src = original_stack_bottom;
  // The first address in the stack with pointer alignment. Pointer-aligned
  // values from this point to the end of the stack are possibly rewritten using
  // RewritePointerIfInOriginalStack(). Bytes before this cannot be a pointer
  // because they occupy less space than a pointer would.
  const uint8_t* first_aligned_address = reinterpret_cast<uint8_t*>(
      (reinterpret_cast<uintptr_t>(byte_src) + sizeof(uintptr_t) - 1) &
      ~(sizeof(uintptr_t) - 1));

  // The stack copy bottom, which is offset from |stack_buffer_bottom| by the
  // same alignment as in the original stack. This guarantees identical
  // alignment between values in the original stack and the copy. This uses the
  // platform stack alignment rather than pointer alignment so that the stack
  // copy is aligned to platform expectations.
  uint8_t* stack_copy_bottom =
      reinterpret_cast<uint8_t*>(stack_buffer_bottom) +
      (reinterpret_cast<uintptr_t>(byte_src) & (platform_stack_alignment - 1));
  uint8_t* byte_dst = stack_copy_bottom;

  // Copy bytes verbatim up to the first aligned address.
  for (; byte_src < first_aligned_address; ++byte_src, ++byte_dst)
    *byte_dst = *byte_src;

  // Copy the remaining stack by pointer-sized values, rewriting anything that
  // looks like a pointer into the stack.
  const uintptr_t* src = reinterpret_cast<const uintptr_t*>(byte_src);
  uintptr_t* dst = reinterpret_cast<uintptr_t*>(byte_dst);
  for (; src < original_stack_top; ++src, ++dst) {
    *dst = RewritePointerIfInOriginalStack(
        original_stack_bottom, original_stack_top, stack_copy_bottom, *src);
  }

  return stack_copy_bottom;
}

}  // namespace base
