/*
 *  Copyright 2013 The LibYuv Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS. All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "libyuv/scale.h"

#include <assert.h>
#include <string.h>

#include "libyuv/cpu_id.h"
#include "libyuv/planar_functions.h"  // For CopyARGB
#include "libyuv/row.h"
#include "libyuv/scale_row.h"

#ifdef __cplusplus
namespace libyuv {
extern "C" {
#endif

// This module is for Mips MMI.
#if !defined(LIBYUV_DISABLE_MMI) && defined(_MIPS_ARCH_LOONGSON3A)

// clang-format off

// CPU agnostic row functions
void ScaleRowDown2_MMI(const uint8_t* src_ptr,
                       ptrdiff_t src_stride,
                       uint8_t* dst,
                       int dst_width) {
  (void)src_stride;

  uint64_t src0, src1, dest;
  const uint64_t shift = 0x8ULL;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldrc1    %[src0],         0x00(%[src_ptr])                 \n\t"
      "gsldlc1    %[src0],         0x07(%[src_ptr])                 \n\t"
      "psrlh      %[src0],         %[src0],           %[shift]      \n\t"

      "gsldrc1    %[src1],         0x08(%[src_ptr])                 \n\t"
      "gsldlc1    %[src1],         0x0f(%[src_ptr])                 \n\t"
      "psrlh      %[src1],         %[src1],           %[shift]      \n\t"

      "packushb   %[dest],         %[src0],           %[src1]       \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x10          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x08          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src0] "=&f"(src0), [src1] "=&f"(src1), [dest] "=&f"(dest)
      : [src_ptr] "r"(src_ptr), [dst_ptr] "r"(dst), [width] "r"(dst_width),
        [shift] "f"(shift)
      : "memory");
}

void ScaleRowDown2Linear_MMI(const uint8_t* src_ptr,
                             ptrdiff_t src_stride,
                             uint8_t* dst,
                             int dst_width) {
  (void)src_stride;

  uint64_t src0, src1;
  uint64_t dest, dest0, dest1;

  const uint64_t mask = 0x00ff00ff00ff00ffULL;
  const uint64_t shift = 0x8ULL;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldrc1    %[src0],          0x00(%[src_ptr])                \n\t"
      "gsldlc1    %[src0],          0x07(%[src_ptr])                \n\t"
      "and        %[dest0],         %[src0],          %[mask]       \n\t"
      "gsldrc1    %[src1],          0x08(%[src_ptr])                \n\t"
      "gsldlc1    %[src1],          0x0f(%[src_ptr])                \n\t"
      "and        %[dest1],         %[src1],          %[mask]       \n\t"
      "packushb   %[dest0],         %[dest0],         %[dest1]      \n\t"

      "psrlh      %[src0],          %[src0],          %[shift]      \n\t"
      "psrlh      %[src1],          %[src1],          %[shift]      \n\t"
      "packushb   %[dest1],         %[src0],          %[src1]       \n\t"

      "pavgb      %[dest],          %[dest0],         %[dest1]      \n\t"
      "gssdlc1    %[dest],          0x07(%[dst_ptr])                \n\t"
      "gssdrc1    %[dest],          0x00(%[dst_ptr])                \n\t"

      "daddiu     %[src_ptr],       %[src_ptr],        0x10         \n\t"
      "daddiu     %[dst_ptr],       %[dst_ptr],        0x08         \n\t"
      "daddi      %[width],         %[width],         -0x08         \n\t"
      "bnez       %[width],         1b                              \n\t"
      : [src0] "=&f"(src0), [src1] "=&f"(src1), [dest0] "=&f"(dest0),
        [dest1] "=&f"(dest1), [dest] "=&f"(dest)
      : [src_ptr] "r"(src_ptr), [dst_ptr] "r"(dst), [mask] "f"(mask),
        [shift] "f"(shift), [width] "r"(dst_width)
      : "memory");
}

void ScaleRowDown2Box_MMI(const uint8_t* src_ptr,
                          ptrdiff_t src_stride,
                          uint8_t* dst,
                          int dst_width) {
  const uint8_t* s = src_ptr;
  const uint8_t* t = src_ptr + src_stride;

  uint64_t s0, s1, t0, t1;
  uint64_t dest, dest0, dest1;

  const uint64_t ph = 0x0002000200020002ULL;
  const uint64_t mask = 0x00ff00ff00ff00ffULL;
  const uint64_t shift0 = 0x2ULL;
  const uint64_t shift1 = 0x8ULL;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldrc1    %[s0],            0x00(%[s])                      \n\t"
      "gsldlc1    %[s0],            0x07(%[s])                      \n\t"
      "psrlh      %[s1],            %[s0],            %[shift1]     \n\t"
      "and        %[s0],            %[s0],            %[mask]       \n\t"

      "gsldrc1    %[t0],            0x00(%[t])                      \n\t"
      "gsldlc1    %[t0],            0x07(%[t])                      \n\t"
      "psrlh      %[t1],            %[t0],            %[shift1]     \n\t"
      "and        %[t0],            %[t0],            %[mask]       \n\t"

      "paddh      %[dest0],         %[s0],            %[s1]         \n\t"
      "paddh      %[dest0],         %[dest0],         %[t0]         \n\t"
      "paddh      %[dest0],         %[dest0],         %[t1]         \n\t"
      "paddh      %[dest0],         %[dest0],         %[ph]         \n\t"
      "psrlh      %[dest0],         %[dest0],         %[shift0]     \n\t"

      "gsldrc1    %[s0],            0x08(%[s])                      \n\t"
      "gsldlc1    %[s0],            0x0f(%[s])                      \n\t"
      "psrlh      %[s1],            %[s0],            %[shift1]     \n\t"
      "and        %[s0],            %[s0],            %[mask]       \n\t"

      "gsldrc1    %[t0],            0x08(%[t])                      \n\t"
      "gsldlc1    %[t0],            0x0f(%[t])                      \n\t"
      "psrlh      %[t1],            %[t0],            %[shift1]     \n\t"
      "and        %[t0],            %[t0],            %[mask]       \n\t"

      "paddh      %[dest1],         %[s0],            %[s1]         \n\t"
      "paddh      %[dest1],         %[dest1],         %[t0]         \n\t"
      "paddh      %[dest1],         %[dest1],         %[t1]         \n\t"
      "paddh      %[dest1],         %[dest1],         %[ph]         \n\t"
      "psrlh      %[dest1],         %[dest1],         %[shift0]     \n\t"

      "packushb   %[dest],          %[dest0],         %[dest1]      \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[s],            %[s],              0x10          \n\t"
      "daddiu     %[t],            %[t],              0x10          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x08          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [s0] "=&f"(s0), [s1] "=&f"(s1), [t0] "=&f"(t0), [t1] "=&f"(t1),
        [dest0] "=&f"(dest0), [dest1] "=&f"(dest1), [dest] "=&f"(dest)
      : [s] "r"(s), [t] "r"(t), [dst_ptr] "r"(dst), [width] "r"(dst_width),
        [shift0] "f"(shift0), [shift1] "f"(shift1), [ph] "f"(ph),
        [mask] "f"(mask)
      : "memory");
}

void ScaleARGBRowDown2_MMI(const uint8_t* src_argb,
                           ptrdiff_t src_stride,
                           uint8_t* dst_argb,
                           int dst_width) {
  (void)src_stride;

  const uint32_t* src = (const uint32_t*)(src_argb);
  uint32_t* dst = (uint32_t*)(dst_argb);

  uint64_t src0, src1, dest;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldrc1    %[src0],         0x00(%[src_ptr])                 \n\t"
      "gsldlc1    %[src0],         0x07(%[src_ptr])                 \n\t"
      "gsldrc1    %[src1],         0x08(%[src_ptr])                 \n\t"
      "gsldlc1    %[src1],         0x0f(%[src_ptr])                 \n\t"
      "punpckhwd  %[dest],         %[src0],           %[src1]       \n\t"

      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x10          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x02          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src0] "=&f"(src0), [src1] "=&f"(src1), [dest] "=&f"(dest)
      : [src_ptr] "r"(src), [dst_ptr] "r"(dst), [width] "r"(dst_width)
      : "memory");
}

void ScaleARGBRowDown2Linear_MMI(const uint8_t* src_argb,
                                 ptrdiff_t src_stride,
                                 uint8_t* dst_argb,
                                 int dst_width) {
  (void)src_stride;

  uint64_t src0, src1;
  uint64_t dest, dest_hi, dest_lo;

  __asm__ volatile(
      "1:                                                           \n\t"
      "lwc1       %[src0],         0x00(%[src_ptr])                 \n\t"
      "lwc1       %[src1],         0x08(%[src_ptr])                 \n\t"
      "punpcklwd  %[dest_lo],      %[src0],           %[src1]       \n\t"
      "lwc1       %[src0],         0x04(%[src_ptr])                 \n\t"
      "lwc1       %[src1],         0x0c(%[src_ptr])                 \n\t"
      "punpcklwd  %[dest_hi],      %[src0],           %[src1]       \n\t"

      "pavgb      %[dest],         %[dest_lo],        %[dest_hi]    \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x10          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x02          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src0] "=&f"(src0), [src1] "=&f"(src1), [dest_hi] "=&f"(dest_hi),
        [dest_lo] "=&f"(dest_lo), [dest] "=&f"(dest)
      : [src_ptr] "r"(src_argb), [dst_ptr] "r"(dst_argb), [width] "r"(dst_width)
      : "memory");
}

void ScaleARGBRowDown2Box_MMI(const uint8_t* src_argb,
                              ptrdiff_t src_stride,
                              uint8_t* dst_argb,
                              int dst_width) {
  const uint8_t* s = src_argb;
  const uint8_t* t = src_argb + src_stride;

  uint64_t s0, s_hi, s_lo;
  uint64_t t0, t_hi, t_lo;
  uint64_t dest, dest_hi, dest_lo;

  const uint64_t mask = 0x0ULL;
  const uint64_t ph = 0x0002000200020002ULL;
  const uint64_t shfit = 0x2ULL;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldrc1    %[s0],            0x00(%[s])                      \n\t"
      "gsldlc1    %[s0],            0x07(%[s])                      \n\t"
      "punpcklbh  %[s_lo],          %[s0],           %[mask]        \n\t"
      "punpckhbh  %[s_hi],          %[s0],           %[mask]        \n\t"
      "paddh      %[dest_lo],       %[s_lo],         %[s_hi]        \n\t"

      "gsldrc1    %[t0],            0x00(%[t])                      \n\t"
      "gsldlc1    %[t0],            0x07(%[t])                      \n\t"
      "punpcklbh  %[t_lo],          %[t0],           %[mask]        \n\t"
      "punpckhbh  %[t_hi],          %[t0],           %[mask]        \n\t"
      "paddh      %[dest_lo],       %[dest_lo],      %[t_lo]        \n\t"
      "paddh      %[dest_lo],       %[dest_lo],      %[t_hi]        \n\t"

      "paddh      %[dest_lo],      %[dest_lo],       %[ph]          \n\t"
      "psrlh      %[dest_lo],      %[dest_lo],       %[shfit]       \n\t"

      "gsldrc1    %[s0],            0x08(%[s])                      \n\t"
      "gsldlc1    %[s0],            0x0f(%[s])                      \n\t"
      "punpcklbh  %[s_lo],          %[s0],           %[mask]        \n\t"
      "punpckhbh  %[s_hi],          %[s0],           %[mask]        \n\t"
      "paddh      %[dest_hi],       %[s_lo],         %[s_hi]        \n\t"

      "gsldrc1    %[t0],            0x08(%[t])                      \n\t"
      "gsldlc1    %[t0],            0x0f(%[t])                      \n\t"
      "punpcklbh  %[t_lo],          %[t0],           %[mask]        \n\t"
      "punpckhbh  %[t_hi],          %[t0],           %[mask]        \n\t"
      "paddh      %[dest_hi],       %[dest_hi],      %[t_lo]        \n\t"
      "paddh      %[dest_hi],       %[dest_hi],      %[t_hi]        \n\t"

      "paddh      %[dest_hi],      %[dest_hi],       %[ph]          \n\t"
      "psrlh      %[dest_hi],      %[dest_hi],       %[shfit]       \n\t"

      "packushb   %[dest],         %[dest_lo],       %[dest_hi]     \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[s],            %[s],              0x10          \n\t"
      "daddiu     %[t],            %[t],              0x10          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x02          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [s0] "=&f"(s0), [t0] "=&f"(t0), [dest_hi] "=&f"(dest_hi),
        [dest_lo] "=&f"(dest_lo), [s_hi] "=&f"(s_hi), [s_lo] "=&f"(s_lo),
        [t_hi] "=&f"(t_hi), [t_lo] "=&f"(t_lo), [dest] "=&f"(dest)
      : [s] "r"(s), [t] "r"(t), [dst_ptr] "r"(dst_argb), [width] "r"(dst_width),
        [mask] "f"(mask), [ph] "f"(ph), [shfit] "f"(shfit)
      : "memory");
}

void ScaleRowDown2_16_MMI(const uint16_t* src_ptr,
                          ptrdiff_t src_stride,
                          uint16_t* dst,
                          int dst_width) {
  (void)src_stride;

  uint64_t src0, src1, dest;
  const uint64_t shift = 0x10ULL;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldrc1    %[src0],         0x00(%[src_ptr])                 \n\t"
      "gsldlc1    %[src0],         0x07(%[src_ptr])                 \n\t"
      "psrlw      %[src0],         %[src0],           %[shift]      \n\t"

      "gsldrc1    %[src1],         0x08(%[src_ptr])                 \n\t"
      "gsldlc1    %[src1],         0x0f(%[src_ptr])                 \n\t"
      "psrlw      %[src1],         %[src1],           %[shift]      \n\t"

      "packsswh   %[dest],         %[src0],           %[src1]       \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x10          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x04          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src0] "=&f"(src0), [src1] "=&f"(src1), [dest] "=&f"(dest)
      : [src_ptr] "r"(src_ptr), [dst_ptr] "r"(dst), [width] "r"(dst_width),
        [shift] "f"(shift)
      : "memory");
}

void ScaleRowDown2Linear_16_MMI(const uint16_t* src_ptr,
                                ptrdiff_t src_stride,
                                uint16_t* dst,
                                int dst_width) {
  (void)src_stride;

  uint64_t src0, src1;
  uint64_t dest, dest_hi, dest_lo;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldrc1    %[src0],         0x00(%[src_ptr])                 \n\t"
      "gsldlc1    %[src0],         0x07(%[src_ptr])                 \n\t"
      "gsldrc1    %[src1],         0x08(%[src_ptr])                 \n\t"
      "gsldlc1    %[src1],         0x0f(%[src_ptr])                 \n\t"
      "punpcklhw  %[dest_lo],      %[src0],           %[src1]       \n\t"
      "punpckhhw  %[dest_hi],      %[src0],           %[src1]       \n\t"

      "punpcklhw  %[src0],         %[dest_lo],        %[dest_hi]    \n\t"
      "punpckhhw  %[src1],         %[dest_lo],        %[dest_hi]    \n\t"

      "pavgh      %[dest],         %[src0],           %[src1]       \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x10          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x04          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src0] "=&f"(src0), [src1] "=&f"(src1), [dest_hi] "=&f"(dest_hi),
        [dest_lo] "=&f"(dest_lo), [dest] "=&f"(dest)
      : [src_ptr] "r"(src_ptr), [dst_ptr] "r"(dst), [width] "r"(dst_width)
      : "memory");
}

void ScaleRowDown2Box_16_MMI(const uint16_t* src_ptr,
                             ptrdiff_t src_stride,
                             uint16_t* dst,
                             int dst_width) {
  const uint16_t* s = src_ptr;
  const uint16_t* t = src_ptr + src_stride;

  uint64_t s0, s1, s_hi, s_lo;
  uint64_t t0, t1, t_hi, t_lo;
  uint64_t dest, dest0, dest1;

  const uint64_t ph = 0x0000000200000002ULL;
  const uint64_t mask = 0x0000ffff0000ffffULL;
  const uint64_t shift0 = 0x10ULL;
  const uint64_t shift1 = 0x2ULL;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldrc1    %[s0],            0x00(%[s])                      \n\t"
      "gsldlc1    %[s0],            0x07(%[s])                      \n\t"
      "psrlw      %[s1],            %[s0],            %[shift0]     \n\t"
      "and        %[s0],            %[s0],            %[mask]       \n\t"

      "gsldrc1    %[t0],            0x00(%[t])                      \n\t"
      "gsldlc1    %[t0],            0x07(%[t])                      \n\t"
      "psrlw      %[t1],            %[t0],            %[shift0]     \n\t"
      "and        %[t0],            %[t0],            %[mask]       \n\t"

      "paddw      %[dest0],         %[s0],            %[s1]         \n\t"
      "paddw      %[dest0],         %[dest0],         %[t0]         \n\t"
      "paddw      %[dest0],         %[dest0],         %[t1]         \n\t"
      "paddw      %[dest0],         %[dest0],         %[ph]         \n\t"
      "psrlw      %[dest0],         %[dest0],         %[shift1]     \n\t"

      "gsldrc1    %[s0],            0x08(%[s])                      \n\t"
      "gsldlc1    %[s0],            0x0f(%[s])                      \n\t"
      "psrlw      %[s1],            %[s0],            %[shift0]     \n\t"
      "and        %[s0],            %[s0],            %[mask]       \n\t"

      "gsldrc1    %[t0],            0x08(%[t])                      \n\t"
      "gsldlc1    %[t0],            0x0f(%[t])                      \n\t"
      "psrlw      %[t1],            %[t0],            %[shift0]     \n\t"
      "and        %[t0],            %[t0],            %[mask]       \n\t"

      "paddw      %[dest1],         %[s0],            %[s1]         \n\t"
      "paddw      %[dest1],         %[dest1],         %[t0]         \n\t"
      "paddw      %[dest1],         %[dest1],         %[t1]         \n\t"
      "paddw      %[dest1],         %[dest1],         %[ph]         \n\t"
      "psrlw      %[dest1],         %[dest1],         %[shift1]     \n\t"

      "packsswh   %[dest],          %[dest0],         %[dest1]      \n\t"
      "gssdlc1    %[dest],          0x07(%[dst_ptr])                \n\t"
      "gssdrc1    %[dest],          0x00(%[dst_ptr])                \n\t"

      "daddiu     %[s],             %[s],              0x10         \n\t"
      "daddiu     %[t],             %[t],              0x10         \n\t"
      "daddiu     %[dst_ptr],       %[dst_ptr],        0x08         \n\t"
      "daddi      %[width],         %[width],         -0x04         \n\t"
      "bnez       %[width],         1b                              \n\t"
      : [s0] "=&f"(s0), [s1] "=&f"(s1), [t0] "=&f"(t0), [t1] "=&f"(t1),
        [s_hi] "=&f"(s_hi), [s_lo] "=&f"(s_lo), [t_hi] "=&f"(t_hi),
        [t_lo] "=&f"(t_lo), [dest0] "=&f"(dest0), [dest1] "=&f"(dest1),
        [dest] "=&f"(dest)
      : [s] "r"(s), [t] "r"(t), [dst_ptr] "r"(dst), [width] "r"(dst_width),
        [shift0] "f"(shift0), [shift1] "f"(shift1), [ph] "f"(ph),
        [mask] "f"(mask)
      : "memory");
}

void ScaleRowDown4_MMI(const uint8_t* src_ptr,
                       ptrdiff_t src_stride,
                       uint8_t* dst,
                       int dst_width) {
  (void)src_stride;

  uint64_t src0, src1;
  uint64_t dest, dest_hi, dest_lo;

  const uint64_t shift = 0x10ULL;
  const uint64_t mask = 0x000000ff000000ffULL;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldrc1    %[src0],         0x00(%[src_ptr])                 \n\t"
      "gsldlc1    %[src0],         0x07(%[src_ptr])                 \n\t"
      "psrlw      %[src0],         %[src0],           %[shift]      \n\t"
      "and        %[src0],         %[src0],           %[mask]       \n\t"
      "gsldrc1    %[src1],         0x08(%[src_ptr])                 \n\t"
      "gsldlc1    %[src1],         0x0f(%[src_ptr])                 \n\t"
      "psrlw      %[src1],         %[src1],           %[shift]      \n\t"
      "and        %[src1],         %[src1],           %[mask]       \n\t"
      "packsswh   %[dest_lo],      %[src0],           %[src1]       \n\t"

      "gsldrc1    %[src0],         0x10(%[src_ptr])                 \n\t"
      "gsldlc1    %[src0],         0x17(%[src_ptr])                 \n\t"
      "psrlw      %[src0],         %[src0],           %[shift]      \n\t"
      "and        %[src0],         %[src0],           %[mask]       \n\t"
      "gsldrc1    %[src1],         0x18(%[src_ptr])                 \n\t"
      "gsldlc1    %[src1],         0x1f(%[src_ptr])                 \n\t"
      "psrlw      %[src1],         %[src1],           %[shift]      \n\t"
      "and        %[src1],         %[src1],           %[mask]       \n\t"
      "packsswh   %[dest_hi],      %[src0],           %[src1]       \n\t"

      "packushb   %[dest],         %[dest_lo],         %[dest_hi]   \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x20          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x08          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src0] "=&f"(src0), [src1] "=&f"(src1), [dest_hi] "=&f"(dest_hi),
        [dest_lo] "=&f"(dest_lo), [dest] "=&f"(dest)
      : [src_ptr] "r"(src_ptr), [dst_ptr] "r"(dst), [width] "r"(dst_width),
        [shift] "f"(shift), [mask] "f"(mask)
      : "memory");
}

void ScaleRowDown4_16_MMI(const uint16_t* src_ptr,
                          ptrdiff_t src_stride,
                          uint16_t* dst,
                          int dst_width) {
  (void)src_stride;

  uint64_t src0, src1;
  uint64_t dest, dest_hi, dest_lo;

  const uint64_t mask = 0x0ULL;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldrc1    %[src0],         0x00(%[src_ptr])                 \n\t"
      "gsldlc1    %[src0],         0x07(%[src_ptr])                 \n\t"
      "gsldrc1    %[src1],         0x08(%[src_ptr])                 \n\t"
      "gsldlc1    %[src1],         0x0f(%[src_ptr])                 \n\t"
      "punpckhhw  %[dest_lo],      %[src0],           %[src1]       \n\t"
      "punpcklhw  %[dest_lo],      %[dest_lo],        %[mask]       \n\t"

      "gsldrc1    %[src0],         0x10(%[src_ptr])                 \n\t"
      "gsldlc1    %[src0],         0x17(%[src_ptr])                 \n\t"
      "gsldrc1    %[src1],         0x18(%[src_ptr])                 \n\t"
      "gsldlc1    %[src1],         0x1f(%[src_ptr])                 \n\t"
      "punpckhhw  %[dest_hi],      %[src0],           %[src1]       \n\t"
      "punpcklhw  %[dest_hi],      %[dest_hi],        %[mask]       \n\t"

      "packushb   %[dest],         %[dest_lo],        %[dest_hi]    \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x20          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x04          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src0] "=&f"(src0), [src1] "=&f"(src1), [dest_hi] "=&f"(dest_hi),
        [dest_lo] "=&f"(dest_lo), [dest] "=&f"(dest)
      : [src_ptr] "r"(src_ptr), [dst_ptr] "r"(dst), [width] "r"(dst_width),
        [mask] "f"(mask)
      : "memory");
}

#define DO_SCALEROWDOWN4BOX_PUNPCKADD()                              \
  "punpcklbh  %[src_lo],       %[src],           %[mask0]      \n\t" \
  "punpckhbh  %[src_hi],       %[src],           %[mask0]      \n\t" \
  "paddh      %[dest_lo],      %[dest_lo],       %[src_lo]     \n\t" \
  "paddh      %[dest_hi],      %[dest_hi],       %[src_hi]     \n\t"

#define DO_SCALEROWDOWN4BOX_LOOP(reg)                                \
  "ldc1       %[src],          0x00(%[src0_ptr])               \n\t" \
  "punpcklbh  %[dest_lo],      %[src],           %[mask0]      \n\t" \
  "punpckhbh  %[dest_hi],      %[src],           %[mask0]      \n\t" \
                                                                     \
  "ldc1       %[src],          0x00(%[src1_ptr])               \n\t" \
  DO_SCALEROWDOWN4BOX_PUNPCKADD()                                    \
                                                                     \
  "ldc1       %[src],          0x00(%[src2_ptr])               \n\t" \
  DO_SCALEROWDOWN4BOX_PUNPCKADD()                                    \
                                                                     \
  "ldc1       %[src],          0x00(%[src3_ptr])               \n\t" \
  DO_SCALEROWDOWN4BOX_PUNPCKADD()                                    \
                                                                     \
  "pmaddhw    %[dest_lo],      %[dest_lo],       %[mask1]      \n\t" \
  "pmaddhw    %[dest_hi],      %[dest_hi],       %[mask1]      \n\t" \
  "packsswh   " #reg   ",      %[dest_lo],       %[dest_hi]    \n\t" \
  "pmaddhw    " #reg   ",      " #reg   ",       %[mask1]      \n\t" \
  "paddh      " #reg   ",      " #reg   ",       %[ph]         \n\t" \
  "psrlh      " #reg   ",      " #reg   ",       %[shift]      \n\t" \
                                                                     \
  "daddiu     %[src0_ptr],     %[src0_ptr],      0x08          \n\t" \
  "daddiu     %[src1_ptr],     %[src1_ptr],      0x08          \n\t" \
  "daddiu     %[src2_ptr],     %[src2_ptr],      0x08          \n\t" \
  "daddiu     %[src3_ptr],     %[src3_ptr],      0x08          \n\t"

/* LibYUVScaleTest.ScaleDownBy4_Box */
void ScaleRowDown4Box_MMI(const uint8_t* src_ptr,
                          ptrdiff_t src_stride,
                          uint8_t* dst,
                          int dst_width) {
  const uint8_t* src0_ptr = src_ptr;
  const uint8_t* src1_ptr = src_ptr + src_stride;
  const uint8_t* src2_ptr = src_ptr + src_stride * 2;
  const uint8_t* src3_ptr = src_ptr + src_stride * 3;

  uint64_t src, src_hi, src_lo;
  uint64_t dest, dest_hi, dest_lo, dest0, dest1, dest2, dest3;

  const uint64_t mask0 = 0x0ULL;
  const uint64_t mask1 = 0x0001000100010001ULL;
  const uint64_t ph = 0x0008000800080008ULL;
  const uint64_t shift = 0x4ULL;

  __asm__ volatile(
      "1:                                                           \n\t"

      DO_SCALEROWDOWN4BOX_LOOP(%[dest0])
      DO_SCALEROWDOWN4BOX_LOOP(%[dest1])
      DO_SCALEROWDOWN4BOX_LOOP(%[dest2])
      DO_SCALEROWDOWN4BOX_LOOP(%[dest3])

      "packsswh   %[dest_lo],      %[dest0],          %[dest1]      \n\t"
      "packsswh   %[dest_hi],      %[dest2],          %[dest3]      \n\t"

      "packushb   %[dest],         %[dest_lo],        %[dest_hi]    \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x08          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src_hi] "=&f"(src_hi), [src_lo] "=&f"(src_lo),
        [dest_hi] "=&f"(dest_hi), [dest_lo] "=&f"(dest_lo),
        [dest0] "=&f"(dest0), [dest1] "=&f"(dest1), [dest2] "=&f"(dest2),
        [dest3] "=&f"(dest3), [src] "=&f"(src), [dest] "=&f"(dest)
      : [src0_ptr] "r"(src0_ptr), [src1_ptr] "r"(src1_ptr),
        [src2_ptr] "r"(src2_ptr), [src3_ptr] "r"(src3_ptr), [dst_ptr] "r"(dst),
        [width] "r"(dst_width), [shift] "f"(shift), [mask0] "f"(mask0),
        [ph] "f"(ph), [mask1] "f"(mask1)
      : "memory");
}

