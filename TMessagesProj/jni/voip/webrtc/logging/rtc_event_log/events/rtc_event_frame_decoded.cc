/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/events/rtc_event_frame_decoded.h"

#include "absl/memory/memory.h"

namespace webrtc {

RtcEventFrameDecoded::RtcEventFrameDecoded(int64_t render_time_ms,
                                           uint32_t ssrc,
                                           int width,
                                           int height,
                                           VideoCodecType codec,
                                           uint8_t qp)
    : render_time_ms_(render_time_ms),
      ssrc_(ssrc),
      width_(width),
      height_(height),
      codec_(codec),
      qp_(qp) {}

RtcEventFrameDecoded::RtcEventFrameDecoded(const RtcEventFrameDecoded& other)
    : RtcEvent(other.timestamp_us_),
      render_time_ms_(other.render_time_ms_),
      ssrc_(other.ssrc_),
      width_(other.width_),
      height_(other.height_),
      codec_(other.codec_),
      qp_(other.qp_) {}

RtcEvent::Type RtcEventFrameDecoded::GetType() const {
  return RtcEvent::Type::FrameDecoded;
}

bool RtcEventFrameDecoded::IsConfigEvent() const {
  return false;
}

std::unique_ptr<RtcEventFrameDecoded> RtcEventFrameDecoded::Copy() const {
  return absl::WrapUnique<RtcEventFrameDecoded>(
      new RtcEventFrameDecoded(*this));
}

}  // namespace webrtc
