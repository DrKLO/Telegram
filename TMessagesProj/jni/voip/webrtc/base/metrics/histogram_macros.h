// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_METRICS_HISTOGRAM_MACROS_H_
#define BASE_METRICS_HISTOGRAM_MACROS_H_

#include "base/macros.h"
#include "base/metrics/histogram.h"
#include "base/metrics/histogram_macros_internal.h"
#include "base/metrics/histogram_macros_local.h"
#include "base/time/time.h"


// Macros for efficient use of histograms.
//
// For best practices on deciding when to emit to a histogram and what form
// the histogram should take, see
// https://chromium.googlesource.com/chromium/src.git/+/HEAD/tools/metrics/histograms/README.md

// All of these macros must be called with |name| as a runtime constant - it
// doesn't have to literally be a constant, but it must be the same string on
// all calls from a particular call site. If this rule is violated, it is
// possible the data will be written to the wrong histogram.

//------------------------------------------------------------------------------
// Enumeration histograms.

// These macros create histograms for enumerated data. Ideally, the data should
// be of the form of "event occurs, log the result". We recommended not putting
// related but not directly connected data as enums within the same histogram.
// You should be defining an associated Enum, and the input sample should be
// an element of the Enum.
// All of these macros must be called with |name| as a runtime constant.

// The first variant of UMA_HISTOGRAM_ENUMERATION accepts two arguments: the
// histogram name and the enum sample. It deduces the correct boundary value to
// use by looking for an enumerator with the name kMaxValue. kMaxValue should
// share the value of the highest enumerator: this avoids switch statements
// having to handle a sentinel no-op value.
//
// Sample usage:
//   // These values are logged to UMA. Entries should not be renumbered and
//   // numeric values should never be reused. Please keep in sync with "MyEnum"
//   // in src/tools/metrics/histograms/enums.xml.
//   enum class MyEnum {
//     kFirstValue = 0,
//     kSecondValue = 1,
//     ...
//     kFinalValue = N,
//     kMaxValue = kFinalValue,
//   };
//   UMA_HISTOGRAM_ENUMERATION("My.Enumeration", MyEnum::kSomeValue);
//
// The second variant requires three arguments: the first two are the same as
// before, and the third argument is the enum boundary: this must be strictly
// greater than any other enumerator that will be sampled.
//
// Sample usage:
//   // These values are logged to UMA. Entries should not be renumbered and
//   // numeric values should never be reused. Please keep in sync with "MyEnum"
//   // in src/tools/metrics/histograms/enums.xml.
//   enum class MyEnum {
//     FIRST_VALUE = 0,
//     SECOND_VALUE = 1,
//     ...
//     FINAL_VALUE = N,
//     COUNT
//   };
//   UMA_HISTOGRAM_ENUMERATION("My.Enumeration",
//                             MyEnum::SOME_VALUE, MyEnum::COUNT);
//
// Note: If the enum is used in a switch, it is often desirable to avoid writing
// a case statement to handle an unused sentinel value (i.e. COUNT in the above
// example). For scoped enums, this is awkward since it requires casting the
// enum to an arithmetic type and adding one. Instead, prefer the two argument
// version of the macro which automatically deduces the boundary from kMaxValue.
#define UMA_HISTOGRAM_ENUMERATION(name, ...)                            \
  INTERNAL_UMA_HISTOGRAM_ENUMERATION_GET_MACRO(                         \
      __VA_ARGS__, INTERNAL_UMA_HISTOGRAM_ENUMERATION_SPECIFY_BOUNDARY, \
      INTERNAL_UMA_HISTOGRAM_ENUMERATION_DEDUCE_BOUNDARY)               \
  (name, __VA_ARGS__, base::HistogramBase::kUmaTargetedHistogramFlag)

// As above but "scaled" count to avoid overflows caused by increments of
// large amounts. See UMA_HISTOGRAM_SCALED_EXACT_LINEAR for more information.
// Only the new format utilizing an internal kMaxValue is supported.
// It'll be necessary to #include "base/lazy_instance.h" to use this macro.
//   name: Full constant name of the histogram (must not change between calls).
//   sample: Bucket to be incremented.
//   count: Amount by which to increment.
//   scale: Amount by which |count| is divided.

