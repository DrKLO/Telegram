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

 WebRtcIlbcfix_Poly2Lsp.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "modules/audio_coding/codecs/ilbc/constants.h"
#include "modules/audio_coding/codecs/ilbc/chebyshev.h"

/*----------------------------------------------------------------*
 * conversion from lpc coefficients to lsp coefficients
 * function is only for 10:th order LPC
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_Poly2Lsp(
    int16_t *a,  /* (o) A coefficients in Q12 */
    int16_t *lsp, /* (i) LSP coefficients in Q15 */
    int16_t *old_lsp /* (i) old LSP coefficients that are used if the new
                              coefficients turn out to be unstable */
                            ) {
  int16_t f[2][6]; /* f[0][] represents f1 and f[1][] represents f2 */
  int16_t *a_i_ptr, *a_10mi_ptr;
  int16_t *f1ptr, *f2ptr;
  int32_t tmpW32;
  int16_t x, y, xlow, ylow, xmid, ymid, xhigh, yhigh, xint;
  int16_t shifts, sign;
  int i, j;
  int foundFreqs;
  int fi_select;

  /*
     Calculate the two polynomials f1(z) and f2(z)
     (the sum and the diff polynomial)
     f1[0] = f2[0] = 1.0;
     f1[i+1] = a[i+1] + a[10-i] - f1[i];
     f2[i+1] = a[i+1] - a[10-i] - f1[i];
  */

  a_i_ptr = a + 1;
  a_10mi_ptr = a + 10;
  f1ptr = f[0];
  f2ptr = f[1];
  (*f1ptr) = 1024; /* 1.0 in Q10 */
  (*f2ptr) = 1024; /* 1.0 in Q10 */
  for (i = 0; i < 5; i++) {
    *(f1ptr + 1) =
        (int16_t)((((int32_t)(*a_i_ptr) + *a_10mi_ptr) >> 2) - *f1ptr);
    *(f2ptr + 1) =
        (int16_t)((((int32_t)(*a_i_ptr) - *a_10mi_ptr) >> 2) + *f2ptr);
    a_i_ptr++;
    a_10mi_ptr--;
    f1ptr++;
    f2ptr++;
  }

  /*
    find the LSPs using the Chebychev pol. evaluation
  */

  fi_select = 0; /* selector between f1 and f2, start with f1 */

  foundFreqs = 0;

  xlow = WebRtcIlbcfix_kCosGrid[0];
  ylow = WebRtcIlbcfix_Chebyshev(xlow, f[fi_select]);

  /*
     Iterate until all the 10 LSP's have been found or
     all the grid points have been tried. If the 10 LSP's can
     not be found, set the LSP vector to previous LSP
  */

  for (j = 1; j < COS_GRID_POINTS && foundFreqs < 10; j++) {
    xhigh = xlow;
    yhigh = ylow;
    xlow = WebRtcIlbcfix_kCosGrid[j];
    ylow = WebRtcIlbcfix_Chebyshev(xlow, f[fi_select]);

    if (ylow * yhigh <= 0) {
      /* Run 4 times to reduce the interval */
      for (i = 0; i < 4; i++) {
        /* xmid =(xlow + xhigh)/2 */
        xmid = (xlow >> 1) + (xhigh >> 1);
        ymid = WebRtcIlbcfix_Chebyshev(xmid, f[fi_select]);

        if (ylow * ymid <= 0) {
          yhigh = ymid;
          xhigh = xmid;
        } else {
          ylow = ymid;
          xlow = xmid;
        }
      }

      /*
        Calculater xint by linear interpolation:
        xint = xlow - ylow*(xhigh-xlow)/(yhigh-ylow);
      */

      x = xhigh - xlow;
      y = yhigh - ylow;

      if (y == 0) {
        xint = xlow;
      } else {
        sign = y;
        y = WEBRTC_SPL_ABS_W16(y);
        shifts = (int16_t)WebRtcSpl_NormW32(y)-16;
        y <<= shifts;
        y = (int16_t)WebRtcSpl_DivW32W16(536838144, y); /* 1/(yhigh-ylow) */

        tmpW32 = (x * y) >> (19 - shifts);

        /* y=(xhigh-xlow)/(yhigh-ylow) */
        y = (int16_t)(tmpW32&0xFFFF);

        if (sign < 0) {
          y = -y;
        }
        /* tmpW32 = ylow*(xhigh-xlow)/(yhigh-ylow) */
        tmpW32 = (ylow * y) >> 10;
        xint = xlow-(int16_t)(tmpW32&0xFFFF);
      }

      /* Store the calculated lsp */
      lsp[foundFreqs] = (int16_t)xint;
      foundFreqs++;

      /* if needed, set xlow and ylow for next recursion */
      if (foundFreqs<10) {
        xlow = xint;
        /* Swap between f1 and f2 (f[0][] and f[1][]) */
        fi_select = ((fi_select+1)&0x1);

        ylow = WebRtcIlbcfix_Chebyshev(xlow, f[fi_select]);
      }
    }
  }

  /* Check if M roots found, if not then use the old LSP */
  if (foundFreqs < 10) {
    WEBRTC_SPL_MEMCPY_W16(lsp, old_lsp, 10);
  }
  return;
}
