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

 WebRtcIlbcfix_SimpleLpcAnalysis.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/simple_lpc_analysis.h"

#include "modules/audio_coding/codecs/ilbc/bw_expand.h"
#include "modules/audio_coding/codecs/ilbc/constants.h"
#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "modules/audio_coding/codecs/ilbc/poly_to_lsf.h"
#include "modules/audio_coding/codecs/ilbc/window32_w32.h"

/*----------------------------------------------------------------*
 *  lpc analysis (subrutine to LPCencode)
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_SimpleLpcAnalysis(
    int16_t *lsf,   /* (o) lsf coefficients */
    int16_t *data,   /* (i) new block of speech */
    IlbcEncoder *iLBCenc_inst
    /* (i/o) the encoder state structure */
                                     ) {
  int k;
  int scale;
  size_t is;
  int16_t stability;
  /* Stack based */
  int16_t A[LPC_FILTERORDER + 1];
  int32_t R[LPC_FILTERORDER + 1];
  int16_t windowedData[BLOCKL_MAX];
  int16_t rc[LPC_FILTERORDER];

  is=LPC_LOOKBACK+BLOCKL_MAX-iLBCenc_inst->blockl;
  WEBRTC_SPL_MEMCPY_W16(iLBCenc_inst->lpc_buffer+is,data,iLBCenc_inst->blockl);

  /* No lookahead, last window is asymmetric */

  for (k = 0; k < iLBCenc_inst->lpc_n; k++) {

    is = LPC_LOOKBACK;

    if (k < (iLBCenc_inst->lpc_n - 1)) {

      /* Hanning table WebRtcIlbcfix_kLpcWin[] is in Q15-domain so the output is right-shifted 15 */
      WebRtcSpl_ElementwiseVectorMult(windowedData, iLBCenc_inst->lpc_buffer, WebRtcIlbcfix_kLpcWin, BLOCKL_MAX, 15);
    } else {

      /* Hanning table WebRtcIlbcfix_kLpcAsymWin[] is in Q15-domain so the output is right-shifted 15 */
      WebRtcSpl_ElementwiseVectorMult(windowedData, iLBCenc_inst->lpc_buffer+is, WebRtcIlbcfix_kLpcAsymWin, BLOCKL_MAX, 15);
    }

    /* Compute autocorrelation */
    WebRtcSpl_AutoCorrelation(windowedData, BLOCKL_MAX, LPC_FILTERORDER, R, &scale);

    /* Window autocorrelation vector */
    WebRtcIlbcfix_Window32W32(R, R, WebRtcIlbcfix_kLpcLagWin, LPC_FILTERORDER + 1 );

    /* Calculate the A coefficients from the Autocorrelation using Levinson Durbin algorithm */
    stability=WebRtcSpl_LevinsonDurbin(R, A, rc, LPC_FILTERORDER);

    /*
       Set the filter to {1.0, 0.0, 0.0,...} if filter from Levinson Durbin algorithm is unstable
       This should basically never happen...
    */
    if (stability!=1) {
      A[0]=4096;
      WebRtcSpl_MemSetW16(&A[1], 0, LPC_FILTERORDER);
    }

    /* Bandwidth expand the filter coefficients */
    WebRtcIlbcfix_BwExpand(A, A, (int16_t*)WebRtcIlbcfix_kLpcChirpSyntDenum, LPC_FILTERORDER+1);

    /* Convert from A to LSF representation */
    WebRtcIlbcfix_Poly2Lsf(lsf + k*LPC_FILTERORDER, A);
  }

  is=LPC_LOOKBACK+BLOCKL_MAX-iLBCenc_inst->blockl;
  WEBRTC_SPL_MEMCPY_W16(iLBCenc_inst->lpc_buffer,
                        iLBCenc_inst->lpc_buffer+LPC_LOOKBACK+BLOCKL_MAX-is, is);

  return;
}
