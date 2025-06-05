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

#include "absl/base/attributes.h"
#include "absl/strings/string_view.h"
#include "rtc_base/checks.h"
#include "rtc_base/network_constants.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/system/rtc_export.h"

namespace webrtc {
enum class IceCandidateType : int { kHost, kSrflx, kPrflx, kRelay };
}  // namespace webrtc

namespace cricket {

// TODO(tommi): These are temporarily here, moved from `port.h` and will
// eventually be removed once we use enums instead of strings for these values.
RTC_EXPORT extern const absl::string_view LOCAL_PORT_TYPE;
RTC_EXPORT extern const absl::string_view STUN_PORT_TYPE;
RTC_EXPORT extern const absl::string_view PRFLX_PORT_TYPE;
RTC_EXPORT extern const absl::string_view RELAY_PORT_TYPE;

// TURN servers are limited to 32 in accordance with
// https://w3c.github.io/webrtc-pc/#dom-rtcconfiguration-iceservers
static constexpr size_t kMaxTurnServers = 32;

// Candidate for ICE based connection discovery.
// TODO(phoglund): remove things in here that are not needed in the public API.

class RTC_EXPORT Candidate {
 public:
  Candidate();
  // TODO(pthatcher): Match the ordering and param list as per RFC 5245
  // candidate-attribute syntax. http://tools.ietf.org/html/rfc5245#section-15.1
  Candidate(int component,
            absl::string_view protocol,
            const rtc::SocketAddress& address,
            uint32_t priority,
            absl::string_view username,
            absl::string_view password,
            absl::string_view type ABSL_ATTRIBUTE_LIFETIME_BOUND,
            uint32_t generation,
            absl::string_view foundation,
            uint16_t network_id = 0,
            uint16_t network_cost = 0);
  Candidate(const Candidate&);
  ~Candidate();

  // 8 character long randomized ID string for logging purposes.
  const std::string& id() const { return id_; }
  // Generates a new, 8 character long, id.
  void generate_id();
  // TODO(tommi): Callers should use generate_id(). Remove.
  [[deprecated]] void set_id(absl::string_view id) { Assign(id_, id); }

  int component() const { return component_; }
  void set_component(int component) { component_ = component; }

  const std::string& protocol() const { return protocol_; }
  void set_protocol(absl::string_view protocol) { Assign(protocol_, protocol); }

  // The protocol used to talk to relay.
  const std::string& relay_protocol() const { return relay_protocol_; }
  void set_relay_protocol(absl::string_view protocol) {
    Assign(relay_protocol_, protocol);
  }

  const rtc::SocketAddress& address() const { return address_; }
  void set_address(const rtc::SocketAddress& address) { address_ = address; }

  uint32_t priority() const { return priority_; }
  void set_priority(const uint32_t priority) { priority_ = priority; }

  // TODO(honghaiz): Change to usernameFragment or ufrag.
  const std::string& username() const { return username_; }
  void set_username(absl::string_view username) { Assign(username_, username); }

  const std::string& password() const { return password_; }
  void set_password(absl::string_view password) { Assign(password_, password); }

  const std::string& type() const { return type_; }

  // Returns the name of the candidate type as specified in
  // https://datatracker.ietf.org/doc/html/rfc5245#section-15.1
  absl::string_view type_name() const;

  // Setting the type requires a constant string (e.g.
  // cricket::LOCAL_PORT_TYPE). The type should really be an enum rather than a
  // string, but until we make that change the lifetime attribute helps us lock
  // things down. See also the `Port` class.
  void set_type(absl::string_view type ABSL_ATTRIBUTE_LIFETIME_BOUND) {
    Assign(type_, type);
  }

  // Provide these simple checkers to abstract away dependency on the port types
  // that are currently defined outside of Candidate. This will ease the change
  // from the string type to an enum.
  bool is_local() const;
  bool is_stun() const;
  bool is_prflx() const;
  bool is_relay() const;

