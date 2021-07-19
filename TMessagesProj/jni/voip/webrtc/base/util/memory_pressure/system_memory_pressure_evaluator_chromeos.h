// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
#ifndef BASE_UTIL_MEMORY_PRESSURE_SYSTEM_MEMORY_PRESSURE_EVALUATOR_CHROMEOS_H_
#define BASE_UTIL_MEMORY_PRESSURE_SYSTEM_MEMORY_PRESSURE_EVALUATOR_CHROMEOS_H_

#include <vector>

#include "base/base_export.h"
#include "base/feature_list.h"
#include "base/files/scoped_file.h"
#include "base/macros.h"
#include "base/memory/memory_pressure_listener.h"
#include "base/memory/weak_ptr.h"
#include "base/process/process_metrics.h"
#include "base/time/time.h"
#include "base/timer/timer.h"
#include "base/util/memory_pressure/memory_pressure_voter.h"
#include "base/util/memory_pressure/system_memory_pressure_evaluator.h"

namespace util {
namespace chromeos {

// A feature which controls user space low memory notification.
extern const base::Feature kCrOSUserSpaceLowMemoryNotification;

////////////////////////////////////////////////////////////////////////////////
// SystemMemoryPressureEvaluator
//
// A class to handle the observation of our free memory. It notifies the
// MemoryPressureListener of memory fill level changes, so that it can take
// action to reduce memory resources accordingly.
class SystemMemoryPressureEvaluator
    : public util::SystemMemoryPressureEvaluator {
 public:
  // The SystemMemoryPressureEvaluator reads the pressure levels from the
  // /sys/kernel/mm/chromeos-low_mem/margin and does not need to be configured.
  //
  // NOTE: You should check that the kernel supports notifications by calling
  // SupportsKernelNotifications() before constructing a new instance of this
  // class.
  explicit SystemMemoryPressureEvaluator(
      std::unique_ptr<MemoryPressureVoter> voter);
  ~SystemMemoryPressureEvaluator() override;

  // GetMarginFileParts returns a vector of the configured margin file values.
  // The margin file contains two or more values, but we're only concerned with
  // the first two. The first represents critical memory pressure, the second
  // is moderate memory pressure level.
  static std::vector<int> GetMarginFileParts();

  // GetAvailableMemoryKB returns the available memory in KiB.
  uint64_t GetAvailableMemoryKB();

  // SupportsKernelNotifications will return true if the kernel supports and is
  // configured for notifications on memory availability changes.
  static bool SupportsKernelNotifications();

  // ScheduleEarlyCheck is used by the ChromeOS tab manager delegate to force it
  // to quickly recheck pressure levels after a tab discard or some other
  // action.
  void ScheduleEarlyCheck();

  // Returns the moderate pressure threshold as read from the margin file.
  int ModeratePressureThresholdMBForTesting() const {
    return moderate_pressure_threshold_mb_;
  }

  // Returns the critical pressure threshold as read from the margin file.
  int CriticalPressureThresholdMBForTesting() const {
    return critical_pressure_threshold_mb_;
  }

  // The memory parameters are saved for optimization.  If these memory
  // parameters are changed, call this function to update the saved values.
  void UpdateMemoryParameters();

  // Returns the current system memory pressure evaluator.
  static SystemMemoryPressureEvaluator* Get();

 protected:
  // This constructor is only used for testing.
  SystemMemoryPressureEvaluator(
      const std::string& margin_file,
      const std::string& available_file,
      base::RepeatingCallback<bool(int)> kernel_waiting_callback,
      bool disable_timer_for_testing,
      bool is_user_space_notify,
      std::unique_ptr<MemoryPressureVoter> voter);

  static std::vector<int> GetMarginFileParts(const std::string& margin_file);

  static uint64_t CalculateReservedFreeKB(const std::string& zoneinfo);

  static uint64_t GetReservedMemoryKB();

  static uint64_t CalculateAvailableMemoryUserSpaceKB(
      const base::SystemMemoryInfoKB& info,
      uint64_t reserved_free,
      uint64_t min_filelist,
      uint64_t ram_swap_weight);

  void CheckMemoryPressure();

 private:
  void HandleKernelNotification(bool result);
  void ScheduleWaitForKernelNotification();
  void CheckMemoryPressureAndRecordStatistics();
  int moderate_pressure_threshold_mb_ = 0;
  int critical_pressure_threshold_mb_ = 0;

  // We keep track of how long it has been since we last notified at the
  // moderate level.
  base::TimeTicks last_moderate_notification_;

  // We keep track of how long it's been since we notified on the
  // Memory.PressureLevel metric.
  base::TimeTicks last_pressure_level_report_;

  // File descriptor used to read and poll(2) available memory from sysfs,
  // In /sys/kernel/mm/chromeos-low_mem/available.
  base::ScopedFD available_mem_file_;

  // A timer to check the memory pressure and to report an UMA metric
  // periodically.
  base::RepeatingTimer checking_timer_;

  // Kernel waiting callback which is responsible for blocking on the
  // available file until it receives a kernel notification, this is
  // configurable to make testing easier.
  base::RepeatingCallback<bool()> kernel_waiting_callback_;

  // User space low memory notification mode.
  const bool is_user_space_notify_;

  // Values saved for user space available memory calculation.  The value of
  // |reserved_free_| should not change unless min_free_kbytes or
  // lowmem_reserve_ratio change.  The value of |min_filelist_| and
  // |ram_swap_weight_| should not change unless the user sets them manually.
  uint64_t reserved_free_;
  uint64_t min_filelist_;
  uint64_t ram_swap_weight_;

  SEQUENCE_CHECKER(sequence_checker_);

  base::WeakPtrFactory<SystemMemoryPressureEvaluator> weak_ptr_factory_;

  DISALLOW_COPY_AND_ASSIGN(SystemMemoryPressureEvaluator);
};

}  // namespace chromeos
}  // namespace util
#endif  // BASE_UTIL_MEMORY_PRESSURE_SYSTEM_MEMORY_PRESSURE_EVALUATOR_CHROMEOS_H_
