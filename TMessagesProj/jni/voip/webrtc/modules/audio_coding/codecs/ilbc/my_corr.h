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

 WebRtcIlbcfix_MyCorr.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_MY_CORR_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_MY_CORR_H_

#include <stddef.h>
#include <stdint.h>

/*----------------------------------------------------------------*
 * compute cross correlation between sequences
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_MyCorr(int32_t* corr, /* (o) correlation of seq1 and seq2 */
                          const int16_t* seq1, /* (i) first sequence */
                          size_t dim1,         /* (i) dimension first seq1 */
                          const int16_t* seq2, /* (i) second sequence */
                          size_t dim2          /* (i) dimension seq2 */
);

#endif