// Sample usage:
//    UMA_HISTOGRAM_SCALED_ENUMERATION("FooKiB", kEnumValue, byte_count, 1024)
#define UMA_HISTOGRAM_SCALED_ENUMERATION(name, sample, count, scale) \
  INTERNAL_HISTOGRAM_SCALED_ENUMERATION_WITH_FLAG(                   \
      name, sample, count, scale,                                    \
      base::HistogramBase::kUmaTargetedHistogramFlag)

// Histogram for boolean values.

// Sample usage:
//   UMA_HISTOGRAM_BOOLEAN("Histogram.Boolean", bool);
#define UMA_HISTOGRAM_BOOLEAN(name, sample)                                    \
    STATIC_HISTOGRAM_POINTER_BLOCK(name, AddBoolean(sample),                   \
        base::BooleanHistogram::FactoryGet(name,                               \
            base::HistogramBase::kUmaTargetedHistogramFlag))

//------------------------------------------------------------------------------
// Linear histograms.

// All of these macros must be called with |name| as a runtime constant.

// Used for capturing integer data with a linear bucketing scheme. This can be
// used when you want the exact value of some small numeric count, with a max of
// 100 or less. If you need to capture a range of greater than 100, we recommend
// the use of the COUNT histograms below.

// Sample usage:
//   UMA_HISTOGRAM_EXACT_LINEAR("Histogram.Linear", count, 10);
#define UMA_HISTOGRAM_EXACT_LINEAR(name, sample, value_max) \
  INTERNAL_HISTOGRAM_EXACT_LINEAR_WITH_FLAG(                \
      name, sample, value_max, base::HistogramBase::kUmaTargetedHistogramFlag)

// Used for capturing basic percentages. This will be 100 buckets of size 1.

// Sample usage:
//   UMA_HISTOGRAM_PERCENTAGE("Histogram.Percent", percent_as_int);
#define UMA_HISTOGRAM_PERCENTAGE(name, percent_as_int) \
  UMA_HISTOGRAM_EXACT_LINEAR(name, percent_as_int, 101)

//------------------------------------------------------------------------------
// Scaled linear histograms.

// These take |count| and |scale| parameters to allow cumulative reporting of
// large numbers. For example, code might pass a count of 1825 bytes and a scale
// of 1024 bytes to report values in kilobytes. Only the scaled count is
// reported, but the remainder is tracked between calls, so that multiple calls
// will accumulate correctly. Only "exact linear" is supported.
// It'll be necessary to #include "base/lazy_instance.h" to use this macro.
//   name: Full constant name of the histogram (must not change between calls).
//   sample: Bucket to be incremented.
//   count: Amount by which to increment.
//   sample_max: Maximum (exclusive) allowed sample value.
//   scale: Amount by which |count| is divided.

// Sample usage:
//    UMA_HISTOGRAM_SCALED_EXACT_LINER("FooKiB", bucket_no, byte_count,
//                                     kBucketsMax+1, 1024)
#define UMA_HISTOGRAM_SCALED_EXACT_LINEAR(name, sample, count, sample_max, \
                                          scale)                           \
  INTERNAL_HISTOGRAM_SCALED_EXACT_LINEAR_WITH_FLAG(                        \
      name, sample, count, sample_max, scale,                              \
      base::HistogramBase::kUmaTargetedHistogramFlag)

//------------------------------------------------------------------------------
// Count histograms. These are used for collecting numeric data. Note that we
// have macros for more specialized use cases below (memory, time, percentages).

// The number suffixes here refer to the max size of the sample, i.e. COUNT_1000
// will be able to collect samples of counts up to 1000. The default number of
// buckets in all default macros is 50. We recommend erring on the side of too
// large a range versus too short a range.
// These macros default to exponential histograms - i.e. the lengths of the
// bucket ranges exponentially increase as the sample range increases.
// These should *not* be used if you are interested in exact counts, i.e. a
// bucket range of 1. In these cases, you should use the ENUMERATION macros
// defined later. These should also not be used to capture the number of some
// event, i.e. "button X was clicked N times". In this cases, an enum should be
// used, ideally with an appropriate baseline enum entry included.
// All of these macros must be called with |name| as a runtime constant.

// Sample usage:
//   UMA_HISTOGRAM_COUNTS_1M("My.Histogram", sample);

#define UMA_HISTOGRAM_COUNTS_100(name, sample) UMA_HISTOGRAM_CUSTOM_COUNTS(    \
    name, sample, 1, 100, 50)

#define UMA_HISTOGRAM_COUNTS_1000(name, sample) UMA_HISTOGRAM_CUSTOM_COUNTS(   \
    name, sample, 1, 1000, 50)

