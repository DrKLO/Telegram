/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <math.h>
#include <string.h>

#include "modules/audio_coding/codecs/isac/main/source/lpc_analysis.h"
#include "modules/audio_coding/codecs/isac/main/source/settings.h"
#include "modules/audio_coding/codecs/isac/main/source/codec.h"
#include "modules/audio_coding/codecs/isac/main/source/entropy_coding.h"
#include "modules/audio_coding/codecs/isac/main/source/filter_functions.h"
#include "modules/audio_coding/codecs/isac/main/source/isac_vad.h"

/* window */
/* Matlab generation code:
 *  t = (1:256)/257; r = 1-(1-t).^.45; w = sin(r*pi).^3; w = w/sum(w); plot((1:256)/8, w); grid;
 *  for k=1:16, fprintf(1, '%.8f, ', w(k*16 + (-15:0))); fprintf(1, '\n'); end
 */
static const double kLpcCorrWindow[WINLEN] = {
  0.00000000, 0.00000001, 0.00000004, 0.00000010, 0.00000020,
  0.00000035, 0.00000055, 0.00000083, 0.00000118, 0.00000163,
  0.00000218, 0.00000283, 0.00000361, 0.00000453, 0.00000558, 0.00000679,
  0.00000817, 0.00000973, 0.00001147, 0.00001342, 0.00001558,
  0.00001796, 0.00002058, 0.00002344, 0.00002657, 0.00002997,
  0.00003365, 0.00003762, 0.00004190, 0.00004651, 0.00005144, 0.00005673,
  0.00006236, 0.00006837, 0.00007476, 0.00008155, 0.00008875,
  0.00009636, 0.00010441, 0.00011290, 0.00012186, 0.00013128,
  0.00014119, 0.00015160, 0.00016252, 0.00017396, 0.00018594, 0.00019846,
  0.00021155, 0.00022521, 0.00023946, 0.00025432, 0.00026978,
  0.00028587, 0.00030260, 0.00031998, 0.00033802, 0.00035674,
  0.00037615, 0.00039626, 0.00041708, 0.00043863, 0.00046092, 0.00048396,
  0.00050775, 0.00053233, 0.00055768, 0.00058384, 0.00061080,
  0.00063858, 0.00066720, 0.00069665, 0.00072696, 0.00075813,
  0.00079017, 0.00082310, 0.00085692, 0.00089164, 0.00092728, 0.00096384,
  0.00100133, 0.00103976, 0.00107914, 0.00111947, 0.00116077,
  0.00120304, 0.00124630, 0.00129053, 0.00133577, 0.00138200,
  0.00142924, 0.00147749, 0.00152676, 0.00157705, 0.00162836, 0.00168070,
  0.00173408, 0.00178850, 0.00184395, 0.00190045, 0.00195799,
  0.00201658, 0.00207621, 0.00213688, 0.00219860, 0.00226137,
  0.00232518, 0.00239003, 0.00245591, 0.00252284, 0.00259079, 0.00265977,
  0.00272977, 0.00280078, 0.00287280, 0.00294582, 0.00301984,
  0.00309484, 0.00317081, 0.00324774, 0.00332563, 0.00340446,
  0.00348421, 0.00356488, 0.00364644, 0.00372889, 0.00381220, 0.00389636,
  0.00398135, 0.00406715, 0.00415374, 0.00424109, 0.00432920,
  0.00441802, 0.00450754, 0.00459773, 0.00468857, 0.00478001,
  0.00487205, 0.00496464, 0.00505775, 0.00515136, 0.00524542, 0.00533990,
  0.00543476, 0.00552997, 0.00562548, 0.00572125, 0.00581725,
  0.00591342, 0.00600973, 0.00610612, 0.00620254, 0.00629895,
  0.00639530, 0.00649153, 0.00658758, 0.00668341, 0.00677894, 0.00687413,
  0.00696891, 0.00706322, 0.00715699, 0.00725016, 0.00734266,
  0.00743441, 0.00752535, 0.00761540, 0.00770449, 0.00779254,
  0.00787947, 0.00796519, 0.00804963, 0.00813270, 0.00821431, 0.00829437,
  0.00837280, 0.00844949, 0.00852436, 0.00859730, 0.00866822,
  0.00873701, 0.00880358, 0.00886781, 0.00892960, 0.00898884,
  0.00904542, 0.00909923, 0.00915014, 0.00919805, 0.00924283, 0.00928436,
  0.00932252, 0.00935718, 0.00938821, 0.00941550, 0.00943890,
  0.00945828, 0.00947351, 0.00948446, 0.00949098, 0.00949294,
  0.00949020, 0.00948262, 0.00947005, 0.00945235, 0.00942938, 0.00940099,
  0.00936704, 0.00932738, 0.00928186, 0.00923034, 0.00917268,
  0.00910872, 0.00903832, 0.00896134, 0.00887763, 0.00878706,
  0.00868949, 0.00858478, 0.00847280, 0.00835343, 0.00822653, 0.00809199,
  0.00794970, 0.00779956, 0.00764145, 0.00747530, 0.00730103,
  0.00711857, 0.00692787, 0.00672888, 0.00652158, 0.00630597,
  0.00608208, 0.00584994, 0.00560962, 0.00536124, 0.00510493, 0.00484089,
  0.00456935, 0.00429062, 0.00400505, 0.00371310, 0.00341532,
  0.00311238, 0.00280511, 0.00249452, 0.00218184, 0.00186864,
  0.00155690, 0.00124918, 0.00094895, 0.00066112, 0.00039320, 0.00015881
};

