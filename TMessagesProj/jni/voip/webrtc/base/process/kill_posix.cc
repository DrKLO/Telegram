// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/process/kill.h"

#include <errno.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#include "base/debug/activity_tracker.h"
#include "base/files/file_util.h"
#include "base/logging.h"
#include "base/macros.h"
#include "base/posix/eintr_wrapper.h"
#include "base/process/process_iterator.h"
#include "base/task/post_task.h"
#include "base/threading/platform_thread.h"
#include "build/build_config.h"

namespace base {

namespace {

TerminationStatus GetTerminationStatusImpl(ProcessHandle handle,
                                           bool can_block,
                                           int* exit_code) {
  DCHECK(exit_code);

  int status = 0;
  const pid_t result = HANDLE_EINTR(waitpid(handle, &status,
                                            can_block ? 0 : WNOHANG));
  if (result == -1) {
    DPLOG(ERROR) << "waitpid(" << handle << ")";
    *exit_code = 0;
    return TERMINATION_STATUS_NORMAL_TERMINATION;
  }
  if (result == 0) {
    // the child hasn't exited yet.
    *exit_code = 0;
    return TERMINATION_STATUS_STILL_RUNNING;
  }

  *exit_code = status;

  if (WIFSIGNALED(status)) {
    switch (WTERMSIG(status)) {
      case SIGABRT:
      case SIGBUS:
      case SIGFPE:
      case SIGILL:
      case SIGSEGV:
      case SIGTRAP:
      case SIGSYS:
        return TERMINATION_STATUS_PROCESS_CRASHED;
      case SIGKILL:
#if defined(OS_CHROMEOS)
        // On ChromeOS, only way a process gets kill by SIGKILL
        // is by oom-killer.
        return TERMINATION_STATUS_PROCESS_WAS_KILLED_BY_OOM;
#endif
      case SIGINT:
      case SIGTERM:
        return TERMINATION_STATUS_PROCESS_WAS_KILLED;
      default:
        break;
    }
  }

  if (WIFEXITED(status) && WEXITSTATUS(status) != 0)
    return TERMINATION_STATUS_ABNORMAL_TERMINATION;

  return TERMINATION_STATUS_NORMAL_TERMINATION;
}

}  // namespace

#if !defined(OS_NACL_NONSFI)
bool KillProcessGroup(ProcessHandle process_group_id) {
  bool result = kill(-1 * process_group_id, SIGKILL) == 0;
  if (!result)
    DPLOG(ERROR) << "Unable to terminate process group " << process_group_id;
  return result;
}
#endif  // !defined(OS_NACL_NONSFI)

TerminationStatus GetTerminationStatus(ProcessHandle handle, int* exit_code) {
  return GetTerminationStatusImpl(handle, false /* can_block */, exit_code);
}

TerminationStatus GetKnownDeadTerminationStatus(ProcessHandle handle,
                                                int* exit_code) {
  bool result = kill(handle, SIGKILL) == 0;

  if (!result)
    DPLOG(ERROR) << "Unable to terminate process " << handle;

  return GetTerminationStatusImpl(handle, true /* can_block */, exit_code);
}

#if !defined(OS_NACL_NONSFI)
bool WaitForProcessesToExit(const FilePath::StringType& executable_name,
                            TimeDelta wait,
                            const ProcessFilter* filter) {
  bool result = false;

  // TODO(port): This is inefficient, but works if there are multiple procs.
  // TODO(port): use waitpid to avoid leaving zombies around

  TimeTicks end_time = TimeTicks::Now() + wait;
  do {
    NamedProcessIterator iter(executable_name, filter);
    if (!iter.NextProcessEntry()) {
      result = true;
      break;
    }
    PlatformThread::Sleep(TimeDelta::FromMilliseconds(100));
  } while ((end_time - TimeTicks::Now()) > TimeDelta());

  return result;
}

bool CleanupProcesses(const FilePath::StringType& executable_name,
                      TimeDelta wait,
                      int exit_code,
                      const ProcessFilter* filter) {
  bool exited_cleanly = WaitForProcessesToExit(executable_name, wait, filter);
  if (!exited_cleanly)
    KillProcesses(executable_name, exit_code, filter);
  return exited_cleanly;
}

#if !defined(OS_MACOSX)

namespace {

class BackgroundReaper : public PlatformThread::Delegate {
 public:
  BackgroundReaper(base::Process child_process, const TimeDelta& wait_time)
      : child_process_(std::move(child_process)), wait_time_(wait_time) {}

  void ThreadMain() override {
    if (!wait_time_.is_zero()) {
      child_process_.WaitForExitWithTimeout(wait_time_, nullptr);
      kill(child_process_.Handle(), SIGKILL);
    }
    child_process_.WaitForExit(nullptr);
    delete this;
  }

 private:
  Process child_process_;
  const TimeDelta wait_time_;
  DISALLOW_COPY_AND_ASSIGN(BackgroundReaper);
};

}  // namespace

void EnsureProcessTerminated(Process process) {
  DCHECK(!process.is_current());

  if (process.WaitForExitWithTimeout(TimeDelta(), nullptr))
    return;

  PlatformThread::CreateNonJoinable(
      0, new BackgroundReaper(std::move(process), TimeDelta::FromSeconds(2)));
}

#if defined(OS_LINUX)
void EnsureProcessGetsReaped(Process process) {
  DCHECK(!process.is_current());

  // If the child is already dead, then there's nothing to do.
  if (process.WaitForExitWithTimeout(TimeDelta(), nullptr))
    return;

  PlatformThread::CreateNonJoinable(
      0, new BackgroundReaper(std::move(process), TimeDelta()));
}
#endif  // defined(OS_LINUX)

#endif  // !defined(OS_MACOSX)
#endif  // !defined(OS_NACL_NONSFI)

}  // namespace base
