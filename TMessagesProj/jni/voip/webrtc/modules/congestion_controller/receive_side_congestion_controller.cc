/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/congestion_controller/include/receive_side_congestion_controller.h"

#include "api/units/data_rate.h"
#include "modules/pacing/packet_router.h"
#include "modules/remote_bitrate_estimator/include/bwe_defines.h"
#include "modules/remote_bitrate_estimator/remote_bitrate_estimator_abs_send_time.h"
#include "modules/remote_bitrate_estimator/remote_bitrate_estimator_single_stream.h"
#include "rtc_base/logging.h"

namespace webrtc {

namespace {
static const uint32_t kTimeOffsetSwitchThreshold = 30;
}  // namespace

void ReceiveSideCongestionController::OnRttUpdate(int64_t avg_rtt_ms,
                                                  int64_t max_rtt_ms) {
  MutexLock lock(&mutex_);
  rbe_->OnRttUpdate(avg_rtt_ms, max_rtt_ms);
}

void ReceiveSideCongestionController::RemoveStream(uint32_t ssrc) {
  MutexLock lock(&mutex_);
  rbe_->RemoveStream(ssrc);
}

DataRate ReceiveSideCongestionController::LatestReceiveSideEstimate() const {
  MutexLock lock(&mutex_);
  return rbe_->LatestEstimate();
}

void ReceiveSideCongestionController::PickEstimatorFromHeader(
    const RTPHeader& header) {
  if (header.extension.hasAbsoluteSendTime) {
    // If we see AST in header, switch RBE strategy immediately.
    if (!using_absolute_send_time_) {
      RTC_LOG(LS_INFO)
          << "WrappingBitrateEstimator: Switching to absolute send time RBE.";
      using_absolute_send_time_ = true;
      PickEstimator();
    }
    packets_since_absolute_send_time_ = 0;
  } else {
    // When we don't see AST, wait for a few packets before going back to TOF.
    if (using_absolute_send_time_) {
      ++packets_since_absolute_send_time_;
      if (packets_since_absolute_send_time_ >= kTimeOffsetSwitchThreshold) {
        RTC_LOG(LS_INFO)
            << "WrappingBitrateEstimator: Switching to transmission "
               "time offset RBE.";
        using_absolute_send_time_ = false;
        PickEstimator();
      }
    }
  }
}

// Instantiate RBE for Time Offset or Absolute Send Time extensions.
void ReceiveSideCongestionController::PickEstimator() {
  if (using_absolute_send_time_) {
    rbe_ = std::make_unique<RemoteBitrateEstimatorAbsSendTime>(&remb_throttler_,
                                                               &clock_);
  } else {
    rbe_ = std::make_unique<RemoteBitrateEstimatorSingleStream>(
        &remb_throttler_, &clock_);
  }
}

ReceiveSideCongestionController::ReceiveSideCongestionController(
    Clock* clock,
    RemoteEstimatorProxy::TransportFeedbackSender feedback_sender,
    RembThrottler::RembSender remb_sender,
    NetworkStateEstimator* network_state_estimator)
    : clock_(*clock),
      remb_throttler_(std::move(remb_sender), clock),
      remote_estimator_proxy_(std::move(feedback_sender),
                              &field_trial_config_,
                              network_state_estimator),
      rbe_(new RemoteBitrateEstimatorSingleStream(&remb_throttler_, clock)),
      using_absolute_send_time_(false),
      packets_since_absolute_send_time_(0) {}

void ReceiveSideCongestionController::OnReceivedPacket(
    int64_t arrival_time_ms,
    size_t payload_size,
    const RTPHeader& header) {
  remote_estimator_proxy_.IncomingPacket(arrival_time_ms, payload_size, header);
  if (!header.extension.hasTransportSequenceNumber) {
    // Receive-side BWE.
    MutexLock lock(&mutex_);
    PickEstimatorFromHeader(header);
    rbe_->IncomingPacket(arrival_time_ms, payload_size, header);
  }
}

void ReceiveSideCongestionController::SetSendPeriodicFeedback(
    bool send_periodic_feedback) {
  remote_estimator_proxy_.SetSendPeriodicFeedback(send_periodic_feedback);
}

void ReceiveSideCongestionController::OnBitrateChanged(int bitrate_bps) {
  remote_estimator_proxy_.OnBitrateChanged(bitrate_bps);
}

TimeDelta ReceiveSideCongestionController::MaybeProcess() {
  Timestamp now = clock_.CurrentTime();
  mutex_.Lock();
  TimeDelta time_until_rbe = rbe_->Process();
  mutex_.Unlock();
  TimeDelta time_until_rep = remote_estimator_proxy_.Process(now);
  TimeDelta time_until = std::min(time_until_rbe, time_until_rep);
  return std::max(time_until, TimeDelta::Zero());
}

void ReceiveSideCongestionController::SetMaxDesiredReceiveBitrate(
    DataRate bitrate) {
  remb_throttler_.SetMaxDesiredReceiveBitrate(bitrate);
}

void ReceiveSideCongestionController::SetTransportOverhead(
    DataSize overhead_per_packet) {
  remote_estimator_proxy_.SetTransportOverhead(overhead_per_packet);
}

}  // namespace webrtc
