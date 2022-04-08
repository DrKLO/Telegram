/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/rtc_event_log/rtc_event.h"

#include "rtc_base/time_utils.h"

namespace webrtc {

RtcEvent::RtcEvent() : timestamp_us_(rtc::TimeMillis() * 1000) {}

}  // namespace webrtc
