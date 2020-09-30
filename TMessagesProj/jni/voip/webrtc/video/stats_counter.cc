/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/stats_counter.h"

#include <algorithm>
#include <limits>
#include <map>

#include "rtc_base/checks.h"
#include "rtc_base/strings/string_builder.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {

namespace {
// Default periodic time interval for processing samples.
const int64_t kDefaultProcessIntervalMs = 2000;
const uint32_t kStreamId0 = 0;
}  // namespace

std::string AggregatedStats::ToString() const {
  return ToStringWithMultiplier(1);
}

std::string AggregatedStats::ToStringWithMultiplier(int multiplier) const {
  rtc::StringBuilder ss;
  ss << "periodic_samples:" << num_samples << ", {";
  ss << "min:" << (min * multiplier) << ", ";
  ss << "avg:" << (average * multiplier) << ", ";
  ss << "max:" << (max * multiplier) << "}";
  return ss.Release();
}

// Class holding periodically computed metrics.
class AggregatedCounter {
 public:
  AggregatedCounter() : last_sample_(0), sum_samples_(0) {}
  ~AggregatedCounter() {}

  void Add(int sample) {
    last_sample_ = sample;
    sum_samples_ += sample;
    ++stats_.num_samples;
    if (stats_.num_samples == 1) {
      stats_.min = sample;
      stats_.max = sample;
    }
    stats_.min = std::min(sample, stats_.min);
    stats_.max = std::max(sample, stats_.max);
  }

  AggregatedStats ComputeStats() {
    Compute();
    return stats_;
  }

  bool Empty() const { return stats_.num_samples == 0; }

  int last_sample() const { return last_sample_; }

 private:
  void Compute() {
    if (stats_.num_samples == 0)
      return;

    stats_.average =
        (sum_samples_ + stats_.num_samples / 2) / stats_.num_samples;
  }
  int last_sample_;
  int64_t sum_samples_;
  AggregatedStats stats_;
};

// Class holding gathered samples within a process interval.
class Samples {
 public:
  Samples() : total_count_(0) {}
  ~Samples() {}

  void Add(int sample, uint32_t stream_id) {
    samples_[stream_id].Add(sample);
    ++total_count_;
  }
  void Set(int64_t sample, uint32_t stream_id) {
    samples_[stream_id].Set(sample);
    ++total_count_;
  }
  void SetLast(int64_t sample, uint32_t stream_id) {
    samples_[stream_id].SetLast(sample);
  }
  int64_t GetLast(uint32_t stream_id) { return samples_[stream_id].GetLast(); }

  int64_t Count() const { return total_count_; }
  bool Empty() const { return total_count_ == 0; }

  int64_t Sum() const {
    int64_t sum = 0;
    for (const auto& it : samples_)
      sum += it.second.sum_;
    return sum;
  }

  int Max() const {
    int max = std::numeric_limits<int>::min();
    for (const auto& it : samples_)
      max = std::max(it.second.max_, max);
    return max;
  }

  void Reset() {
    for (auto& it : samples_)
      it.second.Reset();
    total_count_ = 0;
  }

  int64_t Diff() const {
    int64_t sum_diff = 0;
    int count = 0;
    for (const auto& it : samples_) {
      if (it.second.count_ > 0) {
        int64_t diff = it.second.sum_ - it.second.last_sum_;
        if (diff >= 0) {
          sum_diff += diff;
          ++count;
        }
      }
    }
    return (count > 0) ? sum_diff : -1;
  }

 private:
  struct Stats {
    void Add(int sample) {
      sum_ += sample;
      ++count_;
      max_ = std::max(sample, max_);
    }
    void Set(int64_t sample) {
      sum_ = sample;
      ++count_;
    }
    void SetLast(int64_t sample) { last_sum_ = sample; }
    int64_t GetLast() const { return last_sum_; }
    void Reset() {
      if (count_ > 0)
        last_sum_ = sum_;
      sum_ = 0;
      count_ = 0;
      max_ = std::numeric_limits<int>::min();
    }

    int max_ = std::numeric_limits<int>::min();
    int64_t count_ = 0;
    int64_t sum_ = 0;
    int64_t last_sum_ = 0;
  };

  int64_t total_count_;
  std::map<uint32_t, Stats> samples_;  // Gathered samples mapped by stream id.
};

