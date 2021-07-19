/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/ns/fast_math.h"

#include <math.h>
#include <stdint.h>

#include "rtc_base/checks.h"

namespace webrtc {

namespace {

float FastLog2f(float in) {
  RTC_DCHECK_GT(in, .0f);
  // Read and interpret float as uint32_t and then cast to float.
  // This is done to extract the exponent (bits 30 - 23).
  // "Right shift" of the exponent is then performed by multiplying
  // with the constant (1/2^23). Finally, we subtract a constant to
  // remove the bias (https://en.wikipedia.org/wiki/Exponent_bias).
  union {
    float dummy;
    uint32_t a;
  } x = {in};
  float out = x.a;
  out *= 1.1920929e-7f;  // 1/2^23
  out -= 126.942695f;    // Remove bias.
  return out;
}

}  // namespace

float SqrtFastApproximation(float f) {
  // TODO(peah): Add fast approximate implementation.
  return sqrtf(f);
}

float Pow2Approximation(float p) {
  // TODO(peah): Add fast approximate implementation.
  return powf(2.f, p);
}

float PowApproximation(float x, float p) {
  return Pow2Approximation(p * FastLog2f(x));
}

float LogApproximation(float x) {
  constexpr float kLogOf2 = 0.69314718056f;
  return FastLog2f(x) * kLogOf2;
}

void LogApproximation(rtc::ArrayView<const float> x, rtc::ArrayView<float> y) {
  for (size_t k = 0; k < x.size(); ++k) {
    y[k] = LogApproximation(x[k]);
  }
}

float ExpApproximation(float x) {
  constexpr float kLog10Ofe = 0.4342944819f;
  return PowApproximation(10.f, x * kLog10Ofe);
}

void ExpApproximation(rtc::ArrayView<const float> x, rtc::ArrayView<float> y) {
  for (size_t k = 0; k < x.size(); ++k) {
    y[k] = ExpApproximation(x[k]);
  }
}

void ExpApproximationSignFlip(rtc::ArrayView<const float> x,
                              rtc::ArrayView<float> y) {
  for (size_t k = 0; k < x.size(); ++k) {
    y[k] = ExpApproximation(-x[k]);
  }
}

}  // namespace webrtc
