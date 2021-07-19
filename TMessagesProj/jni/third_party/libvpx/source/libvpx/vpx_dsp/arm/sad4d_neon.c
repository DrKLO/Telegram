/*
 *  Copyright (c) 2015 The WebM project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <arm_neon.h>

#include <assert.h>
#include "./vpx_config.h"
#include "./vpx_dsp_rtcd.h"
#include "vpx/vpx_integer.h"
#include "vpx_dsp/arm/mem_neon.h"
#include "vpx_dsp/arm/sum_neon.h"

static INLINE uint8x8_t load_unaligned_2_buffers(const void *const buf0,
                                                 const void *const buf1) {
  uint32_t a;
  uint32x2_t aa = vdup_n_u32(0);
  memcpy(&a, buf0, 4);
  aa = vset_lane_u32(a, aa, 0);
  memcpy(&a, buf1, 4);
  aa = vset_lane_u32(a, aa, 1);
  return vreinterpret_u8_u32(aa);
}

static INLINE void sad4x_4d(const uint8_t *const src_ptr, const int src_stride,
                            const uint8_t *const ref_array[4],
                            const int ref_stride, const int height,
                            uint32_t *const res) {
  int i;
  uint16x8_t abs[2] = { vdupq_n_u16(0), vdupq_n_u16(0) };
  uint16x4_t a[2];
  uint32x4_t r;

  assert(!((intptr_t)src_ptr % sizeof(uint32_t)));
  assert(!(src_stride % sizeof(uint32_t)));

  for (i = 0; i < height; ++i) {
    const uint8x8_t s = vreinterpret_u8_u32(
        vld1_dup_u32((const uint32_t *)(src_ptr + i * src_stride)));
    const uint8x8_t ref01 = load_unaligned_2_buffers(
        ref_array[0] + i * ref_stride, ref_array[1] + i * ref_stride);
    const uint8x8_t ref23 = load_unaligned_2_buffers(
        ref_array[2] + i * ref_stride, ref_array[3] + i * ref_stride);
    abs[0] = vabal_u8(abs[0], s, ref01);
    abs[1] = vabal_u8(abs[1], s, ref23);
  }

  a[0] = vpadd_u16(vget_low_u16(abs[0]), vget_high_u16(abs[0]));
  a[1] = vpadd_u16(vget_low_u16(abs[1]), vget_high_u16(abs[1]));
  r = vpaddlq_u16(vcombine_u16(a[0], a[1]));
  vst1q_u32(res, r);
}

void vpx_sad4x4x4d_neon(const uint8_t *src_ptr, int src_stride,
                        const uint8_t *const ref_array[4], int ref_stride,
                        uint32_t *res) {
  sad4x_4d(src_ptr, src_stride, ref_array, ref_stride, 4, res);
}

void vpx_sad4x8x4d_neon(const uint8_t *src_ptr, int src_stride,
                        const uint8_t *const ref_array[4], int ref_stride,
                        uint32_t *res) {
  sad4x_4d(src_ptr, src_stride, ref_array, ref_stride, 8, res);
}

////////////////////////////////////////////////////////////////////////////////

// Can handle 512 pixels' sad sum (such as 16x32 or 32x16)
static INLINE void sad_512_pel_final_neon(const uint16x8_t *sum /*[4]*/,
                                          uint32_t *const res) {
  const uint16x4_t a0 = vadd_u16(vget_low_u16(sum[0]), vget_high_u16(sum[0]));
  const uint16x4_t a1 = vadd_u16(vget_low_u16(sum[1]), vget_high_u16(sum[1]));
  const uint16x4_t a2 = vadd_u16(vget_low_u16(sum[2]), vget_high_u16(sum[2]));
  const uint16x4_t a3 = vadd_u16(vget_low_u16(sum[3]), vget_high_u16(sum[3]));
  const uint16x4_t b0 = vpadd_u16(a0, a1);
  const uint16x4_t b1 = vpadd_u16(a2, a3);
  const uint32x4_t r = vpaddlq_u16(vcombine_u16(b0, b1));
  vst1q_u32(res, r);
}

