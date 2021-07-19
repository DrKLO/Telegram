/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/pacing/bitrate_prober.h"

#include <algorithm>

#include "absl/memory/memory.h"
#include "api/rtc_event_log/rtc_event.h"
#include "api/rtc_event_log/rtc_event_log.h"
#include "logging/rtc_event_log/events/rtc_event_probe_cluster_created.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {

namespace {
// The min probe packet size is scaled with the bitrate we're probing at.
// This defines the max min probe packet size, meaning that on high bitrates
// we have a min probe packet size of 200 bytes.
constexpr DataSize kMinProbePacketSize = DataSize::Bytes(200);

constexpr TimeDelta kProbeClusterTimeout = TimeDelta::Seconds(5);

}  // namespace

BitrateProberConfig::BitrateProberConfig(
    const WebRtcKeyValueConfig* key_value_config)
    : min_probe_packets_sent("min_probe_packets_sent", 5),
      min_probe_delta("min_probe_delta", TimeDelta::Millis(1)),
      min_probe_duration("min_probe_duration", TimeDelta::Millis(15)),
      max_probe_delay("max_probe_delay", TimeDelta::Millis(10)),
      abort_delayed_probes("abort_delayed_probes", true) {
  ParseFieldTrial(
      {&min_probe_packets_sent, &min_probe_delta, &min_probe_duration,
       &max_probe_delay, &abort_delayed_probes},
      key_value_config->Lookup("WebRTC-Bwe-ProbingConfiguration"));
  ParseFieldTrial(
      {&min_probe_packets_sent, &min_probe_delta, &min_probe_duration,
       &max_probe_delay, &abort_delayed_probes},
      key_value_config->Lookup("WebRTC-Bwe-ProbingBehavior"));
}

BitrateProber::~BitrateProber() {
  RTC_HISTOGRAM_COUNTS_1000("WebRTC.BWE.Probing.TotalProbeClustersRequested",
                            total_probe_count_);
  RTC_HISTOGRAM_COUNTS_1000("WebRTC.BWE.Probing.TotalFailedProbeClusters",
                            total_failed_probe_count_);
}

BitrateProber::BitrateProber(const WebRtcKeyValueConfig& field_trials)
    : probing_state_(ProbingState::kDisabled),
      next_probe_time_(Timestamp::PlusInfinity()),
      total_probe_count_(0),
      total_failed_probe_count_(0),
      config_(&field_trials) {
  SetEnabled(true);
}

void BitrateProber::SetEnabled(bool enable) {
  if (enable) {
    if (probing_state_ == ProbingState::kDisabled) {
      probing_state_ = ProbingState::kInactive;
      RTC_LOG(LS_INFO) << "Bandwidth probing enabled, set to inactive";
    }
  } else {
    probing_state_ = ProbingState::kDisabled;
    RTC_LOG(LS_INFO) << "Bandwidth probing disabled";
  }
}

void BitrateProber::OnIncomingPacket(DataSize packet_size) {
  // Don't initialize probing unless we have something large enough to start
  // probing.
  if (probing_state_ == ProbingState::kInactive && !clusters_.empty() &&
      packet_size >= std::min(RecommendedMinProbeSize(), kMinProbePacketSize)) {
    // Send next probe right away.
    next_probe_time_ = Timestamp::MinusInfinity();
    probing_state_ = ProbingState::kActive;
  }
}

void BitrateProber::CreateProbeCluster(DataRate bitrate,
                                       Timestamp now,
                                       int cluster_id) {
  RTC_DCHECK(probing_state_ != ProbingState::kDisabled);
  RTC_DCHECK_GT(bitrate, DataRate::Zero());

  total_probe_count_++;
  while (!clusters_.empty() &&
         now - clusters_.front().created_at > kProbeClusterTimeout) {
    clusters_.pop();
    total_failed_probe_count_++;
  }

  ProbeCluster cluster;
  cluster.created_at = now;
  cluster.pace_info.probe_cluster_min_probes = config_.min_probe_packets_sent;
  cluster.pace_info.probe_cluster_min_bytes =
      (bitrate * config_.min_probe_duration.Get()).bytes();
  RTC_DCHECK_GE(cluster.pace_info.probe_cluster_min_bytes, 0);
  cluster.pace_info.send_bitrate_bps = bitrate.bps();
  cluster.pace_info.probe_cluster_id = cluster_id;
  clusters_.push(cluster);

  RTC_LOG(LS_INFO) << "Probe cluster (bitrate:min bytes:min packets): ("
                   << cluster.pace_info.send_bitrate_bps << ":"
                   << cluster.pace_info.probe_cluster_min_bytes << ":"
                   << cluster.pace_info.probe_cluster_min_probes << ")";
  // If we are already probing, continue to do so. Otherwise set it to
  // kInactive and wait for OnIncomingPacket to start the probing.
  if (probing_state_ != ProbingState::kActive)
    probing_state_ = ProbingState::kInactive;
}

