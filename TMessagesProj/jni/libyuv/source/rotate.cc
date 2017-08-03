/*
 *  Copyright 2011 The LibYuv Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS. All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "libyuv/rotate.h"

#include "libyuv/cpu_id.h"
#include "libyuv/convert.h"
#include "libyuv/planar_functions.h"
#include "libyuv/rotate_row.h"
#include "libyuv/row.h"

#ifdef __cplusplus
namespace libyuv {
extern "C" {
#endif

LIBYUV_API
void TransposePlane(const uint8* src, int src_stride,
                    uint8* dst, int dst_stride,
                    int width, int height) {
  int i = height;
  void (*TransposeWx8)(const uint8* src, int src_stride,
                       uint8* dst, int dst_stride, int width) = TransposeWx8_C;
#if defined(HAS_TRANSPOSEWX8_NEON)
  if (TestCpuFlag(kCpuHasNEON)) {
    TransposeWx8 = TransposeWx8_NEON;
  }
#endif
#if defined(HAS_TRANSPOSEWX8_SSSE3)
  if (TestCpuFlag(kCpuHasSSSE3)) {
    TransposeWx8 = TransposeWx8_Any_SSSE3;
    if (IS_ALIGNED(width, 8)) {
      TransposeWx8 = TransposeWx8_SSSE3;
    }
  }
#endif
#if defined(HAS_TRANSPOSEWX8_FAST_SSSE3)
  if (TestCpuFlag(kCpuHasSSSE3)) {
    TransposeWx8 = TransposeWx8_Fast_Any_SSSE3;
    if (IS_ALIGNED(width, 16)) {
      TransposeWx8 = TransposeWx8_Fast_SSSE3;
    }
  }
#endif
#if defined(HAS_TRANSPOSEWX8_DSPR2)
  if (TestCpuFlag(kCpuHasDSPR2)) {
    if (IS_ALIGNED(width, 4) &&
        IS_ALIGNED(src, 4) && IS_ALIGNED(src_stride, 4)) {
      TransposeWx8 = TransposeWx8_Fast_DSPR2;
    } else {
      TransposeWx8 = TransposeWx8_DSPR2;
    }
  }
#endif

  // Work across the source in 8x8 tiles
  while (i >= 8) {
    TransposeWx8(src, src_stride, dst, dst_stride, width);
    src += 8 * src_stride;    // Go down 8 rows.
    dst += 8;                 // Move over 8 columns.
    i -= 8;
  }

  if (i > 0) {
    TransposeWxH_C(src, src_stride, dst, dst_stride, width, i);
  }
}

LIBYUV_API
void RotatePlane90(const uint8* src, int src_stride,
                   uint8* dst, int dst_stride,
                   int width, int height) {
  // Rotate by 90 is a transpose with the source read
  // from bottom to top. So set the source pointer to the end
  // of the buffer and flip the sign of the source stride.
  src += src_stride * (height - 1);
  src_stride = -src_stride;
  TransposePlane(src, src_stride, dst, dst_stride, width, height);
}

LIBYUV_API
void RotatePlane270(const uint8* src, int src_stride,
                    uint8* dst, int dst_stride,
                    int width, int height) {
  // Rotate by 270 is a transpose with the destination written
  // from bottom to top. So set the destination pointer to the end
  // of the buffer and flip the sign of the destination stride.
  dst += dst_stride * (width - 1);
  dst_stride = -dst_stride;
  TransposePlane(src, src_stride, dst, dst_stride, width, height);
}

LIBYUV_API
void RotatePlane180(const uint8* src, int src_stride,
                    uint8* dst, int dst_stride,
                    int width, int height) {
  // Swap first and last row and mirror the content. Uses a temporary row.
  align_buffer_64(row, width);
  const uint8* src_bot = src + src_stride * (height - 1);
  uint8* dst_bot = dst + dst_stride * (height - 1);
  int half_height = (height + 1) >> 1;
  int y;
  void (*MirrorRow)(const uint8* src, uint8* dst, int width) = MirrorRow_C;
  void (*CopyRow)(const uint8* src, uint8* dst, int width) = CopyRow_C;
#if defined(HAS_MIRRORROW_NEON)
  if (TestCpuFlag(kCpuHasNEON)) {
    MirrorRow = MirrorRow_Any_NEON;
    if (IS_ALIGNED(width, 16)) {
      MirrorRow = MirrorRow_NEON;
    }
  }
#endif
#if defined(HAS_MIRRORROW_SSSE3)
  if (TestCpuFlag(kCpuHasSSSE3)) {
    MirrorRow = MirrorRow_Any_SSSE3;
    if (IS_ALIGNED(width, 16)) {
      MirrorRow = MirrorRow_SSSE3;
    }
  }
#endif
#if defined(HAS_MIRRORROW_AVX2)
  if (TestCpuFlag(kCpuHasAVX2)) {
    MirrorRow = MirrorRow_Any_AVX2;
    if (IS_ALIGNED(width, 32)) {
      MirrorRow = MirrorRow_AVX2;
    }
  }
#endif
// TODO(fbarchard): Mirror on mips handle unaligned memory.
#if defined(HAS_MIRRORROW_DSPR2)
  if (TestCpuFlag(kCpuHasDSPR2) &&
      IS_ALIGNED(src, 4) && IS_ALIGNED(src_stride, 4) &&
      IS_ALIGNED(dst, 4) && IS_ALIGNED(dst_stride, 4)) {
    MirrorRow = MirrorRow_DSPR2;
  }
#endif
#if defined(HAS_COPYROW_SSE2)
  if (TestCpuFlag(kCpuHasSSE2)) {
    CopyRow = IS_ALIGNED(width, 32) ? CopyRow_SSE2 : CopyRow_Any_SSE2;
  }
#endif
#if defined(HAS_COPYROW_AVX)
  if (TestCpuFlag(kCpuHasAVX)) {
    CopyRow = IS_ALIGNED(width, 64) ? CopyRow_AVX : CopyRow_Any_AVX;
  }
#endif
#if defined(HAS_COPYROW_ERMS)
  if (TestCpuFlag(kCpuHasERMS)) {
    CopyRow = CopyRow_ERMS;
  }
#endif
#if defined(HAS_COPYROW_NEON)
  if (TestCpuFlag(kCpuHasNEON)) {
    CopyRow = IS_ALIGNED(width, 32) ? CopyRow_NEON : CopyRow_Any_NEON;
  }
#endif
#if defined(HAS_COPYROW_MIPS)
  if (TestCpuFlag(kCpuHasMIPS)) {
    CopyRow = CopyRow_MIPS;
  }
#endif

  // Odd height will harmlessly mirror the middle row twice.
  for (y = 0; y < half_height; ++y) {
    MirrorRow(src, row, width);  // Mirror first row into a buffer
    src += src_stride;
    MirrorRow(src_bot, dst, width);  // Mirror last row into first row
    dst += dst_stride;
    CopyRow(row, dst_bot, width);  // Copy first mirrored row into last
    src_bot -= src_stride;
    dst_bot -= dst_stride;
  }
  free_aligned_buffer_64(row);
}

LIBYUV_API
void TransposeUV(const uint8* src, int src_stride,
                 uint8* dst_a, int dst_stride_a,
                 uint8* dst_b, int dst_stride_b,
                 int width, int height) {
  int i = height;
  void (*TransposeUVWx8)(const uint8* src, int src_stride,
                         uint8* dst_a, int dst_stride_a,
                         uint8* dst_b, int dst_stride_b,
                         int width) = TransposeUVWx8_C;
#if defined(HAS_TRANSPOSEUVWX8_NEON)
  if (TestCpuFlag(kCpuHasNEON)) {
    TransposeUVWx8 = TransposeUVWx8_NEON;
  }
#endif
#if defined(HAS_TRANSPOSEUVWX8_SSE2)
  if (TestCpuFlag(kCpuHasSSE2)) {
    TransposeUVWx8 = TransposeUVWx8_Any_SSE2;
    if (IS_ALIGNED(width, 8)) {
      TransposeUVWx8 = TransposeUVWx8_SSE2;
    }
  }
#endif
#if defined(HAS_TRANSPOSEUVWX8_DSPR2)
  if (TestCpuFlag(kCpuHasDSPR2) && IS_ALIGNED(width, 2) &&
      IS_ALIGNED(src, 4) && IS_ALIGNED(src_stride, 4)) {
    TransposeUVWx8 = TransposeUVWx8_DSPR2;
  }
#endif

  // Work through the source in 8x8 tiles.
  while (i >= 8) {
    TransposeUVWx8(src, src_stride,
                   dst_a, dst_stride_a,
                   dst_b, dst_stride_b,
                   width);
    src += 8 * src_stride;    // Go down 8 rows.
    dst_a += 8;               // Move over 8 columns.
    dst_b += 8;               // Move over 8 columns.
    i -= 8;
  }

  if (i > 0) {
    TransposeUVWxH_C(src, src_stride,
                     dst_a, dst_stride_a,
                     dst_b, dst_stride_b,
                     width, i);
  }
}

LIBYUV_API
void RotateUV90(const uint8* src, int src_stride,
                uint8* dst_a, int dst_stride_a,
                uint8* dst_b, int dst_stride_b,
                int width, int height) {
  src += src_stride * (height - 1);
  src_stride = -src_stride;

  TransposeUV(src, src_stride,
              dst_a, dst_stride_a,
              dst_b, dst_stride_b,
              width, height);
}

LIBYUV_API
void RotateUV270(const uint8* src, int src_stride,
                 uint8* dst_a, int dst_stride_a,
                 uint8* dst_b, int dst_stride_b,
                 int width, int height) {
  dst_a += dst_stride_a * (width - 1);
  dst_b += dst_stride_b * (width - 1);
  dst_stride_a = -dst_stride_a;
  dst_stride_b = -dst_stride_b;

  TransposeUV(src, src_stride,
              dst_a, dst_stride_a,
              dst_b, dst_stride_b,
              width, height);
}

// Rotate 180 is a horizontal and vertical flip.
LIBYUV_API
void RotateUV180(const uint8* src, int src_stride,
                 uint8* dst_a, int dst_stride_a,
                 uint8* dst_b, int dst_stride_b,
                 int width, int height) {
  int i;
  void (*MirrorUVRow)(const uint8* src, uint8* dst_u, uint8* dst_v, int width) =
      MirrorUVRow_C;
#if defined(HAS_MIRRORUVROW_NEON)
  if (TestCpuFlag(kCpuHasNEON) && IS_ALIGNED(width, 8)) {
    MirrorUVRow = MirrorUVRow_NEON;
  }
#endif
#if defined(HAS_MIRRORUVROW_SSSE3)
  if (TestCpuFlag(kCpuHasSSSE3) && IS_ALIGNED(width, 16)) {
    MirrorUVRow = MirrorUVRow_SSSE3;
  }
#endif
#if defined(HAS_MIRRORUVROW_DSPR2)
  if (TestCpuFlag(kCpuHasDSPR2) &&
      IS_ALIGNED(src, 4) && IS_ALIGNED(src_stride, 4)) {
    MirrorUVRow = MirrorUVRow_DSPR2;
  }
#endif

  dst_a += dst_stride_a * (height - 1);
  dst_b += dst_stride_b * (height - 1);

  for (i = 0; i < height; ++i) {
    MirrorUVRow(src, dst_a, dst_b, width);
    src += src_stride;
    dst_a -= dst_stride_a;
    dst_b -= dst_stride_b;
  }
}

LIBYUV_API
int RotatePlane(const uint8* src, int src_stride,
                uint8* dst, int dst_stride,
                int width, int height,
                enum RotationMode mode) {
  if (!src || width <= 0 || height == 0 || !dst) {
    return -1;
  }

  // Negative height means invert the image.
  if (height < 0) {
    height = -height;
    src = src + (height - 1) * src_stride;
    src_stride = -src_stride;
  }

  switch (mode) {
    case kRotate0:
      // copy frame
      CopyPlane(src, src_stride,
                dst, dst_stride,
                width, height);
      return 0;
    case kRotate90:
      RotatePlane90(src, src_stride,
                    dst, dst_stride,
                    width, height);
      return 0;
    case kRotate270:
      RotatePlane270(src, src_stride,
                     dst, dst_stride,
                     width, height);
      return 0;
    case kRotate180:
      RotatePlane180(src, src_stride,
                     dst, dst_stride,
                     width, height);
      return 0;
    default:
      break;
  }
  return -1;
}

LIBYUV_API
int I420Rotate(const uint8* src_y, int src_stride_y,
               const uint8* src_u, int src_stride_u,
               const uint8* src_v, int src_stride_v,
               uint8* dst_y, int dst_stride_y,
               uint8* dst_u, int dst_stride_u,
               uint8* dst_v, int dst_stride_v,
               int width, int height,
               enum RotationMode mode) {
  int halfwidth = (width + 1) >> 1;
  int halfheight = (height + 1) >> 1;
  if (!src_y || !src_u || !src_v || width <= 0 || height == 0 ||
      !dst_y || !dst_u || !dst_v) {
    return -1;
  }

  // Negative height means invert the image.
  if (height < 0) {
    height = -height;
    halfheight = (height + 1) >> 1;
    src_y = src_y + (height - 1) * src_stride_y;
    src_u = src_u + (halfheight - 1) * src_stride_u;
    src_v = src_v + (halfheight - 1) * src_stride_v;
    src_stride_y = -src_stride_y;
    src_stride_u = -src_stride_u;
    src_stride_v = -src_stride_v;
  }

  switch (mode) {
    case kRotate0:
      // copy frame
      return I420Copy(src_y, src_stride_y,
                      src_u, src_stride_u,
                      src_v, src_stride_v,
                      dst_y, dst_stride_y,
                      dst_u, dst_stride_u,
                      dst_v, dst_stride_v,
                      width, height);
    case kRotate90:
      RotatePlane90(src_y, src_stride_y,
                    dst_y, dst_stride_y,
                    width, height);
      RotatePlane90(src_u, src_stride_u,
                    dst_u, dst_stride_u,
                    halfwidth, halfheight);
      RotatePlane90(src_v, src_stride_v,
                    dst_v, dst_stride_v,
                    halfwidth, halfheight);
      return 0;
    case kRotate270:
      RotatePlane270(src_y, src_stride_y,
                     dst_y, dst_stride_y,
                     width, height);
      RotatePlane270(src_u, src_stride_u,
                     dst_u, dst_stride_u,
                     halfwidth, halfheight);
      RotatePlane270(src_v, src_stride_v,
                     dst_v, dst_stride_v,
                     halfwidth, halfheight);
      return 0;
    case kRotate180:
      RotatePlane180(src_y, src_stride_y,
                     dst_y, dst_stride_y,
                     width, height);
      RotatePlane180(src_u, src_stride_u,
                     dst_u, dst_stride_u,
                     halfwidth, halfheight);
      RotatePlane180(src_v, src_stride_v,
                     dst_v, dst_stride_v,
                     halfwidth, halfheight);
      return 0;
    default:
      break;
  }
  return -1;
}

LIBYUV_API
int NV12ToI420Rotate(const uint8* src_y, int src_stride_y,
                     const uint8* src_uv, int src_stride_uv,
                     uint8* dst_y, int dst_stride_y,
                     uint8* dst_u, int dst_stride_u,
                     uint8* dst_v, int dst_stride_v,
                     int width, int height,
                     enum RotationMode mode) {
  int halfwidth = (width + 1) >> 1;
  int halfheight = (height + 1) >> 1;
  if (!src_y || !src_uv || width <= 0 || height == 0 ||
      !dst_y || !dst_u || !dst_v) {
    return -1;
  }

  // Negative height means invert the image.
  if (height < 0) {
    height = -height;
    halfheight = (height + 1) >> 1;
    src_y = src_y + (height - 1) * src_stride_y;
    src_uv = src_uv + (halfheight - 1) * src_stride_uv;
    src_stride_y = -src_stride_y;
    src_stride_uv = -src_stride_uv;
  }

  switch (mode) {
    case kRotate0:
      // copy frame
      return NV12ToI420(src_y, src_stride_y,
                        src_uv, src_stride_uv,
                        dst_y, dst_stride_y,
                        dst_u, dst_stride_u,
                        dst_v, dst_stride_v,
                        width, height);
    case kRotate90:
      RotatePlane90(src_y, src_stride_y,
                    dst_y, dst_stride_y,
                    width, height);
      RotateUV90(src_uv, src_stride_uv,
                 dst_u, dst_stride_u,
                 dst_v, dst_stride_v,
                 halfwidth, halfheight);
      return 0;
    case kRotate270:
      RotatePlane270(src_y, src_stride_y,
                     dst_y, dst_stride_y,
                     width, height);
      RotateUV270(src_uv, src_stride_uv,
                  dst_u, dst_stride_u,
                  dst_v, dst_stride_v,
                  halfwidth, halfheight);
      return 0;
    case kRotate180:
      RotatePlane180(src_y, src_stride_y,
                     dst_y, dst_stride_y,
                     width, height);
      RotateUV180(src_uv, src_stride_uv,
                  dst_u, dst_stride_u,
                  dst_v, dst_stride_v,
                  halfwidth, halfheight);
      return 0;
    default:
      break;
  }
  return -1;
}

#ifdef __cplusplus
}  // extern "C"
}  // namespace libyuv
#endif
