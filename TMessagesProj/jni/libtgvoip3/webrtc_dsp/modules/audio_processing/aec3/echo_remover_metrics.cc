/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/echo_remover_metrics.h"

#include <math.h>
#include <stddef.h>
#include <algorithm>
#include <numeric>

#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_minmax.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {

namespace {

constexpr float kOneByMetricsCollectionBlocks = 1.f / kMetricsCollectionBlocks;

}  // namespace

EchoRemoverMetrics::DbMetric::DbMetric() : DbMetric(0.f, 0.f, 0.f) {}
EchoRemoverMetrics::DbMetric::DbMetric(float sum_value,
                                       float floor_value,
                                       float ceil_value)
    : sum_value(sum_value), floor_value(floor_value), ceil_value(ceil_value) {}

void EchoRemoverMetrics::DbMetric::Update(float value) {
  sum_value += value;
  floor_value = std::min(floor_value, value);
  ceil_value = std::max(ceil_value, value);
}

void EchoRemoverMetrics::DbMetric::UpdateInstant(float value) {
  sum_value = value;
  floor_value = std::min(floor_value, value);
  ceil_value = std::max(ceil_value, value);
}

EchoRemoverMetrics::EchoRemoverMetrics() {
  ResetMetrics();
}

void EchoRemoverMetrics::ResetMetrics() {
  erl_.fill(DbMetric(0.f, 10000.f, 0.000f));
  erl_time_domain_ = DbMetric(0.f, 10000.f, 0.000f);
  erle_.fill(DbMetric(0.f, 0.f, 1000.f));
  erle_time_domain_ = DbMetric(0.f, 0.f, 1000.f);
  comfort_noise_.fill(DbMetric(0.f, 100000000.f, 0.f));
  suppressor_gain_.fill(DbMetric(0.f, 1.f, 0.f));
  active_render_count_ = 0;
  saturated_capture_ = false;
}

