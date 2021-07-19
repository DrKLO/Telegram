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

 WebRtcIlbcfix_AbsQuantLoop.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/abs_quant_loop.h"

#include "modules/audio_coding/codecs/ilbc/constants.h"
#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "modules/audio_coding/codecs/ilbc/sort_sq.h"

void WebRtcIlbcfix_AbsQuantLoop(int16_t *syntOutIN, int16_t *in_weightedIN,
                                int16_t *weightDenumIN, size_t *quantLenIN,
                                int16_t *idxVecIN ) {
  size_t k1, k2;
  int16_t index;
  int32_t toQW32;
  int32_t toQ32;
  int16_t tmp16a;
  int16_t xq;

  int16_t *syntOut   = syntOutIN;
  int16_t *in_weighted  = in_weightedIN;
  int16_t *weightDenum  = weightDenumIN;
  size_t *quantLen  = quantLenIN;
  int16_t *idxVec   = idxVecIN;

  for(k1=0;k1<2;k1++) {
    for(k2=0;k2<quantLen[k1];k2++){

      /* Filter to get the predicted value */
      WebRtcSpl_FilterARFastQ12(
          syntOut, syntOut,
          weightDenum, LPC_FILTERORDER+1, 1);

      /* the quantizer */
      toQW32 = (int32_t)(*in_weighted) - (int32_t)(*syntOut);

      toQ32 = (((int32_t)toQW32)<<2);

      if (toQ32 > 32767) {
        toQ32 = (int32_t) 32767;
      } else if (toQ32 < -32768) {
        toQ32 = (int32_t) -32768;
      }

      /* Quantize the state */
      if (toQW32<(-7577)) {
        /* To prevent negative overflow */
        index=0;
      } else if (toQW32>8151) {
        /* To prevent positive overflow */
        index=7;
      } else {
        /* Find the best quantization index
           (state_sq3Tbl is in Q13 and toQ is in Q11)
        */
        WebRtcIlbcfix_SortSq(&xq, &index,
                             (int16_t)toQ32,
                             WebRtcIlbcfix_kStateSq3, 8);
      }

      /* Store selected index */
      (*idxVec++) = index;

      /* Compute decoded sample and update of the prediction filter */
      tmp16a = ((WebRtcIlbcfix_kStateSq3[index] + 2 ) >> 2);

      *syntOut     = (int16_t) (tmp16a + (int32_t)(*in_weighted) - toQW32);

      syntOut++; in_weighted++;
    }
    /* Update perceptual weighting filter at subframe border */
    weightDenum += 11;
  }
}
