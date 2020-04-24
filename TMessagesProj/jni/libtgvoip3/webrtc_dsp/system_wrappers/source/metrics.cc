// Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the LICENSE file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS.  All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
//

#include "system_wrappers/include/metrics.h"

#include <algorithm>

#include "rtc_base/criticalsection.h"
#include "rtc_base/thread_annotations.h"

// Default implementation of histogram methods for WebRTC clients that do not
// want to provide their own implementation.

namespace webrtc {
namespace metrics {
class Histogram;

namespace {
// Limit for the maximum number of sample values that can be stored.
// TODO(asapersson): Consider using bucket count (and set up
// linearly/exponentially spaced buckets) if samples are logged more frequently.
const int kMaxSampleMapSize = 300;

class RtcHistogram {
 public:
  RtcHistogram(const std::string& name, int min, int max, int bucket_count)
      : min_(min), max_(max), info_(name, min, max, bucket_count) {
    RTC_DCHECK_GT(bucket_count, 0);
  }

  void Add(int sample) {
    sample = std::min(sample, max_);
    sample = std::max(sample, min_ - 1);  // Underflow bucket.

    rtc::CritScope cs(&crit_);
    if (info_.samples.size() == kMaxSampleMapSize &&
        info_.samples.find(sample) == info_.samples.end()) {
      return;
    }
    ++info_.samples[sample];
  }

  // Returns a copy (or nullptr if there are no samples) and clears samples.
  std::unique_ptr<SampleInfo> GetAndReset() {
    rtc::CritScope cs(&crit_);
    if (info_.samples.empty())
      return nullptr;

    SampleInfo* copy =
        new SampleInfo(info_.name, info_.min, info_.max, info_.bucket_count);

    std::swap(info_.samples, copy->samples);

    return std::unique_ptr<SampleInfo>(copy);
  }

  const std::string& name() const { return info_.name; }

  // Functions only for testing.
  void Reset() {
    rtc::CritScope cs(&crit_);
    info_.samples.clear();
  }

  int NumEvents(int sample) const {
    rtc::CritScope cs(&crit_);
    const auto it = info_.samples.find(sample);
    return (it == info_.samples.end()) ? 0 : it->second;
  }

  int NumSamples() const {
    int num_samples = 0;
    rtc::CritScope cs(&crit_);
    for (const auto& sample : info_.samples) {
      num_samples += sample.second;
    }
    return num_samples;
  }

  int MinSample() const {
    rtc::CritScope cs(&crit_);
    return (info_.samples.empty()) ? -1 : info_.samples.begin()->first;
  }

 private:
  rtc::CriticalSection crit_;
  const int min_;
  const int max_;
  SampleInfo info_ RTC_GUARDED_BY(crit_);

  RTC_DISALLOW_COPY_AND_ASSIGN(RtcHistogram);
};

class RtcHistogramMap {
 public:
  RtcHistogramMap() {}
  ~RtcHistogramMap() {}

  Histogram* GetCountsHistogram(const std::string& name,
                                int min,
                                int max,
                                int bucket_count) {
    rtc::CritScope cs(&crit_);
    const auto& it = map_.find(name);
    if (it != map_.end())
      return reinterpret_cast<Histogram*>(it->second.get());

    RtcHistogram* hist = new RtcHistogram(name, min, max, bucket_count);
    map_[name].reset(hist);
    return reinterpret_cast<Histogram*>(hist);
  }

  Histogram* GetEnumerationHistogram(const std::string& name, int boundary) {
    rtc::CritScope cs(&crit_);
    const auto& it = map_.find(name);
    if (it != map_.end())
      return reinterpret_cast<Histogram*>(it->second.get());

    RtcHistogram* hist = new RtcHistogram(name, 1, boundary, boundary + 1);
    map_[name].reset(hist);
    return reinterpret_cast<Histogram*>(hist);
  }

  void GetAndReset(
      std::map<std::string, std::unique_ptr<SampleInfo>>* histograms) {
    rtc::CritScope cs(&crit_);
    for (const auto& kv : map_) {
      std::unique_ptr<SampleInfo> info = kv.second->GetAndReset();
      if (info)
        histograms->insert(std::make_pair(kv.first, std::move(info)));
    }
  }

  // Functions only for testing.
  void Reset() {
    rtc::CritScope cs(&crit_);
    for (const auto& kv : map_)
      kv.second->Reset();
  }

  int NumEvents(const std::string& name, int sample) const {
    rtc::CritScope cs(&crit_);
    const auto& it = map_.find(name);
    return (it == map_.end()) ? 0 : it->second->NumEvents(sample);
  }

  int NumSamples(const std::string& name) const {
    rtc::CritScope cs(&crit_);
    const auto& it = map_.find(name);
    return (it == map_.end()) ? 0 : it->second->NumSamples();
  }

  int MinSample(const std::string& name) const {
    rtc::CritScope cs(&crit_);
    const auto& it = map_.find(name);
    return (it == map_.end()) ? -1 : it->second->MinSample();
  }

