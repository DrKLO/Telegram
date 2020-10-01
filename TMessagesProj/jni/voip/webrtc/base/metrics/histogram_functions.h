// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_METRICS_HISTOGRAM_FUNCTIONS_H_
#define BASE_METRICS_HISTOGRAM_FUNCTIONS_H_

#include <type_traits>

#include "base/metrics/histogram.h"
#include "base/metrics/histogram_base.h"
#include "base/time/time.h"

// Functions for recording metrics.
//
// For best practices on deciding when to emit to a histogram and what form
// the histogram should take, see
// https://chromium.googlesource.com/chromium/src.git/+/HEAD/tools/metrics/histograms/README.md

// Functions for recording UMA histograms. These can be used for cases
// when the histogram name is generated at runtime. The functionality is
// equivalent to macros defined in histogram_macros.h but allowing non-constant
// histogram names. These functions are slower compared to their macro
// equivalent because the histogram objects are not cached between calls.
// So, these shouldn't be used in performance critical code.
//
// Every function is duplicated to take both std::string and char* for the
// name. This avoids ctor/dtor instantiation for constant strigs to std::string
// which makes the call be larger than caching macros (which do accept char*)
// in those cases.
namespace base {

// For histograms with linear buckets.
// Used for capturing integer data with a linear bucketing scheme. This can be
// used when you want the exact value of some small numeric count, with a max of
// 100 or less. If you need to capture a range of greater than 100, we recommend
// the use of the COUNT histograms below.
// Sample usage:
//   base::UmaHistogramExactLinear("Histogram.Linear", some_value, 10);
BASE_EXPORT void UmaHistogramExactLinear(const std::string& name,
                                         int sample,
                                         int value_max);
BASE_EXPORT void UmaHistogramExactLinear(const char* name,
                                         int sample,
                                         int value_max);

// For adding a sample to an enumerated histogram.
// Sample usage:
//   // These values are persisted to logs. Entries should not be renumbered and
//   // numeric values should never be reused.
//   enum class NewTabPageAction {
//     kUseOmnibox = 0,
//     kClickTitle = 1,
//     // kUseSearchbox = 2,  // no longer used, combined into omnibox
//     kOpenBookmark = 3,
//     kMaxValue = kOpenBookmark,
//   };
//   base::UmaHistogramEnumeration("My.Enumeration",
//                                 NewTabPageAction::kUseSearchbox);
template <typename T>
void UmaHistogramEnumeration(const std::string& name, T sample) {
  static_assert(std::is_enum<T>::value, "T is not an enum.");
  // This also ensures that an enumeration that doesn't define kMaxValue fails
  // with a semi-useful error ("no member named 'kMaxValue' in ...").
  static_assert(static_cast<uintmax_t>(T::kMaxValue) <=
                    static_cast<uintmax_t>(INT_MAX) - 1,
                "Enumeration's kMaxValue is out of range of INT_MAX!");
  DCHECK_LE(static_cast<uintmax_t>(sample),
            static_cast<uintmax_t>(T::kMaxValue));
  return UmaHistogramExactLinear(name, static_cast<int>(sample),
                                 static_cast<int>(T::kMaxValue) + 1);
}

template <typename T>
void UmaHistogramEnumeration(const char* name, T sample) {
  static_assert(std::is_enum<T>::value, "T is not an enum.");
  // This also ensures that an enumeration that doesn't define kMaxValue fails
  // with a semi-useful error ("no member named 'kMaxValue' in ...").
  static_assert(static_cast<uintmax_t>(T::kMaxValue) <=
                    static_cast<uintmax_t>(INT_MAX) - 1,
                "Enumeration's kMaxValue is out of range of INT_MAX!");
  DCHECK_LE(static_cast<uintmax_t>(sample),
            static_cast<uintmax_t>(T::kMaxValue));
  return UmaHistogramExactLinear(name, static_cast<int>(sample),
                                 static_cast<int>(T::kMaxValue) + 1);
}

// Some legacy histograms may manually specify a max value, with a kCount,
// COUNT, kMaxValue, or MAX_VALUE sentinel like so:
//   // These values are persisted to logs. Entries should not be renumbered and
//   // numeric values should never be reused.
//   enum class NewTabPageAction {
//     kUseOmnibox = 0,
//     kClickTitle = 1,
//     // kUseSearchbox = 2,  // no longer used, combined into omnibox
//     kOpenBookmark = 3,
//     kMaxValue,
//   };
//   base::UmaHistogramEnumeration("My.Enumeration",
//                                 NewTabPageAction::kUseSearchbox,
//                                 kMaxValue);
// Note: The value in |sample| must be strictly less than |kMaxValue|. This is
// otherwise functionally equivalent to the above.
template <typename T>
void UmaHistogramEnumeration(const std::string& name, T sample, T enum_size) {
  static_assert(std::is_enum<T>::value, "T is not an enum.");
  DCHECK_LE(static_cast<uintmax_t>(enum_size), static_cast<uintmax_t>(INT_MAX));
  DCHECK_LT(static_cast<uintmax_t>(sample), static_cast<uintmax_t>(enum_size));
  return UmaHistogramExactLinear(name, static_cast<int>(sample),
                                 static_cast<int>(enum_size));
}

template <typename T>
void UmaHistogramEnumeration(const char* name, T sample, T enum_size) {
  static_assert(std::is_enum<T>::value, "T is not an enum.");
  DCHECK_LE(static_cast<uintmax_t>(enum_size), static_cast<uintmax_t>(INT_MAX));
  DCHECK_LT(static_cast<uintmax_t>(sample), static_cast<uintmax_t>(enum_size));
  return UmaHistogramExactLinear(name, static_cast<int>(sample),
                                 static_cast<int>(enum_size));
}

// For adding boolean sample to histogram.
// Sample usage:
//   base::UmaHistogramBoolean("My.Boolean", true)
BASE_EXPORT void UmaHistogramBoolean(const std::string& name, bool sample);
BASE_EXPORT void UmaHistogramBoolean(const char* name, bool sample);

// For adding histogram with percent.
// Percents are integer between 1 and 100.
// Sample usage:
//   base::UmaHistogramPercentage("My.Percent", 69)
BASE_EXPORT void UmaHistogramPercentage(const std::string& name, int percent);
BASE_EXPORT void UmaHistogramPercentage(const char* name, int percent);

// For adding counts histogram.
// Sample usage:
//   base::UmaHistogramCustomCounts("My.Counts", some_value, 1, 600, 30)
BASE_EXPORT void UmaHistogramCustomCounts(const std::string& name,
                                          int sample,
                                          int min,
                                          int max,
                                          int buckets);
BASE_EXPORT void UmaHistogramCustomCounts(const char* name,
                                          int sample,
                                          int min,
                                          int max,
                                          int buckets);

// Counts specialization for maximum counts 100, 1000, 10k, 100k, 1M and 10M.
BASE_EXPORT void UmaHistogramCounts100(const std::string& name, int sample);
BASE_EXPORT void UmaHistogramCounts100(const char* name, int sample);
BASE_EXPORT void UmaHistogramCounts1000(const std::string& name, int sample);
BASE_EXPORT void UmaHistogramCounts1000(const char* name, int sample);
BASE_EXPORT void UmaHistogramCounts10000(const std::string& name, int sample);
BASE_EXPORT void UmaHistogramCounts10000(const char* name, int sample);
BASE_EXPORT void UmaHistogramCounts100000(const std::string& name, int sample);
BASE_EXPORT void UmaHistogramCounts100000(const char* name, int sample);
BASE_EXPORT void UmaHistogramCounts1M(const std::string& name, int sample);
BASE_EXPORT void UmaHistogramCounts1M(const char* name, int sample);
BASE_EXPORT void UmaHistogramCounts10M(const std::string& name, int sample);
BASE_EXPORT void UmaHistogramCounts10M(const char* name, int sample);

// For histograms storing times. It uses milliseconds granularity.
BASE_EXPORT void UmaHistogramCustomTimes(const std::string& name,
                                         TimeDelta sample,
                                         TimeDelta min,
                                         TimeDelta max,
                                         int buckets);
BASE_EXPORT void UmaHistogramCustomTimes(const char* name,
                                         TimeDelta sample,
                                         TimeDelta min,
                                         TimeDelta max,
                                         int buckets);
// For short timings from 1 ms up to 10 seconds (50 buckets).
BASE_EXPORT void UmaHistogramTimes(const std::string& name, TimeDelta sample);
BASE_EXPORT void UmaHistogramTimes(const char* name, TimeDelta sample);
// For medium timings up to 3 minutes (50 buckets).
BASE_EXPORT void UmaHistogramMediumTimes(const std::string& name,
                                         TimeDelta sample);
BASE_EXPORT void UmaHistogramMediumTimes(const char* name, TimeDelta sample);
// For time intervals up to 1 hr (50 buckets).
BASE_EXPORT void UmaHistogramLongTimes(const std::string& name,
                                       TimeDelta sample);
BASE_EXPORT void UmaHistogramLongTimes(const char* name, TimeDelta sample);

// For time intervals up to 1 hr (100 buckets).
BASE_EXPORT void UmaHistogramLongTimes100(const std::string& name,
                                          TimeDelta sample);
BASE_EXPORT void UmaHistogramLongTimes100(const char* name, TimeDelta sample);

// For histograms storing times with microseconds granularity.
BASE_EXPORT void UmaHistogramCustomMicrosecondsTimes(const std::string& name,
                                                     TimeDelta sample,
                                                     TimeDelta min,
                                                     TimeDelta max,
                                                     int buckets);
BASE_EXPORT void UmaHistogramCustomMicrosecondsTimes(const char* name,
                                                     TimeDelta sample,
                                                     TimeDelta min,
                                                     TimeDelta max,
                                                     int buckets);

// For microseconds timings from 1 microsecond up to 10 seconds (50 buckets).
BASE_EXPORT void UmaHistogramMicrosecondsTimes(const std::string& name,
                                               TimeDelta sample);
BASE_EXPORT void UmaHistogramMicrosecondsTimes(const char* name,
                                               TimeDelta sample);

// For microseconds timings from 1 microsecond up to 10 ms (50 buckets).
// TODO(crbug.com/983261) Remove this method after moving to
// UmaHistogramMicrosecondsTimes.
BASE_EXPORT void UmaHistogramMicrosecondsTimesUnderTenMilliseconds(
    const std::string& name,
    TimeDelta sample);
BASE_EXPORT void UmaHistogramMicrosecondsTimesUnderTenMilliseconds(
    const char* name,
    TimeDelta sample);

// For recording memory related histograms.
// Used to measure common KB-granularity memory stats. Range is up to 500M.
BASE_EXPORT void UmaHistogramMemoryKB(const std::string& name, int sample);
BASE_EXPORT void UmaHistogramMemoryKB(const char* name, int sample);
// Used to measure common MB-granularity memory stats. Range is up to ~1G.
BASE_EXPORT void UmaHistogramMemoryMB(const std::string& name, int sample);
BASE_EXPORT void UmaHistogramMemoryMB(const char* name, int sample);
// Used to measure common MB-granularity memory stats. Range is up to ~64G.
BASE_EXPORT void UmaHistogramMemoryLargeMB(const std::string& name, int sample);
BASE_EXPORT void UmaHistogramMemoryLargeMB(const char* name, int sample);

// For recording sparse histograms.
// The |sample| can be a negative or non-negative number.
//
// Sparse histograms are well suited for recording counts of exact sample values
// that are sparsely distributed over a relatively large range, in cases where
// ultra-fast performance is not critical. For instance, Sqlite.Version.* are
// sparse because for any given database, there's going to be exactly one
// version logged.
//
// Performance:
// ------------
// Sparse histograms are typically more memory-efficient but less time-efficient
// than other histograms. Essentially, they sparse histograms use a map rather
// than a vector for their backing storage; they also require lock acquisition
// to increment a sample, whereas other histogram do not. Hence, each increment
// operation is a bit slower than for other histograms. But, if the data is
// sparse, then they use less memory client-side, because they allocate buckets
// on demand rather than preallocating.
//
// Data size:
// ----------
// Note that server-side, we still need to load all buckets, across all users,
// at once. Thus, please avoid exploding such histograms, i.e. uploading many
// many distinct values to the server (across all users). Concretely, keep the
// number of distinct values <= 100 ideally, definitely <= 1000. If you have no
// guarantees on the range of your data, use clamping, e.g.:
//   UmaHistogramSparse("MyHistogram", ClampToRange(value, 0, 200));
BASE_EXPORT void UmaHistogramSparse(const std::string& name, int sample);
BASE_EXPORT void UmaHistogramSparse(const char* name, int sample);

}  // namespace base

#endif  // BASE_METRICS_HISTOGRAM_FUNCTIONS_H_
