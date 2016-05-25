/*
 *  Copyright 2014 The LibYuv Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS. All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "libyuv/row.h"

#ifdef __cplusplus
namespace libyuv {
extern "C" {
#endif

// This module is for GCC Neon armv8 64 bit.
#if !defined(LIBYUV_DISABLE_NEON) && defined(__aarch64__)

// Read 8 Y, 4 U and 4 V from 422
#define READYUV422                                                             \
    MEMACCESS(0)                                                               \
    "ld1        {v0.8b}, [%0], #8              \n"                             \
    MEMACCESS(1)                                                               \
    "ld1        {v1.s}[0], [%1], #4            \n"                             \
    MEMACCESS(2)                                                               \
    "ld1        {v1.s}[1], [%2], #4            \n"

// Read 8 Y, 2 U and 2 V from 422
#define READYUV411                                                             \
    MEMACCESS(0)                                                               \
    "ld1        {v0.8b}, [%0], #8              \n"                             \
    MEMACCESS(1)                                                               \
    "ld1        {v2.h}[0], [%1], #2            \n"                             \
    MEMACCESS(2)                                                               \
    "ld1        {v2.h}[1], [%2], #2            \n"                             \
    "zip1       v1.8b, v2.8b, v2.8b            \n"

// Read 8 Y, 8 U and 8 V from 444
#define READYUV444                                                             \
    MEMACCESS(0)                                                               \
    "ld1        {v0.8b}, [%0], #8              \n"                             \
    MEMACCESS(1)                                                               \
    "ld1        {v1.d}[0], [%1], #8            \n"                             \
    MEMACCESS(2)                                                               \
    "ld1        {v1.d}[1], [%2], #8            \n"                             \
    "uaddlp     v1.8h, v1.16b                  \n"                             \
    "rshrn      v1.8b, v1.8h, #1               \n"

// Read 8 Y, and set 4 U and 4 V to 128
#define READYUV400                                                             \
    MEMACCESS(0)                                                               \
    "ld1        {v0.8b}, [%0], #8              \n"                             \
    "movi       v1.8b , #128                   \n"

// Read 8 Y and 4 UV from NV12
#define READNV12                                                               \
    MEMACCESS(0)                                                               \
    "ld1        {v0.8b}, [%0], #8              \n"                             \
    MEMACCESS(1)                                                               \
    "ld1        {v2.8b}, [%1], #8              \n"                             \
    "uzp1       v1.8b, v2.8b, v2.8b            \n"                             \
    "uzp2       v3.8b, v2.8b, v2.8b            \n"                             \
    "ins        v1.s[1], v3.s[0]               \n"

// Read 8 Y and 4 VU from NV21
#define READNV21                                                               \
    MEMACCESS(0)                                                               \
    "ld1        {v0.8b}, [%0], #8              \n"                             \
    MEMACCESS(1)                                                               \
    "ld1        {v2.8b}, [%1], #8              \n"                             \
    "uzp1       v3.8b, v2.8b, v2.8b            \n"                             \
    "uzp2       v1.8b, v2.8b, v2.8b            \n"                             \
    "ins        v1.s[1], v3.s[0]               \n"

// Read 8 YUY2
#define READYUY2                                                               \
    MEMACCESS(0)                                                               \
    "ld2        {v0.8b, v1.8b}, [%0], #16      \n"                             \
    "uzp2       v3.8b, v1.8b, v1.8b            \n"                             \
    "uzp1       v1.8b, v1.8b, v1.8b            \n"                             \
    "ins        v1.s[1], v3.s[0]               \n"

// Read 8 UYVY
#define READUYVY                                                               \
    MEMACCESS(0)                                                               \
    "ld2        {v2.8b, v3.8b}, [%0], #16      \n"                             \
    "orr        v0.8b, v3.8b, v3.8b            \n"                             \
    "uzp1       v1.8b, v2.8b, v2.8b            \n"                             \
    "uzp2       v3.8b, v2.8b, v2.8b            \n"                             \
    "ins        v1.s[1], v3.s[0]               \n"

#define YUVTORGB_SETUP                                                         \
    "ld1r       {v24.8h}, [%[kUVBiasBGR]], #2  \n"                             \
    "ld1r       {v25.8h}, [%[kUVBiasBGR]], #2  \n"                             \
    "ld1r       {v26.8h}, [%[kUVBiasBGR]]      \n"                             \
    "ld1r       {v31.4s}, [%[kYToRgb]]         \n"                             \
    "ld2        {v27.8h, v28.8h}, [%[kUVToRB]] \n"                             \
    "ld2        {v29.8h, v30.8h}, [%[kUVToG]]  \n"

#define YUVTORGB(vR, vG, vB)                                                   \
    "uxtl       v0.8h, v0.8b                   \n" /* Extract Y    */          \
    "shll       v2.8h, v1.8b, #8               \n" /* Replicate UV */          \
    "ushll2     v3.4s, v0.8h, #0               \n" /* Y */                     \
    "ushll      v0.4s, v0.4h, #0               \n"                             \
    "mul        v3.4s, v3.4s, v31.4s           \n"                             \
    "mul        v0.4s, v0.4s, v31.4s           \n"                             \
    "sqshrun    v0.4h, v0.4s, #16              \n"                             \
    "sqshrun2   v0.8h, v3.4s, #16              \n" /* Y */                     \
    "uaddw      v1.8h, v2.8h, v1.8b            \n" /* Replicate UV */          \
    "mov        v2.d[0], v1.d[1]               \n" /* Extract V */             \
    "uxtl       v2.8h, v2.8b                   \n"                             \
    "uxtl       v1.8h, v1.8b                   \n" /* Extract U */             \
    "mul        v3.8h, v1.8h, v27.8h           \n"                             \
    "mul        v5.8h, v1.8h, v29.8h           \n"                             \
    "mul        v6.8h, v2.8h, v30.8h           \n"                             \
    "mul        v7.8h, v2.8h, v28.8h           \n"                             \
    "sqadd      v6.8h, v6.8h, v5.8h            \n"                             \
    "sqadd      " #vB ".8h, v24.8h, v0.8h      \n" /* B */                     \
    "sqadd      " #vG ".8h, v25.8h, v0.8h      \n" /* G */                     \
    "sqadd      " #vR ".8h, v26.8h, v0.8h      \n" /* R */                     \
    "sqadd      " #vB ".8h, " #vB ".8h, v3.8h  \n" /* B */                     \
    "sqsub      " #vG ".8h, " #vG ".8h, v6.8h  \n" /* G */                     \
    "sqadd      " #vR ".8h, " #vR ".8h, v7.8h  \n" /* R */                     \
    "sqshrun    " #vB ".8b, " #vB ".8h, #6     \n" /* B */                     \
    "sqshrun    " #vG ".8b, " #vG ".8h, #6     \n" /* G */                     \
    "sqshrun    " #vR ".8b, " #vR ".8h, #6     \n" /* R */                     \

#ifdef HAS_I444TOARGBROW_NEON
void I444ToARGBRow_NEON(const uint8* src_y,
                        const uint8* src_u,
                        const uint8* src_v,
                        uint8* dst_argb,
                        const struct YuvConstants* yuvconstants,
                        int width) {
  asm volatile (
    YUVTORGB_SETUP
    "movi       v23.8b, #255                   \n" /* A */
  "1:                                          \n"
    READYUV444
    YUVTORGB(v22, v21, v20)
    "subs       %w4, %w4, #8                   \n"
    MEMACCESS(3)
    "st4        {v20.8b,v21.8b,v22.8b,v23.8b}, [%3], #32 \n"
    "b.gt       1b                             \n"
    : "+r"(src_y),     // %0
      "+r"(src_u),     // %1
      "+r"(src_v),     // %2
      "+r"(dst_argb),  // %3
      "+r"(width)      // %4
    : [kUVToRB]"r"(&yuvconstants->kUVToRB),
      [kUVToG]"r"(&yuvconstants->kUVToG),
      [kUVBiasBGR]"r"(&yuvconstants->kUVBiasBGR),
      [kYToRgb]"r"(&yuvconstants->kYToRgb)
    : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v20",
      "v21", "v22", "v23", "v24", "v25", "v26", "v27", "v28", "v29", "v30"
  );
}
#endif  // HAS_I444TOARGBROW_NEON

#ifdef HAS_I422TOARGBROW_NEON
void I422ToARGBRow_NEON(const uint8* src_y,
                        const uint8* src_u,
                        const uint8* src_v,
                        uint8* dst_argb,
                        const struct YuvConstants* yuvconstants,
                        int width) {
  asm volatile (
    YUVTORGB_SETUP
    "movi       v23.8b, #255                   \n" /* A */
  "1:                                          \n"
    READYUV422
    YUVTORGB(v22, v21, v20)
    "subs       %w4, %w4, #8                   \n"
    MEMACCESS(3)
    "st4        {v20.8b,v21.8b,v22.8b,v23.8b}, [%3], #32     \n"
    "b.gt       1b                             \n"
    : "+r"(src_y),     // %0
      "+r"(src_u),     // %1
      "+r"(src_v),     // %2
      "+r"(dst_argb),  // %3
      "+r"(width)      // %4
    : [kUVToRB]"r"(&yuvconstants->kUVToRB),
      [kUVToG]"r"(&yuvconstants->kUVToG),
      [kUVBiasBGR]"r"(&yuvconstants->kUVBiasBGR),
      [kYToRgb]"r"(&yuvconstants->kYToRgb)
    : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v20",
      "v21", "v22", "v23", "v24", "v25", "v26", "v27", "v28", "v29", "v30"
  );
}
#endif  // HAS_I422TOARGBROW_NEON

#ifdef HAS_I422ALPHATOARGBROW_NEON
void I422AlphaToARGBRow_NEON(const uint8* src_y,
                             const uint8* src_u,
                             const uint8* src_v,
                             const uint8* src_a,
                             uint8* dst_argb,
                             const struct YuvConstants* yuvconstants,
                             int width) {
  asm volatile (
    YUVTORGB_SETUP
  "1:                                          \n"
    READYUV422
    YUVTORGB(v22, v21, v20)
    MEMACCESS(3)
    "ld1        {v23.8b}, [%3], #8             \n"
    "subs       %w5, %w5, #8                   \n"
    MEMACCESS(4)
    "st4        {v20.8b,v21.8b,v22.8b,v23.8b}, [%4], #32     \n"
    "b.gt       1b                             \n"
    : "+r"(src_y),     // %0
      "+r"(src_u),     // %1
      "+r"(src_v),     // %2
      "+r"(src_a),     // %3
      "+r"(dst_argb),  // %4
      "+r"(width)      // %5
    : [kUVToRB]"r"(&yuvconstants->kUVToRB),
      [kUVToG]"r"(&yuvconstants->kUVToG),
      [kUVBiasBGR]"r"(&yuvconstants->kUVBiasBGR),
      [kYToRgb]"r"(&yuvconstants->kYToRgb)
    : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v20",
      "v21", "v22", "v23", "v24", "v25", "v26", "v27", "v28", "v29", "v30"
  );
}
#endif  // HAS_I422ALPHATOARGBROW_NEON

#ifdef HAS_I411TOARGBROW_NEON
void I411ToARGBRow_NEON(const uint8* src_y,
                        const uint8* src_u,
                        const uint8* src_v,
                        uint8* dst_argb,
                        const struct YuvConstants* yuvconstants,
                        int width) {
  asm volatile (
    YUVTORGB_SETUP
    "movi       v23.8b, #255                   \n" /* A */
  "1:                                          \n"
    READYUV411
    YUVTORGB(v22, v21, v20)
    "subs       %w4, %w4, #8                   \n"
    MEMACCESS(3)
    "st4        {v20.8b,v21.8b,v22.8b,v23.8b}, [%3], #32     \n"
    "b.gt       1b                             \n"
    : "+r"(src_y),     // %0
      "+r"(src_u),     // %1
      "+r"(src_v),     // %2
      "+r"(dst_argb),  // %3
      "+r"(width)      // %4
    : [kUVToRB]"r"(&yuvconstants->kUVToRB),
      [kUVToG]"r"(&yuvconstants->kUVToG),
      [kUVBiasBGR]"r"(&yuvconstants->kUVBiasBGR),
      [kYToRgb]"r"(&yuvconstants->kYToRgb)
    : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v20",
      "v21", "v22", "v23", "v24", "v25", "v26", "v27", "v28", "v29", "v30"
  );
}
#endif  // HAS_I411TOARGBROW_NEON

#ifdef HAS_I422TORGBAROW_NEON
void I422ToRGBARow_NEON(const uint8* src_y,
                        const uint8* src_u,
                        const uint8* src_v,
                        uint8* dst_rgba,
                        const struct YuvConstants* yuvconstants,
                        int width) {
  asm volatile (
    YUVTORGB_SETUP
    "movi       v20.8b, #255                   \n" /* A */
  "1:                                          \n"
    READYUV422
    YUVTORGB(v23, v22, v21)
    "subs       %w4, %w4, #8                   \n"
    MEMACCESS(3)
    "st4        {v20.8b,v21.8b,v22.8b,v23.8b}, [%3], #32     \n"
    "b.gt       1b                             \n"
    : "+r"(src_y),     // %0
      "+r"(src_u),     // %1
      "+r"(src_v),     // %2
      "+r"(dst_rgba),  // %3
      "+r"(width)      // %4
    : [kUVToRB]"r"(&yuvconstants->kUVToRB),
      [kUVToG]"r"(&yuvconstants->kUVToG),
      [kUVBiasBGR]"r"(&yuvconstants->kUVBiasBGR),
      [kYToRgb]"r"(&yuvconstants->kYToRgb)
    : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v20",
      "v21", "v22", "v23", "v24", "v25", "v26", "v27", "v28", "v29", "v30"
  );
}
#endif  // HAS_I422TORGBAROW_NEON

#ifdef HAS_I422TORGB24ROW_NEON
void I422ToRGB24Row_NEON(const uint8* src_y,
                         const uint8* src_u,
                         const uint8* src_v,
                         uint8* dst_rgb24,
                         const struct YuvConstants* yuvconstants,
                         int width) {
  asm volatile (
    YUVTORGB_SETUP
  "1:                                          \n"
    READYUV422
    YUVTORGB(v22, v21, v20)
    "subs       %w4, %w4, #8                   \n"
    MEMACCESS(3)
    "st3        {v20.8b,v21.8b,v22.8b}, [%3], #24     \n"
    "b.gt       1b                             \n"
    : "+r"(src_y),     // %0
      "+r"(src_u),     // %1
      "+r"(src_v),     // %2
      "+r"(dst_rgb24), // %3
      "+r"(width)      // %4
    : [kUVToRB]"r"(&yuvconstants->kUVToRB),
      [kUVToG]"r"(&yuvconstants->kUVToG),
      [kUVBiasBGR]"r"(&yuvconstants->kUVBiasBGR),
      [kYToRgb]"r"(&yuvconstants->kYToRgb)
    : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v20",
      "v21", "v22", "v23", "v24", "v25", "v26", "v27", "v28", "v29", "v30"
  );
}
#endif  // HAS_I422TORGB24ROW_NEON

#define ARGBTORGB565                                                           \
    "shll       v0.8h,  v22.8b, #8             \n"  /* R                    */ \
    "shll       v21.8h, v21.8b, #8             \n"  /* G                    */ \
    "shll       v20.8h, v20.8b, #8             \n"  /* B                    */ \
    "sri        v0.8h,  v21.8h, #5             \n"  /* RG                   */ \
    "sri        v0.8h,  v20.8h, #11            \n"  /* RGB                  */

#ifdef HAS_I422TORGB565ROW_NEON
void I422ToRGB565Row_NEON(const uint8* src_y,
                          const uint8* src_u,
                          const uint8* src_v,
                          uint8* dst_rgb565,
                          const struct YuvConstants* yuvconstants,
                          int width) {
  asm volatile (
    YUVTORGB_SETUP
  "1:                                          \n"
    READYUV422
    YUVTORGB(v22, v21, v20)
    "subs       %w4, %w4, #8                   \n"
    ARGBTORGB565
    MEMACCESS(3)
    "st1        {v0.8h}, [%3], #16             \n"  // store 8 pixels RGB565.
    "b.gt       1b                             \n"
    : "+r"(src_y),    // %0
      "+r"(src_u),    // %1
      "+r"(src_v),    // %2
      "+r"(dst_rgb565),  // %3
      "+r"(width)     // %4
    : [kUVToRB]"r"(&yuvconstants->kUVToRB),
      [kUVToG]"r"(&yuvconstants->kUVToG),
      [kUVBiasBGR]"r"(&yuvconstants->kUVBiasBGR),
      [kYToRgb]"r"(&yuvconstants->kYToRgb)
    : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v20",
      "v21", "v22", "v23", "v24", "v25", "v26", "v27", "v28", "v29", "v30"
  );
}
#endif  // HAS_I422TORGB565ROW_NEON

#define ARGBTOARGB1555                                                         \
    "shll       v0.8h,  v23.8b, #8             \n"  /* A                    */ \
    "shll       v22.8h, v22.8b, #8             \n"  /* R                    */ \
    "shll       v21.8h, v21.8b, #8             \n"  /* G                    */ \
    "shll       v20.8h, v20.8b, #8             \n"  /* B                    */ \
    "sri        v0.8h,  v22.8h, #1             \n"  /* AR                   */ \
    "sri        v0.8h,  v21.8h, #6             \n"  /* ARG                  */ \
    "sri        v0.8h,  v20.8h, #11            \n"  /* ARGB                 */

#ifdef HAS_I422TOARGB1555ROW_NEON
void I422ToARGB1555Row_NEON(const uint8* src_y,
                            const uint8* src_u,
                            const uint8* src_v,
                            uint8* dst_argb1555,
                            const struct YuvConstants* yuvconstants,
                            int width) {
  asm volatile (
    YUVTORGB_SETUP
    "movi       v23.8b, #255                   \n"
  "1:                                          \n"
    READYUV422
    YUVTORGB(v22, v21, v20)
    "subs       %w4, %w4, #8                   \n"
    ARGBTOARGB1555
    MEMACCESS(3)
    "st1        {v0.8h}, [%3], #16             \n"  // store 8 pixels RGB565.
    "b.gt       1b                             \n"
    : "+r"(src_y),    // %0
      "+r"(src_u),    // %1
      "+r"(src_v),    // %2
      "+r"(dst_argb1555),  // %3
      "+r"(width)     // %4
    : [kUVToRB]"r"(&yuvconstants->kUVToRB),
      [kUVToG]"r"(&yuvconstants->kUVToG),
      [kUVBiasBGR]"r"(&yuvconstants->kUVBiasBGR),
      [kYToRgb]"r"(&yuvconstants->kYToRgb)
    : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v20",
      "v21", "v22", "v23", "v24", "v25", "v26", "v27", "v28", "v29", "v30"
  );
}
#endif  // HAS_I422TOARGB1555ROW_NEON

