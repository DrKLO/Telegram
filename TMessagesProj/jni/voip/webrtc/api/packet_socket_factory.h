/*
 *  Copyright 2019 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_PACKET_SOCKET_FACTORY_H_
#define API_PACKET_SOCKET_FACTORY_H_

#include <memory>
#include <string>
#include <vector>

#include "api/async_dns_resolver.h"
#include "api/wrapping_async_dns_resolver.h"
#include "rtc_base/async_packet_socket.h"
#include "rtc_base/proxy_info.h"
#include "rtc_base/system/rtc_export.h"

namespace rtc {

class SSLCertificateVerifier;
class AsyncResolverInterface;

struct PacketSocketTcpOptions {
  PacketSocketTcpOptions() = default;
  ~PacketSocketTcpOptions() = default;

  int opts = 0;
  std::vector<std::string> tls_alpn_protocols;
  std::vector<std::string> tls_elliptic_curves;
  // An optional custom SSL certificate verifier that an API user can provide to
  // inject their own certificate verification logic (not available to users
  // outside of the WebRTC repo).
  SSLCertificateVerifier* tls_cert_verifier = nullptr;
};

class RTC_EXPORT PacketSocketFactory {
 public:
  enum Options {
    OPT_STUN = 0x04,

    // The TLS options below are mutually exclusive.
    OPT_TLS = 0x02,           // Real and secure TLS.
    OPT_TLS_FAKE = 0x01,      // Fake TLS with a dummy SSL handshake.
    OPT_TLS_INSECURE = 0x08,  // Insecure TLS without certificate validation.

    // Deprecated, use OPT_TLS_FAKE.
    OPT_SSLTCP = OPT_TLS_FAKE,
  };

  PacketSocketFactory() = default;
  virtual ~PacketSocketFactory() = default;

  virtual AsyncPacketSocket* CreateUdpSocket(const SocketAddress& address,
                                             uint16_t min_port,
                                             uint16_t max_port) = 0;
  virtual AsyncListenSocket* CreateServerTcpSocket(
      const SocketAddress& local_address,
      uint16_t min_port,
      uint16_t max_port,
      int opts) = 0;

  virtual AsyncPacketSocket* CreateClientTcpSocket(
      const SocketAddress& local_address,
      const SocketAddress& remote_address,
      const ProxyInfo& proxy_info,
      const std::string& user_agent,
      const PacketSocketTcpOptions& tcp_options) = 0;

  // The AsyncResolverInterface is deprecated; users are encouraged
  // to switch to the AsyncDnsResolverInterface.
  // TODO(bugs.webrtc.org/12598): Remove once all downstream users
  // are converted.
  virtual AsyncResolverInterface* CreateAsyncResolver() {
    // Default implementation, so that downstream users can remove this
    // immediately after changing to CreateAsyncDnsResolver
    RTC_DCHECK_NOTREACHED();
    return nullptr;
  }

  virtual std::unique_ptr<webrtc::AsyncDnsResolverInterface>
  CreateAsyncDnsResolver() {
    // Default implementation, to aid in transition to AsyncDnsResolverInterface
    return std::make_unique<webrtc::WrappingAsyncDnsResolver>(
        CreateAsyncResolver());
  }

 private:
  PacketSocketFactory(const PacketSocketFactory&) = delete;
  PacketSocketFactory& operator=(const PacketSocketFactory&) = delete;
};

}  // namespace rtc

#endif  // API_PACKET_SOCKET_FACTORY_H_
