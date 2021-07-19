// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/logging.h"

#include <limits.h>
#include <stdint.h>

#include "base/pending_task.h"
#include "base/stl_util.h"
#include "base/task/common/task_annotator.h"
#include "base/trace_event/trace_event.h"
#include "build/build_config.h"

#if defined(OS_WIN)
#include <io.h>
#include <windows.h>
typedef HANDLE FileHandle;
typedef HANDLE MutexHandle;
// Windows warns on using write().  It prefers _write().
#define write(fd, buf, count) _write(fd, buf, static_cast<unsigned int>(count))
// Windows doesn't define STDERR_FILENO.  Define it here.
#define STDERR_FILENO 2

#elif defined(OS_MACOSX)
// In MacOS 10.12 and iOS 10.0 and later ASL (Apple System Log) was deprecated
// in favor of OS_LOG (Unified Logging).
#include <AvailabilityMacros.h>
#if defined(OS_IOS)
#if !defined(__IPHONE_10_0) || __IPHONE_OS_VERSION_MIN_REQUIRED < __IPHONE_10_0
#define USE_ASL
#endif
#else  // !defined(OS_IOS)
#if !defined(MAC_OS_X_VERSION_10_12) || \
    MAC_OS_X_VERSION_MIN_REQUIRED < MAC_OS_X_VERSION_10_12
#define USE_ASL
#endif
#endif  // defined(OS_IOS)

#if defined(USE_ASL)
#include <asl.h>
#else
#include <os/log.h>
#endif

#include <CoreFoundation/CoreFoundation.h>
#include <mach/mach.h>
#include <mach/mach_time.h>
#include <mach-o/dyld.h>

#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
#if defined(OS_NACL)
#include <sys/time.h>  // timespec doesn't seem to be in <time.h>
#endif
#include <time.h>
#endif

#if defined(OS_FUCHSIA)
#include <lib/syslog/global.h>
#include <lib/syslog/logger.h>
#include <zircon/process.h>
#include <zircon/syscalls.h>
#endif

#if defined(OS_ANDROID)
#include <android/log.h>
#endif

#if defined(OS_POSIX) || defined(OS_FUCHSIA)
#include <errno.h>
#include <paths.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include "base/process/process_handle.h"
#define MAX_PATH PATH_MAX
typedef FILE* FileHandle;
typedef pthread_mutex_t* MutexHandle;
#endif

#include <algorithm>
#include <cstring>
#include <ctime>
#include <iomanip>
#include <ostream>
#include <string>
#include <utility>

#include "base/base_switches.h"
#include "base/callback.h"
#include "base/command_line.h"
#include "base/containers/stack.h"
#include "base/debug/activity_tracker.h"
#include "base/debug/alias.h"
#include "base/debug/debugger.h"
#include "base/debug/stack_trace.h"
#include "base/debug/task_trace.h"
#include "base/no_destructor.h"
#include "base/path_service.h"
#include "base/posix/eintr_wrapper.h"
#include "base/strings/string_piece.h"
#include "base/strings/string_split.h"
#include "base/strings/string_util.h"
#include "base/strings/stringprintf.h"
#include "base/strings/sys_string_conversions.h"
#include "base/strings/utf_string_conversions.h"
#include "base/synchronization/lock_impl.h"
#include "base/threading/platform_thread.h"
#include "base/vlog.h"

#if defined(OS_WIN)
#include "base/win/win_util.h"
#endif

#if defined(OS_POSIX) || defined(OS_FUCHSIA)
#include "base/posix/safe_strerror.h"
#endif

#if defined(OS_CHROMEOS)
#include "base/files/scoped_file.h"
#endif

