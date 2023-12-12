/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/timing/jitter_estimator.h"

#include <math.h>
#include <string.h>

#include <algorithm>
#include <cstdint>

#include "absl/types/optional.h"
#include "api/field_trials_view.h"
#include "api/units/data_size.h"
#include "api/units/frequency.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "modules/video_coding/timing/rtt_filter.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {
namespace {

// Number of frames to wait for before post processing estimate. Also used in
// the frame rate estimator ramp-up.
constexpr size_t kFrameProcessingStartupCount = 30;

// Number of frames to wait for before enabling the frame size filters.
constexpr size_t kFramesUntilSizeFiltering = 5;

// Initial value for frame size filters.
constexpr double kInitialAvgAndMaxFrameSizeBytes = 500.0;

// Time constant for average frame size filter.
constexpr double kPhi = 0.97;
// Time constant for max frame size filter.
constexpr double kPsi = 0.9999;
// Default constants for percentile frame size filter.
constexpr double kDefaultMaxFrameSizePercentile = 0.95;
constexpr int kDefaultFrameSizeWindow = 30 * 10;

// Outlier rejection constants.
constexpr double kNumStdDevDelayClamp = 3.5;
constexpr double kNumStdDevDelayOutlier = 15.0;
constexpr double kNumStdDevSizeOutlier = 3.0;
constexpr double kCongestionRejectionFactor = -0.25;

// Rampup constant for deviation noise filters.
constexpr size_t kAlphaCountMax = 400;

// Noise threshold constants.
// ~Less than 1% chance (look up in normal distribution table)...
constexpr double kNoiseStdDevs = 2.33;
// ...of getting 30 ms freezes
constexpr double kNoiseStdDevOffset = 30.0;

// Jitter estimate clamping limits.
constexpr TimeDelta kMinJitterEstimate = TimeDelta::Millis(1);
constexpr TimeDelta kMaxJitterEstimate = TimeDelta::Seconds(10);

// A constant describing the delay from the jitter buffer to the delay on the
// receiving side which is not accounted for by the jitter buffer nor the
// decoding delay estimate.
constexpr TimeDelta OPERATING_SYSTEM_JITTER = TimeDelta::Millis(10);

// Time constant for reseting the NACK count.
constexpr TimeDelta kNackCountTimeout = TimeDelta::Seconds(60);

// RTT mult activation.
constexpr size_t kNackLimit = 3;

// Frame rate estimate clamping limit.
constexpr Frequency kMaxFramerateEstimate = Frequency::Hertz(200);

}  // namespace

constexpr char JitterEstimator::Config::kFieldTrialsKey[];

JitterEstimator::Config JitterEstimator::Config::ParseAndValidate(
    absl::string_view field_trial) {
  Config config;
  config.Parser()->Parse(field_trial);

  // The `MovingPercentileFilter` RTC_CHECKs on the validity of the
  // percentile and window length, so we'd better validate the field trial
  // provided values here.
  if (config.max_frame_size_percentile) {
    double original = *config.max_frame_size_percentile;
    config.max_frame_size_percentile = std::min(std::max(0.0, original), 1.0);
    if (config.max_frame_size_percentile != original) {
      RTC_LOG(LS_ERROR) << "Skipping invalid max_frame_size_percentile="
                        << original;
    }
  }
  if (config.frame_size_window && config.frame_size_window < 1) {
    RTC_LOG(LS_ERROR) << "Skipping invalid frame_size_window="
                      << *config.frame_size_window;
    config.frame_size_window = 1;
  }

  // General sanity checks.
  if (config.num_stddev_delay_clamp && config.num_stddev_delay_clamp < 0.0) {
    RTC_LOG(LS_ERROR) << "Skipping invalid num_stddev_delay_clamp="
                      << *config.num_stddev_delay_clamp;
    config.num_stddev_delay_clamp = 0.0;
  }
  if (config.num_stddev_delay_outlier &&
      config.num_stddev_delay_outlier < 0.0) {
    RTC_LOG(LS_ERROR) << "Skipping invalid num_stddev_delay_outlier="
                      << *config.num_stddev_delay_outlier;
    config.num_stddev_delay_outlier = 0.0;
  }
  if (config.num_stddev_size_outlier && config.num_stddev_size_outlier < 0.0) {
    RTC_LOG(LS_ERROR) << "Skipping invalid num_stddev_size_outlier="
                      << *config.num_stddev_size_outlier;
    config.num_stddev_size_outlier = 0.0;
  }

  return config;
}

