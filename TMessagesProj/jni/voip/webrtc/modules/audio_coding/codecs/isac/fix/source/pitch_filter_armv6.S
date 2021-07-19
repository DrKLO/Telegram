@
@ Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
@
@ Use of this source code is governed by a BSD-style license
@ that can be found in the LICENSE file in the root of the source
@ tree. An additional intellectual property rights grant can be found
@ in the file PATENTS.  All contributing project authors may
@ be found in the AUTHORS file in the root of the source tree.
@

@ Contains the core loop routine for the pitch filter function in iSAC,
@ optimized for ARMv7 platforms.
@
@ Output is bit-exact with the reference C code in pitch_filter.c.

#include "modules/audio_coding/codecs/isac/fix/source/settings.h"
#include "rtc_base/system/asm_defines.h"

GLOBAL_FUNCTION WebRtcIsacfix_PitchFilterCore
.align  2

@ void WebRtcIsacfix_PitchFilterCore(int loopNumber,
@                                    int16_t gain,
@                                    size_t index,
@                                    int16_t sign,
@                                    int16_t* inputState,
@                                    int16_t* outputBuf2,
@                                    const int16_t* coefficient,
@                                    int16_t* inputBuf,
@                                    int16_t* outputBuf,
@                                    int* index2) {
DEFINE_FUNCTION WebRtcIsacfix_PitchFilterCore
  push {r4-r11}
  sub sp, #8

  str r0, [sp]                @ loopNumber
  str r3, [sp, #4]            @ sign
  ldr r3, [sp, #44]           @ outputBuf2
  ldr r6, [sp, #60]           @ index2
  ldr r7, [r6]                @ *index2
  ldr r8, [sp, #52]           @ inputBuf
  ldr r12, [sp, #56]          @ outputBuf

  add r4, r7, r0
  str r4, [r6]                @ Store return value to index2.

  mov r10, r7, asl #1
  add r12, r10                @ &outputBuf[*index2]
  add r8, r10                 @ &inputBuf[*index2]

  add r4, r7, #PITCH_BUFFSIZE @ *index2 + PITCH_BUFFSIZE
  add r6, r3, r4, lsl #1      @ &outputBuf2[*index2 + PITCH_BUFFSIZE]
  sub r4, r2                  @ r2: index
  sub r4, #2                  @ *index2 + PITCH_BUFFSIZE - index - 2
  add r3, r4, lsl #1          @ &ubufQQpos2[*index2]
  ldr r9, [sp, #48]           @ coefficient

LOOP:
@ Usage of registers in the loop:
@  r0: loop counter
@  r1: gain
@  r2: tmpW32
@  r3: &ubufQQpos2[]
@  r6: &outputBuf2[]
@  r8: &inputBuf[]
@  r9: &coefficient[]
@  r12: &outputBuf[]
@  r4, r5, r7, r10, r11: scratch

  @ Filter to get fractional pitch.
  @ The pitch filter loop here is unrolled with 9 multipications.
  pld [r3]
  ldr r10, [r3], #4           @ ubufQQpos2[*index2 + 0, *index2 + 1]
  ldr r4, [r9], #4            @ coefficient[0, 1]
  ldr r11, [r3], #4
  ldr r5, [r9], #4
  smuad r2, r10, r4
  smlad r2, r11, r5, r2

  ldr r10, [r3], #4
  ldr r4, [r9], #4
  ldr r11, [r3], #4
  ldr r5, [r9], #4
  smlad r2, r10, r4, r2
  ldrh r10, [r3], #-14        @ r3 back to &ubufQQpos2[*index2].
  ldrh  r4, [r9], #-16        @ r9 back to &coefficient[0].
  smlad r2, r11, r5, r2
  smlabb r2, r10, r4, r2

  @ Saturate to avoid overflow in tmpW16.
  asr r2, #1
  add r4, r2, #0x1000
  ssat r7, #16, r4, asr #13

  @ Shift low pass filter state, and excute the low pass filter.
  @ The memmove() and the low pass filter loop are unrolled and mixed.
  smulbb r5, r1, r7
  add r7, r5, #0x800
  asr r7, #12                 @ Get the value for inputState[0].
  ldr r11, [sp, #40]          @ inputState
  pld [r11]
  adr r10, kDampFilter
  ldrsh r4, [r10], #2         @ kDampFilter[0]
  mul r2, r7, r4
  ldr r4, [r11]               @ inputState[0, 1], before shift.
  strh r7, [r11]              @ inputState[0], after shift.
  ldr r5, [r11, #4]           @ inputState[2, 3], before shift.
  ldr r7, [r10], #4           @ kDampFilter[1, 2]
  ldr r10, [r10]              @ kDampFilter[3, 4]
  str r4, [r11, #2]           @ inputState[1, 2], after shift.
  str r5, [r11, #6]           @ inputState[3, 4], after shift.
  smlad r2, r4, r7, r2
  smlad r2, r5, r10, r2

  @ Saturate to avoid overflow.
  @ First shift the sample to the range of [0xC0000000, 0x3FFFFFFF],
  @ to avoid overflow in the next saturation step.
  asr r2, #1
  add r10, r2, #0x2000
  ssat r10, #16, r10, asr #14

  @ Subtract from input and update buffer.
  ldr r11, [sp, #4]           @ sign
  ldrsh r4, [r8]
  ldrsh r7, [r8], #2          @ inputBuf[*index2]
  smulbb r5, r11, r10
  subs r0, #1
  sub r4, r5
  ssat r2, #16, r4
  strh  r2, [r12], #2         @ outputBuf[*index2]

  add r2, r7
  ssat r2, #16, r2
  strh  r2, [r6], #2          @ outputBuff2[*index2 + PITCH_BUFFSIZE]
  bgt LOOP

  add sp, #8
  pop {r4-r11}
  bx  lr

.align  2
kDampFilter:
  .short  -2294, 8192, 20972, 8192, -2294
