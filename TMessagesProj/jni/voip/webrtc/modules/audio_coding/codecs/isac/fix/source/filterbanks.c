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
 * filterbanks.c
 *
 * This file contains function 
 * WebRtcIsacfix_SplitAndFilter, and WebRtcIsacfix_FilterAndCombine
 * which implement filterbanks that produce decimated lowpass and
 * highpass versions of a signal, and performs reconstruction.
 *
 */

#include "modules/audio_coding/codecs/isac/fix/source/filterbank_internal.h"

#include "modules/audio_coding/codecs/isac/fix/source/codec.h"
#include "modules/audio_coding/codecs/isac/fix/source/filterbank_tables.h"
#include "modules/audio_coding/codecs/isac/fix/source/settings.h"
#include "rtc_base/checks.h"

// Declare a function pointer.
AllpassFilter2FixDec16 WebRtcIsacfix_AllpassFilter2FixDec16;

void WebRtcIsacfix_AllpassFilter2FixDec16C(
    int16_t *data_ch1,  // Input and output in channel 1, in Q0
    int16_t *data_ch2,  // Input and output in channel 2, in Q0
    const int16_t *factor_ch1,  // Scaling factor for channel 1, in Q15
    const int16_t *factor_ch2,  // Scaling factor for channel 2, in Q15
    const int length,  // Length of the data buffers
    int32_t *filter_state_ch1,  // Filter state for channel 1, in Q16
    int32_t *filter_state_ch2) {  // Filter state for channel 2, in Q16
  int n = 0;
  int32_t state0_ch1 = filter_state_ch1[0], state1_ch1 = filter_state_ch1[1];
  int32_t state0_ch2 = filter_state_ch2[0], state1_ch2 = filter_state_ch2[1];
  int16_t in_out = 0;
  int32_t a = 0, b = 0;

  // Assembly file assumption.
  RTC_DCHECK_EQ(0, length % 2);

  for (n = 0; n < length; n++) {
    // Process channel 1:
    in_out = data_ch1[n];
    a = factor_ch1[0] * in_out;  // Q15 * Q0 = Q15
    a *= 1 << 1;  // Q15 -> Q16
    b = WebRtcSpl_AddSatW32(a, state0_ch1);
    a = -factor_ch1[0] * (int16_t)(b >> 16);  // Q15
    state0_ch1 =
        WebRtcSpl_AddSatW32(a * (1 << 1), (int32_t)in_out * (1 << 16));  // Q16
    in_out = (int16_t) (b >> 16);  // Save as Q0

    a = factor_ch1[1] * in_out;  // Q15 * Q0 = Q15
    a *= 1 << 1; // Q15 -> Q16
    b = WebRtcSpl_AddSatW32(a, state1_ch1);  // Q16
    a = -factor_ch1[1] * (int16_t)(b >> 16);  // Q15
    state1_ch1 =
        WebRtcSpl_AddSatW32(a * (1 << 1), (int32_t)in_out * (1 << 16));  // Q16
    data_ch1[n] = (int16_t) (b >> 16);  // Save as Q0

    // Process channel 2:
    in_out = data_ch2[n];
    a = factor_ch2[0] * in_out;  // Q15 * Q0 = Q15
    a *= 1 << 1;  // Q15 -> Q16
    b = WebRtcSpl_AddSatW32(a, state0_ch2);  // Q16
    a = -factor_ch2[0] * (int16_t)(b >> 16);  // Q15
    state0_ch2 =
        WebRtcSpl_AddSatW32(a * (1 << 1), (int32_t)in_out * (1 << 16));  // Q16
    in_out = (int16_t) (b >> 16);  // Save as Q0

    a = factor_ch2[1] * in_out;  // Q15 * Q0 = Q15
    a *= (1 << 1);  // Q15 -> Q16
    b = WebRtcSpl_AddSatW32(a, state1_ch2);  // Q16
    a = -factor_ch2[1] * (int16_t)(b >> 16);  // Q15
    state1_ch2 =
        WebRtcSpl_AddSatW32(a * (1 << 1), (int32_t)in_out * (1 << 16));  // Q16
    data_ch2[n] = (int16_t) (b >> 16);  // Save as Q0
  }

  filter_state_ch1[0] = state0_ch1;
  filter_state_ch1[1] = state1_ch1;
  filter_state_ch2[0] = state0_ch2;
  filter_state_ch2[1] = state1_ch2;
}

// Declare a function pointer.
HighpassFilterFixDec32 WebRtcIsacfix_HighpassFilterFixDec32;

