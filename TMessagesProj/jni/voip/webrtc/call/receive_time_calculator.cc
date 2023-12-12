/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "call/receive_time_calculator.h"

#include <memory>
#include <string>
#include <type_traits>

#include "rtc_base/experiments/field_trial_parser.h"
#include "rtc_base/numerics/safe_minmax.h"

namespace webrtc {
namespace {

const char kBweReceiveTimeCorrection[] = "WebRTC-Bwe-ReceiveTimeFix";
}  // namespace

ReceiveTimeCalculatorConfig::ReceiveTimeCalculatorConfig(
    const FieldTrialsView& field_trials)
    : max_packet_time_repair("maxrep", TimeDelta::Millis(2000)),
      stall_threshold("stall", TimeDelta::Millis(5)),
      tolerance("tol", TimeDelta::Millis(1)),
      max_stall("maxstall", TimeDelta::Seconds(5)) {
  std::string trial_string = field_trials.Lookup(kBweReceiveTimeCorrection);
  ParseFieldTrial(
      {&max_packet_time_repair, &stall_threshold, &tolerance, &max_stall},
      trial_string);
}
ReceiveTimeCalculatorConfig::ReceiveTimeCalculatorConfig(
    const ReceiveTimeCalculatorConfig&) = default;
ReceiveTimeCalculatorConfig::~ReceiveTimeCalculatorConfig() = default;

ReceiveTimeCalculator::ReceiveTimeCalculator(
    const FieldTrialsView& field_trials)
    : config_(field_trials) {}

std::unique_ptr<ReceiveTimeCalculator>
ReceiveTimeCalculator::CreateFromFieldTrial(
    const FieldTrialsView& field_trials) {
  if (!field_trials.IsEnabled(kBweReceiveTimeCorrection))
    return nullptr;
  return std::make_unique<ReceiveTimeCalculator>(field_trials);
}

int64_t ReceiveTimeCalculator::ReconcileReceiveTimes(int64_t packet_time_us,
                                                     int64_t system_time_us,
                                                     int64_t safe_time_us) {
  int64_t stall_time_us = system_time_us - packet_time_us;
  if (total_system_time_passed_us_ < config_.stall_threshold->us()) {
    stall_time_us = rtc::SafeMin(stall_time_us, config_.max_stall->us());
  }
  int64_t corrected_time_us = safe_time_us - stall_time_us;

  if (last_packet_time_us_ == -1 && stall_time_us < 0) {
    static_clock_offset_us_ = stall_time_us;
    corrected_time_us += static_clock_offset_us_;
  } else if (last_packet_time_us_ > 0) {
    // All repairs depend on variables being intialized
    int64_t packet_time_delta_us = packet_time_us - last_packet_time_us_;
    int64_t system_time_delta_us = system_time_us - last_system_time_us_;
    int64_t safe_time_delta_us = safe_time_us - last_safe_time_us_;

    // Repair backwards clock resets during initial stall. In this case, the
    // reset is observed only in packet time but never in system time.
    if (system_time_delta_us < 0)
      total_system_time_passed_us_ += config_.stall_threshold->us();
    else
      total_system_time_passed_us_ += system_time_delta_us;
    if (packet_time_delta_us < 0 &&
        total_system_time_passed_us_ < config_.stall_threshold->us()) {
      static_clock_offset_us_ -= packet_time_delta_us;
    }
    corrected_time_us += static_clock_offset_us_;

    // Detect resets inbetween clock readings in socket and app.
    bool forward_clock_reset =
        corrected_time_us + config_.tolerance->us() < last_corrected_time_us_;
    bool obvious_backward_clock_reset = system_time_us < packet_time_us;

    // Harder case with backward clock reset during stall, the reset being
    // smaller than the stall. Compensate throughout the stall.
    bool small_backward_clock_reset =
        !obvious_backward_clock_reset &&
        safe_time_delta_us > system_time_delta_us + config_.tolerance->us();
    bool stall_start =
        packet_time_delta_us >= 0 &&
        system_time_delta_us > packet_time_delta_us + config_.tolerance->us();
    bool stall_is_over = safe_time_delta_us > config_.stall_threshold->us();
    bool packet_time_caught_up =
        packet_time_delta_us < 0 && system_time_delta_us >= 0;
    if (stall_start && small_backward_clock_reset)
      small_reset_during_stall_ = true;
    else if (stall_is_over || packet_time_caught_up)
      small_reset_during_stall_ = false;

    // If resets are detected, advance time by (capped) packet time increase.
    if (forward_clock_reset || obvious_backward_clock_reset ||
        small_reset_during_stall_) {
      corrected_time_us = last_corrected_time_us_ +
                          rtc::SafeClamp(packet_time_delta_us, 0,
                                         config_.max_packet_time_repair->us());
    }
  }

  last_corrected_time_us_ = corrected_time_us;
  last_packet_time_us_ = packet_time_us;
  last_system_time_us_ = system_time_us;
  last_safe_time_us_ = safe_time_us;
  return corrected_time_us;
}

}  // namespace webrtc
