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

 WebRtcIlbcfix_Chebyshev.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_CHEBYSHEV_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_CHEBYSHEV_H_

#include <stddef.h>
#include <stdint.h>

/*------------------------------------------------------------------*
 *  Calculate the Chevyshev polynomial series
 *  F(w) = 2*exp(-j5w)*C(x)
 *   C(x) = (T_0(x) + f(1)T_1(x) + ... + f(4)T_1(x) + f(5)/2)
 *   T_i(x) is the i:th order Chebyshev polynomial
 *------------------------------------------------------------------*/

int16_t WebRtcIlbcfix_Chebyshev(
    /* (o) Result of C(x) */
    int16_t x, /* (i) Value to the Chevyshev polynomial */
    int16_t* f /* (i) The coefficients in the polynomial */
    );

#endif
