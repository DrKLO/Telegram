// Copyright 2014 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// MIPS version of speed-critical encoding functions.
//
// Author(s): Djordje Pesut    (djordje.pesut@imgtec.com)
//            Jovan Zelincevic (jovan.zelincevic@imgtec.com)
//            Slobodan Prijic  (slobodan.prijic@imgtec.com)

#include "./dsp.h"

#if defined(WEBP_USE_MIPS32)

#include "../enc/vp8enci.h"
#include "../enc/cost.h"

#if defined(__GNUC__) && defined(__ANDROID__) && LOCAL_GCC_VERSION == 0x409
#define WORK_AROUND_GCC
#endif

static const int kC1 = 20091 + (1 << 16);
static const int kC2 = 35468;

// macro for one vertical pass in ITransformOne
// MUL macro inlined
// temp0..temp15 holds tmp[0]..tmp[15]
// A..D - offsets in bytes to load from in buffer
// TEMP0..TEMP3 - registers for corresponding tmp elements
// TEMP4..TEMP5 - temporary registers
#define VERTICAL_PASS(A, B, C, D, TEMP4, TEMP0, TEMP1, TEMP2, TEMP3)        \
  "lh      %[temp16],      "#A"(%[temp20])                 \n\t"            \
  "lh      %[temp18],      "#B"(%[temp20])                 \n\t"            \
  "lh      %[temp17],      "#C"(%[temp20])                 \n\t"            \
  "lh      %[temp19],      "#D"(%[temp20])                 \n\t"            \
  "addu    %["#TEMP4"],    %[temp16],      %[temp18]       \n\t"            \
  "subu    %[temp16],      %[temp16],      %[temp18]       \n\t"            \
  "mul     %["#TEMP0"],    %[temp17],      %[kC2]          \n\t"            \
  "mul     %[temp18],      %[temp19],      %[kC1]          \n\t"            \
  "mul     %[temp17],      %[temp17],      %[kC1]          \n\t"            \
  "mul     %[temp19],      %[temp19],      %[kC2]          \n\t"            \
  "sra     %["#TEMP0"],    %["#TEMP0"],    16              \n\n"            \
  "sra     %[temp18],      %[temp18],      16              \n\n"            \
  "sra     %[temp17],      %[temp17],      16              \n\n"            \
  "sra     %[temp19],      %[temp19],      16              \n\n"            \
  "subu    %["#TEMP2"],    %["#TEMP0"],    %[temp18]       \n\t"            \
  "addu    %["#TEMP3"],    %[temp17],      %[temp19]       \n\t"            \
  "addu    %["#TEMP0"],    %["#TEMP4"],    %["#TEMP3"]     \n\t"            \
  "addu    %["#TEMP1"],    %[temp16],      %["#TEMP2"]     \n\t"            \
  "subu    %["#TEMP2"],    %[temp16],      %["#TEMP2"]     \n\t"            \
  "subu    %["#TEMP3"],    %["#TEMP4"],    %["#TEMP3"]     \n\t"

// macro for one horizontal pass in ITransformOne
// MUL and STORE macros inlined
// a = clip_8b(a) is replaced with: a = max(a, 0); a = min(a, 255)
// temp0..temp15 holds tmp[0]..tmp[15]
// A..D - offsets in bytes to load from ref and store to dst buffer
// TEMP0, TEMP4, TEMP8 and TEMP12 - registers for corresponding tmp elements
#define HORIZONTAL_PASS(A, B, C, D, TEMP0, TEMP4, TEMP8, TEMP12)            \
  "addiu   %["#TEMP0"],    %["#TEMP0"],    4               \n\t"            \
  "addu    %[temp16],      %["#TEMP0"],    %["#TEMP8"]     \n\t"            \
  "subu    %[temp17],      %["#TEMP0"],    %["#TEMP8"]     \n\t"            \
  "mul     %["#TEMP0"],    %["#TEMP4"],    %[kC2]          \n\t"            \
  "mul     %["#TEMP8"],    %["#TEMP12"],   %[kC1]          \n\t"            \
  "mul     %["#TEMP4"],    %["#TEMP4"],    %[kC1]          \n\t"            \
  "mul     %["#TEMP12"],   %["#TEMP12"],   %[kC2]          \n\t"            \
  "sra     %["#TEMP0"],    %["#TEMP0"],    16              \n\t"            \
  "sra     %["#TEMP8"],    %["#TEMP8"],    16              \n\t"            \
  "sra     %["#TEMP4"],    %["#TEMP4"],    16              \n\t"            \
  "sra     %["#TEMP12"],   %["#TEMP12"],   16              \n\t"            \
  "subu    %[temp18],      %["#TEMP0"],    %["#TEMP8"]     \n\t"            \
  "addu    %[temp19],      %["#TEMP4"],    %["#TEMP12"]    \n\t"            \
  "addu    %["#TEMP0"],    %[temp16],      %[temp19]       \n\t"            \
  "addu    %["#TEMP4"],    %[temp17],      %[temp18]       \n\t"            \
  "subu    %["#TEMP8"],    %[temp17],      %[temp18]       \n\t"            \
  "subu    %["#TEMP12"],   %[temp16],      %[temp19]       \n\t"            \
  "lw      %[temp20],      0(%[args])                      \n\t"            \
  "sra     %["#TEMP0"],    %["#TEMP0"],    3               \n\t"            \
  "sra     %["#TEMP4"],    %["#TEMP4"],    3               \n\t"            \
  "sra     %["#TEMP8"],    %["#TEMP8"],    3               \n\t"            \
  "sra     %["#TEMP12"],   %["#TEMP12"],   3               \n\t"            \
  "lbu     %[temp16],      "#A"(%[temp20])                 \n\t"            \
  "lbu     %[temp17],      "#B"(%[temp20])                 \n\t"            \
  "lbu     %[temp18],      "#C"(%[temp20])                 \n\t"            \
  "lbu     %[temp19],      "#D"(%[temp20])                 \n\t"            \
  "addu    %["#TEMP0"],    %[temp16],      %["#TEMP0"]     \n\t"            \
  "addu    %["#TEMP4"],    %[temp17],      %["#TEMP4"]     \n\t"            \
  "addu    %["#TEMP8"],    %[temp18],      %["#TEMP8"]     \n\t"            \
  "addu    %["#TEMP12"],   %[temp19],      %["#TEMP12"]    \n\t"            \
  "slt     %[temp16],      %["#TEMP0"],    $zero           \n\t"            \
  "slt     %[temp17],      %["#TEMP4"],    $zero           \n\t"            \
  "slt     %[temp18],      %["#TEMP8"],    $zero           \n\t"            \
  "slt     %[temp19],      %["#TEMP12"],   $zero           \n\t"            \
  "movn    %["#TEMP0"],    $zero,          %[temp16]       \n\t"            \
  "movn    %["#TEMP4"],    $zero,          %[temp17]       \n\t"            \
  "movn    %["#TEMP8"],    $zero,          %[temp18]       \n\t"            \
  "movn    %["#TEMP12"],   $zero,          %[temp19]       \n\t"            \
  "addiu   %[temp20],      $zero,          255             \n\t"            \
  "slt     %[temp16],      %["#TEMP0"],    %[temp20]       \n\t"            \
  "slt     %[temp17],      %["#TEMP4"],    %[temp20]       \n\t"            \
  "slt     %[temp18],      %["#TEMP8"],    %[temp20]       \n\t"            \
  "slt     %[temp19],      %["#TEMP12"],   %[temp20]       \n\t"            \
  "movz    %["#TEMP0"],    %[temp20],      %[temp16]       \n\t"            \
  "movz    %["#TEMP4"],    %[temp20],      %[temp17]       \n\t"            \
  "lw      %[temp16],      8(%[args])                      \n\t"            \
  "movz    %["#TEMP8"],    %[temp20],      %[temp18]       \n\t"            \
  "movz    %["#TEMP12"],   %[temp20],      %[temp19]       \n\t"            \
  "sb      %["#TEMP0"],    "#A"(%[temp16])                 \n\t"            \
  "sb      %["#TEMP4"],    "#B"(%[temp16])                 \n\t"            \
  "sb      %["#TEMP8"],    "#C"(%[temp16])                 \n\t"            \
  "sb      %["#TEMP12"],   "#D"(%[temp16])                 \n\t"

