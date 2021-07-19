/*
 * Loongson MMI optimizations for libjpeg-turbo
 *
 * Copyright 2009 Pierre Ossman <ossman@cendio.se> for Cendio AB
 * Copyright (C) 2015, D. R. Commander.  All Rights Reserved.
 * Copyright (C) 2016-2017, Loongson Technology Corporation Limited, BeiJing.
 *                          All Rights Reserved.
 * Authors:  ZhuChen     <zhuchen@loongson.cn>
 *           SunZhangzhi <sunzhangzhi-cq@loongson.cn>
 *           CaiWanwei   <caiwanwei@loongson.cn>
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

/* This file is included by jdcolor-mmi.c */


#if RGB_RED == 0
#define mmA  mm0
#define mmB  mm1
#elif RGB_GREEN == 0
#define mmA  mm2
#define mmB  mm3
#elif RGB_BLUE == 0
#define mmA  mm4
#define mmB  mm5
#else
#define mmA  mm6
#define mmB  mm7
#endif

#if RGB_RED == 1
#define mmC  mm0
#define mmD  mm1
#elif RGB_GREEN == 1
#define mmC  mm2
#define mmD  mm3
#elif RGB_BLUE == 1
#define mmC  mm4
#define mmD  mm5
#else
#define mmC  mm6
#define mmD  mm7
#endif

#if RGB_RED == 2
#define mmE  mm0
#define mmF  mm1
#elif RGB_GREEN == 2
#define mmE  mm2
#define mmF  mm3
#elif RGB_BLUE == 2
#define mmE  mm4
#define mmF  mm5
#else
#define mmE  mm6
#define mmF  mm7
#endif

#if RGB_RED == 3
#define mmG  mm0
#define mmH  mm1
#elif RGB_GREEN == 3
#define mmG  mm2
#define mmH  mm3
#elif RGB_BLUE == 3
#define mmG  mm4
#define mmH  mm5
#else
#define mmG  mm6
#define mmH  mm7
#endif


