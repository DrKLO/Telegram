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

#include "./vpx_config.h"
#include "./vpx_dsp_rtcd.h"

#include "vpx/vpx_integer.h"
#include "vpx_dsp/arm/mem_neon.h"
#include "vpx_dsp/arm/sum_neon.h"

uint32_t vpx_sad4x4_neon(const uint8_t *src_ptr, int src_stride,
                         const uint8_t *ref_ptr, int ref_stride) {
  const uint8x16_t src_u8 = load_unaligned_u8q(src_ptr, src_stride);
  const uint8x16_t ref_u8 = load_unaligned_u8q(ref_ptr, ref_stride);
  uint16x8_t abs = vabdl_u8(vget_low_u8(src_u8), vget_low_u8(ref_u8));
  abs = vabal_u8(abs, vget_high_u8(src_u8), vget_high_u8(ref_u8));
  return vget_lane_u32(horizontal_add_uint16x8(abs), 0);
}

uint32_t vpx_sad4x4_avg_neon(const uint8_t *src_ptr, int src_stride,
                             const uint8_t *ref_ptr, int ref_stride,
                             const uint8_t *second_pred) {
  const uint8x16_t src_u8 = load_unaligned_u8q(src_ptr, src_stride);
  const uint8x16_t ref_u8 = load_unaligned_u8q(ref_ptr, ref_stride);
  const uint8x16_t second_pred_u8 = vld1q_u8(second_pred);
  const uint8x16_t avg = vrhaddq_u8(ref_u8, second_pred_u8);
  uint16x8_t abs = vabdl_u8(vget_low_u8(src_u8), vget_low_u8(avg));
  abs = vabal_u8(abs, vget_high_u8(src_u8), vget_high_u8(avg));
  return vget_lane_u32(horizontal_add_uint16x8(abs), 0);
}

uint32_t vpx_sad4x8_neon(const uint8_t *src_ptr, int src_stride,
                         const uint8_t *ref_ptr, int ref_stride) {
  int i;
  uint16x8_t abs = vdupq_n_u16(0);
  for (i = 0; i < 8; i += 4) {
    const uint8x16_t src_u8 = load_unaligned_u8q(src_ptr, src_stride);
    const uint8x16_t ref_u8 = load_unaligned_u8q(ref_ptr, ref_stride);
    src_ptr += 4 * src_stride;
    ref_ptr += 4 * ref_stride;
    abs = vabal_u8(abs, vget_low_u8(src_u8), vget_low_u8(ref_u8));
    abs = vabal_u8(abs, vget_high_u8(src_u8), vget_high_u8(ref_u8));
  }

  return vget_lane_u32(horizontal_add_uint16x8(abs), 0);
}

uint32_t vpx_sad4x8_avg_neon(const uint8_t *src_ptr, int src_stride,
                             const uint8_t *ref_ptr, int ref_stride,
                             const uint8_t *second_pred) {
  int i;
  uint16x8_t abs = vdupq_n_u16(0);
  for (i = 0; i < 8; i += 4) {
    const uint8x16_t src_u8 = load_unaligned_u8q(src_ptr, src_stride);
    const uint8x16_t ref_u8 = load_unaligned_u8q(ref_ptr, ref_stride);
    const uint8x16_t second_pred_u8 = vld1q_u8(second_pred);
    const uint8x16_t avg = vrhaddq_u8(ref_u8, second_pred_u8);
    src_ptr += 4 * src_stride;
    ref_ptr += 4 * ref_stride;
    second_pred += 16;
    abs = vabal_u8(abs, vget_low_u8(src_u8), vget_low_u8(avg));
    abs = vabal_u8(abs, vget_high_u8(src_u8), vget_high_u8(avg));
  }

  return vget_lane_u32(horizontal_add_uint16x8(abs), 0);
}

static INLINE uint16x8_t sad8x(const uint8_t *src_ptr, int src_stride,
                               const uint8_t *ref_ptr, int ref_stride,
                               const int height) {
  int i;
  uint16x8_t abs = vdupq_n_u16(0);

  for (i = 0; i < height; ++i) {
    const uint8x8_t a_u8 = vld1_u8(src_ptr);
    const uint8x8_t b_u8 = vld1_u8(ref_ptr);
    src_ptr += src_stride;
    ref_ptr += ref_stride;
    abs = vabal_u8(abs, a_u8, b_u8);
  }
  return abs;
}

