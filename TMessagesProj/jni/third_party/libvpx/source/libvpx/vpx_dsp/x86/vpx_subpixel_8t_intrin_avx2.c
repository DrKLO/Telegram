/*
 *  Copyright (c) 2010 The WebM project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <immintrin.h>
#include <stdio.h>

#include "./vpx_dsp_rtcd.h"
#include "vpx_dsp/x86/convolve.h"
#include "vpx_dsp/x86/convolve_avx2.h"
#include "vpx_dsp/x86/convolve_sse2.h"
#include "vpx_ports/mem.h"

// filters for 16_h8
DECLARE_ALIGNED(32, static const uint8_t,
                filt1_global_avx2[32]) = { 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5,
                                           6, 6, 7, 7, 8, 0, 1, 1, 2, 2, 3,
                                           3, 4, 4, 5, 5, 6, 6, 7, 7, 8 };

DECLARE_ALIGNED(32, static const uint8_t,
                filt2_global_avx2[32]) = { 2, 3, 3, 4, 4,  5, 5, 6, 6, 7, 7,
                                           8, 8, 9, 9, 10, 2, 3, 3, 4, 4, 5,
                                           5, 6, 6, 7, 7,  8, 8, 9, 9, 10 };

DECLARE_ALIGNED(32, static const uint8_t, filt3_global_avx2[32]) = {
  4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12,
  4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12
};

DECLARE_ALIGNED(32, static const uint8_t, filt4_global_avx2[32]) = {
  6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13, 14,
  6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13, 14
};

static INLINE void vpx_filter_block1d16_h8_x_avx2(
    const uint8_t *src_ptr, ptrdiff_t src_pixels_per_line, uint8_t *output_ptr,
    ptrdiff_t output_pitch, uint32_t output_height, const int16_t *filter,
    const int avg) {
  __m128i outReg1, outReg2;
  __m256i outReg32b1, outReg32b2;
  unsigned int i;
  ptrdiff_t src_stride, dst_stride;
  __m256i f[4], filt[4], s[4];

  shuffle_filter_avx2(filter, f);
  filt[0] = _mm256_load_si256((__m256i const *)filt1_global_avx2);
  filt[1] = _mm256_load_si256((__m256i const *)filt2_global_avx2);
  filt[2] = _mm256_load_si256((__m256i const *)filt3_global_avx2);
  filt[3] = _mm256_load_si256((__m256i const *)filt4_global_avx2);

  // multiple the size of the source and destination stride by two
  src_stride = src_pixels_per_line << 1;
  dst_stride = output_pitch << 1;
  for (i = output_height; i > 1; i -= 2) {
    __m256i srcReg;

    // load the 2 strides of source
    srcReg =
        _mm256_castsi128_si256(_mm_loadu_si128((const __m128i *)(src_ptr - 3)));
    srcReg = _mm256_inserti128_si256(
        srcReg,
        _mm_loadu_si128((const __m128i *)(src_ptr + src_pixels_per_line - 3)),
        1);

    // filter the source buffer
    s[0] = _mm256_shuffle_epi8(srcReg, filt[0]);
    s[1] = _mm256_shuffle_epi8(srcReg, filt[1]);
    s[2] = _mm256_shuffle_epi8(srcReg, filt[2]);
    s[3] = _mm256_shuffle_epi8(srcReg, filt[3]);
    outReg32b1 = convolve8_16_avx2(s, f);

    // reading 2 strides of the next 16 bytes
    // (part of it was being read by earlier read)
    srcReg =
        _mm256_castsi128_si256(_mm_loadu_si128((const __m128i *)(src_ptr + 5)));
    srcReg = _mm256_inserti128_si256(
        srcReg,
        _mm_loadu_si128((const __m128i *)(src_ptr + src_pixels_per_line + 5)),
        1);

    // filter the source buffer
    s[0] = _mm256_shuffle_epi8(srcReg, filt[0]);
    s[1] = _mm256_shuffle_epi8(srcReg, filt[1]);
    s[2] = _mm256_shuffle_epi8(srcReg, filt[2]);
    s[3] = _mm256_shuffle_epi8(srcReg, filt[3]);
    outReg32b2 = convolve8_16_avx2(s, f);

    // shrink to 8 bit each 16 bits, the low and high 64-bits of each lane
    // contain the first and second convolve result respectively
    outReg32b1 = _mm256_packus_epi16(outReg32b1, outReg32b2);

    src_ptr += src_stride;

    // average if necessary
    outReg1 = _mm256_castsi256_si128(outReg32b1);
    outReg2 = _mm256_extractf128_si256(outReg32b1, 1);
    if (avg) {
      outReg1 = _mm_avg_epu8(outReg1, _mm_load_si128((__m128i *)output_ptr));
      outReg2 = _mm_avg_epu8(
          outReg2, _mm_load_si128((__m128i *)(output_ptr + output_pitch)));
    }

    // save 16 bytes
    _mm_store_si128((__m128i *)output_ptr, outReg1);

    // save the next 16 bits
    _mm_store_si128((__m128i *)(output_ptr + output_pitch), outReg2);

    output_ptr += dst_stride;
  }

  // if the number of strides is odd.
  // process only 16 bytes
  if (i > 0) {
    __m128i srcReg;

    // load the first 16 bytes of the last row
    srcReg = _mm_loadu_si128((const __m128i *)(src_ptr - 3));

    // filter the source buffer
    s[0] = _mm256_castsi128_si256(
        _mm_shuffle_epi8(srcReg, _mm256_castsi256_si128(filt[0])));
    s[1] = _mm256_castsi128_si256(
        _mm_shuffle_epi8(srcReg, _mm256_castsi256_si128(filt[1])));
    s[2] = _mm256_castsi128_si256(
        _mm_shuffle_epi8(srcReg, _mm256_castsi256_si128(filt[2])));
    s[3] = _mm256_castsi128_si256(
        _mm_shuffle_epi8(srcReg, _mm256_castsi256_si128(filt[3])));
    outReg1 = convolve8_8_avx2(s, f);

    // reading the next 16 bytes
    // (part of it was being read by earlier read)
    srcReg = _mm_loadu_si128((const __m128i *)(src_ptr + 5));

    // filter the source buffer
    s[0] = _mm256_castsi128_si256(
        _mm_shuffle_epi8(srcReg, _mm256_castsi256_si128(filt[0])));
    s[1] = _mm256_castsi128_si256(
        _mm_shuffle_epi8(srcReg, _mm256_castsi256_si128(filt[1])));
    s[2] = _mm256_castsi128_si256(
        _mm_shuffle_epi8(srcReg, _mm256_castsi256_si128(filt[2])));
    s[3] = _mm256_castsi128_si256(
        _mm_shuffle_epi8(srcReg, _mm256_castsi256_si128(filt[3])));
    outReg2 = convolve8_8_avx2(s, f);

    // shrink to 8 bit each 16 bits, the low and high 64-bits of each lane
    // contain the first and second convolve result respectively
    outReg1 = _mm_packus_epi16(outReg1, outReg2);

    // average if necessary
    if (avg) {
      outReg1 = _mm_avg_epu8(outReg1, _mm_load_si128((__m128i *)output_ptr));
    }

    // save 16 bytes
    _mm_store_si128((__m128i *)output_ptr, outReg1);
  }
}

static void vpx_filter_block1d16_h8_avx2(
    const uint8_t *src_ptr, ptrdiff_t src_stride, uint8_t *output_ptr,
    ptrdiff_t dst_stride, uint32_t output_height, const int16_t *filter) {
  vpx_filter_block1d16_h8_x_avx2(src_ptr, src_stride, output_ptr, dst_stride,
                                 output_height, filter, 0);
}

static void vpx_filter_block1d16_h8_avg_avx2(
    const uint8_t *src_ptr, ptrdiff_t src_stride, uint8_t *output_ptr,
    ptrdiff_t dst_stride, uint32_t output_height, const int16_t *filter) {
  vpx_filter_block1d16_h8_x_avx2(src_ptr, src_stride, output_ptr, dst_stride,
                                 output_height, filter, 1);
}

static INLINE void vpx_filter_block1d16_v8_x_avx2(
    const uint8_t *src_ptr, ptrdiff_t src_pitch, uint8_t *output_ptr,
    ptrdiff_t out_pitch, uint32_t output_height, const int16_t *filter,
    const int avg) {
  __m128i outReg1, outReg2;
  __m256i srcRegHead1;
  unsigned int i;
  ptrdiff_t src_stride, dst_stride;
  __m256i f[4], s1[4], s2[4];

  shuffle_filter_avx2(filter, f);

  // multiple the size of the source and destination stride by two
  src_stride = src_pitch << 1;
  dst_stride = out_pitch << 1;

  {
    __m128i s[6];
    __m256i s32b[6];

    // load 16 bytes 7 times in stride of src_pitch
    s[0] = _mm_loadu_si128((const __m128i *)(src_ptr + 0 * src_pitch));
    s[1] = _mm_loadu_si128((const __m128i *)(src_ptr + 1 * src_pitch));
    s[2] = _mm_loadu_si128((const __m128i *)(src_ptr + 2 * src_pitch));
    s[3] = _mm_loadu_si128((const __m128i *)(src_ptr + 3 * src_pitch));
    s[4] = _mm_loadu_si128((const __m128i *)(src_ptr + 4 * src_pitch));
    s[5] = _mm_loadu_si128((const __m128i *)(src_ptr + 5 * src_pitch));
    srcRegHead1 = _mm256_castsi128_si256(
        _mm_loadu_si128((const __m128i *)(src_ptr + 6 * src_pitch)));

    // have each consecutive loads on the same 256 register
    s32b[0] = _mm256_inserti128_si256(_mm256_castsi128_si256(s[0]), s[1], 1);
    s32b[1] = _mm256_inserti128_si256(_mm256_castsi128_si256(s[1]), s[2], 1);
    s32b[2] = _mm256_inserti128_si256(_mm256_castsi128_si256(s[2]), s[3], 1);
    s32b[3] = _mm256_inserti128_si256(_mm256_castsi128_si256(s[3]), s[4], 1);
    s32b[4] = _mm256_inserti128_si256(_mm256_castsi128_si256(s[4]), s[5], 1);
    s32b[5] = _mm256_inserti128_si256(_mm256_castsi128_si256(s[5]),
                                      _mm256_castsi256_si128(srcRegHead1), 1);

    // merge every two consecutive registers except the last one
    // the first lanes contain values for filtering odd rows (1,3,5...) and
    // the second lanes contain values for filtering even rows (2,4,6...)
    s1[0] = _mm256_unpacklo_epi8(s32b[0], s32b[1]);
    s2[0] = _mm256_unpackhi_epi8(s32b[0], s32b[1]);
    s1[1] = _mm256_unpacklo_epi8(s32b[2], s32b[3]);
    s2[1] = _mm256_unpackhi_epi8(s32b[2], s32b[3]);
    s1[2] = _mm256_unpacklo_epi8(s32b[4], s32b[5]);
    s2[2] = _mm256_unpackhi_epi8(s32b[4], s32b[5]);
  }

  for (i = output_height; i > 1; i -= 2) {
    __m256i srcRegHead2, srcRegHead3;

    // load the next 2 loads of 16 bytes and have every two
    // consecutive loads in the same 256 bit register
    srcRegHead2 = _mm256_castsi128_si256(
        _mm_loadu_si128((const __m128i *)(src_ptr + 7 * src_pitch)));
    srcRegHead1 = _mm256_inserti128_si256(
        srcRegHead1, _mm256_castsi256_si128(srcRegHead2), 1);
    srcRegHead3 = _mm256_castsi128_si256(
        _mm_loadu_si128((const __m128i *)(src_ptr + 8 * src_pitch)));
    srcRegHead2 = _mm256_inserti128_si256(
        srcRegHead2, _mm256_castsi256_si128(srcRegHead3), 1);

    // merge the two new consecutive registers
    // the first lane contain values for filtering odd rows (1,3,5...) and
    // the second lane contain values for filtering even rows (2,4,6...)
    s1[3] = _mm256_unpacklo_epi8(srcRegHead1, srcRegHead2);
    s2[3] = _mm256_unpackhi_epi8(srcRegHead1, srcRegHead2);

    s1[0] = convolve8_16_avx2(s1, f);
    s2[0] = convolve8_16_avx2(s2, f);

    // shrink to 8 bit each 16 bits, the low and high 64-bits of each lane
    // contain the first and second convolve result respectively
    s1[0] = _mm256_packus_epi16(s1[0], s2[0]);

    src_ptr += src_stride;

    // average if necessary
    outReg1 = _mm256_castsi256_si128(s1[0]);
    outReg2 = _mm256_extractf128_si256(s1[0], 1);
    if (avg) {
      outReg1 = _mm_avg_epu8(outReg1, _mm_load_si128((__m128i *)output_ptr));
      outReg2 = _mm_avg_epu8(
          outReg2, _mm_load_si128((__m128i *)(output_ptr + out_pitch)));
    }

    // save 16 bytes
    _mm_store_si128((__m128i *)output_ptr, outReg1);

    // save the next 16 bits
    _mm_store_si128((__m128i *)(output_ptr + out_pitch), outReg2);

    output_ptr += dst_stride;

    // shift down by two rows
    s1[0] = s1[1];
    s2[0] = s2[1];
    s1[1] = s1[2];
    s2[1] = s2[2];
    s1[2] = s1[3];
    s2[2] = s2[3];
    srcRegHead1 = srcRegHead3;
  }

  // if the number of strides is odd.
  // process only 16 bytes
  if (i > 0) {
    // load the last 16 bytes
    const __m128i srcRegHead2 =
        _mm_loadu_si128((const __m128i *)(src_ptr + src_pitch * 7));

    // merge the last 2 results together
    s1[0] = _mm256_castsi128_si256(
        _mm_unpacklo_epi8(_mm256_castsi256_si128(srcRegHead1), srcRegHead2));
    s2[0] = _mm256_castsi128_si256(
        _mm_unpackhi_epi8(_mm256_castsi256_si128(srcRegHead1), srcRegHead2));

    outReg1 = convolve8_8_avx2(s1, f);
    outReg2 = convolve8_8_avx2(s2, f);

    // shrink to 8 bit each 16 bits, the low and high 64-bits of each lane
    // contain the first and second convolve result respectively
    outReg1 = _mm_packus_epi16(outReg1, outReg2);

    // average if necessary
    if (avg) {
      outReg1 = _mm_avg_epu8(outReg1, _mm_load_si128((__m128i *)output_ptr));
    }

    // save 16 bytes
    _mm_store_si128((__m128i *)output_ptr, outReg1);
  }
}

static void vpx_filter_block1d16_v8_avx2(const uint8_t *src_ptr,
                                         ptrdiff_t src_stride, uint8_t *dst_ptr,
                                         ptrdiff_t dst_stride, uint32_t height,
                                         const int16_t *filter) {
  vpx_filter_block1d16_v8_x_avx2(src_ptr, src_stride, dst_ptr, dst_stride,
                                 height, filter, 0);
}

static void vpx_filter_block1d16_v8_avg_avx2(
    const uint8_t *src_ptr, ptrdiff_t src_stride, uint8_t *dst_ptr,
    ptrdiff_t dst_stride, uint32_t height, const int16_t *filter) {
  vpx_filter_block1d16_v8_x_avx2(src_ptr, src_stride, dst_ptr, dst_stride,
                                 height, filter, 1);
}

static void vpx_filter_block1d16_h4_avx2(const uint8_t *src_ptr,
                                         ptrdiff_t src_stride, uint8_t *dst_ptr,
                                         ptrdiff_t dst_stride, uint32_t height,
                                         const int16_t *kernel) {
  // We will cast the kernel from 16-bit words to 8-bit words, and then extract
  // the middle four elements of the kernel into two registers in the form
  // ... k[3] k[2] k[3] k[2]
  // ... k[5] k[4] k[5] k[4]
  // Then we shuffle the source into
  // ... s[1] s[0] s[0] s[-1]
  // ... s[3] s[2] s[2] s[1]
  // Calling multiply and add gives us half of the sum. Calling add gives us
  // first half of the output. Repeat again to get the second half of the
  // output. Finally we shuffle again to combine the two outputs.
  // Since avx2 allows us to use 256-bit buffer, we can do this two rows at a
  // time.

  __m128i kernel_reg;  // Kernel
  __m256i kernel_reg_256, kernel_reg_23,
      kernel_reg_45;                             // Segments of the kernel used
  const __m256i reg_32 = _mm256_set1_epi16(32);  // Used for rounding
  const ptrdiff_t unrolled_src_stride = src_stride << 1;
  const ptrdiff_t unrolled_dst_stride = dst_stride << 1;
  int h;

  __m256i src_reg, src_reg_shift_0, src_reg_shift_2;
  __m256i dst_first, dst_second;
  __m256i tmp_0, tmp_1;
  __m256i idx_shift_0 =
      _mm256_setr_epi8(0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 0, 1, 1,
                       2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8);
  __m256i idx_shift_2 =
      _mm256_setr_epi8(2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 2, 3, 3,
                       4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10);

  // Start one pixel before as we need tap/2 - 1 = 1 sample from the past
  src_ptr -= 1;

  // Load Kernel
  kernel_reg = _mm_loadu_si128((const __m128i *)kernel);
  kernel_reg = _mm_srai_epi16(kernel_reg, 1);
  kernel_reg = _mm_packs_epi16(kernel_reg, kernel_reg);
  kernel_reg_256 = _mm256_broadcastsi128_si256(kernel_reg);
  kernel_reg_23 =
      _mm256_shuffle_epi8(kernel_reg_256, _mm256_set1_epi16(0x0302u));
  kernel_reg_45 =
      _mm256_shuffle_epi8(kernel_reg_256, _mm256_set1_epi16(0x0504u));

  for (h = height; h >= 2; h -= 2) {
    // Load the source
    src_reg = mm256_loadu2_si128(src_ptr, src_ptr + src_stride);
    src_reg_shift_0 = _mm256_shuffle_epi8(src_reg, idx_shift_0);
    src_reg_shift_2 = _mm256_shuffle_epi8(src_reg, idx_shift_2);

    // Partial result for first half
    tmp_0 = _mm256_maddubs_epi16(src_reg_shift_0, kernel_reg_23);
    tmp_1 = _mm256_maddubs_epi16(src_reg_shift_2, kernel_reg_45);
    dst_first = _mm256_adds_epi16(tmp_0, tmp_1);

    // Do again to get the second half of dst
    // Load the source
    src_reg = mm256_loadu2_si128(src_ptr + 8, src_ptr + src_stride + 8);
    src_reg_shift_0 = _mm256_shuffle_epi8(src_reg, idx_shift_0);
    src_reg_shift_2 = _mm256_shuffle_epi8(src_reg, idx_shift_2);

    // Partial result for second half
    tmp_0 = _mm256_maddubs_epi16(src_reg_shift_0, kernel_reg_23);
    tmp_1 = _mm256_maddubs_epi16(src_reg_shift_2, kernel_reg_45);
    dst_second = _mm256_adds_epi16(tmp_0, tmp_1);

    // Round each result
    dst_first = mm256_round_epi16(&dst_first, &reg_32, 6);
    dst_second = mm256_round_epi16(&dst_second, &reg_32, 6);

    // Finally combine to get the final dst
    dst_first = _mm256_packus_epi16(dst_first, dst_second);
    mm256_store2_si128((__m128i *)dst_ptr, (__m128i *)(dst_ptr + dst_stride),
                       &dst_first);

    src_ptr += unrolled_src_stride;
    dst_ptr += unrolled_dst_stride;
  }

  // Repeat for the last row if needed
  if (h > 0) {
    src_reg = _mm256_loadu_si256((const __m256i *)src_ptr);
    // Reorder into 2 1 1 2
    src_reg = _mm256_permute4x64_epi64(src_reg, 0x94);

    src_reg_shift_0 = _mm256_shuffle_epi8(src_reg, idx_shift_0);
    src_reg_shift_2 = _mm256_shuffle_epi8(src_reg, idx_shift_2);

    tmp_0 = _mm256_maddubs_epi16(src_reg_shift_0, kernel_reg_23);
    tmp_1 = _mm256_maddubs_epi16(src_reg_shift_2, kernel_reg_45);
    dst_first = _mm256_adds_epi16(tmp_0, tmp_1);

    dst_first = mm256_round_epi16(&dst_first, &reg_32, 6);

    dst_first = _mm256_packus_epi16(dst_first, dst_first);
    dst_first = _mm256_permute4x64_epi64(dst_first, 0x8);

    _mm_store_si128((__m128i *)dst_ptr, _mm256_castsi256_si128(dst_first));
  }
}

static void vpx_filter_block1d16_v4_avx2(const uint8_t *src_ptr,
                                         ptrdiff_t src_stride, uint8_t *dst_ptr,
                                         ptrdiff_t dst_stride, uint32_t height,
                                         const int16_t *kernel) {
  // We will load two rows of pixels as 8-bit words, rearrange them into the
  // form
  // ... s[1,0] s[0,0] s[0,0] s[-1,0]
  // so that we can call multiply and add with the kernel partial output. Then
  // we can call add with another row to get the output.

  // Register for source s[-1:3, :]
  __m256i src_reg_1, src_reg_2, src_reg_3;
  // Interleaved rows of the source. lo is first half, hi second
  __m256i src_reg_m10, src_reg_01, src_reg_12, src_reg_23;
  __m256i src_reg_m1001_lo, src_reg_m1001_hi, src_reg_1223_lo, src_reg_1223_hi;

  __m128i kernel_reg;  // Kernel
  __m256i kernel_reg_256, kernel_reg_23,
      kernel_reg_45;  // Segments of the kernel used

  // Result after multiply and add
  __m256i res_reg_m1001_lo, res_reg_1223_lo, res_reg_m1001_hi, res_reg_1223_hi;
  __m256i res_reg, res_reg_lo, res_reg_hi;

  const __m256i reg_32 = _mm256_set1_epi16(32);  // Used for rounding

  // We will compute the result two rows at a time
  const ptrdiff_t src_stride_unrolled = src_stride << 1;
  const ptrdiff_t dst_stride_unrolled = dst_stride << 1;
  int h;

  // Load Kernel
  kernel_reg = _mm_loadu_si128((const __m128i *)kernel);
  kernel_reg = _mm_srai_epi16(kernel_reg, 1);
  kernel_reg = _mm_packs_epi16(kernel_reg, kernel_reg);
  kernel_reg_256 = _mm256_broadcastsi128_si256(kernel_reg);
  kernel_reg_23 =
      _mm256_shuffle_epi8(kernel_reg_256, _mm256_set1_epi16(0x0302u));
  kernel_reg_45 =
      _mm256_shuffle_epi8(kernel_reg_256, _mm256_set1_epi16(0x0504u));

  // Row -1 to row 0
  src_reg_m10 = mm256_loadu2_si128((const __m128i *)src_ptr,
                                   (const __m128i *)(src_ptr + src_stride));

  // Row 0 to row 1
  src_reg_1 = _mm256_castsi128_si256(
      _mm_loadu_si128((const __m128i *)(src_ptr + src_stride * 2)));
  src_reg_01 = _mm256_permute2x128_si256(src_reg_m10, src_reg_1, 0x21);

  // First three rows
  src_reg_m1001_lo = _mm256_unpacklo_epi8(src_reg_m10, src_reg_01);
  src_reg_m1001_hi = _mm256_unpackhi_epi8(src_reg_m10, src_reg_01);

  for (h = height; h > 1; h -= 2) {
    src_reg_2 = _mm256_castsi128_si256(
        _mm_loadu_si128((const __m128i *)(src_ptr + src_stride * 3)));

    src_reg_12 = _mm256_inserti128_si256(src_reg_1,
                                         _mm256_castsi256_si128(src_reg_2), 1);

    src_reg_3 = _mm256_castsi128_si256(
        _mm_loadu_si128((const __m128i *)(src_ptr + src_stride * 4)));

    src_reg_23 = _mm256_inserti128_si256(src_reg_2,
                                         _mm256_castsi256_si128(src_reg_3), 1);

    // Last three rows
    src_reg_1223_lo = _mm256_unpacklo_epi8(src_reg_12, src_reg_23);
    src_reg_1223_hi = _mm256_unpackhi_epi8(src_reg_12, src_reg_23);

    // Output from first half
    res_reg_m1001_lo = _mm256_maddubs_epi16(src_reg_m1001_lo, kernel_reg_23);
    res_reg_1223_lo = _mm256_maddubs_epi16(src_reg_1223_lo, kernel_reg_45);
    res_reg_lo = _mm256_adds_epi16(res_reg_m1001_lo, res_reg_1223_lo);

    // Output from second half
    res_reg_m1001_hi = _mm256_maddubs_epi16(src_reg_m1001_hi, kernel_reg_23);
    res_reg_1223_hi = _mm256_maddubs_epi16(src_reg_1223_hi, kernel_reg_45);
    res_reg_hi = _mm256_adds_epi16(res_reg_m1001_hi, res_reg_1223_hi);

    // Round the words
    res_reg_lo = mm256_round_epi16(&res_reg_lo, &reg_32, 6);
    res_reg_hi = mm256_round_epi16(&res_reg_hi, &reg_32, 6);

    // Combine to get the result
    res_reg = _mm256_packus_epi16(res_reg_lo, res_reg_hi);

    // Save the result
    mm256_store2_si128((__m128i *)dst_ptr, (__m128i *)(dst_ptr + dst_stride),
                       &res_reg);

    // Update the source by two rows
    src_ptr += src_stride_unrolled;
    dst_ptr += dst_stride_unrolled;

    src_reg_m1001_lo = src_reg_1223_lo;
    src_reg_m1001_hi = src_reg_1223_hi;
    src_reg_1 = src_reg_3;
  }
}

static void vpx_filter_block1d8_h4_avx2(const uint8_t *src_ptr,
                                        ptrdiff_t src_stride, uint8_t *dst_ptr,
                                        ptrdiff_t dst_stride, uint32_t height,
                                        const int16_t *kernel) {
  // We will cast the kernel from 16-bit words to 8-bit words, and then extract
  // the middle four elements of the kernel into two registers in the form
  // ... k[3] k[2] k[3] k[2]
  // ... k[5] k[4] k[5] k[4]
  // Then we shuffle the source into
  // ... s[1] s[0] s[0] s[-1]
  // ... s[3] s[2] s[2] s[1]
  // Calling multiply and add gives us half of the sum. Calling add gives us
  // first half of the output. Repeat again to get the second half of the
  // output. Finally we shuffle again to combine the two outputs.
  // Since avx2 allows us to use 256-bit buffer, we can do this two rows at a
  // time.

  __m128i kernel_reg_128;  // Kernel
  __m256i kernel_reg, kernel_reg_23,
      kernel_reg_45;                             // Segments of the kernel used
  const __m256i reg_32 = _mm256_set1_epi16(32);  // Used for rounding
  const ptrdiff_t unrolled_src_stride = src_stride << 1;
  const ptrdiff_t unrolled_dst_stride = dst_stride << 1;
  int h;

  __m256i src_reg, src_reg_shift_0, src_reg_shift_2;
  __m256i dst_reg;
  __m256i tmp_0, tmp_1;
  __m256i idx_shift_0 =
      _mm256_setr_epi8(0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 0, 1, 1,
                       2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8);
  __m256i idx_shift_2 =
      _mm256_setr_epi8(2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 2, 3, 3,
                       4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10);

  // Start one pixel before as we need tap/2 - 1 = 1 sample from the past
  src_ptr -= 1;

  // Load Kernel
  kernel_reg_128 = _mm_loadu_si128((const __m128i *)kernel);
  kernel_reg_128 = _mm_srai_epi16(kernel_reg_128, 1);
  kernel_reg_128 = _mm_packs_epi16(kernel_reg_128, kernel_reg_128);
  kernel_reg = _mm256_broadcastsi128_si256(kernel_reg_128);
  kernel_reg_23 = _mm256_shuffle_epi8(kernel_reg, _mm256_set1_epi16(0x0302u));
  kernel_reg_45 = _mm256_shuffle_epi8(kernel_reg, _mm256_set1_epi16(0x0504u));

  for (h = height; h >= 2; h -= 2) {
    // Load the source
    src_reg = mm256_loadu2_si128(src_ptr, src_ptr + src_stride);
    src_reg_shift_0 = _mm256_shuffle_epi8(src_reg, idx_shift_0);
    src_reg_shift_2 = _mm256_shuffle_epi8(src_reg, idx_shift_2);

    // Get the output
    tmp_0 = _mm256_maddubs_epi16(src_reg_shift_0, kernel_reg_23);
    tmp_1 = _mm256_maddubs_epi16(src_reg_shift_2, kernel_reg_45);
    dst_reg = _mm256_adds_epi16(tmp_0, tmp_1);

    // Round the result
    dst_reg = mm256_round_epi16(&dst_reg, &reg_32, 6);

    // Finally combine to get the final dst
    dst_reg = _mm256_packus_epi16(dst_reg, dst_reg);
    mm256_storeu2_epi64((__m128i *)dst_ptr, (__m128i *)(dst_ptr + dst_stride),
                        &dst_reg);

    src_ptr += unrolled_src_stride;
    dst_ptr += unrolled_dst_stride;
  }

  // Repeat for the last row if needed
  if (h > 0) {
    __m128i src_reg = _mm_loadu_si128((const __m128i *)src_ptr);
    __m128i dst_reg;
    const __m128i reg_32 = _mm_set1_epi16(32);  // Used for rounding
    __m128i tmp_0, tmp_1;

    __m128i src_reg_shift_0 =
        _mm_shuffle_epi8(src_reg, _mm256_castsi256_si128(idx_shift_0));
    __m128i src_reg_shift_2 =
        _mm_shuffle_epi8(src_reg, _mm256_castsi256_si128(idx_shift_2));

    tmp_0 = _mm_maddubs_epi16(src_reg_shift_0,
                              _mm256_castsi256_si128(kernel_reg_23));
    tmp_1 = _mm_maddubs_epi16(src_reg_shift_2,
                              _mm256_castsi256_si128(kernel_reg_45));
    dst_reg = _mm_adds_epi16(tmp_0, tmp_1);

    dst_reg = mm_round_epi16_sse2(&dst_reg, &reg_32, 6);

    dst_reg = _mm_packus_epi16(dst_reg, _mm_setzero_si128());

    _mm_storel_epi64((__m128i *)dst_ptr, dst_reg);
  }
}

static void vpx_filter_block1d8_v4_avx2(const uint8_t *src_ptr,
                                        ptrdiff_t src_stride, uint8_t *dst_ptr,
                                        ptrdiff_t dst_stride, uint32_t height,
                                        const int16_t *kernel) {
  // We will load two rows of pixels as 8-bit words, rearrange them into the
  // form
  // ... s[1,0] s[0,0] s[0,0] s[-1,0]
  // so that we can call multiply and add with the kernel partial output. Then
  // we can call add with another row to get the output.

  // Register for source s[-1:3, :]
  __m256i src_reg_1, src_reg_2, src_reg_3;
  // Interleaved rows of the source. lo is first half, hi second
  __m256i src_reg_m10, src_reg_01, src_reg_12, src_reg_23;
  __m256i src_reg_m1001, src_reg_1223;

  __m128i kernel_reg_128;  // Kernel
  __m256i kernel_reg, kernel_reg_23,
      kernel_reg_45;  // Segments of the kernel used

  // Result after multiply and add
  __m256i res_reg_m1001, res_reg_1223;
  __m256i res_reg;

  const __m256i reg_32 = _mm256_set1_epi16(32);  // Used for rounding

  // We will compute the result two rows at a time
  const ptrdiff_t src_stride_unrolled = src_stride << 1;
  const ptrdiff_t dst_stride_unrolled = dst_stride << 1;
  int h;

  // Load Kernel
  kernel_reg_128 = _mm_loadu_si128((const __m128i *)kernel);
  kernel_reg_128 = _mm_srai_epi16(kernel_reg_128, 1);
  kernel_reg_128 = _mm_packs_epi16(kernel_reg_128, kernel_reg_128);
  kernel_reg = _mm256_broadcastsi128_si256(kernel_reg_128);
  kernel_reg_23 = _mm256_shuffle_epi8(kernel_reg, _mm256_set1_epi16(0x0302u));
  kernel_reg_45 = _mm256_shuffle_epi8(kernel_reg, _mm256_set1_epi16(0x0504u));

  // Row -1 to row 0
  src_reg_m10 = mm256_loadu2_epi64((const __m128i *)src_ptr,
                                   (const __m128i *)(src_ptr + src_stride));

  // Row 0 to row 1
  src_reg_1 = _mm256_castsi128_si256(
      _mm_loadu_si128((const __m128i *)(src_ptr + src_stride * 2)));
  src_reg_01 = _mm256_permute2x128_si256(src_reg_m10, src_reg_1, 0x21);

  // First three rows
  src_reg_m1001 = _mm256_unpacklo_epi8(src_reg_m10, src_reg_01);

  for (h = height; h > 1; h -= 2) {
    src_reg_2 = _mm256_castsi128_si256(
        _mm_loadl_epi64((const __m128i *)(src_ptr + src_stride * 3)));

    src_reg_12 = _mm256_inserti128_si256(src_reg_1,
                                         _mm256_castsi256_si128(src_reg_2), 1);

    src_reg_3 = _mm256_castsi128_si256(
        _mm_loadl_epi64((const __m128i *)(src_ptr + src_stride * 4)));

    src_reg_23 = _mm256_inserti128_si256(src_reg_2,
                                         _mm256_castsi256_si128(src_reg_3), 1);

    // Last three rows
    src_reg_1223 = _mm256_unpacklo_epi8(src_reg_12, src_reg_23);

    // Output
    res_reg_m1001 = _mm256_maddubs_epi16(src_reg_m1001, kernel_reg_23);
    res_reg_1223 = _mm256_maddubs_epi16(src_reg_1223, kernel_reg_45);
    res_reg = _mm256_adds_epi16(res_reg_m1001, res_reg_1223);

    // Round the words
    res_reg = mm256_round_epi16(&res_reg, &reg_32, 6);

    // Combine to get the result
    res_reg = _mm256_packus_epi16(res_reg, res_reg);

    // Save the result
    mm256_storeu2_epi64((__m128i *)dst_ptr, (__m128i *)(dst_ptr + dst_stride),
                        &res_reg);

    // Update the source by two rows
    src_ptr += src_stride_unrolled;
    dst_ptr += dst_stride_unrolled;

    src_reg_m1001 = src_reg_1223;
    src_reg_1 = src_reg_3;
  }
}

static void vpx_filter_block1d4_h4_avx2(const uint8_t *src_ptr,
                                        ptrdiff_t src_stride, uint8_t *dst_ptr,
                                        ptrdiff_t dst_stride, uint32_t height,
                                        const int16_t *kernel) {
  // We will cast the kernel from 16-bit words to 8-bit words, and then extract
  // the middle four elements of the kernel into a single register in the form
  // k[5:2] k[5:2] k[5:2] k[5:2]
  // Then we shuffle the source into
  // s[5:2] s[4:1] s[3:0] s[2:-1]
  // Calling multiply and add gives us half of the sum next to each other.
  // Calling horizontal add then gives us the output.
  // Since avx2 has 256-bit register, we can do 2 rows at a time.

  __m128i kernel_reg_128;  // Kernel
  __m256i kernel_reg;
  const __m256i reg_32 = _mm256_set1_epi16(32);  // Used for rounding
  int h;
  const ptrdiff_t unrolled_src_stride = src_stride << 1;
  const ptrdiff_t unrolled_dst_stride = dst_stride << 1;

  __m256i src_reg, src_reg_shuf;
  __m256i dst;
  __m256i shuf_idx =
      _mm256_setr_epi8(0, 1, 2, 3, 1, 2, 3, 4, 2, 3, 4, 5, 3, 4, 5, 6, 0, 1, 2,
                       3, 1, 2, 3, 4, 2, 3, 4, 5, 3, 4, 5, 6);

  // Start one pixel before as we need tap/2 - 1 = 1 sample from the past
  src_ptr -= 1;

  // Load Kernel
  kernel_reg_128 = _mm_loadu_si128((const __m128i *)kernel);
  kernel_reg_128 = _mm_srai_epi16(kernel_reg_128, 1);
  kernel_reg_128 = _mm_packs_epi16(kernel_reg_128, kernel_reg_128);
  kernel_reg = _mm256_broadcastsi128_si256(kernel_reg_128);
  kernel_reg = _mm256_shuffle_epi8(kernel_reg, _mm256_set1_epi32(0x05040302u));

  for (h = height; h > 1; h -= 2) {
    // Load the source
    src_reg = mm256_loadu2_epi64((const __m128i *)src_ptr,
                                 (const __m128i *)(src_ptr + src_stride));
    src_reg_shuf = _mm256_shuffle_epi8(src_reg, shuf_idx);

    // Get the result
    dst = _mm256_maddubs_epi16(src_reg_shuf, kernel_reg);
    dst = _mm256_hadds_epi16(dst, _mm256_setzero_si256());

    // Round result
    dst = mm256_round_epi16(&dst, &reg_32, 6);

    // Pack to 8-bits
    dst = _mm256_packus_epi16(dst, _mm256_setzero_si256());

    // Save
    mm256_storeu2_epi32((__m128i *const)dst_ptr,
                        (__m128i *const)(dst_ptr + dst_stride), &dst);

    src_ptr += unrolled_src_stride;
    dst_ptr += unrolled_dst_stride;
  }

  if (h > 0) {
    // Load the source
    const __m128i reg_32 = _mm_set1_epi16(32);  // Used for rounding
    __m128i src_reg = _mm_loadl_epi64((const __m128i *)src_ptr);
    __m128i src_reg_shuf =
        _mm_shuffle_epi8(src_reg, _mm256_castsi256_si128(shuf_idx));

    // Get the result
    __m128i dst =
        _mm_maddubs_epi16(src_reg_shuf, _mm256_castsi256_si128(kernel_reg));
    dst = _mm_hadds_epi16(dst, _mm_setzero_si128());

    // Round result
    dst = mm_round_epi16_sse2(&dst, &reg_32, 6);

    // Pack to 8-bits
    dst = _mm_packus_epi16(dst, _mm_setzero_si128());
    *((uint32_t *)(dst_ptr)) = _mm_cvtsi128_si32(dst);
  }
}

static void vpx_filter_block1d4_v4_avx2(const uint8_t *src_ptr,
                                        ptrdiff_t src_stride, uint8_t *dst_ptr,
                                        ptrdiff_t dst_stride, uint32_t height,
                                        const int16_t *kernel) {
  // We will load two rows of pixels as 8-bit words, rearrange them into the
  // form
  // ... s[3,0] s[2,0] s[1,0] s[0,0] s[2,0] s[1,0] s[0,0] s[-1,0]
  // so that we can call multiply and add with the kernel to get partial output.
  // Calling horizontal add then gives us the completely output

  // Register for source s[-1:3, :]
  __m256i src_reg_1, src_reg_2, src_reg_3;
  // Interleaved rows of the source. lo is first half, hi second
  __m256i src_reg_m10, src_reg_01, src_reg_12, src_reg_23;
  __m256i src_reg_m1001, src_reg_1223, src_reg_m1012_1023;

  __m128i kernel_reg_128;  // Kernel
  __m256i kernel_reg;

  // Result after multiply and add
  __m256i res_reg;

  const __m256i reg_32 = _mm256_set1_epi16(32);  // Used for rounding

  // We will compute the result two rows at a time
  const ptrdiff_t src_stride_unrolled = src_stride << 1;
  const ptrdiff_t dst_stride_unrolled = dst_stride << 1;
  int h;

  // Load Kernel
  kernel_reg_128 = _mm_loadu_si128((const __m128i *)kernel);
  kernel_reg_128 = _mm_srai_epi16(kernel_reg_128, 1);
  kernel_reg_128 = _mm_packs_epi16(kernel_reg_128, kernel_reg_128);
  kernel_reg = _mm256_broadcastsi128_si256(kernel_reg_128);
  kernel_reg = _mm256_shuffle_epi8(kernel_reg, _mm256_set1_epi32(0x05040302u));

  // Row -1 to row 0
  src_reg_m10 = mm256_loadu2_si128((const __m128i *)src_ptr,
                                   (const __m128i *)(src_ptr + src_stride));

  // Row 0 to row 1
  src_reg_1 = _mm256_castsi128_si256(
      _mm_loadu_si128((const __m128i *)(src_ptr + src_stride * 2)));
  src_reg_01 = _mm256_permute2x128_si256(src_reg_m10, src_reg_1, 0x21);

  // First three rows
  src_reg_m1001 = _mm256_unpacklo_epi8(src_reg_m10, src_reg_01);

  for (h = height; h > 1; h -= 2) {
    src_reg_2 = _mm256_castsi128_si256(
        _mm_loadl_epi64((const __m128i *)(src_ptr + src_stride * 3)));

    src_reg_12 = _mm256_inserti128_si256(src_reg_1,
                                         _mm256_castsi256_si128(src_reg_2), 1);

    src_reg_3 = _mm256_castsi128_si256(
        _mm_loadl_epi64((const __m128i *)(src_ptr + src_stride * 4)));

    src_reg_23 = _mm256_inserti128_si256(src_reg_2,
                                         _mm256_castsi256_si128(src_reg_3), 1);

    // Last three rows
    src_reg_1223 = _mm256_unpacklo_epi8(src_reg_12, src_reg_23);

    // Combine all the rows
    src_reg_m1012_1023 = _mm256_unpacklo_epi16(src_reg_m1001, src_reg_1223);

    // Output
    res_reg = _mm256_maddubs_epi16(src_reg_m1012_1023, kernel_reg);
    res_reg = _mm256_hadds_epi16(res_reg, _mm256_setzero_si256());

    // Round the words
    res_reg = mm256_round_epi16(&res_reg, &reg_32, 6);

    // Combine to get the result
    res_reg = _mm256_packus_epi16(res_reg, res_reg);

    // Save the result
    mm256_storeu2_epi32((__m128i *)dst_ptr, (__m128i *)(dst_ptr + dst_stride),
                        &res_reg);

    // Update the source by two rows
    src_ptr += src_stride_unrolled;
    dst_ptr += dst_stride_unrolled;

    src_reg_m1001 = src_reg_1223;
    src_reg_1 = src_reg_3;
  }
}

#if HAVE_AVX2 && HAVE_SSSE3
filter8_1dfunction vpx_filter_block1d4_v8_ssse3;
#if VPX_ARCH_X86_64
filter8_1dfunction vpx_filter_block1d8_v8_intrin_ssse3;
filter8_1dfunction vpx_filter_block1d8_h8_intrin_ssse3;
filter8_1dfunction vpx_filter_block1d4_h8_intrin_ssse3;
#define vpx_filter_block1d8_v8_avx2 vpx_filter_block1d8_v8_intrin_ssse3
#define vpx_filter_block1d8_h8_avx2 vpx_filter_block1d8_h8_intrin_ssse3
#define vpx_filter_block1d4_h8_avx2 vpx_filter_block1d4_h8_intrin_ssse3
#else  // VPX_ARCH_X86
filter8_1dfunction vpx_filter_block1d8_v8_ssse3;
filter8_1dfunction vpx_filter_block1d8_h8_ssse3;
filter8_1dfunction vpx_filter_block1d4_h8_ssse3;
#define vpx_filter_block1d8_v8_avx2 vpx_filter_block1d8_v8_ssse3
#define vpx_filter_block1d8_h8_avx2 vpx_filter_block1d8_h8_ssse3
#define vpx_filter_block1d4_h8_avx2 vpx_filter_block1d4_h8_ssse3
#endif  // VPX_ARCH_X86_64
filter8_1dfunction vpx_filter_block1d8_v8_avg_ssse3;
filter8_1dfunction vpx_filter_block1d8_h8_avg_ssse3;
filter8_1dfunction vpx_filter_block1d4_v8_avg_ssse3;
filter8_1dfunction vpx_filter_block1d4_h8_avg_ssse3;
#define vpx_filter_block1d8_v8_avg_avx2 vpx_filter_block1d8_v8_avg_ssse3
#define vpx_filter_block1d8_h8_avg_avx2 vpx_filter_block1d8_h8_avg_ssse3
#define vpx_filter_block1d4_v8_avg_avx2 vpx_filter_block1d4_v8_avg_ssse3
#define vpx_filter_block1d4_h8_avg_avx2 vpx_filter_block1d4_h8_avg_ssse3
filter8_1dfunction vpx_filter_block1d16_v2_ssse3;
filter8_1dfunction vpx_filter_block1d16_h2_ssse3;
filter8_1dfunction vpx_filter_block1d8_v2_ssse3;
filter8_1dfunction vpx_filter_block1d8_h2_ssse3;
filter8_1dfunction vpx_filter_block1d4_v2_ssse3;
filter8_1dfunction vpx_filter_block1d4_h2_ssse3;
#define vpx_filter_block1d4_v8_avx2 vpx_filter_block1d4_v8_ssse3
#define vpx_filter_block1d16_v2_avx2 vpx_filter_block1d16_v2_ssse3
#define vpx_filter_block1d16_h2_avx2 vpx_filter_block1d16_h2_ssse3
#define vpx_filter_block1d8_v2_avx2 vpx_filter_block1d8_v2_ssse3
#define vpx_filter_block1d8_h2_avx2 vpx_filter_block1d8_h2_ssse3
#define vpx_filter_block1d4_v2_avx2 vpx_filter_block1d4_v2_ssse3
#define vpx_filter_block1d4_h2_avx2 vpx_filter_block1d4_h2_ssse3
filter8_1dfunction vpx_filter_block1d16_v2_avg_ssse3;
filter8_1dfunction vpx_filter_block1d16_h2_avg_ssse3;
filter8_1dfunction vpx_filter_block1d8_v2_avg_ssse3;
filter8_1dfunction vpx_filter_block1d8_h2_avg_ssse3;
filter8_1dfunction vpx_filter_block1d4_v2_avg_ssse3;
filter8_1dfunction vpx_filter_block1d4_h2_avg_ssse3;
#define vpx_filter_block1d16_v2_avg_avx2 vpx_filter_block1d16_v2_avg_ssse3
#define vpx_filter_block1d16_h2_avg_avx2 vpx_filter_block1d16_h2_avg_ssse3
#define vpx_filter_block1d8_v2_avg_avx2 vpx_filter_block1d8_v2_avg_ssse3
#define vpx_filter_block1d8_h2_avg_avx2 vpx_filter_block1d8_h2_avg_ssse3
#define vpx_filter_block1d4_v2_avg_avx2 vpx_filter_block1d4_v2_avg_ssse3
#define vpx_filter_block1d4_h2_avg_avx2 vpx_filter_block1d4_h2_avg_ssse3

#define vpx_filter_block1d16_v4_avg_avx2 vpx_filter_block1d16_v8_avg_avx2
#define vpx_filter_block1d16_h4_avg_avx2 vpx_filter_block1d16_h8_avg_avx2
#define vpx_filter_block1d8_v4_avg_avx2 vpx_filter_block1d8_v8_avg_avx2
#define vpx_filter_block1d8_h4_avg_avx2 vpx_filter_block1d8_h8_avg_avx2
#define vpx_filter_block1d4_v4_avg_avx2 vpx_filter_block1d4_v8_avg_avx2
#define vpx_filter_block1d4_h4_avg_avx2 vpx_filter_block1d4_h8_avg_avx2
// void vpx_convolve8_horiz_avx2(const uint8_t *src, ptrdiff_t src_stride,
//                                uint8_t *dst, ptrdiff_t dst_stride,
//                                const InterpKernel *filter, int x0_q4,
//                                int32_t x_step_q4, int y0_q4, int y_step_q4,
//                                int w, int h);
// void vpx_convolve8_vert_avx2(const uint8_t *src, ptrdiff_t src_stride,
//                               uint8_t *dst, ptrdiff_t dst_stride,
//                               const InterpKernel *filter, int x0_q4,
//                               int32_t x_step_q4, int y0_q4, int y_step_q4,
//                               int w, int h);
// void vpx_convolve8_avg_horiz_avx2(const uint8_t *src, ptrdiff_t src_stride,
//                                    uint8_t *dst, ptrdiff_t dst_stride,
//                                    const InterpKernel *filter, int x0_q4,
//                                    int32_t x_step_q4, int y0_q4,
//                                    int y_step_q4, int w, int h);
// void vpx_convolve8_avg_vert_avx2(const uint8_t *src, ptrdiff_t src_stride,
//                                   uint8_t *dst, ptrdiff_t dst_stride,
//                                   const InterpKernel *filter, int x0_q4,
//                                   int32_t x_step_q4, int y0_q4,
//                                   int y_step_q4, int w, int h);
FUN_CONV_1D(horiz, x0_q4, x_step_q4, h, src, , avx2, 0);
FUN_CONV_1D(vert, y0_q4, y_step_q4, v, src - src_stride * (num_taps / 2 - 1), ,
            avx2, 0);
FUN_CONV_1D(avg_horiz, x0_q4, x_step_q4, h, src, avg_, avx2, 1);
FUN_CONV_1D(avg_vert, y0_q4, y_step_q4, v,
            src - src_stride * (num_taps / 2 - 1), avg_, avx2, 1);

// void vpx_convolve8_avx2(const uint8_t *src, ptrdiff_t src_stride,
//                          uint8_t *dst, ptrdiff_t dst_stride,
//                          const InterpKernel *filter, int x0_q4,
//                          int32_t x_step_q4, int y0_q4, int y_step_q4,
//                          int w, int h);
// void vpx_convolve8_avg_avx2(const uint8_t *src, ptrdiff_t src_stride,
//                              uint8_t *dst, ptrdiff_t dst_stride,
//                              const InterpKernel *filter, int x0_q4,
//                              int32_t x_step_q4, int y0_q4, int y_step_q4,
//                              int w, int h);
FUN_CONV_2D(, avx2, 0);
FUN_CONV_2D(avg_, avx2, 1);
#endif  // HAVE_AX2 && HAVE_SSSE3