#define ARGBTOARGB4444                                                         \
    /* Input v20.8b<=B, v21.8b<=G, v22.8b<=R, v23.8b<=A, v4.8b<=0x0f        */ \
    "ushr       v20.8b, v20.8b, #4             \n"  /* B                    */ \
    "bic        v21.8b, v21.8b, v4.8b          \n"  /* G                    */ \
    "ushr       v22.8b, v22.8b, #4             \n"  /* R                    */ \
    "bic        v23.8b, v23.8b, v4.8b          \n"  /* A                    */ \
    "orr        v0.8b,  v20.8b, v21.8b         \n"  /* BG                   */ \
    "orr        v1.8b,  v22.8b, v23.8b         \n"  /* RA                   */ \
    "zip1       v0.16b, v0.16b, v1.16b         \n"  /* BGRA                 */

#ifdef HAS_I422TOARGB4444ROW_NEON
void I422ToARGB4444Row_NEON(const uint8* src_y,
                            const uint8* src_u,
                            const uint8* src_v,
                            uint8* dst_argb4444,
                            const struct YuvConstants* yuvconstants,
                            int width) {
  asm volatile (
    YUVTORGB_SETUP
    "movi       v4.16b, #0x0f                  \n"  // bits to clear with vbic.
  "1:                                          \n"
    READYUV422
    YUVTORGB(v22, v21, v20)
    "subs       %w4, %w4, #8                   \n"
    "movi       v23.8b, #255                   \n"
    ARGBTOARGB4444
    MEMACCESS(3)
    "st1        {v0.8h}, [%3], #16             \n"  // store 8 pixels ARGB4444.
    "b.gt       1b                             \n"
    : "+r"(src_y),    // %0
      "+r"(src_u),    // %1
      "+r"(src_v),    // %2
      "+r"(dst_argb4444),  // %3
      "+r"(width)     // %4
    : [kUVToRB]"r"(&yuvconstants->kUVToRB),
      [kUVToG]"r"(&yuvconstants->kUVToG),
      [kUVBiasBGR]"r"(&yuvconstants->kUVBiasBGR),
      [kYToRgb]"r"(&yuvconstants->kYToRgb)
    : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v20",
      "v21", "v22", "v23", "v24", "v25", "v26", "v27", "v28", "v29", "v30"
  );
}
#endif  // HAS_I422TOARGB4444ROW_NEON

#ifdef HAS_I400TOARGBROW_NEON
void I400ToARGBRow_NEON(const uint8* src_y,
                        uint8* dst_argb,
                        int width) {
  int64 width64 = (int64)(width);
  asm volatile (
    YUVTORGB_SETUP
    "movi       v23.8b, #255                   \n"
  "1:                                          \n"
    READYUV400
    YUVTORGB(v22, v21, v20)
    "subs       %w2, %w2, #8                   \n"
    MEMACCESS(1)
    "st4        {v20.8b,v21.8b,v22.8b,v23.8b}, [%1], #32     \n"
    "b.gt       1b                             \n"
    : "+r"(src_y),     // %0
      "+r"(dst_argb),  // %1
      "+r"(width64)    // %2
    : [kUVToRB]"r"(&kYuvI601Constants.kUVToRB),
      [kUVToG]"r"(&kYuvI601Constants.kUVToG),
      [kUVBiasBGR]"r"(&kYuvI601Constants.kUVBiasBGR),
      [kYToRgb]"r"(&kYuvI601Constants.kYToRgb)
    : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v20",
      "v21", "v22", "v23", "v24", "v25", "v26", "v27", "v28", "v29", "v30"
  );
}
#endif  // HAS_I400TOARGBROW_NEON

#ifdef HAS_J400TOARGBROW_NEON
void J400ToARGBRow_NEON(const uint8* src_y,
                        uint8* dst_argb,
                        int width) {
  asm volatile (
    "movi       v23.8b, #255                   \n"
  "1:                                          \n"
    MEMACCESS(0)
    "ld1        {v20.8b}, [%0], #8             \n"
    "orr        v21.8b, v20.8b, v20.8b         \n"
    "orr        v22.8b, v20.8b, v20.8b         \n"
    "subs       %w2, %w2, #8                   \n"
    MEMACCESS(1)
    "st4        {v20.8b,v21.8b,v22.8b,v23.8b}, [%1], #32     \n"
    "b.gt       1b                             \n"
    : "+r"(src_y),     // %0
      "+r"(dst_argb),  // %1
      "+r"(width)      // %2
    :
    : "cc", "memory", "v20", "v21", "v22", "v23"
  );
}
#endif  // HAS_J400TOARGBROW_NEON

#ifdef HAS_NV12TOARGBROW_NEON
void NV12ToARGBRow_NEON(const uint8* src_y,
                        const uint8* src_uv,
                        uint8* dst_argb,
                        const struct YuvConstants* yuvconstants,
                        int width) {
  asm volatile (
    YUVTORGB_SETUP
    "movi       v23.8b, #255                   \n"
  "1:                                          \n"
    READNV12
    YUVTORGB(v22, v21, v20)
    "subs       %w3, %w3, #8                   \n"
    MEMACCESS(2)
    "st4        {v20.8b,v21.8b,v22.8b,v23.8b}, [%2], #32     \n"
    "b.gt       1b                             \n"
    : "+r"(src_y),     // %0
      "+r"(src_uv),    // %1
      "+r"(dst_argb),  // %2
      "+r"(width)      // %3
    : [kUVToRB]"r"(&yuvconstants->kUVToRB),
      [kUVToG]"r"(&yuvconstants->kUVToG),
      [kUVBiasBGR]"r"(&yuvconstants->kUVBiasBGR),
      [kYToRgb]"r"(&yuvconstants->kYToRgb)
    : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v20",
      "v21", "v22", "v23", "v24", "v25", "v26", "v27", "v28", "v29", "v30"
  );
}
#endif  // HAS_NV12TOARGBROW_NEON

#ifdef HAS_NV12TOARGBROW_NEON
void NV21ToARGBRow_NEON(const uint8* src_y,
                        const uint8* src_vu,
                        uint8* dst_argb,
                        const struct YuvConstants* yuvconstants,
                        int width) {
  asm volatile (
    YUVTORGB_SETUP
    "movi       v23.8b, #255                   \n"
  "1:                                          \n"
    READNV21
    YUVTORGB(v22, v21, v20)
    "subs       %w3, %w3, #8                   \n"
    MEMACCESS(2)
    "st4        {v20.8b,v21.8b,v22.8b,v23.8b}, [%2], #32     \n"
    "b.gt       1b                             \n"
    : "+r"(src_y),     // %0
      "+r"(src_vu),    // %1
      "+r"(dst_argb),  // %2
      "+r"(width)      // %3
    : [kUVToRB]"r"(&yuvconstants->kUVToRB),
      [kUVToG]"r"(&yuvconstants->kUVToG),
      [kUVBiasBGR]"r"(&yuvconstants->kUVBiasBGR),
      [kYToRgb]"r"(&yuvconstants->kYToRgb)
    : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v20",
      "v21", "v22", "v23", "v24", "v25", "v26", "v27", "v28", "v29", "v30"
  );
}
#endif  // HAS_NV12TOARGBROW_NEON

#ifdef HAS_NV12TORGB565ROW_NEON
void NV12ToRGB565Row_NEON(const uint8* src_y,
                          const uint8* src_uv,
                          uint8* dst_rgb565,
                          const struct YuvConstants* yuvconstants,
                          int width) {
  asm volatile (
    YUVTORGB_SETUP
  "1:                                          \n"
    READNV12
    YUVTORGB(v22, v21, v20)
    "subs       %w3, %w3, #8                   \n"
    ARGBTORGB565
    MEMACCESS(2)
    "st1        {v0.8h}, [%2], 16              \n"  // store 8 pixels RGB565.
    "b.gt       1b                             \n"
    : "+r"(src_y),     // %0
      "+r"(src_uv),    // %1
      "+r"(dst_rgb565),  // %2
      "+r"(width)      // %3
    : [kUVToRB]"r"(&yuvconstants->kUVToRB),
      [kUVToG]"r"(&yuvconstants->kUVToG),
      [kUVBiasBGR]"r"(&yuvconstants->kUVBiasBGR),
      [kYToRgb]"r"(&yuvconstants->kYToRgb)
    : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v20",
      "v21", "v22", "v23", "v24", "v25", "v26", "v27", "v28", "v29", "v30"
  );
}
#endif  // HAS_NV12TORGB565ROW_NEON

#ifdef HAS_YUY2TOARGBROW_NEON
void YUY2ToARGBRow_NEON(const uint8* src_yuy2,
                        uint8* dst_argb,
                        const struct YuvConstants* yuvconstants,
                        int width) {
  int64 width64 = (int64)(width);
  asm volatile (
    YUVTORGB_SETUP
    "movi       v23.8b, #255                   \n"
  "1:                                          \n"
    READYUY2
    YUVTORGB(v22, v21, v20)
    "subs       %w2, %w2, #8                   \n"
    MEMACCESS(1)
    "st4        {v20.8b,v21.8b,v22.8b,v23.8b}, [%1], #32      \n"
    "b.gt       1b                             \n"
    : "+r"(src_yuy2),  // %0
      "+r"(dst_argb),  // %1
      "+r"(width64)    // %2
    : [kUVToRB]"r"(&yuvconstants->kUVToRB),
      [kUVToG]"r"(&yuvconstants->kUVToG),
      [kUVBiasBGR]"r"(&yuvconstants->kUVBiasBGR),
      [kYToRgb]"r"(&yuvconstants->kYToRgb)
    : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v20",
      "v21", "v22", "v23", "v24", "v25", "v26", "v27", "v28", "v29", "v30"
  );
}
#endif  // HAS_YUY2TOARGBROW_NEON

#ifdef HAS_UYVYTOARGBROW_NEON
void UYVYToARGBRow_NEON(const uint8* src_uyvy,
                        uint8* dst_argb,
                        const struct YuvConstants* yuvconstants,
                        int width) {
  int64 width64 = (int64)(width);
  asm volatile (
    YUVTORGB_SETUP
    "movi       v23.8b, #255                   \n"
  "1:                                          \n"
    READUYVY
    YUVTORGB(v22, v21, v20)
    "subs       %w2, %w2, #8                   \n"
    MEMACCESS(1)
    "st4        {v20.8b,v21.8b,v22.8b,v23.8b}, [%1], 32      \n"
    "b.gt       1b                             \n"
    : "+r"(src_uyvy),  // %0
      "+r"(dst_argb),  // %1
      "+r"(width64)    // %2
    : [kUVToRB]"r"(&yuvconstants->kUVToRB),
      [kUVToG]"r"(&yuvconstants->kUVToG),
      [kUVBiasBGR]"r"(&yuvconstants->kUVBiasBGR),
      [kYToRgb]"r"(&yuvconstants->kYToRgb)
    : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v20",
      "v21", "v22", "v23", "v24", "v25", "v26", "v27", "v28", "v29", "v30"
  );
}
#endif  // HAS_UYVYTOARGBROW_NEON

// Reads 16 pairs of UV and write even values to dst_u and odd to dst_v.
#ifdef HAS_SPLITUVROW_NEON
void SplitUVRow_NEON(const uint8* src_uv, uint8* dst_u, uint8* dst_v,
                     int width) {
  asm volatile (
  "1:                                          \n"
    MEMACCESS(0)
    "ld2        {v0.16b,v1.16b}, [%0], #32     \n"  // load 16 pairs of UV
    "subs       %w3, %w3, #16                  \n"  // 16 processed per loop
    MEMACCESS(1)
    "st1        {v0.16b}, [%1], #16            \n"  // store U
    MEMACCESS(2)
    "st1        {v1.16b}, [%2], #16            \n"  // store V
    "b.gt       1b                             \n"
    : "+r"(src_uv),  // %0
      "+r"(dst_u),   // %1
      "+r"(dst_v),   // %2
      "+r"(width)    // %3  // Output registers
    :                       // Input registers
    : "cc", "memory", "v0", "v1"  // Clobber List
  );
}
#endif  // HAS_SPLITUVROW_NEON

// Reads 16 U's and V's and writes out 16 pairs of UV.
#ifdef HAS_MERGEUVROW_NEON
void MergeUVRow_NEON(const uint8* src_u, const uint8* src_v, uint8* dst_uv,
                     int width) {
  asm volatile (
  "1:                                          \n"
    MEMACCESS(0)
    "ld1        {v0.16b}, [%0], #16            \n"  // load U
    MEMACCESS(1)
    "ld1        {v1.16b}, [%1], #16            \n"  // load V
    "subs       %w3, %w3, #16                  \n"  // 16 processed per loop
    MEMACCESS(2)
    "st2        {v0.16b,v1.16b}, [%2], #32     \n"  // store 16 pairs of UV
    "b.gt       1b                             \n"
    :
      "+r"(src_u),   // %0
      "+r"(src_v),   // %1
      "+r"(dst_uv),  // %2
      "+r"(width)    // %3  // Output registers
    :                       // Input registers
    : "cc", "memory", "v0", "v1"  // Clobber List
  );
}
#endif  // HAS_MERGEUVROW_NEON

// Copy multiple of 32.  vld4.8  allow unaligned and is fastest on a15.
#ifdef HAS_COPYROW_NEON
void CopyRow_NEON(const uint8* src, uint8* dst, int count) {
  asm volatile (
  "1:                                          \n"
    MEMACCESS(0)
    "ld1        {v0.8b,v1.8b,v2.8b,v3.8b}, [%0], #32       \n"  // load 32
    "subs       %w2, %w2, #32                  \n"  // 32 processed per loop
    MEMACCESS(1)
    "st1        {v0.8b,v1.8b,v2.8b,v3.8b}, [%1], #32       \n"  // store 32
    "b.gt       1b                             \n"
  : "+r"(src),   // %0
    "+r"(dst),   // %1
    "+r"(count)  // %2  // Output registers
  :                     // Input registers
  : "cc", "memory", "v0", "v1", "v2", "v3"  // Clobber List
  );
}
#endif  // HAS_COPYROW_NEON

// SetRow writes 'count' bytes using an 8 bit value repeated.
void SetRow_NEON(uint8* dst, uint8 v8, int count) {
  asm volatile (
    "dup        v0.16b, %w2                    \n"  // duplicate 16 bytes
  "1:                                          \n"
    "subs      %w1, %w1, #16                   \n"  // 16 bytes per loop
    MEMACCESS(0)
    "st1        {v0.16b}, [%0], #16            \n"  // store
    "b.gt      1b                              \n"
  : "+r"(dst),   // %0
    "+r"(count)  // %1
  : "r"(v8)      // %2
  : "cc", "memory", "v0"
  );
}

void ARGBSetRow_NEON(uint8* dst, uint32 v32, int count) {
  asm volatile (
    "dup        v0.4s, %w2                     \n"  // duplicate 4 ints
  "1:                                          \n"
    "subs      %w1, %w1, #4                    \n"  // 4 ints per loop
    MEMACCESS(0)
    "st1        {v0.16b}, [%0], #16            \n"  // store
    "b.gt      1b                              \n"
  : "+r"(dst),   // %0
    "+r"(count)  // %1
  : "r"(v32)     // %2
  : "cc", "memory", "v0"
  );
}

#ifdef HAS_MIRRORROW_NEON
void MirrorRow_NEON(const uint8* src, uint8* dst, int width) {
  int64 width64 = (int64) width;
  asm volatile (
    // Start at end of source row.
    "add        %0, %0, %2                     \n"
    "sub        %0, %0, #16                    \n"

  "1:                                          \n"
    MEMACCESS(0)
    "ld1        {v0.16b}, [%0], %3             \n"  // src -= 16
    "subs       %2, %2, #16                   \n"  // 16 pixels per loop.
    "rev64      v0.16b, v0.16b                 \n"
    MEMACCESS(1)
    "st1        {v0.D}[1], [%1], #8            \n"  // dst += 16
    MEMACCESS(1)
    "st1        {v0.D}[0], [%1], #8            \n"
    "b.gt       1b                             \n"
  : "+r"(src),   // %0
    "+r"(dst),   // %1
    "+r"(width64)  // %2
  : "r"((ptrdiff_t)-16)    // %3
  : "cc", "memory", "v0"
  );
}
#endif  // HAS_MIRRORROW_NEON

#ifdef HAS_MIRRORUVROW_NEON
void MirrorUVRow_NEON(const uint8* src_uv, uint8* dst_u, uint8* dst_v,
                      int width) {
  int64 width64 = (int64) width;
  asm volatile (
    // Start at end of source row.
    "add        %0, %0, %3, lsl #1             \n"
    "sub        %0, %0, #16                    \n"

  "1:                                          \n"
    MEMACCESS(0)
    "ld2        {v0.8b, v1.8b}, [%0], %4       \n"  // src -= 16
    "subs       %3, %3, #8                     \n"  // 8 pixels per loop.
    "rev64      v0.8b, v0.8b                   \n"
    "rev64      v1.8b, v1.8b                   \n"
    MEMACCESS(1)
    "st1        {v0.8b}, [%1], #8              \n"  // dst += 8
    MEMACCESS(2)
    "st1        {v1.8b}, [%2], #8              \n"
    "b.gt       1b                             \n"
  : "+r"(src_uv),  // %0
    "+r"(dst_u),   // %1
    "+r"(dst_v),   // %2
    "+r"(width64)    // %3
  : "r"((ptrdiff_t)-16)      // %4
  : "cc", "memory", "v0", "v1"
  );
}
#endif  // HAS_MIRRORUVROW_NEON