// Can handle 1024 pixels' sad sum (such as 32x32)
static INLINE void sad_1024_pel_final_neon(const uint16x8_t *sum /*[4]*/,
                                           uint32_t *const res) {
  const uint16x4_t a0 = vpadd_u16(vget_low_u16(sum[0]), vget_high_u16(sum[0]));
  const uint16x4_t a1 = vpadd_u16(vget_low_u16(sum[1]), vget_high_u16(sum[1]));
  const uint16x4_t a2 = vpadd_u16(vget_low_u16(sum[2]), vget_high_u16(sum[2]));
  const uint16x4_t a3 = vpadd_u16(vget_low_u16(sum[3]), vget_high_u16(sum[3]));
  const uint32x4_t b0 = vpaddlq_u16(vcombine_u16(a0, a1));
  const uint32x4_t b1 = vpaddlq_u16(vcombine_u16(a2, a3));
  const uint32x2_t c0 = vpadd_u32(vget_low_u32(b0), vget_high_u32(b0));
  const uint32x2_t c1 = vpadd_u32(vget_low_u32(b1), vget_high_u32(b1));
  vst1q_u32(res, vcombine_u32(c0, c1));
}

// Can handle 2048 pixels' sad sum (such as 32x64 or 64x32)
static INLINE void sad_2048_pel_final_neon(const uint16x8_t *sum /*[4]*/,
                                           uint32_t *const res) {
  const uint32x4_t a0 = vpaddlq_u16(sum[0]);
  const uint32x4_t a1 = vpaddlq_u16(sum[1]);
  const uint32x4_t a2 = vpaddlq_u16(sum[2]);
  const uint32x4_t a3 = vpaddlq_u16(sum[3]);
  const uint32x2_t b0 = vadd_u32(vget_low_u32(a0), vget_high_u32(a0));
  const uint32x2_t b1 = vadd_u32(vget_low_u32(a1), vget_high_u32(a1));
  const uint32x2_t b2 = vadd_u32(vget_low_u32(a2), vget_high_u32(a2));
  const uint32x2_t b3 = vadd_u32(vget_low_u32(a3), vget_high_u32(a3));
  const uint32x2_t c0 = vpadd_u32(b0, b1);
  const uint32x2_t c1 = vpadd_u32(b2, b3);
  vst1q_u32(res, vcombine_u32(c0, c1));
}

// Can handle 4096 pixels' sad sum (such as 64x64)
static INLINE void sad_4096_pel_final_neon(const uint16x8_t *sum /*[8]*/,
                                           uint32_t *const res) {
  const uint32x4_t a0 = vpaddlq_u16(sum[0]);
  const uint32x4_t a1 = vpaddlq_u16(sum[1]);
  const uint32x4_t a2 = vpaddlq_u16(sum[2]);
  const uint32x4_t a3 = vpaddlq_u16(sum[3]);
  const uint32x4_t a4 = vpaddlq_u16(sum[4]);
  const uint32x4_t a5 = vpaddlq_u16(sum[5]);
  const uint32x4_t a6 = vpaddlq_u16(sum[6]);
  const uint32x4_t a7 = vpaddlq_u16(sum[7]);
  const uint32x4_t b0 = vaddq_u32(a0, a1);
  const uint32x4_t b1 = vaddq_u32(a2, a3);
  const uint32x4_t b2 = vaddq_u32(a4, a5);
  const uint32x4_t b3 = vaddq_u32(a6, a7);
  const uint32x2_t c0 = vadd_u32(vget_low_u32(b0), vget_high_u32(b0));
  const uint32x2_t c1 = vadd_u32(vget_low_u32(b1), vget_high_u32(b1));
  const uint32x2_t c2 = vadd_u32(vget_low_u32(b2), vget_high_u32(b2));
  const uint32x2_t c3 = vadd_u32(vget_low_u32(b3), vget_high_u32(b3));
  const uint32x2_t d0 = vpadd_u32(c0, c1);
  const uint32x2_t d1 = vpadd_u32(c2, c3);
  vst1q_u32(res, vcombine_u32(d0, d1));
}

