// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/metrics/sparse_histogram.h"

#include <utility>

#include "base/memory/ptr_util.h"
#include "base/metrics/dummy_histogram.h"
#include "base/metrics/metrics_hashes.h"
#include "base/metrics/persistent_histogram_allocator.h"
#include "base/metrics/persistent_sample_map.h"
#include "base/metrics/sample_map.h"
#include "base/metrics/statistics_recorder.h"
#include "base/pickle.h"
#include "base/strings/stringprintf.h"
#include "base/synchronization/lock.h"

namespace {
constexpr char kHtmlNewLine[] = "<br>";
constexpr char kAsciiNewLine[] = "\n";
}  // namespace

namespace base {

typedef HistogramBase::Count Count;
typedef HistogramBase::Sample Sample;

// static
HistogramBase* SparseHistogram::FactoryGet(const std::string& name,
                                           int32_t flags) {
  HistogramBase* histogram = StatisticsRecorder::FindHistogram(name);
  if (!histogram) {
    // TODO(gayane): |HashMetricName| is called again in Histogram constructor.
    // Refactor code to avoid the additional call.
    bool should_record =
        StatisticsRecorder::ShouldRecordHistogram(HashMetricName(name));
    if (!should_record)
      return DummyHistogram::GetInstance();
    // Try to create the histogram using a "persistent" allocator. As of
    // 2016-02-25, the availability of such is controlled by a base::Feature
    // that is off by default. If the allocator doesn't exist or if
    // allocating from it fails, code below will allocate the histogram from
    // the process heap.
    PersistentMemoryAllocator::Reference histogram_ref = 0;
    std::unique_ptr<HistogramBase> tentative_histogram;
    PersistentHistogramAllocator* allocator = GlobalHistogramAllocator::Get();
    if (allocator) {
      tentative_histogram = allocator->AllocateHistogram(
          SPARSE_HISTOGRAM, name, 0, 0, nullptr, flags, &histogram_ref);
    }

    // Handle the case where no persistent allocator is present or the
    // persistent allocation fails (perhaps because it is full).
    if (!tentative_histogram) {
      DCHECK(!histogram_ref);  // Should never have been set.
      DCHECK(!allocator);      // Shouldn't have failed.
      flags &= ~HistogramBase::kIsPersistent;
      tentative_histogram.reset(new SparseHistogram(GetPermanentName(name)));
      tentative_histogram->SetFlags(flags);
    }

    // Register this histogram with the StatisticsRecorder. Keep a copy of
    // the pointer value to tell later whether the locally created histogram
    // was registered or deleted. The type is "void" because it could point
    // to released memory after the following line.
    const void* tentative_histogram_ptr = tentative_histogram.get();
    histogram = StatisticsRecorder::RegisterOrDeleteDuplicate(
        tentative_histogram.release());

    // Persistent histograms need some follow-up processing.
    if (histogram_ref) {
      allocator->FinalizeHistogram(histogram_ref,
                                   histogram == tentative_histogram_ptr);
    }
  }

  CHECK_EQ(SPARSE_HISTOGRAM, histogram->GetHistogramType());
  return histogram;
}

// static
std::unique_ptr<HistogramBase> SparseHistogram::PersistentCreate(
    PersistentHistogramAllocator* allocator,
    const char* name,
    HistogramSamples::Metadata* meta,
    HistogramSamples::Metadata* logged_meta) {
  return WrapUnique(
      new SparseHistogram(allocator, name, meta, logged_meta));
}

SparseHistogram::~SparseHistogram() = default;

uint64_t SparseHistogram::name_hash() const {
  return unlogged_samples_->id();
}

HistogramType SparseHistogram::GetHistogramType() const {
  return SPARSE_HISTOGRAM;
}

bool SparseHistogram::HasConstructionArguments(
    Sample expected_minimum,
    Sample expected_maximum,
    uint32_t expected_bucket_count) const {
  // SparseHistogram never has min/max/bucket_count limit.
  return false;
}

void SparseHistogram::Add(Sample value) {
  AddCount(value, 1);
}

void SparseHistogram::AddCount(Sample value, int count) {
  if (count <= 0) {
    NOTREACHED();
    return;
  }
  {
    base::AutoLock auto_lock(lock_);
    unlogged_samples_->Accumulate(value, count);
  }

  if (UNLIKELY(StatisticsRecorder::have_active_callbacks()))
    FindAndRunCallback(value);
}

std::unique_ptr<HistogramSamples> SparseHistogram::SnapshotSamples() const {
  std::unique_ptr<SampleMap> snapshot(new SampleMap(name_hash()));

  base::AutoLock auto_lock(lock_);
  snapshot->Add(*unlogged_samples_);
  snapshot->Add(*logged_samples_);
  return std::move(snapshot);
}

std::unique_ptr<HistogramSamples> SparseHistogram::SnapshotDelta() {
  DCHECK(!final_delta_created_);

  std::unique_ptr<SampleMap> snapshot(new SampleMap(name_hash()));
  base::AutoLock auto_lock(lock_);
  snapshot->Add(*unlogged_samples_);

  unlogged_samples_->Subtract(*snapshot);
  logged_samples_->Add(*snapshot);
  return std::move(snapshot);
}

std::unique_ptr<HistogramSamples> SparseHistogram::SnapshotFinalDelta() const {
  DCHECK(!final_delta_created_);
  final_delta_created_ = true;

  std::unique_ptr<SampleMap> snapshot(new SampleMap(name_hash()));
  base::AutoLock auto_lock(lock_);
  snapshot->Add(*unlogged_samples_);

  return std::move(snapshot);
}

void SparseHistogram::AddSamples(const HistogramSamples& samples) {
  base::AutoLock auto_lock(lock_);
  unlogged_samples_->Add(samples);
}

bool SparseHistogram::AddSamplesFromPickle(PickleIterator* iter) {
  base::AutoLock auto_lock(lock_);
  return unlogged_samples_->AddFromPickle(iter);
}

void SparseHistogram::WriteHTMLGraph(std::string* output) const {
  // Get a local copy of the data so we are consistent.
  std::unique_ptr<HistogramSamples> snapshot = SnapshotSamples();

  output->append("<PRE>");
  output->append("<h4>");
  WriteAsciiHeader(*snapshot, output);
  output->append("</h4>");
  WriteAsciiBody(*snapshot, true, kHtmlNewLine, output);
  output->append("</PRE>");
}

void SparseHistogram::WriteAscii(std::string* output) const {
  // Get a local copy of the data so we are consistent.
  std::unique_ptr<HistogramSamples> snapshot = SnapshotSamples();

  WriteAsciiHeader(*snapshot, output);
  output->append(kAsciiNewLine);
  WriteAsciiBody(*snapshot, true, kAsciiNewLine, output);
}

void SparseHistogram::SerializeInfoImpl(Pickle* pickle) const {
  pickle->WriteString(histogram_name());
  pickle->WriteInt(flags());
}

SparseHistogram::SparseHistogram(const char* name)
    : HistogramBase(name),
      unlogged_samples_(new SampleMap(HashMetricName(name))),
      logged_samples_(new SampleMap(unlogged_samples_->id())) {}

SparseHistogram::SparseHistogram(PersistentHistogramAllocator* allocator,
                                 const char* name,
                                 HistogramSamples::Metadata* meta,
                                 HistogramSamples::Metadata* logged_meta)
    : HistogramBase(name),
      // While other histogram types maintain a static vector of values with
      // sufficient space for both "active" and "logged" samples, with each
      // SampleVector being given the appropriate half, sparse histograms
      // have no such initial allocation. Each sample has its own record
      // attached to a single PersistentSampleMap by a common 64-bit identifier.
      // Since a sparse histogram has two sample maps (active and logged),
      // there must be two sets of sample records with diffent IDs. The
      // "active" samples use, for convenience purposes, an ID matching
      // that of the histogram while the "logged" samples use that number
      // plus 1.
      unlogged_samples_(
          new PersistentSampleMap(HashMetricName(name), allocator, meta)),
      logged_samples_(new PersistentSampleMap(unlogged_samples_->id() + 1,
                                              allocator,
                                              logged_meta)) {}

HistogramBase* SparseHistogram::DeserializeInfoImpl(PickleIterator* iter) {
  std::string histogram_name;
  int flags;
  if (!iter->ReadString(&histogram_name) || !iter->ReadInt(&flags)) {
    DLOG(ERROR) << "Pickle error decoding Histogram: " << histogram_name;
    return nullptr;
  }

  flags &= ~HistogramBase::kIPCSerializationSourceFlag;

  return SparseHistogram::FactoryGet(histogram_name, flags);
}

void SparseHistogram::GetParameters(DictionaryValue* params) const {
  // TODO(kaiwang): Implement. (See HistogramBase::WriteJSON.)
}

void SparseHistogram::GetCountAndBucketData(Count* count,
                                            int64_t* sum,
                                            ListValue* buckets) const {
  // TODO(kaiwang): Implement. (See HistogramBase::WriteJSON.)
}

void SparseHistogram::WriteAsciiBody(const HistogramSamples& snapshot,
                                     bool graph_it,
                                     const std::string& newline,
                                     std::string* output) const {
  Count total_count = snapshot.TotalCount();
  double scaled_total_count = total_count / 100.0;

  // Determine how wide the largest bucket range is (how many digits to print),
  // so that we'll be able to right-align starts for the graphical bars.
  // Determine which bucket has the largest sample count so that we can
  // normalize the graphical bar-width relative to that sample count.
  Count largest_count = 0;
  Sample largest_sample = 0;
  std::unique_ptr<SampleCountIterator> it = snapshot.Iterator();
  while (!it->Done()) {
    Sample min;
    int64_t max;
    Count count;
    it->Get(&min, &max, &count);
    if (min > largest_sample)
      largest_sample = min;
    if (count > largest_count)
      largest_count = count;
    it->Next();
  }
  size_t print_width = GetSimpleAsciiBucketRange(largest_sample).size() + 1;

  // iterate over each item and display them
  it = snapshot.Iterator();
  while (!it->Done()) {
    Sample min;
    int64_t max;
    Count count;
    it->Get(&min, &max, &count);

    // value is min, so display it
    std::string range = GetSimpleAsciiBucketRange(min);
    output->append(range);
    for (size_t j = 0; range.size() + j < print_width + 1; ++j)
      output->push_back(' ');

    if (graph_it)
      WriteAsciiBucketGraph(count, largest_count, output);
    WriteAsciiBucketValue(count, scaled_total_count, output);
    output->append(newline);
    it->Next();
  }
}

void SparseHistogram::WriteAsciiHeader(const HistogramSamples& snapshot,
                                       std::string* output) const {
  StringAppendF(output, "Histogram: %s recorded %d samples", histogram_name(),
                snapshot.TotalCount());
  if (flags())
    StringAppendF(output, " (flags = 0x%x)", flags());
}

}  // namespace base
