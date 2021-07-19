/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef VIDEO_ADAPTATION_QUALITY_RAMPUP_EXPERIMENT_HELPER_H_
#define VIDEO_ADAPTATION_QUALITY_RAMPUP_EXPERIMENT_HELPER_H_

#include <memory>

#include "api/scoped_refptr.h"
#include "api/units/data_rate.h"
#include "rtc_base/experiments/quality_rampup_experiment.h"
#include "system_wrappers/include/clock.h"
#include "video/adaptation/quality_scaler_resource.h"

namespace webrtc {

class QualityRampUpExperimentListener {
 public:
  virtual ~QualityRampUpExperimentListener() = default;
  virtual void OnQualityRampUp() = 0;
};

// Helper class for orchestrating the WebRTC-Video-QualityRampupSettings
// experiment.
class QualityRampUpExperimentHelper {
 public:
  // Returns a QualityRampUpExperimentHelper if the experiment is enabled,
  // an nullptr otherwise.
  static std::unique_ptr<QualityRampUpExperimentHelper> CreateIfEnabled(
      QualityRampUpExperimentListener* experiment_listener,
      Clock* clock);

  QualityRampUpExperimentHelper(const QualityRampUpExperimentHelper&) = delete;
  QualityRampUpExperimentHelper& operator=(
      const QualityRampUpExperimentHelper&) = delete;

  void cpu_adapted(bool cpu_adapted);
  void qp_resolution_adaptations(int qp_adaptations);

  void PerformQualityRampupExperiment(
      rtc::scoped_refptr<QualityScalerResource> quality_scaler_resource,
      DataRate bandwidth,
      DataRate encoder_target_bitrate,
      DataRate max_bitrate,
      int pixels);

 private:
  QualityRampUpExperimentHelper(
      QualityRampUpExperimentListener* experiment_listener,
      Clock* clock,
      QualityRampupExperiment experiment);
  QualityRampUpExperimentListener* const experiment_listener_;
  Clock* clock_;
  QualityRampupExperiment quality_rampup_experiment_;
  bool cpu_adapted_;
  int qp_resolution_adaptations_;
};

}  // namespace webrtc

#endif  // VIDEO_ADAPTATION_QUALITY_RAMPUP_EXPERIMENT_HELPER_H_
