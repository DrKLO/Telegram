/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/isac/fix/source/pitch_estimator.h"
#include "common_audio/signal_processing/include/signal_processing_library.h"
#include "rtc_base/compile_assert_c.h"

/* log2[0.2, 0.5, 0.98] in Q8 */
static const int16_t kLogLagWinQ8[3] = {
  -594, -256, -7
};

/* [1 -0.75 0.25] in Q12 */
static const int16_t kACoefQ12[3] = {
  4096, -3072, 1024
};

int32_t WebRtcIsacfix_Log2Q8(uint32_t x) {
  int32_t zeros;
  int16_t frac;

  zeros=WebRtcSpl_NormU32(x);
  frac = (int16_t)(((x << zeros) & 0x7FFFFFFF) >> 23);
  /* log2(magn(i)) */

  return ((31 - zeros) << 8) + frac;
}

static __inline int16_t Exp2Q10(int16_t x) { // Both in and out in Q10

  int16_t tmp16_1, tmp16_2;

  tmp16_2=(int16_t)(0x0400|(x&0x03FF));
  tmp16_1 = -(x >> 10);
  if(tmp16_1>0)
    return tmp16_2 >> tmp16_1;
  else
    return tmp16_2 << -tmp16_1;

}



/* 1D parabolic interpolation . All input and output values are in Q8 */
static __inline void Intrp1DQ8(int32_t *x, int32_t *fx, int32_t *y, int32_t *fy) {

  int16_t sign1=1, sign2=1;
  int32_t r32, q32, t32, nom32, den32;
  int16_t t16, tmp16, tmp16_1;

  if ((fx[0]>0) && (fx[2]>0)) {
    r32=fx[1]-fx[2];
    q32=fx[0]-fx[1];
    nom32=q32+r32;
    den32 = (q32 - r32) * 2;
    if (nom32<0)
      sign1=-1;
    if (den32<0)
      sign2=-1;

    /* t = (q32+r32)/(2*(q32-r32)) = (fx[0]-fx[1] + fx[1]-fx[2])/(2 * fx[0]-fx[1] - (fx[1]-fx[2]))*/
    /* (Signs are removed because WebRtcSpl_DivResultInQ31 can't handle negative numbers) */
    /* t in Q31, without signs */
    t32 = WebRtcSpl_DivResultInQ31(nom32 * sign1, den32 * sign2);

    t16 = (int16_t)(t32 >> 23);  /* Q8 */
    t16=t16*sign1*sign2;        /* t in Q8 with signs */

    *y = x[0]+t16;          /* Q8 */
    // *y = x[1]+t16;          /* Q8 */

    /* The following code calculates fy in three steps */
    /* fy = 0.5 * t * (t-1) * fx[0] + (1-t*t) * fx[1] + 0.5 * t * (t+1) * fx[2]; */

    /* Part I: 0.5 * t * (t-1) * fx[0] */
    tmp16_1 = (int16_t)(t16 * t16);  /* Q8*Q8=Q16 */
    tmp16_1 >>= 2;  /* Q16>>2 = Q14 */
    t16 <<= 6;  /* Q8<<6 = Q14  */
    tmp16 = tmp16_1-t16;
    *fy = WEBRTC_SPL_MUL_16_32_RSFT15(tmp16, fx[0]); /* (Q14 * Q8 >>15)/2 = Q8 */

    /* Part II: (1-t*t) * fx[1] */
    tmp16 = 16384-tmp16_1;        /* 1 in Q14 - Q14 */
    *fy += WEBRTC_SPL_MUL_16_32_RSFT14(tmp16, fx[1]);/* Q14 * Q8 >> 14 = Q8 */

    /* Part III: 0.5 * t * (t+1) * fx[2] */
    tmp16 = tmp16_1+t16;
    *fy += WEBRTC_SPL_MUL_16_32_RSFT15(tmp16, fx[2]);/* (Q14 * Q8 >>15)/2 = Q8 */
  } else {
    *y = x[0];
    *fy= fx[1];
  }
}


