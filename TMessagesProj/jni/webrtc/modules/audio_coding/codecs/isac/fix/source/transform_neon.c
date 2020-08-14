/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <arm_neon.h>

#include "modules/audio_coding/codecs/isac/fix/source/codec.h"
#include "modules/audio_coding/codecs/isac/fix/source/fft.h"
#include "modules/audio_coding/codecs/isac/fix/source/settings.h"

// Tables are defined in transform_tables.c file.
// Cosine table 1 in Q14.
extern const int16_t WebRtcIsacfix_kCosTab1[FRAMESAMPLES/2];
// Sine table 1 in Q14.
extern const int16_t WebRtcIsacfix_kSinTab1[FRAMESAMPLES/2];
// Sine table 2 in Q14.
extern const int16_t WebRtcIsacfix_kSinTab2[FRAMESAMPLES/4];

static inline int32_t ComplexMulAndFindMaxNeon(int16_t* inre1Q9,
                                               int16_t* inre2Q9,
                                               int32_t* outreQ16,
                                               int32_t* outimQ16) {
  int k;
  const int16_t* kCosTab = &WebRtcIsacfix_kCosTab1[0];
  const int16_t* kSinTab = &WebRtcIsacfix_kSinTab1[0];
  // 0.5 / sqrt(240) in Q19 is round((.5 / sqrt(240)) * (2^19)) = 16921.
  // Use "16921 << 5" and vqdmulh, instead of ">> 26" as in the C code.
  int32_t fact  = 16921 << 5;
  int32x4_t factq = vdupq_n_s32(fact);
  uint32x4_t max_r = vdupq_n_u32(0);
  uint32x4_t max_i = vdupq_n_u32(0);

  for (k = 0; k < FRAMESAMPLES/2; k += 8) {
    int16x8_t tmpr = vld1q_s16(kCosTab);
    int16x8_t tmpi = vld1q_s16(kSinTab);
    int16x8_t inre1 = vld1q_s16(inre1Q9);
    int16x8_t inre2 = vld1q_s16(inre2Q9);
    kCosTab += 8;
    kSinTab += 8;
    inre1Q9 += 8;
    inre2Q9 += 8;

    // Use ">> 26", instead of ">> 7", ">> 16" and then ">> 3" as in the C code.
    int32x4_t tmp0 = vmull_s16(vget_low_s16(tmpr), vget_low_s16(inre1));
    int32x4_t tmp1 = vmull_s16(vget_low_s16(tmpr), vget_low_s16(inre2));
    tmp0 = vmlal_s16(tmp0, vget_low_s16(tmpi), vget_low_s16(inre2));
    tmp1 = vmlsl_s16(tmp1, vget_low_s16(tmpi), vget_low_s16(inre1));
#if defined(WEBRTC_ARCH_ARM64)
    int32x4_t tmp2 = vmull_high_s16(tmpr, inre1);
    int32x4_t tmp3 = vmull_high_s16(tmpr, inre2);
    tmp2 = vmlal_high_s16(tmp2, tmpi, inre2);
    tmp3 = vmlsl_high_s16(tmp3, tmpi, inre1);
#else
    int32x4_t tmp2 = vmull_s16(vget_high_s16(tmpr), vget_high_s16(inre1));
    int32x4_t tmp3 = vmull_s16(vget_high_s16(tmpr), vget_high_s16(inre2));
    tmp2 = vmlal_s16(tmp2, vget_high_s16(tmpi), vget_high_s16(inre2));
    tmp3 = vmlsl_s16(tmp3, vget_high_s16(tmpi), vget_high_s16(inre1));
#endif

    int32x4_t outr_0 = vqdmulhq_s32(tmp0, factq);
    int32x4_t outr_1 = vqdmulhq_s32(tmp2, factq);
    int32x4_t outi_0 = vqdmulhq_s32(tmp1, factq);
    int32x4_t outi_1 = vqdmulhq_s32(tmp3, factq);
    vst1q_s32(outreQ16, outr_0);
    outreQ16 += 4;
    vst1q_s32(outreQ16, outr_1);
    outreQ16 += 4;
    vst1q_s32(outimQ16, outi_0);
    outimQ16 += 4;
    vst1q_s32(outimQ16, outi_1);
    outimQ16 += 4;

    // Find the absolute maximum in the vectors.
    tmp0 = vabsq_s32(outr_0);
    tmp1 = vabsq_s32(outr_1);
    tmp2 = vabsq_s32(outi_0);
    tmp3 = vabsq_s32(outi_1);
    // vabs doesn't change the value of 0x80000000.
    // Use u32 so we don't lose the value 0x80000000.
    max_r = vmaxq_u32(max_r, vreinterpretq_u32_s32(tmp0));
    max_i = vmaxq_u32(max_i, vreinterpretq_u32_s32(tmp2));
    max_r = vmaxq_u32(max_r, vreinterpretq_u32_s32(tmp1));
    max_i = vmaxq_u32(max_i, vreinterpretq_u32_s32(tmp3));
  }

  max_r = vmaxq_u32(max_r, max_i);
#if defined(WEBRTC_ARCH_ARM64)
  uint32_t maximum = vmaxvq_u32(max_r);
#else
  uint32x2_t max32x2_r = vmax_u32(vget_low_u32(max_r), vget_high_u32(max_r));
  max32x2_r = vpmax_u32(max32x2_r, max32x2_r);
  uint32_t maximum = vget_lane_u32(max32x2_r, 0);
#endif

  return (int32_t)maximum;
}

