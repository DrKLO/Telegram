/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/congestion_controller/pcc/pcc_network_controller.h"

#include <algorithm>

#include "absl/types/optional.h"
#include "api/units/data_size.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace pcc {
namespace {
constexpr int64_t kInitialRttMs = 200;
constexpr int64_t kInitialBandwidthKbps = 300;
constexpr double kMonitorIntervalDurationRatio = 1;
constexpr double kDefaultSamplingStep = 0.05;
constexpr double kTimeoutRatio = 2;
constexpr double kAlphaForRtt = 0.9;
constexpr double kSlowStartModeIncrease = 1.5;

constexpr double kAlphaForPacketInterval = 0.9;
constexpr int64_t kMinPacketsNumberPerInterval = 20;
const TimeDelta kMinDurationOfMonitorInterval = TimeDelta::Millis(50);
const TimeDelta kStartupDuration = TimeDelta::Millis(500);
constexpr double kMinRateChangeBps = 4000;
constexpr DataRate kMinRateHaveMultiplicativeRateChange = DataRate::BitsPerSec(
    static_cast<int64_t>(kMinRateChangeBps / kDefaultSamplingStep));

// Bitrate controller constants.
constexpr double kInitialConversionFactor = 5;
constexpr double kInitialDynamicBoundary = 0.1;
constexpr double kDynamicBoundaryIncrement = 0.1;
// Utility function parameters.
constexpr double kRttGradientCoefficientBps = 0.005;
constexpr double kLossCoefficientBps = 10;
constexpr double kThroughputCoefficient = 0.001;
constexpr double kThroughputPower = 0.9;
constexpr double kRttGradientThreshold = 0.01;
constexpr double kDelayGradientNegativeBound = 0.1;

constexpr int64_t kNumberOfPacketsToKeep = 20;
const uint64_t kRandomSeed = 100;
}  // namespace

PccNetworkController::PccNetworkController(NetworkControllerConfig config)
    : start_time_(Timestamp::PlusInfinity()),
      last_sent_packet_time_(Timestamp::PlusInfinity()),
      smoothed_packets_sending_interval_(TimeDelta::Zero()),
      mode_(Mode::kStartup),
      default_bandwidth_(DataRate::KilobitsPerSec(kInitialBandwidthKbps)),
      bandwidth_estimate_(default_bandwidth_),
      rtt_tracker_(TimeDelta::Millis(kInitialRttMs), kAlphaForRtt),
      monitor_interval_timeout_(TimeDelta::Millis(kInitialRttMs) *
                                kTimeoutRatio),
      monitor_interval_length_strategy_(MonitorIntervalLengthStrategy::kFixed),
      monitor_interval_duration_ratio_(kMonitorIntervalDurationRatio),
      sampling_step_(kDefaultSamplingStep),
      monitor_interval_timeout_ratio_(kTimeoutRatio),
      min_packets_number_per_interval_(kMinPacketsNumberPerInterval),
      bitrate_controller_(kInitialConversionFactor,
                          kInitialDynamicBoundary,
                          kDynamicBoundaryIncrement,
                          kRttGradientCoefficientBps,
                          kLossCoefficientBps,
                          kThroughputCoefficient,
                          kThroughputPower,
                          kRttGradientThreshold,
                          kDelayGradientNegativeBound),
      monitor_intervals_duration_(TimeDelta::Zero()),
      complete_feedback_monitor_interval_number_(0),
      random_generator_(kRandomSeed) {
  if (config.constraints.starting_rate) {
    default_bandwidth_ = *config.constraints.starting_rate;
    bandwidth_estimate_ = default_bandwidth_;
  }
}

PccNetworkController::~PccNetworkController() {}

