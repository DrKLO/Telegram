/*
 *  Copyright 2021 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_TEST_MOCK_ASYNC_DNS_RESOLVER_H_
#define API_TEST_MOCK_ASYNC_DNS_RESOLVER_H_

#include <functional>
#include <memory>

#include "api/async_dns_resolver.h"
#include "test/gmock.h"

namespace webrtc {

class MockAsyncDnsResolverResult : public AsyncDnsResolverResult {
 public:
  MOCK_METHOD(bool,
              GetResolvedAddress,
              (int, rtc::SocketAddress*),
              (const, override));
  MOCK_METHOD(int, GetError, (), (const, override));
};

class MockAsyncDnsResolver : public AsyncDnsResolverInterface {
 public:
  MOCK_METHOD(void,
              Start,
              (const rtc::SocketAddress&, std::function<void()>),
              (override));
  MOCK_METHOD(AsyncDnsResolverResult&, result, (), (const, override));
};

class MockAsyncDnsResolverFactory : public AsyncDnsResolverFactoryInterface {
 public:
  MOCK_METHOD(std::unique_ptr<webrtc::AsyncDnsResolverInterface>,
              CreateAndResolve,
              (const rtc::SocketAddress&, std::function<void()>),
              (override));
  MOCK_METHOD(std::unique_ptr<webrtc::AsyncDnsResolverInterface>,
              Create,
              (),
              (override));
};

}  // namespace webrtc

#endif  // API_TEST_MOCK_ASYNC_DNS_RESOLVER_H_
