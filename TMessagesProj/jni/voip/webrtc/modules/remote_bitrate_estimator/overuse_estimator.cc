/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/remote_bitrate_estimator/overuse_estimator.h"

#include <assert.h>
#include <math.h>
#include <string.h>

#include <algorithm>

#include "modules/remote_bitrate_estimator/include/bwe_defines.h"
#include "modules/remote_bitrate_estimator/test/bwe_test_logging.h"
#include "rtc_base/logging.h"

namespace webrtc {

enum { kMinFramePeriodHistoryLength = 60 };
enum { kDeltaCounterMax = 1000 };

OveruseEstimator::OveruseEstimator(const OverUseDetectorOptions& options)
    : options_(options),
      num_of_deltas_(0),
      slope_(options_.initial_slope),
      offset_(options_.initial_offset),
      prev_offset_(options_.initial_offset),
      E_(),
      process_noise_(),
      avg_noise_(options_.initial_avg_noise),
      var_noise_(options_.initial_var_noise),
      ts_delta_hist_() {
  memcpy(E_, options_.initial_e, sizeof(E_));
  memcpy(process_noise_, options_.initial_process_noise,
         sizeof(process_noise_));
}

OveruseEstimator::~OveruseEstimator() {
  ts_delta_hist_.clear();
}

void OveruseEstimator::Update(int64_t t_delta,
                              double ts_delta,
                              int size_delta,
                              BandwidthUsage current_hypothesis,
                              int64_t now_ms) {
  const double min_frame_period = UpdateMinFramePeriod(ts_delta);
  const double t_ts_delta = t_delta - ts_delta;
  BWE_TEST_LOGGING_PLOT(1, "dm_ms", now_ms, t_ts_delta);
  double fs_delta = size_delta;

  ++num_of_deltas_;
  if (num_of_deltas_ > kDeltaCounterMax) {
    num_of_deltas_ = kDeltaCounterMax;
  }

  // Update the Kalman filter.
  E_[0][0] += process_noise_[0];
  E_[1][1] += process_noise_[1];

  if ((current_hypothesis == BandwidthUsage::kBwOverusing &&
       offset_ < prev_offset_) ||
      (current_hypothesis == BandwidthUsage::kBwUnderusing &&
       offset_ > prev_offset_)) {
    E_[1][1] += 10 * process_noise_[1];
  }

  const double h[2] = {fs_delta, 1.0};
  const double Eh[2] = {E_[0][0] * h[0] + E_[0][1] * h[1],
                        E_[1][0] * h[0] + E_[1][1] * h[1]};

  BWE_TEST_LOGGING_PLOT(1, "d_ms", now_ms, slope_ * h[0] - offset_);

  const double residual = t_ts_delta - slope_ * h[0] - offset_;

  const bool in_stable_state =
      (current_hypothesis == BandwidthUsage::kBwNormal);
  const double max_residual = 3.0 * sqrt(var_noise_);
  // We try to filter out very late frames. For instance periodic key
  // frames doesn't fit the Gaussian model well.
  if (fabs(residual) < max_residual) {
    UpdateNoiseEstimate(residual, min_frame_period, in_stable_state);
  } else {
    UpdateNoiseEstimate(residual < 0 ? -max_residual : max_residual,
                        min_frame_period, in_stable_state);
  }

  const double denom = var_noise_ + h[0] * Eh[0] + h[1] * Eh[1];

  const double K[2] = {Eh[0] / denom, Eh[1] / denom};

  const double IKh[2][2] = {{1.0 - K[0] * h[0], -K[0] * h[1]},
                            {-K[1] * h[0], 1.0 - K[1] * h[1]}};
  const double e00 = E_[0][0];
  const double e01 = E_[0][1];

  // Update state.
  E_[0][0] = e00 * IKh[0][0] + E_[1][0] * IKh[0][1];
  E_[0][1] = e01 * IKh[0][0] + E_[1][1] * IKh[0][1];
  E_[1][0] = e00 * IKh[1][0] + E_[1][0] * IKh[1][1];
  E_[1][1] = e01 * IKh[1][0] + E_[1][1] * IKh[1][1];

  // The covariance matrix must be positive semi-definite.
  bool positive_semi_definite =
      E_[0][0] + E_[1][1] >= 0 &&
      E_[0][0] * E_[1][1] - E_[0][1] * E_[1][0] >= 0 && E_[0][0] >= 0;
  assert(positive_semi_definite);
  if (!positive_semi_definite) {
    RTC_LOG(LS_ERROR)
        << "The over-use estimator's covariance matrix is no longer "
           "semi-definite.";
  }

  slope_ = slope_ + K[0] * residual;
  prev_offset_ = offset_;
  offset_ = offset_ + K[1] * residual;

  BWE_TEST_LOGGING_PLOT(1, "kc", now_ms, K[0]);
  BWE_TEST_LOGGING_PLOT(1, "km", now_ms, K[1]);
  BWE_TEST_LOGGING_PLOT(1, "slope_1/bps", now_ms, slope_);
  BWE_TEST_LOGGING_PLOT(1, "var_noise", now_ms, var_noise_);
}

double OveruseEstimator::UpdateMinFramePeriod(double ts_delta) {
  double min_frame_period = ts_delta;
  if (ts_delta_hist_.size() >= kMinFramePeriodHistoryLength) {
    ts_delta_hist_.pop_front();
  }
  for (const double old_ts_delta : ts_delta_hist_) {
    min_frame_period = std::min(old_ts_delta, min_frame_period);
  }
  ts_delta_hist_.push_back(ts_delta);
  return min_frame_period;
}

void OveruseEstimator::UpdateNoiseEstimate(double residual,
                                           double ts_delta,
                                           bool stable_state) {
  if (!stable_state) {
    return;
  }
  // Faster filter during startup to faster adapt to the jitter level
  // of the network. |alpha| is tuned for 30 frames per second, but is scaled
  // according to |ts_delta|.
  double alpha = 0.01;
  if (num_of_deltas_ > 10 * 30) {
    alpha = 0.002;
  }
  // Only update the noise estimate if we're not over-using. |beta| is a
  // function of alpha and the time delta since the previous update.
  const double beta = pow(1 - alpha, ts_delta * 30.0 / 1000.0);
  avg_noise_ = beta * avg_noise_ + (1 - beta) * residual;
  var_noise_ = beta * var_noise_ +
               (1 - beta) * (avg_noise_ - residual) * (avg_noise_ - residual);
  if (var_noise_ < 1) {
    var_noise_ = 1;
  }
}
}  // namespace webrtc
