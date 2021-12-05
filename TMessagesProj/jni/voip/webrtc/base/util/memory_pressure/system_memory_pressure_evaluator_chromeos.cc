// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
#include "base/util/memory_pressure/system_memory_pressure_evaluator_chromeos.h"

#include <fcntl.h>
#include <sys/poll.h>
#include <string>
#include <vector>

#include "base/bind.h"
#include "base/files/file_util.h"
#include "base/metrics/histogram_macros.h"
#include "base/no_destructor.h"
#include "base/posix/eintr_wrapper.h"
#include "base/single_thread_task_runner.h"
#include "base/strings/string_number_conversions.h"
#include "base/strings/string_split.h"
#include "base/strings/string_util.h"
#include "base/system/sys_info.h"
#include "base/task/post_task.h"
#include "base/task/thread_pool.h"
#include "base/threading/scoped_blocking_call.h"
#include "base/threading/thread_task_runner_handle.h"
#include "base/time/time.h"

namespace util {
namespace chromeos {

const base::Feature kCrOSUserSpaceLowMemoryNotification{
    "CrOSUserSpaceLowMemoryNotification", base::FEATURE_DISABLED_BY_DEFAULT};

namespace {
// Pointer to the SystemMemoryPressureEvaluator used by TabManagerDelegate for
// chromeos to need to call into ScheduleEarlyCheck.
SystemMemoryPressureEvaluator* g_system_evaluator = nullptr;

// We try not to re-notify on moderate too frequently, this time
// controls how frequently we will notify after our first notification.
constexpr base::TimeDelta kModerateMemoryPressureCooldownTime =
    base::TimeDelta::FromSeconds(10);

// The margin mem file contains the two memory levels, the first is the
// critical level and the second is the moderate level. Note, this
// file may contain more values but only the first two are used for
// memory pressure notifications in chromeos.
constexpr char kMarginMemFile[] = "/sys/kernel/mm/chromeos-low_mem/margin";

// The available memory file contains the available memory as determined
// by the kernel.
constexpr char kAvailableMemFile[] =
    "/sys/kernel/mm/chromeos-low_mem/available";

// The reserved file cache.
constexpr char kMinFilelist[] = "/proc/sys/vm/min_filelist_kbytes";

// The estimation of how well zram based swap is compressed.
constexpr char kRamVsSwapWeight[] =
    "/sys/kernel/mm/chromeos-low_mem/ram_vs_swap_weight";

// Converts an available memory value in MB to a memory pressure level.
base::MemoryPressureListener::MemoryPressureLevel
GetMemoryPressureLevelFromAvailable(int available_mb,
                                    int moderate_avail_mb,
                                    int critical_avail_mb) {
  if (available_mb < critical_avail_mb)
    return base::MemoryPressureListener::MEMORY_PRESSURE_LEVEL_CRITICAL;
  if (available_mb < moderate_avail_mb)
    return base::MemoryPressureListener::MEMORY_PRESSURE_LEVEL_MODERATE;

  return base::MemoryPressureListener::MEMORY_PRESSURE_LEVEL_NONE;
}

uint64_t ReadFileToUint64(const base::FilePath& file) {
  std::string file_contents;
  if (!ReadFileToString(file, &file_contents))
    return 0;
  TrimWhitespaceASCII(file_contents, base::TRIM_ALL, &file_contents);
  uint64_t file_contents_uint64 = 0;
  if (!base::StringToUint64(file_contents, &file_contents_uint64))
    return 0;
  return file_contents_uint64;
}

uint64_t ReadAvailableMemoryMB(int available_fd) {
  // Read the available memory.
  char buf[32] = {};

  // kernfs/file.c:
  // "Once poll/select indicates that the value has changed, you
  // need to close and re-open the file, or seek to 0 and read again.
  ssize_t bytes_read = HANDLE_EINTR(pread(available_fd, buf, sizeof(buf), 0));
  PCHECK(bytes_read != -1);

  std::string mem_str(buf, bytes_read);
  uint64_t available = std::numeric_limits<uint64_t>::max();
  CHECK(base::StringToUint64(
      base::TrimWhitespaceASCII(mem_str, base::TrimPositions::TRIM_ALL),
      &available));

  return available;
}

// This function will wait until the /sys/kernel/mm/chromeos-low_mem/available
// file becomes readable and then read the latest value. This file will only
// become readable once the available memory cross through one of the margin
// values specified in /sys/kernel/mm/chromeos-low_mem/margin, for more
// details see https://crrev.com/c/536336.
bool WaitForMemoryPressureChanges(int available_fd) {
  base::ScopedBlockingCall scoped_blocking_call(FROM_HERE,
                                                base::BlockingType::WILL_BLOCK);

  pollfd pfd = {available_fd, POLLPRI | POLLERR, 0};
  int res = HANDLE_EINTR(poll(&pfd, 1, -1));  // Wait indefinitely.
  PCHECK(res != -1);

  if (pfd.revents != (POLLPRI | POLLERR)) {
    // If we didn't receive POLLPRI | POLLERR it means we likely received
    // POLLNVAL because the fd has been closed we will only log an error in
    // other situations.
    LOG_IF(ERROR, pfd.revents != POLLNVAL)
        << "WaitForMemoryPressureChanges received unexpected revents: "
        << pfd.revents;

    // We no longer want to wait for a kernel notification if the fd has been
    // closed.
    return false;
  }

  return true;
}

}  // namespace

SystemMemoryPressureEvaluator::SystemMemoryPressureEvaluator(
    std::unique_ptr<MemoryPressureVoter> voter)
    : SystemMemoryPressureEvaluator(
          kMarginMemFile,
          kAvailableMemFile,
          base::BindRepeating(&WaitForMemoryPressureChanges),
          /*disable_timer_for_testing*/ false,
          base::FeatureList::IsEnabled(
              chromeos::kCrOSUserSpaceLowMemoryNotification),
          std::move(voter)) {}

SystemMemoryPressureEvaluator::SystemMemoryPressureEvaluator(
    const std::string& margin_file,
    const std::string& available_file,
    base::RepeatingCallback<bool(int)> kernel_waiting_callback,
    bool disable_timer_for_testing,
    bool is_user_space_notify,
    std::unique_ptr<MemoryPressureVoter> voter)
    : util::SystemMemoryPressureEvaluator(std::move(voter)),
      is_user_space_notify_(is_user_space_notify),
      weak_ptr_factory_(this) {
  DCHECK(g_system_evaluator == nullptr);
  g_system_evaluator = this;

  std::vector<int> margin_parts =
      SystemMemoryPressureEvaluator::GetMarginFileParts(margin_file);

  // This class SHOULD have verified kernel support by calling
  // SupportsKernelNotifications() before creating a new instance of this.
  // Therefore we will check fail if we don't have multiple margin values.
  CHECK_LE(2u, margin_parts.size());
  critical_pressure_threshold_mb_ = margin_parts[0];
  moderate_pressure_threshold_mb_ = margin_parts[1];

  UpdateMemoryParameters();

  if (!is_user_space_notify_) {
    kernel_waiting_callback_ = base::BindRepeating(
        std::move(kernel_waiting_callback), available_mem_file_.get());
    available_mem_file_ =
        base::ScopedFD(HANDLE_EINTR(open(available_file.c_str(), O_RDONLY)));
    CHECK(available_mem_file_.is_valid());

    ScheduleWaitForKernelNotification();
  }

  if (!disable_timer_for_testing) {
    // We will check the memory pressure and report the metric
    // (ChromeOS.MemoryPressureLevel) every 1 second.
    checking_timer_.Start(
        FROM_HERE, base::TimeDelta::FromSeconds(1),
        base::BindRepeating(&SystemMemoryPressureEvaluator::
                                CheckMemoryPressureAndRecordStatistics,
                            weak_ptr_factory_.GetWeakPtr()));
  }
}
SystemMemoryPressureEvaluator::~SystemMemoryPressureEvaluator() {
  DCHECK(g_system_evaluator);
  g_system_evaluator = nullptr;
}

// static
SystemMemoryPressureEvaluator* SystemMemoryPressureEvaluator::Get() {
  return g_system_evaluator;
}

std::vector<int> SystemMemoryPressureEvaluator::GetMarginFileParts() {
  static const base::NoDestructor<std::vector<int>> margin_file_parts(
      GetMarginFileParts(kMarginMemFile));
  return *margin_file_parts;
}

std::vector<int> SystemMemoryPressureEvaluator::GetMarginFileParts(
    const std::string& file) {
  std::vector<int> margin_values;
  std::string margin_contents;
  if (base::ReadFileToString(base::FilePath(file), &margin_contents)) {
    std::vector<std::string> margins =
        base::SplitString(margin_contents, base::kWhitespaceASCII,
                          base::TRIM_WHITESPACE, base::SPLIT_WANT_NONEMPTY);
    for (const auto& v : margins) {
      int value = -1;
      if (!base::StringToInt(v, &value)) {
        // If any of the values weren't parseable as an int we return
        // nothing as the file format is unexpected.
        LOG(ERROR) << "Unable to parse margin file contents as integer: " << v;
        return std::vector<int>();
      }
      margin_values.push_back(value);
    }
  } else {
    LOG(ERROR) << "Unable to read margin file: " << kMarginMemFile;
  }
  return margin_values;
}

// CalculateReservedFreeKB() calculates the reserved free memory in KiB from
// /proc/zoneinfo.  Reserved pages are free pages reserved for emergent kernel
// allocation and are not available to the user space.  It's the sum of high
// watermarks and max protection pages of memory zones.  It implements the same
// reserved pages calculation in linux kernel calculate_totalreserve_pages().
//
// /proc/zoneinfo example:
// ...
// Node 0, zone    DMA32
//   pages free     422432
//         min      16270
//         low      20337
//         high     24404
//         ...
//         protection: (0, 0, 1953, 1953)
//
// The high field is the high watermark for this zone.  The protection field is
// the protected pages for lower zones.  See the lowmem_reserve_ratio section in
// https://www.kernel.org/doc/Documentation/sysctl/vm.txt.
uint64_t SystemMemoryPressureEvaluator::CalculateReservedFreeKB(
    const std::string& zoneinfo) {
  constexpr uint64_t kPageSizeKB = 4;

  uint64_t num_reserved_pages = 0;
  for (const base::StringPiece& line : base::SplitStringPiece(
           zoneinfo, "\n", base::KEEP_WHITESPACE, base::SPLIT_WANT_NONEMPTY)) {
    std::vector<base::StringPiece> tokens = base::SplitStringPiece(
        line, base::kWhitespaceASCII, base::TRIM_WHITESPACE,
        base::SPLIT_WANT_NONEMPTY);

    // Skip the line if there are not enough tokens.
    if (tokens.size() < 2) {
      continue;
    }

    if (tokens[0] == "high") {
      // Parse the high watermark.
      uint64_t high = 0;
      if (base::StringToUint64(tokens[1], &high)) {
        num_reserved_pages += high;
      } else {
        LOG(ERROR) << "Couldn't parse the high field in /proc/zoneinfo: "
                   << tokens[1];
      }
    } else if (tokens[0] == "protection:") {
      // Parse the protection pages.
      uint64_t max = 0;
      for (size_t i = 1; i < tokens.size(); ++i) {
        uint64_t num = 0;
        base::StringPiece entry;
        if (i == 1) {
          // Exclude the leading '(' and the trailing ','.
          entry = tokens[i].substr(1, tokens[i].size() - 2);
        } else {
          // Exclude the trailing ',' or ')'.
          entry = tokens[i].substr(0, tokens[i].size() - 1);
        }
        if (base::StringToUint64(entry, &num)) {
          max = std::max(max, num);
        } else {
          LOG(ERROR)
              << "Couldn't parse the protection field in /proc/zoneinfo: "
              << entry;
        }
      }
      num_reserved_pages += max;
    }
  }

  return num_reserved_pages * kPageSizeKB;
}

uint64_t SystemMemoryPressureEvaluator::GetReservedMemoryKB() {
  std::string file_contents;
  if (!ReadFileToString(base::FilePath("/proc/zoneinfo"), &file_contents)) {
    LOG(ERROR) << "Couldn't get /proc/zoneinfo";
    return 0;
  }
  return CalculateReservedFreeKB(file_contents);
}

// CalculateAvailableMemoryUserSpaceKB implements the same available memory
// calculation as kernel function get_available_mem_adj().  The available memory
// consists of 3 parts: the free memory, the file cache, and the swappable
// memory.  The available free memory is free memory minus reserved free memory.
// The available file cache is the total file cache minus reserved file cache
// (min_filelist).  Because swapping is prohibited if there is no anonymous
// memory or no swap free, the swappable memory is the minimal of anonymous
// memory and swap free.  As swapping memory is more costly than dropping file
// cache, only a fraction (1 / ram_swap_weight) of the swappable memory
// contributes to the available memory.
uint64_t SystemMemoryPressureEvaluator::CalculateAvailableMemoryUserSpaceKB(
    const base::SystemMemoryInfoKB& info,
    uint64_t reserved_free,
    uint64_t min_filelist,
    uint64_t ram_swap_weight) {
  const uint64_t free = info.free;
  const uint64_t anon = info.active_anon + info.inactive_anon;
  const uint64_t file = info.active_file + info.inactive_file;
  const uint64_t dirty = info.dirty;
  const uint64_t swap_free = info.swap_free;

  uint64_t available = (free > reserved_free) ? free - reserved_free : 0;
  available += (file > dirty + min_filelist) ? file - dirty - min_filelist : 0;
  available += std::min<uint64_t>(anon, swap_free) / ram_swap_weight;

  return available;
}

uint64_t SystemMemoryPressureEvaluator::GetAvailableMemoryKB() {
  if (is_user_space_notify_) {
    base::SystemMemoryInfoKB info;
    CHECK(base::GetSystemMemoryInfo(&info));
    return CalculateAvailableMemoryUserSpaceKB(info, reserved_free_,
                                               min_filelist_, ram_swap_weight_);
  } else {
    const uint64_t available_mem_mb =
        ReadAvailableMemoryMB(available_mem_file_.get());
    return available_mem_mb * 1024;
  }
}

bool SystemMemoryPressureEvaluator::SupportsKernelNotifications() {
  // Unfortunately at the moment the only way to determine if the chromeos
  // kernel supports polling on the available file is to observe two values
  // in the margin file, if the critical and moderate levels are specified
  // there then we know the kernel must support polling on available.
  return SystemMemoryPressureEvaluator::GetMarginFileParts().size() >= 2;
}

// CheckMemoryPressure will get the current memory pressure level by reading
// the available file.
void SystemMemoryPressureEvaluator::CheckMemoryPressure() {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);