static inline void PreShiftW32toW16Neon(int32_t* inre,
                                        int32_t* inim,
                                        int16_t* outre,
                                        int16_t* outim,
                                        int32_t sh) {
  int k;
  int32x4_t sh32x4 = vdupq_n_s32(sh);
  for (k = 0; k < FRAMESAMPLES/2; k += 16) {
    int32x4x4_t inre32x4x4 = vld4q_s32(inre);
    int32x4x4_t inim32x4x4 = vld4q_s32(inim);
    inre += 16;
    inim += 16;
    inre32x4x4.val[0] = vrshlq_s32(inre32x4x4.val[0], sh32x4);
    inre32x4x4.val[1] = vrshlq_s32(inre32x4x4.val[1], sh32x4);
    inre32x4x4.val[2] = vrshlq_s32(inre32x4x4.val[2], sh32x4);
    inre32x4x4.val[3] = vrshlq_s32(inre32x4x4.val[3], sh32x4);
    inim32x4x4.val[0] = vrshlq_s32(inim32x4x4.val[0], sh32x4);
    inim32x4x4.val[1] = vrshlq_s32(inim32x4x4.val[1], sh32x4);
    inim32x4x4.val[2] = vrshlq_s32(inim32x4x4.val[2], sh32x4);
    inim32x4x4.val[3] = vrshlq_s32(inim32x4x4.val[3], sh32x4);
    int16x4x4_t outre16x4x4;
    int16x4x4_t outim16x4x4;
    outre16x4x4.val[0]  = vmovn_s32(inre32x4x4.val[0]);
    outre16x4x4.val[1]  = vmovn_s32(inre32x4x4.val[1]);
    outre16x4x4.val[2]  = vmovn_s32(inre32x4x4.val[2]);
    outre16x4x4.val[3]  = vmovn_s32(inre32x4x4.val[3]);
    outim16x4x4.val[0]  = vmovn_s32(inim32x4x4.val[0]);
    outim16x4x4.val[1]  = vmovn_s32(inim32x4x4.val[1]);
    outim16x4x4.val[2]  = vmovn_s32(inim32x4x4.val[2]);
    outim16x4x4.val[3]  = vmovn_s32(inim32x4x4.val[3]);
    vst4_s16(outre, outre16x4x4);
    vst4_s16(outim, outim16x4x4);
    outre += 16;
    outim += 16;
  }
}