  // Returns the type preference, a value between 0-126 inclusive, with 0 being
  // the lowest preference value, as described in RFC 5245.
  // https://datatracker.ietf.org/doc/html/rfc5245#section-4.1.2.1
  int type_preference() const {
    // From https://datatracker.ietf.org/doc/html/rfc5245#section-4.1.4 :
    // It is RECOMMENDED that default candidates be chosen based on the
    // likelihood of those candidates to work with the peer that is being
    // contacted.
    // I.e. it is recommended that relayed > reflexive > host.
    if (is_local())
      return 1;  // Host.
    if (is_stun())
      return 2;  // Reflexive.
    if (is_relay())
      return 3;  // Relayed.
    return 0;    // Unknown, lowest preference.
  }

  const std::string& network_name() const { return network_name_; }
  void set_network_name(absl::string_view network_name) {
    Assign(network_name_, network_name);
  }

  rtc::AdapterType network_type() const { return network_type_; }
  void set_network_type(rtc::AdapterType network_type) {
    network_type_ = network_type;
  }

  rtc::AdapterType underlying_type_for_vpn() const {
    return underlying_type_for_vpn_;
  }
  void set_underlying_type_for_vpn(rtc::AdapterType network_type) {
    underlying_type_for_vpn_ = network_type;
  }

  // Candidates in a new generation replace those in the old generation.
  uint32_t generation() const { return generation_; }
  void set_generation(uint32_t generation) { generation_ = generation; }

  // `network_cost` measures the cost/penalty of using this candidate. A network
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
  void set_foundation(absl::string_view foundation) {
    Assign(foundation_, foundation);
  }

  const rtc::SocketAddress& related_address() const { return related_address_; }
  void set_related_address(const rtc::SocketAddress& related_address) {
    related_address_ = related_address;
  }
  const std::string& tcptype() const { return tcptype_; }
  void set_tcptype(absl::string_view tcptype) { Assign(tcptype_, tcptype); }

  // The name of the transport channel of this candidate.
  // TODO(phoglund): remove.
  const std::string& transport_name() const { return transport_name_; }
  void set_transport_name(absl::string_view transport_name) {
    Assign(transport_name_, transport_name);
  }

  // The URL of the ICE server which this candidate is gathered from.
  const std::string& url() const { return url_; }
  void set_url(absl::string_view url) { Assign(url_, url); }

  // Determines whether this candidate is equivalent to the given one.
  bool IsEquivalent(const Candidate& c) const;

  // Determines whether this candidate can be considered equivalent to the
  // given one when looking for a matching candidate to remove.
  bool MatchesForRemoval(const Candidate& c) const;

  std::string ToString() const { return ToStringInternal(false); }

  std::string ToSensitiveString() const { return ToStringInternal(true); }

  uint32_t GetPriority(uint32_t type_preference,
                       int network_adapter_preference,
                       int relay_preference,
                       bool adjust_local_preference) const;

  bool operator==(const Candidate& o) const;
  bool operator!=(const Candidate& o) const;

  // Returns a sanitized copy configured by the given booleans. If
  // `use_host_address` is true, the returned copy has its IP removed from
  // `address()`, which leads `address()` to be a hostname address. If
  // `filter_related_address`, the returned copy has its related address reset
  // to the wildcard address (i.e. 0.0.0.0 for IPv4 and :: for IPv6). Note that
  // setting both booleans to false returns an identical copy to the original
  // candidate.
  Candidate ToSanitizedCopy(bool use_hostname_address,
                            bool filter_related_address) const;

 private:
  // TODO(bugs.webrtc.org/13220): With C++17, we get a std::string assignment
  // operator accepting any object implicitly convertible to std::string_view,
  // and then we don't need this workaround.
  static void Assign(std::string& s, absl::string_view view);
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
  rtc::AdapterType underlying_type_for_vpn_;
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
