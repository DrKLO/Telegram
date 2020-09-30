/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * entropy_coding.c
 *
 * This header file defines all of the functions used to arithmetically
 * encode the iSAC bistream
 *
 */


#include "common_audio/signal_processing/include/signal_processing_library.h"
#include "modules/audio_coding/codecs/isac/main/source/entropy_coding.h"
#include "modules/audio_coding/codecs/isac/main/source/settings.h"
#include "modules/audio_coding/codecs/isac/main/source/arith_routines.h"
#include "modules/audio_coding/codecs/isac/main/source/spectrum_ar_model_tables.h"
#include "modules/audio_coding/codecs/isac/main/source/lpc_tables.h"
#include "modules/audio_coding/codecs/isac/main/source/pitch_gain_tables.h"
#include "modules/audio_coding/codecs/isac/main/source/pitch_lag_tables.h"
#include "modules/audio_coding/codecs/isac/main/source/encode_lpc_swb.h"
#include "modules/audio_coding/codecs/isac/main/source/lpc_shape_swb12_tables.h"
#include "modules/audio_coding/codecs/isac/main/source/lpc_shape_swb16_tables.h"
#include "modules/audio_coding/codecs/isac/main/source/lpc_gain_swb_tables.h"
#include "modules/audio_coding/codecs/isac/main/source/os_specific_inline.h"

#include <math.h>
#include <string.h>

static const uint16_t kLpcVecPerSegmentUb12 = 5;
static const uint16_t kLpcVecPerSegmentUb16 = 4;

/* CDF array for encoder bandwidth (12 vs 16 kHz) indicator. */
static const uint16_t kOneBitEqualProbCdf[3] = {
    0, 32768, 65535 };

/* Pointer to cdf array for encoder bandwidth (12 vs 16 kHz) indicator. */
static const uint16_t* const kOneBitEqualProbCdf_ptr[1] = {
    kOneBitEqualProbCdf };

/*
 * Initial cdf index for decoder of encoded bandwidth
 * (12 vs 16 kHz) indicator.
 */
static const uint16_t kOneBitEqualProbInitIndex[1] = { 1 };


static const int kIsSWB12 = 1;

/* compute correlation from power spectrum */
static void FindCorrelation(int32_t* PSpecQ12, int32_t* CorrQ7) {
  int32_t summ[FRAMESAMPLES / 8];
  int32_t diff[FRAMESAMPLES / 8];
  const int16_t* CS_ptrQ9;
  int32_t sum;
  int k, n;

  for (k = 0; k < FRAMESAMPLES / 8; k++) {
    summ[k] = (PSpecQ12[k] + PSpecQ12[FRAMESAMPLES_QUARTER - 1 - k] + 16) >> 5;
    diff[k] = (PSpecQ12[k] - PSpecQ12[FRAMESAMPLES_QUARTER - 1 - k] + 16) >> 5;
  }

  sum = 2;
  for (n = 0; n < FRAMESAMPLES / 8; n++) {
    sum += summ[n];
  }
  CorrQ7[0] = sum;

  for (k = 0; k < AR_ORDER; k += 2) {
    sum = 0;
    CS_ptrQ9 = WebRtcIsac_kCos[k];
    for (n = 0; n < FRAMESAMPLES / 8; n++)
      sum += (CS_ptrQ9[n] * diff[n] + 256) >> 9;
    CorrQ7[k + 1] = sum;
  }

  for (k = 1; k < AR_ORDER; k += 2) {
    sum = 0;
    CS_ptrQ9 = WebRtcIsac_kCos[k];
    for (n = 0; n < FRAMESAMPLES / 8; n++)
      sum += (CS_ptrQ9[n] * summ[n] + 256) >> 9;
    CorrQ7[k + 1] = sum;
  }
}

/* compute inverse AR power spectrum */
/* Changed to the function used in iSAC FIX for compatibility reasons */
static void FindInvArSpec(const int16_t* ARCoefQ12,
                          const int32_t gainQ10,
                          int32_t* CurveQ16) {
  int32_t CorrQ11[AR_ORDER + 1];
  int64_t sum, tmpGain;
  int32_t diffQ16[FRAMESAMPLES / 8];
  const int16_t* CS_ptrQ9;
  int k, n;
  int16_t round, shftVal = 0, sh;

  sum = 0;
  for (n = 0; n < AR_ORDER + 1; n++) {
    sum += WEBRTC_SPL_MUL(ARCoefQ12[n], ARCoefQ12[n]);   /* Q24 */
  }
  sum = ((sum >> 6) * 65 + 32768) >> 16;  /* Q8 */
  CorrQ11[0] = (sum * gainQ10 + 256) >> 9;

  /* To avoid overflow, we shift down gainQ10 if it is large.
   * We will not lose any precision */
  if (gainQ10 > 400000) {
    tmpGain = gainQ10 >> 3;
    round = 32;
    shftVal = 6;
  } else {
    tmpGain = gainQ10;
    round = 256;
    shftVal = 9;
  }

  for (k = 1; k < AR_ORDER + 1; k++) {
    sum = 16384;
    for (n = k; n < AR_ORDER + 1; n++)
      sum += WEBRTC_SPL_MUL(ARCoefQ12[n - k], ARCoefQ12[n]); /* Q24 */
    sum >>= 15;
    CorrQ11[k] = (sum * tmpGain + round) >> shftVal;
  }
  sum = CorrQ11[0] << 7;
  for (n = 0; n < FRAMESAMPLES / 8; n++) {
    CurveQ16[n] = sum;
  }
  for (k = 1; k < AR_ORDER; k += 2) {
    for (n = 0; n < FRAMESAMPLES / 8; n++) {
      CurveQ16[n] += (WebRtcIsac_kCos[k][n] * CorrQ11[k + 1] + 2) >> 2;
    }
  }

  CS_ptrQ9 = WebRtcIsac_kCos[0];

  /* If CorrQ11[1] too large we avoid getting overflow in the
   * calculation by shifting */
  sh = WebRtcSpl_NormW32(CorrQ11[1]);
  if (CorrQ11[1] == 0) { /* Use next correlation */
    sh = WebRtcSpl_NormW32(CorrQ11[2]);
  }
  if (sh < 9) {
    shftVal = 9 - sh;
  } else {
    shftVal = 0;
  }
  for (n = 0; n < FRAMESAMPLES / 8; n++) {
    diffQ16[n] = (CS_ptrQ9[n] * (CorrQ11[1] >> shftVal) + 2) >> 2;
  }
  for (k = 2; k < AR_ORDER; k += 2) {
    CS_ptrQ9 = WebRtcIsac_kCos[k];
    for (n = 0; n < FRAMESAMPLES / 8; n++) {
      diffQ16[n] += (CS_ptrQ9[n] * (CorrQ11[k + 1] >> shftVal) + 2) >> 2;
    }
  }

  for (k = 0; k < FRAMESAMPLES / 8; k++) {
    int32_t diff_q16_shifted = (int32_t)((uint32_t)(diffQ16[k]) << shftVal);
    CurveQ16[FRAMESAMPLES_QUARTER - 1 - k] = CurveQ16[k] - diff_q16_shifted;
    CurveQ16[k] += diff_q16_shifted;
  }
}

/* Generate array of dither samples in Q7. */
static void GenerateDitherQ7Lb(int16_t* bufQ7, uint32_t seed,
                               int length, int16_t AvgPitchGain_Q12) {
  int   k, shft;
  int16_t dither1_Q7, dither2_Q7, dither_gain_Q14;

  /* This threshold should be equal to that in decode_spec(). */
  if (AvgPitchGain_Q12 < 614) {
    for (k = 0; k < length - 2; k += 3) {
      /* New random unsigned int. */
      seed = (seed * 196314165) + 907633515;

      /* Fixed-point dither sample between -64 and 64 (Q7). */
      /* dither = seed * 128 / 4294967295 */
      dither1_Q7 = (int16_t)(((int32_t)(seed + 16777216)) >> 25);

      /* New random unsigned int. */
      seed = (seed * 196314165) + 907633515;

      /* Fixed-point dither sample between -64 and 64. */
      dither2_Q7 = (int16_t)(((int32_t)(seed + 16777216)) >> 25);

      shft = (seed >> 25) & 15;
      if (shft < 5) {
        bufQ7[k]   = dither1_Q7;
        bufQ7[k + 1] = dither2_Q7;
        bufQ7[k + 2] = 0;
      } else if (shft < 10) {
        bufQ7[k]   = dither1_Q7;
        bufQ7[k + 1] = 0;
        bufQ7[k + 2] = dither2_Q7;
      } else {
        bufQ7[k]   = 0;
        bufQ7[k + 1] = dither1_Q7;
        bufQ7[k + 2] = dither2_Q7;
      }
    }
  } else {
    dither_gain_Q14 = (int16_t)(22528 - 10 * AvgPitchGain_Q12);

    /* Dither on half of the coefficients. */
    for (k = 0; k < length - 1; k += 2) {
      /* New random unsigned int */
      seed = (seed * 196314165) + 907633515;

      /* Fixed-point dither sample between -64 and 64. */
      dither1_Q7 = (int16_t)(((int32_t)(seed + 16777216)) >> 25);

      /* Dither sample is placed in either even or odd index. */
      shft = (seed >> 25) & 1;     /* Either 0 or 1 */

      bufQ7[k + shft] = (((dither_gain_Q14 * dither1_Q7) + 8192) >> 14);
      bufQ7[k + 1 - shft] = 0;
    }
  }
}



/******************************************************************************
 * GenerateDitherQ7LbUB()
 *
 * generate array of dither samples in Q7 There are less zeros in dither
 * vector compared to GenerateDitherQ7Lb.
 *
 * A uniform random number generator with the range of [-64 64] is employed
 * but the generated dithers are scaled by 0.35, a heuristic scaling.
 *
 * Input:
 *      -seed               : the initial seed for the random number generator.
 *      -length             : the number of dither values to be generated.
 *
 * Output:
 *      -bufQ7              : pointer to a buffer where dithers are written to.
 */
static void GenerateDitherQ7LbUB(
    int16_t* bufQ7,
    uint32_t seed,
    int length) {
  int k;
  for (k = 0; k < length; k++) {
    /* new random unsigned int */
    seed = (seed * 196314165) + 907633515;

    /* Fixed-point dither sample between -64 and 64 (Q7). */
    /* bufQ7 = seed * 128 / 4294967295 */
    bufQ7[k] = (int16_t)(((int32_t)(seed + 16777216)) >> 25);

    /* Scale by 0.35. */
    bufQ7[k] = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(bufQ7[k], 2048, 13);
  }
}

/*
 * Function to decode the complex spectrum from the bit stream
 * returns the total number of bytes in the stream.
 */
