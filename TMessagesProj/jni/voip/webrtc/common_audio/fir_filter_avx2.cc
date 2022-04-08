/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_audio/fir_filter_avx2.h"

#include <immintrin.h>
#include <stdint.h>
#include <string.h>
#include <xmmintrin.h>

#include "rtc_base/checks.h"
#include "rtc_base/memory/aligned_malloc.h"

namespace webrtc {

FIRFilterAVX2::FIRFilterAVX2(const float* unaligned_coefficients,
                             size_t unaligned_coefficients_length,
                             size_t max_input_length)
    :  // Closest higher multiple of eight.
      coefficients_length_((unaligned_coefficients_length + 7) & ~0x07),
      state_length_(coefficients_length_ - 1),
      coefficients_(static_cast<float*>(
          AlignedMalloc(sizeof(float) * coefficients_length_, 32))),
      state_(static_cast<float*>(
          AlignedMalloc(sizeof(float) * (max_input_length + state_length_),
                        32))) {
  // Add zeros at the end of the coefficients.
  RTC_DCHECK_GE(coefficients_length_, unaligned_coefficients_length);
  size_t padding = coefficients_length_ - unaligned_coefficients_length;
  memset(coefficients_.get(), 0, padding * sizeof(coefficients_[0]));
  // The coefficients are reversed to compensate for the order in which the
  // input samples are acquired (most recent last).
  for (size_t i = 0; i < unaligned_coefficients_length; ++i) {
    coefficients_[i + padding] =
        unaligned_coefficients[unaligned_coefficients_length - i - 1];
  }
  memset(state_.get(), 0,
         (max_input_length + state_length_) * sizeof(state_[0]));
}

FIRFilterAVX2::~FIRFilterAVX2() = default;

void FIRFilterAVX2::Filter(const float* in, size_t length, float* out) {
  RTC_DCHECK_GT(length, 0);

  memcpy(&state_[state_length_], in, length * sizeof(*in));

  // Convolves the input signal `in` with the filter kernel `coefficients_`
  // taking into account the previous state.
  for (size_t i = 0; i < length; ++i) {
    float* in_ptr = &state_[i];
    float* coef_ptr = coefficients_.get();

    __m256 m_sum = _mm256_setzero_ps();
    __m256 m_in;

    // Depending on if the pointer is aligned with 32 bytes or not it is loaded
    // differently.
    if (reinterpret_cast<uintptr_t>(in_ptr) & 0x1F) {
      for (size_t j = 0; j < coefficients_length_; j += 8) {
        m_in = _mm256_loadu_ps(in_ptr + j);
        m_sum = _mm256_fmadd_ps(m_in, _mm256_load_ps(coef_ptr + j), m_sum);
      }
    } else {
      for (size_t j = 0; j < coefficients_length_; j += 8) {
        m_in = _mm256_load_ps(in_ptr + j);
        m_sum = _mm256_fmadd_ps(m_in, _mm256_load_ps(coef_ptr + j), m_sum);
      }
    }
    __m128 m128_sum = _mm_add_ps(_mm256_extractf128_ps(m_sum, 0),
                                 _mm256_extractf128_ps(m_sum, 1));
    m128_sum = _mm_add_ps(_mm_movehl_ps(m128_sum, m128_sum), m128_sum);
    _mm_store_ss(out + i,
                 _mm_add_ss(m128_sum, _mm_shuffle_ps(m128_sum, m128_sum, 1)));
  }

  // Update current state.
  memmove(state_.get(), &state_[length], state_length_ * sizeof(state_[0]));
}

}  // namespace webrtc
