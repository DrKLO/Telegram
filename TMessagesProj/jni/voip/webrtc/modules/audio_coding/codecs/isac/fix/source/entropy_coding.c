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
 * entropy_coding.c
 *
 * This file contains all functions used to arithmetically
 * encode the iSAC bistream.
 *
 */

#include <stddef.h>

#include "common_audio/signal_processing/include/signal_processing_library.h"
#include "modules/audio_coding/codecs/isac/fix/source/arith_routins.h"
#include "modules/audio_coding/codecs/isac/fix/source/entropy_coding.h"
#include "modules/audio_coding/codecs/isac/fix/source/lpc_tables.h"
#include "modules/audio_coding/codecs/isac/fix/source/pitch_gain_tables.h"
#include "modules/audio_coding/codecs/isac/fix/source/pitch_lag_tables.h"
#include "modules/audio_coding/codecs/isac/fix/source/settings.h"
#include "modules/audio_coding/codecs/isac/fix/source/spectrum_ar_model_tables.h"
#include "rtc_base/sanitizer.h"

/*
 * Eenumerations for arguments to functions WebRtcIsacfix_MatrixProduct1()
 * and WebRtcIsacfix_MatrixProduct2().
*/

enum matrix_index_factor {
  kTIndexFactor1 = 1,
  kTIndexFactor2 = 2,
  kTIndexFactor3 = SUBFRAMES,
  kTIndexFactor4 = LPC_SHAPE_ORDER
};

enum matrix_index_step {
  kTIndexStep1 = 1,
  kTIndexStep2 = SUBFRAMES,
  kTIndexStep3 = LPC_SHAPE_ORDER
};

enum matrixprod_loop_count {
  kTLoopCount1 = SUBFRAMES,
  kTLoopCount2 = 2,
  kTLoopCount3 = LPC_SHAPE_ORDER
};

enum matrix1_shift_value {
  kTMatrix1_shift0 = 0,
  kTMatrix1_shift1 = 1,
  kTMatrix1_shift5 = 5
};

enum matrixprod_init_case {
  kTInitCase0 = 0,
  kTInitCase1 = 1
};

/*
  This function implements the fix-point correspondant function to lrint.

  FLP: (int32_t)floor(flt+.499999999999)
  FIP: (fixVal+roundVal)>>qDomain

  where roundVal = 2^(qDomain-1) = 1<<(qDomain-1)

*/
static __inline int32_t CalcLrIntQ(int32_t fixVal, int16_t qDomain) {
  return (fixVal + (1 << (qDomain - 1))) >> qDomain;
}

/*
  __inline uint32_t stepwise(int32_t dinQ10) {

  int32_t ind, diQ10, dtQ10;

  diQ10 = dinQ10;
  if (diQ10 < DPMIN_Q10)
  diQ10 = DPMIN_Q10;
  if (diQ10 >= DPMAX_Q10)
  diQ10 = DPMAX_Q10 - 1;

  dtQ10 = diQ10 - DPMIN_Q10;*/ /* Q10 + Q10 = Q10 */
/* ind = (dtQ10 * 5) >> 10;  */ /* 2^10 / 5 = 0.2 in Q10  */
/* Q10 -> Q0 */

/* return rpointsFIX_Q10[ind];

   }
*/

/* logN(x) = logN(2)*log2(x) = 0.6931*log2(x). Output in Q8. */
/* The input argument X to logN(X) is 2^17 times higher than the
   input floating point argument Y to log(Y), since the X value
   is a Q17 value. This can be compensated for after the call, by
   subraction a value Z for each Q-step. One Q-step means that
   X gets 2 thimes higher, i.e. Z = logN(2)*256 = 0.693147180559*256 =
   177.445678 should be subtracted (since logN() returns a Q8 value).
   For a X value in Q17, the value 177.445678*17 = 3017 should be
   subtracted */
static int16_t CalcLogN(int32_t arg) {
  int16_t zeros, log2, frac, logN;

  zeros=WebRtcSpl_NormU32(arg);
  frac = (int16_t)((uint32_t)((arg << zeros) & 0x7FFFFFFF) >> 23);
  log2 = (int16_t)(((31 - zeros) << 8) + frac);  // log2(x) in Q8
  logN = (int16_t)(log2 * 22713 >> 15);  // log(2) = 0.693147 = 22713 in Q15
  logN=logN+11; //Scalar compensation which minimizes the (log(x)-logN(x))^2 error over all x.

  return logN;
}


/*
  expN(x) = 2^(a*x), where a = log2(e) ~= 1.442695

  Input:  Q8  (int16_t)
  Output: Q17 (int32_t)

  a = log2(e) = log2(exp(1)) ~= 1.442695  ==>  a = 23637 in Q14 (1.442688)
  To this value, 700 is added or subtracted in order to get an average error
  nearer zero, instead of always same-sign.
*/

static int32_t CalcExpN(int16_t x) {
  int16_t axINT, axFRAC;
  int16_t exp16;
  int32_t exp;
  int16_t ax = (int16_t)(x * 23637 >> 14);  // Q8

  if (x>=0) {
    axINT = ax >> 8;  //Q0
    axFRAC = ax&0x00FF;
    exp16 = 1 << axINT;  // Q0
    axFRAC = axFRAC+256; //Q8
    exp = exp16 * axFRAC;  // Q0*Q8 = Q8
    exp <<= 9;  // Q17
  } else {
    ax = -ax;
    axINT = 1 + (ax >> 8);  //Q0
    axFRAC = 0x00FF - (ax&0x00FF);
    exp16 = (int16_t)(32768 >> axINT);  // Q15
    axFRAC = axFRAC+256; //Q8
    exp = exp16 * axFRAC;  // Q15*Q8 = Q23
    exp >>= 6;  // Q17
  }

  return exp;
}


/* compute correlation from power spectrum */
static void CalcCorrelation(int32_t *PSpecQ12, int32_t *CorrQ7)
{
  int32_t summ[FRAMESAMPLES/8];
  int32_t diff[FRAMESAMPLES/8];
  int32_t sum;
  int k, n;

  for (k = 0; k < FRAMESAMPLES/8; k++) {
    summ[k] = (PSpecQ12[k] + PSpecQ12[FRAMESAMPLES / 4 - 1 - k] + 16) >> 5;
    diff[k] = (PSpecQ12[k] - PSpecQ12[FRAMESAMPLES / 4 - 1 - k] + 16) >> 5;
  }

  sum = 2;
  for (n = 0; n < FRAMESAMPLES/8; n++)
    sum += summ[n];
  CorrQ7[0] = sum;

  for (k = 0; k < AR_ORDER; k += 2) {
    sum = 0;
    for (n = 0; n < FRAMESAMPLES/8; n++)
      sum += (WebRtcIsacfix_kCos[k][n] * diff[n] + 256) >> 9;
    CorrQ7[k+1] = sum;
  }

  for (k=1; k<AR_ORDER; k+=2) {
    sum = 0;
    for (n = 0; n < FRAMESAMPLES/8; n++)
      sum += (WebRtcIsacfix_kCos[k][n] * summ[n] + 256) >> 9;
    CorrQ7[k+1] = sum;
  }
}

// Some arithmetic operations that are allowed to overflow. (It's still
// undefined behavior, so not a good idea; this just makes UBSan ignore the
// violations, so that our old code can continue to do what it's always been
// doing.)
static inline int32_t RTC_NO_SANITIZE("signed-integer-overflow")
    OverflowingMulS16S32ToS32(int16_t a, int32_t b) {
  return a * b;
}
static inline int32_t RTC_NO_SANITIZE("signed-integer-overflow")
    OverflowingAddS32S32ToS32(int32_t a, int32_t b) {
  return a + b;
}
static inline int32_t RTC_NO_SANITIZE("signed-integer-overflow")
    OverflowingSubS32S32ToS32(int32_t a, int32_t b) {
  return a - b;
}

/* compute inverse AR power spectrum */
static void CalcInvArSpec(const int16_t *ARCoefQ12,
                          const int32_t gainQ10,
                          int32_t *CurveQ16)
{
  int32_t CorrQ11[AR_ORDER+1];
  int32_t sum, tmpGain;
  int32_t diffQ16[FRAMESAMPLES/8];
  const int16_t *CS_ptrQ9;
  int k, n;
  int16_t round, shftVal = 0, sh;

  sum = 0;
  for (n = 0; n < AR_ORDER+1; n++)
    sum += WEBRTC_SPL_MUL(ARCoefQ12[n], ARCoefQ12[n]);    /* Q24 */
  sum = ((sum >> 6) * 65 + 32768) >> 16;  /* Result in Q8. */
  CorrQ11[0] = (sum * gainQ10 + 256) >> 9;

  /* To avoid overflow, we shift down gainQ10 if it is large. We will not lose any precision */
  if(gainQ10>400000){
    tmpGain = gainQ10 >> 3;
    round = 32;
    shftVal = 6;
  } else {
    tmpGain = gainQ10;
    round = 256;
    shftVal = 9;
  }

  for (k = 1; k < AR_ORDER+1; k++) {
    sum = 16384;
    for (n = k; n < AR_ORDER+1; n++)
      sum += WEBRTC_SPL_MUL(ARCoefQ12[n-k], ARCoefQ12[n]);  /* Q24 */
    sum >>= 15;
    CorrQ11[k] = (sum * tmpGain + round) >> shftVal;
  }
  sum = CorrQ11[0] << 7;
  for (n = 0; n < FRAMESAMPLES/8; n++)
    CurveQ16[n] = sum;

  for (k = 1; k < AR_ORDER; k += 2) {
    for (n = 0; n < FRAMESAMPLES/8; n++)
      CurveQ16[n] +=
          (OverflowingMulS16S32ToS32(WebRtcIsacfix_kCos[k][n], CorrQ11[k + 1]) +
           2) >>
          2;
  }

  CS_ptrQ9 = WebRtcIsacfix_kCos[0];

  /* If CorrQ11[1] too large we avoid getting overflow in the calculation by shifting */
  sh=WebRtcSpl_NormW32(CorrQ11[1]);
  if (CorrQ11[1]==0) /* Use next correlation */
    sh=WebRtcSpl_NormW32(CorrQ11[2]);

  if (sh<9)
    shftVal = 9 - sh;
  else
    shftVal = 0;

  for (n = 0; n < FRAMESAMPLES/8; n++)
    diffQ16[n] = (CS_ptrQ9[n] * (CorrQ11[1] >> shftVal) + 2) >> 2;
  for (k = 2; k < AR_ORDER; k += 2) {
    CS_ptrQ9 = WebRtcIsacfix_kCos[k];
    for (n = 0; n < FRAMESAMPLES/8; n++)
      diffQ16[n] += (CS_ptrQ9[n] * (CorrQ11[k + 1] >> shftVal) + 2) >> 2;
  }

  for (k=0; k<FRAMESAMPLES/8; k++) {
    int32_t diff_q16 = diffQ16[k] * (1 << shftVal);
    CurveQ16[FRAMESAMPLES / 4 - 1 - k] =
        OverflowingSubS32S32ToS32(CurveQ16[k], diff_q16);
    CurveQ16[k] = OverflowingAddS32S32ToS32(CurveQ16[k], diff_q16);
  }
}

