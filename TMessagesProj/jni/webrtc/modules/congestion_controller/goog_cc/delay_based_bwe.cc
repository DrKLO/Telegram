/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/congestion_controller/goog_cc/delay_based_bwe.h"

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <memory>
#include <string>
#include <utility>

#include "absl/strings/match.h"
#include "api/rtc_event_log/rtc_event.h"
#include "api/rtc_event_log/rtc_event_log.h"
#include "logging/rtc_event_log/events/rtc_event_bwe_update_delay_based.h"
#include "modules/congestion_controller/goog_cc/trendline_estimator.h"
#include "modules/remote_bitrate_estimator/test/bwe_test_logging.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {
namespace {
constexpr TimeDelta kStreamTimeOut = TimeDelta::Seconds(2);
constexpr int kTimestampGroupLengthMs = 5;
constexpr int kAbsSendTimeFraction = 18;
constexpr int kAbsSendTimeInterArrivalUpshift = 8;
constexpr int kInterArrivalShift =
    kAbsSendTimeFraction + kAbsSendTimeInterArrivalUpshift;
constexpr int kTimestampGroupTicks =
    (kTimestampGroupLengthMs << kInterArrivalShift) / 1000;
constexpr double kTimestampToMs =
    1000.0 / static_cast<double>(1 << kInterArrivalShift);

// This ssrc is used to fulfill the current API but will be removed
// after the API has been changed.
constexpr uint32_t kFixedSsrc = 0;
}  // namespace

constexpr char BweIgnoreSmallPacketsSettings::kKey[];
constexpr char BweSeparateAudioPacketsSettings::kKey[];

BweIgnoreSmallPacketsSettings::BweIgnoreSmallPacketsSettings(
    const WebRtcKeyValueConfig* key_value_config) {
  Parser()->Parse(
      key_value_config->Lookup(BweIgnoreSmallPacketsSettings::kKey));
}

std::unique_ptr<StructParametersParser>
BweIgnoreSmallPacketsSettings::Parser() {
  return StructParametersParser::Create("smoothing", &smoothing_factor,     //
                                        "fraction_large", &fraction_large,  //
                                        "large", &large_threshold,          //
                                        "small", &small_threshold);
}

BweSeparateAudioPacketsSettings::BweSeparateAudioPacketsSettings(
    const WebRtcKeyValueConfig* key_value_config) {
  Parser()->Parse(
      key_value_config->Lookup(BweSeparateAudioPacketsSettings::kKey));
}

std::unique_ptr<StructParametersParser>
BweSeparateAudioPacketsSettings::Parser() {
  return StructParametersParser::Create(      //
      "enabled", &enabled,                    //
      "packet_threshold", &packet_threshold,  //
      "time_threshold", &time_threshold);
}

DelayBasedBwe::Result::Result()
    : updated(false),
      probe(false),
      target_bitrate(DataRate::Zero()),
      recovered_from_overuse(false),
      backoff_in_alr(false) {}

DelayBasedBwe::Result::Result(bool probe, DataRate target_bitrate)
    : updated(true),
      probe(probe),
      target_bitrate(target_bitrate),
      recovered_from_overuse(false),
      backoff_in_alr(false) {}

