/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc/analog_gain_stats_reporter.h"

#include <cmath>

#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_minmax.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {
namespace {

constexpr int kFramesIn60Seconds = 6000;
constexpr int kMinGain = 0;
constexpr int kMaxGain = 255;
constexpr int kMaxUpdate = kMaxGain - kMinGain;

float ComputeAverageUpdate(int sum_updates, int num_updates) {
  RTC_DCHECK_GE(sum_updates, 0);
  RTC_DCHECK_LE(sum_updates, kMaxUpdate * kFramesIn60Seconds);
  RTC_DCHECK_GE(num_updates, 0);
  RTC_DCHECK_LE(num_updates, kFramesIn60Seconds);
  if (num_updates == 0) {
    return 0.0f;
  }
  return std::round(static_cast<float>(sum_updates) /
                    static_cast<float>(num_updates));
}
}  // namespace

AnalogGainStatsReporter::AnalogGainStatsReporter() = default;

AnalogGainStatsReporter::~AnalogGainStatsReporter() = default;

void AnalogGainStatsReporter::UpdateStatistics(int analog_mic_level) {
  RTC_DCHECK_GE(analog_mic_level, kMinGain);
  RTC_DCHECK_LE(analog_mic_level, kMaxGain);
  if (previous_analog_mic_level_.has_value() &&
      analog_mic_level != previous_analog_mic_level_.value()) {
    const int level_change =
        analog_mic_level - previous_analog_mic_level_.value();
    if (level_change < 0) {
      ++level_update_stats_.num_decreases;
      level_update_stats_.sum_decreases -= level_change;
    } else {
      ++level_update_stats_.num_increases;
      level_update_stats_.sum_increases += level_change;
    }
  }
  // Periodically log analog gain change metrics.
  if (++log_level_update_stats_counter_ >= kFramesIn60Seconds) {
    LogLevelUpdateStats();
    level_update_stats_ = {};
    log_level_update_stats_counter_ = 0;
  }
  previous_analog_mic_level_ = analog_mic_level;
}

void AnalogGainStatsReporter::LogLevelUpdateStats() const {
  const float average_decrease = ComputeAverageUpdate(
      level_update_stats_.sum_decreases, level_update_stats_.num_decreases);
  const float average_increase = ComputeAverageUpdate(
      level_update_stats_.sum_increases, level_update_stats_.num_increases);
  const int num_updates =
      level_update_stats_.num_decreases + level_update_stats_.num_increases;
  const float average_update = ComputeAverageUpdate(
      level_update_stats_.sum_decreases + level_update_stats_.sum_increases,
      num_updates);
  RTC_DLOG(LS_INFO) << "Analog gain update rate: "
                    << "num_updates=" << num_updates
                    << ", num_decreases=" << level_update_stats_.num_decreases
                    << ", num_increases=" << level_update_stats_.num_increases;
  RTC_DLOG(LS_INFO) << "Analog gain update average: "
                    << "average_update=" << average_update
                    << ", average_decrease=" << average_decrease
                    << ", average_increase=" << average_increase;
  RTC_HISTOGRAM_COUNTS_LINEAR(
      /*name=*/"WebRTC.Audio.ApmAnalogGainDecreaseRate",
      /*sample=*/level_update_stats_.num_decreases,
      /*min=*/1,
      /*max=*/kFramesIn60Seconds,
      /*bucket_count=*/50);
  if (level_update_stats_.num_decreases > 0) {
    RTC_HISTOGRAM_COUNTS_LINEAR(
        /*name=*/"WebRTC.Audio.ApmAnalogGainDecreaseAverage",
        /*sample=*/average_decrease,
        /*min=*/1,
        /*max=*/kMaxUpdate,
        /*bucket_count=*/50);
  }
  RTC_HISTOGRAM_COUNTS_LINEAR(
      /*name=*/"WebRTC.Audio.ApmAnalogGainIncreaseRate",
      /*sample=*/level_update_stats_.num_increases,
      /*min=*/1,
      /*max=*/kFramesIn60Seconds,
      /*bucket_count=*/50);
  if (level_update_stats_.num_increases > 0) {
    RTC_HISTOGRAM_COUNTS_LINEAR(
        /*name=*/"WebRTC.Audio.ApmAnalogGainIncreaseAverage",
        /*sample=*/average_increase,
        /*min=*/1,
        /*max=*/kMaxUpdate,
        /*bucket_count=*/50);
  }
  RTC_HISTOGRAM_COUNTS_LINEAR(
      /*name=*/"WebRTC.Audio.ApmAnalogGainUpdateRate",
      /*sample=*/num_updates,
      /*min=*/1,
      /*max=*/kFramesIn60Seconds,
      /*bucket_count=*/50);
  if (num_updates > 0) {
    RTC_HISTOGRAM_COUNTS_LINEAR(
        /*name=*/"WebRTC.Audio.ApmAnalogGainUpdateAverage",
        /*sample=*/average_update,
        /*min=*/1,
        /*max=*/kMaxUpdate,
        /*bucket_count=*/50);
  }
}

}  // namespace webrtc
