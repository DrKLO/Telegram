/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/statistics_calculator.h"

#include <string.h>  // memset

#include <algorithm>

#include "absl/strings/string_view.h"
#include "modules/audio_coding/neteq/delay_manager.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {

namespace {
size_t AddIntToSizeTWithLowerCap(int a, size_t b) {
  const size_t ret = b + a;
  // If a + b is negative, resulting in a negative wrap, cap it to zero instead.
  static_assert(sizeof(size_t) >= sizeof(int),
                "int must not be wider than size_t for this to work");
  return (a < 0 && ret > b) ? 0 : ret;
}

constexpr int kInterruptionLenMs = 150;
}  // namespace

// Allocating the static const so that it can be passed by reference to
// RTC_DCHECK.
const size_t StatisticsCalculator::kLenWaitingTimes;

StatisticsCalculator::PeriodicUmaLogger::PeriodicUmaLogger(
    absl::string_view uma_name,
    int report_interval_ms,
    int max_value)
    : uma_name_(uma_name),
      report_interval_ms_(report_interval_ms),
      max_value_(max_value),
      timer_(0) {}

StatisticsCalculator::PeriodicUmaLogger::~PeriodicUmaLogger() = default;

void StatisticsCalculator::PeriodicUmaLogger::AdvanceClock(int step_ms) {
  timer_ += step_ms;
  if (timer_ < report_interval_ms_) {
    return;
  }
  LogToUma(Metric());
  Reset();
  timer_ -= report_interval_ms_;
  RTC_DCHECK_GE(timer_, 0);
}

void StatisticsCalculator::PeriodicUmaLogger::LogToUma(int value) const {
  RTC_HISTOGRAM_COUNTS_SPARSE(uma_name_, value, 1, max_value_, 50);
}

StatisticsCalculator::PeriodicUmaCount::PeriodicUmaCount(
    absl::string_view uma_name,
    int report_interval_ms,
    int max_value)
    : PeriodicUmaLogger(uma_name, report_interval_ms, max_value) {}

StatisticsCalculator::PeriodicUmaCount::~PeriodicUmaCount() {
  // Log the count for the current (incomplete) interval.
  LogToUma(Metric());
}

void StatisticsCalculator::PeriodicUmaCount::RegisterSample() {
  ++counter_;
}

int StatisticsCalculator::PeriodicUmaCount::Metric() const {
  return counter_;
}

void StatisticsCalculator::PeriodicUmaCount::Reset() {
  counter_ = 0;
}

StatisticsCalculator::PeriodicUmaAverage::PeriodicUmaAverage(
    absl::string_view uma_name,
    int report_interval_ms,
    int max_value)
    : PeriodicUmaLogger(uma_name, report_interval_ms, max_value) {}

StatisticsCalculator::PeriodicUmaAverage::~PeriodicUmaAverage() {
  // Log the average for the current (incomplete) interval.
  LogToUma(Metric());
}

void StatisticsCalculator::PeriodicUmaAverage::RegisterSample(int value) {
  sum_ += value;
  ++counter_;
}

int StatisticsCalculator::PeriodicUmaAverage::Metric() const {
  return counter_ == 0 ? 0 : static_cast<int>(sum_ / counter_);
}

void StatisticsCalculator::PeriodicUmaAverage::Reset() {
  sum_ = 0.0;
  counter_ = 0;
}

StatisticsCalculator::StatisticsCalculator()
    : preemptive_samples_(0),
      accelerate_samples_(0),
      expanded_speech_samples_(0),
      expanded_noise_samples_(0),
      timestamps_since_last_report_(0),
      secondary_decoded_samples_(0),
      discarded_secondary_packets_(0),
      delayed_packet_outage_counter_(
          "WebRTC.Audio.DelayedPacketOutageEventsPerMinute",
          60000,  // 60 seconds report interval.
          100),
      excess_buffer_delay_("WebRTC.Audio.AverageExcessBufferDelayMs",
                           60000,  // 60 seconds report interval.
                           1000),
      buffer_full_counter_("WebRTC.Audio.JitterBufferFullPerMinute",
                           60000,  // 60 seconds report interval.
                           100) {}

StatisticsCalculator::~StatisticsCalculator() = default;

void StatisticsCalculator::Reset() {
  preemptive_samples_ = 0;
  accelerate_samples_ = 0;
  expanded_speech_samples_ = 0;
  expanded_noise_samples_ = 0;
  secondary_decoded_samples_ = 0;
  discarded_secondary_packets_ = 0;
  waiting_times_.clear();
}

void StatisticsCalculator::ResetMcu() {
  timestamps_since_last_report_ = 0;
}

void StatisticsCalculator::ExpandedVoiceSamples(size_t num_samples,
                                                bool is_new_concealment_event) {
  expanded_speech_samples_ += num_samples;
  ConcealedSamplesCorrection(rtc::dchecked_cast<int>(num_samples), true);
  lifetime_stats_.concealment_events += is_new_concealment_event;
}

