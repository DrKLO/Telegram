/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_PROXY_INFO_H_
#define RTC_BASE_PROXY_INFO_H_

#include <string>

#include "rtc_base/crypt_string.h"
#include "rtc_base/socket_address.h"

namespace rtc {

enum ProxyType { PROXY_NONE, PROXY_HTTPS, PROXY_SOCKS5, PROXY_UNKNOWN };
const char* ProxyToString(ProxyType proxy);

struct ProxyInfo {
  ProxyType type;
  SocketAddress address;
  std::string autoconfig_url;
  bool autodetect;
  std::string bypass_list;
  std::string username;
  CryptString password;

  ProxyInfo();
  ~ProxyInfo();
};

}  // namespace rtc

#endif  // RTC_BASE_PROXY_INFO_H_
