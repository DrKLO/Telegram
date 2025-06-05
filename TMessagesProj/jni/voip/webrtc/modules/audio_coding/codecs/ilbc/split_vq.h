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

 WebRtcIlbcfix_SplitVq.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_SPLIT_VQ_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_SPLIT_VQ_H_

#include <stdint.h>

/*----------------------------------------------------------------*
 *  split vector quantization
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_SplitVq(
    int16_t* qX,    /* (o) the quantized vector in Q13 */
    int16_t* index, /* (o) a vector of indexes for all vector
                                   codebooks in the split */
    int16_t* X,     /* (i) the vector to quantize */
    int16_t* CB,    /* (i) the quantizer codebook in Q13 */
    int16_t* dim,   /* (i) the dimension of X and qX */
    int16_t* cbsize /* (i) the number of vectors in the codebook */
);

#endif
