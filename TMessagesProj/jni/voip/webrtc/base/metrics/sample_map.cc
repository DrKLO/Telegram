// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/metrics/sample_map.h"

#include "base/logging.h"
#include "base/numerics/safe_conversions.h"
#include "base/stl_util.h"

namespace base {

typedef HistogramBase::Count Count;
typedef HistogramBase::Sample Sample;

namespace {

// An iterator for going through a SampleMap. The logic here is identical
// to that of PersistentSampleMapIterator but with different data structures.
// Changes here likely need to be duplicated there.
class SampleMapIterator : public SampleCountIterator {
 public:
  typedef std::map<HistogramBase::Sample, HistogramBase::Count>
      SampleToCountMap;

  explicit SampleMapIterator(const SampleToCountMap& sample_counts);
  ~SampleMapIterator() override;

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

SampleMapIterator::SampleMapIterator(const SampleToCountMap& sample_counts)
    : iter_(sample_counts.begin()),
      end_(sample_counts.end()) {
  SkipEmptyBuckets();
}

SampleMapIterator::~SampleMapIterator() = default;

bool SampleMapIterator::Done() const {
  return iter_ == end_;
}

void SampleMapIterator::Next() {
  DCHECK(!Done());
  ++iter_;
  SkipEmptyBuckets();
}

void SampleMapIterator::Get(Sample* min, int64_t* max, Count* count) const {
  DCHECK(!Done());
  if (min)
    *min = iter_->first;
  if (max)
    *max = strict_cast<int64_t>(iter_->first) + 1;
  if (count)
    *count = iter_->second;
}

void SampleMapIterator::SkipEmptyBuckets() {
  while (!Done() && iter_->second == 0) {
    ++iter_;
  }
}

}  // namespace

SampleMap::SampleMap() : SampleMap(0) {}

SampleMap::SampleMap(uint64_t id) : HistogramSamples(id, new LocalMetadata()) {}

SampleMap::~SampleMap() {
  delete static_cast<LocalMetadata*>(meta());
}

void SampleMap::Accumulate(Sample value, Count count) {
  sample_counts_[value] += count;
  IncreaseSumAndCount(strict_cast<int64_t>(count) * value, count);
}

Count SampleMap::GetCount(Sample value) const {
  auto it = sample_counts_.find(value);
  if (it == sample_counts_.end())
    return 0;
  return it->second;
}

Count SampleMap::TotalCount() const {
  Count count = 0;
  for (const auto& entry : sample_counts_) {
    count += entry.second;
  }
  return count;
}

std::unique_ptr<SampleCountIterator> SampleMap::Iterator() const {
  return std::make_unique<SampleMapIterator>(sample_counts_);
}

bool SampleMap::AddSubtractImpl(SampleCountIterator* iter, Operator op) {
  Sample min;
  int64_t max;
  Count count;
  for (; !iter->Done(); iter->Next()) {
    iter->Get(&min, &max, &count);
    if (strict_cast<int64_t>(min) + 1 != max)
      return false;  // SparseHistogram only supports bucket with size 1.

    sample_counts_[min] += (op == HistogramSamples::ADD) ? count : -count;
  }
  return true;
}

}  // namespace base
