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

 WebRtcIlbcfix_FrameClassify.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "modules/audio_coding/codecs/ilbc/constants.h"

/*----------------------------------------------------------------*
 *  Classification of subframes to localize start state
 *---------------------------------------------------------------*/

size_t WebRtcIlbcfix_FrameClassify(
    /* (o) Index to the max-energy sub frame */
    IlbcEncoder *iLBCenc_inst,
    /* (i/o) the encoder state structure */
    int16_t *residualFIX /* (i) lpc residual signal */
                                                ){
  int16_t max, scale;
  int32_t ssqEn[NSUB_MAX-1];
  int16_t *ssqPtr;
  int32_t *seqEnPtr;
  int32_t maxW32;
  int16_t scale1;
  size_t pos;
  size_t n;

  /*
    Calculate the energy of each of the 80 sample blocks
    in the draft the 4 first and last samples are windowed with 1/5...4/5
    and 4/5...1/5 respectively. To simplify for the fixpoint we have changed
    this to 0 0 1 1 and 1 1 0 0
  */

  max = WebRtcSpl_MaxAbsValueW16(residualFIX, iLBCenc_inst->blockl);
  scale = WebRtcSpl_GetSizeInBits((uint32_t)(max * max));

  /* Scale to maximum 24 bits so that it won't overflow for 76 samples */
  scale = scale-24;
  scale1 = WEBRTC_SPL_MAX(0, scale);

  /* Calculate energies */
  ssqPtr=residualFIX + 2;
  seqEnPtr=ssqEn;
  for (n=(iLBCenc_inst->nsub-1); n>0; n--) {
    (*seqEnPtr) = WebRtcSpl_DotProductWithScale(ssqPtr, ssqPtr, 76, scale1);
    ssqPtr += 40;
    seqEnPtr++;
  }

  /* Scale to maximum 20 bits in order to allow for the 11 bit window */
  maxW32 = WebRtcSpl_MaxValueW32(ssqEn, iLBCenc_inst->nsub - 1);
  scale = WebRtcSpl_GetSizeInBits(maxW32) - 20;
  scale1 = WEBRTC_SPL_MAX(0, scale);

  /* Window each 80 block with the ssqEn_winTbl window to give higher probability for
     the blocks in the middle
  */
  seqEnPtr=ssqEn;
  if (iLBCenc_inst->mode==20) {
    ssqPtr=(int16_t*)WebRtcIlbcfix_kStartSequenceEnrgWin+1;
  } else {
    ssqPtr=(int16_t*)WebRtcIlbcfix_kStartSequenceEnrgWin;
  }
  for (n=(iLBCenc_inst->nsub-1); n>0; n--) {
    (*seqEnPtr)=WEBRTC_SPL_MUL(((*seqEnPtr)>>scale1), (*ssqPtr));
    seqEnPtr++;
    ssqPtr++;
  }

  /* Extract the best choise of start state */
  pos = WebRtcSpl_MaxIndexW32(ssqEn, iLBCenc_inst->nsub - 1) + 1;

  return(pos);
}