Timestamp BitrateProber::NextProbeTime(Timestamp now) const {
  // Probing is not active or probing is already complete.
  if (probing_state_ != ProbingState::kActive || clusters_.empty()) {
    return Timestamp::PlusInfinity();
  }

  // Legacy behavior, just warn about late probe and return as if not probing.
  if (!config_.abort_delayed_probes && next_probe_time_.IsFinite() &&
      now - next_probe_time_ > config_.max_probe_delay.Get()) {
    RTC_DLOG(LS_WARNING) << "Probe delay too high"
                            " (next_ms:"
                         << next_probe_time_.ms() << ", now_ms: " << now.ms()
                         << ")";
    return Timestamp::PlusInfinity();
  }

  return next_probe_time_;
}

absl::optional<PacedPacketInfo> BitrateProber::CurrentCluster(Timestamp now) {
  if (clusters_.empty() || probing_state_ != ProbingState::kActive) {
    return absl::nullopt;
  }

  if (config_.abort_delayed_probes && next_probe_time_.IsFinite() &&
      now - next_probe_time_ > config_.max_probe_delay.Get()) {
    RTC_DLOG(LS_WARNING) << "Probe delay too high"
                            " (next_ms:"
                         << next_probe_time_.ms() << ", now_ms: " << now.ms()
                         << "), discarding probe cluster.";
    clusters_.pop();
    if (clusters_.empty()) {
      probing_state_ = ProbingState::kSuspended;
      return absl::nullopt;
    }
  }

  PacedPacketInfo info = clusters_.front().pace_info;
  info.probe_cluster_bytes_sent = clusters_.front().sent_bytes;
  return info;
}

// Probe size is recommended based on the probe bitrate required. We choose
// a minimum of twice |kMinProbeDeltaMs| interval to allow scheduling to be
// feasible.
DataSize BitrateProber::RecommendedMinProbeSize() const {
  if (clusters_.empty()) {
    return DataSize::Zero();
  }
  DataRate send_rate =
      DataRate::BitsPerSec(clusters_.front().pace_info.send_bitrate_bps);
  return 2 * send_rate * config_.min_probe_delta;
}

void BitrateProber::ProbeSent(Timestamp now, DataSize size) {
  RTC_DCHECK(probing_state_ == ProbingState::kActive);
  RTC_DCHECK(!size.IsZero());

  if (!clusters_.empty()) {
    ProbeCluster* cluster = &clusters_.front();
    if (cluster->sent_probes == 0) {
      RTC_DCHECK(cluster->started_at.IsInfinite());
      cluster->started_at = now;
    }
    cluster->sent_bytes += size.bytes<int>();
    cluster->sent_probes += 1;
    next_probe_time_ = CalculateNextProbeTime(*cluster);
    if (cluster->sent_bytes >= cluster->pace_info.probe_cluster_min_bytes &&
        cluster->sent_probes >= cluster->pace_info.probe_cluster_min_probes) {
      RTC_HISTOGRAM_COUNTS_100000("WebRTC.BWE.Probing.ProbeClusterSizeInBytes",
                                  cluster->sent_bytes);
      RTC_HISTOGRAM_COUNTS_100("WebRTC.BWE.Probing.ProbesPerCluster",
                               cluster->sent_probes);
      RTC_HISTOGRAM_COUNTS_10000("WebRTC.BWE.Probing.TimePerProbeCluster",
                                 (now - cluster->started_at).ms());

      clusters_.pop();
    }
    if (clusters_.empty()) {
      probing_state_ = ProbingState::kSuspended;
    }
  }
}

Timestamp BitrateProber::CalculateNextProbeTime(
    const ProbeCluster& cluster) const {
  RTC_CHECK_GT(cluster.pace_info.send_bitrate_bps, 0);
  RTC_CHECK(cluster.started_at.IsFinite());

  // Compute the time delta from the cluster start to ensure probe bitrate stays
  // close to the target bitrate. Result is in milliseconds.
  DataSize sent_bytes = DataSize::Bytes(cluster.sent_bytes);
  DataRate send_bitrate =
      DataRate::BitsPerSec(cluster.pace_info.send_bitrate_bps);
  TimeDelta delta = sent_bytes / send_bitrate;
  return cluster.started_at + delta;
}

}  // namespace webrtc
