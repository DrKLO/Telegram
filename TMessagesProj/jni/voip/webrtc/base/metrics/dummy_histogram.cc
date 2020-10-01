// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/metrics/dummy_histogram.h"

#include <memory>

#include "base/logging.h"
#include "base/metrics/histogram_samples.h"
#include "base/metrics/metrics_hashes.h"

namespace base {

namespace {

// Helper classes for DummyHistogram.
class DummySampleCountIterator : public SampleCountIterator {
 public:
  DummySampleCountIterator() {}
  ~DummySampleCountIterator() override {}

  // SampleCountIterator:
  bool Done() const override { return true; }
  void Next() override { NOTREACHED(); }
  void Get(HistogramBase::Sample* min,
           int64_t* max,
           HistogramBase::Count* count) const override {
    NOTREACHED();
  }

 private:
  DISALLOW_COPY_AND_ASSIGN(DummySampleCountIterator);
};

class DummyHistogramSamples : public HistogramSamples {
 public:
  explicit DummyHistogramSamples() : HistogramSamples(0, new LocalMetadata()) {}
  ~DummyHistogramSamples() override {
    delete static_cast<LocalMetadata*>(meta());
  }

  // HistogramSamples:
  void Accumulate(HistogramBase::Sample value,
                  HistogramBase::Count count) override {}
  HistogramBase::Count GetCount(HistogramBase::Sample value) const override {
    return HistogramBase::Count();
  }
  HistogramBase::Count TotalCount() const override {
    return HistogramBase::Count();
  }
  std::unique_ptr<SampleCountIterator> Iterator() const override {
    return std::make_unique<DummySampleCountIterator>();
  }
  bool AddSubtractImpl(SampleCountIterator* iter, Operator op) override {
    return true;
  }

 private:
  DISALLOW_COPY_AND_ASSIGN(DummyHistogramSamples);
};

}  // namespace

// static
DummyHistogram* DummyHistogram::GetInstance() {
  static base::NoDestructor<DummyHistogram> dummy_histogram;
  return dummy_histogram.get();
}

uint64_t DummyHistogram::name_hash() const {
  return HashMetricName(histogram_name());
}

HistogramType DummyHistogram::GetHistogramType() const {
  return DUMMY_HISTOGRAM;
}

bool DummyHistogram::HasConstructionArguments(
    Sample expected_minimum,
    Sample expected_maximum,
    uint32_t expected_bucket_count) const {
  return true;
}

bool DummyHistogram::AddSamplesFromPickle(PickleIterator* iter) {
  return true;
}

std::unique_ptr<HistogramSamples> DummyHistogram::SnapshotSamples() const {
  return std::make_unique<DummyHistogramSamples>();
}

std::unique_ptr<HistogramSamples> DummyHistogram::SnapshotDelta() {
  return std::make_unique<DummyHistogramSamples>();
}

std::unique_ptr<HistogramSamples> DummyHistogram::SnapshotFinalDelta() const {
  return std::make_unique<DummyHistogramSamples>();
}

}  // namespace base
