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
 * \file    svc_set_mb_syn_cabac.cpp
 *
 * \brief   wrtie cabac syntax
 *
 * \date    9/28/2014 Created
 *
 *************************************************************************************
 */
#include "svc_set_mb_syn.h"
#include "set_mb_syn_cabac.h"
#include "svc_enc_golomb.h"

using namespace WelsEnc;

namespace {

static const uint16_t uiSignificantCoeffFlagOffset[5] = {0, 15, 29, 44, 47};
static const uint16_t uiLastCoeffFlagOffset[5] = {0, 15, 29, 44, 47};
static const uint16_t uiCoeffAbsLevelMinus1Offset[5] = {0, 10, 20, 30, 39};
static const uint16_t uiCodecBlockFlagOffset[5] = {0, 4, 8, 12, 16};


static void WelsCabacMbType (SCabacCtx* pCabacCtx, SMB* pCurMb, SMbCache* pMbCache, int32_t iMbWidth,
                             EWelsSliceType eSliceType) {

  if (eSliceType == I_SLICE) {
    uint32_t uiNeighborAvail = pCurMb->uiNeighborAvail;
    SMB* pLeftMb = pCurMb - 1 ;
    SMB* pTopMb = pCurMb - iMbWidth;
    int32_t iCtx = 3;
    if ((uiNeighborAvail & LEFT_MB_POS) && !IS_INTRA4x4 (pLeftMb->uiMbType))
      iCtx++;
    if ((uiNeighborAvail & TOP_MB_POS) && !IS_INTRA4x4 (pTopMb->uiMbType))  //TOP MB
      iCtx++;

    if (pCurMb->uiMbType == MB_TYPE_INTRA4x4) {
      WelsCabacEncodeDecision (pCabacCtx, iCtx, 0);
    } else {
      int32_t iCbpChroma = pCurMb->uiCbp >> 4;
      int32_t iCbpLuma   = pCurMb->uiCbp & 15;
      int32_t iPredMode = g_kiMapModeI16x16[pMbCache->uiLumaI16x16Mode];

      WelsCabacEncodeDecision (pCabacCtx, iCtx, 1);
      WelsCabacEncodeTerminate (pCabacCtx, 0);
      if (iCbpLuma)
        WelsCabacEncodeDecision (pCabacCtx, 6, 1);
      else
        WelsCabacEncodeDecision (pCabacCtx, 6, 0);

      if (iCbpChroma == 0)
        WelsCabacEncodeDecision (pCabacCtx, 7, 0);
      else {
        WelsCabacEncodeDecision (pCabacCtx, 7, 1);
        WelsCabacEncodeDecision (pCabacCtx, 8, iCbpChroma >> 1);
      }
      WelsCabacEncodeDecision (pCabacCtx, 9, iPredMode >> 1);
      WelsCabacEncodeDecision (pCabacCtx, 10, iPredMode & 1);
    }
  } else if (eSliceType == P_SLICE) {
    uint32_t uiMbType = pCurMb->uiMbType;
    if (uiMbType == MB_TYPE_16x16) {
      WelsCabacEncodeDecision (pCabacCtx, 14, 0);
      WelsCabacEncodeDecision (pCabacCtx, 15, 0);
      WelsCabacEncodeDecision (pCabacCtx, 16, 0);
    } else if ((uiMbType == MB_TYPE_16x8) || (uiMbType == MB_TYPE_8x16)) {

      WelsCabacEncodeDecision (pCabacCtx, 14, 0);
      WelsCabacEncodeDecision (pCabacCtx, 15, 1);
      WelsCabacEncodeDecision (pCabacCtx, 17, pCurMb->uiMbType == MB_TYPE_16x8);

    } else if ((uiMbType  == MB_TYPE_8x8) || (uiMbType  == MB_TYPE_8x8_REF0)) {
      WelsCabacEncodeDecision (pCabacCtx, 14, 0);
      WelsCabacEncodeDecision (pCabacCtx, 15, 0);
      WelsCabacEncodeDecision (pCabacCtx, 16, 1);
    } else if (pCurMb->uiMbType == MB_TYPE_INTRA4x4) {
      WelsCabacEncodeDecision (pCabacCtx, 14, 1);
      WelsCabacEncodeDecision (pCabacCtx, 17, 0);
    } else {

      int32_t iCbpChroma = pCurMb->uiCbp >> 4;
      int32_t iCbpLuma   = pCurMb->uiCbp & 15;
      int32_t iPredMode = g_kiMapModeI16x16[pMbCache->uiLumaI16x16Mode];
      //prefix
      WelsCabacEncodeDecision (pCabacCtx, 14, 1);

      //suffix
      WelsCabacEncodeDecision (pCabacCtx, 17, 1);
      WelsCabacEncodeTerminate (pCabacCtx, 0);
      if (iCbpLuma)
        WelsCabacEncodeDecision (pCabacCtx, 18, 1);
      else
        WelsCabacEncodeDecision (pCabacCtx, 18, 0);
      if (iCbpChroma == 0)
        WelsCabacEncodeDecision (pCabacCtx, 19, 0);
      else {
        WelsCabacEncodeDecision (pCabacCtx, 19, 1);
        WelsCabacEncodeDecision (pCabacCtx, 19, iCbpChroma >> 1);
      }
      WelsCabacEncodeDecision (pCabacCtx, 20, iPredMode >> 1);
      WelsCabacEncodeDecision (pCabacCtx, 20, iPredMode & 1);

    }
  }

}
void WelsCabacMbIntra4x4PredMode (SCabacCtx* pCabacCtx, SMbCache* pMbCache) {

  for (int32_t iMode = 0; iMode < 16; iMode++) {

    bool bPredFlag = pMbCache->pPrevIntra4x4PredModeFlag[iMode];
    int8_t iRemMode  = pMbCache->pRemIntra4x4PredModeFlag[iMode];

    if (bPredFlag)
      WelsCabacEncodeDecision (pCabacCtx, 68, 1);
    else {
      WelsCabacEncodeDecision (pCabacCtx, 68, 0);

      WelsCabacEncodeDecision (pCabacCtx, 69, iRemMode & 0x01);
      WelsCabacEncodeDecision (pCabacCtx, 69, (iRemMode >> 1) & 0x01);
      WelsCabacEncodeDecision (pCabacCtx, 69, (iRemMode >> 2));
    }
  }
}

void WelsCabacMbIntraChromaPredMode (SCabacCtx* pCabacCtx, SMB* pCurMb, SMbCache* pMbCache, int32_t iMbWidth) {
  uint32_t uiNeighborAvail = pCurMb->uiNeighborAvail;
  SMB* pLeftMb = pCurMb - 1 ;
  SMB* pTopMb = pCurMb - iMbWidth;

  int32_t iPredMode = g_kiMapModeIntraChroma[pMbCache->uiChmaI8x8Mode];
  int32_t iCtx = 64;
  if ((uiNeighborAvail & LEFT_MB_POS) && g_kiMapModeIntraChroma[pLeftMb->uiChromPredMode] != 0)
    iCtx++;
  if ((uiNeighborAvail & TOP_MB_POS) && g_kiMapModeIntraChroma[pTopMb->uiChromPredMode] != 0)
    iCtx++;

  if (iPredMode == 0) {
    WelsCabacEncodeDecision (pCabacCtx, iCtx, 0);
  } else if (iPredMode == 1) {
    WelsCabacEncodeDecision (pCabacCtx, iCtx, 1);
    WelsCabacEncodeDecision (pCabacCtx, 67, 0);
  } else if (iPredMode == 2) {
    WelsCabacEncodeDecision (pCabacCtx, iCtx, 1);
    WelsCabacEncodeDecision (pCabacCtx, 67, 1);
    WelsCabacEncodeDecision (pCabacCtx, 67, 0);
  } else {
    WelsCabacEncodeDecision (pCabacCtx, iCtx, 1);
    WelsCabacEncodeDecision (pCabacCtx, 67, 1);
    WelsCabacEncodeDecision (pCabacCtx, 67, 1);
  }
}

void WelsCabacMbCbp (SMB* pCurMb, int32_t iMbWidth, SCabacCtx* pCabacCtx) {
  int32_t iCbpBlockLuma[4] = { (pCurMb->uiCbp) & 1, (pCurMb->uiCbp >> 1) & 1, (pCurMb->uiCbp >> 2) & 1, (pCurMb->uiCbp >> 3) & 1};
  int32_t iCbpChroma = pCurMb->uiCbp >> 4;
  int32_t iCbpBlockLeft[4] = {0, 0, 0, 0};
  int32_t iCbpBlockTop[4] = {0, 0, 0, 0};
  int32_t iCbpLeftChroma  = 0;
  int32_t iCbpTopChroma = 0;
  int32_t iCbp = 0;
  int32_t iCtx = 0;
  uint32_t uiNeighborAvail = pCurMb->uiNeighborAvail;
  if (uiNeighborAvail & LEFT_MB_POS) {
    iCbp = (pCurMb - 1)->uiCbp;
    iCbpBlockLeft[0] = ! (iCbp & 1);
    iCbpBlockLeft[1] = ! ((iCbp >> 1) & 1);
    iCbpBlockLeft[2] = ! ((iCbp >> 2) & 1);
    iCbpBlockLeft[3] = ! ((iCbp >> 3) & 1);
    iCbpLeftChroma = iCbp >> 4;
    if (iCbpLeftChroma)
      iCtx += 1;
  }
  if (uiNeighborAvail & TOP_MB_POS) {
    iCbp = (pCurMb - iMbWidth)->uiCbp;
    iCbpBlockTop[0] = ! (iCbp & 1);
    iCbpBlockTop[1] = ! ((iCbp >> 1) & 1);
    iCbpBlockTop[2] = ! ((iCbp >> 2) & 1);
    iCbpBlockTop[3] = ! ((iCbp >> 3) & 1);
    iCbpTopChroma = iCbp >> 4;
    if (iCbpTopChroma)
      iCtx += 2;
  }
  WelsCabacEncodeDecision (pCabacCtx, 73 + iCbpBlockLeft[1] + iCbpBlockTop[2] * 2, iCbpBlockLuma[0]);
  WelsCabacEncodeDecision (pCabacCtx, 73 + !iCbpBlockLuma[0] + iCbpBlockTop[3] * 2, iCbpBlockLuma[1]);
  WelsCabacEncodeDecision (pCabacCtx, 73 + iCbpBlockLeft[3] + (!iCbpBlockLuma[0]) * 2 , iCbpBlockLuma[2]);
  WelsCabacEncodeDecision (pCabacCtx, 73 + !iCbpBlockLuma[2] + (!iCbpBlockLuma[1]) * 2, iCbpBlockLuma[3]);


  //chroma
  if (iCbpChroma) {
    WelsCabacEncodeDecision (pCabacCtx, 77 + iCtx, 1);
    WelsCabacEncodeDecision (pCabacCtx, 81 + (iCbpLeftChroma >> 1) + ((iCbpTopChroma >> 1) * 2), iCbpChroma > 1);
  } else {
    WelsCabacEncodeDecision (pCabacCtx, 77 + iCtx, 0);
  }
}

void WelsCabacMbDeltaQp (SMB* pCurMb, SCabacCtx* pCabacCtx, bool bFirstMbInSlice) {
  SMB* pPrevMb = NULL;
  int32_t iCtx = 0;

  if (!bFirstMbInSlice) {
    pPrevMb = pCurMb - 1;
    pCurMb->iLumaDQp = pCurMb->uiLumaQp - pPrevMb->uiLumaQp;

    if (IS_SKIP (pPrevMb->uiMbType) || ((pPrevMb->uiMbType != MB_TYPE_INTRA16x16) && (!pPrevMb->uiCbp))
        || (!pPrevMb->iLumaDQp))
      iCtx = 0;
    else
      iCtx = 1;
  }

  if (pCurMb->iLumaDQp) {
    int32_t iValue = pCurMb->iLumaDQp < 0 ? (-2 * pCurMb->iLumaDQp) : (2 * pCurMb->iLumaDQp - 1);
    WelsCabacEncodeDecision (pCabacCtx, 60 + iCtx, 1);
    if (iValue == 1) {
      WelsCabacEncodeDecision (pCabacCtx, 60 + 2, 0);
    } else {
      WelsCabacEncodeDecision (pCabacCtx, 60 + 2, 1);
      iValue--;
      while ((--iValue) > 0)
        WelsCabacEncodeDecision (pCabacCtx, 60 + 3, 1);
      WelsCabacEncodeDecision (pCabacCtx, 60 + 3, 0);
    }
  } else {
    WelsCabacEncodeDecision (pCabacCtx, 60 + iCtx, 0);
  }
}

void WelsMbSkipCabac (SCabacCtx* pCabacCtx, SMB* pCurMb, int32_t iMbWidth, EWelsSliceType eSliceType,
                      int16_t bSkipFlag) {
  int32_t iCtx = (eSliceType == P_SLICE) ? 11 : 24;
  uint32_t uiNeighborAvail = pCurMb->uiNeighborAvail;
  if (uiNeighborAvail & LEFT_MB_POS) { //LEFT MB
    if (!IS_SKIP ((pCurMb - 1)->uiMbType))
      iCtx++;
  }
  if (uiNeighborAvail & TOP_MB_POS) { //TOP MB
    if (!IS_SKIP ((pCurMb - iMbWidth)->uiMbType))
      iCtx++;
  }
  WelsCabacEncodeDecision (pCabacCtx, iCtx, bSkipFlag);

  if (bSkipFlag) {
    for (int  i = 0; i < 16; i++) {
      pCurMb->sMvd[i].iMvX = 0;
      pCurMb->sMvd[i].iMvY = 0;
    }
    pCurMb->uiCbp = pCurMb->iCbpDc  = 0;
  }
}

void WelsCabacMbRef (SCabacCtx* pCabacCtx, SMB* pCurMb, SMbCache* pMbCache, int16_t iIdx) {
  SMVComponentUnit* pMvComp = &pMbCache->sMvComponents;
  const int16_t iRefIdxA = pMvComp->iRefIndexCache[iIdx + 6];
  const int16_t iRefIdxB = pMvComp->iRefIndexCache[iIdx + 1];
  int16_t iRefIdx  = pMvComp->iRefIndexCache[iIdx + 7];
  int16_t iCtx  = 0;

  if ((iRefIdxA > 0) && (!pMbCache->bMbTypeSkip[3]))
    iCtx++;
  if ((iRefIdxB > 0) && (!pMbCache->bMbTypeSkip[1]))
    iCtx += 2;

  while (iRefIdx > 0) {
    WelsCabacEncodeDecision (pCabacCtx, 54 + iCtx, 1);
    iCtx = (iCtx >> 2) + 4;
    iRefIdx--;
  }
  WelsCabacEncodeDecision (pCabacCtx, 54 + iCtx, 0);
}

inline void WelsCabacMbMvdLx (SCabacCtx* pCabacCtx, int32_t sMvd, int32_t iCtx, int32_t iPredMvd) {
  const int32_t iAbsMvd = WELS_ABS (sMvd);
  int32_t iCtxInc = 0;
  int32_t iPrefix = WELS_MIN (iAbsMvd, 9);
  int32_t i = 0;

  if (iPredMvd > 32)
    iCtxInc += 2;
  else if (iPredMvd > 2)
    iCtxInc += 1;

  if (iPrefix) {
    if (iPrefix < 9) {
      WelsCabacEncodeDecision (pCabacCtx, iCtx + iCtxInc, 1);
      iCtxInc = 3;
      for (i = 0; i < iPrefix - 1; i++) {
        WelsCabacEncodeDecision (pCabacCtx, iCtx + iCtxInc, 1);
        if (i < 3)
          iCtxInc++;
      }
      WelsCabacEncodeDecision (pCabacCtx, iCtx + iCtxInc, 0);
      WelsCabacEncodeBypassOne (pCabacCtx, sMvd < 0);
    } else {
      WelsCabacEncodeDecision (pCabacCtx, iCtx + iCtxInc, 1);
      iCtxInc = 3;
      for (i = 0; i < (9 - 1); i++) {
        WelsCabacEncodeDecision (pCabacCtx, iCtx + iCtxInc, 1);
        if (i < 3)
          iCtxInc++;
      }
      WelsCabacEncodeUeBypass (pCabacCtx, 3, iAbsMvd - 9);
      WelsCabacEncodeBypassOne (pCabacCtx, sMvd < 0);
    }
  } else {
    WelsCabacEncodeDecision (pCabacCtx, iCtx + iCtxInc, 0);
  }
}
SMVUnitXY WelsCabacMbMvd (SCabacCtx* pCabacCtx, SMB* pCurMb, uint32_t iMbWidth,
                          SMVUnitXY sCurMv, SMVUnitXY sPredMv, int16_t i4x4ScanIdx) {
  uint32_t iAbsMvd0, iAbsMvd1;
  uint8_t uiNeighborAvail = pCurMb->uiNeighborAvail;
  SMVUnitXY sMvd;
  SMVUnitXY sMvdLeft;
  SMVUnitXY sMvdTop;

  sMvdLeft.iMvX = sMvdLeft.iMvY = sMvdTop.iMvX = sMvdTop.iMvY = 0;
  sMvd.sDeltaMv (sCurMv, sPredMv);
  if ((i4x4ScanIdx < 4) && (uiNeighborAvail & TOP_MB_POS)) { //top row blocks
    sMvdTop.sAssignMv ((pCurMb - iMbWidth)->sMvd[i4x4ScanIdx + 12]);
  } else if (i4x4ScanIdx >= 4) {
    sMvdTop.sAssignMv (pCurMb->sMvd[i4x4ScanIdx - 4]);
  }
  if ((! (i4x4ScanIdx & 0x03)) && (uiNeighborAvail & LEFT_MB_POS)) { //left column blocks
    sMvdLeft.sAssignMv ((pCurMb - 1)->sMvd[i4x4ScanIdx + 3]);
  } else if (i4x4ScanIdx & 0x03) {
    sMvdLeft.sAssignMv (pCurMb->sMvd[i4x4ScanIdx - 1]);
  }

  iAbsMvd0 = WELS_ABS (sMvdLeft.iMvX) + WELS_ABS (sMvdTop.iMvX);
  iAbsMvd1 = WELS_ABS (sMvdLeft.iMvY) + WELS_ABS (sMvdTop.iMvY);

  WelsCabacMbMvdLx (pCabacCtx, sMvd.iMvX, 40, iAbsMvd0);
  WelsCabacMbMvdLx (pCabacCtx, sMvd.iMvY, 47, iAbsMvd1);
  return sMvd;
}
static void WelsCabacSubMbType (SCabacCtx* pCabacCtx, SMB* pCurMb) {
  for (int32_t i8x8Idx = 0; i8x8Idx < 4; ++i8x8Idx) {
    uint32_t uiSubMbType = pCurMb->uiSubMbType[i8x8Idx];
    if (SUB_MB_TYPE_8x8 == uiSubMbType) {
      WelsCabacEncodeDecision (pCabacCtx, 21, 1);
      continue;
    }
    WelsCabacEncodeDecision (pCabacCtx, 21, 0);
    if (SUB_MB_TYPE_8x4 == uiSubMbType) {
      WelsCabacEncodeDecision (pCabacCtx, 22, 0);
    } else {
      WelsCabacEncodeDecision (pCabacCtx, 22, 1);
      WelsCabacEncodeDecision (pCabacCtx, 23, SUB_MB_TYPE_4x8 == uiSubMbType);
    }
  } //for
}

static void WelsCabacSubMbMvd (SCabacCtx* pCabacCtx, SMB* pCurMb, SMbCache* pMbCache, const int kiMbWidth) {
  SMVUnitXY sMvd;
  int32_t i8x8Idx, i4x4ScanIdx;
  for (i8x8Idx = 0; i8x8Idx < 4; ++i8x8Idx) {
    uint32_t uiSubMbType = pCurMb->uiSubMbType[i8x8Idx];
    if (SUB_MB_TYPE_8x8 == uiSubMbType) {
      i4x4ScanIdx = g_kuiMbCountScan4Idx[i8x8Idx << 2];
      sMvd = WelsCabacMbMvd (pCabacCtx, pCurMb, kiMbWidth, pCurMb->sMv[i4x4ScanIdx], pMbCache->sMbMvp[i4x4ScanIdx],
                             i4x4ScanIdx);
      pCurMb->sMvd[    i4x4ScanIdx].sAssignMv (sMvd);
      pCurMb->sMvd[1 + i4x4ScanIdx].sAssignMv (sMvd);
      pCurMb->sMvd[4 + i4x4ScanIdx].sAssignMv (sMvd);
      pCurMb->sMvd[5 + i4x4ScanIdx].sAssignMv (sMvd);
    } else if (SUB_MB_TYPE_4x4 == uiSubMbType) {
      for (int32_t i4x4Idx = 0; i4x4Idx < 4; ++i4x4Idx) {
        i4x4ScanIdx = g_kuiMbCountScan4Idx[ (i8x8Idx << 2) + i4x4Idx];
        sMvd = WelsCabacMbMvd (pCabacCtx, pCurMb, kiMbWidth, pCurMb->sMv[i4x4ScanIdx], pMbCache->sMbMvp[i4x4ScanIdx],
                               i4x4ScanIdx);
        pCurMb->sMvd[i4x4ScanIdx].sAssignMv (sMvd);
      }
    } else if (SUB_MB_TYPE_8x4 == uiSubMbType) {
      for (int32_t i8x4Idx = 0; i8x4Idx < 2; ++i8x4Idx) {
        i4x4ScanIdx = g_kuiMbCountScan4Idx[ (i8x8Idx << 2) + (i8x4Idx << 1)];
        sMvd = WelsCabacMbMvd (pCabacCtx, pCurMb, kiMbWidth, pCurMb->sMv[i4x4ScanIdx], pMbCache->sMbMvp[i4x4ScanIdx],
                               i4x4ScanIdx);
        pCurMb->sMvd[    i4x4ScanIdx].sAssignMv (sMvd);
        pCurMb->sMvd[1 + i4x4ScanIdx].sAssignMv (sMvd);
      }
    } else if (SUB_MB_TYPE_4x8 == uiSubMbType) {
      for (int32_t i4x8Idx = 0; i4x8Idx < 2; ++i4x8Idx) {
        i4x4ScanIdx = g_kuiMbCountScan4Idx[ (i8x8Idx << 2) + i4x8Idx];
        sMvd = WelsCabacMbMvd (pCabacCtx, pCurMb, kiMbWidth, pCurMb->sMv[i4x4ScanIdx], pMbCache->sMbMvp[i4x4ScanIdx],
                               i4x4ScanIdx);
        pCurMb->sMvd[    i4x4ScanIdx].sAssignMv (sMvd);
        pCurMb->sMvd[4 + i4x4ScanIdx].sAssignMv (sMvd);
      }
    }
  }
}

int16_t WelsGetMbCtxCabac (SMbCache* pMbCache, SMB* pCurMb, uint32_t iMbWidth, ECtxBlockCat eCtxBlockCat,
                           int16_t iIdx) {
  int16_t iNzA = -1, iNzB = -1;
  int8_t* pNonZeroCoeffCount = pMbCache->iNonZeroCoeffCount;
  int32_t bIntra = IS_INTRA (pCurMb->uiMbType);
  int32_t iCtxInc = 0;
  switch (eCtxBlockCat) {
  case LUMA_AC:
  case CHROMA_AC:
  case LUMA_4x4:
    iNzA = pNonZeroCoeffCount[iIdx - 1];
    iNzB = pNonZeroCoeffCount[iIdx - 8];
    break;
  case LUMA_DC:
  case CHROMA_DC:
    if (pCurMb->uiNeighborAvail & LEFT_MB_POS)
      iNzA = (pCurMb - 1)->iCbpDc & (1 << iIdx);
    if (pCurMb->uiNeighborAvail & TOP_MB_POS)
      iNzB = (pCurMb - iMbWidth)->iCbpDc & (1 << iIdx);
    break;
  default:
    break;
  }
  if (((iNzA == -1) && bIntra) || (iNzA > 0))
    iCtxInc += 1;
  if (((iNzB == -1) && bIntra) || (iNzB > 0))
    iCtxInc += 2;
  return 85 + uiCodecBlockFlagOffset[eCtxBlockCat] + iCtxInc;
}

void  WelsWriteBlockResidualCabac (SMbCache* pMbCache, SMB* pCurMb, uint32_t iMbWidth, SCabacCtx* pCabacCtx,
                                   ECtxBlockCat eCtxBlockCat, int16_t  iIdx, int16_t iNonZeroCount, int16_t* pBlock, int16_t iEndIdx) {
  int32_t iCtx = WelsGetMbCtxCabac (pMbCache, pCurMb, iMbWidth, eCtxBlockCat, iIdx);
  if (iNonZeroCount) {
    int16_t iLevel[16];
    const int32_t iCtxSig = 105 + uiSignificantCoeffFlagOffset[eCtxBlockCat];
    const int32_t iCtxLast = 166 + uiLastCoeffFlagOffset[eCtxBlockCat];
    const int32_t iCtxLevel = 227 + uiCoeffAbsLevelMinus1Offset[eCtxBlockCat];
    int32_t iNonZeroIdx = 0;
    int32_t i = 0;

    WelsCabacEncodeDecision (pCabacCtx, iCtx, 1);
    while (1) {
      if (pBlock[i]) {
        iLevel[iNonZeroIdx] = pBlock[i];

        iNonZeroIdx++;
        WelsCabacEncodeDecision (pCabacCtx, iCtxSig + i, 1);
        if (iNonZeroIdx != iNonZeroCount)
          WelsCabacEncodeDecision (pCabacCtx, iCtxLast + i, 0);
        else {
          WelsCabacEncodeDecision (pCabacCtx, iCtxLast + i, 1);
          break;
        }
      } else
        WelsCabacEncodeDecision (pCabacCtx, iCtxSig + i, 0);
      i++;
      if (i == iEndIdx) {
        iLevel[iNonZeroIdx] = pBlock[i];
        iNonZeroIdx++;
        break;
      }
    }

    int32_t iNumAbsLevelGt1 = 0;
    int32_t iCtx1 = iCtxLevel + 1;

    do {
      int32_t iPrefix = 0;
      iNonZeroIdx--;
      iPrefix = WELS_ABS (iLevel[iNonZeroIdx]) - 1;
      if (iPrefix) {
        iPrefix = WELS_MIN (iPrefix, 14);
        iCtx = WELS_MIN (iCtxLevel + 4, iCtx1);
        WelsCabacEncodeDecision (pCabacCtx, iCtx, 1);
        iNumAbsLevelGt1++;
        iCtx = iCtxLevel + 4 + WELS_MIN (5 - (eCtxBlockCat == CHROMA_DC), iNumAbsLevelGt1);
        for (i = 1; i < iPrefix; i++)
          WelsCabacEncodeDecision (pCabacCtx, iCtx, 1);
        if (WELS_ABS (iLevel[iNonZeroIdx]) < 15)
          WelsCabacEncodeDecision (pCabacCtx, iCtx, 0);
        else
          WelsCabacEncodeUeBypass (pCabacCtx, 0, WELS_ABS (iLevel[iNonZeroIdx]) - 15);
        iCtx1 = iCtxLevel;
      } else {
        iCtx = WELS_MIN (iCtxLevel + 4, iCtx1);
        WelsCabacEncodeDecision (pCabacCtx, iCtx, 0);
        iCtx1 += iNumAbsLevelGt1 == 0;
      }
      WelsCabacEncodeBypassOne (pCabacCtx, iLevel[iNonZeroIdx] < 0);
    } while (iNonZeroIdx > 0);

  } else {
    WelsCabacEncodeDecision (pCabacCtx, iCtx, 0);
  }


}
int32_t WelsCalNonZeroCount2x2Block (int16_t* pBlock) {
  return (pBlock[0] != 0)
         + (pBlock[1] != 0)
         + (pBlock[2] != 0)
         + (pBlock[3] != 0);
}
int32_t WelsWriteMbResidualCabac (SWelsFuncPtrList* pFuncList, SSlice* pSlice, SMbCache* sMbCacheInfo, SMB* pCurMb,
                                  SCabacCtx* pCabacCtx,
                                  int16_t iMbWidth, uint32_t uiChromaQpIndexOffset) {

  const uint16_t uiMbType = pCurMb->uiMbType;
  SMbCache* pMbCache = &pSlice->sMbCacheInfo;
  int16_t i = 0;
  int8_t* pNonZeroCoeffCount = pMbCache->iNonZeroCoeffCount;
  SSliceHeaderExt* pSliceHeadExt = &pSlice->sSliceHeaderExt;
  const int32_t iSliceFirstMbXY = pSliceHeadExt->sSliceHeader.iFirstMbInSlice;


  pCurMb->iCbpDc = 0;
  pCurMb->iLumaDQp = 0;

  if ((pCurMb->uiCbp > 0) || (uiMbType == MB_TYPE_INTRA16x16)) {
    int32_t iCbpChroma = pCurMb->uiCbp >> 4;
    int32_t iCbpLuma   = pCurMb->uiCbp & 15;

    pCurMb->iLumaDQp = pCurMb->uiLumaQp - pSlice->uiLastMbQp;
    WelsCabacMbDeltaQp (pCurMb, pCabacCtx, (pCurMb->iMbXY == iSliceFirstMbXY));
    pSlice->uiLastMbQp = pCurMb->uiLumaQp;

    if (uiMbType == MB_TYPE_INTRA16x16) {
      //Luma DC
      int iNonZeroCount = pFuncList->pfGetNoneZeroCount (pMbCache->pDct->iLumaI16x16Dc);
      WelsWriteBlockResidualCabac (pMbCache, pCurMb, iMbWidth, pCabacCtx, LUMA_DC, 0, iNonZeroCount,
                                   pMbCache->pDct->iLumaI16x16Dc, 15);
      if (iNonZeroCount)
        pCurMb->iCbpDc |= 1;
      //Luma AC

      if (iCbpLuma) {
        for (i = 0; i < 16; i++) {
          int32_t iIdx = g_kuiCache48CountScan4Idx[i];
          WelsWriteBlockResidualCabac (pMbCache, pCurMb, iMbWidth, pCabacCtx, LUMA_AC, iIdx,
                                       pNonZeroCoeffCount[iIdx], pMbCache->pDct->iLumaBlock[i], 14);
        }
      }
    } else {
      //Luma AC
      for (i = 0; i < 16; i++) {
        if (iCbpLuma & (1 << (i >> 2))) {
          int32_t iIdx = g_kuiCache48CountScan4Idx[i];
          WelsWriteBlockResidualCabac (pMbCache, pCurMb, iMbWidth, pCabacCtx, LUMA_4x4, iIdx,
                                       pNonZeroCoeffCount[iIdx], pMbCache->pDct->iLumaBlock[i], 15);
        }

      }
    }

    if (iCbpChroma) {
      int32_t iNonZeroCount = 0;
      //chroma DC
      iNonZeroCount = WelsCalNonZeroCount2x2Block (pMbCache->pDct->iChromaDc[0]);
      if (iNonZeroCount)
        pCurMb->iCbpDc |= 0x2;
      WelsWriteBlockResidualCabac (pMbCache, pCurMb, iMbWidth, pCabacCtx, CHROMA_DC, 1, iNonZeroCount,
                                   pMbCache->pDct->iChromaDc[0], 3);

      iNonZeroCount = WelsCalNonZeroCount2x2Block (pMbCache->pDct->iChromaDc[1]);
      if (iNonZeroCount)
        pCurMb->iCbpDc |= 0x4;
      WelsWriteBlockResidualCabac (pMbCache, pCurMb, iMbWidth, pCabacCtx, CHROMA_DC, 2, iNonZeroCount,
                                   pMbCache->pDct->iChromaDc[1], 3);
      if (iCbpChroma & 0x02) {
        const uint8_t* g_kuiCache48CountScan4Idx_16base = &g_kuiCache48CountScan4Idx[16];
        //Cb AC
        for (i = 0; i < 4; i++) {
          int32_t iIdx = g_kuiCache48CountScan4Idx_16base[i];
          WelsWriteBlockResidualCabac (pMbCache, pCurMb, iMbWidth, pCabacCtx, CHROMA_AC, iIdx,
                                       pNonZeroCoeffCount[iIdx], pMbCache->pDct->iChromaBlock[i], 14);

        }

        //Cr AC

        for (i = 0; i < 4; i++) {
          int32_t iIdx = 24 + g_kuiCache48CountScan4Idx_16base[i];
          WelsWriteBlockResidualCabac (pMbCache, pCurMb, iMbWidth, pCabacCtx, CHROMA_AC, iIdx,
                                       pNonZeroCoeffCount[iIdx], pMbCache->pDct->iChromaBlock[4 + i], 14);
        }
      }
    }
  } else {
    pCurMb->iLumaDQp = 0;
    pCurMb->uiLumaQp = pSlice->uiLastMbQp;
    pCurMb->uiChromaQp = g_kuiChromaQpTable[CLIP3_QP_0_51 (pCurMb->uiLumaQp + uiChromaQpIndexOffset)];
  }
  return 0;
}

} // anon ns.

