/*
 *  Copyright 2017 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/dtls_transport_internal.h"

namespace cricket {

DtlsTransportInternal::DtlsTransportInternal() = default;

DtlsTransportInternal::~DtlsTransportInternal() = default;

webrtc::DtlsTransportState ConvertDtlsTransportState(
    cricket::DtlsTransportState cricket_state) {
  switch (cricket_state) {
    case DtlsTransportState::DTLS_TRANSPORT_NEW:
      return webrtc::DtlsTransportState::kNew;
    case DtlsTransportState::DTLS_TRANSPORT_CONNECTING:
      return webrtc::DtlsTransportState::kConnecting;
    case DtlsTransportState::DTLS_TRANSPORT_CONNECTED:
      return webrtc::DtlsTransportState::kConnected;
    case DtlsTransportState::DTLS_TRANSPORT_CLOSED:
      return webrtc::DtlsTransportState::kClosed;
    case DtlsTransportState::DTLS_TRANSPORT_FAILED:
      return webrtc::DtlsTransportState::kFailed;
  }
  RTC_NOTREACHED();
  return webrtc::DtlsTransportState::kNew;
}

}  // namespace cricket
