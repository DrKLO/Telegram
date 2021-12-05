// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_METRICS_SPARSE_HISTOGRAM_H_
#define BASE_METRICS_SPARSE_HISTOGRAM_H_

#include <stddef.h>
#include <stdint.h>

#include <map>
#include <memory>
#include <string>

#include "base/base_export.h"
#include "base/macros.h"
#include "base/metrics/histogram_base.h"
#include "base/metrics/histogram_samples.h"
#include "base/synchronization/lock.h"

namespace base {

class HistogramSamples;
class PersistentHistogramAllocator;
class Pickle;
class PickleIterator;

class BASE_EXPORT SparseHistogram : public HistogramBase {
 public:
  // If there's one with same name, return the existing one. If not, create a
  // new one.
  static HistogramBase* FactoryGet(const std::string& name, int32_t flags);

  // Create a histogram using data in persistent storage. The allocator must
  // live longer than the created sparse histogram.
  static std::unique_ptr<HistogramBase> PersistentCreate(
      PersistentHistogramAllocator* allocator,
      const char* name,
      HistogramSamples::Metadata* meta,
      HistogramSamples::Metadata* logged_meta);

  ~SparseHistogram() override;

  // HistogramBase implementation:
  uint64_t name_hash() const override;
  HistogramType GetHistogramType() const override;
  bool HasConstructionArguments(Sample expected_minimum,
                                Sample expected_maximum,
                                uint32_t expected_bucket_count) const override;
  void Add(Sample value) override;
  void AddCount(Sample value, int count) override;
  void AddSamples(const HistogramSamples& samples) override;
  bool AddSamplesFromPickle(base::PickleIterator* iter) override;
  std::unique_ptr<HistogramSamples> SnapshotSamples() const override;
  std::unique_ptr<HistogramSamples> SnapshotDelta() override;
  std::unique_ptr<HistogramSamples> SnapshotFinalDelta() const override;
  void WriteHTMLGraph(std::string* output) const override;
  void WriteAscii(std::string* output) const override;

 protected:
  // HistogramBase implementation:
  void SerializeInfoImpl(base::Pickle* pickle) const override;

 private:
  // Clients should always use FactoryGet to create SparseHistogram.
  explicit SparseHistogram(const char* name);

  SparseHistogram(PersistentHistogramAllocator* allocator,
                  const char* name,
                  HistogramSamples::Metadata* meta,
                  HistogramSamples::Metadata* logged_meta);

  friend BASE_EXPORT HistogramBase* DeserializeHistogramInfo(
      base::PickleIterator* iter);
  static HistogramBase* DeserializeInfoImpl(base::PickleIterator* iter);

  void GetParameters(DictionaryValue* params) const override;
  void GetCountAndBucketData(Count* count,
                             int64_t* sum,
                             ListValue* buckets) const override;

  // Helpers for emitting Ascii graphic.  Each method appends data to output.
  void WriteAsciiBody(const HistogramSamples& snapshot,
                      bool graph_it,
                      const std::string& newline,
                      std::string* output) const;

  // Write a common header message describing this histogram.
  void WriteAsciiHeader(const HistogramSamples& snapshot,
                        std::string* output) const;

  // For constuctor calling.
  friend class SparseHistogramTest;

  // Protects access to |samples_|.
  mutable base::Lock lock_;

  // Flag to indicate if PrepareFinalDelta has been previously called.
  mutable bool final_delta_created_ = false;

  std::unique_ptr<HistogramSamples> unlogged_samples_;
  std::unique_ptr<HistogramSamples> logged_samples_;

  DISALLOW_COPY_AND_ASSIGN(SparseHistogram);
};

}  // namespace base

#endif  // BASE_METRICS_SPARSE_HISTOGRAM_H_
