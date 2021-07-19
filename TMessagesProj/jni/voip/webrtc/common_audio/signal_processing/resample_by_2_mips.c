/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
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

#if defined(MIPS32_LE)

#include "common_audio/signal_processing/include/signal_processing_library.h"

#if !defined(MIPS_DSP_R2_LE)
// allpass filter coefficients.
static const uint16_t kResampleAllpass1[3] = {3284, 24441, 49528};
static const uint16_t kResampleAllpass2[3] = {12199, 37471, 60255};
#endif

// Multiply a 32-bit value with a 16-bit value and accumulate to another input:
#define MUL_ACCUM_1(a, b, c) WEBRTC_SPL_SCALEDIFF32(a, b, c)
#define MUL_ACCUM_2(a, b, c) WEBRTC_SPL_SCALEDIFF32(a, b, c)

// decimator
void WebRtcSpl_DownsampleBy2(const int16_t* in,
                             size_t len,
                             int16_t* out,
                             int32_t* filtState) {
  int32_t out32;
  size_t i, len1;

  register int32_t state0 = filtState[0];
  register int32_t state1 = filtState[1];
  register int32_t state2 = filtState[2];
  register int32_t state3 = filtState[3];
  register int32_t state4 = filtState[4];
  register int32_t state5 = filtState[5];
  register int32_t state6 = filtState[6];
  register int32_t state7 = filtState[7];

#if defined(MIPS_DSP_R2_LE)
  int32_t k1Res0, k1Res1, k1Res2, k2Res0, k2Res1, k2Res2;

  k1Res0= 3284;
  k1Res1= 24441;
  k1Res2= 49528;
  k2Res0= 12199;
  k2Res1= 37471;
  k2Res2= 60255;
  len1 = (len >> 1);

  const int32_t* inw = (int32_t*)in;
  int32_t tmp11, tmp12, tmp21, tmp22;
  int32_t in322, in321;
  int32_t diff1, diff2;
  for (i = len1; i > 0; i--) {
    __asm__ volatile (
      "lh         %[in321],    0(%[inw])                  \n\t"
      "lh         %[in322],    2(%[inw])                  \n\t"

      "sll        %[in321],    %[in321],      10          \n\t"
      "sll        %[in322],    %[in322],      10          \n\t"

      "addiu      %[inw],      %[inw],        4           \n\t"

      "subu       %[diff1],    %[in321],      %[state1]   \n\t"
      "subu       %[diff2],    %[in322],      %[state5]   \n\t"

      : [in322] "=&r" (in322), [in321] "=&r" (in321),
        [diff1] "=&r" (diff1), [diff2] "=r" (diff2), [inw] "+r" (inw)
      : [state1] "r" (state1), [state5] "r" (state5)
      : "memory"
    );

    __asm__ volatile (
      "mult       $ac0,       %[diff1],       %[k2Res0]   \n\t"
      "mult       $ac1,       %[diff2],       %[k1Res0]   \n\t"

      "extr.w     %[tmp11],   $ac0,           16          \n\t"
      "extr.w     %[tmp12],   $ac1,           16          \n\t"

      "addu       %[tmp11],   %[state0],      %[tmp11]    \n\t"
      "addu       %[tmp12],   %[state4],      %[tmp12]    \n\t"

      "addiu      %[state0],  %[in321],       0           \n\t"
      "addiu      %[state4],  %[in322],       0           \n\t"

      "subu       %[diff1],   %[tmp11],       %[state2]   \n\t"
      "subu       %[diff2],   %[tmp12],       %[state6]   \n\t"

      "mult       $ac0,       %[diff1],       %[k2Res1]   \n\t"
      "mult       $ac1,       %[diff2],       %[k1Res1]   \n\t"

      "extr.w     %[tmp21],   $ac0,           16          \n\t"
      "extr.w     %[tmp22],   $ac1,           16          \n\t"

      "addu       %[tmp21],   %[state1],      %[tmp21]    \n\t"
      "addu       %[tmp22],   %[state5],      %[tmp22]    \n\t"

      "addiu      %[state1],  %[tmp11],       0           \n\t"
      "addiu      %[state5],  %[tmp12],       0           \n\t"
      : [tmp22] "=r" (tmp22), [tmp21] "=&r" (tmp21),
        [tmp11] "=&r" (tmp11), [state0] "+r" (state0),
        [state1] "+r" (state1),
        [state2] "+r" (state2),
        [state4] "+r" (state4), [tmp12] "=&r" (tmp12),
        [state6] "+r" (state6), [state5] "+r" (state5)
      : [k1Res1] "r" (k1Res1), [k2Res1] "r" (k2Res1), [k2Res0] "r" (k2Res0),
        [diff2] "r" (diff2), [diff1] "r" (diff1), [in322] "r" (in322),
        [in321] "r" (in321), [k1Res0] "r" (k1Res0)
      : "hi", "lo", "$ac1hi", "$ac1lo"
    );

    // upper allpass filter
    __asm__ volatile (
      "subu       %[diff1],   %[tmp21],       %[state3]   \n\t"
      "subu       %[diff2],   %[tmp22],       %[state7]   \n\t"

      "mult       $ac0,       %[diff1],       %[k2Res2]   \n\t"
      "mult       $ac1,       %[diff2],       %[k1Res2]   \n\t"
      "extr.w     %[state3],  $ac0,           16          \n\t"
      "extr.w     %[state7],  $ac1,           16          \n\t"
      "addu       %[state3],  %[state2],      %[state3]   \n\t"
      "addu       %[state7],  %[state6],      %[state7]   \n\t"

      "addiu      %[state2],  %[tmp21],       0           \n\t"
      "addiu      %[state6],  %[tmp22],       0           \n\t"

      // add two allpass outputs, divide by two and round
      "addu       %[out32],   %[state3],      %[state7]   \n\t"
      "addiu      %[out32],   %[out32],       1024        \n\t"
      "sra        %[out32],   %[out32],       11          \n\t"
      : [state3] "+r" (state3), [state6] "+r" (state6),
        [state2] "+r" (state2), [diff2] "=&r" (diff2),
        [out32] "=r" (out32), [diff1] "=&r" (diff1), [state7] "+r" (state7)
      : [tmp22] "r" (tmp22), [tmp21] "r" (tmp21),
        [k1Res2] "r" (k1Res2), [k2Res2] "r" (k2Res2)
      : "hi", "lo", "$ac1hi", "$ac1lo"
    );

    // limit amplitude to prevent wrap-around, and write to output array
    *out++ = WebRtcSpl_SatW32ToW16(out32);
  }
#else  // #if defined(MIPS_DSP_R2_LE)
  int32_t tmp1, tmp2, diff;
  int32_t in32;
  len1 = (len >> 1)/4;
  for (i = len1; i > 0; i--) {
    // lower allpass filter
    in32 = (int32_t)(*in++) << 10;
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
    in32 = (int32_t)(*in++) << 10;
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
    // lower allpass filter
    in32 = (int32_t)(*in++) << 10;
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
    in32 = (int32_t)(*in++) << 10;
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
    // lower allpass filter
    in32 = (int32_t)(*in++) << 10;
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
    in32 = (int32_t)(*in++) << 10;
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
    // lower allpass filter
    in32 = (int32_t)(*in++) << 10;
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
    in32 = (int32_t)(*in++) << 10;
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
#endif  // #if defined(MIPS_DSP_R2_LE)
  __asm__ volatile (
    "sw       %[state0],      0(%[filtState])     \n\t"
    "sw       %[state1],      4(%[filtState])     \n\t"
    "sw       %[state2],      8(%[filtState])     \n\t"
    "sw       %[state3],      12(%[filtState])    \n\t"
    "sw       %[state4],      16(%[filtState])    \n\t"
    "sw       %[state5],      20(%[filtState])    \n\t"
    "sw       %[state6],      24(%[filtState])    \n\t"
    "sw       %[state7],      28(%[filtState])    \n\t"
    :
    : [state0] "r" (state0), [state1] "r" (state1), [state2] "r" (state2),
      [state3] "r" (state3), [state4] "r" (state4), [state5] "r" (state5),
      [state6] "r" (state6), [state7] "r" (state7), [filtState] "r" (filtState)
    : "memory"
  );
}

#endif  // #if defined(MIPS32_LE)
