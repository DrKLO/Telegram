/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC3_SUBTRACTOR_OUTPUT_H_
#define MODULES_AUDIO_PROCESSING_AEC3_SUBTRACTOR_OUTPUT_H_

#include <array>

#include "api/array_view.h"
#include "modules/audio_processing/aec3/aec3_common.h"
#include "modules/audio_processing/aec3/fft_data.h"

namespace webrtc {

// Stores the values being returned from the echo subtractor.
struct SubtractorOutput {
  SubtractorOutput();
  ~SubtractorOutput();

  std::array<float, kBlockSize> s_main;
  std::array<float, kBlockSize> s_shadow;
  std::array<float, kBlockSize> e_main;
  std::array<float, kBlockSize> e_shadow;
  FftData E_main;
  std::array<float, kFftLengthBy2Plus1> E2_main;
  std::array<float, kFftLengthBy2Plus1> E2_shadow;
  float s2_main = 0.f;
  float s2_shadow = 0.f;
  float e2_main = 0.f;
  float e2_shadow = 0.f;
  float y2 = 0.f;
  float s_main_max_abs = 0.f;
  float s_shadow_max_abs = 0.f;

  // Reset the struct content.
  void Reset();

  // Updates the powers of the signals.
  void ComputeMetrics(rtc::ArrayView<const float> y);
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AEC3_SUBTRACTOR_OUTPUT_H_