// Does one or two inverse transforms.
static WEBP_INLINE void ITransformOne(const uint8_t* ref, const int16_t* in,
                                      uint8_t* dst) {
  int temp0, temp1, temp2, temp3, temp4, temp5, temp6;
  int temp7, temp8, temp9, temp10, temp11, temp12, temp13;
  int temp14, temp15, temp16, temp17, temp18, temp19, temp20;
  const int* args[3] = {(const int*)ref, (const int*)in, (const int*)dst};

  __asm__ volatile(
    "lw      %[temp20],      4(%[args])                      \n\t"
    VERTICAL_PASS(0, 16,  8, 24, temp4,  temp0,  temp1,  temp2,  temp3)
    VERTICAL_PASS(2, 18, 10, 26, temp8,  temp4,  temp5,  temp6,  temp7)
    VERTICAL_PASS(4, 20, 12, 28, temp12, temp8,  temp9,  temp10, temp11)
    VERTICAL_PASS(6, 22, 14, 30, temp20, temp12, temp13, temp14, temp15)

    HORIZONTAL_PASS( 0,  1,  2,  3, temp0, temp4, temp8,  temp12)
    HORIZONTAL_PASS(16, 17, 18, 19, temp1, temp5, temp9,  temp13)
    HORIZONTAL_PASS(32, 33, 34, 35, temp2, temp6, temp10, temp14)
    HORIZONTAL_PASS(48, 49, 50, 51, temp3, temp7, temp11, temp15)

    : [temp0]"=&r"(temp0), [temp1]"=&r"(temp1), [temp2]"=&r"(temp2),
      [temp3]"=&r"(temp3), [temp4]"=&r"(temp4), [temp5]"=&r"(temp5),
      [temp6]"=&r"(temp6), [temp7]"=&r"(temp7), [temp8]"=&r"(temp8),
      [temp9]"=&r"(temp9), [temp10]"=&r"(temp10), [temp11]"=&r"(temp11),
      [temp12]"=&r"(temp12), [temp13]"=&r"(temp13), [temp14]"=&r"(temp14),
      [temp15]"=&r"(temp15), [temp16]"=&r"(temp16), [temp17]"=&r"(temp17),
      [temp18]"=&r"(temp18), [temp19]"=&r"(temp19), [temp20]"=&r"(temp20)
    : [args]"r"(args), [kC1]"r"(kC1), [kC2]"r"(kC2)
    : "memory", "hi", "lo"
  );
}

static void ITransform(const uint8_t* ref, const int16_t* in,
                       uint8_t* dst, int do_two) {
  ITransformOne(ref, in, dst);
  if (do_two) {
    ITransformOne(ref + 4, in + 16, dst + 4);
  }
}

#undef VERTICAL_PASS
#undef HORIZONTAL_PASS

// macro for one pass through for loop in QuantizeBlock
// QUANTDIV macro inlined
// J - offset in bytes (kZigzag[n] * 2)
// K - offset in bytes (kZigzag[n] * 4)
// N - offset in bytes (n * 2)
#define QUANTIZE_ONE(J, K, N)                                               \
  "lh           %[temp0],       "#J"(%[ppin])                       \n\t"   \
  "lhu          %[temp1],       "#J"(%[ppsharpen])                  \n\t"   \
  "lw           %[temp2],       "#K"(%[ppzthresh])                  \n\t"   \
  "sra          %[sign],        %[temp0],           15              \n\t"   \
  "xor          %[coeff],       %[temp0],           %[sign]         \n\t"   \
  "subu         %[coeff],       %[coeff],           %[sign]         \n\t"   \
  "addu         %[coeff],       %[coeff],           %[temp1]        \n\t"   \
  "slt          %[temp4],       %[temp2],           %[coeff]        \n\t"   \
  "addiu        %[temp5],       $zero,              0               \n\t"   \
  "addiu        %[level],       $zero,              0               \n\t"   \
  "beqz         %[temp4],       2f                                  \n\t"   \
  "lhu          %[temp1],       "#J"(%[ppiq])                       \n\t"   \
  "lw           %[temp2],       "#K"(%[ppbias])                     \n\t"   \
  "lhu          %[temp3],       "#J"(%[ppq])                        \n\t"   \
  "mul          %[level],       %[coeff],           %[temp1]        \n\t"   \
  "addu         %[level],       %[level],           %[temp2]        \n\t"   \
  "sra          %[level],       %[level],           17              \n\t"   \
  "slt          %[temp4],       %[max_level],       %[level]        \n\t"   \
  "movn         %[level],       %[max_level],       %[temp4]        \n\t"   \
  "xor          %[level],       %[level],           %[sign]         \n\t"   \
  "subu         %[level],       %[level],           %[sign]         \n\t"   \
  "mul          %[temp5],       %[level],           %[temp3]        \n\t"   \