int WebRtcIsac_DecodeSpec(Bitstr* streamdata, int16_t AvgPitchGain_Q12,
                          enum ISACBand band, double* fr, double* fi) {
  int16_t  DitherQ7[FRAMESAMPLES];
  int16_t  data[FRAMESAMPLES];
  int32_t  invARSpec2_Q16[FRAMESAMPLES_QUARTER];
  uint16_t invARSpecQ8[FRAMESAMPLES_QUARTER];
  int16_t  ARCoefQ12[AR_ORDER + 1];
  int16_t  RCQ15[AR_ORDER];
  int16_t  gainQ10;
  int32_t  gain2_Q10, res;
  int32_t  in_sqrt;
  int32_t  newRes;
  int k, len, i;
  int is_12khz = !kIsSWB12;
  int num_dft_coeff = FRAMESAMPLES;
  /* Create dither signal. */
  if (band == kIsacLowerBand) {
    GenerateDitherQ7Lb(DitherQ7, streamdata->W_upper, FRAMESAMPLES,
                       AvgPitchGain_Q12);
  } else {
    GenerateDitherQ7LbUB(DitherQ7, streamdata->W_upper, FRAMESAMPLES);
    if (band == kIsacUpperBand12) {
      is_12khz = kIsSWB12;
      num_dft_coeff = FRAMESAMPLES_HALF;
    }
  }

  /* Decode model parameters. */
  if (WebRtcIsac_DecodeRc(streamdata, RCQ15) < 0)
    return -ISAC_RANGE_ERROR_DECODE_SPECTRUM;

  WebRtcSpl_ReflCoefToLpc(RCQ15, AR_ORDER, ARCoefQ12);

  if (WebRtcIsac_DecodeGain2(streamdata, &gain2_Q10) < 0)
    return -ISAC_RANGE_ERROR_DECODE_SPECTRUM;

  /* Compute inverse AR power spectrum. */
  FindInvArSpec(ARCoefQ12, gain2_Q10, invARSpec2_Q16);

  /* Convert to magnitude spectrum,
   * by doing square-roots (modified from SPLIB). */
  res = 1 << (WebRtcSpl_GetSizeInBits(invARSpec2_Q16[0]) >> 1);
  for (k = 0; k < FRAMESAMPLES_QUARTER; k++) {
    in_sqrt = invARSpec2_Q16[k];
    i = 10;

    /* Negative values make no sense for a real sqrt-function. */
    if (in_sqrt < 0)
      in_sqrt = -in_sqrt;

    newRes = (in_sqrt / res + res) >> 1;
    do {
      res = newRes;
      newRes = (in_sqrt / res + res) >> 1;
    } while (newRes != res && i-- > 0);

    invARSpecQ8[k] = (int16_t)newRes;
  }

  len = WebRtcIsac_DecLogisticMulti2(data, streamdata, invARSpecQ8, DitherQ7,
                                     num_dft_coeff, is_12khz);
  /* Arithmetic decoding of spectrum. */
  if (len < 1) {
    return -ISAC_RANGE_ERROR_DECODE_SPECTRUM;
  }

  switch (band) {
    case kIsacLowerBand: {
      /* Scale down spectral samples with low SNR. */
      int32_t p1;
      int32_t p2;
      if (AvgPitchGain_Q12 <= 614) {
        p1 = 30 << 10;
        p2 = 32768 + (33 << 16);
      } else {
        p1 = 36 << 10;
        p2 = 32768 + (40 << 16);
      }
      for (k = 0; k < FRAMESAMPLES; k += 4) {
        gainQ10 = WebRtcSpl_DivW32W16ResW16(p1, (int16_t)(
            (invARSpec2_Q16[k >> 2] + p2) >> 16));
        *fr++ = (double)((data[ k ] * gainQ10 + 512) >> 10) / 128.0;
        *fi++ = (double)((data[k + 1] * gainQ10 + 512) >> 10) / 128.0;
        *fr++ = (double)((data[k + 2] * gainQ10 + 512) >> 10) / 128.0;
        *fi++ = (double)((data[k + 3] * gainQ10 + 512) >> 10) / 128.0;
      }
      break;
    }
    case kIsacUpperBand12: {
      for (k = 0, i = 0; k < FRAMESAMPLES_HALF; k += 4) {
        fr[i] = (double)data[ k ] / 128.0;
        fi[i] = (double)data[k + 1] / 128.0;
        i++;
        fr[i] = (double)data[k + 2] / 128.0;
        fi[i] = (double)data[k + 3] / 128.0;
        i++;
      }
      /* The second half of real and imaginary coefficients is zero. This is
       * due to using the old FFT module which requires two signals as input
       * while in 0-12 kHz mode we only have 8-12 kHz band, and the second
       * signal is set to zero. */
      memset(&fr[FRAMESAMPLES_QUARTER], 0, FRAMESAMPLES_QUARTER *
             sizeof(double));
      memset(&fi[FRAMESAMPLES_QUARTER], 0, FRAMESAMPLES_QUARTER *
             sizeof(double));
      break;
    }
    case kIsacUpperBand16: {
      for (i = 0, k = 0; k < FRAMESAMPLES; k += 4, i++) {
        fr[i] = (double)data[ k ] / 128.0;
        fi[i] = (double)data[k + 1] / 128.0;
        fr[(FRAMESAMPLES_HALF) - 1 - i] = (double)data[k + 2] / 128.0;
        fi[(FRAMESAMPLES_HALF) - 1 - i] = (double)data[k + 3] / 128.0;
      }
      break;
    }
  }
  return len;
}


int WebRtcIsac_EncodeSpec(const int16_t* fr, const int16_t* fi,
                          int16_t AvgPitchGain_Q12, enum ISACBand band,
                          Bitstr* streamdata) {
  int16_t ditherQ7[FRAMESAMPLES];
  int16_t dataQ7[FRAMESAMPLES];
  int32_t PSpec[FRAMESAMPLES_QUARTER];
  int32_t invARSpec2_Q16[FRAMESAMPLES_QUARTER];
  uint16_t invARSpecQ8[FRAMESAMPLES_QUARTER];
  int32_t CorrQ7[AR_ORDER + 1];
  int32_t CorrQ7_norm[AR_ORDER + 1];
  int16_t RCQ15[AR_ORDER];
  int16_t ARCoefQ12[AR_ORDER + 1];
  int32_t gain2_Q10;
  int16_t val;
  int32_t nrg, res;
  uint32_t sum;
  int32_t in_sqrt;
  int32_t newRes;
  int16_t err;
  uint32_t nrg_u32;
  int shift_var;
  int k, n, j, i;
  int is_12khz = !kIsSWB12;
  int num_dft_coeff = FRAMESAMPLES;

  /* Create dither signal. */
  if (band == kIsacLowerBand) {
    GenerateDitherQ7Lb(ditherQ7, streamdata->W_upper, FRAMESAMPLES,
                       AvgPitchGain_Q12);
  } else {
    GenerateDitherQ7LbUB(ditherQ7, streamdata->W_upper, FRAMESAMPLES);
    if (band == kIsacUpperBand12) {
      is_12khz = kIsSWB12;
      num_dft_coeff = FRAMESAMPLES_HALF;
    }
  }

  /* add dither and quantize, and compute power spectrum */
  switch (band) {
    case kIsacLowerBand: {
      for (k = 0; k < FRAMESAMPLES; k += 4) {
        val = ((*fr++ + ditherQ7[k]   + 64) & 0xFF80) - ditherQ7[k];
        dataQ7[k] = val;
        sum = val * val;

        val = ((*fi++ + ditherQ7[k + 1] + 64) & 0xFF80) - ditherQ7[k + 1];
        dataQ7[k + 1] = val;
        sum += val * val;

        val = ((*fr++ + ditherQ7[k + 2] + 64) & 0xFF80) - ditherQ7[k + 2];
        dataQ7[k + 2] = val;
        sum += val * val;

        val = ((*fi++ + ditherQ7[k + 3] + 64) & 0xFF80) - ditherQ7[k + 3];
        dataQ7[k + 3] = val;
        sum += val * val;

        PSpec[k >> 2] = sum >> 2;
      }
      break;
    }
    case kIsacUpperBand12: {
      for (k = 0, j = 0; k < FRAMESAMPLES_HALF; k += 4) {
        val = ((*fr++ + ditherQ7[k]   + 64) & 0xFF80) - ditherQ7[k];
        dataQ7[k] = val;
        sum = val * val;

        val = ((*fi++ + ditherQ7[k + 1] + 64) & 0xFF80) - ditherQ7[k + 1];
        dataQ7[k + 1] = val;
        sum += val * val;

        PSpec[j++] = sum >> 1;

        val = ((*fr++ + ditherQ7[k + 2] + 64) & 0xFF80) - ditherQ7[k + 2];
        dataQ7[k + 2] = val;
        sum = val * val;

        val = ((*fi++ + ditherQ7[k + 3] + 64) & 0xFF80) - ditherQ7[k + 3];
        dataQ7[k + 3] = val;
        sum += val * val;

        PSpec[j++] = sum >> 1;
      }
      break;
    }
    case kIsacUpperBand16: {
      for (j = 0, k = 0; k < FRAMESAMPLES; k += 4, j++) {
        val = ((fr[j] + ditherQ7[k]   + 64) & 0xFF80) - ditherQ7[k];
        dataQ7[k] = val;
        sum = val * val;

        val = ((fi[j] + ditherQ7[k + 1] + 64) & 0xFF80) - ditherQ7[k + 1];
        dataQ7[k + 1] = val;
        sum += val * val;

        val = ((fr[(FRAMESAMPLES_HALF) - 1 - j] + ditherQ7[k + 2] + 64) &
            0xFF80) - ditherQ7[k + 2];
        dataQ7[k + 2] = val;
        sum += val * val;

        val = ((fi[(FRAMESAMPLES_HALF) - 1 - j] + ditherQ7[k + 3] + 64) &
            0xFF80) - ditherQ7[k + 3];
        dataQ7[k + 3] = val;
        sum += val * val;

        PSpec[k >> 2] = sum >> 2;
      }
      break;
    }
  }

  /* compute correlation from power spectrum */
  FindCorrelation(PSpec, CorrQ7);

  /* Find AR coefficients */
  /* Aumber of bit shifts to 14-bit normalize CorrQ7[0]
   * (leaving room for sign) */
  shift_var = WebRtcSpl_NormW32(CorrQ7[0]) - 18;

  if (shift_var > 0) {
    for (k = 0; k < AR_ORDER + 1; k++) {
      CorrQ7_norm[k] = CorrQ7[k] << shift_var;
    }
  } else {
    for (k = 0; k < AR_ORDER + 1; k++) {
      CorrQ7_norm[k] = CorrQ7[k] >> (-shift_var);
    }
  }

  /* Find RC coefficients. */
  WebRtcSpl_AutoCorrToReflCoef(CorrQ7_norm, AR_ORDER, RCQ15);

  /* Quantize & code RC Coefficient. */
  WebRtcIsac_EncodeRc(RCQ15, streamdata);

  /* RC -> AR coefficients */
  WebRtcSpl_ReflCoefToLpc(RCQ15, AR_ORDER, ARCoefQ12);

  /* Compute ARCoef' * Corr * ARCoef in Q19. */
  nrg = 0;
  for (j = 0; j <= AR_ORDER; j++) {
    for (n = 0; n <= j; n++) {
      nrg += (ARCoefQ12[j] * ((CorrQ7_norm[j - n] * ARCoefQ12[n] + 256) >> 9) +
          4) >> 3;
    }
    for (n = j + 1; n <= AR_ORDER; n++) {
      nrg += (ARCoefQ12[j] * ((CorrQ7_norm[n - j] * ARCoefQ12[n] + 256) >> 9) +
          4) >> 3;
    }
  }

  nrg_u32 = (uint32_t)nrg;
  if (shift_var > 0) {
    nrg_u32 = nrg_u32 >> shift_var;
  } else {
    nrg_u32 = nrg_u32 << (-shift_var);
  }
  if (nrg_u32 > 0x7FFFFFFF) {
    nrg = 0x7FFFFFFF;
  }  else {
    nrg = (int32_t)nrg_u32;
  }
  /* Also shifts 31 bits to the left! */
  gain2_Q10 = WebRtcSpl_DivResultInQ31(FRAMESAMPLES_QUARTER, nrg);

  /* Quantize & code gain2_Q10. */
  if (WebRtcIsac_EncodeGain2(&gain2_Q10, streamdata)) {
    return -1;
  }

  /* Compute inverse AR power spectrum. */
  FindInvArSpec(ARCoefQ12, gain2_Q10, invARSpec2_Q16);
  /* Convert to magnitude spectrum, by doing square-roots
   * (modified from SPLIB). */
  res = 1 << (WebRtcSpl_GetSizeInBits(invARSpec2_Q16[0]) >> 1);
  for (k = 0; k < FRAMESAMPLES_QUARTER; k++) {
    in_sqrt = invARSpec2_Q16[k];
    i = 10;
    /* Negative values make no sense for a real sqrt-function. */
    if (in_sqrt < 0) {
      in_sqrt = -in_sqrt;
    }
    newRes = (in_sqrt / res + res) >> 1;
    do {
      res = newRes;
      newRes = (in_sqrt / res + res) >> 1;
    } while (newRes != res && i-- > 0);

    invARSpecQ8[k] = (int16_t)newRes;
  }
  /* arithmetic coding of spectrum */
  err = WebRtcIsac_EncLogisticMulti2(streamdata, dataQ7, invARSpecQ8,
                                     num_dft_coeff, is_12khz);
  if (err < 0) {
    return (err);
  }
  return 0;
}


