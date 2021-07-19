/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/isac/main/source/pitch_estimator.h"

#include <math.h>
#include <memory.h>
#include <string.h>
#ifdef WEBRTC_ANDROID
#include <stdlib.h>
#endif

#include "modules/audio_coding/codecs/isac/main/source/filter_functions.h"
#include "modules/audio_coding/codecs/isac/main/source/pitch_filter.h"
#include "rtc_base/system/ignore_warnings.h"

static const double kInterpolWin[8] = {-0.00067556028640,  0.02184247643159, -0.12203175715679,  0.60086484101160,
                                       0.60086484101160, -0.12203175715679,  0.02184247643159, -0.00067556028640};

/* interpolation filter */
__inline static void IntrepolFilter(double *data_ptr, double *intrp)
{
  *intrp = kInterpolWin[0] * data_ptr[-3];
  *intrp += kInterpolWin[1] * data_ptr[-2];
  *intrp += kInterpolWin[2] * data_ptr[-1];
  *intrp += kInterpolWin[3] * data_ptr[0];
  *intrp += kInterpolWin[4] * data_ptr[1];
  *intrp += kInterpolWin[5] * data_ptr[2];
  *intrp += kInterpolWin[6] * data_ptr[3];
  *intrp += kInterpolWin[7] * data_ptr[4];
}


/* 2D parabolic interpolation */
/* probably some 0.5 factors can be eliminated, and the square-roots can be removed from the Cholesky fact. */
__inline static void Intrpol2D(double T[3][3], double *x, double *y, double *peak_val)
{
  double c, b[2], A[2][2];
  double t1, t2, d;
  double delta1, delta2;


  // double T[3][3] = {{-1.25, -.25,-.25}, {-.25, .75, .75}, {-.25, .75, .75}};
  // should result in: delta1 = 0.5;  delta2 = 0.0;  peak_val = 1.0

  c = T[1][1];
  b[0] = 0.5 * (T[1][2] + T[2][1] - T[0][1] - T[1][0]);
  b[1] = 0.5 * (T[1][0] + T[2][1] - T[0][1] - T[1][2]);
  A[0][1] = -0.5 * (T[0][1] + T[2][1] - T[1][0] - T[1][2]);
  t1 = 0.5 * (T[0][0] + T[2][2]) - c;
  t2 = 0.5 * (T[2][0] + T[0][2]) - c;
  d = (T[0][1] + T[1][2] + T[1][0] + T[2][1]) - 4.0 * c - t1 - t2;
  A[0][0] = -t1 - 0.5 * d;
  A[1][1] = -t2 - 0.5 * d;

  /* deal with singularities or ill-conditioned cases */
  if ( (A[0][0] < 1e-7) || ((A[0][0] * A[1][1] - A[0][1] * A[0][1]) < 1e-7) ) {
    *peak_val = T[1][1];
    return;
  }

  /* Cholesky decomposition: replace A by upper-triangular factor */
  A[0][0] = sqrt(A[0][0]);
  A[0][1] = A[0][1] / A[0][0];
  A[1][1] = sqrt(A[1][1] - A[0][1] * A[0][1]);

  /* compute [x; y] = -0.5 * inv(A) * b */
  t1 = b[0] / A[0][0];
  t2 = (b[1] - t1 * A[0][1]) / A[1][1];
  delta2 = t2 / A[1][1];
  delta1 = 0.5 * (t1 - delta2 * A[0][1]) / A[0][0];
  delta2 *= 0.5;

  /* limit norm */
  t1 = delta1 * delta1 + delta2 * delta2;
  if (t1 > 1.0) {
    delta1 /= t1;
    delta2 /= t1;
  }

  *peak_val = 0.5 * (b[0] * delta1 + b[1] * delta2) + c;

  *x += delta1;
  *y += delta2;
}


