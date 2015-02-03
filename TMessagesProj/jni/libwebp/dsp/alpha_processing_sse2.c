// Copyright 2014 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// Utilities for processing transparent channel.
//
// Author: Skal (pascal.massimino@gmail.com)

#include "./dsp.h"

#if defined(WEBP_USE_SSE2)
#include <emmintrin.h>

//------------------------------------------------------------------------------

static int ExtractAlpha(const uint8_t* argb, int argb_stride,
                        int width, int height,
                        uint8_t* alpha, int alpha_stride) {
  // alpha_and stores an 'and' operation of all the alpha[] values. The final
  // value is not 0xff if any of the alpha[] is not equal to 0xff.
  uint32_t alpha_and = 0xff;
  int i, j;
  const __m128i a_mask = _mm_set1_epi32(0xffu);  // to preserve alpha
  const __m128i all_0xff = _mm_set_epi32(0, 0, ~0u, ~0u);
  __m128i all_alphas = all_0xff;

  // We must be able to access 3 extra bytes after the last written byte
  // 'src[4 * width - 4]', because we don't know if alpha is the first or the
  // last byte of the quadruplet.
  const int limit = (width - 1) & ~7;

  for (j = 0; j < height; ++j) {
    const __m128i* src = (const __m128i*)argb;
    for (i = 0; i < limit; i += 8) {
      // load 32 argb bytes
      const __m128i a0 = _mm_loadu_si128(src + 0);
      const __m128i a1 = _mm_loadu_si128(src + 1);
      const __m128i b0 = _mm_and_si128(a0, a_mask);
      const __m128i b1 = _mm_and_si128(a1, a_mask);
      const __m128i c0 = _mm_packs_epi32(b0, b1);
      const __m128i d0 = _mm_packus_epi16(c0, c0);
      // store
      _mm_storel_epi64((__m128i*)&alpha[i], d0);
      // accumulate eight alpha 'and' in parallel
      all_alphas = _mm_and_si128(all_alphas, d0);
      src += 2;
    }
    for (; i < width; ++i) {
      const uint32_t alpha_value = argb[4 * i];
      alpha[i] = alpha_value;
      alpha_and &= alpha_value;
    }
    argb += argb_stride;
    alpha += alpha_stride;
  }
  // Combine the eight alpha 'and' into a 8-bit mask.
  alpha_and &= _mm_movemask_epi8(_mm_cmpeq_epi8(all_alphas, all_0xff));
  return (alpha_and == 0xff);
}

#endif   // WEBP_USE_SSE2

//------------------------------------------------------------------------------
// Init function

extern void WebPInitAlphaProcessingSSE2(void);

void WebPInitAlphaProcessingSSE2(void) {
#if defined(WEBP_USE_SSE2)
  WebPExtractAlpha = ExtractAlpha;
#endif
}
