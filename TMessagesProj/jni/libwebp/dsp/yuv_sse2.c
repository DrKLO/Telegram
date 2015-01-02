// Copyright 2014 Google Inc. All Rights Reserved.
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

#if defined(WEBP_USE_SSE2)

#include <emmintrin.h>
#include <string.h>   // for memcpy

typedef union {   // handy struct for converting SSE2 registers
  int32_t i32[4];
  uint8_t u8[16];
  __m128i m;
} VP8kCstSSE2;

#if defined(WEBP_YUV_USE_SSE2_TABLES)

#include "./yuv_tables_sse2.h"

void VP8YUVInitSSE2(void) {}

#else

static int done_sse2 = 0;
static VP8kCstSSE2 VP8kUtoRGBA[256], VP8kVtoRGBA[256], VP8kYtoRGBA[256];

void VP8YUVInitSSE2(void) {
  if (!done_sse2) {
    int i;
    for (i = 0; i < 256; ++i) {
      VP8kYtoRGBA[i].i32[0] =
        VP8kYtoRGBA[i].i32[1] =
        VP8kYtoRGBA[i].i32[2] = (i - 16) * kYScale + YUV_HALF2;
      VP8kYtoRGBA[i].i32[3] = 0xff << YUV_FIX2;

      VP8kUtoRGBA[i].i32[0] = 0;
      VP8kUtoRGBA[i].i32[1] = -kUToG * (i - 128);
      VP8kUtoRGBA[i].i32[2] =  kUToB * (i - 128);
      VP8kUtoRGBA[i].i32[3] = 0;

      VP8kVtoRGBA[i].i32[0] =  kVToR * (i - 128);
      VP8kVtoRGBA[i].i32[1] = -kVToG * (i - 128);
      VP8kVtoRGBA[i].i32[2] = 0;
      VP8kVtoRGBA[i].i32[3] = 0;
    }
    done_sse2 = 1;

#if 0   // code used to generate 'yuv_tables_sse2.h'
    printf("static const VP8kCstSSE2 VP8kYtoRGBA[256] = {\n");
    for (i = 0; i < 256; ++i) {
      printf("  {{0x%.8x, 0x%.8x, 0x%.8x, 0x%.8x}},\n",
             VP8kYtoRGBA[i].i32[0], VP8kYtoRGBA[i].i32[1],
             VP8kYtoRGBA[i].i32[2], VP8kYtoRGBA[i].i32[3]);
    }
    printf("};\n\n");
    printf("static const VP8kCstSSE2 VP8kUtoRGBA[256] = {\n");
    for (i = 0; i < 256; ++i) {
      printf("  {{0, 0x%.8x, 0x%.8x, 0}},\n",
             VP8kUtoRGBA[i].i32[1], VP8kUtoRGBA[i].i32[2]);
    }
    printf("};\n\n");
    printf("static VP8kCstSSE2 VP8kVtoRGBA[256] = {\n");
    for (i = 0; i < 256; ++i) {
      printf("  {{0x%.8x, 0x%.8x, 0, 0}},\n",
             VP8kVtoRGBA[i].i32[0], VP8kVtoRGBA[i].i32[1]);
    }
    printf("};\n\n");
#endif
  }
}

#endif  // WEBP_YUV_USE_SSE2_TABLES

//-----------------------------------------------------------------------------

static WEBP_INLINE __m128i LoadUVPart(int u, int v) {
  const __m128i u_part = _mm_loadu_si128(&VP8kUtoRGBA[u].m);
  const __m128i v_part = _mm_loadu_si128(&VP8kVtoRGBA[v].m);
  const __m128i uv_part = _mm_add_epi32(u_part, v_part);
  return uv_part;
}

static WEBP_INLINE __m128i GetRGBA32bWithUV(int y, const __m128i uv_part) {
  const __m128i y_part = _mm_loadu_si128(&VP8kYtoRGBA[y].m);
  const __m128i rgba1 = _mm_add_epi32(y_part, uv_part);
  const __m128i rgba2 = _mm_srai_epi32(rgba1, YUV_FIX2);
  return rgba2;
}

