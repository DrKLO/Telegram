// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// Histogram is an object that aggregates statistics, and can summarize them in
// various forms, including ASCII graphical, HTML, and numerically (as a
// vector of numbers corresponding to each of the aggregating buckets).
// See header file for details and examples.

#include "base/metrics/histogram.h"

#include <inttypes.h>
#include <limits.h>
#include <math.h>

#include <algorithm>
#include <string>
#include <utility>

#include "base/compiler_specific.h"
#include "base/debug/alias.h"
#include "base/logging.h"
#include "base/memory/ptr_util.h"
#include "base/metrics/dummy_histogram.h"
#include "base/metrics/histogram_functions.h"
#include "base/metrics/metrics_hashes.h"
#include "base/metrics/persistent_histogram_allocator.h"
#include "base/metrics/persistent_memory_allocator.h"
#include "base/metrics/sample_vector.h"
#include "base/metrics/statistics_recorder.h"
#include "base/pickle.h"
#include "base/strings/string_util.h"
#include "base/strings/stringprintf.h"
#include "base/synchronization/lock.h"
#include "base/values.h"
#include "build/build_config.h"

namespace {
constexpr char kHtmlNewLine[] = "<br>";
constexpr char kAsciiNewLine[] = "\n";
}  // namespace

namespace base {

namespace {

bool ReadHistogramArguments(PickleIterator* iter,
                            std::string* histogram_name,
                            int* flags,
                            int* declared_min,
                            int* declared_max,
                            uint32_t* bucket_count,
                            uint32_t* range_checksum) {
  if (!iter->ReadString(histogram_name) ||
      !iter->ReadInt(flags) ||
      !iter->ReadInt(declared_min) ||
      !iter->ReadInt(declared_max) ||
      !iter->ReadUInt32(bucket_count) ||
      !iter->ReadUInt32(range_checksum)) {
    DLOG(ERROR) << "Pickle error decoding Histogram: " << *histogram_name;
    return false;
  }

  // Since these fields may have come from an untrusted renderer, do additional
  // checks above and beyond those in Histogram::Initialize()
  if (*declared_max <= 0 ||
      *declared_min <= 0 ||
      *declared_max < *declared_min ||
      INT_MAX / sizeof(HistogramBase::Count) <= *bucket_count ||
      *bucket_count < 2) {
    DLOG(ERROR) << "Values error decoding Histogram: " << histogram_name;
    return false;
  }

  // We use the arguments to find or create the local version of the histogram
  // in this process, so we need to clear any IPC flag.
  *flags &= ~HistogramBase::kIPCSerializationSourceFlag;

  return true;
}

bool ValidateRangeChecksum(const HistogramBase& histogram,
                           uint32_t range_checksum) {
  // Normally, |histogram| should have type HISTOGRAM or be inherited from it.
  // However, if it's expired, it will actually be a DUMMY_HISTOGRAM.
  // Skip the checks in that case.
  if (histogram.GetHistogramType() == DUMMY_HISTOGRAM)
    return true;
  const Histogram& casted_histogram =
      static_cast<const Histogram&>(histogram);

  return casted_histogram.bucket_ranges()->checksum() == range_checksum;
}

}  // namespace

typedef HistogramBase::Count Count;
typedef HistogramBase::Sample Sample;

// static
const uint32_t Histogram::kBucketCount_MAX = 1002u;

class Histogram::Factory {
 public:
  Factory(const std::string& name,
          HistogramBase::Sample minimum,
          HistogramBase::Sample maximum,
          uint32_t bucket_count,
          int32_t flags)
    : Factory(name, HISTOGRAM, minimum, maximum, bucket_count, flags) {}

  // Create histogram based on construction parameters. Caller takes
  // ownership of the returned object.
  HistogramBase* Build();

 protected:
  Factory(const std::string& name,
          HistogramType histogram_type,
          HistogramBase::Sample minimum,
          HistogramBase::Sample maximum,
          uint32_t bucket_count,
          int32_t flags)
    : name_(name),
      histogram_type_(histogram_type),
      minimum_(minimum),
      maximum_(maximum),
      bucket_count_(bucket_count),
      flags_(flags) {}

  // Create a BucketRanges structure appropriate for this histogram.
  virtual BucketRanges* CreateRanges() {
    BucketRanges* ranges = new BucketRanges(bucket_count_ + 1);
    Histogram::InitializeBucketRanges(minimum_, maximum_, ranges);
    return ranges;
  }

  // Allocate the correct Histogram object off the heap (in case persistent
  // memory is not available).
  virtual std::unique_ptr<HistogramBase> HeapAlloc(const BucketRanges* ranges) {
    return WrapUnique(
        new Histogram(GetPermanentName(name_), minimum_, maximum_, ranges));
  }

  // Perform any required datafill on the just-created histogram.  If
  // overridden, be sure to call the "super" version -- this method may not
  // always remain empty.
  virtual void FillHistogram(HistogramBase* histogram) {}

  // These values are protected (instead of private) because they need to
  // be accessible to methods of sub-classes in order to avoid passing
  // unnecessary parameters everywhere.
  const std::string& name_;
  const HistogramType histogram_type_;
  HistogramBase::Sample minimum_;
  HistogramBase::Sample maximum_;
  uint32_t bucket_count_;
  int32_t flags_;

