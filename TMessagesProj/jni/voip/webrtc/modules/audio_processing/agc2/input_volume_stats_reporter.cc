/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/input_volume_stats_reporter.h"

#include <cmath>

#include "absl/strings/string_view.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_minmax.h"
#include "rtc_base/strings/string_builder.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {
namespace {

using InputVolumeType = InputVolumeStatsReporter::InputVolumeType;

constexpr int kFramesIn60Seconds = 6000;
constexpr int kMinInputVolume = 0;
constexpr int kMaxInputVolume = 255;
constexpr int kMaxUpdate = kMaxInputVolume - kMinInputVolume;

int ComputeAverageUpdate(int sum_updates, int num_updates) {
  RTC_DCHECK_GE(sum_updates, 0);
  RTC_DCHECK_LE(sum_updates, kMaxUpdate * kFramesIn60Seconds);
  RTC_DCHECK_GE(num_updates, 0);
  RTC_DCHECK_LE(num_updates, kFramesIn60Seconds);
  if (num_updates == 0) {
    return 0;
  }
  return std::round(static_cast<float>(sum_updates) /
                    static_cast<float>(num_updates));
}

constexpr absl::string_view MetricNamePrefix(
    InputVolumeType input_volume_type) {
  switch (input_volume_type) {
    case InputVolumeType::kApplied:
      return "WebRTC.Audio.Apm.AppliedInputVolume.";
    case InputVolumeType::kRecommended:
      return "WebRTC.Audio.Apm.RecommendedInputVolume.";
  }
}

metrics::Histogram* CreateVolumeHistogram(InputVolumeType input_volume_type) {
  char buffer[64];
  rtc::SimpleStringBuilder builder(buffer);
  builder << MetricNamePrefix(input_volume_type) << "OnChange";
  return metrics::HistogramFactoryGetCountsLinear(/*name=*/builder.str(),
                                                  /*min=*/1,
                                                  /*max=*/kMaxInputVolume,
                                                  /*bucket_count=*/50);
}

metrics::Histogram* CreateRateHistogram(InputVolumeType input_volume_type,
                                        absl::string_view name) {
  char buffer[64];
  rtc::SimpleStringBuilder builder(buffer);
  builder << MetricNamePrefix(input_volume_type) << name;
  return metrics::HistogramFactoryGetCountsLinear(/*name=*/builder.str(),
                                                  /*min=*/1,
                                                  /*max=*/kFramesIn60Seconds,
                                                  /*bucket_count=*/50);
}

metrics::Histogram* CreateAverageHistogram(InputVolumeType input_volume_type,
                                           absl::string_view name) {
  char buffer[64];
  rtc::SimpleStringBuilder builder(buffer);
  builder << MetricNamePrefix(input_volume_type) << name;
  return metrics::HistogramFactoryGetCountsLinear(/*name=*/builder.str(),
                                                  /*min=*/1,
                                                  /*max=*/kMaxUpdate,
                                                  /*bucket_count=*/50);
}

}  // namespace

InputVolumeStatsReporter::InputVolumeStatsReporter(InputVolumeType type)
    : histograms_(
          {.on_volume_change = CreateVolumeHistogram(type),
           .decrease_rate = CreateRateHistogram(type, "DecreaseRate"),
           .decrease_average = CreateAverageHistogram(type, "DecreaseAverage"),
           .increase_rate = CreateRateHistogram(type, "IncreaseRate"),
           .increase_average = CreateAverageHistogram(type, "IncreaseAverage"),
           .update_rate = CreateRateHistogram(type, "UpdateRate"),
           .update_average = CreateAverageHistogram(type, "UpdateAverage")}),
      cannot_log_stats_(!histograms_.AllPointersSet()) {
  if (cannot_log_stats_) {
    RTC_LOG(LS_WARNING) << "Will not log any `" << MetricNamePrefix(type)
                        << "*` histogram stats.";
  }
}

InputVolumeStatsReporter::~InputVolumeStatsReporter() = default;

void InputVolumeStatsReporter::UpdateStatistics(int input_volume) {
  if (cannot_log_stats_) {
    // Since the stats cannot be logged, do not bother updating them.
    return;
  }

  RTC_DCHECK_GE(input_volume, kMinInputVolume);
  RTC_DCHECK_LE(input_volume, kMaxInputVolume);
  if (previous_input_volume_.has_value() &&
      input_volume != previous_input_volume_.value()) {
    // Update stats when the input volume changes.
    metrics::HistogramAdd(histograms_.on_volume_change, input_volume);
    // Update stats that are periodically logged.
    const int volume_change = input_volume - previous_input_volume_.value();
    if (volume_change < 0) {
      ++volume_update_stats_.num_decreases;
      volume_update_stats_.sum_decreases -= volume_change;
    } else {
      ++volume_update_stats_.num_increases;
      volume_update_stats_.sum_increases += volume_change;
    }
  }
  // Periodically log input volume change metrics.
  if (++log_volume_update_stats_counter_ >= kFramesIn60Seconds) {
    LogVolumeUpdateStats();
    volume_update_stats_ = {};
    log_volume_update_stats_counter_ = 0;
  }
  previous_input_volume_ = input_volume;
}

void InputVolumeStatsReporter::LogVolumeUpdateStats() const {
  // Decrease rate and average.
  metrics::HistogramAdd(histograms_.decrease_rate,
                        volume_update_stats_.num_decreases);
  if (volume_update_stats_.num_decreases > 0) {
    int average_decrease = ComputeAverageUpdate(
        volume_update_stats_.sum_decreases, volume_update_stats_.num_decreases);
    metrics::HistogramAdd(histograms_.decrease_average, average_decrease);
  }
  // Increase rate and average.
  metrics::HistogramAdd(histograms_.increase_rate,
                        volume_update_stats_.num_increases);
  if (volume_update_stats_.num_increases > 0) {
    int average_increase = ComputeAverageUpdate(
        volume_update_stats_.sum_increases, volume_update_stats_.num_increases);
    metrics::HistogramAdd(histograms_.increase_average, average_increase);
  }
  // Update rate and average.
  int num_updates =
      volume_update_stats_.num_decreases + volume_update_stats_.num_increases;
  metrics::HistogramAdd(histograms_.update_rate, num_updates);
  if (num_updates > 0) {
    int average_update = ComputeAverageUpdate(
        volume_update_stats_.sum_decreases + volume_update_stats_.sum_increases,
        num_updates);
    metrics::HistogramAdd(histograms_.update_average, average_update);
  }
}

void UpdateHistogramOnRecommendedInputVolumeChangeToMatchTarget(int volume) {
  RTC_HISTOGRAM_COUNTS_LINEAR(
      "WebRTC.Audio.Apm.RecommendedInputVolume.OnChangeToMatchTarget", volume,
      1, kMaxInputVolume, 50);
}

}  // namespace webrtc