 private:
  rtc::CriticalSection crit_;
  std::map<std::string, std::unique_ptr<RtcHistogram>> map_
      RTC_GUARDED_BY(crit_);

  RTC_DISALLOW_COPY_AND_ASSIGN(RtcHistogramMap);
};

// RtcHistogramMap is allocated upon call to Enable().
// The histogram getter functions, which return pointer values to the histograms
// in the map, are cached in WebRTC. Therefore, this memory is not freed by the
// application (the memory will be reclaimed by the OS).
static RtcHistogramMap* volatile g_rtc_histogram_map = nullptr;

void CreateMap() {
  RtcHistogramMap* map = rtc::AtomicOps::AcquireLoadPtr(&g_rtc_histogram_map);
  if (map == nullptr) {
    RtcHistogramMap* new_map = new RtcHistogramMap();
    RtcHistogramMap* old_map = rtc::AtomicOps::CompareAndSwapPtr(
        &g_rtc_histogram_map, static_cast<RtcHistogramMap*>(nullptr), new_map);
    if (old_map != nullptr)
      delete new_map;
  }
}

// Set the first time we start using histograms. Used to make sure Enable() is
// not called thereafter.
#if RTC_DCHECK_IS_ON
static volatile int g_rtc_histogram_called = 0;
#endif

// Gets the map (or nullptr).
RtcHistogramMap* GetMap() {
#if RTC_DCHECK_IS_ON
  rtc::AtomicOps::ReleaseStore(&g_rtc_histogram_called, 1);
#endif
  return g_rtc_histogram_map;
}
}  // namespace

#ifndef WEBRTC_EXCLUDE_METRICS_DEFAULT
// Implementation of histogram methods in
// webrtc/system_wrappers/interface/metrics.h.

// Histogram with exponentially spaced buckets.
// Creates (or finds) histogram.
// The returned histogram pointer is cached (and used for adding samples in
// subsequent calls).
Histogram* HistogramFactoryGetCounts(const std::string& name,
                                     int min,
                                     int max,
                                     int bucket_count) {
  // TODO(asapersson): Alternative implementation will be needed if this
  // histogram type should be truly exponential.
  return HistogramFactoryGetCountsLinear(name, min, max, bucket_count);
}

// Histogram with linearly spaced buckets.
// Creates (or finds) histogram.
// The returned histogram pointer is cached (and used for adding samples in
// subsequent calls).
Histogram* HistogramFactoryGetCountsLinear(const std::string& name,
                                           int min,
                                           int max,
                                           int bucket_count) {
  RtcHistogramMap* map = GetMap();
  if (!map)
    return nullptr;

  return map->GetCountsHistogram(name, min, max, bucket_count);
}

// Histogram with linearly spaced buckets.
// Creates (or finds) histogram.
// The returned histogram pointer is cached (and used for adding samples in
// subsequent calls).
Histogram* HistogramFactoryGetEnumeration(const std::string& name,
                                          int boundary) {
  RtcHistogramMap* map = GetMap();
  if (!map)
    return nullptr;

  return map->GetEnumerationHistogram(name, boundary);
}

// Our default implementation reuses the non-sparse histogram.
Histogram* SparseHistogramFactoryGetEnumeration(const std::string& name,
                                                int boundary) {
  return HistogramFactoryGetEnumeration(name, boundary);
}

// Fast path. Adds |sample| to cached |histogram_pointer|.
void HistogramAdd(Histogram* histogram_pointer, int sample) {
  RtcHistogram* ptr = reinterpret_cast<RtcHistogram*>(histogram_pointer);
  ptr->Add(sample);
}

#endif  // WEBRTC_EXCLUDE_METRICS_DEFAULT

SampleInfo::SampleInfo(const std::string& name,
                       int min,
                       int max,
                       size_t bucket_count)
    : name(name), min(min), max(max), bucket_count(bucket_count) {}

SampleInfo::~SampleInfo() {}

// Implementation of global functions in metrics.h.
void Enable() {
  RTC_DCHECK(g_rtc_histogram_map == nullptr);
#if RTC_DCHECK_IS_ON
  RTC_DCHECK_EQ(0, rtc::AtomicOps::AcquireLoad(&g_rtc_histogram_called));
#endif
  CreateMap();
}

void GetAndReset(
    std::map<std::string, std::unique_ptr<SampleInfo>>* histograms) {
  histograms->clear();
  RtcHistogramMap* map = GetMap();
  if (map)
    map->GetAndReset(histograms);
}

void Reset() {
  RtcHistogramMap* map = GetMap();
  if (map)
    map->Reset();
}

int NumEvents(const std::string& name, int sample) {
  RtcHistogramMap* map = GetMap();
  return map ? map->NumEvents(name, sample) : 0;
}

int NumSamples(const std::string& name) {
  RtcHistogramMap* map = GetMap();
  return map ? map->NumSamples(name) : 0;
}

int MinSample(const std::string& name) {
  RtcHistogramMap* map = GetMap();
  return map ? map->MinSample(name) : -1;
}

}  // namespace metrics
}  // namespace webrtc