static void WebRtcIsac_GetVars(const double* input,
                               const int16_t* pitchGains_Q12,
                               double* oldEnergy,
                               double* varscale) {
  double nrg[4], chng, pg;
  int k;

  double pitchGains[4]={0,0,0,0};;

  /* Calculate energies of first and second frame halfs */
  nrg[0] = 0.0001;
  for (k = QLOOKAHEAD/2; k < (FRAMESAMPLES_QUARTER + QLOOKAHEAD) / 2; k++) {
    nrg[0] += input[k]*input[k];
  }
  nrg[1] = 0.0001;
  for ( ; k < (FRAMESAMPLES_HALF + QLOOKAHEAD) / 2; k++) {
    nrg[1] += input[k]*input[k];
  }
  nrg[2] = 0.0001;
  for ( ; k < (FRAMESAMPLES*3/4 + QLOOKAHEAD) / 2; k++) {
    nrg[2] += input[k]*input[k];
  }
  nrg[3] = 0.0001;
  for ( ; k < (FRAMESAMPLES + QLOOKAHEAD) / 2; k++) {
    nrg[3] += input[k]*input[k];
  }

  /* Calculate average level change */
  chng = 0.25 * (fabs(10.0 * log10(nrg[3] / nrg[2])) +
                 fabs(10.0 * log10(nrg[2] / nrg[1])) +
                 fabs(10.0 * log10(nrg[1] / nrg[0])) +
                 fabs(10.0 * log10(nrg[0] / *oldEnergy)));


  /* Find average pitch gain */
  pg = 0.0;
  for (k=0; k<4; k++)
  {
    pitchGains[k] = ((float)pitchGains_Q12[k])/4096;
    pg += pitchGains[k];
  }
  pg *= 0.25;

  /* If pitch gain is low and energy constant - increase noise level*/
  /* Matlab code:
     pg = 0:.01:.45; plot(pg, 0.0 + 1.0 * exp( -1.0 * exp(-200.0 * pg.*pg.*pg) / (1.0 + 0.4 * 0) ))
  */
  *varscale = 0.0 + 1.0 * exp( -1.4 * exp(-200.0 * pg*pg*pg) / (1.0 + 0.4 * chng) );

  *oldEnergy = nrg[3];
}

static void WebRtcIsac_GetVarsUB(const double* input,
                                 double* oldEnergy,
                                 double* varscale) {
  double nrg[4], chng;
  int k;

  /* Calculate energies of first and second frame halfs */
  nrg[0] = 0.0001;
  for (k = 0; k < (FRAMESAMPLES_QUARTER) / 2; k++) {
    nrg[0] += input[k]*input[k];
  }
  nrg[1] = 0.0001;
  for ( ; k < (FRAMESAMPLES_HALF) / 2; k++) {
    nrg[1] += input[k]*input[k];
  }
  nrg[2] = 0.0001;
  for ( ; k < (FRAMESAMPLES*3/4) / 2; k++) {
    nrg[2] += input[k]*input[k];
  }
  nrg[3] = 0.0001;
  for ( ; k < (FRAMESAMPLES) / 2; k++) {
    nrg[3] += input[k]*input[k];
  }

  /* Calculate average level change */
  chng = 0.25 * (fabs(10.0 * log10(nrg[3] / nrg[2])) +
                 fabs(10.0 * log10(nrg[2] / nrg[1])) +
                 fabs(10.0 * log10(nrg[1] / nrg[0])) +
                 fabs(10.0 * log10(nrg[0] / *oldEnergy)));


  /* If pitch gain is low and energy constant - increase noise level*/
  /* Matlab code:
     pg = 0:.01:.45; plot(pg, 0.0 + 1.0 * exp( -1.0 * exp(-200.0 * pg.*pg.*pg) / (1.0 + 0.4 * 0) ))
  */
  *varscale = exp( -1.4 / (1.0 + 0.4 * chng) );

  *oldEnergy = nrg[3];
}