static void CalcRootInvArSpec(const int16_t *ARCoefQ12,
                              const int32_t gainQ10,
                              uint16_t *CurveQ8)
{
  int32_t CorrQ11[AR_ORDER+1];
  int32_t sum, tmpGain;
  int32_t summQ16[FRAMESAMPLES/8];
  int32_t diffQ16[FRAMESAMPLES/8];

  const int16_t *CS_ptrQ9;
  int k, n, i;
  int16_t round, shftVal = 0, sh;
  int32_t res, in_sqrt, newRes;

  sum = 0;
  for (n = 0; n < AR_ORDER+1; n++)
    sum += WEBRTC_SPL_MUL(ARCoefQ12[n], ARCoefQ12[n]);    /* Q24 */
  sum = ((sum >> 6) * 65 + 32768) >> 16;  /* Result in Q8. */
  CorrQ11[0] = (sum * gainQ10 + 256) >> 9;

  /* To avoid overflow, we shift down gainQ10 if it is large. We will not lose any precision */
  if(gainQ10>400000){
    tmpGain = gainQ10 >> 3;
    round = 32;
    shftVal = 6;
  } else {
    tmpGain = gainQ10;
    round = 256;
    shftVal = 9;
  }

  for (k = 1; k < AR_ORDER+1; k++) {
    sum = 16384;
    for (n = k; n < AR_ORDER+1; n++)
      sum += WEBRTC_SPL_MUL(ARCoefQ12[n-k], ARCoefQ12[n]);  /* Q24 */
    sum >>= 15;
    CorrQ11[k] = (sum * tmpGain + round) >> shftVal;
  }
  sum = CorrQ11[0] << 7;
  for (n = 0; n < FRAMESAMPLES/8; n++)
    summQ16[n] = sum;

  for (k = 1; k < (AR_ORDER); k += 2) {
    for (n = 0; n < FRAMESAMPLES/8; n++)
      summQ16[n] += ((CorrQ11[k + 1] * WebRtcIsacfix_kCos[k][n]) + 2) >> 2;
  }

  CS_ptrQ9 = WebRtcIsacfix_kCos[0];

  /* If CorrQ11[1] too large we avoid getting overflow in the calculation by shifting */
  sh=WebRtcSpl_NormW32(CorrQ11[1]);
  if (CorrQ11[1]==0) /* Use next correlation */
    sh=WebRtcSpl_NormW32(CorrQ11[2]);

  if (sh<9)
    shftVal = 9 - sh;
  else
    shftVal = 0;

  for (n = 0; n < FRAMESAMPLES/8; n++)
    diffQ16[n] = (CS_ptrQ9[n] * (CorrQ11[1] >> shftVal) + 2) >> 2;
  for (k = 2; k < AR_ORDER; k += 2) {
    CS_ptrQ9 = WebRtcIsacfix_kCos[k];
    for (n = 0; n < FRAMESAMPLES/8; n++)
      diffQ16[n] += (CS_ptrQ9[n] * (CorrQ11[k + 1] >> shftVal) + 2) >> 2;
  }

  in_sqrt = summQ16[0] + (diffQ16[0] << shftVal);

  /* convert to magnitude spectrum, by doing square-roots (modified from SPLIB)  */
  res = 1 << (WebRtcSpl_GetSizeInBits(in_sqrt) >> 1);

  for (k = 0; k < FRAMESAMPLES/8; k++)
  {
    in_sqrt = summQ16[k] + (diffQ16[k] << shftVal);
    i = 10;

    /* make in_sqrt positive to prohibit sqrt of negative values */
    if(in_sqrt<0)
      in_sqrt=-in_sqrt;

    newRes = (in_sqrt / res + res) >> 1;
    do
    {
      res = newRes;
      newRes = (in_sqrt / res + res) >> 1;
    } while (newRes != res && i-- > 0);

    CurveQ8[k] = (int16_t)newRes;
  }
  for (k = FRAMESAMPLES/8; k < FRAMESAMPLES/4; k++) {

    in_sqrt = summQ16[FRAMESAMPLES / 4 - 1 - k] -
        (diffQ16[FRAMESAMPLES / 4 - 1 - k] << shftVal);
    i = 10;

    /* make in_sqrt positive to prohibit sqrt of negative values */
    if(in_sqrt<0)
      in_sqrt=-in_sqrt;

    newRes = (in_sqrt / res + res) >> 1;
    do
    {
      res = newRes;
      newRes = (in_sqrt / res + res) >> 1;
    } while (newRes != res && i-- > 0);

    CurveQ8[k] = (int16_t)newRes;
  }

}



/* generate array of dither samples in Q7 */
static void GenerateDitherQ7(int16_t *bufQ7,
                             uint32_t seed,
                             int16_t length,
                             int16_t AvgPitchGain_Q12)
{
  int   k;
  int16_t dither1_Q7, dither2_Q7, dither_gain_Q14, shft;

  if (AvgPitchGain_Q12 < 614)  /* this threshold should be equal to that in decode_spec() */
  {
    for (k = 0; k < length-2; k += 3)
    {
      /* new random unsigned int32_t */
      seed = WEBRTC_SPL_UMUL(seed, 196314165) + 907633515;

      /* fixed-point dither sample between -64 and 64 (Q7) */
      dither1_Q7 = (int16_t)(((int32_t)(seed + 16777216)) >> 25);

      /* new random unsigned int32_t */
      seed = WEBRTC_SPL_UMUL(seed, 196314165) + 907633515;

      /* fixed-point dither sample between -64 and 64 */
      dither2_Q7 = (int16_t)(((int32_t)(seed + 16777216)) >> 25);

      shft = (int16_t)(WEBRTC_SPL_RSHIFT_U32(seed, 25) & 15);
      if (shft < 5)
      {
        bufQ7[k]   = dither1_Q7;
        bufQ7[k+1] = dither2_Q7;
        bufQ7[k+2] = 0;
      }
      else if (shft < 10)
      {
        bufQ7[k]   = dither1_Q7;
        bufQ7[k+1] = 0;
        bufQ7[k+2] = dither2_Q7;
      }
      else
      {
        bufQ7[k]   = 0;
        bufQ7[k+1] = dither1_Q7;
        bufQ7[k+2] = dither2_Q7;
      }
    }
  }
  else
  {
    dither_gain_Q14 = (int16_t)(22528 - WEBRTC_SPL_MUL(10, AvgPitchGain_Q12));

    /* dither on half of the coefficients */
    for (k = 0; k < length-1; k += 2)
    {
      /* new random unsigned int32_t */
      seed = WEBRTC_SPL_UMUL(seed, 196314165) + 907633515;

      /* fixed-point dither sample between -64 and 64 */
      dither1_Q7 = (int16_t)(((int32_t)(seed + 16777216)) >> 25);

      /* dither sample is placed in either even or odd index */
      shft = (int16_t)(WEBRTC_SPL_RSHIFT_U32(seed, 25) & 1);     /* either 0 or 1 */

      bufQ7[k + shft] = (int16_t)((dither_gain_Q14 * dither1_Q7 + 8192) >> 14);
      bufQ7[k + 1 - shft] = 0;
    }
  }
}




/*
 * function to decode the complex spectrum from the bitstream
 * returns the total number of bytes in the stream
 */
int WebRtcIsacfix_DecodeSpec(Bitstr_dec *streamdata,
                             int16_t *frQ7,
                             int16_t *fiQ7,
                             int16_t AvgPitchGain_Q12)
{
  int16_t  data[FRAMESAMPLES];
  int32_t  invARSpec2_Q16[FRAMESAMPLES/4];
  int16_t  ARCoefQ12[AR_ORDER+1];
  int16_t  RCQ15[AR_ORDER];
  int16_t  gainQ10;
  int32_t  gain2_Q10;
  int len;
  int          k;

  /* create dither signal */
  GenerateDitherQ7(data, streamdata->W_upper, FRAMESAMPLES, AvgPitchGain_Q12); /* Dither is output in vector 'Data' */

  /* decode model parameters */
  if (WebRtcIsacfix_DecodeRcCoef(streamdata, RCQ15) < 0)
    return -ISAC_RANGE_ERROR_DECODE_SPECTRUM;


  WebRtcSpl_ReflCoefToLpc(RCQ15, AR_ORDER, ARCoefQ12);

  if (WebRtcIsacfix_DecodeGain2(streamdata, &gain2_Q10) < 0)
    return -ISAC_RANGE_ERROR_DECODE_SPECTRUM;

  /* compute inverse AR power spectrum */
  CalcInvArSpec(ARCoefQ12, gain2_Q10, invARSpec2_Q16);

  /* arithmetic decoding of spectrum */
  /* 'data' input and output. Input = Dither */
  len = WebRtcIsacfix_DecLogisticMulti2(data, streamdata, invARSpec2_Q16, (int16_t)FRAMESAMPLES);

  if (len<1)
    return -ISAC_RANGE_ERROR_DECODE_SPECTRUM;

  /* subtract dither and scale down spectral samples with low SNR */
  if (AvgPitchGain_Q12 <= 614)
  {
    for (k = 0; k < FRAMESAMPLES; k += 4)
    {
      gainQ10 = WebRtcSpl_DivW32W16ResW16(30 << 10,
          (int16_t)((uint32_t)(invARSpec2_Q16[k >> 2] + 2195456) >> 16));
      *frQ7++ = (int16_t)((data[k] * gainQ10 + 512) >> 10);
      *fiQ7++ = (int16_t)((data[k + 1] * gainQ10 + 512) >> 10);
      *frQ7++ = (int16_t)((data[k + 2] * gainQ10 + 512) >> 10);
      *fiQ7++ = (int16_t)((data[k + 3] * gainQ10 + 512) >> 10);
    }
  }
  else
  {
    for (k = 0; k < FRAMESAMPLES; k += 4)
    {
      gainQ10 = WebRtcSpl_DivW32W16ResW16(36 << 10,
          (int16_t)((uint32_t)(invARSpec2_Q16[k >> 2] + 2654208) >> 16));
      *frQ7++ = (int16_t)((data[k] * gainQ10 + 512) >> 10);
      *fiQ7++ = (int16_t)((data[k + 1] * gainQ10 + 512) >> 10);
      *frQ7++ = (int16_t)((data[k + 2] * gainQ10 + 512) >> 10);
      *fiQ7++ = (int16_t)((data[k + 3] * gainQ10 + 512) >> 10);
    }
  }

  return len;
}


