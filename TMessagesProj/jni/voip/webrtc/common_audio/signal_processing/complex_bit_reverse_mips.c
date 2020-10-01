/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */


#include "common_audio/signal_processing/include/signal_processing_library.h"

static int16_t coefTable_7[] = {
    4, 256,   8, 128,  12, 384,  16,  64,
   20, 320,  24, 192,  28, 448,  36, 288,
   40, 160,  44, 416,  48,  96,  52, 352,
   56, 224,  60, 480,  68, 272,  72, 144,
   76, 400,  84, 336,  88, 208,  92, 464,
  100, 304, 104, 176, 108, 432, 116, 368,
  120, 240, 124, 496, 132, 264, 140, 392,
  148, 328, 152, 200, 156, 456, 164, 296,
  172, 424, 180, 360, 184, 232, 188, 488,
  196, 280, 204, 408, 212, 344, 220, 472,
  228, 312, 236, 440, 244, 376, 252, 504,
  268, 388, 276, 324, 284, 452, 300, 420,
  308, 356, 316, 484, 332, 404, 348, 468,
  364, 436, 380, 500, 412, 460, 444, 492
};

static int16_t coefTable_8[] = {
    4,  512,    8,  256,   12,  768,   16,  128,
   20,  640,   24,  384,   28,  896,   32,   64,
   36,  576,   40,  320,   44,  832,   48,  192,
   52,  704,   56,  448,   60,  960,   68,  544,
   72,  288,   76,  800,   80,  160,   84,  672,
   88,  416,   92,  928,  100,  608,  104,  352,
  108,  864,  112,  224,  116,  736,  120,  480,
  124,  992,  132,  528,  136,  272,  140,  784,
  148,  656,  152,  400,  156,  912,  164,  592,
  168,  336,  172,  848,  176,  208,  180,  720,
  184,  464,  188,  976,  196,  560,  200,  304,
  204,  816,  212,  688,  216,  432,  220,  944,
  228,  624,  232,  368,  236,  880,  244,  752,
  248,  496,  252, 1008,  260,  520,  268,  776,
  276,  648,  280,  392,  284,  904,  292,  584,
  296,  328,  300,  840,  308,  712,  312,  456,
  316,  968,  324,  552,  332,  808,  340,  680,
  344,  424,  348,  936,  356,  616,  364,  872,
  372,  744,  376,  488,  380, 1000,  388,  536,
  396,  792,  404,  664,  412,  920,  420,  600,
  428,  856,  436,  728,  440,  472,  444,  984,
  452,  568,  460,  824,  468,  696,  476,  952,
  484,  632,  492,  888,  500,  760,  508, 1016,
  524,  772,  532,  644,  540,  900,  548,  580,
  556,  836,  564,  708,  572,  964,  588,  804,
  596,  676,  604,  932,  620,  868,  628,  740,
  636,  996,  652,  788,  668,  916,  684,  852,
  692,  724,  700,  980,  716,  820,  732,  948,
  748,  884,  764, 1012,  796,  908,  812,  844,
  828,  972,  860,  940,  892, 1004,  956,  988
};

