/*
 *  Copyright (c) 2010 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "media/base/video_adapter.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <limits>
#include <utility>

#include "absl/types/optional.h"
#include "media/base/video_common.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/time_utils.h"
#include "system_wrappers/include/field_trial.h"

namespace {

struct Fraction {
  int numerator;
  int denominator;

  void DivideByGcd() {
    int g = cricket::GreatestCommonDivisor(numerator, denominator);
    numerator /= g;
    denominator /= g;
  }

  // Determines number of output pixels if both width and height of an input of
  // `input_pixels` pixels is scaled with the fraction numerator / denominator.
  int scale_pixel_count(int input_pixels) {
    return (numerator * numerator * static_cast<int64_t>(input_pixels)) /
           (denominator * denominator);
  }
};

// Round `value_to_round` to a multiple of `multiple`. Prefer rounding upwards,
// but never more than `max_value`.
int roundUp(int value_to_round, int multiple, int max_value) {
  const int rounded_value =
      (value_to_round + multiple - 1) / multiple * multiple;
  return rounded_value <= max_value ? rounded_value
                                    : (max_value / multiple * multiple);
}

// Generates a scale factor that makes `input_pixels` close to `target_pixels`,
// but no higher than `max_pixels`.
Fraction FindScale(int input_width,
                   int input_height,
                   int target_pixels,
                   int max_pixels,
                   bool variable_start_scale_factor) {
  // This function only makes sense for a positive target.
  RTC_DCHECK_GT(target_pixels, 0);
  RTC_DCHECK_GT(max_pixels, 0);
  RTC_DCHECK_GE(max_pixels, target_pixels);

  const int input_pixels = input_width * input_height;

  // Don't scale up original.
  if (target_pixels >= input_pixels)
    return Fraction{1, 1};

  Fraction current_scale = Fraction{1, 1};
  Fraction best_scale = Fraction{1, 1};

  if (variable_start_scale_factor) {
    // Start scaling down by 2/3 depending on `input_width` and `input_height`.
    if (input_width % 3 == 0 && input_height % 3 == 0) {
      // 2/3 (then alternates 3/4, 2/3, 3/4,...).
      current_scale = Fraction{6, 6};
    }
    if (input_width % 9 == 0 && input_height % 9 == 0) {
      // 2/3, 2/3 (then alternates 3/4, 2/3, 3/4,...).
      current_scale = Fraction{36, 36};
    }
  }

  // The minimum (absolute) difference between the number of output pixels and
  // the target pixel count.
  int min_pixel_diff = std::numeric_limits<int>::max();
  if (input_pixels <= max_pixels) {
    // Start condition for 1/1 case, if it is less than max.
    min_pixel_diff = std::abs(input_pixels - target_pixels);
  }

  // Alternately scale down by 3/4 and 2/3. This results in fractions which are
  // effectively scalable. For instance, starting at 1280x720 will result in
  // the series (3/4) => 960x540, (1/2) => 640x360, (3/8) => 480x270,
  // (1/4) => 320x180, (3/16) => 240x125, (1/8) => 160x90.
  while (current_scale.scale_pixel_count(input_pixels) > target_pixels) {
    if (current_scale.numerator % 3 == 0 &&
        current_scale.denominator % 2 == 0) {
      // Multiply by 2/3.
      current_scale.numerator /= 3;
      current_scale.denominator /= 2;
    } else {
      // Multiply by 3/4.
      current_scale.numerator *= 3;
      current_scale.denominator *= 4;
    }

    int output_pixels = current_scale.scale_pixel_count(input_pixels);
    if (output_pixels <= max_pixels) {
      int diff = std::abs(target_pixels - output_pixels);
      if (diff < min_pixel_diff) {
        min_pixel_diff = diff;
        best_scale = current_scale;
      }
    }
  }
  best_scale.DivideByGcd();

  return best_scale;
}

absl::optional<std::pair<int, int>> Swap(
    const absl::optional<std::pair<int, int>>& in) {
  if (!in) {
    return absl::nullopt;
  }
  return std::make_pair(in->second, in->first);
}

}  // namespace

namespace cricket {

VideoAdapter::VideoAdapter(int source_resolution_alignment)
    : frames_in_(0),
      frames_out_(0),
      frames_scaled_(0),
      adaption_changes_(0),
      previous_width_(0),
      previous_height_(0),
      variable_start_scale_factor_(!webrtc::field_trial::IsDisabled(
          "WebRTC-Video-VariableStartScaleFactor")),
      source_resolution_alignment_(source_resolution_alignment),
      resolution_alignment_(source_resolution_alignment),
      resolution_request_target_pixel_count_(std::numeric_limits<int>::max()),
      resolution_request_max_pixel_count_(std::numeric_limits<int>::max()),
      max_framerate_request_(std::numeric_limits<int>::max()) {}

VideoAdapter::VideoAdapter() : VideoAdapter(1) {}

VideoAdapter::~VideoAdapter() {}

bool VideoAdapter::DropFrame(int64_t in_timestamp_ns) {
  int max_fps = max_framerate_request_;
  if (output_format_request_.max_fps)
    max_fps = std::min(max_fps, *output_format_request_.max_fps);

  framerate_controller_.SetMaxFramerate(max_fps);
  return framerate_controller_.ShouldDropFrame(in_timestamp_ns);
}

bool VideoAdapter::AdaptFrameResolution(int in_width,
                                        int in_height,
                                        int64_t in_timestamp_ns,
                                        int* cropped_width,
                                        int* cropped_height,
                                        int* out_width,
                                        int* out_height) {
  webrtc::MutexLock lock(&mutex_);
  ++frames_in_;

  // The max output pixel count is the minimum of the requests from
  // OnOutputFormatRequest and OnResolutionFramerateRequest.
  int max_pixel_count = resolution_request_max_pixel_count_;

  // Select target aspect ratio and max pixel count depending on input frame
  // orientation.
  absl::optional<std::pair<int, int>> target_aspect_ratio;
  if (in_width > in_height) {
    target_aspect_ratio = output_format_request_.target_landscape_aspect_ratio;
    if (output_format_request_.max_landscape_pixel_count)
      max_pixel_count = std::min(
          max_pixel_count, *output_format_request_.max_landscape_pixel_count);
  } else {
    target_aspect_ratio = output_format_request_.target_portrait_aspect_ratio;
    if (output_format_request_.max_portrait_pixel_count)
      max_pixel_count = std::min(
          max_pixel_count, *output_format_request_.max_portrait_pixel_count);
  }

  int target_pixel_count =
      std::min(resolution_request_target_pixel_count_, max_pixel_count);

  // Drop the input frame if necessary.
  if (max_pixel_count <= 0 || DropFrame(in_timestamp_ns)) {
    // Show VAdapt log every 90 frames dropped. (3 seconds)
    if ((frames_in_ - frames_out_) % 90 == 0) {
      // TODO(fbarchard): Reduce to LS_VERBOSE when adapter info is not needed
      // in default calls.
      RTC_LOG(LS_INFO) << "VAdapt Drop Frame: scaled " << frames_scaled_
                       << " / out " << frames_out_ << " / in " << frames_in_
                       << " Changes: " << adaption_changes_
                       << " Input: " << in_width << "x" << in_height
                       << " timestamp: " << in_timestamp_ns
                       << " Output fps: " << max_framerate_request_ << "/"
                       << output_format_request_.max_fps.value_or(-1)
                       << " alignment: " << resolution_alignment_;
    }

    // Drop frame.
    return false;
  }

  // Calculate how the input should be cropped.
  if (!target_aspect_ratio || target_aspect_ratio->first <= 0 ||
      target_aspect_ratio->second <= 0) {
    *cropped_width = in_width;
    *cropped_height = in_height;
  } else {
    const float requested_aspect =
        target_aspect_ratio->first /
        static_cast<float>(target_aspect_ratio->second);
    *cropped_width =
        std::min(in_width, static_cast<int>(in_height * requested_aspect));
    *cropped_height =
        std::min(in_height, static_cast<int>(in_width / requested_aspect));
  }
  const Fraction scale =
      FindScale(*cropped_width, *cropped_height, target_pixel_count,
                max_pixel_count, variable_start_scale_factor_);
  // Adjust cropping slightly to get correctly aligned output size and a perfect
  // scale factor.
  *cropped_width = roundUp(*cropped_width,
                           scale.denominator * resolution_alignment_, in_width);
  *cropped_height = roundUp(
      *cropped_height, scale.denominator * resolution_alignment_, in_height);
  RTC_DCHECK_EQ(0, *cropped_width % scale.denominator);
  RTC_DCHECK_EQ(0, *cropped_height % scale.denominator);

  // Calculate final output size.
  *out_width = *cropped_width / scale.denominator * scale.numerator;
  *out_height = *cropped_height / scale.denominator * scale.numerator;
  RTC_DCHECK_EQ(0, *out_width % resolution_alignment_);
  RTC_DCHECK_EQ(0, *out_height % resolution_alignment_);

  ++frames_out_;
  if (scale.numerator != scale.denominator)
    ++frames_scaled_;

  if (previous_width_ &&
      (previous_width_ != *out_width || previous_height_ != *out_height)) {
    ++adaption_changes_;
    RTC_LOG(LS_INFO) << "Frame size changed: scaled " << frames_scaled_
                     << " / out " << frames_out_ << " / in " << frames_in_
                     << " Changes: " << adaption_changes_
                     << " Input: " << in_width << "x" << in_height
                     << " Scale: " << scale.numerator << "/"
                     << scale.denominator << " Output: " << *out_width << "x"
                     << *out_height << " fps: " << max_framerate_request_ << "/"
                     << output_format_request_.max_fps.value_or(-1)
                     << " alignment: " << resolution_alignment_;
  }

  previous_width_ = *out_width;
  previous_height_ = *out_height;

  return true;
}

void VideoAdapter::OnOutputFormatRequest(
    const absl::optional<VideoFormat>& format) {
  absl::optional<std::pair<int, int>> target_aspect_ratio;
  absl::optional<int> max_pixel_count;
  absl::optional<int> max_fps;
  if (format) {
    target_aspect_ratio = std::make_pair(format->width, format->height);
    max_pixel_count = format->width * format->height;
    if (format->interval > 0)
      max_fps = rtc::kNumNanosecsPerSec / format->interval;
  }
  OnOutputFormatRequest(target_aspect_ratio, max_pixel_count, max_fps);
}

void VideoAdapter::OnOutputFormatRequest(
    const absl::optional<std::pair<int, int>>& target_aspect_ratio,
    const absl::optional<int>& max_pixel_count,
    const absl::optional<int>& max_fps) {
  absl::optional<std::pair<int, int>> target_landscape_aspect_ratio;
  absl::optional<std::pair<int, int>> target_portrait_aspect_ratio;
  if (target_aspect_ratio && target_aspect_ratio->first > 0 &&
      target_aspect_ratio->second > 0) {
    // Maintain input orientation.
    const int max_side =
        std::max(target_aspect_ratio->first, target_aspect_ratio->second);
    const int min_side =
        std::min(target_aspect_ratio->first, target_aspect_ratio->second);
    target_landscape_aspect_ratio = std::make_pair(max_side, min_side);
    target_portrait_aspect_ratio = std::make_pair(min_side, max_side);
  }
  OnOutputFormatRequest(target_landscape_aspect_ratio, max_pixel_count,
                        target_portrait_aspect_ratio, max_pixel_count, max_fps);
}

void VideoAdapter::OnOutputFormatRequest(
    const absl::optional<std::pair<int, int>>& target_landscape_aspect_ratio,
    const absl::optional<int>& max_landscape_pixel_count,
    const absl::optional<std::pair<int, int>>& target_portrait_aspect_ratio,
    const absl::optional<int>& max_portrait_pixel_count,
    const absl::optional<int>& max_fps) {
  webrtc::MutexLock lock(&mutex_);

  OutputFormatRequest request = {
      .target_landscape_aspect_ratio = target_landscape_aspect_ratio,
      .max_landscape_pixel_count = max_landscape_pixel_count,
      .target_portrait_aspect_ratio = target_portrait_aspect_ratio,
      .max_portrait_pixel_count = max_portrait_pixel_count,
      .max_fps = max_fps};

  if (stashed_output_format_request_) {
    // Save the output format request for later use in case the encoder making
    // this call would become active, because currently all active encoders use
    // requested_resolution instead.
    stashed_output_format_request_ = request;
    RTC_LOG(LS_INFO) << "Stashing OnOutputFormatRequest: "
                     << stashed_output_format_request_->ToString();
  } else {
    output_format_request_ = request;
    RTC_LOG(LS_INFO) << "Setting output_format_request_: "
                     << output_format_request_.ToString();
  }

  framerate_controller_.Reset();
}

void VideoAdapter::OnSinkWants(const rtc::VideoSinkWants& sink_wants) {
  webrtc::MutexLock lock(&mutex_);
  resolution_request_max_pixel_count_ = sink_wants.max_pixel_count;
  resolution_request_target_pixel_count_ =
      sink_wants.target_pixel_count.value_or(
          resolution_request_max_pixel_count_);
  max_framerate_request_ = sink_wants.max_framerate_fps;
  resolution_alignment_ = cricket::LeastCommonMultiple(
      source_resolution_alignment_, sink_wants.resolution_alignment);

  if (!sink_wants.aggregates) {
    RTC_LOG(LS_WARNING)
        << "These should always be created by VideoBroadcaster!";
    return;
  }

  // If requested_resolution is used, and there are no active encoders
  // that are NOT using requested_resolution (aka newapi), then override
  // calls to OnOutputFormatRequest and use values from requested_resolution
  // instead (combined with qualityscaling based on pixel counts above).
  if (webrtc::field_trial::IsDisabled(
          "WebRTC-Video-RequestedResolutionOverrideOutputFormatRequest")) {
    // kill-switch...
    return;
  }

  if (!sink_wants.requested_resolution) {
    if (stashed_output_format_request_) {
      // because current active_output_format_request is based on
      // requested_resolution logic, while current encoder(s) doesn't want that,
      // we have to restore the stashed request.
      RTC_LOG(LS_INFO) << "Unstashing OnOutputFormatRequest: "
                       << stashed_output_format_request_->ToString();
      output_format_request_ = *stashed_output_format_request_;
      stashed_output_format_request_.reset();
    }
    return;
  }

  if (sink_wants.aggregates->any_active_without_requested_resolution) {
    return;
  }

  if (!stashed_output_format_request_) {
    // The active output format request is about to be rewritten by
    // request_resolution. We need to save it for later use in case the encoder
    // which doesn't use request_resolution logic become active in the future.
    stashed_output_format_request_ = output_format_request_;
    RTC_LOG(LS_INFO) << "Stashing OnOutputFormatRequest: "
                     << stashed_output_format_request_->ToString();
  }

  auto res = *sink_wants.requested_resolution;
  auto pixel_count = res.width * res.height;
  output_format_request_.target_landscape_aspect_ratio =
      std::make_pair(res.width, res.height);
  output_format_request_.max_landscape_pixel_count = pixel_count;
  output_format_request_.target_portrait_aspect_ratio =
      std::make_pair(res.height, res.width);
  output_format_request_.max_portrait_pixel_count = pixel_count;
  output_format_request_.max_fps = max_framerate_request_;
  RTC_LOG(LS_INFO) << "Setting output_format_request_ based on sink_wants: "
                   << output_format_request_.ToString();
}

int VideoAdapter::GetTargetPixels() const {
  webrtc::MutexLock lock(&mutex_);
  return resolution_request_target_pixel_count_;
}

float VideoAdapter::GetMaxFramerate() const {
  webrtc::MutexLock lock(&mutex_);
  // Minimum of `output_format_request_.max_fps` and `max_framerate_request_` is
  // used to throttle frame-rate.
  int framerate =
      std::min(max_framerate_request_,
               output_format_request_.max_fps.value_or(max_framerate_request_));
  if (framerate == std::numeric_limits<int>::max()) {
    return std::numeric_limits<float>::infinity();
  } else {
    return max_framerate_request_;
  }
}

std::string VideoAdapter::OutputFormatRequest::ToString() const {
  rtc::StringBuilder oss;
  oss << "[ ";
  if (target_landscape_aspect_ratio == Swap(target_portrait_aspect_ratio) &&
      max_landscape_pixel_count == max_portrait_pixel_count) {
    if (target_landscape_aspect_ratio) {
      oss << target_landscape_aspect_ratio->first << "x"
          << target_landscape_aspect_ratio->second;
    } else {
      oss << "unset-resolution";
    }
    if (max_landscape_pixel_count) {
      oss << " max_pixel_count: " << *max_landscape_pixel_count;
    }
  } else {
    oss << "[ landscape: ";
    if (target_landscape_aspect_ratio) {
      oss << target_landscape_aspect_ratio->first << "x"
          << target_landscape_aspect_ratio->second;
    } else {
      oss << "unset";
    }
    if (max_landscape_pixel_count) {
      oss << " max_pixel_count: " << *max_landscape_pixel_count;
    }
    oss << " ] [ portrait: ";
    if (target_portrait_aspect_ratio) {
      oss << target_portrait_aspect_ratio->first << "x"
          << target_portrait_aspect_ratio->second;
    }
    if (max_portrait_pixel_count) {
      oss << " max_pixel_count: " << *max_portrait_pixel_count;
    }
    oss << " ]";
  }
  oss << " max_fps: ";
  if (max_fps) {
    oss << *max_fps;
  } else {
    oss << "unset";
  }
  oss << " ]";
  return oss.Release();
}

}  // namespace cricket
