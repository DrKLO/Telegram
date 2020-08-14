// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/process/launch.h"

#include <fcntl.h>
#include <io.h>
#include <shellapi.h>
#include <windows.h>
#include <userenv.h>
#include <psapi.h>

#include <ios>
#include <limits>

#include "base/bind.h"
#include "base/bind_helpers.h"
#include "base/debug/activity_tracker.h"
#include "base/debug/stack_trace.h"
#include "base/logging.h"
#include "base/metrics/histogram.h"
#include "base/process/environment_internal.h"
#include "base/process/kill.h"
#include "base/strings/string_util.h"
#include "base/strings/utf_string_conversions.h"
#include "base/system/sys_info.h"
#include "base/threading/scoped_thread_priority.h"
#include "base/win/scoped_handle.h"
#include "base/win/scoped_process_information.h"
#include "base/win/startup_information.h"
#include "base/win/windows_version.h"

namespace base {

namespace {

bool GetAppOutputInternal(CommandLine::StringPieceType cl,
                          bool include_stderr,
                          std::string* output,
                          int* exit_code) {
  HANDLE out_read = nullptr;
  HANDLE out_write = nullptr;

  SECURITY_ATTRIBUTES sa_attr;
  // Set the bInheritHandle flag so pipe handles are inherited.
  sa_attr.nLength = sizeof(SECURITY_ATTRIBUTES);
  sa_attr.bInheritHandle = TRUE;
  sa_attr.lpSecurityDescriptor = nullptr;

  // Create the pipe for the child process's STDOUT.
  if (!CreatePipe(&out_read, &out_write, &sa_attr, 0)) {
    NOTREACHED() << "Failed to create pipe";
    return false;
  }

  // Ensure we don't leak the handles.
  win::ScopedHandle scoped_out_read(out_read);
  win::ScopedHandle scoped_out_write(out_write);

  // Ensure the read handles to the pipes are not inherited.
  if (!SetHandleInformation(out_read, HANDLE_FLAG_INHERIT, 0)) {
    NOTREACHED() << "Failed to disabled pipe inheritance";
    return false;
  }

  FilePath::StringType writable_command_line_string(cl);

  STARTUPINFO start_info = {};

  start_info.cb = sizeof(STARTUPINFO);
  start_info.hStdOutput = out_write;
  // Keep the normal stdin.
  start_info.hStdInput = GetStdHandle(STD_INPUT_HANDLE);
  if (include_stderr) {
    start_info.hStdError = out_write;
  } else {
    start_info.hStdError = GetStdHandle(STD_ERROR_HANDLE);
  }
  start_info.dwFlags |= STARTF_USESTDHANDLES;

  // Create the child process.
  PROCESS_INFORMATION temp_process_info = {};
  if (!CreateProcess(nullptr, data(writable_command_line_string), nullptr,
                     nullptr,
                     TRUE,  // Handles are inherited.
                     0, nullptr, nullptr, &start_info, &temp_process_info)) {
    NOTREACHED() << "Failed to start process";
    return false;
  }

  win::ScopedProcessInformation proc_info(temp_process_info);
  debug::GlobalActivityTracker* tracker = debug::GlobalActivityTracker::Get();
  if (tracker)
    tracker->RecordProcessLaunch(proc_info.process_id(), cl.as_string());

  // Close our writing end of pipe now. Otherwise later read would not be able
  // to detect end of child's output.
  scoped_out_write.Close();

  // Read output from the child process's pipe for STDOUT
  const int kBufferSize = 1024;
  char buffer[kBufferSize];

  for (;;) {
    DWORD bytes_read = 0;
    BOOL success =
        ::ReadFile(out_read, buffer, kBufferSize, &bytes_read, nullptr);
    if (!success || bytes_read == 0)
      break;
    output->append(buffer, bytes_read);
  }

  // Let's wait for the process to finish.
  WaitForSingleObject(proc_info.process_handle(), INFINITE);

  TerminationStatus status =
      GetTerminationStatus(proc_info.process_handle(), exit_code);
  debug::GlobalActivityTracker::RecordProcessExitIfEnabled(
      proc_info.process_id(), *exit_code);
  return status != TERMINATION_STATUS_PROCESS_CRASHED &&
         status != TERMINATION_STATUS_ABNORMAL_TERMINATION;
}

}  // namespace

void RouteStdioToConsole(bool create_console_if_not_found) {
  // Don't change anything if stdout or stderr already point to a
  // valid stream.
  //
  // If we are running under Buildbot or under Cygwin's default
  // terminal (mintty), stderr and stderr will be pipe handles.  In
  // that case, we don't want to open CONOUT$, because its output
  // likely does not go anywhere.
  //
  // We don't use GetStdHandle() to check stdout/stderr here because
  // it can return dangling IDs of handles that were never inherited
  // by this process.  These IDs could have been reused by the time
  // this function is called.  The CRT checks the validity of
  // stdout/stderr on startup (before the handle IDs can be reused).
  // _fileno(stdout) will return -2 (_NO_CONSOLE_FILENO) if stdout was
  // invalid.
  if (_fileno(stdout) >= 0 || _fileno(stderr) >= 0) {
    // _fileno was broken for SUBSYSTEM:WINDOWS from VS2010 to VS2012/2013.
    // http://crbug.com/358267. Confirm that the underlying HANDLE is valid
    // before aborting.

    intptr_t stdout_handle = _get_osfhandle(_fileno(stdout));
    intptr_t stderr_handle = _get_osfhandle(_fileno(stderr));
    if (stdout_handle >= 0 || stderr_handle >= 0)
      return;
  }

  if (!AttachConsole(ATTACH_PARENT_PROCESS)) {
    unsigned int result = GetLastError();
    // Was probably already attached.
    if (result == ERROR_ACCESS_DENIED)
      return;
    // Don't bother creating a new console for each child process if the
    // parent process is invalid (eg: crashed).
    if (result == ERROR_GEN_FAILURE)
      return;
    if (create_console_if_not_found) {
      // Make a new console if attaching to parent fails with any other error.
      // It should be ERROR_INVALID_HANDLE at this point, which means the
      // browser was likely not started from a console.
      AllocConsole();
    } else {
      return;
    }
  }

  // Arbitrary byte count to use when buffering output lines.  More
  // means potential waste, less means more risk of interleaved
  // log-lines in output.
  enum { kOutputBufferSize = 64 * 1024 };

  if (freopen("CONOUT$", "w", stdout)) {
    setvbuf(stdout, nullptr, _IOLBF, kOutputBufferSize);
    // Overwrite FD 1 for the benefit of any code that uses this FD
    // directly.  This is safe because the CRT allocates FDs 0, 1 and
    // 2 at startup even if they don't have valid underlying Windows
    // handles.  This means we won't be overwriting an FD created by
    // _open() after startup.
    _dup2(_fileno(stdout), 1);
  }
  if (freopen("CONOUT$", "w", stderr)) {
    setvbuf(stderr, nullptr, _IOLBF, kOutputBufferSize);
    _dup2(_fileno(stderr), 2);
  }

  // Fix all cout, wcout, cin, wcin, cerr, wcerr, clog and wclog.
  std::ios::sync_with_stdio();
}

Process LaunchProcess(const CommandLine& cmdline,
                      const LaunchOptions& options) {
  return LaunchProcess(cmdline.GetCommandLineString(), options);
}

Process LaunchProcess(const CommandLine::StringType& cmdline,
                      const LaunchOptions& options) {
  // Mitigate the issues caused by loading DLLs on a background thread
  // (http://crbug/973868).
  SCOPED_MAY_LOAD_LIBRARY_AT_BACKGROUND_PRIORITY();

  win::StartupInformation startup_info_wrapper;
  STARTUPINFO* startup_info = startup_info_wrapper.startup_info();

  bool inherit_handles = options.inherit_mode == LaunchOptions::Inherit::kAll;
  DWORD flags = 0;
  if (!options.handles_to_inherit.empty()) {
    DCHECK_EQ(options.inherit_mode, LaunchOptions::Inherit::kSpecific);

    if (options.handles_to_inherit.size() >
        std::numeric_limits<DWORD>::max() / sizeof(HANDLE)) {
      DLOG(ERROR) << "Too many handles to inherit.";
      return Process();
    }

    // Ensure the handles can be inherited.
    for (HANDLE handle : options.handles_to_inherit) {
      BOOL result = SetHandleInformation(handle, HANDLE_FLAG_INHERIT,
                                         HANDLE_FLAG_INHERIT);
      PCHECK(result);
    }

    if (!startup_info_wrapper.InitializeProcThreadAttributeList(1)) {
      DPLOG(ERROR);
      return Process();
    }

    if (!startup_info_wrapper.UpdateProcThreadAttribute(
            PROC_THREAD_ATTRIBUTE_HANDLE_LIST,
            const_cast<HANDLE*>(&options.handles_to_inherit[0]),
            static_cast<DWORD>(options.handles_to_inherit.size() *
                               sizeof(HANDLE)))) {
      DPLOG(ERROR);
      return Process();
    }

    inherit_handles = true;
    flags |= EXTENDED_STARTUPINFO_PRESENT;
  }

  if (options.feedback_cursor_off)
    startup_info->dwFlags |= STARTF_FORCEOFFFEEDBACK;
  if (options.empty_desktop_name)
    startup_info->lpDesktop = const_cast<wchar_t*>(L"");
  startup_info->dwFlags |= STARTF_USESHOWWINDOW;
  startup_info->wShowWindow = options.start_hidden ? SW_HIDE : SW_SHOWNORMAL;

  if (options.stdin_handle || options.stdout_handle || options.stderr_handle) {
    DCHECK(inherit_handles);
    DCHECK(options.stdin_handle);
    DCHECK(options.stdout_handle);
    DCHECK(options.stderr_handle);
    startup_info->dwFlags |= STARTF_USESTDHANDLES;
    startup_info->hStdInput = options.stdin_handle;
    startup_info->hStdOutput = options.stdout_handle;
    startup_info->hStdError = options.stderr_handle;
  }

  if (options.job_handle) {
    // If this code is run under a debugger, the launched process is
    // automatically associated with a job object created by the debugger.
    // The CREATE_BREAKAWAY_FROM_JOB flag is used to prevent this on Windows
    // releases that do not support nested jobs.
    if (win::GetVersion() < win::Version::WIN8)
      flags |= CREATE_BREAKAWAY_FROM_JOB;
  }

  if (options.force_breakaway_from_job_)
    flags |= CREATE_BREAKAWAY_FROM_JOB;

  PROCESS_INFORMATION temp_process_info = {};

  LPCTSTR current_directory = options.current_directory.empty()
                                  ? nullptr
                                  : options.current_directory.value().c_str();

  auto writable_cmdline(cmdline);
  DCHECK(!(flags & CREATE_SUSPENDED))
      << "Creating a suspended process can lead to hung processes if the "
      << "launching process is killed before it assigns the process to the"
      << "job. https://crbug.com/820996";
  if (options.as_user) {
    flags |= CREATE_UNICODE_ENVIRONMENT;
    void* environment_block = nullptr;

    if (!CreateEnvironmentBlock(&environment_block, options.as_user, FALSE)) {
      DPLOG(ERROR);
      return Process();
    }

    // Environment options are not implemented for use with |as_user|.
    DCHECK(!options.clear_environment);
    DCHECK(options.environment.empty());

    BOOL launched = CreateProcessAsUser(
        options.as_user, nullptr, data(writable_cmdline), nullptr, nullptr,
        inherit_handles, flags, environment_block, current_directory,
        startup_info, &temp_process_info);
    DestroyEnvironmentBlock(environment_block);
    if (!launched) {
      DPLOG(ERROR) << "Command line:" << std::endl
                   << WideToUTF8(cmdline) << std::endl;
      return Process();
    }
  } else {
    wchar_t* new_environment = nullptr;
    std::wstring env_storage;
    if (options.clear_environment || !options.environment.empty()) {
      if (options.clear_environment) {
        static const wchar_t kEmptyEnvironment[] = {0};
        env_storage =
            internal::AlterEnvironment(kEmptyEnvironment, options.environment);
      } else {
        wchar_t* old_environment = GetEnvironmentStrings();
        if (!old_environment) {
          DPLOG(ERROR);
          return Process();
        }
        env_storage =
            internal::AlterEnvironment(old_environment, options.environment);
        FreeEnvironmentStrings(old_environment);
      }
      new_environment = data(env_storage);
      flags |= CREATE_UNICODE_ENVIRONMENT;
    }

    if (!CreateProcess(nullptr, data(writable_cmdline), nullptr, nullptr,
                       inherit_handles, flags, new_environment,
                       current_directory, startup_info, &temp_process_info)) {
      DPLOG(ERROR) << "Command line:" << std::endl << cmdline << std::endl;
      return Process();
    }
  }
  win::ScopedProcessInformation process_info(temp_process_info);

  if (options.job_handle &&
      !AssignProcessToJobObject(options.job_handle,
                                process_info.process_handle())) {
    DPLOG(ERROR) << "Could not AssignProcessToObject";
    Process scoped_process(process_info.TakeProcessHandle());
    scoped_process.Terminate(win::kProcessKilledExitCode, true);
    return Process();
  }

  if (options.grant_foreground_privilege &&
      !AllowSetForegroundWindow(GetProcId(process_info.process_handle()))) {
    DPLOG(ERROR) << "Failed to grant foreground privilege to launched process";
  }

  if (options.wait)
    WaitForSingleObject(process_info.process_handle(), INFINITE);

  debug::GlobalActivityTracker::RecordProcessLaunchIfEnabled(
      process_info.process_id(), cmdline);
  return Process(process_info.TakeProcessHandle());
}

Process LaunchElevatedProcess(const CommandLine& cmdline,
                              const LaunchOptions& options) {
  const FilePath::StringType file = cmdline.GetProgram().value();
  const CommandLine::StringType arguments = cmdline.GetArgumentsString();

  SHELLEXECUTEINFO shex_info = {};
  shex_info.cbSize = sizeof(shex_info);
  shex_info.fMask = SEE_MASK_NOCLOSEPROCESS;
  shex_info.hwnd = GetActiveWindow();
  shex_info.lpVerb = L"runas";
  shex_info.lpFile = file.c_str();
  shex_info.lpParameters = arguments.c_str();
  shex_info.lpDirectory = nullptr;
  shex_info.nShow = options.start_hidden ? SW_HIDE : SW_SHOWNORMAL;
  shex_info.hInstApp = nullptr;

  if (!ShellExecuteEx(&shex_info)) {
    DPLOG(ERROR);
    return Process();
  }

  if (options.wait)
    WaitForSingleObject(shex_info.hProcess, INFINITE);

  debug::GlobalActivityTracker::RecordProcessLaunchIfEnabled(
      GetProcessId(shex_info.hProcess), file, arguments);
  return Process(shex_info.hProcess);
}

bool SetJobObjectLimitFlags(HANDLE job_object, DWORD limit_flags) {
  JOBOBJECT_EXTENDED_LIMIT_INFORMATION limit_info = {};
  limit_info.BasicLimitInformation.LimitFlags = limit_flags;
  return 0 != SetInformationJobObject(
      job_object,
      JobObjectExtendedLimitInformation,
      &limit_info,
      sizeof(limit_info));
}

bool GetAppOutput(const CommandLine& cl, std::string* output) {
  return GetAppOutput(cl.GetCommandLineString(), output);
}

bool GetAppOutputAndError(const CommandLine& cl, std::string* output) {
  int exit_code;
  return GetAppOutputInternal(
      cl.GetCommandLineString(), true, output, &exit_code);
}

bool GetAppOutputWithExitCode(const CommandLine& cl,
                              std::string* output,
                              int* exit_code) {
  return GetAppOutputInternal(
      cl.GetCommandLineString(), false, output, exit_code);
}

bool GetAppOutput(CommandLine::StringPieceType cl, std::string* output) {
  int exit_code;
  return GetAppOutputInternal(cl, false, output, &exit_code);
}

void RaiseProcessToHighPriority() {
  SetPriorityClass(GetCurrentProcess(), HIGH_PRIORITY_CLASS);
}

}  // namespace base
