/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "rtc_base/event_tracer.h"

#include <inttypes.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include <string>
#include <vector>

#include "api/sequence_checker.h"
#include "rtc_base/atomic_ops.h"
#include "rtc_base/checks.h"
#include "rtc_base/event.h"
#include "rtc_base/logging.h"
#include "rtc_base/platform_thread.h"
#include "rtc_base/platform_thread_types.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/thread_annotations.h"
#include "rtc_base/time_utils.h"
#include "rtc_base/trace_event.h"

// This is a guesstimate that should be enough in most cases.
static const size_t kEventLoggerArgsStrBufferInitialSize = 256;
static const size_t kTraceArgBufferLength = 32;

namespace webrtc {

namespace {

GetCategoryEnabledPtr g_get_category_enabled_ptr = nullptr;
AddTraceEventPtr g_add_trace_event_ptr = nullptr;

}  // namespace

void SetupEventTracer(GetCategoryEnabledPtr get_category_enabled_ptr,
                      AddTraceEventPtr add_trace_event_ptr) {
  g_get_category_enabled_ptr = get_category_enabled_ptr;
  g_add_trace_event_ptr = add_trace_event_ptr;
}

const unsigned char* EventTracer::GetCategoryEnabled(const char* name) {
  if (g_get_category_enabled_ptr)
    return g_get_category_enabled_ptr(name);

  // A string with null terminator means category is disabled.
  return reinterpret_cast<const unsigned char*>("\0");
}

// Arguments to this function (phase, etc.) are as defined in
// webrtc/rtc_base/trace_event.h.
void EventTracer::AddTraceEvent(char phase,
                                const unsigned char* category_enabled,
                                const char* name,
                                unsigned long long id,
                                int num_args,
                                const char** arg_names,
                                const unsigned char* arg_types,
                                const unsigned long long* arg_values,
                                unsigned char flags) {
  if (g_add_trace_event_ptr) {
    g_add_trace_event_ptr(phase, category_enabled, name, id, num_args,
                          arg_names, arg_types, arg_values, flags);
  }
}

}  // namespace webrtc

namespace rtc {
namespace tracing {
namespace {

// Atomic-int fast path for avoiding logging when disabled.
static volatile int g_event_logging_active = 0;

// TODO(pbos): Log metadata for all threads, etc.
class EventLogger final {
 public:
  ~EventLogger() { RTC_DCHECK(thread_checker_.IsCurrent()); }

  void AddTraceEvent(const char* name,
                     const unsigned char* category_enabled,
                     char phase,
                     int num_args,
                     const char** arg_names,
                     const unsigned char* arg_types,
                     const unsigned long long* arg_values,
                     uint64_t timestamp,
                     int pid,
                     rtc::PlatformThreadId thread_id) {
    std::vector<TraceArg> args(num_args);
    for (int i = 0; i < num_args; ++i) {
      TraceArg& arg = args[i];
      arg.name = arg_names[i];
      arg.type = arg_types[i];
      arg.value.as_uint = arg_values[i];

      // Value is a pointer to a temporary string, so we have to make a copy.
      if (arg.type == TRACE_VALUE_TYPE_COPY_STRING) {
        // Space for the string and for the terminating null character.
        size_t str_length = strlen(arg.value.as_string) + 1;
        char* str_copy = new char[str_length];
        memcpy(str_copy, arg.value.as_string, str_length);
        arg.value.as_string = str_copy;
      }
    }
    webrtc::MutexLock lock(&mutex_);
    trace_events_.push_back(
        {name, category_enabled, phase, args, timestamp, 1, thread_id});
  }

