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

 WebRtcIlbcfix_Lsp2Lsf.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_LSP_TO_LSF_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_LSP_TO_LSF_H_

#include <stdint.h>

/*----------------------------------------------------------------*
 *  conversion from LSP coefficients to LSF coefficients
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_Lsp2Lsf(
    int16_t* lsp, /* (i) lsp vector -1...+1 in Q15 */
    int16_t* lsf, /* (o) Lsf vector 0...Pi in Q13
                           (ordered, so that lsf[i]<lsf[i+1]) */
    int16_t m     /* (i) Number of coefficients */
);

#endif
