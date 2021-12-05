// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TRACE_EVENT_TRACE_EVENT_FILTER_H_
#define BASE_TRACE_EVENT_TRACE_EVENT_FILTER_H_

#include <memory>

#include "base/base_export.h"
#include "base/macros.h"

namespace base {
namespace trace_event {

class TraceEvent;

// TraceEventFilter is like iptables for TRACE_EVENT macros. Filters can be
// enabled on a per-category basis, hence a single filter instance can serve
// more than a TraceCategory. There are two use cases for filters:
// 1. Snooping TRACE_EVENT macros without adding them to the TraceLog. This is
//    possible by setting the ENABLED_FOR_FILTERING flag on a category w/o
//    ENABLED_FOR_RECORDING (see TraceConfig for user-facing configuration).
// 2. Filtering TRACE_EVENT macros before they are added to the TraceLog. This
//    requires both the ENABLED_FOR_FILTERING and ENABLED_FOR_RECORDING flags
//    on the category.
// More importantly, filters must be thread-safe. The FilterTraceEvent and
// EndEvent methods can be called concurrently as trace macros are hit on
// different threads.
class BASE_EXPORT TraceEventFilter {
 public:
  TraceEventFilter();
  virtual ~TraceEventFilter();

  // If the category is ENABLED_FOR_RECORDING, the event is added iff all the
  // filters enabled for the category return true. false causes the event to be
  // discarded.
  virtual bool FilterTraceEvent(const TraceEvent& trace_event) const = 0;

  // Notifies the end of a duration event when the RAII macro goes out of scope.
  virtual void EndEvent(const char* category_name,
                        const char* event_name) const;

 private:
  DISALLOW_COPY_AND_ASSIGN(TraceEventFilter);
};

}  // namespace trace_event
}  // namespace base

#endif  // BASE_TRACE_EVENT_TRACE_EVENT_FILTER_H_
