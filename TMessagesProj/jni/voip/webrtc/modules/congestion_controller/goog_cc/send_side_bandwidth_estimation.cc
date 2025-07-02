/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/congestion_controller/goog_cc/send_side_bandwidth_estimation.h"

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <limits>
#include <memory>
#include <string>
#include <utility>

#include "absl/strings/match.h"
#include "absl/types/optional.h"
#include "api/field_trials_view.h"
#include "api/network_state_predictor.h"
#include "api/rtc_event_log/rtc_event_log.h"
#include "api/transport/network_types.h"
#include "api/units/data_rate.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "logging/rtc_event_log/events/rtc_event_bwe_update_loss_based.h"
#include "modules/congestion_controller/goog_cc/loss_based_bwe_v2.h"
#include "modules/remote_bitrate_estimator/include/bwe_defines.h"
#include "rtc_base/checks.h"
#include "rtc_base/experiments/field_trial_parser.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/field_trial.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {
namespace {
constexpr TimeDelta kBweIncreaseInterval = TimeDelta::Millis(1000);
constexpr TimeDelta kBweDecreaseInterval = TimeDelta::Millis(300);
constexpr TimeDelta kStartPhase = TimeDelta::Millis(2000);
constexpr TimeDelta kBweConverganceTime = TimeDelta::Millis(20000);
constexpr int kLimitNumPackets = 20;
constexpr DataRate kDefaultMaxBitrate = DataRate::BitsPerSec(1000000000);
constexpr TimeDelta kLowBitrateLogPeriod = TimeDelta::Millis(10000);
constexpr TimeDelta kRtcEventLogPeriod = TimeDelta::Millis(5000);
// Expecting that RTCP feedback is sent uniformly within [0.5, 1.5]s intervals.
constexpr TimeDelta kMaxRtcpFeedbackInterval = TimeDelta::Millis(5000);

constexpr float kDefaultLowLossThreshold = 0.02f;
constexpr float kDefaultHighLossThreshold = 0.1f;
constexpr DataRate kDefaultBitrateThreshold = DataRate::Zero();

struct UmaRampUpMetric {
  const char* metric_name;
  int bitrate_kbps;
};

const UmaRampUpMetric kUmaRampupMetrics[] = {
    {"WebRTC.BWE.RampUpTimeTo500kbpsInMs", 500},
    {"WebRTC.BWE.RampUpTimeTo1000kbpsInMs", 1000},
    {"WebRTC.BWE.RampUpTimeTo2000kbpsInMs", 2000}};
const size_t kNumUmaRampupMetrics =
    sizeof(kUmaRampupMetrics) / sizeof(kUmaRampupMetrics[0]);

const char kBweLosExperiment[] = "WebRTC-BweLossExperiment";

bool BweLossExperimentIsEnabled() {
  std::string experiment_string =
      webrtc::field_trial::FindFullName(kBweLosExperiment);
  // The experiment is enabled iff the field trial string begins with "Enabled".
  return absl::StartsWith(experiment_string, "Enabled");
}

bool ReadBweLossExperimentParameters(float* low_loss_threshold,
                                     float* high_loss_threshold,
                                     uint32_t* bitrate_threshold_kbps) {
  RTC_DCHECK(low_loss_threshold);
  RTC_DCHECK(high_loss_threshold);
  RTC_DCHECK(bitrate_threshold_kbps);
  std::string experiment_string =
      webrtc::field_trial::FindFullName(kBweLosExperiment);
  int parsed_values =
      sscanf(experiment_string.c_str(), "Enabled-%f,%f,%u", low_loss_threshold,
             high_loss_threshold, bitrate_threshold_kbps);
  if (parsed_values == 3) {
    RTC_CHECK_GT(*low_loss_threshold, 0.0f)
        << "Loss threshold must be greater than 0.";
    RTC_CHECK_LE(*low_loss_threshold, 1.0f)
        << "Loss threshold must be less than or equal to 1.";
    RTC_CHECK_GT(*high_loss_threshold, 0.0f)
        << "Loss threshold must be greater than 0.";
    RTC_CHECK_LE(*high_loss_threshold, 1.0f)
        << "Loss threshold must be less than or equal to 1.";
    RTC_CHECK_LE(*low_loss_threshold, *high_loss_threshold)
        << "The low loss threshold must be less than or equal to the high loss "
           "threshold.";
    RTC_CHECK_GE(*bitrate_threshold_kbps, 0)
        << "Bitrate threshold can't be negative.";
    RTC_CHECK_LT(*bitrate_threshold_kbps,
                 std::numeric_limits<int>::max() / 1000)
        << "Bitrate must be smaller enough to avoid overflows.";
    return true;
  }
  RTC_LOG(LS_WARNING) << "Failed to parse parameters for BweLossExperiment "
                         "experiment from field trial string. Using default.";
  *low_loss_threshold = kDefaultLowLossThreshold;
  *high_loss_threshold = kDefaultHighLossThreshold;
  *bitrate_threshold_kbps = kDefaultBitrateThreshold.kbps();
  return false;
}
}  // namespace

