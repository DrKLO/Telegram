/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <string>
#include <utility>

#include "call/adaptation/video_stream_adapter.h"
#include "rtc_base/synchronization/sequence_checker.h"
#include "video/adaptation/bitrate_constraint.h"

namespace webrtc {

BitrateConstraint::BitrateConstraint()
    : encoder_settings_(absl::nullopt),
      encoder_target_bitrate_bps_(absl::nullopt) {
  sequence_checker_.Detach();
}

void BitrateConstraint::OnEncoderSettingsUpdated(
    absl::optional<EncoderSettings> encoder_settings) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  encoder_settings_ = std::move(encoder_settings);
}

void BitrateConstraint::OnEncoderTargetBitrateUpdated(
    absl::optional<uint32_t> encoder_target_bitrate_bps) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  encoder_target_bitrate_bps_ = std::move(encoder_target_bitrate_bps);
}

bool BitrateConstraint::IsAdaptationUpAllowed(
    const VideoStreamInputState& input_state,
    const VideoSourceRestrictions& restrictions_before,
    const VideoSourceRestrictions& restrictions_after) const {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  // Make sure bitrate limits are not violated.
  if (DidIncreaseResolution(restrictions_before, restrictions_after)) {
    uint32_t bitrate_bps = encoder_target_bitrate_bps_.value_or(0);
    absl::optional<VideoEncoder::ResolutionBitrateLimits> bitrate_limits =
        encoder_settings_.has_value()
            ? encoder_settings_->encoder_info()
                  .GetEncoderBitrateLimitsForResolution(
                      // Need some sort of expected resulting pixels to be used
                      // instead of unrestricted.
                      GetHigherResolutionThan(
                          input_state.frame_size_pixels().value()))
            : absl::nullopt;
    if (bitrate_limits.has_value() && bitrate_bps != 0) {
      RTC_DCHECK_GE(bitrate_limits->frame_size_pixels,
                    input_state.frame_size_pixels().value());
      return bitrate_bps >=
             static_cast<uint32_t>(bitrate_limits->min_start_bitrate_bps);
    }
  }
  return true;
}

}  // namespace webrtc
