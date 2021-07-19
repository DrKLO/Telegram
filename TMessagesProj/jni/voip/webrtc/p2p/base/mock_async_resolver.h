/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_MOCK_ASYNC_RESOLVER_H_
#define P2P_BASE_MOCK_ASYNC_RESOLVER_H_

#include "api/async_resolver_factory.h"
#include "rtc_base/async_resolver_interface.h"
#include "test/gmock.h"

namespace rtc {

using ::testing::_;
using ::testing::InvokeWithoutArgs;

class MockAsyncResolver : public AsyncResolverInterface {
 public:
  MockAsyncResolver() {
    ON_CALL(*this, Start(_)).WillByDefault(InvokeWithoutArgs([this] {
      SignalDone(this);
    }));
  }
  ~MockAsyncResolver() = default;

  MOCK_METHOD(void, Start, (const rtc::SocketAddress&), (override));
  MOCK_METHOD(bool,
              GetResolvedAddress,
              (int family, SocketAddress* addr),
              (const, override));
  MOCK_METHOD(int, GetError, (), (const, override));

  // Note that this won't delete the object like AsyncResolverInterface says in
  // order to avoid sanitizer failures caused by this being a synchronous
  // implementation. The test code should delete the object instead.
  MOCK_METHOD(void, Destroy, (bool), (override));
};

}  // namespace rtc

namespace webrtc {

class MockAsyncResolverFactory : public AsyncResolverFactory {
 public:
  MOCK_METHOD(rtc::AsyncResolverInterface*, Create, (), (override));
};

}  // namespace webrtc

#endif  // P2P_BASE_MOCK_ASYNC_RESOLVER_H_
