/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef VIDEO_VIDEO_SOURCE_SINK_CONTROLLER_H_
#define VIDEO_VIDEO_SOURCE_SINK_CONTROLLER_H_

#include <string>

#include "absl/types/optional.h"
#include "api/video/video_frame.h"
#include "api/video/video_sink_interface.h"
#include "api/video/video_source_interface.h"
#include "call/adaptation/video_source_restrictions.h"
#include "rtc_base/synchronization/mutex.h"

namespace webrtc {

// Responsible for configuring source/sink settings, i.e. performing
// rtc::VideoSourceInterface<VideoFrame>::AddOrUpdateSink(). It does this by
// storing settings internally which are converted to rtc::VideoSinkWants when
// PushSourceSinkSettings() is performed.
class VideoSourceSinkController {
 public:
  VideoSourceSinkController(rtc::VideoSinkInterface<VideoFrame>* sink,
                            rtc::VideoSourceInterface<VideoFrame>* source);

  void SetSource(rtc::VideoSourceInterface<VideoFrame>* source);
  // Must be called in order for changes to settings to have an effect. This
  // allows you to modify multiple properties in a single push to the sink.
  void PushSourceSinkSettings();

  VideoSourceRestrictions restrictions() const;
  absl::optional<size_t> pixels_per_frame_upper_limit() const;
  absl::optional<double> frame_rate_upper_limit() const;
  bool rotation_applied() const;
  int resolution_alignment() const;

  // Updates the settings stored internally. In order for these settings to be
  // applied to the sink, PushSourceSinkSettings() must subsequently be called.
  void SetRestrictions(VideoSourceRestrictions restrictions);
  void SetPixelsPerFrameUpperLimit(
      absl::optional<size_t> pixels_per_frame_upper_limit);
  void SetFrameRateUpperLimit(absl::optional<double> frame_rate_upper_limit);
  void SetRotationApplied(bool rotation_applied);
  void SetResolutionAlignment(int resolution_alignment);

 private:
  rtc::VideoSinkWants CurrentSettingsToSinkWants() const
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  mutable Mutex mutex_;
  rtc::VideoSinkInterface<VideoFrame>* const sink_;
  rtc::VideoSourceInterface<VideoFrame>* source_ RTC_GUARDED_BY(&mutex_);
  // Pixel and frame rate restrictions.
  VideoSourceRestrictions restrictions_ RTC_GUARDED_BY(&mutex_);
  // Ensures that even if we are not restricted, the sink is never configured
  // above this limit. Example: We are not CPU limited (no |restrictions_|) but
  // our encoder is capped at 30 fps (= |frame_rate_upper_limit_|).
  absl::optional<size_t> pixels_per_frame_upper_limit_ RTC_GUARDED_BY(&mutex_);
  absl::optional<double> frame_rate_upper_limit_ RTC_GUARDED_BY(&mutex_);
  bool rotation_applied_ RTC_GUARDED_BY(&mutex_) = false;
  int resolution_alignment_ RTC_GUARDED_BY(&mutex_) = 1;
};

}  // namespace webrtc

#endif  // VIDEO_VIDEO_SOURCE_SINK_CONTROLLER_H_
