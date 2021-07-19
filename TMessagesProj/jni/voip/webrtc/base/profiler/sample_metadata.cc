// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/profiler/sample_metadata.h"

#include "base/metrics/metrics_hashes.h"
#include "base/no_destructor.h"
#include "base/profiler/stack_sampling_profiler.h"

namespace base {

ScopedSampleMetadata::ScopedSampleMetadata(StringPiece name, int64_t value)
    : name_hash_(HashMetricName(name)) {
  GetSampleMetadataRecorder()->Set(name_hash_, nullopt, value);
}

ScopedSampleMetadata::ScopedSampleMetadata(StringPiece name,
                                           int64_t key,
                                           int64_t value)
    : name_hash_(HashMetricName(name)), key_(key) {
  GetSampleMetadataRecorder()->Set(name_hash_, key, value);
}

ScopedSampleMetadata::~ScopedSampleMetadata() {
  GetSampleMetadataRecorder()->Remove(name_hash_, key_);
}

void SetSampleMetadata(StringPiece name, int64_t value) {
  GetSampleMetadataRecorder()->Set(HashMetricName(name), nullopt, value);
}

void SetSampleMetadata(StringPiece name, int64_t key, int64_t value) {
  GetSampleMetadataRecorder()->Set(HashMetricName(name), key, value);
}

void RemoveSampleMetadata(StringPiece name) {
  GetSampleMetadataRecorder()->Remove(HashMetricName(name), nullopt);
}

void RemoveSampleMetadata(StringPiece name, int64_t key) {
  GetSampleMetadataRecorder()->Remove(HashMetricName(name), key);
}

// This function is friended by StackSamplingProfiler so must live directly in
// the base namespace.
void ApplyMetadataToPastSamplesImpl(TimeTicks period_start,
                                    TimeTicks period_end,
                                    int64_t name_hash,
                                    Optional<int64_t> key,
                                    int64_t value) {
  StackSamplingProfiler::ApplyMetadataToPastSamples(period_start, period_end,
                                                    name_hash, key, value);
}

void ApplyMetadataToPastSamples(TimeTicks period_start,
                                TimeTicks period_end,
                                StringPiece name,
                                int64_t value) {
  return ApplyMetadataToPastSamplesImpl(period_start, period_end,
                                        HashMetricName(name), nullopt, value);
}

void ApplyMetadataToPastSamples(TimeTicks period_start,
                                TimeTicks period_end,
                                StringPiece name,
                                int64_t key,
                                int64_t value) {
  return ApplyMetadataToPastSamplesImpl(period_start, period_end,
                                        HashMetricName(name), key, value);
}

MetadataRecorder* GetSampleMetadataRecorder() {
  static NoDestructor<MetadataRecorder> instance;
  return instance.get();
}

}  // namespace base
