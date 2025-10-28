/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <immintrin.h>
#include <math.h>

#include "api/array_view.h"
#include "modules/audio_processing/aec3/vector_math.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace aec3 {

// Elementwise square root.
void VectorMath::SqrtAVX2(rtc::ArrayView<float> x) {
  const int x_size = static_cast<int>(x.size());
  const int vector_limit = x_size >> 3;

  int j = 0;
  for (; j < vector_limit * 8; j += 8) {
    __m256 g = _mm256_loadu_ps(&x[j]);
    g = _mm256_sqrt_ps(g);
    _mm256_storeu_ps(&x[j], g);
  }

  for (; j < x_size; ++j) {
    x[j] = sqrtf(x[j]);
  }
}

// Elementwise vector multiplication z = x * y.
void VectorMath::MultiplyAVX2(rtc::ArrayView<const float> x,
                              rtc::ArrayView<const float> y,
                              rtc::ArrayView<float> z) {
  RTC_DCHECK_EQ(z.size(), x.size());
  RTC_DCHECK_EQ(z.size(), y.size());
  const int x_size = static_cast<int>(x.size());
  const int vector_limit = x_size >> 3;

  int j = 0;
  for (; j < vector_limit * 8; j += 8) {
    const __m256 x_j = _mm256_loadu_ps(&x[j]);
    const __m256 y_j = _mm256_loadu_ps(&y[j]);
    const __m256 z_j = _mm256_mul_ps(x_j, y_j);
    _mm256_storeu_ps(&z[j], z_j);
  }

  for (; j < x_size; ++j) {
    z[j] = x[j] * y[j];
  }
}

// Elementwise vector accumulation z += x.
void VectorMath::AccumulateAVX2(rtc::ArrayView<const float> x,
                                rtc::ArrayView<float> z) {
  RTC_DCHECK_EQ(z.size(), x.size());
  const int x_size = static_cast<int>(x.size());
  const int vector_limit = x_size >> 3;

  int j = 0;
  for (; j < vector_limit * 8; j += 8) {
    const __m256 x_j = _mm256_loadu_ps(&x[j]);
    __m256 z_j = _mm256_loadu_ps(&z[j]);
    z_j = _mm256_add_ps(x_j, z_j);
    _mm256_storeu_ps(&z[j], z_j);
  }

  for (; j < x_size; ++j) {
    z[j] += x[j];
  }
}

}  // namespace aec3
}  // namespace webrtc
