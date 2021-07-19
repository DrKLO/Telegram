/*
 *  Copyright 2017 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/candidate.h"

#include "rtc_base/helpers.h"
#include "rtc_base/ip_address.h"
#include "rtc_base/strings/string_builder.h"

namespace cricket {

Candidate::Candidate()
    : id_(rtc::CreateRandomString(8)),
      component_(0),
      priority_(0),
      network_type_(rtc::ADAPTER_TYPE_UNKNOWN),
      generation_(0),
      network_id_(0),
      network_cost_(0) {}

Candidate::Candidate(int component,
                     const std::string& protocol,
                     const rtc::SocketAddress& address,
                     uint32_t priority,
                     const std::string& username,
                     const std::string& password,
                     const std::string& type,
                     uint32_t generation,
                     const std::string& foundation,
                     uint16_t network_id,
                     uint16_t network_cost)
    : id_(rtc::CreateRandomString(8)),
      component_(component),
      protocol_(protocol),
      address_(address),
      priority_(priority),
      username_(username),
      password_(password),
      type_(type),
      network_type_(rtc::ADAPTER_TYPE_UNKNOWN),
      generation_(generation),
      foundation_(foundation),
      network_id_(network_id),
      network_cost_(network_cost) {}

Candidate::Candidate(const Candidate&) = default;

Candidate::~Candidate() = default;

bool Candidate::IsEquivalent(const Candidate& c) const {
  // We ignore the network name, since that is just debug information, and
  // the priority and the network cost, since they should be the same if the
  // rest are.
  return (component_ == c.component_) && (protocol_ == c.protocol_) &&
         (address_ == c.address_) && (username_ == c.username_) &&
         (password_ == c.password_) && (type_ == c.type_) &&
         (generation_ == c.generation_) && (foundation_ == c.foundation_) &&
         (related_address_ == c.related_address_) &&
         (network_id_ == c.network_id_);
}

bool Candidate::MatchesForRemoval(const Candidate& c) const {
  return component_ == c.component_ && protocol_ == c.protocol_ &&
         address_ == c.address_;
}

std::string Candidate::ToStringInternal(bool sensitive) const {
  rtc::StringBuilder ost;
  std::string address =
      sensitive ? address_.ToSensitiveString() : address_.ToString();
  ost << "Cand[" << transport_name_ << ":" << foundation_ << ":" << component_
      << ":" << protocol_ << ":" << priority_ << ":" << address << ":" << type_
      << ":" << related_address_.ToString() << ":" << username_ << ":"
      << password_ << ":" << network_id_ << ":" << network_cost_ << ":"
      << generation_ << "]";
  return ost.Release();
}

uint32_t Candidate::GetPriority(uint32_t type_preference,
                                int network_adapter_preference,
                                int relay_preference) const {
  // RFC 5245 - 4.1.2.1.
  // priority = (2^24)*(type preference) +
  //            (2^8)*(local preference) +
  //            (2^0)*(256 - component ID)

  // |local_preference| length is 2 bytes, 0-65535 inclusive.
  // In our implemenation we will partion local_preference into
  //              0                 1
  //       0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
  //      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  //      |  NIC Pref     |    Addr Pref  |
  //      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  // NIC Type - Type of the network adapter e.g. 3G/Wifi/Wired.
  // Addr Pref - Address preference value as per RFC 3484.
  // local preference =  (NIC Type << 8 | Addr_Pref) - relay preference.

  int addr_pref = IPAddressPrecedence(address_.ipaddr());
  int local_preference =
      ((network_adapter_preference << 8) | addr_pref) + relay_preference;

  return (type_preference << 24) | (local_preference << 8) | (256 - component_);
}

bool Candidate::operator==(const Candidate& o) const {
  return id_ == o.id_ && component_ == o.component_ &&
         protocol_ == o.protocol_ && relay_protocol_ == o.relay_protocol_ &&
         address_ == o.address_ && priority_ == o.priority_ &&
         username_ == o.username_ && password_ == o.password_ &&
         type_ == o.type_ && network_name_ == o.network_name_ &&
         network_type_ == o.network_type_ && generation_ == o.generation_ &&
         foundation_ == o.foundation_ &&
         related_address_ == o.related_address_ && tcptype_ == o.tcptype_ &&
         transport_name_ == o.transport_name_ && network_id_ == o.network_id_;
}

bool Candidate::operator!=(const Candidate& o) const {
  return !(*this == o);
}

Candidate Candidate::ToSanitizedCopy(bool use_hostname_address,
                                     bool filter_related_address) const {
  Candidate copy(*this);
  if (use_hostname_address) {
    rtc::SocketAddress hostname_only_addr(address().hostname(),
                                          address().port());
    copy.set_address(hostname_only_addr);
  }
  if (filter_related_address) {
    copy.set_related_address(
        rtc::EmptySocketAddressWithFamily(copy.address().family()));
  }
  return copy;
}

}  // namespace cricket
