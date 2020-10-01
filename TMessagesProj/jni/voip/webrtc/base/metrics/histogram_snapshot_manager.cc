// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/metrics/histogram_snapshot_manager.h"

#include <memory>

#include "base/debug/alias.h"
#include "base/metrics/histogram_flattener.h"
#include "base/metrics/histogram_samples.h"
#include "base/metrics/statistics_recorder.h"
#include "base/stl_util.h"

namespace base {

namespace {

// A simple object to set an "active" flag and clear it upon destruction. It is
// an error if the flag is already set.
class MakeActive {
 public:
  MakeActive(std::atomic<bool>* is_active) : is_active_(is_active) {
    bool was_active = is_active_->exchange(true, std::memory_order_relaxed);
    CHECK(!was_active);
  }
  ~MakeActive() { is_active_->store(false, std::memory_order_relaxed); }

 private:
  std::atomic<bool>* is_active_;

  DISALLOW_COPY_AND_ASSIGN(MakeActive);
};

}  // namespace

HistogramSnapshotManager::HistogramSnapshotManager(
    HistogramFlattener* histogram_flattener)
    : histogram_flattener_(histogram_flattener) {
  DCHECK(histogram_flattener_);
  is_active_.store(false, std::memory_order_relaxed);
}

HistogramSnapshotManager::~HistogramSnapshotManager() = default;

void HistogramSnapshotManager::PrepareDeltas(
    const std::vector<HistogramBase*>& histograms,
    HistogramBase::Flags flags_to_set,
    HistogramBase::Flags required_flags) {
  for (HistogramBase* const histogram : histograms) {
    histogram->SetFlags(flags_to_set);
    if ((histogram->flags() & required_flags) == required_flags)
      PrepareDelta(histogram);
  }
}

void HistogramSnapshotManager::PrepareDelta(HistogramBase* histogram) {
  histogram->ValidateHistogramContents();
  PrepareSamples(histogram, histogram->SnapshotDelta());
}

void HistogramSnapshotManager::PrepareFinalDelta(
    const HistogramBase* histogram) {
  histogram->ValidateHistogramContents();
  PrepareSamples(histogram, histogram->SnapshotFinalDelta());
}

void HistogramSnapshotManager::PrepareSamples(
    const HistogramBase* histogram,
    std::unique_ptr<HistogramSamples> samples) {
  DCHECK(histogram_flattener_);

  // Ensure that there is no concurrent access going on while accessing the
  // set of known histograms. The flag will be reset when this object goes
  // out of scope.
  MakeActive make_active(&is_active_);

  // Get information known about this histogram. If it did not previously
  // exist, one will be created and initialized.
  SampleInfo* sample_info = &known_histograms_[histogram->name_hash()];

  // Crash if we detect that our histograms have been overwritten.  This may be
  // a fair distance from the memory smasher, but we hope to correlate these
  // crashes with other events, such as plugins, or usage patterns, etc.
  uint32_t corruption = histogram->FindCorruption(*samples);
  if (HistogramBase::BUCKET_ORDER_ERROR & corruption) {
    // Extract fields useful during debug.
    const BucketRanges* ranges =
        static_cast<const Histogram*>(histogram)->bucket_ranges();
    uint32_t ranges_checksum = ranges->checksum();
    uint32_t ranges_calc_checksum = ranges->CalculateChecksum();
    int32_t flags = histogram->flags();
    // The checksum should have caught this, so crash separately if it didn't.
    CHECK_NE(0U, HistogramBase::RANGE_CHECKSUM_ERROR & corruption);
    CHECK(false);  // Crash for the bucket order corruption.
    // Ensure that compiler keeps around pointers to |histogram| and its
    // internal |bucket_ranges_| for any minidumps.
    base::debug::Alias(&ranges_checksum);
    base::debug::Alias(&ranges_calc_checksum);
    base::debug::Alias(&flags);
  }
  // Checksum corruption might not have caused order corruption.
  CHECK_EQ(0U, HistogramBase::RANGE_CHECKSUM_ERROR & corruption);

  // Note, at this point corruption can only be COUNT_HIGH_ERROR or
  // COUNT_LOW_ERROR and they never arise together, so we don't need to extract
  // bits from corruption.
  if (corruption) {
    DLOG(ERROR) << "Histogram: \"" << histogram->histogram_name()
                << "\" has data corruption: " << corruption;
    // Don't record corrupt data to metrics services.
    const uint32_t old_corruption = sample_info->inconsistencies;
    if (old_corruption == (corruption | old_corruption))
      return;  // We've already seen this corruption for this histogram.
    sample_info->inconsistencies |= corruption;
    return;
  }

  if (samples->TotalCount() > 0)
    histogram_flattener_->RecordDelta(*histogram, *samples);
}

}  // namespace base