#define DO_SCALEROWDOWN4BOX_16_PUNPCKADD()                            \
  "punpcklbh  %[src_lo],       %[src],            %[mask0]      \n\t" \
  "punpckhbh  %[src_hi],       %[src],            %[mask0]      \n\t" \
  "paddh      %[dest_lo],      %[dest_lo],        %[src_lo]     \n\t" \
  "paddh      %[dest_hi],      %[dest_hi],        %[src_hi]     \n\t"

#define DO_SCALEROWDOWN4BOX_16_LOOP(reg)                              \
  "ldc1       %[src],          0x00(%[src0_ptr])                \n\t" \
  "punpcklbh  %[dest_lo],      %[src],            %[mask0]      \n\t" \
  "punpckhbh  %[dest_hi],      %[src],            %[mask0]      \n\t" \
                                                                      \
  "ldc1       %[src],          0x00(%[src1_ptr])                \n\t" \
  DO_SCALEROWDOWN4BOX_16_PUNPCKADD()                                  \
                                                                      \
  "ldc1       %[src],          0x00(%[src2_ptr])                \n\t" \
  DO_SCALEROWDOWN4BOX_16_PUNPCKADD()                                  \
                                                                      \
  "ldc1       %[src],          0x00(%[src3_ptr])                \n\t" \
  DO_SCALEROWDOWN4BOX_16_PUNPCKADD()                                  \
                                                                      \
  "paddw      %[dest],         %[dest_lo],        %[dest_hi]    \n\t" \
  "punpckhwd  %[dest_hi],      %[dest],           %[dest]       \n\t" \
  "paddw      %[dest],         %[dest_hi],        %[dest]       \n\t" \
  "paddw      %[dest],         %[dest],           %[ph]         \n\t" \
  "psraw      %[dest],         %[dest],           %[shift]      \n\t" \
  "and        " #reg ",        %[dest],           %[mask1]      \n\t" \
                                                                      \
  "daddiu     %[src0_ptr],     %[src0_ptr],       0x08          \n\t" \
  "daddiu     %[src1_ptr],     %[src1_ptr],       0x08          \n\t" \
  "daddiu     %[src2_ptr],     %[src2_ptr],       0x08          \n\t" \
  "daddiu     %[src3_ptr],     %[src3_ptr],       0x08          \n\t"