static INLINE uint16x8_t sad8x_avg(const uint8_t *src_ptr, int src_stride,
                                   const uint8_t *ref_ptr, int ref_stride,
                                   const uint8_t *second_pred,
                                   const int height) {
  int i;
  uint16x8_t abs = vdupq_n_u16(0);

  for (i = 0; i < height; ++i) {
    const uint8x8_t a_u8 = vld1_u8(src_ptr);
    const uint8x8_t b_u8 = vld1_u8(ref_ptr);
    const uint8x8_t c_u8 = vld1_u8(second_pred);
    const uint8x8_t avg = vrhadd_u8(b_u8, c_u8);
    src_ptr += src_stride;
    ref_ptr += ref_stride;
    second_pred += 8;
    abs = vabal_u8(abs, a_u8, avg);
  }
  return abs;
}

#define sad8xN(n)                                                              \
  uint32_t vpx_sad8x##n##_neon(const uint8_t *src_ptr, int src_stride,         \
                               const uint8_t *ref_ptr, int ref_stride) {       \
    const uint16x8_t abs = sad8x(src_ptr, src_stride, ref_ptr, ref_stride, n); \
    return vget_lane_u32(horizontal_add_uint16x8(abs), 0);                     \
  }                                                                            \
                                                                               \
  uint32_t vpx_sad8x##n##_avg_neon(const uint8_t *src_ptr, int src_stride,     \
                                   const uint8_t *ref_ptr, int ref_stride,     \
                                   const uint8_t *second_pred) {               \
    const uint16x8_t abs =                                                     \
        sad8x_avg(src_ptr, src_stride, ref_ptr, ref_stride, second_pred, n);   \
    return vget_lane_u32(horizontal_add_uint16x8(abs), 0);                     \
  }

sad8xN(4);
sad8xN(8);
sad8xN(16);

static INLINE uint16x8_t sad16x(const uint8_t *src_ptr, int src_stride,
                                const uint8_t *ref_ptr, int ref_stride,
                                const int height) {
  int i;
  uint16x8_t abs = vdupq_n_u16(0);

  for (i = 0; i < height; ++i) {
    const uint8x16_t a_u8 = vld1q_u8(src_ptr);
    const uint8x16_t b_u8 = vld1q_u8(ref_ptr);
    src_ptr += src_stride;
    ref_ptr += ref_stride;
    abs = vabal_u8(abs, vget_low_u8(a_u8), vget_low_u8(b_u8));
    abs = vabal_u8(abs, vget_high_u8(a_u8), vget_high_u8(b_u8));
  }
  return abs;
}

static INLINE uint16x8_t sad16x_avg(const uint8_t *src_ptr, int src_stride,
                                    const uint8_t *ref_ptr, int ref_stride,
                                    const uint8_t *second_pred,
                                    const int height) {
  int i;
  uint16x8_t abs = vdupq_n_u16(0);

  for (i = 0; i < height; ++i) {
    const uint8x16_t a_u8 = vld1q_u8(src_ptr);
    const uint8x16_t b_u8 = vld1q_u8(ref_ptr);
    const uint8x16_t c_u8 = vld1q_u8(second_pred);
    const uint8x16_t avg = vrhaddq_u8(b_u8, c_u8);
    src_ptr += src_stride;
    ref_ptr += ref_stride;
    second_pred += 16;
    abs = vabal_u8(abs, vget_low_u8(a_u8), vget_low_u8(avg));
    abs = vabal_u8(abs, vget_high_u8(a_u8), vget_high_u8(avg));
  }
  return abs;
}

#define sad16xN(n)                                                            \
  uint32_t vpx_sad16x##n##_neon(const uint8_t *src_ptr, int src_stride,       \
                                const uint8_t *ref_ptr, int ref_stride) {     \
    const uint16x8_t abs =                                                    \
        sad16x(src_ptr, src_stride, ref_ptr, ref_stride, n);                  \
    return vget_lane_u32(horizontal_add_uint16x8(abs), 0);                    \
  }                                                                           \
                                                                              \
  uint32_t vpx_sad16x##n##_avg_neon(const uint8_t *src_ptr, int src_stride,   \
                                    const uint8_t *ref_ptr, int ref_stride,   \
                                    const uint8_t *second_pred) {             \
    const uint16x8_t abs =                                                    \
        sad16x_avg(src_ptr, src_stride, ref_ptr, ref_stride, second_pred, n); \
    return vget_lane_u32(horizontal_add_uint16x8(abs), 0);                    \
  }

sad16xN(8);
sad16xN(16);
sad16xN(32);

