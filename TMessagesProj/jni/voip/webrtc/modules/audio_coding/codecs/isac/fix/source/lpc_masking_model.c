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
 * lpc_masking_model.c
 *
 * LPC analysis and filtering functions
 *
 */

#include "lpc_masking_model.h"

#include <limits.h>  /* For LLONG_MAX and LLONG_MIN. */

#include "modules/audio_coding/codecs/isac/fix/source/codec.h"
#include "modules/audio_coding/codecs/isac/fix/source/entropy_coding.h"
#include "modules/audio_coding/codecs/isac/fix/source/settings.h"

/* The conversion is implemented by the step-down algorithm */
void WebRtcSpl_AToK_JSK(
    int16_t *a16, /* Q11 */
    int16_t useOrder,
    int16_t *k16  /* Q15 */
                        )
{
  int m, k;
  int32_t tmp32[MAX_AR_MODEL_ORDER];
  int32_t tmp32b;
  int32_t tmp_inv_denum32;
  int16_t tmp_inv_denum16;

  k16[useOrder-1] = a16[useOrder] << 4;  // Q11<<4 => Q15

  for (m=useOrder-1; m>0; m--) {
    // (1 - k^2) in Q30
    tmp_inv_denum32 = 1073741823 - k16[m] * k16[m];
    tmp_inv_denum16 = (int16_t)(tmp_inv_denum32 >> 15);  // (1 - k^2) in Q15.

    for (k=1; k<=m; k++) {
      tmp32b = (a16[k] << 16) - ((k16[m] * a16[m - k + 1]) << 1);

      tmp32[k] = WebRtcSpl_DivW32W16(tmp32b, tmp_inv_denum16); //Q27/Q15 = Q12
    }

    for (k=1; k<m; k++) {
      a16[k] = (int16_t)(tmp32[k] >> 1);  // Q12>>1 => Q11
    }

    tmp32[m] = WEBRTC_SPL_SAT(4092, tmp32[m], -4092);
    k16[m - 1] = (int16_t)(tmp32[m] << 3);  // Q12<<3 => Q15
  }

  return;
}





