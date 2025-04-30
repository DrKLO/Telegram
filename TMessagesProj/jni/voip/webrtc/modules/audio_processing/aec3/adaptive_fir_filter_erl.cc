/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/adaptive_fir_filter_erl.h"

#include <algorithm>
#include <functional>

#if defined(WEBRTC_HAS_NEON)
#include <arm_neon.h>
#endif
#if defined(WEBRTC_ARCH_X86_FAMILY)
#include <emmintrin.h>
#endif

namespace webrtc {

namespace aec3 {

// Computes and stores the echo return loss estimate of the filter, which is the
// sum of the partition frequency responses.
void ErlComputer(const std::vector<std::array<float, kFftLengthBy2Plus1>>& H2,
                 rtc::ArrayView<float> erl) {
  std::fill(erl.begin(), erl.end(), 0.f);
  for (auto& H2_j : H2) {
    std::transform(H2_j.begin(), H2_j.end(), erl.begin(), erl.begin(),
                   std::plus<float>());
  }
}

#if defined(WEBRTC_HAS_NEON)
// Computes and stores the echo return loss estimate of the filter, which is the
// sum of the partition frequency responses.
void ErlComputer_NEON(
    const std::vector<std::array<float, kFftLengthBy2Plus1>>& H2,
    rtc::ArrayView<float> erl) {
  std::fill(erl.begin(), erl.end(), 0.f);
  for (auto& H2_j : H2) {
    for (size_t k = 0; k < kFftLengthBy2; k += 4) {
      const float32x4_t H2_j_k = vld1q_f32(&H2_j[k]);
      float32x4_t erl_k = vld1q_f32(&erl[k]);
      erl_k = vaddq_f32(erl_k, H2_j_k);
      vst1q_f32(&erl[k], erl_k);
    }
    erl[kFftLengthBy2] += H2_j[kFftLengthBy2];
  }
}
#endif

#if defined(WEBRTC_ARCH_X86_FAMILY)
// Computes and stores the echo return loss estimate of the filter, which is the
// sum of the partition frequency responses.
void ErlComputer_SSE2(
    const std::vector<std::array<float, kFftLengthBy2Plus1>>& H2,
    rtc::ArrayView<float> erl) {
  std::fill(erl.begin(), erl.end(), 0.f);
  for (auto& H2_j : H2) {
    for (size_t k = 0; k < kFftLengthBy2; k += 4) {
      const __m128 H2_j_k = _mm_loadu_ps(&H2_j[k]);
      __m128 erl_k = _mm_loadu_ps(&erl[k]);
      erl_k = _mm_add_ps(erl_k, H2_j_k);
      _mm_storeu_ps(&erl[k], erl_k);
    }
    erl[kFftLengthBy2] += H2_j[kFftLengthBy2];
  }
}
#endif

}  // namespace aec3

void ComputeErl(const Aec3Optimization& optimization,
                const std::vector<std::array<float, kFftLengthBy2Plus1>>& H2,
                rtc::ArrayView<float> erl) {
  RTC_DCHECK_EQ(kFftLengthBy2Plus1, erl.size());
  // Update the frequency response and echo return loss for the filter.
  switch (optimization) {
#if defined(WEBRTC_ARCH_X86_FAMILY)
    case Aec3Optimization::kSse2:
      aec3::ErlComputer_SSE2(H2, erl);
      break;
    // TODO(@dkaraush): compile with avx support
    /*case Aec3Optimization::kAvx2:
      aec3::ErlComputer_AVX2(H2, erl);
      break;
    */
#endif
#if defined(WEBRTC_HAS_NEON)
    case Aec3Optimization::kNeon:
      aec3::ErlComputer_NEON(H2, erl);
      break;
#endif
    default:
      aec3::ErlComputer(H2, erl);
  }
}

}  // namespace webrtc
