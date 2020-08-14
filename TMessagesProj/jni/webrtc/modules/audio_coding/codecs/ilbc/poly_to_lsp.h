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

 WebRtcIlbcfix_Poly2Lsp.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_POLY_TO_LSP_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_POLY_TO_LSP_H_

#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 * conversion from lpc coefficients to lsp coefficients
 * function is only for 10:th order LPC
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_Poly2Lsp(
    int16_t* a,      /* (o) A coefficients in Q12 */
    int16_t* lsp,    /* (i) LSP coefficients in Q15 */
    int16_t* old_lsp /* (i) old LSP coefficients that are used if the new
                              coefficients turn out to be unstable */
    );

#endif
