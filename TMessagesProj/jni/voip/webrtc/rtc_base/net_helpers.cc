/*
 *  Copyright 2008 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/net_helpers.h"

#include <memory>
#include <string>

#include "absl/strings/string_view.h"

#if defined(WEBRTC_WIN)
#include <ws2spi.h>
#include <ws2tcpip.h>

#endif
#if defined(WEBRTC_POSIX) && !defined(__native_client__)
#include <arpa/inet.h>
#endif  // defined(WEBRTC_POSIX) && !defined(__native_client__)

namespace rtc {

const char* inet_ntop(int af, const void* src, char* dst, socklen_t size) {
#if defined(WEBRTC_WIN)
  return win32_inet_ntop(af, src, dst, size);
#else
  return ::inet_ntop(af, src, dst, size);
#endif
}

int inet_pton(int af, absl::string_view src, void* dst) {
  std::string src_str(src);
#if defined(WEBRTC_WIN)
  return win32_inet_pton(af, src_str.c_str(), dst);
#else
  return ::inet_pton(af, src_str.c_str(), dst);
#endif
}
}  // namespace rtc
