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

 WebRtcIlbcfix_Smooth.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_SMOOTH_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_SMOOTH_H_

#include <stdint.h>

/*----------------------------------------------------------------*
 * find the smoothed output data
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_Smooth(int16_t* odata,   /* (o) smoothed output */
                          int16_t* current, /* (i) the un enhanced residual for
                                                     this block */
                          int16_t* surround /* (i) The approximation from the
                                                     surrounding sequences */
);

#endif
