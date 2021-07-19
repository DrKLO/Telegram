// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/metrics/histogram_functions.h"

#include "base/metrics/histogram.h"
#include "base/metrics/histogram_base.h"
#include "base/metrics/sparse_histogram.h"
#include "base/time/time.h"

namespace base {

void UmaHistogramBoolean(const std::string& name, bool sample) {
  HistogramBase* histogram = BooleanHistogram::FactoryGet(
      name, HistogramBase::kUmaTargetedHistogramFlag);
  histogram->Add(sample);
}

void UmaHistogramBoolean(const char* name, bool sample) {
  HistogramBase* histogram = BooleanHistogram::FactoryGet(
      name, HistogramBase::kUmaTargetedHistogramFlag);
  histogram->Add(sample);
}

void UmaHistogramExactLinear(const std::string& name,
                             int sample,
                             int value_max) {
  HistogramBase* histogram =
      LinearHistogram::FactoryGet(name, 1, value_max, value_max + 1,
                                  HistogramBase::kUmaTargetedHistogramFlag);
  histogram->Add(sample);
}

void UmaHistogramExactLinear(const char* name, int sample, int value_max) {
  HistogramBase* histogram =
      LinearHistogram::FactoryGet(name, 1, value_max, value_max + 1,
                                  HistogramBase::kUmaTargetedHistogramFlag);
  histogram->Add(sample);
}

void UmaHistogramPercentage(const std::string& name, int percent) {
  UmaHistogramExactLinear(name, percent, 100);
}

void UmaHistogramPercentage(const char* name, int percent) {
  UmaHistogramExactLinear(name, percent, 100);
}

void UmaHistogramCustomCounts(const std::string& name,
                              int sample,
                              int min,
                              int max,
                              int buckets) {
  HistogramBase* histogram = Histogram::FactoryGet(
      name, min, max, buckets, HistogramBase::kUmaTargetedHistogramFlag);
  histogram->Add(sample);
}

void UmaHistogramCustomCounts(const char* name,
                              int sample,
                              int min,
                              int max,
                              int buckets) {
  HistogramBase* histogram = Histogram::FactoryGet(
      name, min, max, buckets, HistogramBase::kUmaTargetedHistogramFlag);
  histogram->Add(sample);
}

void UmaHistogramCounts100(const std::string& name, int sample) {
  UmaHistogramCustomCounts(name, sample, 1, 100, 50);
}

void UmaHistogramCounts100(const char* name, int sample) {
  UmaHistogramCustomCounts(name, sample, 1, 100, 50);
}

void UmaHistogramCounts1000(const std::string& name, int sample) {
  UmaHistogramCustomCounts(name, sample, 1, 1000, 50);
}

void UmaHistogramCounts1000(const char* name, int sample) {
  UmaHistogramCustomCounts(name, sample, 1, 1000, 50);
}

void UmaHistogramCounts10000(const std::string& name, int sample) {
  UmaHistogramCustomCounts(name, sample, 1, 10000, 50);
}

void UmaHistogramCounts10000(const char* name, int sample) {
  UmaHistogramCustomCounts(name, sample, 1, 10000, 50);
}

void UmaHistogramCounts100000(const std::string& name, int sample) {
  UmaHistogramCustomCounts(name, sample, 1, 100000, 50);
}

void UmaHistogramCounts100000(const char* name, int sample) {
  UmaHistogramCustomCounts(name, sample, 1, 100000, 50);
}

void UmaHistogramCounts1M(const std::string& name, int sample) {
  UmaHistogramCustomCounts(name, sample, 1, 1000000, 50);
}

void UmaHistogramCounts1M(const char* name, int sample) {
  UmaHistogramCustomCounts(name, sample, 1, 1000000, 50);
}

void UmaHistogramCounts10M(const std::string& name, int sample) {
  UmaHistogramCustomCounts(name, sample, 1, 10000000, 50);
}

void UmaHistogramCounts10M(const char* name, int sample) {
  UmaHistogramCustomCounts(name, sample, 1, 10000000, 50);
}

void UmaHistogramCustomTimes(const std::string& name,
                             TimeDelta sample,
                             TimeDelta min,
                             TimeDelta max,
                             int buckets) {
  HistogramBase* histogram = Histogram::FactoryTimeGet(
      name, min, max, buckets, HistogramBase::kUmaTargetedHistogramFlag);
  histogram->AddTimeMillisecondsGranularity(sample);
}

void UmaHistogramCustomTimes(const char* name,
                             TimeDelta sample,
                             TimeDelta min,
                             TimeDelta max,
                             int buckets) {
  HistogramBase* histogram = Histogram::FactoryTimeGet(
      name, min, max, buckets, HistogramBase::kUmaTargetedHistogramFlag);
  histogram->AddTimeMillisecondsGranularity(sample);
}

void UmaHistogramTimes(const std::string& name, TimeDelta sample) {
  UmaHistogramCustomTimes(name, sample, TimeDelta::FromMilliseconds(1),
                          TimeDelta::FromSeconds(10), 50);
}

void UmaHistogramTimes(const char* name, TimeDelta sample) {
  UmaHistogramCustomTimes(name, sample, TimeDelta::FromMilliseconds(1),
                          TimeDelta::FromSeconds(10), 50);
}

void UmaHistogramMediumTimes(const std::string& name, TimeDelta sample) {
  UmaHistogramCustomTimes(name, sample, TimeDelta::FromMilliseconds(1),
                          TimeDelta::FromMinutes(3), 50);
}

void UmaHistogramMediumTimes(const char* name, TimeDelta sample) {
  UmaHistogramCustomTimes(name, sample, TimeDelta::FromMilliseconds(1),
                          TimeDelta::FromMinutes(3), 50);
}

void UmaHistogramLongTimes(const std::string& name, TimeDelta sample) {
  UmaHistogramCustomTimes(name, sample, TimeDelta::FromMilliseconds(1),
                          TimeDelta::FromHours(1), 50);
}

void UmaHistogramLongTimes(const char* name, TimeDelta sample) {
  UmaHistogramCustomTimes(name, sample, TimeDelta::FromMilliseconds(1),
                          TimeDelta::FromHours(1), 50);
}

void UmaHistogramLongTimes100(const std::string& name, TimeDelta sample) {
  UmaHistogramCustomTimes(name, sample, TimeDelta::FromMilliseconds(1),
                          TimeDelta::FromHours(1), 100);
}

void UmaHistogramLongTimes100(const char* name, TimeDelta sample) {
  UmaHistogramCustomTimes(name, sample, TimeDelta::FromMilliseconds(1),
                          TimeDelta::FromHours(1), 100);
}

void UmaHistogramCustomMicrosecondsTimes(const std::string& name,
                                         TimeDelta sample,
                                         TimeDelta min,
                                         TimeDelta max,
                                         int buckets) {
  HistogramBase* histogram = Histogram::FactoryMicrosecondsTimeGet(
      name, min, max, buckets, HistogramBase::kUmaTargetedHistogramFlag);
  histogram->AddTimeMicrosecondsGranularity(sample);
}

void UmaHistogramCustomMicrosecondsTimes(const char* name,
                                         TimeDelta sample,
                                         TimeDelta min,
                                         TimeDelta max,
                                         int buckets) {
  HistogramBase* histogram = Histogram::FactoryMicrosecondsTimeGet(
      name, min, max, buckets, HistogramBase::kUmaTargetedHistogramFlag);
  histogram->AddTimeMicrosecondsGranularity(sample);
}

void UmaHistogramMicrosecondsTimes(const std::string& name, TimeDelta sample) {
  UmaHistogramCustomMicrosecondsTimes(name, sample,
                                      TimeDelta::FromMicroseconds(1),
                                      TimeDelta::FromSeconds(10), 50);
}

void UmaHistogramMicrosecondsTimes(const char* name, TimeDelta sample) {
  UmaHistogramCustomMicrosecondsTimes(name, sample,
                                      TimeDelta::FromMicroseconds(1),
                                      TimeDelta::FromSeconds(10), 50);
}

// TODO(crbug.com/983261) Remove this method after moving to
// UmaHistogramMicrosecondsTimes.
void UmaHistogramMicrosecondsTimesUnderTenMilliseconds(const std::string& name,
                                                       TimeDelta sample) {
  UmaHistogramCustomMicrosecondsTimes(name, sample,
                                      TimeDelta::FromMicroseconds(1),
                                      TimeDelta::FromMilliseconds(10), 50);
}

// TODO(crbug.com/983261) Remove this method after moving to
// UmaHistogramMicrosecondsTimes.
void UmaHistogramMicrosecondsTimesUnderTenMilliseconds(const char* name,
                                                       TimeDelta sample) {
  UmaHistogramCustomMicrosecondsTimes(name, sample,
                                      TimeDelta::FromMicroseconds(1),
                                      TimeDelta::FromMilliseconds(10), 50);
}

void UmaHistogramMemoryKB(const std::string& name, int sample) {
  UmaHistogramCustomCounts(name, sample, 1000, 500000, 50);
}

void UmaHistogramMemoryKB(const char* name, int sample) {
  UmaHistogramCustomCounts(name, sample, 1000, 500000, 50);
}

void UmaHistogramMemoryMB(const std::string& name, int sample) {
  UmaHistogramCustomCounts(name, sample, 1, 1000, 50);
}

void UmaHistogramMemoryMB(const char* name, int sample) {
  UmaHistogramCustomCounts(name, sample, 1, 1000, 50);
}

void UmaHistogramMemoryLargeMB(const std::string& name, int sample) {
  UmaHistogramCustomCounts(name, sample, 1, 64000, 100);
}

void UmaHistogramMemoryLargeMB(const char* name, int sample) {
  UmaHistogramCustomCounts(name, sample, 1, 64000, 100);
}

void UmaHistogramSparse(const std::string& name, int sample) {
  HistogramBase* histogram = SparseHistogram::FactoryGet(
      name, HistogramBase::kUmaTargetedHistogramFlag);
  histogram->Add(sample);
}

void UmaHistogramSparse(const char* name, int sample) {
  HistogramBase* histogram = SparseHistogram::FactoryGet(
      name, HistogramBase::kUmaTargetedHistogramFlag);
  histogram->Add(sample);
}

}  // namespace base
