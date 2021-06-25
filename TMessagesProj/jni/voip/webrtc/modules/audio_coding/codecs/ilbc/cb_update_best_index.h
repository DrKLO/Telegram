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

 WebRtcIlbcfix_CbUpdateBestIndex.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_CB_UPDATE_BEST_INDEX_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_CB_UPDATE_BEST_INDEX_H_

#include <stddef.h>
#include <stdint.h>

void WebRtcIlbcfix_CbUpdateBestIndex(
    int32_t CritNew,        /* (i) New Potentially best Criteria */
    int16_t CritNewSh,      /* (i) Shift value of above Criteria */
    size_t IndexNew,        /* (i) Index of new Criteria */
    int32_t cDotNew,        /* (i) Cross dot of new index */
    int16_t invEnergyNew,   /* (i) Inversed energy new index */
    int16_t energyShiftNew, /* (i) Energy shifts of new index */
    int32_t* CritMax,       /* (i/o) Maximum Criteria (so far) */
    int16_t* shTotMax,      /* (i/o) Shifts of maximum criteria */
    size_t* bestIndex,      /* (i/o) Index that corresponds to
                                      maximum criteria */
    int16_t* bestGain);     /* (i/o) Gain in Q14 that corresponds
                                     to maximum criteria */

#endif