/* step-up */
void WebRtcIsac_Rc2Poly(double* RC, int N, double* a) {
  int m, k;
  double tmp[MAX_AR_MODEL_ORDER];

  a[0] = 1.0;
  tmp[0] = 1.0;
  for (m = 1; m <= N; m++) {
    /* copy */
    memcpy(&tmp[1], &a[1], (m - 1) * sizeof(double));
    a[m] = RC[m - 1];
    for (k = 1; k < m; k++) {
      a[k] += RC[m - 1] * tmp[m - k];
    }
  }
  return;
}

/* step-down */
void WebRtcIsac_Poly2Rc(double* a, int N, double* RC) {
  int m, k;
  double tmp[MAX_AR_MODEL_ORDER];
  double tmp_inv;

  RC[N - 1] = a[N];
  for (m = N - 1; m > 0; m--) {
    tmp_inv = 1.0 / (1.0 - RC[m] * RC[m]);
    for (k = 1; k <= m; k++) {
      tmp[k] = (a[k] - RC[m] * a[m - k + 1]) * tmp_inv;
    }

    memcpy(&a[1], &tmp[1], (m - 1) * sizeof(double));
    RC[m - 1] = tmp[m];
  }
  return;
}


#define MAX_ORDER 100

/* Matlab's LAR definition */
void WebRtcIsac_Rc2Lar(const double* refc, double* lar, int order) {
  int k;
  for (k = 0; k < order; k++) {
    lar[k] = log((1 + refc[k]) / (1 - refc[k]));
  }
}


void WebRtcIsac_Lar2Rc(const double* lar, double* refc,  int order) {
  int k;
  double tmp;

  for (k = 0; k < order; k++) {
    tmp = exp(lar[k]);
    refc[k] = (tmp - 1) / (tmp + 1);
  }
}

void WebRtcIsac_Poly2Lar(double* lowband, int orderLo, double* hiband,
                         int orderHi, int Nsub, double* lars) {
  int k;
  double rc[MAX_ORDER], *inpl, *inph, *outp;

  inpl = lowband;
  inph = hiband;
  outp = lars;
  for (k = 0; k < Nsub; k++) {
    /* gains */
    outp[0] = inpl[0];
    outp[1] = inph[0];
    outp += 2;

    /* Low band */
    inpl[0] = 1.0;
    WebRtcIsac_Poly2Rc(inpl, orderLo, rc);
    WebRtcIsac_Rc2Lar(rc, outp, orderLo);
    outp += orderLo;

    /* High band */
    inph[0] = 1.0;
    WebRtcIsac_Poly2Rc(inph, orderHi, rc);
    WebRtcIsac_Rc2Lar(rc, outp, orderHi);
    outp += orderHi;

    inpl += orderLo + 1;
    inph += orderHi + 1;
  }
}


int16_t WebRtcIsac_Poly2LarUB(double* lpcVecs, int16_t bandwidth) {
  double      poly[MAX_ORDER];
  double      rc[MAX_ORDER];
  double*     ptrIO;
  int16_t vecCntr;
  int16_t vecSize;
  int16_t numVec;

  vecSize = UB_LPC_ORDER;
  switch (bandwidth) {
    case isac12kHz: {
      numVec  = UB_LPC_VEC_PER_FRAME;
      break;
    }
    case isac16kHz: {
      numVec  = UB16_LPC_VEC_PER_FRAME;
      break;
    }
    default:
      return -1;
  }

  ptrIO = lpcVecs;
  poly[0] = 1.0;
  for (vecCntr = 0; vecCntr < numVec; vecCntr++) {
    memcpy(&poly[1], ptrIO, sizeof(double) * vecSize);
    WebRtcIsac_Poly2Rc(poly, vecSize, rc);
    WebRtcIsac_Rc2Lar(rc, ptrIO, vecSize);
    ptrIO += vecSize;
  }
  return 0;
}


void WebRtcIsac_Lar2Poly(double* lars, double* lowband, int orderLo,
                         double* hiband, int orderHi, int Nsub) {
  int k, orderTot;
  double rc[MAX_ORDER], *outpl, *outph, *inp;

  orderTot = (orderLo + orderHi + 2);
  outpl = lowband;
  outph = hiband;
  /* First two elements of 'inp' store gains*/
  inp = lars;
  for (k = 0; k < Nsub; k++) {
    /* Low band */
    WebRtcIsac_Lar2Rc(&inp[2], rc, orderLo);
    WebRtcIsac_Rc2Poly(rc, orderLo, outpl);

    /* High band */
    WebRtcIsac_Lar2Rc(&inp[orderLo + 2], rc, orderHi);
    WebRtcIsac_Rc2Poly(rc, orderHi, outph);

    /* gains */
    outpl[0] = inp[0];
    outph[0] = inp[1];

    outpl += orderLo + 1;
    outph += orderHi + 1;
    inp += orderTot;
  }
}

/*
 *  assumes 2 LAR vectors interpolates to 'numPolyVec' A-polynomials
 *  Note: 'numPolyVecs' includes the first and the last point of the interval
 */
void WebRtcIsac_Lar2PolyInterpolUB(double* larVecs, double* percepFilterParams,
                                   int numPolyVecs) {
  int polyCntr, coeffCntr;
  double larInterpol[UB_LPC_ORDER];
  double rc[UB_LPC_ORDER];
  double delta[UB_LPC_ORDER];

  /* calculate the step-size for linear interpolation coefficients */
  for (coeffCntr = 0; coeffCntr < UB_LPC_ORDER; coeffCntr++) {
    delta[coeffCntr] = (larVecs[UB_LPC_ORDER + coeffCntr] -
        larVecs[coeffCntr]) / (numPolyVecs - 1);
  }

  for (polyCntr = 0; polyCntr < numPolyVecs; polyCntr++) {
    for (coeffCntr = 0; coeffCntr < UB_LPC_ORDER; coeffCntr++) {
      larInterpol[coeffCntr] = larVecs[coeffCntr] +
          delta[coeffCntr] * polyCntr;
    }
    WebRtcIsac_Lar2Rc(larInterpol, rc, UB_LPC_ORDER);

    /* convert to A-polynomial, the following function returns A[0] = 1;
     * which is written where gains had to be written. Then we write the
     * gain (outside this function). This way we say a memcpy. */
    WebRtcIsac_Rc2Poly(rc, UB_LPC_ORDER, percepFilterParams);
    percepFilterParams += (UB_LPC_ORDER + 1);
  }
}

int WebRtcIsac_DecodeLpc(Bitstr* streamdata, double* LPCCoef_lo,
                         double* LPCCoef_hi) {
  double lars[KLT_ORDER_GAIN + KLT_ORDER_SHAPE];
  int err;

  err = WebRtcIsac_DecodeLpcCoef(streamdata, lars);
  if (err < 0) {
    return -ISAC_RANGE_ERROR_DECODE_LPC;
  }
  WebRtcIsac_Lar2Poly(lars, LPCCoef_lo, ORDERLO, LPCCoef_hi, ORDERHI,
                      SUBFRAMES);
  return 0;
}

int16_t WebRtcIsac_DecodeInterpolLpcUb(Bitstr* streamdata,
                                       double* percepFilterParams,
                                       int16_t bandwidth) {
  double lpcCoeff[UB_LPC_ORDER * UB16_LPC_VEC_PER_FRAME];
  int err;
  int interpolCntr;
  int subframeCntr;
  int16_t numSegments;
  int16_t numVecPerSegment;
  int16_t numGains;

  double percepFilterGains[SUBFRAMES << 1];
  double* ptrOutParam = percepFilterParams;

  err = WebRtcIsac_DecodeLpcCoefUB(streamdata, lpcCoeff, percepFilterGains,
                                   bandwidth);
  if (err < 0) {
    return -ISAC_RANGE_ERROR_DECODE_LPC;
  }

  switch (bandwidth) {
    case isac12kHz: {
      numGains = SUBFRAMES;
      numSegments = UB_LPC_VEC_PER_FRAME - 1;
      numVecPerSegment = kLpcVecPerSegmentUb12;
      break;
    }
    case isac16kHz: {
      numGains = SUBFRAMES << 1;
      numSegments = UB16_LPC_VEC_PER_FRAME - 1;
      numVecPerSegment = kLpcVecPerSegmentUb16;
      break;
    }
    default:
      return -1;
  }

  for (interpolCntr = 0; interpolCntr < numSegments; interpolCntr++) {
    WebRtcIsac_Lar2PolyInterpolUB(&lpcCoeff[interpolCntr * UB_LPC_ORDER],
                                  ptrOutParam, numVecPerSegment + 1);
    ptrOutParam += (numVecPerSegment * (UB_LPC_ORDER + 1));
  }

  ptrOutParam = percepFilterParams;

  if (bandwidth == isac16kHz) {
    ptrOutParam += (1 + UB_LPC_ORDER);
  }

  for (subframeCntr = 0; subframeCntr < numGains; subframeCntr++) {
    *ptrOutParam = percepFilterGains[subframeCntr];
    ptrOutParam += (1 + UB_LPC_ORDER);
  }
  return 0;
}


