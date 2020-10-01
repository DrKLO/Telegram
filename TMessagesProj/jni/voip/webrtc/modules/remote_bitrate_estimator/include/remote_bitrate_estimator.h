/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// This class estimates the incoming available bandwidth.

#ifndef MODULES_REMOTE_BITRATE_ESTIMATOR_INCLUDE_REMOTE_BITRATE_ESTIMATOR_H_
#define MODULES_REMOTE_BITRATE_ESTIMATOR_INCLUDE_REMOTE_BITRATE_ESTIMATOR_H_

#include <map>
#include <memory>
#include <vector>

#include "modules/include/module.h"
#include "modules/include/module_common_types.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/rtp_rtcp/source/rtcp_packet.h"

namespace webrtc {

class Clock;

// RemoteBitrateObserver is used to signal changes in bitrate estimates for
// the incoming streams.
class RemoteBitrateObserver {
 public:
  // Called when a receive channel group has a new bitrate estimate for the
  // incoming streams.
  virtual void OnReceiveBitrateChanged(const std::vector<uint32_t>& ssrcs,
                                       uint32_t bitrate) = 0;

  virtual ~RemoteBitrateObserver() {}
};

class TransportFeedbackSenderInterface {
 public:
  virtual ~TransportFeedbackSenderInterface() = default;

  virtual bool SendCombinedRtcpPacket(
      std::vector<std::unique_ptr<rtcp::RtcpPacket>> packets) = 0;
};

// TODO(holmer): Remove when all implementations have been updated.
struct ReceiveBandwidthEstimatorStats {};

class RemoteBitrateEstimator : public CallStatsObserver, public Module {
 public:
  ~RemoteBitrateEstimator() override {}

  // Called for each incoming packet. Updates the incoming payload bitrate
  // estimate and the over-use detector. If an over-use is detected the
  // remote bitrate estimate will be updated. Note that |payload_size| is the
  // packet size excluding headers.
  // Note that |arrival_time_ms| can be of an arbitrary time base.
  virtual void IncomingPacket(int64_t arrival_time_ms,
                              size_t payload_size,
                              const RTPHeader& header) = 0;

  // Removes all data for |ssrc|.
  virtual void RemoveStream(uint32_t ssrc) = 0;

  // Returns true if a valid estimate exists and sets |bitrate_bps| to the
  // estimated payload bitrate in bits per second. |ssrcs| is the list of ssrcs
  // currently being received and of which the bitrate estimate is based upon.
  virtual bool LatestEstimate(std::vector<uint32_t>* ssrcs,
                              uint32_t* bitrate_bps) const = 0;

  // TODO(holmer): Remove when all implementations have been updated.
  virtual bool GetStats(ReceiveBandwidthEstimatorStats* output) const;

  virtual void SetMinBitrate(int min_bitrate_bps) = 0;

 protected:
  static const int64_t kProcessIntervalMs = 500;
  static const int64_t kStreamTimeOutMs = 2000;
};

inline bool RemoteBitrateEstimator::GetStats(
    ReceiveBandwidthEstimatorStats* output) const {
  return false;
}

}  // namespace webrtc

#endif  // MODULES_REMOTE_BITRATE_ESTIMATOR_INCLUDE_REMOTE_BITRATE_ESTIMATOR_H_
