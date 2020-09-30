/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_ICE_CANDIDATE_PAIR_CONFIG_H_
#define LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_ICE_CANDIDATE_PAIR_CONFIG_H_

#include <stdint.h>

#include <memory>

#include "api/rtc_event_log/rtc_event.h"

namespace webrtc {

enum class IceCandidatePairConfigType {
  kAdded,
  kUpdated,
  kDestroyed,
  kSelected,
  kNumValues,
};

// TODO(qingsi): Change the names of candidate types to "host", "srflx", "prflx"
// and "relay" after the naming is spec-compliant in the signaling part
enum class IceCandidateType {
  kUnknown,
  kLocal,
  kStun,
  kPrflx,
  kRelay,
  kNumValues,
};

enum class IceCandidatePairProtocol {
  kUnknown,
  kUdp,
  kTcp,
  kSsltcp,
  kTls,
  kNumValues,
};

enum class IceCandidatePairAddressFamily {
  kUnknown,
  kIpv4,
  kIpv6,
  kNumValues,
};

enum class IceCandidateNetworkType {
  kUnknown,
  kEthernet,
  kLoopback,
  kWifi,
  kVpn,
  kCellular,
  kNumValues,
};

class IceCandidatePairDescription {
 public:
  IceCandidatePairDescription();
  explicit IceCandidatePairDescription(
      const IceCandidatePairDescription& other);

  ~IceCandidatePairDescription();

  IceCandidateType local_candidate_type;
  IceCandidatePairProtocol local_relay_protocol;
  IceCandidateNetworkType local_network_type;
  IceCandidatePairAddressFamily local_address_family;
  IceCandidateType remote_candidate_type;
  IceCandidatePairAddressFamily remote_address_family;
  IceCandidatePairProtocol candidate_pair_protocol;
};

class RtcEventIceCandidatePairConfig final : public RtcEvent {
 public:
  RtcEventIceCandidatePairConfig(
      IceCandidatePairConfigType type,
      uint32_t candidate_pair_id,
      const IceCandidatePairDescription& candidate_pair_desc);

  ~RtcEventIceCandidatePairConfig() override;

  Type GetType() const override;

  bool IsConfigEvent() const override;

  std::unique_ptr<RtcEventIceCandidatePairConfig> Copy() const;

  IceCandidatePairConfigType type() const { return type_; }
  uint32_t candidate_pair_id() const { return candidate_pair_id_; }
  const IceCandidatePairDescription& candidate_pair_desc() const {
    return candidate_pair_desc_;
  }

 private:
  RtcEventIceCandidatePairConfig(const RtcEventIceCandidatePairConfig& other);

  const IceCandidatePairConfigType type_;
  const uint32_t candidate_pair_id_;
  const IceCandidatePairDescription candidate_pair_desc_;
};

}  // namespace webrtc

#endif  // LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_ICE_CANDIDATE_PAIR_CONFIG_H_
