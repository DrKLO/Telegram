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

 WebRtcIlbcfix_CbUpdateBestIndex.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "modules/audio_coding/codecs/ilbc/cb_update_best_index.h"
#include "modules/audio_coding/codecs/ilbc/constants.h"

void WebRtcIlbcfix_CbUpdateBestIndex(
    int32_t CritNew,    /* (i) New Potentially best Criteria */
    int16_t CritNewSh,   /* (i) Shift value of above Criteria */
    size_t IndexNew,   /* (i) Index of new Criteria */
    int32_t cDotNew,    /* (i) Cross dot of new index */
    int16_t invEnergyNew,  /* (i) Inversed energy new index */
    int16_t energyShiftNew,  /* (i) Energy shifts of new index */
    int32_t *CritMax,   /* (i/o) Maximum Criteria (so far) */
    int16_t *shTotMax,   /* (i/o) Shifts of maximum criteria */
    size_t *bestIndex,   /* (i/o) Index that corresponds to
                                                   maximum criteria */
    int16_t *bestGain)   /* (i/o) Gain in Q14 that corresponds
                                                   to maximum criteria */
{
  int16_t shOld, shNew, tmp16;
  int16_t scaleTmp;
  int32_t gainW32;

  /* Normalize the new and old Criteria to the same domain */
  if (CritNewSh>(*shTotMax)) {
    shOld=WEBRTC_SPL_MIN(31,CritNewSh-(*shTotMax));
    shNew=0;
  } else {
    shOld=0;
    shNew=WEBRTC_SPL_MIN(31,(*shTotMax)-CritNewSh);
  }

  /* Compare the two criterias. If the new one is better,
     calculate the gain and store this index as the new best one
  */

  if ((CritNew >> shNew) > (*CritMax >> shOld)) {

    tmp16 = (int16_t)WebRtcSpl_NormW32(cDotNew);
    tmp16 = 16 - tmp16;

    /* Calculate the gain in Q14
       Compensate for inverseEnergyshift in Q29 and that the energy
       value was stored in a int16_t (shifted down 16 steps)
       => 29-14+16 = 31 */

    scaleTmp = -energyShiftNew-tmp16+31;
    scaleTmp = WEBRTC_SPL_MIN(31, scaleTmp);

    gainW32 = ((int16_t)WEBRTC_SPL_SHIFT_W32(cDotNew, -tmp16) * invEnergyNew) >>
        scaleTmp;

    /* Check if criteria satisfies Gain criteria (max 1.3)
       if it is larger set the gain to 1.3
       (slightly different from FLP version)
    */
    if (gainW32>21299) {
      *bestGain=21299;
    } else if (gainW32<-21299) {
      *bestGain=-21299;
    } else {
      *bestGain=(int16_t)gainW32;
    }

    *CritMax=CritNew;
    *shTotMax=CritNewSh;
    *bestIndex = IndexNew;
  }

  return;
}