namespace logging {

namespace {

VlogInfo* g_vlog_info = nullptr;
VlogInfo* g_vlog_info_prev = nullptr;

const char* const log_severity_names[] = {"INFO", "WARNING", "ERROR", "FATAL"};
static_assert(LOG_NUM_SEVERITIES == base::size(log_severity_names),
              "Incorrect number of log_severity_names");

const char* log_severity_name(int severity) {
  if (severity >= 0 && severity < LOG_NUM_SEVERITIES)
    return log_severity_names[severity];
  return "UNKNOWN";
}

int g_min_log_level = 0;

// Specifies the process' logging sink(s), represented as a combination of
// LoggingDestination values joined by bitwise OR.
int g_logging_destination = LOG_DEFAULT;

// For LOG_ERROR and above, always print to stderr.
const int kAlwaysPrintErrorLevel = LOG_ERROR;

// Which log file to use? This is initialized by InitLogging or
// will be lazily initialized to the default value when it is
// first needed.
using PathString = base::FilePath::StringType;
PathString* g_log_file_name = nullptr;

// This file is lazily opened and the handle may be nullptr
FileHandle g_log_file = nullptr;

// What should be prepended to each message?
bool g_log_process_id = false;
bool g_log_thread_id = false;
bool g_log_timestamp = true;
bool g_log_tickcount = false;
const char* g_log_prefix = nullptr;

// Should we pop up fatal debug messages in a dialog?
bool show_error_dialogs = false;

// An assert handler override specified by the client to be called instead of
// the debug message dialog and process termination. Assert handlers are stored
// in stack to allow overriding and restoring.
base::stack<LogAssertHandlerFunction>& GetLogAssertHandlerStack() {
  static base::NoDestructor<base::stack<LogAssertHandlerFunction>> instance;
  return *instance;
}

// A log message handler that gets notified of every log message we process.
LogMessageHandlerFunction log_message_handler = nullptr;

uint64_t TickCount() {
#if defined(OS_WIN)
  return GetTickCount();
#elif defined(OS_FUCHSIA)
  return zx_clock_get_monotonic() /
         static_cast<zx_time_t>(base::Time::kNanosecondsPerMicrosecond);
#elif defined(OS_MACOSX)
  return mach_absolute_time();
#elif defined(OS_NACL)
  // NaCl sadly does not have _POSIX_TIMERS enabled in sys/features.h
  // So we have to use clock() for now.
  return clock();
#elif defined(OS_POSIX)
  struct timespec ts;
  clock_gettime(CLOCK_MONOTONIC, &ts);

  uint64_t absolute_micro = static_cast<int64_t>(ts.tv_sec) * 1000000 +
                            static_cast<int64_t>(ts.tv_nsec) / 1000;

  return absolute_micro;
#endif
}

void DeleteFilePath(const PathString& log_name) {
#if defined(OS_WIN)
  DeleteFile(log_name.c_str());
#elif defined(OS_NACL)
  // Do nothing; unlink() isn't supported on NaCl.
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
  unlink(log_name.c_str());
#else
#error Unsupported platform
#endif
}

PathString GetDefaultLogFile() {
#if defined(OS_WIN)
  // On Windows we use the same path as the exe.
  wchar_t module_name[MAX_PATH];
  GetModuleFileName(nullptr, module_name, MAX_PATH);

  PathString log_name = module_name;
  PathString::size_type last_backslash = log_name.rfind('\\', log_name.size());
  if (last_backslash != PathString::npos)
    log_name.erase(last_backslash + 1);
  log_name += FILE_PATH_LITERAL("debug.log");
  return log_name;
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
  // On other platforms we just use the current directory.
  return PathString("debug.log");
#endif
}

// We don't need locks on Windows for atomically appending to files. The OS
// provides this functionality.
#if defined(OS_POSIX) || defined(OS_FUCHSIA)
// This class acts as a wrapper for locking the logging files.
// LoggingLock::Init() should be called from the main thread before any logging
// is done. Then whenever logging, be sure to have a local LoggingLock
// instance on the stack. This will ensure that the lock is unlocked upon
// exiting the frame.
// LoggingLocks can not be nested.
class LoggingLock {
 public:
  LoggingLock() {
    LockLogging();
  }

  ~LoggingLock() {
    UnlockLogging();
  }

  static void Init(LogLockingState lock_log, const PathChar* new_log_file) {
    if (initialized)
      return;
    lock_log_file = lock_log;

    if (lock_log_file != LOCK_LOG_FILE)
      log_lock = new base::internal::LockImpl();

    initialized = true;
  }

 private:
  static void LockLogging() {
    if (lock_log_file == LOCK_LOG_FILE) {
      pthread_mutex_lock(&log_mutex);
    } else {
      // use the lock
      log_lock->Lock();
    }
  }

  static void UnlockLogging() {
    if (lock_log_file == LOCK_LOG_FILE) {
      pthread_mutex_unlock(&log_mutex);
    } else {
      log_lock->Unlock();
    }
  }

  // The lock is used if log file locking is false. It helps us avoid problems
  // with multiple threads writing to the log file at the same time.  Use
  // LockImpl directly instead of using Lock, because Lock makes logging calls.
  static base::internal::LockImpl* log_lock;

  // When we don't use a lock, we are using a global mutex. We need to do this
  // because LockFileEx is not thread safe.
  static pthread_mutex_t log_mutex;

