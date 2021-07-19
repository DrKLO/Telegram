/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_audio/third_party/ooura/fft_size_128/ooura_fft.h"
#include "common_audio/third_party/ooura/fft_size_128/ooura_fft_tables_common.h"

namespace webrtc {

#if defined(MIPS_FPU_LE)
void bitrv2_128_mips(float* a) {
  // n is 128
  float xr, xi, yr, yi;

  xr = a[8];
  xi = a[9];
  yr = a[16];
  yi = a[17];
  a[8] = yr;
  a[9] = yi;
  a[16] = xr;
  a[17] = xi;

  xr = a[64];
  xi = a[65];
  yr = a[2];
  yi = a[3];
  a[64] = yr;
  a[65] = yi;
  a[2] = xr;
  a[3] = xi;

  xr = a[72];
  xi = a[73];
  yr = a[18];
  yi = a[19];
  a[72] = yr;
  a[73] = yi;
  a[18] = xr;
  a[19] = xi;

  xr = a[80];
  xi = a[81];
  yr = a[10];
  yi = a[11];
  a[80] = yr;
  a[81] = yi;
  a[10] = xr;
  a[11] = xi;

  xr = a[88];
  xi = a[89];
  yr = a[26];
  yi = a[27];
  a[88] = yr;
  a[89] = yi;
  a[26] = xr;
  a[27] = xi;

  xr = a[74];
  xi = a[75];
  yr = a[82];
  yi = a[83];
  a[74] = yr;
  a[75] = yi;
  a[82] = xr;
  a[83] = xi;

  xr = a[32];
  xi = a[33];
  yr = a[4];
  yi = a[5];
  a[32] = yr;
  a[33] = yi;
  a[4] = xr;
  a[5] = xi;

  xr = a[40];
  xi = a[41];
  yr = a[20];
  yi = a[21];
  a[40] = yr;
  a[41] = yi;
  a[20] = xr;
  a[21] = xi;

  xr = a[48];
  xi = a[49];
  yr = a[12];
  yi = a[13];
  a[48] = yr;
  a[49] = yi;
  a[12] = xr;
  a[13] = xi;

  xr = a[56];
  xi = a[57];
  yr = a[28];
  yi = a[29];
  a[56] = yr;
  a[57] = yi;
  a[28] = xr;
  a[29] = xi;

  xr = a[34];
  xi = a[35];
  yr = a[68];
  yi = a[69];
  a[34] = yr;
  a[35] = yi;
  a[68] = xr;
  a[69] = xi;

  xr = a[42];
  xi = a[43];
  yr = a[84];
  yi = a[85];
  a[42] = yr;
  a[43] = yi;
  a[84] = xr;
  a[85] = xi;

  xr = a[50];
  xi = a[51];
  yr = a[76];
  yi = a[77];
  a[50] = yr;
  a[51] = yi;
  a[76] = xr;
  a[77] = xi;

  xr = a[58];
  xi = a[59];
  yr = a[92];
  yi = a[93];
  a[58] = yr;
  a[59] = yi;
  a[92] = xr;
  a[93] = xi;

  xr = a[44];
  xi = a[45];
  yr = a[52];
  yi = a[53];
  a[44] = yr;
  a[45] = yi;
  a[52] = xr;
  a[53] = xi;

  xr = a[96];
  xi = a[97];
  yr = a[6];
  yi = a[7];
  a[96] = yr;
  a[97] = yi;
  a[6] = xr;
  a[7] = xi;

  xr = a[104];
  xi = a[105];
  yr = a[22];
  yi = a[23];
  a[104] = yr;
  a[105] = yi;
  a[22] = xr;
  a[23] = xi;

  xr = a[112];
  xi = a[113];
  yr = a[14];
  yi = a[15];
  a[112] = yr;
  a[113] = yi;
  a[14] = xr;
  a[15] = xi;

  xr = a[120];
  xi = a[121];
  yr = a[30];
  yi = a[31];
  a[120] = yr;
  a[121] = yi;
  a[30] = xr;
  a[31] = xi;

  xr = a[98];
  xi = a[99];
  yr = a[70];
  yi = a[71];
  a[98] = yr;
  a[99] = yi;
  a[70] = xr;
  a[71] = xi;

  xr = a[106];
  xi = a[107];
  yr = a[86];
  yi = a[87];
  a[106] = yr;
  a[107] = yi;
  a[86] = xr;
  a[87] = xi;

  xr = a[114];
  xi = a[115];
  yr = a[78];
  yi = a[79];
  a[114] = yr;
  a[115] = yi;
  a[78] = xr;
  a[79] = xi;

  xr = a[122];
  xi = a[123];
  yr = a[94];
  yi = a[95];
  a[122] = yr;
  a[123] = yi;
  a[94] = xr;
  a[95] = xi;

  xr = a[100];
  xi = a[101];
  yr = a[38];
  yi = a[39];
  a[100] = yr;
  a[101] = yi;
  a[38] = xr;
  a[39] = xi;

  xr = a[108];
  xi = a[109];
  yr = a[54];
  yi = a[55];
  a[108] = yr;
  a[109] = yi;
  a[54] = xr;
  a[55] = xi;

  xr = a[116];
  xi = a[117];
  yr = a[46];
  yi = a[47];
  a[116] = yr;
  a[117] = yi;
  a[46] = xr;
  a[47] = xi;

  xr = a[124];
  xi = a[125];
  yr = a[62];
  yi = a[63];
  a[124] = yr;
  a[125] = yi;
  a[62] = xr;
  a[63] = xi;

  xr = a[110];
  xi = a[111];
  yr = a[118];
  yi = a[119];
  a[110] = yr;
  a[111] = yi;
  a[118] = xr;
  a[119] = xi;
}

void cft1st_128_mips(float* a) {
  float f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14;
  int a_ptr, p1_rdft, p2_rdft, count;
  const float* first = rdft_wk3ri_first;
  const float* second = rdft_wk3ri_second;

  __asm __volatile(
      ".set       push                                                    \n\t"
      ".set       noreorder                                               \n\t"
      // first 8
      "lwc1       %[f0],        0(%[a])                                   \n\t"
      "lwc1       %[f1],        4(%[a])                                   \n\t"
      "lwc1       %[f2],        8(%[a])                                   \n\t"
      "lwc1       %[f3],        12(%[a])                                  \n\t"
      "lwc1       %[f4],        16(%[a])                                  \n\t"
      "lwc1       %[f5],        20(%[a])                                  \n\t"
      "lwc1       %[f6],        24(%[a])                                  \n\t"
      "lwc1       %[f7],        28(%[a])                                  \n\t"
      "add.s      %[f8],        %[f0],        %[f2]                       \n\t"
      "sub.s      %[f0],        %[f0],        %[f2]                       \n\t"
      "add.s      %[f2],        %[f4],        %[f6]                       \n\t"
      "sub.s      %[f4],        %[f4],        %[f6]                       \n\t"
      "add.s      %[f6],        %[f1],        %[f3]                       \n\t"
      "sub.s      %[f1],        %[f1],        %[f3]                       \n\t"
      "add.s      %[f3],        %[f5],        %[f7]                       \n\t"
      "sub.s      %[f5],        %[f5],        %[f7]                       \n\t"
      "add.s      %[f7],        %[f8],        %[f2]                       \n\t"
      "sub.s      %[f8],        %[f8],        %[f2]                       \n\t"
      "sub.s      %[f2],        %[f1],        %[f4]                       \n\t"
      "add.s      %[f1],        %[f1],        %[f4]                       \n\t"
      "add.s      %[f4],        %[f6],        %[f3]                       \n\t"
      "sub.s      %[f6],        %[f6],        %[f3]                       \n\t"
      "sub.s      %[f3],        %[f0],        %[f5]                       \n\t"
      "add.s      %[f0],        %[f0],        %[f5]                       \n\t"
      "swc1       %[f7],        0(%[a])                                   \n\t"
      "swc1       %[f8],        16(%[a])                                  \n\t"
      "swc1       %[f2],        28(%[a])                                  \n\t"
      "swc1       %[f1],        12(%[a])                                  \n\t"
      "swc1       %[f4],        4(%[a])                                   \n\t"
      "swc1       %[f6],        20(%[a])                                  \n\t"
      "swc1       %[f3],        8(%[a])                                   \n\t"
      "swc1       %[f0],        24(%[a])                                  \n\t"
      // second 8
      "lwc1       %[f0],        32(%[a])                                  \n\t"
      "lwc1       %[f1],        36(%[a])                                  \n\t"
      "lwc1       %[f2],        40(%[a])                                  \n\t"
      "lwc1       %[f3],        44(%[a])                                  \n\t"
      "lwc1       %[f4],        48(%[a])                                  \n\t"
      "lwc1       %[f5],        52(%[a])                                  \n\t"
      "lwc1       %[f6],        56(%[a])                                  \n\t"
      "lwc1       %[f7],        60(%[a])                                  \n\t"
      "add.s      %[f8],        %[f4],        %[f6]                       \n\t"
      "sub.s      %[f4],        %[f4],        %[f6]                       \n\t"
      "add.s      %[f6],        %[f1],        %[f3]                       \n\t"
      "sub.s      %[f1],        %[f1],        %[f3]                       \n\t"
      "add.s      %[f3],        %[f0],        %[f2]                       \n\t"
      "sub.s      %[f0],        %[f0],        %[f2]                       \n\t"
      "add.s      %[f2],        %[f5],        %[f7]                       \n\t"
      "sub.s      %[f5],        %[f5],        %[f7]                       \n\t"
      "add.s      %[f7],        %[f4],        %[f1]                       \n\t"
      "sub.s      %[f4],        %[f4],        %[f1]                       \n\t"
      "add.s      %[f1],        %[f3],        %[f8]                       \n\t"
      "sub.s      %[f3],        %[f3],        %[f8]                       \n\t"
      "sub.s      %[f8],        %[f0],        %[f5]                       \n\t"
      "add.s      %[f0],        %[f0],        %[f5]                       \n\t"
      "add.s      %[f5],        %[f6],        %[f2]                       \n\t"
      "sub.s      %[f6],        %[f2],        %[f6]                       \n\t"
      "lwc1       %[f9],        8(%[rdft_w])                              \n\t"
      "sub.s      %[f2],        %[f8],        %[f7]                       \n\t"
      "add.s      %[f8],        %[f8],        %[f7]                       \n\t"
      "sub.s      %[f7],        %[f4],        %[f0]                       \n\t"
      "add.s      %[f4],        %[f4],        %[f0]                       \n\t"
      // prepare for loop
      "addiu      %[a_ptr],     %[a],         64                          \n\t"
      "addiu      %[p1_rdft],   %[rdft_w],    8                           \n\t"
      "addiu      %[p2_rdft],   %[rdft_w],    16                          \n\t"
      "addiu      %[count],     $zero,        7                           \n\t"
      // finish second 8
      "mul.s      %[f2],        %[f9],        %[f2]                       \n\t"
      "mul.s      %[f8],        %[f9],        %[f8]                       \n\t"
      "mul.s      %[f7],        %[f9],        %[f7]                       \n\t"
      "mul.s      %[f4],        %[f9],        %[f4]                       \n\t"
      "swc1       %[f1],        32(%[a])                                  \n\t"
      "swc1       %[f3],        52(%[a])                                  \n\t"
      "swc1       %[f5],        36(%[a])                                  \n\t"
      "swc1       %[f6],        48(%[a])                                  \n\t"
      "swc1       %[f2],        40(%[a])                                  \n\t"
      "swc1       %[f8],        44(%[a])                                  \n\t"
      "swc1       %[f7],        56(%[a])                                  \n\t"
      "swc1       %[f4],        60(%[a])                                  \n\t"
      // loop
      "1:                                                                  \n\t"
      "lwc1       %[f0],        0(%[a_ptr])                               \n\t"
      "lwc1       %[f1],        4(%[a_ptr])                               \n\t"
      "lwc1       %[f2],        8(%[a_ptr])                               \n\t"
      "lwc1       %[f3],        12(%[a_ptr])                              \n\t"
      "lwc1       %[f4],        16(%[a_ptr])                              \n\t"
      "lwc1       %[f5],        20(%[a_ptr])                              \n\t"
      "lwc1       %[f6],        24(%[a_ptr])                              \n\t"
      "lwc1       %[f7],        28(%[a_ptr])                              \n\t"
      "add.s      %[f8],        %[f0],        %[f2]                       \n\t"
      "sub.s      %[f0],        %[f0],        %[f2]                       \n\t"
      "add.s      %[f2],        %[f4],        %[f6]                       \n\t"
      "sub.s      %[f4],        %[f4],        %[f6]                       \n\t"
      "add.s      %[f6],        %[f1],        %[f3]                       \n\t"
      "sub.s      %[f1],        %[f1],        %[f3]                       \n\t"
      "add.s      %[f3],        %[f5],        %[f7]                       \n\t"
      "sub.s      %[f5],        %[f5],        %[f7]                       \n\t"
      "lwc1       %[f10],       4(%[p1_rdft])                             \n\t"
      "lwc1       %[f11],       0(%[p2_rdft])                             \n\t"
      "lwc1       %[f12],       4(%[p2_rdft])                             \n\t"
      "lwc1       %[f13],       8(%[first])                               \n\t"
      "lwc1       %[f14],       12(%[first])                              \n\t"
      "add.s      %[f7],        %[f8],        %[f2]                       \n\t"
      "sub.s      %[f8],        %[f8],        %[f2]                       \n\t"
      "add.s      %[f2],        %[f6],        %[f3]                       \n\t"
      "sub.s      %[f6],        %[f6],        %[f3]                       \n\t"
      "add.s      %[f3],        %[f0],        %[f5]                       \n\t"
      "sub.s      %[f0],        %[f0],        %[f5]                       \n\t"
      "add.s      %[f5],        %[f1],        %[f4]                       \n\t"
      "sub.s      %[f1],        %[f1],        %[f4]                       \n\t"
      "swc1       %[f7],        0(%[a_ptr])                               \n\t"
      "swc1       %[f2],        4(%[a_ptr])                               \n\t"
      "mul.s      %[f4],        %[f9],        %[f8]                       \n\t"
#if defined(MIPS32_R2_LE)
      "mul.s      %[f8],        %[f10],       %[f8]                       \n\t"
      "mul.s      %[f7],        %[f11],       %[f0]                       \n\t"
      "mul.s      %[f0],        %[f12],       %[f0]                       \n\t"
      "mul.s      %[f2],        %[f13],       %[f3]                       \n\t"
      "mul.s      %[f3],        %[f14],       %[f3]                       \n\t"
      "nmsub.s    %[f4],        %[f4],        %[f10],       %[f6]         \n\t"
      "madd.s     %[f8],        %[f8],        %[f9],        %[f6]         \n\t"
      "nmsub.s    %[f7],        %[f7],        %[f12],       %[f5]         \n\t"
      "madd.s     %[f0],        %[f0],        %[f11],       %[f5]         \n\t"
      "nmsub.s    %[f2],        %[f2],        %[f14],       %[f1]         \n\t"
      "madd.s     %[f3],        %[f3],        %[f13],       %[f1]         \n\t"
#else
      "mul.s      %[f7],        %[f10],       %[f6]                       \n\t"
      "mul.s      %[f6],        %[f9],        %[f6]                       \n\t"
      "mul.s      %[f8],        %[f10],       %[f8]                       \n\t"
      "mul.s      %[f2],        %[f11],       %[f0]                       \n\t"
      "mul.s      %[f11],       %[f11],       %[f5]                       \n\t"
      "mul.s      %[f5],        %[f12],       %[f5]                       \n\t"
      "mul.s      %[f0],        %[f12],       %[f0]                       \n\t"
      "mul.s      %[f12],       %[f13],       %[f3]                       \n\t"
      "mul.s      %[f13],       %[f13],       %[f1]                       \n\t"
      "mul.s      %[f1],        %[f14],       %[f1]                       \n\t"
      "mul.s      %[f3],        %[f14],       %[f3]                       \n\t"
      "sub.s      %[f4],        %[f4],        %[f7]                       \n\t"
      "add.s      %[f8],        %[f6],        %[f8]                       \n\t"
      "sub.s      %[f7],        %[f2],        %[f5]                       \n\t"
      "add.s      %[f0],        %[f11],       %[f0]                       \n\t"
      "sub.s      %[f2],        %[f12],       %[f1]                       \n\t"
      "add.s      %[f3],        %[f13],       %[f3]                       \n\t"
#endif
      "swc1       %[f4],        16(%[a_ptr])                              \n\t"
      "swc1       %[f8],        20(%[a_ptr])                              \n\t"
      "swc1       %[f7],        8(%[a_ptr])                               \n\t"
      "swc1       %[f0],        12(%[a_ptr])                              \n\t"
      "swc1       %[f2],        24(%[a_ptr])                              \n\t"
      "swc1       %[f3],        28(%[a_ptr])                              \n\t"
      "lwc1       %[f0],        32(%[a_ptr])                              \n\t"
      "lwc1       %[f1],        36(%[a_ptr])                              \n\t"
      "lwc1       %[f2],        40(%[a_ptr])                              \n\t"
      "lwc1       %[f3],        44(%[a_ptr])                              \n\t"
      "lwc1       %[f4],        48(%[a_ptr])                              \n\t"
      "lwc1       %[f5],        52(%[a_ptr])                              \n\t"
      "lwc1       %[f6],        56(%[a_ptr])                              \n\t"
      "lwc1       %[f7],        60(%[a_ptr])                              \n\t"
      "add.s      %[f8],        %[f0],        %[f2]                       \n\t"
      "sub.s      %[f0],        %[f0],        %[f2]                       \n\t"
      "add.s      %[f2],        %[f4],        %[f6]                       \n\t"
      "sub.s      %[f4],        %[f4],        %[f6]                       \n\t"
      "add.s      %[f6],        %[f1],        %[f3]                       \n\t"
      "sub.s      %[f1],        %[f1],        %[f3]                       \n\t"
      "add.s      %[f3],        %[f5],        %[f7]                       \n\t"
      "sub.s      %[f5],        %[f5],        %[f7]                       \n\t"
      "lwc1       %[f11],       8(%[p2_rdft])                             \n\t"
      "lwc1       %[f12],       12(%[p2_rdft])                            \n\t"
      "lwc1       %[f13],       8(%[second])                              \n\t"
      "lwc1       %[f14],       12(%[second])                             \n\t"
      "add.s      %[f7],        %[f8],        %[f2]                       \n\t"
      "sub.s      %[f8],        %[f2],        %[f8]                       \n\t"
      "add.s      %[f2],        %[f6],        %[f3]                       \n\t"
      "sub.s      %[f6],        %[f3],        %[f6]                       \n\t"
      "add.s      %[f3],        %[f0],        %[f5]                       \n\t"
      "sub.s      %[f0],        %[f0],        %[f5]                       \n\t"
      "add.s      %[f5],        %[f1],        %[f4]                       \n\t"
      "sub.s      %[f1],        %[f1],        %[f4]                       \n\t"
      "swc1       %[f7],        32(%[a_ptr])                              \n\t"
      "swc1       %[f2],        36(%[a_ptr])                              \n\t"
      "mul.s      %[f4],        %[f10],       %[f8]                       \n\t"
#if defined(MIPS32_R2_LE)
      "mul.s      %[f10],       %[f10],       %[f6]                       \n\t"
      "mul.s      %[f7],        %[f11],       %[f0]                       \n\t"
      "mul.s      %[f11],       %[f11],       %[f5]                       \n\t"
      "mul.s      %[f2],        %[f13],       %[f3]                       \n\t"
      "mul.s      %[f13],       %[f13],       %[f1]                       \n\t"
      "madd.s     %[f4],        %[f4],        %[f9],        %[f6]         \n\t"
      "nmsub.s    %[f10],       %[f10],       %[f9],        %[f8]         \n\t"
      "nmsub.s    %[f7],        %[f7],        %[f12],       %[f5]         \n\t"
      "madd.s     %[f11],       %[f11],       %[f12],       %[f0]         \n\t"
      "nmsub.s    %[f2],        %[f2],        %[f14],       %[f1]         \n\t"
      "madd.s     %[f13],       %[f13],       %[f14],       %[f3]         \n\t"
#else
      "mul.s      %[f2],        %[f9],        %[f6]                       \n\t"
      "mul.s      %[f10],       %[f10],       %[f6]                       \n\t"
      "mul.s      %[f9],        %[f9],        %[f8]                       \n\t"
      "mul.s      %[f7],        %[f11],       %[f0]                       \n\t"
      "mul.s      %[f8],        %[f12],       %[f5]                       \n\t"
      "mul.s      %[f11],       %[f11],       %[f5]                       \n\t"
      "mul.s      %[f12],       %[f12],       %[f0]                       \n\t"
      "mul.s      %[f5],        %[f13],       %[f3]                       \n\t"
      "mul.s      %[f0],        %[f14],       %[f1]                       \n\t"
      "mul.s      %[f13],       %[f13],       %[f1]                       \n\t"
      "mul.s      %[f14],       %[f14],       %[f3]                       \n\t"
      "add.s      %[f4],        %[f4],        %[f2]                       \n\t"
      "sub.s      %[f10],       %[f10],       %[f9]                       \n\t"
      "sub.s      %[f7],        %[f7],        %[f8]                       \n\t"
      "add.s      %[f11],       %[f11],       %[f12]                      \n\t"
      "sub.s      %[f2],        %[f5],        %[f0]                       \n\t"
      "add.s      %[f13],       %[f13],       %[f14]                      \n\t"
#endif
      "swc1       %[f4],        48(%[a_ptr])                              \n\t"
      "swc1       %[f10],       52(%[a_ptr])                              \n\t"
      "swc1       %[f7],        40(%[a_ptr])                              \n\t"
      "swc1       %[f11],       44(%[a_ptr])                              \n\t"
      "swc1       %[f2],        56(%[a_ptr])                              \n\t"
      "swc1       %[f13],       60(%[a_ptr])                              \n\t"
      "addiu      %[count],     %[count],     -1                          \n\t"
      "lwc1       %[f9],        8(%[p1_rdft])                             \n\t"
      "addiu      %[a_ptr],     %[a_ptr],     64                          \n\t"
      "addiu      %[p1_rdft],   %[p1_rdft],   8                           \n\t"
      "addiu      %[p2_rdft],   %[p2_rdft],   16                          \n\t"
      "addiu      %[first],     %[first],     8                           \n\t"
      "bgtz       %[count],     1b                                        \n\t"
      " addiu     %[second],    %[second],    8                           \n\t"
      ".set       pop                                                     \n\t"
      : [f0] "=&f"(f0), [f1] "=&f"(f1), [f2] "=&f"(f2), [f3] "=&f"(f3),
        [f4] "=&f"(f4), [f5] "=&f"(f5), [f6] "=&f"(f6), [f7] "=&f"(f7),
        [f8] "=&f"(f8), [f9] "=&f"(f9), [f10] "=&f"(f10), [f11] "=&f"(f11),
        [f12] "=&f"(f12), [f13] "=&f"(f13), [f14] "=&f"(f14),
        [a_ptr] "=&r"(a_ptr), [p1_rdft] "=&r"(p1_rdft), [first] "+r"(first),
        [p2_rdft] "=&r"(p2_rdft), [count] "=&r"(count), [second] "+r"(second)
      : [a] "r"(a), [rdft_w] "r"(rdft_w)
      : "memory");
}

void cftmdl_128_mips(float* a) {
  float f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14;
  int tmp_a, count;
  __asm __volatile(
      ".set       push                                      \n\t"
      ".set       noreorder                                 \n\t"
      "addiu      %[tmp_a],   %[a],         0               \n\t"
      "addiu      %[count],   $zero,        4               \n\t"
      "1:                                                    \n\t"
      "addiu      %[count],   %[count],     -1              \n\t"
      "lwc1       %[f0],      0(%[tmp_a])                   \n\t"
      "lwc1       %[f2],      32(%[tmp_a])                  \n\t"
      "lwc1       %[f4],      64(%[tmp_a])                  \n\t"
      "lwc1       %[f6],      96(%[tmp_a])                  \n\t"
      "lwc1       %[f1],      4(%[tmp_a])                   \n\t"
      "lwc1       %[f3],      36(%[tmp_a])                  \n\t"
      "lwc1       %[f5],      68(%[tmp_a])                  \n\t"
      "lwc1       %[f7],      100(%[tmp_a])                 \n\t"
      "add.s      %[f8],      %[f0],        %[f2]           \n\t"
      "sub.s      %[f0],      %[f0],        %[f2]           \n\t"
      "add.s      %[f2],      %[f4],        %[f6]           \n\t"
      "sub.s      %[f4],      %[f4],        %[f6]           \n\t"
      "add.s      %[f6],      %[f1],        %[f3]           \n\t"
      "sub.s      %[f1],      %[f1],        %[f3]           \n\t"
      "add.s      %[f3],      %[f5],        %[f7]           \n\t"
      "sub.s      %[f5],      %[f5],        %[f7]           \n\t"
      "add.s      %[f7],      %[f8],        %[f2]           \n\t"
      "sub.s      %[f8],      %[f8],        %[f2]           \n\t"
      "add.s      %[f2],      %[f1],        %[f4]           \n\t"
      "sub.s      %[f1],      %[f1],        %[f4]           \n\t"
      "add.s      %[f4],      %[f6],        %[f3]           \n\t"
      "sub.s      %[f6],      %[f6],        %[f3]           \n\t"
      "sub.s      %[f3],      %[f0],        %[f5]           \n\t"
      "add.s      %[f0],      %[f0],        %[f5]           \n\t"
      "swc1       %[f7],      0(%[tmp_a])                   \n\t"
      "swc1       %[f8],      64(%[tmp_a])                  \n\t"
      "swc1       %[f2],      36(%[tmp_a])                  \n\t"
      "swc1       %[f1],      100(%[tmp_a])                 \n\t"
      "swc1       %[f4],      4(%[tmp_a])                   \n\t"
      "swc1       %[f6],      68(%[tmp_a])                  \n\t"
      "swc1       %[f3],      32(%[tmp_a])                  \n\t"
      "swc1       %[f0],      96(%[tmp_a])                  \n\t"
      "bgtz       %[count],   1b                            \n\t"
      " addiu     %[tmp_a],   %[tmp_a],     8               \n\t"
      ".set       pop                                       \n\t"
      : [f0] "=&f"(f0), [f1] "=&f"(f1), [f2] "=&f"(f2), [f3] "=&f"(f3),
        [f4] "=&f"(f4), [f5] "=&f"(f5), [f6] "=&f"(f6), [f7] "=&f"(f7),
        [f8] "=&f"(f8), [tmp_a] "=&r"(tmp_a), [count] "=&r"(count)
      : [a] "r"(a)
      : "memory");
  f9 = rdft_w[2];
  __asm __volatile(
      ".set       push                                      \n\t"
      ".set       noreorder                                 \n\t"
      "addiu      %[tmp_a],   %[a],         128             \n\t"
      "addiu      %[count],   $zero,        4               \n\t"
      "1:                                                    \n\t"
      "addiu      %[count],   %[count],     -1              \n\t"
      "lwc1       %[f0],      0(%[tmp_a])                   \n\t"
      "lwc1       %[f2],      32(%[tmp_a])                  \n\t"
      "lwc1       %[f5],      68(%[tmp_a])                  \n\t"
      "lwc1       %[f7],      100(%[tmp_a])                 \n\t"
      "lwc1       %[f1],      4(%[tmp_a])                   \n\t"
      "lwc1       %[f3],      36(%[tmp_a])                  \n\t"
      "lwc1       %[f4],      64(%[tmp_a])                  \n\t"
      "lwc1       %[f6],      96(%[tmp_a])                  \n\t"
      "sub.s      %[f8],      %[f0],        %[f2]           \n\t"
      "add.s      %[f0],      %[f0],        %[f2]           \n\t"
      "sub.s      %[f2],      %[f5],        %[f7]           \n\t"
      "add.s      %[f5],      %[f5],        %[f7]           \n\t"
      "sub.s      %[f7],      %[f1],        %[f3]           \n\t"
      "add.s      %[f1],      %[f1],        %[f3]           \n\t"
      "sub.s      %[f3],      %[f4],        %[f6]           \n\t"
      "add.s      %[f4],      %[f4],        %[f6]           \n\t"
      "sub.s      %[f6],      %[f8],        %[f2]           \n\t"
      "add.s      %[f8],      %[f8],        %[f2]           \n\t"
      "add.s      %[f2],      %[f5],        %[f1]           \n\t"
      "sub.s      %[f5],      %[f5],        %[f1]           \n\t"
      "add.s      %[f1],      %[f3],        %[f7]           \n\t"
      "sub.s      %[f3],      %[f3],        %[f7]           \n\t"
      "add.s      %[f7],      %[f0],        %[f4]           \n\t"
      "sub.s      %[f0],      %[f0],        %[f4]           \n\t"
      "sub.s      %[f4],      %[f6],        %[f1]           \n\t"
      "add.s      %[f6],      %[f6],        %[f1]           \n\t"
      "sub.s      %[f1],      %[f3],        %[f8]           \n\t"
      "add.s      %[f3],      %[f3],        %[f8]           \n\t"
      "mul.s      %[f4],      %[f4],        %[f9]           \n\t"
      "mul.s      %[f6],      %[f6],        %[f9]           \n\t"
      "mul.s      %[f1],      %[f1],        %[f9]           \n\t"
      "mul.s      %[f3],      %[f3],        %[f9]           \n\t"
      "swc1       %[f7],      0(%[tmp_a])                   \n\t"
      "swc1       %[f2],      4(%[tmp_a])                   \n\t"
      "swc1       %[f5],      64(%[tmp_a])                  \n\t"
      "swc1       %[f0],      68(%[tmp_a])                  \n\t"
      "swc1       %[f4],      32(%[tmp_a])                  \n\t"
      "swc1       %[f6],      36(%[tmp_a])                  \n\t"
      "swc1       %[f1],      96(%[tmp_a])                  \n\t"
      "swc1       %[f3],      100(%[tmp_a])                 \n\t"
      "bgtz       %[count],   1b                            \n\t"
      " addiu     %[tmp_a],   %[tmp_a],     8               \n\t"
      ".set       pop                                       \n\t"
      : [f0] "=&f"(f0), [f1] "=&f"(f1), [f2] "=&f"(f2), [f3] "=&f"(f3),
        [f4] "=&f"(f4), [f5] "=&f"(f5), [f6] "=&f"(f6), [f7] "=&f"(f7),
        [f8] "=&f"(f8), [tmp_a] "=&r"(tmp_a), [count] "=&r"(count)
      : [a] "r"(a), [f9] "f"(f9)
      : "memory");
  f10 = rdft_w[3];
  f11 = rdft_w[4];
  f12 = rdft_w[5];
  f13 = rdft_wk3ri_first[2];
  f14 = rdft_wk3ri_first[3];

  __asm __volatile(
      ".set       push                                                    \n\t"
      ".set       noreorder                                               \n\t"
      "addiu      %[tmp_a],     %[a],         256                         \n\t"
      "addiu      %[count],     $zero,        4                           \n\t"
      "1:                                                                  \n\t"
      "addiu      %[count],     %[count],     -1                          \n\t"
      "lwc1       %[f0],        0(%[tmp_a])                               \n\t"
      "lwc1       %[f2],        32(%[tmp_a])                              \n\t"
      "lwc1       %[f4],        64(%[tmp_a])                              \n\t"
      "lwc1       %[f6],        96(%[tmp_a])                              \n\t"
      "lwc1       %[f1],        4(%[tmp_a])                               \n\t"
      "lwc1       %[f3],        36(%[tmp_a])                              \n\t"
      "lwc1       %[f5],        68(%[tmp_a])                              \n\t"
      "lwc1       %[f7],        100(%[tmp_a])                             \n\t"
      "add.s      %[f8],        %[f0],        %[f2]                       \n\t"
      "sub.s      %[f0],        %[f0],        %[f2]                       \n\t"
      "add.s      %[f2],        %[f4],        %[f6]                       \n\t"
      "sub.s      %[f4],        %[f4],        %[f6]                       \n\t"
      "add.s      %[f6],        %[f1],        %[f3]                       \n\t"
      "sub.s      %[f1],        %[f1],        %[f3]                       \n\t"
      "add.s      %[f3],        %[f5],        %[f7]                       \n\t"
      "sub.s      %[f5],        %[f5],        %[f7]                       \n\t"
      "sub.s      %[f7],        %[f8],        %[f2]                       \n\t"
      "add.s      %[f8],        %[f8],        %[f2]                       \n\t"
      "add.s      %[f2],        %[f1],        %[f4]                       \n\t"
      "sub.s      %[f1],        %[f1],        %[f4]                       \n\t"
      "sub.s      %[f4],        %[f6],        %[f3]                       \n\t"
      "add.s      %[f6],        %[f6],        %[f3]                       \n\t"
      "sub.s      %[f3],        %[f0],        %[f5]                       \n\t"
      "add.s      %[f0],        %[f0],        %[f5]                       \n\t"
      "swc1       %[f8],        0(%[tmp_a])                               \n\t"
      "swc1       %[f6],        4(%[tmp_a])                               \n\t"
      "mul.s      %[f5],        %[f9],        %[f7]                       \n\t"
#if defined(MIPS32_R2_LE)
      "mul.s      %[f7],        %[f10],       %[f7]                       \n\t"
      "mul.s      %[f8],        %[f11],       %[f3]                       \n\t"
      "mul.s      %[f3],        %[f12],       %[f3]                       \n\t"
      "mul.s      %[f6],        %[f13],       %[f0]                       \n\t"
      "mul.s      %[f0],        %[f14],       %[f0]                       \n\t"
      "nmsub.s    %[f5],        %[f5],        %[f10],       %[f4]         \n\t"
      "madd.s     %[f7],        %[f7],        %[f9],        %[f4]         \n\t"
      "nmsub.s    %[f8],        %[f8],        %[f12],       %[f2]         \n\t"
      "madd.s     %[f3],        %[f3],        %[f11],       %[f2]         \n\t"
      "nmsub.s    %[f6],        %[f6],        %[f14],       %[f1]         \n\t"
      "madd.s     %[f0],        %[f0],        %[f13],       %[f1]         \n\t"
      "swc1       %[f5],        64(%[tmp_a])                              \n\t"
      "swc1       %[f7],        68(%[tmp_a])                              \n\t"
#else
      "mul.s      %[f8],        %[f10],       %[f4]                       \n\t"
      "mul.s      %[f4],        %[f9],        %[f4]                       \n\t"
      "mul.s      %[f7],        %[f10],       %[f7]                       \n\t"
      "mul.s      %[f6],        %[f11],       %[f3]                       \n\t"
      "mul.s      %[f3],        %[f12],       %[f3]                       \n\t"
      "sub.s      %[f5],        %[f5],        %[f8]                       \n\t"
      "mul.s      %[f8],        %[f12],       %[f2]                       \n\t"
      "mul.s      %[f2],        %[f11],       %[f2]                       \n\t"
      "add.s      %[f7],        %[f4],        %[f7]                       \n\t"
      "mul.s      %[f4],        %[f13],       %[f0]                       \n\t"
      "mul.s      %[f0],        %[f14],       %[f0]                       \n\t"
      "sub.s      %[f8],        %[f6],        %[f8]                       \n\t"
      "mul.s      %[f6],        %[f14],       %[f1]                       \n\t"
      "mul.s      %[f1],        %[f13],       %[f1]                       \n\t"
      "add.s      %[f3],        %[f2],        %[f3]                       \n\t"
      "swc1       %[f5],        64(%[tmp_a])                              \n\t"
      "swc1       %[f7],        68(%[tmp_a])                              \n\t"
      "sub.s      %[f6],        %[f4],        %[f6]                       \n\t"
      "add.s      %[f0],        %[f1],        %[f0]                       \n\t"
#endif
      "swc1       %[f8],        32(%[tmp_a])                              \n\t"
      "swc1       %[f3],        36(%[tmp_a])                              \n\t"
      "swc1       %[f6],        96(%[tmp_a])                              \n\t"
      "swc1       %[f0],        100(%[tmp_a])                             \n\t"
      "bgtz       %[count],     1b                                        \n\t"
      " addiu     %[tmp_a],     %[tmp_a],     8                           \n\t"
      ".set       pop                                                     \n\t"
      : [f0] "=&f"(f0), [f1] "=&f"(f1), [f2] "=&f"(f2), [f3] "=&f"(f3),
        [f4] "=&f"(f4), [f5] "=&f"(f5), [f6] "=&f"(f6), [f7] "=&f"(f7),
        [f8] "=&f"(f8), [tmp_a] "=&r"(tmp_a), [count] "=&r"(count)
      : [a] "r"(a), [f9] "f"(f9), [f10] "f"(f10), [f11] "f"(f11),
        [f12] "f"(f12), [f13] "f"(f13), [f14] "f"(f14)
      : "memory");
  f11 = rdft_w[6];
  f12 = rdft_w[7];
  f13 = rdft_wk3ri_second[2];
  f14 = rdft_wk3ri_second[3];
  __asm __volatile(
      ".set       push                                                       "
      "\n\t"
      ".set       noreorder                                                  "
      "\n\t"
      "addiu      %[tmp_a],       %[a],           384                        "
      "\n\t"
      "addiu      %[count],       $zero,          4                          "
      "\n\t"
      "1:                                                                     "
      "\n\t"
      "addiu      %[count],       %[count],       -1                         "
      "\n\t"
      "lwc1       %[f0],          0(%[tmp_a])                                "
      "\n\t"
      "lwc1       %[f1],          4(%[tmp_a])                                "
      "\n\t"
      "lwc1       %[f2],          32(%[tmp_a])                               "
      "\n\t"
      "lwc1       %[f3],          36(%[tmp_a])                               "
      "\n\t"
      "lwc1       %[f4],          64(%[tmp_a])                               "
      "\n\t"
      "lwc1       %[f5],          68(%[tmp_a])                               "
      "\n\t"
      "lwc1       %[f6],          96(%[tmp_a])                               "
      "\n\t"
      "lwc1       %[f7],          100(%[tmp_a])                              "
      "\n\t"
      "add.s      %[f8],          %[f0],          %[f2]                      "
      "\n\t"
      "sub.s      %[f0],          %[f0],          %[f2]                      "
      "\n\t"
      "add.s      %[f2],          %[f4],          %[f6]                      "
      "\n\t"
      "sub.s      %[f4],          %[f4],          %[f6]                      "
      "\n\t"
      "add.s      %[f6],          %[f1],          %[f3]                      "
      "\n\t"
      "sub.s      %[f1],          %[f1],          %[f3]                      "
      "\n\t"
      "add.s      %[f3],          %[f5],          %[f7]                      "
      "\n\t"
      "sub.s      %[f5],          %[f5],          %[f7]                      "
      "\n\t"
      "sub.s      %[f7],          %[f2],          %[f8]                      "
      "\n\t"
      "add.s      %[f2],          %[f2],          %[f8]                      "
      "\n\t"
      "add.s      %[f8],          %[f1],          %[f4]                      "
      "\n\t"
      "sub.s      %[f1],          %[f1],          %[f4]                      "
      "\n\t"
      "sub.s      %[f4],          %[f3],          %[f6]                      "
      "\n\t"
      "add.s      %[f3],          %[f3],          %[f6]                      "
      "\n\t"
      "sub.s      %[f6],          %[f0],          %[f5]                      "
      "\n\t"
      "add.s      %[f0],          %[f0],          %[f5]                      "
      "\n\t"
      "swc1       %[f2],          0(%[tmp_a])                                "
      "\n\t"
      "swc1       %[f3],          4(%[tmp_a])                                "
      "\n\t"
      "mul.s      %[f5],          %[f10],         %[f7]                      "
      "\n\t"
#if defined(MIPS32_R2_LE)
      "mul.s      %[f7],          %[f9],          %[f7]                      "
      "\n\t"
      "mul.s      %[f2],          %[f12],         %[f8]                      "
      "\n\t"
      "mul.s      %[f8],          %[f11],         %[f8]                      "
      "\n\t"
      "mul.s      %[f3],          %[f14],         %[f1]                      "
      "\n\t"
      "mul.s      %[f1],          %[f13],         %[f1]                      "
      "\n\t"
      "madd.s     %[f5],          %[f5],          %[f9],       %[f4]         "
      "\n\t"
      "msub.s     %[f7],          %[f7],          %[f10],      %[f4]         "
      "\n\t"
      "msub.s     %[f2],          %[f2],          %[f11],      %[f6]         "
      "\n\t"
      "madd.s     %[f8],          %[f8],          %[f12],      %[f6]         "
      "\n\t"
      "msub.s     %[f3],          %[f3],          %[f13],      %[f0]         "
      "\n\t"
      "madd.s     %[f1],          %[f1],          %[f14],      %[f0]         "
      "\n\t"
      "swc1       %[f5],          64(%[tmp_a])                               "
      "\n\t"
      "swc1       %[f7],          68(%[tmp_a])                               "
      "\n\t"
#else
      "mul.s      %[f2],          %[f9],          %[f4]                      "
      "\n\t"
      "mul.s      %[f4],          %[f10],         %[f4]                      "
      "\n\t"
      "mul.s      %[f7],          %[f9],          %[f7]                      "
      "\n\t"
      "mul.s      %[f3],          %[f11],         %[f6]                      "
      "\n\t"
      "mul.s      %[f6],          %[f12],         %[f6]                      "
      "\n\t"
      "add.s      %[f5],          %[f5],          %[f2]                      "
      "\n\t"
      "sub.s      %[f7],          %[f4],          %[f7]                      "
      "\n\t"
      "mul.s      %[f2],          %[f12],         %[f8]                      "
      "\n\t"
      "mul.s      %[f8],          %[f11],         %[f8]                      "
      "\n\t"
      "mul.s      %[f4],          %[f14],         %[f1]                      "
      "\n\t"
      "mul.s      %[f1],          %[f13],         %[f1]                      "
      "\n\t"
      "sub.s      %[f2],          %[f3],          %[f2]                      "
      "\n\t"
      "mul.s      %[f3],          %[f13],         %[f0]                      "
      "\n\t"
      "mul.s      %[f0],          %[f14],         %[f0]                      "
      "\n\t"
      "add.s      %[f8],          %[f8],          %[f6]                      "
      "\n\t"
      "swc1       %[f5],          64(%[tmp_a])                               "
      "\n\t"
      "swc1       %[f7],          68(%[tmp_a])                               "
      "\n\t"
      "sub.s      %[f3],          %[f3],          %[f4]                      "
      "\n\t"
      "add.s      %[f1],          %[f1],          %[f0]                      "
      "\n\t"
#endif
      "swc1       %[f2],          32(%[tmp_a])                               "
      "\n\t"
      "swc1       %[f8],          36(%[tmp_a])                               "
      "\n\t"
      "swc1       %[f3],          96(%[tmp_a])                               "
      "\n\t"
      "swc1       %[f1],          100(%[tmp_a])                              "
      "\n\t"
      "bgtz       %[count],       1b                                         "
      "\n\t"
      " addiu     %[tmp_a],       %[tmp_a],       8                          "
      "\n\t"
      ".set       pop                                                        "
      "\n\t"
      : [f0] "=&f"(f0), [f1] "=&f"(f1), [f2] "=&f"(f2), [f3] "=&f"(f3),
        [f4] "=&f"(f4), [f5] "=&f"(f5), [f6] "=&f"(f6), [f7] "=&f"(f7),
        [f8] "=&f"(f8), [tmp_a] "=&r"(tmp_a), [count] "=&r"(count)
      : [a] "r"(a), [f9] "f"(f9), [f10] "f"(f10), [f11] "f"(f11),
        [f12] "f"(f12), [f13] "f"(f13), [f14] "f"(f14)
      : "memory");
}

void cftfsub_128_mips(float* a) {
  float f0, f1, f2, f3, f4, f5, f6, f7, f8;
  int tmp_a, count;

  cft1st_128_mips(a);
  cftmdl_128_mips(a);

  __asm __volatile(
      ".set       push                                      \n\t"
      ".set       noreorder                                 \n\t"
      "addiu      %[tmp_a],       %[a],         0           \n\t"
      "addiu      %[count],       $zero,        16          \n\t"
      "1:                                                    \n\t"
      "addiu      %[count],       %[count],     -1          \n\t"
      "lwc1       %[f0],          0(%[tmp_a])               \n\t"
      "lwc1       %[f2],          128(%[tmp_a])             \n\t"
      "lwc1       %[f4],          256(%[tmp_a])             \n\t"
      "lwc1       %[f6],          384(%[tmp_a])             \n\t"
      "lwc1       %[f1],          4(%[tmp_a])               \n\t"
      "lwc1       %[f3],          132(%[tmp_a])             \n\t"
      "lwc1       %[f5],          260(%[tmp_a])             \n\t"
      "lwc1       %[f7],          388(%[tmp_a])             \n\t"
      "add.s      %[f8],          %[f0],        %[f2]       \n\t"
      "sub.s      %[f0],          %[f0],        %[f2]       \n\t"
      "add.s      %[f2],          %[f4],        %[f6]       \n\t"
      "sub.s      %[f4],          %[f4],        %[f6]       \n\t"
      "add.s      %[f6],          %[f1],        %[f3]       \n\t"
      "sub.s      %[f1],          %[f1],        %[f3]       \n\t"
      "add.s      %[f3],          %[f5],        %[f7]       \n\t"
      "sub.s      %[f5],          %[f5],        %[f7]       \n\t"
      "add.s      %[f7],          %[f8],        %[f2]       \n\t"
      "sub.s      %[f8],          %[f8],        %[f2]       \n\t"
      "add.s      %[f2],          %[f1],        %[f4]       \n\t"
      "sub.s      %[f1],          %[f1],        %[f4]       \n\t"
      "add.s      %[f4],          %[f6],        %[f3]       \n\t"
      "sub.s      %[f6],          %[f6],        %[f3]       \n\t"
      "sub.s      %[f3],          %[f0],        %[f5]       \n\t"
      "add.s      %[f0],          %[f0],        %[f5]       \n\t"
      "swc1       %[f7],          0(%[tmp_a])               \n\t"
      "swc1       %[f8],          256(%[tmp_a])             \n\t"
      "swc1       %[f2],          132(%[tmp_a])             \n\t"
      "swc1       %[f1],          388(%[tmp_a])             \n\t"
      "swc1       %[f4],          4(%[tmp_a])               \n\t"
      "swc1       %[f6],          260(%[tmp_a])             \n\t"
      "swc1       %[f3],          128(%[tmp_a])             \n\t"
      "swc1       %[f0],          384(%[tmp_a])             \n\t"
      "bgtz       %[count],       1b                        \n\t"
      " addiu     %[tmp_a],       %[tmp_a],   8             \n\t"
      ".set       pop                                       \n\t"
      : [f0] "=&f"(f0), [f1] "=&f"(f1), [f2] "=&f"(f2), [f3] "=&f"(f3),
        [f4] "=&f"(f4), [f5] "=&f"(f5), [f6] "=&f"(f6), [f7] "=&f"(f7),
        [f8] "=&f"(f8), [tmp_a] "=&r"(tmp_a), [count] "=&r"(count)
      : [a] "r"(a)
      : "memory");
}

void cftbsub_128_mips(float* a) {
  float f0, f1, f2, f3, f4, f5, f6, f7, f8;
  int tmp_a, count;

  cft1st_128_mips(a);
  cftmdl_128_mips(a);

  __asm __volatile(
      ".set       push                                        \n\t"
      ".set       noreorder                                   \n\t"
      "addiu      %[tmp_a],   %[a],           0               \n\t"
      "addiu      %[count],   $zero,          16              \n\t"
      "1:                                                      \n\t"
      "addiu      %[count],   %[count],       -1              \n\t"
      "lwc1       %[f0],      0(%[tmp_a])                     \n\t"
      "lwc1       %[f2],      128(%[tmp_a])                   \n\t"
      "lwc1       %[f4],      256(%[tmp_a])                   \n\t"
      "lwc1       %[f6],      384(%[tmp_a])                   \n\t"
      "lwc1       %[f1],      4(%[tmp_a])                     \n\t"
      "lwc1       %[f3],      132(%[tmp_a])                   \n\t"
      "lwc1       %[f5],      260(%[tmp_a])                   \n\t"
      "lwc1       %[f7],      388(%[tmp_a])                   \n\t"
      "add.s      %[f8],      %[f0],          %[f2]           \n\t"
      "sub.s      %[f0],      %[f0],          %[f2]           \n\t"
      "add.s      %[f2],      %[f4],          %[f6]           \n\t"
      "sub.s      %[f4],      %[f4],          %[f6]           \n\t"
      "add.s      %[f6],      %[f1],          %[f3]           \n\t"
      "sub.s      %[f1],      %[f3],          %[f1]           \n\t"
      "add.s      %[f3],      %[f5],          %[f7]           \n\t"
      "sub.s      %[f5],      %[f5],          %[f7]           \n\t"
      "add.s      %[f7],      %[f8],          %[f2]           \n\t"
      "sub.s      %[f8],      %[f8],          %[f2]           \n\t"
      "sub.s      %[f2],      %[f1],          %[f4]           \n\t"
      "add.s      %[f1],      %[f1],          %[f4]           \n\t"
      "add.s      %[f4],      %[f3],          %[f6]           \n\t"
      "sub.s      %[f6],      %[f3],          %[f6]           \n\t"
      "sub.s      %[f3],      %[f0],          %[f5]           \n\t"
      "add.s      %[f0],      %[f0],          %[f5]           \n\t"
      "neg.s      %[f4],      %[f4]                           \n\t"
      "swc1       %[f7],      0(%[tmp_a])                     \n\t"
      "swc1       %[f8],      256(%[tmp_a])                   \n\t"
      "swc1       %[f2],      132(%[tmp_a])                   \n\t"
      "swc1       %[f1],      388(%[tmp_a])                   \n\t"
      "swc1       %[f6],      260(%[tmp_a])                   \n\t"
      "swc1       %[f3],      128(%[tmp_a])                   \n\t"
      "swc1       %[f0],      384(%[tmp_a])                   \n\t"
      "swc1       %[f4],       4(%[tmp_a])                     \n\t"
      "bgtz       %[count],   1b                              \n\t"
      " addiu     %[tmp_a],   %[tmp_a],       8               \n\t"
      ".set       pop                                         \n\t"
      : [f0] "=&f"(f0), [f1] "=&f"(f1), [f2] "=&f"(f2), [f3] "=&f"(f3),
        [f4] "=&f"(f4), [f5] "=&f"(f5), [f6] "=&f"(f6), [f7] "=&f"(f7),
        [f8] "=&f"(f8), [tmp_a] "=&r"(tmp_a), [count] "=&r"(count)
      : [a] "r"(a)
      : "memory");
}

void rftfsub_128_mips(float* a) {
  const float* c = rdft_w + 32;
  const float f0 = 0.5f;
  float* a1 = &a[2];
  float* a2 = &a[126];
  const float* c1 = &c[1];
  const float* c2 = &c[31];
  float f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15;
  int count;

  __asm __volatile(
      ".set      push                                             \n\t"
      ".set      noreorder                                        \n\t"
      "lwc1      %[f6],       0(%[c2])                            \n\t"
      "lwc1      %[f1],       0(%[a1])                            \n\t"
      "lwc1      %[f2],       0(%[a2])                            \n\t"
      "lwc1      %[f3],       4(%[a1])                            \n\t"
      "lwc1      %[f4],       4(%[a2])                            \n\t"
      "lwc1      %[f5],       0(%[c1])                            \n\t"
      "sub.s     %[f6],       %[f0],        %[f6]                 \n\t"
      "sub.s     %[f7],       %[f1],        %[f2]                 \n\t"
      "add.s     %[f8],       %[f3],        %[f4]                 \n\t"
      "addiu     %[count],    $zero,        15                    \n\t"
      "mul.s     %[f9],       %[f6],        %[f7]                 \n\t"
      "mul.s     %[f6],       %[f6],        %[f8]                 \n\t"
#if !defined(MIPS32_R2_LE)
      "mul.s     %[f8],       %[f5],        %[f8]                 \n\t"
      "mul.s     %[f5],       %[f5],        %[f7]                 \n\t"
      "sub.s     %[f9],       %[f9],        %[f8]                 \n\t"
      "add.s     %[f6],       %[f6],        %[f5]                 \n\t"
#else
      "nmsub.s   %[f9],       %[f9],        %[f5],      %[f8]     \n\t"
      "madd.s    %[f6],       %[f6],        %[f5],      %[f7]     \n\t"
#endif
      "sub.s     %[f1],       %[f1],        %[f9]                 \n\t"
      "add.s     %[f2],       %[f2],        %[f9]                 \n\t"
      "sub.s     %[f3],       %[f3],        %[f6]                 \n\t"
      "sub.s     %[f4],       %[f4],        %[f6]                 \n\t"
      "swc1      %[f1],       0(%[a1])                            \n\t"
      "swc1      %[f2],       0(%[a2])                            \n\t"
      "swc1      %[f3],       4(%[a1])                            \n\t"
      "swc1      %[f4],       4(%[a2])                            \n\t"
      "addiu     %[a1],       %[a1],        8                     \n\t"
      "addiu     %[a2],       %[a2],        -8                    \n\t"
      "addiu     %[c1],       %[c1],        4                     \n\t"
      "addiu     %[c2],       %[c2],        -4                    \n\t"
      "1:                                                          \n\t"
      "lwc1      %[f6],       0(%[c2])                            \n\t"
      "lwc1      %[f1],       0(%[a1])                            \n\t"
      "lwc1      %[f2],       0(%[a2])                            \n\t"
      "lwc1      %[f3],       4(%[a1])                            \n\t"
      "lwc1      %[f4],       4(%[a2])                            \n\t"
      "lwc1      %[f5],       0(%[c1])                            \n\t"
      "sub.s     %[f6],       %[f0],        %[f6]                 \n\t"
      "sub.s     %[f7],       %[f1],        %[f2]                 \n\t"
      "add.s     %[f8],       %[f3],        %[f4]                 \n\t"
      "lwc1      %[f10],      -4(%[c2])                           \n\t"
      "lwc1      %[f11],      8(%[a1])                            \n\t"
      "lwc1      %[f12],      -8(%[a2])                           \n\t"
      "mul.s     %[f9],       %[f6],        %[f7]                 \n\t"
      "mul.s     %[f6],       %[f6],        %[f8]                 \n\t"
#if !defined(MIPS32_R2_LE)
      "mul.s     %[f8],       %[f5],        %[f8]                 \n\t"
      "mul.s     %[f5],       %[f5],        %[f7]                 \n\t"
      "lwc1      %[f13],      12(%[a1])                           \n\t"
      "lwc1      %[f14],      -4(%[a2])                           \n\t"
      "lwc1      %[f15],      4(%[c1])                            \n\t"
      "sub.s     %[f9],       %[f9],        %[f8]                 \n\t"
      "add.s     %[f6],       %[f6],        %[f5]                 \n\t"
#else
      "lwc1      %[f13],      12(%[a1])                           \n\t"
      "lwc1      %[f14],      -4(%[a2])                           \n\t"
      "lwc1      %[f15],      4(%[c1])                            \n\t"
      "nmsub.s   %[f9],       %[f9],        %[f5],      %[f8]     \n\t"
      "madd.s    %[f6],       %[f6],        %[f5],      %[f7]     \n\t"
#endif
      "sub.s     %[f10],      %[f0],        %[f10]                \n\t"
      "sub.s     %[f5],       %[f11],       %[f12]                \n\t"
      "add.s     %[f7],       %[f13],       %[f14]                \n\t"
      "sub.s     %[f1],       %[f1],        %[f9]                 \n\t"
      "add.s     %[f2],       %[f2],        %[f9]                 \n\t"
      "sub.s     %[f3],       %[f3],        %[f6]                 \n\t"
      "mul.s     %[f8],       %[f10],       %[f5]                 \n\t"
      "mul.s     %[f10],      %[f10],       %[f7]                 \n\t"
#if !defined(MIPS32_R2_LE)
      "mul.s     %[f9],       %[f15],       %[f7]                 \n\t"
      "mul.s     %[f15],      %[f15],       %[f5]                 \n\t"
      "sub.s     %[f4],       %[f4],        %[f6]                 \n\t"
      "swc1      %[f1],       0(%[a1])                            \n\t"
      "swc1      %[f2],       0(%[a2])                            \n\t"
      "sub.s     %[f8],       %[f8],        %[f9]                 \n\t"
      "add.s     %[f10],      %[f10],       %[f15]                \n\t"
#else
      "swc1      %[f1],       0(%[a1])                            \n\t"
      "swc1      %[f2],       0(%[a2])                            \n\t"
      "sub.s     %[f4],       %[f4],        %[f6]                 \n\t"
      "nmsub.s   %[f8],       %[f8],        %[f15],     %[f7]     \n\t"
      "madd.s    %[f10],      %[f10],       %[f15],     %[f5]     \n\t"
#endif
      "swc1      %[f3],       4(%[a1])                            \n\t"
      "swc1      %[f4],       4(%[a2])                            \n\t"
      "sub.s     %[f11],      %[f11],       %[f8]                 \n\t"
      "add.s     %[f12],      %[f12],       %[f8]                 \n\t"
      "sub.s     %[f13],      %[f13],       %[f10]                \n\t"
      "sub.s     %[f14],      %[f14],       %[f10]                \n\t"
      "addiu     %[c2],       %[c2],        -8                    \n\t"
      "addiu     %[c1],       %[c1],        8                     \n\t"
      "swc1      %[f11],      8(%[a1])                            \n\t"
      "swc1      %[f12],      -8(%[a2])                           \n\t"
      "swc1      %[f13],      12(%[a1])                           \n\t"
      "swc1      %[f14],      -4(%[a2])                           \n\t"
      "addiu     %[a1],       %[a1],        16                    \n\t"
      "addiu     %[count],    %[count],     -1                    \n\t"
      "bgtz      %[count],    1b                                  \n\t"
      " addiu    %[a2],       %[a2],        -16                   \n\t"
      ".set      pop                                              \n\t"
      : [a1] "+r"(a1), [a2] "+r"(a2), [c1] "+r"(c1), [c2] "+r"(c2),
        [f1] "=&f"(f1), [f2] "=&f"(f2), [f3] "=&f"(f3), [f4] "=&f"(f4),
        [f5] "=&f"(f5), [f6] "=&f"(f6), [f7] "=&f"(f7), [f8] "=&f"(f8),
        [f9] "=&f"(f9), [f10] "=&f"(f10), [f11] "=&f"(f11), [f12] "=&f"(f12),
        [f13] "=&f"(f13), [f14] "=&f"(f14), [f15] "=&f"(f15),
        [count] "=&r"(count)
      : [f0] "f"(f0)
      : "memory");
}

void rftbsub_128_mips(float* a) {
  const float* c = rdft_w + 32;
  const float f0 = 0.5f;
  float* a1 = &a[2];
  float* a2 = &a[126];
  const float* c1 = &c[1];
  const float* c2 = &c[31];
  float f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15;
  int count;

  a[1] = -a[1];
  a[65] = -a[65];

  __asm __volatile(
      ".set      push                                             \n\t"
      ".set      noreorder                                        \n\t"
      "lwc1      %[f6],       0(%[c2])                            \n\t"
      "lwc1      %[f1],       0(%[a1])                            \n\t"
      "lwc1      %[f2],       0(%[a2])                            \n\t"
      "lwc1      %[f3],       4(%[a1])                            \n\t"
      "lwc1      %[f4],       4(%[a2])                            \n\t"
      "lwc1      %[f5],       0(%[c1])                            \n\t"
      "sub.s     %[f6],       %[f0],        %[f6]                 \n\t"
      "sub.s     %[f7],       %[f1],        %[f2]                 \n\t"
      "add.s     %[f8],       %[f3],        %[f4]                 \n\t"
      "addiu     %[count],    $zero,        15                    \n\t"
      "mul.s     %[f9],       %[f6],        %[f7]                 \n\t"
      "mul.s     %[f6],       %[f6],        %[f8]                 \n\t"
#if !defined(MIPS32_R2_LE)
      "mul.s     %[f8],       %[f5],        %[f8]                 \n\t"
      "mul.s     %[f5],       %[f5],        %[f7]                 \n\t"
      "add.s     %[f9],       %[f9],        %[f8]                 \n\t"
      "sub.s     %[f6],       %[f6],        %[f5]                 \n\t"
#else
      "madd.s    %[f9],       %[f9],        %[f5],      %[f8]     \n\t"
      "nmsub.s   %[f6],       %[f6],        %[f5],      %[f7]     \n\t"
#endif
      "sub.s     %[f1],       %[f1],        %[f9]                 \n\t"
      "add.s     %[f2],       %[f2],        %[f9]                 \n\t"
      "sub.s     %[f3],       %[f6],        %[f3]                 \n\t"
      "sub.s     %[f4],       %[f6],        %[f4]                 \n\t"
      "swc1      %[f1],       0(%[a1])                            \n\t"
      "swc1      %[f2],       0(%[a2])                            \n\t"
      "swc1      %[f3],       4(%[a1])                            \n\t"
      "swc1      %[f4],       4(%[a2])                            \n\t"
      "addiu     %[a1],       %[a1],        8                     \n\t"
      "addiu     %[a2],       %[a2],        -8                    \n\t"
      "addiu     %[c1],       %[c1],        4                     \n\t"
      "addiu     %[c2],       %[c2],        -4                    \n\t"
      "1:                                                          \n\t"
      "lwc1      %[f6],       0(%[c2])                            \n\t"
      "lwc1      %[f1],       0(%[a1])                            \n\t"
      "lwc1      %[f2],       0(%[a2])                            \n\t"
      "lwc1      %[f3],       4(%[a1])                            \n\t"
      "lwc1      %[f4],       4(%[a2])                            \n\t"
      "lwc1      %[f5],       0(%[c1])                            \n\t"
      "sub.s     %[f6],       %[f0],        %[f6]                 \n\t"
      "sub.s     %[f7],       %[f1],        %[f2]                 \n\t"
      "add.s     %[f8],       %[f3],        %[f4]                 \n\t"
      "lwc1      %[f10],      -4(%[c2])                           \n\t"
      "lwc1      %[f11],      8(%[a1])                            \n\t"
      "lwc1      %[f12],      -8(%[a2])                           \n\t"
      "mul.s     %[f9],       %[f6],        %[f7]                 \n\t"
      "mul.s     %[f6],       %[f6],        %[f8]                 \n\t"
#if !defined(MIPS32_R2_LE)
      "mul.s     %[f8],       %[f5],        %[f8]                 \n\t"
      "mul.s     %[f5],       %[f5],        %[f7]                 \n\t"
      "lwc1      %[f13],      12(%[a1])                           \n\t"
      "lwc1      %[f14],      -4(%[a2])                           \n\t"
      "lwc1      %[f15],      4(%[c1])                            \n\t"
      "add.s     %[f9],       %[f9],        %[f8]                 \n\t"
      "sub.s     %[f6],       %[f6],        %[f5]                 \n\t"
#else
      "lwc1      %[f13],      12(%[a1])                           \n\t"
      "lwc1      %[f14],      -4(%[a2])                           \n\t"
      "lwc1      %[f15],      4(%[c1])                            \n\t"
      "madd.s    %[f9],       %[f9],        %[f5],      %[f8]     \n\t"
      "nmsub.s   %[f6],       %[f6],        %[f5],      %[f7]     \n\t"
#endif
      "sub.s     %[f10],      %[f0],        %[f10]                \n\t"
      "sub.s     %[f5],       %[f11],       %[f12]                \n\t"
      "add.s     %[f7],       %[f13],       %[f14]                \n\t"
      "sub.s     %[f1],       %[f1],        %[f9]                 \n\t"
      "add.s     %[f2],       %[f2],        %[f9]                 \n\t"
      "sub.s     %[f3],       %[f6],        %[f3]                 \n\t"
      "mul.s     %[f8],       %[f10],       %[f5]                 \n\t"
      "mul.s     %[f10],      %[f10],       %[f7]                 \n\t"
#if !defined(MIPS32_R2_LE)
      "mul.s     %[f9],       %[f15],       %[f7]                 \n\t"
      "mul.s     %[f15],      %[f15],       %[f5]                 \n\t"
      "sub.s     %[f4],       %[f6],        %[f4]                 \n\t"
      "swc1      %[f1],       0(%[a1])                            \n\t"
      "swc1      %[f2],       0(%[a2])                            \n\t"
      "add.s     %[f8],       %[f8],        %[f9]                 \n\t"
      "sub.s     %[f10],      %[f10],       %[f15]                \n\t"
#else
      "swc1      %[f1],       0(%[a1])                            \n\t"
      "swc1      %[f2],       0(%[a2])                            \n\t"
      "sub.s     %[f4],       %[f6],        %[f4]                 \n\t"
      "madd.s    %[f8],       %[f8],        %[f15],     %[f7]     \n\t"
      "nmsub.s   %[f10],      %[f10],       %[f15],     %[f5]     \n\t"
#endif
      "swc1      %[f3],       4(%[a1])                            \n\t"
      "swc1      %[f4],       4(%[a2])                            \n\t"
      "sub.s     %[f11],      %[f11],       %[f8]                 \n\t"
      "add.s     %[f12],      %[f12],       %[f8]                 \n\t"
      "sub.s     %[f13],      %[f10],       %[f13]                \n\t"
      "sub.s     %[f14],      %[f10],       %[f14]                \n\t"
      "addiu     %[c2],       %[c2],        -8                    \n\t"
      "addiu     %[c1],       %[c1],        8                     \n\t"
      "swc1      %[f11],      8(%[a1])                            \n\t"
      "swc1      %[f12],      -8(%[a2])                           \n\t"
      "swc1      %[f13],      12(%[a1])                           \n\t"
      "swc1      %[f14],      -4(%[a2])                           \n\t"
      "addiu     %[a1],       %[a1],        16                    \n\t"
      "addiu     %[count],    %[count],     -1                    \n\t"
      "bgtz      %[count],    1b                                  \n\t"
      " addiu    %[a2],       %[a2],        -16                   \n\t"
      ".set      pop                                              \n\t"
      : [a1] "+r"(a1), [a2] "+r"(a2), [c1] "+r"(c1), [c2] "+r"(c2),
        [f1] "=&f"(f1), [f2] "=&f"(f2), [f3] "=&f"(f3), [f4] "=&f"(f4),
        [f5] "=&f"(f5), [f6] "=&f"(f6), [f7] "=&f"(f7), [f8] "=&f"(f8),
        [f9] "=&f"(f9), [f10] "=&f"(f10), [f11] "=&f"(f11), [f12] "=&f"(f12),
        [f13] "=&f"(f13), [f14] "=&f"(f14), [f15] "=&f"(f15),
        [count] "=&r"(count)
      : [f0] "f"(f0)
      : "memory");
}
#endif

}  // namespace webrtc
