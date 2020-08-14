// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TRACE_EVENT_HEAP_PROFILER_EVENT_FILTER_H_
#define BASE_TRACE_EVENT_HEAP_PROFILER_EVENT_FILTER_H_

#include "base/base_export.h"
#include "base/macros.h"
#include "base/trace_event/trace_event_filter.h"

namespace base {
namespace trace_event {

class TraceEvent;

// This filter unconditionally accepts all events and pushes/pops them from the
// thread-local AllocationContextTracker instance as they are seen.
// This is used to cheaply construct the heap profiler pseudo stack without
// having to actually record all events.
class BASE_EXPORT HeapProfilerEventFilter : public TraceEventFilter {
 public:
  static const char kName[];

  HeapProfilerEventFilter();
  ~HeapProfilerEventFilter() override;

  // TraceEventFilter implementation.
  bool FilterTraceEvent(const TraceEvent& trace_event) const override;
  void EndEvent(const char* category_name,
                const char* event_name) const override;

 private:
  DISALLOW_COPY_AND_ASSIGN(HeapProfilerEventFilter);
};

}  // namespace trace_event
}  // namespace base

#endif  // BASE_TRACE_EVENT_HEAP_PROFILER_EVENT_FILTER_H_
