/*
 *  Copyright 2013 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/transport_description.h"

#include "absl/strings/ascii.h"
#include "absl/strings/match.h"
#include "absl/strings/string_view.h"
#include "p2p/base/p2p_constants.h"
#include "rtc_base/arraysize.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"

using webrtc::RTCError;
using webrtc::RTCErrorOr;
using webrtc::RTCErrorType;

namespace cricket {
namespace {

bool IsIceChar(char c) {
  // Note: '-', '=', '#' and '_' are *not* valid ice-chars but temporarily
  // permitted in order to allow external software to upgrade.
  if (c == '-' || c == '=' || c == '#' || c == '_') {
    RTC_LOG(LS_WARNING)
        << "'-', '=', '#' and '-' are not valid ice-char and thus not "
        << "permitted in ufrag or pwd. This is a protocol violation that "
        << "is permitted to allow upgrading but will be rejected in "
        << "the future. See https://crbug.com/1053756";
    return true;
  }
  return absl::ascii_isalnum(c) || c == '+' || c == '/';
}

RTCError ValidateIceUfrag(absl::string_view raw_ufrag) {
  if (!(ICE_UFRAG_MIN_LENGTH <= raw_ufrag.size() &&
        raw_ufrag.size() <= ICE_UFRAG_MAX_LENGTH)) {
    rtc::StringBuilder sb;
    sb << "ICE ufrag must be between " << ICE_UFRAG_MIN_LENGTH << " and "
       << ICE_UFRAG_MAX_LENGTH << " characters long.";
    return RTCError(RTCErrorType::SYNTAX_ERROR, sb.Release());
  }

  if (!absl::c_all_of(raw_ufrag, IsIceChar)) {
    return RTCError(
        RTCErrorType::SYNTAX_ERROR,
        "ICE ufrag must contain only alphanumeric characters, '+', and '/'.");
  }

  return RTCError::OK();
}

RTCError ValidateIcePwd(absl::string_view raw_pwd) {
  if (!(ICE_PWD_MIN_LENGTH <= raw_pwd.size() &&
        raw_pwd.size() <= ICE_PWD_MAX_LENGTH)) {
    rtc::StringBuilder sb;
    sb << "ICE pwd must be between " << ICE_PWD_MIN_LENGTH << " and "
       << ICE_PWD_MAX_LENGTH << " characters long.";
    return RTCError(RTCErrorType::SYNTAX_ERROR, sb.Release());
  }

  if (!absl::c_all_of(raw_pwd, IsIceChar)) {
    return RTCError(
        RTCErrorType::SYNTAX_ERROR,
        "ICE pwd must contain only alphanumeric characters, '+', and '/'.");
  }

  return RTCError::OK();
}

}  // namespace

RTCErrorOr<IceParameters> IceParameters::Parse(absl::string_view raw_ufrag,
                                               absl::string_view raw_pwd) {
  IceParameters parameters(std::string(raw_ufrag), std::string(raw_pwd),
                           /* renomination= */ false);
  auto result = parameters.Validate();
  if (!result.ok()) {
    return result;
  }
  return parameters;
}

RTCError IceParameters::Validate() const {
  // For legacy protocols.
  // TODO(zhihuang): Remove this once the legacy protocol is no longer
  // supported.
  if (ufrag.empty() && pwd.empty()) {
    return RTCError::OK();
  }

  auto ufrag_result = ValidateIceUfrag(ufrag);
  if (!ufrag_result.ok()) {
    return ufrag_result;
  }

  auto pwd_result = ValidateIcePwd(pwd);
  if (!pwd_result.ok()) {
    return pwd_result;
  }

  return RTCError::OK();
}

absl::optional<ConnectionRole> StringToConnectionRole(
    absl::string_view role_str) {
  const char* const roles[] = {
      CONNECTIONROLE_ACTIVE_STR, CONNECTIONROLE_PASSIVE_STR,
      CONNECTIONROLE_ACTPASS_STR, CONNECTIONROLE_HOLDCONN_STR};

  for (size_t i = 0; i < arraysize(roles); ++i) {
    if (absl::EqualsIgnoreCase(roles[i], role_str)) {
      return static_cast<ConnectionRole>(CONNECTIONROLE_ACTIVE + i);
    }
  }
  return absl::nullopt;
}

bool ConnectionRoleToString(const ConnectionRole& role, std::string* role_str) {
  switch (role) {
    case cricket::CONNECTIONROLE_ACTIVE:
      *role_str = cricket::CONNECTIONROLE_ACTIVE_STR;
      break;
    case cricket::CONNECTIONROLE_ACTPASS:
      *role_str = cricket::CONNECTIONROLE_ACTPASS_STR;
      break;
    case cricket::CONNECTIONROLE_PASSIVE:
      *role_str = cricket::CONNECTIONROLE_PASSIVE_STR;
      break;
    case cricket::CONNECTIONROLE_HOLDCONN:
      *role_str = cricket::CONNECTIONROLE_HOLDCONN_STR;
      break;
    default:
      return false;
  }
  return true;
}

TransportDescription::TransportDescription()
    : ice_mode(ICEMODE_FULL), connection_role(CONNECTIONROLE_NONE) {}

TransportDescription::TransportDescription(
    const std::vector<std::string>& transport_options,
    absl::string_view ice_ufrag,
    absl::string_view ice_pwd,
    IceMode ice_mode,
    ConnectionRole role,
    const rtc::SSLFingerprint* identity_fingerprint)
    : transport_options(transport_options),
      ice_ufrag(ice_ufrag),
      ice_pwd(ice_pwd),
      ice_mode(ice_mode),
      connection_role(role),
      identity_fingerprint(CopyFingerprint(identity_fingerprint)) {}

TransportDescription::TransportDescription(absl::string_view ice_ufrag,
                                           absl::string_view ice_pwd)
    : ice_ufrag(ice_ufrag),
      ice_pwd(ice_pwd),
      ice_mode(ICEMODE_FULL),
      connection_role(CONNECTIONROLE_NONE) {}

TransportDescription::TransportDescription(const TransportDescription& from)
    : transport_options(from.transport_options),
      ice_ufrag(from.ice_ufrag),
      ice_pwd(from.ice_pwd),
      ice_mode(from.ice_mode),
      connection_role(from.connection_role),
      identity_fingerprint(CopyFingerprint(from.identity_fingerprint.get())) {}

TransportDescription::~TransportDescription() = default;

TransportDescription& TransportDescription::operator=(
    const TransportDescription& from) {
  // Self-assignment
  if (this == &from)
    return *this;

  transport_options = from.transport_options;
  ice_ufrag = from.ice_ufrag;
  ice_pwd = from.ice_pwd;
  ice_mode = from.ice_mode;
  connection_role = from.connection_role;

  identity_fingerprint.reset(CopyFingerprint(from.identity_fingerprint.get()));
  return *this;
}

}  // namespace cricket
