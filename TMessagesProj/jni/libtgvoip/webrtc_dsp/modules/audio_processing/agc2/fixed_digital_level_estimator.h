/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AGC2_FIXED_DIGITAL_LEVEL_ESTIMATOR_H_
#define MODULES_AUDIO_PROCESSING_AGC2_FIXED_DIGITAL_LEVEL_ESTIMATOR_H_

#include <array>
#include <vector>

#include "modules/audio_processing/agc2/agc2_common.h"
#include "modules/audio_processing/include/audio_frame_view.h"
#include "rtc_base/constructormagic.h"

namespace webrtc {

class ApmDataDumper;
// Produces a smooth signal level estimate from an input audio
// stream. The estimate smoothing is done through exponential
// filtering.
class FixedDigitalLevelEstimator {
 public:
  // Sample rates are allowed if the number of samples in a frame
  // (sample_rate_hz * kFrameDurationMs / 1000) is divisible by
  // kSubFramesInSample. For kFrameDurationMs=10 and
  // kSubFramesInSample=20, this means that sample_rate_hz has to be
  // divisible by 2000.
  FixedDigitalLevelEstimator(size_t sample_rate_hz,
                             ApmDataDumper* apm_data_dumper);

  // The input is assumed to be in FloatS16 format. Scaled input will
  // produce similarly scaled output. A frame of with kFrameDurationMs
  // ms of audio produces a level estimates in the same scale. The
  // level estimate contains kSubFramesInFrame values.
  std::array<float, kSubFramesInFrame> ComputeLevel(
      const AudioFrameView<const float>& float_frame);

  // Rate may be changed at any time (but not concurrently) from the
  // value passed to the constructor. The class is not thread safe.
  void SetSampleRate(size_t sample_rate_hz);

  // Resets the level estimator internal state.
  void Reset();

  float LastAudioLevel() const { return filter_state_level_; }

 private:
  void CheckParameterCombination();

  ApmDataDumper* const apm_data_dumper_ = nullptr;
  float filter_state_level_;
  size_t samples_in_frame_;
  size_t samples_in_sub_frame_;

  RTC_DISALLOW_COPY_AND_ASSIGN(FixedDigitalLevelEstimator);
};
}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AGC2_FIXED_DIGITAL_LEVEL_ESTIMATOR_H_
