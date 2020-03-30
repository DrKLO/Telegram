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
 * This file contains the resampling by two functions.
 * The description header can be found in signal_processing_library.h
 *
 */

#include "common_audio/signal_processing/include/signal_processing_library.h"

#ifdef WEBRTC_ARCH_ARM_V7

// allpass filter coefficients.
static const uint32_t kResampleAllpass1[3] = {3284, 24441, 49528 << 15};
static const uint32_t kResampleAllpass2[3] =
  {12199, 37471 << 15, 60255 << 15};

// Multiply two 32-bit values and accumulate to another input value.
// Return: state + ((diff * tbl_value) >> 16)

static __inline int32_t MUL_ACCUM_1(int32_t tbl_value,
                                    int32_t diff,
                                    int32_t state) {
  int32_t result;
  __asm __volatile ("smlawb %0, %1, %2, %3": "=r"(result): "r"(diff),
                                   "r"(tbl_value), "r"(state));
  return result;
}

// Multiply two 32-bit values and accumulate to another input value.
// Return: Return: state + (((diff << 1) * tbl_value) >> 32)
//
// The reason to introduce this function is that, in case we can't use smlawb
// instruction (in MUL_ACCUM_1) due to input value range, we can still use
// smmla to save some cycles.

static __inline int32_t MUL_ACCUM_2(int32_t tbl_value,
                                    int32_t diff,
                                    int32_t state) {
  int32_t result;
  __asm __volatile ("smmla %0, %1, %2, %3": "=r"(result): "r"(diff << 1),
                                  "r"(tbl_value), "r"(state));
  return result;
}

#else

// allpass filter coefficients.
static const uint16_t kResampleAllpass1[3] = {3284, 24441, 49528};
static const uint16_t kResampleAllpass2[3] = {12199, 37471, 60255};

// Multiply a 32-bit value with a 16-bit value and accumulate to another input:
#define MUL_ACCUM_1(a, b, c) WEBRTC_SPL_SCALEDIFF32(a, b, c)
#define MUL_ACCUM_2(a, b, c) WEBRTC_SPL_SCALEDIFF32(a, b, c)

#endif  // WEBRTC_ARCH_ARM_V7


// decimator
#if !defined(MIPS32_LE)
void WebRtcSpl_DownsampleBy2(const int16_t* in, size_t len,
                             int16_t* out, int32_t* filtState) {
  int32_t tmp1, tmp2, diff, in32, out32;
  size_t i;

  register int32_t state0 = filtState[0];
  register int32_t state1 = filtState[1];
  register int32_t state2 = filtState[2];
  register int32_t state3 = filtState[3];
  register int32_t state4 = filtState[4];
  register int32_t state5 = filtState[5];
  register int32_t state6 = filtState[6];
  register int32_t state7 = filtState[7];

  for (i = (len >> 1); i > 0; i--) {
    // lower allpass filter
    in32 = (int32_t)(*in++) * (1 << 10);
    diff = in32 - state1;
    tmp1 = MUL_ACCUM_1(kResampleAllpass2[0], diff, state0);
    state0 = in32;
    diff = tmp1 - state2;
    tmp2 = MUL_ACCUM_2(kResampleAllpass2[1], diff, state1);
    state1 = tmp1;
    diff = tmp2 - state3;
    state3 = MUL_ACCUM_2(kResampleAllpass2[2], diff, state2);
    state2 = tmp2;

    // upper allpass filter
    in32 = (int32_t)(*in++) * (1 << 10);
    diff = in32 - state5;
    tmp1 = MUL_ACCUM_1(kResampleAllpass1[0], diff, state4);
    state4 = in32;
    diff = tmp1 - state6;
    tmp2 = MUL_ACCUM_1(kResampleAllpass1[1], diff, state5);
    state5 = tmp1;
    diff = tmp2 - state7;
    state7 = MUL_ACCUM_2(kResampleAllpass1[2], diff, state6);
    state6 = tmp2;

    // add two allpass outputs, divide by two and round
    out32 = (state3 + state7 + 1024) >> 11;

    // limit amplitude to prevent wrap-around, and write to output array
    *out++ = WebRtcSpl_SatW32ToW16(out32);
  }

  filtState[0] = state0;
  filtState[1] = state1;
  filtState[2] = state2;
  filtState[3] = state3;
  filtState[4] = state4;
  filtState[5] = state5;
  filtState[6] = state6;
  filtState[7] = state7;
}
#endif  // #if defined(MIPS32_LE)


void WebRtcSpl_UpsampleBy2(const int16_t* in, size_t len,
                           int16_t* out, int32_t* filtState) {
  int32_t tmp1, tmp2, diff, in32, out32;
  size_t i;

  register int32_t state0 = filtState[0];
  register int32_t state1 = filtState[1];
  register int32_t state2 = filtState[2];
  register int32_t state3 = filtState[3];
  register int32_t state4 = filtState[4];
  register int32_t state5 = filtState[5];
  register int32_t state6 = filtState[6];
  register int32_t state7 = filtState[7];

  for (i = len; i > 0; i--) {
    // lower allpass filter
    in32 = (int32_t)(*in++) * (1 << 10);
    diff = in32 - state1;
    tmp1 = MUL_ACCUM_1(kResampleAllpass1[0], diff, state0);
    state0 = in32;
    diff = tmp1 - state2;
    tmp2 = MUL_ACCUM_1(kResampleAllpass1[1], diff, state1);
    state1 = tmp1;
    diff = tmp2 - state3;
    state3 = MUL_ACCUM_2(kResampleAllpass1[2], diff, state2);
    state2 = tmp2;

    // round; limit amplitude to prevent wrap-around; write to output array
    out32 = (state3 + 512) >> 10;
    *out++ = WebRtcSpl_SatW32ToW16(out32);

    // upper allpass filter
    diff = in32 - state5;
    tmp1 = MUL_ACCUM_1(kResampleAllpass2[0], diff, state4);
    state4 = in32;
    diff = tmp1 - state6;
    tmp2 = MUL_ACCUM_2(kResampleAllpass2[1], diff, state5);
    state5 = tmp1;
    diff = tmp2 - state7;
    state7 = MUL_ACCUM_2(kResampleAllpass2[2], diff, state6);
    state6 = tmp2;

    // round; limit amplitude to prevent wrap-around; write to output array
    out32 = (state7 + 512) >> 10;
    *out++ = WebRtcSpl_SatW32ToW16(out32);
  }

  filtState[0] = state0;
  filtState[1] = state1;
  filtState[2] = state2;
  filtState[3] = state3;
  filtState[4] = state4;
  filtState[5] = state5;
  filtState[6] = state6;
  filtState[7] = state7;
}