static void PCorr(const double *in, double *outcorr)
{
  double sum, ysum, prod;
  const double *x, *inptr;
  int k, n;

  //ysum = 1e-6;          /* use this with float (i.s.o. double)! */
  ysum = 1e-13;
  sum = 0.0;
  x = in + PITCH_MAX_LAG/2 + 2;
  for (n = 0; n < PITCH_CORR_LEN2; n++) {
    ysum += in[n] * in[n];
    sum += x[n] * in[n];
  }

  outcorr += PITCH_LAG_SPAN2 - 1;     /* index of last element in array */
  *outcorr = sum / sqrt(ysum);

  for (k = 1; k < PITCH_LAG_SPAN2; k++) {
    ysum -= in[k-1] * in[k-1];
    ysum += in[PITCH_CORR_LEN2 + k - 1] * in[PITCH_CORR_LEN2 + k - 1];
    sum = 0.0;
    inptr = &in[k];
    prod = x[0] * inptr[0];
    for (n = 1; n < PITCH_CORR_LEN2; n++) {
      sum += prod;
      prod = x[n] * inptr[n];
    }
    sum += prod;
    outcorr--;
    *outcorr = sum / sqrt(ysum);
  }
}

static void WebRtcIsac_AllpassFilterForDec(double* InOut,
                                           const double* APSectionFactors,
                                           size_t lengthInOut,
                                           double* FilterState) {
  // This performs all-pass filtering--a series of first order all-pass
  // sections are used to filter the input in a cascade manner.
  size_t n, j;
  double temp;
  for (j = 0; j < ALLPASSSECTIONS; j++) {
    for (n = 0; n < lengthInOut; n += 2) {
      temp = InOut[n];  // store input
      InOut[n] = FilterState[j] + APSectionFactors[j] * temp;
      FilterState[j] = -APSectionFactors[j] * InOut[n] + temp;
    }
  }
}

static void WebRtcIsac_DecimateAllpass(
    const double* in,
    double* state_in,  // array of size: 2*ALLPASSSECTIONS+1
    size_t N,          // number of input samples
    double* out) {     // array of size N/2

  static const double APupper[ALLPASSSECTIONS] = {0.0347, 0.3826};
  static const double APlower[ALLPASSSECTIONS] = {0.1544, 0.744};

  size_t n;
  double data_vec[PITCH_FRAME_LEN];

  /* copy input */
  memcpy(data_vec + 1, in, sizeof(double) * (N - 1));

  data_vec[0] = state_in[2 * ALLPASSSECTIONS];  // the z^(-1) state
  state_in[2 * ALLPASSSECTIONS] = in[N - 1];

  WebRtcIsac_AllpassFilterForDec(data_vec + 1, APupper, N, state_in);
  WebRtcIsac_AllpassFilterForDec(data_vec, APlower, N,
                                 state_in + ALLPASSSECTIONS);

  for (n = 0; n < N / 2; n++)
    out[n] = data_vec[2 * n] + data_vec[2 * n + 1];
}

RTC_PUSH_IGNORING_WFRAME_LARGER_THAN()

