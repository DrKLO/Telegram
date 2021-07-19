/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_CANDIDATE_H_
#define API_CANDIDATE_H_

#include <limits.h>
#include <stdint.h>

#include <algorithm>
#include <string>

#include "rtc_base/checks.h"
#include "rtc_base/network_constants.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/system/rtc_export.h"

namespace cricket {

// Candidate for ICE based connection discovery.
// TODO(phoglund): remove things in here that are not needed in the public API.

class RTC_EXPORT Candidate {
 public:
  Candidate();
  // TODO(pthatcher): Match the ordering and param list as per RFC 5245
  // candidate-attribute syntax. http://tools.ietf.org/html/rfc5245#section-15.1
  Candidate(int component,
            const std::string& protocol,
            const rtc::SocketAddress& address,
            uint32_t priority,
            const std::string& username,
            const std::string& password,
            const std::string& type,
            uint32_t generation,
            const std::string& foundation,
            uint16_t network_id = 0,
            uint16_t network_cost = 0);
  Candidate(const Candidate&);
  ~Candidate();

  const std::string& id() const { return id_; }
  void set_id(const std::string& id) { id_ = id; }

  int component() const { return component_; }
  void set_component(int component) { component_ = component; }

  const std::string& protocol() const { return protocol_; }
  void set_protocol(const std::string& protocol) { protocol_ = protocol; }

  // The protocol used to talk to relay.
  const std::string& relay_protocol() const { return relay_protocol_; }
  void set_relay_protocol(const std::string& protocol) {
    relay_protocol_ = protocol;
  }

  const rtc::SocketAddress& address() const { return address_; }
  void set_address(const rtc::SocketAddress& address) { address_ = address; }

  uint32_t priority() const { return priority_; }
  void set_priority(const uint32_t priority) { priority_ = priority; }

  // TODO(pthatcher): Remove once Chromium's jingle/glue/utils.cc
  // doesn't use it.
  // Maps old preference (which was 0.0-1.0) to match priority (which
  // is 0-2^32-1) to to match RFC 5245, section 4.1.2.1.  Also see
  // https://docs.google.com/a/google.com/document/d/
  // 1iNQDiwDKMh0NQOrCqbj3DKKRT0Dn5_5UJYhmZO-t7Uc/edit
  float preference() const {
    // The preference value is clamped to two decimal precision.
    return static_cast<float>(((priority_ >> 24) * 100 / 127) / 100.0);
  }

  // TODO(pthatcher): Remove once Chromium's jingle/glue/utils.cc
  // doesn't use it.
  void set_preference(float preference) {
    // Limiting priority to UINT_MAX when value exceeds uint32_t max.
    // This can happen for e.g. when preference = 3.
    uint64_t prio_val = static_cast<uint64_t>(preference * 127) << 24;
    priority_ = static_cast<uint32_t>(
        std::min(prio_val, static_cast<uint64_t>(UINT_MAX)));
  }

  // TODO(honghaiz): Change to usernameFragment or ufrag.
  const std::string& username() const { return username_; }
  void set_username(const std::string& username) { username_ = username; }

  const std::string& password() const { return password_; }
  void set_password(const std::string& password) { password_ = password; }

  const std::string& type() const { return type_; }
  void set_type(const std::string& type) { type_ = type; }

  const std::string& network_name() const { return network_name_; }
  void set_network_name(const std::string& network_name) {
    network_name_ = network_name;
  }

  rtc::AdapterType network_type() const { return network_type_; }
  void set_network_type(rtc::AdapterType network_type) {
    network_type_ = network_type;
  }

  // Candidates in a new generation replace those in the old generation.
  uint32_t generation() const { return generation_; }
  void set_generation(uint32_t generation) { generation_ = generation; }

  // |network_cost| measures the cost/penalty of using this candidate. A network
  // cost of 0 indicates this candidate can be used freely. A value of
  // rtc::kNetworkCostMax indicates it should be used only as the last resort.
  void set_network_cost(uint16_t network_cost) {
    RTC_DCHECK_LE(network_cost, rtc::kNetworkCostMax);
    network_cost_ = network_cost;
  }
  uint16_t network_cost() const { return network_cost_; }

  // An ID assigned to the network hosting the candidate.
  uint16_t network_id() const { return network_id_; }
  void set_network_id(uint16_t network_id) { network_id_ = network_id; }

  const std::string& foundation() const { return foundation_; }
  void set_foundation(const std::string& foundation) {
    foundation_ = foundation;
  }

  const rtc::SocketAddress& related_address() const { return related_address_; }
  void set_related_address(const rtc::SocketAddress& related_address) {
    related_address_ = related_address;
  }
  const std::string& tcptype() const { return tcptype_; }
  void set_tcptype(const std::string& tcptype) { tcptype_ = tcptype; }

  // The name of the transport channel of this candidate.
  // TODO(phoglund): remove.
  const std::string& transport_name() const { return transport_name_; }
  void set_transport_name(const std::string& transport_name) {
    transport_name_ = transport_name;
  }

  // The URL of the ICE server which this candidate is gathered from.
  const std::string& url() const { return url_; }
  void set_url(const std::string& url) { url_ = url; }

  // Determines whether this candidate is equivalent to the given one.
  bool IsEquivalent(const Candidate& c) const;

  // Determines whether this candidate can be considered equivalent to the
  // given one when looking for a matching candidate to remove.
  bool MatchesForRemoval(const Candidate& c) const;

  std::string ToString() const { return ToStringInternal(false); }

  std::string ToSensitiveString() const { return ToStringInternal(true); }

  uint32_t GetPriority(uint32_t type_preference,
                       int network_adapter_preference,
                       int relay_preference) const;

  bool operator==(const Candidate& o) const;
  bool operator!=(const Candidate& o) const;

  // Returns a sanitized copy configured by the given booleans. If
  // |use_host_address| is true, the returned copy has its IP removed from
  // |address()|, which leads |address()| to be a hostname address. If
  // |filter_related_address|, the returned copy has its related address reset
  // to the wildcard address (i.e. 0.0.0.0 for IPv4 and :: for IPv6). Note that
  // setting both booleans to false returns an identical copy to the original
  // candidate.
  Candidate ToSanitizedCopy(bool use_hostname_address,
                            bool filter_related_address) const;

 private:
  std::string ToStringInternal(bool sensitive) const;

  std::string id_;
  int component_;
  std::string protocol_;
  std::string relay_protocol_;
  rtc::SocketAddress address_;
  uint32_t priority_;
  std::string username_;
  std::string password_;
  std::string type_;
  std::string network_name_;
  rtc::AdapterType network_type_;
  uint32_t generation_;
  std::string foundation_;
  rtc::SocketAddress related_address_;
  std::string tcptype_;
  std::string transport_name_;
  uint16_t network_id_;
  uint16_t network_cost_;
  std::string url_;
};

}  // namespace cricket

#endif  // API_CANDIDATE_H_