int WebRtcIsacfix_EncodeSpec(const int16_t *fr,
                             const int16_t *fi,
                             Bitstr_enc *streamdata,
                             int16_t AvgPitchGain_Q12)
{
  int16_t  dataQ7[FRAMESAMPLES];
  int32_t  PSpec[FRAMESAMPLES/4];
  uint16_t invARSpecQ8[FRAMESAMPLES/4];
  int32_t  CorrQ7[AR_ORDER+1];
  int32_t  CorrQ7_norm[AR_ORDER+1];
  int16_t  RCQ15[AR_ORDER];
  int16_t  ARCoefQ12[AR_ORDER+1];
  int32_t  gain2_Q10;
  int16_t  val;
  int32_t  nrg;
  uint32_t sum;
  int16_t  lft_shft;
  int16_t  status;
  int          k, n, j;


  /* create dither_float signal */
  GenerateDitherQ7(dataQ7, streamdata->W_upper, FRAMESAMPLES, AvgPitchGain_Q12);

  /* add dither and quantize, and compute power spectrum */
  /* Vector dataQ7 contains Dither in Q7 */
  for (k = 0; k < FRAMESAMPLES; k += 4)
  {
    val = ((*fr++ + dataQ7[k]   + 64) & 0xFF80) - dataQ7[k]; /* Data = Dither */
    dataQ7[k] = val;            /* New value in Data */
    sum = WEBRTC_SPL_UMUL(val, val);

    val = ((*fi++ + dataQ7[k+1] + 64) & 0xFF80) - dataQ7[k+1]; /* Data = Dither */
    dataQ7[k+1] = val;            /* New value in Data */
    sum += WEBRTC_SPL_UMUL(val, val);

    val = ((*fr++ + dataQ7[k+2] + 64) & 0xFF80) - dataQ7[k+2]; /* Data = Dither */
    dataQ7[k+2] = val;            /* New value in Data */
    sum += WEBRTC_SPL_UMUL(val, val);

    val = ((*fi++ + dataQ7[k+3] + 64) & 0xFF80) - dataQ7[k+3]; /* Data = Dither */
    dataQ7[k+3] = val;            /* New value in Data */
    sum += WEBRTC_SPL_UMUL(val, val);

    PSpec[k>>2] = WEBRTC_SPL_RSHIFT_U32(sum, 2);
  }

  /* compute correlation from power spectrum */
  CalcCorrelation(PSpec, CorrQ7);


  /* find AR coefficients */
  /* number of bit shifts to 14-bit normalize CorrQ7[0] (leaving room for sign) */
  lft_shft = WebRtcSpl_NormW32(CorrQ7[0]) - 18;

  if (lft_shft > 0) {
    for (k=0; k<AR_ORDER+1; k++)
      CorrQ7_norm[k] = CorrQ7[k] << lft_shft;
  } else {
    for (k=0; k<AR_ORDER+1; k++)
      CorrQ7_norm[k] = CorrQ7[k] >> -lft_shft;
  }

  /* find RC coefficients */
  WebRtcSpl_AutoCorrToReflCoef(CorrQ7_norm, AR_ORDER, RCQ15);

  /* quantize & code RC Coef */
  status = WebRtcIsacfix_EncodeRcCoef(RCQ15, streamdata);
  if (status < 0) {
    return status;
  }

  /* RC -> AR coefficients */
  WebRtcSpl_ReflCoefToLpc(RCQ15, AR_ORDER, ARCoefQ12);

  /* compute ARCoef' * Corr * ARCoef in Q19 */
  nrg = 0;
  for (j = 0; j <= AR_ORDER; j++) {
    for (n = 0; n <= j; n++)
      nrg += (ARCoefQ12[j] * ((CorrQ7_norm[j - n] * ARCoefQ12[n] + 256) >> 9) +
          4) >> 3;
    for (n = j+1; n <= AR_ORDER; n++)
      nrg += (ARCoefQ12[j] * ((CorrQ7_norm[n - j] * ARCoefQ12[n] + 256) >> 9) +
          4) >> 3;
  }

  if (lft_shft > 0)
    nrg >>= lft_shft;
  else
    nrg <<= -lft_shft;

  if(nrg>131072)
    gain2_Q10 = WebRtcSpl_DivResultInQ31(FRAMESAMPLES >> 2, nrg);  /* also shifts 31 bits to the left! */
  else
    gain2_Q10 = FRAMESAMPLES >> 2;

  /* quantize & code gain2_Q10 */
  if (WebRtcIsacfix_EncodeGain2(&gain2_Q10, streamdata))
    return -1;

  /* compute inverse AR magnitude spectrum */
  CalcRootInvArSpec(ARCoefQ12, gain2_Q10, invARSpecQ8);


  /* arithmetic coding of spectrum */
  status = WebRtcIsacfix_EncLogisticMulti2(streamdata, dataQ7, invARSpecQ8, (int16_t)FRAMESAMPLES);
  if ( status )
    return( status );

  return 0;
}


/* Matlab's LAR definition */
static void Rc2LarFix(const int16_t *rcQ15, int32_t *larQ17, int16_t order) {

  /*

    This is a piece-wise implemenetation of a rc2lar-function (all values in the comment
    are Q15 values and  are based on [0 24956/32768 30000/32768 32500/32768], i.e.
    [0.76159667968750   0.91552734375000   0.99182128906250]

    x0  x1           a                 k              x0(again)         b
    ==================================================================================
    0.00 0.76:   0                  2.625997508581   0                  0
    0.76 0.91:   2.000012018559     7.284502668663   0.761596679688    -3.547841027073
    0.91 0.99:   3.121320351712    31.115835041229   0.915527343750   -25.366077452148
    0.99 1.00:   5.495270168700   686.663805654056   0.991821289063  -675.552510708011

    The implementation is y(x)= a + (x-x0)*k, but this can be simplified to

    y(x) = a-x0*k + x*k = b + x*k, where b = a-x0*k

    akx=[0                 2.625997508581   0
    2.000012018559     7.284502668663   0.761596679688
    3.121320351712    31.115835041229   0.915527343750
    5.495270168700   686.663805654056   0.991821289063];

    b = akx(:,1) - akx(:,3).*akx(:,2)

    [ 0.0
    -3.547841027073
    -25.366077452148
    -675.552510708011]

  */

  int k;
  int16_t rc;
  int32_t larAbsQ17;

  for (k = 0; k < order; k++) {

    rc = WEBRTC_SPL_ABS_W16(rcQ15[k]); //Q15

    /* Calculate larAbsQ17 in Q17 from rc in Q15 */

    if (rc<24956) {  //0.7615966 in Q15
      // (Q15*Q13)>>11 = Q17
      larAbsQ17 = rc * 21512 >> 11;
    } else if (rc<30000) { //0.91552734375 in Q15
      // Q17 + (Q15*Q12)>>10 = Q17
      larAbsQ17 = -465024 + (rc * 29837 >> 10);
    } else if (rc<32500) { //0.99182128906250 in Q15
      // Q17 + (Q15*Q10)>>8 = Q17
      larAbsQ17 = -3324784 + (rc * 31863 >> 8);
    } else  {
      // Q17 + (Q15*Q5)>>3 = Q17
      larAbsQ17 = -88546020 + (rc * 21973 >> 3);
    }

    if (rcQ15[k]>0) {
      larQ17[k] = larAbsQ17;
    } else {
      larQ17[k] = -larAbsQ17;
    }
  }
}


static void Lar2RcFix(const int32_t *larQ17, int16_t *rcQ15,  int16_t order) {

  /*
    This is a piece-wise implemenetation of a lar2rc-function
    See comment in Rc2LarFix() about details.
  */

  int k;
  int16_t larAbsQ11;
  int32_t rc;

  for (k = 0; k < order; k++) {

    larAbsQ11 = (int16_t)WEBRTC_SPL_ABS_W32((larQ17[k] + 32) >> 6);  // Q11

    if (larAbsQ11<4097) { //2.000012018559 in Q11
      // Q11*Q16>>12 = Q15
      rc = larAbsQ11 * 24957 >> 12;
    } else if (larAbsQ11<6393) { //3.121320351712 in Q11
      // (Q11*Q17 + Q13)>>13 = Q15
      rc = (larAbsQ11 * 17993 + 130738688) >> 13;
    } else if (larAbsQ11<11255) { //5.495270168700 in Q11
      // (Q11*Q19 + Q30)>>15 = Q15
      rc = (larAbsQ11 * 16850 + 875329820) >> 15;
    } else  {
      // (Q11*Q24>>16 + Q19)>>4 = Q15
      rc = (((larAbsQ11 * 24433) >> 16) + 515804) >> 4;
    }

    if (larQ17[k]<=0) {
      rc = -rc;
    }

    rcQ15[k] = (int16_t) rc;  // Q15
  }
}

static void Poly2LarFix(int16_t *lowbandQ15,
                        int16_t orderLo,
                        int16_t *hibandQ15,
                        int16_t orderHi,
                        int16_t Nsub,
                        int32_t *larsQ17) {

  int k, n;
  int32_t *outpQ17;
  int16_t orderTot;
  int32_t larQ17[MAX_ORDER];   // Size 7+6 is enough

  orderTot = (orderLo + orderHi);
  outpQ17 = larsQ17;
  for (k = 0; k < Nsub; k++) {

    Rc2LarFix(lowbandQ15, larQ17, orderLo);

    for (n = 0; n < orderLo; n++)
      outpQ17[n] = larQ17[n]; //Q17

    Rc2LarFix(hibandQ15, larQ17, orderHi);

    for (n = 0; n < orderHi; n++)
      outpQ17[n + orderLo] = larQ17[n]; //Q17;

    outpQ17 += orderTot;
    lowbandQ15 += orderLo;
    hibandQ15 += orderHi;
  }
}


static void Lar2polyFix(int32_t *larsQ17,
                        int16_t *lowbandQ15,
                        int16_t orderLo,
                        int16_t *hibandQ15,
                        int16_t orderHi,
                        int16_t Nsub) {

  int k, n;
  int16_t orderTot;
  int16_t *outplQ15, *outphQ15;
  int32_t *inpQ17;
  int16_t rcQ15[7+6];

  orderTot = (orderLo + orderHi);
  outplQ15 = lowbandQ15;
  outphQ15 = hibandQ15;
  inpQ17 = larsQ17;
  for (k = 0; k < Nsub; k++) {

    /* gains not handled here as in the FLP version */

    /* Low band */
    Lar2RcFix(&inpQ17[0], rcQ15, orderLo);
    for (n = 0; n < orderLo; n++)
      outplQ15[n] = rcQ15[n]; // Refl. coeffs

    /* High band */
    Lar2RcFix(&inpQ17[orderLo], rcQ15, orderHi);
    for (n = 0; n < orderHi; n++)
      outphQ15[n] = rcQ15[n]; // Refl. coeffs

    inpQ17 += orderTot;
    outplQ15 += orderLo;
    outphQ15 += orderHi;
  }
}

