/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Contains a function for WebRtcIsacfix_AllpassFilter2FixDec16Neon()
// in iSAC codec, optimized for ARM Neon platform. Bit exact with function
// WebRtcIsacfix_AllpassFilter2FixDec16C() in filterbanks.c. Prototype
// C code is at end of this file.

#include <arm_neon.h>

#include "modules/audio_coding/codecs/isac/fix/source/filterbank_internal.h"
#include "rtc_base/checks.h"

void WebRtcIsacfix_AllpassFilter2FixDec16Neon(
    int16_t* data_ch1,  // Input and output in channel 1, in Q0
    int16_t* data_ch2,  // Input and output in channel 2, in Q0
    const int16_t* factor_ch1,  // Scaling factor for channel 1, in Q15
    const int16_t* factor_ch2,  // Scaling factor for channel 2, in Q15
    const int length,  // Length of the data buffers
    int32_t* filter_state_ch1,  // Filter state for channel 1, in Q16
    int32_t* filter_state_ch2) {  // Filter state for channel 2, in Q16
  RTC_DCHECK_EQ(0, length % 2);
  int n = 0;
  int16x4_t factorv;
  int16x4_t datav;
  int32x4_t statev;

  // Load factor_ch1 and factor_ch2.
  factorv = vld1_dup_s16(factor_ch1);
  factorv = vld1_lane_s16(factor_ch1 + 1, factorv, 1);
  factorv = vld1_lane_s16(factor_ch2, factorv, 2);
  factorv = vld1_lane_s16(factor_ch2 + 1, factorv, 3);

  // Load filter_state_ch1[0] and filter_state_ch2[0].
  statev = vld1q_dup_s32(filter_state_ch1);
  statev = vld1q_lane_s32(filter_state_ch2, statev, 2);

  // Loop unrolling preprocessing.
  int32x4_t a;
  int16x4_t tmp1, tmp2;

  // Load data_ch1[0] and data_ch2[0].
  datav = vld1_dup_s16(data_ch1);
  datav = vld1_lane_s16(data_ch2, datav, 2);

  a = vqdmlal_s16(statev, datav, factorv);
  tmp1 = vshrn_n_s32(a, 16);

  // Update filter_state_ch1[0] and filter_state_ch2[0].
  statev = vqdmlsl_s16(vshll_n_s16(datav, 16), tmp1, factorv);

  // Load filter_state_ch1[1] and filter_state_ch2[1].
  statev = vld1q_lane_s32(filter_state_ch1 + 1, statev, 1);
  statev = vld1q_lane_s32(filter_state_ch2 + 1, statev, 3);

  // Load data_ch1[1] and data_ch2[1].
  tmp1 = vld1_lane_s16(data_ch1 + 1, tmp1, 1);
  tmp1 = vld1_lane_s16(data_ch2 + 1, tmp1, 3);
  datav = vrev32_s16(tmp1);

  // Loop unrolling processing.
  for (n = 0; n < length - 2; n += 2) {
    a = vqdmlal_s16(statev, datav, factorv);
    tmp1 = vshrn_n_s32(a, 16);
    // Store data_ch1[n] and data_ch2[n].
    vst1_lane_s16(data_ch1 + n, tmp1, 1);
    vst1_lane_s16(data_ch2 + n, tmp1, 3);

    // Update filter_state_ch1[0], filter_state_ch1[1]
    // and filter_state_ch2[0], filter_state_ch2[1].
    statev = vqdmlsl_s16(vshll_n_s16(datav, 16), tmp1, factorv);

    // Load data_ch1[n + 2] and data_ch2[n + 2].
    tmp1 = vld1_lane_s16(data_ch1 + n + 2, tmp1, 1);
    tmp1 = vld1_lane_s16(data_ch2 + n + 2, tmp1, 3);
    datav = vrev32_s16(tmp1);

    a = vqdmlal_s16(statev, datav, factorv);
    tmp2 = vshrn_n_s32(a, 16);
    // Store data_ch1[n + 1] and data_ch2[n + 1].
    vst1_lane_s16(data_ch1 + n + 1, tmp2, 1);
    vst1_lane_s16(data_ch2 + n + 1, tmp2, 3);

    // Update filter_state_ch1[0], filter_state_ch1[1]
    // and filter_state_ch2[0], filter_state_ch2[1].
    statev = vqdmlsl_s16(vshll_n_s16(datav, 16), tmp2, factorv);

    // Load data_ch1[n + 3] and data_ch2[n + 3].
    tmp2 = vld1_lane_s16(data_ch1 + n + 3, tmp2, 1);
    tmp2 = vld1_lane_s16(data_ch2 + n + 3, tmp2, 3);
    datav = vrev32_s16(tmp2);
  }

  // Loop unrolling post-processing.
  a = vqdmlal_s16(statev, datav, factorv);
  tmp1 = vshrn_n_s32(a, 16);
  // Store data_ch1[n] and data_ch2[n].
  vst1_lane_s16(data_ch1 + n, tmp1, 1);
  vst1_lane_s16(data_ch2 + n, tmp1, 3);

  // Update filter_state_ch1[0], filter_state_ch1[1]
  // and filter_state_ch2[0], filter_state_ch2[1].
  statev = vqdmlsl_s16(vshll_n_s16(datav, 16), tmp1, factorv);
  // Store filter_state_ch1[0] and filter_state_ch2[0].
  vst1q_lane_s32(filter_state_ch1, statev, 0);
  vst1q_lane_s32(filter_state_ch2, statev, 2);

  datav = vrev32_s16(tmp1);
  a = vqdmlal_s16(statev, datav, factorv);
  tmp2 = vshrn_n_s32(a, 16);
  // Store data_ch1[n + 1] and data_ch2[n + 1].
  vst1_lane_s16(data_ch1 + n + 1, tmp2, 1);
  vst1_lane_s16(data_ch2 + n + 1, tmp2, 3);

  // Update filter_state_ch1[1] and filter_state_ch2[1].
  statev = vqdmlsl_s16(vshll_n_s16(datav, 16), tmp2, factorv);
  // Store filter_state_ch1[1] and filter_state_ch2[1].
  vst1q_lane_s32(filter_state_ch1 + 1, statev, 1);
  vst1q_lane_s32(filter_state_ch2 + 1, statev, 3);
}

