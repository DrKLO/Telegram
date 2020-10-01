/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/proxy_info.h"

namespace rtc {

const char* ProxyToString(ProxyType proxy) {
  const char* const PROXY_NAMES[] = {"none", "https", "socks5", "unknown"};
  return PROXY_NAMES[proxy];
}

ProxyInfo::ProxyInfo() : type(PROXY_NONE), autodetect(false) {}
ProxyInfo::~ProxyInfo() = default;

}  // namespace rtc
