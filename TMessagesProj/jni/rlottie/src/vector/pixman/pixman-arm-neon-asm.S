/*
 * Copyright © 2009 Nokia Corporation
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice (including the next
 * paragraph) shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *
 * Author:  Siarhei Siamashka (siarhei.siamashka@nokia.com)
 */

/*
 * This file contains implementations of NEON optimized pixel processing
 * functions. There is no full and detailed tutorial, but some functions
 * (those which are exposing some new or interesting features) are
 * extensively commented and can be used as examples.
 *
 * You may want to have a look at the comments for following functions:
 *  - pixman_composite_over_8888_0565_asm_neon
 *  - pixman_composite_over_n_8_0565_asm_neon
 */

/* Prevent the stack from becoming executable for no reason... */
#if defined(__linux__) && defined(__ELF__)
.section .note.GNU-stack,"",%progbits
#endif

    .text
    .fpu neon
    .arch armv7a
    .object_arch armv4
    .eabi_attribute 10, 0 /* suppress Tag_FP_arch */
    .eabi_attribute 12, 0 /* suppress Tag_Advanced_SIMD_arch */
    .arm
    .altmacro
    .p2align 2


//#include "pixman-arm-asm.h"
/* Supplementary macro for setting function attributes */
.macro pixman_asm_function fname
    .func fname
    .global fname
#ifdef __ELF__
    .hidden fname
    .type fname, %function
#endif
fname:
.endm

//#include "pixman-private.h"
/*
 * The defines which are shared between C and assembly code
 */

/* bilinear interpolation precision (must be < 8) */
#define BILINEAR_INTERPOLATION_BITS 7
#define BILINEAR_INTERPOLATION_RANGE (1 << BILINEAR_INTERPOLATION_BITS)

#include "pixman-arm-neon-asm.h"

/* Global configuration options and preferences */

/*
 * The code can optionally make use of unaligned memory accesses to improve
 * performance of handling leading/trailing pixels for each scanline.
 * Configuration variable RESPECT_STRICT_ALIGNMENT can be set to 0 for
 * example in linux if unaligned memory accesses are not configured to
 * generate.exceptions.
 */
.set RESPECT_STRICT_ALIGNMENT, 1

/*
 * Set default prefetch type. There is a choice between the following options:
 *
 * PREFETCH_TYPE_NONE (may be useful for the ARM cores where PLD is set to work
 * as NOP to workaround some HW bugs or for whatever other reason)
 *
 * PREFETCH_TYPE_SIMPLE (may be useful for simple single-issue ARM cores where
 * advanced prefetch intruduces heavy overhead)
 *
 * PREFETCH_TYPE_ADVANCED (useful for superscalar cores such as ARM Cortex-A8
 * which can run ARM and NEON instructions simultaneously so that extra ARM
 * instructions do not add (many) extra cycles, but improve prefetch efficiency)
 *
 * Note: some types of function can't support advanced prefetch and fallback
 *       to simple one (those which handle 24bpp pixels)
 */
.set PREFETCH_TYPE_DEFAULT, PREFETCH_TYPE_ADVANCED

/* Prefetch distance in pixels for simple prefetch */
.set PREFETCH_DISTANCE_SIMPLE, 64

/*
 * Implementation of pixman_composite_over_8888_0565_asm_neon
 *
 * This function takes a8r8g8b8 source buffer, r5g6b5 destination buffer and
 * performs OVER compositing operation. Function fast_composite_over_8888_0565
 * from pixman-fast-path.c does the same in C and can be used as a reference.
 *
 * First we need to have some NEON assembly code which can do the actual
 * operation on the pixels and provide it to the template macro.
 *
 * Template macro quite conveniently takes care of emitting all the necessary
 * code for memory reading and writing (including quite tricky cases of
 * handling unaligned leading/trailing pixels), so we only need to deal with
 * the data in NEON registers.
 *
 * NEON registers allocation in general is recommented to be the following:
 * d0,  d1,  d2,  d3  - contain loaded source pixel data
 * d4,  d5,  d6,  d7  - contain loaded destination pixels (if they are needed)
 * d24, d25, d26, d27 - contain loading mask pixel data (if mask is used)
 * d28, d29, d30, d31 - place for storing the result (destination pixels)
 *
 * As can be seen above, four 64-bit NEON registers are used for keeping
 * intermediate pixel data and up to 8 pixels can be processed in one step
 * for 32bpp formats (16 pixels for 16bpp, 32 pixels for 8bpp).
 *
 * This particular function uses the following registers allocation:
 * d0,  d1,  d2,  d3  - contain loaded source pixel data
 * d4,  d5            - contain loaded destination pixels (they are needed)
 * d28, d29           - place for storing the result (destination pixels)
 */

