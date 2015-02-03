// Copyright 2014 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// SSE2 variant of methods for lossless decoder
//
// Author: Skal (pascal.massimino@gmail.com)

#include "./dsp.h"

#include <assert.h>

#if defined(WEBP_USE_SSE2)
#include <emmintrin.h>
#include "./lossless.h"

//------------------------------------------------------------------------------
// Predictor Transform

static WEBP_INLINE uint32_t ClampedAddSubtractFull(uint32_t c0, uint32_t c1,
                                                   uint32_t c2) {
  const __m128i zero = _mm_setzero_si128();
  const __m128i C0 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(c0), zero);
  const __m128i C1 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(c1), zero);
  const __m128i C2 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(c2), zero);
  const __m128i V1 = _mm_add_epi16(C0, C1);
  const __m128i V2 = _mm_sub_epi16(V1, C2);
  const __m128i b = _mm_packus_epi16(V2, V2);
  const uint32_t output = _mm_cvtsi128_si32(b);
  return output;
}

static WEBP_INLINE uint32_t ClampedAddSubtractHalf(uint32_t c0, uint32_t c1,
                                                   uint32_t c2) {
  const __m128i zero = _mm_setzero_si128();
  const __m128i C0 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(c0), zero);
  const __m128i C1 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(c1), zero);
  const __m128i B0 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(c2), zero);
  const __m128i avg = _mm_add_epi16(C1, C0);
  const __m128i A0 = _mm_srli_epi16(avg, 1);
  const __m128i A1 = _mm_sub_epi16(A0, B0);
  const __m128i BgtA = _mm_cmpgt_epi16(B0, A0);
  const __m128i A2 = _mm_sub_epi16(A1, BgtA);
  const __m128i A3 = _mm_srai_epi16(A2, 1);
  const __m128i A4 = _mm_add_epi16(A0, A3);
  const __m128i A5 = _mm_packus_epi16(A4, A4);
  const uint32_t output = _mm_cvtsi128_si32(A5);
  return output;
}

static WEBP_INLINE uint32_t Select(uint32_t a, uint32_t b, uint32_t c) {
  int pa_minus_pb;
  const __m128i zero = _mm_setzero_si128();
  const __m128i A0 = _mm_cvtsi32_si128(a);
  const __m128i B0 = _mm_cvtsi32_si128(b);
  const __m128i C0 = _mm_cvtsi32_si128(c);
  const __m128i AC0 = _mm_subs_epu8(A0, C0);
  const __m128i CA0 = _mm_subs_epu8(C0, A0);
  const __m128i BC0 = _mm_subs_epu8(B0, C0);
  const __m128i CB0 = _mm_subs_epu8(C0, B0);
  const __m128i AC = _mm_or_si128(AC0, CA0);
  const __m128i BC = _mm_or_si128(BC0, CB0);
  const __m128i pa = _mm_unpacklo_epi8(AC, zero);  // |a - c|
  const __m128i pb = _mm_unpacklo_epi8(BC, zero);  // |b - c|
  const __m128i diff = _mm_sub_epi16(pb, pa);
  {
    int16_t out[8];
    _mm_storeu_si128((__m128i*)out, diff);
    pa_minus_pb = out[0] + out[1] + out[2] + out[3];
  }
  return (pa_minus_pb <= 0) ? a : b;
}

static WEBP_INLINE __m128i Average2_128i(uint32_t a0, uint32_t a1) {
  const __m128i zero = _mm_setzero_si128();
  const __m128i A0 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(a0), zero);
  const __m128i A1 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(a1), zero);
  const __m128i sum = _mm_add_epi16(A1, A0);
  const __m128i avg = _mm_srli_epi16(sum, 1);
  return avg;
}

static WEBP_INLINE uint32_t Average2(uint32_t a0, uint32_t a1) {
  const __m128i avg = Average2_128i(a0, a1);
  const __m128i A2 = _mm_packus_epi16(avg, avg);
  const uint32_t output = _mm_cvtsi128_si32(A2);
  return output;
}