"2:                                                                 \n\t"   \
  "sh           %[temp5],       "#J"(%[ppin])                       \n\t"   \
  "sh           %[level],       "#N"(%[pout])                       \n\t"

static int QuantizeBlock(int16_t in[16], int16_t out[16],
                         const VP8Matrix* const mtx) {
  int temp0, temp1, temp2, temp3, temp4, temp5;
  int sign, coeff, level, i;
  int max_level = MAX_LEVEL;

  int16_t* ppin             = &in[0];
  int16_t* pout             = &out[0];
  const uint16_t* ppsharpen = &mtx->sharpen_[0];
  const uint32_t* ppzthresh = &mtx->zthresh_[0];
  const uint16_t* ppq       = &mtx->q_[0];
  const uint16_t* ppiq      = &mtx->iq_[0];
  const uint32_t* ppbias    = &mtx->bias_[0];

  __asm__ volatile(
    QUANTIZE_ONE( 0,  0,  0)
    QUANTIZE_ONE( 2,  4,  2)
    QUANTIZE_ONE( 8, 16,  4)
    QUANTIZE_ONE(16, 32,  6)
    QUANTIZE_ONE(10, 20,  8)
    QUANTIZE_ONE( 4,  8, 10)
    QUANTIZE_ONE( 6, 12, 12)
    QUANTIZE_ONE(12, 24, 14)
    QUANTIZE_ONE(18, 36, 16)
    QUANTIZE_ONE(24, 48, 18)
    QUANTIZE_ONE(26, 52, 20)
    QUANTIZE_ONE(20, 40, 22)
    QUANTIZE_ONE(14, 28, 24)
    QUANTIZE_ONE(22, 44, 26)
    QUANTIZE_ONE(28, 56, 28)
    QUANTIZE_ONE(30, 60, 30)

    : [temp0]"=&r"(temp0), [temp1]"=&r"(temp1),
      [temp2]"=&r"(temp2), [temp3]"=&r"(temp3),
      [temp4]"=&r"(temp4), [temp5]"=&r"(temp5),
      [sign]"=&r"(sign), [coeff]"=&r"(coeff),
      [level]"=&r"(level)
    : [pout]"r"(pout), [ppin]"r"(ppin),
      [ppiq]"r"(ppiq), [max_level]"r"(max_level),
      [ppbias]"r"(ppbias), [ppzthresh]"r"(ppzthresh),
      [ppsharpen]"r"(ppsharpen), [ppq]"r"(ppq)
    : "memory", "hi", "lo"
  );

  // moved out from macro to increase possibility for earlier breaking
  for (i = 15; i >= 0; i--) {
    if (out[i]) return 1;
  }
  return 0;
}

#undef QUANTIZE_ONE

// macro for one horizontal pass in Disto4x4 (TTransform)
// two calls of function TTransform are merged into single one
// A..D - offsets in bytes to load from a and b buffers
// E..H - offsets in bytes to store first results to tmp buffer
// E1..H1 - offsets in bytes to store second results to tmp buffer
#define HORIZONTAL_PASS(A, B, C, D, E, F, G, H, E1, F1, G1, H1)   \
  "lbu    %[temp0],  "#A"(%[a])              \n\t"                \
  "lbu    %[temp1],  "#B"(%[a])              \n\t"                \
  "lbu    %[temp2],  "#C"(%[a])              \n\t"                \
  "lbu    %[temp3],  "#D"(%[a])              \n\t"                \
  "lbu    %[temp4],  "#A"(%[b])              \n\t"                \
  "lbu    %[temp5],  "#B"(%[b])              \n\t"                \
  "lbu    %[temp6],  "#C"(%[b])              \n\t"                \
  "lbu    %[temp7],  "#D"(%[b])              \n\t"                \
  "addu   %[temp8],  %[temp0],    %[temp2]   \n\t"                \
  "subu   %[temp0],  %[temp0],    %[temp2]   \n\t"                \
  "addu   %[temp2],  %[temp1],    %[temp3]   \n\t"                \
  "subu   %[temp1],  %[temp1],    %[temp3]   \n\t"                \
  "addu   %[temp3],  %[temp4],    %[temp6]   \n\t"                \
  "subu   %[temp4],  %[temp4],    %[temp6]   \n\t"                \
  "addu   %[temp6],  %[temp5],    %[temp7]   \n\t"                \
  "subu   %[temp5],  %[temp5],    %[temp7]   \n\t"                \
  "addu   %[temp7],  %[temp8],    %[temp2]   \n\t"                \
  "subu   %[temp2],  %[temp8],    %[temp2]   \n\t"                \
  "addu   %[temp8],  %[temp0],    %[temp1]   \n\t"                \
  "subu   %[temp0],  %[temp0],    %[temp1]   \n\t"                \
  "addu   %[temp1],  %[temp3],    %[temp6]   \n\t"                \
  "subu   %[temp3],  %[temp3],    %[temp6]   \n\t"                \
  "addu   %[temp6],  %[temp4],    %[temp5]   \n\t"                \
  "subu   %[temp4],  %[temp4],    %[temp5]   \n\t"                \
  "sw     %[temp7],  "#E"(%[tmp])            \n\t"                \
  "sw     %[temp2],  "#H"(%[tmp])            \n\t"                \
  "sw     %[temp8],  "#F"(%[tmp])            \n\t"                \
  "sw     %[temp0],  "#G"(%[tmp])            \n\t"                \
  "sw     %[temp1],  "#E1"(%[tmp])           \n\t"                \
  "sw     %[temp3],  "#H1"(%[tmp])           \n\t"                \
  "sw     %[temp6],  "#F1"(%[tmp])           \n\t"                \
  "sw     %[temp4],  "#G1"(%[tmp])           \n\t"

