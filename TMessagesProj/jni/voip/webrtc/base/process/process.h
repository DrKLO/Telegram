// Copyright 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_PROCESS_PROCESS_H_
#define BASE_PROCESS_PROCESS_H_

#include "base/base_export.h"
#include "base/macros.h"
#include "base/process/process_handle.h"
#include "base/time/time.h"
#include "build/build_config.h"

#if defined(OS_WIN)
#include "base/win/scoped_handle.h"
#endif

#if defined(OS_FUCHSIA)
#include <lib/zx/process.h>
#endif

#if defined(OS_MACOSX)
#include "base/feature_list.h"
#include "base/process/port_provider_mac.h"
#endif

namespace base {

#if defined(OS_MACOSX)
extern const Feature kMacAllowBackgroundingProcesses;
#endif

// Provides a move-only encapsulation of a process.
//
// This object is not tied to the lifetime of the underlying process: the
// process may be killed and this object may still around, and it will still
// claim to be valid. The actual behavior in that case is OS dependent like so:
//
// Windows: The underlying ProcessHandle will be valid after the process dies
// and can be used to gather some information about that process, but most
// methods will obviously fail.
//
// POSIX: The underlying ProcessHandle is not guaranteed to remain valid after
// the process dies, and it may be reused by the system, which means that it may
// end up pointing to the wrong process.
class BASE_EXPORT Process {
 public:
  // On Windows, this takes ownership of |handle|. On POSIX, this does not take
  // ownership of |handle|.
  explicit Process(ProcessHandle handle = kNullProcessHandle);

  Process(Process&& other);

  // The destructor does not terminate the process.
  ~Process();

  Process& operator=(Process&& other);

  // Returns an object for the current process.
  static Process Current();

  // Returns a Process for the given |pid|.
  static Process Open(ProcessId pid);

  // Returns a Process for the given |pid|. On Windows the handle is opened
  // with more access rights and must only be used by trusted code (can read the
  // address space and duplicate handles).
  static Process OpenWithExtraPrivileges(ProcessId pid);

#if defined(OS_WIN)
  // Returns a Process for the given |pid|, using some |desired_access|.
  // See ::OpenProcess documentation for valid |desired_access|.
  static Process OpenWithAccess(ProcessId pid, DWORD desired_access);
#endif

  // Creates an object from a |handle| owned by someone else.
  // Don't use this for new code. It is only intended to ease the migration to
  // a strict ownership model.
  // TODO(rvargas) crbug.com/417532: Remove this code.
  static Process DeprecatedGetProcessFromHandle(ProcessHandle handle);

  // Returns true if processes can be backgrounded.
  static bool CanBackgroundProcesses();

  // Terminates the current process immediately with |exit_code|.
  [[noreturn]] static void TerminateCurrentProcessImmediately(int exit_code);

  // Returns true if this objects represents a valid process.
  bool IsValid() const;

  // Returns a handle for this process. There is no guarantee about when that
  // handle becomes invalid because this object retains ownership.
  ProcessHandle Handle() const;

  // Returns a second object that represents this process.
  Process Duplicate() const;

  // Get the PID for this process.
  ProcessId Pid() const;

#if !defined(OS_ANDROID)
  // Get the creation time for this process. Since the Pid can be reused after a
  // process dies, it is useful to use both the Pid and the creation time to
  // uniquely identify a process.
  //
  // Not available on Android because /proc/stat/ cannot be accessed on O+.
  // https://issuetracker.google.com/issues/37140047
  Time CreationTime() const;
#endif  // !defined(OS_ANDROID)

  // Returns true if this process is the current process.
  bool is_current() const;

  // Close the process handle. This will not terminate the process.
  void Close();

  // Returns true if this process is still running. This is only safe on Windows
  // (and maybe Fuchsia?), because the ProcessHandle will keep the zombie
  // process information available until itself has been released. But on Posix,
  // the OS may reuse the ProcessId.
#if defined(OS_WIN)
  bool IsRunning() const {
    return !WaitForExitWithTimeout(base::TimeDelta(), nullptr);
  }
#endif