#ifdef HAS_ARGBMIRRORROW_NEON
void ARGBMirrorRow_NEON(const uint8* src, uint8* dst, int width) {
  int64 width64 = (int64) width;
  asm volatile (
    // Start at end of source row.
    "add        %0, %0, %2, lsl #2             \n"
    "sub        %0, %0, #16                    \n"

  "1:                                          \n"
    MEMACCESS(0)
    "ld1        {v0.16b}, [%0], %3             \n"  // src -= 16
    "subs       %2, %2, #4                     \n"  // 4 pixels per loop.
    "rev64      v0.4s, v0.4s                   \n"
    MEMACCESS(1)
    "st1        {v0.D}[1], [%1], #8            \n"  // dst += 16
    MEMACCESS(1)
    "st1        {v0.D}[0], [%1], #8            \n"
    "b.gt       1b                             \n"
  : "+r"(src),   // %0
    "+r"(dst),   // %1
    "+r"(width64)  // %2
  : "r"((ptrdiff_t)-16)    // %3
  : "cc", "memory", "v0"
  );
}
#endif  // HAS_ARGBMIRRORROW_NEON

#ifdef HAS_RGB24TOARGBROW_NEON
void RGB24ToARGBRow_NEON(const uint8* src_rgb24, uint8* dst_argb, int width) {
  asm volatile (
    "movi       v4.8b, #255                    \n"  // Alpha
  "1:                                          \n"
    MEMACCESS(0)
    "ld3        {v1.8b,v2.8b,v3.8b}, [%0], #24 \n"  // load 8 pixels of RGB24.
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    MEMACCESS(1)
    "st4        {v1.8b,v2.8b,v3.8b,v4.8b}, [%1], #32 \n"  // store 8 ARGB pixels
    "b.gt       1b                             \n"
  : "+r"(src_rgb24),  // %0
    "+r"(dst_argb),   // %1
    "+r"(width)         // %2
  :
  : "cc", "memory", "v1", "v2", "v3", "v4"  // Clobber List
  );
}
#endif  // HAS_RGB24TOARGBROW_NEON

#ifdef HAS_RAWTOARGBROW_NEON
void RAWToARGBRow_NEON(const uint8* src_raw, uint8* dst_argb, int width) {
  asm volatile (
    "movi       v5.8b, #255                    \n"  // Alpha
  "1:                                          \n"
    MEMACCESS(0)
    "ld3        {v0.8b,v1.8b,v2.8b}, [%0], #24 \n"  // read r g b
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    "orr        v3.8b, v1.8b, v1.8b            \n"  // move g
    "orr        v4.8b, v0.8b, v0.8b            \n"  // move r
    MEMACCESS(1)
    "st4        {v2.8b,v3.8b,v4.8b,v5.8b}, [%1], #32 \n"  // store b g r a
    "b.gt       1b                             \n"
  : "+r"(src_raw),   // %0
    "+r"(dst_argb),  // %1
    "+r"(width)        // %2
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5"  // Clobber List
  );
}
#endif  // HAS_RAWTOARGBROW_NEON

void RAWToRGB24Row_NEON(const uint8* src_raw, uint8* dst_rgb24, int width) {
  asm volatile (
  "1:                                          \n"
    MEMACCESS(0)
    "ld3        {v0.8b,v1.8b,v2.8b}, [%0], #24 \n"  // read r g b
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    "orr        v3.8b, v1.8b, v1.8b            \n"  // move g
    "orr        v4.8b, v0.8b, v0.8b            \n"  // move r
    MEMACCESS(1)
    "st3        {v2.8b,v3.8b,v4.8b}, [%1], #24 \n"  // store b g r
    "b.gt       1b                             \n"
  : "+r"(src_raw),    // %0
    "+r"(dst_rgb24),  // %1
    "+r"(width)       // %2
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4"  // Clobber List
  );
}

#define RGB565TOARGB                                                           \
    "shrn       v6.8b, v0.8h, #5               \n"  /* G xxGGGGGG           */ \
    "shl        v6.8b, v6.8b, #2               \n"  /* G GGGGGG00 upper 6   */ \
    "ushr       v4.8b, v6.8b, #6               \n"  /* G 000000GG lower 2   */ \
    "orr        v1.8b, v4.8b, v6.8b            \n"  /* G                    */ \
    "xtn        v2.8b, v0.8h                   \n"  /* B xxxBBBBB           */ \
    "ushr       v0.8h, v0.8h, #11              \n"  /* R 000RRRRR           */ \
    "xtn2       v2.16b,v0.8h                   \n"  /* R in upper part      */ \
    "shl        v2.16b, v2.16b, #3             \n"  /* R,B BBBBB000 upper 5 */ \
    "ushr       v0.16b, v2.16b, #5             \n"  /* R,B 00000BBB lower 3 */ \
    "orr        v0.16b, v0.16b, v2.16b         \n"  /* R,B                  */ \
    "dup        v2.2D, v0.D[1]                 \n"  /* R                    */

#ifdef HAS_RGB565TOARGBROW_NEON
void RGB565ToARGBRow_NEON(const uint8* src_rgb565, uint8* dst_argb, int width) {
  asm volatile (
    "movi       v3.8b, #255                    \n"  // Alpha
  "1:                                          \n"
    MEMACCESS(0)
    "ld1        {v0.16b}, [%0], #16            \n"  // load 8 RGB565 pixels.
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    RGB565TOARGB
    MEMACCESS(1)
    "st4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%1], #32 \n"  // store 8 ARGB pixels
    "b.gt       1b                             \n"
  : "+r"(src_rgb565),  // %0
    "+r"(dst_argb),    // %1
    "+r"(width)          // %2
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v6"  // Clobber List
  );
}
#endif  // HAS_RGB565TOARGBROW_NEON

#define ARGB1555TOARGB                                                         \
    "ushr       v2.8h, v0.8h, #10              \n"  /* R xxxRRRRR           */ \
    "shl        v2.8h, v2.8h, #3               \n"  /* R RRRRR000 upper 5   */ \
    "xtn        v3.8b, v2.8h                   \n"  /* RRRRR000 AAAAAAAA    */ \
                                                                               \
    "sshr       v2.8h, v0.8h, #15              \n"  /* A AAAAAAAA           */ \
    "xtn2       v3.16b, v2.8h                  \n"                             \
                                                                               \
    "xtn        v2.8b, v0.8h                   \n"  /* B xxxBBBBB           */ \
    "shrn2      v2.16b,v0.8h, #5               \n"  /* G xxxGGGGG           */ \
                                                                               \
    "ushr       v1.16b, v3.16b, #5             \n"  /* R,A 00000RRR lower 3 */ \
    "shl        v0.16b, v2.16b, #3             \n"  /* B,G BBBBB000 upper 5 */ \
    "ushr       v2.16b, v0.16b, #5             \n"  /* B,G 00000BBB lower 3 */ \
                                                                               \
    "orr        v0.16b, v0.16b, v2.16b         \n"  /* B,G                  */ \
    "orr        v2.16b, v1.16b, v3.16b         \n"  /* R,A                  */ \
    "dup        v1.2D, v0.D[1]                 \n"                             \
    "dup        v3.2D, v2.D[1]                 \n"

// RGB555TOARGB is same as ARGB1555TOARGB but ignores alpha.
#define RGB555TOARGB                                                           \
    "ushr       v2.8h, v0.8h, #10              \n"  /* R xxxRRRRR           */ \
    "shl        v2.8h, v2.8h, #3               \n"  /* R RRRRR000 upper 5   */ \
    "xtn        v3.8b, v2.8h                   \n"  /* RRRRR000             */ \
                                                                               \
    "xtn        v2.8b, v0.8h                   \n"  /* B xxxBBBBB           */ \
    "shrn2      v2.16b,v0.8h, #5               \n"  /* G xxxGGGGG           */ \
                                                                               \
    "ushr       v1.16b, v3.16b, #5             \n"  /* R   00000RRR lower 3 */ \
    "shl        v0.16b, v2.16b, #3             \n"  /* B,G BBBBB000 upper 5 */ \
    "ushr       v2.16b, v0.16b, #5             \n"  /* B,G 00000BBB lower 3 */ \
                                                                               \
    "orr        v0.16b, v0.16b, v2.16b         \n"  /* B,G                  */ \
    "orr        v2.16b, v1.16b, v3.16b         \n"  /* R                    */ \
    "dup        v1.2D, v0.D[1]                 \n"  /* G */                    \

#ifdef HAS_ARGB1555TOARGBROW_NEON
void ARGB1555ToARGBRow_NEON(const uint8* src_argb1555, uint8* dst_argb,
                            int width) {
  asm volatile (
    "movi       v3.8b, #255                    \n"  // Alpha
  "1:                                          \n"
    MEMACCESS(0)
    "ld1        {v0.16b}, [%0], #16            \n"  // load 8 ARGB1555 pixels.
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    ARGB1555TOARGB
    MEMACCESS(1)
    "st4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%1], #32 \n"  // store 8 ARGB pixels
    "b.gt       1b                             \n"
  : "+r"(src_argb1555),  // %0
    "+r"(dst_argb),    // %1
    "+r"(width)          // %2
  :
  : "cc", "memory", "v0", "v1", "v2", "v3"  // Clobber List
  );
}
#endif  // HAS_ARGB1555TOARGBROW_NEON

#define ARGB4444TOARGB                                                         \
    "shrn       v1.8b,  v0.8h, #8              \n"  /* v1(l) AR             */ \
    "xtn2       v1.16b, v0.8h                  \n"  /* v1(h) GB             */ \
    "shl        v2.16b, v1.16b, #4             \n"  /* B,R BBBB0000         */ \
    "ushr       v3.16b, v1.16b, #4             \n"  /* G,A 0000GGGG         */ \
    "ushr       v0.16b, v2.16b, #4             \n"  /* B,R 0000BBBB         */ \
    "shl        v1.16b, v3.16b, #4             \n"  /* G,A GGGG0000         */ \
    "orr        v2.16b, v0.16b, v2.16b         \n"  /* B,R BBBBBBBB         */ \
    "orr        v3.16b, v1.16b, v3.16b         \n"  /* G,A GGGGGGGG         */ \
    "dup        v0.2D, v2.D[1]                 \n"                             \
    "dup        v1.2D, v3.D[1]                 \n"

#ifdef HAS_ARGB4444TOARGBROW_NEON
void ARGB4444ToARGBRow_NEON(const uint8* src_argb4444, uint8* dst_argb,
                            int width) {
  asm volatile (
  "1:                                          \n"
    MEMACCESS(0)
    "ld1        {v0.16b}, [%0], #16            \n"  // load 8 ARGB4444 pixels.
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    ARGB4444TOARGB
    MEMACCESS(1)
    "st4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%1], #32 \n"  // store 8 ARGB pixels
    "b.gt       1b                             \n"
  : "+r"(src_argb4444),  // %0
    "+r"(dst_argb),    // %1
    "+r"(width)          // %2
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4"  // Clobber List
  );
}
#endif  // HAS_ARGB4444TOARGBROW_NEON

#ifdef HAS_ARGBTORGB24ROW_NEON
void ARGBToRGB24Row_NEON(const uint8* src_argb, uint8* dst_rgb24, int width) {
  asm volatile (
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v1.8b,v2.8b,v3.8b,v4.8b}, [%0], #32 \n"  // load 8 ARGB pixels
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    MEMACCESS(1)
    "st3        {v1.8b,v2.8b,v3.8b}, [%1], #24 \n"  // store 8 pixels of RGB24.
    "b.gt       1b                             \n"
  : "+r"(src_argb),   // %0
    "+r"(dst_rgb24),  // %1
    "+r"(width)         // %2
  :
  : "cc", "memory", "v1", "v2", "v3", "v4"  // Clobber List
  );
}
#endif  // HAS_ARGBTORGB24ROW_NEON

#ifdef HAS_ARGBTORAWROW_NEON
void ARGBToRAWRow_NEON(const uint8* src_argb, uint8* dst_raw, int width) {
  asm volatile (
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v1.8b,v2.8b,v3.8b,v4.8b}, [%0], #32 \n"  // load b g r a
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    "orr        v4.8b, v2.8b, v2.8b            \n"  // mov g
    "orr        v5.8b, v1.8b, v1.8b            \n"  // mov b
    MEMACCESS(1)
    "st3        {v3.8b,v4.8b,v5.8b}, [%1], #24 \n"  // store r g b
    "b.gt       1b                             \n"
  : "+r"(src_argb),  // %0
    "+r"(dst_raw),   // %1
    "+r"(width)        // %2
  :
  : "cc", "memory", "v1", "v2", "v3", "v4", "v5"  // Clobber List
  );
}
#endif  // HAS_ARGBTORAWROW_NEON

#ifdef HAS_YUY2TOYROW_NEON
void YUY2ToYRow_NEON(const uint8* src_yuy2, uint8* dst_y, int width) {
  asm volatile (
  "1:                                          \n"
    MEMACCESS(0)
    "ld2        {v0.16b,v1.16b}, [%0], #32     \n"  // load 16 pixels of YUY2.
    "subs       %w2, %w2, #16                  \n"  // 16 processed per loop.
    MEMACCESS(1)
    "st1        {v0.16b}, [%1], #16            \n"  // store 16 pixels of Y.
    "b.gt       1b                             \n"
  : "+r"(src_yuy2),  // %0
    "+r"(dst_y),     // %1
    "+r"(width)        // %2
  :
  : "cc", "memory", "v0", "v1"  // Clobber List
  );
}
#endif  // HAS_YUY2TOYROW_NEON

#ifdef HAS_UYVYTOYROW_NEON
void UYVYToYRow_NEON(const uint8* src_uyvy, uint8* dst_y, int width) {
  asm volatile (
  "1:                                          \n"
    MEMACCESS(0)
    "ld2        {v0.16b,v1.16b}, [%0], #32     \n"  // load 16 pixels of UYVY.
    "subs       %w2, %w2, #16                  \n"  // 16 processed per loop.
    MEMACCESS(1)
    "st1        {v1.16b}, [%1], #16            \n"  // store 16 pixels of Y.
    "b.gt       1b                             \n"
  : "+r"(src_uyvy),  // %0
    "+r"(dst_y),     // %1
    "+r"(width)        // %2
  :
  : "cc", "memory", "v0", "v1"  // Clobber List
  );
}
#endif  // HAS_UYVYTOYROW_NEON

#ifdef HAS_YUY2TOUV422ROW_NEON
void YUY2ToUV422Row_NEON(const uint8* src_yuy2, uint8* dst_u, uint8* dst_v,
                         int width) {
  asm volatile (
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%0], #32 \n"  // load 16 YUY2 pixels
    "subs       %w3, %w3, #16                  \n"  // 16 pixels = 8 UVs.
    MEMACCESS(1)
    "st1        {v1.8b}, [%1], #8              \n"  // store 8 U.
    MEMACCESS(2)
    "st1        {v3.8b}, [%2], #8              \n"  // store 8 V.
    "b.gt       1b                             \n"
  : "+r"(src_yuy2),  // %0
    "+r"(dst_u),     // %1
    "+r"(dst_v),     // %2
    "+r"(width)        // %3
  :
  : "cc", "memory", "v0", "v1", "v2", "v3"  // Clobber List
  );
}
#endif  // HAS_YUY2TOUV422ROW_NEON

#ifdef HAS_UYVYTOUV422ROW_NEON
void UYVYToUV422Row_NEON(const uint8* src_uyvy, uint8* dst_u, uint8* dst_v,
                         int width) {
  asm volatile (
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%0], #32 \n"  // load 16 UYVY pixels
    "subs       %w3, %w3, #16                  \n"  // 16 pixels = 8 UVs.
    MEMACCESS(1)
    "st1        {v0.8b}, [%1], #8              \n"  // store 8 U.
    MEMACCESS(2)
    "st1        {v2.8b}, [%2], #8              \n"  // store 8 V.
    "b.gt       1b                             \n"
  : "+r"(src_uyvy),  // %0
    "+r"(dst_u),     // %1
    "+r"(dst_v),     // %2
    "+r"(width)        // %3
  :
  : "cc", "memory", "v0", "v1", "v2", "v3"  // Clobber List
  );
}
#endif  // HAS_UYVYTOUV422ROW_NEON

#ifdef HAS_YUY2TOUVROW_NEON
void YUY2ToUVRow_NEON(const uint8* src_yuy2, int stride_yuy2,
                      uint8* dst_u, uint8* dst_v, int width) {
  const uint8* src_yuy2b = src_yuy2 + stride_yuy2;
  asm volatile (
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%0], #32 \n"  // load 16 pixels
    "subs       %w4, %w4, #16                  \n"  // 16 pixels = 8 UVs.
    MEMACCESS(1)
    "ld4        {v4.8b,v5.8b,v6.8b,v7.8b}, [%1], #32 \n"  // load next row
    "urhadd     v1.8b, v1.8b, v5.8b            \n"  // average rows of U
    "urhadd     v3.8b, v3.8b, v7.8b            \n"  // average rows of V
    MEMACCESS(2)
    "st1        {v1.8b}, [%2], #8              \n"  // store 8 U.
    MEMACCESS(3)
    "st1        {v3.8b}, [%3], #8              \n"  // store 8 V.
    "b.gt       1b                             \n"
  : "+r"(src_yuy2),     // %0
    "+r"(src_yuy2b),    // %1
    "+r"(dst_u),        // %2
    "+r"(dst_v),        // %3
    "+r"(width)           // %4
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4",
    "v5", "v6", "v7"  // Clobber List
  );
}
#endif  // HAS_YUY2TOUVROW_NEON

#ifdef HAS_UYVYTOUVROW_NEON
void UYVYToUVRow_NEON(const uint8* src_uyvy, int stride_uyvy,
                      uint8* dst_u, uint8* dst_v, int width) {
  const uint8* src_uyvyb = src_uyvy + stride_uyvy;
  asm volatile (
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%0], #32 \n"  // load 16 pixels
    "subs       %w4, %w4, #16                  \n"  // 16 pixels = 8 UVs.
    MEMACCESS(1)
    "ld4        {v4.8b,v5.8b,v6.8b,v7.8b}, [%1], #32 \n"  // load next row
    "urhadd     v0.8b, v0.8b, v4.8b            \n"  // average rows of U
    "urhadd     v2.8b, v2.8b, v6.8b            \n"  // average rows of V
    MEMACCESS(2)
    "st1        {v0.8b}, [%2], #8              \n"  // store 8 U.
    MEMACCESS(3)
    "st1        {v2.8b}, [%3], #8              \n"  // store 8 V.
    "b.gt       1b                             \n"
  : "+r"(src_uyvy),     // %0
    "+r"(src_uyvyb),    // %1
    "+r"(dst_u),        // %2
    "+r"(dst_v),        // %3
    "+r"(width)           // %4
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4",
    "v5", "v6", "v7"  // Clobber List
  );
}
#endif  // HAS_UYVYTOUVROW_NEON

