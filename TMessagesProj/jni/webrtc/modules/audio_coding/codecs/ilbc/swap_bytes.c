/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/******************************************************************

 iLBC Speech Coder ANSI-C Source Code

 WebRtcIlbcfix_SwapBytes.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 * Swap bytes (to simplify operations on Little Endian machines)
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_SwapBytes(
    const uint16_t* input,   /* (i) the sequence to swap */
    size_t wordLength,      /* (i) number or uint16_t to swap */
    uint16_t* output         /* (o) the swapped sequence */
                              ) {
  size_t k;
  for (k = wordLength; k > 0; k--) {
    *output++ = (*input >> 8)|(*input << 8);
    input++;
  }
}
