// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// PersistentSampleMap implements HistogramSamples interface. It is used
// by the SparseHistogram class to store samples in persistent memory which
// allows it to be shared between processes or live across restarts.

#ifndef BASE_METRICS_PERSISTENT_SAMPLE_MAP_H_
#define BASE_METRICS_PERSISTENT_SAMPLE_MAP_H_

#include <stdint.h>

#include <map>
#include <memory>

#include "base/compiler_specific.h"
#include "base/macros.h"
#include "base/metrics/histogram_base.h"
#include "base/metrics/histogram_samples.h"
#include "base/metrics/persistent_memory_allocator.h"

namespace base {

class PersistentHistogramAllocator;
class PersistentSampleMapRecords;

// The logic here is similar to that of SampleMap but with different data
// structures. Changes here likely need to be duplicated there.
class BASE_EXPORT PersistentSampleMap : public HistogramSamples {
 public:
  // Constructs a persistent sample map using a PersistentHistogramAllocator
  // as the data source for persistent records.
  PersistentSampleMap(uint64_t id,
                      PersistentHistogramAllocator* allocator,
                      Metadata* meta);

  ~PersistentSampleMap() override;

  // HistogramSamples:
  void Accumulate(HistogramBase::Sample value,
                  HistogramBase::Count count) override;
  HistogramBase::Count GetCount(HistogramBase::Sample value) const override;
  HistogramBase::Count TotalCount() const override;
  std::unique_ptr<SampleCountIterator> Iterator() const override;

  // Uses a persistent-memory |iterator| to locate and return information about
  // the next record holding information for a PersistentSampleMap. The record
  // could be for any Map so return the |sample_map_id| as well.
  static PersistentMemoryAllocator::Reference GetNextPersistentRecord(
      PersistentMemoryAllocator::Iterator& iterator,
      uint64_t* sample_map_id);

  // Creates a new record in an |allocator| storing count information for a
  // specific sample |value| of a histogram with the given |sample_map_id|.
  static PersistentMemoryAllocator::Reference CreatePersistentRecord(
      PersistentMemoryAllocator* allocator,
      uint64_t sample_map_id,
      HistogramBase::Sample value);

 protected:
  // Performs arithemetic. |op| is ADD or SUBTRACT.
  bool AddSubtractImpl(SampleCountIterator* iter, Operator op) override;

  // Gets a pointer to a "count" corresponding to a given |value|. Returns NULL
  // if sample does not exist.
  HistogramBase::Count* GetSampleCountStorage(HistogramBase::Sample value);

  // Gets a pointer to a "count" corresponding to a given |value|, creating
  // the sample (initialized to zero) if it does not already exists.
  HistogramBase::Count* GetOrCreateSampleCountStorage(
      HistogramBase::Sample value);

 private:
  // Gets the object that manages persistent records. This returns the
  // |records_| member after first initializing it if necessary.
  PersistentSampleMapRecords* GetRecords();

  // Imports samples from persistent memory by iterating over all sample
  // records found therein, adding them to the sample_counts_ map. If a
  // count for the sample |until_value| is found, stop the import and return
  // a pointer to that counter. If that value is not found, null will be
  // returned after all currently available samples have been loaded. Pass
  // true for |import_everything| to force the importing of all available
  // samples even if a match is found.
  HistogramBase::Count* ImportSamples(HistogramBase::Sample until_value,
                                      bool import_everything);

  // All created/loaded sample values and their associated counts. The storage
  // for the actual Count numbers is owned by the |records_| object and its
  // underlying allocator.
  std::map<HistogramBase::Sample, HistogramBase::Count*> sample_counts_;

  // The allocator that manages histograms inside persistent memory. This is
  // owned externally and is expected to live beyond the life of this object.
  PersistentHistogramAllocator* allocator_;

  // The object that manages sample records inside persistent memory. This is
  // owned by the |allocator_| object (above) and so, like it, is expected to
  // live beyond the life of this object. This value is lazily-initialized on
  // first use via the GetRecords() accessor method.
  PersistentSampleMapRecords* records_ = nullptr;

  DISALLOW_COPY_AND_ASSIGN(PersistentSampleMap);
};

}  // namespace base

#endif  // BASE_METRICS_PERSISTENT_SAMPLE_MAP_H_
