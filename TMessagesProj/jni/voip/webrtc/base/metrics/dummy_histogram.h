// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_METRICS_DUMMY_HISTOGRAM_H_
#define BASE_METRICS_DUMMY_HISTOGRAM_H_

#include <stdint.h>

#include <memory>
#include <string>

#include "base/base_export.h"
#include "base/metrics/histogram_base.h"
#include "base/no_destructor.h"

namespace base {

// DummyHistogram is used for mocking histogram objects for histograms that
// shouldn't be recorded. It doesn't do any actual processing.
class BASE_EXPORT DummyHistogram : public HistogramBase {
 public:
  static DummyHistogram* GetInstance();

  // HistogramBase:
  void CheckName(const StringPiece& name) const override {}
  uint64_t name_hash() const override;
  HistogramType GetHistogramType() const override;
  bool HasConstructionArguments(Sample expected_minimum,
                                Sample expected_maximum,
                                uint32_t expected_bucket_count) const override;
  void Add(Sample value) override {}
  void AddCount(Sample value, int count) override {}
  void AddSamples(const HistogramSamples& samples) override {}
  bool AddSamplesFromPickle(PickleIterator* iter) override;
  std::unique_ptr<HistogramSamples> SnapshotSamples() const override;
  std::unique_ptr<HistogramSamples> SnapshotDelta() override;
  std::unique_ptr<HistogramSamples> SnapshotFinalDelta() const override;
  void WriteHTMLGraph(std::string* output) const override {}
  void WriteAscii(std::string* output) const override {}

 protected:
  // HistogramBase:
  void SerializeInfoImpl(Pickle* pickle) const override {}
  void GetParameters(DictionaryValue* params) const override {}
  void GetCountAndBucketData(Count* count,
                             int64_t* sum,
                             ListValue* buckets) const override {}

 private:
  friend class NoDestructor<DummyHistogram>;

  DummyHistogram() : HistogramBase("dummy_histogram") {}
  ~DummyHistogram() override {}

  DISALLOW_COPY_AND_ASSIGN(DummyHistogram);
};

}  // namespace base

#endif  // BASE_METRICS_DUMMY_HISTOGRAM_H_
