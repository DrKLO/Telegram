// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_UTIL_MEMORY_PRESSURE_MULTI_SOURCE_MEMORY_PRESSURE_MONITOR_H_
#define BASE_UTIL_MEMORY_PRESSURE_MULTI_SOURCE_MEMORY_PRESSURE_MONITOR_H_

#include "base/memory/memory_pressure_monitor.h"
#include "base/time/time.h"
#include "base/timer/timer.h"
#include "base/util/memory_pressure/memory_pressure_voter.h"

namespace util {

class SystemMemoryPressureEvaluator;

// This is a specialization of a MemoryPressureMonitor that relies on a set of
// MemoryPressureVoters to determine the memory pressure state. The
// MemoryPressureVoteAggregator is in charge of receiving votes from these
// voters and notifying MemoryPressureListeners of the MemoryPressureLevel via
// the monitor's |dispatch_callback_|. The pressure level is calculated as the
// most critical of all votes collected.
// This class is not thread safe and should be used from a single sequence.
class MultiSourceMemoryPressureMonitor
    : public base::MemoryPressureMonitor,
      public MemoryPressureVoteAggregator::Delegate {
 public:
  using MemoryPressureLevel = base::MemoryPressureMonitor::MemoryPressureLevel;
  using DispatchCallback = base::MemoryPressureMonitor::DispatchCallback;

  MultiSourceMemoryPressureMonitor();
  ~MultiSourceMemoryPressureMonitor() override;

  // Start monitoring memory pressure using the platform-specific voter.
  void Start();

  // MemoryPressureMonitor implementation.
  MemoryPressureLevel GetCurrentPressureLevel() const override;
  void SetDispatchCallback(const DispatchCallback& callback) override;

  // Creates a MemoryPressureVoter to be owned/used by a source that wishes to
  // have input on the overall memory pressure level.
  std::unique_ptr<MemoryPressureVoter> CreateVoter();

  MemoryPressureVoteAggregator* aggregator_for_testing() {
    return &aggregator_;
  }

  void ResetSystemEvaluatorForTesting();

  void SetSystemEvaluator(
      std::unique_ptr<SystemMemoryPressureEvaluator> evaluator);

 protected:
  void StartMetricsTimer();
  void StopMetricsTimer();

 private:
  // Delegate implementation.
  void OnMemoryPressureLevelChanged(MemoryPressureLevel level) override;
  void OnNotifyListenersRequested() override;

  void RecordCurrentPressureLevel();

  MemoryPressureLevel current_pressure_level_;

  DispatchCallback dispatch_callback_;

  MemoryPressureVoteAggregator aggregator_;

  std::unique_ptr<SystemMemoryPressureEvaluator> system_evaluator_;

  // A periodic timer to record UMA metrics.
  base::RepeatingTimer metric_timer_;

  // The timestamp of the last pressure change event.
  base::TimeTicks last_pressure_change_timestamp_;

  SEQUENCE_CHECKER(sequence_checker_);

  DISALLOW_COPY_AND_ASSIGN(MultiSourceMemoryPressureMonitor);
};

}  // namespace util

#endif  // BASE_UTIL_MEMORY_PRESSURE_MULTI_SOURCE_MEMORY_PRESSURE_MONITOR_H_