// This function is the prototype for above neon optimized function.
//void AllpassFilter2FixDec16BothChannels(
//    int16_t *data_ch1,  // Input and output in channel 1, in Q0
//    int16_t *data_ch2,  // Input and output in channel 2, in Q0
//    const int16_t *factor_ch1,  // Scaling factor for channel 1, in Q15
//    const int16_t *factor_ch2,  // Scaling factor for channel 2, in Q15
//    const int length,  // Length of the data buffers
//    int32_t *filter_state_ch1,  // Filter state for channel 1, in Q16
//    int32_t *filter_state_ch2) {  // Filter state for channel 2, in Q16
//  int n = 0;
//  int32_t state0_ch1 = filter_state_ch1[0], state1_ch1 = filter_state_ch1[1];
//  int32_t state0_ch2 = filter_state_ch2[0], state1_ch2 = filter_state_ch2[1];
//  int16_t sample0_ch1 = 0, sample0_ch2 = 0;
//  int16_t sample1_ch1 = 0, sample1_ch2  = 0;
//  int32_t a0_ch1 = 0, a0_ch2 = 0;
//  int32_t b0_ch1 = 0, b0_ch2 = 0;
//
//  int32_t a1_ch1 = 0, a1_ch2 = 0;
//  int32_t b1_ch1 = 0, b1_ch2 = 0;
//  int32_t b2_ch1  = 0, b2_ch2 = 0;
//
//  // Loop unrolling preprocessing.
//
//  sample0_ch1 = data_ch1[n];
//  sample0_ch2 = data_ch2[n];
//
//  a0_ch1 = (factor_ch1[0] * sample0_ch1) << 1;
//  a0_ch2 = (factor_ch2[0] * sample0_ch2) << 1;
//
//  b0_ch1 = WebRtcSpl_AddSatW32(a0_ch1, state0_ch1);
//  b0_ch2 = WebRtcSpl_AddSatW32(a0_ch2, state0_ch2); //Q16+Q16=Q16
//
//  a0_ch1 = -factor_ch1[0] * (int16_t)(b0_ch1 >> 16);
//  a0_ch2 = -factor_ch2[0] * (int16_t)(b0_ch2 >> 16);
//
//  state0_ch1 = WebRtcSpl_AddSatW32(a0_ch1 <<1, (uint32_t)sample0_ch1 << 16);
//  state0_ch2 = WebRtcSpl_AddSatW32(a0_ch2 <<1, (uint32_t)sample0_ch2 << 16);
//
//  sample1_ch1 = data_ch1[n + 1];
//  sample0_ch1 = (int16_t) (b0_ch1 >> 16); //Save as Q0
//  sample1_ch2  = data_ch2[n + 1];
//  sample0_ch2 = (int16_t) (b0_ch2 >> 16); //Save as Q0
//
//
//  for (n = 0; n < length - 2; n += 2) {
//    a1_ch1 = (factor_ch1[0] * sample1_ch1) << 1;
//    a0_ch1 = (factor_ch1[1] * sample0_ch1) << 1;
//    a1_ch2 = (factor_ch2[0] * sample1_ch2) << 1;
//    a0_ch2 = (factor_ch2[1] * sample0_ch2) << 1;
//
//    b1_ch1 = WebRtcSpl_AddSatW32(a1_ch1, state0_ch1);
//    b0_ch1 = WebRtcSpl_AddSatW32(a0_ch1, state1_ch1); //Q16+Q16=Q16
//    b1_ch2 = WebRtcSpl_AddSatW32(a1_ch2, state0_ch2); //Q16+Q16=Q16
//    b0_ch2 = WebRtcSpl_AddSatW32(a0_ch2, state1_ch2); //Q16+Q16=Q16
//
//    a1_ch1 = -factor_ch1[0] * (int16_t)(b1_ch1 >> 16);
//    a0_ch1 = -factor_ch1[1] * (int16_t)(b0_ch1 >> 16);
//    a1_ch2 = -factor_ch2[0] * (int16_t)(b1_ch2 >> 16);
//    a0_ch2 = -factor_ch2[1] * (int16_t)(b0_ch2 >> 16);
//
//    state0_ch1 = WebRtcSpl_AddSatW32(a1_ch1<<1, (uint32_t)sample1_ch1 <<16);
//    state1_ch1 = WebRtcSpl_AddSatW32(a0_ch1<<1, (uint32_t)sample0_ch1 <<16);
//    state0_ch2 = WebRtcSpl_AddSatW32(a1_ch2<<1, (uint32_t)sample1_ch2 <<16);
//    state1_ch2 = WebRtcSpl_AddSatW32(a0_ch2<<1, (uint32_t)sample0_ch2 <<16);
//
//    sample0_ch1 = data_ch1[n + 2];
//    sample1_ch1 = (int16_t) (b1_ch1 >> 16); //Save as Q0
//    sample0_ch2 = data_ch2[n + 2];
//    sample1_ch2  = (int16_t) (b1_ch2 >> 16); //Save as Q0
//
//    a0_ch1 = (factor_ch1[0] * sample0_ch1) << 1;
//    a1_ch1 = (factor_ch1[1] * sample1_ch1) << 1;
//    a0_ch2 = (factor_ch2[0] * sample0_ch2) << 1;
//    a1_ch2 = (factor_ch2[1] * sample1_ch2) << 1;
//
//    b2_ch1 = WebRtcSpl_AddSatW32(a0_ch1, state0_ch1);
//    b1_ch1 = WebRtcSpl_AddSatW32(a1_ch1, state1_ch1); //Q16+Q16=Q16
//    b2_ch2 = WebRtcSpl_AddSatW32(a0_ch2, state0_ch2); //Q16+Q16=Q16
//    b1_ch2 = WebRtcSpl_AddSatW32(a1_ch2, state1_ch2); //Q16+Q16=Q16
//
//    a0_ch1 = -factor_ch1[0] * (int16_t)(b2_ch1 >> 16);
//    a1_ch1 = -factor_ch1[1] * (int16_t)(b1_ch1 >> 16);
//    a0_ch2 = -factor_ch2[0] * (int16_t)(b2_ch2 >> 16);
//    a1_ch2 = -factor_ch2[1] * (int16_t)(b1_ch2 >> 16);
//
//    state0_ch1 = WebRtcSpl_AddSatW32(a0_ch1<<1, (uint32_t)sample0_ch1<<16);
//    state1_ch1 = WebRtcSpl_AddSatW32(a1_ch1<<1, (uint32_t)sample1_ch1<<16);
//    state0_ch2 = WebRtcSpl_AddSatW32(a0_ch2<<1, (uint32_t)sample0_ch2<<16);
//    state1_ch2 = WebRtcSpl_AddSatW32(a1_ch2<<1, (uint32_t)sample1_ch2<<16);
//
//
//    sample1_ch1 = data_ch1[n + 3];
//    sample0_ch1 = (int16_t) (b2_ch1  >> 16); //Save as Q0
//    sample1_ch2 = data_ch2[n + 3];
//    sample0_ch2 = (int16_t) (b2_ch2 >> 16); //Save as Q0
//
//    data_ch1[n]     = (int16_t) (b0_ch1 >> 16); //Save as Q0
//    data_ch1[n + 1] = (int16_t) (b1_ch1 >> 16); //Save as Q0
//    data_ch2[n]     = (int16_t) (b0_ch2 >> 16);
//    data_ch2[n + 1] = (int16_t) (b1_ch2 >> 16);
//  }
//
//  // Loop unrolling post-processing.
//
//  a1_ch1 = (factor_ch1[0] * sample1_ch1) << 1;
//  a0_ch1 = (factor_ch1[1] * sample0_ch1) << 1;
//  a1_ch2 = (factor_ch2[0] * sample1_ch2) << 1;
//  a0_ch2 = (factor_ch2[1] * sample0_ch2) << 1;
//
//  b1_ch1 = WebRtcSpl_AddSatW32(a1_ch1, state0_ch1);
//  b0_ch1 = WebRtcSpl_AddSatW32(a0_ch1, state1_ch1);
//  b1_ch2 = WebRtcSpl_AddSatW32(a1_ch2, state0_ch2);
//  b0_ch2 = WebRtcSpl_AddSatW32(a0_ch2, state1_ch2);
//
//  a1_ch1 = -factor_ch1[0] * (int16_t)(b1_ch1 >> 16);
//  a0_ch1 = -factor_ch1[1] * (int16_t)(b0_ch1 >> 16);
//  a1_ch2 = -factor_ch2[0] * (int16_t)(b1_ch2 >> 16);
//  a0_ch2 = -factor_ch2[1] * (int16_t)(b0_ch2 >> 16);
//
//  state0_ch1 = WebRtcSpl_AddSatW32(a1_ch1<<1, (uint32_t)sample1_ch1 << 16);
//  state1_ch1 = WebRtcSpl_AddSatW32(a0_ch1<<1, (uint32_t)sample0_ch1 << 16);
//  state0_ch2 = WebRtcSpl_AddSatW32(a1_ch2<<1, (uint32_t)sample1_ch2 << 16);
//  state1_ch2 = WebRtcSpl_AddSatW32(a0_ch2<<1, (uint32_t)sample0_ch2 << 16);
//
//  data_ch1[n] = (int16_t) (b0_ch1 >> 16); //Save as Q0
//  data_ch2[n] = (int16_t) (b0_ch2 >> 16);
//
//  sample1_ch1 = (int16_t) (b1_ch1 >> 16); //Save as Q0
//  sample1_ch2  = (int16_t) (b1_ch2 >> 16); //Save as Q0
//
//  a1_ch1 = (factor_ch1[1] * sample1_ch1) << 1;
//  a1_ch2 = (factor_ch2[1] * sample1_ch2) << 1;
//
//  b1_ch1 = WebRtcSpl_AddSatW32(a1_ch1, state1_ch1); //Q16+Q16=Q16
//  b1_ch2 = WebRtcSpl_AddSatW32(a1_ch2, state1_ch2); //Q16+Q16=Q16
//
//  a1_ch1 = -factor_ch1[1] * (int16_t)(b1_ch1 >> 16);
//  a1_ch2 = -factor_ch2[1] * (int16_t)(b1_ch2 >> 16);
//
//  state1_ch1 = WebRtcSpl_AddSatW32(a1_ch1<<1, (uint32_t)sample1_ch1<<16);
//  state1_ch2 = WebRtcSpl_AddSatW32(a1_ch2<<1, (uint32_t)sample1_ch2<<16);
//
//  data_ch1[n + 1] = (int16_t) (b1_ch1 >> 16); //Save as Q0
//  data_ch2[n + 1] = (int16_t) (b1_ch2 >> 16);
//
//  filter_state_ch1[0] = state0_ch1;
//  filter_state_ch1[1] = state1_ch1;
//  filter_state_ch2[0] = state0_ch2;
//  filter_state_ch2[1] = state1_ch2;
//}