DelayBasedBwe::DelayBasedBwe(const WebRtcKeyValueConfig* key_value_config,
                             RtcEventLog* event_log,
                             NetworkStatePredictor* network_state_predictor)
    : event_log_(event_log),
      key_value_config_(key_value_config),
      ignore_small_(key_value_config),
      fraction_large_packets_(0.5),
      separate_audio_(key_value_config),
      audio_packets_since_last_video_(0),
      last_video_packet_recv_time_(Timestamp::MinusInfinity()),
      network_state_predictor_(network_state_predictor),
      video_inter_arrival_(),
      video_delay_detector_(
          new TrendlineEstimator(key_value_config_, network_state_predictor_)),
      audio_inter_arrival_(),
      audio_delay_detector_(
          new TrendlineEstimator(key_value_config_, network_state_predictor_)),
      active_delay_detector_(video_delay_detector_.get()),
      last_seen_packet_(Timestamp::MinusInfinity()),
      uma_recorded_(false),
      rate_control_(key_value_config, /*send_side=*/true),
      prev_bitrate_(DataRate::Zero()),
      has_once_detected_overuse_(false),
      prev_state_(BandwidthUsage::kBwNormal),
      alr_limited_backoff_enabled_(absl::StartsWith(
          key_value_config->Lookup("WebRTC-Bwe-AlrLimitedBackoff"),
          "Enabled")) {
  RTC_LOG(LS_INFO) << "Initialized DelayBasedBwe with small packet filtering "
                   << ignore_small_.Parser()->Encode()
                   << ", separate audio overuse detection"
                   << separate_audio_.Parser()->Encode()
                   << " and alr limited backoff "
                   << (alr_limited_backoff_enabled_ ? "enabled" : "disabled");
}

DelayBasedBwe::~DelayBasedBwe() {}

DelayBasedBwe::Result DelayBasedBwe::IncomingPacketFeedbackVector(
    const TransportPacketsFeedback& msg,
    absl::optional<DataRate> acked_bitrate,
    absl::optional<DataRate> probe_bitrate,
    absl::optional<NetworkStateEstimate> network_estimate,
    bool in_alr) {
  RTC_DCHECK_RUNS_SERIALIZED(&network_race_);

  auto packet_feedback_vector = msg.SortedByReceiveTime();
  // TODO(holmer): An empty feedback vector here likely means that
  // all acks were too late and that the send time history had
  // timed out. We should reduce the rate when this occurs.
  if (packet_feedback_vector.empty()) {
    RTC_LOG(LS_WARNING) << "Very late feedback received.";
    return DelayBasedBwe::Result();
  }

  if (!uma_recorded_) {
    RTC_HISTOGRAM_ENUMERATION(kBweTypeHistogram,
                              BweNames::kSendSideTransportSeqNum,
                              BweNames::kBweNamesMax);
    uma_recorded_ = true;
  }
  bool delayed_feedback = true;
  bool recovered_from_overuse = false;
  BandwidthUsage prev_detector_state = active_delay_detector_->State();
  for (const auto& packet_feedback : packet_feedback_vector) {
    delayed_feedback = false;
    IncomingPacketFeedback(packet_feedback, msg.feedback_time);
    if (prev_detector_state == BandwidthUsage::kBwUnderusing &&
        active_delay_detector_->State() == BandwidthUsage::kBwNormal) {
      recovered_from_overuse = true;
    }
    prev_detector_state = active_delay_detector_->State();
  }

  if (delayed_feedback) {
    // TODO(bugs.webrtc.org/10125): Design a better mechanism to safe-guard
    // against building very large network queues.
    return Result();
  }
  rate_control_.SetInApplicationLimitedRegion(in_alr);
  rate_control_.SetNetworkStateEstimate(network_estimate);
  return MaybeUpdateEstimate(acked_bitrate, probe_bitrate,
                             std::move(network_estimate),
                             recovered_from_overuse, in_alr, msg.feedback_time);
}