static WEBP_INLINE uint32_t Average3(uint32_t a0, uint32_t a1, uint32_t a2) {
  const __m128i zero = _mm_setzero_si128();
  const __m128i avg1 = Average2_128i(a0, a2);
  const __m128i A1 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(a1), zero);
  const __m128i sum = _mm_add_epi16(avg1, A1);
  const __m128i avg2 = _mm_srli_epi16(sum, 1);
  const __m128i A2 = _mm_packus_epi16(avg2, avg2);
  const uint32_t output = _mm_cvtsi128_si32(A2);
  return output;
}

static WEBP_INLINE uint32_t Average4(uint32_t a0, uint32_t a1,
                                     uint32_t a2, uint32_t a3) {
  const __m128i avg1 = Average2_128i(a0, a1);
  const __m128i avg2 = Average2_128i(a2, a3);
  const __m128i sum = _mm_add_epi16(avg2, avg1);
  const __m128i avg3 = _mm_srli_epi16(sum, 1);
  const __m128i A0 = _mm_packus_epi16(avg3, avg3);
  const uint32_t output = _mm_cvtsi128_si32(A0);
  return output;
}

static uint32_t Predictor5(uint32_t left, const uint32_t* const top) {
  const uint32_t pred = Average3(left, top[0], top[1]);
  return pred;
}
static uint32_t Predictor6(uint32_t left, const uint32_t* const top) {
  const uint32_t pred = Average2(left, top[-1]);
  return pred;
}
static uint32_t Predictor7(uint32_t left, const uint32_t* const top) {
  const uint32_t pred = Average2(left, top[0]);
  return pred;
}
static uint32_t Predictor8(uint32_t left, const uint32_t* const top) {
  const uint32_t pred = Average2(top[-1], top[0]);
  (void)left;
  return pred;
}
static uint32_t Predictor9(uint32_t left, const uint32_t* const top) {
  const uint32_t pred = Average2(top[0], top[1]);
  (void)left;
  return pred;
}
static uint32_t Predictor10(uint32_t left, const uint32_t* const top) {
  const uint32_t pred = Average4(left, top[-1], top[0], top[1]);
  return pred;
}
static uint32_t Predictor11(uint32_t left, const uint32_t* const top) {
  const uint32_t pred = Select(top[0], left, top[-1]);
  return pred;
}
static uint32_t Predictor12(uint32_t left, const uint32_t* const top) {
  const uint32_t pred = ClampedAddSubtractFull(left, top[0], top[-1]);
  return pred;
}
static uint32_t Predictor13(uint32_t left, const uint32_t* const top) {
  const uint32_t pred = ClampedAddSubtractHalf(left, top[0], top[-1]);
  return pred;
}

//------------------------------------------------------------------------------
// Subtract-Green Transform

static void SubtractGreenFromBlueAndRed(uint32_t* argb_data, int num_pixels) {
  const __m128i mask = _mm_set1_epi32(0x0000ff00);
  int i;
  for (i = 0; i + 4 <= num_pixels; i += 4) {
    const __m128i in = _mm_loadu_si128((__m128i*)&argb_data[i]);
    const __m128i in_00g0 = _mm_and_si128(in, mask);     // 00g0|00g0|...
    const __m128i in_0g00 = _mm_slli_epi32(in_00g0, 8);  // 0g00|0g00|...
    const __m128i in_000g = _mm_srli_epi32(in_00g0, 8);  // 000g|000g|...
    const __m128i in_0g0g = _mm_or_si128(in_0g00, in_000g);
    const __m128i out = _mm_sub_epi8(in, in_0g0g);
    _mm_storeu_si128((__m128i*)&argb_data[i], out);
  }
  // fallthrough and finish off with plain-C
  VP8LSubtractGreenFromBlueAndRed_C(argb_data + i, num_pixels - i);
}