int16_t WebRtcSpl_LevinsonW32_JSK(
    int32_t *R,  /* (i) Autocorrelation of length >= order+1 */
    int16_t *A,  /* (o) A[0..order] LPC coefficients (Q11) */
    int16_t *K,  /* (o) K[0...order-1] Reflection coefficients (Q15) */
    int16_t order /* (i) filter order */
                                        ) {
  int16_t i, j;
  int16_t R_hi[LEVINSON_MAX_ORDER+1], R_low[LEVINSON_MAX_ORDER+1];
  /* Aurocorr coefficients in high precision */
  int16_t A_hi[LEVINSON_MAX_ORDER+1], A_low[LEVINSON_MAX_ORDER+1];
  /* LPC coefficients in high precicion */
  int16_t A_upd_hi[LEVINSON_MAX_ORDER+1], A_upd_low[LEVINSON_MAX_ORDER+1];
  /* LPC coefficients for next iteration */
  int16_t K_hi, K_low;      /* reflection coefficient in high precision */
  int16_t Alpha_hi, Alpha_low, Alpha_exp; /* Prediction gain Alpha in high precision
                                                   and with scale factor */
  int16_t tmp_hi, tmp_low;
  int32_t temp1W32, temp2W32, temp3W32;
  int16_t norm;

  /* Normalize the autocorrelation R[0]...R[order+1] */

  norm = WebRtcSpl_NormW32(R[0]);

  for (i=order;i>=0;i--) {
    temp1W32 = R[i] << norm;
    /* Put R in hi and low format */
    R_hi[i] = (int16_t)(temp1W32 >> 16);
    R_low[i] = (int16_t)((temp1W32 - ((int32_t)R_hi[i] << 16)) >> 1);
  }

  /* K = A[1] = -R[1] / R[0] */

  temp2W32 = (R_hi[1] << 16) + (R_low[1] << 1);  /* R[1] in Q31      */
  temp3W32  = WEBRTC_SPL_ABS_W32(temp2W32);      /* abs R[1]         */
  temp1W32  = WebRtcSpl_DivW32HiLow(temp3W32, R_hi[0], R_low[0]); /* abs(R[1])/R[0] in Q31 */
  /* Put back the sign on R[1] */
  if (temp2W32 > 0) {
    temp1W32 = -temp1W32;
  }

  /* Put K in hi and low format */
  K_hi = (int16_t)(temp1W32 >> 16);
  K_low = (int16_t)((temp1W32 - ((int32_t)K_hi << 16)) >> 1);

  /* Store first reflection coefficient */
  K[0] = K_hi;

  temp1W32 >>= 4;  /* A[1] in Q27. */

  /* Put A[1] in hi and low format */
  A_hi[1] = (int16_t)(temp1W32 >> 16);
  A_low[1] = (int16_t)((temp1W32 - ((int32_t)A_hi[1] << 16)) >> 1);

  /*  Alpha = R[0] * (1-K^2) */

  temp1W32  = (((K_hi * K_low) >> 14) + K_hi * K_hi) << 1;  /* = k^2 in Q31 */

  temp1W32 = WEBRTC_SPL_ABS_W32(temp1W32);    /* Guard against <0 */
  temp1W32 = (int32_t)0x7fffffffL - temp1W32;    /* temp1W32 = (1 - K[0]*K[0]) in Q31 */

  /* Store temp1W32 = 1 - K[0]*K[0] on hi and low format */
  tmp_hi = (int16_t)(temp1W32 >> 16);
  tmp_low = (int16_t)((temp1W32 - ((int32_t)tmp_hi << 16)) >> 1);

  /* Calculate Alpha in Q31 */
  temp1W32 = (R_hi[0] * tmp_hi + ((R_hi[0] * tmp_low) >> 15) +
      ((R_low[0] * tmp_hi) >> 15)) << 1;

  /* Normalize Alpha and put it in hi and low format */

  Alpha_exp = WebRtcSpl_NormW32(temp1W32);
  temp1W32 <<= Alpha_exp;
  Alpha_hi = (int16_t)(temp1W32 >> 16);
  Alpha_low = (int16_t)((temp1W32 - ((int32_t)Alpha_hi<< 16)) >> 1);

  /* Perform the iterative calculations in the
     Levinson Durbin algorithm */

  for (i=2; i<=order; i++)
  {

    /*                    ----
                          \
        temp1W32 =  R[i] + > R[j]*A[i-j]
                          /
                          ----
                          j=1..i-1
    */

    temp1W32 = 0;

    for(j=1; j<i; j++) {
      /* temp1W32 is in Q31 */
      temp1W32 += ((R_hi[j] * A_hi[i - j]) << 1) +
          ((((R_hi[j] * A_low[i - j]) >> 15) +
              ((R_low[j] * A_hi[i - j]) >> 15)) << 1);
    }

    temp1W32 <<= 4;
    temp1W32 += (R_hi[i] << 16) + (R_low[i] << 1);

    /* K = -temp1W32 / Alpha */
    temp2W32 = WEBRTC_SPL_ABS_W32(temp1W32);      /* abs(temp1W32) */
    temp3W32 = WebRtcSpl_DivW32HiLow(temp2W32, Alpha_hi, Alpha_low); /* abs(temp1W32)/Alpha */

    /* Put the sign of temp1W32 back again */
    if (temp1W32 > 0) {
      temp3W32 = -temp3W32;
    }

    /* Use the Alpha shifts from earlier to denormalize */
    norm = WebRtcSpl_NormW32(temp3W32);
    if ((Alpha_exp <= norm)||(temp3W32==0)) {
      temp3W32 <<= Alpha_exp;
    } else {
      if (temp3W32 > 0)
      {
        temp3W32 = (int32_t)0x7fffffffL;
      } else
      {
        temp3W32 = (int32_t)0x80000000L;
      }
    }

    /* Put K on hi and low format */
    K_hi = (int16_t)(temp3W32 >> 16);
    K_low = (int16_t)((temp3W32 - ((int32_t)K_hi << 16)) >> 1);

    /* Store Reflection coefficient in Q15 */
    K[i-1] = K_hi;

    /* Test for unstable filter. If unstable return 0 and let the
       user decide what to do in that case
    */

    if ((int32_t)WEBRTC_SPL_ABS_W16(K_hi) > (int32_t)32740) {
      return(-i); /* Unstable filter */
    }

    /*
      Compute updated LPC coefficient: Anew[i]
      Anew[j]= A[j] + K*A[i-j]   for j=1..i-1
      Anew[i]= K
    */

    for(j=1; j<i; j++)
    {
      temp1W32 = (A_hi[j] << 16) + (A_low[j] << 1);  // temp1W32 = A[j] in Q27

      temp1W32 += (K_hi * A_hi[i - j] + ((K_hi * A_low[i - j]) >> 15) +
          ((K_low * A_hi[i - j]) >> 15)) << 1;  // temp1W32 += K*A[i-j] in Q27.

      /* Put Anew in hi and low format */
      A_upd_hi[j] = (int16_t)(temp1W32 >> 16);
      A_upd_low[j] = (int16_t)((temp1W32 - ((int32_t)A_upd_hi[j] << 16)) >> 1);
    }

    temp3W32 >>= 4;  /* temp3W32 = K in Q27 (Convert from Q31 to Q27) */

    /* Store Anew in hi and low format */
    A_upd_hi[i] = (int16_t)(temp3W32 >> 16);
    A_upd_low[i] = (int16_t)((temp3W32 - ((int32_t)A_upd_hi[i] << 16)) >> 1);

    /*  Alpha = Alpha * (1-K^2) */

    temp1W32 = (((K_hi * K_low) >> 14) + K_hi * K_hi) << 1;  /* K*K in Q31 */

    temp1W32 = WEBRTC_SPL_ABS_W32(temp1W32);      /* Guard against <0 */
    temp1W32 = (int32_t)0x7fffffffL - temp1W32;      /* 1 - K*K  in Q31 */

    /* Convert 1- K^2 in hi and low format */
    tmp_hi = (int16_t)(temp1W32 >> 16);
    tmp_low = (int16_t)((temp1W32 - ((int32_t)tmp_hi << 16)) >> 1);

    /* Calculate Alpha = Alpha * (1-K^2) in Q31 */
    temp1W32 = (Alpha_hi * tmp_hi + ((Alpha_hi * tmp_low) >> 15) +
        ((Alpha_low * tmp_hi) >> 15)) << 1;

    /* Normalize Alpha and store it on hi and low format */

    norm = WebRtcSpl_NormW32(temp1W32);
    temp1W32 <<= norm;

    Alpha_hi = (int16_t)(temp1W32 >> 16);
    Alpha_low = (int16_t)((temp1W32 - ((int32_t)Alpha_hi << 16)) >> 1);

    /* Update the total nomalization of Alpha */
    Alpha_exp = Alpha_exp + norm;

    /* Update A[] */

    for(j=1; j<=i; j++)
    {
      A_hi[j] =A_upd_hi[j];
      A_low[j] =A_upd_low[j];
    }
  }

  /*
    Set A[0] to 1.0 and store the A[i] i=1...order in Q12
    (Convert from Q27 and use rounding)
  */

  A[0] = 2048;

  for(i=1; i<=order; i++) {
    /* temp1W32 in Q27 */
    temp1W32 = (A_hi[i] << 16) + (A_low[i] << 1);
    /* Round and store upper word */
    A[i] = (int16_t)((temp1W32 + 32768) >> 16);
  }
  return(1); /* Stable filters */
}





