/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/media_protocol_names.h"

#include <ctype.h>
#include <stddef.h>

namespace cricket {

// There are multiple variants of the RTP protocol stack, including
// UDP/TLS/RTP/SAVPF (WebRTC default), RTP/AVP, RTP/AVPF, RTP/SAVPF,
// TCP/DTLS/RTP/SAVPF and so on. We accept anything that has RTP/
// embedded in it somewhere as being an RTP protocol.
const char kMediaProtocolRtpPrefix[] = "RTP/";

const char kMediaProtocolSctp[] = "SCTP";
const char kMediaProtocolDtlsSctp[] = "DTLS/SCTP";
const char kMediaProtocolUdpDtlsSctp[] = "UDP/DTLS/SCTP";
const char kMediaProtocolTcpDtlsSctp[] = "TCP/DTLS/SCTP";

bool IsDtlsSctp(const std::string& protocol) {
  return protocol == kMediaProtocolDtlsSctp ||
         protocol == kMediaProtocolUdpDtlsSctp ||
         protocol == kMediaProtocolTcpDtlsSctp;
}

bool IsPlainSctp(const std::string& protocol) {
  return protocol == kMediaProtocolSctp;
}

bool IsRtpProtocol(const std::string& protocol) {
  if (protocol.empty()) {
    return true;
  }
  size_t pos = protocol.find(cricket::kMediaProtocolRtpPrefix);
  if (pos == std::string::npos) {
    return false;
  }
  // RTP must be at the beginning of a string or not preceded by alpha
  if (pos == 0 || !isalpha(protocol[pos - 1])) {
    return true;
  }
  return false;
}

bool IsSctpProtocol(const std::string& protocol) {
  return IsPlainSctp(protocol) || IsDtlsSctp(protocol);
}

}  // namespace cricket
