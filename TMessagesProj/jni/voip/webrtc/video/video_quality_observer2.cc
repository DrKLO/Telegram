/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/video_quality_observer2.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <string>

#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"
#include "system_wrappers/include/metrics.h"
#include "video/video_receive_stream2.h"

namespace webrtc {
namespace internal {
const uint32_t VideoQualityObserver::kMinFrameSamplesToDetectFreeze = 5;
const uint32_t VideoQualityObserver::kMinIncreaseForFreezeMs = 150;
const uint32_t VideoQualityObserver::kAvgInterframeDelaysWindowSizeFrames = 30;

namespace {
constexpr int kMinVideoDurationMs = 3000;
constexpr int kMinRequiredSamples = 1;
constexpr int kPixelsInHighResolution =
    960 * 540;  // CPU-adapted HD still counts.
constexpr int kPixelsInMediumResolution = 640 * 360;
constexpr int kBlockyQpThresholdVp8 = 70;
constexpr int kBlockyQpThresholdVp9 = 180;
constexpr int kMaxNumCachedBlockyFrames = 100;
// TODO(ilnik): Add H264/HEVC thresholds.
}  // namespace

VideoQualityObserver::VideoQualityObserver()
    : last_frame_rendered_ms_(-1),
      num_frames_rendered_(0),
      first_frame_rendered_ms_(-1),
      last_frame_pixels_(0),
      is_last_frame_blocky_(false),
      last_unfreeze_time_ms_(0),
      render_interframe_delays_(kAvgInterframeDelaysWindowSizeFrames),
      sum_squared_interframe_delays_secs_(0.0),
      time_in_resolution_ms_(3, 0),
      current_resolution_(Resolution::Low),
      num_resolution_downgrades_(0),
      time_in_blocky_video_ms_(0),
      is_paused_(false) {}

void VideoQualityObserver::UpdateHistograms(bool screenshare) {
  // TODO(bugs.webrtc.org/11489): Called on the decoder thread - which _might_
  // be the same as the construction thread.

  // Don't report anything on an empty video stream.
  if (num_frames_rendered_ == 0) {
    return;
  }

  char log_stream_buf[2 * 1024];
  rtc::SimpleStringBuilder log_stream(log_stream_buf);

  if (last_frame_rendered_ms_ > last_unfreeze_time_ms_) {
    smooth_playback_durations_.Add(last_frame_rendered_ms_ -
                                   last_unfreeze_time_ms_);
  }

  std::string uma_prefix =
      screenshare ? "WebRTC.Video.Screenshare" : "WebRTC.Video";

  auto mean_time_between_freezes =
      smooth_playback_durations_.Avg(kMinRequiredSamples);
  if (mean_time_between_freezes) {
    RTC_HISTOGRAM_COUNTS_SPARSE_100000(uma_prefix + ".MeanTimeBetweenFreezesMs",
                                       *mean_time_between_freezes);
    log_stream << uma_prefix << ".MeanTimeBetweenFreezesMs "
               << *mean_time_between_freezes << "\n";
  }
  auto avg_freeze_length = freezes_durations_.Avg(kMinRequiredSamples);
  if (avg_freeze_length) {
    RTC_HISTOGRAM_COUNTS_SPARSE_100000(uma_prefix + ".MeanFreezeDurationMs",
                                       *avg_freeze_length);
    log_stream << uma_prefix << ".MeanFreezeDurationMs " << *avg_freeze_length
               << "\n";
  }

  int64_t video_duration_ms =
      last_frame_rendered_ms_ - first_frame_rendered_ms_;

  if (video_duration_ms >= kMinVideoDurationMs) {
    int time_spent_in_hd_percentage = static_cast<int>(
        time_in_resolution_ms_[Resolution::High] * 100 / video_duration_ms);
    RTC_HISTOGRAM_COUNTS_SPARSE_100(uma_prefix + ".TimeInHdPercentage",
                                    time_spent_in_hd_percentage);
    log_stream << uma_prefix << ".TimeInHdPercentage "
               << time_spent_in_hd_percentage << "\n";

    int time_with_blocky_video_percentage =
        static_cast<int>(time_in_blocky_video_ms_ * 100 / video_duration_ms);
    RTC_HISTOGRAM_COUNTS_SPARSE_100(uma_prefix + ".TimeInBlockyVideoPercentage",
                                    time_with_blocky_video_percentage);
    log_stream << uma_prefix << ".TimeInBlockyVideoPercentage "
               << time_with_blocky_video_percentage << "\n";

    int num_resolution_downgrades_per_minute =
        num_resolution_downgrades_ * 60000 / video_duration_ms;
    if (!screenshare) {
      RTC_HISTOGRAM_COUNTS_SPARSE_100(
          uma_prefix + ".NumberResolutionDownswitchesPerMinute",
          num_resolution_downgrades_per_minute);
      log_stream << uma_prefix << ".NumberResolutionDownswitchesPerMinute "
                 << num_resolution_downgrades_per_minute << "\n";
    }

    int num_freezes_per_minute =
        freezes_durations_.NumSamples() * 60000 / video_duration_ms;
    RTC_HISTOGRAM_COUNTS_SPARSE_100(uma_prefix + ".NumberFreezesPerMinute",
                                    num_freezes_per_minute);
    log_stream << uma_prefix << ".NumberFreezesPerMinute "
               << num_freezes_per_minute << "\n";

    if (sum_squared_interframe_delays_secs_ > 0.0) {
      int harmonic_framerate_fps = std::round(
          video_duration_ms / (1000 * sum_squared_interframe_delays_secs_));
      RTC_HISTOGRAM_COUNTS_SPARSE_100(uma_prefix + ".HarmonicFrameRate",
                                      harmonic_framerate_fps);
      log_stream << uma_prefix << ".HarmonicFrameRate "
                 << harmonic_framerate_fps << "\n";
    }
  }
  RTC_LOG(LS_INFO) << log_stream.str();
}

void VideoQualityObserver::OnRenderedFrame(
    const VideoFrameMetaData& frame_meta) {
  RTC_DCHECK_LE(last_frame_rendered_ms_, frame_meta.decode_timestamp.ms());
  RTC_DCHECK_LE(last_unfreeze_time_ms_, frame_meta.decode_timestamp.ms());

  if (num_frames_rendered_ == 0) {
    first_frame_rendered_ms_ = last_unfreeze_time_ms_ =
        frame_meta.decode_timestamp.ms();
  }

  auto blocky_frame_it = blocky_frames_.find(frame_meta.rtp_timestamp);

  if (num_frames_rendered_ > 0) {
    // Process inter-frame delay.
    const int64_t interframe_delay_ms =
        frame_meta.decode_timestamp.ms() - last_frame_rendered_ms_;
    const double interframe_delays_secs = interframe_delay_ms / 1000.0;

    // Sum of squared inter frame intervals is used to calculate the harmonic
    // frame rate metric. The metric aims to reflect overall experience related
    // to smoothness of video playback and includes both freezes and pauses.
    sum_squared_interframe_delays_secs_ +=
        interframe_delays_secs * interframe_delays_secs;

    if (!is_paused_) {
      render_interframe_delays_.AddSample(interframe_delay_ms);

      bool was_freeze = false;
      if (render_interframe_delays_.Size() >= kMinFrameSamplesToDetectFreeze) {
        const absl::optional<int64_t> avg_interframe_delay =
            render_interframe_delays_.GetAverageRoundedDown();
        RTC_DCHECK(avg_interframe_delay);
        was_freeze = interframe_delay_ms >=
                     std::max(3 * *avg_interframe_delay,
                              *avg_interframe_delay + kMinIncreaseForFreezeMs);
      }

      if (was_freeze) {
        freezes_durations_.Add(interframe_delay_ms);
        smooth_playback_durations_.Add(last_frame_rendered_ms_ -
                                       last_unfreeze_time_ms_);
        last_unfreeze_time_ms_ = frame_meta.decode_timestamp.ms();
      } else {
        // Count spatial metrics if there were no freeze.
        time_in_resolution_ms_[current_resolution_] += interframe_delay_ms;

        if (is_last_frame_blocky_) {
          time_in_blocky_video_ms_ += interframe_delay_ms;
        }
      }
    }
  }

  if (is_paused_) {
    // If the stream was paused since the previous frame, do not count the
    // pause toward smooth playback. Explicitly count the part before it and
    // start the new smooth playback interval from this frame.
    is_paused_ = false;
    if (last_frame_rendered_ms_ > last_unfreeze_time_ms_) {
      smooth_playback_durations_.Add(last_frame_rendered_ms_ -
                                     last_unfreeze_time_ms_);
    }
    last_unfreeze_time_ms_ = frame_meta.decode_timestamp.ms();

    if (num_frames_rendered_ > 0) {
      pauses_durations_.Add(frame_meta.decode_timestamp.ms() -
                            last_frame_rendered_ms_);
    }
  }

  int64_t pixels = frame_meta.width * frame_meta.height;
  if (pixels >= kPixelsInHighResolution) {
    current_resolution_ = Resolution::High;
  } else if (pixels >= kPixelsInMediumResolution) {
    current_resolution_ = Resolution::Medium;
  } else {
    current_resolution_ = Resolution::Low;
  }

  if (pixels < last_frame_pixels_) {
    ++num_resolution_downgrades_;
  }

  last_frame_pixels_ = pixels;
  last_frame_rendered_ms_ = frame_meta.decode_timestamp.ms();

  is_last_frame_blocky_ = blocky_frame_it != blocky_frames_.end();
  if (is_last_frame_blocky_) {
    blocky_frames_.erase(blocky_frames_.begin(), ++blocky_frame_it);
  }

  ++num_frames_rendered_;
}

void VideoQualityObserver::OnDecodedFrame(uint32_t rtp_frame_timestamp,
                                          absl::optional<uint8_t> qp,
                                          VideoCodecType codec) {
  if (!qp)
    return;

  absl::optional<int> qp_blocky_threshold;
  // TODO(ilnik): add other codec types when we have QP for them.
  switch (codec) {
    case kVideoCodecVP8:
      qp_blocky_threshold = kBlockyQpThresholdVp8;
      break;
    case kVideoCodecVP9:
      qp_blocky_threshold = kBlockyQpThresholdVp9;
      break;
    default:
      qp_blocky_threshold = absl::nullopt;
  }

  RTC_DCHECK(blocky_frames_.find(rtp_frame_timestamp) == blocky_frames_.end());

  if (qp_blocky_threshold && *qp > *qp_blocky_threshold) {
    // Cache blocky frame. Its duration will be calculated in render callback.
    if (blocky_frames_.size() > kMaxNumCachedBlockyFrames) {
      RTC_LOG(LS_WARNING) << "Overflow of blocky frames cache.";
      blocky_frames_.erase(
          blocky_frames_.begin(),
          std::next(blocky_frames_.begin(), kMaxNumCachedBlockyFrames / 2));
    }

    blocky_frames_.insert(rtp_frame_timestamp);
  }
}

void VideoQualityObserver::OnStreamInactive() {
  is_paused_ = true;
}

uint32_t VideoQualityObserver::NumFreezes() const {
  return freezes_durations_.NumSamples();
}

uint32_t VideoQualityObserver::NumPauses() const {
  return pauses_durations_.NumSamples();
}

uint32_t VideoQualityObserver::TotalFreezesDurationMs() const {
  return freezes_durations_.Sum(kMinRequiredSamples).value_or(0);
}

uint32_t VideoQualityObserver::TotalPausesDurationMs() const {
  return pauses_durations_.Sum(kMinRequiredSamples).value_or(0);
}

uint32_t VideoQualityObserver::TotalFramesDurationMs() const {
  return last_frame_rendered_ms_ - first_frame_rendered_ms_;
}

double VideoQualityObserver::SumSquaredFrameDurationsSec() const {
  return sum_squared_interframe_delays_secs_;
}

}  // namespace internal
}  // namespace webrtc
