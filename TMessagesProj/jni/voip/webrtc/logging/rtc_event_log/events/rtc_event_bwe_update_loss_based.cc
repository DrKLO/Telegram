/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/events/rtc_event_bwe_update_loss_based.h"

#include "absl/memory/memory.h"

namespace webrtc {

RtcEventBweUpdateLossBased::RtcEventBweUpdateLossBased(int32_t bitrate_bps,
                                                       uint8_t fraction_loss,
                                                       int32_t total_packets)
    : bitrate_bps_(bitrate_bps),
      fraction_loss_(fraction_loss),
      total_packets_(total_packets) {}

RtcEventBweUpdateLossBased::RtcEventBweUpdateLossBased(
    const RtcEventBweUpdateLossBased& other)
    : RtcEvent(other.timestamp_us_),
      bitrate_bps_(other.bitrate_bps_),
      fraction_loss_(other.fraction_loss_),
      total_packets_(other.total_packets_) {}

RtcEventBweUpdateLossBased::~RtcEventBweUpdateLossBased() = default;

std::unique_ptr<RtcEventBweUpdateLossBased> RtcEventBweUpdateLossBased::Copy()
    const {
  return absl::WrapUnique<RtcEventBweUpdateLossBased>(
      new RtcEventBweUpdateLossBased(*this));
}

}  // namespace webrtc
