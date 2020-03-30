@
@ Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
@
@ Use of this source code is governed by a BSD-style license
@ that can be found in the LICENSE file in the root of the source
@ tree. An additional intellectual property rights grant can be found
@ in the file PATENTS.  All contributing project authors may
@ be found in the AUTHORS file in the root of the source tree.
@

@ This file contains the function WebRtcSpl_FilterARFastQ12(), optimized for
@ ARMv7  platform. The description header can be found in
@ signal_processing_library.h
@
@ Output is bit-exact with the generic C code as in filter_ar_fast_q12.c, and
@ the reference C code at end of this file.

@ Assumptions:
@ (1) data_length > 0
@ (2) coefficients_length > 1

@ Register usage:
@
@ r0:  &data_in[i]
@ r1:  &data_out[i], for result ouput
@ r2:  &coefficients[0]
@ r3:  coefficients_length
@ r4:  Iteration counter for the outer loop.
@ r5:  data_out[j] as multiplication inputs
@ r6:  Calculated value for output data_out[]; interation counter for inner loop
@ r7:  Partial sum of a filtering multiplication results
@ r8:  Partial sum of a filtering multiplication results
@ r9:  &data_out[], for filtering input; data_in[i]
@ r10: coefficients[j]
@ r11: Scratch
@ r12: &coefficients[j]
#if defined(__arm__) || defined(_M_ARM) || defined(__aarch64__)

#include "rtc_base/system/asm_defines.h"