static void AddGreenToBlueAndRed(uint32_t* argb_data, int num_pixels) {
  const __m128i mask = _mm_set1_epi32(0x0000ff00);
  int i;
  for (i = 0; i + 4 <= num_pixels; i += 4) {
    const __m128i in = _mm_loadu_si128((__m128i*)&argb_data[i]);
    const __m128i in_00g0 = _mm_and_si128(in, mask);     // 00g0|00g0|...
    const __m128i in_0g00 = _mm_slli_epi32(in_00g0, 8);  // 0g00|0g00|...
    const __m128i in_000g = _mm_srli_epi32(in_00g0, 8);  // 000g|000g|...
    const __m128i in_0g0g = _mm_or_si128(in_0g00, in_000g);
    const __m128i out = _mm_add_epi8(in, in_0g0g);
    _mm_storeu_si128((__m128i*)&argb_data[i], out);
  }
  // fallthrough and finish off with plain-C
  VP8LAddGreenToBlueAndRed_C(argb_data + i, num_pixels - i);
}

//------------------------------------------------------------------------------
// Color Transform

static WEBP_INLINE __m128i ColorTransformDelta(__m128i color_pred,
                                               __m128i color) {
  // We simulate signed 8-bit multiplication as:
  // * Left shift the two (8-bit) numbers by 8 bits,
  // * Perform a 16-bit signed multiplication and retain the higher 16-bits.
  const __m128i color_pred_shifted = _mm_slli_epi32(color_pred, 8);
  const __m128i color_shifted = _mm_slli_epi32(color, 8);
  // Note: This performs multiplication on 8 packed 16-bit numbers, 4 of which
  // happen to be zeroes.
  const __m128i signed_mult =
      _mm_mulhi_epi16(color_pred_shifted, color_shifted);
  return _mm_srli_epi32(signed_mult, 5);
}

static WEBP_INLINE void TransformColor(const VP8LMultipliers* const m,
                                       uint32_t* argb_data,
                                       int num_pixels) {
  const __m128i g_to_r = _mm_set1_epi32(m->green_to_red_);       // multipliers
  const __m128i g_to_b = _mm_set1_epi32(m->green_to_blue_);
  const __m128i r_to_b = _mm_set1_epi32(m->red_to_blue_);

  int i;

  for (i = 0; i + 4 <= num_pixels; i += 4) {
    const __m128i in = _mm_loadu_si128((__m128i*)&argb_data[i]);
    const __m128i alpha_green_mask = _mm_set1_epi32(0xff00ff00);  // masks
    const __m128i red_mask = _mm_set1_epi32(0x00ff0000);
    const __m128i green_mask = _mm_set1_epi32(0x0000ff00);
    const __m128i lower_8bit_mask  = _mm_set1_epi32(0x000000ff);
    const __m128i ag = _mm_and_si128(in, alpha_green_mask);      // alpha, green
    const __m128i r = _mm_srli_epi32(_mm_and_si128(in, red_mask), 16);
    const __m128i g = _mm_srli_epi32(_mm_and_si128(in, green_mask), 8);
    const __m128i b = in;

    const __m128i r_delta = ColorTransformDelta(g_to_r, g);      // red
    const __m128i r_new =
        _mm_and_si128(_mm_sub_epi32(r, r_delta), lower_8bit_mask);
    const __m128i r_new_shifted = _mm_slli_epi32(r_new, 16);

    const __m128i b_delta_1 = ColorTransformDelta(g_to_b, g);    // blue
    const __m128i b_delta_2 = ColorTransformDelta(r_to_b, r);
    const __m128i b_delta = _mm_add_epi32(b_delta_1, b_delta_2);
    const __m128i b_new =
        _mm_and_si128(_mm_sub_epi32(b, b_delta), lower_8bit_mask);

    const __m128i out = _mm_or_si128(_mm_or_si128(ag, r_new_shifted), b_new);
    _mm_storeu_si128((__m128i*)&argb_data[i], out);
  }

  // Fall-back to C-version for left-overs.
  VP8LTransformColor_C(m, argb_data + i, num_pixels - i);
}

