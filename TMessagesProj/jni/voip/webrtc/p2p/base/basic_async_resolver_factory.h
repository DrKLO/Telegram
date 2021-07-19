/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_BASIC_ASYNC_RESOLVER_FACTORY_H_
#define P2P_BASE_BASIC_ASYNC_RESOLVER_FACTORY_H_

#include <functional>
#include <memory>
#include <utility>

#include "api/async_dns_resolver.h"
#include "api/async_resolver_factory.h"
#include "rtc_base/async_resolver_interface.h"

namespace webrtc {

class BasicAsyncResolverFactory final : public AsyncResolverFactory {
 public:
  rtc::AsyncResolverInterface* Create() override;
};

// This class wraps a factory using the older webrtc::AsyncResolverFactory API,
// and produces webrtc::AsyncDnsResolver objects that contain an
// rtc::AsyncResolver object.
class WrappingAsyncDnsResolverFactory final
    : public AsyncDnsResolverFactoryInterface {
 public:
  explicit WrappingAsyncDnsResolverFactory(
      std::unique_ptr<AsyncResolverFactory> wrapped_factory)
      : owned_factory_(std::move(wrapped_factory)),
        wrapped_factory_(owned_factory_.get()) {}

  explicit WrappingAsyncDnsResolverFactory(
      AsyncResolverFactory* non_owned_factory)
      : wrapped_factory_(non_owned_factory) {}

  std::unique_ptr<webrtc::AsyncDnsResolverInterface> CreateAndResolve(
      const rtc::SocketAddress& addr,
      std::function<void()> callback) override;

  std::unique_ptr<webrtc::AsyncDnsResolverInterface> Create() override;

 private:
  const std::unique_ptr<AsyncResolverFactory> owned_factory_;
  AsyncResolverFactory* const wrapped_factory_;
};

}  // namespace webrtc

#endif  // P2P_BASE_BASIC_ASYNC_RESOLVER_FACTORY_H_