LinkCapacityTracker::LinkCapacityTracker()
    : tracking_rate("rate", TimeDelta::Seconds(10)) {
  ParseFieldTrial({&tracking_rate},
                  field_trial::FindFullName("WebRTC-Bwe-LinkCapacity"));
}

LinkCapacityTracker::~LinkCapacityTracker() {}

void LinkCapacityTracker::UpdateDelayBasedEstimate(
    Timestamp at_time,
    DataRate delay_based_bitrate) {
  if (delay_based_bitrate < last_delay_based_estimate_) {
    capacity_estimate_bps_ =
        std::min(capacity_estimate_bps_, delay_based_bitrate.bps<double>());
    last_link_capacity_update_ = at_time;
  }
  last_delay_based_estimate_ = delay_based_bitrate;
}

void LinkCapacityTracker::OnStartingRate(DataRate start_rate) {
  if (last_link_capacity_update_.IsInfinite())
    capacity_estimate_bps_ = start_rate.bps<double>();
}

void LinkCapacityTracker::OnRateUpdate(absl::optional<DataRate> acknowledged,
                                       DataRate target,
                                       Timestamp at_time) {
  if (!acknowledged)
    return;
  DataRate acknowledged_target = std::min(*acknowledged, target);
  if (acknowledged_target.bps() > capacity_estimate_bps_) {
    TimeDelta delta = at_time - last_link_capacity_update_;
    double alpha = delta.IsFinite() ? exp(-(delta / tracking_rate.Get())) : 0;
    capacity_estimate_bps_ = alpha * capacity_estimate_bps_ +
                             (1 - alpha) * acknowledged_target.bps<double>();
  }
  last_link_capacity_update_ = at_time;
}

void LinkCapacityTracker::OnRttBackoff(DataRate backoff_rate,
                                       Timestamp at_time) {
  capacity_estimate_bps_ =
      std::min(capacity_estimate_bps_, backoff_rate.bps<double>());
  last_link_capacity_update_ = at_time;
}

DataRate LinkCapacityTracker::estimate() const {
  return DataRate::BitsPerSec(capacity_estimate_bps_);
}

RttBasedBackoff::RttBasedBackoff(const FieldTrialsView* key_value_config)
    : disabled_("Disabled"),
      configured_limit_("limit", TimeDelta::Seconds(3)),
      drop_fraction_("fraction", 0.8),
      drop_interval_("interval", TimeDelta::Seconds(1)),
      bandwidth_floor_("floor", DataRate::KilobitsPerSec(5)),
      rtt_limit_(TimeDelta::PlusInfinity()),
      // By initializing this to plus infinity, we make sure that we never
      // trigger rtt backoff unless packet feedback is enabled.
      last_propagation_rtt_update_(Timestamp::PlusInfinity()),
      last_propagation_rtt_(TimeDelta::Zero()),
      last_packet_sent_(Timestamp::MinusInfinity()) {
  ParseFieldTrial({&disabled_, &configured_limit_, &drop_fraction_,
                   &drop_interval_, &bandwidth_floor_},
                  key_value_config->Lookup("WebRTC-Bwe-MaxRttLimit"));
  if (!disabled_) {
    rtt_limit_ = configured_limit_.Get();
  }
}

