// Copyright 2014 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// NEON variant of methods for lossless decoder
//
// Author: Skal (pascal.massimino@gmail.com)

#include "./dsp.h"

#if defined(WEBP_USE_NEON)

#include <arm_neon.h>

#include "./lossless.h"
#include "./neon.h"

//------------------------------------------------------------------------------
// Colorspace conversion functions

#if !defined(WORK_AROUND_GCC)
// gcc 4.6.0 had some trouble (NDK-r9) with this code. We only use it for
// gcc-4.8.x at least.
static void ConvertBGRAToRGBA(const uint32_t* src,
                              int num_pixels, uint8_t* dst) {
  const uint32_t* const end = src + (num_pixels & ~15);
  for (; src < end; src += 16) {
    uint8x16x4_t pixel = vld4q_u8((uint8_t*)src);
    // swap B and R. (VSWP d0,d2 has no intrinsics equivalent!)
    const uint8x16_t tmp = pixel.val[0];
    pixel.val[0] = pixel.val[2];
    pixel.val[2] = tmp;
    vst4q_u8(dst, pixel);
    dst += 64;
  }
  VP8LConvertBGRAToRGBA_C(src, num_pixels & 15, dst);  // left-overs
}

static void ConvertBGRAToBGR(const uint32_t* src,
                             int num_pixels, uint8_t* dst) {
  const uint32_t* const end = src + (num_pixels & ~15);
  for (; src < end; src += 16) {
    const uint8x16x4_t pixel = vld4q_u8((uint8_t*)src);
    const uint8x16x3_t tmp = { { pixel.val[0], pixel.val[1], pixel.val[2] } };
    vst3q_u8(dst, tmp);
    dst += 48;
  }
  VP8LConvertBGRAToBGR_C(src, num_pixels & 15, dst);  // left-overs
}

static void ConvertBGRAToRGB(const uint32_t* src,
                             int num_pixels, uint8_t* dst) {
  const uint32_t* const end = src + (num_pixels & ~15);
  for (; src < end; src += 16) {
    const uint8x16x4_t pixel = vld4q_u8((uint8_t*)src);
    const uint8x16x3_t tmp = { { pixel.val[2], pixel.val[1], pixel.val[0] } };
    vst3q_u8(dst, tmp);
    dst += 48;
  }
  VP8LConvertBGRAToRGB_C(src, num_pixels & 15, dst);  // left-overs
}

#else  // WORK_AROUND_GCC

// gcc-4.6.0 fallback

static const uint8_t kRGBAShuffle[8] = { 2, 1, 0, 3, 6, 5, 4, 7 };

static void ConvertBGRAToRGBA(const uint32_t* src,
                              int num_pixels, uint8_t* dst) {
  const uint32_t* const end = src + (num_pixels & ~1);
  const uint8x8_t shuffle = vld1_u8(kRGBAShuffle);
  for (; src < end; src += 2) {
    const uint8x8_t pixels = vld1_u8((uint8_t*)src);
    vst1_u8(dst, vtbl1_u8(pixels, shuffle));
    dst += 8;
  }
  VP8LConvertBGRAToRGBA_C(src, num_pixels & 1, dst);  // left-overs
}

static const uint8_t kBGRShuffle[3][8] = {
  {  0,  1,  2,  4,  5,  6,  8,  9 },
  { 10, 12, 13, 14, 16, 17, 18, 20 },
  { 21, 22, 24, 25, 26, 28, 29, 30 }
};

