/*
 *  Copyright 2023 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/net_test_helpers.h"

#include <memory>
#include <string>

#if defined(WEBRTC_WIN)
#include <ws2spi.h>
#include <ws2tcpip.h>

#include "rtc_base/win/windows_version.h"
#endif
#if defined(WEBRTC_POSIX) && !defined(__native_client__)
#include <arpa/inet.h>
#if defined(WEBRTC_ANDROID)
#include "rtc_base/ifaddrs_android.h"
#else
#include <ifaddrs.h>
#endif
#endif  // defined(WEBRTC_POSIX) && !defined(__native_client__)

namespace rtc {

bool HasIPv4Enabled() {
#if defined(WEBRTC_POSIX) && !defined(__native_client__)
  bool has_ipv4 = false;
  struct ifaddrs* ifa;
  if (getifaddrs(&ifa) < 0) {
    return false;
  }
  for (struct ifaddrs* cur = ifa; cur != nullptr; cur = cur->ifa_next) {
    if (cur->ifa_addr != nullptr && cur->ifa_addr->sa_family == AF_INET) {
      has_ipv4 = true;
      break;
    }
  }
  freeifaddrs(ifa);
  return has_ipv4;
#else
  return true;
#endif
}

bool HasIPv6Enabled() {
#if defined(WINUWP)
  // WinUWP always has IPv6 capability.
  return true;
#elif defined(WEBRTC_WIN)
  if (rtc::rtc_win::GetVersion() >= rtc::rtc_win::Version::VERSION_VISTA) {
    return true;
  }
  if (rtc::rtc_win::GetVersion() < rtc::rtc_win::Version::VERSION_XP) {
    return false;
  }
  DWORD protbuff_size = 4096;
  std::unique_ptr<char[]> protocols;
  LPWSAPROTOCOL_INFOW protocol_infos = nullptr;
  int requested_protocols[2] = {AF_INET6, 0};

  int err = 0;
  int ret = 0;
  // Check for protocols in a do-while loop until we provide a buffer large
  // enough. (WSCEnumProtocols sets protbuff_size to its desired value).
  // It is extremely unlikely that this will loop more than once.
  do {
    protocols.reset(new char[protbuff_size]);
    protocol_infos = reinterpret_cast<LPWSAPROTOCOL_INFOW>(protocols.get());
    ret = WSCEnumProtocols(requested_protocols, protocol_infos, &protbuff_size,
                           &err);
  } while (ret == SOCKET_ERROR && err == WSAENOBUFS);

  if (ret == SOCKET_ERROR) {
    return false;
  }

  // Even if ret is positive, check specifically for IPv6.
  // Non-IPv6 enabled WinXP will still return a RAW protocol.
  for (int i = 0; i < ret; ++i) {
    if (protocol_infos[i].iAddressFamily == AF_INET6) {
      return true;
    }
  }
  return false;
#elif defined(WEBRTC_POSIX) && !defined(__native_client__)
  bool has_ipv6 = false;
  struct ifaddrs* ifa;
  if (getifaddrs(&ifa) < 0) {
    return false;
  }
  for (struct ifaddrs* cur = ifa; cur != nullptr; cur = cur->ifa_next) {
    if (cur->ifa_addr != nullptr && cur->ifa_addr->sa_family == AF_INET6) {
      has_ipv6 = true;
      break;
    }
  }
  freeifaddrs(ifa);
  return has_ipv6;
#else
  return true;
#endif
}
}  // namespace rtc
