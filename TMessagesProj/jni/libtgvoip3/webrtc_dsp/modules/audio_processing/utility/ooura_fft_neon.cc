/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * The rdft AEC algorithm, neon version of speed-critical functions.
 *
 * Based on the sse2 version.
 */
#if defined(__arm__) || defined(_M_ARM) || defined(__aarch64__)

#include "modules/audio_processing/utility/ooura_fft.h"

#include <arm_neon.h>

#include "modules/audio_processing/utility/ooura_fft_tables_common.h"
#include "modules/audio_processing/utility/ooura_fft_tables_neon_sse2.h"

namespace webrtc {

#if defined(WEBRTC_HAS_NEON)
void cft1st_128_neon(float* a) {
  const float32x4_t vec_swap_sign = vld1q_f32((float32_t*)k_swap_sign);
  int j, k2;

  for (k2 = 0, j = 0; j < 128; j += 16, k2 += 4) {
    float32x4_t a00v = vld1q_f32(&a[j + 0]);
    float32x4_t a04v = vld1q_f32(&a[j + 4]);
    float32x4_t a08v = vld1q_f32(&a[j + 8]);
    float32x4_t a12v = vld1q_f32(&a[j + 12]);
    float32x4_t a01v = vcombine_f32(vget_low_f32(a00v), vget_low_f32(a08v));
    float32x4_t a23v = vcombine_f32(vget_high_f32(a00v), vget_high_f32(a08v));
    float32x4_t a45v = vcombine_f32(vget_low_f32(a04v), vget_low_f32(a12v));
    float32x4_t a67v = vcombine_f32(vget_high_f32(a04v), vget_high_f32(a12v));
    const float32x4_t wk1rv = vld1q_f32(&rdft_wk1r[k2]);
    const float32x4_t wk1iv = vld1q_f32(&rdft_wk1i[k2]);
    const float32x4_t wk2rv = vld1q_f32(&rdft_wk2r[k2]);
    const float32x4_t wk2iv = vld1q_f32(&rdft_wk2i[k2]);
    const float32x4_t wk3rv = vld1q_f32(&rdft_wk3r[k2]);
    const float32x4_t wk3iv = vld1q_f32(&rdft_wk3i[k2]);
    float32x4_t x0v = vaddq_f32(a01v, a23v);
    const float32x4_t x1v = vsubq_f32(a01v, a23v);
    const float32x4_t x2v = vaddq_f32(a45v, a67v);
    const float32x4_t x3v = vsubq_f32(a45v, a67v);
    const float32x4_t x3w = vrev64q_f32(x3v);
    float32x4_t x0w;
    a01v = vaddq_f32(x0v, x2v);
    x0v = vsubq_f32(x0v, x2v);
    x0w = vrev64q_f32(x0v);
    a45v = vmulq_f32(wk2rv, x0v);
    a45v = vmlaq_f32(a45v, wk2iv, x0w);
    x0v = vmlaq_f32(x1v, x3w, vec_swap_sign);
    x0w = vrev64q_f32(x0v);
    a23v = vmulq_f32(wk1rv, x0v);
    a23v = vmlaq_f32(a23v, wk1iv, x0w);
    x0v = vmlsq_f32(x1v, x3w, vec_swap_sign);
    x0w = vrev64q_f32(x0v);
    a67v = vmulq_f32(wk3rv, x0v);
    a67v = vmlaq_f32(a67v, wk3iv, x0w);
    a00v = vcombine_f32(vget_low_f32(a01v), vget_low_f32(a23v));
    a04v = vcombine_f32(vget_low_f32(a45v), vget_low_f32(a67v));
    a08v = vcombine_f32(vget_high_f32(a01v), vget_high_f32(a23v));
    a12v = vcombine_f32(vget_high_f32(a45v), vget_high_f32(a67v));
    vst1q_f32(&a[j + 0], a00v);
    vst1q_f32(&a[j + 4], a04v);
    vst1q_f32(&a[j + 8], a08v);
    vst1q_f32(&a[j + 12], a12v);
  }
}

void cftmdl_128_neon(float* a) {
  int j;
  const int l = 8;
  const float32x4_t vec_swap_sign = vld1q_f32((float32_t*)k_swap_sign);
  float32x4_t wk1rv = vld1q_f32(cftmdl_wk1r);

  for (j = 0; j < l; j += 2) {
    const float32x2_t a_00 = vld1_f32(&a[j + 0]);
    const float32x2_t a_08 = vld1_f32(&a[j + 8]);
    const float32x2_t a_32 = vld1_f32(&a[j + 32]);
    const float32x2_t a_40 = vld1_f32(&a[j + 40]);
    const float32x4_t a_00_32 = vcombine_f32(a_00, a_32);
    const float32x4_t a_08_40 = vcombine_f32(a_08, a_40);
    const float32x4_t x0r0_0i0_0r1_x0i1 = vaddq_f32(a_00_32, a_08_40);
    const float32x4_t x1r0_1i0_1r1_x1i1 = vsubq_f32(a_00_32, a_08_40);
    const float32x2_t a_16 = vld1_f32(&a[j + 16]);
    const float32x2_t a_24 = vld1_f32(&a[j + 24]);
    const float32x2_t a_48 = vld1_f32(&a[j + 48]);
    const float32x2_t a_56 = vld1_f32(&a[j + 56]);
    const float32x4_t a_16_48 = vcombine_f32(a_16, a_48);
    const float32x4_t a_24_56 = vcombine_f32(a_24, a_56);
    const float32x4_t x2r0_2i0_2r1_x2i1 = vaddq_f32(a_16_48, a_24_56);
    const float32x4_t x3r0_3i0_3r1_x3i1 = vsubq_f32(a_16_48, a_24_56);
    const float32x4_t xx0 = vaddq_f32(x0r0_0i0_0r1_x0i1, x2r0_2i0_2r1_x2i1);
    const float32x4_t xx1 = vsubq_f32(x0r0_0i0_0r1_x0i1, x2r0_2i0_2r1_x2i1);
    const float32x4_t x3i0_3r0_3i1_x3r1 = vrev64q_f32(x3r0_3i0_3r1_x3i1);
    const float32x4_t x1_x3_add =
        vmlaq_f32(x1r0_1i0_1r1_x1i1, vec_swap_sign, x3i0_3r0_3i1_x3r1);
    const float32x4_t x1_x3_sub =
        vmlsq_f32(x1r0_1i0_1r1_x1i1, vec_swap_sign, x3i0_3r0_3i1_x3r1);
    const float32x2_t yy0_a = vdup_lane_f32(vget_high_f32(x1_x3_add), 0);
    const float32x2_t yy0_s = vdup_lane_f32(vget_high_f32(x1_x3_sub), 0);
    const float32x4_t yy0_as = vcombine_f32(yy0_a, yy0_s);
    const float32x2_t yy1_a = vdup_lane_f32(vget_high_f32(x1_x3_add), 1);
    const float32x2_t yy1_s = vdup_lane_f32(vget_high_f32(x1_x3_sub), 1);
    const float32x4_t yy1_as = vcombine_f32(yy1_a, yy1_s);
    const float32x4_t yy0 = vmlaq_f32(yy0_as, vec_swap_sign, yy1_as);
    const float32x4_t yy4 = vmulq_f32(wk1rv, yy0);
    const float32x4_t xx1_rev = vrev64q_f32(xx1);
    const float32x4_t yy4_rev = vrev64q_f32(yy4);

    vst1_f32(&a[j + 0], vget_low_f32(xx0));
    vst1_f32(&a[j + 32], vget_high_f32(xx0));
    vst1_f32(&a[j + 16], vget_low_f32(xx1));
    vst1_f32(&a[j + 48], vget_high_f32(xx1_rev));

    a[j + 48] = -a[j + 48];

    vst1_f32(&a[j + 8], vget_low_f32(x1_x3_add));
    vst1_f32(&a[j + 24], vget_low_f32(x1_x3_sub));
    vst1_f32(&a[j + 40], vget_low_f32(yy4));
    vst1_f32(&a[j + 56], vget_high_f32(yy4_rev));
  }

  {
    const int k = 64;
    const int k1 = 2;
    const int k2 = 2 * k1;
    const float32x4_t wk2rv = vld1q_f32(&rdft_wk2r[k2 + 0]);
    const float32x4_t wk2iv = vld1q_f32(&rdft_wk2i[k2 + 0]);
    const float32x4_t wk1iv = vld1q_f32(&rdft_wk1i[k2 + 0]);
    const float32x4_t wk3rv = vld1q_f32(&rdft_wk3r[k2 + 0]);
    const float32x4_t wk3iv = vld1q_f32(&rdft_wk3i[k2 + 0]);
    wk1rv = vld1q_f32(&rdft_wk1r[k2 + 0]);
    for (j = k; j < l + k; j += 2) {
      const float32x2_t a_00 = vld1_f32(&a[j + 0]);
      const float32x2_t a_08 = vld1_f32(&a[j + 8]);
      const float32x2_t a_32 = vld1_f32(&a[j + 32]);
      const float32x2_t a_40 = vld1_f32(&a[j + 40]);
      const float32x4_t a_00_32 = vcombine_f32(a_00, a_32);
      const float32x4_t a_08_40 = vcombine_f32(a_08, a_40);
      const float32x4_t x0r0_0i0_0r1_x0i1 = vaddq_f32(a_00_32, a_08_40);
      const float32x4_t x1r0_1i0_1r1_x1i1 = vsubq_f32(a_00_32, a_08_40);
      const float32x2_t a_16 = vld1_f32(&a[j + 16]);
      const float32x2_t a_24 = vld1_f32(&a[j + 24]);
      const float32x2_t a_48 = vld1_f32(&a[j + 48]);
      const float32x2_t a_56 = vld1_f32(&a[j + 56]);
      const float32x4_t a_16_48 = vcombine_f32(a_16, a_48);
      const float32x4_t a_24_56 = vcombine_f32(a_24, a_56);
      const float32x4_t x2r0_2i0_2r1_x2i1 = vaddq_f32(a_16_48, a_24_56);
      const float32x4_t x3r0_3i0_3r1_x3i1 = vsubq_f32(a_16_48, a_24_56);
      const float32x4_t xx = vaddq_f32(x0r0_0i0_0r1_x0i1, x2r0_2i0_2r1_x2i1);
      const float32x4_t xx1 = vsubq_f32(x0r0_0i0_0r1_x0i1, x2r0_2i0_2r1_x2i1);
      const float32x4_t x3i0_3r0_3i1_x3r1 = vrev64q_f32(x3r0_3i0_3r1_x3i1);
      const float32x4_t x1_x3_add =
          vmlaq_f32(x1r0_1i0_1r1_x1i1, vec_swap_sign, x3i0_3r0_3i1_x3r1);
      const float32x4_t x1_x3_sub =
          vmlsq_f32(x1r0_1i0_1r1_x1i1, vec_swap_sign, x3i0_3r0_3i1_x3r1);
      float32x4_t xx4 = vmulq_f32(wk2rv, xx1);
      float32x4_t xx12 = vmulq_f32(wk1rv, x1_x3_add);
      float32x4_t xx22 = vmulq_f32(wk3rv, x1_x3_sub);
      xx4 = vmlaq_f32(xx4, wk2iv, vrev64q_f32(xx1));
      xx12 = vmlaq_f32(xx12, wk1iv, vrev64q_f32(x1_x3_add));
      xx22 = vmlaq_f32(xx22, wk3iv, vrev64q_f32(x1_x3_sub));

      vst1_f32(&a[j + 0], vget_low_f32(xx));
      vst1_f32(&a[j + 32], vget_high_f32(xx));
      vst1_f32(&a[j + 16], vget_low_f32(xx4));
      vst1_f32(&a[j + 48], vget_high_f32(xx4));
      vst1_f32(&a[j + 8], vget_low_f32(xx12));
      vst1_f32(&a[j + 40], vget_high_f32(xx12));
      vst1_f32(&a[j + 24], vget_low_f32(xx22));
      vst1_f32(&a[j + 56], vget_high_f32(xx22));
    }
  }
}

__inline static float32x4_t reverse_order_f32x4(float32x4_t in) {
  // A B C D -> C D A B
  const float32x4_t rev = vcombine_f32(vget_high_f32(in), vget_low_f32(in));
  // C D A B -> D C B A
  return vrev64q_f32(rev);
}

void rftfsub_128_neon(float* a) {
  const float* c = rdft_w + 32;
  int j1, j2;
  const float32x4_t mm_half = vdupq_n_f32(0.5f);

  // Vectorized code (four at once).
  // Note: commented number are indexes for the first iteration of the loop.
  for (j1 = 1, j2 = 2; j2 + 7 < 64; j1 += 4, j2 += 8) {
    // Load 'wk'.
    const float32x4_t c_j1 = vld1q_f32(&c[j1]);          //  1,  2,  3,  4,
    const float32x4_t c_k1 = vld1q_f32(&c[29 - j1]);     // 28, 29, 30, 31,
    const float32x4_t wkrt = vsubq_f32(mm_half, c_k1);   // 28, 29, 30, 31,
    const float32x4_t wkr_ = reverse_order_f32x4(wkrt);  // 31, 30, 29, 28,
    const float32x4_t wki_ = c_j1;                       //  1,  2,  3,  4,
    // Load and shuffle 'a'.
    //   2,   4,   6,   8,   3,   5,   7,   9
    float32x4x2_t a_j2_p = vld2q_f32(&a[0 + j2]);
    // 120, 122, 124, 126, 121, 123, 125, 127,
    const float32x4x2_t k2_0_4 = vld2q_f32(&a[122 - j2]);
    // 126, 124, 122, 120
    const float32x4_t a_k2_p0 = reverse_order_f32x4(k2_0_4.val[0]);
    // 127, 125, 123, 121
    const float32x4_t a_k2_p1 = reverse_order_f32x4(k2_0_4.val[1]);
    // Calculate 'x'.
    const float32x4_t xr_ = vsubq_f32(a_j2_p.val[0], a_k2_p0);
    // 2-126, 4-124, 6-122, 8-120,
    const float32x4_t xi_ = vaddq_f32(a_j2_p.val[1], a_k2_p1);
    // 3-127, 5-125, 7-123, 9-121,
    // Calculate product into 'y'.
    //    yr = wkr * xr - wki * xi;
    //    yi = wkr * xi + wki * xr;
    const float32x4_t a_ = vmulq_f32(wkr_, xr_);
    const float32x4_t b_ = vmulq_f32(wki_, xi_);
    const float32x4_t c_ = vmulq_f32(wkr_, xi_);
    const float32x4_t d_ = vmulq_f32(wki_, xr_);
    const float32x4_t yr_ = vsubq_f32(a_, b_);  // 2-126, 4-124, 6-122, 8-120,
    const float32x4_t yi_ = vaddq_f32(c_, d_);  // 3-127, 5-125, 7-123, 9-121,
                                                // Update 'a'.
                                                //    a[j2 + 0] -= yr;
                                                //    a[j2 + 1] -= yi;
                                                //    a[k2 + 0] += yr;
                                                //    a[k2 + 1] -= yi;
    // 126, 124, 122, 120,
    const float32x4_t a_k2_p0n = vaddq_f32(a_k2_p0, yr_);
    // 127, 125, 123, 121,
    const float32x4_t a_k2_p1n = vsubq_f32(a_k2_p1, yi_);
    // Shuffle in right order and store.
    const float32x4_t a_k2_p0nr = vrev64q_f32(a_k2_p0n);
    const float32x4_t a_k2_p1nr = vrev64q_f32(a_k2_p1n);
    // 124, 125, 126, 127, 120, 121, 122, 123
    const float32x4x2_t a_k2_n = vzipq_f32(a_k2_p0nr, a_k2_p1nr);
    //   2,   4,   6,   8,
    a_j2_p.val[0] = vsubq_f32(a_j2_p.val[0], yr_);
    //   3,   5,   7,   9,
    a_j2_p.val[1] = vsubq_f32(a_j2_p.val[1], yi_);
    //   2,   3,   4,   5,   6,   7,   8,   9,
    vst2q_f32(&a[0 + j2], a_j2_p);

    vst1q_f32(&a[122 - j2], a_k2_n.val[1]);
    vst1q_f32(&a[126 - j2], a_k2_n.val[0]);
  }

  // Scalar code for the remaining items.
  for (; j2 < 64; j1 += 1, j2 += 2) {
    const int k2 = 128 - j2;
    const int k1 = 32 - j1;
    const float wkr = 0.5f - c[k1];
    const float wki = c[j1];
    const float xr = a[j2 + 0] - a[k2 + 0];
    const float xi = a[j2 + 1] + a[k2 + 1];
    const float yr = wkr * xr - wki * xi;
    const float yi = wkr * xi + wki * xr;
    a[j2 + 0] -= yr;
    a[j2 + 1] -= yi;
    a[k2 + 0] += yr;
    a[k2 + 1] -= yi;
  }
}

void rftbsub_128_neon(float* a) {
  const float* c = rdft_w + 32;
  int j1, j2;
  const float32x4_t mm_half = vdupq_n_f32(0.5f);

  a[1] = -a[1];
  // Vectorized code (four at once).
  //    Note: commented number are indexes for the first iteration of the loop.
  for (j1 = 1, j2 = 2; j2 + 7 < 64; j1 += 4, j2 += 8) {
    // Load 'wk'.
    const float32x4_t c_j1 = vld1q_f32(&c[j1]);          //  1,  2,  3,  4,
    const float32x4_t c_k1 = vld1q_f32(&c[29 - j1]);     // 28, 29, 30, 31,
    const float32x4_t wkrt = vsubq_f32(mm_half, c_k1);   // 28, 29, 30, 31,
    const float32x4_t wkr_ = reverse_order_f32x4(wkrt);  // 31, 30, 29, 28,
    const float32x4_t wki_ = c_j1;                       //  1,  2,  3,  4,
    // Load and shuffle 'a'.
    //   2,   4,   6,   8,   3,   5,   7,   9
    float32x4x2_t a_j2_p = vld2q_f32(&a[0 + j2]);
    // 120, 122, 124, 126, 121, 123, 125, 127,
    const float32x4x2_t k2_0_4 = vld2q_f32(&a[122 - j2]);
    // 126, 124, 122, 120
    const float32x4_t a_k2_p0 = reverse_order_f32x4(k2_0_4.val[0]);
    // 127, 125, 123, 121
    const float32x4_t a_k2_p1 = reverse_order_f32x4(k2_0_4.val[1]);
    // Calculate 'x'.
    const float32x4_t xr_ = vsubq_f32(a_j2_p.val[0], a_k2_p0);
    // 2-126, 4-124, 6-122, 8-120,
    const float32x4_t xi_ = vaddq_f32(a_j2_p.val[1], a_k2_p1);
    // 3-127, 5-125, 7-123, 9-121,
    // Calculate product into 'y'.
    //    yr = wkr * xr - wki * xi;
    //    yi = wkr * xi + wki * xr;
    const float32x4_t a_ = vmulq_f32(wkr_, xr_);
    const float32x4_t b_ = vmulq_f32(wki_, xi_);
    const float32x4_t c_ = vmulq_f32(wkr_, xi_);
    const float32x4_t d_ = vmulq_f32(wki_, xr_);
    const float32x4_t yr_ = vaddq_f32(a_, b_);  // 2-126, 4-124, 6-122, 8-120,
    const float32x4_t yi_ = vsubq_f32(c_, d_);  // 3-127, 5-125, 7-123, 9-121,
                                                // Update 'a'.
                                                //    a[j2 + 0] -= yr;
                                                //    a[j2 + 1] -= yi;
                                                //    a[k2 + 0] += yr;
                                                //    a[k2 + 1] -= yi;
    // 126, 124, 122, 120,
    const float32x4_t a_k2_p0n = vaddq_f32(a_k2_p0, yr_);
    // 127, 125, 123, 121,
    const float32x4_t a_k2_p1n = vsubq_f32(yi_, a_k2_p1);
    // Shuffle in right order and store.
    //   2,   3,   4,   5,   6,   7,   8,   9,
    const float32x4_t a_k2_p0nr = vrev64q_f32(a_k2_p0n);
    const float32x4_t a_k2_p1nr = vrev64q_f32(a_k2_p1n);
    // 124, 125, 126, 127, 120, 121, 122, 123
    const float32x4x2_t a_k2_n = vzipq_f32(a_k2_p0nr, a_k2_p1nr);
    //   2,   4,   6,   8,
    a_j2_p.val[0] = vsubq_f32(a_j2_p.val[0], yr_);
    //   3,   5,   7,   9,
    a_j2_p.val[1] = vsubq_f32(yi_, a_j2_p.val[1]);
    //   2,   3,   4,   5,   6,   7,   8,   9,
    vst2q_f32(&a[0 + j2], a_j2_p);

    vst1q_f32(&a[122 - j2], a_k2_n.val[1]);
    vst1q_f32(&a[126 - j2], a_k2_n.val[0]);
  }

  // Scalar code for the remaining items.
  for (; j2 < 64; j1 += 1, j2 += 2) {
    const int k2 = 128 - j2;
    const int k1 = 32 - j1;
    const float wkr = 0.5f - c[k1];
    const float wki = c[j1];
    const float xr = a[j2 + 0] - a[k2 + 0];
    const float xi = a[j2 + 1] + a[k2 + 1];
    const float yr = wkr * xr + wki * xi;
    const float yi = wkr * xi - wki * xr;
    a[j2 + 0] = a[j2 + 0] - yr;
    a[j2 + 1] = yi - a[j2 + 1];
    a[k2 + 0] = yr + a[k2 + 0];
    a[k2 + 1] = yi - a[k2 + 1];
  }
  a[65] = -a[65];
}
#endif

}  // namespace webrtc

#endif