#define UMA_HISTOGRAM_COUNTS_10000(name, sample) UMA_HISTOGRAM_CUSTOM_COUNTS(  \
    name, sample, 1, 10000, 50)

#define UMA_HISTOGRAM_COUNTS_100000(name, sample) UMA_HISTOGRAM_CUSTOM_COUNTS( \
    name, sample, 1, 100000, 50)

#define UMA_HISTOGRAM_COUNTS_1M(name, sample) UMA_HISTOGRAM_CUSTOM_COUNTS(     \
    name, sample, 1, 1000000, 50)

#define UMA_HISTOGRAM_COUNTS_10M(name, sample) UMA_HISTOGRAM_CUSTOM_COUNTS(    \
    name, sample, 1, 10000000, 50)

// This can be used when the default ranges are not sufficient. This macro lets
// the metric developer customize the min and max of the sampled range, as well
// as the number of buckets recorded.
// Any data outside the range here will be put in underflow and overflow
// buckets. Min values should be >=1 as emitted 0s will still go into the
// underflow bucket.

// Sample usage:
//   UMA_HISTOGRAM_CUSTOM_COUNTS("My.Histogram", sample, 1, 100000000, 50);
#define UMA_HISTOGRAM_CUSTOM_COUNTS(name, sample, min, max, bucket_count)      \
    INTERNAL_HISTOGRAM_CUSTOM_COUNTS_WITH_FLAG(                                \
        name, sample, min, max, bucket_count,                                  \
        base::HistogramBase::kUmaTargetedHistogramFlag)

//------------------------------------------------------------------------------
// Timing histograms. These are used for collecting timing data (generally
// latencies).

// These macros create exponentially sized histograms (lengths of the bucket
// ranges exponentially increase as the sample range increases). The input
// sample is a base::TimeDelta. The output data is measured in ms granularity.
// All of these macros must be called with |name| as a runtime constant.

// Sample usage:
//   UMA_HISTOGRAM_TIMES("My.Timing.Histogram", time_delta);

// Short timings - up to 10 seconds. For high-resolution (microseconds) timings,
// see UMA_HISTOGRAM_CUSTOM_MICROSECONDS_TIMES.
#define UMA_HISTOGRAM_TIMES(name, sample) UMA_HISTOGRAM_CUSTOM_TIMES(          \
    name, sample, base::TimeDelta::FromMilliseconds(1),                        \
    base::TimeDelta::FromSeconds(10), 50)

// Medium timings - up to 3 minutes. Note this starts at 10ms (no good reason,
// but not worth changing).
#define UMA_HISTOGRAM_MEDIUM_TIMES(name, sample) UMA_HISTOGRAM_CUSTOM_TIMES(   \
    name, sample, base::TimeDelta::FromMilliseconds(10),                       \
    base::TimeDelta::FromMinutes(3), 50)

// Long timings - up to an hour.
#define UMA_HISTOGRAM_LONG_TIMES(name, sample) UMA_HISTOGRAM_CUSTOM_TIMES(     \
    name, sample, base::TimeDelta::FromMilliseconds(1),                        \
    base::TimeDelta::FromHours(1), 50)

// Long timings with higher granularity - up to an hour with 100 buckets.
#define UMA_HISTOGRAM_LONG_TIMES_100(name, sample) UMA_HISTOGRAM_CUSTOM_TIMES( \
    name, sample, base::TimeDelta::FromMilliseconds(1),                        \
    base::TimeDelta::FromHours(1), 100)

// This can be used when the default ranges are not sufficient. This macro lets
// the metric developer customize the min and max of the sampled range, as well
// as the number of buckets recorded.

// Sample usage:
//   UMA_HISTOGRAM_CUSTOM_TIMES("Very.Long.Timing.Histogram", time_delta,
//       base::TimeDelta::FromSeconds(1), base::TimeDelta::FromDays(1), 100);
#define UMA_HISTOGRAM_CUSTOM_TIMES(name, sample, min, max, bucket_count) \
  STATIC_HISTOGRAM_POINTER_BLOCK(                                        \
      name, AddTimeMillisecondsGranularity(sample),                      \
      base::Histogram::FactoryTimeGet(                                   \
          name, min, max, bucket_count,                                  \
          base::HistogramBase::kUmaTargetedHistogramFlag))

