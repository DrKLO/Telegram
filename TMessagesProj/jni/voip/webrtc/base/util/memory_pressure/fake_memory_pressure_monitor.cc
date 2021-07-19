// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/util/memory_pressure/fake_memory_pressure_monitor.h"

namespace util {
namespace test {

FakeMemoryPressureMonitor::FakeMemoryPressureMonitor()
    : MultiSourceMemoryPressureMonitor(),
      memory_pressure_level_(MemoryPressureLevel::MEMORY_PRESSURE_LEVEL_NONE) {}

FakeMemoryPressureMonitor::~FakeMemoryPressureMonitor() {}

void FakeMemoryPressureMonitor::SetAndNotifyMemoryPressure(
    MemoryPressureLevel level) {
  memory_pressure_level_ = level;
  base::MemoryPressureListener::SimulatePressureNotification(level);
}

base::MemoryPressureMonitor::MemoryPressureLevel
FakeMemoryPressureMonitor::GetCurrentPressureLevel() const {
  return memory_pressure_level_;
}

void FakeMemoryPressureMonitor::SetDispatchCallback(
    const DispatchCallback& callback) {
  LOG(ERROR) << "FakeMemoryPressureMonitor::SetDispatchCallback";
}

}  // namespace test
}  // namespace util
