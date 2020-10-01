/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/events/rtc_event_dtls_writable_state.h"

#include "absl/memory/memory.h"

namespace webrtc {

RtcEventDtlsWritableState::RtcEventDtlsWritableState(bool writable)
    : writable_(writable) {}

RtcEventDtlsWritableState::RtcEventDtlsWritableState(
    const RtcEventDtlsWritableState& other)
    : RtcEvent(other.timestamp_us_), writable_(other.writable_) {}

RtcEventDtlsWritableState::~RtcEventDtlsWritableState() = default;

RtcEvent::Type RtcEventDtlsWritableState::GetType() const {
  return RtcEvent::Type::DtlsWritableState;
}

bool RtcEventDtlsWritableState::IsConfigEvent() const {
  return false;
}

std::unique_ptr<RtcEventDtlsWritableState> RtcEventDtlsWritableState::Copy()
    const {
  return absl::WrapUnique<RtcEventDtlsWritableState>(
      new RtcEventDtlsWritableState(*this));
}

}  // namespace webrtc