static void FindFour32(int32_t *in, int16_t length, int16_t *bestind)
{
  int32_t best[4]= {-100, -100, -100, -100};
  int16_t k;

  for (k=0; k<length; k++) {
    if (in[k] > best[3]) {
      if (in[k] > best[2]) {
        if (in[k] > best[1]) {
          if (in[k] > best[0]) { // The Best
            best[3] = best[2];
            bestind[3] = bestind[2];
            best[2] = best[1];
            bestind[2] = bestind[1];
            best[1] = best[0];
            bestind[1] = bestind[0];
            best[0] = in[k];
            bestind[0] = k;
          } else { // 2nd best
            best[3] = best[2];
            bestind[3] = bestind[2];
            best[2] = best[1];
            bestind[2] = bestind[1];
            best[1] = in[k];
            bestind[1] = k;
          }
        } else { // 3rd best
          best[3] = best[2];
          bestind[3] = bestind[2];
          best[2] = in[k];
          bestind[2] = k;
        }
      } else {  // 4th best
        best[3] = in[k];
        bestind[3] = k;
      }
    }
  }
}





extern void WebRtcIsacfix_PCorr2Q32(const int16_t *in, int32_t *logcorQ8);



void WebRtcIsacfix_InitialPitch(const int16_t *in, /* Q0 */
                                PitchAnalysisStruct *State,
                                int16_t *lagsQ7                   /* Q7 */
                                )
{
  int16_t buf_dec16[PITCH_CORR_LEN2+PITCH_CORR_STEP2+PITCH_MAX_LAG/2+2];
  int32_t *crrvecQ8_1,*crrvecQ8_2;
  int32_t cv1q[PITCH_LAG_SPAN2+2],cv2q[PITCH_LAG_SPAN2+2], peakvq[PITCH_LAG_SPAN2+2];
  int k;
  int16_t peaks_indq;
  int16_t peakiq[PITCH_LAG_SPAN2];
  int32_t corr;
  int32_t corr32, corr_max32, corr_max_o32;
  int16_t npkq;
  int16_t best4q[4]={0,0,0,0};
  int32_t xq[3],yq[1],fyq[1];
  int32_t *fxq;
  int32_t best_lag1q, best_lag2q;
  int32_t tmp32a,tmp32b,lag32,ratq;
  int16_t start;
  int16_t oldgQ12, tmp16a, tmp16b, gain_bias16,tmp16c, tmp16d, bias16;
  int32_t tmp32c,tmp32d, tmp32e;
  int16_t old_lagQ;
  int32_t old_lagQ8;
  int32_t lagsQ8[4];

  old_lagQ = State->PFstr_wght.oldlagQ7; // Q7
  old_lagQ8 = old_lagQ << 1;  // Q8

  oldgQ12= State->PFstr_wght.oldgainQ12;

  crrvecQ8_1=&cv1q[1];
  crrvecQ8_2=&cv2q[1];


  /* copy old values from state buffer */
  memcpy(buf_dec16, State->dec_buffer16, sizeof(State->dec_buffer16));

  /* decimation; put result after the old values */
  WebRtcIsacfix_DecimateAllpass32(in, State->decimator_state32, PITCH_FRAME_LEN,
                                  &buf_dec16[PITCH_CORR_LEN2+PITCH_CORR_STEP2+PITCH_MAX_LAG/2-PITCH_FRAME_LEN/2+2]);

  /* low-pass filtering */
  start= PITCH_CORR_LEN2+PITCH_CORR_STEP2+PITCH_MAX_LAG/2-PITCH_FRAME_LEN/2+2;
  WebRtcSpl_FilterARFastQ12(&buf_dec16[start],&buf_dec16[start],(int16_t*)kACoefQ12,3, PITCH_FRAME_LEN/2);

  /* copy end part back into state buffer */
  for (k = 0; k < (PITCH_CORR_LEN2+PITCH_CORR_STEP2+PITCH_MAX_LAG/2-PITCH_FRAME_LEN/2+2); k++)
    State->dec_buffer16[k] = buf_dec16[k+PITCH_FRAME_LEN/2];


  /* compute correlation for first and second half of the frame */
  WebRtcIsacfix_PCorr2Q32(buf_dec16, crrvecQ8_1);
  WebRtcIsacfix_PCorr2Q32(buf_dec16 + PITCH_CORR_STEP2, crrvecQ8_2);


  /* bias towards pitch lag of previous frame */
  tmp32a = WebRtcIsacfix_Log2Q8((uint32_t) old_lagQ8) - 2304;
      // log2(0.5*oldlag) in Q8
  tmp32b = oldgQ12 * oldgQ12 >> 10;  // Q12 & * 4.0;
  gain_bias16 = (int16_t) tmp32b;  //Q12
  if (gain_bias16 > 3276) gain_bias16 = 3276; // 0.8 in Q12


  for (k = 0; k < PITCH_LAG_SPAN2; k++)
  {
    if (crrvecQ8_1[k]>0) {
      tmp32b = WebRtcIsacfix_Log2Q8((uint32_t) (k + (PITCH_MIN_LAG/2-2)));
      tmp16a = (int16_t) (tmp32b - tmp32a); // Q8 & fabs(ratio)<4
      tmp32c = tmp16a * tmp16a >> 6;  // Q10
      tmp16b = (int16_t) tmp32c; // Q10 & <8
      tmp32d = tmp16b * 177 >> 8;  // mult with ln2 in Q8
      tmp16c = (int16_t) tmp32d; // Q10 & <4
      tmp16d = Exp2Q10((int16_t) -tmp16c); //Q10
      tmp32c = gain_bias16 * tmp16d >> 13;  // Q10  & * 0.5
      bias16 = (int16_t) (1024 + tmp32c); // Q10
      tmp32b = WebRtcIsacfix_Log2Q8((uint32_t)bias16) - 2560;
          // Q10 in -> Q8 out with 10*2^8 offset
      crrvecQ8_1[k] += tmp32b ; // -10*2^8 offset
    }
  }

  /* taper correlation functions */
  for (k = 0; k < 3; k++) {
    crrvecQ8_1[k] += kLogLagWinQ8[k];
    crrvecQ8_2[k] += kLogLagWinQ8[k];

    crrvecQ8_1[PITCH_LAG_SPAN2-1-k] += kLogLagWinQ8[k];
    crrvecQ8_2[PITCH_LAG_SPAN2-1-k] += kLogLagWinQ8[k];
  }


  /* Make zeropadded corr vectors */
  cv1q[0]=0;
  cv2q[0]=0;
  cv1q[PITCH_LAG_SPAN2+1]=0;
  cv2q[PITCH_LAG_SPAN2+1]=0;
  corr_max32 = 0;

  for (k = 1; k <= PITCH_LAG_SPAN2; k++)
  {


    corr32=crrvecQ8_1[k-1];
    if (corr32 > corr_max32)
      corr_max32 = corr32;

    corr32=crrvecQ8_2[k-1];
    corr32 += -4; // Compensate for later (log2(0.99))

    if (corr32 > corr_max32)
      corr_max32 = corr32;

  }

  /* threshold value to qualify as a peak */
  // corr_max32 += -726; // log(0.14)/log(2.0) in Q8
  corr_max32 += -1000; // log(0.14)/log(2.0) in Q8
  corr_max_o32 = corr_max32;


  /* find peaks in corr1 */
  peaks_indq = 0;
  for (k = 1; k <= PITCH_LAG_SPAN2; k++)
  {
    corr32=cv1q[k];
    if (corr32>corr_max32) { // Disregard small peaks
      if ((corr32>=cv1q[k-1]) && (corr32>cv1q[k+1])) { // Peak?
        peakvq[peaks_indq] = corr32;
        peakiq[peaks_indq++] = k;
      }
    }
  }


  /* find highest interpolated peak */
  corr_max32=0;
  best_lag1q =0;
  if (peaks_indq > 0) {
    FindFour32(peakvq, (int16_t) peaks_indq, best4q);
    npkq = WEBRTC_SPL_MIN(peaks_indq, 4);

    for (k=0;k<npkq;k++) {

      lag32 =  peakiq[best4q[k]];
      fxq = &cv1q[peakiq[best4q[k]]-1];
      xq[0]= lag32;
      xq[0] <<= 8;
      Intrp1DQ8(xq, fxq, yq, fyq);

      tmp32a= WebRtcIsacfix_Log2Q8((uint32_t) *yq) - 2048; // offset 8*2^8
      /* Bias towards short lags */
      /* log(pow(0.8, log(2.0 * *y )))/log(2.0) */
      tmp32b = (int16_t)tmp32a * -42 >> 8;
      tmp32c= tmp32b + 256;
      *fyq += tmp32c;
      if (*fyq > corr_max32) {
        corr_max32 = *fyq;
        best_lag1q = *yq;
      }
    }
    tmp32b = (best_lag1q - OFFSET_Q8) * 2;
    lagsQ8[0] = tmp32b + PITCH_MIN_LAG_Q8;
    lagsQ8[1] = lagsQ8[0];
  } else {
    lagsQ8[0] = old_lagQ8;
    lagsQ8[1] = lagsQ8[0];
  }

  /* Bias towards constant pitch */
  tmp32a = lagsQ8[0] - PITCH_MIN_LAG_Q8;
  ratq = (tmp32a >> 1) + OFFSET_Q8;

  for (k = 1; k <= PITCH_LAG_SPAN2; k++)
  {
    tmp32a = k << 7; // 0.5*k Q8
    tmp32b = tmp32a * 2 - ratq;  // Q8
    tmp32c = (int16_t)tmp32b * (int16_t)tmp32b >> 8;  // Q8

    tmp32b = tmp32c + (ratq >> 1);
        // (k-r)^2 + 0.5 * r  Q8
    tmp32c = WebRtcIsacfix_Log2Q8((uint32_t)tmp32a) - 2048;
        // offset 8*2^8 , log2(0.5*k) Q8
    tmp32d = WebRtcIsacfix_Log2Q8((uint32_t)tmp32b) - 2048;
        // offset 8*2^8 , log2(0.5*k) Q8
    tmp32e =  tmp32c - tmp32d;

    cv2q[k] += tmp32e >> 1;

  }

  /* find peaks in corr2 */
  corr_max32 = corr_max_o32;
  peaks_indq = 0;

  for (k = 1; k <= PITCH_LAG_SPAN2; k++)
  {
    corr=cv2q[k];
    if (corr>corr_max32) { // Disregard small peaks
      if ((corr>=cv2q[k-1]) && (corr>cv2q[k+1])) { // Peak?
        peakvq[peaks_indq] = corr;
        peakiq[peaks_indq++] = k;
      }
    }
  }



  /* find highest interpolated peak */
  corr_max32 = 0;
  best_lag2q =0;
  if (peaks_indq > 0) {

    FindFour32(peakvq, (int16_t) peaks_indq, best4q);
    npkq = WEBRTC_SPL_MIN(peaks_indq, 4);
    for (k=0;k<npkq;k++) {

      lag32 =  peakiq[best4q[k]];
      fxq = &cv2q[peakiq[best4q[k]]-1];

      xq[0]= lag32;
      xq[0] <<= 8;
      Intrp1DQ8(xq, fxq, yq, fyq);

      /* Bias towards short lags */
      /* log(pow(0.8, log(2.0f * *y )))/log(2.0f) */
      tmp32a= WebRtcIsacfix_Log2Q8((uint32_t) *yq) - 2048; // offset 8*2^8
      tmp32b = (int16_t)tmp32a * -82 >> 8;
      tmp32c= tmp32b + 256;
      *fyq += tmp32c;
      if (*fyq > corr_max32) {
        corr_max32 = *fyq;
        best_lag2q = *yq;
      }
    }

    tmp32b = (best_lag2q - OFFSET_Q8) * 2;
    lagsQ8[2] = tmp32b + PITCH_MIN_LAG_Q8;
    lagsQ8[3] = lagsQ8[2];
  } else {
    lagsQ8[2] = lagsQ8[0];
    lagsQ8[3] = lagsQ8[0];
  }

  lagsQ7[0] = (int16_t)(lagsQ8[0] >> 1);
  lagsQ7[1] = (int16_t)(lagsQ8[1] >> 1);
  lagsQ7[2] = (int16_t)(lagsQ8[2] >> 1);
  lagsQ7[3] = (int16_t)(lagsQ8[3] >> 1);
}