JitterEstimator::JitterEstimator(Clock* clock,
                                 const FieldTrialsView& field_trials)
    : config_(Config::ParseAndValidate(
          field_trials.Lookup(Config::kFieldTrialsKey))),
      avg_frame_size_median_bytes_(static_cast<size_t>(
          config_.frame_size_window.value_or(kDefaultFrameSizeWindow))),
      max_frame_size_bytes_percentile_(
          config_.max_frame_size_percentile.value_or(
              kDefaultMaxFrameSizePercentile),
          static_cast<size_t>(
              config_.frame_size_window.value_or(kDefaultFrameSizeWindow))),
      fps_counter_(30),  // TODO(sprang): Use an estimator with limit based
                         // on time, rather than number of samples.
      clock_(clock) {
  Reset();
}

JitterEstimator::~JitterEstimator() = default;

// Resets the JitterEstimate.
void JitterEstimator::Reset() {
  avg_frame_size_bytes_ = kInitialAvgAndMaxFrameSizeBytes;
  max_frame_size_bytes_ = kInitialAvgAndMaxFrameSizeBytes;
  var_frame_size_bytes2_ = 100;
  avg_frame_size_median_bytes_.Reset();
  max_frame_size_bytes_percentile_.Reset();
  last_update_time_ = absl::nullopt;
  prev_estimate_ = absl::nullopt;
  prev_frame_size_ = absl::nullopt;
  avg_noise_ms_ = 0.0;
  var_noise_ms2_ = 4.0;
  alpha_count_ = 1;
  filter_jitter_estimate_ = TimeDelta::Zero();
  latest_nack_ = Timestamp::Zero();
  nack_count_ = 0;
  startup_frame_size_sum_bytes_ = 0;
  startup_frame_size_count_ = 0;
  startup_count_ = 0;
  rtt_filter_.Reset();
  fps_counter_.Reset();

  kalman_filter_ = FrameDelayVariationKalmanFilter();
}

