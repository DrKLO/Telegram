/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/congestion_controller/goog_cc/loss_based_bandwidth_estimation.h"

#include <algorithm>
#include <string>
#include <vector>

#include "api/units/data_rate.h"
#include "api/units/time_delta.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {
namespace {
const char kBweLossBasedControl[] = "WebRTC-Bwe-LossBasedControl";

// Increase slower when RTT is high.
double GetIncreaseFactor(const LossBasedControlConfig& config, TimeDelta rtt) {
  // Clamp the RTT
  if (rtt < config.increase_low_rtt) {
    rtt = config.increase_low_rtt;
  } else if (rtt > config.increase_high_rtt) {
    rtt = config.increase_high_rtt;
  }
  auto rtt_range = config.increase_high_rtt.Get() - config.increase_low_rtt;
  if (rtt_range <= TimeDelta::Zero()) {
    RTC_DCHECK(false);  // Only on misconfiguration.
    return config.min_increase_factor;
  }
  auto rtt_offset = rtt - config.increase_low_rtt;
  auto relative_offset = std::max(0.0, std::min(rtt_offset / rtt_range, 1.0));
  auto factor_range = config.max_increase_factor - config.min_increase_factor;
  return config.min_increase_factor + (1 - relative_offset) * factor_range;
}

double LossFromBitrate(DataRate bitrate,
                       DataRate loss_bandwidth_balance,
                       double exponent) {
  if (loss_bandwidth_balance >= bitrate)
    return 1.0;
  return pow(loss_bandwidth_balance / bitrate, exponent);
}

DataRate BitrateFromLoss(double loss,
                         DataRate loss_bandwidth_balance,
                         double exponent) {
  if (exponent <= 0) {
    RTC_DCHECK(false);
    return DataRate::Infinity();
  }
  if (loss < 1e-5)
    return DataRate::Infinity();
  return loss_bandwidth_balance * pow(loss, -1.0 / exponent);
}

double ExponentialUpdate(TimeDelta window, TimeDelta interval) {
  // Use the convention that exponential window length (which is really
  // infinite) is the time it takes to dampen to 1/e.
  if (window <= TimeDelta::Zero()) {
    RTC_DCHECK(false);
    return 1.0f;
  }
  return 1.0f - exp(interval / window * -1.0);
}

}  // namespace

LossBasedControlConfig::LossBasedControlConfig()
    : enabled(field_trial::IsEnabled(kBweLossBasedControl)),
      min_increase_factor("min_incr", 1.02),
      max_increase_factor("max_incr", 1.08),
      increase_low_rtt("incr_low_rtt", TimeDelta::Millis(200)),
      increase_high_rtt("incr_high_rtt", TimeDelta::Millis(800)),
      decrease_factor("decr", 0.99),
      loss_window("loss_win", TimeDelta::Millis(800)),
      loss_max_window("loss_max_win", TimeDelta::Millis(800)),
      acknowledged_rate_max_window("ackrate_max_win", TimeDelta::Millis(800)),
      increase_offset("incr_offset", DataRate::BitsPerSec(1000)),
      loss_bandwidth_balance_increase("balance_incr",
                                      DataRate::KilobitsPerSec(0.5)),
      loss_bandwidth_balance_decrease("balance_decr",
                                      DataRate::KilobitsPerSec(4)),
      loss_bandwidth_balance_exponent("exponent", 0.5),
      allow_resets("resets", false),
      decrease_interval("decr_intvl", TimeDelta::Millis(300)),
      loss_report_timeout("timeout", TimeDelta::Millis(6000)) {
  std::string trial_string = field_trial::FindFullName(kBweLossBasedControl);
  ParseFieldTrial(
      {&min_increase_factor, &max_increase_factor, &increase_low_rtt,
       &increase_high_rtt, &decrease_factor, &loss_window, &loss_max_window,
       &acknowledged_rate_max_window, &increase_offset,
       &loss_bandwidth_balance_increase, &loss_bandwidth_balance_decrease,
       &loss_bandwidth_balance_exponent, &allow_resets, &decrease_interval,
       &loss_report_timeout},
      trial_string);
}
LossBasedControlConfig::LossBasedControlConfig(const LossBasedControlConfig&) =
    default;
LossBasedControlConfig::~LossBasedControlConfig() = default;

LossBasedBandwidthEstimation::LossBasedBandwidthEstimation()
    : config_(LossBasedControlConfig()),
      average_loss_(0),
      average_loss_max_(0),
      loss_based_bitrate_(DataRate::Zero()),
      acknowledged_bitrate_max_(DataRate::Zero()),
      acknowledged_bitrate_last_update_(Timestamp::MinusInfinity()),
      time_last_decrease_(Timestamp::MinusInfinity()),
      has_decreased_since_last_loss_report_(false),
      last_loss_packet_report_(Timestamp::MinusInfinity()),
      last_loss_ratio_(0) {}