// StatsCounter class.
StatsCounter::StatsCounter(Clock* clock,
                           int64_t process_intervals_ms,
                           bool include_empty_intervals,
                           StatsCounterObserver* observer)
    : include_empty_intervals_(include_empty_intervals),
      process_intervals_ms_(process_intervals_ms),
      aggregated_counter_(new AggregatedCounter()),
      samples_(new Samples()),
      clock_(clock),
      observer_(observer),
      last_process_time_ms_(-1),
      paused_(false),
      pause_time_ms_(-1),
      min_pause_time_ms_(0) {
  RTC_DCHECK_GT(process_intervals_ms_, 0);
}

StatsCounter::~StatsCounter() {}

AggregatedStats StatsCounter::GetStats() {
  return aggregated_counter_->ComputeStats();
}

AggregatedStats StatsCounter::ProcessAndGetStats() {
  if (HasSample())
    TryProcess();
  return aggregated_counter_->ComputeStats();
}

void StatsCounter::ProcessAndPauseForDuration(int64_t min_pause_time_ms) {
  ProcessAndPause();
  min_pause_time_ms_ = min_pause_time_ms;
}

void StatsCounter::ProcessAndPause() {
  if (HasSample())
    TryProcess();
  paused_ = true;
  pause_time_ms_ = clock_->TimeInMilliseconds();
}

void StatsCounter::ProcessAndStopPause() {
  if (HasSample())
    TryProcess();
  Resume();
}

bool StatsCounter::HasSample() const {
  return last_process_time_ms_ != -1;
}

bool StatsCounter::TimeToProcess(int* elapsed_intervals) {
  int64_t now = clock_->TimeInMilliseconds();
  if (last_process_time_ms_ == -1)
    last_process_time_ms_ = now;

  int64_t diff_ms = now - last_process_time_ms_;
  if (diff_ms < process_intervals_ms_)
    return false;

  // Advance number of complete |process_intervals_ms_| that have passed.
  int64_t num_intervals = diff_ms / process_intervals_ms_;
  last_process_time_ms_ += num_intervals * process_intervals_ms_;

  *elapsed_intervals = num_intervals;
  return true;
}

void StatsCounter::Add(int sample) {
  TryProcess();
  samples_->Add(sample, kStreamId0);
  ResumeIfMinTimePassed();
}

void StatsCounter::Set(int64_t sample, uint32_t stream_id) {
  if (paused_ && sample == samples_->GetLast(stream_id)) {
    // Do not add same sample while paused (will reset pause).
    return;
  }
  TryProcess();
  samples_->Set(sample, stream_id);
  ResumeIfMinTimePassed();
}

void StatsCounter::SetLast(int64_t sample, uint32_t stream_id) {
  RTC_DCHECK(!HasSample()) << "Should be set before first sample is added.";
  samples_->SetLast(sample, stream_id);
}

// Reports periodically computed metric.
void StatsCounter::ReportMetricToAggregatedCounter(
    int value,
    int num_values_to_add) const {
  for (int i = 0; i < num_values_to_add; ++i) {
    aggregated_counter_->Add(value);
    if (observer_)
      observer_->OnMetricUpdated(value);
  }
}

void StatsCounter::TryProcess() {
  int elapsed_intervals;
  if (!TimeToProcess(&elapsed_intervals))
    return;

  // Get and report periodically computed metric.
  int metric;
  if (GetMetric(&metric))
    ReportMetricToAggregatedCounter(metric, 1);

  // Report value for elapsed intervals without samples.
  if (IncludeEmptyIntervals()) {
    // If there are no samples, all elapsed intervals are empty (otherwise one
    // interval contains sample(s), discard this interval).
    int empty_intervals =
        samples_->Empty() ? elapsed_intervals : (elapsed_intervals - 1);
    ReportMetricToAggregatedCounter(GetValueForEmptyInterval(),
                                    empty_intervals);
  }

  // Reset samples for elapsed interval.
  samples_->Reset();
}

bool StatsCounter::IncludeEmptyIntervals() const {
  return include_empty_intervals_ && !paused_ && !aggregated_counter_->Empty();
}
void StatsCounter::ResumeIfMinTimePassed() {
  if (paused_ &&
      (clock_->TimeInMilliseconds() - pause_time_ms_) >= min_pause_time_ms_) {
    Resume();
  }
}

