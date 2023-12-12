/*
 *  Copyright 2021 The WebRTC project authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/congestion_controller/goog_cc/loss_based_bwe_v2.h"

#include <algorithm>
#include <cmath>
#include <complex>
#include <cstddef>
#include <cstdlib>
#include <limits>
#include <utility>
#include <vector>

#include "absl/algorithm/container.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/field_trials_view.h"
#include "api/network_state_predictor.h"
#include "api/transport/network_types.h"
#include "api/units/data_rate.h"
#include "api/units/data_size.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "modules/remote_bitrate_estimator/include/bwe_defines.h"
#include "rtc_base/experiments/field_trial_list.h"
#include "rtc_base/experiments/field_trial_parser.h"
#include "rtc_base/logging.h"

namespace webrtc {

namespace {

bool IsValid(DataRate datarate) {
  return datarate.IsFinite();
}

bool IsValid(Timestamp timestamp) {
  return timestamp.IsFinite();
}

struct PacketResultsSummary {
  int num_packets = 0;
  int num_lost_packets = 0;
  DataSize total_size = DataSize::Zero();
  Timestamp first_send_time = Timestamp::PlusInfinity();
  Timestamp last_send_time = Timestamp::MinusInfinity();
};

// Returns a `PacketResultsSummary` where `first_send_time` is `PlusInfinity,
// and `last_send_time` is `MinusInfinity`, if `packet_results` is empty.
PacketResultsSummary GetPacketResultsSummary(
    rtc::ArrayView<const PacketResult> packet_results) {
  PacketResultsSummary packet_results_summary;

  packet_results_summary.num_packets = packet_results.size();
  for (const PacketResult& packet : packet_results) {
    if (!packet.IsReceived()) {
      packet_results_summary.num_lost_packets++;
    }
    packet_results_summary.total_size += packet.sent_packet.size;
    packet_results_summary.first_send_time = std::min(
        packet_results_summary.first_send_time, packet.sent_packet.send_time);
    packet_results_summary.last_send_time = std::max(
        packet_results_summary.last_send_time, packet.sent_packet.send_time);
  }

  return packet_results_summary;
}

double GetLossProbability(double inherent_loss,
                          DataRate loss_limited_bandwidth,
                          DataRate sending_rate) {
  if (inherent_loss < 0.0 || inherent_loss > 1.0) {
    RTC_LOG(LS_WARNING) << "The inherent loss must be in [0,1]: "
                        << inherent_loss;
    inherent_loss = std::min(std::max(inherent_loss, 0.0), 1.0);
  }
  if (!sending_rate.IsFinite()) {
    RTC_LOG(LS_WARNING) << "The sending rate must be finite: "
                        << ToString(sending_rate);
  }
  if (!loss_limited_bandwidth.IsFinite()) {
    RTC_LOG(LS_WARNING) << "The loss limited bandwidth must be finite: "
                        << ToString(loss_limited_bandwidth);
  }

  double loss_probability = inherent_loss;
  if (IsValid(sending_rate) && IsValid(loss_limited_bandwidth) &&
      (sending_rate > loss_limited_bandwidth)) {
    loss_probability += (1 - inherent_loss) *
                        (sending_rate - loss_limited_bandwidth) / sending_rate;
  }
  return std::min(std::max(loss_probability, 1.0e-6), 1.0 - 1.0e-6);
}

}  // namespace

LossBasedBweV2::LossBasedBweV2(const FieldTrialsView* key_value_config)
    : config_(CreateConfig(key_value_config)) {
  if (!config_.has_value()) {
    RTC_LOG(LS_VERBOSE) << "The configuration does not specify that the "
                           "estimator should be enabled, disabling it.";
    return;
  }
  if (!IsConfigValid()) {
    RTC_LOG(LS_WARNING)
        << "The configuration is not valid, disabling the estimator.";
    config_.reset();
    return;
  }

  current_estimate_.inherent_loss = config_->initial_inherent_loss_estimate;
  observations_.resize(config_->observation_window_size);
  temporal_weights_.resize(config_->observation_window_size);
  instant_upper_bound_temporal_weights_.resize(
      config_->observation_window_size);
  CalculateTemporalWeights();
}

bool LossBasedBweV2::IsEnabled() const {
  return config_.has_value();
}

bool LossBasedBweV2::IsReady() const {
  return IsEnabled() && IsValid(current_estimate_.loss_limited_bandwidth) &&
         num_observations_ > 0;
}

DataRate LossBasedBweV2::GetBandwidthEstimate(
    DataRate delay_based_limit) const {
  if (!IsReady()) {
    if (!IsEnabled()) {
      RTC_LOG(LS_WARNING)
          << "The estimator must be enabled before it can be used.";
    } else {
      if (!IsValid(current_estimate_.loss_limited_bandwidth)) {
        RTC_LOG(LS_WARNING)
            << "The estimator must be initialized before it can be used.";
      }
      if (num_observations_ <= 0) {
        RTC_LOG(LS_WARNING) << "The estimator must receive enough loss "
                               "statistics before it can be used.";
      }
    }
    return IsValid(delay_based_limit) ? delay_based_limit
                                      : DataRate::PlusInfinity();
  }

  if (delay_based_limit.IsFinite()) {
    return std::min({current_estimate_.loss_limited_bandwidth,
                     GetInstantUpperBound(), delay_based_limit});
  } else {
    return std::min(current_estimate_.loss_limited_bandwidth,
                    GetInstantUpperBound());
  }
}

void LossBasedBweV2::SetAcknowledgedBitrate(DataRate acknowledged_bitrate) {
  if (IsValid(acknowledged_bitrate)) {
    acknowledged_bitrate_ = acknowledged_bitrate;
  } else {
    RTC_LOG(LS_WARNING) << "The acknowledged bitrate must be finite: "
                        << ToString(acknowledged_bitrate);
  }
}

void LossBasedBweV2::SetBandwidthEstimate(DataRate bandwidth_estimate) {
  if (IsValid(bandwidth_estimate)) {
    current_estimate_.loss_limited_bandwidth = bandwidth_estimate;
  } else {
    RTC_LOG(LS_WARNING) << "The bandwidth estimate must be finite: "
                        << ToString(bandwidth_estimate);
  }
}

void LossBasedBweV2::SetMinBitrate(DataRate min_bitrate) {
  if (IsValid(min_bitrate)) {
    min_bitrate_ = min_bitrate;
  } else {
    RTC_LOG(LS_WARNING) << "The min bitrate must be finite: "
                        << ToString(min_bitrate);
  }
}

void LossBasedBweV2::UpdateBandwidthEstimate(
    rtc::ArrayView<const PacketResult> packet_results,
    DataRate delay_based_estimate,
    BandwidthUsage delay_detector_state) {
  if (!IsEnabled()) {
    RTC_LOG(LS_WARNING)
        << "The estimator must be enabled before it can be used.";
    return;
  }
  if (packet_results.empty()) {
    RTC_LOG(LS_VERBOSE)
        << "The estimate cannot be updated without any loss statistics.";
    return;
  }

  if (!PushBackObservation(packet_results, delay_detector_state)) {
    return;
  }

  if (!IsValid(current_estimate_.loss_limited_bandwidth)) {
    RTC_LOG(LS_VERBOSE)
        << "The estimator must be initialized before it can be used.";
    return;
  }

  ChannelParameters best_candidate = current_estimate_;
  double objective_max = std::numeric_limits<double>::lowest();
  for (ChannelParameters candidate : GetCandidates(delay_based_estimate)) {
    NewtonsMethodUpdate(candidate);

    const double candidate_objective = GetObjective(candidate);
    if (candidate_objective > objective_max) {
      objective_max = candidate_objective;
      best_candidate = candidate;
    }
  }
  if (best_candidate.loss_limited_bandwidth <
      current_estimate_.loss_limited_bandwidth) {
    last_time_estimate_reduced_ = last_send_time_most_recent_observation_;
  }

  // Do not increase the estimate if the average loss is greater than current
  // inherent loss.
  if (GetAverageReportedLossRatio() > best_candidate.inherent_loss &&
      config_->not_increase_if_inherent_loss_less_than_average_loss &&
      current_estimate_.loss_limited_bandwidth <
          best_candidate.loss_limited_bandwidth) {
    best_candidate.loss_limited_bandwidth =
        current_estimate_.loss_limited_bandwidth;
  }

  // Bound the estimate increase if:
  // 1. The estimate is limited due to loss, and
  // 2. The estimate has been increased for less than `delayed_increase_window`
  // ago, and
  // 3. The best candidate is greater than bandwidth_limit_in_current_window.
  if (limited_due_to_loss_candidate_ &&
      recovering_after_loss_timestamp_.IsFinite() &&
      recovering_after_loss_timestamp_ + config_->delayed_increase_window >
          last_send_time_most_recent_observation_ &&
      best_candidate.loss_limited_bandwidth >
          bandwidth_limit_in_current_window_) {
    best_candidate.loss_limited_bandwidth = bandwidth_limit_in_current_window_;
  }
  limited_due_to_loss_candidate_ =
      delay_based_estimate.IsFinite() &&
      best_candidate.loss_limited_bandwidth < delay_based_estimate;

  if (limited_due_to_loss_candidate_ &&
      (recovering_after_loss_timestamp_.IsInfinite() ||
       recovering_after_loss_timestamp_ + config_->delayed_increase_window <
           last_send_time_most_recent_observation_)) {
    bandwidth_limit_in_current_window_ = std::max(
        kCongestionControllerMinBitrate,
        best_candidate.loss_limited_bandwidth * config_->max_increase_factor);
    recovering_after_loss_timestamp_ = last_send_time_most_recent_observation_;
  }

  current_estimate_ = best_candidate;
}

// Returns a `LossBasedBweV2::Config` iff the `key_value_config` specifies a
// configuration for the `LossBasedBweV2` which is explicitly enabled.
absl::optional<LossBasedBweV2::Config> LossBasedBweV2::CreateConfig(
    const FieldTrialsView* key_value_config) {
  FieldTrialParameter<bool> enabled("Enabled", false);
  FieldTrialParameter<double> bandwidth_rampup_upper_bound_factor(
      "BwRampupUpperBoundFactor", 1.1);
  FieldTrialParameter<double> rampup_acceleration_max_factor(
      "BwRampupAccelMaxFactor", 0.0);
  FieldTrialParameter<TimeDelta> rampup_acceleration_maxout_time(
      "BwRampupAccelMaxoutTime", TimeDelta::Seconds(60));
  FieldTrialList<double> candidate_factors("CandidateFactors",
                                           {1.05, 1.0, 0.95});
  FieldTrialParameter<double> higher_bandwidth_bias_factor("HigherBwBiasFactor",
                                                           0.00001);
  FieldTrialParameter<double> higher_log_bandwidth_bias_factor(
      "HigherLogBwBiasFactor", 0.001);
  FieldTrialParameter<double> inherent_loss_lower_bound(
      "InherentLossLowerBound", 1.0e-3);
  FieldTrialParameter<double> loss_threshold_of_high_bandwidth_preference(
      "LossThresholdOfHighBandwidthPreference", 0.99);
  FieldTrialParameter<double> bandwidth_preference_smoothing_factor(
      "BandwidthPreferenceSmoothingFactor", 0.002);
  FieldTrialParameter<DataRate> inherent_loss_upper_bound_bandwidth_balance(
      "InherentLossUpperBoundBwBalance", DataRate::KilobitsPerSec(15.0));
  FieldTrialParameter<double> inherent_loss_upper_bound_offset(
      "InherentLossUpperBoundOffset", 0.05);
  FieldTrialParameter<double> initial_inherent_loss_estimate(
      "InitialInherentLossEstimate", 0.01);
  FieldTrialParameter<int> newton_iterations("NewtonIterations", 1);
  FieldTrialParameter<double> newton_step_size("NewtonStepSize", 0.5);
  FieldTrialParameter<bool> append_acknowledged_rate_candidate(
      "AckedRateCandidate", true);
  FieldTrialParameter<bool> append_delay_based_estimate_candidate(
      "DelayBasedCandidate", false);
  FieldTrialParameter<TimeDelta> observation_duration_lower_bound(
      "ObservationDurationLowerBound", TimeDelta::Seconds(1));
  FieldTrialParameter<int> observation_window_size("ObservationWindowSize", 20);
  FieldTrialParameter<double> sending_rate_smoothing_factor(
      "SendingRateSmoothingFactor", 0.0);
  FieldTrialParameter<double> instant_upper_bound_temporal_weight_factor(
      "InstantUpperBoundTemporalWeightFactor", 0.99);
  FieldTrialParameter<DataRate> instant_upper_bound_bandwidth_balance(
      "InstantUpperBoundBwBalance", DataRate::KilobitsPerSec(15.0));
  FieldTrialParameter<double> instant_upper_bound_loss_offset(
      "InstantUpperBoundLossOffset", 0.05);
  FieldTrialParameter<double> temporal_weight_factor("TemporalWeightFactor",
                                                     0.99);
  FieldTrialParameter<double> bandwidth_backoff_lower_bound_factor(
      "BwBackoffLowerBoundFactor", 1.0);
  FieldTrialParameter<bool> trendline_integration_enabled(
      "TrendlineIntegrationEnabled", false);
  FieldTrialParameter<int> trendline_observations_window_size(
      "TrendlineObservationsWindowSize", 20);
  FieldTrialParameter<double> max_increase_factor("MaxIncreaseFactor", 1000.0);
  FieldTrialParameter<TimeDelta> delayed_increase_window(
      "DelayedIncreaseWindow", TimeDelta::Millis(300));
  FieldTrialParameter<bool> use_acked_bitrate_only_when_overusing(
      "UseAckedBitrateOnlyWhenOverusing", false);
  FieldTrialParameter<bool>
      not_increase_if_inherent_loss_less_than_average_loss(
          "NotIncreaseIfInherentLossLessThanAverageLoss", false);
  FieldTrialParameter<double> high_loss_rate_threshold("HighLossRateThreshold",
                                                       1.0);
  FieldTrialParameter<DataRate> bandwidth_cap_at_high_loss_rate(
      "BandwidthCapAtHighLossRate", DataRate::KilobitsPerSec(500.0));
  FieldTrialParameter<double> slope_of_bwe_high_loss_func(
      "SlopeOfBweHighLossFunc", 1000);
  if (key_value_config) {
    ParseFieldTrial({&enabled,
                     &bandwidth_rampup_upper_bound_factor,
                     &rampup_acceleration_max_factor,
                     &rampup_acceleration_maxout_time,
                     &candidate_factors,
                     &higher_bandwidth_bias_factor,
                     &higher_log_bandwidth_bias_factor,
                     &inherent_loss_lower_bound,
                     &loss_threshold_of_high_bandwidth_preference,
                     &bandwidth_preference_smoothing_factor,
                     &inherent_loss_upper_bound_bandwidth_balance,
                     &inherent_loss_upper_bound_offset,
                     &initial_inherent_loss_estimate,
                     &newton_iterations,
                     &newton_step_size,
                     &append_acknowledged_rate_candidate,
                     &append_delay_based_estimate_candidate,
                     &observation_duration_lower_bound,
                     &observation_window_size,
                     &sending_rate_smoothing_factor,
                     &instant_upper_bound_temporal_weight_factor,
                     &instant_upper_bound_bandwidth_balance,
                     &instant_upper_bound_loss_offset,
                     &temporal_weight_factor,
                     &bandwidth_backoff_lower_bound_factor,
                     &trendline_integration_enabled,
                     &trendline_observations_window_size,
                     &max_increase_factor,
                     &delayed_increase_window,
                     &use_acked_bitrate_only_when_overusing,
                     &not_increase_if_inherent_loss_less_than_average_loss,
                     &high_loss_rate_threshold,
                     &bandwidth_cap_at_high_loss_rate,
                     &slope_of_bwe_high_loss_func},
                    key_value_config->Lookup("WebRTC-Bwe-LossBasedBweV2"));
  }

  absl::optional<Config> config;
  if (!enabled.Get()) {
    return config;
  }
  config.emplace();
  config->bandwidth_rampup_upper_bound_factor =
      bandwidth_rampup_upper_bound_factor.Get();
  config->rampup_acceleration_max_factor = rampup_acceleration_max_factor.Get();
  config->rampup_acceleration_maxout_time =
      rampup_acceleration_maxout_time.Get();
  config->candidate_factors = candidate_factors.Get();
  config->higher_bandwidth_bias_factor = higher_bandwidth_bias_factor.Get();
  config->higher_log_bandwidth_bias_factor =
      higher_log_bandwidth_bias_factor.Get();
  config->inherent_loss_lower_bound = inherent_loss_lower_bound.Get();
  config->loss_threshold_of_high_bandwidth_preference =
      loss_threshold_of_high_bandwidth_preference.Get();
  config->bandwidth_preference_smoothing_factor =
      bandwidth_preference_smoothing_factor.Get();
  config->inherent_loss_upper_bound_bandwidth_balance =
      inherent_loss_upper_bound_bandwidth_balance.Get();
  config->inherent_loss_upper_bound_offset =
      inherent_loss_upper_bound_offset.Get();
  config->initial_inherent_loss_estimate = initial_inherent_loss_estimate.Get();
  config->newton_iterations = newton_iterations.Get();
  config->newton_step_size = newton_step_size.Get();
  config->append_acknowledged_rate_candidate =
      append_acknowledged_rate_candidate.Get();
  config->append_delay_based_estimate_candidate =
      append_delay_based_estimate_candidate.Get();
  config->observation_duration_lower_bound =
      observation_duration_lower_bound.Get();
  config->observation_window_size = observation_window_size.Get();
  config->sending_rate_smoothing_factor = sending_rate_smoothing_factor.Get();
  config->instant_upper_bound_temporal_weight_factor =
      instant_upper_bound_temporal_weight_factor.Get();
  config->instant_upper_bound_bandwidth_balance =
      instant_upper_bound_bandwidth_balance.Get();
  config->instant_upper_bound_loss_offset =
      instant_upper_bound_loss_offset.Get();
  config->temporal_weight_factor = temporal_weight_factor.Get();
  config->bandwidth_backoff_lower_bound_factor =
      bandwidth_backoff_lower_bound_factor.Get();
  config->trendline_integration_enabled = trendline_integration_enabled.Get();
  config->trendline_observations_window_size =
      trendline_observations_window_size.Get();
  config->max_increase_factor = max_increase_factor.Get();
  config->delayed_increase_window = delayed_increase_window.Get();
  config->use_acked_bitrate_only_when_overusing =
      use_acked_bitrate_only_when_overusing.Get();
  config->not_increase_if_inherent_loss_less_than_average_loss =
      not_increase_if_inherent_loss_less_than_average_loss.Get();
  config->high_loss_rate_threshold = high_loss_rate_threshold.Get();
  config->bandwidth_cap_at_high_loss_rate =
      bandwidth_cap_at_high_loss_rate.Get();
  config->slope_of_bwe_high_loss_func = slope_of_bwe_high_loss_func.Get();
  return config;
}

bool LossBasedBweV2::IsConfigValid() const {
  if (!config_.has_value()) {
    return false;
  }

  bool valid = true;

  if (config_->bandwidth_rampup_upper_bound_factor <= 1.0) {
    RTC_LOG(LS_WARNING)
        << "The bandwidth rampup upper bound factor must be greater than 1: "
        << config_->bandwidth_rampup_upper_bound_factor;
    valid = false;
  }
  if (config_->rampup_acceleration_max_factor < 0.0) {
    RTC_LOG(LS_WARNING)
        << "The rampup acceleration max factor must be non-negative.: "
        << config_->rampup_acceleration_max_factor;
    valid = false;
  }
  if (config_->rampup_acceleration_maxout_time <= TimeDelta::Zero()) {
    RTC_LOG(LS_WARNING)
        << "The rampup acceleration maxout time must be above zero: "
        << config_->rampup_acceleration_maxout_time.seconds();
    valid = false;
  }
  for (double candidate_factor : config_->candidate_factors) {
    if (candidate_factor <= 0.0) {
      RTC_LOG(LS_WARNING) << "All candidate factors must be greater than zero: "
                          << candidate_factor;
      valid = false;
    }
  }

  // Ensure that the configuration allows generation of at least one candidate
  // other than the current estimate.
  if (!config_->append_acknowledged_rate_candidate &&
      !config_->append_delay_based_estimate_candidate &&
      !absl::c_any_of(config_->candidate_factors,
                      [](double cf) { return cf != 1.0; })) {
    RTC_LOG(LS_WARNING)
        << "The configuration does not allow generating candidates. Specify "
           "a candidate factor other than 1.0, allow the acknowledged rate "
           "to be a candidate, and/or allow the delay based estimate to be a "
           "candidate.";
    valid = false;
  }

  if (config_->higher_bandwidth_bias_factor < 0.0) {
    RTC_LOG(LS_WARNING)
        << "The higher bandwidth bias factor must be non-negative: "
        << config_->higher_bandwidth_bias_factor;
    valid = false;
  }
  if (config_->inherent_loss_lower_bound < 0.0 ||
      config_->inherent_loss_lower_bound >= 1.0) {
    RTC_LOG(LS_WARNING) << "The inherent loss lower bound must be in [0, 1): "
                        << config_->inherent_loss_lower_bound;
    valid = false;
  }
  if (config_->loss_threshold_of_high_bandwidth_preference < 0.0 ||
      config_->loss_threshold_of_high_bandwidth_preference >= 1.0) {
    RTC_LOG(LS_WARNING)
        << "The loss threshold of high bandwidth preference must be in [0, 1): "
        << config_->loss_threshold_of_high_bandwidth_preference;
    valid = false;
  }
  if (config_->bandwidth_preference_smoothing_factor <= 0.0 ||
      config_->bandwidth_preference_smoothing_factor > 1.0) {
    RTC_LOG(LS_WARNING)
        << "The bandwidth preference smoothing factor must be in (0, 1]: "
        << config_->bandwidth_preference_smoothing_factor;
    valid = false;
  }
  if (config_->inherent_loss_upper_bound_bandwidth_balance <=
      DataRate::Zero()) {
    RTC_LOG(LS_WARNING)
        << "The inherent loss upper bound bandwidth balance "
           "must be positive: "
        << ToString(config_->inherent_loss_upper_bound_bandwidth_balance);
    valid = false;
  }
  if (config_->inherent_loss_upper_bound_offset <
          config_->inherent_loss_lower_bound ||
      config_->inherent_loss_upper_bound_offset >= 1.0) {
    RTC_LOG(LS_WARNING) << "The inherent loss upper bound must be greater "
                           "than or equal to the inherent "
                           "loss lower bound, which is "
                        << config_->inherent_loss_lower_bound
                        << ", and less than 1: "
                        << config_->inherent_loss_upper_bound_offset;
    valid = false;
  }
  if (config_->initial_inherent_loss_estimate < 0.0 ||
      config_->initial_inherent_loss_estimate >= 1.0) {
    RTC_LOG(LS_WARNING)
        << "The initial inherent loss estimate must be in [0, 1): "
        << config_->initial_inherent_loss_estimate;
    valid = false;
  }
  if (config_->newton_iterations <= 0) {
    RTC_LOG(LS_WARNING) << "The number of Newton iterations must be positive: "
                        << config_->newton_iterations;
    valid = false;
  }
  if (config_->newton_step_size <= 0.0) {
    RTC_LOG(LS_WARNING) << "The Newton step size must be positive: "
                        << config_->newton_step_size;
    valid = false;
  }
  if (config_->observation_duration_lower_bound <= TimeDelta::Zero()) {
    RTC_LOG(LS_WARNING)
        << "The observation duration lower bound must be positive: "
        << ToString(config_->observation_duration_lower_bound);
    valid = false;
  }
  if (config_->observation_window_size < 2) {
    RTC_LOG(LS_WARNING) << "The observation window size must be at least 2: "
                        << config_->observation_window_size;
    valid = false;
  }
  if (config_->sending_rate_smoothing_factor < 0.0 ||
      config_->sending_rate_smoothing_factor >= 1.0) {
    RTC_LOG(LS_WARNING)
        << "The sending rate smoothing factor must be in [0, 1): "
        << config_->sending_rate_smoothing_factor;
    valid = false;
  }
  if (config_->instant_upper_bound_temporal_weight_factor <= 0.0 ||
      config_->instant_upper_bound_temporal_weight_factor > 1.0) {
    RTC_LOG(LS_WARNING)
        << "The instant upper bound temporal weight factor must be in (0, 1]"
        << config_->instant_upper_bound_temporal_weight_factor;
    valid = false;
  }
  if (config_->instant_upper_bound_bandwidth_balance <= DataRate::Zero()) {
    RTC_LOG(LS_WARNING)
        << "The instant upper bound bandwidth balance must be positive: "
        << ToString(config_->instant_upper_bound_bandwidth_balance);
    valid = false;
  }
  if (config_->instant_upper_bound_loss_offset < 0.0 ||
      config_->instant_upper_bound_loss_offset >= 1.0) {
    RTC_LOG(LS_WARNING)
        << "The instant upper bound loss offset must be in [0, 1): "
        << config_->instant_upper_bound_loss_offset;
    valid = false;
  }
  if (config_->temporal_weight_factor <= 0.0 ||
      config_->temporal_weight_factor > 1.0) {
    RTC_LOG(LS_WARNING) << "The temporal weight factor must be in (0, 1]: "
                        << config_->temporal_weight_factor;
    valid = false;
  }
  if (config_->bandwidth_backoff_lower_bound_factor > 1.0) {
    RTC_LOG(LS_WARNING)
        << "The bandwidth backoff lower bound factor must not be greater than "
           "1: "
        << config_->bandwidth_backoff_lower_bound_factor;
    valid = false;
  }
  if (config_->trendline_observations_window_size < 1) {
    RTC_LOG(LS_WARNING) << "The trendline window size must be at least 1: "
                        << config_->trendline_observations_window_size;
    valid = false;
  }
  if (config_->max_increase_factor <= 0.0) {
    RTC_LOG(LS_WARNING) << "The maximum increase factor must be positive: "
                        << config_->max_increase_factor;
    valid = false;
  }
  if (config_->delayed_increase_window <= TimeDelta::Zero()) {
    RTC_LOG(LS_WARNING) << "The delayed increase window must be positive: "
                        << config_->delayed_increase_window.ms();
    valid = false;
  }
  if (config_->high_loss_rate_threshold <= 0.0 ||
      config_->high_loss_rate_threshold > 1.0) {
    RTC_LOG(LS_WARNING) << "The high loss rate threshold must be in (0, 1]: "
                        << config_->high_loss_rate_threshold;
    valid = false;
  }
  return valid;
}

double LossBasedBweV2::GetAverageReportedLossRatio() const {
  if (num_observations_ <= 0) {
    return 0.0;
  }

  double num_packets = 0;
  double num_lost_packets = 0;
  for (const Observation& observation : observations_) {
    if (!observation.IsInitialized()) {
      continue;
    }

    double instant_temporal_weight =
        instant_upper_bound_temporal_weights_[(num_observations_ - 1) -
                                              observation.id];
    num_packets += instant_temporal_weight * observation.num_packets;
    num_lost_packets += instant_temporal_weight * observation.num_lost_packets;
  }

  return num_lost_packets / num_packets;
}

DataRate LossBasedBweV2::GetCandidateBandwidthUpperBound(
    DataRate delay_based_estimate) const {
  DataRate candidate_bandwidth_upper_bound = DataRate::PlusInfinity();
  if (limited_due_to_loss_candidate_) {
    candidate_bandwidth_upper_bound = bandwidth_limit_in_current_window_;
  }

  if (config_->trendline_integration_enabled) {
    candidate_bandwidth_upper_bound =
        std::min(GetInstantUpperBound(), candidate_bandwidth_upper_bound);
    if (IsValid(delay_based_estimate)) {
      candidate_bandwidth_upper_bound =
          std::min(delay_based_estimate, candidate_bandwidth_upper_bound);
    }
  }

  if (!acknowledged_bitrate_.has_value())
    return candidate_bandwidth_upper_bound;

  candidate_bandwidth_upper_bound =
      IsValid(candidate_bandwidth_upper_bound)
          ? std::min(candidate_bandwidth_upper_bound,
                     config_->bandwidth_rampup_upper_bound_factor *
                         (*acknowledged_bitrate_))
          : config_->bandwidth_rampup_upper_bound_factor *
                (*acknowledged_bitrate_);

  if (config_->rampup_acceleration_max_factor > 0.0) {
    const TimeDelta time_since_bandwidth_reduced = std::min(
        config_->rampup_acceleration_maxout_time,
        std::max(TimeDelta::Zero(), last_send_time_most_recent_observation_ -
                                        last_time_estimate_reduced_));
    const double rampup_acceleration = config_->rampup_acceleration_max_factor *
                                       time_since_bandwidth_reduced /
                                       config_->rampup_acceleration_maxout_time;

    candidate_bandwidth_upper_bound +=
        rampup_acceleration * (*acknowledged_bitrate_);
  }
  return candidate_bandwidth_upper_bound;
}

std::vector<LossBasedBweV2::ChannelParameters> LossBasedBweV2::GetCandidates(
    DataRate delay_based_estimate) const {
  std::vector<DataRate> bandwidths;
  bool can_increase_bitrate = TrendlineEsimateAllowBitrateIncrease();
  for (double candidate_factor : config_->candidate_factors) {
    if (!can_increase_bitrate && candidate_factor > 1.0) {
      continue;
    }
    bandwidths.push_back(candidate_factor *
                         current_estimate_.loss_limited_bandwidth);
  }

  if (acknowledged_bitrate_.has_value() &&
      config_->append_acknowledged_rate_candidate &&
      TrendlineEsimateAllowEmergencyBackoff()) {
    bandwidths.push_back(*acknowledged_bitrate_ *
                         config_->bandwidth_backoff_lower_bound_factor);
  }

  if (IsValid(delay_based_estimate) &&
      config_->append_delay_based_estimate_candidate) {
    if (can_increase_bitrate &&
        delay_based_estimate > current_estimate_.loss_limited_bandwidth) {
      bandwidths.push_back(delay_based_estimate);
    }
  }

  const DataRate candidate_bandwidth_upper_bound =
      GetCandidateBandwidthUpperBound(delay_based_estimate);

  std::vector<ChannelParameters> candidates;
  candidates.resize(bandwidths.size());
  for (size_t i = 0; i < bandwidths.size(); ++i) {
    ChannelParameters candidate = current_estimate_;
    if (config_->trendline_integration_enabled) {
      candidate.loss_limited_bandwidth =
          std::min(bandwidths[i], candidate_bandwidth_upper_bound);
    } else {
      candidate.loss_limited_bandwidth = std::min(
          bandwidths[i], std::max(current_estimate_.loss_limited_bandwidth,
                                  candidate_bandwidth_upper_bound));
    }
    candidate.inherent_loss = GetFeasibleInherentLoss(candidate);
    candidates[i] = candidate;
  }
  return candidates;
}

LossBasedBweV2::Derivatives LossBasedBweV2::GetDerivatives(
    const ChannelParameters& channel_parameters) const {
  Derivatives derivatives;

  for (const Observation& observation : observations_) {
    if (!observation.IsInitialized()) {
      continue;
    }

    double loss_probability = GetLossProbability(
        channel_parameters.inherent_loss,
        channel_parameters.loss_limited_bandwidth, observation.sending_rate);

    double temporal_weight =
        temporal_weights_[(num_observations_ - 1) - observation.id];

    derivatives.first +=
        temporal_weight *
        ((observation.num_lost_packets / loss_probability) -
         (observation.num_received_packets / (1.0 - loss_probability)));
    derivatives.second -=
        temporal_weight *
        ((observation.num_lost_packets / std::pow(loss_probability, 2)) +
         (observation.num_received_packets /
          std::pow(1.0 - loss_probability, 2)));
  }

  if (derivatives.second >= 0.0) {
    RTC_LOG(LS_ERROR) << "The second derivative is mathematically guaranteed "
                         "to be negative but is "
                      << derivatives.second << ".";
    derivatives.second = -1.0e-6;
  }

  return derivatives;
}

double LossBasedBweV2::GetFeasibleInherentLoss(
    const ChannelParameters& channel_parameters) const {
  return std::min(
      std::max(channel_parameters.inherent_loss,
               config_->inherent_loss_lower_bound),
      GetInherentLossUpperBound(channel_parameters.loss_limited_bandwidth));
}

double LossBasedBweV2::GetInherentLossUpperBound(DataRate bandwidth) const {
  if (bandwidth.IsZero()) {
    return 1.0;
  }

  double inherent_loss_upper_bound =
      config_->inherent_loss_upper_bound_offset +
      config_->inherent_loss_upper_bound_bandwidth_balance / bandwidth;
  return std::min(inherent_loss_upper_bound, 1.0);
}

double LossBasedBweV2::AdjustBiasFactor(double loss_rate,
                                        double bias_factor) const {
  return bias_factor *
         (config_->loss_threshold_of_high_bandwidth_preference - loss_rate) /
         (config_->bandwidth_preference_smoothing_factor +
          std::abs(config_->loss_threshold_of_high_bandwidth_preference -
                   loss_rate));
}

double LossBasedBweV2::GetHighBandwidthBias(DataRate bandwidth) const {
  if (IsValid(bandwidth)) {
    const double average_reported_loss_ratio = GetAverageReportedLossRatio();
    return AdjustBiasFactor(average_reported_loss_ratio,
                            config_->higher_bandwidth_bias_factor) *
               bandwidth.kbps() +
           AdjustBiasFactor(average_reported_loss_ratio,
                            config_->higher_log_bandwidth_bias_factor) *
               std::log(1.0 + bandwidth.kbps());
  }
  return 0.0;
}

double LossBasedBweV2::GetObjective(
    const ChannelParameters& channel_parameters) const {
  double objective = 0.0;

  const double high_bandwidth_bias =
      GetHighBandwidthBias(channel_parameters.loss_limited_bandwidth);

  for (const Observation& observation : observations_) {
    if (!observation.IsInitialized()) {
      continue;
    }

    double loss_probability = GetLossProbability(
        channel_parameters.inherent_loss,
        channel_parameters.loss_limited_bandwidth, observation.sending_rate);

    double temporal_weight =
        temporal_weights_[(num_observations_ - 1) - observation.id];

    objective +=
        temporal_weight *
        ((observation.num_lost_packets * std::log(loss_probability)) +
         (observation.num_received_packets * std::log(1.0 - loss_probability)));
    objective +=
        temporal_weight * high_bandwidth_bias * observation.num_packets;
  }

  return objective;
}

DataRate LossBasedBweV2::GetSendingRate(
    DataRate instantaneous_sending_rate) const {
  if (num_observations_ <= 0) {
    return instantaneous_sending_rate;
  }

  const int most_recent_observation_idx =
      (num_observations_ - 1) % config_->observation_window_size;
  const Observation& most_recent_observation =
      observations_[most_recent_observation_idx];
  DataRate sending_rate_previous_observation =
      most_recent_observation.sending_rate;

  return config_->sending_rate_smoothing_factor *
             sending_rate_previous_observation +
         (1.0 - config_->sending_rate_smoothing_factor) *
             instantaneous_sending_rate;
}

DataRate LossBasedBweV2::GetInstantUpperBound() const {
  return cached_instant_upper_bound_.value_or(DataRate::PlusInfinity());
}

void LossBasedBweV2::CalculateInstantUpperBound() {
  DataRate instant_limit = DataRate::PlusInfinity();
  const double average_reported_loss_ratio = GetAverageReportedLossRatio();
  if (average_reported_loss_ratio > config_->instant_upper_bound_loss_offset) {
    instant_limit = config_->instant_upper_bound_bandwidth_balance /
                    (average_reported_loss_ratio -
                     config_->instant_upper_bound_loss_offset);
    if (average_reported_loss_ratio > config_->high_loss_rate_threshold) {
      instant_limit = std::min(
          instant_limit, DataRate::KilobitsPerSec(std::max(
                             static_cast<double>(min_bitrate_.kbps()),
                             config_->bandwidth_cap_at_high_loss_rate.kbps() -
                                 config_->slope_of_bwe_high_loss_func *
                                     average_reported_loss_ratio)));
    }
  }

  cached_instant_upper_bound_ = instant_limit;
}

void LossBasedBweV2::CalculateTemporalWeights() {
  for (int i = 0; i < config_->observation_window_size; ++i) {
    temporal_weights_[i] = std::pow(config_->temporal_weight_factor, i);
    instant_upper_bound_temporal_weights_[i] =
        std::pow(config_->instant_upper_bound_temporal_weight_factor, i);
  }
}

void LossBasedBweV2::NewtonsMethodUpdate(
    ChannelParameters& channel_parameters) const {
  if (num_observations_ <= 0) {
    return;
  }

  for (int i = 0; i < config_->newton_iterations; ++i) {
    const Derivatives derivatives = GetDerivatives(channel_parameters);
    channel_parameters.inherent_loss -=
        config_->newton_step_size * derivatives.first / derivatives.second;
    channel_parameters.inherent_loss =
        GetFeasibleInherentLoss(channel_parameters);
  }
}

bool LossBasedBweV2::TrendlineEsimateAllowBitrateIncrease() const {
  if (!config_->trendline_integration_enabled) {
    return true;
  }

  for (const auto& detector_state : delay_detector_states_) {
    if (detector_state == BandwidthUsage::kBwOverusing ||
        detector_state == BandwidthUsage::kBwUnderusing) {
      return false;
    }
  }
  return true;
}

bool LossBasedBweV2::TrendlineEsimateAllowEmergencyBackoff() const {
  if (!config_->trendline_integration_enabled) {
    return true;
  }

  if (!config_->use_acked_bitrate_only_when_overusing) {
    return true;
  }

  for (const auto& detector_state : delay_detector_states_) {
    if (detector_state == BandwidthUsage::kBwOverusing) {
      return true;
    }
  }

  return false;
}

bool LossBasedBweV2::PushBackObservation(
    rtc::ArrayView<const PacketResult> packet_results,
    BandwidthUsage delay_detector_state) {
  delay_detector_states_.push_front(delay_detector_state);
  if (static_cast<int>(delay_detector_states_.size()) >
      config_->trendline_observations_window_size) {
    delay_detector_states_.pop_back();
  }

  if (packet_results.empty()) {
    return false;
  }

  PacketResultsSummary packet_results_summary =
      GetPacketResultsSummary(packet_results);

  partial_observation_.num_packets += packet_results_summary.num_packets;
  partial_observation_.num_lost_packets +=
      packet_results_summary.num_lost_packets;
  partial_observation_.size += packet_results_summary.total_size;

  // This is the first packet report we have received.
  if (!IsValid(last_send_time_most_recent_observation_)) {
    last_send_time_most_recent_observation_ =
        packet_results_summary.first_send_time;
  }

  const Timestamp last_send_time = packet_results_summary.last_send_time;
  const TimeDelta observation_duration =
      last_send_time - last_send_time_most_recent_observation_;
  // Too small to be meaningful.
  if (observation_duration <= TimeDelta::Zero() ||
      (observation_duration < config_->observation_duration_lower_bound &&
       (delay_detector_state != BandwidthUsage::kBwOverusing ||
        !config_->trendline_integration_enabled))) {
    return false;
  }

  last_send_time_most_recent_observation_ = last_send_time;

  Observation observation;
  observation.num_packets = partial_observation_.num_packets;
  observation.num_lost_packets = partial_observation_.num_lost_packets;
  observation.num_received_packets =
      observation.num_packets - observation.num_lost_packets;
  observation.sending_rate =
      GetSendingRate(partial_observation_.size / observation_duration);
  observation.id = num_observations_++;
  observations_[observation.id % config_->observation_window_size] =
      observation;

  partial_observation_ = PartialObservation();

  CalculateInstantUpperBound();
  return true;
}

}  // namespace webrtc
