// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_UTIL_MEMORY_PRESSURE_SYSTEM_MEMORY_PRESSURE_EVALUATOR_H_
#define BASE_UTIL_MEMORY_PRESSURE_SYSTEM_MEMORY_PRESSURE_EVALUATOR_H_

#include "base/memory/memory_pressure_listener.h"
#include "base/util/memory_pressure/memory_pressure_voter.h"
#include "base/util/memory_pressure/multi_source_memory_pressure_monitor.h"

namespace util {

// Base class for the platform SystemMemoryPressureEvaluators, which use
// MemoryPressureVoters to cast their vote on the overall MemoryPressureLevel.
class SystemMemoryPressureEvaluator {
 public:
  // Used by the MemoryPressureMonitor to create the correct Evaluator for the
  // platform in use.
  static std::unique_ptr<SystemMemoryPressureEvaluator>
  CreateDefaultSystemEvaluator(MultiSourceMemoryPressureMonitor* monitor);

  virtual ~SystemMemoryPressureEvaluator();

  base::MemoryPressureListener::MemoryPressureLevel current_vote() const {
    return current_vote_;
  }

 protected:
  explicit SystemMemoryPressureEvaluator(
      std::unique_ptr<MemoryPressureVoter> voter);

  // Sets the Evaluator's |current_vote_| member without casting vote to the
  // MemoryPressureVoteAggregator.
  void SetCurrentVote(base::MemoryPressureListener::MemoryPressureLevel level);

  // Uses the Evaluators' |voter_| to cast/update its vote on memory pressure
  // level. The MemoryPressureListeners will only be notified of the newly
  // calculated pressure level if |notify| is true.
  void SendCurrentVote(bool notify) const;

 private:
  base::MemoryPressureListener::MemoryPressureLevel current_vote_;

  // In charge of forwarding votes from here to the
  // MemoryPressureVoteAggregator.
  std::unique_ptr<MemoryPressureVoter> voter_;

  SEQUENCE_CHECKER(sequence_checker_);

  DISALLOW_COPY_AND_ASSIGN(SystemMemoryPressureEvaluator);
};

}  // namespace util

#endif  // BASE_UTIL_MEMORY_PRESSURE_SYSTEM_MEMORY_PRESSURE_EVALUATOR_H_
