/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AGC2_ADAPTIVE_DIGITAL_GAIN_APPLIER_H_
#define MODULES_AUDIO_PROCESSING_AGC2_ADAPTIVE_DIGITAL_GAIN_APPLIER_H_

#include "modules/audio_processing/agc2/agc2_common.h"
#include "modules/audio_processing/agc2/gain_applier.h"
#include "modules/audio_processing/agc2/vad_with_level.h"
#include "modules/audio_processing/include/audio_frame_view.h"

namespace webrtc {

class ApmDataDumper;

struct SignalWithLevels {
  SignalWithLevels(AudioFrameView<float> float_frame);
  SignalWithLevels(const SignalWithLevels&);

  float input_level_dbfs = -1.f;
  float input_noise_level_dbfs = -1.f;
  VadWithLevel::LevelAndProbability vad_result;
  float limiter_audio_level_dbfs = -1.f;
  bool estimate_is_confident = false;
  AudioFrameView<float> float_frame;
};

class AdaptiveDigitalGainApplier {
 public:
  explicit AdaptiveDigitalGainApplier(ApmDataDumper* apm_data_dumper);
  // Decide what gain to apply.
  void Process(SignalWithLevels signal_with_levels);

 private:
  float last_gain_db_ = kInitialAdaptiveDigitalGainDb;
  GainApplier gain_applier_;
  int calls_since_last_gain_log_ = 0;

  // For some combinations of noise and speech probability, increasing
  // the level is not allowed. Since we may get VAD results in bursts,
  // we keep track of this variable until the next VAD results come
  // in.
  bool gain_increase_allowed_ = true;
  ApmDataDumper* apm_data_dumper_ = nullptr;
};
}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AGC2_ADAPTIVE_DIGITAL_GAIN_APPLIER_H_
