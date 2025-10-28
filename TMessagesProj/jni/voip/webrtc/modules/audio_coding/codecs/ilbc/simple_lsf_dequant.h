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

 WebRtcIlbcfix_SimpleLsfDeQ.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_SIMPLE_LSF_DEQUANT_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_SIMPLE_LSF_DEQUANT_H_

#include <stdint.h>

/*----------------------------------------------------------------*
 *  obtain dequantized lsf coefficients from quantization index
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_SimpleLsfDeQ(
    int16_t* lsfdeq, /* (o) dequantized lsf coefficients */
    int16_t* index,  /* (i) quantization index */
    int16_t lpc_n    /* (i) number of LPCs */
);

#endif
