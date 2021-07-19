/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/port.h"

#include <math.h>

#include <algorithm>
#include <memory>
#include <utility>
#include <vector>

#include "absl/algorithm/container.h"
#include "absl/strings/match.h"
#include "p2p/base/connection.h"
#include "p2p/base/port_allocator.h"
#include "rtc_base/checks.h"
#include "rtc_base/crc32.h"
#include "rtc_base/helpers.h"
#include "rtc_base/logging.h"
#include "rtc_base/mdns_responder_interface.h"
#include "rtc_base/message_digest.h"
#include "rtc_base/network.h"
#include "rtc_base/numerics/safe_minmax.h"
#include "rtc_base/string_encode.h"
#include "rtc_base/string_utils.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/third_party/base64/base64.h"
#include "system_wrappers/include/field_trial.h"

namespace {

rtc::PacketInfoProtocolType ConvertProtocolTypeToPacketInfoProtocolType(
    cricket::ProtocolType type) {
  switch (type) {
    case cricket::ProtocolType::PROTO_UDP:
      return rtc::PacketInfoProtocolType::kUdp;
    case cricket::ProtocolType::PROTO_TCP:
      return rtc::PacketInfoProtocolType::kTcp;
    case cricket::ProtocolType::PROTO_SSLTCP:
      return rtc::PacketInfoProtocolType::kSsltcp;
    case cricket::ProtocolType::PROTO_TLS:
      return rtc::PacketInfoProtocolType::kTls;
    default:
      return rtc::PacketInfoProtocolType::kUnknown;
  }
}

// The delay before we begin checking if this port is useless. We set
// it to a little higher than a total STUN timeout.
const int kPortTimeoutDelay = cricket::STUN_TOTAL_TIMEOUT + 5000;

}  // namespace