void RttBasedBackoff::UpdatePropagationRtt(Timestamp at_time,
                                           TimeDelta propagation_rtt) {
  last_propagation_rtt_update_ = at_time;
  last_propagation_rtt_ = propagation_rtt;
}

bool RttBasedBackoff::IsRttAboveLimit() const {
  return CorrectedRtt() > rtt_limit_;
}

TimeDelta RttBasedBackoff::CorrectedRtt() const {
  // Avoid timeout when no packets are being sent.
  TimeDelta timeout_correction = std::max(
      last_packet_sent_ - last_propagation_rtt_update_, TimeDelta::Zero());
  return timeout_correction + last_propagation_rtt_;
}

RttBasedBackoff::~RttBasedBackoff() = default;

SendSideBandwidthEstimation::SendSideBandwidthEstimation(
    const FieldTrialsView* key_value_config, RtcEventLog* event_log)
    : key_value_config_(key_value_config),
      rtt_backoff_(key_value_config),
      lost_packets_since_last_loss_update_(0),
      expected_packets_since_last_loss_update_(0),
      current_target_(DataRate::Zero()),
      last_logged_target_(DataRate::Zero()),
      min_bitrate_configured_(kCongestionControllerMinBitrate),
      max_bitrate_configured_(kDefaultMaxBitrate),
      last_low_bitrate_log_(Timestamp::MinusInfinity()),
      has_decreased_since_last_fraction_loss_(false),
      last_loss_feedback_(Timestamp::MinusInfinity()),
      last_loss_packet_report_(Timestamp::MinusInfinity()),
      last_fraction_loss_(0),
      last_logged_fraction_loss_(0),
      last_round_trip_time_(TimeDelta::Zero()),
      receiver_limit_(DataRate::PlusInfinity()),
      delay_based_limit_(DataRate::PlusInfinity()),
      time_last_decrease_(Timestamp::MinusInfinity()),
      first_report_time_(Timestamp::MinusInfinity()),
      initially_lost_packets_(0),
      bitrate_at_2_seconds_(DataRate::Zero()),
      uma_update_state_(kNoUpdate),
      uma_rtt_state_(kNoUpdate),
      rampup_uma_stats_updated_(kNumUmaRampupMetrics, false),
      event_log_(event_log),
      last_rtc_event_log_(Timestamp::MinusInfinity()),
      low_loss_threshold_(kDefaultLowLossThreshold),
      high_loss_threshold_(kDefaultHighLossThreshold),
      bitrate_threshold_(kDefaultBitrateThreshold),
      loss_based_bandwidth_estimator_v1_(key_value_config),
      loss_based_bandwidth_estimator_v2_(new LossBasedBweV2(key_value_config)),
      loss_based_state_(LossBasedState::kDelayBasedEstimate),
      disable_receiver_limit_caps_only_("Disabled") {
  RTC_DCHECK(event_log);
  if (BweLossExperimentIsEnabled()) {
    uint32_t bitrate_threshold_kbps;
    if (ReadBweLossExperimentParameters(&low_loss_threshold_,
                                        &high_loss_threshold_,
                                        &bitrate_threshold_kbps)) {
      RTC_LOG(LS_INFO) << "Enabled BweLossExperiment with parameters "
                       << low_loss_threshold_ << ", " << high_loss_threshold_
                       << ", " << bitrate_threshold_kbps;
      bitrate_threshold_ = DataRate::KilobitsPerSec(bitrate_threshold_kbps);
    }
  }
  ParseFieldTrial({&disable_receiver_limit_caps_only_},
                  key_value_config->Lookup("WebRTC-Bwe-ReceiverLimitCapsOnly"));
  if (LossBasedBandwidthEstimatorV2Enabled()) {
    loss_based_bandwidth_estimator_v2_->SetMinMaxBitrate(
        min_bitrate_configured_, max_bitrate_configured_);
  }
}

SendSideBandwidthEstimation::~SendSideBandwidthEstimation() {}

