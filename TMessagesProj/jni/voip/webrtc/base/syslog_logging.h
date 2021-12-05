// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_SYSLOG_LOGGING_H_
#define BASE_SYSLOG_LOGGING_H_

#include <iosfwd>

#include "base/logging.h"
#include "build/build_config.h"

namespace logging {

// Keep in mind that the syslog is always active regardless of the logging level
// and applied flags. Use only for important information that a system
// administrator might need to maintain the browser installation.
#define SYSLOG_STREAM(severity) \
  COMPACT_GOOGLE_LOG_EX_ ## severity(EventLogMessage).stream()
#define SYSLOG(severity) \
  SYSLOG_STREAM(severity)

#if defined(OS_WIN)
// Sets the name, category and event id of the event source for logging to the
// Windows Event Log. Call this function once before using the SYSLOG macro or
// otherwise it will behave as a regular LOG macro.
void BASE_EXPORT SetEventSource(const std::string& name,
                                uint16_t category,
                                uint32_t event_id);

// The event source may get set more than once in tests.  This function allows
// a test to reset the source when needed.
void BASE_EXPORT ResetEventSourceForTesting();
#endif  // defined(OS_WIN)

// Creates a formatted message on the system event log. That would be the
// Application Event log on Windows and the messages log file on POSIX systems.
class BASE_EXPORT EventLogMessage {
 public:
  EventLogMessage(const char* file, int line, LogSeverity severity);

  ~EventLogMessage();

  std::ostream& stream() { return log_message_.stream(); }

 private:
  LogMessage log_message_;

  DISALLOW_COPY_AND_ASSIGN(EventLogMessage);
};

}  // namespace logging

#endif  // BASE_SYSLOG_LOGGING_H_
