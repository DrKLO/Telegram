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

 WebRtcIlbcfix_SimpleLsfDeQ.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "modules/audio_coding/codecs/ilbc/constants.h"

/*----------------------------------------------------------------*
 *  obtain dequantized lsf coefficients from quantization index
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_SimpleLsfDeQ(
    int16_t *lsfdeq,  /* (o) dequantized lsf coefficients */
    int16_t *index,  /* (i) quantization index */
    int16_t lpc_n  /* (i) number of LPCs */
                                ){
  int i, j, pos, cb_pos;

  /* decode first LSF */

  pos = 0;
  cb_pos = 0;
  for (i = 0; i < LSF_NSPLIT; i++) {
    for (j = 0; j < WebRtcIlbcfix_kLsfDimCb[i]; j++) {
      lsfdeq[pos + j] = WebRtcIlbcfix_kLsfCb[cb_pos + j + index[i] *
                                             WebRtcIlbcfix_kLsfDimCb[i]];
    }
    pos += WebRtcIlbcfix_kLsfDimCb[i];
    cb_pos += WebRtcIlbcfix_kLsfSizeCb[i] * WebRtcIlbcfix_kLsfDimCb[i];
  }

  if (lpc_n>1) {
    /* decode last LSF */
    pos = 0;
    cb_pos = 0;
    for (i = 0; i < LSF_NSPLIT; i++) {
      for (j = 0; j < WebRtcIlbcfix_kLsfDimCb[i]; j++) {
        lsfdeq[LPC_FILTERORDER + pos + j] = WebRtcIlbcfix_kLsfCb[
            cb_pos + index[LSF_NSPLIT + i] * WebRtcIlbcfix_kLsfDimCb[i] + j];
      }
      pos += WebRtcIlbcfix_kLsfDimCb[i];
      cb_pos += WebRtcIlbcfix_kLsfSizeCb[i] * WebRtcIlbcfix_kLsfDimCb[i];
    }
  }
  return;
}