void SendSideBandwidthEstimation::OnRouteChange() {
  lost_packets_since_last_loss_update_ = 0;
  expected_packets_since_last_loss_update_ = 0;
  current_target_ = DataRate::Zero();
  min_bitrate_configured_ = kCongestionControllerMinBitrate;
  max_bitrate_configured_ = kDefaultMaxBitrate;
  last_low_bitrate_log_ = Timestamp::MinusInfinity();
  has_decreased_since_last_fraction_loss_ = false;
  last_loss_feedback_ = Timestamp::MinusInfinity();
  last_loss_packet_report_ = Timestamp::MinusInfinity();
  last_fraction_loss_ = 0;
  last_logged_fraction_loss_ = 0;
  last_round_trip_time_ = TimeDelta::Zero();
  receiver_limit_ = DataRate::PlusInfinity();
  delay_based_limit_ = DataRate::PlusInfinity();
  time_last_decrease_ = Timestamp::MinusInfinity();
  first_report_time_ = Timestamp::MinusInfinity();
  initially_lost_packets_ = 0;
  bitrate_at_2_seconds_ = DataRate::Zero();
  uma_update_state_ = kNoUpdate;
  uma_rtt_state_ = kNoUpdate;
  last_rtc_event_log_ = Timestamp::MinusInfinity();
  if (LossBasedBandwidthEstimatorV2Enabled() &&
      loss_based_bandwidth_estimator_v2_->UseInStartPhase()) {
    loss_based_bandwidth_estimator_v2_.reset(
        new LossBasedBweV2(key_value_config_));
  }
}

void SendSideBandwidthEstimation::SetBitrates(
    absl::optional<DataRate> send_bitrate,
    DataRate min_bitrate,
    DataRate max_bitrate,
    Timestamp at_time) {
  SetMinMaxBitrate(min_bitrate, max_bitrate);
  if (send_bitrate) {
    link_capacity_.OnStartingRate(*send_bitrate);
    SetSendBitrate(*send_bitrate, at_time);
  }
}

void SendSideBandwidthEstimation::SetSendBitrate(DataRate bitrate,
                                                 Timestamp at_time) {
  RTC_DCHECK_GT(bitrate, DataRate::Zero());
  // Reset to avoid being capped by the estimate.
  delay_based_limit_ = DataRate::PlusInfinity();
  UpdateTargetBitrate(bitrate, at_time);
  // Clear last sent bitrate history so the new value can be used directly
  // and not capped.
  min_bitrate_history_.clear();
}

void SendSideBandwidthEstimation::SetMinMaxBitrate(DataRate min_bitrate,
                                                   DataRate max_bitrate) {
  min_bitrate_configured_ =
      std::max(min_bitrate, kCongestionControllerMinBitrate);
  if (max_bitrate > DataRate::Zero() && max_bitrate.IsFinite()) {
    max_bitrate_configured_ = std::max(min_bitrate_configured_, max_bitrate);
  } else {
    max_bitrate_configured_ = kDefaultMaxBitrate;
  }
  loss_based_bandwidth_estimator_v2_->SetMinMaxBitrate(min_bitrate_configured_,
                                                       max_bitrate_configured_);
}

int SendSideBandwidthEstimation::GetMinBitrate() const {
  return min_bitrate_configured_.bps<int>();
}

DataRate SendSideBandwidthEstimation::target_rate() const {
  DataRate target = current_target_;
  if (!disable_receiver_limit_caps_only_)
    target = std::min(target, receiver_limit_);
  return std::max(min_bitrate_configured_, target);
}

LossBasedState SendSideBandwidthEstimation::loss_based_state() const {
  return loss_based_state_;
}

bool SendSideBandwidthEstimation::IsRttAboveLimit() const {
  return rtt_backoff_.IsRttAboveLimit();
}

DataRate SendSideBandwidthEstimation::GetEstimatedLinkCapacity() const {
  return link_capacity_.estimate();
}

