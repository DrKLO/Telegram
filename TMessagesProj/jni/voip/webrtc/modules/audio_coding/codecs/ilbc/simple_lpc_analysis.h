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

 WebRtcIlbcfix_SimpleLpcAnalysis.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_SIMPLE_LPC_ANALYSIS_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_SIMPLE_LPC_ANALYSIS_H_

#include <stdint.h>

#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 *  lpc analysis (subrutine to LPCencode)
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_SimpleLpcAnalysis(
    int16_t* lsf,  /* (o) lsf coefficients */
    int16_t* data, /* (i) new block of speech */
    IlbcEncoder* iLBCenc_inst
    /* (i/o) the encoder state structure */
    );

#endif