// For BGRAToARGB, ABGRToARGB, RGBAToARGB, and ARGBToRGBA.
#ifdef HAS_ARGBSHUFFLEROW_NEON
void ARGBShuffleRow_NEON(const uint8* src_argb, uint8* dst_argb,
                         const uint8* shuffler, int width) {
  asm volatile (
    MEMACCESS(3)
    "ld1        {v2.16b}, [%3]                 \n"  // shuffler
  "1:                                          \n"
    MEMACCESS(0)
    "ld1        {v0.16b}, [%0], #16            \n"  // load 4 pixels.
    "subs       %w2, %w2, #4                   \n"  // 4 processed per loop
    "tbl        v1.16b, {v0.16b}, v2.16b       \n"  // look up 4 pixels
    MEMACCESS(1)
    "st1        {v1.16b}, [%1], #16            \n"  // store 4.
    "b.gt       1b                             \n"
  : "+r"(src_argb),  // %0
    "+r"(dst_argb),  // %1
    "+r"(width)        // %2
  : "r"(shuffler)    // %3
  : "cc", "memory", "v0", "v1", "v2"  // Clobber List
  );
}
#endif  // HAS_ARGBSHUFFLEROW_NEON

#ifdef HAS_I422TOYUY2ROW_NEON
void I422ToYUY2Row_NEON(const uint8* src_y,
                        const uint8* src_u,
                        const uint8* src_v,
                        uint8* dst_yuy2, int width) {
  asm volatile (
  "1:                                          \n"
    MEMACCESS(0)
    "ld2        {v0.8b, v1.8b}, [%0], #16      \n"  // load 16 Ys
    "orr        v2.8b, v1.8b, v1.8b            \n"
    MEMACCESS(1)
    "ld1        {v1.8b}, [%1], #8              \n"  // load 8 Us
    MEMACCESS(2)
    "ld1        {v3.8b}, [%2], #8              \n"  // load 8 Vs
    "subs       %w4, %w4, #16                  \n"  // 16 pixels
    MEMACCESS(3)
    "st4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%3], #32 \n"  // Store 16 pixels.
    "b.gt       1b                             \n"
  : "+r"(src_y),     // %0
    "+r"(src_u),     // %1
    "+r"(src_v),     // %2
    "+r"(dst_yuy2),  // %3
    "+r"(width)      // %4
  :
  : "cc", "memory", "v0", "v1", "v2", "v3"
  );
}
#endif  // HAS_I422TOYUY2ROW_NEON

#ifdef HAS_I422TOUYVYROW_NEON
void I422ToUYVYRow_NEON(const uint8* src_y,
                        const uint8* src_u,
                        const uint8* src_v,
                        uint8* dst_uyvy, int width) {
  asm volatile (
  "1:                                          \n"
    MEMACCESS(0)
    "ld2        {v1.8b,v2.8b}, [%0], #16       \n"  // load 16 Ys
    "orr        v3.8b, v2.8b, v2.8b            \n"
    MEMACCESS(1)
    "ld1        {v0.8b}, [%1], #8              \n"  // load 8 Us
    MEMACCESS(2)
    "ld1        {v2.8b}, [%2], #8              \n"  // load 8 Vs
    "subs       %w4, %w4, #16                  \n"  // 16 pixels
    MEMACCESS(3)
    "st4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%3], #32 \n"  // Store 16 pixels.
    "b.gt       1b                             \n"
  : "+r"(src_y),     // %0
    "+r"(src_u),     // %1
    "+r"(src_v),     // %2
    "+r"(dst_uyvy),  // %3
    "+r"(width)      // %4
  :
  : "cc", "memory", "v0", "v1", "v2", "v3"
  );
}
#endif  // HAS_I422TOUYVYROW_NEON

#ifdef HAS_ARGBTORGB565ROW_NEON
void ARGBToRGB565Row_NEON(const uint8* src_argb, uint8* dst_rgb565, int width) {
  asm volatile (
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v20.8b,v21.8b,v22.8b,v23.8b}, [%0], #32 \n"  // load 8 pixels
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    ARGBTORGB565
    MEMACCESS(1)
    "st1        {v0.16b}, [%1], #16            \n"  // store 8 pixels RGB565.
    "b.gt       1b                             \n"
  : "+r"(src_argb),  // %0
    "+r"(dst_rgb565),  // %1
    "+r"(width)        // %2
  :
  : "cc", "memory", "v0", "v20", "v21", "v22", "v23"
  );
}
#endif  // HAS_ARGBTORGB565ROW_NEON

#ifdef HAS_ARGBTORGB565DITHERROW_NEON
void ARGBToRGB565DitherRow_NEON(const uint8* src_argb, uint8* dst_rgb,
                                const uint32 dither4, int width) {
  asm volatile (
    "dup        v1.4s, %w2                     \n"  // dither4
  "1:                                          \n"
    MEMACCESS(1)
    "ld4        {v20.8b,v21.8b,v22.8b,v23.8b}, [%1], #32 \n"  // load 8 pixels
    "subs       %w3, %w3, #8                   \n"  // 8 processed per loop.
    "uqadd      v20.8b, v20.8b, v1.8b          \n"
    "uqadd      v21.8b, v21.8b, v1.8b          \n"
    "uqadd      v22.8b, v22.8b, v1.8b          \n"
    ARGBTORGB565
    MEMACCESS(0)
    "st1        {v0.16b}, [%0], #16            \n"  // store 8 pixels RGB565.
    "b.gt       1b                             \n"
  : "+r"(dst_rgb)    // %0
  : "r"(src_argb),   // %1
    "r"(dither4),    // %2
    "r"(width)       // %3
  : "cc", "memory", "v0", "v1", "v20", "v21", "v22", "v23"
  );
}
#endif  // HAS_ARGBTORGB565ROW_NEON

#ifdef HAS_ARGBTOARGB1555ROW_NEON
void ARGBToARGB1555Row_NEON(const uint8* src_argb, uint8* dst_argb1555,
                            int width) {
  asm volatile (
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v20.8b,v21.8b,v22.8b,v23.8b}, [%0], #32 \n"  // load 8 pixels
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    ARGBTOARGB1555
    MEMACCESS(1)
    "st1        {v0.16b}, [%1], #16            \n"  // store 8 pixels ARGB1555.
    "b.gt       1b                             \n"
  : "+r"(src_argb),  // %0
    "+r"(dst_argb1555),  // %1
    "+r"(width)        // %2
  :
  : "cc", "memory", "v0", "v20", "v21", "v22", "v23"
  );
}
#endif  // HAS_ARGBTOARGB1555ROW_NEON

#ifdef HAS_ARGBTOARGB4444ROW_NEON
void ARGBToARGB4444Row_NEON(const uint8* src_argb, uint8* dst_argb4444,
                            int width) {
  asm volatile (
    "movi       v4.16b, #0x0f                  \n"  // bits to clear with vbic.
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v20.8b,v21.8b,v22.8b,v23.8b}, [%0], #32 \n"  // load 8 pixels
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    ARGBTOARGB4444
    MEMACCESS(1)
    "st1        {v0.16b}, [%1], #16            \n"  // store 8 pixels ARGB4444.
    "b.gt       1b                             \n"
  : "+r"(src_argb),      // %0
    "+r"(dst_argb4444),  // %1
    "+r"(width)            // %2
  :
  : "cc", "memory", "v0", "v1", "v4", "v20", "v21", "v22", "v23"
  );
}
#endif  // HAS_ARGBTOARGB4444ROW_NEON

#ifdef HAS_ARGBTOYROW_NEON
void ARGBToYRow_NEON(const uint8* src_argb, uint8* dst_y, int width) {
  asm volatile (
    "movi       v4.8b, #13                     \n"  // B * 0.1016 coefficient
    "movi       v5.8b, #65                     \n"  // G * 0.5078 coefficient
    "movi       v6.8b, #33                     \n"  // R * 0.2578 coefficient
    "movi       v7.8b, #16                     \n"  // Add 16 constant
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%0], #32 \n"  // load 8 ARGB pixels.
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    "umull      v3.8h, v0.8b, v4.8b            \n"  // B
    "umlal      v3.8h, v1.8b, v5.8b            \n"  // G
    "umlal      v3.8h, v2.8b, v6.8b            \n"  // R
    "sqrshrun   v0.8b, v3.8h, #7               \n"  // 16 bit to 8 bit Y
    "uqadd      v0.8b, v0.8b, v7.8b            \n"
    MEMACCESS(1)
    "st1        {v0.8b}, [%1], #8              \n"  // store 8 pixels Y.
    "b.gt       1b                             \n"
  : "+r"(src_argb),  // %0
    "+r"(dst_y),     // %1
    "+r"(width)        // %2
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7"
  );
}
#endif  // HAS_ARGBTOYROW_NEON

#ifdef HAS_ARGBTOYJROW_NEON
void ARGBToYJRow_NEON(const uint8* src_argb, uint8* dst_y, int width) {
  asm volatile (
    "movi       v4.8b, #15                     \n"  // B * 0.11400 coefficient
    "movi       v5.8b, #75                     \n"  // G * 0.58700 coefficient
    "movi       v6.8b, #38                     \n"  // R * 0.29900 coefficient
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%0], #32 \n"  // load 8 ARGB pixels.
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    "umull      v3.8h, v0.8b, v4.8b            \n"  // B
    "umlal      v3.8h, v1.8b, v5.8b            \n"  // G
    "umlal      v3.8h, v2.8b, v6.8b            \n"  // R
    "sqrshrun   v0.8b, v3.8h, #7               \n"  // 15 bit to 8 bit Y
    MEMACCESS(1)
    "st1        {v0.8b}, [%1], #8              \n"  // store 8 pixels Y.
    "b.gt       1b                             \n"
  : "+r"(src_argb),  // %0
    "+r"(dst_y),     // %1
    "+r"(width)        // %2
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6"
  );
}
#endif  // HAS_ARGBTOYJROW_NEON

// 8x1 pixels.
#ifdef HAS_ARGBTOUV444ROW_NEON
void ARGBToUV444Row_NEON(const uint8* src_argb, uint8* dst_u, uint8* dst_v,
                         int width) {
  asm volatile (
    "movi       v24.8b, #112                   \n"  // UB / VR 0.875 coefficient
    "movi       v25.8b, #74                    \n"  // UG -0.5781 coefficient
    "movi       v26.8b, #38                    \n"  // UR -0.2969 coefficient
    "movi       v27.8b, #18                    \n"  // VB -0.1406 coefficient
    "movi       v28.8b, #94                    \n"  // VG -0.7344 coefficient
    "movi       v29.16b,#0x80                  \n"  // 128.5
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%0], #32 \n"  // load 8 ARGB pixels.
    "subs       %w3, %w3, #8                   \n"  // 8 processed per loop.
    "umull      v4.8h, v0.8b, v24.8b           \n"  // B
    "umlsl      v4.8h, v1.8b, v25.8b           \n"  // G
    "umlsl      v4.8h, v2.8b, v26.8b           \n"  // R
    "add        v4.8h, v4.8h, v29.8h           \n"  // +128 -> unsigned

    "umull      v3.8h, v2.8b, v24.8b           \n"  // R
    "umlsl      v3.8h, v1.8b, v28.8b           \n"  // G
    "umlsl      v3.8h, v0.8b, v27.8b           \n"  // B
    "add        v3.8h, v3.8h, v29.8h           \n"  // +128 -> unsigned

    "uqshrn     v0.8b, v4.8h, #8               \n"  // 16 bit to 8 bit U
    "uqshrn     v1.8b, v3.8h, #8               \n"  // 16 bit to 8 bit V

    MEMACCESS(1)
    "st1        {v0.8b}, [%1], #8              \n"  // store 8 pixels U.
    MEMACCESS(2)
    "st1        {v1.8b}, [%2], #8              \n"  // store 8 pixels V.
    "b.gt       1b                             \n"
  : "+r"(src_argb),  // %0
    "+r"(dst_u),     // %1
    "+r"(dst_v),     // %2
    "+r"(width)        // %3
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4",
    "v24", "v25", "v26", "v27", "v28", "v29"
  );
}
#endif  // HAS_ARGBTOUV444ROW_NEON

#define RGBTOUV_SETUP_REG                                                      \
    "movi       v20.8h, #56, lsl #0  \n"  /* UB/VR coefficient (0.875) / 2 */  \
    "movi       v21.8h, #37, lsl #0  \n"  /* UG coefficient (-0.5781) / 2  */  \
    "movi       v22.8h, #19, lsl #0  \n"  /* UR coefficient (-0.2969) / 2  */  \
    "movi       v23.8h, #9,  lsl #0  \n"  /* VB coefficient (-0.1406) / 2  */  \
    "movi       v24.8h, #47, lsl #0  \n"  /* VG coefficient (-0.7344) / 2  */  \
    "movi       v25.16b, #0x80       \n"  /* 128.5 (0x8080 in 16-bit)      */

// 32x1 pixels -> 8x1.  width is number of argb pixels. e.g. 32.
#ifdef HAS_ARGBTOUV411ROW_NEON
void ARGBToUV411Row_NEON(const uint8* src_argb, uint8* dst_u, uint8* dst_v,
                         int width) {
  asm volatile (
    RGBTOUV_SETUP_REG
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.16b,v1.16b,v2.16b,v3.16b}, [%0], #64 \n"  // load 16 pixels.
    "uaddlp     v0.8h, v0.16b                  \n"  // B 16 bytes -> 8 shorts.
    "uaddlp     v1.8h, v1.16b                  \n"  // G 16 bytes -> 8 shorts.
    "uaddlp     v2.8h, v2.16b                  \n"  // R 16 bytes -> 8 shorts.
    MEMACCESS(0)
    "ld4        {v4.16b,v5.16b,v6.16b,v7.16b}, [%0], #64 \n"  // load next 16.
    "uaddlp     v4.8h, v4.16b                  \n"  // B 16 bytes -> 8 shorts.
    "uaddlp     v5.8h, v5.16b                  \n"  // G 16 bytes -> 8 shorts.
    "uaddlp     v6.8h, v6.16b                  \n"  // R 16 bytes -> 8 shorts.

    "addp       v0.8h, v0.8h, v4.8h            \n"  // B 16 shorts -> 8 shorts.
    "addp       v1.8h, v1.8h, v5.8h            \n"  // G 16 shorts -> 8 shorts.
    "addp       v2.8h, v2.8h, v6.8h            \n"  // R 16 shorts -> 8 shorts.

    "urshr      v0.8h, v0.8h, #1               \n"  // 2x average
    "urshr      v1.8h, v1.8h, #1               \n"
    "urshr      v2.8h, v2.8h, #1               \n"

    "subs       %w3, %w3, #32                  \n"  // 32 processed per loop.
    "mul        v3.8h, v0.8h, v20.8h           \n"  // B
    "mls        v3.8h, v1.8h, v21.8h           \n"  // G
    "mls        v3.8h, v2.8h, v22.8h           \n"  // R
    "add        v3.8h, v3.8h, v25.8h           \n"  // +128 -> unsigned
    "mul        v4.8h, v2.8h, v20.8h           \n"  // R
    "mls        v4.8h, v1.8h, v24.8h           \n"  // G
    "mls        v4.8h, v0.8h, v23.8h           \n"  // B
    "add        v4.8h, v4.8h, v25.8h           \n"  // +128 -> unsigned
    "uqshrn     v0.8b, v3.8h, #8               \n"  // 16 bit to 8 bit U
    "uqshrn     v1.8b, v4.8h, #8               \n"  // 16 bit to 8 bit V
    MEMACCESS(1)
    "st1        {v0.8b}, [%1], #8              \n"  // store 8 pixels U.
    MEMACCESS(2)
    "st1        {v1.8b}, [%2], #8              \n"  // store 8 pixels V.
    "b.gt       1b                             \n"
  : "+r"(src_argb),  // %0
    "+r"(dst_u),     // %1
    "+r"(dst_v),     // %2
    "+r"(width)        // %3
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7",
    "v20", "v21", "v22", "v23", "v24", "v25"
  );
}
#endif  // HAS_ARGBTOUV411ROW_NEON

// 16x2 pixels -> 8x1.  width is number of argb pixels. e.g. 16.
#define RGBTOUV(QB, QG, QR) \
    "mul        v3.8h, " #QB ",v20.8h          \n"  /* B                    */ \
    "mul        v4.8h, " #QR ",v20.8h          \n"  /* R                    */ \
    "mls        v3.8h, " #QG ",v21.8h          \n"  /* G                    */ \
    "mls        v4.8h, " #QG ",v24.8h          \n"  /* G                    */ \
    "mls        v3.8h, " #QR ",v22.8h          \n"  /* R                    */ \
    "mls        v4.8h, " #QB ",v23.8h          \n"  /* B                    */ \
    "add        v3.8h, v3.8h, v25.8h           \n"  /* +128 -> unsigned     */ \
    "add        v4.8h, v4.8h, v25.8h           \n"  /* +128 -> unsigned     */ \
    "uqshrn     v0.8b, v3.8h, #8               \n"  /* 16 bit to 8 bit U    */ \
    "uqshrn     v1.8b, v4.8h, #8               \n"  /* 16 bit to 8 bit V    */

// TODO(fbarchard): Consider vhadd vertical, then vpaddl horizontal, avoid shr.
// TODO(fbarchard): consider ptrdiff_t for all strides.

#ifdef HAS_ARGBTOUVROW_NEON
void ARGBToUVRow_NEON(const uint8* src_argb, int src_stride_argb,
                      uint8* dst_u, uint8* dst_v, int width) {
  const uint8* src_argb_1 = src_argb + src_stride_argb;
  asm volatile (
    RGBTOUV_SETUP_REG
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.16b,v1.16b,v2.16b,v3.16b}, [%0], #64 \n"  // load 16 pixels.
    "uaddlp     v0.8h, v0.16b                  \n"  // B 16 bytes -> 8 shorts.
    "uaddlp     v1.8h, v1.16b                  \n"  // G 16 bytes -> 8 shorts.
    "uaddlp     v2.8h, v2.16b                  \n"  // R 16 bytes -> 8 shorts.

    MEMACCESS(1)
    "ld4        {v4.16b,v5.16b,v6.16b,v7.16b}, [%1], #64 \n"  // load next 16
    "uadalp     v0.8h, v4.16b                  \n"  // B 16 bytes -> 8 shorts.
    "uadalp     v1.8h, v5.16b                  \n"  // G 16 bytes -> 8 shorts.
    "uadalp     v2.8h, v6.16b                  \n"  // R 16 bytes -> 8 shorts.

    "urshr      v0.8h, v0.8h, #1               \n"  // 2x average
    "urshr      v1.8h, v1.8h, #1               \n"
    "urshr      v2.8h, v2.8h, #1               \n"

    "subs       %w4, %w4, #16                  \n"  // 32 processed per loop.
    RGBTOUV(v0.8h, v1.8h, v2.8h)
    MEMACCESS(2)
    "st1        {v0.8b}, [%2], #8              \n"  // store 8 pixels U.
    MEMACCESS(3)
    "st1        {v1.8b}, [%3], #8              \n"  // store 8 pixels V.
    "b.gt       1b                             \n"
  : "+r"(src_argb),  // %0
    "+r"(src_argb_1),  // %1
    "+r"(dst_u),     // %2
    "+r"(dst_v),     // %3
    "+r"(width)        // %4
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7",
    "v20", "v21", "v22", "v23", "v24", "v25"
  );
}
#endif  // HAS_ARGBTOUVROW_NEON

