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

 WebRtcIlbcfix_IndexConvDec.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/index_conv_dec.h"

#include "modules/audio_coding/codecs/ilbc/defines.h"

void WebRtcIlbcfix_IndexConvDec(
    int16_t *index   /* (i/o) Codebook indexes */
                                ){
  int k;

  for (k=4;k<6;k++) {
    /* Readjust the second and third codebook index for the first 40 sample
       so that they look the same as the first (in terms of lag)
    */
    if ((index[k]>=44)&&(index[k]<108)) {
      index[k]+=64;
    } else if ((index[k]>=108)&&(index[k]<128)) {
      index[k]+=128;
    } else {
      /* ERROR */
    }
  }
}
