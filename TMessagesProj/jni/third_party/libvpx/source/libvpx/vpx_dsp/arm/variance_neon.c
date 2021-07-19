/*
 *  Copyright (c) 2014 The WebM project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <arm_neon.h>
#include <assert.h>

#include "./vpx_dsp_rtcd.h"
#include "./vpx_config.h"

#include "vpx/vpx_integer.h"
#include "vpx_dsp/arm/mem_neon.h"
#include "vpx_dsp/arm/sum_neon.h"
#include "vpx_ports/mem.h"

// The variance helper functions use int16_t for sum. 8 values are accumulated
// and then added (at which point they expand up to int32_t). To avoid overflow,
// there can be no more than 32767 / 255 ~= 128 values accumulated in each
// column. For a 32x32 buffer, this results in 32 / 8 = 4 values per row * 32
// rows = 128. Asserts have been added to each function to warn against reaching
// this limit.

// Process a block of width 4 four rows at a time.
static void variance_neon_w4x4(const uint8_t *src_ptr, int src_stride,
                               const uint8_t *ref_ptr, int ref_stride, int h,
                               uint32_t *sse, int *sum) {
  int i;
  int16x8_t sum_s16 = vdupq_n_s16(0);
  int32x4_t sse_lo_s32 = vdupq_n_s32(0);
  int32x4_t sse_hi_s32 = vdupq_n_s32(0);

  // Since width is only 4, sum_s16 only loads a half row per loop.
  assert(h <= 256);

  for (i = 0; i < h; i += 4) {
    const uint8x16_t a_u8 = load_unaligned_u8q(src_ptr, src_stride);
    const uint8x16_t b_u8 = load_unaligned_u8q(ref_ptr, ref_stride);
    const uint16x8_t diff_lo_u16 =
        vsubl_u8(vget_low_u8(a_u8), vget_low_u8(b_u8));
    const uint16x8_t diff_hi_u16 =
        vsubl_u8(vget_high_u8(a_u8), vget_high_u8(b_u8));

    const int16x8_t diff_lo_s16 = vreinterpretq_s16_u16(diff_lo_u16);
    const int16x8_t diff_hi_s16 = vreinterpretq_s16_u16(diff_hi_u16);

    sum_s16 = vaddq_s16(sum_s16, diff_lo_s16);
    sum_s16 = vaddq_s16(sum_s16, diff_hi_s16);

    sse_lo_s32 = vmlal_s16(sse_lo_s32, vget_low_s16(diff_lo_s16),
                           vget_low_s16(diff_lo_s16));
    sse_lo_s32 = vmlal_s16(sse_lo_s32, vget_high_s16(diff_lo_s16),
                           vget_high_s16(diff_lo_s16));

    sse_hi_s32 = vmlal_s16(sse_hi_s32, vget_low_s16(diff_hi_s16),
                           vget_low_s16(diff_hi_s16));
    sse_hi_s32 = vmlal_s16(sse_hi_s32, vget_high_s16(diff_hi_s16),
                           vget_high_s16(diff_hi_s16));

    src_ptr += 4 * src_stride;
    ref_ptr += 4 * ref_stride;
  }

  *sum = vget_lane_s32(horizontal_add_int16x8(sum_s16), 0);
  *sse = vget_lane_u32(horizontal_add_uint32x4(vreinterpretq_u32_s32(
                           vaddq_s32(sse_lo_s32, sse_hi_s32))),
                       0);
}

// Process a block of any size where the width is divisible by 16.
static void variance_neon_w16(const uint8_t *src_ptr, int src_stride,
                              const uint8_t *ref_ptr, int ref_stride, int w,
                              int h, uint32_t *sse, int *sum) {
  int i, j;
  int16x8_t sum_s16 = vdupq_n_s16(0);
  int32x4_t sse_lo_s32 = vdupq_n_s32(0);
  int32x4_t sse_hi_s32 = vdupq_n_s32(0);

  // The loop loads 16 values at a time but doubles them up when accumulating
  // into sum_s16.
  assert(w / 8 * h <= 128);

  for (i = 0; i < h; ++i) {
    for (j = 0; j < w; j += 16) {
      const uint8x16_t a_u8 = vld1q_u8(src_ptr + j);
      const uint8x16_t b_u8 = vld1q_u8(ref_ptr + j);

      const uint16x8_t diff_lo_u16 =
          vsubl_u8(vget_low_u8(a_u8), vget_low_u8(b_u8));
      const uint16x8_t diff_hi_u16 =
          vsubl_u8(vget_high_u8(a_u8), vget_high_u8(b_u8));

      const int16x8_t diff_lo_s16 = vreinterpretq_s16_u16(diff_lo_u16);
      const int16x8_t diff_hi_s16 = vreinterpretq_s16_u16(diff_hi_u16);

      sum_s16 = vaddq_s16(sum_s16, diff_lo_s16);
      sum_s16 = vaddq_s16(sum_s16, diff_hi_s16);

      sse_lo_s32 = vmlal_s16(sse_lo_s32, vget_low_s16(diff_lo_s16),
                             vget_low_s16(diff_lo_s16));
      sse_lo_s32 = vmlal_s16(sse_lo_s32, vget_high_s16(diff_lo_s16),
                             vget_high_s16(diff_lo_s16));

      sse_hi_s32 = vmlal_s16(sse_hi_s32, vget_low_s16(diff_hi_s16),
                             vget_low_s16(diff_hi_s16));
      sse_hi_s32 = vmlal_s16(sse_hi_s32, vget_high_s16(diff_hi_s16),
                             vget_high_s16(diff_hi_s16));
    }
    src_ptr += src_stride;
    ref_ptr += ref_stride;
  }

  *sum = vget_lane_s32(horizontal_add_int16x8(sum_s16), 0);
  *sse = vget_lane_u32(horizontal_add_uint32x4(vreinterpretq_u32_s32(
                           vaddq_s32(sse_lo_s32, sse_hi_s32))),
                       0);
}

// Process a block of width 8 two rows at a time.
static void variance_neon_w8x2(const uint8_t *src_ptr, int src_stride,
                               const uint8_t *ref_ptr, int ref_stride, int h,
                               uint32_t *sse, int *sum) {
  int i = 0;
  int16x8_t sum_s16 = vdupq_n_s16(0);
  int32x4_t sse_lo_s32 = vdupq_n_s32(0);
  int32x4_t sse_hi_s32 = vdupq_n_s32(0);

  // Each column has it's own accumulator entry in sum_s16.
  assert(h <= 128);

  do {
    const uint8x8_t a_0_u8 = vld1_u8(src_ptr);
    const uint8x8_t a_1_u8 = vld1_u8(src_ptr + src_stride);
    const uint8x8_t b_0_u8 = vld1_u8(ref_ptr);
    const uint8x8_t b_1_u8 = vld1_u8(ref_ptr + ref_stride);
    const uint16x8_t diff_0_u16 = vsubl_u8(a_0_u8, b_0_u8);
    const uint16x8_t diff_1_u16 = vsubl_u8(a_1_u8, b_1_u8);
    const int16x8_t diff_0_s16 = vreinterpretq_s16_u16(diff_0_u16);
    const int16x8_t diff_1_s16 = vreinterpretq_s16_u16(diff_1_u16);
    sum_s16 = vaddq_s16(sum_s16, diff_0_s16);
    sum_s16 = vaddq_s16(sum_s16, diff_1_s16);
    sse_lo_s32 = vmlal_s16(sse_lo_s32, vget_low_s16(diff_0_s16),
                           vget_low_s16(diff_0_s16));
    sse_lo_s32 = vmlal_s16(sse_lo_s32, vget_low_s16(diff_1_s16),
                           vget_low_s16(diff_1_s16));
    sse_hi_s32 = vmlal_s16(sse_hi_s32, vget_high_s16(diff_0_s16),
                           vget_high_s16(diff_0_s16));
    sse_hi_s32 = vmlal_s16(sse_hi_s32, vget_high_s16(diff_1_s16),
                           vget_high_s16(diff_1_s16));
    src_ptr += src_stride + src_stride;
    ref_ptr += ref_stride + ref_stride;
    i += 2;
  } while (i < h);

  *sum = vget_lane_s32(horizontal_add_int16x8(sum_s16), 0);
  *sse = vget_lane_u32(horizontal_add_uint32x4(vreinterpretq_u32_s32(
                           vaddq_s32(sse_lo_s32, sse_hi_s32))),
                       0);
}

void vpx_get8x8var_neon(const uint8_t *src_ptr, int src_stride,
                        const uint8_t *ref_ptr, int ref_stride,
                        unsigned int *sse, int *sum) {
  variance_neon_w8x2(src_ptr, src_stride, ref_ptr, ref_stride, 8, sse, sum);
}

void vpx_get16x16var_neon(const uint8_t *src_ptr, int src_stride,
                          const uint8_t *ref_ptr, int ref_stride,
                          unsigned int *sse, int *sum) {
  variance_neon_w16(src_ptr, src_stride, ref_ptr, ref_stride, 16, 16, sse, sum);
}

#define varianceNxM(n, m, shift)                                             \
  unsigned int vpx_variance##n##x##m##_neon(                                 \
      const uint8_t *src_ptr, int src_stride, const uint8_t *ref_ptr,        \
      int ref_stride, unsigned int *sse) {                                   \
    int sum;                                                                 \
    if (n == 4)                                                              \
      variance_neon_w4x4(src_ptr, src_stride, ref_ptr, ref_stride, m, sse,   \
                         &sum);                                              \
    else if (n == 8)                                                         \
      variance_neon_w8x2(src_ptr, src_stride, ref_ptr, ref_stride, m, sse,   \
                         &sum);                                              \
    else                                                                     \
      variance_neon_w16(src_ptr, src_stride, ref_ptr, ref_stride, n, m, sse, \
                        &sum);                                               \
    if (n * m < 16 * 16)                                                     \
      return *sse - ((sum * sum) >> shift);                                  \
    else                                                                     \
      return *sse - (uint32_t)(((int64_t)sum * sum) >> shift);               \
  }

varianceNxM(4, 4, 4);
varianceNxM(4, 8, 5);
varianceNxM(8, 4, 5);
varianceNxM(8, 8, 6);
varianceNxM(8, 16, 7);
varianceNxM(16, 8, 7);
varianceNxM(16, 16, 8);
varianceNxM(16, 32, 9);
varianceNxM(32, 16, 9);
varianceNxM(32, 32, 10);

unsigned int vpx_variance32x64_neon(const uint8_t *src_ptr, int src_stride,
                                    const uint8_t *ref_ptr, int ref_stride,
                                    unsigned int *sse) {
  int sum1, sum2;
  uint32_t sse1, sse2;
  variance_neon_w16(src_ptr, src_stride, ref_ptr, ref_stride, 32, 32, &sse1,
                    &sum1);
  variance_neon_w16(src_ptr + (32 * src_stride), src_stride,
                    ref_ptr + (32 * ref_stride), ref_stride, 32, 32, &sse2,
                    &sum2);
  *sse = sse1 + sse2;
  sum1 += sum2;
  return *sse - (unsigned int)(((int64_t)sum1 * sum1) >> 11);
}

unsigned int vpx_variance64x32_neon(const uint8_t *src_ptr, int src_stride,
                                    const uint8_t *ref_ptr, int ref_stride,
                                    unsigned int *sse) {
  int sum1, sum2;
  uint32_t sse1, sse2;
  variance_neon_w16(src_ptr, src_stride, ref_ptr, ref_stride, 64, 16, &sse1,
                    &sum1);
  variance_neon_w16(src_ptr + (16 * src_stride), src_stride,
                    ref_ptr + (16 * ref_stride), ref_stride, 64, 16, &sse2,
                    &sum2);
  *sse = sse1 + sse2;
  sum1 += sum2;
  return *sse - (unsigned int)(((int64_t)sum1 * sum1) >> 11);
}

unsigned int vpx_variance64x64_neon(const uint8_t *src_ptr, int src_stride,
                                    const uint8_t *ref_ptr, int ref_stride,
                                    unsigned int *sse) {
  int sum1, sum2;
  uint32_t sse1, sse2;

  variance_neon_w16(src_ptr, src_stride, ref_ptr, ref_stride, 64, 16, &sse1,
                    &sum1);
  variance_neon_w16(src_ptr + (16 * src_stride), src_stride,
                    ref_ptr + (16 * ref_stride), ref_stride, 64, 16, &sse2,
                    &sum2);
  sse1 += sse2;
  sum1 += sum2;

  variance_neon_w16(src_ptr + (16 * 2 * src_stride), src_stride,
                    ref_ptr + (16 * 2 * ref_stride), ref_stride, 64, 16, &sse2,
                    &sum2);
  sse1 += sse2;
  sum1 += sum2;

  variance_neon_w16(src_ptr + (16 * 3 * src_stride), src_stride,
                    ref_ptr + (16 * 3 * ref_stride), ref_stride, 64, 16, &sse2,
                    &sum2);
  *sse = sse1 + sse2;
  sum1 += sum2;
  return *sse - (unsigned int)(((int64_t)sum1 * sum1) >> 12);
}

unsigned int vpx_mse16x16_neon(const unsigned char *src_ptr, int src_stride,
                               const unsigned char *ref_ptr, int ref_stride,
                               unsigned int *sse) {
  int i;
  int16x4_t d22s16, d23s16, d24s16, d25s16, d26s16, d27s16, d28s16, d29s16;
  int64x1_t d0s64;
  uint8x16_t q0u8, q1u8, q2u8, q3u8;
  int32x4_t q7s32, q8s32, q9s32, q10s32;
  uint16x8_t q11u16, q12u16, q13u16, q14u16;
  int64x2_t q1s64;

  q7s32 = vdupq_n_s32(0);
  q8s32 = vdupq_n_s32(0);
  q9s32 = vdupq_n_s32(0);
  q10s32 = vdupq_n_s32(0);

  for (i = 0; i < 8; i++) {  // mse16x16_neon_loop
    q0u8 = vld1q_u8(src_ptr);
    src_ptr += src_stride;
    q1u8 = vld1q_u8(src_ptr);
    src_ptr += src_stride;
    q2u8 = vld1q_u8(ref_ptr);
    ref_ptr += ref_stride;
    q3u8 = vld1q_u8(ref_ptr);
    ref_ptr += ref_stride;

    q11u16 = vsubl_u8(vget_low_u8(q0u8), vget_low_u8(q2u8));
    q12u16 = vsubl_u8(vget_high_u8(q0u8), vget_high_u8(q2u8));
    q13u16 = vsubl_u8(vget_low_u8(q1u8), vget_low_u8(q3u8));
    q14u16 = vsubl_u8(vget_high_u8(q1u8), vget_high_u8(q3u8));

    d22s16 = vreinterpret_s16_u16(vget_low_u16(q11u16));
    d23s16 = vreinterpret_s16_u16(vget_high_u16(q11u16));
    q7s32 = vmlal_s16(q7s32, d22s16, d22s16);
    q8s32 = vmlal_s16(q8s32, d23s16, d23s16);

    d24s16 = vreinterpret_s16_u16(vget_low_u16(q12u16));
    d25s16 = vreinterpret_s16_u16(vget_high_u16(q12u16));
    q9s32 = vmlal_s16(q9s32, d24s16, d24s16);
    q10s32 = vmlal_s16(q10s32, d25s16, d25s16);

    d26s16 = vreinterpret_s16_u16(vget_low_u16(q13u16));
    d27s16 = vreinterpret_s16_u16(vget_high_u16(q13u16));
    q7s32 = vmlal_s16(q7s32, d26s16, d26s16);
    q8s32 = vmlal_s16(q8s32, d27s16, d27s16);

    d28s16 = vreinterpret_s16_u16(vget_low_u16(q14u16));
    d29s16 = vreinterpret_s16_u16(vget_high_u16(q14u16));
    q9s32 = vmlal_s16(q9s32, d28s16, d28s16);
    q10s32 = vmlal_s16(q10s32, d29s16, d29s16);
  }

  q7s32 = vaddq_s32(q7s32, q8s32);
  q9s32 = vaddq_s32(q9s32, q10s32);
  q10s32 = vaddq_s32(q7s32, q9s32);

  q1s64 = vpaddlq_s32(q10s32);
  d0s64 = vadd_s64(vget_low_s64(q1s64), vget_high_s64(q1s64));

  vst1_lane_u32((uint32_t *)sse, vreinterpret_u32_s64(d0s64), 0);
  return vget_lane_u32(vreinterpret_u32_s64(d0s64), 0);
}

unsigned int vpx_get4x4sse_cs_neon(const unsigned char *src_ptr, int src_stride,
                                   const unsigned char *ref_ptr,
                                   int ref_stride) {
  int16x4_t d22s16, d24s16, d26s16, d28s16;
  int64x1_t d0s64;
  uint8x8_t d0u8, d1u8, d2u8, d3u8, d4u8, d5u8, d6u8, d7u8;
  int32x4_t q7s32, q8s32, q9s32, q10s32;
  uint16x8_t q11u16, q12u16, q13u16, q14u16;
  int64x2_t q1s64;

  d0u8 = vld1_u8(src_ptr);
  src_ptr += src_stride;
  d4u8 = vld1_u8(ref_ptr);
  ref_ptr += ref_stride;
  d1u8 = vld1_u8(src_ptr);
  src_ptr += src_stride;
  d5u8 = vld1_u8(ref_ptr);
  ref_ptr += ref_stride;
  d2u8 = vld1_u8(src_ptr);
  src_ptr += src_stride;
  d6u8 = vld1_u8(ref_ptr);
  ref_ptr += ref_stride;
  d3u8 = vld1_u8(src_ptr);
  src_ptr += src_stride;
  d7u8 = vld1_u8(ref_ptr);
  ref_ptr += ref_stride;

  q11u16 = vsubl_u8(d0u8, d4u8);
  q12u16 = vsubl_u8(d1u8, d5u8);
  q13u16 = vsubl_u8(d2u8, d6u8);
  q14u16 = vsubl_u8(d3u8, d7u8);

  d22s16 = vget_low_s16(vreinterpretq_s16_u16(q11u16));
  d24s16 = vget_low_s16(vreinterpretq_s16_u16(q12u16));
  d26s16 = vget_low_s16(vreinterpretq_s16_u16(q13u16));
  d28s16 = vget_low_s16(vreinterpretq_s16_u16(q14u16));

  q7s32 = vmull_s16(d22s16, d22s16);
  q8s32 = vmull_s16(d24s16, d24s16);
  q9s32 = vmull_s16(d26s16, d26s16);
  q10s32 = vmull_s16(d28s16, d28s16);

  q7s32 = vaddq_s32(q7s32, q8s32);
  q9s32 = vaddq_s32(q9s32, q10s32);
  q9s32 = vaddq_s32(q7s32, q9s32);

  q1s64 = vpaddlq_s32(q9s32);
  d0s64 = vadd_s64(vget_low_s64(q1s64), vget_high_s64(q1s64));

  return vget_lane_u32(vreinterpret_u32_s64(d0s64), 0);
}
