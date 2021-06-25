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

 WebRtcIlbcfix_DecoderInterpolateLsp.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_DECODER_INTERPOLATE_LSF_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_DECODER_INTERPOLATE_LSF_H_

#include <stddef.h>
#include <stdint.h>

#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 *  obtain synthesis and weighting filters form lsf coefficients
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_DecoderInterpolateLsp(
    int16_t* syntdenum, /* (o) synthesis filter coefficients */
    int16_t* weightdenum, /* (o) weighting denumerator
                                   coefficients */
    int16_t* lsfdeq, /* (i) dequantized lsf coefficients */
    int16_t length,  /* (i) length of lsf coefficient vector */
    IlbcDecoder* iLBCdec_inst
    /* (i) the decoder state structure */
    );

#endif
