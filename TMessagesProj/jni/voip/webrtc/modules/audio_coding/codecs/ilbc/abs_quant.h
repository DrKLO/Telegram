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

 WebRtcIlbcfix_AbsQuant.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_ABS_QUANT_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_ABS_QUANT_H_

#include <stddef.h>
#include <stdint.h>

#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 *  predictive noise shaping encoding of scaled start state
 *  (subrutine for WebRtcIlbcfix_StateSearch)
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_AbsQuant(
    IlbcEncoder* iLBCenc_inst,
    /* (i) Encoder instance */
    iLBC_bits* iLBC_encbits, /* (i/o) Encoded bits (outputs idxForMax
                                   and idxVec, uses state_first as
                                   input) */
    int16_t* in,             /* (i) vector to encode */
    int16_t* weightDenum     /* (i) denominator of synthesis filter */
    );

#endif
