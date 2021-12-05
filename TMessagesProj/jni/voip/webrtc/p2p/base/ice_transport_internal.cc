/*
 *  Copyright 2017 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/ice_transport_internal.h"

#include "p2p/base/p2p_constants.h"

namespace cricket {

using webrtc::RTCError;
using webrtc::RTCErrorType;

RTCError VerifyCandidate(const Candidate& cand) {
  // No address zero.
  if (cand.address().IsNil() || cand.address().IsAnyIP()) {
    return RTCError(RTCErrorType::INVALID_PARAMETER,
                    "candidate has address of zero");
  }

  // Disallow all ports below 1024, except for 80 and 443 on public addresses.
  int port = cand.address().port();
  if (cand.protocol() == cricket::TCP_PROTOCOL_NAME &&
      (cand.tcptype() == cricket::TCPTYPE_ACTIVE_STR || port == 0)) {
    // Expected for active-only candidates per
    // http://tools.ietf.org/html/rfc6544#section-4.5 so no error.
    // Libjingle clients emit port 0, in "active" mode.
    return RTCError::OK();
  }
  if (port < 1024) {
    if ((port != 80) && (port != 443)) {
      return RTCError(RTCErrorType::INVALID_PARAMETER,
                      "candidate has port below 1024, but not 80 or 443");
    }

    if (cand.address().IsPrivateIP()) {
      return RTCError(
          RTCErrorType::INVALID_PARAMETER,
          "candidate has port of 80 or 443 with private IP address");
    }
  }

  return RTCError::OK();
}

RTCError VerifyCandidates(const Candidates& candidates) {
  for (const Candidate& candidate : candidates) {
    RTCError error = VerifyCandidate(candidate);
    if (!error.ok())
      return error;
  }
  return RTCError::OK();
}

IceConfig::IceConfig() = default;

IceConfig::IceConfig(int receiving_timeout_ms,
                     int backup_connection_ping_interval,
                     ContinualGatheringPolicy gathering_policy,
                     bool prioritize_most_likely_candidate_pairs,
                     int stable_writable_connection_ping_interval_ms,
                     bool presume_writable_when_fully_relayed,
                     int regather_on_failed_networks_interval_ms,
                     int receiving_switching_delay_ms)
    : receiving_timeout(receiving_timeout_ms),
      backup_connection_ping_interval(backup_connection_ping_interval),
      continual_gathering_policy(gathering_policy),
      prioritize_most_likely_candidate_pairs(
          prioritize_most_likely_candidate_pairs),
      stable_writable_connection_ping_interval(
          stable_writable_connection_ping_interval_ms),
      presume_writable_when_fully_relayed(presume_writable_when_fully_relayed),
      regather_on_failed_networks_interval(
          regather_on_failed_networks_interval_ms),
      receiving_switching_delay(receiving_switching_delay_ms) {}

IceConfig::~IceConfig() = default;

int IceConfig::receiving_timeout_or_default() const {
  return receiving_timeout.value_or(RECEIVING_TIMEOUT);
}
int IceConfig::backup_connection_ping_interval_or_default() const {
  return backup_connection_ping_interval.value_or(
      BACKUP_CONNECTION_PING_INTERVAL);
}
int IceConfig::stable_writable_connection_ping_interval_or_default() const {
  return stable_writable_connection_ping_interval.value_or(
      STRONG_AND_STABLE_WRITABLE_CONNECTION_PING_INTERVAL);
}
int IceConfig::regather_on_failed_networks_interval_or_default() const {
  return regather_on_failed_networks_interval.value_or(
      REGATHER_ON_FAILED_NETWORKS_INTERVAL);
}
int IceConfig::receiving_switching_delay_or_default() const {
  return receiving_switching_delay.value_or(RECEIVING_SWITCHING_DELAY);
}
int IceConfig::ice_check_interval_strong_connectivity_or_default() const {
  return ice_check_interval_strong_connectivity.value_or(STRONG_PING_INTERVAL);
}
int IceConfig::ice_check_interval_weak_connectivity_or_default() const {
  return ice_check_interval_weak_connectivity.value_or(WEAK_PING_INTERVAL);
}
int IceConfig::ice_check_min_interval_or_default() const {
  return ice_check_min_interval.value_or(-1);
}
int IceConfig::ice_unwritable_timeout_or_default() const {
  return ice_unwritable_timeout.value_or(CONNECTION_WRITE_CONNECT_TIMEOUT);
}
int IceConfig::ice_unwritable_min_checks_or_default() const {
  return ice_unwritable_min_checks.value_or(CONNECTION_WRITE_CONNECT_FAILURES);
}
int IceConfig::ice_inactive_timeout_or_default() const {
  return ice_inactive_timeout.value_or(CONNECTION_WRITE_TIMEOUT);
}
int IceConfig::stun_keepalive_interval_or_default() const {
  return stun_keepalive_interval.value_or(STUN_KEEPALIVE_INTERVAL);
}

IceTransportInternal::IceTransportInternal() = default;

IceTransportInternal::~IceTransportInternal() = default;

void IceTransportInternal::SetIceCredentials(const std::string& ice_ufrag,
                                             const std::string& ice_pwd) {
  SetIceParameters(IceParameters(ice_ufrag, ice_pwd, false));
}

void IceTransportInternal::SetRemoteIceCredentials(const std::string& ice_ufrag,
                                                   const std::string& ice_pwd) {
  SetRemoteIceParameters(IceParameters(ice_ufrag, ice_pwd, false));
}

}  // namespace cricket