/* window */
/* Matlab generation of floating point code:
 *  t = (1:256)/257; r = 1-(1-t).^.45; w = sin(r*pi).^3; w = w/sum(w); plot((1:256)/8, w); grid;
 *  for k=1:16, fprintf(1, '%.8f, ', w(k*16 + (-15:0))); fprintf(1, '\n'); end
 * All values are multiplyed with 2^21 in fixed point code.
 */
static const int16_t kWindowAutocorr[WINLEN] = {
  0,     0,     0,     0,     0,     1,     1,     2,     2,     3,     5,     6,
  8,    10,    12,    14,    17,    20,    24,    28,    33,    38,    43,    49,
  56,    63,    71,    79,    88,    98,   108,   119,   131,   143,   157,   171,
  186,   202,   219,   237,   256,   275,   296,   318,   341,   365,   390,   416,
  444,   472,   502,   533,   566,   600,   635,   671,   709,   748,   789,   831,
  875,   920,   967,  1015,  1065,  1116,  1170,  1224,  1281,  1339,  1399,  1461,
  1525,  1590,  1657,  1726,  1797,  1870,  1945,  2021,  2100,  2181,  2263,  2348,
  2434,  2523,  2614,  2706,  2801,  2898,  2997,  3099,  3202,  3307,  3415,  3525,
  3637,  3751,  3867,  3986,  4106,  4229,  4354,  4481,  4611,  4742,  4876,  5012,
  5150,  5291,  5433,  5578,  5725,  5874,  6025,  6178,  6333,  6490,  6650,  6811,
  6974,  7140,  7307,  7476,  7647,  7820,  7995,  8171,  8349,  8529,  8711,  8894,
  9079,  9265,  9453,  9642,  9833, 10024, 10217, 10412, 10607, 10803, 11000, 11199,
  11398, 11597, 11797, 11998, 12200, 12401, 12603, 12805, 13008, 13210, 13412, 13614,
  13815, 14016, 14216, 14416, 14615, 14813, 15009, 15205, 15399, 15591, 15782, 15971,
  16157, 16342, 16524, 16704, 16881, 17056, 17227, 17395, 17559, 17720, 17877, 18030,
  18179, 18323, 18462, 18597, 18727, 18851, 18970, 19082, 19189, 19290, 19384, 19471,
  19551, 19623, 19689, 19746, 19795, 19835, 19867, 19890, 19904, 19908, 19902, 19886,
  19860, 19823, 19775, 19715, 19644, 19561, 19465, 19357, 19237, 19102, 18955, 18793,
  18618, 18428, 18223, 18004, 17769, 17518, 17252, 16970, 16672, 16357, 16025, 15677,
  15311, 14929, 14529, 14111, 13677, 13225, 12755, 12268, 11764, 11243, 10706, 10152,
  9583,  8998,  8399,  7787,  7162,  6527,  5883,  5231,  4576,  3919,  3265,  2620,
  1990,  1386,   825,   333
};


/* By using a hearing threshold level in dB of -28 dB (higher value gives more noise),
   the H_T_H (in float) can be calculated as:
   H_T_H = pow(10.0, 0.05 * (-28.0)) = 0.039810717055350
   In Q19, H_T_H becomes round(0.039810717055350*2^19) ~= 20872, i.e.
   H_T_H = 20872/524288.0, and H_T_HQ19 = 20872;
*/


/* The bandwidth expansion vectors are created from:
   kPolyVecLo=[0.900000,0.810000,0.729000,0.656100,0.590490,0.531441,0.478297,0.430467,0.387420,0.348678,0.313811,0.282430];
   kPolyVecHi=[0.800000,0.640000,0.512000,0.409600,0.327680,0.262144];
   round(kPolyVecLo*32768)
   round(kPolyVecHi*32768)
*/
static const int16_t kPolyVecLo[12] = {
  29491, 26542, 23888, 21499, 19349, 17414, 15673, 14106, 12695, 11425, 10283, 9255
};
static const int16_t kPolyVecHi[6] = {
  26214, 20972, 16777, 13422, 10737, 8590
};

static __inline int32_t log2_Q8_LPC( uint32_t x ) {

  int32_t zeros;
  int16_t frac;

  zeros=WebRtcSpl_NormU32(x);
  frac = (int16_t)(((x << zeros) & 0x7FFFFFFF) >> 23);

  /* log2(x) */
  return ((31 - zeros) << 8) + frac;
}

static const int16_t kMulPitchGain = -25; /* 200/256 in Q5 */
static const int16_t kChngFactor = 3523; /* log10(2)*10/4*0.4/1.4=log10(2)/1.4= 0.2150 in Q14 */
static const int16_t kExp2 = 11819; /* 1/log(2) */
const int kShiftLowerBand = 11;  /* Shift value for lower band in Q domain. */
const int kShiftHigherBand = 12;  /* Shift value for higher band in Q domain. */