/* decode & dequantize LPC Coef */
int WebRtcIsac_DecodeLpcCoef(Bitstr* streamdata, double* LPCCoef) {
  int j, k, n, pos, pos2, posg, poss, offsg, offss, offs2;
  int index_g[KLT_ORDER_GAIN], index_s[KLT_ORDER_SHAPE];
  double tmpcoeffs_g[KLT_ORDER_GAIN], tmpcoeffs_s[KLT_ORDER_SHAPE];
  double tmpcoeffs2_g[KLT_ORDER_GAIN], tmpcoeffs2_s[KLT_ORDER_SHAPE];
  double sum;
  int err;
  int model = 1;

  /* entropy decoding of model number */
  /* We are keeping this for backward compatibility of bit-streams. */
  err = WebRtcIsac_DecHistOneStepMulti(&model, streamdata,
                                       WebRtcIsac_kQKltModelCdfPtr,
                                       WebRtcIsac_kQKltModelInitIndex, 1);
  if (err < 0) {
    return err;
  }
  /* Only accepted value of model is 0. It is kept in bit-stream for backward
   * compatibility. */
  if (model != 0) {
    return -ISAC_DISALLOWED_LPC_MODEL;
  }

  /* entropy decoding of quantization indices */
  err = WebRtcIsac_DecHistOneStepMulti(
      index_s, streamdata, WebRtcIsac_kQKltCdfPtrShape,
      WebRtcIsac_kQKltInitIndexShape, KLT_ORDER_SHAPE);
  if (err < 0) {
    return err;
  }
  err = WebRtcIsac_DecHistOneStepMulti(
      index_g, streamdata, WebRtcIsac_kQKltCdfPtrGain,
      WebRtcIsac_kQKltInitIndexGain, KLT_ORDER_GAIN);
  if (err < 0) {
    return err;
  }

  /* find quantization levels for coefficients */
  for (k = 0; k < KLT_ORDER_SHAPE; k++) {
    tmpcoeffs_s[k] =
        WebRtcIsac_kQKltLevelsShape[WebRtcIsac_kQKltOffsetShape[k] +
                                    index_s[k]];
  }
  for (k = 0; k < KLT_ORDER_GAIN; k++) {
    tmpcoeffs_g[k] = WebRtcIsac_kQKltLevelsGain[WebRtcIsac_kQKltOffsetGain[k] +
                                                index_g[k]];
  }

  /* Inverse KLT  */

  /* Left transform, transpose matrix!  */
  offsg = 0;
  offss = 0;
  posg = 0;
  poss = 0;
  for (j = 0; j < SUBFRAMES; j++) {
    offs2 = 0;
    for (k = 0; k < LPC_GAIN_ORDER; k++) {
      sum = 0;
      pos = offsg;
      pos2 = offs2;
      for (n = 0; n < LPC_GAIN_ORDER; n++) {
        sum += tmpcoeffs_g[pos++] * WebRtcIsac_kKltT1Gain[pos2++];
      }
      tmpcoeffs2_g[posg++] = sum;
      offs2 += LPC_GAIN_ORDER;
    }
    offs2 = 0;
    for (k = 0; k < LPC_SHAPE_ORDER; k++) {
      sum = 0;
      pos = offss;
      pos2 = offs2;
      for (n = 0; n < LPC_SHAPE_ORDER; n++) {
        sum += tmpcoeffs_s[pos++] * WebRtcIsac_kKltT1Shape[pos2++];
      }
      tmpcoeffs2_s[poss++] = sum;
      offs2 += LPC_SHAPE_ORDER;
    }
    offsg += LPC_GAIN_ORDER;
    offss += LPC_SHAPE_ORDER;
  }

  /* Right transform, transpose matrix */
  offsg = 0;
  offss = 0;
  posg = 0;
  poss = 0;
  for (j = 0; j < SUBFRAMES; j++) {
    posg = offsg;
    for (k = 0; k < LPC_GAIN_ORDER; k++) {
      sum = 0;
      pos = k;
      pos2 = j;
      for (n = 0; n < SUBFRAMES; n++) {
        sum += tmpcoeffs2_g[pos] * WebRtcIsac_kKltT2Gain[pos2];
        pos += LPC_GAIN_ORDER;
        pos2 += SUBFRAMES;

      }
      tmpcoeffs_g[posg++] = sum;
    }
    poss = offss;
    for (k = 0; k < LPC_SHAPE_ORDER; k++) {
      sum = 0;
      pos = k;
      pos2 = j;
      for (n = 0; n < SUBFRAMES; n++) {
        sum += tmpcoeffs2_s[pos] * WebRtcIsac_kKltT2Shape[pos2];
        pos += LPC_SHAPE_ORDER;
        pos2 += SUBFRAMES;
      }
      tmpcoeffs_s[poss++] = sum;
    }
    offsg += LPC_GAIN_ORDER;
    offss += LPC_SHAPE_ORDER;
  }

  /* scaling, mean addition, and gain restoration */
  posg = 0;
  poss = 0;
  pos = 0;
  for (k = 0; k < SUBFRAMES; k++) {
    /* log gains */
    LPCCoef[pos] = tmpcoeffs_g[posg] / LPC_GAIN_SCALE;
    LPCCoef[pos] += WebRtcIsac_kLpcMeansGain[posg];
    LPCCoef[pos] = exp(LPCCoef[pos]);
    pos++;
    posg++;
    LPCCoef[pos] = tmpcoeffs_g[posg] / LPC_GAIN_SCALE;
    LPCCoef[pos] += WebRtcIsac_kLpcMeansGain[posg];
    LPCCoef[pos] = exp(LPCCoef[pos]);
    pos++;
    posg++;

    /* Low-band LAR coefficients. */
    for (n = 0; n < LPC_LOBAND_ORDER; n++, pos++, poss++) {
      LPCCoef[pos] = tmpcoeffs_s[poss] / LPC_LOBAND_SCALE;
      LPCCoef[pos] += WebRtcIsac_kLpcMeansShape[poss];
    }

    /* High-band LAR coefficients. */
    for (n = 0; n < LPC_HIBAND_ORDER; n++, pos++, poss++) {
      LPCCoef[pos] = tmpcoeffs_s[poss] / LPC_HIBAND_SCALE;
      LPCCoef[pos] += WebRtcIsac_kLpcMeansShape[poss];
    }
  }
  return 0;
}

/* Encode LPC in LAR domain. */
void WebRtcIsac_EncodeLar(double* LPCCoef, Bitstr* streamdata,
                          IsacSaveEncoderData* encData) {
  int j, k, n, pos, pos2, poss, offss, offs2;
  int index_s[KLT_ORDER_SHAPE];
  int index_ovr_s[KLT_ORDER_SHAPE];
  double tmpcoeffs_s[KLT_ORDER_SHAPE];
  double tmpcoeffs2_s[KLT_ORDER_SHAPE];
  double sum;
  const int kModel = 0;

  /* Mean removal and scaling. */
  poss = 0;
  pos = 0;
  for (k = 0; k < SUBFRAMES; k++) {
    /* First two element are gains, move over them. */
    pos += 2;

    /* Low-band LAR coefficients. */
    for (n = 0; n < LPC_LOBAND_ORDER; n++, poss++, pos++) {
      tmpcoeffs_s[poss] = LPCCoef[pos] - WebRtcIsac_kLpcMeansShape[poss];
      tmpcoeffs_s[poss] *= LPC_LOBAND_SCALE;
    }

    /* High-band LAR coefficients. */
    for (n = 0; n < LPC_HIBAND_ORDER; n++, poss++, pos++) {
      tmpcoeffs_s[poss] = LPCCoef[pos] - WebRtcIsac_kLpcMeansShape[poss];
      tmpcoeffs_s[poss] *= LPC_HIBAND_SCALE;
    }
  }

  /* KLT  */

  /* Left transform. */
  offss = 0;
  for (j = 0; j < SUBFRAMES; j++) {
    poss = offss;
    for (k = 0; k < LPC_SHAPE_ORDER; k++) {
      sum = 0;
      pos = offss;
      pos2 = k;
      for (n = 0; n < LPC_SHAPE_ORDER; n++) {
        sum += tmpcoeffs_s[pos++] * WebRtcIsac_kKltT1Shape[pos2];
        pos2 += LPC_SHAPE_ORDER;
      }
      tmpcoeffs2_s[poss++] = sum;
    }
    offss += LPC_SHAPE_ORDER;
  }

  /* Right transform. */
  offss = 0;
  offs2 = 0;
  for (j = 0; j < SUBFRAMES; j++) {
    poss = offss;
    for (k = 0; k < LPC_SHAPE_ORDER; k++) {
      sum = 0;
      pos = k;
      pos2 = offs2;
      for (n = 0; n < SUBFRAMES; n++) {
        sum += tmpcoeffs2_s[pos] * WebRtcIsac_kKltT2Shape[pos2++];
        pos += LPC_SHAPE_ORDER;
      }
      tmpcoeffs_s[poss++] = sum;
    }
    offs2 += SUBFRAMES;
    offss += LPC_SHAPE_ORDER;
  }

  /* Quantize coefficients. */
  for (k = 0; k < KLT_ORDER_SHAPE; k++) {
    index_s[k] = (WebRtcIsac_lrint(tmpcoeffs_s[k] / KLT_STEPSIZE)) +
        WebRtcIsac_kQKltQuantMinShape[k];
    if (index_s[k] < 0) {
      index_s[k] = 0;
    } else if (index_s[k] > WebRtcIsac_kQKltMaxIndShape[k]) {
      index_s[k] = WebRtcIsac_kQKltMaxIndShape[k];
    }
    index_ovr_s[k] = WebRtcIsac_kQKltOffsetShape[k] + index_s[k];
  }


  /* Only one model remains in this version of the code, kModel = 0. We
   * are keeping for bit-streams to be backward compatible. */
  /* entropy coding of model number */
  WebRtcIsac_EncHistMulti(streamdata, &kModel, WebRtcIsac_kQKltModelCdfPtr, 1);

  /* Save data for creation of multiple bit streams */
  /* Entropy coding of quantization indices - shape only. */
  WebRtcIsac_EncHistMulti(streamdata, index_s, WebRtcIsac_kQKltCdfPtrShape,
                          KLT_ORDER_SHAPE);

  /* Save data for creation of multiple bit streams. */
  for (k = 0; k < KLT_ORDER_SHAPE; k++) {
    encData->LPCindex_s[KLT_ORDER_SHAPE * encData->startIdx + k] = index_s[k];
  }

  /* Find quantization levels for shape coefficients. */
  for (k = 0; k < KLT_ORDER_SHAPE; k++) {
    tmpcoeffs_s[k] = WebRtcIsac_kQKltLevelsShape[index_ovr_s[k]];
  }
  /* Inverse KLT.  */
  /* Left transform, transpose matrix.! */
  offss = 0;
  poss = 0;
  for (j = 0; j < SUBFRAMES; j++) {
    offs2 = 0;
    for (k = 0; k < LPC_SHAPE_ORDER; k++) {
      sum = 0;
      pos = offss;
      pos2 = offs2;
      for (n = 0; n < LPC_SHAPE_ORDER; n++) {
        sum += tmpcoeffs_s[pos++] * WebRtcIsac_kKltT1Shape[pos2++];
      }
      tmpcoeffs2_s[poss++] = sum;
      offs2 += LPC_SHAPE_ORDER;
    }
    offss += LPC_SHAPE_ORDER;
  }

  /* Right transform, Transpose matrix */
  offss = 0;
  poss = 0;
  for (j = 0; j < SUBFRAMES; j++) {
    poss = offss;
    for (k = 0; k < LPC_SHAPE_ORDER; k++) {
      sum = 0;
      pos = k;
      pos2 = j;
      for (n = 0; n < SUBFRAMES; n++) {
        sum += tmpcoeffs2_s[pos] * WebRtcIsac_kKltT2Shape[pos2];
        pos += LPC_SHAPE_ORDER;
        pos2 += SUBFRAMES;
      }
      tmpcoeffs_s[poss++] = sum;
    }
    offss += LPC_SHAPE_ORDER;
  }

  /* Scaling, mean addition, and gain restoration. */
  poss = 0;
  pos = 0;
  for (k = 0; k < SUBFRAMES; k++) {
    /* Ignore gains. */
    pos += 2;

    /* Low band LAR coefficients. */
    for (n = 0; n < LPC_LOBAND_ORDER; n++, pos++, poss++) {
      LPCCoef[pos] = tmpcoeffs_s[poss] / LPC_LOBAND_SCALE;
      LPCCoef[pos] += WebRtcIsac_kLpcMeansShape[poss];
    }

    /* High band LAR coefficients. */
    for (n = 0; n < LPC_HIBAND_ORDER; n++, pos++, poss++) {
      LPCCoef[pos] = tmpcoeffs_s[poss] / LPC_HIBAND_SCALE;
      LPCCoef[pos] += WebRtcIsac_kLpcMeansShape[poss];
    }
  }
}


