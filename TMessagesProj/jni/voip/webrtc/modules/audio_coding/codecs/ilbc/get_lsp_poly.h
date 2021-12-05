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

 WebRtcIlbcfix_GetLspPoly.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_GET_LSP_POLY_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_GET_LSP_POLY_H_

#include <stdint.h>

/*----------------------------------------------------------------*
 * Construct the polynomials F1(z) and F2(z) from the LSP
 * (Computations are done in Q24)
 *
 * The expansion is performed using the following recursion:
 *
 * f[0] = 1;
 * tmp = -2.0 * lsp[0];
 * f[1] = tmp;
 * for (i=2; i<=5; i++) {
 *    b = -2.0 * lsp[2*i-2];
 *    f[i] = tmp*f[i-1] + 2.0*f[i-2];
 *    for (j=i; j>=2; j--) {
 *       f[j] = f[j] + tmp*f[j-1] + f[j-2];
 *    }
 *    f[i] = f[i] + tmp;
 * }
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_GetLspPoly(int16_t* lsp, /* (i) LSP in Q15 */
                              int32_t* f);  /* (o) polonymial in Q24 */

#endif
