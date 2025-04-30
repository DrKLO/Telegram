/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/differ_vector_sse2.h"

#if defined(_MSC_VER)
#include <intrin.h>
#else
#include <emmintrin.h>
#include <mmintrin.h>
#endif

namespace webrtc {

extern bool VectorDifference_SSE2_W16(const uint8_t* image1,
                                      const uint8_t* image2) {
  __m128i acc = _mm_setzero_si128();
  __m128i v0;
  __m128i v1;
  __m128i sad;
  const __m128i* i1 = reinterpret_cast<const __m128i*>(image1);
  const __m128i* i2 = reinterpret_cast<const __m128i*>(image2);
  v0 = _mm_loadu_si128(i1);
  v1 = _mm_loadu_si128(i2);
  sad = _mm_sad_epu8(v0, v1);
  acc = _mm_adds_epu16(acc, sad);
  v0 = _mm_loadu_si128(i1 + 1);
  v1 = _mm_loadu_si128(i2 + 1);
  sad = _mm_sad_epu8(v0, v1);
  acc = _mm_adds_epu16(acc, sad);
  v0 = _mm_loadu_si128(i1 + 2);
  v1 = _mm_loadu_si128(i2 + 2);
  sad = _mm_sad_epu8(v0, v1);
  acc = _mm_adds_epu16(acc, sad);
  v0 = _mm_loadu_si128(i1 + 3);
  v1 = _mm_loadu_si128(i2 + 3);
  sad = _mm_sad_epu8(v0, v1);
  acc = _mm_adds_epu16(acc, sad);

  // This essential means sad = acc >> 64. We only care about the lower 16
  // bits.
  sad = _mm_shuffle_epi32(acc, 0xEE);
  sad = _mm_adds_epu16(sad, acc);
  return _mm_cvtsi128_si32(sad) != 0;
}

extern bool VectorDifference_SSE2_W32(const uint8_t* image1,
                                      const uint8_t* image2) {
  __m128i acc = _mm_setzero_si128();
  __m128i v0;
  __m128i v1;
  __m128i sad;
  const __m128i* i1 = reinterpret_cast<const __m128i*>(image1);
  const __m128i* i2 = reinterpret_cast<const __m128i*>(image2);
  v0 = _mm_loadu_si128(i1);
  v1 = _mm_loadu_si128(i2);
  sad = _mm_sad_epu8(v0, v1);
  acc = _mm_adds_epu16(acc, sad);
  v0 = _mm_loadu_si128(i1 + 1);
  v1 = _mm_loadu_si128(i2 + 1);
  sad = _mm_sad_epu8(v0, v1);
  acc = _mm_adds_epu16(acc, sad);
  v0 = _mm_loadu_si128(i1 + 2);
  v1 = _mm_loadu_si128(i2 + 2);
  sad = _mm_sad_epu8(v0, v1);
  acc = _mm_adds_epu16(acc, sad);
  v0 = _mm_loadu_si128(i1 + 3);
  v1 = _mm_loadu_si128(i2 + 3);
  sad = _mm_sad_epu8(v0, v1);
  acc = _mm_adds_epu16(acc, sad);
  v0 = _mm_loadu_si128(i1 + 4);
  v1 = _mm_loadu_si128(i2 + 4);
  sad = _mm_sad_epu8(v0, v1);
  acc = _mm_adds_epu16(acc, sad);
  v0 = _mm_loadu_si128(i1 + 5);
  v1 = _mm_loadu_si128(i2 + 5);
  sad = _mm_sad_epu8(v0, v1);
  acc = _mm_adds_epu16(acc, sad);
  v0 = _mm_loadu_si128(i1 + 6);
  v1 = _mm_loadu_si128(i2 + 6);
  sad = _mm_sad_epu8(v0, v1);
  acc = _mm_adds_epu16(acc, sad);
  v0 = _mm_loadu_si128(i1 + 7);
  v1 = _mm_loadu_si128(i2 + 7);
  sad = _mm_sad_epu8(v0, v1);
  acc = _mm_adds_epu16(acc, sad);

  // This essential means sad = acc >> 64. We only care about the lower 16
  // bits.
  sad = _mm_shuffle_epi32(acc, 0xEE);
  sad = _mm_adds_epu16(sad, acc);
  return _mm_cvtsi128_si32(sad) != 0;
}

}  // namespace webrtc