NetworkControlUpdate PccNetworkController::CreateRateUpdate(
    Timestamp at_time) const {
  DataRate sending_rate = DataRate::Zero();
  if (monitor_intervals_.empty() ||
      (monitor_intervals_.size() >= monitor_intervals_bitrates_.size() &&
       at_time >= monitor_intervals_.back().GetEndTime())) {
    sending_rate = bandwidth_estimate_;
  } else {
    sending_rate = monitor_intervals_.back().GetTargetSendingRate();
  }
  // Set up config when sending rate is computed.
  NetworkControlUpdate update;

  // Set up target rate to encoder.
  TargetTransferRate target_rate_msg;
  target_rate_msg.at_time = at_time;
  target_rate_msg.network_estimate.at_time = at_time;
  target_rate_msg.network_estimate.round_trip_time = rtt_tracker_.GetRtt();
  // TODO(koloskova): Add correct estimate.
  target_rate_msg.network_estimate.loss_rate_ratio = 0;
  target_rate_msg.network_estimate.bwe_period =
      monitor_interval_duration_ratio_ * rtt_tracker_.GetRtt();

  target_rate_msg.target_rate = sending_rate;
  update.target_rate = target_rate_msg;

  // Set up pacing/padding target rate.
  PacerConfig pacer_config;
  pacer_config.at_time = at_time;
  pacer_config.time_window = TimeDelta::Millis(1);
  pacer_config.data_window = sending_rate * pacer_config.time_window;
  pacer_config.pad_window = sending_rate * pacer_config.time_window;

  update.pacer_config = pacer_config;
  return update;
}

NetworkControlUpdate PccNetworkController::OnSentPacket(SentPacket msg) {
  // Start new monitor interval if previous has finished.
  // Monitor interval is initialized in OnProcessInterval function.
  if (start_time_.IsInfinite()) {
    start_time_ = msg.send_time;
    monitor_intervals_duration_ = kStartupDuration;
    monitor_intervals_bitrates_ = {bandwidth_estimate_};
    monitor_intervals_.emplace_back(bandwidth_estimate_, msg.send_time,
                                    monitor_intervals_duration_);
    complete_feedback_monitor_interval_number_ = 0;
  }
  if (last_sent_packet_time_.IsFinite()) {
    smoothed_packets_sending_interval_ =
        (msg.send_time - last_sent_packet_time_) * kAlphaForPacketInterval +
        (1 - kAlphaForPacketInterval) * smoothed_packets_sending_interval_;
  }
  last_sent_packet_time_ = msg.send_time;
  if (!monitor_intervals_.empty() &&
      msg.send_time >= monitor_intervals_.back().GetEndTime() &&
      monitor_intervals_bitrates_.size() > monitor_intervals_.size()) {
    // Start new monitor interval.
    monitor_intervals_.emplace_back(
        monitor_intervals_bitrates_[monitor_intervals_.size()], msg.send_time,
        monitor_intervals_duration_);
  }
  if (IsTimeoutExpired(msg.send_time)) {
    DataSize received_size = DataSize::Zero();
    for (size_t i = 1; i < last_received_packets_.size(); ++i) {
      received_size += last_received_packets_[i].sent_packet.size;
    }
    TimeDelta sending_time = TimeDelta::Zero();
    if (last_received_packets_.size() > 0)
      sending_time = last_received_packets_.back().receive_time -
                     last_received_packets_.front().receive_time;
    DataRate receiving_rate = bandwidth_estimate_;
    if (sending_time > TimeDelta::Zero())
      receiving_rate = received_size / sending_time;
    bandwidth_estimate_ =
        std::min<DataRate>(bandwidth_estimate_ * 0.5, receiving_rate);
    if (mode_ == Mode::kSlowStart)
      mode_ = Mode::kOnlineLearning;
  }
  if (mode_ == Mode::kStartup &&
      msg.send_time - start_time_ >= kStartupDuration) {
    DataSize received_size = DataSize::Zero();
    for (size_t i = 1; i < last_received_packets_.size(); ++i) {
      received_size += last_received_packets_[i].sent_packet.size;
    }
    TimeDelta sending_time = TimeDelta::Zero();
    if (last_received_packets_.size() > 0)
      sending_time = last_received_packets_.back().receive_time -
                     last_received_packets_.front().receive_time;
    DataRate receiving_rate = bandwidth_estimate_;
    if (sending_time > TimeDelta::Zero())
      receiving_rate = received_size / sending_time;
    bandwidth_estimate_ = receiving_rate;
    monitor_intervals_.clear();
    mode_ = Mode::kSlowStart;
    monitor_intervals_duration_ = ComputeMonitorIntervalsDuration();
    monitor_intervals_bitrates_ = {bandwidth_estimate_};
    monitor_intervals_.emplace_back(bandwidth_estimate_, msg.send_time,
                                    monitor_intervals_duration_);
    bandwidth_estimate_ = bandwidth_estimate_ * (1 / kSlowStartModeIncrease);
    complete_feedback_monitor_interval_number_ = 0;
    return CreateRateUpdate(msg.send_time);
  }
  if (IsFeedbackCollectionDone() || IsTimeoutExpired(msg.send_time)) {
    // Creating new monitor intervals.
    monitor_intervals_.clear();
    monitor_interval_timeout_ =
        rtt_tracker_.GetRtt() * monitor_interval_timeout_ratio_;
    monitor_intervals_duration_ = ComputeMonitorIntervalsDuration();
    complete_feedback_monitor_interval_number_ = 0;
    // Compute bitrates and start first monitor interval.
    if (mode_ == Mode::kSlowStart) {
      monitor_intervals_bitrates_ = {kSlowStartModeIncrease *
                                     bandwidth_estimate_};
      monitor_intervals_.emplace_back(
          kSlowStartModeIncrease * bandwidth_estimate_, msg.send_time,
          monitor_intervals_duration_);
    } else {
      RTC_DCHECK(mode_ == Mode::kOnlineLearning || mode_ == Mode::kDoubleCheck);
      monitor_intervals_.clear();
      int64_t sign = 2 * (random_generator_.Rand(0, 1) % 2) - 1;
      RTC_DCHECK_GE(sign, -1);
      RTC_DCHECK_LE(sign, 1);
      if (bandwidth_estimate_ >= kMinRateHaveMultiplicativeRateChange) {
        monitor_intervals_bitrates_ = {
            bandwidth_estimate_ * (1 + sign * sampling_step_),
            bandwidth_estimate_ * (1 - sign * sampling_step_)};
      } else {
        monitor_intervals_bitrates_ = {
            DataRate::BitsPerSec(std::max<double>(
                bandwidth_estimate_.bps() + sign * kMinRateChangeBps, 0)),
            DataRate::BitsPerSec(std::max<double>(
                bandwidth_estimate_.bps() - sign * kMinRateChangeBps, 0))};
      }
      monitor_intervals_.emplace_back(monitor_intervals_bitrates_[0],
                                      msg.send_time,
                                      monitor_intervals_duration_);
    }
  }
  return CreateRateUpdate(msg.send_time);
}

