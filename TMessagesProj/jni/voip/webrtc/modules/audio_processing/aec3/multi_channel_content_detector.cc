/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/multi_channel_content_detector.h"

#include <cmath>

#include "rtc_base/checks.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {

namespace {

constexpr int kNumFramesPerSecond = 100;

// Compares the left and right channels in the render `frame` to determine
// whether the signal is a proper stereo signal. To allow for differences
// introduced by hardware drivers, a threshold `detection_threshold` is used for
// the detection.
bool HasStereoContent(const std::vector<std::vector<std::vector<float>>>& frame,
                      float detection_threshold) {
  if (frame[0].size() < 2) {
    return false;
  }

  for (size_t band = 0; band < frame.size(); ++band) {
    for (size_t k = 0; k < frame[band][0].size(); ++k) {
      if (std::fabs(frame[band][0][k] - frame[band][1][k]) >
          detection_threshold) {
        return true;
      }
    }
  }
  return false;
}

// In order to avoid logging metrics for very short lifetimes that are unlikely
// to reflect real calls and that may dilute the "real" data, logging is limited
// to lifetimes of at leats 5 seconds.
constexpr int kMinNumberOfFramesRequiredToLogMetrics = 500;

// Continuous metrics are logged every 10 seconds.
constexpr int kFramesPer10Seconds = 1000;

}  // namespace

MultiChannelContentDetector::MetricsLogger::MetricsLogger() {}

MultiChannelContentDetector::MetricsLogger::~MetricsLogger() {
  if (frame_counter_ < kMinNumberOfFramesRequiredToLogMetrics)
    return;

  RTC_HISTOGRAM_BOOLEAN(
      "WebRTC.Audio.EchoCanceller.PersistentMultichannelContentEverDetected",
      any_multichannel_content_detected_ ? 1 : 0);
}

void MultiChannelContentDetector::MetricsLogger::Update(
    bool persistent_multichannel_content_detected) {
  ++frame_counter_;
  if (persistent_multichannel_content_detected) {
    any_multichannel_content_detected_ = true;
    ++persistent_multichannel_frame_counter_;
  }

  if (frame_counter_ < kMinNumberOfFramesRequiredToLogMetrics)
    return;
  if (frame_counter_ % kFramesPer10Seconds != 0)
    return;
  const bool mostly_multichannel_last_10_seconds =
      (persistent_multichannel_frame_counter_ >= kFramesPer10Seconds / 2);
  RTC_HISTOGRAM_BOOLEAN(
      "WebRTC.Audio.EchoCanceller.ProcessingPersistentMultichannelContent",
      mostly_multichannel_last_10_seconds ? 1 : 0);

  persistent_multichannel_frame_counter_ = 0;
}

MultiChannelContentDetector::MultiChannelContentDetector(
    bool detect_stereo_content,
    int num_render_input_channels,
    float detection_threshold,
    int stereo_detection_timeout_threshold_seconds,
    float stereo_detection_hysteresis_seconds)
    : detect_stereo_content_(detect_stereo_content),
      detection_threshold_(detection_threshold),
      detection_timeout_threshold_frames_(
          stereo_detection_timeout_threshold_seconds > 0
              ? absl::make_optional(stereo_detection_timeout_threshold_seconds *
                                    kNumFramesPerSecond)
              : absl::nullopt),
      stereo_detection_hysteresis_frames_(static_cast<int>(
          stereo_detection_hysteresis_seconds * kNumFramesPerSecond)),
      metrics_logger_((detect_stereo_content && num_render_input_channels > 1)
                          ? std::make_unique<MetricsLogger>()
                          : nullptr),
      persistent_multichannel_content_detected_(
          !detect_stereo_content && num_render_input_channels > 1) {}

bool MultiChannelContentDetector::UpdateDetection(
    const std::vector<std::vector<std::vector<float>>>& frame) {
  if (!detect_stereo_content_) {
    RTC_DCHECK_EQ(frame[0].size() > 1,
                  persistent_multichannel_content_detected_);
    return false;
  }

  const bool previous_persistent_multichannel_content_detected =
      persistent_multichannel_content_detected_;
  const bool stereo_detected_in_frame =
      HasStereoContent(frame, detection_threshold_);

  consecutive_frames_with_stereo_ =
      stereo_detected_in_frame ? consecutive_frames_with_stereo_ + 1 : 0;
  frames_since_stereo_detected_last_ =
      stereo_detected_in_frame ? 0 : frames_since_stereo_detected_last_ + 1;

  // Detect persistent multichannel content.
  if (consecutive_frames_with_stereo_ > stereo_detection_hysteresis_frames_) {
    persistent_multichannel_content_detected_ = true;
  }
  if (detection_timeout_threshold_frames_.has_value() &&
      frames_since_stereo_detected_last_ >=
          *detection_timeout_threshold_frames_) {
    persistent_multichannel_content_detected_ = false;
  }

  // Detect temporary multichannel content.
  temporary_multichannel_content_detected_ =
      persistent_multichannel_content_detected_ ? false
                                                : stereo_detected_in_frame;

  if (metrics_logger_)
    metrics_logger_->Update(persistent_multichannel_content_detected_);

  return previous_persistent_multichannel_content_detected !=
         persistent_multichannel_content_detected_;
}

}  // namespace webrtc