void WebRtcIsac_EncodeLpcLb(double* LPCCoef_lo, double* LPCCoef_hi,
                            Bitstr* streamdata, IsacSaveEncoderData* encData) {
  double lars[KLT_ORDER_GAIN + KLT_ORDER_SHAPE];
  int k;

  WebRtcIsac_Poly2Lar(LPCCoef_lo, ORDERLO, LPCCoef_hi, ORDERHI, SUBFRAMES,
                      lars);
  WebRtcIsac_EncodeLar(lars, streamdata, encData);
  WebRtcIsac_Lar2Poly(lars, LPCCoef_lo, ORDERLO, LPCCoef_hi, ORDERHI,
                      SUBFRAMES);
  /* Save data for creation of multiple bit streams (and transcoding). */
  for (k = 0; k < (ORDERLO + 1)*SUBFRAMES; k++) {
    encData->LPCcoeffs_lo[(ORDERLO + 1)*SUBFRAMES * encData->startIdx + k] =
        LPCCoef_lo[k];
  }
  for (k = 0; k < (ORDERHI + 1)*SUBFRAMES; k++) {
    encData->LPCcoeffs_hi[(ORDERHI + 1)*SUBFRAMES * encData->startIdx + k] =
        LPCCoef_hi[k];
  }
}


int16_t WebRtcIsac_EncodeLpcUB(double* lpcVecs, Bitstr* streamdata,
                               double* interpolLPCCoeff,
                               int16_t bandwidth,
                                     ISACUBSaveEncDataStruct* encData) {
  double    U[UB_LPC_ORDER * UB16_LPC_VEC_PER_FRAME];
  int     idx[UB_LPC_ORDER * UB16_LPC_VEC_PER_FRAME];
  int interpolCntr;

  WebRtcIsac_Poly2LarUB(lpcVecs, bandwidth);
  WebRtcIsac_RemoveLarMean(lpcVecs, bandwidth);
  WebRtcIsac_DecorrelateIntraVec(lpcVecs, U, bandwidth);
  WebRtcIsac_DecorrelateInterVec(U, lpcVecs, bandwidth);
  WebRtcIsac_QuantizeUncorrLar(lpcVecs, idx, bandwidth);

  WebRtcIsac_CorrelateInterVec(lpcVecs, U, bandwidth);
  WebRtcIsac_CorrelateIntraVec(U, lpcVecs, bandwidth);
  WebRtcIsac_AddLarMean(lpcVecs, bandwidth);

  switch (bandwidth) {
    case isac12kHz: {
      /* Store the indices to be used for multiple encoding. */
      memcpy(encData->indexLPCShape, idx, UB_LPC_ORDER *
             UB_LPC_VEC_PER_FRAME * sizeof(int));
      WebRtcIsac_EncHistMulti(streamdata, idx, WebRtcIsac_kLpcShapeCdfMatUb12,
                              UB_LPC_ORDER * UB_LPC_VEC_PER_FRAME);
      for (interpolCntr = 0; interpolCntr < UB_INTERPOL_SEGMENTS;
          interpolCntr++) {
        WebRtcIsac_Lar2PolyInterpolUB(lpcVecs, interpolLPCCoeff,
                                      kLpcVecPerSegmentUb12 + 1);
        lpcVecs += UB_LPC_ORDER;
        interpolLPCCoeff += (kLpcVecPerSegmentUb12 * (UB_LPC_ORDER + 1));
      }
      break;
    }
    case isac16kHz: {
      /* Store the indices to be used for multiple encoding. */
      memcpy(encData->indexLPCShape, idx, UB_LPC_ORDER *
             UB16_LPC_VEC_PER_FRAME * sizeof(int));
      WebRtcIsac_EncHistMulti(streamdata, idx, WebRtcIsac_kLpcShapeCdfMatUb16,
                              UB_LPC_ORDER * UB16_LPC_VEC_PER_FRAME);
      for (interpolCntr = 0; interpolCntr < UB16_INTERPOL_SEGMENTS;
          interpolCntr++) {
        WebRtcIsac_Lar2PolyInterpolUB(lpcVecs, interpolLPCCoeff,
                                      kLpcVecPerSegmentUb16 + 1);
        lpcVecs += UB_LPC_ORDER;
        interpolLPCCoeff += (kLpcVecPerSegmentUb16 * (UB_LPC_ORDER + 1));
      }
      break;
    }
    default:
      return -1;
  }
  return 0;
}

void WebRtcIsac_EncodeLpcGainLb(double* LPCCoef_lo, double* LPCCoef_hi,
                                Bitstr* streamdata,
                                IsacSaveEncoderData* encData) {
  int j, k, n, pos, pos2, posg, offsg, offs2;
  int index_g[KLT_ORDER_GAIN];
  int index_ovr_g[KLT_ORDER_GAIN];
  double tmpcoeffs_g[KLT_ORDER_GAIN];
  double tmpcoeffs2_g[KLT_ORDER_GAIN];
  double sum;
  /* log gains, mean removal and scaling */
  posg = 0;
  for (k = 0; k < SUBFRAMES; k++) {
    tmpcoeffs_g[posg] = log(LPCCoef_lo[(LPC_LOBAND_ORDER + 1) * k]);
    tmpcoeffs_g[posg] -= WebRtcIsac_kLpcMeansGain[posg];
    tmpcoeffs_g[posg] *= LPC_GAIN_SCALE;
    posg++;
    tmpcoeffs_g[posg] = log(LPCCoef_hi[(LPC_HIBAND_ORDER + 1) * k]);
    tmpcoeffs_g[posg] -= WebRtcIsac_kLpcMeansGain[posg];
    tmpcoeffs_g[posg] *= LPC_GAIN_SCALE;
    posg++;
  }

  /* KLT  */

  /* Left transform. */
  offsg = 0;
  for (j = 0; j < SUBFRAMES; j++) {
    posg = offsg;
    for (k = 0; k < LPC_GAIN_ORDER; k++) {
      sum = 0;
      pos = offsg;
      pos2 = k;
      for (n = 0; n < LPC_GAIN_ORDER; n++) {
        sum += tmpcoeffs_g[pos++] * WebRtcIsac_kKltT1Gain[pos2];
        pos2 += LPC_GAIN_ORDER;
      }
      tmpcoeffs2_g[posg++] = sum;
    }
    offsg += LPC_GAIN_ORDER;
  }

  /* Right transform. */
  offsg = 0;
  offs2 = 0;
  for (j = 0; j < SUBFRAMES; j++) {
    posg = offsg;
    for (k = 0; k < LPC_GAIN_ORDER; k++) {
      sum = 0;
      pos = k;
      pos2 = offs2;
      for (n = 0; n < SUBFRAMES; n++) {
        sum += tmpcoeffs2_g[pos] * WebRtcIsac_kKltT2Gain[pos2++];
        pos += LPC_GAIN_ORDER;
      }
      tmpcoeffs_g[posg++] = sum;
    }
    offs2 += SUBFRAMES;
    offsg += LPC_GAIN_ORDER;
  }

  /* Quantize coefficients. */
  for (k = 0; k < KLT_ORDER_GAIN; k++) {
    /* Get index. */
    pos2 = WebRtcIsac_lrint(tmpcoeffs_g[k] / KLT_STEPSIZE);
    index_g[k] = (pos2) + WebRtcIsac_kQKltQuantMinGain[k];
    if (index_g[k] < 0) {
      index_g[k] = 0;
    } else if (index_g[k] > WebRtcIsac_kQKltMaxIndGain[k]) {
      index_g[k] = WebRtcIsac_kQKltMaxIndGain[k];
    }
    index_ovr_g[k] = WebRtcIsac_kQKltOffsetGain[k] + index_g[k];

    /* Find quantization levels for coefficients. */
    tmpcoeffs_g[k] = WebRtcIsac_kQKltLevelsGain[index_ovr_g[k]];

    /* Save data for creation of multiple bit streams. */
    encData->LPCindex_g[KLT_ORDER_GAIN * encData->startIdx + k] = index_g[k];
  }

  /* Entropy coding of quantization indices - gain. */
  WebRtcIsac_EncHistMulti(streamdata, index_g, WebRtcIsac_kQKltCdfPtrGain,
                          KLT_ORDER_GAIN);

  /* Find quantization levels for coefficients. */
  /* Left transform. */
  offsg = 0;
  posg = 0;
  for (j = 0; j < SUBFRAMES; j++) {
    offs2 = 0;
    for (k = 0; k < LPC_GAIN_ORDER; k++) {
      sum = 0;
      pos = offsg;
      pos2 = offs2;
      for (n = 0; n < LPC_GAIN_ORDER; n++)
        sum += tmpcoeffs_g[pos++] * WebRtcIsac_kKltT1Gain[pos2++];
      tmpcoeffs2_g[posg++] = sum;
      offs2 += LPC_GAIN_ORDER;
    }
    offsg += LPC_GAIN_ORDER;
  }

  /* Right transform, transpose matrix. */
  offsg = 0;
  posg = 0;
  for (j = 0; j < SUBFRAMES; j++) {
    posg = offsg;
    for (k = 0; k < LPC_GAIN_ORDER; k++) {
      sum = 0;
      pos = k;
      pos2 = j;
      for (n = 0; n < SUBFRAMES; n++) {
        sum += tmpcoeffs2_g[pos] * WebRtcIsac_kKltT2Gain[pos2];
        pos += LPC_GAIN_ORDER;
        pos2 += SUBFRAMES;
      }
      tmpcoeffs_g[posg++] = sum;
    }
    offsg += LPC_GAIN_ORDER;
  }


  /* Scaling, mean addition, and gain restoration. */
  posg = 0;
  for (k = 0; k < SUBFRAMES; k++) {
    sum = tmpcoeffs_g[posg] / LPC_GAIN_SCALE;
    sum += WebRtcIsac_kLpcMeansGain[posg];
    LPCCoef_lo[k * (LPC_LOBAND_ORDER + 1)] = exp(sum);
    pos++;
    posg++;
    sum = tmpcoeffs_g[posg] / LPC_GAIN_SCALE;
    sum += WebRtcIsac_kLpcMeansGain[posg];
    LPCCoef_hi[k * (LPC_HIBAND_ORDER + 1)] = exp(sum);
    pos++;
    posg++;
  }

}