/*
 * Step one. We need to have some code to do some arithmetics on pixel data.
 * This is implemented as a pair of macros: '*_head' and '*_tail'. When used
 * back-to-back, they take pixel data from {d0, d1, d2, d3} and {d4, d5},
 * perform all the needed calculations and write the result to {d28, d29}.
 * The rationale for having two macros and not just one will be explained
 * later. In practice, any single monolitic function which does the work can
 * be split into two parts in any arbitrary way without affecting correctness.
 *
 * There is one special trick here too. Common template macro can optionally
 * make our life a bit easier by doing R, G, B, A color components
 * deinterleaving for 32bpp pixel formats (and this feature is used in
 * 'pixman_composite_over_8888_0565_asm_neon' function). So it means that
 * instead of having 8 packed pixels in {d0, d1, d2, d3} registers, we
 * actually use d0 register for blue channel (a vector of eight 8-bit
 * values), d1 register for green, d2 for red and d3 for alpha. This
 * simple conversion can be also done with a few NEON instructions:
 *
 * Packed to planar conversion:
 *  vuzp.8 d0, d1
 *  vuzp.8 d2, d3
 *  vuzp.8 d1, d3
 *  vuzp.8 d0, d2
 *
 * Planar to packed conversion:
 *  vzip.8 d0, d2
 *  vzip.8 d1, d3
 *  vzip.8 d2, d3
 *  vzip.8 d0, d1
 *
 * But pixel can be loaded directly in planar format using VLD4.8 NEON
 * instruction. It is 1 cycle slower than VLD1.32, so this is not always
 * desirable, that's why deinterleaving is optional.
 *
 * But anyway, here is the code:
 */

/*
 * OK, now we got almost everything that we need. Using the above two
 * macros, the work can be done right. But now we want to optimize
 * it a bit. ARM Cortex-A8 is an in-order core, and benefits really
 * a lot from good code scheduling and software pipelining.
 *
 * Let's construct some code, which will run in the core main loop.
 * Some pseudo-code of the main loop will look like this:
 *   head
 *   while (...) {
 *     tail
 *     head
 *   }
 *   tail
 *
 * It may look a bit weird, but this setup allows to hide instruction
 * latencies better and also utilize dual-issue capability more
 * efficiently (make pairs of load-store and ALU instructions).
 *
 * So what we need now is a '*_tail_head' macro, which will be used
 * in the core main loop. A trivial straightforward implementation
 * of this macro would look like this:
 *
 *   pixman_composite_over_8888_0565_process_pixblock_tail
 *   vst1.16     {d28, d29}, [DST_W, :128]!
 *   vld1.16     {d4, d5}, [DST_R, :128]!
 *   vld4.32     {d0, d1, d2, d3}, [SRC]!
 *   pixman_composite_over_8888_0565_process_pixblock_head
 *   cache_preload 8, 8
 *
 * Now it also got some VLD/VST instructions. We simply can't move from
 * processing one block of pixels to the other one with just arithmetics.
 * The previously processed data needs to be written to memory and new
 * data needs to be fetched. Fortunately, this main loop does not deal
 * with partial leading/trailing pixels and can load/store a full block
 * of pixels in a bulk. Additionally, destination buffer is already
 * 16 bytes aligned here (which is good for performance).
 *
 * New things here are DST_R, DST_W, SRC and MASK identifiers. These
 * are the aliases for ARM registers which are used as pointers for
 * accessing data. We maintain separate pointers for reading and writing
 * destination buffer (DST_R and DST_W).
 *
 * Another new thing is 'cache_preload' macro. It is used for prefetching
 * data into CPU L2 cache and improve performance when dealing with large
 * images which are far larger than cache size. It uses one argument
 * (actually two, but they need to be the same here) - number of pixels
 * in a block. Looking into 'pixman-arm-neon-asm.h' can provide some
 * details about this macro. Moreover, if good performance is needed
 * the code from this macro needs to be copied into '*_tail_head' macro
 * and mixed with the rest of code for optimal instructions scheduling.
 * We are actually doing it below.
 *
 * Now after all the explanations, here is the optimized code.
 * Different instruction streams (originaling from '*_head', '*_tail'
 * and 'cache_preload' macro) use different indentation levels for
 * better readability. Actually taking the code from one of these
 * indentation levels and ignoring a few VLD/VST instructions would
 * result in exactly the code from '*_head', '*_tail' or 'cache_preload'
 * macro!
 */