static WEBP_INLINE void TransformColorInverse(const VP8LMultipliers* const m,
                                              uint32_t* argb_data,
                                              int num_pixels) {
  const __m128i g_to_r = _mm_set1_epi32(m->green_to_red_);       // multipliers
  const __m128i g_to_b = _mm_set1_epi32(m->green_to_blue_);
  const __m128i r_to_b = _mm_set1_epi32(m->red_to_blue_);

  int i;

  for (i = 0; i + 4 <= num_pixels; i += 4) {
    const __m128i in = _mm_loadu_si128((__m128i*)&argb_data[i]);
    const __m128i alpha_green_mask = _mm_set1_epi32(0xff00ff00);  // masks
    const __m128i red_mask = _mm_set1_epi32(0x00ff0000);
    const __m128i green_mask = _mm_set1_epi32(0x0000ff00);
    const __m128i lower_8bit_mask  = _mm_set1_epi32(0x000000ff);
    const __m128i ag = _mm_and_si128(in, alpha_green_mask);      // alpha, green
    const __m128i r = _mm_srli_epi32(_mm_and_si128(in, red_mask), 16);
    const __m128i g = _mm_srli_epi32(_mm_and_si128(in, green_mask), 8);
    const __m128i b = in;

    const __m128i r_delta = ColorTransformDelta(g_to_r, g);      // red
    const __m128i r_new =
        _mm_and_si128(_mm_add_epi32(r, r_delta), lower_8bit_mask);
    const __m128i r_new_shifted = _mm_slli_epi32(r_new, 16);

    const __m128i b_delta_1 = ColorTransformDelta(g_to_b, g);    // blue
    const __m128i b_delta_2 = ColorTransformDelta(r_to_b, r_new);
    const __m128i b_delta = _mm_add_epi32(b_delta_1, b_delta_2);
    const __m128i b_new =
        _mm_and_si128(_mm_add_epi32(b, b_delta), lower_8bit_mask);

    const __m128i out = _mm_or_si128(_mm_or_si128(ag, r_new_shifted), b_new);
    _mm_storeu_si128((__m128i*)&argb_data[i], out);
  }

  // Fall-back to C-version for left-overs.
  VP8LTransformColorInverse_C(m, argb_data + i, num_pixels - i);
}

//------------------------------------------------------------------------------
// Color-space conversion functions

static void ConvertBGRAToRGBA(const uint32_t* src,
                              int num_pixels, uint8_t* dst) {
  const __m128i* in = (const __m128i*)src;
  __m128i* out = (__m128i*)dst;
  while (num_pixels >= 8) {
    const __m128i bgra0 = _mm_loadu_si128(in++);     // bgra0|bgra1|bgra2|bgra3
    const __m128i bgra4 = _mm_loadu_si128(in++);     // bgra4|bgra5|bgra6|bgra7
    const __m128i v0l = _mm_unpacklo_epi8(bgra0, bgra4);  // b0b4g0g4r0r4a0a4...
    const __m128i v0h = _mm_unpackhi_epi8(bgra0, bgra4);  // b2b6g2g6r2r6a2a6...
    const __m128i v1l = _mm_unpacklo_epi8(v0l, v0h);   // b0b2b4b6g0g2g4g6...
    const __m128i v1h = _mm_unpackhi_epi8(v0l, v0h);   // b1b3b5b7g1g3g5g7...
    const __m128i v2l = _mm_unpacklo_epi8(v1l, v1h);   // b0...b7 | g0...g7
    const __m128i v2h = _mm_unpackhi_epi8(v1l, v1h);   // r0...r7 | a0...a7
    const __m128i ga0 = _mm_unpackhi_epi64(v2l, v2h);  // g0...g7 | a0...a7
    const __m128i rb0 = _mm_unpacklo_epi64(v2h, v2l);  // r0...r7 | b0...b7
    const __m128i rg0 = _mm_unpacklo_epi8(rb0, ga0);   // r0g0r1g1 ... r6g6r7g7
    const __m128i ba0 = _mm_unpackhi_epi8(rb0, ga0);   // b0a0b1a1 ... b6a6b7a7
    const __m128i rgba0 = _mm_unpacklo_epi16(rg0, ba0);  // rgba0|rgba1...
    const __m128i rgba4 = _mm_unpackhi_epi16(rg0, ba0);  // rgba4|rgba5...
    _mm_storeu_si128(out++, rgba0);
    _mm_storeu_si128(out++, rgba4);
    num_pixels -= 8;
  }
  // left-overs
  VP8LConvertBGRAToRGBA_C((const uint32_t*)in, num_pixels, (uint8_t*)out);
}

