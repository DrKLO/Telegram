/*
 *  Copyright 2011 The LibYuv Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS. All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "libyuv/rotate_row.h"
#include "libyuv/row.h"

#ifdef __cplusplus
namespace libyuv {
extern "C" {
#endif

// This module is for Mips MMI.
#if !defined(LIBYUV_DISABLE_MMI) && defined(_MIPS_ARCH_LOONGSON3A)

void TransposeWx8_MMI(const uint8_t* src,
                      int src_stride,
                      uint8_t* dst,
                      int dst_stride,
                      int width) {
  uint64_t tmp0, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6;
  uint64_t tmp7, tmp8, tmp9, tmp10, tmp11, tmp12, tmp13;
  uint8_t* src_tmp = nullptr;

  __asm__ volatile(
      "1:                                                           \n\t"
      "ldc1       %[tmp12],        0x00(%[src])                     \n\t"
      "dadd       %[src_tmp],      %[src],         %[src_stride]    \n\t"
      "ldc1       %[tmp13],        0x00(%[src_tmp])                 \n\t"

      /* tmp0 = (00 10 01 11 02 12 03 13) */
      "punpcklbh  %[tmp0],         %[tmp12],       %[tmp13]         \n\t"
      /* tmp1 = (04 14 05 15 06 16 07 17) */
      "punpckhbh  %[tmp1],         %[tmp12],       %[tmp13]         \n\t"

      "dadd       %[src_tmp],      %[src_tmp],     %[src_stride]    \n\t"
      "ldc1       %[tmp12],        0x00(%[src_tmp])                 \n\t"
      "dadd       %[src_tmp],      %[src_tmp],     %[src_stride]    \n\t"
      "ldc1       %[tmp13],        0x00(%[src_tmp])                 \n\t"

      /* tmp2 = (20 30 21 31 22 32 23 33) */
      "punpcklbh  %[tmp2],         %[tmp12],       %[tmp13]         \n\t"
      /* tmp3 = (24 34 25 35 26 36 27 37) */
      "punpckhbh  %[tmp3],         %[tmp12],       %[tmp13]         \n\t"

      /* tmp4 = (00 10 20 30 01 11 21 31) */
      "punpcklhw  %[tmp4],         %[tmp0],        %[tmp2]          \n\t"
      /* tmp5 = (02 12 22 32 03 13 23 33) */
      "punpckhhw  %[tmp5],         %[tmp0],        %[tmp2]          \n\t"
      /* tmp6 = (04 14 24 34 05 15 25 35) */
      "punpcklhw  %[tmp6],         %[tmp1],        %[tmp3]          \n\t"
      /* tmp7 = (06 16 26 36 07 17 27 37) */
      "punpckhhw  %[tmp7],         %[tmp1],        %[tmp3]          \n\t"

      "dadd       %[src_tmp],      %[src_tmp],     %[src_stride]    \n\t"
      "ldc1       %[tmp12],        0x00(%[src_tmp])                 \n\t"
      "dadd       %[src_tmp],      %[src_tmp],     %[src_stride]    \n\t"
      "ldc1       %[tmp13],        0x00(%[src_tmp])                 \n\t"

      /* tmp0 = (40 50 41 51 42 52 43 53) */
      "punpcklbh  %[tmp0],         %[tmp12],       %[tmp13]         \n\t"
      /* tmp1 = (44 54 45 55 46 56 47 57) */
      "punpckhbh  %[tmp1],         %[tmp12],       %[tmp13]         \n\t"

      "dadd       %[src_tmp],      %[src_tmp],     %[src_stride]    \n\t"
      "ldc1       %[tmp12],        0x00(%[src_tmp])                 \n\t"
      "dadd       %[src_tmp],      %[src_tmp],     %[src_stride]    \n\t"
      "ldc1       %[tmp13],        0x00(%[src_tmp])                 \n\t"

      /* tmp2 = (60 70 61 71 62 72 63 73) */
      "punpcklbh  %[tmp2],         %[tmp12],       %[tmp13]         \n\t"
      /* tmp3 = (64 74 65 75 66 76 67 77) */
      "punpckhbh  %[tmp3],         %[tmp12],       %[tmp13]         \n\t"

      /* tmp8 = (40 50 60 70 41 51 61 71) */
      "punpcklhw  %[tmp8],         %[tmp0],        %[tmp2]          \n\t"
      /* tmp9 = (42 52 62 72 43 53 63 73) */
      "punpckhhw  %[tmp9],         %[tmp0],        %[tmp2]          \n\t"
      /* tmp10 = (44 54 64 74 45 55 65 75) */
      "punpcklhw  %[tmp10],        %[tmp1],        %[tmp3]          \n\t"
      /* tmp11 = (46 56 66 76 47 57 67 77) */
      "punpckhhw  %[tmp11],        %[tmp1],        %[tmp3]          \n\t"

      /* tmp0 = (00 10 20 30 40 50 60 70) */
      "punpcklwd  %[tmp0],         %[tmp4],        %[tmp8]          \n\t"
      /* tmp1 = (01 11 21 31 41 51 61 71) */
      "punpckhwd  %[tmp1],         %[tmp4],        %[tmp8]          \n\t"
      "gssdlc1    %[tmp0],         0x07(%[dst])                     \n\t"
      "gssdrc1    %[tmp0],         0x00(%[dst])                     \n\t"
      "dadd       %[dst],          %[dst],         %[dst_stride]    \n\t"
      "gssdlc1    %[tmp1],         0x07(%[dst])                     \n\t"
      "gssdrc1    %[tmp1],         0x00(%[dst])                     \n\t"

      /* tmp0 = (02 12 22 32 42 52 62 72) */
      "punpcklwd  %[tmp0],         %[tmp5],        %[tmp9]          \n\t"
      /* tmp1 = (03 13 23 33 43 53 63 73) */
      "punpckhwd  %[tmp1],         %[tmp5],        %[tmp9]          \n\t"
      "dadd       %[dst],          %[dst],         %[dst_stride]    \n\t"
      "gssdlc1    %[tmp0],         0x07(%[dst])                     \n\t"
      "gssdrc1    %[tmp0],         0x00(%[dst])                     \n\t"
      "dadd       %[dst],          %[dst],         %[dst_stride]    \n\t"
      "gssdlc1    %[tmp1],         0x07(%[dst])                     \n\t"
      "gssdrc1    %[tmp1],         0x00(%[dst])                     \n\t"

      /* tmp0 = (04 14 24 34 44 54 64 74) */
      "punpcklwd  %[tmp0],         %[tmp6],        %[tmp10]         \n\t"
      /* tmp1 = (05 15 25 35 45 55 65 75) */
      "punpckhwd  %[tmp1],         %[tmp6],        %[tmp10]         \n\t"
      "dadd       %[dst],          %[dst],         %[dst_stride]    \n\t"
      "gssdlc1    %[tmp0],         0x07(%[dst])                     \n\t"
      "gssdrc1    %[tmp0],         0x00(%[dst])                     \n\t"
      "dadd       %[dst],          %[dst],         %[dst_stride]    \n\t"
      "gssdlc1    %[tmp1],         0x07(%[dst])                     \n\t"
      "gssdrc1    %[tmp1],         0x00(%[dst])                     \n\t"

      /* tmp0 = (06 16 26 36 46 56 66 76) */
      "punpcklwd  %[tmp0],         %[tmp7],        %[tmp11]         \n\t"
      /* tmp1 = (07 17 27 37 47 57 67 77) */
      "punpckhwd  %[tmp1],         %[tmp7],        %[tmp11]         \n\t"
      "dadd       %[dst],          %[dst],         %[dst_stride]    \n\t"
      "gssdlc1    %[tmp0],         0x07(%[dst])                     \n\t"
      "gssdrc1    %[tmp0],         0x00(%[dst])                     \n\t"
      "dadd       %[dst],          %[dst],         %[dst_stride]    \n\t"
      "gssdlc1    %[tmp1],         0x07(%[dst])                     \n\t"
      "gssdrc1    %[tmp1],         0x00(%[dst])                     \n\t"

      "dadd       %[dst],          %[dst],         %[dst_stride]    \n\t"
      "daddi      %[src],          %[src],          0x08            \n\t"
      "daddi      %[width],        %[width],       -0x08            \n\t"
      "bnez       %[width],        1b                               \n\t"

      : [tmp0] "=&f"(tmp0), [tmp1] "=&f"(tmp1), [tmp2] "=&f"(tmp2),
        [tmp3] "=&f"(tmp3), [tmp4] "=&f"(tmp4), [tmp5] "=&f"(tmp5),
        [tmp6] "=&f"(tmp6), [tmp7] "=&f"(tmp7), [tmp8] "=&f"(tmp8),
        [tmp9] "=&f"(tmp9), [tmp10] "=&f"(tmp10), [tmp11] "=&f"(tmp11),
        [tmp12] "=&f"(tmp12), [tmp13] "=&f"(tmp13), [dst] "+&r"(dst),
        [src_tmp] "+&r"(src_tmp)
      : [src] "r"(src), [width] "r"(width), [src_stride] "r"(src_stride),
        [dst_stride] "r"(dst_stride)
      : "memory");
}