void LossBasedBandwidthEstimation::UpdateLossStatistics(
    const std::vector<PacketResult>& packet_results,
    Timestamp at_time) {
  if (packet_results.empty()) {
    RTC_DCHECK(false);
    return;
  }
  int loss_count = 0;
  for (const auto& pkt : packet_results) {
    loss_count += pkt.receive_time.IsInfinite() ? 1 : 0;
  }
  last_loss_ratio_ = static_cast<double>(loss_count) / packet_results.size();
  const TimeDelta time_passed = last_loss_packet_report_.IsFinite()
                                    ? at_time - last_loss_packet_report_
                                    : TimeDelta::Seconds(1);
  last_loss_packet_report_ = at_time;
  has_decreased_since_last_loss_report_ = false;

  average_loss_ += ExponentialUpdate(config_.loss_window, time_passed) *
                   (last_loss_ratio_ - average_loss_);
  if (average_loss_ > average_loss_max_) {
    average_loss_max_ = average_loss_;
  } else {
    average_loss_max_ +=
        ExponentialUpdate(config_.loss_max_window, time_passed) *
        (average_loss_ - average_loss_max_);
  }
}

void LossBasedBandwidthEstimation::UpdateAcknowledgedBitrate(
    DataRate acknowledged_bitrate,
    Timestamp at_time) {
  const TimeDelta time_passed =
      acknowledged_bitrate_last_update_.IsFinite()
          ? at_time - acknowledged_bitrate_last_update_
          : TimeDelta::Seconds(1);
  acknowledged_bitrate_last_update_ = at_time;
  if (acknowledged_bitrate > acknowledged_bitrate_max_) {
    acknowledged_bitrate_max_ = acknowledged_bitrate;
  } else {
    acknowledged_bitrate_max_ -=
        ExponentialUpdate(config_.acknowledged_rate_max_window, time_passed) *
        (acknowledged_bitrate_max_ - acknowledged_bitrate);
  }
}

void LossBasedBandwidthEstimation::Update(Timestamp at_time,
                                          DataRate min_bitrate,
                                          TimeDelta last_round_trip_time) {
  // Only increase if loss has been low for some time.
  const double loss_estimate_for_increase = average_loss_max_;
  // Avoid multiple decreases from averaging over one loss spike.
  const double loss_estimate_for_decrease =
      std::min(average_loss_, last_loss_ratio_);
  const bool allow_decrease =
      !has_decreased_since_last_loss_report_ &&
      (at_time - time_last_decrease_ >=
       last_round_trip_time + config_.decrease_interval);

  if (loss_estimate_for_increase < loss_increase_threshold()) {
    // Increase bitrate by RTT-adaptive ratio.
    DataRate new_increased_bitrate =
        min_bitrate * GetIncreaseFactor(config_, last_round_trip_time) +
        config_.increase_offset;
    // The bitrate that would make the loss "just high enough".
    const DataRate new_increased_bitrate_cap = BitrateFromLoss(
        loss_estimate_for_increase, config_.loss_bandwidth_balance_increase,
        config_.loss_bandwidth_balance_exponent);
    new_increased_bitrate =
        std::min(new_increased_bitrate, new_increased_bitrate_cap);
    loss_based_bitrate_ = std::max(new_increased_bitrate, loss_based_bitrate_);
  } else if (loss_estimate_for_decrease > loss_decrease_threshold() &&
             allow_decrease) {
    // The bitrate that would make the loss "just acceptable".
    const DataRate new_decreased_bitrate_floor = BitrateFromLoss(
        loss_estimate_for_decrease, config_.loss_bandwidth_balance_decrease,
        config_.loss_bandwidth_balance_exponent);
    DataRate new_decreased_bitrate =
        std::max(decreased_bitrate(), new_decreased_bitrate_floor);
    if (new_decreased_bitrate < loss_based_bitrate_) {
      time_last_decrease_ = at_time;
      has_decreased_since_last_loss_report_ = true;
      loss_based_bitrate_ = new_decreased_bitrate;
    }
  }
}

void LossBasedBandwidthEstimation::Reset(DataRate bitrate) {
  loss_based_bitrate_ = bitrate;
  average_loss_ = 0;
  average_loss_max_ = 0;
}

double LossBasedBandwidthEstimation::loss_increase_threshold() const {
  return LossFromBitrate(loss_based_bitrate_,
                         config_.loss_bandwidth_balance_increase,
                         config_.loss_bandwidth_balance_exponent);
}

double LossBasedBandwidthEstimation::loss_decrease_threshold() const {
  return LossFromBitrate(loss_based_bitrate_,
                         config_.loss_bandwidth_balance_decrease,
                         config_.loss_bandwidth_balance_exponent);
}

DataRate LossBasedBandwidthEstimation::decreased_bitrate() const {
  return config_.decrease_factor * acknowledged_bitrate_max_;
}

void LossBasedBandwidthEstimation::MaybeReset(DataRate bitrate) {
  if (config_.allow_resets)
    Reset(bitrate);
}

void LossBasedBandwidthEstimation::SetInitialBitrate(DataRate bitrate) {
  Reset(bitrate);
}

}  // namespace webrtc