void StatisticsCalculator::ExpandedNoiseSamples(size_t num_samples,
                                                bool is_new_concealment_event) {
  expanded_noise_samples_ += num_samples;
  ConcealedSamplesCorrection(rtc::dchecked_cast<int>(num_samples), false);
  lifetime_stats_.concealment_events += is_new_concealment_event;
}

void StatisticsCalculator::ExpandedVoiceSamplesCorrection(int num_samples) {
  expanded_speech_samples_ =
      AddIntToSizeTWithLowerCap(num_samples, expanded_speech_samples_);
  ConcealedSamplesCorrection(num_samples, true);
}

void StatisticsCalculator::ExpandedNoiseSamplesCorrection(int num_samples) {
  expanded_noise_samples_ =
      AddIntToSizeTWithLowerCap(num_samples, expanded_noise_samples_);
  ConcealedSamplesCorrection(num_samples, false);
}

void StatisticsCalculator::DecodedOutputPlayed() {
  decoded_output_played_ = true;
}

void StatisticsCalculator::EndExpandEvent(int fs_hz) {
  RTC_DCHECK_GE(lifetime_stats_.concealed_samples,
                concealed_samples_at_event_end_);
  const int event_duration_ms =
      1000 *
      (lifetime_stats_.concealed_samples - concealed_samples_at_event_end_) /
      fs_hz;
  if (event_duration_ms >= kInterruptionLenMs && decoded_output_played_) {
    lifetime_stats_.interruption_count++;
    lifetime_stats_.total_interruption_duration_ms += event_duration_ms;
    RTC_HISTOGRAM_COUNTS("WebRTC.Audio.AudioInterruptionMs", event_duration_ms,
                         /*min=*/150, /*max=*/5000, /*bucket_count=*/50);
  }
  concealed_samples_at_event_end_ = lifetime_stats_.concealed_samples;
}

void StatisticsCalculator::ConcealedSamplesCorrection(int num_samples,
                                                      bool is_voice) {
  if (num_samples < 0) {
    // Store negative correction to subtract from future positive additions.
    // See also the function comment in the header file.
    concealed_samples_correction_ -= num_samples;
    if (!is_voice) {
      silent_concealed_samples_correction_ -= num_samples;
    }
    return;
  }

  const size_t canceled_out =
      std::min(static_cast<size_t>(num_samples), concealed_samples_correction_);
  concealed_samples_correction_ -= canceled_out;
  lifetime_stats_.concealed_samples += num_samples - canceled_out;

  if (!is_voice) {
    const size_t silent_canceled_out = std::min(
        static_cast<size_t>(num_samples), silent_concealed_samples_correction_);
    silent_concealed_samples_correction_ -= silent_canceled_out;
    lifetime_stats_.silent_concealed_samples +=
        num_samples - silent_canceled_out;
  }
}

void StatisticsCalculator::PreemptiveExpandedSamples(size_t num_samples) {
  preemptive_samples_ += num_samples;
  operations_and_state_.preemptive_samples += num_samples;
  lifetime_stats_.inserted_samples_for_deceleration += num_samples;
}

void StatisticsCalculator::AcceleratedSamples(size_t num_samples) {
  accelerate_samples_ += num_samples;
  operations_and_state_.accelerate_samples += num_samples;
  lifetime_stats_.removed_samples_for_acceleration += num_samples;
}

void StatisticsCalculator::GeneratedNoiseSamples(size_t num_samples) {
  lifetime_stats_.generated_noise_samples += num_samples;
}

void StatisticsCalculator::PacketsDiscarded(size_t num_packets) {
  lifetime_stats_.packets_discarded += num_packets;
}

void StatisticsCalculator::SecondaryPacketsDiscarded(size_t num_packets) {
  discarded_secondary_packets_ += num_packets;
  lifetime_stats_.fec_packets_discarded += num_packets;
}

void StatisticsCalculator::SecondaryPacketsReceived(size_t num_packets) {
  lifetime_stats_.fec_packets_received += num_packets;
}

void StatisticsCalculator::IncreaseCounter(size_t num_samples, int fs_hz) {
  const int time_step_ms =
      rtc::CheckedDivExact(static_cast<int>(1000 * num_samples), fs_hz);
  delayed_packet_outage_counter_.AdvanceClock(time_step_ms);
  excess_buffer_delay_.AdvanceClock(time_step_ms);
  buffer_full_counter_.AdvanceClock(time_step_ms);
  timestamps_since_last_report_ += static_cast<uint32_t>(num_samples);
  if (timestamps_since_last_report_ >
      static_cast<uint32_t>(fs_hz * kMaxReportPeriod)) {
    timestamps_since_last_report_ = 0;
  }
  lifetime_stats_.total_samples_received += num_samples;
}

void StatisticsCalculator::JitterBufferDelay(
    size_t num_samples,
    uint64_t waiting_time_ms,
    uint64_t target_delay_ms,
    uint64_t unlimited_target_delay_ms) {
  lifetime_stats_.jitter_buffer_delay_ms += waiting_time_ms * num_samples;
  lifetime_stats_.jitter_buffer_target_delay_ms +=
      target_delay_ms * num_samples;
  lifetime_stats_.jitter_buffer_minimum_delay_ms +=
      unlimited_target_delay_ms * num_samples;
  lifetime_stats_.jitter_buffer_emitted_count += num_samples;
}

