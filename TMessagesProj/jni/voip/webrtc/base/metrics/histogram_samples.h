// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_METRICS_HISTOGRAM_SAMPLES_H_
#define BASE_METRICS_HISTOGRAM_SAMPLES_H_

#include <stddef.h>
#include <stdint.h>

#include <limits>
#include <memory>

#include "base/atomicops.h"
#include "base/macros.h"
#include "base/metrics/histogram_base.h"

namespace base {

class Pickle;
class PickleIterator;
class SampleCountIterator;

// HistogramSamples is a container storing all samples of a histogram. All
// elements must be of a fixed width to ensure 32/64-bit interoperability.
// If this structure changes, bump the version number for kTypeIdHistogram
// in persistent_histogram_allocator.cc.
//
// Note that though these samples are individually consistent (through the use
// of atomic operations on the counts), there is only "eventual consistency"
// overall when multiple threads are accessing this data. That means that the
// sum, redundant-count, etc. could be momentarily out-of-sync with the stored
// counts but will settle to a consistent "steady state" once all threads have
// exited this code.
class BASE_EXPORT HistogramSamples {
 public:
  // A single bucket and count. To fit within a single atomic on 32-bit build
  // architectures, both |bucket| and |count| are limited in size to 16 bits.
  // This limits the functionality somewhat but if an entry can't fit then
  // the full array of samples can be allocated and used.
  struct SingleSample {
    uint16_t bucket;
    uint16_t count;
  };

  // A structure for managing an atomic single sample. Because this is generally
  // used in association with other atomic values, the defined methods use
  // acquire/release operations to guarantee ordering with outside values.
  union BASE_EXPORT AtomicSingleSample {
    AtomicSingleSample() : as_atomic(0) {}
    AtomicSingleSample(subtle::Atomic32 rhs) : as_atomic(rhs) {}

    // Returns the single sample in an atomic manner. This in an "acquire"
    // load. The returned sample isn't shared and thus its fields can be safely
    // accessed.
    SingleSample Load() const;

    // Extracts the single sample in an atomic manner. If |disable| is true
    // then this object will be set so it will never accumulate another value.
    // This is "no barrier" so doesn't enforce ordering with other atomic ops.
    SingleSample Extract(bool disable);

    // Adds a given count to the held bucket. If not possible, it returns false
    // and leaves the parts unchanged. Once extracted/disabled, this always
    // returns false. This in an "acquire/release" operation.
    bool Accumulate(size_t bucket, HistogramBase::Count count);

    // Returns if the sample has been "disabled" (via Extract) and thus not
    // allowed to accept further accumulation.
    bool IsDisabled() const;

   private:
    // union field: The actual sample bucket and count.
    SingleSample as_parts;

    // union field: The sample as an atomic value. Atomic64 would provide
    // more flexibility but isn't available on all builds. This can hold a
    // special, internal "disabled" value indicating that it must not accept
    // further accumulation.
    subtle::Atomic32 as_atomic;
  };

  // A structure of information about the data, common to all sample containers.
  // Because of how this is used in persistent memory, it must be a POD object
  // that makes sense when initialized to all zeros.
  struct Metadata {
    // Expected size for 32/64-bit check.
    static constexpr size_t kExpectedInstanceSize = 24;

    // Initialized when the sample-set is first created with a value provided
    // by the caller. It is generally used to identify the sample-set across
    // threads and processes, though not necessarily uniquely as it is possible
    // to have multiple sample-sets representing subsets of the data.
    uint64_t id;

    // The sum of all the entries, effectivly the sum(sample * count) for
    // all samples. Despite being atomic, no guarantees are made on the
    // accuracy of this value; there may be races during histogram
    // accumulation and snapshotting that we choose to accept. It should
    // be treated as approximate.
#ifdef ARCH_CPU_64_BITS
    subtle::Atomic64 sum;
#else
    // 32-bit systems don't have atomic 64-bit operations. Use a basic type
    // and don't worry about "shearing".
    int64_t sum;
#endif

    // A "redundant" count helps identify memory corruption. It redundantly
    // stores the total number of samples accumulated in the histogram. We
    // can compare this count to the sum of the counts (TotalCount() function),
    // and detect problems. Note, depending on the implementation of different
    // histogram types, there might be races during histogram accumulation
    // and snapshotting that we choose to accept. In this case, the tallies
    // might mismatch even when no memory corruption has happened.
    HistogramBase::AtomicCount redundant_count;

    // A single histogram value and associated count. This allows histograms
    // that typically report only a single value to not require full storage
    // to be allocated.
    AtomicSingleSample single_sample;  // 32 bits
  };

  // Because structures held in persistent memory must be POD, there can be no
  // default constructor to clear the fields. This derived class exists just
  // to clear them when being allocated on the heap.
  struct BASE_EXPORT LocalMetadata : Metadata {
    LocalMetadata();
  };

  HistogramSamples(uint64_t id, Metadata* meta);
  virtual ~HistogramSamples();

