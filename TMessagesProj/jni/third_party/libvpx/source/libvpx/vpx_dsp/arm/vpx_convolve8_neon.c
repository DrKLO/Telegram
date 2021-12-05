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

#include "./vpx_config.h"
#include "./vpx_dsp_rtcd.h"
#include "vpx/vpx_integer.h"
#include "vpx_dsp/arm/transpose_neon.h"
#include "vpx_dsp/arm/vpx_convolve8_neon.h"
#include "vpx_ports/mem.h"

// Note:
// 1. src is not always 32-bit aligned, so don't call vld1_lane_u32(src).
// 2. After refactoring the shared code in kernel loops with inline functions,
// the decoder speed dropped a lot when using gcc compiler. Therefore there is
// no refactoring for those parts by now.
// 3. For horizontal convolve, there is an alternative optimization that
// convolves a single row in each loop. For each row, 8 sample banks with 4 or 8
// samples in each are read from memory: src, (src+1), (src+2), (src+3),
// (src+4), (src+5), (src+6), (src+7), or prepared by vector extract
// instructions. This optimization is much faster in speed unit test, but slowed
// down the whole decoder by 5%.

static INLINE void store_u8_8x8(uint8_t *s, const ptrdiff_t p,
                                const uint8x8_t s0, const uint8x8_t s1,
                                const uint8x8_t s2, const uint8x8_t s3,
                                const uint8x8_t s4, const uint8x8_t s5,
                                const uint8x8_t s6, const uint8x8_t s7) {
  vst1_u8(s, s0);
  s += p;
  vst1_u8(s, s1);
  s += p;
  vst1_u8(s, s2);
  s += p;
  vst1_u8(s, s3);
  s += p;
  vst1_u8(s, s4);
  s += p;
  vst1_u8(s, s5);
  s += p;
  vst1_u8(s, s6);
  s += p;
  vst1_u8(s, s7);
}

