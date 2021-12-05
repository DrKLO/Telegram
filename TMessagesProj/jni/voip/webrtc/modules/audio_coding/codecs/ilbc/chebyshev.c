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

 WebRtcIlbcfix_Chebyshev.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/chebyshev.h"

#include "modules/audio_coding/codecs/ilbc/constants.h"
#include "modules/audio_coding/codecs/ilbc/defines.h"

/*------------------------------------------------------------------*
 *  Calculate the Chevyshev polynomial series
 *  F(w) = 2*exp(-j5w)*C(x)
 *   C(x) = (T_0(x) + f(1)T_1(x) + ... + f(4)T_1(x) + f(5)/2)
 *   T_i(x) is the i:th order Chebyshev polynomial
 *------------------------------------------------------------------*/

int16_t WebRtcIlbcfix_Chebyshev(
    /* (o) Result of C(x) */
    int16_t x,  /* (i) Value to the Chevyshev polynomial */
    int16_t *f  /* (i) The coefficients in the polynomial */
                                      ) {
  int16_t b1_high, b1_low; /* Use the high, low format to increase the accuracy */
  int32_t b2;
  int32_t tmp1W32;
  int32_t tmp2W32;
  int i;

  b2 = (int32_t)0x1000000; /* b2 = 1.0 (Q23) */
  /* Calculate b1 = 2*x + f[1] */
  tmp1W32 = (x << 10) + (f[1] << 14);

  for (i = 2; i < 5; i++) {
    tmp2W32 = tmp1W32;

    /* Split b1 (in tmp1W32) into a high and low part */
    b1_high = (int16_t)(tmp1W32 >> 16);
    b1_low = (int16_t)((tmp1W32 - ((int32_t)b1_high << 16)) >> 1);

    /* Calculate 2*x*b1-b2+f[i] */
    tmp1W32 = ((b1_high * x + ((b1_low * x) >> 15)) << 2) - b2 + (f[i] << 14);

    /* Update b2 for next round */
    b2 = tmp2W32;
  }

  /* Split b1 (in tmp1W32) into a high and low part */
  b1_high = (int16_t)(tmp1W32 >> 16);
  b1_low = (int16_t)((tmp1W32 - ((int32_t)b1_high << 16)) >> 1);

  /* tmp1W32 = x*b1 - b2 + f[i]/2 */
  tmp1W32 = ((b1_high * x) << 1) + (((b1_low * x) >> 15) << 1) -
      b2 + (f[i] << 13);

  /* Handle overflows and set to maximum or minimum int16_t instead */
  if (tmp1W32>((int32_t)33553408)) {
    return(WEBRTC_SPL_WORD16_MAX);
  } else if (tmp1W32<((int32_t)-33554432)) {
    return(WEBRTC_SPL_WORD16_MIN);
  } else {
    return (int16_t)(tmp1W32 >> 10);
  }
}
