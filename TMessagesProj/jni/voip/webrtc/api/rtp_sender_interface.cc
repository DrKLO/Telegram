/*
 *  Copyright 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/rtp_sender_interface.h"

#include "rtc_base/checks.h"

namespace webrtc {

void RtpSenderInterface::SetParametersAsync(const RtpParameters& parameters,
                                            SetParametersCallback callback) {
  RTC_DCHECK_NOTREACHED() << "Default implementation called";
}

}  // namespace webrtc
