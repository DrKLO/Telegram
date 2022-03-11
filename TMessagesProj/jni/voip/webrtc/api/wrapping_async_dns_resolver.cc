/*
 *  Copyright 2021 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/wrapping_async_dns_resolver.h"

namespace webrtc {

bool WrappingAsyncDnsResolverResult::GetResolvedAddress(
    int family,
    rtc::SocketAddress* addr) const {
  if (!owner_->wrapped()) {
    return false;
  }
  return owner_->wrapped()->GetResolvedAddress(family, addr);
}

int WrappingAsyncDnsResolverResult::GetError() const {
  if (!owner_->wrapped()) {
    return -1;  // FIXME: Find a code that makes sense.
  }
  return owner_->wrapped()->GetError();
}

}  // namespace webrtc
