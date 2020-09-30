@
@ Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
@
@ Use of this source code is governed by a BSD-style license
@ that can be found in the LICENSE file in the root of the source
@ tree. An additional intellectual property rights grant can be found
@ in the file PATENTS.  All contributing project authors may
@ be found in the AUTHORS file in the root of the source tree.
@

@ Contains a function for the core loop in the normalized lattice AR
@ filter routine for iSAC codec, optimized for ARMv7 platforms.
@
@ Output is bit-exact with the reference C code in lattic_c.c
@
@ Register usage:
@
@ r0:  &ar_g_Q0
@ r1:  &ar_f_Q0
@ r2:  &cth_Q15
@ r3:  &sth_Q15
@ r4:  out loop counter
@ r5:  tmpAR
@ r9:  inner loop counter
@ r12: constant #16384
@ r6, r7, r8, r10, r11: scratch

#include "modules/audio_coding/codecs/isac/fix/source/settings.h"
#include "rtc_base/system/asm_defines.h"

GLOBAL_FUNCTION WebRtcIsacfix_FilterArLoop
.align  2
DEFINE_FUNCTION WebRtcIsacfix_FilterArLoop
  push    {r4-r11}

  add     r1, #2                 @ &ar_f_Q0[1]
  mov     r12, #16384
  mov     r4, #HALF_SUBFRAMELEN
  sub     r4, #1                 @ Outer loop counter = HALF_SUBFRAMELEN - 1

HALF_SUBFRAME_LOOP:  @ for (n = 0; n < HALF_SUBFRAMELEN - 1; n++)

  ldr     r9, [sp, #32]          @ Restore the inner loop counter to order_coef
  ldrh    r5, [r1]               @ tmpAR = ar_f_Q0[n+1]
  add     r0, r9, asl #1         @ Restore r0 to &ar_g_Q0[order_coef]
  add     r2, r9, asl #1         @ Restore r2 to &cth_Q15[order_coef]
  add     r3, r9, asl #1         @ Restore r3 to &sth_Q15[order_coef]

ORDER_COEF_LOOP:  @ for (k = order_coef; k > 0; k--)

  ldrh    r7, [r3, #-2]!         @ sth_Q15[k - 1]
  ldrh    r6, [r2, #-2]!         @ cth_Q15[k - 1]

  ldrh    r8, [r0, #-2]          @ ar_g_Q0[k - 1]
  smlabb  r11, r7, r5, r12       @ sth_Q15[k - 1] * tmpAR + 16384
  smlabb  r10, r6, r5, r12       @ cth_Q15[k - 1] * tmpAR + 16384
  smulbb  r7, r7, r8             @ sth_Q15[k - 1] * ar_g_Q0[k - 1]
  smlabb  r11, r6, r8, r11       @ cth_Q15[k - 1] * ar_g_Q0[k - 1] +
                                 @     (sth_Q15[k - 1] * tmpAR + 16384)

  sub     r10, r10, r7           @ cth_Q15[k - 1] * tmpAR + 16384 -
                                 @     (sth_Q15[k - 1] * ar_g_Q0[k - 1])
  ssat    r11, #16, r11, asr #15
  ssat    r5, #16, r10, asr #15
  strh    r11, [r0], #-2         @ Output: ar_g_Q0[k]

  subs    r9, #1
  bgt     ORDER_COEF_LOOP

  strh    r5, [r0]               @ Output: ar_g_Q0[0] = tmpAR;
  strh    r5, [r1], #2           @ Output: ar_f_Q0[n+1] = tmpAR;

  subs    r4, #1
  bne     HALF_SUBFRAME_LOOP

  pop     {r4-r11}
  bx      lr