static void WebRtcIsac_InitializePitch(const double* in,
                                       const double old_lag,
                                       const double old_gain,
                                       PitchAnalysisStruct* State,
                                       double* lags) {
  double buf_dec[PITCH_CORR_LEN2+PITCH_CORR_STEP2+PITCH_MAX_LAG/2+2];
  double ratio, log_lag, gain_bias;
  double bias;
  double corrvec1[PITCH_LAG_SPAN2];
  double corrvec2[PITCH_LAG_SPAN2];
  int m, k;
  // Allocating 10 extra entries at the begining of the CorrSurf
  double corrSurfBuff[10 + (2*PITCH_BW+3)*(PITCH_LAG_SPAN2+4)];
  double* CorrSurf[2*PITCH_BW+3];
  double *CorrSurfPtr1, *CorrSurfPtr2;
  double LagWin[3] = {0.2, 0.5, 0.98};
  int ind1, ind2, peaks_ind, peak, max_ind;
  int peaks[PITCH_MAX_NUM_PEAKS];
  double adj, gain_tmp;
  double corr, corr_max;
  double intrp_a, intrp_b, intrp_c, intrp_d;
  double peak_vals[PITCH_MAX_NUM_PEAKS];
  double lags1[PITCH_MAX_NUM_PEAKS];
  double lags2[PITCH_MAX_NUM_PEAKS];
  double T[3][3];
  int row;

  for(k = 0; k < 2*PITCH_BW+3; k++)
  {
    CorrSurf[k] = &corrSurfBuff[10 + k * (PITCH_LAG_SPAN2+4)];
  }
  /* reset CorrSurf matrix */
  memset(corrSurfBuff, 0, sizeof(double) * (10 + (2*PITCH_BW+3) * (PITCH_LAG_SPAN2+4)));

  //warnings -DH
  max_ind = 0;
  peak = 0;

  /* copy old values from state buffer */
  memcpy(buf_dec, State->dec_buffer, sizeof(double) * (PITCH_CORR_LEN2+PITCH_CORR_STEP2+PITCH_MAX_LAG/2-PITCH_FRAME_LEN/2+2));

  /* decimation; put result after the old values */
  WebRtcIsac_DecimateAllpass(in, State->decimator_state, PITCH_FRAME_LEN,
                             &buf_dec[PITCH_CORR_LEN2+PITCH_CORR_STEP2+PITCH_MAX_LAG/2-PITCH_FRAME_LEN/2+2]);

  /* low-pass filtering */
  for (k = PITCH_CORR_LEN2+PITCH_CORR_STEP2+PITCH_MAX_LAG/2-PITCH_FRAME_LEN/2+2; k < PITCH_CORR_LEN2+PITCH_CORR_STEP2+PITCH_MAX_LAG/2+2; k++)
    buf_dec[k] += 0.75 * buf_dec[k-1] - 0.25 * buf_dec[k-2];

  /* copy end part back into state buffer */
  memcpy(State->dec_buffer, buf_dec+PITCH_FRAME_LEN/2, sizeof(double) * (PITCH_CORR_LEN2+PITCH_CORR_STEP2+PITCH_MAX_LAG/2-PITCH_FRAME_LEN/2+2));

  /* compute correlation for first and second half of the frame */
  PCorr(buf_dec, corrvec1);
  PCorr(buf_dec + PITCH_CORR_STEP2, corrvec2);

  /* bias towards pitch lag of previous frame */
  log_lag = log(0.5 * old_lag);
  gain_bias = 4.0 * old_gain * old_gain;
  if (gain_bias > 0.8) gain_bias = 0.8;
  for (k = 0; k < PITCH_LAG_SPAN2; k++)
  {
    ratio = log((double) (k + (PITCH_MIN_LAG/2-2))) - log_lag;
    bias = 1.0 + gain_bias * exp(-5.0 * ratio * ratio);
    corrvec1[k] *= bias;
  }

  /* taper correlation functions */
  for (k = 0; k < 3; k++) {
    gain_tmp = LagWin[k];
    corrvec1[k] *= gain_tmp;
    corrvec2[k] *= gain_tmp;
    corrvec1[PITCH_LAG_SPAN2-1-k] *= gain_tmp;
    corrvec2[PITCH_LAG_SPAN2-1-k] *= gain_tmp;
  }

  corr_max = 0.0;
  /* fill middle row of correlation surface */
  ind1 = 0;
  ind2 = 0;
  CorrSurfPtr1 = &CorrSurf[PITCH_BW][2];
  for (k = 0; k < PITCH_LAG_SPAN2; k++) {
    corr = corrvec1[ind1++] + corrvec2[ind2++];
    CorrSurfPtr1[k] = corr;
    if (corr > corr_max) {
      corr_max = corr;  /* update maximum */
      max_ind = (int)(&CorrSurfPtr1[k] - &CorrSurf[0][0]);
    }
  }
  /* fill first and last rows of correlation surface */
  ind1 = 0;
  ind2 = PITCH_BW;
  CorrSurfPtr1 = &CorrSurf[0][2];
  CorrSurfPtr2 = &CorrSurf[2*PITCH_BW][PITCH_BW+2];
  for (k = 0; k < PITCH_LAG_SPAN2-PITCH_BW; k++) {
    ratio = ((double) (ind1 + 12)) / ((double) (ind2 + 12));
    adj = 0.2 * ratio * (2.0 - ratio);   /* adjustment factor; inverse parabola as a function of ratio */
    corr = adj * (corrvec1[ind1] + corrvec2[ind2]);
    CorrSurfPtr1[k] = corr;
    if (corr > corr_max) {
      corr_max = corr;  /* update maximum */
      max_ind = (int)(&CorrSurfPtr1[k] - &CorrSurf[0][0]);
    }
    corr = adj * (corrvec1[ind2++] + corrvec2[ind1++]);
    CorrSurfPtr2[k] = corr;
    if (corr > corr_max) {
      corr_max = corr;  /* update maximum */
      max_ind = (int)(&CorrSurfPtr2[k] - &CorrSurf[0][0]);
    }
  }
  /* fill second and next to last rows of correlation surface */
  ind1 = 0;
  ind2 = PITCH_BW-1;
  CorrSurfPtr1 = &CorrSurf[1][2];
  CorrSurfPtr2 = &CorrSurf[2*PITCH_BW-1][PITCH_BW+1];
  for (k = 0; k < PITCH_LAG_SPAN2-PITCH_BW+1; k++) {
    ratio = ((double) (ind1 + 12)) / ((double) (ind2 + 12));
    adj = 0.9 * ratio * (2.0 - ratio);   /* adjustment factor; inverse parabola as a function of ratio */
    corr = adj * (corrvec1[ind1] + corrvec2[ind2]);
    CorrSurfPtr1[k] = corr;
    if (corr > corr_max) {
      corr_max = corr;  /* update maximum */
      max_ind = (int)(&CorrSurfPtr1[k] - &CorrSurf[0][0]);
    }
    corr = adj * (corrvec1[ind2++] + corrvec2[ind1++]);
    CorrSurfPtr2[k] = corr;
    if (corr > corr_max) {
      corr_max = corr;  /* update maximum */
      max_ind = (int)(&CorrSurfPtr2[k] - &CorrSurf[0][0]);
    }
  }
  /* fill remainder of correlation surface */
  for (m = 2; m < PITCH_BW; m++) {
    ind1 = 0;
    ind2 = PITCH_BW - m;         /* always larger than ind1 */
    CorrSurfPtr1 = &CorrSurf[m][2];
    CorrSurfPtr2 = &CorrSurf[2*PITCH_BW-m][PITCH_BW+2-m];
    for (k = 0; k < PITCH_LAG_SPAN2-PITCH_BW+m; k++) {
      ratio = ((double) (ind1 + 12)) / ((double) (ind2 + 12));
      adj = ratio * (2.0 - ratio);    /* adjustment factor; inverse parabola as a function of ratio */
      corr = adj * (corrvec1[ind1] + corrvec2[ind2]);
      CorrSurfPtr1[k] = corr;
      if (corr > corr_max) {
        corr_max = corr;  /* update maximum */
        max_ind = (int)(&CorrSurfPtr1[k] - &CorrSurf[0][0]);
      }
      corr = adj * (corrvec1[ind2++] + corrvec2[ind1++]);
      CorrSurfPtr2[k] = corr;
      if (corr > corr_max) {
        corr_max = corr;  /* update maximum */
        max_ind = (int)(&CorrSurfPtr2[k] - &CorrSurf[0][0]);
      }
    }
  }

  /* threshold value to qualify as a peak */
  corr_max *= 0.6;

  peaks_ind = 0;
  /* find peaks */
  for (m = 1; m < PITCH_BW+1; m++) {
    if (peaks_ind == PITCH_MAX_NUM_PEAKS) break;
    CorrSurfPtr1 = &CorrSurf[m][2];
    for (k = 2; k < PITCH_LAG_SPAN2-PITCH_BW-2+m; k++) {
      corr = CorrSurfPtr1[k];
      if (corr > corr_max) {
        if ( (corr > CorrSurfPtr1[k - (PITCH_LAG_SPAN2+5)]) && (corr > CorrSurfPtr1[k - (PITCH_LAG_SPAN2+4)]) ) {
          if ( (corr > CorrSurfPtr1[k + (PITCH_LAG_SPAN2+4)]) && (corr > CorrSurfPtr1[k + (PITCH_LAG_SPAN2+5)]) ) {
            /* found a peak; store index into matrix */
            peaks[peaks_ind++] = (int)(&CorrSurfPtr1[k] - &CorrSurf[0][0]);
            if (peaks_ind == PITCH_MAX_NUM_PEAKS) break;
          }
        }
      }
    }
  }
  for (m = PITCH_BW+1; m < 2*PITCH_BW; m++) {
    if (peaks_ind == PITCH_MAX_NUM_PEAKS) break;
    CorrSurfPtr1 = &CorrSurf[m][2];
    for (k = 2+m-PITCH_BW; k < PITCH_LAG_SPAN2-2; k++) {
      corr = CorrSurfPtr1[k];
      if (corr > corr_max) {
        if ( (corr > CorrSurfPtr1[k - (PITCH_LAG_SPAN2+5)]) && (corr > CorrSurfPtr1[k - (PITCH_LAG_SPAN2+4)]) ) {
          if ( (corr > CorrSurfPtr1[k + (PITCH_LAG_SPAN2+4)]) && (corr > CorrSurfPtr1[k + (PITCH_LAG_SPAN2+5)]) ) {
            /* found a peak; store index into matrix */
            peaks[peaks_ind++] = (int)(&CorrSurfPtr1[k] - &CorrSurf[0][0]);
            if (peaks_ind == PITCH_MAX_NUM_PEAKS) break;
          }
        }
      }
    }
  }

  if (peaks_ind > 0) {
    /* examine each peak */
    CorrSurfPtr1 = &CorrSurf[0][0];
    for (k = 0; k < peaks_ind; k++) {
      peak = peaks[k];

      /* compute four interpolated values around current peak */
      IntrepolFilter(&CorrSurfPtr1[peak - (PITCH_LAG_SPAN2+5)], &intrp_a);
      IntrepolFilter(&CorrSurfPtr1[peak - 1            ], &intrp_b);
      IntrepolFilter(&CorrSurfPtr1[peak                ], &intrp_c);
      IntrepolFilter(&CorrSurfPtr1[peak + (PITCH_LAG_SPAN2+4)], &intrp_d);

      /* determine maximum of the interpolated values */
      corr = CorrSurfPtr1[peak];
      corr_max = intrp_a;
      if (intrp_b > corr_max) corr_max = intrp_b;
      if (intrp_c > corr_max) corr_max = intrp_c;
      if (intrp_d > corr_max) corr_max = intrp_d;

      /* determine where the peak sits and fill a 3x3 matrix around it */
      row = peak / (PITCH_LAG_SPAN2+4);
      lags1[k] = (double) ((peak - row * (PITCH_LAG_SPAN2+4)) + PITCH_MIN_LAG/2 - 4);
      lags2[k] = (double) (lags1[k] + PITCH_BW - row);
      if ( corr > corr_max ) {
        T[0][0] = CorrSurfPtr1[peak - (PITCH_LAG_SPAN2+5)];
        T[2][0] = CorrSurfPtr1[peak - (PITCH_LAG_SPAN2+4)];
        T[1][1] = corr;
        T[0][2] = CorrSurfPtr1[peak + (PITCH_LAG_SPAN2+4)];
        T[2][2] = CorrSurfPtr1[peak + (PITCH_LAG_SPAN2+5)];
        T[1][0] = intrp_a;
        T[0][1] = intrp_b;
        T[2][1] = intrp_c;
        T[1][2] = intrp_d;
      } else {
        if (intrp_a == corr_max) {
          lags1[k] -= 0.5;
          lags2[k] += 0.5;
          IntrepolFilter(&CorrSurfPtr1[peak - 2*(PITCH_LAG_SPAN2+5)], &T[0][0]);
          IntrepolFilter(&CorrSurfPtr1[peak - (2*PITCH_LAG_SPAN2+9)], &T[2][0]);
          T[1][1] = intrp_a;
          T[0][2] = intrp_b;
          T[2][2] = intrp_c;
          T[1][0] = CorrSurfPtr1[peak - (2*PITCH_LAG_SPAN2+9)];
          T[0][1] = CorrSurfPtr1[peak - (PITCH_LAG_SPAN2+5)];
          T[2][1] = CorrSurfPtr1[peak - (PITCH_LAG_SPAN2+4)];
          T[1][2] = corr;
        } else if (intrp_b == corr_max) {
          lags1[k] -= 0.5;
          lags2[k] -= 0.5;
          IntrepolFilter(&CorrSurfPtr1[peak - (PITCH_LAG_SPAN2+6)], &T[0][0]);
          T[2][0] = intrp_a;
          T[1][1] = intrp_b;
          IntrepolFilter(&CorrSurfPtr1[peak + (PITCH_LAG_SPAN2+3)], &T[0][2]);
          T[2][2] = intrp_d;
          T[1][0] = CorrSurfPtr1[peak - (PITCH_LAG_SPAN2+5)];
          T[0][1] = CorrSurfPtr1[peak - 1];
          T[2][1] = corr;
          T[1][2] = CorrSurfPtr1[peak + (PITCH_LAG_SPAN2+4)];
        } else if (intrp_c == corr_max) {
          lags1[k] += 0.5;
          lags2[k] += 0.5;
          T[0][0] = intrp_a;
          IntrepolFilter(&CorrSurfPtr1[peak - (PITCH_LAG_SPAN2+4)], &T[2][0]);
          T[1][1] = intrp_c;
          T[0][2] = intrp_d;
          IntrepolFilter(&CorrSurfPtr1[peak + (PITCH_LAG_SPAN2+5)], &T[2][2]);
          T[1][0] = CorrSurfPtr1[peak - (PITCH_LAG_SPAN2+4)];
          T[0][1] = corr;
          T[2][1] = CorrSurfPtr1[peak + 1];
          T[1][2] = CorrSurfPtr1[peak + (PITCH_LAG_SPAN2+5)];
        } else {
          lags1[k] += 0.5;
          lags2[k] -= 0.5;
          T[0][0] = intrp_b;
          T[2][0] = intrp_c;
          T[1][1] = intrp_d;
          IntrepolFilter(&CorrSurfPtr1[peak + 2*(PITCH_LAG_SPAN2+4)], &T[0][2]);
          IntrepolFilter(&CorrSurfPtr1[peak + (2*PITCH_LAG_SPAN2+9)], &T[2][2]);
          T[1][0] = corr;
          T[0][1] = CorrSurfPtr1[peak + (PITCH_LAG_SPAN2+4)];
          T[2][1] = CorrSurfPtr1[peak + (PITCH_LAG_SPAN2+5)];
          T[1][2] = CorrSurfPtr1[peak + (2*PITCH_LAG_SPAN2+9)];
        }
      }

      /* 2D parabolic interpolation gives more accurate lags and peak value */
      Intrpol2D(T, &lags1[k], &lags2[k], &peak_vals[k]);
    }

    /* determine the highest peak, after applying a bias towards short lags */
    corr_max = 0.0;
    for (k = 0; k < peaks_ind; k++) {
      corr = peak_vals[k] * pow(PITCH_PEAK_DECAY, log(lags1[k] + lags2[k]));
      if (corr > corr_max) {
        corr_max = corr;
        peak = k;
      }
    }

    lags1[peak] *= 2.0;
    lags2[peak] *= 2.0;

    if (lags1[peak] < (double) PITCH_MIN_LAG) lags1[peak] = (double) PITCH_MIN_LAG;
    if (lags2[peak] < (double) PITCH_MIN_LAG) lags2[peak] = (double) PITCH_MIN_LAG;
    if (lags1[peak] > (double) PITCH_MAX_LAG) lags1[peak] = (double) PITCH_MAX_LAG;
    if (lags2[peak] > (double) PITCH_MAX_LAG) lags2[peak] = (double) PITCH_MAX_LAG;

    /* store lags of highest peak in output array */
    lags[0] = lags1[peak];
    lags[1] = lags1[peak];
    lags[2] = lags2[peak];
    lags[3] = lags2[peak];
  }
  else
  {
    row = max_ind / (PITCH_LAG_SPAN2+4);
    lags1[0] = (double) ((max_ind - row * (PITCH_LAG_SPAN2+4)) + PITCH_MIN_LAG/2 - 4);
    lags2[0] = (double) (lags1[0] + PITCH_BW - row);

    if (lags1[0] < (double) PITCH_MIN_LAG) lags1[0] = (double) PITCH_MIN_LAG;
    if (lags2[0] < (double) PITCH_MIN_LAG) lags2[0] = (double) PITCH_MIN_LAG;
    if (lags1[0] > (double) PITCH_MAX_LAG) lags1[0] = (double) PITCH_MAX_LAG;
    if (lags2[0] > (double) PITCH_MAX_LAG) lags2[0] = (double) PITCH_MAX_LAG;

    /* store lags of highest peak in output array */
    lags[0] = lags1[0];
    lags[1] = lags1[0];
    lags[2] = lags2[0];
    lags[3] = lags2[0];
  }
}