void WebRtcIsacfix_PitchAnalysis(const int16_t *inn,               /* PITCH_FRAME_LEN samples */
                                 int16_t *outQ0,                  /* PITCH_FRAME_LEN+QLOOKAHEAD samples */
                                 PitchAnalysisStruct *State,
                                 int16_t *PitchLags_Q7,
                                 int16_t *PitchGains_Q12)
{
  int16_t inbufQ0[PITCH_FRAME_LEN + QLOOKAHEAD];
  int16_t k;

  /* inital pitch estimate */
  WebRtcIsacfix_InitialPitch(inn, State,  PitchLags_Q7);


  /* Calculate gain */
  WebRtcIsacfix_PitchFilterGains(inn, &(State->PFstr_wght), PitchLags_Q7, PitchGains_Q12);

  /* concatenate previous input's end and current input */
  for (k = 0; k < QLOOKAHEAD; k++) {
    inbufQ0[k] = State->inbuf[k];
  }
  for (k = 0; k < PITCH_FRAME_LEN; k++) {
    inbufQ0[k+QLOOKAHEAD] = (int16_t) inn[k];
  }

  /* lookahead pitch filtering for masking analysis */
  WebRtcIsacfix_PitchFilter(inbufQ0, outQ0, &(State->PFstr), PitchLags_Q7,PitchGains_Q12, 2);


  /* store last part of input */
  for (k = 0; k < QLOOKAHEAD; k++) {
    State->inbuf[k] = inbufQ0[k + PITCH_FRAME_LEN];
  }
}
