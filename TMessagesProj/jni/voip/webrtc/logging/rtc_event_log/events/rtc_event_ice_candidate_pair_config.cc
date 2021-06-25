/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/events/rtc_event_ice_candidate_pair_config.h"

#include "absl/memory/memory.h"

namespace webrtc {

IceCandidatePairDescription::IceCandidatePairDescription() {
  local_candidate_type = IceCandidateType::kUnknown;
  local_relay_protocol = IceCandidatePairProtocol::kUnknown;
  local_network_type = IceCandidateNetworkType::kUnknown;
  local_address_family = IceCandidatePairAddressFamily::kUnknown;
  remote_candidate_type = IceCandidateType::kUnknown;
  remote_address_family = IceCandidatePairAddressFamily::kUnknown;
  candidate_pair_protocol = IceCandidatePairProtocol::kUnknown;
}

IceCandidatePairDescription::IceCandidatePairDescription(
    const IceCandidatePairDescription& other) {
  local_candidate_type = other.local_candidate_type;
  local_relay_protocol = other.local_relay_protocol;
  local_network_type = other.local_network_type;
  local_address_family = other.local_address_family;
  remote_candidate_type = other.remote_candidate_type;
  remote_address_family = other.remote_address_family;
  candidate_pair_protocol = other.candidate_pair_protocol;
}

IceCandidatePairDescription::~IceCandidatePairDescription() {}

RtcEventIceCandidatePairConfig::RtcEventIceCandidatePairConfig(
    IceCandidatePairConfigType type,
    uint32_t candidate_pair_id,
    const IceCandidatePairDescription& candidate_pair_desc)
    : type_(type),
      candidate_pair_id_(candidate_pair_id),
      candidate_pair_desc_(candidate_pair_desc) {}

RtcEventIceCandidatePairConfig::RtcEventIceCandidatePairConfig(
    const RtcEventIceCandidatePairConfig& other)
    : RtcEvent(other.timestamp_us_),
      type_(other.type_),
      candidate_pair_id_(other.candidate_pair_id_),
      candidate_pair_desc_(other.candidate_pair_desc_) {}

RtcEventIceCandidatePairConfig::~RtcEventIceCandidatePairConfig() = default;

std::unique_ptr<RtcEventIceCandidatePairConfig>
RtcEventIceCandidatePairConfig::Copy() const {
  return absl::WrapUnique<RtcEventIceCandidatePairConfig>(
      new RtcEventIceCandidatePairConfig(*this));
}

}  // namespace webrtc
