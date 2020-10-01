/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/adaptation/quality_rampup_experiment_helper.h"

#include <memory>
#include <utility>

#include "rtc_base/logging.h"

namespace webrtc {

QualityRampUpExperimentHelper::QualityRampUpExperimentHelper(
    QualityRampUpExperimentListener* experiment_listener,
    Clock* clock,
    QualityRampupExperiment experiment)
    : experiment_listener_(experiment_listener),
      clock_(clock),
      quality_rampup_experiment_(std::move(experiment)),
      cpu_adapted_(false),
      qp_resolution_adaptations_(0) {
  RTC_DCHECK(experiment_listener_);
  RTC_DCHECK(clock_);
}

std::unique_ptr<QualityRampUpExperimentHelper>
QualityRampUpExperimentHelper::CreateIfEnabled(
    QualityRampUpExperimentListener* experiment_listener,
    Clock* clock) {
  QualityRampupExperiment experiment = QualityRampupExperiment::ParseSettings();
  if (experiment.Enabled()) {
    return std::unique_ptr<QualityRampUpExperimentHelper>(
        new QualityRampUpExperimentHelper(experiment_listener, clock,
                                          experiment));
  }
  return nullptr;
}

void QualityRampUpExperimentHelper::PerformQualityRampupExperiment(
    rtc::scoped_refptr<QualityScalerResource> quality_scaler_resource,
    DataRate bandwidth,
    DataRate encoder_target_bitrate,
    DataRate max_bitrate,
    int pixels) {
  if (!quality_scaler_resource->is_started())
    return;

  int64_t now_ms = clock_->TimeInMilliseconds();
  quality_rampup_experiment_.SetMaxBitrate(pixels, max_bitrate.kbps());

  bool try_quality_rampup = false;
  if (quality_rampup_experiment_.BwHigh(now_ms, bandwidth.kbps())) {
    // Verify that encoder is at max bitrate and the QP is low.
    if (encoder_target_bitrate == max_bitrate &&
        quality_scaler_resource->QpFastFilterLow()) {
      try_quality_rampup = true;
    }
  }
  if (try_quality_rampup && qp_resolution_adaptations_ > 0 && !cpu_adapted_) {
    experiment_listener_->OnQualityRampUp();
  }
}

void QualityRampUpExperimentHelper::cpu_adapted(bool cpu_adapted) {
  cpu_adapted_ = cpu_adapted;
}

void QualityRampUpExperimentHelper::qp_resolution_adaptations(
    int qp_resolution_adaptations) {
  qp_resolution_adaptations_ = qp_resolution_adaptations;
}

}  // namespace webrtc
