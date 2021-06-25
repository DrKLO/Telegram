/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/congestion_controller/remb_throttler.h"

#include <algorithm>
#include <utility>

namespace webrtc {

namespace {
constexpr TimeDelta kRembSendInterval = TimeDelta::Millis(200);
}  // namespace

RembThrottler::RembThrottler(RembSender remb_sender, Clock* clock)
    : remb_sender_(std::move(remb_sender)),
      clock_(clock),
      last_remb_time_(Timestamp::MinusInfinity()),
      last_send_remb_bitrate_(DataRate::PlusInfinity()),
      max_remb_bitrate_(DataRate::PlusInfinity()) {}

void RembThrottler::OnReceiveBitrateChanged(const std::vector<uint32_t>& ssrcs,
                                            uint32_t bitrate_bps) {
  DataRate receive_bitrate = DataRate::BitsPerSec(bitrate_bps);
  Timestamp now = clock_->CurrentTime();
  {
    MutexLock lock(&mutex_);
    // % threshold for if we should send a new REMB asap.
    const int64_t kSendThresholdPercent = 103;
    if (receive_bitrate * kSendThresholdPercent / 100 >
            last_send_remb_bitrate_ &&
        now < last_remb_time_ + kRembSendInterval) {
      return;
    }
    last_remb_time_ = now;
    last_send_remb_bitrate_ = receive_bitrate;
    receive_bitrate = std::min(last_send_remb_bitrate_, max_remb_bitrate_);
  }
  remb_sender_(receive_bitrate.bps(), ssrcs);
}

void RembThrottler::SetMaxDesiredReceiveBitrate(DataRate bitrate) {
  Timestamp now = clock_->CurrentTime();
  {
    MutexLock lock(&mutex_);
    max_remb_bitrate_ = bitrate;
    if (now - last_remb_time_ < kRembSendInterval &&
        !last_send_remb_bitrate_.IsZero() &&
        last_send_remb_bitrate_ <= max_remb_bitrate_) {
      return;
    }
  }
  remb_sender_(bitrate.bps(), /*ssrcs=*/{});
}

}  // namespace webrtc