void StatisticsCalculator::SecondaryDecodedSamples(int num_samples) {
  secondary_decoded_samples_ += num_samples;
}

void StatisticsCalculator::FlushedPacketBuffer() {
  operations_and_state_.packet_buffer_flushes++;
  buffer_full_counter_.RegisterSample();
}

void StatisticsCalculator::ReceivedPacket() {
  ++lifetime_stats_.jitter_buffer_packets_received;
}

void StatisticsCalculator::RelativePacketArrivalDelay(size_t delay_ms) {
  lifetime_stats_.relative_packet_arrival_delay_ms += delay_ms;
}

void StatisticsCalculator::LogDelayedPacketOutageEvent(int num_samples,
                                                       int fs_hz) {
  int outage_duration_ms = num_samples / (fs_hz / 1000);
  RTC_HISTOGRAM_COUNTS("WebRTC.Audio.DelayedPacketOutageEventMs",
                       outage_duration_ms, 1 /* min */, 2000 /* max */,
                       100 /* bucket count */);
  delayed_packet_outage_counter_.RegisterSample();
  lifetime_stats_.delayed_packet_outage_samples += num_samples;
}

void StatisticsCalculator::StoreWaitingTime(int waiting_time_ms) {
  excess_buffer_delay_.RegisterSample(waiting_time_ms);
  RTC_DCHECK_LE(waiting_times_.size(), kLenWaitingTimes);
  if (waiting_times_.size() == kLenWaitingTimes) {
    // Erase first value.
    waiting_times_.pop_front();
  }
  waiting_times_.push_back(waiting_time_ms);
  operations_and_state_.last_waiting_time_ms = waiting_time_ms;
}

void StatisticsCalculator::GetNetworkStatistics(size_t samples_per_packet,
                                                NetEqNetworkStatistics* stats) {
  RTC_DCHECK(stats);

  stats->accelerate_rate =
      CalculateQ14Ratio(accelerate_samples_, timestamps_since_last_report_);

  stats->preemptive_rate =
      CalculateQ14Ratio(preemptive_samples_, timestamps_since_last_report_);

  stats->expand_rate =
      CalculateQ14Ratio(expanded_speech_samples_ + expanded_noise_samples_,
                        timestamps_since_last_report_);

  stats->speech_expand_rate = CalculateQ14Ratio(expanded_speech_samples_,
                                                timestamps_since_last_report_);

  stats->secondary_decoded_rate = CalculateQ14Ratio(
      secondary_decoded_samples_, timestamps_since_last_report_);

  const size_t discarded_secondary_samples =
      discarded_secondary_packets_ * samples_per_packet;
  stats->secondary_discarded_rate =
      CalculateQ14Ratio(discarded_secondary_samples,
                        static_cast<uint32_t>(discarded_secondary_samples +
                                              secondary_decoded_samples_));

  if (waiting_times_.size() == 0) {
    stats->mean_waiting_time_ms = -1;
    stats->median_waiting_time_ms = -1;
    stats->min_waiting_time_ms = -1;
    stats->max_waiting_time_ms = -1;
  } else {
    std::sort(waiting_times_.begin(), waiting_times_.end());
    // Find mid-point elements. If the size is odd, the two values
    // `middle_left` and `middle_right` will both be the one middle element; if
    // the size is even, they will be the the two neighboring elements at the
    // middle of the list.
    const int middle_left = waiting_times_[(waiting_times_.size() - 1) / 2];
    const int middle_right = waiting_times_[waiting_times_.size() / 2];
    // Calculate the average of the two. (Works also for odd sizes.)
    stats->median_waiting_time_ms = (middle_left + middle_right) / 2;
    stats->min_waiting_time_ms = waiting_times_.front();
    stats->max_waiting_time_ms = waiting_times_.back();
    double sum = 0;
    for (auto time : waiting_times_) {
      sum += time;
    }
    stats->mean_waiting_time_ms = static_cast<int>(sum / waiting_times_.size());
  }

  // Reset counters.
  ResetMcu();
  Reset();
}

NetEqLifetimeStatistics StatisticsCalculator::GetLifetimeStatistics() const {
  return lifetime_stats_;
}

NetEqOperationsAndState StatisticsCalculator::GetOperationsAndState() const {
  return operations_and_state_;
}

uint16_t StatisticsCalculator::CalculateQ14Ratio(size_t numerator,
                                                 uint32_t denominator) {
  if (numerator == 0) {
    return 0;
  } else if (numerator < denominator) {
    // Ratio must be smaller than 1 in Q14.
    RTC_DCHECK_LT((numerator << 14) / denominator, (1 << 14));
    return static_cast<uint16_t>((numerator << 14) / denominator);
  } else {
    // Will not produce a ratio larger than 1, since this is probably an error.
    return 1 << 14;
  }
}

}  // namespace webrtc