void vpx_convolve8_horiz_neon(const uint8_t *src, ptrdiff_t src_stride,
                              uint8_t *dst, ptrdiff_t dst_stride,
                              const InterpKernel *filter, int x0_q4,
                              int x_step_q4, int y0_q4, int y_step_q4, int w,
                              int h) {
  const int16x8_t filters = vld1q_s16(filter[x0_q4]);
  uint8x8_t t0, t1, t2, t3;

  assert(!((intptr_t)dst & 3));
  assert(!(dst_stride & 3));
  assert(x_step_q4 == 16);

  (void)x_step_q4;
  (void)y0_q4;
  (void)y_step_q4;

  src -= 3;

  if (h == 4) {
    uint8x8_t d01, d23;
    int16x4_t filter3, filter4, s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, d0,
        d1, d2, d3;
    int16x8_t tt0, tt1, tt2, tt3;

    __builtin_prefetch(src + 0 * src_stride);
    __builtin_prefetch(src + 1 * src_stride);
    __builtin_prefetch(src + 2 * src_stride);
    __builtin_prefetch(src + 3 * src_stride);
    filter3 = vdup_lane_s16(vget_low_s16(filters), 3);
    filter4 = vdup_lane_s16(vget_high_s16(filters), 0);
    load_u8_8x4(src, src_stride, &t0, &t1, &t2, &t3);
    transpose_u8_8x4(&t0, &t1, &t2, &t3);
    tt0 = vreinterpretq_s16_u16(vmovl_u8(t0));
    tt1 = vreinterpretq_s16_u16(vmovl_u8(t1));
    tt2 = vreinterpretq_s16_u16(vmovl_u8(t2));
    tt3 = vreinterpretq_s16_u16(vmovl_u8(t3));
    s0 = vget_low_s16(tt0);
    s1 = vget_low_s16(tt1);
    s2 = vget_low_s16(tt2);
    s3 = vget_low_s16(tt3);
    s4 = vget_high_s16(tt0);
    s5 = vget_high_s16(tt1);
    s6 = vget_high_s16(tt2);
    __builtin_prefetch(dst + 0 * dst_stride);
    __builtin_prefetch(dst + 1 * dst_stride);
    __builtin_prefetch(dst + 2 * dst_stride);
    __builtin_prefetch(dst + 3 * dst_stride);
    src += 7;

    do {
      load_u8_8x4(src, src_stride, &t0, &t1, &t2, &t3);
      transpose_u8_8x4(&t0, &t1, &t2, &t3);
      tt0 = vreinterpretq_s16_u16(vmovl_u8(t0));
      tt1 = vreinterpretq_s16_u16(vmovl_u8(t1));
      tt2 = vreinterpretq_s16_u16(vmovl_u8(t2));
      tt3 = vreinterpretq_s16_u16(vmovl_u8(t3));
      s7 = vget_low_s16(tt0);
      s8 = vget_low_s16(tt1);
      s9 = vget_low_s16(tt2);
      s10 = vget_low_s16(tt3);

      d0 = convolve8_4(s0, s1, s2, s3, s4, s5, s6, s7, filters, filter3,
                       filter4);
      d1 = convolve8_4(s1, s2, s3, s4, s5, s6, s7, s8, filters, filter3,
                       filter4);
      d2 = convolve8_4(s2, s3, s4, s5, s6, s7, s8, s9, filters, filter3,
                       filter4);
      d3 = convolve8_4(s3, s4, s5, s6, s7, s8, s9, s10, filters, filter3,
                       filter4);

      d01 = vqrshrun_n_s16(vcombine_s16(d0, d1), 7);
      d23 = vqrshrun_n_s16(vcombine_s16(d2, d3), 7);
      transpose_u8_4x4(&d01, &d23);

      vst1_lane_u32((uint32_t *)(dst + 0 * dst_stride),
                    vreinterpret_u32_u8(d01), 0);
      vst1_lane_u32((uint32_t *)(dst + 1 * dst_stride),
                    vreinterpret_u32_u8(d23), 0);
      vst1_lane_u32((uint32_t *)(dst + 2 * dst_stride),
                    vreinterpret_u32_u8(d01), 1);
      vst1_lane_u32((uint32_t *)(dst + 3 * dst_stride),
                    vreinterpret_u32_u8(d23), 1);

      s0 = s4;
      s1 = s5;
      s2 = s6;
      s3 = s7;
      s4 = s8;
      s5 = s9;
      s6 = s10;
      src += 4;
      dst += 4;
      w -= 4;
    } while (w > 0);
  } else {
    const int16x8_t filter3 = vdupq_lane_s16(vget_low_s16(filters), 3);
    const int16x8_t filter4 = vdupq_lane_s16(vget_high_s16(filters), 0);
    int width;
    const uint8_t *s;
    uint8x8_t t4, t5, t6, t7;
    int16x8_t s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10;

    if (w == 4) {
      do {
        load_u8_8x8(src, src_stride, &t0, &t1, &t2, &t3, &t4, &t5, &t6, &t7);
        transpose_u8_8x8(&t0, &t1, &t2, &t3, &t4, &t5, &t6, &t7);
        s0 = vreinterpretq_s16_u16(vmovl_u8(t0));
        s1 = vreinterpretq_s16_u16(vmovl_u8(t1));
        s2 = vreinterpretq_s16_u16(vmovl_u8(t2));
        s3 = vreinterpretq_s16_u16(vmovl_u8(t3));
        s4 = vreinterpretq_s16_u16(vmovl_u8(t4));
        s5 = vreinterpretq_s16_u16(vmovl_u8(t5));
        s6 = vreinterpretq_s16_u16(vmovl_u8(t6));

        load_u8_8x8(src + 7, src_stride, &t0, &t1, &t2, &t3, &t4, &t5, &t6,
                    &t7);
        src += 8 * src_stride;
        __builtin_prefetch(dst + 0 * dst_stride);
        __builtin_prefetch(dst + 1 * dst_stride);
        __builtin_prefetch(dst + 2 * dst_stride);
        __builtin_prefetch(dst + 3 * dst_stride);
        __builtin_prefetch(dst + 4 * dst_stride);
        __builtin_prefetch(dst + 5 * dst_stride);
        __builtin_prefetch(dst + 6 * dst_stride);
        __builtin_prefetch(dst + 7 * dst_stride);
        transpose_u8_4x8(&t0, &t1, &t2, &t3, t4, t5, t6, t7);
        s7 = vreinterpretq_s16_u16(vmovl_u8(t0));
        s8 = vreinterpretq_s16_u16(vmovl_u8(t1));
        s9 = vreinterpretq_s16_u16(vmovl_u8(t2));
        s10 = vreinterpretq_s16_u16(vmovl_u8(t3));

        __builtin_prefetch(src + 0 * src_stride);
        __builtin_prefetch(src + 1 * src_stride);
        __builtin_prefetch(src + 2 * src_stride);
        __builtin_prefetch(src + 3 * src_stride);
        __builtin_prefetch(src + 4 * src_stride);
        __builtin_prefetch(src + 5 * src_stride);
        __builtin_prefetch(src + 6 * src_stride);
        __builtin_prefetch(src + 7 * src_stride);
        t0 = convolve8_8(s0, s1, s2, s3, s4, s5, s6, s7, filters, filter3,
                         filter4);
        t1 = convolve8_8(s1, s2, s3, s4, s5, s6, s7, s8, filters, filter3,
                         filter4);
        t2 = convolve8_8(s2, s3, s4, s5, s6, s7, s8, s9, filters, filter3,
                         filter4);
        t3 = convolve8_8(s3, s4, s5, s6, s7, s8, s9, s10, filters, filter3,
                         filter4);

        transpose_u8_8x4(&t0, &t1, &t2, &t3);
        vst1_lane_u32((uint32_t *)dst, vreinterpret_u32_u8(t0), 0);
        dst += dst_stride;
        vst1_lane_u32((uint32_t *)dst, vreinterpret_u32_u8(t1), 0);
        dst += dst_stride;
        vst1_lane_u32((uint32_t *)dst, vreinterpret_u32_u8(t2), 0);
        dst += dst_stride;
        vst1_lane_u32((uint32_t *)dst, vreinterpret_u32_u8(t3), 0);
        dst += dst_stride;
        vst1_lane_u32((uint32_t *)dst, vreinterpret_u32_u8(t0), 1);
        dst += dst_stride;
        vst1_lane_u32((uint32_t *)dst, vreinterpret_u32_u8(t1), 1);
        dst += dst_stride;
        vst1_lane_u32((uint32_t *)dst, vreinterpret_u32_u8(t2), 1);
        dst += dst_stride;
        vst1_lane_u32((uint32_t *)dst, vreinterpret_u32_u8(t3), 1);
        dst += dst_stride;
        h -= 8;
      } while (h > 0);
    } else {
      uint8_t *d;
      int16x8_t s11, s12, s13, s14;

      do {
        __builtin_prefetch(src + 0 * src_stride);
        __builtin_prefetch(src + 1 * src_stride);
        __builtin_prefetch(src + 2 * src_stride);
        __builtin_prefetch(src + 3 * src_stride);
        __builtin_prefetch(src + 4 * src_stride);
        __builtin_prefetch(src + 5 * src_stride);
        __builtin_prefetch(src + 6 * src_stride);
        __builtin_prefetch(src + 7 * src_stride);
        load_u8_8x8(src, src_stride, &t0, &t1, &t2, &t3, &t4, &t5, &t6, &t7);
        transpose_u8_8x8(&t0, &t1, &t2, &t3, &t4, &t5, &t6, &t7);
        s0 = vreinterpretq_s16_u16(vmovl_u8(t0));
        s1 = vreinterpretq_s16_u16(vmovl_u8(t1));
        s2 = vreinterpretq_s16_u16(vmovl_u8(t2));
        s3 = vreinterpretq_s16_u16(vmovl_u8(t3));
        s4 = vreinterpretq_s16_u16(vmovl_u8(t4));
        s5 = vreinterpretq_s16_u16(vmovl_u8(t5));
        s6 = vreinterpretq_s16_u16(vmovl_u8(t6));

        width = w;
        s = src + 7;
        d = dst;
        __builtin_prefetch(dst + 0 * dst_stride);
        __builtin_prefetch(dst + 1 * dst_stride);
        __builtin_prefetch(dst + 2 * dst_stride);
        __builtin_prefetch(dst + 3 * dst_stride);
        __builtin_prefetch(dst + 4 * dst_stride);
        __builtin_prefetch(dst + 5 * dst_stride);
        __builtin_prefetch(dst + 6 * dst_stride);
        __builtin_prefetch(dst + 7 * dst_stride);

        do {
          load_u8_8x8(s, src_stride, &t0, &t1, &t2, &t3, &t4, &t5, &t6, &t7);
          transpose_u8_8x8(&t0, &t1, &t2, &t3, &t4, &t5, &t6, &t7);
          s7 = vreinterpretq_s16_u16(vmovl_u8(t0));
          s8 = vreinterpretq_s16_u16(vmovl_u8(t1));
          s9 = vreinterpretq_s16_u16(vmovl_u8(t2));
          s10 = vreinterpretq_s16_u16(vmovl_u8(t3));
          s11 = vreinterpretq_s16_u16(vmovl_u8(t4));
          s12 = vreinterpretq_s16_u16(vmovl_u8(t5));
          s13 = vreinterpretq_s16_u16(vmovl_u8(t6));
          s14 = vreinterpretq_s16_u16(vmovl_u8(t7));

          t0 = convolve8_8(s0, s1, s2, s3, s4, s5, s6, s7, filters, filter3,
                           filter4);
          t1 = convolve8_8(s1, s2, s3, s4, s5, s6, s7, s8, filters, filter3,
                           filter4);
          t2 = convolve8_8(s2, s3, s4, s5, s6, s7, s8, s9, filters, filter3,
                           filter4);
          t3 = convolve8_8(s3, s4, s5, s6, s7, s8, s9, s10, filters, filter3,
                           filter4);
          t4 = convolve8_8(s4, s5, s6, s7, s8, s9, s10, s11, filters, filter3,
                           filter4);
          t5 = convolve8_8(s5, s6, s7, s8, s9, s10, s11, s12, filters, filter3,
                           filter4);
          t6 = convolve8_8(s6, s7, s8, s9, s10, s11, s12, s13, filters, filter3,
                           filter4);
          t7 = convolve8_8(s7, s8, s9, s10, s11, s12, s13, s14, filters,
                           filter3, filter4);

          transpose_u8_8x8(&t0, &t1, &t2, &t3, &t4, &t5, &t6, &t7);
          store_u8_8x8(d, dst_stride, t0, t1, t2, t3, t4, t5, t6, t7);

          s0 = s8;
          s1 = s9;
          s2 = s10;
          s3 = s11;
          s4 = s12;
          s5 = s13;
          s6 = s14;
          s += 8;
          d += 8;
          width -= 8;
        } while (width > 0);
        src += 8 * src_stride;
        dst += 8 * dst_stride;
        h -= 8;
      } while (h > 0);
    }
  }
}