namespace WelsEnc {

void WelsInitSliceCabac (sWelsEncCtx* pEncCtx, SSlice* pSlice) {
  /* alignment needed */
  SBitStringAux* pBs = pSlice->pSliceBsa;
  BsAlign (pBs);

  /* init cabac */
  WelsCabacContextInit (pEncCtx, &pSlice->sCabacCtx, pSlice->iCabacInitIdc);
  WelsCabacEncodeInit (&pSlice->sCabacCtx, pBs->pCurBuf, pBs->pEndBuf);
}

int32_t WelsSpatialWriteMbSynCabac (sWelsEncCtx* pEncCtx, SSlice* pSlice, SMB* pCurMb) {
  SCabacCtx* pCabacCtx = &pSlice->sCabacCtx;
  SMbCache* pMbCache = &pSlice->sMbCacheInfo;
  const uint16_t uiMbType = pCurMb->uiMbType;
  SSliceHeaderExt* pSliceHeadExt = &pSlice->sSliceHeaderExt;
  uint32_t uiNumRefIdxL0Active = pSliceHeadExt->sSliceHeader.uiNumRefIdxL0Active - 1;
  const int32_t iSliceFirstMbXY = pSliceHeadExt->sSliceHeader.iFirstMbInSlice;
  int16_t i = 0;
  int16_t iMbWidth = pEncCtx->pCurDqLayer->iMbWidth;
  uint32_t uiChromaQpIndexOffset = pEncCtx->pCurDqLayer->sLayerInfo.pPpsP->uiChromaQpIndexOffset;
  SMVUnitXY sMvd;
  int32_t iRet = 0;
  if (pCurMb->iMbXY > iSliceFirstMbXY)
    WelsCabacEncodeTerminate (&pSlice->sCabacCtx, 0);

  if (IS_SKIP (pCurMb->uiMbType)) {
    pCurMb->uiLumaQp = pSlice->uiLastMbQp;
    pCurMb->uiChromaQp = g_kuiChromaQpTable[CLIP3_QP_0_51 (pCurMb->uiLumaQp + uiChromaQpIndexOffset)];
    WelsMbSkipCabac (&pSlice->sCabacCtx, pCurMb, iMbWidth, pEncCtx->eSliceType, 1);

  } else {
    //skip flag
    if (pEncCtx->eSliceType != I_SLICE)
      WelsMbSkipCabac (&pSlice->sCabacCtx, pCurMb, iMbWidth, pEncCtx->eSliceType, 0);

    //write mb type
    WelsCabacMbType (pCabacCtx, pCurMb, pMbCache, iMbWidth, pEncCtx->eSliceType);

    if (IS_INTRA (uiMbType)) {
      if (uiMbType == MB_TYPE_INTRA4x4) {
        WelsCabacMbIntra4x4PredMode (pCabacCtx, pMbCache);
      }
      WelsCabacMbIntraChromaPredMode (pCabacCtx, pCurMb, pMbCache, iMbWidth);
      sMvd.iMvX = sMvd.iMvY = 0;
      for (i = 0; i < 16; ++i) {
        pCurMb->sMvd[i].sAssignMv (sMvd);
      }

    } else if (uiMbType == MB_TYPE_16x16) {

      if (uiNumRefIdxL0Active > 0) {
        WelsCabacMbRef (pCabacCtx, pCurMb, pMbCache, 0);
      }
      sMvd = WelsCabacMbMvd (pCabacCtx, pCurMb, iMbWidth, pCurMb->sMv[0], pMbCache->sMbMvp[0], 0);

      for (i = 0; i < 16; ++i) {
        pCurMb->sMvd[i].sAssignMv (sMvd);
      }

    } else if (uiMbType == MB_TYPE_16x8) {
      if (uiNumRefIdxL0Active > 0) {
        WelsCabacMbRef (pCabacCtx, pCurMb, pMbCache, 0);
        WelsCabacMbRef (pCabacCtx, pCurMb, pMbCache, 12);
      }
      sMvd = WelsCabacMbMvd (pCabacCtx, pCurMb, iMbWidth , pCurMb->sMv[0], pMbCache->sMbMvp[0], 0);
      for (i = 0; i < 8; ++i) {
        pCurMb->sMvd[i].sAssignMv (sMvd);
      }
      sMvd = WelsCabacMbMvd (pCabacCtx, pCurMb, iMbWidth, pCurMb->sMv[8], pMbCache->sMbMvp[1], 8);
      for (i = 8; i < 16; ++i) {
        pCurMb->sMvd[i].sAssignMv (sMvd);
      }
    } else  if (uiMbType == MB_TYPE_8x16) {
      if (uiNumRefIdxL0Active > 0) {
        WelsCabacMbRef (pCabacCtx, pCurMb, pMbCache, 0);
        WelsCabacMbRef (pCabacCtx, pCurMb, pMbCache, 2);
      }
      sMvd = WelsCabacMbMvd (pCabacCtx, pCurMb, iMbWidth, pCurMb->sMv[0], pMbCache->sMbMvp[0], 0);
      for (i = 0; i < 16; i += 4) {
        pCurMb->sMvd[i    ].sAssignMv (sMvd);
        pCurMb->sMvd[i + 1].sAssignMv (sMvd);
      }
      sMvd = WelsCabacMbMvd (pCabacCtx, pCurMb, iMbWidth,  pCurMb->sMv[2], pMbCache->sMbMvp[1], 2);
      for (i = 0; i < 16; i += 4) {
        pCurMb->sMvd[i + 2].sAssignMv (sMvd);
        pCurMb->sMvd[i + 3].sAssignMv (sMvd);
      }
    } else if ((uiMbType == MB_TYPE_8x8) || (uiMbType == MB_TYPE_8x8_REF0)) {
      //write sub_mb_type
      WelsCabacSubMbType (pCabacCtx, pCurMb);

      if (uiNumRefIdxL0Active > 0) {
        WelsCabacMbRef (pCabacCtx, pCurMb, pMbCache, 0);
        WelsCabacMbRef (pCabacCtx, pCurMb, pMbCache, 2);
        WelsCabacMbRef (pCabacCtx, pCurMb, pMbCache, 12);
        WelsCabacMbRef (pCabacCtx, pCurMb, pMbCache, 14);
      }
      //write sub8x8 mvd
      WelsCabacSubMbMvd (pCabacCtx, pCurMb, pMbCache, iMbWidth);
    }
    if (uiMbType != MB_TYPE_INTRA16x16) {
      WelsCabacMbCbp (pCurMb, iMbWidth, pCabacCtx);
    }
    iRet = WelsWriteMbResidualCabac (pEncCtx->pFuncList, pSlice, pMbCache, pCurMb, pCabacCtx, iMbWidth,
                                     uiChromaQpIndexOffset);
  }
  if (!IS_INTRA (pCurMb->uiMbType))
    pCurMb->uiChromPredMode = 0;

  return iRet;
}


}
