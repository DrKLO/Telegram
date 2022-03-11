/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/utility/bandwidth_quality_scaler.h"

#include <algorithm>
#include <memory>
#include <utility>
#include <vector>

#include "api/video/video_adaptation_reason.h"
#include "api/video_codecs/video_encoder.h"
#include "rtc_base/checks.h"
#include "rtc_base/experiments/bandwidth_quality_scaler_settings.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/exp_filter.h"
#include "rtc_base/task_queue.h"
#include "rtc_base/task_utils/to_queued_task.h"
#include "rtc_base/time_utils.h"
#include "rtc_base/weak_ptr.h"

namespace webrtc {

namespace {

constexpr int kDefaultMaxWindowSizeMs = 5000;
constexpr float kHigherMaxBitrateTolerationFactor = 0.95;
constexpr float kLowerMinBitrateTolerationFactor = 0.8;
constexpr int kDefaultBitrateStateUpdateIntervalSeconds = 5;
}  // namespace

BandwidthQualityScaler::BandwidthQualityScaler(
    BandwidthQualityScalerUsageHandlerInterface* handler)
    : kBitrateStateUpdateInterval(TimeDelta::Seconds(
          BandwidthQualityScalerSettings::ParseFromFieldTrials()
              .BitrateStateUpdateInterval()
              .value_or(kDefaultBitrateStateUpdateIntervalSeconds))),
      handler_(handler),
      encoded_bitrate_(kDefaultMaxWindowSizeMs, RateStatistics::kBpsScale),
      weak_ptr_factory_(this) {
  RTC_DCHECK_RUN_ON(&task_checker_);
  RTC_DCHECK(handler_ != nullptr);

  StartCheckForBitrate();
}

BandwidthQualityScaler::~BandwidthQualityScaler() {
  RTC_DCHECK_RUN_ON(&task_checker_);
}

void BandwidthQualityScaler::StartCheckForBitrate() {
  RTC_DCHECK_RUN_ON(&task_checker_);
  TaskQueueBase::Current()->PostDelayedTask(
      ToQueuedTask([this_weak_ptr = weak_ptr_factory_.GetWeakPtr(), this] {
        if (!this_weak_ptr) {
          // The caller BandwidthQualityScaler has been deleted.
          return;
        }
        RTC_DCHECK_RUN_ON(&task_checker_);
        switch (CheckBitrate()) {
          case BandwidthQualityScaler::CheckBitrateResult::kHighBitRate: {
            handler_->OnReportUsageBandwidthHigh();
            last_frame_size_pixels_.reset();
            break;
          }
          case BandwidthQualityScaler::CheckBitrateResult::kLowBitRate: {
            handler_->OnReportUsageBandwidthLow();
            last_frame_size_pixels_.reset();
            break;
          }
          case BandwidthQualityScaler::CheckBitrateResult::kNormalBitrate: {
            break;
          }
          case BandwidthQualityScaler::CheckBitrateResult::
              kInsufficientSamples: {
            break;
          }
        }
        StartCheckForBitrate();
      }),
      kBitrateStateUpdateInterval.ms());
}

void BandwidthQualityScaler::ReportEncodeInfo(int frame_size_bytes,
                                              int64_t time_sent_in_ms,
                                              uint32_t encoded_width,
                                              uint32_t encoded_height) {
  RTC_DCHECK_RUN_ON(&task_checker_);
  last_time_sent_in_ms_ = time_sent_in_ms;
  last_frame_size_pixels_ = encoded_width * encoded_height;
  encoded_bitrate_.Update(frame_size_bytes, time_sent_in_ms);
}

void BandwidthQualityScaler::SetResolutionBitrateLimits(
    const std::vector<VideoEncoder::ResolutionBitrateLimits>&
        resolution_bitrate_limits) {
  if (resolution_bitrate_limits.empty()) {
    resolution_bitrate_limits_ = EncoderInfoSettings::
        GetDefaultSinglecastBitrateLimitsWhenQpIsUntrusted();
  } else {
    resolution_bitrate_limits_ = resolution_bitrate_limits;
  }
}

BandwidthQualityScaler::CheckBitrateResult
BandwidthQualityScaler::CheckBitrate() {
  RTC_DCHECK_RUN_ON(&task_checker_);
  if (!last_frame_size_pixels_.has_value() ||
      !last_time_sent_in_ms_.has_value()) {
    return BandwidthQualityScaler::CheckBitrateResult::kInsufficientSamples;
  }

  absl::optional<int64_t> current_bitrate_bps =
      encoded_bitrate_.Rate(last_time_sent_in_ms_.value());
  if (!current_bitrate_bps.has_value()) {
    // We can't get a valid bitrate due to not enough data points.
    return BandwidthQualityScaler::CheckBitrateResult::kInsufficientSamples;
  }
  absl::optional<VideoEncoder::ResolutionBitrateLimits> suitable_bitrate_limit =
      EncoderInfoSettings::
          GetSinglecastBitrateLimitForResolutionWhenQpIsUntrusted(
              last_frame_size_pixels_, resolution_bitrate_limits_);

  if (!suitable_bitrate_limit.has_value()) {
    return BandwidthQualityScaler::CheckBitrateResult::kInsufficientSamples;
  }

  // Multiply by toleration factor to solve the frequent adaptation due to
  // critical value.
  if (current_bitrate_bps > suitable_bitrate_limit->max_bitrate_bps *
                                kHigherMaxBitrateTolerationFactor) {
    return BandwidthQualityScaler::CheckBitrateResult::kLowBitRate;
  } else if (current_bitrate_bps <
             suitable_bitrate_limit->min_start_bitrate_bps *
                 kLowerMinBitrateTolerationFactor) {
    return BandwidthQualityScaler::CheckBitrateResult::kHighBitRate;
  }
  return BandwidthQualityScaler::CheckBitrateResult::kNormalBitrate;
}

BandwidthQualityScalerUsageHandlerInterface::
    ~BandwidthQualityScalerUsageHandlerInterface() {}
}  // namespace webrtc
