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

 WebRtcIlbcfix_GainDequant.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_GAIN_DEQUANT_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_GAIN_DEQUANT_H_

#include <stdint.h>

/*----------------------------------------------------------------*
 *  decoder for quantized gains in the gain-shape coding of
 *  residual
 *---------------------------------------------------------------*/

int16_t WebRtcIlbcfix_GainDequant(
    /* (o) quantized gain value (Q14) */
    int16_t index, /* (i) quantization index */
    int16_t maxIn, /* (i) maximum of unquantized gain (Q14) */
    int16_t stage  /* (i) The stage of the search */
);

#endif
