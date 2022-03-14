/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_REMOTE_BITRATE_ESTIMATOR_REMOTE_BITRATE_ESTIMATOR_SINGLE_STREAM_H_
#define MODULES_REMOTE_BITRATE_ESTIMATOR_REMOTE_BITRATE_ESTIMATOR_SINGLE_STREAM_H_

#include <stddef.h>
#include <stdint.h>

#include <map>
#include <memory>
#include <vector>

#include "api/transport/field_trial_based_config.h"
#include "modules/remote_bitrate_estimator/aimd_rate_control.h"
#include "modules/remote_bitrate_estimator/include/remote_bitrate_estimator.h"
#include "rtc_base/rate_statistics.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/thread_annotations.h"

namespace webrtc {

class Clock;
struct RTPHeader;

class RemoteBitrateEstimatorSingleStream : public RemoteBitrateEstimator {
 public:
  RemoteBitrateEstimatorSingleStream(RemoteBitrateObserver* observer,
                                     Clock* clock);

  RemoteBitrateEstimatorSingleStream() = delete;
  RemoteBitrateEstimatorSingleStream(
      const RemoteBitrateEstimatorSingleStream&) = delete;
  RemoteBitrateEstimatorSingleStream& operator=(
      const RemoteBitrateEstimatorSingleStream&) = delete;

  ~RemoteBitrateEstimatorSingleStream() override;

  void IncomingPacket(int64_t arrival_time_ms,
                      size_t payload_size,
                      const RTPHeader& header) override;
  void Process() override;
  int64_t TimeUntilNextProcess() override;
  void OnRttUpdate(int64_t avg_rtt_ms, int64_t max_rtt_ms) override;
  void RemoveStream(uint32_t ssrc) override;
  bool LatestEstimate(std::vector<uint32_t>* ssrcs,
                      uint32_t* bitrate_bps) const override;
  void SetMinBitrate(int min_bitrate_bps) override;

 private:
  struct Detector;

  typedef std::map<uint32_t, Detector*> SsrcOveruseEstimatorMap;

  // Triggers a new estimate calculation.
  void UpdateEstimate(int64_t time_now) RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  void GetSsrcs(std::vector<uint32_t>* ssrcs) const
      RTC_SHARED_LOCKS_REQUIRED(mutex_);

  // Returns `remote_rate_` if the pointed to object exists,
  // otherwise creates it.
  AimdRateControl* GetRemoteRate() RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  Clock* const clock_;
  const FieldTrialBasedConfig field_trials_;
  SsrcOveruseEstimatorMap overuse_detectors_ RTC_GUARDED_BY(mutex_);
  RateStatistics incoming_bitrate_ RTC_GUARDED_BY(mutex_);
  uint32_t last_valid_incoming_bitrate_ RTC_GUARDED_BY(mutex_);
  std::unique_ptr<AimdRateControl> remote_rate_ RTC_GUARDED_BY(mutex_);
  RemoteBitrateObserver* const observer_ RTC_GUARDED_BY(mutex_);
  mutable Mutex mutex_;
  int64_t last_process_time_;
  int64_t process_interval_ms_ RTC_GUARDED_BY(mutex_);
  bool uma_recorded_;
};

}  // namespace webrtc

#endif  // MODULES_REMOTE_BITRATE_ESTIMATOR_REMOTE_BITRATE_ESTIMATOR_SINGLE_STREAM_H_
