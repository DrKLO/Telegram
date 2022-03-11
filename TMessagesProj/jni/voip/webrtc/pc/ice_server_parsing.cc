/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/ice_server_parsing.h"

#include <stddef.h>

#include <cctype>  // For std::isdigit.
#include <string>

#include "p2p/base/port_interface.h"
#include "rtc_base/arraysize.h"
#include "rtc_base/checks.h"
#include "rtc_base/ip_address.h"
#include "rtc_base/logging.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/string_encode.h"

namespace webrtc {

// Number of tokens must be preset when TURN uri has transport param.
static const size_t kTurnTransportTokensNum = 2;
// The default stun port.
static const int kDefaultStunPort = 3478;
static const int kDefaultStunTlsPort = 5349;
static const char kTransport[] = "transport";

// Allowed characters in hostname per RFC 3986 Appendix A "reg-name"
static const char kRegNameCharacters[] =
    "abcdefghijklmnopqrstuvwxyz"
    "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    "0123456789"
    "-._~"          // unreserved
    "%"             // pct-encoded
    "!$&'()*+,;=";  // sub-delims

// NOTE: Must be in the same order as the ServiceType enum.
static const char* kValidIceServiceTypes[] = {"stun", "stuns", "turn", "turns"};

// NOTE: A loop below assumes that the first value of this enum is 0 and all
// other values are incremental.
enum ServiceType {
  STUN = 0,  // Indicates a STUN server.
  STUNS,     // Indicates a STUN server used with a TLS session.
  TURN,      // Indicates a TURN server
  TURNS,     // Indicates a TURN server used with a TLS session.
  INVALID,   // Unknown.
};
static_assert(INVALID == arraysize(kValidIceServiceTypes),
              "kValidIceServiceTypes must have as many strings as ServiceType "
              "has values.");

// `in_str` should follow of RFC 7064/7065 syntax, but with an optional
// "?transport=" already stripped. I.e.,
// stunURI       = scheme ":" host [ ":" port ]
// scheme        = "stun" / "stuns" / "turn" / "turns"
// host          = IP-literal / IPv4address / reg-name
// port          = *DIGIT
static bool GetServiceTypeAndHostnameFromUri(const std::string& in_str,
                                             ServiceType* service_type,
                                             std::string* hostname) {
  const std::string::size_type colonpos = in_str.find(':');
  if (colonpos == std::string::npos) {
    RTC_LOG(LS_WARNING) << "Missing ':' in ICE URI: " << in_str;
    return false;
  }
  if ((colonpos + 1) == in_str.length()) {
    RTC_LOG(LS_WARNING) << "Empty hostname in ICE URI: " << in_str;
    return false;
  }
  *service_type = INVALID;
  for (size_t i = 0; i < arraysize(kValidIceServiceTypes); ++i) {
    if (in_str.compare(0, colonpos, kValidIceServiceTypes[i]) == 0) {
      *service_type = static_cast<ServiceType>(i);
      break;
    }
  }
  if (*service_type == INVALID) {
    return false;
  }
  *hostname = in_str.substr(colonpos + 1, std::string::npos);
  return true;
}

static bool ParsePort(const std::string& in_str, int* port) {
  // Make sure port only contains digits. FromString doesn't check this.
  for (const char& c : in_str) {
    if (!std::isdigit(c)) {
      return false;
    }
  }
  return rtc::FromString(in_str, port);
}

// This method parses IPv6 and IPv4 literal strings, along with hostnames in
// standard hostname:port format.
// Consider following formats as correct.
// `hostname:port`, |[IPV6 address]:port|, |IPv4 address|:port,
// `hostname`, |[IPv6 address]|, |IPv4 address|.
static bool ParseHostnameAndPortFromString(const std::string& in_str,
                                           std::string* host,
                                           int* port) {
  RTC_DCHECK(host->empty());
  if (in_str.at(0) == '[') {
    // IP_literal syntax
    std::string::size_type closebracket = in_str.rfind(']');
    if (closebracket != std::string::npos) {
      std::string::size_type colonpos = in_str.find(':', closebracket);
      if (std::string::npos != colonpos) {
        if (!ParsePort(in_str.substr(closebracket + 2, std::string::npos),
                       port)) {
          return false;
        }
      }
      *host = in_str.substr(1, closebracket - 1);
    } else {
      return false;
    }
  } else {
    // IPv4address or reg-name syntax
    std::string::size_type colonpos = in_str.find(':');
    if (std::string::npos != colonpos) {
      if (!ParsePort(in_str.substr(colonpos + 1, std::string::npos), port)) {
        return false;
      }
      *host = in_str.substr(0, colonpos);
    } else {
      *host = in_str;
    }
    // RFC 3986 section 3.2.2 and Appendix A - "reg-name" syntax
    if (host->find_first_not_of(kRegNameCharacters) != std::string::npos) {
      return false;
    }
  }
  return !host->empty();
}

// Adds a STUN or TURN server to the appropriate list,
// by parsing `url` and using the username/password in `server`.
static RTCErrorType ParseIceServerUrl(
    const PeerConnectionInterface::IceServer& server,
    const std::string& url,
    cricket::ServerAddresses* stun_servers,
    std::vector<cricket::RelayServerConfig>* turn_servers) {
  // RFC 7064
  // stunURI       = scheme ":" host [ ":" port ]
  // scheme        = "stun" / "stuns"

  // RFC 7065
  // turnURI       = scheme ":" host [ ":" port ]
  //                 [ "?transport=" transport ]
  // scheme        = "turn" / "turns"
  // transport     = "udp" / "tcp" / transport-ext
  // transport-ext = 1*unreserved

  // RFC 3986
  // host     = IP-literal / IPv4address / reg-name
  // port     = *DIGIT

  RTC_DCHECK(stun_servers != nullptr);
  RTC_DCHECK(turn_servers != nullptr);
  std::vector<std::string> tokens;
  cricket::ProtocolType turn_transport_type = cricket::PROTO_UDP;
  RTC_DCHECK(!url.empty());
  rtc::split(url, '?', &tokens);
  std::string uri_without_transport = tokens[0];
  // Let's look into transport= param, if it exists.
  if (tokens.size() == kTurnTransportTokensNum) {  // ?transport= is present.
    std::string uri_transport_param = tokens[1];
    rtc::split(uri_transport_param, '=', &tokens);
    if (tokens[0] != kTransport) {
      RTC_LOG(LS_WARNING) << "Invalid transport parameter key.";
      return RTCErrorType::SYNTAX_ERROR;
    }
    if (tokens.size() < 2) {
      RTC_LOG(LS_WARNING) << "Transport parameter missing value.";
      return RTCErrorType::SYNTAX_ERROR;
    }
    if (!cricket::StringToProto(tokens[1].c_str(), &turn_transport_type) ||
        (turn_transport_type != cricket::PROTO_UDP &&
         turn_transport_type != cricket::PROTO_TCP)) {
      RTC_LOG(LS_WARNING) << "Transport parameter should always be udp or tcp.";
      return RTCErrorType::SYNTAX_ERROR;
    }
  }

  std::string hoststring;
  ServiceType service_type;
  if (!GetServiceTypeAndHostnameFromUri(uri_without_transport, &service_type,
                                        &hoststring)) {
    RTC_LOG(LS_WARNING) << "Invalid transport parameter in ICE URI: " << url;
    return RTCErrorType::SYNTAX_ERROR;
  }

  // GetServiceTypeAndHostnameFromUri should never give an empty hoststring
  RTC_DCHECK(!hoststring.empty());

  int port = kDefaultStunPort;
  if (service_type == TURNS) {
    port = kDefaultStunTlsPort;
    turn_transport_type = cricket::PROTO_TLS;
  }

  if (hoststring.find('@') != std::string::npos) {
    RTC_LOG(LS_WARNING) << "Invalid url: " << uri_without_transport;
    RTC_LOG(LS_WARNING)
        << "Note that user-info@ in turn:-urls is long-deprecated.";
    return RTCErrorType::SYNTAX_ERROR;
  }
  std::string address;
  if (!ParseHostnameAndPortFromString(hoststring, &address, &port)) {
    RTC_LOG(LS_WARNING) << "Invalid hostname format: " << uri_without_transport;
    return RTCErrorType::SYNTAX_ERROR;
  }

  if (port <= 0 || port > 0xffff) {
    RTC_LOG(LS_WARNING) << "Invalid port: " << port;
    return RTCErrorType::SYNTAX_ERROR;
  }

  switch (service_type) {
    case STUN:
    case STUNS:
      stun_servers->insert(rtc::SocketAddress(address, port));
      break;
    case TURN:
    case TURNS: {
      if (server.username.empty() || server.password.empty()) {
        // The WebRTC spec requires throwing an InvalidAccessError when username
        // or credential are ommitted; this is the native equivalent.
        RTC_LOG(LS_WARNING) << "TURN server with empty username or password";
        return RTCErrorType::INVALID_PARAMETER;
      }
      // If the hostname field is not empty, then the server address must be
      // the resolved IP for that host, the hostname is needed later for TLS
      // handshake (SNI and Certificate verification).
      const std::string& hostname =
          server.hostname.empty() ? address : server.hostname;
      rtc::SocketAddress socket_address(hostname, port);
      if (!server.hostname.empty()) {
        rtc::IPAddress ip;
        if (!IPFromString(address, &ip)) {
          // When hostname is set, the server address must be a
          // resolved ip address.
          RTC_LOG(LS_WARNING)
              << "IceServer has hostname field set, but URI does not "
                 "contain an IP address.";
          return RTCErrorType::INVALID_PARAMETER;
        }
        socket_address.SetResolvedIP(ip);
      }
      cricket::RelayServerConfig config =
          cricket::RelayServerConfig(socket_address, server.username,
                                     server.password, turn_transport_type);
      if (server.tls_cert_policy ==
          PeerConnectionInterface::kTlsCertPolicyInsecureNoCheck) {
        config.tls_cert_policy =
            cricket::TlsCertPolicy::TLS_CERT_POLICY_INSECURE_NO_CHECK;
      }
      config.tls_alpn_protocols = server.tls_alpn_protocols;
      config.tls_elliptic_curves = server.tls_elliptic_curves;

      turn_servers->push_back(config);
      break;
    }
    default:
      // We shouldn't get to this point with an invalid service_type, we should
      // have returned an error already.
      RTC_DCHECK_NOTREACHED() << "Unexpected service type";
      return RTCErrorType::INTERNAL_ERROR;
  }
  return RTCErrorType::NONE;
}

RTCErrorType ParseIceServers(
    const PeerConnectionInterface::IceServers& servers,
    cricket::ServerAddresses* stun_servers,
    std::vector<cricket::RelayServerConfig>* turn_servers) {
  for (const PeerConnectionInterface::IceServer& server : servers) {
    if (!server.urls.empty()) {
      for (const std::string& url : server.urls) {
        if (url.empty()) {
          RTC_LOG(LS_WARNING) << "Empty uri.";
          return RTCErrorType::SYNTAX_ERROR;
        }
        RTCErrorType err =
            ParseIceServerUrl(server, url, stun_servers, turn_servers);
        if (err != RTCErrorType::NONE) {
          return err;
        }
      }
    } else if (!server.uri.empty()) {
      // Fallback to old .uri if new .urls isn't present.
      RTCErrorType err =
          ParseIceServerUrl(server, server.uri, stun_servers, turn_servers);
      if (err != RTCErrorType::NONE) {
        return err;
      }
    } else {
      RTC_LOG(LS_WARNING) << "Empty uri.";
      return RTCErrorType::SYNTAX_ERROR;
    }
  }
  // Candidates must have unique priorities, so that connectivity checks
  // are performed in a well-defined order.
  int priority = static_cast<int>(turn_servers->size() - 1);
  for (cricket::RelayServerConfig& turn_server : *turn_servers) {
    // First in the list gets highest priority.
    turn_server.priority = priority--;
  }
  return RTCErrorType::NONE;
}

}  // namespace webrtc