  // The TraceEvent format is documented here:
  // https://docs.google.com/document/d/1CvAClvFfyA5R-PhYUmn5OOQtYMH4h6I0nSsKchNAySU/preview
  void Log() {
    RTC_DCHECK(output_file_);
    static const int kLoggingIntervalMs = 100;
    fprintf(output_file_, "{ \"traceEvents\": [\n");
    bool has_logged_event = false;
    while (true) {
      bool shutting_down = shutdown_event_.Wait(kLoggingIntervalMs);
      std::vector<TraceEvent> events;
      {
        webrtc::MutexLock lock(&mutex_);
        trace_events_.swap(events);
      }
      std::string args_str;
      args_str.reserve(kEventLoggerArgsStrBufferInitialSize);
      for (TraceEvent& e : events) {
        args_str.clear();
        if (!e.args.empty()) {
          args_str += ", \"args\": {";
          bool is_first_argument = true;
          for (TraceArg& arg : e.args) {
            if (!is_first_argument)
              args_str += ",";
            is_first_argument = false;
            args_str += " \"";
            args_str += arg.name;
            args_str += "\": ";
            args_str += TraceArgValueAsString(arg);

            // Delete our copy of the string.
            if (arg.type == TRACE_VALUE_TYPE_COPY_STRING) {
              delete[] arg.value.as_string;
              arg.value.as_string = nullptr;
            }
          }
          args_str += " }";
        }
        fprintf(output_file_,
                "%s{ \"name\": \"%s\""
                ", \"cat\": \"%s\""
                ", \"ph\": \"%c\""
                ", \"ts\": %" PRIu64
                ", \"pid\": %d"
#if defined(WEBRTC_WIN)
                ", \"tid\": %lu"
#else
                ", \"tid\": %d"
#endif  // defined(WEBRTC_WIN)
                "%s"
                "}\n",
                has_logged_event ? "," : " ", e.name, e.category_enabled,
                e.phase, e.timestamp, e.pid, e.tid, args_str.c_str());
        has_logged_event = true;
      }
      if (shutting_down)
        break;
    }
    fprintf(output_file_, "]}\n");
    if (output_file_owned_)
      fclose(output_file_);
    output_file_ = nullptr;
  }

  void Start(FILE* file, bool owned) {
    RTC_DCHECK(thread_checker_.IsCurrent());
    RTC_DCHECK(file);
    RTC_DCHECK(!output_file_);
    output_file_ = file;
    output_file_owned_ = owned;
    {
      webrtc::MutexLock lock(&mutex_);
      // Since the atomic fast-path for adding events to the queue can be
      // bypassed while the logging thread is shutting down there may be some
      // stale events in the queue, hence the vector needs to be cleared to not
      // log events from a previous logging session (which may be days old).
      trace_events_.clear();
    }
    // Enable event logging (fast-path). This should be disabled since starting
    // shouldn't be done twice.
    RTC_CHECK_EQ(0,
                 rtc::AtomicOps::CompareAndSwap(&g_event_logging_active, 0, 1));

    // Finally start, everything should be set up now.
    logging_thread_ =
        PlatformThread::SpawnJoinable([this] { Log(); }, "EventTracingThread");
    TRACE_EVENT_INSTANT0("webrtc", "EventLogger::Start");
  }

  void Stop() {
    RTC_DCHECK(thread_checker_.IsCurrent());
    TRACE_EVENT_INSTANT0("webrtc", "EventLogger::Stop");
    // Try to stop. Abort if we're not currently logging.
    if (rtc::AtomicOps::CompareAndSwap(&g_event_logging_active, 1, 0) == 0)
      return;

    // Wake up logging thread to finish writing.
    shutdown_event_.Set();
    // Join the logging thread.
    logging_thread_.Finalize();
  }

 private:
  struct TraceArg {
    const char* name;
    unsigned char type;
    // Copied from webrtc/rtc_base/trace_event.h TraceValueUnion.
    union TraceArgValue {
      bool as_bool;
      unsigned long long as_uint;
      long long as_int;
      double as_double;
      const void* as_pointer;
      const char* as_string;
    } value;

    // Assert that the size of the union is equal to the size of the as_uint
    // field since we are assigning to arbitrary types using it.
    static_assert(sizeof(TraceArgValue) == sizeof(unsigned long long),
                  "Size of TraceArg value union is not equal to the size of "
                  "the uint field of that union.");
  };

  struct TraceEvent {
    const char* name;
    const unsigned char* category_enabled;
    char phase;
    std::vector<TraceArg> args;
    uint64_t timestamp;
    int pid;
    rtc::PlatformThreadId tid;
  };