void WebRtcIsac_EncodeLpcGainUb(double* lpGains, Bitstr* streamdata,
                                int* lpcGainIndex) {
  double U[UB_LPC_GAIN_DIM];
  int idx[UB_LPC_GAIN_DIM];
  WebRtcIsac_ToLogDomainRemoveMean(lpGains);
  WebRtcIsac_DecorrelateLPGain(lpGains, U);
  WebRtcIsac_QuantizeLpcGain(U, idx);
  /* Store the index for re-encoding for FEC. */
  memcpy(lpcGainIndex, idx, UB_LPC_GAIN_DIM * sizeof(int));
  WebRtcIsac_CorrelateLpcGain(U, lpGains);
  WebRtcIsac_AddMeanToLinearDomain(lpGains);
  WebRtcIsac_EncHistMulti(streamdata, idx, WebRtcIsac_kLpcGainCdfMat,
                          UB_LPC_GAIN_DIM);
}


void WebRtcIsac_StoreLpcGainUb(double* lpGains, Bitstr* streamdata) {
  double U[UB_LPC_GAIN_DIM];
  int idx[UB_LPC_GAIN_DIM];
  WebRtcIsac_ToLogDomainRemoveMean(lpGains);
  WebRtcIsac_DecorrelateLPGain(lpGains, U);
  WebRtcIsac_QuantizeLpcGain(U, idx);
  WebRtcIsac_EncHistMulti(streamdata, idx, WebRtcIsac_kLpcGainCdfMat,
                          UB_LPC_GAIN_DIM);
}



int16_t WebRtcIsac_DecodeLpcGainUb(double* lpGains, Bitstr* streamdata) {
  double U[UB_LPC_GAIN_DIM];
  int idx[UB_LPC_GAIN_DIM];
  int err;
  err = WebRtcIsac_DecHistOneStepMulti(idx, streamdata,
                                       WebRtcIsac_kLpcGainCdfMat,
                                       WebRtcIsac_kLpcGainEntropySearch,
                                       UB_LPC_GAIN_DIM);
  if (err < 0) {
    return -1;
  }
  WebRtcIsac_DequantizeLpcGain(idx, U);
  WebRtcIsac_CorrelateLpcGain(U, lpGains);
  WebRtcIsac_AddMeanToLinearDomain(lpGains);
  return 0;
}



/* decode & dequantize RC */
int WebRtcIsac_DecodeRc(Bitstr* streamdata, int16_t* RCQ15) {
  int k, err;
  int index[AR_ORDER];

  /* entropy decoding of quantization indices */
  err = WebRtcIsac_DecHistOneStepMulti(index, streamdata,
                                       WebRtcIsac_kQArRcCdfPtr,
                                       WebRtcIsac_kQArRcInitIndex, AR_ORDER);
  if (err < 0)
    return err;

  /* find quantization levels for reflection coefficients */
  for (k = 0; k < AR_ORDER; k++) {
    RCQ15[k] = *(WebRtcIsac_kQArRcLevelsPtr[k] + index[k]);
  }
  return 0;
}


/* quantize & code RC */
void WebRtcIsac_EncodeRc(int16_t* RCQ15, Bitstr* streamdata) {
  int k;
  int index[AR_ORDER];

  /* quantize reflection coefficients (add noise feedback?) */
  for (k = 0; k < AR_ORDER; k++) {
    index[k] = WebRtcIsac_kQArRcInitIndex[k];
    // The safe-guards in following while conditions are to suppress gcc 4.8.3
    // warnings, Issue 2888. Otherwise, first and last elements of
    // |WebRtcIsac_kQArBoundaryLevels| are such that the following search
    // *never* cause an out-of-boundary read.
    if (RCQ15[k] > WebRtcIsac_kQArBoundaryLevels[index[k]]) {
      while (index[k] + 1 < NUM_AR_RC_QUANT_BAUNDARY &&
        RCQ15[k] > WebRtcIsac_kQArBoundaryLevels[index[k] + 1]) {
        index[k]++;
      }
    } else {
      while (index[k] > 0 &&
        RCQ15[k] < WebRtcIsac_kQArBoundaryLevels[--index[k]]) ;
    }
    RCQ15[k] = *(WebRtcIsac_kQArRcLevelsPtr[k] + index[k]);
  }

  /* entropy coding of quantization indices */
  WebRtcIsac_EncHistMulti(streamdata, index, WebRtcIsac_kQArRcCdfPtr, AR_ORDER);
}


/* decode & dequantize squared Gain */
int WebRtcIsac_DecodeGain2(Bitstr* streamdata, int32_t* gainQ10) {
  int index, err;

  /* entropy decoding of quantization index */
  err = WebRtcIsac_DecHistOneStepMulti(&index, streamdata,
                                       WebRtcIsac_kQGainCdf_ptr,
                                       WebRtcIsac_kQGainInitIndex, 1);
  if (err < 0) {
    return err;
  }
  /* find quantization level */
  *gainQ10 = WebRtcIsac_kQGain2Levels[index];
  return 0;
}


/* quantize & code squared Gain */
int WebRtcIsac_EncodeGain2(int32_t* gainQ10, Bitstr* streamdata) {
  int index;

  /* find quantization index */
  index = WebRtcIsac_kQGainInitIndex[0];
  if (*gainQ10 > WebRtcIsac_kQGain2BoundaryLevels[index]) {
    while (*gainQ10 > WebRtcIsac_kQGain2BoundaryLevels[index + 1]) {
      index++;
    }
  } else {
    while (*gainQ10 < WebRtcIsac_kQGain2BoundaryLevels[--index]) ;
  }
  /* De-quantize */
  *gainQ10 = WebRtcIsac_kQGain2Levels[index];

  /* entropy coding of quantization index */
  WebRtcIsac_EncHistMulti(streamdata, &index, WebRtcIsac_kQGainCdf_ptr, 1);
  return 0;
}


/* code and decode Pitch Gains and Lags functions */

/* decode & dequantize Pitch Gains */
int WebRtcIsac_DecodePitchGain(Bitstr* streamdata,
                               int16_t* PitchGains_Q12) {
  int index_comb, err;
  const uint16_t* WebRtcIsac_kQPitchGainCdf_ptr[1];

  /* Entropy decoding of quantization indices */
  *WebRtcIsac_kQPitchGainCdf_ptr = WebRtcIsac_kQPitchGainCdf;
  err = WebRtcIsac_DecHistBisectMulti(&index_comb, streamdata,
                                      WebRtcIsac_kQPitchGainCdf_ptr,
                                      WebRtcIsac_kQCdfTableSizeGain, 1);
  /* Error check, Q_mean_Gain.. tables are of size 144 */
  if ((err < 0) || (index_comb < 0) || (index_comb >= 144)) {
    return -ISAC_RANGE_ERROR_DECODE_PITCH_GAIN;
  }
  /* De-quantize back to pitch gains by table look-up. */
  PitchGains_Q12[0] = WebRtcIsac_kQMeanGain1Q12[index_comb];
  PitchGains_Q12[1] = WebRtcIsac_kQMeanGain2Q12[index_comb];
  PitchGains_Q12[2] = WebRtcIsac_kQMeanGain3Q12[index_comb];
  PitchGains_Q12[3] = WebRtcIsac_kQMeanGain4Q12[index_comb];
  return 0;
}


