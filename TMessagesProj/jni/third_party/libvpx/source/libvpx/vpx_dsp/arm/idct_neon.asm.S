@ This file was created from a .asm file
@  using the ads2gas.pl script.
	.syntax unified
@
@  Copyright (c) 2016 The WebM project authors. All Rights Reserved.
@
@  Use of this source code is governed by a BSD-style license
@  that can be found in the LICENSE file in the root of the source
@  tree. An additional intellectual property rights grant can be found
@  in the file PATENTS.  All contributing project authors may
@  be found in the AUTHORS file in the root of the source tree.
@

    .include  "./vpx_config.asm"

    @ Helper functions used to load tran_low_t into int16, narrowing if
    @ necessary.

    @ $dst0..3 are d registers with the pairs assumed to be contiguous in
    @ non-high-bitdepth builds. q0-q3 are used as temporaries in high-bitdepth.
.macro    LOAD_TRAN_LOW_TO_S16 dst0, dst1, dst2, dst3, src
    .if CONFIG_VP9_HIGHBITDEPTH
    vld1.s32        {q0,q1}, [\src]!
    vld1.s32        {q2,q3}, [\src]!
    vmovn.i32       \dst0, q0
    vmovn.i32       \dst1, q1
    vmovn.i32       \dst2, q2
    vmovn.i32       \dst3, q3
    .else
    vld1.s16        {\dst0-\dst1,\dst2-\dst3}, [\src]!
    .endif
    .endm

    @ $dst0..3 are d registers. q0-q3 are used as temporaries in high-bitdepth.
.macro    LOAD_TRAN_LOW_TO_S16X2 dst0, dst1, dst2, dst3, src
    .if CONFIG_VP9_HIGHBITDEPTH
    vld2.s32        {q0,q1}, [\src]!
    vld2.s32        {q2,q3}, [\src]!
    vmovn.i32       \dst0, q0
    vmovn.i32       \dst1, q2
    vmovn.i32       \dst2, q1
    vmovn.i32       \dst3, q3
    .else
    vld2.s16        {\dst0,\dst1,\dst2,\dst3}, [\src]!
    .endif
    .endm
	.section	.note.GNU-stack,"",%progbits