static INLINE void sad8x_4d(const uint8_t *src_ptr, int src_stride,
                            const uint8_t *const ref_array[4], int ref_stride,
                            uint32_t *res, const int height) {
  int i, j;
  const uint8_t *ref_loop[4] = { ref_array[0], ref_array[1], ref_array[2],
                                 ref_array[3] };
  uint16x8_t sum[4] = { vdupq_n_u16(0), vdupq_n_u16(0), vdupq_n_u16(0),
                        vdupq_n_u16(0) };

  for (i = 0; i < height; ++i) {
    const uint8x8_t s = vld1_u8(src_ptr);
    src_ptr += src_stride;
    for (j = 0; j < 4; ++j) {
      const uint8x8_t b_u8 = vld1_u8(ref_loop[j]);
      ref_loop[j] += ref_stride;
      sum[j] = vabal_u8(sum[j], s, b_u8);
    }
  }

  sad_512_pel_final_neon(sum, res);
}

void vpx_sad8x4x4d_neon(const uint8_t *src_ptr, int src_stride,
                        const uint8_t *const ref_array[4], int ref_stride,
                        uint32_t *res) {
  sad8x_4d(src_ptr, src_stride, ref_array, ref_stride, res, 4);
}

void vpx_sad8x8x4d_neon(const uint8_t *src_ptr, int src_stride,
                        const uint8_t *const ref_array[4], int ref_stride,
                        uint32_t *res) {
  sad8x_4d(src_ptr, src_stride, ref_array, ref_stride, res, 8);
}

void vpx_sad8x16x4d_neon(const uint8_t *src_ptr, int src_stride,
                         const uint8_t *const ref_array[4], int ref_stride,
                         uint32_t *res) {
  sad8x_4d(src_ptr, src_stride, ref_array, ref_stride, res, 16);
}

////////////////////////////////////////////////////////////////////////////////

static INLINE void sad16_neon(const uint8_t *ref_ptr, const uint8x16_t src_ptr,
                              uint16x8_t *const sum) {
  const uint8x16_t r = vld1q_u8(ref_ptr);
  *sum = vabal_u8(*sum, vget_low_u8(src_ptr), vget_low_u8(r));
  *sum = vabal_u8(*sum, vget_high_u8(src_ptr), vget_high_u8(r));
}

static INLINE void sad16x_4d(const uint8_t *src_ptr, int src_stride,
                             const uint8_t *const ref_array[4], int ref_stride,
                             uint32_t *res, const int height) {
  int i, j;
  const uint8_t *ref_loop[4] = { ref_array[0], ref_array[1], ref_array[2],
                                 ref_array[3] };
  uint16x8_t sum[4] = { vdupq_n_u16(0), vdupq_n_u16(0), vdupq_n_u16(0),
                        vdupq_n_u16(0) };

  for (i = 0; i < height; ++i) {
    const uint8x16_t s = vld1q_u8(src_ptr);
    src_ptr += src_stride;
    for (j = 0; j < 4; ++j) {
      sad16_neon(ref_loop[j], s, &sum[j]);
      ref_loop[j] += ref_stride;
    }
  }

  sad_512_pel_final_neon(sum, res);
}

void vpx_sad16x8x4d_neon(const uint8_t *src_ptr, int src_stride,
                         const uint8_t *const ref_array[4], int ref_stride,
                         uint32_t *res) {
  sad16x_4d(src_ptr, src_stride, ref_array, ref_stride, res, 8);
}

void vpx_sad16x16x4d_neon(const uint8_t *src_ptr, int src_stride,
                          const uint8_t *const ref_array[4], int ref_stride,
                          uint32_t *res) {
  sad16x_4d(src_ptr, src_stride, ref_array, ref_stride, res, 16);
}