RTC_POP_IGNORING_WFRAME_LARGER_THAN()

/* create weighting matrix by orthogonalizing a basis of polynomials of increasing order
 * t = (0:4)';
 * A = [t.^0, t.^1, t.^2, t.^3, t.^4];
 * [Q, dummy] = qr(A);
 * P.Weight = Q * diag([0, .1, .5, 1, 1]) * Q'; */
static const double kWeight[5][5] = {
  { 0.29714285714286,  -0.30857142857143,  -0.05714285714286,   0.05142857142857,  0.01714285714286},
  {-0.30857142857143,   0.67428571428571,  -0.27142857142857,  -0.14571428571429,  0.05142857142857},
  {-0.05714285714286,  -0.27142857142857,   0.65714285714286,  -0.27142857142857, -0.05714285714286},
  { 0.05142857142857,  -0.14571428571429,  -0.27142857142857,   0.67428571428571, -0.30857142857143},
  { 0.01714285714286,   0.05142857142857,  -0.05714285714286,  -0.30857142857143,  0.29714285714286}
};

/* second order high-pass filter */
static void WebRtcIsac_Highpass(const double* in,
                         double* out,
                         double* state,
                         size_t N) {
  /* create high-pass filter ocefficients
   * z = 0.998 * exp(j*2*pi*35/8000);
   * p = 0.94 * exp(j*2*pi*140/8000);
   * HP_b = [1, -2*real(z), abs(z)^2];
   * HP_a = [1, -2*real(p), abs(p)^2]; */
  static const double a_coef[2] = { 1.86864659625574, -0.88360000000000};
  static const double b_coef[2] = {-1.99524591718270,  0.99600400000000};

  size_t k;

  for (k=0; k<N; k++) {
    *out = *in + state[1];
    state[1] = state[0] + b_coef[0] * *in + a_coef[0] * *out;
    state[0] = b_coef[1] * *in++ + a_coef[1] * *out++;
  }
}

