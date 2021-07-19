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

#include "api/transport/field_trial_based_config.h"
#include "modules/remote_bitrate_estimator/include/bwe_defines.h"
#include "modules/remote_bitrate_estimator/include/remote_bitrate_estimator.h"
#include "rtc_base/checks.h"
#include "rtc_base/constructor_magic.h"
#include "rtc_base/logging.h"
#include "rtc_base/thread_annotations.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {
namespace {
absl::optional<DataRate> OptionalRateFromOptionalBps(
    absl::optional<int> bitrate_bps) {
  if (bitrate_bps) {
    return DataRate::BitsPerSec(*bitrate_bps);
  } else {
    return absl::nullopt;
  }
}
}  // namespace

enum {
  kTimestampGroupLengthMs = 5,
  kAbsSendTimeInterArrivalUpshift = 8,
  kInterArrivalShift = RTPHeaderExtension::kAbsSendTimeFraction +
                       kAbsSendTimeInterArrivalUpshift,
  kInitialProbingIntervalMs = 2000,
  kMinClusterSize = 4,
  kMaxProbePackets = 15,
  kExpectedNumberOfProbes = 3
};

static const double kTimestampToMs =
    1000.0 / static_cast<double>(1 << kInterArrivalShift);

template <typename K, typename V>
std::vector<K> Keys(const std::map<K, V>& map) {
  std::vector<K> keys;
  keys.reserve(map.size());
  for (typename std::map<K, V>::const_iterator it = map.begin();
       it != map.end(); ++it) {
    keys.push_back(it->first);
  }
  return keys;
}

uint32_t ConvertMsTo24Bits(int64_t time_ms) {
  uint32_t time_24_bits =
      static_cast<uint32_t>(((static_cast<uint64_t>(time_ms)
                              << RTPHeaderExtension::kAbsSendTimeFraction) +
                             500) /
                            1000) &
      0x00FFFFFF;
  return time_24_bits;
}

RemoteBitrateEstimatorAbsSendTime::~RemoteBitrateEstimatorAbsSendTime() =
    default;

bool RemoteBitrateEstimatorAbsSendTime::IsWithinClusterBounds(
    int send_delta_ms,
    const Cluster& cluster_aggregate) {
  if (cluster_aggregate.count == 0)
    return true;
  float cluster_mean = cluster_aggregate.send_mean_ms /
                       static_cast<float>(cluster_aggregate.count);
  return fabs(static_cast<float>(send_delta_ms) - cluster_mean) < 2.5f;
}

void RemoteBitrateEstimatorAbsSendTime::AddCluster(std::list<Cluster>* clusters,
                                                   Cluster* cluster) {
  cluster->send_mean_ms /= static_cast<float>(cluster->count);
  cluster->recv_mean_ms /= static_cast<float>(cluster->count);
  cluster->mean_size /= cluster->count;
  clusters->push_back(*cluster);
}

RemoteBitrateEstimatorAbsSendTime::RemoteBitrateEstimatorAbsSendTime(
    RemoteBitrateObserver* observer,
    Clock* clock)
    : clock_(clock),
      observer_(observer),
      inter_arrival_(),
      estimator_(),
      detector_(&field_trials_),
      incoming_bitrate_(kBitrateWindowMs, 8000),
      incoming_bitrate_initialized_(false),
      total_probes_received_(0),
      first_packet_time_ms_(-1),
      last_update_ms_(-1),
      uma_recorded_(false),
      remote_rate_(&field_trials_) {
  RTC_DCHECK(clock_);
  RTC_DCHECK(observer_);
  RTC_LOG(LS_INFO) << "RemoteBitrateEstimatorAbsSendTime: Instantiating.";
}

void RemoteBitrateEstimatorAbsSendTime::ComputeClusters(
    std::list<Cluster>* clusters) const {
  Cluster current;
  int64_t prev_send_time = -1;
  int64_t prev_recv_time = -1;
  for (std::list<Probe>::const_iterator it = probes_.begin();
       it != probes_.end(); ++it) {
    if (prev_send_time >= 0) {
      int send_delta_ms = it->send_time_ms - prev_send_time;
      int recv_delta_ms = it->recv_time_ms - prev_recv_time;
      if (send_delta_ms >= 1 && recv_delta_ms >= 1) {
        ++current.num_above_min_delta;
      }
      if (!IsWithinClusterBounds(send_delta_ms, current)) {
        if (current.count >= kMinClusterSize && current.send_mean_ms > 0.0f &&
            current.recv_mean_ms > 0.0f) {
          AddCluster(clusters, &current);
        }
        current = Cluster();
      }
      current.send_mean_ms += send_delta_ms;
      current.recv_mean_ms += recv_delta_ms;
      current.mean_size += it->payload_size;
      ++current.count;
    }
    prev_send_time = it->send_time_ms;
    prev_recv_time = it->recv_time_ms;
  }
  if (current.count >= kMinClusterSize && current.send_mean_ms > 0.0f &&
      current.recv_mean_ms > 0.0f) {
    AddCluster(clusters, &current);
  }
}

