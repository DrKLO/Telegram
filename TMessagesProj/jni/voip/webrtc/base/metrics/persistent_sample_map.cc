// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/metrics/persistent_sample_map.h"

#include "base/logging.h"
#include "base/metrics/histogram_macros.h"
#include "base/metrics/persistent_histogram_allocator.h"
#include "base/numerics/safe_conversions.h"
#include "base/stl_util.h"

namespace base {

typedef HistogramBase::Count Count;
typedef HistogramBase::Sample Sample;

namespace {

// An iterator for going through a PersistentSampleMap. The logic here is
// identical to that of SampleMapIterator but with different data structures.
// Changes here likely need to be duplicated there.
class PersistentSampleMapIterator : public SampleCountIterator {
 public:
  typedef std::map<HistogramBase::Sample, HistogramBase::Count*>
      SampleToCountMap;

  explicit PersistentSampleMapIterator(const SampleToCountMap& sample_counts);
  ~PersistentSampleMapIterator() override;

  // SampleCountIterator:
  bool Done() const override;
  void Next() override;
  void Get(HistogramBase::Sample* min,
           int64_t* max,
           HistogramBase::Count* count) const override;

 private:
  void SkipEmptyBuckets();

  SampleToCountMap::const_iterator iter_;
  const SampleToCountMap::const_iterator end_;
};

PersistentSampleMapIterator::PersistentSampleMapIterator(
    const SampleToCountMap& sample_counts)
    : iter_(sample_counts.begin()),
      end_(sample_counts.end()) {
  SkipEmptyBuckets();
}

PersistentSampleMapIterator::~PersistentSampleMapIterator() = default;

bool PersistentSampleMapIterator::Done() const {
  return iter_ == end_;
}

void PersistentSampleMapIterator::Next() {
  DCHECK(!Done());
  ++iter_;
  SkipEmptyBuckets();
}

void PersistentSampleMapIterator::Get(Sample* min,
                                      int64_t* max,
                                      Count* count) const {
  DCHECK(!Done());
  if (min)
    *min = iter_->first;
  if (max)
    *max = strict_cast<int64_t>(iter_->first) + 1;
  if (count)
    *count = *iter_->second;
}

void PersistentSampleMapIterator::SkipEmptyBuckets() {
  while (!Done() && *iter_->second == 0) {
    ++iter_;
  }
}

// This structure holds an entry for a PersistentSampleMap within a persistent
// memory allocator. The "id" must be unique across all maps held by an
// allocator or they will get attached to the wrong sample map.
struct SampleRecord {
  // SHA1(SampleRecord): Increment this if structure changes!
  static constexpr uint32_t kPersistentTypeId = 0x8FE6A69F + 1;

  // Expected size for 32/64-bit check.
  static constexpr size_t kExpectedInstanceSize = 16;