  // Terminates the process with extreme prejudice. The given |exit_code| will
  // be the exit code of the process. If |wait| is true, this method will wait
  // for up to one minute for the process to actually terminate.
  // Returns true if the process terminates within the allowed time.
  // NOTE: On POSIX |exit_code| is ignored.
  bool Terminate(int exit_code, bool wait) const;

#if defined(OS_WIN)
  enum class WaitExitStatus {
    PROCESS_EXITED,
    STOP_EVENT_SIGNALED,
    FAILED,
  };

  // Waits for the process to exit, or the specified |stop_event_handle| to be
  // set. Returns value indicating which event was set. The given |exit_code|
  // will be the exit code of the process.
  WaitExitStatus WaitForExitOrEvent(
      const base::win::ScopedHandle& stop_event_handle,
      int* exit_code) const;
#endif  // OS_WIN

  // Waits for the process to exit. Returns true on success.
  // On POSIX, if the process has been signaled then |exit_code| is set to -1.
  // On Linux this must be a child process, however on Mac and Windows it can be
  // any process.
  // NOTE: |exit_code| is optional, nullptr can be passed if the exit code is
  // not required.
  bool WaitForExit(int* exit_code) const;

  // Same as WaitForExit() but only waits for up to |timeout|.
  // NOTE: |exit_code| is optional, nullptr can be passed if the exit code
  // is not required.
  bool WaitForExitWithTimeout(TimeDelta timeout, int* exit_code) const;

  // Indicates that the process has exited with the specified |exit_code|.
  // This should be called if process exit is observed outside of this class.
  // (i.e. Not because Terminate or WaitForExit, above, was called.)
  // Note that nothing prevents this being called multiple times for a dead
  // process though that should be avoided.
  void Exited(int exit_code) const;

#if defined(OS_MACOSX)
  // The Mac needs a Mach port in order to manipulate a process's priority,
  // and there's no good way to get that from base given the pid. These Mac
  // variants of the IsProcessBackgrounded and SetProcessBackgrounded API take
  // a port provider for this reason. See crbug.com/460102
  //
  // A process is backgrounded when its task priority is
  // |TASK_BACKGROUND_APPLICATION|.
  //
  // Returns true if the port_provider can locate a task port for the process
  // and it is backgrounded. If port_provider is null, returns false.
  bool IsProcessBackgrounded(PortProvider* port_provider) const;

  // Set the process as backgrounded. If value is
  // true, the priority of the associated task will be set to
  // TASK_BACKGROUND_APPLICATION. If value is false, the
  // priority of the process will be set to TASK_FOREGROUND_APPLICATION.
  //
  // Returns true if the priority was changed, false otherwise. If
  // |port_provider| is null, this is a no-op and it returns false.
  bool SetProcessBackgrounded(PortProvider* port_provider, bool value);
#else
  // A process is backgrounded when it's priority is lower than normal.
  // Return true if this process is backgrounded, false otherwise.
  bool IsProcessBackgrounded() const;

  // Set a process as backgrounded. If value is true, the priority of the
  // process will be lowered. If value is false, the priority of the process
  // will be made "normal" - equivalent to default process priority.
  // Returns true if the priority was changed, false otherwise.
  bool SetProcessBackgrounded(bool value);
#endif  // defined(OS_MACOSX)
  // Returns an integer representing the priority of a process. The meaning
  // of this value is OS dependent.
  int GetPriority() const;

#if defined(OS_CHROMEOS)
  // Get the PID in its PID namespace.
  // If the process is not in a PID namespace or /proc/<pid>/status does not
  // report NSpid, kNullProcessId is returned.
  ProcessId GetPidInNamespace() const;
#endif

 private:
#if defined(OS_WIN)
  win::ScopedHandle process_;
#elif defined(OS_FUCHSIA)
  zx::process process_;
#else
  ProcessHandle process_;
#endif

#if defined(OS_WIN) || defined(OS_FUCHSIA)
  bool is_current_process_;
#endif

  DISALLOW_COPY_AND_ASSIGN(Process);
};

#if defined(OS_CHROMEOS)
// Exposed for testing.
// Given the contents of the /proc/<pid>/cgroup file, determine whether the
// process is backgrounded or not.
BASE_EXPORT bool IsProcessBackgroundedCGroup(
    const StringPiece& cgroup_contents);
#endif  // defined(OS_CHROMEOS)

}  // namespace base

#endif  // BASE_PROCESS_PROCESS_H_