// macro for one vertical pass in Disto4x4 (TTransform)
// two calls of function TTransform are merged into single one
// since only one accu is available in mips32r1 instruction set
//   first is done second call of function TTransform and after
//   that first one.
//   const int sum1 = TTransform(a, w);
//   const int sum2 = TTransform(b, w);
//   return abs(sum2 - sum1) >> 5;
//   (sum2 - sum1) is calculated with madds (sub2) and msubs (sub1)
// A..D - offsets in bytes to load first results from tmp buffer
// A1..D1 - offsets in bytes to load second results from tmp buffer
// E..H - offsets in bytes to load from w buffer
#define VERTICAL_PASS(A, B, C, D, A1, B1, C1, D1, E, F, G, H)     \
  "lw     %[temp0],  "#A1"(%[tmp])           \n\t"                \
  "lw     %[temp1],  "#C1"(%[tmp])           \n\t"                \
  "lw     %[temp2],  "#B1"(%[tmp])           \n\t"                \
  "lw     %[temp3],  "#D1"(%[tmp])           \n\t"                \
  "addu   %[temp8],  %[temp0],    %[temp1]   \n\t"                \
  "subu   %[temp0],  %[temp0],    %[temp1]   \n\t"                \
  "addu   %[temp1],  %[temp2],    %[temp3]   \n\t"                \
  "subu   %[temp2],  %[temp2],    %[temp3]   \n\t"                \
  "addu   %[temp3],  %[temp8],    %[temp1]   \n\t"                \
  "subu   %[temp8],  %[temp8],    %[temp1]   \n\t"                \
  "addu   %[temp1],  %[temp0],    %[temp2]   \n\t"                \
  "subu   %[temp0],  %[temp0],    %[temp2]   \n\t"                \
  "sra    %[temp4],  %[temp3],    31         \n\t"                \
  "sra    %[temp5],  %[temp1],    31         \n\t"                \
  "sra    %[temp6],  %[temp0],    31         \n\t"                \
  "sra    %[temp7],  %[temp8],    31         \n\t"                \
  "xor    %[temp3],  %[temp3],    %[temp4]   \n\t"                \
  "xor    %[temp1],  %[temp1],    %[temp5]   \n\t"                \
  "xor    %[temp0],  %[temp0],    %[temp6]   \n\t"                \
  "xor    %[temp8],  %[temp8],    %[temp7]   \n\t"                \
  "subu   %[temp3],  %[temp3],    %[temp4]   \n\t"                \
  "subu   %[temp1],  %[temp1],    %[temp5]   \n\t"                \
  "subu   %[temp0],  %[temp0],    %[temp6]   \n\t"                \
  "subu   %[temp8],  %[temp8],    %[temp7]   \n\t"                \
  "lhu    %[temp4],  "#E"(%[w])              \n\t"                \
  "lhu    %[temp5],  "#F"(%[w])              \n\t"                \
  "lhu    %[temp6],  "#G"(%[w])              \n\t"                \
  "lhu    %[temp7],  "#H"(%[w])              \n\t"                \
  "madd   %[temp4],  %[temp3]                \n\t"                \
  "madd   %[temp5],  %[temp1]                \n\t"                \
  "madd   %[temp6],  %[temp0]                \n\t"                \
  "madd   %[temp7],  %[temp8]                \n\t"                \
  "lw     %[temp0],  "#A"(%[tmp])            \n\t"                \
  "lw     %[temp1],  "#C"(%[tmp])            \n\t"                \
  "lw     %[temp2],  "#B"(%[tmp])            \n\t"                \
  "lw     %[temp3],  "#D"(%[tmp])            \n\t"                \
  "addu   %[temp8],  %[temp0],    %[temp1]   \n\t"                \
  "subu   %[temp0],  %[temp0],    %[temp1]   \n\t"                \
  "addu   %[temp1],  %[temp2],    %[temp3]   \n\t"                \
  "subu   %[temp2],  %[temp2],    %[temp3]   \n\t"                \
  "addu   %[temp3],  %[temp8],    %[temp1]   \n\t"                \
  "subu   %[temp1],  %[temp8],    %[temp1]   \n\t"                \
  "addu   %[temp8],  %[temp0],    %[temp2]   \n\t"                \
  "subu   %[temp0],  %[temp0],    %[temp2]   \n\t"                \
  "sra    %[temp2],  %[temp3],    31         \n\t"                \
  "xor    %[temp3],  %[temp3],    %[temp2]   \n\t"                \
  "subu   %[temp3],  %[temp3],    %[temp2]   \n\t"                \
  "msub   %[temp4],  %[temp3]                \n\t"                \
  "sra    %[temp2],  %[temp8],    31         \n\t"                \
  "sra    %[temp3],  %[temp0],    31         \n\t"                \
  "sra    %[temp4],  %[temp1],    31         \n\t"                \
  "xor    %[temp8],  %[temp8],    %[temp2]   \n\t"                \
  "xor    %[temp0],  %[temp0],    %[temp3]   \n\t"                \
  "xor    %[temp1],  %[temp1],    %[temp4]   \n\t"                \
  "subu   %[temp8],  %[temp8],    %[temp2]   \n\t"                \
  "subu   %[temp0],  %[temp0],    %[temp3]   \n\t"                \
  "subu   %[temp1],  %[temp1],    %[temp4]   \n\t"                \
  "msub   %[temp5],  %[temp8]                \n\t"                \
  "msub   %[temp6],  %[temp0]                \n\t"                \
  "msub   %[temp7],  %[temp1]                \n\t"

