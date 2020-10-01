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

 WebRtcIlbcfix_GainDequant.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "modules/audio_coding/codecs/ilbc/constants.h"

/*----------------------------------------------------------------*
 *  decoder for quantized gains in the gain-shape coding of
 *  residual
 *---------------------------------------------------------------*/

int16_t WebRtcIlbcfix_GainDequant(
    /* (o) quantized gain value (Q14) */
    int16_t index, /* (i) quantization index */
    int16_t maxIn, /* (i) maximum of unquantized gain (Q14) */
    int16_t stage /* (i) The stage of the search */
                                                ){
  int16_t scale;
  const int16_t *gain;

  /* obtain correct scale factor */

  scale=WEBRTC_SPL_ABS_W16(maxIn);
  scale = WEBRTC_SPL_MAX(1638, scale);  /* if lower than 0.1, set it to 0.1 */

  /* select the quantization table and return the decoded value */
  gain = WebRtcIlbcfix_kGain[stage];

  return (int16_t)((scale * gain[index] + 8192) >> 14);
}