  static std::string TraceArgValueAsString(TraceArg arg) {
    std::string output;

    if (arg.type == TRACE_VALUE_TYPE_STRING ||
        arg.type == TRACE_VALUE_TYPE_COPY_STRING) {
      // Space for every character to be an espaced character + two for
      // quatation marks.
      output.reserve(strlen(arg.value.as_string) * 2 + 2);
      output += '\"';
      const char* c = arg.value.as_string;
      do {
        if (*c == '"' || *c == '\\') {
          output += '\\';
          output += *c;
        } else {
          output += *c;
        }
      } while (*++c);
      output += '\"';
    } else {
      output.resize(kTraceArgBufferLength);
      size_t print_length = 0;
      switch (arg.type) {
        case TRACE_VALUE_TYPE_BOOL:
          if (arg.value.as_bool) {
            strcpy(&output[0], "true");
            print_length = 4;
          } else {
            strcpy(&output[0], "false");
            print_length = 5;
          }
          break;
        case TRACE_VALUE_TYPE_UINT:
          print_length = snprintf(&output[0], kTraceArgBufferLength, "%llu",
                                  arg.value.as_uint);
          break;
        case TRACE_VALUE_TYPE_INT:
          print_length = snprintf(&output[0], kTraceArgBufferLength, "%lld",
                                  arg.value.as_int);
          break;
        case TRACE_VALUE_TYPE_DOUBLE:
          print_length = snprintf(&output[0], kTraceArgBufferLength, "%f",
                                  arg.value.as_double);
          break;
        case TRACE_VALUE_TYPE_POINTER:
          print_length = snprintf(&output[0], kTraceArgBufferLength, "\"%p\"",
                                  arg.value.as_pointer);
          break;
      }
      size_t output_length = print_length < kTraceArgBufferLength
                                 ? print_length
                                 : kTraceArgBufferLength - 1;
      // This will hopefully be very close to nop. On most implementations, it
      // just writes null byte and sets the length field of the string.
      output.resize(output_length);
    }

    return output;
  }

  webrtc::Mutex mutex_;
  std::vector<TraceEvent> trace_events_ RTC_GUARDED_BY(mutex_);
  rtc::PlatformThread logging_thread_;
  rtc::Event shutdown_event_;
  webrtc::SequenceChecker thread_checker_;
  FILE* output_file_ = nullptr;
  bool output_file_owned_ = false;
};

static EventLogger* volatile g_event_logger = nullptr;
static const char* const kDisabledTracePrefix = TRACE_DISABLED_BY_DEFAULT("");
const unsigned char* InternalGetCategoryEnabled(const char* name) {
  const char* prefix_ptr = &kDisabledTracePrefix[0];
  const char* name_ptr = name;
  // Check whether name contains the default-disabled prefix.
  while (*prefix_ptr == *name_ptr && *prefix_ptr != '\0') {
    ++prefix_ptr;
    ++name_ptr;
  }
  return reinterpret_cast<const unsigned char*>(*prefix_ptr == '\0' ? ""
                                                                    : name);
}

void InternalAddTraceEvent(char phase,
                           const unsigned char* category_enabled,
                           const char* name,
                           unsigned long long id,
                           int num_args,
                           const char** arg_names,
                           const unsigned char* arg_types,
                           const unsigned long long* arg_values,
                           unsigned char flags) {
  // Fast path for when event tracing is inactive.
  if (rtc::AtomicOps::AcquireLoad(&g_event_logging_active) == 0)
    return;

  g_event_logger->AddTraceEvent(name, category_enabled, phase, num_args,
                                arg_names, arg_types, arg_values,
                                rtc::TimeMicros(), 1, rtc::CurrentThreadId());
}

}  // namespace

void SetupInternalTracer() {
  RTC_CHECK(rtc::AtomicOps::CompareAndSwapPtr(
                &g_event_logger, static_cast<EventLogger*>(nullptr),
                new EventLogger()) == nullptr);
  webrtc::SetupEventTracer(InternalGetCategoryEnabled, InternalAddTraceEvent);
}

void StartInternalCaptureToFile(FILE* file) {
  if (g_event_logger) {
    g_event_logger->Start(file, false);
  }
}

bool StartInternalCapture(const char* filename) {
  if (!g_event_logger)
    return false;

  FILE* file = fopen(filename, "w");
  if (!file) {
    RTC_LOG(LS_ERROR) << "Failed to open trace file '" << filename
                      << "' for writing.";
    return false;
  }
  g_event_logger->Start(file, true);
  return true;
}

void StopInternalCapture() {
  if (g_event_logger) {
    g_event_logger->Stop();
  }
}

void ShutdownInternalTracer() {
  StopInternalCapture();
  EventLogger* old_logger = rtc::AtomicOps::AcquireLoadPtr(&g_event_logger);
  RTC_DCHECK(old_logger);
  RTC_CHECK(rtc::AtomicOps::CompareAndSwapPtr(
                &g_event_logger, old_logger,
                static_cast<EventLogger*>(nullptr)) == old_logger);
  delete old_logger;
  webrtc::SetupEventTracer(nullptr, nullptr);
}

}  // namespace tracing
}  // namespace rtc
