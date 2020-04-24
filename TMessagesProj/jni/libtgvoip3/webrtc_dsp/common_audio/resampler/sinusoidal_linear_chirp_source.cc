/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// MSVC++ requires this to be set before any other includes to get M_PI.
#define _USE_MATH_DEFINES

#include "common_audio/resampler/sinusoidal_linear_chirp_source.h"

#include <math.h>

namespace webrtc {

SinusoidalLinearChirpSource::SinusoidalLinearChirpSource(int sample_rate,
                                                         size_t samples,
                                                         double max_frequency,
                                                         double delay_samples)
    : sample_rate_(sample_rate),
      total_samples_(samples),
      max_frequency_(max_frequency),
      current_index_(0),
      delay_samples_(delay_samples) {
  // Chirp rate.
  double duration = static_cast<double>(total_samples_) / sample_rate_;
  k_ = (max_frequency_ - kMinFrequency) / duration;
}

void SinusoidalLinearChirpSource::Run(size_t frames, float* destination) {
  for (size_t i = 0; i < frames; ++i, ++current_index_) {
    // Filter out frequencies higher than Nyquist.
    if (Frequency(current_index_) > 0.5 * sample_rate_) {
      destination[i] = 0;
    } else {
      // Calculate time in seconds.
      if (current_index_ < delay_samples_) {
        destination[i] = 0;
      } else {
        // Sinusoidal linear chirp.
        double t = (current_index_ - delay_samples_) / sample_rate_;
        destination[i] = sin(2 * M_PI * (kMinFrequency * t + (k_ / 2) * t * t));
      }
    }
  }
}

double SinusoidalLinearChirpSource::Frequency(size_t position) {
  return kMinFrequency + (position - delay_samples_) *
                             (max_frequency_ - kMinFrequency) / total_samples_;
}

}  // namespace webrtc
