/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_CONGESTION_CONTROLLER_INCLUDE_RECEIVE_SIDE_CONGESTION_CONTROLLER_H_
#define MODULES_CONGESTION_CONTROLLER_INCLUDE_RECEIVE_SIDE_CONGESTION_CONTROLLER_H_

#include <memory>
#include <vector>

#include "api/transport/field_trial_based_config.h"
#include "api/transport/network_control.h"
#include "api/units/data_rate.h"
#include "modules/congestion_controller/remb_throttler.h"
#include "modules/include/module.h"
#include "modules/pacing/packet_router.h"
#include "modules/remote_bitrate_estimator/remote_estimator_proxy.h"
#include "rtc_base/synchronization/mutex.h"

namespace webrtc {
class RemoteBitrateEstimator;
class RemoteBitrateObserver;

// This class represents the congestion control state for receive
// streams. For send side bandwidth estimation, this is simply
// relaying for each received RTP packet back to the sender. While for
// receive side bandwidth estimation, we do the estimation locally and
// send our results back to the sender.
class ReceiveSideCongestionController : public CallStatsObserver,
                                        public Module {
 public:
  ReceiveSideCongestionController(
      Clock* clock,
      RemoteEstimatorProxy::TransportFeedbackSender feedback_sender,
      RembThrottler::RembSender remb_sender,
      NetworkStateEstimator* network_state_estimator);

  ~ReceiveSideCongestionController() override {}

  virtual void OnReceivedPacket(int64_t arrival_time_ms,
                                size_t payload_size,
                                const RTPHeader& header);

  void SetSendPeriodicFeedback(bool send_periodic_feedback);
  // TODO(nisse): Delete these methods, design a more specific interface.
  virtual RemoteBitrateEstimator* GetRemoteBitrateEstimator(bool send_side_bwe);
  virtual const RemoteBitrateEstimator* GetRemoteBitrateEstimator(
      bool send_side_bwe) const;

  // Implements CallStatsObserver.
  void OnRttUpdate(int64_t avg_rtt_ms, int64_t max_rtt_ms) override;

  // This is send bitrate, used to control the rate of feedback messages.
  void OnBitrateChanged(int bitrate_bps);

  // Ensures the remote party is notified of the receive bitrate no larger than
  // `bitrate` using RTCP REMB.
  void SetMaxDesiredReceiveBitrate(DataRate bitrate);

  // Implements Module.
  int64_t TimeUntilNextProcess() override;
  void Process() override;

 private:
  class WrappingBitrateEstimator : public RemoteBitrateEstimator {
   public:
    WrappingBitrateEstimator(RemoteBitrateObserver* observer, Clock* clock);

    WrappingBitrateEstimator() = delete;
    WrappingBitrateEstimator(const WrappingBitrateEstimator&) = delete;
    WrappingBitrateEstimator& operator=(const WrappingBitrateEstimator&) =
        delete;

    ~WrappingBitrateEstimator() override;

    void IncomingPacket(int64_t arrival_time_ms,
                        size_t payload_size,
                        const RTPHeader& header) override;

    void Process() override;

    int64_t TimeUntilNextProcess() override;

    void OnRttUpdate(int64_t avg_rtt_ms, int64_t max_rtt_ms) override;

    void RemoveStream(unsigned int ssrc) override;

    bool LatestEstimate(std::vector<unsigned int>* ssrcs,
                        unsigned int* bitrate_bps) const override;

    void SetMinBitrate(int min_bitrate_bps) override;

   private:
    void PickEstimatorFromHeader(const RTPHeader& header)
        RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_);
    void PickEstimator() RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_);
    RemoteBitrateObserver* observer_;
    Clock* const clock_;
    mutable Mutex mutex_;
    std::unique_ptr<RemoteBitrateEstimator> rbe_;
    bool using_absolute_send_time_;
    uint32_t packets_since_absolute_send_time_;
    int min_bitrate_bps_;
  };

  const FieldTrialBasedConfig field_trial_config_;
  RembThrottler remb_throttler_;
  WrappingBitrateEstimator remote_bitrate_estimator_;
  RemoteEstimatorProxy remote_estimator_proxy_;
};

}  // namespace webrtc

#endif  // MODULES_CONGESTION_CONTROLLER_INCLUDE_RECEIVE_SIDE_CONGESTION_CONTROLLER_H_