static void ConvertBGRAToRGBA4444(const uint32_t* src,
                                  int num_pixels, uint8_t* dst) {
  const __m128i mask_0x0f = _mm_set1_epi8(0x0f);
  const __m128i mask_0xf0 = _mm_set1_epi8(0xf0);
  const __m128i* in = (const __m128i*)src;
  __m128i* out = (__m128i*)dst;
  while (num_pixels >= 8) {
    const __m128i bgra0 = _mm_loadu_si128(in++);     // bgra0|bgra1|bgra2|bgra3
    const __m128i bgra4 = _mm_loadu_si128(in++);     // bgra4|bgra5|bgra6|bgra7
    const __m128i v0l = _mm_unpacklo_epi8(bgra0, bgra4);  // b0b4g0g4r0r4a0a4...
    const __m128i v0h = _mm_unpackhi_epi8(bgra0, bgra4);  // b2b6g2g6r2r6a2a6...
    const __m128i v1l = _mm_unpacklo_epi8(v0l, v0h);    // b0b2b4b6g0g2g4g6...
    const __m128i v1h = _mm_unpackhi_epi8(v0l, v0h);    // b1b3b5b7g1g3g5g7...
    const __m128i v2l = _mm_unpacklo_epi8(v1l, v1h);    // b0...b7 | g0...g7
    const __m128i v2h = _mm_unpackhi_epi8(v1l, v1h);    // r0...r7 | a0...a7
    const __m128i ga0 = _mm_unpackhi_epi64(v2l, v2h);   // g0...g7 | a0...a7
    const __m128i rb0 = _mm_unpacklo_epi64(v2h, v2l);   // r0...r7 | b0...b7
    const __m128i ga1 = _mm_srli_epi16(ga0, 4);         // g0-|g1-|...|a6-|a7-
    const __m128i rb1 = _mm_and_si128(rb0, mask_0xf0);  // -r0|-r1|...|-b6|-a7
    const __m128i ga2 = _mm_and_si128(ga1, mask_0x0f);  // g0-|g1-|...|a6-|a7-
    const __m128i rgba0 = _mm_or_si128(ga2, rb1);       // rg0..rg7 | ba0..ba7
    const __m128i rgba1 = _mm_srli_si128(rgba0, 8);     // ba0..ba7 | 0
#ifdef WEBP_SWAP_16BIT_CSP
    const __m128i rgba = _mm_unpacklo_epi8(rgba1, rgba0);  // barg0...barg7
#else
    const __m128i rgba = _mm_unpacklo_epi8(rgba0, rgba1);  // rgba0...rgba7
#endif
    _mm_storeu_si128(out++, rgba);
    num_pixels -= 8;
  }
  // left-overs
  VP8LConvertBGRAToRGBA4444_C((const uint32_t*)in, num_pixels, (uint8_t*)out);
}

