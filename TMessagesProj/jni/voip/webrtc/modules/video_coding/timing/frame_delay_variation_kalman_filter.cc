/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/timing/frame_delay_variation_kalman_filter.h"

#include "api/units/data_size.h"
#include "api/units/time_delta.h"

namespace webrtc {

namespace {
// TODO(brandtr): The value below corresponds to 8 Gbps. Is that reasonable?
constexpr double kMaxBandwidth = 0.000001;  // Unit: [1 / bytes per ms].
}  // namespace

FrameDelayVariationKalmanFilter::FrameDelayVariationKalmanFilter() {
  // TODO(brandtr): Is there a factor 1000 missing here?
  estimate_[0] = 1 / (512e3 / 8);  // Unit: [1 / bytes per ms]
  estimate_[1] = 0;                // Unit: [ms]

  // Initial estimate covariance.
  estimate_cov_[0][0] = 1e-4;  // Unit: [(1 / bytes per ms)^2]
  estimate_cov_[1][1] = 1e2;   // Unit: [ms^2]
  estimate_cov_[0][1] = estimate_cov_[1][0] = 0;

  // Process noise covariance.
  process_noise_cov_diag_[0] = 2.5e-10;  // Unit: [(1 / bytes per ms)^2]
  process_noise_cov_diag_[1] = 1e-10;    // Unit: [ms^2]
}

void FrameDelayVariationKalmanFilter::PredictAndUpdate(
    double frame_delay_variation_ms,
    double frame_size_variation_bytes,
    double max_frame_size_bytes,
    double var_noise) {
  // Sanity checks.
  if (max_frame_size_bytes < 1) {
    return;
  }
  if (var_noise <= 0.0) {
    return;
  }

  // This member function follows the data flow in
  // https://en.wikipedia.org/wiki/Kalman_filter#Details.

  // 1) Estimate prediction: `x = F*x`.
  // For this model, there is no need to explicitly predict the estimate, since
  // the state transition matrix is the identity.

  // 2) Estimate covariance prediction: `P = F*P*F' + Q`.
  // Again, since the state transition matrix is the identity, this update
  // is performed by simply adding the process noise covariance.
  estimate_cov_[0][0] += process_noise_cov_diag_[0];
  estimate_cov_[1][1] += process_noise_cov_diag_[1];

  // 3) Innovation: `y = z - H*x`.
  // This is the part of the measurement that cannot be explained by the current
  // estimate.
  double innovation =
      frame_delay_variation_ms -
      GetFrameDelayVariationEstimateTotal(frame_size_variation_bytes);

  // 4) Innovation variance: `s = H*P*H' + r`.
  double estim_cov_times_obs[2];
  estim_cov_times_obs[0] =
      estimate_cov_[0][0] * frame_size_variation_bytes + estimate_cov_[0][1];
  estim_cov_times_obs[1] =
      estimate_cov_[1][0] * frame_size_variation_bytes + estimate_cov_[1][1];
  double observation_noise_stddev =
      (300.0 * exp(-fabs(frame_size_variation_bytes) /
                   (1e0 * max_frame_size_bytes)) +
       1) *
      sqrt(var_noise);
  if (observation_noise_stddev < 1.0) {
    observation_noise_stddev = 1.0;
  }
  // TODO(brandtr): Shouldn't we add observation_noise_stddev^2 here? Otherwise,
  // the dimensional analysis fails.
  double innovation_var = frame_size_variation_bytes * estim_cov_times_obs[0] +
                          estim_cov_times_obs[1] + observation_noise_stddev;
  if ((innovation_var < 1e-9 && innovation_var >= 0) ||
      (innovation_var > -1e-9 && innovation_var <= 0)) {
    RTC_DCHECK_NOTREACHED();
    return;
  }

  // 5) Optimal Kalman gain: `K = P*H'/s`.
  // How much to trust the model vs. how much to trust the measurement.
  double kalman_gain[2];
  kalman_gain[0] = estim_cov_times_obs[0] / innovation_var;
  kalman_gain[1] = estim_cov_times_obs[1] / innovation_var;

  // 6) Estimate update: `x = x + K*y`.
  // Optimally weight the new information in the innovation and add it to the
  // old estimate.
  estimate_[0] += kalman_gain[0] * innovation;
  estimate_[1] += kalman_gain[1] * innovation;

  // (This clamping is not part of the linear Kalman filter.)
  if (estimate_[0] < kMaxBandwidth) {
    estimate_[0] = kMaxBandwidth;
  }

  // 7) Estimate covariance update: `P = (I - K*H)*P`
  double t00 = estimate_cov_[0][0];
  double t01 = estimate_cov_[0][1];
  estimate_cov_[0][0] =
      (1 - kalman_gain[0] * frame_size_variation_bytes) * t00 -
      kalman_gain[0] * estimate_cov_[1][0];
  estimate_cov_[0][1] =
      (1 - kalman_gain[0] * frame_size_variation_bytes) * t01 -
      kalman_gain[0] * estimate_cov_[1][1];
  estimate_cov_[1][0] = estimate_cov_[1][0] * (1 - kalman_gain[1]) -
                        kalman_gain[1] * frame_size_variation_bytes * t00;
  estimate_cov_[1][1] = estimate_cov_[1][1] * (1 - kalman_gain[1]) -
                        kalman_gain[1] * frame_size_variation_bytes * t01;

  // Covariance matrix, must be positive semi-definite.
  RTC_DCHECK(estimate_cov_[0][0] + estimate_cov_[1][1] >= 0 &&
             estimate_cov_[0][0] * estimate_cov_[1][1] -
                     estimate_cov_[0][1] * estimate_cov_[1][0] >=
                 0 &&
             estimate_cov_[0][0] >= 0);
}

double FrameDelayVariationKalmanFilter::GetFrameDelayVariationEstimateSizeBased(
    double frame_size_variation_bytes) const {
  // Unit: [1 / bytes per millisecond] * [bytes] = [milliseconds].
  return estimate_[0] * frame_size_variation_bytes;
}

double FrameDelayVariationKalmanFilter::GetFrameDelayVariationEstimateTotal(
    double frame_size_variation_bytes) const {
  double frame_transmission_delay_ms =
      GetFrameDelayVariationEstimateSizeBased(frame_size_variation_bytes);
  double link_queuing_delay_ms = estimate_[1];
  return frame_transmission_delay_ms + link_queuing_delay_ms;
}

}  // namespace webrtc
