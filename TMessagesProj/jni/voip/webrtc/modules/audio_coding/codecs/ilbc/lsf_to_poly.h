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

 WebRtcIlbcfix_Lsf2Poly.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_LSF_TO_POLY_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_LSF_TO_POLY_H_

#include <stdint.h>

/*----------------------------------------------------------------*
 *  Convert from LSF coefficients to A coefficients
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_Lsf2Poly(
    int16_t* a,  /* (o) predictor coefficients (order = 10) in Q12 */
    int16_t* lsf /* (i) line spectral frequencies in Q13 */
);

#endif
