/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/events/rtc_event_dtls_transport_state.h"

#include "absl/memory/memory.h"

namespace webrtc {

RtcEventDtlsTransportState::RtcEventDtlsTransportState(DtlsTransportState state)
    : dtls_transport_state_(state) {}

RtcEventDtlsTransportState::RtcEventDtlsTransportState(
    const RtcEventDtlsTransportState& other)
    : RtcEvent(other.timestamp_us_),
      dtls_transport_state_(other.dtls_transport_state_) {}

RtcEventDtlsTransportState::~RtcEventDtlsTransportState() = default;

RtcEvent::Type RtcEventDtlsTransportState::GetType() const {
  return RtcEvent::Type::DtlsTransportState;
}

bool RtcEventDtlsTransportState::IsConfigEvent() const {
  return false;
}

std::unique_ptr<RtcEventDtlsTransportState> RtcEventDtlsTransportState::Copy()
    const {
  return absl::WrapUnique<RtcEventDtlsTransportState>(
      new RtcEventDtlsTransportState(*this));
}

}  // namespace webrtc