/*
Function WebRtcIsacfix_MatrixProduct1C() does one form of matrix multiplication.
It first shifts input data of one matrix, determines the right indexes for the
two matrixes, multiply them, and write the results into an output buffer.

Note that two factors (or, multipliers) determine the initialization values of
the variable |matrix1_index| in the code. The relationship is
|matrix1_index| = |matrix1_index_factor1| * |matrix1_index_factor2|, where
|matrix1_index_factor1| is given by the argument while |matrix1_index_factor2|
is determined by the value of argument |matrix1_index_init_case|;
|matrix1_index_factor2| is the value of the outmost loop counter j (when
|matrix1_index_init_case| is 0), or the value of the middle loop counter k (when
|matrix1_index_init_case| is non-zero).

|matrix0_index| is determined the same way.

Arguments:
  matrix0[]:                 matrix0 data in Q15 domain.
  matrix1[]:                 matrix1 data.
  matrix_product[]:          output data (matrix product).
  matrix1_index_factor1:     The first of two factors determining the
                             initialization value of matrix1_index.
  matrix0_index_factor1:     The first of two factors determining the
                             initialization value of matrix0_index.
  matrix1_index_init_case:   Case number for selecting the second of two
                             factors determining the initialization value
                             of matrix1_index and matrix0_index.
  matrix1_index_step:        Incremental step for matrix1_index.
  matrix0_index_step:        Incremental step for matrix0_index.
  inner_loop_count:          Maximum count of the inner loop.
  mid_loop_count:            Maximum count of the intermediate loop.
  shift:                     Left shift value for matrix1.
*/
void WebRtcIsacfix_MatrixProduct1C(const int16_t matrix0[],
                                   const int32_t matrix1[],
                                   int32_t matrix_product[],
                                   const int matrix1_index_factor1,
                                   const int matrix0_index_factor1,
                                   const int matrix1_index_init_case,
                                   const int matrix1_index_step,
                                   const int matrix0_index_step,
                                   const int inner_loop_count,
                                   const int mid_loop_count,
                                   const int shift) {
  int j = 0, k = 0, n = 0;
  int matrix0_index = 0, matrix1_index = 0, matrix_prod_index = 0;
  int* matrix0_index_factor2 = &k;
  int* matrix1_index_factor2 = &j;
  if (matrix1_index_init_case != 0) {
    matrix0_index_factor2 = &j;
    matrix1_index_factor2 = &k;
  }

  for (j = 0; j < SUBFRAMES; j++) {
    matrix_prod_index = mid_loop_count * j;
    for (k = 0; k < mid_loop_count; k++) {
      int32_t sum32 = 0;
      matrix0_index = matrix0_index_factor1 * (*matrix0_index_factor2);
      matrix1_index = matrix1_index_factor1 * (*matrix1_index_factor2);
      for (n = 0; n < inner_loop_count; n++) {
        sum32 += WEBRTC_SPL_MUL_16_32_RSFT16(
            matrix0[matrix0_index], matrix1[matrix1_index] * (1 << shift));
        matrix0_index += matrix0_index_step;
        matrix1_index += matrix1_index_step;
      }
      matrix_product[matrix_prod_index] = sum32;
      matrix_prod_index++;
    }
  }
}

/*
Function WebRtcIsacfix_MatrixProduct2C() returns the product of two matrixes,
one of which has two columns. It first has to determine the correct index of
the first matrix before doing the actual element multiplication.

Arguments:
  matrix0[]:                 A matrix in Q15 domain.
  matrix1[]:                 A matrix in Q21 domain.
  matrix_product[]:          Output data in Q17 domain.
  matrix0_index_factor:      A factor determining the initialization value
                             of matrix0_index.
  matrix0_index_step:        Incremental step for matrix0_index.
*/
void WebRtcIsacfix_MatrixProduct2C(const int16_t matrix0[],
                                   const int32_t matrix1[],
                                   int32_t matrix_product[],
                                   const int matrix0_index_factor,
                                   const int matrix0_index_step) {
  int j = 0, n = 0;
  int matrix1_index = 0, matrix0_index = 0, matrix_prod_index = 0;
  for (j = 0; j < SUBFRAMES; j++) {
    int32_t sum32 = 0, sum32_2 = 0;
    matrix1_index = 0;
    matrix0_index = matrix0_index_factor * j;
    for (n = SUBFRAMES; n > 0; n--) {
      sum32 += (WEBRTC_SPL_MUL_16_32_RSFT16(matrix0[matrix0_index],
                                            matrix1[matrix1_index]));
      sum32_2 += (WEBRTC_SPL_MUL_16_32_RSFT16(matrix0[matrix0_index],
                                            matrix1[matrix1_index + 1]));
      matrix1_index += 2;
      matrix0_index += matrix0_index_step;
    }
    matrix_product[matrix_prod_index] = sum32 >> 3;
    matrix_product[matrix_prod_index + 1] = sum32_2 >> 3;
    matrix_prod_index += 2;
  }
}

int WebRtcIsacfix_DecodeLpc(int32_t *gain_lo_hiQ17,
                            int16_t *LPCCoef_loQ15,
                            int16_t *LPCCoef_hiQ15,
                            Bitstr_dec *streamdata,
                            int16_t *outmodel) {

  int32_t larsQ17[KLT_ORDER_SHAPE]; // KLT_ORDER_GAIN+KLT_ORDER_SHAPE == (ORDERLO+ORDERHI)*SUBFRAMES
  int err;

  err = WebRtcIsacfix_DecodeLpcCoef(streamdata, larsQ17, gain_lo_hiQ17, outmodel);
  if (err<0)  // error check
    return -ISAC_RANGE_ERROR_DECODE_LPC;

  Lar2polyFix(larsQ17, LPCCoef_loQ15, ORDERLO, LPCCoef_hiQ15, ORDERHI, SUBFRAMES);

  return 0;
}

/* decode & dequantize LPC Coef */
int WebRtcIsacfix_DecodeLpcCoef(Bitstr_dec *streamdata,
                                int32_t *LPCCoefQ17,
                                int32_t *gain_lo_hiQ17,
                                int16_t *outmodel)
{
  int j, k, n;
  int err;
  int16_t pos, pos2, posg, poss;
  int16_t gainpos;
  int16_t model;
  int16_t index_QQ[KLT_ORDER_SHAPE];
  int32_t tmpcoeffs_gQ17[KLT_ORDER_GAIN];
  int32_t tmpcoeffs2_gQ21[KLT_ORDER_GAIN];
  int16_t tmpcoeffs_sQ10[KLT_ORDER_SHAPE];
  int32_t tmpcoeffs_sQ17[KLT_ORDER_SHAPE];
  int32_t tmpcoeffs2_sQ18[KLT_ORDER_SHAPE];
  int32_t sumQQ;
  int16_t sumQQ16;
  int32_t tmp32;



  /* entropy decoding of model number */
  err = WebRtcIsacfix_DecHistOneStepMulti(&model, streamdata, WebRtcIsacfix_kModelCdfPtr, WebRtcIsacfix_kModelInitIndex, 1);
  if (err<0)  // error check
    return err;

  /* entropy decoding of quantization indices */
  err = WebRtcIsacfix_DecHistOneStepMulti(index_QQ, streamdata, WebRtcIsacfix_kCdfShapePtr[model], WebRtcIsacfix_kInitIndexShape[model], KLT_ORDER_SHAPE);
  if (err<0)  // error check
    return err;
  /* find quantization levels for coefficients */
  for (k=0; k<KLT_ORDER_SHAPE; k++) {
    tmpcoeffs_sQ10[WebRtcIsacfix_kSelIndShape[k]] = WebRtcIsacfix_kLevelsShapeQ10[WebRtcIsacfix_kOfLevelsShape[model]+WebRtcIsacfix_kOffsetShape[model][k] + index_QQ[k]];
  }

  err = WebRtcIsacfix_DecHistOneStepMulti(index_QQ, streamdata, WebRtcIsacfix_kCdfGainPtr[model], WebRtcIsacfix_kInitIndexGain[model], KLT_ORDER_GAIN);
  if (err<0)  // error check
    return err;
  /* find quantization levels for coefficients */
  for (k=0; k<KLT_ORDER_GAIN; k++) {
    tmpcoeffs_gQ17[WebRtcIsacfix_kSelIndGain[k]] = WebRtcIsacfix_kLevelsGainQ17[WebRtcIsacfix_kOfLevelsGain[model]+ WebRtcIsacfix_kOffsetGain[model][k] + index_QQ[k]];
  }


  /* inverse KLT  */

  /* left transform */  // Transpose matrix!
  WebRtcIsacfix_MatrixProduct1(WebRtcIsacfix_kT1GainQ15[model], tmpcoeffs_gQ17,
                               tmpcoeffs2_gQ21, kTIndexFactor2, kTIndexFactor2,
                               kTInitCase0, kTIndexStep1, kTIndexStep1,
                               kTLoopCount2, kTLoopCount2, kTMatrix1_shift5);

  poss = 0;
  for (j=0; j<SUBFRAMES; j++) {
    for (k=0; k<LPC_SHAPE_ORDER; k++) {
      sumQQ = 0;
      pos = LPC_SHAPE_ORDER * j;
      pos2 = LPC_SHAPE_ORDER * k;
      for (n=0; n<LPC_SHAPE_ORDER; n++) {
        sumQQ += tmpcoeffs_sQ10[pos] *
            WebRtcIsacfix_kT1ShapeQ15[model][pos2] >> 7;  // (Q10*Q15)>>7 = Q18
        pos++;
        pos2++;
      }
      tmpcoeffs2_sQ18[poss] = sumQQ; //Q18
      poss++;
    }
  }

  /* right transform */ // Transpose matrix
  WebRtcIsacfix_MatrixProduct2(WebRtcIsacfix_kT2GainQ15[0], tmpcoeffs2_gQ21,
                               tmpcoeffs_gQ17, kTIndexFactor1, kTIndexStep2);
  WebRtcIsacfix_MatrixProduct1(WebRtcIsacfix_kT2ShapeQ15[model],
      tmpcoeffs2_sQ18, tmpcoeffs_sQ17, kTIndexFactor1, kTIndexFactor1,
      kTInitCase1, kTIndexStep3, kTIndexStep2, kTLoopCount1, kTLoopCount3,
      kTMatrix1_shift0);

  /* scaling, mean addition, and gain restoration */
  gainpos = 0;
  posg = 0;poss = 0;pos=0;
  for (k=0; k<SUBFRAMES; k++) {

    /* log gains */
    // Divide by 4 and get Q17 to Q8, i.e. shift 2+9.
    sumQQ16 = (int16_t)(tmpcoeffs_gQ17[posg] >> 11);
    sumQQ16 += WebRtcIsacfix_kMeansGainQ8[model][posg];
    sumQQ = CalcExpN(sumQQ16); // Q8 in and Q17 out
    gain_lo_hiQ17[gainpos] = sumQQ; //Q17
    gainpos++;
    posg++;

    // Divide by 4 and get Q17 to Q8, i.e. shift 2+9.
    sumQQ16 = (int16_t)(tmpcoeffs_gQ17[posg] >> 11);
    sumQQ16 += WebRtcIsacfix_kMeansGainQ8[model][posg];
    sumQQ = CalcExpN(sumQQ16); // Q8 in and Q17 out
    gain_lo_hiQ17[gainpos] = sumQQ; //Q17
    gainpos++;
    posg++;

    /* lo band LAR coeffs */
    for (n=0; n<ORDERLO; n++, pos++, poss++) {
      tmp32 = WEBRTC_SPL_MUL_16_32_RSFT16(31208, tmpcoeffs_sQ17[poss]); // (Q16*Q17)>>16 = Q17, with 1/2.1 = 0.47619047619 ~= 31208 in Q16
      tmp32 = tmp32 + WebRtcIsacfix_kMeansShapeQ17[model][poss]; // Q17+Q17 = Q17
      LPCCoefQ17[pos] = tmp32;
    }

    /* hi band LAR coeffs */
    for (n=0; n<ORDERHI; n++, pos++, poss++) {
      // ((Q13*Q17)>>16)<<3 = Q17, with 1/0.45 = 2.222222222222 ~= 18204 in Q13
      tmp32 =
          WEBRTC_SPL_MUL_16_32_RSFT16(18204, tmpcoeffs_sQ17[poss]) * (1 << 3);
      tmp32 = tmp32 + WebRtcIsacfix_kMeansShapeQ17[model][poss]; // Q17+Q17 = Q17
      LPCCoefQ17[pos] = tmp32;
    }
  }


  *outmodel=model;

  return 0;
}