void jsimd_ycc_rgb_convert_mmi(JDIMENSION out_width, JSAMPIMAGE input_buf,
                               JDIMENSION input_row, JSAMPARRAY output_buf,
                               int num_rows)
{
  JSAMPROW outptr, inptr0, inptr1, inptr2;
  int num_cols, col;
  __m64 mm0, mm1, mm2, mm3, mm4, mm5, mm6, mm7;
  __m64 mm8, wk[2];

  while (--num_rows >= 0) {
    inptr0 = input_buf[0][input_row];
    inptr1 = input_buf[1][input_row];
    inptr2 = input_buf[2][input_row];
    input_row++;
    outptr = *output_buf++;

    for (num_cols = out_width; num_cols > 0; num_cols -= 8,
         inptr0 += 8, inptr1 += 8, inptr2 += 8) {

      mm5 = _mm_load_si64((__m64 *)inptr1);
      mm1 = _mm_load_si64((__m64 *)inptr2);
      mm8 = _mm_load_si64((__m64 *)inptr0);
      mm4 = 0;
      mm7 = 0;
      mm4 = _mm_cmpeq_pi16(mm4, mm4);
      mm7 = _mm_cmpeq_pi16(mm7, mm7);
      mm4 = _mm_srli_pi16(mm4, BYTE_BIT);
      mm7 = _mm_slli_pi16(mm7, 7);      /* mm7={0xFF80 0xFF80 0xFF80 0xFF80} */
      mm0 = mm4;                        /* mm0=mm4={0xFF 0x00 0xFF 0x00 ..} */

      mm4 = _mm_and_si64(mm4, mm5);           /* mm4=Cb(0246)=CbE */
      mm5 = _mm_srli_pi16(mm5, BYTE_BIT);     /* mm5=Cb(1357)=CbO */
      mm0 = _mm_and_si64(mm0, mm1);           /* mm0=Cr(0246)=CrE */
      mm1 = _mm_srli_pi16(mm1, BYTE_BIT);     /* mm1=Cr(1357)=CrO */
      mm4 = _mm_add_pi16(mm4, mm7);
      mm5 = _mm_add_pi16(mm5, mm7);
      mm0 = _mm_add_pi16(mm0, mm7);
      mm1 = _mm_add_pi16(mm1, mm7);

      /* (Original)
       * R = Y                + 1.40200 * Cr
       * G = Y - 0.34414 * Cb - 0.71414 * Cr
       * B = Y + 1.77200 * Cb
       *
       * (This implementation)
       * R = Y                + 0.40200 * Cr + Cr
       * G = Y - 0.34414 * Cb + 0.28586 * Cr - Cr
       * B = Y - 0.22800 * Cb + Cb + Cb
       */

      mm2 = mm4;                              /* mm2 = CbE */
      mm3 = mm5;                              /* mm3 = CbO */
      mm4 = _mm_add_pi16(mm4, mm4);           /* mm4 = 2*CbE */
      mm5 = _mm_add_pi16(mm5, mm5);           /* mm5 = 2*CbO */
      mm6 = mm0;                              /* mm6 = CrE */
      mm7 = mm1;                              /* mm7 = CrO */
      mm0 = _mm_add_pi16(mm0, mm0);           /* mm0 = 2*CrE */
      mm1 = _mm_add_pi16(mm1, mm1);           /* mm1 = 2*CrO */

      mm4 = _mm_mulhi_pi16(mm4, PW_MF0228);   /* mm4=(2*CbE * -FIX(0.22800) */
      mm5 = _mm_mulhi_pi16(mm5, PW_MF0228);   /* mm5=(2*CbO * -FIX(0.22800) */
      mm0 = _mm_mulhi_pi16(mm0, PW_F0402);    /* mm0=(2*CrE * FIX(0.40200)) */
      mm1 = _mm_mulhi_pi16(mm1, PW_F0402);    /* mm1=(2*CrO * FIX(0.40200)) */

      mm4 = _mm_add_pi16(mm4, PW_ONE);
      mm5 = _mm_add_pi16(mm5, PW_ONE);
      mm4 = _mm_srai_pi16(mm4, 1);            /* mm4=(CbE * -FIX(0.22800)) */
      mm5 = _mm_srai_pi16(mm5, 1);            /* mm5=(CbO * -FIX(0.22800)) */
      mm0 = _mm_add_pi16(mm0, PW_ONE);
      mm1 = _mm_add_pi16(mm1, PW_ONE);
      mm0 = _mm_srai_pi16(mm0, 1);            /* mm0=(CrE * FIX(0.40200)) */
      mm1 = _mm_srai_pi16(mm1, 1);            /* mm1=(CrO * FIX(0.40200)) */

      mm4 = _mm_add_pi16(mm4, mm2);
      mm5 = _mm_add_pi16(mm5, mm3);
      mm4 = _mm_add_pi16(mm4, mm2);       /* mm4=(CbE * FIX(1.77200))=(B-Y)E */
      mm5 = _mm_add_pi16(mm5, mm3);       /* mm5=(CbO * FIX(1.77200))=(B-Y)O */
      mm0 = _mm_add_pi16(mm0, mm6);       /* mm0=(CrE * FIX(1.40200))=(R-Y)E */
      mm1 = _mm_add_pi16(mm1, mm7);       /* mm1=(CrO * FIX(1.40200))=(R-Y)O */

      wk[0] = mm4;                            /* wk(0)=(B-Y)E */
      wk[1] = mm5;                            /* wk(1)=(B-Y)O */

      mm4 = mm2;
      mm5 = mm3;
      mm2 = _mm_unpacklo_pi16(mm2, mm6);
      mm4 = _mm_unpackhi_pi16(mm4, mm6);
      mm2 = _mm_madd_pi16(mm2, PW_MF0344_F0285);
      mm4 = _mm_madd_pi16(mm4, PW_MF0344_F0285);
      mm3 = _mm_unpacklo_pi16(mm3, mm7);
      mm5 = _mm_unpackhi_pi16(mm5, mm7);
      mm3 = _mm_madd_pi16(mm3, PW_MF0344_F0285);
      mm5 = _mm_madd_pi16(mm5, PW_MF0344_F0285);

      mm2 = _mm_add_pi32(mm2, PD_ONEHALF);
      mm4 = _mm_add_pi32(mm4, PD_ONEHALF);
      mm2 = _mm_srai_pi32(mm2, SCALEBITS);
      mm4 = _mm_srai_pi32(mm4, SCALEBITS);
      mm3 = _mm_add_pi32(mm3, PD_ONEHALF);
      mm5 = _mm_add_pi32(mm5, PD_ONEHALF);
      mm3 = _mm_srai_pi32(mm3, SCALEBITS);
      mm5 = _mm_srai_pi32(mm5, SCALEBITS);

      mm2 = _mm_packs_pi32(mm2, mm4);  /* mm2=CbE*-FIX(0.344)+CrE*FIX(0.285) */
      mm3 = _mm_packs_pi32(mm3, mm5);  /* mm3=CbO*-FIX(0.344)+CrO*FIX(0.285) */
      mm2 = _mm_sub_pi16(mm2, mm6);  /* mm2=CbE*-FIX(0.344)+CrE*-FIX(0.714)=(G-Y)E */
      mm3 = _mm_sub_pi16(mm3, mm7);  /* mm3=CbO*-FIX(0.344)+CrO*-FIX(0.714)=(G-Y)O */

      mm5 = mm8;                              /* mm5=Y(01234567) */

      mm4 = _mm_cmpeq_pi16(mm4, mm4);
      mm4 = _mm_srli_pi16(mm4, BYTE_BIT);    /* mm4={0xFF 0x00 0xFF 0x00 ..} */
      mm4 = _mm_and_si64(mm4, mm5);          /* mm4=Y(0246)=YE */
      mm5 = _mm_srli_pi16(mm5, BYTE_BIT);    /* mm5=Y(1357)=YO */

      mm0 = _mm_add_pi16(mm0, mm4);      /* mm0=((R-Y)E+YE)=RE=(R0 R2 R4 R6) */
      mm1 = _mm_add_pi16(mm1, mm5);      /* mm1=((R-Y)O+YO)=RO=(R1 R3 R5 R7) */
      mm0 = _mm_packs_pu16(mm0, mm0);    /* mm0=(R0 R2 R4 R6 ** ** ** **) */
      mm1 = _mm_packs_pu16(mm1, mm1);    /* mm1=(R1 R3 R5 R7 ** ** ** **) */

      mm2 = _mm_add_pi16(mm2, mm4);      /* mm2=((G-Y)E+YE)=GE=(G0 G2 G4 G6) */
      mm3 = _mm_add_pi16(mm3, mm5);      /* mm3=((G-Y)O+YO)=GO=(G1 G3 G5 G7) */
      mm2 = _mm_packs_pu16(mm2, mm2);    /* mm2=(G0 G2 G4 G6 ** ** ** **) */
      mm3 = _mm_packs_pu16(mm3, mm3);    /* mm3=(G1 G3 G5 G7 ** ** ** **) */

      mm4 = _mm_add_pi16(mm4, wk[0]);    /* mm4=(YE+(B-Y)E)=BE=(B0 B2 B4 B6) */
      mm5 = _mm_add_pi16(mm5, wk[1]);    /* mm5=(YO+(B-Y)O)=BO=(B1 B3 B5 B7) */
      mm4 = _mm_packs_pu16(mm4, mm4);    /* mm4=(B0 B2 B4 B6 ** ** ** **) */
      mm5 = _mm_packs_pu16(mm5, mm5);    /* mm5=(B1 B3 B5 B7 ** ** ** **) */

#if RGB_PIXELSIZE == 3

      /* mmA=(00 02 04 06 ** ** ** **), mmB=(01 03 05 07 ** ** ** **) */
      /* mmC=(10 12 14 16 ** ** ** **), mmD=(11 13 15 17 ** ** ** **) */
      mmA = _mm_unpacklo_pi8(mmA, mmC);     /* mmA=(00 10 02 12 04 14 06 16) */
      mmE = _mm_unpacklo_pi8(mmE, mmB);     /* mmE=(20 01 22 03 24 05 26 07) */
      mmD = _mm_unpacklo_pi8(mmD, mmF);     /* mmD=(11 21 13 23 15 25 17 27) */

      mmG = mmA;
      mmH = mmA;
      mmA = _mm_unpacklo_pi16(mmA, mmE);    /* mmA=(00 10 20 01 02 12 22 03) */
      mmG = _mm_unpackhi_pi16(mmG, mmE);    /* mmG=(04 14 24 05 06 16 26 07) */

      mmH = _mm_srli_si64(mmH, 2 * BYTE_BIT);
      mmE = _mm_srli_si64(mmE, 2 * BYTE_BIT);

      mmC = mmD;
      mmB = mmD;
      mmD = _mm_unpacklo_pi16(mmD, mmH);    /* mmD=(11 21 02 12 13 23 04 14) */
      mmC = _mm_unpackhi_pi16(mmC, mmH);    /* mmC=(15 25 06 16 17 27 -- --) */

      mmB = _mm_srli_si64(mmB, 2 * BYTE_BIT); /* mmB=(13 23 15 25 17 27 -- --) */

      mmF = mmE;
      mmE = _mm_unpacklo_pi16(mmE, mmB);    /* mmE=(22 03 13 23 24 05 15 25) */
      mmF = _mm_unpackhi_pi16(mmF, mmB);    /* mmF=(26 07 17 27 -- -- -- --) */

      mmA = _mm_unpacklo_pi32(mmA, mmD);    /* mmA=(00 10 20 01 11 21 02 12) */
      mmE = _mm_unpacklo_pi32(mmE, mmG);    /* mmE=(22 03 13 23 04 14 24 05) */
      mmC = _mm_unpacklo_pi32(mmC, mmF);    /* mmC=(15 25 06 16 26 07 17 27) */

      if (num_cols >= 8) {
        _mm_store_si64((__m64 *)outptr, mmA);
        _mm_store_si64((__m64 *)(outptr + 8), mmE);
        _mm_store_si64((__m64 *)(outptr + 16), mmC);
        outptr += RGB_PIXELSIZE * 8;
      } else {
        col = num_cols * 3;
        asm(".set noreorder\r\n"

            "li      $8, 16\r\n"
            "move    $9, %4\r\n"
            "mov.s   $f4, %1\r\n"
            "mov.s   $f6, %3\r\n"
            "move    $10, %5\r\n"
            "bltu    $9, $8, 1f\r\n"
            "nop     \r\n"
            "gssdlc1 $f4, 7($10)\r\n"
            "gssdrc1 $f4, 0($10)\r\n"
            "gssdlc1 $f6, 7+8($10)\r\n"
            "gssdrc1 $f6, 8($10)\r\n"
            "mov.s   $f4, %2\r\n"
            "subu    $9, $9, 16\r\n"
            "daddu   $10, $10, 16\r\n"
            "b       2f\r\n"
            "nop     \r\n"

            "1:      \r\n"
            "li      $8, 8\r\n"               /* st8 */
            "bltu    $9, $8, 2f\r\n"
            "nop     \r\n"
            "gssdlc1 $f4, 7($10)\r\n"
            "gssdrc1 $f4, ($10)\r\n"
            "mov.s   $f4, %3\r\n"
            "subu    $9, $9, 8\r\n"
            "daddu   $10, $10, 8\r\n"

            "2:      \r\n"
            "li      $8, 4\r\n"               /* st4 */
            "mfc1    $11, $f4\r\n"
            "bltu    $9, $8, 3f\r\n"
            "nop     \r\n"
            "swl     $11, 3($10)\r\n"
            "swr     $11, 0($10)\r\n"
            "li      $8, 32\r\n"
            "mtc1    $8, $f6\r\n"
            "dsrl    $f4, $f4, $f6\r\n"
            "mfc1    $11, $f4\r\n"
            "subu    $9, $9, 4\r\n"
            "daddu   $10, $10, 4\r\n"

            "3:      \r\n"
            "li      $8, 2\r\n"               /* st2 */
            "bltu    $9, $8, 4f\r\n"
            "nop     \r\n"
            "ush     $11, 0($10)\r\n"
            "srl     $11, 16\r\n"
            "subu    $9, $9, 2\r\n"
            "daddu   $10, $10, 2\r\n"

            "4:      \r\n"
            "li      $8, 1\r\n"               /* st1 */
            "bltu    $9, $8, 5f\r\n"
            "nop     \r\n"
            "sb      $11, 0($10)\r\n"

            "5:      \r\n"
            "nop     \r\n"                    /* end */
            : "=m" (*outptr)
            : "f" (mmA), "f" (mmC), "f" (mmE), "r" (col), "r" (outptr)
            : "$f4", "$f6", "$8", "$9", "$10", "$11", "memory"
           );
      }

#else  /* RGB_PIXELSIZE == 4 */

#ifdef RGBX_FILLER_0XFF
      mm6 = _mm_cmpeq_pi8(mm6, mm6);
      mm7 = _mm_cmpeq_pi8(mm7, mm7);
#else
      mm6 = _mm_xor_si64(mm6, mm6);
      mm7 = _mm_xor_si64(mm7, mm7);
#endif
      /* mmA=(00 02 04 06 ** ** ** **), mmB=(01 03 05 07 ** ** ** **) */
      /* mmC=(10 12 14 16 ** ** ** **), mmD=(11 13 15 17 ** ** ** **) */
      /* mmE=(20 22 24 26 ** ** ** **), mmF=(21 23 25 27 ** ** ** **) */
      /* mmG=(30 32 34 36 ** ** ** **), mmH=(31 33 35 37 ** ** ** **) */

      mmA = _mm_unpacklo_pi8(mmA, mmC);     /* mmA=(00 10 02 12 04 14 06 16) */
      mmE = _mm_unpacklo_pi8(mmE, mmG);     /* mmE=(20 30 22 32 24 34 26 36) */
      mmB = _mm_unpacklo_pi8(mmB, mmD);     /* mmB=(01 11 03 13 05 15 07 17) */
      mmF = _mm_unpacklo_pi8(mmF, mmH);     /* mmF=(21 31 23 33 25 35 27 37) */

      mmC = mmA;
      mmA = _mm_unpacklo_pi16(mmA, mmE);    /* mmA=(00 10 20 30 02 12 22 32) */
      mmC = _mm_unpackhi_pi16(mmC, mmE);    /* mmC=(04 14 24 34 06 16 26 36) */
      mmG = mmB;
      mmB = _mm_unpacklo_pi16(mmB, mmF);    /* mmB=(01 11 21 31 03 13 23 33) */
      mmG = _mm_unpackhi_pi16(mmG, mmF);    /* mmG=(05 15 25 35 07 17 27 37) */

      mmD = mmA;
      mmA = _mm_unpacklo_pi32(mmA, mmB);    /* mmA=(00 10 20 30 01 11 21 31) */
      mmD = _mm_unpackhi_pi32(mmD, mmB);    /* mmD=(02 12 22 32 03 13 23 33) */
      mmH = mmC;
      mmC = _mm_unpacklo_pi32(mmC, mmG);    /* mmC=(04 14 24 34 05 15 25 35) */
      mmH = _mm_unpackhi_pi32(mmH, mmG);    /* mmH=(06 16 26 36 07 17 27 37) */

      if (num_cols >= 8) {
        _mm_store_si64((__m64 *)outptr, mmA);
        _mm_store_si64((__m64 *)(outptr + 8), mmD);
        _mm_store_si64((__m64 *)(outptr + 16), mmC);
        _mm_store_si64((__m64 *)(outptr + 24), mmH);
        outptr += RGB_PIXELSIZE * 8;
      } else {
        col = num_cols;
        asm(".set noreorder\r\n"              /* st16 */

            "li      $8, 4\r\n"
            "move    $9, %6\r\n"
            "move    $10, %7\r\n"
            "mov.s   $f4, %2\r\n"
            "mov.s   $f6, %4\r\n"
            "bltu    $9, $8, 1f\r\n"
            "nop     \r\n"
            "gssdlc1 $f4, 7($10)\r\n"
            "gssdrc1 $f4, ($10)\r\n"
            "gssdlc1 $f6, 7+8($10)\r\n"
            "gssdrc1 $f6, 8($10)\r\n"
            "mov.s   $f4, %3\r\n"
            "mov.s   $f6, %5\r\n"
            "subu    $9, $9, 4\r\n"
            "daddu   $10, $10, 16\r\n"

            "1:      \r\n"
            "li      $8, 2\r\n"               /* st8 */
            "bltu    $9, $8, 2f\r\n"
            "nop     \r\n"
            "gssdlc1 $f4, 7($10)\r\n"
            "gssdrc1 $f4, 0($10)\r\n"
            "mov.s   $f4, $f6\r\n"
            "subu    $9, $9, 2\r\n"
            "daddu   $10, $10, 8\r\n"

            "2:      \r\n"
            "li      $8, 1\r\n"               /* st4 */
            "bltu    $9, $8, 3f\r\n"
            "nop     \r\n"
            "gsswlc1 $f4, 3($10)\r\n"
            "gsswrc1 $f4, 0($10)\r\n"

            "3:      \r\n"
            "li      %1, 0\r\n"               /* end */
            : "=m" (*outptr), "=r" (col)
            : "f" (mmA), "f" (mmC), "f" (mmD), "f" (mmH), "r" (col),
              "r" (outptr)
            : "$f4", "$f6", "$8", "$9", "$10", "memory"
           );
      }

#endif

    }
  }
}

#undef mmA
#undef mmB
#undef mmC
#undef mmD
#undef mmE
#undef mmF
#undef mmG
#undef mmH