static int Disto4x4(const uint8_t* const a, const uint8_t* const b,
                    const uint16_t* const w) {
  int tmp[32];
  int temp0, temp1, temp2, temp3, temp4, temp5, temp6, temp7, temp8;

  __asm__ volatile(
    HORIZONTAL_PASS( 0,  1,  2,  3,    0,  4,  8, 12,    64,  68,  72,  76)
    HORIZONTAL_PASS(16, 17, 18, 19,   16, 20, 24, 28,    80,  84,  88,  92)
    HORIZONTAL_PASS(32, 33, 34, 35,   32, 36, 40, 44,    96, 100, 104, 108)
    HORIZONTAL_PASS(48, 49, 50, 51,   48, 52, 56, 60,   112, 116, 120, 124)
    "mthi   $zero                             \n\t"
    "mtlo   $zero                             \n\t"
    VERTICAL_PASS( 0, 16, 32, 48,     64, 80,  96, 112,   0,  8, 16, 24)
    VERTICAL_PASS( 4, 20, 36, 52,     68, 84, 100, 116,   2, 10, 18, 26)
    VERTICAL_PASS( 8, 24, 40, 56,     72, 88, 104, 120,   4, 12, 20, 28)
    VERTICAL_PASS(12, 28, 44, 60,     76, 92, 108, 124,   6, 14, 22, 30)
    "mflo   %[temp0]                          \n\t"
    "sra    %[temp1],  %[temp0],  31          \n\t"
    "xor    %[temp0],  %[temp0],  %[temp1]    \n\t"
    "subu   %[temp0],  %[temp0],  %[temp1]    \n\t"
    "sra    %[temp0],  %[temp0],  5           \n\t"

    : [temp0]"=&r"(temp0), [temp1]"=&r"(temp1), [temp2]"=&r"(temp2),
      [temp3]"=&r"(temp3), [temp4]"=&r"(temp4), [temp5]"=&r"(temp5),
      [temp6]"=&r"(temp6), [temp7]"=&r"(temp7), [temp8]"=&r"(temp8)
    : [a]"r"(a), [b]"r"(b), [w]"r"(w), [tmp]"r"(tmp)
    : "memory", "hi", "lo"
  );

  return temp0;
}

#undef VERTICAL_PASS
#undef HORIZONTAL_PASS

static int Disto16x16(const uint8_t* const a, const uint8_t* const b,
                      const uint16_t* const w) {
  int D = 0;
  int x, y;
  for (y = 0; y < 16 * BPS; y += 4 * BPS) {
    for (x = 0; x < 16; x += 4) {
      D += Disto4x4(a + x + y, b + x + y, w);
    }
  }
  return D;
}

// macro for one horizontal pass in FTransform
// temp0..temp15 holds tmp[0]..tmp[15]
// A..D - offsets in bytes to load from src and ref buffers
// TEMP0..TEMP3 - registers for corresponding tmp elements
#define HORIZONTAL_PASS(A, B, C, D, TEMP0, TEMP1, TEMP2, TEMP3) \
  "lw     %["#TEMP1"],  0(%[args])                     \n\t"    \
  "lw     %["#TEMP2"],  4(%[args])                     \n\t"    \
  "lbu    %[temp16],    "#A"(%["#TEMP1"])              \n\t"    \
  "lbu    %[temp17],    "#A"(%["#TEMP2"])              \n\t"    \
  "lbu    %[temp18],    "#B"(%["#TEMP1"])              \n\t"    \
  "lbu    %[temp19],    "#B"(%["#TEMP2"])              \n\t"    \
  "subu   %[temp20],    %[temp16],    %[temp17]        \n\t"    \
  "lbu    %[temp16],    "#C"(%["#TEMP1"])              \n\t"    \
  "lbu    %[temp17],    "#C"(%["#TEMP2"])              \n\t"    \
  "subu   %["#TEMP0"],  %[temp18],    %[temp19]        \n\t"    \
  "lbu    %[temp18],    "#D"(%["#TEMP1"])              \n\t"    \
  "lbu    %[temp19],    "#D"(%["#TEMP2"])              \n\t"    \
  "subu   %["#TEMP1"],  %[temp16],    %[temp17]        \n\t"    \
  "subu   %["#TEMP2"],  %[temp18],    %[temp19]        \n\t"    \
  "addu   %["#TEMP3"],  %[temp20],    %["#TEMP2"]      \n\t"    \
  "subu   %["#TEMP2"],  %[temp20],    %["#TEMP2"]      \n\t"    \
  "addu   %[temp20],    %["#TEMP0"],  %["#TEMP1"]      \n\t"    \
  "subu   %["#TEMP0"],  %["#TEMP0"],  %["#TEMP1"]      \n\t"    \
  "mul    %[temp16],    %["#TEMP2"],  %[c5352]         \n\t"    \
  "mul    %[temp17],    %["#TEMP2"],  %[c2217]         \n\t"    \
  "mul    %[temp18],    %["#TEMP0"],  %[c5352]         \n\t"    \
  "mul    %[temp19],    %["#TEMP0"],  %[c2217]         \n\t"    \
  "addu   %["#TEMP1"],  %["#TEMP3"],  %[temp20]        \n\t"    \
  "subu   %[temp20],    %["#TEMP3"],  %[temp20]        \n\t"    \
  "sll    %["#TEMP0"],  %["#TEMP1"],  3                \n\t"    \
  "sll    %["#TEMP2"],  %[temp20],    3                \n\t"    \
  "addiu  %[temp16],    %[temp16],    1812             \n\t"    \
  "addiu  %[temp17],    %[temp17],    937              \n\t"    \
  "addu   %[temp16],    %[temp16],    %[temp19]        \n\t"    \
  "subu   %[temp17],    %[temp17],    %[temp18]        \n\t"    \
  "sra    %["#TEMP1"],  %[temp16],    9                \n\t"    \
  "sra    %["#TEMP3"],  %[temp17],    9                \n\t"