/* estimate codel length of LPC Coef */
static int EstCodeLpcCoef(int32_t *LPCCoefQ17,
                          int32_t *gain_lo_hiQ17,
                          int16_t *model,
                          int32_t *sizeQ11,
                          Bitstr_enc *streamdata,
                          IsacSaveEncoderData* encData,
                          transcode_obj *transcodingParam) {
  int j, k, n;
  int16_t posQQ, pos2QQ, gainpos;
  int16_t  pos, poss, posg, offsg;
  int16_t index_gQQ[KLT_ORDER_GAIN], index_sQQ[KLT_ORDER_SHAPE];
  int16_t index_ovr_gQQ[KLT_ORDER_GAIN], index_ovr_sQQ[KLT_ORDER_SHAPE];
  int32_t BitsQQ;

  int16_t tmpcoeffs_gQ6[KLT_ORDER_GAIN];
  int32_t tmpcoeffs_gQ17[KLT_ORDER_GAIN];
  int32_t tmpcoeffs_sQ17[KLT_ORDER_SHAPE];
  int32_t tmpcoeffs2_gQ21[KLT_ORDER_GAIN];
  int32_t tmpcoeffs2_sQ17[KLT_ORDER_SHAPE];
  int32_t sumQQ;
  int32_t tmp32;
  int16_t sumQQ16;
  int status = 0;

  /* write LAR coefficients to statistics file */
  /* Save data for creation of multiple bitstreams (and transcoding) */
  if (encData != NULL) {
    for (k=0; k<KLT_ORDER_GAIN; k++) {
      encData->LPCcoeffs_g[KLT_ORDER_GAIN*encData->startIdx + k] = gain_lo_hiQ17[k];
    }
  }

  /* log gains, mean removal and scaling */
  posg = 0;poss = 0;pos=0; gainpos=0;

  for (k=0; k<SUBFRAMES; k++) {
    /* log gains */

    /* The input argument X to logN(X) is 2^17 times higher than the
       input floating point argument Y to log(Y), since the X value
       is a Q17 value. This can be compensated for after the call, by
       subraction a value Z for each Q-step. One Q-step means that
       X gets 2 times higher, i.e. Z = logN(2)*256 = 0.693147180559*256 =
       177.445678 should be subtracted (since logN() returns a Q8 value).
       For a X value in Q17, the value 177.445678*17 = 3017 should be
       subtracted */
    tmpcoeffs_gQ6[posg] = CalcLogN(gain_lo_hiQ17[gainpos])-3017; //Q8
    tmpcoeffs_gQ6[posg] -= WebRtcIsacfix_kMeansGainQ8[0][posg]; //Q8, but Q6 after not-needed mult. by 4
    posg++; gainpos++;

    tmpcoeffs_gQ6[posg] = CalcLogN(gain_lo_hiQ17[gainpos])-3017; //Q8
    tmpcoeffs_gQ6[posg] -= WebRtcIsacfix_kMeansGainQ8[0][posg]; //Q8, but Q6 after not-needed mult. by 4
    posg++; gainpos++;

    /* lo band LAR coeffs */
    for (n=0; n<ORDERLO; n++, poss++, pos++) {
      tmp32 = LPCCoefQ17[pos] - WebRtcIsacfix_kMeansShapeQ17[0][poss]; //Q17
      tmp32 = WEBRTC_SPL_MUL_16_32_RSFT16(17203, tmp32<<3); // tmp32 = 2.1*tmp32
      tmpcoeffs_sQ17[poss] = tmp32; //Q17
    }

    /* hi band LAR coeffs */
    for (n=0; n<ORDERHI; n++, poss++, pos++) {
      tmp32 = LPCCoefQ17[pos] - WebRtcIsacfix_kMeansShapeQ17[0][poss]; //Q17
      tmp32 = WEBRTC_SPL_MUL_16_32_RSFT16(14746, tmp32<<1); // tmp32 = 0.45*tmp32
      tmpcoeffs_sQ17[poss] = tmp32; //Q17
    }

  }


  /* KLT  */

  /* left transform */
  offsg = 0;
  posg = 0;
  for (j=0; j<SUBFRAMES; j++) {
    // Q21 = Q6 * Q15
    sumQQ = tmpcoeffs_gQ6[offsg] * WebRtcIsacfix_kT1GainQ15[0][0] +
        tmpcoeffs_gQ6[offsg + 1] * WebRtcIsacfix_kT1GainQ15[0][2];
    tmpcoeffs2_gQ21[posg] = sumQQ;
    posg++;

    // Q21 = Q6 * Q15
    sumQQ = tmpcoeffs_gQ6[offsg] * WebRtcIsacfix_kT1GainQ15[0][1] +
        tmpcoeffs_gQ6[offsg + 1] * WebRtcIsacfix_kT1GainQ15[0][3];
    tmpcoeffs2_gQ21[posg] = sumQQ;
    posg++;

    offsg += 2;
  }

  WebRtcIsacfix_MatrixProduct1(WebRtcIsacfix_kT1ShapeQ15[0], tmpcoeffs_sQ17,
      tmpcoeffs2_sQ17, kTIndexFactor4, kTIndexFactor1, kTInitCase0,
      kTIndexStep1, kTIndexStep3, kTLoopCount3, kTLoopCount3, kTMatrix1_shift1);

  /* right transform */
  WebRtcIsacfix_MatrixProduct2(WebRtcIsacfix_kT2GainQ15[0], tmpcoeffs2_gQ21,
                               tmpcoeffs_gQ17, kTIndexFactor3, kTIndexStep1);

  WebRtcIsacfix_MatrixProduct1(WebRtcIsacfix_kT2ShapeQ15[0], tmpcoeffs2_sQ17,
      tmpcoeffs_sQ17, kTIndexFactor1, kTIndexFactor3, kTInitCase1, kTIndexStep3,
      kTIndexStep1, kTLoopCount1, kTLoopCount3, kTMatrix1_shift1);

  /* quantize coefficients */

  BitsQQ = 0;
  for (k=0; k<KLT_ORDER_GAIN; k++) //ATTN: ok?
  {
    posQQ = WebRtcIsacfix_kSelIndGain[k];
    pos2QQ= (int16_t)CalcLrIntQ(tmpcoeffs_gQ17[posQQ], 17);

    index_gQQ[k] = pos2QQ + WebRtcIsacfix_kQuantMinGain[k]; //ATTN: ok?
    if (index_gQQ[k] < 0) {
      index_gQQ[k] = 0;
    }
    else if (index_gQQ[k] > WebRtcIsacfix_kMaxIndGain[k]) {
      index_gQQ[k] = WebRtcIsacfix_kMaxIndGain[k];
    }
    index_ovr_gQQ[k] = WebRtcIsacfix_kOffsetGain[0][k]+index_gQQ[k];
    posQQ = WebRtcIsacfix_kOfLevelsGain[0] + index_ovr_gQQ[k];

    /* Save data for creation of multiple bitstreams */
    if (encData != NULL) {
      encData->LPCindex_g[KLT_ORDER_GAIN*encData->startIdx + k] = index_gQQ[k];
    }

    /* determine number of bits */
    sumQQ = WebRtcIsacfix_kCodeLenGainQ11[posQQ]; //Q11
    BitsQQ += sumQQ;
  }

  for (k=0; k<KLT_ORDER_SHAPE; k++) //ATTN: ok?
  {
    index_sQQ[k] = (int16_t)(CalcLrIntQ(tmpcoeffs_sQ17[WebRtcIsacfix_kSelIndShape[k]], 17) + WebRtcIsacfix_kQuantMinShape[k]); //ATTN: ok?

    if (index_sQQ[k] < 0)
      index_sQQ[k] = 0;
    else if (index_sQQ[k] > WebRtcIsacfix_kMaxIndShape[k])
      index_sQQ[k] = WebRtcIsacfix_kMaxIndShape[k];
    index_ovr_sQQ[k] = WebRtcIsacfix_kOffsetShape[0][k]+index_sQQ[k];

    posQQ = WebRtcIsacfix_kOfLevelsShape[0] + index_ovr_sQQ[k];
    sumQQ = WebRtcIsacfix_kCodeLenShapeQ11[posQQ]; //Q11
    BitsQQ += sumQQ;
  }



  *model = 0;
  *sizeQ11=BitsQQ;

  /* entropy coding of model number */
  status = WebRtcIsacfix_EncHistMulti(streamdata, model, WebRtcIsacfix_kModelCdfPtr, 1);
  if (status < 0) {
    return status;
  }

  /* entropy coding of quantization indices - shape only */
  status = WebRtcIsacfix_EncHistMulti(streamdata, index_sQQ, WebRtcIsacfix_kCdfShapePtr[0], KLT_ORDER_SHAPE);
  if (status < 0) {
    return status;
  }

  /* Save data for creation of multiple bitstreams */
  if (encData != NULL) {
    for (k=0; k<KLT_ORDER_SHAPE; k++)
    {
      encData->LPCindex_s[KLT_ORDER_SHAPE*encData->startIdx + k] = index_sQQ[k];
    }
  }
  /* save the state of the bitstream object 'streamdata' for the possible bit-rate reduction */
  transcodingParam->full         = streamdata->full;
  transcodingParam->stream_index = streamdata->stream_index;
  transcodingParam->streamval    = streamdata->streamval;
  transcodingParam->W_upper      = streamdata->W_upper;
  transcodingParam->beforeLastWord     = streamdata->stream[streamdata->stream_index-1];
  transcodingParam->lastWord     = streamdata->stream[streamdata->stream_index];

  /* entropy coding of index */
  status = WebRtcIsacfix_EncHistMulti(streamdata, index_gQQ, WebRtcIsacfix_kCdfGainPtr[0], KLT_ORDER_GAIN);
  if (status < 0) {
    return status;
  }

  /* find quantization levels for shape coefficients */
  for (k=0; k<KLT_ORDER_SHAPE; k++) {
    tmpcoeffs_sQ17[WebRtcIsacfix_kSelIndShape[k]] = WEBRTC_SPL_MUL(128, WebRtcIsacfix_kLevelsShapeQ10[WebRtcIsacfix_kOfLevelsShape[0]+index_ovr_sQQ[k]]);

  }
  /* inverse KLT  */

  /* left transform */  // Transpose matrix!
  WebRtcIsacfix_MatrixProduct1(WebRtcIsacfix_kT1ShapeQ15[0], tmpcoeffs_sQ17,
      tmpcoeffs2_sQ17, kTIndexFactor4, kTIndexFactor4, kTInitCase0,
      kTIndexStep1, kTIndexStep1, kTLoopCount3, kTLoopCount3, kTMatrix1_shift1);

  /* right transform */ // Transpose matrix
  WebRtcIsacfix_MatrixProduct1(WebRtcIsacfix_kT2ShapeQ15[0], tmpcoeffs2_sQ17,
      tmpcoeffs_sQ17, kTIndexFactor1, kTIndexFactor1, kTInitCase1, kTIndexStep3,
      kTIndexStep2, kTLoopCount1, kTLoopCount3, kTMatrix1_shift1);

  /* scaling, mean addition, and gain restoration */
  poss = 0;pos=0;
  for (k=0; k<SUBFRAMES; k++) {

    /* lo band LAR coeffs */
    for (n=0; n<ORDERLO; n++, pos++, poss++) {
      tmp32 = WEBRTC_SPL_MUL_16_32_RSFT16(31208, tmpcoeffs_sQ17[poss]); // (Q16*Q17)>>16 = Q17, with 1/2.1 = 0.47619047619 ~= 31208 in Q16
      tmp32 = tmp32 + WebRtcIsacfix_kMeansShapeQ17[0][poss]; // Q17+Q17 = Q17
      LPCCoefQ17[pos] = tmp32;
    }

    /* hi band LAR coeffs */
    for (n=0; n<ORDERHI; n++, pos++, poss++) {
      // ((Q13*Q17)>>16)<<3 = Q17, with 1/0.45 = 2.222222222222 ~= 18204 in Q13
      tmp32 = WEBRTC_SPL_MUL_16_32_RSFT16(18204, tmpcoeffs_sQ17[poss]) << 3;
      tmp32 = tmp32 + WebRtcIsacfix_kMeansShapeQ17[0][poss]; // Q17+Q17 = Q17
      LPCCoefQ17[pos] = tmp32;
    }

  }

  //to update tmpcoeffs_gQ17 to the proper state
  for (k=0; k<KLT_ORDER_GAIN; k++) {
    tmpcoeffs_gQ17[WebRtcIsacfix_kSelIndGain[k]] = WebRtcIsacfix_kLevelsGainQ17[WebRtcIsacfix_kOfLevelsGain[0]+index_ovr_gQQ[k]];
  }



  /* find quantization levels for coefficients */

  /* left transform */
  offsg = 0;
  posg = 0;
  for (j=0; j<SUBFRAMES; j++) {
    // (Q15 * Q17) >> (16 - 1) = Q17; Q17 << 4 = Q21.
    sumQQ = (WEBRTC_SPL_MUL_16_32_RSFT16(WebRtcIsacfix_kT1GainQ15[0][0],
                                         tmpcoeffs_gQ17[offsg]) << 1);
    sumQQ += (WEBRTC_SPL_MUL_16_32_RSFT16(WebRtcIsacfix_kT1GainQ15[0][1],
                                          tmpcoeffs_gQ17[offsg + 1]) << 1);
    tmpcoeffs2_gQ21[posg] = sumQQ << 4;
    posg++;

    sumQQ = (WEBRTC_SPL_MUL_16_32_RSFT16(WebRtcIsacfix_kT1GainQ15[0][2],
                                         tmpcoeffs_gQ17[offsg]) << 1);
    sumQQ += (WEBRTC_SPL_MUL_16_32_RSFT16(WebRtcIsacfix_kT1GainQ15[0][3],
                                          tmpcoeffs_gQ17[offsg + 1]) << 1);
    tmpcoeffs2_gQ21[posg] = sumQQ << 4;
    posg++;
    offsg += 2;
  }

  /* right transform */ // Transpose matrix
  WebRtcIsacfix_MatrixProduct2(WebRtcIsacfix_kT2GainQ15[0], tmpcoeffs2_gQ21,
                               tmpcoeffs_gQ17, kTIndexFactor1, kTIndexStep2);

  /* scaling, mean addition, and gain restoration */
  posg = 0;
  gainpos = 0;
  for (k=0; k<2*SUBFRAMES; k++) {

    // Divide by 4 and get Q17 to Q8, i.e. shift 2+9.
    sumQQ16 = (int16_t)(tmpcoeffs_gQ17[posg] >> 11);
    sumQQ16 += WebRtcIsacfix_kMeansGainQ8[0][posg];
    sumQQ = CalcExpN(sumQQ16); // Q8 in and Q17 out
    gain_lo_hiQ17[gainpos] = sumQQ; //Q17

    gainpos++;
    pos++;posg++;
  }

  return 0;
}

