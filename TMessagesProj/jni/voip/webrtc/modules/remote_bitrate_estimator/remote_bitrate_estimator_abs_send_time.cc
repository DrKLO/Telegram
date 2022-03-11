/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/remote_bitrate_estimator/remote_bitrate_estimator_abs_send_time.h"

#include <math.h>

#include <algorithm>
#include <memory>
#include <utility>

#include "api/transport/field_trial_based_config.h"
#include "api/units/data_rate.h"
#include "api/units/data_size.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "modules/remote_bitrate_estimator/include/bwe_defines.h"
#include "modules/remote_bitrate_estimator/include/remote_bitrate_estimator.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/thread_annotations.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {
namespace {

constexpr TimeDelta kMinClusterDelta = TimeDelta::Millis(1);
constexpr TimeDelta kInitialProbingInterval = TimeDelta::Seconds(2);
constexpr int kTimestampGroupLengthMs = 5;
constexpr int kAbsSendTimeInterArrivalUpshift = 8;
constexpr int kInterArrivalShift =
    RTPHeaderExtension::kAbsSendTimeFraction + kAbsSendTimeInterArrivalUpshift;
constexpr int kMinClusterSize = 4;
constexpr int kMaxProbePackets = 15;
constexpr int kExpectedNumberOfProbes = 3;
constexpr double kTimestampToMs =
    1000.0 / static_cast<double>(1 << kInterArrivalShift);

absl::optional<DataRate> OptionalRateFromOptionalBps(
    absl::optional<int> bitrate_bps) {
  if (bitrate_bps) {
    return DataRate::BitsPerSec(*bitrate_bps);
  } else {
    return absl::nullopt;
  }
}

template <typename K, typename V>
std::vector<K> Keys(const std::map<K, V>& map) {
  std::vector<K> keys;
  keys.reserve(map.size());
  for (const auto& kv_pair : map) {
    keys.push_back(kv_pair.first);
  }
  return keys;
}

}  // namespace

RemoteBitrateEstimatorAbsSendTime::~RemoteBitrateEstimatorAbsSendTime() =
    default;