// macro for one vertical pass in FTransform
// temp0..temp15 holds tmp[0]..tmp[15]
// A..D - offsets in bytes to store to out buffer
// TEMP0, TEMP4, TEMP8 and TEMP12 - registers for corresponding tmp elements
#define VERTICAL_PASS(A, B, C, D, TEMP0, TEMP4, TEMP8, TEMP12)  \
  "addu   %[temp16],    %["#TEMP0"],  %["#TEMP12"]     \n\t"    \
  "subu   %[temp19],    %["#TEMP0"],  %["#TEMP12"]     \n\t"    \
  "addu   %[temp17],    %["#TEMP4"],  %["#TEMP8"]      \n\t"    \
  "subu   %[temp18],    %["#TEMP4"],  %["#TEMP8"]      \n\t"    \
  "mul    %["#TEMP8"],  %[temp19],    %[c2217]         \n\t"    \
  "mul    %["#TEMP12"], %[temp18],    %[c2217]         \n\t"    \
  "mul    %["#TEMP4"],  %[temp19],    %[c5352]         \n\t"    \
  "mul    %[temp18],    %[temp18],    %[c5352]         \n\t"    \
  "addiu  %[temp16],    %[temp16],    7                \n\t"    \
  "addu   %["#TEMP0"],  %[temp16],    %[temp17]        \n\t"    \
  "sra    %["#TEMP0"],  %["#TEMP0"],  4                \n\t"    \
  "addu   %["#TEMP12"], %["#TEMP12"], %["#TEMP4"]      \n\t"    \
  "subu   %["#TEMP4"],  %[temp16],    %[temp17]        \n\t"    \
  "sra    %["#TEMP4"],  %["#TEMP4"],  4                \n\t"    \
  "addiu  %["#TEMP8"],  %["#TEMP8"],  30000            \n\t"    \
  "addiu  %["#TEMP12"], %["#TEMP12"], 12000            \n\t"    \
  "addiu  %["#TEMP8"],  %["#TEMP8"],  21000            \n\t"    \
  "subu   %["#TEMP8"],  %["#TEMP8"],  %[temp18]        \n\t"    \
  "sra    %["#TEMP12"], %["#TEMP12"], 16               \n\t"    \
  "sra    %["#TEMP8"],  %["#TEMP8"],  16               \n\t"    \
  "addiu  %[temp16],    %["#TEMP12"], 1                \n\t"    \
  "movn   %["#TEMP12"], %[temp16],    %[temp19]        \n\t"    \
  "sh     %["#TEMP0"],  "#A"(%[temp20])                \n\t"    \
  "sh     %["#TEMP4"],  "#C"(%[temp20])                \n\t"    \
  "sh     %["#TEMP8"],  "#D"(%[temp20])                \n\t"    \
  "sh     %["#TEMP12"], "#B"(%[temp20])                \n\t"

static void FTransform(const uint8_t* src, const uint8_t* ref, int16_t* out) {
  int temp0, temp1, temp2, temp3, temp4, temp5, temp6, temp7, temp8;
  int temp9, temp10, temp11, temp12, temp13, temp14, temp15, temp16;
  int temp17, temp18, temp19, temp20;
  const int c2217 = 2217;
  const int c5352 = 5352;
  const int* const args[3] =
      { (const int*)src, (const int*)ref, (const int*)out };

  __asm__ volatile(
    HORIZONTAL_PASS( 0,  1,  2,  3, temp0,  temp1,  temp2,  temp3)
    HORIZONTAL_PASS(16, 17, 18, 19, temp4,  temp5,  temp6,  temp7)
    HORIZONTAL_PASS(32, 33, 34, 35, temp8,  temp9,  temp10, temp11)
    HORIZONTAL_PASS(48, 49, 50, 51, temp12, temp13, temp14, temp15)
    "lw   %[temp20],    8(%[args])                     \n\t"
    VERTICAL_PASS(0,  8, 16, 24, temp0, temp4, temp8,  temp12)
    VERTICAL_PASS(2, 10, 18, 26, temp1, temp5, temp9,  temp13)
    VERTICAL_PASS(4, 12, 20, 28, temp2, temp6, temp10, temp14)
    VERTICAL_PASS(6, 14, 22, 30, temp3, temp7, temp11, temp15)

    : [temp0]"=&r"(temp0), [temp1]"=&r"(temp1), [temp2]"=&r"(temp2),
      [temp3]"=&r"(temp3), [temp4]"=&r"(temp4), [temp5]"=&r"(temp5),
      [temp6]"=&r"(temp6), [temp7]"=&r"(temp7), [temp8]"=&r"(temp8),
      [temp9]"=&r"(temp9), [temp10]"=&r"(temp10), [temp11]"=&r"(temp11),
      [temp12]"=&r"(temp12), [temp13]"=&r"(temp13), [temp14]"=&r"(temp14),
      [temp15]"=&r"(temp15), [temp16]"=&r"(temp16), [temp17]"=&r"(temp17),
      [temp18]"=&r"(temp18), [temp19]"=&r"(temp19), [temp20]"=&r"(temp20)
    : [args]"r"(args), [c2217]"r"(c2217), [c5352]"r"(c5352)
    : "memory", "hi", "lo"
  );
}

#undef VERTICAL_PASS
#undef HORIZONTAL_PASS

// Forward declaration.
extern int VP8GetResidualCostMIPS32(int ctx0, const VP8Residual* const res);