void vpx_convolve8_avg_horiz_neon(const uint8_t *src, ptrdiff_t src_stride,
                                  uint8_t *dst, ptrdiff_t dst_stride,
                                  const InterpKernel *filter, int x0_q4,
                                  int x_step_q4, int y0_q4, int y_step_q4,
                                  int w, int h) {
  const int16x8_t filters = vld1q_s16(filter[x0_q4]);
  uint8x8_t t0, t1, t2, t3;

  assert(!((intptr_t)dst & 3));
  assert(!(dst_stride & 3));
  assert(x_step_q4 == 16);

  (void)x_step_q4;
  (void)y0_q4;
  (void)y_step_q4;

  src -= 3;

  if (h == 4) {
    uint8x8_t d01, d23;
    int16x4_t filter3, filter4, s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, d0,
        d1, d2, d3;
    int16x8_t tt0, tt1, tt2, tt3;
    uint32x4_t d0123 = vdupq_n_u32(0);

    __builtin_prefetch(src + 0 * src_stride);
    __builtin_prefetch(src + 1 * src_stride);
    __builtin_prefetch(src + 2 * src_stride);
    __builtin_prefetch(src + 3 * src_stride);
    filter3 = vdup_lane_s16(vget_low_s16(filters), 3);
    filter4 = vdup_lane_s16(vget_high_s16(filters), 0);
    load_u8_8x4(src, src_stride, &t0, &t1, &t2, &t3);
    transpose_u8_8x4(&t0, &t1, &t2, &t3);
    tt0 = vreinterpretq_s16_u16(vmovl_u8(t0));
    tt1 = vreinterpretq_s16_u16(vmovl_u8(t1));
    tt2 = vreinterpretq_s16_u16(vmovl_u8(t2));
    tt3 = vreinterpretq_s16_u16(vmovl_u8(t3));
    s0 = vget_low_s16(tt0);
    s1 = vget_low_s16(tt1);
    s2 = vget_low_s16(tt2);
    s3 = vget_low_s16(tt3);
    s4 = vget_high_s16(tt0);
    s5 = vget_high_s16(tt1);
    s6 = vget_high_s16(tt2);
    __builtin_prefetch(dst + 0 * dst_stride);
    __builtin_prefetch(dst + 1 * dst_stride);
    __builtin_prefetch(dst + 2 * dst_stride);
    __builtin_prefetch(dst + 3 * dst_stride);
    src += 7;

    do {
      load_u8_8x4(src, src_stride, &t0, &t1, &t2, &t3);
      transpose_u8_8x4(&t0, &t1, &t2, &t3);
      tt0 = vreinterpretq_s16_u16(vmovl_u8(t0));
      tt1 = vreinterpretq_s16_u16(vmovl_u8(t1));
      tt2 = vreinterpretq_s16_u16(vmovl_u8(t2));
      tt3 = vreinterpretq_s16_u16(vmovl_u8(t3));
      s7 = vget_low_s16(tt0);
      s8 = vget_low_s16(tt1);
      s9 = vget_low_s16(tt2);
      s10 = vget_low_s16(tt3);

      d0 = convolve8_4(s0, s1, s2, s3, s4, s5, s6, s7, filters, filter3,
                       filter4);
      d1 = convolve8_4(s1, s2, s3, s4, s5, s6, s7, s8, filters, filter3,
                       filter4);
      d2 = convolve8_4(s2, s3, s4, s5, s6, s7, s8, s9, filters, filter3,
                       filter4);
      d3 = convolve8_4(s3, s4, s5, s6, s7, s8, s9, s10, filters, filter3,
                       filter4);

      d01 = vqrshrun_n_s16(vcombine_s16(d0, d1), 7);
      d23 = vqrshrun_n_s16(vcombine_s16(d2, d3), 7);
      transpose_u8_4x4(&d01, &d23);

      d0123 = vld1q_lane_u32((uint32_t *)(dst + 0 * dst_stride), d0123, 0);
      d0123 = vld1q_lane_u32((uint32_t *)(dst + 1 * dst_stride), d0123, 2);
      d0123 = vld1q_lane_u32((uint32_t *)(dst + 2 * dst_stride), d0123, 1);
      d0123 = vld1q_lane_u32((uint32_t *)(dst + 3 * dst_stride), d0123, 3);
      d0123 = vreinterpretq_u32_u8(
          vrhaddq_u8(vreinterpretq_u8_u32(d0123), vcombine_u8(d01, d23)));

      vst1q_lane_u32((uint32_t *)(dst + 0 * dst_stride), d0123, 0);
      vst1q_lane_u32((uint32_t *)(dst + 1 * dst_stride), d0123, 2);
      vst1q_lane_u32((uint32_t *)(dst + 2 * dst_stride), d0123, 1);
      vst1q_lane_u32((uint32_t *)(dst + 3 * dst_stride), d0123, 3);

      s0 = s4;
      s1 = s5;
      s2 = s6;
      s3 = s7;
      s4 = s8;
      s5 = s9;
      s6 = s10;
      src += 4;
      dst += 4;
      w -= 4;
    } while (w > 0);
  } else {
    const int16x8_t filter3 = vdupq_lane_s16(vget_low_s16(filters), 3);
    const int16x8_t filter4 = vdupq_lane_s16(vget_high_s16(filters), 0);
    int width;
    const uint8_t *s;
    uint8x8_t t4, t5, t6, t7;
    int16x8_t s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10;

    if (w == 4) {
      uint32x4_t d0415 = vdupq_n_u32(0);
      uint32x4_t d2637 = vdupq_n_u32(0);
      do {
        load_u8_8x8(src, src_stride, &t0, &t1, &t2, &t3, &t4, &t5, &t6, &t7);
        transpose_u8_8x8(&t0, &t1, &t2, &t3, &t4, &t5, &t6, &t7);
        s0 = vreinterpretq_s16_u16(vmovl_u8(t0));
        s1 = vreinterpretq_s16_u16(vmovl_u8(t1));
        s2 = vreinterpretq_s16_u16(vmovl_u8(t2));
        s3 = vreinterpretq_s16_u16(vmovl_u8(t3));
        s4 = vreinterpretq_s16_u16(vmovl_u8(t4));
        s5 = vreinterpretq_s16_u16(vmovl_u8(t5));
        s6 = vreinterpretq_s16_u16(vmovl_u8(t6));

        load_u8_8x8(src + 7, src_stride, &t0, &t1, &t2, &t3, &t4, &t5, &t6,
                    &t7);
        src += 8 * src_stride;
        __builtin_prefetch(dst + 0 * dst_stride);
        __builtin_prefetch(dst + 1 * dst_stride);
        __builtin_prefetch(dst + 2 * dst_stride);
        __builtin_prefetch(dst + 3 * dst_stride);
        __builtin_prefetch(dst + 4 * dst_stride);
        __builtin_prefetch(dst + 5 * dst_stride);
        __builtin_prefetch(dst + 6 * dst_stride);
        __builtin_prefetch(dst + 7 * dst_stride);
        transpose_u8_4x8(&t0, &t1, &t2, &t3, t4, t5, t6, t7);
        s7 = vreinterpretq_s16_u16(vmovl_u8(t0));
        s8 = vreinterpretq_s16_u16(vmovl_u8(t1));
        s9 = vreinterpretq_s16_u16(vmovl_u8(t2));
        s10 = vreinterpretq_s16_u16(vmovl_u8(t3));

        __builtin_prefetch(src + 0 * src_stride);
        __builtin_prefetch(src + 1 * src_stride);
        __builtin_prefetch(src + 2 * src_stride);
        __builtin_prefetch(src + 3 * src_stride);
        __builtin_prefetch(src + 4 * src_stride);
        __builtin_prefetch(src + 5 * src_stride);
        __builtin_prefetch(src + 6 * src_stride);
        __builtin_prefetch(src + 7 * src_stride);
        t0 = convolve8_8(s0, s1, s2, s3, s4, s5, s6, s7, filters, filter3,
                         filter4);
        t1 = convolve8_8(s1, s2, s3, s4, s5, s6, s7, s8, filters, filter3,
                         filter4);
        t2 = convolve8_8(s2, s3, s4, s5, s6, s7, s8, s9, filters, filter3,
                         filter4);
        t3 = convolve8_8(s3, s4, s5, s6, s7, s8, s9, s10, filters, filter3,
                         filter4);

        transpose_u8_8x4(&t0, &t1, &t2, &t3);

        d0415 = vld1q_lane_u32((uint32_t *)(dst + 0 * dst_stride), d0415, 0);
        d0415 = vld1q_lane_u32((uint32_t *)(dst + 1 * dst_stride), d0415, 2);
        d2637 = vld1q_lane_u32((uint32_t *)(dst + 2 * dst_stride), d2637, 0);
        d2637 = vld1q_lane_u32((uint32_t *)(dst + 3 * dst_stride), d2637, 2);
        d0415 = vld1q_lane_u32((uint32_t *)(dst + 4 * dst_stride), d0415, 1);
        d0415 = vld1q_lane_u32((uint32_t *)(dst + 5 * dst_stride), d0415, 3);
        d2637 = vld1q_lane_u32((uint32_t *)(dst + 6 * dst_stride), d2637, 1);
        d2637 = vld1q_lane_u32((uint32_t *)(dst + 7 * dst_stride), d2637, 3);
        d0415 = vreinterpretq_u32_u8(
            vrhaddq_u8(vreinterpretq_u8_u32(d0415), vcombine_u8(t0, t1)));
        d2637 = vreinterpretq_u32_u8(
            vrhaddq_u8(vreinterpretq_u8_u32(d2637), vcombine_u8(t2, t3)));

        vst1q_lane_u32((uint32_t *)dst, d0415, 0);
        dst += dst_stride;
        vst1q_lane_u32((uint32_t *)dst, d0415, 2);
        dst += dst_stride;
        vst1q_lane_u32((uint32_t *)dst, d2637, 0);
        dst += dst_stride;
        vst1q_lane_u32((uint32_t *)dst, d2637, 2);
        dst += dst_stride;
        vst1q_lane_u32((uint32_t *)dst, d0415, 1);
        dst += dst_stride;
        vst1q_lane_u32((uint32_t *)dst, d0415, 3);
        dst += dst_stride;
        vst1q_lane_u32((uint32_t *)dst, d2637, 1);
        dst += dst_stride;
        vst1q_lane_u32((uint32_t *)dst, d2637, 3);
        dst += dst_stride;
        h -= 8;
      } while (h > 0);
    } else {
      uint8_t *d;
      int16x8_t s11, s12, s13, s14;
      uint8x16_t d01, d23, d45, d67;

      do {
        __builtin_prefetch(src + 0 * src_stride);
        __builtin_prefetch(src + 1 * src_stride);
        __builtin_prefetch(src + 2 * src_stride);
        __builtin_prefetch(src + 3 * src_stride);
        __builtin_prefetch(src + 4 * src_stride);
        __builtin_prefetch(src + 5 * src_stride);
        __builtin_prefetch(src + 6 * src_stride);
        __builtin_prefetch(src + 7 * src_stride);
        load_u8_8x8(src, src_stride, &t0, &t1, &t2, &t3, &t4, &t5, &t6, &t7);
        transpose_u8_8x8(&t0, &t1, &t2, &t3, &t4, &t5, &t6, &t7);
        s0 = vreinterpretq_s16_u16(vmovl_u8(t0));
        s1 = vreinterpretq_s16_u16(vmovl_u8(t1));
        s2 = vreinterpretq_s16_u16(vmovl_u8(t2));
        s3 = vreinterpretq_s16_u16(vmovl_u8(t3));
        s4 = vreinterpretq_s16_u16(vmovl_u8(t4));
        s5 = vreinterpretq_s16_u16(vmovl_u8(t5));
        s6 = vreinterpretq_s16_u16(vmovl_u8(t6));

        width = w;
        s = src + 7;
        d = dst;
        __builtin_prefetch(dst + 0 * dst_stride);
        __builtin_prefetch(dst + 1 * dst_stride);
        __builtin_prefetch(dst + 2 * dst_stride);
        __builtin_prefetch(dst + 3 * dst_stride);
        __builtin_prefetch(dst + 4 * dst_stride);
        __builtin_prefetch(dst + 5 * dst_stride);
        __builtin_prefetch(dst + 6 * dst_stride);
        __builtin_prefetch(dst + 7 * dst_stride);

        do {
          load_u8_8x8(s, src_stride, &t0, &t1, &t2, &t3, &t4, &t5, &t6, &t7);
          transpose_u8_8x8(&t0, &t1, &t2, &t3, &t4, &t5, &t6, &t7);
          s7 = vreinterpretq_s16_u16(vmovl_u8(t0));
          s8 = vreinterpretq_s16_u16(vmovl_u8(t1));
          s9 = vreinterpretq_s16_u16(vmovl_u8(t2));
          s10 = vreinterpretq_s16_u16(vmovl_u8(t3));
          s11 = vreinterpretq_s16_u16(vmovl_u8(t4));
          s12 = vreinterpretq_s16_u16(vmovl_u8(t5));
          s13 = vreinterpretq_s16_u16(vmovl_u8(t6));
          s14 = vreinterpretq_s16_u16(vmovl_u8(t7));

          t0 = convolve8_8(s0, s1, s2, s3, s4, s5, s6, s7, filters, filter3,
                           filter4);
          t1 = convolve8_8(s1, s2, s3, s4, s5, s6, s7, s8, filters, filter3,
                           filter4);
          t2 = convolve8_8(s2, s3, s4, s5, s6, s7, s8, s9, filters, filter3,
                           filter4);
          t3 = convolve8_8(s3, s4, s5, s6, s7, s8, s9, s10, filters, filter3,
                           filter4);
          t4 = convolve8_8(s4, s5, s6, s7, s8, s9, s10, s11, filters, filter3,
                           filter4);
          t5 = convolve8_8(s5, s6, s7, s8, s9, s10, s11, s12, filters, filter3,
                           filter4);
          t6 = convolve8_8(s6, s7, s8, s9, s10, s11, s12, s13, filters, filter3,
                           filter4);
          t7 = convolve8_8(s7, s8, s9, s10, s11, s12, s13, s14, filters,
                           filter3, filter4);

          transpose_u8_8x8(&t0, &t1, &t2, &t3, &t4, &t5, &t6, &t7);

          d01 = vcombine_u8(vld1_u8(d + 0 * dst_stride),
                            vld1_u8(d + 1 * dst_stride));
          d23 = vcombine_u8(vld1_u8(d + 2 * dst_stride),
                            vld1_u8(d + 3 * dst_stride));
          d45 = vcombine_u8(vld1_u8(d + 4 * dst_stride),
                            vld1_u8(d + 5 * dst_stride));
          d67 = vcombine_u8(vld1_u8(d + 6 * dst_stride),
                            vld1_u8(d + 7 * dst_stride));
          d01 = vrhaddq_u8(d01, vcombine_u8(t0, t1));
          d23 = vrhaddq_u8(d23, vcombine_u8(t2, t3));
          d45 = vrhaddq_u8(d45, vcombine_u8(t4, t5));
          d67 = vrhaddq_u8(d67, vcombine_u8(t6, t7));

          store_u8_8x8(d, dst_stride, vget_low_u8(d01), vget_high_u8(d01),
                       vget_low_u8(d23), vget_high_u8(d23), vget_low_u8(d45),
                       vget_high_u8(d45), vget_low_u8(d67), vget_high_u8(d67));

          s0 = s8;
          s1 = s9;
          s2 = s10;
          s3 = s11;
          s4 = s12;
          s5 = s13;
          s6 = s14;
          s += 8;
          d += 8;
          width -= 8;
        } while (width > 0);
        src += 8 * src_stride;
        dst += 8 * dst_stride;
        h -= 8;
      } while (h > 0);
    }
  }
}