  virtual void Accumulate(HistogramBase::Sample value,
                          HistogramBase::Count count) = 0;
  virtual HistogramBase::Count GetCount(HistogramBase::Sample value) const = 0;
  virtual HistogramBase::Count TotalCount() const = 0;

  virtual void Add(const HistogramSamples& other);

  // Add from serialized samples.
  virtual bool AddFromPickle(PickleIterator* iter);

  virtual void Subtract(const HistogramSamples& other);

  virtual std::unique_ptr<SampleCountIterator> Iterator() const = 0;
  virtual void Serialize(Pickle* pickle) const;

  // Accessor fuctions.
  uint64_t id() const { return meta_->id; }
  int64_t sum() const {
#ifdef ARCH_CPU_64_BITS
    return subtle::NoBarrier_Load(&meta_->sum);
#else
    return meta_->sum;
#endif
  }
  HistogramBase::Count redundant_count() const {
    return subtle::NoBarrier_Load(&meta_->redundant_count);
  }

 protected:
  enum NegativeSampleReason {
    SAMPLES_HAVE_LOGGED_BUT_NOT_SAMPLE,
    SAMPLES_SAMPLE_LESS_THAN_LOGGED,
    SAMPLES_ADDED_NEGATIVE_COUNT,
    SAMPLES_ADD_WENT_NEGATIVE,
    SAMPLES_ADD_OVERFLOW,
    SAMPLES_ACCUMULATE_NEGATIVE_COUNT,
    SAMPLES_ACCUMULATE_WENT_NEGATIVE,
    DEPRECATED_SAMPLES_ACCUMULATE_OVERFLOW,
    SAMPLES_ACCUMULATE_OVERFLOW,
    MAX_NEGATIVE_SAMPLE_REASONS
  };

  // Based on |op| type, add or subtract sample counts data from the iterator.
  enum Operator { ADD, SUBTRACT };
  virtual bool AddSubtractImpl(SampleCountIterator* iter, Operator op) = 0;

  // Accumulates to the embedded single-sample field if possible. Returns true
  // on success, false otherwise. Sum and redundant-count are also updated in
  // the success case.
  bool AccumulateSingleSample(HistogramBase::Sample value,
                              HistogramBase::Count count,
                              size_t bucket);

  // Atomically adjust the sum and redundant-count.
  void IncreaseSumAndCount(int64_t sum, HistogramBase::Count count);

  // Record a negative-sample observation and the reason why.
  void RecordNegativeSample(NegativeSampleReason reason,
                            HistogramBase::Count increment);

  AtomicSingleSample& single_sample() { return meta_->single_sample; }
  const AtomicSingleSample& single_sample() const {
    return meta_->single_sample;
  }

  Metadata* meta() { return meta_; }

 private:
  // Depending on derived class meta values can come from local stoarge or
  // external storage in which case HistogramSamples class cannot take ownership
  // of Metadata*.
  Metadata* meta_;

  DISALLOW_COPY_AND_ASSIGN(HistogramSamples);
};

class BASE_EXPORT SampleCountIterator {
 public:
  virtual ~SampleCountIterator();

  virtual bool Done() const = 0;
  virtual void Next() = 0;

  // Get the sample and count at current position.
  // |min| |max| and |count| can be NULL if the value is not of interest.
  // Note: |max| is int64_t because histograms support logged values in the
  // full int32_t range and bucket max is exclusive, so it needs to support
  // values up to MAXINT32+1.
  // Requires: !Done();
  virtual void Get(HistogramBase::Sample* min,
                   int64_t* max,
                   HistogramBase::Count* count) const = 0;
  static_assert(std::numeric_limits<HistogramBase::Sample>::max() <
                    std::numeric_limits<int64_t>::max(),
                "Get() |max| must be able to hold Histogram::Sample max + 1");

  // Get the index of current histogram bucket.
  // For histograms that don't use predefined buckets, it returns false.
  // Requires: !Done();
  virtual bool GetBucketIndex(size_t* index) const;
};

class BASE_EXPORT SingleSampleIterator : public SampleCountIterator {
 public:
  SingleSampleIterator(HistogramBase::Sample min,
                       int64_t max,
                       HistogramBase::Count count);
  SingleSampleIterator(HistogramBase::Sample min,
                       int64_t max,
                       HistogramBase::Count count,
                       size_t bucket_index);
  ~SingleSampleIterator() override;

  // SampleCountIterator:
  bool Done() const override;
  void Next() override;
  void Get(HistogramBase::Sample* min,
           int64_t* max,
           HistogramBase::Count* count) const override;

  // SampleVector uses predefined buckets so iterator can return bucket index.
  bool GetBucketIndex(size_t* index) const override;

 private:
  // Information about the single value to return.
  const HistogramBase::Sample min_;
  const int64_t max_;
  const size_t bucket_index_;
  HistogramBase::Count count_;
};

}  // namespace base

#endif  // BASE_METRICS_HISTOGRAM_SAMPLES_H_
