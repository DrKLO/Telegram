/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/limiter_db_gain_curve.h"

#include <cmath>

#include "common_audio/include/audio_util.h"
#include "modules/audio_processing/agc2/agc2_common.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace {

double ComputeKneeStart(double max_input_level_db,
                        double knee_smoothness_db,
                        double compression_ratio) {
  RTC_CHECK_LT((compression_ratio - 1.0) * knee_smoothness_db /
                   (2.0 * compression_ratio),
               max_input_level_db);
  return -knee_smoothness_db / 2.0 -
         max_input_level_db / (compression_ratio - 1.0);
}

std::array<double, 3> ComputeKneeRegionPolynomial(double knee_start_dbfs,
                                                  double knee_smoothness_db,
                                                  double compression_ratio) {
  const double a = (1.0 - compression_ratio) /
                   (2.0 * knee_smoothness_db * compression_ratio);
  const double b = 1.0 - 2.0 * a * knee_start_dbfs;
  const double c = a * knee_start_dbfs * knee_start_dbfs;
  return {{a, b, c}};
}

double ComputeLimiterD1(double max_input_level_db, double compression_ratio) {
  return (std::pow(10.0, -max_input_level_db / (20.0 * compression_ratio)) *
          (1.0 - compression_ratio) / compression_ratio) /
         kMaxAbsFloatS16Value;
}

constexpr double ComputeLimiterD2(double compression_ratio) {
  return (1.0 - 2.0 * compression_ratio) / compression_ratio;
}

double ComputeLimiterI2(double max_input_level_db,
                        double compression_ratio,
                        double gain_curve_limiter_i1) {
  RTC_CHECK_NE(gain_curve_limiter_i1, 0.f);
  return std::pow(10.0, -max_input_level_db / (20.0 * compression_ratio)) /
         gain_curve_limiter_i1 /
         std::pow(kMaxAbsFloatS16Value, gain_curve_limiter_i1 - 1);
}

}  // namespace

LimiterDbGainCurve::LimiterDbGainCurve()
    : max_input_level_linear_(DbfsToFloatS16(max_input_level_db_)),
      knee_start_dbfs_(ComputeKneeStart(max_input_level_db_,
                                        knee_smoothness_db_,
                                        compression_ratio_)),
      knee_start_linear_(DbfsToFloatS16(knee_start_dbfs_)),
      limiter_start_dbfs_(knee_start_dbfs_ + knee_smoothness_db_),
      limiter_start_linear_(DbfsToFloatS16(limiter_start_dbfs_)),
      knee_region_polynomial_(ComputeKneeRegionPolynomial(knee_start_dbfs_,
                                                          knee_smoothness_db_,
                                                          compression_ratio_)),
      gain_curve_limiter_d1_(
          ComputeLimiterD1(max_input_level_db_, compression_ratio_)),
      gain_curve_limiter_d2_(ComputeLimiterD2(compression_ratio_)),
      gain_curve_limiter_i1_(1.0 / compression_ratio_),
      gain_curve_limiter_i2_(ComputeLimiterI2(max_input_level_db_,
                                              compression_ratio_,
                                              gain_curve_limiter_i1_)) {
  static_assert(knee_smoothness_db_ > 0.0f, "");
  static_assert(compression_ratio_ > 1.0f, "");
  RTC_CHECK_GE(max_input_level_db_, knee_start_dbfs_ + knee_smoothness_db_);
}

constexpr double LimiterDbGainCurve::max_input_level_db_;
constexpr double LimiterDbGainCurve::knee_smoothness_db_;
constexpr double LimiterDbGainCurve::compression_ratio_;

double LimiterDbGainCurve::GetOutputLevelDbfs(double input_level_dbfs) const {
  if (input_level_dbfs < knee_start_dbfs_) {
    return input_level_dbfs;
  } else if (input_level_dbfs < limiter_start_dbfs_) {
    return GetKneeRegionOutputLevelDbfs(input_level_dbfs);
  }
  return GetCompressorRegionOutputLevelDbfs(input_level_dbfs);
}

double LimiterDbGainCurve::GetGainLinear(double input_level_linear) const {
  if (input_level_linear < knee_start_linear_) {
    return 1.0;
  }
  return DbfsToFloatS16(
             GetOutputLevelDbfs(FloatS16ToDbfs(input_level_linear))) /
         input_level_linear;
}

// Computes the first derivative of GetGainLinear() in `x`.
double LimiterDbGainCurve::GetGainFirstDerivativeLinear(double x) const {
  // Beyond-knee region only.
  RTC_CHECK_GE(x, limiter_start_linear_ - 1e-7 * kMaxAbsFloatS16Value);
  return gain_curve_limiter_d1_ *
         std::pow(x / kMaxAbsFloatS16Value, gain_curve_limiter_d2_);
}

// Computes the integral of GetGainLinear() in the range [x0, x1].
double LimiterDbGainCurve::GetGainIntegralLinear(double x0, double x1) const {
  RTC_CHECK_LE(x0, x1);                     // Valid interval.
  RTC_CHECK_GE(x0, limiter_start_linear_);  // Beyond-knee region only.
  auto limiter_integral = [this](const double& x) {
    return gain_curve_limiter_i2_ * std::pow(x, gain_curve_limiter_i1_);
  };
  return limiter_integral(x1) - limiter_integral(x0);
}

double LimiterDbGainCurve::GetKneeRegionOutputLevelDbfs(
    double input_level_dbfs) const {
  return knee_region_polynomial_[0] * input_level_dbfs * input_level_dbfs +
         knee_region_polynomial_[1] * input_level_dbfs +
         knee_region_polynomial_[2];
}

double LimiterDbGainCurve::GetCompressorRegionOutputLevelDbfs(
    double input_level_dbfs) const {
  return (input_level_dbfs - max_input_level_db_) / compression_ratio_;
}

}  // namespace webrtc