static INLINE uint16x8_t sad32x(const uint8_t *src_ptr, int src_stride,
                                const uint8_t *ref_ptr, int ref_stride,
                                const int height) {
  int i;
  uint16x8_t abs = vdupq_n_u16(0);

  for (i = 0; i < height; ++i) {
    const uint8x16_t a_lo = vld1q_u8(src_ptr);
    const uint8x16_t a_hi = vld1q_u8(src_ptr + 16);
    const uint8x16_t b_lo = vld1q_u8(ref_ptr);
    const uint8x16_t b_hi = vld1q_u8(ref_ptr + 16);
    src_ptr += src_stride;
    ref_ptr += ref_stride;
    abs = vabal_u8(abs, vget_low_u8(a_lo), vget_low_u8(b_lo));
    abs = vabal_u8(abs, vget_high_u8(a_lo), vget_high_u8(b_lo));
    abs = vabal_u8(abs, vget_low_u8(a_hi), vget_low_u8(b_hi));
    abs = vabal_u8(abs, vget_high_u8(a_hi), vget_high_u8(b_hi));
  }
  return abs;
}

static INLINE uint16x8_t sad32x_avg(const uint8_t *src_ptr, int src_stride,
                                    const uint8_t *ref_ptr, int ref_stride,
                                    const uint8_t *second_pred,
                                    const int height) {
  int i;
  uint16x8_t abs = vdupq_n_u16(0);

  for (i = 0; i < height; ++i) {
    const uint8x16_t a_lo = vld1q_u8(src_ptr);
    const uint8x16_t a_hi = vld1q_u8(src_ptr + 16);
    const uint8x16_t b_lo = vld1q_u8(ref_ptr);
    const uint8x16_t b_hi = vld1q_u8(ref_ptr + 16);
    const uint8x16_t c_lo = vld1q_u8(second_pred);
    const uint8x16_t c_hi = vld1q_u8(second_pred + 16);
    const uint8x16_t avg_lo = vrhaddq_u8(b_lo, c_lo);
    const uint8x16_t avg_hi = vrhaddq_u8(b_hi, c_hi);
    src_ptr += src_stride;
    ref_ptr += ref_stride;
    second_pred += 32;
    abs = vabal_u8(abs, vget_low_u8(a_lo), vget_low_u8(avg_lo));
    abs = vabal_u8(abs, vget_high_u8(a_lo), vget_high_u8(avg_lo));
    abs = vabal_u8(abs, vget_low_u8(a_hi), vget_low_u8(avg_hi));
    abs = vabal_u8(abs, vget_high_u8(a_hi), vget_high_u8(avg_hi));
  }
  return abs;
}

#define sad32xN(n)                                                            \
  uint32_t vpx_sad32x##n##_neon(const uint8_t *src_ptr, int src_stride,       \
                                const uint8_t *ref_ptr, int ref_stride) {     \
    const uint16x8_t abs =                                                    \
        sad32x(src_ptr, src_stride, ref_ptr, ref_stride, n);                  \
    return vget_lane_u32(horizontal_add_uint16x8(abs), 0);                    \
  }                                                                           \
                                                                              \
  uint32_t vpx_sad32x##n##_avg_neon(const uint8_t *src_ptr, int src_stride,   \
                                    const uint8_t *ref_ptr, int ref_stride,   \
                                    const uint8_t *second_pred) {             \
    const uint16x8_t abs =                                                    \
        sad32x_avg(src_ptr, src_stride, ref_ptr, ref_stride, second_pred, n); \
    return vget_lane_u32(horizontal_add_uint16x8(abs), 0);                    \
  }

sad32xN(16);
sad32xN(32);
sad32xN(64);

static INLINE uint32x4_t sad64x(const uint8_t *src_ptr, int src_stride,
                                const uint8_t *ref_ptr, int ref_stride,
                                const int height) {
  int i;
  uint16x8_t abs_0 = vdupq_n_u16(0);
  uint16x8_t abs_1 = vdupq_n_u16(0);

  for (i = 0; i < height; ++i) {
    const uint8x16_t a_0 = vld1q_u8(src_ptr);
    const uint8x16_t a_1 = vld1q_u8(src_ptr + 16);
    const uint8x16_t a_2 = vld1q_u8(src_ptr + 32);
    const uint8x16_t a_3 = vld1q_u8(src_ptr + 48);
    const uint8x16_t b_0 = vld1q_u8(ref_ptr);
    const uint8x16_t b_1 = vld1q_u8(ref_ptr + 16);
    const uint8x16_t b_2 = vld1q_u8(ref_ptr + 32);
    const uint8x16_t b_3 = vld1q_u8(ref_ptr + 48);
    src_ptr += src_stride;
    ref_ptr += ref_stride;
    abs_0 = vabal_u8(abs_0, vget_low_u8(a_0), vget_low_u8(b_0));
    abs_0 = vabal_u8(abs_0, vget_high_u8(a_0), vget_high_u8(b_0));
    abs_0 = vabal_u8(abs_0, vget_low_u8(a_1), vget_low_u8(b_1));
    abs_0 = vabal_u8(abs_0, vget_high_u8(a_1), vget_high_u8(b_1));
    abs_1 = vabal_u8(abs_1, vget_low_u8(a_2), vget_low_u8(b_2));
    abs_1 = vabal_u8(abs_1, vget_high_u8(a_2), vget_high_u8(b_2));
    abs_1 = vabal_u8(abs_1, vget_low_u8(a_3), vget_low_u8(b_3));
    abs_1 = vabal_u8(abs_1, vget_high_u8(a_3), vget_high_u8(b_3));
  }

  {
    const uint32x4_t sum = vpaddlq_u16(abs_0);
    return vpadalq_u16(sum, abs_1);
  }
}

