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

 WebRtcIlbcfix_GetSyncSeq.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_GET_SYNC_SEQ_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_GET_SYNC_SEQ_H_

#include <stddef.h>
#include <stdint.h>

/*----------------------------------------------------------------*
 * get the pitch-synchronous sample sequence
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_GetSyncSeq(
    int16_t* idata,        /* (i) original data */
    size_t idatal,         /* (i) dimension of data */
    size_t centerStartPos, /* (i) where current block starts */
    size_t* period,        /* (i) rough-pitch-period array       (Q-2) */
    const size_t* plocs, /* (i) where periods of period array are taken (Q-2) */
    size_t periodl,      /* (i) dimension period array */
    size_t hl,           /* (i) 2*hl+1 is the number of sequences */
    int16_t* surround    /* (i/o) The contribution from this sequence
                                  summed with earlier contributions */
    );

#endif