GLOBAL_FUNCTION WebRtcSpl_FilterARFastQ12
.align  2
DEFINE_FUNCTION WebRtcSpl_FilterARFastQ12
  push {r4-r11}

  ldrsh r12, [sp, #32]         @ data_length
  subs r4, r12, #1
  beq ODD_LENGTH               @ jump if data_length == 1

LOOP_LENGTH:
  add r12, r2, r3, lsl #1
  sub r12, #4                  @ &coefficients[coefficients_length - 2]
  sub r9, r1, r3, lsl #1
  add r9, #2                   @ &data_out[i - coefficients_length + 1]
  ldr r5, [r9], #4             @ data_out[i - coefficients_length + {1,2}]

  mov r7, #0                   @ sum1
  mov r8, #0                   @ sum2
  subs r6, r3, #3              @ Iteration counter for inner loop.
  beq ODD_A_LENGTH             @ branch if coefficients_length == 3
  blt POST_LOOP_A_LENGTH       @ branch if coefficients_length == 2

LOOP_A_LENGTH:
  ldr r10, [r12], #-4          @ coefficients[j - 1], coefficients[j]
  subs r6, #2
  smlatt r8, r10, r5, r8       @ sum2 += coefficients[j] * data_out[i - j + 1];
  smlatb r7, r10, r5, r7       @ sum1 += coefficients[j] * data_out[i - j];
  smlabt r7, r10, r5, r7       @ coefficients[j - 1] * data_out[i - j + 1];
  ldr r5, [r9], #4             @ data_out[i - j + 2],  data_out[i - j + 3]
  smlabb r8, r10, r5, r8       @ coefficients[j - 1] * data_out[i - j + 2];
  bgt LOOP_A_LENGTH
  blt POST_LOOP_A_LENGTH

ODD_A_LENGTH:
  ldrsh r10, [r12, #2]         @ Filter coefficients coefficients[2]
  sub r12, #2                  @ &coefficients[0]
  smlabb r7, r10, r5, r7       @ sum1 += coefficients[2] * data_out[i - 2];
  smlabt r8, r10, r5, r8       @ sum2 += coefficients[2] * data_out[i - 1];
  ldr r5, [r9, #-2]            @ data_out[i - 1],  data_out[i]

POST_LOOP_A_LENGTH:
  ldr r10, [r12]               @ coefficients[0], coefficients[1]
  smlatb r7, r10, r5, r7       @ sum1 += coefficients[1] * data_out[i - 1];

  ldr r9, [r0], #4             @ data_in[i], data_in[i + 1]
  smulbb r6, r10, r9           @ output1 = coefficients[0] * data_in[i];
  sub r6, r7                   @ output1 -= sum1;

  sbfx r11, r6, #12, #16
  ssat r7, #16, r6, asr #12
  cmp r7, r11
  addeq r6, r6, #2048
  ssat r6, #16, r6, asr #12
  strh r6, [r1], #2            @ Store data_out[i]

  smlatb r8, r10, r6, r8       @ sum2 += coefficients[1] * data_out[i];
  smulbt r6, r10, r9           @ output2 = coefficients[0] * data_in[i + 1];
  sub r6, r8                   @ output1 -= sum1;

  sbfx r11, r6, #12, #16
  ssat r7, #16, r6, asr #12
  cmp r7, r11
  addeq r6, r6, #2048
  ssat r6, #16, r6, asr #12
  strh r6, [r1], #2            @ Store data_out[i + 1]

  subs r4, #2
  bgt LOOP_LENGTH
  blt END                      @ For even data_length, it's done. Jump to END.

@ Process i = data_length -1, for the case of an odd length.
ODD_LENGTH:
  add r12, r2, r3, lsl #1
  sub r12, #4                  @ &coefficients[coefficients_length - 2]
  sub r9, r1, r3, lsl #1
  add r9, #2                   @ &data_out[i - coefficients_length + 1]
  mov r7, #0                   @ sum1
  mov r8, #0                   @ sum1
  subs r6, r3, #2              @ inner loop counter
  beq EVEN_A_LENGTH            @ branch if coefficients_length == 2

LOOP2_A_LENGTH:
  ldr r10, [r12], #-4          @ coefficients[j - 1], coefficients[j]
  ldr r5, [r9], #4             @ data_out[i - j],  data_out[i - j + 1]
  subs r6, #2
  smlatb r7, r10, r5, r7       @ sum1 += coefficients[j] * data_out[i - j];
  smlabt r8, r10, r5, r8       @ coefficients[j - 1] * data_out[i - j + 1];
  bgt LOOP2_A_LENGTH
  addlt r12, #2
  blt POST_LOOP2_A_LENGTH

EVEN_A_LENGTH:
  ldrsh r10, [r12, #2]         @ Filter coefficients coefficients[1]
  ldrsh r5, [r9]               @ data_out[i - 1]
  smlabb r7, r10, r5, r7       @ sum1 += coefficients[1] * data_out[i - 1];

POST_LOOP2_A_LENGTH:
  ldrsh r10, [r12]             @ Filter coefficients coefficients[0]
  ldrsh r9, [r0]               @ data_in[i]
  smulbb r6, r10, r9           @ output1 = coefficients[0] * data_in[i];
  sub r6, r7                   @ output1 -= sum1;
  sub r6, r8                   @ output1 -= sum1;
  sbfx r8, r6, #12, #16
  ssat r7, #16, r6, asr #12
  cmp r7, r8
  addeq r6, r6, #2048
  ssat r6, #16, r6, asr #12
  strh r6, [r1]                @ Store the data_out[i]

END:
  pop {r4-r11}
  bx  lr

@Reference C code:
@
@void WebRtcSpl_FilterARFastQ12(int16_t* data_in,
@                               int16_t* data_out,
@                               int16_t* __restrict coefficients,
@                               size_t coefficients_length,
@                               size_t data_length) {
@  size_t i = 0;
@  size_t j = 0;
@
@  assert(data_length > 0);
@  assert(coefficients_length > 1);
@
@  for (i = 0; i < data_length - 1; i += 2) {
@    int32_t output1 = 0;
@    int32_t sum1 = 0;
@    int32_t output2 = 0;
@    int32_t sum2 = 0;
@
@    for (j = coefficients_length - 1; j > 2; j -= 2) {
@      sum1 += coefficients[j]      * data_out[i - j];
@      sum1 += coefficients[j - 1]  * data_out[i - j + 1];
@      sum2 += coefficients[j]     * data_out[i - j + 1];
@      sum2 += coefficients[j - 1] * data_out[i - j + 2];
@    }
@
@    if (j == 2) {
@      sum1 += coefficients[2] * data_out[i - 2];
@      sum2 += coefficients[2] * data_out[i - 1];
@    }
@
@    sum1 += coefficients[1] * data_out[i - 1];
@    output1 = coefficients[0] * data_in[i];
@    output1 -= sum1;
@    // Saturate and store the output.
@    output1 = WEBRTC_SPL_SAT(134215679, output1, -134217728);
@    data_out[i] = (int16_t)((output1 + 2048) >> 12);
@
@    sum2 += coefficients[1] * data_out[i];
@    output2 = coefficients[0] * data_in[i + 1];
@    output2 -= sum2;
@    // Saturate and store the output.
@    output2 = WEBRTC_SPL_SAT(134215679, output2, -134217728);
@    data_out[i + 1] = (int16_t)((output2 + 2048) >> 12);
@  }
@
@  if (i == data_length - 1) {
@    int32_t output1 = 0;
@    int32_t sum1 = 0;
@
@    for (j = coefficients_length - 1; j > 1; j -= 2) {
@      sum1 += coefficients[j]      * data_out[i - j];
@      sum1 += coefficients[j - 1]  * data_out[i - j + 1];
@    }
@
@    if (j == 1) {
@      sum1 += coefficients[1] * data_out[i - 1];
@    }
@
@    output1 = coefficients[0] * data_in[i];
@    output1 -= sum1;
@    // Saturate and store the output.
@    output1 = WEBRTC_SPL_SAT(134215679, output1, -134217728);
@    data_out[i] = (int16_t)((output1 + 2048) >> 12);
@  }
@}

#endif