void SendSideBandwidthEstimation::UpdateReceiverEstimate(Timestamp at_time,
                                                         DataRate bandwidth) {
  // TODO(srte): Ensure caller passes PlusInfinity, not zero, to represent no
  // limitation.
  receiver_limit_ = bandwidth.IsZero() ? DataRate::PlusInfinity() : bandwidth;
  ApplyTargetLimits(at_time);
}

void SendSideBandwidthEstimation::UpdateDelayBasedEstimate(Timestamp at_time,
                                                           DataRate bitrate) {
  link_capacity_.UpdateDelayBasedEstimate(at_time, bitrate);
  // TODO(srte): Ensure caller passes PlusInfinity, not zero, to represent no
  // limitation.
  delay_based_limit_ = bitrate.IsZero() ? DataRate::PlusInfinity() : bitrate;
  ApplyTargetLimits(at_time);
}

void SendSideBandwidthEstimation::SetAcknowledgedRate(
    absl::optional<DataRate> acknowledged_rate,
    Timestamp at_time) {
  acknowledged_rate_ = acknowledged_rate;
  if (!acknowledged_rate.has_value()) {
    return;
  }
  if (LossBasedBandwidthEstimatorV1Enabled()) {
    loss_based_bandwidth_estimator_v1_.UpdateAcknowledgedBitrate(
        *acknowledged_rate, at_time);
  }
  if (LossBasedBandwidthEstimatorV2Enabled()) {
    loss_based_bandwidth_estimator_v2_->SetAcknowledgedBitrate(
        *acknowledged_rate);
  }
}

void SendSideBandwidthEstimation::UpdateLossBasedEstimator(
    const TransportPacketsFeedback& report,
    BandwidthUsage delay_detector_state,
    absl::optional<DataRate> probe_bitrate,
    bool in_alr) {
  if (LossBasedBandwidthEstimatorV1Enabled()) {
    loss_based_bandwidth_estimator_v1_.UpdateLossStatistics(
        report.packet_feedbacks, report.feedback_time);
  }
  if (LossBasedBandwidthEstimatorV2Enabled()) {
    loss_based_bandwidth_estimator_v2_->UpdateBandwidthEstimate(
        report.packet_feedbacks, delay_based_limit_, in_alr);
    UpdateEstimate(report.feedback_time);
  }
}

void SendSideBandwidthEstimation::UpdatePacketsLost(int64_t packets_lost,
                                                    int64_t number_of_packets,
                                                    Timestamp at_time) {
  last_loss_feedback_ = at_time;
  if (first_report_time_.IsInfinite())
    first_report_time_ = at_time;

  // Check sequence number diff and weight loss report
  if (number_of_packets > 0) {
    int64_t expected =
        expected_packets_since_last_loss_update_ + number_of_packets;

    // Don't generate a loss rate until it can be based on enough packets.
    if (expected < kLimitNumPackets) {
      // Accumulate reports.
      expected_packets_since_last_loss_update_ = expected;
      lost_packets_since_last_loss_update_ += packets_lost;
      return;
    }

    has_decreased_since_last_fraction_loss_ = false;
    int64_t lost_q8 =
        std::max<int64_t>(lost_packets_since_last_loss_update_ + packets_lost,
                          0)
        << 8;
    last_fraction_loss_ = std::min<int>(lost_q8 / expected, 255);

    // Reset accumulators.
    lost_packets_since_last_loss_update_ = 0;
    expected_packets_since_last_loss_update_ = 0;
    last_loss_packet_report_ = at_time;
    UpdateEstimate(at_time);
  }

  UpdateUmaStatsPacketsLost(at_time, packets_lost);
}