// TODO(fbarchard): Subsample match C code.
#ifdef HAS_ARGBTOUVJROW_NEON
void ARGBToUVJRow_NEON(const uint8* src_argb, int src_stride_argb,
                       uint8* dst_u, uint8* dst_v, int width) {
  const uint8* src_argb_1 = src_argb + src_stride_argb;
  asm volatile (
    "movi       v20.8h, #63, lsl #0            \n"  // UB/VR coeff (0.500) / 2
    "movi       v21.8h, #42, lsl #0            \n"  // UG coeff (-0.33126) / 2
    "movi       v22.8h, #21, lsl #0            \n"  // UR coeff (-0.16874) / 2
    "movi       v23.8h, #10, lsl #0            \n"  // VB coeff (-0.08131) / 2
    "movi       v24.8h, #53, lsl #0            \n"  // VG coeff (-0.41869) / 2
    "movi       v25.16b, #0x80                 \n"  // 128.5 (0x8080 in 16-bit)
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.16b,v1.16b,v2.16b,v3.16b}, [%0], #64 \n"  // load 16 pixels.
    "uaddlp     v0.8h, v0.16b                  \n"  // B 16 bytes -> 8 shorts.
    "uaddlp     v1.8h, v1.16b                  \n"  // G 16 bytes -> 8 shorts.
    "uaddlp     v2.8h, v2.16b                  \n"  // R 16 bytes -> 8 shorts.
    MEMACCESS(1)
    "ld4        {v4.16b,v5.16b,v6.16b,v7.16b}, [%1], #64  \n"  // load next 16
    "uadalp     v0.8h, v4.16b                  \n"  // B 16 bytes -> 8 shorts.
    "uadalp     v1.8h, v5.16b                  \n"  // G 16 bytes -> 8 shorts.
    "uadalp     v2.8h, v6.16b                  \n"  // R 16 bytes -> 8 shorts.

    "urshr      v0.8h, v0.8h, #1               \n"  // 2x average
    "urshr      v1.8h, v1.8h, #1               \n"
    "urshr      v2.8h, v2.8h, #1               \n"

    "subs       %w4, %w4, #16                  \n"  // 32 processed per loop.
    RGBTOUV(v0.8h, v1.8h, v2.8h)
    MEMACCESS(2)
    "st1        {v0.8b}, [%2], #8              \n"  // store 8 pixels U.
    MEMACCESS(3)
    "st1        {v1.8b}, [%3], #8              \n"  // store 8 pixels V.
    "b.gt       1b                             \n"
  : "+r"(src_argb),  // %0
    "+r"(src_argb_1),  // %1
    "+r"(dst_u),     // %2
    "+r"(dst_v),     // %3
    "+r"(width)        // %4
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7",
    "v20", "v21", "v22", "v23", "v24", "v25"
  );
}
#endif  // HAS_ARGBTOUVJROW_NEON

#ifdef HAS_BGRATOUVROW_NEON
void BGRAToUVRow_NEON(const uint8* src_bgra, int src_stride_bgra,
                      uint8* dst_u, uint8* dst_v, int width) {
  const uint8* src_bgra_1 = src_bgra + src_stride_bgra;
  asm volatile (
    RGBTOUV_SETUP_REG
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.16b,v1.16b,v2.16b,v3.16b}, [%0], #64 \n"  // load 16 pixels.
    "uaddlp     v0.8h, v3.16b                  \n"  // B 16 bytes -> 8 shorts.
    "uaddlp     v3.8h, v2.16b                  \n"  // G 16 bytes -> 8 shorts.
    "uaddlp     v2.8h, v1.16b                  \n"  // R 16 bytes -> 8 shorts.
    MEMACCESS(1)
    "ld4        {v4.16b,v5.16b,v6.16b,v7.16b}, [%1], #64 \n"  // load 16 more
    "uadalp     v0.8h, v7.16b                  \n"  // B 16 bytes -> 8 shorts.
    "uadalp     v3.8h, v6.16b                  \n"  // G 16 bytes -> 8 shorts.
    "uadalp     v2.8h, v5.16b                  \n"  // R 16 bytes -> 8 shorts.

    "urshr      v0.8h, v0.8h, #1               \n"  // 2x average
    "urshr      v1.8h, v3.8h, #1               \n"
    "urshr      v2.8h, v2.8h, #1               \n"

    "subs       %w4, %w4, #16                  \n"  // 32 processed per loop.
    RGBTOUV(v0.8h, v1.8h, v2.8h)
    MEMACCESS(2)
    "st1        {v0.8b}, [%2], #8              \n"  // store 8 pixels U.
    MEMACCESS(3)
    "st1        {v1.8b}, [%3], #8              \n"  // store 8 pixels V.
    "b.gt       1b                             \n"
  : "+r"(src_bgra),  // %0
    "+r"(src_bgra_1),  // %1
    "+r"(dst_u),     // %2
    "+r"(dst_v),     // %3
    "+r"(width)        // %4
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7",
    "v20", "v21", "v22", "v23", "v24", "v25"
  );
}
#endif  // HAS_BGRATOUVROW_NEON

#ifdef HAS_ABGRTOUVROW_NEON
void ABGRToUVRow_NEON(const uint8* src_abgr, int src_stride_abgr,
                      uint8* dst_u, uint8* dst_v, int width) {
  const uint8* src_abgr_1 = src_abgr + src_stride_abgr;
  asm volatile (
    RGBTOUV_SETUP_REG
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.16b,v1.16b,v2.16b,v3.16b}, [%0], #64 \n"  // load 16 pixels.
    "uaddlp     v3.8h, v2.16b                  \n"  // B 16 bytes -> 8 shorts.
    "uaddlp     v2.8h, v1.16b                  \n"  // G 16 bytes -> 8 shorts.
    "uaddlp     v1.8h, v0.16b                  \n"  // R 16 bytes -> 8 shorts.
    MEMACCESS(1)
    "ld4        {v4.16b,v5.16b,v6.16b,v7.16b}, [%1], #64 \n"  // load 16 more.
    "uadalp     v3.8h, v6.16b                  \n"  // B 16 bytes -> 8 shorts.
    "uadalp     v2.8h, v5.16b                  \n"  // G 16 bytes -> 8 shorts.
    "uadalp     v1.8h, v4.16b                  \n"  // R 16 bytes -> 8 shorts.

    "urshr      v0.8h, v3.8h, #1               \n"  // 2x average
    "urshr      v2.8h, v2.8h, #1               \n"
    "urshr      v1.8h, v1.8h, #1               \n"

    "subs       %w4, %w4, #16                  \n"  // 32 processed per loop.
    RGBTOUV(v0.8h, v2.8h, v1.8h)
    MEMACCESS(2)
    "st1        {v0.8b}, [%2], #8              \n"  // store 8 pixels U.
    MEMACCESS(3)
    "st1        {v1.8b}, [%3], #8              \n"  // store 8 pixels V.
    "b.gt       1b                             \n"
  : "+r"(src_abgr),  // %0
    "+r"(src_abgr_1),  // %1
    "+r"(dst_u),     // %2
    "+r"(dst_v),     // %3
    "+r"(width)        // %4
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7",
    "v20", "v21", "v22", "v23", "v24", "v25"
  );
}
#endif  // HAS_ABGRTOUVROW_NEON

#ifdef HAS_RGBATOUVROW_NEON
void RGBAToUVRow_NEON(const uint8* src_rgba, int src_stride_rgba,
                      uint8* dst_u, uint8* dst_v, int width) {
  const uint8* src_rgba_1 = src_rgba + src_stride_rgba;
  asm volatile (
    RGBTOUV_SETUP_REG
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.16b,v1.16b,v2.16b,v3.16b}, [%0], #64 \n"  // load 16 pixels.
    "uaddlp     v0.8h, v1.16b                  \n"  // B 16 bytes -> 8 shorts.
    "uaddlp     v1.8h, v2.16b                  \n"  // G 16 bytes -> 8 shorts.
    "uaddlp     v2.8h, v3.16b                  \n"  // R 16 bytes -> 8 shorts.
    MEMACCESS(1)
    "ld4        {v4.16b,v5.16b,v6.16b,v7.16b}, [%1], #64 \n"  // load 16 more.
    "uadalp     v0.8h, v5.16b                  \n"  // B 16 bytes -> 8 shorts.
    "uadalp     v1.8h, v6.16b                  \n"  // G 16 bytes -> 8 shorts.
    "uadalp     v2.8h, v7.16b                  \n"  // R 16 bytes -> 8 shorts.

    "urshr      v0.8h, v0.8h, #1               \n"  // 2x average
    "urshr      v1.8h, v1.8h, #1               \n"
    "urshr      v2.8h, v2.8h, #1               \n"

    "subs       %w4, %w4, #16                  \n"  // 32 processed per loop.
    RGBTOUV(v0.8h, v1.8h, v2.8h)
    MEMACCESS(2)
    "st1        {v0.8b}, [%2], #8              \n"  // store 8 pixels U.
    MEMACCESS(3)
    "st1        {v1.8b}, [%3], #8              \n"  // store 8 pixels V.
    "b.gt       1b                             \n"
  : "+r"(src_rgba),  // %0
    "+r"(src_rgba_1),  // %1
    "+r"(dst_u),     // %2
    "+r"(dst_v),     // %3
    "+r"(width)        // %4
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7",
    "v20", "v21", "v22", "v23", "v24", "v25"
  );
}
#endif  // HAS_RGBATOUVROW_NEON

#ifdef HAS_RGB24TOUVROW_NEON
void RGB24ToUVRow_NEON(const uint8* src_rgb24, int src_stride_rgb24,
                       uint8* dst_u, uint8* dst_v, int width) {
  const uint8* src_rgb24_1 = src_rgb24 + src_stride_rgb24;
  asm volatile (
    RGBTOUV_SETUP_REG
  "1:                                          \n"
    MEMACCESS(0)
    "ld3        {v0.16b,v1.16b,v2.16b}, [%0], #48 \n"  // load 16 pixels.
    "uaddlp     v0.8h, v0.16b                  \n"  // B 16 bytes -> 8 shorts.
    "uaddlp     v1.8h, v1.16b                  \n"  // G 16 bytes -> 8 shorts.
    "uaddlp     v2.8h, v2.16b                  \n"  // R 16 bytes -> 8 shorts.
    MEMACCESS(1)
    "ld3        {v4.16b,v5.16b,v6.16b}, [%1], #48 \n"  // load 16 more.
    "uadalp     v0.8h, v4.16b                  \n"  // B 16 bytes -> 8 shorts.
    "uadalp     v1.8h, v5.16b                  \n"  // G 16 bytes -> 8 shorts.
    "uadalp     v2.8h, v6.16b                  \n"  // R 16 bytes -> 8 shorts.

    "urshr      v0.8h, v0.8h, #1               \n"  // 2x average
    "urshr      v1.8h, v1.8h, #1               \n"
    "urshr      v2.8h, v2.8h, #1               \n"

    "subs       %w4, %w4, #16                  \n"  // 32 processed per loop.
    RGBTOUV(v0.8h, v1.8h, v2.8h)
    MEMACCESS(2)
    "st1        {v0.8b}, [%2], #8              \n"  // store 8 pixels U.
    MEMACCESS(3)
    "st1        {v1.8b}, [%3], #8              \n"  // store 8 pixels V.
    "b.gt       1b                             \n"
  : "+r"(src_rgb24),  // %0
    "+r"(src_rgb24_1),  // %1
    "+r"(dst_u),     // %2
    "+r"(dst_v),     // %3
    "+r"(width)        // %4
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7",
    "v20", "v21", "v22", "v23", "v24", "v25"
  );
}
#endif  // HAS_RGB24TOUVROW_NEON

#ifdef HAS_RAWTOUVROW_NEON
void RAWToUVRow_NEON(const uint8* src_raw, int src_stride_raw,
                     uint8* dst_u, uint8* dst_v, int width) {
  const uint8* src_raw_1 = src_raw + src_stride_raw;
  asm volatile (
    RGBTOUV_SETUP_REG
  "1:                                          \n"
    MEMACCESS(0)
    "ld3        {v0.16b,v1.16b,v2.16b}, [%0], #48 \n"  // load 8 RAW pixels.
    "uaddlp     v2.8h, v2.16b                  \n"  // B 16 bytes -> 8 shorts.
    "uaddlp     v1.8h, v1.16b                  \n"  // G 16 bytes -> 8 shorts.
    "uaddlp     v0.8h, v0.16b                  \n"  // R 16 bytes -> 8 shorts.
    MEMACCESS(1)
    "ld3        {v4.16b,v5.16b,v6.16b}, [%1], #48 \n"  // load 8 more RAW pixels
    "uadalp     v2.8h, v6.16b                  \n"  // B 16 bytes -> 8 shorts.
    "uadalp     v1.8h, v5.16b                  \n"  // G 16 bytes -> 8 shorts.
    "uadalp     v0.8h, v4.16b                  \n"  // R 16 bytes -> 8 shorts.

    "urshr      v2.8h, v2.8h, #1               \n"  // 2x average
    "urshr      v1.8h, v1.8h, #1               \n"
    "urshr      v0.8h, v0.8h, #1               \n"

    "subs       %w4, %w4, #16                  \n"  // 32 processed per loop.
    RGBTOUV(v2.8h, v1.8h, v0.8h)
    MEMACCESS(2)
    "st1        {v0.8b}, [%2], #8              \n"  // store 8 pixels U.
    MEMACCESS(3)
    "st1        {v1.8b}, [%3], #8              \n"  // store 8 pixels V.
    "b.gt       1b                             \n"
  : "+r"(src_raw),  // %0
    "+r"(src_raw_1),  // %1
    "+r"(dst_u),     // %2
    "+r"(dst_v),     // %3
    "+r"(width)        // %4
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7",
    "v20", "v21", "v22", "v23", "v24", "v25"
  );
}
#endif  // HAS_RAWTOUVROW_NEON

// 16x2 pixels -> 8x1.  width is number of argb pixels. e.g. 16.
#ifdef HAS_RGB565TOUVROW_NEON
void RGB565ToUVRow_NEON(const uint8* src_rgb565, int src_stride_rgb565,
                        uint8* dst_u, uint8* dst_v, int width) {
  const uint8* src_rgb565_1 = src_rgb565 + src_stride_rgb565;
  asm volatile (
    "movi       v22.8h, #56, lsl #0            \n"  // UB / VR coeff (0.875) / 2
    "movi       v23.8h, #37, lsl #0            \n"  // UG coeff (-0.5781) / 2
    "movi       v24.8h, #19, lsl #0            \n"  // UR coeff (-0.2969) / 2
    "movi       v25.8h, #9 , lsl #0            \n"  // VB coeff (-0.1406) / 2
    "movi       v26.8h, #47, lsl #0            \n"  // VG coeff (-0.7344) / 2
    "movi       v27.16b, #0x80                 \n"  // 128.5 (0x8080 in 16-bit)
  "1:                                          \n"
    MEMACCESS(0)
    "ld1        {v0.16b}, [%0], #16            \n"  // load 8 RGB565 pixels.
    RGB565TOARGB
    "uaddlp     v16.4h, v0.8b                  \n"  // B 8 bytes -> 4 shorts.
    "uaddlp     v18.4h, v1.8b                  \n"  // G 8 bytes -> 4 shorts.
    "uaddlp     v20.4h, v2.8b                  \n"  // R 8 bytes -> 4 shorts.
    MEMACCESS(0)
    "ld1        {v0.16b}, [%0], #16            \n"  // next 8 RGB565 pixels.
    RGB565TOARGB
    "uaddlp     v17.4h, v0.8b                  \n"  // B 8 bytes -> 4 shorts.
    "uaddlp     v19.4h, v1.8b                  \n"  // G 8 bytes -> 4 shorts.
    "uaddlp     v21.4h, v2.8b                  \n"  // R 8 bytes -> 4 shorts.

    MEMACCESS(1)
    "ld1        {v0.16b}, [%1], #16            \n"  // load 8 RGB565 pixels.
    RGB565TOARGB
    "uadalp     v16.4h, v0.8b                  \n"  // B 8 bytes -> 4 shorts.
    "uadalp     v18.4h, v1.8b                  \n"  // G 8 bytes -> 4 shorts.
    "uadalp     v20.4h, v2.8b                  \n"  // R 8 bytes -> 4 shorts.
    MEMACCESS(1)
    "ld1        {v0.16b}, [%1], #16            \n"  // next 8 RGB565 pixels.
    RGB565TOARGB
    "uadalp     v17.4h, v0.8b                  \n"  // B 8 bytes -> 4 shorts.
    "uadalp     v19.4h, v1.8b                  \n"  // G 8 bytes -> 4 shorts.
    "uadalp     v21.4h, v2.8b                  \n"  // R 8 bytes -> 4 shorts.

    "ins        v16.D[1], v17.D[0]             \n"
    "ins        v18.D[1], v19.D[0]             \n"
    "ins        v20.D[1], v21.D[0]             \n"

    "urshr      v4.8h, v16.8h, #1              \n"  // 2x average
    "urshr      v5.8h, v18.8h, #1              \n"
    "urshr      v6.8h, v20.8h, #1              \n"

    "subs       %w4, %w4, #16                  \n"  // 16 processed per loop.
    "mul        v16.8h, v4.8h, v22.8h          \n"  // B
    "mls        v16.8h, v5.8h, v23.8h          \n"  // G
    "mls        v16.8h, v6.8h, v24.8h          \n"  // R
    "add        v16.8h, v16.8h, v27.8h         \n"  // +128 -> unsigned
    "mul        v17.8h, v6.8h, v22.8h          \n"  // R
    "mls        v17.8h, v5.8h, v26.8h          \n"  // G
    "mls        v17.8h, v4.8h, v25.8h          \n"  // B
    "add        v17.8h, v17.8h, v27.8h         \n"  // +128 -> unsigned
    "uqshrn     v0.8b, v16.8h, #8              \n"  // 16 bit to 8 bit U
    "uqshrn     v1.8b, v17.8h, #8              \n"  // 16 bit to 8 bit V
    MEMACCESS(2)
    "st1        {v0.8b}, [%2], #8              \n"  // store 8 pixels U.
    MEMACCESS(3)
    "st1        {v1.8b}, [%3], #8              \n"  // store 8 pixels V.
    "b.gt       1b                             \n"
  : "+r"(src_rgb565),  // %0
    "+r"(src_rgb565_1),  // %1
    "+r"(dst_u),     // %2
    "+r"(dst_v),     // %3
    "+r"(width)        // %4
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7",
    "v16", "v17", "v18", "v19", "v20", "v21", "v22", "v23", "v24",
    "v25", "v26", "v27"
  );
}
#endif  // HAS_RGB565TOUVROW_NEON

