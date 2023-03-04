/*
 *  Copyright 2011 The LibYuv Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS. All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "libyuv/row.h"

#include <string.h>  // For memcpy and memset.

#include "libyuv/basic_types.h"

#ifdef __cplusplus
namespace libyuv {
extern "C" {
#endif

// This module is for Mips MMI.
#if !defined(LIBYUV_DISABLE_MMI) && defined(_MIPS_ARCH_LOONGSON3A)

// clang-format off

void RGB24ToARGBRow_MMI(const uint8_t* src_rgb24,
                        uint8_t* dst_argb,
                        int width) {
  uint64_t src0, src1, dest;
  const uint64_t mask = 0xff000000ULL;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gslwlc1    %[src0],         0x03(%[src_ptr])                 \n\t"
      "gslwrc1    %[src0],         0x00(%[src_ptr])                 \n\t"
      "gslwlc1    %[src1],         0x06(%[src_ptr])                 \n\t"
      "gslwrc1    %[src1],         0x03(%[src_ptr])                 \n\t"

      "or         %[src0],         %[src0],           %[mask]       \n\t"
      "or         %[src1],         %[src1],           %[mask]       \n\t"
      "punpcklwd  %[dest],         %[src0],           %[src1]       \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "gslwlc1    %[src0],         0x09(%[src_ptr])                 \n\t"
      "gslwrc1    %[src0],         0x06(%[src_ptr])                 \n\t"
      "gslwlc1    %[src1],         0x0c(%[src_ptr])                 \n\t"
      "gslwrc1    %[src1],         0x09(%[src_ptr])                 \n\t"

      "or         %[src0],         %[src0],           %[mask]       \n\t"
      "or         %[src1],         %[src1],           %[mask]       \n\t"
      "punpcklwd  %[dest],         %[src0],           %[src1]       \n\t"
      "gssdlc1    %[dest],         0x0f(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x08(%[dst_ptr])                 \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x0c          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x10          \n\t"
      "daddi      %[width],        %[width],         -0x04          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src0] "=&f"(src0), [src1] "=&f"(src1), [dest] "=&f"(dest)
      : [src_ptr] "r"(src_rgb24), [dst_ptr] "r"(dst_argb), [width] "r"(width),
        [mask] "f"(mask)
      : "memory");
}

void RAWToARGBRow_MMI(const uint8_t* src_raw, uint8_t* dst_argb, int width) {
  uint64_t src0, src1, dest;
  const uint64_t mask0 = 0x0;
  const uint64_t mask1 = 0xff000000ULL;
  const uint64_t mask2 = 0xc6;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gslwlc1    %[src0],         0x03(%[src_ptr])                 \n\t"
      "gslwrc1    %[src0],         0x00(%[src_ptr])                 \n\t"
      "gslwlc1    %[src1],         0x06(%[src_ptr])                 \n\t"
      "gslwrc1    %[src1],         0x03(%[src_ptr])                 \n\t"

      "or         %[src0],         %[src0],           %[mask1]      \n\t"
      "punpcklbh  %[src0],         %[src0],           %[mask0]      \n\t"
      "pshufh     %[src0],         %[src0],           %[mask2]      \n\t"
      "or         %[src1],         %[src1],           %[mask1]      \n\t"
      "punpcklbh  %[src1],         %[src1],           %[mask0]      \n\t"
      "pshufh     %[src1],         %[src1],           %[mask2]      \n\t"
      "packushb   %[dest],         %[src0],           %[src1]       \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "gslwlc1    %[src0],         0x09(%[src_ptr])                 \n\t"
      "gslwrc1    %[src0],         0x06(%[src_ptr])                 \n\t"
      "gslwlc1    %[src1],         0x0c(%[src_ptr])                 \n\t"
      "gslwrc1    %[src1],         0x09(%[src_ptr])                 \n\t"

      "or         %[src0],         %[src0],           %[mask1]      \n\t"
      "punpcklbh  %[src0],         %[src0],           %[mask0]      \n\t"
      "pshufh     %[src0],         %[src0],           %[mask2]      \n\t"
      "or         %[src1],         %[src1],           %[mask1]      \n\t"
      "punpcklbh  %[src1],         %[src1],           %[mask0]      \n\t"
      "pshufh     %[src1],         %[src1],           %[mask2]      \n\t"
      "packushb   %[dest],         %[src0],           %[src1]       \n\t"
      "gssdlc1    %[dest],         0x0f(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x08(%[dst_ptr])                 \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x0c          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x10          \n\t"
      "daddi      %[width],        %[width],         -0x04          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src0] "=&f"(src0), [src1] "=&f"(src1), [dest] "=&f"(dest)
      : [src_ptr] "r"(src_raw), [dst_ptr] "r"(dst_argb), [mask0] "f"(mask0),
        [mask1] "f"(mask1), [mask2] "f"(mask2), [width] "r"(width)
      : "memory");
}

void RAWToRGB24Row_MMI(const uint8_t* src_raw, uint8_t* dst_rgb24, int width) {
  uint64_t src0, src1;
  uint64_t ftmp[4];
  uint64_t mask0 = 0xc6;
  uint64_t mask1 = 0x6c;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldrc1    %[src0],         0x00(%[src_raw])                 \n\t"
      "gsldlc1    %[src0],         0x07(%[src_raw])                 \n\t"
      "gslwrc1    %[src1],         0x08(%[src_raw])                 \n\t"
      "gslwlc1    %[src1],         0x0b(%[src_raw])                 \n\t"

      "punpcklbh  %[ftmp0],        %[src0],           %[zero]       \n\t"
      "pshufh     %[ftmp0],        %[ftmp0],          %[mask0]      \n\t"
      "punpckhbh  %[ftmp1],        %[src0],           %[zero]       \n\t"
      "punpcklbh  %[src1],         %[src1],           %[zero]       \n\t"
      "pextrh     %[ftmp2],        %[ftmp0],          %[three]      \n\t"
      "pextrh     %[ftmp3],        %[ftmp1],          %[one]        \n\t"
      "pinsrh_3   %[ftmp0],        %[ftmp0],          %[ftmp3]      \n\t"
      "pextrh     %[ftmp3],        %[ftmp1],          %[two]        \n\t"
      "pinsrh_1   %[ftmp1],        %[ftmp1],          %[ftmp2]      \n\t"
      "pshufh     %[src1],         %[src1],           %[mask1]      \n\t"
      "pextrh     %[ftmp2],        %[src1],           %[zero]       \n\t"
      "pinsrh_2   %[ftmp1],        %[ftmp1],          %[ftmp2]      \n\t"
      "pinsrh_0   %[src1],         %[src1],           %[ftmp3]      \n\t"
      "packushb   %[ftmp0],        %[ftmp0],          %[ftmp1]      \n\t"
      "packushb   %[src1],         %[src1],           %[zero]       \n\t"

      "gssdrc1    %[ftmp0],        0x00(%[dst_rgb24])               \n\t"
      "gssdlc1    %[ftmp0],        0x07(%[dst_rgb24])               \n\t"
      "gsswrc1    %[src1],         0x08(%[dst_rgb24])               \n\t"
      "gsswlc1    %[src1],         0x0b(%[dst_rgb24])               \n\t"

      "daddiu     %[src_raw],      %[src_raw],        0x0c          \n\t"
      "daddiu     %[dst_rgb24],    %[dst_rgb24],      0x0c          \n\t"
      "daddiu     %[width],        %[width],         -0x04          \n\t"
      "bgtz       %[width],        1b                               \n\t"
      : [src0] "=&f"(src0), [src1] "=&f"(src1), [ftmp0] "=&f"(ftmp[0]),
        [ftmp1] "=&f"(ftmp[1]), [ftmp2] "=&f"(ftmp[2]), [ftmp3] "=&f"(ftmp[3])
      : [src_raw] "r"(src_raw), [dst_rgb24] "r"(dst_rgb24), [width] "r"(width),
        [mask0] "f"(mask0), [mask1] "f"(mask1), [zero] "f"(0x00),
        [one] "f"(0x01), [two] "f"(0x02), [three] "f"(0x03)
      : "memory");
}

void RGB565ToARGBRow_MMI(const uint8_t* src_rgb565,
                         uint8_t* dst_argb,
                         int width) {
  uint64_t ftmp[5];
  uint64_t c0 = 0x001f001f001f001f;
  uint64_t c1 = 0x00ff00ff00ff00ff;
  uint64_t c2 = 0x0007000700070007;
  __asm__ volatile(
      "1:                                                      \n\t"
      "gsldrc1   %[src0],       0x00(%[src_rgb565])            \n\t"
      "gsldlc1   %[src0],       0x07(%[src_rgb565])            \n\t"
      "psrlh     %[src1],       %[src0],             %[eight]  \n\t"
      "and       %[b],          %[src0],             %[c0]     \n\t"
      "and       %[src0],       %[src0],             %[c1]     \n\t"
      "psrlh     %[src0],       %[src0],             %[five]   \n\t"
      "and       %[g],          %[src1],             %[c2]     \n\t"
      "psllh     %[g],          %[g],                %[three]  \n\t"
      "or        %[g],          %[src0],             %[g]      \n\t"
      "psrlh     %[r],          %[src1],             %[three]  \n\t"
      "psllh     %[src0],       %[b],                %[three]  \n\t"
      "psrlh     %[src1],       %[b],                %[two]    \n\t"
      "or        %[b],          %[src0],             %[src1]   \n\t"
      "psllh     %[src0],       %[g],                %[two]    \n\t"
      "psrlh     %[src1],       %[g],                %[four]   \n\t"
      "or        %[g],          %[src0],             %[src1]   \n\t"
      "psllh     %[src0],       %[r],                %[three]  \n\t"
      "psrlh     %[src1],       %[r],                %[two]    \n\t"
      "or        %[r],          %[src0],             %[src1]   \n\t"
      "packushb  %[b],          %[b],                %[r]      \n\t"
      "packushb  %[g],          %[g],                %[c1]     \n\t"
      "punpcklbh %[src0],       %[b],                %[g]      \n\t"
      "punpckhbh %[src1],       %[b],                %[g]      \n\t"
      "punpcklhw %[r],          %[src0],             %[src1]   \n\t"
      "gssdrc1   %[r],          0x00(%[dst_argb])              \n\t"
      "gssdlc1   %[r],          0x07(%[dst_argb])              \n\t"
      "punpckhhw %[r],          %[src0],             %[src1]   \n\t"
      "gssdrc1   %[r],          0x08(%[dst_argb])              \n\t"
      "gssdlc1   %[r],          0x0f(%[dst_argb])              \n\t"
      "daddiu    %[src_rgb565], %[src_rgb565],       0x08      \n\t"
      "daddiu    %[dst_argb],   %[dst_argb],         0x10      \n\t"
      "daddiu    %[width],      %[width],           -0x04      \n\t"
      "bgtz      %[width],     1b                              \n\t"
      : [src0] "=&f"(ftmp[0]), [src1] "=&f"(ftmp[1]), [b] "=&f"(ftmp[2]),
        [g] "=&f"(ftmp[3]), [r] "=&f"(ftmp[4])
      : [src_rgb565] "r"(src_rgb565), [dst_argb] "r"(dst_argb),
        [width] "r"(width), [c0] "f"(c0), [c1] "f"(c1), [c2] "f"(c2),
        [eight] "f"(0x08), [five] "f"(0x05), [three] "f"(0x03), [two] "f"(0x02),
        [four] "f"(0x04)
      : "memory");
}

void ARGB1555ToARGBRow_MMI(const uint8_t* src_argb1555,
                           uint8_t* dst_argb,
                           int width) {
  uint64_t ftmp[6];
  uint64_t c0 = 0x001f001f001f001f;
  uint64_t c1 = 0x00ff00ff00ff00ff;
  uint64_t c2 = 0x0003000300030003;
  uint64_t c3 = 0x007c007c007c007c;
  uint64_t c4 = 0x0001000100010001;
  __asm__ volatile(
      "1:                                                         \n\t"
      "gsldrc1   %[src0],         0x00(%[src_argb1555])           \n\t"
      "gsldlc1   %[src0],         0x07(%[src_argb1555])           \n\t"
      "psrlh     %[src1],         %[src0],              %[eight]  \n\t"
      "and       %[b],            %[src0],              %[c0]     \n\t"
      "and       %[src0],         %[src0],              %[c1]     \n\t"
      "psrlh     %[src0],         %[src0],              %[five]   \n\t"
      "and       %[g],            %[src1],              %[c2]     \n\t"
      "psllh     %[g],            %[g],                 %[three]  \n\t"
      "or        %[g],            %[src0],              %[g]      \n\t"
      "and       %[r],            %[src1],              %[c3]     \n\t"
      "psrlh     %[r],            %[r],                 %[two]    \n\t"
      "psrlh     %[a],            %[src1],              %[seven]  \n\t"
      "psllh     %[src0],         %[b],                 %[three]  \n\t"
      "psrlh     %[src1],         %[b],                 %[two]    \n\t"
      "or        %[b],            %[src0],              %[src1]   \n\t"
      "psllh     %[src0],         %[g],                 %[three]  \n\t"
      "psrlh     %[src1],         %[g],                 %[two]    \n\t"
      "or        %[g],            %[src0],              %[src1]   \n\t"
      "psllh     %[src0],         %[r],                 %[three]  \n\t"
      "psrlh     %[src1],         %[r],                 %[two]    \n\t"
      "or        %[r],            %[src0],              %[src1]   \n\t"
      "xor       %[a],            %[a],                 %[c1]     \n\t"
      "paddb     %[a],            %[a],                 %[c4]     \n\t"
      "packushb  %[b],            %[b],                 %[r]      \n\t"
      "packushb  %[g],            %[g],                 %[a]      \n\t"
      "punpcklbh %[src0],         %[b],                 %[g]      \n\t"
      "punpckhbh %[src1],         %[b],                 %[g]      \n\t"
      "punpcklhw %[r],            %[src0],              %[src1]   \n\t"
      "gssdrc1   %[r],            0x00(%[dst_argb])               \n\t"
      "gssdlc1   %[r],            0x07(%[dst_argb])               \n\t"
      "punpckhhw %[r],            %[src0],              %[src1]   \n\t"
      "gssdrc1   %[r],            0x08(%[dst_argb])               \n\t"
      "gssdlc1   %[r],            0x0f(%[dst_argb])               \n\t"
      "daddiu    %[src_argb1555], %[src_argb1555],      0x08      \n\t"
      "daddiu    %[dst_argb],     %[dst_argb],          0x10      \n\t"
      "daddiu    %[width],        %[width],            -0x04      \n\t"
      "bgtz      %[width],        1b                              \n\t"
      : [src0] "=&f"(ftmp[0]), [src1] "=&f"(ftmp[1]), [b] "=&f"(ftmp[2]),
        [g] "=&f"(ftmp[3]), [r] "=&f"(ftmp[4]), [a] "=&f"(ftmp[5])
      : [src_argb1555] "r"(src_argb1555), [dst_argb] "r"(dst_argb),
        [width] "r"(width), [c0] "f"(c0), [c1] "f"(c1), [c2] "f"(c2),
        [c3] "f"(c3), [c4] "f"(c4), [eight] "f"(0x08), [five] "f"(0x05),
        [three] "f"(0x03), [two] "f"(0x02), [seven] "f"(0x07)
      : "memory");
}

void ARGB4444ToARGBRow_MMI(const uint8_t* src_argb4444,
                           uint8_t* dst_argb,
                           int width) {
  uint64_t ftmp[6];
  uint64_t c0 = 0x000f000f000f000f;
  uint64_t c1 = 0x00ff00ff00ff00ff;
  __asm__ volatile(
      "1:                                                          \n\t"
      "gsldrc1   %[src0],         0x00(%[src_argb4444])            \n\t"
      "gsldlc1   %[src0],         0x07(%[src_argb4444])            \n\t"
      "psrlh     %[src1],         %[src0],              %[eight]   \n\t"
      "and       %[b],            %[src0],              %[c0]      \n\t"
      "and       %[src0],         %[src0],              %[c1]      \n\t"
      "psrlh     %[g],            %[src0],              %[four]    \n\t"
      "and       %[r],            %[src1],              %[c0]      \n\t"
      "psrlh     %[a],            %[src1],              %[four]    \n\t"
      "psllh     %[src0],         %[b],                 %[four]    \n\t"
      "or        %[b],            %[src0],              %[b]       \n\t"
      "psllh     %[src0],         %[g],                 %[four]    \n\t"
      "or        %[g],            %[src0],              %[g]       \n\t"
      "psllh     %[src0],         %[r],                 %[four]    \n\t"
      "or        %[r],            %[src0],              %[r]       \n\t"
      "psllh     %[src0],         %[a],                 %[four]    \n\t"
      "or        %[a],            %[src0],              %[a]       \n\t"
      "packushb  %[b],            %[b],                 %[r]       \n\t"
      "packushb  %[g],            %[g],                 %[a]       \n\t"
      "punpcklbh %[src0],         %[b],                 %[g]       \n\t"
      "punpckhbh %[src1],         %[b],                 %[g]       \n\t"
      "punpcklhw %[r],            %[src0],              %[src1]    \n\t"
      "gssdrc1   %[r],            0x00(%[dst_argb])                \n\t"
      "gssdlc1   %[r],            0x07(%[dst_argb])                \n\t"
      "punpckhhw %[r],            %[src0],              %[src1]    \n\t"
      "gssdrc1   %[r],            0x08(%[dst_argb])                \n\t"
      "gssdlc1   %[r],            0x0f(%[dst_argb])                \n\t"
      "daddiu    %[src_argb4444], %[src_argb4444],      0x08       \n\t"
      "daddiu    %[dst_argb],     %[dst_argb],          0x10       \n\t"
      "daddiu    %[width],        %[width],            -0x04       \n\t"
      "bgtz      %[width],        1b                               \n\t"
      : [src0] "=&f"(ftmp[0]), [src1] "=&f"(ftmp[1]), [b] "=&f"(ftmp[2]),
        [g] "=&f"(ftmp[3]), [r] "=&f"(ftmp[4]), [a] "=&f"(ftmp[5])
      : [src_argb4444] "r"(src_argb4444), [dst_argb] "r"(dst_argb),
        [width] "r"(width), [c0] "f"(c0), [c1] "f"(c1), [eight] "f"(0x08),
        [four] "f"(0x04)
      : "memory");
}

void ARGBToRGB24Row_MMI(const uint8_t* src_argb, uint8_t* dst_rgb, int width) {
  uint64_t src;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gslwlc1    %[src],          0x03(%[src_ptr])                 \n\t"
      "gslwrc1    %[src],          0x00(%[src_ptr])                 \n\t"
      "gsswlc1    %[src],          0x03(%[dst_ptr])                 \n\t"
      "gsswrc1    %[src],          0x00(%[dst_ptr])                 \n\t"

      "gslwlc1    %[src],          0x07(%[src_ptr])                 \n\t"
      "gslwrc1    %[src],          0x04(%[src_ptr])                 \n\t"
      "gsswlc1    %[src],          0x06(%[dst_ptr])                 \n\t"
      "gsswrc1    %[src],          0x03(%[dst_ptr])                 \n\t"

      "gslwlc1    %[src],          0x0b(%[src_ptr])                 \n\t"
      "gslwrc1    %[src],          0x08(%[src_ptr])                 \n\t"
      "gsswlc1    %[src],          0x09(%[dst_ptr])                 \n\t"
      "gsswrc1    %[src],          0x06(%[dst_ptr])                 \n\t"

      "gslwlc1    %[src],          0x0f(%[src_ptr])                 \n\t"
      "gslwrc1    %[src],          0x0c(%[src_ptr])                 \n\t"
      "gsswlc1    %[src],          0x0c(%[dst_ptr])                 \n\t"
      "gsswrc1    %[src],          0x09(%[dst_ptr])                 \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x10          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x0c          \n\t"
      "daddi      %[width],        %[width],         -0x04          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src] "=&f"(src)
      : [src_ptr] "r"(src_argb), [dst_ptr] "r"(dst_rgb), [width] "r"(width)
      : "memory");
}

void ARGBToRAWRow_MMI(const uint8_t* src_argb, uint8_t* dst_rgb, int width) {
  uint64_t src0, src1;
  uint64_t ftmp[3];
  uint64_t mask0 = 0xc6;
  uint64_t mask1 = 0x18;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldrc1    %[src0],         0x00(%[src_argb])                \n\t"
      "gsldlc1    %[src0],         0x07(%[src_argb])                \n\t"
      "gsldrc1    %[src1],         0x08(%[src_argb])                \n\t"
      "gsldlc1    %[src1],         0x0f(%[src_argb])                \n\t"

      "punpcklbh  %[ftmp0],        %[src0],           %[zero]       \n\t"
      "pshufh     %[ftmp0],        %[ftmp0],          %[mask0]      \n\t"
      "punpckhbh  %[ftmp1],        %[src0],           %[zero]       \n\t"
      "punpcklbh  %[ftmp2],        %[src1],           %[zero]       \n\t"
      "punpckhbh  %[src1],         %[src1],           %[zero]       \n\t"

      "pextrh     %[src0],         %[ftmp1],          %[two]        \n\t"
      "pinsrh_3   %[ftmp0],        %[ftmp0],          %[src0]       \n\t"
      "pshufh     %[ftmp1],        %[ftmp1],          %[one]        \n\t"

      "pextrh     %[src0],         %[ftmp2],          %[two]        \n\t"
      "pinsrh_2   %[ftmp1],        %[ftmp1],          %[src0]       \n\t"
      "pextrh     %[src0],         %[ftmp2],          %[one]        \n\t"
      "pinsrh_3   %[ftmp1],        %[ftmp1],          %[src0]       \n\t"
      "pextrh     %[src0],         %[ftmp2],          %[zero]       \n\t"
      "pshufh     %[src1],         %[src1],           %[mask1]      \n\t"
      "pinsrh_0   %[src1],         %[src1],           %[src0]       \n\t"
      "packushb   %[ftmp0],        %[ftmp0],          %[ftmp1]      \n\t"
      "packushb   %[src1],         %[src1],           %[zero]       \n\t"

      "gssdrc1    %[ftmp0],        0x00(%[dst_rgb])                 \n\t"
      "gssdlc1    %[ftmp0],        0x07(%[dst_rgb])                 \n\t"
      "gsswrc1    %[src1],         0x08(%[dst_rgb])                 \n\t"
      "gsswlc1    %[src1],         0x0b(%[dst_rgb])                 \n\t"

      "daddiu     %[src_argb],     %[src_argb],       0x10          \n\t"
      "daddiu     %[dst_rgb],      %[dst_rgb],        0x0c          \n\t"
      "daddiu     %[width],        %[width],         -0x04          \n\t"
      "bgtz       %[width],        1b                               \n\t"
      : [src0] "=&f"(src0), [src1] "=&f"(src1), [ftmp0] "=&f"(ftmp[0]),
        [ftmp1] "=&f"(ftmp[1]), [ftmp2] "=&f"(ftmp[2])
      : [src_argb] "r"(src_argb), [dst_rgb] "r"(dst_rgb), [width] "r"(width),
        [mask0] "f"(mask0), [mask1] "f"(mask1), [zero] "f"(0x00),
        [one] "f"(0x01), [two] "f"(0x02)
      : "memory");
}

void ARGBToRGB565Row_MMI(const uint8_t* src_argb, uint8_t* dst_rgb, int width) {
  uint64_t src0, src1;
  uint64_t ftmp[3];

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldrc1    %[src0],         0x00(%[src_argb])                \n\t"
      "gsldlc1    %[src0],         0x07(%[src_argb])                \n\t"
      "gsldrc1    %[src1],         0x08(%[src_argb])                \n\t"
      "gsldlc1    %[src1],         0x0f(%[src_argb])                \n\t"

      "punpcklbh  %[b],            %[src0],           %[src1]       \n\t"
      "punpckhbh  %[g],            %[src0],           %[src1]       \n\t"
      "punpcklbh  %[src0],         %[b],              %[g]          \n\t"
      "punpckhbh  %[src1],         %[b],              %[g]          \n\t"
      "punpcklbh  %[b],            %[src0],           %[zero]       \n\t"
      "punpckhbh  %[g],            %[src0],           %[zero]       \n\t"
      "punpcklbh  %[r],            %[src1],           %[zero]       \n\t"

      "psrlh      %[b],            %[b],              %[three]      \n\t"
      "psrlh      %[g],            %[g],              %[two]        \n\t"
      "psrlh      %[r],            %[r],              %[three]      \n\t"

      "psllh      %[g],            %[g],              %[five]       \n\t"
      "psllh      %[r],            %[r],              %[eleven]     \n\t"
      "or         %[b],            %[b],              %[g]          \n\t"
      "or         %[b],            %[b],              %[r]          \n\t"

      "gssdrc1    %[b],            0x00(%[dst_rgb])                 \n\t"
      "gssdlc1    %[b],            0x07(%[dst_rgb])                 \n\t"

      "daddiu     %[src_argb],     %[src_argb],       0x10          \n\t"
      "daddiu     %[dst_rgb],      %[dst_rgb],        0x08          \n\t"
      "daddiu     %[width],        %[width],         -0x04          \n\t"
      "bgtz       %[width],        1b                               \n\t"
      : [src0] "=&f"(src0), [src1] "=&f"(src1), [b] "=&f"(ftmp[0]),
        [g] "=&f"(ftmp[1]), [r] "=&f"(ftmp[2])
      : [src_argb] "r"(src_argb), [dst_rgb] "r"(dst_rgb), [width] "r"(width),
        [zero] "f"(0x00), [two] "f"(0x02), [three] "f"(0x03), [five] "f"(0x05),
        [eleven] "f"(0x0b)
      : "memory");
}

// dither4 is a row of 4 values from 4x4 dither matrix.
// The 4x4 matrix contains values to increase RGB.  When converting to
// fewer bits (565) this provides an ordered dither.
// The order in the 4x4 matrix in first byte is upper left.
// The 4 values are passed as an int, then referenced as an array, so
// endian will not affect order of the original matrix.  But the dither4
// will containing the first pixel in the lower byte for little endian
// or the upper byte for big endian.
void ARGBToRGB565DitherRow_MMI(const uint8_t* src_argb,
                               uint8_t* dst_rgb,
                               const uint32_t dither4,
                               int width) {
  uint64_t src0, src1;
  uint64_t ftmp[3];
  uint64_t c0 = 0x00ff00ff00ff00ff;

  __asm__ volatile(
      "punpcklbh  %[dither],       %[dither],         %[zero]       \n\t"
      "1:                                                           \n\t"
      "gsldrc1    %[src0],         0x00(%[src_argb])                \n\t"
      "gsldlc1    %[src0],         0x07(%[src_argb])                \n\t"
      "gsldrc1    %[src1],         0x08(%[src_argb])                \n\t"
      "gsldlc1    %[src1],         0x0f(%[src_argb])                \n\t"

      "punpcklbh  %[b],            %[src0],           %[src1]       \n\t"
      "punpckhbh  %[g],            %[src0],           %[src1]       \n\t"
      "punpcklbh  %[src0],         %[b],              %[g]          \n\t"
      "punpckhbh  %[src1],         %[b],              %[g]          \n\t"
      "punpcklbh  %[b],            %[src0],           %[zero]       \n\t"
      "punpckhbh  %[g],            %[src0],           %[zero]       \n\t"
      "punpcklbh  %[r],            %[src1],           %[zero]       \n\t"

      "paddh      %[b],            %[b],              %[dither]     \n\t"
      "paddh      %[g],            %[g],              %[dither]     \n\t"
      "paddh      %[r],            %[r],              %[dither]     \n\t"
      "pcmpgth    %[src0],         %[b],              %[c0]         \n\t"
      "or         %[src0],         %[src0],           %[b]          \n\t"
      "and        %[b],            %[src0],           %[c0]         \n\t"
      "pcmpgth    %[src0],         %[g],              %[c0]         \n\t"
      "or         %[src0],         %[src0],           %[g]          \n\t"
      "and        %[g],            %[src0],           %[c0]         \n\t"
      "pcmpgth    %[src0],         %[r],              %[c0]         \n\t"
      "or         %[src0],         %[src0],           %[r]          \n\t"
      "and        %[r],            %[src0],           %[c0]         \n\t"

      "psrlh      %[b],            %[b],              %[three]      \n\t"
      "psrlh      %[g],            %[g],              %[two]        \n\t"
      "psrlh      %[r],            %[r],              %[three]      \n\t"

      "psllh      %[g],            %[g],              %[five]       \n\t"
      "psllh      %[r],            %[r],              %[eleven]     \n\t"
      "or         %[b],            %[b],              %[g]          \n\t"
      "or         %[b],            %[b],              %[r]          \n\t"

      "gssdrc1    %[b],            0x00(%[dst_rgb])                 \n\t"
      "gssdlc1    %[b],            0x07(%[dst_rgb])                 \n\t"

      "daddiu     %[src_argb],     %[src_argb],       0x10          \n\t"
      "daddiu     %[dst_rgb],      %[dst_rgb],        0x08          \n\t"
      "daddiu     %[width],        %[width],         -0x04          \n\t"
      "bgtz       %[width],        1b                               \n\t"
      : [src0] "=&f"(src0), [src1] "=&f"(src1), [b] "=&f"(ftmp[0]),
        [g] "=&f"(ftmp[1]), [r] "=&f"(ftmp[2])
      : [src_argb] "r"(src_argb), [dst_rgb] "r"(dst_rgb), [width] "r"(width),
        [dither] "f"(dither4), [c0] "f"(c0), [zero] "f"(0x00), [two] "f"(0x02),
        [three] "f"(0x03), [five] "f"(0x05), [eleven] "f"(0x0b)
      : "memory");
}

void ARGBToARGB1555Row_MMI(const uint8_t* src_argb,
                           uint8_t* dst_rgb,
                           int width) {
  uint64_t src0, src1;
  uint64_t ftmp[4];

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldrc1    %[src0],         0x00(%[src_argb])                \n\t"
      "gsldlc1    %[src0],         0x07(%[src_argb])                \n\t"
      "gsldrc1    %[src1],         0x08(%[src_argb])                \n\t"
      "gsldlc1    %[src1],         0x0f(%[src_argb])                \n\t"

      "punpcklbh  %[b],            %[src0],           %[src1]       \n\t"
      "punpckhbh  %[g],            %[src0],           %[src1]       \n\t"
      "punpcklbh  %[src0],         %[b],              %[g]          \n\t"
      "punpckhbh  %[src1],         %[b],              %[g]          \n\t"
      "punpcklbh  %[b],            %[src0],           %[zero]       \n\t"
      "punpckhbh  %[g],            %[src0],           %[zero]       \n\t"
      "punpcklbh  %[r],            %[src1],           %[zero]       \n\t"
      "punpckhbh  %[a],            %[src1],           %[zero]       \n\t"

      "psrlh      %[b],            %[b],              %[three]      \n\t"
      "psrlh      %[g],            %[g],              %[three]      \n\t"
      "psrlh      %[r],            %[r],              %[three]      \n\t"
      "psrlh      %[a],            %[a],              %[seven]      \n\t"

      "psllh      %[g],            %[g],              %[five]       \n\t"
      "psllh      %[r],            %[r],              %[ten]        \n\t"
      "psllh      %[a],            %[a],              %[fifteen]    \n\t"
      "or         %[b],            %[b],              %[g]          \n\t"
      "or         %[b],            %[b],              %[r]          \n\t"
      "or         %[b],            %[b],              %[a]          \n\t"

      "gssdrc1    %[b],            0x00(%[dst_rgb])                 \n\t"
      "gssdlc1    %[b],            0x07(%[dst_rgb])                 \n\t"

      "daddiu     %[src_argb],     %[src_argb],       0x10          \n\t"
      "daddiu     %[dst_rgb],      %[dst_rgb],        0x08          \n\t"
      "daddiu     %[width],        %[width],         -0x04          \n\t"
      "bgtz       %[width],        1b                               \n\t"
      : [src0] "=&f"(src0), [src1] "=&f"(src1), [b] "=&f"(ftmp[0]),
        [g] "=&f"(ftmp[1]), [r] "=&f"(ftmp[2]), [a] "=&f"(ftmp[3])
      : [src_argb] "r"(src_argb), [dst_rgb] "r"(dst_rgb), [width] "r"(width),
        [zero] "f"(0x00), [three] "f"(0x03), [five] "f"(0x05),
        [seven] "f"(0x07), [ten] "f"(0x0a), [fifteen] "f"(0x0f)
      : "memory");
}

void ARGBToARGB4444Row_MMI(const uint8_t* src_argb,
                           uint8_t* dst_rgb,
                           int width) {
  uint64_t src0, src1;
  uint64_t ftmp[4];

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldrc1    %[src0],         0x00(%[src_argb])                \n\t"
      "gsldlc1    %[src0],         0x07(%[src_argb])                \n\t"
      "gsldrc1    %[src1],         0x08(%[src_argb])                \n\t"
      "gsldlc1    %[src1],         0x0f(%[src_argb])                \n\t"

      "punpcklbh  %[b],            %[src0],           %[src1]       \n\t"
      "punpckhbh  %[g],            %[src0],           %[src1]       \n\t"
      "punpcklbh  %[src0],         %[b],              %[g]          \n\t"
      "punpckhbh  %[src1],         %[b],              %[g]          \n\t"
      "punpcklbh  %[b],            %[src0],           %[zero]       \n\t"
      "punpckhbh  %[g],            %[src0],           %[zero]       \n\t"
      "punpcklbh  %[r],            %[src1],           %[zero]       \n\t"
      "punpckhbh  %[a],            %[src1],           %[zero]       \n\t"

      "psrlh      %[b],            %[b],              %[four]       \n\t"
      "psrlh      %[g],            %[g],              %[four]       \n\t"
      "psrlh      %[r],            %[r],              %[four]       \n\t"
      "psrlh      %[a],            %[a],              %[four]       \n\t"

      "psllh      %[g],            %[g],              %[four]       \n\t"
      "psllh      %[r],            %[r],              %[eight]      \n\t"
      "psllh      %[a],            %[a],              %[twelve]     \n\t"
      "or         %[b],            %[b],              %[g]          \n\t"
      "or         %[b],            %[b],              %[r]          \n\t"
      "or         %[b],            %[b],              %[a]          \n\t"

      "gssdrc1    %[b],            0x00(%[dst_rgb])                 \n\t"
      "gssdlc1    %[b],            0x07(%[dst_rgb])                 \n\t"

      "daddiu     %[src_argb],     %[src_argb],       0x10          \n\t"
      "daddiu     %[dst_rgb],      %[dst_rgb],        0x08          \n\t"
      "daddiu     %[width],        %[width],         -0x04          \n\t"
      "bgtz       %[width],        1b                               \n\t"
      : [src0] "=&f"(src0), [src1] "=&f"(src1), [b] "=&f"(ftmp[0]),
        [g] "=&f"(ftmp[1]), [r] "=&f"(ftmp[2]), [a] "=&f"(ftmp[3])
      : [src_argb] "r"(src_argb), [dst_rgb] "r"(dst_rgb), [width] "r"(width),
        [zero] "f"(0x00), [four] "f"(0x04), [eight] "f"(0x08),
        [twelve] "f"(0x0c)
      : "memory");
}

void ARGBToYRow_MMI(const uint8_t* src_argb, uint8_t* dst_y, int width) {
  uint64_t src, src_hi, src_lo;
  uint64_t dest0, dest1, dest2, dest3;
  const uint64_t value = 0x1080;
  const uint64_t mask = 0x0001004200810019;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[src],          0x07(%[src_argb])               \n\t"
      "gsldrc1    %[src],          0x00(%[src_argb])               \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask]       \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask]       \n\t"
      "punpcklwd  %[src],          %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[dest0],        %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest0],        %[dest0],          %[src]        \n\t"
      "psrlw      %[dest0],        %[dest0],          %[eight]      \n\t"

      "gsldlc1    %[src],          0x0f(%[src_argb])               \n\t"
      "gsldrc1    %[src],          0x08(%[src_argb])               \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask]       \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask]       \n\t"
      "punpcklwd  %[src],          %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[dest1],        %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest1],        %[dest1],          %[src]        \n\t"
      "psrlw      %[dest1],        %[dest1],          %[eight]      \n\t"

      "gsldlc1    %[src],          0x17(%[src_argb])               \n\t"
      "gsldrc1    %[src],          0x10(%[src_argb])               \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask]       \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask]       \n\t"
      "punpcklwd  %[src],          %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[dest2],        %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest2],        %[dest2],          %[src]        \n\t"
      "psrlw      %[dest2],        %[dest2],          %[eight]      \n\t"

      "gsldlc1    %[src],          0x1f(%[src_argb])               \n\t"
      "gsldrc1    %[src],          0x18(%[src_argb])               \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask]       \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask]       \n\t"
      "punpcklwd  %[src],          %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[dest3],        %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest3],        %[dest3],          %[src]        \n\t"
      "psrlw      %[dest3],        %[dest3],          %[eight]      \n\t"

      "packsswh   %[src_lo],       %[dest0],          %[dest1]      \n\t"
      "packsswh   %[src_hi],       %[dest2],          %[dest3]      \n\t"
      "packushb   %[dest0],        %[src_lo],         %[src_hi]     \n\t"
      "gssdlc1    %[dest0],        0x07(%[dst_y])                   \n\t"
      "gssdrc1    %[dest0],        0x00(%[dst_y])                   \n\t"

      "daddiu     %[src_argb],    %[src_argb],      0x20          \n\t"
      "daddiu     %[dst_y],        %[dst_y],          0x08          \n\t"
      "daddi      %[width],        %[width],         -0x08          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src] "=&f"(src), [src_hi] "=&f"(src_hi), [src_lo] "=&f"(src_lo),
        [dest0] "=&f"(dest0), [dest1] "=&f"(dest1), [dest2] "=&f"(dest2),
        [dest3] "=&f"(dest3)
      : [src_argb] "r"(src_argb), [dst_y] "r"(dst_y), [width] "r"(width),
        [mask] "f"(mask), [value] "f"(value), [eight] "f"(0x08),
        [zero] "f"(0x00)
      : "memory");
}

void ARGBToUVRow_MMI(const uint8_t* src_rgb,
                     int src_stride_rgb,
                     uint8_t* dst_u,
                     uint8_t* dst_v,
                     int width) {
  uint64_t src_rgb1;
  uint64_t ftmp[13];
  uint64_t tmp[1];
  const uint64_t value = 0x4040;
  const uint64_t mask_u = 0x0013002500380002;
  const uint64_t mask_v = 0x00020038002f0009;

  __asm__ volatile(
      "dli        %[tmp0],         0x0001000100010001                   \n\t"
      "dmtc1      %[tmp0],         %[ftmp12]                            \n\t"
      "1:                                                               \n\t"
      "daddu      %[src_rgb1],     %[src_rgb],       %[src_stride_rgb] \n\t"
      "gsldrc1    %[src0],         0x00(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x07(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x00(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x07(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "dsll       %[dest0_u],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[dest0_u],      %[dest0_u],        %[value]          \n\t"
      "pinsrh_3   %[dest0_v],      %[src0],           %[value]          \n\t"
      "pmaddhw    %[dest0_u],      %[dest0_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest0_v],      %[dest0_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x08(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x0f(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x08(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x0f(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "dsll       %[src_lo],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[src_lo],       %[src_lo],         %[value]          \n\t"
      "pinsrh_3   %[src_hi],       %[src0],           %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest0_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest0_u],        %[src_lo]         \n\t"
      "psubw      %[dest0_u],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest0_u],      %[dest0_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest0_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest0_v],        %[src_hi]         \n\t"
      "psubw      %[dest0_v],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest0_v],      %[dest0_v],        %[eight]          \n\t"

      "gsldrc1    %[src0],         0x10(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x17(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x10(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x17(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "dsll       %[dest1_u],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[dest1_u],      %[dest1_u],        %[value]          \n\t"
      "pinsrh_3   %[dest1_v],      %[src0],           %[value]          \n\t"
      "pmaddhw    %[dest1_u],      %[dest1_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest1_v],      %[dest1_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x18(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x1f(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x18(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x1f(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "dsll       %[src_lo],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[src_lo],       %[src_lo],         %[value]          \n\t"
      "pinsrh_3   %[src_hi],       %[src0],           %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest1_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest1_u],        %[src_lo]         \n\t"
      "psubw      %[dest1_u],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest1_u],      %[dest1_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest1_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest1_v],        %[src_hi]         \n\t"
      "psubw      %[dest1_v],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest1_v],      %[dest1_v],        %[eight]          \n\t"

      "gsldrc1    %[src0],         0x20(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x27(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x20(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x27(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "dsll       %[dest2_u],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[dest2_u],      %[dest2_u],        %[value]          \n\t"
      "pinsrh_3   %[dest2_v],      %[src0],           %[value]          \n\t"
      "pmaddhw    %[dest2_u],      %[dest2_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest2_v],      %[dest2_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x28(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x2f(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x28(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x2f(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "dsll       %[src_lo],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[src_lo],       %[src_lo],         %[value]          \n\t"
      "pinsrh_3   %[src_hi],       %[src0],           %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest2_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest2_u],        %[src_lo]         \n\t"
      "psubw      %[dest2_u],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest2_u],      %[dest2_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest2_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest2_v],        %[src_hi]         \n\t"
      "psubw      %[dest2_v],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest2_v],      %[dest2_v],        %[eight]          \n\t"

      "gsldrc1    %[src0],         0x30(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x37(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x30(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x37(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "dsll       %[dest3_u],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[dest3_u],      %[dest3_u],        %[value]          \n\t"
      "pinsrh_3   %[dest3_v],      %[src0],           %[value]          \n\t"
      "pmaddhw    %[dest3_u],      %[dest3_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest3_v],      %[dest3_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x38(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x3f(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x38(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x3f(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "dsll       %[src_lo],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[src_lo],       %[src_lo],         %[value]          \n\t"
      "pinsrh_3   %[src_hi],       %[src0],           %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest3_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest3_u],        %[src_lo]         \n\t"
      "psubw      %[dest3_u],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest3_u],      %[dest3_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest3_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest3_v],        %[src_hi]         \n\t"
      "psubw      %[dest3_v],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest3_v],      %[dest3_v],        %[eight]          \n\t"

      "packsswh   %[src0],         %[dest0_u],        %[dest1_u]        \n\t"
      "packsswh   %[src1],         %[dest2_u],        %[dest3_u]        \n\t"
      "packushb   %[dest0_u],      %[src0],           %[src1]           \n\t"
      "gssdlc1    %[dest0_u],      0x07(%[dst_u])                       \n\t"
      "gssdrc1    %[dest0_u],      0x00(%[dst_u])                       \n\t"

      "packsswh   %[src0],         %[dest0_v],        %[dest1_v]        \n\t"
      "packsswh   %[src1],         %[dest2_v],        %[dest3_v]        \n\t"
      "packushb   %[dest0_v],      %[src0],           %[src1]           \n\t"
      "gssdlc1    %[dest0_v],      0x07(%[dst_v])                       \n\t"
      "gssdrc1    %[dest0_v],      0x00(%[dst_v])                       \n\t"

      "daddiu     %[src_rgb],     %[src_rgb],       0x40              \n\t"
      "daddiu     %[dst_u],        %[dst_u],          0x08              \n\t"
      "daddiu     %[dst_v],        %[dst_v],          0x08              \n\t"
      "daddi      %[width],        %[width],         -0x10              \n\t"
      "bgtz       %[width],        1b                                   \n\t"
      : [src_rgb1] "=&r"(src_rgb1), [src0] "=&f"(ftmp[0]),
        [src1] "=&f"(ftmp[1]), [src_lo] "=&f"(ftmp[2]), [src_hi] "=&f"(ftmp[3]),
        [dest0_u] "=&f"(ftmp[4]), [dest0_v] "=&f"(ftmp[5]),
        [dest1_u] "=&f"(ftmp[6]), [dest1_v] "=&f"(ftmp[7]),
        [dest2_u] "=&f"(ftmp[8]), [dest2_v] "=&f"(ftmp[9]),
        [dest3_u] "=&f"(ftmp[10]), [dest3_v] "=&f"(ftmp[11]),
        [ftmp12] "=&f"(ftmp[12]), [tmp0] "=&r"(tmp[0])
      : [src_rgb] "r"(src_rgb), [src_stride_rgb] "r"(src_stride_rgb),
        [dst_u] "r"(dst_u), [dst_v] "r"(dst_v), [width] "r"(width),
        [mask_u] "f"(mask_u), [mask_v] "f"(mask_v), [value] "f"(value),
        [zero] "f"(0x00), [eight] "f"(0x08), [one] "f"(0x01),
        [sixteen] "f"(0x10)
      : "memory");
}

void BGRAToYRow_MMI(const uint8_t* src_argb, uint8_t* dst_y, int width) {
  uint64_t src, src_hi, src_lo;
  uint64_t dest0, dest1, dest2, dest3;
  const uint64_t value = 0x1080;
  const uint64_t mask = 0x0019008100420001;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[src],          0x07(%[src_argb])               \n\t"
      "gsldrc1    %[src],          0x00(%[src_argb])               \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[zero]       \n\t"
      "pinsrh_0   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask]       \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[zero]       \n\t"
      "pinsrh_0   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask]       \n\t"
      "punpcklwd  %[src],          %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[dest0],        %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest0],        %[dest0],          %[src]        \n\t"
      "psrlw      %[dest0],        %[dest0],          %[eight]      \n\t"

      "gsldlc1    %[src],          0x0f(%[src_argb])               \n\t"
      "gsldrc1    %[src],          0x08(%[src_argb])               \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[zero]       \n\t"
      "pinsrh_0   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask]       \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[zero]       \n\t"
      "pinsrh_0   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask]       \n\t"
      "punpcklwd  %[src],          %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[dest1],        %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest1],        %[dest1],          %[src]        \n\t"
      "psrlw      %[dest1],        %[dest1],          %[eight]      \n\t"

      "gsldlc1    %[src],          0x17(%[src_argb])               \n\t"
      "gsldrc1    %[src],          0x10(%[src_argb])               \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[zero]       \n\t"
      "pinsrh_0   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask]       \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[zero]       \n\t"
      "pinsrh_0   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask]       \n\t"
      "punpcklwd  %[src],          %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[dest2],        %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest2],        %[dest2],          %[src]        \n\t"
      "psrlw      %[dest2],        %[dest2],          %[eight]      \n\t"

      "gsldlc1    %[src],          0x1f(%[src_argb])               \n\t"
      "gsldrc1    %[src],          0x18(%[src_argb])               \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[zero]       \n\t"
      "pinsrh_0   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask]       \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[zero]       \n\t"
      "pinsrh_0   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask]       \n\t"
      "punpcklwd  %[src],          %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[dest3],        %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest3],        %[dest3],          %[src]        \n\t"
      "psrlw      %[dest3],        %[dest3],          %[eight]      \n\t"

      "packsswh   %[src_lo],       %[dest0],          %[dest1]      \n\t"
      "packsswh   %[src_hi],       %[dest2],          %[dest3]      \n\t"
      "packushb   %[dest0],        %[src_lo],         %[src_hi]     \n\t"
      "gssdlc1    %[dest0],        0x07(%[dst_y])                   \n\t"
      "gssdrc1    %[dest0],        0x00(%[dst_y])                   \n\t"

      "daddiu     %[src_argb],    %[src_argb],      0x20          \n\t"
      "daddiu     %[dst_y],        %[dst_y],          0x08          \n\t"
      "daddi      %[width],        %[width],         -0x08          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src] "=&f"(src), [src_hi] "=&f"(src_hi), [src_lo] "=&f"(src_lo),
        [dest0] "=&f"(dest0), [dest1] "=&f"(dest1), [dest2] "=&f"(dest2),
        [dest3] "=&f"(dest3)
      : [src_argb] "r"(src_argb), [dst_y] "r"(dst_y), [width] "r"(width),
        [mask] "f"(mask), [value] "f"(value), [eight] "f"(0x08),
        [zero] "f"(0x00)
      : "memory");
}

void BGRAToUVRow_MMI(const uint8_t* src_rgb,
                     int src_stride_rgb,
                     uint8_t* dst_u,
                     uint8_t* dst_v,
                     int width) {
  uint64_t src_rgb1;
  uint64_t ftmp[13];
  uint64_t tmp[1];
  const uint64_t value = 0x4040;
  const uint64_t mask_u = 0x0002003800250013;
  const uint64_t mask_v = 0x0009002f00380002;

  __asm__ volatile(
      "dli        %[tmp0],         0x0001000100010001                   \n\t"
      "dmtc1      %[tmp0],         %[ftmp12]                            \n\t"
      "1:                                                               \n\t"
      "daddu      %[src_rgb1],     %[src_rgb],       %[src_stride_rgb] \n\t"
      "gsldrc1    %[src0],         0x00(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x07(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x00(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x07(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "dsrl       %[dest0_u],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_3   %[dest0_u],      %[dest0_u],        %[value]          \n\t"
      "pinsrh_0   %[dest0_v],      %[src0],           %[value]          \n\t"
      "pmaddhw    %[dest0_u],      %[dest0_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest0_v],      %[dest0_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x08(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x0f(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x08(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x0f(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "dsrl       %[src_lo],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[value]          \n\t"
      "pinsrh_0   %[src_hi],       %[src0],           %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest0_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest0_u],        %[src_lo]         \n\t"
      "psubw      %[dest0_u],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest0_u],      %[dest0_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest0_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest0_v],        %[src_hi]         \n\t"
      "psubw      %[dest0_v],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest0_v],      %[dest0_v],        %[eight]          \n\t"

      "gsldrc1    %[src0],         0x10(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x17(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x10(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x17(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "dsrl       %[dest1_u],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_3   %[dest1_u],      %[dest1_u],        %[value]          \n\t"
      "pinsrh_0   %[dest1_v],      %[src0],           %[value]          \n\t"
      "pmaddhw    %[dest1_u],      %[dest1_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest1_v],      %[dest1_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x18(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x1f(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x18(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x1f(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "dsrl       %[src_lo],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[value]          \n\t"
      "pinsrh_0   %[src_hi],       %[src0],           %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest1_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest1_u],        %[src_lo]         \n\t"
      "psubw      %[dest1_u],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest1_u],      %[dest1_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest1_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest1_v],        %[src_hi]         \n\t"
      "psubw      %[dest1_v],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest1_v],      %[dest1_v],        %[eight]          \n\t"

      "gsldrc1    %[src0],         0x20(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x27(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x20(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x27(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "dsrl       %[dest2_u],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_3   %[dest2_u],      %[dest2_u],        %[value]          \n\t"
      "pinsrh_0   %[dest2_v],      %[src0],           %[value]          \n\t"
      "pmaddhw    %[dest2_u],      %[dest2_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest2_v],      %[dest2_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x28(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x2f(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x28(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x2f(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]        \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "dsrl       %[src_lo],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[value]          \n\t"
      "pinsrh_0   %[src_hi],       %[src0],           %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest2_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest2_u],        %[src_lo]         \n\t"
      "psubw      %[dest2_u],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest2_u],      %[dest2_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest2_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest2_v],        %[src_hi]         \n\t"
      "psubw      %[dest2_v],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest2_v],      %[dest2_v],        %[eight]          \n\t"

      "gsldrc1    %[src0],         0x30(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x37(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x30(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x37(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "dsrl       %[dest3_u],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_3   %[dest3_u],      %[dest3_u],        %[value]          \n\t"
      "pinsrh_0   %[dest3_v],      %[src0],           %[value]          \n\t"
      "pmaddhw    %[dest3_u],      %[dest3_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest3_v],      %[dest3_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x38(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x3f(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x38(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x3f(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "dsrl       %[src_lo],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[value]          \n\t"
      "pinsrh_0   %[src_hi],       %[src0],           %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest3_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest3_u],        %[src_lo]         \n\t"
      "psubw      %[dest3_u],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest3_u],      %[dest3_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest3_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest3_v],        %[src_hi]         \n\t"
      "psubw      %[dest3_v],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest3_v],      %[dest3_v],        %[eight]          \n\t"

      "packsswh   %[src0],         %[dest0_u],        %[dest1_u]        \n\t"
      "packsswh   %[src1],         %[dest2_u],        %[dest3_u]        \n\t"
      "packushb   %[dest0_u],      %[src0],           %[src1]           \n\t"
      "gssdlc1    %[dest0_u],      0x07(%[dst_u])                       \n\t"
      "gssdrc1    %[dest0_u],      0x00(%[dst_u])                       \n\t"

      "packsswh   %[src0],         %[dest0_v],        %[dest1_v]        \n\t"
      "packsswh   %[src1],         %[dest2_v],        %[dest3_v]        \n\t"
      "packushb   %[dest0_v],      %[src0],           %[src1]           \n\t"
      "gssdlc1    %[dest0_v],      0x07(%[dst_v])                       \n\t"
      "gssdrc1    %[dest0_v],      0x00(%[dst_v])                       \n\t"

      "daddiu     %[src_rgb],     %[src_rgb],       0x40              \n\t"
      "daddiu     %[dst_u],        %[dst_u],          0x08              \n\t"
      "daddiu     %[dst_v],        %[dst_v],          0x08              \n\t"
      "daddi      %[width],        %[width],         -0x10              \n\t"
      "bgtz       %[width],        1b                                   \n\t"
      : [src_rgb1] "=&r"(src_rgb1), [src0] "=&f"(ftmp[0]),
        [src1] "=&f"(ftmp[1]), [src_lo] "=&f"(ftmp[2]), [src_hi] "=&f"(ftmp[3]),
        [dest0_u] "=&f"(ftmp[4]), [dest0_v] "=&f"(ftmp[5]),
        [dest1_u] "=&f"(ftmp[6]), [dest1_v] "=&f"(ftmp[7]),
        [dest2_u] "=&f"(ftmp[8]), [dest2_v] "=&f"(ftmp[9]),
        [dest3_u] "=&f"(ftmp[10]), [dest3_v] "=&f"(ftmp[11]),
        [ftmp12] "=&f"(ftmp[12]), [tmp0] "=&r"(tmp[0])
      : [src_rgb] "r"(src_rgb), [src_stride_rgb] "r"(src_stride_rgb),
        [dst_u] "r"(dst_u), [dst_v] "r"(dst_v), [width] "r"(width),
        [mask_u] "f"(mask_u), [mask_v] "f"(mask_v), [value] "f"(value),
        [zero] "f"(0x00), [eight] "f"(0x08), [one] "f"(0x01),
        [sixteen] "f"(0x10)
      : "memory");
}

void ABGRToYRow_MMI(const uint8_t* src_argb, uint8_t* dst_y, int width) {
  uint64_t src, src_hi, src_lo;
  uint64_t dest0, dest1, dest2, dest3;
  const uint64_t value = 0x1080;
  const uint64_t mask = 0x0001001900810042;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[src],          0x07(%[src_argb])               \n\t"
      "gsldrc1    %[src],          0x00(%[src_argb])               \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask]       \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask]       \n\t"
      "punpcklwd  %[src],          %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[dest0],        %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest0],        %[dest0],          %[src]        \n\t"
      "psrlw      %[dest0],        %[dest0],          %[eight]      \n\t"

      "gsldlc1    %[src],          0x0f(%[src_argb])               \n\t"
      "gsldrc1    %[src],          0x08(%[src_argb])               \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask]       \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask]       \n\t"
      "punpcklwd  %[src],          %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[dest1],        %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest1],        %[dest1],          %[src]        \n\t"
      "psrlw      %[dest1],        %[dest1],          %[eight]      \n\t"

      "gsldlc1    %[src],          0x17(%[src_argb])               \n\t"
      "gsldrc1    %[src],          0x10(%[src_argb])               \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask]       \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask]       \n\t"
      "punpcklwd  %[src],          %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[dest2],        %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest2],        %[dest2],          %[src]        \n\t"
      "psrlw      %[dest2],        %[dest2],          %[eight]      \n\t"

      "gsldlc1    %[src],          0x1f(%[src_argb])               \n\t"
      "gsldrc1    %[src],          0x18(%[src_argb])               \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask]       \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask]       \n\t"
      "punpcklwd  %[src],          %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[dest3],        %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest3],        %[dest3],          %[src]        \n\t"
      "psrlw      %[dest3],        %[dest3],          %[eight]      \n\t"

      "packsswh   %[src_lo],       %[dest0],          %[dest1]      \n\t"
      "packsswh   %[src_hi],       %[dest2],          %[dest3]      \n\t"
      "packushb   %[dest0],        %[src_lo],         %[src_hi]     \n\t"
      "gssdlc1    %[dest0],        0x07(%[dst_y])                   \n\t"
      "gssdrc1    %[dest0],        0x00(%[dst_y])                   \n\t"

      "daddiu     %[src_argb],    %[src_argb],      0x20          \n\t"
      "daddiu     %[dst_y],        %[dst_y],          0x08          \n\t"
      "daddi      %[width],        %[width],         -0x08          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src] "=&f"(src), [src_hi] "=&f"(src_hi), [src_lo] "=&f"(src_lo),
        [dest0] "=&f"(dest0), [dest1] "=&f"(dest1), [dest2] "=&f"(dest2),
        [dest3] "=&f"(dest3)
      : [src_argb] "r"(src_argb), [dst_y] "r"(dst_y), [width] "r"(width),
        [mask] "f"(mask), [value] "f"(value), [eight] "f"(0x08),
        [zero] "f"(0x00)
      : "memory");
}

void ABGRToUVRow_MMI(const uint8_t* src_rgb,
                     int src_stride_rgb,
                     uint8_t* dst_u,
                     uint8_t* dst_v,
                     int width) {
  uint64_t src_rgb1;
  uint64_t ftmp[13];
  uint64_t tmp[1];
  const uint64_t value = 0x4040;
  const uint64_t mask_u = 0x0002003800250013;
  const uint64_t mask_v = 0x0009002F00380002;

  __asm__ volatile(
      "dli        %[tmp0],         0x0001000100010001                   \n\t"
      "dmtc1      %[tmp0],         %[ftmp12]                            \n\t"
      "1:                                                               \n\t"
      "daddu      %[src_rgb1],     %[src_rgb],       %[src_stride_rgb] \n\t"
      "gsldrc1    %[src0],         0x00(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x07(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x00(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x07(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "pinsrh_3   %[dest0_u],      %[src0],           %[value]          \n\t"
      "dsll       %[dest0_v],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[dest0_v],      %[dest0_v],        %[value]          \n\t"
      "pmaddhw    %[dest0_u],      %[dest0_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest0_v],      %[dest0_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x08(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x0f(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x08(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x0f(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "pinsrh_3   %[src_lo],       %[src0],           %[value]          \n\t"
      "dsll       %[src_hi],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[src_hi],       %[src_hi],         %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest0_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest0_u],        %[src_lo]         \n\t"
      "psubw      %[dest0_u],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest0_u],      %[dest0_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest0_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest0_v],        %[src_hi]         \n\t"
      "psubw      %[dest0_v],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest0_v],      %[dest0_v],        %[eight]          \n\t"

      "gsldrc1    %[src0],         0x10(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x17(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x10(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x17(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "pinsrh_3   %[dest1_u],      %[src0],           %[value]          \n\t"
      "dsll       %[dest1_v],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[dest1_v],      %[dest1_v],        %[value]          \n\t"
      "pmaddhw    %[dest1_u],      %[dest1_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest1_v],      %[dest1_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x18(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x1f(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x18(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x1f(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "pinsrh_3   %[src_lo],       %[src0],           %[value]          \n\t"
      "dsll       %[src_hi],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[src_hi],       %[src_hi],         %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest1_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest1_u],        %[src_lo]         \n\t"
      "psubw      %[dest1_u],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest1_u],      %[dest1_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest1_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest1_v],        %[src_hi]         \n\t"
      "psubw      %[dest1_v],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest1_v],      %[dest1_v],        %[eight]          \n\t"

      "gsldrc1    %[src0],         0x20(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x27(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x20(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x27(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "pinsrh_3   %[dest2_u],      %[src0],           %[value]          \n\t"
      "dsll       %[dest2_v],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[dest2_v],      %[dest2_v],        %[value]          \n\t"
      "pmaddhw    %[dest2_u],      %[dest2_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest2_v],      %[dest2_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x28(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x2f(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x28(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x2f(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "pinsrh_3   %[src_lo],       %[src0],           %[value]          \n\t"
      "dsll       %[src_hi],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[src_hi],       %[src_hi],         %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest2_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest2_u],        %[src_lo]         \n\t"
      "psubw      %[dest2_u],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest2_u],      %[dest2_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest2_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest2_v],        %[src_hi]         \n\t"
      "psubw      %[dest2_v],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest2_v],      %[dest2_v],        %[eight]          \n\t"

      "gsldrc1    %[src0],         0x30(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x37(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x30(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x37(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "pinsrh_3   %[dest3_u],      %[src0],           %[value]          \n\t"
      "dsll       %[dest3_v],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[dest3_v],      %[dest3_v],        %[value]          \n\t"
      "pmaddhw    %[dest3_u],      %[dest3_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest3_v],      %[dest3_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x38(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x3f(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x38(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x3f(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "pinsrh_3   %[src_lo],       %[src0],           %[value]          \n\t"
      "dsll       %[src_hi],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[src_hi],       %[src_hi],         %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest3_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest3_u],        %[src_lo]         \n\t"
      "psubw      %[dest3_u],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest3_u],      %[dest3_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest3_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest3_v],        %[src_hi]         \n\t"
      "psubw      %[dest3_v],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest3_v],      %[dest3_v],        %[eight]          \n\t"

      "packsswh   %[src0],         %[dest0_u],        %[dest1_u]        \n\t"
      "packsswh   %[src1],         %[dest2_u],        %[dest3_u]        \n\t"
      "packushb   %[dest0_u],      %[src0],           %[src1]           \n\t"
      "gssdlc1    %[dest0_u],      0x07(%[dst_u])                       \n\t"
      "gssdrc1    %[dest0_u],      0x00(%[dst_u])                       \n\t"

      "packsswh   %[src0],         %[dest0_v],        %[dest1_v]        \n\t"
      "packsswh   %[src1],         %[dest2_v],        %[dest3_v]        \n\t"
      "packushb   %[dest0_v],      %[src0],           %[src1]           \n\t"
      "gssdlc1    %[dest0_v],      0x07(%[dst_v])                       \n\t"
      "gssdrc1    %[dest0_v],      0x00(%[dst_v])                       \n\t"

      "daddiu     %[src_rgb],     %[src_rgb],       0x40              \n\t"
      "daddiu     %[dst_u],        %[dst_u],          0x08              \n\t"
      "daddiu     %[dst_v],        %[dst_v],          0x08              \n\t"
      "daddi      %[width],        %[width],         -0x10              \n\t"
      "bgtz       %[width],        1b                                   \n\t"
      : [src_rgb1] "=&r"(src_rgb1), [src0] "=&f"(ftmp[0]),
        [src1] "=&f"(ftmp[1]), [src_lo] "=&f"(ftmp[2]), [src_hi] "=&f"(ftmp[3]),
        [dest0_u] "=&f"(ftmp[4]), [dest0_v] "=&f"(ftmp[5]),
        [dest1_u] "=&f"(ftmp[6]), [dest1_v] "=&f"(ftmp[7]),
        [dest2_u] "=&f"(ftmp[8]), [dest2_v] "=&f"(ftmp[9]),
        [dest3_u] "=&f"(ftmp[10]), [dest3_v] "=&f"(ftmp[11]),
        [ftmp12] "=&f"(ftmp[12]), [tmp0] "=&r"(tmp[0])
      : [src_rgb] "r"(src_rgb), [src_stride_rgb] "r"(src_stride_rgb),
        [dst_u] "r"(dst_u), [dst_v] "r"(dst_v), [width] "r"(width),
        [mask_u] "f"(mask_u), [mask_v] "f"(mask_v), [value] "f"(value),
        [zero] "f"(0x00), [eight] "f"(0x08), [one] "f"(0x01),
        [sixteen] "f"(0x10)
      : "memory");
}

void RGBAToYRow_MMI(const uint8_t* src_argb, uint8_t* dst_y, int width) {
  uint64_t src, src_hi, src_lo;
  uint64_t dest0, dest1, dest2, dest3;
  const uint64_t value = 0x1080;
  const uint64_t mask = 0x0042008100190001;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[src],          0x07(%[src_argb])               \n\t"
      "gsldrc1    %[src],          0x00(%[src_argb])               \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[zero]       \n\t"
      "pinsrh_0   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask]       \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[zero]       \n\t"
      "pinsrh_0   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask]       \n\t"
      "punpcklwd  %[src],          %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[dest0],        %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest0],        %[dest0],          %[src]        \n\t"
      "psrlw      %[dest0],        %[dest0],          %[eight]      \n\t"

      "gsldlc1    %[src],          0x0f(%[src_argb])               \n\t"
      "gsldrc1    %[src],          0x08(%[src_argb])               \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[zero]       \n\t"
      "pinsrh_0   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask]       \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[zero]       \n\t"
      "pinsrh_0   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask]       \n\t"
      "punpcklwd  %[src],          %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[dest1],        %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest1],        %[dest1],          %[src]        \n\t"
      "psrlw      %[dest1],        %[dest1],          %[eight]      \n\t"

      "gsldlc1    %[src],          0x17(%[src_argb])               \n\t"
      "gsldrc1    %[src],          0x10(%[src_argb])               \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[zero]       \n\t"
      "pinsrh_0   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask]       \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[zero]       \n\t"
      "pinsrh_0   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask]       \n\t"
      "punpcklwd  %[src],          %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[dest2],        %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest2],        %[dest2],          %[src]        \n\t"
      "psrlw      %[dest2],        %[dest2],          %[eight]      \n\t"

      "gsldlc1    %[src],          0x1f(%[src_argb])               \n\t"
      "gsldrc1    %[src],          0x18(%[src_argb])               \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[zero]       \n\t"
      "pinsrh_0   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask]       \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[zero]       \n\t"
      "pinsrh_0   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask]       \n\t"
      "punpcklwd  %[src],          %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[dest3],        %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest3],        %[dest3],          %[src]        \n\t"
      "psrlw      %[dest3],        %[dest3],          %[eight]      \n\t"

      "packsswh   %[src_lo],       %[dest0],          %[dest1]      \n\t"
      "packsswh   %[src_hi],       %[dest2],          %[dest3]      \n\t"
      "packushb   %[dest0],        %[src_lo],         %[src_hi]     \n\t"
      "gssdlc1    %[dest0],        0x07(%[dst_y])                   \n\t"
      "gssdrc1    %[dest0],        0x00(%[dst_y])                   \n\t"

      "daddiu     %[src_argb],    %[src_argb],      0x20          \n\t"
      "daddiu     %[dst_y],        %[dst_y],          0x08          \n\t"
      "daddi      %[width],        %[width],         -0x08          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src] "=&f"(src), [src_hi] "=&f"(src_hi), [src_lo] "=&f"(src_lo),
        [dest0] "=&f"(dest0), [dest1] "=&f"(dest1), [dest2] "=&f"(dest2),
        [dest3] "=&f"(dest3)
      : [src_argb] "r"(src_argb), [dst_y] "r"(dst_y), [width] "r"(width),
        [mask] "f"(mask), [value] "f"(value), [eight] "f"(0x08),
        [zero] "f"(0x00)
      : "memory");
}

void RGBAToUVRow_MMI(const uint8_t* src_rgb,
                     int src_stride_rgb,
                     uint8_t* dst_u,
                     uint8_t* dst_v,
                     int width) {
  uint64_t src_rgb1;
  uint64_t ftmp[13];
  uint64_t tmp[1];
  const uint64_t value = 0x4040;
  const uint64_t mask_u = 0x0013002500380002;
  const uint64_t mask_v = 0x00020038002f0009;

  __asm__ volatile(
      "dli        %[tmp0],         0x0001000100010001                   \n\t"
      "dmtc1      %[tmp0],         %[ftmp12]                            \n\t"
      "1:                                                               \n\t"
      "daddu      %[src_rgb1],     %[src_rgb],       %[src_stride_rgb] \n\t"
      "gsldrc1    %[src0],         0x00(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x07(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x00(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x07(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "pinsrh_0   %[dest0_u],      %[src0],           %[value]          \n\t"
      "dsrl       %[dest0_v],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_3   %[dest0_v],      %[dest0_v],        %[value]          \n\t"
      "pmaddhw    %[dest0_u],      %[dest0_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest0_v],      %[dest0_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x08(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x0f(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x08(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x0f(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "pinsrh_0   %[src_lo],       %[src0],           %[value]          \n\t"
      "dsrl       %[src_hi],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest0_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest0_u],        %[src_lo]         \n\t"
      "psubw      %[dest0_u],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest0_u],      %[dest0_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest0_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest0_v],        %[src_hi]         \n\t"
      "psubw      %[dest0_v],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest0_v],      %[dest0_v],        %[eight]          \n\t"

      "gsldrc1    %[src0],         0x10(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x17(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x10(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x17(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "pinsrh_0   %[dest1_u],      %[src0],           %[value]          \n\t"
      "dsrl       %[dest1_v],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_3   %[dest1_v],      %[dest1_v],        %[value]          \n\t"
      "pmaddhw    %[dest1_u],      %[dest1_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest1_v],      %[dest1_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x18(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x1f(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x18(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x1f(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "pinsrh_0   %[src_lo],       %[src0],           %[value]          \n\t"
      "dsrl       %[src_hi],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest1_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest1_u],        %[src_lo]         \n\t"
      "psubw      %[dest1_u],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest1_u],      %[dest1_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest1_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest1_v],        %[src_hi]         \n\t"
      "psubw      %[dest1_v],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest1_v],      %[dest1_v],        %[eight]          \n\t"

      "gsldrc1    %[src0],         0x20(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x27(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x20(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x27(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "pinsrh_0   %[dest2_u],      %[src0],           %[value]          \n\t"
      "dsrl       %[dest2_v],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_3   %[dest2_v],      %[dest2_v],        %[value]          \n\t"
      "pmaddhw    %[dest2_u],      %[dest2_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest2_v],      %[dest2_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x28(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x2f(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x28(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x2f(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "pinsrh_0   %[src_lo],       %[src0],           %[value]          \n\t"
      "dsrl       %[src_hi],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest2_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest2_u],        %[src_lo]         \n\t"
      "psubw      %[dest2_u],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest2_u],      %[dest2_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest2_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest2_v],        %[src_hi]         \n\t"
      "psubw      %[dest2_v],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest2_v],      %[dest2_v],        %[eight]          \n\t"

      "gsldrc1    %[src0],         0x30(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x37(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x30(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x37(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "pinsrh_0   %[dest3_u],      %[src0],           %[value]          \n\t"
      "dsrl       %[dest3_v],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_3   %[dest3_v],      %[dest3_v],        %[value]          \n\t"
      "pmaddhw    %[dest3_u],      %[dest3_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest3_v],      %[dest3_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x38(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x3f(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x38(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x3f(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "pinsrh_0   %[src_lo],       %[src0],           %[value]          \n\t"
      "dsrl       %[src_hi],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest3_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest3_u],        %[src_lo]         \n\t"
      "psubw      %[dest3_u],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest3_u],      %[dest3_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest3_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest3_v],        %[src_hi]         \n\t"
      "psubw      %[dest3_v],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest3_v],      %[dest3_v],        %[eight]          \n\t"

      "packsswh   %[src0],         %[dest0_u],        %[dest1_u]        \n\t"
      "packsswh   %[src1],         %[dest2_u],        %[dest3_u]        \n\t"
      "packushb   %[dest0_u],      %[src0],           %[src1]           \n\t"
      "gssdlc1    %[dest0_u],      0x07(%[dst_u])                       \n\t"
      "gssdrc1    %[dest0_u],      0x00(%[dst_u])                       \n\t"

      "packsswh   %[src0],         %[dest0_v],        %[dest1_v]        \n\t"
      "packsswh   %[src1],         %[dest2_v],        %[dest3_v]        \n\t"
      "packushb   %[dest0_v],      %[src0],           %[src1]           \n\t"
      "gssdlc1    %[dest0_v],      0x07(%[dst_v])                       \n\t"
      "gssdrc1    %[dest0_v],      0x00(%[dst_v])                       \n\t"

      "daddiu     %[src_rgb],     %[src_rgb],       0x40              \n\t"
      "daddiu     %[dst_u],        %[dst_u],          0x08              \n\t"
      "daddiu     %[dst_v],        %[dst_v],          0x08              \n\t"
      "daddi      %[width],        %[width],         -0x10              \n\t"
      "bgtz       %[width],        1b                                   \n\t"
      : [src_rgb1] "=&r"(src_rgb1), [src0] "=&f"(ftmp[0]),
        [src1] "=&f"(ftmp[1]), [src_lo] "=&f"(ftmp[2]), [src_hi] "=&f"(ftmp[3]),
        [dest0_u] "=&f"(ftmp[4]), [dest0_v] "=&f"(ftmp[5]),
        [dest1_u] "=&f"(ftmp[6]), [dest1_v] "=&f"(ftmp[7]),
        [dest2_u] "=&f"(ftmp[8]), [dest2_v] "=&f"(ftmp[9]),
        [dest3_u] "=&f"(ftmp[10]), [dest3_v] "=&f"(ftmp[11]),
        [ftmp12] "=&f"(ftmp[12]), [tmp0] "=&r"(tmp[0])
      : [src_rgb] "r"(src_rgb), [src_stride_rgb] "r"(src_stride_rgb),
        [dst_u] "r"(dst_u), [dst_v] "r"(dst_v), [width] "r"(width),
        [mask_u] "f"(mask_u), [mask_v] "f"(mask_v), [value] "f"(value),
        [zero] "f"(0x00), [eight] "f"(0x08), [one] "f"(0x01),
        [sixteen] "f"(0x10)
      : "memory");
}

void RGB24ToYRow_MMI(const uint8_t* src_argb, uint8_t* dst_y, int width) {
  uint64_t src, src_hi, src_lo;
  uint64_t dest0, dest1, dest2, dest3;
  const uint64_t value = 0x1080;
  const uint64_t mask = 0x0001004200810019;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[src],          0x07(%[src_argb])               \n\t"
      "gsldrc1    %[src],          0x00(%[src_argb])               \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask]       \n\t"
      "dsll       %[src],          %[src],            %[eight]      \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask]       \n\t"
      "punpcklwd  %[src],          %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[dest0],        %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest0],        %[dest0],          %[src]        \n\t"
      "psrlw      %[dest0],        %[dest0],          %[eight]      \n\t"

      "gsldlc1    %[src],          0x0d(%[src_argb])               \n\t"
      "gsldrc1    %[src],          0x06(%[src_argb])               \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask]       \n\t"
      "dsll       %[src],          %[src],            %[eight]      \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask]       \n\t"
      "punpcklwd  %[src],          %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[dest1],        %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest1],        %[dest1],          %[src]        \n\t"
      "psrlw      %[dest1],        %[dest1],          %[eight]      \n\t"

      "gsldlc1    %[src],          0x13(%[src_argb])               \n\t"
      "gsldrc1    %[src],          0x0c(%[src_argb])               \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask]       \n\t"
      "dsll       %[src],          %[src],            %[eight]      \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask]       \n\t"
      "punpcklwd  %[src],          %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[dest2],        %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest2],        %[dest2],          %[src]        \n\t"
      "psrlw      %[dest2],        %[dest2],          %[eight]      \n\t"

      "gsldlc1    %[src],          0x19(%[src_argb])               \n\t"
      "gsldrc1    %[src],          0x12(%[src_argb])               \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask]       \n\t"
      "dsll       %[src],          %[src],            %[eight]      \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask]       \n\t"
      "punpcklwd  %[src],          %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[dest3],        %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest3],        %[dest3],          %[src]        \n\t"
      "psrlw      %[dest3],        %[dest3],          %[eight]      \n\t"

      "packsswh   %[src_lo],       %[dest0],          %[dest1]      \n\t"
      "packsswh   %[src_hi],       %[dest2],          %[dest3]      \n\t"
      "packushb   %[dest0],        %[src_lo],         %[src_hi]     \n\t"
      "gssdlc1    %[dest0],        0x07(%[dst_y])                   \n\t"
      "gssdrc1    %[dest0],        0x00(%[dst_y])                   \n\t"

      "daddiu     %[src_argb],    %[src_argb],      0x18          \n\t"
      "daddiu     %[dst_y],        %[dst_y],          0x08          \n\t"
      "daddi      %[width],        %[width],         -0x08          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src] "=&f"(src), [src_hi] "=&f"(src_hi), [src_lo] "=&f"(src_lo),
        [dest0] "=&f"(dest0), [dest1] "=&f"(dest1), [dest2] "=&f"(dest2),
        [dest3] "=&f"(dest3)
      : [src_argb] "r"(src_argb), [dst_y] "r"(dst_y), [width] "r"(width),
        [mask] "f"(mask), [value] "f"(value), [eight] "f"(0x08),
        [zero] "f"(0x00)
      : "memory");
}

void RGB24ToUVRow_MMI(const uint8_t* src_rgb,
                      int src_stride_rgb,
                      uint8_t* dst_u,
                      uint8_t* dst_v,
                      int width) {
  uint64_t src_rgb1;
  uint64_t ftmp[13];
  uint64_t tmp[1];
  const uint64_t value = 0x4040;
  const uint64_t mask_u = 0x0013002500380002;
  const uint64_t mask_v = 0x00020038002f0009;

  __asm__ volatile(
      "dli        %[tmp0],         0x0001000100010001                   \n\t"
      "dmtc1      %[tmp0],         %[ftmp12]                            \n\t"
      "1:                                                               \n\t"
      "daddu      %[src_rgb1],     %[src_rgb],       %[src_stride_rgb] \n\t"
      "gsldrc1    %[src0],         0x00(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x07(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x00(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x07(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "dsll       %[src0],         %[src0],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "dsll       %[src1],         %[src1],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "dsll       %[dest0_u],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[dest0_u],      %[dest0_u],        %[value]          \n\t"
      "pinsrh_3   %[dest0_v],      %[src0],           %[value]          \n\t"
      "pmaddhw    %[dest0_u],      %[dest0_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest0_v],      %[dest0_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x06(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x0d(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x06(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x0d(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "dsll       %[src0],         %[src0],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "dsll       %[src1],         %[src1],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "dsll       %[src_lo],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[src_lo],       %[src_lo],         %[value]          \n\t"
      "pinsrh_3   %[src_hi],       %[src0],           %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest0_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest0_u],        %[src_lo]         \n\t"
      "psubw      %[dest0_u],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest0_u],      %[dest0_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest0_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest0_v],        %[src_hi]         \n\t"
      "psubw      %[dest0_v],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest0_v],      %[dest0_v],        %[eight]          \n\t"

      "gsldrc1    %[src0],         0x0c(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x13(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x0c(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x13(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "dsll       %[src0],         %[src0],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "dsll       %[src1],         %[src1],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "dsll       %[dest1_u],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[dest1_u],      %[dest1_u],        %[value]          \n\t"
      "pinsrh_3   %[dest1_v],      %[src0],           %[value]          \n\t"
      "pmaddhw    %[dest1_u],      %[dest1_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest1_v],      %[dest1_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x12(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x19(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x12(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x19(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "dsll       %[src0],         %[src0],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "dsll       %[src1],         %[src1],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "dsll       %[src_lo],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[src_lo],       %[src_lo],         %[value]          \n\t"
      "pinsrh_3   %[src_hi],       %[src0],           %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest1_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest1_u],        %[src_lo]         \n\t"
      "psubw      %[dest1_u],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest1_u],      %[dest1_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest1_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest1_v],        %[src_hi]         \n\t"
      "psubw      %[dest1_v],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest1_v],      %[dest1_v],        %[eight]          \n\t"

      "gsldrc1    %[src0],         0x18(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x1f(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x18(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x1f(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "dsll       %[src0],         %[src0],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "dsll       %[src1],         %[src1],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "dsll       %[dest2_u],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[dest2_u],      %[dest2_u],        %[value]          \n\t"
      "pinsrh_3   %[dest2_v],      %[src0],           %[value]          \n\t"
      "pmaddhw    %[dest2_u],      %[dest2_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest2_v],      %[dest2_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x1e(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x25(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x1e(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x25(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "dsll       %[src0],         %[src0],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "dsll       %[src1],         %[src1],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "dsll       %[src_lo],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[src_lo],       %[src_lo],         %[value]          \n\t"
      "pinsrh_3   %[src_hi],       %[src0],           %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest2_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest2_u],        %[src_lo]         \n\t"
      "psubw      %[dest2_u],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest2_u],      %[dest2_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest2_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest2_v],        %[src_hi]         \n\t"
      "psubw      %[dest2_v],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest2_v],      %[dest2_v],        %[eight]          \n\t"

      "gsldrc1    %[src0],         0x24(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x2b(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x24(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x2b(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "dsll       %[src0],         %[src0],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "dsll       %[src1],         %[src1],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "dsll       %[dest3_u],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[dest3_u],      %[dest3_u],        %[value]          \n\t"
      "pinsrh_3   %[dest3_v],      %[src0],           %[value]          \n\t"
      "pmaddhw    %[dest3_u],      %[dest3_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest3_v],      %[dest3_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x2a(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x31(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x2a(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x31(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "dsll       %[src0],         %[src0],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "dsll       %[src1],         %[src1],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "dsll       %[src_lo],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[src_lo],       %[src_lo],         %[value]          \n\t"
      "pinsrh_3   %[src_hi],       %[src0],           %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest3_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest3_u],        %[src_lo]         \n\t"
      "psubw      %[dest3_u],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest3_u],      %[dest3_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest3_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest3_v],        %[src_hi]         \n\t"
      "psubw      %[dest3_v],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest3_v],      %[dest3_v],        %[eight]          \n\t"

      "packsswh   %[src0],         %[dest0_u],        %[dest1_u]        \n\t"
      "packsswh   %[src1],         %[dest2_u],        %[dest3_u]        \n\t"
      "packushb   %[dest0_u],      %[src0],           %[src1]           \n\t"
      "gssdlc1    %[dest0_u],      0x07(%[dst_u])                       \n\t"
      "gssdrc1    %[dest0_u],      0x00(%[dst_u])                       \n\t"

      "packsswh   %[src0],         %[dest0_v],        %[dest1_v]        \n\t"
      "packsswh   %[src1],         %[dest2_v],        %[dest3_v]        \n\t"
      "packushb   %[dest0_v],      %[src0],           %[src1]           \n\t"
      "gssdlc1    %[dest0_v],      0x07(%[dst_v])                       \n\t"
      "gssdrc1    %[dest0_v],      0x00(%[dst_v])                       \n\t"

      "daddiu     %[src_rgb],     %[src_rgb],       0x30              \n\t"
      "daddiu     %[dst_u],        %[dst_u],          0x08              \n\t"
      "daddiu     %[dst_v],        %[dst_v],          0x08              \n\t"
      "daddi      %[width],        %[width],         -0x10              \n\t"
      "bgtz       %[width],        1b                                   \n\t"
      : [src_rgb1] "=&r"(src_rgb1), [src0] "=&f"(ftmp[0]),
        [src1] "=&f"(ftmp[1]), [src_lo] "=&f"(ftmp[2]), [src_hi] "=&f"(ftmp[3]),
        [dest0_u] "=&f"(ftmp[4]), [dest0_v] "=&f"(ftmp[5]),
        [dest1_u] "=&f"(ftmp[6]), [dest1_v] "=&f"(ftmp[7]),
        [dest2_u] "=&f"(ftmp[8]), [dest2_v] "=&f"(ftmp[9]),
        [dest3_u] "=&f"(ftmp[10]), [dest3_v] "=&f"(ftmp[11]),
        [ftmp12] "=&f"(ftmp[12]), [tmp0] "=&r"(tmp[0])
      : [src_rgb] "r"(src_rgb), [src_stride_rgb] "r"(src_stride_rgb),
        [dst_u] "r"(dst_u), [dst_v] "r"(dst_v), [width] "r"(width),
        [mask_u] "f"(mask_u), [mask_v] "f"(mask_v), [value] "f"(value),
        [zero] "f"(0x00), [eight] "f"(0x08), [one] "f"(0x01),
        [sixteen] "f"(0x10)
      : "memory");
}

void RAWToYRow_MMI(const uint8_t* src_argb, uint8_t* dst_y, int width) {
  uint64_t src, src_hi, src_lo;
  uint64_t dest0, dest1, dest2, dest3;
  const uint64_t value = 0x1080;
  const uint64_t mask = 0x0001001900810042;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[src],          0x07(%[src_argb])               \n\t"
      "gsldrc1    %[src],          0x00(%[src_argb])               \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask]       \n\t"
      "dsll       %[src],          %[src],            %[eight]      \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask]       \n\t"
      "punpcklwd  %[src],          %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[dest0],        %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest0],        %[dest0],          %[src]        \n\t"
      "psrlw      %[dest0],        %[dest0],          %[eight]      \n\t"

      "gsldlc1    %[src],          0x0d(%[src_argb])               \n\t"
      "gsldrc1    %[src],          0x06(%[src_argb])               \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask]       \n\t"
      "dsll       %[src],          %[src],            %[eight]      \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask]       \n\t"
      "punpcklwd  %[src],          %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[dest1],        %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest1],        %[dest1],          %[src]        \n\t"
      "psrlw      %[dest1],        %[dest1],          %[eight]      \n\t"

      "gsldlc1    %[src],          0x13(%[src_argb])               \n\t"
      "gsldrc1    %[src],          0x0c(%[src_argb])               \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask]       \n\t"
      "dsll       %[src],          %[src],            %[eight]      \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask]       \n\t"
      "punpcklwd  %[src],          %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[dest2],        %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest2],        %[dest2],          %[src]        \n\t"
      "psrlw      %[dest2],        %[dest2],          %[eight]      \n\t"

      "gsldlc1    %[src],          0x19(%[src_argb])               \n\t"
      "gsldrc1    %[src],          0x12(%[src_argb])               \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask]       \n\t"
      "dsll       %[src],          %[src],            %[eight]      \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[zero]       \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask]       \n\t"
      "punpcklwd  %[src],          %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[dest3],        %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest3],        %[dest3],          %[src]        \n\t"
      "psrlw      %[dest3],        %[dest3],          %[eight]      \n\t"

      "packsswh   %[src_lo],       %[dest0],          %[dest1]      \n\t"
      "packsswh   %[src_hi],       %[dest2],          %[dest3]      \n\t"
      "packushb   %[dest0],        %[src_lo],         %[src_hi]     \n\t"
      "gssdlc1    %[dest0],        0x07(%[dst_y])                   \n\t"
      "gssdrc1    %[dest0],        0x00(%[dst_y])                   \n\t"

      "daddiu     %[src_argb],    %[src_argb],      0x18          \n\t"
      "daddiu     %[dst_y],        %[dst_y],          0x08          \n\t"
      "daddi      %[width],        %[width],         -0x08          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src] "=&f"(src), [src_hi] "=&f"(src_hi), [src_lo] "=&f"(src_lo),
        [dest0] "=&f"(dest0), [dest1] "=&f"(dest1), [dest2] "=&f"(dest2),
        [dest3] "=&f"(dest3)
      : [src_argb] "r"(src_argb), [dst_y] "r"(dst_y), [width] "r"(width),
        [mask] "f"(mask), [value] "f"(value), [eight] "f"(0x08),
        [zero] "f"(0x00)
      : "memory");
}

void RAWToUVRow_MMI(const uint8_t* src_rgb,
                    int src_stride_rgb,
                    uint8_t* dst_u,
                    uint8_t* dst_v,
                    int width) {
  uint64_t src_rgb1;
  uint64_t ftmp[13];
  uint64_t tmp[1];
  const uint64_t value = 0x4040;
  const uint64_t mask_u = 0x0002003800250013;
  const uint64_t mask_v = 0x0009002f00380002;

  __asm__ volatile(
      "dli        %[tmp0],         0x0001000100010001                   \n\t"
      "dmtc1      %[tmp0],         %[ftmp12]                            \n\t"
      "1:                                                               \n\t"
      "daddu      %[src_rgb1],     %[src_rgb],       %[src_stride_rgb] \n\t"
      "gsldrc1    %[src0],         0x00(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x07(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x00(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x07(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "dsll       %[src0],         %[src0],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "dsll       %[src1],         %[src1],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "pinsrh_3   %[dest0_u],      %[src0],           %[value]          \n\t"
      "dsll       %[dest0_v],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[dest0_v],      %[dest0_v],        %[value]          \n\t"
      "pmaddhw    %[dest0_u],      %[dest0_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest0_v],      %[dest0_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x06(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x0d(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x06(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x0d(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "dsll       %[src0],         %[src0],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "dsll       %[src1],         %[src1],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "pinsrh_3   %[src_lo],       %[src0],           %[value]          \n\t"
      "dsll       %[src_hi],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[src_hi],       %[src_hi],         %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest0_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest0_u],        %[src_lo]         \n\t"
      "psubw      %[dest0_u],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest0_u],      %[dest0_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest0_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest0_v],        %[src_hi]         \n\t"
      "psubw      %[dest0_v],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest0_v],      %[dest0_v],        %[eight]          \n\t"

      "gsldrc1    %[src0],         0x0c(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x13(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x0c(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x13(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "dsll       %[src0],         %[src0],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "dsll       %[src1],         %[src1],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "pinsrh_3   %[dest1_u],      %[src0],           %[value]          \n\t"
      "dsll       %[dest1_v],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[dest1_v],      %[dest1_v],        %[value]          \n\t"
      "pmaddhw    %[dest1_u],      %[dest1_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest1_v],      %[dest1_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x12(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x19(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x12(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x19(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "dsll       %[src0],         %[src0],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "dsll       %[src1],         %[src1],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "pinsrh_3   %[src_lo],       %[src0],           %[value]          \n\t"
      "dsll       %[src_hi],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[src_hi],       %[src_hi],         %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest1_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest1_u],        %[src_lo]         \n\t"
      "psubw      %[dest1_u],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest1_u],      %[dest1_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest1_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest1_v],        %[src_hi]         \n\t"
      "psubw      %[dest1_v],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest1_v],      %[dest1_v],        %[eight]          \n\t"

      "gsldrc1    %[src0],         0x18(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x1f(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x18(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x1f(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "dsll       %[src0],         %[src0],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "dsll       %[src1],         %[src1],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "pinsrh_3   %[dest2_u],      %[src0],           %[value]          \n\t"
      "dsll       %[dest2_v],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[dest2_v],      %[dest2_v],        %[value]          \n\t"
      "pmaddhw    %[dest2_u],      %[dest2_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest2_v],      %[dest2_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x1e(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x25(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x1e(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x25(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "dsll       %[src0],         %[src0],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "dsll       %[src1],         %[src1],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "pinsrh_3   %[src_lo],       %[src0],           %[value]          \n\t"
      "dsll       %[src_hi],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[src_hi],       %[src_hi],         %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest2_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest2_u],        %[src_lo]         \n\t"
      "psubw      %[dest2_u],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest2_u],      %[dest2_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest2_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest2_v],        %[src_hi]         \n\t"
      "psubw      %[dest2_v],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest2_v],      %[dest2_v],        %[eight]          \n\t"

      "gsldrc1    %[src0],         0x24(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x2b(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x24(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x2b(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "dsll       %[src0],         %[src0],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "dsll       %[src1],         %[src1],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "pinsrh_3   %[dest3_u],      %[src0],           %[value]          \n\t"
      "dsll       %[dest3_v],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[dest3_v],      %[dest3_v],        %[value]          \n\t"
      "pmaddhw    %[dest3_u],      %[dest3_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest3_v],      %[dest3_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x2a(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x31(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x2a(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x31(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "dsll       %[src0],         %[src0],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src_hi]         \n\t"
      "punpcklbh  %[src_lo],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_lo]         \n\t"
      "dsll       %[src1],         %[src1],           %[eight]          \n\t"
      "punpckhbh  %[src_hi],       %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src0],           %[src_hi]         \n\t"
      "paddh      %[src0],         %[src0],           %[ftmp12]         \n\t"
      "psrlh      %[src0],         %[src0],           %[one]            \n\t"
      "pinsrh_3   %[src_lo],       %[src0],           %[value]          \n\t"
      "dsll       %[src_hi],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[src_hi],       %[src_hi],         %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest3_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest3_u],        %[src_lo]         \n\t"
      "psubw      %[dest3_u],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest3_u],      %[dest3_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest3_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest3_v],        %[src_hi]         \n\t"
      "psubw      %[dest3_v],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest3_v],      %[dest3_v],        %[eight]          \n\t"

      "packsswh   %[src0],         %[dest0_u],        %[dest1_u]        \n\t"
      "packsswh   %[src1],         %[dest2_u],        %[dest3_u]        \n\t"
      "packushb   %[dest0_u],      %[src0],           %[src1]           \n\t"
      "gssdlc1    %[dest0_u],      0x07(%[dst_u])                       \n\t"
      "gssdrc1    %[dest0_u],      0x00(%[dst_u])                       \n\t"

      "packsswh   %[src0],         %[dest0_v],        %[dest1_v]        \n\t"
      "packsswh   %[src1],         %[dest2_v],        %[dest3_v]        \n\t"
      "packushb   %[dest0_v],      %[src0],           %[src1]           \n\t"
      "gssdlc1    %[dest0_v],      0x07(%[dst_v])                       \n\t"
      "gssdrc1    %[dest0_v],      0x00(%[dst_v])                       \n\t"

      "daddiu     %[src_rgb],     %[src_rgb],       0x30              \n\t"
      "daddiu     %[dst_u],        %[dst_u],          0x08              \n\t"
      "daddiu     %[dst_v],        %[dst_v],          0x08              \n\t"
      "daddi      %[width],        %[width],         -0x10              \n\t"
      "bgtz       %[width],        1b                                   \n\t"
      : [src_rgb1] "=&r"(src_rgb1), [src0] "=&f"(ftmp[0]),
        [src1] "=&f"(ftmp[1]), [src_lo] "=&f"(ftmp[2]), [src_hi] "=&f"(ftmp[3]),
        [dest0_u] "=&f"(ftmp[4]), [dest0_v] "=&f"(ftmp[5]),
        [dest1_u] "=&f"(ftmp[6]), [dest1_v] "=&f"(ftmp[7]),
        [dest2_u] "=&f"(ftmp[8]), [dest2_v] "=&f"(ftmp[9]),
        [dest3_u] "=&f"(ftmp[10]), [dest3_v] "=&f"(ftmp[11]),
        [ftmp12] "=&f"(ftmp[12]), [tmp0] "=&r"(tmp[0])
      : [src_rgb] "r"(src_rgb), [src_stride_rgb] "r"(src_stride_rgb),
        [dst_u] "r"(dst_u), [dst_v] "r"(dst_v), [width] "r"(width),
        [mask_u] "f"(mask_u), [mask_v] "f"(mask_v), [value] "f"(value),
        [zero] "f"(0x00), [eight] "f"(0x08), [one] "f"(0x01),
        [sixteen] "f"(0x10)
      : "memory");
}

void ARGBToYJRow_MMI(const uint8_t* src_argb, uint8_t* dst_y, int width) {
  uint64_t src, src_hi, src_lo;
  uint64_t dest, dest0, dest1, dest2, dest3;
  uint64_t tmp0, tmp1;
  const uint64_t shift = 0x08;
  const uint64_t value = 0x80;
  const uint64_t mask0 = 0x0;
  const uint64_t mask1 = 0x0001004D0096001DULL;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[src],          0x07(%[src_ptr])                 \n\t"
      "gsldrc1    %[src],          0x00(%[src_ptr])                 \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[mask0]      \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask1]      \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[mask0]      \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask1]      \n\t"
      "punpcklwd  %[tmp0],         %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[tmp1],         %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest0],        %[tmp0],           %[tmp1]       \n\t"
      "psrlw      %[dest0],        %[dest0],          %[shift]      \n\t"

      "gsldlc1    %[src],          0x0f(%[src_ptr])                 \n\t"
      "gsldrc1    %[src],          0x08(%[src_ptr])                 \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[mask0]      \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask1]      \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[mask0]      \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask1]      \n\t"
      "punpcklwd  %[tmp0],         %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[tmp1],         %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest1],        %[tmp0],           %[tmp1]       \n\t"
      "psrlw      %[dest1],        %[dest1],          %[shift]      \n\t"

      "gsldlc1    %[src],          0x17(%[src_ptr])                 \n\t"
      "gsldrc1    %[src],          0x10(%[src_ptr])                 \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[mask0]      \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask1]      \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[mask0]      \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask1]      \n\t"
      "punpcklwd  %[tmp0],         %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[tmp1],         %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest2],        %[tmp0],           %[tmp1]       \n\t"
      "psrlw      %[dest2],        %[dest2],          %[shift]      \n\t"

      "gsldlc1    %[src],          0x1f(%[src_ptr])                 \n\t"
      "gsldrc1    %[src],          0x18(%[src_ptr])                 \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[mask0]      \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[value]      \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask1]      \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[mask0]      \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]      \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask1]      \n\t"
      "punpcklwd  %[tmp0],         %[src_lo],         %[src_hi]     \n\t"
      "punpckhwd  %[tmp1],         %[src_lo],         %[src_hi]     \n\t"
      "paddw      %[dest3],        %[tmp0],           %[tmp1]       \n\t"
      "psrlw      %[dest3],        %[dest3],          %[shift]      \n\t"

      "packsswh   %[tmp0],         %[dest0],          %[dest1]      \n\t"
      "packsswh   %[tmp1],         %[dest2],          %[dest3]      \n\t"
      "packushb   %[dest],         %[tmp0],           %[tmp1]       \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x20          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x08          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src] "=&f"(src), [dest] "=&f"(dest), [src_hi] "=&f"(src_hi),
        [src_lo] "=&f"(src_lo), [dest0] "=&f"(dest0), [dest1] "=&f"(dest1),
        [dest2] "=&f"(dest2), [dest3] "=&f"(dest3), [tmp0] "=&f"(tmp0),
        [tmp1] "=&f"(tmp1)
      : [src_ptr] "r"(src_argb), [dst_ptr] "r"(dst_y), [mask0] "f"(mask0),
        [mask1] "f"(mask1), [shift] "f"(shift), [value] "f"(value),
        [width] "r"(width)
      : "memory");
}

void ARGBToUVJRow_MMI(const uint8_t* src_rgb,
                      int src_stride_rgb,
                      uint8_t* dst_u,
                      uint8_t* dst_v,
                      int width) {
  uint64_t src_rgb1;
  uint64_t ftmp[12];
  const uint64_t value = 0x4040;
  const uint64_t mask_u = 0x0015002a003f0002;
  const uint64_t mask_v = 0x0002003f0035000a;

  __asm__ volatile(
      "1:                                                               \n\t"
      "daddu      %[src_rgb1],     %[src_rgb],       %[src_stride_rgb] \n\t"
      "gsldrc1    %[src0],         0x00(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x07(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x00(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x07(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "punpcklbh  %[src0],         %[src1],           %[zero]           \n\t"
      "punpckhbh  %[src1],         %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src0]           \n\t"
      "paddh      %[src1],         %[src_hi],         %[src1]           \n\t"
      "pavgh      %[src0],         %[src0],           %[src1]           \n\t"
      "dsll       %[dest0_u],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[dest0_u],      %[dest0_u],        %[value]          \n\t"
      "pinsrh_3   %[dest0_v],      %[src0],           %[value]          \n\t"
      "pmaddhw    %[dest0_u],      %[dest0_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest0_v],      %[dest0_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x08(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x0f(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x08(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x0f(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "punpcklbh  %[src0],         %[src1],           %[zero]           \n\t"
      "punpckhbh  %[src1],         %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src0]           \n\t"
      "paddh      %[src1],         %[src_hi],         %[src1]           \n\t"
      "pavgh      %[src0],         %[src0],           %[src1]           \n\t"
      "dsll       %[src_lo],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[src_lo],       %[src_lo],         %[value]          \n\t"
      "pinsrh_3   %[src_hi],       %[src0],           %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest0_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest0_u],        %[src_lo]         \n\t"
      "psubw      %[dest0_u],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest0_u],      %[dest0_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest0_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest0_v],        %[src_hi]         \n\t"
      "psubw      %[dest0_v],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest0_v],      %[dest0_v],        %[eight]          \n\t"

      "gsldrc1    %[src0],         0x10(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x17(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x10(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x17(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "punpcklbh  %[src0],         %[src1],           %[zero]           \n\t"
      "punpckhbh  %[src1],         %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src0]           \n\t"
      "paddh      %[src1],         %[src_hi],         %[src1]           \n\t"
      "pavgh      %[src0],         %[src0],           %[src1]           \n\t"
      "dsll       %[dest1_u],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[dest1_u],      %[dest1_u],        %[value]          \n\t"
      "pinsrh_3   %[dest1_v],      %[src0],           %[value]          \n\t"
      "pmaddhw    %[dest1_u],      %[dest1_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest1_v],      %[dest1_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x18(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x1f(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x18(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x1f(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "punpcklbh  %[src0],         %[src1],           %[zero]           \n\t"
      "punpckhbh  %[src1],         %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src0]           \n\t"
      "paddh      %[src1],         %[src_hi],         %[src1]           \n\t"
      "pavgh      %[src0],         %[src0],           %[src1]           \n\t"
      "dsll       %[src_lo],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[src_lo],       %[src_lo],         %[value]          \n\t"
      "pinsrh_3   %[src_hi],       %[src0],           %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest1_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest1_u],        %[src_lo]         \n\t"
      "psubw      %[dest1_u],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest1_u],      %[dest1_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest1_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest1_v],        %[src_hi]         \n\t"
      "psubw      %[dest1_v],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest1_v],      %[dest1_v],        %[eight]          \n\t"

      "gsldrc1    %[src0],         0x20(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x27(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x20(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x27(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "punpcklbh  %[src0],         %[src1],           %[zero]           \n\t"
      "punpckhbh  %[src1],         %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src0]           \n\t"
      "paddh      %[src1],         %[src_hi],         %[src1]           \n\t"
      "pavgh      %[src0],         %[src0],           %[src1]           \n\t"
      "dsll       %[dest2_u],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[dest2_u],      %[dest2_u],        %[value]          \n\t"
      "pinsrh_3   %[dest2_v],      %[src0],           %[value]          \n\t"
      "pmaddhw    %[dest2_u],      %[dest2_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest2_v],      %[dest2_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x28(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x2f(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x28(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x2f(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "punpcklbh  %[src0],         %[src1],           %[zero]           \n\t"
      "punpckhbh  %[src1],         %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src0]           \n\t"
      "paddh      %[src1],         %[src_hi],         %[src1]           \n\t"
      "pavgh      %[src0],         %[src0],           %[src1]           \n\t"
      "dsll       %[src_lo],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[src_lo],       %[src_lo],         %[value]          \n\t"
      "pinsrh_3   %[src_hi],       %[src0],           %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest2_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest2_u],        %[src_lo]         \n\t"
      "psubw      %[dest2_u],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest2_u],      %[dest2_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest2_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest2_v],        %[src_hi]         \n\t"
      "psubw      %[dest2_v],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest2_v],      %[dest2_v],        %[eight]          \n\t"

      "gsldrc1    %[src0],         0x30(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x37(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x30(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x37(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "punpcklbh  %[src0],         %[src1],           %[zero]           \n\t"
      "punpckhbh  %[src1],         %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src0]           \n\t"
      "paddh      %[src1],         %[src_hi],         %[src1]           \n\t"
      "pavgh      %[src0],         %[src0],           %[src1]           \n\t"
      "dsll       %[dest3_u],      %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[dest3_u],      %[dest3_u],        %[value]          \n\t"
      "pinsrh_3   %[dest3_v],      %[src0],           %[value]          \n\t"
      "pmaddhw    %[dest3_u],      %[dest3_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest3_v],      %[dest3_v],        %[mask_v]         \n\t"

      "gsldrc1    %[src0],         0x38(%[src_rgb])                    \n\t"
      "gsldlc1    %[src0],         0x3f(%[src_rgb])                    \n\t"
      "gsldrc1    %[src1],         0x38(%[src_rgb1])                    \n\t"
      "gsldlc1    %[src1],         0x3f(%[src_rgb1])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "punpcklbh  %[src0],         %[src1],           %[zero]           \n\t"
      "punpckhbh  %[src1],         %[src1],           %[zero]           \n\t"
      "paddh      %[src0],         %[src_lo],         %[src0]           \n\t"
      "paddh      %[src1],         %[src_hi],         %[src1]           \n\t"
      "pavgh      %[src0],         %[src0],           %[src1]           \n\t"
      "dsll       %[src_lo],       %[src0],           %[sixteen]        \n\t"
      "pinsrh_0   %[src_lo],       %[src_lo],         %[value]          \n\t"
      "pinsrh_3   %[src_hi],       %[src0],           %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest3_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest3_u],        %[src_lo]         \n\t"
      "psubw      %[dest3_u],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest3_u],      %[dest3_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest3_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest3_v],        %[src_hi]         \n\t"
      "psubw      %[dest3_v],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest3_v],      %[dest3_v],        %[eight]          \n\t"

      "packsswh   %[src0],         %[dest0_u],        %[dest1_u]        \n\t"
      "packsswh   %[src1],         %[dest2_u],        %[dest3_u]        \n\t"
      "packushb   %[dest0_u],      %[src0],           %[src1]           \n\t"
      "gssdlc1    %[dest0_u],      0x07(%[dst_u])                       \n\t"
      "gssdrc1    %[dest0_u],      0x00(%[dst_u])                       \n\t"

      "packsswh   %[src0],         %[dest0_v],        %[dest1_v]        \n\t"
      "packsswh   %[src1],         %[dest2_v],        %[dest3_v]        \n\t"
      "packushb   %[dest0_v],      %[src0],           %[src1]           \n\t"
      "gssdlc1    %[dest0_v],      0x07(%[dst_v])                       \n\t"
      "gssdrc1    %[dest0_v],      0x00(%[dst_v])                       \n\t"

      "daddiu     %[src_rgb],     %[src_rgb],       0x40              \n\t"
      "daddiu     %[dst_u],        %[dst_u],          0x08              \n\t"
      "daddiu     %[dst_v],        %[dst_v],          0x08              \n\t"
      "daddi      %[width],        %[width],         -0x10              \n\t"
      "bgtz       %[width],        1b                                   \n\t"
      : [src_rgb1] "=&r"(src_rgb1), [src0] "=&f"(ftmp[0]),
        [src1] "=&f"(ftmp[1]), [src_lo] "=&f"(ftmp[2]), [src_hi] "=&f"(ftmp[3]),
        [dest0_u] "=&f"(ftmp[4]), [dest0_v] "=&f"(ftmp[5]),
        [dest1_u] "=&f"(ftmp[6]), [dest1_v] "=&f"(ftmp[7]),
        [dest2_u] "=&f"(ftmp[8]), [dest2_v] "=&f"(ftmp[9]),
        [dest3_u] "=&f"(ftmp[10]), [dest3_v] "=&f"(ftmp[11])
      : [src_rgb] "r"(src_rgb), [src_stride_rgb] "r"(src_stride_rgb),
        [dst_u] "r"(dst_u), [dst_v] "r"(dst_v), [width] "r"(width),
        [mask_u] "f"(mask_u), [mask_v] "f"(mask_v), [value] "f"(value),
        [zero] "f"(0x00), [eight] "f"(0x08),
        [sixteen] "f"(0x10)
      : "memory");
}

void RGB565ToYRow_MMI(const uint8_t* src_rgb565, uint8_t* dst_y, int width) {
  uint64_t ftmp[11];
  const uint64_t value = 0x1080108010801080;
  const uint64_t mask = 0x0001004200810019;
  uint64_t c0 = 0x001f001f001f001f;
  uint64_t c1 = 0x00ff00ff00ff00ff;
  uint64_t c2 = 0x0007000700070007;
  __asm__ volatile(
      "1:                                                            \n\t"
      "gsldrc1    %[src0],        0x00(%[src_rgb565])                \n\t"
      "gsldlc1    %[src0],        0x07(%[src_rgb565])                \n\t"
      "psrlh      %[src1],        %[src0],             %[eight]      \n\t"
      "and        %[b],           %[src0],             %[c0]         \n\t"
      "and        %[src0],        %[src0],             %[c1]         \n\t"
      "psrlh      %[src0],        %[src0],             %[five]       \n\t"
      "and        %[g],           %[src1],             %[c2]         \n\t"
      "psllh      %[g],           %[g],                %[three]      \n\t"
      "or         %[g],           %[src0],             %[g]          \n\t"
      "psrlh      %[r],           %[src1],             %[three]      \n\t"
      "psllh      %[src0],        %[b],                %[three]      \n\t"
      "psrlh      %[src1],        %[b],                %[two]        \n\t"
      "or         %[b],           %[src0],             %[src1]       \n\t"
      "psllh      %[src0],        %[g],                %[two]        \n\t"
      "psrlh      %[src1],        %[g],                %[four]       \n\t"
      "or         %[g],           %[src0],             %[src1]       \n\t"
      "psllh      %[src0],        %[r],                %[three]      \n\t"
      "psrlh      %[src1],        %[r],                %[two]        \n\t"
      "or         %[r],           %[src0],             %[src1]       \n\t"
      "punpcklhw  %[src0],        %[b],                %[r]          \n\t"
      "punpcklhw  %[src1],        %[g],                %[value]      \n\t"
      "punpcklhw  %[src_lo],      %[src0],             %[src1]       \n\t"
      "punpckhhw  %[src_hi],      %[src0],             %[src1]       \n\t"
      "pmaddhw    %[src_lo],      %[src_lo],           %[mask]       \n\t"
      "pmaddhw    %[src_hi],      %[src_hi],           %[mask]       \n\t"
      "punpcklwd  %[src0],        %[src_lo],           %[src_hi]     \n\t"
      "punpckhwd  %[src1],        %[src_lo],           %[src_hi]     \n\t"
      "paddw      %[dest0],       %[src0],             %[src1]       \n\t"
      "psrlw      %[dest0],       %[dest0],            %[eight]      \n\t"

      "punpckhhw  %[src0],        %[b],                %[r]          \n\t"
      "punpckhhw  %[src1],        %[g],                %[value]      \n\t"
      "punpcklhw  %[src_lo],      %[src0],             %[src1]       \n\t"
      "punpckhhw  %[src_hi],      %[src0],             %[src1]       \n\t"
      "pmaddhw    %[src_lo],      %[src_lo],           %[mask]       \n\t"
      "pmaddhw    %[src_hi],      %[src_hi],           %[mask]       \n\t"
      "punpcklwd  %[src0],        %[src_lo],           %[src_hi]     \n\t"
      "punpckhwd  %[src1],        %[src_lo],           %[src_hi]     \n\t"
      "paddw      %[dest1],       %[src0],             %[src1]       \n\t"
      "psrlw      %[dest1],       %[dest1],            %[eight]      \n\t"

      "gsldrc1    %[src0],        0x08(%[src_rgb565])                \n\t"
      "gsldlc1    %[src0],        0x0f(%[src_rgb565])                \n\t"
      "psrlh      %[src1],        %[src0],             %[eight]      \n\t"
      "and        %[b],           %[src0],             %[c0]         \n\t"
      "and        %[src0],        %[src0],             %[c1]         \n\t"
      "psrlh      %[src0],        %[src0],             %[five]       \n\t"
      "and        %[g],           %[src1],             %[c2]         \n\t"
      "psllh      %[g],           %[g],                %[three]      \n\t"
      "or         %[g],           %[src0],             %[g]          \n\t"
      "psrlh      %[r],           %[src1],             %[three]      \n\t"
      "psllh      %[src0],        %[b],                %[three]      \n\t"
      "psrlh      %[src1],        %[b],                %[two]        \n\t"
      "or         %[b],           %[src0],             %[src1]       \n\t"
      "psllh      %[src0],        %[g],                %[two]        \n\t"
      "psrlh      %[src1],        %[g],                %[four]       \n\t"
      "or         %[g],           %[src0],             %[src1]       \n\t"
      "psllh      %[src0],        %[r],                %[three]      \n\t"
      "psrlh      %[src1],        %[r],                %[two]        \n\t"
      "or         %[r],           %[src0],             %[src1]       \n\t"
      "punpcklhw  %[src0],        %[b],                %[r]          \n\t"
      "punpcklhw  %[src1],        %[g],                %[value]      \n\t"
      "punpcklhw  %[src_lo],      %[src0],             %[src1]       \n\t"
      "punpckhhw  %[src_hi],      %[src0],             %[src1]       \n\t"
      "pmaddhw    %[src_lo],      %[src_lo],           %[mask]       \n\t"
      "pmaddhw    %[src_hi],      %[src_hi],           %[mask]       \n\t"
      "punpcklwd  %[src0],        %[src_lo],           %[src_hi]     \n\t"
      "punpckhwd  %[src1],        %[src_lo],           %[src_hi]     \n\t"
      "paddw      %[dest2],       %[src0],             %[src1]       \n\t"
      "psrlw      %[dest2],       %[dest2],            %[eight]      \n\t"

      "punpckhhw  %[src0],        %[b],                %[r]          \n\t"
      "punpckhhw  %[src1],        %[g],                %[value]      \n\t"
      "punpcklhw  %[src_lo],      %[src0],             %[src1]       \n\t"
      "punpckhhw  %[src_hi],      %[src0],             %[src1]       \n\t"
      "pmaddhw    %[src_lo],      %[src_lo],           %[mask]       \n\t"
      "pmaddhw    %[src_hi],      %[src_hi],           %[mask]       \n\t"
      "punpcklwd  %[src0],        %[src_lo],           %[src_hi]     \n\t"
      "punpckhwd  %[src1],        %[src_lo],           %[src_hi]     \n\t"
      "paddw      %[dest3],       %[src0],             %[src1]       \n\t"
      "psrlw      %[dest3],       %[dest3],            %[eight]      \n\t"

      "packsswh   %[src_lo],      %[dest0],            %[dest1]      \n\t"
      "packsswh   %[src_hi],      %[dest2],            %[dest3]      \n\t"
      "packushb   %[dest0],       %[src_lo],           %[src_hi]     \n\t"
      "gssdlc1    %[dest0],       0x07(%[dst_y])                     \n\t"
      "gssdrc1    %[dest0],       0x00(%[dst_y])                     \n\t"

      "daddiu    %[src_rgb565],   %[src_rgb565],       0x10          \n\t"
      "daddiu    %[dst_y],        %[dst_y],            0x08          \n\t"
      "daddiu    %[width],        %[width],           -0x08          \n\t"
      "bgtz      %[width],        1b                                 \n\t"
      : [src0] "=&f"(ftmp[0]), [src1] "=&f"(ftmp[1]), [src_lo] "=&f"(ftmp[2]),
        [src_hi] "=&f"(ftmp[3]), [b] "=&f"(ftmp[4]), [g] "=&f"(ftmp[5]),
        [r] "=&f"(ftmp[6]), [dest0] "=&f"(ftmp[7]), [dest1] "=&f"(ftmp[8]),
        [dest2] "=&f"(ftmp[9]), [dest3] "=&f"(ftmp[10])
      : [src_rgb565] "r"(src_rgb565), [dst_y] "r"(dst_y), [value] "f"(value),
        [width] "r"(width), [c0] "f"(c0), [c1] "f"(c1), [c2] "f"(c2),
        [mask] "f"(mask), [eight] "f"(0x08), [five] "f"(0x05),
        [three] "f"(0x03), [two] "f"(0x02), [four] "f"(0x04)
      : "memory");
}

void ARGB1555ToYRow_MMI(const uint8_t* src_argb1555,
                        uint8_t* dst_y,
                        int width) {
  uint64_t ftmp[11];
  const uint64_t value = 0x1080108010801080;
  const uint64_t mask = 0x0001004200810019;
  uint64_t c0 = 0x001f001f001f001f;
  uint64_t c1 = 0x00ff00ff00ff00ff;
  uint64_t c2 = 0x0003000300030003;
  uint64_t c3 = 0x007c007c007c007c;
  __asm__ volatile(
      "1:                                                            \n\t"
      "gsldrc1    %[src0],         0x00(%[src_argb1555])             \n\t"
      "gsldlc1    %[src0],         0x07(%[src_argb1555])             \n\t"
      "psrlh      %[src1],         %[src0],              %[eight]    \n\t"
      "and        %[b],            %[src0],              %[c0]       \n\t"
      "and        %[src0],         %[src0],              %[c1]       \n\t"
      "psrlh      %[src0],         %[src0],              %[five]     \n\t"
      "and        %[g],            %[src1],              %[c2]       \n\t"
      "psllh      %[g],            %[g],                 %[three]    \n\t"
      "or         %[g],            %[src0],              %[g]        \n\t"
      "and        %[r],            %[src1],              %[c3]       \n\t"
      "psrlh      %[r],            %[r],                 %[two]      \n\t"
      "psllh      %[src0],         %[b],                 %[three]    \n\t"
      "psrlh      %[src1],         %[b],                 %[two]      \n\t"
      "or         %[b],            %[src0],              %[src1]     \n\t"
      "psllh      %[src0],         %[g],                 %[three]    \n\t"
      "psrlh      %[src1],         %[g],                 %[two]      \n\t"
      "or         %[g],            %[src0],              %[src1]     \n\t"
      "psllh      %[src0],         %[r],                 %[three]    \n\t"
      "psrlh      %[src1],         %[r],                 %[two]      \n\t"
      "or         %[r],            %[src0],              %[src1]     \n\t"
      "punpcklhw  %[src0],         %[b],                 %[r]        \n\t"
      "punpcklhw  %[src1],         %[g],                 %[value]    \n\t"
      "punpcklhw  %[src_lo],       %[src0],              %[src1]     \n\t"
      "punpckhhw  %[src_hi],       %[src0],              %[src1]     \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],            %[mask]     \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],            %[mask]     \n\t"
      "punpcklwd  %[src0],         %[src_lo],            %[src_hi]   \n\t"
      "punpckhwd  %[src1],         %[src_lo],            %[src_hi]   \n\t"
      "paddw      %[dest0],        %[src0],              %[src1]     \n\t"
      "psrlw      %[dest0],        %[dest0],             %[eight]    \n\t"

      "punpckhhw  %[src0],         %[b],                 %[r]        \n\t"
      "punpckhhw  %[src1],         %[g],                 %[value]    \n\t"
      "punpcklhw  %[src_lo],       %[src0],              %[src1]     \n\t"
      "punpckhhw  %[src_hi],       %[src0],              %[src1]     \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],            %[mask]     \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],            %[mask]     \n\t"
      "punpcklwd  %[src0],         %[src_lo],            %[src_hi]   \n\t"
      "punpckhwd  %[src1],         %[src_lo],            %[src_hi]   \n\t"
      "paddw      %[dest1],        %[src0],              %[src1]     \n\t"
      "psrlw      %[dest1],        %[dest1],             %[eight]    \n\t"

      "gsldrc1    %[src0],         0x08(%[src_argb1555])             \n\t"
      "gsldlc1    %[src0],         0x0f(%[src_argb1555])             \n\t"
      "psrlh      %[src1],         %[src0],              %[eight]    \n\t"
      "and        %[b],            %[src0],              %[c0]       \n\t"
      "and        %[src0],         %[src0],              %[c1]       \n\t"
      "psrlh      %[src0],         %[src0],              %[five]     \n\t"
      "and        %[g],            %[src1],              %[c2]       \n\t"
      "psllh      %[g],            %[g],                 %[three]    \n\t"
      "or         %[g],            %[src0],              %[g]        \n\t"
      "and        %[r],            %[src1],              %[c3]       \n\t"
      "psrlh      %[r],            %[r],                 %[two]      \n\t"
      "psllh      %[src0],         %[b],                 %[three]    \n\t"
      "psrlh      %[src1],         %[b],                 %[two]      \n\t"
      "or         %[b],            %[src0],              %[src1]     \n\t"
      "psllh      %[src0],         %[g],                 %[three]    \n\t"
      "psrlh      %[src1],         %[g],                 %[two]      \n\t"
      "or         %[g],            %[src0],              %[src1]     \n\t"
      "psllh      %[src0],         %[r],                 %[three]    \n\t"
      "psrlh      %[src1],         %[r],                 %[two]      \n\t"
      "or         %[r],            %[src0],              %[src1]     \n\t"
      "punpcklhw  %[src0],         %[b],                 %[r]        \n\t"
      "punpcklhw  %[src1],         %[g],                 %[value]    \n\t"
      "punpcklhw  %[src_lo],       %[src0],              %[src1]     \n\t"
      "punpckhhw  %[src_hi],       %[src0],              %[src1]     \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],            %[mask]     \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],            %[mask]     \n\t"
      "punpcklwd  %[src0],         %[src_lo],            %[src_hi]   \n\t"
      "punpckhwd  %[src1],         %[src_lo],            %[src_hi]   \n\t"
      "paddw      %[dest2],        %[src0],              %[src1]     \n\t"
      "psrlw      %[dest2],        %[dest2],             %[eight]    \n\t"

      "punpckhhw  %[src0],         %[b],                 %[r]        \n\t"
      "punpckhhw  %[src1],         %[g],                 %[value]    \n\t"
      "punpcklhw  %[src_lo],       %[src0],              %[src1]     \n\t"
      "punpckhhw  %[src_hi],       %[src0],              %[src1]     \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],            %[mask]     \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],            %[mask]     \n\t"
      "punpcklwd  %[src0],         %[src_lo],            %[src_hi]   \n\t"
      "punpckhwd  %[src1],         %[src_lo],            %[src_hi]   \n\t"
      "paddw      %[dest3],        %[src0],              %[src1]     \n\t"
      "psrlw      %[dest3],        %[dest3],             %[eight]    \n\t"

      "packsswh   %[src_lo],       %[dest0],             %[dest1]    \n\t"
      "packsswh   %[src_hi],       %[dest2],             %[dest3]    \n\t"
      "packushb   %[dest0],        %[src_lo],            %[src_hi]   \n\t"
      "gssdlc1    %[dest0],        0x07(%[dst_y])                    \n\t"
      "gssdrc1    %[dest0],        0x00(%[dst_y])                    \n\t"

      "daddiu     %[src_argb1555], %[src_argb1555],      0x10        \n\t"
      "daddiu     %[dst_y],        %[dst_y],             0x08        \n\t"
      "daddiu     %[width],        %[width],            -0x08        \n\t"
      "bgtz       %[width],        1b                                \n\t"
      : [src0] "=&f"(ftmp[0]), [src1] "=&f"(ftmp[1]), [src_lo] "=&f"(ftmp[2]),
        [src_hi] "=&f"(ftmp[3]), [b] "=&f"(ftmp[4]), [g] "=&f"(ftmp[5]),
        [r] "=&f"(ftmp[6]), [dest0] "=&f"(ftmp[7]), [dest1] "=&f"(ftmp[8]),
        [dest2] "=&f"(ftmp[9]), [dest3] "=&f"(ftmp[10])
      : [src_argb1555] "r"(src_argb1555), [dst_y] "r"(dst_y),
        [width] "r"(width), [value] "f"(value), [mask] "f"(mask), [c0] "f"(c0),
        [c1] "f"(c1), [c2] "f"(c2), [c3] "f"(c3), [eight] "f"(0x08),
        [five] "f"(0x05), [three] "f"(0x03), [two] "f"(0x02), [seven] "f"(0x07)
      : "memory");
}

void ARGB4444ToYRow_MMI(const uint8_t* src_argb4444,
                        uint8_t* dst_y,
                        int width) {
  uint64_t ftmp[11];
  uint64_t value = 0x1080108010801080;
  uint64_t mask = 0x0001004200810019;
  uint64_t c0 = 0x000f000f000f000f;
  uint64_t c1 = 0x00ff00ff00ff00ff;
  __asm__ volatile(
      "1:                                                            \n\t"
      "gsldrc1    %[src0],         0x00(%[src_argb4444])             \n\t"
      "gsldlc1    %[src0],         0x07(%[src_argb4444])             \n\t"
      "psrlh      %[src1],         %[src0],              %[eight]    \n\t"
      "and        %[b],            %[src0],              %[c0]       \n\t"
      "and        %[src0],         %[src0],              %[c1]       \n\t"
      "psrlh      %[g],            %[src0],              %[four]     \n\t"
      "and        %[r],            %[src1],              %[c0]       \n\t"
      "psllh      %[src0],         %[b],                 %[four]     \n\t"
      "or         %[b],            %[src0],              %[b]        \n\t"
      "psllh      %[src0],         %[g],                 %[four]     \n\t"
      "or         %[g],            %[src0],              %[g]        \n\t"
      "psllh      %[src0],         %[r],                 %[four]     \n\t"
      "or         %[r],            %[src0],              %[r]        \n\t"
      "punpcklhw  %[src0],         %[b],                 %[r]        \n\t"
      "punpcklhw  %[src1],         %[g],                 %[value]    \n\t"
      "punpcklhw  %[src_lo],       %[src0],              %[src1]     \n\t"
      "punpckhhw  %[src_hi],       %[src0],              %[src1]     \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],            %[mask]     \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],            %[mask]     \n\t"
      "punpcklwd  %[src0],         %[src_lo],            %[src_hi]   \n\t"
      "punpckhwd  %[src1],         %[src_lo],            %[src_hi]   \n\t"
      "paddw      %[dest0],        %[src0],              %[src1]     \n\t"
      "psrlw      %[dest0],        %[dest0],             %[eight]    \n\t"

      "punpckhhw  %[src0],         %[b],                 %[r]        \n\t"
      "punpckhhw  %[src1],         %[g],                 %[value]    \n\t"
      "punpcklhw  %[src_lo],       %[src0],              %[src1]     \n\t"
      "punpckhhw  %[src_hi],       %[src0],              %[src1]     \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],            %[mask]     \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],            %[mask]     \n\t"
      "punpcklwd  %[src0],         %[src_lo],            %[src_hi]   \n\t"
      "punpckhwd  %[src1],         %[src_lo],            %[src_hi]   \n\t"
      "paddw      %[dest1],        %[src0],              %[src1]     \n\t"
      "psrlw      %[dest1],        %[dest1],             %[eight]    \n\t"

      "gsldrc1    %[src0],         0x08(%[src_argb4444])             \n\t"
      "gsldlc1    %[src0],         0x0f(%[src_argb4444])             \n\t"
      "psrlh      %[src1],         %[src0],              %[eight]    \n\t"
      "and        %[b],            %[src0],              %[c0]       \n\t"
      "and        %[src0],         %[src0],              %[c1]       \n\t"
      "psrlh      %[g],            %[src0],              %[four]     \n\t"
      "and        %[r],            %[src1],              %[c0]       \n\t"
      "psllh      %[src0],         %[b],                 %[four]     \n\t"
      "or         %[b],            %[src0],              %[b]        \n\t"
      "psllh      %[src0],         %[g],                 %[four]     \n\t"
      "or         %[g],            %[src0],              %[g]        \n\t"
      "psllh      %[src0],         %[r],                 %[four]     \n\t"
      "or         %[r],            %[src0],              %[r]        \n\t"
      "punpcklhw  %[src0],         %[b],                 %[r]        \n\t"
      "punpcklhw  %[src1],         %[g],                 %[value]    \n\t"
      "punpcklhw  %[src_lo],       %[src0],              %[src1]     \n\t"
      "punpckhhw  %[src_hi],       %[src0],              %[src1]     \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],            %[mask]     \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],            %[mask]     \n\t"
      "punpcklwd  %[src0],         %[src_lo],            %[src_hi]   \n\t"
      "punpckhwd  %[src1],         %[src_lo],            %[src_hi]   \n\t"
      "paddw      %[dest2],        %[src0],              %[src1]     \n\t"
      "psrlw      %[dest2],        %[dest2],             %[eight]    \n\t"

      "punpckhhw  %[src0],         %[b],                 %[r]        \n\t"
      "punpckhhw  %[src1],         %[g],                 %[value]    \n\t"
      "punpcklhw  %[src_lo],       %[src0],              %[src1]     \n\t"
      "punpckhhw  %[src_hi],       %[src0],              %[src1]     \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],            %[mask]     \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],            %[mask]     \n\t"
      "punpcklwd  %[src0],         %[src_lo],            %[src_hi]   \n\t"
      "punpckhwd  %[src1],         %[src_lo],            %[src_hi]   \n\t"
      "paddw      %[dest3],        %[src0],              %[src1]     \n\t"
      "psrlw      %[dest3],        %[dest3],             %[eight]    \n\t"

      "packsswh   %[src_lo],       %[dest0],             %[dest1]    \n\t"
      "packsswh   %[src_hi],       %[dest2],             %[dest3]    \n\t"
      "packushb   %[dest0],        %[src_lo],            %[src_hi]   \n\t"
      "gssdlc1    %[dest0],        0x07(%[dst_y])                    \n\t"
      "gssdrc1    %[dest0],        0x00(%[dst_y])                    \n\t"

      "daddiu     %[src_argb4444], %[src_argb4444],      0x10        \n\t"
      "daddiu     %[dst_y],        %[dst_y],             0x08        \n\t"
      "daddiu     %[width],        %[width],            -0x08        \n\t"
      "bgtz       %[width],        1b                                \n\t"
      : [src0] "=&f"(ftmp[0]), [src1] "=&f"(ftmp[1]), [src_lo] "=&f"(ftmp[2]),
        [src_hi] "=&f"(ftmp[3]), [b] "=&f"(ftmp[4]), [g] "=&f"(ftmp[5]),
        [r] "=&f"(ftmp[6]), [dest0] "=&f"(ftmp[7]), [dest1] "=&f"(ftmp[8]),
        [dest2] "=&f"(ftmp[9]), [dest3] "=&f"(ftmp[10])
      : [src_argb4444] "r"(src_argb4444), [dst_y] "r"(dst_y),
        [width] "r"(width), [value] "f"(value), [mask] "f"(mask), [c0] "f"(c0),
        [c1] "f"(c1), [eight] "f"(0x08), [four] "f"(0x04)
      : "memory");
}

void RGB565ToUVRow_MMI(const uint8_t* src_rgb565,
                       int src_stride_rgb565,
                       uint8_t* dst_u,
                       uint8_t* dst_v,
                       int width) {
  uint64_t ftmp[13];
  uint64_t value = 0x2020202020202020;
  uint64_t mask_u = 0x0026004a00700002;
  uint64_t mask_v = 0x00020070005e0012;
  uint64_t mask = 0x93;
  uint64_t c0 = 0x001f001f001f001f;
  uint64_t c1 = 0x00ff00ff00ff00ff;
  uint64_t c2 = 0x0007000700070007;
  __asm__ volatile(
      "daddu      %[next_rgb565], %[src_rgb565],       %[next_rgb565]   \n\t"
      "1:                                                               \n\t"
      "gsldrc1    %[src0],        0x00(%[src_rgb565])                   \n\t"
      "gsldlc1    %[src0],        0x07(%[src_rgb565])                   \n\t"
      "gsldrc1    %[src1],        0x00(%[next_rgb565])                  \n\t"
      "gsldlc1    %[src1],        0x07(%[next_rgb565])                  \n\t"
      "psrlh      %[dest0_u],     %[src0],             %[eight]         \n\t"
      "and        %[b0],          %[src0],             %[c0]            \n\t"
      "and        %[src0],        %[src0],             %[c1]            \n\t"
      "psrlh      %[src0],        %[src0],             %[five]          \n\t"
      "and        %[g0],          %[dest0_u],          %[c2]            \n\t"
      "psllh      %[g0],          %[g0],               %[three]         \n\t"
      "or         %[g0],          %[src0],             %[g0]            \n\t"
      "psrlh      %[r0],          %[dest0_u],          %[three]         \n\t"
      "psrlh      %[src0],        %[src1],             %[eight]         \n\t"
      "and        %[dest0_u],     %[src1],             %[c0]            \n\t"
      "and        %[src1],        %[src1],             %[c1]            \n\t"
      "psrlh      %[src1],        %[src1],             %[five]          \n\t"
      "and        %[dest0_v],     %[src0],             %[c2]            \n\t"
      "psllh      %[dest0_v],     %[dest0_v],          %[three]         \n\t"
      "or         %[dest0_v],     %[src1],             %[dest0_v]       \n\t"
      "psrlh      %[src0],        %[src0],             %[three]         \n\t"
      "paddh      %[b0],          %[b0],               %[dest0_u]       \n\t"
      "paddh      %[g0],          %[g0],               %[dest0_v]       \n\t"
      "paddh      %[r0],          %[r0],               %[src0]          \n\t"
      "punpcklhw  %[src0],        %[b0],               %[r0]            \n\t"
      "punpckhhw  %[src1],        %[b0],               %[r0]            \n\t"
      "punpcklwd  %[dest0_u],     %[src0],             %[src1]          \n\t"
      "punpckhwd  %[dest0_v],     %[src0],             %[src1]          \n\t"
      "paddh      %[src0],        %[dest0_u],          %[dest0_v]       \n\t"
      "psrlh      %[b0],          %[src0],             %[six]           \n\t"
      "psllh      %[r0],          %[src0],             %[one]           \n\t"
      "or         %[b0],          %[b0],               %[r0]            \n\t"
      "punpcklhw  %[src0],        %[g0],               %[value]         \n\t"
      "punpckhhw  %[src1],        %[g0],               %[value]         \n\t"
      "punpcklwd  %[dest0_u],     %[src0],             %[src1]          \n\t"
      "punpckhwd  %[dest0_v],     %[src0],             %[src1]          \n\t"
      "paddh      %[g0],          %[dest0_u],          %[dest0_v]       \n\t"
      "punpcklhw  %[src0],        %[b0],               %[g0]            \n\t"
      "punpckhhw  %[src1],        %[b0],               %[g0]            \n\t"

      "pmaddhw    %[dest0_v],     %[src0],             %[mask_v]        \n\t"
      "pshufh     %[dest0_u],     %[src0],             %[mask]          \n\t"
      "pmaddhw    %[dest0_u],     %[dest0_u],          %[mask_u]        \n\t"
      "pmaddhw    %[g0],          %[src1],             %[mask_v]        \n\t"
      "pshufh     %[b0],          %[src1],             %[mask]          \n\t"
      "pmaddhw    %[b0],          %[b0],               %[mask_u]        \n\t"

      "punpcklwd  %[src0],        %[dest0_u],          %[b0]            \n\t"
      "punpckhwd  %[src1],        %[dest0_u],          %[b0]            \n\t"
      "psubw      %[dest0_u],     %[src0],             %[src1]          \n\t"
      "psraw      %[dest0_u],     %[dest0_u],          %[eight]         \n\t"
      "punpcklwd  %[src0],        %[dest0_v],          %[g0]            \n\t"
      "punpckhwd  %[src1],        %[dest0_v],          %[g0]            \n\t"
      "psubw      %[dest0_v],     %[src1],             %[src0]          \n\t"
      "psraw      %[dest0_v],     %[dest0_v],          %[eight]         \n\t"

      "gsldrc1    %[src0],        0x08(%[src_rgb565])                   \n\t"
      "gsldlc1    %[src0],        0x0f(%[src_rgb565])                   \n\t"
      "gsldrc1    %[src1],        0x08(%[next_rgb565])                  \n\t"
      "gsldlc1    %[src1],        0x0f(%[next_rgb565])                  \n\t"
      "psrlh      %[dest1_u],     %[src0],             %[eight]         \n\t"
      "and        %[b0],          %[src0],             %[c0]            \n\t"
      "and        %[src0],        %[src0],             %[c1]            \n\t"
      "psrlh      %[src0],        %[src0],             %[five]          \n\t"
      "and        %[g0],          %[dest1_u],          %[c2]            \n\t"
      "psllh      %[g0],          %[g0],               %[three]         \n\t"
      "or         %[g0],          %[src0],             %[g0]            \n\t"
      "psrlh      %[r0],          %[dest1_u],          %[three]         \n\t"
      "psrlh      %[src0],        %[src1],             %[eight]         \n\t"
      "and        %[dest1_u],     %[src1],             %[c0]            \n\t"
      "and        %[src1],        %[src1],             %[c1]            \n\t"
      "psrlh      %[src1],        %[src1],             %[five]          \n\t"
      "and        %[dest1_v],     %[src0],             %[c2]            \n\t"
      "psllh      %[dest1_v],     %[dest1_v],          %[three]         \n\t"
      "or         %[dest1_v],     %[src1],             %[dest1_v]       \n\t"
      "psrlh      %[src0],        %[src0],             %[three]         \n\t"
      "paddh      %[b0],          %[b0],               %[dest1_u]       \n\t"
      "paddh      %[g0],          %[g0],               %[dest1_v]       \n\t"
      "paddh      %[r0],          %[r0],               %[src0]          \n\t"
      "punpcklhw  %[src0],        %[b0],               %[r0]            \n\t"
      "punpckhhw  %[src1],        %[b0],               %[r0]            \n\t"
      "punpcklwd  %[dest1_u],     %[src0],             %[src1]          \n\t"
      "punpckhwd  %[dest1_v],     %[src0],             %[src1]          \n\t"
      "paddh      %[src0],        %[dest1_u],          %[dest1_v]       \n\t"
      "psrlh      %[b0],          %[src0],             %[six]           \n\t"
      "psllh      %[r0],          %[src0],             %[one]           \n\t"
      "or         %[b0],          %[b0],               %[r0]            \n\t"
      "punpcklhw  %[src0],        %[g0],               %[value]         \n\t"
      "punpckhhw  %[src1],        %[g0],               %[value]         \n\t"
      "punpcklwd  %[dest1_u],     %[src0],             %[src1]          \n\t"
      "punpckhwd  %[dest1_v],     %[src0],             %[src1]          \n\t"
      "paddh      %[g0],          %[dest1_u],          %[dest1_v]       \n\t"
      "punpcklhw  %[src0],        %[b0],               %[g0]            \n\t"
      "punpckhhw  %[src1],        %[b0],               %[g0]            \n\t"

      "pmaddhw    %[dest1_v],     %[src0],             %[mask_v]        \n\t"
      "pshufh     %[dest1_u],     %[src0],             %[mask]          \n\t"
      "pmaddhw    %[dest1_u],     %[dest1_u],          %[mask_u]        \n\t"
      "pmaddhw    %[g0],          %[src1],             %[mask_v]        \n\t"
      "pshufh     %[b0],          %[src1],             %[mask]          \n\t"
      "pmaddhw    %[b0],          %[b0],               %[mask_u]        \n\t"

      "punpcklwd  %[src0],        %[dest1_u],          %[b0]            \n\t"
      "punpckhwd  %[src1],        %[dest1_u],          %[b0]            \n\t"
      "psubw      %[dest1_u],     %[src0],             %[src1]          \n\t"
      "psraw      %[dest1_u],     %[dest1_u],          %[eight]         \n\t"
      "punpcklwd  %[src0],        %[dest1_v],          %[g0]            \n\t"
      "punpckhwd  %[src1],        %[dest1_v],          %[g0]            \n\t"
      "psubw      %[dest1_v],     %[src1],             %[src0]          \n\t"
      "psraw      %[dest1_v],     %[dest1_v],          %[eight]         \n\t"

      "gsldrc1    %[src0],        0x10(%[src_rgb565])                   \n\t"
      "gsldlc1    %[src0],        0x17(%[src_rgb565])                   \n\t"
      "gsldrc1    %[src1],        0x10(%[next_rgb565])                  \n\t"
      "gsldlc1    %[src1],        0x17(%[next_rgb565])                  \n\t"
      "psrlh      %[dest2_u],     %[src0],             %[eight]         \n\t"
      "and        %[b0],          %[src0],             %[c0]            \n\t"
      "and        %[src0],        %[src0],             %[c1]            \n\t"
      "psrlh      %[src0],        %[src0],             %[five]          \n\t"
      "and        %[g0],          %[dest2_u],          %[c2]            \n\t"
      "psllh      %[g0],          %[g0],               %[three]         \n\t"
      "or         %[g0],          %[src0],             %[g0]            \n\t"
      "psrlh      %[r0],          %[dest2_u],          %[three]         \n\t"
      "psrlh      %[src0],        %[src1],             %[eight]         \n\t"
      "and        %[dest2_u],     %[src1],             %[c0]            \n\t"
      "and        %[src1],        %[src1],             %[c1]            \n\t"
      "psrlh      %[src1],        %[src1],             %[five]          \n\t"
      "and        %[dest2_v],     %[src0],             %[c2]            \n\t"
      "psllh      %[dest2_v],     %[dest2_v],          %[three]         \n\t"
      "or         %[dest2_v],     %[src1],             %[dest2_v]       \n\t"
      "psrlh      %[src0],        %[src0],             %[three]         \n\t"
      "paddh      %[b0],          %[b0],               %[dest2_u]       \n\t"
      "paddh      %[g0],          %[g0],               %[dest2_v]       \n\t"
      "paddh      %[r0],          %[r0],               %[src0]          \n\t"
      "punpcklhw  %[src0],        %[b0],               %[r0]            \n\t"
      "punpckhhw  %[src1],        %[b0],               %[r0]            \n\t"
      "punpcklwd  %[dest2_u],     %[src0],             %[src1]          \n\t"
      "punpckhwd  %[dest2_v],     %[src0],             %[src1]          \n\t"
      "paddh      %[src0],        %[dest2_u],          %[dest2_v]       \n\t"
      "psrlh      %[b0],          %[src0],             %[six]           \n\t"
      "psllh      %[r0],          %[src0],             %[one]           \n\t"
      "or         %[b0],          %[b0],               %[r0]            \n\t"
      "punpcklhw  %[src0],        %[g0],               %[value]         \n\t"
      "punpckhhw  %[src1],        %[g0],               %[value]         \n\t"
      "punpcklwd  %[dest2_u],     %[src0],             %[src1]          \n\t"
      "punpckhwd  %[dest2_v],     %[src0],             %[src1]          \n\t"
      "paddh      %[g0],          %[dest2_u],          %[dest2_v]       \n\t"
      "punpcklhw  %[src0],        %[b0],               %[g0]            \n\t"
      "punpckhhw  %[src1],        %[b0],               %[g0]            \n\t"

      "pmaddhw    %[dest2_v],     %[src0],             %[mask_v]        \n\t"
      "pshufh     %[dest2_u],     %[src0],             %[mask]          \n\t"
      "pmaddhw    %[dest2_u],     %[dest2_u],          %[mask_u]        \n\t"
      "pmaddhw    %[g0],          %[src1],             %[mask_v]        \n\t"
      "pshufh     %[b0],          %[src1],             %[mask]          \n\t"
      "pmaddhw    %[b0],          %[b0],               %[mask_u]        \n\t"

      "punpcklwd  %[src0],        %[dest2_u],          %[b0]            \n\t"
      "punpckhwd  %[src1],        %[dest2_u],          %[b0]            \n\t"
      "psubw      %[dest2_u],     %[src0],             %[src1]          \n\t"
      "psraw      %[dest2_u],     %[dest2_u],          %[eight]         \n\t"
      "punpcklwd  %[src0],        %[dest2_v],          %[g0]            \n\t"
      "punpckhwd  %[src1],        %[dest2_v],          %[g0]            \n\t"
      "psubw      %[dest2_v],     %[src1],             %[src0]          \n\t"
      "psraw      %[dest2_v],     %[dest2_v],          %[eight]         \n\t"

      "gsldrc1    %[src0],        0x18(%[src_rgb565])                   \n\t"
      "gsldlc1    %[src0],        0x1f(%[src_rgb565])                   \n\t"
      "gsldrc1    %[src1],        0x18(%[next_rgb565])                  \n\t"
      "gsldlc1    %[src1],        0x1f(%[next_rgb565])                  \n\t"
      "psrlh      %[dest3_u],     %[src0],             %[eight]         \n\t"
      "and        %[b0],          %[src0],             %[c0]            \n\t"
      "and        %[src0],        %[src0],             %[c1]            \n\t"
      "psrlh      %[src0],        %[src0],             %[five]          \n\t"
      "and        %[g0],          %[dest3_u],          %[c2]            \n\t"
      "psllh      %[g0],          %[g0],               %[three]         \n\t"
      "or         %[g0],          %[src0],             %[g0]            \n\t"
      "psrlh      %[r0],          %[dest3_u],          %[three]         \n\t"
      "psrlh      %[src0],        %[src1],             %[eight]         \n\t"
      "and        %[dest3_u],     %[src1],             %[c0]            \n\t"
      "and        %[src1],        %[src1],             %[c1]            \n\t"
      "psrlh      %[src1],        %[src1],             %[five]          \n\t"
      "and        %[dest3_v],     %[src0],             %[c2]            \n\t"
      "psllh      %[dest3_v],     %[dest3_v],          %[three]         \n\t"
      "or         %[dest3_v],     %[src1],             %[dest3_v]       \n\t"
      "psrlh      %[src0],        %[src0],             %[three]         \n\t"
      "paddh      %[b0],          %[b0],               %[dest3_u]       \n\t"
      "paddh      %[g0],          %[g0],               %[dest3_v]       \n\t"
      "paddh      %[r0],          %[r0],               %[src0]          \n\t"
      "punpcklhw  %[src0],        %[b0],               %[r0]            \n\t"
      "punpckhhw  %[src1],        %[b0],               %[r0]            \n\t"
      "punpcklwd  %[dest3_u],     %[src0],             %[src1]          \n\t"
      "punpckhwd  %[dest3_v],     %[src0],             %[src1]          \n\t"
      "paddh      %[src0],        %[dest3_u],          %[dest3_v]       \n\t"
      "psrlh      %[b0],          %[src0],             %[six]           \n\t"
      "psllh      %[r0],          %[src0],             %[one]           \n\t"
      "or         %[b0],          %[b0],               %[r0]            \n\t"
      "punpcklhw  %[src0],        %[g0],               %[value]         \n\t"
      "punpckhhw  %[src1],        %[g0],               %[value]         \n\t"
      "punpcklwd  %[dest3_u],     %[src0],             %[src1]          \n\t"
      "punpckhwd  %[dest3_v],     %[src0],             %[src1]          \n\t"
      "paddh      %[g0],          %[dest3_u],          %[dest3_v]       \n\t"
      "punpcklhw  %[src0],        %[b0],               %[g0]            \n\t"
      "punpckhhw  %[src1],        %[b0],               %[g0]            \n\t"

      "pmaddhw    %[dest3_v],     %[src0],             %[mask_v]        \n\t"
      "pshufh     %[dest3_u],     %[src0],             %[mask]          \n\t"
      "pmaddhw    %[dest3_u],     %[dest3_u],          %[mask_u]        \n\t"
      "pmaddhw    %[g0],          %[src1],             %[mask_v]        \n\t"
      "pshufh     %[b0],          %[src1],             %[mask]          \n\t"
      "pmaddhw    %[b0],          %[b0],               %[mask_u]        \n\t"

      "punpcklwd  %[src0],        %[dest3_u],          %[b0]            \n\t"
      "punpckhwd  %[src1],        %[dest3_u],          %[b0]            \n\t"
      "psubw      %[dest3_u],     %[src0],             %[src1]          \n\t"
      "psraw      %[dest3_u],     %[dest3_u],          %[eight]         \n\t"
      "punpcklwd  %[src0],        %[dest3_v],          %[g0]            \n\t"
      "punpckhwd  %[src1],        %[dest3_v],          %[g0]            \n\t"
      "psubw      %[dest3_v],     %[src1],             %[src0]          \n\t"
      "psraw      %[dest3_v],     %[dest3_v],          %[eight]         \n\t"

      "packsswh   %[src0],        %[dest0_u],          %[dest1_u]       \n\t"
      "packsswh   %[src1],        %[dest2_u],          %[dest3_u]       \n\t"
      "packushb   %[dest0_u],     %[src0],             %[src1]          \n\t"
      "gssdlc1    %[dest0_u],     0x07(%[dst_u])                        \n\t"
      "gssdrc1    %[dest0_u],     0x00(%[dst_u])                        \n\t"
      "packsswh   %[src0],        %[dest0_v],          %[dest1_v]       \n\t"
      "packsswh   %[src1],        %[dest2_v],          %[dest3_v]       \n\t"
      "packushb   %[dest0_v],     %[src0],             %[src1]          \n\t"
      "gssdlc1    %[dest0_v],     0x07(%[dst_v])                        \n\t"
      "gssdrc1    %[dest0_v],     0x00(%[dst_v])                        \n\t"

      "daddiu    %[src_rgb565],   %[src_rgb565],       0x20             \n\t"
      "daddiu    %[next_rgb565],  %[next_rgb565],      0x20             \n\t"
      "daddiu    %[dst_u],        %[dst_u],            0x08             \n\t"
      "daddiu    %[dst_v],        %[dst_v],            0x08             \n\t"
      "daddiu    %[width],        %[width],           -0x10             \n\t"
      "bgtz      %[width],        1b                                    \n\t"
      : [src0] "=&f"(ftmp[0]), [src1] "=&f"(ftmp[1]), [b0] "=&f"(ftmp[2]),
        [g0] "=&f"(ftmp[3]), [r0] "=&f"(ftmp[4]), [dest0_u] "=&f"(ftmp[5]),
        [dest1_u] "=&f"(ftmp[6]), [dest2_u] "=&f"(ftmp[7]),
        [dest3_u] "=&f"(ftmp[8]), [dest0_v] "=&f"(ftmp[9]),
        [dest1_v] "=&f"(ftmp[10]), [dest2_v] "=&f"(ftmp[11]),
        [dest3_v] "=&f"(ftmp[12])
      : [src_rgb565] "r"(src_rgb565), [next_rgb565] "r"(src_stride_rgb565),
        [dst_u] "r"(dst_u), [dst_v] "r"(dst_v), [width] "r"(width),
        [value] "f"(value), [c0] "f"(c0), [c1] "f"(c1), [c2] "f"(c2),
        [mask] "f"(mask), [mask_u] "f"(mask_u), [mask_v] "f"(mask_v),
        [eight] "f"(0x08), [six] "f"(0x06), [five] "f"(0x05), [three] "f"(0x03),
        [one] "f"(0x01)
      : "memory");
}

void ARGB1555ToUVRow_MMI(const uint8_t* src_argb1555,
                         int src_stride_argb1555,
                         uint8_t* dst_u,
                         uint8_t* dst_v,
                         int width) {
  uint64_t ftmp[11];
  uint64_t value = 0x2020202020202020;
  uint64_t mask_u = 0x0026004a00700002;
  uint64_t mask_v = 0x00020070005e0012;
  uint64_t mask = 0x93;
  uint64_t c0 = 0x001f001f001f001f;
  uint64_t c1 = 0x00ff00ff00ff00ff;
  uint64_t c2 = 0x0003000300030003;
  uint64_t c3 = 0x007c007c007c007c;
  __asm__ volatile(
      "daddu      %[next_argb1555], %[src_argb1555],      %[next_argb1555] \n\t"
      "1:                                                                  \n\t"
      "gsldrc1    %[src0],          0x00(%[src_argb1555])                  \n\t"
      "gsldlc1    %[src0],          0x07(%[src_argb1555])                  \n\t"
      "gsldrc1    %[src1],          0x00(%[next_argb1555])                 \n\t"
      "gsldlc1    %[src1],          0x07(%[next_argb1555])                 \n\t"
      "psrlh      %[dest0_u],       %[src0],               %[eight]        \n\t"
      "and        %[b0],            %[src0],               %[c0]           \n\t"
      "and        %[src0],          %[src0],               %[c1]           \n\t"
      "psrlh      %[src0],          %[src0],               %[five]         \n\t"
      "and        %[g0],            %[dest0_u],            %[c2]           \n\t"
      "psllh      %[g0],            %[g0],                 %[three]        \n\t"
      "or         %[g0],            %[src0],               %[g0]           \n\t"
      "and        %[r0],            %[dest0_u],            %[c3]           \n\t"
      "psrlh      %[r0],            %[r0],                 %[two]          \n\t"
      "psrlh      %[src0],          %[src1],               %[eight]        \n\t"
      "and        %[dest0_u],       %[src1],               %[c0]           \n\t"
      "and        %[src1],          %[src1],               %[c1]           \n\t"
      "psrlh      %[src1],          %[src1],               %[five]         \n\t"
      "and        %[dest0_v],       %[src0],               %[c2]           \n\t"
      "psllh      %[dest0_v],       %[dest0_v],            %[three]        \n\t"
      "or         %[dest0_v],       %[src1],               %[dest0_v]      \n\t"
      "and        %[src0],          %[src0],               %[c3]           \n\t"
      "psrlh      %[src0],          %[src0],               %[two]          \n\t"
      "paddh      %[b0],            %[b0],                 %[dest0_u]      \n\t"
      "paddh      %[g0],            %[g0],                 %[dest0_v]      \n\t"
      "paddh      %[r0],            %[r0],                 %[src0]         \n\t"
      "punpcklhw  %[src0],          %[b0],                 %[r0]           \n\t"
      "punpckhhw  %[src1],          %[b0],                 %[r0]           \n\t"
      "punpcklwd  %[dest0_u],       %[src0],               %[src1]         \n\t"
      "punpckhwd  %[dest0_v],       %[src0],               %[src1]         \n\t"
      "paddh      %[src0],          %[dest0_u],            %[dest0_v]      \n\t"
      "psrlh      %[b0],            %[src0],               %[six]          \n\t"
      "psllh      %[r0],            %[src0],               %[one]          \n\t"
      "or         %[b0],            %[b0],                 %[r0]           \n\t"
      "psrlh      %[r0],            %[g0],                 %[six]          \n\t"
      "psllh      %[g0],            %[g0],                 %[one]          \n\t"
      "or         %[g0],            %[g0],                 %[r0]           \n\t"
      "punpcklhw  %[src0],          %[g0],                 %[value]        \n\t"
      "punpckhhw  %[src1],          %[g0],                 %[value]        \n\t"
      "punpcklwd  %[dest0_u],       %[src0],               %[src1]         \n\t"
      "punpckhwd  %[dest0_v],       %[src0],               %[src1]         \n\t"
      "paddh      %[g0],            %[dest0_u],            %[dest0_v]      \n\t"
      "punpcklhw  %[src0],          %[b0],                 %[g0]           \n\t"
      "punpckhhw  %[src1],          %[b0],                 %[g0]           \n\t"

      "pmaddhw    %[dest0_v],       %[src0],               %[mask_v]       \n\t"
      "pshufh     %[dest0_u],       %[src0],               %[mask]         \n\t"
      "pmaddhw    %[dest0_u],       %[dest0_u],            %[mask_u]       \n\t"
      "pmaddhw    %[g0],            %[src1],               %[mask_v]       \n\t"
      "pshufh     %[b0],            %[src1],               %[mask]         \n\t"
      "pmaddhw    %[b0],            %[b0],                 %[mask_u]       \n\t"

      "punpcklwd  %[src0],          %[dest0_u],            %[b0]           \n\t"
      "punpckhwd  %[src1],          %[dest0_u],            %[b0]           \n\t"
      "psubw      %[dest0_u],       %[src0],               %[src1]         \n\t"
      "psraw      %[dest0_u],       %[dest0_u],            %[eight]        \n\t"
      "punpcklwd  %[src0],          %[dest0_v],            %[g0]           \n\t"
      "punpckhwd  %[src1],          %[dest0_v],            %[g0]           \n\t"
      "psubw      %[dest0_v],       %[src1],               %[src0]         \n\t"
      "psraw      %[dest0_v],       %[dest0_v],            %[eight]        \n\t"

      "gsldrc1    %[src0],          0x08(%[src_argb1555])                  \n\t"
      "gsldlc1    %[src0],          0x0f(%[src_argb1555])                  \n\t"
      "gsldrc1    %[src1],          0x08(%[next_argb1555])                 \n\t"
      "gsldlc1    %[src1],          0x0f(%[next_argb1555])                 \n\t"
      "psrlh      %[dest1_u],       %[src0],               %[eight]        \n\t"
      "and        %[b0],            %[src0],               %[c0]           \n\t"
      "and        %[src0],          %[src0],               %[c1]           \n\t"
      "psrlh      %[src0],          %[src0],               %[five]         \n\t"
      "and        %[g0],            %[dest1_u],            %[c2]           \n\t"
      "psllh      %[g0],            %[g0],                 %[three]        \n\t"
      "or         %[g0],            %[src0],               %[g0]           \n\t"
      "and        %[r0],            %[dest1_u],            %[c3]           \n\t"
      "psrlh      %[r0],            %[r0],                 %[two]          \n\t"
      "psrlh      %[src0],          %[src1],               %[eight]        \n\t"
      "and        %[dest1_u],       %[src1],               %[c0]           \n\t"
      "and        %[src1],          %[src1],               %[c1]           \n\t"
      "psrlh      %[src1],          %[src1],               %[five]         \n\t"
      "and        %[dest1_v],       %[src0],               %[c2]           \n\t"
      "psllh      %[dest1_v],       %[dest1_v],            %[three]        \n\t"
      "or         %[dest1_v],       %[src1],               %[dest1_v]      \n\t"
      "and        %[src0],          %[src0],               %[c3]           \n\t"
      "psrlh      %[src0],          %[src0],               %[two]          \n\t"
      "paddh      %[b0],            %[b0],                 %[dest1_u]      \n\t"
      "paddh      %[g0],            %[g0],                 %[dest1_v]      \n\t"
      "paddh      %[r0],            %[r0],                 %[src0]         \n\t"
      "punpcklhw  %[src0],          %[b0],                 %[r0]           \n\t"
      "punpckhhw  %[src1],          %[b0],                 %[r0]           \n\t"
      "punpcklwd  %[dest1_u],       %[src0],               %[src1]         \n\t"
      "punpckhwd  %[dest1_v],       %[src0],               %[src1]         \n\t"
      "paddh      %[src0],          %[dest1_u],            %[dest1_v]      \n\t"
      "psrlh      %[b0],            %[src0],               %[six]          \n\t"
      "psllh      %[r0],            %[src0],               %[one]          \n\t"
      "or         %[b0],            %[b0],                 %[r0]           \n\t"
      "psrlh      %[r0],            %[g0],                 %[six]          \n\t"
      "psllh      %[g0],            %[g0],                 %[one]          \n\t"
      "or         %[g0],            %[g0],                 %[r0]           \n\t"
      "punpcklhw  %[src0],          %[g0],                 %[value]        \n\t"
      "punpckhhw  %[src1],          %[g0],                 %[value]        \n\t"
      "punpcklwd  %[dest1_u],       %[src0],               %[src1]         \n\t"
      "punpckhwd  %[dest1_v],       %[src0],               %[src1]         \n\t"
      "paddh      %[g0],            %[dest1_u],            %[dest1_v]      \n\t"
      "punpcklhw  %[src0],          %[b0],                 %[g0]           \n\t"
      "punpckhhw  %[src1],          %[b0],                 %[g0]           \n\t"

      "pmaddhw    %[dest1_v],       %[src0],               %[mask_v]       \n\t"
      "pshufh     %[dest1_u],       %[src0],               %[mask]         \n\t"
      "pmaddhw    %[dest1_u],       %[dest1_u],            %[mask_u]       \n\t"
      "pmaddhw    %[g0],            %[src1],               %[mask_v]       \n\t"
      "pshufh     %[b0],            %[src1],               %[mask]         \n\t"
      "pmaddhw    %[b0],            %[b0],                 %[mask_u]       \n\t"

      "punpcklwd  %[src0],          %[dest1_u],            %[b0]           \n\t"
      "punpckhwd  %[src1],          %[dest1_u],            %[b0]           \n\t"
      "psubw      %[dest1_u],       %[src0],               %[src1]         \n\t"
      "psraw      %[dest1_u],       %[dest1_u],            %[eight]        \n\t"
      "punpcklwd  %[src0],          %[dest1_v],            %[g0]           \n\t"
      "punpckhwd  %[src1],          %[dest1_v],            %[g0]           \n\t"
      "psubw      %[dest1_v],       %[src1],               %[src0]         \n\t"
      "psraw      %[dest1_v],       %[dest1_v],            %[eight]        \n\t"

      "packsswh   %[dest0_u],       %[dest0_u],            %[dest1_u]      \n\t"
      "packsswh   %[dest1_u],       %[dest0_v],            %[dest1_v]      \n\t"

      "gsldrc1    %[src0],          0x10(%[src_argb1555])                  \n\t"
      "gsldlc1    %[src0],          0x17(%[src_argb1555])                  \n\t"
      "gsldrc1    %[src1],          0x10(%[next_argb1555])                 \n\t"
      "gsldlc1    %[src1],          0x17(%[next_argb1555])                 \n\t"
      "psrlh      %[dest2_u],       %[src0],               %[eight]        \n\t"
      "and        %[b0],            %[src0],               %[c0]           \n\t"
      "and        %[src0],          %[src0],               %[c1]           \n\t"
      "psrlh      %[src0],          %[src0],               %[five]         \n\t"
      "and        %[g0],            %[dest2_u],            %[c2]           \n\t"
      "psllh      %[g0],            %[g0],                 %[three]        \n\t"
      "or         %[g0],            %[src0],               %[g0]           \n\t"
      "and        %[r0],            %[dest2_u],            %[c3]           \n\t"
      "psrlh      %[r0],            %[r0],                 %[two]          \n\t"
      "psrlh      %[src0],          %[src1],               %[eight]        \n\t"
      "and        %[dest2_u],       %[src1],               %[c0]           \n\t"
      "and        %[src1],          %[src1],               %[c1]           \n\t"
      "psrlh      %[src1],          %[src1],               %[five]         \n\t"
      "and        %[dest0_v],       %[src0],               %[c2]           \n\t"
      "psllh      %[dest0_v],       %[dest0_v],            %[three]        \n\t"
      "or         %[dest0_v],       %[src1],               %[dest0_v]      \n\t"
      "and        %[src0],          %[src0],               %[c3]           \n\t"
      "psrlh      %[src0],          %[src0],               %[two]          \n\t"
      "paddh      %[b0],            %[b0],                 %[dest2_u]      \n\t"
      "paddh      %[g0],            %[g0],                 %[dest0_v]      \n\t"
      "paddh      %[r0],            %[r0],                 %[src0]         \n\t"
      "punpcklhw  %[src0],          %[b0],                 %[r0]           \n\t"
      "punpckhhw  %[src1],          %[b0],                 %[r0]           \n\t"
      "punpcklwd  %[dest2_u],       %[src0],               %[src1]         \n\t"
      "punpckhwd  %[dest0_v],       %[src0],               %[src1]         \n\t"
      "paddh      %[src0],          %[dest2_u],            %[dest0_v]      \n\t"
      "psrlh      %[b0],            %[src0],               %[six]          \n\t"
      "psllh      %[r0],            %[src0],               %[one]          \n\t"
      "or         %[b0],            %[b0],                 %[r0]           \n\t"
      "psrlh      %[r0],            %[g0],                 %[six]          \n\t"
      "psllh      %[g0],            %[g0],                 %[one]          \n\t"
      "or         %[g0],            %[g0],                 %[r0]           \n\t"
      "punpcklhw  %[src0],          %[g0],                 %[value]        \n\t"
      "punpckhhw  %[src1],          %[g0],                 %[value]        \n\t"
      "punpcklwd  %[dest2_u],       %[src0],               %[src1]         \n\t"
      "punpckhwd  %[dest0_v],       %[src0],               %[src1]         \n\t"
      "paddh      %[g0],            %[dest2_u],            %[dest0_v]      \n\t"
      "punpcklhw  %[src0],          %[b0],                 %[g0]           \n\t"
      "punpckhhw  %[src1],          %[b0],                 %[g0]           \n\t"

      "pmaddhw    %[dest0_v],       %[src0],               %[mask_v]       \n\t"
      "pshufh     %[dest2_u],       %[src0],               %[mask]         \n\t"
      "pmaddhw    %[dest2_u],       %[dest2_u],            %[mask_u]       \n\t"
      "pmaddhw    %[g0],            %[src1],               %[mask_v]       \n\t"
      "pshufh     %[b0],            %[src1],               %[mask]         \n\t"
      "pmaddhw    %[b0],            %[b0],                 %[mask_u]       \n\t"

      "punpcklwd  %[src0],          %[dest2_u],            %[b0]           \n\t"
      "punpckhwd  %[src1],          %[dest2_u],            %[b0]           \n\t"
      "psubw      %[dest2_u],       %[src0],               %[src1]         \n\t"
      "psraw      %[dest2_u],       %[dest2_u],            %[eight]        \n\t"
      "punpcklwd  %[src0],          %[dest0_v],            %[g0]           \n\t"
      "punpckhwd  %[src1],          %[dest0_v],            %[g0]           \n\t"
      "psubw      %[dest0_v],       %[src1],               %[src0]         \n\t"
      "psraw      %[dest0_v],       %[dest0_v],            %[eight]        \n\t"

      "gsldrc1    %[src0],          0x18(%[src_argb1555])                  \n\t"
      "gsldlc1    %[src0],          0x1f(%[src_argb1555])                  \n\t"
      "gsldrc1    %[src1],          0x18(%[next_argb1555])                 \n\t"
      "gsldlc1    %[src1],          0x1f(%[next_argb1555])                 \n\t"
      "psrlh      %[dest3_u],       %[src0],               %[eight]        \n\t"
      "and        %[b0],            %[src0],               %[c0]           \n\t"
      "and        %[src0],          %[src0],               %[c1]           \n\t"
      "psrlh      %[src0],          %[src0],               %[five]         \n\t"
      "and        %[g0],            %[dest3_u],            %[c2]           \n\t"
      "psllh      %[g0],            %[g0],                 %[three]        \n\t"
      "or         %[g0],            %[src0],               %[g0]           \n\t"
      "and        %[r0],            %[dest3_u],            %[c3]           \n\t"
      "psrlh      %[r0],            %[r0],                 %[two]          \n\t"
      "psrlh      %[src0],          %[src1],               %[eight]        \n\t"
      "and        %[dest3_u],       %[src1],               %[c0]           \n\t"
      "and        %[src1],          %[src1],               %[c1]           \n\t"
      "psrlh      %[src1],          %[src1],               %[five]         \n\t"
      "and        %[dest1_v],       %[src0],               %[c2]           \n\t"
      "psllh      %[dest1_v],       %[dest1_v],            %[three]        \n\t"
      "or         %[dest1_v],       %[src1],               %[dest1_v]      \n\t"
      "and        %[src0],          %[src0],               %[c3]           \n\t"
      "psrlh      %[src0],          %[src0],               %[two]          \n\t"
      "paddh      %[b0],            %[b0],                 %[dest3_u]      \n\t"
      "paddh      %[g0],            %[g0],                 %[dest1_v]      \n\t"
      "paddh      %[r0],            %[r0],                 %[src0]         \n\t"
      "punpcklhw  %[src0],          %[b0],                 %[r0]           \n\t"
      "punpckhhw  %[src1],          %[b0],                 %[r0]           \n\t"
      "punpcklwd  %[dest3_u],       %[src0],               %[src1]         \n\t"
      "punpckhwd  %[dest1_v],       %[src0],               %[src1]         \n\t"
      "paddh      %[src0],          %[dest3_u],            %[dest1_v]      \n\t"
      "psrlh      %[b0],            %[src0],               %[six]          \n\t"
      "psllh      %[r0],            %[src0],               %[one]          \n\t"
      "or         %[b0],            %[b0],                 %[r0]           \n\t"
      "psrlh      %[r0],            %[g0],                 %[six]          \n\t"
      "psllh      %[g0],            %[g0],                 %[one]          \n\t"
      "or         %[g0],            %[g0],                 %[r0]           \n\t"
      "punpcklhw  %[src0],          %[g0],                 %[value]        \n\t"
      "punpckhhw  %[src1],          %[g0],                 %[value]        \n\t"
      "punpcklwd  %[dest3_u],       %[src0],               %[src1]         \n\t"
      "punpckhwd  %[dest1_v],       %[src0],               %[src1]         \n\t"
      "paddh      %[g0],            %[dest3_u],            %[dest1_v]      \n\t"
      "punpcklhw  %[src0],          %[b0],                 %[g0]           \n\t"
      "punpckhhw  %[src1],          %[b0],                 %[g0]           \n\t"

      "pmaddhw    %[dest1_v],       %[src0],               %[mask_v]       \n\t"
      "pshufh     %[dest3_u],       %[src0],               %[mask]         \n\t"
      "pmaddhw    %[dest3_u],       %[dest3_u],            %[mask_u]       \n\t"
      "pmaddhw    %[g0],            %[src1],               %[mask_v]       \n\t"
      "pshufh     %[b0],            %[src1],               %[mask]         \n\t"
      "pmaddhw    %[b0],            %[b0],                 %[mask_u]       \n\t"

      "punpcklwd  %[src0],          %[dest3_u],            %[b0]           \n\t"
      "punpckhwd  %[src1],          %[dest3_u],            %[b0]           \n\t"
      "psubw      %[dest3_u],       %[src0],               %[src1]         \n\t"
      "psraw      %[dest3_u],       %[dest3_u],            %[eight]        \n\t"
      "punpcklwd  %[src0],          %[dest1_v],            %[g0]           \n\t"
      "punpckhwd  %[src1],          %[dest1_v],            %[g0]           \n\t"
      "psubw      %[dest1_v],       %[src1],               %[src0]         \n\t"
      "psraw      %[dest1_v],       %[dest1_v],            %[eight]        \n\t"

      "packsswh   %[src1],          %[dest2_u],            %[dest3_u]      \n\t"
      "packushb   %[dest0_u],       %[dest0_u],            %[src1]         \n\t"
      "gssdlc1    %[dest0_u],       0x07(%[dst_u])                         \n\t"
      "gssdrc1    %[dest0_u],       0x00(%[dst_u])                         \n\t"
      "packsswh   %[src1],          %[dest0_v],            %[dest1_v]      \n\t"
      "packushb   %[dest0_v],       %[dest1_u],            %[src1]         \n\t"
      "gssdlc1    %[dest0_v],       0x07(%[dst_v])                         \n\t"
      "gssdrc1    %[dest0_v],       0x00(%[dst_v])                         \n\t"

      "daddiu    %[src_argb1555],   %[src_argb1555],       0x20            \n\t"
      "daddiu    %[next_argb1555],  %[next_argb1555],      0x20            \n\t"
      "daddiu    %[dst_u],          %[dst_u],              0x08            \n\t"
      "daddiu    %[dst_v],          %[dst_v],              0x08            \n\t"
      "daddiu    %[width],          %[width],             -0x10            \n\t"
      "bgtz      %[width],          1b                                     \n\t"
      : [src0] "=&f"(ftmp[0]), [src1] "=&f"(ftmp[1]), [b0] "=&f"(ftmp[2]),
        [g0] "=&f"(ftmp[3]), [r0] "=&f"(ftmp[4]), [dest0_u] "=&f"(ftmp[5]),
        [dest1_u] "=&f"(ftmp[6]), [dest2_u] "=&f"(ftmp[7]),
        [dest3_u] "=&f"(ftmp[8]), [dest0_v] "=&f"(ftmp[9]),
        [dest1_v] "=&f"(ftmp[10])
      : [src_argb1555] "r"(src_argb1555),
        [next_argb1555] "r"(src_stride_argb1555), [dst_u] "r"(dst_u),
        [dst_v] "r"(dst_v), [width] "r"(width), [value] "f"(value),
        [c0] "f"(c0), [c1] "f"(c1), [c2] "f"(c2), [c3] "f"(c3),
        [mask] "f"(mask), [mask_u] "f"(mask_u), [mask_v] "f"(mask_v),
        [eight] "f"(0x08), [six] "f"(0x06), [five] "f"(0x05), [three] "f"(0x03),
        [two] "f"(0x02), [one] "f"(0x01)
      : "memory");
}

void ARGB4444ToUVRow_MMI(const uint8_t* src_argb4444,
                         int src_stride_argb4444,
                         uint8_t* dst_u,
                         uint8_t* dst_v,
                         int width) {
  uint64_t ftmp[13];
  uint64_t value = 0x2020202020202020;
  uint64_t mask_u = 0x0026004a00700002;
  uint64_t mask_v = 0x00020070005e0012;
  uint64_t mask = 0x93;
  uint64_t c0 = 0x000f000f000f000f;
  uint64_t c1 = 0x00ff00ff00ff00ff;
  __asm__ volatile(
      "daddu      %[next_argb4444], %[src_argb4444],      %[next_argb4444] \n\t"
      "1:                                                                  \n\t"
      "gsldrc1    %[src0],          0x00(%[src_argb4444])                  \n\t"
      "gsldlc1    %[src0],          0x07(%[src_argb4444])                  \n\t"
      "gsldrc1    %[src1],          0x00(%[next_argb4444])                 \n\t"
      "gsldlc1    %[src1],          0x07(%[next_argb4444])                 \n\t"
      "psrlh      %[dest0_u],       %[src0],               %[eight]        \n\t"
      "and        %[b0],            %[src0],               %[c0]           \n\t"
      "and        %[src0],          %[src0],               %[c1]           \n\t"
      "psrlh      %[g0],            %[src0],               %[four]         \n\t"
      "and        %[r0],            %[dest0_u],            %[c0]           \n\t"
      "psrlh      %[src0],          %[src1],               %[eight]        \n\t"
      "and        %[dest0_u],       %[src1],               %[c0]           \n\t"
      "and        %[src1],          %[src1],               %[c1]           \n\t"
      "psrlh      %[dest0_v],       %[src1],               %[four]         \n\t"
      "and        %[src0],          %[src0],               %[c0]           \n\t"
      "paddh      %[b0],            %[b0],                 %[dest0_u]      \n\t"
      "paddh      %[g0],            %[g0],                 %[dest0_v]      \n\t"
      "paddh      %[r0],            %[r0],                 %[src0]         \n\t"
      "punpcklhw  %[src0],          %[b0],                 %[r0]           \n\t"
      "punpckhhw  %[src1],          %[b0],                 %[r0]           \n\t"
      "punpcklwd  %[dest0_u],       %[src0],               %[src1]         \n\t"
      "punpckhwd  %[dest0_v],       %[src0],               %[src1]         \n\t"
      "paddh      %[src0],          %[dest0_u],            %[dest0_v]      \n\t"
      "psrlh      %[b0],            %[src0],               %[four]         \n\t"
      "psllh      %[r0],            %[src0],               %[two]          \n\t"
      "or         %[b0],            %[b0],                 %[r0]           \n\t"
      "psrlh      %[r0],            %[g0],                 %[four]         \n\t"
      "psllh      %[g0],            %[g0],                 %[two]          \n\t"
      "or         %[g0],            %[g0],                 %[r0]           \n\t"
      "punpcklhw  %[src0],          %[g0],                 %[value]        \n\t"
      "punpckhhw  %[src1],          %[g0],                 %[value]        \n\t"
      "punpcklwd  %[dest0_u],       %[src0],               %[src1]         \n\t"
      "punpckhwd  %[dest0_v],       %[src0],               %[src1]         \n\t"
      "paddh      %[g0],            %[dest0_u],            %[dest0_v]      \n\t"
      "punpcklhw  %[src0],          %[b0],                 %[g0]           \n\t"
      "punpckhhw  %[src1],          %[b0],                 %[g0]           \n\t"

      "pmaddhw    %[dest0_v],       %[src0],               %[mask_v]       \n\t"
      "pshufh     %[dest0_u],       %[src0],               %[mask]         \n\t"
      "pmaddhw    %[dest0_u],       %[dest0_u],            %[mask_u]       \n\t"
      "pmaddhw    %[g0],            %[src1],               %[mask_v]       \n\t"
      "pshufh     %[b0],            %[src1],               %[mask]         \n\t"
      "pmaddhw    %[b0],            %[b0],                 %[mask_u]       \n\t"

      "punpcklwd  %[src0],          %[dest0_u],            %[b0]           \n\t"
      "punpckhwd  %[src1],          %[dest0_u],            %[b0]           \n\t"
      "psubw      %[dest0_u],       %[src0],               %[src1]         \n\t"
      "psraw      %[dest0_u],       %[dest0_u],            %[eight]        \n\t"
      "punpcklwd  %[src0],          %[dest0_v],            %[g0]           \n\t"
      "punpckhwd  %[src1],          %[dest0_v],            %[g0]           \n\t"
      "psubw      %[dest0_v],       %[src1],               %[src0]         \n\t"
      "psraw      %[dest0_v],       %[dest0_v],            %[eight]        \n\t"

      "gsldrc1    %[src0],          0x08(%[src_argb4444])                  \n\t"
      "gsldlc1    %[src0],          0x0f(%[src_argb4444])                  \n\t"
      "gsldrc1    %[src1],          0x08(%[next_argb4444])                 \n\t"
      "gsldlc1    %[src1],          0x0f(%[next_argb4444])                 \n\t"
      "psrlh      %[dest1_u],       %[src0],               %[eight]        \n\t"
      "and        %[b0],            %[src0],               %[c0]           \n\t"
      "and        %[src0],          %[src0],               %[c1]           \n\t"
      "psrlh      %[g0],            %[src0],               %[four]         \n\t"
      "and        %[r0],            %[dest1_u],            %[c0]           \n\t"
      "psrlh      %[src0],          %[src1],               %[eight]        \n\t"
      "and        %[dest1_u],       %[src1],               %[c0]           \n\t"
      "and        %[src1],          %[src1],               %[c1]           \n\t"
      "psrlh      %[dest1_v],       %[src1],               %[four]         \n\t"
      "and        %[src0],          %[src0],               %[c0]           \n\t"
      "paddh      %[b0],            %[b0],                 %[dest1_u]      \n\t"
      "paddh      %[g0],            %[g0],                 %[dest1_v]      \n\t"
      "paddh      %[r0],            %[r0],                 %[src0]         \n\t"
      "punpcklhw  %[src0],          %[b0],                 %[r0]           \n\t"
      "punpckhhw  %[src1],          %[b0],                 %[r0]           \n\t"
      "punpcklwd  %[dest1_u],       %[src0],               %[src1]         \n\t"
      "punpckhwd  %[dest1_v],       %[src0],               %[src1]         \n\t"
      "paddh      %[src0],          %[dest1_u],            %[dest1_v]      \n\t"
      "psrlh      %[b0],            %[src0],               %[four]         \n\t"
      "psllh      %[r0],            %[src0],               %[two]          \n\t"
      "or         %[b0],            %[b0],                 %[r0]           \n\t"
      "psrlh      %[r0],            %[g0],                 %[four]         \n\t"
      "psllh      %[g0],            %[g0],                 %[two]          \n\t"
      "or         %[g0],            %[g0],                 %[r0]           \n\t"
      "punpcklhw  %[src0],          %[g0],                 %[value]        \n\t"
      "punpckhhw  %[src1],          %[g0],                 %[value]        \n\t"
      "punpcklwd  %[dest1_u],       %[src0],               %[src1]         \n\t"
      "punpckhwd  %[dest1_v],       %[src0],               %[src1]         \n\t"
      "paddh      %[g0],            %[dest1_u],            %[dest1_v]      \n\t"
      "punpcklhw  %[src0],          %[b0],                 %[g0]           \n\t"
      "punpckhhw  %[src1],          %[b0],                 %[g0]           \n\t"

      "pmaddhw    %[dest1_v],       %[src0],               %[mask_v]       \n\t"
      "pshufh     %[dest1_u],       %[src0],               %[mask]         \n\t"
      "pmaddhw    %[dest1_u],       %[dest1_u],            %[mask_u]       \n\t"
      "pmaddhw    %[g0],            %[src1],               %[mask_v]       \n\t"
      "pshufh     %[b0],            %[src1],               %[mask]         \n\t"
      "pmaddhw    %[b0],            %[b0],                 %[mask_u]       \n\t"

      "punpcklwd  %[src0],          %[dest1_u],            %[b0]           \n\t"
      "punpckhwd  %[src1],          %[dest1_u],            %[b0]           \n\t"
      "psubw      %[dest1_u],       %[src0],               %[src1]         \n\t"
      "psraw      %[dest1_u],       %[dest1_u],            %[eight]        \n\t"
      "punpcklwd  %[src0],          %[dest1_v],            %[g0]           \n\t"
      "punpckhwd  %[src1],          %[dest1_v],            %[g0]           \n\t"
      "psubw      %[dest1_v],       %[src1],               %[src0]         \n\t"
      "psraw      %[dest1_v],       %[dest1_v],            %[eight]        \n\t"

      "gsldrc1    %[src0],          0x10(%[src_argb4444])                  \n\t"
      "gsldlc1    %[src0],          0x17(%[src_argb4444])                  \n\t"
      "gsldrc1    %[src1],          0x10(%[next_argb4444])                 \n\t"
      "gsldlc1    %[src1],          0x17(%[next_argb4444])                 \n\t"
      "psrlh      %[dest2_u],       %[src0],               %[eight]        \n\t"
      "and        %[b0],            %[src0],               %[c0]           \n\t"
      "and        %[src0],          %[src0],               %[c1]           \n\t"
      "psrlh      %[g0],            %[src0],               %[four]         \n\t"
      "and        %[r0],            %[dest2_u],            %[c0]           \n\t"
      "psrlh      %[src0],          %[src1],               %[eight]        \n\t"
      "and        %[dest2_u],       %[src1],               %[c0]           \n\t"
      "and        %[src1],          %[src1],               %[c1]           \n\t"
      "psrlh      %[dest2_v],       %[src1],               %[four]         \n\t"
      "and        %[src0],          %[src0],               %[c0]           \n\t"
      "paddh      %[b0],            %[b0],                 %[dest2_u]      \n\t"
      "paddh      %[g0],            %[g0],                 %[dest2_v]      \n\t"
      "paddh      %[r0],            %[r0],                 %[src0]         \n\t"
      "punpcklhw  %[src0],          %[b0],                 %[r0]           \n\t"
      "punpckhhw  %[src1],          %[b0],                 %[r0]           \n\t"
      "punpcklwd  %[dest2_u],       %[src0],               %[src1]         \n\t"
      "punpckhwd  %[dest2_v],       %[src0],               %[src1]         \n\t"
      "paddh      %[src0],          %[dest2_u],            %[dest2_v]      \n\t"
      "psrlh      %[b0],            %[src0],               %[four]         \n\t"
      "psllh      %[r0],            %[src0],               %[two]          \n\t"
      "or         %[b0],            %[b0],                 %[r0]           \n\t"
      "psrlh      %[r0],            %[g0],                 %[four]         \n\t"
      "psllh      %[g0],            %[g0],                 %[two]          \n\t"
      "or         %[g0],            %[g0],                 %[r0]           \n\t"
      "punpcklhw  %[src0],          %[g0],                 %[value]        \n\t"
      "punpckhhw  %[src1],          %[g0],                 %[value]        \n\t"
      "punpcklwd  %[dest2_u],       %[src0],               %[src1]         \n\t"
      "punpckhwd  %[dest2_v],       %[src0],               %[src1]         \n\t"
      "paddh      %[g0],            %[dest2_u],            %[dest2_v]      \n\t"
      "punpcklhw  %[src0],          %[b0],                 %[g0]           \n\t"
      "punpckhhw  %[src1],          %[b0],                 %[g0]           \n\t"

      "pmaddhw    %[dest2_v],       %[src0],               %[mask_v]       \n\t"
      "pshufh     %[dest2_u],       %[src0],               %[mask]         \n\t"
      "pmaddhw    %[dest2_u],       %[dest2_u],            %[mask_u]       \n\t"
      "pmaddhw    %[g0],            %[src1],               %[mask_v]       \n\t"
      "pshufh     %[b0],            %[src1],               %[mask]         \n\t"
      "pmaddhw    %[b0],            %[b0],                 %[mask_u]       \n\t"

      "punpcklwd  %[src0],          %[dest2_u],            %[b0]           \n\t"
      "punpckhwd  %[src1],          %[dest2_u],            %[b0]           \n\t"
      "psubw      %[dest2_u],       %[src0],               %[src1]         \n\t"
      "psraw      %[dest2_u],       %[dest2_u],            %[eight]        \n\t"
      "punpcklwd  %[src0],          %[dest2_v],            %[g0]           \n\t"
      "punpckhwd  %[src1],          %[dest2_v],            %[g0]           \n\t"
      "psubw      %[dest2_v],       %[src1],               %[src0]         \n\t"
      "psraw      %[dest2_v],       %[dest2_v],            %[eight]        \n\t"

      "gsldrc1    %[src0],          0x18(%[src_argb4444])                  \n\t"
      "gsldlc1    %[src0],          0x1f(%[src_argb4444])                  \n\t"
      "gsldrc1    %[src1],          0x18(%[next_argb4444])                 \n\t"
      "gsldlc1    %[src1],          0x1f(%[next_argb4444])                 \n\t"
      "psrlh      %[dest3_u],       %[src0],               %[eight]        \n\t"
      "and        %[b0],            %[src0],               %[c0]           \n\t"
      "and        %[src0],          %[src0],               %[c1]           \n\t"
      "psrlh      %[g0],            %[src0],               %[four]         \n\t"
      "and        %[r0],            %[dest3_u],            %[c0]           \n\t"
      "psrlh      %[src0],          %[src1],               %[eight]        \n\t"
      "and        %[dest3_u],       %[src1],               %[c0]           \n\t"
      "and        %[src1],          %[src1],               %[c1]           \n\t"
      "psrlh      %[dest3_v],       %[src1],               %[four]         \n\t"
      "and        %[src0],          %[src0],               %[c0]           \n\t"
      "paddh      %[b0],            %[b0],                 %[dest3_u]      \n\t"
      "paddh      %[g0],            %[g0],                 %[dest3_v]      \n\t"
      "paddh      %[r0],            %[r0],                 %[src0]         \n\t"
      "punpcklhw  %[src0],          %[b0],                 %[r0]           \n\t"
      "punpckhhw  %[src1],          %[b0],                 %[r0]           \n\t"
      "punpcklwd  %[dest3_u],       %[src0],               %[src1]         \n\t"
      "punpckhwd  %[dest3_v],       %[src0],               %[src1]         \n\t"
      "paddh      %[src0],          %[dest3_u],            %[dest3_v]      \n\t"
      "psrlh      %[b0],            %[src0],               %[four]         \n\t"
      "psllh      %[r0],            %[src0],               %[two]          \n\t"
      "or         %[b0],            %[b0],                 %[r0]           \n\t"
      "psrlh      %[r0],            %[g0],                 %[four]         \n\t"
      "psllh      %[g0],            %[g0],                 %[two]          \n\t"
      "or         %[g0],            %[g0],                 %[r0]           \n\t"
      "punpcklhw  %[src0],          %[g0],                 %[value]        \n\t"
      "punpckhhw  %[src1],          %[g0],                 %[value]        \n\t"
      "punpcklwd  %[dest3_u],       %[src0],               %[src1]         \n\t"
      "punpckhwd  %[dest3_v],       %[src0],               %[src1]         \n\t"
      "paddh      %[g0],            %[dest3_u],            %[dest3_v]      \n\t"
      "punpcklhw  %[src0],          %[b0],                 %[g0]           \n\t"
      "punpckhhw  %[src1],          %[b0],                 %[g0]           \n\t"

      "pmaddhw    %[dest3_v],       %[src0],               %[mask_v]       \n\t"
      "pshufh     %[dest3_u],       %[src0],               %[mask]         \n\t"
      "pmaddhw    %[dest3_u],       %[dest3_u],            %[mask_u]       \n\t"
      "pmaddhw    %[g0],            %[src1],               %[mask_v]       \n\t"
      "pshufh     %[b0],            %[src1],               %[mask]         \n\t"
      "pmaddhw    %[b0],            %[b0],                 %[mask_u]       \n\t"

      "punpcklwd  %[src0],          %[dest3_u],            %[b0]           \n\t"
      "punpckhwd  %[src1],          %[dest3_u],            %[b0]           \n\t"
      "psubw      %[dest3_u],       %[src0],               %[src1]         \n\t"
      "psraw      %[dest3_u],       %[dest3_u],            %[eight]        \n\t"
      "punpcklwd  %[src0],          %[dest3_v],            %[g0]           \n\t"
      "punpckhwd  %[src1],          %[dest3_v],            %[g0]           \n\t"
      "psubw      %[dest3_v],       %[src1],               %[src0]         \n\t"
      "psraw      %[dest3_v],       %[dest3_v],            %[eight]        \n\t"

      "packsswh   %[src0],          %[dest0_u],            %[dest1_u]      \n\t"
      "packsswh   %[src1],          %[dest2_u],            %[dest3_u]      \n\t"
      "packushb   %[dest0_u],       %[src0],               %[src1]         \n\t"
      "gssdlc1    %[dest0_u],       0x07(%[dst_u])                         \n\t"
      "gssdrc1    %[dest0_u],       0x00(%[dst_u])                         \n\t"
      "packsswh   %[src0],          %[dest0_v],            %[dest1_v]      \n\t"
      "packsswh   %[src1],          %[dest2_v],            %[dest3_v]      \n\t"
      "packushb   %[dest0_v],       %[src0],               %[src1]         \n\t"
      "gssdlc1    %[dest0_v],       0x07(%[dst_v])                         \n\t"
      "gssdrc1    %[dest0_v],       0x00(%[dst_v])                         \n\t"

      "daddiu    %[src_argb4444],   %[src_argb4444],       0x20            \n\t"
      "daddiu    %[next_argb4444],  %[next_argb4444],      0x20            \n\t"
      "daddiu    %[dst_u],          %[dst_u],              0x08            \n\t"
      "daddiu    %[dst_v],          %[dst_v],              0x08            \n\t"
      "daddiu    %[width],          %[width],             -0x10            \n\t"
      "bgtz      %[width],          1b                                     \n\t"
      : [src0] "=&f"(ftmp[0]), [src1] "=&f"(ftmp[1]), [b0] "=&f"(ftmp[2]),
        [g0] "=&f"(ftmp[3]), [r0] "=&f"(ftmp[4]), [dest0_u] "=&f"(ftmp[5]),
        [dest1_u] "=&f"(ftmp[6]), [dest2_u] "=&f"(ftmp[7]),
        [dest3_u] "=&f"(ftmp[8]), [dest0_v] "=&f"(ftmp[9]),
        [dest1_v] "=&f"(ftmp[10]), [dest2_v] "=&f"(ftmp[11]),
        [dest3_v] "=&f"(ftmp[12])
      : [src_argb4444] "r"(src_argb4444),
        [next_argb4444] "r"(src_stride_argb4444), [dst_u] "r"(dst_u),
        [dst_v] "r"(dst_v), [width] "r"(width), [value] "f"(value),
        [c0] "f"(c0), [c1] "f"(c1), [mask] "f"(mask), [mask_u] "f"(mask_u),
        [mask_v] "f"(mask_v), [eight] "f"(0x08), [four] "f"(0x04),
        [two] "f"(0x02)
      : "memory");
}

void ARGBToUV444Row_MMI(const uint8_t* src_argb,
                        uint8_t* dst_u,
                        uint8_t* dst_v,
                        int width) {
  uint64_t ftmp[12];
  const uint64_t value = 0x4040;
  const uint64_t mask_u = 0x0026004a00700002;
  const uint64_t mask_v = 0x00020070005e0012;

  __asm__ volatile(
      "1:                                                               \n\t"
      "gsldrc1    %[src0],         0x00(%[src_argb])                    \n\t"
      "gsldlc1    %[src0],         0x07(%[src_argb])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "dsll       %[dest0_u],      %[src_lo],         %[sixteen]        \n\t"
      "pinsrh_0   %[dest0_u],      %[dest0_u],        %[value]          \n\t"
      "pinsrh_3   %[dest0_v],      %[src_lo],         %[value]          \n\t"
      "pmaddhw    %[dest0_u],      %[dest0_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest0_v],      %[dest0_v],        %[mask_v]         \n\t"

      "dsll       %[src_lo],       %[src_hi],         %[sixteen]        \n\t"
      "pinsrh_0   %[src_lo],       %[src_lo],         %[value]          \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest0_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest0_u],        %[src_lo]         \n\t"
      "psubw      %[dest0_u],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest0_u],      %[dest0_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest0_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest0_v],        %[src_hi]         \n\t"
      "psubw      %[dest0_v],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest0_v],      %[dest0_v],        %[eight]          \n\t"

      "gsldrc1    %[src0],         0x08(%[src_argb])                    \n\t"
      "gsldlc1    %[src0],         0x0f(%[src_argb])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "dsll       %[dest1_u],      %[src_lo],         %[sixteen]        \n\t"
      "pinsrh_0   %[dest1_u],      %[dest1_u],        %[value]          \n\t"
      "pinsrh_3   %[dest1_v],      %[src_lo],         %[value]          \n\t"
      "pmaddhw    %[dest1_u],      %[dest1_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest1_v],      %[dest1_v],        %[mask_v]         \n\t"
      "dsll       %[src_lo],       %[src_hi],         %[sixteen]        \n\t"
      "pinsrh_0   %[src_lo],       %[src_lo],         %[value]          \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest1_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest1_u],        %[src_lo]         \n\t"
      "psubw      %[dest1_u],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest1_u],      %[dest1_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest1_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest1_v],        %[src_hi]         \n\t"
      "psubw      %[dest1_v],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest1_v],      %[dest1_v],        %[eight]          \n\t"

      "gsldrc1    %[src0],         0x10(%[src_argb])                    \n\t"
      "gsldlc1    %[src0],         0x17(%[src_argb])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "dsll       %[dest2_u],      %[src_lo],         %[sixteen]        \n\t"
      "pinsrh_0   %[dest2_u],      %[dest2_u],        %[value]          \n\t"
      "pinsrh_3   %[dest2_v],      %[src_lo],         %[value]          \n\t"
      "pmaddhw    %[dest2_u],      %[dest2_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest2_v],      %[dest2_v],        %[mask_v]         \n\t"
      "dsll       %[src_lo],       %[src_hi],         %[sixteen]        \n\t"
      "pinsrh_0   %[src_lo],       %[src_lo],         %[value]          \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest2_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest2_u],        %[src_lo]         \n\t"
      "psubw      %[dest2_u],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest2_u],      %[dest2_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest2_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest2_v],        %[src_hi]         \n\t"
      "psubw      %[dest2_v],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest2_v],      %[dest2_v],        %[eight]          \n\t"

      "gsldrc1    %[src0],         0x18(%[src_argb])                    \n\t"
      "gsldlc1    %[src0],         0x1f(%[src_argb])                    \n\t"
      "punpcklbh  %[src_lo],       %[src0],           %[zero]           \n\t"
      "punpckhbh  %[src_hi],       %[src0],           %[zero]           \n\t"
      "dsll       %[dest3_u],      %[src_lo],         %[sixteen]        \n\t"
      "pinsrh_0   %[dest3_u],      %[dest3_u],        %[value]          \n\t"
      "pinsrh_3   %[dest3_v],      %[src_lo],         %[value]          \n\t"
      "pmaddhw    %[dest3_u],      %[dest3_u],        %[mask_u]         \n\t"
      "pmaddhw    %[dest3_v],      %[dest3_v],        %[mask_v]         \n\t"
      "dsll       %[src_lo],       %[src_hi],         %[sixteen]        \n\t"
      "pinsrh_0   %[src_lo],       %[src_lo],         %[value]          \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[value]          \n\t"
      "pmaddhw    %[src_lo],       %[src_lo],         %[mask_u]         \n\t"
      "pmaddhw    %[src_hi],       %[src_hi],         %[mask_v]         \n\t"

      "punpcklwd  %[src0],         %[dest3_u],        %[src_lo]         \n\t"
      "punpckhwd  %[src1],         %[dest3_u],        %[src_lo]         \n\t"
      "psubw      %[dest3_u],      %[src0],           %[src1]           \n\t"
      "psraw      %[dest3_u],      %[dest3_u],        %[eight]          \n\t"
      "punpcklwd  %[src0],         %[dest3_v],        %[src_hi]         \n\t"
      "punpckhwd  %[src1],         %[dest3_v],        %[src_hi]         \n\t"
      "psubw      %[dest3_v],      %[src1],           %[src0]           \n\t"
      "psraw      %[dest3_v],      %[dest3_v],        %[eight]          \n\t"

      "packsswh   %[src0],         %[dest0_u],        %[dest1_u]        \n\t"
      "packsswh   %[src1],         %[dest2_u],        %[dest3_u]        \n\t"
      "packushb   %[dest0_u],      %[src0],           %[src1]           \n\t"
      "gssdlc1    %[dest0_u],      0x07(%[dst_u])                       \n\t"
      "gssdrc1    %[dest0_u],      0x00(%[dst_u])                       \n\t"

      "packsswh   %[src0],         %[dest0_v],        %[dest1_v]        \n\t"
      "packsswh   %[src1],         %[dest2_v],        %[dest3_v]        \n\t"
      "packushb   %[dest0_v],      %[src0],           %[src1]           \n\t"
      "gssdlc1    %[dest0_v],      0x07(%[dst_v])                       \n\t"
      "gssdrc1    %[dest0_v],      0x00(%[dst_v])                       \n\t"

      "daddiu     %[src_argb],     %[src_argb],       0x20              \n\t"
      "daddiu     %[dst_u],        %[dst_u],          0x08              \n\t"
      "daddiu     %[dst_v],        %[dst_v],          0x08              \n\t"
      "daddi      %[width],        %[width],         -0x08              \n\t"
      "bgtz       %[width],        1b                                   \n\t"
      : [src0] "=&f"(ftmp[0]), [src1] "=&f"(ftmp[1]), [src_lo] "=&f"(ftmp[2]),
        [src_hi] "=&f"(ftmp[3]), [dest0_u] "=&f"(ftmp[4]),
        [dest0_v] "=&f"(ftmp[5]), [dest1_u] "=&f"(ftmp[6]),
        [dest1_v] "=&f"(ftmp[7]), [dest2_u] "=&f"(ftmp[8]),
        [dest2_v] "=&f"(ftmp[9]), [dest3_u] "=&f"(ftmp[10]),
        [dest3_v] "=&f"(ftmp[11])
      : [src_argb] "r"(src_argb), [dst_u] "r"(dst_u), [dst_v] "r"(dst_v),
        [width] "r"(width), [mask_u] "f"(mask_u), [mask_v] "f"(mask_v),
        [value] "f"(value), [zero] "f"(0x00), [sixteen] "f"(0x10),
        [eight] "f"(0x08)
      : "memory");
}

void ARGBGrayRow_MMI(const uint8_t* src_argb, uint8_t* dst_argb, int width) {
  uint64_t src, src_lo, src_hi, src37, dest, dest_lo, dest_hi;
  uint64_t tmp0, tmp1;
  const uint64_t mask0 = 0x0;
  const uint64_t mask1 = 0x01;
  const uint64_t mask2 = 0x0080004D0096001DULL;
  const uint64_t mask3 = 0xFF000000FF000000ULL;
  const uint64_t mask4 = ~mask3;
  const uint64_t shift = 0x08;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[src],          0x07(%[src_ptr])                 \n\t"
      "gsldrc1    %[src],          0x00(%[src_ptr])                 \n\t"

      "and        %[src37],        %[src],            %[mask3]      \n\t"

      "punpcklbh  %[src_lo],       %[src],            %[mask0]      \n\t"
      "pinsrh_3   %[src_lo],       %[src_lo],         %[mask1]      \n\t"
      "pmaddhw    %[dest_lo],      %[src_lo],         %[mask2]      \n\t"
      "punpcklwd  %[tmp0],         %[dest_lo],        %[dest_lo]    \n\t"
      "punpckhwd  %[tmp1],         %[dest_lo],        %[dest_lo]    \n\t"
      "paddw      %[dest_lo],      %[tmp0],           %[tmp1]       \n\t"
      "psrlw      %[dest_lo],      %[dest_lo],        %[shift]      \n\t"
      "packsswh   %[dest_lo],      %[dest_lo],        %[dest_lo]    \n\t"

      "punpckhbh  %[src_hi],       %[src],            %[mask0]      \n\t"
      "pinsrh_3   %[src_hi],       %[src_hi],         %[mask1]      \n\t"
      "pmaddhw    %[dest_hi],      %[src_hi],         %[mask2]      \n\t"
      "punpcklwd  %[tmp0],         %[dest_hi],        %[dest_hi]    \n\t"
      "punpckhwd  %[tmp1],         %[dest_hi],        %[dest_hi]    \n\t"
      "paddw      %[dest_hi],      %[tmp0],           %[tmp1]       \n\t"
      "psrlw      %[dest_hi],      %[dest_hi],        %[shift]      \n\t"
      "packsswh   %[dest_hi],      %[dest_hi],        %[dest_hi]    \n\t"

      "packushb   %[dest],         %[dest_lo],        %[dest_hi]    \n\t"
      "and        %[dest],         %[dest],           %[mask4]      \n\t"
      "or         %[dest],         %[dest],           %[src37]      \n\t"

      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x08          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x02          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [dest_hi] "=&f"(dest_hi), [dest_lo] "=&f"(dest_lo),
        [src_hi] "=&f"(src_hi), [src_lo] "=&f"(src_lo), [tmp0] "=&f"(tmp0),
        [tmp1] "=&f"(tmp1), [src] "=&f"(src), [dest] "=&f"(dest),
        [src37] "=&f"(src37)
      : [src_ptr] "r"(src_argb), [dst_ptr] "r"(dst_argb), [width] "r"(width),
        [shift] "f"(shift), [mask0] "f"(mask0), [mask1] "f"(mask1),
        [mask2] "f"(mask2), [mask3] "f"(mask3), [mask4] "f"(mask4)
      : "memory");
}

// Convert a row of image to Sepia tone.
void ARGBSepiaRow_MMI(uint8_t* dst_argb, int width) {
  uint64_t dest, dest_lo, dest_hi, dest37, dest0, dest1, dest2;
  uint64_t tmp0, tmp1;
  const uint64_t mask0 = 0x0;
  const uint64_t mask1 = 0x002300440011ULL;
  const uint64_t mask2 = 0x002D00580016ULL;
  const uint64_t mask3 = 0x003200620018ULL;
  const uint64_t mask4 = 0xFF000000FF000000ULL;
  const uint64_t shift = 0x07;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gsldrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "and        %[dest37],       %[dest],           %[mask4]      \n\t"

      "punpcklbh  %[dest_lo],      %[dest],           %[mask0]      \n\t"
      "pmaddhw    %[dest0],        %[dest_lo],        %[mask1]      \n\t"
      "pmaddhw    %[dest1],        %[dest_lo],        %[mask2]      \n\t"
      "pmaddhw    %[dest2],        %[dest_lo],        %[mask3]      \n\t"
      "punpcklwd  %[tmp0],         %[dest0],          %[dest1]      \n\t"
      "punpckhwd  %[tmp1],         %[dest0],          %[dest1]      \n\t"
      "paddw      %[dest0],        %[tmp0],           %[tmp1]       \n\t"
      "psrlw      %[dest0],        %[dest0],          %[shift]      \n\t"
      "punpcklwd  %[tmp0],         %[dest2],          %[mask0]      \n\t"
      "punpckhwd  %[tmp1],         %[dest2],          %[mask0]      \n\t"
      "paddw      %[dest1],        %[tmp0],           %[tmp1]       \n\t"
      "psrlw      %[dest1],        %[dest1],          %[shift]      \n\t"
      "packsswh   %[dest_lo],      %[dest0],          %[dest1]      \n\t"

      "punpckhbh  %[dest_hi],      %[dest],           %[mask0]      \n\t"
      "pmaddhw    %[dest0],        %[dest_hi],        %[mask1]      \n\t"
      "pmaddhw    %[dest1],        %[dest_hi],        %[mask2]      \n\t"
      "pmaddhw    %[dest2],        %[dest_hi],        %[mask3]      \n\t"
      "punpcklwd  %[tmp0],         %[dest0],          %[dest1]      \n\t"
      "punpckhwd  %[tmp1],         %[dest0],          %[dest1]      \n\t"
      "paddw      %[dest0],        %[tmp0],           %[tmp1]       \n\t"
      "psrlw      %[dest0],        %[dest0],          %[shift]      \n\t"
      "punpcklwd  %[tmp0],         %[dest2],          %[mask0]      \n\t"
      "punpckhwd  %[tmp1],         %[dest2],          %[mask0]      \n\t"
      "paddw      %[dest1],        %[tmp0],           %[tmp1]       \n\t"
      "psrlw      %[dest1],        %[dest1],          %[shift]      \n\t"
      "packsswh   %[dest_hi],      %[dest0],          %[dest1]      \n\t"

      "packushb   %[dest],         %[dest_lo],        %[dest_hi]    \n\t"
      "or         %[dest],         %[dest],           %[dest37]     \n\t"

      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x02          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [dest_hi] "=&f"(dest_hi), [dest_lo] "=&f"(dest_lo),
        [dest0] "=&f"(dest0), [dest1] "=&f"(dest1), [dest2] "=&f"(dest2),
        [dest37] "=&f"(dest37), [tmp0] "=&f"(tmp0), [tmp1] "=&f"(tmp1),
        [dest] "=&f"(dest)
      : [dst_ptr] "r"(dst_argb), [width] "r"(width), [mask0] "f"(mask0),
        [mask1] "f"(mask1), [mask2] "f"(mask2), [mask3] "f"(mask3),
        [mask4] "f"(mask4), [shift] "f"(shift)
      : "memory");
}

// Apply color matrix to a row of image. Matrix is signed.
// TODO(fbarchard): Consider adding rounding (+32).
void ARGBColorMatrixRow_MMI(const uint8_t* src_argb,
                            uint8_t* dst_argb,
                            const int8_t* matrix_argb,
                            int width) {
  uint64_t src, src_hi, src_lo, dest, dest_lo, dest_hi, dest0, dest1, dest2,
      dest3;
  uint64_t matrix, matrix_hi, matrix_lo;
  uint64_t tmp0, tmp1;
  const uint64_t shift0 = 0x06;
  const uint64_t shift1 = 0x08;
  const uint64_t mask0 = 0x0;
  const uint64_t mask1 = 0x08;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[src],          0x07(%[src_ptr])                 \n\t"
      "gsldrc1    %[src],          0x00(%[src_ptr])                 \n\t"

      "punpcklbh  %[src_lo],       %[src],            %[mask0]      \n\t"

      "gsldlc1    %[matrix],       0x07(%[matrix_ptr])              \n\t"
      "gsldrc1    %[matrix],       0x00(%[matrix_ptr])              \n\t"
      "punpcklbh  %[matrix_lo],    %[matrix],         %[mask0]      \n\t"
      "psllh      %[matrix_lo],    %[matrix_lo],      %[shift1]     \n\t"
      "psrah      %[matrix_lo],    %[matrix_lo],      %[shift1]     \n\t"
      "punpckhbh  %[matrix_hi],    %[matrix],         %[mask0]      \n\t"
      "psllh      %[matrix_hi],    %[matrix_hi],      %[shift1]     \n\t"
      "psrah      %[matrix_hi],    %[matrix_hi],      %[shift1]     \n\t"
      "pmaddhw    %[dest_lo],      %[src_lo],         %[matrix_lo]  \n\t"
      "pmaddhw    %[dest_hi],      %[src_lo],         %[matrix_hi]  \n\t"
      "punpcklwd  %[tmp0],         %[dest_lo],        %[dest_hi]    \n\t"
      "punpckhwd  %[tmp1],         %[dest_lo],        %[dest_hi]    \n\t"
      "paddw      %[dest0],        %[tmp0],           %[tmp1]       \n\t"
      "psraw      %[dest0],        %[dest0],          %[shift0]     \n\t"

      "gsldlc1    %[matrix],       0x0f(%[matrix_ptr])              \n\t"
      "gsldrc1    %[matrix],       0x08(%[matrix_ptr])              \n\t"
      "punpcklbh  %[matrix_lo],    %[matrix],         %[mask0]      \n\t"
      "psllh      %[matrix_lo],    %[matrix_lo],      %[shift1]     \n\t"
      "psrah      %[matrix_lo],    %[matrix_lo],      %[shift1]     \n\t"
      "punpckhbh  %[matrix_hi],    %[matrix],         %[mask0]      \n\t"
      "psllh      %[matrix_hi],    %[matrix_hi],      %[shift1]     \n\t"
      "psrah      %[matrix_hi],    %[matrix_hi],      %[shift1]     \n\t"
      "pmaddhw    %[dest_lo],      %[src_lo],         %[matrix_lo]  \n\t"
      "pmaddhw    %[dest_hi],      %[src_lo],         %[matrix_hi]  \n\t"
      "punpcklwd  %[tmp0],         %[dest_lo],        %[dest_hi]    \n\t"
      "punpckhwd  %[tmp1],         %[dest_lo],        %[dest_hi]    \n\t"
      "paddw      %[dest1],        %[tmp0],           %[tmp1]       \n\t"
      "psraw      %[dest1],        %[dest1],          %[shift0]     \n\t"

      "punpckhbh  %[src_hi],       %[src],            %[mask0]      \n\t"

      "gsldlc1    %[matrix],       0x07(%[matrix_ptr])              \n\t"
      "gsldrc1    %[matrix],       0x00(%[matrix_ptr])              \n\t"
      "punpcklbh  %[matrix_lo],    %[matrix],         %[mask0]      \n\t"
      "psllh      %[matrix_lo],    %[matrix_lo],      %[shift1]     \n\t"
      "psrah      %[matrix_lo],    %[matrix_lo],      %[shift1]     \n\t"
      "punpckhbh  %[matrix_hi],    %[matrix],         %[mask0]      \n\t"
      "psllh      %[matrix_hi],    %[matrix_hi],      %[shift1]     \n\t"
      "psrah      %[matrix_hi],    %[matrix_hi],      %[shift1]     \n\t"
      "pmaddhw    %[dest_lo],      %[src_hi],         %[matrix_lo]  \n\t"
      "pmaddhw    %[dest_hi],      %[src_hi],         %[matrix_hi]  \n\t"
      "punpcklwd  %[tmp0],         %[dest_lo],        %[dest_hi]    \n\t"
      "punpckhwd  %[tmp1],         %[dest_lo],        %[dest_hi]    \n\t"
      "paddw      %[dest2],        %[tmp0],           %[tmp1]       \n\t"
      "psraw      %[dest2],        %[dest2],          %[shift0]     \n\t"

      "gsldlc1    %[matrix],       0x0f(%[matrix_ptr])              \n\t"
      "gsldrc1    %[matrix],       0x08(%[matrix_ptr])              \n\t"
      "punpcklbh  %[matrix_lo],    %[matrix],         %[mask0]      \n\t"
      "psllh      %[matrix_lo],    %[matrix_lo],      %[shift1]     \n\t"
      "psrah      %[matrix_lo],    %[matrix_lo],      %[shift1]     \n\t"
      "punpckhbh  %[matrix_hi],    %[matrix],         %[mask0]      \n\t"
      "psllh      %[matrix_hi],    %[matrix_hi],      %[shift1]     \n\t"
      "psrah      %[matrix_hi],    %[matrix_hi],      %[shift1]     \n\t"
      "pmaddhw    %[dest_lo],      %[src_hi],         %[matrix_lo]  \n\t"
      "pmaddhw    %[dest_hi],      %[src_hi],         %[matrix_hi]  \n\t"
      "punpcklwd  %[tmp0],         %[dest_lo],        %[dest_hi]    \n\t"
      "punpckhwd  %[tmp1],         %[dest_lo],        %[dest_hi]    \n\t"
      "paddw      %[dest3],        %[tmp0],           %[tmp1]       \n\t"
      "psraw      %[dest3],        %[dest3],          %[shift0]     \n\t"

      "packsswh   %[tmp0],         %[dest0],          %[dest1]      \n\t"
      "packsswh   %[tmp1],         %[dest2],          %[dest3]      \n\t"
      "packushb   %[dest],         %[tmp0],           %[tmp1]       \n\t"

      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x08          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x02          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src_hi] "=&f"(src_hi), [src_lo] "=&f"(src_lo),
        [dest_hi] "=&f"(dest_hi), [dest_lo] "=&f"(dest_lo),
        [dest0] "=&f"(dest0), [dest1] "=&f"(dest1), [dest2] "=&f"(dest2),
        [dest3] "=&f"(dest3), [src] "=&f"(src), [dest] "=&f"(dest),
        [tmp0] "=&f"(tmp0), [tmp1] "=&f"(tmp1), [matrix_hi] "=&f"(matrix_hi),
        [matrix_lo] "=&f"(matrix_lo), [matrix] "=&f"(matrix)
      : [src_ptr] "r"(src_argb), [matrix_ptr] "r"(matrix_argb),
        [dst_ptr] "r"(dst_argb), [width] "r"(width), [shift0] "f"(shift0),
        [shift1] "f"(shift1), [mask0] "f"(mask0), [mask1] "f"(mask1)
      : "memory");
}

void ARGBShadeRow_MMI(const uint8_t* src_argb,
                      uint8_t* dst_argb,
                      int width,
                      uint32_t value) {
  uint64_t src, src_hi, src_lo, dest, dest_lo, dest_hi;
  const uint64_t shift = 0x08;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[src],          0x07(%[src_ptr])                 \n\t"
      "gsldrc1    %[src],          0x00(%[src_ptr])                 \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[src]        \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[src]        \n\t"

      "punpcklbh  %[value],        %[value],          %[value]      \n\t"

      "pmulhuh    %[dest_lo],      %[src_lo],         %[value]      \n\t"
      "psrlh      %[dest_lo],      %[dest_lo],        %[shift]      \n\t"
      "pmulhuh    %[dest_hi],      %[src_hi],         %[value]      \n\t"
      "psrlh      %[dest_hi],      %[dest_hi],        %[shift]      \n\t"
      "packushb   %[dest],         %[dest_lo],        %[dest_hi]    \n\t"

      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x08          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x02          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src_hi] "=&f"(src_hi), [src_lo] "=&f"(src_lo),
        [dest_hi] "=&f"(dest_hi), [dest_lo] "=&f"(dest_lo), [src] "=&f"(src),
        [dest] "=&f"(dest)
      : [src_ptr] "r"(src_argb), [dst_ptr] "r"(dst_argb), [width] "r"(width),
        [value] "f"(value), [shift] "f"(shift)
      : "memory");
}

void ARGBMultiplyRow_MMI(const uint8_t* src_argb,
                         const uint8_t* src_argb1,
                         uint8_t* dst_argb,
                         int width) {
  uint64_t src0, src0_hi, src0_lo, src1, src1_hi, src1_lo;
  uint64_t dest, dest_lo, dest_hi;
  const uint64_t mask = 0x0;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[src0],         0x07(%[src0_ptr])                \n\t"
      "gsldrc1    %[src0],         0x00(%[src0_ptr])                \n\t"
      "punpcklbh  %[src0_lo],      %[src0],           %[src0]       \n\t"
      "punpckhbh  %[src0_hi],      %[src0],           %[src0]       \n\t"

      "gsldlc1    %[src1],         0x07(%[src1_ptr])                \n\t"
      "gsldrc1    %[src1],         0x00(%[src1_ptr])                \n\t"
      "punpcklbh  %[src1_lo],      %[src1],           %[mask]       \n\t"
      "punpckhbh  %[src1_hi],      %[src1],           %[mask]       \n\t"

      "pmulhuh    %[dest_lo],      %[src0_lo],        %[src1_lo]    \n\t"
      "pmulhuh    %[dest_hi],      %[src0_hi],        %[src1_hi]    \n\t"
      "packushb   %[dest],         %[dest_lo],        %[dest_hi]    \n\t"

      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[src0_ptr],     %[src0_ptr],       0x08          \n\t"
      "daddiu     %[src1_ptr],     %[src1_ptr],       0x08          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x02          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src0_hi] "=&f"(src0_hi), [src0_lo] "=&f"(src0_lo),
        [src1_hi] "=&f"(src1_hi), [src1_lo] "=&f"(src1_lo),
        [dest_hi] "=&f"(dest_hi), [dest_lo] "=&f"(dest_lo), [src0] "=&f"(src0),
        [src1] "=&f"(src1), [dest] "=&f"(dest)
      : [src0_ptr] "r"(src_argb), [src1_ptr] "r"(src_argb1),
        [dst_ptr] "r"(dst_argb), [width] "r"(width), [mask] "f"(mask)
      : "memory");
}

void ARGBAddRow_MMI(const uint8_t* src_argb,
                    const uint8_t* src_argb1,
                    uint8_t* dst_argb,
                    int width) {
  uint64_t src0, src1, dest;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[src0],         0x07(%[src0_ptr])                \n\t"
      "gsldrc1    %[src0],         0x00(%[src0_ptr])                \n\t"
      "gsldlc1    %[src1],         0x07(%[src1_ptr])                \n\t"
      "gsldrc1    %[src1],         0x00(%[src1_ptr])                \n\t"
      "paddusb    %[dest],         %[src0],           %[src1]       \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[src0_ptr],     %[src0_ptr],       0x08          \n\t"
      "daddiu     %[src1_ptr],     %[src1_ptr],       0x08          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x02          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src0] "=&f"(src0), [src1] "=&f"(src1), [dest] "=&f"(dest)
      : [src0_ptr] "r"(src_argb), [src1_ptr] "r"(src_argb1),
        [dst_ptr] "r"(dst_argb), [width] "r"(width)
      : "memory");
}

void ARGBSubtractRow_MMI(const uint8_t* src_argb,
                         const uint8_t* src_argb1,
                         uint8_t* dst_argb,
                         int width) {
  uint64_t src0, src1, dest;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[src0],         0x07(%[src0_ptr])                \n\t"
      "gsldrc1    %[src0],         0x00(%[src0_ptr])                \n\t"
      "gsldlc1    %[src1],         0x07(%[src1_ptr])                \n\t"
      "gsldrc1    %[src1],         0x00(%[src1_ptr])                \n\t"
      "psubusb    %[dest],         %[src0],           %[src1]       \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[src0_ptr],     %[src0_ptr],       0x08          \n\t"
      "daddiu     %[src1_ptr],     %[src1_ptr],       0x08          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x02          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src0] "=&f"(src0), [src1] "=&f"(src1), [dest] "=&f"(dest)
      : [src0_ptr] "r"(src_argb), [src1_ptr] "r"(src_argb1),
        [dst_ptr] "r"(dst_argb), [width] "r"(width)
      : "memory");
}

// Sobel functions which mimics SSSE3.
void SobelXRow_MMI(const uint8_t* src_y0,
                   const uint8_t* src_y1,
                   const uint8_t* src_y2,
                   uint8_t* dst_sobelx,
                   int width) {
  uint64_t y00 = 0, y10 = 0, y20 = 0;
  uint64_t y02 = 0, y12 = 0, y22 = 0;
  uint64_t zero = 0x0;
  uint64_t sobel = 0x0;
  __asm__ volatile(
      "1:	                                         \n\t"
      "gsldlc1   %[y00],        0x07(%[src_y0])          \n\t"  // a=src_y0[i]
      "gsldrc1   %[y00],        0x00(%[src_y0])          \n\t"
      "gsldlc1   %[y02],        0x09(%[src_y0])          \n\t"  // a_sub=src_y0[i+2]
      "gsldrc1   %[y02],        0x02(%[src_y0])          \n\t"

      "gsldlc1   %[y10],        0x07(%[src_y1])          \n\t"  // b=src_y1[i]
      "gsldrc1   %[y10],        0x00(%[src_y1])          \n\t"
      "gsldlc1   %[y12],        0x09(%[src_y1])          \n\t"  // b_sub=src_y1[i+2]
      "gsldrc1   %[y12],        0x02(%[src_y1])          \n\t"

      "gsldlc1   %[y20],        0x07(%[src_y2])          \n\t"  // c=src_y2[i]
      "gsldrc1   %[y20],        0x00(%[src_y2])          \n\t"
      "gsldlc1   %[y22],        0x09(%[src_y2])          \n\t"  // c_sub=src_y2[i+2]
      "gsldrc1   %[y22],        0x02(%[src_y2])          \n\t"

      "punpcklbh %[y00],        %[y00],          %[zero] \n\t"
      "punpcklbh %[y10],        %[y10],          %[zero] \n\t"
      "punpcklbh %[y20],        %[y20],          %[zero] \n\t"

      "punpcklbh %[y02],        %[y02],          %[zero] \n\t"
      "punpcklbh %[y12],        %[y12],          %[zero] \n\t"
      "punpcklbh %[y22],        %[y22],          %[zero] \n\t"

      "paddh     %[y00],        %[y00],          %[y10]  \n\t"  // a+b
      "paddh     %[y20],        %[y20],          %[y10]  \n\t"  // c+b
      "paddh     %[y00],        %[y00],          %[y20]  \n\t"  // a+2b+c

      "paddh     %[y02],        %[y02],          %[y12]  \n\t"  // a_sub+b_sub
      "paddh     %[y22],        %[y22],          %[y12]  \n\t"  // c_sub+b_sub
      "paddh     %[y02],        %[y02],          %[y22]  \n\t"  // a_sub+2b_sub+c_sub

      "pmaxsh    %[y10],        %[y00],          %[y02]  \n\t"
      "pminsh    %[y20],        %[y00],          %[y02]  \n\t"
      "psubh     %[sobel],      %[y10],          %[y20]  \n\t"  // Abs

      "gsldlc1   %[y00],        0x0B(%[src_y0])          \n\t"
      "gsldrc1   %[y00],        0x04(%[src_y0])          \n\t"
      "gsldlc1   %[y02],        0x0D(%[src_y0])          \n\t"
      "gsldrc1   %[y02],        0x06(%[src_y0])          \n\t"

      "gsldlc1   %[y10],        0x0B(%[src_y1])          \n\t"
      "gsldrc1   %[y10],        0x04(%[src_y1])          \n\t"
      "gsldlc1   %[y12],        0x0D(%[src_y1])          \n\t"
      "gsldrc1   %[y12],        0x06(%[src_y1])          \n\t"

      "gsldlc1   %[y20],        0x0B(%[src_y2])          \n\t"
      "gsldrc1   %[y20],        0x04(%[src_y2])          \n\t"
      "gsldlc1   %[y22],        0x0D(%[src_y2])          \n\t"
      "gsldrc1   %[y22],        0x06(%[src_y2])          \n\t"

      "punpcklbh %[y00],        %[y00],          %[zero] \n\t"
      "punpcklbh %[y10],        %[y10],          %[zero] \n\t"
      "punpcklbh %[y20],        %[y20],          %[zero] \n\t"

      "punpcklbh %[y02],        %[y02],          %[zero] \n\t"
      "punpcklbh %[y12],        %[y12],          %[zero] \n\t"
      "punpcklbh %[y22],        %[y22],          %[zero] \n\t"

      "paddh     %[y00],        %[y00],          %[y10]  \n\t"
      "paddh     %[y20],        %[y20],          %[y10]  \n\t"
      "paddh     %[y00],        %[y00],          %[y20]  \n\t"

      "paddh     %[y02],        %[y02],          %[y12]  \n\t"
      "paddh     %[y22],        %[y22],          %[y12]  \n\t"
      "paddh     %[y02],        %[y02],          %[y22]  \n\t"

      "pmaxsh    %[y10],        %[y00],          %[y02]  \n\t"
      "pminsh    %[y20],        %[y00],          %[y02]  \n\t"
      "psubh     %[y00],        %[y10],          %[y20]  \n\t"

      "packushb  %[sobel],      %[sobel],        %[y00]  \n\t"  // clamp255
      "gssdrc1   %[sobel],      0(%[dst_sobelx])         \n\t"
      "gssdlc1   %[sobel],      7(%[dst_sobelx])         \n\t"

      "daddiu    %[src_y0],     %[src_y0],      8        \n\t"
      "daddiu    %[src_y1],     %[src_y1],      8        \n\t"
      "daddiu    %[src_y2],     %[src_y2],      8        \n\t"
      "daddiu    %[dst_sobelx], %[dst_sobelx],  8        \n\t"
      "daddiu    %[width],      %[width],      -8        \n\t"
      "bgtz      %[width],      1b                       \n\t"
      "nop                                               \n\t"
      : [sobel] "=&f"(sobel), [y00] "=&f"(y00), [y10] "=&f"(y10),
        [y20] "=&f"(y20), [y02] "=&f"(y02), [y12] "=&f"(y12), [y22] "=&f"(y22)
      : [src_y0] "r"(src_y0), [src_y1] "r"(src_y1), [src_y2] "r"(src_y2),
        [dst_sobelx] "r"(dst_sobelx), [width] "r"(width), [zero] "f"(zero)
      : "memory");
}

void SobelYRow_MMI(const uint8_t* src_y0,
                   const uint8_t* src_y1,
                   uint8_t* dst_sobely,
                   int width) {
  uint64_t y00 = 0, y01 = 0, y02 = 0;
  uint64_t y10 = 0, y11 = 0, y12 = 0;
  uint64_t zero = 0x0;
  uint64_t sobel = 0x0;
  __asm__ volatile(
      "1:	                                        \n\t"
      "gsldlc1   %[y00],        0x07(%[src_y0])         \n\t"  // a=src_y0[i]
      "gsldrc1   %[y00],        0x00(%[src_y0])         \n\t"
      "gsldlc1   %[y01],        0x08(%[src_y0])         \n\t"  // b=src_y0[i+1]
      "gsldrc1   %[y01],        0x01(%[src_y0])         \n\t"
      "gsldlc1   %[y02],        0x09(%[src_y0])         \n\t"  // c=src_y0[i+2]
      "gsldrc1   %[y02],        0x02(%[src_y0])         \n\t"

      "gsldlc1   %[y10],        0x07(%[src_y1])         \n\t"  // a_sub=src_y1[i]
      "gsldrc1   %[y10],        0x00(%[src_y1])         \n\t"
      "gsldlc1   %[y11],        0x08(%[src_y1])         \n\t"  // b_sub=src_y1[i+1]
      "gsldrc1   %[y11],        0x01(%[src_y1])         \n\t"
      "gsldlc1   %[y12],        0x09(%[src_y1])         \n\t"  // c_sub=src_y1[i+2]
      "gsldrc1   %[y12],        0x02(%[src_y1])         \n\t"

      "punpcklbh %[y00],        %[y00],         %[zero] \n\t"
      "punpcklbh %[y01],        %[y01],         %[zero] \n\t"
      "punpcklbh %[y02],        %[y02],         %[zero] \n\t"

      "punpcklbh %[y10],        %[y10],         %[zero] \n\t"
      "punpcklbh %[y11],        %[y11],         %[zero] \n\t"
      "punpcklbh %[y12],        %[y12],         %[zero] \n\t"

      "paddh     %[y00],        %[y00],         %[y01]  \n\t"  // a+b
      "paddh     %[y02],        %[y02],         %[y01]  \n\t"  // c+b
      "paddh     %[y00],        %[y00],         %[y02]  \n\t"  // a+2b+c

      "paddh     %[y10],        %[y10],         %[y11]  \n\t"  // a_sub+b_sub
      "paddh     %[y12],        %[y12],         %[y11]  \n\t"  // c_sub+b_sub
      "paddh     %[y10],        %[y10],         %[y12]  \n\t"  // a_sub+2b_sub+c_sub

      "pmaxsh    %[y02],        %[y00],         %[y10]  \n\t"
      "pminsh    %[y12],        %[y00],         %[y10]  \n\t"
      "psubh     %[sobel],      %[y02],         %[y12]  \n\t"  // Abs

      "gsldlc1   %[y00],        0x0B(%[src_y0])         \n\t"
      "gsldrc1   %[y00],        0x04(%[src_y0])         \n\t"
      "gsldlc1   %[y01],        0x0C(%[src_y0])         \n\t"
      "gsldrc1   %[y01],        0x05(%[src_y0])         \n\t"
      "gsldlc1   %[y02],        0x0D(%[src_y0])         \n\t"
      "gsldrc1   %[y02],        0x06(%[src_y0])         \n\t"

      "gsldlc1   %[y10],        0x0B(%[src_y1])         \n\t"
      "gsldrc1   %[y10],        0x04(%[src_y1])         \n\t"
      "gsldlc1   %[y11],        0x0C(%[src_y1])         \n\t"
      "gsldrc1   %[y11],        0x05(%[src_y1])         \n\t"
      "gsldlc1   %[y12],        0x0D(%[src_y1])         \n\t"
      "gsldrc1   %[y12],        0x06(%[src_y1])         \n\t"

      "punpcklbh %[y00],        %[y00],         %[zero] \n\t"
      "punpcklbh %[y01],        %[y01],         %[zero] \n\t"
      "punpcklbh %[y02],        %[y02],         %[zero] \n\t"

      "punpcklbh %[y10],        %[y10],         %[zero] \n\t"
      "punpcklbh %[y11],        %[y11],         %[zero] \n\t"
      "punpcklbh %[y12],        %[y12],         %[zero] \n\t"

      "paddh     %[y00],        %[y00],         %[y01]  \n\t"
      "paddh     %[y02],        %[y02],         %[y01]  \n\t"
      "paddh     %[y00],        %[y00],         %[y02]  \n\t"

      "paddh     %[y10],        %[y10],         %[y11]  \n\t"
      "paddh     %[y12],        %[y12],         %[y11]  \n\t"
      "paddh     %[y10],        %[y10],         %[y12]  \n\t"

      "pmaxsh    %[y02],        %[y00],         %[y10]  \n\t"
      "pminsh    %[y12],        %[y00],         %[y10]  \n\t"
      "psubh     %[y00],        %[y02],         %[y12]  \n\t"

      "packushb  %[sobel],      %[sobel],       %[y00]  \n\t"  // clamp255
      "gssdrc1   %[sobel],      0(%[dst_sobely])        \n\t"
      "gssdlc1   %[sobel],      7(%[dst_sobely])        \n\t"

      "daddiu    %[src_y0],     %[src_y0],      8       \n\t"
      "daddiu    %[src_y1],     %[src_y1],      8       \n\t"
      "daddiu    %[dst_sobely], %[dst_sobely],  8       \n\t"
      "daddiu    %[width],      %[width],      -8       \n\t"
      "bgtz      %[width],      1b                      \n\t"
      "nop                                              \n\t"
      : [sobel] "=&f"(sobel), [y00] "=&f"(y00), [y01] "=&f"(y01),
        [y02] "=&f"(y02), [y10] "=&f"(y10), [y11] "=&f"(y11), [y12] "=&f"(y12)
      : [src_y0] "r"(src_y0), [src_y1] "r"(src_y1),
        [dst_sobely] "r"(dst_sobely), [width] "r"(width), [zero] "f"(zero)
      : "memory");
}

void SobelRow_MMI(const uint8_t* src_sobelx,
                  const uint8_t* src_sobely,
                  uint8_t* dst_argb,
                  int width) {
  double temp[3];
  uint64_t c1 = 0xff000000ff000000;
  __asm__ volatile(
      "1:	                                          \n\t"
      "gsldlc1   %[t0],         0x07(%[src_sobelx])       \n\t"  // a=src_sobelx[i]
      "gsldrc1   %[t0],         0x00(%[src_sobelx])       \n\t"
      "gsldlc1   %[t1],         0x07(%[src_sobely])       \n\t"  // b=src_sobely[i]
      "gsldrc1   %[t1],         0x00(%[src_sobely])       \n\t"
      // s7 s6 s5 s4 s3 s2 s1 s0 = a+b
      "paddusb   %[t2] ,        %[t0],              %[t1] \n\t"

      // s3 s2 s1 s0->s3 s3 s2 s2 s1 s1 s0 s0
      "punpcklbh %[t0],         %[t2],              %[t2] \n\t"

      // s1 s1 s0 s0->s1 s2 s1 s1 s0 s0 s0 s0
      "punpcklbh %[t1],         %[t0],              %[t0] \n\t"
      "or        %[t1],         %[t1],              %[c1] \n\t"
      // 255 s1 s1 s1 s55 s0 s0 s0
      "gssdrc1   %[t1],         0x00(%[dst_argb])	  \n\t"
      "gssdlc1   %[t1],         0x07(%[dst_argb])         \n\t"

      // s3 s3 s2 s2->s3 s3 s3 s3 s2 s2 s2 s2
      "punpckhbh %[t1],         %[t0],              %[t0] \n\t"
      "or        %[t1],         %[t1],              %[c1] \n\t"
      // 255 s3 s3 s3 255 s2 s2 s2
      "gssdrc1   %[t1],         0x08(%[dst_argb])	  \n\t"
      "gssdlc1   %[t1],         0x0f(%[dst_argb])         \n\t"

      // s7 s6 s5 s4->s7 s7 s6 s6 s5 s5 s4 s4
      "punpckhbh %[t0],         %[t2],              %[t2] \n\t"

      // s5 s5 s4 s4->s5 s5 s5 s5 s4 s4 s4 s4
      "punpcklbh %[t1],         %[t0],              %[t0] \n\t"
      "or        %[t1],         %[t1],              %[c1] \n\t"
      "gssdrc1   %[t1],         0x10(%[dst_argb])	  \n\t"
      "gssdlc1   %[t1],         0x17(%[dst_argb])         \n\t"

      // s7 s7 s6 s6->s7 s7 s7 s7 s6 s6 s6 s6
      "punpckhbh %[t1],         %[t0],              %[t0] \n\t"
      "or        %[t1],         %[t1],              %[c1] \n\t"
      "gssdrc1   %[t1],         0x18(%[dst_argb])	  \n\t"
      "gssdlc1   %[t1],         0x1f(%[dst_argb])         \n\t"

      "daddiu    %[dst_argb],   %[dst_argb],        32    \n\t"
      "daddiu    %[src_sobelx], %[src_sobelx],      8     \n\t"
      "daddiu    %[src_sobely], %[src_sobely],      8     \n\t"
      "daddiu    %[width],      %[width],          -8     \n\t"
      "bgtz      %[width],      1b                        \n\t"
      "nop                                                \n\t"
      : [t0] "=&f"(temp[0]), [t1] "=&f"(temp[1]), [t2] "=&f"(temp[2])
      : [src_sobelx] "r"(src_sobelx), [src_sobely] "r"(src_sobely),
        [dst_argb] "r"(dst_argb), [width] "r"(width), [c1] "f"(c1)
      : "memory");
}

void SobelToPlaneRow_MMI(const uint8_t* src_sobelx,
                         const uint8_t* src_sobely,
                         uint8_t* dst_y,
                         int width) {
  uint64_t tr = 0;
  uint64_t tb = 0;
  __asm__ volatile(
      "1:	                                       \n\t"
      "gsldrc1 %[tr],         0x0(%[src_sobelx])       \n\t"
      "gsldlc1 %[tr],         0x7(%[src_sobelx])       \n\t"  // r=src_sobelx[i]
      "gsldrc1 %[tb],         0x0(%[src_sobely])       \n\t"
      "gsldlc1 %[tb],         0x7(%[src_sobely])       \n\t"  // b=src_sobely[i]
      "paddusb %[tr],         %[tr],             %[tb] \n\t"  // g
      "gssdrc1 %[tr],         0x0(%[dst_y])	       \n\t"
      "gssdlc1 %[tr],         0x7(%[dst_y])            \n\t"

      "daddiu  %[dst_y],      %[dst_y],          8     \n\t"
      "daddiu  %[src_sobelx], %[src_sobelx],     8     \n\t"
      "daddiu  %[src_sobely], %[src_sobely],     8     \n\t"
      "daddiu  %[width],      %[width],         -8     \n\t"
      "bgtz    %[width],      1b                       \n\t"
      "nop                                             \n\t"
      : [tr] "=&f"(tr), [tb] "=&f"(tb)
      : [src_sobelx] "r"(src_sobelx), [src_sobely] "r"(src_sobely),
        [dst_y] "r"(dst_y), [width] "r"(width)
      : "memory");
}

void SobelXYRow_MMI(const uint8_t* src_sobelx,
                    const uint8_t* src_sobely,
                    uint8_t* dst_argb,
                    int width) {
  uint64_t temp[3];
  uint64_t result = 0;
  uint64_t gb = 0;
  uint64_t cr = 0;
  uint64_t c1 = 0xffffffffffffffff;
  __asm__ volatile(
      "1:	                                          \n\t"
      "gsldlc1   %[tr],         0x07(%[src_sobelx])       \n\t"  // r=src_sobelx[i]
      "gsldrc1   %[tr],         0x00(%[src_sobelx])       \n\t"
      "gsldlc1   %[tb],         0x07(%[src_sobely])       \n\t"  // b=src_sobely[i]
      "gsldrc1   %[tb],         0x00(%[src_sobely])       \n\t"
      "paddusb   %[tg] ,        %[tr],              %[tb] \n\t"  // g

      // g3 b3 g2 b2 g1 b1 g0 b0
      "punpcklbh %[gb],         %[tb],              %[tg] \n\t"
      // c3 r3 r2 r2 c1 r1 c0 r0
      "punpcklbh %[cr],         %[tr],              %[c1] \n\t"
      // c1 r1 g1 b1 c0 r0 g0 b0
      "punpcklhw %[result],     %[gb],              %[cr] \n\t"
      "gssdrc1   %[result],     0x00(%[dst_argb])	  \n\t"
      "gssdlc1   %[result],     0x07(%[dst_argb])         \n\t"
      // c3 r3 g3 b3 c2 r2 g2 b2
      "punpckhhw %[result],     %[gb],              %[cr] \n\t"
      "gssdrc1   %[result],     0x08(%[dst_argb])	  \n\t"
      "gssdlc1   %[result],     0x0f(%[dst_argb])         \n\t"

      // g7 b7 g6 b6 g5 b5 g4 b4
      "punpckhbh %[gb],         %[tb],              %[tg] \n\t"
      // c7 r7 c6 r6 c5 r5 c4 r4
      "punpckhbh %[cr],         %[tr],              %[c1] \n\t"
      // c5 r5 g5 b5 c4 r4 g4 b4
      "punpcklhw %[result],     %[gb],              %[cr] \n\t"
      "gssdrc1   %[result],     0x10(%[dst_argb])	  \n\t"
      "gssdlc1   %[result],     0x17(%[dst_argb])         \n\t"
      // c7 r7 g7 b7 c6 r6 g6 b6
      "punpckhhw %[result],     %[gb],              %[cr] \n\t"
      "gssdrc1   %[result],     0x18(%[dst_argb])	  \n\t"
      "gssdlc1   %[result],     0x1f(%[dst_argb])         \n\t"

      "daddiu    %[dst_argb],   %[dst_argb],        32    \n\t"
      "daddiu    %[src_sobelx], %[src_sobelx],      8     \n\t"
      "daddiu    %[src_sobely], %[src_sobely],      8     \n\t"
      "daddiu    %[width],      %[width],          -8     \n\t"
      "bgtz      %[width],      1b                        \n\t"
      "nop                                                \n\t"
      : [tr] "=&f"(temp[0]), [tb] "=&f"(temp[1]), [tg] "=&f"(temp[2]),
        [gb] "=&f"(gb), [cr] "=&f"(cr), [result] "=&f"(result)
      : [src_sobelx] "r"(src_sobelx), [src_sobely] "r"(src_sobely),
        [dst_argb] "r"(dst_argb), [width] "r"(width), [c1] "f"(c1)
      : "memory");
}

void J400ToARGBRow_MMI(const uint8_t* src_y, uint8_t* dst_argb, int width) {
  // Copy a Y to RGB.
  uint64_t src, dest;
  const uint64_t mask0 = 0x00ffffff00ffffffULL;
  const uint64_t mask1 = ~mask0;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gslwlc1    %[src],          0x03(%[src_ptr])                 \n\t"
      "gslwrc1    %[src],          0x00(%[src_ptr])                 \n\t"
      "punpcklbh  %[src],          %[src],            %[src]        \n\t"
      "punpcklhw  %[dest],         %[src],            %[src]        \n\t"
      "and        %[dest],         %[dest],           %[mask0]      \n\t"
      "or         %[dest],         %[dest],           %[mask1]      \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"

      "punpckhhw  %[dest],         %[src],            %[src]        \n\t"
      "and        %[dest],         %[dest],           %[mask0]      \n\t"
      "or         %[dest],         %[dest],           %[mask1]      \n\t"
      "gssdrc1    %[dest],         0x08(%[dst_ptr])                 \n\t"
      "gssdlc1    %[dest],         0x0f(%[dst_ptr])                 \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x04          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x10          \n\t"
      "daddi      %[width],        %[width],         -0x04          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src] "=&f"(src), [dest] "=&f"(dest)
      : [src_ptr] "r"(src_y), [dst_ptr] "r"(dst_argb), [mask0] "f"(mask0),
        [mask1] "f"(mask1), [width] "r"(width)
      : "memory");
}

// TODO - respect YuvConstants
void I400ToARGBRow_MMI(const uint8_t* src_y, uint8_t* rgb_buf,
                       const struct YuvConstants*, int width) {
  uint64_t src, src_lo, src_hi, dest, dest_lo, dest_hi;
  const uint64_t mask0 = 0x0;
  const uint64_t mask1 = 0x55;
  const uint64_t mask2 = 0xAA;
  const uint64_t mask3 = 0xFF;
  const uint64_t mask4 = 0x4A354A354A354A35ULL;
  const uint64_t mask5 = 0x0488048804880488ULL;
  const uint64_t shift0 = 0x08;
  const uint64_t shift1 = 0x06;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[src],          0x07(%[src_ptr])                 \n\t"
      "gsldrc1    %[src],          0x00(%[src_ptr])                 \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[mask0]      \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[mask0]      \n\t"

      "pshufh     %[src],          %[src_lo],         %[mask0]      \n\t"
      "psllh      %[dest_lo],      %[src],            %[shift0]     \n\t"
      "paddush    %[dest_lo],      %[dest_lo],        %[src]        \n\t"
      "pmulhuh    %[dest_lo],      %[dest_lo],        %[mask4]      \n\t"
      "psubh      %[dest_lo],      %[dest_lo],        %[mask5]      \n\t"
      "psrah      %[dest_lo],      %[dest_lo],        %[shift1]     \n\t"
      "pinsrh_3   %[dest_lo],      %[dest_lo],        %[mask3]      \n\t"
      "pshufh     %[src],          %[src_lo],         %[mask1]      \n\t"
      "psllh      %[dest_hi],      %[src],            %[shift0]     \n\t"
      "paddush    %[dest_hi],      %[dest_hi],        %[src]        \n\t"
      "pmulhuh    %[dest_hi],      %[dest_hi],        %[mask4]      \n\t"
      "psubh      %[dest_hi],      %[dest_hi],        %[mask5]      \n\t"
      "psrah      %[dest_hi],      %[dest_hi],        %[shift1]     \n\t"
      "pinsrh_3   %[dest_hi],      %[dest_hi],        %[mask3]      \n\t"
      "packushb   %[dest],         %[dest_lo],        %[dest_hi]    \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "pshufh     %[src],          %[src_lo],         %[mask2]      \n\t"
      "psllh      %[dest_lo],      %[src],            %[shift0]     \n\t"
      "paddush    %[dest_lo],      %[dest_lo],        %[src]        \n\t"
      "pmulhuh    %[dest_lo],      %[dest_lo],        %[mask4]      \n\t"
      "psubh      %[dest_lo],      %[dest_lo],        %[mask5]      \n\t"
      "psrah      %[dest_lo],      %[dest_lo],        %[shift1]     \n\t"
      "pinsrh_3   %[dest_lo],      %[dest_lo],        %[mask3]      \n\t"
      "pshufh     %[src],          %[src_lo],         %[mask3]      \n\t"
      "psllh      %[dest_hi],      %[src],            %[shift0]     \n\t"
      "paddush    %[dest_hi],      %[dest_hi],        %[src]        \n\t"
      "pmulhuh    %[dest_hi],      %[dest_hi],        %[mask4]      \n\t"
      "psubh      %[dest_hi],      %[dest_hi],        %[mask5]      \n\t"
      "psrah      %[dest_hi],      %[dest_hi],        %[shift1]     \n\t"
      "pinsrh_3   %[dest_hi],      %[dest_hi],        %[mask3]      \n\t"
      "packushb   %[dest],         %[dest_lo],        %[dest_hi]    \n\t"
      "gssdlc1    %[dest],         0x0f(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x08(%[dst_ptr])                 \n\t"

      "pshufh     %[src],          %[src_hi],         %[mask0]      \n\t"
      "psllh      %[dest_lo],      %[src],            %[shift0]     \n\t"
      "paddush    %[dest_lo],      %[dest_lo],        %[src]        \n\t"
      "pmulhuh    %[dest_lo],      %[dest_lo],        %[mask4]      \n\t"
      "psubh      %[dest_lo],      %[dest_lo],        %[mask5]      \n\t"
      "psrah      %[dest_lo],      %[dest_lo],        %[shift1]     \n\t"
      "pinsrh_3   %[dest_lo],      %[dest_lo],        %[mask3]      \n\t"
      "pshufh     %[src],          %[src_hi],         %[mask1]      \n\t"
      "psllh      %[dest_hi],      %[src],            %[shift0]     \n\t"
      "paddush    %[dest_hi],      %[dest_hi],        %[src]        \n\t"
      "pmulhuh    %[dest_hi],      %[dest_hi],        %[mask4]      \n\t"
      "psubh      %[dest_hi],      %[dest_hi],        %[mask5]      \n\t"
      "psrah      %[dest_hi],      %[dest_hi],        %[shift1]     \n\t"
      "pinsrh_3   %[dest_hi],      %[dest_hi],        %[mask3]      \n\t"
      "packushb   %[dest],         %[dest_lo],        %[dest_hi]    \n\t"
      "gssdlc1    %[dest],         0x17(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x10(%[dst_ptr])                 \n\t"

      "pshufh     %[src],          %[src_hi],         %[mask2]      \n\t"
      "psllh      %[dest_lo],      %[src],            %[shift0]     \n\t"
      "paddush    %[dest_lo],      %[dest_lo],        %[src]        \n\t"
      "pmulhuh    %[dest_lo],      %[dest_lo],        %[mask4]      \n\t"
      "psubh      %[dest_lo],      %[dest_lo],        %[mask5]      \n\t"
      "psrah      %[dest_lo],      %[dest_lo],        %[shift1]     \n\t"
      "pinsrh_3   %[dest_lo],      %[dest_lo],        %[mask3]      \n\t"
      "pshufh     %[src],          %[src_hi],         %[mask3]      \n\t"
      "psllh      %[dest_hi],      %[src],            %[shift0]     \n\t"
      "paddush    %[dest_hi],      %[dest_hi],        %[src]        \n\t"
      "pmulhuh    %[dest_hi],      %[dest_hi],        %[mask4]      \n\t"
      "psubh      %[dest_hi],      %[dest_hi],        %[mask5]      \n\t"
      "psrah      %[dest_hi],      %[dest_hi],        %[shift1]     \n\t"
      "pinsrh_3   %[dest_hi],      %[dest_hi],        %[mask3]      \n\t"
      "packushb   %[dest],         %[dest_lo],        %[dest_hi]    \n\t"
      "gssdlc1    %[dest],         0x1f(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x18(%[dst_ptr])                 \n\t"

      "daddi      %[src_ptr],      %[src_ptr],        0x08          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x20          \n\t"
      "daddi      %[width],        %[width],         -0x08          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src] "=&f"(src), [dest] "=&f"(dest), [src_hi] "=&f"(src_hi),
        [src_lo] "=&f"(src_lo), [dest_hi] "=&f"(dest_hi),
        [dest_lo] "=&f"(dest_lo)
      : [src_ptr] "r"(src_y), [dst_ptr] "r"(rgb_buf), [mask0] "f"(mask0),
        [mask1] "f"(mask1), [mask2] "f"(mask2), [mask3] "f"(mask3),
        [mask4] "f"(mask4), [mask5] "f"(mask5), [shift0] "f"(shift0),
        [shift1] "f"(shift1), [width] "r"(width)
      : "memory");
}

void MirrorRow_MMI(const uint8_t* src, uint8_t* dst, int width) {
  uint64_t source, src0, src1, dest;
  const uint64_t mask0 = 0x0;
  const uint64_t mask1 = 0x1b;

  src += width - 1;
  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[source],       0(%[src_ptr])                    \n\t"
      "gsldrc1    %[source],       -7(%[src_ptr])                   \n\t"
      "punpcklbh  %[src0],         %[source],         %[mask0]      \n\t"
      "pshufh     %[src0],         %[src0],           %[mask1]      \n\t"
      "punpckhbh  %[src1],         %[source],         %[mask0]      \n\t"
      "pshufh     %[src1],         %[src1],           %[mask1]      \n\t"
      "packushb   %[dest],         %[src1],           %[src0]       \n\t"

      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddi      %[src_ptr],      %[src_ptr],       -0x08          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x08          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [source] "=&f"(source), [dest] "=&f"(dest), [src0] "=&f"(src0),
        [src1] "=&f"(src1)
      : [src_ptr] "r"(src), [dst_ptr] "r"(dst), [mask0] "f"(mask0),
        [mask1] "f"(mask1), [width] "r"(width)
      : "memory");
}

void MirrorSplitUVRow_MMI(const uint8_t* src_uv,
                          uint8_t* dst_u,
                          uint8_t* dst_v,
                          int width) {
  uint64_t src0, src1, dest0, dest1;
  const uint64_t mask0 = 0x00ff00ff00ff00ffULL;
  const uint64_t mask1 = 0x1b;
  const uint64_t shift = 0x08;

  src_uv += (width - 1) << 1;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[src0],         1(%[src_ptr])                    \n\t"
      "gsldrc1    %[src0],         -6(%[src_ptr])                   \n\t"
      "gsldlc1    %[src1],         -7(%[src_ptr])                   \n\t"
      "gsldrc1    %[src1],         -14(%[src_ptr])                  \n\t"

      "and        %[dest0],        %[src0],           %[mask0]      \n\t"
      "pshufh     %[dest0],        %[dest0],          %[mask1]      \n\t"
      "and        %[dest1],        %[src1],           %[mask0]      \n\t"
      "pshufh     %[dest1],        %[dest1],          %[mask1]      \n\t"
      "packushb   %[dest0],        %[dest0],          %[dest1]      \n\t"
      "gssdlc1    %[dest0],        0x07(%[dstu_ptr])                \n\t"
      "gssdrc1    %[dest0],        0x00(%[dstu_ptr])                \n\t"

      "psrlh      %[dest0],        %[src0],           %[shift]      \n\t"
      "pshufh     %[dest0],        %[dest0],          %[mask1]      \n\t"
      "psrlh      %[dest1],        %[src1],           %[shift]      \n\t"
      "pshufh     %[dest1],        %[dest1],          %[mask1]      \n\t"
      "packushb   %[dest0],        %[dest0],          %[dest1]      \n\t"
      "gssdlc1    %[dest0],        0x07(%[dstv_ptr])                \n\t"
      "gssdrc1    %[dest0],        0x00(%[dstv_ptr])                \n\t"

      "daddi      %[src_ptr],      %[src_ptr],       -0x10          \n\t"
      "daddiu     %[dstu_ptr],     %[dstu_ptr],       0x08          \n\t"
      "daddiu     %[dstv_ptr],     %[dstv_ptr],       0x08          \n\t"
      "daddi      %[width],        %[width],         -0x08          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [dest0] "=&f"(dest0), [dest1] "=&f"(dest1), [src0] "=&f"(src0),
        [src1] "=&f"(src1)
      : [src_ptr] "r"(src_uv), [dstu_ptr] "r"(dst_u), [dstv_ptr] "r"(dst_v),
        [width] "r"(width), [mask0] "f"(mask0), [mask1] "f"(mask1),
        [shift] "f"(shift)
      : "memory");
}

void ARGBMirrorRow_MMI(const uint8_t* src, uint8_t* dst, int width) {
  src += (width - 1) * 4;
  uint64_t temp = 0x0;
  uint64_t shuff = 0x4e;  // 01 00 11 10
  __asm__ volatile(
      "1:                                      \n\t"
      "gsldlc1 %[temp],  3(%[src])     	       \n\t"
      "gsldrc1 %[temp], -4(%[src])     	       \n\t"
      "pshufh  %[temp],  %[temp],    %[shuff]  \n\t"
      "gssdrc1 %[temp],  0x0(%[dst])           \n\t"
      "gssdlc1 %[temp],  0x7(%[dst])           \n\t"

      "daddiu  %[src],   %[src],    -0x08      \n\t"
      "daddiu  %[dst],   %[dst],     0x08      \n\t"
      "daddiu  %[width], %[width],  -0x02      \n\t"
      "bnez    %[width], 1b                    \n\t"
      : [temp] "=&f"(temp)
      : [src] "r"(src), [dst] "r"(dst), [width] "r"(width), [shuff] "f"(shuff)
      : "memory");
}

void SplitUVRow_MMI(const uint8_t* src_uv,
                    uint8_t* dst_u,
                    uint8_t* dst_v,
                    int width) {
  uint64_t c0 = 0x00ff00ff00ff00ff;
  uint64_t temp[4];
  uint64_t shift = 0x08;
  __asm__ volatile(
      "1:	                                    \n\t"
      "gsldrc1  %[t0],     0x00(%[src_uv])          \n\t"
      "gsldlc1  %[t0],     0x07(%[src_uv])          \n\t"
      "gsldrc1  %[t1],     0x08(%[src_uv])          \n\t"
      "gsldlc1  %[t1],     0x0f(%[src_uv])          \n\t"

      "and      %[t2],     %[t0],          %[c0]    \n\t"
      "and      %[t3],     %[t1],          %[c0]    \n\t"
      "packushb %[t2],     %[t2],          %[t3]    \n\t"
      "gssdrc1  %[t2],     0x0(%[dst_u])	    \n\t"
      "gssdlc1  %[t2],     0x7(%[dst_u])            \n\t"

      "psrlh    %[t2],     %[t0],          %[shift] \n\t"
      "psrlh    %[t3],     %[t1],          %[shift] \n\t"
      "packushb %[t2],     %[t2],          %[t3]    \n\t"
      "gssdrc1  %[t2],     0x0(%[dst_v])            \n\t"
      "gssdlc1  %[t2],     0x7(%[dst_v])            \n\t"

      "daddiu   %[src_uv], %[src_uv],      16       \n\t"
      "daddiu   %[dst_u],  %[dst_u],       8        \n\t"
      "daddiu   %[dst_v],  %[dst_v],       8        \n\t"
      "daddiu   %[width],  %[width],      -8        \n\t"
      "bgtz     %[width],  1b                       \n\t"
      "nop                                          \n\t"
      : [t0] "=&f"(temp[0]), [t1] "=&f"(temp[1]), [t2] "=&f"(temp[2]),
        [t3] "=&f"(temp[3])
      : [src_uv] "r"(src_uv), [dst_u] "r"(dst_u), [dst_v] "r"(dst_v),
        [width] "r"(width), [c0] "f"(c0), [shift] "f"(shift)
      : "memory");
}

void MergeUVRow_MMI(const uint8_t* src_u,
                    const uint8_t* src_v,
                    uint8_t* dst_uv,
                    int width) {
  uint64_t temp[3];
  __asm__ volatile(
      "1:	                                 \n\t"
      "gsldrc1   %[t0],     0x0(%[src_u])        \n\t"
      "gsldlc1   %[t0],     0x7(%[src_u])        \n\t"
      "gsldrc1   %[t1],     0x0(%[src_v])        \n\t"
      "gsldlc1   %[t1],     0x7(%[src_v])        \n\t"
      "punpcklbh %[t2],     %[t0],         %[t1] \n\t"
      "gssdrc1   %[t2],     0x0(%[dst_uv])	 \n\t"
      "gssdlc1   %[t2],     0x7(%[dst_uv])       \n\t"
      "punpckhbh %[t2],     %[t0],         %[t1] \n\t"
      "gssdrc1   %[t2],     0x8(%[dst_uv])	 \n\t"
      "gssdlc1   %[t2],     0xf(%[dst_uv])       \n\t"

      "daddiu    %[src_u],  %[src_u],      8     \n\t"
      "daddiu    %[src_v],  %[src_v],      8     \n\t"
      "daddiu    %[dst_uv], %[dst_uv],     16    \n\t"
      "daddiu    %[width],  %[width],     -8     \n\t"
      "bgtz      %[width],  1b                   \n\t"
      "nop                                       \n\t"
      : [t0] "=&f"(temp[0]), [t1] "=&f"(temp[1]), [t2] "=&f"(temp[2])
      : [dst_uv] "r"(dst_uv), [src_u] "r"(src_u), [src_v] "r"(src_v),
        [width] "r"(width)
      : "memory");
}

void SplitRGBRow_MMI(const uint8_t* src_rgb,
                     uint8_t* dst_r,
                     uint8_t* dst_g,
                     uint8_t* dst_b,
                     int width) {
  uint64_t src[4];
  uint64_t dest_hi, dest_lo, dest;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gslwlc1    %[src0],         0x03(%[src_ptr])                 \n\t"
      "gslwrc1    %[src0],         0x00(%[src_ptr])                 \n\t"
      "gslwlc1    %[src1],         0x06(%[src_ptr])                 \n\t"
      "gslwrc1    %[src1],         0x03(%[src_ptr])                 \n\t"
      "punpcklbh  %[dest_lo],      %[src0],           %[src1]       \n\t"
      "gslwlc1    %[src2],         0x09(%[src_ptr])                 \n\t"
      "gslwrc1    %[src2],         0x06(%[src_ptr])                 \n\t"
      "gslwlc1    %[src3],         0x0c(%[src_ptr])                 \n\t"
      "gslwrc1    %[src3],         0x09(%[src_ptr])                 \n\t"
      "punpcklbh  %[dest_hi],      %[src2],           %[src3]       \n\t"

      "punpcklhw  %[dest],         %[dest_lo],        %[dest_hi]    \n\t"
      "gsswlc1    %[dest],         0x03(%[dstr_ptr])                \n\t"
      "gsswrc1    %[dest],         0x00(%[dstr_ptr])                \n\t"
      "punpckhwd  %[dest],         %[dest],           %[dest]       \n\t"
      "gsswlc1    %[dest],         0x03(%[dstg_ptr])                \n\t"
      "gsswrc1    %[dest],         0x00(%[dstg_ptr])                \n\t"
      "punpckhhw  %[dest],         %[dest_lo],        %[dest_hi]    \n\t"
      "gsswlc1    %[dest],         0x03(%[dstb_ptr])                \n\t"
      "gsswrc1    %[dest],         0x00(%[dstb_ptr])                \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x0c          \n\t"
      "daddiu     %[dstr_ptr],     %[dstr_ptr],       0x04          \n\t"
      "daddiu     %[dstg_ptr],     %[dstg_ptr],       0x04          \n\t"
      "daddiu     %[dstb_ptr],     %[dstb_ptr],       0x04          \n\t"
      "daddi      %[width],        %[width],         -0x04          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src0] "=&f"(src[0]), [src1] "=&f"(src[1]), [src2] "=&f"(src[2]),
        [src3] "=&f"(src[3]), [dest_hi] "=&f"(dest_hi),
        [dest_lo] "=&f"(dest_lo), [dest] "=&f"(dest)
      : [src_ptr] "r"(src_rgb), [dstr_ptr] "r"(dst_r), [dstg_ptr] "r"(dst_g),
        [dstb_ptr] "r"(dst_b), [width] "r"(width)
      : "memory");
}

void MergeRGBRow_MMI(const uint8_t* src_r,
                     const uint8_t* src_g,
                     const uint8_t* src_b,
                     uint8_t* dst_rgb,
                     int width) {
  uint64_t srcr, srcg, srcb, dest;
  uint64_t srcrg_hi, srcrg_lo, srcbz_hi, srcbz_lo;
  const uint64_t temp = 0x0;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[srcr],         0x07(%[srcr_ptr])                \n\t"
      "gsldrc1    %[srcr],         0x00(%[srcr_ptr])                \n\t"
      "gsldlc1    %[srcg],         0x07(%[srcg_ptr])                \n\t"
      "gsldrc1    %[srcg],         0x00(%[srcg_ptr])                \n\t"
      "punpcklbh  %[srcrg_lo],     %[srcr],           %[srcg]       \n\t"
      "punpckhbh  %[srcrg_hi],     %[srcr],           %[srcg]       \n\t"

      "gsldlc1    %[srcb],         0x07(%[srcb_ptr])                \n\t"
      "gsldrc1    %[srcb],         0x00(%[srcb_ptr])                \n\t"
      "punpcklbh  %[srcbz_lo],     %[srcb],           %[temp]       \n\t"
      "punpckhbh  %[srcbz_hi],     %[srcb],           %[temp]       \n\t"

      "punpcklhw  %[dest],         %[srcrg_lo],       %[srcbz_lo]   \n\t"
      "gsswlc1    %[dest],         0x03(%[dst_ptr])                 \n\t"
      "gsswrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"
      "punpckhwd  %[dest],         %[dest],           %[dest]       \n\t"
      "gsswlc1    %[dest],         0x06(%[dst_ptr])                 \n\t"
      "gsswrc1    %[dest],         0x03(%[dst_ptr])                 \n\t"
      "punpckhhw  %[dest],         %[srcrg_lo],       %[srcbz_lo]   \n\t"
      "gsswlc1    %[dest],         0x09(%[dst_ptr])                 \n\t"
      "gsswrc1    %[dest],         0x06(%[dst_ptr])                 \n\t"
      "punpckhwd  %[dest],         %[dest],           %[dest]       \n\t"
      "gsswlc1    %[dest],         0x0c(%[dst_ptr])                 \n\t"
      "gsswrc1    %[dest],         0x09(%[dst_ptr])                 \n\t"
      "punpcklhw  %[dest],         %[srcrg_hi],       %[srcbz_hi]   \n\t"
      "gsswlc1    %[dest],         0x0f(%[dst_ptr])                 \n\t"
      "gsswrc1    %[dest],         0x0c(%[dst_ptr])                 \n\t"
      "punpckhwd  %[dest],         %[dest],           %[dest]       \n\t"
      "gsswlc1    %[dest],         0x12(%[dst_ptr])                 \n\t"
      "gsswrc1    %[dest],         0x0f(%[dst_ptr])                 \n\t"
      "punpckhhw  %[dest],         %[srcrg_hi],       %[srcbz_hi]   \n\t"
      "gsswlc1    %[dest],         0x15(%[dst_ptr])                 \n\t"
      "gsswrc1    %[dest],         0x12(%[dst_ptr])                 \n\t"
      "punpckhwd  %[dest],         %[dest],           %[dest]       \n\t"
      "gsswlc1    %[dest],         0x18(%[dst_ptr])                 \n\t"
      "gsswrc1    %[dest],         0x15(%[dst_ptr])                 \n\t"

      "daddiu     %[srcr_ptr],     %[srcr_ptr],       0x08          \n\t"
      "daddiu     %[srcg_ptr],     %[srcg_ptr],       0x08          \n\t"
      "daddiu     %[srcb_ptr],     %[srcb_ptr],       0x08          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x18          \n\t"
      "daddi      %[width],        %[width],         -0x08          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [srcr] "=&f"(srcr), [srcg] "=&f"(srcg), [srcb] "=&f"(srcb),
        [dest] "=&f"(dest), [srcrg_hi] "=&f"(srcrg_hi),
        [srcrg_lo] "=&f"(srcrg_lo), [srcbz_hi] "=&f"(srcbz_hi),
        [srcbz_lo] "=&f"(srcbz_lo)
      : [srcr_ptr] "r"(src_r), [srcg_ptr] "r"(src_g), [srcb_ptr] "r"(src_b),
        [dst_ptr] "r"(dst_rgb), [width] "r"(width), [temp] "f"(temp)
      : "memory");
}

// Filter 2 rows of YUY2 UV's (422) into U and V (420).
void YUY2ToUVRow_MMI(const uint8_t* src_yuy2,
                     int src_stride_yuy2,
                     uint8_t* dst_u,
                     uint8_t* dst_v,
                     int width) {
  uint64_t c0 = 0xff00ff00ff00ff00;
  uint64_t c1 = 0x00ff00ff00ff00ff;
  uint64_t temp[3];
  uint64_t data[4];
  uint64_t shift = 0x08;
  uint64_t src_stride = 0x0;
  __asm__ volatile(
      "1:	                                                     \n\t"
      "gsldrc1  %[t0],         0x00(%[src_yuy2])                     \n\t"
      "gsldlc1  %[t0],         0x07(%[src_yuy2])                     \n\t"
      "daddu    %[src_stride], %[src_yuy2],       %[src_stride_yuy2] \n\t"
      "gsldrc1  %[t1],         0x00(%[src_stride])                   \n\t"
      "gsldlc1  %[t1],         0x07(%[src_stride])                   \n\t"
      "pavgb    %[t0],         %[t0],             %[t1]              \n\t"

      "gsldrc1  %[t2],         0x08(%[src_yuy2])                     \n\t"
      "gsldlc1  %[t2],         0x0f(%[src_yuy2])                     \n\t"
      "gsldrc1  %[t1],         0x08(%[src_stride])                   \n\t"
      "gsldlc1  %[t1],         0x0f(%[src_stride])                   \n\t"
      "pavgb    %[t1],         %[t2],             %[t1]              \n\t"

      "and      %[t0],         %[t0],             %[c0]              \n\t"
      "and      %[t1],         %[t1],             %[c0]              \n\t"
      "psrlh    %[t0],         %[t0],             %[shift]           \n\t"
      "psrlh    %[t1],         %[t1],             %[shift]           \n\t"
      "packushb %[t0],         %[t0],             %[t1]              \n\t"
      "mov.s    %[t1],         %[t0]                                 \n\t"
      "and      %[d0],         %[t0],             %[c1]              \n\t"
      "psrlh    %[d1],         %[t1],             %[shift]           \n\t"

      "gsldrc1  %[t0],         0x10(%[src_yuy2])                     \n\t"
      "gsldlc1  %[t0],         0x17(%[src_yuy2])                     \n\t"
      "gsldrc1  %[t1],         0x10(%[src_stride])                   \n\t"
      "gsldlc1  %[t1],         0x17(%[src_stride])                   \n\t"
      "pavgb    %[t0],         %[t0],              %[t1]             \n\t"

      "gsldrc1  %[t2],         0x18(%[src_yuy2])                     \n\t"
      "gsldlc1  %[t2],         0x1f(%[src_yuy2])                     \n\t"
      "gsldrc1  %[t1],         0x18(%[src_stride])                   \n\t"
      "gsldlc1  %[t1],         0x1f(%[src_stride])                   \n\t"
      "pavgb    %[t1],         %[t2],              %[t1]             \n\t"

      "and      %[t0],         %[t0],              %[c0]             \n\t"
      "and      %[t1],         %[t1],              %[c0]             \n\t"
      "psrlh    %[t0],         %[t0],              %[shift]          \n\t"
      "psrlh    %[t1],         %[t1],              %[shift]          \n\t"
      "packushb %[t0],         %[t0],              %[t1]             \n\t"
      "mov.s    %[t1],         %[t0]                                 \n\t"
      "and      %[d2],         %[t0],              %[c1]             \n\t"
      "psrlh    %[d3],         %[t1],              %[shift]          \n\t"

      "packushb %[d0],         %[d0],              %[d2]             \n\t"
      "packushb %[d1],         %[d1],              %[d3]             \n\t"
      "gssdrc1  %[d0],         0x0(%[dst_u])	                     \n\t"
      "gssdlc1  %[d0],         0x7(%[dst_u])                         \n\t"
      "gssdrc1  %[d1],         0x0(%[dst_v])	                     \n\t"
      "gssdlc1  %[d1],         0x7(%[dst_v])                         \n\t"
      "daddiu   %[src_yuy2],   %[src_yuy2],        32                \n\t"
      "daddiu   %[dst_u],      %[dst_u],           8                 \n\t"
      "daddiu   %[dst_v],      %[dst_v],           8                 \n\t"
      "daddiu   %[width],      %[width],          -16                \n\t"
      "bgtz     %[width],      1b                                    \n\t"
      "nop                                                           \n\t"
      : [t0] "=&f"(temp[0]), [t1] "=&f"(temp[1]), [t2] "=&f"(temp[2]),
        [d0] "=&f"(data[0]), [d1] "=&f"(data[1]), [d2] "=&f"(data[2]),
        [d3] "=&f"(data[3]), [src_stride] "=&r"(src_stride)
      : [src_yuy2] "r"(src_yuy2), [src_stride_yuy2] "r"(src_stride_yuy2),
        [dst_u] "r"(dst_u), [dst_v] "r"(dst_v), [width] "r"(width),
        [c0] "f"(c0), [c1] "f"(c1), [shift] "f"(shift)
      : "memory");
}

// Copy row of YUY2 UV's (422) into U and V (422).
void YUY2ToUV422Row_MMI(const uint8_t* src_yuy2,
                        uint8_t* dst_u,
                        uint8_t* dst_v,
                        int width) {
  uint64_t c0 = 0xff00ff00ff00ff00;
  uint64_t c1 = 0x00ff00ff00ff00ff;
  uint64_t temp[2];
  uint64_t data[4];
  uint64_t shift = 0x08;
  __asm__ volatile(
      "1:	                                        \n\t"
      "gsldrc1  %[t0],       0x00(%[src_yuy2])          \n\t"
      "gsldlc1  %[t0],       0x07(%[src_yuy2])          \n\t"
      "gsldrc1  %[t1],       0x08(%[src_yuy2])          \n\t"
      "gsldlc1  %[t1],       0x0f(%[src_yuy2])          \n\t"
      "and      %[t0],       %[t0],            %[c0]    \n\t"
      "and      %[t1],       %[t1],            %[c0]    \n\t"
      "psrlh    %[t0],       %[t0],            %[shift] \n\t"
      "psrlh    %[t1],       %[t1],            %[shift] \n\t"
      "packushb %[t0],       %[t0],            %[t1]    \n\t"
      "mov.s    %[t1],       %[t0]                      \n\t"
      "and      %[d0],       %[t0],            %[c1]    \n\t"
      "psrlh    %[d1],       %[t1],            %[shift] \n\t"

      "gsldrc1  %[t0],       0x10(%[src_yuy2])          \n\t"
      "gsldlc1  %[t0],       0x17(%[src_yuy2])          \n\t"
      "gsldrc1  %[t1],       0x18(%[src_yuy2])          \n\t"
      "gsldlc1  %[t1],       0x1f(%[src_yuy2])          \n\t"
      "and      %[t0],       %[t0],            %[c0]    \n\t"
      "and      %[t1],       %[t1],            %[c0]    \n\t"
      "psrlh    %[t0],       %[t0],            %[shift] \n\t"
      "psrlh    %[t1],       %[t1],            %[shift] \n\t"
      "packushb %[t0],       %[t0],            %[t1]    \n\t"
      "mov.s    %[t1],       %[t0]                      \n\t"
      "and      %[d2],       %[t0],            %[c1]    \n\t"
      "psrlh    %[d3],       %[t1],            %[shift] \n\t"

      "packushb %[d0],       %[d0],            %[d2]    \n\t"
      "packushb %[d1],       %[d1],            %[d3]    \n\t"
      "gssdrc1  %[d0],       0x0(%[dst_u])	        \n\t"
      "gssdlc1  %[d0],       0x7(%[dst_u])              \n\t"
      "gssdrc1  %[d1],       0x0(%[dst_v])	        \n\t"
      "gssdlc1  %[d1],       0x7(%[dst_v])              \n\t"
      "daddiu   %[src_yuy2], %[src_yuy2],      32       \n\t"
      "daddiu   %[dst_u],    %[dst_u],         8        \n\t"
      "daddiu   %[dst_v],    %[dst_v],         8        \n\t"
      "daddiu   %[width],    %[width],        -16       \n\t"
      "bgtz     %[width],    1b                         \n\t"
      "nop                                              \n\t"
      : [t0] "=&f"(temp[0]), [t1] "=&f"(temp[1]), [d0] "=&f"(data[0]),
        [d1] "=&f"(data[1]), [d2] "=&f"(data[2]), [d3] "=&f"(data[3])
      : [src_yuy2] "r"(src_yuy2), [dst_u] "r"(dst_u), [dst_v] "r"(dst_v),
        [width] "r"(width), [c0] "f"(c0), [c1] "f"(c1), [shift] "f"(shift)
      : "memory");
}

// Copy row of YUY2 Y's (422) into Y (420/422).
void YUY2ToYRow_MMI(const uint8_t* src_yuy2, uint8_t* dst_y, int width) {
  uint64_t c0 = 0x00ff00ff00ff00ff;
  uint64_t temp[2];
  __asm__ volatile(
      "1:	                                     \n\t"
      "gsldrc1  %[t0],       0x00(%[src_yuy2])       \n\t"
      "gsldlc1  %[t0],       0x07(%[src_yuy2])       \n\t"
      "gsldrc1  %[t1],       0x08(%[src_yuy2])       \n\t"
      "gsldlc1  %[t1],       0x0f(%[src_yuy2])       \n\t"
      "and      %[t0],       %[t0],            %[c0] \n\t"
      "and      %[t1],       %[t1],            %[c0] \n\t"
      "packushb %[t0],       %[t0],            %[t1] \n\t"
      "gssdrc1  %[t0],       0x0(%[dst_y])	     \n\t"
      "gssdlc1  %[t0],       0x7(%[dst_y])           \n\t"
      "daddiu   %[src_yuy2], %[src_yuy2],      16    \n\t"
      "daddiu   %[dst_y],    %[dst_y],         8     \n\t"
      "daddiu   %[width],    %[width],        -8     \n\t"
      "bgtz     %[width],    1b                      \n\t"
      "nop                                           \n\t"
      : [t0] "=&f"(temp[0]), [t1] "=&f"(temp[1])
      : [src_yuy2] "r"(src_yuy2), [dst_y] "r"(dst_y), [width] "r"(width),
        [c0] "f"(c0)
      : "memory");
}

// Filter 2 rows of UYVY UV's (422) into U and V (420).
void UYVYToUVRow_MMI(const uint8_t* src_uyvy,
                     int src_stride_uyvy,
                     uint8_t* dst_u,
                     uint8_t* dst_v,
                     int width) {
  // Output a row of UV values.
  uint64_t c0 = 0x00ff00ff00ff00ff;
  uint64_t temp[3];
  uint64_t data[4];
  uint64_t shift = 0x08;
  uint64_t src_stride = 0x0;
  __asm__ volatile(
      "1:	                                                      \n\t"
      "gsldrc1  %[t0],         0x00(%[src_uyvy])                      \n\t"
      "gsldlc1  %[t0],         0x07(%[src_uyvy])                      \n\t"
      "daddu    %[src_stride], %[src_uyvy],        %[src_stride_uyvy] \n\t"
      "gsldrc1  %[t1],         0x00(%[src_stride])                    \n\t"
      "gsldlc1  %[t1],         0x07(%[src_stride])                    \n\t"
      "pavgb    %[t0],         %[t0],              %[t1]              \n\t"

      "gsldrc1  %[t2],         0x08(%[src_uyvy])                      \n\t"
      "gsldlc1  %[t2],         0x0f(%[src_uyvy])                      \n\t"
      "gsldrc1  %[t1],         0x08(%[src_stride])                    \n\t"
      "gsldlc1  %[t1],         0x0f(%[src_stride])                    \n\t"
      "pavgb    %[t1],         %[t2],              %[t1]              \n\t"

      "and      %[t0],         %[t0],              %[c0]              \n\t"
      "and      %[t1],         %[t1],              %[c0]              \n\t"
      "packushb %[t0],         %[t0],              %[t1]              \n\t"
      "mov.s    %[t1],         %[t0]                                  \n\t"
      "and      %[d0],         %[t0],              %[c0]              \n\t"
      "psrlh    %[d1],         %[t1],              %[shift]           \n\t"

      "gsldrc1  %[t0],         0x10(%[src_uyvy])                      \n\t"
      "gsldlc1  %[t0],         0x17(%[src_uyvy])                      \n\t"
      "gsldrc1  %[t1],         0x10(%[src_stride])                    \n\t"
      "gsldlc1  %[t1],         0x17(%[src_stride])                    \n\t"
      "pavgb    %[t0],         %[t0],              %[t1]              \n\t"

      "gsldrc1  %[t2],         0x18(%[src_uyvy])                      \n\t"
      "gsldlc1  %[t2],         0x1f(%[src_uyvy])                      \n\t"
      "gsldrc1  %[t1],         0x18(%[src_stride])                    \n\t"
      "gsldlc1  %[t1],         0x1f(%[src_stride])                    \n\t"
      "pavgb    %[t1],         %[t2],              %[t1]              \n\t"

      "and      %[t0],         %[t0],              %[c0]              \n\t"
      "and      %[t1],         %[t1],              %[c0]              \n\t"
      "packushb %[t0],         %[t0],              %[t1]              \n\t"
      "mov.s    %[t1],         %[t0]                                  \n\t"
      "and      %[d2],         %[t0],              %[c0]              \n\t"
      "psrlh    %[d3],         %[t1],              %[shift]           \n\t"

      "packushb %[d0],         %[d0],              %[d2]              \n\t"
      "packushb %[d1],         %[d1],              %[d3]              \n\t"
      "gssdrc1  %[d0],         0x0(%[dst_u])	                      \n\t"
      "gssdlc1  %[d0],         0x7(%[dst_u])                          \n\t"
      "gssdrc1  %[d1],         0x0(%[dst_v])	                      \n\t"
      "gssdlc1  %[d1],         0x7(%[dst_v])                          \n\t"
      "daddiu   %[src_uyvy],   %[src_uyvy],        32                 \n\t"
      "daddiu   %[dst_u],      %[dst_u],           8                  \n\t"
      "daddiu   %[dst_v],      %[dst_v],           8                  \n\t"
      "daddiu   %[width],      %[width],          -16                 \n\t"
      "bgtz     %[width],      1b                                     \n\t"
      "nop                                                            \n\t"
      : [t0] "=&f"(temp[0]), [t1] "=&f"(temp[1]), [t2] "=&f"(temp[2]),
        [d0] "=&f"(data[0]), [d1] "=&f"(data[1]), [d2] "=&f"(data[2]),
        [d3] "=&f"(data[3]), [src_stride] "=&r"(src_stride)
      : [src_uyvy] "r"(src_uyvy), [src_stride_uyvy] "r"(src_stride_uyvy),
        [dst_u] "r"(dst_u), [dst_v] "r"(dst_v), [width] "r"(width),
        [c0] "f"(c0), [shift] "f"(shift)
      : "memory");
}

// Copy row of UYVY UV's (422) into U and V (422).
void UYVYToUV422Row_MMI(const uint8_t* src_uyvy,
                        uint8_t* dst_u,
                        uint8_t* dst_v,
                        int width) {
  // Output a row of UV values.
  uint64_t c0 = 0x00ff00ff00ff00ff;
  uint64_t temp[2];
  uint64_t data[4];
  uint64_t shift = 0x08;
  __asm__ volatile(
      "1:	                                        \n\t"
      "gsldrc1  %[t0],       0x00(%[src_uyvy])          \n\t"
      "gsldlc1  %[t0],       0x07(%[src_uyvy])          \n\t"
      "gsldrc1  %[t1],       0x08(%[src_uyvy])          \n\t"
      "gsldlc1  %[t1],       0x0f(%[src_uyvy])          \n\t"
      "and      %[t0],       %[t0],            %[c0]    \n\t"
      "and      %[t1],       %[t1],            %[c0]    \n\t"
      "packushb %[t0],       %[t0],            %[t1]    \n\t"
      "mov.s    %[t1],       %[t0]                      \n\t"
      "and      %[d0],       %[t0],            %[c0]    \n\t"
      "psrlh    %[d1],       %[t1],            %[shift] \n\t"

      "gsldrc1  %[t0],       0x10(%[src_uyvy])          \n\t"
      "gsldlc1  %[t0],       0x17(%[src_uyvy])          \n\t"
      "gsldrc1  %[t1],       0x18(%[src_uyvy])          \n\t"
      "gsldlc1  %[t1],       0x1f(%[src_uyvy])          \n\t"
      "and      %[t0],       %[t0],            %[c0]    \n\t"
      "and      %[t1],       %[t1],            %[c0]    \n\t"
      "packushb %[t0],       %[t0],            %[t1]    \n\t"
      "mov.s    %[t1],       %[t0]                      \n\t"
      "and      %[d2],       %[t0],            %[c0]    \n\t"
      "psrlh    %[d3],       %[t1],            %[shift] \n\t"

      "packushb %[d0],       %[d0],            %[d2]    \n\t"
      "packushb %[d1],       %[d1],            %[d3]    \n\t"
      "gssdrc1  %[d0],       0x0(%[dst_u])	        \n\t"
      "gssdlc1  %[d0],       0x7(%[dst_u])              \n\t"
      "gssdrc1  %[d1],       0x0(%[dst_v])	        \n\t"
      "gssdlc1  %[d1],       0x7(%[dst_v])              \n\t"
      "daddiu   %[src_uyvy], %[src_uyvy],      32       \n\t"
      "daddiu   %[dst_u],    %[dst_u],         8        \n\t"
      "daddiu   %[dst_v],    %[dst_v],         8        \n\t"
      "daddiu   %[width],    %[width],        -16       \n\t"
      "bgtz     %[width],    1b                         \n\t"
      "nop                                              \n\t"
      : [t0] "=&f"(temp[0]), [t1] "=&f"(temp[1]), [d0] "=&f"(data[0]),
        [d1] "=&f"(data[1]), [d2] "=&f"(data[2]), [d3] "=&f"(data[3])
      : [src_uyvy] "r"(src_uyvy), [dst_u] "r"(dst_u), [dst_v] "r"(dst_v),
        [width] "r"(width), [c0] "f"(c0), [shift] "f"(shift)
      : "memory");
}

// Copy row of UYVY Y's (422) into Y (420/422).
void UYVYToYRow_MMI(const uint8_t* src_uyvy, uint8_t* dst_y, int width) {
  // Output a row of Y values.
  uint64_t c0 = 0x00ff00ff00ff00ff;
  uint64_t shift = 0x08;
  uint64_t temp[2];
  __asm__ volatile(
      "1:	                                        \n\t"
      "gsldrc1  %[t0],       0x00(%[src_uyvy])          \n\t"
      "gsldlc1  %[t0],       0x07(%[src_uyvy])          \n\t"
      "gsldrc1  %[t1],       0x08(%[src_uyvy])          \n\t"
      "gsldlc1  %[t1],       0x0f(%[src_uyvy])          \n\t"
      "dsrl     %[t0],       %[t0],            %[shift] \n\t"
      "dsrl     %[t1],       %[t1],            %[shift] \n\t"
      "and      %[t0],       %[t0],            %[c0]    \n\t"
      "and      %[t1],       %[t1],            %[c0]    \n\t"
      "and      %[t1],       %[t1],            %[c0]    \n\t"
      "packushb %[t0],       %[t0],            %[t1]    \n\t"
      "gssdrc1  %[t0],       0x0(%[dst_y])	        \n\t"
      "gssdlc1  %[t0],       0x7(%[dst_y])              \n\t"
      "daddiu   %[src_uyvy], %[src_uyvy],      16       \n\t"
      "daddiu   %[dst_y],    %[dst_y],         8        \n\t"
      "daddiu   %[width],    %[width],        -8        \n\t"
      "bgtz     %[width],    1b                         \n\t"
      "nop                                              \n\t"
      : [t0] "=&f"(temp[0]), [t1] "=&f"(temp[1])
      : [src_uyvy] "r"(src_uyvy), [dst_y] "r"(dst_y), [width] "r"(width),
        [c0] "f"(c0), [shift] "f"(shift)
      : "memory");
}

// Blend src_argb over src_argb1 and store to dst_argb.
// dst_argb may be src_argb or src_argb1.
// This code mimics the SSSE3 version for better testability.
void ARGBBlendRow_MMI(const uint8_t* src_argb,
                      const uint8_t* src_argb1,
                      uint8_t* dst_argb,
                      int width) {
  uint64_t src0, src1, dest, alpha, src0_hi, src0_lo, src1_hi, src1_lo, dest_hi,
      dest_lo;
  const uint64_t mask0 = 0x0;
  const uint64_t mask1 = 0x00FFFFFF00FFFFFFULL;
  const uint64_t mask2 = 0x00FF00FF00FF00FFULL;
  const uint64_t mask3 = 0xFF;
  const uint64_t mask4 = ~mask1;
  const uint64_t shift = 0x08;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[src0],         0x07(%[src0_ptr])                \n\t"
      "gsldrc1    %[src0],         0x00(%[src0_ptr])                \n\t"
      "punpcklbh  %[src0_lo],      %[src0],           %[mask0]      \n\t"

      "gsldlc1    %[src1],         0x07(%[src1_ptr])                \n\t"
      "gsldrc1    %[src1],         0x00(%[src1_ptr])                \n\t"
      "punpcklbh  %[src1_lo],      %[src1],           %[mask0]      \n\t"

      "psubush    %[alpha],        %[mask2],          %[src0_lo]    \n\t"
      "pshufh     %[alpha],        %[alpha],          %[mask3]      \n\t"
      "pmullh     %[dest_lo],      %[src1_lo],        %[alpha]      \n\t"
      "psrlh      %[dest_lo],      %[dest_lo],        %[shift]      \n\t"
      "paddush    %[dest_lo],      %[dest_lo],        %[src0_lo]    \n\t"

      "punpckhbh  %[src0_hi],      %[src0],           %[mask0]      \n\t"
      "punpckhbh  %[src1_hi],      %[src1],           %[mask0]      \n\t"

      "psubush    %[alpha],        %[mask2],          %[src0_hi]    \n\t"
      "pshufh     %[alpha],        %[alpha],          %[mask3]      \n\t"
      "pmullh     %[dest_hi],      %[src1_hi],        %[alpha]      \n\t"
      "psrlh      %[dest_hi],      %[dest_hi],        %[shift]      \n\t"
      "paddush    %[dest_hi],      %[dest_hi],        %[src0_hi]    \n\t"

      "packushb   %[dest],         %[dest_lo],        %[dest_hi]    \n\t"
      "and        %[dest],         %[dest],           %[mask1]      \n\t"
      "or         %[dest],         %[dest],           %[mask4]      \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[src0_ptr],     %[src0_ptr],       0x08          \n\t"
      "daddiu     %[src1_ptr],     %[src1_ptr],       0x08          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x02          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src0] "=&f"(src0), [src1] "=&f"(src1), [alpha] "=&f"(alpha),
        [dest] "=&f"(dest), [src0_hi] "=&f"(src0_hi), [src0_lo] "=&f"(src0_lo),
        [src1_hi] "=&f"(src1_hi), [src1_lo] "=&f"(src1_lo),
        [dest_hi] "=&f"(dest_hi), [dest_lo] "=&f"(dest_lo)
      : [src0_ptr] "r"(src_argb), [src1_ptr] "r"(src_argb1),
        [dst_ptr] "r"(dst_argb), [mask0] "f"(mask0), [mask1] "f"(mask1),
        [mask2] "f"(mask2), [mask3] "f"(mask3), [mask4] "f"(mask4),
        [shift] "f"(shift), [width] "r"(width)
      : "memory");
}

void BlendPlaneRow_MMI(const uint8_t* src0,
                       const uint8_t* src1,
                       const uint8_t* alpha,
                       uint8_t* dst,
                       int width) {
  uint64_t source0, source1, dest, alph;
  uint64_t src0_hi, src0_lo, src1_hi, src1_lo, alpha_hi, alpha_lo, dest_hi,
      dest_lo;
  uint64_t alpha_rev, alpha_rev_lo, alpha_rev_hi;
  const uint64_t mask0 = 0x0;
  const uint64_t mask1 = 0xFFFFFFFFFFFFFFFFULL;
  const uint64_t mask2 = 0x00FF00FF00FF00FFULL;
  const uint64_t shift = 0x08;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[src0],         0x07(%[src0_ptr])                \n\t"
      "gsldrc1    %[src0],         0x00(%[src0_ptr])                \n\t"
      "punpcklbh  %[src0_lo],      %[src0],           %[mask0]      \n\t"
      "punpckhbh  %[src0_hi],      %[src0],           %[mask0]      \n\t"

      "gsldlc1    %[src1],         0x07(%[src1_ptr])                \n\t"
      "gsldrc1    %[src1],         0x00(%[src1_ptr])                \n\t"
      "punpcklbh  %[src1_lo],      %[src1],           %[mask0]      \n\t"
      "punpckhbh  %[src1_hi],      %[src1],           %[mask0]      \n\t"

      "gsldlc1    %[alpha],        0x07(%[alpha_ptr])               \n\t"
      "gsldrc1    %[alpha],        0x00(%[alpha_ptr])               \n\t"
      "psubusb    %[alpha_r],      %[mask1],          %[alpha]      \n\t"
      "punpcklbh  %[alpha_lo],     %[alpha],          %[mask0]      \n\t"
      "punpckhbh  %[alpha_hi],     %[alpha],          %[mask0]      \n\t"
      "punpcklbh  %[alpha_rlo],    %[alpha_r],        %[mask0]      \n\t"
      "punpckhbh  %[alpha_rhi],    %[alpha_r],        %[mask0]      \n\t"

      "pmullh     %[dest_lo],      %[src0_lo],        %[alpha_lo]   \n\t"
      "pmullh     %[dest],         %[src1_lo],        %[alpha_rlo]  \n\t"
      "paddush    %[dest_lo],      %[dest_lo],        %[dest]       \n\t"
      "paddush    %[dest_lo],      %[dest_lo],        %[mask2]      \n\t"
      "psrlh      %[dest_lo],      %[dest_lo],        %[shift]      \n\t"

      "pmullh     %[dest_hi],      %[src0_hi],        %[alpha_hi]   \n\t"
      "pmullh     %[dest],         %[src1_hi],        %[alpha_rhi]  \n\t"
      "paddush    %[dest_hi],      %[dest_hi],        %[dest]       \n\t"
      "paddush    %[dest_hi],      %[dest_hi],        %[mask2]      \n\t"
      "psrlh      %[dest_hi],      %[dest_hi],        %[shift]      \n\t"

      "packushb   %[dest],         %[dest_lo],        %[dest_hi]    \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[src0_ptr],     %[src0_ptr],       0x08          \n\t"
      "daddiu     %[src1_ptr],     %[src1_ptr],       0x08          \n\t"
      "daddiu     %[alpha_ptr],    %[alpha_ptr],      0x08          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x08          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src0] "=&f"(source0), [src1] "=&f"(source1), [alpha] "=&f"(alph),
        [dest] "=&f"(dest), [src0_hi] "=&f"(src0_hi), [src0_lo] "=&f"(src0_lo),
        [src1_hi] "=&f"(src1_hi), [src1_lo] "=&f"(src1_lo),
        [alpha_hi] "=&f"(alpha_hi), [alpha_lo] "=&f"(alpha_lo),
        [dest_hi] "=&f"(dest_hi), [dest_lo] "=&f"(dest_lo),
        [alpha_rlo] "=&f"(alpha_rev_lo), [alpha_rhi] "=&f"(alpha_rev_hi),
        [alpha_r] "=&f"(alpha_rev)
      : [src0_ptr] "r"(src0), [src1_ptr] "r"(src1), [alpha_ptr] "r"(alpha),
        [dst_ptr] "r"(dst), [mask0] "f"(mask0), [mask1] "f"(mask1),
        [mask2] "f"(mask2), [shift] "f"(shift), [width] "r"(width)
      : "memory");
}

// Multiply source RGB by alpha and store to destination.
// This code mimics the SSSE3 version for better testability.
void ARGBAttenuateRow_MMI(const uint8_t* src_argb,
                          uint8_t* dst_argb,
                          int width) {
  uint64_t src, src_hi, src_lo, dest, dest_hi, dest_lo, alpha;
  const uint64_t mask0 = 0xFF;
  const uint64_t mask1 = 0xFF000000FF000000ULL;
  const uint64_t mask2 = ~mask1;
  const uint64_t shift = 0x08;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[src],          0x07(%[src_ptr])                 \n\t"
      "gsldrc1    %[src],          0x00(%[src_ptr])                 \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[src]        \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[src]        \n\t"

      "pshufh     %[alpha],        %[src_lo],         %[mask0]      \n\t"
      "pmulhuh    %[dest_lo],      %[alpha],          %[src_lo]     \n\t"
      "psrlh      %[dest_lo],      %[dest_lo],        %[shift]      \n\t"
      "pshufh     %[alpha],        %[src_hi],         %[mask0]      \n\t"
      "pmulhuh    %[dest_hi],      %[alpha],          %[src_hi]     \n\t"
      "psrlh      %[dest_hi],      %[dest_hi],        %[shift]      \n\t"

      "packushb   %[dest],         %[dest_lo],        %[dest_hi]    \n\t"
      "and        %[dest],         %[dest],           %[mask2]      \n\t"
      "and        %[src],          %[src],            %[mask1]      \n\t"
      "or         %[dest],         %[dest],           %[src]        \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x08          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x02          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src] "=&f"(src), [dest] "=&f"(dest), [src_hi] "=&f"(src_hi),
        [src_lo] "=&f"(src_lo), [dest_hi] "=&f"(dest_hi),
        [dest_lo] "=&f"(dest_lo), [alpha] "=&f"(alpha)
      : [src_ptr] "r"(src_argb), [dst_ptr] "r"(dst_argb), [mask0] "f"(mask0),
        [mask1] "f"(mask1), [mask2] "f"(mask2), [shift] "f"(shift),
        [width] "r"(width)
      : "memory");
}

void ComputeCumulativeSumRow_MMI(const uint8_t* row,
                                 int32_t* cumsum,
                                 const int32_t* previous_cumsum,
                                 int width) {
  int64_t row_sum[2] = {0, 0};
  uint64_t src, dest0, dest1, presrc0, presrc1, dest;
  const uint64_t mask = 0x0;

  __asm__ volatile(
      "xor        %[row_sum0],     %[row_sum0],       %[row_sum0]   \n\t"
      "xor        %[row_sum1],     %[row_sum1],       %[row_sum1]   \n\t"

      "1:                                                           \n\t"
      "gslwlc1    %[src],          0x03(%[row_ptr])                 \n\t"
      "gslwrc1    %[src],          0x00(%[row_ptr])                 \n\t"

      "punpcklbh  %[src],          %[src],            %[mask]       \n\t"
      "punpcklhw  %[dest0],        %[src],            %[mask]       \n\t"
      "punpckhhw  %[dest1],        %[src],            %[mask]       \n\t"

      "paddw      %[row_sum0],     %[row_sum0],       %[dest0]      \n\t"
      "paddw      %[row_sum1],     %[row_sum1],       %[dest1]      \n\t"

      "gsldlc1    %[presrc0],      0x07(%[pre_ptr])                 \n\t"
      "gsldrc1    %[presrc0],      0x00(%[pre_ptr])                 \n\t"
      "gsldlc1    %[presrc1],      0x0f(%[pre_ptr])                 \n\t"
      "gsldrc1    %[presrc1],      0x08(%[pre_ptr])                 \n\t"

      "paddw      %[dest0],        %[row_sum0],       %[presrc0]    \n\t"
      "paddw      %[dest1],        %[row_sum1],       %[presrc1]    \n\t"

      "gssdlc1    %[dest0],        0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest0],        0x00(%[dst_ptr])                 \n\t"
      "gssdlc1    %[dest1],        0x0f(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest1],        0x08(%[dst_ptr])                 \n\t"

      "daddiu     %[row_ptr],      %[row_ptr],        0x04          \n\t"
      "daddiu     %[pre_ptr],      %[pre_ptr],        0x10          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x10          \n\t"
      "daddi      %[width],        %[width],         -0x01          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src] "=&f"(src), [dest] "=&f"(dest), [dest0] "=&f"(dest0),
        [dest1] "=&f"(dest1), [row_sum0] "+&f"(row_sum[0]),
        [row_sum1] "+&f"(row_sum[1]), [presrc0] "=&f"(presrc0),
        [presrc1] "=&f"(presrc1)
      : [row_ptr] "r"(row), [pre_ptr] "r"(previous_cumsum),
        [dst_ptr] "r"(cumsum), [width] "r"(width), [mask] "f"(mask)
      : "memory");
}

// C version 2x2 -> 2x1.
void InterpolateRow_MMI(uint8_t* dst_ptr,
                        const uint8_t* src_ptr,
                        ptrdiff_t src_stride,
                        int width,
                        int source_y_fraction) {
  if (source_y_fraction == 0) {
    __asm__ volatile(
        "1:	                              \n\t"
        "ld     $t0,        0x0(%[src_ptr])   \n\t"
        "sd     $t0,        0x0(%[dst_ptr])   \n\t"
        "daddiu %[src_ptr], %[src_ptr],     8 \n\t"
        "daddiu %[dst_ptr], %[dst_ptr],     8 \n\t"
        "daddiu %[width],   %[width],      -8 \n\t"
        "bgtz   %[width],   1b                \n\t"
        "nop                                  \n\t"
        :
        : [dst_ptr] "r"(dst_ptr), [src_ptr] "r"(src_ptr), [width] "r"(width)
        : "memory");
    return;
  }
  if (source_y_fraction == 128) {
    uint64_t uv = 0x0;
    uint64_t uv_stride = 0x0;
    __asm__ volatile(
        "1:	                                            \n\t"
        "gsldrc1 %[uv],        0x0(%[src_ptr])              \n\t"
        "gsldlc1 %[uv],        0x7(%[src_ptr])              \n\t"
        "daddu   $t0,          %[src_ptr],     %[stride]    \n\t"
        "gsldrc1 %[uv_stride], 0x0($t0)                     \n\t"
        "gsldlc1 %[uv_stride], 0x7($t0)                     \n\t"

        "pavgb   %[uv],        %[uv],          %[uv_stride] \n\t"
        "gssdrc1 %[uv],        0x0(%[dst_ptr])              \n\t"
        "gssdlc1 %[uv],        0x7(%[dst_ptr])              \n\t"

        "daddiu  %[src_ptr],   %[src_ptr],     8            \n\t"
        "daddiu  %[dst_ptr],   %[dst_ptr],     8            \n\t"
        "daddiu  %[width],     %[width],      -8            \n\t"
        "bgtz    %[width],     1b                           \n\t"
        "nop                                                \n\t"
        : [uv] "=&f"(uv), [uv_stride] "=&f"(uv_stride)
        : [src_ptr] "r"(src_ptr), [dst_ptr] "r"(dst_ptr), [width] "r"(width),
          [stride] "r"((int64_t)src_stride)
        : "memory");
    return;
  }
  const uint8_t* src_ptr1 = src_ptr + src_stride;
  uint64_t temp;
  uint64_t data[4];
  uint64_t zero = 0x0;
  uint64_t c0 = 0x0080008000800080;
  uint64_t fy0 = 0x0100010001000100;
  uint64_t shift = 0x8;
  __asm__ volatile(
      "pshufh    %[fy1],      %[fy1],          %[zero]  \n\t"
      "psubh     %[fy0],      %[fy0],          %[fy1]   \n\t"
      "1:	                                        \n\t"
      "gsldrc1   %[t0],       0x0(%[src_ptr])           \n\t"
      "gsldlc1   %[t0],       0x7(%[src_ptr])           \n\t"
      "punpcklbh %[d0],       %[t0],           %[zero]  \n\t"
      "punpckhbh %[d1],       %[t0],           %[zero]  \n\t"
      "gsldrc1   %[t0],       0x0(%[src_ptr1])          \n\t"
      "gsldlc1   %[t0],       0x7(%[src_ptr1])          \n\t"
      "punpcklbh %[d2],       %[t0],           %[zero]  \n\t"
      "punpckhbh %[d3],       %[t0],           %[zero]  \n\t"

      "pmullh    %[d0],       %[d0],           %[fy0]   \n\t"
      "pmullh    %[d2],       %[d2],           %[fy1]   \n\t"
      "paddh     %[d0],       %[d0],           %[d2]    \n\t"
      "paddh     %[d0],       %[d0],           %[c0]    \n\t"
      "psrlh     %[d0],       %[d0],           %[shift] \n\t"

      "pmullh    %[d1],       %[d1],           %[fy0]   \n\t"
      "pmullh    %[d3],       %[d3],           %[fy1]   \n\t"
      "paddh     %[d1],       %[d1],           %[d3]    \n\t"
      "paddh     %[d1],       %[d1],           %[c0]    \n\t"
      "psrlh     %[d1],       %[d1],           %[shift] \n\t"

      "packushb  %[d0],       %[d0],           %[d1]    \n\t"
      "gssdrc1   %[d0],       0x0(%[dst_ptr])           \n\t"
      "gssdlc1   %[d0],       0x7(%[dst_ptr])           \n\t"
      "daddiu    %[src_ptr],  %[src_ptr],      8        \n\t"
      "daddiu    %[src_ptr1], %[src_ptr1],     8        \n\t"
      "daddiu    %[dst_ptr],  %[dst_ptr],      8        \n\t"
      "daddiu    %[width],    %[width],       -8        \n\t"
      "bgtz      %[width],    1b                        \n\t"
      "nop                                              \n\t"
      : [t0] "=&f"(temp), [d0] "=&f"(data[0]), [d1] "=&f"(data[1]),
        [d2] "=&f"(data[2]), [d3] "=&f"(data[3])
      : [src_ptr] "r"(src_ptr), [src_ptr1] "r"(src_ptr1),
        [dst_ptr] "r"(dst_ptr), [width] "r"(width),
        [fy1] "f"(source_y_fraction), [fy0] "f"(fy0), [c0] "f"(c0),
        [shift] "f"(shift), [zero] "f"(zero)
      : "memory");
}

// Use first 4 shuffler values to reorder ARGB channels.
void ARGBShuffleRow_MMI(const uint8_t* src_argb,
                        uint8_t* dst_argb,
                        const uint8_t* shuffler,
                        int width) {
  uint64_t source, dest0, dest1, dest;
  const uint64_t mask0 = 0x0;
  const uint64_t mask1 = (shuffler[0] & 0x03) | ((shuffler[1] & 0x03) << 2) |
                         ((shuffler[2] & 0x03) << 4) |
                         ((shuffler[3] & 0x03) << 6);

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[src],          0x07(%[src_ptr])                 \n\t"
      "gsldrc1    %[src],          0x00(%[src_ptr])                 \n\t"

      "punpcklbh  %[dest0],        %[src],            %[mask0]      \n\t"
      "pshufh     %[dest0],        %[dest0],          %[mask1]      \n\t"
      "punpckhbh  %[dest1],        %[src],            %[mask0]      \n\t"
      "pshufh     %[dest1],        %[dest1],          %[mask1]      \n\t"
      "packushb   %[dest],         %[dest0],          %[dest1]      \n\t"

      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x08          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x02          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src] "=&f"(source), [dest] "=&f"(dest), [dest0] "=&f"(dest0),
        [dest1] "=&f"(dest1)
      : [src_ptr] "r"(src_argb), [dst_ptr] "r"(dst_argb), [mask0] "f"(mask0),
        [mask1] "f"(mask1), [width] "r"(width)
      : "memory");
}

void I422ToYUY2Row_MMI(const uint8_t* src_y,
                       const uint8_t* src_u,
                       const uint8_t* src_v,
                       uint8_t* dst_frame,
                       int width) {
  uint64_t temp[3];
  uint64_t vu = 0x0;
  __asm__ volatile(
      "1:	                                        \n\t"
      "gsldlc1   %[ty],        0x7(%[src_y])            \n\t"  // r=src_sobelx[i]
      "gsldrc1   %[ty],        0x0(%[src_y])            \n\t"  // r=src_sobelx[i]
      "gslwlc1   %[tu],        0x3(%[src_u])            \n\t"  // b=src_sobely[i]
      "gslwrc1   %[tu],        0x0(%[src_u])            \n\t"  // b=src_sobely[i]
      "gslwlc1   %[tv],        0x3(%[src_v])            \n\t"  // b=src_sobely[i]
      "gslwrc1   %[tv],        0x0(%[src_v])            \n\t"  // b=src_sobely[i]
      "punpcklbh %[vu],        %[tu],             %[tv]	\n\t"  // g
      "punpcklbh %[tu],        %[ty],             %[vu]	\n\t"  // g
      "gssdlc1   %[tu],        0x7(%[dst_frame])        \n\t"
      "gssdrc1   %[tu],        0x0(%[dst_frame])        \n\t"
      "punpckhbh %[tu],        %[ty],             %[vu]	\n\t"  // g
      "gssdlc1   %[tu],        0x0F(%[dst_frame])       \n\t"
      "gssdrc1   %[tu],        0x08(%[dst_frame])       \n\t"
      "daddiu    %[src_y],     %[src_y],          8     \n\t"
      "daddiu    %[src_u],     %[src_u],          4     \n\t"
      "daddiu    %[src_v],     %[src_v],          4     \n\t"
      "daddiu    %[dst_frame], %[dst_frame],      16    \n\t"
      "daddiu    %[width],     %[width],         -8     \n\t"
      "bgtz      %[width],     1b                       \n\t"
      "nop                                              \n\t"
      : [ty] "=&f"(temp[1]), [tu] "=&f"(temp[1]), [tv] "=&f"(temp[1]),
        [vu] "=&f"(vu)
      : [src_y] "r"(src_y), [src_u] "r"(src_u), [src_v] "r"(src_v),
        [dst_frame] "r"(dst_frame), [width] "r"(width)
      : "memory");
}

void I422ToUYVYRow_MMI(const uint8_t* src_y,
                       const uint8_t* src_u,
                       const uint8_t* src_v,
                       uint8_t* dst_frame,
                       int width) {
  uint64_t temp[3];
  uint64_t vu = 0x0;
  __asm__ volatile(
      "1:	                                        \n\t"
      "gsldlc1   %[ty],        0x7(%[src_y])            \n\t"  // r=src_sobelx[i]
      "gsldrc1   %[ty],        0x0(%[src_y])            \n\t"  // r=src_sobelx[i]
      "gslwlc1   %[tu],        0x3(%[src_u])            \n\t"  // b=src_sobely[i]
      "gslwrc1   %[tu],        0x0(%[src_u])            \n\t"  // b=src_sobely[i]
      "gslwlc1   %[tv],        0x3(%[src_v])            \n\t"  // b=src_sobely[i]
      "gslwrc1   %[tv],        0x0(%[src_v])            \n\t"  // b=src_sobely[i]
      "punpcklbh %[vu],        %[tu],             %[tv]	\n\t"  // g
      "punpcklbh %[tu],        %[vu],             %[ty]	\n\t"  // g
      "gssdlc1   %[tu],        0x7(%[dst_frame])        \n\t"
      "gssdrc1   %[tu],        0x0(%[dst_frame])        \n\t"
      "punpckhbh %[tu],        %[vu],             %[ty]	\n\t"  // g
      "gssdlc1   %[tu],        0x0F(%[dst_frame])       \n\t"
      "gssdrc1   %[tu],        0x08(%[dst_frame])       \n\t"
      "daddiu    %[src_y],     %[src_y],          8     \n\t"
      "daddiu    %[src_u],     %[src_u],          4     \n\t"
      "daddiu    %[src_v],     %[src_v],          4     \n\t"
      "daddiu    %[dst_frame], %[dst_frame],      16    \n\t"
      "daddiu    %[width],     %[width],         -8     \n\t"
      "bgtz      %[width],     1b                       \n\t"
      "nop                                              \n\t"
      : [ty] "=&f"(temp[1]), [tu] "=&f"(temp[1]), [tv] "=&f"(temp[1]),
        [vu] "=&f"(vu)
      : [src_y] "r"(src_y), [src_u] "r"(src_u), [src_v] "r"(src_v),
        [dst_frame] "r"(dst_frame), [width] "r"(width)
      : "memory");
}

void ARGBCopyAlphaRow_MMI(const uint8_t* src, uint8_t* dst, int width) {
  uint64_t source, dest;
  const uint64_t mask0 = 0xff000000ff000000ULL;
  const uint64_t mask1 = ~mask0;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[src],          0x07(%[src_ptr])                 \n\t"
      "gsldrc1    %[src],          0x00(%[src_ptr])                 \n\t"
      "gsldlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gsldrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "and        %[src],          %[src],            %[mask0]      \n\t"
      "and        %[dest],         %[dest],           %[mask1]      \n\t"
      "or         %[dest],         %[src],            %[dest]       \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x08          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x02          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src] "=&f"(source), [dest] "=&f"(dest)
      : [src_ptr] "r"(src), [dst_ptr] "r"(dst), [mask0] "f"(mask0),
        [mask1] "f"(mask1), [width] "r"(width)
      : "memory");
}

void ARGBExtractAlphaRow_MMI(const uint8_t* src_argb,
                             uint8_t* dst_a,
                             int width) {
  uint64_t src, dest0, dest1, dest_lo, dest_hi, dest;
  const uint64_t mask = 0xff000000ff000000ULL;
  const uint64_t shift = 0x18;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[src],          0x07(%[src_ptr])                 \n\t"
      "gsldrc1    %[src],          0x00(%[src_ptr])                 \n\t"
      "and        %[dest0],        %[src],            %[mask]       \n\t"
      "psrlw      %[dest0],        %[dest0],          %[shift]      \n\t"
      "gsldlc1    %[src],          0x0f(%[src_ptr])                 \n\t"
      "gsldrc1    %[src],          0x08(%[src_ptr])                 \n\t"
      "and        %[dest1],        %[src],            %[mask]       \n\t"
      "psrlw      %[dest1],        %[dest1],          %[shift]      \n\t"
      "packsswh   %[dest_lo],      %[dest0],          %[dest1]      \n\t"

      "gsldlc1    %[src],          0x17(%[src_ptr])                 \n\t"
      "gsldrc1    %[src],          0x10(%[src_ptr])                 \n\t"
      "and        %[dest0],        %[src],            %[mask]       \n\t"
      "psrlw      %[dest0],        %[dest0],          %[shift]      \n\t"
      "gsldlc1    %[src],          0x1f(%[src_ptr])                 \n\t"
      "gsldrc1    %[src],          0x18(%[src_ptr])                 \n\t"
      "and        %[dest1],        %[src],            %[mask]       \n\t"
      "psrlw      %[dest1],        %[dest1],          %[shift]      \n\t"
      "packsswh   %[dest_hi],      %[dest0],          %[dest1]      \n\t"

      "packushb   %[dest],         %[dest_lo],        %[dest_hi]    \n\t"

      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x20          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x08          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src] "=&f"(src), [dest] "=&f"(dest), [dest0] "=&f"(dest0),
        [dest1] "=&f"(dest1), [dest_lo] "=&f"(dest_lo), [dest_hi] "=&f"(dest_hi)
      : [src_ptr] "r"(src_argb), [dst_ptr] "r"(dst_a), [mask] "f"(mask),
        [shift] "f"(shift), [width] "r"(width)
      : "memory");
}

void ARGBCopyYToAlphaRow_MMI(const uint8_t* src, uint8_t* dst, int width) {
  uint64_t source, dest0, dest1, dest;
  const uint64_t mask0 = 0x0;
  const uint64_t mask1 = 0x00ffffff00ffffffULL;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[src],          0x07(%[src_ptr])                 \n\t"
      "gsldrc1    %[src],          0x00(%[src_ptr])                 \n\t"

      "punpcklbh  %[dest0],        %[mask0],          %[src]        \n\t"
      "punpcklhw  %[dest1],        %[mask0],          %[dest0]      \n\t"
      "gsldlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gsldrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"
      "and        %[dest],         %[dest],           %[mask1]      \n\t"
      "or         %[dest],         %[dest],           %[dest1]      \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"
      "punpckhhw  %[dest1],        %[mask0],          %[dest0]      \n\t"
      "gsldlc1    %[dest],         0x0f(%[dst_ptr])                 \n\t"
      "gsldrc1    %[dest],         0x08(%[dst_ptr])                 \n\t"
      "and        %[dest],         %[dest],           %[mask1]      \n\t"
      "or         %[dest],         %[dest],           %[dest1]      \n\t"
      "gssdlc1    %[dest],         0x0f(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x08(%[dst_ptr])                 \n\t"

      "punpckhbh  %[dest0],        %[mask0],          %[src]        \n\t"
      "punpcklhw  %[dest1],        %[mask0],          %[dest0]      \n\t"
      "gsldlc1    %[dest],         0x17(%[dst_ptr])                 \n\t"
      "gsldrc1    %[dest],         0x10(%[dst_ptr])                 \n\t"
      "and        %[dest],         %[dest],           %[mask1]      \n\t"
      "or         %[dest],         %[dest],           %[dest1]      \n\t"
      "gssdlc1    %[dest],         0x17(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x10(%[dst_ptr])                 \n\t"
      "punpckhhw  %[dest1],        %[mask0],          %[dest0]      \n\t"
      "gsldlc1    %[dest],         0x1f(%[dst_ptr])                 \n\t"
      "gsldrc1    %[dest],         0x18(%[dst_ptr])                 \n\t"
      "and        %[dest],         %[dest],           %[mask1]      \n\t"
      "or         %[dest],         %[dest],           %[dest1]      \n\t"
      "gssdlc1    %[dest],         0x1f(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x18(%[dst_ptr])                 \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x08          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x20          \n\t"
      "daddi      %[width],        %[width],         -0x08          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src] "=&f"(source), [dest] "=&f"(dest), [dest0] "=&f"(dest0),
        [dest1] "=&f"(dest1)
      : [src_ptr] "r"(src), [dst_ptr] "r"(dst), [mask0] "f"(mask0),
        [mask1] "f"(mask1), [width] "r"(width)
      : "memory");
}

void I444ToARGBRow_MMI(const uint8_t* src_y,
                       const uint8_t* src_u,
                       const uint8_t* src_v,
                       uint8_t* rgb_buf,
                       const struct YuvConstants* yuvconstants,
                       int width) {
  uint64_t y,u,v;
  uint64_t b_vec[2],g_vec[2],r_vec[2];
  uint64_t mask = 0xff00ff00ff00ff00ULL;
  uint64_t ub,ug,vg,vr,bb,bg,br,yg;
  __asm__ volatile (
    "ldc1       %[yg],           0xc0(%[yuvcons_ptr])             \n\t"//yg
    "ldc1       %[bb],           0x60(%[yuvcons_ptr])             \n\t"//bb
    "ldc1       %[ub],           0x00(%[yuvcons_ptr])             \n\t"//ub
    "or         %[ub],           %[ub],             %[mask]       \n\t"//must sign extension
    "ldc1       %[bg],           0x80(%[yuvcons_ptr])             \n\t"//bg
    "ldc1       %[ug],           0x20(%[yuvcons_ptr])             \n\t"//ug
    "punpcklbh  %[ug],           %[ug],             %[zero]       \n\t"
    "pshufh     %[ug],           %[ug],             %[zero]       \n\t"
    "ldc1       %[vg],           0x20(%[yuvcons_ptr])             \n\t"//vg
    "punpcklbh  %[vg],           %[vg],             %[zero]       \n\t"
    "pshufh     %[vg],           %[vg],             %[five]       \n\t"
    "ldc1       %[br],           0xa0(%[yuvcons_ptr])             \n\t"//br
    "ldc1       %[vr],           0x40(%[yuvcons_ptr])             \n\t"//vr
    "punpcklbh  %[vr],           %[vr],             %[zero]       \n\t"
    "pshufh     %[vr],           %[vr],             %[five]       \n\t"
    "or         %[vr],           %[vr],             %[mask]       \n\t"//sign extension

    "1:                                                           \n\t"
    "gslwlc1    %[y],            0x03(%[y_ptr])                   \n\t"
    "gslwrc1    %[y],            0x00(%[y_ptr])                   \n\t"
    "gslwlc1    %[u],            0x03(%[u_ptr])                   \n\t"
    "gslwrc1    %[u],            0x00(%[u_ptr])                   \n\t"
    "gslwlc1    %[v],            0x03(%[v_ptr])                   \n\t"
    "gslwrc1    %[v],            0x00(%[v_ptr])                   \n\t"

    "punpcklbh  %[y],            %[y],              %[y]          \n\t"//y*0x0101
    "pmulhuh    %[y],            %[y],              %[yg]         \n\t"//y1

    "punpcklbh  %[u],            %[u],              %[zero]       \n\t"//u
    "paddsh     %[b_vec0],       %[y],              %[bb]         \n\t"
    "pmullh     %[b_vec1],       %[u],              %[ub]         \n\t"
    "psubsh     %[b_vec0],       %[b_vec0],         %[b_vec1]     \n\t"
    "psrah      %[b_vec0],       %[b_vec0],         %[six]        \n\t"

    "punpcklbh  %[v],            %[v],              %[zero]       \n\t"//v
    "paddsh     %[g_vec0],       %[y],              %[bg]         \n\t"
    "pmullh     %[g_vec1],       %[u],              %[ug]         \n\t"//u*ug
    "psubsh     %[g_vec0],       %[g_vec0],         %[g_vec1]     \n\t"
    "pmullh     %[g_vec1],       %[v],              %[vg]         \n\t"//v*vg
    "psubsh     %[g_vec0],       %[g_vec0],         %[g_vec1]     \n\t"
    "psrah      %[g_vec0],       %[g_vec0],         %[six]        \n\t"

    "paddsh     %[r_vec0],       %[y],              %[br]         \n\t"
    "pmullh     %[r_vec1],       %[v],              %[vr]         \n\t"//v*vr
    "psubsh     %[r_vec0],       %[r_vec0],         %[r_vec1]     \n\t"
    "psrah      %[r_vec0],       %[r_vec0],         %[six]        \n\t"

    "packushb   %[r_vec0],       %[b_vec0],         %[r_vec0]     \n\t"//rrrrbbbb
    "packushb   %[g_vec0],       %[g_vec0],         %[alpha]      \n\t"//ffffgggg
    "punpcklwd  %[g_vec0],       %[g_vec0],         %[alpha]      \n\t"
    "punpcklbh  %[b_vec0],       %[r_vec0],         %[g_vec0]     \n\t"//gbgbgbgb
    "punpckhbh  %[r_vec0],       %[r_vec0],         %[g_vec0]     \n\t"//frfrfrfr
    "punpcklhw  %[g_vec0],       %[b_vec0],         %[r_vec0]     \n\t"//frgbfrgb
    "punpckhhw  %[g_vec1],       %[b_vec0],         %[r_vec0]     \n\t"//frgbfrgb
    "gssdlc1    %[g_vec0],       0x07(%[rgbbuf_ptr])              \n\t"
    "gssdrc1    %[g_vec0],       0x00(%[rgbbuf_ptr])              \n\t"
    "gssdlc1    %[g_vec1],       0x0f(%[rgbbuf_ptr])              \n\t"
    "gssdrc1    %[g_vec1],       0x08(%[rgbbuf_ptr])              \n\t"

    "daddiu     %[y_ptr],        %[y_ptr],          0x04          \n\t"
    "daddiu     %[u_ptr],        %[u_ptr],          0x04          \n\t"
    "daddiu     %[v_ptr],        %[v_ptr],          0x04          \n\t"
    "daddiu     %[rgbbuf_ptr],   %[rgbbuf_ptr],     0x10          \n\t"
    "daddi      %[width],        %[width],          -0x04         \n\t"
    "bnez       %[width],        1b                               \n\t"
    : [y]"=&f"(y),
      [u]"=&f"(u),                         [v]"=&f"(v),
      [b_vec0]"=&f"(b_vec[0]),             [b_vec1]"=&f"(b_vec[1]),
      [g_vec0]"=&f"(g_vec[0]),             [g_vec1]"=&f"(g_vec[1]),
      [r_vec0]"=&f"(r_vec[0]),             [r_vec1]"=&f"(r_vec[1]),
      [ub]"=&f"(ub),                       [ug]"=&f"(ug),
      [vg]"=&f"(vg),                       [vr]"=&f"(vr),
      [bb]"=&f"(bb),                       [bg]"=&f"(bg),
      [br]"=&f"(br),                       [yg]"=&f"(yg)
    : [y_ptr]"r"(src_y),                   [u_ptr]"r"(src_u),
      [v_ptr]"r"(src_v),                   [rgbbuf_ptr]"r"(rgb_buf),
      [yuvcons_ptr]"r"(yuvconstants),      [width]"r"(width),
      [zero]"f"(0x00),                     [alpha]"f"(-1),
      [six]"f"(0x6),                       [five]"f"(0x55),
      [mask]"f"(mask)
    : "memory"
  );
}

// Also used for 420
void I422ToARGBRow_MMI(const uint8_t* src_y,
                       const uint8_t* src_u,
                       const uint8_t* src_v,
                       uint8_t* rgb_buf,
                       const struct YuvConstants* yuvconstants,
                       int width) {
  uint64_t y,u,v;
  uint64_t b_vec[2],g_vec[2],r_vec[2];
  uint64_t mask = 0xff00ff00ff00ff00ULL;
  uint64_t ub,ug,vg,vr,bb,bg,br,yg;

  __asm__ volatile(
    "ldc1       %[yg],           0xc0(%[yuvcons_ptr])             \n\t"//yg
    "ldc1       %[bb],           0x60(%[yuvcons_ptr])             \n\t"//bb
    "ldc1       %[ub],           0x00(%[yuvcons_ptr])             \n\t"//ub
    "or         %[ub],           %[ub],             %[mask]       \n\t"//must sign extension
    "ldc1       %[bg],           0x80(%[yuvcons_ptr])             \n\t"//bg
    "ldc1       %[ug],           0x20(%[yuvcons_ptr])             \n\t"//ug
    "punpcklbh  %[ug],           %[ug],             %[zero]       \n\t"
    "pshufh     %[ug],           %[ug],             %[zero]       \n\t"
    "ldc1       %[vg],           0x20(%[yuvcons_ptr])             \n\t"//vg
    "punpcklbh  %[vg],           %[vg],             %[zero]       \n\t"
    "pshufh     %[vg],           %[vg],             %[five]       \n\t"
    "ldc1       %[br],           0xa0(%[yuvcons_ptr])             \n\t"//br
    "ldc1       %[vr],           0x40(%[yuvcons_ptr])             \n\t"//vr
    "punpcklbh  %[vr],           %[vr],             %[zero]       \n\t"
    "pshufh     %[vr],           %[vr],             %[five]       \n\t"
    "or         %[vr],           %[vr],             %[mask]       \n\t"//sign extension

    "1:                                                           \n\t"
    "gslwlc1    %[y],            0x03(%[y_ptr])                   \n\t"
    "gslwrc1    %[y],            0x00(%[y_ptr])                   \n\t"
    "gslwlc1    %[u],            0x03(%[u_ptr])                   \n\t"
    "gslwrc1    %[u],            0x00(%[u_ptr])                   \n\t"
    "gslwlc1    %[v],            0x03(%[v_ptr])                   \n\t"
    "gslwrc1    %[v],            0x00(%[v_ptr])                   \n\t"

    "punpcklbh  %[y],            %[y],              %[y]          \n\t"//y*0x0101
    "pmulhuh    %[y],            %[y],              %[yg]         \n\t"//y1

    //u3|u2|u1|u0 --> u1|u1|u0|u0
    "punpcklbh  %[u],            %[u],              %[u]          \n\t"//u
    "punpcklbh  %[u],            %[u],              %[zero]       \n\t"
    "paddsh     %[b_vec0],       %[y],              %[bb]         \n\t"
    "pmullh     %[b_vec1],       %[u],              %[ub]         \n\t"
    "psubsh     %[b_vec0],       %[b_vec0],         %[b_vec1]     \n\t"
    "psrah      %[b_vec0],       %[b_vec0],         %[six]        \n\t"

    //v3|v2|v1|v0 --> v1|v1|v0|v0
    "punpcklbh  %[v],            %[v],              %[v]          \n\t"//v
    "punpcklbh  %[v],            %[v],              %[zero]       \n\t"
    "paddsh     %[g_vec0],       %[y],              %[bg]         \n\t"
    "pmullh     %[g_vec1],       %[u],              %[ug]         \n\t"//u*ug
    "psubsh     %[g_vec0],       %[g_vec0],         %[g_vec1]     \n\t"
    "pmullh     %[g_vec1],       %[v],              %[vg]         \n\t"//v*vg
    "psubsh     %[g_vec0],       %[g_vec0],         %[g_vec1]     \n\t"
    "psrah      %[g_vec0],       %[g_vec0],         %[six]        \n\t"

    "paddsh     %[r_vec0],       %[y],              %[br]         \n\t"
    "pmullh     %[r_vec1],       %[v],              %[vr]         \n\t"//v*vr
    "psubsh     %[r_vec0],       %[r_vec0],         %[r_vec1]     \n\t"
    "psrah      %[r_vec0],       %[r_vec0],         %[six]        \n\t"

    "packushb   %[r_vec0],       %[b_vec0],         %[r_vec0]     \n\t"//rrrrbbbb
    "packushb   %[g_vec0],       %[g_vec0],         %[alpha]      \n\t"//ffffgggg
    "punpcklwd  %[g_vec0],       %[g_vec0],         %[alpha]      \n\t"
    "punpcklbh  %[b_vec0],       %[r_vec0],         %[g_vec0]     \n\t"//gbgbgbgb
    "punpckhbh  %[r_vec0],       %[r_vec0],         %[g_vec0]     \n\t"//frfrfrfr
    "punpcklhw  %[g_vec0],       %[b_vec0],         %[r_vec0]     \n\t"//frgbfrgb
    "punpckhhw  %[g_vec1],       %[b_vec0],         %[r_vec0]     \n\t"//frgbfrgb
    "gssdlc1    %[g_vec0],       0x07(%[rgbbuf_ptr])              \n\t"
    "gssdrc1    %[g_vec0],       0x00(%[rgbbuf_ptr])              \n\t"
    "gssdlc1    %[g_vec1],       0x0f(%[rgbbuf_ptr])              \n\t"
    "gssdrc1    %[g_vec1],       0x08(%[rgbbuf_ptr])              \n\t"

    "daddiu     %[y_ptr],        %[y_ptr],          0x04          \n\t"
    "daddiu     %[u_ptr],        %[u_ptr],          0x02          \n\t"
    "daddiu     %[v_ptr],        %[v_ptr],          0x02          \n\t"
    "daddiu     %[rgbbuf_ptr],   %[rgbbuf_ptr],     0x10          \n\t"
    "daddi      %[width],        %[width],          -0x04         \n\t"
    "bnez       %[width],        1b                               \n\t"

    : [y]"=&f"(y),
      [u]"=&f"(u),                         [v]"=&f"(v),
      [b_vec0]"=&f"(b_vec[0]),             [b_vec1]"=&f"(b_vec[1]),
      [g_vec0]"=&f"(g_vec[0]),             [g_vec1]"=&f"(g_vec[1]),
      [r_vec0]"=&f"(r_vec[0]),             [r_vec1]"=&f"(r_vec[1]),
      [ub]"=&f"(ub),                       [ug]"=&f"(ug),
      [vg]"=&f"(vg),                       [vr]"=&f"(vr),
      [bb]"=&f"(bb),                       [bg]"=&f"(bg),
      [br]"=&f"(br),                       [yg]"=&f"(yg)
    : [y_ptr]"r"(src_y),                   [u_ptr]"r"(src_u),
      [v_ptr]"r"(src_v),                   [rgbbuf_ptr]"r"(rgb_buf),
      [yuvcons_ptr]"r"(yuvconstants),      [width]"r"(width),
      [zero]"f"(0x00),                     [alpha]"f"(-1),
      [six]"f"(0x6),                       [five]"f"(0x55),
      [mask]"f"(mask)
    : "memory"
  );
}

// 10 bit YUV to ARGB
void I210ToARGBRow_MMI(const uint16_t* src_y,
                       const uint16_t* src_u,
                       const uint16_t* src_v,
                       uint8_t* rgb_buf,
                       const struct YuvConstants* yuvconstants,
                       int width) {
  uint64_t y,u,v;
  uint64_t b_vec[2],g_vec[2],r_vec[2];
  uint64_t mask = 0xff00ff00ff00ff00ULL;
  uint64_t ub,ug,vg,vr,bb,bg,br,yg;

  __asm__ volatile(
    "ldc1       %[yg],           0xc0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[bb],           0x60(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ub],           0x00(%[yuvcons_ptr])             \n\t"
    "or         %[ub],           %[ub],             %[mask]       \n\t"
    "ldc1       %[bg],           0x80(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ug],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[ug],           %[ug],             %[zero]       \n\t"
    "pshufh     %[ug],           %[ug],             %[zero]       \n\t"
    "ldc1       %[vg],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vg],           %[vg],             %[zero]       \n\t"
    "pshufh     %[vg],           %[vg],             %[five]       \n\t"
    "ldc1       %[br],           0xa0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[vr],           0x40(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vr],           %[vr],             %[zero]       \n\t"
    "pshufh     %[vr],           %[vr],             %[five]       \n\t"
    "or         %[vr],           %[vr],             %[mask]       \n\t"

    "1:                                                           \n\t"
    "gsldlc1    %[y],            0x07(%[y_ptr])                   \n\t"
    "gsldrc1    %[y],            0x00(%[y_ptr])                   \n\t"
    "gslwlc1    %[u],            0x03(%[u_ptr])                   \n\t"
    "gslwrc1    %[u],            0x00(%[u_ptr])                   \n\t"
    "gslwlc1    %[v],            0x03(%[v_ptr])                   \n\t"
    "gslwrc1    %[v],            0x00(%[v_ptr])                   \n\t"

    "psllh      %[y],            %[y],              %[six]        \n\t"
    "pmulhuh    %[y],            %[y],              %[yg]         \n\t"

    "punpcklhw  %[u],            %[u],              %[u]          \n\t"
    "psrah      %[u],            %[u],              %[two]        \n\t"
    "punpcklhw  %[v],            %[v],              %[v]          \n\t"
    "psrah      %[v],            %[v],              %[two]        \n\t"
    "pminsh     %[u],            %[u],              %[mask1]      \n\t"
    "pminsh     %[v],            %[v],              %[mask1]      \n\t"

    "paddsh     %[b_vec0],       %[y],              %[bb]         \n\t"
    "pmullh     %[b_vec1],       %[u],              %[ub]         \n\t"
    "psubsh     %[b_vec0],       %[b_vec0],         %[b_vec1]     \n\t"

    "paddsh     %[g_vec0],       %[y],              %[bg]         \n\t"
    "pmullh     %[g_vec1],       %[u],              %[ug]         \n\t"
    "psubsh     %[g_vec0],       %[g_vec0],         %[g_vec1]     \n\t"
    "pmullh     %[g_vec1],       %[v],              %[vg]         \n\t"
    "psubsh     %[g_vec0],       %[g_vec0],         %[g_vec1]     \n\t"

    "paddsh     %[r_vec0],       %[y],              %[br]         \n\t"
    "pmullh     %[r_vec1],       %[v],              %[vr]         \n\t"
    "psubsh     %[r_vec0],       %[r_vec0],         %[r_vec1]     \n\t"

    "psrah      %[b_vec0],       %[b_vec0],         %[six]        \n\t"
    "psrah      %[g_vec0],       %[g_vec0],         %[six]        \n\t"
    "psrah      %[r_vec0],       %[r_vec0],         %[six]        \n\t"

    "packushb   %[r_vec0],       %[b_vec0],         %[r_vec0]     \n\t"
    "packushb   %[g_vec0],       %[g_vec0],         %[alpha]      \n\t"
    "punpcklwd  %[g_vec0],       %[g_vec0],         %[alpha]      \n\t"
    "punpcklbh  %[b_vec0],       %[r_vec0],         %[g_vec0]     \n\t"
    "punpckhbh  %[r_vec0],       %[r_vec0],         %[g_vec0]     \n\t"
    "punpcklhw  %[g_vec0],       %[b_vec0],         %[r_vec0]     \n\t"
    "punpckhhw  %[g_vec1],       %[b_vec0],         %[r_vec0]     \n\t"
    "gssdlc1    %[g_vec0],       0x07(%[rgbbuf_ptr])              \n\t"
    "gssdrc1    %[g_vec0],       0x00(%[rgbbuf_ptr])              \n\t"
    "gssdlc1    %[g_vec1],       0x0f(%[rgbbuf_ptr])              \n\t"
    "gssdrc1    %[g_vec1],       0x08(%[rgbbuf_ptr])              \n\t"

    "daddiu     %[y_ptr],        %[y_ptr],          0x08          \n\t"
    "daddiu     %[u_ptr],        %[u_ptr],          0x04          \n\t"
    "daddiu     %[v_ptr],        %[v_ptr],          0x04          \n\t"
    "daddiu     %[rgbbuf_ptr],   %[rgbbuf_ptr],     0x10          \n\t"
    "daddi      %[width],        %[width],          -0x04         \n\t"
    "bnez       %[width],        1b                               \n\t"

    : [y]"=&f"(y),
      [u]"=&f"(u),                         [v]"=&f"(v),
      [b_vec0]"=&f"(b_vec[0]),             [b_vec1]"=&f"(b_vec[1]),
      [g_vec0]"=&f"(g_vec[0]),             [g_vec1]"=&f"(g_vec[1]),
      [r_vec0]"=&f"(r_vec[0]),             [r_vec1]"=&f"(r_vec[1]),
      [ub]"=&f"(ub),                       [ug]"=&f"(ug),
      [vg]"=&f"(vg),                       [vr]"=&f"(vr),
      [bb]"=&f"(bb),                       [bg]"=&f"(bg),
      [br]"=&f"(br),                       [yg]"=&f"(yg)
    : [y_ptr]"r"(src_y),                   [u_ptr]"r"(src_u),
      [v_ptr]"r"(src_v),                   [rgbbuf_ptr]"r"(rgb_buf),
      [yuvcons_ptr]"r"(yuvconstants),      [width]"r"(width),
      [zero]"f"(0x00),                     [alpha]"f"(-1),
      [six]"f"(0x6),                       [five]"f"(0x55),
      [mask]"f"(mask),                     [two]"f"(0x02),
      [mask1]"f"(0x00ff00ff00ff00ff)
    : "memory"
  );
}

void I422AlphaToARGBRow_MMI(const uint8_t* src_y,
                            const uint8_t* src_u,
                            const uint8_t* src_v,
                            const uint8_t* src_a,
                            uint8_t* rgb_buf,
                            const struct YuvConstants* yuvconstants,
                            int width) {
  uint64_t y,u,v,a;
  uint64_t b_vec[2],g_vec[2],r_vec[2];
  uint64_t mask = 0xff00ff00ff00ff00ULL;
  uint64_t ub,ug,vg,vr,bb,bg,br,yg;

  __asm__ volatile(
    "ldc1       %[yg],           0xc0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[bb],           0x60(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ub],           0x00(%[yuvcons_ptr])             \n\t"
    "or         %[ub],           %[ub],             %[mask]       \n\t"
    "ldc1       %[bg],           0x80(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ug],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[ug],           %[ug],             %[zero]       \n\t"
    "pshufh     %[ug],           %[ug],             %[zero]       \n\t"
    "ldc1       %[vg],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vg],           %[vg],             %[zero]       \n\t"
    "pshufh     %[vg],           %[vg],             %[five]       \n\t"
    "ldc1       %[br],           0xa0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[vr],           0x40(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vr],           %[vr],             %[zero]       \n\t"
    "pshufh     %[vr],           %[vr],             %[five]       \n\t"
    "or         %[vr],           %[vr],             %[mask]       \n\t"

    "1:                                                           \n\t"
    "gslwlc1    %[y],            0x03(%[y_ptr])                   \n\t"
    "gslwrc1    %[y],            0x00(%[y_ptr])                   \n\t"
    "gslwlc1    %[u],            0x03(%[u_ptr])                   \n\t"
    "gslwrc1    %[u],            0x00(%[u_ptr])                   \n\t"
    "gslwlc1    %[v],            0x03(%[v_ptr])                   \n\t"
    "gslwrc1    %[v],            0x00(%[v_ptr])                   \n\t"
    "gslwlc1    %[a],            0x03(%[a_ptr])                   \n\t"
    "gslwrc1    %[a],            0x00(%[a_ptr])                   \n\t"

    "punpcklbh  %[y],            %[y],              %[y]          \n\t"//y*0x0101
    "pmulhuh    %[y],            %[y],              %[yg]         \n\t"//y1

    //u3|u2|u1|u0 --> u1|u1|u0|u0
    "punpcklbh  %[u],            %[u],              %[u]          \n\t"//u
    "punpcklbh  %[u],            %[u],              %[zero]       \n\t"
    "paddsh     %[b_vec0],       %[y],              %[bb]         \n\t"
    "pmullh     %[b_vec1],       %[u],              %[ub]         \n\t"
    "psubsh     %[b_vec0],       %[b_vec0],         %[b_vec1]     \n\t"
    "psrah      %[b_vec0],       %[b_vec0],         %[six]        \n\t"

    //v3|v2|v1|v0 --> v1|v1|v0|v0
    "punpcklbh  %[v],            %[v],              %[v]          \n\t"
    "punpcklbh  %[v],            %[v],              %[zero]       \n\t"
    "paddsh     %[g_vec0],       %[y],              %[bg]         \n\t"
    "pmullh     %[g_vec1],       %[u],              %[ug]         \n\t"
    "psubsh     %[g_vec0],       %[g_vec0],         %[g_vec1]     \n\t"
    "pmullh     %[g_vec1],       %[v],              %[vg]         \n\t"
    "psubsh     %[g_vec0],       %[g_vec0],         %[g_vec1]     \n\t"
    "psrah      %[g_vec0],       %[g_vec0],         %[six]        \n\t"

    "paddsh     %[r_vec0],       %[y],              %[br]         \n\t"
    "pmullh     %[r_vec1],       %[v],              %[vr]         \n\t"
    "psubsh     %[r_vec0],       %[r_vec0],         %[r_vec1]     \n\t"
    "psrah      %[r_vec0],       %[r_vec0],         %[six]        \n\t"

    "packushb   %[r_vec0],       %[b_vec0],         %[r_vec0]     \n\t"//rrrrbbbb
    "packushb   %[g_vec0],       %[g_vec0],         %[a]          \n\t"
    "punpcklwd  %[g_vec0],       %[g_vec0],         %[a]          \n\t"//aaaagggg
    "punpcklbh  %[b_vec0],       %[r_vec0],         %[g_vec0]     \n\t"
    "punpckhbh  %[r_vec0],       %[r_vec0],         %[g_vec0]     \n\t"
    "punpcklhw  %[g_vec0],       %[b_vec0],         %[r_vec0]     \n\t"
    "punpckhhw  %[g_vec1],       %[b_vec0],         %[r_vec0]     \n\t"
    "gssdlc1    %[g_vec0],       0x07(%[rgbbuf_ptr])              \n\t"
    "gssdrc1    %[g_vec0],       0x00(%[rgbbuf_ptr])              \n\t"
    "gssdlc1    %[g_vec1],       0x0f(%[rgbbuf_ptr])              \n\t"
    "gssdrc1    %[g_vec1],       0x08(%[rgbbuf_ptr])              \n\t"

    "daddiu     %[y_ptr],        %[y_ptr],          0x04          \n\t"
    "daddiu     %[a_ptr],        %[a_ptr],          0x04          \n\t"
    "daddiu     %[u_ptr],        %[u_ptr],          0x02          \n\t"
    "daddiu     %[v_ptr],        %[v_ptr],          0x02          \n\t"
    "daddiu     %[rgbbuf_ptr],   %[rgbbuf_ptr],     0x10          \n\t"
    "daddi      %[width],        %[width],          -0x04         \n\t"
    "bnez       %[width],        1b                               \n\t"

    : [y]"=&f"(y),                         [u]"=&f"(u),
      [v]"=&f"(v),                         [a]"=&f"(a),
      [b_vec0]"=&f"(b_vec[0]),             [b_vec1]"=&f"(b_vec[1]),
      [g_vec0]"=&f"(g_vec[0]),             [g_vec1]"=&f"(g_vec[1]),
      [r_vec0]"=&f"(r_vec[0]),             [r_vec1]"=&f"(r_vec[1]),
      [ub]"=&f"(ub),                       [ug]"=&f"(ug),
      [vg]"=&f"(vg),                       [vr]"=&f"(vr),
      [bb]"=&f"(bb),                       [bg]"=&f"(bg),
      [br]"=&f"(br),                       [yg]"=&f"(yg)
    : [y_ptr]"r"(src_y),                   [u_ptr]"r"(src_u),
      [v_ptr]"r"(src_v),                   [rgbbuf_ptr]"r"(rgb_buf),
      [yuvcons_ptr]"r"(yuvconstants),      [width]"r"(width),
      [a_ptr]"r"(src_a),                   [zero]"f"(0x00),
      [six]"f"(0x6),                       [five]"f"(0x55),
      [mask]"f"(mask)
    : "memory"
  );
}

void I422ToRGB24Row_MMI(const uint8_t* src_y,
                        const uint8_t* src_u,
                        const uint8_t* src_v,
                        uint8_t* rgb_buf,
                        const struct YuvConstants* yuvconstants,
                        int width) {
  uint64_t y,u,v;
  uint64_t b_vec[2],g_vec[2],r_vec[2];
  uint64_t mask = 0xff00ff00ff00ff00ULL;
  uint64_t ub,ug,vg,vr,bb,bg,br,yg;

  __asm__ volatile(
    "ldc1       %[yg],           0xc0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[bb],           0x60(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ub],           0x00(%[yuvcons_ptr])             \n\t"
    "or         %[ub],           %[ub],             %[mask]       \n\t"
    "ldc1       %[bg],           0x80(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ug],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[ug],           %[ug],             %[zero]       \n\t"
    "pshufh     %[ug],           %[ug],             %[zero]       \n\t"
    "ldc1       %[vg],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vg],           %[vg],             %[zero]       \n\t"
    "pshufh     %[vg],           %[vg],             %[five]       \n\t"
    "ldc1       %[br],           0xa0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[vr],           0x40(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vr],           %[vr],             %[zero]       \n\t"
    "pshufh     %[vr],           %[vr],             %[five]       \n\t"
    "or         %[vr],           %[vr],             %[mask]       \n\t"

    "1:                                                           \n\t"
    "gslwlc1    %[y],            0x03(%[y_ptr])                   \n\t"
    "gslwrc1    %[y],            0x00(%[y_ptr])                   \n\t"
    "gslwlc1    %[u],            0x03(%[u_ptr])                   \n\t"
    "gslwrc1    %[u],            0x00(%[u_ptr])                   \n\t"
    "gslwlc1    %[v],            0x03(%[v_ptr])                   \n\t"
    "gslwrc1    %[v],            0x00(%[v_ptr])                   \n\t"

    "punpcklbh  %[y],            %[y],              %[y]          \n\t"//y*0x0101
    "pmulhuh    %[y],            %[y],              %[yg]         \n\t"//y1

    //u3|u2|u1|u0 --> u1|u1|u0|u0
    "punpcklbh  %[u],            %[u],              %[u]          \n\t"//u
    "punpcklbh  %[u],            %[u],              %[zero]       \n\t"
    "paddsh     %[b_vec0],       %[y],              %[bb]         \n\t"
    "pmullh     %[b_vec1],       %[u],              %[ub]         \n\t"
    "psubsh     %[b_vec0],       %[b_vec0],         %[b_vec1]     \n\t"
    "psrah      %[b_vec0],       %[b_vec0],         %[six]        \n\t"

    //v3|v2|v1|v0 --> v1|v1|v0|v0
    "punpcklbh  %[v],            %[v],              %[v]          \n\t"
    "punpcklbh  %[v],            %[v],              %[zero]       \n\t"
    "paddsh     %[g_vec0],       %[y],              %[bg]         \n\t"
    "pmullh     %[g_vec1],       %[u],              %[ug]         \n\t"
    "psubsh     %[g_vec0],       %[g_vec0],         %[g_vec1]     \n\t"
    "pmullh     %[g_vec1],       %[v],              %[vg]         \n\t"
    "psubsh     %[g_vec0],       %[g_vec0],         %[g_vec1]     \n\t"
    "psrah      %[g_vec0],       %[g_vec0],         %[six]        \n\t"

    "paddsh     %[r_vec0],       %[y],              %[br]         \n\t"
    "pmullh     %[r_vec1],       %[v],              %[vr]         \n\t"
    "psubsh     %[r_vec0],       %[r_vec0],         %[r_vec1]     \n\t"
    "psrah      %[r_vec0],       %[r_vec0],         %[six]        \n\t"

    "packushb   %[r_vec0],       %[b_vec0],         %[r_vec0]     \n\t"
    "packushb   %[g_vec0],       %[g_vec0],         %[zero]       \n\t"
    "punpcklbh  %[b_vec0],       %[r_vec0],         %[g_vec0]     \n\t"
    "punpckhbh  %[r_vec0],       %[r_vec0],         %[g_vec0]     \n\t"
    "punpcklhw  %[g_vec0],       %[b_vec0],         %[r_vec0]     \n\t"
    "punpckhhw  %[g_vec1],       %[b_vec0],         %[r_vec0]     \n\t"

    "punpckhwd  %[r_vec0],       %[g_vec0],         %[g_vec0]     \n\t"
    "psllw      %[r_vec1],       %[r_vec0],         %[lmove1]     \n\t"
    "or         %[g_vec0],       %[g_vec0],         %[r_vec1]     \n\t"
    "psrlw      %[r_vec1],       %[r_vec0],         %[rmove1]     \n\t"
    "pextrh     %[r_vec1],       %[r_vec1],         %[zero]       \n\t"
    "pinsrh_2   %[g_vec0],       %[g_vec0],         %[r_vec1]     \n\t"
    "pextrh     %[r_vec1],       %[g_vec1],         %[zero]       \n\t"
    "pinsrh_3   %[g_vec0],       %[g_vec0],         %[r_vec1]     \n\t"
    "pextrh     %[r_vec1],       %[g_vec1],         %[one]        \n\t"
    "punpckhwd  %[g_vec1],       %[g_vec1],         %[g_vec1]     \n\t"
    "psllw      %[g_vec1],       %[g_vec1],         %[rmove1]     \n\t"
    "or         %[g_vec1],       %[g_vec1],         %[r_vec1]     \n\t"
    "gssdlc1    %[g_vec0],       0x07(%[rgbbuf_ptr])              \n\t"
    "gssdrc1    %[g_vec0],       0x00(%[rgbbuf_ptr])              \n\t"
    "gsswlc1    %[g_vec1],       0x0b(%[rgbbuf_ptr])              \n\t"
    "gsswrc1    %[g_vec1],       0x08(%[rgbbuf_ptr])              \n\t"


    "daddiu     %[y_ptr],        %[y_ptr],          0x04          \n\t"
    "daddiu     %[u_ptr],        %[u_ptr],          0x02          \n\t"
    "daddiu     %[v_ptr],        %[v_ptr],          0x02          \n\t"
    "daddiu     %[rgbbuf_ptr],   %[rgbbuf_ptr],     0x0c          \n\t"
    "daddi      %[width],        %[width],          -0x04         \n\t"
    "bnez       %[width],        1b                               \n\t"

    : [y]"=&f"(y),                         [u]"=&f"(u),
      [v]"=&f"(v),
      [b_vec0]"=&f"(b_vec[0]),             [b_vec1]"=&f"(b_vec[1]),
      [g_vec0]"=&f"(g_vec[0]),             [g_vec1]"=&f"(g_vec[1]),
      [r_vec0]"=&f"(r_vec[0]),             [r_vec1]"=&f"(r_vec[1]),
      [ub]"=&f"(ub),                       [ug]"=&f"(ug),
      [vg]"=&f"(vg),                       [vr]"=&f"(vr),
      [bb]"=&f"(bb),                       [bg]"=&f"(bg),
      [br]"=&f"(br),                       [yg]"=&f"(yg)
    : [y_ptr]"r"(src_y),                   [u_ptr]"r"(src_u),
      [v_ptr]"r"(src_v),                   [rgbbuf_ptr]"r"(rgb_buf),
      [yuvcons_ptr]"r"(yuvconstants),      [width]"r"(width),
      [zero]"f"(0x00),                     [five]"f"(0x55),
      [six]"f"(0x6),                       [mask]"f"(mask),
      [lmove1]"f"(0x18),                   [rmove1]"f"(0x8),
      [one]"f"(0x1)
    : "memory"
  );
}

void I422ToARGB4444Row_MMI(const uint8_t* src_y,
                           const uint8_t* src_u,
                           const uint8_t* src_v,
                           uint8_t* dst_argb4444,
                           const struct YuvConstants* yuvconstants,
                           int width) {
  uint64_t y, u, v;
  uint64_t b_vec, g_vec, r_vec, temp;
  uint64_t ub,ug,vg,vr,bb,bg,br,yg;

  __asm__ volatile(
    "ldc1       %[yg],           0xc0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[bb],           0x60(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ub],           0x00(%[yuvcons_ptr])             \n\t"
    "or         %[ub],           %[ub],             %[mask]       \n\t"
    "ldc1       %[bg],           0x80(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ug],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[ug],           %[ug],             %[zero]       \n\t"
    "pshufh     %[ug],           %[ug],             %[zero]       \n\t"
    "ldc1       %[vg],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vg],           %[vg],             %[zero]       \n\t"
    "pshufh     %[vg],           %[vg],             %[five]       \n\t"
    "ldc1       %[br],           0xa0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[vr],           0x40(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vr],           %[vr],             %[zero]       \n\t"
    "pshufh     %[vr],           %[vr],             %[five]       \n\t"
    "or         %[vr],           %[vr],             %[mask]       \n\t"

    "1:                                                           \n\t"
    "gslwlc1    %[y],            0x03(%[y_ptr])                   \n\t"
    "gslwrc1    %[y],            0x00(%[y_ptr])                   \n\t"
    "gslwlc1    %[u],            0x03(%[u_ptr])                   \n\t"
    "gslwrc1    %[u],            0x00(%[u_ptr])                   \n\t"
    "gslwlc1    %[v],            0x03(%[v_ptr])                   \n\t"
    "gslwrc1    %[v],            0x00(%[v_ptr])                   \n\t"

    "punpcklbh  %[y],            %[y],              %[y]          \n\t"//y*0x0101
    "pmulhuh    %[y],            %[y],              %[yg]         \n\t"//y1

    //u3|u2|u1|u0 --> u1|u1|u0|u0
    "punpcklbh  %[u],            %[u],              %[u]          \n\t"//u
    "punpcklbh  %[u],            %[u],              %[zero]       \n\t"
    "paddsh     %[b_vec],        %[y],              %[bb]         \n\t"
    "pmullh     %[temp],         %[u],              %[ub]         \n\t"
    "psubsh     %[b_vec],        %[b_vec],          %[temp]       \n\t"
    "psrah      %[b_vec],        %[b_vec],          %[six]        \n\t"

    //v3|v2|v1|v0 --> v1|v1|v0|v0
    "punpcklbh  %[v],            %[v],              %[v]          \n\t"
    "punpcklbh  %[v],            %[v],              %[zero]       \n\t"
    "paddsh     %[g_vec],        %[y],              %[bg]         \n\t"
    "pmullh     %[temp],         %[u],              %[ug]         \n\t"
    "psubsh     %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "pmullh     %[temp],         %[v],              %[vg]         \n\t"
    "psubsh     %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "psrah      %[g_vec],        %[g_vec],          %[six]        \n\t"

    "paddsh     %[r_vec],        %[y],              %[br]         \n\t"
    "pmullh     %[temp],         %[v],              %[vr]         \n\t"
    "psubsh     %[r_vec],        %[r_vec],          %[temp]       \n\t"
    "psrah      %[r_vec],        %[r_vec],          %[six]        \n\t"

    "packushb   %[r_vec],        %[b_vec],          %[r_vec]      \n\t"
    "packushb   %[g_vec],        %[g_vec],          %[zero]       \n\t"
    "punpcklwd  %[g_vec],        %[g_vec],          %[alpha]      \n\t"
    "punpcklbh  %[b_vec],        %[r_vec],          %[g_vec]      \n\t"
    "punpckhbh  %[r_vec],        %[r_vec],          %[g_vec]      \n\t"
    "punpcklhw  %[g_vec],        %[b_vec],          %[r_vec]      \n\t"
    "punpckhhw  %[b_vec],        %[b_vec],          %[r_vec]      \n\t"

    "and        %[g_vec],        %[g_vec],          %[mask1]      \n\t"
    "psrlw      %[g_vec],        %[g_vec],          %[four]       \n\t"
    "psrlw      %[r_vec],        %[g_vec],          %[four]       \n\t"
    "or         %[g_vec],        %[g_vec],          %[r_vec]      \n\t"
    "punpcklbh  %[r_vec],        %[alpha],          %[zero]       \n\t"
    "and        %[g_vec],        %[g_vec],          %[r_vec]      \n\t"

    "and        %[b_vec],        %[b_vec],          %[mask1]      \n\t"
    "psrlw      %[b_vec],        %[b_vec],          %[four]       \n\t"
    "psrlw      %[r_vec],        %[b_vec],          %[four]       \n\t"
    "or         %[b_vec],        %[b_vec],          %[r_vec]      \n\t"
    "punpcklbh  %[r_vec],        %[alpha],          %[zero]       \n\t"
    "and        %[b_vec],        %[b_vec],          %[r_vec]      \n\t"
    "packushb   %[g_vec],        %[g_vec],          %[b_vec]      \n\t"

    "gssdlc1    %[g_vec],        0x07(%[dst_argb4444])            \n\t"
    "gssdrc1    %[g_vec],        0x00(%[dst_argb4444])            \n\t"

    "daddiu     %[y_ptr],        %[y_ptr],          0x04          \n\t"
    "daddiu     %[u_ptr],        %[u_ptr],          0x02          \n\t"
    "daddiu     %[v_ptr],        %[v_ptr],          0x02          \n\t"
    "daddiu     %[dst_argb4444], %[dst_argb4444],   0x08          \n\t"
    "daddi      %[width],        %[width],          -0x04         \n\t"
    "bnez       %[width],        1b                               \n\t"

    : [y]"=&f"(y),                         [u]"=&f"(u),
      [v]"=&f"(v),
      [b_vec]"=&f"(b_vec),                 [g_vec]"=&f"(g_vec),
      [r_vec]"=&f"(r_vec),                 [temp]"=&f"(temp),
      [ub]"=&f"(ub),                       [ug]"=&f"(ug),
      [vg]"=&f"(vg),                       [vr]"=&f"(vr),
      [bb]"=&f"(bb),                       [bg]"=&f"(bg),
      [br]"=&f"(br),                       [yg]"=&f"(yg)
    : [y_ptr]"r"(src_y),                   [u_ptr]"r"(src_u),
      [v_ptr]"r"(src_v),                   [dst_argb4444]"r"(dst_argb4444),
      [yuvcons_ptr]"r"(yuvconstants),      [width]"r"(width),
      [zero]"f"(0x00),                     [five]"f"(0x55),
      [six]"f"(0x6),                       [mask]"f"(0xff00ff00ff00ff00),
      [four]"f"(0x4),                      [mask1]"f"(0xf0f0f0f0f0f0f0f0),
      [alpha]"f"(-1)
    : "memory"
  );
}

void I422ToARGB1555Row_MMI(const uint8_t* src_y,
                           const uint8_t* src_u,
                           const uint8_t* src_v,
                           uint8_t* dst_argb1555,
                           const struct YuvConstants* yuvconstants,
                           int width) {
  uint64_t y, u, v;
  uint64_t b_vec, g_vec, r_vec, temp;
  uint64_t ub,ug,vg,vr,bb,bg,br,yg;

  __asm__ volatile(
    "ldc1       %[yg],           0xc0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[bb],           0x60(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ub],           0x00(%[yuvcons_ptr])             \n\t"
    "or         %[ub],           %[ub],             %[mask1]      \n\t"
    "ldc1       %[bg],           0x80(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ug],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[ug],           %[ug],             %[zero]       \n\t"
    "pshufh     %[ug],           %[ug],             %[zero]       \n\t"
    "ldc1       %[vg],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vg],           %[vg],             %[zero]       \n\t"
    "pshufh     %[vg],           %[vg],             %[five]       \n\t"
    "ldc1       %[br],           0xa0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[vr],           0x40(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vr],           %[vr],             %[zero]       \n\t"
    "pshufh     %[vr],           %[vr],             %[five]       \n\t"
    "or         %[vr],           %[vr],             %[mask1]      \n\t"

    "1:                                                           \n\t"
    "gslwlc1    %[y],            0x03(%[y_ptr])                   \n\t"
    "gslwrc1    %[y],            0x00(%[y_ptr])                   \n\t"
    "gslwlc1    %[u],            0x03(%[u_ptr])                   \n\t"
    "gslwrc1    %[u],            0x00(%[u_ptr])                   \n\t"
    "gslwlc1    %[v],            0x03(%[v_ptr])                   \n\t"
    "gslwrc1    %[v],            0x00(%[v_ptr])                   \n\t"

    "punpcklbh  %[y],            %[y],              %[y]          \n\t"
    "pmulhuh    %[y],            %[y],              %[yg]         \n\t"

    //u3|u2|u1|u0 --> u1|u1|u0|u0
    "punpcklbh  %[u],            %[u],              %[u]          \n\t"
    "punpcklbh  %[u],            %[u],              %[zero]       \n\t"
    "paddsh     %[b_vec],        %[y],              %[bb]         \n\t"
    "pmullh     %[temp],         %[u],              %[ub]         \n\t"
    "psubsh     %[b_vec],        %[b_vec],          %[temp]       \n\t"
    "psrah      %[b_vec],        %[b_vec],          %[six]        \n\t"

    //v3|v2|v1|v0 --> v1|v1|v0|v0
    "punpcklbh  %[v],            %[v],              %[v]          \n\t"
    "punpcklbh  %[v],            %[v],              %[zero]       \n\t"
    "paddsh     %[g_vec],        %[y],              %[bg]         \n\t"
    "pmullh     %[temp],         %[u],              %[ug]         \n\t"
    "psubsh     %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "pmullh     %[temp],         %[v],              %[vg]         \n\t"
    "psubsh     %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "psrah      %[g_vec],        %[g_vec],          %[six]        \n\t"

    "paddsh     %[r_vec],        %[y],              %[br]         \n\t"
    "pmullh     %[temp],         %[v],              %[vr]         \n\t"
    "psubsh     %[r_vec],        %[r_vec],          %[temp]       \n\t"
    "psrah      %[r_vec],        %[r_vec],          %[six]        \n\t"

    "packushb   %[r_vec],        %[b_vec],          %[r_vec]      \n\t"
    "packushb   %[g_vec],        %[g_vec],          %[zero]       \n\t"
    "punpcklbh  %[b_vec],        %[r_vec],          %[g_vec]      \n\t"
    "punpckhbh  %[r_vec],        %[r_vec],          %[g_vec]      \n\t"
    "punpcklhw  %[g_vec],        %[b_vec],          %[r_vec]      \n\t"
    "punpckhhw  %[b_vec],        %[b_vec],          %[r_vec]      \n\t"

    "psrlw      %[temp],         %[g_vec],          %[three]      \n\t"
    "and        %[g_vec],        %[temp],           %[mask2]      \n\t"
    "psrlw      %[temp],         %[temp],           %[eight]      \n\t"
    "and        %[r_vec],        %[temp],           %[mask2]      \n\t"
    "psllw      %[r_vec],        %[r_vec],          %[lmove5]     \n\t"
    "or         %[g_vec],        %[g_vec],          %[r_vec]      \n\t"
    "psrlw      %[temp],         %[temp],           %[eight]      \n\t"
    "and        %[r_vec],        %[temp],           %[mask2]      \n\t"
    "psllw      %[r_vec],        %[r_vec],          %[lmove5]     \n\t"
    "psllw      %[r_vec],        %[r_vec],          %[lmove5]     \n\t"
    "or         %[g_vec],        %[g_vec],          %[r_vec]      \n\t"
    "or         %[g_vec],        %[g_vec],          %[mask3]      \n\t"

    "psrlw      %[temp],         %[b_vec],          %[three]      \n\t"
    "and        %[b_vec],        %[temp],           %[mask2]      \n\t"
    "psrlw      %[temp],         %[temp],           %[eight]      \n\t"
    "and        %[r_vec],        %[temp],           %[mask2]      \n\t"
    "psllw      %[r_vec],        %[r_vec],          %[lmove5]     \n\t"
    "or         %[b_vec],        %[b_vec],          %[r_vec]      \n\t"
    "psrlw      %[temp],         %[temp],           %[eight]      \n\t"
    "and        %[r_vec],        %[temp],           %[mask2]      \n\t"
    "psllw      %[r_vec],        %[r_vec],          %[lmove5]     \n\t"
    "psllw      %[r_vec],        %[r_vec],          %[lmove5]     \n\t"
    "or         %[b_vec],        %[b_vec],          %[r_vec]      \n\t"
    "or         %[b_vec],        %[b_vec],          %[mask3]      \n\t"

    "punpcklhw  %[r_vec],        %[g_vec],          %[b_vec]      \n\t"
    "punpckhhw  %[b_vec],        %[g_vec],          %[b_vec]      \n\t"
    "punpcklhw  %[g_vec],        %[r_vec],          %[b_vec]      \n\t"

    "gssdlc1    %[g_vec],        0x07(%[dst_argb1555])            \n\t"
    "gssdrc1    %[g_vec],        0x00(%[dst_argb1555])            \n\t"

    "daddiu     %[y_ptr],        %[y_ptr],          0x04          \n\t"
    "daddiu     %[u_ptr],        %[u_ptr],          0x02          \n\t"
    "daddiu     %[v_ptr],        %[v_ptr],          0x02          \n\t"
    "daddiu     %[dst_argb1555], %[dst_argb1555],   0x08          \n\t"
    "daddi      %[width],        %[width],          -0x04         \n\t"
    "bnez       %[width],        1b                               \n\t"

    : [y]"=&f"(y),                         [u]"=&f"(u),
      [v]"=&f"(v),
      [b_vec]"=&f"(b_vec),                 [g_vec]"=&f"(g_vec),
      [r_vec]"=&f"(r_vec),                 [temp]"=&f"(temp),
      [ub]"=&f"(ub),                       [ug]"=&f"(ug),
      [vg]"=&f"(vg),                       [vr]"=&f"(vr),
      [bb]"=&f"(bb),                       [bg]"=&f"(bg),
      [br]"=&f"(br),                       [yg]"=&f"(yg)
    : [y_ptr]"r"(src_y),                   [u_ptr]"r"(src_u),
      [v_ptr]"r"(src_v),                   [dst_argb1555]"r"(dst_argb1555),
      [yuvcons_ptr]"r"(yuvconstants),      [width]"r"(width),
      [zero]"f"(0x00),                     [five]"f"(0x55),
      [six]"f"(0x6),                       [mask1]"f"(0xff00ff00ff00ff00),
      [three]"f"(0x3),                     [mask2]"f"(0x1f0000001f),
      [eight]"f"(0x8),                     [mask3]"f"(0x800000008000),
      [lmove5]"f"(0x5)
    : "memory"
  );
}

void I422ToRGB565Row_MMI(const uint8_t* src_y,
                         const uint8_t* src_u,
                         const uint8_t* src_v,
                         uint8_t* dst_rgb565,
                         const struct YuvConstants* yuvconstants,
                         int width) {
  uint64_t y, u, v;
  uint64_t b_vec, g_vec, r_vec, temp;
  uint64_t ub,ug,vg,vr,bb,bg,br,yg;

  __asm__ volatile(
    "ldc1       %[yg],           0xc0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[bb],           0x60(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ub],           0x00(%[yuvcons_ptr])             \n\t"
    "or         %[ub],           %[ub],             %[mask1]      \n\t"
    "ldc1       %[bg],           0x80(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ug],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[ug],           %[ug],             %[zero]       \n\t"
    "pshufh     %[ug],           %[ug],             %[zero]       \n\t"
    "ldc1       %[vg],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vg],           %[vg],             %[zero]       \n\t"
    "pshufh     %[vg],           %[vg],             %[five]       \n\t"
    "ldc1       %[br],           0xa0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[vr],           0x40(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vr],           %[vr],             %[zero]       \n\t"
    "pshufh     %[vr],           %[vr],             %[five]       \n\t"
    "or         %[vr],           %[vr],             %[mask1]      \n\t"

    "1:                                                           \n\t"
    "gslwlc1    %[y],            0x03(%[y_ptr])                   \n\t"
    "gslwrc1    %[y],            0x00(%[y_ptr])                   \n\t"
    "gslwlc1    %[u],            0x03(%[u_ptr])                   \n\t"
    "gslwrc1    %[u],            0x00(%[u_ptr])                   \n\t"
    "gslwlc1    %[v],            0x03(%[v_ptr])                   \n\t"
    "gslwrc1    %[v],            0x00(%[v_ptr])                   \n\t"

    "punpcklbh  %[y],            %[y],              %[y]          \n\t"
    "pmulhuh    %[y],            %[y],              %[yg]         \n\t"

    //u3|u2|u1|u0 --> u1|u1|u0|u0
    "punpcklbh  %[u],            %[u],              %[u]          \n\t"
    "punpcklbh  %[u],            %[u],              %[zero]       \n\t"
    "paddsh     %[b_vec],        %[y],              %[bb]         \n\t"
    "pmullh     %[temp],         %[u],              %[ub]         \n\t"
    "psubsh     %[b_vec],        %[b_vec],          %[temp]       \n\t"
    "psrah      %[b_vec],        %[b_vec],          %[six]        \n\t"

    //v3|v2|v1|v0 --> v1|v1|v0|v0
    "punpcklbh  %[v],            %[v],              %[v]          \n\t"
    "punpcklbh  %[v],            %[v],              %[zero]       \n\t"
    "paddsh     %[g_vec],        %[y],              %[bg]         \n\t"
    "pmullh     %[temp],         %[u],              %[ug]         \n\t"
    "psubsh     %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "pmullh     %[temp],         %[v],              %[vg]         \n\t"
    "psubsh     %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "psrah      %[g_vec],        %[g_vec],          %[six]        \n\t"

    "paddsh     %[r_vec],        %[y],              %[br]         \n\t"
    "pmullh     %[temp],         %[v],              %[vr]         \n\t"
    "psubsh     %[r_vec],        %[r_vec],          %[temp]       \n\t"
    "psrah      %[r_vec],        %[r_vec],          %[six]        \n\t"

    "packushb   %[r_vec],        %[b_vec],          %[r_vec]      \n\t"
    "packushb   %[g_vec],        %[g_vec],          %[zero]       \n\t"
    "punpcklbh  %[b_vec],        %[r_vec],          %[g_vec]      \n\t"
    "punpckhbh  %[r_vec],        %[r_vec],          %[g_vec]      \n\t"
    "punpcklhw  %[g_vec],        %[b_vec],          %[r_vec]      \n\t"
    "punpckhhw  %[b_vec],        %[b_vec],          %[r_vec]      \n\t"

    "psrlh      %[temp],         %[g_vec],          %[three]      \n\t"
    "and        %[g_vec],        %[temp],           %[mask2]      \n\t"
    "psrlw      %[temp],         %[temp],           %[seven]      \n\t"
    "psrlw      %[r_vec],        %[mask1],          %[eight]      \n\t"
    "and        %[r_vec],        %[temp],           %[r_vec]      \n\t"
    "psllw      %[r_vec],        %[r_vec],          %[lmove5]     \n\t"
    "or         %[g_vec],        %[g_vec],          %[r_vec]      \n\t"
    "paddb      %[r_vec],        %[three],          %[six]        \n\t"
    "psrlw      %[temp],         %[temp],           %[r_vec]      \n\t"
    "and        %[r_vec],        %[temp],           %[mask2]      \n\t"
    "paddb      %[temp],         %[three],          %[eight]      \n\t"
    "psllw      %[r_vec],        %[r_vec],          %[temp]       \n\t"
    "or         %[g_vec],        %[g_vec],          %[r_vec]      \n\t"

    "psrlh      %[temp],         %[b_vec],          %[three]      \n\t"
    "and        %[b_vec],        %[temp],           %[mask2]      \n\t"
    "psrlw      %[temp],         %[temp],           %[seven]      \n\t"
    "psrlw      %[r_vec],        %[mask1],          %[eight]      \n\t"
    "and        %[r_vec],        %[temp],           %[r_vec]      \n\t"
    "psllw      %[r_vec],        %[r_vec],          %[lmove5]     \n\t"
    "or         %[b_vec],        %[b_vec],          %[r_vec]      \n\t"
    "paddb      %[r_vec],        %[three],          %[six]        \n\t"
    "psrlw      %[temp],         %[temp],           %[r_vec]      \n\t"
    "and        %[r_vec],        %[temp],           %[mask2]      \n\t"
    "paddb      %[temp],         %[three],          %[eight]      \n\t"
    "psllw      %[r_vec],        %[r_vec],          %[temp]       \n\t"
    "or         %[b_vec],        %[b_vec],          %[r_vec]      \n\t"

    "punpcklhw  %[r_vec],        %[g_vec],          %[b_vec]      \n\t"
    "punpckhhw  %[b_vec],        %[g_vec],          %[b_vec]      \n\t"
    "punpcklhw  %[g_vec],        %[r_vec],          %[b_vec]      \n\t"

    "gssdlc1    %[g_vec],        0x07(%[dst_rgb565])             \n\t"
    "gssdrc1    %[g_vec],        0x00(%[dst_rgb565])             \n\t"

    "daddiu     %[y_ptr],        %[y_ptr],          0x04          \n\t"
    "daddiu     %[u_ptr],        %[u_ptr],          0x02          \n\t"
    "daddiu     %[v_ptr],        %[v_ptr],          0x02          \n\t"
    "daddiu     %[dst_rgb565],   %[dst_rgb565],     0x08          \n\t"
    "daddi      %[width],        %[width],          -0x04         \n\t"
    "bnez       %[width],        1b                               \n\t"

    : [y]"=&f"(y),                         [u]"=&f"(u),
      [v]"=&f"(v),
      [b_vec]"=&f"(b_vec),                 [g_vec]"=&f"(g_vec),
      [r_vec]"=&f"(r_vec),                 [temp]"=&f"(temp),
      [ub]"=&f"(ub),                       [ug]"=&f"(ug),
      [vg]"=&f"(vg),                       [vr]"=&f"(vr),
      [bb]"=&f"(bb),                       [bg]"=&f"(bg),
      [br]"=&f"(br),                       [yg]"=&f"(yg)
    : [y_ptr]"r"(src_y),                   [u_ptr]"r"(src_u),
      [v_ptr]"r"(src_v),                   [dst_rgb565]"r"(dst_rgb565),
      [yuvcons_ptr]"r"(yuvconstants),      [width]"r"(width),
      [zero]"f"(0x00),                     [five]"f"(0x55),
      [six]"f"(0x6),                       [mask1]"f"(0xff00ff00ff00ff00),
      [three]"f"(0x3),                     [mask2]"f"(0x1f0000001f),
      [eight]"f"(0x8),                     [seven]"f"(0x7),
      [lmove5]"f"(0x5)
    : "memory"
  );
}

void NV12ToARGBRow_MMI(const uint8_t* src_y,
                       const uint8_t* src_uv,
                       uint8_t* rgb_buf,
                       const struct YuvConstants* yuvconstants,
                       int width) {
  uint64_t y, u, v;
  uint64_t b_vec, g_vec, r_vec, temp;
  uint64_t ub,ug,vg,vr,bb,bg,br,yg;

  __asm__ volatile(
    "ldc1       %[yg],           0xc0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[bb],           0x60(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ub],           0x00(%[yuvcons_ptr])             \n\t"
    "or         %[ub],           %[ub],             %[mask1]      \n\t"
    "ldc1       %[bg],           0x80(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ug],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[ug],           %[ug],             %[zero]       \n\t"
    "pshufh     %[ug],           %[ug],             %[zero]       \n\t"
    "ldc1       %[vg],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vg],           %[vg],             %[zero]       \n\t"
    "pshufh     %[vg],           %[vg],             %[five]       \n\t"
    "ldc1       %[br],           0xa0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[vr],           0x40(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vr],           %[vr],             %[zero]       \n\t"
    "pshufh     %[vr],           %[vr],             %[five]       \n\t"
    "or         %[vr],           %[vr],             %[mask1]      \n\t"

    "1:                                                           \n\t"
    "gslwlc1    %[y],            0x03(%[y_ptr])                   \n\t"
    "gslwrc1    %[y],            0x00(%[y_ptr])                   \n\t"
    "gslwlc1    %[u],            0x03(%[uv_ptr])                  \n\t"
    "gslwrc1    %[u],            0x00(%[uv_ptr])                  \n\t"
    "punpcklbh  %[u],            %[u],              %[zero]       \n\t"
    "pshufh     %[v],            %[u],              %[vshu]       \n\t"
    "pshufh     %[u],            %[u],              %[ushu]       \n\t"

    "punpcklbh  %[y],            %[y],              %[y]          \n\t"
    "pmulhuh    %[y],            %[y],              %[yg]         \n\t"

    "paddsh     %[b_vec],        %[y],              %[bb]         \n\t"
    "pmullh     %[temp],         %[u],              %[ub]         \n\t"
    "psubsh     %[b_vec],        %[b_vec],          %[temp]       \n\t"
    "psrah      %[b_vec],        %[b_vec],          %[six]        \n\t"

    "paddsh     %[g_vec],        %[y],              %[bg]         \n\t"
    "pmullh     %[temp],         %[u],              %[ug]         \n\t"
    "psubsh     %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "pmullh     %[temp],         %[v],              %[vg]         \n\t"
    "psubsh     %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "psrah      %[g_vec],        %[g_vec],          %[six]        \n\t"

    "paddsh     %[r_vec],        %[y],              %[br]         \n\t"
    "pmullh     %[temp],         %[v],              %[vr]         \n\t"
    "psubsh     %[r_vec],        %[r_vec],          %[temp]       \n\t"
    "psrah      %[r_vec],        %[r_vec],          %[six]        \n\t"

    "packushb   %[r_vec],        %[b_vec],          %[r_vec]      \n\t"
    "packushb   %[g_vec],        %[g_vec],          %[zero]       \n\t"
    "punpcklwd  %[g_vec],        %[g_vec],          %[alpha]      \n\t"
    "punpcklbh  %[b_vec],        %[r_vec],          %[g_vec]      \n\t"
    "punpckhbh  %[r_vec],        %[r_vec],          %[g_vec]      \n\t"
    "punpcklhw  %[g_vec],        %[b_vec],          %[r_vec]      \n\t"
    "punpckhhw  %[b_vec],        %[b_vec],          %[r_vec]      \n\t"

    "gssdlc1    %[g_vec],       0x07(%[rgbbuf_ptr])               \n\t"
    "gssdrc1    %[g_vec],       0x00(%[rgbbuf_ptr])               \n\t"
    "gssdlc1    %[b_vec],       0x0f(%[rgbbuf_ptr])               \n\t"
    "gssdrc1    %[b_vec],       0x08(%[rgbbuf_ptr])               \n\t"

    "daddiu     %[y_ptr],        %[y_ptr],          0x04          \n\t"
    "daddiu     %[uv_ptr],       %[uv_ptr],         0x04          \n\t"
    "daddiu     %[rgbbuf_ptr],   %[rgbbuf_ptr],     0x10          \n\t"
    "daddi      %[width],        %[width],          -0x04         \n\t"
    "bnez       %[width],        1b                               \n\t"

    : [y]"=&f"(y),                         [u]"=&f"(u),
      [v]"=&f"(v),
      [b_vec]"=&f"(b_vec),                 [g_vec]"=&f"(g_vec),
      [r_vec]"=&f"(r_vec),                 [temp]"=&f"(temp),
      [ub]"=&f"(ub),                       [ug]"=&f"(ug),
      [vg]"=&f"(vg),                       [vr]"=&f"(vr),
      [bb]"=&f"(bb),                       [bg]"=&f"(bg),
      [br]"=&f"(br),                       [yg]"=&f"(yg)
    : [y_ptr]"r"(src_y),                   [uv_ptr]"r"(src_uv),
      [rgbbuf_ptr]"r"(rgb_buf),
      [yuvcons_ptr]"r"(yuvconstants),      [width]"r"(width),
      [zero]"f"(0x00),                     [five]"f"(0x55),
      [six]"f"(0x6),                       [mask1]"f"(0xff00ff00ff00ff00),
      [ushu]"f"(0xA0),                     [vshu]"f"(0xf5),
      [alpha]"f"(-1)
    : "memory"
  );
}

void NV21ToARGBRow_MMI(const uint8_t* src_y,
                       const uint8_t* src_vu,
                       uint8_t* rgb_buf,
                       const struct YuvConstants* yuvconstants,
                       int width) {
  uint64_t y, u, v;
  uint64_t b_vec, g_vec, r_vec, temp;
  uint64_t ub,ug,vg,vr,bb,bg,br,yg;

  __asm__ volatile(
    "ldc1       %[yg],           0xc0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[bb],           0x60(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ub],           0x00(%[yuvcons_ptr])             \n\t"
    "or         %[ub],           %[ub],             %[mask1]      \n\t"
    "ldc1       %[bg],           0x80(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ug],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[ug],           %[ug],             %[zero]       \n\t"
    "pshufh     %[ug],           %[ug],             %[zero]       \n\t"
    "ldc1       %[vg],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vg],           %[vg],             %[zero]       \n\t"
    "pshufh     %[vg],           %[vg],             %[five]       \n\t"
    "ldc1       %[br],           0xa0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[vr],           0x40(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vr],           %[vr],             %[zero]       \n\t"
    "pshufh     %[vr],           %[vr],             %[five]       \n\t"
    "or         %[vr],           %[vr],             %[mask1]      \n\t"

    "1:                                                           \n\t"
    "gslwlc1    %[y],            0x03(%[y_ptr])                   \n\t"
    "gslwrc1    %[y],            0x00(%[y_ptr])                   \n\t"
    "gslwlc1    %[u],            0x03(%[vu_ptr])                  \n\t"
    "gslwrc1    %[u],            0x00(%[vu_ptr])                  \n\t"
    "punpcklbh  %[u],            %[u],              %[zero]       \n\t"
    "pshufh     %[v],            %[u],              %[ushu]       \n\t"
    "pshufh     %[u],            %[u],              %[vshu]       \n\t"

    "punpcklbh  %[y],            %[y],              %[y]          \n\t"
    "pmulhuh    %[y],            %[y],              %[yg]         \n\t"

    "paddsh     %[b_vec],        %[y],              %[bb]         \n\t"
    "pmullh     %[temp],         %[u],              %[ub]         \n\t"
    "psubsh     %[b_vec],        %[b_vec],          %[temp]       \n\t"
    "psrah      %[b_vec],        %[b_vec],          %[six]        \n\t"

    "paddsh     %[g_vec],        %[y],              %[bg]         \n\t"
    "pmullh     %[temp],         %[u],              %[ug]         \n\t"
    "psubsh     %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "pmullh     %[temp],         %[v],              %[vg]         \n\t"
    "psubsh     %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "psrah      %[g_vec],        %[g_vec],          %[six]        \n\t"

    "paddsh     %[r_vec],        %[y],              %[br]         \n\t"
    "pmullh     %[temp],         %[v],              %[vr]         \n\t"
    "psubsh     %[r_vec],        %[r_vec],          %[temp]       \n\t"
    "psrah      %[r_vec],        %[r_vec],          %[six]        \n\t"

    "packushb   %[r_vec],        %[b_vec],          %[r_vec]      \n\t"
    "packushb   %[g_vec],        %[g_vec],          %[zero]       \n\t"
    "punpcklwd  %[g_vec],        %[g_vec],          %[alpha]      \n\t"
    "punpcklbh  %[b_vec],        %[r_vec],          %[g_vec]      \n\t"
    "punpckhbh  %[r_vec],        %[r_vec],          %[g_vec]      \n\t"
    "punpcklhw  %[g_vec],        %[b_vec],          %[r_vec]      \n\t"
    "punpckhhw  %[b_vec],        %[b_vec],          %[r_vec]      \n\t"

    "gssdlc1    %[g_vec],       0x07(%[rgbbuf_ptr])               \n\t"
    "gssdrc1    %[g_vec],       0x00(%[rgbbuf_ptr])               \n\t"
    "gssdlc1    %[b_vec],       0x0f(%[rgbbuf_ptr])               \n\t"
    "gssdrc1    %[b_vec],       0x08(%[rgbbuf_ptr])               \n\t"

    "daddiu     %[y_ptr],        %[y_ptr],          0x04          \n\t"
    "daddiu     %[vu_ptr],       %[vu_ptr],         0x04          \n\t"
    "daddiu     %[rgbbuf_ptr],   %[rgbbuf_ptr],     0x10          \n\t"
    "daddi      %[width],        %[width],          -0x04         \n\t"
    "bnez       %[width],        1b                               \n\t"

    : [y]"=&f"(y),                         [u]"=&f"(u),
      [v]"=&f"(v),
      [b_vec]"=&f"(b_vec),                 [g_vec]"=&f"(g_vec),
      [r_vec]"=&f"(r_vec),                 [temp]"=&f"(temp),
      [ub]"=&f"(ub),                       [ug]"=&f"(ug),
      [vg]"=&f"(vg),                       [vr]"=&f"(vr),
      [bb]"=&f"(bb),                       [bg]"=&f"(bg),
      [br]"=&f"(br),                       [yg]"=&f"(yg)
    : [y_ptr]"r"(src_y),                   [vu_ptr]"r"(src_vu),
      [rgbbuf_ptr]"r"(rgb_buf),
      [yuvcons_ptr]"r"(yuvconstants),      [width]"r"(width),
      [zero]"f"(0x00),                     [five]"f"(0x55),
      [six]"f"(0x6),                       [mask1]"f"(0xff00ff00ff00ff00),
      [ushu]"f"(0xA0),                     [vshu]"f"(0xf5),
      [alpha]"f"(-1)
    : "memory"
  );
}

void NV12ToRGB24Row_MMI(const uint8_t* src_y,
                        const uint8_t* src_uv,
                        uint8_t* rgb_buf,
                        const struct YuvConstants* yuvconstants,
                        int width) {
  uint64_t y, u, v;
  uint64_t b_vec, g_vec, r_vec, temp;
  uint64_t ub,ug,vg,vr,bb,bg,br,yg;

  __asm__ volatile(
    "ldc1       %[yg],           0xc0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[bb],           0x60(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ub],           0x00(%[yuvcons_ptr])             \n\t"
    "or         %[ub],           %[ub],             %[mask1]      \n\t"
    "ldc1       %[bg],           0x80(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ug],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[ug],           %[ug],             %[zero]       \n\t"
    "pshufh     %[ug],           %[ug],             %[zero]       \n\t"
    "ldc1       %[vg],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vg],           %[vg],             %[zero]       \n\t"
    "pshufh     %[vg],           %[vg],             %[five]       \n\t"
    "ldc1       %[br],           0xa0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[vr],           0x40(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vr],           %[vr],             %[zero]       \n\t"
    "pshufh     %[vr],           %[vr],             %[five]       \n\t"
    "or         %[vr],           %[vr],             %[mask1]      \n\t"

    "1:                                                           \n\t"
    "gslwlc1    %[y],            0x03(%[y_ptr])                   \n\t"
    "gslwrc1    %[y],            0x00(%[y_ptr])                   \n\t"
    "gslwlc1    %[u],            0x03(%[uv_ptr])                  \n\t"
    "gslwrc1    %[u],            0x00(%[uv_ptr])                  \n\t"
    "punpcklbh  %[u],            %[u],              %[zero]       \n\t"
    "pshufh     %[v],            %[u],              %[vshu]       \n\t"
    "pshufh     %[u],            %[u],              %[ushu]       \n\t"

    "punpcklbh  %[y],            %[y],              %[y]          \n\t"
    "pmulhuh    %[y],            %[y],              %[yg]         \n\t"

    "paddsh     %[b_vec],        %[y],              %[bb]         \n\t"
    "pmullh     %[temp],         %[u],              %[ub]         \n\t"
    "psubsh     %[b_vec],        %[b_vec],          %[temp]       \n\t"
    "psrah      %[b_vec],        %[b_vec],          %[six]        \n\t"

    "paddsh     %[g_vec],        %[y],              %[bg]         \n\t"
    "pmullh     %[temp],         %[u],              %[ug]         \n\t"
    "psubsh     %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "pmullh     %[temp],         %[v],              %[vg]         \n\t"
    "psubsh     %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "psrah      %[g_vec],        %[g_vec],          %[six]        \n\t"

    "paddsh     %[r_vec],        %[y],              %[br]         \n\t"
    "pmullh     %[temp],         %[v],              %[vr]         \n\t"
    "psubsh     %[r_vec],        %[r_vec],          %[temp]       \n\t"
    "psrah      %[r_vec],        %[r_vec],          %[six]        \n\t"

    "packushb   %[r_vec],        %[b_vec],          %[r_vec]      \n\t"
    "packushb   %[g_vec],        %[g_vec],          %[zero]       \n\t"
    "punpcklbh  %[b_vec],        %[r_vec],          %[g_vec]      \n\t"
    "punpckhbh  %[r_vec],        %[r_vec],          %[g_vec]      \n\t"
    "punpcklhw  %[g_vec],        %[b_vec],          %[r_vec]      \n\t"
    "punpckhhw  %[b_vec],        %[b_vec],          %[r_vec]      \n\t"

    "punpckhwd  %[r_vec],        %[g_vec],          %[g_vec]      \n\t"
    "psllw      %[temp],         %[r_vec],          %[lmove1]     \n\t"
    "or         %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "psrlw      %[temp],         %[r_vec],          %[rmove1]     \n\t"
    "pextrh     %[temp],         %[temp],           %[zero]       \n\t"
    "pinsrh_2   %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "pextrh     %[temp],         %[b_vec],          %[zero]       \n\t"
    "pinsrh_3   %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "pextrh     %[temp],         %[b_vec],          %[one]        \n\t"
    "punpckhwd  %[b_vec],        %[b_vec],          %[b_vec]      \n\t"
    "psllw      %[b_vec],        %[b_vec],          %[rmove1]     \n\t"
    "or         %[b_vec],        %[b_vec],          %[temp]       \n\t"
    "gssdlc1    %[g_vec],        0x07(%[rgbbuf_ptr])              \n\t"
    "gssdrc1    %[g_vec],        0x00(%[rgbbuf_ptr])              \n\t"
    "gsswlc1    %[b_vec],        0x0b(%[rgbbuf_ptr])              \n\t"
    "gsswrc1    %[b_vec],        0x08(%[rgbbuf_ptr])              \n\t"

    "daddiu     %[y_ptr],        %[y_ptr],          0x04          \n\t"
    "daddiu     %[uv_ptr],       %[uv_ptr],         0x04          \n\t"
    "daddiu     %[rgbbuf_ptr],   %[rgbbuf_ptr],     0x0C          \n\t"
    "daddi      %[width],        %[width],          -0x04         \n\t"
    "bnez       %[width],        1b                               \n\t"

    : [y]"=&f"(y),                         [u]"=&f"(u),
      [v]"=&f"(v),
      [b_vec]"=&f"(b_vec),                 [g_vec]"=&f"(g_vec),
      [r_vec]"=&f"(r_vec),                 [temp]"=&f"(temp),
      [ub]"=&f"(ub),                       [ug]"=&f"(ug),
      [vg]"=&f"(vg),                       [vr]"=&f"(vr),
      [bb]"=&f"(bb),                       [bg]"=&f"(bg),
      [br]"=&f"(br),                       [yg]"=&f"(yg)
    : [y_ptr]"r"(src_y),                   [uv_ptr]"r"(src_uv),
      [rgbbuf_ptr]"r"(rgb_buf),
      [yuvcons_ptr]"r"(yuvconstants),      [width]"r"(width),
      [zero]"f"(0x00),                     [five]"f"(0x55),
      [six]"f"(0x6),                       [mask1]"f"(0xff00ff00ff00ff00),
      [ushu]"f"(0xA0),                     [vshu]"f"(0xf5),
      [alpha]"f"(-1),                      [lmove1]"f"(0x18),
      [one]"f"(0x1),                       [rmove1]"f"(0x8)
    : "memory"
  );
}

void NV21ToRGB24Row_MMI(const uint8_t* src_y,
                        const uint8_t* src_vu,
                        uint8_t* rgb_buf,
                        const struct YuvConstants* yuvconstants,
                        int width) {
  uint64_t y, u, v;
  uint64_t b_vec, g_vec, r_vec, temp;
  uint64_t ub,ug,vg,vr,bb,bg,br,yg;

  __asm__ volatile(
    "ldc1       %[yg],           0xc0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[bb],           0x60(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ub],           0x00(%[yuvcons_ptr])             \n\t"
    "or         %[ub],           %[ub],             %[mask1]      \n\t"
    "ldc1       %[bg],           0x80(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ug],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[ug],           %[ug],             %[zero]       \n\t"
    "pshufh     %[ug],           %[ug],             %[zero]       \n\t"
    "ldc1       %[vg],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vg],           %[vg],             %[zero]       \n\t"
    "pshufh     %[vg],           %[vg],             %[five]       \n\t"
    "ldc1       %[br],           0xa0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[vr],           0x40(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vr],           %[vr],             %[zero]       \n\t"
    "pshufh     %[vr],           %[vr],             %[five]       \n\t"
    "or         %[vr],           %[vr],             %[mask1]      \n\t"

    "1:                                                           \n\t"
    "gslwlc1    %[y],            0x03(%[y_ptr])                   \n\t"
    "gslwrc1    %[y],            0x00(%[y_ptr])                   \n\t"
    "gslwlc1    %[u],            0x03(%[vu_ptr])                  \n\t"
    "gslwrc1    %[u],            0x00(%[vu_ptr])                  \n\t"
    "punpcklbh  %[u],            %[u],              %[zero]       \n\t"
    "pshufh     %[v],            %[u],              %[ushu]       \n\t"
    "pshufh     %[u],            %[u],              %[vshu]       \n\t"

    "punpcklbh  %[y],            %[y],              %[y]          \n\t"
    "pmulhuh    %[y],            %[y],              %[yg]         \n\t"

    "paddsh     %[b_vec],        %[y],              %[bb]         \n\t"
    "pmullh     %[temp],         %[u],              %[ub]         \n\t"
    "psubsh     %[b_vec],        %[b_vec],          %[temp]       \n\t"
    "psrah      %[b_vec],        %[b_vec],          %[six]        \n\t"

    "paddsh     %[g_vec],        %[y],              %[bg]         \n\t"
    "pmullh     %[temp],         %[u],              %[ug]         \n\t"
    "psubsh     %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "pmullh     %[temp],         %[v],              %[vg]         \n\t"
    "psubsh     %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "psrah      %[g_vec],        %[g_vec],          %[six]        \n\t"

    "paddsh     %[r_vec],        %[y],              %[br]         \n\t"
    "pmullh     %[temp],         %[v],              %[vr]         \n\t"
    "psubsh     %[r_vec],        %[r_vec],          %[temp]       \n\t"
    "psrah      %[r_vec],        %[r_vec],          %[six]        \n\t"

    "packushb   %[r_vec],        %[b_vec],          %[r_vec]      \n\t"
    "packushb   %[g_vec],        %[g_vec],          %[zero]       \n\t"
    "punpcklbh  %[b_vec],        %[r_vec],          %[g_vec]      \n\t"
    "punpckhbh  %[r_vec],        %[r_vec],          %[g_vec]      \n\t"
    "punpcklhw  %[g_vec],        %[b_vec],          %[r_vec]      \n\t"
    "punpckhhw  %[b_vec],        %[b_vec],          %[r_vec]      \n\t"

    "punpckhwd  %[r_vec],        %[g_vec],          %[g_vec]      \n\t"
    "psllw      %[temp],         %[r_vec],          %[lmove1]     \n\t"
    "or         %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "psrlw      %[temp],         %[r_vec],          %[rmove1]     \n\t"
    "pextrh     %[temp],         %[temp],           %[zero]       \n\t"
    "pinsrh_2   %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "pextrh     %[temp],         %[b_vec],          %[zero]       \n\t"
    "pinsrh_3   %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "pextrh     %[temp],         %[b_vec],          %[one]        \n\t"
    "punpckhwd  %[b_vec],        %[b_vec],          %[b_vec]      \n\t"
    "psllw      %[b_vec],        %[b_vec],          %[rmove1]     \n\t"
    "or         %[b_vec],        %[b_vec],          %[temp]       \n\t"
    "gssdlc1    %[g_vec],        0x07(%[rgbbuf_ptr])              \n\t"
    "gssdrc1    %[g_vec],        0x00(%[rgbbuf_ptr])              \n\t"
    "gsswlc1    %[b_vec],        0x0b(%[rgbbuf_ptr])              \n\t"
    "gsswrc1    %[b_vec],        0x08(%[rgbbuf_ptr])              \n\t"

    "daddiu     %[y_ptr],        %[y_ptr],          0x04          \n\t"
    "daddiu     %[vu_ptr],       %[vu_ptr],         0x04          \n\t"
    "daddiu     %[rgbbuf_ptr],   %[rgbbuf_ptr],     0x0C          \n\t"
    "daddi      %[width],        %[width],          -0x04         \n\t"
    "bnez       %[width],        1b                               \n\t"

    : [y]"=&f"(y),                         [u]"=&f"(u),
      [v]"=&f"(v),
      [b_vec]"=&f"(b_vec),                 [g_vec]"=&f"(g_vec),
      [r_vec]"=&f"(r_vec),                 [temp]"=&f"(temp),
      [ub]"=&f"(ub),                       [ug]"=&f"(ug),
      [vg]"=&f"(vg),                       [vr]"=&f"(vr),
      [bb]"=&f"(bb),                       [bg]"=&f"(bg),
      [br]"=&f"(br),                       [yg]"=&f"(yg)
    : [y_ptr]"r"(src_y),                   [vu_ptr]"r"(src_vu),
      [rgbbuf_ptr]"r"(rgb_buf),
      [yuvcons_ptr]"r"(yuvconstants),      [width]"r"(width),
      [zero]"f"(0x00),                     [five]"f"(0x55),
      [six]"f"(0x6),                       [mask1]"f"(0xff00ff00ff00ff00),
      [ushu]"f"(0xA0),                     [vshu]"f"(0xf5),
      [lmove1]"f"(0x18),                   [rmove1]"f"(0x8),
      [one]"f"(0x1)
    : "memory"
  );
}

void NV12ToRGB565Row_MMI(const uint8_t* src_y,
                         const uint8_t* src_uv,
                         uint8_t* dst_rgb565,
                         const struct YuvConstants* yuvconstants,
                         int width) {
  uint64_t y, u, v;
  uint64_t b_vec, g_vec, r_vec, temp;
  uint64_t ub,ug,vg,vr,bb,bg,br,yg;

  __asm__ volatile(
    "ldc1       %[yg],           0xc0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[bb],           0x60(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ub],           0x00(%[yuvcons_ptr])             \n\t"
    "or         %[ub],           %[ub],             %[mask1]      \n\t"
    "ldc1       %[bg],           0x80(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ug],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[ug],           %[ug],             %[zero]       \n\t"
    "pshufh     %[ug],           %[ug],             %[zero]       \n\t"
    "ldc1       %[vg],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vg],           %[vg],             %[zero]       \n\t"
    "pshufh     %[vg],           %[vg],             %[five]       \n\t"
    "ldc1       %[br],           0xa0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[vr],           0x40(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vr],           %[vr],             %[zero]       \n\t"
    "pshufh     %[vr],           %[vr],             %[five]       \n\t"
    "or         %[vr],           %[vr],             %[mask1]      \n\t"

    "1:                                                           \n\t"
    "gslwlc1    %[y],            0x03(%[y_ptr])                   \n\t"
    "gslwrc1    %[y],            0x00(%[y_ptr])                   \n\t"
    "gslwlc1    %[u],            0x03(%[uv_ptr])                  \n\t"
    "gslwrc1    %[u],            0x00(%[uv_ptr])                  \n\t"
    "punpcklbh  %[u],            %[u],              %[zero]       \n\t"
    "pshufh     %[v],            %[u],              %[vshu]       \n\t"
    "pshufh     %[u],            %[u],              %[ushu]       \n\t"

    "punpcklbh  %[y],            %[y],              %[y]          \n\t"
    "pmulhuh    %[y],            %[y],              %[yg]         \n\t"

    "paddsh     %[b_vec],        %[y],              %[bb]         \n\t"
    "pmullh     %[temp],         %[u],              %[ub]         \n\t"
    "psubsh     %[b_vec],        %[b_vec],          %[temp]       \n\t"
    "psrah      %[b_vec],        %[b_vec],          %[six]        \n\t"

    "paddsh     %[g_vec],        %[y],              %[bg]         \n\t"
    "pmullh     %[temp],         %[u],              %[ug]         \n\t"
    "psubsh     %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "pmullh     %[temp],         %[v],              %[vg]         \n\t"
    "psubsh     %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "psrah      %[g_vec],        %[g_vec],          %[six]        \n\t"

    "paddsh     %[r_vec],        %[y],              %[br]         \n\t"
    "pmullh     %[temp],         %[v],              %[vr]         \n\t"
    "psubsh     %[r_vec],        %[r_vec],          %[temp]       \n\t"
    "psrah      %[r_vec],        %[r_vec],          %[six]        \n\t"

    "packushb   %[r_vec],        %[b_vec],          %[r_vec]      \n\t"
    "packushb   %[g_vec],        %[g_vec],          %[zero]       \n\t"
    "punpcklbh  %[b_vec],        %[r_vec],          %[g_vec]      \n\t"
    "punpckhbh  %[r_vec],        %[r_vec],          %[g_vec]      \n\t"
    "punpcklhw  %[g_vec],        %[b_vec],          %[r_vec]      \n\t"
    "punpckhhw  %[b_vec],        %[b_vec],          %[r_vec]      \n\t"

    "psrlh      %[temp],         %[g_vec],          %[three]      \n\t"
    "and        %[g_vec],        %[temp],           %[mask2]      \n\t"
    "psrlw      %[temp],         %[temp],           %[seven]      \n\t"
    "psrlw      %[r_vec],        %[mask1],          %[eight]      \n\t"
    "and        %[r_vec],        %[temp],           %[r_vec]      \n\t"
    "psubb      %[y],            %[eight],          %[three]      \n\t"//5
    "psllw      %[r_vec],        %[r_vec],          %[y]          \n\t"
    "or         %[g_vec],        %[g_vec],          %[r_vec]      \n\t"
    "paddb      %[r_vec],        %[three],          %[six]        \n\t"
    "psrlw      %[temp],         %[temp],           %[r_vec]      \n\t"
    "and        %[r_vec],        %[temp],           %[mask2]      \n\t"
    "paddb      %[temp],         %[three],          %[eight]      \n\t"
    "psllw      %[r_vec],        %[r_vec],          %[temp]       \n\t"
    "or         %[g_vec],        %[g_vec],          %[r_vec]      \n\t"

    "psrlh      %[temp],         %[b_vec],          %[three]      \n\t"
    "and        %[b_vec],        %[temp],           %[mask2]      \n\t"
    "psrlw      %[temp],         %[temp],           %[seven]      \n\t"
    "psrlw      %[r_vec],        %[mask1],          %[eight]      \n\t"
    "and        %[r_vec],        %[temp],           %[r_vec]      \n\t"
    "psubb      %[y],            %[eight],          %[three]      \n\t"//5
    "psllw      %[r_vec],        %[r_vec],          %[y]          \n\t"
    "or         %[b_vec],        %[b_vec],          %[r_vec]      \n\t"
    "paddb      %[r_vec],        %[three],          %[six]        \n\t"
    "psrlw      %[temp],         %[temp],           %[r_vec]      \n\t"
    "and        %[r_vec],        %[temp],           %[mask2]      \n\t"
    "paddb      %[temp],         %[three],          %[eight]      \n\t"
    "psllw      %[r_vec],        %[r_vec],          %[temp]       \n\t"
    "or         %[b_vec],        %[b_vec],          %[r_vec]      \n\t"

    "punpcklhw  %[r_vec],        %[g_vec],          %[b_vec]      \n\t"
    "punpckhhw  %[b_vec],        %[g_vec],          %[b_vec]      \n\t"
    "punpcklhw  %[g_vec],        %[r_vec],          %[b_vec]      \n\t"

    "gssdlc1    %[g_vec],        0x07(%[dst_rgb565])             \n\t"
    "gssdrc1    %[g_vec],        0x00(%[dst_rgb565])             \n\t"

    "daddiu     %[y_ptr],        %[y_ptr],          0x04          \n\t"
	"daddiu     %[uv_ptr],       %[uv_ptr],         0x04          \n\t"
    "daddiu     %[dst_rgb565],   %[dst_rgb565],     0x08          \n\t"
    "daddi      %[width],        %[width],          -0x04         \n\t"
    "bnez       %[width],        1b                               \n\t"

    : [y]"=&f"(y),                         [u]"=&f"(u),
      [v]"=&f"(v),
      [b_vec]"=&f"(b_vec),                 [g_vec]"=&f"(g_vec),
      [r_vec]"=&f"(r_vec),                 [temp]"=&f"(temp),
      [ub]"=&f"(ub),                       [ug]"=&f"(ug),
      [vg]"=&f"(vg),                       [vr]"=&f"(vr),
      [bb]"=&f"(bb),                       [bg]"=&f"(bg),
      [br]"=&f"(br),                       [yg]"=&f"(yg)
    : [y_ptr]"r"(src_y),                   [uv_ptr]"r"(src_uv),
      [dst_rgb565]"r"(dst_rgb565),
      [yuvcons_ptr]"r"(yuvconstants),      [width]"r"(width),
      [zero]"f"(0x00),                     [five]"f"(0x55),
      [six]"f"(0x6),                       [mask1]"f"(0xff00ff00ff00ff00),
      [ushu]"f"(0xA0),                     [vshu]"f"(0xf5),
      [three]"f"(0x3),                     [mask2]"f"(0x1f0000001f),
      [eight]"f"(0x8),                     [seven]"f"(0x7)
    : "memory"
  );
}

void YUY2ToARGBRow_MMI(const uint8_t* src_yuy2,
                       uint8_t* rgb_buf,
                       const struct YuvConstants* yuvconstants,
                       int width) {
  uint64_t y, u, v;
  uint64_t b_vec, g_vec, r_vec, temp;
  uint64_t ub,ug,vg,vr,bb,bg,br,yg;

  __asm__ volatile(
    "ldc1       %[yg],           0xc0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[bb],           0x60(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ub],           0x00(%[yuvcons_ptr])             \n\t"
    "or         %[ub],           %[ub],             %[mask1]      \n\t"
    "ldc1       %[bg],           0x80(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ug],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[ug],           %[ug],             %[zero]       \n\t"
    "pshufh     %[ug],           %[ug],             %[zero]       \n\t"
    "ldc1       %[vg],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vg],           %[vg],             %[zero]       \n\t"
    "pshufh     %[vg],           %[vg],             %[five]       \n\t"
    "ldc1       %[br],           0xa0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[vr],           0x40(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vr],           %[vr],             %[zero]       \n\t"
    "pshufh     %[vr],           %[vr],             %[five]       \n\t"
    "or         %[vr],           %[vr],             %[mask1]      \n\t"

    "1:                                                           \n\t"
    "gsldlc1    %[y],            0x07(%[yuy2_ptr])                \n\t"
    "gsldrc1    %[y],            0x00(%[yuy2_ptr])                \n\t"
    "psrlh      %[temp],         %[y],              %[eight]      \n\t"
    "pshufh     %[u],            %[temp],           %[ushu]       \n\t"
    "pshufh     %[v],            %[temp],           %[vshu]       \n\t"

    "psrlh      %[temp],         %[mask1],          %[eight]      \n\t"
    "and        %[y],            %[y],              %[temp]       \n\t"
    "psllh      %[temp],         %[y],              %[eight]      \n\t"
    "or         %[y],            %[y],              %[temp]       \n\t"
    "pmulhuh    %[y],            %[y],              %[yg]         \n\t"

    "paddsh     %[b_vec],        %[y],              %[bb]         \n\t"
    "pmullh     %[temp],         %[u],              %[ub]         \n\t"
    "psubsh     %[b_vec],        %[b_vec],          %[temp]       \n\t"
    "psrah      %[b_vec],        %[b_vec],          %[six]        \n\t"

    "paddsh     %[g_vec],        %[y],              %[bg]         \n\t"
    "pmullh     %[temp],         %[u],              %[ug]         \n\t"
    "psubsh     %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "pmullh     %[temp],         %[v],              %[vg]         \n\t"
    "psubsh     %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "psrah      %[g_vec],        %[g_vec],          %[six]        \n\t"

    "paddsh     %[r_vec],        %[y],              %[br]         \n\t"
    "pmullh     %[temp],         %[v],              %[vr]         \n\t"
    "psubsh     %[r_vec],        %[r_vec],          %[temp]       \n\t"
    "psrah      %[r_vec],        %[r_vec],          %[six]        \n\t"

    "packushb   %[r_vec],        %[b_vec],          %[r_vec]      \n\t"
    "packushb   %[g_vec],        %[g_vec],          %[zero]       \n\t"
    "punpcklwd  %[g_vec],        %[g_vec],          %[alpha]      \n\t"
    "punpcklbh  %[b_vec],        %[r_vec],          %[g_vec]      \n\t"
    "punpckhbh  %[r_vec],        %[r_vec],          %[g_vec]      \n\t"
    "punpcklhw  %[g_vec],        %[b_vec],          %[r_vec]      \n\t"
    "punpckhhw  %[b_vec],        %[b_vec],          %[r_vec]      \n\t"

    "gssdlc1    %[g_vec],        0x07(%[rgbbuf_ptr])              \n\t"
    "gssdrc1    %[g_vec],        0x00(%[rgbbuf_ptr])              \n\t"
    "gssdlc1    %[b_vec],        0x0f(%[rgbbuf_ptr])              \n\t"
    "gssdrc1    %[b_vec],        0x08(%[rgbbuf_ptr])              \n\t"

    "daddiu     %[yuy2_ptr],     %[yuy2_ptr],       0x08          \n\t"
    "daddiu     %[rgbbuf_ptr],   %[rgbbuf_ptr],     0x10          \n\t"
    "daddi      %[width],        %[width],          -0x04         \n\t"
    "bnez       %[width],        1b                               \n\t"

    : [y]"=&f"(y),                         [u]"=&f"(u),
      [v]"=&f"(v),
      [b_vec]"=&f"(b_vec),                 [g_vec]"=&f"(g_vec),
      [r_vec]"=&f"(r_vec),                 [temp]"=&f"(temp),
      [ub]"=&f"(ub),                       [ug]"=&f"(ug),
      [vg]"=&f"(vg),                       [vr]"=&f"(vr),
      [bb]"=&f"(bb),                       [bg]"=&f"(bg),
      [br]"=&f"(br),                       [yg]"=&f"(yg)
    : [yuy2_ptr]"r"(src_yuy2),             [rgbbuf_ptr]"r"(rgb_buf),
      [yuvcons_ptr]"r"(yuvconstants),      [width]"r"(width),
      [zero]"f"(0x00),                     [five]"f"(0x55),
      [six]"f"(0x6),                       [mask1]"f"(0xff00ff00ff00ff00),
      [ushu]"f"(0xA0),                     [vshu]"f"(0xf5),
      [alpha]"f"(-1),                      [eight]"f"(0x8)
    : "memory"
  );
}

void UYVYToARGBRow_MMI(const uint8_t* src_uyvy,
                       uint8_t* rgb_buf,
                       const struct YuvConstants* yuvconstants,
                       int width) {
  uint64_t y, u, v;
  uint64_t b_vec, g_vec, r_vec, temp;
  uint64_t ub,ug,vg,vr,bb,bg,br,yg;

  __asm__ volatile(
    "ldc1       %[yg],           0xc0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[bb],           0x60(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ub],           0x00(%[yuvcons_ptr])             \n\t"
    "or         %[ub],           %[ub],             %[mask1]      \n\t"
    "ldc1       %[bg],           0x80(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ug],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[ug],           %[ug],             %[zero]       \n\t"
    "pshufh     %[ug],           %[ug],             %[zero]       \n\t"
    "ldc1       %[vg],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vg],           %[vg],             %[zero]       \n\t"
    "pshufh     %[vg],           %[vg],             %[five]       \n\t"
    "ldc1       %[br],           0xa0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[vr],           0x40(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vr],           %[vr],             %[zero]       \n\t"
    "pshufh     %[vr],           %[vr],             %[five]       \n\t"
    "or         %[vr],           %[vr],             %[mask1]      \n\t"

    "1:                                                           \n\t"
    "gsldlc1    %[y],            0x07(%[uyvy_ptr])                \n\t"
    "gsldrc1    %[y],            0x00(%[uyvy_ptr])                \n\t"
    "psrlh      %[temp],         %[mask1],          %[eight]      \n\t"
    "and        %[temp],         %[y],              %[temp]       \n\t"
    "pshufh     %[u],            %[temp],           %[ushu]       \n\t"
    "pshufh     %[v],            %[temp],           %[vshu]       \n\t"

    "psrlh      %[y],            %[y],              %[eight]      \n\t"
    "psllh      %[temp],         %[y],              %[eight]      \n\t"
    "or         %[y],            %[y],              %[temp]       \n\t"
    "pmulhuh    %[y],            %[y],              %[yg]         \n\t"

    "paddsh     %[b_vec],        %[y],              %[bb]         \n\t"
    "pmullh     %[temp],         %[u],              %[ub]         \n\t"
    "psubsh     %[b_vec],        %[b_vec],          %[temp]       \n\t"
    "psrah      %[b_vec],        %[b_vec],          %[six]        \n\t"

    "paddsh     %[g_vec],        %[y],              %[bg]         \n\t"
    "pmullh     %[temp],         %[u],              %[ug]         \n\t"
    "psubsh     %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "pmullh     %[temp],         %[v],              %[vg]         \n\t"
    "psubsh     %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "psrah      %[g_vec],        %[g_vec],          %[six]        \n\t"

    "paddsh     %[r_vec],        %[y],              %[br]         \n\t"
    "pmullh     %[temp],         %[v],              %[vr]         \n\t"
    "psubsh     %[r_vec],        %[r_vec],          %[temp]       \n\t"
    "psrah      %[r_vec],        %[r_vec],          %[six]        \n\t"

    "packushb   %[r_vec],        %[b_vec],          %[r_vec]      \n\t"
    "packushb   %[g_vec],        %[g_vec],          %[zero]       \n\t"
    "punpcklwd  %[g_vec],        %[g_vec],          %[alpha]      \n\t"
    "punpcklbh  %[b_vec],        %[r_vec],          %[g_vec]      \n\t"
    "punpckhbh  %[r_vec],        %[r_vec],          %[g_vec]      \n\t"
    "punpcklhw  %[g_vec],        %[b_vec],          %[r_vec]      \n\t"
    "punpckhhw  %[b_vec],        %[b_vec],          %[r_vec]      \n\t"

    "gssdlc1    %[g_vec],        0x07(%[rgbbuf_ptr])              \n\t"
    "gssdrc1    %[g_vec],        0x00(%[rgbbuf_ptr])              \n\t"
    "gssdlc1    %[b_vec],        0x0f(%[rgbbuf_ptr])              \n\t"
    "gssdrc1    %[b_vec],        0x08(%[rgbbuf_ptr])              \n\t"

    "daddiu     %[uyvy_ptr],     %[uyvy_ptr],       0x08          \n\t"
    "daddiu     %[rgbbuf_ptr],   %[rgbbuf_ptr],     0x10          \n\t"
    "daddi      %[width],        %[width],          -0x04         \n\t"
    "bnez       %[width],        1b                               \n\t"

    : [y]"=&f"(y),                         [u]"=&f"(u),
      [v]"=&f"(v),
      [b_vec]"=&f"(b_vec),                 [g_vec]"=&f"(g_vec),
      [r_vec]"=&f"(r_vec),                 [temp]"=&f"(temp),
      [ub]"=&f"(ub),                       [ug]"=&f"(ug),
      [vg]"=&f"(vg),                       [vr]"=&f"(vr),
      [bb]"=&f"(bb),                       [bg]"=&f"(bg),
      [br]"=&f"(br),                       [yg]"=&f"(yg)
    : [uyvy_ptr]"r"(src_uyvy),             [rgbbuf_ptr]"r"(rgb_buf),
      [yuvcons_ptr]"r"(yuvconstants),      [width]"r"(width),
      [zero]"f"(0x00),                     [five]"f"(0x55),
      [six]"f"(0x6),                       [mask1]"f"(0xff00ff00ff00ff00),
      [ushu]"f"(0xA0),                     [vshu]"f"(0xf5),
      [alpha]"f"(-1),                      [eight]"f"(0x8)
    : "memory"
  );
}

void I422ToRGBARow_MMI(const uint8_t* src_y,
                       const uint8_t* src_u,
                       const uint8_t* src_v,
                       uint8_t* rgb_buf,
                       const struct YuvConstants* yuvconstants,
                       int width) {
  uint64_t y, u, v;
  uint64_t b_vec, g_vec, r_vec, temp;
  uint64_t ub,ug,vg,vr,bb,bg,br,yg;

  __asm__ volatile(
    "ldc1       %[yg],           0xc0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[bb],           0x60(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ub],           0x00(%[yuvcons_ptr])             \n\t"
    "or         %[ub],           %[ub],             %[mask1]      \n\t"
    "ldc1       %[bg],           0x80(%[yuvcons_ptr])             \n\t"
    "ldc1       %[ug],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[ug],           %[ug],             %[zero]       \n\t"
    "pshufh     %[ug],           %[ug],             %[zero]       \n\t"
    "ldc1       %[vg],           0x20(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vg],           %[vg],             %[zero]       \n\t"
    "pshufh     %[vg],           %[vg],             %[five]       \n\t"
    "ldc1       %[br],           0xa0(%[yuvcons_ptr])             \n\t"
    "ldc1       %[vr],           0x40(%[yuvcons_ptr])             \n\t"
    "punpcklbh  %[vr],           %[vr],             %[zero]       \n\t"
    "pshufh     %[vr],           %[vr],             %[five]       \n\t"
    "or         %[vr],           %[vr],             %[mask1]      \n\t"

    "1:                                                           \n\t"
    "gslwlc1    %[y],            0x03(%[y_ptr])                   \n\t"
    "gslwrc1    %[y],            0x00(%[y_ptr])                   \n\t"
    "gslwlc1    %[u],            0x03(%[u_ptr])                   \n\t"
    "gslwrc1    %[u],            0x00(%[u_ptr])                   \n\t"
    "gslwlc1    %[v],            0x03(%[v_ptr])                   \n\t"
    "gslwrc1    %[v],            0x00(%[v_ptr])                   \n\t"

    "punpcklbh  %[y],            %[y],              %[y]          \n\t"
    "pmulhuh    %[y],            %[y],              %[yg]         \n\t"

    "punpcklbh  %[u],            %[u],              %[u]          \n\t"
    "punpcklbh  %[u],            %[u],              %[zero]       \n\t"
    "paddsh     %[b_vec],        %[y],              %[bb]         \n\t"
    "pmullh     %[temp],         %[u],              %[ub]         \n\t"
    "psubsh     %[b_vec],        %[b_vec],          %[temp]       \n\t"
    "psrah      %[b_vec],        %[b_vec],          %[six]        \n\t"

    "punpcklbh  %[v],            %[v],              %[v]          \n\t"
    "punpcklbh  %[v],            %[v],              %[zero]       \n\t"
    "paddsh     %[g_vec],        %[y],              %[bg]         \n\t"
    "pmullh     %[temp],         %[u],              %[ug]         \n\t"
    "psubsh     %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "pmullh     %[temp],         %[v],              %[vg]         \n\t"
    "psubsh     %[g_vec],        %[g_vec],          %[temp]       \n\t"
    "psrah      %[g_vec],        %[g_vec],          %[six]        \n\t"

    "paddsh     %[r_vec],        %[y],              %[br]         \n\t"
    "pmullh     %[temp],         %[v],              %[vr]         \n\t"
    "psubsh     %[r_vec],        %[r_vec],          %[temp]       \n\t"
    "psrah      %[r_vec],        %[r_vec],          %[six]        \n\t"

    "packushb   %[r_vec],        %[b_vec],          %[r_vec]      \n\t"
    "packushb   %[g_vec],        %[g_vec],          %[zero]       \n\t"
    "punpcklwd  %[g_vec],        %[alpha],          %[g_vec]      \n\t"
    "punpcklbh  %[b_vec],        %[g_vec],          %[r_vec]      \n\t"
    "punpckhbh  %[r_vec],        %[g_vec],          %[r_vec]      \n\t"
    "punpcklhw  %[g_vec],        %[b_vec],          %[r_vec]      \n\t"
    "punpckhhw  %[b_vec],        %[b_vec],          %[r_vec]      \n\t"

    "gssdlc1    %[g_vec],       0x07(%[rgbbuf_ptr])               \n\t"
    "gssdrc1    %[g_vec],       0x00(%[rgbbuf_ptr])               \n\t"
    "gssdlc1    %[b_vec],       0x0f(%[rgbbuf_ptr])               \n\t"
    "gssdrc1    %[b_vec],       0x08(%[rgbbuf_ptr])               \n\t"

    "daddiu     %[y_ptr],        %[y_ptr],          0x04          \n\t"
    "daddiu     %[u_ptr],        %[u_ptr],          0x02          \n\t"
    "daddiu     %[v_ptr],        %[v_ptr],          0x02          \n\t"
    "daddiu     %[rgbbuf_ptr],   %[rgbbuf_ptr],     0x10          \n\t"
    "daddi      %[width],        %[width],          -0x04         \n\t"
    "bnez       %[width],        1b                               \n\t"

    : [y]"=&f"(y),                         [u]"=&f"(u),
      [v]"=&f"(v),
      [b_vec]"=&f"(b_vec),                 [g_vec]"=&f"(g_vec),
      [r_vec]"=&f"(r_vec),                 [temp]"=&f"(temp),
      [ub]"=&f"(ub),                       [ug]"=&f"(ug),
      [vg]"=&f"(vg),                       [vr]"=&f"(vr),
      [bb]"=&f"(bb),                       [bg]"=&f"(bg),
      [br]"=&f"(br),                       [yg]"=&f"(yg)
    : [y_ptr]"r"(src_y),                   [u_ptr]"r"(src_u),
      [v_ptr]"r"(src_v),                   [rgbbuf_ptr]"r"(rgb_buf),
      [yuvcons_ptr]"r"(yuvconstants),      [width]"r"(width),
      [zero]"f"(0x00),                     [five]"f"(0x55),
      [six]"f"(0x6),                       [mask1]"f"(0xff00ff00ff00ff00),
      [alpha]"f"(-1)
    : "memory"
  );
}

void ARGBSetRow_MMI(uint8_t* dst_argb, uint32_t v32, int width) {
  __asm__ volatile (
    "punpcklwd  %[v32],          %[v32],            %[v32]        \n\t"
    "1:                                                           \n\t"
    "gssdlc1    %[v32],          0x07(%[dst_ptr])                 \n\t"
    "gssdrc1    %[v32],          0x00(%[dst_ptr])                 \n\t"
    "gssdlc1    %[v32],          0x0f(%[dst_ptr])                 \n\t"
    "gssdrc1    %[v32],          0x08(%[dst_ptr])                 \n\t"

    "daddi      %[width],        %[width],         -0x04          \n\t"
    "daddiu     %[dst_ptr],      %[dst_ptr],        0x10          \n\t"
    "bnez       %[width],        1b                               \n\t"
    : [v32]"+&f"(v32)
    : [dst_ptr]"r"(dst_argb),           [width]"r"(width)
    : "memory"
  );
}
// clang-format on

// 10 bit YUV to ARGB
#endif  // !defined(LIBYUV_DISABLE_MMI) && defined(_MIPS_ARCH_LOONGSON3A)

#ifdef __cplusplus
}  // extern "C"
}  // namespace libyuv
#endif
