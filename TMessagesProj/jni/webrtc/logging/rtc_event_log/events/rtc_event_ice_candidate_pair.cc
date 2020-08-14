/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/events/rtc_event_ice_candidate_pair.h"

#include "absl/memory/memory.h"

namespace webrtc {

RtcEventIceCandidatePair::RtcEventIceCandidatePair(
    IceCandidatePairEventType type,
    uint32_t candidate_pair_id,
    uint32_t transaction_id)
    : type_(type),
      candidate_pair_id_(candidate_pair_id),
      transaction_id_(transaction_id) {}

RtcEventIceCandidatePair::RtcEventIceCandidatePair(
    const RtcEventIceCandidatePair& other)
    : RtcEvent(other.timestamp_us_),
      type_(other.type_),
      candidate_pair_id_(other.candidate_pair_id_),
      transaction_id_(other.transaction_id_) {}

RtcEventIceCandidatePair::~RtcEventIceCandidatePair() = default;

RtcEvent::Type RtcEventIceCandidatePair::GetType() const {
  return RtcEvent::Type::IceCandidatePairEvent;
}

bool RtcEventIceCandidatePair::IsConfigEvent() const {
  return false;
}

std::unique_ptr<RtcEventIceCandidatePair> RtcEventIceCandidatePair::Copy()
    const {
  return absl::WrapUnique<RtcEventIceCandidatePair>(
      new RtcEventIceCandidatePair(*this));
}

}  // namespace webrtc
