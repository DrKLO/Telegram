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

 WebRtcIlbcfix_SimpleInterpolateLsf.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "modules/audio_coding/codecs/ilbc/lsf_interpolate_to_poly_enc.h"
#include "modules/audio_coding/codecs/ilbc/bw_expand.h"
#include "modules/audio_coding/codecs/ilbc/constants.h"

/*----------------------------------------------------------------*
 *  lsf interpolator (subrutine to LPCencode)
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_SimpleInterpolateLsf(
    int16_t *syntdenum, /* (o) the synthesis filter denominator
                                   resulting from the quantized
                                   interpolated lsf Q12 */
    int16_t *weightdenum, /* (o) the weighting filter denominator
                                   resulting from the unquantized
                                   interpolated lsf Q12 */
    int16_t *lsf,  /* (i) the unquantized lsf coefficients Q13 */
    int16_t *lsfdeq,  /* (i) the dequantized lsf coefficients Q13 */
    int16_t *lsfold,  /* (i) the unquantized lsf coefficients of
                                           the previous signal frame Q13 */
    int16_t *lsfdeqold, /* (i) the dequantized lsf coefficients of the
                                   previous signal frame Q13 */
    int16_t length,  /* (i) should equate FILTERORDER */
    IlbcEncoder *iLBCenc_inst
    /* (i/o) the encoder state structure */
                                        ) {
  size_t i;
  int pos, lp_length;

  int16_t *lsf2, *lsfdeq2;
  /* Stack based */
  int16_t lp[LPC_FILTERORDER + 1];

  lsf2 = lsf + length;
  lsfdeq2 = lsfdeq + length;
  lp_length = length + 1;

  if (iLBCenc_inst->mode==30) {
    /* subframe 1: Interpolation between old and first set of
       lsf coefficients */

    /* Calculate Analysis/Syntehsis filter from quantized LSF */
    WebRtcIlbcfix_LsfInterpolate2PloyEnc(lp, lsfdeqold, lsfdeq,
                                         WebRtcIlbcfix_kLsfWeight30ms[0],
                                         length);
    WEBRTC_SPL_MEMCPY_W16(syntdenum, lp, lp_length);

    /* Calculate Weighting filter from quantized LSF */
    WebRtcIlbcfix_LsfInterpolate2PloyEnc(lp, lsfold, lsf,
                                         WebRtcIlbcfix_kLsfWeight30ms[0],
                                         length);
    WebRtcIlbcfix_BwExpand(weightdenum, lp,
                           (int16_t*)WebRtcIlbcfix_kLpcChirpWeightDenum,
                           (int16_t)lp_length);

    /* subframe 2 to 6: Interpolation between first and second
       set of lsf coefficients */

    pos = lp_length;
    for (i = 1; i < iLBCenc_inst->nsub; i++) {

      /* Calculate Analysis/Syntehsis filter from quantized LSF */
      WebRtcIlbcfix_LsfInterpolate2PloyEnc(lp, lsfdeq, lsfdeq2,
                                           WebRtcIlbcfix_kLsfWeight30ms[i],
                                           length);
      WEBRTC_SPL_MEMCPY_W16(syntdenum + pos, lp, lp_length);

      /* Calculate Weighting filter from quantized LSF */
      WebRtcIlbcfix_LsfInterpolate2PloyEnc(lp, lsf, lsf2,
                                           WebRtcIlbcfix_kLsfWeight30ms[i],
                                           length);
      WebRtcIlbcfix_BwExpand(weightdenum + pos, lp,
                             (int16_t*)WebRtcIlbcfix_kLpcChirpWeightDenum,
                             (int16_t)lp_length);

      pos += lp_length;
    }

    /* update memory */

    WEBRTC_SPL_MEMCPY_W16(lsfold, lsf2, length);
    WEBRTC_SPL_MEMCPY_W16(lsfdeqold, lsfdeq2, length);

  } else { /* iLBCenc_inst->mode==20 */
    pos = 0;
    for (i = 0; i < iLBCenc_inst->nsub; i++) {

      /* Calculate Analysis/Syntehsis filter from quantized LSF */
      WebRtcIlbcfix_LsfInterpolate2PloyEnc(lp, lsfdeqold, lsfdeq,
                                           WebRtcIlbcfix_kLsfWeight20ms[i],
                                           length);
      WEBRTC_SPL_MEMCPY_W16(syntdenum + pos, lp, lp_length);

      /* Calculate Weighting filter from quantized LSF */
      WebRtcIlbcfix_LsfInterpolate2PloyEnc(lp, lsfold, lsf,
                                           WebRtcIlbcfix_kLsfWeight20ms[i],
                                           length);
      WebRtcIlbcfix_BwExpand(weightdenum+pos, lp,
                             (int16_t*)WebRtcIlbcfix_kLpcChirpWeightDenum,
                             (int16_t)lp_length);

      pos += lp_length;
    }

    /* update memory */

    WEBRTC_SPL_MEMCPY_W16(lsfold, lsf, length);
    WEBRTC_SPL_MEMCPY_W16(lsfdeqold, lsfdeq, length);

  }

  return;
}