// Updates the estimates with the new measurements.
void JitterEstimator::UpdateEstimate(TimeDelta frame_delay,
                                     DataSize frame_size) {
  if (frame_size.IsZero()) {
    return;
  }
  // Can't use DataSize since this can be negative.
  double delta_frame_bytes =
      frame_size.bytes() - prev_frame_size_.value_or(DataSize::Zero()).bytes();
  if (startup_frame_size_count_ < kFramesUntilSizeFiltering) {
    startup_frame_size_sum_bytes_ += frame_size.bytes();
    startup_frame_size_count_++;
  } else if (startup_frame_size_count_ == kFramesUntilSizeFiltering) {
    // Give the frame size filter.
    avg_frame_size_bytes_ = startup_frame_size_sum_bytes_ /
                            static_cast<double>(startup_frame_size_count_);
    startup_frame_size_count_++;
  }

  double avg_frame_size_bytes =
      kPhi * avg_frame_size_bytes_ + (1 - kPhi) * frame_size.bytes();
  double deviation_size_bytes = 2 * sqrt(var_frame_size_bytes2_);
  if (frame_size.bytes() < avg_frame_size_bytes_ + deviation_size_bytes) {
    // Only update the average frame size if this sample wasn't a key frame.
    avg_frame_size_bytes_ = avg_frame_size_bytes;
  }

  double delta_bytes = frame_size.bytes() - avg_frame_size_bytes;
  var_frame_size_bytes2_ = std::max(
      kPhi * var_frame_size_bytes2_ + (1 - kPhi) * (delta_bytes * delta_bytes),
      1.0);

  // Update non-linear IIR estimate of max frame size.
  max_frame_size_bytes_ =
      std::max<double>(kPsi * max_frame_size_bytes_, frame_size.bytes());

  // Maybe update percentile estimates of frame sizes.
  if (config_.avg_frame_size_median) {
    avg_frame_size_median_bytes_.Insert(frame_size.bytes());
  }
  if (config_.MaxFrameSizePercentileEnabled()) {
    max_frame_size_bytes_percentile_.Insert(frame_size.bytes());
  }

  if (!prev_frame_size_) {
    prev_frame_size_ = frame_size;
    return;
  }
  prev_frame_size_ = frame_size;

  // Cap frame_delay based on the current time deviation noise.
  double num_stddev_delay_clamp =
      config_.num_stddev_delay_clamp.value_or(kNumStdDevDelayClamp);
  TimeDelta max_time_deviation =
      TimeDelta::Millis(num_stddev_delay_clamp * sqrt(var_noise_ms2_) + 0.5);
  frame_delay.Clamp(-max_time_deviation, max_time_deviation);

  double delay_deviation_ms =
      frame_delay.ms() -
      kalman_filter_.GetFrameDelayVariationEstimateTotal(delta_frame_bytes);

  // Outlier rejection: these conditions depend on filtered versions of the
  // delay and frame size _means_, respectively, together with a configurable
  // number of standard deviations. If a sample is large with respect to the
  // corresponding mean and dispersion (defined by the number of
  // standard deviations and the sample standard deviation), it is deemed an
  // outlier. This "empirical rule" is further described in
  // https://en.wikipedia.org/wiki/68-95-99.7_rule. Note that neither of the
  // estimated means are true sample means, which implies that they are possibly
  // not normally distributed. Hence, this rejection method is just a heuristic.
  double num_stddev_delay_outlier =
      config_.num_stddev_delay_outlier.value_or(kNumStdDevDelayOutlier);
  // Delay outlier rejection is two-sided.
  bool abs_delay_is_not_outlier =
      fabs(delay_deviation_ms) <
      num_stddev_delay_outlier * sqrt(var_noise_ms2_);
  // The reasoning above means, in particular, that we should use the sample
  // mean-style `avg_frame_size_bytes_` estimate, as opposed to the
  // median-filtered version, even if configured to use latter for the
  // calculation in `CalculateEstimate()`.
  // Size outlier rejection is one-sided.
  double num_stddev_size_outlier =
      config_.num_stddev_size_outlier.value_or(kNumStdDevSizeOutlier);
  bool size_is_positive_outlier =
      frame_size.bytes() >
      avg_frame_size_bytes_ +
          num_stddev_size_outlier * sqrt(var_frame_size_bytes2_);

  // Only update the Kalman filter if the sample is not considered an extreme
  // outlier. Even if it is an extreme outlier from a delay point of view, if
  // the frame size also is large the deviation is probably due to an incorrect
  // line slope.
  if (abs_delay_is_not_outlier || size_is_positive_outlier) {
    // Prevent updating with frames which have been congested by a large frame,
    // and therefore arrives almost at the same time as that frame.
    // This can occur when we receive a large frame (key frame) which has been
    // delayed. The next frame is of normal size (delta frame), and thus deltaFS
    // will be << 0. This removes all frame samples which arrives after a key
    // frame.
    double congestion_rejection_factor =
        config_.congestion_rejection_factor.value_or(
            kCongestionRejectionFactor);
    double filtered_max_frame_size_bytes =
        config_.MaxFrameSizePercentileEnabled()
            ? max_frame_size_bytes_percentile_.GetFilteredValue()
            : max_frame_size_bytes_;
    bool is_not_congested =
        delta_frame_bytes >
        congestion_rejection_factor * filtered_max_frame_size_bytes;

    if (is_not_congested || config_.estimate_noise_when_congested) {
      // Update the variance of the deviation from the line given by the Kalman
      // filter.
      EstimateRandomJitter(delay_deviation_ms);
    }
    if (is_not_congested) {
      // Neither a delay outlier nor a congested frame, so we can safely update
      // the Kalman filter with the sample.
      kalman_filter_.PredictAndUpdate(frame_delay.ms(), delta_frame_bytes,
                                      filtered_max_frame_size_bytes,
                                      var_noise_ms2_);
    }
  } else {
    // Delay outliers affect the noise estimate through a value equal to the
    // outlier rejection threshold.
    double num_stddev = (delay_deviation_ms >= 0) ? num_stddev_delay_outlier
                                                  : -num_stddev_delay_outlier;
    EstimateRandomJitter(num_stddev * sqrt(var_noise_ms2_));
  }
  // Post process the total estimated jitter
  if (startup_count_ >= kFrameProcessingStartupCount) {
    PostProcessEstimate();
  } else {
    startup_count_++;
  }
}