  static bool initialized;
  static LogLockingState lock_log_file;
};

// static
bool LoggingLock::initialized = false;
// static
base::internal::LockImpl* LoggingLock::log_lock = nullptr;
// static
LogLockingState LoggingLock::lock_log_file = LOCK_LOG_FILE;

pthread_mutex_t LoggingLock::log_mutex = PTHREAD_MUTEX_INITIALIZER;

#endif  // OS_POSIX || OS_FUCHSIA

// Called by logging functions to ensure that |g_log_file| is initialized
// and can be used for writing. Returns false if the file could not be
// initialized. |g_log_file| will be nullptr in this case.
bool InitializeLogFileHandle() {
  if (g_log_file)
    return true;

  if (!g_log_file_name) {
    // Nobody has called InitLogging to specify a debug log file, so here we
    // initialize the log file name to a default.
    g_log_file_name = new PathString(GetDefaultLogFile());
  }

  if ((g_logging_destination & LOG_TO_FILE) == 0)
    return true;

#if defined(OS_WIN)
  // The FILE_APPEND_DATA access mask ensures that the file is atomically
  // appended to across accesses from multiple threads.
  // https://msdn.microsoft.com/en-us/library/windows/desktop/aa364399(v=vs.85).aspx
  // https://msdn.microsoft.com/en-us/library/windows/desktop/aa363858(v=vs.85).aspx
  g_log_file = CreateFile(g_log_file_name->c_str(), FILE_APPEND_DATA,
                          FILE_SHARE_READ | FILE_SHARE_WRITE, nullptr,
                          OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
  if (g_log_file == INVALID_HANDLE_VALUE || g_log_file == nullptr) {
    // We are intentionally not using FilePath or FileUtil here to reduce the
    // dependencies of the logging implementation. For e.g. FilePath and
    // FileUtil depend on shell32 and user32.dll. This is not acceptable for
    // some consumers of base logging like chrome_elf, etc.
    // Please don't change the code below to use FilePath.
    // try the current directory
    wchar_t system_buffer[MAX_PATH];
    system_buffer[0] = 0;
    DWORD len = ::GetCurrentDirectory(base::size(system_buffer), system_buffer);
    if (len == 0 || len > base::size(system_buffer))
      return false;

    *g_log_file_name = system_buffer;
    // Append a trailing backslash if needed.
    if (g_log_file_name->back() != L'\\')
      *g_log_file_name += FILE_PATH_LITERAL("\\");
    *g_log_file_name += FILE_PATH_LITERAL("debug.log");

    g_log_file = CreateFile(g_log_file_name->c_str(), FILE_APPEND_DATA,
                            FILE_SHARE_READ | FILE_SHARE_WRITE, nullptr,
                            OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (g_log_file == INVALID_HANDLE_VALUE || g_log_file == nullptr) {
      g_log_file = nullptr;
      return false;
    }
  }
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
  g_log_file = fopen(g_log_file_name->c_str(), "a");
  if (g_log_file == nullptr)
    return false;
#else
#error Unsupported platform
#endif

  return true;
}

void CloseFile(FileHandle log) {
#if defined(OS_WIN)
  CloseHandle(log);
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
  fclose(log);
#else
#error Unsupported platform
#endif
}

void CloseLogFileUnlocked() {
  if (!g_log_file)
    return;

  CloseFile(g_log_file);
  g_log_file = nullptr;

  // If we initialized logging via an externally-provided file descriptor, we
  // won't have a log path set and shouldn't try to reopen the log file.
  if (!g_log_file_name)
    g_logging_destination &= ~LOG_TO_FILE;
}

}  // namespace

#if defined(DCHECK_IS_CONFIGURABLE)
// In DCHECK-enabled Chrome builds, allow the meaning of LOG_DCHECK to be
// determined at run-time. We default it to INFO, to avoid it triggering
// crashes before the run-time has explicitly chosen the behaviour.
BASE_EXPORT logging::LogSeverity LOG_DCHECK = LOG_INFO;
#endif  // defined(DCHECK_IS_CONFIGURABLE)

// This is never instantiated, it's just used for EAT_STREAM_PARAMETERS to have
// an object of the correct type on the LHS of the unused part of the ternary
// operator.
std::ostream* g_swallow_stream;

bool BaseInitLoggingImpl(const LoggingSettings& settings) {
#if defined(OS_NACL)
  // Can log only to the system debug log and stderr.
  CHECK_EQ(settings.logging_dest & ~(LOG_TO_SYSTEM_DEBUG_LOG | LOG_TO_STDERR),
           0u);
#endif

  base::CommandLine* command_line = base::CommandLine::ForCurrentProcess();
  // Don't bother initializing |g_vlog_info| unless we use one of the
  // vlog switches.
  if (command_line->HasSwitch(switches::kV) ||
      command_line->HasSwitch(switches::kVModule)) {
    // NOTE: If |g_vlog_info| has already been initialized, it might be in use
    // by another thread. Don't delete the old VLogInfo, just create a second
    // one. We keep track of both to avoid memory leak warnings.
    CHECK(!g_vlog_info_prev);
    g_vlog_info_prev = g_vlog_info;

    g_vlog_info =
        new VlogInfo(command_line->GetSwitchValueASCII(switches::kV),
                     command_line->GetSwitchValueASCII(switches::kVModule),
                     &g_min_log_level);
  }

  g_logging_destination = settings.logging_dest;

#if defined(OS_FUCHSIA)
  if (g_logging_destination & LOG_TO_SYSTEM_DEBUG_LOG) {
    fx_logger_config_t config;
    config.min_severity = FX_LOG_INFO;
    config.console_fd = -1;
    config.log_service_channel = ZX_HANDLE_INVALID;
    std::string log_tag = command_line->GetProgram().BaseName().AsUTF8Unsafe();
    const char* log_tag_data = log_tag.data();
    config.tags = &log_tag_data;
    config.num_tags = 1;
    fx_log_init_with_config(&config);
  }
#endif

  // ignore file options unless logging to file is set.
  if ((g_logging_destination & LOG_TO_FILE) == 0)
    return true;

#if defined(OS_POSIX) || defined(OS_FUCHSIA)
  LoggingLock::Init(settings.lock_log, settings.log_file_path);
  LoggingLock logging_lock;
#endif

  // Calling InitLogging twice or after some log call has already opened the
  // default log file will re-initialize to the new options.
  CloseLogFileUnlocked();

#if defined(OS_CHROMEOS)
  if (settings.log_file) {
    DCHECK(!settings.log_file_path);
    g_log_file = settings.log_file;
    return true;
  }
#endif

  if (!g_log_file_name)
    g_log_file_name = new PathString();
  *g_log_file_name = settings.log_file_path;
  if (settings.delete_old == DELETE_OLD_LOG_FILE)
    DeleteFilePath(*g_log_file_name);

  return InitializeLogFileHandle();
}

void SetMinLogLevel(int level) {
  g_min_log_level = std::min(LOG_FATAL, level);
}

int GetMinLogLevel() {
  return g_min_log_level;
}

bool ShouldCreateLogMessage(int severity) {
  if (severity < g_min_log_level)
    return false;

  // Return true here unless we know ~LogMessage won't do anything.
  return g_logging_destination != LOG_NONE || log_message_handler ||
         severity >= kAlwaysPrintErrorLevel;
}

// Returns true when LOG_TO_STDERR flag is set, or |severity| is high.
// If |severity| is high then true will be returned when no log destinations are
// set, or only LOG_TO_FILE is set, since that is useful for local development
// and debugging.
bool ShouldLogToStderr(int severity) {
  if (g_logging_destination & LOG_TO_STDERR)
    return true;
  if (severity >= kAlwaysPrintErrorLevel)
    return (g_logging_destination & ~LOG_TO_FILE) == LOG_NONE;
  return false;
}

int GetVlogVerbosity() {
  return std::max(-1, LOG_INFO - GetMinLogLevel());
}

int GetVlogLevelHelper(const char* file, size_t N) {
  DCHECK_GT(N, 0U);
  // Note: |g_vlog_info| may change on a different thread during startup
  // (but will always be valid or nullptr).
  VlogInfo* vlog_info = g_vlog_info;
  return vlog_info ?
      vlog_info->GetVlogLevel(base::StringPiece(file, N - 1)) :
      GetVlogVerbosity();
}

void SetLogItems(bool enable_process_id, bool enable_thread_id,
                 bool enable_timestamp, bool enable_tickcount) {
  g_log_process_id = enable_process_id;
  g_log_thread_id = enable_thread_id;
  g_log_timestamp = enable_timestamp;
  g_log_tickcount = enable_tickcount;
}

void SetLogPrefix(const char* prefix) {
  DCHECK(!prefix ||
         base::ContainsOnlyChars(prefix, "abcdefghijklmnopqrstuvwxyz"));
  g_log_prefix = prefix;
}

void SetShowErrorDialogs(bool enable_dialogs) {
  show_error_dialogs = enable_dialogs;
}

ScopedLogAssertHandler::ScopedLogAssertHandler(
    LogAssertHandlerFunction handler) {
  GetLogAssertHandlerStack().push(std::move(handler));
}

ScopedLogAssertHandler::~ScopedLogAssertHandler() {
  GetLogAssertHandlerStack().pop();
}

void SetLogMessageHandler(LogMessageHandlerFunction handler) {
  log_message_handler = handler;
}

LogMessageHandlerFunction GetLogMessageHandler() {
  return log_message_handler;
}

// Explicit instantiations for commonly used comparisons.
template std::string* MakeCheckOpString<int, int>(
    const int&, const int&, const char* names);
template std::string* MakeCheckOpString<unsigned long, unsigned long>(
    const unsigned long&, const unsigned long&, const char* names);
template std::string* MakeCheckOpString<unsigned long, unsigned int>(
    const unsigned long&, const unsigned int&, const char* names);
template std::string* MakeCheckOpString<unsigned int, unsigned long>(
    const unsigned int&, const unsigned long&, const char* names);
template std::string* MakeCheckOpString<std::string, std::string>(
    const std::string&, const std::string&, const char* name);

void MakeCheckOpValueString(std::ostream* os, std::nullptr_t p) {
  (*os) << "nullptr";
}

#if !defined(NDEBUG)
// Displays a message box to the user with the error message in it.
// Used for fatal messages, where we close the app simultaneously.
// This is for developers only; we don't use this in circumstances
// (like release builds) where users could see it, since users don't
// understand these messages anyway.
void DisplayDebugMessageInDialog(const std::string& str) {
  if (str.empty())
    return;

  if (!show_error_dialogs)
    return;

#if defined(OS_WIN)
  // We intentionally don't implement a dialog on other platforms.
  // You can just look at stderr.
  if (base::win::IsUser32AndGdi32Available()) {
    MessageBoxW(nullptr, base::as_wcstr(base::UTF8ToUTF16(str)), L"Fatal error",
                MB_OK | MB_ICONHAND | MB_TOPMOST);
  } else {
    OutputDebugStringW(base::as_wcstr(base::UTF8ToUTF16(str)));
  }
#endif  // defined(OS_WIN)
}
#endif  // !defined(NDEBUG)

LogMessage::LogMessage(const char* file, int line, LogSeverity severity)
    : severity_(severity), file_(file), line_(line) {
  Init(file, line);
}

LogMessage::LogMessage(const char* file, int line, const char* condition)
    : severity_(LOG_FATAL), file_(file), line_(line) {
  Init(file, line);
  stream_ << "Check failed: " << condition << ". ";
}

LogMessage::LogMessage(const char* file, int line, std::string* result)
    : severity_(LOG_FATAL), file_(file), line_(line) {
  Init(file, line);
  stream_ << "Check failed: " << *result;
  delete result;
}

LogMessage::LogMessage(const char* file, int line, LogSeverity severity,
                       std::string* result)
    : severity_(severity), file_(file), line_(line) {
  Init(file, line);
  stream_ << "Check failed: " << *result;
  delete result;
}

LogMessage::~LogMessage() {
  size_t stack_start = stream_.tellp();
#if !defined(OFFICIAL_BUILD) && !defined(OS_NACL) && !defined(__UCLIBC__) && \
    !defined(OS_AIX)
  if (severity_ == LOG_FATAL && !base::debug::BeingDebugged()) {
    // Include a stack trace on a fatal, unless a debugger is attached.
    base::debug::StackTrace stack_trace;
    stream_ << std::endl;  // Newline to separate from log message.
    stack_trace.OutputToStream(&stream_);
    base::debug::TaskTrace task_trace;
    if (!task_trace.empty())
      task_trace.OutputToStream(&stream_);

    // Include the IPC context, if any.
    // TODO(chrisha): Integrate with symbolization once those tools exist!
    const auto* task = base::TaskAnnotator::CurrentTaskForThread();
    if (task && task->ipc_hash) {
      stream_ << "IPC message handler context: "
              << base::StringPrintf("0x%08X", task->ipc_hash) << std::endl;
    }
  }
#endif
  stream_ << std::endl;
  std::string str_newline(stream_.str());
  TRACE_LOG_MESSAGE(
      file_, base::StringPiece(str_newline).substr(message_start_), line_);

  // Give any log message handler first dibs on the message.
  if (log_message_handler &&
      log_message_handler(severity_, file_, line_,
                          message_start_, str_newline)) {
    // The handler took care of it, no further processing.
    return;
  }

  if ((g_logging_destination & LOG_TO_SYSTEM_DEBUG_LOG) != 0) {
#if defined(OS_WIN)
    OutputDebugStringA(str_newline.c_str());
#elif defined(OS_MACOSX)
    // In LOG_TO_SYSTEM_DEBUG_LOG mode, log messages are always written to
    // stderr. If stderr is /dev/null, also log via ASL (Apple System Log) or
    // its successor OS_LOG. If there's something weird about stderr, assume
    // that log messages are going nowhere and log via ASL/OS_LOG too.
    // Messages logged via ASL/OS_LOG show up in Console.app.
    //
    // Programs started by launchd, as UI applications normally are, have had
    // stderr connected to /dev/null since OS X 10.8. Prior to that, stderr was
    // a pipe to launchd, which logged what it received (see log_redirect_fd in
    // 10.7.5 launchd-392.39/launchd/src/launchd_core_logic.c).
    //
    // Another alternative would be to determine whether stderr is a pipe to
    // launchd and avoid logging via ASL only in that case. See 10.7.5
    // CF-635.21/CFUtilities.c also_do_stderr(). This would result in logging to
    // both stderr and ASL/OS_LOG even in tests, where it's undesirable to log
    // to the system log at all.
    //
    // Note that the ASL client by default discards messages whose levels are
    // below ASL_LEVEL_NOTICE. It's possible to change that with
    // asl_set_filter(), but this is pointless because syslogd normally applies
    // the same filter.
    const bool log_to_system = []() {
      struct stat stderr_stat;
      if (fstat(fileno(stderr), &stderr_stat) == -1) {
        return true;
      }
      if (!S_ISCHR(stderr_stat.st_mode)) {
        return false;
      }

      struct stat dev_null_stat;
      if (stat(_PATH_DEVNULL, &dev_null_stat) == -1) {
        return true;
      }

      return !S_ISCHR(dev_null_stat.st_mode) ||
             stderr_stat.st_rdev == dev_null_stat.st_rdev;
    }();

    if (log_to_system) {
      // Log roughly the same way that CFLog() and NSLog() would. See 10.10.5
      // CF-1153.18/CFUtilities.c __CFLogCString().
      CFBundleRef main_bundle = CFBundleGetMainBundle();
      CFStringRef main_bundle_id_cf =
          main_bundle ? CFBundleGetIdentifier(main_bundle) : nullptr;
      std::string main_bundle_id =
          main_bundle_id_cf ? base::SysCFStringRefToUTF8(main_bundle_id_cf)
                            : std::string("");
#if defined(USE_ASL)
      // The facility is set to the main bundle ID if available. Otherwise,
      // "com.apple.console" is used.
      const class ASLClient {
       public:
        explicit ASLClient(const std::string& facility)
            : client_(asl_open(nullptr, facility.c_str(), ASL_OPT_NO_DELAY)) {}
        ~ASLClient() { asl_close(client_); }

        aslclient get() const { return client_; }

       private:
        aslclient client_;
        DISALLOW_COPY_AND_ASSIGN(ASLClient);
      } asl_client(main_bundle_id.empty() ? main_bundle_id
                                          : "com.apple.console");

      const class ASLMessage {
       public:
        ASLMessage() : message_(asl_new(ASL_TYPE_MSG)) {}
        ~ASLMessage() { asl_free(message_); }

        aslmsg get() const { return message_; }

       private:
        aslmsg message_;
        DISALLOW_COPY_AND_ASSIGN(ASLMessage);
      } asl_message;

      // By default, messages are only readable by the admin group. Explicitly
      // make them readable by the user generating the messages.
      char euid_string[12];
      snprintf(euid_string, base::size(euid_string), "%d", geteuid());
      asl_set(asl_message.get(), ASL_KEY_READ_UID, euid_string);

      // Map Chrome log severities to ASL log levels.
      const char* const asl_level_string = [](LogSeverity severity) {
        // ASL_LEVEL_* are ints, but ASL needs equivalent strings. This
        // non-obvious two-step macro trick achieves what's needed.
        // https://gcc.gnu.org/onlinedocs/cpp/Stringification.html
#define ASL_LEVEL_STR(level) ASL_LEVEL_STR_X(level)
#define ASL_LEVEL_STR_X(level) #level
        switch (severity) {
          case LOG_INFO:
            return ASL_LEVEL_STR(ASL_LEVEL_INFO);
          case LOG_WARNING:
            return ASL_LEVEL_STR(ASL_LEVEL_WARNING);
          case LOG_ERROR:
            return ASL_LEVEL_STR(ASL_LEVEL_ERR);
          case LOG_FATAL:
            return ASL_LEVEL_STR(ASL_LEVEL_CRIT);
          default:
            return severity < 0 ? ASL_LEVEL_STR(ASL_LEVEL_DEBUG)
                                : ASL_LEVEL_STR(ASL_LEVEL_NOTICE);
        }
#undef ASL_LEVEL_STR
#undef ASL_LEVEL_STR_X
      }(severity_);
      asl_set(asl_message.get(), ASL_KEY_LEVEL, asl_level_string);

      asl_set(asl_message.get(), ASL_KEY_MSG, str_newline.c_str());

      asl_send(asl_client.get(), asl_message.get());
#else   // !defined(USE_ASL)
      const class OSLog {
       public:
        explicit OSLog(const char* subsystem)
            : os_log_(subsystem ? os_log_create(subsystem, "chromium_logging")
                                : OS_LOG_DEFAULT) {}
        ~OSLog() {
          if (os_log_ != OS_LOG_DEFAULT) {
            os_release(os_log_);
          }
        }
        os_log_t get() const { return os_log_; }

       private:
        os_log_t os_log_;
        DISALLOW_COPY_AND_ASSIGN(OSLog);
      } log(main_bundle_id.empty() ? nullptr : main_bundle_id.c_str());
      const os_log_type_t os_log_type = [](LogSeverity severity) {
        switch (severity) {
          case LOG_INFO:
            return OS_LOG_TYPE_INFO;
          case LOG_WARNING:
            return OS_LOG_TYPE_DEFAULT;
          case LOG_ERROR:
            return OS_LOG_TYPE_ERROR;
          case LOG_FATAL:
            return OS_LOG_TYPE_FAULT;
          default:
            return severity < 0 ? OS_LOG_TYPE_DEBUG : OS_LOG_TYPE_DEFAULT;
        }
      }(severity_);
      os_log_with_type(log.get(), os_log_type, "%{public}s",
                       str_newline.c_str());
#endif  // defined(USE_ASL)
    }
#elif defined(OS_ANDROID)
    android_LogPriority priority =
        (severity_ < 0) ? ANDROID_LOG_VERBOSE : ANDROID_LOG_UNKNOWN;
    switch (severity_) {
      case LOG_INFO:
        priority = ANDROID_LOG_INFO;
        break;
      case LOG_WARNING:
        priority = ANDROID_LOG_WARN;
        break;
      case LOG_ERROR:
        priority = ANDROID_LOG_ERROR;
        break;
      case LOG_FATAL:
        priority = ANDROID_LOG_FATAL;
        break;
    }
    const char kAndroidLogTag[] = "chromium";
#if DCHECK_IS_ON()
    // Split the output by new lines to prevent the Android system from
    // truncating the log.
    std::vector<std::string> lines = base::SplitString(
        str_newline, "\n", base::KEEP_WHITESPACE, base::SPLIT_WANT_ALL);
    // str_newline has an extra newline appended to it (at the top of this
    // function), so skip the last split element to avoid needlessly
    // logging an empty string.
    lines.pop_back();
    for (const auto& line : lines)
      __android_log_write(priority, kAndroidLogTag, line.c_str());
#else
    // The Android system may truncate the string if it's too long.
    __android_log_write(priority, kAndroidLogTag, str_newline.c_str());
#endif
#elif defined(OS_FUCHSIA)
    fx_log_severity_t severity = FX_LOG_INFO;
    switch (severity_) {
      case LOG_INFO:
        severity = FX_LOG_INFO;
        break;
      case LOG_WARNING:
        severity = FX_LOG_WARNING;
        break;
      case LOG_ERROR:
        severity = FX_LOG_ERROR;
        break;
      case LOG_FATAL:
        // Don't use FX_LOG_FATAL, otherwise fx_logger_log() will abort().
        severity = FX_LOG_ERROR;
        break;
    }

    fx_logger_t* logger = fx_log_get_logger();
    if (logger) {
      // Temporarily remove the trailing newline from |str_newline|'s C-string
      // representation, since fx_logger will add a newline of its own.
      str_newline.pop_back();
      std::string message =
          base::StringPrintf("%s(%d) %s", file_basename_, line_,
                             str_newline.c_str() + message_start_);
      fx_logger_log(logger, severity, nullptr, message.data());
      str_newline.push_back('\n');
    }
#endif  // OS_FUCHSIA
  }

  if (ShouldLogToStderr(severity_)) {
    ignore_result(fwrite(str_newline.data(), str_newline.size(), 1, stderr));
    fflush(stderr);
  }

  if ((g_logging_destination & LOG_TO_FILE) != 0) {
    // We can have multiple threads and/or processes, so try to prevent them
    // from clobbering each other's writes.
    // If the client app did not call InitLogging, and the lock has not
    // been created do it now. We do this on demand, but if two threads try
    // to do this at the same time, there will be a race condition to create
    // the lock. This is why InitLogging should be called from the main
    // thread at the beginning of execution.
#if defined(OS_POSIX) || defined(OS_FUCHSIA)
    LoggingLock::Init(LOCK_LOG_FILE, nullptr);
    LoggingLock logging_lock;
#endif
    if (InitializeLogFileHandle()) {
#if defined(OS_WIN)
      DWORD num_written;
      WriteFile(g_log_file,
                static_cast<const void*>(str_newline.c_str()),
                static_cast<DWORD>(str_newline.length()),
                &num_written,
                nullptr);
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
      ignore_result(fwrite(
          str_newline.data(), str_newline.size(), 1, g_log_file));
      fflush(g_log_file);
#else
#error Unsupported platform
#endif
    }
  }

  if (severity_ == LOG_FATAL) {
    // Write the log message to the global activity tracker, if running.
    base::debug::GlobalActivityTracker* tracker =
        base::debug::GlobalActivityTracker::Get();
    if (tracker)
      tracker->RecordLogMessage(str_newline);

    // Ensure the first characters of the string are on the stack so they
    // are contained in minidumps for diagnostic purposes. We place start
    // and end marker values at either end, so we can scan captured stacks
    // for the data easily.
    struct {
      uint32_t start_marker = 0xbedead01;
      char data[1024];
      uint32_t end_marker = 0x5050dead;
    } str_stack;
    base::strlcpy(str_stack.data, str_newline.data(),
                  base::size(str_stack.data));
    base::debug::Alias(&str_stack);

    if (!GetLogAssertHandlerStack().empty()) {
      LogAssertHandlerFunction log_assert_handler =
          GetLogAssertHandlerStack().top();

      if (log_assert_handler) {
        log_assert_handler.Run(
            file_, line_,
            base::StringPiece(str_newline.c_str() + message_start_,
                              stack_start - message_start_),
            base::StringPiece(str_newline.c_str() + stack_start));
      }
    } else {
      // Don't use the string with the newline, get a fresh version to send to
      // the debug message process. We also don't display assertions to the
      // user in release mode. The enduser can't do anything with this
      // information, and displaying message boxes when the application is
      // hosed can cause additional problems.
#ifndef NDEBUG
      if (!base::debug::BeingDebugged()) {
        // Displaying a dialog is unnecessary when debugging and can complicate
        // debugging.
        DisplayDebugMessageInDialog(stream_.str());
      }
#endif
      // Crash the process to generate a dump.
#if defined(OFFICIAL_BUILD) && defined(NDEBUG)
      IMMEDIATE_CRASH();
#else
      base::debug::BreakDebugger();
#endif
    }
  }
}

// writes the common header info to the stream
void LogMessage::Init(const char* file, int line) {
  base::StringPiece filename(file);
  size_t last_slash_pos = filename.find_last_of("\\/");
  if (last_slash_pos != base::StringPiece::npos)
    filename.remove_prefix(last_slash_pos + 1);

  // Stores the base name as the null-terminated suffix substring of |filename|.
  file_basename_ = filename.data();

  // TODO(darin): It might be nice if the columns were fixed width.

  stream_ <<  '[';
  if (g_log_prefix)
    stream_ << g_log_prefix << ':';
  if (g_log_process_id)
    stream_ << base::GetUniqueIdForProcess() << ':';
  if (g_log_thread_id)
    stream_ << base::PlatformThread::CurrentId() << ':';
  if (g_log_timestamp) {
#if defined(OS_WIN)
    SYSTEMTIME local_time;
    GetLocalTime(&local_time);
    stream_ << std::setfill('0')
            << std::setw(2) << local_time.wMonth
            << std::setw(2) << local_time.wDay
            << '/'
            << std::setw(2) << local_time.wHour
            << std::setw(2) << local_time.wMinute
            << std::setw(2) << local_time.wSecond
            << '.'
            << std::setw(3)
            << local_time.wMilliseconds
            << ':';
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
    timeval tv;
    gettimeofday(&tv, nullptr);
    time_t t = tv.tv_sec;
    struct tm local_time;
    localtime_r(&t, &local_time);
    struct tm* tm_time = &local_time;
    stream_ << std::setfill('0')
            << std::setw(2) << 1 + tm_time->tm_mon
            << std::setw(2) << tm_time->tm_mday
            << '/'
            << std::setw(2) << tm_time->tm_hour
            << std::setw(2) << tm_time->tm_min
            << std::setw(2) << tm_time->tm_sec
            << '.'
            << std::setw(6) << tv.tv_usec
            << ':';
#else
#error Unsupported platform
#endif
  }
  if (g_log_tickcount)
    stream_ << TickCount() << ':';
  if (severity_ >= 0)
    stream_ << log_severity_name(severity_);
  else
    stream_ << "VERBOSE" << -severity_;

  stream_ << ":" << filename << "(" << line << ")] ";

  message_start_ = stream_.str().length();
}

#if defined(OS_WIN)
// This has already been defined in the header, but defining it again as DWORD
// ensures that the type used in the header is equivalent to DWORD. If not,
// the redefinition is a compile error.
typedef DWORD SystemErrorCode;
#endif

SystemErrorCode GetLastSystemErrorCode() {
#if defined(OS_WIN)
  return ::GetLastError();
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
  return errno;
#endif
}

BASE_EXPORT std::string SystemErrorCodeToString(SystemErrorCode error_code) {
#if defined(OS_WIN)
  const int kErrorMessageBufferSize = 256;
  char msgbuf[kErrorMessageBufferSize];
  DWORD flags = FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS;
  DWORD len = FormatMessageA(flags, nullptr, error_code, 0, msgbuf,
                             base::size(msgbuf), nullptr);
  if (len) {
    // Messages returned by system end with line breaks.
    return base::CollapseWhitespaceASCII(msgbuf, true) +
           base::StringPrintf(" (0x%lX)", error_code);
  }
  return base::StringPrintf("Error (0x%lX) while retrieving error. (0x%lX)",
                            GetLastError(), error_code);
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
  return base::safe_strerror(error_code) +
         base::StringPrintf(" (%d)", error_code);
#endif  // defined(OS_WIN)
}


#if defined(OS_WIN)
Win32ErrorLogMessage::Win32ErrorLogMessage(const char* file,
                                           int line,
                                           LogSeverity severity,
                                           SystemErrorCode err)
    : err_(err),
      log_message_(file, line, severity) {
}

Win32ErrorLogMessage::~Win32ErrorLogMessage() {
  stream() << ": " << SystemErrorCodeToString(err_);
  // We're about to crash (CHECK). Put |err_| on the stack (by placing it in a
  // field) and use Alias in hopes that it makes it into crash dumps.
  DWORD last_error = err_;
  base::debug::Alias(&last_error);
}
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
ErrnoLogMessage::ErrnoLogMessage(const char* file,
                                 int line,
                                 LogSeverity severity,
                                 SystemErrorCode err)
    : err_(err),
      log_message_(file, line, severity) {
}

ErrnoLogMessage::~ErrnoLogMessage() {
  stream() << ": " << SystemErrorCodeToString(err_);
  // We're about to crash (CHECK). Put |err_| on the stack (by placing it in a
  // field) and use Alias in hopes that it makes it into crash dumps.
  int last_error = err_;
  base::debug::Alias(&last_error);
}
#endif  // defined(OS_WIN)

void CloseLogFile() {
#if defined(OS_POSIX) || defined(OS_FUCHSIA)
  LoggingLock logging_lock;
#endif
  CloseLogFileUnlocked();
}

#if defined(OS_CHROMEOS)
FILE* DuplicateLogFILE() {
  if ((g_logging_destination & LOG_TO_FILE) == 0 || !InitializeLogFileHandle())
    return nullptr;

  int log_fd = fileno(g_log_file);
  if (log_fd == -1)
    return nullptr;
  base::ScopedFD dup_fd(dup(log_fd));
  if (dup_fd == -1)
    return nullptr;
  FILE* duplicate = fdopen(dup_fd.get(), "a");
  if (!duplicate)
    return nullptr;
  ignore_result(dup_fd.release());
  return duplicate;
}
#endif

void RawLog(int level, const char* message) {
  if (level >= g_min_log_level && message) {
    size_t bytes_written = 0;
    const size_t message_len = strlen(message);
    int rv;
    while (bytes_written < message_len) {
      rv = HANDLE_EINTR(
          write(STDERR_FILENO, message + bytes_written,
                message_len - bytes_written));
      if (rv < 0) {
        // Give up, nothing we can do now.
        break;
      }
      bytes_written += rv;
    }

    if (message_len > 0 && message[message_len - 1] != '\n') {
      do {
        rv = HANDLE_EINTR(write(STDERR_FILENO, "\n", 1));
        if (rv < 0) {
          // Give up, nothing we can do now.
          break;
        }
      } while (rv != 1);
    }
  }

  if (level == LOG_FATAL)
    base::debug::BreakDebugger();
}

// This was defined at the beginning of this file.
#undef write

#if defined(OS_WIN)
bool IsLoggingToFileEnabled() {
  return g_logging_destination & LOG_TO_FILE;
}

std::wstring GetLogFileFullPath() {
  if (g_log_file_name)
    return *g_log_file_name;
  return std::wstring();
}
#endif

BASE_EXPORT void LogErrorNotReached(const char* file, int line) {
  LogMessage(file, line, LOG_ERROR).stream()
      << "NOTREACHED() hit.";
}

}  // namespace logging

std::ostream& std::operator<<(std::ostream& out, const wchar_t* wstr) {
  return out << (wstr ? base::WideToUTF8(wstr) : std::string());
}
