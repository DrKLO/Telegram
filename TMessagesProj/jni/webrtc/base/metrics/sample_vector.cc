// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/metrics/sample_vector.h"

#include "base/lazy_instance.h"
#include "base/logging.h"
#include "base/memory/ptr_util.h"
#include "base/metrics/persistent_memory_allocator.h"
#include "base/numerics/safe_conversions.h"
#include "base/synchronization/lock.h"
#include "base/threading/platform_thread.h"

// This SampleVector makes use of the single-sample embedded in the base
// HistogramSamples class. If the count is non-zero then there is guaranteed
// (within the bounds of "eventual consistency") to be no allocated external
// storage. Once the full counts storage is allocated, the single-sample must
// be extracted and disabled.

namespace base {

typedef HistogramBase::Count Count;
typedef HistogramBase::Sample Sample;

SampleVectorBase::SampleVectorBase(uint64_t id,
                                   Metadata* meta,
                                   const BucketRanges* bucket_ranges)
    : HistogramSamples(id, meta), bucket_ranges_(bucket_ranges) {
  CHECK_GE(bucket_ranges_->bucket_count(), 1u);
}

SampleVectorBase::~SampleVectorBase() = default;

void SampleVectorBase::Accumulate(Sample value, Count count) {
  const size_t bucket_index = GetBucketIndex(value);

  // Handle the single-sample case.
  if (!counts()) {
    // Try to accumulate the parameters into the single-count entry.
    if (AccumulateSingleSample(value, count, bucket_index)) {
      // A race condition could lead to a new single-sample being accumulated
      // above just after another thread executed the MountCountsStorage below.
      // Since it is mounted, it could be mounted elsewhere and have values
      // written to it. It's not allowed to have both a single-sample and
      // entries in the counts array so move the single-sample.
      if (counts())
        MoveSingleSampleToCounts();
      return;
    }

    // Need real storage to store both what was in the single-sample plus the
    // parameter information.
    MountCountsStorageAndMoveSingleSample();
  }

  // Handle the multi-sample case.
  Count new_value =
      subtle::NoBarrier_AtomicIncrement(&counts()[bucket_index], count);
  IncreaseSumAndCount(strict_cast<int64_t>(count) * value, count);

  // TODO(bcwhite) Remove after crbug.com/682680.
  Count old_value = new_value - count;
  if ((new_value >= 0) != (old_value >= 0) && count > 0)
    RecordNegativeSample(SAMPLES_ACCUMULATE_OVERFLOW, count);
}

Count SampleVectorBase::GetCount(Sample value) const {
  return GetCountAtIndex(GetBucketIndex(value));
}

Count SampleVectorBase::TotalCount() const {
  // Handle the single-sample case.
  SingleSample sample = single_sample().Load();
  if (sample.count != 0)
    return sample.count;

  // Handle the multi-sample case.
  if (counts() || MountExistingCountsStorage()) {
    Count count = 0;
    size_t size = counts_size();
    const HistogramBase::AtomicCount* counts_array = counts();
    for (size_t i = 0; i < size; ++i) {
      count += subtle::NoBarrier_Load(&counts_array[i]);
    }
    return count;
  }

  // And the no-value case.
  return 0;
}

Count SampleVectorBase::GetCountAtIndex(size_t bucket_index) const {
  DCHECK(bucket_index < counts_size());

  // Handle the single-sample case.
  SingleSample sample = single_sample().Load();
  if (sample.count != 0)
    return sample.bucket == bucket_index ? sample.count : 0;

  // Handle the multi-sample case.
  if (counts() || MountExistingCountsStorage())
    return subtle::NoBarrier_Load(&counts()[bucket_index]);

  // And the no-value case.
  return 0;
}

std::unique_ptr<SampleCountIterator> SampleVectorBase::Iterator() const {
  // Handle the single-sample case.
  SingleSample sample = single_sample().Load();
  if (sample.count != 0) {
    return std::make_unique<SingleSampleIterator>(
        bucket_ranges_->range(sample.bucket),
        bucket_ranges_->range(sample.bucket + 1), sample.count, sample.bucket);
  }

  // Handle the multi-sample case.
  if (counts() || MountExistingCountsStorage()) {
    return std::make_unique<SampleVectorIterator>(counts(), counts_size(),
                                                  bucket_ranges_);
  }

  // And the no-value case.
  return std::make_unique<SampleVectorIterator>(nullptr, 0, bucket_ranges_);
}

bool SampleVectorBase::AddSubtractImpl(SampleCountIterator* iter,
                                       HistogramSamples::Operator op) {
  // Stop now if there's nothing to do.
  if (iter->Done())
    return true;

  // Get the first value and its index.
  HistogramBase::Sample min;
  int64_t max;
  HistogramBase::Count count;
  iter->Get(&min, &max, &count);
  size_t dest_index = GetBucketIndex(min);

  // The destination must be a superset of the source meaning that though the
  // incoming ranges will find an exact match, the incoming bucket-index, if
  // it exists, may be offset from the destination bucket-index. Calculate
  // that offset of the passed iterator; there are are no overflow checks
  // because 2's compliment math will work it out in the end.
  //
  // Because GetBucketIndex() always returns the same true or false result for
  // a given iterator object, |index_offset| is either set here and used below,
  // or never set and never used. The compiler doesn't know this, though, which
  // is why it's necessary to initialize it to something.
  size_t index_offset = 0;
  size_t iter_index;
  if (iter->GetBucketIndex(&iter_index))
    index_offset = dest_index - iter_index;
  if (dest_index >= counts_size())
    return false;

  // Post-increment. Information about the current sample is not available
  // after this point.
  iter->Next();

  // Single-value storage is possible if there is no counts storage and the
  // retrieved entry is the only one in the iterator.
  if (!counts()) {
    if (iter->Done()) {
      // Don't call AccumulateSingleSample because that updates sum and count
      // which was already done by the caller of this method.
      if (single_sample().Accumulate(
              dest_index, op == HistogramSamples::ADD ? count : -count)) {
        // Handle race-condition that mounted counts storage between above and
        // here.
        if (counts())
          MoveSingleSampleToCounts();
        return true;
      }
    }

    // The counts storage will be needed to hold the multiple incoming values.
    MountCountsStorageAndMoveSingleSample();
  }

  // Go through the iterator and add the counts into correct bucket.
  while (true) {
    // Ensure that the sample's min/max match the ranges min/max.
    if (min != bucket_ranges_->range(dest_index) ||
        max != bucket_ranges_->range(dest_index + 1)) {
      NOTREACHED() << "sample=" << min << "," << max
                   << "; range=" << bucket_ranges_->range(dest_index) << ","
                   << bucket_ranges_->range(dest_index + 1);
      return false;
    }

    // Sample's bucket matches exactly. Adjust count.
    subtle::NoBarrier_AtomicIncrement(
        &counts()[dest_index], op == HistogramSamples::ADD ? count : -count);

    // Advance to the next iterable sample. See comments above for how
    // everything works.
    if (iter->Done())
      return true;
    iter->Get(&min, &max, &count);
    if (iter->GetBucketIndex(&iter_index)) {
      // Destination bucket is a known offset from the source bucket.
      dest_index = iter_index + index_offset;
    } else {
      // Destination bucket has to be determined anew each time.
      dest_index = GetBucketIndex(min);
    }
    if (dest_index >= counts_size())
      return false;
    iter->Next();
  }
}

// Use simple binary search.  This is very general, but there are better
// approaches if we knew that the buckets were linearly distributed.
size_t SampleVectorBase::GetBucketIndex(Sample value) const {
  size_t bucket_count = bucket_ranges_->bucket_count();
  CHECK_GE(bucket_count, 1u);
  CHECK_GE(value, bucket_ranges_->range(0));
  CHECK_LT(value, bucket_ranges_->range(bucket_count));

  size_t under = 0;
  size_t over = bucket_count;
  size_t mid;
  do {
    DCHECK_GE(over, under);
    mid = under + (over - under)/2;
    if (mid == under)
      break;
    if (bucket_ranges_->range(mid) <= value)
      under = mid;
    else
      over = mid;
  } while (true);

  DCHECK_LE(bucket_ranges_->range(mid), value);
  CHECK_GT(bucket_ranges_->range(mid + 1), value);
  return mid;
}

void SampleVectorBase::MoveSingleSampleToCounts() {
  DCHECK(counts());

  // Disable the single-sample since there is now counts storage for the data.
  SingleSample sample = single_sample().Extract(/*disable=*/true);

  // Stop here if there is no "count" as trying to find the bucket index of
  // an invalid (including zero) "value" will crash.
  if (sample.count == 0)
    return;

  // Move the value into storage. Sum and redundant-count already account
  // for this entry so no need to call IncreaseSumAndCount().
  subtle::NoBarrier_AtomicIncrement(&counts()[sample.bucket], sample.count);
}

void SampleVectorBase::MountCountsStorageAndMoveSingleSample() {
  // There are many SampleVector objects and the lock is needed very
  // infrequently (just when advancing from single-sample to multi-sample) so
  // define a single, global lock that all can use. This lock only prevents
  // concurrent entry into the code below; access and updates to |counts_|
  // still requires atomic operations.
  static LazyInstance<Lock>::Leaky counts_lock = LAZY_INSTANCE_INITIALIZER;
  if (subtle::NoBarrier_Load(&counts_) == 0) {
    AutoLock lock(counts_lock.Get());
    if (subtle::NoBarrier_Load(&counts_) == 0) {
      // Create the actual counts storage while the above lock is acquired.
      HistogramBase::Count* counts = CreateCountsStorageWhileLocked();
      DCHECK(counts);

      // Point |counts_| to the newly created storage. This is done while
      // locked to prevent possible concurrent calls to CreateCountsStorage
      // but, between that call and here, other threads could notice the
      // existence of the storage and race with this to set_counts(). That's
      // okay because (a) it's atomic and (b) it always writes the same value.
      set_counts(counts);
    }
  }

  // Move any single-sample into the newly mounted storage.
  MoveSingleSampleToCounts();
}

SampleVector::SampleVector(const BucketRanges* bucket_ranges)
    : SampleVector(0, bucket_ranges) {}

SampleVector::SampleVector(uint64_t id, const BucketRanges* bucket_ranges)
    : SampleVectorBase(id, new LocalMetadata(), bucket_ranges) {}

SampleVector::~SampleVector() {
  delete static_cast<LocalMetadata*>(meta());
}

bool SampleVector::MountExistingCountsStorage() const {
  // There is never any existing storage other than what is already in use.
  return counts() != nullptr;
}

HistogramBase::AtomicCount* SampleVector::CreateCountsStorageWhileLocked() {
  local_counts_.resize(counts_size());
  return &local_counts_[0];
}

PersistentSampleVector::PersistentSampleVector(
    uint64_t id,
    const BucketRanges* bucket_ranges,
    Metadata* meta,
    const DelayedPersistentAllocation& counts)
    : SampleVectorBase(id, meta, bucket_ranges), persistent_counts_(counts) {
  // Only mount the full storage if the single-sample has been disabled.
  // Otherwise, it is possible for this object instance to start using (empty)
  // storage that was created incidentally while another instance continues to
  // update to the single sample. This "incidental creation" can happen because
  // the memory is a DelayedPersistentAllocation which allows multiple memory
  // blocks within it and applies an all-or-nothing approach to the allocation.
  // Thus, a request elsewhere for one of the _other_ blocks would make _this_
  // block available even though nothing has explicitly requested it.
  //
  // Note that it's not possible for the ctor to mount existing storage and
  // move any single-sample to it because sometimes the persistent memory is
  // read-only. Only non-const methods (which assume that memory is read/write)
  // can do that.
  if (single_sample().IsDisabled()) {
    bool success = MountExistingCountsStorage();
    DCHECK(success);
  }
}

PersistentSampleVector::~PersistentSampleVector() = default;

bool PersistentSampleVector::MountExistingCountsStorage() const {
  // There is no early exit if counts is not yet mounted because, given that
  // this is a virtual function, it's more efficient to do that at the call-
  // site. There is no danger, however, should this get called anyway (perhaps
  // because of a race condition) because at worst the |counts_| value would
  // be over-written (in an atomic manner) with the exact same address.

  if (!persistent_counts_.reference())
    return false;  // Nothing to mount.

  // Mount the counts array in position.
  set_counts(
      static_cast<HistogramBase::AtomicCount*>(persistent_counts_.Get()));

  // The above shouldn't fail but can if the data is corrupt or incomplete.
  return counts() != nullptr;
}

HistogramBase::AtomicCount*
PersistentSampleVector::CreateCountsStorageWhileLocked() {
  void* mem = persistent_counts_.Get();
  if (!mem) {
    // The above shouldn't fail but can if Bad Things(tm) are occurring in the
    // persistent allocator. Crashing isn't a good option so instead just
    // allocate something from the heap and return that. There will be no
    // sharing or persistence but worse things are already happening.
    return new HistogramBase::AtomicCount[counts_size()];
  }

  return static_cast<HistogramBase::AtomicCount*>(mem);
}

SampleVectorIterator::SampleVectorIterator(
    const std::vector<HistogramBase::AtomicCount>* counts,
    const BucketRanges* bucket_ranges)
    : counts_(&(*counts)[0]),
      counts_size_(counts->size()),
      bucket_ranges_(bucket_ranges),
      index_(0) {
  DCHECK_GE(bucket_ranges_->bucket_count(), counts_size_);
  SkipEmptyBuckets();
}

SampleVectorIterator::SampleVectorIterator(
    const HistogramBase::AtomicCount* counts,
    size_t counts_size,
    const BucketRanges* bucket_ranges)
    : counts_(counts),
      counts_size_(counts_size),
      bucket_ranges_(bucket_ranges),
      index_(0) {
  DCHECK_GE(bucket_ranges_->bucket_count(), counts_size_);
  SkipEmptyBuckets();
}

SampleVectorIterator::~SampleVectorIterator() = default;

bool SampleVectorIterator::Done() const {
  return index_ >= counts_size_;
}

void SampleVectorIterator::Next() {
  DCHECK(!Done());
  index_++;
  SkipEmptyBuckets();
}

void SampleVectorIterator::Get(HistogramBase::Sample* min,
                               int64_t* max,
                               HistogramBase::Count* count) const {
  DCHECK(!Done());
  if (min != nullptr)
    *min = bucket_ranges_->range(index_);
  if (max != nullptr)
    *max = strict_cast<int64_t>(bucket_ranges_->range(index_ + 1));
  if (count != nullptr)
    *count = subtle::NoBarrier_Load(&counts_[index_]);
}

bool SampleVectorIterator::GetBucketIndex(size_t* index) const {
  DCHECK(!Done());
  if (index != nullptr)
    *index = index_;
  return true;
}

void SampleVectorIterator::SkipEmptyBuckets() {
  if (Done())
    return;

  while (index_ < counts_size_) {
    if (subtle::NoBarrier_Load(&counts_[index_]) != 0)
      return;
    index_++;
  }
}

}  // namespace base
