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
 * \file    deblocking.c
 *
 * \brief   Interfaces introduced in frame deblocking filtering
 *
 * \date    08/03/2009 Created
 *
 *************************************************************************************
 */

#include "deblocking.h"
#include "cpu_core.h"

namespace WelsEnc {

#define g_kuiAlphaTable(x) g_kuiAlphaTable[(x)]
#define g_kiBetaTable(x)  g_kiBetaTable[(x)]
#define g_kiTc0Table(x)   g_kiTc0Table[(x)]

#define MB_BS_MV(sCurMv, sNeighMv, uiBIdx, uiBnIdx) \
  (\
  ( WELS_ABS( sCurMv[uiBIdx].iMvX - sNeighMv[uiBnIdx].iMvX ) >= 4 ) ||\
  ( WELS_ABS( sCurMv[uiBIdx].iMvY - sNeighMv[uiBnIdx].iMvY ) >= 4 )\
  )

#define SMB_EDGE_MV(uiRefIndex, sMotionVector, uiBIdx, uiBnIdx) \
  (\
  !!((WELS_ABS(sMotionVector[uiBIdx].iMvX - sMotionVector[uiBnIdx].iMvX) &(~3)) | (WELS_ABS(sMotionVector[uiBIdx].iMvY - sMotionVector[uiBnIdx].iMvY) &(~3)))\
  )

#define BS_EDGE(bsx1, uiRefIndex, sMotionVector, uiBIdx, uiBnIdx) \
  ( (bsx1|SMB_EDGE_MV(uiRefIndex, sMotionVector, uiBIdx, uiBnIdx))<<(bsx1?1:0))

#define GET_ALPHA_BETA_FROM_QP(QP, iAlphaOffset, iBetaOffset, iIdexA, iAlpha, iBeta) \
{\
  iIdexA = (QP + iAlphaOffset);\
  iIdexA = CLIP3_QP_0_51(iIdexA);\
  iAlpha = g_kuiAlphaTable(iIdexA);\
  iBeta  = g_kiBetaTable((CLIP3_QP_0_51(QP + iBetaOffset)));\
}

static const uint8_t g_kuiAlphaTable[52 + 12] = { //this table refers to Table 8-16 in H.264/AVC standard
  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
  0,  0,  0,  0,  0,  0,  4,  4,  5,  6,
  7,  8,  9, 10, 12, 13, 15, 17, 20, 22,
  25, 28, 32, 36, 40, 45, 50, 56, 63, 71,
  80, 90, 101, 113, 127, 144, 162, 182, 203, 226,
  255, 255
  , 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255
};

static const int8_t g_kiBetaTable[52 + 12] = { //this table refers to Table 8-16 in H.264/AVC standard
  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
  0,  0,  0,  0,  0,  0,  2,  2,  2,  3,
  3,  3,  3,  4,  4,  4,  6,  6,  7,  7,
  8,  8,  9,  9, 10, 10, 11, 11, 12, 12,
  13, 13, 14, 14, 15, 15, 16, 16, 17, 17,
  18, 18
  , 18, 18, 18, 18, 18, 18, 18, 18, 18, 18, 18, 18
};

static const int8_t g_kiTc0Table[52 + 12][4] = { //this table refers Table 8-17 in H.264/AVC standard
  { -1, 0, 0, 0 }, { -1, 0, 0, 0 }, { -1, 0, 0, 0 }, { -1, 0, 0, 0 }, { -1, 0, 0, 0 }, { -1, 0, 0, 0 },
  { -1, 0, 0, 0 }, { -1, 0, 0, 0 }, { -1, 0, 0, 0 }, { -1, 0, 0, 0 }, { -1, 0, 0, 0 }, { -1, 0, 0, 0 },
  { -1, 0, 0, 0 }, { -1, 0, 0, 0 }, { -1, 0, 0, 0 }, { -1, 0, 0, 0 }, { -1, 0, 0, 0 }, { -1, 0, 0, 1 },
  { -1, 0, 0, 1 }, { -1, 0, 0, 1 }, { -1, 0, 0, 1 }, { -1, 0, 1, 1 }, { -1, 0, 1, 1 }, { -1, 1, 1, 1 },
  { -1, 1, 1, 1 }, { -1, 1, 1, 1 }, { -1, 1, 1, 1 }, { -1, 1, 1, 2 }, { -1, 1, 1, 2 }, { -1, 1, 1, 2 },
  { -1, 1, 1, 2 }, { -1, 1, 2, 3 }, { -1, 1, 2, 3 }, { -1, 2, 2, 3 }, { -1, 2, 2, 4 }, { -1, 2, 3, 4 },
  { -1, 2, 3, 4 }, { -1, 3, 3, 5 }, { -1, 3, 4, 6 }, { -1, 3, 4, 6 }, { -1, 4, 5, 7 }, { -1, 4, 5, 8 },
  { -1, 4, 6, 9 }, { -1, 5, 7, 10 }, { -1, 6, 8, 11 }, { -1, 6, 8, 13 }, { -1, 7, 10, 14 }, { -1, 8, 11, 16 },
  { -1, 9, 12, 18 }, { -1, 10, 13, 20 }, { -1, 11, 15, 23 }, { -1, 13, 17, 25 }
  , { -1, 13, 17, 25 }, { -1, 13, 17, 25 }, { -1, 13, 17, 25 }, { -1, 13, 17, 25 }, { -1, 13, 17, 25 }, { -1, 13, 17, 25 }
  , { -1, 13, 17, 25 }, { -1, 13, 17, 25 }, { -1, 13, 17, 25 }, { -1, 13, 17, 25 }, { -1, 13, 17, 25 }, { -1, 13, 17, 25 }
};

static const uint8_t g_kuiTableBIdx[2][8] = {
  {
    0,  4,  8,  12, // g_kuiTableBIdx
    3,  7,  11, 15
  }, // table_bn_idx

  {
    0,  1,  2,  3 , // g_kuiTableBIdx
    12, 13, 14, 15
  }, // table_bn_idx
};

#define TC0_TBL_LOOKUP(iTc, iIdexA, pBS, bchroma) \
{\
  iTc[0] = g_kiTc0Table(iIdexA)[pBS[0]] + bchroma;\
  iTc[1] = g_kiTc0Table(iIdexA)[pBS[1]] + bchroma;\
  iTc[2] = g_kiTc0Table(iIdexA)[pBS[2]] + bchroma;\
  iTc[3] = g_kiTc0Table(iIdexA)[pBS[3]] + bchroma;\
}

void inline DeblockingBSInsideMBAvsbase (int8_t* pNnzTab, uint8_t uiBS[2][4][4], int32_t iLShiftFactor) {
  uint32_t uiNnz32b0, uiNnz32b1, uiNnz32b2, uiNnz32b3;

  uiNnz32b0 = * (uint32_t*) (pNnzTab + 0);
  uiNnz32b1 = * (uint32_t*) (pNnzTab + 4);
  uiNnz32b2 = * (uint32_t*) (pNnzTab + 8);
  uiNnz32b3 = * (uint32_t*) (pNnzTab + 12);

  uiBS[0][1][0] = (pNnzTab[0] | pNnzTab[1]) << iLShiftFactor;
  uiBS[0][2][0] = (pNnzTab[1] | pNnzTab[2]) << iLShiftFactor;
  uiBS[0][3][0] = (pNnzTab[2] | pNnzTab[3]) << iLShiftFactor;

  uiBS[0][1][1] = (pNnzTab[4] | pNnzTab[5]) << iLShiftFactor;
  uiBS[0][2][1] = (pNnzTab[5] | pNnzTab[6]) << iLShiftFactor;
  uiBS[0][3][1] = (pNnzTab[6] | pNnzTab[7]) << iLShiftFactor;
  * (uint32_t*)uiBS[1][1] = (uiNnz32b0 | uiNnz32b1) << iLShiftFactor;

  uiBS[0][1][2] = (pNnzTab[8]  | pNnzTab[9])  << iLShiftFactor;
  uiBS[0][2][2] = (pNnzTab[9]  | pNnzTab[10]) << iLShiftFactor;
  uiBS[0][3][2] = (pNnzTab[10] | pNnzTab[11]) << iLShiftFactor;
  * (uint32_t*)uiBS[1][2] = (uiNnz32b1 | uiNnz32b2) << iLShiftFactor;

  uiBS[0][1][3] = (pNnzTab[12] | pNnzTab[13]) << iLShiftFactor;
  uiBS[0][2][3] = (pNnzTab[13] | pNnzTab[14]) << iLShiftFactor;
  uiBS[0][3][3] = (pNnzTab[14] | pNnzTab[15]) << iLShiftFactor;
  * (uint32_t*)uiBS[1][3] = (uiNnz32b2 | uiNnz32b3) << iLShiftFactor;

}

void inline DeblockingBSInsideMBNormal (SMB* pCurMb, uint8_t uiBS[2][4][4], int8_t* pNnzTab) {
  uint32_t uiNnz32b0, uiNnz32b1, uiNnz32b2, uiNnz32b3;
  ENFORCE_STACK_ALIGN_1D (uint8_t, uiBsx4, 4, 4);

  uiNnz32b0 = * (uint32_t*) (pNnzTab + 0);
  uiNnz32b1 = * (uint32_t*) (pNnzTab + 4);
  uiNnz32b2 = * (uint32_t*) (pNnzTab + 8);
  uiNnz32b3 = * (uint32_t*) (pNnzTab + 12);

  for (int i = 0; i < 3; i++)
    uiBsx4[i] = pNnzTab[i] | pNnzTab[i + 1];
  uiBS[0][1][0] = BS_EDGE (uiBsx4[0], iRefIdx, pCurMb->sMv, 1, 0);
  uiBS[0][2][0] = BS_EDGE (uiBsx4[1], iRefIdx, pCurMb->sMv, 2, 1);
  uiBS[0][3][0] = BS_EDGE (uiBsx4[2], iRefIdx, pCurMb->sMv, 3, 2);

  for (int i = 0; i < 3; i++)
    uiBsx4[i] = pNnzTab[4 + i] | pNnzTab[4 + i + 1];
  uiBS[0][1][1] = BS_EDGE (uiBsx4[0], iRefIdx, pCurMb->sMv, 5, 4);
  uiBS[0][2][1] = BS_EDGE (uiBsx4[1], iRefIdx, pCurMb->sMv, 6, 5);
  uiBS[0][3][1] = BS_EDGE (uiBsx4[2], iRefIdx, pCurMb->sMv, 7, 6);

  for (int i = 0; i < 3; i++)
    uiBsx4[i] = pNnzTab[8 + i] | pNnzTab[8 + i + 1];
  uiBS[0][1][2] = BS_EDGE (uiBsx4[0], iRefIdx, pCurMb->sMv, 9, 8);
  uiBS[0][2][2] = BS_EDGE (uiBsx4[1], iRefIdx, pCurMb->sMv, 10, 9);
  uiBS[0][3][2] = BS_EDGE (uiBsx4[2], iRefIdx, pCurMb->sMv, 11, 10);

  for (int i = 0; i < 3; i++)
    uiBsx4[i] = pNnzTab[12 + i] | pNnzTab[12 + i + 1];
  uiBS[0][1][3] = BS_EDGE (uiBsx4[0], iRefIdx, pCurMb->sMv, 13, 12);
  uiBS[0][2][3] = BS_EDGE (uiBsx4[1], iRefIdx, pCurMb->sMv, 14, 13);
  uiBS[0][3][3] = BS_EDGE (uiBsx4[2], iRefIdx, pCurMb->sMv, 15, 14);

  //horizontal
  * (uint32_t*)uiBsx4 = (uiNnz32b0 | uiNnz32b1);
  uiBS[1][1][0] = BS_EDGE (uiBsx4[0], iRefIdx, pCurMb->sMv, 4, 0);
  uiBS[1][1][1] = BS_EDGE (uiBsx4[1], iRefIdx, pCurMb->sMv, 5, 1);
  uiBS[1][1][2] = BS_EDGE (uiBsx4[2], iRefIdx, pCurMb->sMv, 6, 2);
  uiBS[1][1][3] = BS_EDGE (uiBsx4[3], iRefIdx, pCurMb->sMv, 7, 3);

  * (uint32_t*)uiBsx4 = (uiNnz32b1 | uiNnz32b2);
  uiBS[1][2][0] = BS_EDGE (uiBsx4[0], iRefIdx, pCurMb->sMv, 8, 4);
  uiBS[1][2][1] = BS_EDGE (uiBsx4[1], iRefIdx, pCurMb->sMv, 9, 5);
  uiBS[1][2][2] = BS_EDGE (uiBsx4[2], iRefIdx, pCurMb->sMv, 10, 6);
  uiBS[1][2][3] = BS_EDGE (uiBsx4[3], iRefIdx, pCurMb->sMv, 11, 7);

  * (uint32_t*)uiBsx4 = (uiNnz32b2 | uiNnz32b3);
  uiBS[1][3][0] = BS_EDGE (uiBsx4[0], iRefIdx, pCurMb->sMv, 12, 8);
  uiBS[1][3][1] = BS_EDGE (uiBsx4[1], iRefIdx, pCurMb->sMv, 13, 9);
  uiBS[1][3][2] = BS_EDGE (uiBsx4[2], iRefIdx, pCurMb->sMv, 14, 10);
  uiBS[1][3][3] = BS_EDGE (uiBsx4[3], iRefIdx, pCurMb->sMv, 15, 11);
}

uint32_t DeblockingBSMarginalMBAvcbase (SMB* pCurMb, SMB* pNeighMb, int32_t iEdge) {
  int32_t i;
  uint32_t uiBSx4;
  uint8_t* pBS = (uint8_t*) (&uiBSx4);
  const uint8_t* pBIdx  = &g_kuiTableBIdx[iEdge][0];
  const uint8_t* pBnIdx = &g_kuiTableBIdx[iEdge][4];


  for (i = 0; i < 4; i++) {
    if (pCurMb->pNonZeroCount[*pBIdx] | pNeighMb->pNonZeroCount[*pBnIdx]) {
      pBS[i] = 2;
    } else {
      pBS[i] =
#ifndef SINGLE_REF_FRAME
        (pCurMb->uiRefIndex[g_kiTableBlock8x8Idx[1][iEdge][i]] - pNeighMb->uiRefIndex[g_kiTableBlock8x8NIdx[1][iEdge][i]]) ||
#endif
        MB_BS_MV (pCurMb->sMv, pNeighMb->sMv, *pBIdx, *pBnIdx);
    }
    pBIdx++;
    pBnIdx++;
  }
  return uiBSx4;
}

void FilteringEdgeLumaH (DeblockingFunc* pfDeblocking, SDeblockingFilter* pFilter, uint8_t* pPix, int32_t iStride,
                         uint8_t* pBS) {
  int32_t iIdexA;
  int32_t iAlpha;
  int32_t iBeta;
  ENFORCE_STACK_ALIGN_1D (int8_t, iTc, 4, 16);

  GET_ALPHA_BETA_FROM_QP (pFilter->uiLumaQP, pFilter->iSliceAlphaC0Offset, pFilter->iSliceBetaOffset, iIdexA, iAlpha,
                          iBeta);

  if (iAlpha | iBeta) {
    TC0_TBL_LOOKUP (iTc, iIdexA, pBS, 0);
    pfDeblocking->pfLumaDeblockingLT4Ver (pPix, iStride, iAlpha, iBeta, iTc);
  }
  return;
}
void FilteringEdgeLumaV (DeblockingFunc* pfDeblocking, SDeblockingFilter* pFilter, uint8_t* pPix, int32_t iStride,
                         uint8_t* pBS) {
  int32_t  iIdexA;
  int32_t  iAlpha;
  int32_t  iBeta;
  ENFORCE_STACK_ALIGN_1D (int8_t, iTc, 4, 16);

  GET_ALPHA_BETA_FROM_QP (pFilter->uiLumaQP, pFilter->iSliceAlphaC0Offset, pFilter->iSliceBetaOffset, iIdexA, iAlpha,
                          iBeta);

  if (iAlpha | iBeta) {
    TC0_TBL_LOOKUP (iTc, iIdexA, pBS, 0);
    pfDeblocking->pfLumaDeblockingLT4Hor (pPix, iStride, iAlpha, iBeta, iTc);
  }
  return;
}

void FilteringEdgeLumaIntraH (DeblockingFunc* pfDeblocking, SDeblockingFilter* pFilter, uint8_t* pPix, int32_t iStride,
                              uint8_t* pBS) {
  int32_t iIdexA;
  int32_t iAlpha;
  int32_t iBeta;

  GET_ALPHA_BETA_FROM_QP (pFilter->uiLumaQP, pFilter->iSliceAlphaC0Offset, pFilter->iSliceBetaOffset, iIdexA, iAlpha,
                          iBeta);

  if (iAlpha | iBeta) {
    pfDeblocking->pfLumaDeblockingEQ4Ver (pPix, iStride, iAlpha, iBeta);
  }
  return;
}

void FilteringEdgeLumaIntraV (DeblockingFunc* pfDeblocking, SDeblockingFilter* pFilter, uint8_t* pPix, int32_t iStride,
                              uint8_t* pBS) {
  int32_t iIdexA;
  int32_t iAlpha;
  int32_t iBeta;

  GET_ALPHA_BETA_FROM_QP (pFilter->uiLumaQP, pFilter->iSliceAlphaC0Offset, pFilter->iSliceBetaOffset, iIdexA, iAlpha,
                          iBeta);

  if (iAlpha | iBeta) {
    pfDeblocking->pfLumaDeblockingEQ4Hor (pPix, iStride, iAlpha, iBeta);
  }
  return;
}
void FilteringEdgeChromaH (DeblockingFunc* pfDeblocking, SDeblockingFilter* pFilter, uint8_t* pPixCb, uint8_t* pPixCr,
                           int32_t iStride, uint8_t* pBS) {
  int32_t iIdexA;
  int32_t iAlpha;
  int32_t iBeta;
  ENFORCE_STACK_ALIGN_1D (int8_t, iTc, 4, 16);

  GET_ALPHA_BETA_FROM_QP (pFilter->uiChromaQP, pFilter->iSliceAlphaC0Offset, pFilter->iSliceBetaOffset, iIdexA, iAlpha,
                          iBeta);

  if (iAlpha | iBeta) {
    TC0_TBL_LOOKUP (iTc, iIdexA, pBS, 1);
    pfDeblocking->pfChromaDeblockingLT4Ver (pPixCb, pPixCr, iStride, iAlpha, iBeta, iTc);
  }
  return;
}
void FilteringEdgeChromaV (DeblockingFunc* pfDeblocking, SDeblockingFilter* pFilter, uint8_t* pPixCb, uint8_t* pPixCr,
                           int32_t iStride, uint8_t* pBS) {
  int32_t iIdexA;
  int32_t iAlpha;
  int32_t iBeta;
  ENFORCE_STACK_ALIGN_1D (int8_t, iTc, 4, 16);

  GET_ALPHA_BETA_FROM_QP (pFilter->uiChromaQP, pFilter->iSliceAlphaC0Offset, pFilter->iSliceBetaOffset, iIdexA, iAlpha,
                          iBeta);

  if (iAlpha | iBeta) {
    TC0_TBL_LOOKUP (iTc, iIdexA, pBS, 1);
    pfDeblocking->pfChromaDeblockingLT4Hor (pPixCb, pPixCr, iStride, iAlpha, iBeta, iTc);
  }
  return;
}

void FilteringEdgeChromaIntraH (DeblockingFunc* pfDeblocking, SDeblockingFilter* pFilter, uint8_t* pPixCb,
                                uint8_t* pPixCr, int32_t iStride, uint8_t* pBS) {
  int32_t iIdexA;
  int32_t iAlpha;
  int32_t iBeta;

  GET_ALPHA_BETA_FROM_QP (pFilter->uiChromaQP, pFilter->iSliceAlphaC0Offset, pFilter->iSliceBetaOffset, iIdexA, iAlpha,
                          iBeta);

  if (iAlpha | iBeta) {
    pfDeblocking->pfChromaDeblockingEQ4Ver (pPixCb, pPixCr, iStride, iAlpha, iBeta);
  }
  return;
}

void FilteringEdgeChromaIntraV (DeblockingFunc* pfDeblocking, SDeblockingFilter* pFilter, uint8_t* pPixCb,
                                uint8_t* pPixCr, int32_t iStride, uint8_t* pBS) {
  int32_t iIdexA;
  int32_t iAlpha;
  int32_t iBeta;

  GET_ALPHA_BETA_FROM_QP (pFilter->uiChromaQP, pFilter->iSliceAlphaC0Offset, pFilter->iSliceBetaOffset, iIdexA, iAlpha,
                          iBeta);

  if (iAlpha | iBeta) {
    pfDeblocking->pfChromaDeblockingEQ4Hor (pPixCb, pPixCr, iStride, iAlpha, iBeta);
  }
  return;
}

void DeblockingInterMb (DeblockingFunc* pfDeblocking, SMB* pCurMb, SDeblockingFilter* pFilter, uint8_t uiBS[2][4][4]) {
  int8_t iCurLumaQp   = pCurMb->uiLumaQp;
  int8_t iCurChromaQp = pCurMb->uiChromaQp;
  int32_t iLineSize     = pFilter->iCsStride[0];
  int32_t iLineSizeUV   = pFilter->iCsStride[1];
  int32_t iMbStride    = pFilter->iMbStride;

  int32_t iMbX = pCurMb->iMbX;
  int32_t iMbY = pCurMb->iMbY;

  bool bLeftBsValid[2] = { (iMbX > 0), ((iMbX > 0)&& (pCurMb->uiSliceIdc == (pCurMb - 1)->uiSliceIdc))};
  bool bTopBsValid[2]  = { (iMbY > 0), ((iMbY > 0)&& (pCurMb->uiSliceIdc == (pCurMb - iMbStride)->uiSliceIdc))};

  int32_t iLeftFlag = bLeftBsValid[pFilter->uiFilterIdc];
  int32_t iTopFlag  = bTopBsValid[pFilter->uiFilterIdc];

  uint8_t* pDestY, *pDestCb, *pDestCr;
  pDestY  = pFilter->pCsData[0];
  pDestCb = pFilter->pCsData[1];
  pDestCr = pFilter->pCsData[2];

  if (iLeftFlag) {
    pFilter->uiLumaQP   = (iCurLumaQp + (pCurMb - 1)->uiLumaQp + 1) >> 1;
    pFilter->uiChromaQP = (iCurChromaQp + (pCurMb - 1)->uiChromaQp + 1) >> 1;

    if (uiBS[0][0][0] == 0x04) {
      FilteringEdgeLumaIntraV (pfDeblocking, pFilter, pDestY, iLineSize , NULL);
      FilteringEdgeChromaIntraV (pfDeblocking, pFilter, pDestCb, pDestCr, iLineSizeUV, NULL);
    } else {
      if (* (uint32_t*)uiBS[0][0] != 0) {
        FilteringEdgeLumaV (pfDeblocking, pFilter, pDestY, iLineSize, uiBS[0][0]);
        FilteringEdgeChromaV (pfDeblocking, pFilter, pDestCb, pDestCr, iLineSizeUV, uiBS[0][0]);
      }
    }
  }

  pFilter->uiLumaQP = iCurLumaQp;
  pFilter->uiChromaQP = iCurChromaQp;

  if (* (uint32_t*)uiBS[0][1] != 0) {
    FilteringEdgeLumaV (pfDeblocking, pFilter, &pDestY[1 << 2], iLineSize, uiBS[0][1]);
  }

  if (* (uint32_t*)uiBS[0][2] != 0) {
    FilteringEdgeLumaV (pfDeblocking, pFilter, &pDestY[2 << 2], iLineSize, uiBS[0][2]);
    FilteringEdgeChromaV (pfDeblocking, pFilter, &pDestCb[2 << 1], &pDestCr[2 << 1], iLineSizeUV, uiBS[0][2]);
  }

  if (* (uint32_t*)uiBS[0][3] != 0) {
    FilteringEdgeLumaV (pfDeblocking, pFilter, &pDestY[3 << 2], iLineSize, uiBS[0][3]);
  }

  if (iTopFlag) {
    pFilter->uiLumaQP = (iCurLumaQp + (pCurMb - iMbStride)->uiLumaQp + 1) >> 1;
    pFilter->uiChromaQP = (iCurChromaQp + (pCurMb - iMbStride)->uiChromaQp + 1) >> 1;

    if (uiBS[1][0][0] == 0x04) {
      FilteringEdgeLumaIntraH (pfDeblocking, pFilter, pDestY, iLineSize , NULL);
      FilteringEdgeChromaIntraH (pfDeblocking, pFilter, pDestCb, pDestCr, iLineSizeUV, NULL);
    } else {
      if (* (uint32_t*)uiBS[1][0] != 0) {
        FilteringEdgeLumaH (pfDeblocking, pFilter, pDestY, iLineSize, uiBS[1][0]);
        FilteringEdgeChromaH (pfDeblocking, pFilter, pDestCb, pDestCr, iLineSizeUV, uiBS[1][0]);
      }
    }
  }

  pFilter->uiLumaQP = iCurLumaQp;
  pFilter->uiChromaQP = iCurChromaQp;

  if (* (uint32_t*)uiBS[1][1] != 0) {
    FilteringEdgeLumaH (pfDeblocking, pFilter, &pDestY[ (1 << 2)*iLineSize], iLineSize, uiBS[1][1]);
  }

  if (* (uint32_t*)uiBS[1][2] != 0) {
    FilteringEdgeLumaH (pfDeblocking, pFilter, &pDestY[ (2 << 2)*iLineSize], iLineSize, uiBS[1][2]);
    FilteringEdgeChromaH (pfDeblocking, pFilter, &pDestCb[ (2 << 1)*iLineSizeUV], &pDestCr[ (2 << 1)*iLineSizeUV],
                          iLineSizeUV, uiBS[1][2]);
  }

  if (* (uint32_t*)uiBS[1][3] != 0) {
    FilteringEdgeLumaH (pfDeblocking, pFilter, &pDestY[ (3 << 2)*iLineSize], iLineSize, uiBS[1][3]);
  }
}

void FilteringEdgeLumaHV (DeblockingFunc* pfDeblocking, SMB* pCurMb, SDeblockingFilter* pFilter) {
  int32_t iLineSize  = pFilter->iCsStride[0];
  int32_t iMbStride = pFilter->iMbStride;

  uint8_t*  pDestY;
  int8_t   iCurQp;
  int32_t  iIdexA, iAlpha, iBeta;

  int32_t iMbX = pCurMb->iMbX;
  int32_t iMbY = pCurMb->iMbY;

  bool bLeftBsValid[2] = { (iMbX > 0), ((iMbX > 0)&& (pCurMb->uiSliceIdc == (pCurMb - 1)->uiSliceIdc))};
  bool bTopBsValid[2]  = { (iMbY > 0), ((iMbY > 0)&& (pCurMb->uiSliceIdc == (pCurMb - iMbStride)->uiSliceIdc))};

  int32_t iLeftFlag = bLeftBsValid[pFilter->uiFilterIdc];
  int32_t iTopFlag  = bTopBsValid[pFilter->uiFilterIdc];

  ENFORCE_STACK_ALIGN_1D (int8_t,  iTc,   4, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, uiBSx4, 4, 4);

  pDestY  = pFilter->pCsData[0];
  iCurQp  = pCurMb->uiLumaQp;

  * (uint32_t*)uiBSx4 = 0x03030303;

  // luma v
  if (iLeftFlag) {
    pFilter->uiLumaQP = (iCurQp + (pCurMb - 1)->uiLumaQp + 1) >> 1;
    FilteringEdgeLumaIntraV (pfDeblocking, pFilter, pDestY, iLineSize, NULL);
  }

  pFilter->uiLumaQP   = iCurQp;
  GET_ALPHA_BETA_FROM_QP (pFilter->uiLumaQP, pFilter->iSliceAlphaC0Offset, pFilter->iSliceBetaOffset, iIdexA, iAlpha,
                          iBeta);
  if (iAlpha | iBeta) {
    TC0_TBL_LOOKUP (iTc, iIdexA, uiBSx4, 0);
    pfDeblocking->pfLumaDeblockingLT4Hor (&pDestY[1 << 2], iLineSize, iAlpha, iBeta, iTc);
    pfDeblocking->pfLumaDeblockingLT4Hor (&pDestY[2 << 2], iLineSize, iAlpha, iBeta, iTc);
    pfDeblocking->pfLumaDeblockingLT4Hor (&pDestY[3 << 2], iLineSize, iAlpha, iBeta, iTc);

  }

  // luma h
  if (iTopFlag) {
    pFilter->uiLumaQP   = (iCurQp   + (pCurMb - iMbStride)->uiLumaQp + 1) >> 1;
    FilteringEdgeLumaIntraH (pfDeblocking, pFilter, pDestY, iLineSize, NULL);
  }

  pFilter->uiLumaQP   = iCurQp;
  if (iAlpha | iBeta) {
    pfDeblocking->pfLumaDeblockingLT4Ver (&pDestY[ (1 << 2)*iLineSize], iLineSize, iAlpha, iBeta, iTc);
    pfDeblocking->pfLumaDeblockingLT4Ver (&pDestY[ (2 << 2)*iLineSize], iLineSize, iAlpha, iBeta, iTc);
    pfDeblocking->pfLumaDeblockingLT4Ver (&pDestY[ (3 << 2)*iLineSize], iLineSize, iAlpha, iBeta, iTc);
  }
}
void FilteringEdgeChromaHV (DeblockingFunc* pfDeblocking, SMB* pCurMb, SDeblockingFilter* pFilter) {
  int32_t iLineSize  = pFilter->iCsStride[1];
  int32_t iMbStride = pFilter->iMbStride;

  uint8_t*  pDestCb, *pDestCr;
  int8_t   iCurQp;
  int32_t  iIdexA, iAlpha, iBeta;

  int32_t iMbX = pCurMb->iMbX;
  int32_t iMbY = pCurMb->iMbY;

  bool bLeftBsValid[2] = { (iMbX > 0), ((iMbX > 0)&& (pCurMb->uiSliceIdc == (pCurMb - 1)->uiSliceIdc))};
  bool bTopBsValid[2]  = { (iMbY > 0), ((iMbY > 0)&& (pCurMb->uiSliceIdc == (pCurMb - iMbStride)->uiSliceIdc))};

  int32_t iLeftFlag = bLeftBsValid[pFilter->uiFilterIdc];
  int32_t iTopFlag  = bTopBsValid[pFilter->uiFilterIdc];

  ENFORCE_STACK_ALIGN_1D (int8_t,  iTc,   4, 16);
  ENFORCE_STACK_ALIGN_1D (uint8_t, uiBSx4, 4, 4);

  pDestCb = pFilter->pCsData[1];
  pDestCr = pFilter->pCsData[2];
  iCurQp  = pCurMb->uiChromaQp;
  * (uint32_t*)uiBSx4 = 0x03030303;

  // chroma v
  if (iLeftFlag) {
    pFilter->uiChromaQP = (iCurQp + (pCurMb - 1)->uiChromaQp + 1) >> 1;
    FilteringEdgeChromaIntraV (pfDeblocking, pFilter, pDestCb, pDestCr, iLineSize, NULL);
  }

  pFilter->uiChromaQP   = iCurQp;
  GET_ALPHA_BETA_FROM_QP (pFilter->uiChromaQP, pFilter->iSliceAlphaC0Offset, pFilter->iSliceBetaOffset, iIdexA, iAlpha,
                          iBeta);
  if (iAlpha | iBeta) {
    TC0_TBL_LOOKUP (iTc, iIdexA, uiBSx4, 1);
    pfDeblocking->pfChromaDeblockingLT4Hor (&pDestCb[2 << 1], &pDestCr[2 << 1], iLineSize, iAlpha, iBeta, iTc);
  }

  // chroma h
  if (iTopFlag) {
    pFilter->uiChromaQP = (iCurQp + (pCurMb - iMbStride)->uiChromaQp + 1) >> 1;
    FilteringEdgeChromaIntraH (pfDeblocking, pFilter, pDestCb, pDestCr, iLineSize, NULL);
  }

  pFilter->uiChromaQP   = iCurQp;
  if (iAlpha | iBeta) {
    pfDeblocking->pfChromaDeblockingLT4Ver (&pDestCb[ (2 << 1)*iLineSize], &pDestCr[ (2 << 1)*iLineSize], iLineSize, iAlpha,
                                            iBeta, iTc);
  }
}

// merge h&v lookup table operation to save performance
void DeblockingIntraMb (DeblockingFunc* pfDeblocking, SMB* pCurMb, SDeblockingFilter* pFilter) {
  FilteringEdgeLumaHV (pfDeblocking, pCurMb, pFilter);
  FilteringEdgeChromaHV (pfDeblocking, pCurMb, pFilter);
}

#if defined(HAVE_NEON) && defined(SINGLE_REF_FRAME)
void DeblockingBSCalc_neon (SWelsFuncPtrList* pFunc, SMB* pCurMb, uint8_t uiBS[2][4][4], Mb_Type uiCurMbType,
                            int32_t iMbStride, int32_t iLeftFlag, int32_t iTopFlag) {
  DeblockingBSCalcEnc_neon (pCurMb->pNonZeroCount, pCurMb->sMv,
                            (iLeftFlag ? LEFT_MB_POS : 0) | (iTopFlag ? TOP_MB_POS : 0), iMbStride, uiBS);
  if (iLeftFlag) {
    if (IS_INTRA ((pCurMb - 1)->uiMbType)) {
      * (uint32_t*)uiBS[0][0] = 0x04040404;
    }
  } else {
    * (uint32_t*)uiBS[0][0] = 0;
  }
  if (iTopFlag) {
    if (IS_INTRA ((pCurMb - iMbStride)->uiMbType)) {
      * (uint32_t*)uiBS[1][0] = 0x04040404;
    }
  } else {
    * (uint32_t*)uiBS[1][0] = 0;
  }
}
#endif

#if defined(HAVE_NEON_AARCH64) && defined(SINGLE_REF_FRAME)
void DeblockingBSCalc_AArch64_neon (SWelsFuncPtrList* pFunc, SMB* pCurMb, uint8_t uiBS[2][4][4], Mb_Type uiCurMbType,
                                    int32_t iMbStride, int32_t iLeftFlag, int32_t iTopFlag) {
  DeblockingBSCalcEnc_AArch64_neon (pCurMb->pNonZeroCount, pCurMb->sMv,
                                    (iLeftFlag ? LEFT_MB_POS : 0) | (iTopFlag ? TOP_MB_POS : 0), iMbStride, uiBS);
  if (iLeftFlag) {
    if (IS_INTRA ((pCurMb - 1)->uiMbType)) {
      * (uint32_t*)uiBS[0][0] = 0x04040404;
    }
  } else {
    * (uint32_t*)uiBS[0][0] = 0;
  }
  if (iTopFlag) {
    if (IS_INTRA ((pCurMb - iMbStride)->uiMbType)) {
      * (uint32_t*)uiBS[1][0] = 0x04040404;
    }
  } else {
    * (uint32_t*)uiBS[1][0] = 0;
  }
}
#endif

void DeblockingBSCalc_c (SWelsFuncPtrList* pFunc, SMB* pCurMb, uint8_t uiBS[2][4][4], Mb_Type uiCurMbType,
                         int32_t iMbStride, int32_t iLeftFlag, int32_t iTopFlag) {
  if (iLeftFlag) {
    * (uint32_t*)uiBS[0][0] = IS_INTRA ((pCurMb - 1)->uiMbType) ? 0x04040404 : DeblockingBSMarginalMBAvcbase (pCurMb,
                              pCurMb - 1, 0);
  } else {
    * (uint32_t*)uiBS[0][0] = 0;
  }
  if (iTopFlag) {
    * (uint32_t*)uiBS[1][0] = IS_INTRA ((pCurMb - iMbStride)->uiMbType) ? 0x04040404 : DeblockingBSMarginalMBAvcbase (
                                pCurMb, (pCurMb - iMbStride), 1);
  } else {
    * (uint32_t*)uiBS[1][0] = 0;
  }
  //SKIP MB_16x16 or others
  if (uiCurMbType != MB_TYPE_SKIP) {
    pFunc->pfSetNZCZero (pCurMb->pNonZeroCount); // set all none-zero nzc to 1; dbk can be opti!

    if (uiCurMbType == MB_TYPE_16x16) {
      DeblockingBSInsideMBAvsbase (pCurMb->pNonZeroCount, uiBS, 1);
    } else {
      DeblockingBSInsideMBNormal (pCurMb, uiBS, pCurMb->pNonZeroCount);
    }
  } else {
    * (uint32_t*)uiBS[0][1] = * (uint32_t*)uiBS[0][2] = * (uint32_t*)uiBS[0][3] =
                                * (uint32_t*)uiBS[1][1] = * (uint32_t*)uiBS[1][2] = * (uint32_t*)uiBS[1][3] = 0;
  }
}

void DeblockingMbAvcbase (SWelsFuncPtrList* pFunc, SMB* pCurMb, SDeblockingFilter* pFilter) {
  uint8_t uiBS[2][4][4] = {{{ 0 }}};

  Mb_Type uiCurMbType = pCurMb->uiMbType;
  int32_t iMbStride  = pFilter->iMbStride;

  int32_t iMbX = pCurMb->iMbX;
  int32_t iMbY = pCurMb->iMbY;

  bool bLeftBsValid[2] = { (iMbX > 0), ((iMbX > 0)&& (pCurMb->uiSliceIdc == (pCurMb - 1)->uiSliceIdc))};
  bool bTopBsValid[2]  = { (iMbY > 0), ((iMbY > 0)&& (pCurMb->uiSliceIdc == (pCurMb - iMbStride)->uiSliceIdc))};

  int32_t iLeftFlag = bLeftBsValid[pFilter->uiFilterIdc];
  int32_t iTopFlag  = bTopBsValid[pFilter->uiFilterIdc];

  switch (uiCurMbType) {
  case MB_TYPE_INTRA4x4:
  case MB_TYPE_INTRA16x16:
  case MB_TYPE_INTRA_PCM:
    DeblockingIntraMb (&pFunc->pfDeblocking, pCurMb, pFilter);
    break;
  default:
    pFunc->pfDeblocking.pfDeblockingBSCalc (pFunc, pCurMb, uiBS, uiCurMbType, iMbStride, iLeftFlag, iTopFlag);
    DeblockingInterMb (&pFunc->pfDeblocking, pCurMb, pFilter, uiBS);
    break;
  }
}

void  DeblockingFilterFrameAvcbase (SDqLayer* pCurDq, SWelsFuncPtrList* pFunc) {
  int32_t i, j;
  const int32_t kiMbWidth   = pCurDq->iMbWidth;
  const int32_t kiMbHeight  = pCurDq->iMbHeight;
  SMB* pCurrentMbBlock      = pCurDq->sMbDataP;
  SSliceHeaderExt* sSliceHeaderExt = &pCurDq->ppSliceInLayer[0]->sSliceHeaderExt;
  SDeblockingFilter pFilter;

  /* Step1: parameters set */
  if (sSliceHeaderExt->sSliceHeader.uiDisableDeblockingFilterIdc == 1)
    return;

  pFilter.uiFilterIdc = (sSliceHeaderExt->sSliceHeader.uiDisableDeblockingFilterIdc != 0);

  pFilter.iCsStride[0] = pCurDq->pDecPic->iLineSize[0];
  pFilter.iCsStride[1] = pCurDq->pDecPic->iLineSize[1];
  pFilter.iCsStride[2] = pCurDq->pDecPic->iLineSize[2];

  pFilter.iSliceAlphaC0Offset = sSliceHeaderExt->sSliceHeader.iSliceAlphaC0Offset;
  pFilter.iSliceBetaOffset     = sSliceHeaderExt->sSliceHeader.iSliceBetaOffset;

  pFilter.iMbStride = kiMbWidth;

  for (j = 0; j < kiMbHeight; ++j) {
    pFilter.pCsData[0] = pCurDq->pDecPic->pData[0] + ((j * pFilter.iCsStride[0]) << 4);
    pFilter.pCsData[1] = pCurDq->pDecPic->pData[1] + ((j * pFilter.iCsStride[1]) << 3);
    pFilter.pCsData[2] = pCurDq->pDecPic->pData[2] + ((j * pFilter.iCsStride[2]) << 3);
    for (i = 0; i < kiMbWidth; i++) {
      DeblockingMbAvcbase (pFunc, pCurrentMbBlock, &pFilter);
      ++pCurrentMbBlock;
      pFilter.pCsData[0] += MB_WIDTH_LUMA;
      pFilter.pCsData[1] += MB_WIDTH_CHROMA;
      pFilter.pCsData[2] += MB_WIDTH_CHROMA;
    }
  }
}

void DeblockingFilterSliceAvcbase (SDqLayer* pCurDq, SWelsFuncPtrList* pFunc, SSlice* pSlice) {
  SMB* pMbList                          = pCurDq->sMbDataP;
  SSliceHeaderExt* sSliceHeaderExt      = &pSlice->sSliceHeaderExt;
  SMB* pCurrentMbBlock;

  const int32_t kiMbWidth               = pCurDq->iMbWidth;
  const int32_t kiMbHeight              = pCurDq->iMbHeight;
  const int32_t kiTotalNumMb            = kiMbWidth * kiMbHeight;
  int32_t iCurMbIdx = 0, iNextMbIdx = 0, iNumMbFiltered = 0;

  /* Step1: parameters set */
  if (sSliceHeaderExt->sSliceHeader.uiDisableDeblockingFilterIdc == 1)
    return;

  SDeblockingFilter pFilter;

  pFilter.uiFilterIdc = (sSliceHeaderExt->sSliceHeader.uiDisableDeblockingFilterIdc != 0);
  pFilter.iCsStride[0] = pCurDq->pDecPic->iLineSize[0];
  pFilter.iCsStride[1] = pCurDq->pDecPic->iLineSize[1];
  pFilter.iCsStride[2] = pCurDq->pDecPic->iLineSize[2];
  pFilter.iSliceAlphaC0Offset = sSliceHeaderExt->sSliceHeader.iSliceAlphaC0Offset;
  pFilter.iSliceBetaOffset    = sSliceHeaderExt->sSliceHeader.iSliceBetaOffset;
  pFilter.iMbStride           = kiMbWidth;

  iNextMbIdx  = sSliceHeaderExt->sSliceHeader.iFirstMbInSlice;

  for (; ;) {
    iCurMbIdx       = iNextMbIdx;
    pCurrentMbBlock = &pMbList[ iCurMbIdx ];

    pFilter.pCsData[0] = pCurDq->pDecPic->pData[0] + ((pCurrentMbBlock->iMbX + pCurrentMbBlock->iMbY * pFilter.iCsStride[0])
                         << 4);
    pFilter.pCsData[1] = pCurDq->pDecPic->pData[1] + ((pCurrentMbBlock->iMbX + pCurrentMbBlock->iMbY * pFilter.iCsStride[1])
                         << 3);
    pFilter.pCsData[2] = pCurDq->pDecPic->pData[2] + ((pCurrentMbBlock->iMbX + pCurrentMbBlock->iMbY * pFilter.iCsStride[2])
                         << 3);

    DeblockingMbAvcbase (pFunc, pCurrentMbBlock, &pFilter);

    ++iNumMbFiltered;
    iNextMbIdx = WelsGetNextMbOfSlice (pCurDq, iCurMbIdx);
    //whether all of MB in current slice filtered or not
    if (iNextMbIdx == -1 || iNextMbIdx >= kiTotalNumMb || iNumMbFiltered >= kiTotalNumMb) {
      break;
    }
  }
}

void DeblockingFilterSliceAvcbaseNull (SDqLayer* pCurDq, SWelsFuncPtrList* pFunc, SSlice* pSlice) {
}

void PerformDeblockingFilter (sWelsEncCtx* pEnc) {
  SDqLayer* pCurLayer = pEnc->pCurDqLayer;
  SSlice* pSlice      = NULL;

  if (pCurLayer->iLoopFilterDisableIdc == 0) {
    DeblockingFilterFrameAvcbase (pCurLayer, pEnc->pFuncList);
  } else if (pCurLayer->iLoopFilterDisableIdc == 2) {
    int32_t iSliceCount = 0;
    int32_t iSliceIdx   = 0;

    iSliceCount = GetCurrentSliceNum (pCurLayer);
    do {
      pSlice = pCurLayer->ppSliceInLayer[iSliceIdx];
      assert (NULL != pSlice);
      DeblockingFilterSliceAvcbase (pCurLayer, pEnc->pFuncList, pSlice);
      ++ iSliceIdx;
    } while (iSliceIdx < iSliceCount);
  }
}

void WelsBlockFuncInit (PSetNoneZeroCountZeroFunc* pfSetNZCZero,  int32_t iCpu) {
  *pfSetNZCZero = WelsNonZeroCount_c;
#ifdef HAVE_NEON
  if (iCpu & WELS_CPU_NEON) {
    *pfSetNZCZero = WelsNonZeroCount_neon;
  }
#endif
#ifdef HAVE_NEON_AARCH64
  if (iCpu & WELS_CPU_NEON) {
    *pfSetNZCZero = WelsNonZeroCount_AArch64_neon;
  }
#endif
#if defined(X86_ASM)
  if (iCpu & WELS_CPU_SSE2) {
    *pfSetNZCZero = WelsNonZeroCount_sse2;
  }
#endif
#if defined(HAVE_MMI)
  if (iCpu & WELS_CPU_MMI) {
    *pfSetNZCZero = WelsNonZeroCount_mmi;
  }
#endif
#if defined(HAVE_MSA)
  if (iCpu & WELS_CPU_MSA) {
    *pfSetNZCZero = WelsNonZeroCount_msa;
  }
#endif
}

void  DeblockingInit (DeblockingFunc*   pFunc,  int32_t iCpu) {
  pFunc->pfLumaDeblockingLT4Ver     = DeblockLumaLt4V_c;
  pFunc->pfLumaDeblockingEQ4Ver     = DeblockLumaEq4V_c;
  pFunc->pfLumaDeblockingLT4Hor     = DeblockLumaLt4H_c;
  pFunc->pfLumaDeblockingEQ4Hor     = DeblockLumaEq4H_c;

  pFunc->pfChromaDeblockingLT4Ver   = DeblockChromaLt4V_c;
  pFunc->pfChromaDeblockingEQ4Ver   = DeblockChromaEq4V_c;
  pFunc->pfChromaDeblockingLT4Hor   = DeblockChromaLt4H_c;
  pFunc->pfChromaDeblockingEQ4Hor   = DeblockChromaEq4H_c;

  pFunc->pfDeblockingBSCalc         = DeblockingBSCalc_c;


#ifdef X86_ASM
  if (iCpu & WELS_CPU_SSSE3) {
    pFunc->pfLumaDeblockingLT4Ver   = DeblockLumaLt4V_ssse3;
    pFunc->pfLumaDeblockingEQ4Ver   = DeblockLumaEq4V_ssse3;
    pFunc->pfLumaDeblockingLT4Hor   = DeblockLumaLt4H_ssse3;
    pFunc->pfLumaDeblockingEQ4Hor   = DeblockLumaEq4H_ssse3;
    pFunc->pfChromaDeblockingLT4Ver = DeblockChromaLt4V_ssse3;
    pFunc->pfChromaDeblockingEQ4Ver = DeblockChromaEq4V_ssse3;
    pFunc->pfChromaDeblockingLT4Hor = DeblockChromaLt4H_ssse3;
    pFunc->pfChromaDeblockingEQ4Hor = DeblockChromaEq4H_ssse3;
  }
#endif

#if defined(HAVE_NEON)
  if (iCpu & WELS_CPU_NEON) {
    pFunc->pfLumaDeblockingLT4Ver   = DeblockLumaLt4V_neon;
    pFunc->pfLumaDeblockingEQ4Ver   = DeblockLumaEq4V_neon;
    pFunc->pfLumaDeblockingLT4Hor   = DeblockLumaLt4H_neon;
    pFunc->pfLumaDeblockingEQ4Hor   = DeblockLumaEq4H_neon;

    pFunc->pfChromaDeblockingLT4Ver = DeblockChromaLt4V_neon;
    pFunc->pfChromaDeblockingEQ4Ver = DeblockChromaEq4V_neon;
    pFunc->pfChromaDeblockingLT4Hor = DeblockChromaLt4H_neon;
    pFunc->pfChromaDeblockingEQ4Hor = DeblockChromaEq4H_neon;

#if defined(SINGLE_REF_FRAME)
    pFunc->pfDeblockingBSCalc       = DeblockingBSCalc_neon;
#endif
  }
#endif

#if defined(HAVE_NEON_AARCH64)
  if (iCpu & WELS_CPU_NEON) {
    pFunc->pfLumaDeblockingLT4Ver   = DeblockLumaLt4V_AArch64_neon;
    pFunc->pfLumaDeblockingEQ4Ver   = DeblockLumaEq4V_AArch64_neon;
    pFunc->pfLumaDeblockingLT4Hor   = DeblockLumaLt4H_AArch64_neon;
    pFunc->pfLumaDeblockingEQ4Hor   = DeblockLumaEq4H_AArch64_neon;

    pFunc->pfChromaDeblockingLT4Ver = DeblockChromaLt4V_AArch64_neon;
    pFunc->pfChromaDeblockingEQ4Ver = DeblockChromaEq4V_AArch64_neon;
    pFunc->pfChromaDeblockingLT4Hor = DeblockChromaLt4H_AArch64_neon;
    pFunc->pfChromaDeblockingEQ4Hor = DeblockChromaEq4H_AArch64_neon;

#if defined(SINGLE_REF_FRAME)
    pFunc->pfDeblockingBSCalc       = DeblockingBSCalc_AArch64_neon;
#endif
  }
#endif

#if defined(HAVE_MMI)
  if (iCpu & WELS_CPU_MMI) {
    pFunc->pfLumaDeblockingLT4Ver   = DeblockLumaLt4V_mmi;
    pFunc->pfLumaDeblockingEQ4Ver   = DeblockLumaEq4V_mmi;
    pFunc->pfLumaDeblockingLT4Hor   = DeblockLumaLt4H_mmi;
    pFunc->pfLumaDeblockingEQ4Hor   = DeblockLumaEq4H_mmi;
    pFunc->pfChromaDeblockingLT4Ver = DeblockChromaLt4V_mmi;
    pFunc->pfChromaDeblockingEQ4Ver = DeblockChromaEq4V_mmi;
    pFunc->pfChromaDeblockingLT4Hor = DeblockChromaLt4H_mmi;
    pFunc->pfChromaDeblockingEQ4Hor = DeblockChromaEq4H_mmi;
  }
#endif//HAVE_MMI

#if defined(HAVE_MSA)
  if (iCpu & WELS_CPU_MSA) {
    pFunc->pfLumaDeblockingLT4Ver   = DeblockLumaLt4V_msa;
    pFunc->pfLumaDeblockingEQ4Ver   = DeblockLumaEq4V_msa;
    pFunc->pfLumaDeblockingLT4Hor   = DeblockLumaLt4H_msa;
    pFunc->pfLumaDeblockingEQ4Hor   = DeblockLumaEq4H_msa;
    pFunc->pfChromaDeblockingLT4Ver = DeblockChromaLt4V_msa;
    pFunc->pfChromaDeblockingEQ4Ver = DeblockChromaEq4V_msa;
    pFunc->pfChromaDeblockingLT4Hor = DeblockChromaLt4H_msa;
    pFunc->pfChromaDeblockingEQ4Hor = DeblockChromaEq4H_msa;
  }
#endif//HAVE_MSA
}


} // namespace WelsEnc

