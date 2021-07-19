/*!
 * \copy
 *     Copyright (c)  2009-2013, Cisco Systems
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
 *
 * \file    mc.c
 *
 * \brief   Interfaces implementation for motion compensation
 *
 * \date    03/17/2009 Created
 *
 *************************************************************************************
 */

#include "mc.h"

#include "cpu_core.h"
#include "ls_defines.h"
#include "macros.h"
#include "asmdefs_mmi.h"

namespace {

typedef void (*PMcChromaWidthExtFunc) (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                       const uint8_t* kpABCD, int32_t iHeight);
typedef void (*PWelsSampleWidthAveragingFunc) (uint8_t*, int32_t, const uint8_t*, int32_t, const uint8_t*,
    int32_t, int32_t);
typedef void (*PWelsMcWidthHeightFunc) (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                        int32_t iWidth, int32_t iHeight);

/*------------------weight for chroma fraction pixel interpolation------------------*/
//iA = (8 - dx) * (8 - dy);
//iB = dx * (8 - dy);
//iC = (8 - dx) * dy;
//iD = dx * dy
static const uint8_t g_kuiABCD[8][8][4] = { //g_kA[dy][dx], g_kB[dy][dx], g_kC[dy][dx], g_kD[dy][dx]
  {
    {64, 0, 0, 0}, {56, 8, 0, 0}, {48, 16, 0, 0}, {40, 24, 0, 0},
    {32, 32, 0, 0}, {24, 40, 0, 0}, {16, 48, 0, 0}, {8, 56, 0, 0}
  },
  {
    {56, 0, 8, 0}, {49, 7, 7, 1}, {42, 14, 6, 2}, {35, 21, 5, 3},
    {28, 28, 4, 4}, {21, 35, 3, 5}, {14, 42, 2, 6}, {7, 49, 1, 7}
  },
  {
    {48, 0, 16, 0}, {42, 6, 14, 2}, {36, 12, 12, 4}, {30, 18, 10, 6},
    {24, 24, 8, 8}, {18, 30, 6, 10}, {12, 36, 4, 12}, {6, 42, 2, 14}
  },
  {
    {40, 0, 24, 0}, {35, 5, 21, 3}, {30, 10, 18, 6}, {25, 15, 15, 9},
    {20, 20, 12, 12}, {15, 25, 9, 15}, {10, 30, 6, 18}, {5, 35, 3, 21}
  },
  {
    {32, 0, 32, 0}, {28, 4, 28, 4}, {24, 8, 24, 8}, {20, 12, 20, 12},
    {16, 16, 16, 16}, {12, 20, 12, 20}, {8, 24, 8, 24}, {4, 28, 4, 28}
  },
  {
    {24, 0, 40, 0}, {21, 3, 35, 5}, {18, 6, 30, 10}, {15, 9, 25, 15},
    {12, 12, 20, 20}, {9, 15, 15, 25}, {6, 18, 10, 30}, {3, 21, 5, 35}
  },
  {
    {16, 0, 48, 0}, {14, 2, 42, 6}, {12, 4, 36, 12}, {10, 6, 30, 18},
    {8, 8, 24, 24}, {6, 10, 18, 30}, {4, 12, 12, 36}, {2, 14, 6, 42}
  },
  {
    {8, 0, 56, 0}, {7, 1, 49, 7}, {6, 2, 42, 14}, {5, 3, 35, 21},
    {4, 4, 28, 28}, {3, 5, 21, 35}, {2, 6, 14, 42}, {1, 7, 7, 49}
  }
};

//***************************************************************************//
//                          C code implementation                            //
//***************************************************************************//
static inline void McCopyWidthEq2_c (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                     int32_t iHeight) {
  int32_t i;
  for (i = 0; i < iHeight; i++) { // iWidth == 2 only for chroma
    ST16A2 (pDst, LD16 (pSrc));
    pDst += iDstStride;
    pSrc += iSrcStride;
  }
}

static inline void McCopyWidthEq4_c (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                     int32_t iHeight) {
  int32_t i;
  for (i = 0; i < iHeight; i++) {
    ST32A4 (pDst, LD32 (pSrc));
    pDst += iDstStride;
    pSrc += iSrcStride;
  }
}

static inline void McCopyWidthEq8_c (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                     int32_t iHeight) {
  int32_t i;
  for (i = 0; i < iHeight; i++) {
    ST64A8 (pDst, LD64 (pSrc));
    pDst += iDstStride;
    pSrc += iSrcStride;
  }
}

static inline void McCopyWidthEq16_c (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                      int32_t iHeight) {
  int32_t i;
  for (i = 0; i < iHeight; i++) {
    ST64A8 (pDst  , LD64 (pSrc));
    ST64A8 (pDst + 8, LD64 (pSrc + 8));
    pDst += iDstStride;
    pSrc += iSrcStride;
  }
}

//--------------------Luma sample MC------------------//

static inline int32_t HorFilterInput16bit_c (const int16_t* pSrc) {
  int32_t iPix05 = pSrc[0] + pSrc[5];
  int32_t iPix14 = pSrc[1] + pSrc[4];
  int32_t iPix23 = pSrc[2] + pSrc[3];

  return (iPix05 - (iPix14 * 5) + (iPix23 * 20));
}
// h: iOffset=1 / v: iOffset=iSrcStride
static inline int32_t FilterInput8bitWithStride_c (const uint8_t* pSrc, const int32_t kiOffset) {
  const int32_t kiOffset1 = kiOffset;
  const int32_t kiOffset2 = (kiOffset << 1);
  const int32_t kiOffset3 = kiOffset + kiOffset2;
  const uint32_t kuiPix05   = * (pSrc - kiOffset2) + * (pSrc + kiOffset3);
  const uint32_t kuiPix14   = * (pSrc - kiOffset1) + * (pSrc + kiOffset2);
  const uint32_t kuiPix23   = * (pSrc) + * (pSrc + kiOffset1);

  return (kuiPix05 - ((kuiPix14 << 2) + kuiPix14) + (kuiPix23 << 4) + (kuiPix23 << 2));
}

static inline void PixelAvg_c (uint8_t* pDst, int32_t iDstStride, const uint8_t* pSrcA, int32_t iSrcAStride,
                               const uint8_t* pSrcB, int32_t iSrcBStride, int32_t iWidth, int32_t iHeight) {
  int32_t i, j;
  for (i = 0; i < iHeight; i++) {
    for (j = 0; j < iWidth; j++) {
      pDst[j] = (pSrcA[j] + pSrcB[j] + 1) >> 1;
    }
    pDst  += iDstStride;
    pSrcA += iSrcAStride;
    pSrcB += iSrcBStride;
  }
}
static inline void McCopy_c (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride, int32_t iWidth,
                             int32_t iHeight) {
  if (iWidth == 16)
    McCopyWidthEq16_c (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McCopyWidthEq8_c (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 4)
    McCopyWidthEq4_c (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else //here iWidth == 2
    McCopyWidthEq2_c (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}

//horizontal filter to gain half sample, that is (2, 0) location in quarter sample
static inline void McHorVer20_c (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                 int32_t iWidth,
                                 int32_t iHeight) {
  int32_t i, j;
  for (i = 0; i < iHeight; i++) {
    for (j = 0; j < iWidth; j++) {
      pDst[j] = WelsClip1 ((FilterInput8bitWithStride_c (pSrc + j, 1) + 16) >> 5);
    }
    pDst += iDstStride;
    pSrc += iSrcStride;
  }
}

//vertical filter to gain half sample, that is (0, 2) location in quarter sample
static inline void McHorVer02_c (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                 int32_t iWidth,
                                 int32_t iHeight) {
  int32_t i, j;
  for (i = 0; i < iHeight; i++) {
    for (j = 0; j < iWidth; j++) {
      pDst[j] = WelsClip1 ((FilterInput8bitWithStride_c (pSrc + j, iSrcStride) + 16) >> 5);
    }
    pDst += iDstStride;
    pSrc += iSrcStride;
  }
}

//horizontal and vertical filter to gain half sample, that is (2, 2) location in quarter sample
static inline void McHorVer22_c (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                 int32_t iWidth,
                                 int32_t iHeight) {
  int16_t iTmp[17 + 5];
  int32_t i, j, k;

  for (i = 0; i < iHeight; i++) {
    for (j = 0; j < iWidth + 5; j++) {
      iTmp[j] = FilterInput8bitWithStride_c (pSrc - 2 + j, iSrcStride);
    }
    for (k = 0; k < iWidth; k++) {
      pDst[k] = WelsClip1 ((HorFilterInput16bit_c (&iTmp[k]) + 512) >> 10);
    }
    pSrc += iSrcStride;
    pDst += iDstStride;
  }
}

/////////////////////luma MC//////////////////////////
static inline void McHorVer01_c (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                 int32_t iWidth,
                                 int32_t iHeight) {
  uint8_t uiTmp[256];
  McHorVer02_c (pSrc, iSrcStride, uiTmp, 16, iWidth, iHeight);
  PixelAvg_c (pDst, iDstStride, pSrc, iSrcStride, uiTmp, 16, iWidth, iHeight);
}
static inline void McHorVer03_c (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                 int32_t iWidth,
                                 int32_t iHeight) {
  uint8_t uiTmp[256];
  McHorVer02_c (pSrc, iSrcStride, uiTmp, 16, iWidth, iHeight);
  PixelAvg_c (pDst, iDstStride, pSrc + iSrcStride, iSrcStride, uiTmp, 16, iWidth, iHeight);
}
static inline void McHorVer10_c (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                 int32_t iWidth,
                                 int32_t iHeight) {
  uint8_t uiTmp[256];
  McHorVer20_c (pSrc, iSrcStride, uiTmp, 16, iWidth, iHeight);
  PixelAvg_c (pDst, iDstStride, pSrc, iSrcStride, uiTmp, 16, iWidth, iHeight);
}
static inline void McHorVer11_c (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                 int32_t iWidth,
                                 int32_t iHeight) {
  uint8_t uiHorTmp[256];
  uint8_t uiVerTmp[256];
  McHorVer20_c (pSrc, iSrcStride, uiHorTmp, 16, iWidth, iHeight);
  McHorVer02_c (pSrc, iSrcStride, uiVerTmp, 16, iWidth, iHeight);
  PixelAvg_c (pDst, iDstStride, uiHorTmp, 16, uiVerTmp, 16, iWidth, iHeight);
}
static inline void McHorVer12_c (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                 int32_t iWidth,
                                 int32_t iHeight) {
  uint8_t uiVerTmp[256];
  uint8_t uiCtrTmp[256];
  McHorVer02_c (pSrc, iSrcStride, uiVerTmp, 16, iWidth, iHeight);
  McHorVer22_c (pSrc, iSrcStride, uiCtrTmp, 16, iWidth, iHeight);
  PixelAvg_c (pDst, iDstStride, uiVerTmp, 16, uiCtrTmp, 16, iWidth, iHeight);
}
static inline void McHorVer13_c (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                 int32_t iWidth,
                                 int32_t iHeight) {
  uint8_t uiHorTmp[256];
  uint8_t uiVerTmp[256];
  McHorVer20_c (pSrc + iSrcStride, iSrcStride, uiHorTmp, 16, iWidth, iHeight);
  McHorVer02_c (pSrc, iSrcStride, uiVerTmp, 16, iWidth, iHeight);
  PixelAvg_c (pDst, iDstStride, uiHorTmp, 16, uiVerTmp, 16, iWidth, iHeight);
}
static inline void McHorVer21_c (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                 int32_t iWidth,
                                 int32_t iHeight) {
  uint8_t uiHorTmp[256];
  uint8_t uiCtrTmp[256];
  McHorVer20_c (pSrc, iSrcStride, uiHorTmp, 16, iWidth, iHeight);
  McHorVer22_c (pSrc, iSrcStride, uiCtrTmp, 16, iWidth, iHeight);
  PixelAvg_c (pDst, iDstStride, uiHorTmp, 16, uiCtrTmp, 16, iWidth, iHeight);
}
static inline void McHorVer23_c (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                 int32_t iWidth,
                                 int32_t iHeight) {
  uint8_t uiHorTmp[256];
  uint8_t uiCtrTmp[256];
  McHorVer20_c (pSrc + iSrcStride, iSrcStride, uiHorTmp, 16, iWidth, iHeight);
  McHorVer22_c (pSrc, iSrcStride, uiCtrTmp, 16, iWidth, iHeight);
  PixelAvg_c (pDst, iDstStride, uiHorTmp, 16, uiCtrTmp, 16, iWidth, iHeight);
}
static inline void McHorVer30_c (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                 int32_t iWidth,
                                 int32_t iHeight) {
  uint8_t uiHorTmp[256];
  McHorVer20_c (pSrc, iSrcStride, uiHorTmp, 16, iWidth, iHeight);
  PixelAvg_c (pDst, iDstStride, pSrc + 1, iSrcStride, uiHorTmp, 16, iWidth, iHeight);
}
static inline void McHorVer31_c (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                 int32_t iWidth,
                                 int32_t iHeight) {
  uint8_t uiHorTmp[256];
  uint8_t uiVerTmp[256];
  McHorVer20_c (pSrc, iSrcStride, uiHorTmp, 16, iWidth, iHeight);
  McHorVer02_c (pSrc + 1, iSrcStride, uiVerTmp, 16, iWidth, iHeight);
  PixelAvg_c (pDst, iDstStride, uiHorTmp, 16, uiVerTmp, 16, iWidth, iHeight);
}
static inline void McHorVer32_c (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                 int32_t iWidth,
                                 int32_t iHeight) {
  uint8_t uiVerTmp[256];
  uint8_t uiCtrTmp[256];
  McHorVer02_c (pSrc + 1, iSrcStride, uiVerTmp, 16, iWidth, iHeight);
  McHorVer22_c (pSrc, iSrcStride, uiCtrTmp, 16, iWidth, iHeight);
  PixelAvg_c (pDst, iDstStride, uiVerTmp, 16, uiCtrTmp, 16, iWidth, iHeight);
}
static inline void McHorVer33_c (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                 int32_t iWidth,
                                 int32_t iHeight) {
  uint8_t uiHorTmp[256];
  uint8_t uiVerTmp[256];
  McHorVer20_c (pSrc + iSrcStride, iSrcStride, uiHorTmp, 16, iWidth, iHeight);
  McHorVer02_c (pSrc + 1, iSrcStride, uiVerTmp, 16, iWidth, iHeight);
  PixelAvg_c (pDst, iDstStride, uiHorTmp, 16, uiVerTmp, 16, iWidth, iHeight);
}

void McLuma_c (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
               int16_t iMvX, int16_t iMvY, int32_t iWidth, int32_t iHeight)
//pSrc has been added the offset of mv
{
  static const PWelsMcWidthHeightFunc pWelsMcFunc[4][4] = { //[x][y]
    {McCopy_c,      McHorVer01_c, McHorVer02_c, McHorVer03_c},
    {McHorVer10_c,  McHorVer11_c, McHorVer12_c, McHorVer13_c},
    {McHorVer20_c,  McHorVer21_c, McHorVer22_c, McHorVer23_c},
    {McHorVer30_c,  McHorVer31_c, McHorVer32_c, McHorVer33_c},
  };

  pWelsMcFunc[iMvX & 0x03][iMvY & 0x03] (pSrc, iSrcStride, pDst, iDstStride, iWidth, iHeight);
}

static inline void McChromaWithFragMv_c (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
    int16_t iMvX, int16_t iMvY, int32_t iWidth, int32_t iHeight) {
  int32_t i, j;
  int32_t iA, iB, iC, iD;
  const uint8_t* pSrcNext = pSrc + iSrcStride;
  const uint8_t* pABCD = g_kuiABCD[iMvY & 0x07][iMvX & 0x07];
  iA = pABCD[0];
  iB = pABCD[1];
  iC = pABCD[2];
  iD = pABCD[3];
  for (i = 0; i < iHeight; i++) {
    for (j = 0; j < iWidth; j++) {
      pDst[j] = (iA * pSrc[j] + iB * pSrc[j + 1] + iC * pSrcNext[j] + iD * pSrcNext[j + 1] + 32) >> 6;
    }
    pDst     += iDstStride;
    pSrc      = pSrcNext;
    pSrcNext += iSrcStride;
  }
}

void McChroma_c (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                 int16_t iMvX, int16_t iMvY, int32_t iWidth, int32_t iHeight)
//pSrc has been added the offset of mv
{
  const int32_t kiD8x = iMvX & 0x07;
  const int32_t kiD8y = iMvY & 0x07;
  if (0 == kiD8x && 0 == kiD8y)
    McCopy_c (pSrc, iSrcStride, pDst, iDstStride, iWidth, iHeight);
  else
    McChromaWithFragMv_c (pSrc, iSrcStride, pDst, iDstStride, iMvX, iMvY, iWidth, iHeight);
}

#if defined(X86_ASM)
//***************************************************************************//
//                       SSE2 implement                          //
//***************************************************************************//
static inline void McHorVer22WidthEq8_sse2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
    int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (int16_t, iTap, 21, 8, 16)
  McHorVer22Width8HorFirst_sse2 (pSrc - 2, iSrcStride, (uint8_t*)iTap, 16, iHeight + 5);
  McHorVer22Width8VerLastAlign_sse2 ((uint8_t*)iTap, 16, pDst, iDstStride, 8, iHeight);
}

static inline void McHorVer02WidthEq16_sse2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
    int32_t iHeight) {
  McHorVer02WidthEq8_sse2 (pSrc,     iSrcStride, pDst,     iDstStride, iHeight);
  McHorVer02WidthEq8_sse2 (&pSrc[8], iSrcStride, &pDst[8], iDstStride, iHeight);
}

static inline void McHorVer22WidthEq16_sse2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
    int32_t iHeight) {
  McHorVer22WidthEq8_sse2 (pSrc,     iSrcStride, pDst,     iDstStride, iHeight);
  McHorVer22WidthEq8_sse2 (&pSrc[8], iSrcStride, &pDst[8], iDstStride, iHeight);
}

void McHorVer20Width5Or9Or17_sse2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
        int32_t iWidth, int32_t iHeight) {
    if (iWidth == 17 || iWidth == 9)
        McHorVer20Width9Or17_sse2 (pSrc, iSrcStride, pDst, iDstStride, iWidth, iHeight);
    else //if (iWidth == 5)
        McHorVer20Width5_sse2 (pSrc, iSrcStride, pDst, iDstStride, iWidth, iHeight);
}

void McHorVer02Height5Or9Or17_sse2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
        int32_t iWidth, int32_t iHeight) {
    if (iWidth == 16 || iWidth == 8)
        McHorVer02Height9Or17_sse2 (pSrc, iSrcStride, pDst, iDstStride, iWidth, iHeight);
    else //if (iWidth == 4)
        McHorVer02Height5_sse2 (pSrc, iSrcStride, pDst, iDstStride, iWidth, iHeight);
}

void McHorVer22Width5Or9Or17Height5Or9Or17_sse2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
        int32_t iWidth, int32_t iHeight) {
    ENFORCE_STACK_ALIGN_2D (int16_t, pTap, 22, 24, 16)
    if (iWidth == 17 || iWidth == 9){
        int32_t tmp1 = 2 * (iWidth - 8);
        McHorVer22HorFirst_sse2 (pSrc - 2, iSrcStride, (uint8_t*)pTap, 48, iWidth, iHeight + 5);
        McHorVer22Width8VerLastAlign_sse2 ((uint8_t*)pTap,  48, pDst, iDstStride, iWidth - 1, iHeight);
        McHorVer22Width8VerLastUnAlign_sse2 ((uint8_t*)pTap + tmp1,  48, pDst + iWidth - 8, iDstStride, 8, iHeight);
    }
    else{ //if(iWidth == 5)
        int32_t tmp1 = 2 * (iWidth - 4);
        McHorVer22Width5HorFirst_sse2 (pSrc - 2, iSrcStride, (uint8_t*)pTap, 48, iWidth, iHeight + 5);
        McHorVer22Width4VerLastAlign_sse2 ((uint8_t*)pTap,  48, pDst, iDstStride, iWidth - 1, iHeight);
        McHorVer22Width4VerLastUnAlign_sse2 ((uint8_t*)pTap + tmp1,  48, pDst + iWidth - 4, iDstStride, 4, iHeight);
    }

}

