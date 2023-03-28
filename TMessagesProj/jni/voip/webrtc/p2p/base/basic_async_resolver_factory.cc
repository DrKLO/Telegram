/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/basic_async_resolver_factory.h"

#include <memory>
#include <utility>

#include "absl/memory/memory.h"
#include "api/async_dns_resolver.h"
#include "api/wrapping_async_dns_resolver.h"
#include "rtc_base/async_resolver.h"
#include "rtc_base/logging.h"

namespace webrtc {

rtc::AsyncResolverInterface* BasicAsyncResolverFactory::Create() {
  return new rtc::AsyncResolver();
}


std::unique_ptr<webrtc::AsyncDnsResolverInterface>
WrappingAsyncDnsResolverFactory::Create() {
  return std::make_unique<WrappingAsyncDnsResolver>(wrapped_factory_->Create());
}

std::unique_ptr<webrtc::AsyncDnsResolverInterface>
WrappingAsyncDnsResolverFactory::CreateAndResolve(
    const rtc::SocketAddress& addr,
    std::function<void()> callback) {
  std::unique_ptr<webrtc::AsyncDnsResolverInterface> resolver = Create();
  resolver->Start(addr, std::move(callback));
  return resolver;
}

std::unique_ptr<webrtc::AsyncDnsResolverInterface>
WrappingAsyncDnsResolverFactory::CreateAndResolve(
    const rtc::SocketAddress& addr,
    int family,
    std::function<void()> callback) {
  std::unique_ptr<webrtc::AsyncDnsResolverInterface> resolver = Create();
  resolver->Start(addr, family, std::move(callback));
  return resolver;
}

}  // namespace webrtc
