/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#define _USE_MATH_DEFINES

#include "common_audio/window_generator.h"

#include <cmath>
#include <complex>

#include "rtc_base/checks.h"

using std::complex;

namespace {

// Modified Bessel function of order 0 for complex inputs.
complex<float> I0(complex<float> x) {
  complex<float> y = x / 3.75f;
  y *= y;
  return 1.0f + y * (3.5156229f +
                     y * (3.0899424f +
                          y * (1.2067492f +
                               y * (0.2659732f +
                                    y * (0.360768e-1f + y * 0.45813e-2f)))));
}

}  // namespace

namespace webrtc {

void WindowGenerator::Hanning(int length, float* window) {
  RTC_CHECK_GT(length, 1);
  RTC_CHECK(window != nullptr);
  for (int i = 0; i < length; ++i) {
    window[i] =
        0.5f * (1 - cosf(2 * static_cast<float>(M_PI) * i / (length - 1)));
  }
}

void WindowGenerator::KaiserBesselDerived(float alpha,
                                          size_t length,
                                          float* window) {
  RTC_CHECK_GT(length, 1U);
  RTC_CHECK(window != nullptr);

  const size_t half = (length + 1) / 2;
  float sum = 0.0f;

  for (size_t i = 0; i <= half; ++i) {
    complex<float> r = (4.0f * i) / length - 1.0f;
    sum += I0(static_cast<float>(M_PI) * alpha * sqrt(1.0f - r * r)).real();
    window[i] = sum;
  }
  for (size_t i = length - 1; i >= half; --i) {
    window[length - i - 1] = sqrtf(window[length - i - 1] / sum);
    window[i] = window[length - i - 1];
  }
  if (length % 2 == 1) {
    window[half - 1] = sqrtf(window[half - 1] / sum);
  }
}

}  // namespace webrtc