// Updates the nack/packet ratio.
void JitterEstimator::FrameNacked() {
  if (nack_count_ < kNackLimit) {
    nack_count_++;
  }
  latest_nack_ = clock_->CurrentTime();
}

void JitterEstimator::UpdateRtt(TimeDelta rtt) {
  rtt_filter_.Update(rtt);
}

JitterEstimator::Config JitterEstimator::GetConfigForTest() const {
  return config_;
}

// Estimates the random jitter by calculating the variance of the sample
// distance from the line given by the Kalman filter.
void JitterEstimator::EstimateRandomJitter(double d_dT) {
  Timestamp now = clock_->CurrentTime();
  if (last_update_time_.has_value()) {
    fps_counter_.AddSample((now - *last_update_time_).us());
  }
  last_update_time_ = now;

  if (alpha_count_ == 0) {
    RTC_DCHECK_NOTREACHED();
    return;
  }
  double alpha =
      static_cast<double>(alpha_count_ - 1) / static_cast<double>(alpha_count_);
  alpha_count_++;
  if (alpha_count_ > kAlphaCountMax)
    alpha_count_ = kAlphaCountMax;

  // In order to avoid a low frame rate stream to react slower to changes,
  // scale the alpha weight relative a 30 fps stream.
  Frequency fps = GetFrameRate();
  if (fps > Frequency::Zero()) {
    constexpr Frequency k30Fps = Frequency::Hertz(30);
    double rate_scale = k30Fps / fps;
    // At startup, there can be a lot of noise in the fps estimate.
    // Interpolate rate_scale linearly, from 1.0 at sample #1, to 30.0 / fps
    // at sample #kFrameProcessingStartupCount.
    if (alpha_count_ < kFrameProcessingStartupCount) {
      rate_scale = (alpha_count_ * rate_scale +
                    (kFrameProcessingStartupCount - alpha_count_)) /
                   kFrameProcessingStartupCount;
    }
    alpha = pow(alpha, rate_scale);
  }

  double avg_noise_ms = alpha * avg_noise_ms_ + (1 - alpha) * d_dT;
  double var_noise_ms2 = alpha * var_noise_ms2_ + (1 - alpha) *
                                                      (d_dT - avg_noise_ms_) *
                                                      (d_dT - avg_noise_ms_);
  avg_noise_ms_ = avg_noise_ms;
  var_noise_ms2_ = var_noise_ms2;
  if (var_noise_ms2_ < 1.0) {
    // The variance should never be zero, since we might get stuck and consider
    // all samples as outliers.
    var_noise_ms2_ = 1.0;
  }
}

double JitterEstimator::NoiseThreshold() const {
  double noise_threshold_ms =
      kNoiseStdDevs * sqrt(var_noise_ms2_) - kNoiseStdDevOffset;
  if (noise_threshold_ms < 1.0) {
    noise_threshold_ms = 1.0;
  }
  return noise_threshold_ms;
}

