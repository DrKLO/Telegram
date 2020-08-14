/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * encode.c
 *
 * Encoding function for the iSAC coder.
 *
 */

#include "rtc_base/checks.h"
#include "modules/audio_coding/codecs/isac/fix/source/codec.h"

#include <stdio.h>

#include "modules/audio_coding/codecs/isac/fix/source/arith_routins.h"
#include "modules/audio_coding/codecs/isac/fix/source/bandwidth_estimator.h"
#include "modules/audio_coding/codecs/isac/fix/source/entropy_coding.h"
#include "modules/audio_coding/codecs/isac/fix/source/lpc_masking_model.h"
#include "modules/audio_coding/codecs/isac/fix/source/lpc_tables.h"
#include "modules/audio_coding/codecs/isac/fix/source/pitch_estimator.h"
#include "modules/audio_coding/codecs/isac/fix/source/pitch_gain_tables.h"
#include "modules/audio_coding/codecs/isac/fix/source/pitch_lag_tables.h"
#include "modules/audio_coding/codecs/isac/fix/source/structs.h"


int WebRtcIsacfix_EncodeImpl(int16_t      *in,
                             IsacFixEncoderInstance  *ISACenc_obj,
                             BwEstimatorstr      *bw_estimatordata,
                             int16_t         CodingMode)
{
  int16_t stream_length = 0;
  int16_t usefulstr_len = 0;
  int k;
  int16_t BWno;

  int16_t lofilt_coefQ15[(ORDERLO)*SUBFRAMES];
  int16_t hifilt_coefQ15[(ORDERHI)*SUBFRAMES];
  int32_t gain_lo_hiQ17[2*SUBFRAMES];

  int16_t LPandHP[FRAMESAMPLES/2 + QLOOKAHEAD];
  int16_t LP16a[FRAMESAMPLES/2 + QLOOKAHEAD];
  int16_t HP16a[FRAMESAMPLES/2 + QLOOKAHEAD];

  int16_t PitchLags_Q7[PITCH_SUBFRAMES];
  int16_t PitchGains_Q12[PITCH_SUBFRAMES];
  int16_t AvgPitchGain_Q12;

  int16_t frame_mode; /* 0 for 30ms, 1 for 60ms */
  int16_t processed_samples;
  int status;

  int32_t bits_gainsQ11;
  int16_t MinBytes;
  int16_t bmodel;

  transcode_obj transcodingParam;
  int16_t payloadLimitBytes;
  int16_t arithLenBeforeEncodingDFT;
  int16_t iterCntr;

  /* copy new frame length and bottle neck rate only for the first 10 ms data */
  if (ISACenc_obj->buffer_index == 0) {
    /* set the framelength for the next packet */
    ISACenc_obj->current_framesamples = ISACenc_obj->new_framelength;
  }

  frame_mode = ISACenc_obj->current_framesamples/MAX_FRAMESAMPLES; /* 0 (30 ms) or 1 (60 ms)  */
  processed_samples = ISACenc_obj->current_framesamples/(frame_mode+1); /* 480 (30, 60 ms) */

  /* buffer speech samples (by 10ms packet) until the framelength is reached (30 or 60 ms) */
  /**************************************************************************************/
  /* fill the buffer with 10ms input data */
  for(k=0; k<FRAMESAMPLES_10ms; k++) {
    ISACenc_obj->data_buffer_fix[k + ISACenc_obj->buffer_index] = in[k];
  }
  /* if buffersize is not equal to current framesize, and end of file is not reached yet, */
  /* increase index and go back to main to get more speech samples */
  if (ISACenc_obj->buffer_index + FRAMESAMPLES_10ms != processed_samples) {
    ISACenc_obj->buffer_index = ISACenc_obj->buffer_index + FRAMESAMPLES_10ms;
    return 0;
  }
  /* if buffer reached the right size, reset index and continue with encoding the frame */
  ISACenc_obj->buffer_index = 0;

  /* end of buffer function */
  /**************************/

  /* encoding */
  /************/

  if (frame_mode == 0 || ISACenc_obj->frame_nb == 0 )
  {
    /* reset bitstream */
    ISACenc_obj->bitstr_obj.W_upper = 0xFFFFFFFF;
    ISACenc_obj->bitstr_obj.streamval = 0;
    ISACenc_obj->bitstr_obj.stream_index = 0;
    ISACenc_obj->bitstr_obj.full = 1;

    if (CodingMode == 0) {
      ISACenc_obj->BottleNeck =  WebRtcIsacfix_GetUplinkBandwidth(bw_estimatordata);
      ISACenc_obj->MaxDelay =  WebRtcIsacfix_GetUplinkMaxDelay(bw_estimatordata);
    }
    if (CodingMode == 0 && frame_mode == 0 && (ISACenc_obj->enforceFrameSize == 0)) {
      ISACenc_obj->new_framelength = WebRtcIsacfix_GetNewFrameLength(ISACenc_obj->BottleNeck,
                                                                     ISACenc_obj->current_framesamples);
    }

    // multiply the bottleneck by 0.88 before computing SNR, 0.88 is tuned by experimenting on TIMIT
    // 901/1024 is 0.87988281250000
    ISACenc_obj->s2nr = WebRtcIsacfix_GetSnr(
        (int16_t)(ISACenc_obj->BottleNeck * 901 >> 10),
        ISACenc_obj->current_framesamples);

    /* encode frame length */
    status = WebRtcIsacfix_EncodeFrameLen(ISACenc_obj->current_framesamples, &ISACenc_obj->bitstr_obj);
    if (status < 0)
    {
      /* Wrong frame size */
      if (frame_mode == 1 && ISACenc_obj->frame_nb == 1)
      {
        // If this is the second 30ms of a 60ms frame reset this such that in the next call
        // encoder starts fresh.
        ISACenc_obj->frame_nb = 0;
      }
      return status;
    }

    /* Save framelength for multiple packets memory */
    if (ISACenc_obj->SaveEnc_ptr != NULL) {
      (ISACenc_obj->SaveEnc_ptr)->framelength=ISACenc_obj->current_framesamples;
    }

    /* bandwidth estimation and coding */
    BWno = WebRtcIsacfix_GetDownlinkBwIndexImpl(bw_estimatordata);
    status = WebRtcIsacfix_EncodeReceiveBandwidth(&BWno, &ISACenc_obj->bitstr_obj);
    if (status < 0)
    {
      if (frame_mode == 1 && ISACenc_obj->frame_nb == 1)
      {
        // If this is the second 30ms of a 60ms frame reset this such that in the next call
        // encoder starts fresh.
        ISACenc_obj->frame_nb = 0;
      }
      return status;
    }
  }

  /* split signal in two bands */
  WebRtcIsacfix_SplitAndFilter1(ISACenc_obj->data_buffer_fix, LP16a, HP16a, &ISACenc_obj->prefiltbankstr_obj );

  /* estimate pitch parameters and pitch-filter lookahead signal */
  WebRtcIsacfix_PitchAnalysis(LP16a+QLOOKAHEAD, LPandHP,
                              &ISACenc_obj->pitchanalysisstr_obj,  PitchLags_Q7, PitchGains_Q12); /* LPandHP = LP_lookahead_pfQ0, */

  /* Set where to store data in multiple packets memory */
  if (ISACenc_obj->SaveEnc_ptr != NULL) {
    if (frame_mode == 0 || ISACenc_obj->frame_nb == 0)
    {
      (ISACenc_obj->SaveEnc_ptr)->startIdx = 0;
    }
    else
    {
      (ISACenc_obj->SaveEnc_ptr)->startIdx = 1;
    }
  }

  /* quantize & encode pitch parameters */
  status = WebRtcIsacfix_EncodePitchGain(PitchGains_Q12, &ISACenc_obj->bitstr_obj,  ISACenc_obj->SaveEnc_ptr);
  if (status < 0)
  {
    if (frame_mode == 1 && ISACenc_obj->frame_nb == 1)
    {
      // If this is the second 30ms of a 60ms frame reset this such that in the next call
      // encoder starts fresh.
      ISACenc_obj->frame_nb = 0;
    }
    return status;
  }
  status = WebRtcIsacfix_EncodePitchLag(PitchLags_Q7 , PitchGains_Q12, &ISACenc_obj->bitstr_obj,  ISACenc_obj->SaveEnc_ptr);
  if (status < 0)
  {
    if (frame_mode == 1 && ISACenc_obj->frame_nb == 1)
    {
      // If this is the second 30ms of a 60ms frame reset this such that in the next call
      // encoder starts fresh.
      ISACenc_obj->frame_nb = 0;
    }
    return status;
  }
  AvgPitchGain_Q12 = (PitchGains_Q12[0] + PitchGains_Q12[1] +
      PitchGains_Q12[2] + PitchGains_Q12[3]) >> 2;

  /* find coefficients for perceptual pre-filters */
  WebRtcIsacfix_GetLpcCoef(LPandHP, HP16a+QLOOKAHEAD, &ISACenc_obj->maskfiltstr_obj,
                           ISACenc_obj->s2nr, PitchGains_Q12,
                           gain_lo_hiQ17, lofilt_coefQ15, hifilt_coefQ15); /*LPandHP = LP_lookahead_pfQ0*/

  // record LPC Gains for possible bit-rate reduction
  for(k = 0; k < KLT_ORDER_GAIN; k++)
  {
    transcodingParam.lpcGains[k] = gain_lo_hiQ17[k];
  }

  /* code LPC model and shape - gains not quantized yet */
  status = WebRtcIsacfix_EncodeLpc(gain_lo_hiQ17, lofilt_coefQ15, hifilt_coefQ15,
                                   &bmodel, &bits_gainsQ11, &ISACenc_obj->bitstr_obj, ISACenc_obj->SaveEnc_ptr, &transcodingParam);
  if (status < 0)
  {
    if (frame_mode == 1 && ISACenc_obj->frame_nb == 1)
    {
      // If this is the second 30ms of a 60ms frame reset this such that in the next call
      // encoder starts fresh.
      ISACenc_obj->frame_nb = 0;
    }
    return status;
  }
  arithLenBeforeEncodingDFT = (ISACenc_obj->bitstr_obj.stream_index << 1) + (1-ISACenc_obj->bitstr_obj.full);

  /* low-band filtering */
  WebRtcIsacfix_NormLatticeFilterMa(ORDERLO, ISACenc_obj->maskfiltstr_obj.PreStateLoGQ15,
                                    LP16a, lofilt_coefQ15, gain_lo_hiQ17, 0, LPandHP);/* LPandHP = LP16b */

  /* pitch filter */
  WebRtcIsacfix_PitchFilter(LPandHP, LP16a, &ISACenc_obj->pitchfiltstr_obj, PitchLags_Q7, PitchGains_Q12, 1);/* LPandHP = LP16b */

  /* high-band filtering */
  WebRtcIsacfix_NormLatticeFilterMa(ORDERHI, ISACenc_obj->maskfiltstr_obj.PreStateHiGQ15,
                                    HP16a, hifilt_coefQ15, gain_lo_hiQ17, 1, LPandHP);/*LPandHP = HP16b*/

  /* transform */
  WebRtcIsacfix_Time2Spec(LP16a, LPandHP, LP16a, LPandHP); /*LPandHP = HP16b*/

  /* Save data for multiple packets memory */
  if (ISACenc_obj->SaveEnc_ptr != NULL) {
    for (k = 0; k < FRAMESAMPLES_HALF; k++) {
      (ISACenc_obj->SaveEnc_ptr)->fre[k + (ISACenc_obj->SaveEnc_ptr)->startIdx*FRAMESAMPLES_HALF] = LP16a[k];
      (ISACenc_obj->SaveEnc_ptr)->fim[k + (ISACenc_obj->SaveEnc_ptr)->startIdx*FRAMESAMPLES_HALF] = LPandHP[k];
    }
    (ISACenc_obj->SaveEnc_ptr)->AvgPitchGain[(ISACenc_obj->SaveEnc_ptr)->startIdx] = AvgPitchGain_Q12;
  }

  /* quantization and lossless coding */
  status = WebRtcIsacfix_EncodeSpec(LP16a, LPandHP, &ISACenc_obj->bitstr_obj, AvgPitchGain_Q12);
  if((status <= -1) && (status != -ISAC_DISALLOWED_BITSTREAM_LENGTH)) /*LPandHP = HP16b*/
  {
    if (frame_mode == 1 && ISACenc_obj->frame_nb == 1)
    {
      // If this is the second 30ms of a 60ms frame reset this such that in the next call
      // encoder starts fresh.
      ISACenc_obj->frame_nb = 0;
    }
    return status;
  }

  if((frame_mode == 1) && (ISACenc_obj->frame_nb == 0))
  {
    // it is a 60ms and we are in the first 30ms
    // then the limit at this point should be half of the assigned value
    payloadLimitBytes = ISACenc_obj->payloadLimitBytes60 >> 1;
  }
  else if (frame_mode == 0)
  {
    // it is a 30ms frame
    payloadLimitBytes = (ISACenc_obj->payloadLimitBytes30) - 3;
  }
  else
  {
    // this is the second half of a 60ms frame.
    payloadLimitBytes = ISACenc_obj->payloadLimitBytes60 - 3; // subract 3 because termination process may add 3 bytes
  }

  iterCntr = 0;
  while((((ISACenc_obj->bitstr_obj.stream_index) << 1) > payloadLimitBytes) ||
        (status == -ISAC_DISALLOWED_BITSTREAM_LENGTH))
  {
    int16_t arithLenDFTByte;
    int16_t bytesLeftQ5;
    int16_t ratioQ5[8] = {0, 6, 9, 12, 16, 19, 22, 25};

    // According to experiments on TIMIT the following is proper for audio, but it is not agressive enough for tonal inputs
    // such as DTMF, sweep-sine, ...
    //
    // (0.55 - (0.8 - ratio[i]/32) * 5 / 6) * 2^14
    // int16_t scaleQ14[8] = {0, 648, 1928, 3208, 4915, 6195, 7475, 8755};


    // This is a supper-agressive scaling passed the tests (tonal inputs) tone with one iteration for payload limit
    // of 120 (32kbps bottleneck), number of frames needed a rate-reduction was 58403
    //
    int16_t scaleQ14[8] = {0, 348, 828, 1408, 2015, 3195, 3500, 3500};
    int16_t idx;

    if(iterCntr >= MAX_PAYLOAD_LIMIT_ITERATION)
    {
      // We were not able to limit the payload size

      if((frame_mode == 1) && (ISACenc_obj->frame_nb == 0))
      {
        // This was the first 30ms of a 60ms frame. Although the payload is larger than it
        // should be but we let the second 30ms be encoded. Maybe togetehr we won't exceed
        // the limit.
        ISACenc_obj->frame_nb = 1;
        return 0;
      }
      else if((frame_mode == 1) && (ISACenc_obj->frame_nb == 1))
      {
        ISACenc_obj->frame_nb = 0;
      }

      if(status != -ISAC_DISALLOWED_BITSTREAM_LENGTH)
      {
        return -ISAC_PAYLOAD_LARGER_THAN_LIMIT;
      }
      else
      {
        return status;
      }
    }
    if(status != -ISAC_DISALLOWED_BITSTREAM_LENGTH)
    {
      arithLenDFTByte = (ISACenc_obj->bitstr_obj.stream_index << 1) + (1-ISACenc_obj->bitstr_obj.full) - arithLenBeforeEncodingDFT;
      bytesLeftQ5 = (payloadLimitBytes - arithLenBeforeEncodingDFT) << 5;

      // bytesLeft / arithLenDFTBytes indicates how much scaling is required a rough estimate (agressive)
      // scale = 0.55 - (0.8 - bytesLeft / arithLenDFTBytes) * 5 / 6
      // bytesLeft / arithLenDFTBytes below 0.2 will have a scale of zero and above 0.8 are treated as 0.8
      // to avoid division we do more simplification.
      //
      // values of (bytesLeft / arithLenDFTBytes)*32 between ratioQ5[i] and ratioQ5[i+1] are rounded to ratioQ5[i]
      // and the corresponding scale is chosen

      // we compare bytesLeftQ5 with ratioQ5[]*arithLenDFTByte;
      idx = 4;
      idx += (bytesLeftQ5 >= ratioQ5[idx] * arithLenDFTByte) ? 2 : -2;
      idx += (bytesLeftQ5 >= ratioQ5[idx] * arithLenDFTByte) ? 1 : -1;
      idx += (bytesLeftQ5 >= ratioQ5[idx] * arithLenDFTByte) ? 0 : -1;
    }
    else
    {
      // we are here because the bit-stream did not fit into the buffer, in this case, the stream_index is not
      // trustable, especially if the is the first 30ms of a packet. Thereforem, we will go for the most agressive
      // case.
      idx = 0;
    }
    // scale FFT coefficients to reduce the bit-rate
    for(k = 0; k < FRAMESAMPLES_HALF; k++)
    {
      LP16a[k] = (int16_t)(LP16a[k] * scaleQ14[idx] >> 14);
      LPandHP[k] = (int16_t)(LPandHP[k] * scaleQ14[idx] >> 14);
    }

    // Save data for multiple packets memory
    if (ISACenc_obj->SaveEnc_ptr != NULL)
    {
      for(k = 0; k < FRAMESAMPLES_HALF; k++)
      {
        (ISACenc_obj->SaveEnc_ptr)->fre[k + (ISACenc_obj->SaveEnc_ptr)->startIdx*FRAMESAMPLES_HALF] = LP16a[k];
        (ISACenc_obj->SaveEnc_ptr)->fim[k + (ISACenc_obj->SaveEnc_ptr)->startIdx*FRAMESAMPLES_HALF] = LPandHP[k];
      }
    }

    // scale the unquantized LPC gains and save the scaled version for the future use
    for(k = 0; k < KLT_ORDER_GAIN; k++)
    {
      gain_lo_hiQ17[k] = WEBRTC_SPL_MUL_16_32_RSFT14(scaleQ14[idx], transcodingParam.lpcGains[k]);//transcodingParam.lpcGains[k]; //
      transcodingParam.lpcGains[k] = gain_lo_hiQ17[k];
    }

    // reset the bit-stream object to the state which it had before encoding LPC Gains
    ISACenc_obj->bitstr_obj.full = transcodingParam.full;
    ISACenc_obj->bitstr_obj.stream_index = transcodingParam.stream_index;
    ISACenc_obj->bitstr_obj.streamval = transcodingParam.streamval;
    ISACenc_obj->bitstr_obj.W_upper = transcodingParam.W_upper;
    ISACenc_obj->bitstr_obj.stream[transcodingParam.stream_index-1] = transcodingParam.beforeLastWord;
    ISACenc_obj->bitstr_obj.stream[transcodingParam.stream_index] = transcodingParam.lastWord;


    // quantize and encode LPC gain
    WebRtcIsacfix_EstCodeLpcGain(gain_lo_hiQ17, &ISACenc_obj->bitstr_obj, ISACenc_obj->SaveEnc_ptr);
    arithLenBeforeEncodingDFT = (ISACenc_obj->bitstr_obj.stream_index << 1) + (1-ISACenc_obj->bitstr_obj.full);
    status = WebRtcIsacfix_EncodeSpec(LP16a, LPandHP, &ISACenc_obj->bitstr_obj, AvgPitchGain_Q12);
    if((status <= -1) && (status != -ISAC_DISALLOWED_BITSTREAM_LENGTH)) /*LPandHP = HP16b*/
    {
      if (frame_mode == 1 && ISACenc_obj->frame_nb == 1)
      {
        // If this is the second 30ms of a 60ms frame reset this such that in the next call
        // encoder starts fresh.
        ISACenc_obj->frame_nb = 0;
      }
      return status;
    }
    iterCntr++;
  }

  if (frame_mode == 1 && ISACenc_obj->frame_nb == 0)
    /* i.e. 60 ms framesize and just processed the first 30ms, */
    /* go back to main function to buffer the other 30ms speech frame */
  {
    ISACenc_obj->frame_nb = 1;
    return 0;
  }
  else if (frame_mode == 1 && ISACenc_obj->frame_nb == 1)
  {
    ISACenc_obj->frame_nb = 0;
    /* also update the framelength for next packet, in Adaptive mode only */
    if (CodingMode == 0 && (ISACenc_obj->enforceFrameSize == 0)) {
      ISACenc_obj->new_framelength = WebRtcIsacfix_GetNewFrameLength(ISACenc_obj->BottleNeck,
                                                                     ISACenc_obj->current_framesamples);
    }
  }


  /* complete arithmetic coding */
  stream_length = WebRtcIsacfix_EncTerminate(&ISACenc_obj->bitstr_obj);
  /* can this be negative? */

  if(CodingMode == 0)
  {

    /* update rate model and get minimum number of bytes in this packet */
    MinBytes = WebRtcIsacfix_GetMinBytes(&ISACenc_obj->rate_data_obj, (int16_t) stream_length,
                                         ISACenc_obj->current_framesamples, ISACenc_obj->BottleNeck, ISACenc_obj->MaxDelay);

    /* if bitstream is too short, add garbage at the end */

    /* Store length of coded data */
    usefulstr_len = stream_length;

    /* Make sure MinBytes does not exceed packet size limit */
    if ((ISACenc_obj->frame_nb == 0) && (MinBytes > ISACenc_obj->payloadLimitBytes30)) {
      MinBytes = ISACenc_obj->payloadLimitBytes30;
    } else if ((ISACenc_obj->frame_nb == 1) && (MinBytes > ISACenc_obj->payloadLimitBytes60)) {
      MinBytes = ISACenc_obj->payloadLimitBytes60;
    }

    /* Make sure we don't allow more than 255 bytes of garbage data.
       We store the length of the garbage data in 8 bits in the bitstream,
       255 is the max garbage lenght we can signal using 8 bits. */
    if( MinBytes > usefulstr_len + 255 ) {
      MinBytes = usefulstr_len + 255;
    }

    /* Save data for creation of multiple bitstreams */
    if (ISACenc_obj->SaveEnc_ptr != NULL) {
      (ISACenc_obj->SaveEnc_ptr)->minBytes = MinBytes;
    }

    while (stream_length < MinBytes)
    {
      RTC_DCHECK_GE(stream_length, 0);
      if (stream_length & 0x0001){
        ISACenc_obj->bitstr_seed = WEBRTC_SPL_RAND( ISACenc_obj->bitstr_seed );
        ISACenc_obj->bitstr_obj.stream[stream_length / 2] |=
            (uint16_t)(ISACenc_obj->bitstr_seed & 0xFF);
      } else {
        ISACenc_obj->bitstr_seed = WEBRTC_SPL_RAND( ISACenc_obj->bitstr_seed );
        ISACenc_obj->bitstr_obj.stream[stream_length / 2] =
            ((uint16_t)ISACenc_obj->bitstr_seed << 8);
      }
      stream_length++;
    }

    /* to get the real stream_length, without garbage */
    if (usefulstr_len & 0x0001) {
      ISACenc_obj->bitstr_obj.stream[usefulstr_len>>1] &= 0xFF00;
      ISACenc_obj->bitstr_obj.stream[usefulstr_len>>1] += (MinBytes - usefulstr_len) & 0x00FF;
    }
    else {
      ISACenc_obj->bitstr_obj.stream[usefulstr_len>>1] &= 0x00FF;
      ISACenc_obj->bitstr_obj.stream[usefulstr_len >> 1] +=
          ((uint16_t)((MinBytes - usefulstr_len) & 0x00FF) << 8);
    }
  }
  else
  {
    /* update rate model */
    WebRtcIsacfix_UpdateRateModel(&ISACenc_obj->rate_data_obj, (int16_t) stream_length,
                                  ISACenc_obj->current_framesamples, ISACenc_obj->BottleNeck);
  }
  return stream_length;
}