static void ConvertBGRAToBGR(const uint32_t* src,
                             int num_pixels, uint8_t* dst) {
  const uint32_t* const end = src + (num_pixels & ~7);
  const uint8x8_t shuffle0 = vld1_u8(kBGRShuffle[0]);
  const uint8x8_t shuffle1 = vld1_u8(kBGRShuffle[1]);
  const uint8x8_t shuffle2 = vld1_u8(kBGRShuffle[2]);
  for (; src < end; src += 8) {
    uint8x8x4_t pixels;
    INIT_VECTOR4(pixels,
                 vld1_u8((const uint8_t*)(src + 0)),
                 vld1_u8((const uint8_t*)(src + 2)),
                 vld1_u8((const uint8_t*)(src + 4)),
                 vld1_u8((const uint8_t*)(src + 6)));
    vst1_u8(dst +  0, vtbl4_u8(pixels, shuffle0));
    vst1_u8(dst +  8, vtbl4_u8(pixels, shuffle1));
    vst1_u8(dst + 16, vtbl4_u8(pixels, shuffle2));
    dst += 8 * 3;
  }
  VP8LConvertBGRAToBGR_C(src, num_pixels & 7, dst);  // left-overs
}

static const uint8_t kRGBShuffle[3][8] = {
  {  2,  1,  0,  6,  5,  4, 10,  9 },
  {  8, 14, 13, 12, 18, 17, 16, 22 },
  { 21, 20, 26, 25, 24, 30, 29, 28 }
};

static void ConvertBGRAToRGB(const uint32_t* src,
                             int num_pixels, uint8_t* dst) {
  const uint32_t* const end = src + (num_pixels & ~7);
  const uint8x8_t shuffle0 = vld1_u8(kRGBShuffle[0]);
  const uint8x8_t shuffle1 = vld1_u8(kRGBShuffle[1]);
  const uint8x8_t shuffle2 = vld1_u8(kRGBShuffle[2]);
  for (; src < end; src += 8) {
    uint8x8x4_t pixels;
    INIT_VECTOR4(pixels,
                 vld1_u8((const uint8_t*)(src + 0)),
                 vld1_u8((const uint8_t*)(src + 2)),
                 vld1_u8((const uint8_t*)(src + 4)),
                 vld1_u8((const uint8_t*)(src + 6)));
    vst1_u8(dst +  0, vtbl4_u8(pixels, shuffle0));
    vst1_u8(dst +  8, vtbl4_u8(pixels, shuffle1));
    vst1_u8(dst + 16, vtbl4_u8(pixels, shuffle2));
    dst += 8 * 3;
  }
  VP8LConvertBGRAToRGB_C(src, num_pixels & 7, dst);  // left-overs
}

#endif   // !WORK_AROUND_GCC

//------------------------------------------------------------------------------

#ifdef USE_INTRINSICS

static WEBP_INLINE uint32_t Average2(const uint32_t* const a,
                                     const uint32_t* const b) {
  const uint8x8_t a0 = vreinterpret_u8_u64(vcreate_u64(*a));
  const uint8x8_t b0 = vreinterpret_u8_u64(vcreate_u64(*b));
  const uint8x8_t avg = vhadd_u8(a0, b0);
  return vget_lane_u32(vreinterpret_u32_u8(avg), 0);
}

static WEBP_INLINE uint32_t Average3(const uint32_t* const a,
                                     const uint32_t* const b,
                                     const uint32_t* const c) {
  const uint8x8_t a0 = vreinterpret_u8_u64(vcreate_u64(*a));
  const uint8x8_t b0 = vreinterpret_u8_u64(vcreate_u64(*b));
  const uint8x8_t c0 = vreinterpret_u8_u64(vcreate_u64(*c));
  const uint8x8_t avg1 = vhadd_u8(a0, c0);
  const uint8x8_t avg2 = vhadd_u8(avg1, b0);
  return vget_lane_u32(vreinterpret_u32_u8(avg2), 0);
}

static WEBP_INLINE uint32_t Average4(const uint32_t* const a,
                                     const uint32_t* const b,
                                     const uint32_t* const c,
                                     const uint32_t* const d) {
  const uint8x8_t a0 = vreinterpret_u8_u64(vcreate_u64(*a));
  const uint8x8_t b0 = vreinterpret_u8_u64(vcreate_u64(*b));
  const uint8x8_t c0 = vreinterpret_u8_u64(vcreate_u64(*c));
  const uint8x8_t d0 = vreinterpret_u8_u64(vcreate_u64(*d));
  const uint8x8_t avg1 = vhadd_u8(a0, b0);
  const uint8x8_t avg2 = vhadd_u8(c0, d0);
  const uint8x8_t avg3 = vhadd_u8(avg1, avg2);
  return vget_lane_u32(vreinterpret_u32_u8(avg3), 0);
}

