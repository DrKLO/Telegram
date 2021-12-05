/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/receiver.h"

#include <assert.h>

#include <cstdint>
#include <cstdlib>
#include <utility>
#include <vector>

#include "absl/memory/memory.h"
#include "api/video/encoded_image.h"
#include "modules/video_coding/encoded_frame.h"
#include "modules/video_coding/internal_defines.h"
#include "modules/video_coding/jitter_buffer_common.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/trace_event.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {

enum { kMaxReceiverDelayMs = 10000 };

VCMReceiver::VCMReceiver(VCMTiming* timing, Clock* clock)
    : VCMReceiver::VCMReceiver(timing,
                               clock,
                               absl::WrapUnique(EventWrapper::Create()),
                               absl::WrapUnique(EventWrapper::Create())) {}

VCMReceiver::VCMReceiver(VCMTiming* timing,
                         Clock* clock,
                         std::unique_ptr<EventWrapper> receiver_event,
                         std::unique_ptr<EventWrapper> jitter_buffer_event)
    : clock_(clock),
      jitter_buffer_(clock_, std::move(jitter_buffer_event)),
      timing_(timing),
      render_wait_event_(std::move(receiver_event)),
      max_video_delay_ms_(kMaxVideoDelayMs) {
  jitter_buffer_.Start();
}

VCMReceiver::~VCMReceiver() {
  render_wait_event_->Set();
}

int32_t VCMReceiver::InsertPacket(const VCMPacket& packet) {
  // Insert the packet into the jitter buffer. The packet can either be empty or
  // contain media at this point.
  bool retransmitted = false;
  const VCMFrameBufferEnum ret =
      jitter_buffer_.InsertPacket(packet, &retransmitted);
  if (ret == kOldPacket) {
    return VCM_OK;
  } else if (ret == kFlushIndicator) {
    return VCM_FLUSH_INDICATOR;
  } else if (ret < 0) {
    return VCM_JITTER_BUFFER_ERROR;
  }
  if (ret == kCompleteSession && !retransmitted) {
    // We don't want to include timestamps which have suffered from
    // retransmission here, since we compensate with extra retransmission
    // delay within the jitter estimate.
    timing_->IncomingTimestamp(packet.timestamp, clock_->TimeInMilliseconds());
  }
  return VCM_OK;
}

VCMEncodedFrame* VCMReceiver::FrameForDecoding(uint16_t max_wait_time_ms,
                                               bool prefer_late_decoding) {
  const int64_t start_time_ms = clock_->TimeInMilliseconds();
  uint32_t frame_timestamp = 0;
  int min_playout_delay_ms = -1;
  int max_playout_delay_ms = -1;
  int64_t render_time_ms = 0;
  // Exhaust wait time to get a complete frame for decoding.
  VCMEncodedFrame* found_frame =
      jitter_buffer_.NextCompleteFrame(max_wait_time_ms);

  if (found_frame) {
    frame_timestamp = found_frame->Timestamp();
    min_playout_delay_ms = found_frame->EncodedImage().playout_delay_.min_ms;
    max_playout_delay_ms = found_frame->EncodedImage().playout_delay_.max_ms;
  } else {
    return nullptr;
  }

  if (min_playout_delay_ms >= 0)
    timing_->set_min_playout_delay(min_playout_delay_ms);

  if (max_playout_delay_ms >= 0)
    timing_->set_max_playout_delay(max_playout_delay_ms);

  // We have a frame - Set timing and render timestamp.
  timing_->SetJitterDelay(jitter_buffer_.EstimatedJitterMs());
  const int64_t now_ms = clock_->TimeInMilliseconds();
  timing_->UpdateCurrentDelay(frame_timestamp);
  render_time_ms = timing_->RenderTimeMs(frame_timestamp, now_ms);
  // Check render timing.
  bool timing_error = false;
  // Assume that render timing errors are due to changes in the video stream.
  if (render_time_ms < 0) {
    timing_error = true;
  } else if (std::abs(render_time_ms - now_ms) > max_video_delay_ms_) {
    int frame_delay = static_cast<int>(std::abs(render_time_ms - now_ms));
    RTC_LOG(LS_WARNING)
        << "A frame about to be decoded is out of the configured "
           "delay bounds ("
        << frame_delay << " > " << max_video_delay_ms_
        << "). Resetting the video jitter buffer.";
    timing_error = true;
  } else if (static_cast<int>(timing_->TargetVideoDelay()) >
             max_video_delay_ms_) {
    RTC_LOG(LS_WARNING) << "The video target delay has grown larger than "
                        << max_video_delay_ms_
                        << " ms. Resetting jitter buffer.";
    timing_error = true;
  }

  if (timing_error) {
    // Timing error => reset timing and flush the jitter buffer.
    jitter_buffer_.Flush();
    timing_->Reset();
    return NULL;
  }

  if (prefer_late_decoding) {
    // Decode frame as close as possible to the render timestamp.
    const int32_t available_wait_time =
        max_wait_time_ms -
        static_cast<int32_t>(clock_->TimeInMilliseconds() - start_time_ms);
    uint16_t new_max_wait_time =
        static_cast<uint16_t>(VCM_MAX(available_wait_time, 0));
    uint32_t wait_time_ms = rtc::saturated_cast<uint32_t>(
        timing_->MaxWaitingTime(render_time_ms, clock_->TimeInMilliseconds()));
    if (new_max_wait_time < wait_time_ms) {
      // We're not allowed to wait until the frame is supposed to be rendered,
      // waiting as long as we're allowed to avoid busy looping, and then return
      // NULL. Next call to this function might return the frame.
      render_wait_event_->Wait(new_max_wait_time);
      return NULL;
    }
    // Wait until it's time to render.
    render_wait_event_->Wait(wait_time_ms);
  }

  // Extract the frame from the jitter buffer and set the render time.
  VCMEncodedFrame* frame = jitter_buffer_.ExtractAndSetDecode(frame_timestamp);
  if (frame == NULL) {
    return NULL;
  }
  frame->SetRenderTime(render_time_ms);
  TRACE_EVENT_ASYNC_STEP1("webrtc", "Video", frame->Timestamp(), "SetRenderTS",
                          "render_time", frame->RenderTimeMs());
  return frame;
}

void VCMReceiver::ReleaseFrame(VCMEncodedFrame* frame) {
  jitter_buffer_.ReleaseFrame(frame);
}

void VCMReceiver::SetNackSettings(size_t max_nack_list_size,
                                  int max_packet_age_to_nack,
                                  int max_incomplete_time_ms) {
  jitter_buffer_.SetNackSettings(max_nack_list_size, max_packet_age_to_nack,
                                 max_incomplete_time_ms);
}

std::vector<uint16_t> VCMReceiver::NackList(bool* request_key_frame) {
  return jitter_buffer_.GetNackList(request_key_frame);
}

}  // namespace webrtc
