// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/trace_event/trace_event_filter_test_utils.h"

#include "base/logging.h"

namespace base {
namespace trace_event {

namespace {
TestEventFilter::HitsCounter* g_hits_counter;
}  // namespace;

// static
const char TestEventFilter::kName[] = "testing_predicate";
bool TestEventFilter::filter_return_value_;

// static
std::unique_ptr<TraceEventFilter> TestEventFilter::Factory(
    const std::string& predicate_name) {
  std::unique_ptr<TraceEventFilter> res;
  if (predicate_name == kName)
    res.reset(new TestEventFilter());
  return res;
}

TestEventFilter::TestEventFilter() = default;
TestEventFilter::~TestEventFilter() = default;

bool TestEventFilter::FilterTraceEvent(const TraceEvent& trace_event) const {
  if (g_hits_counter)
    g_hits_counter->filter_trace_event_hit_count++;
  return filter_return_value_;
}

void TestEventFilter::EndEvent(const char* category_name,
                               const char* name) const {
  if (g_hits_counter)
    g_hits_counter->end_event_hit_count++;
}

TestEventFilter::HitsCounter::HitsCounter() {
  Reset();
  DCHECK(!g_hits_counter);
  g_hits_counter = this;
}

TestEventFilter::HitsCounter::~HitsCounter() {
  DCHECK(g_hits_counter);
  g_hits_counter = nullptr;
}

void TestEventFilter::HitsCounter::Reset() {
  filter_trace_event_hit_count = 0;
  end_event_hit_count = 0;
}

}  // namespace trace_event
}  // namespace base
