/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_CODING_TIMING_H_
#define MODULES_VIDEO_CODING_TIMING_H_

#include <memory>

#include "absl/types/optional.h"
#include "api/video/video_timing.h"
#include "modules/video_coding/codec_timer.h"
#include "rtc_base/experiments/field_trial_parser.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/thread_annotations.h"
#include "rtc_base/time/timestamp_extrapolator.h"

namespace webrtc {

class Clock;
class TimestampExtrapolator;

class VCMTiming {
 public:
  explicit VCMTiming(Clock* clock);
  virtual ~VCMTiming() = default;

  // Resets the timing to the initial state.
  void Reset();

  // Set the amount of time needed to render an image. Defaults to 10 ms.
  void set_render_delay(int render_delay_ms);

  // Set the minimum time the video must be delayed on the receiver to
  // get the desired jitter buffer level.
  void SetJitterDelay(int required_delay_ms);

  // Set/get the minimum playout delay from capture to render in ms.
  void set_min_playout_delay(int min_playout_delay_ms);
  int min_playout_delay();

  // Set/get the maximum playout delay from capture to render in ms.
  void set_max_playout_delay(int max_playout_delay_ms);
  int max_playout_delay();

  // Increases or decreases the current delay to get closer to the target delay.
  // Calculates how long it has been since the previous call to this function,
  // and increases/decreases the delay in proportion to the time difference.
  void UpdateCurrentDelay(uint32_t frame_timestamp);

  // Increases or decreases the current delay to get closer to the target delay.
  // Given the actual decode time in ms and the render time in ms for a frame,
  // this function calculates how late the frame is and increases the delay
  // accordingly.
  void UpdateCurrentDelay(int64_t render_time_ms,
                          int64_t actual_decode_time_ms);

  // Stops the decoder timer, should be called when the decoder returns a frame
  // or when the decoded frame callback is called.
  void StopDecodeTimer(int32_t decode_time_ms, int64_t now_ms);
  // TODO(kron): Remove once downstream projects has been changed to use the
  // above function.
  void StopDecodeTimer(uint32_t time_stamp,
                       int32_t decode_time_ms,
                       int64_t now_ms,
                       int64_t render_time_ms);

  // Used to report that a frame is passed to decoding. Updates the timestamp
  // filter which is used to map between timestamps and receiver system time.
  void IncomingTimestamp(uint32_t time_stamp, int64_t last_packet_time_ms);

  // Returns the receiver system time when the frame with timestamp
  // |frame_timestamp| should be rendered, assuming that the system time
  // currently is |now_ms|.
  virtual int64_t RenderTimeMs(uint32_t frame_timestamp, int64_t now_ms) const;

  // Returns the maximum time in ms that we can wait for a frame to become
  // complete before we must pass it to the decoder.
  virtual int64_t MaxWaitingTime(int64_t render_time_ms, int64_t now_ms) const;

  // Returns the current target delay which is required delay + decode time +
  // render delay.
  int TargetVideoDelay() const;

  // Return current timing information. Returns true if the first frame has been
  // decoded, false otherwise.
  virtual bool GetTimings(int* max_decode_ms,
                          int* current_delay_ms,
                          int* target_delay_ms,
                          int* jitter_buffer_ms,
                          int* min_playout_delay_ms,
                          int* render_delay_ms) const;

  void SetTimingFrameInfo(const TimingFrameInfo& info);
  absl::optional<TimingFrameInfo> GetTimingFrameInfo();

  void SetMaxCompositionDelayInFrames(
      absl::optional<int> max_composition_delay_in_frames);
  absl::optional<int> MaxCompositionDelayInFrames() const;

  enum { kDefaultRenderDelayMs = 10 };
  enum { kDelayMaxChangeMsPerS = 100 };

 protected:
  int RequiredDecodeTimeMs() const RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_);
  int64_t RenderTimeMsInternal(uint32_t frame_timestamp, int64_t now_ms) const
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_);
  int TargetDelayInternal() const RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

 private:
  mutable Mutex mutex_;
  Clock* const clock_;
  const std::unique_ptr<TimestampExtrapolator> ts_extrapolator_
      RTC_PT_GUARDED_BY(mutex_);
  std::unique_ptr<VCMCodecTimer> codec_timer_ RTC_GUARDED_BY(mutex_)
      RTC_PT_GUARDED_BY(mutex_);
  int render_delay_ms_ RTC_GUARDED_BY(mutex_);
  // Best-effort playout delay range for frames from capture to render.
  // The receiver tries to keep the delay between |min_playout_delay_ms_|
  // and |max_playout_delay_ms_| taking the network jitter into account.
  // A special case is where min_playout_delay_ms_ = max_playout_delay_ms_ = 0,
  // in which case the receiver tries to play the frames as they arrive.
  int min_playout_delay_ms_ RTC_GUARDED_BY(mutex_);
  int max_playout_delay_ms_ RTC_GUARDED_BY(mutex_);
  int jitter_delay_ms_ RTC_GUARDED_BY(mutex_);
  int current_delay_ms_ RTC_GUARDED_BY(mutex_);
  uint32_t prev_frame_timestamp_ RTC_GUARDED_BY(mutex_);
  absl::optional<TimingFrameInfo> timing_frame_info_ RTC_GUARDED_BY(mutex_);
  size_t num_decoded_frames_ RTC_GUARDED_BY(mutex_);
  // Set by the field trial WebRTC-LowLatencyRenderer. The parameter enabled
  // determines if the low-latency renderer algorithm should be used for the
  // case min playout delay=0 and max playout delay>0.
  FieldTrialParameter<bool> low_latency_renderer_enabled_
      RTC_GUARDED_BY(mutex_);
  absl::optional<int> max_composition_delay_in_frames_ RTC_GUARDED_BY(mutex_);
};
}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_TIMING_H_