void EchoRemoverMetrics::Update(
    const AecState& aec_state,
    const std::array<float, kFftLengthBy2Plus1>& comfort_noise_spectrum,
    const std::array<float, kFftLengthBy2Plus1>& suppressor_gain) {
  metrics_reported_ = false;
  if (++block_counter_ <= kMetricsCollectionBlocks) {
    aec3::UpdateDbMetric(aec_state.Erl(), &erl_);
    erl_time_domain_.UpdateInstant(aec_state.ErlTimeDomain());
    aec3::UpdateDbMetric(aec_state.Erle(), &erle_);
    erle_time_domain_.UpdateInstant(aec_state.FullBandErleLog2());
    aec3::UpdateDbMetric(comfort_noise_spectrum, &comfort_noise_);
    aec3::UpdateDbMetric(suppressor_gain, &suppressor_gain_);
    active_render_count_ += (aec_state.ActiveRender() ? 1 : 0);
    saturated_capture_ = saturated_capture_ || aec_state.SaturatedCapture();
  } else {
    // Report the metrics over several frames in order to lower the impact of
    // the logarithms involved on the computational complexity.
    constexpr int kMetricsCollectionBlocksBy2 = kMetricsCollectionBlocks / 2;
    constexpr float kComfortNoiseScaling = 1.f / (kBlockSize * kBlockSize);
    switch (block_counter_) {
      case kMetricsCollectionBlocks + 1:
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.ErleBand0.Average",
            aec3::TransformDbMetricForReporting(true, 0.f, 19.f, 0.f,
                                                kOneByMetricsCollectionBlocks,
                                                erle_[0].sum_value),
            0, 19, 20);
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.ErleBand0.Max",
            aec3::TransformDbMetricForReporting(true, 0.f, 19.f, 0.f, 1.f,
                                                erle_[0].ceil_value),
            0, 19, 20);
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.ErleBand0.Min",
            aec3::TransformDbMetricForReporting(true, 0.f, 19.f, 0.f, 1.f,
                                                erle_[0].floor_value),
            0, 19, 20);
        break;
      case kMetricsCollectionBlocks + 2:
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.ErleBand1.Average",
            aec3::TransformDbMetricForReporting(true, 0.f, 19.f, 0.f,
                                                kOneByMetricsCollectionBlocks,
                                                erle_[1].sum_value),
            0, 19, 20);
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.ErleBand1.Max",
            aec3::TransformDbMetricForReporting(true, 0.f, 19.f, 0.f, 1.f,
                                                erle_[1].ceil_value),
            0, 19, 20);
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.ErleBand1.Min",
            aec3::TransformDbMetricForReporting(true, 0.f, 19.f, 0.f, 1.f,
                                                erle_[1].floor_value),
            0, 19, 20);
        break;
      case kMetricsCollectionBlocks + 3:
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.ErlBand0.Average",
            aec3::TransformDbMetricForReporting(true, 0.f, 59.f, 30.f,
                                                kOneByMetricsCollectionBlocks,
                                                erl_[0].sum_value),
            0, 59, 30);
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.ErlBand0.Max",
            aec3::TransformDbMetricForReporting(true, 0.f, 59.f, 30.f, 1.f,
                                                erl_[0].ceil_value),
            0, 59, 30);
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.ErlBand0.Min",
            aec3::TransformDbMetricForReporting(true, 0.f, 59.f, 30.f, 1.f,
                                                erl_[0].floor_value),
            0, 59, 30);
        break;
      case kMetricsCollectionBlocks + 4:
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.ErlBand1.Average",
            aec3::TransformDbMetricForReporting(true, 0.f, 59.f, 30.f,
                                                kOneByMetricsCollectionBlocks,
                                                erl_[1].sum_value),
            0, 59, 30);
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.ErlBand1.Max",
            aec3::TransformDbMetricForReporting(true, 0.f, 59.f, 30.f, 1.f,
                                                erl_[1].ceil_value),
            0, 59, 30);
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.ErlBand1.Min",
            aec3::TransformDbMetricForReporting(true, 0.f, 59.f, 30.f, 1.f,
                                                erl_[1].floor_value),
            0, 59, 30);
        break;
      case kMetricsCollectionBlocks + 5:
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.ComfortNoiseBand0.Average",
            aec3::TransformDbMetricForReporting(
                true, 0.f, 89.f, -90.3f,
                kComfortNoiseScaling * kOneByMetricsCollectionBlocks,
                comfort_noise_[0].sum_value),
            0, 89, 45);
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.ComfortNoiseBand0.Max",
            aec3::TransformDbMetricForReporting(true, 0.f, 89.f, -90.3f,
                                                kComfortNoiseScaling,
                                                comfort_noise_[0].ceil_value),
            0, 89, 45);
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.ComfortNoiseBand0.Min",
            aec3::TransformDbMetricForReporting(true, 0.f, 89.f, -90.3f,
                                                kComfortNoiseScaling,
                                                comfort_noise_[0].floor_value),
            0, 89, 45);
        break;
      case kMetricsCollectionBlocks + 6:
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.ComfortNoiseBand1.Average",
            aec3::TransformDbMetricForReporting(
                true, 0.f, 89.f, -90.3f,
                kComfortNoiseScaling * kOneByMetricsCollectionBlocks,
                comfort_noise_[1].sum_value),
            0, 89, 45);
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.ComfortNoiseBand1.Max",
            aec3::TransformDbMetricForReporting(true, 0.f, 89.f, -90.3f,
                                                kComfortNoiseScaling,
                                                comfort_noise_[1].ceil_value),
            0, 89, 45);
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.ComfortNoiseBand1.Min",
            aec3::TransformDbMetricForReporting(true, 0.f, 89.f, -90.3f,
                                                kComfortNoiseScaling,
                                                comfort_noise_[1].floor_value),
            0, 89, 45);
        break;
      case kMetricsCollectionBlocks + 7:
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.SuppressorGainBand0.Average",
            aec3::TransformDbMetricForReporting(true, 0.f, 59.f, 0.f,
                                                kOneByMetricsCollectionBlocks,
                                                suppressor_gain_[0].sum_value),
            0, 59, 30);
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.SuppressorGainBand0.Max",
            aec3::TransformDbMetricForReporting(true, 0.f, 59.f, 0.f, 1.f,
                                                suppressor_gain_[0].ceil_value),
            0, 59, 30);
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.SuppressorGainBand0.Min",
            aec3::TransformDbMetricForReporting(
                true, 0.f, 59.f, 0.f, 1.f, suppressor_gain_[0].floor_value),
            0, 59, 30);
        break;
      case kMetricsCollectionBlocks + 8:
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.SuppressorGainBand1.Average",
            aec3::TransformDbMetricForReporting(true, 0.f, 59.f, 0.f,
                                                kOneByMetricsCollectionBlocks,
                                                suppressor_gain_[1].sum_value),
            0, 59, 30);
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.SuppressorGainBand1.Max",
            aec3::TransformDbMetricForReporting(true, 0.f, 59.f, 0.f, 1.f,
                                                suppressor_gain_[1].ceil_value),
            0, 59, 30);
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.SuppressorGainBand1.Min",
            aec3::TransformDbMetricForReporting(
                true, 0.f, 59.f, 0.f, 1.f, suppressor_gain_[1].floor_value),
            0, 59, 30);
        break;
      case kMetricsCollectionBlocks + 9:
        RTC_HISTOGRAM_BOOLEAN(
            "WebRTC.Audio.EchoCanceller.UsableLinearEstimate",
            static_cast<int>(aec_state.UsableLinearEstimate() ? 1 : 0));
        RTC_HISTOGRAM_BOOLEAN(
            "WebRTC.Audio.EchoCanceller.ActiveRender",
            static_cast<int>(
                active_render_count_ > kMetricsCollectionBlocksBy2 ? 1 : 0));
        RTC_HISTOGRAM_COUNTS_LINEAR("WebRTC.Audio.EchoCanceller.FilterDelay",
                                    aec_state.FilterDelayBlocks(), 0, 30, 31);
        RTC_HISTOGRAM_BOOLEAN("WebRTC.Audio.EchoCanceller.CaptureSaturation",
                              static_cast<int>(saturated_capture_ ? 1 : 0));
        break;
      case kMetricsCollectionBlocks + 10:
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.Erl.Value",
            aec3::TransformDbMetricForReporting(true, 0.f, 59.f, 30.f, 1.f,
                                                erl_time_domain_.sum_value),
            0, 59, 30);
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.Erl.Max",
            aec3::TransformDbMetricForReporting(true, 0.f, 59.f, 30.f, 1.f,
                                                erl_time_domain_.ceil_value),
            0, 59, 30);
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.Erl.Min",
            aec3::TransformDbMetricForReporting(true, 0.f, 59.f, 30.f, 1.f,
                                                erl_time_domain_.floor_value),
            0, 59, 30);
        break;
      case kMetricsCollectionBlocks + 11:
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.Erle.Value",
            aec3::TransformDbMetricForReporting(false, 0.f, 19.f, 0.f, 1.f,
                                                erle_time_domain_.sum_value),
            0, 19, 20);
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.Erle.Max",
            aec3::TransformDbMetricForReporting(false, 0.f, 19.f, 0.f, 1.f,
                                                erle_time_domain_.ceil_value),
            0, 19, 20);
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.Audio.EchoCanceller.Erle.Min",
            aec3::TransformDbMetricForReporting(false, 0.f, 19.f, 0.f, 1.f,
                                                erle_time_domain_.floor_value),
            0, 19, 20);
        metrics_reported_ = true;
        RTC_DCHECK_EQ(kMetricsReportingIntervalBlocks, block_counter_);
        block_counter_ = 0;
        ResetMetrics();
        break;
      default:
        RTC_NOTREACHED();
        break;
    }
  }
}

