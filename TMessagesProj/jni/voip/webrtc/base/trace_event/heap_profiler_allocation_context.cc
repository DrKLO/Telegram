// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/trace_event/heap_profiler_allocation_context.h"

#include <cstring>

#include "base/hash/hash.h"
#include "base/macros.h"

namespace base {
namespace trace_event {

bool operator < (const StackFrame& lhs, const StackFrame& rhs) {
  return lhs.value < rhs.value;
}

bool operator == (const StackFrame& lhs, const StackFrame& rhs) {
  return lhs.value == rhs.value;
}

bool operator != (const StackFrame& lhs, const StackFrame& rhs) {
  return !(lhs.value == rhs.value);
}

Backtrace::Backtrace() = default;

bool operator==(const Backtrace& lhs, const Backtrace& rhs) {
  if (lhs.frame_count != rhs.frame_count) return false;
  return std::equal(lhs.frames, lhs.frames + lhs.frame_count, rhs.frames);
}

bool operator!=(const Backtrace& lhs, const Backtrace& rhs) {
  return !(lhs == rhs);
}

AllocationContext::AllocationContext(): type_name(nullptr) {}

AllocationContext::AllocationContext(const Backtrace& backtrace,
                                     const char* type_name)
  : backtrace(backtrace), type_name(type_name) {}

bool operator==(const AllocationContext& lhs, const AllocationContext& rhs) {
  return (lhs.backtrace == rhs.backtrace) && (lhs.type_name == rhs.type_name);
}

bool operator!=(const AllocationContext& lhs, const AllocationContext& rhs) {
  return !(lhs == rhs);
}

}  // namespace trace_event
}  // namespace base

namespace std {

using base::trace_event::AllocationContext;
using base::trace_event::Backtrace;
using base::trace_event::StackFrame;

size_t hash<StackFrame>::operator()(const StackFrame& frame) const {
  return hash<const void*>()(frame.value);
}

size_t hash<Backtrace>::operator()(const Backtrace& backtrace) const {
  const void* values[Backtrace::kMaxFrameCount];
  for (size_t i = 0; i != backtrace.frame_count; ++i) {
    values[i] = backtrace.frames[i].value;
  }
  return base::PersistentHash(values, backtrace.frame_count * sizeof(*values));
}

size_t hash<AllocationContext>::operator()(const AllocationContext& ctx) const {
  size_t backtrace_hash = hash<Backtrace>()(ctx.backtrace);

  // Multiplicative hash from [Knuth 1998]. Works best if |size_t| is 32 bits,
  // because the magic number is a prime very close to 2^32 / golden ratio, but
  // will still redistribute keys bijectively on 64-bit architectures because
  // the magic number is coprime to 2^64.
  size_t type_hash = reinterpret_cast<size_t>(ctx.type_name) * 2654435761;

  // Multiply one side to break the commutativity of +. Multiplication with a
  // number coprime to |numeric_limits<size_t>::max() + 1| is bijective so
  // randomness is preserved.
  return (backtrace_hash * 3) + type_hash;
}

}  // namespace std