static WEBP_INLINE __m128i GetRGBA32b(int y, int u, int v) {
  const __m128i uv_part = LoadUVPart(u, v);
  return GetRGBA32bWithUV(y, uv_part);
}

static WEBP_INLINE void YuvToRgbSSE2(uint8_t y, uint8_t u, uint8_t v,
                                     uint8_t* const rgb) {
  const __m128i tmp0 = GetRGBA32b(y, u, v);
  const __m128i tmp1 = _mm_packs_epi32(tmp0, tmp0);
  const __m128i tmp2 = _mm_packus_epi16(tmp1, tmp1);
  // Note: we store 8 bytes at a time, not 3 bytes! -> memory stomp
  _mm_storel_epi64((__m128i*)rgb, tmp2);
}

static WEBP_INLINE void YuvToBgrSSE2(uint8_t y, uint8_t u, uint8_t v,
                                     uint8_t* const bgr) {
  const __m128i tmp0 = GetRGBA32b(y, u, v);
  const __m128i tmp1 = _mm_shuffle_epi32(tmp0, _MM_SHUFFLE(3, 0, 1, 2));
  const __m128i tmp2 = _mm_packs_epi32(tmp1, tmp1);
  const __m128i tmp3 = _mm_packus_epi16(tmp2, tmp2);
  // Note: we store 8 bytes at a time, not 3 bytes! -> memory stomp
  _mm_storel_epi64((__m128i*)bgr, tmp3);
}

//-----------------------------------------------------------------------------
// Convert spans of 32 pixels to various RGB formats for the fancy upsampler.

#ifdef FANCY_UPSAMPLING

void VP8YuvToRgba32(const uint8_t* y, const uint8_t* u, const uint8_t* v,
                    uint8_t* dst) {
  int n;
  for (n = 0; n < 32; n += 4) {
    const __m128i tmp0_1 = GetRGBA32b(y[n + 0], u[n + 0], v[n + 0]);
    const __m128i tmp0_2 = GetRGBA32b(y[n + 1], u[n + 1], v[n + 1]);
    const __m128i tmp0_3 = GetRGBA32b(y[n + 2], u[n + 2], v[n + 2]);
    const __m128i tmp0_4 = GetRGBA32b(y[n + 3], u[n + 3], v[n + 3]);
    const __m128i tmp1_1 = _mm_packs_epi32(tmp0_1, tmp0_2);
    const __m128i tmp1_2 = _mm_packs_epi32(tmp0_3, tmp0_4);
    const __m128i tmp2 = _mm_packus_epi16(tmp1_1, tmp1_2);
    _mm_storeu_si128((__m128i*)dst, tmp2);
    dst += 4 * 4;
  }
}

void VP8YuvToBgra32(const uint8_t* y, const uint8_t* u, const uint8_t* v,
                    uint8_t* dst) {
  int n;
  for (n = 0; n < 32; n += 2) {
    const __m128i tmp0_1 = GetRGBA32b(y[n + 0], u[n + 0], v[n + 0]);
    const __m128i tmp0_2 = GetRGBA32b(y[n + 1], u[n + 1], v[n + 1]);
    const __m128i tmp1_1 = _mm_shuffle_epi32(tmp0_1, _MM_SHUFFLE(3, 0, 1, 2));
    const __m128i tmp1_2 = _mm_shuffle_epi32(tmp0_2, _MM_SHUFFLE(3, 0, 1, 2));
    const __m128i tmp2_1 = _mm_packs_epi32(tmp1_1, tmp1_2);
    const __m128i tmp3 = _mm_packus_epi16(tmp2_1, tmp2_1);
    _mm_storel_epi64((__m128i*)dst, tmp3);
    dst += 4 * 2;
  }
}

