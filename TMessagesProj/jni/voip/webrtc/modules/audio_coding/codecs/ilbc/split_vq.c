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

 WebRtcIlbcfix_SplitVq.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/split_vq.h"

#include "modules/audio_coding/codecs/ilbc/constants.h"
#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "modules/audio_coding/codecs/ilbc/vq3.h"
#include "modules/audio_coding/codecs/ilbc/vq4.h"

/*----------------------------------------------------------------*
 *  split vector quantization
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_SplitVq(
    int16_t *qX,  /* (o) the quantized vector in Q13 */
    int16_t *index, /* (o) a vector of indexes for all vector
                                   codebooks in the split */
    int16_t *X,  /* (i) the vector to quantize */
    int16_t *CB,  /* (i) the quantizer codebook in Q13 */
    int16_t *dim, /* (i) the dimension of X and qX */
    int16_t *cbsize /* (i) the number of vectors in the codebook */
                           ) {

  int16_t *qXPtr, *indexPtr, *CBPtr, *XPtr;

  /* Quantize X with the 3 vectror quantization tables */

  qXPtr=qX;
  indexPtr=index;
  CBPtr=CB;
  XPtr=X;
  WebRtcIlbcfix_Vq3(qXPtr, indexPtr, CBPtr, XPtr, cbsize[0]);

  qXPtr+=3;
  indexPtr+=1;
  CBPtr+=(dim[0]*cbsize[0]);
  XPtr+=3;
  WebRtcIlbcfix_Vq3(qXPtr, indexPtr, CBPtr, XPtr, cbsize[1]);

  qXPtr+=3;
  indexPtr+=1;
  CBPtr+=(dim[1]*cbsize[1]);
  XPtr+=3;
  WebRtcIlbcfix_Vq4(qXPtr, indexPtr, CBPtr, XPtr, cbsize[2]);

  return;
}