void WebRtcSpl_ComplexBitReverse(int16_t frfi[], int stages) {
  int l;
  int16_t tr, ti;
  int32_t tmp1, tmp2, tmp3, tmp4;
  int32_t* ptr_i;
  int32_t* ptr_j;

  if (stages == 8) {
    int16_t* pcoeftable_8 = coefTable_8;

    __asm __volatile (
      ".set         push                                             \n\t"
      ".set         noreorder                                        \n\t"
      "addiu        %[l],            $zero,               120        \n\t"
     "1:                                                             \n\t"
      "addiu        %[l],            %[l],                -4         \n\t"
      "lh           %[tr],           0(%[pcoeftable_8])              \n\t"
      "lh           %[ti],           2(%[pcoeftable_8])              \n\t"
      "lh           %[tmp3],         4(%[pcoeftable_8])              \n\t"
      "lh           %[tmp4],         6(%[pcoeftable_8])              \n\t"
      "addu         %[ptr_i],        %[frfi],             %[tr]      \n\t"
      "addu         %[ptr_j],        %[frfi],             %[ti]      \n\t"
      "addu         %[tr],           %[frfi],             %[tmp3]    \n\t"
      "addu         %[ti],           %[frfi],             %[tmp4]    \n\t"
      "ulw          %[tmp1],         0(%[ptr_i])                     \n\t"
      "ulw          %[tmp2],         0(%[ptr_j])                     \n\t"
      "ulw          %[tmp3],         0(%[tr])                        \n\t"
      "ulw          %[tmp4],         0(%[ti])                        \n\t"
      "usw          %[tmp1],         0(%[ptr_j])                     \n\t"
      "usw          %[tmp2],         0(%[ptr_i])                     \n\t"
      "usw          %[tmp4],         0(%[tr])                        \n\t"
      "usw          %[tmp3],         0(%[ti])                        \n\t"
      "lh           %[tmp1],         8(%[pcoeftable_8])              \n\t"
      "lh           %[tmp2],         10(%[pcoeftable_8])             \n\t"
      "lh           %[tr],           12(%[pcoeftable_8])             \n\t"
      "lh           %[ti],           14(%[pcoeftable_8])             \n\t"
      "addu         %[ptr_i],        %[frfi],             %[tmp1]    \n\t"
      "addu         %[ptr_j],        %[frfi],             %[tmp2]    \n\t"
      "addu         %[tr],           %[frfi],             %[tr]      \n\t"
      "addu         %[ti],           %[frfi],             %[ti]      \n\t"
      "ulw          %[tmp1],         0(%[ptr_i])                     \n\t"
      "ulw          %[tmp2],         0(%[ptr_j])                     \n\t"
      "ulw          %[tmp3],         0(%[tr])                        \n\t"
      "ulw          %[tmp4],         0(%[ti])                        \n\t"
      "usw          %[tmp1],         0(%[ptr_j])                     \n\t"
      "usw          %[tmp2],         0(%[ptr_i])                     \n\t"
      "usw          %[tmp4],         0(%[tr])                        \n\t"
      "usw          %[tmp3],         0(%[ti])                        \n\t"
      "bgtz         %[l],            1b                              \n\t"
      " addiu       %[pcoeftable_8], %[pcoeftable_8],     16         \n\t"
      ".set         pop                                              \n\t"

      : [tmp1] "=&r" (tmp1), [tmp2] "=&r" (tmp2), [ptr_i] "=&r" (ptr_i),
        [ptr_j] "=&r" (ptr_j), [tr] "=&r" (tr), [l] "=&r" (l),
        [tmp3] "=&r" (tmp3), [pcoeftable_8] "+r" (pcoeftable_8),
        [ti] "=&r" (ti), [tmp4] "=&r" (tmp4)
      : [frfi] "r" (frfi)
      : "memory"
    );
  } else if (stages == 7) {
    int16_t* pcoeftable_7 = coefTable_7;

    __asm __volatile (
      ".set push                                                     \n\t"
      ".set noreorder                                                \n\t"
      "addiu        %[l],            $zero,               56         \n\t"
     "1:                                                             \n\t"
      "addiu        %[l],            %[l],                -4         \n\t"
      "lh           %[tr],           0(%[pcoeftable_7])              \n\t"
      "lh           %[ti],           2(%[pcoeftable_7])              \n\t"
      "lh           %[tmp3],         4(%[pcoeftable_7])              \n\t"
      "lh           %[tmp4],         6(%[pcoeftable_7])              \n\t"
      "addu         %[ptr_i],        %[frfi],             %[tr]      \n\t"
      "addu         %[ptr_j],        %[frfi],             %[ti]      \n\t"
      "addu         %[tr],           %[frfi],             %[tmp3]    \n\t"
      "addu         %[ti],           %[frfi],             %[tmp4]    \n\t"
      "ulw          %[tmp1],         0(%[ptr_i])                     \n\t"
      "ulw          %[tmp2],         0(%[ptr_j])                     \n\t"
      "ulw          %[tmp3],         0(%[tr])                        \n\t"
      "ulw          %[tmp4],         0(%[ti])                        \n\t"
      "usw          %[tmp1],         0(%[ptr_j])                     \n\t"
      "usw          %[tmp2],         0(%[ptr_i])                     \n\t"
      "usw          %[tmp4],         0(%[tr])                        \n\t"
      "usw          %[tmp3],         0(%[ti])                        \n\t"
      "lh           %[tmp1],         8(%[pcoeftable_7])              \n\t"
      "lh           %[tmp2],         10(%[pcoeftable_7])             \n\t"
      "lh           %[tr],           12(%[pcoeftable_7])             \n\t"
      "lh           %[ti],           14(%[pcoeftable_7])             \n\t"
      "addu         %[ptr_i],        %[frfi],             %[tmp1]    \n\t"
      "addu         %[ptr_j],        %[frfi],             %[tmp2]    \n\t"
      "addu         %[tr],           %[frfi],             %[tr]      \n\t"
      "addu         %[ti],           %[frfi],             %[ti]      \n\t"
      "ulw          %[tmp1],         0(%[ptr_i])                     \n\t"
      "ulw          %[tmp2],         0(%[ptr_j])                     \n\t"
      "ulw          %[tmp3],         0(%[tr])                        \n\t"
      "ulw          %[tmp4],         0(%[ti])                        \n\t"
      "usw          %[tmp1],         0(%[ptr_j])                     \n\t"
      "usw          %[tmp2],         0(%[ptr_i])                     \n\t"
      "usw          %[tmp4],         0(%[tr])                        \n\t"
      "usw          %[tmp3],         0(%[ti])                        \n\t"
      "bgtz         %[l],            1b                              \n\t"
      " addiu       %[pcoeftable_7], %[pcoeftable_7],     16         \n\t"
      ".set pop                                                      \n\t"

      : [tmp1] "=&r" (tmp1), [tmp2] "=&r" (tmp2), [ptr_i] "=&r" (ptr_i),
        [ptr_j] "=&r" (ptr_j), [ti] "=&r" (ti), [tr] "=&r" (tr),
        [l] "=&r" (l), [pcoeftable_7] "+r" (pcoeftable_7),
        [tmp3] "=&r" (tmp3), [tmp4] "=&r" (tmp4)
      : [frfi] "r" (frfi)
      : "memory"
    );
  }
}
