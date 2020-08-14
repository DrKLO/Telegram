/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/ifaddrs_converter.h"

namespace rtc {

IfAddrsConverter::IfAddrsConverter() {}

IfAddrsConverter::~IfAddrsConverter() {}

bool IfAddrsConverter::ConvertIfAddrsToIPAddress(
    const struct ifaddrs* interface,
    InterfaceAddress* ip,
    IPAddress* mask) {
  switch (interface->ifa_addr->sa_family) {
    case AF_INET: {
      *ip = InterfaceAddress(IPAddress(
          reinterpret_cast<sockaddr_in*>(interface->ifa_addr)->sin_addr));
      *mask = IPAddress(
          reinterpret_cast<sockaddr_in*>(interface->ifa_netmask)->sin_addr);
      return true;
    }
    case AF_INET6: {
      int ip_attributes = IPV6_ADDRESS_FLAG_NONE;
      if (!ConvertNativeAttributesToIPAttributes(interface, &ip_attributes)) {
        return false;
      }
      *ip = InterfaceAddress(
          reinterpret_cast<sockaddr_in6*>(interface->ifa_addr)->sin6_addr,
          ip_attributes);
      *mask = IPAddress(
          reinterpret_cast<sockaddr_in6*>(interface->ifa_netmask)->sin6_addr);
      return true;
    }
    default: {
      return false;
    }
  }
}

bool IfAddrsConverter::ConvertNativeAttributesToIPAttributes(
    const struct ifaddrs* interface,
    int* ip_attributes) {
  *ip_attributes = IPV6_ADDRESS_FLAG_NONE;
  return true;
}

#if !defined(WEBRTC_MAC)
// For MAC and IOS, it's defined in macifaddrs_converter.cc
IfAddrsConverter* CreateIfAddrsConverter() {
  return new IfAddrsConverter();
}
#endif
}  // namespace rtc
