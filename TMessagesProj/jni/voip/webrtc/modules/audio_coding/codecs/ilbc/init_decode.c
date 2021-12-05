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

	WebRtcIlbcfix_InitDecode.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/init_decode.h"

#include "modules/audio_coding/codecs/ilbc/constants.h"
#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 *  Initiation of decoder instance.
 *---------------------------------------------------------------*/

int WebRtcIlbcfix_InitDecode(  /* (o) Number of decoded samples */
    IlbcDecoder *iLBCdec_inst,  /* (i/o) Decoder instance */
    int16_t mode,  /* (i) frame size mode */
    int use_enhancer) {  /* (i) 1: use enhancer, 0: no enhancer */
  int i;

  iLBCdec_inst->mode = mode;

  /* Set all the variables that are dependent on the frame size mode */
  if (mode==30) {
    iLBCdec_inst->blockl = BLOCKL_30MS;
    iLBCdec_inst->nsub = NSUB_30MS;
    iLBCdec_inst->nasub = NASUB_30MS;
    iLBCdec_inst->lpc_n = LPC_N_30MS;
    iLBCdec_inst->no_of_bytes = NO_OF_BYTES_30MS;
    iLBCdec_inst->no_of_words = NO_OF_WORDS_30MS;
    iLBCdec_inst->state_short_len=STATE_SHORT_LEN_30MS;
  }
  else if (mode==20) {
    iLBCdec_inst->blockl = BLOCKL_20MS;
    iLBCdec_inst->nsub = NSUB_20MS;
    iLBCdec_inst->nasub = NASUB_20MS;
    iLBCdec_inst->lpc_n = LPC_N_20MS;
    iLBCdec_inst->no_of_bytes = NO_OF_BYTES_20MS;
    iLBCdec_inst->no_of_words = NO_OF_WORDS_20MS;
    iLBCdec_inst->state_short_len=STATE_SHORT_LEN_20MS;
  }
  else {
    return(-1);
  }

  /* Reset all the previous LSF to mean LSF */
  WEBRTC_SPL_MEMCPY_W16(iLBCdec_inst->lsfdeqold, WebRtcIlbcfix_kLsfMean, LPC_FILTERORDER);

  /* Clear the synthesis filter memory */
  WebRtcSpl_MemSetW16(iLBCdec_inst->syntMem, 0, LPC_FILTERORDER);

  /* Set the old synthesis filter to {1.0 0.0 ... 0.0} */
  WebRtcSpl_MemSetW16(iLBCdec_inst->old_syntdenum, 0, ((LPC_FILTERORDER + 1)*NSUB_MAX));
  for (i=0; i<NSUB_MAX; i++) {
    iLBCdec_inst->old_syntdenum[i*(LPC_FILTERORDER+1)] = 4096;
  }

  /* Clear the variables that are used for the PLC */
  iLBCdec_inst->last_lag = 20;
  iLBCdec_inst->consPLICount = 0;
  iLBCdec_inst->prevPLI = 0;
  iLBCdec_inst->perSquare = 0;
  iLBCdec_inst->prevLag = 120;
  iLBCdec_inst->prevLpc[0] = 4096;
  WebRtcSpl_MemSetW16(iLBCdec_inst->prevLpc+1, 0, LPC_FILTERORDER);
  WebRtcSpl_MemSetW16(iLBCdec_inst->prevResidual, 0, BLOCKL_MAX);

  /* Initialize the seed for the random number generator */
  iLBCdec_inst->seed = 777;

  /* Set the filter state of the HP filter to 0 */
  WebRtcSpl_MemSetW16(iLBCdec_inst->hpimemx, 0, 2);
  WebRtcSpl_MemSetW16(iLBCdec_inst->hpimemy, 0, 4);

  /* Set the variables that are used in the ehnahcer */
  iLBCdec_inst->use_enhancer = use_enhancer;
  WebRtcSpl_MemSetW16(iLBCdec_inst->enh_buf, 0, (ENH_BUFL+ENH_BUFL_FILTEROVERHEAD));
  for (i=0;i<ENH_NBLOCKS_TOT;i++) {
    iLBCdec_inst->enh_period[i]=160; /* Q(-4) */
  }

  iLBCdec_inst->prev_enh_pl = 0;

  return (int)(iLBCdec_inst->blockl);
}
