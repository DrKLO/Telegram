// Copyright 2014 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// WebPPicture tools for measuring distortion
//
// Author: Skal (pascal.massimino@gmail.com)

#include <math.h>

#include "./vp8enci.h"

//------------------------------------------------------------------------------
// local-min distortion
//
// For every pixel in the *reference* picture, we search for the local best
// match in the compressed image. This is not a symmetrical measure.

#define RADIUS 2  // search radius. Shouldn't be too large.

static float AccumulateLSIM(const uint8_t* src, int src_stride,
                            const uint8_t* ref, int ref_stride,
                            int w, int h) {
  int x, y;
  double total_sse = 0.;
  for (y = 0; y < h; ++y) {
    const int y_0 = (y - RADIUS < 0) ? 0 : y - RADIUS;
    const int y_1 = (y + RADIUS + 1 >= h) ? h : y + RADIUS + 1;
    for (x = 0; x < w; ++x) {
      const int x_0 = (x - RADIUS < 0) ? 0 : x - RADIUS;
      const int x_1 = (x + RADIUS + 1 >= w) ? w : x + RADIUS + 1;
      double best_sse = 255. * 255.;
      const double value = (double)ref[y * ref_stride + x];
      int i, j;
      for (j = y_0; j < y_1; ++j) {
        const uint8_t* s = src + j * src_stride;
        for (i = x_0; i < x_1; ++i) {
          const double sse = (double)(s[i] - value) * (s[i] - value);
          if (sse < best_sse) best_sse = sse;
        }
      }
      total_sse += best_sse;
    }
  }
  return (float)total_sse;
}
#undef RADIUS

//------------------------------------------------------------------------------
// Distortion

// Max value returned in case of exact similarity.
static const double kMinDistortion_dB = 99.;
static float GetPSNR(const double v) {
  return (float)((v > 0.) ? -4.3429448 * log(v / (255 * 255.))
                          : kMinDistortion_dB);
}

int WebPPictureDistortion(const WebPPicture* src, const WebPPicture* ref,
                          int type, float result[5]) {
  DistoStats stats[5];
  int has_alpha;
  int uv_w, uv_h;

  if (src == NULL || ref == NULL ||
      src->width != ref->width || src->height != ref->height ||
      src->y == NULL || ref->y == NULL ||
      src->u == NULL || ref->u == NULL ||
      src->v == NULL || ref->v == NULL ||
      result == NULL) {
    return 0;
  }
  // TODO(skal): provide distortion for ARGB too.
  if (src->use_argb == 1 || src->use_argb != ref->use_argb) {
    return 0;
  }

  has_alpha = !!(src->colorspace & WEBP_CSP_ALPHA_BIT);
  if (has_alpha != !!(ref->colorspace & WEBP_CSP_ALPHA_BIT) ||
      (has_alpha && (src->a == NULL || ref->a == NULL))) {
    return 0;
  }

  memset(stats, 0, sizeof(stats));

  uv_w = (src->width + 1) >> 1;
  uv_h = (src->height + 1) >> 1;
  if (type >= 2) {
    float sse[4];
    sse[0] = AccumulateLSIM(src->y, src->y_stride,
                            ref->y, ref->y_stride, src->width, src->height);
    sse[1] = AccumulateLSIM(src->u, src->uv_stride,
                            ref->u, ref->uv_stride, uv_w, uv_h);
    sse[2] = AccumulateLSIM(src->v, src->uv_stride,
                            ref->v, ref->uv_stride, uv_w, uv_h);
    sse[3] = has_alpha ? AccumulateLSIM(src->a, src->a_stride,
                                        ref->a, ref->a_stride,
                                        src->width, src->height)
                       : 0.f;
    result[0] = GetPSNR(sse[0] / (src->width * src->height));
    result[1] = GetPSNR(sse[1] / (uv_w * uv_h));
    result[2] = GetPSNR(sse[2] / (uv_w * uv_h));
    result[3] = GetPSNR(sse[3] / (src->width * src->height));
    {
      double total_sse = sse[0] + sse[1] + sse[2];
      int total_pixels = src->width * src->height + 2 * uv_w * uv_h;
      if (has_alpha) {
        total_pixels += src->width * src->height;
        total_sse += sse[3];
      }
      result[4] = GetPSNR(total_sse / total_pixels);
    }
  } else {
    int c;
    VP8SSIMAccumulatePlane(src->y, src->y_stride,
                           ref->y, ref->y_stride,
                           src->width, src->height, &stats[0]);
    VP8SSIMAccumulatePlane(src->u, src->uv_stride,
                           ref->u, ref->uv_stride,
                           uv_w, uv_h, &stats[1]);
    VP8SSIMAccumulatePlane(src->v, src->uv_stride,
                           ref->v, ref->uv_stride,
                           uv_w, uv_h, &stats[2]);
    if (has_alpha) {
      VP8SSIMAccumulatePlane(src->a, src->a_stride,
                             ref->a, ref->a_stride,
                             src->width, src->height, &stats[3]);
    }
    for (c = 0; c <= 4; ++c) {
      if (type == 1) {
        const double v = VP8SSIMGet(&stats[c]);
        result[c] = (float)((v < 1.) ? -10.0 * log10(1. - v)
                                     : kMinDistortion_dB);
      } else {
        const double v = VP8SSIMGetSquaredError(&stats[c]);
        result[c] = GetPSNR(v);
      }
      // Accumulate forward
      if (c < 4) VP8SSIMAddStats(&stats[c], &stats[4]);
    }
  }
  return 1;
}

//------------------------------------------------------------------------------