void WebRtcIsacfix_HighpassFilterFixDec32C(int16_t *io,
                                           int16_t len,
                                           const int16_t *coefficient,
                                           int32_t *state)
{
  int k;
  int32_t a1 = 0, b1 = 0, c = 0, in = 0;
  int32_t a2 = 0, b2 = 0;
  int32_t state0 = state[0];
  int32_t state1 = state[1];

  for (k=0; k<len; k++) {
    in = (int32_t)io[k];

#ifdef WEBRTC_ARCH_ARM_V7
    {
      register int tmp_coeff0;
      register int tmp_coeff1;
      __asm __volatile(
        "ldr %[tmp_coeff0], [%[coeff]]\n\t"
        "ldr %[tmp_coeff1], [%[coeff], #4]\n\t"
        "smmulr %[a2], %[tmp_coeff0], %[state0]\n\t"
        "smmulr %[b2], %[tmp_coeff1], %[state1]\n\t"
        "ldr %[tmp_coeff0], [%[coeff], #8]\n\t"
        "ldr %[tmp_coeff1], [%[coeff], #12]\n\t"
        "smmulr %[a1], %[tmp_coeff0], %[state0]\n\t"
        "smmulr %[b1], %[tmp_coeff1], %[state1]\n\t"
        :[a2]"=&r"(a2),
         [b2]"=&r"(b2),
         [a1]"=&r"(a1),
         [b1]"=r"(b1),
         [tmp_coeff0]"=&r"(tmp_coeff0),
         [tmp_coeff1]"=&r"(tmp_coeff1)
        :[coeff]"r"(coefficient),
         [state0]"r"(state0),
         [state1]"r"(state1)
      );
    }
#else
    /* Q35 * Q4 = Q39 ; shift 32 bit => Q7 */
    a1 = WEBRTC_SPL_MUL_16_32_RSFT16(coefficient[5], state0) +
        (WEBRTC_SPL_MUL_16_32_RSFT16(coefficient[4], state0) >> 16);
    b1 = WEBRTC_SPL_MUL_16_32_RSFT16(coefficient[7], state1) +
        (WEBRTC_SPL_MUL_16_32_RSFT16(coefficient[6], state1) >> 16);

    /* Q30 * Q4 = Q34 ; shift 32 bit => Q2 */
    a2 = WEBRTC_SPL_MUL_16_32_RSFT16(coefficient[1], state0) +
        (WEBRTC_SPL_MUL_16_32_RSFT16(coefficient[0], state0) >> 16);
    b2 = WEBRTC_SPL_MUL_16_32_RSFT16(coefficient[3], state1) +
        (WEBRTC_SPL_MUL_16_32_RSFT16(coefficient[2], state1) >> 16);
#endif

    c = in + ((a1 + b1) >> 7);  // Q0.
    io[k] = (int16_t)WebRtcSpl_SatW32ToW16(c);  // Write output as Q0.

    c = in * (1 << 2) - a2 - b2;  // In Q2.
    c = (int32_t)WEBRTC_SPL_SAT(536870911, c, -536870912);

    state1 = state0;
    state0 = c * (1 << 2);  // Write state as Q4
  }
  state[0] = state0;
  state[1] = state1;
}


void WebRtcIsacfix_SplitAndFilter1(int16_t *pin,
                                   int16_t *LP16,
                                   int16_t *HP16,
                                   PreFiltBankstr *prefiltdata)
{
  /* Function WebRtcIsacfix_SplitAndFilter */
  /* This function creates low-pass and high-pass decimated versions of part of
     the input signal, and part of the signal in the input 'lookahead buffer'. */

  int k;

  int16_t tempin_ch1[FRAMESAMPLES/2 + QLOOKAHEAD];
  int16_t tempin_ch2[FRAMESAMPLES/2 + QLOOKAHEAD];
  int32_t tmpState_ch1[2 * (QORDER-1)]; /* 4 */
  int32_t tmpState_ch2[2 * (QORDER-1)]; /* 4 */

  /* High pass filter */
  WebRtcIsacfix_HighpassFilterFixDec32(pin, FRAMESAMPLES, WebRtcIsacfix_kHpStCoeffInQ30, prefiltdata->HPstates_fix);


  /* First Channel */
  for (k=0;k<FRAMESAMPLES/2;k++) {
    tempin_ch1[QLOOKAHEAD + k] = pin[1 + 2 * k];
  }
  for (k=0;k<QLOOKAHEAD;k++) {
    tempin_ch1[k]=prefiltdata->INLABUF1_fix[k];
    prefiltdata->INLABUF1_fix[k] = pin[FRAMESAMPLES + 1 - 2 * (QLOOKAHEAD - k)];
  }

  /* Second Channel.  This is exactly like the first channel, except that the
     even samples are now filtered instead (lower channel). */
  for (k=0;k<FRAMESAMPLES/2;k++) {
    tempin_ch2[QLOOKAHEAD + k] = pin[2 * k];
  }
  for (k=0;k<QLOOKAHEAD;k++) {
    tempin_ch2[k]=prefiltdata->INLABUF2_fix[k];
    prefiltdata->INLABUF2_fix[k] = pin[FRAMESAMPLES - 2 * (QLOOKAHEAD - k)];
  }


  /*obtain polyphase components by forward all-pass filtering through each channel */
  /* The all pass filtering automatically updates the filter states which are exported in the
     prefiltdata structure */
  WebRtcIsacfix_AllpassFilter2FixDec16(tempin_ch1,
                                       tempin_ch2,
                                       WebRtcIsacfix_kUpperApFactorsQ15,
                                       WebRtcIsacfix_kLowerApFactorsQ15,
                                       FRAMESAMPLES/2,
                                       prefiltdata->INSTAT1_fix,
                                       prefiltdata->INSTAT2_fix);

  for (k = 0; k < 2 * (QORDER - 1); k++) {
    tmpState_ch1[k] = prefiltdata->INSTAT1_fix[k];
    tmpState_ch2[k] = prefiltdata->INSTAT2_fix[k];
  }
  WebRtcIsacfix_AllpassFilter2FixDec16(tempin_ch1 + FRAMESAMPLES/2,
                                       tempin_ch2 + FRAMESAMPLES/2,
                                       WebRtcIsacfix_kUpperApFactorsQ15,
                                       WebRtcIsacfix_kLowerApFactorsQ15,
                                       QLOOKAHEAD,
                                       tmpState_ch1,
                                       tmpState_ch2);

  /* Now Construct low-pass and high-pass signals as combinations of polyphase components */
  for (k=0; k<FRAMESAMPLES/2 + QLOOKAHEAD; k++) {
    int32_t tmp1, tmp2, tmp3;
    tmp1 = (int32_t)tempin_ch1[k]; // Q0 -> Q0
    tmp2 = (int32_t)tempin_ch2[k]; // Q0 -> Q0
    tmp3 = (tmp1 + tmp2) >> 1;  /* Low pass signal. */
    LP16[k] = (int16_t)WebRtcSpl_SatW32ToW16(tmp3); /*low pass */
    tmp3 = (tmp1 - tmp2) >> 1;  /* High pass signal. */
    HP16[k] = (int16_t)WebRtcSpl_SatW32ToW16(tmp3); /*high pass */
  }

}/*end of WebRtcIsacfix_SplitAndFilter */