// Calculates the current jitter estimate from the filtered estimates.
TimeDelta JitterEstimator::CalculateEstimate() {
  // Using median- and percentile-filtered versions of the frame sizes may be
  // more robust than using sample mean-style estimates.
  double filtered_avg_frame_size_bytes =
      config_.avg_frame_size_median
          ? avg_frame_size_median_bytes_.GetFilteredValue()
          : avg_frame_size_bytes_;
  double filtered_max_frame_size_bytes =
      config_.MaxFrameSizePercentileEnabled()
          ? max_frame_size_bytes_percentile_.GetFilteredValue()
          : max_frame_size_bytes_;
  double worst_case_frame_size_deviation_bytes =
      filtered_max_frame_size_bytes - filtered_avg_frame_size_bytes;
  double ret_ms = kalman_filter_.GetFrameDelayVariationEstimateSizeBased(
                      worst_case_frame_size_deviation_bytes) +
                  NoiseThreshold();
  TimeDelta ret = TimeDelta::Millis(ret_ms);

  // A very low estimate (or negative) is neglected.
  if (ret < kMinJitterEstimate) {
    ret = prev_estimate_.value_or(kMinJitterEstimate);
    // Sanity check to make sure that no other method has set `prev_estimate_`
    // to a value lower than `kMinJitterEstimate`.
    RTC_DCHECK_GE(ret, kMinJitterEstimate);
  } else if (ret > kMaxJitterEstimate) {  // Sanity
    ret = kMaxJitterEstimate;
  }
  prev_estimate_ = ret;
  return ret;
}

void JitterEstimator::PostProcessEstimate() {
  filter_jitter_estimate_ = CalculateEstimate();
}

// Returns the current filtered estimate if available,
// otherwise tries to calculate an estimate.
TimeDelta JitterEstimator::GetJitterEstimate(
    double rtt_multiplier,
    absl::optional<TimeDelta> rtt_mult_add_cap) {
  TimeDelta jitter = CalculateEstimate() + OPERATING_SYSTEM_JITTER;
  Timestamp now = clock_->CurrentTime();

  if (now - latest_nack_ > kNackCountTimeout)
    nack_count_ = 0;

  if (filter_jitter_estimate_ > jitter)
    jitter = filter_jitter_estimate_;
  if (nack_count_ >= kNackLimit) {
    if (rtt_mult_add_cap.has_value()) {
      jitter += std::min(rtt_filter_.Rtt() * rtt_multiplier,
                         rtt_mult_add_cap.value());
    } else {
      jitter += rtt_filter_.Rtt() * rtt_multiplier;
    }
  }

  static const Frequency kJitterScaleLowThreshold = Frequency::Hertz(5);
  static const Frequency kJitterScaleHighThreshold = Frequency::Hertz(10);
  Frequency fps = GetFrameRate();
  // Ignore jitter for very low fps streams.
  if (fps < kJitterScaleLowThreshold) {
    if (fps.IsZero()) {
      return std::max(TimeDelta::Zero(), jitter);
    }
    return TimeDelta::Zero();
  }

  // Semi-low frame rate; scale by factor linearly interpolated from 0.0 at
  // kJitterScaleLowThreshold to 1.0 at kJitterScaleHighThreshold.
  if (fps < kJitterScaleHighThreshold) {
    jitter = (1.0 / (kJitterScaleHighThreshold - kJitterScaleLowThreshold)) *
             (fps - kJitterScaleLowThreshold) * jitter;
  }

  return std::max(TimeDelta::Zero(), jitter);
}

Frequency JitterEstimator::GetFrameRate() const {
  TimeDelta mean_frame_period = TimeDelta::Micros(fps_counter_.ComputeMean());
  if (mean_frame_period <= TimeDelta::Zero())
    return Frequency::Zero();

  Frequency fps = 1 / mean_frame_period;
  // Sanity check.
  RTC_DCHECK_GE(fps, Frequency::Zero());
  return std::min(fps, kMaxFramerateEstimate);
}
}  // namespace webrtc