std::list<Cluster>::const_iterator
RemoteBitrateEstimatorAbsSendTime::FindBestProbe(
    const std::list<Cluster>& clusters) const {
  int highest_probe_bitrate_bps = 0;
  std::list<Cluster>::const_iterator best_it = clusters.end();
  for (std::list<Cluster>::const_iterator it = clusters.begin();
       it != clusters.end(); ++it) {
    if (it->send_mean_ms == 0 || it->recv_mean_ms == 0)
      continue;
    if (it->num_above_min_delta > it->count / 2 &&
        (it->recv_mean_ms - it->send_mean_ms <= 2.0f &&
         it->send_mean_ms - it->recv_mean_ms <= 5.0f)) {
      int probe_bitrate_bps =
          std::min(it->GetSendBitrateBps(), it->GetRecvBitrateBps());
      if (probe_bitrate_bps > highest_probe_bitrate_bps) {
        highest_probe_bitrate_bps = probe_bitrate_bps;
        best_it = it;
      }
    } else {
      int send_bitrate_bps = it->mean_size * 8 * 1000 / it->send_mean_ms;
      int recv_bitrate_bps = it->mean_size * 8 * 1000 / it->recv_mean_ms;
      RTC_LOG(LS_INFO) << "Probe failed, sent at " << send_bitrate_bps
                       << " bps, received at " << recv_bitrate_bps
                       << " bps. Mean send delta: " << it->send_mean_ms
                       << " ms, mean recv delta: " << it->recv_mean_ms
                       << " ms, num probes: " << it->count;
      break;
    }
  }
  return best_it;
}