static void ConvertBGRAToRGB565(const uint32_t* src,
                                int num_pixels, uint8_t* dst) {
  const __m128i mask_0xe0 = _mm_set1_epi8(0xe0);
  const __m128i mask_0xf8 = _mm_set1_epi8(0xf8);
  const __m128i mask_0x07 = _mm_set1_epi8(0x07);
  const __m128i* in = (const __m128i*)src;
  __m128i* out = (__m128i*)dst;
  while (num_pixels >= 8) {
    const __m128i bgra0 = _mm_loadu_si128(in++);     // bgra0|bgra1|bgra2|bgra3
    const __m128i bgra4 = _mm_loadu_si128(in++);     // bgra4|bgra5|bgra6|bgra7
    const __m128i v0l = _mm_unpacklo_epi8(bgra0, bgra4);  // b0b4g0g4r0r4a0a4...
    const __m128i v0h = _mm_unpackhi_epi8(bgra0, bgra4);  // b2b6g2g6r2r6a2a6...
    const __m128i v1l = _mm_unpacklo_epi8(v0l, v0h);      // b0b2b4b6g0g2g4g6...
    const __m128i v1h = _mm_unpackhi_epi8(v0l, v0h);      // b1b3b5b7g1g3g5g7...
    const __m128i v2l = _mm_unpacklo_epi8(v1l, v1h);      // b0...b7 | g0...g7
    const __m128i v2h = _mm_unpackhi_epi8(v1l, v1h);      // r0...r7 | a0...a7
    const __m128i ga0 = _mm_unpackhi_epi64(v2l, v2h);     // g0...g7 | a0...a7
    const __m128i rb0 = _mm_unpacklo_epi64(v2h, v2l);     // r0...r7 | b0...b7
    const __m128i rb1 = _mm_and_si128(rb0, mask_0xf8);    // -r0..-r7|-b0..-b7
    const __m128i g_lo1 = _mm_srli_epi16(ga0, 5);
    const __m128i g_lo2 = _mm_and_si128(g_lo1, mask_0x07);  // g0-...g7-|xx (3b)
    const __m128i g_hi1 = _mm_slli_epi16(ga0, 3);
    const __m128i g_hi2 = _mm_and_si128(g_hi1, mask_0xe0);  // -g0...-g7|xx (3b)
    const __m128i b0 = _mm_srli_si128(rb1, 8);              // -b0...-b7|0
    const __m128i rg1 = _mm_or_si128(rb1, g_lo2);           // gr0...gr7|xx
    const __m128i b1 = _mm_srli_epi16(b0, 3);
    const __m128i gb1 = _mm_or_si128(b1, g_hi2);            // bg0...bg7|xx
#ifdef WEBP_SWAP_16BIT_CSP
    const __m128i rgba = _mm_unpacklo_epi8(gb1, rg1);     // rggb0...rggb7
#else
    const __m128i rgba = _mm_unpacklo_epi8(rg1, gb1);     // bgrb0...bgrb7
#endif
    _mm_storeu_si128(out++, rgba);
    num_pixels -= 8;
  }
  // left-overs
  VP8LConvertBGRAToRGB565_C((const uint32_t*)in, num_pixels, (uint8_t*)out);
}

