// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TRACE_EVENT_HEAP_PROFILER_H
#define BASE_TRACE_EVENT_HEAP_PROFILER_H

#include "base/compiler_specific.h"
#include "base/trace_event/heap_profiler_allocation_context_tracker.h"

// This header file defines the set of macros that are used to track memory
// usage in the heap profiler. This is in addition to the macros defined in
// trace_event.h and are specific to heap profiler. This file also defines
// implementation details of these macros.

// Implementation detail: heap profiler macros create temporary variables to
// keep instrumentation overhead low. These macros give each temporary variable
// a unique name based on the line number to prevent name collisions.
#define INTERNAL_HEAP_PROFILER_UID3(a, b) heap_profiler_unique_##a##b
#define INTERNAL_HEAP_PROFILER_UID2(a, b) INTERNAL_HEAP_PROFILER_UID3(a, b)
#define INTERNAL_HEAP_PROFILER_UID(name_prefix) \
  INTERNAL_HEAP_PROFILER_UID2(name_prefix, __LINE__)

// Scoped tracker for task execution context in the heap profiler.
#define TRACE_HEAP_PROFILER_API_SCOPED_TASK_EXECUTION \
  trace_event_internal::HeapProfilerScopedTaskExecutionTracker

// Scoped tracker that tracks the given program counter as a native stack frame
// in the heap profiler.
#define TRACE_HEAP_PROFILER_API_SCOPED_WITH_PROGRAM_COUNTER \
  trace_event_internal::HeapProfilerScopedStackFrame

// Returns the current task context (c-string) tracked by heap profiler. This is
// useful along with TRACE_HEAP_PROFILER_API_SCOPED_TASK_EXECUTION if a async
// system needs to track client's allocation context across post tasks. Use this
// macro to get the current context and use
// TRACE_HEAP_PROFILER_API_SCOPED_TASK_EXECUTION in the posted task which
// allocates memory for a client.
#define TRACE_HEAP_PROFILER_API_GET_CURRENT_TASK_CONTEXT \
  trace_event_internal::HeapProfilerCurrentTaskContext

// A scoped ignore event used to tell heap profiler to ignore all the
// allocations in the scope. It is useful to exclude allocations made for
// tracing from the heap profiler dumps.
#define HEAP_PROFILER_SCOPED_IGNORE                                          \
  trace_event_internal::HeapProfilerScopedIgnore INTERNAL_HEAP_PROFILER_UID( \
      scoped_ignore)

namespace trace_event_internal {

// HeapProfilerScopedTaskExecutionTracker records the current task's context in
// the heap profiler.
class HeapProfilerScopedTaskExecutionTracker {
 public:
  inline explicit HeapProfilerScopedTaskExecutionTracker(
      const char* task_context)
      : context_(task_context) {
    using base::trace_event::AllocationContextTracker;
    if (UNLIKELY(AllocationContextTracker::capture_mode() !=
                 AllocationContextTracker::CaptureMode::DISABLED)) {
      AllocationContextTracker::GetInstanceForCurrentThread()
          ->PushCurrentTaskContext(context_);
    }
  }

  inline ~HeapProfilerScopedTaskExecutionTracker() {
    using base::trace_event::AllocationContextTracker;
    if (UNLIKELY(AllocationContextTracker::capture_mode() !=
                 AllocationContextTracker::CaptureMode::DISABLED)) {
      AllocationContextTracker::GetInstanceForCurrentThread()
          ->PopCurrentTaskContext(context_);
    }
  }

 private:
  const char* context_;
};

class HeapProfilerScopedStackFrame {
 public:
  inline explicit HeapProfilerScopedStackFrame(const void* program_counter)
      : program_counter_(program_counter) {
    using base::trace_event::AllocationContextTracker;
    if (UNLIKELY(AllocationContextTracker::capture_mode() ==
                 AllocationContextTracker::CaptureMode::MIXED_STACK)) {
      AllocationContextTracker::GetInstanceForCurrentThread()
          ->PushNativeStackFrame(program_counter_);
    }
  }

  inline ~HeapProfilerScopedStackFrame() {
    using base::trace_event::AllocationContextTracker;
    if (UNLIKELY(AllocationContextTracker::capture_mode() ==
                 AllocationContextTracker::CaptureMode::MIXED_STACK)) {
      AllocationContextTracker::GetInstanceForCurrentThread()
          ->PopNativeStackFrame(program_counter_);
    }
  }

 private:
  const void* const program_counter_;
};

inline const char* HeapProfilerCurrentTaskContext() {
  return base::trace_event::AllocationContextTracker::
      GetInstanceForCurrentThread()
          ->TaskContext();
}

class BASE_EXPORT HeapProfilerScopedIgnore {
 public:
  inline HeapProfilerScopedIgnore() {
    using base::trace_event::AllocationContextTracker;
    if (UNLIKELY(
            AllocationContextTracker::capture_mode() !=
            AllocationContextTracker::CaptureMode::DISABLED)) {
      AllocationContextTracker::GetInstanceForCurrentThread()
          ->begin_ignore_scope();
    }
  }
  inline ~HeapProfilerScopedIgnore() {
    using base::trace_event::AllocationContextTracker;
    if (UNLIKELY(
            AllocationContextTracker::capture_mode() !=
            AllocationContextTracker::CaptureMode::DISABLED)) {
      AllocationContextTracker::GetInstanceForCurrentThread()
          ->end_ignore_scope();
    }
  }
};

}  // namespace trace_event_internal

#endif  // BASE_TRACE_EVENT_HEAP_PROFILER_H