// Same as UMA_HISTOGRAM_CUSTOM_TIMES but reports |sample| in microseconds,
// dropping the report if this client doesn't have a high-resolution clock.
//
// Note: dropping reports on clients with low-resolution clocks means these
// reports will be biased to a portion of the population on Windows. See
// Windows.HasHighResolutionTimeTicks for the affected sample.
//
// Sample usage:
//  UMA_HISTOGRAM_CUSTOM_MICROSECONDS_TIMES(
//      "High.Resolution.TimingMicroseconds.Histogram", time_delta,
//      base::TimeDelta::FromMicroseconds(1),
//      base::TimeDelta::FromMilliseconds(10), 100);
#define UMA_HISTOGRAM_CUSTOM_MICROSECONDS_TIMES(name, sample, min, max, \
                                                bucket_count)           \
  STATIC_HISTOGRAM_POINTER_BLOCK(                                       \
      name, AddTimeMicrosecondsGranularity(sample),                     \
      base::Histogram::FactoryMicrosecondsTimeGet(                      \
          name, min, max, bucket_count,                                 \
          base::HistogramBase::kUmaTargetedHistogramFlag))

// Scoped class which logs its time on this earth in milliseconds as a UMA
// statistic. This is recommended for when you want a histogram which measures
// the time it takes for a method to execute. This measures up to 10 seconds. It
// uses UMA_HISTOGRAM_TIMES under the hood.

// Sample usage:
//   void Function() {
//     SCOPED_UMA_HISTOGRAM_TIMER("Component.FunctionTime");
//     ...
//   }
#define SCOPED_UMA_HISTOGRAM_TIMER(name)                                       \
  INTERNAL_SCOPED_UMA_HISTOGRAM_TIMER_EXPANDER(name, false, __COUNTER__)

// Similar scoped histogram timer, but this uses UMA_HISTOGRAM_LONG_TIMES_100,
// which measures up to an hour, and uses 100 buckets. This is more expensive
// to store, so only use if this often takes >10 seconds.
#define SCOPED_UMA_HISTOGRAM_LONG_TIMER(name)                                  \
  INTERNAL_SCOPED_UMA_HISTOGRAM_TIMER_EXPANDER(name, true, __COUNTER__)


//------------------------------------------------------------------------------
// Memory histograms.

// These macros create exponentially sized histograms (lengths of the bucket
// ranges exponentially increase as the sample range increases). The input
// sample must be a number measured in kilobytes.
// All of these macros must be called with |name| as a runtime constant.

// Sample usage:
//   UMA_HISTOGRAM_MEMORY_KB("My.Memory.Histogram", memory_in_kb);

// Used to measure common KB-granularity memory stats. Range is up to 500000KB -
// approximately 500M.
#define UMA_HISTOGRAM_MEMORY_KB(name, sample)                                  \
    UMA_HISTOGRAM_CUSTOM_COUNTS(name, sample, 1000, 500000, 50)

// Used to measure common MB-granularity memory stats. Range is up to ~64G.
#define UMA_HISTOGRAM_MEMORY_LARGE_MB(name, sample)                            \
    UMA_HISTOGRAM_CUSTOM_COUNTS(name, sample, 1, 64000, 100)


//------------------------------------------------------------------------------
// Stability-specific histograms.

// Histograms logged in as stability histograms will be included in the initial
// stability log. See comments by declaration of
// MetricsService::PrepareInitialStabilityLog().
// All of these macros must be called with |name| as a runtime constant.

// For details on usage, see the documentation on the non-stability equivalents.

#define UMA_STABILITY_HISTOGRAM_BOOLEAN(name, sample) \
  STATIC_HISTOGRAM_POINTER_BLOCK(                     \
      name, AddBoolean(sample),                       \
      base::BooleanHistogram::FactoryGet(             \
          name, base::HistogramBase::kUmaStabilityHistogramFlag))

#define UMA_STABILITY_HISTOGRAM_COUNTS_100(name, sample)                       \
    UMA_STABILITY_HISTOGRAM_CUSTOM_COUNTS(name, sample, 1, 100, 50)

#define UMA_STABILITY_HISTOGRAM_CUSTOM_COUNTS(name, sample, min, max,          \
                                              bucket_count)                    \
    INTERNAL_HISTOGRAM_CUSTOM_COUNTS_WITH_FLAG(                                \
        name, sample, min, max, bucket_count,                                  \
        base::HistogramBase::kUmaStabilityHistogramFlag)