static inline void McCopy_sse2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                int32_t iWidth,
                                int32_t iHeight) {
  if (iWidth == 16)
    McCopyWidthEq16_sse2 (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McCopyWidthEq8_mmx (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 4)
    McCopyWidthEq4_c (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else
    McCopyWidthEq2_c (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}

static inline void McHorVer20_sse2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                    int32_t iWidth, int32_t iHeight) {
  if (iWidth == 16)
    McHorVer20WidthEq16_sse2 (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McHorVer20WidthEq8_sse2 (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else
    McHorVer20WidthEq4_mmx (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}

static inline void McHorVer02_sse2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                    int32_t iWidth, int32_t iHeight) {
  if (iWidth == 16)
    McHorVer02WidthEq16_sse2 (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McHorVer02WidthEq8_sse2 (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else
    McHorVer02_c (pSrc, iSrcStride, pDst, iDstStride, 4, iHeight);
}

static inline void McHorVer22_sse2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                    int32_t iWidth, int32_t iHeight) {
  if (iWidth == 16)
    McHorVer22WidthEq16_sse2 (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McHorVer22WidthEq8_sse2 (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else
    McHorVer22_c (pSrc, iSrcStride, pDst, iDstStride, 4, iHeight);
}

static inline void McHorVer01_sse2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                    int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer02WidthEq16_sse2 (pSrc, iSrcStride, pTmp, 16, iHeight);
    PixelAvgWidthEq16_sse2 (pDst, iDstStride, pSrc, iSrcStride, pTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer02WidthEq8_sse2 (pSrc, iSrcStride, pTmp, 16, iHeight);
    PixelAvgWidthEq8_mmx (pDst, iDstStride, pSrc, iSrcStride, pTmp, 16, iHeight);
  } else {
    McHorVer02_c (pSrc, iSrcStride, pTmp, 16, 4, iHeight);
    PixelAvgWidthEq4_mmx (pDst, iDstStride, pSrc, iSrcStride, pTmp, 16, iHeight);
  }
}
static inline void McHorVer03_sse2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                    int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer02WidthEq16_sse2 (pSrc, iSrcStride, pTmp, 16, iHeight);
    PixelAvgWidthEq16_sse2 (pDst, iDstStride, pSrc + iSrcStride, iSrcStride, pTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer02WidthEq8_sse2 (pSrc, iSrcStride, pTmp, 16, iHeight);
    PixelAvgWidthEq8_mmx (pDst, iDstStride, pSrc + iSrcStride, iSrcStride, pTmp, 16, iHeight);
  } else {
    McHorVer02_c (pSrc, iSrcStride, pTmp, 16, 4, iHeight);
    PixelAvgWidthEq4_mmx (pDst, iDstStride, pSrc + iSrcStride, iSrcStride, pTmp, 16, iHeight);
  }
}
static inline void McHorVer10_sse2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                    int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_sse2 (pSrc, iSrcStride, pTmp, 16, iHeight);
    PixelAvgWidthEq16_sse2 (pDst, iDstStride, pSrc, iSrcStride, pTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_sse2 (pSrc, iSrcStride, pTmp, 16, iHeight);
    PixelAvgWidthEq8_mmx (pDst, iDstStride, pSrc, iSrcStride, pTmp, 16, iHeight);
  } else {
    McHorVer20WidthEq4_mmx (pSrc, iSrcStride, pTmp, 16, iHeight);
    PixelAvgWidthEq4_mmx (pDst, iDstStride, pSrc, iSrcStride, pTmp, 16, iHeight);
  }
}
static inline void McHorVer11_sse2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                    int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pVerTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_sse2 (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq16_sse2 (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq16_sse2 (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_sse2 (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq8_sse2 (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq8_mmx (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  } else {
    McHorVer20WidthEq4_mmx (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02_c (pSrc, iSrcStride, pVerTmp, 16, 4, iHeight);
    PixelAvgWidthEq4_mmx (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  }
}
static inline void McHorVer12_sse2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                    int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pVerTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pCtrTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer02WidthEq16_sse2 (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    McHorVer22WidthEq16_sse2 (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq16_sse2 (pDst, iDstStride, pVerTmp, 16, pCtrTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer02WidthEq8_sse2 (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    McHorVer22WidthEq8_sse2 (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq8_mmx (pDst, iDstStride, pVerTmp, 16, pCtrTmp, 16, iHeight);
  } else {
    McHorVer02_c (pSrc, iSrcStride, pVerTmp, 16, 4, iHeight);
    McHorVer22_c (pSrc, iSrcStride, pCtrTmp, 16, 4, iHeight);
    PixelAvgWidthEq4_mmx (pDst, iDstStride, pVerTmp, 16, pCtrTmp, 16, iHeight);
  }
}
static inline void McHorVer13_sse2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                    int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pVerTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_sse2 (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq16_sse2 (pSrc,            iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq16_sse2 (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_sse2 (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq8_sse2 (pSrc,            iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq8_mmx (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  } else {
    McHorVer20WidthEq4_mmx (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02_c (pSrc,            iSrcStride, pVerTmp, 16, 4 , iHeight);
    PixelAvgWidthEq4_mmx (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  }
}
static inline void McHorVer21_sse2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                    int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pCtrTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_sse2 (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer22WidthEq16_sse2 (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq16_sse2 (pDst, iDstStride, pHorTmp, 16, pCtrTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_sse2 (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer22WidthEq8_sse2 (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq8_mmx (pDst, iDstStride, pHorTmp, 16, pCtrTmp, 16, iHeight);
  } else {
    McHorVer20WidthEq4_mmx (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer22_c (pSrc, iSrcStride, pCtrTmp, 16, 4, iHeight);
    PixelAvgWidthEq4_mmx (pDst, iDstStride, pHorTmp, 16, pCtrTmp, 16, iHeight);
  }
}
static inline void McHorVer23_sse2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                    int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pCtrTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_sse2 (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer22WidthEq16_sse2 (pSrc,            iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq16_sse2 (pDst, iDstStride, pHorTmp, 16, pCtrTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_sse2 (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer22WidthEq8_sse2 (pSrc,            iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq8_mmx (pDst, iDstStride, pHorTmp, 16, pCtrTmp, 16, iHeight);
  } else {
    McHorVer20WidthEq4_mmx (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer22_c (pSrc,            iSrcStride, pCtrTmp, 16, 4, iHeight);
    PixelAvgWidthEq4_mmx (pDst, iDstStride, pHorTmp, 16, pCtrTmp, 16, iHeight);
  }
}
static inline void McHorVer30_sse2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                    int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_sse2 (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    PixelAvgWidthEq16_sse2 (pDst, iDstStride, pSrc + 1, iSrcStride, pHorTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_sse2 (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    PixelAvgWidthEq8_mmx (pDst, iDstStride, pSrc + 1, iSrcStride, pHorTmp, 16, iHeight);
  } else {
    McHorVer20WidthEq4_mmx (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    PixelAvgWidthEq4_mmx (pDst, iDstStride, pSrc + 1, iSrcStride, pHorTmp, 16, iHeight);
  }
}
static inline void McHorVer31_sse2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                    int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pVerTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_sse2 (pSrc,   iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq16_sse2 (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq16_sse2 (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_sse2 (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq8_sse2 (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq8_mmx (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  } else {
    McHorVer20WidthEq4_mmx (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02_c (pSrc + 1, iSrcStride, pVerTmp, 16, 4, iHeight);
    PixelAvgWidthEq4_mmx (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  }
}
static inline void McHorVer32_sse2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                    int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pVerTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pCtrTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer02WidthEq16_sse2 (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    McHorVer22WidthEq16_sse2 (pSrc,   iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq16_sse2 (pDst, iDstStride, pVerTmp, 16, pCtrTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer02WidthEq8_sse2 (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    McHorVer22WidthEq8_sse2 (pSrc,   iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq8_mmx (pDst, iDstStride, pVerTmp, 16, pCtrTmp, 16, iHeight);
  } else {
    McHorVer02_c (pSrc + 1, iSrcStride, pVerTmp, 16, 4, iHeight);
    McHorVer22_c (pSrc,   iSrcStride, pCtrTmp, 16, 4, iHeight);
    PixelAvgWidthEq4_mmx (pDst, iDstStride, pVerTmp, 16, pCtrTmp, 16, iHeight);
  }
}
static inline void McHorVer33_sse2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                    int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pVerTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_sse2 (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq16_sse2 (pSrc + 1,          iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq16_sse2 (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_sse2 (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq8_sse2 (pSrc + 1,          iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq8_mmx (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  } else {
    McHorVer20WidthEq4_mmx (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02_c (pSrc + 1,          iSrcStride, pVerTmp, 16, 4, iHeight);
    PixelAvgWidthEq4_mmx (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  }
}

void McLuma_sse2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                  int16_t iMvX, int16_t iMvY, int32_t iWidth, int32_t iHeight)
//pSrc has been added the offset of mv
{
  static const PWelsMcWidthHeightFunc pWelsMcFunc[4][4] = { //[x][y]
    {McCopy_sse2,     McHorVer01_sse2, McHorVer02_sse2, McHorVer03_sse2},
    {McHorVer10_sse2, McHorVer11_sse2, McHorVer12_sse2, McHorVer13_sse2},
    {McHorVer20_sse2, McHorVer21_sse2, McHorVer22_sse2, McHorVer23_sse2},
    {McHorVer30_sse2, McHorVer31_sse2, McHorVer32_sse2, McHorVer33_sse2},
  };

  pWelsMcFunc[iMvX & 0x03][iMvY & 0x03] (pSrc, iSrcStride, pDst, iDstStride, iWidth, iHeight);
}

void McChroma_sse2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                    int16_t iMvX, int16_t iMvY, int32_t iWidth, int32_t iHeight) {
  static const PMcChromaWidthExtFunc kpMcChromaWidthFuncs[2] = {
    McChromaWidthEq4_mmx,
    McChromaWidthEq8_sse2
  };
  const int32_t kiD8x = iMvX & 0x07;
  const int32_t kiD8y = iMvY & 0x07;
  if (kiD8x == 0 && kiD8y == 0) {
    McCopy_sse2 (pSrc, iSrcStride, pDst, iDstStride, iWidth, iHeight);
    return;
  }
  if (iWidth != 2) {
    kpMcChromaWidthFuncs[iWidth >> 3] (pSrc, iSrcStride, pDst, iDstStride, g_kuiABCD[kiD8y][kiD8x], iHeight);
  } else
    McChromaWithFragMv_c (pSrc, iSrcStride, pDst, iDstStride, iMvX, iMvY, iWidth, iHeight);
}

//***************************************************************************//
//                          SSSE3 implementation                             //
//***************************************************************************//

void PixelAvgWidth4Or8Or16_sse2 (uint8_t* pDst, int32_t iDstStride, const uint8_t* pSrcA, int32_t iSrcAStride,
                                 const uint8_t* pSrcB, int32_t iSrcBStride, int32_t iWidth, int32_t iHeight) {
  if (iWidth < 8) {
    PixelAvgWidthEq4_mmx   (pDst, iDstStride, pSrcA, iSrcAStride, pSrcB, iSrcBStride, iHeight);
  } else if (iWidth == 8) {
    PixelAvgWidthEq8_mmx   (pDst, iDstStride, pSrcA, iSrcAStride, pSrcB, iSrcBStride, iHeight);
  } else {
    PixelAvgWidthEq16_sse2 (pDst, iDstStride, pSrcA, iSrcAStride, pSrcB, iSrcBStride, iHeight);
  }
}

void McCopy_sse3 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                  int32_t iWidth, int32_t iHeight) {
  switch (iWidth) {
  case 16: return McCopyWidthEq16_sse3 (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  case 8:  return McCopyWidthEq8_mmx (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  case 4:  return McCopyWidthEq4_c (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  }
  return McCopyWidthEq2_c (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}

void McHorVer22_ssse3 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                       int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (int16_t, pTmp, 16 + 5, 8, 16);
  if (iWidth < 8) {
    McHorVer20Width4U8ToS16_ssse3 (pSrc, iSrcStride, &pTmp[0][0], iHeight + 5);
    McHorVer02Width4S16ToU8_ssse3 (&pTmp[0][0], pDst, iDstStride, iHeight);
  } else if (iWidth == 8) {
    McHorVer20Width8U8ToS16_ssse3 (pSrc, iSrcStride, &pTmp[0][0], sizeof *pTmp, iHeight + 5);
    McHorVer02WidthGe8S16ToU8_ssse3 (&pTmp[0][0], sizeof *pTmp, pDst, iDstStride, iWidth, iHeight);
  } else {
    McHorVer20Width8U8ToS16_ssse3 (pSrc, iSrcStride, &pTmp[0][0], sizeof *pTmp, iHeight + 5);
    McHorVer02WidthGe8S16ToU8_ssse3 (&pTmp[0][0], sizeof *pTmp, pDst, iDstStride, 8, iHeight);
    McHorVer20Width8U8ToS16_ssse3 (pSrc + 8, iSrcStride, &pTmp[0][0], sizeof *pTmp, iHeight + 5);
    McHorVer02WidthGe8S16ToU8_ssse3 (&pTmp[0][0], sizeof *pTmp, pDst + 8, iDstStride, 8, iHeight);
  }
}

void McHorVer01_ssse3 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                       int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (uint8_t, pTmp, 16, 16, 16);
  McHorVer02_ssse3 (pSrc, iSrcStride, &pTmp[0][0], sizeof *pTmp, iWidth, iHeight);
  PixelAvgWidth4Or8Or16_sse2 (pDst, iDstStride, pSrc, iSrcStride,
                              &pTmp[0][0], sizeof *pTmp, iWidth, iHeight);
}

void McHorVer03_ssse3 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                       int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (uint8_t, pTmp, 16, 16, 16);
  McHorVer02_ssse3 (pSrc, iSrcStride, &pTmp[0][0], sizeof *pTmp, iWidth, iHeight);
  PixelAvgWidth4Or8Or16_sse2 (pDst, iDstStride, pSrc + iSrcStride, iSrcStride,
                              &pTmp[0][0], sizeof *pTmp, iWidth, iHeight);
}

void McHorVer10_ssse3 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                       int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (uint8_t, pTmp, 16, 16, 16);
  McHorVer20_ssse3 (pSrc, iSrcStride, &pTmp[0][0], sizeof *pTmp, iWidth, iHeight);
  PixelAvgWidth4Or8Or16_sse2 (pDst, iDstStride, pSrc, iSrcStride,
                              &pTmp[0][0], sizeof *pTmp, iWidth, iHeight);
}

void McHorVer11_ssse3 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                       int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (uint8_t, pHorTmp, 16, 16, 16);
  ENFORCE_STACK_ALIGN_2D (uint8_t, pVerTmp, 16, 16, 16);
  McHorVer20_ssse3 (pSrc, iSrcStride, &pHorTmp[0][0], sizeof *pHorTmp, iWidth, iHeight);
  McHorVer02_ssse3 (pSrc, iSrcStride, &pVerTmp[0][0], sizeof *pVerTmp, iWidth, iHeight);
  PixelAvgWidth4Or8Or16_sse2 (pDst, iDstStride, &pHorTmp[0][0], sizeof *pHorTmp,
                              &pVerTmp[0][0], sizeof *pVerTmp, iWidth, iHeight);
}

void McHorVer12_ssse3 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                       int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (uint8_t, pVerTmp, 16, 16, 16);
  ENFORCE_STACK_ALIGN_2D (uint8_t, pCtrTmp, 16, 16, 16);
  McHorVer02_ssse3 (pSrc, iSrcStride, &pVerTmp[0][0], sizeof *pVerTmp, iWidth, iHeight);
  McHorVer22_ssse3 (pSrc, iSrcStride, &pCtrTmp[0][0], sizeof *pCtrTmp, iWidth, iHeight);
  PixelAvgWidth4Or8Or16_sse2 (pDst, iDstStride, &pVerTmp[0][0], sizeof *pVerTmp,
                              &pCtrTmp[0][0], sizeof *pCtrTmp, iWidth, iHeight);
}

void McHorVer13_ssse3 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                       int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (uint8_t, pHorTmp, 16, 16, 16);
  ENFORCE_STACK_ALIGN_2D (uint8_t, pVerTmp, 16, 16, 16);
  McHorVer20_ssse3 (pSrc + iSrcStride, iSrcStride, &pHorTmp[0][0], sizeof *pHorTmp, iWidth, iHeight);
  McHorVer02_ssse3 (pSrc,              iSrcStride, &pVerTmp[0][0], sizeof *pVerTmp, iWidth, iHeight);
  PixelAvgWidth4Or8Or16_sse2 (pDst, iDstStride, &pHorTmp[0][0], sizeof *pHorTmp,
                              &pVerTmp[0][0], sizeof *pVerTmp, iWidth, iHeight);
}

void McHorVer21_ssse3 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                       int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (uint8_t, pHorTmp, 16, 16, 16);
  ENFORCE_STACK_ALIGN_2D (uint8_t, pCtrTmp, 16, 16, 16);
  McHorVer20_ssse3 (pSrc, iSrcStride, &pHorTmp[0][0], sizeof *pHorTmp, iWidth, iHeight);
  McHorVer22_ssse3 (pSrc, iSrcStride, &pCtrTmp[0][0], sizeof *pCtrTmp, iWidth, iHeight);
  PixelAvgWidth4Or8Or16_sse2 (pDst, iDstStride, &pHorTmp[0][0], sizeof *pHorTmp,
                              &pCtrTmp[0][0], sizeof *pCtrTmp, iWidth, iHeight);
}

void McHorVer23_ssse3 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                       int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (uint8_t, pHorTmp, 16, 16, 16);
  ENFORCE_STACK_ALIGN_2D (uint8_t, pCtrTmp, 16, 16, 16);
  McHorVer20_ssse3 (pSrc + iSrcStride, iSrcStride, &pHorTmp[0][0], sizeof *pHorTmp, iWidth, iHeight);
  McHorVer22_ssse3 (pSrc,              iSrcStride, &pCtrTmp[0][0], sizeof *pCtrTmp, iWidth, iHeight);
  PixelAvgWidth4Or8Or16_sse2 (pDst, iDstStride, &pHorTmp[0][0], sizeof *pHorTmp,
                              &pCtrTmp[0][0], sizeof *pCtrTmp, iWidth, iHeight);
}

void McHorVer30_ssse3 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                       int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (uint8_t, pTmp, 16, 16, 16);
  McHorVer20_ssse3 (pSrc, iSrcStride, &pTmp[0][0], sizeof *pTmp, iWidth, iHeight);
  PixelAvgWidth4Or8Or16_sse2 (pDst, iDstStride, pSrc + 1, iSrcStride, &pTmp[0][0], sizeof *pTmp, iWidth, iHeight);
}

void McHorVer31_ssse3 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                       int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (uint8_t, pHorTmp, 16, 16, 16);
  ENFORCE_STACK_ALIGN_2D (uint8_t, pVerTmp, 16, 16, 16);
  McHorVer20_ssse3 (pSrc,     iSrcStride, &pHorTmp[0][0], sizeof *pHorTmp, iWidth, iHeight);
  McHorVer02_ssse3 (pSrc + 1, iSrcStride, &pVerTmp[0][0], sizeof *pVerTmp, iWidth, iHeight);
  PixelAvgWidth4Or8Or16_sse2 (pDst, iDstStride, &pHorTmp[0][0], sizeof *pHorTmp,
                              &pVerTmp[0][0], sizeof *pVerTmp, iWidth, iHeight);
}

void McHorVer32_ssse3 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                       int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (uint8_t, pVerTmp, 16, 16, 16);
  ENFORCE_STACK_ALIGN_2D (uint8_t, pCtrTmp, 16, 16, 16);
  McHorVer02_ssse3 (pSrc + 1, iSrcStride, &pVerTmp[0][0], sizeof *pVerTmp, iWidth, iHeight);
  McHorVer22_ssse3 (pSrc,     iSrcStride, &pCtrTmp[0][0], sizeof *pCtrTmp, iWidth, iHeight);
  PixelAvgWidth4Or8Or16_sse2 (pDst, iDstStride, &pVerTmp[0][0], sizeof *pVerTmp,
                              &pCtrTmp[0][0], sizeof *pCtrTmp, iWidth, iHeight);
}

void McHorVer33_ssse3 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                       int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (uint8_t, pHorTmp, 16, 16, 16);
  ENFORCE_STACK_ALIGN_2D (uint8_t, pVerTmp, 16, 16, 16);
  McHorVer20_ssse3 (pSrc + iSrcStride, iSrcStride, &pHorTmp[0][0], sizeof *pHorTmp, iWidth, iHeight);
  McHorVer02_ssse3 (pSrc + 1,          iSrcStride, &pVerTmp[0][0], sizeof *pVerTmp, iWidth, iHeight);
  PixelAvgWidth4Or8Or16_sse2 (pDst, iDstStride, &pHorTmp[0][0], sizeof *pHorTmp,
                              &pVerTmp[0][0], sizeof *pVerTmp, iWidth, iHeight);
}

void McHorVer22Width5Or9Or17_ssse3 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                    int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (int16_t, pTmp, 17 + 5, WELS_ALIGN(17, 16 / sizeof (int16_t)), 16)
  if (iWidth > 5) {
    McHorVer20Width9Or17U8ToS16_ssse3 (pSrc, iSrcStride, &pTmp[0][0], sizeof *pTmp, iWidth, iHeight + 5);
    McHorVer02WidthGe8S16ToU8_ssse3 (&pTmp[0][0], sizeof *pTmp, pDst, iDstStride, iWidth, iHeight);
  } else {
    McHorVer20Width8U8ToS16_ssse3 (pSrc, iSrcStride, &pTmp[0][0], sizeof *pTmp, iHeight + 5);
    McHorVer02Width5S16ToU8_ssse3 (&pTmp[0][0], sizeof *pTmp, pDst, iDstStride, iHeight);
  }
}

void McLuma_ssse3 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                   int16_t iMvX, int16_t iMvY, int32_t iWidth, int32_t iHeight) {
  static const PWelsMcWidthHeightFunc pWelsMcFunc[4][4] = {
    {McCopy_sse3,      McHorVer01_ssse3, McHorVer02_ssse3, McHorVer03_ssse3},
    {McHorVer10_ssse3, McHorVer11_ssse3, McHorVer12_ssse3, McHorVer13_ssse3},
    {McHorVer20_ssse3, McHorVer21_ssse3, McHorVer22_ssse3, McHorVer23_ssse3},
    {McHorVer30_ssse3, McHorVer31_ssse3, McHorVer32_ssse3, McHorVer33_ssse3},
  };

  pWelsMcFunc[iMvX & 0x03][iMvY & 0x03] (pSrc, iSrcStride, pDst, iDstStride, iWidth, iHeight);
}

void McChroma_ssse3 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                     int16_t iMvX, int16_t iMvY, int32_t iWidth, int32_t iHeight) {
  static const PMcChromaWidthExtFunc kpMcChromaWidthFuncs[2] = {
    McChromaWidthEq4_mmx,
    McChromaWidthEq8_ssse3
  };
  const int32_t kiD8x = iMvX & 0x07;
  const int32_t kiD8y = iMvY & 0x07;
  if (kiD8x == 0 && kiD8y == 0) {
    McCopy_sse2 (pSrc, iSrcStride, pDst, iDstStride, iWidth, iHeight);
    return;
  }
  if (iWidth != 2) {
    kpMcChromaWidthFuncs[iWidth >> 3] (pSrc, iSrcStride, pDst, iDstStride, g_kuiABCD[kiD8y][kiD8x], iHeight);
  } else
    McChromaWithFragMv_c (pSrc, iSrcStride, pDst, iDstStride, iMvX, iMvY, iWidth, iHeight);
}

//***************************************************************************//
//                          AVX2 implementation                              //
//***************************************************************************//

#ifdef HAVE_AVX2

void McHorVer22_avx2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (int16_t, pTmp, 16 + 5, 16, 32);
  if (iWidth < 8) {
    McHorVer20Width4U8ToS16_avx2 (pSrc, iSrcStride, &pTmp[0][0], iHeight + 5);
    McHorVer02Width4S16ToU8_avx2 (&pTmp[0][0], pDst, iDstStride, iHeight);
  } else if (iWidth == 8) {
    McHorVer20Width8U8ToS16_avx2 (pSrc, iSrcStride, &pTmp[0][0], iHeight + 5);
    McHorVer02Width8S16ToU8_avx2 (&pTmp[0][0], pDst, iDstStride, iHeight);
  } else {
    McHorVer20Width16U8ToS16_avx2 (pSrc, iSrcStride, &pTmp[0][0], iHeight + 5);
    McHorVer02Width16Or17S16ToU8_avx2 (&pTmp[0][0], sizeof *pTmp, pDst, iDstStride, iWidth, iHeight);
  }
}

void McHorVer01_avx2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (uint8_t, pTmp, 16, 16, 16);
  McHorVer02_avx2 (pSrc, iSrcStride, &pTmp[0][0], sizeof *pTmp, iWidth, iHeight);
  PixelAvgWidth4Or8Or16_sse2 (pDst, iDstStride, pSrc, iSrcStride,
                              &pTmp[0][0], sizeof *pTmp, iWidth, iHeight);
}

void McHorVer03_avx2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (uint8_t, pTmp, 16, 16, 16);
  McHorVer02_avx2 (pSrc, iSrcStride, &pTmp[0][0], sizeof *pTmp, iWidth, iHeight);
  PixelAvgWidth4Or8Or16_sse2 (pDst, iDstStride, pSrc + iSrcStride, iSrcStride,
                              &pTmp[0][0], sizeof *pTmp, iWidth, iHeight);
}

void McHorVer10_avx2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (uint8_t, pTmp, 16, 16, 16);
  McHorVer20_avx2 (pSrc, iSrcStride, &pTmp[0][0], sizeof *pTmp, iWidth, iHeight);
  PixelAvgWidth4Or8Or16_sse2 (pDst, iDstStride, pSrc, iSrcStride,
                              &pTmp[0][0], sizeof *pTmp, iWidth, iHeight);
}

void McHorVer11_avx2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (uint8_t, pHorTmp, 16, 16, 16);
  ENFORCE_STACK_ALIGN_2D (uint8_t, pVerTmp, 16, 16, 16);
  McHorVer20_avx2 (pSrc, iSrcStride, &pHorTmp[0][0], sizeof *pHorTmp, iWidth, iHeight);
  McHorVer02_avx2 (pSrc, iSrcStride, &pVerTmp[0][0], sizeof *pVerTmp, iWidth, iHeight);
  PixelAvgWidth4Or8Or16_sse2 (pDst, iDstStride, &pHorTmp[0][0], sizeof *pHorTmp,
                              &pVerTmp[0][0], sizeof *pVerTmp, iWidth, iHeight);
}

void McHorVer12_avx2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (uint8_t, pVerTmp, 16, 16, 16);
  ENFORCE_STACK_ALIGN_2D (uint8_t, pCtrTmp, 16, 16, 16);
  McHorVer02_avx2 (pSrc, iSrcStride, &pVerTmp[0][0], sizeof *pVerTmp, iWidth, iHeight);
  McHorVer22_avx2 (pSrc, iSrcStride, &pCtrTmp[0][0], sizeof *pCtrTmp, iWidth, iHeight);
  PixelAvgWidth4Or8Or16_sse2 (pDst, iDstStride, &pVerTmp[0][0], sizeof *pVerTmp,
                              &pCtrTmp[0][0], sizeof *pCtrTmp, iWidth, iHeight);
}

void McHorVer13_avx2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (uint8_t, pHorTmp, 16, 16, 16);
  ENFORCE_STACK_ALIGN_2D (uint8_t, pVerTmp, 16, 16, 16);
  McHorVer20_avx2 (pSrc + iSrcStride, iSrcStride, &pHorTmp[0][0], sizeof *pHorTmp, iWidth, iHeight);
  McHorVer02_avx2 (pSrc,              iSrcStride, &pVerTmp[0][0], sizeof *pVerTmp, iWidth, iHeight);
  PixelAvgWidth4Or8Or16_sse2 (pDst, iDstStride, &pHorTmp[0][0], sizeof *pHorTmp,
                              &pVerTmp[0][0], sizeof *pVerTmp, iWidth, iHeight);
}

void McHorVer21_avx2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (uint8_t, pHorTmp, 16, 16, 16);
  ENFORCE_STACK_ALIGN_2D (uint8_t, pCtrTmp, 16, 16, 16);
  McHorVer20_avx2 (pSrc, iSrcStride, &pHorTmp[0][0], sizeof *pHorTmp, iWidth, iHeight);
  McHorVer22_avx2 (pSrc, iSrcStride, &pCtrTmp[0][0], sizeof *pCtrTmp, iWidth, iHeight);
  PixelAvgWidth4Or8Or16_sse2 (pDst, iDstStride, &pHorTmp[0][0], sizeof *pHorTmp,
                              &pCtrTmp[0][0], sizeof *pCtrTmp, iWidth, iHeight);
}

void McHorVer23_avx2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (uint8_t, pHorTmp, 16, 16, 16);
  ENFORCE_STACK_ALIGN_2D (uint8_t, pCtrTmp, 16, 16, 16);
  McHorVer20_avx2 (pSrc + iSrcStride, iSrcStride, &pHorTmp[0][0], sizeof *pHorTmp, iWidth, iHeight);
  McHorVer22_avx2 (pSrc,              iSrcStride, &pCtrTmp[0][0], sizeof *pCtrTmp, iWidth, iHeight);
  PixelAvgWidth4Or8Or16_sse2 (pDst, iDstStride, &pHorTmp[0][0], sizeof *pHorTmp,
                              &pCtrTmp[0][0], sizeof *pCtrTmp, iWidth, iHeight);
}

void McHorVer30_avx2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (uint8_t, pTmp, 16, 16, 16);
  McHorVer20_avx2 (pSrc, iSrcStride, &pTmp[0][0], sizeof *pTmp, iWidth, iHeight);
  PixelAvgWidth4Or8Or16_sse2 (pDst, iDstStride, pSrc + 1, iSrcStride, &pTmp[0][0], sizeof *pTmp, iWidth, iHeight);
}

void McHorVer31_avx2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (uint8_t, pHorTmp, 16, 16, 16);
  ENFORCE_STACK_ALIGN_2D (uint8_t, pVerTmp, 16, 16, 16);
  McHorVer20_avx2 (pSrc,     iSrcStride, &pHorTmp[0][0], sizeof *pHorTmp, iWidth, iHeight);
  McHorVer02_avx2 (pSrc + 1, iSrcStride, &pVerTmp[0][0], sizeof *pVerTmp, iWidth, iHeight);
  PixelAvgWidth4Or8Or16_sse2 (pDst, iDstStride, &pHorTmp[0][0], sizeof *pHorTmp,
                              &pVerTmp[0][0], sizeof *pVerTmp, iWidth, iHeight);
}

void McHorVer32_avx2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (uint8_t, pVerTmp, 16, 16, 16);
  ENFORCE_STACK_ALIGN_2D (uint8_t, pCtrTmp, 16, 16, 16);
  McHorVer02_avx2 (pSrc + 1, iSrcStride, &pVerTmp[0][0], sizeof *pVerTmp, iWidth, iHeight);
  McHorVer22_avx2 (pSrc,     iSrcStride, &pCtrTmp[0][0], sizeof *pCtrTmp, iWidth, iHeight);
  PixelAvgWidth4Or8Or16_sse2 (pDst, iDstStride, &pVerTmp[0][0], sizeof *pVerTmp,
                              &pCtrTmp[0][0], sizeof *pCtrTmp, iWidth, iHeight);
}

void McHorVer33_avx2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (uint8_t, pHorTmp, 16, 16, 16);
  ENFORCE_STACK_ALIGN_2D (uint8_t, pVerTmp, 16, 16, 16);
  McHorVer20_avx2 (pSrc + iSrcStride, iSrcStride, &pHorTmp[0][0], sizeof *pHorTmp, iWidth, iHeight);
  McHorVer02_avx2 (pSrc + 1,          iSrcStride, &pVerTmp[0][0], sizeof *pVerTmp, iWidth, iHeight);
  PixelAvgWidth4Or8Or16_sse2 (pDst, iDstStride, &pHorTmp[0][0], sizeof *pHorTmp,
                              &pVerTmp[0][0], sizeof *pVerTmp, iWidth, iHeight);
}

void McHorVer22Width5Or9Or17_avx2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                   int32_t iWidth, int32_t iHeight) {
  if (iWidth < 9) {
    ENFORCE_STACK_ALIGN_2D (int16_t, pTmp, 9 + 5, WELS_ALIGN(5, 16 / sizeof (int16_t)), 16)
    McHorVer20Width8U8ToS16_avx2 (pSrc, iSrcStride, &pTmp[0][0], iHeight + 5);
    McHorVer02Width5S16ToU8_avx2 (&pTmp[0][0], pDst, iDstStride, iHeight);
  } else if (iWidth == 9) {
    ENFORCE_STACK_ALIGN_2D (int16_t, pTmp, 17 + 5, 16, 32)
    McHorVer20Width16U8ToS16_avx2 (pSrc, iSrcStride, &pTmp[0][0], iHeight + 5);
    McHorVer02Width9S16ToU8_avx2 (&pTmp[0][0], pDst, iDstStride, iHeight);
  } else {
    ENFORCE_STACK_ALIGN_2D (int16_t, pTmp, 17 + 5, WELS_ALIGN(17, 32 / sizeof (int16_t)), 32)
    McHorVer20Width17U8ToS16_avx2 (pSrc, iSrcStride, &pTmp[0][0], iHeight + 5);
    McHorVer02Width16Or17S16ToU8_avx2 (&pTmp[0][0], sizeof *pTmp, pDst, iDstStride, iWidth, iHeight);
  }
}

void McLuma_avx2 (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                  int16_t iMvX, int16_t iMvY, int32_t iWidth, int32_t iHeight) {
  static const PWelsMcWidthHeightFunc pWelsMcFunc[4][4] = {
    {McCopy_sse3,     McHorVer01_avx2, McHorVer02_avx2, McHorVer03_avx2},
    {McHorVer10_avx2, McHorVer11_avx2, McHorVer12_avx2, McHorVer13_avx2},
    {McHorVer20_avx2, McHorVer21_avx2, McHorVer22_avx2, McHorVer23_avx2},
    {McHorVer30_avx2, McHorVer31_avx2, McHorVer32_avx2, McHorVer33_avx2},
  };

  pWelsMcFunc[iMvX & 0x03][iMvY & 0x03] (pSrc, iSrcStride, pDst, iDstStride, iWidth, iHeight);
}

#endif //HAVE_AVX2

void PixelAvg_sse2 (uint8_t* pDst, int32_t iDstStride, const uint8_t* pSrcA, int32_t iSrcAStride,
                    const uint8_t* pSrcB, int32_t iSrcBStride, int32_t iWidth, int32_t iHeight) {
  static const PWelsSampleWidthAveragingFunc kpfFuncs[2] = {
    PixelAvgWidthEq8_mmx,
    PixelAvgWidthEq16_sse2
  };
  kpfFuncs[iWidth >> 4] (pDst, iDstStride, pSrcA, iSrcAStride, pSrcB, iSrcBStride, iHeight);
}

#endif //X86_ASM
//***************************************************************************//
//                       NEON implementation                      //
//***************************************************************************//
#if defined(HAVE_NEON)
void McHorVer20Width5Or9Or17_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                int32_t iWidth, int32_t iHeight) {
  if (iWidth == 17)
    McHorVer20Width17_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 9)
    McHorVer20Width9_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else //if (iWidth == 5)
    McHorVer20Width5_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}
void McHorVer02Height5Or9Or17_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                 int32_t iWidth, int32_t iHeight) {
  if (iWidth == 16)
    McHorVer02Height17_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McHorVer02Height9_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else //if (iWidth == 4)
    McHorVer02Height5_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}
void McHorVer22Width5Or9Or17Height5Or9Or17_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
    int32_t iWidth, int32_t iHeight) {
  if (iWidth == 17)
    McHorVer22Width17_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 9)
    McHorVer22Width9_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else //if (iWidth == 5)
    McHorVer22Width5_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}
void McCopy_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                  int32_t iWidth, int32_t iHeight) {
  if (16 == iWidth)
    McCopyWidthEq16_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (8 == iWidth)
    McCopyWidthEq8_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (4 == iWidth)
    McCopyWidthEq4_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else
    McCopyWidthEq2_c (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}
void McHorVer20_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  if (iWidth == 16)
    McHorVer20WidthEq16_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McHorVer20WidthEq8_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 4)
    McHorVer20WidthEq4_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}
void McHorVer02_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  if (iWidth == 16)
    McHorVer02WidthEq16_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McHorVer02WidthEq8_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 4)
    McHorVer02WidthEq4_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}
void McHorVer22_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  if (iWidth == 16)
    McHorVer22WidthEq16_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McHorVer22WidthEq8_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 4)
    McHorVer22WidthEq4_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}

void McHorVer01_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  if (iWidth == 16)
    McHorVer01WidthEq16_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McHorVer01WidthEq8_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 4)
    McHorVer01WidthEq4_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}
void McHorVer03_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  if (iWidth == 16)
    McHorVer03WidthEq16_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McHorVer03WidthEq8_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 4)
    McHorVer03WidthEq4_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}
