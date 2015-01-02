// Copyright 2010 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// YUV->RGB conversion functions
//
// Author: Skal (pascal.massimino@gmail.com)

#include "./yuv.h"

#if defined(WEBP_YUV_USE_TABLE)

static int done = 0;

static WEBP_INLINE uint8_t clip(int v, int max_value) {
  return v < 0 ? 0 : v > max_value ? max_value : v;
}

int16_t VP8kVToR[256], VP8kUToB[256];
int32_t VP8kVToG[256], VP8kUToG[256];
uint8_t VP8kClip[YUV_RANGE_MAX - YUV_RANGE_MIN];
uint8_t VP8kClip4Bits[YUV_RANGE_MAX - YUV_RANGE_MIN];

void VP8YUVInit(void) {
  int i;
  if (done) {
    return;
  }
#ifndef USE_YUVj
  for (i = 0; i < 256; ++i) {
    VP8kVToR[i] = (89858 * (i - 128) + YUV_HALF) >> YUV_FIX;
    VP8kUToG[i] = -22014 * (i - 128) + YUV_HALF;
    VP8kVToG[i] = -45773 * (i - 128);
    VP8kUToB[i] = (113618 * (i - 128) + YUV_HALF) >> YUV_FIX;
  }
  for (i = YUV_RANGE_MIN; i < YUV_RANGE_MAX; ++i) {
    const int k = ((i - 16) * 76283 + YUV_HALF) >> YUV_FIX;
    VP8kClip[i - YUV_RANGE_MIN] = clip(k, 255);
    VP8kClip4Bits[i - YUV_RANGE_MIN] = clip((k + 8) >> 4, 15);
  }
#else
  for (i = 0; i < 256; ++i) {
    VP8kVToR[i] = (91881 * (i - 128) + YUV_HALF) >> YUV_FIX;
    VP8kUToG[i] = -22554 * (i - 128) + YUV_HALF;
    VP8kVToG[i] = -46802 * (i - 128);
    VP8kUToB[i] = (116130 * (i - 128) + YUV_HALF) >> YUV_FIX;
  }
  for (i = YUV_RANGE_MIN; i < YUV_RANGE_MAX; ++i) {
    const int k = i;
    VP8kClip[i - YUV_RANGE_MIN] = clip(k, 255);
    VP8kClip4Bits[i - YUV_RANGE_MIN] = clip((k + 8) >> 4, 15);
  }
#endif

  done = 1;
}

#else

void VP8YUVInit(void) {}

#endif  // WEBP_YUV_USE_TABLE

//-----------------------------------------------------------------------------
// Plain-C version

#define ROW_FUNC(FUNC_NAME, FUNC, XSTEP)                                       \
static void FUNC_NAME(const uint8_t* y,                                        \
                      const uint8_t* u, const uint8_t* v,                      \
                      uint8_t* dst, int len) {                                 \
  const uint8_t* const end = dst + (len & ~1) * XSTEP;                         \
  while (dst != end) {                                                         \
    FUNC(y[0], u[0], v[0], dst);                                               \
    FUNC(y[1], u[0], v[0], dst + XSTEP);                                       \
    y += 2;                                                                    \
    ++u;                                                                       \
    ++v;                                                                       \
    dst += 2 * XSTEP;                                                          \
  }                                                                            \
  if (len & 1) {                                                               \
    FUNC(y[0], u[0], v[0], dst);                                               \
  }                                                                            \
}                                                                              \

// All variants implemented.
ROW_FUNC(YuvToRgbRow,      VP8YuvToRgb,  3)
ROW_FUNC(YuvToBgrRow,      VP8YuvToBgr,  3)
ROW_FUNC(YuvToRgbaRow,     VP8YuvToRgba, 4)
ROW_FUNC(YuvToBgraRow,     VP8YuvToBgra, 4)
ROW_FUNC(YuvToArgbRow,     VP8YuvToArgb, 4)
ROW_FUNC(YuvToRgba4444Row, VP8YuvToRgba4444, 2)
ROW_FUNC(YuvToRgb565Row,   VP8YuvToRgb565, 2)

#undef ROW_FUNC

// Main call for processing a plane with a WebPSamplerRowFunc function:
void WebPSamplerProcessPlane(const uint8_t* y, int y_stride,
                             const uint8_t* u, const uint8_t* v, int uv_stride,
                             uint8_t* dst, int dst_stride,
                             int width, int height, WebPSamplerRowFunc func) {
  int j;
  for (j = 0; j < height; ++j) {
    func(y, u, v, dst, width);
    y += y_stride;
    if (j & 1) {
      u += uv_stride;
      v += uv_stride;
    }
    dst += dst_stride;
  }
}

//-----------------------------------------------------------------------------
// Main call

WebPSamplerRowFunc WebPSamplers[MODE_LAST];

extern void WebPInitSamplersSSE2(void);
extern void WebPInitSamplersMIPS32(void);

void WebPInitSamplers(void) {
  WebPSamplers[MODE_RGB]       = YuvToRgbRow;
  WebPSamplers[MODE_RGBA]      = YuvToRgbaRow;
  WebPSamplers[MODE_BGR]       = YuvToBgrRow;
  WebPSamplers[MODE_BGRA]      = YuvToBgraRow;
  WebPSamplers[MODE_ARGB]      = YuvToArgbRow;
  WebPSamplers[MODE_RGBA_4444] = YuvToRgba4444Row;
  WebPSamplers[MODE_RGB_565]   = YuvToRgb565Row;
  WebPSamplers[MODE_rgbA]      = YuvToRgbaRow;
  WebPSamplers[MODE_bgrA]      = YuvToBgraRow;
  WebPSamplers[MODE_Argb]      = YuvToArgbRow;
  WebPSamplers[MODE_rgbA_4444] = YuvToRgba4444Row;

  // If defined, use CPUInfo() to overwrite some pointers with faster versions.
  if (VP8GetCPUInfo != NULL) {
#if defined(WEBP_USE_SSE2)
    if (VP8GetCPUInfo(kSSE2)) {
      WebPInitSamplersSSE2();
    }
#endif  // WEBP_USE_SSE2
#if defined(WEBP_USE_MIPS32)
    if (VP8GetCPUInfo(kMIPS32)) {
      WebPInitSamplersMIPS32();
    }
#endif  // WEBP_USE_MIPS32
  }
}

//-----------------------------------------------------------------------------