bool RemoteBitrateEstimatorAbsSendTime::IsWithinClusterBounds(
    TimeDelta send_delta,
    const Cluster& cluster_aggregate) {
  if (cluster_aggregate.count == 0)
    return true;
  TimeDelta cluster_mean =
      cluster_aggregate.send_mean / cluster_aggregate.count;
  return (send_delta - cluster_mean).Abs() < TimeDelta::Micros(2'500);
}

void RemoteBitrateEstimatorAbsSendTime::MaybeAddCluster(
    const Cluster& cluster_aggregate,
    std::list<Cluster>& clusters) {
  if (cluster_aggregate.count < kMinClusterSize ||
      cluster_aggregate.send_mean <= TimeDelta::Zero() ||
      cluster_aggregate.recv_mean <= TimeDelta::Zero()) {
    return;
  }

  Cluster cluster;
  cluster.send_mean = cluster_aggregate.send_mean / cluster_aggregate.count;
  cluster.recv_mean = cluster_aggregate.recv_mean / cluster_aggregate.count;
  cluster.mean_size = cluster_aggregate.mean_size / cluster_aggregate.count;
  cluster.count = cluster_aggregate.count;
  cluster.num_above_min_delta = cluster_aggregate.num_above_min_delta;
  clusters.push_back(cluster);
}

RemoteBitrateEstimatorAbsSendTime::RemoteBitrateEstimatorAbsSendTime(
    RemoteBitrateObserver* observer,
    Clock* clock)
    : clock_(clock),
      observer_(observer),
      detector_(&field_trials_),
      remote_rate_(&field_trials_) {
  RTC_DCHECK(clock_);
  RTC_DCHECK(observer_);
  RTC_LOG(LS_INFO) << "RemoteBitrateEstimatorAbsSendTime: Instantiating.";
}

std::list<RemoteBitrateEstimatorAbsSendTime::Cluster>
RemoteBitrateEstimatorAbsSendTime::ComputeClusters() const {
  std::list<Cluster> clusters;
  Cluster cluster_aggregate;
  Timestamp prev_send_time = Timestamp::MinusInfinity();
  Timestamp prev_recv_time = Timestamp::MinusInfinity();
  for (const Probe& probe : probes_) {
    if (prev_send_time.IsFinite()) {
      TimeDelta send_delta = probe.send_time - prev_send_time;
      TimeDelta recv_delta = probe.recv_time - prev_recv_time;
      if (send_delta >= kMinClusterDelta && recv_delta >= kMinClusterDelta) {
        ++cluster_aggregate.num_above_min_delta;
      }
      if (!IsWithinClusterBounds(send_delta, cluster_aggregate)) {
        MaybeAddCluster(cluster_aggregate, clusters);
        cluster_aggregate = Cluster();
      }
      cluster_aggregate.send_mean += send_delta;
      cluster_aggregate.recv_mean += recv_delta;
      cluster_aggregate.mean_size += probe.payload_size;
      ++cluster_aggregate.count;
    }
    prev_send_time = probe.send_time;
    prev_recv_time = probe.recv_time;
  }
  MaybeAddCluster(cluster_aggregate, clusters);
  return clusters;
}

const RemoteBitrateEstimatorAbsSendTime::Cluster*
RemoteBitrateEstimatorAbsSendTime::FindBestProbe(
    const std::list<Cluster>& clusters) const {
  DataRate highest_probe_bitrate = DataRate::Zero();
  const Cluster* best = nullptr;
  for (const auto& cluster : clusters) {
    if (cluster.send_mean == TimeDelta::Zero() ||
        cluster.recv_mean == TimeDelta::Zero()) {
      continue;
    }
    if (cluster.num_above_min_delta > cluster.count / 2 &&
        (cluster.recv_mean - cluster.send_mean <= TimeDelta::Millis(2) &&
         cluster.send_mean - cluster.recv_mean <= TimeDelta::Millis(5))) {
      DataRate probe_bitrate =
          std::min(cluster.SendBitrate(), cluster.RecvBitrate());
      if (probe_bitrate > highest_probe_bitrate) {
        highest_probe_bitrate = probe_bitrate;
        best = &cluster;
      }
    } else {
      RTC_LOG(LS_INFO) << "Probe failed, sent at "
                       << cluster.SendBitrate().bps() << " bps, received at "
                       << cluster.RecvBitrate().bps()
                       << " bps. Mean send delta: " << cluster.send_mean.ms()
                       << " ms, mean recv delta: " << cluster.recv_mean.ms()
                       << " ms, num probes: " << cluster.count;
      break;
    }
  }
  return best;
}

RemoteBitrateEstimatorAbsSendTime::ProbeResult
RemoteBitrateEstimatorAbsSendTime::ProcessClusters(Timestamp now) {
  std::list<Cluster> clusters = ComputeClusters();
  if (clusters.empty()) {
    // If we reach the max number of probe packets and still have no clusters,
    // we will remove the oldest one.
    if (probes_.size() >= kMaxProbePackets)
      probes_.pop_front();
    return ProbeResult::kNoUpdate;
  }

  if (const Cluster* best = FindBestProbe(clusters)) {
    DataRate probe_bitrate = std::min(best->SendBitrate(), best->RecvBitrate());
    // Make sure that a probe sent on a lower bitrate than our estimate can't
    // reduce the estimate.
    if (IsBitrateImproving(probe_bitrate)) {
      RTC_LOG(LS_INFO) << "Probe successful, sent at "
                       << best->SendBitrate().bps() << " bps, received at "
                       << best->RecvBitrate().bps()
                       << " bps. Mean send delta: " << best->send_mean.ms()
                       << " ms, mean recv delta: " << best->recv_mean.ms()
                       << " ms, num probes: " << best->count;
      remote_rate_.SetEstimate(probe_bitrate, now);
      return ProbeResult::kBitrateUpdated;
    }
  }

  // Not probing and received non-probe packet, or finished with current set
  // of probes.
  if (clusters.size() >= kExpectedNumberOfProbes)
    probes_.clear();
  return ProbeResult::kNoUpdate;
}

bool RemoteBitrateEstimatorAbsSendTime::IsBitrateImproving(
    DataRate probe_bitrate) const {
  bool initial_probe =
      !remote_rate_.ValidEstimate() && probe_bitrate > DataRate::Zero();
  bool bitrate_above_estimate = remote_rate_.ValidEstimate() &&
                                probe_bitrate > remote_rate_.LatestEstimate();
  return initial_probe || bitrate_above_estimate;
}

void RemoteBitrateEstimatorAbsSendTime::IncomingPacket(
    int64_t arrival_time_ms,
    size_t payload_size,
    const RTPHeader& header) {
  RTC_DCHECK_RUNS_SERIALIZED(&network_race_);
  if (!header.extension.hasAbsoluteSendTime) {
    RTC_LOG(LS_WARNING)
        << "RemoteBitrateEstimatorAbsSendTimeImpl: Incoming packet "
           "is missing absolute send time extension!";
    return;
  }
  IncomingPacketInfo(Timestamp::Millis(arrival_time_ms),
                     header.extension.absoluteSendTime,
                     DataSize::Bytes(payload_size), header.ssrc);
}

void RemoteBitrateEstimatorAbsSendTime::IncomingPacketInfo(
    Timestamp arrival_time,
    uint32_t send_time_24bits,
    DataSize payload_size,
    uint32_t ssrc) {
  RTC_CHECK(send_time_24bits < (1ul << 24));
  if (!uma_recorded_) {
    RTC_HISTOGRAM_ENUMERATION(kBweTypeHistogram, BweNames::kReceiverAbsSendTime,
                              BweNames::kBweNamesMax);
    uma_recorded_ = true;
  }
  // Shift up send time to use the full 32 bits that inter_arrival works with,
  // so wrapping works properly.
  uint32_t timestamp = send_time_24bits << kAbsSendTimeInterArrivalUpshift;
  Timestamp send_time =
      Timestamp::Millis(static_cast<int64_t>(timestamp) * kTimestampToMs);

  Timestamp now = clock_->CurrentTime();
  // TODO(holmer): SSRCs are only needed for REMB, should be broken out from
  // here.

  // Check if incoming bitrate estimate is valid, and if it needs to be reset.
  absl::optional<uint32_t> incoming_bitrate =
      incoming_bitrate_.Rate(arrival_time.ms());
  if (incoming_bitrate) {
    incoming_bitrate_initialized_ = true;
  } else if (incoming_bitrate_initialized_) {
    // Incoming bitrate had a previous valid value, but now not enough data
    // point are left within the current window. Reset incoming bitrate
    // estimator so that the window size will only contain new data points.
    incoming_bitrate_.Reset();
    incoming_bitrate_initialized_ = false;
  }
  incoming_bitrate_.Update(payload_size.bytes(), arrival_time.ms());

  if (first_packet_time_.IsInfinite()) {
    first_packet_time_ = now;
  }

  uint32_t ts_delta = 0;
  int64_t t_delta = 0;
  int size_delta = 0;
  bool update_estimate = false;
  DataRate target_bitrate = DataRate::Zero();
  std::vector<uint32_t> ssrcs;
  {
    MutexLock lock(&mutex_);

    TimeoutStreams(now);
    RTC_DCHECK(inter_arrival_);
    RTC_DCHECK(estimator_);
    ssrcs_.insert_or_assign(ssrc, now);

    // For now only try to detect probes while we don't have a valid estimate.
    // We currently assume that only packets larger than 200 bytes are paced by
    // the sender.
    static constexpr DataSize kMinProbePacketSize = DataSize::Bytes(200);
    if (payload_size > kMinProbePacketSize &&
        (!remote_rate_.ValidEstimate() ||
         now - first_packet_time_ < kInitialProbingInterval)) {
      // TODO(holmer): Use a map instead to get correct order?
      if (total_probes_received_ < kMaxProbePackets) {
        TimeDelta send_delta = TimeDelta::Millis(-1);
        TimeDelta recv_delta = TimeDelta::Millis(-1);
        if (!probes_.empty()) {
          send_delta = send_time - probes_.back().send_time;
          recv_delta = arrival_time - probes_.back().recv_time;
        }
        RTC_LOG(LS_INFO) << "Probe packet received: send time="
                         << send_time.ms()
                         << " ms, recv time=" << arrival_time.ms()
                         << " ms, send delta=" << send_delta.ms()
                         << " ms, recv delta=" << recv_delta.ms() << " ms.";
      }
      probes_.emplace_back(send_time, arrival_time, payload_size);
      ++total_probes_received_;
      // Make sure that a probe which updated the bitrate immediately has an
      // effect by calling the OnReceiveBitrateChanged callback.
      if (ProcessClusters(now) == ProbeResult::kBitrateUpdated)
        update_estimate = true;
    }
    if (inter_arrival_->ComputeDeltas(timestamp, arrival_time.ms(), now.ms(),
                                      payload_size.bytes(), &ts_delta, &t_delta,
                                      &size_delta)) {
      double ts_delta_ms = (1000.0 * ts_delta) / (1 << kInterArrivalShift);
      estimator_->Update(t_delta, ts_delta_ms, size_delta, detector_.State(),
                         arrival_time.ms());
      detector_.Detect(estimator_->offset(), ts_delta_ms,
                       estimator_->num_of_deltas(), arrival_time.ms());
    }

    if (!update_estimate) {
      // Check if it's time for a periodic update or if we should update because
      // of an over-use.
      if (last_update_.IsInfinite() ||
          now.ms() - last_update_.ms() >
              remote_rate_.GetFeedbackInterval().ms()) {
        update_estimate = true;
      } else if (detector_.State() == BandwidthUsage::kBwOverusing) {
        absl::optional<uint32_t> incoming_rate =
            incoming_bitrate_.Rate(arrival_time.ms());
        if (incoming_rate && remote_rate_.TimeToReduceFurther(
                                 now, DataRate::BitsPerSec(*incoming_rate))) {
          update_estimate = true;
        }
      }
    }

    if (update_estimate) {
      // The first overuse should immediately trigger a new estimate.
      // We also have to update the estimate immediately if we are overusing
      // and the target bitrate is too high compared to what we are receiving.
      const RateControlInput input(
          detector_.State(), OptionalRateFromOptionalBps(
                                 incoming_bitrate_.Rate(arrival_time.ms())));
      target_bitrate = remote_rate_.Update(&input, now);
      update_estimate = remote_rate_.ValidEstimate();
      ssrcs = Keys(ssrcs_);
    }
  }
  if (update_estimate) {
    last_update_ = now;
    observer_->OnReceiveBitrateChanged(ssrcs, target_bitrate.bps<uint32_t>());
  }
}

void RemoteBitrateEstimatorAbsSendTime::Process() {}

int64_t RemoteBitrateEstimatorAbsSendTime::TimeUntilNextProcess() {
  const int64_t kDisabledModuleTime = 1000;
  return kDisabledModuleTime;
}

void RemoteBitrateEstimatorAbsSendTime::TimeoutStreams(Timestamp now) {
  for (auto it = ssrcs_.begin(); it != ssrcs_.end();) {
    if (now - it->second > TimeDelta::Millis(kStreamTimeOutMs)) {
      ssrcs_.erase(it++);
    } else {
      ++it;
    }
  }
  if (ssrcs_.empty()) {
    // We can't update the estimate if we don't have any active streams.
    inter_arrival_ = std::make_unique<InterArrival>(
        (kTimestampGroupLengthMs << kInterArrivalShift) / 1000, kTimestampToMs,
        true);
    estimator_ = std::make_unique<OveruseEstimator>(OverUseDetectorOptions());
    // We deliberately don't reset the first_packet_time_ms_ here for now since
    // we only probe for bandwidth in the beginning of a call right now.
  }
}

void RemoteBitrateEstimatorAbsSendTime::OnRttUpdate(int64_t avg_rtt_ms,
                                                    int64_t /*max_rtt_ms*/) {
  MutexLock lock(&mutex_);
  remote_rate_.SetRtt(TimeDelta::Millis(avg_rtt_ms));
}

void RemoteBitrateEstimatorAbsSendTime::RemoveStream(uint32_t ssrc) {
  MutexLock lock(&mutex_);
  ssrcs_.erase(ssrc);
}

bool RemoteBitrateEstimatorAbsSendTime::LatestEstimate(
    std::vector<uint32_t>* ssrcs,
    uint32_t* bitrate_bps) const {
  // Currently accessed from both the process thread (see
  // ModuleRtpRtcpImpl::Process()) and the configuration thread (see
  // Call::GetStats()). Should in the future only be accessed from a single
  // thread.
  RTC_DCHECK(ssrcs);
  RTC_DCHECK(bitrate_bps);
  MutexLock lock(&mutex_);
  if (!remote_rate_.ValidEstimate()) {
    return false;
  }
  *ssrcs = Keys(ssrcs_);
  if (ssrcs_.empty()) {
    *bitrate_bps = 0;
  } else {
    *bitrate_bps = remote_rate_.LatestEstimate().bps<uint32_t>();
  }
  return true;
}

void RemoteBitrateEstimatorAbsSendTime::SetMinBitrate(int min_bitrate_bps) {
  // Called from both the configuration thread and the network thread. Shouldn't
  // be called from the network thread in the future.
  MutexLock lock(&mutex_);
  remote_rate_.SetMinBitrate(DataRate::BitsPerSec(min_bitrate_bps));
}
}  // namespace webrtc
