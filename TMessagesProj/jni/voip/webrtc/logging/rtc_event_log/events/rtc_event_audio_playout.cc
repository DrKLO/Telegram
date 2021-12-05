/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/events/rtc_event_audio_playout.h"

#include "absl/memory/memory.h"

namespace webrtc {

RtcEventAudioPlayout::RtcEventAudioPlayout(uint32_t ssrc) : ssrc_(ssrc) {}

RtcEventAudioPlayout::RtcEventAudioPlayout(const RtcEventAudioPlayout& other)
    : RtcEvent(other.timestamp_us_), ssrc_(other.ssrc_) {}

std::unique_ptr<RtcEventAudioPlayout> RtcEventAudioPlayout::Copy() const {
  return absl::WrapUnique<RtcEventAudioPlayout>(
      new RtcEventAudioPlayout(*this));
}

}  // namespace webrtc