void DelayBasedBwe::IncomingPacketFeedback(const PacketResult& packet_feedback,
                                           Timestamp at_time) {
  // Reset if the stream has timed out.
  if (last_seen_packet_.IsInfinite() ||
      at_time - last_seen_packet_ > kStreamTimeOut) {
    video_inter_arrival_.reset(
        new InterArrival(kTimestampGroupTicks, kTimestampToMs, true));
    video_delay_detector_.reset(
        new TrendlineEstimator(key_value_config_, network_state_predictor_));
    audio_inter_arrival_.reset(
        new InterArrival(kTimestampGroupTicks, kTimestampToMs, true));
    audio_delay_detector_.reset(
        new TrendlineEstimator(key_value_config_, network_state_predictor_));
    active_delay_detector_ = video_delay_detector_.get();
  }
  last_seen_packet_ = at_time;

  // Ignore "small" packets if many/most packets in the call are "large". The
  // packet size may have a significant effect on the propagation delay,
  // especially at low bandwidths. Variations in packet size will then show up
  // as noise in the delay measurement. By default, we include all packets.
  DataSize packet_size = packet_feedback.sent_packet.size;
  if (!ignore_small_.small_threshold.IsZero()) {
    double is_large =
        static_cast<double>(packet_size >= ignore_small_.large_threshold);
    fraction_large_packets_ +=
        ignore_small_.smoothing_factor * (is_large - fraction_large_packets_);
    if (packet_size <= ignore_small_.small_threshold &&
        fraction_large_packets_ >= ignore_small_.fraction_large) {
      return;
    }
  }

  // As an alternative to ignoring small packets, we can separate audio and
  // video packets for overuse detection.
  InterArrival* inter_arrival_for_packet = video_inter_arrival_.get();
  DelayIncreaseDetectorInterface* delay_detector_for_packet =
      video_delay_detector_.get();
  if (separate_audio_.enabled) {
    if (packet_feedback.sent_packet.audio) {
      inter_arrival_for_packet = audio_inter_arrival_.get();
      delay_detector_for_packet = audio_delay_detector_.get();
      audio_packets_since_last_video_++;
      if (audio_packets_since_last_video_ > separate_audio_.packet_threshold &&
          packet_feedback.receive_time - last_video_packet_recv_time_ >
              separate_audio_.time_threshold) {
        active_delay_detector_ = audio_delay_detector_.get();
      }
    } else {
      audio_packets_since_last_video_ = 0;
      last_video_packet_recv_time_ =
          std::max(last_video_packet_recv_time_, packet_feedback.receive_time);
      active_delay_detector_ = video_delay_detector_.get();
    }
  }

  uint32_t send_time_24bits =
      static_cast<uint32_t>(
          ((static_cast<uint64_t>(packet_feedback.sent_packet.send_time.ms())
            << kAbsSendTimeFraction) +
           500) /
          1000) &
      0x00FFFFFF;
  // Shift up send time to use the full 32 bits that inter_arrival works with,
  // so wrapping works properly.
  uint32_t timestamp = send_time_24bits << kAbsSendTimeInterArrivalUpshift;

  uint32_t timestamp_delta = 0;
  int64_t recv_delta_ms = 0;
  int size_delta = 0;
  bool calculated_deltas = inter_arrival_for_packet->ComputeDeltas(
      timestamp, packet_feedback.receive_time.ms(), at_time.ms(),
      packet_size.bytes(), &timestamp_delta, &recv_delta_ms, &size_delta);
  double send_delta_ms = (1000.0 * timestamp_delta) / (1 << kInterArrivalShift);
  delay_detector_for_packet->Update(recv_delta_ms, send_delta_ms,
                                    packet_feedback.sent_packet.send_time.ms(),
                                    packet_feedback.receive_time.ms(),
                                    packet_size.bytes(), calculated_deltas);
}

DataRate DelayBasedBwe::TriggerOveruse(Timestamp at_time,
                                       absl::optional<DataRate> link_capacity) {
  RateControlInput input(BandwidthUsage::kBwOverusing, link_capacity);
  return rate_control_.Update(&input, at_time);
}

