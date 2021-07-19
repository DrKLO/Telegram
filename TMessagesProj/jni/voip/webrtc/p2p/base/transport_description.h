/*
 *  Copyright 2012 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_TRANSPORT_DESCRIPTION_H_
#define P2P_BASE_TRANSPORT_DESCRIPTION_H_

#include <memory>
#include <string>
#include <vector>

#include "absl/algorithm/container.h"
#include "absl/types/optional.h"
#include "api/rtc_error.h"
#include "p2p/base/p2p_constants.h"
#include "rtc_base/ssl_fingerprint.h"
#include "rtc_base/system/rtc_export.h"

namespace cricket {

// SEC_ENABLED and SEC_REQUIRED should only be used if the session
// was negotiated over TLS, to protect the inline crypto material
// exchange.
// SEC_DISABLED: No crypto in outgoing offer, ignore any supplied crypto.
// SEC_ENABLED:  Crypto in outgoing offer and answer (if supplied in offer).
// SEC_REQUIRED: Crypto in outgoing offer and answer. Fail any offer with absent
//               or unsupported crypto.
// TODO(deadbeef): Remove this or rename it to something more appropriate, like
// SdesPolicy.
enum SecurePolicy { SEC_DISABLED, SEC_ENABLED, SEC_REQUIRED };

// Whether our side of the call is driving the negotiation, or the other side.
enum IceRole { ICEROLE_CONTROLLING = 0, ICEROLE_CONTROLLED, ICEROLE_UNKNOWN };

// ICE RFC 5245 implementation type.
enum IceMode {
  ICEMODE_FULL,  // As defined in http://tools.ietf.org/html/rfc5245#section-4.1
  ICEMODE_LITE   // As defined in http://tools.ietf.org/html/rfc5245#section-4.2
};

// RFC 4145 - http://tools.ietf.org/html/rfc4145#section-4
// 'active':  The endpoint will initiate an outgoing connection.
// 'passive': The endpoint will accept an incoming connection.
// 'actpass': The endpoint is willing to accept an incoming
//            connection or to initiate an outgoing connection.
enum ConnectionRole {
  CONNECTIONROLE_NONE = 0,
  CONNECTIONROLE_ACTIVE,
  CONNECTIONROLE_PASSIVE,
  CONNECTIONROLE_ACTPASS,
  CONNECTIONROLE_HOLDCONN,
};

struct IceParameters {
  // Constructs an IceParameters from a user-provided ufrag/pwd combination.
  // Returns a SyntaxError if the ufrag or pwd are malformed.
  static RTC_EXPORT webrtc::RTCErrorOr<IceParameters> Parse(
      absl::string_view raw_ufrag,
      absl::string_view raw_pwd);

  // TODO(honghaiz): Include ICE mode in this structure to match the ORTC
  // struct:
  // http://ortc.org/wp-content/uploads/2016/03/ortc.html#idl-def-RTCIceParameters
  std::string ufrag;
  std::string pwd;
  bool renomination = false;
  IceParameters() = default;
  IceParameters(const std::string& ice_ufrag,
                const std::string& ice_pwd,
                bool ice_renomination)
      : ufrag(ice_ufrag), pwd(ice_pwd), renomination(ice_renomination) {}

  bool operator==(const IceParameters& other) const {
    return ufrag == other.ufrag && pwd == other.pwd &&
           renomination == other.renomination;
  }
  bool operator!=(const IceParameters& other) const {
    return !(*this == other);
  }

  // Validate IceParameters, returns a SyntaxError if the ufrag or pwd are
  // malformed.
  webrtc::RTCError Validate() const;
};

extern const char CONNECTIONROLE_ACTIVE_STR[];
extern const char CONNECTIONROLE_PASSIVE_STR[];
extern const char CONNECTIONROLE_ACTPASS_STR[];
extern const char CONNECTIONROLE_HOLDCONN_STR[];

constexpr auto* ICE_OPTION_TRICKLE = "trickle";
constexpr auto* ICE_OPTION_RENOMINATION = "renomination";

bool StringToConnectionRole(const std::string& role_str, ConnectionRole* role);
bool ConnectionRoleToString(const ConnectionRole& role, std::string* role_str);

struct TransportDescription {
  TransportDescription();
  TransportDescription(const std::vector<std::string>& transport_options,
                       const std::string& ice_ufrag,
                       const std::string& ice_pwd,
                       IceMode ice_mode,
                       ConnectionRole role,
                       const rtc::SSLFingerprint* identity_fingerprint);
  TransportDescription(const std::string& ice_ufrag,
                       const std::string& ice_pwd);
  TransportDescription(const TransportDescription& from);
  ~TransportDescription();

  TransportDescription& operator=(const TransportDescription& from);

  // TODO(deadbeef): Rename to HasIceOption, etc.
  bool HasOption(const std::string& option) const {
    return absl::c_linear_search(transport_options, option);
  }
  void AddOption(const std::string& option) {
    transport_options.push_back(option);
  }
  bool secure() const { return identity_fingerprint != nullptr; }

  IceParameters GetIceParameters() const {
    return IceParameters(ice_ufrag, ice_pwd,
                         HasOption(ICE_OPTION_RENOMINATION));
  }

  static rtc::SSLFingerprint* CopyFingerprint(const rtc::SSLFingerprint* from) {
    if (!from)
      return NULL;

    return new rtc::SSLFingerprint(*from);
  }

  // These are actually ICE options (appearing in the ice-options attribute in
  // SDP).
  // TODO(deadbeef): Rename to ice_options.
  std::vector<std::string> transport_options;
  std::string ice_ufrag;
  std::string ice_pwd;
  IceMode ice_mode;
  ConnectionRole connection_role;

  std::unique_ptr<rtc::SSLFingerprint> identity_fingerprint;
};

}  // namespace cricket

#endif  // P2P_BASE_TRANSPORT_DESCRIPTION_H_
