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

 WebRtcIlbcfix_InterpolateSamples.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "modules/audio_coding/codecs/ilbc/constants.h"

void WebRtcIlbcfix_InterpolateSamples(
    int16_t *interpSamples, /* (o) The interpolated samples */
    int16_t *CBmem,   /* (i) The CB memory */
    size_t lMem    /* (i) Length of the CB memory */
                                      ) {
  int16_t *ppi, *ppo, i, j, temp1, temp2;
  int16_t *tmpPtr;

  /* Calculate the 20 vectors of interpolated samples (4 samples each)
     that are used in the codebooks for lag 20 to 39 */
  tmpPtr = interpSamples;
  for (j=0; j<20; j++) {
    temp1 = 0;
    temp2 = 3;
    ppo = CBmem+lMem-4;
    ppi = CBmem+lMem-j-24;
    for (i=0; i<4; i++) {

      *tmpPtr++ = (int16_t)((WebRtcIlbcfix_kAlpha[temp2] * *ppo) >> 15) +
          (int16_t)((WebRtcIlbcfix_kAlpha[temp1] * *ppi) >> 15);

      ppo++;
      ppi++;
      temp1++;
      temp2--;
    }
  }

  return;
}
