// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_PROFILER_STACK_BUFFER_H_
#define BASE_PROFILER_STACK_BUFFER_H_

#include <stddef.h>
#include <stdint.h>
#include <memory>

#include "base/base_export.h"
#include "base/macros.h"

namespace base {

// This class contains a buffer for stack copies that can be shared across
// multiple instances of StackSampler.
class BASE_EXPORT StackBuffer {
 public:
  // The expected alignment of the stack on the current platform. Windows and
  // System V AMD64 ABIs on x86, x64, and ARM require the stack to be aligned
  // to twice the pointer size. Excepted from this requirement is code setting
  // up the stack during function calls (between pushing the return address
  // and the end of the function prologue). The profiler will sometimes
  // encounter this exceptional case for leaf frames.
  static constexpr size_t kPlatformStackAlignment = 2 * sizeof(uintptr_t);

  StackBuffer(size_t buffer_size);
  ~StackBuffer();

  // Returns a kPlatformStackAlignment-aligned pointer to the stack buffer.
  uintptr_t* buffer() const {
    // Return the first address in the buffer aligned to
    // kPlatformStackAlignment. The buffer is guaranteed to have enough space
    // for size() bytes beyond this value.
    return reinterpret_cast<uintptr_t*>(
        (reinterpret_cast<uintptr_t>(buffer_.get()) + kPlatformStackAlignment -
         1) &
        ~(kPlatformStackAlignment - 1));
  }

  // Size in bytes.
  size_t size() const { return size_; }

 private:
  // The buffer to store the stack.
  const std::unique_ptr<uint8_t[]> buffer_;

  // The size in bytes of the requested buffer allocation. The actual allocation
  // is larger to accommodate alignment requirements.
  const size_t size_;

  DISALLOW_COPY_AND_ASSIGN(StackBuffer);
};

}  // namespace base

#endif  // BASE_PROFILER_STACK_BUFFER_H_