TimeDelta PccNetworkController::ComputeMonitorIntervalsDuration() const {
  TimeDelta monitor_intervals_duration = TimeDelta::Zero();
  if (monitor_interval_length_strategy_ ==
      MonitorIntervalLengthStrategy::kAdaptive) {
    monitor_intervals_duration = std::max(
        rtt_tracker_.GetRtt() * monitor_interval_duration_ratio_,
        smoothed_packets_sending_interval_ * min_packets_number_per_interval_);
  } else {
    RTC_DCHECK(monitor_interval_length_strategy_ ==
               MonitorIntervalLengthStrategy::kFixed);
    monitor_intervals_duration =
        smoothed_packets_sending_interval_ * min_packets_number_per_interval_;
  }
  monitor_intervals_duration =
      std::max(kMinDurationOfMonitorInterval, monitor_intervals_duration);
  return monitor_intervals_duration;
}

bool PccNetworkController::IsTimeoutExpired(Timestamp current_time) const {
  if (complete_feedback_monitor_interval_number_ >= monitor_intervals_.size()) {
    return false;
  }
  return current_time -
             monitor_intervals_[complete_feedback_monitor_interval_number_]
                 .GetEndTime() >=
         monitor_interval_timeout_;
}

bool PccNetworkController::IsFeedbackCollectionDone() const {
  return complete_feedback_monitor_interval_number_ >=
         monitor_intervals_bitrates_.size();
}