#define UMA_STABILITY_HISTOGRAM_ENUMERATION(name, ...)                  \
  INTERNAL_UMA_HISTOGRAM_ENUMERATION_GET_MACRO(                         \
      __VA_ARGS__, INTERNAL_UMA_HISTOGRAM_ENUMERATION_SPECIFY_BOUNDARY, \
      INTERNAL_UMA_HISTOGRAM_ENUMERATION_DEDUCE_BOUNDARY)               \
  (name, __VA_ARGS__, base::HistogramBase::kUmaStabilityHistogramFlag)

#define UMA_STABILITY_HISTOGRAM_LONG_TIMES(name, sample) \
  STATIC_HISTOGRAM_POINTER_BLOCK(                        \
      name, AddTimeMillisecondsGranularity(sample),      \
      base::Histogram::FactoryTimeGet(                   \
          name, base::TimeDelta::FromMilliseconds(1),    \
          base::TimeDelta::FromHours(1), 50,             \
          base::HistogramBase::kUmaStabilityHistogramFlag))

#define UMA_STABILITY_HISTOGRAM_PERCENTAGE(name, percent_as_int) \
  INTERNAL_HISTOGRAM_EXACT_LINEAR_WITH_FLAG(                     \
      name, percent_as_int, 101,                                 \
      base::HistogramBase::kUmaStabilityHistogramFlag)

//------------------------------------------------------------------------------
// Histogram instantiation helpers.

// Support a collection of histograms, perhaps one for each entry in an
// enumeration. This macro manages a block of pointers, adding to a specific
// one by its index.
//
// A typical instantiation looks something like this:
//  STATIC_HISTOGRAM_POINTER_GROUP(
//      GetHistogramNameForIndex(histogram_index),
//      histogram_index, MAXIMUM_HISTOGRAM_INDEX, Add(some_delta),
//      base::Histogram::FactoryGet(
//          GetHistogramNameForIndex(histogram_index),
//          MINIMUM_SAMPLE, MAXIMUM_SAMPLE, BUCKET_COUNT,
//          base::HistogramBase::kUmaTargetedHistogramFlag));
//
// Though it seems inefficient to generate the name twice, the first
// instance will be used only for DCHECK builds and the second will
// execute only during the first access to the given index, after which
// the pointer is cached and the name never needed again.
#define STATIC_HISTOGRAM_POINTER_GROUP(                                     \
    constant_histogram_name, index, constant_maximum,                       \
    histogram_add_method_invocation, histogram_factory_get_invocation)      \
  do {                                                                      \
    static std::atomic_uintptr_t atomic_histograms[constant_maximum];       \
    DCHECK_LE(0, index);                                                    \
    DCHECK_LT(index, constant_maximum);                                     \
    HISTOGRAM_POINTER_USE(                                                  \
        std::addressof(atomic_histograms[index]), constant_histogram_name,  \
        histogram_add_method_invocation, histogram_factory_get_invocation); \
  } while (0)

//------------------------------------------------------------------------------
// Deprecated histogram macros. Not recommended for current use.

// Legacy name for UMA_HISTOGRAM_COUNTS_1M. Suggest using explicit naming
// and not using this macro going forward.
#define UMA_HISTOGRAM_COUNTS(name, sample) UMA_HISTOGRAM_CUSTOM_COUNTS(        \
    name, sample, 1, 1000000, 50)

// MB-granularity memory metric. This has a short max (1G).
#define UMA_HISTOGRAM_MEMORY_MB(name, sample)                                  \
    UMA_HISTOGRAM_CUSTOM_COUNTS(name, sample, 1, 1000, 50)

// For an enum with customized range. In general, sparse histograms should be
// used instead.
// Samples should be one of the std::vector<int> list provided via
// |custom_ranges|. See comments above CustomRanges::FactoryGet about the
// requirement of |custom_ranges|. You can use the helper function
// CustomHistogram::ArrayToCustomEnumRanges to transform a C-style array of
// valid sample values to a std::vector<int>.
#define UMA_HISTOGRAM_CUSTOM_ENUMERATION(name, sample, custom_ranges)          \
    STATIC_HISTOGRAM_POINTER_BLOCK(name, Add(sample),                          \
        base::CustomHistogram::FactoryGet(name, custom_ranges,                 \
            base::HistogramBase::kUmaTargetedHistogramFlag))

#endif  // BASE_METRICS_HISTOGRAM_MACROS_H_
