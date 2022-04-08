/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/adaptation/bandwidth_quality_scaler_resource.h"

#include <utility>

#include "rtc_base/checks.h"
#include "rtc_base/experiments/balanced_degradation_settings.h"
#include "rtc_base/logging.h"
#include "rtc_base/ref_counted_object.h"
#include "rtc_base/task_utils/to_queued_task.h"
#include "rtc_base/time_utils.h"

namespace webrtc {

// static
rtc::scoped_refptr<BandwidthQualityScalerResource>
BandwidthQualityScalerResource::Create() {
  return rtc::make_ref_counted<BandwidthQualityScalerResource>();
}

BandwidthQualityScalerResource::BandwidthQualityScalerResource()
    : VideoStreamEncoderResource("BandwidthQualityScalerResource"),
      bandwidth_quality_scaler_(nullptr) {}

BandwidthQualityScalerResource::~BandwidthQualityScalerResource() {
  RTC_DCHECK(!bandwidth_quality_scaler_);
}

bool BandwidthQualityScalerResource::is_started() const {
  RTC_DCHECK_RUN_ON(encoder_queue());
  return bandwidth_quality_scaler_.get();
}

void BandwidthQualityScalerResource::StartCheckForOveruse(
    const std::vector<VideoEncoder::ResolutionBitrateLimits>&
        resolution_bitrate_limits) {
  RTC_DCHECK_RUN_ON(encoder_queue());
  RTC_DCHECK(!is_started());
  bandwidth_quality_scaler_ = std::make_unique<BandwidthQualityScaler>(this);

  // If the configuration parameters more than one, we should define and
  // declare the function BandwidthQualityScaler::Initialize() and call it.
  bandwidth_quality_scaler_->SetResolutionBitrateLimits(
      resolution_bitrate_limits);
}

void BandwidthQualityScalerResource::StopCheckForOveruse() {
  RTC_DCHECK_RUN_ON(encoder_queue());
  RTC_DCHECK(is_started());
  // Ensure we have no pending callbacks. This makes it safe to destroy the
  // BandwidthQualityScaler and even task queues with tasks in-flight.
  bandwidth_quality_scaler_.reset();
}

void BandwidthQualityScalerResource::OnReportUsageBandwidthHigh() {
  OnResourceUsageStateMeasured(ResourceUsageState::kOveruse);
}

void BandwidthQualityScalerResource::OnReportUsageBandwidthLow() {
  OnResourceUsageStateMeasured(ResourceUsageState::kUnderuse);
}

void BandwidthQualityScalerResource::OnEncodeCompleted(
    const EncodedImage& encoded_image,
    int64_t time_sent_in_us,
    int64_t encoded_image_size_bytes) {
  RTC_DCHECK_RUN_ON(encoder_queue());

  if (bandwidth_quality_scaler_) {
    bandwidth_quality_scaler_->ReportEncodeInfo(
        encoded_image_size_bytes, time_sent_in_us / 1000,
        encoded_image._encodedWidth, encoded_image._encodedHeight);
  }
}

}  // namespace webrtc
