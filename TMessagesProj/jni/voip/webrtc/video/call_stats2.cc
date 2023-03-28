/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/call_stats2.h"

#include <algorithm>
#include <memory>
#include <utility>

#include "absl/algorithm/container.h"
#include "rtc_base/checks.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {
namespace internal {
namespace {

void RemoveOldReports(int64_t now, std::list<CallStats::RttTime>* reports) {
  static constexpr const int64_t kRttTimeoutMs = 1500;
  reports->remove_if(
      [&now](CallStats::RttTime& r) { return now - r.time > kRttTimeoutMs; });
}

int64_t GetMaxRttMs(const std::list<CallStats::RttTime>& reports) {
  int64_t max_rtt_ms = -1;
  for (const CallStats::RttTime& rtt_time : reports)
    max_rtt_ms = std::max(rtt_time.rtt, max_rtt_ms);
  return max_rtt_ms;
}

int64_t GetAvgRttMs(const std::list<CallStats::RttTime>& reports) {
  RTC_DCHECK(!reports.empty());
  int64_t sum = 0;
  for (std::list<CallStats::RttTime>::const_iterator it = reports.begin();
       it != reports.end(); ++it) {
    sum += it->rtt;
  }
  return sum / reports.size();
}

int64_t GetNewAvgRttMs(const std::list<CallStats::RttTime>& reports,
                       int64_t prev_avg_rtt) {
  if (reports.empty())
    return -1;  // Reset (invalid average).

  int64_t cur_rtt_ms = GetAvgRttMs(reports);
  if (prev_avg_rtt == -1)
    return cur_rtt_ms;  // New initial average value.

  // Weight factor to apply to the average rtt.
  // We weigh the old average at 70% against the new average (30%).
  constexpr const float kWeightFactor = 0.3f;
  return prev_avg_rtt * (1.0f - kWeightFactor) + cur_rtt_ms * kWeightFactor;
}

}  // namespace

constexpr TimeDelta CallStats::kUpdateInterval;

CallStats::CallStats(Clock* clock, TaskQueueBase* task_queue)
    : clock_(clock),
      max_rtt_ms_(-1),
      avg_rtt_ms_(-1),
      sum_avg_rtt_ms_(0),
      num_avg_rtt_(0),
      time_of_first_rtt_ms_(-1),
      task_queue_(task_queue) {
  RTC_DCHECK(task_queue_);
  RTC_DCHECK_RUN_ON(task_queue_);
}

CallStats::~CallStats() {
  RTC_DCHECK_RUN_ON(task_queue_);
  RTC_DCHECK(observers_.empty());

  repeating_task_.Stop();

  UpdateHistograms();
}

void CallStats::EnsureStarted() {
  RTC_DCHECK_RUN_ON(task_queue_);
  repeating_task_ =
      RepeatingTaskHandle::DelayedStart(task_queue_, kUpdateInterval, [this]() {
        UpdateAndReport();
        return kUpdateInterval;
      });
}

void CallStats::UpdateAndReport() {
  RTC_DCHECK_RUN_ON(task_queue_);

  RemoveOldReports(clock_->CurrentTime().ms(), &reports_);
  max_rtt_ms_ = GetMaxRttMs(reports_);
  avg_rtt_ms_ = GetNewAvgRttMs(reports_, avg_rtt_ms_);

  // If there is a valid rtt, update all observers with the max rtt.
  if (max_rtt_ms_ >= 0) {
    RTC_DCHECK_GE(avg_rtt_ms_, 0);
    for (CallStatsObserver* observer : observers_)
      observer->OnRttUpdate(avg_rtt_ms_, max_rtt_ms_);
    // Sum for Histogram of average RTT reported over the entire call.
    sum_avg_rtt_ms_ += avg_rtt_ms_;
    ++num_avg_rtt_;
  }
}

void CallStats::RegisterStatsObserver(CallStatsObserver* observer) {
  RTC_DCHECK_RUN_ON(task_queue_);
  if (!absl::c_linear_search(observers_, observer))
    observers_.push_back(observer);
}

void CallStats::DeregisterStatsObserver(CallStatsObserver* observer) {
  RTC_DCHECK_RUN_ON(task_queue_);
  observers_.remove(observer);
}

int64_t CallStats::LastProcessedRtt() const {
  RTC_DCHECK_RUN_ON(task_queue_);
  // No need for locking since we're on the construction thread.
  return avg_rtt_ms_;
}

void CallStats::OnRttUpdate(int64_t rtt) {
  // This callback may for some RtpRtcp module instances (video send stream) be
  // invoked from a separate task queue, in other cases, we should already be
  // on the correct TQ.
  int64_t now_ms = clock_->TimeInMilliseconds();
  auto update = [this, rtt, now_ms]() {
    RTC_DCHECK_RUN_ON(task_queue_);
    reports_.push_back(RttTime(rtt, now_ms));
    if (time_of_first_rtt_ms_ == -1)
      time_of_first_rtt_ms_ = now_ms;
    UpdateAndReport();
  };

  if (task_queue_->IsCurrent()) {
    update();
  } else {
    task_queue_->PostTask(SafeTask(task_safety_.flag(), std::move(update)));
  }
}

void CallStats::UpdateHistograms() {
  RTC_DCHECK_RUN_ON(task_queue_);

  if (time_of_first_rtt_ms_ == -1 || num_avg_rtt_ < 1)
    return;

  int64_t elapsed_sec =
      (clock_->TimeInMilliseconds() - time_of_first_rtt_ms_) / 1000;
  if (elapsed_sec >= metrics::kMinRunTimeInSeconds) {
    int64_t avg_rtt_ms = (sum_avg_rtt_ms_ + num_avg_rtt_ / 2) / num_avg_rtt_;
    RTC_HISTOGRAM_COUNTS_10000(
        "WebRTC.Video.AverageRoundTripTimeInMilliseconds", avg_rtt_ms);
  }
}

}  // namespace internal
}  // namespace webrtc