RTC_PUSH_IGNORING_WFRAME_LARGER_THAN()

void WebRtcIsac_PitchAnalysis(const double *in,               /* PITCH_FRAME_LEN samples */
                              double *out,                    /* PITCH_FRAME_LEN+QLOOKAHEAD samples */
                              PitchAnalysisStruct *State,
                              double *lags,
                              double *gains)
{
  double HPin[PITCH_FRAME_LEN];
  double Weighted[PITCH_FRAME_LEN];
  double Whitened[PITCH_FRAME_LEN + QLOOKAHEAD];
  double inbuf[PITCH_FRAME_LEN + QLOOKAHEAD];
  double out_G[PITCH_FRAME_LEN + QLOOKAHEAD];          // could be removed by using out instead
  double out_dG[4][PITCH_FRAME_LEN + QLOOKAHEAD];
  double old_lag, old_gain;
  double nrg_wht, tmp;
  double Wnrg, Wfluct, Wgain;
  double H[4][4];
  double grad[4];
  double dG[4];
  int k, m, n, iter;

  /* high pass filtering using second order pole-zero filter */
  WebRtcIsac_Highpass(in, HPin, State->hp_state, PITCH_FRAME_LEN);

  /* copy from state into buffer */
  memcpy(Whitened, State->whitened_buf, sizeof(double) * QLOOKAHEAD);

  /* compute weighted and whitened signals */
  WebRtcIsac_WeightingFilter(HPin, &Weighted[0], &Whitened[QLOOKAHEAD], &(State->Wghtstr));

  /* copy from buffer into state */
  memcpy(State->whitened_buf, Whitened+PITCH_FRAME_LEN, sizeof(double) * QLOOKAHEAD);

  old_lag = State->PFstr_wght.oldlagp[0];
  old_gain = State->PFstr_wght.oldgainp[0];

  /* inital pitch estimate */
  WebRtcIsac_InitializePitch(Weighted, old_lag, old_gain, State, lags);


  /* Iterative optimization of lags - to be done */

  /* compute energy of whitened signal */
  nrg_wht = 0.0;
  for (k = 0; k < PITCH_FRAME_LEN + QLOOKAHEAD; k++)
    nrg_wht += Whitened[k] * Whitened[k];


  /* Iterative optimization of gains */

  /* set weights for energy, gain fluctiation, and spectral gain penalty functions */
  Wnrg = 1.0 / nrg_wht;
  Wgain = 0.005;
  Wfluct = 3.0;

  /* set initial gains */
  for (k = 0; k < 4; k++)
    gains[k] = PITCH_MAX_GAIN_06;

  /* two iterations should be enough */
  for (iter = 0; iter < 2; iter++) {
    /* compute Jacobian of pre-filter output towards gains */
    WebRtcIsac_PitchfilterPre_gains(Whitened, out_G, out_dG, &(State->PFstr_wght), lags, gains);

    /* gradient and approximate Hessian (lower triangle) for minimizing the filter's output power */
    for (k = 0; k < 4; k++) {
      tmp = 0.0;
      for (n = 0; n < PITCH_FRAME_LEN + QLOOKAHEAD; n++)
        tmp += out_G[n] * out_dG[k][n];
      grad[k] = tmp * Wnrg;
    }
    for (k = 0; k < 4; k++) {
      for (m = 0; m <= k; m++) {
        tmp = 0.0;
        for (n = 0; n < PITCH_FRAME_LEN + QLOOKAHEAD; n++)
          tmp += out_dG[m][n] * out_dG[k][n];
        H[k][m] = tmp * Wnrg;
      }
    }

    /* add gradient and Hessian (lower triangle) for dampening fast gain changes */
    for (k = 0; k < 4; k++) {
      tmp = kWeight[k+1][0] * old_gain;
      for (m = 0; m < 4; m++)
        tmp += kWeight[k+1][m+1] * gains[m];
      grad[k] += tmp * Wfluct;
    }
    for (k = 0; k < 4; k++) {
      for (m = 0; m <= k; m++) {
        H[k][m] += kWeight[k+1][m+1] * Wfluct;
      }
    }

    /* add gradient and Hessian for dampening gain */
    for (k = 0; k < 3; k++) {
      tmp = 1.0 / (1 - gains[k]);
      grad[k] += tmp * tmp * Wgain;
      H[k][k] += 2.0 * tmp * (tmp * tmp * Wgain);
    }
    tmp = 1.0 / (1 - gains[3]);
    grad[3] += 1.33 * (tmp * tmp * Wgain);
    H[3][3] += 2.66 * tmp * (tmp * tmp * Wgain);


    /* compute Cholesky factorization of Hessian
     * by overwritting the upper triangle; scale factors on diagonal
     * (for non pc-platforms store the inverse of the diagonals seperately to minimize divisions) */
    H[0][1] = H[1][0] / H[0][0];
    H[0][2] = H[2][0] / H[0][0];
    H[0][3] = H[3][0] / H[0][0];
    H[1][1] -= H[0][0] * H[0][1] * H[0][1];
    H[1][2] = (H[2][1] - H[0][1] * H[2][0]) / H[1][1];
    H[1][3] = (H[3][1] - H[0][1] * H[3][0]) / H[1][1];
    H[2][2] -= H[0][0] * H[0][2] * H[0][2] + H[1][1] * H[1][2] * H[1][2];
    H[2][3] = (H[3][2] - H[0][2] * H[3][0] - H[1][2] * H[1][1] * H[1][3]) / H[2][2];
    H[3][3] -= H[0][0] * H[0][3] * H[0][3] + H[1][1] * H[1][3] * H[1][3] + H[2][2] * H[2][3] * H[2][3];

    /* Compute update as  delta_gains = -inv(H) * grad */
    /* copy and negate */
    for (k = 0; k < 4; k++)
      dG[k] = -grad[k];
    /* back substitution */
    dG[1] -= dG[0] * H[0][1];
    dG[2] -= dG[0] * H[0][2] + dG[1] * H[1][2];
    dG[3] -= dG[0] * H[0][3] + dG[1] * H[1][3] + dG[2] * H[2][3];
    /* scale */
    for (k = 0; k < 4; k++)
      dG[k] /= H[k][k];
    /* back substitution */
    dG[2] -= dG[3] * H[2][3];
    dG[1] -= dG[3] * H[1][3] + dG[2] * H[1][2];
    dG[0] -= dG[3] * H[0][3] + dG[2] * H[0][2] + dG[1] * H[0][1];

    /* update gains and check range */
    for (k = 0; k < 4; k++) {
      gains[k] += dG[k];
      if (gains[k] > PITCH_MAX_GAIN)
        gains[k] = PITCH_MAX_GAIN;
      else if (gains[k] < 0.0)
        gains[k] = 0.0;
    }
  }

  /* update state for next frame */
  WebRtcIsac_PitchfilterPre(Whitened, out, &(State->PFstr_wght), lags, gains);

  /* concatenate previous input's end and current input */
  memcpy(inbuf, State->inbuf, sizeof(double) * QLOOKAHEAD);
  memcpy(inbuf+QLOOKAHEAD, in, sizeof(double) * PITCH_FRAME_LEN);

  /* lookahead pitch filtering for masking analysis */
  WebRtcIsac_PitchfilterPre_la(inbuf, out, &(State->PFstr), lags, gains);

  /* store last part of input */
  for (k = 0; k < QLOOKAHEAD; k++)
    State->inbuf[k] = inbuf[k + PITCH_FRAME_LEN];
}

RTC_POP_IGNORING_WFRAME_LARGER_THAN()
