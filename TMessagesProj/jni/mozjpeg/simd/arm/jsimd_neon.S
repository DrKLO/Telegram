/*
 * ARMv7 NEON optimizations for libjpeg-turbo
 *
 * Copyright (C) 2009-2011, Nokia Corporation and/or its subsidiary(-ies).
 *                          All Rights Reserved.
 * Author:  Siarhei Siamashka <siarhei.siamashka@nokia.com>
 * Copyright (C) 2014, Siarhei Siamashka.  All Rights Reserved.
 * Copyright (C) 2014, Linaro Limited.  All Rights Reserved.
 * Copyright (C) 2015, D. R. Commander.  All Rights Reserved.
 * Copyright (C) 2015-2016, 2018, Matthieu Darbois.  All Rights Reserved.
 *
 * This software is provided 'as-is', without any express or implied
 * warranty.  In no event will the authors be held liable for any damages
 * arising from the use of this software.
 *
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 *
 * 1. The origin of this software must not be misrepresented; you must not
 *    claim that you wrote the original software. If you use this software
 *    in a product, an acknowledgment in the product documentation would be
 *    appreciated but is not required.
 * 2. Altered source versions must be plainly marked as such, and must not be
 *    misrepresented as being the original software.
 * 3. This notice may not be removed or altered from any source distribution.
 */

#if defined(__linux__) && defined(__ELF__)
.section .note.GNU-stack, "", %progbits  /* mark stack as non-executable */
#endif

.text
.fpu neon
.arch armv7a
.object_arch armv4
.arm
.syntax unified


#define RESPECT_STRICT_ALIGNMENT  1


/*****************************************************************************/

/* Supplementary macro for setting function attributes */
.macro asm_function fname
#ifdef __APPLE__
    .private_extern _\fname
    .globl _\fname
_\fname:
#else
    .global \fname
#ifdef __ELF__
    .hidden \fname
    .type \fname, %function
#endif
\fname:
#endif
.endm

/* Transpose a block of 4x4 coefficients in four 64-bit registers */
.macro transpose_4x4 x0, x1, x2, x3
    vtrn.16         \x0, \x1
    vtrn.16         \x2, \x3
    vtrn.32         \x0, \x2
    vtrn.32         \x1, \x3
.endm


#define CENTERJSAMPLE  128

/*****************************************************************************/

/*
 * Perform dequantization and inverse DCT on one block of coefficients.
 *
 * GLOBAL(void)
 * jsimd_idct_islow_neon(void *dct_table, JCOEFPTR coef_block,
 *                       JSAMPARRAY output_buf, JDIMENSION output_col)
 */

#define FIX_0_298631336  (2446)
#define FIX_0_390180644  (3196)
#define FIX_0_541196100  (4433)
#define FIX_0_765366865  (6270)
#define FIX_0_899976223  (7373)
#define FIX_1_175875602  (9633)
#define FIX_1_501321110  (12299)
#define FIX_1_847759065  (15137)
#define FIX_1_961570560  (16069)
#define FIX_2_053119869  (16819)
#define FIX_2_562915447  (20995)
#define FIX_3_072711026  (25172)

#define FIX_1_175875602_MINUS_1_961570560  (FIX_1_175875602 - FIX_1_961570560)
#define FIX_1_175875602_MINUS_0_390180644  (FIX_1_175875602 - FIX_0_390180644)
#define FIX_0_541196100_MINUS_1_847759065  (FIX_0_541196100 - FIX_1_847759065)
#define FIX_3_072711026_MINUS_2_562915447  (FIX_3_072711026 - FIX_2_562915447)
#define FIX_0_298631336_MINUS_0_899976223  (FIX_0_298631336 - FIX_0_899976223)
#define FIX_1_501321110_MINUS_0_899976223  (FIX_1_501321110 - FIX_0_899976223)
#define FIX_2_053119869_MINUS_2_562915447  (FIX_2_053119869 - FIX_2_562915447)
#define FIX_0_541196100_PLUS_0_765366865   (FIX_0_541196100 + FIX_0_765366865)

/*
 * Reference SIMD-friendly 1-D ISLOW iDCT C implementation.
 * Uses some ideas from the comments in 'simd/jiss2int-64.asm'
 */
#define REF_1D_IDCT(xrow0, xrow1, xrow2, xrow3, xrow4, xrow5, xrow6, xrow7) { \
    DCTELEM row0, row1, row2, row3, row4, row5, row6, row7; \
    JLONG   q1, q2, q3, q4, q5, q6, q7; \
    JLONG   tmp11_plus_tmp2, tmp11_minus_tmp2; \
    \
    /* 1-D iDCT input data */ \
    row0 = xrow0; \
    row1 = xrow1; \
    row2 = xrow2; \
    row3 = xrow3; \
    row4 = xrow4; \
    row5 = xrow5; \
    row6 = xrow6; \
    row7 = xrow7; \
    \
    q5 = row7 + row3; \
    q4 = row5 + row1; \
    q6 = MULTIPLY(q5, FIX_1_175875602_MINUS_1_961570560) + \
         MULTIPLY(q4, FIX_1_175875602); \
    q7 = MULTIPLY(q5, FIX_1_175875602) + \
         MULTIPLY(q4, FIX_1_175875602_MINUS_0_390180644); \
    q2 = MULTIPLY(row2, FIX_0_541196100) + \
         MULTIPLY(row6, FIX_0_541196100_MINUS_1_847759065); \
    q4 = q6; \
    q3 = ((JLONG)row0 - (JLONG)row4) << 13; \
    q6 += MULTIPLY(row5, -FIX_2_562915447) + \
          MULTIPLY(row3, FIX_3_072711026_MINUS_2_562915447); \
    /* now we can use q1 (reloadable constants have been used up) */ \
    q1 = q3 + q2; \
    q4 += MULTIPLY(row7, FIX_0_298631336_MINUS_0_899976223) + \
          MULTIPLY(row1, -FIX_0_899976223); \
    q5 = q7; \
    q1 = q1 + q6; \
    q7 += MULTIPLY(row7, -FIX_0_899976223) + \
          MULTIPLY(row1, FIX_1_501321110_MINUS_0_899976223); \
    \
    /* (tmp11 + tmp2) has been calculated (out_row1 before descale) */ \
    tmp11_plus_tmp2 = q1; \
    row1 = 0; \
    \
    q1 = q1 - q6; \
    q5 += MULTIPLY(row5, FIX_2_053119869_MINUS_2_562915447) + \
          MULTIPLY(row3, -FIX_2_562915447); \
    q1 = q1 - q6; \
    q6 = MULTIPLY(row2, FIX_0_541196100_PLUS_0_765366865) + \
         MULTIPLY(row6, FIX_0_541196100); \
    q3 = q3 - q2; \
    \
    /* (tmp11 - tmp2) has been calculated (out_row6 before descale) */ \
    tmp11_minus_tmp2 = q1; \
    \
    q1 = ((JLONG)row0 + (JLONG)row4) << 13; \
    q2 = q1 + q6; \
    q1 = q1 - q6; \
    \
    /* pick up the results */ \
    tmp0  = q4; \
    tmp1  = q5; \
    tmp2  = (tmp11_plus_tmp2 - tmp11_minus_tmp2) / 2; \
    tmp3  = q7; \
    tmp10 = q2; \
    tmp11 = (tmp11_plus_tmp2 + tmp11_minus_tmp2) / 2; \
    tmp12 = q3; \
    tmp13 = q1; \
}

#define XFIX_0_899976223                    d0[0]
#define XFIX_0_541196100                    d0[1]
#define XFIX_2_562915447                    d0[2]
#define XFIX_0_298631336_MINUS_0_899976223  d0[3]
#define XFIX_1_501321110_MINUS_0_899976223  d1[0]
#define XFIX_2_053119869_MINUS_2_562915447  d1[1]
#define XFIX_0_541196100_PLUS_0_765366865   d1[2]
#define XFIX_1_175875602                    d1[3]
#define XFIX_1_175875602_MINUS_0_390180644  d2[0]
#define XFIX_0_541196100_MINUS_1_847759065  d2[1]
#define XFIX_3_072711026_MINUS_2_562915447  d2[2]
#define XFIX_1_175875602_MINUS_1_961570560  d2[3]

.balign 16
jsimd_idct_islow_neon_consts:
  .short FIX_0_899976223                    /* d0[0] */
  .short FIX_0_541196100                    /* d0[1] */
  .short FIX_2_562915447                    /* d0[2] */
  .short FIX_0_298631336_MINUS_0_899976223  /* d0[3] */
  .short FIX_1_501321110_MINUS_0_899976223  /* d1[0] */
  .short FIX_2_053119869_MINUS_2_562915447  /* d1[1] */
  .short FIX_0_541196100_PLUS_0_765366865   /* d1[2] */
  .short FIX_1_175875602                    /* d1[3] */
  /* reloadable constants */
  .short FIX_1_175875602_MINUS_0_390180644  /* d2[0] */
  .short FIX_0_541196100_MINUS_1_847759065  /* d2[1] */
  .short FIX_3_072711026_MINUS_2_562915447  /* d2[2] */
  .short FIX_1_175875602_MINUS_1_961570560  /* d2[3] */