void WebRtcIsacfix_GetVars(const int16_t *input, const int16_t *pitchGains_Q12,
                           uint32_t *oldEnergy, int16_t *varscale)
{
  int k;
  uint32_t nrgQ[4];
  int16_t nrgQlog[4];
  int16_t tmp16, chng1, chng2, chng3, chng4, tmp, chngQ, oldNrgQlog, pgQ, pg3;
  int32_t expPg32;
  int16_t expPg, divVal;
  int16_t tmp16_1, tmp16_2;

  /* Calculate energies of first and second frame halfs */
  nrgQ[0]=0;
  for (k = QLOOKAHEAD/2; k < (FRAMESAMPLES/4 + QLOOKAHEAD) / 2; k++) {
    nrgQ[0] += (uint32_t)(input[k] * input[k]);
  }
  nrgQ[1]=0;
  for ( ; k < (FRAMESAMPLES/2 + QLOOKAHEAD) / 2; k++) {
    nrgQ[1] += (uint32_t)(input[k] * input[k]);
  }
  nrgQ[2]=0;
  for ( ; k < (FRAMESAMPLES * 3 / 4 + QLOOKAHEAD) / 2; k++) {
    nrgQ[2] += (uint32_t)(input[k] * input[k]);
  }
  nrgQ[3]=0;
  for ( ; k < (FRAMESAMPLES + QLOOKAHEAD) / 2; k++) {
    nrgQ[3] += (uint32_t)(input[k] * input[k]);
  }

  for ( k=0; k<4; k++) {
    nrgQlog[k] = (int16_t)log2_Q8_LPC(nrgQ[k]); /* log2(nrgQ) */
  }
  oldNrgQlog = (int16_t)log2_Q8_LPC(*oldEnergy);

  /* Calculate average level change */
  chng1 = WEBRTC_SPL_ABS_W16(nrgQlog[3]-nrgQlog[2]);
  chng2 = WEBRTC_SPL_ABS_W16(nrgQlog[2]-nrgQlog[1]);
  chng3 = WEBRTC_SPL_ABS_W16(nrgQlog[1]-nrgQlog[0]);
  chng4 = WEBRTC_SPL_ABS_W16(nrgQlog[0]-oldNrgQlog);
  tmp = chng1+chng2+chng3+chng4;
  chngQ = (int16_t)(tmp * kChngFactor >> 10);  /* Q12 */
  chngQ += 2926; /* + 1.0/1.4 in Q12 */

  /* Find average pitch gain */
  pgQ = 0;
  for (k=0; k<4; k++)
  {
    pgQ += pitchGains_Q12[k];
  }

  pg3 = (int16_t)(pgQ * pgQ >> 11);  // pgQ in Q(12+2)=Q14. Q14*Q14>>11 => Q17
  pg3 = (int16_t)(pgQ * pg3 >> 13);  /* Q14*Q17>>13 =>Q18  */
  /* kMulPitchGain = -25 = -200 in Q-3. */
  pg3 = (int16_t)(pg3 * kMulPitchGain >> 5);  // Q10
  tmp16=(int16_t)WEBRTC_SPL_MUL_16_16_RSFT_WITH_ROUND(kExp2,pg3,13);/* Q13*Q10>>13 => Q10*/
  if (tmp16<0) {
    tmp16_2 = (0x0400 | (tmp16 & 0x03FF));
    tmp16_1 = ((uint16_t)(tmp16 ^ 0xFFFF) >> 10) - 3;  /* Gives result in Q14 */
    if (tmp16_1<0)
      expPg = -(tmp16_2 << -tmp16_1);
    else
      expPg = -(tmp16_2 >> tmp16_1);
  } else
    expPg = (int16_t) -16384; /* 1 in Q14, since 2^0=1 */

  expPg32 = (int32_t)expPg << 8;  /* Q22 */
  divVal = WebRtcSpl_DivW32W16ResW16(expPg32, chngQ); /* Q22/Q12=Q10 */

  tmp16=(int16_t)WEBRTC_SPL_MUL_16_16_RSFT_WITH_ROUND(kExp2,divVal,13);/* Q13*Q10>>13 => Q10*/
  if (tmp16<0) {
    tmp16_2 = (0x0400 | (tmp16 & 0x03FF));
    tmp16_1 = ((uint16_t)(tmp16 ^ 0xFFFF) >> 10) - 3;  /* Gives result in Q14 */
    if (tmp16_1<0)
      expPg = tmp16_2 << -tmp16_1;
    else
      expPg = tmp16_2 >> tmp16_1;
  } else
    expPg = (int16_t) 16384; /* 1 in Q14, since 2^0=1 */

  *varscale = expPg-1;
  *oldEnergy = nrgQ[3];
}



static __inline int16_t  exp2_Q10_T(int16_t x) { // Both in and out in Q10

  int16_t tmp16_1, tmp16_2;

  tmp16_2=(int16_t)(0x0400|(x&0x03FF));
  tmp16_1 = -(x >> 10);
  if(tmp16_1>0)
    return tmp16_2 >> tmp16_1;
  else
    return tmp16_2 << -tmp16_1;

}


// Declare function pointers.
AutocorrFix WebRtcIsacfix_AutocorrFix;
CalculateResidualEnergy WebRtcIsacfix_CalculateResidualEnergy;

/* This routine calculates the residual energy for LPC.
 * Formula as shown in comments inside.
 */
int32_t WebRtcIsacfix_CalculateResidualEnergyC(int lpc_order,
                                               int32_t q_val_corr,
                                               int q_val_polynomial,
                                               int16_t* a_polynomial,
                                               int32_t* corr_coeffs,
                                               int* q_val_residual_energy) {
  int i = 0, j = 0;
  int shift_internal = 0, shift_norm = 0;
  int32_t tmp32 = 0, word32_high = 0, word32_low = 0, residual_energy = 0;
  int64_t sum64 = 0, sum64_tmp = 0;

  for (i = 0; i <= lpc_order; i++) {
    for (j = i; j <= lpc_order; j++) {
      /* For the case of i == 0: residual_energy +=
       *    a_polynomial[j] * corr_coeffs[i] * a_polynomial[j - i];
       * For the case of i != 0: residual_energy +=
       *    a_polynomial[j] * corr_coeffs[i] * a_polynomial[j - i] * 2;
       */

      tmp32 = a_polynomial[j] * a_polynomial[j - i];
                                   /* tmp32 in Q(q_val_polynomial * 2). */
      if (i != 0) {
        tmp32 <<= 1;
      }
      sum64_tmp = (int64_t)tmp32 * (int64_t)corr_coeffs[i];
      sum64_tmp >>= shift_internal;

      /* Test overflow and sum the result. */
      if(((sum64_tmp > 0 && sum64 > 0) && (LLONG_MAX - sum64 < sum64_tmp)) ||
         ((sum64_tmp < 0 && sum64 < 0) && (LLONG_MIN - sum64 > sum64_tmp))) {
        /* Shift right for overflow. */
        shift_internal += 1;
        sum64 >>= 1;
        sum64 += sum64_tmp >> 1;
      } else {
        sum64 += sum64_tmp;
      }
    }
  }

  word32_high = (int32_t)(sum64 >> 32);
  word32_low = (int32_t)sum64;

  // Calculate the value of shifting (shift_norm) for the 64-bit sum.
  if(word32_high != 0) {
    shift_norm = 32 - WebRtcSpl_NormW32(word32_high);
    residual_energy = (int32_t)(sum64 >> shift_norm);
  } else {
    if((word32_low & 0x80000000) != 0) {
      shift_norm = 1;
      residual_energy = (uint32_t)word32_low >> 1;
    } else {
      shift_norm = WebRtcSpl_NormW32(word32_low);
      residual_energy = word32_low << shift_norm;
      shift_norm = -shift_norm;
    }
  }

  /* Q(q_val_polynomial * 2) * Q(q_val_corr) >> shift_internal >> shift_norm
   *   = Q(q_val_corr - shift_internal - shift_norm + q_val_polynomial * 2)
   */
  *q_val_residual_energy = q_val_corr - shift_internal - shift_norm
                           + q_val_polynomial * 2;

  return residual_energy;
}

