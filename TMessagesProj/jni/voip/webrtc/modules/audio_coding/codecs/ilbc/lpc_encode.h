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

 WebRtcIlbcfix_LpcEncode.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_LPC_ENCODE_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_LPC_ENCODE_H_

#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 *  lpc encoder
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_LpcEncode(
    int16_t* syntdenum,   /* (i/o) synthesis filter coefficients
                                   before/after encoding */
    int16_t* weightdenum, /* (i/o) weighting denumerator coefficients
                                   before/after encoding */
    int16_t* lsf_index,   /* (o) lsf quantization index */
    int16_t* data,        /* (i) Speech to do LPC analysis on */
    IlbcEncoder* iLBCenc_inst
    /* (i/o) the encoder state structure */
    );

#endif
