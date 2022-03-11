/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/frame_decode_timing.h"

#include <algorithm>

#include "absl/types/optional.h"
#include "api/units/time_delta.h"
#include "rtc_base/logging.h"

namespace webrtc {

FrameDecodeTiming::FrameDecodeTiming(Clock* clock,
                                     webrtc::VCMTiming const* timing)
    : clock_(clock), timing_(timing) {
  RTC_DCHECK(clock_);
  RTC_DCHECK(timing_);
}

absl::optional<FrameDecodeTiming::FrameSchedule>
FrameDecodeTiming::OnFrameBufferUpdated(uint32_t next_temporal_unit_rtp,
                                        uint32_t last_temporal_unit_rtp,
                                        bool too_many_frames_queued) {
  const Timestamp now = clock_->CurrentTime();
  Timestamp render_time = Timestamp::Millis(
      timing_->RenderTimeMs(next_temporal_unit_rtp, now.ms()));
  TimeDelta max_wait = TimeDelta::Millis(timing_->MaxWaitingTime(
      render_time.ms(), now.ms(), too_many_frames_queued));

  // If the delay is not too far in the past, or this is the last decodable
  // frame then it is the best frame to be decoded. Otherwise, fast-forward
  // to the next frame in the buffer.
  if (max_wait <= -kMaxAllowedFrameDelay &&
      next_temporal_unit_rtp != last_temporal_unit_rtp) {
    RTC_DLOG(LS_VERBOSE) << "Fast-forwarded frame " << next_temporal_unit_rtp
                         << " render time " << render_time.ms()
                         << " with delay " << max_wait.ms() << "ms";
    return absl::nullopt;
  }
  RTC_DLOG(LS_VERBOSE) << "Selected frame with rtp " << next_temporal_unit_rtp
                       << " render time " << render_time.ms()
                       << " with a max wait of " << max_wait.ms() << "ms";

  Timestamp latest_decode_time = now + std::max(max_wait, TimeDelta::Zero());
  return FrameSchedule{.latest_decode_time = latest_decode_time,
                       .render_time = render_time};
}

}  // namespace webrtc