/* LibYUVScaleTest.ScaleDownBy4_Box_16 */
void ScaleRowDown4Box_16_MMI(const uint16_t* src_ptr,
                             ptrdiff_t src_stride,
                             uint16_t* dst,
                             int dst_width) {
  const uint16_t* src0_ptr = src_ptr;
  const uint16_t* src1_ptr = src_ptr + src_stride;
  const uint16_t* src2_ptr = src_ptr + src_stride * 2;
  const uint16_t* src3_ptr = src_ptr + src_stride * 3;

  uint64_t src, src_hi, src_lo;
  uint64_t dest, dest_hi, dest_lo, dest0, dest1, dest2, dest3;

  const uint64_t mask0 = 0x0ULL;
  const uint64_t mask1 = 0x00000000ffffffffULL;
  const uint64_t ph = 0x0000000800000008ULL;
  const uint64_t shift = 0x04ULL;

  __asm__ volatile(
      "1:                                                        \n\t"

      DO_SCALEROWDOWN4BOX_16_LOOP(%[dest0])
      DO_SCALEROWDOWN4BOX_16_LOOP(%[dest1])
      DO_SCALEROWDOWN4BOX_16_LOOP(%[dest2])
      DO_SCALEROWDOWN4BOX_16_LOOP(%[dest3])
      "punpcklwd  %[dest_lo],      %[dest0],          %[dest1]   \n\t"
      "punpcklwd  %[dest_hi],      %[dest2],          %[dest3]   \n\t"

      "packushb   %[dest],         %[dest_lo],        %[dest_hi] \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])              \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])              \n\t"

      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08       \n\t"
      "daddi      %[width],        %[width],         -0x04       \n\t"
      "bnez       %[width],        1b                            \n\t"
      : [src_hi] "=&f"(src_hi), [src_lo] "=&f"(src_lo),
        [dest_hi] "=&f"(dest_hi), [dest_lo] "=&f"(dest_lo),
        [dest0] "=&f"(dest0), [dest1] "=&f"(dest1), [dest2] "=&f"(dest2),
        [dest3] "=&f"(dest3), [src] "=&f"(src), [dest] "=&f"(dest)
      : [src0_ptr] "r"(src0_ptr), [src1_ptr] "r"(src1_ptr),
        [src2_ptr] "r"(src2_ptr), [src3_ptr] "r"(src3_ptr), [dst_ptr] "r"(dst),
        [width] "r"(dst_width), [shift] "f"(shift), [mask0] "f"(mask0),
        [ph] "f"(ph), [mask1] "f"(mask1)
      : "memory");
}

