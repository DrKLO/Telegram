/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/congestion_controller/pcc/bitrate_controller.h"

#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <memory>
#include <utility>
#include <vector>

namespace webrtc {
namespace pcc {

PccBitrateController::PccBitrateController(double initial_conversion_factor,
                                           double initial_dynamic_boundary,
                                           double dynamic_boundary_increment,
                                           double rtt_gradient_coefficient,
                                           double loss_coefficient,
                                           double throughput_coefficient,
                                           double throughput_power,
                                           double rtt_gradient_threshold,
                                           double delay_gradient_negative_bound)
    : PccBitrateController(initial_conversion_factor,
                           initial_dynamic_boundary,
                           dynamic_boundary_increment,
                           std::make_unique<ModifiedVivaceUtilityFunction>(
                               rtt_gradient_coefficient,
                               loss_coefficient,
                               throughput_coefficient,
                               throughput_power,
                               rtt_gradient_threshold,
                               delay_gradient_negative_bound)) {}

PccBitrateController::PccBitrateController(
    double initial_conversion_factor,
    double initial_dynamic_boundary,
    double dynamic_boundary_increment,
    std::unique_ptr<PccUtilityFunctionInterface> utility_function)
    : consecutive_boundary_adjustments_number_(0),
      initial_dynamic_boundary_(initial_dynamic_boundary),
      dynamic_boundary_increment_(dynamic_boundary_increment),
      utility_function_(std::move(utility_function)),
      step_size_adjustments_number_(0),
      initial_conversion_factor_(initial_conversion_factor) {}

PccBitrateController::~PccBitrateController() = default;

double PccBitrateController::ComputeStepSize(double utility_gradient) {
  // Computes number of consecutive step size adjustments.
  if (utility_gradient > 0) {
    step_size_adjustments_number_ =
        std::max<int64_t>(step_size_adjustments_number_ + 1, 1);
  } else if (utility_gradient < 0) {
    step_size_adjustments_number_ =
        std::min<int64_t>(step_size_adjustments_number_ - 1, -1);
  } else {
    step_size_adjustments_number_ = 0;
  }
  // Computes step size amplifier.
  int64_t step_size_amplifier = 1;
  if (std::abs(step_size_adjustments_number_) <= 3) {
    step_size_amplifier =
        std::max<int64_t>(std::abs(step_size_adjustments_number_), 1);
  } else {
    step_size_amplifier = 2 * std::abs(step_size_adjustments_number_) - 3;
  }
  return step_size_amplifier * initial_conversion_factor_;
}

double PccBitrateController::ApplyDynamicBoundary(double rate_change,
                                                  double bitrate) {
  double rate_change_abs = std::abs(rate_change);
  int64_t rate_change_sign = (rate_change > 0) ? 1 : -1;
  if (consecutive_boundary_adjustments_number_ * rate_change_sign < 0) {
    consecutive_boundary_adjustments_number_ = 0;
  }
  double dynamic_change_boundary =
      initial_dynamic_boundary_ +
      std::abs(consecutive_boundary_adjustments_number_) *
          dynamic_boundary_increment_;
  double boundary = bitrate * dynamic_change_boundary;
  if (rate_change_abs > boundary) {
    consecutive_boundary_adjustments_number_ += rate_change_sign;
    return boundary * rate_change_sign;
  }
  // Rate change smaller than boundary. Reset boundary to the smallest possible
  // that would allow the change.
  while (rate_change_abs <= boundary &&
         consecutive_boundary_adjustments_number_ * rate_change_sign > 0) {
    consecutive_boundary_adjustments_number_ -= rate_change_sign;
    dynamic_change_boundary =
        initial_dynamic_boundary_ +
        std::abs(consecutive_boundary_adjustments_number_) *
            dynamic_boundary_increment_;
    boundary = bitrate * dynamic_change_boundary;
  }
  consecutive_boundary_adjustments_number_ += rate_change_sign;
  return rate_change;
}

absl::optional<DataRate>
PccBitrateController::ComputeRateUpdateForSlowStartMode(
    const PccMonitorInterval& monitor_interval) {
  double utility_value = utility_function_->Compute(monitor_interval);
  if (previous_utility_.has_value() && utility_value <= previous_utility_) {
    return absl::nullopt;
  }
  previous_utility_ = utility_value;
  return monitor_interval.GetTargetSendingRate();
}

DataRate PccBitrateController::ComputeRateUpdateForOnlineLearningMode(
    const std::vector<PccMonitorInterval>& intervals,
    DataRate bandwith_estimate) {
  double first_utility = utility_function_->Compute(intervals[0]);
  double second_utility = utility_function_->Compute(intervals[1]);
  double first_bitrate_bps = intervals[0].GetTargetSendingRate().bps();
  double second_bitrate_bps = intervals[1].GetTargetSendingRate().bps();
  double gradient = (first_utility - second_utility) /
                    (first_bitrate_bps - second_bitrate_bps);
  double rate_change_bps = gradient * ComputeStepSize(gradient);  // delta_r
  rate_change_bps =
      ApplyDynamicBoundary(rate_change_bps, bandwith_estimate.bps());
  return DataRate::BitsPerSec(
      std::max(0.0, bandwith_estimate.bps() + rate_change_bps));
}

}  // namespace pcc
}  // namespace webrtc
