/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/events/rtc_event_bwe_update_delay_based.h"

#include "absl/memory/memory.h"
#include "api/network_state_predictor.h"

namespace webrtc {

constexpr RtcEventDefinition<RtcEventBweUpdateDelayBased,
                             LoggedBweDelayBasedUpdate,
                             int32_t,
                             BandwidthUsage>
    RtcEventBweUpdateDelayBased::definition_;

RtcEventBweUpdateDelayBased::RtcEventBweUpdateDelayBased(
    int32_t bitrate_bps,
    BandwidthUsage detector_state)
    : bitrate_bps_(bitrate_bps), detector_state_(detector_state) {}

RtcEventBweUpdateDelayBased::RtcEventBweUpdateDelayBased(
    const RtcEventBweUpdateDelayBased& other)
    : RtcEvent(other.timestamp_us_),
      bitrate_bps_(other.bitrate_bps_),
      detector_state_(other.detector_state_) {}

RtcEventBweUpdateDelayBased::~RtcEventBweUpdateDelayBased() = default;

std::unique_ptr<RtcEventBweUpdateDelayBased> RtcEventBweUpdateDelayBased::Copy()
    const {
  return absl::WrapUnique<RtcEventBweUpdateDelayBased>(
      new RtcEventBweUpdateDelayBased(*this));
}

}  // namespace webrtc