static uint32_t Predictor5(uint32_t left, const uint32_t* const top) {
  return Average3(&left, top + 0, top + 1);
}

static uint32_t Predictor6(uint32_t left, const uint32_t* const top) {
  return Average2(&left, top - 1);
}

static uint32_t Predictor7(uint32_t left, const uint32_t* const top) {
  return Average2(&left, top + 0);
}

static uint32_t Predictor8(uint32_t left, const uint32_t* const top) {
  (void)left;
  return Average2(top - 1, top + 0);
}

static uint32_t Predictor9(uint32_t left, const uint32_t* const top) {
  (void)left;
  return Average2(top + 0, top + 1);
}

static uint32_t Predictor10(uint32_t left, const uint32_t* const top) {
  return Average4(&left, top - 1, top + 0, top + 1);
}

//------------------------------------------------------------------------------

static WEBP_INLINE uint32_t Select(const uint32_t* const c0,
                                   const uint32_t* const c1,
                                   const uint32_t* const c2) {
  const uint8x8_t p0 = vreinterpret_u8_u64(vcreate_u64(*c0));
  const uint8x8_t p1 = vreinterpret_u8_u64(vcreate_u64(*c1));
  const uint8x8_t p2 = vreinterpret_u8_u64(vcreate_u64(*c2));
  const uint8x8_t bc = vabd_u8(p1, p2);   // |b-c|
  const uint8x8_t ac = vabd_u8(p0, p2);   // |a-c|
  const int16x4_t sum_bc = vreinterpret_s16_u16(vpaddl_u8(bc));
  const int16x4_t sum_ac = vreinterpret_s16_u16(vpaddl_u8(ac));
  const int32x2_t diff = vpaddl_s16(vsub_s16(sum_bc, sum_ac));
  const int32_t pa_minus_pb = vget_lane_s32(diff, 0);
  return (pa_minus_pb <= 0) ? *c0 : *c1;
}

static uint32_t Predictor11(uint32_t left, const uint32_t* const top) {
  return Select(top + 0, &left, top - 1);
}

static WEBP_INLINE uint32_t ClampedAddSubtractFull(const uint32_t* const c0,
                                                   const uint32_t* const c1,
                                                   const uint32_t* const c2) {
  const uint8x8_t p0 = vreinterpret_u8_u64(vcreate_u64(*c0));
  const uint8x8_t p1 = vreinterpret_u8_u64(vcreate_u64(*c1));
  const uint8x8_t p2 = vreinterpret_u8_u64(vcreate_u64(*c2));
  const uint16x8_t sum0 = vaddl_u8(p0, p1);                // add and widen
  const uint16x8_t sum1 = vqsubq_u16(sum0, vmovl_u8(p2));  // widen and subtract
  const uint8x8_t out = vqmovn_u16(sum1);                  // narrow and clamp
  return vget_lane_u32(vreinterpret_u32_u8(out), 0);
}

static uint32_t Predictor12(uint32_t left, const uint32_t* const top) {
  return ClampedAddSubtractFull(&left, top + 0, top - 1);
}

