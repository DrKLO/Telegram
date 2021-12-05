// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/profiler/stack_buffer.h"

namespace base {

constexpr size_t StackBuffer::kPlatformStackAlignment;

StackBuffer::StackBuffer(size_t buffer_size)
    : buffer_(new uint8_t[buffer_size + kPlatformStackAlignment - 1]),
      size_(buffer_size) {}

StackBuffer::~StackBuffer() = default;

}  // namespace base
