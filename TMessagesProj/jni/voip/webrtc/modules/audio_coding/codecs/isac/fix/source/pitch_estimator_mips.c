/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/isac/fix/source/pitch_estimator.h"
#include "common_audio/signal_processing/include/signal_processing_library.h"
#include "rtc_base/compile_assert_c.h"

extern int32_t WebRtcIsacfix_Log2Q8(uint32_t x);

void WebRtcIsacfix_PCorr2Q32(const int16_t* in, int32_t* logcorQ8) {
  int16_t scaling,n,k;
  int32_t ysum32,csum32, lys, lcs;
  const int32_t oneQ8 = 1 << 8;  // 1.00 in Q8
  const int16_t* x;
  const int16_t* inptr;

  x = in + PITCH_MAX_LAG / 2 + 2;
  scaling = WebRtcSpl_GetScalingSquare((int16_t*)in,
                                       PITCH_CORR_LEN2,
                                       PITCH_CORR_LEN2);
  ysum32 = 1;
  csum32 = 0;
  x = in + PITCH_MAX_LAG / 2 + 2;
  {
    const int16_t* tmp_x = x;
    const int16_t* tmp_in = in;
    int32_t tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, tmp7, tmp8;
    n = PITCH_CORR_LEN2;
    RTC_COMPILE_ASSERT(PITCH_CORR_LEN2 % 4 == 0);
    __asm __volatile (
      ".set       push                                          \n\t"
      ".set       noreorder                                     \n\t"
     "1:                                                        \n\t"
      "lh         %[tmp1],       0(%[tmp_in])                   \n\t"
      "lh         %[tmp2],       2(%[tmp_in])                   \n\t"
      "lh         %[tmp3],       4(%[tmp_in])                   \n\t"
      "lh         %[tmp4],       6(%[tmp_in])                   \n\t"
      "lh         %[tmp5],       0(%[tmp_x])                    \n\t"
      "lh         %[tmp6],       2(%[tmp_x])                    \n\t"
      "lh         %[tmp7],       4(%[tmp_x])                    \n\t"
      "lh         %[tmp8],       6(%[tmp_x])                    \n\t"
      "mul        %[tmp5],       %[tmp1],        %[tmp5]        \n\t"
      "mul        %[tmp1],       %[tmp1],        %[tmp1]        \n\t"
      "mul        %[tmp6],       %[tmp2],        %[tmp6]        \n\t"
      "mul        %[tmp2],       %[tmp2],        %[tmp2]        \n\t"
      "mul        %[tmp7],       %[tmp3],        %[tmp7]        \n\t"
      "mul        %[tmp3],       %[tmp3],        %[tmp3]        \n\t"
      "mul        %[tmp8],       %[tmp4],        %[tmp8]        \n\t"
      "mul        %[tmp4],       %[tmp4],        %[tmp4]        \n\t"
      "addiu      %[n],          %[n],           -4             \n\t"
      "srav       %[tmp5],       %[tmp5],        %[scaling]     \n\t"
      "srav       %[tmp1],       %[tmp1],        %[scaling]     \n\t"
      "srav       %[tmp6],       %[tmp6],        %[scaling]     \n\t"
      "srav       %[tmp2],       %[tmp2],        %[scaling]     \n\t"
      "srav       %[tmp7],       %[tmp7],        %[scaling]     \n\t"
      "srav       %[tmp3],       %[tmp3],        %[scaling]     \n\t"
      "srav       %[tmp8],       %[tmp8],        %[scaling]     \n\t"
      "srav       %[tmp4],       %[tmp4],        %[scaling]     \n\t"
      "addu       %[ysum32],     %[ysum32],      %[tmp1]        \n\t"
      "addu       %[csum32],     %[csum32],      %[tmp5]        \n\t"
      "addu       %[ysum32],     %[ysum32],      %[tmp2]        \n\t"
      "addu       %[csum32],     %[csum32],      %[tmp6]        \n\t"
      "addu       %[ysum32],     %[ysum32],      %[tmp3]        \n\t"
      "addu       %[csum32],     %[csum32],      %[tmp7]        \n\t"
      "addu       %[ysum32],     %[ysum32],      %[tmp4]        \n\t"
      "addu       %[csum32],     %[csum32],      %[tmp8]        \n\t"
      "addiu      %[tmp_in],     %[tmp_in],      8              \n\t"
      "bgtz       %[n],          1b                             \n\t"
      " addiu     %[tmp_x],      %[tmp_x],       8              \n\t"
      ".set       pop                                           \n\t"
      : [tmp1] "=&r" (tmp1), [tmp2] "=&r" (tmp2), [tmp3] "=&r" (tmp3),
        [tmp4] "=&r" (tmp4), [tmp5] "=&r" (tmp5), [tmp6] "=&r" (tmp6),
        [tmp7] "=&r" (tmp7), [tmp8] "=&r" (tmp8), [tmp_in] "+r" (tmp_in),
        [ysum32] "+r" (ysum32), [tmp_x] "+r" (tmp_x), [csum32] "+r" (csum32),
        [n] "+r" (n)
      : [scaling] "r" (scaling)
      : "memory", "hi", "lo"
    );
  }
  logcorQ8 += PITCH_LAG_SPAN2 - 1;
  lys = WebRtcIsacfix_Log2Q8((uint32_t)ysum32) >> 1; // Q8, sqrt(ysum)
  if (csum32 > 0) {
    lcs = WebRtcIsacfix_Log2Q8((uint32_t)csum32);  // 2log(csum) in Q8
    if (lcs > (lys + oneQ8)) {  // csum/sqrt(ysum) > 2 in Q8
      *logcorQ8 = lcs - lys;  // log2(csum/sqrt(ysum))
    } else {
      *logcorQ8 = oneQ8;  // 1.00
    }
  } else {
    *logcorQ8 = 0;
  }

  for (k = 1; k < PITCH_LAG_SPAN2; k++) {
    inptr = &in[k];
    const int16_t* tmp_in1 = &in[k - 1];
    const int16_t* tmp_in2 = &in[PITCH_CORR_LEN2 + k - 1];
    const int16_t* tmp_x = x;
    int32_t tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, tmp7, tmp8;
    n = PITCH_CORR_LEN2;
    csum32 = 0;
    __asm __volatile (
      ".set       push                                             \n\t"
      ".set       noreorder                                        \n\t"
      "lh         %[tmp1],        0(%[tmp_in1])                    \n\t"
      "lh         %[tmp2],        0(%[tmp_in2])                    \n\t"
      "mul        %[tmp1],        %[tmp1],         %[tmp1]         \n\t"
      "mul        %[tmp2],        %[tmp2],         %[tmp2]         \n\t"
      "srav       %[tmp1],        %[tmp1],         %[scaling]      \n\t"
      "srav       %[tmp2],        %[tmp2],         %[scaling]      \n\t"
      "subu       %[ysum32],      %[ysum32],       %[tmp1]         \n\t"
      "bnez       %[scaling],     2f                               \n\t"
      " addu      %[ysum32],      %[ysum32],       %[tmp2]         \n\t"
     "1:                                                           \n\t"
      "lh         %[tmp1],        0(%[inptr])                      \n\t"
      "lh         %[tmp2],        0(%[tmp_x])                      \n\t"
      "lh         %[tmp3],        2(%[inptr])                      \n\t"
      "lh         %[tmp4],        2(%[tmp_x])                      \n\t"
      "lh         %[tmp5],        4(%[inptr])                      \n\t"
      "lh         %[tmp6],        4(%[tmp_x])                      \n\t"
      "lh         %[tmp7],        6(%[inptr])                      \n\t"
      "lh         %[tmp8],        6(%[tmp_x])                      \n\t"
      "mul        %[tmp1],        %[tmp1],         %[tmp2]         \n\t"
      "mul        %[tmp2],        %[tmp3],         %[tmp4]         \n\t"
      "mul        %[tmp3],        %[tmp5],         %[tmp6]         \n\t"
      "mul        %[tmp4],        %[tmp7],         %[tmp8]         \n\t"
      "addiu      %[n],           %[n],            -4              \n\t"
      "addiu      %[inptr],       %[inptr],        8               \n\t"
      "addiu      %[tmp_x],       %[tmp_x],        8               \n\t"
      "addu       %[csum32],      %[csum32],       %[tmp1]         \n\t"
      "addu       %[csum32],      %[csum32],       %[tmp2]         \n\t"
      "addu       %[csum32],      %[csum32],       %[tmp3]         \n\t"
      "bgtz       %[n],           1b                               \n\t"
      " addu      %[csum32],      %[csum32],       %[tmp4]         \n\t"
      "b          3f                                               \n\t"
      " nop                                                        \n\t"
     "2:                                                           \n\t"
      "lh         %[tmp1],        0(%[inptr])                      \n\t"
      "lh         %[tmp2],        0(%[tmp_x])                      \n\t"
      "lh         %[tmp3],        2(%[inptr])                      \n\t"
      "lh         %[tmp4],        2(%[tmp_x])                      \n\t"
      "lh         %[tmp5],        4(%[inptr])                      \n\t"
      "lh         %[tmp6],        4(%[tmp_x])                      \n\t"
      "lh         %[tmp7],        6(%[inptr])                      \n\t"
      "lh         %[tmp8],        6(%[tmp_x])                      \n\t"
      "mul        %[tmp1],        %[tmp1],         %[tmp2]         \n\t"
      "mul        %[tmp2],        %[tmp3],         %[tmp4]         \n\t"
      "mul        %[tmp3],        %[tmp5],         %[tmp6]         \n\t"
      "mul        %[tmp4],        %[tmp7],         %[tmp8]         \n\t"
      "addiu      %[n],           %[n],            -4              \n\t"
      "addiu      %[inptr],       %[inptr],        8               \n\t"
      "addiu      %[tmp_x],       %[tmp_x],        8               \n\t"
      "srav       %[tmp1],        %[tmp1],         %[scaling]      \n\t"
      "srav       %[tmp2],        %[tmp2],         %[scaling]      \n\t"
      "srav       %[tmp3],        %[tmp3],         %[scaling]      \n\t"
      "srav       %[tmp4],        %[tmp4],         %[scaling]      \n\t"
      "addu       %[csum32],      %[csum32],       %[tmp1]         \n\t"
      "addu       %[csum32],      %[csum32],       %[tmp2]         \n\t"
      "addu       %[csum32],      %[csum32],       %[tmp3]         \n\t"
      "bgtz       %[n],           2b                               \n\t"
      " addu      %[csum32],      %[csum32],       %[tmp4]         \n\t"
     "3:                                                           \n\t"
      ".set       pop                                              \n\t"
      : [tmp1] "=&r" (tmp1), [tmp2] "=&r" (tmp2), [tmp3] "=&r" (tmp3),
        [tmp4] "=&r" (tmp4), [tmp5] "=&r" (tmp5), [tmp6] "=&r" (tmp6),
        [tmp7] "=&r" (tmp7), [tmp8] "=&r" (tmp8), [inptr] "+r" (inptr),
        [csum32] "+r" (csum32), [tmp_x] "+r" (tmp_x), [ysum32] "+r" (ysum32),
        [n] "+r" (n)
      : [tmp_in1] "r" (tmp_in1), [tmp_in2] "r" (tmp_in2),
        [scaling] "r" (scaling)
      : "memory", "hi", "lo"
    );

    logcorQ8--;
    lys = WebRtcIsacfix_Log2Q8((uint32_t)ysum32) >> 1; // Q8, sqrt(ysum)
    if (csum32 > 0) {
      lcs = WebRtcIsacfix_Log2Q8((uint32_t)csum32); // 2log(csum) in Q8
      if (lcs > (lys + oneQ8)) { // csum/sqrt(ysum) > 2
        *logcorQ8 = lcs - lys;  // log2(csum/sqrt(ysum))
      } else {
        *logcorQ8 = oneQ8;  // 1.00
      }
    } else {
      *logcorQ8 = 0;
    }
  }
}