void WebRtcIsacfix_GetLpcCoef(int16_t *inLoQ0,
                              int16_t *inHiQ0,
                              MaskFiltstr_enc *maskdata,
                              int16_t snrQ10,
                              const int16_t *pitchGains_Q12,
                              int32_t *gain_lo_hiQ17,
                              int16_t *lo_coeffQ15,
                              int16_t *hi_coeffQ15)
{
  int k, n, ii;
  int pos1, pos2;
  int sh_lo, sh_hi, sh, ssh, shMem;
  int16_t varscaleQ14;

  int16_t tmpQQlo, tmpQQhi;
  int32_t tmp32;
  int16_t tmp16,tmp16b;

  int16_t polyHI[ORDERHI+1];
  int16_t rcQ15_lo[ORDERLO], rcQ15_hi[ORDERHI];


  int16_t DataLoQ6[WINLEN], DataHiQ6[WINLEN];
  int32_t corrloQQ[ORDERLO+2];
  int32_t corrhiQQ[ORDERHI+1];
  int32_t corrlo2QQ[ORDERLO+1];
  int16_t scale;
  int16_t QdomLO, QdomHI, newQdomHI, newQdomLO;

  int32_t res_nrgQQ;
  int32_t sqrt_nrg;

  /* less-noise-at-low-frequencies factor */
  int16_t aaQ14;

  /* Multiplication with 1/sqrt(12) ~= 0.28901734104046 can be done by convertion to
     Q15, i.e. round(0.28901734104046*32768) = 9471, and use 9471/32768.0 ~= 0.289032
  */
  int16_t snrq;
  int shft;

  int16_t tmp16a;
  int32_t tmp32a, tmp32b, tmp32c;

  int16_t a_LOQ11[ORDERLO+1];
  int16_t k_vecloQ15[ORDERLO];
  int16_t a_HIQ12[ORDERHI+1];
  int16_t k_vechiQ15[ORDERHI];

  int16_t stab;

  snrq=snrQ10;

  /* SNR= C * 2 ^ (D * snrq) ; C=0.289, D=0.05*log2(10)=0.166 (~=172 in Q10)*/
  tmp16 = (int16_t)(snrq * 172 >> 10);  // Q10
  tmp16b = exp2_Q10_T(tmp16); // Q10
  snrq = (int16_t)(tmp16b * 285 >> 10);  // Q10

  /* change quallevel depending on pitch gains and level fluctuations */
  WebRtcIsacfix_GetVars(inLoQ0, pitchGains_Q12, &(maskdata->OldEnergy), &varscaleQ14);

  /* less-noise-at-low-frequencies factor */
  /* Calculation of 0.35 * (0.5 + 0.5 * varscale) in fixpoint:
     With 0.35 in Q16 (0.35 ~= 22938/65536.0 = 0.3500061) and varscaleQ14 in Q14,
     we get Q16*Q14>>16 = Q14
  */
  aaQ14 = (int16_t)((22938 * (8192 + (varscaleQ14 >> 1)) + 32768) >> 16);

  /* Calculate tmp = (1.0 + aa*aa); in Q12 */
  tmp16 = (int16_t)(aaQ14 * aaQ14 >> 15);  // Q14*Q14>>15 = Q13
  tmpQQlo = 4096 + (tmp16 >> 1);  // Q12 + Q13>>1 = Q12.

  /* Calculate tmp = (1.0+aa) * (1.0+aa); */
  tmp16 = 8192 + (aaQ14 >> 1);  // 1+a in Q13.
  tmpQQhi = (int16_t)(tmp16 * tmp16 >> 14);  // Q13*Q13>>14 = Q12

  /* replace data in buffer by new look-ahead data */
  for (pos1 = 0; pos1 < QLOOKAHEAD; pos1++) {
    maskdata->DataBufferLoQ0[pos1 + WINLEN - QLOOKAHEAD] = inLoQ0[pos1];
  }

  for (k = 0; k < SUBFRAMES; k++) {

    /* Update input buffer and multiply signal with window */
    for (pos1 = 0; pos1 < WINLEN - UPDATE/2; pos1++) {
      maskdata->DataBufferLoQ0[pos1] = maskdata->DataBufferLoQ0[pos1 + UPDATE/2];
      maskdata->DataBufferHiQ0[pos1] = maskdata->DataBufferHiQ0[pos1 + UPDATE/2];
      DataLoQ6[pos1] = (int16_t)(maskdata->DataBufferLoQ0[pos1] *
          kWindowAutocorr[pos1] >> 15);  // Q0*Q21>>15 = Q6
      DataHiQ6[pos1] = (int16_t)(maskdata->DataBufferHiQ0[pos1] *
          kWindowAutocorr[pos1] >> 15);  // Q0*Q21>>15 = Q6
    }
    pos2 = (int16_t)(k * UPDATE / 2);
    for (n = 0; n < UPDATE/2; n++, pos1++) {
      maskdata->DataBufferLoQ0[pos1] = inLoQ0[QLOOKAHEAD + pos2];
      maskdata->DataBufferHiQ0[pos1] = inHiQ0[pos2++];
      DataLoQ6[pos1] = (int16_t)(maskdata->DataBufferLoQ0[pos1] *
          kWindowAutocorr[pos1] >> 15);  // Q0*Q21>>15 = Q6
      DataHiQ6[pos1] = (int16_t)(maskdata->DataBufferHiQ0[pos1] *
          kWindowAutocorr[pos1] >> 15);  // Q0*Q21>>15 = Q6
    }

    /* Get correlation coefficients */
    /* The highest absolute value measured inside DataLo in the test set
       For DataHi, corresponding value was 160.

       This means that it should be possible to represent the input values
       to WebRtcSpl_AutoCorrelation() as Q6 values (since 307*2^6 =
       19648). Of course, Q0 will also work, but due to the low energy in
       DataLo and DataHi, the outputted autocorrelation will be more accurate
       and mimic the floating point code better, by being in an high as possible
       Q-domain.
    */

    WebRtcIsacfix_AutocorrFix(corrloQQ,DataLoQ6,WINLEN, ORDERLO+1, &scale);
    QdomLO = 12-scale; // QdomLO is the Q-domain of corrloQQ
    sh_lo = WebRtcSpl_NormW32(corrloQQ[0]);
    QdomLO += sh_lo;
    for (ii=0; ii<ORDERLO+2; ii++) {
      corrloQQ[ii] <<= sh_lo;
    }
    /* It is investigated whether it was possible to use 16 bits for the
       32-bit vector corrloQQ, but it didn't work. */

    WebRtcIsacfix_AutocorrFix(corrhiQQ,DataHiQ6,WINLEN, ORDERHI, &scale);

    QdomHI = 12-scale; // QdomHI is the Q-domain of corrhiQQ
    sh_hi = WebRtcSpl_NormW32(corrhiQQ[0]);
    QdomHI += sh_hi;
    for (ii=0; ii<ORDERHI+1; ii++) {
      corrhiQQ[ii] <<= sh_hi;
    }

    /* less noise for lower frequencies, by filtering/scaling autocorrelation sequences */

    /* Calculate corrlo2[0] = tmpQQlo * corrlo[0] - 2.0*tmpQQlo * corrlo[1];*/
    // |corrlo2QQ| in Q(QdomLO-5).
    corrlo2QQ[0] = (WEBRTC_SPL_MUL_16_32_RSFT16(tmpQQlo, corrloQQ[0]) >> 1) -
        (WEBRTC_SPL_MUL_16_32_RSFT16(aaQ14, corrloQQ[1]) >> 2);

    /* Calculate corrlo2[n] = tmpQQlo * corrlo[n] - tmpQQlo * (corrlo[n-1] + corrlo[n+1]);*/
    for (n = 1; n <= ORDERLO; n++) {

      tmp32 = (corrloQQ[n - 1] >> 1) + (corrloQQ[n + 1] >> 1);  // Q(QdomLO-1).
      corrlo2QQ[n] = (WEBRTC_SPL_MUL_16_32_RSFT16(tmpQQlo, corrloQQ[n]) >> 1) -
          (WEBRTC_SPL_MUL_16_32_RSFT16(aaQ14, tmp32) >> 2);
    }
    QdomLO -= 5;

    /* Calculate corrhi[n] = tmpQQhi * corrhi[n]; */
    for (n = 0; n <= ORDERHI; n++) {
      corrhiQQ[n] = WEBRTC_SPL_MUL_16_32_RSFT16(tmpQQhi, corrhiQQ[n]); // Q(12+QdomHI-16) = Q(QdomHI-4)
    }
    QdomHI -= 4;

    /* add white noise floor */
    /* corrlo2QQ is in Q(QdomLO) and corrhiQQ is in Q(QdomHI) */
    /* Calculate corrlo2[0] += 9.5367431640625e-7; and
       corrhi[0]  += 9.5367431640625e-7, where the constant is 1/2^20 */

    tmp32 = WEBRTC_SPL_SHIFT_W32((int32_t) 1, QdomLO-20);
    corrlo2QQ[0] += tmp32;
    tmp32 = WEBRTC_SPL_SHIFT_W32((int32_t) 1, QdomHI-20);
    corrhiQQ[0]  += tmp32;

    /* corrlo2QQ is in Q(QdomLO) and corrhiQQ is in Q(QdomHI) before the following
       code segment, where we want to make sure we get a 1-bit margin */
    for (n = 0; n <= ORDERLO; n++) {
      corrlo2QQ[n] >>= 1;  // Make sure we have a 1-bit margin.
    }
    QdomLO -= 1; // Now, corrlo2QQ is in Q(QdomLO), with a 1-bit margin

    for (n = 0; n <= ORDERHI; n++) {
      corrhiQQ[n] >>= 1;  // Make sure we have a 1-bit margin.
    }
    QdomHI -= 1; // Now, corrhiQQ is in Q(QdomHI), with a 1-bit margin


    newQdomLO = QdomLO;

    for (n = 0; n <= ORDERLO; n++) {
      int32_t tmp, tmpB, tmpCorr;
      int16_t alpha=328; //0.01 in Q15
      int16_t beta=324; //(1-0.01)*0.01=0.0099 in Q15
      int16_t gamma=32440; //(1-0.01)=0.99 in Q15

      if (maskdata->CorrBufLoQQ[n] != 0) {
        shMem=WebRtcSpl_NormW32(maskdata->CorrBufLoQQ[n]);
        sh = QdomLO - maskdata->CorrBufLoQdom[n];
        if (sh<=shMem) {
          tmp = WEBRTC_SPL_SHIFT_W32(maskdata->CorrBufLoQQ[n], sh); // Get CorrBufLoQQ to same domain as corrlo2
          tmp = WEBRTC_SPL_MUL_16_32_RSFT15(alpha, tmp);
        } else if ((sh-shMem)<7){
          tmp = WEBRTC_SPL_SHIFT_W32(maskdata->CorrBufLoQQ[n], shMem); // Shift up CorrBufLoQQ as much as possible
          // Shift |alpha| the number of times required to get |tmp| in QdomLO.
          tmp = WEBRTC_SPL_MUL_16_32_RSFT15(alpha << (sh - shMem), tmp);
        } else {
          tmp = WEBRTC_SPL_SHIFT_W32(maskdata->CorrBufLoQQ[n], shMem); // Shift up CorrBufHiQQ as much as possible
          // Shift |alpha| as much as possible without overflow the number of
          // times required to get |tmp| in QdomLO.
          tmp = WEBRTC_SPL_MUL_16_32_RSFT15(alpha << 6, tmp);
          tmpCorr = corrloQQ[n] >> (sh - shMem - 6);
          tmp = tmp + tmpCorr;
          maskdata->CorrBufLoQQ[n] = tmp;
          newQdomLO = QdomLO-(sh-shMem-6);
          maskdata->CorrBufLoQdom[n] = newQdomLO;
        }
      } else
        tmp = 0;

      tmp = tmp + corrlo2QQ[n];

      maskdata->CorrBufLoQQ[n] = tmp;
      maskdata->CorrBufLoQdom[n] = QdomLO;

      tmp=WEBRTC_SPL_MUL_16_32_RSFT15(beta, tmp);
      tmpB=WEBRTC_SPL_MUL_16_32_RSFT15(gamma, corrlo2QQ[n]);
      corrlo2QQ[n] = tmp + tmpB;
    }
    if( newQdomLO!=QdomLO) {
      for (n = 0; n <= ORDERLO; n++) {
        if (maskdata->CorrBufLoQdom[n] != newQdomLO)
          corrloQQ[n] >>= maskdata->CorrBufLoQdom[n] - newQdomLO;
      }
      QdomLO = newQdomLO;
    }


    newQdomHI = QdomHI;

    for (n = 0; n <= ORDERHI; n++) {
      int32_t tmp, tmpB, tmpCorr;
      int16_t alpha=328; //0.01 in Q15
      int16_t beta=324; //(1-0.01)*0.01=0.0099 in Q15
      int16_t gamma=32440; //(1-0.01)=0.99 in Q1
      if (maskdata->CorrBufHiQQ[n] != 0) {
        shMem=WebRtcSpl_NormW32(maskdata->CorrBufHiQQ[n]);
        sh = QdomHI - maskdata->CorrBufHiQdom[n];
        if (sh<=shMem) {
          tmp = WEBRTC_SPL_SHIFT_W32(maskdata->CorrBufHiQQ[n], sh); // Get CorrBufHiQQ to same domain as corrhi
          tmp = WEBRTC_SPL_MUL_16_32_RSFT15(alpha, tmp);
          tmpCorr = corrhiQQ[n];
          tmp = tmp + tmpCorr;
          maskdata->CorrBufHiQQ[n] = tmp;
          maskdata->CorrBufHiQdom[n] = QdomHI;
        } else if ((sh-shMem)<7) {
          tmp = WEBRTC_SPL_SHIFT_W32(maskdata->CorrBufHiQQ[n], shMem); // Shift up CorrBufHiQQ as much as possible
          // Shift |alpha| the number of times required to get |tmp| in QdomHI.
          tmp = WEBRTC_SPL_MUL_16_32_RSFT15(alpha << (sh - shMem), tmp);
          tmpCorr = corrhiQQ[n];
          tmp = tmp + tmpCorr;
          maskdata->CorrBufHiQQ[n] = tmp;
          maskdata->CorrBufHiQdom[n] = QdomHI;
        } else {
          tmp = WEBRTC_SPL_SHIFT_W32(maskdata->CorrBufHiQQ[n], shMem); // Shift up CorrBufHiQQ as much as possible
          // Shift |alpha| as much as possible without overflow the number of
          // times required to get |tmp| in QdomHI.
          tmp = WEBRTC_SPL_MUL_16_32_RSFT15(alpha << 6, tmp);
          tmpCorr = corrhiQQ[n] >> (sh - shMem - 6);
          tmp = tmp + tmpCorr;
          maskdata->CorrBufHiQQ[n] = tmp;
          newQdomHI = QdomHI-(sh-shMem-6);
          maskdata->CorrBufHiQdom[n] = newQdomHI;
        }
      } else {
        tmp = corrhiQQ[n];
        tmpCorr = tmp;
        maskdata->CorrBufHiQQ[n] = tmp;
        maskdata->CorrBufHiQdom[n] = QdomHI;
      }

      tmp=WEBRTC_SPL_MUL_16_32_RSFT15(beta, tmp);
      tmpB=WEBRTC_SPL_MUL_16_32_RSFT15(gamma, tmpCorr);
      corrhiQQ[n] = tmp + tmpB;
    }

    if( newQdomHI!=QdomHI) {
      for (n = 0; n <= ORDERHI; n++) {
        if (maskdata->CorrBufHiQdom[n] != newQdomHI)
          corrhiQQ[n] >>= maskdata->CorrBufHiQdom[n] - newQdomHI;
      }
      QdomHI = newQdomHI;
    }

    stab=WebRtcSpl_LevinsonW32_JSK(corrlo2QQ, a_LOQ11, k_vecloQ15, ORDERLO);

    if (stab<0) {  // If unstable use lower order
      a_LOQ11[0]=2048;
      for (n = 1; n <= ORDERLO; n++) {
        a_LOQ11[n]=0;
      }

      stab=WebRtcSpl_LevinsonW32_JSK(corrlo2QQ, a_LOQ11, k_vecloQ15, 8);
    }


    WebRtcSpl_LevinsonDurbin(corrhiQQ,  a_HIQ12,  k_vechiQ15, ORDERHI);

    /* bandwidth expansion */
    for (n = 1; n <= ORDERLO; n++) {
      a_LOQ11[n] = (int16_t)((kPolyVecLo[n - 1] * a_LOQ11[n] + (1 << 14)) >>
          15);
    }


    polyHI[0] = a_HIQ12[0];
    for (n = 1; n <= ORDERHI; n++) {
      a_HIQ12[n] = (int16_t)(((int32_t)(kPolyVecHi[n - 1] * a_HIQ12[n]) +
        (1 << 14)) >> 15);
      polyHI[n] = a_HIQ12[n];
    }

    /* Normalize the corrlo2 vector */
    sh = WebRtcSpl_NormW32(corrlo2QQ[0]);
    for (n = 0; n <= ORDERLO; n++) {
      corrlo2QQ[n] <<= sh;
    }
    QdomLO += sh; /* Now, corrlo2QQ is still in Q(QdomLO) */


    /* residual energy */

    sh_lo = 31;
    res_nrgQQ = WebRtcIsacfix_CalculateResidualEnergy(ORDERLO, QdomLO,
        kShiftLowerBand, a_LOQ11, corrlo2QQ, &sh_lo);

    /* Convert to reflection coefficients */
    WebRtcSpl_AToK_JSK(a_LOQ11, ORDERLO, rcQ15_lo);

    if (sh_lo & 0x0001) {
      res_nrgQQ >>= 1;
      sh_lo-=1;
    }


    if( res_nrgQQ > 0 )
    {
      sqrt_nrg=WebRtcSpl_Sqrt(res_nrgQQ);

      /* add hearing threshold and compute the gain */
      /* lo_coeff = varscale * S_N_R / (sqrt_nrg + varscale * H_T_H); */

      tmp32a = varscaleQ14 >> 1;  // H_T_HQ19=65536 (16-17=-1)
      ssh = sh_lo >> 1;  // sqrt_nrg is in Qssh.
      sh = ssh - 14;
      tmp32b = WEBRTC_SPL_SHIFT_W32(tmp32a, sh); // Q14->Qssh
      tmp32c = sqrt_nrg + tmp32b;  // Qssh  (denominator)
      tmp32a = varscaleQ14 * snrq;  // Q24 (numerator)

      sh = WebRtcSpl_NormW32(tmp32c);
      shft = 16 - sh;
      tmp16a = (int16_t) WEBRTC_SPL_SHIFT_W32(tmp32c, -shft); // Q(ssh-shft)  (denominator)

      tmp32b = WebRtcSpl_DivW32W16(tmp32a, tmp16a); // Q(24-ssh+shft)
      sh = ssh-shft-7;
      *gain_lo_hiQ17 = WEBRTC_SPL_SHIFT_W32(tmp32b, sh);  // Gains in Q17
    }
    else
    {
      *gain_lo_hiQ17 = 100;  // Gains in Q17
    }
    gain_lo_hiQ17++;

    /* copy coefficients to output array */
    for (n = 0; n < ORDERLO; n++) {
      *lo_coeffQ15 = (int16_t) (rcQ15_lo[n]);
      lo_coeffQ15++;
    }
    /* residual energy */
    sh_hi = 31;
    res_nrgQQ = WebRtcIsacfix_CalculateResidualEnergy(ORDERHI, QdomHI,
        kShiftHigherBand, a_HIQ12, corrhiQQ, &sh_hi);

    /* Convert to reflection coefficients */
    WebRtcSpl_LpcToReflCoef(polyHI, ORDERHI, rcQ15_hi);

    if (sh_hi & 0x0001) {
      res_nrgQQ >>= 1;
      sh_hi-=1;
    }


    if( res_nrgQQ > 0 )
    {
      sqrt_nrg=WebRtcSpl_Sqrt(res_nrgQQ);


      /* add hearing threshold and compute the gain */
      /* hi_coeff = varscale * S_N_R / (sqrt_nrg + varscale * H_T_H); */

      tmp32a = varscaleQ14 >> 1;  // H_T_HQ19=65536 (16-17=-1)

      ssh = sh_hi >> 1;  // |sqrt_nrg| is in Qssh.
      sh = ssh - 14;
      tmp32b = WEBRTC_SPL_SHIFT_W32(tmp32a, sh); // Q14->Qssh
      tmp32c = sqrt_nrg + tmp32b;  // Qssh  (denominator)
      tmp32a = varscaleQ14 * snrq;  // Q24 (numerator)

      sh = WebRtcSpl_NormW32(tmp32c);
      shft = 16 - sh;
      tmp16a = (int16_t) WEBRTC_SPL_SHIFT_W32(tmp32c, -shft); // Q(ssh-shft)  (denominator)

      tmp32b = WebRtcSpl_DivW32W16(tmp32a, tmp16a); // Q(24-ssh+shft)
      sh = ssh-shft-7;
      *gain_lo_hiQ17 = WEBRTC_SPL_SHIFT_W32(tmp32b, sh);  // Gains in Q17
    }
    else
    {
      *gain_lo_hiQ17 = 100;  // Gains in Q17
    }
    gain_lo_hiQ17++;


    /* copy coefficients to output array */
    for (n = 0; n < ORDERHI; n++) {
      *hi_coeffQ15 = rcQ15_hi[n];
      hi_coeffQ15++;
    }
  }
}
