// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/trace_event/trace_event_impl.h"

#include <stddef.h>

#include "base/format_macros.h"
#include "base/json/string_escape.h"
#include "base/memory/ptr_util.h"
#include "base/process/process_handle.h"
#include "base/stl_util.h"
#include "base/strings/string_number_conversions.h"
#include "base/strings/string_util.h"
#include "base/strings/stringprintf.h"
#include "base/strings/utf_string_conversions.h"
#include "base/trace_event/trace_event.h"
#include "base/trace_event/trace_log.h"
#include "base/trace_event/traced_value.h"

namespace base {
namespace trace_event {

bool ConvertableToTraceFormat::AppendToProto(ProtoAppender* appender) {
  return false;
}

// See comment for name TraceEvent::scope_ definition.
static_assert(trace_event_internal::kGlobalScope == nullptr,
              "Invalid TraceEvent::scope default initializer value");

TraceEvent::TraceEvent() = default;

TraceEvent::TraceEvent(int thread_id,
                       TimeTicks timestamp,
                       ThreadTicks thread_timestamp,
                       ThreadInstructionCount thread_instruction_count,
                       char phase,
                       const unsigned char* category_group_enabled,
                       const char* name,
                       const char* scope,
                       unsigned long long id,
                       unsigned long long bind_id,
                       TraceArguments* args,
                       unsigned int flags)
    : timestamp_(timestamp),
      thread_timestamp_(thread_timestamp),
      thread_instruction_count_(thread_instruction_count),
      scope_(scope),
      id_(id),
      category_group_enabled_(category_group_enabled),
      name_(name),
      thread_id_(thread_id),
      flags_(flags),
      bind_id_(bind_id),
      phase_(phase) {
  InitArgs(args);
}

TraceEvent::~TraceEvent() = default;

TraceEvent::TraceEvent(TraceEvent&& other) noexcept = default;
TraceEvent& TraceEvent::operator=(TraceEvent&& other) noexcept = default;

void TraceEvent::Reset() {
  // Only reset fields that won't be initialized in Reset(int, ...), or that may
  // hold references to other objects.
  duration_ = TimeDelta::FromInternalValue(-1);
  thread_instruction_delta_ = ThreadInstructionDelta();
  args_.Reset();
  parameter_copy_storage_.Reset();
}

void TraceEvent::Reset(int thread_id,
                       TimeTicks timestamp,
                       ThreadTicks thread_timestamp,
                       ThreadInstructionCount thread_instruction_count,
                       char phase,
                       const unsigned char* category_group_enabled,
                       const char* name,
                       const char* scope,
                       unsigned long long id,
                       unsigned long long bind_id,
                       TraceArguments* args,
                       unsigned int flags) {
  Reset();
  timestamp_ = timestamp;
  thread_timestamp_ = thread_timestamp;
  scope_ = scope;
  id_ = id;
  category_group_enabled_ = category_group_enabled;
  name_ = name;
  thread_id_ = thread_id;
  flags_ = flags;
  bind_id_ = bind_id;
  thread_instruction_count_ = thread_instruction_count;
  phase_ = phase;

  InitArgs(args);
}

void TraceEvent::InitArgs(TraceArguments* args) {
  if (args)
    args_ = std::move(*args);
  args_.CopyStringsTo(&parameter_copy_storage_,
                      !!(flags_ & TRACE_EVENT_FLAG_COPY), &name_, &scope_);
}

void TraceEvent::UpdateDuration(const TimeTicks& now,
                                const ThreadTicks& thread_now,
                                ThreadInstructionCount thread_instruction_now) {
  DCHECK_EQ(duration_.ToInternalValue(), -1);
  duration_ = now - timestamp_;

  // |thread_timestamp_| can be empty if the thread ticks clock wasn't
  // initialized when it was recorded.
  if (thread_timestamp_ != ThreadTicks())
    thread_duration_ = thread_now - thread_timestamp_;

  if (!thread_instruction_count_.is_null()) {
    thread_instruction_delta_ =
        thread_instruction_now - thread_instruction_count_;
  }
}

void TraceEvent::EstimateTraceMemoryOverhead(
    TraceEventMemoryOverhead* overhead) {
  overhead->Add(TraceEventMemoryOverhead::kTraceEvent,
                parameter_copy_storage_.EstimateTraceMemoryOverhead());

  for (size_t i = 0; i < arg_size(); ++i) {
    if (arg_type(i) == TRACE_VALUE_TYPE_CONVERTABLE)
      arg_value(i).as_convertable->EstimateTraceMemoryOverhead(overhead);
  }
}

void TraceEvent::AppendAsJSON(
    std::string* out,
    const ArgumentFilterPredicate& argument_filter_predicate) const {
  int64_t time_int64 = timestamp_.ToInternalValue();
  int process_id;
  int thread_id;
  if ((flags_ & TRACE_EVENT_FLAG_HAS_PROCESS_ID) &&
      process_id_ != kNullProcessId) {
    process_id = process_id_;
    thread_id = -1;
  } else {
    process_id = TraceLog::GetInstance()->process_id();
    thread_id = thread_id_;
  }
  const char* category_group_name =
      TraceLog::GetCategoryGroupName(category_group_enabled_);

  // Category group checked at category creation time.
  DCHECK(!strchr(name_, '"'));
  StringAppendF(out, "{\"pid\":%i,\"tid\":%i,\"ts\":%" PRId64
                     ",\"ph\":\"%c\",\"cat\":\"%s\",\"name\":",
                process_id, thread_id, time_int64, phase_, category_group_name);
  EscapeJSONString(name_, true, out);
  *out += ",\"args\":";

  // Output argument names and values, stop at first NULL argument name.
  // TODO(oysteine): The dual predicates here is a bit ugly; if the filtering
  // capabilities need to grow even more precise we should rethink this
  // approach
  ArgumentNameFilterPredicate argument_name_filter_predicate;
  bool strip_args =
      arg_size() > 0 && arg_name(0) && !argument_filter_predicate.is_null() &&
      !argument_filter_predicate.Run(category_group_name, name_,
                                     &argument_name_filter_predicate);

  if (strip_args) {
    *out += "\"__stripped__\"";
  } else {
    *out += "{";

    for (size_t i = 0; i < arg_size() && arg_name(i); ++i) {
      if (i > 0)
        *out += ",";
      *out += "\"";
      *out += arg_name(i);
      *out += "\":";

      if (argument_name_filter_predicate.is_null() ||
          argument_name_filter_predicate.Run(arg_name(i))) {
        arg_value(i).AppendAsJSON(arg_type(i), out);
      } else {
        *out += "\"__stripped__\"";
      }
    }

    *out += "}";
  }

  if (phase_ == TRACE_EVENT_PHASE_COMPLETE) {
    int64_t duration = duration_.ToInternalValue();
    if (duration != -1)
      StringAppendF(out, ",\"dur\":%" PRId64, duration);
    if (!thread_timestamp_.is_null()) {
      int64_t thread_duration = thread_duration_.ToInternalValue();
      if (thread_duration != -1)
        StringAppendF(out, ",\"tdur\":%" PRId64, thread_duration);
    }
    if (!thread_instruction_count_.is_null()) {
      int64_t thread_instructions = thread_instruction_delta_.ToInternalValue();
      StringAppendF(out, ",\"tidelta\":%" PRId64, thread_instructions);
    }
  }

  // Output tts if thread_timestamp is valid.
  if (!thread_timestamp_.is_null()) {
    int64_t thread_time_int64 = thread_timestamp_.ToInternalValue();
    StringAppendF(out, ",\"tts\":%" PRId64, thread_time_int64);
  }

  // Output ticount if thread_instruction_count is valid.
  if (!thread_instruction_count_.is_null()) {
    int64_t thread_instructions = thread_instruction_count_.ToInternalValue();
    StringAppendF(out, ",\"ticount\":%" PRId64, thread_instructions);
  }

  // Output async tts marker field if flag is set.
  if (flags_ & TRACE_EVENT_FLAG_ASYNC_TTS) {
    StringAppendF(out, ", \"use_async_tts\":1");
  }

  // If id_ is set, print it out as a hex string so we don't loose any
  // bits (it might be a 64-bit pointer).
  unsigned int id_flags_ = flags_ & (TRACE_EVENT_FLAG_HAS_ID |
                                     TRACE_EVENT_FLAG_HAS_LOCAL_ID |
                                     TRACE_EVENT_FLAG_HAS_GLOBAL_ID);
  if (id_flags_) {
    if (scope_ != trace_event_internal::kGlobalScope)
      StringAppendF(out, ",\"scope\":\"%s\"", scope_);

    switch (id_flags_) {
      case TRACE_EVENT_FLAG_HAS_ID:
        StringAppendF(out, ",\"id\":\"0x%" PRIx64 "\"",
                      static_cast<uint64_t>(id_));
        break;

      case TRACE_EVENT_FLAG_HAS_LOCAL_ID:
        StringAppendF(out, ",\"id2\":{\"local\":\"0x%" PRIx64 "\"}",
                      static_cast<uint64_t>(id_));
        break;

      case TRACE_EVENT_FLAG_HAS_GLOBAL_ID:
        StringAppendF(out, ",\"id2\":{\"global\":\"0x%" PRIx64 "\"}",
                      static_cast<uint64_t>(id_));
        break;

      default:
        NOTREACHED() << "More than one of the ID flags are set";
        break;
    }
  }

  if (flags_ & TRACE_EVENT_FLAG_BIND_TO_ENCLOSING)
    StringAppendF(out, ",\"bp\":\"e\"");

  if ((flags_ & TRACE_EVENT_FLAG_FLOW_OUT) ||
      (flags_ & TRACE_EVENT_FLAG_FLOW_IN)) {
    StringAppendF(out, ",\"bind_id\":\"0x%" PRIx64 "\"",
                  static_cast<uint64_t>(bind_id_));
  }
  if (flags_ & TRACE_EVENT_FLAG_FLOW_IN)
    StringAppendF(out, ",\"flow_in\":true");
  if (flags_ & TRACE_EVENT_FLAG_FLOW_OUT)
    StringAppendF(out, ",\"flow_out\":true");

  // Instant events also output their scope.
  if (phase_ == TRACE_EVENT_PHASE_INSTANT) {
    char scope = '?';
    switch (flags_ & TRACE_EVENT_FLAG_SCOPE_MASK) {
      case TRACE_EVENT_SCOPE_GLOBAL:
        scope = TRACE_EVENT_SCOPE_NAME_GLOBAL;
        break;

      case TRACE_EVENT_SCOPE_PROCESS:
        scope = TRACE_EVENT_SCOPE_NAME_PROCESS;
        break;

      case TRACE_EVENT_SCOPE_THREAD:
        scope = TRACE_EVENT_SCOPE_NAME_THREAD;
        break;
    }
    StringAppendF(out, ",\"s\":\"%c\"", scope);
  }

  *out += "}";
}

void TraceEvent::AppendPrettyPrinted(std::ostringstream* out) const {
  *out << name_ << "[";
  *out << TraceLog::GetCategoryGroupName(category_group_enabled_);
  *out << "]";
  if (arg_size() > 0 && arg_name(0)) {
    *out << ", {";
    for (size_t i = 0; i < arg_size() && arg_name(i); ++i) {
      if (i > 0)
        *out << ", ";
      *out << arg_name(i) << ":";
      std::string value_as_text;
      arg_value(i).AppendAsJSON(arg_type(i), &value_as_text);
      *out << value_as_text;
    }
    *out << "}";
  }
}

}  // namespace trace_event
}  // namespace base