static void ConvertBGRAToBGR(const uint32_t* src,
                             int num_pixels, uint8_t* dst) {
  const __m128i mask_l = _mm_set_epi32(0, 0x00ffffff, 0, 0x00ffffff);
  const __m128i mask_h = _mm_set_epi32(0x00ffffff, 0, 0x00ffffff, 0);
  const __m128i* in = (const __m128i*)src;
  const uint8_t* const end = dst + num_pixels * 3;
  // the last storel_epi64 below writes 8 bytes starting at offset 18
  while (dst + 26 <= end) {
    const __m128i bgra0 = _mm_loadu_si128(in++);     // bgra0|bgra1|bgra2|bgra3
    const __m128i bgra4 = _mm_loadu_si128(in++);     // bgra4|bgra5|bgra6|bgra7
    const __m128i a0l = _mm_and_si128(bgra0, mask_l);   // bgr0|0|bgr0|0
    const __m128i a4l = _mm_and_si128(bgra4, mask_l);   // bgr0|0|bgr0|0
    const __m128i a0h = _mm_and_si128(bgra0, mask_h);   // 0|bgr0|0|bgr0
    const __m128i a4h = _mm_and_si128(bgra4, mask_h);   // 0|bgr0|0|bgr0
    const __m128i b0h = _mm_srli_epi64(a0h, 8);         // 000b|gr00|000b|gr00
    const __m128i b4h = _mm_srli_epi64(a4h, 8);         // 000b|gr00|000b|gr00
    const __m128i c0 = _mm_or_si128(a0l, b0h);          // rgbrgb00|rgbrgb00
    const __m128i c4 = _mm_or_si128(a4l, b4h);          // rgbrgb00|rgbrgb00
    const __m128i c2 = _mm_srli_si128(c0, 8);
    const __m128i c6 = _mm_srli_si128(c4, 8);
    _mm_storel_epi64((__m128i*)(dst +   0), c0);
    _mm_storel_epi64((__m128i*)(dst +   6), c2);
    _mm_storel_epi64((__m128i*)(dst +  12), c4);
    _mm_storel_epi64((__m128i*)(dst +  18), c6);
    dst += 24;
    num_pixels -= 8;
  }
  // left-overs
  VP8LConvertBGRAToBGR_C((const uint32_t*)in, num_pixels, dst);
}

//------------------------------------------------------------------------------

#define LINE_SIZE 16    // 8 or 16
static void AddVector(const uint32_t* a, const uint32_t* b, uint32_t* out,
                      int size) {
  int i;
  assert(size % LINE_SIZE == 0);
  for (i = 0; i < size; i += LINE_SIZE) {
    const __m128i a0 = _mm_loadu_si128((__m128i*)&a[i +  0]);
    const __m128i a1 = _mm_loadu_si128((__m128i*)&a[i +  4]);
#if (LINE_SIZE == 16)
    const __m128i a2 = _mm_loadu_si128((__m128i*)&a[i +  8]);
    const __m128i a3 = _mm_loadu_si128((__m128i*)&a[i + 12]);
#endif
    const __m128i b0 = _mm_loadu_si128((__m128i*)&b[i +  0]);
    const __m128i b1 = _mm_loadu_si128((__m128i*)&b[i +  4]);
#if (LINE_SIZE == 16)
    const __m128i b2 = _mm_loadu_si128((__m128i*)&b[i +  8]);
    const __m128i b3 = _mm_loadu_si128((__m128i*)&b[i + 12]);
#endif
    _mm_storeu_si128((__m128i*)&out[i +  0], _mm_add_epi32(a0, b0));
    _mm_storeu_si128((__m128i*)&out[i +  4], _mm_add_epi32(a1, b1));
#if (LINE_SIZE == 16)
    _mm_storeu_si128((__m128i*)&out[i +  8], _mm_add_epi32(a2, b2));
    _mm_storeu_si128((__m128i*)&out[i + 12], _mm_add_epi32(a3, b3));
#endif
  }
}

