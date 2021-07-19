// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/trace_event/trace_event_filter.h"

namespace base {
namespace trace_event {

TraceEventFilter::TraceEventFilter() = default;
TraceEventFilter::~TraceEventFilter() = default;

void TraceEventFilter::EndEvent(const char* category_name,
                                const char* event_name) const {}

}  // namespace trace_event
}  // namespace base