namespace cricket {

using webrtc::RTCError;
using webrtc::RTCErrorType;

// TODO(ronghuawu): Use "local", "srflx", "prflx" and "relay". But this requires
// the signaling part be updated correspondingly as well.
const char LOCAL_PORT_TYPE[] = "local";
const char STUN_PORT_TYPE[] = "stun";
const char PRFLX_PORT_TYPE[] = "prflx";
const char RELAY_PORT_TYPE[] = "relay";

static const char* const PROTO_NAMES[] = {UDP_PROTOCOL_NAME, TCP_PROTOCOL_NAME,
                                          SSLTCP_PROTOCOL_NAME,
                                          TLS_PROTOCOL_NAME};

const char* ProtoToString(ProtocolType proto) {
  return PROTO_NAMES[proto];
}

bool StringToProto(const char* value, ProtocolType* proto) {
  for (size_t i = 0; i <= PROTO_LAST; ++i) {
    if (absl::EqualsIgnoreCase(PROTO_NAMES[i], value)) {
      *proto = static_cast<ProtocolType>(i);
      return true;
    }
  }
  return false;
}

// RFC 6544, TCP candidate encoding rules.
const int DISCARD_PORT = 9;
const char TCPTYPE_ACTIVE_STR[] = "active";
const char TCPTYPE_PASSIVE_STR[] = "passive";
const char TCPTYPE_SIMOPEN_STR[] = "so";

std::string Port::ComputeFoundation(const std::string& type,
                                    const std::string& protocol,
                                    const std::string& relay_protocol,
                                    const rtc::SocketAddress& base_address) {
  rtc::StringBuilder sb;
  sb << type << base_address.ipaddr().ToString() << protocol << relay_protocol;
  return rtc::ToString(rtc::ComputeCrc32(sb.Release()));
}

CandidateStats::CandidateStats() = default;

CandidateStats::CandidateStats(const CandidateStats&) = default;

CandidateStats::CandidateStats(Candidate candidate) {
  this->candidate = candidate;
}

CandidateStats::~CandidateStats() = default;

Port::Port(rtc::Thread* thread,
           const std::string& type,
           rtc::PacketSocketFactory* factory,
           rtc::Network* network,
           const std::string& username_fragment,
           const std::string& password)
    : thread_(thread),
      factory_(factory),
      type_(type),
      send_retransmit_count_attribute_(false),
      network_(network),
      min_port_(0),
      max_port_(0),
      component_(ICE_CANDIDATE_COMPONENT_DEFAULT),
      generation_(0),
      ice_username_fragment_(username_fragment),
      password_(password),
      timeout_delay_(kPortTimeoutDelay),
      enable_port_packets_(false),
      ice_role_(ICEROLE_UNKNOWN),
      tiebreaker_(0),
      shared_socket_(true),
      weak_factory_(this) {
  RTC_DCHECK(factory_ != NULL);
  Construct();
}

Port::Port(rtc::Thread* thread,
           const std::string& type,
           rtc::PacketSocketFactory* factory,
           rtc::Network* network,
           uint16_t min_port,
           uint16_t max_port,
           const std::string& username_fragment,
           const std::string& password)
    : thread_(thread),
      factory_(factory),
      type_(type),
      send_retransmit_count_attribute_(false),
      network_(network),
      min_port_(min_port),
      max_port_(max_port),
      component_(ICE_CANDIDATE_COMPONENT_DEFAULT),
      generation_(0),
      ice_username_fragment_(username_fragment),
      password_(password),
      timeout_delay_(kPortTimeoutDelay),
      enable_port_packets_(false),
      ice_role_(ICEROLE_UNKNOWN),
      tiebreaker_(0),
      shared_socket_(false),
      weak_factory_(this) {
  RTC_DCHECK(factory_ != NULL);
  Construct();
}

void Port::Construct() {
  // TODO(pthatcher): Remove this old behavior once we're sure no one
  // relies on it.  If the username_fragment and password are empty,
  // we should just create one.
  if (ice_username_fragment_.empty()) {
    RTC_DCHECK(password_.empty());
    ice_username_fragment_ = rtc::CreateRandomString(ICE_UFRAG_LENGTH);
    password_ = rtc::CreateRandomString(ICE_PWD_LENGTH);
  }
  network_->SignalTypeChanged.connect(this, &Port::OnNetworkTypeChanged);
  network_cost_ = network_->GetCost();

  thread_->PostDelayed(RTC_FROM_HERE, timeout_delay_, this,
                       MSG_DESTROY_IF_DEAD);
  RTC_LOG(LS_INFO) << ToString() << ": Port created with network cost "
                   << network_cost_;
}

Port::~Port() {
  RTC_DCHECK_RUN_ON(thread_);
  CancelPendingTasks();

  // Delete all of the remaining connections.  We copy the list up front
  // because each deletion will cause it to be modified.

  std::vector<Connection*> list;

  AddressMap::iterator iter = connections_.begin();
  while (iter != connections_.end()) {
    list.push_back(iter->second);
    ++iter;
  }

  for (uint32_t i = 0; i < list.size(); i++)
    delete list[i];
}

const std::string& Port::Type() const {
  return type_;
}
rtc::Network* Port::Network() const {
  return network_;
}

IceRole Port::GetIceRole() const {
  return ice_role_;
}

void Port::SetIceRole(IceRole role) {
  ice_role_ = role;
}

void Port::SetIceTiebreaker(uint64_t tiebreaker) {
  tiebreaker_ = tiebreaker;
}
uint64_t Port::IceTiebreaker() const {
  return tiebreaker_;
}

bool Port::SharedSocket() const {
  return shared_socket_;
}

void Port::SetIceParameters(int component,
                            const std::string& username_fragment,
                            const std::string& password) {
  component_ = component;
  ice_username_fragment_ = username_fragment;
  password_ = password;
  for (Candidate& c : candidates_) {
    c.set_component(component);
    c.set_username(username_fragment);
    c.set_password(password);
  }
}

const std::vector<Candidate>& Port::Candidates() const {
  return candidates_;
}

Connection* Port::GetConnection(const rtc::SocketAddress& remote_addr) {
  AddressMap::const_iterator iter = connections_.find(remote_addr);
  if (iter != connections_.end())
    return iter->second;
  else
    return NULL;
}

void Port::AddAddress(const rtc::SocketAddress& address,
                      const rtc::SocketAddress& base_address,
                      const rtc::SocketAddress& related_address,
                      const std::string& protocol,
                      const std::string& relay_protocol,
                      const std::string& tcptype,
                      const std::string& type,
                      uint32_t type_preference,
                      uint32_t relay_preference,
                      const std::string& url,
                      bool is_final) {
  if (protocol == TCP_PROTOCOL_NAME && type == LOCAL_PORT_TYPE) {
    RTC_DCHECK(!tcptype.empty());
  }

  std::string foundation =
      ComputeFoundation(type, protocol, relay_protocol, base_address);
  Candidate c(component_, protocol, address, 0U, username_fragment(), password_,
              type, generation_, foundation, network_->id(), network_cost_);
  c.set_priority(
      c.GetPriority(type_preference, network_->preference(), relay_preference));
  c.set_relay_protocol(relay_protocol);
  c.set_tcptype(tcptype);
  c.set_network_name(network_->name());
  c.set_network_type(network_->type());
  c.set_url(url);
  c.set_related_address(related_address);

  bool pending = MaybeObfuscateAddress(&c, type, is_final);

  if (!pending) {
    FinishAddingAddress(c, is_final);
  }
}

bool Port::MaybeObfuscateAddress(Candidate* c,
                                 const std::string& type,
                                 bool is_final) {
  // TODO(bugs.webrtc.org/9723): Use a config to control the feature of IP
  // handling with mDNS.
  if (network_->GetMdnsResponder() == nullptr) {
    return false;
  }
  if (type != LOCAL_PORT_TYPE) {
    return false;
  }

  auto copy = *c;
  auto weak_ptr = weak_factory_.GetWeakPtr();
  auto callback = [weak_ptr, copy, is_final](const rtc::IPAddress& addr,
                                             const std::string& name) mutable {
    RTC_DCHECK(copy.address().ipaddr() == addr);
    rtc::SocketAddress hostname_address(name, copy.address().port());
    // In Port and Connection, we need the IP address information to
    // correctly handle the update of candidate type to prflx. The removal
    // of IP address when signaling this candidate will take place in
    // BasicPortAllocatorSession::OnCandidateReady, via SanitizeCandidate.
    hostname_address.SetResolvedIP(addr);
    copy.set_address(hostname_address);
    copy.set_related_address(rtc::SocketAddress());
    if (weak_ptr != nullptr) {
      weak_ptr->set_mdns_name_registration_status(
          MdnsNameRegistrationStatus::kCompleted);
      weak_ptr->FinishAddingAddress(copy, is_final);
    }
  };
  set_mdns_name_registration_status(MdnsNameRegistrationStatus::kInProgress);
  network_->GetMdnsResponder()->CreateNameForAddress(copy.address().ipaddr(),
                                                     callback);
  return true;
}

void Port::FinishAddingAddress(const Candidate& c, bool is_final) {
  candidates_.push_back(c);
  SignalCandidateReady(this, c);

  PostAddAddress(is_final);
}

void Port::PostAddAddress(bool is_final) {
  if (is_final) {
    SignalPortComplete(this);
  }
}

void Port::AddOrReplaceConnection(Connection* conn) {
  auto ret = connections_.insert(
      std::make_pair(conn->remote_candidate().address(), conn));
  // If there is a different connection on the same remote address, replace
  // it with the new one and destroy the old one.
  if (ret.second == false && ret.first->second != conn) {
    RTC_LOG(LS_WARNING)
        << ToString()
        << ": A new connection was created on an existing remote address. "
           "New remote candidate: "
        << conn->remote_candidate().ToSensitiveString();
    ret.first->second->SignalDestroyed.disconnect(this);
    ret.first->second->Destroy();
    ret.first->second = conn;
  }
  conn->SignalDestroyed.connect(this, &Port::OnConnectionDestroyed);
  SignalConnectionCreated(this, conn);
}

void Port::OnReadPacket(const char* data,
                        size_t size,
                        const rtc::SocketAddress& addr,
                        ProtocolType proto) {
  // If the user has enabled port packets, just hand this over.
  if (enable_port_packets_) {
    SignalReadPacket(this, data, size, addr);
    return;
  }

  // If this is an authenticated STUN request, then signal unknown address and
  // send back a proper binding response.
  std::unique_ptr<IceMessage> msg;
  std::string remote_username;
  if (!GetStunMessage(data, size, addr, &msg, &remote_username)) {
    RTC_LOG(LS_ERROR) << ToString()
                      << ": Received non-STUN packet from unknown address: "
                      << addr.ToSensitiveString();
  } else if (!msg) {
    // STUN message handled already
  } else if (msg->type() == STUN_BINDING_REQUEST) {
    RTC_LOG(LS_INFO) << "Received " << StunMethodToString(msg->type())
                     << " id=" << rtc::hex_encode(msg->transaction_id())
                     << " from unknown address " << addr.ToSensitiveString();
    // We need to signal an unknown address before we handle any role conflict
    // below. Otherwise there would be no candidate pair and TURN entry created
    // to send the error response in case of a role conflict.
    SignalUnknownAddress(this, addr, proto, msg.get(), remote_username, false);
    // Check for role conflicts.
    if (!MaybeIceRoleConflict(addr, msg.get(), remote_username)) {
      RTC_LOG(LS_INFO) << "Received conflicting role from the peer.";
      return;
    }
  } else if (msg->type() == GOOG_PING_REQUEST) {
    // This is a PING sent to a connection that was destroyed.
    // Send back that this is the case and a authenticated BINDING
    // is needed.
    SendBindingErrorResponse(msg.get(), addr, STUN_ERROR_BAD_REQUEST,
                             STUN_ERROR_REASON_BAD_REQUEST);
  } else {
    // NOTE(tschmelcher): STUN_BINDING_RESPONSE is benign. It occurs if we
    // pruned a connection for this port while it had STUN requests in flight,
    // because we then get back responses for them, which this code correctly
    // does not handle.
    if (msg->type() != STUN_BINDING_RESPONSE &&
        msg->type() != GOOG_PING_RESPONSE &&
        msg->type() != GOOG_PING_ERROR_RESPONSE) {
      RTC_LOG(LS_ERROR) << ToString()
                        << ": Received unexpected STUN message type: "
                        << msg->type() << " from unknown address: "
                        << addr.ToSensitiveString();
    }
  }
}

void Port::OnReadyToSend() {
  AddressMap::iterator iter = connections_.begin();
  for (; iter != connections_.end(); ++iter) {
    iter->second->OnReadyToSend();
  }
}

size_t Port::AddPrflxCandidate(const Candidate& local) {
  candidates_.push_back(local);
  return (candidates_.size() - 1);
}

bool Port::GetStunMessage(const char* data,
                          size_t size,
                          const rtc::SocketAddress& addr,
                          std::unique_ptr<IceMessage>* out_msg,
                          std::string* out_username) {
  // NOTE: This could clearly be optimized to avoid allocating any memory.
  //       However, at the data rates we'll be looking at on the client side,
  //       this probably isn't worth worrying about.
  RTC_DCHECK(out_msg != NULL);
  RTC_DCHECK(out_username != NULL);
  out_username->clear();

  // Don't bother parsing the packet if we can tell it's not STUN.
  // In ICE mode, all STUN packets will have a valid fingerprint.
  // Except GOOG_PING_REQUEST/RESPONSE that does not send fingerprint.
  int types[] = {GOOG_PING_REQUEST, GOOG_PING_RESPONSE,
                 GOOG_PING_ERROR_RESPONSE};
  if (!StunMessage::IsStunMethod(types, data, size) &&
      !StunMessage::ValidateFingerprint(data, size)) {
    return false;
  }

  // Parse the request message.  If the packet is not a complete and correct
  // STUN message, then ignore it.
  std::unique_ptr<IceMessage> stun_msg(new IceMessage());
  rtc::ByteBufferReader buf(data, size);
  if (!stun_msg->Read(&buf) || (buf.Length() > 0)) {
    return false;
  }

  // Get list of attributes in the "comprehension-required" range that were not
  // comprehended. If one or more is found, the behavior differs based on the
  // type of the incoming message; see below.
  std::vector<uint16_t> unknown_attributes =
      stun_msg->GetNonComprehendedAttributes();

  if (stun_msg->type() == STUN_BINDING_REQUEST) {
    // Check for the presence of USERNAME and MESSAGE-INTEGRITY (if ICE) first.
    // If not present, fail with a 400 Bad Request.
    if (!stun_msg->GetByteString(STUN_ATTR_USERNAME) ||
        !stun_msg->GetByteString(STUN_ATTR_MESSAGE_INTEGRITY)) {
      RTC_LOG(LS_ERROR) << ToString() << ": Received "
                        << StunMethodToString(stun_msg->type())
                        << " without username/M-I from: "
                        << addr.ToSensitiveString();
      SendBindingErrorResponse(stun_msg.get(), addr, STUN_ERROR_BAD_REQUEST,
                               STUN_ERROR_REASON_BAD_REQUEST);
      return true;
    }

    // If the username is bad or unknown, fail with a 401 Unauthorized.
    std::string local_ufrag;
    std::string remote_ufrag;
    if (!ParseStunUsername(stun_msg.get(), &local_ufrag, &remote_ufrag) ||
        local_ufrag != username_fragment()) {
      RTC_LOG(LS_ERROR) << ToString() << ": Received "
                        << StunMethodToString(stun_msg->type())
                        << " with bad local username " << local_ufrag
                        << " from " << addr.ToSensitiveString();
      SendBindingErrorResponse(stun_msg.get(), addr, STUN_ERROR_UNAUTHORIZED,
                               STUN_ERROR_REASON_UNAUTHORIZED);
      return true;
    }

    // If ICE, and the MESSAGE-INTEGRITY is bad, fail with a 401 Unauthorized
    if (stun_msg->ValidateMessageIntegrity(password_) !=
        StunMessage::IntegrityStatus::kIntegrityOk) {
      RTC_LOG(LS_ERROR) << ToString() << ": Received "
                        << StunMethodToString(stun_msg->type())
                        << " with bad M-I from " << addr.ToSensitiveString()
                        << ", password_=" << password_;
      SendBindingErrorResponse(stun_msg.get(), addr, STUN_ERROR_UNAUTHORIZED,
                               STUN_ERROR_REASON_UNAUTHORIZED);
      return true;
    }

    // If a request contains unknown comprehension-required attributes, reply
    // with an error. See RFC5389 section 7.3.1.
    if (!unknown_attributes.empty()) {
      SendUnknownAttributesErrorResponse(stun_msg.get(), addr,
                                         unknown_attributes);
      return true;
    }

    out_username->assign(remote_ufrag);
  } else if ((stun_msg->type() == STUN_BINDING_RESPONSE) ||
             (stun_msg->type() == STUN_BINDING_ERROR_RESPONSE)) {
    if (stun_msg->type() == STUN_BINDING_ERROR_RESPONSE) {
      if (const StunErrorCodeAttribute* error_code = stun_msg->GetErrorCode()) {
        RTC_LOG(LS_ERROR) << ToString() << ": Received "
                          << StunMethodToString(stun_msg->type())
                          << ": class=" << error_code->eclass()
                          << " number=" << error_code->number() << " reason='"
                          << error_code->reason() << "' from "
                          << addr.ToSensitiveString();
        // Return message to allow error-specific processing
      } else {
        RTC_LOG(LS_ERROR) << ToString() << ": Received "
                          << StunMethodToString(stun_msg->type())
                          << " without a error code from "
                          << addr.ToSensitiveString();
        return true;
      }
    }
    // If a response contains unknown comprehension-required attributes, it's
    // simply discarded and the transaction is considered failed. See RFC5389
    // sections 7.3.3 and 7.3.4.
    if (!unknown_attributes.empty()) {
      RTC_LOG(LS_ERROR) << ToString()
                        << ": Discarding STUN response due to unknown "
                           "comprehension-required attribute";
      return true;
    }
    // NOTE: Username should not be used in verifying response messages.
    out_username->clear();
  } else if (stun_msg->type() == STUN_BINDING_INDICATION) {
    RTC_LOG(LS_VERBOSE) << ToString() << ": Received "
                        << StunMethodToString(stun_msg->type()) << ": from "
                        << addr.ToSensitiveString();
    out_username->clear();

    // If an indication contains unknown comprehension-required attributes,[]
    // it's simply discarded. See RFC5389 section 7.3.2.
    if (!unknown_attributes.empty()) {
      RTC_LOG(LS_ERROR) << ToString()
                        << ": Discarding STUN indication due to "
                           "unknown comprehension-required attribute";
      return true;
    }
    // No stun attributes will be verified, if it's stun indication message.
    // Returning from end of the this method.
  } else if (stun_msg->type() == GOOG_PING_REQUEST) {
    if (stun_msg->ValidateMessageIntegrity(password_) !=
        StunMessage::IntegrityStatus::kIntegrityOk) {
      RTC_LOG(LS_ERROR) << ToString() << ": Received "
                        << StunMethodToString(stun_msg->type())
                        << " with bad M-I from " << addr.ToSensitiveString()
                        << ", password_=" << password_;
      SendBindingErrorResponse(stun_msg.get(), addr, STUN_ERROR_UNAUTHORIZED,
                               STUN_ERROR_REASON_UNAUTHORIZED);
      return true;
    }
    RTC_LOG(LS_VERBOSE) << ToString() << ": Received "
                        << StunMethodToString(stun_msg->type()) << " from "
                        << addr.ToSensitiveString();
    out_username->clear();
  } else if (stun_msg->type() == GOOG_PING_RESPONSE ||
             stun_msg->type() == GOOG_PING_ERROR_RESPONSE) {
    // note: the MessageIntegrity32 will be verified in Connection.cc
    RTC_LOG(LS_VERBOSE) << ToString() << ": Received "
                        << StunMethodToString(stun_msg->type()) << " from "
                        << addr.ToSensitiveString();
    out_username->clear();
  } else {
    RTC_LOG(LS_ERROR) << ToString()
                      << ": Received STUN packet with invalid type ("
                      << stun_msg->type() << ") from "
                      << addr.ToSensitiveString();
    return true;
  }

  // Return the STUN message found.
  *out_msg = std::move(stun_msg);
  return true;
}

bool Port::IsCompatibleAddress(const rtc::SocketAddress& addr) {
  // Get a representative IP for the Network this port is configured to use.
  rtc::IPAddress ip = network_->GetBestIP();
  // We use single-stack sockets, so families must match.
  if (addr.family() != ip.family()) {
    return false;
  }
  // Link-local IPv6 ports can only connect to other link-local IPv6 ports.
  if (ip.family() == AF_INET6 &&
      (IPIsLinkLocal(ip) != IPIsLinkLocal(addr.ipaddr()))) {
    return false;
  }
  return true;
}

rtc::DiffServCodePoint Port::StunDscpValue() const {
  // By default, inherit from whatever the MediaChannel sends.
  return rtc::DSCP_NO_CHANGE;
}

void Port::set_timeout_delay(int delay) {
  RTC_DCHECK_RUN_ON(thread_);
  // Although this method is meant to only be used by tests, some downstream
  // projects have started using it. Ideally we should update our tests to not
  // require to modify this state and instead use a testing harness that allows
  // adjusting the clock and then just use the kPortTimeoutDelay constant
  // directly.
  timeout_delay_ = delay;
}

bool Port::ParseStunUsername(const StunMessage* stun_msg,
                             std::string* local_ufrag,
                             std::string* remote_ufrag) const {
  // The packet must include a username that either begins or ends with our
  // fragment.  It should begin with our fragment if it is a request and it
  // should end with our fragment if it is a response.
  local_ufrag->clear();
  remote_ufrag->clear();
  const StunByteStringAttribute* username_attr =
      stun_msg->GetByteString(STUN_ATTR_USERNAME);
  if (username_attr == NULL)
    return false;

  // RFRAG:LFRAG
  const std::string username = username_attr->GetString();
  size_t colon_pos = username.find(':');
  if (colon_pos == std::string::npos) {
    return false;
  }

  *local_ufrag = username.substr(0, colon_pos);
  *remote_ufrag = username.substr(colon_pos + 1, username.size());
  return true;
}

bool Port::MaybeIceRoleConflict(const rtc::SocketAddress& addr,
                                IceMessage* stun_msg,
                                const std::string& remote_ufrag) {
  // Validate ICE_CONTROLLING or ICE_CONTROLLED attributes.
  bool ret = true;
  IceRole remote_ice_role = ICEROLE_UNKNOWN;
  uint64_t remote_tiebreaker = 0;
  const StunUInt64Attribute* stun_attr =
      stun_msg->GetUInt64(STUN_ATTR_ICE_CONTROLLING);
  if (stun_attr) {
    remote_ice_role = ICEROLE_CONTROLLING;
    remote_tiebreaker = stun_attr->value();
  }

  // If |remote_ufrag| is same as port local username fragment and
  // tie breaker value received in the ping message matches port
  // tiebreaker value this must be a loopback call.
  // We will treat this as valid scenario.
  if (remote_ice_role == ICEROLE_CONTROLLING &&
      username_fragment() == remote_ufrag &&
      remote_tiebreaker == IceTiebreaker()) {
    return true;
  }

  stun_attr = stun_msg->GetUInt64(STUN_ATTR_ICE_CONTROLLED);
  if (stun_attr) {
    remote_ice_role = ICEROLE_CONTROLLED;
    remote_tiebreaker = stun_attr->value();
  }

  switch (ice_role_) {
    case ICEROLE_CONTROLLING:
      if (ICEROLE_CONTROLLING == remote_ice_role) {
        if (remote_tiebreaker >= tiebreaker_) {
          SignalRoleConflict(this);
        } else {
          // Send Role Conflict (487) error response.
          SendBindingErrorResponse(stun_msg, addr, STUN_ERROR_ROLE_CONFLICT,
                                   STUN_ERROR_REASON_ROLE_CONFLICT);
          ret = false;
        }
      }
      break;
    case ICEROLE_CONTROLLED:
      if (ICEROLE_CONTROLLED == remote_ice_role) {
        if (remote_tiebreaker < tiebreaker_) {
          SignalRoleConflict(this);
        } else {
          // Send Role Conflict (487) error response.
          SendBindingErrorResponse(stun_msg, addr, STUN_ERROR_ROLE_CONFLICT,
                                   STUN_ERROR_REASON_ROLE_CONFLICT);
          ret = false;
        }
      }
      break;
    default:
      RTC_NOTREACHED();
  }
  return ret;
}

void Port::CreateStunUsername(const std::string& remote_username,
                              std::string* stun_username_attr_str) const {
  stun_username_attr_str->clear();
  *stun_username_attr_str = remote_username;
  stun_username_attr_str->append(":");
  stun_username_attr_str->append(username_fragment());
}

bool Port::HandleIncomingPacket(rtc::AsyncPacketSocket* socket,
                                const char* data,
                                size_t size,
                                const rtc::SocketAddress& remote_addr,
                                int64_t packet_time_us) {
  RTC_NOTREACHED();
  return false;
}

bool Port::CanHandleIncomingPacketsFrom(const rtc::SocketAddress&) const {
  return false;
}

void Port::SendBindingErrorResponse(StunMessage* request,
                                    const rtc::SocketAddress& addr,
                                    int error_code,
                                    const std::string& reason) {
  RTC_DCHECK(request->type() == STUN_BINDING_REQUEST ||
             request->type() == GOOG_PING_REQUEST);

  // Fill in the response message.
  StunMessage response;
  if (request->type() == STUN_BINDING_REQUEST) {
    response.SetType(STUN_BINDING_ERROR_RESPONSE);
  } else {
    response.SetType(GOOG_PING_ERROR_RESPONSE);
  }
  response.SetTransactionID(request->transaction_id());

  // When doing GICE, we need to write out the error code incorrectly to
  // maintain backwards compatiblility.
  auto error_attr = StunAttribute::CreateErrorCode();
  error_attr->SetCode(error_code);
  error_attr->SetReason(reason);
  response.AddAttribute(std::move(error_attr));

  // Per Section 10.1.2, certain error cases don't get a MESSAGE-INTEGRITY,
  // because we don't have enough information to determine the shared secret.
  if (error_code != STUN_ERROR_BAD_REQUEST &&
      error_code != STUN_ERROR_UNAUTHORIZED &&
      request->type() != GOOG_PING_REQUEST) {
    if (request->type() == STUN_BINDING_REQUEST) {
      response.AddMessageIntegrity(password_);
    } else {
      response.AddMessageIntegrity32(password_);
    }
  }

  if (request->type() == STUN_BINDING_REQUEST) {
    response.AddFingerprint();
  }

  // Send the response message.
  rtc::ByteBufferWriter buf;
  response.Write(&buf);
  rtc::PacketOptions options(StunDscpValue());
  options.info_signaled_after_sent.packet_type =
      rtc::PacketType::kIceConnectivityCheckResponse;
  SendTo(buf.Data(), buf.Length(), addr, options, false);
  RTC_LOG(LS_INFO) << ToString() << ": Sending STUN "
                   << StunMethodToString(response.type())
                   << ": reason=" << reason << " to "
                   << addr.ToSensitiveString();
}

void Port::SendUnknownAttributesErrorResponse(
    StunMessage* request,
    const rtc::SocketAddress& addr,
    const std::vector<uint16_t>& unknown_types) {
  RTC_DCHECK(request->type() == STUN_BINDING_REQUEST);

  // Fill in the response message.
  StunMessage response;
  response.SetType(STUN_BINDING_ERROR_RESPONSE);
  response.SetTransactionID(request->transaction_id());

  auto error_attr = StunAttribute::CreateErrorCode();
  error_attr->SetCode(STUN_ERROR_UNKNOWN_ATTRIBUTE);
  error_attr->SetReason(STUN_ERROR_REASON_UNKNOWN_ATTRIBUTE);
  response.AddAttribute(std::move(error_attr));

  std::unique_ptr<StunUInt16ListAttribute> unknown_attr =
      StunAttribute::CreateUnknownAttributes();
  for (uint16_t type : unknown_types) {
    unknown_attr->AddType(type);
  }
  response.AddAttribute(std::move(unknown_attr));

  response.AddMessageIntegrity(password_);
  response.AddFingerprint();

  // Send the response message.
  rtc::ByteBufferWriter buf;
  response.Write(&buf);
  rtc::PacketOptions options(StunDscpValue());
  options.info_signaled_after_sent.packet_type =
      rtc::PacketType::kIceConnectivityCheckResponse;
  SendTo(buf.Data(), buf.Length(), addr, options, false);
  RTC_LOG(LS_ERROR) << ToString() << ": Sending STUN binding error: reason="
                    << STUN_ERROR_UNKNOWN_ATTRIBUTE << " to "
                    << addr.ToSensitiveString();
}

void Port::KeepAliveUntilPruned() {
  // If it is pruned, we won't bring it up again.
  if (state_ == State::INIT) {
    state_ = State::KEEP_ALIVE_UNTIL_PRUNED;
  }
}

void Port::Prune() {
  state_ = State::PRUNED;
  thread_->Post(RTC_FROM_HERE, this, MSG_DESTROY_IF_DEAD);
}

// Call to stop any currently pending operations from running.
void Port::CancelPendingTasks() {
  RTC_DCHECK_RUN_ON(thread_);
  thread_->Clear(this);
}

void Port::OnMessage(rtc::Message* pmsg) {
  RTC_DCHECK_RUN_ON(thread_);
  RTC_DCHECK(pmsg->message_id == MSG_DESTROY_IF_DEAD);
  bool dead =
      (state_ == State::INIT || state_ == State::PRUNED) &&
      connections_.empty() &&
      rtc::TimeMillis() - last_time_all_connections_removed_ >= timeout_delay_;
  if (dead) {
    Destroy();
  }
}

void Port::SubscribePortDestroyed(
    std::function<void(PortInterface*)> callback) {
  port_destroyed_callback_list_.AddReceiver(callback);
}

void Port::SendPortDestroyed(Port* port) {
  port_destroyed_callback_list_.Send(port);
}
void Port::OnNetworkTypeChanged(const rtc::Network* network) {
  RTC_DCHECK(network == network_);

  UpdateNetworkCost();
}

std::string Port::ToString() const {
  rtc::StringBuilder ss;
  ss << "Port[" << rtc::ToHex(reinterpret_cast<uintptr_t>(this)) << ":"
     << content_name_ << ":" << component_ << ":" << generation_ << ":" << type_
     << ":" << network_->ToString() << "]";
  return ss.Release();
}

// TODO(honghaiz): Make the network cost configurable from user setting.
void Port::UpdateNetworkCost() {
  uint16_t new_cost = network_->GetCost();
  if (network_cost_ == new_cost) {
    return;
  }
  RTC_LOG(LS_INFO) << "Network cost changed from " << network_cost_ << " to "
                   << new_cost
                   << ". Number of candidates created: " << candidates_.size()
                   << ". Number of connections created: "
                   << connections_.size();
  network_cost_ = new_cost;
  for (cricket::Candidate& candidate : candidates_) {
    candidate.set_network_cost(network_cost_);
  }
  // Network cost change will affect the connection selection criteria.
  // Signal the connection state change on each connection to force a
  // re-sort in P2PTransportChannel.
  for (const auto& kv : connections_) {
    Connection* conn = kv.second;
    conn->SignalStateChange(conn);
  }
}

void Port::EnablePortPackets() {
  enable_port_packets_ = true;
}

void Port::OnConnectionDestroyed(Connection* conn) {
  AddressMap::iterator iter =
      connections_.find(conn->remote_candidate().address());
  RTC_DCHECK(iter != connections_.end());
  connections_.erase(iter);
  HandleConnectionDestroyed(conn);

  // Ports time out after all connections fail if it is not marked as
  // "keep alive until pruned."
  // Note: If a new connection is added after this message is posted, but it
  // fails and is removed before kPortTimeoutDelay, then this message will
  // not cause the Port to be destroyed.
  if (connections_.empty()) {
    last_time_all_connections_removed_ = rtc::TimeMillis();
    thread_->PostDelayed(RTC_FROM_HERE, timeout_delay_, this,
                         MSG_DESTROY_IF_DEAD);
  }
}

void Port::Destroy() {
  RTC_DCHECK(connections_.empty());
  RTC_LOG(LS_INFO) << ToString() << ": Port deleted";
  SendPortDestroyed(this);
  delete this;
}

const std::string Port::username_fragment() const {
  return ice_username_fragment_;
}

void Port::CopyPortInformationToPacketInfo(rtc::PacketInfo* info) const {
  info->protocol = ConvertProtocolTypeToPacketInfoProtocolType(GetProtocol());
  info->network_id = Network()->id();
}

}  // namespace cricket
