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

 WebRtcIlbcfix_SimpleLsfQ.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_SIMPLE_LSF_QUANT_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_SIMPLE_LSF_QUANT_H_

#include <stdint.h>

/*----------------------------------------------------------------*
 *  lsf quantizer (subrutine to LPCencode)
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_SimpleLsfQ(
    int16_t* lsfdeq, /* (o) dequantized lsf coefficients
                                   (dimension FILTERORDER) Q13 */
    int16_t* index,  /* (o) quantization index */
    int16_t* lsf,    /* (i) the lsf coefficient vector to be
                              quantized (dimension FILTERORDER) Q13 */
    int16_t lpc_n    /* (i) number of lsf sets to quantize */
    );

#endif
