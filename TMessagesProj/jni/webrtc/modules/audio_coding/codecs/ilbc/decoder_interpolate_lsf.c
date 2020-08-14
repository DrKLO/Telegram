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

 WebRtcIlbcfix_DecoderInterpolateLsp.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/lsf_interpolate_to_poly_dec.h"
#include "modules/audio_coding/codecs/ilbc/bw_expand.h"
#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "modules/audio_coding/codecs/ilbc/constants.h"

/*----------------------------------------------------------------*
 *  obtain synthesis and weighting filters form lsf coefficients
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_DecoderInterpolateLsp(
    int16_t *syntdenum,  /* (o) synthesis filter coefficients */
    int16_t *weightdenum, /* (o) weighting denumerator
                                   coefficients */
    int16_t *lsfdeq,   /* (i) dequantized lsf coefficients */
    int16_t length,   /* (i) length of lsf coefficient vector */
    IlbcDecoder *iLBCdec_inst
    /* (i) the decoder state structure */
                                          ){
  size_t i;
  int pos, lp_length;
  int16_t  lp[LPC_FILTERORDER + 1], *lsfdeq2;

  lsfdeq2 = lsfdeq + length;
  lp_length = length + 1;

  if (iLBCdec_inst->mode==30) {
    /* subframe 1: Interpolation between old and first LSF */

    WebRtcIlbcfix_LspInterpolate2PolyDec(lp, (*iLBCdec_inst).lsfdeqold, lsfdeq,
                                         WebRtcIlbcfix_kLsfWeight30ms[0], length);
    WEBRTC_SPL_MEMCPY_W16(syntdenum,lp,lp_length);
    WebRtcIlbcfix_BwExpand(weightdenum, lp, (int16_t*)WebRtcIlbcfix_kLpcChirpSyntDenum, (int16_t)lp_length);

    /* subframes 2 to 6: interpolation between first and last LSF */

    pos = lp_length;
    for (i = 1; i < 6; i++) {
      WebRtcIlbcfix_LspInterpolate2PolyDec(lp, lsfdeq, lsfdeq2,
                                           WebRtcIlbcfix_kLsfWeight30ms[i], length);
      WEBRTC_SPL_MEMCPY_W16(syntdenum + pos,lp,lp_length);
      WebRtcIlbcfix_BwExpand(weightdenum + pos, lp,
                             (int16_t*)WebRtcIlbcfix_kLpcChirpSyntDenum, (int16_t)lp_length);
      pos += lp_length;
    }
  } else { /* iLBCdec_inst->mode=20 */
    /* subframes 1 to 4: interpolation between old and new LSF */
    pos = 0;
    for (i = 0; i < iLBCdec_inst->nsub; i++) {
      WebRtcIlbcfix_LspInterpolate2PolyDec(lp, iLBCdec_inst->lsfdeqold, lsfdeq,
                                           WebRtcIlbcfix_kLsfWeight20ms[i], length);
      WEBRTC_SPL_MEMCPY_W16(syntdenum+pos,lp,lp_length);
      WebRtcIlbcfix_BwExpand(weightdenum+pos, lp,
                             (int16_t*)WebRtcIlbcfix_kLpcChirpSyntDenum, (int16_t)lp_length);
      pos += lp_length;
    }
  }

  /* update memory */

  if (iLBCdec_inst->mode==30) {
    WEBRTC_SPL_MEMCPY_W16(iLBCdec_inst->lsfdeqold, lsfdeq2, length);
  } else {
    WEBRTC_SPL_MEMCPY_W16(iLBCdec_inst->lsfdeqold, lsfdeq, length);
  }
}
