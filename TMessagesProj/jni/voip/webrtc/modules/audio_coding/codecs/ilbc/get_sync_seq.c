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

 WebRtcIlbcfix_GetSyncSeq.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "modules/audio_coding/codecs/ilbc/constants.h"
#include "modules/audio_coding/codecs/ilbc/refiner.h"
#include "modules/audio_coding/codecs/ilbc/nearest_neighbor.h"

/*----------------------------------------------------------------*
 * get the pitch-synchronous sample sequence
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_GetSyncSeq(
    int16_t *idata,   /* (i) original data */
    size_t idatal,   /* (i) dimension of data */
    size_t centerStartPos, /* (i) where current block starts */
    size_t *period,   /* (i) rough-pitch-period array       (Q-2) */
    const size_t *plocs, /* (i) where periods of period array are taken (Q-2) */
    size_t periodl,   /* (i) dimension period array */
    size_t hl,    /* (i) 2*hl+1 is the number of sequences */
    int16_t *surround  /* (i/o) The contribution from this sequence
                                summed with earlier contributions */
                              ){
  size_t i, centerEndPos, q;
  /* Stack based */
  size_t lagBlock[2 * ENH_HL + 1];
  size_t blockStartPos[2 * ENH_HL + 1]; /* The position to search around (Q2) */
  size_t plocs2[ENH_PLOCSL];

  centerEndPos = centerStartPos + ENH_BLOCKL - 1;

  /* present (find predicted lag from this position) */

  WebRtcIlbcfix_NearestNeighbor(lagBlock + hl,
                                plocs,
                                2 * (centerStartPos + centerEndPos),
                                periodl);

  blockStartPos[hl] = 4 * centerStartPos;

  /* past (find predicted position and perform a refined
     search to find the best sequence) */

  for (q = hl; q > 0; q--) {
    size_t qq = q - 1;
    size_t period_q = period[lagBlock[q]];
    /* Stop if this sequence would be outside the buffer; that means all
       further-past sequences would also be outside the buffer. */
    if (blockStartPos[q] < period_q + (4 * ENH_OVERHANG))
      break;
    blockStartPos[qq] = blockStartPos[q] - period_q;

    size_t value = blockStartPos[qq] + 4 * ENH_BLOCKL_HALF;
    value = (value > period_q) ? (value - period_q) : 0;
    WebRtcIlbcfix_NearestNeighbor(lagBlock + qq, plocs, value, periodl);

    /* Find the best possible sequence in the 4 times upsampled
        domain around blockStartPos+q */
    WebRtcIlbcfix_Refiner(blockStartPos + qq, idata, idatal, centerStartPos,
                          blockStartPos[qq], surround,
                          WebRtcIlbcfix_kEnhWt[qq]);
  }

  /* future (find predicted position and perform a refined
     search to find the best sequence) */

  for (i = 0; i < periodl; i++) {
    plocs2[i] = plocs[i] - period[i];
  }

  for (q = hl + 1; q <= (2 * hl); q++) {

    WebRtcIlbcfix_NearestNeighbor(
        lagBlock + q,
        plocs2,
        blockStartPos[q - 1] + 4 * ENH_BLOCKL_HALF,
        periodl);

    blockStartPos[q]=blockStartPos[q-1]+period[lagBlock[q]];

    if (blockStartPos[q] + 4 * (ENH_BLOCKL + ENH_OVERHANG) < 4 * idatal) {

      /* Find the best possible sequence in the 4 times upsampled
         domain around blockStartPos+q */
      WebRtcIlbcfix_Refiner(blockStartPos + q, idata, idatal, centerStartPos,
                            blockStartPos[q], surround,
                            WebRtcIlbcfix_kEnhWt[2 * hl - q]);

    } else {
      /* Don't add anything since this sequence would
         be outside the buffer */
    }
  }
}
