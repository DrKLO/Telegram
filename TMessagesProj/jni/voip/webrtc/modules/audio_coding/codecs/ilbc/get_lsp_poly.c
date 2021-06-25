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

 WebRtcIlbcfix_GetLspPoly.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/get_lsp_poly.h"

#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 * Construct the polynomials F1(z) and F2(z) from the LSP
 * (Computations are done in Q24)
 *
 * The expansion is performed using the following recursion:
 *
 * f[0] = 1;
 * tmp = -2.0 * lsp[0];
 * f[1] = tmp;
 * for (i=2; i<=5; i++) {
 *    b = -2.0 * lsp[2*i-2];
 *    f[i] = tmp*f[i-1] + 2.0*f[i-2];
 *    for (j=i; j>=2; j--) {
 *       f[j] = f[j] + tmp*f[j-1] + f[j-2];
 *    }
 *    f[i] = f[i] + tmp;
 * }
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_GetLspPoly(
    int16_t *lsp, /* (i) LSP in Q15 */
    int32_t *f)  /* (o) polonymial in Q24 */
{
  int32_t tmpW32;
  int i, j;
  int16_t high, low;
  int16_t *lspPtr;
  int32_t *fPtr;

  lspPtr = lsp;
  fPtr = f;
  /* f[0] = 1.0 (Q24) */
  (*fPtr) = (int32_t)16777216;
  fPtr++;

  (*fPtr) = WEBRTC_SPL_MUL((*lspPtr), -1024);
  fPtr++;
  lspPtr+=2;

  for(i=2; i<=5; i++)
  {
    (*fPtr) = fPtr[-2];

    for(j=i; j>1; j--)
    {
      /* Compute f[j] = f[j] + tmp*f[j-1] + f[j-2]; */
      high = (int16_t)(fPtr[-1] >> 16);
      low = (int16_t)((fPtr[-1] & 0xffff) >> 1);

      tmpW32 = 4 * high * *lspPtr + 4 * ((low * *lspPtr) >> 15);

      (*fPtr) += fPtr[-2];
      (*fPtr) -= tmpW32;
      fPtr--;
    }
    *fPtr -= *lspPtr * (1 << 10);

    fPtr+=i;
    lspPtr+=2;
  }
  return;
}
