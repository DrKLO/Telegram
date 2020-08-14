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

 WebRtcIlbcfix_DecodeResidual.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_DECODE_RESIDUAL_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_DECODE_RESIDUAL_H_

#include <stdbool.h>
#include <stdint.h>

#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "rtc_base/system/unused.h"

/*----------------------------------------------------------------*
 *  frame residual decoder function (subrutine to iLBC_decode)
 *---------------------------------------------------------------*/

// Returns true on success, false on failure. In case of failure, the decoder
// state may be corrupted and needs resetting.
bool WebRtcIlbcfix_DecodeResidual(
    IlbcDecoder* iLBCdec_inst, /* (i/o) the decoder state structure */
    iLBC_bits* iLBC_encbits,   /* (i/o) Encoded bits, which are used
                                        for the decoding  */
    int16_t* decresidual,      /* (o) decoded residual frame */
    int16_t* syntdenum         /* (i) the decoded synthesis filter
                                                         coefficients */
    ) RTC_WARN_UNUSED_RESULT;

#endif
