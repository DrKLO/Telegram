/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * This is a fast-and-accurate implementation of inverse Discrete Cosine
 * Transform (IDCT) for ARMv6+. It also performs dequantization of the input
 * coefficients just like other methods.
 *
 * This implementation is based on the scaled 1-D DCT algorithm proposed by
 * Arai, Agui, and Nakajima. The following code is based on the figure 4-8
 * on page 52 of the JPEG textbook by Pennebaker and Mitchell. Coefficients
 * are (almost) directly mapped into registers.
 *
 * The accuracy is achieved by using SMULWy and SMLAWy instructions. Both
 * multiply 32 bits by 16 bits and store the top 32 bits of the result. It
 * makes 32-bit fixed-point arithmetic possible without overflow. That is
 * why jpeg_idct_ifast(), which is written in C, cannot be improved.
 *
 * More tricks are used to gain more speed. First of all, we use as many
 * registers as possible. ARM processor has 16 registers including sp (r13)
 * and pc (r15), so only 14 registers can be used without limitations. In
 * general, we let r0 to r7 hold the coefficients; r10 and r11 hold four
 * 16-bit constants; r12 and r14 hold two of the four arguments; and r8 hold
 * intermediate value. In the second pass, r9 is the loop counter. In the
 * first pass, r8 to r11 are used to hold quantization values, so the loop
 * counter is held by sp. Yes, the stack pointer. Since it must be aligned
 * to 4-byte boundary all the time, we align it to 32-byte boundary and use
 * bit 3 to bit 5. As the result, we actually use 14.1 registers. :-)
 *
 * Second, we rearrange quantization values to access them sequentially. The
 * table is first transposed, and the new columns are placed in the order of
 * 7, 5, 1, 3, 0, 2, 4, 6. Thus we can use LDMDB to load four values at a
 * time. Rearranging coefficients also helps, but that requires to change a
 * dozen of files, which seems not worth it. In addition, we choose to scale
 * up quantization values by 13 bits, so the coefficients are scaled up by
 * 16 bits after both passes. Then we can pack and saturate them two at a
 * time using PKHTB and USAT16 instructions.
 *
 * Third, we reorder the instructions to avoid bubbles in the pipeline. This
 * is done by hand accroding to the cycle timings and the interlock behavior
 * described in the technical reference manual of ARM1136JF-S. We also take
 * advantage of dual issue processors by interleaving instructions with
 * dependencies. It has been benchmarked on four devices and all the results
 * showed distinguishable improvements. Note that PLD instructions actually
 * slow things down, so they are removed at the last minute. In the future,
 * this might be futher improved using a system profiler.
 */

#ifdef __arm__
#include <machine/cpu-features.h>
#endif

#if __ARM_ARCH__ >= 6

// void armv6_idct(short *coefs, int *quans, unsigned char *rows, int col)
    .arm
    .text
    .align
    .global armv6_idct
    .func   armv6_idct

armv6_idct:
    // Push everything except sp (r13) and pc (r15).
    stmdb   sp!, {r4, r5, r6, r7, r8, r9, r10, r11, r12, r14}

    // r12 = quans, r14 = coefs.
    sub     r4, sp, #236
    bic     sp, r4, #31
    add     r5, sp, #224
    add     r12, r1, #256
    stm     r5, {r2, r3, r4}
    add     r14, r0, #16

