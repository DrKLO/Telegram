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

 WebRtcIlbcfix_Refiner.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "modules/audio_coding/codecs/ilbc/constants.h"
#include "modules/audio_coding/codecs/ilbc/enh_upsample.h"
#include "modules/audio_coding/codecs/ilbc/my_corr.h"

/*----------------------------------------------------------------*
 * find segment starting near idata+estSegPos that has highest
 * correlation with idata+centerStartPos through
 * idata+centerStartPos+ENH_BLOCKL-1 segment is found at a
 * resolution of ENH_UPSO times the original of the original
 * sampling rate
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_Refiner(
    size_t *updStartPos, /* (o) updated start point (Q-2) */
    int16_t *idata,   /* (i) original data buffer */
    size_t idatal,   /* (i) dimension of idata */
    size_t centerStartPos, /* (i) beginning center segment */
    size_t estSegPos,  /* (i) estimated beginning other segment (Q-2) */
    int16_t *surround,  /* (i/o) The contribution from this sequence
                                           summed with earlier contributions */
    int16_t gain    /* (i) Gain to use for this sequence */
                           ){
  size_t estSegPosRounded, searchSegStartPos, searchSegEndPos, corrdim;
  size_t tloc, tloc2, i;

  int32_t maxtemp, scalefact;
  int16_t *filtStatePtr, *polyPtr;
  /* Stack based */
  int16_t filt[7];
  int32_t corrVecUps[ENH_CORRDIM*ENH_UPS0];
  int32_t corrVecTemp[ENH_CORRDIM];
  int16_t vect[ENH_VECTL];
  int16_t corrVec[ENH_CORRDIM];

  /* defining array bounds */

  estSegPosRounded = (estSegPos - 2) >> 2;

  searchSegStartPos =
      (estSegPosRounded < ENH_SLOP) ? 0 : (estSegPosRounded - ENH_SLOP);

  searchSegEndPos = estSegPosRounded + ENH_SLOP;
  if ((searchSegEndPos + ENH_BLOCKL) >= idatal) {
    searchSegEndPos = idatal - ENH_BLOCKL - 1;
  }

  corrdim = searchSegEndPos + 1 - searchSegStartPos;

  /* compute upsampled correlation and find
     location of max */

  WebRtcIlbcfix_MyCorr(corrVecTemp, idata + searchSegStartPos,
                       corrdim + ENH_BLOCKL - 1, idata + centerStartPos,
                       ENH_BLOCKL);

  /* Calculate the rescaling factor for the correlation in order to
     put the correlation in a int16_t vector instead */
  maxtemp = WebRtcSpl_MaxAbsValueW32(corrVecTemp, corrdim);

  scalefact = WebRtcSpl_GetSizeInBits(maxtemp) - 15;

  if (scalefact > 0) {
    for (i = 0; i < corrdim; i++) {
      corrVec[i] = (int16_t)(corrVecTemp[i] >> scalefact);
    }
  } else {
    for (i = 0; i < corrdim; i++) {
      corrVec[i] = (int16_t)corrVecTemp[i];
    }
  }
  /* In order to guarantee that all values are initialized */
  for (i = corrdim; i < ENH_CORRDIM; i++) {
    corrVec[i] = 0;
  }

  /* Upsample the correlation */
  WebRtcIlbcfix_EnhUpsample(corrVecUps, corrVec);

  /* Find maximum */
  tloc = WebRtcSpl_MaxIndexW32(corrVecUps, ENH_UPS0 * corrdim);

  /* make vector can be upsampled without ever running outside
     bounds */
  *updStartPos = searchSegStartPos * 4 + tloc + 4;

  tloc2 = (tloc + 3) >> 2;

  /* initialize the vector to be filtered, stuff with zeros
     when data is outside idata buffer */
  if (ENH_FL0 > (searchSegStartPos + tloc2)) {
    const size_t st = ENH_FL0 - searchSegStartPos - tloc2;
    WebRtcSpl_MemSetW16(vect, 0, st);
    WEBRTC_SPL_MEMCPY_W16(&vect[st], idata, ENH_VECTL - st);
  } else {
    const size_t st = searchSegStartPos + tloc2 - ENH_FL0;
    if ((st + ENH_VECTL) > idatal) {
      const size_t en = st + ENH_VECTL - idatal;
      WEBRTC_SPL_MEMCPY_W16(vect, &idata[st], ENH_VECTL - en);
      WebRtcSpl_MemSetW16(&vect[ENH_VECTL - en], 0, en);
    } else {
      WEBRTC_SPL_MEMCPY_W16(vect, &idata[st], ENH_VECTL);
    }
  }

  /* compute the segment (this is actually a convolution) */
  filtStatePtr = filt + 6;
  polyPtr = (int16_t*)WebRtcIlbcfix_kEnhPolyPhaser[tloc2 * ENH_UPS0 - tloc];
  for (i = 0; i < 7; i++) {
    *filtStatePtr-- = *polyPtr++;
  }

  WebRtcSpl_FilterMAFastQ12(&vect[6], vect, filt, ENH_FLO_MULT2_PLUS1,
                            ENH_BLOCKL);

  /* Add the contribution from this vector (scaled with gain) to the total
     surround vector */
  WebRtcSpl_AddAffineVectorToVector(surround, vect, gain, 32768, 16,
                                    ENH_BLOCKL);

  return;
}
