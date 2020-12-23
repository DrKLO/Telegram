/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_REMOTE_BITRATE_ESTIMATOR_REMOTE_BITRATE_ESTIMATOR_ABS_SEND_TIME_H_
#define MODULES_REMOTE_BITRATE_ESTIMATOR_REMOTE_BITRATE_ESTIMATOR_ABS_SEND_TIME_H_

#include <stddef.h>
#include <stdint.h>

#include <list>
#include <map>
#include <memory>
#include <vector>

#include "api/rtp_headers.h"
#include "api/transport/field_trial_based_config.h"
#include "modules/remote_bitrate_estimator/aimd_rate_control.h"
#include "modules/remote_bitrate_estimator/include/remote_bitrate_estimator.h"
#include "modules/remote_bitrate_estimator/inter_arrival.h"
#include "modules/remote_bitrate_estimator/overuse_detector.h"
#include "modules/remote_bitrate_estimator/overuse_estimator.h"
#include "rtc_base/checks.h"
#include "rtc_base/race_checker.h"
#include "rtc_base/rate_statistics.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/thread_annotations.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {

struct Probe {
  Probe(int64_t send_time_ms, int64_t recv_time_ms, size_t payload_size)
      : send_time_ms(send_time_ms),
        recv_time_ms(recv_time_ms),
        payload_size(payload_size) {}
  int64_t send_time_ms;
  int64_t recv_time_ms;
  size_t payload_size;
};

struct Cluster {
  Cluster()
      : send_mean_ms(0.0f),
        recv_mean_ms(0.0f),
        mean_size(0),
        count(0),
        num_above_min_delta(0) {}

  int GetSendBitrateBps() const {
    RTC_CHECK_GT(send_mean_ms, 0.0f);
    return mean_size * 8 * 1000 / send_mean_ms;
  }

  int GetRecvBitrateBps() const {
    RTC_CHECK_GT(recv_mean_ms, 0.0f);
    return mean_size * 8 * 1000 / recv_mean_ms;
  }

  float send_mean_ms;
  float recv_mean_ms;
  // TODO(holmer): Add some variance metric as well?
  size_t mean_size;
  int count;
  int num_above_min_delta;
};

class RemoteBitrateEstimatorAbsSendTime : public RemoteBitrateEstimator {
 public:
  RemoteBitrateEstimatorAbsSendTime(RemoteBitrateObserver* observer,
                                    Clock* clock);

  RemoteBitrateEstimatorAbsSendTime() = delete;
  RemoteBitrateEstimatorAbsSendTime(const RemoteBitrateEstimatorAbsSendTime&) =
      delete;
  RemoteBitrateEstimatorAbsSendTime& operator=(
      const RemoteBitrateEstimatorAbsSendTime&) = delete;

  ~RemoteBitrateEstimatorAbsSendTime() override;

  void IncomingPacket(int64_t arrival_time_ms,
                      size_t payload_size,
                      const RTPHeader& header) override;
  // This class relies on Process() being called periodically (at least once
  // every other second) for streams to be timed out properly. Therefore it
  // shouldn't be detached from the ProcessThread except if it's about to be
  // deleted.
  void Process() override;
  int64_t TimeUntilNextProcess() override;
  void OnRttUpdate(int64_t avg_rtt_ms, int64_t max_rtt_ms) override;
  void RemoveStream(uint32_t ssrc) override;
  bool LatestEstimate(std::vector<uint32_t>* ssrcs,
                      uint32_t* bitrate_bps) const override;
  void SetMinBitrate(int min_bitrate_bps) override;

 private:
  typedef std::map<uint32_t, int64_t> Ssrcs;
  enum class ProbeResult { kBitrateUpdated, kNoUpdate };

  static bool IsWithinClusterBounds(int send_delta_ms,
                                    const Cluster& cluster_aggregate);

  static void AddCluster(std::list<Cluster>* clusters, Cluster* cluster);

  void IncomingPacketInfo(int64_t arrival_time_ms,
                          uint32_t send_time_24bits,
                          size_t payload_size,
                          uint32_t ssrc);

  void ComputeClusters(std::list<Cluster>* clusters) const;

  std::list<Cluster>::const_iterator FindBestProbe(
      const std::list<Cluster>& clusters) const;

  // Returns true if a probe which changed the estimate was detected.
  ProbeResult ProcessClusters(int64_t now_ms)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(&mutex_);

  bool IsBitrateImproving(int probe_bitrate_bps) const
      RTC_EXCLUSIVE_LOCKS_REQUIRED(&mutex_);

  void TimeoutStreams(int64_t now_ms) RTC_EXCLUSIVE_LOCKS_REQUIRED(&mutex_);

  rtc::RaceChecker network_race_;
  Clock* const clock_;
  const FieldTrialBasedConfig field_trials_;
  RemoteBitrateObserver* const observer_;
  std::unique_ptr<InterArrival> inter_arrival_;
  std::unique_ptr<OveruseEstimator> estimator_;
  OveruseDetector detector_;
  RateStatistics incoming_bitrate_;
  bool incoming_bitrate_initialized_;
  std::vector<int> recent_propagation_delta_ms_;
  std::vector<int64_t> recent_update_time_ms_;
  std::list<Probe> probes_;
  size_t total_probes_received_;
  int64_t first_packet_time_ms_;
  int64_t last_update_ms_;
  bool uma_recorded_;

  mutable Mutex mutex_;
  Ssrcs ssrcs_ RTC_GUARDED_BY(&mutex_);
  AimdRateControl remote_rate_ RTC_GUARDED_BY(&mutex_);
};

}  // namespace webrtc

#endif  // MODULES_REMOTE_BITRATE_ESTIMATOR_REMOTE_BITRATE_ESTIMATOR_ABS_SEND_TIME_H_
