/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * WebRtcIsacfix_kTransform.c
 *
 * Transform functions
 *
 */

#include "modules/audio_coding/codecs/isac/fix/source/codec.h"
#include "modules/audio_coding/codecs/isac/fix/source/fft.h"
#include "modules/audio_coding/codecs/isac/fix/source/settings.h"
#include "modules/third_party/fft/fft.h"

/* Tables are defined in transform_tables.c file or ARM assembly files. */
/* Cosine table 1 in Q14 */
extern const int16_t WebRtcIsacfix_kCosTab1[FRAMESAMPLES/2];
/* Sine table 1 in Q14 */
extern const int16_t WebRtcIsacfix_kSinTab1[FRAMESAMPLES/2];
/* Sine table 2 in Q14 */
extern const int16_t WebRtcIsacfix_kSinTab2[FRAMESAMPLES/4];

void WebRtcIsacfix_Time2SpecC(int16_t *inre1Q9,
                              int16_t *inre2Q9,
                              int16_t *outreQ7,
                              int16_t *outimQ7)
{

  int k;
  int32_t tmpreQ16[FRAMESAMPLES/2], tmpimQ16[FRAMESAMPLES/2];
  int16_t tmp1rQ14, tmp1iQ14;
  int32_t xrQ16, xiQ16, yrQ16, yiQ16;
  int32_t v1Q16, v2Q16;
  int16_t factQ19, sh;

  /* Multiply with complex exponentials and combine into one complex vector */
  factQ19 = 16921; // 0.5/sqrt(240) in Q19 is round(.5/sqrt(240)*(2^19)) = 16921
  for (k = 0; k < FRAMESAMPLES/2; k++) {
    tmp1rQ14 = WebRtcIsacfix_kCosTab1[k];
    tmp1iQ14 = WebRtcIsacfix_kSinTab1[k];
    xrQ16 = (tmp1rQ14 * inre1Q9[k] + tmp1iQ14 * inre2Q9[k]) >> 7;
    xiQ16 = (tmp1rQ14 * inre2Q9[k] - tmp1iQ14 * inre1Q9[k]) >> 7;
    // Q-domains below: (Q16*Q19>>16)>>3 = Q16
    tmpreQ16[k] = (WEBRTC_SPL_MUL_16_32_RSFT16(factQ19, xrQ16) + 4) >> 3;
    tmpimQ16[k] = (WEBRTC_SPL_MUL_16_32_RSFT16(factQ19, xiQ16) + 4) >> 3;
  }


  xrQ16  = WebRtcSpl_MaxAbsValueW32(tmpreQ16, FRAMESAMPLES/2);
  yrQ16 = WebRtcSpl_MaxAbsValueW32(tmpimQ16, FRAMESAMPLES/2);
  if (yrQ16>xrQ16) {
    xrQ16 = yrQ16;
  }

  sh = WebRtcSpl_NormW32(xrQ16);
  sh = sh-24; //if sh becomes >=0, then we should shift sh steps to the left, and the domain will become Q(16+sh)
  //if sh becomes <0, then we should shift -sh steps to the right, and the domain will become Q(16+sh)

  //"Fastest" vectors
  if (sh>=0) {
    for (k=0; k<FRAMESAMPLES/2; k++) {
      inre1Q9[k] = (int16_t)(tmpreQ16[k] << sh);  // Q(16+sh)
      inre2Q9[k] = (int16_t)(tmpimQ16[k] << sh);  // Q(16+sh)
    }
  } else {
    int32_t round = 1 << (-sh - 1);
    for (k=0; k<FRAMESAMPLES/2; k++) {
      inre1Q9[k] = (int16_t)((tmpreQ16[k] + round) >> -sh);  // Q(16+sh)
      inre2Q9[k] = (int16_t)((tmpimQ16[k] + round) >> -sh);  // Q(16+sh)
    }
  }

  /* Get DFT */
  WebRtcIsacfix_FftRadix16Fastest(inre1Q9, inre2Q9, -1); // real call

  //"Fastest" vectors
  if (sh>=0) {
    for (k=0; k<FRAMESAMPLES/2; k++) {
      tmpreQ16[k] = inre1Q9[k] >> sh;  // Q(16+sh) -> Q16
      tmpimQ16[k] = inre2Q9[k] >> sh;  // Q(16+sh) -> Q16
    }
  } else {
    for (k=0; k<FRAMESAMPLES/2; k++) {
      tmpreQ16[k] = inre1Q9[k] << -sh;  // Q(16+sh) -> Q16
      tmpimQ16[k] = inre2Q9[k] << -sh;  // Q(16+sh) -> Q16
    }
  }


  /* Use symmetry to separate into two complex vectors and center frames in time around zero */
  for (k = 0; k < FRAMESAMPLES/4; k++) {
    xrQ16 = tmpreQ16[k] + tmpreQ16[FRAMESAMPLES/2 - 1 - k];
    yiQ16 = -tmpreQ16[k] + tmpreQ16[FRAMESAMPLES/2 - 1 - k];
    xiQ16 = tmpimQ16[k] - tmpimQ16[FRAMESAMPLES/2 - 1 - k];
    yrQ16 = tmpimQ16[k] + tmpimQ16[FRAMESAMPLES/2 - 1 - k];
    tmp1rQ14 = -WebRtcIsacfix_kSinTab2[FRAMESAMPLES/4 - 1 - k];
    tmp1iQ14 = WebRtcIsacfix_kSinTab2[k];
    v1Q16 = WEBRTC_SPL_MUL_16_32_RSFT14(tmp1rQ14, xrQ16) - WEBRTC_SPL_MUL_16_32_RSFT14(tmp1iQ14, xiQ16);
    v2Q16 = WEBRTC_SPL_MUL_16_32_RSFT14(tmp1iQ14, xrQ16) + WEBRTC_SPL_MUL_16_32_RSFT14(tmp1rQ14, xiQ16);
    outreQ7[k] = (int16_t)(v1Q16 >> 9);
    outimQ7[k] = (int16_t)(v2Q16 >> 9);
    v1Q16 = -WEBRTC_SPL_MUL_16_32_RSFT14(tmp1iQ14, yrQ16) - WEBRTC_SPL_MUL_16_32_RSFT14(tmp1rQ14, yiQ16);
    v2Q16 = -WEBRTC_SPL_MUL_16_32_RSFT14(tmp1rQ14, yrQ16) + WEBRTC_SPL_MUL_16_32_RSFT14(tmp1iQ14, yiQ16);
    // CalcLrIntQ(v1Q16, 9);
    outreQ7[FRAMESAMPLES / 2 - 1 - k] = (int16_t)(v1Q16 >> 9);
    // CalcLrIntQ(v2Q16, 9);
    outimQ7[FRAMESAMPLES / 2 - 1 - k] = (int16_t)(v2Q16 >> 9);

  }
}


