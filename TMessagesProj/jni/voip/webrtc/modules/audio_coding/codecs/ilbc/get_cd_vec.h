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

 WebRtcIlbcfix_GetCbVec.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_GET_CD_VEC_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_GET_CD_VEC_H_

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "rtc_base/system/unused.h"

// Returns true on success, false on failure. In case of failure, the decoder
// state may be corrupted and needs resetting.
bool WebRtcIlbcfix_GetCbVec(
    int16_t* cbvec, /* (o) Constructed codebook vector */
    int16_t* mem,   /* (i) Codebook buffer */
    size_t index,   /* (i) Codebook index */
    size_t lMem,    /* (i) Length of codebook buffer */
    size_t cbveclen /* (i) Codebook vector length */
    ) RTC_WARN_UNUSED_RESULT;

#endif