/*
 * And now the final part. We are using 'generate_composite_function' macro
 * to put all the stuff together. We are specifying the name of the function
 * which we want to get, number of bits per pixel for the source, mask and
 * destination (0 if unused, like mask in this case). Next come some bit
 * flags:
 *   FLAG_DST_READWRITE      - tells that the destination buffer is both read
 *                             and written, for write-only buffer we would use
 *                             FLAG_DST_WRITEONLY flag instead
 *   FLAG_DEINTERLEAVE_32BPP - tells that we prefer to work with planar data
 *                             and separate color channels for 32bpp format.
 * The next things are:
 *  - the number of pixels processed per iteration (8 in this case, because
 *    that's the maximum what can fit into four 64-bit NEON registers).
 *  - prefetch distance, measured in pixel blocks. In this case it is 5 times
 *    by 8 pixels. That would be 40 pixels, or up to 160 bytes. Optimal
 *    prefetch distance can be selected by running some benchmarks.
 *
 * After that we specify some macros, these are 'default_init',
 * 'default_cleanup' here which are empty (but it is possible to have custom
 * init/cleanup macros to be able to save/restore some extra NEON registers
 * like d8-d15 or do anything else) followed by
 * 'pixman_composite_over_8888_0565_process_pixblock_head',
 * 'pixman_composite_over_8888_0565_process_pixblock_tail' and
 * 'pixman_composite_over_8888_0565_process_pixblock_tail_head'
 * which we got implemented above.
 *
 * The last part is the NEON registers allocation scheme.
 */

/******************************************************************************/

/******************************************************************************/
 .macro pixman_composite_out_reverse_8888_8888_process_pixblock_head
     vmvn.8      d24, d3  /* get inverted alpha */
     /* do alpha blending */
     vmull.u8    q8, d24, d4
     vmull.u8    q9, d24, d5
     vmull.u8    q10, d24, d6
     vmull.u8    q11, d24, d7
 .endm

 .macro pixman_composite_out_reverse_8888_8888_process_pixblock_tail
     vrshr.u16   q14, q8, #8
     vrshr.u16   q15, q9, #8
     vrshr.u16   q12, q10, #8
     vrshr.u16   q13, q11, #8
     vraddhn.u16 d28, q14, q8
     vraddhn.u16 d29, q15, q9
     vraddhn.u16 d30, q12, q10
     vraddhn.u16 d31, q13, q11
 .endm

/******************************************************************************/

.macro pixman_composite_over_8888_8888_process_pixblock_head
    pixman_composite_out_reverse_8888_8888_process_pixblock_head
.endm

.macro pixman_composite_over_8888_8888_process_pixblock_tail
    pixman_composite_out_reverse_8888_8888_process_pixblock_tail
    vqadd.u8    q14, q0, q14
    vqadd.u8    q15, q1, q15
.endm

