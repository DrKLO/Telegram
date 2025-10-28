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

 WebRtcIlbcfix_StateSearch.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_STATE_SEARCH_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_STATE_SEARCH_H_

#include <stddef.h>
#include <stdint.h>

#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 *  encoding of start state
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_StateSearch(
    IlbcEncoder* iLBCenc_inst,
    /* (i) Encoder instance */
    iLBC_bits* iLBC_encbits, /* (i/o) Encoded bits (output idxForMax
                                and idxVec, input state_first) */
    int16_t* residual,       /* (i) target residual vector */
    int16_t* syntDenum,      /* (i) lpc synthesis filter */
    int16_t* weightDenum     /* (i) weighting filter denuminator */
);

#endif
