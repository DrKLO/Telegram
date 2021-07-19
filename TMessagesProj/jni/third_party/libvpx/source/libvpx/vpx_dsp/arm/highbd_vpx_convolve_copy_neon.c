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

#include "./vpx_dsp_rtcd.h"
#include "vpx/vpx_integer.h"

void vpx_highbd_convolve_copy_neon(const uint16_t *src, ptrdiff_t src_stride,
                                   uint16_t *dst, ptrdiff_t dst_stride,
                                   const InterpKernel *filter, int x0_q4,
                                   int x_step_q4, int y0_q4, int y_step_q4,
                                   int w, int h, int bd) {
  (void)filter;
  (void)x0_q4;
  (void)x_step_q4;
  (void)y0_q4;
  (void)y_step_q4;
  (void)bd;

  if (w < 8) {  // copy4
    do {
      vst1_u16(dst, vld1_u16(src));
      src += src_stride;
      dst += dst_stride;
      vst1_u16(dst, vld1_u16(src));
      src += src_stride;
      dst += dst_stride;
      h -= 2;
    } while (h > 0);
  } else if (w == 8) {  // copy8
    do {
      vst1q_u16(dst, vld1q_u16(src));
      src += src_stride;
      dst += dst_stride;
      vst1q_u16(dst, vld1q_u16(src));
      src += src_stride;
      dst += dst_stride;
      h -= 2;
    } while (h > 0);
  } else if (w < 32) {  // copy16
    do {
      vst2q_u16(dst, vld2q_u16(src));
      src += src_stride;
      dst += dst_stride;
      vst2q_u16(dst, vld2q_u16(src));
      src += src_stride;
      dst += dst_stride;
      vst2q_u16(dst, vld2q_u16(src));
      src += src_stride;
      dst += dst_stride;
      vst2q_u16(dst, vld2q_u16(src));
      src += src_stride;
      dst += dst_stride;
      h -= 4;
    } while (h > 0);
  } else if (w == 32) {  // copy32
    do {
      vst4q_u16(dst, vld4q_u16(src));
      src += src_stride;
      dst += dst_stride;
      vst4q_u16(dst, vld4q_u16(src));
      src += src_stride;
      dst += dst_stride;
      vst4q_u16(dst, vld4q_u16(src));
      src += src_stride;
      dst += dst_stride;
      vst4q_u16(dst, vld4q_u16(src));
      src += src_stride;
      dst += dst_stride;
      h -= 4;
    } while (h > 0);
  } else {  // copy64
    do {
      vst4q_u16(dst, vld4q_u16(src));
      vst4q_u16(dst + 32, vld4q_u16(src + 32));
      src += src_stride;
      dst += dst_stride;
      vst4q_u16(dst, vld4q_u16(src));
      vst4q_u16(dst + 32, vld4q_u16(src + 32));
      src += src_stride;
      dst += dst_stride;
      vst4q_u16(dst, vld4q_u16(src));
      vst4q_u16(dst + 32, vld4q_u16(src + 32));
      src += src_stride;
      dst += dst_stride;
      vst4q_u16(dst, vld4q_u16(src));
      vst4q_u16(dst + 32, vld4q_u16(src + 32));
      src += src_stride;
      dst += dst_stride;
      h -= 4;
    } while (h > 0);
  }
}