void McHorVer10_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  if (iWidth == 16)
    McHorVer10WidthEq16_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McHorVer10WidthEq8_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 4)
    McHorVer10WidthEq4_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}
void McHorVer11_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pVerTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_neon (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq16_neon (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq16_neon (pDst, iDstStride, pHorTmp, pVerTmp, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_neon (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq8_neon (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq8_neon (pDst, iDstStride, pHorTmp, pVerTmp, iHeight);
  } else if (iWidth == 4) {
    McHorVer20WidthEq4_neon (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq4_neon (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq4_neon (pDst, iDstStride, pHorTmp, pVerTmp, iHeight);
  }
}
void McHorVer12_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pVerTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pCtrTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer02WidthEq16_neon (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    McHorVer22WidthEq16_neon (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq16_neon (pDst, iDstStride, pVerTmp, pCtrTmp, iHeight);
  } else if (iWidth == 8) {
    McHorVer02WidthEq8_neon (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    McHorVer22WidthEq8_neon (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq8_neon (pDst, iDstStride, pVerTmp, pCtrTmp, iHeight);
  } else if (iWidth == 4) {
    McHorVer02WidthEq4_neon (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    McHorVer22WidthEq4_neon (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq4_neon (pDst, iDstStride, pVerTmp, pCtrTmp, iHeight);
  }
}
void McHorVer13_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pVerTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_neon (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq16_neon (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq16_neon (pDst, iDstStride, pHorTmp, pVerTmp, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_neon (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq8_neon (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq8_neon (pDst, iDstStride, pHorTmp, pVerTmp, iHeight);
  } else if (iWidth == 4) {
    McHorVer20WidthEq4_neon (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq4_neon (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq4_neon (pDst, iDstStride, pHorTmp, pVerTmp, iHeight);
  }
}
void McHorVer21_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pCtrTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_neon (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer22WidthEq16_neon (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq16_neon (pDst, iDstStride, pHorTmp, pCtrTmp, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_neon (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer22WidthEq8_neon (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq8_neon (pDst, iDstStride, pHorTmp, pCtrTmp, iHeight);
  } else if (iWidth == 4) {
    McHorVer20WidthEq4_neon (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer22WidthEq4_neon (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq4_neon (pDst, iDstStride, pHorTmp, pCtrTmp, iHeight);
  }
}
void McHorVer23_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pCtrTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_neon (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer22WidthEq16_neon (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq16_neon (pDst, iDstStride, pHorTmp, pCtrTmp, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_neon (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer22WidthEq8_neon (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq8_neon (pDst, iDstStride, pHorTmp, pCtrTmp, iHeight);
  } else if (iWidth == 4) {
    McHorVer20WidthEq4_neon (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer22WidthEq4_neon (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq4_neon (pDst, iDstStride, pHorTmp, pCtrTmp, iHeight);
  }
}
void McHorVer30_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  if (iWidth == 16)
    McHorVer30WidthEq16_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McHorVer30WidthEq8_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 4)
    McHorVer30WidthEq4_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}
void McHorVer31_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pVerTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_neon (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq16_neon (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq16_neon (pDst, iDstStride, pHorTmp, pVerTmp, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_neon (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq8_neon (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq8_neon (pDst, iDstStride, pHorTmp, pVerTmp, iHeight);
  } else if (iWidth == 4) {
    McHorVer20WidthEq4_neon (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq4_neon (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq4_neon (pDst, iDstStride, pHorTmp, pVerTmp, iHeight);
  }
}
void McHorVer32_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pVerTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pCtrTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer02WidthEq16_neon (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    McHorVer22WidthEq16_neon (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq16_neon (pDst, iDstStride, pVerTmp, pCtrTmp, iHeight);
  } else if (iWidth == 8) {
    McHorVer02WidthEq8_neon (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    McHorVer22WidthEq8_neon (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq8_neon (pDst, iDstStride, pVerTmp, pCtrTmp, iHeight);
  } else if (iWidth == 4) {
    McHorVer02WidthEq4_neon (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    McHorVer22WidthEq4_neon (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq4_neon (pDst, iDstStride, pVerTmp, pCtrTmp, iHeight);
  }
}
void McHorVer33_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                      int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pVerTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_neon (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq16_neon (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq16_neon (pDst, iDstStride, pHorTmp, pVerTmp, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_neon (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq8_neon (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq8_neon (pDst, iDstStride, pHorTmp, pVerTmp, iHeight);
  } else if (iWidth == 4) {
    McHorVer20WidthEq4_neon (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq4_neon (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq4_neon (pDst, iDstStride, pHorTmp, pVerTmp, iHeight);
  }
}

void McLuma_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                  int16_t iMvX, int16_t iMvY, int32_t iWidth, int32_t iHeight) {
  static const PWelsMcWidthHeightFunc pWelsMcFunc[4][4] = { //[x][y]
    {McCopy_neon,  McHorVer01_neon, McHorVer02_neon,    McHorVer03_neon},
    {McHorVer10_neon, McHorVer11_neon, McHorVer12_neon, McHorVer13_neon},
    {McHorVer20_neon,    McHorVer21_neon, McHorVer22_neon,    McHorVer23_neon},
    {McHorVer30_neon, McHorVer31_neon, McHorVer32_neon, McHorVer33_neon},
  };
  // pSrc += (iMvY >> 2) * iSrcStride + (iMvX >> 2);
  pWelsMcFunc[iMvX & 0x03][iMvY & 0x03] (pSrc, iSrcStride, pDst, iDstStride, iWidth, iHeight);
}
void McChroma_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                    int16_t iMvX, int16_t iMvY, int32_t iWidth, int32_t iHeight) {
  if (0 == iMvX && 0 == iMvY) {
    if (8 == iWidth)
      McCopyWidthEq8_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
    else if (iWidth == 4)
      McCopyWidthEq4_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
    else //here iWidth == 2
      McCopyWidthEq2_c (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  } else {
    const int32_t kiD8x = iMvX & 0x07;
    const int32_t kiD8y = iMvY & 0x07;
    if (8 == iWidth)
      McChromaWidthEq8_neon (pSrc, iSrcStride, pDst, iDstStride, (int32_t*) (g_kuiABCD[kiD8y][kiD8x]), iHeight);
    else if (4 == iWidth)
      McChromaWidthEq4_neon (pSrc, iSrcStride, pDst, iDstStride, (int32_t*) (g_kuiABCD[kiD8y][kiD8x]), iHeight);
    else //here iWidth == 2
      McChromaWithFragMv_c (pSrc, iSrcStride, pDst, iDstStride, iMvX, iMvY, iWidth, iHeight);
  }
}
void PixelAvg_neon (uint8_t* pDst, int32_t iDstStride, const uint8_t* pSrcA, int32_t iSrcAStride,
                    const uint8_t* pSrcB, int32_t iSrcBStride, int32_t iWidth, int32_t iHeight) {
  static const PWelsSampleWidthAveragingFunc kpfFuncs[2] = {
    PixStrideAvgWidthEq8_neon,
    PixStrideAvgWidthEq16_neon
  };
  kpfFuncs[iWidth >> 4] (pDst, iDstStride, pSrcA, iSrcAStride, pSrcB, iSrcBStride, iHeight);
}
#endif
#if defined(HAVE_NEON_AARCH64)
void McHorVer20Width5Or9Or17_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                                        int32_t iWidth, int32_t iHeight) {
  if (iWidth == 17)
    McHorVer20Width17_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 9)
    McHorVer20Width9_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else //if (iWidth == 5)
    McHorVer20Width5_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}
void McHorVer02Height5Or9Or17_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
    int32_t iWidth, int32_t iHeight) {
  if (iWidth == 16)
    McHorVer02Height17_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McHorVer02Height9_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else //if (iWidth == 4)
    McHorVer02Height5_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}
void McHorVer22Width5Or9Or17Height5Or9Or17_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst,
    int32_t iDstStride,
    int32_t iWidth, int32_t iHeight) {
  if (iWidth == 17)
    McHorVer22Width17_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 9)
    McHorVer22Width9_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else //if (iWidth == 5)
    McHorVer22Width5_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}
void McCopy_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                          int32_t iWidth, int32_t iHeight) {
  if (16 == iWidth)
    McCopyWidthEq16_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (8 == iWidth)
    McCopyWidthEq8_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (4 == iWidth)
    McCopyWidthEq4_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else
    McCopyWidthEq2_c (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}
void McHorVer20_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                              int32_t iWidth, int32_t iHeight) {
  if (iWidth == 16)
    McHorVer20WidthEq16_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McHorVer20WidthEq8_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 4)
    McHorVer20WidthEq4_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}
void McHorVer02_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                              int32_t iWidth, int32_t iHeight) {
  if (iWidth == 16)
    McHorVer02WidthEq16_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McHorVer02WidthEq8_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 4)
    McHorVer02WidthEq4_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}
void McHorVer22_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                              int32_t iWidth, int32_t iHeight) {
  if (iWidth == 16)
    McHorVer22WidthEq16_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McHorVer22WidthEq8_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 4)
    McHorVer22WidthEq4_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}

void McHorVer01_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                              int32_t iWidth, int32_t iHeight) {
  if (iWidth == 16)
    McHorVer01WidthEq16_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McHorVer01WidthEq8_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 4)
    McHorVer01WidthEq4_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}
void McHorVer03_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                              int32_t iWidth, int32_t iHeight) {
  if (iWidth == 16)
    McHorVer03WidthEq16_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McHorVer03WidthEq8_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 4)
    McHorVer03WidthEq4_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}
void McHorVer10_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                              int32_t iWidth, int32_t iHeight) {
  if (iWidth == 16)
    McHorVer10WidthEq16_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McHorVer10WidthEq8_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 4)
    McHorVer10WidthEq4_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}
void McHorVer11_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                              int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pVerTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_AArch64_neon (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq16_AArch64_neon (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq16_AArch64_neon (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_AArch64_neon (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq8_AArch64_neon (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq8_AArch64_neon (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  } else if (iWidth == 4) {
    McHorVer20WidthEq4_AArch64_neon (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq4_AArch64_neon (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq4_AArch64_neon (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  }
}
void McHorVer12_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                              int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pVerTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pCtrTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer02WidthEq16_AArch64_neon (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    McHorVer22WidthEq16_AArch64_neon (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq16_AArch64_neon (pDst, iDstStride, pVerTmp, 16, pCtrTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer02WidthEq8_AArch64_neon (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    McHorVer22WidthEq8_AArch64_neon (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq8_AArch64_neon (pDst, iDstStride, pVerTmp, 16, pCtrTmp, 16, iHeight);
  } else if (iWidth == 4) {
    McHorVer02WidthEq4_AArch64_neon (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    McHorVer22WidthEq4_AArch64_neon (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq4_AArch64_neon (pDst, iDstStride, pVerTmp, 16, pCtrTmp, 16, iHeight);
  }
}
void McHorVer13_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                              int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pVerTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_AArch64_neon (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq16_AArch64_neon (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq16_AArch64_neon (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_AArch64_neon (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq8_AArch64_neon (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq8_AArch64_neon (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  } else if (iWidth == 4) {
    McHorVer20WidthEq4_AArch64_neon (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq4_AArch64_neon (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq4_AArch64_neon (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  }
}
void McHorVer21_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                              int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pCtrTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_AArch64_neon (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer22WidthEq16_AArch64_neon (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq16_AArch64_neon (pDst, iDstStride, pHorTmp, 16, pCtrTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_AArch64_neon (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer22WidthEq8_AArch64_neon (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq8_AArch64_neon (pDst, iDstStride, pHorTmp, 16, pCtrTmp, 16, iHeight);
  } else if (iWidth == 4) {
    McHorVer20WidthEq4_AArch64_neon (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer22WidthEq4_AArch64_neon (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq4_AArch64_neon (pDst, iDstStride, pHorTmp, 16, pCtrTmp, 16, iHeight);
  }
}
void McHorVer23_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                              int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pCtrTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_AArch64_neon (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer22WidthEq16_AArch64_neon (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq16_AArch64_neon (pDst, iDstStride, pHorTmp, 16, pCtrTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_AArch64_neon (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer22WidthEq8_AArch64_neon (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq8_AArch64_neon (pDst, iDstStride, pHorTmp, 16, pCtrTmp, 16, iHeight);
  } else if (iWidth == 4) {
    McHorVer20WidthEq4_AArch64_neon (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer22WidthEq4_AArch64_neon (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq4_AArch64_neon (pDst, iDstStride, pHorTmp, 16, pCtrTmp, 16, iHeight);
  }
}
void McHorVer30_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                              int32_t iWidth, int32_t iHeight) {
  if (iWidth == 16)
    McHorVer30WidthEq16_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McHorVer30WidthEq8_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 4)
    McHorVer30WidthEq4_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}
void McHorVer31_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                              int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pVerTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_AArch64_neon (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq16_AArch64_neon (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq16_AArch64_neon (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_AArch64_neon (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq8_AArch64_neon (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq8_AArch64_neon (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  } else if (iWidth == 4) {
    McHorVer20WidthEq4_AArch64_neon (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq4_AArch64_neon (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq4_AArch64_neon (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  }
}
void McHorVer32_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                              int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pVerTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pCtrTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer02WidthEq16_AArch64_neon (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    McHorVer22WidthEq16_AArch64_neon (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq16_AArch64_neon (pDst, iDstStride, pVerTmp, 16, pCtrTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer02WidthEq8_AArch64_neon (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    McHorVer22WidthEq8_AArch64_neon (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq8_AArch64_neon (pDst, iDstStride, pVerTmp, 16, pCtrTmp, 16, iHeight);
  } else if (iWidth == 4) {
    McHorVer02WidthEq4_AArch64_neon (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    McHorVer22WidthEq4_AArch64_neon (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq4_AArch64_neon (pDst, iDstStride, pVerTmp, 16, pCtrTmp, 16, iHeight);
  }
}
void McHorVer33_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                              int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pVerTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_AArch64_neon (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq16_AArch64_neon (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq16_AArch64_neon (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_AArch64_neon (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq8_AArch64_neon (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq8_AArch64_neon (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  } else if (iWidth == 4) {
    McHorVer20WidthEq4_AArch64_neon (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq4_AArch64_neon (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq4_AArch64_neon (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  }
}

void McLuma_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                          int16_t iMvX, int16_t iMvY, int32_t iWidth, int32_t iHeight) {
  static const PWelsMcWidthHeightFunc pWelsMcFunc[4][4] = { //[x][y]
    {McCopy_AArch64_neon,  McHorVer01_AArch64_neon, McHorVer02_AArch64_neon,    McHorVer03_AArch64_neon},
    {McHorVer10_AArch64_neon, McHorVer11_AArch64_neon, McHorVer12_AArch64_neon, McHorVer13_AArch64_neon},
    {McHorVer20_AArch64_neon,    McHorVer21_AArch64_neon, McHorVer22_AArch64_neon,    McHorVer23_AArch64_neon},
    {McHorVer30_AArch64_neon, McHorVer31_AArch64_neon, McHorVer32_AArch64_neon, McHorVer33_AArch64_neon},
  };
  // pSrc += (iMvY >> 2) * iSrcStride + (iMvX >> 2);
  pWelsMcFunc[iMvX & 0x03][iMvY & 0x03] (pSrc, iSrcStride, pDst, iDstStride, iWidth, iHeight);
}
void McChroma_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                            int16_t iMvX, int16_t iMvY, int32_t iWidth, int32_t iHeight) {
  if (0 == iMvX && 0 == iMvY) {
    if (8 == iWidth)
      McCopyWidthEq8_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
    else if (iWidth == 4)
      McCopyWidthEq4_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, iHeight);
    else //here iWidth == 2
      McCopyWidthEq2_c (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  } else {
    const int32_t kiD8x = iMvX & 0x07;
    const int32_t kiD8y = iMvY & 0x07;
    if (8 == iWidth)
      McChromaWidthEq8_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, (int32_t*) (g_kuiABCD[kiD8y][kiD8x]), iHeight);
    else if (4 == iWidth)
      McChromaWidthEq4_AArch64_neon (pSrc, iSrcStride, pDst, iDstStride, (int32_t*) (g_kuiABCD[kiD8y][kiD8x]), iHeight);
    else //here iWidth == 2
      McChromaWithFragMv_c (pSrc, iSrcStride, pDst, iDstStride, iMvX, iMvY, iWidth, iHeight);
  }
}
void PixelAvg_AArch64_neon (uint8_t* pDst, int32_t iDstStride, const uint8_t* pSrcA, int32_t iSrcAStride,
                            const uint8_t* pSrcB, int32_t iSrcBStride, int32_t iWidth, int32_t iHeight) {
  static const PWelsSampleWidthAveragingFunc kpfFuncs[2] = {
    PixStrideAvgWidthEq8_AArch64_neon,
    PixStrideAvgWidthEq16_AArch64_neon
  };
  kpfFuncs[iWidth >> 4] (pDst, iDstStride, pSrcA, iSrcAStride, pSrcB, iSrcBStride, iHeight);
}
#endif

#if defined(HAVE_MMI)
#define MMI_LOAD_8P(f0, f2, f4, r0) \
  "gsldlc1    "#f0", 0x7("#r0")               \n\t" \
  "gsldrc1    "#f0", 0x0("#r0")               \n\t" \
  "punpckhbh  "#f2", "#f0", "#f4"             \n\t" \
  "punpcklbh  "#f0", "#f0", "#f4"             \n\t"

#define FILTER_HV_W4(f0, f2, f4, f6, f8, f10, f12, f14, f16, f18, \
                     f20, f22, f24, f26, f28, f30, r0, r1, r2) \
  "paddh      "#f0", "#f0", "#f20"            \n\t" \
  "paddh      "#f2", "#f2", "#f22"            \n\t" \
  "mov.d      "#f28", "#f8"                   \n\t" \
  "mov.d      "#f30", "#f10"                  \n\t" \
  "mov.d      "#f24", "#f4"                   \n\t" \
  "mov.d      "#f26", "#f6"                   \n\t" \
  "dmfc1      "#r2", "#f8"                    \n\t" \
  "dli        "#r1", 0x0010001000100010       \n\t" \
  "dmtc1      "#r1", "#f8"                    \n\t" \
  "paddh      "#f0", "#f0", "#f8"             \n\t" \
  "paddh      "#f2", "#f2", "#f8"             \n\t" \
  "paddh      "#f28", "#f28", "#f12"          \n\t" \
  "paddh      "#f30", "#f30", "#f14"          \n\t" \
  "paddh      "#f24", "#f24", "#f16"          \n\t" \
  "paddh      "#f26", "#f26", "#f18"          \n\t" \
  "dli        "#r1", 0x2                      \n\t" \
  "dmtc1      "#r1", "#f8"                    \n\t" \
  "psllh      "#f28", "#f28", "#f8"           \n\t" \
  "psllh      "#f30", "#f30", "#f8"           \n\t" \
  "psubh      "#f28", "#f28", "#f24"          \n\t" \
  "psubh      "#f30", "#f30", "#f26"          \n\t" \
  "paddh      "#f0", "#f0", "#f28"            \n\t" \
  "paddh      "#f2", "#f2", "#f30"            \n\t" \
  "psllh      "#f28", "#f28", "#f8"           \n\t" \
  "psllh      "#f30", "#f30", "#f8"           \n\t" \
  "paddh      "#f0", "#f0", "#f28"            \n\t" \
  "paddh      "#f2", "#f2", "#f30"            \n\t" \
  "dli        "#r1", 0x5                      \n\t" \
  "dmtc1      "#r1", "#f8"                    \n\t" \
  "psrah      "#f0", "#f0", "#f8"             \n\t" \
  "psrah      "#f2", "#f2", "#f8"             \n\t" \
  "xor        "#f28", "#f28", "#f28"          \n\t" \
  "packushb   "#f0", "#f0", "#f2"             \n\t" \
  "gsswlc1    "#f0", 0x3("#r0")               \n\t" \
  "gsswrc1    "#f0", 0x0("#r0")               \n\t" \
  "dmtc1      "#r2", "#f8"                    \n\t"

#define FILTER_HV_W8(f0, f2, f4, f6, f8, f10, f12, f14, f16, f18, \
                     f20, f22, f24, f26, f28, f30, r0, r1, r2) \
  "paddh      "#f0", "#f0", "#f20"            \n\t" \
  "paddh      "#f2", "#f2", "#f22"            \n\t" \
  "mov.d      "#f28", "#f8"                   \n\t" \
  "mov.d      "#f30", "#f10"                  \n\t" \
  "mov.d      "#f24", "#f4"                   \n\t" \
  "mov.d      "#f26", "#f6"                   \n\t" \
  "dmfc1      "#r2", "#f8"                    \n\t" \
  "dli        "#r1", 0x0010001000100010       \n\t" \
  "dmtc1      "#r1", "#f8"                    \n\t" \
  "paddh      "#f0", "#f0", "#f8"             \n\t" \
  "paddh      "#f2", "#f2", "#f8"             \n\t" \
  "paddh      "#f28", "#f28", "#f12"          \n\t" \
  "paddh      "#f30", "#f30", "#f14"          \n\t" \
  "paddh      "#f24", "#f24", "#f16"          \n\t" \
  "paddh      "#f26", "#f26", "#f18"          \n\t" \
  "dli        "#r1", 0x2                      \n\t" \
  "dmtc1      "#r1", "#f8"                    \n\t" \
  "psllh      "#f28", "#f28", "#f8"           \n\t" \
  "psllh      "#f30", "#f30", "#f8"           \n\t" \
  "psubh      "#f28", "#f28", "#f24"          \n\t" \
  "psubh      "#f30", "#f30", "#f26"          \n\t" \
  "paddh      "#f0", "#f0", "#f28"            \n\t" \
  "paddh      "#f2", "#f2", "#f30"            \n\t" \
  "psllh      "#f28", "#f28", "#f8"           \n\t" \
  "psllh      "#f30", "#f30", "#f8"           \n\t" \
  "paddh      "#f0", "#f0", "#f28"            \n\t" \
  "paddh      "#f2", "#f2", "#f30"            \n\t" \
  "dli        "#r1", 0x5                      \n\t" \
  "dmtc1      "#r1", "#f8"                    \n\t" \
  "psrah      "#f0", "#f0", "#f8"             \n\t" \
  "psrah      "#f2", "#f2", "#f8"             \n\t" \
  "xor        "#f28", "#f28", "#f28"          \n\t" \
  "packushb   "#f0", "#f0", "#f2"             \n\t" \
  "gssdlc1    "#f0", 0x7("#r0")               \n\t" \
  "gssdrc1    "#f0", 0x0("#r0")               \n\t" \
  "dmtc1      "#r2", "#f8"                    \n\t"

#define FILTER_VER_ALIGN(f0, f2, f4, f6, f8, f10, f12, f14, f16, f18, \
                         f20, f22, f24, f26, f28, f30, r0, r1, r2, r3, r4) \
  "paddh      "#f0", "#f0", "#f20"            \n\t" \
  "paddh      "#f2", "#f2", "#f22"            \n\t" \
  "mov.d      "#f24", "#f4"                   \n\t" \
  "mov.d      "#f26", "#f6"                   \n\t" \
  "mov.d      "#f28", "#f8"                   \n\t" \
  "mov.d      "#f30", "#f10"                  \n\t" \
  "dli        "#r2", 0x2                      \n\t" \
  "paddh      "#f24", "#f24", "#f16"          \n\t" \
  "paddh      "#f26", "#f26", "#f18"          \n\t" \
  "dmfc1      "#r3", "#f8"                    \n\t" \
  "paddh      "#f28", "#f28", "#f12"          \n\t" \
  "paddh      "#f30", "#f30", "#f14"          \n\t" \
  "dmtc1      "#r2", "#f8"                    \n\t" \
  "psubh      "#f0", "#f0", "#f24"            \n\t" \
  "psubh      "#f2", "#f2", "#f26"            \n\t" \
  "psrah      "#f0", "#f0", "#f8"             \n\t" \
  "psrah      "#f2", "#f2", "#f8"             \n\t" \
  "paddh      "#f0", "#f0", "#f28"            \n\t" \
  "paddh      "#f2", "#f2", "#f30"            \n\t" \
  "psubh      "#f0", "#f0", "#f24"            \n\t" \
  "psubh      "#f2", "#f2", "#f26"            \n\t" \
  "psrah      "#f0", "#f0", "#f8"             \n\t" \
  "psrah      "#f2", "#f2", "#f8"             \n\t" \
  "dmtc1      "#r4", "#f8"                    \n\t" \
  "paddh      "#f28", "#f28", "#f0"           \n\t" \
  "paddh      "#f30", "#f30", "#f2"           \n\t" \
  "dli        "#r2", 0x6                      \n\t" \
  "paddh      "#f28", "#f28", "#f8"           \n\t" \
  "paddh      "#f30", "#f30", "#f8"           \n\t" \
  "dmtc1      "#r2", "#f8"                    \n\t" \
  "psrah      "#f28", "#f28", "#f8"           \n\t" \
  "psrah      "#f30", "#f30", "#f8"           \n\t" \
  "packushb   "#f28", "#f28", "#f30"          \n\t" \
  "gssdxc1    "#f28", 0x0("#r0", "#r1")       \n\t" \
  "dmtc1      "#r3", "#f8"                    \n\t"

#define FILTER_VER_UNALIGN(f0, f2, f4, f6, f8, f10, f12, f14, f16, f18, \
                           f20, f22, f24, f26, f28, f30, r0, r1, r2, r3) \
  "paddh      "#f0", "#f0", "#f20"            \n\t" \
  "paddh      "#f2", "#f2", "#f22"            \n\t" \
  "mov.d      "#f24", "#f4"                   \n\t" \
  "mov.d      "#f26", "#f6"                   \n\t" \
  "mov.d      "#f28", "#f8"                   \n\t" \
  "mov.d      "#f30", "#f10"                  \n\t" \
  "dli        "#r1", 0x2                      \n\t" \
  "paddh      "#f24", "#f24", "#f16"          \n\t" \
  "paddh      "#f26", "#f26", "#f18"          \n\t" \
  "dmfc1      "#r2", "#f8"                    \n\t" \
  "paddh      "#f28", "#f28", "#f12"          \n\t" \
  "paddh      "#f30", "#f30", "#f14"          \n\t" \
  "dmtc1      "#r1", "#f8"                    \n\t" \
  "psubh      "#f0", "#f0", "#f24"            \n\t" \
  "psubh      "#f2", "#f2", "#f26"            \n\t" \
  "psrah      "#f0", "#f0", "#f8"             \n\t" \
  "psrah      "#f2", "#f2", "#f8"             \n\t" \
  "paddh      "#f0", "#f0", "#f28"            \n\t" \
  "paddh      "#f2", "#f2", "#f30"            \n\t" \
  "psubh      "#f0", "#f0", "#f24"            \n\t" \
  "psubh      "#f2", "#f2", "#f26"            \n\t" \
  "psrah      "#f0", "#f0", "#f8"             \n\t" \
  "psrah      "#f2", "#f2", "#f8"             \n\t" \
  "dmtc1      "#r3", "#f8"                    \n\t" \
  "paddh      "#f28", "#f28", "#f0"           \n\t" \
  "paddh      "#f30", "#f30", "#f2"           \n\t" \
  "dli        "#r1", 0x6                      \n\t" \
  "paddh      "#f28", "#f28", "#f8"           \n\t" \
  "paddh      "#f30", "#f30", "#f8"           \n\t" \
  "dmtc1      "#r1", "#f8"                    \n\t" \
  "psrah      "#f28", "#f28", "#f8"           \n\t" \
  "psrah      "#f30", "#f30", "#f8"           \n\t" \
  "packushb   "#f28", "#f28", "#f30"          \n\t" \
  "gssdlc1    "#f28", 0x7("#r0")              \n\t" \
  "gssdrc1    "#f28", 0x0("#r0")              \n\t" \
  "dmtc1      "#r2", "#f8"                    \n\t"

void McHorVer20Width5_mmi(const uint8_t *pSrc, int32_t iSrcStride, uint8_t *pDst,
                          int32_t iDstStride, int32_t iWidth, int32_t iHeight) {
  BACKUP_REG;
  __asm__ volatile (
    ".set       arch=loongson3a                 \n\t"
    "xor        $f28, $f28, $f28                \n\t"
    PTR_ADDIU  "%[pSrc], %[pSrc], -0x2          \n\t"
    "dli        $8, 0x2                         \n\t"
    "dli        $10, 0x0010001000100010         \n\t"
    "dli        $11, 0x5                        \n\t"
    "1:                                         \n\t"
    "xor        $f28, $f28, $f28                \n\t"
    "gsldlc1    $f0, 0x7(%[pSrc])               \n\t"
    "gsldlc1    $f4, 0xc(%[pSrc])               \n\t"
    "gsldlc1    $f8, 0x8(%[pSrc])               \n\t"
    "gsldlc1    $f12, 0xb(%[pSrc])              \n\t"
    "gsldlc1    $f16, 0x9(%[pSrc])              \n\t"
    "gsldlc1    $f20, 0xa(%[pSrc])              \n\t"
    "gsldrc1    $f0, 0x0(%[pSrc])               \n\t"
    "gsldrc1    $f4, 0x5(%[pSrc])               \n\t"
    "gsldrc1    $f8, 0x1(%[pSrc])               \n\t"
    "gsldrc1    $f12, 0x4(%[pSrc])              \n\t"
    "gsldrc1    $f16, 0x2(%[pSrc])              \n\t"
    "gsldrc1    $f20, 0x3(%[pSrc])              \n\t"
    "punpckhbh  $f2, $f0, $f28                  \n\t"
    "punpckhbh  $f6, $f4, $f28                  \n\t"
    "punpckhbh  $f10, $f8, $f28                 \n\t"
    "punpckhbh  $f14, $f12, $f28                \n\t"
    "punpckhbh  $f18, $f16, $f28                \n\t"
    "punpckhbh  $f22, $f20, $f28                \n\t"
    "punpcklbh  $f0, $f0, $f28                  \n\t"
    "punpcklbh  $f4, $f4, $f28                  \n\t"
    "punpcklbh  $f8, $f8, $f28                  \n\t"
    "punpcklbh  $f12, $f12, $f28                \n\t"
    "punpcklbh  $f16, $f16, $f28                \n\t"
    "punpcklbh  $f20, $f20, $f28                \n\t"

    "mov.d      $f28, $f8                       \n\t"
    "mov.d      $f30, $f10                      \n\t"
    "paddh      $f28, $f28, $f12                \n\t"
    "paddh      $f30, $f30, $f14                \n\t"
    "mov.d      $f24, $f16                      \n\t"
    "mov.d      $f26, $f18                      \n\t"
    "paddh      $f24, $f24, $f20                \n\t"
    "paddh      $f26, $f26, $f22                \n\t"
    "dmfc1      $9, $f12                        \n\t"
    "dmtc1      $8, $f12                        \n\t"
    "psllh      $f24, $f24, $f12                \n\t"
    "psllh      $f26, $f26, $f12                \n\t"
    "psubh      $f24, $f24, $f28                \n\t"
    "psubh      $f26, $f26, $f30                \n\t"
    "paddh      $f0, $f0, $f4                   \n\t"
    "paddh      $f2, $f2, $f6                   \n\t"
    "paddh      $f0, $f0, $f24                  \n\t"
    "paddh      $f2, $f2, $f26                  \n\t"
    "psllh      $f24, $f24, $f12                \n\t"
    "psllh      $f26, $f26, $f12                \n\t"
    "paddh      $f0, $f0, $f24                  \n\t"
    "paddh      $f2, $f2, $f26                  \n\t"

    "dmtc1      $10, $f12                       \n\t"
    "paddh      $f0, $f0, $f12                  \n\t"
    "paddh      $f2, $f2, $f12                  \n\t"
    "dmtc1      $11, $f12                       \n\t"
    "psrah      $f0, $f0, $f12                  \n\t"
    "psrah      $f2, $f2, $f12                  \n\t"
    "packushb   $f0, $f0, $f2                   \n\t"

    "gsswlc1    $f0, 0x3(%[pDst])               \n\t"
    "gsswrc1    $f0, 0x0(%[pDst])               \n\t"

    "gsldlc1    $f0, 0xd(%[pSrc])               \n\t"
    "xor        $f28, $f28, $f28                \n\t"
    "gsldrc1    $f0, 0x6(%[pSrc])               \n\t"
    "punpckhbh  $f2, $f0, $f28                  \n\t"
    "punpcklbh  $f0, $f0, $f28                  \n\t"
    "dmtc1      $9, $f12                        \n\t"
    "dmtc1      $8, $f24                        \n\t"

    "paddh      $f16, $f16, $f4                 \n\t"
    "paddh      $f18, $f18, $f6                 \n\t"
    "paddh      $f20, $f20, $f12                \n\t"
    "paddh      $f22, $f22, $f14                \n\t"
    "psllh      $f20, $f20, $f24                \n\t"
    "psllh      $f22, $f22, $f24                \n\t"
    "psubh      $f20, $f20, $f16                \n\t"
    "psubh      $f22, $f22, $f18                \n\t"
    "paddh      $f8, $f8, $f0                   \n\t"
    "paddh      $f10, $f10, $f2                 \n\t"
    "paddh      $f8, $f8, $f20                  \n\t"
    "paddh      $f10, $f10, $f22                \n\t"
    "psllh      $f20, $f20, $f24                \n\t"
    "psllh      $f22, $f22, $f24                \n\t"
    "paddh      $f8, $f8, $f20                  \n\t"
    "paddh      $f10, $f10, $f22                \n\t"

    "dmtc1      $10, $f24                       \n\t"
    "paddh      $f8, $f8, $f24                  \n\t"
    "paddh      $f10, $f10, $f24                \n\t"
    "dmtc1      $11, $f24                       \n\t"
    "psrah      $f8, $f8, $f24                  \n\t"
    "psrah      $f10, $f10, $f24                \n\t"
    "packushb   $f8, $f8, $f10                  \n\t"
    "gsswlc1    $f8, 0x4(%[pDst])               \n\t"
    "gsswrc1    $f8, 0x1(%[pDst])               \n\t"

    PTR_ADDU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "bnez       %[iHeight], 1b                  \n\t"
    : [pSrc]"+&r"((unsigned char *)pSrc), [pDst]"+&r"((unsigned char *)pDst),
      [iWidth]"+&r"((int)iWidth), [iHeight]"+&r"((int)iHeight)
    : [iSrcStride]"r"((int)iSrcStride),  [iDstStride]"r"((int)iDstStride)
    : "memory", "$8", "$9", "$10", "$11", "$f0", "$f2", "$f4", "$f6", "$f8",
      "$f10", "$f12", "$f14", "$f16", "$f18", "$f20", "$f22", "$f24", "$f26",
      "$f28", "$f30"
  );
  RECOVER_REG;
}

void McHorVer20Width9Or17_mmi(const uint8_t *pSrc, int32_t iSrcStride, uint8_t *pDst,
                              int32_t iDstStride, int32_t iWidth, int32_t iHeight) {
  BACKUP_REG;
  __asm__ volatile (
    ".set       arch=loongson3a                 \n\t"
    PTR_ADDIU  "%[pSrc], %[pSrc], -0x2          \n\t"
    "xor        $f28, $f28, $f28                \n\t"
    "dli        $8, 0x2                         \n\t"
    "dli        $9, 0x9                         \n\t"
    "dli        $10, 0x0010001000100010         \n\t"
    "dli        $11, 0x5                        \n\t"
    "bne        %[iWidth], $9, 2f               \n\t"
    "1:                                         \n\t"
    "xor        $f28, $f28, $f28                \n\t"
    "gsldlc1    $f0, 0x7(%[pSrc])               \n\t"
    "gsldlc1    $f4, 0xc(%[pSrc])               \n\t"
    "gsldlc1    $f8, 0x8(%[pSrc])               \n\t"
    "gsldlc1    $f12, 0xb(%[pSrc])              \n\t"
    "gsldlc1    $f16, 0x9(%[pSrc])              \n\t"
    "gsldlc1    $f20, 0xa(%[pSrc])              \n\t"
    "gsldrc1    $f0, 0x0(%[pSrc])               \n\t"
    "gsldrc1    $f4, 0x5(%[pSrc])               \n\t"
    "gsldrc1    $f8, 0x1(%[pSrc])               \n\t"
    "gsldrc1    $f12, 0x4(%[pSrc])              \n\t"
    "gsldrc1    $f16, 0x2(%[pSrc])              \n\t"
    "gsldrc1    $f20, 0x3(%[pSrc])              \n\t"
    "punpckhbh  $f2, $f0, $f28                  \n\t"
    "punpckhbh  $f6, $f4, $f28                  \n\t"
    "punpckhbh  $f10, $f8, $f28                 \n\t"
    "punpckhbh  $f14, $f12, $f28                \n\t"
    "punpckhbh  $f18, $f16, $f28                \n\t"
    "punpckhbh  $f22, $f20, $f28                \n\t"
    "punpcklbh  $f0, $f0, $f28                  \n\t"
    "punpcklbh  $f4, $f4, $f28                  \n\t"
    "punpcklbh  $f8, $f8, $f28                  \n\t"
    "punpcklbh  $f12, $f12, $f28                \n\t"
    "punpcklbh  $f16, $f16, $f28                \n\t"
    "punpcklbh  $f20, $f20, $f28                \n\t"

    "mov.d      $f28, $f8                       \n\t"
    "mov.d      $f30, $f10                      \n\t"
    "paddh      $f28, $f28, $f12                \n\t"
    "paddh      $f30, $f30, $f14                \n\t"
    "mov.d      $f24, $f16                      \n\t"
    "mov.d      $f26, $f18                      \n\t"
    "paddh      $f24, $f24, $f20                \n\t"
    "paddh      $f26, $f26, $f22                \n\t"
    "dmfc1      $9, $f12                        \n\t"
    "dmtc1      $8, $f12                        \n\t"
    "psllh      $f24, $f24, $f12                \n\t"
    "psllh      $f26, $f26, $f12                \n\t"
    "psubh      $f24, $f24, $f28                \n\t"
    "psubh      $f26, $f26, $f30                \n\t"
    "paddh      $f0, $f0, $f4                   \n\t"
    "paddh      $f2, $f2, $f6                   \n\t"
    "paddh      $f0, $f0, $f24                  \n\t"
    "paddh      $f2, $f2, $f26                  \n\t"
    "psllh      $f24, $f24, $f12                \n\t"
    "psllh      $f26, $f26, $f12                \n\t"
    "paddh      $f0, $f0, $f24                  \n\t"
    "paddh      $f2, $f2, $f26                  \n\t"

    "dmtc1      $10, $f12                       \n\t"
    "paddh      $f0, $f0, $f12                  \n\t"
    "paddh      $f2, $f2, $f12                  \n\t"
    "dmtc1      $11, $f12                       \n\t"
    "psrah      $f0, $f0, $f12                  \n\t"
    "psrah      $f2, $f2, $f12                  \n\t"
    "packushb   $f0, $f0, $f2                   \n\t"

    "gsswlc1    $f0, 0x3(%[pDst])               \n\t"
    "gsswrc1    $f0, 0x0(%[pDst])               \n\t"

    "gsldlc1    $f0, 0xd(%[pSrc])               \n\t"
    "xor        $f28, $f28, $f28                \n\t"
    "gsldrc1    $f0, 0x6(%[pSrc])               \n\t"
    "punpckhbh  $f2, $f0, $f28                  \n\t"
    "punpcklbh  $f0, $f0, $f28                  \n\t"
    "dmtc1      $9, $f12                        \n\t"
    "dmtc1      $8, $f24                        \n\t"

    "paddh      $f16, $f16, $f4                 \n\t"
    "paddh      $f18, $f18, $f6                 \n\t"
    "paddh      $f20, $f20, $f12                \n\t"
    "paddh      $f22, $f22, $f14                \n\t"
    "psllh      $f20, $f20, $f24                \n\t"
    "psllh      $f22, $f22, $f24                \n\t"
    "psubh      $f20, $f20, $f16                \n\t"
    "psubh      $f22, $f22, $f18                \n\t"
    "paddh      $f8, $f8, $f0                   \n\t"
    "paddh      $f10, $f10, $f2                 \n\t"
    "paddh      $f8, $f8, $f20                  \n\t"
    "paddh      $f10, $f10, $f22                \n\t"
    "psllh      $f20, $f20, $f24                \n\t"
    "psllh      $f22, $f22, $f24                \n\t"
    "paddh      $f8, $f8, $f20                  \n\t"
    "paddh      $f10, $f10, $f22                \n\t"

    "dmtc1      $10, $f24                       \n\t"
    "paddh      $f8, $f8, $f24                  \n\t"
    "paddh      $f10, $f10, $f24                \n\t"
    "dmtc1      $11, $f24                       \n\t"
    "psrah      $f8, $f8, $f24                  \n\t"
    "psrah      $f10, $f10, $f24                \n\t"
    "packushb   $f8, $f8, $f10                  \n\t"
    "gssdlc1    $f8, 0x8(%[pDst])               \n\t"
    "gssdrc1    $f8, 0x1(%[pDst])               \n\t"

    PTR_ADDU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "bnez       %[iHeight], 1b                  \n\t"
    "j          3f                              \n\t"

    "2:                                         \n\t"
    "xor        $f28, $f28, $f28                \n\t"
    "gsldlc1    $f0, 0x7(%[pSrc])               \n\t"
    "gsldlc1    $f4, 0xc(%[pSrc])               \n\t"
    "gsldlc1    $f8, 0x8(%[pSrc])               \n\t"
    "gsldlc1    $f12, 0xb(%[pSrc])              \n\t"
    "gsldlc1    $f16, 0x9(%[pSrc])              \n\t"
    "gsldlc1    $f20, 0xa(%[pSrc])              \n\t"
    "gsldrc1    $f0, 0x0(%[pSrc])               \n\t"
    "gsldrc1    $f4, 0x5(%[pSrc])               \n\t"
    "gsldrc1    $f8, 0x1(%[pSrc])               \n\t"
    "gsldrc1    $f12, 0x4(%[pSrc])              \n\t"
    "gsldrc1    $f16, 0x2(%[pSrc])              \n\t"
    "gsldrc1    $f20, 0x3(%[pSrc])              \n\t"
    "punpckhbh  $f2, $f0, $f28                  \n\t"
    "punpckhbh  $f6, $f4, $f28                  \n\t"
    "punpckhbh  $f10, $f8, $f28                 \n\t"
    "punpckhbh  $f14, $f12, $f28                \n\t"
    "punpckhbh  $f18, $f16, $f28                \n\t"
    "punpckhbh  $f22, $f20, $f28                \n\t"
    "punpcklbh  $f0, $f0, $f28                  \n\t"
    "punpcklbh  $f4, $f4, $f28                  \n\t"
    "punpcklbh  $f8, $f8, $f28                  \n\t"
    "punpcklbh  $f12, $f12, $f28                \n\t"
    "punpcklbh  $f16, $f16, $f28                \n\t"
    "punpcklbh  $f20, $f20, $f28                \n\t"

    "dmtc1      $8, $f30                        \n\t"
    "paddh      $f8, $f8, $f12                  \n\t"
    "paddh      $f10, $f10, $f14                \n\t"
    "paddh      $f16, $f16, $f20                \n\t"
    "paddh      $f18, $f18, $f22                \n\t"
    "psllh      $f16, $f16, $f30                \n\t"
    "psllh      $f18, $f18, $f30                \n\t"
    "psubh      $f16, $f16, $f8                 \n\t"
    "psubh      $f18, $f18, $f10                \n\t"
    "paddh      $f0, $f0, $f4                   \n\t"
    "paddh      $f2, $f2, $f6                   \n\t"
    "paddh      $f0, $f0, $f16                  \n\t"
    "paddh      $f2, $f2, $f18                  \n\t"
    "psllh      $f16, $f16, $f30                \n\t"
    "psllh      $f18, $f18, $f30                \n\t"
    "paddh      $f0, $f0, $f16                  \n\t"
    "paddh      $f2, $f2, $f18                  \n\t"

    "dmtc1      $10, $f30                       \n\t"
    "paddh      $f0, $f0, $f30                  \n\t"
    "paddh      $f2, $f2, $f30                  \n\t"
    "dmtc1      $11, $f30                       \n\t"
    "psrah      $f0, $f0, $f30                  \n\t"
    "psrah      $f2, $f2, $f30                  \n\t"
    "packushb   $f0, $f0, $f2                   \n\t"
    "gssdlc1    $f0, 0x7(%[pDst])               \n\t"
    "gssdrc1    $f0, 0x0(%[pDst])               \n\t"

    "gsldlc1    $f0, 15(%[pSrc])                \n\t"
    "gsldlc1    $f4, 0x14(%[pSrc])              \n\t"
    "gsldlc1    $f8, 0x10(%[pSrc])              \n\t"
    "gsldlc1    $f12, 0x13(%[pSrc])             \n\t"
    "gsldlc1    $f16, 0x11(%[pSrc])             \n\t"
    "gsldlc1    $f20, 0x12(%[pSrc])             \n\t"
    "gsldrc1    $f0, 8(%[pSrc])                 \n\t"
    "gsldrc1    $f4, 0xd(%[pSrc])               \n\t"
    "gsldrc1    $f8, 0x9(%[pSrc])               \n\t"
    "gsldrc1    $f12, 0xc(%[pSrc])              \n\t"
    "gsldrc1    $f16, 0xa(%[pSrc])              \n\t"
    "gsldrc1    $f20, 0xb(%[pSrc])              \n\t"
    "punpckhbh  $f2, $f0, $f28                  \n\t"
    "punpckhbh  $f6, $f4, $f28                  \n\t"
    "punpckhbh  $f10, $f8, $f28                 \n\t"
    "punpckhbh  $f14, $f12, $f28                \n\t"
    "punpckhbh  $f18, $f16, $f28                \n\t"
    "punpckhbh  $f22, $f20, $f28                \n\t"
    "punpcklbh  $f0, $f0, $f28                  \n\t"
    "punpcklbh  $f4, $f4, $f28                  \n\t"
    "punpcklbh  $f8, $f8, $f28                  \n\t"
    "punpcklbh  $f12, $f12, $f28                \n\t"
    "punpcklbh  $f16, $f16, $f28                \n\t"
    "punpcklbh  $f20, $f20, $f28                \n\t"

    "mov.d      $f28, $f8                       \n\t"
    "mov.d      $f30, $f10                      \n\t"
    "paddh      $f28, $f28, $f12                \n\t"
    "paddh      $f30, $f30, $f14                \n\t"
    "mov.d      $f24, $f16                      \n\t"
    "mov.d      $f26, $f18                      \n\t"
    "paddh      $f24, $f24, $f20                \n\t"
    "paddh      $f26, $f26, $f22                \n\t"
    "dmfc1      $9, $f12                        \n\t"
    "dmtc1      $8, $f12                        \n\t"
    "psllh      $f24, $f24, $f12                \n\t"
    "psllh      $f26, $f26, $f12                \n\t"
    "psubh      $f24, $f24, $f28                \n\t"
    "psubh      $f26, $f26, $f30                \n\t"
    "paddh      $f0, $f0, $f4                   \n\t"
    "paddh      $f2, $f2, $f6                   \n\t"
    "paddh      $f0, $f0, $f24                  \n\t"
    "paddh      $f2, $f2, $f26                  \n\t"
    "psllh      $f24, $f24, $f12                \n\t"
    "psllh      $f26, $f26, $f12                \n\t"
    "paddh      $f0, $f0, $f24                  \n\t"
    "paddh      $f2, $f2, $f26                  \n\t"

    "dmtc1      $10, $f30                       \n\t"
    "paddh      $f0, $f0, $f30                  \n\t"
    "paddh      $f2, $f2, $f30                  \n\t"
    "dmtc1      $11, $f30                       \n\t"
    "psrah      $f0, $f0, $f30                  \n\t"
    "psrah      $f2, $f2, $f30                  \n\t"
    "packushb   $f0, $f0, $f2                   \n\t"
    "gsswlc1    $f0, 0xb(%[pDst])               \n\t"
    "gsswrc1    $f0, 0x8(%[pDst])               \n\t"

    "dmtc1      $9, $f12                        \n\t"
    "xor        $f28, $f28, $f28                \n\t"
    "dli        $9, 0x20                        \n\t"
    "gsldlc1    $f0, 0x15(%[pSrc])              \n\t"
    "dmtc1      $9, $f30                        \n\t"
    "gsldrc1    $f0, 0xE(%[pSrc])               \n\t"
    "punpckhbh  $f2, $f0, $f28                  \n\t"
    "punpcklbh  $f0, $f0, $f28                  \n\t"
    "dmtc1      $8, $f24                        \n\t"

    "paddh      $f16, $f16, $f4                 \n\t"
    "paddh      $f18, $f18, $f6                 \n\t"
    "paddh      $f20, $f20, $f12                \n\t"
    "paddh      $f22, $f22, $f14                \n\t"
    "psllh      $f20, $f20, $f24                \n\t"
    "psllh      $f22, $f22, $f24                \n\t"
    "psubh      $f20, $f20, $f16                \n\t"
    "psubh      $f22, $f22, $f18                \n\t"
    "paddh      $f8, $f8, $f0                   \n\t"
    "paddh      $f10, $f10, $f2                 \n\t"
    "paddh      $f8, $f8, $f20                  \n\t"
    "paddh      $f10, $f10, $f22                \n\t"
    "psllh      $f20, $f20, $f24                \n\t"
    "psllh      $f22, $f22, $f24                \n\t"
    "paddh      $f8, $f8, $f20                  \n\t"
    "paddh      $f10, $f10, $f22                \n\t"

    "dmtc1      $10, $f24                       \n\t"
    "paddh      $f8, $f8, $f24                  \n\t"
    "paddh      $f10, $f10, $f24                \n\t"
    "dmtc1      $11, $f24                       \n\t"
    "psrah      $f8, $f8, $f24                  \n\t"
    "psrah      $f10, $f10, $f24                \n\t"
    "packushb   $f8, $f8, $f10                  \n\t"
    "gssdlc1    $f8, 0x10(%[pDst])              \n\t"
    "gssdrc1    $f8, 0x9(%[pDst])               \n\t"

    PTR_ADDU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "bnez       %[iHeight], 2b                  \n\t"
    "3:                                         \n\t"
    : [pSrc]"+&r"((unsigned char *)pSrc), [pDst]"+&r"((unsigned char *)pDst),
      [iWidth]"+&r"((int)iWidth), [iHeight]"+&r"((int)iHeight)
    : [iSrcStride]"r"((int)iSrcStride),  [iDstStride]"r"((int)iDstStride)
    : "memory", "$8", "$9", "$10", "$11", "$f0", "$f2", "$f4", "$f6", "$f8",
      "$f10", "$f12", "$f14", "$f16", "$f18", "$f20", "$f22", "$f24", "$f26",
      "$f28", "$f30"
  );
  RECOVER_REG;
}

//horizontal filter to gain half sample, that is (2, 0) location in quarter sample
static inline void McHorVer20Width5Or9Or17_mmi(const uint8_t* pSrc, int32_t iSrcStride,
                                               uint8_t* pDst, int32_t iDstStride,
                                               int32_t iWidth, int32_t iHeight) {
  if (iWidth == 17 || iWidth == 9)
      McHorVer20Width9Or17_mmi(pSrc, iSrcStride, pDst, iDstStride, iWidth, iHeight);
  else //if (iWidth == 5)
      McHorVer20Width5_mmi(pSrc, iSrcStride, pDst, iDstStride, iWidth, iHeight);
}

void McHorVer02Height5_mmi(const uint8_t *pSrc, int32_t iSrcStride, uint8_t *pDst,
                           int32_t iDstStride, int32_t iWidth, int32_t iHeight) {
  BACKUP_REG;
  __asm__ volatile (
    ".set       arch=loongson3a                 \n\t"
    "move       $12, %[pSrc]                    \n\t"
    "move       $13, %[pDst]                    \n\t"
    "move       $14, %[iHeight]                 \n\t"

    "dsrl       %[iWidth], %[iWidth], 0x2       \n\t"
    PTR_ADDU   "$10, %[iSrcStride], %[iSrcStride] \n\t"
    PTR_SUBU   "%[pSrc], %[pSrc], $10           \n\t"

    "1:                                         \n\t"
    "xor        $f28, $f28, $f28                \n\t"
    MMI_LOAD_8P($f0, $f2, $f28, %[pSrc])
    PTR_ADDU   "$8, %[pSrc], %[iSrcStride]      \n\t"
    MMI_LOAD_8P($f4, $f6, $f28, $8)

    PTR_ADDU   "%[pSrc], %[pSrc], $10           \n\t"
    MMI_LOAD_8P($f8, $f10, $f28, %[pSrc])
    PTR_ADDU   "$8, %[pSrc], %[iSrcStride]      \n\t"
    MMI_LOAD_8P($f12, $f14, $f28, $8)
    PTR_ADDU   "%[pSrc], %[pSrc], $10           \n\t"
    MMI_LOAD_8P($f16, $f18, $f28, %[pSrc])
    PTR_ADDU   "$8, %[pSrc], %[iSrcStride]      \n\t"
    MMI_LOAD_8P($f20, $f22, $f28, $8)
    FILTER_HV_W4($f0, $f2, $f4, $f6, $f8, $f10, $f12, $f14, $f16, $f18, $f20,
                 $f22, $f24, $f26, $f28, $f30, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    PTR_ADDU   "%[pSrc], %[pSrc], $10           \n\t"
    MMI_LOAD_8P($f24, $f26, $f28, %[pSrc])
    "mov.d      $f0, $f4                        \n\t"
    "mov.d      $f2, $f6                        \n\t"
    "mov.d      $f4, $f8                        \n\t"
    "mov.d      $f6, $f10                       \n\t"
    "mov.d      $f8, $f12                       \n\t"
    "mov.d      $f10, $f14                      \n\t"
    "mov.d      $f12, $f16                      \n\t"
    "mov.d      $f14, $f18                      \n\t"
    "mov.d      $f16, $f20                      \n\t"
    "mov.d      $f18, $f22                      \n\t"
    "mov.d      $f20, $f24                      \n\t"
    "mov.d      $f22, $f26                      \n\t"

    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_SUBU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"

    "2:                                         \n\t"
    FILTER_HV_W4($f0, $f2, $f4, $f6, $f8, $f10, $f12, $f14, $f16, $f18, $f20,
                 $f22, $f24, $f26, $f28, $f30, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 3f                  \n\t"

    PTR_ADDU   "%[pSrc], %[pSrc], $10           \n\t"
    MMI_LOAD_8P($f24, $f26, $f28, %[pSrc])
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    FILTER_HV_W4($f4, $f6, $f8, $f10, $f12, $f14, $f16, $f18, $f20, $f22, $f24,
                 $f26, $f28, $f30, $f0, $f2, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 3f                  \n\t"

    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDU   "$8, %[pSrc], %[iSrcStride]      \n\t"
    MMI_LOAD_8P($f28, $f30, $f0, $8)
    FILTER_HV_W4($f8, $f10, $f12, $f14, $f16, $f18, $f20, $f22, $f24, $f26, $f28,
                 $f30, $f0, $f2, $f4, $f6, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 3f                  \n\t"

    PTR_ADDU   "%[pSrc], %[pSrc], $10           \n\t"
    MMI_LOAD_8P($f0, $f2, $f4, %[pSrc])
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    FILTER_HV_W4($f12, $f14, $f16, $f18, $f20, $f22, $f24, $f26, $f28, $f30, $f0,
                 $f2, $f4, $f6, $f8, $f10, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 3f                  \n\t"

    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDU   "$8, %[pSrc], %[iSrcStride]      \n\t"
    MMI_LOAD_8P($f4, $f6, $f8, $8)
    FILTER_HV_W4($f16, $f18, $f20, $f22, $f24, $f26, $f28, $f30, $f0, $f2, $f4, $f6,
                 $f8, $f10, $f12, $f14, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 3f                  \n\t"

    PTR_ADDU   "%[pSrc], %[pSrc], $10           \n\t"
    MMI_LOAD_8P($f8, $f10, $f12, %[pSrc])
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    FILTER_HV_W4($f20, $f22, $f24, $f26, $f28, $f30, $f0, $f2, $f4, $f6, $f8, $f10,
                 $f12, $f14, $f16, $f18, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 3f                  \n\t"

    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDU   "$8, %[pSrc], %[iSrcStride]      \n\t"
    MMI_LOAD_8P($f12, $f14, $f16, $8)
    FILTER_HV_W4($f24, $f26, $f28, $f30, $f0, $f2, $f4, $f6, $f8, $f10, $f12, $f14,
                 $f16, $f18, $f20, $f22, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 3f                  \n\t"

    PTR_ADDU   "%[pSrc], %[pSrc], $10           \n\t"
    MMI_LOAD_8P($f16, $f18, $f20, %[pSrc])
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    FILTER_HV_W4($f28, $f30, $f0, $f2, $f4, $f6, $f8, $f10, $f12, $f14, $f16, $f18,
                 $f20, $f22, $f24, $f26, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 3f                  \n\t"

    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDU   "$8, %[pSrc], %[iSrcStride]      \n\t"
    MMI_LOAD_8P($f20, $f22, $f24, $8)
    "j          2b                              \n\t"

    "3:                                         \n\t"
    PTR_ADDIU  "%[iWidth], %[iWidth], -0x1      \n\t"
    "beqz       %[iWidth], 4f                   \n\t"
    "move       %[pSrc], $12                    \n\t"
    "move       %[pDst], $13                    \n\t"
    "move       %[iHeight], $14                 \n\t"
    PTR_SUBU   "%[pSrc], %[pSrc], $10           \n\t"
    PTR_ADDIU  "%[pSrc], %[pSrc], 0x4           \n\t"
    PTR_ADDIU  "%[pDst], %[pDst], 0x4           \n\t"
    "j          1b                              \n\t"
    "4:                                         \n\t"
    : [pSrc]"+&r"((unsigned char *)pSrc), [pDst]"+&r"((unsigned char *)pDst),
      [iWidth]"+&r"(iWidth), [iHeight]"+&r"(iHeight)
    : [iSrcStride]"r"(iSrcStride),  [iDstStride]"r"(iDstStride)
    : "memory", "$8", "$9", "$10", "$12", "$13", "$14", "$f0", "$f2", "$f4",
      "$f6", "$f8", "$f10", "$f12", "$f14", "$f16", "$f18", "$f20", "$f22",
      "$f24", "$f26", "$f28", "$f30"
  );
  RECOVER_REG;
}

void McHorVer02Height9Or17_mmi(const uint8_t *pSrc, int32_t iSrcStride, uint8_t *pDst,
                               int32_t iDstStride, int32_t iWidth, int32_t iHeight) {
  BACKUP_REG;
  __asm__ volatile (
    ".set       arch=loongson3a                 \n\t"
    "move       $12, %[pSrc]                    \n\t"
    "move       $13, %[pDst]                    \n\t"
    "move       $14, %[iHeight]                 \n\t"

    "dsrl       %[iWidth], %[iWidth], 0x3       \n\t"
    PTR_ADDU   "$10, %[iSrcStride], %[iSrcStride] \n\t"
    PTR_SUBU   "%[pSrc], %[pSrc], $10           \n\t"

    "1:                                         \n\t"
    "dli        $8, 0x20                        \n\t"
    "xor        $f28, $f28, $f28                \n\t"
    "dmtc1      $8, $f30                        \n\t"

    MMI_LOAD_8P($f0, $f2, $f28, %[pSrc])
    PTR_ADDU   "$8, %[pSrc], %[iSrcStride]      \n\t"
    MMI_LOAD_8P($f4, $f6, $f28, $8)
    PTR_ADDU   "%[pSrc], %[pSrc], $10           \n\t"
    MMI_LOAD_8P($f8, $f10, $f28, %[pSrc])
    PTR_ADDU   "$8, %[pSrc], %[iSrcStride]      \n\t"
    MMI_LOAD_8P($f12, $f14, $f28, $8)
    PTR_ADDU   "%[pSrc], %[pSrc], $10           \n\t"
    MMI_LOAD_8P($f16, $f18, $f28, %[pSrc])
    PTR_ADDU   "$8, %[pSrc], %[iSrcStride]      \n\t"
    MMI_LOAD_8P($f20, $f22, $f28, $8)
    FILTER_HV_W8($f0, $f2, $f4, $f6, $f8, $f10, $f12, $f14, $f16, $f18, $f20,
                 $f22, $f24, $f26, $f28, $f30, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    PTR_ADDU   "%[pSrc], %[pSrc], $10           \n\t"
    MMI_LOAD_8P($f24, $f26, $f28, %[pSrc])
    "mov.d      $f0, $f4                        \n\t"
    "mov.d      $f2, $f6                        \n\t"
    "mov.d      $f4, $f8                        \n\t"
    "mov.d      $f6, $f10                       \n\t"
    "mov.d      $f8, $f12                       \n\t"
    "mov.d      $f10, $f14                      \n\t"
    "mov.d      $f12, $f16                      \n\t"
    "mov.d      $f14, $f18                      \n\t"
    "mov.d      $f16, $f20                      \n\t"
    "mov.d      $f18, $f22                      \n\t"
    "mov.d      $f20, $f24                      \n\t"
    "mov.d      $f22, $f26                      \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_SUBU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"

    "2:                                         \n\t"
    FILTER_HV_W8($f0, $f2, $f4, $f6, $f8, $f10, $f12, $f14, $f16, $f18, $f20,
                 $f22, $f24, $f26, $f28, $f30, %[pDst], $8, $9)
    "dmtc1      $9, $f8                         \n\t"
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 3f                  \n\t"

    PTR_ADDU   "%[pSrc], %[pSrc], $10           \n\t"
    MMI_LOAD_8P($f24, $f26, $f28, %[pSrc])
    PTR_ADDU   "%[pDst],  %[pDst], %[iDstStride] \n\t"
    FILTER_HV_W8($f4, $f6, $f8, $f10, $f12, $f14, $f16, $f18, $f20, $f22, $f24,
                 $f26, $f28, $f30, $f0, $f2, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 3f                  \n\t"

    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDU   "$8, %[pSrc], %[iSrcStride]      \n\t"
    MMI_LOAD_8P($f28, $f30, $f0, $8)
    FILTER_HV_W8($f8, $f10, $f12, $f14, $f16, $f18, $f20, $f22, $f24, $f26, $f28,
                 $f30, $f0, $f2, $f4, $f6, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 3f                  \n\t"

    PTR_ADDU   "%[pSrc], %[pSrc], $10           \n\t"
    MMI_LOAD_8P($f0, $f2, $f4, %[pSrc])
    PTR_ADDU   "%[pDst],  %[pDst], %[iDstStride] \n\t"
    FILTER_HV_W8($f12, $f14, $f16, $f18, $f20, $f22, $f24, $f26, $f28, $f30, $f0,
                 $f2, $f4, $f6, $f8, $f10, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 3f                  \n\t"

    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDU   "$8, %[pSrc], %[iSrcStride]      \n\t"
    MMI_LOAD_8P($f4, $f6, $f8, $8)
    FILTER_HV_W8($f16, $f18, $f20, $f22, $f24, $f26, $f28, $f30, $f0, $f2, $f4,
                 $f6, $f8, $f10, $f12, $f14, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 3f                  \n\t"

    PTR_ADDU   "%[pSrc], %[pSrc], $10           \n\t"
    MMI_LOAD_8P($f8, $f10, $f12, %[pSrc])
    PTR_ADDU   "%[pDst],  %[pDst], %[iDstStride] \n\t"
    FILTER_HV_W8($f20, $f22, $f24, $f26, $f28, $f30, $f0, $f2, $f4, $f6, $f8,
                 $f10, $f12, $f14, $f16, $f18, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 3f                  \n\t"

    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDU   "$8, %[pSrc], %[iSrcStride]      \n\t"
    MMI_LOAD_8P($f12, $f14, $f16, $8)
    FILTER_HV_W8($f24, $f26, $f28, $f30, $f0, $f2, $f4, $f6, $f8, $f10, $f12,
                 $f14, $f16, $f18, $f20, $f22, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 3f                  \n\t"

    PTR_ADDU   "%[pSrc], %[pSrc], $10           \n\t"
    MMI_LOAD_8P($f16, $f18, $f20, %[pSrc])
    PTR_ADDU   "%[pDst],  %[pDst], %[iDstStride] \n\t"
    FILTER_HV_W8($f28, $f30, $f0, $f2, $f4, $f6, $f8, $f10, $f12, $f14, $f16,
                 $f18, $f20, $f22, $f24, $f26, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 3f                  \n\t"

    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDU   "$8, %[pSrc], %[iSrcStride]      \n\t"
    MMI_LOAD_8P($f20, $f22, $f24, $8)
    "j          2b                              \n\t"

    "3:                                         \n\t"
    PTR_ADDIU  "%[iWidth], %[iWidth], -0x1      \n\t"
    "beqz       %[iWidth], 4f                   \n\t"

    "move       %[pSrc], $12                    \n\t"
    "move       %[pDst], $13                    \n\t"
    "move       %[iHeight], $14                 \n\t"
    PTR_SUBU   "%[pSrc], %[pSrc], $10           \n\t"
    PTR_ADDIU  "%[pSrc], %[pSrc], 0x8           \n\t"
    PTR_ADDIU  "%[pDst], %[pDst], 0x8           \n\t"
    "j          1b                              \n\t"
    "4:                                         \n\t"
    : [pSrc]"+&r"((unsigned char *)pSrc), [pDst]"+&r"((unsigned char *)pDst),
      [iWidth]"+&r"(iWidth), [iHeight]"+&r"(iHeight)
    : [iSrcStride]"r"(iSrcStride),  [iDstStride]"r"(iDstStride)
    : "memory", "$8", "$9", "$10", "$12", "$13", "$14", "$f0", "$f2", "$f4",
      "$f6", "$f8", "$f10", "$f12", "$f14", "$f16", "$f18", "$f20", "$f22",
      "$f24", "$f26", "$f28", "$f30"
  );
  RECOVER_REG;
}

//vertical filter to gain half sample, that is (0, 2) location in quarter sample
static inline void McHorVer02Height5Or9Or17_mmi(const uint8_t* pSrc, int32_t iSrcStride,
                                                uint8_t* pDst, int32_t iDstStride,
                                                int32_t iWidth, int32_t iHeight) {
  if (iWidth == 16 || iWidth == 8)
    McHorVer02Height9Or17_mmi(pSrc, iSrcStride, pDst, iDstStride, iWidth, iHeight );
  else
    McHorVer02Height5_mmi (pSrc, iSrcStride, pDst, iDstStride, iWidth, iHeight);
}

static inline void McHorVer22HorFirst_mmi(const uint8_t *pSrc, int32_t iSrcStride,
                                          uint8_t * pTap, int32_t iTapStride,
                                          int32_t iWidth, int32_t iHeight) {
  BACKUP_REG;
  __asm__ volatile (
    ".set       arch=loongson3a                 \n\t"
    "dli        $8, 0x9                         \n\t"
    PTR_SUBU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    PTR_SUBU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    "bne        %[iWidth], $8, 2f               \n\t"

    "1:                                         \n\t"
    "gsldlc1    $f0, 0x7(%[pSrc])               \n\t"
    "xor        $f28, $f28, $f28                \n\t"
    "gsldrc1    $f0, 0x0(%[pSrc])               \n\t"
    "punpckhbh  $f2, $f0, $f28                  \n\t"
    "gsldlc1    $f4, 0xc(%[pSrc])               \n\t"
    "punpcklbh  $f0, $f0, $f28                  \n\t"
    "gsldrc1    $f4, 0x5(%[pSrc])               \n\t"
    "punpckhbh  $f6, $f4, $f28                  \n\t"
    "gsldlc1    $f8, 0x8(%[pSrc])               \n\t"
    "punpcklbh  $f4, $f4, $f28                  \n\t"
    "gsldrc1    $f8, 0x1(%[pSrc])               \n\t"
    "punpckhbh  $f10, $f8, $f28                 \n\t"
    "gsldlc1    $f12, 0xb(%[pSrc])              \n\t"
    "punpcklbh  $f8, $f8, $f28                  \n\t"
    "gsldrc1    $f12, 0x4(%[pSrc])              \n\t"
    "punpckhbh  $f14, $f12, $f28                \n\t"
    "gsldlc1    $f16, 0x9(%[pSrc])              \n\t"
    "punpcklbh  $f12, $f12, $f28                \n\t"
    "gsldrc1    $f16, 0x2(%[pSrc])              \n\t"
    "punpckhbh  $f18, $f16, $f28                \n\t"
    "gsldlc1    $f20, 0xa(%[pSrc])              \n\t"
    "punpcklbh  $f16, $f16, $f28                \n\t"
    "gsldrc1    $f20, 0x3(%[pSrc])              \n\t"
    "punpckhbh  $f22, $f20, $f28                \n\t"
    "punpcklbh  $f20, $f20, $f28                \n\t"

    "mov.d      $f28, $f8                       \n\t"
    "mov.d      $f30, $f10                      \n\t"
    "paddh      $f28, $f28, $f12                \n\t"
    "paddh      $f30, $f30, $f14                \n\t"
    "mov.d      $f24, $f16                      \n\t"
    "mov.d      $f26, $f18                      \n\t"
    "paddh      $f24, $f24, $f20                \n\t"
    "paddh      $f26, $f26, $f22                \n\t"
    "dli        $8, 0x2                         \n\t"
    "dmfc1      $9, $f12                        \n\t"
    "dmtc1      $8, $f12                        \n\t"
    "psllh      $f24, $f24, $f12                \n\t"
    "psllh      $f26, $f26, $f12                \n\t"
    "psubh      $f24, $f24, $f28                \n\t"
    "psubh      $f26, $f26, $f30                \n\t"
    "paddh      $f0, $f0, $f4                   \n\t"
    "paddh      $f2, $f2, $f6                   \n\t"
    "paddh      $f0, $f0, $f24                  \n\t"
    "paddh      $f2, $f2, $f26                  \n\t"
    "psllh      $f24, $f24, $f12                \n\t"
    "psllh      $f26, $f26, $f12                \n\t"
    "paddh      $f0, $f0, $f24                  \n\t"
    "paddh      $f2, $f2, $f26                  \n\t"
    "gsswlc1    $f0, 0x3(%[pTap])               \n\t"
    "gsswrc1    $f0, 0x0(%[pTap])               \n\t"

    "gsldlc1    $f0, 0xd(%[pSrc])               \n\t"
    "xor        $f28, $f28, $f28                \n\t"
    "gsldrc1    $f0, 0x6(%[pSrc])               \n\t"
    "punpckhbh  $f2, $f0, $f28                  \n\t"
    "punpcklbh  $f0, $f0, $f28                  \n\t"
    "dli        $8, 0x2                         \n\t"
    "dmtc1      $9, $f12                        \n\t"
    "dmtc1      $8, $f24                        \n\t"

    "paddh      $f16, $f16, $f4                 \n\t"
    "paddh      $f18, $f18, $f6                 \n\t"
    "paddh      $f20, $f20, $f12                \n\t"
    "paddh      $f22, $f22, $f14                \n\t"
    "psllh      $f20, $f20, $f24                \n\t"
    "psllh      $f22, $f22, $f24                \n\t"
    "psubh      $f20, $f20, $f16                \n\t"
    "psubh      $f22, $f22, $f18                \n\t"
    "paddh      $f8, $f8, $f0                   \n\t"
    "paddh      $f10, $f10, $f2                 \n\t"
    "paddh      $f8, $f8, $f20                  \n\t"
    "paddh      $f10, $f10, $f22                \n\t"
    "psllh      $f20, $f20, $f24                \n\t"
    "psllh      $f22, $f22, $f24                \n\t"
    "paddh      $f8, $f8, $f20                  \n\t"
    "paddh      $f10, $f10, $f22                \n\t"
    "gssdlc1    $f8, 0x9(%[pTap])               \n\t"
    "gssdlc1    $f10, 0x11(%[pTap])             \n\t"
    "gssdrc1    $f8, 0x2(%[pTap])               \n\t"
    "gssdrc1    $f10, 0xa(%[pTap])              \n\t"

    PTR_ADDU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    PTR_ADDU   "%[pTap], %[pTap], %[iTapStride] \n\t"
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "bnez       %[iHeight], 1b                  \n\t"
    "j          3f                              \n\t"

    "2:                                         \n\t"
    "gsldlc1    $f0, 0x7(%[pSrc])               \n\t"
    "xor        $f28, $f28, $f28                \n\t"
    "gsldrc1    $f0, 0x0(%[pSrc])               \n\t"
    "punpckhbh  $f2, $f0, $f28                  \n\t"
    "gsldlc1    $f4, 0xc(%[pSrc])               \n\t"
    "punpcklbh  $f0, $f0, $f28                  \n\t"
    "gsldrc1    $f4, 0x5(%[pSrc])               \n\t"
    "punpckhbh  $f6, $f4, $f28                  \n\t"
    "gsldlc1    $f8, 0x8(%[pSrc])               \n\t"
    "punpcklbh  $f4, $f4, $f28                  \n\t"
    "gsldrc1    $f8, 0x1(%[pSrc])               \n\t"
    "punpckhbh  $f10, $f8, $f28                 \n\t"
    "gsldlc1    $f12, 0xb(%[pSrc])              \n\t"
    "punpcklbh  $f8, $f8, $f28                  \n\t"
    "gsldrc1    $f12, 0x4(%[pSrc])              \n\t"
    "punpckhbh  $f14, $f12, $f28                \n\t"
    "gsldlc1    $f16, 0x9(%[pSrc])              \n\t"
    "punpcklbh  $f12, $f12, $f28                \n\t"
    "gsldrc1    $f16, 0x2(%[pSrc])              \n\t"
    "punpckhbh  $f18, $f16, $f28                \n\t"
    "gsldlc1    $f20, 0xa(%[pSrc])              \n\t"
    "punpcklbh  $f16, $f16, $f28                \n\t"
    "gsldrc1    $f20, 0x3(%[pSrc])              \n\t"
    "punpckhbh  $f22, $f20, $f28                \n\t"
    "dli        $8, 0x2                         \n\t"
    "punpcklbh  $f20, $f20, $f28                \n\t"

    "dmtc1      $8, $f30                        \n\t"
    "paddh      $f8, $f8, $f12                  \n\t"
    "paddh      $f10, $f10, $f14                \n\t"
    "paddh      $f16, $f16, $f20                \n\t"
    "paddh      $f18, $f18, $f22                \n\t"
    "psllh      $f16, $f16, $f30                \n\t"
    "psllh      $f18, $f18, $f30                \n\t"
    "psubh      $f16, $f16, $f8                 \n\t"
    "psubh      $f18, $f18, $f10                \n\t"
    "paddh      $f0, $f0, $f4                   \n\t"
    "paddh      $f2, $f2, $f6                   \n\t"
    "paddh      $f0, $f0, $f16                  \n\t"
    "paddh      $f2, $f2, $f18                  \n\t"
    "psllh      $f16, $f16, $f30                \n\t"
    "psllh      $f18, $f18, $f30                \n\t"
    "paddh      $f0, $f0, $f16                  \n\t"
    "paddh      $f2, $f2, $f18                  \n\t"
    "gssqc1     $f2, $f0, 0x0(%[pTap])          \n\t"

    "gsldlc1    $f0, 15(%[pSrc])                \n\t"
    "gsldrc1    $f0, 8(%[pSrc])                 \n\t"
    "punpckhbh  $f2, $f0, $f28                  \n\t"
    "gsldlc1    $f4, 0x14(%[pSrc])              \n\t"
    "punpcklbh  $f0, $f0, $f28                  \n\t"
    "gsldrc1    $f4, 0xd(%[pSrc])               \n\t"
    "punpckhbh  $f6, $f4, $f28                  \n\t"
    "gsldlc1    $f8, 0x10(%[pSrc])              \n\t"
    "punpcklbh  $f4, $f4, $f28                  \n\t"
    "gsldrc1    $f8, 0x9(%[pSrc])               \n\t"
    "punpckhbh  $f10, $f8, $f28                 \n\t"
    "gsldlc1    $f12, 0x13(%[pSrc])             \n\t"
    "punpcklbh  $f8, $f8, $f28                  \n\t"
    "gsldrc1    $f12, 0xc(%[pSrc])              \n\t"
    "punpckhbh  $f14, $f12, $f28                \n\t"
    "gsldlc1    $f16, 0x11(%[pSrc])             \n\t"
    "punpcklbh  $f12, $f12, $f28                \n\t"
    "gsldrc1    $f16, 0xa(%[pSrc])              \n\t"
    "punpckhbh  $f18, $f16, $f28                \n\t"
    "gsldlc1    $f20, 0x12(%[pSrc])             \n\t"
    "punpcklbh  $f16, $f16, $f28                \n\t"
    "gsldrc1    $f20, 0xb(%[pSrc])              \n\t"
    "punpckhbh  $f22, $f20, $f28                \n\t"
    "punpcklbh  $f20, $f20, $f28                \n\t"

    "mov.d      $f28, $f8                       \n\t"
    "mov.d      $f30, $f10                      \n\t"
    "paddh      $f28, $f28, $f12                \n\t"
    "paddh      $f30, $f30, $f14                \n\t"
    "mov.d      $f24, $f16                      \n\t"
    "mov.d      $f26, $f18                      \n\t"
    "dli        $8, 0x2                         \n\t"
    "paddh      $f24, $f24, $f20                \n\t"
    "paddh      $f26, $f26, $f22                \n\t"
    "dmfc1      $9, $f12                        \n\t"
    "dmtc1      $8, $f12                        \n\t"
    "psllh      $f24, $f24, $f12                \n\t"
    "psllh      $f26, $f26, $f12                \n\t"
    "psubh      $f24, $f24, $f28                \n\t"
    "psubh      $f26, $f26, $f30                \n\t"
    "paddh      $f0, $f0, $f4                   \n\t"
    "paddh      $f2, $f2, $f6                   \n\t"
    "paddh      $f0, $f0, $f24                  \n\t"
    "paddh      $f2, $f2, $f26                  \n\t"
    "psllh      $f24, $f24, $f12                \n\t"
    "psllh      $f26, $f26, $f12                \n\t"
    "paddh      $f0, $f0, $f24                  \n\t"
    "paddh      $f2, $f2, $f26                  \n\t"
    "gsswlc1    $f0, 0x13(%[pTap])              \n\t"
    "gsswrc1    $f0, 0x10(%[pTap])              \n\t"

    "gsldlc1    $f0, 0x15(%[pSrc])              \n\t"
    "xor        $f28, $f28, $f28                \n\t"
    "gsldrc1    $f0, 0xE(%[pSrc])               \n\t"
    "punpckhbh  $f2, $f0, $f28                  \n\t"
    "punpcklbh  $f0, $f0, $f28                  \n\t"
    "dli        $8, 0x2                         \n\t"
    "dmtc1      $9, $f12                        \n\t"
    "dmtc1      $8, $f24                        \n\t"

    "paddh      $f16, $f16, $f4                 \n\t"
    "paddh      $f18, $f18, $f6                 \n\t"
    "paddh      $f20, $f20, $f12                \n\t"
    "paddh      $f22, $f22, $f14                \n\t"
    "psllh      $f20, $f20, $f24                \n\t"
    "psllh      $f22, $f22, $f24                \n\t"
    "psubh      $f20, $f20, $f16                \n\t"
    "psubh      $f22, $f22, $f18                \n\t"
    "paddh      $f8, $f8, $f0                   \n\t"
    "paddh      $f10, $f10, $f2                 \n\t"
    "paddh      $f8, $f8, $f20                  \n\t"
    "paddh      $f10, $f10, $f22                \n\t"
    "psllh      $f20, $f20, $f24                \n\t"
    "psllh      $f22, $f22, $f24                \n\t"
    "paddh      $f8, $f8, $f20                  \n\t"
    "paddh      $f10, $f10, $f22                \n\t"
    "gssdlc1    $f8, 0x19(%[pTap])              \n\t"
    "gssdlc1    $f10, 0x21(%[pTap])             \n\t"
    "gssdrc1    $f8, 0x12(%[pTap])              \n\t"
    "gssdrc1    $f10, 0x1a(%[pTap])             \n\t"

    PTR_ADDU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    PTR_ADDU   "%[pTap], %[pTap], %[iTapStride] \n\t"
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "bnez       %[iHeight], 2b                  \n\t"
    "3:                                         \n\t"
    : [pSrc]"+&r"(pSrc), [pTap]"+&r"(pTap), [iWidth]"+&r"(iWidth),
      [iHeight]"+&r"(iHeight)
    : [iSrcStride]"r"(iSrcStride),  [iTapStride]"r"(iTapStride)
    : "memory", "$8", "$9", "$f0", "$f2", "$f4", "$f6", "$f8", "$f10", "$f12",
      "$f14", "$f16", "$f18", "$f20", "$f22", "$f24", "$f26", "$f28", "$f30"
  );
  RECOVER_REG;
}

static inline void McHorVer22Width8VerLastAlign_mmi(const uint8_t *pTap,
                   int32_t iTapStride, uint8_t * pDst, int32_t iDstStride,
                   int32_t iWidth, int32_t iHeight) {
  BACKUP_REG;
  __asm__ volatile (
    ".set       arch=loongson3a                 \n\t"
    "move       $10, %[pTap]                    \n\t"
    "move       $11, %[pDst]                    \n\t"
    "move       $12, %[iHeight]                 \n\t"
    "dsrl       %[iWidth], 0x3                  \n\t"
    PTR_ADDU   "$13, %[iTapStride], %[iTapStride] \n\t"
    PTR_ADDU   "$14, %[iDstStride], %[iDstStride] \n\t"
    "dli        $15, 0x0020002000200020         \n\t"

    "4:                                         \n\t"
    "gslqc1     $f2, $f0, 0x0(%[pTap])          \n\t"
    PTR_ADDU   "$8, %[pTap], %[iTapStride]      \n\t"
    "gslqc1     $f6, $f4, 0x0($8)               \n\t"
    PTR_ADDU   "%[pTap], %[pTap], $13           \n\t"
    "gslqc1     $f10, $f8, 0x0(%[pTap])         \n\t"
    PTR_ADDU   "$8, %[pTap], %[iTapStride]      \n\t"
    "gslqc1     $f14, $f12, 0x0($8)             \n\t"
    PTR_ADDU   "%[pTap], %[pTap], $13           \n\t"
    "gslqc1     $f18, $f16, 0x0(%[pTap])        \n\t"
    PTR_ADDU   "$8, %[pTap], %[iTapStride]      \n\t"
    "gslqc1     $f22, $f20, 0x0($8)             \n\t"

    FILTER_VER_ALIGN($f0, $f2, $f4, $f6, $f8, $f10, $f12, $f14, $f16, $f18, $f20,
                     $f22, $f24, $f26, $f28, $f30, %[pDst], $0, $8, $9, $15)

    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    PTR_ADDU   "%[pTap], %[pTap], $13           \n\t"
    "gslqc1     $f26, $f24, 0x0(%[pTap])        \n\t"
    "mov.d      $f0, $f4                        \n\t"
    "mov.d      $f2, $f6                        \n\t"
    "mov.d      $f4, $f8                        \n\t"
    "mov.d      $f6, $f10                       \n\t"
    "mov.d      $f8, $f12                       \n\t"
    "mov.d      $f10, $f14                      \n\t"
    "mov.d      $f12, $f16                      \n\t"
    "mov.d      $f14, $f18                      \n\t"
    "mov.d      $f16, $f20                      \n\t"
    "mov.d      $f18, $f22                      \n\t"
    "mov.d      $f20, $f24                      \n\t"
    "mov.d      $f22, $f26                      \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_SUBU   "%[pTap], %[pTap], %[iTapStride] \n\t"

    "5:                                         \n\t"
    FILTER_VER_ALIGN($f0, $f2, $f4, $f6, $f8, $f10, $f12, $f14, $f16, $f18, $f20,
                     $f22, $f24, $f26, $f28, $f30, %[pDst], $0, $8, $9, $15)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 6f                  \n\t"
    PTR_ADDU   "%[pTap], %[pTap], $13           \n\t"
    "gslqc1     $f26, $f24, 0x0(%[pTap])        \n\t"

    FILTER_VER_ALIGN($f4, $f6, $f8, $f10, $f12, $f14, $f16, $f18, $f20, $f22, $f24,
                     $f26, $f28, $f30, $f0, $f2, %[pDst], %[iDstStride], $8, $9, $15)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 6f                  \n\t"
    PTR_ADDU   "%[pDst], %[pDst], $14           \n\t"
    PTR_ADDU   "$8, %[pTap], %[iTapStride]      \n\t"
    "gslqc1     $f30, $f28, 0x0($8)             \n\t"

    FILTER_VER_ALIGN($f8, $f10, $f12, $f14, $f16, $f18, $f20, $f22, $f24, $f26, $f28,
                     $f30, $f0, $f2, $f4, $f6, %[pDst], $0, $8, $9, $15)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 6f                  \n\t"
    PTR_ADDU   "%[pTap], %[pTap], $13           \n\t"
    "gslqc1     $f2, $f0, 0x0(%[pTap])          \n\t"

    FILTER_VER_ALIGN($f12, $f14, $f16, $f18, $f20, $f22, $f24, $f26, $f28, $f30, $f0,
                     $f2, $f4, $f6, $f8, $f10, %[pDst], %[iDstStride], $8, $9, $15)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 6f                  \n\t"
    PTR_ADDU   "%[pDst], %[pDst], $14           \n\t"
    PTR_ADDU   "$8, %[pTap], %[iTapStride]      \n\t"
    "gslqc1     $f6, $f4, 0x0($8)               \n\t"

    FILTER_VER_ALIGN($f16, $f18, $f20, $f22, $f24, $f26, $f28, $f30, $f0, $f2, $f4,
                     $f6, $f8, $f10, $f12, $f14, %[pDst], $0, $8, $9, $15)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 6f                  \n\t"
    PTR_ADDU   "%[pTap], %[pTap], $13           \n\t"
    "gslqc1     $f10, $f8, 0x0(%[pTap])         \n\t"

    FILTER_VER_ALIGN($f20, $f22, $f24, $f26, $f28, $f30, $f0, $f2, $f4, $f6, $f8,
                     $f10, $f12, $f14, $f16, $f18, %[pDst], %[iDstStride], $8, $9, $15)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 6f                  \n\t"
    PTR_ADDU   "%[pDst], %[pDst], $14           \n\t"
    PTR_ADDU   "$8, %[pTap], %[iTapStride]      \n\t"
    "gslqc1     $f14, $f12, 0x0($8)             \n\t"

    FILTER_VER_ALIGN($f24, $f26, $f28, $f30, $f0, $f2, $f4, $f6, $f8, $f10, $f12,
                     $f14, $f16, $f18, $f20, $f22, %[pDst], $0, $8, $9, $15)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 6f                  \n\t"
    PTR_ADDU   "%[pTap], %[pTap], $13           \n\t"
    "gslqc1     $f18, $f16, 0x0(%[pTap])        \n\t"

    FILTER_VER_ALIGN($f28, $f30, $f0, $f2, $f4, $f6, $f8, $f10, $f12, $f14, $f16,
                     $f18, $f20, $f22, $f24, $f26, %[pDst], %[iDstStride], $8, $9, $15)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 6f                  \n\t"
    PTR_ADDU   "%[pDst], %[pDst], $14           \n\t"
    PTR_ADDU   "$8, %[pTap], %[iTapStride]      \n\t"
    "gslqc1     $f22, $f20, 0x0($8)             \n\t"
    "j          5b                              \n\t"

    "6:                                         \n\t"
    PTR_ADDIU  "%[iWidth], %[iWidth], -0x1      \n\t"
    "beqz       %[iWidth], 7f                   \n\t"
    "move       %[pTap], $10                    \n\t"
    "move       %[pDst], $11                    \n\t"
    "move       %[iHeight], $12                 \n\t"
    PTR_ADDIU  "%[pTap], %[pTap], 0x10          \n\t"
    PTR_ADDIU  "%[pDst], %[pDst], 0x8           \n\t"
    "j          4b                              \n\t"
    "7:                                         \n\t"
    : [pTap]"+&r"((unsigned char *)pTap), [pDst]"+&r"((unsigned char *)pDst),
      [iWidth]"+&r"((int)iWidth), [iHeight]"+&r"((int)iHeight)
    : [iTapStride]"r"((int)iTapStride), [iDstStride]"r"((int)iDstStride)
    : "memory", "$8", "$9", "$10", "$11", "$12", "$13", "$14", "$15", "$f0",
      "$f2", "$f4", "$f6", "$f8", "$f10", "$f12", "$f14", "$f16", "$f18",
      "$f20", "$f22", "$f24", "$f26", "$f28", "$f30"
  );
  RECOVER_REG;
}

static inline void McHorVer22Width8VerLastUnAlign_mmi(const uint8_t *pTap,
                   int32_t iTapStride, uint8_t * pDst, int32_t iDstStride,
                   int32_t iWidth, int32_t iHeight) {
  BACKUP_REG;
  __asm__ volatile (
    ".set       arch=loongson3a                 \n\t"
    "move       $10, %[pTap]                    \n\t"
    "move       $11, %[pDst]                    \n\t"
    "move       $12, %[iHeight]                 \n\t"
    "dsrl       %[iWidth], 0x3                  \n\t"
    PTR_ADDU   "$13, %[iTapStride], %[iTapStride] \n\t"
    "dli        $14, 0x0020002000200020         \n\t"

    "4:                                         \n\t"
    PTR_ADDU   "$8, %[pTap], %[iTapStride]      \n\t"
    "gsldlc1    $f0, 0x7(%[pTap])               \n\t"
    "gsldlc1    $f2, 0xF(%[pTap])               \n\t"
    "gsldlc1    $f4, 0x7($8)                    \n\t"
    "gsldlc1    $f6, 0xF($8)                    \n\t"
    "gsldrc1    $f0, 0x0(%[pTap])               \n\t"
    "gsldrc1    $f2, 0x8(%[pTap])               \n\t"
    "gsldrc1    $f4, 0x0($8)                    \n\t"
    "gsldrc1    $f6, 0x8($8)                    \n\t"
    PTR_ADDU   "%[pTap], %[pTap], $13           \n\t"
    PTR_ADDU   "$8, %[pTap], %[iTapStride]      \n\t"
    "gsldlc1    $f8, 0x7(%[pTap])               \n\t"
    "gsldlc1    $f10, 0xF(%[pTap])              \n\t"
    "gsldlc1    $f12, 0x7($8)                   \n\t"
    "gsldlc1    $f14, 0xF($8)                   \n\t"
    "gsldrc1    $f8, 0x0(%[pTap])               \n\t"
    "gsldrc1    $f10, 0x8(%[pTap])              \n\t"
    "gsldrc1    $f12, 0x0($8)                   \n\t"
    "gsldrc1    $f14, 0x8($8)                   \n\t"
    PTR_ADDU   "%[pTap], %[pTap], $13           \n\t"
    PTR_ADDU   "$8, %[pTap], %[iTapStride]      \n\t"
    "gsldlc1    $f16, 0x7(%[pTap])              \n\t"
    "gsldlc1    $f18, 0xF(%[pTap])              \n\t"
    "gsldlc1    $f20, 0x7($8)                   \n\t"
    "gsldlc1    $f22, 0xF($8)                   \n\t"
    "gsldrc1    $f16, 0x0(%[pTap])              \n\t"
    "gsldrc1    $f18, 0x8(%[pTap])              \n\t"
    "gsldrc1    $f20, 0x0($8)                   \n\t"
    "gsldrc1    $f22, 0x8($8)                   \n\t"

    FILTER_VER_UNALIGN($f0, $f2, $f4, $f6, $f8, $f10, $f12, $f14, $f16, $f18,
                       $f20, $f22, $f24, $f26, $f28, $f30, %[pDst], $8, $9, $14)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    PTR_ADDU   "%[pTap], %[pTap], $13           \n\t"
    "gsldlc1    $f24, 0x7(%[pTap])              \n\t"
    "gsldlc1    $f26, 0xF(%[pTap])              \n\t"
    "gsldrc1    $f24, 0x0(%[pTap])              \n\t"
    "gsldrc1    $f26, 0x8(%[pTap])              \n\t"
    "mov.d      $f0, $f4                        \n\t"
    "mov.d      $f2, $f6                        \n\t"
    "mov.d      $f4, $f8                        \n\t"
    "mov.d      $f6, $f10                       \n\t"
    "mov.d      $f8, $f12                       \n\t"
    "mov.d      $f10, $f14                      \n\t"
    "mov.d      $f12, $f16                      \n\t"
    "mov.d      $f14, $f18                      \n\t"
    "mov.d      $f16, $f20                      \n\t"
    "mov.d      $f18, $f22                      \n\t"
    "mov.d      $f20, $f24                      \n\t"
    "mov.d      $f22, $f26                      \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_SUBU   "%[pTap], %[pTap], %[iTapStride] \n\t"

    "5:                                         \n\t"
    FILTER_VER_UNALIGN($f0, $f2, $f4, $f6, $f8, $f10, $f12, $f14, $f16, $f18,
                       $f20, $f22, $f24, $f26, $f28, $f30, %[pDst], $8, $9, $14)

    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 6f                  \n\t"
    PTR_ADDU   "%[pTap], %[pTap], $13           \n\t"
    "gsldlc1    $f24, 0x7(%[pTap])              \n\t"
    "gsldlc1    $f26, 0xF(%[pTap])              \n\t"
    "gsldrc1    $f24, 0x0(%[pTap])              \n\t"
    "gsldrc1    $f26, 0x8(%[pTap])              \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"

    FILTER_VER_UNALIGN($f4, $f6, $f8, $f10, $f12, $f14, $f16, $f18, $f20, $f22,
                       $f24, $f26, $f28, $f30, $f0, $f2, %[pDst], $8, $9, $14)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 6f                  \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDU   "$8, %[pTap], %[iTapStride]      \n\t"
    "gsldlc1    $f28, 0x7($8)                   \n\t"
    "gsldlc1    $f30, 0xF($8)                   \n\t"
    "gsldrc1    $f28, 0x0($8)                   \n\t"
    "gsldrc1    $f30, 0x8($8)                   \n\t"

    FILTER_VER_UNALIGN($f8, $f10, $f12, $f14, $f16, $f18, $f20, $f22, $f24, $f26,
                       $f28, $f30, $f0, $f2, $f4, $f6, %[pDst], $8, $9, $14)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 6f                  \n\t"
    PTR_ADDU   "%[pTap], %[pTap], $13           \n\t"
    "gsldlc1    $f0, 0x7(%[pTap])               \n\t"
    "gsldlc1    $f2, 0xF(%[pTap])               \n\t"
    "gsldrc1    $f0, 0x0(%[pTap])               \n\t"
    "gsldrc1    $f2, 0x8(%[pTap])               \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"

    FILTER_VER_UNALIGN($f12, $f14, $f16, $f18, $f20, $f22, $f24, $f26, $f28,
                       $f30, $f0, $f2, $f4, $f6, $f8, $f10, %[pDst], $8, $9, $14)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 6f                  \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDU   "$8, %[pTap], %[iTapStride]      \n\t"
    "gsldlc1    $f4, 0x7($8)                    \n\t"
    "gsldlc1    $f6, 0xF($8)                    \n\t"
    "gsldrc1    $f4, 0x0($8)                    \n\t"
    "gsldrc1    $f6, 0x8($8)                    \n\t"

    FILTER_VER_UNALIGN($f16, $f18, $f20, $f22, $f24, $f26, $f28, $f30, $f0, $f2,
                       $f4, $f6, $f8, $f10, $f12, $f14, %[pDst], $8, $9, $14)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 6f                  \n\t"
    PTR_ADDU   "%[pTap], %[pTap], $13           \n\t"
    "gsldlc1    $f8, 0x7(%[pTap])               \n\t"
    "gsldlc1    $f10, 0xF(%[pTap])              \n\t"
    "gsldrc1    $f8, 0x0(%[pTap])               \n\t"
    "gsldrc1    $f10, 0x8(%[pTap])              \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"

    FILTER_VER_UNALIGN($f20, $f22, $f24, $f26, $f28, $f30, $f0, $f2, $f4, $f6,
                       $f8, $f10, $f12, $f14, $f16, $f18, %[pDst], $8, $9, $14)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 6f                  \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDU   "$8, %[pTap], %[iTapStride]      \n\t"
    "gsldlc1    $f12, 0x7($8)                   \n\t"
    "gsldlc1    $f14, 0xF($8)                   \n\t"
    "gsldrc1    $f12, 0x0($8)                   \n\t"
    "gsldrc1    $f14, 0x8($8)                   \n\t"

    FILTER_VER_UNALIGN($f24, $f26, $f28, $f30, $f0, $f2, $f4, $f6, $f8, $f10,
                       $f12, $f14, $f16, $f18, $f20, $f22, %[pDst], $8, $9, $14)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 6f                  \n\t"
    PTR_ADDU   "%[pTap], %[pTap], $13           \n\t"
    "gsldlc1    $f16, 0x7(%[pTap])              \n\t"
    "gsldlc1    $f18, 0xF(%[pTap])              \n\t"
    "gsldrc1    $f16, 0x0(%[pTap])              \n\t"
    "gsldrc1    $f18, 0x8(%[pTap])              \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"

    FILTER_VER_UNALIGN($f28, $f30, $f0, $f2, $f4, $f6, $f8, $f10, $f12, $f14,
                       $f16, $f18, $f20, $f22, $f24, $f26, %[pDst], $8, $9, $14)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 6f                  \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDU   "$8, %[pTap], %[iTapStride]      \n\t"
    "gsldlc1    $f20, 0x7($8)                   \n\t"
    "gsldlc1    $f22, 0xF($8)                   \n\t"
    "gsldrc1    $f20, 0x0($8)                   \n\t"
    "gsldrc1    $f22, 0x8($8)                   \n\t"
    "j          5b                              \n\t"

    "6:                                         \n\t"
    PTR_ADDIU  "%[iWidth], %[iWidth], -0x1      \n\t"
    "beqz       %[iWidth], 7f                   \n\t"
    "move       %[pTap], $10                    \n\t"
    "move       %[pDst], $11                    \n\t"
    "move       %[iHeight], $12                 \n\t"
    PTR_ADDIU  "%[pTap], %[pTap], 0x10          \n\t"
    PTR_ADDIU  "%[pDst], %[pDst], 0x8           \n\t"
    "j          4b                              \n\t"

    "7:                                         \n\t"
    : [pTap]"+&r"((unsigned char *)pTap), [pDst]"+&r"((unsigned char *)pDst),
      [iWidth]"+&r"((int)iWidth), [iHeight]"+&r"((int)iHeight)
    : [iTapStride]"r"((int)iTapStride), [iDstStride]"r"((int)iDstStride)
    : "memory", "$8", "$9", "$10", "$11", "$12", "$13", "$14", "$f0", "$f2",
      "$f4", "$f6", "$f8", "$f10", "$f12", "$f14", "$f16", "$f18", "$f20",
      "$f22", "$f24", "$f26", "$f28", "$f30"
  );
  RECOVER_REG;
}

//horizontal and vertical filter to gain half sample, that is (2, 2) location in quarter sample
static inline void McHorVer22Width5Or9Or17Height5Or9Or17_mmi(const uint8_t* pSrc,
                   int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                   int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (int16_t, pTap, 22, 24, 16)

  if (iWidth == 17 || iWidth == 9){
    int32_t tmp1 = 2 * (iWidth - 8);
    McHorVer22HorFirst_mmi(pSrc - 2, iSrcStride, (uint8_t*)pTap, 48, iWidth, iHeight + 5);

    McHorVer22Width8VerLastAlign_mmi((uint8_t*)pTap,  48, pDst, iDstStride, iWidth - 1, iHeight);

    McHorVer22Width8VerLastUnAlign_mmi((uint8_t*)pTap + tmp1,  48, pDst + iWidth - 8,
                                        iDstStride, 8, iHeight);
  } else {
    int16_t iTmp[17 + 5];
    int32_t i, j, k;

    for (i = 0; i < iHeight; i++) {
      for (j = 0; j < iWidth + 5; j++) {
        iTmp[j] = FilterInput8bitWithStride_c (pSrc - 2 + j, iSrcStride);
      }
      for (k = 0; k < iWidth; k++) {
        pDst[k] = WelsClip1 ((HorFilterInput16bit_c (&iTmp[k]) + 512) >> 10);
      }
      pSrc += iSrcStride;
      pDst += iDstStride;
    }
  }
}

void McCopyWidthEq4_mmi(const uint8_t *pSrc, int iSrcStride,
                        uint8_t *pDst, int iDstStride, int iHeight) {
  __asm__ volatile (
    ".set       arch=loongson3a                 \n\t"
    "1:                                         \n\t"
    "lwl        $8, 0x3(%[pSrc])                \n\t"
    "lwr        $8, 0x0(%[pSrc])                \n\t"
    "swl        $8, 0x3(%[pDst])                \n\t"
    "swr        $8, 0x0(%[pDst])                \n\t"
    PTR_ADDU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDIU  "%[iHeight], %[iHeight], -1      \n\t"
    "bnez       %[iHeight], 1b                  \n\t"
    : [pSrc]"+&r"(pSrc), [pDst]"+&r"(pDst), [iHeight]"+&r"(iHeight)
    : [iSrcStride]"r"(iSrcStride), [iDstStride]"r"(iDstStride)
    : "memory", "$8"
  );
}

void McCopyWidthEq8_mmi(const uint8_t *pSrc, int iSrcStride,
                        uint8_t *pDst, int iDstStride, int iHeight) {
  __asm__ volatile (
    ".set       arch=loongson3a                 \n\t"
    "1:                                         \n\t"
    "ldl        $8, 0x7(%[pSrc])                \n\t"
    "ldr        $8, 0x0(%[pSrc])                \n\t"
    "sdl        $8, 0x7(%[pDst])                \n\t"
    "sdr        $8, 0x0(%[pDst])                \n\t"
    PTR_ADDU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDIU  "%[iHeight], %[iHeight], -1      \n\t"
    "bnez       %[iHeight], 1b                  \n\t"
    : [pSrc]"+&r"(pSrc), [pDst]"+&r"(pDst), [iHeight]"+&r"(iHeight)
    : [iSrcStride]"r"(iSrcStride), [iDstStride]"r"(iDstStride)
    : "memory", "$8"
  );
}

void McCopyWidthEq16_mmi(const uint8_t *pSrc, int iSrcStride,
                         uint8_t *pDst, int iDstStride, int iHeight) {
  __asm__ volatile (
    ".set       arch=loongson3a                 \n\t"
    "1:                                         \n\t"
    "ldl        $8, 0x7(%[pSrc])                \n\t"
    "ldl        $9, 0xF(%[pSrc])                \n\t"
    "ldr        $8, 0x0(%[pSrc])                \n\t"
    "ldr        $9, 0x8(%[pSrc])                \n\t"
    "sdl        $8, 0x7(%[pDst])                \n\t"
    "sdl        $9, 0xF(%[pDst])                \n\t"
    "sdr        $8, 0x0(%[pDst])                \n\t"
    "sdr        $9, 0x8(%[pDst])                \n\t"
    PTR_ADDU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDIU  "%[iHeight], %[iHeight], -1      \n\t"
    "bnez       %[iHeight], 1b                  \n\t"
    : [pSrc]"+&r"(pSrc), [pDst]"+&r"(pDst), [iHeight]"+&r"(iHeight)
    : [iSrcStride]"r"(iSrcStride), [iDstStride]"r"(iDstStride)
    : "memory", "$8", "$9"
  );
}

static inline void McCopy_mmi(const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst,
                              int32_t iDstStride, int32_t iWidth, int32_t iHeight) {
  if (iWidth == 16)
    McCopyWidthEq16_mmi (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McCopyWidthEq8_mmi (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 4)
    McCopyWidthEq4_mmi (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else
    McCopyWidthEq2_c (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}

void McChromaWidthEq4_mmi(const uint8_t *pSrc, int32_t iSrcStride, uint8_t *pDst,
                          int32_t iDstStride, const uint8_t *pABCD, int32_t iHeight) {
  __asm__ volatile (
    ".set       arch=loongson3a                 \n\t"
    "gsldlc1    $f6, 0x7(%[pABCD])              \n\t"
    "gsldrc1    $f6, 0x0(%[pABCD])              \n\t"
    "xor        $f14, $f14, $f14                \n\t"
    "punpcklbh  $f6, $f6, $f6                   \n\t"
    "mov.d      $f8, $f6                        \n\t"
    "punpcklhw  $f6, $f6, $f6                   \n\t"
    "punpckhhw  $f8, $f8, $f8                   \n\t"
    "mov.d      $f10, $f6                       \n\t"
    "punpcklbh  $f6, $f6, $f14                  \n\t"
    "punpckhbh  $f10, $f10, $f14                \n\t"

    "mov.d      $f12, $f8                       \n\t"
    "punpcklbh  $f8, $f8, $f14                  \n\t"
    "punpckhbh  $f12, $f12, $f14                \n\t"
    PTR_ADDU   "%[pABCD], %[pSrc], %[iSrcStride] \n\t"
    "dli        $8, 0x6                         \n\t"
    "gsldlc1    $f0, 0x7(%[pSrc])               \n\t"
    "gsldlc1    $f2, 0x8(%[pSrc])               \n\t"
    "dmtc1      $8, $f16                        \n\t"
    "gsldrc1    $f0, 0x0(%[pSrc])               \n\t"
    "gsldrc1    $f2, 0x1(%[pSrc])               \n\t"
    "dli        $8, 0x0020002000200020          \n\t"
    "punpcklbh  $f0, $f0, $f14                  \n\t"
    "punpcklbh  $f2, $f2, $f14                  \n\t"

    "dmtc1      $8, $f18                        \n\t"
    "1:                                         \n\t"
    "pmullh     $f0, $f0, $f6                   \n\t"
    "pmullh     $f2, $f2, $f10                  \n\t"
    "paddh      $f0, $f0, $f2                   \n\t"

    "gsldlc1    $f2, 0x7(%[pABCD])              \n\t"
    "gsldrc1    $f2, 0x0(%[pABCD])              \n\t"
    "punpcklbh  $f2, $f2, $f14                  \n\t"
    "mov.d      $f4, $f2                        \n\t"
    "pmullh     $f2, $f2, $f8                   \n\t"
    "paddh      $f0, $f0, $f2                   \n\t"
    "gsldlc1    $f2, 0x8(%[pABCD])              \n\t"
    "gsldrc1    $f2, 0x1(%[pABCD])              \n\t"
    "punpcklbh  $f2, $f2, $f14                  \n\t"
    "mov.d      $f14, $f2                       \n\t"
    "pmullh     $f2, $f2, $f12                  \n\t"
    "paddh      $f0, $f0, $f2                   \n\t"
    "mov.d      $f2, $f14                       \n\t"
    "paddh      $f0, $f0, $f18                  \n\t"
    "psrlh      $f0, $f0, $f16                  \n\t"
    "xor        $f14, $f14, $f14                \n\t"
    "packushb   $f0, $f0, $f14                  \n\t"
    "gsswlc1    $f0, 0x3(%[pDst])               \n\t"
    "gsswrc1    $f0, 0x0(%[pDst])               \n\t"
    "mov.d      $f0, $f4                        \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDU   "%[pABCD], %[pABCD], %[iSrcStride] \n\t"
    PTR_ADDIU  "%[iHeight], %[iHeight], -1      \n\t"
    "bnez       %[iHeight], 1b                  \n\t"
    : [pSrc]"+&r"((unsigned char *)pSrc), [pDst]"+&r"((unsigned char *)pDst),
      [pABCD]"+&r"((unsigned char *)pABCD), [iHeight]"+&r"((int)iHeight)
    : [iSrcStride]"r"((int)iSrcStride), [iDstStride]"r"((int)iDstStride)
    : "memory", "$8", "$f0", "$f2", "$f4", "$f6", "$f8", "$f10", "$f12",
      "$f14", "$f16", "$f18"
  );
}

void McChromaWidthEq8_mmi(const uint8_t *pSrc, int32_t iSrcStride, uint8_t *pDst,
                          int32_t iDstStride, const uint8_t *pABCD, int32_t iHeight) {
  BACKUP_REG;
  __asm__ volatile (
    ".set       arch=loongson3a                 \n\t"
    "gsldlc1    $f12, 0x7(%[pABCD])             \n\t"
    "xor        $f28, $f28, $f28                \n\t"
    "gsldrc1    $f12, 0x0(%[pABCD])             \n\t"
    "punpcklbh  $f12, $f12, $f12                \n\t"
    "punpckhhw  $f14, $f12, $f12                \n\t"
    "punpcklhw  $f12, $f12, $f12                \n\t"

    "mov.d      $f16, $f14                      \n\t"
    "punpckhwd  $f14, $f12, $f12                \n\t"
    "punpcklwd  $f12, $f12, $f12                \n\t"
    "punpckhwd  $f18, $f16, $f16                \n\t"
    "punpcklwd  $f16, $f16, $f16                \n\t"
    "mov.d      $f20, $f14                      \n\t"
    "mov.d      $f24, $f18                      \n\t"

    "punpckhbh  $f14, $f12, $f28                \n\t"
    "punpcklbh  $f12, $f12, $f28                \n\t"
    "punpckhbh  $f22, $f20, $f28                \n\t"
    "punpcklbh  $f20, $f20, $f28                \n\t"
    "punpckhbh  $f18, $f16, $f28                \n\t"
    "punpcklbh  $f16, $f16, $f28                \n\t"
    "punpckhbh  $f26, $f24, $f28                \n\t"
    "punpcklbh  $f24, $f24, $f28                \n\t"

    PTR_ADDU   "%[pABCD], %[pSrc], %[iSrcStride] \n\t"
    "gsldlc1    $f0, 0x7(%[pSrc])               \n\t"
    "gsldlc1    $f4, 0x8(%[pSrc])               \n\t"
    "gsldrc1    $f0, 0x0(%[pSrc])               \n\t"
    "gsldrc1    $f4, 0x1(%[pSrc])               \n\t"
    "punpckhbh  $f2, $f0, $f28                  \n\t"
    "punpcklbh  $f0, $f0, $f28                  \n\t"
    "punpckhbh  $f6, $f4, $f28                  \n\t"
    "punpcklbh  $f4, $f4, $f28                  \n\t"
    "1:                                         \n\t"
    "dli        $8, 0x20                        \n\t"
    "dmtc1      $8, $f30                        \n\t"

    "pmullh     $f0, $f0, $f12                  \n\t"
    "pmullh     $f2, $f2, $f14                  \n\t"
    "pmullh     $f4, $f4, $f20                  \n\t"
    "pmullh     $f6, $f6, $f22                  \n\t"
    "paddh      $f0, $f0, $f4                   \n\t"
    "paddh      $f2, $f2, $f6                   \n\t"

    "gsldlc1    $f4, 0x7(%[pABCD])              \n\t"
    "gsldrc1    $f4, 0x0(%[pABCD])              \n\t"
    "punpckhbh  $f6, $f4, $f28                  \n\t"
    "punpcklbh  $f4, $f4, $f28                  \n\t"
    "mov.d      $f8, $f4                        \n\t"
    "mov.d      $f10, $f6                       \n\t"
    "pmullh     $f4, $f4, $f16                  \n\t"
    "pmullh     $f6, $f6, $f18                  \n\t"
    "paddh      $f0, $f0, $f4                   \n\t"
    "paddh      $f2, $f2, $f6                   \n\t"

    "gsldlc1    $f4, 0x8(%[pABCD])              \n\t"
    "gsldrc1    $f4, 0x1(%[pABCD])              \n\t"
    "punpckhbh  $f6, $f4, $f28                  \n\t"
    "punpcklbh  $f4, $f4, $f28                  \n\t"
    "mov.d      $f28, $f4                       \n\t"
    "mov.d      $f30, $f6                       \n\t"
    "pmullh     $f4, $f4, $f24                  \n\t"
    "pmullh     $f6, $f6, $f26                  \n\t"
    "paddh      $f0, $f0, $f4                   \n\t"
    "paddh      $f2, $f2, $f6                   \n\t"
    "mov.d      $f4, $f28                       \n\t"
    "mov.d      $f6, $f30                       \n\t"

    "dli        $8, 0x0020002000200020          \n\t"
    "dmfc1      $9, $f20                        \n\t"
    "dmtc1      $8, $f20                        \n\t"
    "dli        $8, 0x6                         \n\t"
    "paddh      $f0, $f0, $f20                  \n\t"
    "paddh      $f2, $f2, $f20                  \n\t"
    "dmtc1      $8, $f20                        \n\t"
    "psrlh      $f0, $f0, $f20                  \n\t"
    "psrlh      $f2, $f2, $f20                  \n\t"

    "xor        $f28, $f28, $f28                \n\t"
    "packushb   $f0, $f0, $f2                   \n\t"
    "gssdlc1    $f0, 0x7(%[pDst])               \n\t"
    "gssdrc1    $f0, 0x0(%[pDst])               \n\t"

    "mov.d      $f0, $f8                        \n\t"
    "mov.d      $f2, $f10                       \n\t"
    "dmtc1      $9, $f20                        \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDU   "%[pABCD], %[pABCD], %[iSrcStride] \n\t"

    PTR_ADDIU  "%[iHeight], %[iHeight], -1      \n\t"
    "bnez       %[iHeight], 1b                  \n\t"
    : [pSrc]"+&r"(pSrc), [pDst]"+&r"(pDst), [pABCD]"+&r"(pABCD),
      [iHeight]"+&r"(iHeight)
    : [iSrcStride]"r"(iSrcStride), [iDstStride]"r"(iDstStride)
    : "memory", "$8", "$9", "$f0", "$f2", "$f4", "$f6", "$f8", "$f10", "$f12",
      "$f14", "$f16", "$f18", "$f20", "$f22", "$f24", "$f26", "$f28", "$f30"
  );
  RECOVER_REG;
}

void McChroma_mmi(const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst,
                  int32_t iDstStride, int16_t iMvX, int16_t iMvY,
                  int32_t iWidth, int32_t iHeight) {
  static const PMcChromaWidthExtFunc kpMcChromaWidthFuncs[2] = {
    McChromaWidthEq4_mmi,
    McChromaWidthEq8_mmi
  };
  const int32_t kiD8x = iMvX & 0x07;
  const int32_t kiD8y = iMvY & 0x07;
  if (kiD8x == 0 && kiD8y == 0) {
    McCopy_mmi (pSrc, iSrcStride, pDst, iDstStride, iWidth, iHeight);
    return;
  }
  if (iWidth != 2) {
    kpMcChromaWidthFuncs[iWidth >> 3] (pSrc, iSrcStride, pDst, iDstStride,
                                      g_kuiABCD[kiD8y][kiD8x], iHeight);
  } else
    McChromaWithFragMv_c (pSrc, iSrcStride, pDst, iDstStride, iMvX, iMvY,
                          iWidth, iHeight);
}

void McHorVer20WidthEq8_mmi(const uint8_t *pSrc, int iSrcStride, uint8_t *pDst,
                            int iDstStride, int iHeight) {
  BACKUP_REG;
  __asm__ volatile (
    ".set       arch=loongson3a                 \n\t"
    PTR_ADDIU  "%[pSrc], %[pSrc], -0x2          \n\t"
    "xor        $f28, $f28, $f28                \n\t"
    "dli        $8, 0x0010001000100010          \n\t"
    "dmtc1      $8, $f24                        \n\t"
    "dli        $8, 0x2                         \n\t"
    "dmtc1      $8, $f26                        \n\t"
    "dli        $8, 0x5                         \n\t"
    "dmtc1      $8, $f30                        \n\t"
    "1:                                         \n\t"
    "gsldlc1    $f0, 0x7(%[pSrc])               \n\t"
    "gsldlc1    $f4, 0xc(%[pSrc])               \n\t"
    "gsldlc1    $f8, 0x8(%[pSrc])               \n\t"
    "gsldlc1    $f12, 0xb(%[pSrc])              \n\t"
    "gsldlc1    $f16, 0x9(%[pSrc])              \n\t"
    "gsldlc1    $f20, 0xa(%[pSrc])              \n\t"
    "gsldrc1    $f0, 0x0(%[pSrc])               \n\t"
    "gsldrc1    $f4, 0x5(%[pSrc])               \n\t"
    "gsldrc1    $f8, 0x1(%[pSrc])               \n\t"
    "gsldrc1    $f12, 0x4(%[pSrc])              \n\t"
    "gsldrc1    $f16, 0x2(%[pSrc])              \n\t"
    "gsldrc1    $f20, 0x3(%[pSrc])              \n\t"
    "punpckhbh  $f2, $f0, $f28                  \n\t"
    "punpckhbh  $f6, $f4, $f28                  \n\t"
    "punpckhbh  $f10, $f8, $f28                 \n\t"
    "punpckhbh  $f14, $f12, $f28                \n\t"
    "punpckhbh  $f18, $f16, $f28                \n\t"
    "punpckhbh  $f22, $f20, $f28                \n\t"
    "punpcklbh  $f0, $f0, $f28                  \n\t"
    "punpcklbh  $f4, $f4, $f28                  \n\t"
    "punpcklbh  $f8, $f8, $f28                  \n\t"
    "punpcklbh  $f12, $f12, $f28                \n\t"
    "punpcklbh  $f16, $f16, $f28                \n\t"
    "punpcklbh  $f20, $f20, $f28                \n\t"
    "paddh      $f8, $f8, $f12                  \n\t"
    "paddh      $f10, $f10, $f14                \n\t"
    "paddh      $f16, $f16, $f20                \n\t"
    "paddh      $f18, $f18, $f22                \n\t"
    "psllh      $f16, $f16, $f26                \n\t"
    "psllh      $f18, $f18, $f26                \n\t"
    "psubh      $f16, $f16, $f8                 \n\t"
    "psubh      $f18, $f18, $f10                \n\t"
    "paddh      $f0, $f0, $f4                   \n\t"
    "paddh      $f2, $f2, $f6                   \n\t"
    "paddh      $f0, $f0, $f16                  \n\t"
    "paddh      $f2, $f2, $f18                  \n\t"
    "psllh      $f16, $f16, $f26                \n\t"
    "psllh      $f18, $f18, $f26                \n\t"
    "paddh      $f0, $f0, $f16                  \n\t"
    "paddh      $f2, $f2, $f18                  \n\t"
    "paddh      $f0, $f0, $f24                  \n\t"
    "paddh      $f2, $f2, $f24                  \n\t"
    "psrah      $f0, $f0, $f30                  \n\t"
    "psrah      $f2, $f2, $f30                  \n\t"
    "packushb   $f0, $f0, $f2                   \n\t"
    "gssdlc1    $f0, 0x7(%[pDst])               \n\t"
    "gssdrc1    $f0, 0x0(%[pDst])               \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "bnez       %[iHeight], 1b                  \n\t"
    : [pSrc]"+&r"(pSrc), [pDst]"+&r"(pDst), [iHeight]"+&r"(iHeight)
    : [iSrcStride]"r"(iSrcStride), [iDstStride]"r"(iDstStride)
    : "memory", "$8", "$f0", "$f2", "$f4", "$f6", "$f8", "$f10", "$f12",
      "$f14", "$f16", "$f18", "$f20", "$f22", "$f24", "$f26", "$f28", "$f30"
  );
  RECOVER_REG;
}

void McHorVer20WidthEq16_mmi(const uint8_t *pSrc, int iSrcStride, uint8_t *pDst,
                             int iDstStride, int iHeight) {
  BACKUP_REG;
  __asm__ volatile (
    ".set       arch=loongson3a                 \n\t"
    PTR_ADDIU  "%[pSrc], %[pSrc], -0x2          \n\t"
    "dli        $8, 0x0010001000100010          \n\t"
    "dmtc1      $8, $f24                        \n\t"
    "dli        $8, 0x2                         \n\t"
    "dmtc1      $8, $f26                        \n\t"
    "dli        $8, 0x5                         \n\t"
    "dmtc1      $8, $f30                        \n\t"
    "1:                                         \n\t"
    "xor        $f28, $f28, $f28                \n\t"
    "gsldlc1    $f0, 0x7(%[pSrc])               \n\t"
    "gsldlc1    $f4, 0xc(%[pSrc])               \n\t"
    "gsldlc1    $f8, 0x8(%[pSrc])               \n\t"
    "gsldlc1    $f12, 0xb(%[pSrc])              \n\t"
    "gsldlc1    $f16, 0x9(%[pSrc])              \n\t"
    "gsldlc1    $f20, 0xa(%[pSrc])              \n\t"
    "gsldrc1    $f0, 0x0(%[pSrc])               \n\t"
    "gsldrc1    $f4, 0x5(%[pSrc])               \n\t"
    "gsldrc1    $f8, 0x1(%[pSrc])               \n\t"
    "gsldrc1    $f12, 0x4(%[pSrc])              \n\t"
    "gsldrc1    $f16, 0x2(%[pSrc])              \n\t"
    "gsldrc1    $f20, 0x3(%[pSrc])              \n\t"
    "punpckhbh  $f2, $f0, $f28                  \n\t"
    "punpckhbh  $f6, $f4, $f28                  \n\t"
    "punpckhbh  $f10, $f8, $f28                 \n\t"
    "punpckhbh  $f14, $f12, $f28                \n\t"
    "punpckhbh  $f18, $f16, $f28                \n\t"
    "punpckhbh  $f22, $f20, $f28                \n\t"
    "punpcklbh  $f0, $f0, $f28                  \n\t"
    "punpcklbh  $f4, $f4, $f28                  \n\t"
    "punpcklbh  $f8, $f8, $f28                  \n\t"
    "punpcklbh  $f12, $f12, $f28                \n\t"
    "punpcklbh  $f16, $f16, $f28                \n\t"
    "punpcklbh  $f20, $f20, $f28                \n\t"
    "paddh      $f8, $f8, $f12                  \n\t"
    "paddh      $f10, $f10, $f14                \n\t"
    "paddh      $f16, $f16, $f20                \n\t"
    "paddh      $f18, $f18, $f22                \n\t"
    "psllh      $f16, $f16, $f26                \n\t"
    "psllh      $f18, $f18, $f26                \n\t"
    "psubh      $f16, $f16, $f8                 \n\t"
    "psubh      $f18, $f18, $f10                \n\t"
    "paddh      $f0, $f0, $f4                   \n\t"
    "paddh      $f2, $f2, $f6                   \n\t"
    "paddh      $f0, $f0, $f16                  \n\t"
    "paddh      $f2, $f2, $f18                  \n\t"
    "psllh      $f16, $f16, $f26                \n\t"
    "psllh      $f18, $f18, $f26                \n\t"
    "paddh      $f0, $f0, $f16                  \n\t"
    "paddh      $f2, $f2, $f18                  \n\t"
    "paddh      $f0, $f0, $f24                  \n\t"
    "paddh      $f2, $f2, $f24                  \n\t"
    "psrah      $f0, $f0, $f30                  \n\t"
    "psrah      $f2, $f2, $f30                  \n\t"
    "packushb   $f0, $f0, $f2                   \n\t"
    "gssdlc1    $f0, 0x7(%[pDst])               \n\t"
    "gssdrc1    $f0, 0x0(%[pDst])               \n\t"
    "gsldlc1    $f0, 0xF(%[pSrc])               \n\t"
    "gsldlc1    $f4, 0x14(%[pSrc])              \n\t"
    "gsldlc1    $f8, 0x10(%[pSrc])              \n\t"
    "gsldlc1    $f12, 0x13(%[pSrc])             \n\t"
    "gsldlc1    $f16, 0x11(%[pSrc])             \n\t"
    "gsldlc1    $f20, 0x12(%[pSrc])             \n\t"
    "gsldrc1    $f0, 0x8(%[pSrc])               \n\t"
    "gsldrc1    $f4, 0xd(%[pSrc])               \n\t"
    "gsldrc1    $f8, 0x9(%[pSrc])               \n\t"
    "gsldrc1    $f12, 0xc(%[pSrc])              \n\t"
    "gsldrc1    $f16, 0xa(%[pSrc])              \n\t"
    "gsldrc1    $f20, 0xb(%[pSrc])              \n\t"
    "punpckhbh  $f2, $f0, $f28                  \n\t"
    "punpckhbh  $f6, $f4, $f28                  \n\t"
    "punpckhbh  $f10, $f8, $f28                 \n\t"
    "punpckhbh  $f14, $f12, $f28                \n\t"
    "punpckhbh  $f18, $f16, $f28                \n\t"
    "punpckhbh  $f22, $f20, $f28                \n\t"
    "punpcklbh  $f0, $f0, $f28                  \n\t"
    "punpcklbh  $f4, $f4, $f28                  \n\t"
    "punpcklbh  $f8, $f8, $f28                  \n\t"
    "punpcklbh  $f12, $f12, $f28                \n\t"
    "punpcklbh  $f16, $f16, $f28                \n\t"
    "punpcklbh  $f20, $f20, $f28                \n\t"
    "paddh      $f8, $f8, $f12                  \n\t"
    "paddh      $f10, $f10, $f14                \n\t"
    "paddh      $f16, $f16, $f20                \n\t"
    "paddh      $f18, $f18, $f22                \n\t"
    "psllh      $f16, $f16, $f26                \n\t"
    "psllh      $f18, $f18, $f26                \n\t"
    "psubh      $f16, $f16, $f8                 \n\t"
    "psubh      $f18, $f18, $f10                \n\t"
    "paddh      $f0, $f0, $f4                   \n\t"
    "paddh      $f2, $f2, $f6                   \n\t"
    "paddh      $f0, $f0, $f16                  \n\t"
    "paddh      $f2, $f2, $f18                  \n\t"
    "psllh      $f16, $f16, $f26                \n\t"
    "psllh      $f18, $f18, $f26                \n\t"
    "paddh      $f0, $f0, $f16                  \n\t"
    "paddh      $f2, $f2, $f18                  \n\t"
    "paddh      $f0, $f0, $f24                  \n\t"
    "paddh      $f2, $f2, $f24                  \n\t"
    "psrah      $f0, $f0, $f30                  \n\t"
    "psrah      $f2, $f2, $f30                  \n\t"
    "packushb   $f0, $f0, $f2                   \n\t"
    "gssdlc1    $f0, 0xF(%[pDst])               \n\t"
    "gssdrc1    $f0, 0x8(%[pDst])               \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "bnez       %[iHeight], 1b                  \n\t"
    : [pSrc]"+&r"(pSrc), [pDst]"+&r"(pDst), [iHeight]"+&r"(iHeight)
    : [iSrcStride]"r"(iSrcStride), [iDstStride]"r"(iDstStride)
    : "memory", "$8", "$f0", "$f2", "$f4", "$f6", "$f8", "$f10", "$f12",
      "$f14", "$f16", "$f18", "$f20", "$f22", "$f24", "$f26", "$f28", "$f30"
  );
  RECOVER_REG;
}

void McHorVer20WidthEq4_mmi(const uint8_t *pSrc, int iSrcStride, uint8_t *pDst,
                            int iDstStride, int iHeight) {
  __asm__ volatile (
    ".set       arch=loongson3a                 \n\t"
    "1:                                         \n\t"
    PTR_ADDIU  "%[pSrc], %[pSrc], -0x2          \n\t"
    "xor        $f14, $f14, $f14                \n\t"
    "dli        $8, 0x0010001000100010          \n\t"
    "dmtc1      $8, $f12                        \n\t"
    "1:                                         \n\t"
    "gsldlc1    $f0, 0x7(%[pSrc])               \n\t"
    "gsldlc1    $f2, 0xc(%[pSrc])               \n\t"
    "gsldlc1    $f4, 0x8(%[pSrc])               \n\t"
    "gsldlc1    $f6, 0xb(%[pSrc])               \n\t"
    "gsldlc1    $f8, 0x9(%[pSrc])               \n\t"
    "gsldlc1    $f10, 0xa(%[pSrc])              \n\t"
    "gsldrc1    $f0, 0x0(%[pSrc])               \n\t"
    "gsldrc1    $f2, 0x5(%[pSrc])               \n\t"
    "gsldrc1    $f4, 0x1(%[pSrc])               \n\t"
    "gsldrc1    $f6, 0x4(%[pSrc])               \n\t"
    "gsldrc1    $f8, 0x2(%[pSrc])               \n\t"
    "gsldrc1    $f10, 0x3(%[pSrc])              \n\t"
    "dli        $8, 0x2                         \n\t"
    "punpcklbh  $f0, $f0, $f14                  \n\t"
    "punpcklbh  $f2, $f2, $f14                  \n\t"
    "punpcklbh  $f4, $f4, $f14                  \n\t"
    "punpcklbh  $f6, $f6, $f14                  \n\t"
    "punpcklbh  $f8, $f8, $f14                  \n\t"
    "punpcklbh  $f10, $f10, $f14                \n\t"
    "dmtc1      $8, $f16                        \n\t"
    "paddh      $f4, $f4, $f6                   \n\t"
    "paddh      $f8, $f8, $f10                  \n\t"
    "psllh      $f8, $f8, $f16                  \n\t"
    "psubh      $f8, $f8, $f4                   \n\t"
    "paddh      $f0, $f0, $f2                   \n\t"
    "paddh      $f0, $f0, $f8                   \n\t"
    "dli        $8, 0x5                         \n\t"
    "psllh      $f8, $f8, $f16                  \n\t"
    "paddh      $f0, $f0, $f8                   \n\t"
    "paddh      $f0, $f0, $f12                  \n\t"
    "dmtc1      $8, $f16                        \n\t"
    "psrah      $f0, $f0, $f16                  \n\t"
    "packushb   $f0, $f0, $f14                  \n\t"
    "gsswlc1    $f0, 0x3(%[pDst])               \n\t"
    "gsswrc1    $f0, 0x0(%[pDst])               \n\t"
    PTR_ADDU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "bnez       %[iHeight], 1b                  \n\t"
    : [pSrc]"+&r"(pSrc), [pDst]"+&r"(pDst), [iHeight]"+&r"(iHeight)
    : [iSrcStride]"r"(iSrcStride), [iDstStride]"r"(iDstStride)
    : "memory", "$8", "$f0", "$f2", "$f4", "$f6", "$f8", "$f10", "$f12",
      "$f14", "$f16"
  );
}

static inline void McHorVer20_mmi(const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst,
                                  int32_t iDstStride, int32_t iWidth, int32_t iHeight) {
  if (iWidth == 16)
    McHorVer20WidthEq16_mmi (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McHorVer20WidthEq8_mmi (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else
    McHorVer20WidthEq4_mmi (pSrc, iSrcStride, pDst, iDstStride, iHeight);
}

void McHorVer02WidthEq8_mmi(const uint8_t *pSrc, int iSrcStride, uint8_t *pDst,
                            int iDstStride, int iHeight) {
  BACKUP_REG;
  __asm__ volatile (
    ".set       arch=loongson3a                 \n\t"
    PTR_SUBU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    PTR_SUBU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    "xor        $f28, $f28, $f28                \n\t"
    MMI_LOAD_8P($f0, $f2, $f28, %[pSrc])
    PTR_ADDU   "$8, %[pSrc], %[iSrcStride]      \n\t"
    MMI_LOAD_8P($f4, $f6, $f28, $8)
    PTR_ADDU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    PTR_ADDU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    MMI_LOAD_8P($f8, $f10, $f28, %[pSrc])
    PTR_ADDU   "$8, %[pSrc], %[iSrcStride]      \n\t"
    MMI_LOAD_8P($f12, $f14, $f28, $8)
    PTR_ADDU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    PTR_ADDU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    MMI_LOAD_8P($f16, $f18, $f28, %[pSrc])
    PTR_ADDU   "$8, %[pSrc], %[iSrcStride]      \n\t"
    MMI_LOAD_8P($f20, $f22, $f28, $8)

    "1:                                         \n\t"
    FILTER_HV_W8($f0, $f2, $f4, $f6, $f8, $f10, $f12, $f14, $f16, $f18, $f20,
                 $f22, $f24, $f26, $f28, $f30, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 2f                  \n\t"
    PTR_ADDU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    PTR_ADDU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    MMI_LOAD_8P($f24, $f26, $f28, %[pSrc])
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    FILTER_HV_W8($f4, $f6, $f8, $f10, $f12, $f14, $f16, $f18, $f20, $f22, $f24,
                 $f26, $f28, $f30, $f0, $f2, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 2f                  \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDU   "$8, %[pSrc], %[iSrcStride]      \n\t"
    MMI_LOAD_8P($f28, $f30, $f0, $8)
    FILTER_HV_W8($f8, $f10, $f12, $f14, $f16, $f18, $f20, $f22, $f24, $f26, $f28,
                 $f30, $f0, $f2, $f4, $f6, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 2f                  \n\t"
    PTR_ADDU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    PTR_ADDU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    MMI_LOAD_8P($f0, $f2, $f4, %[pSrc])
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    FILTER_HV_W8($f12, $f14, $f16, $f18, $f20, $f22, $f24, $f26, $f28, $f30, $f0,
                 $f2, $f4, $f6, $f8, $f10, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 2f                  \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDU   "$8, %[pSrc], %[iSrcStride]      \n\t"
    MMI_LOAD_8P($f4, $f6, $f8, $8)
    FILTER_HV_W8($f16, $f18, $f20, $f22, $f24, $f26, $f28, $f30, $f0, $f2, $f4,
                 $f6, $f8, $f10, $f12, $f14, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 2f                  \n\t"
    PTR_ADDU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    PTR_ADDU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    MMI_LOAD_8P($f8, $f10, $f12, %[pSrc])
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    FILTER_HV_W8($f20, $f22, $f24, $f26, $f28, $f30, $f0, $f2, $f4, $f6, $f8,
                 $f10, $f12, $f14, $f16, $f18, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 2f                  \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDU   "$8, %[pSrc], %[iSrcStride]      \n\t"
    MMI_LOAD_8P($f12, $f14, $f16, $8)
    FILTER_HV_W8($f24, $f26, $f28, $f30, $f0, $f2, $f4, $f6, $f8, $f10, $f12,
                 $f14, $f16, $f18, $f20, $f22, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 2f                  \n\t"
    PTR_ADDU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    PTR_ADDU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    MMI_LOAD_8P($f16, $f18, $f20, %[pSrc])
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    FILTER_HV_W8($f28, $f30, $f0, $f2, $f4, $f6, $f8, $f10, $f12, $f14, $f16,
                 $f18, $f20, $f22, $f24, $f26, %[pDst], $8, $9)
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "beqz       %[iHeight], 2f                  \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDU   "$8, %[pSrc], %[iSrcStride]      \n\t"
    MMI_LOAD_8P($f20, $f22, $f24, $8)
    "j          1b                              \n\t"
    "2:                                         \n\t"
    : [pSrc]"+&r"(pSrc), [pDst]"+&r"(pDst), [iHeight]"+&r"(iHeight)
    : [iSrcStride]"r"(iSrcStride), [iDstStride]"r"(iDstStride)
    : "memory", "$8", "$9", "$f0", "$f2", "$f4", "$f6", "$f8", "$f10", "$f12",
      "$f14", "$f16", "$f18", "$f20", "$f22", "$f24", "$f26", "$f28", "$f30"
  );
  RECOVER_REG;
}

static inline void McHorVer02WidthEq16_mmi(const uint8_t* pSrc, int32_t iSrcStride,
                   uint8_t* pDst, int32_t iDstStride, int32_t iHeight) {
  McHorVer02WidthEq8_mmi (pSrc,     iSrcStride, pDst,     iDstStride, iHeight);
  McHorVer02WidthEq8_mmi (&pSrc[8], iSrcStride, &pDst[8], iDstStride, iHeight);
}

static inline void McHorVer02_mmi(const uint8_t* pSrc, int32_t iSrcStride,
                   uint8_t* pDst, int32_t iDstStride, int32_t iWidth,
                   int32_t iHeight) {
  if (iWidth == 16)
    McHorVer02WidthEq16_mmi (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McHorVer02WidthEq8_mmi (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else
    McHorVer02_c (pSrc, iSrcStride, pDst, iDstStride, 4, iHeight);
}

void McHorVer22Width8HorFirst_mmi(const uint8_t *pSrc, int16_t iSrcStride,
     uint8_t *pDst, int32_t iDstStride, int32_t iHeight) {
  BACKUP_REG;
  __asm__ volatile (
    ".set       arch=loongson3a                 \n\t"
    "xor        $f28, $f28, $f28                \n\t"
    PTR_SUBU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    PTR_SUBU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    "dli        $8, 0x2                         \n\t"
    "dmtc1      $8, $f30                        \n\t"
    "1:                                         \n\t"
    "xor        $f28, $f28, $f28                \n\t"
    "gsldlc1    $f0, 0x7(%[pSrc])               \n\t"
    "gsldlc1    $f4, 0xc(%[pSrc])               \n\t"
    "gsldlc1    $f8, 0x8(%[pSrc])               \n\t"
    "gsldlc1    $f12, 0xb(%[pSrc])              \n\t"
    "gsldlc1    $f16, 0x9(%[pSrc])              \n\t"
    "gsldlc1    $f20, 0xa(%[pSrc])              \n\t"
    "gsldrc1    $f0, 0x0(%[pSrc])               \n\t"
    "gsldrc1    $f4, 0x5(%[pSrc])               \n\t"
    "gsldrc1    $f8, 0x1(%[pSrc])               \n\t"
    "gsldrc1    $f12, 0x4(%[pSrc])              \n\t"
    "gsldrc1    $f16, 0x2(%[pSrc])              \n\t"
    "gsldrc1    $f20, 0x3(%[pSrc])              \n\t"
    "punpckhbh  $f2, $f0, $f28                  \n\t"
    "punpckhbh  $f6, $f4, $f28                  \n\t"
    "punpckhbh  $f10, $f8, $f28                 \n\t"
    "punpckhbh  $f14, $f12, $f28                \n\t"
    "punpckhbh  $f18, $f16, $f28                \n\t"
    "punpckhbh  $f22, $f20, $f28                \n\t"
    "punpcklbh  $f0, $f0, $f28                  \n\t"
    "punpcklbh  $f4, $f4, $f28                  \n\t"
    "punpcklbh  $f8, $f8, $f28                  \n\t"
    "punpcklbh  $f12, $f12, $f28                \n\t"
    "punpcklbh  $f16, $f16, $f28                \n\t"
    "punpcklbh  $f20, $f20, $f28                \n\t"
    "paddh      $f8, $f8, $f12                  \n\t"
    "paddh      $f10, $f10, $f14                \n\t"
    "paddh      $f16, $f16, $f20                \n\t"
    "paddh      $f18, $f18, $f22                \n\t"
    "psllh      $f16, $f16, $f30                \n\t"
    "psllh      $f18, $f18, $f30                \n\t"
    "psubh      $f16, $f16, $f8                 \n\t"
    "psubh      $f18, $f18, $f10                \n\t"
    "paddh      $f0, $f0, $f4                   \n\t"
    "paddh      $f2, $f2, $f6                   \n\t"
    "paddh      $f0, $f0, $f16                  \n\t"
    "paddh      $f2, $f2, $f18                  \n\t"
    "psllh      $f16, $f16, $f30                \n\t"
    "psllh      $f18, $f18, $f30                \n\t"
    "paddh      $f0, $f0, $f16                  \n\t"
    "paddh      $f2, $f2, $f18                  \n\t"
    "gssdlc1    $f0, 0x7(%[pDst])               \n\t"
    "gssdlc1    $f2, 0xF(%[pDst])               \n\t"
    "gssdrc1    $f0, 0x0(%[pDst])               \n\t"
    "gssdrc1    $f2, 0x8(%[pDst])               \n\t"
    PTR_ADDU   "%[pSrc], %[pSrc], %[iSrcStride] \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride] \n\t"
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1    \n\t"
    "bnez       %[iHeight], 1b                  \n\t"
    : [pSrc]"+&r"(pSrc), [pDst]"+&r"(pDst), [iHeight]"+&r"(iHeight)
    : [iSrcStride]"r"(iSrcStride),  [iDstStride]"r"(iDstStride)
    : "memory", "$8", "$f0", "$f2", "$f4", "$f6", "$f8", "$f10", "$f12",
      "$f14", "$f16", "$f18", "$f20", "$f22", "$f24", "$f26", "$f28", "$f30"
  );
  RECOVER_REG;
}

static inline void McHorVer22WidthEq8_mmi(const uint8_t* pSrc, int32_t iSrcStride,
                   uint8_t* pDst, int32_t iDstStride, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_2D (int16_t, iTap, 21, 8, 16)
  McHorVer22Width8HorFirst_mmi (pSrc - 2, iSrcStride, (uint8_t*)iTap, 16, iHeight + 5);
  McHorVer22Width8VerLastAlign_mmi ((uint8_t*)iTap, 16, pDst, iDstStride, 8, iHeight);
}

static inline void McHorVer22WidthEq16_mmi(const uint8_t* pSrc, int32_t iSrcStride,
                   uint8_t* pDst, int32_t iDstStride, int32_t iHeight) {
  McHorVer22WidthEq8_mmi (pSrc,     iSrcStride, pDst,     iDstStride, iHeight);
  McHorVer22WidthEq8_mmi (&pSrc[8], iSrcStride, &pDst[8], iDstStride, iHeight);
}

static inline void McHorVer22_mmi(const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst,
                   int32_t iDstStride, int32_t iWidth, int32_t iHeight) {
  if (iWidth == 16)
    McHorVer22WidthEq16_mmi (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else if (iWidth == 8)
    McHorVer22WidthEq8_mmi (pSrc, iSrcStride, pDst, iDstStride, iHeight);
  else
    McHorVer22_c (pSrc, iSrcStride, pDst, iDstStride, 4, iHeight);
}

void PixelAvgWidthEq4_mmi(uint8_t *pDst,  int iDstStride, const uint8_t *pSrcA,
     int iSrcAStride, const uint8_t *pSrcB, int iSrcBStride, int iHeight ) {
  __asm__ volatile (
    ".set       arch=loongson3a                    \n\t"
    "1:                                            \n\t"
    "gsldlc1    $f0, 0x7(%[pSrcB])                 \n\t"
    "gsldlc1    $f2, 0x7(%[pSrcA])                 \n\t"
    "gsldrc1    $f0, 0x0(%[pSrcB])                 \n\t"
    "gsldrc1    $f2, 0x0(%[pSrcA])                 \n\t"
    "pavgb      $f0, $f0, $f2                      \n\t"
    "gsswlc1    $f0, 0x3(%[pDst])                  \n\t"
    "gsswrc1    $f0, 0x0(%[pDst])                  \n\t"
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x1       \n\t"
    PTR_ADDU   "%[pDst], %[pDst], %[iDstStride]    \n\t"
    PTR_ADDU   "%[pSrcA], %[pSrcA], %[iSrcAStride] \n\t"
    PTR_ADDU   "%[pSrcB], %[pSrcB], %[iSrcBStride] \n\t"
    "bnez       %[iHeight], 1b                     \n\t"
    : [pDst]"+&r"((unsigned char *)pDst), [pSrcA]"+&r"((unsigned char *)pSrcA),
      [pSrcB]"+&r"((unsigned char *)pSrcB), [iHeight]"+&r"((int)iHeight)
    : [iDstStride]"r"((int)iDstStride), [iSrcAStride]"r"((int)iSrcAStride),
      [iSrcBStride]"r"((int)iSrcBStride)
    : "memory", "$8", "$9", "$10", "$f0", "$f2"
  );
}

void PixelAvgWidthEq8_mmi(uint8_t *pDst,  int iDstStride, const uint8_t *pSrcA,
     int iSrcAStride, const uint8_t *pSrcB, int iSrcBStride, int iHeight ) {
  __asm__ volatile (
    ".set       arch=loongson3a                 \n\t"
    "1:                                         \n\t"
    "gsldlc1    $f0, 0x7(%[pSrcA])              \n\t"
    "gsldlc1    $f2, 0x7(%[pSrcB])              \n\t"
    "gsldrc1    $f0, 0x0(%[pSrcA])              \n\t"
    "gsldrc1    $f2, 0x0(%[pSrcB])              \n\t"
    "pavgb      $f0, $f0, $f2                   \n\t"
    PTR_ADDU   "$8, %[pSrcA], %[iSrcAStride]    \n\t"
    "gssdlc1    $f0, 0x7(%[pDst])               \n\t"
    PTR_ADDU   "$9, %[pSrcB], %[iSrcBStride]    \n\t"
    "gssdrc1    $f0, 0x0(%[pDst])               \n\t"
    "gsldlc1    $f0, 0x7($8)                    \n\t"
    "gsldlc1    $f2, 0x7($9)                    \n\t"
    "gsldrc1    $f0, 0x0($8)                    \n\t"
    "gsldrc1    $f2, 0x0($9)                    \n\t"
    "pavgb      $f0, $f0, $f2                   \n\t"
    PTR_ADDU   "$10, %[pDst], %[iDstStride]     \n\t"
    "gssdlc1    $f0, 0x7($10)                   \n\t"
    PTR_ADDU   "%[pSrcA], $8, %[iSrcAStride]    \n\t"
    "gssdrc1    $f0, 0x0($10)                   \n\t"
    PTR_ADDU   "%[pSrcB], $9, %[iSrcBStride]    \n\t"
    PTR_ADDU   "%[pDst], $10, %[iDstStride]     \n\t"
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x2    \n\t"
    "bnez       %[iHeight], 1b                  \n\t"
    : [pDst]"+&r"((unsigned char *)pDst), [pSrcA]"+&r"((unsigned char *)pSrcA),
      [pSrcB]"+&r"((unsigned char *)pSrcB), [iHeight]"+&r"((int)iHeight)
    : [iDstStride]"r"((int)iDstStride), [iSrcAStride]"r"((int)iSrcAStride),
      [iSrcBStride]"r"((int)iSrcBStride)
    : "memory", "$8", "$9", "$10", "$f0", "$f2"
  );
}

void PixelAvgWidthEq16_mmi(uint8_t *pDst, int iDstStride, const uint8_t *pSrcA,
     int iSrcAStride, const uint8_t *pSrcB, int iSrcBStride, int iHeight ) {
  __asm__ volatile (
    ".set       arch=loongson3a                 \n\t"
    "1:                                         \n\t"
    "gsldlc1    $f0, 0x7(%[pSrcA])              \n\t"
    "gsldlc1    $f2, 0xF(%[pSrcA])              \n\t"
    "gsldlc1    $f4, 0x7(%[pSrcB])              \n\t"
    "gsldlc1    $f6, 0xF(%[pSrcB])              \n\t"
    "gsldrc1    $f0, 0x0(%[pSrcA])              \n\t"
    "gsldrc1    $f2, 0x8(%[pSrcA])              \n\t"
    "gsldrc1    $f4, 0x0(%[pSrcB])              \n\t"
    "gsldrc1    $f6, 0x8(%[pSrcB])              \n\t"
    "pavgb      $f0, $f0, $f4                   \n\t"
    "pavgb      $f2, $f2, $f6                   \n\t"
    PTR_ADDU   "$8, %[pSrcA], %[iSrcAStride]    \n\t"
    "gssdlc1    $f0, 0x7(%[pDst])               \n\t"
    "gssdlc1    $f2, 0xF(%[pDst])               \n\t"
    "gssdrc1    $f0, 0x0(%[pDst])               \n\t"
    "gssdrc1    $f2, 0x8(%[pDst])               \n\t"
    PTR_ADDU   "$9, %[pSrcB], %[iSrcBStride]    \n\t"
    "gsldlc1    $f0, 0x7($8)                    \n\t"
    "gsldlc1    $f2, 0xF($8)                    \n\t"
    "gsldrc1    $f0, 0x0($8)                    \n\t"
    "gsldrc1    $f2, 0x8($8)                    \n\t"
    PTR_ADDU   "$10, %[pDst], %[iDstStride]     \n\t"
    "gsldlc1    $f4, 0x7($9)                    \n\t"
    "gsldlc1    $f6, 0xF($9)                    \n\t"
    "gsldrc1    $f4, 0x0($9)                    \n\t"
    "gsldrc1    $f6, 0x8($9)                    \n\t"
    "pavgb      $f0, $f0, $f4                   \n\t"
    "pavgb      $f2, $f2, $f6                   \n\t"
    "gssdlc1    $f0, 0x7($10)                   \n\t"
    "gssdlc1    $f2, 0xF($10)                   \n\t"
    "gssdrc1    $f0, 0x0($10)                   \n\t"
    "gssdrc1    $f2, 0x8($10)                   \n\t"

    PTR_ADDU   "%[pSrcA], $8, %[iSrcAStride]    \n\t"
    PTR_ADDU   "%[pSrcB], $9, %[iSrcBStride]    \n\t"
    PTR_ADDU   "%[pDst], $10, %[iDstStride]     \n\t"
    "gsldlc1    $f0, 0x7(%[pSrcA])              \n\t"
    "gsldlc1    $f2, 0xF(%[pSrcA])              \n\t"
    "gsldlc1    $f4, 0x7(%[pSrcB])              \n\t"
    "gsldlc1    $f6, 0xF(%[pSrcB])              \n\t"
    "gsldrc1    $f0, 0x0(%[pSrcA])              \n\t"
    "gsldrc1    $f2, 0x8(%[pSrcA])              \n\t"
    "gsldrc1    $f4, 0x0(%[pSrcB])              \n\t"
    "gsldrc1    $f6, 0x8(%[pSrcB])              \n\t"
    "pavgb      $f0, $f0, $f4                   \n\t"
    "pavgb      $f2, $f2, $f6                   \n\t"
    PTR_ADDU   "$8, %[pSrcA], %[iSrcAStride]    \n\t"
    PTR_ADDU   "$9, %[pSrcB], %[iSrcBStride]    \n\t"
    "gssdlc1    $f0, 0x7(%[pDst])               \n\t"
    "gssdlc1    $f2, 0xF(%[pDst])               \n\t"
    "gssdrc1    $f0, 0x0(%[pDst])               \n\t"
    "gssdrc1    $f2, 0x8(%[pDst])               \n\t"
    "gsldlc1    $f0, 0x7($8)                    \n\t"
    "gsldlc1    $f2, 0xF($8)                    \n\t"
    "gsldlc1    $f4, 0x7($9)                    \n\t"
    "gsldlc1    $f6, 0xF($9)                    \n\t"
    "gsldrc1    $f0, 0x0($8)                    \n\t"
    "gsldrc1    $f2, 0x8($8)                    \n\t"
    "gsldrc1    $f4, 0x0($9)                    \n\t"
    "gsldrc1    $f6, 0x8($9)                    \n\t"
    PTR_ADDU   "$10, %[pDst], %[iDstStride]     \n\t"
    "pavgb      $f0, $f0, $f4                   \n\t"
    "pavgb      $f2, $f2, $f6                   \n\t"
    "gssdlc1    $f0, 0x7($10)                   \n\t"
    "gssdlc1    $f2, 0xF($10)                   \n\t"
    "gssdrc1    $f0, 0x0($10)                   \n\t"
    "gssdrc1    $f2, 0x8($10)                   \n\t"
    PTR_ADDU   "%[pSrcA], $8, %[iSrcAStride]    \n\t"
    PTR_ADDU   "%[pSrcB], $9, %[iSrcBStride]    \n\t"
    PTR_ADDU   "%[pDst], $10, %[iDstStride]     \n\t"
    PTR_ADDIU  "%[iHeight], %[iHeight], -0x4    \n\t"
    "bnez       %[iHeight], 1b                  \n\t"
    : [pDst]"+&r"((unsigned char *)pDst), [pSrcA]"+&r"((unsigned char *)pSrcA),
      [pSrcB]"+&r"((unsigned char *)pSrcB), [iHeight]"+&r"((int)iHeight)
    : [iDstStride]"r"((int)iDstStride), [iSrcAStride]"r"((int)iSrcAStride),
      [iSrcBStride]"r"((int)iSrcBStride)
    : "memory", "$8", "$9", "$10", "$f0", "$f2", "$f4", "$f6"
  );
}

static inline void McHorVer01_mmi(const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst,
                                  int32_t iDstStride, int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer02WidthEq16_mmi (pSrc, iSrcStride, pTmp, 16, iHeight);
    PixelAvgWidthEq16_mmi (pDst, iDstStride, pSrc, iSrcStride, pTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer02WidthEq8_mmi (pSrc, iSrcStride, pTmp, 16, iHeight);
    PixelAvgWidthEq8_mmi (pDst, iDstStride, pSrc, iSrcStride, pTmp, 16, iHeight);
  } else {
    McHorVer02_c (pSrc, iSrcStride, pTmp, 16, 4, iHeight);
    PixelAvgWidthEq4_mmi (pDst, iDstStride, pSrc, iSrcStride, pTmp, 16, iHeight);
  }
}

static inline void McHorVer03_mmi(const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst,
                                  int32_t iDstStride, int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer02WidthEq16_mmi (pSrc, iSrcStride, pTmp, 16, iHeight);
    PixelAvgWidthEq16_mmi (pDst, iDstStride, pSrc + iSrcStride, iSrcStride, pTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer02WidthEq8_mmi (pSrc, iSrcStride, pTmp, 16, iHeight);
    PixelAvgWidthEq8_mmi (pDst, iDstStride, pSrc + iSrcStride, iSrcStride, pTmp, 16, iHeight);
  } else {
    McHorVer02_c (pSrc, iSrcStride, pTmp, 16, 4, iHeight);
    PixelAvgWidthEq4_mmi (pDst, iDstStride, pSrc + iSrcStride, iSrcStride, pTmp, 16, iHeight);
  }
}

static inline void McHorVer10_mmi(const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst,
                                  int32_t iDstStride, int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_mmi (pSrc, iSrcStride, pTmp, 16, iHeight);
    PixelAvgWidthEq16_mmi (pDst, iDstStride, pSrc, iSrcStride, pTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_mmi (pSrc, iSrcStride, pTmp, 16, iHeight);
    PixelAvgWidthEq8_mmi (pDst, iDstStride, pSrc, iSrcStride, pTmp, 16, iHeight);
  } else {
    McHorVer20WidthEq4_mmi (pSrc, iSrcStride, pTmp, 16, iHeight);
    PixelAvgWidthEq4_mmi (pDst, iDstStride, pSrc, iSrcStride, pTmp, 16, iHeight);
  }
}

static inline void McHorVer11_mmi(const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst,
                                  int32_t iDstStride, int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pVerTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_mmi (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq16_mmi (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq16_mmi (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_mmi (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq8_mmi (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq8_mmi (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  } else {
    McHorVer20WidthEq4_mmi (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02_c (pSrc, iSrcStride, pVerTmp, 16, 4, iHeight);
    PixelAvgWidthEq4_mmi (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  }
}

static inline void McHorVer12_mmi(const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst,
                                  int32_t iDstStride, int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pVerTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pCtrTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer02WidthEq16_mmi (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    McHorVer22WidthEq16_mmi (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq16_mmi (pDst, iDstStride, pVerTmp, 16, pCtrTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer02WidthEq8_mmi (pSrc, iSrcStride, pVerTmp, 16, iHeight);
    McHorVer22WidthEq8_mmi (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq8_mmi (pDst, iDstStride, pVerTmp, 16, pCtrTmp, 16, iHeight);
  } else {
    McHorVer02_c (pSrc, iSrcStride, pVerTmp, 16, 4, iHeight);
    McHorVer22_c (pSrc, iSrcStride, pCtrTmp, 16, 4, iHeight);
    PixelAvgWidthEq4_mmi (pDst, iDstStride, pVerTmp, 16, pCtrTmp, 16, iHeight);
  }
}
static inline void McHorVer13_mmi(const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst,
                                  int32_t iDstStride, int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pVerTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_mmi (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq16_mmi (pSrc,            iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq16_mmi (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_mmi (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq8_mmi (pSrc,            iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq8_mmi (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  } else {
    McHorVer20WidthEq4_mmi (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02_c (pSrc,            iSrcStride, pVerTmp, 16, 4 , iHeight);
    PixelAvgWidthEq4_mmi (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  }
}
static inline void McHorVer21_mmi(const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst,
                                  int32_t iDstStride, int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pCtrTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_mmi (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer22WidthEq16_mmi (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq16_mmi (pDst, iDstStride, pHorTmp, 16, pCtrTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_mmi (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer22WidthEq8_mmi (pSrc, iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq8_mmi (pDst, iDstStride, pHorTmp, 16, pCtrTmp, 16, iHeight);
  } else {
    McHorVer20WidthEq4_mmi (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer22_c (pSrc, iSrcStride, pCtrTmp, 16, 4, iHeight);
    PixelAvgWidthEq4_mmi (pDst, iDstStride, pHorTmp, 16, pCtrTmp, 16, iHeight);
  }
}

static inline void McHorVer23_mmi(const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst,
                                  int32_t iDstStride, int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pCtrTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_mmi (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer22WidthEq16_mmi (pSrc,            iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq16_mmi (pDst, iDstStride, pHorTmp, 16, pCtrTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_mmi (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer22WidthEq8_mmi (pSrc,            iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq8_mmi (pDst, iDstStride, pHorTmp, 16, pCtrTmp, 16, iHeight);
  } else {
    McHorVer20WidthEq4_mmi (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer22_c (pSrc,            iSrcStride, pCtrTmp, 16, 4, iHeight);
    PixelAvgWidthEq4_mmi (pDst, iDstStride, pHorTmp, 16, pCtrTmp, 16, iHeight);
  }
}
static inline void McHorVer30_mmi(const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst,
                                  int32_t iDstStride, int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_mmi (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    PixelAvgWidthEq16_mmi (pDst, iDstStride, pSrc + 1, iSrcStride, pHorTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_mmi (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    PixelAvgWidthEq8_mmi (pDst, iDstStride, pSrc + 1, iSrcStride, pHorTmp, 16, iHeight);
  } else {
    McHorVer20WidthEq4_mmi (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    PixelAvgWidthEq4_mmi (pDst, iDstStride, pSrc + 1, iSrcStride, pHorTmp, 16, iHeight);
  }
}
static inline void McHorVer31_mmi(const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst,
                                  int32_t iDstStride, int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pVerTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_mmi (pSrc,   iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq16_mmi (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq16_mmi (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_mmi (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq8_mmi (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq8_mmi (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  } else {
    McHorVer20WidthEq4_mmi (pSrc, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02_c (pSrc + 1, iSrcStride, pVerTmp, 16, 4, iHeight);
    PixelAvgWidthEq4_mmi (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  }
}
static inline void McHorVer32_mmi(const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst,
                                  int32_t iDstStride, int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pVerTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pCtrTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer02WidthEq16_mmi (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    McHorVer22WidthEq16_mmi (pSrc,   iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq16_mmi (pDst, iDstStride, pVerTmp, 16, pCtrTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer02WidthEq8_mmi (pSrc + 1, iSrcStride, pVerTmp, 16, iHeight);
    McHorVer22WidthEq8_mmi (pSrc,   iSrcStride, pCtrTmp, 16, iHeight);
    PixelAvgWidthEq8_mmi (pDst, iDstStride, pVerTmp, 16, pCtrTmp, 16, iHeight);
  } else {
    McHorVer02_c (pSrc + 1, iSrcStride, pVerTmp, 16, 4, iHeight);
    McHorVer22_c (pSrc,   iSrcStride, pCtrTmp, 16, 4, iHeight);
    PixelAvgWidthEq4_mmi (pDst, iDstStride, pVerTmp, 16, pCtrTmp, 16, iHeight);
  }
}
static inline void McHorVer33_mmi(const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst,
                                  int32_t iDstStride, int32_t iWidth, int32_t iHeight) {
  ENFORCE_STACK_ALIGN_1D (uint8_t, pHorTmp, 256, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, pVerTmp, 256, 16);
  if (iWidth == 16) {
    McHorVer20WidthEq16_mmi (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq16_mmi (pSrc + 1,          iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq16_mmi (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  } else if (iWidth == 8) {
    McHorVer20WidthEq8_mmi (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02WidthEq8_mmi (pSrc + 1,          iSrcStride, pVerTmp, 16, iHeight);
    PixelAvgWidthEq8_mmi (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  } else {
    McHorVer20WidthEq4_mmi (pSrc + iSrcStride, iSrcStride, pHorTmp, 16, iHeight);
    McHorVer02_c (pSrc + 1,          iSrcStride, pVerTmp, 16, 4, iHeight);
    PixelAvgWidthEq4_mmi (pDst, iDstStride, pHorTmp, 16, pVerTmp, 16, iHeight);
  }
}

void McLuma_mmi(const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
                int16_t iMvX, int16_t iMvY, int32_t iWidth, int32_t iHeight) {
  static const PWelsMcWidthHeightFunc pWelsMcFunc[4][4] = { //[x][y]
    {McCopy_mmi,     McHorVer01_mmi, McHorVer02_mmi, McHorVer03_mmi},
    {McHorVer10_mmi, McHorVer11_mmi, McHorVer12_mmi, McHorVer13_mmi},
    {McHorVer20_mmi, McHorVer21_mmi, McHorVer22_mmi, McHorVer23_mmi},
    {McHorVer30_mmi, McHorVer31_mmi, McHorVer32_mmi, McHorVer33_mmi},
  };

  pWelsMcFunc[iMvX & 0x03][iMvY & 0x03] (pSrc, iSrcStride, pDst, iDstStride, iWidth, iHeight);
}

void PixelAvg_mmi(uint8_t* pDst, int32_t iDstStride, const uint8_t* pSrcA, int32_t iSrcAStride,
                  const uint8_t* pSrcB, int32_t iSrcBStride, int32_t iWidth, int32_t iHeight) {
  static const PWelsSampleWidthAveragingFunc kpfFuncs[2] = {
    PixelAvgWidthEq8_mmi,
    PixelAvgWidthEq16_mmi
  };
  kpfFuncs[iWidth >> 4] (pDst, iDstStride, pSrcA, iSrcAStride, pSrcB, iSrcBStride, iHeight);
}
#endif//HAVE_MMI
} // anon ns.

void WelsCommon::InitMcFunc (SMcFunc* pMcFuncs, uint32_t uiCpuFlag) {
  pMcFuncs->pfLumaHalfpelHor  = McHorVer20_c;
  pMcFuncs->pfLumaHalfpelVer  = McHorVer02_c;
  pMcFuncs->pfLumaHalfpelCen  = McHorVer22_c;
  pMcFuncs->pfSampleAveraging = PixelAvg_c;
  pMcFuncs->pMcChromaFunc     = McChroma_c;
  pMcFuncs->pMcLumaFunc       = McLuma_c;

#if defined (X86_ASM)
  if (uiCpuFlag & WELS_CPU_SSE2) {
    pMcFuncs->pfLumaHalfpelHor  = McHorVer20Width5Or9Or17_sse2;
    pMcFuncs->pfLumaHalfpelVer  = McHorVer02Height5Or9Or17_sse2;
    pMcFuncs->pfLumaHalfpelCen  = McHorVer22Width5Or9Or17Height5Or9Or17_sse2;
    pMcFuncs->pfSampleAveraging = PixelAvg_sse2;
    pMcFuncs->pMcChromaFunc     = McChroma_sse2;
    pMcFuncs->pMcLumaFunc       = McLuma_sse2;
  }

  if (uiCpuFlag & WELS_CPU_SSSE3) {
    pMcFuncs->pfLumaHalfpelHor  = McHorVer20Width5Or9Or17_ssse3;
    pMcFuncs->pfLumaHalfpelVer  = McHorVer02_ssse3;
    pMcFuncs->pfLumaHalfpelCen  = McHorVer22Width5Or9Or17_ssse3;
    pMcFuncs->pMcChromaFunc = McChroma_ssse3;
    pMcFuncs->pMcLumaFunc   = McLuma_ssse3;
  }
#ifdef HAVE_AVX2
  if (uiCpuFlag & WELS_CPU_AVX2) {
    pMcFuncs->pfLumaHalfpelHor  = McHorVer20Width5Or9Or17_avx2;
    pMcFuncs->pfLumaHalfpelVer  = McHorVer02_avx2;
    pMcFuncs->pfLumaHalfpelCen  = McHorVer22Width5Or9Or17_avx2;
    pMcFuncs->pMcLumaFunc       = McLuma_avx2;
  }
#endif
#endif //(X86_ASM)

#if defined(HAVE_NEON)
  if (uiCpuFlag & WELS_CPU_NEON) {
    pMcFuncs->pMcLumaFunc       = McLuma_neon;
    pMcFuncs->pMcChromaFunc     = McChroma_neon;
    pMcFuncs->pfSampleAveraging = PixelAvg_neon;
    pMcFuncs->pfLumaHalfpelHor  = McHorVer20Width5Or9Or17_neon;//iWidth+1:4/8/16
    pMcFuncs->pfLumaHalfpelVer  = McHorVer02Height5Or9Or17_neon;//heigh+1:4/8/16
    pMcFuncs->pfLumaHalfpelCen  = McHorVer22Width5Or9Or17Height5Or9Or17_neon;//iWidth+1/heigh+1
  }
#endif
#if defined(HAVE_NEON_AARCH64)
  if (uiCpuFlag & WELS_CPU_NEON) {
    pMcFuncs->pMcLumaFunc       = McLuma_AArch64_neon;
    pMcFuncs->pMcChromaFunc     = McChroma_AArch64_neon;
    pMcFuncs->pfSampleAveraging = PixelAvg_AArch64_neon;
    pMcFuncs->pfLumaHalfpelHor  = McHorVer20Width5Or9Or17_AArch64_neon;//iWidth+1:4/8/16
    pMcFuncs->pfLumaHalfpelVer  = McHorVer02Height5Or9Or17_AArch64_neon;//heigh+1:4/8/16
    pMcFuncs->pfLumaHalfpelCen  = McHorVer22Width5Or9Or17Height5Or9Or17_AArch64_neon;//iWidth+1/heigh+1
  }
#endif

#if defined(HAVE_MMI)
  if (uiCpuFlag & WELS_CPU_MMI) {
    pMcFuncs->pfLumaHalfpelHor  = McHorVer20Width5Or9Or17_mmi;
    pMcFuncs->pfLumaHalfpelVer  = McHorVer02Height5Or9Or17_mmi;
    pMcFuncs->pfLumaHalfpelCen  = McHorVer22Width5Or9Or17Height5Or9Or17_mmi;
    pMcFuncs->pfSampleAveraging = PixelAvg_mmi;
    pMcFuncs->pMcChromaFunc     = McChroma_mmi;
    pMcFuncs->pMcLumaFunc       = McLuma_mmi;
  }
#endif//HAVE_MMI
}