 private:
  DISALLOW_COPY_AND_ASSIGN(Factory);
};

HistogramBase* Histogram::Factory::Build() {
  HistogramBase* histogram = StatisticsRecorder::FindHistogram(name_);
  if (!histogram) {
    // TODO(gayane): |HashMetricName()| is called again in Histogram
    // constructor. Refactor code to avoid the additional call.
    bool should_record =
        StatisticsRecorder::ShouldRecordHistogram(HashMetricName(name_));
    if (!should_record)
      return DummyHistogram::GetInstance();
    // To avoid racy destruction at shutdown, the following will be leaked.
    const BucketRanges* created_ranges = CreateRanges();

    const BucketRanges* registered_ranges =
        StatisticsRecorder::RegisterOrDeleteDuplicateRanges(created_ranges);

    // In most cases, the bucket-count, minimum, and maximum values are known
    // when the code is written and so are passed in explicitly. In other
    // cases (such as with a CustomHistogram), they are calculated dynamically
    // at run-time. In the latter case, those ctor parameters are zero and
    // the results extracted from the result of CreateRanges().
    if (bucket_count_ == 0) {
      bucket_count_ = static_cast<uint32_t>(registered_ranges->bucket_count());
      minimum_ = registered_ranges->range(1);
      maximum_ = registered_ranges->range(bucket_count_ - 1);
    }
    DCHECK_EQ(minimum_, registered_ranges->range(1));
    DCHECK_EQ(maximum_, registered_ranges->range(bucket_count_ - 1));

    // Try to create the histogram using a "persistent" allocator. As of
    // 2016-02-25, the availability of such is controlled by a base::Feature
    // that is off by default. If the allocator doesn't exist or if
    // allocating from it fails, code below will allocate the histogram from
    // the process heap.
    PersistentHistogramAllocator::Reference histogram_ref = 0;
    std::unique_ptr<HistogramBase> tentative_histogram;
    PersistentHistogramAllocator* allocator = GlobalHistogramAllocator::Get();
    if (allocator) {
      tentative_histogram = allocator->AllocateHistogram(
          histogram_type_,
          name_,
          minimum_,
          maximum_,
          registered_ranges,
          flags_,
          &histogram_ref);
    }

    // Handle the case where no persistent allocator is present or the
    // persistent allocation fails (perhaps because it is full).
    if (!tentative_histogram) {
      DCHECK(!histogram_ref);  // Should never have been set.
      DCHECK(!allocator);  // Shouldn't have failed.
      flags_ &= ~HistogramBase::kIsPersistent;
      tentative_histogram = HeapAlloc(registered_ranges);
      tentative_histogram->SetFlags(flags_);
    }

    FillHistogram(tentative_histogram.get());

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

  if (histogram_type_ != histogram->GetHistogramType() ||
      (bucket_count_ != 0 && !histogram->HasConstructionArguments(
                                 minimum_, maximum_, bucket_count_))) {
    // The construction arguments do not match the existing histogram.  This can
    // come about if an extension updates in the middle of a chrome run and has
    // changed one of them, or simply by bad code within Chrome itself.  A NULL
    // return would cause Chrome to crash; better to just record it for later
    // analysis.
    UmaHistogramSparse("Histogram.MismatchedConstructionArguments",
                       static_cast<Sample>(HashMetricName(name_)));
    DLOG(ERROR) << "Histogram " << name_
                << " has mismatched construction arguments";
    return DummyHistogram::GetInstance();
  }
  return histogram;
}

HistogramBase* Histogram::FactoryGet(const std::string& name,
                                     Sample minimum,
                                     Sample maximum,
                                     uint32_t bucket_count,
                                     int32_t flags) {
  bool valid_arguments =
      InspectConstructionArguments(name, &minimum, &maximum, &bucket_count);
  DCHECK(valid_arguments) << name;

  return Factory(name, minimum, maximum, bucket_count, flags).Build();
}

HistogramBase* Histogram::FactoryTimeGet(const std::string& name,
                                         TimeDelta minimum,
                                         TimeDelta maximum,
                                         uint32_t bucket_count,
                                         int32_t flags) {
  DCHECK_LT(minimum.InMilliseconds(), std::numeric_limits<Sample>::max());
  DCHECK_LT(maximum.InMilliseconds(), std::numeric_limits<Sample>::max());
  return FactoryGet(name, static_cast<Sample>(minimum.InMilliseconds()),
                    static_cast<Sample>(maximum.InMilliseconds()), bucket_count,
                    flags);
}

HistogramBase* Histogram::FactoryMicrosecondsTimeGet(const std::string& name,
                                                     TimeDelta minimum,
                                                     TimeDelta maximum,
                                                     uint32_t bucket_count,
                                                     int32_t flags) {
  DCHECK_LT(minimum.InMicroseconds(), std::numeric_limits<Sample>::max());
  DCHECK_LT(maximum.InMicroseconds(), std::numeric_limits<Sample>::max());
  return FactoryGet(name, static_cast<Sample>(minimum.InMicroseconds()),
                    static_cast<Sample>(maximum.InMicroseconds()), bucket_count,
                    flags);
}

HistogramBase* Histogram::FactoryGet(const char* name,
                                     Sample minimum,
                                     Sample maximum,
                                     uint32_t bucket_count,
                                     int32_t flags) {
  return FactoryGet(std::string(name), minimum, maximum, bucket_count, flags);
}

HistogramBase* Histogram::FactoryTimeGet(const char* name,
                                         TimeDelta minimum,
                                         TimeDelta maximum,
                                         uint32_t bucket_count,
                                         int32_t flags) {
  return FactoryTimeGet(std::string(name), minimum, maximum, bucket_count,
                        flags);
}

HistogramBase* Histogram::FactoryMicrosecondsTimeGet(const char* name,
                                                     TimeDelta minimum,
                                                     TimeDelta maximum,
                                                     uint32_t bucket_count,
                                                     int32_t flags) {
  return FactoryMicrosecondsTimeGet(std::string(name), minimum, maximum,
                                    bucket_count, flags);
}

std::unique_ptr<HistogramBase> Histogram::PersistentCreate(
    const char* name,
    Sample minimum,
    Sample maximum,
    const BucketRanges* ranges,
    const DelayedPersistentAllocation& counts,
    const DelayedPersistentAllocation& logged_counts,
    HistogramSamples::Metadata* meta,
    HistogramSamples::Metadata* logged_meta) {
  return WrapUnique(new Histogram(name, minimum, maximum, ranges, counts,
                                  logged_counts, meta, logged_meta));
}

// Calculate what range of values are held in each bucket.
// We have to be careful that we don't pick a ratio between starting points in
// consecutive buckets that is sooo small, that the integer bounds are the same
// (effectively making one bucket get no values).  We need to avoid:
//   ranges(i) == ranges(i + 1)
// To avoid that, we just do a fine-grained bucket width as far as we need to
// until we get a ratio that moves us along at least 2 units at a time.  From
// that bucket onward we do use the exponential growth of buckets.
//
// static
void Histogram::InitializeBucketRanges(Sample minimum,
                                       Sample maximum,
                                       BucketRanges* ranges) {
  double log_max = log(static_cast<double>(maximum));
  double log_ratio;
  double log_next;
  size_t bucket_index = 1;
  Sample current = minimum;
  ranges->set_range(bucket_index, current);
  size_t bucket_count = ranges->bucket_count();

  while (bucket_count > ++bucket_index) {
    double log_current;
    log_current = log(static_cast<double>(current));
    debug::Alias(&log_current);
    // Calculate the count'th root of the range.
    log_ratio = (log_max - log_current) / (bucket_count - bucket_index);
    // See where the next bucket would start.
    log_next = log_current + log_ratio;
    Sample next;
    next = static_cast<int>(std::round(exp(log_next)));
    if (next > current)
      current = next;
    else
      ++current;  // Just do a narrow bucket, and keep trying.
    ranges->set_range(bucket_index, current);
  }
  ranges->set_range(ranges->bucket_count(), HistogramBase::kSampleType_MAX);
  ranges->ResetChecksum();
}

// static
const int Histogram::kCommonRaceBasedCountMismatch = 5;

uint32_t Histogram::FindCorruption(const HistogramSamples& samples) const {
  int inconsistencies = NO_INCONSISTENCIES;
  Sample previous_range = -1;  // Bottom range is always 0.
  for (uint32_t index = 0; index < bucket_count(); ++index) {
    int new_range = ranges(index);
    if (previous_range >= new_range)
      inconsistencies |= BUCKET_ORDER_ERROR;
    previous_range = new_range;
  }

  if (!bucket_ranges()->HasValidChecksum())
    inconsistencies |= RANGE_CHECKSUM_ERROR;

  int64_t delta64 = samples.redundant_count() - samples.TotalCount();
  if (delta64 != 0) {
    int delta = static_cast<int>(delta64);
    if (delta != delta64)
      delta = INT_MAX;  // Flag all giant errors as INT_MAX.
    if (delta > 0) {
      if (delta > kCommonRaceBasedCountMismatch)
        inconsistencies |= COUNT_HIGH_ERROR;
    } else {
      DCHECK_GT(0, delta);
      if (-delta > kCommonRaceBasedCountMismatch)
        inconsistencies |= COUNT_LOW_ERROR;
    }
  }
  return inconsistencies;
}

const BucketRanges* Histogram::bucket_ranges() const {
  return unlogged_samples_->bucket_ranges();
}

Sample Histogram::declared_min() const {
  const BucketRanges* ranges = bucket_ranges();
  if (ranges->bucket_count() < 2)
    return -1;
  return ranges->range(1);
}

Sample Histogram::declared_max() const {
  const BucketRanges* ranges = bucket_ranges();
  if (ranges->bucket_count() < 2)
    return -1;
  return ranges->range(ranges->bucket_count() - 1);
}

Sample Histogram::ranges(uint32_t i) const {
  return bucket_ranges()->range(i);
}

uint32_t Histogram::bucket_count() const {
  return static_cast<uint32_t>(bucket_ranges()->bucket_count());
}

// static
bool Histogram::InspectConstructionArguments(StringPiece name,
                                             Sample* minimum,
                                             Sample* maximum,
                                             uint32_t* bucket_count) {
  bool check_okay = true;

  // Checks below must be done after any min/max swap.
  if (*minimum > *maximum) {
    check_okay = false;
    std::swap(*minimum, *maximum);
  }

  // Defensive code for backward compatibility.
  if (*minimum < 1) {
    DVLOG(1) << "Histogram: " << name << " has bad minimum: " << *minimum;
    *minimum = 1;
    if (*maximum < 1)
      *maximum = 1;
  }
  if (*maximum >= kSampleType_MAX) {
    DVLOG(1) << "Histogram: " << name << " has bad maximum: " << *maximum;
    *maximum = kSampleType_MAX - 1;
  }
  if (*bucket_count > kBucketCount_MAX) {
    UmaHistogramSparse("Histogram.TooManyBuckets.1000",
                       static_cast<Sample>(HashMetricName(name)));

    // TODO(bcwhite): Clean these up as bugs get fixed. Also look at injecting
    // whitelist (using hashes) from a higher layer rather than hardcoding
    // them here.
    // Blink.UseCounter legitimately has more than 1000 entries in its enum.
    // Arc.OOMKills: https://crbug.com/916757
    if (!name.starts_with("Blink.UseCounter") &&
        !name.starts_with("Arc.OOMKills.")) {
      DVLOG(1) << "Histogram: " << name
               << " has bad bucket_count: " << *bucket_count << " (limit "
               << kBucketCount_MAX << ")";

      // Assume it's a mistake and limit to 100 buckets, plus under and over.
      // If the DCHECK doesn't alert the user then hopefully the small number
      // will be obvious on the dashboard. If not, then it probably wasn't
      // important.
      *bucket_count = 102;
      check_okay = false;
    }
  }

  // Ensure parameters are sane.
  if (*maximum == *minimum) {
    check_okay = false;
    *maximum = *minimum + 1;
  }
  if (*bucket_count < 3) {
    check_okay = false;
    *bucket_count = 3;
  }
  if (*bucket_count > static_cast<uint32_t>(*maximum - *minimum + 2)) {
    check_okay = false;
    *bucket_count = static_cast<uint32_t>(*maximum - *minimum + 2);
  }

  if (!check_okay) {
    UmaHistogramSparse("Histogram.BadConstructionArguments",
                       static_cast<Sample>(HashMetricName(name)));
  }

  return check_okay;
}

uint64_t Histogram::name_hash() const {
  return unlogged_samples_->id();
}

HistogramType Histogram::GetHistogramType() const {
  return HISTOGRAM;
}

bool Histogram::HasConstructionArguments(Sample expected_minimum,
                                         Sample expected_maximum,
                                         uint32_t expected_bucket_count) const {
  return (expected_bucket_count == bucket_count() &&
          expected_minimum == declared_min() &&
          expected_maximum == declared_max());
}

void Histogram::Add(int value) {
  AddCount(value, 1);
}

void Histogram::AddCount(int value, int count) {
  DCHECK_EQ(0, ranges(0));
  DCHECK_EQ(kSampleType_MAX, ranges(bucket_count()));

  if (value > kSampleType_MAX - 1)
    value = kSampleType_MAX - 1;
  if (value < 0)
    value = 0;
  if (count <= 0) {
    NOTREACHED();
    return;
  }
  unlogged_samples_->Accumulate(value, count);

  if (UNLIKELY(StatisticsRecorder::have_active_callbacks()))
    FindAndRunCallback(value);
}

std::unique_ptr<HistogramSamples> Histogram::SnapshotSamples() const {
  return SnapshotAllSamples();
}

std::unique_ptr<HistogramSamples> Histogram::SnapshotDelta() {
#if DCHECK_IS_ON()
  DCHECK(!final_delta_created_);
#endif

  // The code below has subtle thread-safety guarantees! All changes to
  // the underlying SampleVectors use atomic integer operations, which guarantee
  // eventual consistency, but do not guarantee full synchronization between
  // different entries in the SampleVector. In particular, this means that
  // concurrent updates to the histogram might result in the reported sum not
  // matching the individual bucket counts; or there being some buckets that are
  // logically updated "together", but end up being only partially updated when
  // a snapshot is captured. Note that this is why it's important to subtract
  // exactly the snapshotted unlogged samples, rather than simply resetting the
  // vector: this way, the next snapshot will include any concurrent updates
  // missed by the current snapshot.

  std::unique_ptr<HistogramSamples> snapshot = SnapshotUnloggedSamples();
  unlogged_samples_->Subtract(*snapshot);
  logged_samples_->Add(*snapshot);

  return snapshot;
}

std::unique_ptr<HistogramSamples> Histogram::SnapshotFinalDelta() const {
#if DCHECK_IS_ON()
  DCHECK(!final_delta_created_);
  final_delta_created_ = true;
#endif

  return SnapshotUnloggedSamples();
}

void Histogram::AddSamples(const HistogramSamples& samples) {
  unlogged_samples_->Add(samples);
}

bool Histogram::AddSamplesFromPickle(PickleIterator* iter) {
  return unlogged_samples_->AddFromPickle(iter);
}

// The following methods provide a graphical histogram display.
void Histogram::WriteHTMLGraph(std::string* output) const {
  // TBD(jar) Write a nice HTML bar chart, with divs an mouse-overs etc.

  // Get local (stack) copies of all effectively volatile class data so that we
  // are consistent across our output activities.
  std::unique_ptr<SampleVector> snapshot = SnapshotAllSamples();
  output->append("<PRE>");
  output->append("<h4>");
  WriteAsciiHeader(*snapshot, output);
  output->append("</h4>");
  WriteAsciiBody(*snapshot, true, kHtmlNewLine, output);
  output->append("</PRE>");
}

void Histogram::WriteAscii(std::string* output) const {
  // Get local (stack) copies of all effectively volatile class data so that we
  // are consistent across our output activities.
  std::unique_ptr<SampleVector> snapshot = SnapshotAllSamples();
  WriteAsciiHeader(*snapshot, output);
  output->append(kAsciiNewLine);
  WriteAsciiBody(*snapshot, true, kAsciiNewLine, output);
}

void Histogram::ValidateHistogramContents() const {
  CHECK(unlogged_samples_);
  CHECK(unlogged_samples_->bucket_ranges());
  CHECK(logged_samples_);
  CHECK(logged_samples_->bucket_ranges());
  CHECK_NE(0U, logged_samples_->id());
}

void Histogram::SerializeInfoImpl(Pickle* pickle) const {
  DCHECK(bucket_ranges()->HasValidChecksum());
  pickle->WriteString(histogram_name());
  pickle->WriteInt(flags());
  pickle->WriteInt(declared_min());
  pickle->WriteInt(declared_max());
  pickle->WriteUInt32(bucket_count());
  pickle->WriteUInt32(bucket_ranges()->checksum());
}

// TODO(bcwhite): Remove minimum/maximum parameters from here and call chain.
Histogram::Histogram(const char* name,
                     Sample minimum,
                     Sample maximum,
                     const BucketRanges* ranges)
    : HistogramBase(name) {
  DCHECK(ranges) << name << ": " << minimum << "-" << maximum;
  unlogged_samples_.reset(new SampleVector(HashMetricName(name), ranges));
  logged_samples_.reset(new SampleVector(unlogged_samples_->id(), ranges));
}

Histogram::Histogram(const char* name,
                     Sample minimum,
                     Sample maximum,
                     const BucketRanges* ranges,
                     const DelayedPersistentAllocation& counts,
                     const DelayedPersistentAllocation& logged_counts,
                     HistogramSamples::Metadata* meta,
                     HistogramSamples::Metadata* logged_meta)
    : HistogramBase(name) {
  DCHECK(ranges) << name << ": " << minimum << "-" << maximum;
  unlogged_samples_.reset(
      new PersistentSampleVector(HashMetricName(name), ranges, meta, counts));
  logged_samples_.reset(new PersistentSampleVector(
      unlogged_samples_->id(), ranges, logged_meta, logged_counts));
}

Histogram::~Histogram() = default;

bool Histogram::PrintEmptyBucket(uint32_t index) const {
  return true;
}

// Use the actual bucket widths (like a linear histogram) until the widths get
// over some transition value, and then use that transition width.  Exponentials
// get so big so fast (and we don't expect to see a lot of entries in the large
// buckets), so we need this to make it possible to see what is going on and
// not have 0-graphical-height buckets.
double Histogram::GetBucketSize(Count current, uint32_t i) const {
  DCHECK_GT(ranges(i + 1), ranges(i));
  static const double kTransitionWidth = 5;
  double denominator = ranges(i + 1) - ranges(i);
  if (denominator > kTransitionWidth)
    denominator = kTransitionWidth;  // Stop trying to normalize.
  return current/denominator;
}

const std::string Histogram::GetAsciiBucketRange(uint32_t i) const {
  return GetSimpleAsciiBucketRange(ranges(i));
}

//------------------------------------------------------------------------------
// Private methods

// static
HistogramBase* Histogram::DeserializeInfoImpl(PickleIterator* iter) {
  std::string histogram_name;
  int flags;
  int declared_min;
  int declared_max;
  uint32_t bucket_count;
  uint32_t range_checksum;

  if (!ReadHistogramArguments(iter, &histogram_name, &flags, &declared_min,
                              &declared_max, &bucket_count, &range_checksum)) {
    return nullptr;
  }

  // Find or create the local version of the histogram in this process.
  HistogramBase* histogram = Histogram::FactoryGet(
      histogram_name, declared_min, declared_max, bucket_count, flags);
  if (!histogram)
    return nullptr;

  // The serialized histogram might be corrupted.
  if (!ValidateRangeChecksum(*histogram, range_checksum))
    return nullptr;

  return histogram;
}

std::unique_ptr<SampleVector> Histogram::SnapshotAllSamples() const {
  std::unique_ptr<SampleVector> samples = SnapshotUnloggedSamples();
  samples->Add(*logged_samples_);
  return samples;
}

std::unique_ptr<SampleVector> Histogram::SnapshotUnloggedSamples() const {
  std::unique_ptr<SampleVector> samples(
      new SampleVector(unlogged_samples_->id(), bucket_ranges()));
  samples->Add(*unlogged_samples_);
  return samples;
}

void Histogram::WriteAsciiBody(const SampleVector& snapshot,
                               bool graph_it,
                               const std::string& newline,
                               std::string* output) const {
  Count sample_count = snapshot.TotalCount();

  // Prepare to normalize graphical rendering of bucket contents.
  double max_size = 0;
  if (graph_it)
    max_size = GetPeakBucketSize(snapshot);

  // Calculate space needed to print bucket range numbers.  Leave room to print
  // nearly the largest bucket range without sliding over the histogram.
  uint32_t largest_non_empty_bucket = bucket_count() - 1;
  while (0 == snapshot.GetCountAtIndex(largest_non_empty_bucket)) {
    if (0 == largest_non_empty_bucket)
      break;  // All buckets are empty.
    --largest_non_empty_bucket;
  }

  // Calculate largest print width needed for any of our bucket range displays.
  size_t print_width = 1;
  for (uint32_t i = 0; i < bucket_count(); ++i) {
    if (snapshot.GetCountAtIndex(i)) {
      size_t width = GetAsciiBucketRange(i).size() + 1;
      if (width > print_width)
        print_width = width;
    }
  }

  int64_t remaining = sample_count;
  int64_t past = 0;
  // Output the actual histogram graph.
  for (uint32_t i = 0; i < bucket_count(); ++i) {
    Count current = snapshot.GetCountAtIndex(i);
    if (!current && !PrintEmptyBucket(i))
      continue;
    remaining -= current;
    std::string range = GetAsciiBucketRange(i);
    output->append(range);
    for (size_t j = 0; range.size() + j < print_width + 1; ++j)
      output->push_back(' ');
    if (0 == current && i < bucket_count() - 1 &&
        0 == snapshot.GetCountAtIndex(i + 1)) {
      while (i < bucket_count() - 1 && 0 == snapshot.GetCountAtIndex(i + 1)) {
        ++i;
      }
      output->append("... ");
      output->append(newline);
      continue;  // No reason to plot emptiness.
    }
    double current_size = GetBucketSize(current, i);
    if (graph_it)
      WriteAsciiBucketGraph(current_size, max_size, output);
    WriteAsciiBucketContext(past, current, remaining, i, output);
    output->append(newline);
    past += current;
  }
  DCHECK_EQ(sample_count, past);
}

double Histogram::GetPeakBucketSize(const SampleVectorBase& samples) const {
  double max = 0;
  for (uint32_t i = 0; i < bucket_count() ; ++i) {
    double current_size = GetBucketSize(samples.GetCountAtIndex(i), i);
    if (current_size > max)
      max = current_size;
  }
  return max;
}

void Histogram::WriteAsciiHeader(const SampleVectorBase& samples,
                                 std::string* output) const {
  Count sample_count = samples.TotalCount();

  StringAppendF(output, "Histogram: %s recorded %d samples", histogram_name(),
                sample_count);
  if (sample_count == 0) {
    DCHECK_EQ(samples.sum(), 0);
  } else {
    double mean = static_cast<float>(samples.sum()) / sample_count;
    StringAppendF(output, ", mean = %.1f", mean);
  }
  if (flags())
    StringAppendF(output, " (flags = 0x%x)", flags());
}

void Histogram::WriteAsciiBucketContext(const int64_t past,
                                        const Count current,
                                        const int64_t remaining,
                                        const uint32_t i,
                                        std::string* output) const {
  double scaled_sum = (past + current + remaining) / 100.0;
  WriteAsciiBucketValue(current, scaled_sum, output);
  if (0 < i) {
    double percentage = past / scaled_sum;
    StringAppendF(output, " {%3.1f%%}", percentage);
  }
}

void Histogram::GetParameters(DictionaryValue* params) const {
  params->SetString("type", HistogramTypeToString(GetHistogramType()));
  params->SetIntKey("min", declared_min());
  params->SetIntKey("max", declared_max());
  params->SetIntKey("bucket_count", static_cast<int>(bucket_count()));
}

void Histogram::GetCountAndBucketData(Count* count,
                                      int64_t* sum,
                                      ListValue* buckets) const {
  std::unique_ptr<SampleVector> snapshot = SnapshotAllSamples();
  *count = snapshot->TotalCount();
  *sum = snapshot->sum();
  uint32_t index = 0;
  for (uint32_t i = 0; i < bucket_count(); ++i) {
    Sample count_at_index = snapshot->GetCountAtIndex(i);
    if (count_at_index > 0) {
      std::unique_ptr<DictionaryValue> bucket_value(new DictionaryValue());
      bucket_value->SetIntKey("low", ranges(i));
      if (i != bucket_count() - 1)
        bucket_value->SetIntKey("high", ranges(i + 1));
      bucket_value->SetIntKey("count", count_at_index);
      buckets->Set(index, std::move(bucket_value));
      ++index;
    }
  }
}

//------------------------------------------------------------------------------
// LinearHistogram: This histogram uses a traditional set of evenly spaced
// buckets.
//------------------------------------------------------------------------------

class LinearHistogram::Factory : public Histogram::Factory {
 public:
  Factory(const std::string& name,
          HistogramBase::Sample minimum,
          HistogramBase::Sample maximum,
          uint32_t bucket_count,
          int32_t flags,
          const DescriptionPair* descriptions)
    : Histogram::Factory(name, LINEAR_HISTOGRAM, minimum, maximum,
                         bucket_count, flags) {
    descriptions_ = descriptions;
  }

 protected:
  BucketRanges* CreateRanges() override {
    BucketRanges* ranges = new BucketRanges(bucket_count_ + 1);
    LinearHistogram::InitializeBucketRanges(minimum_, maximum_, ranges);
    return ranges;
  }

  std::unique_ptr<HistogramBase> HeapAlloc(
      const BucketRanges* ranges) override {
    return WrapUnique(new LinearHistogram(GetPermanentName(name_), minimum_,
                                          maximum_, ranges));
  }

  void FillHistogram(HistogramBase* base_histogram) override {
    Histogram::Factory::FillHistogram(base_histogram);
    // Normally, |base_histogram| should have type LINEAR_HISTOGRAM or be
    // inherited from it. However, if it's expired, it will actually be a
    // DUMMY_HISTOGRAM. Skip filling in that case.
    if (base_histogram->GetHistogramType() == DUMMY_HISTOGRAM)
      return;
    LinearHistogram* histogram = static_cast<LinearHistogram*>(base_histogram);
    // Set range descriptions.
    if (descriptions_) {
      for (int i = 0; descriptions_[i].description; ++i) {
        histogram->bucket_description_[descriptions_[i].sample] =
            descriptions_[i].description;
      }
    }
  }

 private:
  const DescriptionPair* descriptions_;

  DISALLOW_COPY_AND_ASSIGN(Factory);
};

LinearHistogram::~LinearHistogram() = default;

HistogramBase* LinearHistogram::FactoryGet(const std::string& name,
                                           Sample minimum,
                                           Sample maximum,
                                           uint32_t bucket_count,
                                           int32_t flags) {
  return FactoryGetWithRangeDescription(name, minimum, maximum, bucket_count,
                                        flags, NULL);
}

HistogramBase* LinearHistogram::FactoryTimeGet(const std::string& name,
                                               TimeDelta minimum,
                                               TimeDelta maximum,
                                               uint32_t bucket_count,
                                               int32_t flags) {
  DCHECK_LT(minimum.InMilliseconds(), std::numeric_limits<Sample>::max());
  DCHECK_LT(maximum.InMilliseconds(), std::numeric_limits<Sample>::max());
  return FactoryGet(name, static_cast<Sample>(minimum.InMilliseconds()),
                    static_cast<Sample>(maximum.InMilliseconds()), bucket_count,
                    flags);
}

HistogramBase* LinearHistogram::FactoryGet(const char* name,
                                           Sample minimum,
                                           Sample maximum,
                                           uint32_t bucket_count,
                                           int32_t flags) {
  return FactoryGet(std::string(name), minimum, maximum, bucket_count, flags);
}

HistogramBase* LinearHistogram::FactoryTimeGet(const char* name,
                                               TimeDelta minimum,
                                               TimeDelta maximum,
                                               uint32_t bucket_count,
                                               int32_t flags) {
  return FactoryTimeGet(std::string(name),  minimum, maximum, bucket_count,
                        flags);
}

std::unique_ptr<HistogramBase> LinearHistogram::PersistentCreate(
    const char* name,
    Sample minimum,
    Sample maximum,
    const BucketRanges* ranges,
    const DelayedPersistentAllocation& counts,
    const DelayedPersistentAllocation& logged_counts,
    HistogramSamples::Metadata* meta,
    HistogramSamples::Metadata* logged_meta) {
  return WrapUnique(new LinearHistogram(name, minimum, maximum, ranges, counts,
                                        logged_counts, meta, logged_meta));
}

HistogramBase* LinearHistogram::FactoryGetWithRangeDescription(
    const std::string& name,
    Sample minimum,
    Sample maximum,
    uint32_t bucket_count,
    int32_t flags,
    const DescriptionPair descriptions[]) {
  // Originally, histograms were required to have at least one sample value
  // plus underflow and overflow buckets. For single-entry enumerations,
  // that one value is usually zero (which IS the underflow bucket)
  // resulting in a |maximum| value of 1 (the exclusive upper-bound) and only
  // the two outlier buckets. Handle this by making max==2 and buckets==3.
  // This usually won't have any cost since the single-value-optimization
  // will be used until the count exceeds 16 bits.
  if (maximum == 1 && bucket_count == 2) {
    maximum = 2;
    bucket_count = 3;
  }

  bool valid_arguments = Histogram::InspectConstructionArguments(
      name, &minimum, &maximum, &bucket_count);
  DCHECK(valid_arguments) << name;

  return Factory(name, minimum, maximum, bucket_count, flags, descriptions)
      .Build();
}

HistogramType LinearHistogram::GetHistogramType() const {
  return LINEAR_HISTOGRAM;
}

LinearHistogram::LinearHistogram(const char* name,
                                 Sample minimum,
                                 Sample maximum,
                                 const BucketRanges* ranges)
    : Histogram(name, minimum, maximum, ranges) {}

LinearHistogram::LinearHistogram(
    const char* name,
    Sample minimum,
    Sample maximum,
    const BucketRanges* ranges,
    const DelayedPersistentAllocation& counts,
    const DelayedPersistentAllocation& logged_counts,
    HistogramSamples::Metadata* meta,
    HistogramSamples::Metadata* logged_meta)
    : Histogram(name,
                minimum,
                maximum,
                ranges,
                counts,
                logged_counts,
                meta,
                logged_meta) {}

double LinearHistogram::GetBucketSize(Count current, uint32_t i) const {
  DCHECK_GT(ranges(i + 1), ranges(i));
  // Adjacent buckets with different widths would have "surprisingly" many (few)
  // samples in a histogram if we didn't normalize this way.
  double denominator = ranges(i + 1) - ranges(i);
  return current/denominator;
}

const std::string LinearHistogram::GetAsciiBucketRange(uint32_t i) const {
  int range = ranges(i);
  BucketDescriptionMap::const_iterator it = bucket_description_.find(range);
  if (it == bucket_description_.end())
    return Histogram::GetAsciiBucketRange(i);
  return it->second;
}

bool LinearHistogram::PrintEmptyBucket(uint32_t index) const {
  return bucket_description_.find(ranges(index)) == bucket_description_.end();
}

// static
void LinearHistogram::InitializeBucketRanges(Sample minimum,
                                             Sample maximum,
                                             BucketRanges* ranges) {
  double min = minimum;
  double max = maximum;
  size_t bucket_count = ranges->bucket_count();

  for (size_t i = 1; i < bucket_count; ++i) {
    double linear_range =
        (min * (bucket_count - 1 - i) + max * (i - 1)) / (bucket_count - 2);
    uint32_t range = static_cast<Sample>(linear_range + 0.5);
    ranges->set_range(i, range);
  }
  ranges->set_range(ranges->bucket_count(), HistogramBase::kSampleType_MAX);
  ranges->ResetChecksum();
}

// static
HistogramBase* LinearHistogram::DeserializeInfoImpl(PickleIterator* iter) {
  std::string histogram_name;
  int flags;
  int declared_min;
  int declared_max;
  uint32_t bucket_count;
  uint32_t range_checksum;

  if (!ReadHistogramArguments(iter, &histogram_name, &flags, &declared_min,
                              &declared_max, &bucket_count, &range_checksum)) {
    return nullptr;
  }

  HistogramBase* histogram = LinearHistogram::FactoryGet(
      histogram_name, declared_min, declared_max, bucket_count, flags);
  if (!histogram)
    return nullptr;

  if (!ValidateRangeChecksum(*histogram, range_checksum)) {
    // The serialized histogram might be corrupted.
    return nullptr;
  }
  return histogram;
}

//------------------------------------------------------------------------------
// ScaledLinearHistogram: This is a wrapper around a LinearHistogram that
// scales input counts.
//------------------------------------------------------------------------------

ScaledLinearHistogram::ScaledLinearHistogram(const char* name,
                                             Sample minimum,
                                             Sample maximum,
                                             uint32_t bucket_count,
                                             int32_t scale,
                                             int32_t flags)
    : histogram_(static_cast<LinearHistogram*>(
          LinearHistogram::FactoryGet(name,
                                      minimum,
                                      maximum,
                                      bucket_count,
                                      flags))),
      scale_(scale) {
  DCHECK(histogram_);
  DCHECK_LT(1, scale);
  DCHECK_EQ(1, minimum);
  CHECK_EQ(static_cast<Sample>(bucket_count), maximum - minimum + 2)
      << " ScaledLinearHistogram requires buckets of size 1";

  remainders_.resize(histogram_->bucket_count(), 0);
}

ScaledLinearHistogram::~ScaledLinearHistogram() = default;

void ScaledLinearHistogram::AddScaledCount(Sample value, int count) {
  if (count == 0)
    return;
  if (count < 0) {
    NOTREACHED();
    return;
  }
  const int32_t max_value =
      static_cast<int32_t>(histogram_->bucket_count() - 1);
  if (value > max_value)
    value = max_value;
  if (value < 0)
    value = 0;

  int scaled_count = count / scale_;
  subtle::Atomic32 remainder = count - scaled_count * scale_;

  // ScaledLinearHistogram currently requires 1-to-1 mappings between value
  // and bucket which alleviates the need to do a bucket lookup here (something
  // that is internal to the HistogramSamples object).
  if (remainder > 0) {
    remainder =
        subtle::NoBarrier_AtomicIncrement(&remainders_[value], remainder);
    // If remainder passes 1/2 scale, increment main count (thus rounding up).
    // The remainder is decremented by the full scale, though, which will
    // cause it to go negative and thus requrire another increase by the full
    // scale amount before another bump of the scaled count.
    if (remainder >= scale_ / 2) {
      scaled_count += 1;
      subtle::NoBarrier_AtomicIncrement(&remainders_[value], -scale_);
    }
  }

  if (scaled_count > 0)
    histogram_->AddCount(value, scaled_count);
}

//------------------------------------------------------------------------------
// This section provides implementation for BooleanHistogram.
//------------------------------------------------------------------------------

class BooleanHistogram::Factory : public Histogram::Factory {
 public:
  Factory(const std::string& name, int32_t flags)
    : Histogram::Factory(name, BOOLEAN_HISTOGRAM, 1, 2, 3, flags) {}

 protected:
  BucketRanges* CreateRanges() override {
    BucketRanges* ranges = new BucketRanges(3 + 1);
    LinearHistogram::InitializeBucketRanges(1, 2, ranges);
    return ranges;
  }

  std::unique_ptr<HistogramBase> HeapAlloc(
      const BucketRanges* ranges) override {
    return WrapUnique(new BooleanHistogram(GetPermanentName(name_), ranges));
  }

 private:
  DISALLOW_COPY_AND_ASSIGN(Factory);
};

HistogramBase* BooleanHistogram::FactoryGet(const std::string& name,
                                            int32_t flags) {
  return Factory(name, flags).Build();
}

HistogramBase* BooleanHistogram::FactoryGet(const char* name, int32_t flags) {
  return FactoryGet(std::string(name), flags);
}

std::unique_ptr<HistogramBase> BooleanHistogram::PersistentCreate(
    const char* name,
    const BucketRanges* ranges,
    const DelayedPersistentAllocation& counts,
    const DelayedPersistentAllocation& logged_counts,
    HistogramSamples::Metadata* meta,
    HistogramSamples::Metadata* logged_meta) {
  return WrapUnique(new BooleanHistogram(name, ranges, counts, logged_counts,
                                         meta, logged_meta));
}

HistogramType BooleanHistogram::GetHistogramType() const {
  return BOOLEAN_HISTOGRAM;
}

BooleanHistogram::BooleanHistogram(const char* name, const BucketRanges* ranges)
    : LinearHistogram(name, 1, 2, ranges) {}

BooleanHistogram::BooleanHistogram(
    const char* name,
    const BucketRanges* ranges,
    const DelayedPersistentAllocation& counts,
    const DelayedPersistentAllocation& logged_counts,
    HistogramSamples::Metadata* meta,
    HistogramSamples::Metadata* logged_meta)
    : LinearHistogram(name,
                      1,
                      2,
                      ranges,
                      counts,
                      logged_counts,
                      meta,
                      logged_meta) {}

HistogramBase* BooleanHistogram::DeserializeInfoImpl(PickleIterator* iter) {
  std::string histogram_name;
  int flags;
  int declared_min;
  int declared_max;
  uint32_t bucket_count;
  uint32_t range_checksum;

  if (!ReadHistogramArguments(iter, &histogram_name, &flags, &declared_min,
                              &declared_max, &bucket_count, &range_checksum)) {
    return nullptr;
  }

  HistogramBase* histogram = BooleanHistogram::FactoryGet(
      histogram_name, flags);
  if (!histogram)
    return nullptr;

  if (!ValidateRangeChecksum(*histogram, range_checksum)) {
    // The serialized histogram might be corrupted.
    return nullptr;
  }
  return histogram;
}

//------------------------------------------------------------------------------
// CustomHistogram:
//------------------------------------------------------------------------------

class CustomHistogram::Factory : public Histogram::Factory {
 public:
  Factory(const std::string& name,
          const std::vector<Sample>* custom_ranges,
          int32_t flags)
    : Histogram::Factory(name, CUSTOM_HISTOGRAM, 0, 0, 0, flags) {
    custom_ranges_ = custom_ranges;
  }

 protected:
  BucketRanges* CreateRanges() override {
    // Remove the duplicates in the custom ranges array.
    std::vector<int> ranges = *custom_ranges_;
    ranges.push_back(0);  // Ensure we have a zero value.
    ranges.push_back(HistogramBase::kSampleType_MAX);
    std::sort(ranges.begin(), ranges.end());
    ranges.erase(std::unique(ranges.begin(), ranges.end()), ranges.end());

    BucketRanges* bucket_ranges = new BucketRanges(ranges.size());
    for (uint32_t i = 0; i < ranges.size(); i++) {
      bucket_ranges->set_range(i, ranges[i]);
    }
    bucket_ranges->ResetChecksum();
    return bucket_ranges;
  }

  std::unique_ptr<HistogramBase> HeapAlloc(
      const BucketRanges* ranges) override {
    return WrapUnique(new CustomHistogram(GetPermanentName(name_), ranges));
  }

 private:
  const std::vector<Sample>* custom_ranges_;

  DISALLOW_COPY_AND_ASSIGN(Factory);
};

HistogramBase* CustomHistogram::FactoryGet(
    const std::string& name,
    const std::vector<Sample>& custom_ranges,
    int32_t flags) {
  CHECK(ValidateCustomRanges(custom_ranges));

  return Factory(name, &custom_ranges, flags).Build();
}

HistogramBase* CustomHistogram::FactoryGet(
    const char* name,
    const std::vector<Sample>& custom_ranges,
    int32_t flags) {
  return FactoryGet(std::string(name), custom_ranges, flags);
}

std::unique_ptr<HistogramBase> CustomHistogram::PersistentCreate(
    const char* name,
    const BucketRanges* ranges,
    const DelayedPersistentAllocation& counts,
    const DelayedPersistentAllocation& logged_counts,
    HistogramSamples::Metadata* meta,
    HistogramSamples::Metadata* logged_meta) {
  return WrapUnique(new CustomHistogram(name, ranges, counts, logged_counts,
                                        meta, logged_meta));
}

HistogramType CustomHistogram::GetHistogramType() const {
  return CUSTOM_HISTOGRAM;
}

// static
std::vector<Sample> CustomHistogram::ArrayToCustomEnumRanges(
    base::span<const Sample> values) {
  std::vector<Sample> all_values;
  for (Sample value : values) {
    all_values.push_back(value);

    // Ensure that a guard bucket is added. If we end up with duplicate
    // values, FactoryGet will take care of removing them.
    all_values.push_back(value + 1);
  }
  return all_values;
}

CustomHistogram::CustomHistogram(const char* name, const BucketRanges* ranges)
    : Histogram(name,
                ranges->range(1),
                ranges->range(ranges->bucket_count() - 1),
                ranges) {}

CustomHistogram::CustomHistogram(
    const char* name,
    const BucketRanges* ranges,
    const DelayedPersistentAllocation& counts,
    const DelayedPersistentAllocation& logged_counts,
    HistogramSamples::Metadata* meta,
    HistogramSamples::Metadata* logged_meta)
    : Histogram(name,
                ranges->range(1),
                ranges->range(ranges->bucket_count() - 1),
                ranges,
                counts,
                logged_counts,
                meta,
                logged_meta) {}

void CustomHistogram::SerializeInfoImpl(Pickle* pickle) const {
  Histogram::SerializeInfoImpl(pickle);

  // Serialize ranges. First and last ranges are alwasy 0 and INT_MAX, so don't
  // write them.
  for (uint32_t i = 1; i < bucket_ranges()->bucket_count(); ++i)
    pickle->WriteInt(bucket_ranges()->range(i));
}

double CustomHistogram::GetBucketSize(Count current, uint32_t i) const {
  // If this is a histogram of enum values, normalizing the bucket count
  // by the bucket range is not helpful, so just return the bucket count.
  return current;
}

// static
HistogramBase* CustomHistogram::DeserializeInfoImpl(PickleIterator* iter) {
  std::string histogram_name;
  int flags;
  int declared_min;
  int declared_max;
  uint32_t bucket_count;
  uint32_t range_checksum;

  if (!ReadHistogramArguments(iter, &histogram_name, &flags, &declared_min,
                              &declared_max, &bucket_count, &range_checksum)) {
    return nullptr;
  }

  // First and last ranges are not serialized.
  std::vector<Sample> sample_ranges(bucket_count - 1);

  for (uint32_t i = 0; i < sample_ranges.size(); ++i) {
    if (!iter->ReadInt(&sample_ranges[i]))
      return nullptr;
  }

  HistogramBase* histogram = CustomHistogram::FactoryGet(
      histogram_name, sample_ranges, flags);
  if (!histogram)
    return nullptr;

  if (!ValidateRangeChecksum(*histogram, range_checksum)) {
    // The serialized histogram might be corrupted.
    return nullptr;
  }
  return histogram;
}

// static
bool CustomHistogram::ValidateCustomRanges(
    const std::vector<Sample>& custom_ranges) {
  bool has_valid_range = false;
  for (uint32_t i = 0; i < custom_ranges.size(); i++) {
    Sample sample = custom_ranges[i];
    if (sample < 0 || sample > HistogramBase::kSampleType_MAX - 1)
      return false;
    if (sample != 0)
      has_valid_range = true;
  }
  return has_valid_range;
}

}  // namespace base
