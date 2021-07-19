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
#include "./vpx_dsp_rtcd.h"
#include "./vpx_config.h"

#include "vpx/vpx_integer.h"

#include "vpx_dsp/variance.h"
#include "vpx_dsp/arm/mem_neon.h"

static const uint8_t bilinear_filters[8][2] = {
  { 128, 0 }, { 112, 16 }, { 96, 32 }, { 80, 48 },
  { 64, 64 }, { 48, 80 },  { 32, 96 }, { 16, 112 },
};

// Process a block exactly 4 wide and a multiple of 2 high.
static void var_filter_block2d_bil_w4(const uint8_t *src_ptr,
                                      uint8_t *output_ptr,
                                      unsigned int src_pixels_per_line,
                                      int pixel_step,
                                      unsigned int output_height,
                                      const uint8_t *filter) {
  const uint8x8_t f0 = vdup_n_u8(filter[0]);
  const uint8x8_t f1 = vdup_n_u8(filter[1]);
  unsigned int i;
  for (i = 0; i < output_height; i += 2) {
    const uint8x8_t src_0 = load_unaligned_u8(src_ptr, src_pixels_per_line);
    const uint8x8_t src_1 =
        load_unaligned_u8(src_ptr + pixel_step, src_pixels_per_line);
    const uint16x8_t a = vmull_u8(src_0, f0);
    const uint16x8_t b = vmlal_u8(a, src_1, f1);
    const uint8x8_t out = vrshrn_n_u16(b, FILTER_BITS);
    vst1_u8(output_ptr, out);
    src_ptr += 2 * src_pixels_per_line;
    output_ptr += 8;
  }
}

// Process a block exactly 8 wide and any height.
static void var_filter_block2d_bil_w8(const uint8_t *src_ptr,
                                      uint8_t *output_ptr,
                                      unsigned int src_pixels_per_line,
                                      int pixel_step,
                                      unsigned int output_height,
                                      const uint8_t *filter) {
  const uint8x8_t f0 = vdup_n_u8(filter[0]);
  const uint8x8_t f1 = vdup_n_u8(filter[1]);
  unsigned int i;
  for (i = 0; i < output_height; ++i) {
    const uint8x8_t src_0 = vld1_u8(&src_ptr[0]);
    const uint8x8_t src_1 = vld1_u8(&src_ptr[pixel_step]);
    const uint16x8_t a = vmull_u8(src_0, f0);
    const uint16x8_t b = vmlal_u8(a, src_1, f1);
    const uint8x8_t out = vrshrn_n_u16(b, FILTER_BITS);
    vst1_u8(output_ptr, out);
    src_ptr += src_pixels_per_line;
    output_ptr += 8;
  }
}

// Process a block which is a mutiple of 16 wide and any height.
static void var_filter_block2d_bil_w16(const uint8_t *src_ptr,
                                       uint8_t *output_ptr,
                                       unsigned int src_pixels_per_line,
                                       int pixel_step,
                                       unsigned int output_height,
                                       unsigned int output_width,
                                       const uint8_t *filter) {
  const uint8x8_t f0 = vdup_n_u8(filter[0]);
  const uint8x8_t f1 = vdup_n_u8(filter[1]);
  unsigned int i, j;
  for (i = 0; i < output_height; ++i) {
    for (j = 0; j < output_width; j += 16) {
      const uint8x16_t src_0 = vld1q_u8(&src_ptr[j]);
      const uint8x16_t src_1 = vld1q_u8(&src_ptr[j + pixel_step]);
      const uint16x8_t a = vmull_u8(vget_low_u8(src_0), f0);
      const uint16x8_t b = vmlal_u8(a, vget_low_u8(src_1), f1);
      const uint8x8_t out_lo = vrshrn_n_u16(b, FILTER_BITS);
      const uint16x8_t c = vmull_u8(vget_high_u8(src_0), f0);
      const uint16x8_t d = vmlal_u8(c, vget_high_u8(src_1), f1);
      const uint8x8_t out_hi = vrshrn_n_u16(d, FILTER_BITS);
      vst1q_u8(output_ptr + j, vcombine_u8(out_lo, out_hi));
    }
    src_ptr += src_pixels_per_line;
    output_ptr += output_width;
  }
}

