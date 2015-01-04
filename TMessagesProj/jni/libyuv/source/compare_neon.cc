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
#include "libyuv/row.h"

#ifdef __cplusplus
namespace libyuv {
extern "C" {
#endif

#if !defined(LIBYUV_DISABLE_NEON) && defined(__ARM_NEON__)

uint32 SumSquareError_NEON(const uint8* src_a, const uint8* src_b, int count) {
  volatile uint32 sse;
  asm volatile (
    "vmov.u8    q8, #0                         \n"
    "vmov.u8    q10, #0                        \n"
    "vmov.u8    q9, #0                         \n"
    "vmov.u8    q11, #0                        \n"

    ".p2align  2                               \n"
  "1:                                          \n"
    MEMACCESS(0)
    "vld1.8     {q0}, [%0]!                    \n"
    MEMACCESS(1)
    "vld1.8     {q1}, [%1]!                    \n"
    "subs       %2, %2, #16                    \n"
    "vsubl.u8   q2, d0, d2                     \n"
    "vsubl.u8   q3, d1, d3                     \n"
    "vmlal.s16  q8, d4, d4                     \n"
    "vmlal.s16  q9, d6, d6                     \n"
    "vmlal.s16  q10, d5, d5                    \n"
    "vmlal.s16  q11, d7, d7                    \n"
    "bgt        1b                             \n"

    "vadd.u32   q8, q8, q9                     \n"
    "vadd.u32   q10, q10, q11                  \n"
    "vadd.u32   q11, q8, q10                   \n"
    "vpaddl.u32 q1, q11                        \n"
    "vadd.u64   d0, d2, d3                     \n"
    "vmov.32    %3, d0[0]                      \n"
    : "+r"(src_a),
      "+r"(src_b),
      "+r"(count),
      "=r"(sse)
    :
    : "memory", "cc", "q0", "q1", "q2", "q3", "q8", "q9", "q10", "q11");
  return sse;
}

#elif !defined(LIBYUV_DISABLE_NEON) && defined(__aarch64__)

uint32 SumSquareError_NEON(const uint8* src_a, const uint8* src_b, int count) {
  volatile uint32 sse;
  asm volatile (
    "eor        v16.16b, v16.16b, v16.16b      \n"
    "eor        v18.16b, v18.16b, v18.16b      \n"
    "eor        v17.16b, v17.16b, v17.16b      \n"
    "eor        v19.16b, v19.16b, v19.16b      \n"

    ".p2align  2                               \n"
  "1:                                          \n"
    MEMACCESS(0)
    "ld1        {v0.16b}, [%0], #16            \n"
    MEMACCESS(1)
    "ld1        {v1.16b}, [%1], #16            \n"
    "subs       %2, %2, #16                    \n"
    "usubl      v2.8h, v0.8b, v1.8b            \n"
    "usubl2     v3.8h, v0.16b, v1.16b          \n"
    "smlal      v16.4s, v2.4h, v2.4h           \n"
    "smlal      v17.4s, v3.4h, v3.4h           \n"
    "smlal2     v18.4s, v2.8h, v2.8h           \n"
    "smlal2     v19.4s, v3.8h, v3.8h           \n"
    "bgt        1b                             \n"

    "add        v16.4s, v16.4s, v17.4s         \n"
    "add        v18.4s, v18.4s, v19.4s         \n"
    "add        v19.4s, v16.4s, v18.4s         \n"
    "addv       s0, v19.4s                     \n"
    "fmov       %w3, s0                        \n"
    : "+r"(src_a),
      "+r"(src_b),
      "+r"(count),
      "=r"(sse)
    :
    : "cc", "v0", "v1", "v2", "v3", "v16", "v17", "v18", "v19");
  return sse;
}

#endif  // __ARM_NEON__

#ifdef __cplusplus
}  // extern "C"
}  // namespace libyuv
#endif