int WebRtcIsacfix_EstCodeLpcGain(int32_t *gain_lo_hiQ17,
                                 Bitstr_enc *streamdata,
                                 IsacSaveEncoderData* encData) {
  int j, k;
  int16_t posQQ, pos2QQ, gainpos;
  int16_t posg;
  int16_t index_gQQ[KLT_ORDER_GAIN];

  int16_t tmpcoeffs_gQ6[KLT_ORDER_GAIN];
  int32_t tmpcoeffs_gQ17[KLT_ORDER_GAIN];
  int32_t tmpcoeffs2_gQ21[KLT_ORDER_GAIN];
  int32_t sumQQ;
  int status = 0;

  /* write LAR coefficients to statistics file */
  /* Save data for creation of multiple bitstreams (and transcoding) */
  if (encData != NULL) {
    for (k=0; k<KLT_ORDER_GAIN; k++) {
      encData->LPCcoeffs_g[KLT_ORDER_GAIN*encData->startIdx + k] = gain_lo_hiQ17[k];
    }
  }

  /* log gains, mean removal and scaling */
  posg = 0; gainpos = 0;

  for (k=0; k<SUBFRAMES; k++) {
    /* log gains */

    /* The input argument X to logN(X) is 2^17 times higher than the
       input floating point argument Y to log(Y), since the X value
       is a Q17 value. This can be compensated for after the call, by
       subraction a value Z for each Q-step. One Q-step means that
       X gets 2 times higher, i.e. Z = logN(2)*256 = 0.693147180559*256 =
       177.445678 should be subtracted (since logN() returns a Q8 value).
       For a X value in Q17, the value 177.445678*17 = 3017 should be
       subtracted */
    tmpcoeffs_gQ6[posg] = CalcLogN(gain_lo_hiQ17[gainpos])-3017; //Q8
    tmpcoeffs_gQ6[posg] -= WebRtcIsacfix_kMeansGainQ8[0][posg]; //Q8, but Q6 after not-needed mult. by 4
    posg++; gainpos++;

    tmpcoeffs_gQ6[posg] = CalcLogN(gain_lo_hiQ17[gainpos])-3017; //Q8
    tmpcoeffs_gQ6[posg] -= WebRtcIsacfix_kMeansGainQ8[0][posg]; //Q8, but Q6 after not-needed mult. by 4
    posg++; gainpos++;
  }


  /* KLT  */

  /* left transform */
  posg = 0;
  for (j=0; j<SUBFRAMES; j++) {
      // Q21 = Q6 * Q15
      sumQQ = tmpcoeffs_gQ6[j * 2] * WebRtcIsacfix_kT1GainQ15[0][0] +
          tmpcoeffs_gQ6[j * 2 + 1] * WebRtcIsacfix_kT1GainQ15[0][2];
      tmpcoeffs2_gQ21[posg] = sumQQ;
      posg++;

      sumQQ = tmpcoeffs_gQ6[j * 2] * WebRtcIsacfix_kT1GainQ15[0][1] +
          tmpcoeffs_gQ6[j * 2 + 1] * WebRtcIsacfix_kT1GainQ15[0][3];
      tmpcoeffs2_gQ21[posg] = sumQQ;
      posg++;
  }

  /* right transform */
  WebRtcIsacfix_MatrixProduct2(WebRtcIsacfix_kT2GainQ15[0], tmpcoeffs2_gQ21,
                               tmpcoeffs_gQ17, kTIndexFactor3, kTIndexStep1);

  /* quantize coefficients */

  for (k=0; k<KLT_ORDER_GAIN; k++) //ATTN: ok?
  {
    posQQ = WebRtcIsacfix_kSelIndGain[k];
    pos2QQ= (int16_t)CalcLrIntQ(tmpcoeffs_gQ17[posQQ], 17);

    index_gQQ[k] = pos2QQ + WebRtcIsacfix_kQuantMinGain[k]; //ATTN: ok?
    if (index_gQQ[k] < 0) {
      index_gQQ[k] = 0;
    }
    else if (index_gQQ[k] > WebRtcIsacfix_kMaxIndGain[k]) {
      index_gQQ[k] = WebRtcIsacfix_kMaxIndGain[k];
    }

    /* Save data for creation of multiple bitstreams */
    if (encData != NULL) {
      encData->LPCindex_g[KLT_ORDER_GAIN*encData->startIdx + k] = index_gQQ[k];
    }
  }

  /* entropy coding of index */
  status = WebRtcIsacfix_EncHistMulti(streamdata, index_gQQ, WebRtcIsacfix_kCdfGainPtr[0], KLT_ORDER_GAIN);
  if (status < 0) {
    return status;
  }

  return 0;
}


int WebRtcIsacfix_EncodeLpc(int32_t *gain_lo_hiQ17,
                            int16_t *LPCCoef_loQ15,
                            int16_t *LPCCoef_hiQ15,
                            int16_t *model,
                            int32_t *sizeQ11,
                            Bitstr_enc *streamdata,
                            IsacSaveEncoderData* encData,
                            transcode_obj *transcodeParam)
{
  int status = 0;
  int32_t larsQ17[KLT_ORDER_SHAPE]; // KLT_ORDER_SHAPE == (ORDERLO+ORDERHI)*SUBFRAMES
  // = (6+12)*6 == 108

  Poly2LarFix(LPCCoef_loQ15, ORDERLO, LPCCoef_hiQ15, ORDERHI, SUBFRAMES, larsQ17);

  status = EstCodeLpcCoef(larsQ17, gain_lo_hiQ17, model, sizeQ11,
                          streamdata, encData, transcodeParam);
  if (status < 0) {
    return (status);
  }

  Lar2polyFix(larsQ17, LPCCoef_loQ15, ORDERLO, LPCCoef_hiQ15, ORDERHI, SUBFRAMES);

  return 0;
}


/* decode & dequantize RC */
int WebRtcIsacfix_DecodeRcCoef(Bitstr_dec *streamdata, int16_t *RCQ15)
{
  int k, err;
  int16_t index[AR_ORDER];

  /* entropy decoding of quantization indices */
  err = WebRtcIsacfix_DecHistOneStepMulti(index, streamdata, WebRtcIsacfix_kRcCdfPtr, WebRtcIsacfix_kRcInitInd, AR_ORDER);
  if (err<0)  // error check
    return err;

  /* find quantization levels for reflection coefficients */
  for (k=0; k<AR_ORDER; k++)
  {
    RCQ15[k] = *(WebRtcIsacfix_kRcLevPtr[k] + index[k]);
  }

  return 0;
}