/* Quantize & code Pitch Gains. */
void WebRtcIsac_EncodePitchGain(int16_t* PitchGains_Q12,
                                Bitstr* streamdata,
                                IsacSaveEncoderData* encData) {
  int k, j;
  double C;
  double S[PITCH_SUBFRAMES];
  int index[3];
  int index_comb;
  const uint16_t* WebRtcIsac_kQPitchGainCdf_ptr[1];
  double PitchGains[PITCH_SUBFRAMES] = {0, 0, 0, 0};

  /* Take the asin. */
  for (k = 0; k < PITCH_SUBFRAMES; k++) {
    PitchGains[k] = ((float)PitchGains_Q12[k]) / 4096;
    S[k] = asin(PitchGains[k]);
  }

  /* Find quantization index; only for the first three
   * transform coefficients. */
  for (k = 0; k < 3; k++) {
    /*  transform */
    C = 0.0;
    for (j = 0; j < PITCH_SUBFRAMES; j++) {
      C += WebRtcIsac_kTransform[k][j] * S[j];
    }
    /* Quantize */
    index[k] = WebRtcIsac_lrint(C / PITCH_GAIN_STEPSIZE);

    /* Check that the index is not outside the boundaries of the table. */
    if (index[k] < WebRtcIsac_kIndexLowerLimitGain[k]) {
      index[k] = WebRtcIsac_kIndexLowerLimitGain[k];
    } else if (index[k] > WebRtcIsac_kIndexUpperLimitGain[k]) {
      index[k] = WebRtcIsac_kIndexUpperLimitGain[k];
    }
    index[k] -= WebRtcIsac_kIndexLowerLimitGain[k];
  }

  /* Calculate unique overall index. */
  index_comb = WebRtcIsac_kIndexMultsGain[0] * index[0] +
      WebRtcIsac_kIndexMultsGain[1] * index[1] + index[2];

  /* unquantize back to pitch gains by table look-up */
  PitchGains_Q12[0] = WebRtcIsac_kQMeanGain1Q12[index_comb];
  PitchGains_Q12[1] = WebRtcIsac_kQMeanGain2Q12[index_comb];
  PitchGains_Q12[2] = WebRtcIsac_kQMeanGain3Q12[index_comb];
  PitchGains_Q12[3] = WebRtcIsac_kQMeanGain4Q12[index_comb];

  /* entropy coding of quantization pitch gains */
  *WebRtcIsac_kQPitchGainCdf_ptr = WebRtcIsac_kQPitchGainCdf;
  WebRtcIsac_EncHistMulti(streamdata, &index_comb,
                          WebRtcIsac_kQPitchGainCdf_ptr, 1);
  encData->pitchGain_index[encData->startIdx] = index_comb;
}



/* Pitch LAG */
/* Decode & de-quantize Pitch Lags. */
int WebRtcIsac_DecodePitchLag(Bitstr* streamdata, int16_t* PitchGain_Q12,
                              double* PitchLags) {
  int k, err;
  double StepSize;
  double C;
  int index[PITCH_SUBFRAMES];
  double mean_gain;
  const double* mean_val2, *mean_val3, *mean_val4;
  const int16_t* lower_limit;
  const uint16_t* init_index;
  const uint16_t* cdf_size;
  const uint16_t** cdf;
  double PitchGain[4] = {0, 0, 0, 0};

  /* compute mean pitch gain */
  mean_gain = 0.0;
  for (k = 0; k < 4; k++) {
    PitchGain[k] = ((float)PitchGain_Q12[k]) / 4096;
    mean_gain += PitchGain[k];
  }
  mean_gain /= 4.0;

  /* voicing classification. */
  if (mean_gain < 0.2) {
    StepSize = WebRtcIsac_kQPitchLagStepsizeLo;
    cdf = WebRtcIsac_kQPitchLagCdfPtrLo;
    cdf_size = WebRtcIsac_kQPitchLagCdfSizeLo;
    mean_val2 = WebRtcIsac_kQMeanLag2Lo;
    mean_val3 = WebRtcIsac_kQMeanLag3Lo;
    mean_val4 = WebRtcIsac_kQMeanLag4Lo;
    lower_limit = WebRtcIsac_kQIndexLowerLimitLagLo;
    init_index = WebRtcIsac_kQInitIndexLagLo;
  } else if (mean_gain < 0.4) {
    StepSize = WebRtcIsac_kQPitchLagStepsizeMid;
    cdf = WebRtcIsac_kQPitchLagCdfPtrMid;
    cdf_size = WebRtcIsac_kQPitchLagCdfSizeMid;
    mean_val2 = WebRtcIsac_kQMeanLag2Mid;
    mean_val3 = WebRtcIsac_kQMeanLag3Mid;
    mean_val4 = WebRtcIsac_kQMeanLag4Mid;
    lower_limit = WebRtcIsac_kQIndexLowerLimitLagMid;
    init_index = WebRtcIsac_kQInitIndexLagMid;
  } else {
    StepSize = WebRtcIsac_kQPitchLagStepsizeHi;
    cdf = WebRtcIsac_kQPitchLagCdfPtrHi;
    cdf_size = WebRtcIsac_kQPitchLagCdfSizeHi;
    mean_val2 = WebRtcIsac_kQMeanLag2Hi;
    mean_val3 = WebRtcIsac_kQMeanLag3Hi;
    mean_val4 = WebRtcIsac_kQMeanLag4Hi;
    lower_limit = WebRtcIsac_kQindexLowerLimitLagHi;
    init_index = WebRtcIsac_kQInitIndexLagHi;
  }

  /* Entropy decoding of quantization indices. */
  err = WebRtcIsac_DecHistBisectMulti(index, streamdata, cdf, cdf_size, 1);
  if ((err < 0) || (index[0] < 0)) {
    return -ISAC_RANGE_ERROR_DECODE_PITCH_LAG;
  }
  err = WebRtcIsac_DecHistOneStepMulti(index + 1, streamdata, cdf + 1,
                                       init_index, 3);
  if (err < 0) {
    return -ISAC_RANGE_ERROR_DECODE_PITCH_LAG;
  }

  /* Unquantize back to transform coefficients and do the inverse transform:
   * S = T'*C. */
  C = (index[0] + lower_limit[0]) * StepSize;
  for (k = 0; k < PITCH_SUBFRAMES; k++) {
    PitchLags[k] = WebRtcIsac_kTransformTranspose[k][0] * C;
  }
  C = mean_val2[index[1]];
  for (k = 0; k < PITCH_SUBFRAMES; k++) {
    PitchLags[k] += WebRtcIsac_kTransformTranspose[k][1] * C;
  }
  C = mean_val3[index[2]];
  for (k = 0; k < PITCH_SUBFRAMES; k++) {
    PitchLags[k] += WebRtcIsac_kTransformTranspose[k][2] * C;
  }
  C = mean_val4[index[3]];
  for (k = 0; k < PITCH_SUBFRAMES; k++) {
    PitchLags[k] += WebRtcIsac_kTransformTranspose[k][3] * C;
  }
  return 0;
}



/* Quantize & code pitch lags. */
void WebRtcIsac_EncodePitchLag(double* PitchLags, int16_t* PitchGain_Q12,
                               Bitstr* streamdata,
                               IsacSaveEncoderData* encData) {
  int k, j;
  double StepSize;
  double C;
  int index[PITCH_SUBFRAMES];
  double mean_gain;
  const double* mean_val2, *mean_val3, *mean_val4;
  const int16_t* lower_limit, *upper_limit;
  const uint16_t** cdf;
  double PitchGain[4] = {0, 0, 0, 0};

  /* compute mean pitch gain */
  mean_gain = 0.0;
  for (k = 0; k < 4; k++) {
    PitchGain[k] = ((float)PitchGain_Q12[k]) / 4096;
    mean_gain += PitchGain[k];
  }
  mean_gain /= 4.0;

  /* Save data for creation of multiple bit streams */
  encData->meanGain[encData->startIdx] = mean_gain;

  /* Voicing classification. */
  if (mean_gain < 0.2) {
    StepSize = WebRtcIsac_kQPitchLagStepsizeLo;
    cdf = WebRtcIsac_kQPitchLagCdfPtrLo;
    mean_val2 = WebRtcIsac_kQMeanLag2Lo;
    mean_val3 = WebRtcIsac_kQMeanLag3Lo;
    mean_val4 = WebRtcIsac_kQMeanLag4Lo;
    lower_limit = WebRtcIsac_kQIndexLowerLimitLagLo;
    upper_limit = WebRtcIsac_kQIndexUpperLimitLagLo;
  } else if (mean_gain < 0.4) {
    StepSize = WebRtcIsac_kQPitchLagStepsizeMid;
    cdf = WebRtcIsac_kQPitchLagCdfPtrMid;
    mean_val2 = WebRtcIsac_kQMeanLag2Mid;
    mean_val3 = WebRtcIsac_kQMeanLag3Mid;
    mean_val4 = WebRtcIsac_kQMeanLag4Mid;
    lower_limit = WebRtcIsac_kQIndexLowerLimitLagMid;
    upper_limit = WebRtcIsac_kQIndexUpperLimitLagMid;
  } else {
    StepSize = WebRtcIsac_kQPitchLagStepsizeHi;
    cdf = WebRtcIsac_kQPitchLagCdfPtrHi;
    mean_val2 = WebRtcIsac_kQMeanLag2Hi;
    mean_val3 = WebRtcIsac_kQMeanLag3Hi;
    mean_val4 = WebRtcIsac_kQMeanLag4Hi;
    lower_limit = WebRtcIsac_kQindexLowerLimitLagHi;
    upper_limit = WebRtcIsac_kQindexUpperLimitLagHi;
  }

  /* find quantization index */
  for (k = 0; k < 4; k++) {
    /*  transform */
    C = 0.0;
    for (j = 0; j < PITCH_SUBFRAMES; j++) {
      C += WebRtcIsac_kTransform[k][j] * PitchLags[j];
    }
    /* quantize */
    index[k] = WebRtcIsac_lrint(C / StepSize);

    /* check that the index is not outside the boundaries of the table */
    if (index[k] < lower_limit[k]) {
      index[k] = lower_limit[k];
    } else if (index[k] > upper_limit[k]) index[k] = upper_limit[k]; {
      index[k] -= lower_limit[k];
    }
    /* Save data for creation of multiple bit streams */
    encData->pitchIndex[PITCH_SUBFRAMES * encData->startIdx + k] = index[k];
  }

  /* Un-quantize back to transform coefficients and do the inverse transform:
   * S = T'*C */
  C = (index[0] + lower_limit[0]) * StepSize;
  for (k = 0; k < PITCH_SUBFRAMES; k++) {
    PitchLags[k] = WebRtcIsac_kTransformTranspose[k][0] * C;
  }
  C = mean_val2[index[1]];
  for (k = 0; k < PITCH_SUBFRAMES; k++) {
    PitchLags[k] += WebRtcIsac_kTransformTranspose[k][1] * C;
  }
  C = mean_val3[index[2]];
  for (k = 0; k < PITCH_SUBFRAMES; k++) {
    PitchLags[k] += WebRtcIsac_kTransformTranspose[k][2] * C;
  }
  C = mean_val4[index[3]];
  for (k = 0; k < PITCH_SUBFRAMES; k++) {
    PitchLags[k] += WebRtcIsac_kTransformTranspose[k][3] * C;
  }
  /* entropy coding of quantization pitch lags */
  WebRtcIsac_EncHistMulti(streamdata, index, cdf, PITCH_SUBFRAMES);
}



/* Routines for in-band signaling of bandwidth estimation */
/* Histograms based on uniform distribution of indices */
/* Move global variables later! */


/* cdf array for frame length indicator */
const uint16_t WebRtcIsac_kFrameLengthCdf[4] = {
    0, 21845, 43690, 65535 };

/* pointer to cdf array for frame length indicator */
const uint16_t* WebRtcIsac_kFrameLengthCdf_ptr[1] = {
    WebRtcIsac_kFrameLengthCdf };

