/*
 *  Copyright 2012 The LibYuv Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS. All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "libyuv/basic_types.h"

#include "libyuv/compare_row.h"

#ifdef __cplusplus
namespace libyuv {
extern "C" {
#endif

// This module is for Mips MMI.
#if !defined(LIBYUV_DISABLE_MMI) && defined(_MIPS_ARCH_LOONGSON3A)

// Hakmem method for hamming distance.
uint32_t HammingDistance_MMI(const uint8_t* src_a,
                             const uint8_t* src_b,
                             int count) {
  uint32_t diff = 0u;

  uint64_t temp = 0, temp1 = 0, ta = 0, tb = 0;
  uint64_t c1 = 0x5555555555555555;
  uint64_t c2 = 0x3333333333333333;
  uint64_t c3 = 0x0f0f0f0f0f0f0f0f;
  uint32_t c4 = 0x01010101;
  uint64_t s1 = 1, s2 = 2, s3 = 4;
  __asm__ volatile(
      "1:	\n\t"
      "ldc1   %[ta],    0(%[src_a])          \n\t"
      "ldc1   %[tb],    0(%[src_b])          \n\t"
      "xor    %[temp],  %[ta],      %[tb]    \n\t"
      "psrlw  %[temp1], %[temp],    %[s1]    \n\t"  // temp1=x>>1
      "and    %[temp1], %[temp1],   %[c1]    \n\t"  // temp1&=c1
      "psubw  %[temp1], %[temp],    %[temp1] \n\t"  // x-temp1
      "and    %[temp],  %[temp1],   %[c2]    \n\t"  // t = (u&c2)
      "psrlw  %[temp1], %[temp1],   %[s2]    \n\t"  // u>>2
      "and    %[temp1], %[temp1],   %[c2]    \n\t"  // u>>2 & c2
      "paddw  %[temp1], %[temp1],   %[temp]  \n\t"  // t1 = t1+t
      "psrlw  %[temp],  %[temp1],   %[s3]    \n\t"  // u>>4
      "paddw  %[temp1], %[temp1],   %[temp]  \n\t"  // u+(u>>4)
      "and    %[temp1], %[temp1],   %[c3]    \n\t"  //&c3
      "dmfc1  $t0,      %[temp1]             \n\t"
      "dsrl32 $t0,      $t0,        0        \n\t "
      "mul    $t0,      $t0,        %[c4]    \n\t"
      "dsrl   $t0,      $t0,        24       \n\t"
      "dadd   %[diff],  %[diff],    $t0      \n\t"
      "dmfc1  $t0,      %[temp1]             \n\t"
      "mul    $t0,      $t0,        %[c4]    \n\t"
      "dsrl   $t0,      $t0,        24       \n\t"
      "dadd   %[diff],  %[diff],    $t0      \n\t"
      "daddiu %[src_a], %[src_a],   8        \n\t"
      "daddiu %[src_b], %[src_b],   8        \n\t"
      "addiu  %[count], %[count],  -8        \n\t"
      "bgtz   %[count], 1b \n\t"
      "nop                            \n\t"
      : [diff] "+r"(diff), [src_a] "+r"(src_a), [src_b] "+r"(src_b),
        [count] "+r"(count), [ta] "+f"(ta), [tb] "+f"(tb), [temp] "+f"(temp),
        [temp1] "+f"(temp1)
      : [c1] "f"(c1), [c2] "f"(c2), [c3] "f"(c3), [c4] "r"(c4), [s1] "f"(s1),
        [s2] "f"(s2), [s3] "f"(s3)
      : "memory");
  return diff;
}

uint32_t SumSquareError_MMI(const uint8_t* src_a,
                            const uint8_t* src_b,
                            int count) {
  uint32_t sse = 0u;
  uint32_t sse_hi = 0u, sse_lo = 0u;

  uint64_t src1, src2;
  uint64_t diff, diff_hi, diff_lo;
  uint64_t sse_sum, sse_tmp;

  const uint64_t mask = 0x0ULL;

  __asm__ volatile(
      "xor        %[sse_sum],      %[sse_sum],        %[sse_sum]    \n\t"

      "1:                                                           \n\t"
      "ldc1       %[src1],         0x00(%[src_a])                   \n\t"
      "ldc1       %[src2],         0x00(%[src_b])                   \n\t"
      "pasubub    %[diff],         %[src1],           %[src2]       \n\t"
      "punpcklbh  %[diff_lo],      %[diff],           %[mask]       \n\t"
      "punpckhbh  %[diff_hi],      %[diff],           %[mask]       \n\t"
      "pmaddhw    %[sse_tmp],      %[diff_lo],        %[diff_lo]    \n\t"
      "paddw      %[sse_sum],      %[sse_sum],        %[sse_tmp]    \n\t"
      "pmaddhw    %[sse_tmp],      %[diff_hi],        %[diff_hi]    \n\t"
      "paddw      %[sse_sum],      %[sse_sum],        %[sse_tmp]    \n\t"

      "daddiu     %[src_a],        %[src_a],          0x08          \n\t"
      "daddiu     %[src_b],        %[src_b],          0x08          \n\t"
      "daddiu     %[count],        %[count],         -0x08          \n\t"
      "bnez       %[count],        1b                               \n\t"

      "mfc1       %[sse_lo],       %[sse_sum]                       \n\t"
      "mfhc1      %[sse_hi],       %[sse_sum]                       \n\t"
      "daddu      %[sse],          %[sse_hi],         %[sse_lo]     \n\t"
      : [sse] "+&r"(sse), [diff] "=&f"(diff), [src1] "=&f"(src1),
        [src2] "=&f"(src2), [diff_lo] "=&f"(diff_lo), [diff_hi] "=&f"(diff_hi),
        [sse_sum] "=&f"(sse_sum), [sse_tmp] "=&f"(sse_tmp),
        [sse_hi] "+&r"(sse_hi), [sse_lo] "+&r"(sse_lo)
      : [src_a] "r"(src_a), [src_b] "r"(src_b), [count] "r"(count),
        [mask] "f"(mask)
      : "memory");

  return sse;
}

#endif  // !defined(LIBYUV_DISABLE_MMI) && defined(_MIPS_ARCH_LOONGSON3A)

#ifdef __cplusplus
}  // extern "C"
}  // namespace libyuv
#endif