//////////////////////////////////////////////////////////
////////// Combining
/* Function WebRtcIsacfix_FilterAndCombine */
/* This is a decoder function that takes the decimated
   length FRAMESAMPLES/2 input low-pass and
   high-pass signals and creates a reconstructed fullband
   output signal of length FRAMESAMPLES. WebRtcIsacfix_FilterAndCombine
   is the sibling function of WebRtcIsacfix_SplitAndFilter */
/* INPUTS:
   inLP: a length FRAMESAMPLES/2 array of input low-pass
   samples.
   inHP: a length FRAMESAMPLES/2 array of input high-pass
   samples.
   postfiltdata: input data structure containing the filterbank
   states from the previous decoding iteration.
   OUTPUTS:
   Out: a length FRAMESAMPLES array of output reconstructed
   samples (fullband) based on the input low-pass and
   high-pass signals.
   postfiltdata: the input data structure containing the filterbank
   states is updated for the next decoding iteration */
void WebRtcIsacfix_FilterAndCombine1(int16_t *tempin_ch1,
                                     int16_t *tempin_ch2,
                                     int16_t *out16,
                                     PostFiltBankstr *postfiltdata)
{
  int k;
  int16_t in[FRAMESAMPLES];

  /* all-pass filter the new upper and lower channel signal.
     For upper channel, use the all-pass filter factors that were used as a
     lower channel at the encoding side. So at the decoder, the corresponding
     all-pass filter factors for each channel are swapped.
     For lower channel signal, since all-pass filter factors at the decoder are
     swapped from the ones at the encoder, the 'upper' channel all-pass filter
     factors (kUpperApFactors) are used to filter this new lower channel signal.
  */
  WebRtcIsacfix_AllpassFilter2FixDec16(tempin_ch1,
                                       tempin_ch2,
                                       WebRtcIsacfix_kLowerApFactorsQ15,
                                       WebRtcIsacfix_kUpperApFactorsQ15,
                                       FRAMESAMPLES/2,
                                       postfiltdata->STATE_0_UPPER_fix,
                                       postfiltdata->STATE_0_LOWER_fix);

  /* Merge outputs to form the full length output signal.*/
  for (k=0;k<FRAMESAMPLES/2;k++) {
    in[2 * k] = tempin_ch2[k];
    in[2 * k + 1] = tempin_ch1[k];
  }

  /* High pass filter */
  WebRtcIsacfix_HighpassFilterFixDec32(in, FRAMESAMPLES, WebRtcIsacfix_kHPStCoeffOut1Q30, postfiltdata->HPstates1_fix);
  WebRtcIsacfix_HighpassFilterFixDec32(in, FRAMESAMPLES, WebRtcIsacfix_kHPStCoeffOut2Q30, postfiltdata->HPstates2_fix);

  for (k=0;k<FRAMESAMPLES;k++) {
    out16[k] = in[k];
  }
}