  auto old_vote = current_vote();

  uint64_t mem_avail_mb = GetAvailableMemoryKB() / 1024;

  SetCurrentVote(GetMemoryPressureLevelFromAvailable(
      mem_avail_mb, moderate_pressure_threshold_mb_,
      critical_pressure_threshold_mb_));
  bool notify = true;

  if (current_vote() ==
      base::MemoryPressureListener::MEMORY_PRESSURE_LEVEL_NONE) {
    last_moderate_notification_ = base::TimeTicks();
    notify = false;
  }

  // In the case of MODERATE memory pressure we may be in this state for quite
  // some time so we limit the rate at which we dispatch notifications.
  else if (current_vote() ==
           base::MemoryPressureListener::MEMORY_PRESSURE_LEVEL_MODERATE) {
    if (old_vote == current_vote()) {
      if (base::TimeTicks::Now() - last_moderate_notification_ <
          kModerateMemoryPressureCooldownTime) {
        notify = false;
      } else if (old_vote ==
                 base::MemoryPressureListener::MEMORY_PRESSURE_LEVEL_CRITICAL) {
        // Reset the moderate notification time if we just crossed back.
        last_moderate_notification_ = base::TimeTicks::Now();
        notify = false;
      }
    }

    if (notify)
      last_moderate_notification_ = base::TimeTicks::Now();
  }