void SendSideBandwidthEstimation::UpdateUmaStatsPacketsLost(Timestamp at_time,
                                                            int packets_lost) {
  DataRate bitrate_kbps =
      DataRate::KilobitsPerSec((current_target_.bps() + 500) / 1000);
  for (size_t i = 0; i < kNumUmaRampupMetrics; ++i) {
    if (!rampup_uma_stats_updated_[i] &&
        bitrate_kbps.kbps() >= kUmaRampupMetrics[i].bitrate_kbps) {
      RTC_HISTOGRAMS_COUNTS_100000(i, kUmaRampupMetrics[i].metric_name,
                                   (at_time - first_report_time_).ms());
      rampup_uma_stats_updated_[i] = true;
    }
  }
  if (IsInStartPhase(at_time)) {
    initially_lost_packets_ += packets_lost;
  } else if (uma_update_state_ == kNoUpdate) {
    uma_update_state_ = kFirstDone;
    bitrate_at_2_seconds_ = bitrate_kbps;
    RTC_HISTOGRAM_COUNTS("WebRTC.BWE.InitiallyLostPackets",
                         initially_lost_packets_, 0, 100, 50);
    RTC_HISTOGRAM_COUNTS("WebRTC.BWE.InitialBandwidthEstimate",
                         bitrate_at_2_seconds_.kbps(), 0, 2000, 50);
  } else if (uma_update_state_ == kFirstDone &&
             at_time - first_report_time_ >= kBweConverganceTime) {
    uma_update_state_ = kDone;
    int bitrate_diff_kbps = std::max(
        bitrate_at_2_seconds_.kbps<int>() - bitrate_kbps.kbps<int>(), 0);
    RTC_HISTOGRAM_COUNTS("WebRTC.BWE.InitialVsConvergedDiff", bitrate_diff_kbps,
                         0, 2000, 50);
  }
}

void SendSideBandwidthEstimation::UpdateRtt(TimeDelta rtt, Timestamp at_time) {
  // Update RTT if we were able to compute an RTT based on this RTCP.
  // FlexFEC doesn't send RTCP SR, which means we won't be able to compute RTT.
  if (rtt > TimeDelta::Zero())
    last_round_trip_time_ = rtt;

  if (!IsInStartPhase(at_time) && uma_rtt_state_ == kNoUpdate) {
    uma_rtt_state_ = kDone;
    RTC_HISTOGRAM_COUNTS("WebRTC.BWE.InitialRtt", rtt.ms<int>(), 0, 2000, 50);
  }
}