pass1_head:
    // Load quantization values. (q[0, 2, 4, 6])
    ldmdb   r12!, {r8, r9, r10, r11}

    // Load coefficients. (c[4, 1, 2, 3, 0, 5, 6, 7])
    ldrsh   r4, [r14, #-2] !
    ldrsh   r1, [r14, #16]
    ldrsh   r2, [r14, #32]
    ldrsh   r3, [r14, #48]
    ldrsh   r0, [r14, #64]
    ldrsh   r5, [r14, #80]
    ldrsh   r6, [r14, #96]
    ldrsh   r7, [r14, #112]

    // r4 = q[0] * c[0];
    mul     r4, r8, r4

    // Check if ACs are all zero.
    cmp     r0, #0
    orreqs  r8, r1, r2
    orreqs  r8, r3, r5
    orreqs  r8, r6, r7
    beq     pass1_zero

    // Step 1: Dequantizations.

    // r2 = q[2] * c[2];
    // r0 = q[4] * c[4] + r4;
    // r6 = q[6] * c[6] + r2;
    mul     r2, r9, r2
    mla     r0, r10, r0, r4
    mla     r6, r11, r6, r2

    // Load quantization values. (q[7, 5, 1, 3])
    ldmdb   r12!, {r8, r9, r10, r11}

    // r4 = r4 * 2 - r0 = -(r0 - r4 * 2);
    // r2 = r2 * 2 - r6 = -(r6 - r2 * 2);
    rsb     r4, r0, r4, lsl #1
    rsb     r2, r6, r2, lsl #1

    // r7 = q[7] * c[7];
    // r5 = q[5] * c[5];
    // r1 = q[1] * c[1] + r7;
    // r3 = q[3] * c[3] + r5;
    mul     r7, r8, r7
    mul     r5, r9, r5
    mla     r1, r10, r1, r7
    mla     r3, r11, r3, r5

    // Load constants.
    ldrd    r10, constants

    // Step 2: Rotations and Butterflies.

    // r7 = r1 - r7 * 2;
    // r1 = r1 - r3;
    // r5 = r5 * 2 - r3 = -(r3 - r5 * 2);
    // r3 = r1 + r3 * 2;
    // r8 = r5 + r7;
    sub     r7, r1, r7, lsl #1
    sub     r1, r1, r3
    rsb     r5, r3, r5, lsl #1
    add     r3, r1, r3, lsl #1
    add     r8, r5, r7

    // r2 = r2 * 1.41421 = r2 * 27146 / 65536 + r2;
    // r8 = r8 * 1.84776 / 8 = r8 * 15137 / 65536;
    // r1 = r1 * 1.41421 = r1 * 27146 / 65536 + r1;
    smlawt  r2, r2, r10, r2
    smulwb  r8, r8, r10
    smlawt  r1, r1, r10, r1

    // r0 = r0 + r6;
    // r2 = r2 - r6;
    // r6 = r0 - r6 * 2;
    add     r0, r0, r6
    sub     r2, r2, r6
    sub     r6, r0, r6, lsl #1

    // r5 = r5 * -2.61313 / 8 + r8 = r5 * -21407 / 65536 + r8;
    // r8 = r7 * -1.08239 / 8 + r8 = r7 * -8867 / 65536 + r8;
    smlawt  r5, r5, r11, r8
    smlawb  r8, r7, r11, r8

    // r4 = r4 + r2;
    // r0 = r0 + r3;
    // r2 = r4 - r2 * 2;
    add     r4, r4, r2
    add     r0, r0, r3
    sub     r2, r4, r2, lsl #1

    // r7 = r5 * 8 - r3 = -(r3 - r5 * 8);
    // r3 = r0 - r3 * 2;
    // r1 = r1 - r7;
    // r4 = r4 + r7;
    // r5 = r8 * 8 - r1 = -(r1 - r8 * 8);
    // r7 = r4 - r7 * 2;
    rsb     r7, r3, r5, lsl #3
    sub     r3, r0, r3, lsl #1
    sub     r1, r1, r7
    add     r4, r4, r7
    rsb     r5, r1, r8, lsl #3
    sub     r7, r4, r7, lsl #1

    // r2 = r2 + r1;
    // r6 = r6 + r5;
    // r1 = r2 - r1 * 2;
    // r5 = r6 - r5 * 2;
    add     r2, r2, r1
    add     r6, r6, r5
    sub     r1, r2, r1, lsl #1
    sub     r5, r6, r5, lsl #1

    // Step 3: Reorder and Save.

    str     r0, [sp, #-4] !
    str     r4, [sp, #32]
    str     r2, [sp, #64]
    str     r6, [sp, #96]
    str     r5, [sp, #128]
    str     r1, [sp, #160]
    str     r7, [sp, #192]
    str     r3, [sp, #224]
    b       pass1_tail

    // Precomputed 16-bit constants: 27146, 15137, -21407, -8867.
    // Put them in the middle since LDRD only accepts offsets from -255 to 255.
    .align  3
constants:
    .word   0x6a0a3b21
    .word   0xac61dd5d

pass1_zero:
    str     r4, [sp, #-4] !
    str     r4, [sp, #32]
    str     r4, [sp, #64]
    str     r4, [sp, #96]
    str     r4, [sp, #128]
    str     r4, [sp, #160]
    str     r4, [sp, #192]
    str     r4, [sp, #224]
    sub     r12, r12, #16

pass1_tail:
    ands    r9, sp, #31
    bne     pass1_head

    // r12 = rows, r14 = col.
    ldr     r12, [sp, #256]
    ldr     r14, [sp, #260]

    // Load constants.
    ldrd    r10, constants

pass2_head:
    // Load coefficients. (c[0, 1, 2, 3, 4, 5, 6, 7])
    ldmia   sp!, {r0, r1, r2, r3, r4, r5, r6, r7}

    // r0 = r0 + 0x00808000;
    add     r0, r0, #0x00800000
    add     r0, r0, #0x00008000

    // Step 1: Analog to the first pass.

    // r0 = r0 + r4;
    // r6 = r6 + r2;
    add     r0, r0, r4
    add     r6, r6, r2

    // r4 = r0 - r4 * 2;
    // r2 = r2 * 2 - r6 = -(r6 - r2 * 2);
    sub     r4, r0, r4, lsl #1
    rsb     r2, r6, r2, lsl #1

    // r1 = r1 + r7;
    // r3 = r3 + r5;
    add     r1, r1, r7
    add     r3, r3, r5

    // Step 2: Rotations and Butterflies.

    // r7 = r1 - r7 * 2;
    // r1 = r1 - r3;
    // r5 = r5 * 2 - r3 = -(r3 - r5 * 2);
    // r3 = r1 + r3 * 2;
    // r8 = r5 + r7;
    sub     r7, r1, r7, lsl #1
    sub     r1, r1, r3
    rsb     r5, r3, r5, lsl #1
    add     r3, r1, r3, lsl #1
    add     r8, r5, r7

    // r2 = r2 * 1.41421 = r2 * 27146 / 65536 + r2;
    // r8 = r8 * 1.84776 / 8 = r8 * 15137 / 65536;
    // r1 = r1 * 1.41421 = r1 * 27146 / 65536 + r1;
    smlawt  r2, r2, r10, r2
    smulwb  r8, r8, r10
    smlawt  r1, r1, r10, r1

    // r0 = r0 + r6;
    // r2 = r2 - r6;
    // r6 = r0 - r6 * 2;
    add     r0, r0, r6
    sub     r2, r2, r6
    sub     r6, r0, r6, lsl #1

    // r5 = r5 * -2.61313 / 8 + r8 = r5 * -21407 / 65536 + r8;
    // r8 = r7 * -1.08239 / 8 + r8 = r7 * -8867 / 65536 + r8;
    smlawt  r5, r5, r11, r8
    smlawb  r8, r7, r11, r8

    // r4 = r4 + r2;
    // r0 = r0 + r3;
    // r2 = r4 - r2 * 2;
    add     r4, r4, r2
    add     r0, r0, r3
    sub     r2, r4, r2, lsl #1

    // r7 = r5 * 8 - r3 = -(r3 - r5 * 8);
    // r3 = r0 - r3 * 2;
    // r1 = r1 - r7;
    // r4 = r4 + r7;
    // r5 = r8 * 8 - r1 = -(r1 - r8 * 8);
    // r7 = r4 - r7 * 2;
    rsb     r7, r3, r5, lsl #3
    sub     r3, r0, r3, lsl #1
    sub     r1, r1, r7
    add     r4, r4, r7
    rsb     r5, r1, r8, lsl #3
    sub     r7, r4, r7, lsl #1

    // r2 = r2 + r1;
    // r6 = r6 + r5;
    // r1 = r2 - r1 * 2;
    // r5 = r6 - r5 * 2;
    add     r2, r2, r1
    add     r6, r6, r5
    sub     r1, r2, r1, lsl #1
    sub     r5, r6, r5, lsl #1

    // Step 3: Reorder and Save.

    // Load output pointer.
    ldr     r8, [r12], #4

    // For little endian: r6, r2, r4, r0, r3, r7, r1, r5.
    pkhtb   r6, r6, r4, asr #16
    pkhtb   r2, r2, r0, asr #16
    pkhtb   r3, r3, r1, asr #16
    pkhtb   r7, r7, r5, asr #16
    usat16  r6, #8, r6
    usat16  r2, #8, r2
    usat16  r3, #8, r3
    usat16  r7, #8, r7
    orr     r0, r2, r6, lsl #8
    orr     r1, r7, r3, lsl #8

#ifdef __ARMEB__
    // Reverse bytes for big endian.
    rev     r0, r0
    rev     r1, r1
#endif

    // Use STR instead of STRD to support unaligned access.
    str     r0, [r8, r14] !
    str     r1, [r8, #4]

pass2_tail:
    adds    r9, r9, #0x10000000
    bpl     pass2_head

    ldr     sp, [sp, #8]
    add     sp, sp, #236

    ldmia   sp!, {r4, r5, r6, r7, r8, r9, r10, r11, r12, r14}
    bx      lr
    .endfunc

#endif
