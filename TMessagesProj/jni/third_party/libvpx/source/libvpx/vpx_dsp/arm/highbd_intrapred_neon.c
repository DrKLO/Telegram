/*
 *  Copyright (c) 2016 The WebM project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <arm_neon.h>

#include "./vpx_config.h"
#include "./vpx_dsp_rtcd.h"
#include "vpx/vpx_integer.h"

//------------------------------------------------------------------------------
// DC 4x4

static INLINE uint16x4_t dc_sum_4(const uint16_t *ref) {
  const uint16x4_t ref_u16 = vld1_u16(ref);
  const uint16x4_t p0 = vpadd_u16(ref_u16, ref_u16);
  return vpadd_u16(p0, p0);
}

static INLINE void dc_store_4x4(uint16_t *dst, ptrdiff_t stride,
                                const uint16x4_t dc) {
  const uint16x4_t dc_dup = vdup_lane_u16(dc, 0);
  int i;
  for (i = 0; i < 4; ++i, dst += stride) {
    vst1_u16(dst, dc_dup);
  }
}

void vpx_highbd_dc_predictor_4x4_neon(uint16_t *dst, ptrdiff_t stride,
                                      const uint16_t *above,
                                      const uint16_t *left, int bd) {
  const uint16x4_t a = vld1_u16(above);
  const uint16x4_t l = vld1_u16(left);
  uint16x4_t sum;
  uint16x4_t dc;
  (void)bd;
  sum = vadd_u16(a, l);
  sum = vpadd_u16(sum, sum);
  sum = vpadd_u16(sum, sum);
  dc = vrshr_n_u16(sum, 3);
  dc_store_4x4(dst, stride, dc);
}

void vpx_highbd_dc_left_predictor_4x4_neon(uint16_t *dst, ptrdiff_t stride,
                                           const uint16_t *above,
                                           const uint16_t *left, int bd) {
  const uint16x4_t sum = dc_sum_4(left);
  const uint16x4_t dc = vrshr_n_u16(sum, 2);
  (void)above;
  (void)bd;
  dc_store_4x4(dst, stride, dc);
}

void vpx_highbd_dc_top_predictor_4x4_neon(uint16_t *dst, ptrdiff_t stride,
                                          const uint16_t *above,
                                          const uint16_t *left, int bd) {
  const uint16x4_t sum = dc_sum_4(above);
  const uint16x4_t dc = vrshr_n_u16(sum, 2);
  (void)left;
  (void)bd;
  dc_store_4x4(dst, stride, dc);
}

void vpx_highbd_dc_128_predictor_4x4_neon(uint16_t *dst, ptrdiff_t stride,
                                          const uint16_t *above,
                                          const uint16_t *left, int bd) {
  const uint16x4_t dc = vdup_n_u16(1 << (bd - 1));
  (void)above;
  (void)left;
  dc_store_4x4(dst, stride, dc);
}

//------------------------------------------------------------------------------
// DC 8x8

static INLINE uint16x4_t dc_sum_8(const uint16_t *ref) {
  const uint16x8_t ref_u16 = vld1q_u16(ref);
  uint16x4_t sum = vadd_u16(vget_low_u16(ref_u16), vget_high_u16(ref_u16));
  sum = vpadd_u16(sum, sum);
  return vpadd_u16(sum, sum);
}

static INLINE void dc_store_8x8(uint16_t *dst, ptrdiff_t stride,
                                const uint16x4_t dc) {
  const uint16x8_t dc_dup = vdupq_lane_u16(dc, 0);
  int i;
  for (i = 0; i < 8; ++i, dst += stride) {
    vst1q_u16(dst, dc_dup);
  }
}

void vpx_highbd_dc_predictor_8x8_neon(uint16_t *dst, ptrdiff_t stride,
                                      const uint16_t *above,
                                      const uint16_t *left, int bd) {
  const uint16x8_t above_u16 = vld1q_u16(above);
  const uint16x8_t left_u16 = vld1q_u16(left);
  const uint16x8_t p0 = vaddq_u16(above_u16, left_u16);
  uint16x4_t sum = vadd_u16(vget_low_u16(p0), vget_high_u16(p0));
  uint16x4_t dc;
  (void)bd;
  sum = vpadd_u16(sum, sum);
  sum = vpadd_u16(sum, sum);
  dc = vrshr_n_u16(sum, 4);
  dc_store_8x8(dst, stride, dc);
}

void vpx_highbd_dc_left_predictor_8x8_neon(uint16_t *dst, ptrdiff_t stride,
                                           const uint16_t *above,
                                           const uint16_t *left, int bd) {
  const uint16x4_t sum = dc_sum_8(left);
  const uint16x4_t dc = vrshr_n_u16(sum, 3);
  (void)above;
  (void)bd;
  dc_store_8x8(dst, stride, dc);
}

void vpx_highbd_dc_top_predictor_8x8_neon(uint16_t *dst, ptrdiff_t stride,
                                          const uint16_t *above,
                                          const uint16_t *left, int bd) {
  const uint16x4_t sum = dc_sum_8(above);
  const uint16x4_t dc = vrshr_n_u16(sum, 3);
  (void)left;
  (void)bd;
  dc_store_8x8(dst, stride, dc);
}

void vpx_highbd_dc_128_predictor_8x8_neon(uint16_t *dst, ptrdiff_t stride,
                                          const uint16_t *above,
                                          const uint16_t *left, int bd) {
  const uint16x4_t dc = vdup_n_u16(1 << (bd - 1));
  (void)above;
  (void)left;
  dc_store_8x8(dst, stride, dc);
}

//------------------------------------------------------------------------------
// DC 16x16

static INLINE uint16x4_t dc_sum_16(const uint16_t *ref) {
  const uint16x8x2_t ref_u16 = vld2q_u16(ref);
  const uint16x8_t p0 = vaddq_u16(ref_u16.val[0], ref_u16.val[1]);
  uint16x4_t sum = vadd_u16(vget_low_u16(p0), vget_high_u16(p0));
  sum = vpadd_u16(sum, sum);
  return vpadd_u16(sum, sum);
}

static INLINE void dc_store_16x16(uint16_t *dst, ptrdiff_t stride,
                                  const uint16x4_t dc) {
  uint16x8x2_t dc_dup;
  int i;
  dc_dup.val[0] = dc_dup.val[1] = vdupq_lane_u16(dc, 0);
  for (i = 0; i < 16; ++i, dst += stride) {
    vst2q_u16(dst, dc_dup);
  }
}

void vpx_highbd_dc_predictor_16x16_neon(uint16_t *dst, ptrdiff_t stride,
                                        const uint16_t *above,
                                        const uint16_t *left, int bd) {
  const uint16x8x2_t a = vld2q_u16(above);
  const uint16x8x2_t l = vld2q_u16(left);
  const uint16x8_t pa = vaddq_u16(a.val[0], a.val[1]);
  const uint16x8_t pl = vaddq_u16(l.val[0], l.val[1]);
  const uint16x8_t pal0 = vaddq_u16(pa, pl);
  uint16x4_t pal1 = vadd_u16(vget_low_u16(pal0), vget_high_u16(pal0));
  uint32x2_t sum;
  uint16x4_t dc;
  (void)bd;
  pal1 = vpadd_u16(pal1, pal1);
  sum = vpaddl_u16(pal1);
  dc = vreinterpret_u16_u32(vrshr_n_u32(sum, 5));
  dc_store_16x16(dst, stride, dc);
}

void vpx_highbd_dc_left_predictor_16x16_neon(uint16_t *dst, ptrdiff_t stride,
                                             const uint16_t *above,
                                             const uint16_t *left, int bd) {
  const uint16x4_t sum = dc_sum_16(left);
  const uint16x4_t dc = vrshr_n_u16(sum, 4);
  (void)above;
  (void)bd;
  dc_store_16x16(dst, stride, dc);
}

void vpx_highbd_dc_top_predictor_16x16_neon(uint16_t *dst, ptrdiff_t stride,
                                            const uint16_t *above,
                                            const uint16_t *left, int bd) {
  const uint16x4_t sum = dc_sum_16(above);
  const uint16x4_t dc = vrshr_n_u16(sum, 4);
  (void)left;
  (void)bd;
  dc_store_16x16(dst, stride, dc);
}

void vpx_highbd_dc_128_predictor_16x16_neon(uint16_t *dst, ptrdiff_t stride,
                                            const uint16_t *above,
                                            const uint16_t *left, int bd) {
  const uint16x4_t dc = vdup_n_u16(1 << (bd - 1));
  (void)above;
  (void)left;
  dc_store_16x16(dst, stride, dc);
}

//------------------------------------------------------------------------------
// DC 32x32

static INLINE uint32x2_t dc_sum_32(const uint16_t *ref) {
  const uint16x8x4_t r = vld4q_u16(ref);
  const uint16x8_t p0 = vaddq_u16(r.val[0], r.val[1]);
  const uint16x8_t p1 = vaddq_u16(r.val[2], r.val[3]);
  const uint16x8_t p2 = vaddq_u16(p0, p1);
  uint16x4_t sum = vadd_u16(vget_low_u16(p2), vget_high_u16(p2));
  sum = vpadd_u16(sum, sum);
  return vpaddl_u16(sum);
}

static INLINE void dc_store_32x32(uint16_t *dst, ptrdiff_t stride,
                                  const uint16x4_t dc) {
  uint16x8x2_t dc_dup;
  int i;
  dc_dup.val[0] = dc_dup.val[1] = vdupq_lane_u16(dc, 0);

  for (i = 0; i < 32; ++i) {
    vst2q_u16(dst, dc_dup);
    dst += 16;
    vst2q_u16(dst, dc_dup);
    dst += stride - 16;
  }
}

void vpx_highbd_dc_predictor_32x32_neon(uint16_t *dst, ptrdiff_t stride,
                                        const uint16_t *above,
                                        const uint16_t *left, int bd) {
  const uint16x8x4_t a = vld4q_u16(above);
  const uint16x8x4_t l = vld4q_u16(left);
  const uint16x8_t pa0 = vaddq_u16(a.val[0], a.val[1]);
  const uint16x8_t pa1 = vaddq_u16(a.val[2], a.val[3]);
  const uint16x8_t pl0 = vaddq_u16(l.val[0], l.val[1]);
  const uint16x8_t pl1 = vaddq_u16(l.val[2], l.val[3]);
  const uint16x8_t pa = vaddq_u16(pa0, pa1);
  const uint16x8_t pl = vaddq_u16(pl0, pl1);
  const uint16x8_t pal0 = vaddq_u16(pa, pl);
  const uint16x4_t pal1 = vadd_u16(vget_low_u16(pal0), vget_high_u16(pal0));
  uint32x2_t sum = vpaddl_u16(pal1);
  uint16x4_t dc;
  (void)bd;
  sum = vpadd_u32(sum, sum);
  dc = vreinterpret_u16_u32(vrshr_n_u32(sum, 6));
  dc_store_32x32(dst, stride, dc);
}

void vpx_highbd_dc_left_predictor_32x32_neon(uint16_t *dst, ptrdiff_t stride,
                                             const uint16_t *above,
                                             const uint16_t *left, int bd) {
  const uint32x2_t sum = dc_sum_32(left);
  const uint16x4_t dc = vreinterpret_u16_u32(vrshr_n_u32(sum, 5));
  (void)above;
  (void)bd;
  dc_store_32x32(dst, stride, dc);
}

void vpx_highbd_dc_top_predictor_32x32_neon(uint16_t *dst, ptrdiff_t stride,
                                            const uint16_t *above,
                                            const uint16_t *left, int bd) {
  const uint32x2_t sum = dc_sum_32(above);
  const uint16x4_t dc = vreinterpret_u16_u32(vrshr_n_u32(sum, 5));
  (void)left;
  (void)bd;
  dc_store_32x32(dst, stride, dc);
}

void vpx_highbd_dc_128_predictor_32x32_neon(uint16_t *dst, ptrdiff_t stride,
                                            const uint16_t *above,
                                            const uint16_t *left, int bd) {
  const uint16x4_t dc = vdup_n_u16(1 << (bd - 1));
  (void)above;
  (void)left;
  dc_store_32x32(dst, stride, dc);
}

// -----------------------------------------------------------------------------

void vpx_highbd_d45_predictor_4x4_neon(uint16_t *dst, ptrdiff_t stride,
                                       const uint16_t *above,
                                       const uint16_t *left, int bd) {
  const uint16x8_t ABCDEFGH = vld1q_u16(above);
  const uint16x8_t BCDEFGH0 = vld1q_u16(above + 1);
  const uint16x8_t CDEFGH00 = vld1q_u16(above + 2);
  const uint16x8_t avg1 = vhaddq_u16(ABCDEFGH, CDEFGH00);
  const uint16x8_t avg2 = vrhaddq_u16(avg1, BCDEFGH0);
  const uint16x4_t avg2_low = vget_low_u16(avg2);
  const uint16x4_t avg2_high = vget_high_u16(avg2);
  const uint16x4_t r1 = vext_u16(avg2_low, avg2_high, 1);
  const uint16x4_t r2 = vext_u16(avg2_low, avg2_high, 2);
  const uint16x4_t r3 = vext_u16(avg2_low, avg2_high, 3);
  (void)left;
  (void)bd;
  vst1_u16(dst, avg2_low);
  dst += stride;
  vst1_u16(dst, r1);
  dst += stride;
  vst1_u16(dst, r2);
  dst += stride;
  vst1_u16(dst, r3);
  vst1q_lane_u16(dst + 3, ABCDEFGH, 7);
}

static INLINE void d45_store_8(uint16_t **dst, const ptrdiff_t stride,
                               const uint16x8_t above_right, uint16x8_t *row) {
  *row = vextq_u16(*row, above_right, 1);
  vst1q_u16(*dst, *row);
  *dst += stride;
}

void vpx_highbd_d45_predictor_8x8_neon(uint16_t *dst, ptrdiff_t stride,
                                       const uint16_t *above,
                                       const uint16_t *left, int bd) {
  const uint16x8_t A0 = vld1q_u16(above);
  const uint16x8_t above_right = vdupq_lane_u16(vget_high_u16(A0), 3);
  const uint16x8_t A1 = vld1q_u16(above + 1);
  const uint16x8_t A2 = vld1q_u16(above + 2);
  const uint16x8_t avg1 = vhaddq_u16(A0, A2);
  uint16x8_t row = vrhaddq_u16(avg1, A1);
  (void)left;
  (void)bd;

  vst1q_u16(dst, row);
  dst += stride;
  d45_store_8(&dst, stride, above_right, &row);
  d45_store_8(&dst, stride, above_right, &row);
  d45_store_8(&dst, stride, above_right, &row);
  d45_store_8(&dst, stride, above_right, &row);
  d45_store_8(&dst, stride, above_right, &row);
  d45_store_8(&dst, stride, above_right, &row);
  vst1q_u16(dst, above_right);
}

static INLINE void d45_store_16(uint16_t **dst, const ptrdiff_t stride,
                                const uint16x8_t above_right, uint16x8_t *row_0,
                                uint16x8_t *row_1) {
  *row_0 = vextq_u16(*row_0, *row_1, 1);
  *row_1 = vextq_u16(*row_1, above_right, 1);
  vst1q_u16(*dst, *row_0);
  *dst += 8;
  vst1q_u16(*dst, *row_1);
  *dst += stride - 8;
}

void vpx_highbd_d45_predictor_16x16_neon(uint16_t *dst, ptrdiff_t stride,
                                         const uint16_t *above,
                                         const uint16_t *left, int bd) {
  const uint16x8_t A0_0 = vld1q_u16(above);
  const uint16x8_t A0_1 = vld1q_u16(above + 8);
  const uint16x8_t above_right = vdupq_lane_u16(vget_high_u16(A0_1), 3);
  const uint16x8_t A1_0 = vld1q_u16(above + 1);
  const uint16x8_t A1_1 = vld1q_u16(above + 9);
  const uint16x8_t A2_0 = vld1q_u16(above + 2);
  const uint16x8_t A2_1 = vld1q_u16(above + 10);
  const uint16x8_t avg_0 = vhaddq_u16(A0_0, A2_0);
  const uint16x8_t avg_1 = vhaddq_u16(A0_1, A2_1);
  uint16x8_t row_0 = vrhaddq_u16(avg_0, A1_0);
  uint16x8_t row_1 = vrhaddq_u16(avg_1, A1_1);
  (void)left;
  (void)bd;

  vst1q_u16(dst, row_0);
  vst1q_u16(dst + 8, row_1);
  dst += stride;
  d45_store_16(&dst, stride, above_right, &row_0, &row_1);
  d45_store_16(&dst, stride, above_right, &row_0, &row_1);
  d45_store_16(&dst, stride, above_right, &row_0, &row_1);
  d45_store_16(&dst, stride, above_right, &row_0, &row_1);
  d45_store_16(&dst, stride, above_right, &row_0, &row_1);
  d45_store_16(&dst, stride, above_right, &row_0, &row_1);
  d45_store_16(&dst, stride, above_right, &row_0, &row_1);
  d45_store_16(&dst, stride, above_right, &row_0, &row_1);
  d45_store_16(&dst, stride, above_right, &row_0, &row_1);
  d45_store_16(&dst, stride, above_right, &row_0, &row_1);
  d45_store_16(&dst, stride, above_right, &row_0, &row_1);
  d45_store_16(&dst, stride, above_right, &row_0, &row_1);
  d45_store_16(&dst, stride, above_right, &row_0, &row_1);
  d45_store_16(&dst, stride, above_right, &row_0, &row_1);
  vst1q_u16(dst, above_right);
  vst1q_u16(dst + 8, above_right);
}

void vpx_highbd_d45_predictor_32x32_neon(uint16_t *dst, ptrdiff_t stride,
                                         const uint16_t *above,
                                         const uint16_t *left, int bd) {
  const uint16x8_t A0_0 = vld1q_u16(above);
  const uint16x8_t A0_1 = vld1q_u16(above + 8);
  const uint16x8_t A0_2 = vld1q_u16(above + 16);
  const uint16x8_t A0_3 = vld1q_u16(above + 24);
  const uint16x8_t above_right = vdupq_lane_u16(vget_high_u16(A0_3), 3);
  const uint16x8_t A1_0 = vld1q_u16(above + 1);
  const uint16x8_t A1_1 = vld1q_u16(above + 9);
  const uint16x8_t A1_2 = vld1q_u16(above + 17);
  const uint16x8_t A1_3 = vld1q_u16(above + 25);
  const uint16x8_t A2_0 = vld1q_u16(above + 2);
  const uint16x8_t A2_1 = vld1q_u16(above + 10);
  const uint16x8_t A2_2 = vld1q_u16(above + 18);
  const uint16x8_t A2_3 = vld1q_u16(above + 26);
  const uint16x8_t avg_0 = vhaddq_u16(A0_0, A2_0);
  const uint16x8_t avg_1 = vhaddq_u16(A0_1, A2_1);
  const uint16x8_t avg_2 = vhaddq_u16(A0_2, A2_2);
  const uint16x8_t avg_3 = vhaddq_u16(A0_3, A2_3);
  uint16x8_t row_0 = vrhaddq_u16(avg_0, A1_0);
  uint16x8_t row_1 = vrhaddq_u16(avg_1, A1_1);
  uint16x8_t row_2 = vrhaddq_u16(avg_2, A1_2);
  uint16x8_t row_3 = vrhaddq_u16(avg_3, A1_3);
  int i;
  (void)left;
  (void)bd;

  vst1q_u16(dst, row_0);
  dst += 8;
  vst1q_u16(dst, row_1);
  dst += 8;
  vst1q_u16(dst, row_2);
  dst += 8;
  vst1q_u16(dst, row_3);
  dst += stride - 24;

  for (i = 0; i < 30; ++i) {
    row_0 = vextq_u16(row_0, row_1, 1);
    row_1 = vextq_u16(row_1, row_2, 1);
    row_2 = vextq_u16(row_2, row_3, 1);
    row_3 = vextq_u16(row_3, above_right, 1);
    vst1q_u16(dst, row_0);
    dst += 8;
    vst1q_u16(dst, row_1);
    dst += 8;
    vst1q_u16(dst, row_2);
    dst += 8;
    vst1q_u16(dst, row_3);
    dst += stride - 24;
  }

  vst1q_u16(dst, above_right);
  dst += 8;
  vst1q_u16(dst, above_right);
  dst += 8;
  vst1q_u16(dst, above_right);
  dst += 8;
  vst1q_u16(dst, above_right);
}

// -----------------------------------------------------------------------------

void vpx_highbd_d135_predictor_4x4_neon(uint16_t *dst, ptrdiff_t stride,
                                        const uint16_t *above,
                                        const uint16_t *left, int bd) {
  const uint16x8_t XA0123___ = vld1q_u16(above - 1);
  const uint16x4_t L0123 = vld1_u16(left);
  const uint16x4_t L3210 = vrev64_u16(L0123);
  const uint16x8_t L____3210 = vcombine_u16(L0123, L3210);
  const uint16x8_t L3210XA012 = vcombine_u16(L3210, vget_low_u16(XA0123___));
  const uint16x8_t L210XA0123 = vextq_u16(L____3210, XA0123___, 5);
  const uint16x8_t L10XA0123_ = vextq_u16(L____3210, XA0123___, 6);
  const uint16x8_t avg1 = vhaddq_u16(L3210XA012, L10XA0123_);
  const uint16x8_t avg2 = vrhaddq_u16(avg1, L210XA0123);
  const uint16x4_t row_0 = vget_low_u16(avg2);
  const uint16x4_t row_1 = vget_high_u16(avg2);
  const uint16x4_t r0 = vext_u16(row_0, row_1, 3);
  const uint16x4_t r1 = vext_u16(row_0, row_1, 2);
  const uint16x4_t r2 = vext_u16(row_0, row_1, 1);
  (void)bd;
  vst1_u16(dst, r0);
  dst += stride;
  vst1_u16(dst, r1);
  dst += stride;
  vst1_u16(dst, r2);
  dst += stride;
  vst1_u16(dst, row_0);
}

void vpx_highbd_d135_predictor_8x8_neon(uint16_t *dst, ptrdiff_t stride,
                                        const uint16_t *above,
                                        const uint16_t *left, int bd) {
  const uint16x8_t XA0123456 = vld1q_u16(above - 1);
  const uint16x8_t A01234567 = vld1q_u16(above);
  const uint16x8_t A1234567_ = vld1q_u16(above + 1);
  const uint16x8_t L01234567 = vld1q_u16(left);
  const uint16x4_t L3210 = vrev64_u16(vget_low_u16(L01234567));
  const uint16x4_t L7654 = vrev64_u16(vget_high_u16(L01234567));
  const uint16x8_t L76543210 = vcombine_u16(L7654, L3210);
  const uint16x8_t L6543210X = vextq_u16(L76543210, XA0123456, 1);
  const uint16x8_t L543210XA0 = vextq_u16(L76543210, XA0123456, 2);
  const uint16x8_t avg_0 = vhaddq_u16(L76543210, L543210XA0);
  const uint16x8_t avg_1 = vhaddq_u16(XA0123456, A1234567_);
  const uint16x8_t row_0 = vrhaddq_u16(avg_0, L6543210X);
  const uint16x8_t row_1 = vrhaddq_u16(avg_1, A01234567);
  const uint16x8_t r0 = vextq_u16(row_0, row_1, 7);
  const uint16x8_t r1 = vextq_u16(row_0, row_1, 6);
  const uint16x8_t r2 = vextq_u16(row_0, row_1, 5);
  const uint16x8_t r3 = vextq_u16(row_0, row_1, 4);
  const uint16x8_t r4 = vextq_u16(row_0, row_1, 3);
  const uint16x8_t r5 = vextq_u16(row_0, row_1, 2);
  const uint16x8_t r6 = vextq_u16(row_0, row_1, 1);
  (void)bd;
  vst1q_u16(dst, r0);
  dst += stride;
  vst1q_u16(dst, r1);
  dst += stride;
  vst1q_u16(dst, r2);
  dst += stride;
  vst1q_u16(dst, r3);
  dst += stride;
  vst1q_u16(dst, r4);
  dst += stride;
  vst1q_u16(dst, r5);
  dst += stride;
  vst1q_u16(dst, r6);
  dst += stride;
  vst1q_u16(dst, row_0);
}

static INLINE void d135_store_16(uint16_t **dst, const ptrdiff_t stride,
                                 const uint16x8_t row_0,
                                 const uint16x8_t row_1) {
  vst1q_u16(*dst, row_0);
  *dst += 8;
  vst1q_u16(*dst, row_1);
  *dst += stride - 8;
}

void vpx_highbd_d135_predictor_16x16_neon(uint16_t *dst, ptrdiff_t stride,
                                          const uint16_t *above,
                                          const uint16_t *left, int bd) {
  const uint16x8_t L01234567 = vld1q_u16(left);
  const uint16x8_t L89abcdef = vld1q_u16(left + 8);
  const uint16x4_t L3210 = vrev64_u16(vget_low_u16(L01234567));
  const uint16x4_t L7654 = vrev64_u16(vget_high_u16(L01234567));
  const uint16x4_t Lba98 = vrev64_u16(vget_low_u16(L89abcdef));
  const uint16x4_t Lfedc = vrev64_u16(vget_high_u16(L89abcdef));
  const uint16x8_t L76543210 = vcombine_u16(L7654, L3210);
  const uint16x8_t Lfedcba98 = vcombine_u16(Lfedc, Lba98);
  const uint16x8_t Ledcba987 = vextq_u16(Lfedcba98, L76543210, 1);
  const uint16x8_t Ldcba9876 = vextq_u16(Lfedcba98, L76543210, 2);
  const uint16x8_t avg_0 = vhaddq_u16(Lfedcba98, Ldcba9876);
  const uint16x8_t row_0 = vrhaddq_u16(avg_0, Ledcba987);

  const uint16x8_t XA0123456 = vld1q_u16(above - 1);
  const uint16x8_t L6543210X = vextq_u16(L76543210, XA0123456, 1);
  const uint16x8_t L543210XA0 = vextq_u16(L76543210, XA0123456, 2);
  const uint16x8_t avg_1 = vhaddq_u16(L76543210, L543210XA0);
  const uint16x8_t row_1 = vrhaddq_u16(avg_1, L6543210X);

  const uint16x8_t A01234567 = vld1q_u16(above);
  const uint16x8_t A12345678 = vld1q_u16(above + 1);
  const uint16x8_t avg_2 = vhaddq_u16(XA0123456, A12345678);
  const uint16x8_t row_2 = vrhaddq_u16(avg_2, A01234567);

  const uint16x8_t A789abcde = vld1q_u16(above + 7);
  const uint16x8_t A89abcdef = vld1q_u16(above + 8);
  const uint16x8_t A9abcdef_ = vld1q_u16(above + 9);
  const uint16x8_t avg_3 = vhaddq_u16(A789abcde, A9abcdef_);
  const uint16x8_t row_3 = vrhaddq_u16(avg_3, A89abcdef);

  const uint16x8_t r0_0 = vextq_u16(row_1, row_2, 7);
  const uint16x8_t r0_1 = vextq_u16(row_2, row_3, 7);
  const uint16x8_t r1_0 = vextq_u16(row_1, row_2, 6);
  const uint16x8_t r1_1 = vextq_u16(row_2, row_3, 6);
  const uint16x8_t r2_0 = vextq_u16(row_1, row_2, 5);
  const uint16x8_t r2_1 = vextq_u16(row_2, row_3, 5);
  const uint16x8_t r3_0 = vextq_u16(row_1, row_2, 4);
  const uint16x8_t r3_1 = vextq_u16(row_2, row_3, 4);
  const uint16x8_t r4_0 = vextq_u16(row_1, row_2, 3);
  const uint16x8_t r4_1 = vextq_u16(row_2, row_3, 3);
  const uint16x8_t r5_0 = vextq_u16(row_1, row_2, 2);
  const uint16x8_t r5_1 = vextq_u16(row_2, row_3, 2);
  const uint16x8_t r6_0 = vextq_u16(row_1, row_2, 1);
  const uint16x8_t r6_1 = vextq_u16(row_2, row_3, 1);
  const uint16x8_t r8_0 = vextq_u16(row_0, row_1, 7);
  const uint16x8_t r9_0 = vextq_u16(row_0, row_1, 6);
  const uint16x8_t ra_0 = vextq_u16(row_0, row_1, 5);
  const uint16x8_t rb_0 = vextq_u16(row_0, row_1, 4);
  const uint16x8_t rc_0 = vextq_u16(row_0, row_1, 3);
  const uint16x8_t rd_0 = vextq_u16(row_0, row_1, 2);
  const uint16x8_t re_0 = vextq_u16(row_0, row_1, 1);
  (void)bd;

  d135_store_16(&dst, stride, r0_0, r0_1);
  d135_store_16(&dst, stride, r1_0, r1_1);
  d135_store_16(&dst, stride, r2_0, r2_1);
  d135_store_16(&dst, stride, r3_0, r3_1);
  d135_store_16(&dst, stride, r4_0, r4_1);
  d135_store_16(&dst, stride, r5_0, r5_1);
  d135_store_16(&dst, stride, r6_0, r6_1);
  d135_store_16(&dst, stride, row_1, row_2);
  d135_store_16(&dst, stride, r8_0, r0_0);
  d135_store_16(&dst, stride, r9_0, r1_0);
  d135_store_16(&dst, stride, ra_0, r2_0);
  d135_store_16(&dst, stride, rb_0, r3_0);
  d135_store_16(&dst, stride, rc_0, r4_0);
  d135_store_16(&dst, stride, rd_0, r5_0);
  d135_store_16(&dst, stride, re_0, r6_0);
  vst1q_u16(dst, row_0);
  dst += 8;
  vst1q_u16(dst, row_1);
}

void vpx_highbd_d135_predictor_32x32_neon(uint16_t *dst, ptrdiff_t stride,
                                          const uint16_t *above,
                                          const uint16_t *left, int bd) {
  const uint16x8_t LL01234567 = vld1q_u16(left + 16);
  const uint16x8_t LL89abcdef = vld1q_u16(left + 24);
  const uint16x4_t LL3210 = vrev64_u16(vget_low_u16(LL01234567));
  const uint16x4_t LL7654 = vrev64_u16(vget_high_u16(LL01234567));
  const uint16x4_t LLba98 = vrev64_u16(vget_low_u16(LL89abcdef));
  const uint16x4_t LLfedc = vrev64_u16(vget_high_u16(LL89abcdef));
  const uint16x8_t LL76543210 = vcombine_u16(LL7654, LL3210);
  const uint16x8_t LLfedcba98 = vcombine_u16(LLfedc, LLba98);
  const uint16x8_t LLedcba987 = vextq_u16(LLfedcba98, LL76543210, 1);
  const uint16x8_t LLdcba9876 = vextq_u16(LLfedcba98, LL76543210, 2);
  const uint16x8_t avg_0 = vhaddq_u16(LLfedcba98, LLdcba9876);
  uint16x8_t row_0 = vrhaddq_u16(avg_0, LLedcba987);

  const uint16x8_t LU01234567 = vld1q_u16(left);
  const uint16x8_t LU89abcdef = vld1q_u16(left + 8);
  const uint16x4_t LU3210 = vrev64_u16(vget_low_u16(LU01234567));
  const uint16x4_t LU7654 = vrev64_u16(vget_high_u16(LU01234567));
  const uint16x4_t LUba98 = vrev64_u16(vget_low_u16(LU89abcdef));
  const uint16x4_t LUfedc = vrev64_u16(vget_high_u16(LU89abcdef));
  const uint16x8_t LU76543210 = vcombine_u16(LU7654, LU3210);
  const uint16x8_t LUfedcba98 = vcombine_u16(LUfedc, LUba98);
  const uint16x8_t LL6543210Uf = vextq_u16(LL76543210, LUfedcba98, 1);
  const uint16x8_t LL543210Ufe = vextq_u16(LL76543210, LUfedcba98, 2);
  const uint16x8_t avg_1 = vhaddq_u16(LL76543210, LL543210Ufe);
  uint16x8_t row_1 = vrhaddq_u16(avg_1, LL6543210Uf);

  const uint16x8_t LUedcba987 = vextq_u16(LUfedcba98, LU76543210, 1);
  const uint16x8_t LUdcba9876 = vextq_u16(LUfedcba98, LU76543210, 2);
  const uint16x8_t avg_2 = vhaddq_u16(LUfedcba98, LUdcba9876);
  uint16x8_t row_2 = vrhaddq_u16(avg_2, LUedcba987);

  const uint16x8_t XAL0123456 = vld1q_u16(above - 1);
  const uint16x8_t LU6543210X = vextq_u16(LU76543210, XAL0123456, 1);
  const uint16x8_t LU543210XA0 = vextq_u16(LU76543210, XAL0123456, 2);
  const uint16x8_t avg_3 = vhaddq_u16(LU76543210, LU543210XA0);
  uint16x8_t row_3 = vrhaddq_u16(avg_3, LU6543210X);

  const uint16x8_t AL01234567 = vld1q_u16(above);
  const uint16x8_t AL12345678 = vld1q_u16(above + 1);
  const uint16x8_t avg_4 = vhaddq_u16(XAL0123456, AL12345678);
  uint16x8_t row_4 = vrhaddq_u16(avg_4, AL01234567);

  const uint16x8_t AL789abcde = vld1q_u16(above + 7);
  const uint16x8_t AL89abcdef = vld1q_u16(above + 8);
  const uint16x8_t AL9abcdefg = vld1q_u16(above + 9);
  const uint16x8_t avg_5 = vhaddq_u16(AL789abcde, AL9abcdefg);
  uint16x8_t row_5 = vrhaddq_u16(avg_5, AL89abcdef);

  const uint16x8_t ALfR0123456 = vld1q_u16(above + 15);
  const uint16x8_t AR01234567 = vld1q_u16(above + 16);
  const uint16x8_t AR12345678 = vld1q_u16(above + 17);
  const uint16x8_t avg_6 = vhaddq_u16(ALfR0123456, AR12345678);
  uint16x8_t row_6 = vrhaddq_u16(avg_6, AR01234567);

  const uint16x8_t AR789abcde = vld1q_u16(above + 23);
  const uint16x8_t AR89abcdef = vld1q_u16(above + 24);
  const uint16x8_t AR9abcdef_ = vld1q_u16(above + 25);
  const uint16x8_t avg_7 = vhaddq_u16(AR789abcde, AR9abcdef_);
  uint16x8_t row_7 = vrhaddq_u16(avg_7, AR89abcdef);
  int i, j;
  (void)bd;

  dst += 31 * stride;
  for (i = 0; i < 4; ++i) {
    for (j = 0; j < 8; ++j) {
      vst1q_u16(dst, row_0);
      dst += 8;
      vst1q_u16(dst, row_1);
      dst += 8;
      vst1q_u16(dst, row_2);
      dst += 8;
      vst1q_u16(dst, row_3);
      dst -= stride + 24;
      row_0 = vextq_u16(row_0, row_1, 1);
      row_1 = vextq_u16(row_1, row_2, 1);
      row_2 = vextq_u16(row_2, row_3, 1);
      row_3 = vextq_u16(row_3, row_4, 1);
      row_4 = vextq_u16(row_4, row_4, 1);
    }
    row_4 = row_5;
    row_5 = row_6;
    row_6 = row_7;
  }
}

//------------------------------------------------------------------------------

void vpx_highbd_v_predictor_4x4_neon(uint16_t *dst, ptrdiff_t stride,
                                     const uint16_t *above,
                                     const uint16_t *left, int bd) {
  const uint16x4_t row = vld1_u16(above);
  int i;
  (void)left;
  (void)bd;

  for (i = 0; i < 4; i++, dst += stride) {
    vst1_u16(dst, row);
  }
}

void vpx_highbd_v_predictor_8x8_neon(uint16_t *dst, ptrdiff_t stride,
                                     const uint16_t *above,
                                     const uint16_t *left, int bd) {
  const uint16x8_t row = vld1q_u16(above);
  int i;
  (void)left;
  (void)bd;

  for (i = 0; i < 8; i++, dst += stride) {
    vst1q_u16(dst, row);
  }
}

void vpx_highbd_v_predictor_16x16_neon(uint16_t *dst, ptrdiff_t stride,
                                       const uint16_t *above,
                                       const uint16_t *left, int bd) {
  const uint16x8x2_t row = vld2q_u16(above);
  int i;
  (void)left;
  (void)bd;

  for (i = 0; i < 16; i++, dst += stride) {
    vst2q_u16(dst, row);
  }
}

void vpx_highbd_v_predictor_32x32_neon(uint16_t *dst, ptrdiff_t stride,
                                       const uint16_t *above,
                                       const uint16_t *left, int bd) {
  const uint16x8x2_t row0 = vld2q_u16(above);
  const uint16x8x2_t row1 = vld2q_u16(above + 16);
  int i;
  (void)left;
  (void)bd;

  for (i = 0; i < 32; i++) {
    vst2q_u16(dst, row0);
    dst += 16;
    vst2q_u16(dst, row1);
    dst += stride - 16;
  }
}

// -----------------------------------------------------------------------------

void vpx_highbd_h_predictor_4x4_neon(uint16_t *dst, ptrdiff_t stride,
                                     const uint16_t *above,
                                     const uint16_t *left, int bd) {
  const uint16x4_t left_u16 = vld1_u16(left);
  uint16x4_t row;
  (void)above;
  (void)bd;

  row = vdup_lane_u16(left_u16, 0);
  vst1_u16(dst, row);
  dst += stride;
  row = vdup_lane_u16(left_u16, 1);
  vst1_u16(dst, row);
  dst += stride;
  row = vdup_lane_u16(left_u16, 2);
  vst1_u16(dst, row);
  dst += stride;
  row = vdup_lane_u16(left_u16, 3);
  vst1_u16(dst, row);
}

void vpx_highbd_h_predictor_8x8_neon(uint16_t *dst, ptrdiff_t stride,
                                     const uint16_t *above,
                                     const uint16_t *left, int bd) {
  const uint16x8_t left_u16 = vld1q_u16(left);
  const uint16x4_t left_low = vget_low_u16(left_u16);
  const uint16x4_t left_high = vget_high_u16(left_u16);
  uint16x8_t row;
  (void)above;
  (void)bd;

  row = vdupq_lane_u16(left_low, 0);
  vst1q_u16(dst, row);
  dst += stride;
  row = vdupq_lane_u16(left_low, 1);
  vst1q_u16(dst, row);
  dst += stride;
  row = vdupq_lane_u16(left_low, 2);
  vst1q_u16(dst, row);
  dst += stride;
  row = vdupq_lane_u16(left_low, 3);
  vst1q_u16(dst, row);
  dst += stride;
  row = vdupq_lane_u16(left_high, 0);
  vst1q_u16(dst, row);
  dst += stride;
  row = vdupq_lane_u16(left_high, 1);
  vst1q_u16(dst, row);
  dst += stride;
  row = vdupq_lane_u16(left_high, 2);
  vst1q_u16(dst, row);
  dst += stride;
  row = vdupq_lane_u16(left_high, 3);
  vst1q_u16(dst, row);
}

static INLINE void h_store_16(uint16_t **dst, const ptrdiff_t stride,
                              const uint16x8_t row) {
  // Note: vst1q is faster than vst2q
  vst1q_u16(*dst, row);
  *dst += 8;
  vst1q_u16(*dst, row);
  *dst += stride - 8;
}

void vpx_highbd_h_predictor_16x16_neon(uint16_t *dst, ptrdiff_t stride,
                                       const uint16_t *above,
                                       const uint16_t *left, int bd) {
  int i;
  (void)above;
  (void)bd;

  for (i = 0; i < 2; i++, left += 8) {
    const uint16x8_t left_u16q = vld1q_u16(left);
    const uint16x4_t left_low = vget_low_u16(left_u16q);
    const uint16x4_t left_high = vget_high_u16(left_u16q);
    uint16x8_t row;

    row = vdupq_lane_u16(left_low, 0);
    h_store_16(&dst, stride, row);
    row = vdupq_lane_u16(left_low, 1);
    h_store_16(&dst, stride, row);
    row = vdupq_lane_u16(left_low, 2);
    h_store_16(&dst, stride, row);
    row = vdupq_lane_u16(left_low, 3);
    h_store_16(&dst, stride, row);
    row = vdupq_lane_u16(left_high, 0);
    h_store_16(&dst, stride, row);
    row = vdupq_lane_u16(left_high, 1);
    h_store_16(&dst, stride, row);
    row = vdupq_lane_u16(left_high, 2);
    h_store_16(&dst, stride, row);
    row = vdupq_lane_u16(left_high, 3);
    h_store_16(&dst, stride, row);
  }
}

static INLINE void h_store_32(uint16_t **dst, const ptrdiff_t stride,
                              const uint16x8_t row) {
  // Note: vst1q is faster than vst2q
  vst1q_u16(*dst, row);
  *dst += 8;
  vst1q_u16(*dst, row);
  *dst += 8;
  vst1q_u16(*dst, row);
  *dst += 8;
  vst1q_u16(*dst, row);
  *dst += stride - 24;
}

void vpx_highbd_h_predictor_32x32_neon(uint16_t *dst, ptrdiff_t stride,
                                       const uint16_t *above,
                                       const uint16_t *left, int bd) {
  int i;
  (void)above;
  (void)bd;

  for (i = 0; i < 4; i++, left += 8) {
    const uint16x8_t left_u16q = vld1q_u16(left);
    const uint16x4_t left_low = vget_low_u16(left_u16q);
    const uint16x4_t left_high = vget_high_u16(left_u16q);
    uint16x8_t row;

    row = vdupq_lane_u16(left_low, 0);
    h_store_32(&dst, stride, row);
    row = vdupq_lane_u16(left_low, 1);
    h_store_32(&dst, stride, row);
    row = vdupq_lane_u16(left_low, 2);
    h_store_32(&dst, stride, row);
    row = vdupq_lane_u16(left_low, 3);
    h_store_32(&dst, stride, row);
    row = vdupq_lane_u16(left_high, 0);
    h_store_32(&dst, stride, row);
    row = vdupq_lane_u16(left_high, 1);
    h_store_32(&dst, stride, row);
    row = vdupq_lane_u16(left_high, 2);
    h_store_32(&dst, stride, row);
    row = vdupq_lane_u16(left_high, 3);
    h_store_32(&dst, stride, row);
  }
}

// -----------------------------------------------------------------------------

void vpx_highbd_tm_predictor_4x4_neon(uint16_t *dst, ptrdiff_t stride,
                                      const uint16_t *above,
                                      const uint16_t *left, int bd) {
  const int16x8_t max = vmovq_n_s16((1 << bd) - 1);
  const int16x8_t top_left = vld1q_dup_s16((const int16_t *)(above - 1));
  const int16x4_t above_s16d = vld1_s16((const int16_t *)above);
  const int16x8_t above_s16 = vcombine_s16(above_s16d, above_s16d);
  const int16x4_t left_s16 = vld1_s16((const int16_t *)left);
  const int16x8_t sub = vsubq_s16(above_s16, top_left);
  int16x8_t sum;
  uint16x8_t row;

  sum = vcombine_s16(vdup_lane_s16(left_s16, 0), vdup_lane_s16(left_s16, 1));
  sum = vaddq_s16(sum, sub);
  sum = vminq_s16(sum, max);
  row = vqshluq_n_s16(sum, 0);
  vst1_u16(dst, vget_low_u16(row));
  dst += stride;
  vst1_u16(dst, vget_high_u16(row));
  dst += stride;

  sum = vcombine_s16(vdup_lane_s16(left_s16, 2), vdup_lane_s16(left_s16, 3));
  sum = vaddq_s16(sum, sub);
  sum = vminq_s16(sum, max);
  row = vqshluq_n_s16(sum, 0);
  vst1_u16(dst, vget_low_u16(row));
  dst += stride;
  vst1_u16(dst, vget_high_u16(row));
}

static INLINE void tm_8_kernel(uint16_t **dst, const ptrdiff_t stride,
                               const int16x8_t left_dup, const int16x8_t sub,
                               const int16x8_t max) {
  uint16x8_t row;
  int16x8_t sum = vaddq_s16(left_dup, sub);
  sum = vminq_s16(sum, max);
  row = vqshluq_n_s16(sum, 0);
  vst1q_u16(*dst, row);
  *dst += stride;
}

void vpx_highbd_tm_predictor_8x8_neon(uint16_t *dst, ptrdiff_t stride,
                                      const uint16_t *above,
                                      const uint16_t *left, int bd) {
  const int16x8_t max = vmovq_n_s16((1 << bd) - 1);
  const int16x8_t top_left = vld1q_dup_s16((const int16_t *)(above - 1));
  const int16x8_t above_s16 = vld1q_s16((const int16_t *)above);
  const int16x8_t left_s16 = vld1q_s16((const int16_t *)left);
  const int16x8_t sub = vsubq_s16(above_s16, top_left);
  int16x4_t left_s16d;
  int16x8_t left_dup;
  int i;

  left_s16d = vget_low_s16(left_s16);

  for (i = 0; i < 2; i++, left_s16d = vget_high_s16(left_s16)) {
    left_dup = vdupq_lane_s16(left_s16d, 0);
    tm_8_kernel(&dst, stride, left_dup, sub, max);

    left_dup = vdupq_lane_s16(left_s16d, 1);
    tm_8_kernel(&dst, stride, left_dup, sub, max);

    left_dup = vdupq_lane_s16(left_s16d, 2);
    tm_8_kernel(&dst, stride, left_dup, sub, max);

    left_dup = vdupq_lane_s16(left_s16d, 3);
    tm_8_kernel(&dst, stride, left_dup, sub, max);
  }
}

static INLINE void tm_16_kernel(uint16_t **dst, const ptrdiff_t stride,
                                const int16x8_t left_dup, const int16x8_t sub0,
                                const int16x8_t sub1, const int16x8_t max) {
  uint16x8_t row0, row1;
  int16x8_t sum0 = vaddq_s16(left_dup, sub0);
  int16x8_t sum1 = vaddq_s16(left_dup, sub1);
  sum0 = vminq_s16(sum0, max);
  sum1 = vminq_s16(sum1, max);
  row0 = vqshluq_n_s16(sum0, 0);
  row1 = vqshluq_n_s16(sum1, 0);
  vst1q_u16(*dst, row0);
  *dst += 8;
  vst1q_u16(*dst, row1);
  *dst += stride - 8;
}

void vpx_highbd_tm_predictor_16x16_neon(uint16_t *dst, ptrdiff_t stride,
                                        const uint16_t *above,
                                        const uint16_t *left, int bd) {
  const int16x8_t max = vmovq_n_s16((1 << bd) - 1);
  const int16x8_t top_left = vld1q_dup_s16((const int16_t *)(above - 1));
  const int16x8_t above0 = vld1q_s16((const int16_t *)above);
  const int16x8_t above1 = vld1q_s16((const int16_t *)(above + 8));
  const int16x8_t sub0 = vsubq_s16(above0, top_left);
  const int16x8_t sub1 = vsubq_s16(above1, top_left);
  int16x8_t left_dup;
  int i, j;

  for (j = 0; j < 2; j++, left += 8) {
    const int16x8_t left_s16q = vld1q_s16((const int16_t *)left);
    int16x4_t left_s16d = vget_low_s16(left_s16q);
    for (i = 0; i < 2; i++, left_s16d = vget_high_s16(left_s16q)) {
      left_dup = vdupq_lane_s16(left_s16d, 0);
      tm_16_kernel(&dst, stride, left_dup, sub0, sub1, max);

      left_dup = vdupq_lane_s16(left_s16d, 1);
      tm_16_kernel(&dst, stride, left_dup, sub0, sub1, max);

      left_dup = vdupq_lane_s16(left_s16d, 2);
      tm_16_kernel(&dst, stride, left_dup, sub0, sub1, max);

      left_dup = vdupq_lane_s16(left_s16d, 3);
      tm_16_kernel(&dst, stride, left_dup, sub0, sub1, max);
    }
  }
}

static INLINE void tm_32_kernel(uint16_t **dst, const ptrdiff_t stride,
                                const int16x8_t left_dup, const int16x8_t sub0,
                                const int16x8_t sub1, const int16x8_t sub2,
                                const int16x8_t sub3, const int16x8_t max) {
  uint16x8_t row0, row1, row2, row3;
  int16x8_t sum0 = vaddq_s16(left_dup, sub0);
  int16x8_t sum1 = vaddq_s16(left_dup, sub1);
  int16x8_t sum2 = vaddq_s16(left_dup, sub2);
  int16x8_t sum3 = vaddq_s16(left_dup, sub3);
  sum0 = vminq_s16(sum0, max);
  sum1 = vminq_s16(sum1, max);
  sum2 = vminq_s16(sum2, max);
  sum3 = vminq_s16(sum3, max);
  row0 = vqshluq_n_s16(sum0, 0);
  row1 = vqshluq_n_s16(sum1, 0);
  row2 = vqshluq_n_s16(sum2, 0);
  row3 = vqshluq_n_s16(sum3, 0);
  vst1q_u16(*dst, row0);
  *dst += 8;
  vst1q_u16(*dst, row1);
  *dst += 8;
  vst1q_u16(*dst, row2);
  *dst += 8;
  vst1q_u16(*dst, row3);
  *dst += stride - 24;
}

void vpx_highbd_tm_predictor_32x32_neon(uint16_t *dst, ptrdiff_t stride,
                                        const uint16_t *above,
                                        const uint16_t *left, int bd) {
  const int16x8_t max = vmovq_n_s16((1 << bd) - 1);
  const int16x8_t top_left = vld1q_dup_s16((const int16_t *)(above - 1));
  const int16x8_t above0 = vld1q_s16((const int16_t *)above);
  const int16x8_t above1 = vld1q_s16((const int16_t *)(above + 8));
  const int16x8_t above2 = vld1q_s16((const int16_t *)(above + 16));
  const int16x8_t above3 = vld1q_s16((const int16_t *)(above + 24));
  const int16x8_t sub0 = vsubq_s16(above0, top_left);
  const int16x8_t sub1 = vsubq_s16(above1, top_left);
  const int16x8_t sub2 = vsubq_s16(above2, top_left);
  const int16x8_t sub3 = vsubq_s16(above3, top_left);
  int16x8_t left_dup;
  int i, j;

  for (i = 0; i < 4; i++, left += 8) {
    const int16x8_t left_s16q = vld1q_s16((const int16_t *)left);
    int16x4_t left_s16d = vget_low_s16(left_s16q);
    for (j = 0; j < 2; j++, left_s16d = vget_high_s16(left_s16q)) {
      left_dup = vdupq_lane_s16(left_s16d, 0);
      tm_32_kernel(&dst, stride, left_dup, sub0, sub1, sub2, sub3, max);

      left_dup = vdupq_lane_s16(left_s16d, 1);
      tm_32_kernel(&dst, stride, left_dup, sub0, sub1, sub2, sub3, max);

      left_dup = vdupq_lane_s16(left_s16d, 2);
      tm_32_kernel(&dst, stride, left_dup, sub0, sub1, sub2, sub3, max);

      left_dup = vdupq_lane_s16(left_s16d, 3);
      tm_32_kernel(&dst, stride, left_dup, sub0, sub1, sub2, sub3, max);
    }
  }
}