void WebRtcIsacfix_Spec2TimeC(int16_t *inreQ7, int16_t *inimQ7, int32_t *outre1Q16, int32_t *outre2Q16)
{

  int k;
  int16_t tmp1rQ14, tmp1iQ14;
  int32_t xrQ16, xiQ16, yrQ16, yiQ16;
  int32_t tmpInRe, tmpInIm, tmpInRe2, tmpInIm2;
  int16_t factQ11;
  int16_t sh;

  for (k = 0; k < FRAMESAMPLES/4; k++) {
    /* Move zero in time to beginning of frames */
    tmp1rQ14 = -WebRtcIsacfix_kSinTab2[FRAMESAMPLES/4 - 1 - k];
    tmp1iQ14 = WebRtcIsacfix_kSinTab2[k];

    tmpInRe = inreQ7[k] * (1 << 9);  // Q7 -> Q16
    tmpInIm = inimQ7[k] * (1 << 9);  // Q7 -> Q16
    tmpInRe2 = inreQ7[FRAMESAMPLES / 2 - 1 - k] * (1 << 9);  // Q7 -> Q16
    tmpInIm2 = inimQ7[FRAMESAMPLES / 2 - 1 - k] * (1 << 9);  // Q7 -> Q16

    xrQ16 = WEBRTC_SPL_MUL_16_32_RSFT14(tmp1rQ14, tmpInRe) + WEBRTC_SPL_MUL_16_32_RSFT14(tmp1iQ14, tmpInIm);
    xiQ16 = WEBRTC_SPL_MUL_16_32_RSFT14(tmp1rQ14, tmpInIm) - WEBRTC_SPL_MUL_16_32_RSFT14(tmp1iQ14, tmpInRe);
    yrQ16 = -WEBRTC_SPL_MUL_16_32_RSFT14(tmp1rQ14, tmpInIm2) - WEBRTC_SPL_MUL_16_32_RSFT14(tmp1iQ14, tmpInRe2);
    yiQ16 = -WEBRTC_SPL_MUL_16_32_RSFT14(tmp1rQ14, tmpInRe2) + WEBRTC_SPL_MUL_16_32_RSFT14(tmp1iQ14, tmpInIm2);

    /* Combine into one vector,  z = x + j * y */
    outre1Q16[k] = xrQ16 - yiQ16;
    outre1Q16[FRAMESAMPLES/2 - 1 - k] = xrQ16 + yiQ16;
    outre2Q16[k] = xiQ16 + yrQ16;
    outre2Q16[FRAMESAMPLES/2 - 1 - k] = -xiQ16 + yrQ16;
  }

  /* Get IDFT */
  tmpInRe  = WebRtcSpl_MaxAbsValueW32(outre1Q16, 240);
  tmpInIm = WebRtcSpl_MaxAbsValueW32(outre2Q16, 240);
  if (tmpInIm>tmpInRe) {
    tmpInRe = tmpInIm;
  }

  sh = WebRtcSpl_NormW32(tmpInRe);
  sh = sh-24; //if sh becomes >=0, then we should shift sh steps to the left, and the domain will become Q(16+sh)
  //if sh becomes <0, then we should shift -sh steps to the right, and the domain will become Q(16+sh)

  //"Fastest" vectors
  if (sh>=0) {
    for (k=0; k<240; k++) {
      inreQ7[k] = (int16_t)(outre1Q16[k] << sh);  // Q(16+sh)
      inimQ7[k] = (int16_t)(outre2Q16[k] << sh);  // Q(16+sh)
    }
  } else {
    int32_t round = 1 << (-sh - 1);
    for (k=0; k<240; k++) {
      inreQ7[k] = (int16_t)((outre1Q16[k] + round) >> -sh);  // Q(16+sh)
      inimQ7[k] = (int16_t)((outre2Q16[k] + round) >> -sh);  // Q(16+sh)
    }
  }

  WebRtcIsacfix_FftRadix16Fastest(inreQ7, inimQ7, 1); // real call

  //"Fastest" vectors
  if (sh>=0) {
    for (k=0; k<240; k++) {
      outre1Q16[k] = inreQ7[k] >> sh;  // Q(16+sh) -> Q16
      outre2Q16[k] = inimQ7[k] >> sh;  // Q(16+sh) -> Q16
    }
  } else {
    for (k=0; k<240; k++) {
      outre1Q16[k] = inreQ7[k] * (1 << -sh);  // Q(16+sh) -> Q16
      outre2Q16[k] = inimQ7[k] * (1 << -sh);  // Q(16+sh) -> Q16
    }
  }

  /* Divide through by the normalizing constant: */
  /* scale all values with 1/240, i.e. with 273 in Q16 */
  /* 273/65536 ~= 0.0041656                            */
  /*     1/240 ~= 0.0041666                            */
  for (k=0; k<240; k++) {
    outre1Q16[k] = WEBRTC_SPL_MUL_16_32_RSFT16(273, outre1Q16[k]);
    outre2Q16[k] = WEBRTC_SPL_MUL_16_32_RSFT16(273, outre2Q16[k]);
  }

  /* Demodulate and separate */
  factQ11 = 31727; // sqrt(240) in Q11 is round(15.49193338482967*2048) = 31727
  for (k = 0; k < FRAMESAMPLES/2; k++) {
    tmp1rQ14 = WebRtcIsacfix_kCosTab1[k];
    tmp1iQ14 = WebRtcIsacfix_kSinTab1[k];
    xrQ16 = WEBRTC_SPL_MUL_16_32_RSFT14(tmp1rQ14, outre1Q16[k]) - WEBRTC_SPL_MUL_16_32_RSFT14(tmp1iQ14, outre2Q16[k]);
    xiQ16 = WEBRTC_SPL_MUL_16_32_RSFT14(tmp1rQ14, outre2Q16[k]) + WEBRTC_SPL_MUL_16_32_RSFT14(tmp1iQ14, outre1Q16[k]);
    xrQ16 = WEBRTC_SPL_MUL_16_32_RSFT11(factQ11, xrQ16);
    xiQ16 = WEBRTC_SPL_MUL_16_32_RSFT11(factQ11, xiQ16);
    outre2Q16[k] = xiQ16;
    outre1Q16[k] = xrQ16;
  }
}