static inline void PostShiftAndSeparateNeon(int16_t* inre,
                                            int16_t* inim,
                                            int16_t* outre,
                                            int16_t* outim,
                                            int32_t sh) {
  int k;
  int16_t* inre1 = inre;
  int16_t* inre2 = &inre[FRAMESAMPLES/2 - 4];
  int16_t* inim1 = inim;
  int16_t* inim2 = &inim[FRAMESAMPLES/2 - 4];
  int16_t* outre1 = outre;
  int16_t* outre2 = &outre[FRAMESAMPLES/2 - 4];
  int16_t* outim1 = outim;
  int16_t* outim2 = &outim[FRAMESAMPLES/2 - 4];
  const int16_t* kSinTab1 = &WebRtcIsacfix_kSinTab2[0];
  const int16_t* kSinTab2 = &WebRtcIsacfix_kSinTab2[FRAMESAMPLES/4 -4];
  // By vshl, we effectively did "<< (-sh - 23)", instead of "<< (-sh)",
  // ">> 14" and then ">> 9" as in the C code.
  int32x4_t shift = vdupq_n_s32(-sh - 23);

  for (k = 0; k < FRAMESAMPLES/4; k += 4) {
    int16x4_t tmpi = vld1_s16(kSinTab1);
    kSinTab1 += 4;
    int16x4_t tmpr = vld1_s16(kSinTab2);
    kSinTab2 -= 4;
    int16x4_t inre_0 = vld1_s16(inre1);
    inre1 += 4;
    int16x4_t inre_1 = vld1_s16(inre2);
    inre2 -= 4;
    int16x4_t inim_0 = vld1_s16(inim1);
    inim1 += 4;
    int16x4_t inim_1 = vld1_s16(inim2);
    inim2 -= 4;
    tmpr = vneg_s16(tmpr);
    inre_1 = vrev64_s16(inre_1);
    inim_1 = vrev64_s16(inim_1);
    tmpr = vrev64_s16(tmpr);

    int16x4_t xr = vqadd_s16(inre_0, inre_1);
    int16x4_t xi = vqsub_s16(inim_0, inim_1);
    int16x4_t yr = vqadd_s16(inim_0, inim_1);
    int16x4_t yi = vqsub_s16(inre_1, inre_0);

    int32x4_t outr0 = vmull_s16(tmpr, xr);
    int32x4_t outi0 = vmull_s16(tmpi, xr);
    int32x4_t outr1 = vmull_s16(tmpi, yr);
    int32x4_t outi1 = vmull_s16(tmpi, yi);
    outr0 = vmlsl_s16(outr0, tmpi, xi);
    outi0 = vmlal_s16(outi0, tmpr, xi);
    outr1 = vmlal_s16(outr1, tmpr, yi);
    outi1 = vmlsl_s16(outi1, tmpr, yr);

    outr0 = vshlq_s32(outr0, shift);
    outi0 = vshlq_s32(outi0, shift);
    outr1 = vshlq_s32(outr1, shift);
    outi1 = vshlq_s32(outi1, shift);
    outr1 = vnegq_s32(outr1);

    int16x4_t outre_0  = vmovn_s32(outr0);
    int16x4_t outim_0  = vmovn_s32(outi0);
    int16x4_t outre_1  = vmovn_s32(outr1);
    int16x4_t outim_1  = vmovn_s32(outi1);
    outre_1 = vrev64_s16(outre_1);
    outim_1 = vrev64_s16(outim_1);

    vst1_s16(outre1, outre_0);
    outre1 += 4;
    vst1_s16(outim1, outim_0);
    outim1 += 4;
    vst1_s16(outre2, outre_1);
    outre2 -= 4;
    vst1_s16(outim2, outim_1);
    outim2 -= 4;
  }
}

void WebRtcIsacfix_Time2SpecNeon(int16_t* inre1Q9,
                                 int16_t* inre2Q9,
                                 int16_t* outreQ7,
                                 int16_t* outimQ7) {
  int32_t tmpreQ16[FRAMESAMPLES/2], tmpimQ16[FRAMESAMPLES/2];
  int32_t max;
  int32_t sh;

  // Multiply with complex exponentials and combine into one complex vector.
  // And find the maximum.
  max = ComplexMulAndFindMaxNeon(inre1Q9, inre2Q9, tmpreQ16, tmpimQ16);

  sh = (int32_t)WebRtcSpl_NormW32(max);
  sh = sh - 24;

  // If sh becomes >= 0, then we should shift sh steps to the left,
  // and the domain will become Q(16 + sh).
  // If sh becomes < 0, then we should shift -sh steps to the right,
  // and the domain will become Q(16 + sh).
  PreShiftW32toW16Neon(tmpreQ16, tmpimQ16, inre1Q9, inre2Q9, sh);

  // Get DFT.
  WebRtcIsacfix_FftRadix16Fastest(inre1Q9, inre2Q9, -1);

  // If sh >= 0, shift sh steps to the right,
  // If sh < 0, shift -sh steps to the left.
  // Use symmetry to separate into two complex vectors
  // and center frames in time around zero.
  PostShiftAndSeparateNeon(inre1Q9, inre2Q9, outreQ7, outimQ7, sh);
}

