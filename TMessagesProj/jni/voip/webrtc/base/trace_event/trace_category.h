// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TRACE_EVENT_TRACE_CATEGORY_H_
#define BASE_TRACE_EVENT_TRACE_CATEGORY_H_

#include <stdint.h>

namespace base {
namespace trace_event {

// Captures the state of an invidivual trace category. Nothing except tracing
// internals (e.g., TraceLog) is supposed to have non-const Category pointers.
struct TraceCategory {
  // The TRACE_EVENT macros should only use this value as a bool.
  // These enum values are effectively a public API and third_party projects
  // depend on their value. Hence, never remove or recycle existing bits, unless
  // you are sure that all the third-party projects that depend on this have
  // been updated.
  enum StateFlags : uint8_t {
    ENABLED_FOR_RECORDING = 1 << 0,

    // Not used anymore.
    DEPRECATED_ENABLED_FOR_MONITORING = 1 << 1,
    DEPRECATED_ENABLED_FOR_EVENT_CALLBACK = 1 << 2,

    ENABLED_FOR_ETW_EXPORT = 1 << 3,
    ENABLED_FOR_FILTERING = 1 << 4
  };

  static const TraceCategory* FromStatePtr(const uint8_t* state_ptr) {
    static_assert(
        offsetof(TraceCategory, state_) == 0,
        "|state_| must be the first field of the TraceCategory class.");
    return reinterpret_cast<const TraceCategory*>(state_ptr);
  }

  bool is_valid() const { return name_ != nullptr; }
  void set_name(const char* name) { name_ = name; }
  const char* name() const {
    DCHECK(is_valid());
    return name_;
  }

  // TODO(primiano): This is an intermediate solution to deal with the fact that
  // today TRACE_EVENT* macros cache the state ptr. They should just cache the
  // full TraceCategory ptr, which is immutable, and use these helper function
  // here. This will get rid of the need of this awkward ptr getter completely.
  constexpr const uint8_t* state_ptr() const {
    return const_cast<const uint8_t*>(&state_);
  }

  uint8_t state() const {
    return *const_cast<volatile const uint8_t*>(&state_);
  }

  bool is_enabled() const { return state() != 0; }

  void set_state(uint8_t state) {
    *const_cast<volatile uint8_t*>(&state_) = state;
  }

  void clear_state_flag(StateFlags flag) { set_state(state() & (~flag)); }
  void set_state_flag(StateFlags flag) { set_state(state() | flag); }

  uint32_t enabled_filters() const {
    return *const_cast<volatile const uint32_t*>(&enabled_filters_);
  }

  bool is_filter_enabled(size_t index) const {
    DCHECK(index < sizeof(enabled_filters_) * 8);
    return (enabled_filters() & (1 << index)) != 0;
  }

  void set_enabled_filters(uint32_t enabled_filters) {
    *const_cast<volatile uint32_t*>(&enabled_filters_) = enabled_filters;
  }

  void reset_for_testing() {
    set_state(0);
    set_enabled_filters(0);
  }

  // These fields should not be accessed directly, not even by tracing code.
  // The only reason why these are not private is because it makes it impossible
  // to have a global array of TraceCategory in category_registry.cc without
  // creating initializers. See discussion on goo.gl/qhZN94 and
  // crbug.com/{660967,660828}.

  // The enabled state. TRACE_EVENT* macros will capture events if any of the
  // flags here are set. Since TRACE_EVENTx macros are used in a lot of
  // fast-paths, accesses to this field are non-barriered and racy by design.
  // This field is mutated when starting/stopping tracing and we don't care
  // about missing some events.
  uint8_t state_;

  // When ENABLED_FOR_FILTERING is set, this contains a bitmap to the
  // corresponding filter (see event_filters.h).
  uint32_t enabled_filters_;

  // TraceCategory group names are long lived static strings.
  const char* name_;
};

}  // namespace trace_event
}  // namespace base

#endif  // BASE_TRACE_EVENT_TRACE_CATEGORY_H_
