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

 WebRtcIlbcfix_Smooth_odata.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_SMOOTH_OUT_DATA_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_SMOOTH_OUT_DATA_H_

#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 * help function to WebRtcIlbcfix_Smooth()
 *---------------------------------------------------------------*/

int32_t WebRtcIlbcfix_Smooth_odata(int16_t* odata,
                                   int16_t* psseq,
                                   int16_t* surround,
                                   int16_t C);

#endif
