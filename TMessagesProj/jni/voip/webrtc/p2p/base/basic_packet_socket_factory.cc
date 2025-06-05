/*
 *  Copyright 2011 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/basic_packet_socket_factory.h"

#include <stddef.h>

#include <string>

#include "absl/memory/memory.h"
#include "api/async_dns_resolver.h"
#include "p2p/base/async_stun_tcp_socket.h"
#include "rtc_base/async_dns_resolver.h"
#include "rtc_base/async_tcp_socket.h"
#include "rtc_base/async_udp_socket.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/socket.h"
#include "rtc_base/socket_adapters.h"
#include "rtc_base/ssl_adapter.h"

namespace rtc {

BasicPacketSocketFactory::BasicPacketSocketFactory(
    SocketFactory* socket_factory)
    : socket_factory_(socket_factory) {}

BasicPacketSocketFactory::~BasicPacketSocketFactory() {}

AsyncPacketSocket* BasicPacketSocketFactory::CreateUdpSocket(
    const SocketAddress& address,
    uint16_t min_port,
    uint16_t max_port) {
  // UDP sockets are simple.
  Socket* socket = socket_factory_->CreateSocket(address.family(), SOCK_DGRAM);
  if (!socket) {
    return NULL;
  }
  if (BindSocket(socket, address, min_port, max_port) < 0) {
    RTC_LOG(LS_ERROR) << "UDP bind failed with error " << socket->GetError();
    delete socket;
    return NULL;
  }
  return new AsyncUDPSocket(socket);
}

AsyncListenSocket* BasicPacketSocketFactory::CreateServerTcpSocket(
    const SocketAddress& local_address,
    uint16_t min_port,
    uint16_t max_port,
    int opts) {
  // Fail if TLS is required.
  if (opts & PacketSocketFactory::OPT_TLS) {
    RTC_LOG(LS_ERROR) << "TLS support currently is not available.";
    return NULL;
  }

  if (opts & PacketSocketFactory::OPT_TLS_FAKE) {
    RTC_LOG(LS_ERROR) << "Fake TLS not supported.";
    return NULL;
  }
  Socket* socket =
      socket_factory_->CreateSocket(local_address.family(), SOCK_STREAM);
  if (!socket) {
    return NULL;
  }

  if (BindSocket(socket, local_address, min_port, max_port) < 0) {
    RTC_LOG(LS_ERROR) << "TCP bind failed with error " << socket->GetError();
    delete socket;
    return NULL;
  }

  RTC_CHECK(!(opts & PacketSocketFactory::OPT_STUN));

  return new AsyncTcpListenSocket(absl::WrapUnique(socket));
}

AsyncPacketSocket* BasicPacketSocketFactory::CreateClientTcpSocket(
    const SocketAddress& local_address,
    const SocketAddress& remote_address,
    const ProxyInfo& proxy_info,
    const std::string& user_agent,
    const PacketSocketTcpOptions& tcp_options) {
  Socket* socket =
      socket_factory_->CreateSocket(local_address.family(), SOCK_STREAM);
  if (!socket) {
    return NULL;
  }

  if (BindSocket(socket, local_address, 0, 0) < 0) {
    // Allow BindSocket to fail if we're binding to the ANY address, since this
    // is mostly redundant in the first place. The socket will be bound when we
    // call Connect() instead.
    if (local_address.IsAnyIP()) {
      RTC_LOG(LS_WARNING) << "TCP bind failed with error " << socket->GetError()
                          << "; ignoring since socket is using 'any' address.";
    } else {
      RTC_LOG(LS_ERROR) << "TCP bind failed with error " << socket->GetError();
      delete socket;
      return NULL;
    }
  }

  // Set TCP_NODELAY (via OPT_NODELAY) for improved performance; this causes
  // small media packets to be sent immediately rather than being buffered up,
  // reducing latency.
  //
  // Must be done before calling Connect, otherwise it may fail.
  if (socket->SetOption(Socket::OPT_NODELAY, 1) != 0) {
    RTC_LOG(LS_ERROR) << "Setting TCP_NODELAY option failed with error "
                      << socket->GetError();
  }

  if (proxy_info.type == PROXY_HTTPS) {
    socket =
        new AsyncHttpsProxySocket(socket, user_agent, proxy_info.address,
                                  proxy_info.username, proxy_info.password);
  }

  // Assert that at most one TLS option is used.
  int tlsOpts = tcp_options.opts & (PacketSocketFactory::OPT_TLS |
                                    PacketSocketFactory::OPT_TLS_FAKE |
                                    PacketSocketFactory::OPT_TLS_INSECURE);
  RTC_DCHECK((tlsOpts & (tlsOpts - 1)) == 0);

  if ((tlsOpts & PacketSocketFactory::OPT_TLS) ||
      (tlsOpts & PacketSocketFactory::OPT_TLS_INSECURE)) {
    // Using TLS, wrap the socket in an SSL adapter.
    SSLAdapter* ssl_adapter = SSLAdapter::Create(socket);
    if (!ssl_adapter) {
      return NULL;
    }

    if (tlsOpts & PacketSocketFactory::OPT_TLS_INSECURE) {
      ssl_adapter->SetIgnoreBadCert(true);
    }

    ssl_adapter->SetAlpnProtocols(tcp_options.tls_alpn_protocols);
    ssl_adapter->SetEllipticCurves(tcp_options.tls_elliptic_curves);
    ssl_adapter->SetCertVerifier(tcp_options.tls_cert_verifier);

    socket = ssl_adapter;

    if (ssl_adapter->StartSSL(remote_address.hostname().c_str()) != 0) {
      delete ssl_adapter;
      return NULL;
    }

  } else if (tlsOpts & PacketSocketFactory::OPT_TLS_FAKE) {
    // Using fake TLS, wrap the TCP socket in a pseudo-SSL socket.
    socket = new AsyncSSLSocket(socket);
  }

  if (socket->Connect(remote_address) < 0) {
    RTC_LOG(LS_ERROR) << "TCP connect failed with error " << socket->GetError();
    delete socket;
    return NULL;
  }

  // Finally, wrap that socket in a TCP or STUN TCP packet socket.
  AsyncPacketSocket* tcp_socket;
  if (tcp_options.opts & PacketSocketFactory::OPT_STUN) {
    tcp_socket = new cricket::AsyncStunTCPSocket(socket);
  } else {
    tcp_socket = new AsyncTCPSocket(socket);
  }

  return tcp_socket;
}

std::unique_ptr<webrtc::AsyncDnsResolverInterface>
BasicPacketSocketFactory::CreateAsyncDnsResolver() {
  return std::make_unique<webrtc::AsyncDnsResolver>();
}

int BasicPacketSocketFactory::BindSocket(Socket* socket,
                                         const SocketAddress& local_address,
                                         uint16_t min_port,
                                         uint16_t max_port) {
  int ret = -1;
  if (min_port == 0 && max_port == 0) {
    // If there's no port range, let the OS pick a port for us.
    ret = socket->Bind(local_address);
  } else {
    // Otherwise, try to find a port in the provided range.
    for (int port = min_port; ret < 0 && port <= max_port; ++port) {
      ret = socket->Bind(SocketAddress(local_address.ipaddr(), port));
    }
  }
  return ret;
}

}  // namespace rtc
