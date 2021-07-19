/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/isac/fix/source/entropy_coding.h"
#include "modules/audio_coding/codecs/isac/fix/source/settings.h"

// MIPS optimization of the function WebRtcIsacfix_MatrixProduct1.
// Bit-exact with the function WebRtcIsacfix_MatrixProduct1C from
// entropy_coding.c file.
void WebRtcIsacfix_MatrixProduct1MIPS(const int16_t matrix0[],
                                      const int32_t matrix1[],
                                      int32_t matrix_product[],
                                      const int matrix1_index_factor1,
                                      const int matrix0_index_factor1,
                                      const int matrix1_index_init_case,
                                      const int matrix1_index_step,
                                      const int matrix0_index_step,
                                      const int inner_loop_count,
                                      const int mid_loop_count,
                                      const int shift) {
  if (matrix1_index_init_case != 0) {
    int j = SUBFRAMES, k = 0, n = 0;
    int32_t r0, r1, r2, sum32;
    int32_t* product_start = matrix_product;
    int32_t* product_ptr;
    const uint32_t product_step = 4 * mid_loop_count;
    const uint32_t matrix0_step = 2 * matrix0_index_step;
    const uint32_t matrix1_step = 4 * matrix1_index_step;
    const uint32_t matrix0_step2 = 2 * matrix0_index_factor1;
    const uint32_t matrix1_step2 = 4 * matrix1_index_factor1;
    const int16_t* matrix0_start = matrix0;
    const int32_t* matrix1_start = matrix1;
    int16_t* matrix0_ptr;
    int32_t* matrix1_ptr;

    __asm __volatile (
      ".set     push                                                       \n\t"
      ".set     noreorder                                                  \n\t"
     "1:                                                                   \n\t"
      "addu     %[product_ptr],     %[product_start],     $0               \n\t"
      "addu     %[k],               %[product_step],      $0               \n\t"
      "addiu    %[j],               %[j],                 -1               \n\t"
      "addu     %[matrix1_start],   %[matrix1],           $0               \n\t"
     "2:                                                                   \n\t"
      "addu     %[matrix1_ptr],     %[matrix1_start],     $0               \n\t"
      "addu     %[matrix0_ptr],     %[matrix0_start],     $0               \n\t"
      "addu     %[n],               %[inner_loop_count],  $0               \n\t"
      "mul      %[sum32],           $0,                   $0               \n\t"
     "3:                                                                   \n\t"
      "lw       %[r0],              0(%[matrix1_ptr])                      \n\t"
      "lh       %[r1],              0(%[matrix0_ptr])                      \n\t"
      "addu     %[matrix1_ptr],     %[matrix1_ptr],       %[matrix1_step]  \n\t"
      "sllv     %[r0],              %[r0],                %[shift]         \n\t"
      "andi     %[r2],              %[r0],                0xffff           \n\t"
      "sra      %[r2],              %[r2],                1                \n\t"
      "mul      %[r2],              %[r2],                %[r1]            \n\t"
      "sra      %[r0],              %[r0],                16               \n\t"
      "mul      %[r0],              %[r0],                %[r1]            \n\t"
      "addu     %[matrix0_ptr],     %[matrix0_ptr],       %[matrix0_step]  \n\t"
      "addiu    %[n],               %[n],                 -1               \n\t"
#if defined(MIPS_DSP_R1_LE)
      "shra_r.w %[r2],              %[r2],                15               \n\t"
#else
      "addiu    %[r2],              %[r2],                0x4000           \n\t"
      "sra      %[r2],              %[r2],                15               \n\t"
#endif
      "addu     %[sum32],           %[sum32],             %[r2]            \n\t"
      "bgtz     %[n],               3b                                     \n\t"
      " addu    %[sum32],           %[sum32],             %[r0]            \n\t"
      "addiu    %[k],               %[k],                 -4               \n\t"
      "addu     %[matrix1_start],   %[matrix1_start],     %[matrix1_step2] \n\t"
      "sw       %[sum32],           0(%[product_ptr])                      \n\t"
      "bgtz     %[k],               2b                                     \n\t"
      " addiu   %[product_ptr],     %[product_ptr],       4                \n\t"
      "addu     %[matrix0_start],   %[matrix0_start],     %[matrix0_step2] \n\t"
      "bgtz     %[j],               1b                                     \n\t"
      " addu    %[product_start],   %[product_start],     %[product_step]  \n\t"
      ".set     pop                                                        \n\t"
      : [product_ptr] "=&r" (product_ptr), [product_start] "+r" (product_start),
        [k] "=&r" (k), [j] "+r" (j), [matrix1_start] "=&r"(matrix1_start),
        [matrix1_ptr] "=&r" (matrix1_ptr), [matrix0_ptr] "=&r" (matrix0_ptr),
        [matrix0_start] "+r" (matrix0_start), [n] "=&r" (n), [r0] "=&r" (r0),
        [sum32] "=&r" (sum32), [r1] "=&r" (r1),[r2] "=&r" (r2)
      : [product_step] "r" (product_step), [matrix1] "r" (matrix1),
        [inner_loop_count] "r" (inner_loop_count),
        [matrix1_step] "r" (matrix1_step), [shift] "r" (shift),
        [matrix0_step] "r" (matrix0_step), [matrix1_step2] "r" (matrix1_step2),
        [matrix0_step2] "r" (matrix0_step2)
      : "hi", "lo", "memory"
    );
  } else {
    int j = SUBFRAMES, k = 0, n = 0;
    int32_t r0, r1, r2, sum32;
    int32_t* product_start = matrix_product;
    int32_t* product_ptr;
    const uint32_t product_step = 4 * mid_loop_count;
    const uint32_t matrix0_step = 2 * matrix0_index_step;
    const uint32_t matrix1_step = 4 * matrix1_index_step;
    const uint32_t matrix0_step2 = 2 * matrix0_index_factor1;
    const uint32_t matrix1_step2 = 4 * matrix1_index_factor1;
    const int16_t* matrix0_start = matrix0;
    const int32_t* matrix1_start = matrix1;
    int16_t* matrix0_ptr;
    int32_t* matrix1_ptr;

    __asm __volatile (
      ".set     push                                                       \n\t"
      ".set     noreorder                                                  \n\t"
     "1:                                                                   \n\t"
      "addu     %[product_ptr],     %[product_start],     $0               \n\t"
      "addu     %[k],               %[product_step],      $0               \n\t"
      "addiu    %[j],               %[j],                 -1               \n\t"
      "addu     %[matrix0_start],   %[matrix0],           $0               \n\t"
     "2:                                                                   \n\t"
      "addu     %[matrix1_ptr],     %[matrix1_start],     $0               \n\t"
      "addu     %[matrix0_ptr],     %[matrix0_start],     $0               \n\t"
      "addu     %[n],               %[inner_loop_count],  $0               \n\t"
      "mul      %[sum32],           $0,                   $0               \n\t"
     "3:                                                                   \n\t"
      "lw       %[r0],              0(%[matrix1_ptr])                      \n\t"
      "lh       %[r1],              0(%[matrix0_ptr])                      \n\t"
      "addu     %[matrix1_ptr],     %[matrix1_ptr],       %[matrix1_step]  \n\t"
      "sllv     %[r0],              %[r0],                %[shift]         \n\t"
      "andi     %[r2],              %[r0],                0xffff           \n\t"
      "sra      %[r2],              %[r2],                1                \n\t"
      "mul      %[r2],              %[r2],                %[r1]            \n\t"
      "sra      %[r0],              %[r0],                16               \n\t"
      "mul      %[r0],              %[r0],                %[r1]            \n\t"
      "addu     %[matrix0_ptr],     %[matrix0_ptr],       %[matrix0_step]  \n\t"
      "addiu    %[n],               %[n],                 -1               \n\t"
#if defined(MIPS_DSP_R1_LE)
      "shra_r.w %[r2],              %[r2],                15               \n\t"
#else
      "addiu    %[r2],              %[r2],                0x4000           \n\t"
      "sra      %[r2],              %[r2],                15               \n\t"
#endif
      "addu     %[sum32],           %[sum32],             %[r2]            \n\t"
      "bgtz     %[n],               3b                                     \n\t"
      " addu    %[sum32],           %[sum32],             %[r0]            \n\t"
      "addiu    %[k],               %[k],                 -4               \n\t"
      "addu     %[matrix0_start],   %[matrix0_start],     %[matrix0_step2] \n\t"
      "sw       %[sum32],           0(%[product_ptr])                      \n\t"
      "bgtz     %[k],               2b                                     \n\t"
      " addiu   %[product_ptr],     %[product_ptr],       4                \n\t"
      "addu     %[matrix1_start],   %[matrix1_start],     %[matrix1_step2] \n\t"
      "bgtz     %[j],               1b                                     \n\t"
      " addu    %[product_start],   %[product_start],     %[product_step]  \n\t"
      ".set     pop                                                        \n\t"
      : [product_ptr] "=&r" (product_ptr), [product_start] "+r" (product_start),
        [k] "=&r" (k), [j] "+r" (j), [matrix1_start] "+r"(matrix1_start),
        [matrix1_ptr] "=&r" (matrix1_ptr), [matrix0_ptr] "=&r" (matrix0_ptr),
        [matrix0_start] "=&r" (matrix0_start), [n] "=&r" (n), [r0] "=&r" (r0),
        [sum32] "=&r" (sum32), [r1] "=&r" (r1),[r2] "=&r" (r2)
      : [product_step] "r" (product_step), [matrix0] "r" (matrix0),
        [inner_loop_count] "r" (inner_loop_count),
        [matrix1_step] "r" (matrix1_step), [shift] "r" (shift),
        [matrix0_step] "r" (matrix0_step), [matrix1_step2] "r" (matrix1_step2),
        [matrix0_step2] "r" (matrix0_step2)
      : "hi", "lo", "memory"
    );
  }
}

