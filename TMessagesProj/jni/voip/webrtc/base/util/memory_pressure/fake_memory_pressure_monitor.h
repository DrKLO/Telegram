// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_UTIL_MEMORY_PRESSURE_FAKE_MEMORY_PRESSURE_MONITOR_H_
#define BASE_UTIL_MEMORY_PRESSURE_FAKE_MEMORY_PRESSURE_MONITOR_H_

#include "base/macros.h"
#include "base/util/memory_pressure/multi_source_memory_pressure_monitor.h"

namespace util {
namespace test {

class FakeMemoryPressureMonitor
    : public ::util::MultiSourceMemoryPressureMonitor {
 public:
  using MemoryPressureLevel =
      ::util::MultiSourceMemoryPressureMonitor::MemoryPressureLevel;
  using DispatchCallback =
      ::util::MultiSourceMemoryPressureMonitor::DispatchCallback;

  FakeMemoryPressureMonitor();
  ~FakeMemoryPressureMonitor() override;

  void SetAndNotifyMemoryPressure(MemoryPressureLevel level);

  // base::MemoryPressureMonitor overrides:
  MemoryPressureLevel GetCurrentPressureLevel() const override;
  void SetDispatchCallback(const DispatchCallback& callback) override;

 private:
  MemoryPressureLevel memory_pressure_level_;

  DISALLOW_COPY_AND_ASSIGN(FakeMemoryPressureMonitor);
};

}  // namespace test
}  // namespace util

#endif  // BASE_UTIL_MEMORY_PRESSURE_FAKE_MEMORY_PRESSURE_MONITOR_H_