// Scales a single row of pixels up by 2x using point sampling.
void ScaleColsUp2_MMI(uint8_t* dst_ptr,
                      const uint8_t* src_ptr,
                      int dst_width,
                      int x,
                      int dx) {
  uint64_t src, dest;

  (void)x;
  (void)dx;

  __asm__ volatile(
      "1:                                                           \n\t"
      "lwc1       %[src],          0x00(%[src_ptr])                 \n\t"

      "punpcklbh  %[dest],         %[src],            %[src]        \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x04          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x08          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src] "=&f"(src), [dest] "=&f"(dest)
      : [src_ptr] "r"(src_ptr), [dst_ptr] "r"(dst_ptr), [width] "r"(dst_width)
      : "memory");
}

void ScaleColsUp2_16_MMI(uint16_t* dst_ptr,
                         const uint16_t* src_ptr,
                         int dst_width,
                         int x,
                         int dx) {
  uint64_t src, dest;

  (void)x;
  (void)dx;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldrc1    %[src],          0x00(%[src_ptr])                 \n\t"
      "gsldlc1    %[src],          0x07(%[src_ptr])                 \n\t"

      "punpcklhw  %[dest],         %[src],            %[src]        \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "punpckhhw  %[dest],         %[src],            %[src]        \n\t"
      "gssdlc1    %[dest],         0x0f(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x08(%[dst_ptr])                 \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x08          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x10          \n\t"
      "daddi      %[width],        %[width],         -0x08          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src] "=&f"(src), [dest] "=&f"(dest)
      : [src_ptr] "r"(src_ptr), [dst_ptr] "r"(dst_ptr), [width] "r"(dst_width)
      : "memory");
}

