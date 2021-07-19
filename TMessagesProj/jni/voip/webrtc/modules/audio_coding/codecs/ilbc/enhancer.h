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

 WebRtcIlbcfix_Enhancer.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_ENHANCER_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_ENHANCER_H_

#include <stddef.h>
#include <stdint.h>

/*----------------------------------------------------------------*
 * perform enhancement on idata+centerStartPos through
 * idata+centerStartPos+ENH_BLOCKL-1
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_Enhancer(
    int16_t* odata,        /* (o) smoothed block, dimension blockl */
    int16_t* idata,        /* (i) data buffer used for enhancing */
    size_t idatal,         /* (i) dimension idata */
    size_t centerStartPos, /* (i) first sample current block within idata */
    size_t* period,        /* (i) pitch period array (pitch bward-in time) */
    const size_t* plocs,   /* (i) locations where period array values valid */
    size_t periodl         /* (i) dimension of period and plocs */
    );

#endif
