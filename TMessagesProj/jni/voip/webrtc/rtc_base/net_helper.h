/*
 *  Copyright 2017 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef RTC_BASE_NET_HELPER_H_
#define RTC_BASE_NET_HELPER_H_

#include <string>

#include "absl/strings/string_view.h"
#include "rtc_base/system/rtc_export.h"

// This header contains helper functions and constants used by different types
// of transports.
namespace cricket {

RTC_EXPORT extern const char UDP_PROTOCOL_NAME[];
RTC_EXPORT extern const char TCP_PROTOCOL_NAME[];
extern const char SSLTCP_PROTOCOL_NAME[];
extern const char TLS_PROTOCOL_NAME[];

constexpr int kTcpHeaderSize = 20;
constexpr int kUdpHeaderSize = 8;

// Get the transport layer overhead per packet based on the protocol.
int GetProtocolOverhead(absl::string_view protocol);

}  // namespace cricket

#endif  // RTC_BASE_NET_HELPER_H_
