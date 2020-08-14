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

 WebRtcIlbcfix_GainQuant.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_GAIN_QUANT_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_GAIN_QUANT_H_

#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 *  quantizer for the gain in the gain-shape coding of residual
 *---------------------------------------------------------------*/

int16_t
WebRtcIlbcfix_GainQuant(               /* (o) quantized gain value */
                        int16_t gain,  /* (i) gain value Q14 */
                        int16_t maxIn, /* (i) maximum of gain value Q14 */
                        int16_t stage, /* (i) The stage of the search */
                        int16_t* index /* (o) quantization index */
                        );

#endif
