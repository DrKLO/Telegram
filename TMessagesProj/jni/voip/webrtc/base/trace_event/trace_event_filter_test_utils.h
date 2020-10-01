// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TRACE_EVENT_TRACE_EVENT_FILTER_TEST_UTILS_H_
#define BASE_TRACE_EVENT_TRACE_EVENT_FILTER_TEST_UTILS_H_

#include <memory>
#include <string>

#include "base/macros.h"
#include "base/trace_event/trace_event_filter.h"

namespace base {
namespace trace_event {

class TestEventFilter : public TraceEventFilter {
 public:
  struct HitsCounter {
    HitsCounter();
    ~HitsCounter();
    void Reset();
    size_t filter_trace_event_hit_count;
    size_t end_event_hit_count;
  };

  static const char kName[];

  // Factory method for TraceLog::SetFilterFactoryForTesting().
  static std::unique_ptr<TraceEventFilter> Factory(
      const std::string& predicate_name);

  TestEventFilter();
  ~TestEventFilter() override;

  // TraceEventFilter implementation.
  bool FilterTraceEvent(const TraceEvent& trace_event) const override;
  void EndEvent(const char* category_name, const char* name) const override;

  static void set_filter_return_value(bool value) {
    filter_return_value_ = value;
  }

 private:
  static bool filter_return_value_;

  DISALLOW_COPY_AND_ASSIGN(TestEventFilter);
};

}  // namespace trace_event
}  // namespace base

#endif  // BASE_TRACE_EVENT_TRACE_EVENT_FILTER_TEST_UTILS_H_