void SendSideBandwidthEstimation::UpdateEstimate(Timestamp at_time) {
  if (rtt_backoff_.IsRttAboveLimit()) {
    if (at_time - time_last_decrease_ >= rtt_backoff_.drop_interval_ &&
        current_target_ > rtt_backoff_.bandwidth_floor_) {
      time_last_decrease_ = at_time;
      DataRate new_bitrate =
          std::max(current_target_ * rtt_backoff_.drop_fraction_,
                   rtt_backoff_.bandwidth_floor_.Get());
      link_capacity_.OnRttBackoff(new_bitrate, at_time);
      UpdateTargetBitrate(new_bitrate, at_time);
      return;
    }
    // TODO(srte): This is likely redundant in most cases.
    ApplyTargetLimits(at_time);
    return;
  }

  // We trust the REMB and/or delay-based estimate during the first 2 seconds if
  // we haven't had any packet loss reported, to allow startup bitrate probing.
  if (last_fraction_loss_ == 0 && IsInStartPhase(at_time) &&
      !loss_based_bandwidth_estimator_v2_->ReadyToUseInStartPhase()) {
    DataRate new_bitrate = current_target_;
    // TODO(srte): We should not allow the new_bitrate to be larger than the
    // receiver limit here.
    if (receiver_limit_.IsFinite())
      new_bitrate = std::max(receiver_limit_, new_bitrate);
    if (delay_based_limit_.IsFinite())
      new_bitrate = std::max(delay_based_limit_, new_bitrate);
    if (LossBasedBandwidthEstimatorV1Enabled()) {
      loss_based_bandwidth_estimator_v1_.Initialize(new_bitrate);
    }

    if (new_bitrate != current_target_) {
      min_bitrate_history_.clear();
      if (LossBasedBandwidthEstimatorV1Enabled()) {
        min_bitrate_history_.push_back(std::make_pair(at_time, new_bitrate));
      } else {
        min_bitrate_history_.push_back(
            std::make_pair(at_time, current_target_));
      }
      UpdateTargetBitrate(new_bitrate, at_time);
      return;
    }
  }
  UpdateMinHistory(at_time);
  if (last_loss_packet_report_.IsInfinite()) {
    // No feedback received.
    // TODO(srte): This is likely redundant in most cases.
    ApplyTargetLimits(at_time);
    return;
  }

  if (LossBasedBandwidthEstimatorV1ReadyForUse()) {
    DataRate new_bitrate = loss_based_bandwidth_estimator_v1_.Update(
        at_time, min_bitrate_history_.front().second, delay_based_limit_,
        last_round_trip_time_);
    UpdateTargetBitrate(new_bitrate, at_time);
    return;
  }

  if (LossBasedBandwidthEstimatorV2ReadyForUse()) {
    LossBasedBweV2::Result result =
        loss_based_bandwidth_estimator_v2_->GetLossBasedResult();
    loss_based_state_ = result.state;
    UpdateTargetBitrate(result.bandwidth_estimate, at_time);
    return;
  }

  TimeDelta time_since_loss_packet_report = at_time - last_loss_packet_report_;
  if (time_since_loss_packet_report < 1.2 * kMaxRtcpFeedbackInterval) {
    // We only care about loss above a given bitrate threshold.
    float loss = last_fraction_loss_ / 256.0f;
    // We only make decisions based on loss when the bitrate is above a
    // threshold. This is a crude way of handling loss which is uncorrelated
    // to congestion.
    if (current_target_ < bitrate_threshold_ || loss <= low_loss_threshold_) {
      // Loss < 2%: Increase rate by 8% of the min bitrate in the last
      // kBweIncreaseInterval.
      // Note that by remembering the bitrate over the last second one can
      // rampup up one second faster than if only allowed to start ramping
      // at 8% per second rate now. E.g.:
      //   If sending a constant 100kbps it can rampup immediately to 108kbps
      //   whenever a receiver report is received with lower packet loss.
      //   If instead one would do: current_bitrate_ *= 1.08^(delta time),
      //   it would take over one second since the lower packet loss to achieve
      //   108kbps.
      DataRate new_bitrate = DataRate::BitsPerSec(
          min_bitrate_history_.front().second.bps() * 1.08 + 0.5);

      // Add 1 kbps extra, just to make sure that we do not get stuck
      // (gives a little extra increase at low rates, negligible at higher
      // rates).
      new_bitrate += DataRate::BitsPerSec(1000);
      UpdateTargetBitrate(new_bitrate, at_time);
      return;
    } else if (current_target_ > bitrate_threshold_) {
      if (loss <= high_loss_threshold_) {
        // Loss between 2% - 10%: Do nothing.
      } else {
        // Loss > 10%: Limit the rate decreases to once a kBweDecreaseInterval
        // + rtt.
        if (!has_decreased_since_last_fraction_loss_ &&
            (at_time - time_last_decrease_) >=
                (kBweDecreaseInterval + last_round_trip_time_)) {
          time_last_decrease_ = at_time;

          // Reduce rate:
          //   newRate = rate * (1 - 0.5*lossRate);
          //   where packetLoss = 256*lossRate;
          DataRate new_bitrate = DataRate::BitsPerSec(
              (current_target_.bps() *
               static_cast<double>(512 - last_fraction_loss_)) /
              512.0);
          has_decreased_since_last_fraction_loss_ = true;
          UpdateTargetBitrate(new_bitrate, at_time);
          return;
        }
      }
    }
  }
  // TODO(srte): This is likely redundant in most cases.
  ApplyTargetLimits(at_time);
}

void SendSideBandwidthEstimation::UpdatePropagationRtt(
    Timestamp at_time,
    TimeDelta propagation_rtt) {
  rtt_backoff_.UpdatePropagationRtt(at_time, propagation_rtt);
}

void SendSideBandwidthEstimation::OnSentPacket(const SentPacket& sent_packet) {
  // Only feedback-triggering packets will be reported here.
  rtt_backoff_.last_packet_sent_ = sent_packet.send_time;
}

bool SendSideBandwidthEstimation::IsInStartPhase(Timestamp at_time) const {
  return first_report_time_.IsInfinite() ||
         at_time - first_report_time_ < kStartPhase;
}