// 16x2 pixels -> 8x1.  width is number of argb pixels. e.g. 16.
#ifdef HAS_ARGB1555TOUVROW_NEON
void ARGB1555ToUVRow_NEON(const uint8* src_argb1555, int src_stride_argb1555,
                        uint8* dst_u, uint8* dst_v, int width) {
  const uint8* src_argb1555_1 = src_argb1555 + src_stride_argb1555;
  asm volatile (
    RGBTOUV_SETUP_REG
  "1:                                          \n"
    MEMACCESS(0)
    "ld1        {v0.16b}, [%0], #16            \n"  // load 8 ARGB1555 pixels.
    RGB555TOARGB
    "uaddlp     v16.4h, v0.8b                  \n"  // B 8 bytes -> 4 shorts.
    "uaddlp     v17.4h, v1.8b                  \n"  // G 8 bytes -> 4 shorts.
    "uaddlp     v18.4h, v2.8b                  \n"  // R 8 bytes -> 4 shorts.
    MEMACCESS(0)
    "ld1        {v0.16b}, [%0], #16            \n"  // next 8 ARGB1555 pixels.
    RGB555TOARGB
    "uaddlp     v26.4h, v0.8b                  \n"  // B 8 bytes -> 4 shorts.
    "uaddlp     v27.4h, v1.8b                  \n"  // G 8 bytes -> 4 shorts.
    "uaddlp     v28.4h, v2.8b                  \n"  // R 8 bytes -> 4 shorts.

    MEMACCESS(1)
    "ld1        {v0.16b}, [%1], #16            \n"  // load 8 ARGB1555 pixels.
    RGB555TOARGB
    "uadalp     v16.4h, v0.8b                  \n"  // B 8 bytes -> 4 shorts.
    "uadalp     v17.4h, v1.8b                  \n"  // G 8 bytes -> 4 shorts.
    "uadalp     v18.4h, v2.8b                  \n"  // R 8 bytes -> 4 shorts.
    MEMACCESS(1)
    "ld1        {v0.16b}, [%1], #16            \n"  // next 8 ARGB1555 pixels.
    RGB555TOARGB
    "uadalp     v26.4h, v0.8b                  \n"  // B 8 bytes -> 4 shorts.
    "uadalp     v27.4h, v1.8b                  \n"  // G 8 bytes -> 4 shorts.
    "uadalp     v28.4h, v2.8b                  \n"  // R 8 bytes -> 4 shorts.

    "ins        v16.D[1], v26.D[0]             \n"
    "ins        v17.D[1], v27.D[0]             \n"
    "ins        v18.D[1], v28.D[0]             \n"

    "urshr      v4.8h, v16.8h, #1              \n"  // 2x average
    "urshr      v5.8h, v17.8h, #1              \n"
    "urshr      v6.8h, v18.8h, #1              \n"

    "subs       %w4, %w4, #16                  \n"  // 16 processed per loop.
    "mul        v2.8h, v4.8h, v20.8h           \n"  // B
    "mls        v2.8h, v5.8h, v21.8h           \n"  // G
    "mls        v2.8h, v6.8h, v22.8h           \n"  // R
    "add        v2.8h, v2.8h, v25.8h           \n"  // +128 -> unsigned
    "mul        v3.8h, v6.8h, v20.8h           \n"  // R
    "mls        v3.8h, v5.8h, v24.8h           \n"  // G
    "mls        v3.8h, v4.8h, v23.8h           \n"  // B
    "add        v3.8h, v3.8h, v25.8h           \n"  // +128 -> unsigned
    "uqshrn     v0.8b, v2.8h, #8               \n"  // 16 bit to 8 bit U
    "uqshrn     v1.8b, v3.8h, #8               \n"  // 16 bit to 8 bit V
    MEMACCESS(2)
    "st1        {v0.8b}, [%2], #8              \n"  // store 8 pixels U.
    MEMACCESS(3)
    "st1        {v1.8b}, [%3], #8              \n"  // store 8 pixels V.
    "b.gt       1b                             \n"
  : "+r"(src_argb1555),  // %0
    "+r"(src_argb1555_1),  // %1
    "+r"(dst_u),     // %2
    "+r"(dst_v),     // %3
    "+r"(width)        // %4
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6",
    "v16", "v17", "v18", "v19", "v20", "v21", "v22", "v23", "v24", "v25",
    "v26", "v27", "v28"
  );
}
#endif  // HAS_ARGB1555TOUVROW_NEON

// 16x2 pixels -> 8x1.  width is number of argb pixels. e.g. 16.
#ifdef HAS_ARGB4444TOUVROW_NEON
void ARGB4444ToUVRow_NEON(const uint8* src_argb4444, int src_stride_argb4444,
                          uint8* dst_u, uint8* dst_v, int width) {
  const uint8* src_argb4444_1 = src_argb4444 + src_stride_argb4444;
  asm volatile (
    RGBTOUV_SETUP_REG
  "1:                                          \n"
    MEMACCESS(0)
    "ld1        {v0.16b}, [%0], #16            \n"  // load 8 ARGB4444 pixels.
    ARGB4444TOARGB
    "uaddlp     v16.4h, v0.8b                  \n"  // B 8 bytes -> 4 shorts.
    "uaddlp     v17.4h, v1.8b                  \n"  // G 8 bytes -> 4 shorts.
    "uaddlp     v18.4h, v2.8b                  \n"  // R 8 bytes -> 4 shorts.
    MEMACCESS(0)
    "ld1        {v0.16b}, [%0], #16            \n"  // next 8 ARGB4444 pixels.
    ARGB4444TOARGB
    "uaddlp     v26.4h, v0.8b                  \n"  // B 8 bytes -> 4 shorts.
    "uaddlp     v27.4h, v1.8b                  \n"  // G 8 bytes -> 4 shorts.
    "uaddlp     v28.4h, v2.8b                  \n"  // R 8 bytes -> 4 shorts.

    MEMACCESS(1)
    "ld1        {v0.16b}, [%1], #16            \n"  // load 8 ARGB4444 pixels.
    ARGB4444TOARGB
    "uadalp     v16.4h, v0.8b                  \n"  // B 8 bytes -> 4 shorts.
    "uadalp     v17.4h, v1.8b                  \n"  // G 8 bytes -> 4 shorts.
    "uadalp     v18.4h, v2.8b                  \n"  // R 8 bytes -> 4 shorts.
    MEMACCESS(1)
    "ld1        {v0.16b}, [%1], #16            \n"  // next 8 ARGB4444 pixels.
    ARGB4444TOARGB
    "uadalp     v26.4h, v0.8b                  \n"  // B 8 bytes -> 4 shorts.
    "uadalp     v27.4h, v1.8b                  \n"  // G 8 bytes -> 4 shorts.
    "uadalp     v28.4h, v2.8b                  \n"  // R 8 bytes -> 4 shorts.

    "ins        v16.D[1], v26.D[0]             \n"
    "ins        v17.D[1], v27.D[0]             \n"
    "ins        v18.D[1], v28.D[0]             \n"

    "urshr      v4.8h, v16.8h, #1              \n"  // 2x average
    "urshr      v5.8h, v17.8h, #1              \n"
    "urshr      v6.8h, v18.8h, #1              \n"

    "subs       %w4, %w4, #16                  \n"  // 16 processed per loop.
    "mul        v2.8h, v4.8h, v20.8h           \n"  // B
    "mls        v2.8h, v5.8h, v21.8h           \n"  // G
    "mls        v2.8h, v6.8h, v22.8h           \n"  // R
    "add        v2.8h, v2.8h, v25.8h           \n"  // +128 -> unsigned
    "mul        v3.8h, v6.8h, v20.8h           \n"  // R
    "mls        v3.8h, v5.8h, v24.8h           \n"  // G
    "mls        v3.8h, v4.8h, v23.8h           \n"  // B
    "add        v3.8h, v3.8h, v25.8h           \n"  // +128 -> unsigned
    "uqshrn     v0.8b, v2.8h, #8               \n"  // 16 bit to 8 bit U
    "uqshrn     v1.8b, v3.8h, #8               \n"  // 16 bit to 8 bit V
    MEMACCESS(2)
    "st1        {v0.8b}, [%2], #8              \n"  // store 8 pixels U.
    MEMACCESS(3)
    "st1        {v1.8b}, [%3], #8              \n"  // store 8 pixels V.
    "b.gt       1b                             \n"
  : "+r"(src_argb4444),  // %0
    "+r"(src_argb4444_1),  // %1
    "+r"(dst_u),     // %2
    "+r"(dst_v),     // %3
    "+r"(width)        // %4
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6",
    "v16", "v17", "v18", "v19", "v20", "v21", "v22", "v23", "v24", "v25",
    "v26", "v27", "v28"

  );
}
#endif  // HAS_ARGB4444TOUVROW_NEON

#ifdef HAS_RGB565TOYROW_NEON
void RGB565ToYRow_NEON(const uint8* src_rgb565, uint8* dst_y, int width) {
  asm volatile (
    "movi       v24.8b, #13                    \n"  // B * 0.1016 coefficient
    "movi       v25.8b, #65                    \n"  // G * 0.5078 coefficient
    "movi       v26.8b, #33                    \n"  // R * 0.2578 coefficient
    "movi       v27.8b, #16                    \n"  // Add 16 constant
  "1:                                          \n"
    MEMACCESS(0)
    "ld1        {v0.16b}, [%0], #16            \n"  // load 8 RGB565 pixels.
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    RGB565TOARGB
    "umull      v3.8h, v0.8b, v24.8b           \n"  // B
    "umlal      v3.8h, v1.8b, v25.8b           \n"  // G
    "umlal      v3.8h, v2.8b, v26.8b           \n"  // R
    "sqrshrun   v0.8b, v3.8h, #7               \n"  // 16 bit to 8 bit Y
    "uqadd      v0.8b, v0.8b, v27.8b           \n"
    MEMACCESS(1)
    "st1        {v0.8b}, [%1], #8              \n"  // store 8 pixels Y.
    "b.gt       1b                             \n"
  : "+r"(src_rgb565),  // %0
    "+r"(dst_y),       // %1
    "+r"(width)          // %2
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v6",
    "v24", "v25", "v26", "v27"
  );
}
#endif  // HAS_RGB565TOYROW_NEON

#ifdef HAS_ARGB1555TOYROW_NEON
void ARGB1555ToYRow_NEON(const uint8* src_argb1555, uint8* dst_y, int width) {
  asm volatile (
    "movi       v4.8b, #13                     \n"  // B * 0.1016 coefficient
    "movi       v5.8b, #65                     \n"  // G * 0.5078 coefficient
    "movi       v6.8b, #33                     \n"  // R * 0.2578 coefficient
    "movi       v7.8b, #16                     \n"  // Add 16 constant
  "1:                                          \n"
    MEMACCESS(0)
    "ld1        {v0.16b}, [%0], #16            \n"  // load 8 ARGB1555 pixels.
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    ARGB1555TOARGB
    "umull      v3.8h, v0.8b, v4.8b            \n"  // B
    "umlal      v3.8h, v1.8b, v5.8b            \n"  // G
    "umlal      v3.8h, v2.8b, v6.8b            \n"  // R
    "sqrshrun   v0.8b, v3.8h, #7               \n"  // 16 bit to 8 bit Y
    "uqadd      v0.8b, v0.8b, v7.8b            \n"
    MEMACCESS(1)
    "st1        {v0.8b}, [%1], #8              \n"  // store 8 pixels Y.
    "b.gt       1b                             \n"
  : "+r"(src_argb1555),  // %0
    "+r"(dst_y),         // %1
    "+r"(width)            // %2
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7"
  );
}
#endif  // HAS_ARGB1555TOYROW_NEON

#ifdef HAS_ARGB4444TOYROW_NEON
void ARGB4444ToYRow_NEON(const uint8* src_argb4444, uint8* dst_y, int width) {
  asm volatile (
    "movi       v24.8b, #13                    \n"  // B * 0.1016 coefficient
    "movi       v25.8b, #65                    \n"  // G * 0.5078 coefficient
    "movi       v26.8b, #33                    \n"  // R * 0.2578 coefficient
    "movi       v27.8b, #16                    \n"  // Add 16 constant
  "1:                                          \n"
    MEMACCESS(0)
    "ld1        {v0.16b}, [%0], #16            \n"  // load 8 ARGB4444 pixels.
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    ARGB4444TOARGB
    "umull      v3.8h, v0.8b, v24.8b           \n"  // B
    "umlal      v3.8h, v1.8b, v25.8b           \n"  // G
    "umlal      v3.8h, v2.8b, v26.8b           \n"  // R
    "sqrshrun   v0.8b, v3.8h, #7               \n"  // 16 bit to 8 bit Y
    "uqadd      v0.8b, v0.8b, v27.8b           \n"
    MEMACCESS(1)
    "st1        {v0.8b}, [%1], #8              \n"  // store 8 pixels Y.
    "b.gt       1b                             \n"
  : "+r"(src_argb4444),  // %0
    "+r"(dst_y),         // %1
    "+r"(width)            // %2
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v24", "v25", "v26", "v27"
  );
}
#endif  // HAS_ARGB4444TOYROW_NEON

#ifdef HAS_BGRATOYROW_NEON
void BGRAToYRow_NEON(const uint8* src_bgra, uint8* dst_y, int width) {
  asm volatile (
    "movi       v4.8b, #33                     \n"  // R * 0.2578 coefficient
    "movi       v5.8b, #65                     \n"  // G * 0.5078 coefficient
    "movi       v6.8b, #13                     \n"  // B * 0.1016 coefficient
    "movi       v7.8b, #16                     \n"  // Add 16 constant
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%0], #32 \n"  // load 8 pixels.
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    "umull      v16.8h, v1.8b, v4.8b           \n"  // R
    "umlal      v16.8h, v2.8b, v5.8b           \n"  // G
    "umlal      v16.8h, v3.8b, v6.8b           \n"  // B
    "sqrshrun   v0.8b, v16.8h, #7              \n"  // 16 bit to 8 bit Y
    "uqadd      v0.8b, v0.8b, v7.8b            \n"
    MEMACCESS(1)
    "st1        {v0.8b}, [%1], #8              \n"  // store 8 pixels Y.
    "b.gt       1b                             \n"
  : "+r"(src_bgra),  // %0
    "+r"(dst_y),     // %1
    "+r"(width)        // %2
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v16"
  );
}
#endif  // HAS_BGRATOYROW_NEON

#ifdef HAS_ABGRTOYROW_NEON
void ABGRToYRow_NEON(const uint8* src_abgr, uint8* dst_y, int width) {
  asm volatile (
    "movi       v4.8b, #33                     \n"  // R * 0.2578 coefficient
    "movi       v5.8b, #65                     \n"  // G * 0.5078 coefficient
    "movi       v6.8b, #13                     \n"  // B * 0.1016 coefficient
    "movi       v7.8b, #16                     \n"  // Add 16 constant
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%0], #32 \n"  // load 8 pixels.
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    "umull      v16.8h, v0.8b, v4.8b           \n"  // R
    "umlal      v16.8h, v1.8b, v5.8b           \n"  // G
    "umlal      v16.8h, v2.8b, v6.8b           \n"  // B
    "sqrshrun   v0.8b, v16.8h, #7              \n"  // 16 bit to 8 bit Y
    "uqadd      v0.8b, v0.8b, v7.8b            \n"
    MEMACCESS(1)
    "st1        {v0.8b}, [%1], #8              \n"  // store 8 pixels Y.
    "b.gt       1b                             \n"
  : "+r"(src_abgr),  // %0
    "+r"(dst_y),     // %1
    "+r"(width)        // %2
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v16"
  );
}
#endif  // HAS_ABGRTOYROW_NEON

#ifdef HAS_RGBATOYROW_NEON
void RGBAToYRow_NEON(const uint8* src_rgba, uint8* dst_y, int width) {
  asm volatile (
    "movi       v4.8b, #13                     \n"  // B * 0.1016 coefficient
    "movi       v5.8b, #65                     \n"  // G * 0.5078 coefficient
    "movi       v6.8b, #33                     \n"  // R * 0.2578 coefficient
    "movi       v7.8b, #16                     \n"  // Add 16 constant
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%0], #32 \n"  // load 8 pixels.
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    "umull      v16.8h, v1.8b, v4.8b           \n"  // B
    "umlal      v16.8h, v2.8b, v5.8b           \n"  // G
    "umlal      v16.8h, v3.8b, v6.8b           \n"  // R
    "sqrshrun   v0.8b, v16.8h, #7              \n"  // 16 bit to 8 bit Y
    "uqadd      v0.8b, v0.8b, v7.8b            \n"
    MEMACCESS(1)
    "st1        {v0.8b}, [%1], #8              \n"  // store 8 pixels Y.
    "b.gt       1b                             \n"
  : "+r"(src_rgba),  // %0
    "+r"(dst_y),     // %1
    "+r"(width)        // %2
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v16"
  );
}
#endif  // HAS_RGBATOYROW_NEON