void vpx_sad16x32x4d_neon(const uint8_t *src_ptr, int src_stride,
                          const uint8_t *const ref_array[4], int ref_stride,
                          uint32_t *res) {
  sad16x_4d(src_ptr, src_stride, ref_array, ref_stride, res, 32);
}

////////////////////////////////////////////////////////////////////////////////

static INLINE void sad32x_4d(const uint8_t *src_ptr, int src_stride,
                             const uint8_t *const ref_array[4], int ref_stride,
                             const int height, uint16x8_t *const sum) {
  int i;
  const uint8_t *ref_loop[4] = { ref_array[0], ref_array[1], ref_array[2],
                                 ref_array[3] };

  sum[0] = sum[1] = sum[2] = sum[3] = vdupq_n_u16(0);

  for (i = 0; i < height; ++i) {
    uint8x16_t s;

    s = vld1q_u8(src_ptr + 0 * 16);
    sad16_neon(ref_loop[0] + 0 * 16, s, &sum[0]);
    sad16_neon(ref_loop[1] + 0 * 16, s, &sum[1]);
    sad16_neon(ref_loop[2] + 0 * 16, s, &sum[2]);
    sad16_neon(ref_loop[3] + 0 * 16, s, &sum[3]);

    s = vld1q_u8(src_ptr + 1 * 16);
    sad16_neon(ref_loop[0] + 1 * 16, s, &sum[0]);
    sad16_neon(ref_loop[1] + 1 * 16, s, &sum[1]);
    sad16_neon(ref_loop[2] + 1 * 16, s, &sum[2]);
    sad16_neon(ref_loop[3] + 1 * 16, s, &sum[3]);

    src_ptr += src_stride;
    ref_loop[0] += ref_stride;
    ref_loop[1] += ref_stride;
    ref_loop[2] += ref_stride;
    ref_loop[3] += ref_stride;
  }
}

void vpx_sad32x16x4d_neon(const uint8_t *src_ptr, int src_stride,
                          const uint8_t *const ref_array[4], int ref_stride,
                          uint32_t *res) {
  uint16x8_t sum[4];
  sad32x_4d(src_ptr, src_stride, ref_array, ref_stride, 16, sum);
  sad_512_pel_final_neon(sum, res);
}

void vpx_sad32x32x4d_neon(const uint8_t *src_ptr, int src_stride,
                          const uint8_t *const ref_array[4], int ref_stride,
                          uint32_t *res) {
  uint16x8_t sum[4];
  sad32x_4d(src_ptr, src_stride, ref_array, ref_stride, 32, sum);
  sad_1024_pel_final_neon(sum, res);
}

void vpx_sad32x64x4d_neon(const uint8_t *src_ptr, int src_stride,
                          const uint8_t *const ref_array[4], int ref_stride,
                          uint32_t *res) {
  uint16x8_t sum[4];
  sad32x_4d(src_ptr, src_stride, ref_array, ref_stride, 64, sum);
  sad_2048_pel_final_neon(sum, res);
}

////////////////////////////////////////////////////////////////////////////////

