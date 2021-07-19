/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/******************************************************************

 iLBC Speech Coder ANSI-C Source Code

 WebRtcIlbcfix_Encode.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_ENCODE_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_ENCODE_H_

#include <stddef.h>
#include <stdint.h>

#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 *  main encoder function
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_EncodeImpl(
    uint16_t* bytes,      /* (o) encoded data bits iLBC */
    const int16_t* block, /* (i) speech vector to encode */
    IlbcEncoder* iLBCenc_inst /* (i/o) the general encoder
                                           state */
    );

#endif
