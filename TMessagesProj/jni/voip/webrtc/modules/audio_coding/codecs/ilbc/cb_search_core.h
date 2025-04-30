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

 WebRtcIlbcfix_CbSearchCore.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_CB_SEARCH_CORE_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_CB_SEARCH_CORE_H_

#include <stddef.h>
#include <stdint.h>

void WebRtcIlbcfix_CbSearchCore(
    int32_t* cDot,               /* (i) Cross Correlation */
    size_t range,                /* (i) Search range */
    int16_t stage,               /* (i) Stage of this search */
    int16_t* inverseEnergy,      /* (i) Inversed energy */
    int16_t* inverseEnergyShift, /* (i) Shifts of inversed energy
                                          with the offset 2*16-29 */
    int32_t* Crit,               /* (o) The criteria */
    size_t* bestIndex,           /* (o) Index that corresponds to
                                           maximum criteria (in this
                                           vector) */
    int32_t* bestCrit,           /* (o) Value of critera for the
                                          chosen index */
    int16_t* bestCritSh);        /* (o) The domain of the chosen
                                          criteria */

#endif
