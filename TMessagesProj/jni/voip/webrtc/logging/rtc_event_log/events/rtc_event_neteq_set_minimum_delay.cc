/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/events/rtc_event_neteq_set_minimum_delay.h"

#include <stdint.h>

#include <map>
#include <memory>
#include <string>
#include <vector>

#include "api/rtc_event_log/rtc_event.h"
#include "api/units/timestamp.h"
#include "logging/rtc_event_log/events/rtc_event_definition.h"

namespace webrtc {

RtcEventNetEqSetMinimumDelay::RtcEventNetEqSetMinimumDelay(uint32_t remote_ssrc,
                                                           int delay_ms)
    : remote_ssrc_(remote_ssrc), minimum_delay_ms_(delay_ms) {}
RtcEventNetEqSetMinimumDelay::~RtcEventNetEqSetMinimumDelay() {}

}  // namespace webrtc
