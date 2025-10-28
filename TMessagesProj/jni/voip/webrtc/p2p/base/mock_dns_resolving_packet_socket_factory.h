/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_MOCK_DNS_RESOLVING_PACKET_SOCKET_FACTORY_H_
#define P2P_BASE_MOCK_DNS_RESOLVING_PACKET_SOCKET_FACTORY_H_

#include <functional>
#include <memory>

#include "api/test/mock_async_dns_resolver.h"
#include "p2p/base/basic_packet_socket_factory.h"

namespace rtc {

// A PacketSocketFactory implementation for tests that uses a mock DnsResolver
// and allows setting expectations on the resolver and results.
class MockDnsResolvingPacketSocketFactory : public BasicPacketSocketFactory {
 public:
  using Expectations = std::function<void(webrtc::MockAsyncDnsResolver*,
                                          webrtc::MockAsyncDnsResolverResult*)>;

  explicit MockDnsResolvingPacketSocketFactory(SocketFactory* socket_factory)
      : BasicPacketSocketFactory(socket_factory) {}

  std::unique_ptr<webrtc::AsyncDnsResolverInterface> CreateAsyncDnsResolver()
      override {
    std::unique_ptr<webrtc::MockAsyncDnsResolver> resolver =
        std::make_unique<webrtc::MockAsyncDnsResolver>();
    if (expectations_) {
      expectations_(resolver.get(), &resolver_result_);
    }
    return resolver;
  }

  void SetExpectations(Expectations expectations) {
    expectations_ = expectations;
  }

 private:
  webrtc::MockAsyncDnsResolverResult resolver_result_;
  Expectations expectations_;
};

}  // namespace rtc

#endif  // P2P_BASE_MOCK_DNS_RESOLVING_PACKET_SOCKET_FACTORY_H_
