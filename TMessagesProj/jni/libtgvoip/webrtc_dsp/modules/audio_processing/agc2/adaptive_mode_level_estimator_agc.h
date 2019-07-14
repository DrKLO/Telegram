/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AGC2_ADAPTIVE_MODE_LEVEL_ESTIMATOR_AGC_H_
#define MODULES_AUDIO_PROCESSING_AGC2_ADAPTIVE_MODE_LEVEL_ESTIMATOR_AGC_H_

#include <stddef.h>
#include <stdint.h>

#include "modules/audio_processing/agc/agc.h"
#include "modules/audio_processing/agc2/adaptive_mode_level_estimator.h"
#include "modules/audio_processing/agc2/saturation_protector.h"
#include "modules/audio_processing/agc2/vad_with_level.h"

namespace webrtc {
class AdaptiveModeLevelEstimatorAgc : public Agc {
 public:
  explicit AdaptiveModeLevelEstimatorAgc(ApmDataDumper* apm_data_dumper);

  // |audio| must be mono; in a multi-channel stream, provide the first (usually
  // left) channel.
  void Process(const int16_t* audio,
               size_t length,
               int sample_rate_hz) override;

  // Retrieves the difference between the target RMS level and the current
  // signal RMS level in dB. Returns true if an update is available and false
  // otherwise, in which case |error| should be ignored and no action taken.
  bool GetRmsErrorDb(int* error) override;
  void Reset() override;

  float voice_probability() const override;

 private:
  static constexpr int kTimeUntilConfidentMs = 700;
  static constexpr int kDefaultAgc2LevelHeadroomDbfs = -1;
  int32_t time_in_ms_since_last_estimate_ = 0;
  AdaptiveModeLevelEstimator level_estimator_;
  VadWithLevel agc2_vad_;
  float latest_voice_probability_ = 0.f;
};
}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AGC2_ADAPTIVE_MODE_LEVEL_ESTIMATOR_AGC_H_