RemoteBitrateEstimatorAbsSendTime::ProbeResult
RemoteBitrateEstimatorAbsSendTime::ProcessClusters(int64_t now_ms) {
  std::list<Cluster> clusters;
  ComputeClusters(&clusters);
  if (clusters.empty()) {
    // If we reach the max number of probe packets and still have no clusters,
    // we will remove the oldest one.
    if (probes_.size() >= kMaxProbePackets)
      probes_.pop_front();
    return ProbeResult::kNoUpdate;
  }

  std::list<Cluster>::const_iterator best_it = FindBestProbe(clusters);
  if (best_it != clusters.end()) {
    int probe_bitrate_bps =
        std::min(best_it->GetSendBitrateBps(), best_it->GetRecvBitrateBps());
    // Make sure that a probe sent on a lower bitrate than our estimate can't
    // reduce the estimate.
    if (IsBitrateImproving(probe_bitrate_bps)) {
      RTC_LOG(LS_INFO) << "Probe successful, sent at "
                       << best_it->GetSendBitrateBps() << " bps, received at "
                       << best_it->GetRecvBitrateBps()
                       << " bps. Mean send delta: " << best_it->send_mean_ms
                       << " ms, mean recv delta: " << best_it->recv_mean_ms
                       << " ms, num probes: " << best_it->count;
      remote_rate_.SetEstimate(DataRate::BitsPerSec(probe_bitrate_bps),
                               Timestamp::Millis(now_ms));
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
    int new_bitrate_bps) const {
  bool initial_probe = !remote_rate_.ValidEstimate() && new_bitrate_bps > 0;
  bool bitrate_above_estimate =
      remote_rate_.ValidEstimate() &&
      new_bitrate_bps > remote_rate_.LatestEstimate().bps<int>();
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
  IncomingPacketInfo(arrival_time_ms, header.extension.absoluteSendTime,
                     payload_size, header.ssrc);
}

void RemoteBitrateEstimatorAbsSendTime::IncomingPacketInfo(
    int64_t arrival_time_ms,
    uint32_t send_time_24bits,
    size_t payload_size,
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
  int64_t send_time_ms = static_cast<int64_t>(timestamp) * kTimestampToMs;

  int64_t now_ms = clock_->TimeInMilliseconds();
  // TODO(holmer): SSRCs are only needed for REMB, should be broken out from
  // here.

  // Check if incoming bitrate estimate is valid, and if it needs to be reset.
  absl::optional<uint32_t> incoming_bitrate =
      incoming_bitrate_.Rate(arrival_time_ms);
  if (incoming_bitrate) {
    incoming_bitrate_initialized_ = true;
  } else if (incoming_bitrate_initialized_) {
    // Incoming bitrate had a previous valid value, but now not enough data
    // point are left within the current window. Reset incoming bitrate
    // estimator so that the window size will only contain new data points.
    incoming_bitrate_.Reset();
    incoming_bitrate_initialized_ = false;
  }
  incoming_bitrate_.Update(payload_size, arrival_time_ms);

  if (first_packet_time_ms_ == -1)
    first_packet_time_ms_ = now_ms;

  uint32_t ts_delta = 0;
  int64_t t_delta = 0;
  int size_delta = 0;
  bool update_estimate = false;
  uint32_t target_bitrate_bps = 0;
  std::vector<uint32_t> ssrcs;
  {
    MutexLock lock(&mutex_);

    TimeoutStreams(now_ms);
    RTC_DCHECK(inter_arrival_.get());
    RTC_DCHECK(estimator_.get());
    ssrcs_[ssrc] = now_ms;

    // For now only try to detect probes while we don't have a valid estimate.
    // We currently assume that only packets larger than 200 bytes are paced by
    // the sender.
    const size_t kMinProbePacketSize = 200;
    if (payload_size > kMinProbePacketSize &&
        (!remote_rate_.ValidEstimate() ||
         now_ms - first_packet_time_ms_ < kInitialProbingIntervalMs)) {
      // TODO(holmer): Use a map instead to get correct order?
      if (total_probes_received_ < kMaxProbePackets) {
        int send_delta_ms = -1;
        int recv_delta_ms = -1;
        if (!probes_.empty()) {
          send_delta_ms = send_time_ms - probes_.back().send_time_ms;
          recv_delta_ms = arrival_time_ms - probes_.back().recv_time_ms;
        }
        RTC_LOG(LS_INFO) << "Probe packet received: send time=" << send_time_ms
                         << " ms, recv time=" << arrival_time_ms
                         << " ms, send delta=" << send_delta_ms
                         << " ms, recv delta=" << recv_delta_ms << " ms.";
      }
      probes_.push_back(Probe(send_time_ms, arrival_time_ms, payload_size));
      ++total_probes_received_;
      // Make sure that a probe which updated the bitrate immediately has an
      // effect by calling the OnReceiveBitrateChanged callback.
      if (ProcessClusters(now_ms) == ProbeResult::kBitrateUpdated)
        update_estimate = true;
    }
    if (inter_arrival_->ComputeDeltas(timestamp, arrival_time_ms, now_ms,
                                      payload_size, &ts_delta, &t_delta,
                                      &size_delta)) {
      double ts_delta_ms = (1000.0 * ts_delta) / (1 << kInterArrivalShift);
      estimator_->Update(t_delta, ts_delta_ms, size_delta, detector_.State(),
                         arrival_time_ms);
      detector_.Detect(estimator_->offset(), ts_delta_ms,
                       estimator_->num_of_deltas(), arrival_time_ms);
    }

    if (!update_estimate) {
      // Check if it's time for a periodic update or if we should update because
      // of an over-use.
      if (last_update_ms_ == -1 ||
          now_ms - last_update_ms_ > remote_rate_.GetFeedbackInterval().ms()) {
        update_estimate = true;
      } else if (detector_.State() == BandwidthUsage::kBwOverusing) {
        absl::optional<uint32_t> incoming_rate =
            incoming_bitrate_.Rate(arrival_time_ms);
        if (incoming_rate && remote_rate_.TimeToReduceFurther(
                                 Timestamp::Millis(now_ms),
                                 DataRate::BitsPerSec(*incoming_rate))) {
          update_estimate = true;
        }
      }
    }

    if (update_estimate) {
      // The first overuse should immediately trigger a new estimate.
      // We also have to update the estimate immediately if we are overusing
      // and the target bitrate is too high compared to what we are receiving.
      const RateControlInput input(
          detector_.State(),
          OptionalRateFromOptionalBps(incoming_bitrate_.Rate(arrival_time_ms)));
      target_bitrate_bps =
          remote_rate_.Update(&input, Timestamp::Millis(now_ms))
              .bps<uint32_t>();
      update_estimate = remote_rate_.ValidEstimate();
      ssrcs = Keys(ssrcs_);
    }
  }
  if (update_estimate) {
    last_update_ms_ = now_ms;
    observer_->OnReceiveBitrateChanged(ssrcs, target_bitrate_bps);
  }
}

void RemoteBitrateEstimatorAbsSendTime::Process() {}

int64_t RemoteBitrateEstimatorAbsSendTime::TimeUntilNextProcess() {
  const int64_t kDisabledModuleTime = 1000;
  return kDisabledModuleTime;
}

void RemoteBitrateEstimatorAbsSendTime::TimeoutStreams(int64_t now_ms) {
  for (Ssrcs::iterator it = ssrcs_.begin(); it != ssrcs_.end();) {
    if ((now_ms - it->second) > kStreamTimeOutMs) {
      ssrcs_.erase(it++);
    } else {
      ++it;
    }
  }
  if (ssrcs_.empty()) {
    // We can't update the estimate if we don't have any active streams.
    inter_arrival_.reset(
        new InterArrival((kTimestampGroupLengthMs << kInterArrivalShift) / 1000,
                         kTimestampToMs, true));
    estimator_.reset(new OveruseEstimator(OverUseDetectorOptions()));
    // We deliberately don't reset the first_packet_time_ms_ here for now since
    // we only probe for bandwidth in the beginning of a call right now.
  }
}

void RemoteBitrateEstimatorAbsSendTime::OnRttUpdate(int64_t avg_rtt_ms,
                                                    int64_t max_rtt_ms) {
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
