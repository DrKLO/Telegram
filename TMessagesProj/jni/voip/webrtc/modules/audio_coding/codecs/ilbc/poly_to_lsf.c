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

 WebRtcIlbcfix_Poly2Lsf.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "modules/audio_coding/codecs/ilbc/constants.h"
#include "modules/audio_coding/codecs/ilbc/poly_to_lsp.h"
#include "modules/audio_coding/codecs/ilbc/lsp_to_lsf.h"

void WebRtcIlbcfix_Poly2Lsf(
    int16_t *lsf,   /* (o) lsf coefficients (Q13) */
    int16_t *a    /* (i) A coefficients (Q12) */
                            ) {
  int16_t lsp[10];
  WebRtcIlbcfix_Poly2Lsp(a, lsp, (int16_t*)WebRtcIlbcfix_kLspMean);
  WebRtcIlbcfix_Lsp2Lsf(lsp, lsf, 10);
}
