/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC3_SUPPRESSION_FILTER_H_
#define MODULES_AUDIO_PROCESSING_AEC3_SUPPRESSION_FILTER_H_

#include <array>
#include <vector>

#include "modules/audio_processing/aec3/aec3_common.h"
#include "modules/audio_processing/aec3/aec3_fft.h"
#include "modules/audio_processing/aec3/fft_data.h"
#include "modules/audio_processing/utility/ooura_fft.h"
#include "rtc_base/constructormagic.h"

namespace webrtc {

class SuppressionFilter {
 public:
  SuppressionFilter(Aec3Optimization optimization, int sample_rate_hz);
  ~SuppressionFilter();
  void ApplyGain(const FftData& comfort_noise,
                 const FftData& comfort_noise_high_bands,
                 const std::array<float, kFftLengthBy2Plus1>& suppression_gain,
                 float high_bands_gain,
                 const FftData& E_lowest_band,
                 std::vector<std::vector<float>>* e);

 private:
  const Aec3Optimization optimization_;
  const int sample_rate_hz_;
  const OouraFft ooura_fft_;
  const Aec3Fft fft_;
  std::vector<std::array<float, kFftLengthBy2>> e_output_old_;
  RTC_DISALLOW_COPY_AND_ASSIGN(SuppressionFilter);
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AEC3_SUPPRESSION_FILTER_H_