#ifdef HAS_RGB24TOYROW_NEON
void RGB24ToYRow_NEON(const uint8* src_rgb24, uint8* dst_y, int width) {
  asm volatile (
    "movi       v4.8b, #13                     \n"  // B * 0.1016 coefficient
    "movi       v5.8b, #65                     \n"  // G * 0.5078 coefficient
    "movi       v6.8b, #33                     \n"  // R * 0.2578 coefficient
    "movi       v7.8b, #16                     \n"  // Add 16 constant
  "1:                                          \n"
    MEMACCESS(0)
    "ld3        {v0.8b,v1.8b,v2.8b}, [%0], #24 \n"  // load 8 pixels.
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    "umull      v16.8h, v0.8b, v4.8b           \n"  // B
    "umlal      v16.8h, v1.8b, v5.8b           \n"  // G
    "umlal      v16.8h, v2.8b, v6.8b           \n"  // R
    "sqrshrun   v0.8b, v16.8h, #7              \n"  // 16 bit to 8 bit Y
    "uqadd      v0.8b, v0.8b, v7.8b            \n"
    MEMACCESS(1)
    "st1        {v0.8b}, [%1], #8              \n"  // store 8 pixels Y.
    "b.gt       1b                             \n"
  : "+r"(src_rgb24),  // %0
    "+r"(dst_y),      // %1
    "+r"(width)         // %2
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v16"
  );
}
#endif  // HAS_RGB24TOYROW_NEON

#ifdef HAS_RAWTOYROW_NEON
void RAWToYRow_NEON(const uint8* src_raw, uint8* dst_y, int width) {
  asm volatile (
    "movi       v4.8b, #33                     \n"  // R * 0.2578 coefficient
    "movi       v5.8b, #65                     \n"  // G * 0.5078 coefficient
    "movi       v6.8b, #13                     \n"  // B * 0.1016 coefficient
    "movi       v7.8b, #16                     \n"  // Add 16 constant
  "1:                                          \n"
    MEMACCESS(0)
    "ld3        {v0.8b,v1.8b,v2.8b}, [%0], #24 \n"  // load 8 pixels.
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    "umull      v16.8h, v0.8b, v4.8b           \n"  // B
    "umlal      v16.8h, v1.8b, v5.8b           \n"  // G
    "umlal      v16.8h, v2.8b, v6.8b           \n"  // R
    "sqrshrun   v0.8b, v16.8h, #7              \n"  // 16 bit to 8 bit Y
    "uqadd      v0.8b, v0.8b, v7.8b            \n"
    MEMACCESS(1)
    "st1        {v0.8b}, [%1], #8              \n"  // store 8 pixels Y.
    "b.gt       1b                             \n"
  : "+r"(src_raw),  // %0
    "+r"(dst_y),    // %1
    "+r"(width)       // %2
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v16"
  );
}
#endif  // HAS_RAWTOYROW_NEON

// Bilinear filter 16x2 -> 16x1
#ifdef HAS_INTERPOLATEROW_NEON
void InterpolateRow_NEON(uint8* dst_ptr,
                         const uint8* src_ptr, ptrdiff_t src_stride,
                         int dst_width, int source_y_fraction) {
  int y1_fraction = source_y_fraction;
  int y0_fraction = 256 - y1_fraction;
  const uint8* src_ptr1 = src_ptr + src_stride;
  asm volatile (
    "cmp        %w4, #0                        \n"
    "b.eq       100f                           \n"
    "cmp        %w4, #128                      \n"
    "b.eq       50f                            \n"

    "dup        v5.16b, %w4                    \n"
    "dup        v4.16b, %w5                    \n"
    // General purpose row blend.
  "1:                                          \n"
    MEMACCESS(1)
    "ld1        {v0.16b}, [%1], #16            \n"
    MEMACCESS(2)
    "ld1        {v1.16b}, [%2], #16            \n"
    "subs       %w3, %w3, #16                  \n"
    "umull      v2.8h, v0.8b,  v4.8b           \n"
    "umull2     v3.8h, v0.16b, v4.16b          \n"
    "umlal      v2.8h, v1.8b,  v5.8b           \n"
    "umlal2     v3.8h, v1.16b, v5.16b          \n"
    "rshrn      v0.8b,  v2.8h, #8              \n"
    "rshrn2     v0.16b, v3.8h, #8              \n"
    MEMACCESS(0)
    "st1        {v0.16b}, [%0], #16            \n"
    "b.gt       1b                             \n"
    "b          99f                            \n"

    // Blend 50 / 50.
  "50:                                         \n"
    MEMACCESS(1)
    "ld1        {v0.16b}, [%1], #16            \n"
    MEMACCESS(2)
    "ld1        {v1.16b}, [%2], #16            \n"
    "subs       %w3, %w3, #16                  \n"
    "urhadd     v0.16b, v0.16b, v1.16b         \n"
    MEMACCESS(0)
    "st1        {v0.16b}, [%0], #16            \n"
    "b.gt       50b                            \n"
    "b          99f                            \n"

    // Blend 100 / 0 - Copy row unchanged.
  "100:                                        \n"
    MEMACCESS(1)
    "ld1        {v0.16b}, [%1], #16            \n"
    "subs       %w3, %w3, #16                  \n"
    MEMACCESS(0)
    "st1        {v0.16b}, [%0], #16            \n"
    "b.gt       100b                           \n"

  "99:                                         \n"
  : "+r"(dst_ptr),          // %0
    "+r"(src_ptr),          // %1
    "+r"(src_ptr1),         // %2
    "+r"(dst_width),        // %3
    "+r"(y1_fraction),      // %4
    "+r"(y0_fraction)       // %5
  :
  : "cc", "memory", "v0", "v1", "v3", "v4", "v5"
  );
}
#endif  // HAS_INTERPOLATEROW_NEON

// dr * (256 - sa) / 256 + sr = dr - dr * sa / 256 + sr
#ifdef HAS_ARGBBLENDROW_NEON
void ARGBBlendRow_NEON(const uint8* src_argb0, const uint8* src_argb1,
                       uint8* dst_argb, int width) {
  asm volatile (
    "subs       %w3, %w3, #8                   \n"
    "b.lt       89f                            \n"
    // Blend 8 pixels.
  "8:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%0], #32 \n"  // load 8 ARGB0 pixels
    MEMACCESS(1)
    "ld4        {v4.8b,v5.8b,v6.8b,v7.8b}, [%1], #32 \n"  // load 8 ARGB1 pixels
    "subs       %w3, %w3, #8                   \n"  // 8 processed per loop.
    "umull      v16.8h, v4.8b, v3.8b           \n"  // db * a
    "umull      v17.8h, v5.8b, v3.8b           \n"  // dg * a
    "umull      v18.8h, v6.8b, v3.8b           \n"  // dr * a
    "uqrshrn    v16.8b, v16.8h, #8             \n"  // db >>= 8
    "uqrshrn    v17.8b, v17.8h, #8             \n"  // dg >>= 8
    "uqrshrn    v18.8b, v18.8h, #8             \n"  // dr >>= 8
    "uqsub      v4.8b, v4.8b, v16.8b           \n"  // db - (db * a / 256)
    "uqsub      v5.8b, v5.8b, v17.8b           \n"  // dg - (dg * a / 256)
    "uqsub      v6.8b, v6.8b, v18.8b           \n"  // dr - (dr * a / 256)
    "uqadd      v0.8b, v0.8b, v4.8b            \n"  // + sb
    "uqadd      v1.8b, v1.8b, v5.8b            \n"  // + sg
    "uqadd      v2.8b, v2.8b, v6.8b            \n"  // + sr
    "movi       v3.8b, #255                    \n"  // a = 255
    MEMACCESS(2)
    "st4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%2], #32 \n"  // store 8 ARGB pixels
    "b.ge       8b                             \n"

  "89:                                         \n"
    "adds       %w3, %w3, #8-1                 \n"
    "b.lt       99f                            \n"

    // Blend 1 pixels.
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.b,v1.b,v2.b,v3.b}[0], [%0], #4 \n"  // load 1 pixel ARGB0.
    MEMACCESS(1)
    "ld4        {v4.b,v5.b,v6.b,v7.b}[0], [%1], #4 \n"  // load 1 pixel ARGB1.
    "subs       %w3, %w3, #1                   \n"  // 1 processed per loop.
    "umull      v16.8h, v4.8b, v3.8b           \n"  // db * a
    "umull      v17.8h, v5.8b, v3.8b           \n"  // dg * a
    "umull      v18.8h, v6.8b, v3.8b           \n"  // dr * a
    "uqrshrn    v16.8b, v16.8h, #8             \n"  // db >>= 8
    "uqrshrn    v17.8b, v17.8h, #8             \n"  // dg >>= 8
    "uqrshrn    v18.8b, v18.8h, #8             \n"  // dr >>= 8
    "uqsub      v4.8b, v4.8b, v16.8b           \n"  // db - (db * a / 256)
    "uqsub      v5.8b, v5.8b, v17.8b           \n"  // dg - (dg * a / 256)
    "uqsub      v6.8b, v6.8b, v18.8b           \n"  // dr - (dr * a / 256)
    "uqadd      v0.8b, v0.8b, v4.8b            \n"  // + sb
    "uqadd      v1.8b, v1.8b, v5.8b            \n"  // + sg
    "uqadd      v2.8b, v2.8b, v6.8b            \n"  // + sr
    "movi       v3.8b, #255                    \n"  // a = 255
    MEMACCESS(2)
    "st4        {v0.b,v1.b,v2.b,v3.b}[0], [%2], #4 \n"  // store 1 pixel.
    "b.ge       1b                             \n"

  "99:                                         \n"

  : "+r"(src_argb0),    // %0
    "+r"(src_argb1),    // %1
    "+r"(dst_argb),     // %2
    "+r"(width)         // %3
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7",
    "v16", "v17", "v18"
  );
}
#endif  // HAS_ARGBBLENDROW_NEON

// Attenuate 8 pixels at a time.
#ifdef HAS_ARGBATTENUATEROW_NEON
void ARGBAttenuateRow_NEON(const uint8* src_argb, uint8* dst_argb, int width) {
  asm volatile (
    // Attenuate 8 pixels.
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%0], #32 \n"  // load 8 ARGB pixels
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    "umull      v4.8h, v0.8b, v3.8b            \n"  // b * a
    "umull      v5.8h, v1.8b, v3.8b            \n"  // g * a
    "umull      v6.8h, v2.8b, v3.8b            \n"  // r * a
    "uqrshrn    v0.8b, v4.8h, #8               \n"  // b >>= 8
    "uqrshrn    v1.8b, v5.8h, #8               \n"  // g >>= 8
    "uqrshrn    v2.8b, v6.8h, #8               \n"  // r >>= 8
    MEMACCESS(1)
    "st4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%1], #32 \n"  // store 8 ARGB pixels
    "b.gt       1b                             \n"
  : "+r"(src_argb),   // %0
    "+r"(dst_argb),   // %1
    "+r"(width)       // %2
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6"
  );
}
#endif  // HAS_ARGBATTENUATEROW_NEON

// Quantize 8 ARGB pixels (32 bytes).
// dst = (dst * scale >> 16) * interval_size + interval_offset;
#ifdef HAS_ARGBQUANTIZEROW_NEON
void ARGBQuantizeRow_NEON(uint8* dst_argb, int scale, int interval_size,
                          int interval_offset, int width) {
  asm volatile (
    "dup        v4.8h, %w2                     \n"
    "ushr       v4.8h, v4.8h, #1               \n"  // scale >>= 1
    "dup        v5.8h, %w3                     \n"  // interval multiply.
    "dup        v6.8h, %w4                     \n"  // interval add

    // 8 pixel loop.
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%0]  \n"  // load 8 pixels of ARGB.
    "subs       %w1, %w1, #8                   \n"  // 8 processed per loop.
    "uxtl       v0.8h, v0.8b                   \n"  // b (0 .. 255)
    "uxtl       v1.8h, v1.8b                   \n"
    "uxtl       v2.8h, v2.8b                   \n"
    "sqdmulh    v0.8h, v0.8h, v4.8h            \n"  // b * scale
    "sqdmulh    v1.8h, v1.8h, v4.8h            \n"  // g
    "sqdmulh    v2.8h, v2.8h, v4.8h            \n"  // r
    "mul        v0.8h, v0.8h, v5.8h            \n"  // b * interval_size
    "mul        v1.8h, v1.8h, v5.8h            \n"  // g
    "mul        v2.8h, v2.8h, v5.8h            \n"  // r
    "add        v0.8h, v0.8h, v6.8h            \n"  // b + interval_offset
    "add        v1.8h, v1.8h, v6.8h            \n"  // g
    "add        v2.8h, v2.8h, v6.8h            \n"  // r
    "uqxtn      v0.8b, v0.8h                   \n"
    "uqxtn      v1.8b, v1.8h                   \n"
    "uqxtn      v2.8b, v2.8h                   \n"
    MEMACCESS(0)
    "st4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%0], #32 \n"  // store 8 ARGB pixels
    "b.gt       1b                             \n"
  : "+r"(dst_argb),       // %0
    "+r"(width)           // %1
  : "r"(scale),           // %2
    "r"(interval_size),   // %3
    "r"(interval_offset)  // %4
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6"
  );
}
#endif  // HAS_ARGBQUANTIZEROW_NEON

// Shade 8 pixels at a time by specified value.
// NOTE vqrdmulh.s16 q10, q10, d0[0] must use a scaler register from 0 to 8.
// Rounding in vqrdmulh does +1 to high if high bit of low s16 is set.
#ifdef HAS_ARGBSHADEROW_NEON
void ARGBShadeRow_NEON(const uint8* src_argb, uint8* dst_argb, int width,
                       uint32 value) {
  asm volatile (
    "dup        v0.4s, %w3                     \n"  // duplicate scale value.
    "zip1       v0.8b, v0.8b, v0.8b            \n"  // v0.8b aarrggbb.
    "ushr       v0.8h, v0.8h, #1               \n"  // scale / 2.

    // 8 pixel loop.
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v4.8b,v5.8b,v6.8b,v7.8b}, [%0], #32 \n"  // load 8 ARGB pixels.
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    "uxtl       v4.8h, v4.8b                   \n"  // b (0 .. 255)
    "uxtl       v5.8h, v5.8b                   \n"
    "uxtl       v6.8h, v6.8b                   \n"
    "uxtl       v7.8h, v7.8b                   \n"
    "sqrdmulh   v4.8h, v4.8h, v0.h[0]          \n"  // b * scale * 2
    "sqrdmulh   v5.8h, v5.8h, v0.h[1]          \n"  // g
    "sqrdmulh   v6.8h, v6.8h, v0.h[2]          \n"  // r
    "sqrdmulh   v7.8h, v7.8h, v0.h[3]          \n"  // a
    "uqxtn      v4.8b, v4.8h                   \n"
    "uqxtn      v5.8b, v5.8h                   \n"
    "uqxtn      v6.8b, v6.8h                   \n"
    "uqxtn      v7.8b, v7.8h                   \n"
    MEMACCESS(1)
    "st4        {v4.8b,v5.8b,v6.8b,v7.8b}, [%1], #32 \n"  // store 8 ARGB pixels
    "b.gt       1b                             \n"
  : "+r"(src_argb),       // %0
    "+r"(dst_argb),       // %1
    "+r"(width)           // %2
  : "r"(value)            // %3
  : "cc", "memory", "v0", "v4", "v5", "v6", "v7"
  );
}
#endif  // HAS_ARGBSHADEROW_NEON

// Convert 8 ARGB pixels (64 bytes) to 8 Gray ARGB pixels
// Similar to ARGBToYJ but stores ARGB.
// C code is (15 * b + 75 * g + 38 * r + 64) >> 7;
#ifdef HAS_ARGBGRAYROW_NEON
void ARGBGrayRow_NEON(const uint8* src_argb, uint8* dst_argb, int width) {
  asm volatile (
    "movi       v24.8b, #15                    \n"  // B * 0.11400 coefficient
    "movi       v25.8b, #75                    \n"  // G * 0.58700 coefficient
    "movi       v26.8b, #38                    \n"  // R * 0.29900 coefficient
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%0], #32 \n"  // load 8 ARGB pixels.
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    "umull      v4.8h, v0.8b, v24.8b           \n"  // B
    "umlal      v4.8h, v1.8b, v25.8b           \n"  // G
    "umlal      v4.8h, v2.8b, v26.8b           \n"  // R
    "sqrshrun   v0.8b, v4.8h, #7               \n"  // 15 bit to 8 bit B
    "orr        v1.8b, v0.8b, v0.8b            \n"  // G
    "orr        v2.8b, v0.8b, v0.8b            \n"  // R
    MEMACCESS(1)
    "st4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%1], #32 \n"  // store 8 pixels.
    "b.gt       1b                             \n"
  : "+r"(src_argb),  // %0
    "+r"(dst_argb),  // %1
    "+r"(width)      // %2
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v24", "v25", "v26"
  );
}
#endif  // HAS_ARGBGRAYROW_NEON

// Convert 8 ARGB pixels (32 bytes) to 8 Sepia ARGB pixels.
//    b = (r * 35 + g * 68 + b * 17) >> 7
//    g = (r * 45 + g * 88 + b * 22) >> 7
//    r = (r * 50 + g * 98 + b * 24) >> 7

#ifdef HAS_ARGBSEPIAROW_NEON
void ARGBSepiaRow_NEON(uint8* dst_argb, int width) {
  asm volatile (
    "movi       v20.8b, #17                    \n"  // BB coefficient
    "movi       v21.8b, #68                    \n"  // BG coefficient
    "movi       v22.8b, #35                    \n"  // BR coefficient
    "movi       v24.8b, #22                    \n"  // GB coefficient
    "movi       v25.8b, #88                    \n"  // GG coefficient
    "movi       v26.8b, #45                    \n"  // GR coefficient
    "movi       v28.8b, #24                    \n"  // BB coefficient
    "movi       v29.8b, #98                    \n"  // BG coefficient
    "movi       v30.8b, #50                    \n"  // BR coefficient
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%0] \n"  // load 8 ARGB pixels.
    "subs       %w1, %w1, #8                   \n"  // 8 processed per loop.
    "umull      v4.8h, v0.8b, v20.8b           \n"  // B to Sepia B
    "umlal      v4.8h, v1.8b, v21.8b           \n"  // G
    "umlal      v4.8h, v2.8b, v22.8b           \n"  // R
    "umull      v5.8h, v0.8b, v24.8b           \n"  // B to Sepia G
    "umlal      v5.8h, v1.8b, v25.8b           \n"  // G
    "umlal      v5.8h, v2.8b, v26.8b           \n"  // R
    "umull      v6.8h, v0.8b, v28.8b           \n"  // B to Sepia R
    "umlal      v6.8h, v1.8b, v29.8b           \n"  // G
    "umlal      v6.8h, v2.8b, v30.8b           \n"  // R
    "uqshrn     v0.8b, v4.8h, #7               \n"  // 16 bit to 8 bit B
    "uqshrn     v1.8b, v5.8h, #7               \n"  // 16 bit to 8 bit G
    "uqshrn     v2.8b, v6.8h, #7               \n"  // 16 bit to 8 bit R
    MEMACCESS(0)
    "st4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%0], #32 \n"  // store 8 pixels.
    "b.gt       1b                             \n"
  : "+r"(dst_argb),  // %0
    "+r"(width)      // %1
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7",
    "v20", "v21", "v22", "v24", "v25", "v26", "v28", "v29", "v30"
  );
}
#endif  // HAS_ARGBSEPIAROW_NEON

