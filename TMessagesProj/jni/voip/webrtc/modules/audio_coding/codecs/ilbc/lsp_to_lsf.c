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

 WebRtcIlbcfix_Lsp2Lsf.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/lsp_to_lsf.h"

#include "modules/audio_coding/codecs/ilbc/constants.h"
#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 *  conversion from LSP coefficients to LSF coefficients
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_Lsp2Lsf(
    int16_t *lsp, /* (i) lsp vector -1...+1 in Q15 */
    int16_t *lsf, /* (o) Lsf vector 0...Pi in Q13
                           (ordered, so that lsf[i]<lsf[i+1]) */
    int16_t m  /* (i) Number of coefficients */
                           )
{
  int16_t i, k;
  int16_t diff; /* diff between table value and desired value (Q15) */
  int16_t freq; /* lsf/(2*pi) (Q16) */
  int16_t *lspPtr, *lsfPtr, *cosTblPtr;
  int16_t tmp;

  /* set the index to maximum index value in WebRtcIlbcfix_kCos */
  k = 63;

  /*
     Start with the highest LSP and then work the way down
     For each LSP the lsf is calculated by first order approximation
     of the acos(x) function
  */
  lspPtr = &lsp[9];
  lsfPtr = &lsf[9];
  cosTblPtr=(int16_t*)&WebRtcIlbcfix_kCos[k];
  for(i=m-1; i>=0; i--)
  {
    /*
       locate value in the table, which is just above lsp[i],
       basically an approximation to acos(x)
    */
    while( (((int32_t)(*cosTblPtr)-(*lspPtr)) < 0)&&(k>0) )
    {
      k-=1;
      cosTblPtr--;
    }

    /* Calculate diff, which is used in the linear approximation of acos(x) */
    diff = (*lspPtr)-(*cosTblPtr);

    /*
       The linear approximation of acos(lsp[i]) :
       acos(lsp[i])= k*512 + (WebRtcIlbcfix_kAcosDerivative[ind]*offset >> 11)
    */

    /* tmp (linear offset) in Q16 */
    tmp = (int16_t)((WebRtcIlbcfix_kAcosDerivative[k] * diff) >> 11);

    /* freq in Q16 */
    freq = (k << 9) + tmp;

    /* lsf = freq*2*pi */
    (*lsfPtr) = (int16_t)(((int32_t)freq*25736)>>15);

    lsfPtr--;
    lspPtr--;
  }

  return;
}