NetworkControlUpdate PccNetworkController::OnTransportPacketsFeedback(
    TransportPacketsFeedback msg) {
  if (msg.packet_feedbacks.empty())
    return NetworkControlUpdate();
  // Save packets to last_received_packets_ array.
  for (const PacketResult& packet_result : msg.ReceivedWithSendInfo()) {
    last_received_packets_.push_back(packet_result);
  }
  while (last_received_packets_.size() > kNumberOfPacketsToKeep) {
    last_received_packets_.pop_front();
  }
  rtt_tracker_.OnPacketsFeedback(msg.PacketsWithFeedback(), msg.feedback_time);
  // Skip rate update in case when online learning mode just started, but
  // corresponding monitor intervals were not started yet.
  if (mode_ == Mode::kOnlineLearning &&
      monitor_intervals_bitrates_.size() < 2) {
    return NetworkControlUpdate();
  }
  if (!IsFeedbackCollectionDone() && !monitor_intervals_.empty()) {
    while (complete_feedback_monitor_interval_number_ <
           monitor_intervals_.size()) {
      monitor_intervals_[complete_feedback_monitor_interval_number_]
          .OnPacketsFeedback(msg.PacketsWithFeedback());
      if (!monitor_intervals_[complete_feedback_monitor_interval_number_]
               .IsFeedbackCollectionDone())
        break;
      ++complete_feedback_monitor_interval_number_;
    }
  }
  if (IsFeedbackCollectionDone()) {
    if (mode_ == Mode::kDoubleCheck) {
      mode_ = Mode::kOnlineLearning;
    } else if (NeedDoubleCheckMeasurments()) {
      mode_ = Mode::kDoubleCheck;
    }
    if (mode_ != Mode::kDoubleCheck)
      UpdateSendingRateAndMode();
  }
  return NetworkControlUpdate();
}

bool PccNetworkController::NeedDoubleCheckMeasurments() const {
  if (mode_ == Mode::kSlowStart) {
    return false;
  }
  double first_loss_rate = monitor_intervals_[0].GetLossRate();
  double second_loss_rate = monitor_intervals_[1].GetLossRate();
  DataRate first_bitrate = monitor_intervals_[0].GetTargetSendingRate();
  DataRate second_bitrate = monitor_intervals_[1].GetTargetSendingRate();
  if ((first_bitrate.bps() - second_bitrate.bps()) *
          (first_loss_rate - second_loss_rate) <
      0) {
    return true;
  }
  return false;
}

void PccNetworkController::UpdateSendingRateAndMode() {
  if (monitor_intervals_.empty() || !IsFeedbackCollectionDone()) {
    return;
  }
  if (mode_ == Mode::kSlowStart) {
    DataRate old_bandwidth_estimate = bandwidth_estimate_;
    bandwidth_estimate_ =
        bitrate_controller_
            .ComputeRateUpdateForSlowStartMode(monitor_intervals_[0])
            .value_or(bandwidth_estimate_);
    if (bandwidth_estimate_ <= old_bandwidth_estimate)
      mode_ = Mode::kOnlineLearning;
  } else {
    RTC_DCHECK(mode_ == Mode::kOnlineLearning);
    bandwidth_estimate_ =
        bitrate_controller_.ComputeRateUpdateForOnlineLearningMode(
            monitor_intervals_, bandwidth_estimate_);
  }
}

NetworkControlUpdate PccNetworkController::OnNetworkAvailability(
    NetworkAvailability msg) {
  return NetworkControlUpdate();
}

NetworkControlUpdate PccNetworkController::OnNetworkRouteChange(
    NetworkRouteChange msg) {
  return NetworkControlUpdate();
}

NetworkControlUpdate PccNetworkController::OnProcessInterval(
    ProcessInterval msg) {
  return CreateRateUpdate(msg.at_time);
}

NetworkControlUpdate PccNetworkController::OnTargetRateConstraints(
    TargetRateConstraints msg) {
  return NetworkControlUpdate();
}

NetworkControlUpdate PccNetworkController::OnRemoteBitrateReport(
    RemoteBitrateReport) {
  return NetworkControlUpdate();
}

NetworkControlUpdate PccNetworkController::OnRoundTripTimeUpdate(
    RoundTripTimeUpdate) {
  return NetworkControlUpdate();
}

NetworkControlUpdate PccNetworkController::OnTransportLossReport(
    TransportLossReport) {
  return NetworkControlUpdate();
}

NetworkControlUpdate PccNetworkController::OnStreamsConfig(StreamsConfig msg) {
  return NetworkControlUpdate();
}

NetworkControlUpdate PccNetworkController::OnReceivedPacket(
    ReceivedPacket msg) {
  return NetworkControlUpdate();
}

NetworkControlUpdate PccNetworkController::OnNetworkStateEstimate(
    NetworkStateEstimate msg) {
  return NetworkControlUpdate();
}

}  // namespace pcc
}  // namespace webrtc
