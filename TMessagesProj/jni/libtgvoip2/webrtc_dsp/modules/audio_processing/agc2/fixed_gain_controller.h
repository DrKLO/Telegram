/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef MODULES_AUDIO_PROCESSING_AGC2_FIXED_GAIN_CONTROLLER_H_
#define MODULES_AUDIO_PROCESSING_AGC2_FIXED_GAIN_CONTROLLER_H_
#include "modules/audio_processing/agc2/limiter.h"
#include "modules/audio_processing/include/audio_frame_view.h"
namespace webrtc {
class ApmDataDumper;
class FixedGainController {
 public:
  explicit FixedGainController(ApmDataDumper* apm_data_dumper);
  FixedGainController(ApmDataDumper* apm_data_dumper,
                      std::string histogram_name_prefix);
  void Process(AudioFrameView<float> signal);
  // Gain and sample rate may be changed at any time (but not
  // concurrently with any other method call).
  void SetGain(float gain_to_apply_db);
  void SetSampleRate(size_t sample_rate_hz);
  float LastAudioLevel() const;
 private:
  float gain_to_apply_ = 1.f;
  ApmDataDumper* apm_data_dumper_ = nullptr;
  Limiter limiter_;
};
}  // namespace webrtc
#endif  // MODULES_AUDIO_PROCESSING_AGC2_FIXED_GAIN_CONTROLLER_H_