@
@ Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
@
@ Use of this source code is governed by a BSD-style license
@ that can be found in the LICENSE file in the root of the source
@ tree. An additional intellectual property rights grant can be found
@ in the file PATENTS.  All contributing project authors may
@ be found in the AUTHORS file in the root of the source tree.
@

@ This file contains the function WebRtcSpl_ComplexBitReverse(), optimized
@ for ARMv5 platforms.
@ Reference C code is in file complex_bit_reverse.c. Bit-exact.

#include "rtc_base/system/asm_defines.h"

GLOBAL_FUNCTION WebRtcSpl_ComplexBitReverse
.align  2
DEFINE_FUNCTION WebRtcSpl_ComplexBitReverse
  push {r4-r7}

  cmp r1, #7
  adr r3, index_7                 @ Table pointer.
  mov r4, #112                    @ Number of interations.
  beq PRE_LOOP_STAGES_7_OR_8

  cmp r1, #8
  adr r3, index_8                 @ Table pointer.
  mov r4, #240                    @ Number of interations.
  beq PRE_LOOP_STAGES_7_OR_8

  mov r3, #1                      @ Initialize m.
  mov r1, r3, asl r1              @ n = 1 << stages;
  subs r6, r1, #1                 @ nn = n - 1;
  ble END

  mov r5, r0                      @ &complex_data
  mov r4, #0                      @ ml

LOOP_GENERIC:
  rsb r12, r4, r6                 @ l > nn - mr
  mov r2, r1                      @ n

LOOP_SHIFT:
  asr r2, #1                      @ l >>= 1;
  cmp r2, r12
  bgt LOOP_SHIFT

  sub r12, r2, #1
  and r4, r12, r4
  add r4, r2                      @ mr = (mr & (l - 1)) + l;
  cmp r4, r3                      @ mr <= m ?
  ble UPDATE_REGISTERS

  mov r12, r4, asl #2
  ldr r7, [r5, #4]                @ complex_data[2 * m, 2 * m + 1].
                                  @   Offset 4 due to m incrementing from 1.
  ldr r2, [r0, r12]               @ complex_data[2 * mr, 2 * mr + 1].
  str r7, [r0, r12]
  str r2, [r5, #4]

UPDATE_REGISTERS:
  add r3, r3, #1
  add r5, #4
  cmp r3, r1
  bne LOOP_GENERIC

  b END

PRE_LOOP_STAGES_7_OR_8:
  add r4, r3, r4, asl #1

LOOP_STAGES_7_OR_8:
  ldrsh r2, [r3], #2              @ index[m]
  ldrsh r5, [r3], #2              @ index[m + 1]
  ldr r1, [r0, r2]                @ complex_data[index[m], index[m] + 1]
  ldr r12, [r0, r5]               @ complex_data[index[m + 1], index[m + 1] + 1]
  cmp r3, r4
  str r1, [r0, r5]
  str r12, [r0, r2]
  bne LOOP_STAGES_7_OR_8

END:
  pop {r4-r7}
  bx lr

@ The index tables. Note the values are doubles of the actual indexes for 16-bit
@ elements, different from the generic C code. It actually provides byte offsets
@ for the indexes.

.align  2
index_7:  @ Indexes for stages == 7.
  .short 4, 256, 8, 128, 12, 384, 16, 64, 20, 320, 24, 192, 28, 448, 36, 288
  .short 40, 160, 44, 416, 48, 96, 52, 352, 56, 224, 60, 480, 68, 272, 72, 144
  .short 76, 400, 84, 336, 88, 208, 92, 464, 100, 304, 104, 176, 108, 432, 116
  .short 368, 120, 240, 124, 496, 132, 264, 140, 392, 148, 328, 152, 200, 156
  .short 456, 164, 296, 172, 424, 180, 360, 184, 232, 188, 488, 196, 280, 204
  .short 408, 212, 344, 220, 472, 228, 312, 236, 440, 244, 376, 252, 504, 268
  .short 388, 276, 324, 284, 452, 300, 420, 308, 356, 316, 484, 332, 404, 348
  .short 468, 364, 436, 380, 500, 412, 460, 444, 492

index_8:  @ Indexes for stages == 8.
  .short 4, 512, 8, 256, 12, 768, 16, 128, 20, 640, 24, 384, 28, 896, 32, 64
  .short 36, 576, 40, 320, 44, 832, 48, 192, 52, 704, 56, 448, 60, 960, 68, 544
  .short 72, 288, 76, 800, 80, 160, 84, 672, 88, 416, 92, 928, 100, 608, 104
  .short 352, 108, 864, 112, 224, 116, 736, 120, 480, 124, 992, 132, 528, 136
  .short 272, 140, 784, 148, 656, 152, 400, 156, 912, 164, 592, 168, 336, 172
  .short 848, 176, 208, 180, 720, 184, 464, 188, 976, 196, 560, 200, 304, 204
  .short 816, 212, 688, 216, 432, 220, 944, 228, 624, 232, 368, 236, 880, 244
  .short 752, 248, 496, 252, 1008, 260, 520, 268, 776, 276, 648, 280, 392, 284
  .short 904, 292, 584, 296, 328, 300, 840, 308, 712, 312, 456, 316, 968, 324
  .short 552, 332, 808, 340, 680, 344, 424, 348, 936, 356, 616, 364, 872, 372
  .short 744, 376, 488, 380, 1000, 388, 536, 396, 792, 404, 664, 412, 920, 420
  .short 600, 428, 856, 436, 728, 440, 472, 444, 984, 452, 568, 460, 824, 468
  .short 696, 476, 952, 484, 632, 492, 888, 500, 760, 508, 1016, 524, 772, 532
  .short 644, 540, 900, 548, 580, 556, 836, 564, 708, 572, 964, 588, 804, 596
  .short 676, 604, 932, 620, 868, 628, 740, 636, 996, 652, 788, 668, 916, 684
  .short 852, 692, 724, 700, 980, 716, 820, 732, 948, 748, 884, 764, 1012, 796
  .short 908, 812, 844, 828, 972, 860, 940, 892, 1004, 956, 988
