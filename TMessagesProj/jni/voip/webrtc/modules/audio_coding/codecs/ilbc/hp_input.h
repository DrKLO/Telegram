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

 WebRtcIlbcfix_HpInput.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_HP_INPUT_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_HP_INPUT_H_

#include "modules/audio_coding/codecs/ilbc/defines.h"

// clang-format off
// Bad job here. https://bugs.llvm.org/show_bug.cgi?id=34274
void WebRtcIlbcfix_HpInput(
    int16_t* signal, /* (i/o) signal vector */
    int16_t* ba,     /* (i)   B- and A-coefficients (2:nd order)
                              {b[0] b[1] b[2] -a[1] -a[2]}
                              a[0] is assumed to be 1.0 */
    int16_t* y,      /* (i/o) Filter state yhi[n-1] ylow[n-1]
                              yhi[n-2] ylow[n-2] */
    int16_t* x,      /* (i/o) Filter state x[n-1] x[n-2] */
    size_t len);     /* (i)   Number of samples to filter */
// clang-format on

#endif