int VP8GetResidualCostMIPS32(int ctx0, const VP8Residual* const res) {
  int n = res->first;
  // should be prob[VP8EncBands[n]], but it's equivalent for n=0 or 1
  int p0 = res->prob[n][ctx0][0];
  const uint16_t* t = res->cost[n][ctx0];
  int cost;
  const int const_2 = 2;
  const int const_255 = 255;
  const int const_max_level = MAX_VARIABLE_LEVEL;
  int res_cost;
  int res_prob;
  int res_coeffs;
  int res_last;
  int v_reg;
  int b_reg;
  int ctx_reg;
  int cost_add, temp_1, temp_2, temp_3;

  if (res->last < 0) {
    return VP8BitCost(0, p0);
  }

  cost = (ctx0 == 0) ? VP8BitCost(1, p0) : 0;

  res_cost = (int)res->cost;
  res_prob = (int)res->prob;
  res_coeffs = (int)res->coeffs;
  res_last = (int)res->last;

  __asm__ volatile(
    ".set   push                                                           \n\t"
    ".set   noreorder                                                      \n\t"

    "sll    %[temp_1],     %[n],              1                            \n\t"
    "addu   %[res_coeffs], %[res_coeffs],     %[temp_1]                    \n\t"
    "slt    %[temp_2],     %[n],              %[res_last]                  \n\t"
    "bnez   %[temp_2],     1f                                              \n\t"
    " li    %[cost_add],   0                                               \n\t"
    "b      2f                                                             \n\t"
    " nop                                                                  \n\t"
  "1:                                                                      \n\t"
    "lh     %[v_reg],      0(%[res_coeffs])                                \n\t"
    "addu   %[b_reg],      %[n],              %[VP8EncBands]               \n\t"
    "move   %[temp_1],     %[const_max_level]                              \n\t"
    "addu   %[cost],       %[cost],           %[cost_add]                  \n\t"
    "negu   %[temp_2],     %[v_reg]                                        \n\t"
    "slti   %[temp_3],     %[v_reg],          0                            \n\t"
    "movn   %[v_reg],      %[temp_2],         %[temp_3]                    \n\t"
    "lbu    %[b_reg],      1(%[b_reg])                                     \n\t"
    "li     %[cost_add],   0                                               \n\t"

    "sltiu  %[temp_3],     %[v_reg],          2                            \n\t"
    "move   %[ctx_reg],    %[v_reg]                                        \n\t"
    "movz   %[ctx_reg],    %[const_2],        %[temp_3]                    \n\t"
    //  cost += VP8LevelCost(t, v);
    "slt    %[temp_3],     %[v_reg],          %[const_max_level]           \n\t"
    "movn   %[temp_1],     %[v_reg],          %[temp_3]                    \n\t"
    "sll    %[temp_2],     %[v_reg],          1                            \n\t"
    "addu   %[temp_2],     %[temp_2],         %[VP8LevelFixedCosts]        \n\t"
    "lhu    %[temp_2],     0(%[temp_2])                                    \n\t"
    "sll    %[temp_1],     %[temp_1],         1                            \n\t"
    "addu   %[temp_1],     %[temp_1],         %[t]                         \n\t"
    "lhu    %[temp_3],     0(%[temp_1])                                    \n\t"
    "addu   %[cost],       %[cost],           %[temp_2]                    \n\t"

    //  t = res->cost[b][ctx];
    "sll    %[temp_1],     %[ctx_reg],        7                            \n\t"
    "sll    %[temp_2],     %[ctx_reg],        3                            \n\t"
    "addu   %[cost],       %[cost],           %[temp_3]                    \n\t"
    "addu   %[temp_1],     %[temp_1],         %[temp_2]                    \n\t"
    "sll    %[temp_2],     %[b_reg],          3                            \n\t"
    "sll    %[temp_3],     %[b_reg],          5                            \n\t"
    "sub    %[temp_2],     %[temp_3],         %[temp_2]                    \n\t"
    "sll    %[temp_3],     %[temp_2],         4                            \n\t"
    "addu   %[temp_1],     %[temp_1],         %[temp_3]                    \n\t"
    "addu   %[temp_2],     %[temp_2],         %[res_cost]                  \n\t"
    "addiu  %[n],          %[n],              1                            \n\t"
    "addu   %[t],          %[temp_1],         %[temp_2]                    \n\t"
    "slt    %[temp_1],     %[n],              %[res_last]                  \n\t"
    "bnez   %[temp_1],     1b                                              \n\t"
    " addiu %[res_coeffs], %[res_coeffs],     2                            \n\t"
   "2:                                                                     \n\t"

    ".set   pop                                                            \n\t"
    : [cost]"+r"(cost), [t]"+r"(t), [n]"+r"(n), [v_reg]"=&r"(v_reg),
      [ctx_reg]"=&r"(ctx_reg), [b_reg]"=&r"(b_reg), [cost_add]"=&r"(cost_add),
      [temp_1]"=&r"(temp_1), [temp_2]"=&r"(temp_2), [temp_3]"=&r"(temp_3)
    : [const_2]"r"(const_2), [const_255]"r"(const_255), [res_last]"r"(res_last),
      [VP8EntropyCost]"r"(VP8EntropyCost), [VP8EncBands]"r"(VP8EncBands),
      [const_max_level]"r"(const_max_level), [res_prob]"r"(res_prob),
      [VP8LevelFixedCosts]"r"(VP8LevelFixedCosts), [res_coeffs]"r"(res_coeffs),
      [res_cost]"r"(res_cost)
    : "memory"
  );

  // Last coefficient is always non-zero
  {
    const int v = abs(res->coeffs[n]);
    assert(v != 0);
    cost += VP8LevelCost(t, v);
    if (n < 15) {
      const int b = VP8EncBands[n + 1];
      const int ctx = (v == 1) ? 1 : 2;
      const int last_p0 = res->prob[b][ctx][0];
      cost += VP8BitCost(0, last_p0);
    }
  }
  return cost;
}

#define GET_SSE_INNER(A, B, C, D)                               \
  "lbu     %[temp0],    "#A"(%[a])                   \n\t"      \
  "lbu     %[temp1],    "#A"(%[b])                   \n\t"      \
  "lbu     %[temp2],    "#B"(%[a])                   \n\t"      \
  "lbu     %[temp3],    "#B"(%[b])                   \n\t"      \
  "lbu     %[temp4],    "#C"(%[a])                   \n\t"      \
  "lbu     %[temp5],    "#C"(%[b])                   \n\t"      \
  "lbu     %[temp6],    "#D"(%[a])                   \n\t"      \
  "lbu     %[temp7],    "#D"(%[b])                   \n\t"      \
  "subu    %[temp0],    %[temp0],     %[temp1]       \n\t"      \
  "subu    %[temp2],    %[temp2],     %[temp3]       \n\t"      \
  "subu    %[temp4],    %[temp4],     %[temp5]       \n\t"      \
  "subu    %[temp6],    %[temp6],     %[temp7]       \n\t"      \
  "madd    %[temp0],    %[temp0]                     \n\t"      \
  "madd    %[temp2],    %[temp2]                     \n\t"      \
  "madd    %[temp4],    %[temp4]                     \n\t"      \
  "madd    %[temp6],    %[temp6]                     \n\t"

