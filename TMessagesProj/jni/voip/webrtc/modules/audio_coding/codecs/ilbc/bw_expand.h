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

 WebRtcIlbcfix_BwExpand.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_BW_EXPAND_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_BW_EXPAND_H_

#include <stddef.h>
#include <stdint.h>

/*----------------------------------------------------------------*
 *  lpc bandwidth expansion
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_BwExpand(
    int16_t* out,  /* (o) the bandwidth expanded lpc coefficients */
    int16_t* in,   /* (i) the lpc coefficients before bandwidth
                                    expansion */
    int16_t* coef, /* (i) the bandwidth expansion factor Q15 */
    int16_t length /* (i) the length of lpc coefficient vectors */
    );

#endif
