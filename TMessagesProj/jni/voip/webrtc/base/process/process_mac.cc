// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/process/process.h"

#include <mach/mach.h>
#include <stddef.h>
#include <sys/sysctl.h>
#include <sys/time.h>
#include <unistd.h>

#include <memory>

#include "base/feature_list.h"
#include "base/mac/mach_logging.h"
#include "base/memory/free_deleter.h"
#include "base/stl_util.h"

namespace base {

// Enables backgrounding hidden renderers on Mac.
const Feature kMacAllowBackgroundingProcesses{"MacAllowBackgroundingProcesses",
                                              FEATURE_DISABLED_BY_DEFAULT};

Time Process::CreationTime() const {
  int mib[] = {CTL_KERN, KERN_PROC, KERN_PROC_PID, Pid()};
  size_t len = 0;
  if (sysctl(mib, size(mib), NULL, &len, NULL, 0) < 0)
    return Time();

  std::unique_ptr<struct kinfo_proc, base::FreeDeleter> proc(
      static_cast<struct kinfo_proc*>(malloc(len)));
  if (sysctl(mib, size(mib), proc.get(), &len, NULL, 0) < 0)
    return Time();
  return Time::FromTimeVal(proc->kp_proc.p_un.__p_starttime);
}

bool Process::CanBackgroundProcesses() {
  return FeatureList::IsEnabled(kMacAllowBackgroundingProcesses);
}

bool Process::IsProcessBackgrounded(PortProvider* port_provider) const {
  DCHECK(IsValid());
  if (port_provider == nullptr || !CanBackgroundProcesses())
    return false;

  mach_port_t task_port = port_provider->TaskForPid(Pid());
  if (task_port == TASK_NULL)
    return false;

  task_category_policy_data_t category_policy;
  mach_msg_type_number_t task_info_count = TASK_CATEGORY_POLICY_COUNT;
  boolean_t get_default = FALSE;

  kern_return_t result =
      task_policy_get(task_port, TASK_CATEGORY_POLICY,
                      reinterpret_cast<task_policy_t>(&category_policy),
                      &task_info_count, &get_default);
  MACH_LOG_IF(ERROR, result != KERN_SUCCESS, result)
      << "task_policy_get TASK_CATEGORY_POLICY";

  if (result == KERN_SUCCESS && get_default == FALSE) {
    return category_policy.role == TASK_BACKGROUND_APPLICATION;
  }
  return false;
}

bool Process::SetProcessBackgrounded(PortProvider* port_provider,
                                     bool background) {
  DCHECK(IsValid());
  if (port_provider == nullptr || !CanBackgroundProcesses())
    return false;

  mach_port_t task_port = port_provider->TaskForPid(Pid());
  if (task_port == TASK_NULL)
    return false;

  if (IsProcessBackgrounded(port_provider) == background)
    return true;

  task_category_policy category_policy;
  category_policy.role =
      background ? TASK_BACKGROUND_APPLICATION : TASK_FOREGROUND_APPLICATION;
  kern_return_t result =
      task_policy_set(task_port, TASK_CATEGORY_POLICY,
                      reinterpret_cast<task_policy_t>(&category_policy),
                      TASK_CATEGORY_POLICY_COUNT);

  if (result != KERN_SUCCESS) {
    MACH_LOG(ERROR, result) << "task_policy_set TASK_CATEGORY_POLICY";
    return false;
  }

  return true;
}

}  // namespace base
