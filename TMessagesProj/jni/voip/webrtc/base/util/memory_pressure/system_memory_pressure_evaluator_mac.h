// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_UTIL_MEMORY_PRESSURE_SYSTEM_MEMORY_PRESSURE_EVALUATOR_MAC_H_
#define BASE_UTIL_MEMORY_PRESSURE_SYSTEM_MEMORY_PRESSURE_EVALUATOR_MAC_H_

#include <CoreFoundation/CFDate.h>
#include <dispatch/dispatch.h>

#include "base/mac/scoped_cftyperef.h"
#include "base/mac/scoped_dispatch_object.h"
#include "base/macros.h"
#include "base/message_loop/message_pump_mac.h"
#include "base/sequence_checker.h"
#include "base/util/memory_pressure/memory_pressure_voter.h"
#include "base/util/memory_pressure/system_memory_pressure_evaluator.h"

namespace util {
namespace mac {

class TestSystemMemoryPressureEvaluator;

// Declares the interface for the Mac SystemMemoryPressureEvaluator, which
// reports memory pressure events and status.
class SystemMemoryPressureEvaluator
    : public util::SystemMemoryPressureEvaluator {
 public:
  explicit SystemMemoryPressureEvaluator(
      std::unique_ptr<MemoryPressureVoter> voter);
  ~SystemMemoryPressureEvaluator() override;

 private:
  friend TestSystemMemoryPressureEvaluator;

  static base::MemoryPressureListener::MemoryPressureLevel
  MemoryPressureLevelForMacMemoryPressureLevel(int mac_memory_pressure_level);

  // Returns the raw memory pressure level from the macOS. Exposed for
  // unit testing.
  virtual int GetMacMemoryPressureLevel();

  // Updates |last_pressure_level_| with the current memory pressure level.
  void UpdatePressureLevel();

  // Run |dispatch_callback| on memory pressure notifications from the OS.
  void OnMemoryPressureChanged();

  // The dispatch source that generates memory pressure change notifications.
  base::ScopedDispatchObject<dispatch_source_t> memory_level_event_source_;

  SEQUENCE_CHECKER(sequence_checker_);

  base::WeakPtrFactory<SystemMemoryPressureEvaluator> weak_ptr_factory_;

  DISALLOW_COPY_AND_ASSIGN(SystemMemoryPressureEvaluator);
};

}  // namespace mac
}  // namespace util

#endif  // BASE_UTIL_MEMORY_PRESSURE_SYSTEM_MEMORY_PRESSURE_EVALUATOR_MAC_H_
