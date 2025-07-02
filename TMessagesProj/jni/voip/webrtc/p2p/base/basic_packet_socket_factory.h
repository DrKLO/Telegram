/*
 *  Copyright 2011 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_BASIC_PACKET_SOCKET_FACTORY_H_
#define P2P_BASE_BASIC_PACKET_SOCKET_FACTORY_H_

#include <stdint.h>

#include <memory>
#include <string>

#include "api/async_dns_resolver.h"
#include "api/packet_socket_factory.h"
#include "rtc_base/async_packet_socket.h"
#include "rtc_base/proxy_info.h"
#include "rtc_base/socket.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/socket_factory.h"
#include "rtc_base/system/rtc_export.h"

namespace rtc {

class SocketFactory;

class RTC_EXPORT BasicPacketSocketFactory : public PacketSocketFactory {
 public:
  explicit BasicPacketSocketFactory(SocketFactory* socket_factory);
  ~BasicPacketSocketFactory() override;

  AsyncPacketSocket* CreateUdpSocket(const SocketAddress& local_address,
                                     uint16_t min_port,
                                     uint16_t max_port) override;
  AsyncListenSocket* CreateServerTcpSocket(const SocketAddress& local_address,
                                           uint16_t min_port,
                                           uint16_t max_port,
                                           int opts) override;
  AsyncPacketSocket* CreateClientTcpSocket(
      const SocketAddress& local_address,
      const SocketAddress& remote_address,
      const ProxyInfo& proxy_info,
      const std::string& user_agent,
      const PacketSocketTcpOptions& tcp_options) override;

  std::unique_ptr<webrtc::AsyncDnsResolverInterface> CreateAsyncDnsResolver()
      override;

 private:
  int BindSocket(Socket* socket,
                 const SocketAddress& local_address,
                 uint16_t min_port,
                 uint16_t max_port);

  SocketFactory* socket_factory_;
};

}  // namespace rtc

#endif  // P2P_BASE_BASIC_PACKET_SOCKET_FACTORY_H_