/* This function is used to create a new bitstream with new BWE.
   The same data as previously encoded with the fucntion WebRtcIsacfix_EncodeImpl()
   is used. The data needed is taken from the struct, where it was stored
   when calling the encoder. */
int WebRtcIsacfix_EncodeStoredData(IsacFixEncoderInstance  *ISACenc_obj,
                                   int     BWnumber,
                                   float              scale)
{
  int ii;
  int status;
  int16_t BWno = (int16_t)BWnumber;
  int stream_length = 0;

  int16_t model;
  const uint16_t *Q_PitchGain_cdf_ptr[1];
  const uint16_t **cdf;
  const IsacSaveEncoderData *SaveEnc_str;
  int32_t tmpLPCcoeffs_g[KLT_ORDER_GAIN<<1];
  int16_t tmpLPCindex_g[KLT_ORDER_GAIN<<1];
  int16_t tmp_fre[FRAMESAMPLES];
  int16_t tmp_fim[FRAMESAMPLES];

  SaveEnc_str = ISACenc_obj->SaveEnc_ptr;

  /* Check if SaveEnc memory exists */
  if (SaveEnc_str == NULL) {
    return (-1);
  }

  /* Sanity Check - possible values for BWnumber is 0 - 23 */
  if ((BWnumber < 0) || (BWnumber > 23)) {
    return -ISAC_RANGE_ERROR_BW_ESTIMATOR;
  }

  /* reset bitstream */
  ISACenc_obj->bitstr_obj.W_upper = 0xFFFFFFFF;
  ISACenc_obj->bitstr_obj.streamval = 0;
  ISACenc_obj->bitstr_obj.stream_index = 0;
  ISACenc_obj->bitstr_obj.full = 1;

  /* encode frame length */
  status = WebRtcIsacfix_EncodeFrameLen(SaveEnc_str->framelength, &ISACenc_obj->bitstr_obj);
  if (status < 0) {
    /* Wrong frame size */
    return status;
  }

  /* encode bandwidth estimate */
  status = WebRtcIsacfix_EncodeReceiveBandwidth(&BWno, &ISACenc_obj->bitstr_obj);
  if (status < 0) {
    return status;
  }

  /* Transcoding                                                 */
  /* If scale < 1, rescale data to produce lower bitrate signal  */
  if ((0.0 < scale) && (scale < 1.0)) {
    /* Compensate LPC gain */
    for (ii = 0; ii < (KLT_ORDER_GAIN*(1+SaveEnc_str->startIdx)); ii++) {
      tmpLPCcoeffs_g[ii] = (int32_t) ((scale) * (float) SaveEnc_str->LPCcoeffs_g[ii]);
    }

    /* Scale DFT */
    for (ii = 0; ii < (FRAMESAMPLES_HALF*(1+SaveEnc_str->startIdx)); ii++) {
      tmp_fre[ii] = (int16_t) ((scale) * (float) SaveEnc_str->fre[ii]) ;
      tmp_fim[ii] = (int16_t) ((scale) * (float) SaveEnc_str->fim[ii]) ;
    }
  } else {
    for (ii = 0; ii < (KLT_ORDER_GAIN*(1+SaveEnc_str->startIdx)); ii++) {
      tmpLPCindex_g[ii] =  SaveEnc_str->LPCindex_g[ii];
    }

    for (ii = 0; ii < (FRAMESAMPLES_HALF*(1+SaveEnc_str->startIdx)); ii++) {
      tmp_fre[ii] = SaveEnc_str->fre[ii];
      tmp_fim[ii] = SaveEnc_str->fim[ii];
    }
  }

  /* Loop over number of 30 msec */
  for (ii = 0; ii <= SaveEnc_str->startIdx; ii++)
  {

    /* encode pitch gains */
    *Q_PitchGain_cdf_ptr = WebRtcIsacfix_kPitchGainCdf;
    status = WebRtcIsacfix_EncHistMulti(&ISACenc_obj->bitstr_obj, &SaveEnc_str->pitchGain_index[ii],
                                       Q_PitchGain_cdf_ptr, 1);
    if (status < 0) {
      return status;
    }

    /* entropy coding of quantization pitch lags */
    /* voicing classificiation */
    if (SaveEnc_str->meanGain[ii] <= 819) {
      cdf = WebRtcIsacfix_kPitchLagPtrLo;
    } else if (SaveEnc_str->meanGain[ii] <= 1638) {
      cdf = WebRtcIsacfix_kPitchLagPtrMid;
    } else {
      cdf = WebRtcIsacfix_kPitchLagPtrHi;
    }
    status = WebRtcIsacfix_EncHistMulti(&ISACenc_obj->bitstr_obj,
                                       &SaveEnc_str->pitchIndex[PITCH_SUBFRAMES*ii], cdf, PITCH_SUBFRAMES);
    if (status < 0) {
      return status;
    }

    /* LPC */
    /* entropy coding of model number */
    model = 0;
    status = WebRtcIsacfix_EncHistMulti(&ISACenc_obj->bitstr_obj,  &model,
                                       WebRtcIsacfix_kModelCdfPtr, 1);
    if (status < 0) {
      return status;
    }

    /* entropy coding of quantization indices - LPC shape only */
    status = WebRtcIsacfix_EncHistMulti(&ISACenc_obj->bitstr_obj, &SaveEnc_str->LPCindex_s[KLT_ORDER_SHAPE*ii],
                                       WebRtcIsacfix_kCdfShapePtr[0], KLT_ORDER_SHAPE);
    if (status < 0) {
      return status;
    }

    /* If transcoding, get new LPC gain indices */
    if (scale < 1.0) {
      WebRtcIsacfix_TranscodeLpcCoef(&tmpLPCcoeffs_g[KLT_ORDER_GAIN*ii], &tmpLPCindex_g[KLT_ORDER_GAIN*ii]);
    }

    /* entropy coding of quantization indices - LPC gain */
    status = WebRtcIsacfix_EncHistMulti(&ISACenc_obj->bitstr_obj, &tmpLPCindex_g[KLT_ORDER_GAIN*ii],
                                       WebRtcIsacfix_kCdfGainPtr[0], KLT_ORDER_GAIN);
    if (status < 0) {
      return status;
    }

    /* quantization and lossless coding */
    status = WebRtcIsacfix_EncodeSpec(&tmp_fre[ii*FRAMESAMPLES_HALF], &tmp_fim[ii*FRAMESAMPLES_HALF],
                                      &ISACenc_obj->bitstr_obj, SaveEnc_str->AvgPitchGain[ii]);
    if (status < 0) {
      return status;
    }
  }

  /* complete arithmetic coding */
  stream_length = WebRtcIsacfix_EncTerminate(&ISACenc_obj->bitstr_obj);

  return stream_length;
}
