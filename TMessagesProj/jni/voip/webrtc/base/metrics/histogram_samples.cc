// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/metrics/histogram_samples.h"

#include <limits>

#include "base/compiler_specific.h"
#include "base/metrics/histogram_functions.h"
#include "base/metrics/histogram_macros.h"
#include "base/numerics/safe_conversions.h"
#include "base/numerics/safe_math.h"
#include "base/pickle.h"

namespace base {

namespace {

// A shorthand constant for the max value of size_t.
constexpr size_t kSizeMax = std::numeric_limits<size_t>::max();

// A constant stored in an AtomicSingleSample (as_atomic) to indicate that the
// sample is "disabled" and no further accumulation should be done with it. The
// value is chosen such that it will be MAX_UINT16 for both |bucket| & |count|,
// and thus less likely to conflict with real use. Conflicts are explicitly
// handled in the code but it's worth making them as unlikely as possible.
constexpr int32_t kDisabledSingleSample = -1;

class SampleCountPickleIterator : public SampleCountIterator {
 public:
  explicit SampleCountPickleIterator(PickleIterator* iter);

  bool Done() const override;
  void Next() override;
  void Get(HistogramBase::Sample* min,
           int64_t* max,
           HistogramBase::Count* count) const override;

 private:
  PickleIterator* const iter_;

