// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TRACE_EVENT_THREAD_INSTRUCTION_COUNT_H_
#define BASE_TRACE_EVENT_THREAD_INSTRUCTION_COUNT_H_

#include <stdint.h>

#include "base/base_export.h"

namespace base {
namespace trace_event {

// Represents the number of instructions that were retired between two samples
// of a thread's performance counters.
class BASE_EXPORT ThreadInstructionDelta {
 public:
  constexpr ThreadInstructionDelta() : delta_(0) {}
  explicit constexpr ThreadInstructionDelta(int64_t delta) : delta_(delta) {}

  constexpr int64_t ToInternalValue() const { return delta_; }

 private:
  int64_t delta_;
};

// Uses the system's performance counters in order to measure the number of
// instructions that have been retired on the current thread.
class BASE_EXPORT ThreadInstructionCount {
 public:
  // Returns true if the platform supports hardware retired instruction
  // counters.
  static bool IsSupported();

  // Returns the number of retired instructions relative to some epoch count,
  // or -1 if getting the current instruction count failed / is disabled.
  static ThreadInstructionCount Now();

  constexpr ThreadInstructionCount() : value_(-1) {}
  explicit constexpr ThreadInstructionCount(int64_t value) : value_(value) {}

  constexpr bool is_null() const { return value_ == -1; }

  constexpr ThreadInstructionDelta operator-(
      ThreadInstructionCount other) const {
    return ThreadInstructionDelta(value_ - other.value_);
  }

  constexpr int64_t ToInternalValue() const { return value_; }

 private:
  int64_t value_;
};

}  // namespace trace_event
}  // namespace base

#endif  // BASE_TRACE_EVENT_THREAD_INSTRUCTION_COUNT_H_
