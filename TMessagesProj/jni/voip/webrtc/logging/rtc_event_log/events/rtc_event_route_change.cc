/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/events/rtc_event_route_change.h"

#include "absl/memory/memory.h"

namespace webrtc {

RtcEventRouteChange::RtcEventRouteChange(bool connected, uint32_t overhead)
    : connected_(connected), overhead_(overhead) {}

RtcEventRouteChange::RtcEventRouteChange(const RtcEventRouteChange& other)
    : RtcEvent(other.timestamp_us_),
      connected_(other.connected_),
      overhead_(other.overhead_) {}

RtcEventRouteChange::~RtcEventRouteChange() = default;

std::unique_ptr<RtcEventRouteChange> RtcEventRouteChange::Copy() const {
  return absl::WrapUnique<RtcEventRouteChange>(new RtcEventRouteChange(*this));
}

}  // namespace webrtc
