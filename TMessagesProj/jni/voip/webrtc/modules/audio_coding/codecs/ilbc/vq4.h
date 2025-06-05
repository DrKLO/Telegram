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

 WebRtcIlbcfix_Vq4.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_VQ4_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_VQ4_H_

#include <stdint.h>

/*----------------------------------------------------------------*
 *  Vector quantization of order 4 (based on MSE)
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_Vq4(
    int16_t* Xq,    /* (o) the quantized vector (Q13) */
    int16_t* index, /* (o) the quantization index */
    int16_t* CB,    /* (i) the vector quantization codebook (Q13) */
    int16_t* X,     /* (i) the vector to quantize (Q13) */
    int16_t n_cb    /* (i) the number of vectors in the codebook */
);

#endif