static void AddVectorEq(const uint32_t* a, uint32_t* out, int size) {
  int i;
  assert(size % LINE_SIZE == 0);
  for (i = 0; i < size; i += LINE_SIZE) {
    const __m128i a0 = _mm_loadu_si128((__m128i*)&a[i +  0]);
    const __m128i a1 = _mm_loadu_si128((__m128i*)&a[i +  4]);
#if (LINE_SIZE == 16)
    const __m128i a2 = _mm_loadu_si128((__m128i*)&a[i +  8]);
    const __m128i a3 = _mm_loadu_si128((__m128i*)&a[i + 12]);
#endif
    const __m128i b0 = _mm_loadu_si128((__m128i*)&out[i +  0]);
    const __m128i b1 = _mm_loadu_si128((__m128i*)&out[i +  4]);
#if (LINE_SIZE == 16)
    const __m128i b2 = _mm_loadu_si128((__m128i*)&out[i +  8]);
    const __m128i b3 = _mm_loadu_si128((__m128i*)&out[i + 12]);
#endif
    _mm_storeu_si128((__m128i*)&out[i +  0], _mm_add_epi32(a0, b0));
    _mm_storeu_si128((__m128i*)&out[i +  4], _mm_add_epi32(a1, b1));
#if (LINE_SIZE == 16)
    _mm_storeu_si128((__m128i*)&out[i +  8], _mm_add_epi32(a2, b2));
    _mm_storeu_si128((__m128i*)&out[i + 12], _mm_add_epi32(a3, b3));
#endif
  }
}
#undef LINE_SIZE

// Note we are adding uint32_t's as *signed* int32's (using _mm_add_epi32). But
// that's ok since the histogram values are less than 1<<28 (max picture size).
static void HistogramAdd(const VP8LHistogram* const a,
                         const VP8LHistogram* const b,
                         VP8LHistogram* const out) {
  int i;
  const int literal_size = VP8LHistogramNumCodes(a->palette_code_bits_);
  assert(a->palette_code_bits_ == b->palette_code_bits_);
  if (b != out) {
    AddVector(a->literal_, b->literal_, out->literal_, NUM_LITERAL_CODES);
    AddVector(a->red_, b->red_, out->red_, NUM_LITERAL_CODES);
    AddVector(a->blue_, b->blue_, out->blue_, NUM_LITERAL_CODES);
    AddVector(a->alpha_, b->alpha_, out->alpha_, NUM_LITERAL_CODES);
  } else {
    AddVectorEq(a->literal_, out->literal_, NUM_LITERAL_CODES);
    AddVectorEq(a->red_, out->red_, NUM_LITERAL_CODES);
    AddVectorEq(a->blue_, out->blue_, NUM_LITERAL_CODES);
    AddVectorEq(a->alpha_, out->alpha_, NUM_LITERAL_CODES);
  }
  for (i = NUM_LITERAL_CODES; i < literal_size; ++i) {
    out->literal_[i] = a->literal_[i] + b->literal_[i];
  }
  for (i = 0; i < NUM_DISTANCE_CODES; ++i) {
    out->distance_[i] = a->distance_[i] + b->distance_[i];
  }
}

#endif   // WEBP_USE_SSE2

//------------------------------------------------------------------------------

extern void VP8LDspInitSSE2(void);

void VP8LDspInitSSE2(void) {
#if defined(WEBP_USE_SSE2)
  VP8LPredictors[5] = Predictor5;
  VP8LPredictors[6] = Predictor6;
  VP8LPredictors[7] = Predictor7;
  VP8LPredictors[8] = Predictor8;
  VP8LPredictors[9] = Predictor9;
  VP8LPredictors[10] = Predictor10;
  VP8LPredictors[11] = Predictor11;
  VP8LPredictors[12] = Predictor12;
  VP8LPredictors[13] = Predictor13;

  VP8LSubtractGreenFromBlueAndRed = SubtractGreenFromBlueAndRed;
  VP8LAddGreenToBlueAndRed = AddGreenToBlueAndRed;

  VP8LTransformColor = TransformColor;
  VP8LTransformColorInverse = TransformColorInverse;

  VP8LConvertBGRAToRGBA = ConvertBGRAToRGBA;
  VP8LConvertBGRAToRGBA4444 = ConvertBGRAToRGBA4444;
  VP8LConvertBGRAToRGB565 = ConvertBGRAToRGB565;
  VP8LConvertBGRAToBGR = ConvertBGRAToBGR;

  VP8LHistogramAdd = HistogramAdd;
#endif   // WEBP_USE_SSE2
}

//------------------------------------------------------------------------------
