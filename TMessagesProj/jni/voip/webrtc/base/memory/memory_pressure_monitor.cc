// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/memory/memory_pressure_monitor.h"

#include "base/logging.h"
#include "base/metrics/histogram_macros.h"

namespace base {
namespace {

MemoryPressureMonitor* g_monitor = nullptr;

// Enumeration of UMA memory pressure levels. This needs to be kept in sync with
// histograms.xml and the memory pressure levels defined in
// MemoryPressureListener.
enum MemoryPressureLevelUMA {
  UMA_MEMORY_PRESSURE_LEVEL_NONE = 0,
  UMA_MEMORY_PRESSURE_LEVEL_MODERATE = 1,
  UMA_MEMORY_PRESSURE_LEVEL_CRITICAL = 2,
  // This must be the last value in the enum.
  UMA_MEMORY_PRESSURE_LEVEL_COUNT,
};

// Converts a memory pressure level to an UMA enumeration value.
MemoryPressureLevelUMA MemoryPressureLevelToUmaEnumValue(
    base::MemoryPressureListener::MemoryPressureLevel level) {
  switch (level) {
    case MemoryPressureListener::MEMORY_PRESSURE_LEVEL_NONE:
      return UMA_MEMORY_PRESSURE_LEVEL_NONE;
    case MemoryPressureListener::MEMORY_PRESSURE_LEVEL_MODERATE:
      return UMA_MEMORY_PRESSURE_LEVEL_MODERATE;
    case MemoryPressureListener::MEMORY_PRESSURE_LEVEL_CRITICAL:
      return UMA_MEMORY_PRESSURE_LEVEL_CRITICAL;
  }
  NOTREACHED();
  return UMA_MEMORY_PRESSURE_LEVEL_NONE;
}

}  // namespace

const base::TimeDelta MemoryPressureMonitor::kUMAMemoryPressureLevelPeriod =
    base::TimeDelta::FromSeconds(5);

MemoryPressureMonitor::MemoryPressureMonitor() {
  DCHECK(!g_monitor);
  g_monitor = this;
}

MemoryPressureMonitor::~MemoryPressureMonitor() {
  DCHECK(g_monitor);
  g_monitor = nullptr;
}

// static
MemoryPressureMonitor* MemoryPressureMonitor::Get() {
  return g_monitor;
}
void MemoryPressureMonitor::RecordMemoryPressure(
    base::MemoryPressureListener::MemoryPressureLevel level,
    int ticks) {
  // Use the more primitive STATIC_HISTOGRAM_POINTER_BLOCK macro because the
  // simple UMA_HISTOGRAM macros don't expose 'AddCount' functionality.
  STATIC_HISTOGRAM_POINTER_BLOCK(
      "Memory.PressureLevel",
      AddCount(MemoryPressureLevelToUmaEnumValue(level), ticks),
      base::LinearHistogram::FactoryGet(
          "Memory.PressureLevel", 1, UMA_MEMORY_PRESSURE_LEVEL_COUNT,
          UMA_MEMORY_PRESSURE_LEVEL_COUNT + 1,
          base::HistogramBase::kUmaTargetedHistogramFlag));
}

}  // namespace base
