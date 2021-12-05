/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "call/flexfec_receive_stream.h"

#include "rtc_base/checks.h"

namespace webrtc {

FlexfecReceiveStream::Config::Config(Transport* rtcp_send_transport)
    : rtcp_send_transport(rtcp_send_transport) {
  RTC_DCHECK(rtcp_send_transport);
}

FlexfecReceiveStream::Config::Config(const Config& config) = default;

FlexfecReceiveStream::Config::~Config() = default;

}  // namespace webrtc