void WebRtcIsac_GetLpcCoefLb(double *inLo, double *inHi, MaskFiltstr *maskdata,
                             double signal_noise_ratio, const int16_t *pitchGains_Q12,
                             double *lo_coeff, double *hi_coeff)
{
  int k, n, j, pos1, pos2;
  double varscale;

  double DataLo[WINLEN], DataHi[WINLEN];
  double corrlo[ORDERLO+2], corrlo2[ORDERLO+1];
  double corrhi[ORDERHI+1];
  double k_veclo[ORDERLO], k_vechi[ORDERHI];

  double a_LO[ORDERLO+1], a_HI[ORDERHI+1];
  double tmp, res_nrg;

  double FwdA, FwdB;

  /* hearing threshold level in dB; higher value gives more noise */
  const double HearThresOffset = -28.0;

  /* bandwdith expansion factors for low- and high band */
  const double gammaLo = 0.9;
  const double gammaHi = 0.8;

  /* less-noise-at-low-frequencies factor */
  double aa;


  /* convert from dB to signal level */
  const double H_T_H = pow(10.0, 0.05 * HearThresOffset);
  double S_N_R = pow(10.0, 0.05 * signal_noise_ratio) / 3.46;    /* divide by sqrt(12) */

  /* change quallevel depending on pitch gains and level fluctuations */
  WebRtcIsac_GetVars(inLo, pitchGains_Q12, &(maskdata->OldEnergy), &varscale);

  /* less-noise-at-low-frequencies factor */
  aa = 0.35 * (0.5 + 0.5 * varscale);

  /* replace data in buffer by new look-ahead data */
  for (pos1 = 0; pos1 < QLOOKAHEAD; pos1++)
    maskdata->DataBufferLo[pos1 + WINLEN - QLOOKAHEAD] = inLo[pos1];

  for (k = 0; k < SUBFRAMES; k++) {

    /* Update input buffer and multiply signal with window */
    for (pos1 = 0; pos1 < WINLEN - UPDATE/2; pos1++) {
      maskdata->DataBufferLo[pos1] = maskdata->DataBufferLo[pos1 + UPDATE/2];
      maskdata->DataBufferHi[pos1] = maskdata->DataBufferHi[pos1 + UPDATE/2];
      DataLo[pos1] = maskdata->DataBufferLo[pos1] * kLpcCorrWindow[pos1];
      DataHi[pos1] = maskdata->DataBufferHi[pos1] * kLpcCorrWindow[pos1];
    }
    pos2 = k * UPDATE/2;
    for (n = 0; n < UPDATE/2; n++, pos1++) {
      maskdata->DataBufferLo[pos1] = inLo[QLOOKAHEAD + pos2];
      maskdata->DataBufferHi[pos1] = inHi[pos2++];
      DataLo[pos1] = maskdata->DataBufferLo[pos1] * kLpcCorrWindow[pos1];
      DataHi[pos1] = maskdata->DataBufferHi[pos1] * kLpcCorrWindow[pos1];
    }

    /* Get correlation coefficients */
    WebRtcIsac_AutoCorr(corrlo, DataLo, WINLEN, ORDERLO+1); /* computing autocorrelation */
    WebRtcIsac_AutoCorr(corrhi, DataHi, WINLEN, ORDERHI);


    /* less noise for lower frequencies, by filtering/scaling autocorrelation sequences */
    corrlo2[0] = (1.0+aa*aa) * corrlo[0] - 2.0*aa * corrlo[1];
    tmp = (1.0 + aa*aa);
    for (n = 1; n <= ORDERLO; n++) {
      corrlo2[n] = tmp * corrlo[n] - aa * (corrlo[n-1] + corrlo[n+1]);
    }
    tmp = (1.0+aa) * (1.0+aa);
    for (n = 0; n <= ORDERHI; n++) {
      corrhi[n] = tmp * corrhi[n];
    }

    /* add white noise floor */
    corrlo2[0] += 1e-6;
    corrhi[0] += 1e-6;


    FwdA = 0.01;
    FwdB = 0.01;

    /* recursive filtering of correlation over subframes */
    for (n = 0; n <= ORDERLO; n++) {
      maskdata->CorrBufLo[n] = FwdA * maskdata->CorrBufLo[n] + corrlo2[n];
      corrlo2[n] = ((1.0-FwdA)*FwdB) * maskdata->CorrBufLo[n] + (1.0-FwdB) * corrlo2[n];
    }
    for (n = 0; n <= ORDERHI; n++) {
      maskdata->CorrBufHi[n] = FwdA * maskdata->CorrBufHi[n] + corrhi[n];
      corrhi[n] = ((1.0-FwdA)*FwdB) * maskdata->CorrBufHi[n] + (1.0-FwdB) * corrhi[n];
    }

    /* compute prediction coefficients */
    WebRtcIsac_LevDurb(a_LO, k_veclo, corrlo2, ORDERLO);
    WebRtcIsac_LevDurb(a_HI, k_vechi, corrhi, ORDERHI);

    /* bandwidth expansion */
    tmp = gammaLo;
    for (n = 1; n <= ORDERLO; n++) {
      a_LO[n] *= tmp;
      tmp *= gammaLo;
    }

    /* residual energy */
    res_nrg = 0.0;
    for (j = 0; j <= ORDERLO; j++) {
      for (n = 0; n <= j; n++) {
        res_nrg += a_LO[j] * corrlo2[j-n] * a_LO[n];
      }
      for (n = j+1; n <= ORDERLO; n++) {
        res_nrg += a_LO[j] * corrlo2[n-j] * a_LO[n];
      }
    }

    /* add hearing threshold and compute the gain */
    *lo_coeff++ = S_N_R / (sqrt(res_nrg) / varscale + H_T_H);

    /* copy coefficients to output array */
    for (n = 1; n <= ORDERLO; n++) {
      *lo_coeff++ = a_LO[n];
    }


    /* bandwidth expansion */
    tmp = gammaHi;
    for (n = 1; n <= ORDERHI; n++) {
      a_HI[n] *= tmp;
      tmp *= gammaHi;
    }

    /* residual energy */
    res_nrg = 0.0;
    for (j = 0; j <= ORDERHI; j++) {
      for (n = 0; n <= j; n++) {
        res_nrg += a_HI[j] * corrhi[j-n] * a_HI[n];
      }
      for (n = j+1; n <= ORDERHI; n++) {
        res_nrg += a_HI[j] * corrhi[n-j] * a_HI[n];
      }
    }

    /* add hearing threshold and compute of the gain */
    *hi_coeff++ = S_N_R / (sqrt(res_nrg) / varscale + H_T_H);

    /* copy coefficients to output array */
    for (n = 1; n <= ORDERHI; n++) {
      *hi_coeff++ = a_HI[n];
    }
  }
}



