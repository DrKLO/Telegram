/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/rtp_transceiver_interface.h"

#include "rtc_base/checks.h"

namespace webrtc {

RtpTransceiverInit::RtpTransceiverInit() = default;

RtpTransceiverInit::RtpTransceiverInit(const RtpTransceiverInit& rhs) = default;

RtpTransceiverInit::~RtpTransceiverInit() = default;

absl::optional<RtpTransceiverDirection>
RtpTransceiverInterface::fired_direction() const {
  return absl::nullopt;
}

bool RtpTransceiverInterface::stopping() const {
  return false;
}

void RtpTransceiverInterface::Stop() {
  StopInternal();
}

RTCError RtpTransceiverInterface::StopStandard() {
  RTC_DCHECK_NOTREACHED()
      << "DEBUG: RtpTransceiverInterface::StopStandard called";
  return RTCError::OK();
}

void RtpTransceiverInterface::StopInternal() {
  RTC_DCHECK_NOTREACHED()
      << "DEBUG: RtpTransceiverInterface::StopInternal called";
}

// TODO(bugs.webrtc.org/11839) Remove default implementations when clients
// are updated.
void RtpTransceiverInterface::SetDirection(
    RtpTransceiverDirection new_direction) {
  SetDirectionWithError(new_direction);
}

RTCError RtpTransceiverInterface::SetDirectionWithError(
    RtpTransceiverDirection new_direction) {
  RTC_DCHECK_NOTREACHED() << "Default implementation called";
  return RTCError::OK();
}

}  // namespace webrtc