void vpx_convolve8_vert_neon(const uint8_t *src, ptrdiff_t src_stride,
                             uint8_t *dst, ptrdiff_t dst_stride,
                             const InterpKernel *filter, int x0_q4,
                             int x_step_q4, int y0_q4, int y_step_q4, int w,
                             int h) {
  const int16x8_t filters = vld1q_s16(filter[y0_q4]);

  assert(!((intptr_t)dst & 3));
  assert(!(dst_stride & 3));
  assert(y_step_q4 == 16);

  (void)x0_q4;
  (void)x_step_q4;
  (void)y_step_q4;

  src -= 3 * src_stride;

  if (w == 4) {
    const int16x4_t filter3 = vdup_lane_s16(vget_low_s16(filters), 3);
    const int16x4_t filter4 = vdup_lane_s16(vget_high_s16(filters), 0);
    uint8x8_t d01, d23;
    int16x4_t s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, d0, d1, d2, d3;

    s0 = vreinterpret_s16_u16(vget_low_u16(vmovl_u8(vld1_u8(src))));
    src += src_stride;
    s1 = vreinterpret_s16_u16(vget_low_u16(vmovl_u8(vld1_u8(src))));
    src += src_stride;
    s2 = vreinterpret_s16_u16(vget_low_u16(vmovl_u8(vld1_u8(src))));
    src += src_stride;
    s3 = vreinterpret_s16_u16(vget_low_u16(vmovl_u8(vld1_u8(src))));
    src += src_stride;
    s4 = vreinterpret_s16_u16(vget_low_u16(vmovl_u8(vld1_u8(src))));
    src += src_stride;
    s5 = vreinterpret_s16_u16(vget_low_u16(vmovl_u8(vld1_u8(src))));
    src += src_stride;
    s6 = vreinterpret_s16_u16(vget_low_u16(vmovl_u8(vld1_u8(src))));
    src += src_stride;

    do {
      s7 = vreinterpret_s16_u16(vget_low_u16(vmovl_u8(vld1_u8(src))));
      src += src_stride;
      s8 = vreinterpret_s16_u16(vget_low_u16(vmovl_u8(vld1_u8(src))));
      src += src_stride;
      s9 = vreinterpret_s16_u16(vget_low_u16(vmovl_u8(vld1_u8(src))));
      src += src_stride;
      s10 = vreinterpret_s16_u16(vget_low_u16(vmovl_u8(vld1_u8(src))));
      src += src_stride;

      __builtin_prefetch(dst + 0 * dst_stride);
      __builtin_prefetch(dst + 1 * dst_stride);
      __builtin_prefetch(dst + 2 * dst_stride);
      __builtin_prefetch(dst + 3 * dst_stride);
      __builtin_prefetch(src + 0 * src_stride);
      __builtin_prefetch(src + 1 * src_stride);
      __builtin_prefetch(src + 2 * src_stride);
      __builtin_prefetch(src + 3 * src_stride);
      d0 = convolve8_4(s0, s1, s2, s3, s4, s5, s6, s7, filters, filter3,
                       filter4);
      d1 = convolve8_4(s1, s2, s3, s4, s5, s6, s7, s8, filters, filter3,
                       filter4);
      d2 = convolve8_4(s2, s3, s4, s5, s6, s7, s8, s9, filters, filter3,
                       filter4);
      d3 = convolve8_4(s3, s4, s5, s6, s7, s8, s9, s10, filters, filter3,
                       filter4);

      d01 = vqrshrun_n_s16(vcombine_s16(d0, d1), 7);
      d23 = vqrshrun_n_s16(vcombine_s16(d2, d3), 7);
      vst1_lane_u32((uint32_t *)dst, vreinterpret_u32_u8(d01), 0);
      dst += dst_stride;
      vst1_lane_u32((uint32_t *)dst, vreinterpret_u32_u8(d01), 1);
      dst += dst_stride;
      vst1_lane_u32((uint32_t *)dst, vreinterpret_u32_u8(d23), 0);
      dst += dst_stride;
      vst1_lane_u32((uint32_t *)dst, vreinterpret_u32_u8(d23), 1);
      dst += dst_stride;

      s0 = s4;
      s1 = s5;
      s2 = s6;
      s3 = s7;
      s4 = s8;
      s5 = s9;
      s6 = s10;
      h -= 4;
    } while (h > 0);
  } else {
    const int16x8_t filter3 = vdupq_lane_s16(vget_low_s16(filters), 3);
    const int16x8_t filter4 = vdupq_lane_s16(vget_high_s16(filters), 0);
    int height;
    const uint8_t *s;
    uint8_t *d;
    uint8x8_t t0, t1, t2, t3;
    int16x8_t s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10;

    do {
      __builtin_prefetch(src + 0 * src_stride);
      __builtin_prefetch(src + 1 * src_stride);
      __builtin_prefetch(src + 2 * src_stride);
      __builtin_prefetch(src + 3 * src_stride);
      __builtin_prefetch(src + 4 * src_stride);
      __builtin_prefetch(src + 5 * src_stride);
      __builtin_prefetch(src + 6 * src_stride);
      s = src;
      s0 = vreinterpretq_s16_u16(vmovl_u8(vld1_u8(s)));
      s += src_stride;
      s1 = vreinterpretq_s16_u16(vmovl_u8(vld1_u8(s)));
      s += src_stride;
      s2 = vreinterpretq_s16_u16(vmovl_u8(vld1_u8(s)));
      s += src_stride;
      s3 = vreinterpretq_s16_u16(vmovl_u8(vld1_u8(s)));
      s += src_stride;
      s4 = vreinterpretq_s16_u16(vmovl_u8(vld1_u8(s)));
      s += src_stride;
      s5 = vreinterpretq_s16_u16(vmovl_u8(vld1_u8(s)));
      s += src_stride;
      s6 = vreinterpretq_s16_u16(vmovl_u8(vld1_u8(s)));
      s += src_stride;
      d = dst;
      height = h;

      do {
        s7 = vreinterpretq_s16_u16(vmovl_u8(vld1_u8(s)));
        s += src_stride;
        s8 = vreinterpretq_s16_u16(vmovl_u8(vld1_u8(s)));
        s += src_stride;
        s9 = vreinterpretq_s16_u16(vmovl_u8(vld1_u8(s)));
        s += src_stride;
        s10 = vreinterpretq_s16_u16(vmovl_u8(vld1_u8(s)));
        s += src_stride;

        __builtin_prefetch(d + 0 * dst_stride);
        __builtin_prefetch(d + 1 * dst_stride);
        __builtin_prefetch(d + 2 * dst_stride);
        __builtin_prefetch(d + 3 * dst_stride);
        __builtin_prefetch(s + 0 * src_stride);
        __builtin_prefetch(s + 1 * src_stride);
        __builtin_prefetch(s + 2 * src_stride);
        __builtin_prefetch(s + 3 * src_stride);
        t0 = convolve8_8(s0, s1, s2, s3, s4, s5, s6, s7, filters, filter3,
                         filter4);
        t1 = convolve8_8(s1, s2, s3, s4, s5, s6, s7, s8, filters, filter3,
                         filter4);
        t2 = convolve8_8(s2, s3, s4, s5, s6, s7, s8, s9, filters, filter3,
                         filter4);
        t3 = convolve8_8(s3, s4, s5, s6, s7, s8, s9, s10, filters, filter3,
                         filter4);

        vst1_u8(d, t0);
        d += dst_stride;
        vst1_u8(d, t1);
        d += dst_stride;
        vst1_u8(d, t2);
        d += dst_stride;
        vst1_u8(d, t3);
        d += dst_stride;

        s0 = s4;
        s1 = s5;
        s2 = s6;
        s3 = s7;
        s4 = s8;
        s5 = s9;
        s6 = s10;
        height -= 4;
      } while (height > 0);
      src += 8;
      dst += 8;
      w -= 8;
    } while (w > 0);
  }
}