  HistogramBase::Sample min_;
  int64_t max_;
  HistogramBase::Count count_;
  bool is_done_;
};

SampleCountPickleIterator::SampleCountPickleIterator(PickleIterator* iter)
    : iter_(iter),
      is_done_(false) {
  Next();
}

bool SampleCountPickleIterator::Done() const {
  return is_done_;
}

void SampleCountPickleIterator::Next() {
  DCHECK(!Done());
  if (!iter_->ReadInt(&min_) || !iter_->ReadInt64(&max_) ||
      !iter_->ReadInt(&count_)) {
    is_done_ = true;
  }
}

void SampleCountPickleIterator::Get(HistogramBase::Sample* min,
                                    int64_t* max,
                                    HistogramBase::Count* count) const {
  DCHECK(!Done());
  *min = min_;
  *max = max_;
  *count = count_;
}

}  // namespace

static_assert(sizeof(HistogramSamples::AtomicSingleSample) ==
                  sizeof(subtle::Atomic32),
              "AtomicSingleSample isn't 32 bits");

HistogramSamples::SingleSample HistogramSamples::AtomicSingleSample::Load()
    const {
  AtomicSingleSample single_sample = subtle::Acquire_Load(&as_atomic);

  // If the sample was extracted/disabled, it's still zero to the outside.
  if (single_sample.as_atomic == kDisabledSingleSample)
    single_sample.as_atomic = 0;

  return single_sample.as_parts;
}

HistogramSamples::SingleSample HistogramSamples::AtomicSingleSample::Extract(
    bool disable) {
  AtomicSingleSample single_sample = subtle::NoBarrier_AtomicExchange(
      &as_atomic, disable ? kDisabledSingleSample : 0);
  if (single_sample.as_atomic == kDisabledSingleSample)
    single_sample.as_atomic = 0;
  return single_sample.as_parts;
}

bool HistogramSamples::AtomicSingleSample::Accumulate(
    size_t bucket,
    HistogramBase::Count count) {
  if (count == 0)
    return true;

  // Convert the parameters to 16-bit variables because it's all 16-bit below.
  // To support decrements/subtractions, divide the |count| into sign/value and
  // do the proper operation below. The alternative is to change the single-
  // sample's count to be a signed integer (int16_t) and just add an int16_t
  // |count16| but that is somewhat wasteful given that the single-sample is
  // never expected to have a count less than zero.
  if (count < -std::numeric_limits<uint16_t>::max() ||
      count > std::numeric_limits<uint16_t>::max() ||
      bucket > std::numeric_limits<uint16_t>::max()) {
    return false;
  }
  bool count_is_negative = count < 0;
  uint16_t count16 = static_cast<uint16_t>(count_is_negative ? -count : count);
  uint16_t bucket16 = static_cast<uint16_t>(bucket);

  // A local, unshared copy of the single-sample is necessary so the parts
  // can be manipulated without worrying about atomicity.
  AtomicSingleSample single_sample;

  bool sample_updated;
  do {
    subtle::Atomic32 original = subtle::Acquire_Load(&as_atomic);
    if (original == kDisabledSingleSample)
      return false;
    single_sample.as_atomic = original;
    if (single_sample.as_atomic != 0) {
      // Only the same bucket (parameter and stored) can be counted multiple
      // times.
      if (single_sample.as_parts.bucket != bucket16)
        return false;
    } else {
      // The |single_ sample| was zero so becomes the |bucket| parameter, the
      // contents of which were checked above to fit in 16 bits.
      single_sample.as_parts.bucket = bucket16;
    }

    // Update count, making sure that it doesn't overflow.
    CheckedNumeric<uint16_t> new_count(single_sample.as_parts.count);
    if (count_is_negative)
      new_count -= count16;
    else
      new_count += count16;
    if (!new_count.AssignIfValid(&single_sample.as_parts.count))
      return false;

    // Don't let this become equivalent to the "disabled" value.
    if (single_sample.as_atomic == kDisabledSingleSample)
      return false;

    // Store the updated single-sample back into memory. |existing| is what
    // was in that memory location at the time of the call; if it doesn't
    // match |original| then the swap didn't happen so loop again.
    subtle::Atomic32 existing = subtle::Release_CompareAndSwap(
        &as_atomic, original, single_sample.as_atomic);
    sample_updated = (existing == original);
  } while (!sample_updated);

  return true;
}

bool HistogramSamples::AtomicSingleSample::IsDisabled() const {
  return subtle::Acquire_Load(&as_atomic) == kDisabledSingleSample;
}

HistogramSamples::LocalMetadata::LocalMetadata() {
  // This is the same way it's done for persistent metadata since no ctor
  // is called for the data members in that case.
  memset(this, 0, sizeof(*this));
}

HistogramSamples::HistogramSamples(uint64_t id, Metadata* meta)
    : meta_(meta) {
  DCHECK(meta_->id == 0 || meta_->id == id);

  // It's possible that |meta| is contained in initialized, read-only memory
  // so it's essential that no write be done in that case.
  if (!meta_->id)
    meta_->id = id;
}

// This mustn't do anything with |meta_|. It was passed to the ctor and may
// be invalid by the time this dtor gets called.
HistogramSamples::~HistogramSamples() = default;

void HistogramSamples::Add(const HistogramSamples& other) {
  IncreaseSumAndCount(other.sum(), other.redundant_count());
  std::unique_ptr<SampleCountIterator> it = other.Iterator();
  bool success = AddSubtractImpl(it.get(), ADD);
  DCHECK(success);
}

bool HistogramSamples::AddFromPickle(PickleIterator* iter) {
  int64_t sum;
  HistogramBase::Count redundant_count;

  if (!iter->ReadInt64(&sum) || !iter->ReadInt(&redundant_count))
    return false;

  IncreaseSumAndCount(sum, redundant_count);

  SampleCountPickleIterator pickle_iter(iter);
  return AddSubtractImpl(&pickle_iter, ADD);
}

void HistogramSamples::Subtract(const HistogramSamples& other) {
  IncreaseSumAndCount(-other.sum(), -other.redundant_count());
  std::unique_ptr<SampleCountIterator> it = other.Iterator();
  bool success = AddSubtractImpl(it.get(), SUBTRACT);
  DCHECK(success);
}

void HistogramSamples::Serialize(Pickle* pickle) const {
  pickle->WriteInt64(sum());
  pickle->WriteInt(redundant_count());

  HistogramBase::Sample min;
  int64_t max;
  HistogramBase::Count count;
  for (std::unique_ptr<SampleCountIterator> it = Iterator(); !it->Done();
       it->Next()) {
    it->Get(&min, &max, &count);
    pickle->WriteInt(min);
    pickle->WriteInt64(max);
    pickle->WriteInt(count);
  }
}

bool HistogramSamples::AccumulateSingleSample(HistogramBase::Sample value,
                                              HistogramBase::Count count,
                                              size_t bucket) {
  if (single_sample().Accumulate(bucket, count)) {
    // Success. Update the (separate) sum and redundant-count.
    IncreaseSumAndCount(strict_cast<int64_t>(value) * count, count);
    return true;
  }
  return false;
}

void HistogramSamples::IncreaseSumAndCount(int64_t sum,
                                           HistogramBase::Count count) {
#ifdef ARCH_CPU_64_BITS
  subtle::NoBarrier_AtomicIncrement(&meta_->sum, sum);
#else
  meta_->sum += sum;
#endif
  subtle::NoBarrier_AtomicIncrement(&meta_->redundant_count, count);
}

void HistogramSamples::RecordNegativeSample(NegativeSampleReason reason,
                                            HistogramBase::Count increment) {
  UMA_HISTOGRAM_ENUMERATION("UMA.NegativeSamples.Reason", reason,
                            MAX_NEGATIVE_SAMPLE_REASONS);
  UMA_HISTOGRAM_CUSTOM_COUNTS("UMA.NegativeSamples.Increment", increment, 1,
                              1 << 30, 100);
  UmaHistogramSparse("UMA.NegativeSamples.Histogram",
                     static_cast<int32_t>(id()));
}

SampleCountIterator::~SampleCountIterator() = default;

bool SampleCountIterator::GetBucketIndex(size_t* index) const {
  DCHECK(!Done());
  return false;
}

SingleSampleIterator::SingleSampleIterator(HistogramBase::Sample min,
                                           int64_t max,
                                           HistogramBase::Count count)
    : SingleSampleIterator(min, max, count, kSizeMax) {}

SingleSampleIterator::SingleSampleIterator(HistogramBase::Sample min,
                                           int64_t max,
                                           HistogramBase::Count count,
                                           size_t bucket_index)
    : min_(min), max_(max), bucket_index_(bucket_index), count_(count) {}

SingleSampleIterator::~SingleSampleIterator() = default;

bool SingleSampleIterator::Done() const {
  return count_ == 0;
}

void SingleSampleIterator::Next() {
  DCHECK(!Done());
  count_ = 0;
}

void SingleSampleIterator::Get(HistogramBase::Sample* min,
                               int64_t* max,
                               HistogramBase::Count* count) const {
  DCHECK(!Done());
  if (min != nullptr)
    *min = min_;
  if (max != nullptr)
    *max = max_;
  if (count != nullptr)
    *count = count_;
}

bool SingleSampleIterator::GetBucketIndex(size_t* index) const {
  DCHECK(!Done());
  if (bucket_index_ == kSizeMax)
    return false;
  *index = bucket_index_;
  return true;
}

}  // namespace base
