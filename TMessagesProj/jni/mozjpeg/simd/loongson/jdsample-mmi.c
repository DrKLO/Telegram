/*
 * Loongson MMI optimizations for libjpeg-turbo
 *
 * Copyright (C) 2015, 2018, D. R. Commander.  All Rights Reserved.
 * Copyright (C) 2016-2017, Loongson Technology Corporation Limited, BeiJing.
 *                          All Rights Reserved.
 * Authors:  ZhuChen     <zhuchen@loongson.cn>
 *           CaiWanwei   <caiwanwei@loongson.cn>
 *           SunZhangzhi <sunzhangzhi-cq@loongson.cn>
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

/* CHROMA UPSAMPLING */

#include "jsimd_mmi.h"


enum const_index {
  index_PW_THREE,
  index_PW_SEVEN,
  index_PW_EIGHT,
};

static uint64_t const_value[] = {
  _uint64_set_pi16(3, 3, 3, 3),
  _uint64_set_pi16(7, 7, 7, 7),
  _uint64_set_pi16(8, 8, 8, 8),
};

#define PW_THREE  get_const_value(index_PW_THREE)
#define PW_SEVEN  get_const_value(index_PW_SEVEN)
#define PW_EIGHT  get_const_value(index_PW_EIGHT)


#define PROCESS_ROW(r) { \
  mm7 = _mm_load_si64((__m64 *)outptr##r);      /* mm7=IntrL=( 0 1 2 3) */ \
  mm3 = _mm_load_si64((__m64 *)outptr##r + 1);  /* mm3=IntrH=( 4 5 6 7) */ \
  \
  mm0 = mm7; \
  mm4 = mm3; \
  mm0 = _mm_srli_si64(mm0, 2 * BYTE_BIT);                   /* mm0=( 1 2 3 -) */ \
  mm4 = _mm_slli_si64(mm4, (SIZEOF_MMWORD - 2) * BYTE_BIT); /* mm4=( - - - 4) */ \
  mm5 = mm7; \
  mm6 = mm3; \
  mm5 = _mm_srli_si64(mm5, (SIZEOF_MMWORD - 2) * BYTE_BIT); /* mm5=( 3 - - -) */ \
  mm6 = _mm_slli_si64(mm6, 2 * BYTE_BIT);                   /* mm6=( - 4 5 6) */ \
  \
  mm0 = _mm_or_si64(mm0, mm4);                /* mm0=( 1 2 3 4) */ \
  mm5 = _mm_or_si64(mm5, mm6);                /* mm5=( 3 4 5 6) */ \
  \
  mm1 = mm7; \
  mm2 = mm3; \
  mm1 = _mm_slli_si64(mm1, 2 * BYTE_BIT);     /* mm1=( - 0 1 2) */ \
  mm2 = _mm_srli_si64(mm2, 2 * BYTE_BIT);     /* mm2=( 5 6 7 -) */ \
  mm4 = mm3; \
  mm4 = _mm_srli_si64(mm4, (SIZEOF_MMWORD - 2) * BYTE_BIT); /* mm4=( 7 - - -) */ \
  \
  mm1 = _mm_or_si64(mm1, wk[r]);              /* mm1=(-1 0 1 2) */ \
  mm2 = _mm_or_si64(mm2, wk[r + 2]);          /* mm2=( 5 6 6 8) */ \
  \
  wk[r] = mm4; \
  \
  mm7 = _mm_mullo_pi16(mm7, PW_THREE); \
  mm3 = _mm_mullo_pi16(mm3, PW_THREE); \
  mm1 = _mm_add_pi16(mm1, PW_EIGHT); \
  mm5 = _mm_add_pi16(mm5, PW_EIGHT); \
  mm0 = _mm_add_pi16(mm0, PW_SEVEN); \
  mm2 = _mm_add_pi16(mm2, PW_SEVEN); \
  \
  mm1 = _mm_add_pi16(mm1, mm7); \
  mm5 = _mm_add_pi16(mm5, mm3); \
  mm1 = _mm_srli_pi16(mm1, 4);                /* mm1=OutrLE=( 0  2  4  6) */ \
  mm5 = _mm_srli_pi16(mm5, 4);                /* mm5=OutrHE=( 8 10 12 14) */ \
  mm0 = _mm_add_pi16(mm0, mm7); \
  mm2 = _mm_add_pi16(mm2, mm3); \
  mm0 = _mm_srli_pi16(mm0, 4);                /* mm0=OutrLO=( 1  3  5  7) */ \
  mm2 = _mm_srli_pi16(mm2, 4);                /* mm2=OutrHO=( 9 11 13 15) */ \
  \
  mm0 = _mm_slli_pi16(mm0, BYTE_BIT); \
  mm2 = _mm_slli_pi16(mm2, BYTE_BIT); \
  mm1 = _mm_or_si64(mm1, mm0);     /* mm1=OutrL=( 0  1  2  3  4  5  6  7) */ \
  mm5 = _mm_or_si64(mm5, mm2);     /* mm5=OutrH=( 8  9 10 11 12 13 14 15) */ \
  \
  _mm_store_si64((__m64 *)outptr##r, mm1); \
  _mm_store_si64((__m64 *)outptr##r + 1, mm5); \
}

void jsimd_h2v2_fancy_upsample_mmi(int max_v_samp_factor,
                                   JDIMENSION downsampled_width,
                                   JSAMPARRAY input_data,
                                   JSAMPARRAY *output_data_ptr)
{
  JSAMPARRAY output_data = *output_data_ptr;
  JSAMPROW inptr_1, inptr0, inptr1, outptr0, outptr1;
  int inrow, outrow, incol, tmp, tmp1;
  __m64 mm0, mm1, mm2, mm3 = 0.0, mm4, mm5, mm6, mm7 = 0.0;
  __m64 wk[4], mm_tmp;

  for (inrow = 0, outrow = 0; outrow < max_v_samp_factor; inrow++) {

    inptr_1 = input_data[inrow - 1];
    inptr0 = input_data[inrow];
    inptr1 = input_data[inrow + 1];
    outptr0 = output_data[outrow++];
    outptr1 = output_data[outrow++];

    if (downsampled_width & 7) {
      tmp = (downsampled_width - 1) * sizeof(JSAMPLE);
      tmp1 =  downsampled_width * sizeof(JSAMPLE);
      asm("daddu  $8, %3, %6\r\n"
          "lb     $9, ($8)\r\n"
          "daddu  $8, %3, %7\r\n"
          "sb     $9, ($8)\r\n"
          "daddu  $8, %4, %6\r\n"
          "lb     $9, ($8)\r\n"
          "daddu  $8, %4, %7\r\n"
          "sb     $9, ($8)\r\n"
          "daddu  $8, %5, %6\r\n"
          "lb     $9, ($8)\r\n"
          "daddu  $8, %5, %7\r\n"
          "sb     $9, ($8)\r\n"
          : "=m" (*inptr_1), "=m" (*inptr0), "=m" (*inptr1)
          : "r" (inptr_1), "r" (inptr0), "r" (inptr1), "r" (tmp), "r" (tmp1)
          : "$8", "$9"
         );
    }

    /* process the first column block */
    mm0 = _mm_load_si64((__m64 *)inptr0);     /* mm0 = row[ 0][0] */
    mm1 = _mm_load_si64((__m64 *)inptr_1);    /* mm1 = row[-1][0] */
    mm2 = _mm_load_si64((__m64 *)inptr1);     /* mm2 = row[ 1][0] */

    mm3 = _mm_xor_si64(mm3, mm3);             /* mm3 = (all 0's) */
    mm4 = mm0;
    mm0 = _mm_unpacklo_pi8(mm0, mm3);         /* mm0 = row[ 0][0]( 0 1 2 3) */
    mm4 = _mm_unpackhi_pi8(mm4, mm3);         /* mm4 = row[ 0][0]( 4 5 6 7) */
    mm5 = mm1;
    mm1 = _mm_unpacklo_pi8(mm1, mm3);         /* mm1 = row[-1][0]( 0 1 2 3) */
    mm5 = _mm_unpackhi_pi8(mm5, mm3);         /* mm5 = row[-1][0]( 4 5 6 7) */
    mm6 = mm2;
    mm2 = _mm_unpacklo_pi8(mm2, mm3);         /* mm2 = row[+1][0]( 0 1 2 3) */
    mm6 = _mm_unpackhi_pi8(mm6, mm3);         /* mm6 = row[+1][0]( 4 5 6 7) */

    mm0 = _mm_mullo_pi16(mm0, PW_THREE);
    mm4 = _mm_mullo_pi16(mm4, PW_THREE);

    mm7 = _mm_cmpeq_pi8(mm7, mm7);
    mm7 = _mm_srli_si64(mm7, (SIZEOF_MMWORD - 2) * BYTE_BIT);

    mm1 = _mm_add_pi16(mm1, mm0);             /* mm1=Int0L=( 0 1 2 3) */
    mm5 = _mm_add_pi16(mm5, mm4);             /* mm5=Int0H=( 4 5 6 7) */
    mm2 = _mm_add_pi16(mm2, mm0);             /* mm2=Int1L=( 0 1 2 3) */
    mm6 = _mm_add_pi16(mm6, mm4);             /* mm6=Int1H=( 4 5 6 7) */

    _mm_store_si64((__m64 *)outptr0, mm1);      /* temporarily save */
    _mm_store_si64((__m64 *)outptr0 + 1, mm5);  /* the intermediate data */
    _mm_store_si64((__m64 *)outptr1, mm2);
    _mm_store_si64((__m64 *)outptr1 + 1, mm6);

    mm1 = _mm_and_si64(mm1, mm7);             /* mm1=( 0 - - -) */
    mm2 = _mm_and_si64(mm2, mm7);             /* mm2=( 0 - - -) */

    wk[0] = mm1;
    wk[1] = mm2;

    for (incol = downsampled_width; incol > 0;
         incol -= 8, inptr_1 += 8, inptr0 += 8, inptr1 += 8,
         outptr0 += 16, outptr1 += 16) {

      if (incol > 8) {
        /* process the next column block */
        mm0 = _mm_load_si64((__m64 *)inptr0 + 1);   /* mm0 = row[ 0][1] */
        mm1 = _mm_load_si64((__m64 *)inptr_1 + 1);  /* mm1 = row[-1][1] */
        mm2 = _mm_load_si64((__m64 *)inptr1 + 1);   /* mm2 = row[+1][1] */

        mm3 = _mm_setzero_si64();             /* mm3 = (all 0's) */
        mm4 = mm0;
        mm0 = _mm_unpacklo_pi8(mm0, mm3);     /* mm0 = row[ 0][1]( 0 1 2 3) */
        mm4 = _mm_unpackhi_pi8(mm4, mm3);     /* mm4 = row[ 0][1]( 4 5 6 7) */
        mm5 = mm1;
        mm1 = _mm_unpacklo_pi8(mm1, mm3);     /* mm1 = row[-1][1]( 0 1 2 3) */
        mm5 = _mm_unpackhi_pi8(mm5, mm3);     /* mm5 = row[-1][1]( 4 5 6 7) */
        mm6 = mm2;
        mm2 = _mm_unpacklo_pi8(mm2, mm3);     /* mm2 = row[+1][1]( 0 1 2 3) */
        mm6 = _mm_unpackhi_pi8(mm6, mm3);     /* mm6 = row[+1][1]( 4 5 6 7) */

        mm0 = _mm_mullo_pi16(mm0, PW_THREE);
        mm4 = _mm_mullo_pi16(mm4, PW_THREE);

        mm1 = _mm_add_pi16(mm1, mm0);         /* mm1 = Int0L = ( 0 1 2 3) */
        mm5 = _mm_add_pi16(mm5, mm4);         /* mm5 = Int0H = ( 4 5 6 7) */
        mm2 = _mm_add_pi16(mm2, mm0);         /* mm2 = Int1L = ( 0 1 2 3) */
        mm6 = _mm_add_pi16(mm6, mm4);         /* mm6 = Int1H = ( 4 5 6 7) */

        _mm_store_si64((__m64 *)outptr0 + 2, mm1);  /* temporarily save */
        _mm_store_si64((__m64 *)outptr0 + 3, mm5);  /* the intermediate data */
        _mm_store_si64((__m64 *)outptr1 + 2, mm2);
        _mm_store_si64((__m64 *)outptr1 + 3, mm6);

        mm1 = _mm_slli_si64(mm1, (SIZEOF_MMWORD - 2) * BYTE_BIT); /* mm1=( - - - 0) */
        mm2 = _mm_slli_si64(mm2, (SIZEOF_MMWORD - 2) * BYTE_BIT); /* mm2=( - - - 0) */

        wk[2] = mm1;
        wk[3] = mm2;
      } else {
        /* process the last column block */
        mm1 = _mm_cmpeq_pi8(mm1, mm1);
        mm1 = _mm_slli_si64(mm1, (SIZEOF_MMWORD - 2) * BYTE_BIT);
        mm2 = mm1;

        mm_tmp = _mm_load_si64((__m64 *)outptr0 + 1);
        mm1 = _mm_and_si64(mm1, mm_tmp);      /* mm1=( - - - 7) */
        mm_tmp = _mm_load_si64((__m64 *)outptr1 + 1);
        mm2 = _mm_and_si64(mm2, mm_tmp);      /* mm2=( - - - 7) */

        wk[2] = mm1;
        wk[3] = mm2;
      }

      /* process the upper row */
      PROCESS_ROW(0)

      /* process the lower row */
      PROCESS_ROW(1)
    }
  }
}
