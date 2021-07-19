/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/congestion_controller/pcc/utility_function.h"

#include <algorithm>
#include <cmath>

#include "api/units/data_rate.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace pcc {

VivaceUtilityFunction::VivaceUtilityFunction(
    double delay_gradient_coefficient,
    double loss_coefficient,
    double throughput_coefficient,
    double throughput_power,
    double delay_gradient_threshold,
    double delay_gradient_negative_bound)
    : delay_gradient_coefficient_(delay_gradient_coefficient),
      loss_coefficient_(loss_coefficient),
      throughput_power_(throughput_power),
      throughput_coefficient_(throughput_coefficient),
      delay_gradient_threshold_(delay_gradient_threshold),
      delay_gradient_negative_bound_(delay_gradient_negative_bound) {
  RTC_DCHECK_GE(delay_gradient_negative_bound_, 0);
}

double VivaceUtilityFunction::Compute(
    const PccMonitorInterval& monitor_interval) const {
  RTC_DCHECK(monitor_interval.IsFeedbackCollectionDone());
  double bitrate = monitor_interval.GetTargetSendingRate().bps();
  double loss_rate = monitor_interval.GetLossRate();
  double rtt_gradient =
      monitor_interval.ComputeDelayGradient(delay_gradient_threshold_);
  rtt_gradient = std::max(rtt_gradient, -delay_gradient_negative_bound_);
  return (throughput_coefficient_ * std::pow(bitrate, throughput_power_)) -
         (delay_gradient_coefficient_ * bitrate * rtt_gradient) -
         (loss_coefficient_ * bitrate * loss_rate);
}

VivaceUtilityFunction::~VivaceUtilityFunction() = default;

ModifiedVivaceUtilityFunction::ModifiedVivaceUtilityFunction(
    double delay_gradient_coefficient,
    double loss_coefficient,
    double throughput_coefficient,
    double throughput_power,
    double delay_gradient_threshold,
    double delay_gradient_negative_bound)
    : delay_gradient_coefficient_(delay_gradient_coefficient),
      loss_coefficient_(loss_coefficient),
      throughput_power_(throughput_power),
      throughput_coefficient_(throughput_coefficient),
      delay_gradient_threshold_(delay_gradient_threshold),
      delay_gradient_negative_bound_(delay_gradient_negative_bound) {
  RTC_DCHECK_GE(delay_gradient_negative_bound_, 0);
}

double ModifiedVivaceUtilityFunction::Compute(
    const PccMonitorInterval& monitor_interval) const {
  RTC_DCHECK(monitor_interval.IsFeedbackCollectionDone());
  double bitrate = monitor_interval.GetTargetSendingRate().bps();
  double loss_rate = monitor_interval.GetLossRate();
  double rtt_gradient =
      monitor_interval.ComputeDelayGradient(delay_gradient_threshold_);
  rtt_gradient = std::max(rtt_gradient, -delay_gradient_negative_bound_);
  return (throughput_coefficient_ * std::pow(bitrate, throughput_power_) *
          bitrate) -
         (delay_gradient_coefficient_ * bitrate * bitrate * rtt_gradient) -
         (loss_coefficient_ * bitrate * bitrate * loss_rate);
}

ModifiedVivaceUtilityFunction::~ModifiedVivaceUtilityFunction() = default;

}  // namespace pcc
}  // namespace webrtc
