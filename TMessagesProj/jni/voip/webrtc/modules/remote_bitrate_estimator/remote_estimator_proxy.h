/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_REMOTE_BITRATE_ESTIMATOR_REMOTE_ESTIMATOR_PROXY_H_
#define MODULES_REMOTE_BITRATE_ESTIMATOR_REMOTE_ESTIMATOR_PROXY_H_

#include <map>
#include <vector>

#include "api/transport/network_control.h"
#include "api/transport/webrtc_key_value_config.h"
#include "modules/remote_bitrate_estimator/include/remote_bitrate_estimator.h"
#include "rtc_base/experiments/field_trial_parser.h"
#include "rtc_base/numerics/sequence_number_util.h"
#include "rtc_base/synchronization/mutex.h"

namespace webrtc {

class Clock;
class PacketRouter;
namespace rtcp {
class TransportFeedback;
}

// Class used when send-side BWE is enabled: This proxy is instantiated on the
// receive side. It buffers a number of receive timestamps and then sends
// transport feedback messages back too the send side.

class RemoteEstimatorProxy : public RemoteBitrateEstimator {
 public:
  RemoteEstimatorProxy(Clock* clock,
                       TransportFeedbackSenderInterface* feedback_sender,
                       const WebRtcKeyValueConfig* key_value_config,
                       NetworkStateEstimator* network_state_estimator);
  ~RemoteEstimatorProxy() override;

  void IncomingPacket(int64_t arrival_time_ms,
                      size_t payload_size,
                      const RTPHeader& header) override;
  void RemoveStream(uint32_t ssrc) override {}
  bool LatestEstimate(std::vector<unsigned int>* ssrcs,
                      unsigned int* bitrate_bps) const override;
  void OnRttUpdate(int64_t avg_rtt_ms, int64_t max_rtt_ms) override {}
  void SetMinBitrate(int min_bitrate_bps) override {}
  int64_t TimeUntilNextProcess() override;
  void Process() override;
  void OnBitrateChanged(int bitrate);
  void SetSendPeriodicFeedback(bool send_periodic_feedback);

 private:
  struct TransportWideFeedbackConfig {
    FieldTrialParameter<TimeDelta> back_window{"wind", TimeDelta::Millis(500)};
    FieldTrialParameter<TimeDelta> min_interval{"min", TimeDelta::Millis(50)};
    FieldTrialParameter<TimeDelta> max_interval{"max", TimeDelta::Millis(250)};
    FieldTrialParameter<TimeDelta> default_interval{"def",
                                                    TimeDelta::Millis(100)};
    FieldTrialParameter<double> bandwidth_fraction{"frac", 0.05};
    explicit TransportWideFeedbackConfig(
        const WebRtcKeyValueConfig* key_value_config) {
      ParseFieldTrial({&back_window, &min_interval, &max_interval,
                       &default_interval, &bandwidth_fraction},
                      key_value_config->Lookup(
                          "WebRTC-Bwe-TransportWideFeedbackIntervals"));
    }
  };

  static const int kMaxNumberOfPackets;

  void SendPeriodicFeedbacks() RTC_EXCLUSIVE_LOCKS_REQUIRED(&lock_);
  void SendFeedbackOnRequest(int64_t sequence_number,
                             const FeedbackRequest& feedback_request)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(&lock_);
  static int64_t BuildFeedbackPacket(
      uint8_t feedback_packet_count,
      uint32_t media_ssrc,
      int64_t base_sequence_number,
      std::map<int64_t, int64_t>::const_iterator
          begin_iterator,  // |begin_iterator| is inclusive.
      std::map<int64_t, int64_t>::const_iterator
          end_iterator,  // |end_iterator| is exclusive.
      rtcp::TransportFeedback* feedback_packet);

  Clock* const clock_;
  TransportFeedbackSenderInterface* const feedback_sender_;
  const TransportWideFeedbackConfig send_config_;
  int64_t last_process_time_ms_;

  Mutex lock_;
  //  |network_state_estimator_| may be null.
  NetworkStateEstimator* const network_state_estimator_
      RTC_PT_GUARDED_BY(&lock_);
  uint32_t media_ssrc_ RTC_GUARDED_BY(&lock_);
  uint8_t feedback_packet_count_ RTC_GUARDED_BY(&lock_);
  SeqNumUnwrapper<uint16_t> unwrapper_ RTC_GUARDED_BY(&lock_);
  absl::optional<int64_t> periodic_window_start_seq_ RTC_GUARDED_BY(&lock_);
  // Map unwrapped seq -> time.
  std::map<int64_t, int64_t> packet_arrival_times_ RTC_GUARDED_BY(&lock_);
  int64_t send_interval_ms_ RTC_GUARDED_BY(&lock_);
  bool send_periodic_feedback_ RTC_GUARDED_BY(&lock_);

  // Unwraps absolute send times.
  uint32_t previous_abs_send_time_ RTC_GUARDED_BY(&lock_);
  Timestamp abs_send_timestamp_ RTC_GUARDED_BY(&lock_);
};

}  // namespace webrtc

#endif  //  MODULES_REMOTE_BITRATE_ESTIMATOR_REMOTE_ESTIMATOR_PROXY_H_