void vpx_convolve8_avg_vert_neon(const uint8_t *src, ptrdiff_t src_stride,
                                 uint8_t *dst, ptrdiff_t dst_stride,
                                 const InterpKernel *filter, int x0_q4,
                                 int x_step_q4, int y0_q4, int y_step_q4, int w,
                                 int h) {
  const int16x8_t filters = vld1q_s16(filter[y0_q4]);

  assert(!((intptr_t)dst & 3));
  assert(!(dst_stride & 3));
  assert(y_step_q4 == 16);

  (void)x0_q4;
  (void)x_step_q4;
  (void)y_step_q4;

  src -= 3 * src_stride;

  if (w == 4) {
    const int16x4_t filter3 = vdup_lane_s16(vget_low_s16(filters), 3);
    const int16x4_t filter4 = vdup_lane_s16(vget_high_s16(filters), 0);
    uint8x8_t d01, d23;
    int16x4_t s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, d0, d1, d2, d3;
    uint32x4_t d0123 = vdupq_n_u32(0);

    s0 = vreinterpret_s16_u16(vget_low_u16(vmovl_u8(vld1_u8(src))));
    src += src_stride;
    s1 = vreinterpret_s16_u16(vget_low_u16(vmovl_u8(vld1_u8(src))));
    src += src_stride;
    s2 = vreinterpret_s16_u16(vget_low_u16(vmovl_u8(vld1_u8(src))));
    src += src_stride;
    s3 = vreinterpret_s16_u16(vget_low_u16(vmovl_u8(vld1_u8(src))));
    src += src_stride;
    s4 = vreinterpret_s16_u16(vget_low_u16(vmovl_u8(vld1_u8(src))));
    src += src_stride;
    s5 = vreinterpret_s16_u16(vget_low_u16(vmovl_u8(vld1_u8(src))));
    src += src_stride;
    s6 = vreinterpret_s16_u16(vget_low_u16(vmovl_u8(vld1_u8(src))));
    src += src_stride;

    do {
      s7 = vreinterpret_s16_u16(vget_low_u16(vmovl_u8(vld1_u8(src))));
      src += src_stride;
      s8 = vreinterpret_s16_u16(vget_low_u16(vmovl_u8(vld1_u8(src))));
      src += src_stride;
      s9 = vreinterpret_s16_u16(vget_low_u16(vmovl_u8(vld1_u8(src))));
      src += src_stride;
      s10 = vreinterpret_s16_u16(vget_low_u16(vmovl_u8(vld1_u8(src))));
      src += src_stride;

      __builtin_prefetch(dst + 0 * dst_stride);
      __builtin_prefetch(dst + 1 * dst_stride);
      __builtin_prefetch(dst + 2 * dst_stride);
      __builtin_prefetch(dst + 3 * dst_stride);
      __builtin_prefetch(src + 0 * src_stride);
      __builtin_prefetch(src + 1 * src_stride);
      __builtin_prefetch(src + 2 * src_stride);
      __builtin_prefetch(src + 3 * src_stride);
      d0 = convolve8_4(s0, s1, s2, s3, s4, s5, s6, s7, filters, filter3,
                       filter4);
      d1 = convolve8_4(s1, s2, s3, s4, s5, s6, s7, s8, filters, filter3,
                       filter4);
      d2 = convolve8_4(s2, s3, s4, s5, s6, s7, s8, s9, filters, filter3,
                       filter4);
      d3 = convolve8_4(s3, s4, s5, s6, s7, s8, s9, s10, filters, filter3,
                       filter4);

      d01 = vqrshrun_n_s16(vcombine_s16(d0, d1), 7);
      d23 = vqrshrun_n_s16(vcombine_s16(d2, d3), 7);

      d0123 = vld1q_lane_u32((uint32_t *)(dst + 0 * dst_stride), d0123, 0);
      d0123 = vld1q_lane_u32((uint32_t *)(dst + 1 * dst_stride), d0123, 1);
      d0123 = vld1q_lane_u32((uint32_t *)(dst + 2 * dst_stride), d0123, 2);
      d0123 = vld1q_lane_u32((uint32_t *)(dst + 3 * dst_stride), d0123, 3);
      d0123 = vreinterpretq_u32_u8(
          vrhaddq_u8(vreinterpretq_u8_u32(d0123), vcombine_u8(d01, d23)));

      vst1q_lane_u32((uint32_t *)dst, d0123, 0);
      dst += dst_stride;
      vst1q_lane_u32((uint32_t *)dst, d0123, 1);
      dst += dst_stride;
      vst1q_lane_u32((uint32_t *)dst, d0123, 2);
      dst += dst_stride;
      vst1q_lane_u32((uint32_t *)dst, d0123, 3);
      dst += dst_stride;

      s0 = s4;
      s1 = s5;
      s2 = s6;
      s3 = s7;
      s4 = s8;
      s5 = s9;
      s6 = s10;
      h -= 4;
    } while (h > 0);
  } else {
    const int16x8_t filter3 = vdupq_lane_s16(vget_low_s16(filters), 3);
    const int16x8_t filter4 = vdupq_lane_s16(vget_high_s16(filters), 0);
    int height;
    const uint8_t *s;
    uint8_t *d;
    uint8x8_t t0, t1, t2, t3;
    uint8x16_t d01, d23, dd01, dd23;
    int16x8_t s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10;

    do {
      __builtin_prefetch(src + 0 * src_stride);
      __builtin_prefetch(src + 1 * src_stride);
      __builtin_prefetch(src + 2 * src_stride);
      __builtin_prefetch(src + 3 * src_stride);
      __builtin_prefetch(src + 4 * src_stride);
      __builtin_prefetch(src + 5 * src_stride);
      __builtin_prefetch(src + 6 * src_stride);
      s = src;
      s0 = vreinterpretq_s16_u16(vmovl_u8(vld1_u8(s)));
      s += src_stride;
      s1 = vreinterpretq_s16_u16(vmovl_u8(vld1_u8(s)));
      s += src_stride;
      s2 = vreinterpretq_s16_u16(vmovl_u8(vld1_u8(s)));
      s += src_stride;
      s3 = vreinterpretq_s16_u16(vmovl_u8(vld1_u8(s)));
      s += src_stride;
      s4 = vreinterpretq_s16_u16(vmovl_u8(vld1_u8(s)));
      s += src_stride;
      s5 = vreinterpretq_s16_u16(vmovl_u8(vld1_u8(s)));
      s += src_stride;
      s6 = vreinterpretq_s16_u16(vmovl_u8(vld1_u8(s)));
      s += src_stride;
      d = dst;
      height = h;

      do {
        s7 = vreinterpretq_s16_u16(vmovl_u8(vld1_u8(s)));
        s += src_stride;
        s8 = vreinterpretq_s16_u16(vmovl_u8(vld1_u8(s)));
        s += src_stride;
        s9 = vreinterpretq_s16_u16(vmovl_u8(vld1_u8(s)));
        s += src_stride;
        s10 = vreinterpretq_s16_u16(vmovl_u8(vld1_u8(s)));
        s += src_stride;

        __builtin_prefetch(d + 0 * dst_stride);
        __builtin_prefetch(d + 1 * dst_stride);
        __builtin_prefetch(d + 2 * dst_stride);
        __builtin_prefetch(d + 3 * dst_stride);
        __builtin_prefetch(s + 0 * src_stride);
        __builtin_prefetch(s + 1 * src_stride);
        __builtin_prefetch(s + 2 * src_stride);
        __builtin_prefetch(s + 3 * src_stride);
        t0 = convolve8_8(s0, s1, s2, s3, s4, s5, s6, s7, filters, filter3,
                         filter4);
        t1 = convolve8_8(s1, s2, s3, s4, s5, s6, s7, s8, filters, filter3,
                         filter4);
        t2 = convolve8_8(s2, s3, s4, s5, s6, s7, s8, s9, filters, filter3,
                         filter4);
        t3 = convolve8_8(s3, s4, s5, s6, s7, s8, s9, s10, filters, filter3,
                         filter4);

        d01 = vcombine_u8(t0, t1);
        d23 = vcombine_u8(t2, t3);
        dd01 = vcombine_u8(vld1_u8(d + 0 * dst_stride),
                           vld1_u8(d + 1 * dst_stride));
        dd23 = vcombine_u8(vld1_u8(d + 2 * dst_stride),
                           vld1_u8(d + 3 * dst_stride));
        dd01 = vrhaddq_u8(dd01, d01);
        dd23 = vrhaddq_u8(dd23, d23);

        vst1_u8(d, vget_low_u8(dd01));
        d += dst_stride;
        vst1_u8(d, vget_high_u8(dd01));
        d += dst_stride;
        vst1_u8(d, vget_low_u8(dd23));
        d += dst_stride;
        vst1_u8(d, vget_high_u8(dd23));
        d += dst_stride;

        s0 = s4;
        s1 = s5;
        s2 = s6;
        s3 = s7;
        s4 = s8;
        s5 = s9;
        s6 = s10;
        height -= 4;
      } while (height > 0);
      src += 8;
      dst += 8;
      w -= 8;
    } while (w > 0);
  }
}
