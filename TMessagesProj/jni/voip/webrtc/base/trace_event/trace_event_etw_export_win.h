// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// This file contains the Windows-specific exporting to ETW.
#ifndef BASE_TRACE_EVENT_TRACE_EVENT_ETW_EXPORT_WIN_H_
#define BASE_TRACE_EVENT_TRACE_EVENT_ETW_EXPORT_WIN_H_

#include <stdint.h>

#include <map>

#include "base/base_export.h"
#include "base/macros.h"
#include "base/strings/string_piece.h"
#include "base/trace_event/trace_event_impl.h"

namespace base {

template <typename Type>
struct StaticMemorySingletonTraits;

namespace trace_event {

class BASE_EXPORT TraceEventETWExport {
 public:
  ~TraceEventETWExport();

  // Retrieves the singleton.
  // Note that this may return NULL post-AtExit processing.
  static TraceEventETWExport* GetInstance();

  // Retrieves the singleton iff it was previously instantiated by a
  // GetInstance() call. Avoids creating the instance only to check that it
  // wasn't disabled. Note that, like GetInstance(), this may also return NULL
  // post-AtExit processing.
  static TraceEventETWExport* GetInstanceIfExists();

  // Enables exporting of events to ETW. If tracing is disabled for the Chrome
  // provider, AddEvent and AddCustomEvent will simply return when called.
  static void EnableETWExport();

  // Exports an event to ETW. This is mainly used in
  // TraceLog::AddTraceEventWithThreadIdAndTimestamp to export internal events.
  static void AddEvent(char phase,
                       const unsigned char* category_group_enabled,
                       const char* name,
                       unsigned long long id,
                       const TraceArguments* args);

  // Exports an ETW event that marks the end of a complete event.
  static void AddCompleteEndEvent(const char* name);

  // Returns true if any category in the group is enabled.
  static bool IsCategoryGroupEnabled(StringPiece category_group_name);

  // Called from the ETW EnableCallback when the state of the provider or
  // keywords has changed.
  static void OnETWEnableUpdate();

 private:
  // Ensure only the provider can construct us.
  friend struct StaticMemorySingletonTraits<TraceEventETWExport>;

  TraceEventETWExport();

  // Updates the list of enabled categories by consulting the ETW keyword.
  // Returns true if there was a change, false otherwise.
  bool UpdateEnabledCategories();

  // Returns true if the category is enabled.
  bool IsCategoryEnabled(StringPiece category_name) const;

  static bool is_registration_complete_;

  // Maps category names to their status (enabled/disabled).
  std::map<StringPiece, bool> categories_status_;

  // Local copy of the ETW keyword.
  uint64_t etw_match_any_keyword_;

  DISALLOW_COPY_AND_ASSIGN(TraceEventETWExport);
};

}  // namespace trace_event
}  // namespace base

#endif  // BASE_TRACE_EVENT_TRACE_EVENT_ETW_EXPORT_WIN_H_
