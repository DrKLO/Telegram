// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/process/process.h"

#include <errno.h>
#include <sys/resource.h>

#include "base/files/file_util.h"
#include "base/logging.h"
#include "base/posix/can_lower_nice_to.h"
#include "base/process/internal_linux.h"
#include "base/strings/string_number_conversions.h"
#include "base/strings/string_split.h"
#include "base/strings/stringprintf.h"
#include "base/synchronization/lock.h"
#include "base/threading/thread_restrictions.h"
#include "build/build_config.h"

namespace base {

namespace {

const int kForegroundPriority = 0;

#if defined(OS_CHROMEOS)
// We are more aggressive in our lowering of background process priority
// for chromeos as we have much more control over other processes running
// on the machine.
//
// TODO(davemoore) Refactor this by adding support for higher levels to set
// the foregrounding / backgrounding process so we don't have to keep
// chrome / chromeos specific logic here.
const int kBackgroundPriority = 19;
const char kControlPath[] = "/sys/fs/cgroup/cpu%s/cgroup.procs";
const char kForeground[] = "/chrome_renderers/foreground";
const char kBackground[] = "/chrome_renderers/background";
const char kProcPath[] = "/proc/%d/cgroup";

struct CGroups {
  // Check for cgroups files. ChromeOS supports these by default. It creates
  // a cgroup mount in /sys/fs/cgroup and then configures two cpu task groups,
  // one contains at most a single foreground renderer and the other contains
  // all background renderers. This allows us to limit the impact of background
  // renderers on foreground ones to a greater level than simple renicing.
  bool enabled;
  base::FilePath foreground_file;
  base::FilePath background_file;

  CGroups() {
    foreground_file =
        base::FilePath(base::StringPrintf(kControlPath, kForeground));
    background_file =
        base::FilePath(base::StringPrintf(kControlPath, kBackground));
    base::FileSystemType foreground_type;
    base::FileSystemType background_type;
    enabled =
        base::GetFileSystemType(foreground_file, &foreground_type) &&
        base::GetFileSystemType(background_file, &background_type) &&
        foreground_type == FILE_SYSTEM_CGROUP &&
        background_type == FILE_SYSTEM_CGROUP;
  }

  static CGroups& Get() {
    static auto& groups = *new CGroups;
    return groups;
  }
};
#else
const int kBackgroundPriority = 5;
#endif  // defined(OS_CHROMEOS)

}  // namespace

Time Process::CreationTime() const {
  int64_t start_ticks = is_current()
                            ? internal::ReadProcSelfStatsAndGetFieldAsInt64(
                                  internal::VM_STARTTIME)
                            : internal::ReadProcStatsAndGetFieldAsInt64(
                                  Pid(), internal::VM_STARTTIME);
  if (!start_ticks)
    return Time();
  TimeDelta start_offset = internal::ClockTicksToTimeDelta(start_ticks);
  Time boot_time = internal::GetBootTime();
  if (boot_time.is_null())
    return Time();
  return Time(boot_time + start_offset);
}

// static
bool Process::CanBackgroundProcesses() {
#if defined(OS_CHROMEOS)
  if (CGroups::Get().enabled)
    return true;
#endif  // defined(OS_CHROMEOS)

  static const bool can_reraise_priority =
      internal::CanLowerNiceTo(kForegroundPriority);
  return can_reraise_priority;
}

bool Process::IsProcessBackgrounded() const {
  DCHECK(IsValid());

#if defined(OS_CHROMEOS)
  if (CGroups::Get().enabled) {
    // Used to allow reading the process priority from proc on thread launch.
    base::ThreadRestrictions::ScopedAllowIO allow_io;
    std::string proc;
    if (base::ReadFileToString(
            base::FilePath(StringPrintf(kProcPath, process_)), &proc)) {
      return IsProcessBackgroundedCGroup(proc);
    }
    return false;
  }
#endif  // defined(OS_CHROMEOS)

  return GetPriority() == kBackgroundPriority;
}

bool Process::SetProcessBackgrounded(bool background) {
  DCHECK(IsValid());

#if defined(OS_CHROMEOS)
  if (CGroups::Get().enabled) {
    std::string pid = NumberToString(process_);
    const base::FilePath file = background ? CGroups::Get().background_file
                                           : CGroups::Get().foreground_file;
    return base::WriteFile(file, pid.c_str(), pid.size()) > 0;
  }
#endif  // defined(OS_CHROMEOS)

  if (!CanBackgroundProcesses())
    return false;

  int priority = background ? kBackgroundPriority : kForegroundPriority;
  int result = setpriority(PRIO_PROCESS, process_, priority);
  DPCHECK(result == 0);
  return result == 0;
}

#if defined(OS_CHROMEOS)
bool IsProcessBackgroundedCGroup(const StringPiece& cgroup_contents) {
  // The process can be part of multiple control groups, and for each cgroup
  // hierarchy there's an entry in the file. We look for a control group
  // named "/chrome_renderers/background" to determine if the process is
  // backgrounded. crbug.com/548818.
  std::vector<StringPiece> lines = SplitStringPiece(
      cgroup_contents, "\n", TRIM_WHITESPACE, SPLIT_WANT_NONEMPTY);
  for (const auto& line : lines) {
    std::vector<StringPiece> fields =
        SplitStringPiece(line, ":", TRIM_WHITESPACE, SPLIT_WANT_ALL);
    if (fields.size() != 3U) {
      NOTREACHED();
      continue;
    }
    if (fields[2] == kBackground)
      return true;
  }

  return false;
}
#endif  // defined(OS_CHROMEOS)

#if defined(OS_CHROMEOS)
// Reads /proc/<pid>/status and returns the PID in its PID namespace.
// If the process is not in a PID namespace or /proc/<pid>/status does not
// report NSpid, kNullProcessId is returned.
ProcessId Process::GetPidInNamespace() const {
  std::string status;
  {
    // Synchronously reading files in /proc does not hit the disk.
    ThreadRestrictions::ScopedAllowIO allow_io;
    FilePath status_file =
        FilePath("/proc").Append(NumberToString(process_)).Append("status");
    if (!ReadFileToString(status_file, &status)) {
      return kNullProcessId;
    }
  }

  StringPairs pairs;
  SplitStringIntoKeyValuePairs(status, ':', '\n', &pairs);
  for (const auto& pair : pairs) {
    const std::string& key = pair.first;
    const std::string& value_str = pair.second;
    if (key == "NSpid") {
      std::vector<StringPiece> split_value_str = SplitStringPiece(
          value_str, "\t", TRIM_WHITESPACE, SPLIT_WANT_NONEMPTY);
      if (split_value_str.size() <= 1) {
        return kNullProcessId;
      }
      int value;
      // The last value in the list is the PID in the namespace.
      if (!StringToInt(split_value_str.back(), &value)) {
        NOTREACHED();
        return kNullProcessId;
      }
      return value;
    }
  }
  return kNullProcessId;
}
#endif  // defined(OS_CHROMEOS)

}  // namespace base
