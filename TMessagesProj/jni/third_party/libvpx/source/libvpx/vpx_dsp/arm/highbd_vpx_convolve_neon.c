/*
 *  Copyright (c) 2016 The WebM project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "./vpx_dsp_rtcd.h"
#include "vpx_dsp/vpx_dsp_common.h"
#include "vpx_dsp/vpx_filter.h"
#include "vpx_ports/mem.h"

void vpx_highbd_convolve8_neon(const uint16_t *src, ptrdiff_t src_stride,
                               uint16_t *dst, ptrdiff_t dst_stride,
                               const InterpKernel *filter, int x0_q4,
                               int x_step_q4, int y0_q4, int y_step_q4, int w,
                               int h, int bd) {
  // + 1 to make it divisible by 4
  uint16_t temp[64 * 136];
  const int intermediate_height =
      (((h - 1) * y_step_q4 + y0_q4) >> SUBPEL_BITS) + SUBPEL_TAPS;

  /* Filter starting 3 lines back. The neon implementation will ignore the given
   * height and filter a multiple of 4 lines. Since this goes in to the temp
   * buffer which has lots of extra room and is subsequently discarded this is
   * safe if somewhat less than ideal.   */
  vpx_highbd_convolve8_horiz_neon(src - src_stride * 3, src_stride, temp, w,
                                  filter, x0_q4, x_step_q4, y0_q4, y_step_q4, w,
                                  intermediate_height, bd);

  /* Step into the temp buffer 3 lines to get the actual frame data */
  vpx_highbd_convolve8_vert_neon(temp + w * 3, w, dst, dst_stride, filter,
                                 x0_q4, x_step_q4, y0_q4, y_step_q4, w, h, bd);
}

void vpx_highbd_convolve8_avg_neon(const uint16_t *src, ptrdiff_t src_stride,
                                   uint16_t *dst, ptrdiff_t dst_stride,
                                   const InterpKernel *filter, int x0_q4,
                                   int x_step_q4, int y0_q4, int y_step_q4,
                                   int w, int h, int bd) {
  // + 1 to make it divisible by 4
  uint16_t temp[64 * 136];
  const int intermediate_height =
      (((h - 1) * y_step_q4 + y0_q4) >> SUBPEL_BITS) + SUBPEL_TAPS;

  /* This implementation has the same issues as above. In addition, we only want
   * to average the values after both passes.
   */
  vpx_highbd_convolve8_horiz_neon(src - src_stride * 3, src_stride, temp, w,
                                  filter, x0_q4, x_step_q4, y0_q4, y_step_q4, w,
                                  intermediate_height, bd);
  vpx_highbd_convolve8_avg_vert_neon(temp + w * 3, w, dst, dst_stride, filter,
                                     x0_q4, x_step_q4, y0_q4, y_step_q4, w, h,
                                     bd);
}
