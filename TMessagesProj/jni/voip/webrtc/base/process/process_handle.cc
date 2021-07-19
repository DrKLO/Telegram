// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/process/process_handle.h"

#include <stdint.h>

#include "base/logging.h"
#include "build/build_config.h"

namespace base {

namespace {
ProcessId g_pid_outside_of_namespace = kNullProcessId;
}  // namespace

std::ostream& operator<<(std::ostream& os, const UniqueProcId& obj) {
  os << obj.GetUnsafeValue();
  return os;
}

UniqueProcId GetUniqueIdForProcess() {
  // Used for logging. Must not use LogMessage or any of the macros that call
  // into it.
  return (g_pid_outside_of_namespace != kNullProcessId)
             ? UniqueProcId(g_pid_outside_of_namespace)
             : UniqueProcId(GetCurrentProcId());
}

#if defined(OS_LINUX) || defined(OS_AIX)

void InitUniqueIdForProcessInPidNamespace(ProcessId pid_outside_of_namespace) {
  DCHECK(pid_outside_of_namespace != kNullProcessId);
  g_pid_outside_of_namespace = pid_outside_of_namespace;
}

#endif

}  // namespace base
