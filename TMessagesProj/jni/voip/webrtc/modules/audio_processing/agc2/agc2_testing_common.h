/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AGC2_AGC2_TESTING_COMMON_H_
#define MODULES_AUDIO_PROCESSING_AGC2_AGC2_TESTING_COMMON_H_

#include <math.h>

#include <limits>
#include <vector>

#include "rtc_base/checks.h"

namespace webrtc {

namespace test {

// Level Estimator test parameters.
constexpr float kDecayMs = 500.f;

// Limiter parameters.
constexpr float kLimiterMaxInputLevelDbFs = 1.f;
constexpr float kLimiterKneeSmoothnessDb = 1.f;
constexpr float kLimiterCompressionRatio = 5.f;
constexpr float kPi = 3.1415926536f;

std::vector<double> LinSpace(const double l, const double r, size_t num_points);

class SineGenerator {
 public:
  SineGenerator(float frequency, int rate)
      : frequency_(frequency), rate_(rate) {}
  float operator()() {
    x_radians_ += frequency_ / rate_ * 2 * kPi;
    if (x_radians_ > 2 * kPi) {
      x_radians_ -= 2 * kPi;
    }
    return 1000.f * sinf(x_radians_);
  }

 private:
  float frequency_;
  int rate_;
  float x_radians_ = 0.f;
};

class PulseGenerator {
 public:
  PulseGenerator(float frequency, int rate)
      : samples_period_(
            static_cast<int>(static_cast<float>(rate) / frequency)) {
    RTC_DCHECK_GT(rate, frequency);
  }
  float operator()() {
    sample_counter_++;
    if (sample_counter_ >= samples_period_) {
      sample_counter_ -= samples_period_;
    }
    return static_cast<float>(
        sample_counter_ == 0 ? std::numeric_limits<int16_t>::max() : 10.f);
  }

 private:
  int samples_period_;
  int sample_counter_ = 0;
};

}  // namespace test
}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AGC2_AGC2_TESTING_COMMON_H_
