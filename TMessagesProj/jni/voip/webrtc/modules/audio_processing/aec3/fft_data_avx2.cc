/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/fft_data.h"

#include <immintrin.h>

#include "api/array_view.h"

namespace webrtc {

// Computes the power spectrum of the data.
void FftData::SpectrumAVX2(rtc::ArrayView<float> power_spectrum) const {
  RTC_DCHECK_EQ(kFftLengthBy2Plus1, power_spectrum.size());
  for (size_t k = 0; k < kFftLengthBy2; k += 8) {
    __m256 r = _mm256_loadu_ps(&re[k]);
    __m256 i = _mm256_loadu_ps(&im[k]);
    __m256 ii = _mm256_mul_ps(i, i);
    ii = _mm256_fmadd_ps(r, r, ii);
    _mm256_storeu_ps(&power_spectrum[k], ii);
  }
  power_spectrum[kFftLengthBy2] = re[kFftLengthBy2] * re[kFftLengthBy2] +
                                  im[kFftLengthBy2] * im[kFftLengthBy2];
}

}  // namespace webrtc
