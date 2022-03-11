/*
 *  Copyright 2013 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_ASYNC_RESOLVER_INTERFACE_H_
#define RTC_BASE_ASYNC_RESOLVER_INTERFACE_H_

#include "rtc_base/socket_address.h"
#include "rtc_base/system/rtc_export.h"
#include "rtc_base/third_party/sigslot/sigslot.h"

namespace rtc {

// This interface defines the methods to resolve the address asynchronously.
class RTC_EXPORT AsyncResolverInterface {
 public:
  AsyncResolverInterface();
  virtual ~AsyncResolverInterface();

  // Start address resolution of the hostname in `addr`.
  virtual void Start(const SocketAddress& addr) = 0;
  // Returns true iff the address from `Start` was successfully resolved.
  // If the address was successfully resolved, sets `addr` to a copy of the
  // address from `Start` with the IP address set to the top most resolved
  // address of `family` (`addr` will have both hostname and the resolved ip).
  virtual bool GetResolvedAddress(int family, SocketAddress* addr) const = 0;
  // Returns error from resolver.
  virtual int GetError() const = 0;
  // Delete the resolver.
  virtual void Destroy(bool wait) = 0;
  // Returns top most resolved IPv4 address if address is resolved successfully.
  // Otherwise returns address set in SetAddress.
  SocketAddress address() const {
    SocketAddress addr;
    GetResolvedAddress(AF_INET, &addr);
    return addr;
  }

  // This signal is fired when address resolve process is completed.
  sigslot::signal1<AsyncResolverInterface*> SignalDone;
};

}  // namespace rtc

#endif
