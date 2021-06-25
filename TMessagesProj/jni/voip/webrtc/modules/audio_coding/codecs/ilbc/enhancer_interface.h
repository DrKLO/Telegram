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

 WebRtcIlbcfix_EnhancerInterface.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_ENHANCER_INTERFACE_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_ENHANCER_INTERFACE_H_

#include <stddef.h>
#include <stdint.h>

#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 * interface for enhancer
 *---------------------------------------------------------------*/

size_t  // (o) Estimated lag in end of in[]
WebRtcIlbcfix_EnhancerInterface(int16_t* out,       // (o) enhanced signal
                                const int16_t* in,  // (i) unenhanced signal
                                IlbcDecoder* iLBCdec_inst);  // (i) buffers etc

#endif