void TransposeUVWx8_MMI(const uint8_t* src,
                        int src_stride,
                        uint8_t* dst_a,
                        int dst_stride_a,
                        uint8_t* dst_b,
                        int dst_stride_b,
                        int width) {
  uint64_t tmp0, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6;
  uint64_t tmp7, tmp8, tmp9, tmp10, tmp11, tmp12, tmp13;
  uint8_t* src_tmp = nullptr;

  __asm__ volatile(
      "1:                                                           \n\t"
      /* tmp12 = (u00 v00 u01 v01 u02 v02 u03 v03) */
      "ldc1       %[tmp12],        0x00(%[src])                     \n\t"
      "dadd       %[src_tmp],      %[src],         %[src_stride]    \n\t"
      /* tmp13 = (u10 v10 u11 v11 u12 v12 u13 v13) */
      "ldc1       %[tmp13],        0x00(%[src_tmp])                  \n\t"

      /* tmp0 = (u00 u10 v00 v10 u01 u11 v01 v11) */
      "punpcklbh  %[tmp0],         %[tmp12],       %[tmp13]         \n\t"
      /* tmp1 = (u02 u12 v02 v12 u03 u13 v03 v13) */
      "punpckhbh  %[tmp1],         %[tmp12],       %[tmp13]         \n\t"

      "dadd       %[src_tmp],      %[src_tmp],     %[src_stride]    \n\t"
      /* tmp12 = (u20 v20 u21 v21 u22 v22 u23 v23) */
      "ldc1       %[tmp12],        0x00(%[src_tmp])                 \n\t"
      "dadd       %[src_tmp],      %[src_tmp],     %[src_stride]    \n\t"
      /* tmp13 = (u30 v30 u31 v31 u32 v32 u33 v33) */
      "ldc1       %[tmp13],        0x00(%[src_tmp])                 \n\t"

      /* tmp2 = (u20 u30 v20 v30 u21 u31 v21 v31) */
      "punpcklbh  %[tmp2],         %[tmp12],       %[tmp13]         \n\t"
      /* tmp3 = (u22 u32 v22 v32 u23 u33 v23 v33) */
      "punpckhbh  %[tmp3],         %[tmp12],       %[tmp13]         \n\t"

      /* tmp4 = (u00 u10 u20 u30 v00 v10 v20 v30) */
      "punpcklhw  %[tmp4],         %[tmp0],        %[tmp2]          \n\t"
      /* tmp5 = (u01 u11 u21 u31 v01 v11 v21 v31) */
      "punpckhhw  %[tmp5],         %[tmp0],        %[tmp2]          \n\t"
      /* tmp6 = (u02 u12 u22 u32 v02 v12 v22 v32) */
      "punpcklhw  %[tmp6],         %[tmp1],        %[tmp3]          \n\t"
      /* tmp7 = (u03 u13 u23 u33 v03 v13 v23 v33) */
      "punpckhhw  %[tmp7],         %[tmp1],        %[tmp3]          \n\t"

      "dadd       %[src_tmp],     %[src_tmp],      %[src_stride]    \n\t"
      /* tmp12 = (u40 v40 u41 v41 u42 v42 u43 v43) */
      "ldc1       %[tmp12],        0x00(%[src_tmp])                 \n\t"
      /* tmp13 = (u50 v50 u51 v51 u52 v52 u53 v53) */
      "dadd       %[src_tmp],      %[src_tmp],     %[src_stride]    \n\t"
      "ldc1       %[tmp13],        0x00(%[src_tmp])                 \n\t"

      /* tmp0 = (u40 u50 v40 v50 u41 u51 v41 v51) */
      "punpcklbh  %[tmp0],         %[tmp12],       %[tmp13]         \n\t"
      /* tmp1 = (u42 u52 v42 v52 u43 u53 v43 v53) */
      "punpckhbh  %[tmp1],         %[tmp12],       %[tmp13]         \n\t"

      "dadd       %[src_tmp],      %[src_tmp],     %[src_stride]    \n\t"
      /* tmp12 = (u60 v60 u61 v61 u62 v62 u63 v63) */
      "ldc1       %[tmp12],        0x00(%[src_tmp])                 \n\t"
      /* tmp13 = (u70 v70 u71 v71 u72 v72 u73 v73) */
      "dadd       %[src_tmp],      %[src_tmp],     %[src_stride]    \n\t"
      "ldc1       %[tmp13],        0x00(%[src_tmp])                 \n\t"

      /* tmp2 = (u60 u70 v60 v70 u61 u71 v61 v71) */
      "punpcklbh  %[tmp2],         %[tmp12],       %[tmp13]         \n\t"
      /* tmp3 = (u62 u72 v62 v72 u63 u73 v63 v73) */
      "punpckhbh  %[tmp3],         %[tmp12],       %[tmp13]         \n\t"

      /* tmp8 = (u40 u50 u60 u70 v40 v50 v60 v70) */
      "punpcklhw  %[tmp8],         %[tmp0],        %[tmp2]          \n\t"
      /* tmp9 = (u41 u51 u61 u71 v41 v51 v61 v71) */
      "punpckhhw  %[tmp9],         %[tmp0],        %[tmp2]          \n\t"
      /* tmp10 = (u42 u52 u62 u72 v42 v52 v62 v72) */
      "punpcklhw  %[tmp10],        %[tmp1],        %[tmp3]          \n\t"
      /* tmp11 = (u43 u53 u63 u73 v43 v53 v63 v73) */
      "punpckhhw  %[tmp11],        %[tmp1],        %[tmp3]          \n\t"

      /* tmp0 = (u00 u10 u20 u30 u40 u50 u60 u70) */
      "punpcklwd  %[tmp0],         %[tmp4],        %[tmp8]          \n\t"
      /* tmp1 = (v00 v10 v20 v30 v40 v50 v60 v70) */
      "punpckhwd  %[tmp1],         %[tmp4],        %[tmp8]          \n\t"
      "gssdlc1    %[tmp0],         0x07(%[dst_a])                   \n\t"
      "gssdrc1    %[tmp0],         0x00(%[dst_a])                   \n\t"
      "gssdlc1    %[tmp1],         0x07(%[dst_b])                   \n\t"
      "gssdrc1    %[tmp1],         0x00(%[dst_b])                   \n\t"

      /* tmp0 = (u01 u11 u21 u31 u41 u51 u61 u71) */
      "punpcklwd  %[tmp0],         %[tmp5],        %[tmp9]          \n\t"
      /* tmp1 = (v01 v11 v21 v31 v41 v51 v61 v71) */
      "punpckhwd  %[tmp1],         %[tmp5],        %[tmp9]          \n\t"
      "dadd       %[dst_a],        %[dst_a],       %[dst_stride_a]  \n\t"
      "gssdlc1    %[tmp0],         0x07(%[dst_a])                   \n\t"
      "gssdrc1    %[tmp0],         0x00(%[dst_a])                   \n\t"
      "dadd       %[dst_b],        %[dst_b],       %[dst_stride_b]  \n\t"
      "gssdlc1    %[tmp1],         0x07(%[dst_b])                   \n\t"
      "gssdrc1    %[tmp1],         0x00(%[dst_b])                   \n\t"

      /* tmp0 = (u02 u12 u22 u32 u42 u52 u62 u72) */
      "punpcklwd  %[tmp0],         %[tmp6],        %[tmp10]         \n\t"
      /* tmp1 = (v02 v12 v22 v32 v42 v52 v62 v72) */
      "punpckhwd  %[tmp1],         %[tmp6],        %[tmp10]         \n\t"
      "dadd       %[dst_a],        %[dst_a],       %[dst_stride_a]  \n\t"
      "gssdlc1    %[tmp0],         0x07(%[dst_a])                   \n\t"
      "gssdrc1    %[tmp0],         0x00(%[dst_a])                   \n\t"
      "dadd       %[dst_b],        %[dst_b],       %[dst_stride_b]  \n\t"
      "gssdlc1    %[tmp1],         0x07(%[dst_b])                   \n\t"
      "gssdrc1    %[tmp1],         0x00(%[dst_b])                   \n\t"

      /* tmp0 = (u03 u13 u23 u33 u43 u53 u63 u73) */
      "punpcklwd  %[tmp0],         %[tmp7],        %[tmp11]         \n\t"
      /* tmp1 = (v03 v13 v23 v33 v43 v53 v63 v73) */
      "punpckhwd  %[tmp1],         %[tmp7],        %[tmp11]         \n\t"
      "dadd       %[dst_a],        %[dst_a],       %[dst_stride_a]  \n\t"
      "gssdlc1    %[tmp0],         0x07(%[dst_a])                   \n\t"
      "gssdrc1    %[tmp0],         0x00(%[dst_a])                   \n\t"
      "dadd       %[dst_b],        %[dst_b],       %[dst_stride_b]  \n\t"
      "gssdlc1    %[tmp1],         0x07(%[dst_b])                   \n\t"
      "gssdrc1    %[tmp1],         0x00(%[dst_b])                   \n\t"

      "dadd       %[dst_a],        %[dst_a],       %[dst_stride_a]  \n\t"
      "dadd       %[dst_b],        %[dst_b],       %[dst_stride_b]  \n\t"
      "daddiu     %[src],          %[src],          0x08            \n\t"
      "daddi      %[width],        %[width],       -0x04            \n\t"
      "bnez       %[width],        1b                               \n\t"

      : [tmp0] "=&f"(tmp0), [tmp1] "=&f"(tmp1), [tmp2] "=&f"(tmp2),
        [tmp3] "=&f"(tmp3), [tmp4] "=&f"(tmp4), [tmp5] "=&f"(tmp5),
        [tmp6] "=&f"(tmp6), [tmp7] "=&f"(tmp7), [tmp8] "=&f"(tmp8),
        [tmp9] "=&f"(tmp9), [tmp10] "=&f"(tmp10), [tmp11] "=&f"(tmp11),
        [tmp12] "=&f"(tmp12), [tmp13] "=&f"(tmp13), [dst_a] "+&r"(dst_a),
        [dst_b] "+&r"(dst_b), [src_tmp] "+&r"(src_tmp)
      : [src] "r"(src), [width] "r"(width), [dst_stride_a] "r"(dst_stride_a),
        [dst_stride_b] "r"(dst_stride_b), [src_stride] "r"(src_stride)
      : "memory");
}

#endif  // !defined(LIBYUV_DISABLE_MMI) && defined(_MIPS_ARCH_LOONGSON3A)

#ifdef __cplusplus
}  // extern "C"
}  // namespace libyuv
#endif
