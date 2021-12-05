/*
 *  Copyright (c) 2017 The WebM project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <arm_neon.h>
#include <assert.h>
#include <string.h>

#include "./vpx_config.h"
#include "./vpx_dsp_rtcd.h"
#include "vpx/vpx_integer.h"
#include "vpx_dsp/arm/transpose_neon.h"
#include "vpx_dsp/arm/vpx_convolve8_neon.h"
#include "vpx_ports/mem.h"

static INLINE void scaledconvolve_horiz_w4(
    const uint8_t *src, const ptrdiff_t src_stride, uint8_t *dst,
    const ptrdiff_t dst_stride, const InterpKernel *const x_filters,
    const int x0_q4, const int x_step_q4, const int w, const int h) {
  DECLARE_ALIGNED(16, uint8_t, temp[4 * 4]);
  int x, y, z;

  src -= SUBPEL_TAPS / 2 - 1;

  y = h;
  do {
    int x_q4 = x0_q4;
    x = 0;
    do {
      // process 4 src_x steps
      for (z = 0; z < 4; ++z) {
        const uint8_t *const src_x = &src[x_q4 >> SUBPEL_BITS];
        if (x_q4 & SUBPEL_MASK) {
          const int16x8_t filters = vld1q_s16(x_filters[x_q4 & SUBPEL_MASK]);
          const int16x4_t filter3 = vdup_lane_s16(vget_low_s16(filters), 3);
          const int16x4_t filter4 = vdup_lane_s16(vget_high_s16(filters), 0);
          uint8x8_t s[8], d;
          int16x8_t ss[4];
          int16x4_t t[8], tt;

          load_u8_8x4(src_x, src_stride, &s[0], &s[1], &s[2], &s[3]);
          transpose_u8_8x4(&s[0], &s[1], &s[2], &s[3]);

          ss[0] = vreinterpretq_s16_u16(vmovl_u8(s[0]));
          ss[1] = vreinterpretq_s16_u16(vmovl_u8(s[1]));
          ss[2] = vreinterpretq_s16_u16(vmovl_u8(s[2]));
          ss[3] = vreinterpretq_s16_u16(vmovl_u8(s[3]));
          t[0] = vget_low_s16(ss[0]);
          t[1] = vget_low_s16(ss[1]);
          t[2] = vget_low_s16(ss[2]);
          t[3] = vget_low_s16(ss[3]);
          t[4] = vget_high_s16(ss[0]);
          t[5] = vget_high_s16(ss[1]);
          t[6] = vget_high_s16(ss[2]);
          t[7] = vget_high_s16(ss[3]);

          tt = convolve8_4(t[0], t[1], t[2], t[3], t[4], t[5], t[6], t[7],
                           filters, filter3, filter4);
          d = vqrshrun_n_s16(vcombine_s16(tt, tt), 7);
          vst1_lane_u32((uint32_t *)&temp[4 * z], vreinterpret_u32_u8(d), 0);
        } else {
          int i;
          for (i = 0; i < 4; ++i) {
            temp[z * 4 + i] = src_x[i * src_stride + 3];
          }
        }
        x_q4 += x_step_q4;
      }

      // transpose the 4x4 filters values back to dst
      {
        const uint8x8x4_t d4 = vld4_u8(temp);
        vst1_lane_u32((uint32_t *)&dst[x + 0 * dst_stride],
                      vreinterpret_u32_u8(d4.val[0]), 0);
        vst1_lane_u32((uint32_t *)&dst[x + 1 * dst_stride],
                      vreinterpret_u32_u8(d4.val[1]), 0);
        vst1_lane_u32((uint32_t *)&dst[x + 2 * dst_stride],
                      vreinterpret_u32_u8(d4.val[2]), 0);
        vst1_lane_u32((uint32_t *)&dst[x + 3 * dst_stride],
                      vreinterpret_u32_u8(d4.val[3]), 0);
      }
      x += 4;
    } while (x < w);

    src += src_stride * 4;
    dst += dst_stride * 4;
    y -= 4;
  } while (y > 0);
}

static INLINE void scaledconvolve_horiz_w8(
    const uint8_t *src, const ptrdiff_t src_stride, uint8_t *dst,
    const ptrdiff_t dst_stride, const InterpKernel *const x_filters,
    const int x0_q4, const int x_step_q4, const int w, const int h) {
  DECLARE_ALIGNED(16, uint8_t, temp[8 * 8]);
  int x, y, z;
  src -= SUBPEL_TAPS / 2 - 1;

  // This function processes 8x8 areas. The intermediate height is not always
  // a multiple of 8, so force it to be a multiple of 8 here.
  y = (h + 7) & ~7;

  do {
    int x_q4 = x0_q4;
    x = 0;
    do {
      uint8x8_t d[8];
      // process 8 src_x steps
      for (z = 0; z < 8; ++z) {
        const uint8_t *const src_x = &src[x_q4 >> SUBPEL_BITS];

        if (x_q4 & SUBPEL_MASK) {
          const int16x8_t filters = vld1q_s16(x_filters[x_q4 & SUBPEL_MASK]);
          uint8x8_t s[8];
          load_u8_8x8(src_x, src_stride, &s[0], &s[1], &s[2], &s[3], &s[4],
                      &s[5], &s[6], &s[7]);
          transpose_u8_8x8(&s[0], &s[1], &s[2], &s[3], &s[4], &s[5], &s[6],
                           &s[7]);
          d[0] = scale_filter_8(s, filters);
          vst1_u8(&temp[8 * z], d[0]);
        } else {
          int i;
          for (i = 0; i < 8; ++i) {
            temp[z * 8 + i] = src_x[i * src_stride + 3];
          }
        }
        x_q4 += x_step_q4;
      }

      // transpose the 8x8 filters values back to dst
      load_u8_8x8(temp, 8, &d[0], &d[1], &d[2], &d[3], &d[4], &d[5], &d[6],
                  &d[7]);
      transpose_u8_8x8(&d[0], &d[1], &d[2], &d[3], &d[4], &d[5], &d[6], &d[7]);
      vst1_u8(&dst[x + 0 * dst_stride], d[0]);
      vst1_u8(&dst[x + 1 * dst_stride], d[1]);
      vst1_u8(&dst[x + 2 * dst_stride], d[2]);
      vst1_u8(&dst[x + 3 * dst_stride], d[3]);
      vst1_u8(&dst[x + 4 * dst_stride], d[4]);
      vst1_u8(&dst[x + 5 * dst_stride], d[5]);
      vst1_u8(&dst[x + 6 * dst_stride], d[6]);
      vst1_u8(&dst[x + 7 * dst_stride], d[7]);
      x += 8;
    } while (x < w);

    src += src_stride * 8;
    dst += dst_stride * 8;
  } while (y -= 8);
}

static INLINE void scaledconvolve_vert_w4(
    const uint8_t *src, const ptrdiff_t src_stride, uint8_t *dst,
    const ptrdiff_t dst_stride, const InterpKernel *const y_filters,
    const int y0_q4, const int y_step_q4, const int w, const int h) {
  int y;
  int y_q4 = y0_q4;

  src -= src_stride * (SUBPEL_TAPS / 2 - 1);
  y = h;
  do {
    const unsigned char *src_y = &src[(y_q4 >> SUBPEL_BITS) * src_stride];

    if (y_q4 & SUBPEL_MASK) {
      const int16x8_t filters = vld1q_s16(y_filters[y_q4 & SUBPEL_MASK]);
      const int16x4_t filter3 = vdup_lane_s16(vget_low_s16(filters), 3);
      const int16x4_t filter4 = vdup_lane_s16(vget_high_s16(filters), 0);
      uint8x8_t s[8], d;
      int16x4_t t[8], tt;

      load_u8_8x8(src_y, src_stride, &s[0], &s[1], &s[2], &s[3], &s[4], &s[5],
                  &s[6], &s[7]);
      t[0] = vget_low_s16(vreinterpretq_s16_u16(vmovl_u8(s[0])));
      t[1] = vget_low_s16(vreinterpretq_s16_u16(vmovl_u8(s[1])));
      t[2] = vget_low_s16(vreinterpretq_s16_u16(vmovl_u8(s[2])));
      t[3] = vget_low_s16(vreinterpretq_s16_u16(vmovl_u8(s[3])));
      t[4] = vget_low_s16(vreinterpretq_s16_u16(vmovl_u8(s[4])));
      t[5] = vget_low_s16(vreinterpretq_s16_u16(vmovl_u8(s[5])));
      t[6] = vget_low_s16(vreinterpretq_s16_u16(vmovl_u8(s[6])));
      t[7] = vget_low_s16(vreinterpretq_s16_u16(vmovl_u8(s[7])));

      tt = convolve8_4(t[0], t[1], t[2], t[3], t[4], t[5], t[6], t[7], filters,
                       filter3, filter4);
      d = vqrshrun_n_s16(vcombine_s16(tt, tt), 7);
      vst1_lane_u32((uint32_t *)dst, vreinterpret_u32_u8(d), 0);
    } else {
      memcpy(dst, &src_y[3 * src_stride], w);
    }

    dst += dst_stride;
    y_q4 += y_step_q4;
  } while (--y);
}

static INLINE void scaledconvolve_vert_w8(
    const uint8_t *src, const ptrdiff_t src_stride, uint8_t *dst,
    const ptrdiff_t dst_stride, const InterpKernel *const y_filters,
    const int y0_q4, const int y_step_q4, const int w, const int h) {
  int y;
  int y_q4 = y0_q4;

  src -= src_stride * (SUBPEL_TAPS / 2 - 1);
  y = h;
  do {
    const unsigned char *src_y = &src[(y_q4 >> SUBPEL_BITS) * src_stride];
    if (y_q4 & SUBPEL_MASK) {
      const int16x8_t filters = vld1q_s16(y_filters[y_q4 & SUBPEL_MASK]);
      uint8x8_t s[8], d;
      load_u8_8x8(src_y, src_stride, &s[0], &s[1], &s[2], &s[3], &s[4], &s[5],
                  &s[6], &s[7]);
      d = scale_filter_8(s, filters);
      vst1_u8(dst, d);
    } else {
      memcpy(dst, &src_y[3 * src_stride], w);
    }
    dst += dst_stride;
    y_q4 += y_step_q4;
  } while (--y);
}

static INLINE void scaledconvolve_vert_w16(
    const uint8_t *src, const ptrdiff_t src_stride, uint8_t *dst,
    const ptrdiff_t dst_stride, const InterpKernel *const y_filters,
    const int y0_q4, const int y_step_q4, const int w, const int h) {
  int x, y;
  int y_q4 = y0_q4;

  src -= src_stride * (SUBPEL_TAPS / 2 - 1);
  y = h;
  do {
    const unsigned char *src_y = &src[(y_q4 >> SUBPEL_BITS) * src_stride];
    if (y_q4 & SUBPEL_MASK) {
      x = 0;
      do {
        const int16x8_t filters = vld1q_s16(y_filters[y_q4 & SUBPEL_MASK]);
        uint8x16_t ss[8];
        uint8x8_t s[8], d[2];
        load_u8_16x8(src_y, src_stride, &ss[0], &ss[1], &ss[2], &ss[3], &ss[4],
                     &ss[5], &ss[6], &ss[7]);
        s[0] = vget_low_u8(ss[0]);
        s[1] = vget_low_u8(ss[1]);
        s[2] = vget_low_u8(ss[2]);
        s[3] = vget_low_u8(ss[3]);
        s[4] = vget_low_u8(ss[4]);
        s[5] = vget_low_u8(ss[5]);
        s[6] = vget_low_u8(ss[6]);
        s[7] = vget_low_u8(ss[7]);
        d[0] = scale_filter_8(s, filters);

        s[0] = vget_high_u8(ss[0]);
        s[1] = vget_high_u8(ss[1]);
        s[2] = vget_high_u8(ss[2]);
        s[3] = vget_high_u8(ss[3]);
        s[4] = vget_high_u8(ss[4]);
        s[5] = vget_high_u8(ss[5]);
        s[6] = vget_high_u8(ss[6]);
        s[7] = vget_high_u8(ss[7]);
        d[1] = scale_filter_8(s, filters);
        vst1q_u8(&dst[x], vcombine_u8(d[0], d[1]));
        src_y += 16;
        x += 16;
      } while (x < w);
    } else {
      memcpy(dst, &src_y[3 * src_stride], w);
    }
    dst += dst_stride;
    y_q4 += y_step_q4;
  } while (--y);
}

void vpx_scaled_2d_neon(const uint8_t *src, ptrdiff_t src_stride, uint8_t *dst,
                        ptrdiff_t dst_stride, const InterpKernel *filter,
                        int x0_q4, int x_step_q4, int y0_q4, int y_step_q4,
                        int w, int h) {
  // Note: Fixed size intermediate buffer, temp, places limits on parameters.
  // 2d filtering proceeds in 2 steps:
  //   (1) Interpolate horizontally into an intermediate buffer, temp.
  //   (2) Interpolate temp vertically to derive the sub-pixel result.
  // Deriving the maximum number of rows in the temp buffer (135):
  // --Smallest scaling factor is x1/2 ==> y_step_q4 = 32 (Normative).
  // --Largest block size is 64x64 pixels.
  // --64 rows in the downscaled frame span a distance of (64 - 1) * 32 in the
  //   original frame (in 1/16th pixel units).
  // --Must round-up because block may be located at sub-pixel position.
  // --Require an additional SUBPEL_TAPS rows for the 8-tap filter tails.
  // --((64 - 1) * 32 + 15) >> 4 + 8 = 135.
  // --Require an additional 8 rows for the horiz_w8 transpose tail.
  // When calling in frame scaling function, the smallest scaling factor is x1/4
  // ==> y_step_q4 = 64. Since w and h are at most 16, the temp buffer is still
  // big enough.
  DECLARE_ALIGNED(16, uint8_t, temp[(135 + 8) * 64]);
  const int intermediate_height =
      (((h - 1) * y_step_q4 + y0_q4) >> SUBPEL_BITS) + SUBPEL_TAPS;

  assert(w <= 64);
  assert(h <= 64);
  assert(y_step_q4 <= 32 || (y_step_q4 <= 64 && h <= 32));
  assert(x_step_q4 <= 64);

  if (w >= 8) {
    scaledconvolve_horiz_w8(src - src_stride * (SUBPEL_TAPS / 2 - 1),
                            src_stride, temp, 64, filter, x0_q4, x_step_q4, w,
                            intermediate_height);
  } else {
    scaledconvolve_horiz_w4(src - src_stride * (SUBPEL_TAPS / 2 - 1),
                            src_stride, temp, 64, filter, x0_q4, x_step_q4, w,
                            intermediate_height);
  }

  if (w >= 16) {
    scaledconvolve_vert_w16(temp + 64 * (SUBPEL_TAPS / 2 - 1), 64, dst,
                            dst_stride, filter, y0_q4, y_step_q4, w, h);
  } else if (w == 8) {
    scaledconvolve_vert_w8(temp + 64 * (SUBPEL_TAPS / 2 - 1), 64, dst,
                           dst_stride, filter, y0_q4, y_step_q4, w, h);
  } else {
    scaledconvolve_vert_w4(temp + 64 * (SUBPEL_TAPS / 2 - 1), 64, dst,
                           dst_stride, filter, y0_q4, y_step_q4, w, h);
  }
}