// MIPS optimization of the function WebRtcIsacfix_MatrixProduct2.
// Bit-exact with the function WebRtcIsacfix_MatrixProduct2C from
// entropy_coding.c file.
void WebRtcIsacfix_MatrixProduct2MIPS(const int16_t matrix0[],
                                      const int32_t matrix1[],
                                      int32_t matrix_product[],
                                      const int matrix0_index_factor,
                                      const int matrix0_index_step) {
  int j = 0, n = 0;
  int loop_count = SUBFRAMES;
  const int16_t* matrix0_ptr;
  const int32_t* matrix1_ptr;
  const int16_t* matrix0_start = matrix0;
  const int matrix0_step = 2 * matrix0_index_step;
  const int matrix0_step2 = 2 * matrix0_index_factor;
  int32_t r0, r1, r2, r3, r4, sum32, sum32_2;

  __asm __volatile (
    ".set       push                                                   \n\t"
    ".set       noreorder                                              \n\t"
    "addu       %[j],              %[loop_count],     $0               \n\t"
    "addu       %[matrix0_start],  %[matrix0],        $0               \n\t"
   "1:                                                                 \n\t"
    "addu       %[matrix1_ptr],    %[matrix1],        $0               \n\t"
    "addu       %[matrix0_ptr],    %[matrix0_start],  $0               \n\t"
    "addu       %[n],              %[loop_count],     $0               \n\t"
    "mul        %[sum32],          $0,                $0               \n\t"
    "mul        %[sum32_2],        $0,                $0               \n\t"
   "2:                                                                 \n\t"
    "lw         %[r0],             0(%[matrix1_ptr])                   \n\t"
    "lw         %[r1],             4(%[matrix1_ptr])                   \n\t"
    "lh         %[r2],             0(%[matrix0_ptr])                   \n\t"
    "andi       %[r3],             %[r0],             0xffff           \n\t"
    "sra        %[r3],             %[r3],             1                \n\t"
    "mul        %[r3],             %[r3],             %[r2]            \n\t"
    "andi       %[r4],             %[r1],             0xffff           \n\t"
    "sra        %[r4],             %[r4],             1                \n\t"
    "mul        %[r4],             %[r4],             %[r2]            \n\t"
    "sra        %[r0],             %[r0],             16               \n\t"
    "mul        %[r0],             %[r0],             %[r2]            \n\t"
    "sra        %[r1],             %[r1],             16               \n\t"
    "mul        %[r1],             %[r1],             %[r2]            \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w   %[r3],             %[r3],             15               \n\t"
    "shra_r.w   %[r4],             %[r4],             15               \n\t"
#else
    "addiu      %[r3],             %[r3],             0x4000           \n\t"
    "sra        %[r3],             %[r3],             15               \n\t"
    "addiu      %[r4],             %[r4],             0x4000           \n\t"
    "sra        %[r4],             %[r4],             15               \n\t"
#endif
    "addiu      %[matrix1_ptr],    %[matrix1_ptr],    8                \n\t"
    "addu       %[matrix0_ptr],    %[matrix0_ptr],    %[matrix0_step]  \n\t"
    "addiu      %[n],              %[n],              -1               \n\t"
    "addu       %[sum32],          %[sum32],          %[r3]            \n\t"
    "addu       %[sum32_2],        %[sum32_2],        %[r4]            \n\t"
    "addu       %[sum32],          %[sum32],          %[r0]            \n\t"
    "bgtz       %[n],              2b                                  \n\t"
    " addu      %[sum32_2],        %[sum32_2],        %[r1]            \n\t"
    "sra        %[sum32],          %[sum32],          3                \n\t"
    "sra        %[sum32_2],        %[sum32_2],        3                \n\t"
    "addiu      %[j],              %[j],              -1               \n\t"
    "addu       %[matrix0_start],  %[matrix0_start],  %[matrix0_step2] \n\t"
    "sw         %[sum32],          0(%[matrix_product])                \n\t"
    "sw         %[sum32_2],        4(%[matrix_product])                \n\t"
    "bgtz       %[j],              1b                                  \n\t"
    " addiu     %[matrix_product], %[matrix_product], 8                \n\t"
    ".set       pop                                                    \n\t"
    : [j] "=&r" (j), [matrix0_start] "=&r" (matrix0_start),
      [matrix1_ptr] "=&r" (matrix1_ptr), [matrix0_ptr] "=&r" (matrix0_ptr),
      [n] "=&r" (n), [sum32] "=&r" (sum32), [sum32_2] "=&r" (sum32_2),
      [r0] "=&r" (r0), [r1] "=&r" (r1), [r2] "=&r" (r2), [r3] "=&r" (r3),
      [r4] "=&r" (r4), [matrix_product] "+r" (matrix_product)
    : [loop_count] "r" (loop_count), [matrix0] "r" (matrix0),
      [matrix1] "r" (matrix1), [matrix0_step] "r" (matrix0_step),
      [matrix0_step2] "r" (matrix0_step2)
    : "hi", "lo", "memory"
  );
}
