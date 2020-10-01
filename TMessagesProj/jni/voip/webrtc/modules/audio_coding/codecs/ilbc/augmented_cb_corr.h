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

 WebRtcIlbcfix_AugmentedCbCorr.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_AUGMENTED_CB_CORR_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_AUGMENTED_CB_CORR_H_

#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 *  Calculate correlation between target and Augmented codebooks
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_AugmentedCbCorr(
    int16_t* target,        /* (i) Target vector */
    int16_t* buffer,        /* (i) Memory buffer */
    int16_t* interpSamples, /* (i) buffer with
                                           interpolated samples */
    int32_t* crossDot,      /* (o) The cross correlation between
                                               the target and the Augmented
                                               vector */
    size_t low,             /* (i) Lag to start from (typically
                                                            20) */
    size_t high,            /* (i) Lag to end at (typically 39 */
    int scale);             /* (i) Scale factor to use for the crossDot */

#endif