#define GET_SSE(A, B, C, D)               \
  GET_SSE_INNER(A, A + 1, A + 2, A + 3)   \
  GET_SSE_INNER(B, B + 1, B + 2, B + 3)   \
  GET_SSE_INNER(C, C + 1, C + 2, C + 3)   \
  GET_SSE_INNER(D, D + 1, D + 2, D + 3)

#if !defined(WORK_AROUND_GCC)
static int SSE16x16(const uint8_t* a, const uint8_t* b) {
  int count;
  int temp0, temp1, temp2, temp3, temp4, temp5, temp6, temp7;

  __asm__ volatile(
     "mult   $zero,    $zero                            \n\t"

     GET_SSE(  0,   4,   8,  12)
     GET_SSE( 16,  20,  24,  28)
     GET_SSE( 32,  36,  40,  44)
     GET_SSE( 48,  52,  56,  60)
     GET_SSE( 64,  68,  72,  76)
     GET_SSE( 80,  84,  88,  92)
     GET_SSE( 96, 100, 104, 108)
     GET_SSE(112, 116, 120, 124)
     GET_SSE(128, 132, 136, 140)
     GET_SSE(144, 148, 152, 156)
     GET_SSE(160, 164, 168, 172)
     GET_SSE(176, 180, 184, 188)
     GET_SSE(192, 196, 200, 204)
     GET_SSE(208, 212, 216, 220)
     GET_SSE(224, 228, 232, 236)
     GET_SSE(240, 244, 248, 252)

    "mflo    %[count]                                   \n\t"
    : [temp0]"=&r"(temp0), [temp1]"=&r"(temp1), [temp2]"=&r"(temp2),
      [temp3]"=&r"(temp3), [temp4]"=&r"(temp4), [temp5]"=&r"(temp5),
      [temp6]"=&r"(temp6), [temp7]"=&r"(temp7), [count]"=&r"(count)
    : [a]"r"(a), [b]"r"(b)
    : "memory", "hi" , "lo"
  );
  return count;
}

static int SSE16x8(const uint8_t* a, const uint8_t* b) {
  int count;
  int temp0, temp1, temp2, temp3, temp4, temp5, temp6, temp7;

  __asm__ volatile(
     "mult   $zero,    $zero                            \n\t"

     GET_SSE(  0,   4,   8,  12)
     GET_SSE( 16,  20,  24,  28)
     GET_SSE( 32,  36,  40,  44)
     GET_SSE( 48,  52,  56,  60)
     GET_SSE( 64,  68,  72,  76)
     GET_SSE( 80,  84,  88,  92)
     GET_SSE( 96, 100, 104, 108)
     GET_SSE(112, 116, 120, 124)

    "mflo    %[count]                                   \n\t"
    : [temp0]"=&r"(temp0), [temp1]"=&r"(temp1), [temp2]"=&r"(temp2),
      [temp3]"=&r"(temp3), [temp4]"=&r"(temp4), [temp5]"=&r"(temp5),
      [temp6]"=&r"(temp6), [temp7]"=&r"(temp7), [count]"=&r"(count)
    : [a]"r"(a), [b]"r"(b)
    : "memory", "hi" , "lo"
  );
  return count;
}

static int SSE8x8(const uint8_t* a, const uint8_t* b) {
  int count;
  int temp0, temp1, temp2, temp3, temp4, temp5, temp6, temp7;

  __asm__ volatile(
     "mult   $zero,    $zero                            \n\t"

     GET_SSE( 0,   4,  16,  20)
     GET_SSE(32,  36,  48,  52)
     GET_SSE(64,  68,  80,  84)
     GET_SSE(96, 100, 112, 116)

    "mflo    %[count]                                   \n\t"
    : [temp0]"=&r"(temp0), [temp1]"=&r"(temp1), [temp2]"=&r"(temp2),
      [temp3]"=&r"(temp3), [temp4]"=&r"(temp4), [temp5]"=&r"(temp5),
      [temp6]"=&r"(temp6), [temp7]"=&r"(temp7), [count]"=&r"(count)
    : [a]"r"(a), [b]"r"(b)
    : "memory", "hi" , "lo"
  );
  return count;
}

static int SSE4x4(const uint8_t* a, const uint8_t* b) {
  int count;
  int temp0, temp1, temp2, temp3, temp4, temp5, temp6, temp7;

  __asm__ volatile(
     "mult   $zero,    $zero                            \n\t"

     GET_SSE(0, 16, 32, 48)

    "mflo    %[count]                                   \n\t"
    : [temp0]"=&r"(temp0), [temp1]"=&r"(temp1), [temp2]"=&r"(temp2),
      [temp3]"=&r"(temp3), [temp4]"=&r"(temp4), [temp5]"=&r"(temp5),
      [temp6]"=&r"(temp6), [temp7]"=&r"(temp7), [count]"=&r"(count)
    : [a]"r"(a), [b]"r"(b)
    : "memory", "hi" , "lo"
  );
  return count;
}

#endif  // WORK_AROUND_GCC

#undef GET_SSE_MIPS32
#undef GET_SSE_MIPS32_INNER

#endif  // WEBP_USE_MIPS32

//------------------------------------------------------------------------------
// Entry point

extern void VP8EncDspInitMIPS32(void);

void VP8EncDspInitMIPS32(void) {
#if defined(WEBP_USE_MIPS32)
  VP8ITransform = ITransform;
  VP8EncQuantizeBlock = QuantizeBlock;
  VP8TDisto4x4 = Disto4x4;
  VP8TDisto16x16 = Disto16x16;
  VP8FTransform = FTransform;
#if !defined(WORK_AROUND_GCC)
  VP8SSE16x16 = SSE16x16;
  VP8SSE8x8 = SSE8x8;
  VP8SSE16x8 = SSE16x8;
  VP8SSE4x4 = SSE4x4;
#endif
#endif  // WEBP_USE_MIPS32
}
