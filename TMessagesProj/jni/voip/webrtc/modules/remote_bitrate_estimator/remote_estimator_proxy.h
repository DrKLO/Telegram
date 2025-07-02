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

#include <deque>
#include <functional>
#include <memory>
#include <vector>

#include "absl/types/optional.h"
#include "api/field_trials_view.h"
#include "api/rtp_headers.h"
#include "api/transport/network_control.h"
#include "api/units/data_size.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "modules/remote_bitrate_estimator/packet_arrival_map.h"
#include "modules/rtp_rtcp/source/rtcp_packet.h"
#include "modules/rtp_rtcp/source/rtcp_packet/transport_feedback.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "rtc_base/numerics/sequence_number_unwrapper.h"
#include "rtc_base/synchronization/mutex.h"

namespace webrtc {

// Class used when send-side BWE is enabled: This proxy is instantiated on the
// receive side. It buffers a number of receive timestamps and then sends
// transport feedback messages back too the send side.
class RemoteEstimatorProxy {
 public:
  // Used for sending transport feedback messages when send side
  // BWE is used.
  using TransportFeedbackSender = std::function<void(
      std::vector<std::unique_ptr<rtcp::RtcpPacket>> packets)>;
  RemoteEstimatorProxy(TransportFeedbackSender feedback_sender,
                       NetworkStateEstimator* network_state_estimator);
  ~RemoteEstimatorProxy();

  void IncomingPacket(const RtpPacketReceived& packet);

  // Sends periodic feedback if it is time to send it.
  // Returns time until next call to Process should be made.
  TimeDelta Process(Timestamp now);

  void OnBitrateChanged(int bitrate);
  void SetTransportOverhead(DataSize overhead_per_packet);

 private:
  void MaybeCullOldPackets(int64_t sequence_number, Timestamp arrival_time)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(&lock_);
  void SendPeriodicFeedbacks() RTC_EXCLUSIVE_LOCKS_REQUIRED(&lock_);
  void SendFeedbackOnRequest(int64_t sequence_number,
                             const FeedbackRequest& feedback_request)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(&lock_);

  // Returns a Transport Feedback packet with information about as many packets
  // that has been received between [`begin_sequence_number_incl`,
  // `end_sequence_number_excl`) that can fit in it. If `is_periodic_update`,
  // this represents sending a periodic feedback message, which will make it
  // update the `periodic_window_start_seq_` variable with the first packet that
  // was not included in the feedback packet, so that the next update can
  // continue from that sequence number.
  //
  // If no incoming packets were added, nullptr is returned.
  //
  // `include_timestamps` decide if the returned TransportFeedback should
  // include timestamps.
  std::unique_ptr<rtcp::TransportFeedback> MaybeBuildFeedbackPacket(
      bool include_timestamps,
      int64_t begin_sequence_number_inclusive,
      int64_t end_sequence_number_exclusive,
      bool is_periodic_update) RTC_EXCLUSIVE_LOCKS_REQUIRED(&lock_);

  const TransportFeedbackSender feedback_sender_;
  Timestamp last_process_time_;

  Mutex lock_;
  //  `network_state_estimator_` may be null.
  NetworkStateEstimator* const network_state_estimator_
      RTC_PT_GUARDED_BY(&lock_);
  uint32_t media_ssrc_ RTC_GUARDED_BY(&lock_);
  uint8_t feedback_packet_count_ RTC_GUARDED_BY(&lock_);
  SeqNumUnwrapper<uint16_t> unwrapper_ RTC_GUARDED_BY(&lock_);
  DataSize packet_overhead_ RTC_GUARDED_BY(&lock_);

  // The next sequence number that should be the start sequence number during
  // periodic reporting. Will be absl::nullopt before the first seen packet.
  absl::optional<int64_t> periodic_window_start_seq_ RTC_GUARDED_BY(&lock_);

  // Packet arrival times, by sequence number.
  PacketArrivalTimeMap packet_arrival_times_ RTC_GUARDED_BY(&lock_);

  TimeDelta send_interval_ RTC_GUARDED_BY(&lock_);
  bool send_periodic_feedback_ RTC_GUARDED_BY(&lock_);

  // Unwraps absolute send times.
  uint32_t previous_abs_send_time_ RTC_GUARDED_BY(&lock_);
  Timestamp abs_send_timestamp_ RTC_GUARDED_BY(&lock_);
  Timestamp last_arrival_time_with_abs_send_time_ RTC_GUARDED_BY(&lock_);
};

}  // namespace webrtc

#endif  //  MODULES_REMOTE_BITRATE_ESTIMATOR_REMOTE_ESTIMATOR_PROXY_H_
