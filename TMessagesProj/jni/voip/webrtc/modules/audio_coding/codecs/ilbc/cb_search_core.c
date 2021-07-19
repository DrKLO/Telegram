/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/******************************************************************

 iLBC Speech Coder ANSI-C Source Code

 WebRtcIlbcfix_CbSearchCore.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/cb_search_core.h"

#include "modules/audio_coding/codecs/ilbc/constants.h"
#include "modules/audio_coding/codecs/ilbc/defines.h"

void WebRtcIlbcfix_CbSearchCore(
    int32_t *cDot,    /* (i) Cross Correlation */
    size_t range,    /* (i) Search range */
    int16_t stage,    /* (i) Stage of this search */
    int16_t *inverseEnergy,  /* (i) Inversed energy */
    int16_t *inverseEnergyShift, /* (i) Shifts of inversed energy
                                           with the offset 2*16-29 */
    int32_t *Crit,    /* (o) The criteria */
    size_t *bestIndex,   /* (o) Index that corresponds to
                                                   maximum criteria (in this
                                                   vector) */
    int32_t *bestCrit,   /* (o) Value of critera for the
                                                   chosen index */
    int16_t *bestCritSh)   /* (o) The domain of the chosen
                                                   criteria */
{
  int32_t maxW32, tmp32;
  int16_t max, sh, tmp16;
  size_t i;
  int32_t *cDotPtr;
  int16_t cDotSqW16;
  int16_t *inverseEnergyPtr;
  int32_t *critPtr;
  int16_t *inverseEnergyShiftPtr;

  /* Don't allow negative values for stage 0 */
  if (stage==0) {
    cDotPtr=cDot;
    for (i=0;i<range;i++) {
      *cDotPtr=WEBRTC_SPL_MAX(0, (*cDotPtr));
      cDotPtr++;
    }
  }

  /* Normalize cDot to int16_t, calculate the square of cDot and store the upper int16_t */
  maxW32 = WebRtcSpl_MaxAbsValueW32(cDot, range);

  sh = (int16_t)WebRtcSpl_NormW32(maxW32);
  cDotPtr = cDot;
  inverseEnergyPtr = inverseEnergy;
  critPtr = Crit;
  inverseEnergyShiftPtr=inverseEnergyShift;
  max=WEBRTC_SPL_WORD16_MIN;

  for (i=0;i<range;i++) {
    /* Calculate cDot*cDot and put the result in a int16_t */
    tmp32 = *cDotPtr << sh;
    tmp16 = (int16_t)(tmp32 >> 16);
    cDotSqW16 = (int16_t)(((int32_t)(tmp16)*(tmp16))>>16);

    /* Calculate the criteria (cDot*cDot/energy) */
    *critPtr = cDotSqW16 * *inverseEnergyPtr;

    /* Extract the maximum shift value under the constraint
       that the criteria is not zero */
    if ((*critPtr)!=0) {
      max = WEBRTC_SPL_MAX((*inverseEnergyShiftPtr), max);
    }

    inverseEnergyPtr++;
    inverseEnergyShiftPtr++;
    critPtr++;
    cDotPtr++;
  }

  /* If no max shifts still at initialization value, set shift to zero */
  if (max==WEBRTC_SPL_WORD16_MIN) {
    max = 0;
  }

  /* Modify the criterias, so that all of them use the same Q domain */
  critPtr=Crit;
  inverseEnergyShiftPtr=inverseEnergyShift;
  for (i=0;i<range;i++) {
    /* Guarantee that the shift value is less than 16
       in order to simplify for DSP's (and guard against >31) */
    tmp16 = WEBRTC_SPL_MIN(16, max-(*inverseEnergyShiftPtr));

    (*critPtr)=WEBRTC_SPL_SHIFT_W32((*critPtr),-tmp16);
    critPtr++;
    inverseEnergyShiftPtr++;
  }

  /* Find the index of the best value */
  *bestIndex = WebRtcSpl_MaxIndexW32(Crit, range);
  *bestCrit = Crit[*bestIndex];

  /* Calculate total shifts of this criteria */
  *bestCritSh = 32 - 2*sh + max;

  return;
}
