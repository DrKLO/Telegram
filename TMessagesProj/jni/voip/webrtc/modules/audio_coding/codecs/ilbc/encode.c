/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/******************************************************************

 iLBC Speech Coder ANSI-C Source Code

 WebRtcIlbcfix_Encode.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/encode.h"

#include <string.h>

#include "modules/audio_coding/codecs/ilbc/cb_construct.h"
#include "modules/audio_coding/codecs/ilbc/cb_search.h"
#include "modules/audio_coding/codecs/ilbc/constants.h"
#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "modules/audio_coding/codecs/ilbc/frame_classify.h"
#include "modules/audio_coding/codecs/ilbc/hp_input.h"
#include "modules/audio_coding/codecs/ilbc/index_conv_enc.h"
#include "modules/audio_coding/codecs/ilbc/lpc_encode.h"
#include "modules/audio_coding/codecs/ilbc/pack_bits.h"
#include "modules/audio_coding/codecs/ilbc/state_construct.h"
#include "modules/audio_coding/codecs/ilbc/state_search.h"
#include "rtc_base/checks.h"
#include "rtc_base/system/arch.h"

#ifdef SPLIT_10MS
#include "modules/audio_coding/codecs/ilbc/unpack_bits.h"
#include "modules/audio_coding/codecs/ilbc/index_conv_dec.h"
#endif

#ifndef WEBRTC_ARCH_BIG_ENDIAN
#include "modules/audio_coding/codecs/ilbc/swap_bytes.h"
#endif

/*----------------------------------------------------------------*
 *  main encoder function
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_EncodeImpl(
    uint16_t *bytes,     /* (o) encoded data bits iLBC */
    const int16_t *block, /* (i) speech vector to encode */
    IlbcEncoder *iLBCenc_inst /* (i/o) the general encoder
                                     state */
                          ){
  size_t n, meml_gotten, Nfor;
  size_t diff, start_pos;
  size_t index;
  size_t subcount, subframe;
  size_t start_count, end_count;
  int16_t *residual;
  int32_t en1, en2;
  int16_t scale, max;
  int16_t *syntdenum;
  int16_t *decresidual;
  int16_t *reverseResidual;
  int16_t *reverseDecresidual;
  /* Stack based */
  int16_t weightdenum[(LPC_FILTERORDER + 1)*NSUB_MAX];
  int16_t dataVec[BLOCKL_MAX + LPC_FILTERORDER];
  int16_t memVec[CB_MEML+CB_FILTERLEN];
  int16_t bitsMemory[sizeof(iLBC_bits)/sizeof(int16_t)];
  iLBC_bits *iLBCbits_inst = (iLBC_bits*)bitsMemory;


#ifdef SPLIT_10MS
  int16_t *weightdenumbuf = iLBCenc_inst->weightdenumbuf;
  int16_t last_bit;
#endif

  int16_t *data = &dataVec[LPC_FILTERORDER];
  int16_t *mem = &memVec[CB_HALFFILTERLEN];

  /* Reuse som buffers to save stack memory */
  residual = &iLBCenc_inst->lpc_buffer[LPC_LOOKBACK+BLOCKL_MAX-iLBCenc_inst->blockl];
  syntdenum = mem;      /* syntdenum[(LPC_FILTERORDER + 1)*NSUB_MAX] and mem are used non overlapping in the code */
  decresidual = residual;     /* Already encoded residual is overwritten by the decoded version */
  reverseResidual = data;     /* data and reverseResidual are used non overlapping in the code */
  reverseDecresidual = reverseResidual; /* Already encoded residual is overwritten by the decoded version */

#ifdef SPLIT_10MS

  WebRtcSpl_MemSetW16 (  (int16_t *) iLBCbits_inst, 0,
                         sizeof(iLBC_bits) / sizeof(int16_t)  );

  start_pos = iLBCenc_inst->start_pos;
  diff = iLBCenc_inst->diff;

  if (iLBCenc_inst->section != 0){
    WEBRTC_SPL_MEMCPY_W16 (weightdenum, weightdenumbuf,
                           SCRATCH_ENCODE_DATAVEC - SCRATCH_ENCODE_WEIGHTDENUM);
    /* Un-Packetize the frame into parameters */
    last_bit = WebRtcIlbcfix_UnpackBits (iLBCenc_inst->bytes, iLBCbits_inst, iLBCenc_inst->mode);
    if (last_bit)
      return;
    /* adjust index */
    WebRtcIlbcfix_IndexConvDec (iLBCbits_inst->cb_index);

    if (iLBCenc_inst->section == 1){
      /* Save first 80 samples of a 160/240 sample frame for 20/30msec */
      WEBRTC_SPL_MEMCPY_W16 (iLBCenc_inst->past_samples, block, 80);
    }
    else{ // iLBCenc_inst->section == 2 AND mode = 30ms
      /* Save second 80 samples of a 240 sample frame for 30msec */
      WEBRTC_SPL_MEMCPY_W16 (iLBCenc_inst->past_samples + 80, block, 80);
    }
  }
  else{ // iLBCenc_inst->section == 0
    /* form a complete frame of 160/240 for 20msec/30msec mode */
    WEBRTC_SPL_MEMCPY_W16 (data + (iLBCenc_inst->mode * 8) - 80, block, 80);
    WEBRTC_SPL_MEMCPY_W16 (data, iLBCenc_inst->past_samples,
                           (iLBCenc_inst->mode * 8) - 80);
    iLBCenc_inst->Nfor_flag = 0;
    iLBCenc_inst->Nback_flag = 0;
#else
    /* copy input block to data*/
    WEBRTC_SPL_MEMCPY_W16(data,block,iLBCenc_inst->blockl);
#endif

    /* high pass filtering of input signal and scale down the residual (*0.5) */
    WebRtcIlbcfix_HpInput(data, (int16_t*)WebRtcIlbcfix_kHpInCoefs,
                          iLBCenc_inst->hpimemy, iLBCenc_inst->hpimemx,
                          iLBCenc_inst->blockl);

    /* LPC of hp filtered input data */
    WebRtcIlbcfix_LpcEncode(syntdenum, weightdenum, iLBCbits_inst->lsf, data,
                            iLBCenc_inst);

    /* Set up state */
    WEBRTC_SPL_MEMCPY_W16(dataVec, iLBCenc_inst->anaMem, LPC_FILTERORDER);

    /* inverse filter to get residual */
    for (n=0; n<iLBCenc_inst->nsub; n++ ) {
      WebRtcSpl_FilterMAFastQ12(
          &data[n*SUBL], &residual[n*SUBL],
          &syntdenum[n*(LPC_FILTERORDER+1)],
          LPC_FILTERORDER+1, SUBL);
    }

    /* Copy the state for next frame */
    WEBRTC_SPL_MEMCPY_W16(iLBCenc_inst->anaMem, &data[iLBCenc_inst->blockl-LPC_FILTERORDER], LPC_FILTERORDER);

    /* find state location */

    iLBCbits_inst->startIdx = WebRtcIlbcfix_FrameClassify(iLBCenc_inst,residual);

    /* check if state should be in first or last part of the
       two subframes */

    index = (iLBCbits_inst->startIdx-1)*SUBL;
    max=WebRtcSpl_MaxAbsValueW16(&residual[index], 2*SUBL);
    scale = WebRtcSpl_GetSizeInBits((uint32_t)(max * max));

    /* Scale to maximum 25 bits so that the MAC won't cause overflow */
    scale = scale - 25;
    if(scale < 0) {
      scale = 0;
    }

    diff = STATE_LEN - iLBCenc_inst->state_short_len;
    en1=WebRtcSpl_DotProductWithScale(&residual[index], &residual[index],
                                      iLBCenc_inst->state_short_len, scale);
    index += diff;
    en2=WebRtcSpl_DotProductWithScale(&residual[index], &residual[index],
                                      iLBCenc_inst->state_short_len, scale);
    if (en1 > en2) {
      iLBCbits_inst->state_first = 1;
      start_pos = (iLBCbits_inst->startIdx-1)*SUBL;
    } else {
      iLBCbits_inst->state_first = 0;
      start_pos = (iLBCbits_inst->startIdx-1)*SUBL + diff;
    }

    /* scalar quantization of state */

    WebRtcIlbcfix_StateSearch(iLBCenc_inst, iLBCbits_inst, &residual[start_pos],
                              &syntdenum[(iLBCbits_inst->startIdx-1)*(LPC_FILTERORDER+1)],
                              &weightdenum[(iLBCbits_inst->startIdx-1)*(LPC_FILTERORDER+1)]);

    WebRtcIlbcfix_StateConstruct(iLBCbits_inst->idxForMax, iLBCbits_inst->idxVec,
                                 &syntdenum[(iLBCbits_inst->startIdx-1)*(LPC_FILTERORDER+1)],
                                 &decresidual[start_pos], iLBCenc_inst->state_short_len
                                 );

    /* predictive quantization in state */

    if (iLBCbits_inst->state_first) { /* put adaptive part in the end */

      /* setup memory */

      WebRtcSpl_MemSetW16(mem, 0, CB_MEML - iLBCenc_inst->state_short_len);
      WEBRTC_SPL_MEMCPY_W16(mem+CB_MEML-iLBCenc_inst->state_short_len,
                            decresidual+start_pos, iLBCenc_inst->state_short_len);

      /* encode subframes */

      WebRtcIlbcfix_CbSearch(iLBCenc_inst, iLBCbits_inst->cb_index, iLBCbits_inst->gain_index,
                             &residual[start_pos+iLBCenc_inst->state_short_len],
                             mem+CB_MEML-ST_MEM_L_TBL, ST_MEM_L_TBL, diff,
                             &weightdenum[iLBCbits_inst->startIdx*(LPC_FILTERORDER+1)], 0);

      /* construct decoded vector */

      RTC_CHECK(WebRtcIlbcfix_CbConstruct(
          &decresidual[start_pos + iLBCenc_inst->state_short_len],
          iLBCbits_inst->cb_index, iLBCbits_inst->gain_index,
          mem + CB_MEML - ST_MEM_L_TBL, ST_MEM_L_TBL, diff));

    }
    else { /* put adaptive part in the beginning */

      /* create reversed vectors for prediction */

      WebRtcSpl_MemCpyReversedOrder(&reverseResidual[diff-1],
                                    &residual[(iLBCbits_inst->startIdx+1)*SUBL-STATE_LEN], diff);

      /* setup memory */

      meml_gotten = iLBCenc_inst->state_short_len;
      WebRtcSpl_MemCpyReversedOrder(&mem[CB_MEML-1], &decresidual[start_pos], meml_gotten);
      WebRtcSpl_MemSetW16(mem, 0, CB_MEML - iLBCenc_inst->state_short_len);

      /* encode subframes */
      WebRtcIlbcfix_CbSearch(iLBCenc_inst, iLBCbits_inst->cb_index, iLBCbits_inst->gain_index,
                             reverseResidual, mem+CB_MEML-ST_MEM_L_TBL, ST_MEM_L_TBL, diff,
                             &weightdenum[(iLBCbits_inst->startIdx-1)*(LPC_FILTERORDER+1)],
                             0);

      /* construct decoded vector */
      RTC_CHECK(WebRtcIlbcfix_CbConstruct(
            reverseDecresidual, iLBCbits_inst->cb_index,
            iLBCbits_inst->gain_index, mem + CB_MEML - ST_MEM_L_TBL,
            ST_MEM_L_TBL, diff));

      /* get decoded residual from reversed vector */

      WebRtcSpl_MemCpyReversedOrder(&decresidual[start_pos-1], reverseDecresidual, diff);
    }

#ifdef SPLIT_10MS
    iLBCenc_inst->start_pos = start_pos;
    iLBCenc_inst->diff = diff;
    iLBCenc_inst->section++;
    /* adjust index */
    WebRtcIlbcfix_IndexConvEnc (iLBCbits_inst->cb_index);
    /* Packetize the parameters into the frame */
    WebRtcIlbcfix_PackBits (iLBCenc_inst->bytes, iLBCbits_inst, iLBCenc_inst->mode);
    WEBRTC_SPL_MEMCPY_W16 (weightdenumbuf, weightdenum,
                           SCRATCH_ENCODE_DATAVEC - SCRATCH_ENCODE_WEIGHTDENUM);
    return;
  }
#endif

  /* forward prediction of subframes */

  Nfor = iLBCenc_inst->nsub-iLBCbits_inst->startIdx-1;

  /* counter for predicted subframes */
#ifdef SPLIT_10MS
  if (iLBCenc_inst->mode == 20)
  {
    subcount = 1;
  }
  if (iLBCenc_inst->mode == 30)
  {
    if (iLBCenc_inst->section == 1)
    {
      subcount = 1;
    }
    if (iLBCenc_inst->section == 2)
    {
      subcount = 3;
    }
  }
#else
  subcount=1;
#endif

  if( Nfor > 0 ){

    /* setup memory */

    WebRtcSpl_MemSetW16(mem, 0, CB_MEML-STATE_LEN);
    WEBRTC_SPL_MEMCPY_W16(mem+CB_MEML-STATE_LEN,
                          decresidual+(iLBCbits_inst->startIdx-1)*SUBL, STATE_LEN);

#ifdef SPLIT_10MS
    if (iLBCenc_inst->Nfor_flag > 0)
    {
      for (subframe = 0; subframe < WEBRTC_SPL_MIN (Nfor, 2); subframe++)
      {
        /* update memory */
        WEBRTC_SPL_MEMCPY_W16 (mem, mem + SUBL, (CB_MEML - SUBL));
        WEBRTC_SPL_MEMCPY_W16 (mem + CB_MEML - SUBL,
                               &decresidual[(iLBCbits_inst->startIdx + 1 +
                                             subframe) * SUBL], SUBL);
      }
    }

    iLBCenc_inst->Nfor_flag++;

    if (iLBCenc_inst->mode == 20)
    {
      start_count = 0;
      end_count = Nfor;
    }
    if (iLBCenc_inst->mode == 30)
    {
      if (iLBCenc_inst->section == 1)
      {
        start_count = 0;
        end_count = WEBRTC_SPL_MIN (Nfor, (size_t)2);
      }
      if (iLBCenc_inst->section == 2)
      {
        start_count = WEBRTC_SPL_MIN (Nfor, (size_t)2);
        end_count = Nfor;
      }
    }
#else
    start_count = 0;
    end_count = Nfor;
#endif

    /* loop over subframes to encode */

    for (subframe = start_count; subframe < end_count; subframe++){

      /* encode subframe */

      WebRtcIlbcfix_CbSearch(iLBCenc_inst, iLBCbits_inst->cb_index+subcount*CB_NSTAGES,
                             iLBCbits_inst->gain_index+subcount*CB_NSTAGES,
                             &residual[(iLBCbits_inst->startIdx+1+subframe)*SUBL],
                             mem, MEM_LF_TBL, SUBL,
                             &weightdenum[(iLBCbits_inst->startIdx+1+subframe)*(LPC_FILTERORDER+1)],
                             subcount);

      /* construct decoded vector */
      RTC_CHECK(WebRtcIlbcfix_CbConstruct(
            &decresidual[(iLBCbits_inst->startIdx + 1 + subframe) * SUBL],
            iLBCbits_inst->cb_index + subcount * CB_NSTAGES,
            iLBCbits_inst->gain_index + subcount * CB_NSTAGES, mem, MEM_LF_TBL,
            SUBL));

      /* update memory */

      memmove(mem, mem + SUBL, (CB_MEML - SUBL) * sizeof(*mem));
      WEBRTC_SPL_MEMCPY_W16(mem+CB_MEML-SUBL,
                            &decresidual[(iLBCbits_inst->startIdx+1+subframe)*SUBL], SUBL);

      subcount++;
    }
  }

#ifdef SPLIT_10MS
  if ((iLBCenc_inst->section == 1) &&
      (iLBCenc_inst->mode == 30) && (Nfor > 0) && (end_count == 2))
  {
    iLBCenc_inst->section++;
    /* adjust index */
    WebRtcIlbcfix_IndexConvEnc (iLBCbits_inst->cb_index);
    /* Packetize the parameters into the frame */
    WebRtcIlbcfix_PackBits (iLBCenc_inst->bytes, iLBCbits_inst, iLBCenc_inst->mode);
    WEBRTC_SPL_MEMCPY_W16 (weightdenumbuf, weightdenum,
                           SCRATCH_ENCODE_DATAVEC - SCRATCH_ENCODE_WEIGHTDENUM);
    return;
  }
#endif

  /* backward prediction of subframes */

  if (iLBCbits_inst->startIdx > 1) {

    /* create reverse order vectors
       (The decresidual does not need to be copied since it is
       contained in the same vector as the residual)
    */

    size_t Nback = iLBCbits_inst->startIdx - 1;
    WebRtcSpl_MemCpyReversedOrder(&reverseResidual[Nback*SUBL-1], residual, Nback*SUBL);

    /* setup memory */

    meml_gotten = SUBL*(iLBCenc_inst->nsub+1-iLBCbits_inst->startIdx);
    if( meml_gotten > CB_MEML ) {
      meml_gotten=CB_MEML;
    }

    WebRtcSpl_MemCpyReversedOrder(&mem[CB_MEML-1], &decresidual[Nback*SUBL], meml_gotten);
    WebRtcSpl_MemSetW16(mem, 0, CB_MEML - meml_gotten);

#ifdef SPLIT_10MS
    if (iLBCenc_inst->Nback_flag > 0)
    {
      for (subframe = 0; subframe < WEBRTC_SPL_MAX (2 - Nfor, 0); subframe++)
      {
        /* update memory */
        WEBRTC_SPL_MEMCPY_W16 (mem, mem + SUBL, (CB_MEML - SUBL));
        WEBRTC_SPL_MEMCPY_W16 (mem + CB_MEML - SUBL,
                               &reverseDecresidual[subframe * SUBL], SUBL);
      }
    }

    iLBCenc_inst->Nback_flag++;


    if (iLBCenc_inst->mode == 20)
    {
      start_count = 0;
      end_count = Nback;
    }
    if (iLBCenc_inst->mode == 30)
    {
      if (iLBCenc_inst->section == 1)
      {
        start_count = 0;
        end_count = (Nfor >= 2) ? 0 : (2 - NFor);
      }
      if (iLBCenc_inst->section == 2)
      {
        start_count = (Nfor >= 2) ? 0 : (2 - NFor);
        end_count = Nback;
      }
    }
#else
    start_count = 0;
    end_count = Nback;
#endif

    /* loop over subframes to encode */

    for (subframe = start_count; subframe < end_count; subframe++){

      /* encode subframe */

      WebRtcIlbcfix_CbSearch(iLBCenc_inst, iLBCbits_inst->cb_index+subcount*CB_NSTAGES,
                             iLBCbits_inst->gain_index+subcount*CB_NSTAGES, &reverseResidual[subframe*SUBL],
                             mem, MEM_LF_TBL, SUBL,
                             &weightdenum[(iLBCbits_inst->startIdx-2-subframe)*(LPC_FILTERORDER+1)],
                             subcount);

      /* construct decoded vector */
      RTC_CHECK(WebRtcIlbcfix_CbConstruct(
            &reverseDecresidual[subframe * SUBL],
            iLBCbits_inst->cb_index + subcount * CB_NSTAGES,
            iLBCbits_inst->gain_index + subcount * CB_NSTAGES, mem, MEM_LF_TBL,
            SUBL));

      /* update memory */
      memmove(mem, mem + SUBL, (CB_MEML - SUBL) * sizeof(*mem));
      WEBRTC_SPL_MEMCPY_W16(mem+CB_MEML-SUBL,
                            &reverseDecresidual[subframe*SUBL], SUBL);

      subcount++;

    }

    /* get decoded residual from reversed vector */

    WebRtcSpl_MemCpyReversedOrder(&decresidual[SUBL*Nback-1], reverseDecresidual, SUBL*Nback);
  }
  /* end encoding part */

  /* adjust index */

  WebRtcIlbcfix_IndexConvEnc(iLBCbits_inst->cb_index);

  /* Packetize the parameters into the frame */

#ifdef SPLIT_10MS
  if( (iLBCenc_inst->mode==30) && (iLBCenc_inst->section==1) ){
    WebRtcIlbcfix_PackBits(iLBCenc_inst->bytes, iLBCbits_inst, iLBCenc_inst->mode);
  }
  else{
    WebRtcIlbcfix_PackBits(bytes, iLBCbits_inst, iLBCenc_inst->mode);
  }
#else
  WebRtcIlbcfix_PackBits(bytes, iLBCbits_inst, iLBCenc_inst->mode);
#endif

#ifndef WEBRTC_ARCH_BIG_ENDIAN
  /* Swap bytes for LITTLE ENDIAN since the packbits()
     function assumes BIG_ENDIAN machine */
#ifdef SPLIT_10MS
  if (( (iLBCenc_inst->section == 1) && (iLBCenc_inst->mode == 20) ) ||
      ( (iLBCenc_inst->section == 2) && (iLBCenc_inst->mode == 30) )){
    WebRtcIlbcfix_SwapBytes(bytes, iLBCenc_inst->no_of_words, bytes);
  }
#else
  WebRtcIlbcfix_SwapBytes(bytes, iLBCenc_inst->no_of_words, bytes);
#endif
#endif

#ifdef SPLIT_10MS
  if (subcount == (iLBCenc_inst->nsub - 1))
  {
    iLBCenc_inst->section = 0;
  }
  else
  {
    iLBCenc_inst->section++;
    WEBRTC_SPL_MEMCPY_W16 (weightdenumbuf, weightdenum,
                           SCRATCH_ENCODE_DATAVEC - SCRATCH_ENCODE_WEIGHTDENUM);
  }
#endif

}