/* quantize & code RC */
int WebRtcIsacfix_EncodeRcCoef(int16_t *RCQ15, Bitstr_enc *streamdata)
{
  int k;
  int16_t index[AR_ORDER];
  int status;

  /* quantize reflection coefficients (add noise feedback?) */
  for (k=0; k<AR_ORDER; k++)
  {
    index[k] = WebRtcIsacfix_kRcInitInd[k];

    if (RCQ15[k] > WebRtcIsacfix_kRcBound[index[k]])
    {
      while (RCQ15[k] > WebRtcIsacfix_kRcBound[index[k] + 1])
        index[k]++;
    }
    else
    {
      while (RCQ15[k] < WebRtcIsacfix_kRcBound[--index[k]]) ;
    }

    RCQ15[k] = *(WebRtcIsacfix_kRcLevPtr[k] + index[k]);
  }


  /* entropy coding of quantization indices */
  status = WebRtcIsacfix_EncHistMulti(streamdata, index, WebRtcIsacfix_kRcCdfPtr, AR_ORDER);

  /* If error in WebRtcIsacfix_EncHistMulti(), status will be negative, otherwise 0 */
  return status;
}


/* decode & dequantize squared Gain */
int WebRtcIsacfix_DecodeGain2(Bitstr_dec *streamdata, int32_t *gainQ10)
{
  int err;
  int16_t index;

  /* entropy decoding of quantization index */
  err = WebRtcIsacfix_DecHistOneStepMulti(
      &index,
      streamdata,
      WebRtcIsacfix_kGainPtr,
      WebRtcIsacfix_kGainInitInd,
      1);
  /* error check */
  if (err<0) {
    return err;
  }

  /* find quantization level */
  *gainQ10 = WebRtcIsacfix_kGain2Lev[index];

  return 0;
}



/* quantize & code squared Gain */
int WebRtcIsacfix_EncodeGain2(int32_t *gainQ10, Bitstr_enc *streamdata)
{
  int16_t index;
  int status = 0;

  /* find quantization index */
  index = WebRtcIsacfix_kGainInitInd[0];
  if (*gainQ10 > WebRtcIsacfix_kGain2Bound[index])
  {
    while (*gainQ10 > WebRtcIsacfix_kGain2Bound[index + 1])
      index++;
  }
  else
  {
    while (*gainQ10 < WebRtcIsacfix_kGain2Bound[--index]) ;
  }

  /* dequantize */
  *gainQ10 = WebRtcIsacfix_kGain2Lev[index];

  /* entropy coding of quantization index */
  status = WebRtcIsacfix_EncHistMulti(streamdata, &index, WebRtcIsacfix_kGainPtr, 1);

  /* If error in WebRtcIsacfix_EncHistMulti(), status will be negative, otherwise 0 */
  return status;
}


/* code and decode Pitch Gains and Lags functions */

/* decode & dequantize Pitch Gains */
int WebRtcIsacfix_DecodePitchGain(Bitstr_dec *streamdata, int16_t *PitchGains_Q12)
{
  int err;
  int16_t index_comb;
  const uint16_t *pitch_gain_cdf_ptr[1];

  /* entropy decoding of quantization indices */
  *pitch_gain_cdf_ptr = WebRtcIsacfix_kPitchGainCdf;
  err = WebRtcIsacfix_DecHistBisectMulti(&index_comb, streamdata, pitch_gain_cdf_ptr, WebRtcIsacfix_kCdfTableSizeGain, 1);
  /* error check, Q_mean_Gain.. tables are of size 144 */
  if ((err < 0) || (index_comb < 0) || (index_comb >= 144))
    return -ISAC_RANGE_ERROR_DECODE_PITCH_GAIN;

  /* unquantize back to pitch gains by table look-up */
  PitchGains_Q12[0] = WebRtcIsacfix_kPitchGain1[index_comb];
  PitchGains_Q12[1] = WebRtcIsacfix_kPitchGain2[index_comb];
  PitchGains_Q12[2] = WebRtcIsacfix_kPitchGain3[index_comb];
  PitchGains_Q12[3] = WebRtcIsacfix_kPitchGain4[index_comb];

  return 0;
}


/* quantize & code Pitch Gains */
int WebRtcIsacfix_EncodePitchGain(int16_t* PitchGains_Q12,
                                  Bitstr_enc* streamdata,
                                  IsacSaveEncoderData* encData) {
  int k,j;
  int16_t SQ15[PITCH_SUBFRAMES];
  int16_t index[3];
  int16_t index_comb;
  const uint16_t *pitch_gain_cdf_ptr[1];
  int32_t CQ17;
  int status = 0;


  /* get the approximate arcsine (almost linear)*/
  for (k=0; k<PITCH_SUBFRAMES; k++)
    SQ15[k] = (int16_t)(PitchGains_Q12[k] * 33 >> 2);  // Q15


  /* find quantization index; only for the first three transform coefficients */
  for (k=0; k<3; k++)
  {
    /*  transform */
    CQ17=0;
    for (j=0; j<PITCH_SUBFRAMES; j++) {
      CQ17 += WebRtcIsacfix_kTransform[k][j] * SQ15[j] >> 10;  // Q17
    }

    index[k] = (int16_t)((CQ17 + 8192)>>14); // Rounding and scaling with stepsize (=1/0.125=8)

    /* check that the index is not outside the boundaries of the table */
    if (index[k] < WebRtcIsacfix_kLowerlimiGain[k]) index[k] = WebRtcIsacfix_kLowerlimiGain[k];
    else if (index[k] > WebRtcIsacfix_kUpperlimitGain[k]) index[k] = WebRtcIsacfix_kUpperlimitGain[k];
    index[k] -= WebRtcIsacfix_kLowerlimiGain[k];
  }

  /* calculate unique overall index */
  index_comb = (int16_t)(WEBRTC_SPL_MUL(WebRtcIsacfix_kMultsGain[0], index[0]) +
                               WEBRTC_SPL_MUL(WebRtcIsacfix_kMultsGain[1], index[1]) + index[2]);

  /* unquantize back to pitch gains by table look-up */
  // (Y)
  PitchGains_Q12[0] = WebRtcIsacfix_kPitchGain1[index_comb];
  PitchGains_Q12[1] = WebRtcIsacfix_kPitchGain2[index_comb];
  PitchGains_Q12[2] = WebRtcIsacfix_kPitchGain3[index_comb];
  PitchGains_Q12[3] = WebRtcIsacfix_kPitchGain4[index_comb];


  /* entropy coding of quantization pitch gains */
  *pitch_gain_cdf_ptr = WebRtcIsacfix_kPitchGainCdf;
  status = WebRtcIsacfix_EncHistMulti(streamdata, &index_comb, pitch_gain_cdf_ptr, 1);
  if (status < 0) {
    return status;
  }

  /* Save data for creation of multiple bitstreams */
  if (encData != NULL) {
    encData->pitchGain_index[encData->startIdx] = index_comb;
  }

  return 0;
}



/* Pitch LAG */


/* decode & dequantize Pitch Lags */
int WebRtcIsacfix_DecodePitchLag(Bitstr_dec *streamdata,
                                 int16_t *PitchGain_Q12,
                                 int16_t *PitchLags_Q7)
{
  int k, err;
  int16_t index[PITCH_SUBFRAMES];
  const int16_t *mean_val2Q10, *mean_val4Q10;

  const int16_t *lower_limit;
  const uint16_t *init_index;
  const uint16_t *cdf_size;
  const uint16_t **cdf;

  int32_t meangainQ12;
  int32_t CQ11, CQ10,tmp32a,tmp32b;
  int16_t shft;

  meangainQ12=0;
  for (k = 0; k < 4; k++)
    meangainQ12 += PitchGain_Q12[k];

  meangainQ12 >>= 2;  // Get average.

  /* voicing classificiation */
  if (meangainQ12 <= 819) {                 // mean_gain < 0.2
    shft = -1;        // StepSize=2.0;
    cdf = WebRtcIsacfix_kPitchLagPtrLo;
    cdf_size = WebRtcIsacfix_kPitchLagSizeLo;
    mean_val2Q10 = WebRtcIsacfix_kMeanLag2Lo;
    mean_val4Q10 = WebRtcIsacfix_kMeanLag4Lo;
    lower_limit = WebRtcIsacfix_kLowerLimitLo;
    init_index = WebRtcIsacfix_kInitIndLo;
  } else if (meangainQ12 <= 1638) {            // mean_gain < 0.4
    shft = 0;        // StepSize=1.0;
    cdf = WebRtcIsacfix_kPitchLagPtrMid;
    cdf_size = WebRtcIsacfix_kPitchLagSizeMid;
    mean_val2Q10 = WebRtcIsacfix_kMeanLag2Mid;
    mean_val4Q10 = WebRtcIsacfix_kMeanLag4Mid;
    lower_limit = WebRtcIsacfix_kLowerLimitMid;
    init_index = WebRtcIsacfix_kInitIndMid;
  } else {
    shft = 1;        // StepSize=0.5;
    cdf = WebRtcIsacfix_kPitchLagPtrHi;
    cdf_size = WebRtcIsacfix_kPitchLagSizeHi;
    mean_val2Q10 = WebRtcIsacfix_kMeanLag2Hi;
    mean_val4Q10 = WebRtcIsacfix_kMeanLag4Hi;
    lower_limit = WebRtcIsacfix_kLowerLimitHi;
    init_index = WebRtcIsacfix_kInitIndHi;
  }

  /* entropy decoding of quantization indices */
  err = WebRtcIsacfix_DecHistBisectMulti(index, streamdata, cdf, cdf_size, 1);
  if ((err<0) || (index[0]<0))  // error check
    return -ISAC_RANGE_ERROR_DECODE_PITCH_LAG;

  err = WebRtcIsacfix_DecHistOneStepMulti(index+1, streamdata, cdf+1, init_index, 3);
  if (err<0)  // error check
    return -ISAC_RANGE_ERROR_DECODE_PITCH_LAG;


  /* unquantize back to transform coefficients and do the inverse transform: S = T'*C */
  CQ11 = ((int32_t)index[0] + lower_limit[0]);  // Q0
  CQ11 = WEBRTC_SPL_SHIFT_W32(CQ11,11-shft); // Scale with StepSize, Q11
  for (k=0; k<PITCH_SUBFRAMES; k++) {
    tmp32a =  WEBRTC_SPL_MUL_16_32_RSFT11(WebRtcIsacfix_kTransform[0][k], CQ11);
    PitchLags_Q7[k] = (int16_t)(tmp32a >> 5);
  }

  CQ10 = mean_val2Q10[index[1]];
  for (k=0; k<PITCH_SUBFRAMES; k++) {
    tmp32b = WebRtcIsacfix_kTransform[1][k] * (int16_t)CQ10 >> 10;
    PitchLags_Q7[k] += (int16_t)(tmp32b >> 5);
  }

  CQ10 = mean_val4Q10[index[3]];
  for (k=0; k<PITCH_SUBFRAMES; k++) {
    tmp32b = WebRtcIsacfix_kTransform[3][k] * (int16_t)CQ10 >> 10;
    PitchLags_Q7[k] += (int16_t)(tmp32b >> 5);
  }

  return 0;
}