void VP8YuvToRgb32(const uint8_t* y, const uint8_t* u, const uint8_t* v,
                   uint8_t* dst) {
  int n;
  uint8_t tmp0[2 * 3 + 5 + 15];
  uint8_t* const tmp = (uint8_t*)((uintptr_t)(tmp0 + 15) & ~15);  // align
  for (n = 0; n < 30; ++n) {   // we directly stomp the *dst memory
    YuvToRgbSSE2(y[n], u[n], v[n], dst + n * 3);
  }
  // Last two pixels are special: we write in a tmp buffer before sending
  // to dst.
  YuvToRgbSSE2(y[n + 0], u[n + 0], v[n + 0], tmp + 0);
  YuvToRgbSSE2(y[n + 1], u[n + 1], v[n + 1], tmp + 3);
  memcpy(dst + n * 3, tmp, 2 * 3);
}

void VP8YuvToBgr32(const uint8_t* y, const uint8_t* u, const uint8_t* v,
                   uint8_t* dst) {
  int n;
  uint8_t tmp0[2 * 3 + 5 + 15];
  uint8_t* const tmp = (uint8_t*)((uintptr_t)(tmp0 + 15) & ~15);  // align
  for (n = 0; n < 30; ++n) {
    YuvToBgrSSE2(y[n], u[n], v[n], dst + n * 3);
  }
  YuvToBgrSSE2(y[n + 0], u[n + 0], v[n + 0], tmp + 0);
  YuvToBgrSSE2(y[n + 1], u[n + 1], v[n + 1], tmp + 3);
  memcpy(dst + n * 3, tmp, 2 * 3);
}

#endif  // FANCY_UPSAMPLING

//-----------------------------------------------------------------------------
// Arbitrary-length row conversion functions

static void YuvToRgbaRowSSE2(const uint8_t* y,
                             const uint8_t* u, const uint8_t* v,
                             uint8_t* dst, int len) {
  int n;
  for (n = 0; n + 4 <= len; n += 4) {
    const __m128i uv_0 = LoadUVPart(u[0], v[0]);
    const __m128i uv_1 = LoadUVPart(u[1], v[1]);
    const __m128i tmp0_1 = GetRGBA32bWithUV(y[0], uv_0);
    const __m128i tmp0_2 = GetRGBA32bWithUV(y[1], uv_0);
    const __m128i tmp0_3 = GetRGBA32bWithUV(y[2], uv_1);
    const __m128i tmp0_4 = GetRGBA32bWithUV(y[3], uv_1);
    const __m128i tmp1_1 = _mm_packs_epi32(tmp0_1, tmp0_2);
    const __m128i tmp1_2 = _mm_packs_epi32(tmp0_3, tmp0_4);
    const __m128i tmp2 = _mm_packus_epi16(tmp1_1, tmp1_2);
    _mm_storeu_si128((__m128i*)dst, tmp2);
    dst += 4 * 4;
    y += 4;
    u += 2;
    v += 2;
  }
  // Finish off
  while (n < len) {
    VP8YuvToRgba(y[0], u[0], v[0], dst);
    dst += 4;
    ++y;
    u += (n & 1);
    v += (n & 1);
    ++n;
  }
}

static void YuvToBgraRowSSE2(const uint8_t* y,
                             const uint8_t* u, const uint8_t* v,
                             uint8_t* dst, int len) {
  int n;
  for (n = 0; n + 2 <= len; n += 2) {
    const __m128i uv_0 = LoadUVPart(u[0], v[0]);
    const __m128i tmp0_1 = GetRGBA32bWithUV(y[0], uv_0);
    const __m128i tmp0_2 = GetRGBA32bWithUV(y[1], uv_0);
    const __m128i tmp1_1 = _mm_shuffle_epi32(tmp0_1, _MM_SHUFFLE(3, 0, 1, 2));
    const __m128i tmp1_2 = _mm_shuffle_epi32(tmp0_2, _MM_SHUFFLE(3, 0, 1, 2));
    const __m128i tmp2_1 = _mm_packs_epi32(tmp1_1, tmp1_2);
    const __m128i tmp3 = _mm_packus_epi16(tmp2_1, tmp2_1);
    _mm_storel_epi64((__m128i*)dst, tmp3);
    dst += 4 * 2;
    y += 2;
    ++u;
    ++v;
  }
  // Finish off
  if (len & 1) {
    VP8YuvToBgra(y[0], u[0], v[0], dst);
  }
}