static inline int32_t TransformAndFindMaxNeon(int16_t* inre,
                                              int16_t* inim,
                                              int32_t* outre,
                                              int32_t* outim) {
  int k;
  int16_t* inre1 = inre;
  int16_t* inre2 = &inre[FRAMESAMPLES/2 - 4];
  int16_t* inim1 = inim;
  int16_t* inim2 = &inim[FRAMESAMPLES/2 - 4];
  int32_t* outre1 = outre;
  int32_t* outre2 = &outre[FRAMESAMPLES/2 - 4];
  int32_t* outim1 = outim;
  int32_t* outim2 = &outim[FRAMESAMPLES/2 - 4];
  const int16_t* kSinTab1 = &WebRtcIsacfix_kSinTab2[0];
  const int16_t* kSinTab2 = &WebRtcIsacfix_kSinTab2[FRAMESAMPLES/4 - 4];
  uint32x4_t max_r = vdupq_n_u32(0);
  uint32x4_t max_i = vdupq_n_u32(0);

  // Use ">> 5", instead of "<< 9" and then ">> 14" as in the C code.
  for (k = 0; k < FRAMESAMPLES/4; k += 4) {
    int16x4_t tmpi = vld1_s16(kSinTab1);
    kSinTab1 += 4;
    int16x4_t tmpr = vld1_s16(kSinTab2);
    kSinTab2 -= 4;
    int16x4_t inre_0 = vld1_s16(inre1);
    inre1 += 4;
    int16x4_t inre_1 = vld1_s16(inre2);
    inre2 -= 4;
    int16x4_t inim_0 = vld1_s16(inim1);
    inim1 += 4;
    int16x4_t inim_1 = vld1_s16(inim2);
    inim2 -= 4;
    tmpr = vneg_s16(tmpr);
    inre_1 = vrev64_s16(inre_1);
    inim_1 = vrev64_s16(inim_1);
    tmpr = vrev64_s16(tmpr);

    int32x4_t xr = vmull_s16(tmpr, inre_0);
    int32x4_t xi = vmull_s16(tmpr, inim_0);
    int32x4_t yr = vmull_s16(tmpr, inim_1);
    int32x4_t yi = vmull_s16(tmpi, inim_1);
    xr = vmlal_s16(xr, tmpi, inim_0);
    xi = vmlsl_s16(xi, tmpi, inre_0);
    yr = vmlal_s16(yr, tmpi, inre_1);
    yi = vmlsl_s16(yi, tmpr, inre_1);
    yr = vnegq_s32(yr);

    xr = vshrq_n_s32(xr, 5);
    xi = vshrq_n_s32(xi, 5);
    yr = vshrq_n_s32(yr, 5);
    yi = vshrq_n_s32(yi, 5);

    int32x4_t outr0 = vsubq_s32(xr, yi);
    int32x4_t outr1 = vaddq_s32(xr, yi);
    int32x4_t outi0 = vaddq_s32(xi, yr);
    int32x4_t outi1 = vsubq_s32(yr, xi);

    // Find the absolute maximum in the vectors.
    int32x4_t tmp0 = vabsq_s32(outr0);
    int32x4_t tmp1 = vabsq_s32(outr1);
    int32x4_t tmp2 = vabsq_s32(outi0);
    int32x4_t tmp3 = vabsq_s32(outi1);
    // vabs doesn't change the value of 0x80000000.
    // Use u32 so we don't lose the value 0x80000000.
    max_r = vmaxq_u32(max_r, vreinterpretq_u32_s32(tmp0));
    max_i = vmaxq_u32(max_i, vreinterpretq_u32_s32(tmp2));
    max_r = vmaxq_u32(max_r, vreinterpretq_u32_s32(tmp1));
    max_i = vmaxq_u32(max_i, vreinterpretq_u32_s32(tmp3));

    // Store the vectors.
    outr1 = vrev64q_s32(outr1);
    outi1 = vrev64q_s32(outi1);
    int32x4_t outr_1 = vcombine_s32(vget_high_s32(outr1), vget_low_s32(outr1));
    int32x4_t outi_1 = vcombine_s32(vget_high_s32(outi1), vget_low_s32(outi1));

    vst1q_s32(outre1, outr0);
    outre1 += 4;
    vst1q_s32(outim1, outi0);
    outim1 += 4;
    vst1q_s32(outre2, outr_1);
    outre2 -= 4;
    vst1q_s32(outim2, outi_1);
    outim2 -= 4;
  }

  max_r = vmaxq_u32(max_r, max_i);
#if defined(WEBRTC_ARCH_ARM64)
  uint32_t maximum = vmaxvq_u32(max_r);
#else
  uint32x2_t max32x2_r = vmax_u32(vget_low_u32(max_r), vget_high_u32(max_r));
  max32x2_r = vpmax_u32(max32x2_r, max32x2_r);
  uint32_t maximum = vget_lane_u32(max32x2_r, 0);
#endif

  return (int32_t)maximum;
}

