/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/events/rtc_event_alr_state.h"

#include "absl/memory/memory.h"

namespace webrtc {

RtcEventAlrState::RtcEventAlrState(bool in_alr) : in_alr_(in_alr) {}

RtcEventAlrState::RtcEventAlrState(const RtcEventAlrState& other)
    : RtcEvent(other.timestamp_us_), in_alr_(other.in_alr_) {}

RtcEventAlrState::~RtcEventAlrState() = default;

std::unique_ptr<RtcEventAlrState> RtcEventAlrState::Copy() const {
  return absl::WrapUnique<RtcEventAlrState>(new RtcEventAlrState(*this));
}

}  // namespace webrtc