// Tranform 8 ARGB pixels (32 bytes) with color matrix.
// TODO(fbarchard): Was same as Sepia except matrix is provided.  This function
// needs to saturate.  Consider doing a non-saturating version.
#ifdef HAS_ARGBCOLORMATRIXROW_NEON
void ARGBColorMatrixRow_NEON(const uint8* src_argb, uint8* dst_argb,
                             const int8* matrix_argb, int width) {
  asm volatile (
    MEMACCESS(3)
    "ld1        {v2.16b}, [%3]                 \n"  // load 3 ARGB vectors.
    "sxtl       v0.8h, v2.8b                   \n"  // B,G coefficients s16.
    "sxtl2      v1.8h, v2.16b                  \n"  // R,A coefficients s16.

  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v16.8b,v17.8b,v18.8b,v19.8b}, [%0], #32 \n"  // load 8 pixels.
    "subs       %w2, %w2, #8                   \n"  // 8 processed per loop.
    "uxtl       v16.8h, v16.8b                 \n"  // b (0 .. 255) 16 bit
    "uxtl       v17.8h, v17.8b                 \n"  // g
    "uxtl       v18.8h, v18.8b                 \n"  // r
    "uxtl       v19.8h, v19.8b                 \n"  // a
    "mul        v22.8h, v16.8h, v0.h[0]        \n"  // B = B * Matrix B
    "mul        v23.8h, v16.8h, v0.h[4]        \n"  // G = B * Matrix G
    "mul        v24.8h, v16.8h, v1.h[0]        \n"  // R = B * Matrix R
    "mul        v25.8h, v16.8h, v1.h[4]        \n"  // A = B * Matrix A
    "mul        v4.8h, v17.8h, v0.h[1]         \n"  // B += G * Matrix B
    "mul        v5.8h, v17.8h, v0.h[5]         \n"  // G += G * Matrix G
    "mul        v6.8h, v17.8h, v1.h[1]         \n"  // R += G * Matrix R
    "mul        v7.8h, v17.8h, v1.h[5]         \n"  // A += G * Matrix A
    "sqadd      v22.8h, v22.8h, v4.8h          \n"  // Accumulate B
    "sqadd      v23.8h, v23.8h, v5.8h          \n"  // Accumulate G
    "sqadd      v24.8h, v24.8h, v6.8h          \n"  // Accumulate R
    "sqadd      v25.8h, v25.8h, v7.8h          \n"  // Accumulate A
    "mul        v4.8h, v18.8h, v0.h[2]         \n"  // B += R * Matrix B
    "mul        v5.8h, v18.8h, v0.h[6]         \n"  // G += R * Matrix G
    "mul        v6.8h, v18.8h, v1.h[2]         \n"  // R += R * Matrix R
    "mul        v7.8h, v18.8h, v1.h[6]         \n"  // A += R * Matrix A
    "sqadd      v22.8h, v22.8h, v4.8h          \n"  // Accumulate B
    "sqadd      v23.8h, v23.8h, v5.8h          \n"  // Accumulate G
    "sqadd      v24.8h, v24.8h, v6.8h          \n"  // Accumulate R
    "sqadd      v25.8h, v25.8h, v7.8h          \n"  // Accumulate A
    "mul        v4.8h, v19.8h, v0.h[3]         \n"  // B += A * Matrix B
    "mul        v5.8h, v19.8h, v0.h[7]         \n"  // G += A * Matrix G
    "mul        v6.8h, v19.8h, v1.h[3]         \n"  // R += A * Matrix R
    "mul        v7.8h, v19.8h, v1.h[7]         \n"  // A += A * Matrix A
    "sqadd      v22.8h, v22.8h, v4.8h          \n"  // Accumulate B
    "sqadd      v23.8h, v23.8h, v5.8h          \n"  // Accumulate G
    "sqadd      v24.8h, v24.8h, v6.8h          \n"  // Accumulate R
    "sqadd      v25.8h, v25.8h, v7.8h          \n"  // Accumulate A
    "sqshrun    v16.8b, v22.8h, #6             \n"  // 16 bit to 8 bit B
    "sqshrun    v17.8b, v23.8h, #6             \n"  // 16 bit to 8 bit G
    "sqshrun    v18.8b, v24.8h, #6             \n"  // 16 bit to 8 bit R
    "sqshrun    v19.8b, v25.8h, #6             \n"  // 16 bit to 8 bit A
    MEMACCESS(1)
    "st4        {v16.8b,v17.8b,v18.8b,v19.8b}, [%1], #32 \n"  // store 8 pixels.
    "b.gt       1b                             \n"
  : "+r"(src_argb),   // %0
    "+r"(dst_argb),   // %1
    "+r"(width)       // %2
  : "r"(matrix_argb)  // %3
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v16", "v17",
    "v18", "v19", "v22", "v23", "v24", "v25"
  );
}
#endif  // HAS_ARGBCOLORMATRIXROW_NEON

// TODO(fbarchard): fix vqshrun in ARGBMultiplyRow_NEON and reenable.
// Multiply 2 rows of ARGB pixels together, 8 pixels at a time.
#ifdef HAS_ARGBMULTIPLYROW_NEON
void ARGBMultiplyRow_NEON(const uint8* src_argb0, const uint8* src_argb1,
                          uint8* dst_argb, int width) {
  asm volatile (
    // 8 pixel loop.
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%0], #32 \n"  // load 8 ARGB pixels.
    MEMACCESS(1)
    "ld4        {v4.8b,v5.8b,v6.8b,v7.8b}, [%1], #32 \n"  // load 8 more pixels.
    "subs       %w3, %w3, #8                   \n"  // 8 processed per loop.
    "umull      v0.8h, v0.8b, v4.8b            \n"  // multiply B
    "umull      v1.8h, v1.8b, v5.8b            \n"  // multiply G
    "umull      v2.8h, v2.8b, v6.8b            \n"  // multiply R
    "umull      v3.8h, v3.8b, v7.8b            \n"  // multiply A
    "rshrn      v0.8b, v0.8h, #8               \n"  // 16 bit to 8 bit B
    "rshrn      v1.8b, v1.8h, #8               \n"  // 16 bit to 8 bit G
    "rshrn      v2.8b, v2.8h, #8               \n"  // 16 bit to 8 bit R
    "rshrn      v3.8b, v3.8h, #8               \n"  // 16 bit to 8 bit A
    MEMACCESS(2)
    "st4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%2], #32 \n"  // store 8 ARGB pixels
    "b.gt       1b                             \n"

  : "+r"(src_argb0),  // %0
    "+r"(src_argb1),  // %1
    "+r"(dst_argb),   // %2
    "+r"(width)       // %3
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7"
  );
}
#endif  // HAS_ARGBMULTIPLYROW_NEON

// Add 2 rows of ARGB pixels together, 8 pixels at a time.
#ifdef HAS_ARGBADDROW_NEON
void ARGBAddRow_NEON(const uint8* src_argb0, const uint8* src_argb1,
                     uint8* dst_argb, int width) {
  asm volatile (
    // 8 pixel loop.
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%0], #32 \n"  // load 8 ARGB pixels.
    MEMACCESS(1)
    "ld4        {v4.8b,v5.8b,v6.8b,v7.8b}, [%1], #32 \n"  // load 8 more pixels.
    "subs       %w3, %w3, #8                   \n"  // 8 processed per loop.
    "uqadd      v0.8b, v0.8b, v4.8b            \n"
    "uqadd      v1.8b, v1.8b, v5.8b            \n"
    "uqadd      v2.8b, v2.8b, v6.8b            \n"
    "uqadd      v3.8b, v3.8b, v7.8b            \n"
    MEMACCESS(2)
    "st4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%2], #32 \n"  // store 8 ARGB pixels
    "b.gt       1b                             \n"

  : "+r"(src_argb0),  // %0
    "+r"(src_argb1),  // %1
    "+r"(dst_argb),   // %2
    "+r"(width)       // %3
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7"
  );
}
#endif  // HAS_ARGBADDROW_NEON

// Subtract 2 rows of ARGB pixels, 8 pixels at a time.
#ifdef HAS_ARGBSUBTRACTROW_NEON
void ARGBSubtractRow_NEON(const uint8* src_argb0, const uint8* src_argb1,
                          uint8* dst_argb, int width) {
  asm volatile (
    // 8 pixel loop.
  "1:                                          \n"
    MEMACCESS(0)
    "ld4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%0], #32 \n"  // load 8 ARGB pixels.
    MEMACCESS(1)
    "ld4        {v4.8b,v5.8b,v6.8b,v7.8b}, [%1], #32 \n"  // load 8 more pixels.
    "subs       %w3, %w3, #8                   \n"  // 8 processed per loop.
    "uqsub      v0.8b, v0.8b, v4.8b            \n"
    "uqsub      v1.8b, v1.8b, v5.8b            \n"
    "uqsub      v2.8b, v2.8b, v6.8b            \n"
    "uqsub      v3.8b, v3.8b, v7.8b            \n"
    MEMACCESS(2)
    "st4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%2], #32 \n"  // store 8 ARGB pixels
    "b.gt       1b                             \n"

  : "+r"(src_argb0),  // %0
    "+r"(src_argb1),  // %1
    "+r"(dst_argb),   // %2
    "+r"(width)       // %3
  :
  : "cc", "memory", "v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7"
  );
}
#endif  // HAS_ARGBSUBTRACTROW_NEON

// Adds Sobel X and Sobel Y and stores Sobel into ARGB.
// A = 255
// R = Sobel
// G = Sobel
// B = Sobel
#ifdef HAS_SOBELROW_NEON
void SobelRow_NEON(const uint8* src_sobelx, const uint8* src_sobely,
                     uint8* dst_argb, int width) {
  asm volatile (
    "movi       v3.8b, #255                    \n"  // alpha
    // 8 pixel loop.
  "1:                                          \n"
    MEMACCESS(0)
    "ld1        {v0.8b}, [%0], #8              \n"  // load 8 sobelx.
    MEMACCESS(1)
    "ld1        {v1.8b}, [%1], #8              \n"  // load 8 sobely.
    "subs       %w3, %w3, #8                   \n"  // 8 processed per loop.
    "uqadd      v0.8b, v0.8b, v1.8b            \n"  // add
    "orr        v1.8b, v0.8b, v0.8b            \n"
    "orr        v2.8b, v0.8b, v0.8b            \n"
    MEMACCESS(2)
    "st4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%2], #32 \n"  // store 8 ARGB pixels
    "b.gt       1b                             \n"
  : "+r"(src_sobelx),  // %0
    "+r"(src_sobely),  // %1
    "+r"(dst_argb),    // %2
    "+r"(width)        // %3
  :
  : "cc", "memory", "v0", "v1", "v2", "v3"
  );
}
#endif  // HAS_SOBELROW_NEON

// Adds Sobel X and Sobel Y and stores Sobel into plane.
#ifdef HAS_SOBELTOPLANEROW_NEON
void SobelToPlaneRow_NEON(const uint8* src_sobelx, const uint8* src_sobely,
                          uint8* dst_y, int width) {
  asm volatile (
    // 16 pixel loop.
  "1:                                          \n"
    MEMACCESS(0)
    "ld1        {v0.16b}, [%0], #16            \n"  // load 16 sobelx.
    MEMACCESS(1)
    "ld1        {v1.16b}, [%1], #16            \n"  // load 16 sobely.
    "subs       %w3, %w3, #16                  \n"  // 16 processed per loop.
    "uqadd      v0.16b, v0.16b, v1.16b         \n"  // add
    MEMACCESS(2)
    "st1        {v0.16b}, [%2], #16            \n"  // store 16 pixels.
    "b.gt       1b                             \n"
  : "+r"(src_sobelx),  // %0
    "+r"(src_sobely),  // %1
    "+r"(dst_y),       // %2
    "+r"(width)        // %3
  :
  : "cc", "memory", "v0", "v1"
  );
}
#endif  // HAS_SOBELTOPLANEROW_NEON

// Mixes Sobel X, Sobel Y and Sobel into ARGB.
// A = 255
// R = Sobel X
// G = Sobel
// B = Sobel Y
#ifdef HAS_SOBELXYROW_NEON
void SobelXYRow_NEON(const uint8* src_sobelx, const uint8* src_sobely,
                     uint8* dst_argb, int width) {
  asm volatile (
    "movi       v3.8b, #255                    \n"  // alpha
    // 8 pixel loop.
  "1:                                          \n"
    MEMACCESS(0)
    "ld1        {v2.8b}, [%0], #8              \n"  // load 8 sobelx.
    MEMACCESS(1)
    "ld1        {v0.8b}, [%1], #8              \n"  // load 8 sobely.
    "subs       %w3, %w3, #8                   \n"  // 8 processed per loop.
    "uqadd      v1.8b, v0.8b, v2.8b            \n"  // add
    MEMACCESS(2)
    "st4        {v0.8b,v1.8b,v2.8b,v3.8b}, [%2], #32 \n"  // store 8 ARGB pixels
    "b.gt       1b                             \n"
  : "+r"(src_sobelx),  // %0
    "+r"(src_sobely),  // %1
    "+r"(dst_argb),    // %2
    "+r"(width)        // %3
  :
  : "cc", "memory", "v0", "v1", "v2", "v3"
  );
}
#endif  // HAS_SOBELXYROW_NEON

// SobelX as a matrix is
// -1  0  1
// -2  0  2
// -1  0  1
#ifdef HAS_SOBELXROW_NEON
void SobelXRow_NEON(const uint8* src_y0, const uint8* src_y1,
                    const uint8* src_y2, uint8* dst_sobelx, int width) {
  asm volatile (
  "1:                                          \n"
    MEMACCESS(0)
    "ld1        {v0.8b}, [%0],%5               \n"  // top
    MEMACCESS(0)
    "ld1        {v1.8b}, [%0],%6               \n"
    "usubl      v0.8h, v0.8b, v1.8b            \n"
    MEMACCESS(1)
    "ld1        {v2.8b}, [%1],%5               \n"  // center * 2
    MEMACCESS(1)
    "ld1        {v3.8b}, [%1],%6               \n"
    "usubl      v1.8h, v2.8b, v3.8b            \n"
    "add        v0.8h, v0.8h, v1.8h            \n"
    "add        v0.8h, v0.8h, v1.8h            \n"
    MEMACCESS(2)
    "ld1        {v2.8b}, [%2],%5               \n"  // bottom
    MEMACCESS(2)
    "ld1        {v3.8b}, [%2],%6               \n"
    "subs       %w4, %w4, #8                   \n"  // 8 pixels
    "usubl      v1.8h, v2.8b, v3.8b            \n"
    "add        v0.8h, v0.8h, v1.8h            \n"
    "abs        v0.8h, v0.8h                   \n"
    "uqxtn      v0.8b, v0.8h                   \n"
    MEMACCESS(3)
    "st1        {v0.8b}, [%3], #8              \n"  // store 8 sobelx
    "b.gt       1b                             \n"
  : "+r"(src_y0),      // %0
    "+r"(src_y1),      // %1
    "+r"(src_y2),      // %2
    "+r"(dst_sobelx),  // %3
    "+r"(width)        // %4
  : "r"(2LL),          // %5
    "r"(6LL)           // %6
  : "cc", "memory", "v0", "v1", "v2", "v3"  // Clobber List
  );
}
#endif  // HAS_SOBELXROW_NEON

// SobelY as a matrix is
// -1 -2 -1
//  0  0  0
//  1  2  1
#ifdef HAS_SOBELYROW_NEON
void SobelYRow_NEON(const uint8* src_y0, const uint8* src_y1,
                    uint8* dst_sobely, int width) {
  asm volatile (
  "1:                                          \n"
    MEMACCESS(0)
    "ld1        {v0.8b}, [%0],%4               \n"  // left
    MEMACCESS(1)
    "ld1        {v1.8b}, [%1],%4               \n"
    "usubl      v0.8h, v0.8b, v1.8b            \n"
    MEMACCESS(0)
    "ld1        {v2.8b}, [%0],%4               \n"  // center * 2
    MEMACCESS(1)
    "ld1        {v3.8b}, [%1],%4               \n"
    "usubl      v1.8h, v2.8b, v3.8b            \n"
    "add        v0.8h, v0.8h, v1.8h            \n"
    "add        v0.8h, v0.8h, v1.8h            \n"
    MEMACCESS(0)
    "ld1        {v2.8b}, [%0],%5               \n"  // right
    MEMACCESS(1)
    "ld1        {v3.8b}, [%1],%5               \n"
    "subs       %w3, %w3, #8                   \n"  // 8 pixels
    "usubl      v1.8h, v2.8b, v3.8b            \n"
    "add        v0.8h, v0.8h, v1.8h            \n"
    "abs        v0.8h, v0.8h                   \n"
    "uqxtn      v0.8b, v0.8h                   \n"
    MEMACCESS(2)
    "st1        {v0.8b}, [%2], #8              \n"  // store 8 sobely
    "b.gt       1b                             \n"
  : "+r"(src_y0),      // %0
    "+r"(src_y1),      // %1
    "+r"(dst_sobely),  // %2
    "+r"(width)        // %3
  : "r"(1LL),          // %4
    "r"(6LL)           // %5
  : "cc", "memory", "v0", "v1", "v2", "v3"  // Clobber List
  );
}
#endif  // HAS_SOBELYROW_NEON
#endif  // !defined(LIBYUV_DISABLE_NEON) && defined(__aarch64__)

#ifdef __cplusplus
}  // extern "C"
}  // namespace libyuv
#endif
