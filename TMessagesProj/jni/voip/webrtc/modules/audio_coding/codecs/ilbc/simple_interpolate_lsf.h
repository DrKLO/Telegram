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

 WebRtcIlbcfix_SimpleInterpolateLsf.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_SIMPLE_INTERPOLATE_LSF_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_SIMPLE_INTERPOLATE_LSF_H_

#include <stdint.h>

#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 *  lsf interpolator (subrutine to LPCencode)
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_SimpleInterpolateLsf(
    int16_t* syntdenum,   /* (o) the synthesis filter denominator
                                     resulting from the quantized
                                     interpolated lsf Q12 */
    int16_t* weightdenum, /* (o) the weighting filter denominator
                                   resulting from the unquantized
                                   interpolated lsf Q12 */
    int16_t* lsf,         /* (i) the unquantized lsf coefficients Q13 */
    int16_t* lsfdeq,      /* (i) the dequantized lsf coefficients Q13 */
    int16_t* lsfold,      /* (i) the unquantized lsf coefficients of
                                               the previous signal frame Q13 */
    int16_t* lsfdeqold,   /* (i) the dequantized lsf coefficients of the
                                     previous signal frame Q13 */
    int16_t length,       /* (i) should equate FILTERORDER */
    IlbcEncoder* iLBCenc_inst
    /* (i/o) the encoder state structure */
);

#endif