// 4xM filter writes an extra row to fdata because it processes two rows at a
// time.
#define sub_pixel_varianceNxM(n, m)                                         \
  uint32_t vpx_sub_pixel_variance##n##x##m##_neon(                          \
      const uint8_t *src_ptr, int src_stride, int x_offset, int y_offset,   \
      const uint8_t *ref_ptr, int ref_stride, uint32_t *sse) {              \
    uint8_t temp0[n * (m + (n == 4 ? 2 : 1))];                              \
    uint8_t temp1[n * m];                                                   \
                                                                            \
    if (n == 4) {                                                           \
      var_filter_block2d_bil_w4(src_ptr, temp0, src_stride, 1, (m + 2),     \
                                bilinear_filters[x_offset]);                \
      var_filter_block2d_bil_w4(temp0, temp1, n, n, m,                      \
                                bilinear_filters[y_offset]);                \
    } else if (n == 8) {                                                    \
      var_filter_block2d_bil_w8(src_ptr, temp0, src_stride, 1, (m + 1),     \
                                bilinear_filters[x_offset]);                \
      var_filter_block2d_bil_w8(temp0, temp1, n, n, m,                      \
                                bilinear_filters[y_offset]);                \
    } else {                                                                \
      var_filter_block2d_bil_w16(src_ptr, temp0, src_stride, 1, (m + 1), n, \
                                 bilinear_filters[x_offset]);               \
      var_filter_block2d_bil_w16(temp0, temp1, n, n, m, n,                  \
                                 bilinear_filters[y_offset]);               \
    }                                                                       \
    return vpx_variance##n##x##m(temp1, n, ref_ptr, ref_stride, sse);       \
  }

sub_pixel_varianceNxM(4, 4);
sub_pixel_varianceNxM(4, 8);
sub_pixel_varianceNxM(8, 4);
sub_pixel_varianceNxM(8, 8);
sub_pixel_varianceNxM(8, 16);
sub_pixel_varianceNxM(16, 8);
sub_pixel_varianceNxM(16, 16);
sub_pixel_varianceNxM(16, 32);
sub_pixel_varianceNxM(32, 16);
sub_pixel_varianceNxM(32, 32);
sub_pixel_varianceNxM(32, 64);
sub_pixel_varianceNxM(64, 32);
sub_pixel_varianceNxM(64, 64);

// 4xM filter writes an extra row to fdata because it processes two rows at a
// time.
#define sub_pixel_avg_varianceNxM(n, m)                                     \
  uint32_t vpx_sub_pixel_avg_variance##n##x##m##_neon(                      \
      const uint8_t *src_ptr, int src_stride, int x_offset, int y_offset,   \
      const uint8_t *ref_ptr, int ref_stride, uint32_t *sse,                \
      const uint8_t *second_pred) {                                         \
    uint8_t temp0[n * (m + (n == 4 ? 2 : 1))];                              \
    uint8_t temp1[n * m];                                                   \
                                                                            \
    if (n == 4) {                                                           \
      var_filter_block2d_bil_w4(src_ptr, temp0, src_stride, 1, (m + 2),     \
                                bilinear_filters[x_offset]);                \
      var_filter_block2d_bil_w4(temp0, temp1, n, n, m,                      \
                                bilinear_filters[y_offset]);                \
    } else if (n == 8) {                                                    \
      var_filter_block2d_bil_w8(src_ptr, temp0, src_stride, 1, (m + 1),     \
                                bilinear_filters[x_offset]);                \
      var_filter_block2d_bil_w8(temp0, temp1, n, n, m,                      \
                                bilinear_filters[y_offset]);                \
    } else {                                                                \
      var_filter_block2d_bil_w16(src_ptr, temp0, src_stride, 1, (m + 1), n, \
                                 bilinear_filters[x_offset]);               \
      var_filter_block2d_bil_w16(temp0, temp1, n, n, m, n,                  \
                                 bilinear_filters[y_offset]);               \
    }                                                                       \
                                                                            \
    vpx_comp_avg_pred(temp0, second_pred, n, m, temp1, n);                  \
                                                                            \
    return vpx_variance##n##x##m(temp0, n, ref_ptr, ref_stride, sse);       \
  }

sub_pixel_avg_varianceNxM(4, 4);
sub_pixel_avg_varianceNxM(4, 8);
sub_pixel_avg_varianceNxM(8, 4);
sub_pixel_avg_varianceNxM(8, 8);
sub_pixel_avg_varianceNxM(8, 16);
sub_pixel_avg_varianceNxM(16, 8);
sub_pixel_avg_varianceNxM(16, 16);
sub_pixel_avg_varianceNxM(16, 32);
sub_pixel_avg_varianceNxM(32, 16);
sub_pixel_avg_varianceNxM(32, 32);
sub_pixel_avg_varianceNxM(32, 64);
sub_pixel_avg_varianceNxM(64, 32);
sub_pixel_avg_varianceNxM(64, 64);