void ScaleAddRow_MMI(const uint8_t* src_ptr, uint16_t* dst_ptr, int src_width) {
  uint64_t src, src_hi, src_lo, dest0, dest1;
  const uint64_t mask = 0x0ULL;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldlc1    %[src],          0x07(%[src_ptr])                 \n\t"
      "gsldrc1    %[src],          0x00(%[src_ptr])                 \n\t"
      "punpcklbh  %[src_lo],       %[src],            %[mask]       \n\t"
      "punpckhbh  %[src_hi],       %[src],            %[mask]       \n\t"

      "gsldrc1    %[dest0],        0x00(%[dst_ptr])                 \n\t"
      "gsldlc1    %[dest0],        0x07(%[dst_ptr])                 \n\t"
      "paddush    %[dest0],        %[dest0],          %[src_lo]     \n\t"
      "gsldrc1    %[dest1],        0x08(%[dst_ptr])                 \n\t"
      "gsldlc1    %[dest1],        0x0f(%[dst_ptr])                 \n\t"
      "paddush    %[dest1],        %[dest1],          %[src_hi]     \n\t"

      "gssdlc1    %[dest0],        0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest0],        0x00(%[dst_ptr])                 \n\t"
      "gssdlc1    %[dest1],        0x0f(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest1],        0x08(%[dst_ptr])                 \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x08          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x10          \n\t"
      "daddi      %[width],        %[width],         -0x08          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [dest0] "=&f"(dest0), [dest1] "=&f"(dest1), [src_hi] "=&f"(src_hi),
        [src_lo] "=&f"(src_lo), [src] "=&f"(src)
      : [src_ptr] "r"(src_ptr), [dst_ptr] "r"(dst_ptr), [width] "r"(src_width),
        [mask] "f"(mask)
      : "memory");
}

