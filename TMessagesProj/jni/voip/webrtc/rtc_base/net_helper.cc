/*
 *  Copyright 2017 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/net_helper.h"

#include "absl/strings/string_view.h"

namespace cricket {

const char UDP_PROTOCOL_NAME[] = "udp";
const char TCP_PROTOCOL_NAME[] = "tcp";
const char SSLTCP_PROTOCOL_NAME[] = "ssltcp";
const char TLS_PROTOCOL_NAME[] = "tls";

int GetProtocolOverhead(absl::string_view protocol) {
  if (protocol == TCP_PROTOCOL_NAME || protocol == SSLTCP_PROTOCOL_NAME) {
    return kTcpHeaderSize;
  } else if (protocol == UDP_PROTOCOL_NAME) {
    return kUdpHeaderSize;
  } else {
    // TODO(srte): We should crash on unexpected input and handle TLS correctly.
    return 8;
  }
}

}  // namespace cricket