.macro pixman_composite_over_8888_8888_process_pixblock_tail_head
    vld4.8      {d4, d5, d6, d7}, [DST_R, :128]!
        vrshr.u16   q14, q8, #8
                                    PF add PF_X, PF_X, #8
                                    PF tst PF_CTL, #0xF
        vrshr.u16   q15, q9, #8
        vrshr.u16   q12, q10, #8
        vrshr.u16   q13, q11, #8
                                    PF addne PF_X, PF_X, #8
                                    PF subne PF_CTL, PF_CTL, #1
        vraddhn.u16 d28, q14, q8
        vraddhn.u16 d29, q15, q9
                                    PF cmp PF_X, ORIG_W
        vraddhn.u16 d30, q12, q10
        vraddhn.u16 d31, q13, q11
        vqadd.u8    q14, q0, q14
        vqadd.u8    q15, q1, q15
    fetch_src_pixblock
                                    PF pld, [PF_SRC, PF_X, lsl #src_bpp_shift]
    vmvn.8      d22, d3
                                    PF pld, [PF_DST, PF_X, lsl #dst_bpp_shift]
        vst4.8      {d28, d29, d30, d31}, [DST_W, :128]!
                                    PF subge PF_X, PF_X, ORIG_W
    vmull.u8    q8, d22, d4
                                    PF subges PF_CTL, PF_CTL, #0x10
    vmull.u8    q9, d22, d5
                                    PF ldrgeb DUMMY, [PF_SRC, SRC_STRIDE, lsl #src_bpp_shift]!
    vmull.u8    q10, d22, d6
                                    PF ldrgeb DUMMY, [PF_DST, DST_STRIDE, lsl #dst_bpp_shift]!
    vmull.u8    q11, d22, d7
.endm

generate_composite_function \
    pixman_composite_over_8888_8888_asm_neon, 32, 0, 32, \
    FLAG_DST_READWRITE | FLAG_DEINTERLEAVE_32BPP, \
    8, /* number of pixels, processed in a single block */ \
    5, /* prefetch distance */ \
    default_init, \
    default_cleanup, \
    pixman_composite_over_8888_8888_process_pixblock_head, \
    pixman_composite_over_8888_8888_process_pixblock_tail, \
    pixman_composite_over_8888_8888_process_pixblock_tail_head

generate_composite_function_single_scanline \
    pixman_composite_scanline_over_asm_neon, 32, 0, 32, \
    FLAG_DST_READWRITE | FLAG_DEINTERLEAVE_32BPP, \
    8, /* number of pixels, processed in a single block */ \
    default_init, \
    default_cleanup, \
    pixman_composite_over_8888_8888_process_pixblock_head, \
    pixman_composite_over_8888_8888_process_pixblock_tail, \
    pixman_composite_over_8888_8888_process_pixblock_tail_head

/******************************************************************************/

.macro pixman_composite_over_n_8888_process_pixblock_head
    /* deinterleaved source pixels in {d0, d1, d2, d3} */
    /* inverted alpha in {d24} */
    /* destination pixels in {d4, d5, d6, d7} */
    vmull.u8    q8, d24, d4
    vmull.u8    q9, d24, d5
    vmull.u8    q10, d24, d6
    vmull.u8    q11, d24, d7
.endm

.macro pixman_composite_over_n_8888_process_pixblock_tail
    vrshr.u16   q14, q8, #8
    vrshr.u16   q15, q9, #8
    vrshr.u16   q2, q10, #8
    vrshr.u16   q3, q11, #8
    vraddhn.u16 d28, q14, q8
    vraddhn.u16 d29, q15, q9
    vraddhn.u16 d30, q2, q10
    vraddhn.u16 d31, q3, q11
    vqadd.u8    q14, q0, q14
    vqadd.u8    q15, q1, q15
.endm

.macro pixman_composite_over_n_8888_process_pixblock_tail_head
        vrshr.u16   q14, q8, #8
        vrshr.u16   q15, q9, #8
        vrshr.u16   q2, q10, #8
        vrshr.u16   q3, q11, #8
        vraddhn.u16 d28, q14, q8
        vraddhn.u16 d29, q15, q9
        vraddhn.u16 d30, q2, q10
        vraddhn.u16 d31, q3, q11
    vld4.8      {d4, d5, d6, d7}, [DST_R, :128]!
        vqadd.u8    q14, q0, q14
                                    PF add PF_X, PF_X, #8
                                    PF tst PF_CTL, #0x0F
                                    PF addne PF_X, PF_X, #8
                                    PF subne PF_CTL, PF_CTL, #1
        vqadd.u8    q15, q1, q15
                                    PF cmp PF_X, ORIG_W
    vmull.u8    q8, d24, d4
                                    PF pld, [PF_DST, PF_X, lsl #dst_bpp_shift]
    vmull.u8    q9, d24, d5
                                    PF subge PF_X, PF_X, ORIG_W
    vmull.u8    q10, d24, d6
                                    PF subges PF_CTL, PF_CTL, #0x10
    vmull.u8    q11, d24, d7
                                    PF ldrgeb DUMMY, [PF_DST, DST_STRIDE, lsl #dst_bpp_shift]!
        vst4.8      {d28, d29, d30, d31}, [DST_W, :128]!
.endm

.macro pixman_composite_over_n_8888_init
    add         DUMMY, sp, #ARGS_STACK_OFFSET
    vld1.32     {d3[0]}, [DUMMY]
    vdup.8      d0, d3[0]
    vdup.8      d1, d3[1]
    vdup.8      d2, d3[2]
    vdup.8      d3, d3[3]
    vmvn.8      d24, d3  /* get inverted alpha */
.endm

generate_composite_function \
    pixman_composite_over_n_8888_asm_neon, 0, 0, 32, \
    FLAG_DST_READWRITE | FLAG_DEINTERLEAVE_32BPP, \
    8, /* number of pixels, processed in a single block */ \
    5, /* prefetch distance */ \
    pixman_composite_over_n_8888_init, \
    default_cleanup, \
    pixman_composite_over_8888_8888_process_pixblock_head, \
    pixman_composite_over_8888_8888_process_pixblock_tail, \
    pixman_composite_over_n_8888_process_pixblock_tail_head

/******************************************************************************/

.macro pixman_composite_src_n_8888_process_pixblock_head
.endm

.macro pixman_composite_src_n_8888_process_pixblock_tail
.endm

.macro pixman_composite_src_n_8888_process_pixblock_tail_head
    vst1.32 {d0, d1, d2, d3}, [DST_W, :128]!
.endm

.macro pixman_composite_src_n_8888_init
    add         DUMMY, sp, #ARGS_STACK_OFFSET
    vld1.32     {d0[0]}, [DUMMY]
    vsli.u64    d0, d0, #32
    vorr        d1, d0, d0
    vorr        q1, q0, q0
.endm

.macro pixman_composite_src_n_8888_cleanup
.endm

generate_composite_function \
    pixman_composite_src_n_8888_asm_neon, 0, 0, 32, \
    FLAG_DST_WRITEONLY, \
    8, /* number of pixels, processed in a single block */ \
    0, /* prefetch distance */ \
    pixman_composite_src_n_8888_init, \
    pixman_composite_src_n_8888_cleanup, \
    pixman_composite_src_n_8888_process_pixblock_head, \
    pixman_composite_src_n_8888_process_pixblock_tail, \
    pixman_composite_src_n_8888_process_pixblock_tail_head, \
    0, /* dst_w_basereg */ \
    0, /* dst_r_basereg */ \
    0, /* src_basereg   */ \
    0  /* mask_basereg  */

/******************************************************************************/

.macro pixman_composite_src_8888_8888_process_pixblock_head
.endm

.macro pixman_composite_src_8888_8888_process_pixblock_tail
.endm

.macro pixman_composite_src_8888_8888_process_pixblock_tail_head
    vst1.32 {d0, d1, d2, d3}, [DST_W, :128]!
    fetch_src_pixblock
    cache_preload 8, 8
.endm

generate_composite_function \
    pixman_composite_src_8888_8888_asm_neon, 32, 0, 32, \
    FLAG_DST_WRITEONLY, \
    8, /* number of pixels, processed in a single block */ \
    10, /* prefetch distance */ \
    default_init, \
    default_cleanup, \
    pixman_composite_src_8888_8888_process_pixblock_head, \
    pixman_composite_src_8888_8888_process_pixblock_tail, \
    pixman_composite_src_8888_8888_process_pixblock_tail_head, \
    0, /* dst_w_basereg */ \
    0, /* dst_r_basereg */ \
    0, /* src_basereg   */ \
    0  /* mask_basereg  */

/******************************************************************************/
