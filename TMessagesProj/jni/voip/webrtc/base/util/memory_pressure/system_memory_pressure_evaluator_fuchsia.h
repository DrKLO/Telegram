// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_UTIL_MEMORY_PRESSURE_SYSTEM_MEMORY_PRESSURE_EVALUATOR_FUCHSIA_H_
#define BASE_UTIL_MEMORY_PRESSURE_SYSTEM_MEMORY_PRESSURE_EVALUATOR_FUCHSIA_H_

#include <fuchsia/memorypressure/cpp/fidl.h>
#include <lib/fidl/cpp/binding.h>

#include "base/sequence_checker.h"
#include "base/util/memory_pressure/system_memory_pressure_evaluator.h"

namespace util {
class MemoryPressureVoter;

// Registers with the fuchsia.memorypressure.Provider to be notified of changes
// to the system memory pressure level.
class SystemMemoryPressureEvaluatorFuchsia
    : public SystemMemoryPressureEvaluator,
      public fuchsia::memorypressure::Watcher {
 public:
  explicit SystemMemoryPressureEvaluatorFuchsia(
      std::unique_ptr<util::MemoryPressureVoter> voter);

  ~SystemMemoryPressureEvaluatorFuchsia() override;

  SystemMemoryPressureEvaluatorFuchsia(
      const SystemMemoryPressureEvaluatorFuchsia&) = delete;
  SystemMemoryPressureEvaluatorFuchsia& operator=(
      const SystemMemoryPressureEvaluatorFuchsia&) = delete;

 private:
  // fuchsia::memorypressure::Watcher implementation.
  void OnLevelChanged(fuchsia::memorypressure::Level level,
                      OnLevelChangedCallback callback) override;

  fidl::Binding<fuchsia::memorypressure::Watcher> binding_;

  SEQUENCE_CHECKER(sequence_checker_);
};

}  // namespace util

#endif  // BASE_UTIL_MEMORY_PRESSURE_SYSTEM_MEMORY_PRESSURE_EVALUATOR_FUCHSIA_H_