void ScaleAddRow_16_MMI(const uint16_t* src_ptr,
                        uint32_t* dst_ptr,
                        int src_width) {
  uint64_t src, src_hi, src_lo, dest0, dest1;
  const uint64_t mask = 0x0ULL;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldrc1    %[src],          0x00(%[src_ptr])                 \n\t"
      "gsldlc1    %[src],          0x07(%[src_ptr])                 \n\t"
      "punpcklhw  %[src_lo],       %[src],            %[mask]       \n\t"
      "punpckhhw  %[src_hi],       %[src],            %[mask]       \n\t"

      "gsldrc1    %[dest0],        0x00(%[dst_ptr])                 \n\t"
      "gsldlc1    %[dest0],        0x07(%[dst_ptr])                 \n\t"
      "paddw      %[dest0],        %[dest0],          %[src_lo]     \n\t"
      "gssdlc1    %[dest0],        0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest0],        0x00(%[dst_ptr])                 \n\t"

      "gsldrc1    %[dest1],        0x08(%[dst_ptr])                 \n\t"
      "gsldlc1    %[dest1],        0x0f(%[dst_ptr])                 \n\t"
      "paddw      %[dest1],        %[dest1],          %[src_hi]     \n\t"
      "gssdlc1    %[dest1],        0x0f(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest1],        0x08(%[dst_ptr])                 \n\t"

      "daddiu     %[src_ptr],      %[src_ptr],        0x08          \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x10          \n\t"
      "daddi      %[width],        %[width],         -0x04          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [dest0] "=&f"(dest0), [dest1] "=&f"(dest1), [src_hi] "=&f"(src_hi),
        [src_lo] "=&f"(src_lo), [src] "=&f"(src)
      : [src_ptr] "r"(src_ptr), [dst_ptr] "r"(dst_ptr), [width] "r"(src_width),
        [mask] "f"(mask)
      : "memory");
}

void ScaleARGBRowDownEven_MMI(const uint8_t* src_argb,
                              ptrdiff_t src_stride,
                              int src_stepx,
                              uint8_t* dst_argb,
                              int dst_width) {
  (void)src_stride;

  uint64_t src0, src1, dest;

  __asm__ volatile(
      "1:                                                           \n\t"
      "lwc1       %[src0],          0x00(%[src_ptr])                \n\t"
      "dadd       %[src_ptr],       %[src_ptr],       %[src_stepx_4]\n\t"
      "lwc1       %[src1],          0x00(%[src_ptr])                \n\t"
      "punpcklwd  %[dest],          %[src0],          %[src1]       \n\t"

      "gssdlc1    %[dest],          0x07(%[dst_ptr])                \n\t"
      "gssdrc1    %[dest],          0x00(%[dst_ptr])                \n\t"

      "dadd       %[src_ptr],       %[src_ptr],       %[src_stepx_4]\n\t"
      "daddiu     %[dst_ptr],       %[dst_ptr],       0x08          \n\t"
      "daddi      %[width],         %[width],        -0x02          \n\t"
      "bnez       %[width],         1b                              \n\t"
      : [src0] "=&f"(src0), [src1] "=&f"(src1), [dest] "=&f"(dest)
      : [src_ptr] "r"(src_argb), [dst_ptr] "r"(dst_argb),
        [src_stepx_4] "r"(src_stepx << 2), [width] "r"(dst_width)
      : "memory");
}

void ScaleARGBRowDownEvenBox_MMI(const uint8_t* src_argb,
                                 ptrdiff_t src_stride,
                                 int src_stepx,
                                 uint8_t* dst_argb,
                                 int dst_width) {
  const uint8_t* src0_ptr = src_argb;
  const uint8_t* src1_ptr = src_argb + src_stride;

  uint64_t src0, src1, src_hi, src_lo;
  uint64_t dest, dest_hi, dest_lo, dest0, dest1;

  const uint64_t mask = 0x0ULL;
  const uint64_t ph = 0x0002000200020002ULL;
  const uint64_t shift = 0x2ULL;

  __asm__ volatile(
      "1:                                                           \n\t"

      "lwc1       %[src0],         0x00(%[src0_ptr])                \n\t"
      "punpcklbh  %[dest_lo],      %[src0],          %[mask]        \n\t"
      "lwc1       %[src0],         0x04(%[src0_ptr])                \n\t"
      "punpcklbh  %[dest_hi],      %[src0],          %[mask]        \n\t"

      "lwc1       %[src1],         0x00(%[src1_ptr])                \n\t"
      "punpcklbh  %[src_lo],       %[src1],          %[mask]        \n\t"
      "lwc1       %[src1],         0x04(%[src1_ptr])                \n\t"
      "punpcklbh  %[src_hi],       %[src1],          %[mask]        \n\t"
      "paddh      %[dest_lo],      %[dest_lo],       %[src_lo]      \n\t"
      "paddh      %[dest_hi],      %[dest_hi],       %[src_hi]      \n\t"
      "paddh      %[dest0],        %[dest_hi],       %[dest_lo]     \n\t"
      "paddh      %[dest0],        %[dest0],         %[ph]          \n\t"
      "psrlh      %[dest0],        %[dest0],         %[shift]       \n\t"

      "dadd       %[src0_ptr],     %[src0_ptr],      %[src_stepx_4] \n\t"
      "dadd       %[src1_ptr],     %[src1_ptr],      %[src_stepx_4] \n\t"

      "lwc1       %[src0],         0x00(%[src0_ptr])                \n\t"
      "punpcklbh  %[dest_lo],      %[src0],          %[mask]        \n\t"
      "lwc1       %[src0],         0x04(%[src0_ptr])                \n\t"
      "punpcklbh  %[dest_hi],      %[src0],          %[mask]        \n\t"

      "lwc1       %[src1],         0x00(%[src1_ptr])                \n\t"
      "punpcklbh  %[src_lo],       %[src1],          %[mask]        \n\t"
      "lwc1       %[src1],         0x04(%[src1_ptr])                \n\t"
      "punpcklbh  %[src_hi],       %[src1],          %[mask]        \n\t"
      "paddh      %[dest_lo],      %[dest_lo],       %[src_lo]      \n\t"
      "paddh      %[dest_hi],      %[dest_hi],       %[src_hi]      \n\t"
      "paddh      %[dest1],        %[dest_hi],       %[dest_lo]     \n\t"
      "paddh      %[dest1],        %[dest1],         %[ph]          \n\t"
      "psrlh      %[dest1],        %[dest1],         %[shift]       \n\t"

      "packushb   %[dest],         %[dest0],          %[dest1]      \n\t"
      "gssdlc1    %[dest],         0x07(%[dst_ptr])                 \n\t"
      "gssdrc1    %[dest],         0x00(%[dst_ptr])                 \n\t"

      "dadd       %[src0_ptr],     %[src0_ptr],      %[src_stepx_4] \n\t"
      "dadd       %[src1_ptr],     %[src1_ptr],      %[src_stepx_4] \n\t"
      "daddiu     %[dst_ptr],      %[dst_ptr],        0x08          \n\t"
      "daddi      %[width],        %[width],         -0x02          \n\t"
      "bnez       %[width],        1b                               \n\t"
      : [src_hi] "=&f"(src_hi), [src_lo] "=&f"(src_lo),
        [dest_hi] "=&f"(dest_hi), [dest_lo] "=&f"(dest_lo),
        [dest0] "=&f"(dest0), [dest1] "=&f"(dest1), [src0] "=&f"(src0),
        [src1] "=&f"(src1), [dest] "=&f"(dest)
      : [src0_ptr] "r"(src0_ptr), [src1_ptr] "r"(src1_ptr),
        [dst_ptr] "r"(dst_argb), [width] "r"(dst_width),
        [src_stepx_4] "r"(src_stepx << 2), [shift] "f"(shift), [mask] "f"(mask),
        [ph] "f"(ph)
      : "memory");
}

