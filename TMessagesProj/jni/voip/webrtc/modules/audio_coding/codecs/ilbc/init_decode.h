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

 WebRtcIlbcfix_InitDecode.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_INIT_DECODE_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_INIT_DECODE_H_

#include <stdint.h>

#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 *  Initiation of decoder instance.
 *---------------------------------------------------------------*/

int WebRtcIlbcfix_InitDecode(/* (o) Number of decoded samples */
                             IlbcDecoder*
                                 iLBCdec_inst, /* (i/o) Decoder instance */
                             int16_t mode,     /* (i) frame size mode */
                             int use_enhancer  /* (i) 1 to use enhancer
                                                  0 to run without enhancer */
);

#endif
