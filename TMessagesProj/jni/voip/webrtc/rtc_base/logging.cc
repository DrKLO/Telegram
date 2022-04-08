/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/logging.h"

#include <string.h>

#if RTC_LOG_ENABLED()

#if defined(WEBRTC_WIN)
#include <windows.h>
#if _MSC_VER < 1900
#define snprintf _snprintf
#endif
#undef ERROR  // wingdi.h
#endif

#if defined(WEBRTC_MAC) && !defined(WEBRTC_IOS)
#include <CoreServices/CoreServices.h>
#elif defined(WEBRTC_ANDROID)
#include <android/log.h>

// Android has a 1024 limit on log inputs. We use 60 chars as an
// approx for the header/tag portion.
// See android/system/core/liblog/logd_write.c
static const int kMaxLogLineSize = 1024 - 60;
#endif  // WEBRTC_MAC && !defined(WEBRTC_IOS) || WEBRTC_ANDROID

#include <inttypes.h>
#include <stdio.h>
#include <time.h>

#include <algorithm>
#include <cstdarg>
#include <vector>

#include "absl/base/attributes.h"
#include "rtc_base/checks.h"
#include "rtc_base/platform_thread_types.h"
#include "rtc_base/string_encode.h"
#include "rtc_base/string_utils.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/thread_annotations.h"
#include "rtc_base/time_utils.h"

