/*
 * Loongson MMI optimizations for libjpeg-turbo
 *
 * Copyright (C) 2016-2017, Loongson Technology Corporation Limited, BeiJing.
 *                          All Rights Reserved.
 * Authors:  ZhuChen     <zhuchen@loongson.cn>
 *           CaiWanwei   <caiwanwei@loongson.cn>
 *           SunZhangzhi <sunzhangzhi-cq@loongson.cn>
 * Copyright (C) 2018, D. R. Commander.  All Rights Reserved.
 *
 * Based on the x86 SIMD extension for IJG JPEG library
 * Copyright (C) 1999-2006, MIYASAKA Masaru.
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

/* INTEGER QUANTIZATION AND SAMPLE CONVERSION */

#include "jsimd_mmi.h"


#define DO_QUANT() { \
  mm2 = _mm_load_si64((__m64 *)&workspace[0]); \
  mm3 = _mm_load_si64((__m64 *)&workspace[4]); \
  \
  mm0 = mm2; \
  mm1 = mm3; \
  \
  mm2 = _mm_srai_pi16(mm2, (WORD_BIT - 1));   /* -1 if value < 0, */ \
                                              /* 0 otherwise */ \
  mm3 = _mm_srai_pi16(mm3, (WORD_BIT - 1)); \
  \
  mm0 = _mm_xor_si64(mm0, mm2);               /* val = -val */ \
  mm1 = _mm_xor_si64(mm1, mm3); \
  mm0 = _mm_sub_pi16(mm0, mm2); \
  mm1 = _mm_sub_pi16(mm1, mm3); \
  \
  corr0 = _mm_load_si64((__m64 *)&divisors[DCTSIZE2 * 1]);  /* correction */ \
  corr1 = _mm_load_si64((__m64 *)&divisors[DCTSIZE2 * 1 + 4]); \
  \
  mm0 = _mm_add_pi16(mm0, corr0);             /* correction + roundfactor */ \
  mm1 = _mm_add_pi16(mm1, corr1); \
  \
  mm4 = mm0; \
  mm5 = mm1; \
  \
  recip0 = _mm_load_si64((__m64 *)&divisors[DCTSIZE2 * 0]);  /* reciprocal */ \
  recip1 = _mm_load_si64((__m64 *)&divisors[DCTSIZE2 * 0 + 4]); \
  \
  mm0 = _mm_mulhi_pi16(mm0, recip0); \
  mm1 = _mm_mulhi_pi16(mm1, recip1); \
  \
  mm0 = _mm_add_pi16(mm0, mm4);  /* reciprocal is always negative */ \
  mm1 = _mm_add_pi16(mm1, mm5);  /* (MSB=1), so we always need to add the */ \
                                 /* initial value (input value is never */ \
                                 /* negative as we inverted it at the */ \
                                 /* start of this routine) */ \
  \
  scale0 = _mm_load_si64((__m64 *)&divisors[DCTSIZE2 * 2]);  /* scale */ \
  scale1 = _mm_load_si64((__m64 *)&divisors[DCTSIZE2 * 2 + 4]); \
  \
  mm6 = scale0; \
  mm7 = scale1; \
  mm4 = mm0; \
  mm5 = mm1; \
  \
  mm0 = _mm_mulhi_pi16(mm0, mm6); \
  mm1 = _mm_mulhi_pi16(mm1, mm7); \
  \
  mm6 = _mm_srai_pi16(mm6, (WORD_BIT - 1));   /* determine if scale... */ \
                                              /* is negative */ \
  mm7 = _mm_srai_pi16(mm7, (WORD_BIT - 1)); \
  \
  mm6 = _mm_and_si64(mm6, mm4);               /* and add input if it is */ \
  mm7 = _mm_and_si64(mm7, mm5); \
  mm0 = _mm_add_pi16(mm0, mm6); \
  mm1 = _mm_add_pi16(mm1, mm7); \
  \
  mm4 = _mm_srai_pi16(mm4, (WORD_BIT - 1));   /* then check if... */ \
  mm5 = _mm_srai_pi16(mm5, (WORD_BIT - 1));   /* negative input */ \
  \
  mm4 = _mm_and_si64(mm4, scale0);            /* and add scale if it is */ \
  mm5 = _mm_and_si64(mm5, scale1); \
  mm0 = _mm_add_pi16(mm0, mm4); \
  mm1 = _mm_add_pi16(mm1, mm5); \
  \
  mm0 = _mm_xor_si64(mm0, mm2);               /* val = -val */ \
  mm1 = _mm_xor_si64(mm1, mm3); \
  mm0 = _mm_sub_pi16(mm0, mm2); \
  mm1 = _mm_sub_pi16(mm1, mm3); \
  \
  _mm_store_si64((__m64 *)&output_ptr[0], mm0); \
  _mm_store_si64((__m64 *)&output_ptr[4], mm1); \
  \
  workspace += DCTSIZE; \
  divisors += DCTSIZE; \
  output_ptr += DCTSIZE; \
}


void jsimd_quantize_mmi(JCOEFPTR coef_block, DCTELEM *divisors,
                        DCTELEM *workspace)
{
  JCOEFPTR output_ptr = coef_block;
  __m64 mm0, mm1, mm2, mm3, mm4, mm5, mm6, mm7;
  __m64 corr0, corr1, recip0, recip1, scale0, scale1;

  DO_QUANT()
  DO_QUANT()
  DO_QUANT()
  DO_QUANT()
  DO_QUANT()
  DO_QUANT()
  DO_QUANT()
  DO_QUANT()
}