void StatsCounter::Resume() {
  paused_ = false;
  min_pause_time_ms_ = 0;
}

// StatsCounter sub-classes.
AvgCounter::AvgCounter(Clock* clock,
                       StatsCounterObserver* observer,
                       bool include_empty_intervals)
    : StatsCounter(clock,
                   kDefaultProcessIntervalMs,
                   include_empty_intervals,
                   observer) {}

void AvgCounter::Add(int sample) {
  StatsCounter::Add(sample);
}

bool AvgCounter::GetMetric(int* metric) const {
  int64_t count = samples_->Count();
  if (count == 0)
    return false;

  *metric = (samples_->Sum() + count / 2) / count;
  return true;
}

int AvgCounter::GetValueForEmptyInterval() const {
  return aggregated_counter_->last_sample();
}

MaxCounter::MaxCounter(Clock* clock,
                       StatsCounterObserver* observer,
                       int64_t process_intervals_ms)
    : StatsCounter(clock,
                   process_intervals_ms,
                   false,  // |include_empty_intervals|
                   observer) {}

void MaxCounter::Add(int sample) {
  StatsCounter::Add(sample);
}

bool MaxCounter::GetMetric(int* metric) const {
  if (samples_->Empty())
    return false;

  *metric = samples_->Max();
  return true;
}

int MaxCounter::GetValueForEmptyInterval() const {
  RTC_NOTREACHED();
  return 0;
}

PercentCounter::PercentCounter(Clock* clock, StatsCounterObserver* observer)
    : StatsCounter(clock,
                   kDefaultProcessIntervalMs,
                   false,  // |include_empty_intervals|
                   observer) {}

void PercentCounter::Add(bool sample) {
  StatsCounter::Add(sample ? 1 : 0);
}

bool PercentCounter::GetMetric(int* metric) const {
  int64_t count = samples_->Count();
  if (count == 0)
    return false;

  *metric = (samples_->Sum() * 100 + count / 2) / count;
  return true;
}

int PercentCounter::GetValueForEmptyInterval() const {
  RTC_NOTREACHED();
  return 0;
}

PermilleCounter::PermilleCounter(Clock* clock, StatsCounterObserver* observer)
    : StatsCounter(clock,
                   kDefaultProcessIntervalMs,
                   false,  // |include_empty_intervals|
                   observer) {}

void PermilleCounter::Add(bool sample) {
  StatsCounter::Add(sample ? 1 : 0);
}

bool PermilleCounter::GetMetric(int* metric) const {
  int64_t count = samples_->Count();
  if (count == 0)
    return false;

  *metric = (samples_->Sum() * 1000 + count / 2) / count;
  return true;
}

int PermilleCounter::GetValueForEmptyInterval() const {
  RTC_NOTREACHED();
  return 0;
}

RateCounter::RateCounter(Clock* clock,
                         StatsCounterObserver* observer,
                         bool include_empty_intervals)
    : StatsCounter(clock,
                   kDefaultProcessIntervalMs,
                   include_empty_intervals,
                   observer) {}

void RateCounter::Add(int sample) {
  StatsCounter::Add(sample);
}

bool RateCounter::GetMetric(int* metric) const {
  if (samples_->Empty())
    return false;

  *metric = (samples_->Sum() * 1000 + process_intervals_ms_ / 2) /
            process_intervals_ms_;
  return true;
}

int RateCounter::GetValueForEmptyInterval() const {
  return 0;
}

RateAccCounter::RateAccCounter(Clock* clock,
                               StatsCounterObserver* observer,
                               bool include_empty_intervals)
    : StatsCounter(clock,
                   kDefaultProcessIntervalMs,
                   include_empty_intervals,
                   observer) {}

void RateAccCounter::Set(int64_t sample, uint32_t stream_id) {
  StatsCounter::Set(sample, stream_id);
}

void RateAccCounter::SetLast(int64_t sample, uint32_t stream_id) {
  StatsCounter::SetLast(sample, stream_id);
}

bool RateAccCounter::GetMetric(int* metric) const {
  int64_t diff = samples_->Diff();
  if (diff < 0 || (!include_empty_intervals_ && diff == 0))
    return false;

  *metric = (diff * 1000 + process_intervals_ms_ / 2) / process_intervals_ms_;
  return true;
}

int RateAccCounter::GetValueForEmptyInterval() const {
  return 0;
}

}  // namespace webrtc
