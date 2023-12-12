/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/video_source_sink_controller.h"

#include <algorithm>
#include <limits>
#include <utility>

#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/strings/string_builder.h"

namespace webrtc {

VideoSourceSinkController::VideoSourceSinkController(
    rtc::VideoSinkInterface<VideoFrame>* sink,
    rtc::VideoSourceInterface<VideoFrame>* source)
    : sink_(sink), source_(source) {
  RTC_DCHECK(sink_);
}

VideoSourceSinkController::~VideoSourceSinkController() {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
}

void VideoSourceSinkController::SetSource(
    rtc::VideoSourceInterface<VideoFrame>* source) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);

  rtc::VideoSourceInterface<VideoFrame>* old_source = source_;
  source_ = source;

  if (old_source != source && old_source)
    old_source->RemoveSink(sink_);

  if (!source)
    return;

  source->AddOrUpdateSink(sink_, CurrentSettingsToSinkWants());
}

bool VideoSourceSinkController::HasSource() const {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  return source_ != nullptr;
}

void VideoSourceSinkController::RequestRefreshFrame() {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  if (source_)
    source_->RequestRefreshFrame();
}

void VideoSourceSinkController::PushSourceSinkSettings() {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  if (!source_)
    return;
  rtc::VideoSinkWants wants = CurrentSettingsToSinkWants();
  source_->AddOrUpdateSink(sink_, wants);
}

VideoSourceRestrictions VideoSourceSinkController::restrictions() const {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  return restrictions_;
}

absl::optional<size_t> VideoSourceSinkController::pixels_per_frame_upper_limit()
    const {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  return pixels_per_frame_upper_limit_;
}

absl::optional<double> VideoSourceSinkController::frame_rate_upper_limit()
    const {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  return frame_rate_upper_limit_;
}

bool VideoSourceSinkController::rotation_applied() const {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  return rotation_applied_;
}

int VideoSourceSinkController::resolution_alignment() const {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  return resolution_alignment_;
}

const std::vector<rtc::VideoSinkWants::FrameSize>&
VideoSourceSinkController::resolutions() const {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  return resolutions_;
}

bool VideoSourceSinkController::active() const {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  return active_;
}

absl::optional<rtc::VideoSinkWants::FrameSize>
VideoSourceSinkController::requested_resolution() const {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  return requested_resolution_;
}

void VideoSourceSinkController::SetRestrictions(
    VideoSourceRestrictions restrictions) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  restrictions_ = std::move(restrictions);
}

void VideoSourceSinkController::SetPixelsPerFrameUpperLimit(
    absl::optional<size_t> pixels_per_frame_upper_limit) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  pixels_per_frame_upper_limit_ = std::move(pixels_per_frame_upper_limit);
}

void VideoSourceSinkController::SetFrameRateUpperLimit(
    absl::optional<double> frame_rate_upper_limit) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  frame_rate_upper_limit_ = std::move(frame_rate_upper_limit);
}

void VideoSourceSinkController::SetRotationApplied(bool rotation_applied) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  rotation_applied_ = rotation_applied;
}

void VideoSourceSinkController::SetResolutionAlignment(
    int resolution_alignment) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  resolution_alignment_ = resolution_alignment;
}

void VideoSourceSinkController::SetResolutions(
    std::vector<rtc::VideoSinkWants::FrameSize> resolutions) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  resolutions_ = std::move(resolutions);
}

void VideoSourceSinkController::SetActive(bool active) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  active_ = active;
}

void VideoSourceSinkController::SetRequestedResolution(
    absl::optional<rtc::VideoSinkWants::FrameSize> requested_resolution) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  requested_resolution_ = std::move(requested_resolution);
}

// RTC_EXCLUSIVE_LOCKS_REQUIRED(sequence_checker_)
rtc::VideoSinkWants VideoSourceSinkController::CurrentSettingsToSinkWants()
    const {
  rtc::VideoSinkWants wants;
  wants.rotation_applied = rotation_applied_;
  // `wants.black_frames` is not used, it always has its default value false.
  wants.max_pixel_count =
      rtc::dchecked_cast<int>(restrictions_.max_pixels_per_frame().value_or(
          std::numeric_limits<int>::max()));
  wants.target_pixel_count =
      restrictions_.target_pixels_per_frame().has_value()
          ? absl::optional<int>(rtc::dchecked_cast<int>(
                restrictions_.target_pixels_per_frame().value()))
          : absl::nullopt;
  wants.max_framerate_fps =
      restrictions_.max_frame_rate().has_value()
          ? static_cast<int>(restrictions_.max_frame_rate().value())
          : std::numeric_limits<int>::max();
  wants.resolution_alignment = resolution_alignment_;
  wants.max_pixel_count =
      std::min(wants.max_pixel_count,
               rtc::dchecked_cast<int>(pixels_per_frame_upper_limit_.value_or(
                   std::numeric_limits<int>::max())));
  wants.max_framerate_fps =
      std::min(wants.max_framerate_fps,
               frame_rate_upper_limit_.has_value()
                   ? static_cast<int>(frame_rate_upper_limit_.value())
                   : std::numeric_limits<int>::max());
  wants.resolutions = resolutions_;
  wants.is_active = active_;
  wants.requested_resolution = requested_resolution_;
  return wants;
}

}  // namespace webrtc