/******************************************************************************
 * WebRtcIsac_GetLpcCoefUb()
 *
 * Compute LP coefficients and correlation coefficients. At 12 kHz LP
 * coefficients of the first and the last sub-frame is computed. At 16 kHz
 * LP coefficients of 4th, 8th and 12th sub-frames are computed. We always
 * compute correlation coefficients of all sub-frames.
 *
 * Inputs:
 *       -inSignal           : Input signal
 *       -maskdata           : a structure keeping signal from previous frame.
 *       -bandwidth          : specifies if the codec is in 0-16 kHz mode or
 *                             0-12 kHz mode.
 *
 * Outputs:
 *       -lpCoeff            : pointer to a buffer where A-polynomials are
 *                             written to (first coeff is 1 and it is not
 *                             written)
 *       -corrMat            : a matrix where correlation coefficients of each
 *                             sub-frame are written to one row.
 *       -varscale           : a scale used to compute LPC gains.
 */
void
WebRtcIsac_GetLpcCoefUb(
    double*      inSignal,
    MaskFiltstr* maskdata,
    double*      lpCoeff,
    double       corrMat[][UB_LPC_ORDER + 1],
    double*      varscale,
    int16_t  bandwidth)
{
  int frameCntr, activeFrameCntr, n, pos1, pos2;
  int16_t criterion1;
  int16_t criterion2;
  int16_t numSubFrames = SUBFRAMES * (1 + (bandwidth == isac16kHz));
  double data[WINLEN];
  double corrSubFrame[UB_LPC_ORDER+2];
  double reflecCoeff[UB_LPC_ORDER];

  double aPolynom[UB_LPC_ORDER+1];
  double tmp;

  /* bandwdith expansion factors */
  const double gamma = 0.9;

  /* change quallevel depending on pitch gains and level fluctuations */
  WebRtcIsac_GetVarsUB(inSignal, &(maskdata->OldEnergy), varscale);

  /* replace data in buffer by new look-ahead data */
  for(frameCntr = 0, activeFrameCntr = 0; frameCntr < numSubFrames;
      frameCntr++)
  {
    if(frameCntr == SUBFRAMES)
    {
      // we are in 16 kHz
      varscale++;
      WebRtcIsac_GetVarsUB(&inSignal[FRAMESAMPLES_HALF],
                          &(maskdata->OldEnergy), varscale);
    }
    /* Update input buffer and multiply signal with window */
    for(pos1 = 0; pos1 < WINLEN - UPDATE/2; pos1++)
    {
      maskdata->DataBufferLo[pos1] = maskdata->DataBufferLo[pos1 +
                                                            UPDATE/2];
      data[pos1] = maskdata->DataBufferLo[pos1] * kLpcCorrWindow[pos1];
    }
    pos2 = frameCntr * UPDATE/2;
    for(n = 0; n < UPDATE/2; n++, pos1++, pos2++)
    {
      maskdata->DataBufferLo[pos1] = inSignal[pos2];
      data[pos1] = maskdata->DataBufferLo[pos1] * kLpcCorrWindow[pos1];
    }

    /* Get correlation coefficients */
    /* computing autocorrelation    */
    WebRtcIsac_AutoCorr(corrSubFrame, data, WINLEN, UB_LPC_ORDER+1);
    memcpy(corrMat[frameCntr], corrSubFrame,
           (UB_LPC_ORDER+1)*sizeof(double));

    criterion1 = ((frameCntr == 0) || (frameCntr == (SUBFRAMES - 1))) &&
        (bandwidth == isac12kHz);
    criterion2 = (((frameCntr+1) % 4) == 0) &&
        (bandwidth == isac16kHz);
    if(criterion1 || criterion2)
    {
      /* add noise */
      corrSubFrame[0] += 1e-6;
      /* compute prediction coefficients */
      WebRtcIsac_LevDurb(aPolynom, reflecCoeff, corrSubFrame,
                        UB_LPC_ORDER);

      /* bandwidth expansion */
      tmp = gamma;
      for (n = 1; n <= UB_LPC_ORDER; n++)
      {
        *lpCoeff++ = aPolynom[n] * tmp;
        tmp *= gamma;
      }
      activeFrameCntr++;
    }
  }
}