namespace aec3 {

void UpdateDbMetric(const std::array<float, kFftLengthBy2Plus1>& value,
                    std::array<EchoRemoverMetrics::DbMetric, 2>* statistic) {
  RTC_DCHECK(statistic);
  // Truncation is intended in the band width computation.
  constexpr int kNumBands = 2;
  constexpr int kBandWidth = 65 / kNumBands;
  constexpr float kOneByBandWidth = 1.f / kBandWidth;
  RTC_DCHECK_EQ(kNumBands, statistic->size());
  RTC_DCHECK_EQ(65, value.size());
  for (size_t k = 0; k < statistic->size(); ++k) {
    float average_band =
        std::accumulate(value.begin() + kBandWidth * k,
                        value.begin() + kBandWidth * (k + 1), 0.f) *
        kOneByBandWidth;
    (*statistic)[k].Update(average_band);
  }
}

int TransformDbMetricForReporting(bool negate,
                                  float min_value,
                                  float max_value,
                                  float offset,
                                  float scaling,
                                  float value) {
  float new_value = 10.f * log10(value * scaling + 1e-10f) + offset;
  if (negate) {
    new_value = -new_value;
  }
  return static_cast<int>(rtc::SafeClamp(new_value, min_value, max_value));
}

}  // namespace aec3

}  // namespace webrtc