static void YuvToArgbRowSSE2(const uint8_t* y,
                             const uint8_t* u, const uint8_t* v,
                             uint8_t* dst, int len) {
  int n;
  for (n = 0; n + 2 <= len; n += 2) {
    const __m128i uv_0 = LoadUVPart(u[0], v[0]);
    const __m128i tmp0_1 = GetRGBA32bWithUV(y[0], uv_0);
    const __m128i tmp0_2 = GetRGBA32bWithUV(y[1], uv_0);
    const __m128i tmp1_1 = _mm_shuffle_epi32(tmp0_1, _MM_SHUFFLE(2, 1, 0, 3));
    const __m128i tmp1_2 = _mm_shuffle_epi32(tmp0_2, _MM_SHUFFLE(2, 1, 0, 3));
    const __m128i tmp2_1 = _mm_packs_epi32(tmp1_1, tmp1_2);
    const __m128i tmp3 = _mm_packus_epi16(tmp2_1, tmp2_1);
    _mm_storel_epi64((__m128i*)dst, tmp3);
    dst += 4 * 2;
    y += 2;
    ++u;
    ++v;
  }
  // Finish off
  if (len & 1) {
    VP8YuvToArgb(y[0], u[0], v[0], dst);
  }
}

static void YuvToRgbRowSSE2(const uint8_t* y,
                            const uint8_t* u, const uint8_t* v,
                            uint8_t* dst, int len) {
  int n;
  for (n = 0; n + 2 < len; ++n) {   // we directly stomp the *dst memory
    YuvToRgbSSE2(y[0], u[0], v[0], dst);  // stomps 8 bytes
    dst += 3;
    ++y;
    u += (n & 1);
    v += (n & 1);
  }
  VP8YuvToRgb(y[0], u[0], v[0], dst);
  if (len > 1) {
    VP8YuvToRgb(y[1], u[n & 1], v[n & 1], dst + 3);
  }
}

static void YuvToBgrRowSSE2(const uint8_t* y,
                            const uint8_t* u, const uint8_t* v,
                            uint8_t* dst, int len) {
  int n;
  for (n = 0; n + 2 < len; ++n) {   // we directly stomp the *dst memory
    YuvToBgrSSE2(y[0], u[0], v[0], dst);  // stomps 8 bytes
    dst += 3;
    ++y;
    u += (n & 1);
    v += (n & 1);
  }
  VP8YuvToBgr(y[0], u[0], v[0], dst + 0);
  if (len > 1) {
    VP8YuvToBgr(y[1], u[n & 1], v[n & 1], dst + 3);
  }
}

#endif  // WEBP_USE_SSE2

//------------------------------------------------------------------------------
// Entry point

extern void WebPInitSamplersSSE2(void);

void WebPInitSamplersSSE2(void) {
#if defined(WEBP_USE_SSE2)
  WebPSamplers[MODE_RGB]  = YuvToRgbRowSSE2;
  WebPSamplers[MODE_RGBA] = YuvToRgbaRowSSE2;
  WebPSamplers[MODE_BGR]  = YuvToBgrRowSSE2;
  WebPSamplers[MODE_BGRA] = YuvToBgraRowSSE2;
  WebPSamplers[MODE_ARGB] = YuvToArgbRowSSE2;
#endif  // WEBP_USE_SSE2
}