void vpx_sad64x32x4d_neon(const uint8_t *src_ptr, int src_stride,
                          const uint8_t *const ref_array[4], int ref_stride,
                          uint32_t *res) {
  int i;
  const uint8_t *ref_loop[4] = { ref_array[0], ref_array[1], ref_array[2],
                                 ref_array[3] };
  uint16x8_t sum[4] = { vdupq_n_u16(0), vdupq_n_u16(0), vdupq_n_u16(0),
                        vdupq_n_u16(0) };

  for (i = 0; i < 32; ++i) {
    uint8x16_t s;

    s = vld1q_u8(src_ptr + 0 * 16);
    sad16_neon(ref_loop[0] + 0 * 16, s, &sum[0]);
    sad16_neon(ref_loop[1] + 0 * 16, s, &sum[1]);
    sad16_neon(ref_loop[2] + 0 * 16, s, &sum[2]);
    sad16_neon(ref_loop[3] + 0 * 16, s, &sum[3]);

    s = vld1q_u8(src_ptr + 1 * 16);
    sad16_neon(ref_loop[0] + 1 * 16, s, &sum[0]);
    sad16_neon(ref_loop[1] + 1 * 16, s, &sum[1]);
    sad16_neon(ref_loop[2] + 1 * 16, s, &sum[2]);
    sad16_neon(ref_loop[3] + 1 * 16, s, &sum[3]);

    s = vld1q_u8(src_ptr + 2 * 16);
    sad16_neon(ref_loop[0] + 2 * 16, s, &sum[0]);
    sad16_neon(ref_loop[1] + 2 * 16, s, &sum[1]);
    sad16_neon(ref_loop[2] + 2 * 16, s, &sum[2]);
    sad16_neon(ref_loop[3] + 2 * 16, s, &sum[3]);

    s = vld1q_u8(src_ptr + 3 * 16);
    sad16_neon(ref_loop[0] + 3 * 16, s, &sum[0]);
    sad16_neon(ref_loop[1] + 3 * 16, s, &sum[1]);
    sad16_neon(ref_loop[2] + 3 * 16, s, &sum[2]);
    sad16_neon(ref_loop[3] + 3 * 16, s, &sum[3]);

    src_ptr += src_stride;
    ref_loop[0] += ref_stride;
    ref_loop[1] += ref_stride;
    ref_loop[2] += ref_stride;
    ref_loop[3] += ref_stride;
  }

  sad_2048_pel_final_neon(sum, res);
}

void vpx_sad64x64x4d_neon(const uint8_t *src_ptr, int src_stride,
                          const uint8_t *const ref_array[4], int ref_stride,
                          uint32_t *res) {
  int i;
  const uint8_t *ref_loop[4] = { ref_array[0], ref_array[1], ref_array[2],
                                 ref_array[3] };
  uint16x8_t sum[8] = { vdupq_n_u16(0), vdupq_n_u16(0), vdupq_n_u16(0),
                        vdupq_n_u16(0), vdupq_n_u16(0), vdupq_n_u16(0),
                        vdupq_n_u16(0), vdupq_n_u16(0) };

  for (i = 0; i < 64; ++i) {
    uint8x16_t s;

    s = vld1q_u8(src_ptr + 0 * 16);
    sad16_neon(ref_loop[0] + 0 * 16, s, &sum[0]);
    sad16_neon(ref_loop[1] + 0 * 16, s, &sum[2]);
    sad16_neon(ref_loop[2] + 0 * 16, s, &sum[4]);
    sad16_neon(ref_loop[3] + 0 * 16, s, &sum[6]);

    s = vld1q_u8(src_ptr + 1 * 16);
    sad16_neon(ref_loop[0] + 1 * 16, s, &sum[0]);
    sad16_neon(ref_loop[1] + 1 * 16, s, &sum[2]);
    sad16_neon(ref_loop[2] + 1 * 16, s, &sum[4]);
    sad16_neon(ref_loop[3] + 1 * 16, s, &sum[6]);

    s = vld1q_u8(src_ptr + 2 * 16);
    sad16_neon(ref_loop[0] + 2 * 16, s, &sum[1]);
    sad16_neon(ref_loop[1] + 2 * 16, s, &sum[3]);
    sad16_neon(ref_loop[2] + 2 * 16, s, &sum[5]);
    sad16_neon(ref_loop[3] + 2 * 16, s, &sum[7]);

    s = vld1q_u8(src_ptr + 3 * 16);
    sad16_neon(ref_loop[0] + 3 * 16, s, &sum[1]);
    sad16_neon(ref_loop[1] + 3 * 16, s, &sum[3]);
    sad16_neon(ref_loop[2] + 3 * 16, s, &sum[5]);
    sad16_neon(ref_loop[3] + 3 * 16, s, &sum[7]);

    src_ptr += src_stride;
    ref_loop[0] += ref_stride;
    ref_loop[1] += ref_stride;
    ref_loop[2] += ref_stride;
    ref_loop[3] += ref_stride;
  }

  sad_4096_pel_final_neon(sum, res);
}
