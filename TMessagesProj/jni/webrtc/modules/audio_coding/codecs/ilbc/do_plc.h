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

 WebRtcIlbcfix_DoThePlc.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_DO_PLC_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_DO_PLC_H_

#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 *  Packet loss concealment routine. Conceals a residual signal
 *  and LP parameters. If no packet loss, update state.
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_DoThePlc(
    int16_t* PLCresidual, /* (o) concealed residual */
    int16_t* PLClpc,      /* (o) concealed LP parameters */
    int16_t PLI,          /* (i) packet loss indicator
                                                                0 - no PL, 1 = PL */
    int16_t* decresidual, /* (i) decoded residual */
    int16_t* lpc,         /* (i) decoded LPC (only used for no PL) */
    size_t inlag,         /* (i) pitch lag */
    IlbcDecoder* iLBCdec_inst
    /* (i/o) decoder instance */
    );

#endif
