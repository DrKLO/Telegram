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

#include "api/units/data_size.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

namespace {
constexpr TimeDelta kProbeClusterTimeout = TimeDelta::Seconds(5);
constexpr size_t kMaxPendingProbeClusters = 5;

}  // namespace

BitrateProberConfig::BitrateProberConfig(
    const FieldTrialsView* key_value_config)
    : min_probe_delta("min_probe_delta", TimeDelta::Millis(2)),
      max_probe_delay("max_probe_delay", TimeDelta::Millis(10)),
      min_packet_size("min_packet_size", DataSize::Bytes(200)) {
  ParseFieldTrial({&min_probe_delta, &max_probe_delay, &min_packet_size},
                  key_value_config->Lookup("WebRTC-Bwe-ProbingBehavior"));
}

BitrateProber::BitrateProber(const FieldTrialsView& field_trials)
    : probing_state_(ProbingState::kDisabled),
      next_probe_time_(Timestamp::PlusInfinity()),
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

void BitrateProber::SetAllowProbeWithoutMediaPacket(bool allow) {
  config_.allow_start_probing_immediately = allow;
  MaybeSetActiveState(/*packet_size=*/DataSize::Zero());
}

void BitrateProber::MaybeSetActiveState(DataSize packet_size) {
  if (ReadyToSetActiveState(packet_size)) {
    next_probe_time_ = Timestamp::MinusInfinity();
    probing_state_ = ProbingState::kActive;
  }
}

bool BitrateProber::ReadyToSetActiveState(DataSize packet_size) const {
  if (clusters_.empty()) {
    RTC_DCHECK(probing_state_ == ProbingState::kDisabled ||
               probing_state_ == ProbingState::kInactive);
    return false;
  }
  switch (probing_state_) {
    case ProbingState::kDisabled:
    case ProbingState::kActive:
      return false;
    case ProbingState::kInactive:
      if (config_.allow_start_probing_immediately) {
        return true;
      }
      // If config_.min_packet_size > 0, a "large enough" packet must be
      // sent first, before a probe can be generated and sent. Otherwise,
      // send the probe asap.
      return packet_size >=
             std::min(RecommendedMinProbeSize(), config_.min_packet_size.Get());
  }
}

void BitrateProber::OnIncomingPacket(DataSize packet_size) {
  MaybeSetActiveState(packet_size);
}

void BitrateProber::CreateProbeCluster(
    const ProbeClusterConfig& cluster_config) {
  RTC_DCHECK(probing_state_ != ProbingState::kDisabled);

  while (!clusters_.empty() &&
         (cluster_config.at_time - clusters_.front().requested_at >
              kProbeClusterTimeout ||
          clusters_.size() > kMaxPendingProbeClusters)) {
    clusters_.pop();
  }

  ProbeCluster cluster;
  cluster.requested_at = cluster_config.at_time;
  cluster.pace_info.probe_cluster_min_probes =
      cluster_config.target_probe_count;
  cluster.pace_info.probe_cluster_min_bytes =
      (cluster_config.target_data_rate * cluster_config.target_duration)
          .bytes();
  RTC_DCHECK_GE(cluster.pace_info.probe_cluster_min_bytes, 0);
  cluster.pace_info.send_bitrate = cluster_config.target_data_rate;
  cluster.pace_info.probe_cluster_id = cluster_config.id;
  clusters_.push(cluster);

  MaybeSetActiveState(/*packet_size=*/DataSize::Zero());

  RTC_DCHECK(probing_state_ == ProbingState::kActive ||
             probing_state_ == ProbingState::kInactive);

  RTC_LOG(LS_INFO) << "Probe cluster (bitrate_bps:min bytes:min packets): ("
                   << cluster.pace_info.send_bitrate << ":"
                   << cluster.pace_info.probe_cluster_min_bytes << ":"
                   << cluster.pace_info.probe_cluster_min_probes << ", "
                   << (probing_state_ == ProbingState::kInactive ? "Inactive"
                                                                 : "Active")
                   << ")";
}

Timestamp BitrateProber::NextProbeTime(Timestamp now) const {
  // Probing is not active or probing is already complete.
  if (probing_state_ != ProbingState::kActive || clusters_.empty()) {
    return Timestamp::PlusInfinity();
  }

  return next_probe_time_;
}

absl::optional<PacedPacketInfo> BitrateProber::CurrentCluster(Timestamp now) {
  if (clusters_.empty() || probing_state_ != ProbingState::kActive) {
    return absl::nullopt;
  }

  if (next_probe_time_.IsFinite() &&
      now - next_probe_time_ > config_.max_probe_delay.Get()) {
    RTC_DLOG(LS_WARNING) << "Probe delay too high"
                            " (next_ms:"
                         << next_probe_time_.ms() << ", now_ms: " << now.ms()
                         << "), discarding probe cluster.";
    clusters_.pop();
    if (clusters_.empty()) {
      probing_state_ = ProbingState::kInactive;
      return absl::nullopt;
    }
  }

  PacedPacketInfo info = clusters_.front().pace_info;
  info.probe_cluster_bytes_sent = clusters_.front().sent_bytes;
  return info;
}

DataSize BitrateProber::RecommendedMinProbeSize() const {
  if (clusters_.empty()) {
    return DataSize::Zero();
  }
  DataRate send_rate = clusters_.front().pace_info.send_bitrate;
  return send_rate * config_.min_probe_delta;
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
      clusters_.pop();
    }
    if (clusters_.empty()) {
      probing_state_ = ProbingState::kInactive;
    }
  }
}

Timestamp BitrateProber::CalculateNextProbeTime(
    const ProbeCluster& cluster) const {
  RTC_CHECK_GT(cluster.pace_info.send_bitrate.bps(), 0);
  RTC_CHECK(cluster.started_at.IsFinite());

  // Compute the time delta from the cluster start to ensure probe bitrate stays
  // close to the target bitrate. Result is in milliseconds.
  DataSize sent_bytes = DataSize::Bytes(cluster.sent_bytes);
  DataRate send_bitrate = cluster.pace_info.send_bitrate;

  TimeDelta delta = sent_bytes / send_bitrate;
  return cluster.started_at + delta;
}

}  // namespace webrtc