static inline void PostShiftAndDivideAndDemodulateNeon(int16_t* inre,
                                                       int16_t* inim,
                                                       int32_t* outre1,
                                                       int32_t* outre2,
                                                       int32_t sh) {
  int k;
  int16_t* p_inre = inre;
  int16_t* p_inim = inim;
  int32_t* p_outre1 = outre1;
  int32_t* p_outre2 = outre2;
  const int16_t* kCosTab = &WebRtcIsacfix_kCosTab1[0];
  const int16_t* kSinTab = &WebRtcIsacfix_kSinTab1[0];
  int32x4_t shift = vdupq_n_s32(-sh - 16);
  // Divide through by the normalizing constant:
  // scale all values with 1/240, i.e. with 273 in Q16.
  // 273/65536 ~= 0.0041656
  // 1/240 ~= 0.0041666
  int16x8_t scale = vdupq_n_s16(273);
  // Sqrt(240) in Q11 is round(15.49193338482967 * 2048) = 31727.
  int factQ19 = 31727 << 16;
  int32x4_t fact = vdupq_n_s32(factQ19);

  for (k = 0; k < FRAMESAMPLES/2; k += 8) {
    int16x8_t inre16x8 = vld1q_s16(p_inre);
    int16x8_t inim16x8 = vld1q_s16(p_inim);
    p_inre += 8;
    p_inim += 8;
    int16x8_t tmpr = vld1q_s16(kCosTab);
    int16x8_t tmpi = vld1q_s16(kSinTab);
    kCosTab += 8;
    kSinTab += 8;
    // By vshl and vmull, we effectively did "<< (-sh - 16)",
    // instead of "<< (-sh)" and ">> 16" as in the C code.
    int32x4_t outre1_0 = vmull_s16(vget_low_s16(inre16x8), vget_low_s16(scale));
    int32x4_t outre2_0 = vmull_s16(vget_low_s16(inim16x8), vget_low_s16(scale));
#if defined(WEBRTC_ARCH_ARM64)
    int32x4_t outre1_1 = vmull_high_s16(inre16x8, scale);
    int32x4_t outre2_1 = vmull_high_s16(inim16x8, scale);
#else
    int32x4_t outre1_1 = vmull_s16(vget_high_s16(inre16x8),
                                   vget_high_s16(scale));
    int32x4_t outre2_1 = vmull_s16(vget_high_s16(inim16x8),
                                   vget_high_s16(scale));
#endif

    outre1_0 = vshlq_s32(outre1_0, shift);
    outre1_1 = vshlq_s32(outre1_1, shift);
    outre2_0 = vshlq_s32(outre2_0, shift);
    outre2_1 = vshlq_s32(outre2_1, shift);

    // Demodulate and separate.
    int32x4_t tmpr_0 = vmovl_s16(vget_low_s16(tmpr));
    int32x4_t tmpi_0 = vmovl_s16(vget_low_s16(tmpi));
#if defined(WEBRTC_ARCH_ARM64)
    int32x4_t tmpr_1 = vmovl_high_s16(tmpr);
    int32x4_t tmpi_1 = vmovl_high_s16(tmpi);
#else
    int32x4_t tmpr_1 = vmovl_s16(vget_high_s16(tmpr));
    int32x4_t tmpi_1 = vmovl_s16(vget_high_s16(tmpi));
#endif

    int64x2_t xr0 = vmull_s32(vget_low_s32(tmpr_0), vget_low_s32(outre1_0));
    int64x2_t xi0 = vmull_s32(vget_low_s32(tmpr_0), vget_low_s32(outre2_0));
    int64x2_t xr2 = vmull_s32(vget_low_s32(tmpr_1), vget_low_s32(outre1_1));
    int64x2_t xi2 = vmull_s32(vget_low_s32(tmpr_1), vget_low_s32(outre2_1));
    xr0 = vmlsl_s32(xr0, vget_low_s32(tmpi_0), vget_low_s32(outre2_0));
    xi0 = vmlal_s32(xi0, vget_low_s32(tmpi_0), vget_low_s32(outre1_0));
    xr2 = vmlsl_s32(xr2, vget_low_s32(tmpi_1), vget_low_s32(outre2_1));
    xi2 = vmlal_s32(xi2, vget_low_s32(tmpi_1), vget_low_s32(outre1_1));

#if defined(WEBRTC_ARCH_ARM64)
    int64x2_t xr1 = vmull_high_s32(tmpr_0, outre1_0);
    int64x2_t xi1 = vmull_high_s32(tmpr_0, outre2_0);
    int64x2_t xr3 = vmull_high_s32(tmpr_1, outre1_1);
    int64x2_t xi3 = vmull_high_s32(tmpr_1, outre2_1);
    xr1 = vmlsl_high_s32(xr1, tmpi_0, outre2_0);
    xi1 = vmlal_high_s32(xi1, tmpi_0, outre1_0);
    xr3 = vmlsl_high_s32(xr3, tmpi_1, outre2_1);
    xi3 = vmlal_high_s32(xi3, tmpi_1, outre1_1);
#else
    int64x2_t xr1 = vmull_s32(vget_high_s32(tmpr_0), vget_high_s32(outre1_0));
    int64x2_t xi1 = vmull_s32(vget_high_s32(tmpr_0), vget_high_s32(outre2_0));
    int64x2_t xr3 = vmull_s32(vget_high_s32(tmpr_1), vget_high_s32(outre1_1));
    int64x2_t xi3 = vmull_s32(vget_high_s32(tmpr_1), vget_high_s32(outre2_1));
    xr1 = vmlsl_s32(xr1, vget_high_s32(tmpi_0), vget_high_s32(outre2_0));
    xi1 = vmlal_s32(xi1, vget_high_s32(tmpi_0), vget_high_s32(outre1_0));
    xr3 = vmlsl_s32(xr3, vget_high_s32(tmpi_1), vget_high_s32(outre2_1));
    xi3 = vmlal_s32(xi3, vget_high_s32(tmpi_1), vget_high_s32(outre1_1));
#endif

    outre1_0 = vcombine_s32(vrshrn_n_s64(xr0, 10), vrshrn_n_s64(xr1, 10));
    outre2_0 = vcombine_s32(vrshrn_n_s64(xi0, 10), vrshrn_n_s64(xi1, 10));
    outre1_1 = vcombine_s32(vrshrn_n_s64(xr2, 10), vrshrn_n_s64(xr3, 10));
    outre2_1 = vcombine_s32(vrshrn_n_s64(xi2, 10), vrshrn_n_s64(xi3, 10));
    outre1_0 = vqdmulhq_s32(outre1_0, fact);
    outre2_0 = vqdmulhq_s32(outre2_0, fact);
    outre1_1 = vqdmulhq_s32(outre1_1, fact);
    outre2_1 = vqdmulhq_s32(outre2_1, fact);

    vst1q_s32(p_outre1, outre1_0);
    p_outre1 += 4;
    vst1q_s32(p_outre1, outre1_1);
    p_outre1 += 4;
    vst1q_s32(p_outre2, outre2_0);
    p_outre2 += 4;
    vst1q_s32(p_outre2, outre2_1);
    p_outre2 += 4;
  }
}

void WebRtcIsacfix_Spec2TimeNeon(int16_t* inreQ7,
                                 int16_t* inimQ7,
                                 int32_t* outre1Q16,
                                 int32_t* outre2Q16) {
  int32_t max;
  int32_t sh;

  max = TransformAndFindMaxNeon(inreQ7, inimQ7, outre1Q16, outre2Q16);


  sh = (int32_t)WebRtcSpl_NormW32(max);
  sh = sh - 24;
  // If sh becomes >= 0, then we should shift sh steps to the left,
  // and the domain will become Q(16 + sh).
  // If sh becomes < 0, then we should shift -sh steps to the right,
  // and the domain will become Q(16 + sh).

  // "Fastest" vectors.
  PreShiftW32toW16Neon(outre1Q16, outre2Q16, inreQ7, inimQ7, sh);

  // Get IDFT.
  WebRtcIsacfix_FftRadix16Fastest(inreQ7, inimQ7, 1);

  PostShiftAndDivideAndDemodulateNeon(inreQ7, inimQ7, outre1Q16, outre2Q16, sh);
}
