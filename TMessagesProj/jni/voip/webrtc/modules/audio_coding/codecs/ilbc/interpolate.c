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

 WebRtcIlbcfix_Interpolate.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/interpolate.h"

#include "modules/audio_coding/codecs/ilbc/constants.h"
#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 *  interpolation between vectors
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_Interpolate(
    int16_t *out, /* (o) output vector */
    int16_t *in1, /* (i) first input vector */
    int16_t *in2, /* (i) second input vector */
    int16_t coef, /* (i) weight coefficient in Q14 */
    int16_t length)  /* (i) number of sample is vectors */
{
  int i;
  int16_t invcoef;

  /*
    Performs the operation out[i] = in[i]*coef + (1-coef)*in2[i] (with rounding)
  */

  invcoef = 16384 - coef; /* 16384 = 1.0 (Q14)*/
  for (i = 0; i < length; i++) {
    out[i] = (int16_t)((coef * in1[i] + invcoef * in2[i] + 8192) >> 14);
  }

  return;
}
