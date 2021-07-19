// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/util/memory_pressure/multi_source_memory_pressure_monitor.h"

#include "base/bind.h"
#include "base/logging.h"
#include "base/metrics/histogram_functions.h"
#include "base/metrics/histogram_macros.h"
#include "base/time/time.h"
#include "base/trace_event/trace_event.h"
#include "base/util/memory_pressure/system_memory_pressure_evaluator.h"

namespace util {

MultiSourceMemoryPressureMonitor::MultiSourceMemoryPressureMonitor()
    : current_pressure_level_(
          base::MemoryPressureListener::MEMORY_PRESSURE_LEVEL_NONE),
      dispatch_callback_(base::BindRepeating(
          &base::MemoryPressureListener::NotifyMemoryPressure)),
      aggregator_(this) {
}

MultiSourceMemoryPressureMonitor::~MultiSourceMemoryPressureMonitor() {
  // Destroy system evaluator early while the remaining members of this class
  // still exist. MultiSourceMemoryPressureMonitor implements
  // MemoryPressureVoteAggregator::Delegate, and
  // delegate_->OnMemoryPressureLevelChanged() gets indirectly called during
  // ~SystemMemoryPressureEvaluator().
  system_evaluator_.reset();
}

void MultiSourceMemoryPressureMonitor::Start() {
  system_evaluator_ =
      SystemMemoryPressureEvaluator::CreateDefaultSystemEvaluator(this);
  StartMetricsTimer();
}

void MultiSourceMemoryPressureMonitor::StartMetricsTimer() {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  // Unretained is safe here since this task is running on a timer owned by this
  // object.
  metric_timer_.Start(
      FROM_HERE, MemoryPressureMonitor::kUMAMemoryPressureLevelPeriod,
      BindRepeating(
          &MultiSourceMemoryPressureMonitor::RecordCurrentPressureLevel,
          base::Unretained(this)));
}

void MultiSourceMemoryPressureMonitor::StopMetricsTimer() {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  metric_timer_.Stop();
}

void MultiSourceMemoryPressureMonitor::RecordCurrentPressureLevel() {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  RecordMemoryPressure(GetCurrentPressureLevel(), /* ticks = */ 1);
}

base::MemoryPressureListener::MemoryPressureLevel
MultiSourceMemoryPressureMonitor::GetCurrentPressureLevel() const {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  return current_pressure_level_;
}

std::unique_ptr<MemoryPressureVoter>
MultiSourceMemoryPressureMonitor::CreateVoter() {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  return aggregator_.CreateVoter();
}

void MultiSourceMemoryPressureMonitor::SetDispatchCallback(
    const DispatchCallback& callback) {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  dispatch_callback_ = callback;
}

void MultiSourceMemoryPressureMonitor::OnMemoryPressureLevelChanged(
    base::MemoryPressureListener::MemoryPressureLevel level) {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  DCHECK_NE(current_pressure_level_, level);

  TRACE_EVENT_INSTANT1(
      "base", "MultiSourceMemoryPressureMonitor::OnMemoryPressureLevelChanged",
      TRACE_EVENT_SCOPE_THREAD, "level", level);

  // Records the duration of the latest pressure session, there are 4
  // transitions of interest:
  //   - Moderate -> None
  //   - Moderate -> Critical
  //   - Critical -> Moderate
  //   - Critical -> None

  base::TimeTicks now = base::TimeTicks::Now();

  if (current_pressure_level_ !=
      MemoryPressureLevel::MEMORY_PRESSURE_LEVEL_NONE) {
    DCHECK(!last_pressure_change_timestamp_.is_null());
    std::string histogram_name = "Memory.PressureWindowDuration.";
    switch (current_pressure_level_) {
      // From:
      case MemoryPressureLevel::MEMORY_PRESSURE_LEVEL_MODERATE: {
        // To:
        if (level == MemoryPressureLevel::MEMORY_PRESSURE_LEVEL_NONE) {
          histogram_name += "ModerateToNone";
        } else {  // MemoryPressureLevel::MEMORY_PRESSURE_LEVEL_CRITICAL
          histogram_name += "ModerateToCritical";
        }
        break;
      }
      // From:
      case MemoryPressureLevel::MEMORY_PRESSURE_LEVEL_CRITICAL: {
        // To:
        if (level == MemoryPressureLevel::MEMORY_PRESSURE_LEVEL_NONE) {
          histogram_name += "CriticalToNone";
        } else {  // MemoryPressureLevel::MEMORY_PRESSURE_LEVEL_MODERATE
          histogram_name += "CriticalToModerate";
        }
        break;
      }
      case MemoryPressureLevel::MEMORY_PRESSURE_LEVEL_NONE:
      default:
        break;
    }

    base::UmaHistogramCustomTimes(
        histogram_name, now - last_pressure_change_timestamp_,
        base::TimeDelta::FromSeconds(1), base::TimeDelta::FromMinutes(10), 50);
  }

  last_pressure_change_timestamp_ = now;

  current_pressure_level_ = level;
}

void MultiSourceMemoryPressureMonitor::OnNotifyListenersRequested() {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  dispatch_callback_.Run(current_pressure_level_);
}

void MultiSourceMemoryPressureMonitor::ResetSystemEvaluatorForTesting() {
  system_evaluator_.reset();
}

void MultiSourceMemoryPressureMonitor::SetSystemEvaluator(
    std::unique_ptr<SystemMemoryPressureEvaluator> evaluator) {
  DCHECK(!system_evaluator_);
  system_evaluator_ = std::move(evaluator);
}

}  // namespace util
