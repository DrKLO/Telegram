/*
 *  Copyright 2008 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_NET_HELPERS_H_
#define RTC_BASE_NET_HELPERS_H_

#if defined(WEBRTC_POSIX)
#include <sys/socket.h>
#elif WEBRTC_WIN
#include <winsock2.h>  // NOLINT

#include "rtc_base/win32.h"
#endif

namespace rtc {

// rtc namespaced wrappers for inet_ntop and inet_pton so we can avoid
// the windows-native versions of these.
const char* inet_ntop(int af, const void* src, char* dst, socklen_t size);
int inet_pton(int af, const char* src, void* dst);

bool HasIPv4Enabled();
bool HasIPv6Enabled();

}  // namespace rtc

#endif  // RTC_BASE_NET_HELPERS_H_