// Scales a single row of pixels using point sampling.
void ScaleARGBCols_MMI(uint8_t* dst_argb,
                       const uint8_t* src_argb,
                       int dst_width,
                       int x,
                       int dx) {
  const uint32_t* src = (const uint32_t*)(src_argb);
  uint32_t* dst = (uint32_t*)(dst_argb);

  const uint32_t* src_tmp;

  uint64_t dest, offset;

  const uint64_t shift0 = 16;
  const uint64_t shift1 = 2;

  __asm__ volatile(
      "1:                                                           \n\t"
      "srav       %[offset],        %[x],             %[shift0]     \n\t"
      "sllv       %[offset],        %[offset],        %[shift1]     \n\t"
      "dadd       %[src_tmp],       %[src_ptr],       %[offset]     \n\t"
      "lwc1       %[dest],          0x00(%[src_tmp])                \n\t"
      "swc1       %[dest],          0x00(%[dst_ptr])                \n\t"

      "dadd       %[x],             %[x],             %[dx]         \n\t"

      "daddiu     %[dst_ptr],       %[dst_ptr],       0x04          \n\t"
      "daddi      %[width],         %[width],        -0x01          \n\t"
      "bnez       %[width],         1b                              \n\t"
      : [dest] "=&f"(dest), [offset] "=&r"(offset), [src_tmp] "=&r"(src_tmp)
      : [src_ptr] "r"(src), [dst_ptr] "r"(dst), [width] "r"(dst_width),
        [dx] "r"(dx), [x] "r"(x), [shift0] "r"(shift0), [shift1] "r"(shift1)
      : "memory");
}

// Scales a single row of pixels up by 2x using point sampling.
void ScaleARGBColsUp2_MMI(uint8_t* dst_argb,
                          const uint8_t* src_argb,
                          int dst_width,
                          int x,
                          int dx) {
  uint64_t src, dest0, dest1;
  (void)x;
  (void)dx;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldrc1    %[src],           0x00(%[src_ptr])                \n\t"
      "gsldlc1    %[src],           0x07(%[src_ptr])                \n\t"
      "punpcklwd  %[dest0],         %[src],           %[src]        \n\t"
      "gssdlc1    %[dest0],         0x07(%[dst_ptr])                \n\t"
      "gssdrc1    %[dest0],         0x00(%[dst_ptr])                \n\t"
      "punpckhwd  %[dest1],         %[src],           %[src]        \n\t"
      "gssdlc1    %[dest1],         0x0f(%[dst_ptr])                \n\t"
      "gssdrc1    %[dest1],         0x08(%[dst_ptr])                \n\t"

      "daddiu     %[src_ptr],       %[src_ptr],       0x08          \n\t"
      "daddiu     %[dst_ptr],       %[dst_ptr],       0x10          \n\t"
      "daddi      %[width],         %[width],        -0x04          \n\t"
      "bnez       %[width],         1b                              \n\t"
      : [dest0] "=&f"(dest0), [dest1] "=&f"(dest1), [src] "=&f"(src)
      : [src_ptr] "r"(src_argb), [dst_ptr] "r"(dst_argb), [width] "r"(dst_width)
      : "memory");
}

// Divide num by div and return as 16.16 fixed point result.
/* LibYUVBaseTest.TestFixedDiv */
int FixedDiv_MIPS(int num, int div) {
  int quotient = 0;
  const int shift = 16;

  asm(
      "dsll    %[num],     %[num],     %[shift]    \n\t"
      "ddiv    %[num],     %[div]                  \t\n"
      "mflo    %[quo]                              \t\n"
      : [quo] "+&r"(quotient)
      : [num] "r"(num), [div] "r"(div), [shift] "r"(shift));

  return quotient;
}

// Divide num by div and return as 16.16 fixed point result.
/* LibYUVScaleTest.ARGBScaleTo320x240_Linear */
int FixedDiv1_MIPS(int num, int div) {
  int quotient = 0;
  const int shift = 16;
  const int val1 = 1;
  const int64_t val11 = 0x00010001ULL;

  asm(
      "dsll    %[num],     %[num],     %[shift]    \n\t"
      "dsub    %[num],     %[num],     %[val11]    \n\t"
      "dsub    %[div],     %[div],     %[val1]     \n\t"
      "ddiv    %[num],     %[div]                  \t\n"
      "mflo    %[quo]                              \t\n"
      : [quo] "+&r"(quotient)
      : [num] "r"(num), [div] "r"(div), [val1] "r"(val1), [val11] "r"(val11),
        [shift] "r"(shift));

  return quotient;
}