  uint64_t id;   // Unique identifier of owner.
  Sample value;  // The value for which this record holds a count.
  Count count;   // The count associated with the above value.
};

}  // namespace

PersistentSampleMap::PersistentSampleMap(
    uint64_t id,
    PersistentHistogramAllocator* allocator,
    Metadata* meta)
    : HistogramSamples(id, meta), allocator_(allocator) {}

PersistentSampleMap::~PersistentSampleMap() {
  if (records_)
    records_->Release(this);
}

void PersistentSampleMap::Accumulate(Sample value, Count count) {
#if 0  // TODO(bcwhite) Re-enable efficient version after crbug.com/682680.
  *GetOrCreateSampleCountStorage(value) += count;
#else
  Count* local_count_ptr = GetOrCreateSampleCountStorage(value);
  if (count < 0) {
    if (*local_count_ptr < -count)
      RecordNegativeSample(SAMPLES_ACCUMULATE_WENT_NEGATIVE, -count);
    else
      RecordNegativeSample(SAMPLES_ACCUMULATE_NEGATIVE_COUNT, -count);
    *local_count_ptr += count;
  } else {
    Sample old_value = *local_count_ptr;
    Sample new_value = old_value + count;
    *local_count_ptr = new_value;
    if ((new_value >= 0) != (old_value >= 0))
      RecordNegativeSample(SAMPLES_ACCUMULATE_OVERFLOW, count);
  }
#endif
  IncreaseSumAndCount(strict_cast<int64_t>(count) * value, count);
}

Count PersistentSampleMap::GetCount(Sample value) const {
  // Have to override "const" to make sure all samples have been loaded before
  // being able to know what value to return.
  Count* count_pointer =
      const_cast<PersistentSampleMap*>(this)->GetSampleCountStorage(value);
  return count_pointer ? *count_pointer : 0;
}

Count PersistentSampleMap::TotalCount() const {
  // Have to override "const" in order to make sure all samples have been
  // loaded before trying to iterate over the map.
  const_cast<PersistentSampleMap*>(this)->ImportSamples(-1, true);

  Count count = 0;
  for (const auto& entry : sample_counts_) {
    count += *entry.second;
  }
  return count;
}

std::unique_ptr<SampleCountIterator> PersistentSampleMap::Iterator() const {
  // Have to override "const" in order to make sure all samples have been
  // loaded before trying to iterate over the map.
  const_cast<PersistentSampleMap*>(this)->ImportSamples(-1, true);
  return std::make_unique<PersistentSampleMapIterator>(sample_counts_);
}

// static
PersistentMemoryAllocator::Reference
PersistentSampleMap::GetNextPersistentRecord(
    PersistentMemoryAllocator::Iterator& iterator,
    uint64_t* sample_map_id) {
  const SampleRecord* record = iterator.GetNextOfObject<SampleRecord>();
  if (!record)
    return 0;

  *sample_map_id = record->id;
  return iterator.GetAsReference(record);
}

// static
PersistentMemoryAllocator::Reference
PersistentSampleMap::CreatePersistentRecord(
    PersistentMemoryAllocator* allocator,
    uint64_t sample_map_id,
    Sample value) {
  SampleRecord* record = allocator->New<SampleRecord>();
  if (!record) {
    NOTREACHED() << "full=" << allocator->IsFull()
                 << ", corrupt=" << allocator->IsCorrupt();
    return 0;
  }

  record->id = sample_map_id;
  record->value = value;
  record->count = 0;

  PersistentMemoryAllocator::Reference ref = allocator->GetAsReference(record);
  allocator->MakeIterable(ref);
  return ref;
}

bool PersistentSampleMap::AddSubtractImpl(SampleCountIterator* iter,
                                          Operator op) {
  Sample min;
  int64_t max;
  Count count;
  for (; !iter->Done(); iter->Next()) {
    iter->Get(&min, &max, &count);
    if (count == 0)
      continue;
    if (strict_cast<int64_t>(min) + 1 != max)
      return false;  // SparseHistogram only supports bucket with size 1.
    *GetOrCreateSampleCountStorage(min) +=
        (op == HistogramSamples::ADD) ? count : -count;
  }
  return true;
}

Count* PersistentSampleMap::GetSampleCountStorage(Sample value) {
  // If |value| is already in the map, just return that.
  auto it = sample_counts_.find(value);
  if (it != sample_counts_.end())
    return it->second;

  // Import any new samples from persistent memory looking for the value.
  return ImportSamples(value, false);
}

Count* PersistentSampleMap::GetOrCreateSampleCountStorage(Sample value) {
  // Get any existing count storage.
  Count* count_pointer = GetSampleCountStorage(value);
  if (count_pointer)
    return count_pointer;

  // Create a new record in persistent memory for the value. |records_| will
  // have been initialized by the GetSampleCountStorage() call above.
  DCHECK(records_);
  PersistentMemoryAllocator::Reference ref = records_->CreateNew(value);
  if (!ref) {
    // If a new record could not be created then the underlying allocator is
    // full or corrupt. Instead, allocate the counter from the heap. This
    // sample will not be persistent, will not be shared, and will leak...
    // but it's better than crashing.
    count_pointer = new Count(0);
    sample_counts_[value] = count_pointer;
    return count_pointer;
  }

  // A race condition between two independent processes (i.e. two independent
  // histogram objects sharing the same sample data) could cause two of the
  // above records to be created. The allocator, however, forces a strict
  // ordering on iterable objects so use the import method to actually add the
  // just-created record. This ensures that all PersistentSampleMap objects
  // will always use the same record, whichever was first made iterable.
  // Thread-safety within a process where multiple threads use the same
  // histogram object is delegated to the controlling histogram object which,
  // for sparse histograms, is a lock object.
  count_pointer = ImportSamples(value, false);
  DCHECK(count_pointer);
  return count_pointer;
}

PersistentSampleMapRecords* PersistentSampleMap::GetRecords() {
  // The |records_| pointer is lazily fetched from the |allocator_| only on
  // first use. Sometimes duplicate histograms are created by race conditions
  // and if both were to grab the records object, there would be a conflict.
  // Use of a histogram, and thus a call to this method, won't occur until
  // after the histogram has been de-dup'd.
  if (!records_)
    records_ = allocator_->UseSampleMapRecords(id(), this);
  return records_;
}

Count* PersistentSampleMap::ImportSamples(Sample until_value,
                                          bool import_everything) {
  Count* found_count = nullptr;
  PersistentMemoryAllocator::Reference ref;
  PersistentSampleMapRecords* records = GetRecords();
  while ((ref = records->GetNext()) != 0) {
    SampleRecord* record = records->GetAsObject<SampleRecord>(ref);
    if (!record)
      continue;

    DCHECK_EQ(id(), record->id);

    // Check if the record's value is already known.
    if (!Contains(sample_counts_, record->value)) {
      // No: Add it to map of known values.
      sample_counts_[record->value] = &record->count;
    } else {
      // Yes: Ignore it; it's a duplicate caused by a race condition -- see
      // code & comment in GetOrCreateSampleCountStorage() for details.
      // Check that nothing ever operated on the duplicate record.
      DCHECK_EQ(0, record->count);
    }

    // Check if it's the value being searched for and, if so, keep a pointer
    // to return later. Stop here unless everything is being imported.
    // Because race conditions can cause multiple records for a single value,
    // be sure to return the first one found.
    if (record->value == until_value) {
      if (!found_count)
        found_count = &record->count;
      if (!import_everything)
        break;
    }
  }

  return found_count;
}

}  // namespace base
