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

 WebRtcIlbcfix_InterpolateSamples.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_INTERPOLATE_SAMPLES_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_INTERPOLATE_SAMPLES_H_

#include <stddef.h>
#include <stdint.h>

/*----------------------------------------------------------------*
 *  Construct the interpolated samples for the Augmented CB
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_InterpolateSamples(
    int16_t* interpSamples, /* (o) The interpolated samples */
    int16_t* CBmem,         /* (i) The CB memory */
    size_t lMem             /* (i) Length of the CB memory */
);

#endif