/* initial cdf index for decoder of frame length indicator */
const uint16_t WebRtcIsac_kFrameLengthInitIndex[1] = { 1 };


int WebRtcIsac_DecodeFrameLen(Bitstr* streamdata, int16_t* framesamples) {
  int frame_mode, err;
  err = 0;
  /* entropy decoding of frame length [1:30ms,2:60ms] */
  err = WebRtcIsac_DecHistOneStepMulti(&frame_mode, streamdata,
                                       WebRtcIsac_kFrameLengthCdf_ptr,
                                       WebRtcIsac_kFrameLengthInitIndex, 1);
  if (err < 0)
    return -ISAC_RANGE_ERROR_DECODE_FRAME_LENGTH;

  switch (frame_mode) {
    case 1:
      *framesamples = 480; /* 30ms */
      break;
    case 2:
      *framesamples = 960; /* 60ms */
      break;
    default:
      err = -ISAC_DISALLOWED_FRAME_MODE_DECODER;
  }
  return err;
}

int WebRtcIsac_EncodeFrameLen(int16_t framesamples, Bitstr* streamdata) {
  int frame_mode, status;

  status = 0;
  frame_mode = 0;
  /* entropy coding of frame length [1:480 samples,2:960 samples] */
  switch (framesamples) {
    case 480:
      frame_mode = 1;
      break;
    case 960:
      frame_mode = 2;
      break;
    default:
      status = - ISAC_DISALLOWED_FRAME_MODE_ENCODER;
  }

  if (status < 0)
    return status;

  WebRtcIsac_EncHistMulti(streamdata, &frame_mode,
                          WebRtcIsac_kFrameLengthCdf_ptr, 1);
  return status;
}

/* cdf array for estimated bandwidth */
static const uint16_t kBwCdf[25] = {
    0, 2731, 5461, 8192, 10923, 13653, 16384, 19114, 21845, 24576, 27306, 30037,
    32768, 35498, 38229, 40959, 43690, 46421, 49151, 51882, 54613, 57343, 60074,
    62804, 65535 };

/* pointer to cdf array for estimated bandwidth */
static const uint16_t* const kBwCdfPtr[1] = { kBwCdf };

/* initial cdf index for decoder of estimated bandwidth*/
static const uint16_t kBwInitIndex[1] = { 7 };


int WebRtcIsac_DecodeSendBW(Bitstr* streamdata, int16_t* BWno) {
  int BWno32, err;

  /* entropy decoding of sender's BW estimation [0..23] */
  err = WebRtcIsac_DecHistOneStepMulti(&BWno32, streamdata, kBwCdfPtr,
                                       kBwInitIndex, 1);
  if (err < 0) {
    return -ISAC_RANGE_ERROR_DECODE_BANDWIDTH;
  }
  *BWno = (int16_t)BWno32;
  return err;
}

void WebRtcIsac_EncodeReceiveBw(int* BWno, Bitstr* streamdata) {
  /* entropy encoding of receiver's BW estimation [0..23] */
  WebRtcIsac_EncHistMulti(streamdata, BWno, kBwCdfPtr, 1);
}


/* estimate code length of LPC Coef */
void WebRtcIsac_TranscodeLPCCoef(double* LPCCoef_lo, double* LPCCoef_hi,
                                 int* index_g) {
  int j, k, n, pos, pos2, posg, offsg, offs2;
  int index_ovr_g[KLT_ORDER_GAIN];
  double tmpcoeffs_g[KLT_ORDER_GAIN];
  double tmpcoeffs2_g[KLT_ORDER_GAIN];
  double sum;

  /* log gains, mean removal and scaling */
  posg = 0;
  for (k = 0; k < SUBFRAMES; k++) {
    tmpcoeffs_g[posg] = log(LPCCoef_lo[(LPC_LOBAND_ORDER + 1) * k]);
    tmpcoeffs_g[posg] -= WebRtcIsac_kLpcMeansGain[posg];
    tmpcoeffs_g[posg] *= LPC_GAIN_SCALE;
    posg++;
    tmpcoeffs_g[posg] = log(LPCCoef_hi[(LPC_HIBAND_ORDER + 1) * k]);
    tmpcoeffs_g[posg] -= WebRtcIsac_kLpcMeansGain[posg];
    tmpcoeffs_g[posg] *= LPC_GAIN_SCALE;
    posg++;
  }

  /* KLT  */

  /* Left transform. */
  offsg = 0;
  for (j = 0; j < SUBFRAMES; j++) {
    posg = offsg;
    for (k = 0; k < LPC_GAIN_ORDER; k++) {
      sum = 0;
      pos = offsg;
      pos2 = k;
      for (n = 0; n < LPC_GAIN_ORDER; n++) {
        sum += tmpcoeffs_g[pos++] * WebRtcIsac_kKltT1Gain[pos2];
        pos2 += LPC_GAIN_ORDER;
      }
      tmpcoeffs2_g[posg++] = sum;
    }
    offsg += LPC_GAIN_ORDER;
  }

  /* Right transform. */
  offsg = 0;
  offs2 = 0;
  for (j = 0; j < SUBFRAMES; j++) {
    posg = offsg;
    for (k = 0; k < LPC_GAIN_ORDER; k++) {
      sum = 0;
      pos = k;
      pos2 = offs2;
      for (n = 0; n < SUBFRAMES; n++) {
        sum += tmpcoeffs2_g[pos] * WebRtcIsac_kKltT2Gain[pos2++];
        pos += LPC_GAIN_ORDER;
      }
      tmpcoeffs_g[posg++] = sum;
    }
    offs2 += SUBFRAMES;
    offsg += LPC_GAIN_ORDER;
  }


  /* quantize coefficients */
  for (k = 0; k < KLT_ORDER_GAIN; k++) {
    /* Get index. */
    pos2 = WebRtcIsac_lrint(tmpcoeffs_g[k] / KLT_STEPSIZE);
    index_g[k] = (pos2) + WebRtcIsac_kQKltQuantMinGain[k];
    if (index_g[k] < 0) {
      index_g[k] = 0;
    } else if (index_g[k] > WebRtcIsac_kQKltMaxIndGain[k]) {
      index_g[k] = WebRtcIsac_kQKltMaxIndGain[k];
    }
    index_ovr_g[k] = WebRtcIsac_kQKltOffsetGain[k] + index_g[k];

    /* find quantization levels for coefficients */
    tmpcoeffs_g[k] = WebRtcIsac_kQKltLevelsGain[index_ovr_g[k]];
  }
}


/* Decode & de-quantize LPC Coefficients. */
int WebRtcIsac_DecodeLpcCoefUB(Bitstr* streamdata, double* lpcVecs,
                               double* percepFilterGains,
                               int16_t bandwidth) {
  int  index_s[KLT_ORDER_SHAPE];

  double U[UB_LPC_ORDER * UB16_LPC_VEC_PER_FRAME];
  int err;

  /* Entropy decoding of quantization indices. */
  switch (bandwidth) {
    case isac12kHz: {
      err = WebRtcIsac_DecHistOneStepMulti(
          index_s, streamdata, WebRtcIsac_kLpcShapeCdfMatUb12,
          WebRtcIsac_kLpcShapeEntropySearchUb12, UB_LPC_ORDER *
          UB_LPC_VEC_PER_FRAME);
      break;
    }
    case isac16kHz: {
      err = WebRtcIsac_DecHistOneStepMulti(
          index_s, streamdata, WebRtcIsac_kLpcShapeCdfMatUb16,
          WebRtcIsac_kLpcShapeEntropySearchUb16, UB_LPC_ORDER *
          UB16_LPC_VEC_PER_FRAME);
      break;
    }
    default:
      return -1;
  }

  if (err < 0) {
    return err;
  }

  WebRtcIsac_DequantizeLpcParam(index_s, lpcVecs, bandwidth);
  WebRtcIsac_CorrelateInterVec(lpcVecs, U, bandwidth);
  WebRtcIsac_CorrelateIntraVec(U, lpcVecs, bandwidth);
  WebRtcIsac_AddLarMean(lpcVecs, bandwidth);
  WebRtcIsac_DecodeLpcGainUb(percepFilterGains, streamdata);

  if (bandwidth == isac16kHz) {
    /* Decode another set of Gains. */
    WebRtcIsac_DecodeLpcGainUb(&percepFilterGains[SUBFRAMES], streamdata);
  }
  return 0;
}

int16_t WebRtcIsac_EncodeBandwidth(enum ISACBandwidth bandwidth,
                                   Bitstr* streamData) {
  int bandwidthMode;
  switch (bandwidth) {
    case isac12kHz: {
      bandwidthMode = 0;
      break;
    }
    case isac16kHz: {
      bandwidthMode = 1;
      break;
    }
    default:
      return -ISAC_DISALLOWED_ENCODER_BANDWIDTH;
  }
  WebRtcIsac_EncHistMulti(streamData, &bandwidthMode, kOneBitEqualProbCdf_ptr,
                          1);
  return 0;
}

int16_t WebRtcIsac_DecodeBandwidth(Bitstr* streamData,
                                   enum ISACBandwidth* bandwidth) {
  int bandwidthMode;
  if (WebRtcIsac_DecHistOneStepMulti(&bandwidthMode, streamData,
                                     kOneBitEqualProbCdf_ptr,
                                     kOneBitEqualProbInitIndex, 1) < 0) {
    return -ISAC_RANGE_ERROR_DECODE_BANDWITH;
  }
  switch (bandwidthMode) {
    case 0: {
      *bandwidth = isac12kHz;
      break;
    }
    case 1: {
      *bandwidth = isac16kHz;
      break;
    }
    default:
      return -ISAC_DISALLOWED_BANDWIDTH_MODE_DECODER;
  }
  return 0;
}

int16_t WebRtcIsac_EncodeJitterInfo(int32_t jitterIndex,
                                    Bitstr* streamData) {
  /* This is to avoid LINUX warning until we change 'int' to 'Word32'. */
  int intVar;

  if ((jitterIndex < 0) || (jitterIndex > 1)) {
    return -1;
  }
  intVar = (int)(jitterIndex);
  /* Use the same CDF table as for bandwidth
   * both take two values with equal probability.*/
  WebRtcIsac_EncHistMulti(streamData, &intVar, kOneBitEqualProbCdf_ptr, 1);
  return 0;
}

int16_t WebRtcIsac_DecodeJitterInfo(Bitstr* streamData,
                                    int32_t* jitterInfo) {
  int intVar;
  /* Use the same CDF table as for bandwidth
   * both take two values with equal probability. */
  if (WebRtcIsac_DecHistOneStepMulti(&intVar, streamData,
                                     kOneBitEqualProbCdf_ptr,
                                     kOneBitEqualProbInitIndex, 1) < 0) {
    return -ISAC_RANGE_ERROR_DECODE_BANDWITH;
  }
  *jitterInfo = (int16_t)(intVar);
  return 0;
}
