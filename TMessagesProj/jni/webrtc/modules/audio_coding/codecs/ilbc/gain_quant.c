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

 WebRtcIlbcfix_GainQuant.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "modules/audio_coding/codecs/ilbc/constants.h"

/*----------------------------------------------------------------*
 *  quantizer for the gain in the gain-shape coding of residual
 *---------------------------------------------------------------*/

int16_t WebRtcIlbcfix_GainQuant( /* (o) quantized gain value */
    int16_t gain, /* (i) gain value Q14 */
    int16_t maxIn, /* (i) maximum of gain value Q14 */
    int16_t stage, /* (i) The stage of the search */
    int16_t *index /* (o) quantization index */
                                        ) {

  int16_t scale, cblen;
  int32_t gainW32, measure1, measure2;
  const int16_t *cbPtr, *cb;
  int loc, noMoves, noChecks, i;

  /* ensure a lower bound (0.1) on the scaling factor */

  scale = WEBRTC_SPL_MAX(1638, maxIn);

  /* select the quantization table and calculate
     the length of the table and the number of
     steps in the binary search that are needed */
  cb = WebRtcIlbcfix_kGain[stage];
  cblen = 32>>stage;
  noChecks = 4-stage;

  /* Multiply the gain with 2^14 to make the comparison
     easier and with higher precision */
  gainW32 = gain << 14;

  /* Do a binary search, starting in the middle of the CB
     loc - defines the current position in the table
     noMoves - defines the number of steps to move in the CB in order
     to get next CB location
  */

  loc = cblen>>1;
  noMoves = loc;
  cbPtr = cb + loc; /* Centre of CB */

  for (i=noChecks;i>0;i--) {
    noMoves>>=1;
    measure1 = scale * *cbPtr;

    /* Move up if gain is larger, otherwise move down in table */
    measure1 = measure1 - gainW32;

    if (0>measure1) {
      cbPtr+=noMoves;
      loc+=noMoves;
    } else {
      cbPtr-=noMoves;
      loc-=noMoves;
    }
  }

  /* Check which value is the closest one: loc-1, loc or loc+1 */

  measure1 = scale * *cbPtr;
  if (gainW32>measure1) {
    /* Check against value above loc */
    measure2 = scale * cbPtr[1];
    if ((measure2-gainW32)<(gainW32-measure1)) {
      loc+=1;
    }
  } else {
    /* Check against value below loc */
    measure2 = scale * cbPtr[-1];
    if ((gainW32-measure2)<=(measure1-gainW32)) {
      loc-=1;
    }
  }

  /* Guard against getting outside the table. The calculation above can give a location
     which is one above the maximum value (in very rare cases) */
  loc=WEBRTC_SPL_MIN(loc, (cblen-1));
  *index=loc;

  /* Calculate and return the quantized gain value (in Q14) */
  return (int16_t)((scale * cb[loc] + 8192) >> 14);
}
