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

 WebRtcIlbcfix_Poly2Lsf.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_POLY_TO_LSF_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_POLY_TO_LSF_H_

#include <stdint.h>

/*----------------------------------------------------------------*
 *  conversion from lpc coefficients to lsf coefficients
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_Poly2Lsf(int16_t* lsf, /* (o) lsf coefficients (Q13) */
                            int16_t* a    /* (i) A coefficients (Q12) */
                            );

#endif
