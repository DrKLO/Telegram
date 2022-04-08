/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/checks.h"
#include "modules/audio_coding/codecs/isac/fix/source/codec.h"

// Autocorrelation function in fixed point.
// NOTE! Different from SPLIB-version in how it scales the signal.
int WebRtcIsacfix_AutocorrC(int32_t* __restrict r,
                            const int16_t* __restrict x,
                            int16_t N,
                            int16_t order,
                            int16_t* __restrict scale) {
  int i = 0;
  int j = 0;
  int16_t scaling = 0;
  int32_t sum = 0;
  uint32_t temp = 0;
  int64_t prod = 0;

  // The ARM assembly code assumptoins.
  RTC_DCHECK_EQ(0, N % 4);
  RTC_DCHECK_GE(N, 8);

  // Calculate r[0].
  for (i = 0; i < N; i++) {
    prod += x[i] * x[i];
  }

  // Calculate scaling (the value of shifting).
  temp = (uint32_t)(prod >> 31);
  if(temp == 0) {
    scaling = 0;
  } else {
    scaling = 32 - WebRtcSpl_NormU32(temp);
  }
  r[0] = (int32_t)(prod >> scaling);

  // Perform the actual correlation calculation.
  for (i = 1; i < order + 1; i++) {
    prod = 0;
    for (j = 0; j < N - i; j++) {
      prod += x[j] * x[i + j];
    }
    sum = (int32_t)(prod >> scaling);
    r[i] = sum;
  }

  *scale = scaling;

  return(order + 1);
}

static const int32_t kApUpperQ15[ALLPASSSECTIONS] = { 1137, 12537 };
static const int32_t kApLowerQ15[ALLPASSSECTIONS] = { 5059, 24379 };


static void AllpassFilterForDec32(int16_t         *InOut16, //Q0
                                  const int32_t   *APSectionFactors, //Q15
                                  int16_t         lengthInOut,
                                  int32_t          *FilterState) //Q16
{
  int n, j;
  int32_t a, b;

  for (j=0; j<ALLPASSSECTIONS; j++) {
    for (n=0;n<lengthInOut;n+=2){
      a = WEBRTC_SPL_MUL_16_32_RSFT16(InOut16[n], APSectionFactors[j]); //Q0*Q31=Q31 shifted 16 gives Q15
      a <<= 1;  // Q15 -> Q16
      b = WebRtcSpl_AddSatW32(a, FilterState[j]);  //Q16+Q16=Q16
      // `a` in Q15 (Q0*Q31=Q31 shifted 16 gives Q15).
      a = WEBRTC_SPL_MUL_16_32_RSFT16(b >> 16, -APSectionFactors[j]);
      // FilterState[j]: Q15<<1 + Q0<<16 = Q16 + Q16 = Q16
      FilterState[j] = WebRtcSpl_AddSatW32(a << 1, (uint32_t)InOut16[n] << 16);
      InOut16[n] = (int16_t)(b >> 16);  // Save as Q0.
    }
  }
}




void WebRtcIsacfix_DecimateAllpass32(const int16_t *in,
                                     int32_t *state_in,        /* array of size: 2*ALLPASSSECTIONS+1 */
                                     int16_t N,                /* number of input samples */
                                     int16_t *out)             /* array of size N/2 */
{
  int n;
  int16_t data_vec[PITCH_FRAME_LEN];

  /* copy input */
  memcpy(data_vec + 1, in, sizeof(int16_t) * (N - 1));

  data_vec[0] = (int16_t)(state_in[2 * ALLPASSSECTIONS] >> 16);  // z^-1 state.
  state_in[2 * ALLPASSSECTIONS] = (uint32_t)in[N - 1] << 16;



  AllpassFilterForDec32(data_vec+1, kApUpperQ15, N, state_in);
  AllpassFilterForDec32(data_vec, kApLowerQ15, N, state_in+ALLPASSSECTIONS);

  for (n=0;n<N/2;n++) {
    out[n] = WebRtcSpl_AddSatW16(data_vec[2 * n], data_vec[2 * n + 1]);
  }
}