DelayBasedBwe::Result DelayBasedBwe::MaybeUpdateEstimate(
    absl::optional<DataRate> acked_bitrate,
    absl::optional<DataRate> probe_bitrate,
    absl::optional<NetworkStateEstimate> state_estimate,
    bool recovered_from_overuse,
    bool in_alr,
    Timestamp at_time) {
  Result result;

  // Currently overusing the bandwidth.
  if (active_delay_detector_->State() == BandwidthUsage::kBwOverusing) {
    if (has_once_detected_overuse_ && in_alr && alr_limited_backoff_enabled_) {
      if (rate_control_.TimeToReduceFurther(at_time, prev_bitrate_)) {
        result.updated =
            UpdateEstimate(at_time, prev_bitrate_, &result.target_bitrate);
        result.backoff_in_alr = true;
      }
    } else if (acked_bitrate &&
               rate_control_.TimeToReduceFurther(at_time, *acked_bitrate)) {
      result.updated =
          UpdateEstimate(at_time, acked_bitrate, &result.target_bitrate);
    } else if (!acked_bitrate && rate_control_.ValidEstimate() &&
               rate_control_.InitialTimeToReduceFurther(at_time)) {
      // Overusing before we have a measured acknowledged bitrate. Reduce send
      // rate by 50% every 200 ms.
      // TODO(tschumim): Improve this and/or the acknowledged bitrate estimator
      // so that we (almost) always have a bitrate estimate.
      rate_control_.SetEstimate(rate_control_.LatestEstimate() / 2, at_time);
      result.updated = true;
      result.probe = false;
      result.target_bitrate = rate_control_.LatestEstimate();
    }
    has_once_detected_overuse_ = true;
  } else {
    if (probe_bitrate) {
      result.probe = true;
      result.updated = true;
      result.target_bitrate = *probe_bitrate;
      rate_control_.SetEstimate(*probe_bitrate, at_time);
    } else {
      result.updated =
          UpdateEstimate(at_time, acked_bitrate, &result.target_bitrate);
      result.recovered_from_overuse = recovered_from_overuse;
    }
  }
  BandwidthUsage detector_state = active_delay_detector_->State();
  if ((result.updated && prev_bitrate_ != result.target_bitrate) ||
      detector_state != prev_state_) {
    DataRate bitrate = result.updated ? result.target_bitrate : prev_bitrate_;

    BWE_TEST_LOGGING_PLOT(1, "target_bitrate_bps", at_time.ms(), bitrate.bps());

    if (event_log_) {
      event_log_->Log(std::make_unique<RtcEventBweUpdateDelayBased>(
          bitrate.bps(), detector_state));
    }

    prev_bitrate_ = bitrate;
    prev_state_ = detector_state;
  }
  return result;
}

bool DelayBasedBwe::UpdateEstimate(Timestamp at_time,
                                   absl::optional<DataRate> acked_bitrate,
                                   DataRate* target_rate) {
  const RateControlInput input(active_delay_detector_->State(), acked_bitrate);
  *target_rate = rate_control_.Update(&input, at_time);
  return rate_control_.ValidEstimate();
}

void DelayBasedBwe::OnRttUpdate(TimeDelta avg_rtt) {
  rate_control_.SetRtt(avg_rtt);
}

bool DelayBasedBwe::LatestEstimate(std::vector<uint32_t>* ssrcs,
                                   DataRate* bitrate) const {
  // Currently accessed from both the process thread (see
  // ModuleRtpRtcpImpl::Process()) and the configuration thread (see
  // Call::GetStats()). Should in the future only be accessed from a single
  // thread.
  RTC_DCHECK(ssrcs);
  RTC_DCHECK(bitrate);
  if (!rate_control_.ValidEstimate())
    return false;

  *ssrcs = {kFixedSsrc};
  *bitrate = rate_control_.LatestEstimate();
  return true;
}

void DelayBasedBwe::SetStartBitrate(DataRate start_bitrate) {
  RTC_LOG(LS_INFO) << "BWE Setting start bitrate to: "
                   << ToString(start_bitrate);
  rate_control_.SetStartBitrate(start_bitrate);
}

void DelayBasedBwe::SetMinBitrate(DataRate min_bitrate) {
  // Called from both the configuration thread and the network thread. Shouldn't
  // be called from the network thread in the future.
  rate_control_.SetMinBitrate(min_bitrate);
}

TimeDelta DelayBasedBwe::GetExpectedBwePeriod() const {
  return rate_control_.GetExpectedBandwidthPeriod();
}

void DelayBasedBwe::SetAlrLimitedBackoffExperiment(bool enabled) {
  alr_limited_backoff_enabled_ = enabled;
}

}  // namespace webrtc