static INLINE uint32x4_t sad64x_avg(const uint8_t *src_ptr, int src_stride,
                                    const uint8_t *ref_ptr, int ref_stride,
                                    const uint8_t *second_pred,
                                    const int height) {
  int i;
  uint16x8_t abs_0 = vdupq_n_u16(0);
  uint16x8_t abs_1 = vdupq_n_u16(0);

  for (i = 0; i < height; ++i) {
    const uint8x16_t a_0 = vld1q_u8(src_ptr);
    const uint8x16_t a_1 = vld1q_u8(src_ptr + 16);
    const uint8x16_t a_2 = vld1q_u8(src_ptr + 32);
    const uint8x16_t a_3 = vld1q_u8(src_ptr + 48);
    const uint8x16_t b_0 = vld1q_u8(ref_ptr);
    const uint8x16_t b_1 = vld1q_u8(ref_ptr + 16);
    const uint8x16_t b_2 = vld1q_u8(ref_ptr + 32);
    const uint8x16_t b_3 = vld1q_u8(ref_ptr + 48);
    const uint8x16_t c_0 = vld1q_u8(second_pred);
    const uint8x16_t c_1 = vld1q_u8(second_pred + 16);
    const uint8x16_t c_2 = vld1q_u8(second_pred + 32);
    const uint8x16_t c_3 = vld1q_u8(second_pred + 48);
    const uint8x16_t avg_0 = vrhaddq_u8(b_0, c_0);
    const uint8x16_t avg_1 = vrhaddq_u8(b_1, c_1);
    const uint8x16_t avg_2 = vrhaddq_u8(b_2, c_2);
    const uint8x16_t avg_3 = vrhaddq_u8(b_3, c_3);
    src_ptr += src_stride;
    ref_ptr += ref_stride;
    second_pred += 64;
    abs_0 = vabal_u8(abs_0, vget_low_u8(a_0), vget_low_u8(avg_0));
    abs_0 = vabal_u8(abs_0, vget_high_u8(a_0), vget_high_u8(avg_0));
    abs_0 = vabal_u8(abs_0, vget_low_u8(a_1), vget_low_u8(avg_1));
    abs_0 = vabal_u8(abs_0, vget_high_u8(a_1), vget_high_u8(avg_1));
    abs_1 = vabal_u8(abs_1, vget_low_u8(a_2), vget_low_u8(avg_2));
    abs_1 = vabal_u8(abs_1, vget_high_u8(a_2), vget_high_u8(avg_2));
    abs_1 = vabal_u8(abs_1, vget_low_u8(a_3), vget_low_u8(avg_3));
    abs_1 = vabal_u8(abs_1, vget_high_u8(a_3), vget_high_u8(avg_3));
  }

  {
    const uint32x4_t sum = vpaddlq_u16(abs_0);
    return vpadalq_u16(sum, abs_1);
  }
}

#define sad64xN(n)                                                            \
  uint32_t vpx_sad64x##n##_neon(const uint8_t *src_ptr, int src_stride,       \
                                const uint8_t *ref_ptr, int ref_stride) {     \
    const uint32x4_t abs =                                                    \
        sad64x(src_ptr, src_stride, ref_ptr, ref_stride, n);                  \
    return vget_lane_u32(horizontal_add_uint32x4(abs), 0);                    \
  }                                                                           \
                                                                              \
  uint32_t vpx_sad64x##n##_avg_neon(const uint8_t *src_ptr, int src_stride,   \
                                    const uint8_t *ref_ptr, int ref_stride,   \
                                    const uint8_t *second_pred) {             \
    const uint32x4_t abs =                                                    \
        sad64x_avg(src_ptr, src_stride, ref_ptr, ref_stride, second_pred, n); \
    return vget_lane_u32(horizontal_add_uint32x4(abs), 0);                    \
  }

sad64xN(32);
sad64xN(64);