asm_function jsimd_idct_islow_neon

    DCT_TABLE       .req r0
    COEF_BLOCK      .req r1
    OUTPUT_BUF      .req r2
    OUTPUT_COL      .req r3
    TMP1            .req r0
    TMP2            .req r1
    TMP3            .req r2
    TMP4            .req ip

    ROW0L           .req d16
    ROW0R           .req d17
    ROW1L           .req d18
    ROW1R           .req d19
    ROW2L           .req d20
    ROW2R           .req d21
    ROW3L           .req d22
    ROW3R           .req d23
    ROW4L           .req d24
    ROW4R           .req d25
    ROW5L           .req d26
    ROW5R           .req d27
    ROW6L           .req d28
    ROW6R           .req d29
    ROW7L           .req d30
    ROW7R           .req d31

    /* Load and dequantize coefficients into NEON registers
     * with the following allocation:
     *       0 1 2 3 | 4 5 6 7
     *      ---------+--------
     *   0 | d16     | d17     ( q8  )
     *   1 | d18     | d19     ( q9  )
     *   2 | d20     | d21     ( q10 )
     *   3 | d22     | d23     ( q11 )
     *   4 | d24     | d25     ( q12 )
     *   5 | d26     | d27     ( q13 )
     *   6 | d28     | d29     ( q14 )
     *   7 | d30     | d31     ( q15 )
     */
    adr             ip, jsimd_idct_islow_neon_consts
    vld1.16         {d16, d17, d18, d19}, [COEF_BLOCK, :128]!
    vld1.16         {d0, d1, d2, d3}, [DCT_TABLE, :128]!
    vld1.16         {d20, d21, d22, d23}, [COEF_BLOCK, :128]!
    vmul.s16        q8, q8, q0
    vld1.16         {d4, d5, d6, d7}, [DCT_TABLE, :128]!
    vmul.s16        q9, q9, q1
    vld1.16         {d24, d25, d26, d27}, [COEF_BLOCK, :128]!
    vmul.s16        q10, q10, q2
    vld1.16         {d0, d1, d2, d3}, [DCT_TABLE, :128]!
    vmul.s16        q11, q11, q3
    vld1.16         {d28, d29, d30, d31}, [COEF_BLOCK, :128]
    vmul.s16        q12, q12, q0
    vld1.16         {d4, d5, d6, d7}, [DCT_TABLE, :128]!
    vmul.s16        q14, q14, q2
    vmul.s16        q13, q13, q1
    vld1.16         {d0, d1, d2, d3}, [ip, :128]  /* load constants */
    add             ip, ip, #16
    vmul.s16        q15, q15, q3
    vpush           {d8-d15}                      /* save NEON registers */
    /* 1-D IDCT, pass 1, left 4x8 half */
    vadd.s16        d4, ROW7L, ROW3L
    vadd.s16        d5, ROW5L, ROW1L
    vmull.s16       q6, d4, XFIX_1_175875602_MINUS_1_961570560
    vmlal.s16       q6, d5, XFIX_1_175875602
    vmull.s16       q7, d4, XFIX_1_175875602
      /* Check for the zero coefficients in the right 4x8 half */
      push            {r4, r5}
    vmlal.s16       q7, d5, XFIX_1_175875602_MINUS_0_390180644
    vsubl.s16       q3, ROW0L, ROW4L
      ldrd            r4, [COEF_BLOCK, #(-96 + 2 * (4 + 1 * 8))]
    vmull.s16       q2, ROW2L, XFIX_0_541196100
    vmlal.s16       q2, ROW6L, XFIX_0_541196100_MINUS_1_847759065
      orr             r0, r4, r5
    vmov            q4, q6
    vmlsl.s16       q6, ROW5L, XFIX_2_562915447
      ldrd            r4, [COEF_BLOCK, #(-96 + 2 * (4 + 2 * 8))]
    vmlal.s16       q6, ROW3L, XFIX_3_072711026_MINUS_2_562915447
    vshl.s32        q3, q3, #13
      orr             r0, r0, r4
    vmlsl.s16       q4, ROW1L, XFIX_0_899976223
      orr             r0, r0, r5
    vadd.s32        q1, q3, q2
      ldrd            r4, [COEF_BLOCK, #(-96 + 2 * (4 + 3 * 8))]
    vmov            q5, q7
    vadd.s32        q1, q1, q6
      orr             r0, r0, r4
    vmlsl.s16       q7, ROW7L, XFIX_0_899976223
      orr             r0, r0, r5
    vmlal.s16       q7, ROW1L, XFIX_1_501321110_MINUS_0_899976223
    vrshrn.s32      ROW1L, q1, #11
      ldrd            r4, [COEF_BLOCK, #(-96 + 2 * (4 + 4 * 8))]
    vsub.s32        q1, q1, q6
    vmlal.s16       q5, ROW5L, XFIX_2_053119869_MINUS_2_562915447
      orr             r0, r0, r4
    vmlsl.s16       q5, ROW3L, XFIX_2_562915447
      orr             r0, r0, r5
    vsub.s32        q1, q1, q6
    vmull.s16       q6, ROW2L, XFIX_0_541196100_PLUS_0_765366865
      ldrd            r4, [COEF_BLOCK, #(-96 + 2 * (4 + 5 * 8))]
    vmlal.s16       q6, ROW6L, XFIX_0_541196100
    vsub.s32        q3, q3, q2
      orr             r0, r0, r4
    vrshrn.s32      ROW6L, q1, #11
      orr             r0, r0, r5
    vadd.s32        q1, q3, q5
      ldrd            r4, [COEF_BLOCK, #(-96 + 2 * (4 + 6 * 8))]
    vsub.s32        q3, q3, q5
    vaddl.s16       q5, ROW0L, ROW4L
      orr             r0, r0, r4
    vrshrn.s32      ROW2L, q1, #11
      orr             r0, r0, r5
    vrshrn.s32      ROW5L, q3, #11
      ldrd            r4, [COEF_BLOCK, #(-96 + 2 * (4 + 7 * 8))]
    vshl.s32        q5, q5, #13
    vmlal.s16       q4, ROW7L, XFIX_0_298631336_MINUS_0_899976223
      orr             r0, r0, r4
    vadd.s32        q2, q5, q6
      orrs            r0, r0, r5
    vsub.s32        q1, q5, q6
    vadd.s32        q6, q2, q7
      ldrd            r4, [COEF_BLOCK, #(-96 + 2 * (4 + 0 * 8))]
    vsub.s32        q2, q2, q7
    vadd.s32        q5, q1, q4
      orr             r0, r4, r5
    vsub.s32        q3, q1, q4
      pop             {r4, r5}
    vrshrn.s32      ROW7L, q2, #11
    vrshrn.s32      ROW3L, q5, #11
    vrshrn.s32      ROW0L, q6, #11
    vrshrn.s32      ROW4L, q3, #11

      beq             3f  /* Go to do some special handling for the sparse
                             right 4x8 half */

    /* 1-D IDCT, pass 1, right 4x8 half */
    vld1.s16        {d2}, [ip, :64]  /* reload constants */
    vadd.s16        d10, ROW7R, ROW3R
    vadd.s16        d8, ROW5R, ROW1R
      /* Transpose left 4x8 half */
      vtrn.16         ROW6L, ROW7L
    vmull.s16       q6, d10, XFIX_1_175875602_MINUS_1_961570560
    vmlal.s16       q6, d8, XFIX_1_175875602
      vtrn.16         ROW2L, ROW3L
    vmull.s16       q7, d10, XFIX_1_175875602
    vmlal.s16       q7, d8, XFIX_1_175875602_MINUS_0_390180644
      vtrn.16         ROW0L, ROW1L
    vsubl.s16       q3, ROW0R, ROW4R
    vmull.s16       q2, ROW2R, XFIX_0_541196100
    vmlal.s16       q2, ROW6R, XFIX_0_541196100_MINUS_1_847759065
      vtrn.16         ROW4L, ROW5L
    vmov            q4, q6
    vmlsl.s16       q6, ROW5R, XFIX_2_562915447
    vmlal.s16       q6, ROW3R, XFIX_3_072711026_MINUS_2_562915447
      vtrn.32         ROW1L, ROW3L
    vshl.s32        q3, q3, #13
    vmlsl.s16       q4, ROW1R, XFIX_0_899976223
      vtrn.32         ROW4L, ROW6L
    vadd.s32        q1, q3, q2
    vmov            q5, q7
    vadd.s32        q1, q1, q6
      vtrn.32         ROW0L, ROW2L
    vmlsl.s16       q7, ROW7R, XFIX_0_899976223
    vmlal.s16       q7, ROW1R, XFIX_1_501321110_MINUS_0_899976223
    vrshrn.s32      ROW1R, q1, #11
      vtrn.32         ROW5L, ROW7L
    vsub.s32        q1, q1, q6
    vmlal.s16       q5, ROW5R, XFIX_2_053119869_MINUS_2_562915447
    vmlsl.s16       q5, ROW3R, XFIX_2_562915447
    vsub.s32        q1, q1, q6
    vmull.s16       q6, ROW2R, XFIX_0_541196100_PLUS_0_765366865
    vmlal.s16       q6, ROW6R, XFIX_0_541196100
    vsub.s32        q3, q3, q2
    vrshrn.s32      ROW6R, q1, #11
    vadd.s32        q1, q3, q5
    vsub.s32        q3, q3, q5
    vaddl.s16       q5, ROW0R, ROW4R
    vrshrn.s32      ROW2R, q1, #11
    vrshrn.s32      ROW5R, q3, #11
    vshl.s32        q5, q5, #13
    vmlal.s16       q4, ROW7R, XFIX_0_298631336_MINUS_0_899976223
    vadd.s32        q2, q5, q6
    vsub.s32        q1, q5, q6
    vadd.s32        q6, q2, q7
    vsub.s32        q2, q2, q7
    vadd.s32        q5, q1, q4
    vsub.s32        q3, q1, q4
    vrshrn.s32      ROW7R, q2, #11
    vrshrn.s32      ROW3R, q5, #11
    vrshrn.s32      ROW0R, q6, #11
    vrshrn.s32      ROW4R, q3, #11
    /* Transpose right 4x8 half */
    vtrn.16         ROW6R, ROW7R
    vtrn.16         ROW2R, ROW3R
    vtrn.16         ROW0R, ROW1R
    vtrn.16         ROW4R, ROW5R
    vtrn.32         ROW1R, ROW3R
    vtrn.32         ROW4R, ROW6R
    vtrn.32         ROW0R, ROW2R
    vtrn.32         ROW5R, ROW7R

1:  /* 1-D IDCT, pass 2 (normal variant), left 4x8 half */
    vld1.s16        {d2}, [ip, :64]               /* reload constants */
    vmull.s16       q6, ROW1R, XFIX_1_175875602   /* ROW5L <-> ROW1R */
    vmlal.s16       q6, ROW1L, XFIX_1_175875602
    vmlal.s16       q6, ROW3R, XFIX_1_175875602_MINUS_1_961570560  /* ROW7L <-> ROW3R */
    vmlal.s16       q6, ROW3L, XFIX_1_175875602_MINUS_1_961570560
    vmull.s16       q7, ROW3R, XFIX_1_175875602   /* ROW7L <-> ROW3R */
    vmlal.s16       q7, ROW3L, XFIX_1_175875602
    vmlal.s16       q7, ROW1R, XFIX_1_175875602_MINUS_0_390180644  /* ROW5L <-> ROW1R */
    vmlal.s16       q7, ROW1L, XFIX_1_175875602_MINUS_0_390180644
    vsubl.s16       q3, ROW0L, ROW0R              /* ROW4L <-> ROW0R */
    vmull.s16       q2, ROW2L, XFIX_0_541196100
    vmlal.s16       q2, ROW2R, XFIX_0_541196100_MINUS_1_847759065  /* ROW6L <-> ROW2R */
    vmov            q4, q6
    vmlsl.s16       q6, ROW1R, XFIX_2_562915447   /* ROW5L <-> ROW1R */
    vmlal.s16       q6, ROW3L, XFIX_3_072711026_MINUS_2_562915447
    vshl.s32        q3, q3, #13
    vmlsl.s16       q4, ROW1L, XFIX_0_899976223
    vadd.s32        q1, q3, q2
    vmov            q5, q7
    vadd.s32        q1, q1, q6
    vmlsl.s16       q7, ROW3R, XFIX_0_899976223   /* ROW7L <-> ROW3R */
    vmlal.s16       q7, ROW1L, XFIX_1_501321110_MINUS_0_899976223
    vshrn.s32       ROW1L, q1, #16
    vsub.s32        q1, q1, q6
    vmlal.s16       q5, ROW1R, XFIX_2_053119869_MINUS_2_562915447  /* ROW5L <-> ROW1R */
    vmlsl.s16       q5, ROW3L, XFIX_2_562915447
    vsub.s32        q1, q1, q6
    vmull.s16       q6, ROW2L, XFIX_0_541196100_PLUS_0_765366865
    vmlal.s16       q6, ROW2R, XFIX_0_541196100   /* ROW6L <-> ROW2R */
    vsub.s32        q3, q3, q2
    vshrn.s32       ROW2R, q1, #16                /* ROW6L <-> ROW2R */
    vadd.s32        q1, q3, q5
    vsub.s32        q3, q3, q5
    vaddl.s16       q5, ROW0L, ROW0R              /* ROW4L <-> ROW0R */
    vshrn.s32       ROW2L, q1, #16
    vshrn.s32       ROW1R, q3, #16                /* ROW5L <-> ROW1R */
    vshl.s32        q5, q5, #13
    vmlal.s16       q4, ROW3R, XFIX_0_298631336_MINUS_0_899976223  /* ROW7L <-> ROW3R */
    vadd.s32        q2, q5, q6
    vsub.s32        q1, q5, q6
    vadd.s32        q6, q2, q7
    vsub.s32        q2, q2, q7
    vadd.s32        q5, q1, q4
    vsub.s32        q3, q1, q4
    vshrn.s32       ROW3R, q2, #16                /* ROW7L <-> ROW3R */
    vshrn.s32       ROW3L, q5, #16
    vshrn.s32       ROW0L, q6, #16
    vshrn.s32       ROW0R, q3, #16                /* ROW4L <-> ROW0R */
    /* 1-D IDCT, pass 2, right 4x8 half */
    vld1.s16        {d2}, [ip, :64]               /* reload constants */
    vmull.s16       q6, ROW5R, XFIX_1_175875602
    vmlal.s16       q6, ROW5L, XFIX_1_175875602   /* ROW5L <-> ROW1R */
    vmlal.s16       q6, ROW7R, XFIX_1_175875602_MINUS_1_961570560
    vmlal.s16       q6, ROW7L, XFIX_1_175875602_MINUS_1_961570560  /* ROW7L <-> ROW3R */
    vmull.s16       q7, ROW7R, XFIX_1_175875602
    vmlal.s16       q7, ROW7L, XFIX_1_175875602   /* ROW7L <-> ROW3R */
    vmlal.s16       q7, ROW5R, XFIX_1_175875602_MINUS_0_390180644
    vmlal.s16       q7, ROW5L, XFIX_1_175875602_MINUS_0_390180644  /* ROW5L <-> ROW1R */
    vsubl.s16       q3, ROW4L, ROW4R              /* ROW4L <-> ROW0R */
    vmull.s16       q2, ROW6L, XFIX_0_541196100   /* ROW6L <-> ROW2R */
    vmlal.s16       q2, ROW6R, XFIX_0_541196100_MINUS_1_847759065
    vmov            q4, q6
    vmlsl.s16       q6, ROW5R, XFIX_2_562915447
    vmlal.s16       q6, ROW7L, XFIX_3_072711026_MINUS_2_562915447  /* ROW7L <-> ROW3R */
    vshl.s32        q3, q3, #13
    vmlsl.s16       q4, ROW5L, XFIX_0_899976223   /* ROW5L <-> ROW1R */
    vadd.s32        q1, q3, q2
    vmov            q5, q7
    vadd.s32        q1, q1, q6
    vmlsl.s16       q7, ROW7R, XFIX_0_899976223
    vmlal.s16       q7, ROW5L, XFIX_1_501321110_MINUS_0_899976223  /* ROW5L <-> ROW1R */
    vshrn.s32       ROW5L, q1, #16                /* ROW5L <-> ROW1R */
    vsub.s32        q1, q1, q6
    vmlal.s16       q5, ROW5R, XFIX_2_053119869_MINUS_2_562915447
    vmlsl.s16       q5, ROW7L, XFIX_2_562915447   /* ROW7L <-> ROW3R */
    vsub.s32        q1, q1, q6
    vmull.s16       q6, ROW6L, XFIX_0_541196100_PLUS_0_765366865  /* ROW6L <-> ROW2R */
    vmlal.s16       q6, ROW6R, XFIX_0_541196100
    vsub.s32        q3, q3, q2
    vshrn.s32       ROW6R, q1, #16
    vadd.s32        q1, q3, q5
    vsub.s32        q3, q3, q5
    vaddl.s16       q5, ROW4L, ROW4R              /* ROW4L <-> ROW0R */
    vshrn.s32       ROW6L, q1, #16                /* ROW6L <-> ROW2R */
    vshrn.s32       ROW5R, q3, #16
    vshl.s32        q5, q5, #13
    vmlal.s16       q4, ROW7R, XFIX_0_298631336_MINUS_0_899976223
    vadd.s32        q2, q5, q6
    vsub.s32        q1, q5, q6
    vadd.s32        q6, q2, q7
    vsub.s32        q2, q2, q7
    vadd.s32        q5, q1, q4
    vsub.s32        q3, q1, q4
    vshrn.s32       ROW7R, q2, #16
    vshrn.s32       ROW7L, q5, #16                /* ROW7L <-> ROW3R */
    vshrn.s32       ROW4L, q6, #16                /* ROW4L <-> ROW0R */
    vshrn.s32       ROW4R, q3, #16

2:  /* Descale to 8-bit and range limit */
    vqrshrn.s16     d16, q8, #2
    vqrshrn.s16     d17, q9, #2
    vqrshrn.s16     d18, q10, #2
    vqrshrn.s16     d19, q11, #2
    vpop            {d8-d15}                      /* restore NEON registers */
    vqrshrn.s16     d20, q12, #2
      /* Transpose the final 8-bit samples and do signed->unsigned conversion */
      vtrn.16         q8, q9
    vqrshrn.s16     d21, q13, #2
    vqrshrn.s16     d22, q14, #2
      vmov.u8         q0, #(CENTERJSAMPLE)
    vqrshrn.s16     d23, q15, #2
      vtrn.8          d16, d17
      vtrn.8          d18, d19
      vadd.u8         q8, q8, q0
      vadd.u8         q9, q9, q0
      vtrn.16         q10, q11
        /* Store results to the output buffer */
        ldmia           OUTPUT_BUF!, {TMP1, TMP2}
        add             TMP1, TMP1, OUTPUT_COL
        add             TMP2, TMP2, OUTPUT_COL
        vst1.8          {d16}, [TMP1]
      vtrn.8          d20, d21
        vst1.8          {d17}, [TMP2]
        ldmia           OUTPUT_BUF!, {TMP1, TMP2}
        add             TMP1, TMP1, OUTPUT_COL
        add             TMP2, TMP2, OUTPUT_COL
        vst1.8          {d18}, [TMP1]
      vadd.u8         q10, q10, q0
        vst1.8          {d19}, [TMP2]
        ldmia           OUTPUT_BUF, {TMP1, TMP2, TMP3, TMP4}
        add             TMP1, TMP1, OUTPUT_COL
        add             TMP2, TMP2, OUTPUT_COL
        add             TMP3, TMP3, OUTPUT_COL
        add             TMP4, TMP4, OUTPUT_COL
      vtrn.8          d22, d23
        vst1.8          {d20}, [TMP1]
      vadd.u8         q11, q11, q0
        vst1.8          {d21}, [TMP2]
        vst1.8          {d22}, [TMP3]
        vst1.8          {d23}, [TMP4]
    bx              lr

3:  /* Left 4x8 half is done, right 4x8 half contains mostly zeros */

    /* Transpose left 4x8 half */
    vtrn.16         ROW6L, ROW7L
    vtrn.16         ROW2L, ROW3L
    vtrn.16         ROW0L, ROW1L
    vtrn.16         ROW4L, ROW5L
    vshl.s16        ROW0R, ROW0R, #2  /* PASS1_BITS */
    vtrn.32         ROW1L, ROW3L
    vtrn.32         ROW4L, ROW6L
    vtrn.32         ROW0L, ROW2L
    vtrn.32         ROW5L, ROW7L

    cmp             r0, #0
    beq             4f  /* Right 4x8 half has all zeros, go to 'sparse' second
                           pass */

    /* Only row 0 is non-zero for the right 4x8 half  */
    vdup.s16        ROW1R, ROW0R[1]
    vdup.s16        ROW2R, ROW0R[2]
    vdup.s16        ROW3R, ROW0R[3]
    vdup.s16        ROW4R, ROW0R[0]
    vdup.s16        ROW5R, ROW0R[1]
    vdup.s16        ROW6R, ROW0R[2]
    vdup.s16        ROW7R, ROW0R[3]
    vdup.s16        ROW0R, ROW0R[0]
    b               1b  /* Go to 'normal' second pass */

4:  /* 1-D IDCT, pass 2 (sparse variant with zero rows 4-7), left 4x8 half */
    vld1.s16        {d2}, [ip, :64]               /* reload constants */
    vmull.s16       q6, ROW1L, XFIX_1_175875602
    vmlal.s16       q6, ROW3L, XFIX_1_175875602_MINUS_1_961570560
    vmull.s16       q7, ROW3L, XFIX_1_175875602
    vmlal.s16       q7, ROW1L, XFIX_1_175875602_MINUS_0_390180644
    vmull.s16       q2, ROW2L, XFIX_0_541196100
    vshll.s16       q3, ROW0L, #13
    vmov            q4, q6
    vmlal.s16       q6, ROW3L, XFIX_3_072711026_MINUS_2_562915447
    vmlsl.s16       q4, ROW1L, XFIX_0_899976223
    vadd.s32        q1, q3, q2
    vmov            q5, q7
    vmlal.s16       q7, ROW1L, XFIX_1_501321110_MINUS_0_899976223
    vadd.s32        q1, q1, q6
    vadd.s32        q6, q6, q6
    vmlsl.s16       q5, ROW3L, XFIX_2_562915447
    vshrn.s32       ROW1L, q1, #16
    vsub.s32        q1, q1, q6
    vmull.s16       q6, ROW2L, XFIX_0_541196100_PLUS_0_765366865
    vsub.s32        q3, q3, q2
    vshrn.s32       ROW2R, q1, #16                /* ROW6L <-> ROW2R */
    vadd.s32        q1, q3, q5
    vsub.s32        q3, q3, q5
    vshll.s16       q5, ROW0L, #13
    vshrn.s32       ROW2L, q1, #16
    vshrn.s32       ROW1R, q3, #16                /* ROW5L <-> ROW1R */
    vadd.s32        q2, q5, q6
    vsub.s32        q1, q5, q6
    vadd.s32        q6, q2, q7
    vsub.s32        q2, q2, q7
    vadd.s32        q5, q1, q4
    vsub.s32        q3, q1, q4
    vshrn.s32       ROW3R, q2, #16                /* ROW7L <-> ROW3R */
    vshrn.s32       ROW3L, q5, #16
    vshrn.s32       ROW0L, q6, #16
    vshrn.s32       ROW0R, q3, #16                /* ROW4L <-> ROW0R */
    /* 1-D IDCT, pass 2 (sparse variant with zero rows 4-7), right 4x8 half */
    vld1.s16        {d2}, [ip, :64]               /* reload constants */
    vmull.s16       q6, ROW5L, XFIX_1_175875602
    vmlal.s16       q6, ROW7L, XFIX_1_175875602_MINUS_1_961570560
    vmull.s16       q7, ROW7L, XFIX_1_175875602
    vmlal.s16       q7, ROW5L, XFIX_1_175875602_MINUS_0_390180644
    vmull.s16       q2, ROW6L, XFIX_0_541196100
    vshll.s16       q3, ROW4L, #13
    vmov            q4, q6
    vmlal.s16       q6, ROW7L, XFIX_3_072711026_MINUS_2_562915447
    vmlsl.s16       q4, ROW5L, XFIX_0_899976223
    vadd.s32        q1, q3, q2
    vmov            q5, q7
    vmlal.s16       q7, ROW5L, XFIX_1_501321110_MINUS_0_899976223
    vadd.s32        q1, q1, q6
    vadd.s32        q6, q6, q6
    vmlsl.s16       q5, ROW7L, XFIX_2_562915447
    vshrn.s32       ROW5L, q1, #16                /* ROW5L <-> ROW1R */
    vsub.s32        q1, q1, q6
    vmull.s16       q6, ROW6L, XFIX_0_541196100_PLUS_0_765366865
    vsub.s32        q3, q3, q2
    vshrn.s32       ROW6R, q1, #16
    vadd.s32        q1, q3, q5
    vsub.s32        q3, q3, q5
    vshll.s16       q5, ROW4L, #13
    vshrn.s32       ROW6L, q1, #16                /* ROW6L <-> ROW2R */
    vshrn.s32       ROW5R, q3, #16
    vadd.s32        q2, q5, q6
    vsub.s32        q1, q5, q6
    vadd.s32        q6, q2, q7
    vsub.s32        q2, q2, q7
    vadd.s32        q5, q1, q4
    vsub.s32        q3, q1, q4
    vshrn.s32       ROW7R, q2, #16
    vshrn.s32       ROW7L, q5, #16                /* ROW7L <-> ROW3R */
    vshrn.s32       ROW4L, q6, #16                /* ROW4L <-> ROW0R */
    vshrn.s32       ROW4R, q3, #16
    b               2b                            /* Go to epilogue */

    .unreq          DCT_TABLE
    .unreq          COEF_BLOCK
    .unreq          OUTPUT_BUF
    .unreq          OUTPUT_COL
    .unreq          TMP1
    .unreq          TMP2
    .unreq          TMP3
    .unreq          TMP4

    .unreq          ROW0L
    .unreq          ROW0R
    .unreq          ROW1L
    .unreq          ROW1R
    .unreq          ROW2L
    .unreq          ROW2R
    .unreq          ROW3L
    .unreq          ROW3R
    .unreq          ROW4L
    .unreq          ROW4R
    .unreq          ROW5L
    .unreq          ROW5R
    .unreq          ROW6L
    .unreq          ROW6R
    .unreq          ROW7L
    .unreq          ROW7R


/*****************************************************************************/

/*
 * jsimd_idct_ifast_neon
 *
 * This function contains a fast, not so accurate integer implementation of
 * the inverse DCT (Discrete Cosine Transform). It uses the same calculations
 * and produces exactly the same output as IJG's original 'jpeg_idct_ifast'
 * function from jidctfst.c
 *
 * Normally 1-D AAN DCT needs 5 multiplications and 29 additions.
 * But in ARM NEON case some extra additions are required because VQDMULH
 * instruction can't handle the constants larger than 1. So the expressions
 * like "x * 1.082392200" have to be converted to "x * 0.082392200 + x",
 * which introduces an extra addition. Overall, there are 6 extra additions
 * per 1-D IDCT pass, totalling to 5 VQDMULH and 35 VADD/VSUB instructions.
 */

#define XFIX_1_082392200  d0[0]
#define XFIX_1_414213562  d0[1]
#define XFIX_1_847759065  d0[2]
#define XFIX_2_613125930  d0[3]

.balign 16
jsimd_idct_ifast_neon_consts:
  .short (277 * 128 - 256 * 128)  /* XFIX_1_082392200 */
  .short (362 * 128 - 256 * 128)  /* XFIX_1_414213562 */
  .short (473 * 128 - 256 * 128)  /* XFIX_1_847759065 */
  .short (669 * 128 - 512 * 128)  /* XFIX_2_613125930 */

asm_function jsimd_idct_ifast_neon

    DCT_TABLE       .req r0
    COEF_BLOCK      .req r1
    OUTPUT_BUF      .req r2
    OUTPUT_COL      .req r3
    TMP1            .req r0
    TMP2            .req r1
    TMP3            .req r2
    TMP4            .req ip

    /* Load and dequantize coefficients into NEON registers
     * with the following allocation:
     *       0 1 2 3 | 4 5 6 7
     *      ---------+--------
     *   0 | d16     | d17     ( q8  )
     *   1 | d18     | d19     ( q9  )
     *   2 | d20     | d21     ( q10 )
     *   3 | d22     | d23     ( q11 )
     *   4 | d24     | d25     ( q12 )
     *   5 | d26     | d27     ( q13 )
     *   6 | d28     | d29     ( q14 )
     *   7 | d30     | d31     ( q15 )
     */
    adr             ip, jsimd_idct_ifast_neon_consts
    vld1.16         {d16, d17, d18, d19}, [COEF_BLOCK, :128]!
    vld1.16         {d0, d1, d2, d3}, [DCT_TABLE, :128]!
    vld1.16         {d20, d21, d22, d23}, [COEF_BLOCK, :128]!
    vmul.s16        q8, q8, q0
    vld1.16         {d4, d5, d6, d7}, [DCT_TABLE, :128]!
    vmul.s16        q9, q9, q1
    vld1.16         {d24, d25, d26, d27}, [COEF_BLOCK, :128]!
    vmul.s16        q10, q10, q2
    vld1.16         {d0, d1, d2, d3}, [DCT_TABLE, :128]!
    vmul.s16        q11, q11, q3
    vld1.16         {d28, d29, d30, d31}, [COEF_BLOCK, :128]
    vmul.s16        q12, q12, q0
    vld1.16         {d4, d5, d6, d7}, [DCT_TABLE, :128]!
    vmul.s16        q14, q14, q2
    vmul.s16        q13, q13, q1
    vld1.16         {d0}, [ip, :64]  /* load constants */
    vmul.s16        q15, q15, q3
    vpush           {d8-d13}         /* save NEON registers */
    /* 1-D IDCT, pass 1 */
    vsub.s16        q2, q10, q14
    vadd.s16        q14, q10, q14
    vsub.s16        q1, q11, q13
    vadd.s16        q13, q11, q13
    vsub.s16        q5, q9, q15
    vadd.s16        q15, q9, q15
    vqdmulh.s16     q4, q2, XFIX_1_414213562
    vqdmulh.s16     q6, q1, XFIX_2_613125930
    vadd.s16        q3, q1, q1
    vsub.s16        q1, q5, q1
    vadd.s16        q10, q2, q4
    vqdmulh.s16     q4, q1, XFIX_1_847759065
    vsub.s16        q2, q15, q13
    vadd.s16        q3, q3, q6
    vqdmulh.s16     q6, q2, XFIX_1_414213562
    vadd.s16        q1, q1, q4
    vqdmulh.s16     q4, q5, XFIX_1_082392200
    vsub.s16        q10, q10, q14
    vadd.s16        q2, q2, q6
    vsub.s16        q6, q8, q12
    vadd.s16        q12, q8, q12
    vadd.s16        q9, q5, q4
    vadd.s16        q5, q6, q10
    vsub.s16        q10, q6, q10
    vadd.s16        q6, q15, q13
    vadd.s16        q8, q12, q14
    vsub.s16        q3, q6, q3
    vsub.s16        q12, q12, q14
    vsub.s16        q3, q3, q1
    vsub.s16        q1, q9, q1
    vadd.s16        q2, q3, q2
    vsub.s16        q15, q8, q6
    vadd.s16        q1, q1, q2
    vadd.s16        q8, q8, q6
    vadd.s16        q14, q5, q3
    vsub.s16        q9, q5, q3
    vsub.s16        q13, q10, q2
    vadd.s16        q10, q10, q2
      /* Transpose */
      vtrn.16         q8, q9
    vsub.s16        q11, q12, q1
      vtrn.16         q14, q15
    vadd.s16        q12, q12, q1
      vtrn.16         q10, q11
      vtrn.16         q12, q13
      vtrn.32         q9, q11
      vtrn.32         q12, q14
      vtrn.32         q8, q10
      vtrn.32         q13, q15
      vswp            d28, d21
      vswp            d26, d19
    /* 1-D IDCT, pass 2 */
    vsub.s16        q2, q10, q14
      vswp            d30, d23
    vadd.s16        q14, q10, q14
      vswp            d24, d17
    vsub.s16        q1, q11, q13
    vadd.s16        q13, q11, q13
    vsub.s16        q5, q9, q15
    vadd.s16        q15, q9, q15
    vqdmulh.s16     q4, q2, XFIX_1_414213562
    vqdmulh.s16     q6, q1, XFIX_2_613125930
    vadd.s16        q3, q1, q1
    vsub.s16        q1, q5, q1
    vadd.s16        q10, q2, q4
    vqdmulh.s16     q4, q1, XFIX_1_847759065
    vsub.s16        q2, q15, q13
    vadd.s16        q3, q3, q6
    vqdmulh.s16     q6, q2, XFIX_1_414213562
    vadd.s16        q1, q1, q4
    vqdmulh.s16     q4, q5, XFIX_1_082392200
    vsub.s16        q10, q10, q14
    vadd.s16        q2, q2, q6
    vsub.s16        q6, q8, q12
    vadd.s16        q12, q8, q12
    vadd.s16        q9, q5, q4
    vadd.s16        q5, q6, q10
    vsub.s16        q10, q6, q10
    vadd.s16        q6, q15, q13
    vadd.s16        q8, q12, q14
    vsub.s16        q3, q6, q3
    vsub.s16        q12, q12, q14
    vsub.s16        q3, q3, q1
    vsub.s16        q1, q9, q1
    vadd.s16        q2, q3, q2
    vsub.s16        q15, q8, q6
    vadd.s16        q1, q1, q2
    vadd.s16        q8, q8, q6
    vadd.s16        q14, q5, q3
    vsub.s16        q9, q5, q3
    vsub.s16        q13, q10, q2
    vpop            {d8-d13}      /* restore NEON registers */
    vadd.s16        q10, q10, q2
    vsub.s16        q11, q12, q1
    vadd.s16        q12, q12, q1
    /* Descale to 8-bit and range limit */
    vmov.u8         q0, #0x80
    vqshrn.s16      d16, q8, #5
    vqshrn.s16      d17, q9, #5
    vqshrn.s16      d18, q10, #5
    vqshrn.s16      d19, q11, #5
    vqshrn.s16      d20, q12, #5
    vqshrn.s16      d21, q13, #5
    vqshrn.s16      d22, q14, #5
    vqshrn.s16      d23, q15, #5
    vadd.u8         q8, q8, q0
    vadd.u8         q9, q9, q0
    vadd.u8         q10, q10, q0
    vadd.u8         q11, q11, q0
    /* Transpose the final 8-bit samples */
    vtrn.16         q8, q9
    vtrn.16         q10, q11
    vtrn.32         q8, q10
    vtrn.32         q9, q11
    vtrn.8          d16, d17
    vtrn.8          d18, d19
      /* Store results to the output buffer */
      ldmia           OUTPUT_BUF!, {TMP1, TMP2}
      add             TMP1, TMP1, OUTPUT_COL
      add             TMP2, TMP2, OUTPUT_COL
      vst1.8          {d16}, [TMP1]
      vst1.8          {d17}, [TMP2]
      ldmia           OUTPUT_BUF!, {TMP1, TMP2}
      add             TMP1, TMP1, OUTPUT_COL
      add             TMP2, TMP2, OUTPUT_COL
      vst1.8          {d18}, [TMP1]
    vtrn.8          d20, d21
      vst1.8          {d19}, [TMP2]
      ldmia           OUTPUT_BUF, {TMP1, TMP2, TMP3, TMP4}
      add             TMP1, TMP1, OUTPUT_COL
      add             TMP2, TMP2, OUTPUT_COL
      add             TMP3, TMP3, OUTPUT_COL
      add             TMP4, TMP4, OUTPUT_COL
      vst1.8          {d20}, [TMP1]
    vtrn.8          d22, d23
      vst1.8          {d21}, [TMP2]
      vst1.8          {d22}, [TMP3]
      vst1.8          {d23}, [TMP4]
    bx              lr

    .unreq          DCT_TABLE
    .unreq          COEF_BLOCK
    .unreq          OUTPUT_BUF
    .unreq          OUTPUT_COL
    .unreq          TMP1
    .unreq          TMP2
    .unreq          TMP3
    .unreq          TMP4


/*****************************************************************************/

/*
 * jsimd_idct_4x4_neon
 *
 * This function contains inverse-DCT code for getting reduced-size
 * 4x4 pixels output from an 8x8 DCT block. It uses the same  calculations
 * and produces exactly the same output as IJG's original 'jpeg_idct_4x4'
 * function from jpeg-6b (jidctred.c).
 *
 * NOTE: jpeg-8 has an improved implementation of 4x4 inverse-DCT, which
 *       requires much less arithmetic operations and hence should be faster.
 *       The primary purpose of this particular NEON optimized function is
 *       bit exact compatibility with jpeg-6b.
 *
 * TODO: a bit better instructions scheduling can be achieved by expanding
 *       idct_helper/transpose_4x4 macros and reordering instructions,
 *       but readability will suffer somewhat.
 */

#define CONST_BITS  13

#define FIX_0_211164243  (1730)   /* FIX(0.211164243) */
#define FIX_0_509795579  (4176)   /* FIX(0.509795579) */
#define FIX_0_601344887  (4926)   /* FIX(0.601344887) */
#define FIX_0_720959822  (5906)   /* FIX(0.720959822) */
#define FIX_0_765366865  (6270)   /* FIX(0.765366865) */
#define FIX_0_850430095  (6967)   /* FIX(0.850430095) */
#define FIX_0_899976223  (7373)   /* FIX(0.899976223) */
#define FIX_1_061594337  (8697)   /* FIX(1.061594337) */
#define FIX_1_272758580  (10426)  /* FIX(1.272758580) */
#define FIX_1_451774981  (11893)  /* FIX(1.451774981) */
#define FIX_1_847759065  (15137)  /* FIX(1.847759065) */
#define FIX_2_172734803  (17799)  /* FIX(2.172734803) */
#define FIX_2_562915447  (20995)  /* FIX(2.562915447) */
#define FIX_3_624509785  (29692)  /* FIX(3.624509785) */

.balign 16
jsimd_idct_4x4_neon_consts:
  .short FIX_1_847759065        /* d0[0] */
  .short -FIX_0_765366865       /* d0[1] */
  .short -FIX_0_211164243       /* d0[2] */
  .short FIX_1_451774981        /* d0[3] */
  .short -FIX_2_172734803       /* d1[0] */
  .short FIX_1_061594337        /* d1[1] */
  .short -FIX_0_509795579       /* d1[2] */
  .short -FIX_0_601344887       /* d1[3] */
  .short FIX_0_899976223        /* d2[0] */
  .short FIX_2_562915447        /* d2[1] */
  .short 1 << (CONST_BITS + 1)  /* d2[2] */
  .short 0                      /* d2[3] */

.macro idct_helper x4, x6, x8, x10, x12, x14, x16, shift, y26, y27, y28, y29
    vmull.s16       q14, \x4, d2[2]
    vmlal.s16       q14, \x8, d0[0]
    vmlal.s16       q14, \x14, d0[1]

    vmull.s16       q13, \x16, d1[2]
    vmlal.s16       q13, \x12, d1[3]
    vmlal.s16       q13, \x10, d2[0]
    vmlal.s16       q13, \x6, d2[1]

    vmull.s16       q15, \x4, d2[2]
    vmlsl.s16       q15, \x8, d0[0]
    vmlsl.s16       q15, \x14, d0[1]

    vmull.s16       q12, \x16, d0[2]
    vmlal.s16       q12, \x12, d0[3]
    vmlal.s16       q12, \x10, d1[0]
    vmlal.s16       q12, \x6, d1[1]

    vadd.s32        q10, q14, q13
    vsub.s32        q14, q14, q13

  .if \shift > 16
    vrshr.s32       q10, q10, #\shift
    vrshr.s32       q14, q14, #\shift
    vmovn.s32       \y26, q10
    vmovn.s32       \y29, q14
  .else
    vrshrn.s32      \y26, q10, #\shift
    vrshrn.s32      \y29, q14, #\shift
  .endif

    vadd.s32        q10, q15, q12
    vsub.s32        q15, q15, q12

  .if \shift > 16
    vrshr.s32       q10, q10, #\shift
    vrshr.s32       q15, q15, #\shift
    vmovn.s32       \y27, q10
    vmovn.s32       \y28, q15
  .else
    vrshrn.s32      \y27, q10, #\shift
    vrshrn.s32      \y28, q15, #\shift
  .endif
.endm

asm_function jsimd_idct_4x4_neon

    DCT_TABLE       .req r0
    COEF_BLOCK      .req r1
    OUTPUT_BUF      .req r2
    OUTPUT_COL      .req r3
    TMP1            .req r0
    TMP2            .req r1
    TMP3            .req r2
    TMP4            .req ip

    vpush           {d8-d15}

    /* Load constants (d3 is just used for padding) */
    adr             TMP4, jsimd_idct_4x4_neon_consts
    vld1.16         {d0, d1, d2, d3}, [TMP4, :128]

    /* Load all COEF_BLOCK into NEON registers with the following allocation:
     *       0 1 2 3 | 4 5 6 7
     *      ---------+--------
     *   0 | d4      | d5
     *   1 | d6      | d7
     *   2 | d8      | d9
     *   3 | d10     | d11
     *   4 | -       | -
     *   5 | d12     | d13
     *   6 | d14     | d15
     *   7 | d16     | d17
     */
    vld1.16         {d4, d5, d6, d7}, [COEF_BLOCK, :128]!
    vld1.16         {d8, d9, d10, d11}, [COEF_BLOCK, :128]!
    add COEF_BLOCK, COEF_BLOCK, #16
    vld1.16         {d12, d13, d14, d15}, [COEF_BLOCK, :128]!
    vld1.16         {d16, d17}, [COEF_BLOCK, :128]!
    /* dequantize */
    vld1.16         {d18, d19, d20, d21}, [DCT_TABLE, :128]!
    vmul.s16        q2, q2, q9
    vld1.16         {d22, d23, d24, d25}, [DCT_TABLE, :128]!
    vmul.s16        q3, q3, q10
    vmul.s16        q4, q4, q11
    add             DCT_TABLE, DCT_TABLE, #16
    vld1.16         {d26, d27, d28, d29}, [DCT_TABLE, :128]!
    vmul.s16        q5, q5, q12
    vmul.s16        q6, q6, q13
    vld1.16         {d30, d31}, [DCT_TABLE, :128]!
    vmul.s16        q7, q7, q14
    vmul.s16        q8, q8, q15

    /* Pass 1 */
    idct_helper     d4, d6, d8, d10, d12, d14, d16, 12, d4, d6, d8, d10
    transpose_4x4   d4, d6, d8, d10
    idct_helper     d5, d7, d9, d11, d13, d15, d17, 12, d5, d7, d9, d11
    transpose_4x4   d5, d7, d9, d11

    /* Pass 2 */
    idct_helper     d4, d6, d8, d10, d7, d9, d11, 19, d26, d27, d28, d29
    transpose_4x4   d26, d27, d28, d29

    /* Range limit */
    vmov.u16        q15, #0x80
    vadd.s16        q13, q13, q15
    vadd.s16        q14, q14, q15
    vqmovun.s16     d26, q13
    vqmovun.s16     d27, q14

    /* Store results to the output buffer */
    ldmia           OUTPUT_BUF, {TMP1, TMP2, TMP3, TMP4}
    add             TMP1, TMP1, OUTPUT_COL
    add             TMP2, TMP2, OUTPUT_COL
    add             TMP3, TMP3, OUTPUT_COL
    add             TMP4, TMP4, OUTPUT_COL

#if defined(__ARMEL__) && !RESPECT_STRICT_ALIGNMENT
    /* We can use much less instructions on little endian systems if the
     * OS kernel is not configured to trap unaligned memory accesses
     */
    vst1.32         {d26[0]}, [TMP1]!
    vst1.32         {d27[0]}, [TMP3]!
    vst1.32         {d26[1]}, [TMP2]!
    vst1.32         {d27[1]}, [TMP4]!
#else
    vst1.8          {d26[0]}, [TMP1]!
    vst1.8          {d27[0]}, [TMP3]!
    vst1.8          {d26[1]}, [TMP1]!
    vst1.8          {d27[1]}, [TMP3]!
    vst1.8          {d26[2]}, [TMP1]!
    vst1.8          {d27[2]}, [TMP3]!
    vst1.8          {d26[3]}, [TMP1]!
    vst1.8          {d27[3]}, [TMP3]!

    vst1.8          {d26[4]}, [TMP2]!
    vst1.8          {d27[4]}, [TMP4]!
    vst1.8          {d26[5]}, [TMP2]!
    vst1.8          {d27[5]}, [TMP4]!
    vst1.8          {d26[6]}, [TMP2]!
    vst1.8          {d27[6]}, [TMP4]!
    vst1.8          {d26[7]}, [TMP2]!
    vst1.8          {d27[7]}, [TMP4]!
#endif

    vpop            {d8-d15}
    bx              lr

    .unreq          DCT_TABLE
    .unreq          COEF_BLOCK
    .unreq          OUTPUT_BUF
    .unreq          OUTPUT_COL
    .unreq          TMP1
    .unreq          TMP2
    .unreq          TMP3
    .unreq          TMP4

.purgem idct_helper


/*****************************************************************************/

/*
 * jsimd_idct_2x2_neon
 *
 * This function contains inverse-DCT code for getting reduced-size
 * 2x2 pixels output from an 8x8 DCT block. It uses the same  calculations
 * and produces exactly the same output as IJG's original 'jpeg_idct_2x2'
 * function from jpeg-6b (jidctred.c).
 *
 * NOTE: jpeg-8 has an improved implementation of 2x2 inverse-DCT, which
 *       requires much less arithmetic operations and hence should be faster.
 *       The primary purpose of this particular NEON optimized function is
 *       bit exact compatibility with jpeg-6b.
 */

.balign 8
jsimd_idct_2x2_neon_consts:
  .short -FIX_0_720959822  /* d0[0] */
  .short FIX_0_850430095   /* d0[1] */
  .short -FIX_1_272758580  /* d0[2] */
  .short FIX_3_624509785   /* d0[3] */

.macro idct_helper x4, x6, x10, x12, x16, shift, y26, y27
    vshll.s16       q14, \x4, #15
    vmull.s16       q13, \x6, d0[3]
    vmlal.s16       q13, \x10, d0[2]
    vmlal.s16       q13, \x12, d0[1]
    vmlal.s16       q13, \x16, d0[0]

    vadd.s32        q10, q14, q13
    vsub.s32        q14, q14, q13

  .if \shift > 16
    vrshr.s32       q10, q10, #\shift
    vrshr.s32       q14, q14, #\shift
    vmovn.s32       \y26, q10
    vmovn.s32       \y27, q14
  .else
    vrshrn.s32      \y26, q10, #\shift
    vrshrn.s32      \y27, q14, #\shift
  .endif
.endm

asm_function jsimd_idct_2x2_neon

    DCT_TABLE       .req r0
    COEF_BLOCK      .req r1
    OUTPUT_BUF      .req r2
    OUTPUT_COL      .req r3
    TMP1            .req r0
    TMP2            .req ip

    vpush           {d8-d15}

    /* Load constants */
    adr             TMP2, jsimd_idct_2x2_neon_consts
    vld1.16         {d0}, [TMP2, :64]

    /* Load all COEF_BLOCK into NEON registers with the following allocation:
     *       0 1 2 3 | 4 5 6 7
     *      ---------+--------
     *   0 | d4      | d5
     *   1 | d6      | d7
     *   2 | -       | -
     *   3 | d10     | d11
     *   4 | -       | -
     *   5 | d12     | d13
     *   6 | -       | -
     *   7 | d16     | d17
     */
    vld1.16         {d4, d5, d6, d7}, [COEF_BLOCK, :128]!
    add             COEF_BLOCK, COEF_BLOCK, #16
    vld1.16         {d10, d11}, [COEF_BLOCK, :128]!
    add             COEF_BLOCK, COEF_BLOCK, #16
    vld1.16         {d12, d13}, [COEF_BLOCK, :128]!
    add             COEF_BLOCK, COEF_BLOCK, #16
    vld1.16         {d16, d17}, [COEF_BLOCK, :128]!
    /* Dequantize */
    vld1.16         {d18, d19, d20, d21}, [DCT_TABLE, :128]!
    vmul.s16        q2, q2, q9
    vmul.s16        q3, q3, q10
    add             DCT_TABLE, DCT_TABLE, #16
    vld1.16         {d24, d25}, [DCT_TABLE, :128]!
    vmul.s16        q5, q5, q12
    add             DCT_TABLE, DCT_TABLE, #16
    vld1.16         {d26, d27}, [DCT_TABLE, :128]!
    vmul.s16        q6, q6, q13
    add             DCT_TABLE, DCT_TABLE, #16
    vld1.16         {d30, d31}, [DCT_TABLE, :128]!
    vmul.s16        q8, q8, q15

    /* Pass 1 */
#if 0
    idct_helper     d4, d6, d10, d12, d16, 13, d4, d6
    transpose_4x4   d4, d6, d8, d10
    idct_helper     d5, d7, d11, d13, d17, 13, d5, d7
    transpose_4x4   d5, d7, d9, d11
#else
    vmull.s16       q13, d6, d0[3]
    vmlal.s16       q13, d10, d0[2]
    vmlal.s16       q13, d12, d0[1]
    vmlal.s16       q13, d16, d0[0]
    vmull.s16       q12, d7, d0[3]
    vmlal.s16       q12, d11, d0[2]
    vmlal.s16       q12, d13, d0[1]
    vmlal.s16       q12, d17, d0[0]
    vshll.s16       q14, d4, #15
    vshll.s16       q15, d5, #15
    vadd.s32        q10, q14, q13
    vsub.s32        q14, q14, q13
    vrshrn.s32      d4, q10, #13
    vrshrn.s32      d6, q14, #13
    vadd.s32        q10, q15, q12
    vsub.s32        q14, q15, q12
    vrshrn.s32      d5, q10, #13
    vrshrn.s32      d7, q14, #13
    vtrn.16         q2, q3
    vtrn.32         q3, q5
#endif

    /* Pass 2 */
    idct_helper     d4, d6, d10, d7, d11, 20, d26, d27

    /* Range limit */
    vmov.u16        q15, #0x80
    vadd.s16        q13, q13, q15
    vqmovun.s16     d26, q13
    vqmovun.s16     d27, q13

    /* Store results to the output buffer */
    ldmia           OUTPUT_BUF, {TMP1, TMP2}
    add             TMP1, TMP1, OUTPUT_COL
    add             TMP2, TMP2, OUTPUT_COL

    vst1.8          {d26[0]}, [TMP1]!
    vst1.8          {d27[4]}, [TMP1]!
    vst1.8          {d26[1]}, [TMP2]!
    vst1.8          {d27[5]}, [TMP2]!

    vpop            {d8-d15}
    bx              lr

    .unreq          DCT_TABLE
    .unreq          COEF_BLOCK
    .unreq          OUTPUT_BUF
    .unreq          OUTPUT_COL
    .unreq          TMP1
    .unreq          TMP2

.purgem idct_helper


/*****************************************************************************/

/*
 * jsimd_ycc_extrgb_convert_neon
 * jsimd_ycc_extbgr_convert_neon
 * jsimd_ycc_extrgbx_convert_neon
 * jsimd_ycc_extbgrx_convert_neon
 * jsimd_ycc_extxbgr_convert_neon
 * jsimd_ycc_extxrgb_convert_neon
 *
 * Colorspace conversion YCbCr -> RGB
 */


.macro do_load size
  .if \size == 8
    vld1.8          {d4}, [U, :64]!
    vld1.8          {d5}, [V, :64]!
    vld1.8          {d0}, [Y, :64]!
    pld             [U, #64]
    pld             [V, #64]
    pld             [Y, #64]
  .elseif \size == 4
    vld1.8          {d4[0]}, [U]!
    vld1.8          {d4[1]}, [U]!
    vld1.8          {d4[2]}, [U]!
    vld1.8          {d4[3]}, [U]!
    vld1.8          {d5[0]}, [V]!
    vld1.8          {d5[1]}, [V]!
    vld1.8          {d5[2]}, [V]!
    vld1.8          {d5[3]}, [V]!
    vld1.8          {d0[0]}, [Y]!
    vld1.8          {d0[1]}, [Y]!
    vld1.8          {d0[2]}, [Y]!
    vld1.8          {d0[3]}, [Y]!
  .elseif \size == 2
    vld1.8          {d4[4]}, [U]!
    vld1.8          {d4[5]}, [U]!
    vld1.8          {d5[4]}, [V]!
    vld1.8          {d5[5]}, [V]!
    vld1.8          {d0[4]}, [Y]!
    vld1.8          {d0[5]}, [Y]!
  .elseif \size == 1
    vld1.8          {d4[6]}, [U]!
    vld1.8          {d5[6]}, [V]!
    vld1.8          {d0[6]}, [Y]!
  .else
    .error unsupported macroblock size
  .endif
.endm

.macro do_store bpp, size
  .if \bpp == 24
    .if \size == 8
      vst3.8        {d10, d11, d12}, [RGB]!
    .elseif \size == 4
      vst3.8        {d10[0], d11[0], d12[0]}, [RGB]!
      vst3.8        {d10[1], d11[1], d12[1]}, [RGB]!
      vst3.8        {d10[2], d11[2], d12[2]}, [RGB]!
      vst3.8        {d10[3], d11[3], d12[3]}, [RGB]!
    .elseif \size == 2
      vst3.8        {d10[4], d11[4], d12[4]}, [RGB]!
      vst3.8        {d10[5], d11[5], d12[5]}, [RGB]!
    .elseif \size == 1
      vst3.8        {d10[6], d11[6], d12[6]}, [RGB]!
    .else
      .error unsupported macroblock size
    .endif
  .elseif \bpp == 32
    .if \size == 8
      vst4.8        {d10, d11, d12, d13}, [RGB]!
    .elseif \size == 4
      vst4.8        {d10[0], d11[0], d12[0], d13[0]}, [RGB]!
      vst4.8        {d10[1], d11[1], d12[1], d13[1]}, [RGB]!
      vst4.8        {d10[2], d11[2], d12[2], d13[2]}, [RGB]!
      vst4.8        {d10[3], d11[3], d12[3], d13[3]}, [RGB]!
    .elseif \size == 2
      vst4.8        {d10[4], d11[4], d12[4], d13[4]}, [RGB]!
      vst4.8        {d10[5], d11[5], d12[5], d13[5]}, [RGB]!
    .elseif \size == 1
      vst4.8        {d10[6], d11[6], d12[6], d13[6]}, [RGB]!
    .else
      .error unsupported macroblock size
    .endif
  .elseif \bpp == 16
    .if \size == 8
      vst1.16       {q15}, [RGB]!
    .elseif \size == 4
      vst1.16       {d30}, [RGB]!
    .elseif \size == 2
      vst1.16       {d31[0]}, [RGB]!
      vst1.16       {d31[1]}, [RGB]!
    .elseif \size == 1
      vst1.16       {d31[2]}, [RGB]!
    .else
      .error unsupported macroblock size
    .endif
  .else
    .error unsupported bpp
  .endif
.endm

.macro generate_jsimd_ycc_rgb_convert_neon colorid, bpp, r_offs, g_offs, b_offs

/*
 * 2-stage pipelined YCbCr->RGB conversion
 */

.macro do_yuv_to_rgb_stage1
    vaddw.u8        q3, q1, d4      /* q3 = u - 128 */
    vaddw.u8        q4, q1, d5      /* q2 = v - 128 */
    vmull.s16       q10, d6, d1[1]  /* multiply by -11277 */
    vmlal.s16       q10, d8, d1[2]  /* multiply by -23401 */
    vmull.s16       q11, d7, d1[1]  /* multiply by -11277 */
    vmlal.s16       q11, d9, d1[2]  /* multiply by -23401 */
    vmull.s16       q12, d8, d1[0]  /* multiply by 22971 */
    vmull.s16       q13, d9, d1[0]  /* multiply by 22971 */
    vmull.s16       q14, d6, d1[3]  /* multiply by 29033 */
    vmull.s16       q15, d7, d1[3]  /* multiply by 29033 */
.endm

.macro do_yuv_to_rgb_stage2
    vrshrn.s32      d20, q10, #15
    vrshrn.s32      d21, q11, #15
    vrshrn.s32      d24, q12, #14
    vrshrn.s32      d25, q13, #14
    vrshrn.s32      d28, q14, #14
    vrshrn.s32      d29, q15, #14
    vaddw.u8        q11, q10, d0
    vaddw.u8        q12, q12, d0
    vaddw.u8        q14, q14, d0
  .if \bpp != 16
    vqmovun.s16     d1\g_offs, q11
    vqmovun.s16     d1\r_offs, q12
    vqmovun.s16     d1\b_offs, q14
  .else  /* rgb565 */
    vqshlu.s16      q13, q11, #8
    vqshlu.s16      q15, q12, #8
    vqshlu.s16      q14, q14, #8
    vsri.u16        q15, q13, #5
    vsri.u16        q15, q14, #11
  .endif
.endm

.macro do_yuv_to_rgb_stage2_store_load_stage1
                                       /* "do_yuv_to_rgb_stage2" and "store" */
                                       vrshrn.s32      d20, q10, #15
    /* "load" and "do_yuv_to_rgb_stage1" */
    pld             [U, #64]
                                       vrshrn.s32      d21, q11, #15
    pld             [V, #64]
                                       vrshrn.s32      d24, q12, #14
                                       vrshrn.s32      d25, q13, #14
    vld1.8          {d4}, [U, :64]!
                                       vrshrn.s32      d28, q14, #14
    vld1.8          {d5}, [V, :64]!
                                       vrshrn.s32      d29, q15, #14
    vaddw.u8        q3, q1, d4      /* q3 = u - 128 */
    vaddw.u8        q4, q1, d5      /* q2 = v - 128 */
                                       vaddw.u8        q11, q10, d0
    vmull.s16       q10, d6, d1[1]  /* multiply by -11277 */
    vmlal.s16       q10, d8, d1[2]  /* multiply by -23401 */
                                       vaddw.u8        q12, q12, d0
                                       vaddw.u8        q14, q14, d0
  .if \bpp != 16  /**************** rgb24/rgb32 ******************************/
                                       vqmovun.s16     d1\g_offs, q11
    pld             [Y, #64]
                                       vqmovun.s16     d1\r_offs, q12
    vld1.8          {d0}, [Y, :64]!
                                       vqmovun.s16     d1\b_offs, q14
    vmull.s16       q11, d7, d1[1]  /* multiply by -11277 */
    vmlal.s16       q11, d9, d1[2]  /* multiply by -23401 */
                                       do_store        \bpp, 8
    vmull.s16       q12, d8, d1[0]  /* multiply by 22971 */
    vmull.s16       q13, d9, d1[0]  /* multiply by 22971 */
    vmull.s16       q14, d6, d1[3]  /* multiply by 29033 */
    vmull.s16       q15, d7, d1[3]  /* multiply by 29033 */
  .else  /**************************** rgb565 ********************************/
                                       vqshlu.s16      q13, q11, #8
    pld             [Y, #64]
                                       vqshlu.s16      q15, q12, #8
                                       vqshlu.s16      q14, q14, #8
    vld1.8          {d0}, [Y, :64]!
    vmull.s16       q11, d7, d1[1]
    vmlal.s16       q11, d9, d1[2]
                                       vsri.u16        q15, q13, #5
    vmull.s16       q12, d8, d1[0]
                                       vsri.u16        q15, q14, #11
    vmull.s16       q13, d9, d1[0]
    vmull.s16       q14, d6, d1[3]
                                       do_store        \bpp, 8
    vmull.s16       q15, d7, d1[3]
  .endif
.endm

.macro do_yuv_to_rgb
    do_yuv_to_rgb_stage1
    do_yuv_to_rgb_stage2
.endm

/* Apple gas crashes on adrl, work around that by using adr.
 * But this requires a copy of these constants for each function.
 */

.balign 16
jsimd_ycc_\colorid\()_neon_consts:
  .short 0,      0,     0,      0
  .short 22971, -11277, -23401, 29033
  .short -128,  -128,   -128,   -128
  .short -128,  -128,   -128,   -128

asm_function jsimd_ycc_\colorid\()_convert_neon
    OUTPUT_WIDTH    .req r0
    INPUT_BUF       .req r1
    INPUT_ROW       .req r2
    OUTPUT_BUF      .req r3
    NUM_ROWS        .req r4

    INPUT_BUF0      .req r5
    INPUT_BUF1      .req r6
    INPUT_BUF2      .req INPUT_BUF

    RGB             .req r7
    Y               .req r8
    U               .req r9
    V               .req r10
    N               .req ip

    /* Load constants to d1, d2, d3 (d0 is just used for padding) */
    adr             ip, jsimd_ycc_\colorid\()_neon_consts
    vld1.16         {d0, d1, d2, d3}, [ip, :128]

    /* Save ARM registers and handle input arguments */
    push            {r4, r5, r6, r7, r8, r9, r10, lr}
    ldr             NUM_ROWS, [sp, #(4 * 8)]
    ldr             INPUT_BUF0, [INPUT_BUF]
    ldr             INPUT_BUF1, [INPUT_BUF, #4]
    ldr             INPUT_BUF2, [INPUT_BUF, #8]
    .unreq          INPUT_BUF

    /* Save NEON registers */
    vpush           {d8-d15}

    /* Initially set d10, d11, d12, d13 to 0xFF */
    vmov.u8         q5, #255
    vmov.u8         q6, #255

    /* Outer loop over scanlines */
    cmp             NUM_ROWS, #1
    blt             9f
0:
    ldr             Y, [INPUT_BUF0, INPUT_ROW, lsl #2]
    ldr             U, [INPUT_BUF1, INPUT_ROW, lsl #2]
    mov             N, OUTPUT_WIDTH
    ldr             V, [INPUT_BUF2, INPUT_ROW, lsl #2]
    add             INPUT_ROW, INPUT_ROW, #1
    ldr             RGB, [OUTPUT_BUF], #4

    /* Inner loop over pixels */
    subs            N, N, #8
    blt             3f
    do_load         8
    do_yuv_to_rgb_stage1
    subs            N, N, #8
    blt             2f
1:
    do_yuv_to_rgb_stage2_store_load_stage1
    subs            N, N, #8
    bge             1b
2:
    do_yuv_to_rgb_stage2
    do_store        \bpp, 8
    tst             N, #7
    beq             8f
3:
    tst             N, #4
    beq             3f
    do_load         4
3:
    tst             N, #2
    beq             4f
    do_load         2
4:
    tst             N, #1
    beq             5f
    do_load         1
5:
    do_yuv_to_rgb
    tst             N, #4
    beq             6f
    do_store        \bpp, 4
6:
    tst             N, #2
    beq             7f
    do_store        \bpp, 2
7:
    tst             N, #1
    beq             8f
    do_store        \bpp, 1
8:
    subs            NUM_ROWS, NUM_ROWS, #1
    bgt             0b
9:
    /* Restore all registers and return */
    vpop            {d8-d15}
    pop             {r4, r5, r6, r7, r8, r9, r10, pc}

    .unreq          OUTPUT_WIDTH
    .unreq          INPUT_ROW
    .unreq          OUTPUT_BUF
    .unreq          NUM_ROWS
    .unreq          INPUT_BUF0
    .unreq          INPUT_BUF1
    .unreq          INPUT_BUF2
    .unreq          RGB
    .unreq          Y
    .unreq          U
    .unreq          V
    .unreq          N

.purgem do_yuv_to_rgb
.purgem do_yuv_to_rgb_stage1
.purgem do_yuv_to_rgb_stage2
.purgem do_yuv_to_rgb_stage2_store_load_stage1

.endm

/*--------------------------------- id ----- bpp R  G  B */
generate_jsimd_ycc_rgb_convert_neon extrgb,  24, 0, 1, 2
generate_jsimd_ycc_rgb_convert_neon extbgr,  24, 2, 1, 0
generate_jsimd_ycc_rgb_convert_neon extrgbx, 32, 0, 1, 2
generate_jsimd_ycc_rgb_convert_neon extbgrx, 32, 2, 1, 0
generate_jsimd_ycc_rgb_convert_neon extxbgr, 32, 3, 2, 1
generate_jsimd_ycc_rgb_convert_neon extxrgb, 32, 1, 2, 3
generate_jsimd_ycc_rgb_convert_neon rgb565,  16, 0, 0, 0

.purgem do_load
.purgem do_store


/*****************************************************************************/

/*
 * jsimd_extrgb_ycc_convert_neon
 * jsimd_extbgr_ycc_convert_neon
 * jsimd_extrgbx_ycc_convert_neon
 * jsimd_extbgrx_ycc_convert_neon
 * jsimd_extxbgr_ycc_convert_neon
 * jsimd_extxrgb_ycc_convert_neon
 *
 * Colorspace conversion RGB -> YCbCr
 */

.macro do_store size
  .if \size == 8
    vst1.8          {d20}, [Y]!
    vst1.8          {d21}, [U]!
    vst1.8          {d22}, [V]!
  .elseif \size == 4
    vst1.8          {d20[0]}, [Y]!
    vst1.8          {d20[1]}, [Y]!
    vst1.8          {d20[2]}, [Y]!
    vst1.8          {d20[3]}, [Y]!
    vst1.8          {d21[0]}, [U]!
    vst1.8          {d21[1]}, [U]!
    vst1.8          {d21[2]}, [U]!
    vst1.8          {d21[3]}, [U]!
    vst1.8          {d22[0]}, [V]!
    vst1.8          {d22[1]}, [V]!
    vst1.8          {d22[2]}, [V]!
    vst1.8          {d22[3]}, [V]!
  .elseif \size == 2
    vst1.8          {d20[4]}, [Y]!
    vst1.8          {d20[5]}, [Y]!
    vst1.8          {d21[4]}, [U]!
    vst1.8          {d21[5]}, [U]!
    vst1.8          {d22[4]}, [V]!
    vst1.8          {d22[5]}, [V]!
  .elseif \size == 1
    vst1.8          {d20[6]}, [Y]!
    vst1.8          {d21[6]}, [U]!
    vst1.8          {d22[6]}, [V]!
  .else
    .error unsupported macroblock size
  .endif
.endm

.macro do_load bpp, size
  .if \bpp == 24
    .if \size == 8
      vld3.8        {d10, d11, d12}, [RGB]!
      pld           [RGB, #128]
    .elseif \size == 4
      vld3.8        {d10[0], d11[0], d12[0]}, [RGB]!
      vld3.8        {d10[1], d11[1], d12[1]}, [RGB]!
      vld3.8        {d10[2], d11[2], d12[2]}, [RGB]!
      vld3.8        {d10[3], d11[3], d12[3]}, [RGB]!
    .elseif \size == 2
      vld3.8        {d10[4], d11[4], d12[4]}, [RGB]!
      vld3.8        {d10[5], d11[5], d12[5]}, [RGB]!
    .elseif \size == 1
      vld3.8        {d10[6], d11[6], d12[6]}, [RGB]!
    .else
      .error unsupported macroblock size
    .endif
  .elseif \bpp == 32
    .if \size == 8
      vld4.8        {d10, d11, d12, d13}, [RGB]!
      pld           [RGB, #128]
    .elseif \size == 4
      vld4.8        {d10[0], d11[0], d12[0], d13[0]}, [RGB]!
      vld4.8        {d10[1], d11[1], d12[1], d13[1]}, [RGB]!
      vld4.8        {d10[2], d11[2], d12[2], d13[2]}, [RGB]!
      vld4.8        {d10[3], d11[3], d12[3], d13[3]}, [RGB]!
    .elseif \size == 2
      vld4.8        {d10[4], d11[4], d12[4], d13[4]}, [RGB]!
      vld4.8        {d10[5], d11[5], d12[5], d13[5]}, [RGB]!
    .elseif \size == 1
      vld4.8        {d10[6], d11[6], d12[6], d13[6]}, [RGB]!
    .else
      .error unsupported macroblock size
    .endif
  .else
    .error unsupported bpp
  .endif
.endm

.macro generate_jsimd_rgb_ycc_convert_neon colorid, bpp, r_offs, g_offs, b_offs

/*
 * 2-stage pipelined RGB->YCbCr conversion
 */

.macro do_rgb_to_yuv_stage1
    vmovl.u8        q2, d1\r_offs  /* r = { d4, d5 } */
    vmovl.u8        q3, d1\g_offs  /* g = { d6, d7 } */
    vmovl.u8        q4, d1\b_offs  /* b = { d8, d9 } */
    vmull.u16       q7, d4, d0[0]
    vmlal.u16       q7, d6, d0[1]
    vmlal.u16       q7, d8, d0[2]
    vmull.u16       q8, d5, d0[0]
    vmlal.u16       q8, d7, d0[1]
    vmlal.u16       q8, d9, d0[2]
    vrev64.32       q9, q1
    vrev64.32       q13, q1
    vmlsl.u16       q9, d4, d0[3]
    vmlsl.u16       q9, d6, d1[0]
    vmlal.u16       q9, d8, d1[1]
    vmlsl.u16       q13, d5, d0[3]
    vmlsl.u16       q13, d7, d1[0]
    vmlal.u16       q13, d9, d1[1]
    vrev64.32       q14, q1
    vrev64.32       q15, q1
    vmlal.u16       q14, d4, d1[1]
    vmlsl.u16       q14, d6, d1[2]
    vmlsl.u16       q14, d8, d1[3]
    vmlal.u16       q15, d5, d1[1]
    vmlsl.u16       q15, d7, d1[2]
    vmlsl.u16       q15, d9, d1[3]
.endm

.macro do_rgb_to_yuv_stage2
    vrshrn.u32      d20, q7, #16
    vrshrn.u32      d21, q8, #16
    vshrn.u32       d22, q9, #16
    vshrn.u32       d23, q13, #16
    vshrn.u32       d24, q14, #16
    vshrn.u32       d25, q15, #16
    vmovn.u16       d20, q10       /* d20 = y */
    vmovn.u16       d21, q11       /* d21 = u */
    vmovn.u16       d22, q12       /* d22 = v */
.endm

.macro do_rgb_to_yuv
    do_rgb_to_yuv_stage1
    do_rgb_to_yuv_stage2
.endm

.macro do_rgb_to_yuv_stage2_store_load_stage1
      vrshrn.u32      d20, q7, #16
      vrshrn.u32      d21, q8, #16
      vshrn.u32       d22, q9, #16
    vrev64.32       q9, q1
      vshrn.u32       d23, q13, #16
    vrev64.32       q13, q1
      vshrn.u32       d24, q14, #16
      vshrn.u32       d25, q15, #16
    do_load         \bpp, 8
      vmovn.u16       d20, q10     /* d20 = y */
    vmovl.u8        q2, d1\r_offs  /* r = { d4, d5 } */
      vmovn.u16       d21, q11     /* d21 = u */
    vmovl.u8        q3, d1\g_offs  /* g = { d6, d7 } */
      vmovn.u16       d22, q12     /* d22 = v */
    vmovl.u8        q4, d1\b_offs  /* b = { d8, d9 } */
    vmull.u16       q7, d4, d0[0]
    vmlal.u16       q7, d6, d0[1]
    vmlal.u16       q7, d8, d0[2]
      vst1.8          {d20}, [Y]!
    vmull.u16       q8, d5, d0[0]
    vmlal.u16       q8, d7, d0[1]
    vmlal.u16       q8, d9, d0[2]
    vmlsl.u16       q9, d4, d0[3]
    vmlsl.u16       q9, d6, d1[0]
    vmlal.u16       q9, d8, d1[1]
      vst1.8          {d21}, [U]!
    vmlsl.u16       q13, d5, d0[3]
    vmlsl.u16       q13, d7, d1[0]
    vmlal.u16       q13, d9, d1[1]
    vrev64.32       q14, q1
    vrev64.32       q15, q1
    vmlal.u16       q14, d4, d1[1]
    vmlsl.u16       q14, d6, d1[2]
    vmlsl.u16       q14, d8, d1[3]
      vst1.8          {d22}, [V]!
    vmlal.u16       q15, d5, d1[1]
    vmlsl.u16       q15, d7, d1[2]
    vmlsl.u16       q15, d9, d1[3]
.endm

.balign 16
jsimd_\colorid\()_ycc_neon_consts:
  .short 19595, 38470, 7471,  11059
  .short 21709, 32768, 27439, 5329
  .short 32767, 128,   32767, 128
  .short 32767, 128,   32767, 128

asm_function jsimd_\colorid\()_ycc_convert_neon
    OUTPUT_WIDTH    .req r0
    INPUT_BUF       .req r1
    OUTPUT_BUF      .req r2
    OUTPUT_ROW      .req r3
    NUM_ROWS        .req r4

    OUTPUT_BUF0     .req r5
    OUTPUT_BUF1     .req r6
    OUTPUT_BUF2     .req OUTPUT_BUF

    RGB             .req r7
    Y               .req r8
    U               .req r9
    V               .req r10
    N               .req ip

    /* Load constants to d0, d1, d2, d3 */
    adr             ip, jsimd_\colorid\()_ycc_neon_consts
    vld1.16         {d0, d1, d2, d3}, [ip, :128]

    /* Save ARM registers and handle input arguments */
    push            {r4, r5, r6, r7, r8, r9, r10, lr}
    ldr             NUM_ROWS, [sp, #(4 * 8)]
    ldr             OUTPUT_BUF0, [OUTPUT_BUF]
    ldr             OUTPUT_BUF1, [OUTPUT_BUF, #4]
    ldr             OUTPUT_BUF2, [OUTPUT_BUF, #8]
    .unreq          OUTPUT_BUF

    /* Save NEON registers */
    vpush           {d8-d15}

    /* Outer loop over scanlines */
    cmp             NUM_ROWS, #1
    blt             9f
0:
    ldr             Y, [OUTPUT_BUF0, OUTPUT_ROW, lsl #2]
    ldr             U, [OUTPUT_BUF1, OUTPUT_ROW, lsl #2]
    mov             N, OUTPUT_WIDTH
    ldr             V, [OUTPUT_BUF2, OUTPUT_ROW, lsl #2]
    add             OUTPUT_ROW, OUTPUT_ROW, #1
    ldr             RGB, [INPUT_BUF], #4

    /* Inner loop over pixels */
    subs            N, N, #8
    blt             3f
    do_load         \bpp, 8
    do_rgb_to_yuv_stage1
    subs            N, N, #8
    blt             2f
1:
    do_rgb_to_yuv_stage2_store_load_stage1
    subs            N, N, #8
    bge             1b
2:
    do_rgb_to_yuv_stage2
    do_store        8
    tst             N, #7
    beq             8f
3:
    tst             N, #4
    beq             3f
    do_load         \bpp, 4
3:
    tst             N, #2
    beq             4f
    do_load         \bpp, 2
4:
    tst             N, #1
    beq             5f
    do_load         \bpp, 1
5:
    do_rgb_to_yuv
    tst             N, #4
    beq             6f
    do_store        4
6:
    tst             N, #2
    beq             7f
    do_store        2
7:
    tst             N, #1
    beq             8f
    do_store        1
8:
    subs            NUM_ROWS, NUM_ROWS, #1
    bgt             0b
9:
    /* Restore all registers and return */
    vpop            {d8-d15}
    pop             {r4, r5, r6, r7, r8, r9, r10, pc}

    .unreq          OUTPUT_WIDTH
    .unreq          OUTPUT_ROW
    .unreq          INPUT_BUF
    .unreq          NUM_ROWS
    .unreq          OUTPUT_BUF0
    .unreq          OUTPUT_BUF1
    .unreq          OUTPUT_BUF2
    .unreq          RGB
    .unreq          Y
    .unreq          U
    .unreq          V
    .unreq          N

.purgem do_rgb_to_yuv
.purgem do_rgb_to_yuv_stage1
.purgem do_rgb_to_yuv_stage2
.purgem do_rgb_to_yuv_stage2_store_load_stage1

.endm

/*--------------------------------- id ----- bpp R  G  B */
generate_jsimd_rgb_ycc_convert_neon extrgb,  24, 0, 1, 2
generate_jsimd_rgb_ycc_convert_neon extbgr,  24, 2, 1, 0
generate_jsimd_rgb_ycc_convert_neon extrgbx, 32, 0, 1, 2
generate_jsimd_rgb_ycc_convert_neon extbgrx, 32, 2, 1, 0
generate_jsimd_rgb_ycc_convert_neon extxbgr, 32, 3, 2, 1
generate_jsimd_rgb_ycc_convert_neon extxrgb, 32, 1, 2, 3

.purgem do_load
.purgem do_store


/*****************************************************************************/

/*
 * Load data into workspace, applying unsigned->signed conversion
 *
 * TODO: can be combined with 'jsimd_fdct_ifast_neon' to get
 *       rid of VST1.16 instructions
 */

asm_function jsimd_convsamp_neon
    SAMPLE_DATA     .req r0
    START_COL       .req r1
    WORKSPACE       .req r2
    TMP1            .req r3
    TMP2            .req r4
    TMP3            .req r5
    TMP4            .req ip

    push            {r4, r5}
    vmov.u8         d0, #128

    ldmia           SAMPLE_DATA!, {TMP1, TMP2, TMP3, TMP4}
    add             TMP1, TMP1, START_COL
    add             TMP2, TMP2, START_COL
    add             TMP3, TMP3, START_COL
    add             TMP4, TMP4, START_COL
    vld1.8          {d16}, [TMP1]
    vsubl.u8        q8, d16, d0
    vld1.8          {d18}, [TMP2]
    vsubl.u8        q9, d18, d0
    vld1.8          {d20}, [TMP3]
    vsubl.u8        q10, d20, d0
    vld1.8          {d22}, [TMP4]
    ldmia           SAMPLE_DATA!, {TMP1, TMP2, TMP3, TMP4}
    vsubl.u8        q11, d22, d0
    vst1.16         {d16, d17, d18, d19}, [WORKSPACE, :128]!
    add             TMP1, TMP1, START_COL
    add             TMP2, TMP2, START_COL
    vst1.16         {d20, d21, d22, d23}, [WORKSPACE, :128]!
    add             TMP3, TMP3, START_COL
    add             TMP4, TMP4, START_COL
    vld1.8          {d24}, [TMP1]
    vsubl.u8        q12, d24, d0
    vld1.8          {d26}, [TMP2]
    vsubl.u8        q13, d26, d0
    vld1.8          {d28}, [TMP3]
    vsubl.u8        q14, d28, d0
    vld1.8          {d30}, [TMP4]
    vsubl.u8        q15, d30, d0
    vst1.16         {d24, d25, d26, d27}, [WORKSPACE, :128]!
    vst1.16         {d28, d29, d30, d31}, [WORKSPACE, :128]!
    pop             {r4, r5}
    bx              lr

    .unreq          SAMPLE_DATA
    .unreq          START_COL
    .unreq          WORKSPACE
    .unreq          TMP1
    .unreq          TMP2
    .unreq          TMP3
    .unreq          TMP4


/*****************************************************************************/

/*
 * jsimd_fdct_ifast_neon
 *
 * This function contains a fast, not so accurate integer implementation of
 * the forward DCT (Discrete Cosine Transform). It uses the same calculations
 * and produces exactly the same output as IJG's original 'jpeg_fdct_ifast'
 * function from jfdctfst.c
 *
 * TODO: can be combined with 'jsimd_convsamp_neon' to get
 *       rid of a bunch of VLD1.16 instructions
 */

#define XFIX_0_382683433  d0[0]
#define XFIX_0_541196100  d0[1]
#define XFIX_0_707106781  d0[2]
#define XFIX_1_306562965  d0[3]

.balign 16
jsimd_fdct_ifast_neon_consts:
  .short (98 * 128)               /* XFIX_0_382683433 */
  .short (139 * 128)              /* XFIX_0_541196100 */
  .short (181 * 128)              /* XFIX_0_707106781 */
  .short (334 * 128 - 256 * 128)  /* XFIX_1_306562965 */

asm_function jsimd_fdct_ifast_neon

    DATA            .req r0
    TMP             .req ip

    vpush           {d8-d15}

    /* Load constants */
    adr             TMP, jsimd_fdct_ifast_neon_consts
    vld1.16         {d0}, [TMP, :64]

    /* Load all DATA into NEON registers with the following allocation:
     *       0 1 2 3 | 4 5 6 7
     *      ---------+--------
     *   0 | d16     | d17    | q8
     *   1 | d18     | d19    | q9
     *   2 | d20     | d21    | q10
     *   3 | d22     | d23    | q11
     *   4 | d24     | d25    | q12
     *   5 | d26     | d27    | q13
     *   6 | d28     | d29    | q14
     *   7 | d30     | d31    | q15
     */

    vld1.16         {d16, d17, d18, d19}, [DATA, :128]!
    vld1.16         {d20, d21, d22, d23}, [DATA, :128]!
    vld1.16         {d24, d25, d26, d27}, [DATA, :128]!
    vld1.16         {d28, d29, d30, d31}, [DATA, :128]
    sub             DATA, DATA, #(128 - 32)

    mov             TMP, #2
1:
    /* Transpose */
    vtrn.16         q12, q13
    vtrn.16         q10, q11
    vtrn.16         q8, q9
    vtrn.16         q14, q15
    vtrn.32         q9, q11
    vtrn.32         q13, q15
    vtrn.32         q8, q10
    vtrn.32         q12, q14
    vswp            d30, d23
    vswp            d24, d17
    vswp            d26, d19
      /* 1-D FDCT */
      vadd.s16        q2, q11, q12
    vswp            d28, d21
      vsub.s16        q12, q11, q12
      vsub.s16        q6, q10, q13
      vadd.s16        q10, q10, q13
      vsub.s16        q7, q9, q14
      vadd.s16        q9, q9, q14
      vsub.s16        q1, q8, q15
      vadd.s16        q8, q8, q15
      vsub.s16        q4, q9, q10
      vsub.s16        q5, q8, q2
      vadd.s16        q3, q9, q10
      vadd.s16        q4, q4, q5
      vadd.s16        q2, q8, q2
      vqdmulh.s16     q4, q4, XFIX_0_707106781
      vadd.s16        q11, q12, q6
      vadd.s16        q8, q2, q3
      vsub.s16        q12, q2, q3
      vadd.s16        q3, q6, q7
      vadd.s16        q7, q7, q1
      vqdmulh.s16     q3, q3, XFIX_0_707106781
      vsub.s16        q6, q11, q7
      vadd.s16        q10, q5, q4
      vqdmulh.s16     q6, q6, XFIX_0_382683433
      vsub.s16        q14, q5, q4
      vqdmulh.s16     q11, q11, XFIX_0_541196100
      vqdmulh.s16     q5, q7, XFIX_1_306562965
      vadd.s16        q4, q1, q3
      vsub.s16        q3, q1, q3
      vadd.s16        q7, q7, q6
      vadd.s16        q11, q11, q6
      vadd.s16        q7, q7, q5
      vadd.s16        q13, q3, q11
      vsub.s16        q11, q3, q11
      vadd.s16        q9, q4, q7
      vsub.s16        q15, q4, q7
    subs            TMP, TMP, #1
    bne             1b

    /* store results */
    vst1.16         {d16, d17, d18, d19}, [DATA, :128]!
    vst1.16         {d20, d21, d22, d23}, [DATA, :128]!
    vst1.16         {d24, d25, d26, d27}, [DATA, :128]!
    vst1.16         {d28, d29, d30, d31}, [DATA, :128]

    vpop            {d8-d15}
    bx              lr

    .unreq          DATA
    .unreq          TMP


/*****************************************************************************/

/*
 * GLOBAL(void)
 * jsimd_quantize_neon(JCOEFPTR coef_block, DCTELEM *divisors,
 *                     DCTELEM *workspace);
 *
 * Note: the code uses 2 stage pipelining in order to improve instructions
 *       scheduling and eliminate stalls (this provides ~15% better
 *       performance for this function on both ARM Cortex-A8 and
 *       ARM Cortex-A9 when compared to the non-pipelined variant).
 *       The instructions which belong to the second stage use different
 *       indentation for better readiability.
 */
asm_function jsimd_quantize_neon

    COEF_BLOCK      .req r0
    DIVISORS        .req r1
    WORKSPACE       .req r2

    RECIPROCAL      .req DIVISORS
    CORRECTION      .req r3
    SHIFT           .req ip
    LOOP_COUNT      .req r4

    vld1.16         {d0, d1, d2, d3}, [WORKSPACE, :128]!
    vabs.s16        q12, q0
    add             CORRECTION, DIVISORS, #(64 * 2)
    add             SHIFT, DIVISORS, #(64 * 6)
    vld1.16         {d20, d21, d22, d23}, [CORRECTION, :128]!
    vabs.s16        q13, q1
    vld1.16         {d16, d17, d18, d19}, [RECIPROCAL, :128]!
    vadd.u16        q12, q12, q10  /* add correction */
    vadd.u16        q13, q13, q11
    vmull.u16       q10, d24, d16  /* multiply by reciprocal */
    vmull.u16       q11, d25, d17
    vmull.u16       q8, d26, d18
    vmull.u16       q9, d27, d19
    vld1.16         {d24, d25, d26, d27}, [SHIFT, :128]!
    vshrn.u32       d20, q10, #16
    vshrn.u32       d21, q11, #16
    vshrn.u32       d22, q8, #16
    vshrn.u32       d23, q9, #16
    vneg.s16        q12, q12
    vneg.s16        q13, q13
    vshr.s16        q2, q0, #15    /* extract sign */
    vshr.s16        q3, q1, #15
    vshl.u16        q14, q10, q12  /* shift */
    vshl.u16        q15, q11, q13

    push            {r4, r5}
    mov             LOOP_COUNT, #3
1:
    vld1.16         {d0, d1, d2, d3}, [WORKSPACE, :128]!
      veor.u16        q14, q14, q2  /* restore sign */
    vabs.s16        q12, q0
    vld1.16         {d20, d21, d22, d23}, [CORRECTION, :128]!
    vabs.s16        q13, q1
      veor.u16        q15, q15, q3
    vld1.16         {d16, d17, d18, d19}, [RECIPROCAL, :128]!
    vadd.u16        q12, q12, q10  /* add correction */
    vadd.u16        q13, q13, q11
    vmull.u16       q10, d24, d16  /* multiply by reciprocal */
    vmull.u16       q11, d25, d17
    vmull.u16       q8, d26, d18
    vmull.u16       q9, d27, d19
      vsub.u16        q14, q14, q2
    vld1.16         {d24, d25, d26, d27}, [SHIFT, :128]!
      vsub.u16        q15, q15, q3
    vshrn.u32       d20, q10, #16
    vshrn.u32       d21, q11, #16
      vst1.16         {d28, d29, d30, d31}, [COEF_BLOCK, :128]!
    vshrn.u32       d22, q8, #16
    vshrn.u32       d23, q9, #16
    vneg.s16        q12, q12
    vneg.s16        q13, q13
    vshr.s16        q2, q0, #15    /* extract sign */
    vshr.s16        q3, q1, #15
    vshl.u16        q14, q10, q12  /* shift */
    vshl.u16        q15, q11, q13
    subs            LOOP_COUNT, LOOP_COUNT, #1
    bne             1b
    pop             {r4, r5}

      veor.u16        q14, q14, q2  /* restore sign */
      veor.u16        q15, q15, q3
      vsub.u16        q14, q14, q2
      vsub.u16        q15, q15, q3
      vst1.16         {d28, d29, d30, d31}, [COEF_BLOCK, :128]!

    bx              lr  /* return */

    .unreq          COEF_BLOCK
    .unreq          DIVISORS
    .unreq          WORKSPACE
    .unreq          RECIPROCAL
    .unreq          CORRECTION
    .unreq          SHIFT
    .unreq          LOOP_COUNT


/*****************************************************************************/

/*
 * GLOBAL(void)
 * jsimd_h2v1_fancy_upsample_neon(int max_v_samp_factor,
 *                                JDIMENSION downsampled_width,
 *                                JSAMPARRAY input_data,
 *                                JSAMPARRAY *output_data_ptr);
 *
 * Note: the use of unaligned writes is the main remaining bottleneck in
 *       this code, which can be potentially solved to get up to tens
 *       of percents performance improvement on Cortex-A8/Cortex-A9.
 */

/*
 * Upsample 16 source pixels to 32 destination pixels. The new 16 source
 * pixels are loaded to q0. The previous 16 source pixels are in q1. The
 * shifted-by-one source pixels are constructed in q2 by using q0 and q1.
 * Register d28 is used for multiplication by 3. Register q15 is used
 * for adding +1 bias.
 */
.macro upsample16 OUTPTR, INPTR
    vld1.8          {q0}, [\INPTR]!
    vmovl.u8        q8, d0
    vext.8          q2, q1, q0, #15
    vmovl.u8        q9, d1
    vaddw.u8        q10, q15, d4
    vaddw.u8        q11, q15, d5
    vmlal.u8        q8, d4, d28
    vmlal.u8        q9, d5, d28
    vmlal.u8        q10, d0, d28
    vmlal.u8        q11, d1, d28
    vmov            q1, q0        /* backup source pixels to q1 */
    vrshrn.u16      d6, q8, #2
    vrshrn.u16      d7, q9, #2
    vshrn.u16       d8, q10, #2
    vshrn.u16       d9, q11, #2
    vst2.8          {d6, d7, d8, d9}, [\OUTPTR]!
.endm

/*
 * Upsample 32 source pixels to 64 destination pixels. Compared to 'usample16'
 * macro, the roles of q0 and q1 registers are reversed for even and odd
 * groups of 16 pixels, that's why "vmov q1, q0" instructions are not needed.
 * Also this unrolling allows to reorder loads and stores to compensate
 * multiplication latency and reduce stalls.
 */
.macro upsample32 OUTPTR, INPTR
    /* even 16 pixels group */
    vld1.8          {q0}, [\INPTR]!
    vmovl.u8        q8, d0
    vext.8          q2, q1, q0, #15
    vmovl.u8        q9, d1
    vaddw.u8        q10, q15, d4
    vaddw.u8        q11, q15, d5
    vmlal.u8        q8, d4, d28
    vmlal.u8        q9, d5, d28
    vmlal.u8        q10, d0, d28
    vmlal.u8        q11, d1, d28
      /* odd 16 pixels group */
      vld1.8          {q1}, [\INPTR]!
    vrshrn.u16      d6, q8, #2
    vrshrn.u16      d7, q9, #2
    vshrn.u16       d8, q10, #2
    vshrn.u16       d9, q11, #2
      vmovl.u8        q8, d2
      vext.8          q2, q0, q1, #15
      vmovl.u8        q9, d3
      vaddw.u8        q10, q15, d4
      vaddw.u8        q11, q15, d5
      vmlal.u8        q8, d4, d28
      vmlal.u8        q9, d5, d28
      vmlal.u8        q10, d2, d28
      vmlal.u8        q11, d3, d28
    vst2.8          {d6, d7, d8, d9}, [\OUTPTR]!
      vrshrn.u16      d6, q8, #2
      vrshrn.u16      d7, q9, #2
      vshrn.u16       d8, q10, #2
      vshrn.u16       d9, q11, #2
      vst2.8          {d6, d7, d8, d9}, [\OUTPTR]!
.endm

/*
 * Upsample a row of WIDTH pixels from INPTR to OUTPTR.
 */
.macro upsample_row OUTPTR, INPTR, WIDTH, TMP1
    /* special case for the first and last pixels */
    sub             \WIDTH, \WIDTH, #1
    add             \OUTPTR, \OUTPTR, #1
    ldrb            \TMP1, [\INPTR, \WIDTH]
    strb            \TMP1, [\OUTPTR, \WIDTH, asl #1]
    ldrb            \TMP1, [\INPTR], #1
    strb            \TMP1, [\OUTPTR, #-1]
    vmov.8          d3[7], \TMP1

    subs            \WIDTH, \WIDTH, #32
    blt             5f
0:  /* process 32 pixels per iteration */
    upsample32      \OUTPTR, \INPTR
    subs            \WIDTH, \WIDTH, #32
    bge             0b
5:
    adds            \WIDTH, \WIDTH, #16
    blt             1f
0:  /* process 16 pixels if needed */
    upsample16      \OUTPTR, \INPTR
    subs            \WIDTH, \WIDTH, #16
1:
    adds            \WIDTH, \WIDTH, #16
    beq             9f

    /* load the remaining 1-15 pixels */
    add             \INPTR, \INPTR, \WIDTH
    tst             \WIDTH, #1
    beq             2f
    sub             \INPTR, \INPTR, #1
    vld1.8          {d0[0]}, [\INPTR]
2:
    tst             \WIDTH, #2
    beq             2f
    vext.8          d0, d0, d0, #6
    sub             \INPTR, \INPTR, #1
    vld1.8          {d0[1]}, [\INPTR]
    sub             \INPTR, \INPTR, #1
    vld1.8          {d0[0]}, [\INPTR]
2:
    tst             \WIDTH, #4
    beq             2f
    vrev64.32       d0, d0
    sub             \INPTR, \INPTR, #1
    vld1.8          {d0[3]}, [\INPTR]
    sub             \INPTR, \INPTR, #1
    vld1.8          {d0[2]}, [\INPTR]
    sub             \INPTR, \INPTR, #1
    vld1.8          {d0[1]}, [\INPTR]
    sub             \INPTR, \INPTR, #1
    vld1.8          {d0[0]}, [\INPTR]
2:
    tst             \WIDTH, #8
    beq             2f
    vmov            d1, d0
    sub             \INPTR, \INPTR, #8
    vld1.8          {d0}, [\INPTR]
2:  /* upsample the remaining pixels */
    vmovl.u8        q8, d0
    vext.8          q2, q1, q0, #15
    vmovl.u8        q9, d1
    vaddw.u8        q10, q15, d4
    vaddw.u8        q11, q15, d5
    vmlal.u8        q8, d4, d28
    vmlal.u8        q9, d5, d28
    vmlal.u8        q10, d0, d28
    vmlal.u8        q11, d1, d28
    vrshrn.u16      d10, q8, #2
    vrshrn.u16      d12, q9, #2
    vshrn.u16       d11, q10, #2
    vshrn.u16       d13, q11, #2
    vzip.8          d10, d11
    vzip.8          d12, d13
    /* store the remaining pixels */
    tst             \WIDTH, #8
    beq             2f
    vst1.8          {d10, d11}, [\OUTPTR]!
    vmov            q5, q6
2:
    tst             \WIDTH, #4
    beq             2f
    vst1.8          {d10}, [\OUTPTR]!
    vmov            d10, d11
2:
    tst             \WIDTH, #2
    beq             2f
    vst1.8          {d10[0]}, [\OUTPTR]!
    vst1.8          {d10[1]}, [\OUTPTR]!
    vst1.8          {d10[2]}, [\OUTPTR]!
    vst1.8          {d10[3]}, [\OUTPTR]!
    vext.8          d10, d10, d10, #4
2:
    tst             \WIDTH, #1
    beq             2f
    vst1.8          {d10[0]}, [\OUTPTR]!
    vst1.8          {d10[1]}, [\OUTPTR]!
2:
9:
.endm

asm_function jsimd_h2v1_fancy_upsample_neon

    MAX_V_SAMP_FACTOR .req r0
    DOWNSAMPLED_WIDTH .req r1
    INPUT_DATA        .req r2
    OUTPUT_DATA_PTR   .req r3
    OUTPUT_DATA       .req OUTPUT_DATA_PTR

    OUTPTR            .req r4
    INPTR             .req r5
    WIDTH             .req ip
    TMP               .req lr

    push            {r4, r5, r6, lr}
    vpush           {d8-d15}

    ldr             OUTPUT_DATA, [OUTPUT_DATA_PTR]
    cmp             MAX_V_SAMP_FACTOR, #0
    ble             99f

    /* initialize constants */
    vmov.u8         d28, #3
    vmov.u16        q15, #1
11:
    ldr             INPTR, [INPUT_DATA], #4
    ldr             OUTPTR, [OUTPUT_DATA], #4
    mov             WIDTH, DOWNSAMPLED_WIDTH
    upsample_row    OUTPTR, INPTR, WIDTH, TMP
    subs            MAX_V_SAMP_FACTOR, MAX_V_SAMP_FACTOR, #1
    bgt             11b

99:
    vpop            {d8-d15}
    pop             {r4, r5, r6, pc}

    .unreq          MAX_V_SAMP_FACTOR
    .unreq          DOWNSAMPLED_WIDTH
    .unreq          INPUT_DATA
    .unreq          OUTPUT_DATA_PTR
    .unreq          OUTPUT_DATA

    .unreq          OUTPTR
    .unreq          INPTR
    .unreq          WIDTH
    .unreq          TMP

.purgem upsample16
.purgem upsample32
.purgem upsample_row


/*****************************************************************************/

/*
 * GLOBAL(JOCTET *)
 * jsimd_huff_encode_one_block(working_state *state, JOCTET *buffer,
 *                             JCOEFPTR block, int last_dc_val,
 *                             c_derived_tbl *dctbl, c_derived_tbl *actbl)
 *
 */

.macro emit_byte BUFFER, PUT_BUFFER, PUT_BITS, ZERO, TMP
    sub             \PUT_BITS, \PUT_BITS, #0x8
    lsr             \TMP, \PUT_BUFFER, \PUT_BITS
    uxtb            \TMP, \TMP
    strb            \TMP, [\BUFFER, #1]!
    cmp             \TMP, #0xff
    /*it eq*/
    strbeq          \ZERO, [\BUFFER, #1]!
.endm

.macro put_bits PUT_BUFFER, PUT_BITS, CODE, SIZE
    /*lsl             \PUT_BUFFER, \PUT_BUFFER, \SIZE*/
    add             \PUT_BITS, \SIZE
    /*orr             \PUT_BUFFER, \PUT_BUFFER, \CODE*/
    orr             \PUT_BUFFER, \CODE, \PUT_BUFFER, lsl \SIZE
.endm

.macro checkbuf15 BUFFER, PUT_BUFFER, PUT_BITS, ZERO, TMP
  cmp               \PUT_BITS, #0x10
  blt               15f
    eor               \ZERO, \ZERO, \ZERO
    emit_byte         \BUFFER, \PUT_BUFFER, \PUT_BITS, \ZERO, \TMP
    emit_byte         \BUFFER, \PUT_BUFFER, \PUT_BITS, \ZERO, \TMP
15:
.endm

.balign 16
jsimd_huff_encode_one_block_neon_consts:
  .byte 0x01
  .byte 0x02
  .byte 0x04
  .byte 0x08
  .byte 0x10
  .byte 0x20
  .byte 0x40
  .byte 0x80

asm_function jsimd_huff_encode_one_block_neon
    push            {r4, r5, r6, r7, r8, r9, r10, r11, lr}
    add             r7, sp, #0x1c
    sub             r4, sp, #0x40
    bfc             r4, #0, #5
    mov             sp, r4           /* align sp on 32 bytes */
    vst1.64         {d8, d9, d10, d11}, [r4, :128]!
    vst1.64         {d12, d13, d14, d15}, [r4, :128]
    sub             sp, #0x140       /* reserve 320 bytes */
    str             r0, [sp, #0x18]  /* working state > sp + Ox18 */
    add             r4, sp, #0x20    /* r4 = t1 */
    ldr             lr, [r7, #0x8]   /* lr = dctbl */
    sub             r10, r1, #0x1    /* r10=buffer-- */
    ldrsh           r1, [r2]
    mov             r9, #0x10
    mov             r8, #0x1
    adr             r5, jsimd_huff_encode_one_block_neon_consts
    /* prepare data */
    vld1.8          {d26}, [r5, :64]
    veor            q8, q8, q8
    veor            q9, q9, q9
    vdup.16         q14, r9
    vdup.16         q15, r8
    veor            q10, q10, q10
    veor            q11, q11, q11
    sub             r1, r1, r3
    add             r9, r2, #0x22
    add             r8, r2, #0x18
    add             r3, r2, #0x36
    vmov.16         d0[0], r1
    vld1.16         {d2[0]}, [r9, :16]
    vld1.16         {d4[0]}, [r8, :16]
    vld1.16         {d6[0]}, [r3, :16]
    add             r1, r2, #0x2
    add             r9, r2, #0x30
    add             r8, r2, #0x26
    add             r3, r2, #0x28
    vld1.16         {d0[1]}, [r1, :16]
    vld1.16         {d2[1]}, [r9, :16]
    vld1.16         {d4[1]}, [r8, :16]
    vld1.16         {d6[1]}, [r3, :16]
    add             r1, r2, #0x10
    add             r9, r2, #0x40
    add             r8, r2, #0x34
    add             r3, r2, #0x1a
    vld1.16         {d0[2]}, [r1, :16]
    vld1.16         {d2[2]}, [r9, :16]
    vld1.16         {d4[2]}, [r8, :16]
    vld1.16         {d6[2]}, [r3, :16]
    add             r1, r2, #0x20
    add             r9, r2, #0x32
    add             r8, r2, #0x42
    add             r3, r2, #0xc
    vld1.16         {d0[3]}, [r1, :16]
    vld1.16         {d2[3]}, [r9, :16]
    vld1.16         {d4[3]}, [r8, :16]
    vld1.16         {d6[3]}, [r3, :16]
    add             r1, r2, #0x12
    add             r9, r2, #0x24
    add             r8, r2, #0x50
    add             r3, r2, #0xe
    vld1.16         {d1[0]}, [r1, :16]
    vld1.16         {d3[0]}, [r9, :16]
    vld1.16         {d5[0]}, [r8, :16]
    vld1.16         {d7[0]}, [r3, :16]
    add             r1, r2, #0x4
    add             r9, r2, #0x16
    add             r8, r2, #0x60
    add             r3, r2, #0x1c
    vld1.16         {d1[1]}, [r1, :16]
    vld1.16         {d3[1]}, [r9, :16]
    vld1.16         {d5[1]}, [r8, :16]
    vld1.16         {d7[1]}, [r3, :16]
    add             r1, r2, #0x6
    add             r9, r2, #0x8
    add             r8, r2, #0x52
    add             r3, r2, #0x2a
    vld1.16         {d1[2]}, [r1, :16]
    vld1.16         {d3[2]}, [r9, :16]
    vld1.16         {d5[2]}, [r8, :16]
    vld1.16         {d7[2]}, [r3, :16]
    add             r1, r2, #0x14
    add             r9, r2, #0xa
    add             r8, r2, #0x44
    add             r3, r2, #0x38
    vld1.16         {d1[3]}, [r1, :16]
    vld1.16         {d3[3]}, [r9, :16]
    vld1.16         {d5[3]}, [r8, :16]
    vld1.16         {d7[3]}, [r3, :16]
    vcgt.s16        q8, q8, q0
    vcgt.s16        q9, q9, q1
    vcgt.s16        q10, q10, q2
    vcgt.s16        q11, q11, q3
    vabs.s16        q0, q0
    vabs.s16        q1, q1
    vabs.s16        q2, q2
    vabs.s16        q3, q3
    veor            q8, q8, q0
    veor            q9, q9, q1
    veor            q10, q10, q2
    veor            q11, q11, q3
    add             r9, r4, #0x20
    add             r8, r4, #0x80
    add             r3, r4, #0xa0
    vclz.i16        q0, q0
    vclz.i16        q1, q1
    vclz.i16        q2, q2
    vclz.i16        q3, q3
    vsub.i16        q0, q14, q0
    vsub.i16        q1, q14, q1
    vsub.i16        q2, q14, q2
    vsub.i16        q3, q14, q3
    vst1.16         {d0, d1, d2, d3}, [r4, :256]
    vst1.16         {d4, d5, d6, d7}, [r9, :256]
    vshl.s16        q0, q15, q0
    vshl.s16        q1, q15, q1
    vshl.s16        q2, q15, q2
    vshl.s16        q3, q15, q3
    vsub.i16        q0, q0, q15
    vsub.i16        q1, q1, q15
    vsub.i16        q2, q2, q15
    vsub.i16        q3, q3, q15
    vand            q8, q8, q0
    vand            q9, q9, q1
    vand            q10, q10, q2
    vand            q11, q11, q3
    vst1.16         {d16, d17, d18, d19}, [r8, :256]
    vst1.16         {d20, d21, d22, d23}, [r3, :256]
    add             r1, r2, #0x46
    add             r9, r2, #0x3a
    add             r8, r2, #0x74
    add             r3, r2, #0x6a
    vld1.16         {d8[0]}, [r1, :16]
    vld1.16         {d10[0]}, [r9, :16]
    vld1.16         {d12[0]}, [r8, :16]
    vld1.16         {d14[0]}, [r3, :16]
    veor            q8, q8, q8
    veor            q9, q9, q9
    veor            q10, q10, q10
    veor            q11, q11, q11
    add             r1, r2, #0x54
    add             r9, r2, #0x2c
    add             r8, r2, #0x76
    add             r3, r2, #0x78
    vld1.16         {d8[1]}, [r1, :16]
    vld1.16         {d10[1]}, [r9, :16]
    vld1.16         {d12[1]}, [r8, :16]
    vld1.16         {d14[1]}, [r3, :16]
    add             r1, r2, #0x62
    add             r9, r2, #0x1e
    add             r8, r2, #0x68
    add             r3, r2, #0x7a
    vld1.16         {d8[2]}, [r1, :16]
    vld1.16         {d10[2]}, [r9, :16]
    vld1.16         {d12[2]}, [r8, :16]
    vld1.16         {d14[2]}, [r3, :16]
    add             r1, r2, #0x70
    add             r9, r2, #0x2e
    add             r8, r2, #0x5a
    add             r3, r2, #0x6c
    vld1.16         {d8[3]}, [r1, :16]
    vld1.16         {d10[3]}, [r9, :16]
    vld1.16         {d12[3]}, [r8, :16]
    vld1.16         {d14[3]}, [r3, :16]
    add             r1, r2, #0x72
    add             r9, r2, #0x3c
    add             r8, r2, #0x4c
    add             r3, r2, #0x5e
    vld1.16         {d9[0]}, [r1, :16]
    vld1.16         {d11[0]}, [r9, :16]
    vld1.16         {d13[0]}, [r8, :16]
    vld1.16         {d15[0]}, [r3, :16]
    add             r1, r2, #0x64
    add             r9, r2, #0x4a
    add             r8, r2, #0x3e
    add             r3, r2, #0x6e
    vld1.16         {d9[1]}, [r1, :16]
    vld1.16         {d11[1]}, [r9, :16]
    vld1.16         {d13[1]}, [r8, :16]
    vld1.16         {d15[1]}, [r3, :16]
    add             r1, r2, #0x56
    add             r9, r2, #0x58
    add             r8, r2, #0x4e
    add             r3, r2, #0x7c
    vld1.16         {d9[2]}, [r1, :16]
    vld1.16         {d11[2]}, [r9, :16]
    vld1.16         {d13[2]}, [r8, :16]
    vld1.16         {d15[2]}, [r3, :16]
    add             r1, r2, #0x48
    add             r9, r2, #0x66
    add             r8, r2, #0x5c
    add             r3, r2, #0x7e
    vld1.16         {d9[3]}, [r1, :16]
    vld1.16         {d11[3]}, [r9, :16]
    vld1.16         {d13[3]}, [r8, :16]
    vld1.16         {d15[3]}, [r3, :16]
    vcgt.s16        q8, q8, q4
    vcgt.s16        q9, q9, q5
    vcgt.s16        q10, q10, q6
    vcgt.s16        q11, q11, q7
    vabs.s16        q4, q4
    vabs.s16        q5, q5
    vabs.s16        q6, q6
    vabs.s16        q7, q7
    veor            q8, q8, q4
    veor            q9, q9, q5
    veor            q10, q10, q6
    veor            q11, q11, q7
    add             r1, r4, #0x40
    add             r9, r4, #0x60
    add             r8, r4, #0xc0
    add             r3, r4, #0xe0
    vclz.i16        q4, q4
    vclz.i16        q5, q5
    vclz.i16        q6, q6
    vclz.i16        q7, q7
    vsub.i16        q4, q14, q4
    vsub.i16        q5, q14, q5
    vsub.i16        q6, q14, q6
    vsub.i16        q7, q14, q7
    vst1.16         {d8, d9, d10, d11}, [r1, :256]
    vst1.16         {d12, d13, d14, d15}, [r9, :256]
    vshl.s16        q4, q15, q4
    vshl.s16        q5, q15, q5
    vshl.s16        q6, q15, q6
    vshl.s16        q7, q15, q7
    vsub.i16        q4, q4, q15
    vsub.i16        q5, q5, q15
    vsub.i16        q6, q6, q15
    vsub.i16        q7, q7, q15
    vand            q8, q8, q4
    vand            q9, q9, q5
    vand            q10, q10, q6
    vand            q11, q11, q7
    vst1.16         {d16, d17, d18, d19}, [r8, :256]
    vst1.16         {d20, d21, d22, d23}, [r3, :256]
    ldr             r12, [r7, #0xc]       /* r12 = actbl */
    add             r1, lr, #0x400        /* r1 = dctbl->ehufsi */
    mov             r9, r12               /* r9 = actbl */
    add             r6, r4, #0x80         /* r6 = t2 */
    ldr             r11, [r0, #0x8]       /* r11 = put_buffer */
    ldr             r4, [r0, #0xc]        /* r4  = put_bits */
    ldrh            r2, [r6, #-128]       /* r2  = nbits */
    ldrh            r3, [r6]              /* r3  = temp2 & (((JLONG)1)<<nbits) - 1; */
    ldr             r0, [lr, r2, lsl #2]
    ldrb            r5, [r1, r2]
    put_bits        r11, r4, r0, r5
    checkbuf15      r10, r11, r4, r5, r0
    put_bits        r11, r4, r3, r2
    checkbuf15      r10, r11, r4, r5, r0
    mov             lr, r6                /* lr = t2 */
    add             r5, r9, #0x400        /* r5 = actbl->ehufsi */
    ldrsb           r6, [r5, #0xf0]       /* r6 = actbl->ehufsi[0xf0] */
    veor            q8, q8, q8
    vceq.i16        q0, q0, q8
    vceq.i16        q1, q1, q8
    vceq.i16        q2, q2, q8
    vceq.i16        q3, q3, q8
    vceq.i16        q4, q4, q8
    vceq.i16        q5, q5, q8
    vceq.i16        q6, q6, q8
    vceq.i16        q7, q7, q8
    vmovn.i16       d0, q0
    vmovn.i16       d2, q1
    vmovn.i16       d4, q2
    vmovn.i16       d6, q3
    vmovn.i16       d8, q4
    vmovn.i16       d10, q5
    vmovn.i16       d12, q6
    vmovn.i16       d14, q7
    vand            d0, d0, d26
    vand            d2, d2, d26
    vand            d4, d4, d26
    vand            d6, d6, d26
    vand            d8, d8, d26
    vand            d10, d10, d26
    vand            d12, d12, d26
    vand            d14, d14, d26
    vpadd.i8        d0, d0, d2
    vpadd.i8        d4, d4, d6
    vpadd.i8        d8, d8, d10
    vpadd.i8        d12, d12, d14
    vpadd.i8        d0, d0, d4
    vpadd.i8        d8, d8, d12
    vpadd.i8        d0, d0, d8
    vmov.32         r1, d0[1]
    vmov.32         r8, d0[0]
    mvn             r1, r1
    mvn             r8, r8
    lsrs            r1, r1, #0x1
    rrx             r8, r8            /* shift in last r1 bit while shifting out DC bit */
    rbit            r1, r1            /* r1 = index1 */
    rbit            r8, r8            /* r8 = index0 */
    ldr             r0, [r9, #0x3c0]  /* r0 = actbl->ehufco[0xf0] */
    str             r1, [sp, #0x14]   /* index1 > sp + 0x14 */
    cmp             r8, #0x0
    beq             6f
1:
    clz             r2, r8
    add             lr, lr, r2, lsl #1
    lsl             r8, r8, r2
    ldrh            r1, [lr, #-126]
2:
    cmp             r2, #0x10
    blt             3f
    sub             r2, r2, #0x10
    put_bits        r11, r4, r0, r6
    cmp             r4, #0x10
    blt             2b
    eor             r3, r3, r3
    emit_byte       r10, r11, r4, r3, r12
    emit_byte       r10, r11, r4, r3, r12
    b               2b
3:
    add             r2, r1, r2, lsl #4
    ldrh            r3, [lr, #2]!
    ldr             r12, [r9, r2, lsl #2]
    ldrb            r2, [r5, r2]
    put_bits        r11, r4, r12, r2
    checkbuf15      r10, r11, r4, r2, r12
    put_bits        r11, r4, r3, r1
    checkbuf15      r10, r11, r4, r2, r12
    lsls            r8, r8, #0x1
    bne             1b
6:
    add             r12, sp, #0x20   /* r12 = t1 */
    ldr             r8, [sp, #0x14]  /* r8 = index1 */
    adds            r12, #0xc0       /* r12 = t2 + (DCTSIZE2/2) */
    cmp             r8, #0x0
    beq             6f
    clz             r2, r8
    sub             r12, r12, lr
    lsl             r8, r8, r2
    add             r2, r2, r12, lsr #1
    add             lr, lr, r2, lsl #1
    b               7f
1:
    clz             r2, r8
    add             lr, lr, r2, lsl #1
    lsl             r8, r8, r2
7:
    ldrh            r1, [lr, #-126]
2:
    cmp             r2, #0x10
    blt             3f
    sub             r2, r2, #0x10
    put_bits        r11, r4, r0, r6
    cmp             r4, #0x10
    blt             2b
    eor             r3, r3, r3
    emit_byte       r10, r11, r4, r3, r12
    emit_byte       r10, r11, r4, r3, r12
    b               2b
3:
    add             r2, r1, r2, lsl #4
    ldrh            r3, [lr, #2]!
    ldr             r12, [r9, r2, lsl #2]
    ldrb            r2, [r5, r2]
    put_bits        r11, r4, r12, r2
    checkbuf15      r10, r11, r4, r2, r12
    put_bits        r11, r4, r3, r1
    checkbuf15      r10, r11, r4, r2, r12
    lsls            r8, r8, #0x1
    bne             1b
6:
    add             r0, sp, #0x20
    add             r0, #0xfe
    cmp             lr, r0
    bhs             1f
    ldr             r1, [r9]
    ldrb            r0, [r5]
    put_bits        r11, r4, r1, r0
    checkbuf15      r10, r11, r4, r0, r1
1:
    ldr             r12, [sp, #0x18]
    str             r11, [r12, #0x8]
    str             r4, [r12, #0xc]
    add             r0, r10, #0x1
    add             r4, sp, #0x140
    vld1.64         {d8, d9, d10, d11}, [r4, :128]!
    vld1.64         {d12, d13, d14, d15}, [r4, :128]
    sub             r4, r7, #0x1c
    mov             sp, r4
    pop             {r4, r5, r6, r7, r8, r9, r10, r11, pc}

.purgem emit_byte
.purgem put_bits
.purgem checkbuf15
