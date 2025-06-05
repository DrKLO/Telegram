/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_TEST_MOCK_PACKET_SOCKET_FACTORY_H_
#define API_TEST_MOCK_PACKET_SOCKET_FACTORY_H_

#include <memory>
#include <string>

#include "api/packet_socket_factory.h"
#include "test/gmock.h"

namespace rtc {
class MockPacketSocketFactory : public PacketSocketFactory {
 public:
  MOCK_METHOD(AsyncPacketSocket*,
              CreateUdpSocket,
              (const SocketAddress&, uint16_t, uint16_t),
              (override));
  MOCK_METHOD(AsyncListenSocket*,
              CreateServerTcpSocket,
              (const SocketAddress&, uint16_t, uint16_t, int opts),
              (override));
  MOCK_METHOD(AsyncPacketSocket*,
              CreateClientTcpSocket,
              (const SocketAddress& local_address,
               const SocketAddress&,
               const ProxyInfo&,
               const std::string&,
               const PacketSocketTcpOptions&),
              (override));
  MOCK_METHOD(std::unique_ptr<webrtc::AsyncDnsResolverInterface>,
              CreateAsyncDnsResolver,
              (),
              (override));
};

static_assert(!std::is_abstract_v<MockPacketSocketFactory>, "");

}  // namespace rtc

#endif  // API_TEST_MOCK_PACKET_SOCKET_FACTORY_H_
