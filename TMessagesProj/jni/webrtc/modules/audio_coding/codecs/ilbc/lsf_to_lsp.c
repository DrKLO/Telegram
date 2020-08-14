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

 WebRtcIlbcfix_Lsf2Lsp.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "modules/audio_coding/codecs/ilbc/constants.h"

/*----------------------------------------------------------------*
 *  conversion from lsf to lsp coefficients
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_Lsf2Lsp(
    int16_t *lsf, /* (i) lsf in Q13 values between 0 and pi */
    int16_t *lsp, /* (o) lsp in Q15 values between -1 and 1 */
    int16_t m  /* (i) number of coefficients */
                           ) {
  int16_t i, k;
  int16_t diff; /* difference, which is used for the
                           linear approximation (Q8) */
  int16_t freq; /* normalized frequency in Q15 (0..1) */
  int32_t tmpW32;

  for(i=0; i<m; i++)
  {
    freq = (int16_t)((lsf[i] * 20861) >> 15);
    /* 20861: 1.0/(2.0*PI) in Q17 */
    /*
       Upper 8 bits give the index k and
       Lower 8 bits give the difference, which needs
       to be approximated linearly
    */
    k = freq >> 8;
    diff = (freq&0x00ff);

    /* Guard against getting outside table */

    if (k>63) {
      k = 63;
    }

    /* Calculate linear approximation */
    tmpW32 = WebRtcIlbcfix_kCosDerivative[k] * diff;
    lsp[i] = WebRtcIlbcfix_kCos[k] + (int16_t)(tmpW32 >> 12);
  }

  return;
}
