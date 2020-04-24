/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Modified from the Chromium original:
// src/media/base/simd/sinc_resampler_sse.cc
#include "rtc_base/system/arch.h"
#ifdef WEBRTC_ARCH_X86_FAMILY

#include <stddef.h>
#include <stdint.h>
#include <xmmintrin.h>

#include "common_audio/resampler/sinc_resampler.h"

namespace webrtc {

float SincResampler::Convolve_SSE(const float* input_ptr,
                                  const float* k1,
                                  const float* k2,
                                  double kernel_interpolation_factor) {
  __m128 m_input;
  __m128 m_sums1 = _mm_setzero_ps();
  __m128 m_sums2 = _mm_setzero_ps();

  // Based on |input_ptr| alignment, we need to use loadu or load.  Unrolling
  // these loops hurt performance in local testing.
  if (reinterpret_cast<uintptr_t>(input_ptr) & 0x0F) {
    for (size_t i = 0; i < kKernelSize; i += 4) {
      m_input = _mm_loadu_ps(input_ptr + i);
      m_sums1 = _mm_add_ps(m_sums1, _mm_mul_ps(m_input, _mm_load_ps(k1 + i)));
      m_sums2 = _mm_add_ps(m_sums2, _mm_mul_ps(m_input, _mm_load_ps(k2 + i)));
    }
  } else {
    for (size_t i = 0; i < kKernelSize; i += 4) {
      m_input = _mm_load_ps(input_ptr + i);
      m_sums1 = _mm_add_ps(m_sums1, _mm_mul_ps(m_input, _mm_load_ps(k1 + i)));
      m_sums2 = _mm_add_ps(m_sums2, _mm_mul_ps(m_input, _mm_load_ps(k2 + i)));
    }
  }

  // Linearly interpolate the two "convolutions".
  m_sums1 = _mm_mul_ps(
      m_sums1,
      _mm_set_ps1(static_cast<float>(1.0 - kernel_interpolation_factor)));
  m_sums2 = _mm_mul_ps(
      m_sums2, _mm_set_ps1(static_cast<float>(kernel_interpolation_factor)));
  m_sums1 = _mm_add_ps(m_sums1, m_sums2);

  // Sum components together.
  float result;
  m_sums2 = _mm_add_ps(_mm_movehl_ps(m_sums1, m_sums1), m_sums1);
  _mm_store_ss(&result,
               _mm_add_ss(m_sums2, _mm_shuffle_ps(m_sums2, m_sums2, 1)));

  return result;
}

}  // namespace webrtc

#endif
