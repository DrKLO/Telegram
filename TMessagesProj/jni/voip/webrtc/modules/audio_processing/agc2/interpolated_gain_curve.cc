/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/interpolated_gain_curve.h"

#include <algorithm>
#include <iterator>

#include "modules/audio_processing/agc2/agc2_common.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/checks.h"

namespace webrtc {

constexpr std::array<float, kInterpolatedGainCurveTotalPoints>
    InterpolatedGainCurve::approximation_params_x_;

constexpr std::array<float, kInterpolatedGainCurveTotalPoints>
    InterpolatedGainCurve::approximation_params_m_;

constexpr std::array<float, kInterpolatedGainCurveTotalPoints>
    InterpolatedGainCurve::approximation_params_q_;

InterpolatedGainCurve::InterpolatedGainCurve(ApmDataDumper* apm_data_dumper,
                                             std::string histogram_name_prefix)
    : region_logger_("WebRTC.Audio." + histogram_name_prefix +
                         ".FixedDigitalGainCurveRegion.Identity",
                     "WebRTC.Audio." + histogram_name_prefix +
                         ".FixedDigitalGainCurveRegion.Knee",
                     "WebRTC.Audio." + histogram_name_prefix +
                         ".FixedDigitalGainCurveRegion.Limiter",
                     "WebRTC.Audio." + histogram_name_prefix +
                         ".FixedDigitalGainCurveRegion.Saturation"),
      apm_data_dumper_(apm_data_dumper) {}

InterpolatedGainCurve::~InterpolatedGainCurve() {
  if (stats_.available) {
    RTC_DCHECK(apm_data_dumper_);
    apm_data_dumper_->DumpRaw("agc2_interp_gain_curve_lookups_identity",
                              stats_.look_ups_identity_region);
    apm_data_dumper_->DumpRaw("agc2_interp_gain_curve_lookups_knee",
                              stats_.look_ups_knee_region);
    apm_data_dumper_->DumpRaw("agc2_interp_gain_curve_lookups_limiter",
                              stats_.look_ups_limiter_region);
    apm_data_dumper_->DumpRaw("agc2_interp_gain_curve_lookups_saturation",
                              stats_.look_ups_saturation_region);
    region_logger_.LogRegionStats(stats_);
  }
}

InterpolatedGainCurve::RegionLogger::RegionLogger(
    std::string identity_histogram_name,
    std::string knee_histogram_name,
    std::string limiter_histogram_name,
    std::string saturation_histogram_name)
    : identity_histogram(
          metrics::HistogramFactoryGetCounts(identity_histogram_name,
                                             1,
                                             10000,
                                             50)),
      knee_histogram(metrics::HistogramFactoryGetCounts(knee_histogram_name,
                                                        1,
                                                        10000,
                                                        50)),
      limiter_histogram(
          metrics::HistogramFactoryGetCounts(limiter_histogram_name,
                                             1,
                                             10000,
                                             50)),
      saturation_histogram(
          metrics::HistogramFactoryGetCounts(saturation_histogram_name,
                                             1,
                                             10000,
                                             50)) {}

InterpolatedGainCurve::RegionLogger::~RegionLogger() = default;

void InterpolatedGainCurve::RegionLogger::LogRegionStats(
    const InterpolatedGainCurve::Stats& stats) const {
  using Region = InterpolatedGainCurve::GainCurveRegion;
  const int duration_s =
      stats.region_duration_frames / (1000 / kFrameDurationMs);

  switch (stats.region) {
    case Region::kIdentity: {
      if (identity_histogram) {
        metrics::HistogramAdd(identity_histogram, duration_s);
      }
      break;
    }
    case Region::kKnee: {
      if (knee_histogram) {
        metrics::HistogramAdd(knee_histogram, duration_s);
      }
      break;
    }
    case Region::kLimiter: {
      if (limiter_histogram) {
        metrics::HistogramAdd(limiter_histogram, duration_s);
      }
      break;
    }
    case Region::kSaturation: {
      if (saturation_histogram) {
        metrics::HistogramAdd(saturation_histogram, duration_s);
      }
      break;
    }
    default: {
      RTC_NOTREACHED();
    }
  }
}

void InterpolatedGainCurve::UpdateStats(float input_level) const {
  stats_.available = true;

  GainCurveRegion region;

  if (input_level < approximation_params_x_[0]) {
    stats_.look_ups_identity_region++;
    region = GainCurveRegion::kIdentity;
  } else if (input_level <
             approximation_params_x_[kInterpolatedGainCurveKneePoints - 1]) {
    stats_.look_ups_knee_region++;
    region = GainCurveRegion::kKnee;
  } else if (input_level < kMaxInputLevelLinear) {
    stats_.look_ups_limiter_region++;
    region = GainCurveRegion::kLimiter;
  } else {
    stats_.look_ups_saturation_region++;
    region = GainCurveRegion::kSaturation;
  }

  if (region == stats_.region) {
    ++stats_.region_duration_frames;
  } else {
    region_logger_.LogRegionStats(stats_);

    stats_.region_duration_frames = 0;
    stats_.region = region;
  }
}

// Looks up a gain to apply given a non-negative input level.
// The cost of this operation depends on the region in which |input_level|
// falls.
// For the identity and the saturation regions the cost is O(1).
// For the other regions, namely knee and limiter, the cost is
// O(2 + log2(|LightkInterpolatedGainCurveTotalPoints|), plus O(1) for the
// linear interpolation (one product and one sum).
float InterpolatedGainCurve::LookUpGainToApply(float input_level) const {
  UpdateStats(input_level);

  if (input_level <= approximation_params_x_[0]) {
    // Identity region.
    return 1.0f;
  }

  if (input_level >= kMaxInputLevelLinear) {
    // Saturating lower bound. The saturing samples exactly hit the clipping
    // level. This method achieves has the lowest harmonic distorsion, but it
    // may reduce the amplitude of the non-saturating samples too much.
    return 32768.f / input_level;
  }

  // Knee and limiter regions; find the linear piece index. Spelling
  // out the complete type was the only way to silence both the clang
  // plugin and the windows compilers.
  std::array<float, kInterpolatedGainCurveTotalPoints>::const_iterator it =
      std::lower_bound(approximation_params_x_.begin(),
                       approximation_params_x_.end(), input_level);
  const size_t index = std::distance(approximation_params_x_.begin(), it) - 1;
  RTC_DCHECK_LE(0, index);
  RTC_DCHECK_LT(index, approximation_params_m_.size());
  RTC_DCHECK_LE(approximation_params_x_[index], input_level);
  if (index < approximation_params_m_.size() - 1) {
    RTC_DCHECK_LE(input_level, approximation_params_x_[index + 1]);
  }

  // Piece-wise linear interploation.
  const float gain = approximation_params_m_[index] * input_level +
                     approximation_params_q_[index];
  RTC_DCHECK_LE(0.f, gain);
  return gain;
}

}  // namespace webrtc
