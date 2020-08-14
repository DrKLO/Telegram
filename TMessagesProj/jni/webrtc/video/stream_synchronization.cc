/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/stream_synchronization.h"

#include <stdlib.h>

#include <algorithm>

#include "rtc_base/logging.h"

namespace webrtc {

static const int kMaxChangeMs = 80;
static const int kMaxDeltaDelayMs = 10000;
static const int kFilterLength = 4;
// Minimum difference between audio and video to warrant a change.
static const int kMinDeltaMs = 30;

StreamSynchronization::StreamSynchronization(uint32_t video_stream_id,
                                             uint32_t audio_stream_id)
    : video_stream_id_(video_stream_id),
      audio_stream_id_(audio_stream_id),
      base_target_delay_ms_(0),
      avg_diff_ms_(0) {}

bool StreamSynchronization::ComputeRelativeDelay(
    const Measurements& audio_measurement,
    const Measurements& video_measurement,
    int* relative_delay_ms) {
  int64_t audio_last_capture_time_ms;
  if (!audio_measurement.rtp_to_ntp.Estimate(audio_measurement.latest_timestamp,
                                             &audio_last_capture_time_ms)) {
    return false;
  }
  int64_t video_last_capture_time_ms;
  if (!video_measurement.rtp_to_ntp.Estimate(video_measurement.latest_timestamp,
                                             &video_last_capture_time_ms)) {
    return false;
  }
  if (video_last_capture_time_ms < 0) {
    return false;
  }
  // Positive diff means that video_measurement is behind audio_measurement.
  *relative_delay_ms =
      video_measurement.latest_receive_time_ms -
      audio_measurement.latest_receive_time_ms -
      (video_last_capture_time_ms - audio_last_capture_time_ms);

  if (*relative_delay_ms > kMaxDeltaDelayMs ||
      *relative_delay_ms < -kMaxDeltaDelayMs) {
    return false;
  }
  return true;
}

bool StreamSynchronization::ComputeDelays(int relative_delay_ms,
                                          int current_audio_delay_ms,
                                          int* total_audio_delay_target_ms,
                                          int* total_video_delay_target_ms) {
  int current_video_delay_ms = *total_video_delay_target_ms;

  RTC_LOG(LS_VERBOSE) << "Audio delay: " << current_audio_delay_ms
                      << " current diff: " << relative_delay_ms
                      << " for stream " << audio_stream_id_;

  // Calculate the difference between the lowest possible video delay and the
  // current audio delay.
  int current_diff_ms =
      current_video_delay_ms - current_audio_delay_ms + relative_delay_ms;

  avg_diff_ms_ =
      ((kFilterLength - 1) * avg_diff_ms_ + current_diff_ms) / kFilterLength;
  if (abs(avg_diff_ms_) < kMinDeltaMs) {
    // Don't adjust if the diff is within our margin.
    return false;
  }

  // Make sure we don't move too fast.
  int diff_ms = avg_diff_ms_ / 2;
  diff_ms = std::min(diff_ms, kMaxChangeMs);
  diff_ms = std::max(diff_ms, -kMaxChangeMs);

  // Reset the average after a move to prevent overshooting reaction.
  avg_diff_ms_ = 0;

  if (diff_ms > 0) {
    // The minimum video delay is longer than the current audio delay.
    // We need to decrease extra video delay, or add extra audio delay.
    if (video_delay_.extra_ms > base_target_delay_ms_) {
      // We have extra delay added to ViE. Reduce this delay before adding
      // extra delay to VoE.
      video_delay_.extra_ms -= diff_ms;
      audio_delay_.extra_ms = base_target_delay_ms_;
    } else {  // video_delay_.extra_ms > 0
      // We have no extra video delay to remove, increase the audio delay.
      audio_delay_.extra_ms += diff_ms;
      video_delay_.extra_ms = base_target_delay_ms_;
    }
  } else {  // if (diff_ms > 0)
    // The video delay is lower than the current audio delay.
    // We need to decrease extra audio delay, or add extra video delay.
    if (audio_delay_.extra_ms > base_target_delay_ms_) {
      // We have extra delay in VoiceEngine.
      // Start with decreasing the voice delay.
      // Note: diff_ms is negative; add the negative difference.
      audio_delay_.extra_ms += diff_ms;
      video_delay_.extra_ms = base_target_delay_ms_;
    } else {  // audio_delay_.extra_ms > base_target_delay_ms_
      // We have no extra delay in VoiceEngine, increase the video delay.
      // Note: diff_ms is negative; subtract the negative difference.
      video_delay_.extra_ms -= diff_ms;  // X - (-Y) = X + Y.
      audio_delay_.extra_ms = base_target_delay_ms_;
    }
  }

  // Make sure that video is never below our target.
  video_delay_.extra_ms =
      std::max(video_delay_.extra_ms, base_target_delay_ms_);

  int new_video_delay_ms;
  if (video_delay_.extra_ms > base_target_delay_ms_) {
    new_video_delay_ms = video_delay_.extra_ms;
  } else {
    // No change to the extra video delay. We are changing audio and we only
    // allow to change one at the time.
    new_video_delay_ms = video_delay_.last_ms;
  }

  // Make sure that we don't go below the extra video delay.
  new_video_delay_ms = std::max(new_video_delay_ms, video_delay_.extra_ms);

  // Verify we don't go above the maximum allowed video delay.
  new_video_delay_ms =
      std::min(new_video_delay_ms, base_target_delay_ms_ + kMaxDeltaDelayMs);

  int new_audio_delay_ms;
  if (audio_delay_.extra_ms > base_target_delay_ms_) {
    new_audio_delay_ms = audio_delay_.extra_ms;
  } else {
    // No change to the audio delay. We are changing video and we only allow to
    // change one at the time.
    new_audio_delay_ms = audio_delay_.last_ms;
  }

  // Make sure that we don't go below the extra audio delay.
  new_audio_delay_ms = std::max(new_audio_delay_ms, audio_delay_.extra_ms);

  // Verify we don't go above the maximum allowed audio delay.
  new_audio_delay_ms =
      std::min(new_audio_delay_ms, base_target_delay_ms_ + kMaxDeltaDelayMs);

  video_delay_.last_ms = new_video_delay_ms;
  audio_delay_.last_ms = new_audio_delay_ms;

  RTC_LOG(LS_VERBOSE) << "Sync video delay " << new_video_delay_ms
                      << " for video stream " << video_stream_id_
                      << " and audio delay " << audio_delay_.extra_ms
                      << " for audio stream " << audio_stream_id_;

  *total_video_delay_target_ms = new_video_delay_ms;
  *total_audio_delay_target_ms = new_audio_delay_ms;
  return true;
}

void StreamSynchronization::SetTargetBufferingDelay(int target_delay_ms) {
  // Initial extra delay for audio (accounting for existing extra delay).
  audio_delay_.extra_ms += target_delay_ms - base_target_delay_ms_;
  audio_delay_.last_ms += target_delay_ms - base_target_delay_ms_;

  // The video delay is compared to the last value (and how much we can update
  // is limited by that as well).
  video_delay_.last_ms += target_delay_ms - base_target_delay_ms_;
  video_delay_.extra_ms += target_delay_ms - base_target_delay_ms_;

  // Video is already delayed by the desired amount.
  base_target_delay_ms_ = target_delay_ms;
}

}  // namespace webrtc
