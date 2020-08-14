/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/events/rtc_event_probe_result_failure.h"

#include "absl/memory/memory.h"

namespace webrtc {

RtcEventProbeResultFailure::RtcEventProbeResultFailure(
    int32_t id,
    ProbeFailureReason failure_reason)
    : id_(id), failure_reason_(failure_reason) {}

RtcEventProbeResultFailure::RtcEventProbeResultFailure(
    const RtcEventProbeResultFailure& other)
    : RtcEvent(other.timestamp_us_),
      id_(other.id_),
      failure_reason_(other.failure_reason_) {}

RtcEvent::Type RtcEventProbeResultFailure::GetType() const {
  return RtcEvent::Type::ProbeResultFailure;
}

bool RtcEventProbeResultFailure::IsConfigEvent() const {
  return false;
}

std::unique_ptr<RtcEventProbeResultFailure> RtcEventProbeResultFailure::Copy()
    const {
  return absl::WrapUnique<RtcEventProbeResultFailure>(
      new RtcEventProbeResultFailure(*this));
}

}  // namespace webrtc
