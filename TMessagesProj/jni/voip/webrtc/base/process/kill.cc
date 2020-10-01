// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/process/kill.h"

#include "base/bind.h"
#include "base/process/process_iterator.h"
#include "base/task/post_task.h"
#include "base/task/thread_pool.h"
#include "base/time/time.h"

namespace base {

bool KillProcesses(const FilePath::StringType& executable_name,
                   int exit_code,
                   const ProcessFilter* filter) {
  bool result = true;
  NamedProcessIterator iter(executable_name, filter);
  while (const ProcessEntry* entry = iter.NextProcessEntry()) {
    Process process = Process::Open(entry->pid());
    // Sometimes process open fails. This would cause a DCHECK in
    // process.Terminate(). Maybe the process has killed itself between the
    // time the process list was enumerated and the time we try to open the
    // process?
    if (!process.IsValid()) {
      result = false;
      continue;
    }
    result &= process.Terminate(exit_code, true);
  }
  return result;
}

#if defined(OS_WIN) || defined(OS_FUCHSIA)
// Common implementation for platforms under which |process| is a handle to
// the process, rather than an identifier that must be "reaped".
void EnsureProcessTerminated(Process process) {
  DCHECK(!process.is_current());

  if (process.WaitForExitWithTimeout(TimeDelta(), nullptr))
    return;

  ThreadPool::PostDelayedTask(
      FROM_HERE,
      {TaskPriority::BEST_EFFORT, TaskShutdownBehavior::CONTINUE_ON_SHUTDOWN},
      BindOnce(
          [](Process process) {
            if (process.WaitForExitWithTimeout(TimeDelta(), nullptr))
              return;
#if defined(OS_WIN)
            process.Terminate(win::kProcessKilledExitCode, false);
#else
            process.Terminate(-1, false);
#endif
          },
          std::move(process)),
      TimeDelta::FromSeconds(2));
}
#endif  // defined(OS_WIN) || defined(OS_FUCHSIA)

}  // namespace base
