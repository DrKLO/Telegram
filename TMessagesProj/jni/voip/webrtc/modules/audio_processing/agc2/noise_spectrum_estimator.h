/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AGC2_NOISE_SPECTRUM_ESTIMATOR_H_
#define MODULES_AUDIO_PROCESSING_AGC2_NOISE_SPECTRUM_ESTIMATOR_H_

#include "api/array_view.h"

namespace webrtc {

class ApmDataDumper;

class NoiseSpectrumEstimator {
 public:
  explicit NoiseSpectrumEstimator(ApmDataDumper* data_dumper);

  NoiseSpectrumEstimator() = delete;
  NoiseSpectrumEstimator(const NoiseSpectrumEstimator&) = delete;
  NoiseSpectrumEstimator& operator=(const NoiseSpectrumEstimator&) = delete;

  void Initialize();
  void Update(rtc::ArrayView<const float> spectrum, bool first_update);

  rtc::ArrayView<const float> GetNoiseSpectrum() const {
    return rtc::ArrayView<const float>(noise_spectrum_);
  }

 private:
  ApmDataDumper* data_dumper_;
  float noise_spectrum_[65];
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AGC2_NOISE_SPECTRUM_ESTIMATOR_H_