// Read 8x2 upsample with filtering and write 16x1.
// actually reads an extra pixel, so 9x2.
void ScaleRowUp2_16_MMI(const uint16_t* src_ptr,
                        ptrdiff_t src_stride,
                        uint16_t* dst,
                        int dst_width) {
  const uint16_t* src2_ptr = src_ptr + src_stride;

  uint64_t src0, src1;
  uint64_t dest, dest04, dest15, dest26, dest37;
  uint64_t tmp0, tmp1, tmp2, tmp3;

  const uint64_t mask0 = 0x0003000900030009ULL;
  const uint64_t mask1 = 0x0001000300010003ULL;
  const uint64_t mask2 = 0x0009000300090003ULL;
  const uint64_t mask3 = 0x0003000100030001ULL;
  const uint64_t ph = 0x0000000800000008ULL;
  const uint64_t shift = 4;

  __asm__ volatile(
      "1:                                                           \n\t"
      "gsldrc1    %[src0],          0x00(%[src1_ptr])               \n\t"
      "gsldlc1    %[src0],          0x07(%[src1_ptr])               \n\t"
      "pmaddhw    %[dest04],        %[src0],          %[mask0]      \n\t"
      "gsldrc1    %[src1],          0x00(%[src2_ptr])               \n\t"
      "gsldlc1    %[src1],          0x07(%[src2_ptr])               \n\t"
      "pmaddhw    %[dest],          %[src1],          %[mask1]      \n\t"
      "paddw      %[dest04],        %[dest04],        %[dest]       \n\t"
      "paddw      %[dest04],        %[dest04],        %[ph]         \n\t"
      "psrlw      %[dest04],        %[dest04],        %[shift]      \n\t"

      "pmaddhw    %[dest15],        %[src0],          %[mask2]      \n\t"
      "pmaddhw    %[dest],          %[src1],          %[mask3]      \n\t"
      "paddw      %[dest15],        %[dest15],        %[dest]       \n\t"
      "paddw      %[dest15],        %[dest15],        %[ph]         \n\t"
      "psrlw      %[dest15],        %[dest15],        %[shift]      \n\t"

      "gsldrc1    %[src0],          0x02(%[src1_ptr])               \n\t"
      "gsldlc1    %[src0],          0x09(%[src1_ptr])               \n\t"
      "pmaddhw    %[dest26],        %[src0],          %[mask0]      \n\t"
      "gsldrc1    %[src1],          0x02(%[src2_ptr])               \n\t"
      "gsldlc1    %[src1],          0x09(%[src2_ptr])               \n\t"
      "pmaddhw    %[dest],          %[src1],          %[mask1]      \n\t"
      "paddw      %[dest26],        %[dest26],        %[dest]       \n\t"
      "paddw      %[dest26],        %[dest26],        %[ph]         \n\t"
      "psrlw      %[dest26],        %[dest26],        %[shift]      \n\t"

      "pmaddhw    %[dest37],        %[src0],          %[mask2]      \n\t"
      "pmaddhw    %[dest],          %[src1],          %[mask3]      \n\t"
      "paddw      %[dest37],        %[dest37],        %[dest]       \n\t"
      "paddw      %[dest37],        %[dest37],        %[ph]         \n\t"
      "psrlw      %[dest37],        %[dest37],        %[shift]      \n\t"

      /* tmp0 = ( 00 04 02 06 ) */
      "packsswh   %[tmp0],          %[dest04],        %[dest26]     \n\t"
      /* tmp1 = ( 01 05 03 07 ) */
      "packsswh   %[tmp1],          %[dest15],        %[dest37]     \n\t"

      /* tmp2 = ( 00 01 04 05 )*/
      "punpcklhw  %[tmp2],          %[tmp0],          %[tmp1]       \n\t"
      /* tmp3 = ( 02 03 06 07 )*/
      "punpckhhw  %[tmp3],          %[tmp0],          %[tmp1]       \n\t"

      /* ( 00 01 02 03 ) */
      "punpcklwd  %[dest],          %[tmp2],          %[tmp3]       \n\t"
      "gssdlc1    %[dest],          0x07(%[dst_ptr])                \n\t"
      "gssdrc1    %[dest],          0x00(%[dst_ptr])                \n\t"

      /* ( 04 05 06 07 ) */
      "punpckhwd  %[dest],          %[tmp2],          %[tmp3]       \n\t"
      "gssdlc1    %[dest],          0x0f(%[dst_ptr])                \n\t"
      "gssdrc1    %[dest],          0x08(%[dst_ptr])                \n\t"

      "daddiu     %[src1_ptr],      %[src1_ptr],      0x08          \n\t"
      "daddiu     %[src2_ptr],      %[src2_ptr],      0x08          \n\t"
      "daddiu     %[dst_ptr],       %[dst_ptr],       0x10          \n\t"
      "daddi      %[width],         %[width],        -0x08          \n\t"
      "bnez       %[width],         1b                              \n\t"
      : [src0] "=&f"(src0), [src1] "=&f"(src1), [dest04] "=&f"(dest04),
        [dest15] "=&f"(dest15), [dest26] "=&f"(dest26), [dest37] "=&f"(dest37),
        [tmp0] "=&f"(tmp0), [tmp1] "=&f"(tmp1), [tmp2] "=&f"(tmp2),
        [tmp3] "=&f"(tmp3), [dest] "=&f"(dest)
      : [src1_ptr] "r"(src_ptr), [src2_ptr] "r"(src2_ptr), [dst_ptr] "r"(dst),
        [width] "r"(dst_width), [mask0] "f"(mask0), [mask1] "f"(mask1),
        [mask2] "f"(mask2), [mask3] "f"(mask3), [shift] "f"(shift), [ph] "f"(ph)
      : "memory");
}

void ScaleRowDown34_MMI(const uint8_t* src_ptr,
                      ptrdiff_t src_stride,
                      uint8_t* dst,
                      int dst_width) {
  (void)src_stride;
  assert((dst_width % 3 == 0) && (dst_width > 0));
  uint64_t src[2];
  uint64_t tmp[2];
  __asm__ volatile (
    "1:                                                           \n\t"
    "gsldlc1    %[src0],         0x07(%[src_ptr])                 \n\t"
    "gsldrc1    %[src0],         0x00(%[src_ptr])                 \n\t"
    "gsldlc1    %[src1],         0x0f(%[src_ptr])                 \n\t"
    "gsldrc1    %[src1],         0x08(%[src_ptr])                 \n\t"
    "and        %[tmp1],         %[src0],        %[mask1]         \n\t"
    "psrlw      %[tmp0],         %[src0],        %[rmov]          \n\t"
    "psllw      %[tmp0],         %[tmp0],        %[lmov1]         \n\t"
    "or         %[src0],         %[tmp0],        %[tmp1]          \n\t"
    "punpckhwd  %[tmp0],         %[src0],        %[src0]          \n\t"
    "psllw      %[tmp1],         %[tmp0],        %[rmov]          \n\t"
    "or         %[src0],         %[src0],        %[tmp1]          \n\t"
    "psrlw      %[tmp0],         %[tmp0],        %[rmov8]         \n\t"
    "pextrh     %[tmp0],         %[tmp0],        %[zero]          \n\t"
    "pinsrh_2   %[src0],         %[src0],        %[tmp0]          \n\t"
    "pextrh     %[tmp0],         %[src1],        %[zero]          \n\t"
    "pinsrh_3   %[src0],         %[src0],        %[tmp0]          \n\t"

    "punpckhwd  %[tmp0],         %[src1],        %[src1]          \n\t"
    "pextrh     %[tmp1],         %[tmp0],        %[zero]          \n\t"
    "psrlw      %[src1],         %[src1],        %[rmov]          \n\t"
    "psllw      %[tmp1],         %[tmp1],        %[rmov8]         \n\t"
    "or         %[src1],         %[src1],        %[tmp1]          \n\t"
    "and        %[tmp0],         %[tmp0],        %[mask2]         \n\t"
    "or         %[src1],         %[src1],        %[tmp0]          \n\t"

    "gssdlc1    %[src0],         0x07(%[dst_ptr])                 \n\t"
    "gssdrc1    %[src0],         0x00(%[dst_ptr])                 \n\t"
    "gsswlc1    %[src1],         0x0b(%[dst_ptr])                 \n\t"
    "gsswrc1    %[src1],         0x08(%[dst_ptr])                 \n\t"

    "daddiu     %[src_ptr],      %[src_ptr],     0x10             \n\t"
    "daddi      %[width],        %[width],      -0x0c             \n\t"
    "daddiu     %[dst_ptr],      %[dst_ptr],     0x0c             \n\t"
    "bnez       %[width],        1b                               \n\t"

    : [src0]"=&f"(src[0]),              [src1]"=&f"(src[1]),
      [tmp0]"=&f"(tmp[0]),              [tmp1]"=&f"(tmp[1])
    : [src_ptr]"r"(src_ptr),            [dst_ptr]"r"(dst),
      [lmov]"f"(0xc),                   [rmov]"f"(0x18),
      [mask1]"f"(0xffff0000ffff),       [rmov8]"f"(0x8),
      [zero]"f"(0x0),                   [mask2]"f"(0xff000000),
      [width]"r"(dst_width),            [lmov1]"f"(0x10)
    : "memory"
  );
}
// clang-format on

#endif  // !defined(LIBYUV_DISABLE_MMI) && defined(_MIPS_ARCH_LOONGSON3A)

#ifdef __cplusplus
}  // extern "C"
}  // namespace libyuv
#endif