/* quantize & code Pitch Lags */
int WebRtcIsacfix_EncodePitchLag(int16_t* PitchLagsQ7,
                                 int16_t* PitchGain_Q12,
                                 Bitstr_enc* streamdata,
                                 IsacSaveEncoderData* encData) {
  int k, j;
  int16_t index[PITCH_SUBFRAMES];
  int32_t meangainQ12, CQ17;
  int32_t CQ11, CQ10,tmp32a;

  const int16_t *mean_val2Q10,*mean_val4Q10;
  const int16_t *lower_limit, *upper_limit;
  const uint16_t **cdf;
  int16_t shft, tmp16b;
  int32_t tmp32b;
  int status = 0;

  /* compute mean pitch gain */
  meangainQ12=0;
  for (k = 0; k < 4; k++)
    meangainQ12 += PitchGain_Q12[k];

  meangainQ12 >>= 2;

  /* Save data for creation of multiple bitstreams */
  if (encData != NULL) {
    encData->meanGain[encData->startIdx] = meangainQ12;
  }

  /* voicing classificiation */
  if (meangainQ12 <= 819) {                 // mean_gain < 0.2
    shft = -1;        // StepSize=2.0;
    cdf = WebRtcIsacfix_kPitchLagPtrLo;
    mean_val2Q10 = WebRtcIsacfix_kMeanLag2Lo;
    mean_val4Q10 = WebRtcIsacfix_kMeanLag4Lo;
    lower_limit = WebRtcIsacfix_kLowerLimitLo;
    upper_limit = WebRtcIsacfix_kUpperLimitLo;
  } else if (meangainQ12 <= 1638) {            // mean_gain < 0.4
    shft = 0;        // StepSize=1.0;
    cdf = WebRtcIsacfix_kPitchLagPtrMid;
    mean_val2Q10 = WebRtcIsacfix_kMeanLag2Mid;
    mean_val4Q10 = WebRtcIsacfix_kMeanLag4Mid;
    lower_limit = WebRtcIsacfix_kLowerLimitMid;
    upper_limit = WebRtcIsacfix_kUpperLimitMid;
  } else {
    shft = 1;        // StepSize=0.5;
    cdf = WebRtcIsacfix_kPitchLagPtrHi;
    mean_val2Q10 = WebRtcIsacfix_kMeanLag2Hi;
    mean_val4Q10 = WebRtcIsacfix_kMeanLag4Hi;
    lower_limit = WebRtcIsacfix_kLowerLimitHi;
    upper_limit = WebRtcIsacfix_kUpperLimitHi;
  }

  /* find quantization index */
  for (k=0; k<4; k++)
  {
    /*  transform */
    CQ17=0;
    for (j=0; j<PITCH_SUBFRAMES; j++)
      CQ17 += WebRtcIsacfix_kTransform[k][j] * PitchLagsQ7[j] >> 2;  // Q17

    CQ17 = WEBRTC_SPL_SHIFT_W32(CQ17,shft); // Scale with StepSize

    /* quantize */
    tmp16b = (int16_t)((CQ17 + 65536) >> 17);
    index[k] =  tmp16b;

    /* check that the index is not outside the boundaries of the table */
    if (index[k] < lower_limit[k]) index[k] = lower_limit[k];
    else if (index[k] > upper_limit[k]) index[k] = upper_limit[k];
    index[k] -= lower_limit[k];

    /* Save data for creation of multiple bitstreams */
    if(encData != NULL) {
      encData->pitchIndex[PITCH_SUBFRAMES*encData->startIdx + k] = index[k];
    }
  }

  /* unquantize back to transform coefficients and do the inverse transform: S = T'*C */
  CQ11 = (index[0] + lower_limit[0]);  // Q0
  CQ11 = WEBRTC_SPL_SHIFT_W32(CQ11,11-shft); // Scale with StepSize, Q11

  for (k=0; k<PITCH_SUBFRAMES; k++) {
    tmp32a =  WEBRTC_SPL_MUL_16_32_RSFT11(WebRtcIsacfix_kTransform[0][k], CQ11); // Q12
    PitchLagsQ7[k] = (int16_t)(tmp32a >> 5);  // Q7.
  }

  CQ10 = mean_val2Q10[index[1]];
  for (k=0; k<PITCH_SUBFRAMES; k++) {
    tmp32b = WebRtcIsacfix_kTransform[1][k] * (int16_t)CQ10 >> 10;
    PitchLagsQ7[k] += (int16_t)(tmp32b >> 5);  // Q7.
  }

  CQ10 = mean_val4Q10[index[3]];
  for (k=0; k<PITCH_SUBFRAMES; k++) {
    tmp32b = WebRtcIsacfix_kTransform[3][k] * (int16_t)CQ10 >> 10;
    PitchLagsQ7[k] += (int16_t)(tmp32b >> 5);  // Q7.
  }

  /* entropy coding of quantization pitch lags */
  status = WebRtcIsacfix_EncHistMulti(streamdata, index, cdf, PITCH_SUBFRAMES);

  /* If error in WebRtcIsacfix_EncHistMulti(), status will be negative, otherwise 0 */
  return status;
}



/* Routines for inband signaling of bandwitdh estimation */
/* Histograms based on uniform distribution of indices */
/* Move global variables later! */


/* cdf array for frame length indicator */
const uint16_t kFrameLenCdf[4] = {
  0, 21845, 43690, 65535};

/* pointer to cdf array for frame length indicator */
const uint16_t * const kFrameLenCdfPtr[1] = {kFrameLenCdf};

/* initial cdf index for decoder of frame length indicator */
const uint16_t kFrameLenInitIndex[1] = {1};


int WebRtcIsacfix_DecodeFrameLen(Bitstr_dec *streamdata,
                                 size_t *framesamples)
{

  int err;
  int16_t frame_mode;

  err = 0;
  /* entropy decoding of frame length [1:30ms,2:60ms] */
  err = WebRtcIsacfix_DecHistOneStepMulti(&frame_mode, streamdata, kFrameLenCdfPtr, kFrameLenInitIndex, 1);
  if (err<0)  // error check
    return -ISAC_RANGE_ERROR_DECODE_FRAME_LENGTH;

  switch(frame_mode) {
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


int WebRtcIsacfix_EncodeFrameLen(int16_t framesamples, Bitstr_enc *streamdata) {

  int status;
  int16_t frame_mode;

  status = 0;
  frame_mode = 0;
  /* entropy coding of frame length [1:480 samples,2:960 samples] */
  switch(framesamples) {
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

  status = WebRtcIsacfix_EncHistMulti(streamdata, &frame_mode, kFrameLenCdfPtr, 1);

  return status;
}

/* cdf array for estimated bandwidth */
const uint16_t kBwCdf[25] = {
  0, 2731, 5461, 8192, 10923, 13653, 16384, 19114, 21845, 24576, 27306, 30037,
  32768, 35498, 38229, 40959, 43690, 46421, 49151, 51882, 54613, 57343, 60074,
  62804, 65535};

/* pointer to cdf array for estimated bandwidth */
const uint16_t * const kBwCdfPtr[1] = {kBwCdf};

/* initial cdf index for decoder of estimated bandwidth*/
const uint16_t kBwInitIndex[1] = {7};


int WebRtcIsacfix_DecodeSendBandwidth(Bitstr_dec *streamdata, int16_t *BWno) {

  int err;
  int16_t BWno32;

  /* entropy decoding of sender's BW estimation [0..23] */
  err = WebRtcIsacfix_DecHistOneStepMulti(&BWno32, streamdata, kBwCdfPtr, kBwInitIndex, 1);
  if (err<0)  // error check
    return -ISAC_RANGE_ERROR_DECODE_BANDWIDTH;
  *BWno = (int16_t)BWno32;
  return err;

}


int WebRtcIsacfix_EncodeReceiveBandwidth(int16_t *BWno, Bitstr_enc *streamdata)
{
  int status = 0;
  /* entropy encoding of receiver's BW estimation [0..23] */
  status = WebRtcIsacfix_EncHistMulti(streamdata, BWno, kBwCdfPtr, 1);

  return status;
}

/* estimate codel length of LPC Coef */
void WebRtcIsacfix_TranscodeLpcCoef(int32_t *gain_lo_hiQ17,
                                    int16_t *index_gQQ) {
  int j, k;
  int16_t posQQ, pos2QQ;
  int16_t posg, offsg, gainpos;
  int32_t tmpcoeffs_gQ6[KLT_ORDER_GAIN];
  int32_t tmpcoeffs_gQ17[KLT_ORDER_GAIN];
  int32_t tmpcoeffs2_gQ21[KLT_ORDER_GAIN];
  int32_t sumQQ;


  /* log gains, mean removal and scaling */
  posg = 0; gainpos=0;

  for (k=0; k<SUBFRAMES; k++) {
    /* log gains */

    /* The input argument X to logN(X) is 2^17 times higher than the
       input floating point argument Y to log(Y), since the X value
       is a Q17 value. This can be compensated for after the call, by
       subraction a value Z for each Q-step. One Q-step means that
       X gets 2 times higher, i.e. Z = logN(2)*256 = 0.693147180559*256 =
       177.445678 should be subtracted (since logN() returns a Q8 value).
       For a X value in Q17, the value 177.445678*17 = 3017 should be
       subtracted */
    tmpcoeffs_gQ6[posg] = CalcLogN(gain_lo_hiQ17[gainpos])-3017; //Q8
    tmpcoeffs_gQ6[posg] -= WebRtcIsacfix_kMeansGainQ8[0][posg]; //Q8, but Q6 after not-needed mult. by 4
    posg++; gainpos++;

    tmpcoeffs_gQ6[posg] = CalcLogN(gain_lo_hiQ17[gainpos])-3017; //Q8
    tmpcoeffs_gQ6[posg] -= WebRtcIsacfix_kMeansGainQ8[0][posg]; //Q8, but Q6 after not-needed mult. by 4
    posg++; gainpos++;

  }


  /* KLT  */

  /* left transform */
  for (j = 0, offsg = 0; j < SUBFRAMES; j++, offsg += 2) {
    // Q21 = Q6 * Q15
    sumQQ = tmpcoeffs_gQ6[offsg] * WebRtcIsacfix_kT1GainQ15[0][0] +
        tmpcoeffs_gQ6[offsg + 1] * WebRtcIsacfix_kT1GainQ15[0][2];
    tmpcoeffs2_gQ21[offsg] = sumQQ;

    // Q21 = Q6 * Q15
    sumQQ = tmpcoeffs_gQ6[offsg] * WebRtcIsacfix_kT1GainQ15[0][1] +
        tmpcoeffs_gQ6[offsg + 1] * WebRtcIsacfix_kT1GainQ15[0][3];
    tmpcoeffs2_gQ21[offsg + 1] = sumQQ;
  }

  /* right transform */
  WebRtcIsacfix_MatrixProduct2(WebRtcIsacfix_kT2GainQ15[0], tmpcoeffs2_gQ21,
                               tmpcoeffs_gQ17, kTIndexFactor3, kTIndexStep1);

  /* quantize coefficients */
  for (k=0; k<KLT_ORDER_GAIN; k++) //ATTN: ok?
  {
    posQQ = WebRtcIsacfix_kSelIndGain[k];
    pos2QQ= (int16_t)CalcLrIntQ(tmpcoeffs_gQ17[posQQ], 17);

    index_gQQ[k] = pos2QQ + WebRtcIsacfix_kQuantMinGain[k]; //ATTN: ok?
    if (index_gQQ[k] < 0) {
      index_gQQ[k] = 0;
    }
    else if (index_gQQ[k] > WebRtcIsacfix_kMaxIndGain[k]) {
      index_gQQ[k] = WebRtcIsacfix_kMaxIndGain[k];
    }
  }
}
