// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/trace_event/heap_profiler_event_filter.h"

#include "base/trace_event/category_registry.h"
#include "base/trace_event/heap_profiler_allocation_context_tracker.h"
#include "base/trace_event/trace_category.h"
#include "base/trace_event/trace_event.h"
#include "base/trace_event/trace_event_impl.h"

namespace base {
namespace trace_event {

namespace {

inline bool IsPseudoStackEnabled() {
  // Only PSEUDO_STACK and MIXED_STACK modes require trace events.
  return AllocationContextTracker::capture_mode() ==
             AllocationContextTracker::CaptureMode::PSEUDO_STACK ||
         AllocationContextTracker::capture_mode() ==
             AllocationContextTracker::CaptureMode::MIXED_STACK;
}

inline AllocationContextTracker* GetThreadLocalTracker() {
  return AllocationContextTracker::GetInstanceForCurrentThread();
}

}  // namespace

// static
const char HeapProfilerEventFilter::kName[] = "heap_profiler_predicate";

HeapProfilerEventFilter::HeapProfilerEventFilter() = default;
HeapProfilerEventFilter::~HeapProfilerEventFilter() = default;

bool HeapProfilerEventFilter::FilterTraceEvent(
    const TraceEvent& trace_event) const {
  if (!IsPseudoStackEnabled())
    return true;

  // TODO(primiano): Add support for events with copied name crbug.com/581079.
  if (trace_event.flags() & TRACE_EVENT_FLAG_COPY)
    return true;

  const auto* category = CategoryRegistry::GetCategoryByStatePtr(
      trace_event.category_group_enabled());
  AllocationContextTracker::PseudoStackFrame frame = {category->name(),
                                                      trace_event.name()};
  if (trace_event.phase() == TRACE_EVENT_PHASE_BEGIN ||
      trace_event.phase() == TRACE_EVENT_PHASE_COMPLETE) {
    GetThreadLocalTracker()->PushPseudoStackFrame(frame);
  } else if (trace_event.phase() == TRACE_EVENT_PHASE_END) {
    // The pop for |TRACE_EVENT_PHASE_COMPLETE| events is in |EndEvent|.
    GetThreadLocalTracker()->PopPseudoStackFrame(frame);
  }
  // Do not filter-out any events and always return true. TraceLog adds the
  // event only if it is enabled for recording.
  return true;
}

void HeapProfilerEventFilter::EndEvent(const char* category_name,
                                       const char* event_name) const {
  if (IsPseudoStackEnabled())
    GetThreadLocalTracker()->PopPseudoStackFrame({category_name, event_name});
}

}  // namespace trace_event
}  // namespace base