/******************************************************************************
 * WebRtcIsac_GetLpcGain()
 *
 * Compute the LPC gains for each sub-frame, given the LPC of each sub-frame
 * and the corresponding correlation coefficients.
 *
 * Inputs:
 *       -signal_noise_ratio : the desired SNR in dB.
 *       -numVecs            : number of sub-frames
 *       -corrMat             : a matrix of correlation coefficients where
 *                             each row is a set of correlation coefficients of
 *                             one sub-frame.
 *       -varscale           : a scale computed when WebRtcIsac_GetLpcCoefUb()
 *                             is called.
 *
 * Outputs:
 *       -gain               : pointer to a buffer where LP gains are written.
 *
 */
void
WebRtcIsac_GetLpcGain(
    double        signal_noise_ratio,
    const double* filtCoeffVecs,
    int           numVecs,
    double*       gain,
    double        corrMat[][UB_LPC_ORDER + 1],
    const double* varscale)
{
  int16_t j, n;
  int16_t subFrameCntr;
  double aPolynom[ORDERLO + 1];
  double res_nrg;

  const double HearThresOffset = -28.0;
  const double H_T_H = pow(10.0, 0.05 * HearThresOffset);
  /* divide by sqrt(12) = 3.46 */
  const double S_N_R = pow(10.0, 0.05 * signal_noise_ratio) / 3.46;

  aPolynom[0] = 1;
  for(subFrameCntr = 0; subFrameCntr < numVecs; subFrameCntr++)
  {
    if(subFrameCntr == SUBFRAMES)
    {
      // we are in second half of a SWB frame. use new varscale
      varscale++;
    }
    memcpy(&aPolynom[1], &filtCoeffVecs[(subFrameCntr * (UB_LPC_ORDER + 1)) +
                                        1], sizeof(double) * UB_LPC_ORDER);

    /* residual energy */
    res_nrg = 0.0;
    for(j = 0; j <= UB_LPC_ORDER; j++)
    {
      for(n = 0; n <= j; n++)
      {
        res_nrg += aPolynom[j] * corrMat[subFrameCntr][j-n] *
            aPolynom[n];
      }
      for(n = j+1; n <= UB_LPC_ORDER; n++)
      {
        res_nrg += aPolynom[j] * corrMat[subFrameCntr][n-j] *
            aPolynom[n];
      }
    }

    /* add hearing threshold and compute the gain */
    gain[subFrameCntr] = S_N_R / (sqrt(res_nrg) / *varscale + H_T_H);
  }
}
