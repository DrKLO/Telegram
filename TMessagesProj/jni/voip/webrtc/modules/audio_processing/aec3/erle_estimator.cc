/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/erle_estimator.h"

#include "modules/audio_processing/aec3/aec3_common.h"
#include "rtc_base/checks.h"

namespace webrtc {

ErleEstimator::ErleEstimator(size_t startup_phase_length_blocks,
                             const EchoCanceller3Config& config,
                             size_t num_capture_channels)
    : startup_phase_length_blocks_(startup_phase_length_blocks),
      fullband_erle_estimator_(config.erle, num_capture_channels),
      subband_erle_estimator_(config, num_capture_channels) {
  if (config.erle.num_sections > 1) {
    signal_dependent_erle_estimator_ =
        std::make_unique<SignalDependentErleEstimator>(config,
                                                       num_capture_channels);
  }
  Reset(true);
}

ErleEstimator::~ErleEstimator() = default;

void ErleEstimator::Reset(bool delay_change) {
  fullband_erle_estimator_.Reset();
  subband_erle_estimator_.Reset();
  if (signal_dependent_erle_estimator_) {
    signal_dependent_erle_estimator_->Reset();
  }
  if (delay_change) {
    blocks_since_reset_ = 0;
  }
}

void ErleEstimator::Update(
    const RenderBuffer& render_buffer,
    rtc::ArrayView<const std::vector<std::array<float, kFftLengthBy2Plus1>>>
        filter_frequency_responses,
    rtc::ArrayView<const float, kFftLengthBy2Plus1>
        avg_render_spectrum_with_reverb,
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> capture_spectra,
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>>
        subtractor_spectra,
    const std::vector<bool>& converged_filters) {
  RTC_DCHECK_EQ(subband_erle_estimator_.Erle(/*onset_compensated=*/true).size(),
                capture_spectra.size());
  RTC_DCHECK_EQ(subband_erle_estimator_.Erle(/*onset_compensated=*/true).size(),
                subtractor_spectra.size());
  const auto& X2_reverb = avg_render_spectrum_with_reverb;
  const auto& Y2 = capture_spectra;
  const auto& E2 = subtractor_spectra;

  if (++blocks_since_reset_ < startup_phase_length_blocks_) {
    return;
  }

  subband_erle_estimator_.Update(X2_reverb, Y2, E2, converged_filters);

  if (signal_dependent_erle_estimator_) {
    signal_dependent_erle_estimator_->Update(
        render_buffer, filter_frequency_responses, X2_reverb, Y2, E2,
        subband_erle_estimator_.Erle(/*onset_compensated=*/false),
        subband_erle_estimator_.Erle(/*onset_compensated=*/true),
        converged_filters);
  }

  fullband_erle_estimator_.Update(X2_reverb, Y2, E2, converged_filters);
}

void ErleEstimator::Dump(
    const std::unique_ptr<ApmDataDumper>& data_dumper) const {
  fullband_erle_estimator_.Dump(data_dumper);
  subband_erle_estimator_.Dump(data_dumper);
  if (signal_dependent_erle_estimator_) {
    signal_dependent_erle_estimator_->Dump(data_dumper);
  }
}

}  // namespace webrtc
