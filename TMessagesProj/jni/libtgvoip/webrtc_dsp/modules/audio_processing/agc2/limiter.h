/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AGC2_LIMITER_H_
#define MODULES_AUDIO_PROCESSING_AGC2_LIMITER_H_

#include <string>
#include <vector>

#include "modules/audio_processing/agc2/fixed_digital_level_estimator.h"
#include "modules/audio_processing/agc2/interpolated_gain_curve.h"
#include "modules/audio_processing/include/audio_frame_view.h"
#include "rtc_base/constructormagic.h"

namespace webrtc {
class ApmDataDumper;

class Limiter {
 public:
  Limiter(size_t sample_rate_hz,
          ApmDataDumper* apm_data_dumper,
          std::string histogram_name_prefix);
  Limiter(const Limiter& limiter) = delete;
  Limiter& operator=(const Limiter& limiter) = delete;
  ~Limiter();

  // Applies limiter and hard-clipping to |signal|.
  void Process(AudioFrameView<float> signal);
  InterpolatedGainCurve::Stats GetGainCurveStats() const;

  // Supported rates must be
  // * supported by FixedDigitalLevelEstimator
  // * below kMaximalNumberOfSamplesPerChannel*1000/kFrameDurationMs
  //   so that samples_per_channel fit in the
  //   per_sample_scaling_factors_ array.
  void SetSampleRate(size_t sample_rate_hz);

  // Resets the internal state.
  void Reset();

  float LastAudioLevel() const;

 private:
  const InterpolatedGainCurve interp_gain_curve_;
  FixedDigitalLevelEstimator level_estimator_;
  ApmDataDumper* const apm_data_dumper_ = nullptr;

  // Work array containing the sub-frame scaling factors to be interpolated.
  std::array<float, kSubFramesInFrame + 1> scaling_factors_ = {};
  std::array<float, kMaximalNumberOfSamplesPerChannel>
      per_sample_scaling_factors_ = {};
  float last_scaling_factor_ = 1.f;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AGC2_LIMITER_H_
