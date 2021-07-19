// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/process/internal_aix.h"

#include <sys/procfs.h>

#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <unistd.h>

#include <map>
#include <string>
#include <vector>

#include "base/files/file_util.h"
#include "base/logging.h"
#include "base/strings/string_number_conversions.h"
#include "base/strings/string_split.h"
#include "base/strings/string_util.h"
#include "base/threading/thread_restrictions.h"
#include "base/time/time.h"

// Not defined on AIX by default.
#define NAME_MAX 255

namespace base {
namespace internalAIX {

const char kProcDir[] = "/proc";

const char kStatFile[] = "psinfo";  // AIX specific

FilePath GetProcPidDir(pid_t pid) {
  return FilePath(kProcDir).Append(NumberToString(pid));
}

pid_t ProcDirSlotToPid(const char* d_name) {
  int i;
  for (i = 0; i < NAME_MAX && d_name[i]; ++i) {
    if (!IsAsciiDigit(d_name[i])) {
      return 0;
    }
  }
  if (i == NAME_MAX)
    return 0;

  // Read the process's command line.
  pid_t pid;
  std::string pid_string(d_name);
  if (!StringToInt(pid_string, &pid)) {
    NOTREACHED();
    return 0;
  }
  return pid;
}

bool ReadProcFile(const FilePath& file, struct psinfo* info) {
  // Synchronously reading files in /proc is safe.
  ThreadRestrictions::ScopedAllowIO allow_io;
  int fileId;
  if ((fileId = open(file.value().c_str(), O_RDONLY)) < 0) {
    DPLOG(WARNING) << "Failed to open " << file.MaybeAsASCII();
    return false;
  }

  if (read(fileId, info, sizeof(*info)) < 0) {
    DPLOG(WARNING) << "Failed to read " << file.MaybeAsASCII();
    return false;
  }

  return true;
}

bool ReadProcStats(pid_t pid, struct psinfo* info) {
  FilePath stat_file = internalAIX::GetProcPidDir(pid).Append(kStatFile);
  return ReadProcFile(stat_file, info);
}

bool ParseProcStats(struct psinfo& stats_data,
                    std::vector<std::string>* proc_stats) {
  // The stat file is formatted as:
  // struct psinfo
  // see -
  // https://www.ibm.com/support/knowledgecenter/ssw_aix_71/com.ibm.aix.files/proc.htm
  proc_stats->clear();
  // PID.
  proc_stats->push_back(NumberToString(stats_data.pr_pid));
  // Process name without parentheses. // 1
  proc_stats->push_back(stats_data.pr_fname);
  // Process State (Not available)  // 2
  proc_stats->push_back("0");
  // Process id of parent  // 3
  proc_stats->push_back(NumberToString(stats_data.pr_ppid));

  // Process group id // 4
  proc_stats->push_back(NumberToString(stats_data.pr_pgid));

  return true;
}

typedef std::map<std::string, std::string> ProcStatMap;
void ParseProcStat(const std::string& contents, ProcStatMap* output) {
  StringPairs key_value_pairs;
  SplitStringIntoKeyValuePairs(contents, ' ', '\n', &key_value_pairs);
  for (size_t i = 0; i < key_value_pairs.size(); ++i) {
    output->insert(key_value_pairs[i]);
  }
}

int64_t GetProcStatsFieldAsInt64(const std::vector<std::string>& proc_stats,
                                 ProcStatsFields field_num) {
  DCHECK_GE(field_num, VM_PPID);
  CHECK_LT(static_cast<size_t>(field_num), proc_stats.size());

  int64_t value;
  return StringToInt64(proc_stats[field_num], &value) ? value : 0;
}

size_t GetProcStatsFieldAsSizeT(const std::vector<std::string>& proc_stats,
                                ProcStatsFields field_num) {
  DCHECK_GE(field_num, VM_PPID);
  CHECK_LT(static_cast<size_t>(field_num), proc_stats.size());

  size_t value;
  return StringToSizeT(proc_stats[field_num], &value) ? value : 0;
}

int64_t ReadProcStatsAndGetFieldAsInt64(pid_t pid, ProcStatsFields field_num) {
  struct psinfo stats_data;
  if (!ReadProcStats(pid, &stats_data))
    return 0;
  std::vector<std::string> proc_stats;
  if (!ParseProcStats(stats_data, &proc_stats))
    return 0;

  return GetProcStatsFieldAsInt64(proc_stats, field_num);
}

size_t ReadProcStatsAndGetFieldAsSizeT(pid_t pid, ProcStatsFields field_num) {
  struct psinfo stats_data;
  if (!ReadProcStats(pid, &stats_data))
    return 0;
  std::vector<std::string> proc_stats;
  if (!ParseProcStats(stats_data, &proc_stats))
    return 0;
  return GetProcStatsFieldAsSizeT(proc_stats, field_num);
}

}  // namespace internalAIX
}  // namespace base
