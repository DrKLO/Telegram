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

 WebRtcIlbcfix_Window32W32.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_WINDOW32_W32_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_WINDOW32_W32_H_

#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 *  window multiplication
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_Window32W32(int32_t* z, /* Output */
                               int32_t* x, /* Input (same domain as Output)*/
                               const int32_t* y, /* Q31 Window */
                               size_t N          /* length to process */
                               );

#endif