void SendSideBandwidthEstimation::UpdateMinHistory(Timestamp at_time) {
  // Remove old data points from history.
  // Since history precision is in ms, add one so it is able to increase
  // bitrate if it is off by as little as 0.5ms.
  while (!min_bitrate_history_.empty() &&
         at_time - min_bitrate_history_.front().first + TimeDelta::Millis(1) >
             kBweIncreaseInterval) {
    min_bitrate_history_.pop_front();
  }

  // Typical minimum sliding-window algorithm: Pop values higher than current
  // bitrate before pushing it.
  while (!min_bitrate_history_.empty() &&
         current_target_ <= min_bitrate_history_.back().second) {
    min_bitrate_history_.pop_back();
  }

  min_bitrate_history_.push_back(std::make_pair(at_time, current_target_));
}

DataRate SendSideBandwidthEstimation::GetUpperLimit() const {
  DataRate upper_limit = delay_based_limit_;
  if (disable_receiver_limit_caps_only_)
    upper_limit = std::min(upper_limit, receiver_limit_);
  return std::min(upper_limit, max_bitrate_configured_);
}

void SendSideBandwidthEstimation::MaybeLogLowBitrateWarning(DataRate bitrate,
                                                            Timestamp at_time) {
  if (at_time - last_low_bitrate_log_ > kLowBitrateLogPeriod) {
    RTC_LOG(LS_WARNING) << "Estimated available bandwidth " << ToString(bitrate)
                        << " is below configured min bitrate "
                        << ToString(min_bitrate_configured_) << ".";
    last_low_bitrate_log_ = at_time;
  }
}

void SendSideBandwidthEstimation::MaybeLogLossBasedEvent(Timestamp at_time) {
  if (current_target_ != last_logged_target_ ||
      last_fraction_loss_ != last_logged_fraction_loss_ ||
      at_time - last_rtc_event_log_ > kRtcEventLogPeriod) {
    event_log_->Log(std::make_unique<RtcEventBweUpdateLossBased>(
        current_target_.bps(), last_fraction_loss_,
        expected_packets_since_last_loss_update_));
    last_logged_fraction_loss_ = last_fraction_loss_;
    last_logged_target_ = current_target_;
    last_rtc_event_log_ = at_time;
  }
}

void SendSideBandwidthEstimation::UpdateTargetBitrate(DataRate new_bitrate,
                                                      Timestamp at_time) {
  new_bitrate = std::min(new_bitrate, GetUpperLimit());
  if (new_bitrate < min_bitrate_configured_) {
    MaybeLogLowBitrateWarning(new_bitrate, at_time);
    new_bitrate = min_bitrate_configured_;
  }
  current_target_ = new_bitrate;
  MaybeLogLossBasedEvent(at_time);
  link_capacity_.OnRateUpdate(acknowledged_rate_, current_target_, at_time);
}

void SendSideBandwidthEstimation::ApplyTargetLimits(Timestamp at_time) {
  UpdateTargetBitrate(current_target_, at_time);
}

bool SendSideBandwidthEstimation::LossBasedBandwidthEstimatorV1Enabled() const {
  return loss_based_bandwidth_estimator_v1_.Enabled() &&
         !LossBasedBandwidthEstimatorV2Enabled();
}

bool SendSideBandwidthEstimation::LossBasedBandwidthEstimatorV1ReadyForUse()
    const {
  return LossBasedBandwidthEstimatorV1Enabled() &&
         loss_based_bandwidth_estimator_v1_.InUse();
}

bool SendSideBandwidthEstimation::LossBasedBandwidthEstimatorV2Enabled() const {
  return loss_based_bandwidth_estimator_v2_->IsEnabled();
}

bool SendSideBandwidthEstimation::LossBasedBandwidthEstimatorV2ReadyForUse()
    const {
  return loss_based_bandwidth_estimator_v2_->IsReady();
}

bool SendSideBandwidthEstimation::PaceAtLossBasedEstimate() const {
  return LossBasedBandwidthEstimatorV2ReadyForUse() &&
         loss_based_bandwidth_estimator_v2_->PaceAtLossBasedEstimate();
}

}  // namespace webrtc