static WEBP_INLINE uint32_t ClampedAddSubtractHalf(const uint32_t* const c0,
                                                   const uint32_t* const c1,
                                                   const uint32_t* const c2) {
  const uint8x8_t p0 = vreinterpret_u8_u64(vcreate_u64(*c0));
  const uint8x8_t p1 = vreinterpret_u8_u64(vcreate_u64(*c1));
  const uint8x8_t p2 = vreinterpret_u8_u64(vcreate_u64(*c2));
  const uint8x8_t avg = vhadd_u8(p0, p1);                  // Average(c0,c1)
  const uint8x8_t ab = vshr_n_u8(vqsub_u8(avg, p2), 1);    // (a-b)>>1 saturated
  const uint8x8_t ba = vshr_n_u8(vqsub_u8(p2, avg), 1);    // (b-a)>>1 saturated
  const uint8x8_t out = vqsub_u8(vqadd_u8(avg, ab), ba);
  return vget_lane_u32(vreinterpret_u32_u8(out), 0);
}

static uint32_t Predictor13(uint32_t left, const uint32_t* const top) {
  return ClampedAddSubtractHalf(&left, top + 0, top - 1);
}

//------------------------------------------------------------------------------
// Subtract-Green Transform

// vtbl? are unavailable in iOS/arm64 builds.
#if !defined(__aarch64__)

// 255 = byte will be zero'd
static const uint8_t kGreenShuffle[8] = { 1, 255, 1, 255, 5, 255, 5, 255  };

static void SubtractGreenFromBlueAndRed(uint32_t* argb_data, int num_pixels) {
  const uint32_t* const end = argb_data + (num_pixels & ~3);
  const uint8x8_t shuffle = vld1_u8(kGreenShuffle);
  for (; argb_data < end; argb_data += 4) {
    const uint8x16_t argb = vld1q_u8((uint8_t*)argb_data);
    const uint8x16_t greens =
        vcombine_u8(vtbl1_u8(vget_low_u8(argb), shuffle),
                    vtbl1_u8(vget_high_u8(argb), shuffle));
    vst1q_u8((uint8_t*)argb_data, vsubq_u8(argb, greens));
  }
  // fallthrough and finish off with plain-C
  VP8LSubtractGreenFromBlueAndRed_C(argb_data, num_pixels & 3);
}

static void AddGreenToBlueAndRed(uint32_t* argb_data, int num_pixels) {
  const uint32_t* const end = argb_data + (num_pixels & ~3);
  const uint8x8_t shuffle = vld1_u8(kGreenShuffle);
  for (; argb_data < end; argb_data += 4) {
    const uint8x16_t argb = vld1q_u8((uint8_t*)argb_data);
    const uint8x16_t greens =
        vcombine_u8(vtbl1_u8(vget_low_u8(argb), shuffle),
                    vtbl1_u8(vget_high_u8(argb), shuffle));
    vst1q_u8((uint8_t*)argb_data, vaddq_u8(argb, greens));
  }
  // fallthrough and finish off with plain-C
  VP8LAddGreenToBlueAndRed_C(argb_data, num_pixels & 3);
}

#endif   // !__aarch64__

#endif   // USE_INTRINSICS

#endif   // WEBP_USE_NEON

//------------------------------------------------------------------------------

extern void VP8LDspInitNEON(void);

void VP8LDspInitNEON(void) {
#if defined(WEBP_USE_NEON)
  VP8LConvertBGRAToRGBA = ConvertBGRAToRGBA;
  VP8LConvertBGRAToBGR = ConvertBGRAToBGR;
  VP8LConvertBGRAToRGB = ConvertBGRAToRGB;

#ifdef USE_INTRINSICS
  VP8LPredictors[5] = Predictor5;
  VP8LPredictors[6] = Predictor6;
  VP8LPredictors[7] = Predictor7;
  VP8LPredictors[8] = Predictor8;
  VP8LPredictors[9] = Predictor9;
  VP8LPredictors[10] = Predictor10;
  VP8LPredictors[11] = Predictor11;
  VP8LPredictors[12] = Predictor12;
  VP8LPredictors[13] = Predictor13;

#if !defined(__aarch64__)
  VP8LSubtractGreenFromBlueAndRed = SubtractGreenFromBlueAndRed;
  VP8LAddGreenToBlueAndRed = AddGreenToBlueAndRed;
#endif
#endif

#endif   // WEBP_USE_NEON
}

//------------------------------------------------------------------------------