  VLOG(1) << "SystemMemoryPressureEvaluator::CheckMemoryPressure dispatching "
             "at level: "
          << current_vote();
  SendCurrentVote(notify);
}

void SystemMemoryPressureEvaluator::HandleKernelNotification(bool result) {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);

  // If WaitForKernelNotification returned false then the FD has been closed and
  // we just exit without waiting again.
  if (!result) {
    return;
  }

  CheckMemoryPressure();

  // Now we need to schedule back our blocking task to wait for more
  // kernel notifications.
  ScheduleWaitForKernelNotification();
}

void SystemMemoryPressureEvaluator::CheckMemoryPressureAndRecordStatistics() {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);

  // Note: If we support notifications of memory pressure changes in both
  // directions we will not have to update the cached value as it will always
  // be correct.
  CheckMemoryPressure();

  // Record UMA histogram statistics for the current memory pressure level, it
  // would seem that only Memory.PressureLevel would be necessary.
  constexpr int kNumberPressureLevels = 3;
  UMA_HISTOGRAM_ENUMERATION("ChromeOS.MemoryPressureLevel", current_vote(),
                            kNumberPressureLevels);
}

void SystemMemoryPressureEvaluator::ScheduleEarlyCheck() {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);

  base::ThreadTaskRunnerHandle::Get()->PostTask(
      FROM_HERE,
      base::BindOnce(&SystemMemoryPressureEvaluator::CheckMemoryPressure,
                     weak_ptr_factory_.GetWeakPtr()));
}

void SystemMemoryPressureEvaluator::UpdateMemoryParameters() {
  if (is_user_space_notify_) {
    reserved_free_ = GetReservedMemoryKB();
    min_filelist_ = ReadFileToUint64(base::FilePath(kMinFilelist));
    ram_swap_weight_ = ReadFileToUint64(base::FilePath(kRamVsSwapWeight));
  }
}

void SystemMemoryPressureEvaluator::ScheduleWaitForKernelNotification() {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);

  base::ThreadPool::PostTaskAndReplyWithResult(
      FROM_HERE,
      {base::MayBlock(), base::TaskShutdownBehavior::CONTINUE_ON_SHUTDOWN},
      base::BindOnce(kernel_waiting_callback_),
      base::BindOnce(&SystemMemoryPressureEvaluator::HandleKernelNotification,
                     weak_ptr_factory_.GetWeakPtr()));
}

}  // namespace chromeos
}  // namespace util
