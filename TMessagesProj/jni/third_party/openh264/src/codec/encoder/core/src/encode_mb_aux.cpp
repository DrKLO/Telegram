/*!
 * \copy
 *     Copyright (c)  2013, Cisco Systems
 *     All rights reserved.
 *
 *     Redistribution and use in source and binary forms, with or without
 *     modification, are permitted provided that the following conditions
 *     are met:
 *
 *        * Redistributions of source code must retain the above copyright
 *          notice, this list of conditions and the following disclaimer.
 *
 *        * Redistributions in binary form must reproduce the above copyright
 *          notice, this list of conditions and the following disclaimer in
 *          the documentation and/or other materials provided with the
 *          distribution.
 *
 *     THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *     "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *     LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *     FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *     COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *     INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *     BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *     LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *     CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *     LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 *     ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *     POSSIBILITY OF SUCH DAMAGE.
 *
 */


#include "ls_defines.h"
#include "encode_mb_aux.h"
#include "cpu_core.h"
namespace WelsEnc {

ALIGNED_DECLARE (const int16_t, g_kiQuantInterFF[58][8], 16) = {
  /* 0*/ {   0,   1,   0,   1,   1,   1,   1,   1 },
  /* 1*/ {   0,   1,   0,   1,   1,   1,   1,   1 },
  /* 2*/ {   1,   1,   1,   1,   1,   1,   1,   1 },
  /* 3*/ {   1,   1,   1,   1,   1,   1,   1,   1 },
  /* 4*/ {   1,   1,   1,   1,   1,   2,   1,   2 },
  /* 5*/ {   1,   1,   1,   1,   1,   2,   1,   2 },
  /* 6*/ {   1,   1,   1,   1,   1,   2,   1,   2 },
  /* 7*/ {   1,   1,   1,   1,   1,   2,   1,   2 },
  /* 8*/ {   1,   2,   1,   2,   2,   3,   2,   3 },
  /* 9*/ {   1,   2,   1,   2,   2,   3,   2,   3 },
  /*10*/ {   1,   2,   1,   2,   2,   3,   2,   3 },
  /*11*/ {   1,   2,   1,   2,   2,   4,   2,   4 },
  /*12*/ {   2,   3,   2,   3,   3,   4,   3,   4 },
  /*13*/ {   2,   3,   2,   3,   3,   5,   3,   5 },
  /*14*/ {   2,   3,   2,   3,   3,   5,   3,   5 },
  /*15*/ {   2,   4,   2,   4,   4,   6,   4,   6 },
  /*16*/ {   3,   4,   3,   4,   4,   7,   4,   7 },
  /*17*/ {   3,   5,   3,   5,   5,   8,   5,   8 },
  /*18*/ {   3,   5,   3,   5,   5,   8,   5,   8 },
  /*19*/ {   4,   6,   4,   6,   6,   9,   6,   9 },
  /*20*/ {   4,   7,   4,   7,   7,  10,   7,  10 },
  /*21*/ {   5,   8,   5,   8,   8,  12,   8,  12 },
  /*22*/ {   5,   8,   5,   8,   8,  13,   8,  13 },
  /*23*/ {   6,  10,   6,  10,  10,  15,  10,  15 },
  /*24*/ {   7,  11,   7,  11,  11,  17,  11,  17 },
  /*25*/ {   7,  12,   7,  12,  12,  19,  12,  19 },
  /*26*/ {   9,  13,   9,  13,  13,  21,  13,  21 },
  /*27*/ {   9,  15,   9,  15,  15,  24,  15,  24 },
  /*28*/ {  11,  17,  11,  17,  17,  26,  17,  26 },
  /*29*/ {  12,  19,  12,  19,  19,  30,  19,  30 },
  /*30*/ {  13,  22,  13,  22,  22,  33,  22,  33 },
  /*31*/ {  15,  23,  15,  23,  23,  38,  23,  38 },
  /*32*/ {  17,  27,  17,  27,  27,  42,  27,  42 },
  /*33*/ {  19,  30,  19,  30,  30,  48,  30,  48 },
  /*34*/ {  21,  33,  21,  33,  33,  52,  33,  52 },
  /*35*/ {  24,  38,  24,  38,  38,  60,  38,  60 },
  /*36*/ {  27,  43,  27,  43,  43,  67,  43,  67 },
  /*37*/ {  29,  47,  29,  47,  47,  75,  47,  75 },
  /*38*/ {  35,  53,  35,  53,  53,  83,  53,  83 },
  /*39*/ {  37,  60,  37,  60,  60,  96,  60,  96 },
  /*40*/ {  43,  67,  43,  67,  67, 104,  67, 104 },
  /*41*/ {  48,  77,  48,  77,  77, 121,  77, 121 },
  /*42*/ {  53,  87,  53,  87,  87, 133,  87, 133 },
  /*43*/ {  59,  93,  59,  93,  93, 150,  93, 150 },
  /*44*/ {  69, 107,  69, 107, 107, 167, 107, 167 },
  /*45*/ {  75, 120,  75, 120, 120, 192, 120, 192 },
  /*46*/ {  85, 133,  85, 133, 133, 208, 133, 208 },
  /*47*/ {  96, 153,  96, 153, 153, 242, 153, 242 },
  /*48*/ { 107, 173, 107, 173, 173, 267, 173, 267 },
  /*49*/ { 117, 187, 117, 187, 187, 300, 187, 300 },
  /*50*/ { 139, 213, 139, 213, 213, 333, 213, 333 },
  /*51*/ { 149, 240, 149, 240, 240, 383, 240, 383 },
  /* from here below is only for intra */
  /*46*/ { 171, 267, 171, 267, 267, 417, 267, 417 },
  /*47*/ { 192, 307, 192, 307, 307, 483, 307, 483 },
  /*48*/ { 213, 347, 213, 347, 347, 533, 347, 533 },
  /*49*/ { 235, 373, 235, 373, 373, 600, 373, 600 },
  /*50*/ { 277, 427, 277, 427, 427, 667, 427, 667 },
  /*51*/ { 299, 480, 299, 480, 480, 767, 480, 767 },
};



ALIGNED_DECLARE (const int16_t, g_kiQuantMF[52][8], 16) = {
  /* 0*/        {26214, 16132, 26214, 16132, 16132, 10486, 16132, 10486 },
  /* 1*/        {23832, 14980, 23832, 14980, 14980,  9320, 14980,  9320 },
  /* 2*/        {20164, 13108, 20164, 13108, 13108,  8388, 13108,  8388 },
  /* 3*/        {18724, 11650, 18724, 11650, 11650,  7294, 11650,  7294 },
  /* 4*/        {16384, 10486, 16384, 10486, 10486,  6710, 10486,  6710 },
  /* 5*/        {14564,  9118, 14564,  9118,  9118,  5786,  9118,  5786 },
  /* 6*/        {13107,  8066, 13107,  8066,  8066,  5243,  8066,  5243 },
  /* 7*/        {11916,  7490, 11916,  7490,  7490,  4660,  7490,  4660 },
  /* 8*/        {10082,  6554, 10082,  6554,  6554,  4194,  6554,  4194 },
  /* 9*/        { 9362,  5825,  9362,  5825,  5825,  3647,  5825,  3647 },
  /*10*/        { 8192,  5243,  8192,  5243,  5243,  3355,  5243,  3355 },
  /*11*/        { 7282,  4559,  7282,  4559,  4559,  2893,  4559,  2893 },
  /*12*/        { 6554,  4033,  6554,  4033,  4033,  2622,  4033,  2622 },
  /*13*/        { 5958,  3745,  5958,  3745,  3745,  2330,  3745,  2330 },
  /*14*/        { 5041,  3277,  5041,  3277,  3277,  2097,  3277,  2097 },
  /*15*/        { 4681,  2913,  4681,  2913,  2913,  1824,  2913,  1824 },
  /*16*/        { 4096,  2622,  4096,  2622,  2622,  1678,  2622,  1678 },
  /*17*/        { 3641,  2280,  3641,  2280,  2280,  1447,  2280,  1447 },
  /*18*/        { 3277,  2017,  3277,  2017,  2017,  1311,  2017,  1311 },
  /*19*/        { 2979,  1873,  2979,  1873,  1873,  1165,  1873,  1165 },
  /*20*/        { 2521,  1639,  2521,  1639,  1639,  1049,  1639,  1049 },
  /*21*/        { 2341,  1456,  2341,  1456,  1456,   912,  1456,   912 },
  /*22*/        { 2048,  1311,  2048,  1311,  1311,   839,  1311,   839 },
  /*23*/        { 1821,  1140,  1821,  1140,  1140,   723,  1140,   723 },
  /*24*/        { 1638,  1008,  1638,  1008,  1008,   655,  1008,   655 },
  /*25*/        { 1490,   936,  1490,   936,   936,   583,   936,   583 },
  /*26*/        { 1260,   819,  1260,   819,   819,   524,   819,   524 },
  /*27*/        { 1170,   728,  1170,   728,   728,   456,   728,   456 },
  /*28*/        { 1024,   655,  1024,   655,   655,   419,   655,   419 },
  /*29*/        {  910,   570,   910,   570,   570,   362,   570,   362 },
  /*30*/        {  819,   504,   819,   504,   504,   328,   504,   328 },
  /*31*/        {  745,   468,   745,   468,   468,   291,   468,   291 },
  /*32*/        {  630,   410,   630,   410,   410,   262,   410,   262 },
  /*33*/        {  585,   364,   585,   364,   364,   228,   364,   228 },
  /*34*/        {  512,   328,   512,   328,   328,   210,   328,   210 },
  /*35*/        {  455,   285,   455,   285,   285,   181,   285,   181 },
  /*36*/        {  410,   252,   410,   252,   252,   164,   252,   164 },
  /*37*/        {  372,   234,   372,   234,   234,   146,   234,   146 },
  /*38*/        {  315,   205,   315,   205,   205,   131,   205,   131 },
  /*39*/        {  293,   182,   293,   182,   182,   114,   182,   114 },
  /*40*/        {  256,   164,   256,   164,   164,   105,   164,   105 },
  /*41*/        {  228,   142,   228,   142,   142,    90,   142,    90 },
  /*42*/        {  205,   126,   205,   126,   126,    82,   126,    82 },
  /*43*/        {  186,   117,   186,   117,   117,    73,   117,    73 },
  /*44*/        {  158,   102,   158,   102,   102,    66,   102,    66 },
  /*45*/        {  146,    91,   146,    91,    91,    57,    91,    57 },
  /*46*/        {  128,    82,   128,    82,    82,    52,    82,    52 },
  /*47*/        {  114,    71,   114,    71,    71,    45,    71,    45 },
  /*48*/        {  102,    63,   102,    63,    63,    41,    63,    41 },
  /*49*/        {   93,    59,    93,    59,    59,    36,    59,    36 },
  /*50*/        {   79,    51,    79,    51,    51,    33,    51,    33 },
  /*51*/        {   73,    46,    73,    46,    46,    28,    46,    28 }
};

/****************************************************************************
 * HDM and Quant functions
 ****************************************************************************/
#define WELS_ABS_LC(a) ((iSign ^ (int32_t)(a)) - iSign)
#define NEW_QUANT(pDct, iFF, iMF) (((iFF)+ WELS_ABS_LC(pDct))*(iMF)) >>16
#define WELS_NEW_QUANT(pDct,iFF,iMF) WELS_ABS_LC(NEW_QUANT(pDct, iFF, iMF))
void WelsQuant4x4_c (int16_t* pDct, const int16_t* pFF,  const int16_t* pMF) {
  int32_t i, j, iSign;
  for (i = 0; i < 16; i += 4) {
    j = i & 0x07;
    iSign = WELS_SIGN (pDct[i]);
    pDct[i] = WELS_NEW_QUANT (pDct[i], pFF[j], pMF[j]);
    iSign = WELS_SIGN (pDct[i + 1]);
    pDct[i + 1] = WELS_NEW_QUANT (pDct[i + 1], pFF[j + 1], pMF[j + 1]);
    iSign = WELS_SIGN (pDct[i + 2]);
    pDct[i + 2] = WELS_NEW_QUANT (pDct[i + 2], pFF[j + 2], pMF[j + 2]);
    iSign = WELS_SIGN (pDct[i + 3]);
    pDct[i + 3] = WELS_NEW_QUANT (pDct[i + 3], pFF[j + 3], pMF[j + 3]);
  }
}

void WelsQuant4x4Dc_c (int16_t* pDct, int16_t iFF,  int16_t iMF) {
  int32_t i, iSign;
  for (i = 0; i < 16; i += 4) {
    iSign = WELS_SIGN (pDct[i]);
    pDct[i] = WELS_NEW_QUANT (pDct[i], iFF, iMF);
    iSign = WELS_SIGN (pDct[i + 1]);
    pDct[i + 1] = WELS_NEW_QUANT (pDct[i + 1], iFF, iMF);
    iSign = WELS_SIGN (pDct[i + 2]);
    pDct[i + 2] = WELS_NEW_QUANT (pDct[i + 2], iFF, iMF);
    iSign = WELS_SIGN (pDct[i + 3]);
    pDct[i + 3] = WELS_NEW_QUANT (pDct[i + 3], iFF, iMF);
  }
}

void WelsQuantFour4x4_c (int16_t* pDct, const int16_t* pFF, const int16_t* pMF) {
  int32_t i, j, iSign;

  for (i = 0; i < 64; i += 4) {
    j = i & 0x07;
    iSign = WELS_SIGN (pDct[i]);
    pDct[i] = WELS_NEW_QUANT (pDct[i], pFF[j], pMF[j]);
    iSign = WELS_SIGN (pDct[i + 1]);
    pDct[i + 1] = WELS_NEW_QUANT (pDct[i + 1], pFF[j + 1], pMF[j + 1]);
    iSign = WELS_SIGN (pDct[i + 2]);
    pDct[i + 2] = WELS_NEW_QUANT (pDct[i + 2], pFF[j + 2], pMF[j + 2]);
    iSign = WELS_SIGN (pDct[i + 3]);
    pDct[i + 3] = WELS_NEW_QUANT (pDct[i + 3], pFF[j + 3], pMF[j + 3]);
  }
}

void WelsQuantFour4x4Max_c (int16_t* pDct, const int16_t* pFF, const int16_t* pMF, int16_t* pMax) {
  int32_t i, j, k, iSign;
  int16_t iMaxAbs;
  for (k = 0; k < 4; k++) {
    iMaxAbs = 0;
    for (i = 0; i < 16; i++) {
      j = i & 0x07;
      iSign = WELS_SIGN (pDct[i]);
      pDct[i] = NEW_QUANT (pDct[i], pFF[j], pMF[j]);
      if (iMaxAbs < pDct[i]) iMaxAbs = pDct[i];
      pDct[i] = WELS_ABS_LC (pDct[i]);
    }
    pDct += 16;
    pMax[k] = iMaxAbs;
  }
}

int32_t WelsHadamardQuant2x2Skip_c (int16_t* pRs, int16_t iFF,  int16_t iMF) {
  int16_t pDct[4], s[4];
  int16_t iThreshold = ((1 << 16) - 1) / iMF - iFF;

  s[0] = pRs[0]  + pRs[32];
  s[1] = pRs[0]  - pRs[32];
  s[2] = pRs[16] + pRs[48];
  s[3] = pRs[16] - pRs[48];

  pDct[0] = s[0] + s[2];
  pDct[1] = s[0] - s[2];
  pDct[2] = s[1] + s[3];
  pDct[3] = s[1] - s[3];

  return ((WELS_ABS (pDct[0]) > iThreshold) || (WELS_ABS (pDct[1]) > iThreshold) || (WELS_ABS (pDct[2]) > iThreshold)
          || (WELS_ABS (pDct[3]) > iThreshold));
}

int32_t WelsHadamardQuant2x2_c (int16_t* pRs, const int16_t iFF, int16_t iMF, int16_t* pDct, int16_t* pBlock) {
  int16_t s[4];
  int32_t iSign, i, iDcNzc = 0;

  s[0] = pRs[0]  + pRs[32];
  s[1] = pRs[0]  - pRs[32];
  s[2] = pRs[16] + pRs[48];
  s[3] = pRs[16] - pRs[48];

  pRs[0] = 0;
  pRs[16] = 0;
  pRs[32] = 0;
  pRs[48] = 0;

  pDct[0] = s[0] + s[2];
  pDct[1] = s[0] - s[2];
  pDct[2] = s[1] + s[3];
  pDct[3] = s[1] - s[3];

  iSign = WELS_SIGN (pDct[0]);
  pDct[0] = WELS_NEW_QUANT (pDct[0], iFF, iMF);
  iSign = WELS_SIGN (pDct[1]);
  pDct[1] = WELS_NEW_QUANT (pDct[1], iFF, iMF);
  iSign = WELS_SIGN (pDct[2]);
  pDct[2] = WELS_NEW_QUANT (pDct[2], iFF, iMF);
  iSign = WELS_SIGN (pDct[3]);
  pDct[3] = WELS_NEW_QUANT (pDct[3], iFF, iMF);

  ST64 (pBlock, LD64 (pDct));

  for (i = 0; i < 4; i++)
    iDcNzc += (pBlock[i] != 0);
  return iDcNzc;
}

/* dc value pick up and hdm_4x4 */
void WelsHadamardT4Dc_c (int16_t* pLumaDc, int16_t* pDct) {
  int32_t p[16], s[4];
  int32_t i, iIdx;

  for (i = 0 ; i < 16 ; i += 4) {
    iIdx = ((i & 0x08) << 4) + ((i & 0x04) << 3);
    s[0] = pDct[iIdx ]     + pDct[iIdx + 80];
    s[3] = pDct[iIdx ]     - pDct[iIdx + 80];
    s[1] = pDct[iIdx + 16] + pDct[iIdx + 64];
    s[2] = pDct[iIdx + 16] - pDct[iIdx + 64];

    p[i  ]   = s[0] + s[1];
    p[i + 2] = s[0] - s[1];
    p[i + 1] = s[3] + s[2];
    p[i + 3] = s[3] - s[2];
  }

  for (i = 0 ; i < 4 ; i ++) {
    s[0] = p[i ]    + p[i + 12];
    s[3] = p[i ]    - p[i + 12];
    s[1] = p[i + 4] + p[i + 8];
    s[2] = p[i + 4] - p[i + 8];

    pLumaDc[i  ]    = WELS_CLIP3 ((s[0] + s[1] + 1) >> 1, -32768, 32767);
    pLumaDc[i + 8 ] = WELS_CLIP3 ((s[0] - s[1] + 1) >> 1, -32768, 32767);
    pLumaDc[i + 4 ] = WELS_CLIP3 ((s[3] + s[2] + 1) >> 1, -32768, 32767);
    pLumaDc[i + 12] = WELS_CLIP3 ((s[3] - s[2] + 1) >> 1, -32768, 32767);
  }
}

/****************************************************************************
 * DCT functions
 ****************************************************************************/
void WelsDctT4_c (int16_t* pDct, uint8_t* pPixel1, int32_t iStride1, uint8_t* pPixel2, int32_t iStride2) {
  int16_t i, pData[16], s[4];
  for (i = 0 ; i < 16 ; i += 4) {
    const int32_t kiI1 = 1 + i;
    const int32_t kiI2 = 2 + i;
    const int32_t kiI3 = 3 + i;

    pData[i ] = pPixel1[0] - pPixel2[0];
    pData[kiI1] = pPixel1[1] - pPixel2[1];
    pData[kiI2] = pPixel1[2] - pPixel2[2];
    pData[kiI3] = pPixel1[3] - pPixel2[3];

    pPixel1 += iStride1;
    pPixel2 += iStride2;

    /*horizontal transform */
    s[0] = pData[i] + pData[kiI3];
    s[3] = pData[i] - pData[kiI3];
    s[1] = pData[kiI1] + pData[kiI2];
    s[2] = pData[kiI1] - pData[kiI2];

    pDct[i ]   = s[0] + s[1];
    pDct[kiI2] = s[0] - s[1];
    pDct[kiI1] = (s[3] * (1 << 1)) + s[2];
    pDct[kiI3] = s[3] - (s[2] * (1 << 1));
  }

  /* vertical transform */
  for (i = 0 ; i < 4 ; i ++) {
    const int32_t kiI4  = 4 + i;
    const int32_t kiI8  = 8 + i;
    const int32_t kiI12 = 12 + i;

    s[0] = pDct[i ] + pDct[kiI12];
    s[3] = pDct[i ] - pDct[kiI12];
    s[1] = pDct[kiI4] + pDct[kiI8 ];
    s[2] = pDct[kiI4] - pDct[kiI8 ];

    pDct[i  ]   = s[0] + s[1];
    pDct[kiI8 ] = s[0] - s[1];
    pDct[kiI4 ] = (s[3] * (1 << 1)) + s[2];
    pDct[kiI12] = s[3] - (s[2] * (1 << 1));
  }
}

void WelsDctFourT4_c (int16_t* pDct, uint8_t* pPixel1, int32_t iStride1, uint8_t* pPixel2, int32_t iStride2) {
  int32_t stride_1 = iStride1 << 2;
  int32_t stride_2 = iStride2 << 2;

  WelsDctT4_c (pDct,      &pPixel1[0],            iStride1, &pPixel2[0],            iStride2);
  WelsDctT4_c (pDct + 16, &pPixel1[4],            iStride1, &pPixel2[4],            iStride2);
  WelsDctT4_c (pDct + 32, &pPixel1[stride_1    ], iStride1, &pPixel2[stride_2    ], iStride2);
  WelsDctT4_c (pDct + 48, &pPixel1[stride_1 + 4], iStride1, &pPixel2[stride_2 + 4], iStride2);
}

/****************************************************************************
 * Scan and Score functions
 ****************************************************************************/
void WelsScan4x4DcAc_c (int16_t* pLevel, int16_t* pDct) {
  ST32 (pLevel, LD32 (pDct));
  pLevel[2] = pDct[4];
  pLevel[3] = pDct[8];
  pLevel[4] = pDct[5];
  ST32 (pLevel + 5, LD32 (pDct + 2));
  pLevel[7] = pDct[6];
  pLevel[8] = pDct[9];
  ST32 (pLevel + 9, LD32 (pDct + 12));
  pLevel[11] = pDct[10];
  pLevel[12] = pDct[7];
  pLevel[13] = pDct[11];
  ST32 (pLevel + 14, LD32 (pDct + 14));
}

void WelsScan4x4Ac_c (int16_t* pLevel, int16_t* pDct) {
  pLevel[0]  = pDct[1];
  pLevel[1]  = pDct[4];
  pLevel[2]  = pDct[8];
  pLevel[3]  = pDct[5];
  ST32 (&pLevel[4], LD32 (&pDct[2]));
  pLevel[6]  = pDct[6];
  pLevel[7]  = pDct[9];
  ST32 (&pLevel[8], LD32 (&pDct[12]));
  pLevel[10] = pDct[10];
  pLevel[11] = pDct[7];
  pLevel[12] = pDct[11];
  ST32 (&pLevel[13], LD32 (&pDct[14]));
  pLevel[15] = 0;
}

void WelsScan4x4Dc (int16_t* pLevel, int16_t* pDct) {
  ST32 (pLevel, LD32 (pDct));
  pLevel[2] = pDct[4];
  pLevel[3] = pDct[8];
  pLevel[4] = pDct[5];
  ST32 (pLevel + 5, LD32 (pDct + 2));
  pLevel[7] = pDct[6];
  pLevel[8] = pDct[9];
  ST32 (pLevel + 9, LD32 (pDct + 12));
  pLevel[11] = pDct[10];
  pLevel[12] = pDct[7];
  pLevel[13] = pDct[11];
  ST32 (pLevel + 14, LD32 (pDct + 14));
}

//refer to JVT-O079
int32_t WelsCalculateSingleCtr4x4_c (int16_t* pDct) {
  static const int32_t kiTRunTable[16] = { 3, 2, 2, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

  int32_t iSingleCtr = 0;
  int32_t iIdx = 15;
  int32_t iRun;

  while (iIdx >= 0 && pDct[iIdx] == 0)      --iIdx;

  while (iIdx >= 0) {
    -- iIdx;
    iRun = iIdx;
    while (iIdx >= 0 && pDct[iIdx] == 0)  --iIdx;
    iRun -= iIdx;
    iSingleCtr += kiTRunTable[iRun];
  }
  return iSingleCtr;
}

int32_t WelsGetNoneZeroCount_c (int16_t* pLevel) {
  int32_t iCnt = 0;
  int32_t iIdx = 0;

  while (iIdx < 16) {
    iCnt += (pLevel[  iIdx] == 0);
    iCnt += (pLevel[1 + iIdx] == 0);
    iCnt += (pLevel[2 + iIdx] == 0);
    iCnt += (pLevel[3 + iIdx] == 0);

    iIdx += 4;
  }
  return (16 - iCnt);
}

#ifdef HAVE_NEON
int32_t WelsHadamardQuant2x2Skip_neon (int16_t* pRes, int16_t iFF,  int16_t iMF) {
  int16_t iThreshold = ((1 << 16) - 1) / iMF - iFF;
  return WelsHadamardQuant2x2SkipKernel_neon (pRes, iThreshold);
}
#endif
#ifdef HAVE_NEON_AARCH64
int32_t WelsHadamardQuant2x2Skip_AArch64_neon (int16_t* pRes, int16_t iFF,  int16_t iMF) {
  int16_t iThreshold = ((1 << 16) - 1) / iMF - iFF;
  return WelsHadamardQuant2x2SkipKernel_AArch64_neon (pRes, iThreshold);
}
#endif
void WelsInitEncodingFuncs (SWelsFuncPtrList* pFuncList, uint32_t  uiCpuFlag) {
  pFuncList->pfCopy8x8Aligned           = WelsCopy8x8_c;
  pFuncList->pfCopy16x16Aligned         =
  pFuncList->pfCopy16x16NotAligned      = WelsCopy16x16_c;
  pFuncList->pfCopy16x8NotAligned       = WelsCopy16x8_c;
  pFuncList->pfCopy8x16Aligned          = WelsCopy8x16_c;
  pFuncList->pfCopy4x4           = WelsCopy4x4_c;
  pFuncList->pfCopy8x4           = WelsCopy8x4_c;
  pFuncList->pfCopy4x8           = WelsCopy4x8_c;
  pFuncList->pfQuantizationHadamard2x2          = WelsHadamardQuant2x2_c;
  pFuncList->pfQuantizationHadamard2x2Skip      = WelsHadamardQuant2x2Skip_c;
  pFuncList->pfTransformHadamard4x4Dc           = WelsHadamardT4Dc_c;

  pFuncList->pfDctT4                    = WelsDctT4_c;
  pFuncList->pfDctFourT4                = WelsDctFourT4_c;

  pFuncList->pfScan4x4                  = WelsScan4x4DcAc_c;
  pFuncList->pfScan4x4Ac                = WelsScan4x4Ac_c;
  pFuncList->pfCalculateSingleCtr4x4    = WelsCalculateSingleCtr4x4_c;

  pFuncList->pfGetNoneZeroCount         = WelsGetNoneZeroCount_c;

  pFuncList->pfQuantization4x4          = WelsQuant4x4_c;
  pFuncList->pfQuantizationDc4x4        = WelsQuant4x4Dc_c;
  pFuncList->pfQuantizationFour4x4      = WelsQuantFour4x4_c;
  pFuncList->pfQuantizationFour4x4Max   = WelsQuantFour4x4Max_c;

#if defined(X86_ASM)
  if (uiCpuFlag & WELS_CPU_MMXEXT) {

    pFuncList->pfQuantizationHadamard2x2        = WelsHadamardQuant2x2_mmx;
    pFuncList->pfQuantizationHadamard2x2Skip    = WelsHadamardQuant2x2Skip_mmx;

    pFuncList->pfDctT4                  = WelsDctT4_mmx;

    pFuncList->pfCopy8x8Aligned         = WelsCopy8x8_mmx;
    pFuncList->pfCopy8x16Aligned        = WelsCopy8x16_mmx;
  }
  if (uiCpuFlag & WELS_CPU_SSE2) {
    pFuncList->pfGetNoneZeroCount       = WelsGetNoneZeroCount_sse2;
    pFuncList->pfTransformHadamard4x4Dc = WelsHadamardT4Dc_sse2;

    pFuncList->pfQuantization4x4        = WelsQuant4x4_sse2;
    pFuncList->pfQuantizationDc4x4      = WelsQuant4x4Dc_sse2;
    pFuncList->pfQuantizationFour4x4    = WelsQuantFour4x4_sse2;
    pFuncList->pfQuantizationFour4x4Max = WelsQuantFour4x4Max_sse2;

    pFuncList->pfCopy16x16Aligned       = WelsCopy16x16_sse2;
    pFuncList->pfCopy16x16NotAligned    = WelsCopy16x16NotAligned_sse2;
    pFuncList->pfCopy16x8NotAligned     = WelsCopy16x8NotAligned_sse2;

    pFuncList->pfScan4x4                = WelsScan4x4DcAc_sse2;
    pFuncList->pfScan4x4Ac              = WelsScan4x4Ac_sse2;
    pFuncList->pfCalculateSingleCtr4x4  = WelsCalculateSingleCtr4x4_sse2;

    pFuncList->pfDctT4                  = WelsDctT4_sse2;
    pFuncList->pfDctFourT4              = WelsDctFourT4_sse2;
  }
//#ifndef MACOS
  if (uiCpuFlag & WELS_CPU_SSSE3) {
    pFuncList->pfScan4x4                = WelsScan4x4DcAc_ssse3;
  }
  if (uiCpuFlag & WELS_CPU_SSE42) {
    pFuncList->pfGetNoneZeroCount       = WelsGetNoneZeroCount_sse42;
  }
#if defined(HAVE_AVX2)
  if (uiCpuFlag & WELS_CPU_AVX2) {
    pFuncList->pfDctT4                  = WelsDctT4_avx2;
    pFuncList->pfDctFourT4              = WelsDctFourT4_avx2;

    pFuncList->pfQuantization4x4        = WelsQuant4x4_avx2;
    pFuncList->pfQuantizationDc4x4      = WelsQuant4x4Dc_avx2;
    pFuncList->pfQuantizationFour4x4    = WelsQuantFour4x4_avx2;
    pFuncList->pfQuantizationFour4x4Max = WelsQuantFour4x4Max_avx2;
  }
#endif
//#endif//MACOS

#endif//X86_ASM

#if defined(HAVE_NEON)
  if (uiCpuFlag & WELS_CPU_NEON) {
    pFuncList->pfQuantizationHadamard2x2        = WelsHadamardQuant2x2_neon;
    pFuncList->pfQuantizationHadamard2x2Skip    = WelsHadamardQuant2x2Skip_neon;
    pFuncList->pfDctT4                          = WelsDctT4_neon;
    pFuncList->pfCopy8x8Aligned                 = WelsCopy8x8_neon;
    pFuncList->pfCopy8x16Aligned                = WelsCopy8x16_neon;

    pFuncList->pfGetNoneZeroCount       = WelsGetNoneZeroCount_neon;
    pFuncList->pfTransformHadamard4x4Dc = WelsHadamardT4Dc_neon;

    pFuncList->pfQuantization4x4        = WelsQuant4x4_neon;
    pFuncList->pfQuantizationDc4x4      = WelsQuant4x4Dc_neon;
    pFuncList->pfQuantizationFour4x4    = WelsQuantFour4x4_neon;
    pFuncList->pfQuantizationFour4x4Max = WelsQuantFour4x4Max_neon;

    pFuncList->pfCopy16x16Aligned       = WelsCopy16x16_neon;
    pFuncList->pfCopy16x16NotAligned    = WelsCopy16x16NotAligned_neon;
    pFuncList->pfCopy16x8NotAligned     = WelsCopy16x8NotAligned_neon;
    pFuncList->pfDctFourT4              = WelsDctFourT4_neon;
  }
#endif

#if defined(HAVE_NEON_AARCH64)
  if (uiCpuFlag & WELS_CPU_NEON) {
    pFuncList->pfQuantizationHadamard2x2        = WelsHadamardQuant2x2_AArch64_neon;
    pFuncList->pfQuantizationHadamard2x2Skip    = WelsHadamardQuant2x2Skip_AArch64_neon;
    pFuncList->pfDctT4                          = WelsDctT4_AArch64_neon;
    pFuncList->pfCopy8x8Aligned                 = WelsCopy8x8_AArch64_neon;
    pFuncList->pfCopy8x16Aligned                = WelsCopy8x16_AArch64_neon;

    pFuncList->pfGetNoneZeroCount       = WelsGetNoneZeroCount_AArch64_neon;
    pFuncList->pfTransformHadamard4x4Dc = WelsHadamardT4Dc_AArch64_neon;

    pFuncList->pfQuantization4x4        = WelsQuant4x4_AArch64_neon;
    pFuncList->pfQuantizationDc4x4      = WelsQuant4x4Dc_AArch64_neon;
    pFuncList->pfQuantizationFour4x4    = WelsQuantFour4x4_AArch64_neon;
    pFuncList->pfQuantizationFour4x4Max = WelsQuantFour4x4Max_AArch64_neon;

    pFuncList->pfCopy16x16Aligned       = WelsCopy16x16_AArch64_neon;
    pFuncList->pfCopy16x16NotAligned    = WelsCopy16x16NotAligned_AArch64_neon;
    pFuncList->pfCopy16x8NotAligned     = WelsCopy16x8NotAligned_AArch64_neon;
    pFuncList->pfDctFourT4              = WelsDctFourT4_AArch64_neon;
  }
#endif

#if defined(HAVE_MMI)
  if (uiCpuFlag & WELS_CPU_MMI) {
    pFuncList->pfCopy8x8Aligned         = WelsCopy8x8_mmi;
    pFuncList->pfCopy8x16Aligned        = WelsCopy8x16_mmi;

    pFuncList->pfGetNoneZeroCount       = WelsGetNoneZeroCount_mmi;
    pFuncList->pfTransformHadamard4x4Dc = WelsHadamardT4Dc_mmi;

    pFuncList->pfQuantization4x4        = WelsQuant4x4_mmi;
    pFuncList->pfQuantizationDc4x4      = WelsQuant4x4Dc_mmi;
    pFuncList->pfQuantizationFour4x4    = WelsQuantFour4x4_mmi;
    pFuncList->pfQuantizationFour4x4Max = WelsQuantFour4x4Max_mmi;

    pFuncList->pfCopy16x16Aligned       = WelsCopy16x16_mmi;
    pFuncList->pfCopy16x16NotAligned    = WelsCopy16x16NotAligned_mmi;
    pFuncList->pfCopy16x8NotAligned     = WelsCopy16x8NotAligned_mmi;

    pFuncList->pfScan4x4                = WelsScan4x4DcAc_mmi;
    pFuncList->pfScan4x4Ac              = WelsScan4x4Ac_mmi;
    pFuncList->pfCalculateSingleCtr4x4  = WelsCalculateSingleCtr4x4_mmi;

    pFuncList->pfDctT4                  = WelsDctT4_mmi;
    pFuncList->pfDctFourT4              = WelsDctFourT4_mmi;
  }
#endif//HAVE_MMI

#if defined(HAVE_MSA)
  if (uiCpuFlag & WELS_CPU_MSA) {
    pFuncList->pfCopy8x8Aligned         = WelsCopy8x8_msa;
    pFuncList->pfCopy8x16Aligned        = WelsCopy8x16_msa;

    pFuncList->pfCopy16x16Aligned       =
    pFuncList->pfCopy16x16NotAligned    = WelsCopy16x16_msa;
    pFuncList->pfCopy16x8NotAligned     = WelsCopy16x8_msa;
  }
#endif
}
}
