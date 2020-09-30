/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "call/adaptation/video_source_restrictions.h"

#include <limits>

#include "rtc_base/checks.h"
#include "rtc_base/strings/string_builder.h"

namespace webrtc {

VideoSourceRestrictions::VideoSourceRestrictions()
    : max_pixels_per_frame_(absl::nullopt),
      target_pixels_per_frame_(absl::nullopt),
      max_frame_rate_(absl::nullopt) {}

VideoSourceRestrictions::VideoSourceRestrictions(
    absl::optional<size_t> max_pixels_per_frame,
    absl::optional<size_t> target_pixels_per_frame,
    absl::optional<double> max_frame_rate)
    : max_pixels_per_frame_(std::move(max_pixels_per_frame)),
      target_pixels_per_frame_(std::move(target_pixels_per_frame)),
      max_frame_rate_(std::move(max_frame_rate)) {
  RTC_DCHECK(!max_pixels_per_frame_.has_value() ||
             max_pixels_per_frame_.value() <
                 static_cast<size_t>(std::numeric_limits<int>::max()));
  RTC_DCHECK(!max_frame_rate_.has_value() ||
             max_frame_rate_.value() < std::numeric_limits<int>::max());
  RTC_DCHECK(!max_frame_rate_.has_value() || max_frame_rate_.value() > 0.0);
}

std::string VideoSourceRestrictions::ToString() const {
  rtc::StringBuilder ss;
  ss << "{";
  if (max_frame_rate_)
    ss << " max_fps=" << max_frame_rate_.value();
  if (max_pixels_per_frame_)
    ss << " max_pixels_per_frame=" << max_pixels_per_frame_.value();
  if (target_pixels_per_frame_)
    ss << " target_pixels_per_frame=" << target_pixels_per_frame_.value();
  ss << " }";
  return ss.Release();
}

const absl::optional<size_t>& VideoSourceRestrictions::max_pixels_per_frame()
    const {
  return max_pixels_per_frame_;
}

const absl::optional<size_t>& VideoSourceRestrictions::target_pixels_per_frame()
    const {
  return target_pixels_per_frame_;
}

const absl::optional<double>& VideoSourceRestrictions::max_frame_rate() const {
  return max_frame_rate_;
}

void VideoSourceRestrictions::set_max_pixels_per_frame(
    absl::optional<size_t> max_pixels_per_frame) {
  max_pixels_per_frame_ = std::move(max_pixels_per_frame);
}

void VideoSourceRestrictions::set_target_pixels_per_frame(
    absl::optional<size_t> target_pixels_per_frame) {
  target_pixels_per_frame_ = std::move(target_pixels_per_frame);
}

void VideoSourceRestrictions::set_max_frame_rate(
    absl::optional<double> max_frame_rate) {
  max_frame_rate_ = std::move(max_frame_rate);
}

bool DidRestrictionsIncrease(VideoSourceRestrictions before,
                             VideoSourceRestrictions after) {
  bool decreased_resolution = DidDecreaseResolution(before, after);
  bool decreased_framerate = DidDecreaseFrameRate(before, after);
  bool same_resolution =
      before.max_pixels_per_frame() == after.max_pixels_per_frame();
  bool same_framerate = before.max_frame_rate() == after.max_frame_rate();

  return (decreased_resolution && decreased_framerate) ||
         (decreased_resolution && same_framerate) ||
         (same_resolution && decreased_framerate);
}

bool DidRestrictionsDecrease(VideoSourceRestrictions before,
                             VideoSourceRestrictions after) {
  bool increased_resolution = DidIncreaseResolution(before, after);
  bool increased_framerate = DidIncreaseFrameRate(before, after);
  bool same_resolution =
      before.max_pixels_per_frame() == after.max_pixels_per_frame();
  bool same_framerate = before.max_frame_rate() == after.max_frame_rate();

  return (increased_resolution && increased_framerate) ||
         (increased_resolution && same_framerate) ||
         (same_resolution && increased_framerate);
}

bool DidIncreaseResolution(VideoSourceRestrictions restrictions_before,
                           VideoSourceRestrictions restrictions_after) {
  if (!restrictions_before.max_pixels_per_frame().has_value())
    return false;
  if (!restrictions_after.max_pixels_per_frame().has_value())
    return true;
  return restrictions_after.max_pixels_per_frame().value() >
         restrictions_before.max_pixels_per_frame().value();
}

bool DidDecreaseResolution(VideoSourceRestrictions restrictions_before,
                           VideoSourceRestrictions restrictions_after) {
  if (!restrictions_after.max_pixels_per_frame().has_value())
    return false;
  if (!restrictions_before.max_pixels_per_frame().has_value())
    return true;
  return restrictions_after.max_pixels_per_frame().value() <
         restrictions_before.max_pixels_per_frame().value();
}

bool DidIncreaseFrameRate(VideoSourceRestrictions restrictions_before,
                          VideoSourceRestrictions restrictions_after) {
  if (!restrictions_before.max_frame_rate().has_value())
    return false;
  if (!restrictions_after.max_frame_rate().has_value())
    return true;
  return restrictions_after.max_frame_rate().value() >
         restrictions_before.max_frame_rate().value();
}

bool DidDecreaseFrameRate(VideoSourceRestrictions restrictions_before,
                          VideoSourceRestrictions restrictions_after) {
  if (!restrictions_after.max_frame_rate().has_value())
    return false;
  if (!restrictions_before.max_frame_rate().has_value())
    return true;
  return restrictions_after.max_frame_rate().value() <
         restrictions_before.max_frame_rate().value();
}

}  // namespace webrtc