namespace rtc {
namespace {
// By default, release builds don't log, debug builds at info level
#if !defined(NDEBUG)
static LoggingSeverity g_min_sev = LS_INFO;
static LoggingSeverity g_dbg_sev = LS_INFO;
#else
static LoggingSeverity g_min_sev = LS_NONE;
static LoggingSeverity g_dbg_sev = LS_NONE;
#endif

// Return the filename portion of the string (that following the last slash).
const char* FilenameFromPath(const char* file) {
  const char* end1 = ::strrchr(file, '/');
  const char* end2 = ::strrchr(file, '\\');
  if (!end1 && !end2)
    return file;
  else
    return (end1 > end2) ? end1 + 1 : end2 + 1;
}

// Global lock for log subsystem, only needed to serialize access to streams_.
webrtc::Mutex& GetLoggingLock() {
  static webrtc::Mutex& mutex = *new webrtc::Mutex();
  return mutex;
}

}  // namespace

/////////////////////////////////////////////////////////////////////////////
// LogMessage
/////////////////////////////////////////////////////////////////////////////

bool LogMessage::log_to_stderr_ = true;

// The list of logging streams currently configured.
// Note: we explicitly do not clean this up, because of the uncertain ordering
// of destructors at program exit.  Let the person who sets the stream trigger
// cleanup by setting to null, or let it leak (safe at program exit).
ABSL_CONST_INIT LogSink* LogMessage::streams_ RTC_GUARDED_BY(GetLoggingLock()) =
    nullptr;
ABSL_CONST_INIT std::atomic<bool> LogMessage::streams_empty_ = {true};

// Boolean options default to false (0)
bool LogMessage::thread_, LogMessage::timestamp_;

LogMessage::LogMessage(const char* file, int line, LoggingSeverity sev)
    : LogMessage(file, line, sev, ERRCTX_NONE, 0) {}

LogMessage::LogMessage(const char* file,
                       int line,
                       LoggingSeverity sev,
                       LogErrorContext err_ctx,
                       int err)
    : severity_(sev) {
  if (timestamp_) {
    // Use SystemTimeMillis so that even if tests use fake clocks, the timestamp
    // in log messages represents the real system time.
    int64_t time = TimeDiff(SystemTimeMillis(), LogStartTime());
    // Also ensure WallClockStartTime is initialized, so that it matches
    // LogStartTime.
    WallClockStartTime();
    // TODO(kwiberg): Switch to absl::StrFormat, if binary size is ok.
    char timestamp[50];  // Maximum string length of an int64_t is 20.
    int len =
        snprintf(timestamp, sizeof(timestamp), "[%03" PRId64 ":%03" PRId64 "]",
                 time / 1000, time % 1000);
    RTC_DCHECK_LT(len, sizeof(timestamp));
    print_stream_ << timestamp;
  }

  if (thread_) {
    PlatformThreadId id = CurrentThreadId();
    print_stream_ << "[" << id << "] ";
  }

  if (file != nullptr) {
#if defined(WEBRTC_ANDROID)
    tag_ = FilenameFromPath(file);
    print_stream_ << "(line " << line << "): ";
#else
    print_stream_ << "(" << FilenameFromPath(file) << ":" << line << "): ";
#endif
  }

  if (err_ctx != ERRCTX_NONE) {
    char tmp_buf[1024];
    SimpleStringBuilder tmp(tmp_buf);
    tmp.AppendFormat("[0x%08X]", err);
    switch (err_ctx) {
      case ERRCTX_ERRNO:
        tmp << " " << strerror(err);
        break;
#ifdef WEBRTC_WIN
      case ERRCTX_HRESULT: {
        char msgbuf[256];
        DWORD flags =
            FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS;
        if (DWORD len = FormatMessageA(
                flags, nullptr, err, MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
                msgbuf, sizeof(msgbuf) / sizeof(msgbuf[0]), nullptr)) {
          while ((len > 0) &&
                 isspace(static_cast<unsigned char>(msgbuf[len - 1]))) {
            msgbuf[--len] = 0;
          }
          tmp << " " << msgbuf;
        }
        break;
      }
#endif  // WEBRTC_WIN
      default:
        break;
    }
    extra_ = tmp.str();
  }
}

#if defined(WEBRTC_ANDROID)
LogMessage::LogMessage(const char* file,
                       int line,
                       LoggingSeverity sev,
                       const char* tag)
    : LogMessage(file, line, sev, ERRCTX_NONE, 0 /* err */) {
  tag_ = tag;
  print_stream_ << tag << ": ";
}
#endif

// DEPRECATED. Currently only used by downstream projects that use
// implementation details of logging.h. Work is ongoing to remove those
// dependencies.
LogMessage::LogMessage(const char* file,
                       int line,
                       LoggingSeverity sev,
                       const std::string& tag)
    : LogMessage(file, line, sev) {
  print_stream_ << tag << ": ";
}

LogMessage::~LogMessage() {
  FinishPrintStream();

  const std::string str = print_stream_.Release();

  if (severity_ >= g_dbg_sev) {
#if defined(WEBRTC_ANDROID)
    OutputToDebug(str, severity_, tag_);
#else
    OutputToDebug(str, severity_);
#endif
  }

  webrtc::MutexLock lock(&GetLoggingLock());
  for (LogSink* entry = streams_; entry != nullptr; entry = entry->next_) {
    if (severity_ >= entry->min_severity_) {
#if defined(WEBRTC_ANDROID)
      entry->OnLogMessage(str, severity_, tag_);
#else
      entry->OnLogMessage(str, severity_);
#endif
    }
  }
}

void LogMessage::AddTag(const char* tag) {
#ifdef WEBRTC_ANDROID
  tag_ = tag;
#endif
}

rtc::StringBuilder& LogMessage::stream() {
  return print_stream_;
}

int LogMessage::GetMinLogSeverity() {
  return g_min_sev;
}

LoggingSeverity LogMessage::GetLogToDebug() {
  return g_dbg_sev;
}
int64_t LogMessage::LogStartTime() {
  static const int64_t g_start = SystemTimeMillis();
  return g_start;
}

uint32_t LogMessage::WallClockStartTime() {
  static const uint32_t g_start_wallclock = time(nullptr);
  return g_start_wallclock;
}

void LogMessage::LogThreads(bool on) {
  thread_ = on;
}

void LogMessage::LogTimestamps(bool on) {
  timestamp_ = on;
}

void LogMessage::LogToDebug(LoggingSeverity min_sev) {
  g_dbg_sev = min_sev;
  webrtc::MutexLock lock(&GetLoggingLock());
  UpdateMinLogSeverity();
}

void LogMessage::SetLogToStderr(bool log_to_stderr) {
  log_to_stderr_ = log_to_stderr;
}

int LogMessage::GetLogToStream(LogSink* stream) {
  webrtc::MutexLock lock(&GetLoggingLock());
  LoggingSeverity sev = LS_NONE;
  for (LogSink* entry = streams_; entry != nullptr; entry = entry->next_) {
    if (stream == nullptr || stream == entry) {
      sev = std::min(sev, entry->min_severity_);
    }
  }
  return sev;
}

void LogMessage::AddLogToStream(LogSink* stream, LoggingSeverity min_sev) {
  webrtc::MutexLock lock(&GetLoggingLock());
  stream->min_severity_ = min_sev;
  stream->next_ = streams_;
  streams_ = stream;
  streams_empty_.store(false, std::memory_order_relaxed);
  UpdateMinLogSeverity();
}

void LogMessage::RemoveLogToStream(LogSink* stream) {
  webrtc::MutexLock lock(&GetLoggingLock());
  for (LogSink** entry = &streams_; *entry != nullptr;
       entry = &(*entry)->next_) {
    if (*entry == stream) {
      *entry = (*entry)->next_;
      break;
    }
  }
  streams_empty_.store(streams_ == nullptr, std::memory_order_relaxed);
  UpdateMinLogSeverity();
}

void LogMessage::ConfigureLogging(const char* params) {
  LoggingSeverity current_level = LS_VERBOSE;
  LoggingSeverity debug_level = GetLogToDebug();

  std::vector<std::string> tokens;
  tokenize(params, ' ', &tokens);

  for (const std::string& token : tokens) {
    if (token.empty())
      continue;

    // Logging features
    if (token == "tstamp") {
      LogTimestamps();
    } else if (token == "thread") {
      LogThreads();

      // Logging levels
    } else if (token == "verbose") {
      current_level = LS_VERBOSE;
    } else if (token == "info") {
      current_level = LS_INFO;
    } else if (token == "warning") {
      current_level = LS_WARNING;
    } else if (token == "error") {
      current_level = LS_ERROR;
    } else if (token == "none") {
      current_level = LS_NONE;

      // Logging targets
    } else if (token == "debug") {
      debug_level = current_level;
    }
  }

#if defined(WEBRTC_WIN) && !defined(WINUWP)
  if ((LS_NONE != debug_level) && !::IsDebuggerPresent()) {
    // First, attempt to attach to our parent's console... so if you invoke
    // from the command line, we'll see the output there.  Otherwise, create
    // our own console window.
    // Note: These methods fail if a console already exists, which is fine.
    if (!AttachConsole(ATTACH_PARENT_PROCESS))
      ::AllocConsole();
  }
#endif  // defined(WEBRTC_WIN) && !defined(WINUWP)

  LogToDebug(debug_level);
}

void LogMessage::UpdateMinLogSeverity()
    RTC_EXCLUSIVE_LOCKS_REQUIRED(GetLoggingLock()) {
  LoggingSeverity min_sev = g_dbg_sev;
  for (LogSink* entry = streams_; entry != nullptr; entry = entry->next_) {
    min_sev = std::min(min_sev, entry->min_severity_);
  }
  g_min_sev = min_sev;
}

#if defined(WEBRTC_ANDROID)
void LogMessage::OutputToDebug(const std::string& str,
                               LoggingSeverity severity,
                               const char* tag) {
#else
void LogMessage::OutputToDebug(const std::string& str,
                               LoggingSeverity severity) {
#endif
  bool log_to_stderr = log_to_stderr_;
#if defined(WEBRTC_MAC) && !defined(WEBRTC_IOS) && defined(NDEBUG)
  // On the Mac, all stderr output goes to the Console log and causes clutter.
  // So in opt builds, don't log to stderr unless the user specifically sets
  // a preference to do so.
  CFStringRef key = CFStringCreateWithCString(
      kCFAllocatorDefault, "logToStdErr", kCFStringEncodingUTF8);
  CFStringRef domain = CFBundleGetIdentifier(CFBundleGetMainBundle());
  if (key != nullptr && domain != nullptr) {
    Boolean exists_and_is_valid;
    Boolean should_log =
        CFPreferencesGetAppBooleanValue(key, domain, &exists_and_is_valid);
    // If the key doesn't exist or is invalid or is false, we will not log to
    // stderr.
    log_to_stderr = exists_and_is_valid && should_log;
  }
  if (key != nullptr) {
    CFRelease(key);
  }
#endif  // defined(WEBRTC_MAC) && !defined(WEBRTC_IOS) && defined(NDEBUG)

#if defined(WEBRTC_WIN)
  // Always log to the debugger.
  // Perhaps stderr should be controlled by a preference, as on Mac?
  OutputDebugStringA(str.c_str());
  if (log_to_stderr) {
    // This handles dynamically allocated consoles, too.
    if (HANDLE error_handle = ::GetStdHandle(STD_ERROR_HANDLE)) {
      log_to_stderr = false;
      DWORD written = 0;
      ::WriteFile(error_handle, str.data(), static_cast<DWORD>(str.size()),
                  &written, 0);
    }
  }
#endif  // WEBRTC_WIN

#if defined(WEBRTC_ANDROID)
  // Android's logging facility uses severity to log messages but we
  // need to map libjingle's severity levels to Android ones first.
  // Also write to stderr which maybe available to executable started
  // from the shell.
  int prio;
  switch (severity) {
    case LS_VERBOSE:
      prio = ANDROID_LOG_VERBOSE;
      break;
    case LS_INFO:
      prio = ANDROID_LOG_INFO;
      break;
    case LS_WARNING:
      prio = ANDROID_LOG_WARN;
      break;
    case LS_ERROR:
      prio = ANDROID_LOG_ERROR;
      break;
    default:
      prio = ANDROID_LOG_UNKNOWN;
  }

  int size = str.size();
  int line = 0;
  int idx = 0;
  const int max_lines = size / kMaxLogLineSize + 1;
  if (max_lines == 1) {
    __android_log_print(prio, tag, "%.*s", size, str.c_str());
  } else {
    while (size > 0) {
      const int len = std::min(size, kMaxLogLineSize);
      // Use the size of the string in the format (str may have \0 in the
      // middle).
      __android_log_print(prio, tag, "[%d/%d] %.*s", line + 1, max_lines, len,
                          str.c_str() + idx);
      idx += len;
      size -= len;
      ++line;
    }
  }
#endif  // WEBRTC_ANDROID
  if (log_to_stderr) {
    fprintf(stderr, "%s", str.c_str());
    fflush(stderr);
  }
}

// static
bool LogMessage::IsNoop(LoggingSeverity severity) {
  if (severity >= g_dbg_sev || severity >= g_min_sev)
    return false;
  return streams_empty_.load(std::memory_order_relaxed);
}

void LogMessage::FinishPrintStream() {
  if (!extra_.empty())
    print_stream_ << " : " << extra_;
  print_stream_ << "\n";
}

namespace webrtc_logging_impl {

void Log(const LogArgType* fmt, ...) {
  va_list args;
  va_start(args, fmt);

  LogMetadataErr meta;
  const char* tag = nullptr;
  switch (*fmt) {
    case LogArgType::kLogMetadata: {
      meta = {va_arg(args, LogMetadata), ERRCTX_NONE, 0};
      break;
    }
    case LogArgType::kLogMetadataErr: {
      meta = va_arg(args, LogMetadataErr);
      break;
    }
#ifdef WEBRTC_ANDROID
    case LogArgType::kLogMetadataTag: {
      const LogMetadataTag tag_meta = va_arg(args, LogMetadataTag);
      meta = {{nullptr, 0, tag_meta.severity}, ERRCTX_NONE, 0};
      tag = tag_meta.tag;
      break;
    }
#endif
    default: {
      RTC_DCHECK_NOTREACHED();
      va_end(args);
      return;
    }
  }

  LogMessage log_message(meta.meta.File(), meta.meta.Line(),
                         meta.meta.Severity(), meta.err_ctx, meta.err);
  if (tag) {
    log_message.AddTag(tag);
  }

  for (++fmt; *fmt != LogArgType::kEnd; ++fmt) {
    switch (*fmt) {
      case LogArgType::kInt:
        log_message.stream() << va_arg(args, int);
        break;
      case LogArgType::kLong:
        log_message.stream() << va_arg(args, long);
        break;
      case LogArgType::kLongLong:
        log_message.stream() << va_arg(args, long long);
        break;
      case LogArgType::kUInt:
        log_message.stream() << va_arg(args, unsigned);
        break;
      case LogArgType::kULong:
        log_message.stream() << va_arg(args, unsigned long);
        break;
      case LogArgType::kULongLong:
        log_message.stream() << va_arg(args, unsigned long long);
        break;
      case LogArgType::kDouble:
        log_message.stream() << va_arg(args, double);
        break;
      case LogArgType::kLongDouble:
        log_message.stream() << va_arg(args, long double);
        break;
      case LogArgType::kCharP: {
        const char* s = va_arg(args, const char*);
        log_message.stream() << (s ? s : "(null)");
        break;
      }
      case LogArgType::kStdString:
        log_message.stream() << *va_arg(args, const std::string*);
        break;
      case LogArgType::kStringView:
        log_message.stream() << *va_arg(args, const absl::string_view*);
        break;
      case LogArgType::kVoidP:
        log_message.stream() << rtc::ToHex(
            reinterpret_cast<uintptr_t>(va_arg(args, const void*)));
        break;
      default:
        RTC_DCHECK_NOTREACHED();
        va_end(args);
        return;
    }
  }

  va_end(args);
}

}  // namespace webrtc_logging_impl
}  // namespace rtc
#endif

namespace rtc {
// Inefficient default implementation, override is recommended.
void LogSink::OnLogMessage(const std::string& msg,
                           LoggingSeverity severity,
                           const char* tag) {
  OnLogMessage(tag + (": " + msg), severity);
}

void LogSink::OnLogMessage(const std::string& msg,
                           LoggingSeverity /* severity */) {
  OnLogMessage(msg);
}
}  // namespace rtc
