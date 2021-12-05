// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// SampleVector implements HistogramSamples interface. It is used by all
// Histogram based classes to store samples.

#ifndef BASE_METRICS_SAMPLE_VECTOR_H_
#define BASE_METRICS_SAMPLE_VECTOR_H_

#include <stddef.h>
#include <stdint.h>

#include <memory>
#include <vector>

#include "base/atomicops.h"
#include "base/compiler_specific.h"
#include "base/gtest_prod_util.h"
#include "base/macros.h"
#include "base/metrics/bucket_ranges.h"
#include "base/metrics/histogram_base.h"
#include "base/metrics/histogram_samples.h"
#include "base/metrics/persistent_memory_allocator.h"

namespace base {

class BucketRanges;

class BASE_EXPORT SampleVectorBase : public HistogramSamples {
 public:
  SampleVectorBase(uint64_t id,
                   Metadata* meta,
                   const BucketRanges* bucket_ranges);
  ~SampleVectorBase() override;

  // HistogramSamples:
  void Accumulate(HistogramBase::Sample value,
                  HistogramBase::Count count) override;
  HistogramBase::Count GetCount(HistogramBase::Sample value) const override;
  HistogramBase::Count TotalCount() const override;
  std::unique_ptr<SampleCountIterator> Iterator() const override;

  // Get count of a specific bucket.
  HistogramBase::Count GetCountAtIndex(size_t bucket_index) const;

  // Access the bucket ranges held externally.
  const BucketRanges* bucket_ranges() const { return bucket_ranges_; }

 protected:
  bool AddSubtractImpl(
      SampleCountIterator* iter,
      HistogramSamples::Operator op) override;  // |op| is ADD or SUBTRACT.

  virtual size_t GetBucketIndex(HistogramBase::Sample value) const;

  // Moves the single-sample value to a mounted "counts" array.
  void MoveSingleSampleToCounts();

  // Mounts (creating if necessary) an array of "counts" for multi-value
  // storage.
  void MountCountsStorageAndMoveSingleSample();

  // Mounts "counts" storage that already exists. This does not attempt to move
  // any single-sample information to that storage as that would violate the
  // "const" restriction that is often used to indicate read-only memory.
  virtual bool MountExistingCountsStorage() const = 0;

  // Creates "counts" storage and returns a pointer to it. Ownership of the
  // array remains with the called method but will never change. This must be
  // called while some sort of lock is held to prevent reentry.
  virtual HistogramBase::Count* CreateCountsStorageWhileLocked() = 0;

  HistogramBase::AtomicCount* counts() {
    return reinterpret_cast<HistogramBase::AtomicCount*>(
        subtle::Acquire_Load(&counts_));
  }

  const HistogramBase::AtomicCount* counts() const {
    return reinterpret_cast<HistogramBase::AtomicCount*>(
        subtle::Acquire_Load(&counts_));
  }

  void set_counts(const HistogramBase::AtomicCount* counts) const {
    subtle::Release_Store(&counts_, reinterpret_cast<uintptr_t>(counts));
  }

  size_t counts_size() const { return bucket_ranges_->bucket_count(); }

 private:
  friend class SampleVectorTest;
  FRIEND_TEST_ALL_PREFIXES(HistogramTest, CorruptSampleCounts);
  FRIEND_TEST_ALL_PREFIXES(SharedHistogramTest, CorruptSampleCounts);

  // |counts_| is actually a pointer to a HistogramBase::AtomicCount array but
  // is held as an AtomicWord for concurrency reasons. When combined with the
  // single_sample held in the metadata, there are four possible states:
  //   1) single_sample == zero, counts_ == null
  //   2) single_sample != zero, counts_ == null
  //   3) single_sample != zero, counts_ != null BUT IS EMPTY
  //   4) single_sample == zero, counts_ != null and may have data
  // Once |counts_| is set, it can never revert and any existing single-sample
  // must be moved to this storage. It is mutable because changing it doesn't
  // change the (const) data but must adapt if a non-const object causes the
  // storage to be allocated and updated.
  mutable subtle::AtomicWord counts_ = 0;

  // Shares the same BucketRanges with Histogram object.
  const BucketRanges* const bucket_ranges_;

  DISALLOW_COPY_AND_ASSIGN(SampleVectorBase);
};

// A sample vector that uses local memory for the counts array.
class BASE_EXPORT SampleVector : public SampleVectorBase {
 public:
  explicit SampleVector(const BucketRanges* bucket_ranges);
  SampleVector(uint64_t id, const BucketRanges* bucket_ranges);
  ~SampleVector() override;

 private:
  // SampleVectorBase:
  bool MountExistingCountsStorage() const override;
  HistogramBase::Count* CreateCountsStorageWhileLocked() override;

  // Simple local storage for counts.
  mutable std::vector<HistogramBase::AtomicCount> local_counts_;

  DISALLOW_COPY_AND_ASSIGN(SampleVector);
};

// A sample vector that uses persistent memory for the counts array.
class BASE_EXPORT PersistentSampleVector : public SampleVectorBase {
 public:
  PersistentSampleVector(uint64_t id,
                         const BucketRanges* bucket_ranges,
                         Metadata* meta,
                         const DelayedPersistentAllocation& counts);
  ~PersistentSampleVector() override;

 private:
  // SampleVectorBase:
  bool MountExistingCountsStorage() const override;
  HistogramBase::Count* CreateCountsStorageWhileLocked() override;

  // Persistent storage for counts.
  DelayedPersistentAllocation persistent_counts_;

  DISALLOW_COPY_AND_ASSIGN(PersistentSampleVector);
};

// An iterator for sample vectors. This could be defined privately in the .cc
// file but is here for easy testing.
class BASE_EXPORT SampleVectorIterator : public SampleCountIterator {
 public:
  SampleVectorIterator(const std::vector<HistogramBase::AtomicCount>* counts,
                       const BucketRanges* bucket_ranges);
  SampleVectorIterator(const HistogramBase::AtomicCount* counts,
                       size_t counts_size,
                       const BucketRanges* bucket_ranges);
  ~SampleVectorIterator() override;

  // SampleCountIterator implementation:
  bool Done() const override;
  void Next() override;
  void Get(HistogramBase::Sample* min,
           int64_t* max,
           HistogramBase::Count* count) const override;

  // SampleVector uses predefined buckets, so iterator can return bucket index.
  bool GetBucketIndex(size_t* index) const override;

 private:
  void SkipEmptyBuckets();

  const HistogramBase::AtomicCount* counts_;
  size_t counts_size_;
  const BucketRanges* bucket_ranges_;

  size_t index_;
};

}  // namespace base

#endif  // BASE_METRICS_SAMPLE_VECTOR_H_
