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

 WebRtcIlbcfix_EnhancerInterface.c

******************************************************************/

#include <string.h>

#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "modules/audio_coding/codecs/ilbc/constants.h"
#include "modules/audio_coding/codecs/ilbc/xcorr_coef.h"
#include "modules/audio_coding/codecs/ilbc/enhancer.h"
#include "modules/audio_coding/codecs/ilbc/hp_output.h"



/*----------------------------------------------------------------*
 * interface for enhancer
 *---------------------------------------------------------------*/

size_t  // (o) Estimated lag in end of in[]
    WebRtcIlbcfix_EnhancerInterface(
        int16_t* out,                 // (o) enhanced signal
        const int16_t* in,            // (i) unenhanced signal
        IlbcDecoder* iLBCdec_inst) {  // (i) buffers etc
  size_t iblock;
  size_t lag=20, tlag=20;
  size_t inLen=iLBCdec_inst->blockl+120;
  int16_t scale, scale1;
  size_t plc_blockl;
  int16_t *enh_buf;
  size_t *enh_period;
  int32_t tmp1, tmp2, max;
  size_t new_blocks;
  int16_t *enh_bufPtr1;
  size_t i;
  size_t k;
  int16_t EnChange;
  int16_t SqrtEnChange;
  int16_t inc;
  int16_t win;
  int16_t *tmpW16ptr;
  size_t startPos;
  int16_t *plc_pred;
  const int16_t *target, *regressor;
  int16_t max16;
  int shifts;
  int32_t ener;
  int16_t enerSh;
  int16_t corrSh;
  size_t ind;
  int16_t sh;
  size_t start, stop;
  /* Stack based */
  int16_t totsh[3];
  int16_t downsampled[(BLOCKL_MAX+120)>>1]; /* length 180 */
  int32_t corr32[50];
  int32_t corrmax[3];
  int16_t corr16[3];
  int16_t en16[3];
  size_t lagmax[3];

  plc_pred = downsampled; /* Reuse memory since plc_pred[ENH_BLOCKL] and
                              downsampled are non overlapping */
  enh_buf=iLBCdec_inst->enh_buf;
  enh_period=iLBCdec_inst->enh_period;

  /* Copy in the new data into the enhancer buffer */
  memmove(enh_buf, &enh_buf[iLBCdec_inst->blockl],
          (ENH_BUFL - iLBCdec_inst->blockl) * sizeof(*enh_buf));

  WEBRTC_SPL_MEMCPY_W16(&enh_buf[ENH_BUFL-iLBCdec_inst->blockl], in,
                        iLBCdec_inst->blockl);

  /* Set variables that are dependent on frame size */
  if (iLBCdec_inst->mode==30) {
    plc_blockl=ENH_BLOCKL;
    new_blocks=3;
    startPos=320;  /* Start position for enhancement
                     (640-new_blocks*ENH_BLOCKL-80) */
  } else {
    plc_blockl=40;
    new_blocks=2;
    startPos=440;  /* Start position for enhancement
                    (640-new_blocks*ENH_BLOCKL-40) */
  }

  /* Update the pitch prediction for each enhancer block, move the old ones */
  memmove(enh_period, &enh_period[new_blocks],
          (ENH_NBLOCKS_TOT - new_blocks) * sizeof(*enh_period));

  WebRtcSpl_DownsampleFast(
      enh_buf+ENH_BUFL-inLen,    /* Input samples */
      inLen + ENH_BUFL_FILTEROVERHEAD,
      downsampled,
      inLen / 2,
      (int16_t*)WebRtcIlbcfix_kLpFiltCoefs,  /* Coefficients in Q12 */
      FILTERORDER_DS_PLUS1,    /* Length of filter (order-1) */
      FACTOR_DS,
      DELAY_DS);

  /* Estimate the pitch in the down sampled domain. */
  for(iblock = 0; iblock<new_blocks; iblock++){

    /* references */
    target = downsampled + 60 + iblock * ENH_BLOCKL_HALF;
    regressor = target - 10;

    /* scaling */
    max16 = WebRtcSpl_MaxAbsValueW16(&regressor[-50], ENH_BLOCKL_HALF + 50 - 1);
    shifts = WebRtcSpl_GetSizeInBits((uint32_t)(max16 * max16)) - 25;
    shifts = WEBRTC_SPL_MAX(0, shifts);

    /* compute cross correlation */
    WebRtcSpl_CrossCorrelation(corr32, target, regressor, ENH_BLOCKL_HALF, 50,
                               shifts, -1);

    /* Find 3 highest correlations that should be compared for the
       highest (corr*corr)/ener */

    for (i=0;i<2;i++) {
      lagmax[i] = WebRtcSpl_MaxIndexW32(corr32, 50);
      corrmax[i] = corr32[lagmax[i]];
      start = WEBRTC_SPL_MAX(2, lagmax[i]) - 2;
      stop = WEBRTC_SPL_MIN(47, lagmax[i]) + 2;
      for (k = start; k <= stop; k++) {
        corr32[k] = 0;
      }
    }
    lagmax[2] = WebRtcSpl_MaxIndexW32(corr32, 50);
    corrmax[2] = corr32[lagmax[2]];

    /* Calculate normalized corr^2 and ener */
    for (i=0;i<3;i++) {
      corrSh = 15-WebRtcSpl_GetSizeInBits(corrmax[i]);
      ener = WebRtcSpl_DotProductWithScale(regressor - lagmax[i],
                                           regressor - lagmax[i],
                                           ENH_BLOCKL_HALF, shifts);
      enerSh = 15-WebRtcSpl_GetSizeInBits(ener);
      corr16[i] = (int16_t)WEBRTC_SPL_SHIFT_W32(corrmax[i], corrSh);
      corr16[i] = (int16_t)((corr16[i] * corr16[i]) >> 16);
      en16[i] = (int16_t)WEBRTC_SPL_SHIFT_W32(ener, enerSh);
      totsh[i] = enerSh - 2 * corrSh;
    }

    /* Compare lagmax[0..3] for the (corr^2)/ener criteria */
    ind = 0;
    for (i=1; i<3; i++) {
      if (totsh[ind] > totsh[i]) {
        sh = WEBRTC_SPL_MIN(31, totsh[ind]-totsh[i]);
        if (corr16[ind] * en16[i] < (corr16[i] * en16[ind]) >> sh) {
          ind = i;
        }
      } else {
        sh = WEBRTC_SPL_MIN(31, totsh[i]-totsh[ind]);
        if ((corr16[ind] * en16[i]) >> sh < corr16[i] * en16[ind]) {
          ind = i;
        }
      }
    }

    lag = lagmax[ind] + 10;

    /* Store the estimated lag in the non-downsampled domain */
    enh_period[ENH_NBLOCKS_TOT - new_blocks + iblock] = lag * 8;

    /* Store the estimated lag for backward PLC */
    if (iLBCdec_inst->prev_enh_pl==1) {
      if (!iblock) {
        tlag = lag * 2;
      }
    } else {
      if (iblock==1) {
        tlag = lag * 2;
      }
    }

    lag *= 2;
  }

  if ((iLBCdec_inst->prev_enh_pl==1)||(iLBCdec_inst->prev_enh_pl==2)) {

    /* Calculate the best lag of the new frame
       This is used to interpolate backwards and mix with the PLC'd data
    */

    /* references */
    target=in;
    regressor=in+tlag-1;

    /* scaling */
    max16 = WebRtcSpl_MaxAbsValueW16(regressor, plc_blockl + 3 - 1);
    if (max16>5000)
      shifts=2;
    else
      shifts=0;

    /* compute cross correlation */
    WebRtcSpl_CrossCorrelation(corr32, target, regressor, plc_blockl, 3, shifts,
                               1);

    /* find lag */
    lag=WebRtcSpl_MaxIndexW32(corr32, 3);
    lag+=tlag-1;

    /* Copy the backward PLC to plc_pred */

    if (iLBCdec_inst->prev_enh_pl==1) {
      if (lag>plc_blockl) {
        WEBRTC_SPL_MEMCPY_W16(plc_pred, &in[lag-plc_blockl], plc_blockl);
      } else {
        WEBRTC_SPL_MEMCPY_W16(&plc_pred[plc_blockl-lag], in, lag);
        WEBRTC_SPL_MEMCPY_W16(
            plc_pred, &enh_buf[ENH_BUFL-iLBCdec_inst->blockl-plc_blockl+lag],
            (plc_blockl-lag));
      }
    } else {
      size_t pos;

      pos = plc_blockl;

      while (lag<pos) {
        WEBRTC_SPL_MEMCPY_W16(&plc_pred[pos-lag], in, lag);
        pos = pos - lag;
      }
      WEBRTC_SPL_MEMCPY_W16(plc_pred, &in[lag-pos], pos);

    }

    if (iLBCdec_inst->prev_enh_pl==1) {
      /* limit energy change
         if energy in backward PLC is more than 4 times higher than the forward
         PLC, then reduce the energy in the backward PLC vector:
         sample 1...len-16 set energy of the to 4 times forward PLC
         sample len-15..len interpolate between 4 times fw PLC and bw PLC energy

         Note: Compared to floating point code there is a slight change,
         the window is 16 samples long instead of 10 samples to simplify the
         calculations
      */

      max=WebRtcSpl_MaxAbsValueW16(
          &enh_buf[ENH_BUFL-iLBCdec_inst->blockl-plc_blockl], plc_blockl);
      max16=WebRtcSpl_MaxAbsValueW16(plc_pred, plc_blockl);
      max = WEBRTC_SPL_MAX(max, max16);
      scale=22-(int16_t)WebRtcSpl_NormW32(max);
      scale=WEBRTC_SPL_MAX(scale,0);

      tmp2 = WebRtcSpl_DotProductWithScale(
          &enh_buf[ENH_BUFL-iLBCdec_inst->blockl-plc_blockl],
          &enh_buf[ENH_BUFL-iLBCdec_inst->blockl-plc_blockl],
          plc_blockl, scale);
      tmp1 = WebRtcSpl_DotProductWithScale(plc_pred, plc_pred,
                                           plc_blockl, scale);

      /* Check the energy difference */
      if ((tmp1>0)&&((tmp1>>2)>tmp2)) {
        /* EnChange is now guaranteed to be <0.5
           Calculate EnChange=tmp2/tmp1 in Q16
        */

        scale1=(int16_t)WebRtcSpl_NormW32(tmp1);
        tmp1=WEBRTC_SPL_SHIFT_W32(tmp1, (scale1-16)); /* using 15 bits */

        tmp2=WEBRTC_SPL_SHIFT_W32(tmp2, (scale1));
        EnChange = (int16_t)WebRtcSpl_DivW32W16(tmp2,
                                                      (int16_t)tmp1);

        /* Calculate the Sqrt of the energy in Q15 ((14+16)/2) */
        SqrtEnChange = (int16_t)WebRtcSpl_SqrtFloor(EnChange << 14);


        /* Multiply first part of vector with 2*SqrtEnChange */
        WebRtcSpl_ScaleVector(plc_pred, plc_pred, SqrtEnChange, plc_blockl-16,
                              14);

        /* Calculate increase parameter for window part (16 last samples) */
        /* (1-2*SqrtEnChange)/16 in Q15 */
        inc = 2048 - (SqrtEnChange >> 3);

        win=0;
        tmpW16ptr=&plc_pred[plc_blockl-16];

        for (i=16;i>0;i--) {
          *tmpW16ptr = (int16_t)(
              (*tmpW16ptr * (SqrtEnChange + (win >> 1))) >> 14);
          /* multiply by (2.0*SqrtEnChange+win) */

          win += inc;
          tmpW16ptr++;
        }
      }

      /* Make the linear interpolation between the forward PLC'd data
         and the backward PLC'd data (from the new frame)
      */

      if (plc_blockl==40) {
        inc=400; /* 1/41 in Q14 */
      } else { /* plc_blockl==80 */
        inc=202; /* 1/81 in Q14 */
      }
      win=0;
      enh_bufPtr1=&enh_buf[ENH_BUFL-1-iLBCdec_inst->blockl];
      for (i=0; i<plc_blockl; i++) {
        win+=inc;
        *enh_bufPtr1 = (int16_t)((*enh_bufPtr1 * win) >> 14);
        *enh_bufPtr1 += (int16_t)(
            ((16384 - win) * plc_pred[plc_blockl - 1 - i]) >> 14);
        enh_bufPtr1--;
      }
    } else {
      int16_t *synt = &downsampled[LPC_FILTERORDER];

      enh_bufPtr1=&enh_buf[ENH_BUFL-iLBCdec_inst->blockl-plc_blockl];
      WEBRTC_SPL_MEMCPY_W16(enh_bufPtr1, plc_pred, plc_blockl);

      /* Clear fileter memory */
      WebRtcSpl_MemSetW16(iLBCdec_inst->syntMem, 0, LPC_FILTERORDER);
      WebRtcSpl_MemSetW16(iLBCdec_inst->hpimemy, 0, 4);
      WebRtcSpl_MemSetW16(iLBCdec_inst->hpimemx, 0, 2);

      /* Initialize filter memory by filtering through 2 lags */
      WEBRTC_SPL_MEMCPY_W16(&synt[-LPC_FILTERORDER], iLBCdec_inst->syntMem,
                            LPC_FILTERORDER);
      WebRtcSpl_FilterARFastQ12(
          enh_bufPtr1,
          synt,
          &iLBCdec_inst->old_syntdenum[
              (iLBCdec_inst->nsub-1)*(LPC_FILTERORDER+1)],
          LPC_FILTERORDER+1, lag);

      WEBRTC_SPL_MEMCPY_W16(&synt[-LPC_FILTERORDER], &synt[lag-LPC_FILTERORDER],
                            LPC_FILTERORDER);
      WebRtcIlbcfix_HpOutput(synt, (int16_t*)WebRtcIlbcfix_kHpOutCoefs,
                             iLBCdec_inst->hpimemy, iLBCdec_inst->hpimemx,
                             lag);
      WebRtcSpl_FilterARFastQ12(
          enh_bufPtr1, synt,
          &iLBCdec_inst->old_syntdenum[
              (iLBCdec_inst->nsub-1)*(LPC_FILTERORDER+1)],
          LPC_FILTERORDER+1, lag);

      WEBRTC_SPL_MEMCPY_W16(iLBCdec_inst->syntMem, &synt[lag-LPC_FILTERORDER],
                            LPC_FILTERORDER);
      WebRtcIlbcfix_HpOutput(synt, (int16_t*)WebRtcIlbcfix_kHpOutCoefs,
                             iLBCdec_inst->hpimemy, iLBCdec_inst->hpimemx,
                             lag);
    }
  }


  /* Perform enhancement block by block */

  for (iblock = 0; iblock<new_blocks; iblock++) {
    WebRtcIlbcfix_Enhancer(out + iblock * ENH_BLOCKL,
                           enh_buf,
                           ENH_BUFL,
                           iblock * ENH_BLOCKL + startPos,
                           enh_period,
                           WebRtcIlbcfix_kEnhPlocs, ENH_NBLOCKS_TOT);
  }

  return (lag);
}
