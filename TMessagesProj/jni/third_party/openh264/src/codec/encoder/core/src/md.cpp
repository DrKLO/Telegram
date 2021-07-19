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
 * \file    md.c
 *
 * \brief   mode decision
 *
 * \date    2009.05.14 Created
 *
 *************************************************************************************
 */

#include "ls_defines.h"
#include "md.h"
#include "cpu_core.h"
#include "svc_enc_golomb.h"

namespace WelsEnc {
#define INTRA_VARIANCE_SAD_THRESHOLD 150
#define INTER_VARIANCE_SAD_THRESHOLD 20

//fill cache of neighbor MB, containing pNonZeroCount, sample_avail, pIntra4x4PredMode
void FillNeighborCacheIntra (SMbCache* pMbCache, SMB* pCurMb, int32_t iMbWidth) {
  uint32_t uiNeighborAvail = pCurMb->uiNeighborAvail;
  uint32_t uiNeighborIntra = 0;

  if (uiNeighborAvail & LEFT_MB_POS) { //LEFT MB
    int8_t* pLeftMbNonZeroCount = pCurMb->pNonZeroCount - MB_LUMA_CHROMA_BLOCK4x4_NUM;
    pMbCache->iNonZeroCoeffCount[8] = pLeftMbNonZeroCount[ 3];
    pMbCache->iNonZeroCoeffCount[16] = pLeftMbNonZeroCount[ 7];
    pMbCache->iNonZeroCoeffCount[24] = pLeftMbNonZeroCount[11];
    pMbCache->iNonZeroCoeffCount[32] = pLeftMbNonZeroCount[15];

    pMbCache->iNonZeroCoeffCount[ 13] = pLeftMbNonZeroCount[17];
    pMbCache->iNonZeroCoeffCount[21] = pLeftMbNonZeroCount[21];
    pMbCache->iNonZeroCoeffCount[37] = pLeftMbNonZeroCount[19];
    pMbCache->iNonZeroCoeffCount[45] = pLeftMbNonZeroCount[23];

    uiNeighborIntra |= LEFT_MB_POS;

    if (IS_INTRA4x4 ((pCurMb - 1)->uiMbType)) {
      int8_t* pLeftMbIntra4x4PredMode = pCurMb->pIntra4x4PredMode - INTRA_4x4_MODE_NUM;
      pMbCache->iIntraPredMode[8] = pLeftMbIntra4x4PredMode[4];
      pMbCache->iIntraPredMode[16] = pLeftMbIntra4x4PredMode[5];
      pMbCache->iIntraPredMode[24] = pLeftMbIntra4x4PredMode[6];
      pMbCache->iIntraPredMode[32] = pLeftMbIntra4x4PredMode[3];
    } else { // if ( 0 == constrained_intra_pred_flag || IS_INTRA16x16((pCurMb-1)->uiMbType ))
      pMbCache->iIntraPredMode[8] =
        pMbCache->iIntraPredMode[16] =
          pMbCache->iIntraPredMode[24] =
            pMbCache->iIntraPredMode[32] = 2; //DC
    }
  } else {
    pMbCache->iNonZeroCoeffCount[ 8] =
      pMbCache->iNonZeroCoeffCount[16] =
        pMbCache->iNonZeroCoeffCount[24] =
          pMbCache->iNonZeroCoeffCount[32] = -1;//unavailable
    pMbCache->iNonZeroCoeffCount[13] =
      pMbCache->iNonZeroCoeffCount[21] =
        pMbCache->iNonZeroCoeffCount[37] =
          pMbCache->iNonZeroCoeffCount[45] = -1;//unavailable

    pMbCache->iIntraPredMode[8] =
      pMbCache->iIntraPredMode[16] =
        pMbCache->iIntraPredMode[24] =
          pMbCache->iIntraPredMode[32] = -1;//unavailable
  }

  if (uiNeighborAvail & TOP_MB_POS) { //TOP MB
    SMB* pTopMb = pCurMb - iMbWidth;
    ST32 (&pMbCache->iNonZeroCoeffCount[1], LD32 (&pTopMb->pNonZeroCount[12]));

    ST16 (&pMbCache->iNonZeroCoeffCount[6], LD16 (&pTopMb->pNonZeroCount[20]));
    ST16 (&pMbCache->iNonZeroCoeffCount[30], LD16 (&pTopMb->pNonZeroCount[22]));

    uiNeighborIntra |= TOP_MB_POS;

    if (IS_INTRA4x4 (pTopMb->uiMbType)) {
      ST32 (pMbCache->iIntraPredMode + 1, LD32 (&pTopMb->pIntra4x4PredMode[0]));
    } else { // if ( 0 == constrained_intra_pred_flag || IS_INTRA16x16( pTopMb->uiMbType ))
      const uint32_t kuiDc32 = 0x02020202;
      ST32 (pMbCache->iIntraPredMode + 1 , kuiDc32);
    }
  } else {
    const uint32_t kuiUnavail32 = 0xffffffff;
    ST32 (pMbCache->iIntraPredMode + 1 , kuiUnavail32);
    ST32 (&pMbCache->iNonZeroCoeffCount[1], kuiUnavail32);

    ST16 (&pMbCache->iNonZeroCoeffCount[6], 0xffff);
    ST16 (&pMbCache->iNonZeroCoeffCount[30], 0xffff);
  }

  if (uiNeighborAvail & TOPLEFT_MB_POS) {
    uiNeighborIntra |= 0x04;
  }


  if (uiNeighborAvail & TOPRIGHT_MB_POS) {
    uiNeighborIntra |= 0x08;
  }
  pMbCache->uiNeighborIntra = uiNeighborIntra;
}
//fill cache of neighbor MB, containing motion_vector and uiRefIndex
void FillNeighborCacheInterWithoutBGD (SMbCache* pMbCache, SMB* pCurMb, int32_t iMbWidth, int8_t* pVaaBgMbFlag) {
  uint32_t uiNeighborAvail = pCurMb->uiNeighborAvail;
  SMB* pLeftMb = pCurMb - 1 ;
  SMB* pTopMb = pCurMb - iMbWidth;
  SMB* pLeftTopMb = pCurMb - iMbWidth - 1 ;
  SMB* iRightTopMb = pCurMb - iMbWidth + 1 ;
  SMVComponentUnit* pMvComp = &pMbCache->sMvComponents;
  if ((uiNeighborAvail & LEFT_MB_POS) && IS_SVC_INTER (pLeftMb->uiMbType)) {
    pMvComp->sMotionVectorCache[ 6] = pLeftMb->sMv[ 3];
    pMvComp->sMotionVectorCache[12] = pLeftMb->sMv[ 7];
    pMvComp->sMotionVectorCache[18] = pLeftMb->sMv[11];
    pMvComp->sMotionVectorCache[24] = pLeftMb->sMv[15];
    pMvComp->iRefIndexCache[ 6] = pLeftMb->pRefIndex[1];
    pMvComp->iRefIndexCache[12] = pLeftMb->pRefIndex[1];
    pMvComp->iRefIndexCache[18] = pLeftMb->pRefIndex[3];
    pMvComp->iRefIndexCache[24] = pLeftMb->pRefIndex[3];
    pMbCache->iSadCost[3] = pLeftMb->pSadCost[0];

    if (pLeftMb->uiMbType == MB_TYPE_SKIP) {
      pMbCache->bMbTypeSkip[3] = 1;
      pMbCache->iSadCostSkip[3] = pMbCache->pEncSad[-1];
    } else {
      pMbCache->bMbTypeSkip[3] = 0;
      pMbCache->iSadCostSkip[3] = 0;
    }
  } else { //avail or non-inter
    ST32 (&pMvComp->sMotionVectorCache[ 6], 0);
    ST32 (&pMvComp->sMotionVectorCache[12], 0);
    ST32 (&pMvComp->sMotionVectorCache[18], 0);
    ST32 (&pMvComp->sMotionVectorCache[24], 0);
    pMvComp->iRefIndexCache[ 6] =
      pMvComp->iRefIndexCache[12] =
        pMvComp->iRefIndexCache[18] =
          pMvComp->iRefIndexCache[24] = (uiNeighborAvail & LEFT_MB_POS) ? REF_NOT_IN_LIST : REF_NOT_AVAIL;
    pMbCache->iSadCost[3] = 0;
    pMbCache->bMbTypeSkip[3] = 0;
    pMbCache->iSadCostSkip[3] = 0;
  }

  if ((uiNeighborAvail & TOP_MB_POS) && IS_SVC_INTER (pTopMb->uiMbType)) { //TOP MB
    ST64 (&pMvComp->sMotionVectorCache[1], LD64 (&pTopMb->sMv[12]));
    ST64 (&pMvComp->sMotionVectorCache[3], LD64 (&pTopMb->sMv[14]));
    pMvComp->iRefIndexCache[1] = pTopMb->pRefIndex[2];
    pMvComp->iRefIndexCache[2] = pTopMb->pRefIndex[2];
    pMvComp->iRefIndexCache[3] = pTopMb->pRefIndex[3];
    pMvComp->iRefIndexCache[4] = pTopMb->pRefIndex[3];
    pMbCache->iSadCost[1] = pTopMb->pSadCost[0];

    if (pTopMb->uiMbType == MB_TYPE_SKIP) {
      pMbCache->bMbTypeSkip[1] = 1;
      pMbCache->iSadCostSkip[1] = pMbCache->pEncSad[-iMbWidth];
    } else {
      pMbCache->bMbTypeSkip[1] = 0;
      pMbCache->iSadCostSkip[1] = 0;
    }
  } else { //unavail
    ST64 (&pMvComp->sMotionVectorCache[1], 0);
    ST64 (&pMvComp->sMotionVectorCache[3], 0);
    pMvComp->iRefIndexCache[1] =
      pMvComp->iRefIndexCache[2] =
        pMvComp->iRefIndexCache[3] =
          pMvComp->iRefIndexCache[4] = (uiNeighborAvail & TOP_MB_POS) ? REF_NOT_IN_LIST : REF_NOT_AVAIL;
    pMbCache->iSadCost[1] = 0;

    pMbCache->bMbTypeSkip[1] = 0;
    pMbCache->iSadCostSkip[1] = 0;
  }

  if ((uiNeighborAvail & TOPLEFT_MB_POS) && IS_SVC_INTER (pLeftTopMb->uiMbType)) { //LEFT_TOP MB
    pMvComp->sMotionVectorCache[0] = pLeftTopMb->sMv[15];
    pMvComp->iRefIndexCache[0] = pLeftTopMb->pRefIndex[3];
    pMbCache->iSadCost[0] = pLeftTopMb->pSadCost[0];

    if (pLeftTopMb->uiMbType == MB_TYPE_SKIP) {
      pMbCache->bMbTypeSkip[0] = 1;
      pMbCache->iSadCostSkip[0] = pMbCache->pEncSad[-iMbWidth - 1];
    } else {
      pMbCache->bMbTypeSkip[0] = 0;
      pMbCache->iSadCostSkip[0] = 0;
    }
  } else { //unavail
    ST32 (&pMvComp->sMotionVectorCache[0], 0);
    pMvComp->iRefIndexCache[0] = (uiNeighborAvail & TOPLEFT_MB_POS) ? REF_NOT_IN_LIST : REF_NOT_AVAIL;
    pMbCache->iSadCost[0] = 0;
    pMbCache->bMbTypeSkip[0] = 0;
    pMbCache->iSadCostSkip[0] = 0;
  }

  if ((uiNeighborAvail & TOPRIGHT_MB_POS) && IS_SVC_INTER (iRightTopMb->uiMbType)) { //RIGHT_TOP MB
    pMvComp->sMotionVectorCache[5] = iRightTopMb->sMv[12];
    pMvComp->iRefIndexCache[5] = iRightTopMb->pRefIndex[2];
    pMbCache->iSadCost[2] = iRightTopMb->pSadCost[0];

    if (iRightTopMb->uiMbType == MB_TYPE_SKIP) {
      pMbCache->bMbTypeSkip[2] = 1;
      pMbCache->iSadCostSkip[2] = pMbCache->pEncSad[-iMbWidth + 1];
    } else {
      pMbCache->bMbTypeSkip[2] = 0;
      pMbCache->iSadCostSkip[2] = 0;
    }
  } else { //unavail
    ST32 (&pMvComp->sMotionVectorCache[5], 0);
    pMvComp->iRefIndexCache[5] = (uiNeighborAvail & TOPRIGHT_MB_POS) ? REF_NOT_IN_LIST : REF_NOT_AVAIL;
    pMbCache->iSadCost[2] = 0;
    pMbCache->bMbTypeSkip[2] = 0;
    pMbCache->iSadCostSkip[2] = 0;
  }

  //right-top 4*4 pBlock unavailable
  ST32 (&pMvComp->sMotionVectorCache[ 9], 0);
  ST32 (&pMvComp->sMotionVectorCache[21], 0);
  ST32 (&pMvComp->sMotionVectorCache[11], 0);
  ST32 (&pMvComp->sMotionVectorCache[17], 0);
  ST32 (&pMvComp->sMotionVectorCache[23], 0);
  pMvComp->iRefIndexCache[ 9] =
    pMvComp->iRefIndexCache[11] =
      pMvComp->iRefIndexCache[17] =
        pMvComp->iRefIndexCache[21] =
          pMvComp->iRefIndexCache[23] = REF_NOT_AVAIL;
}

void FillNeighborCacheInterWithBGD (SMbCache* pMbCache, SMB* pCurMb, int32_t iMbWidth, int8_t* pVaaBgMbFlag) {
  uint32_t uiNeighborAvail = pCurMb->uiNeighborAvail;
  SMB* pLeftMb = pCurMb - 1 ;
  SMB* pTopMb = pCurMb - iMbWidth;
  SMB* pLeftTopMb = pCurMb - iMbWidth - 1 ;
  SMB* iRightTopMb = pCurMb - iMbWidth + 1 ;
  SMVComponentUnit* pMvComp = &pMbCache->sMvComponents;

  if ((uiNeighborAvail & LEFT_MB_POS) && IS_SVC_INTER (pLeftMb->uiMbType)) {
    pMvComp->sMotionVectorCache[ 6] = pLeftMb->sMv[ 3];
    pMvComp->sMotionVectorCache[12] = pLeftMb->sMv[ 7];
    pMvComp->sMotionVectorCache[18] = pLeftMb->sMv[11];
    pMvComp->sMotionVectorCache[24] = pLeftMb->sMv[15];
    pMvComp->iRefIndexCache[ 6] = pLeftMb->pRefIndex[1];
    pMvComp->iRefIndexCache[12] = pLeftMb->pRefIndex[1];
    pMvComp->iRefIndexCache[18] = pLeftMb->pRefIndex[3];
    pMvComp->iRefIndexCache[24] = pLeftMb->pRefIndex[3];
    pMbCache->iSadCost[3] = pLeftMb->pSadCost[0];

    if (pLeftMb->uiMbType == MB_TYPE_SKIP && pVaaBgMbFlag[-1] == 0) {
      pMbCache->bMbTypeSkip[3] = 1;
      pMbCache->iSadCostSkip[3] = pMbCache->pEncSad[-1];
    } else {
      pMbCache->bMbTypeSkip[3] = 0;
      pMbCache->iSadCostSkip[3] = 0;
    }
  } else { //avail or non-inter
    ST32 (&pMvComp->sMotionVectorCache[ 6], 0);
    ST32 (&pMvComp->sMotionVectorCache[12], 0);
    ST32 (&pMvComp->sMotionVectorCache[18], 0);
    ST32 (&pMvComp->sMotionVectorCache[24], 0);
    pMvComp->iRefIndexCache[ 6] =
      pMvComp->iRefIndexCache[12] =
        pMvComp->iRefIndexCache[18] =
          pMvComp->iRefIndexCache[24] = (uiNeighborAvail & LEFT_MB_POS) ? REF_NOT_IN_LIST : REF_NOT_AVAIL;
    pMbCache->iSadCost[3] = 0;
    pMbCache->bMbTypeSkip[3] = 0;
    pMbCache->iSadCostSkip[3] = 0;
  }

  if ((uiNeighborAvail & TOP_MB_POS) && IS_SVC_INTER (pTopMb->uiMbType)) { //TOP MB
    ST64 (&pMvComp->sMotionVectorCache[1], LD64 (&pTopMb->sMv[12]));
    ST64 (&pMvComp->sMotionVectorCache[3], LD64 (&pTopMb->sMv[14]));
    pMvComp->iRefIndexCache[1] = pTopMb->pRefIndex[2];
    pMvComp->iRefIndexCache[2] = pTopMb->pRefIndex[2];
    pMvComp->iRefIndexCache[3] = pTopMb->pRefIndex[3];
    pMvComp->iRefIndexCache[4] = pTopMb->pRefIndex[3];
    pMbCache->iSadCost[1] = pTopMb->pSadCost[0];
    if (pTopMb->uiMbType == MB_TYPE_SKIP  && pVaaBgMbFlag[-iMbWidth] == 0) {
      pMbCache->bMbTypeSkip[1] = 1;
      pMbCache->iSadCostSkip[1] = pMbCache->pEncSad[-iMbWidth];
    } else {
      pMbCache->bMbTypeSkip[1] = 0;
      pMbCache->iSadCostSkip[1] = 0;
    }
  } else { //unavail
    ST64 (&pMvComp->sMotionVectorCache[1], 0);
    ST64 (&pMvComp->sMotionVectorCache[3], 0);
    pMvComp->iRefIndexCache[1] =
      pMvComp->iRefIndexCache[2] =
        pMvComp->iRefIndexCache[3] =
          pMvComp->iRefIndexCache[4] = (uiNeighborAvail & TOP_MB_POS) ? REF_NOT_IN_LIST : REF_NOT_AVAIL;
    pMbCache->iSadCost[1] = 0;
    pMbCache->bMbTypeSkip[1] = 0;
    pMbCache->iSadCostSkip[1] = 0;
  }


  if ((uiNeighborAvail & TOPLEFT_MB_POS) && IS_SVC_INTER (pLeftTopMb->uiMbType)) { //LEFT_TOP MB
    pMvComp->sMotionVectorCache[0] = pLeftTopMb->sMv[15];
    pMvComp->iRefIndexCache[0] = pLeftTopMb->pRefIndex[3];
    pMbCache->iSadCost[0] = pLeftTopMb->pSadCost[0];

    if (pLeftTopMb->uiMbType == MB_TYPE_SKIP  && pVaaBgMbFlag[-iMbWidth - 1] == 0) {
      pMbCache->bMbTypeSkip[0] = 1;
      pMbCache->iSadCostSkip[0] = pMbCache->pEncSad[-iMbWidth - 1];
    } else {
      pMbCache->bMbTypeSkip[0] = 0;
      pMbCache->iSadCostSkip[0] = 0;
    }
  } else { //unavail
    ST32 (&pMvComp->sMotionVectorCache[0], 0);
    pMvComp->iRefIndexCache[0] = (uiNeighborAvail & TOPLEFT_MB_POS) ? REF_NOT_IN_LIST : REF_NOT_AVAIL;
    pMbCache->iSadCost[0] = 0;
    pMbCache->bMbTypeSkip[0] = 0;
    pMbCache->iSadCostSkip[0] = 0;
  }

  if ((uiNeighborAvail & TOPRIGHT_MB_POS) && IS_SVC_INTER (iRightTopMb->uiMbType)) { //RIGHT_TOP MB
    pMvComp->sMotionVectorCache[5] = iRightTopMb->sMv[12];
    pMvComp->iRefIndexCache[5] = iRightTopMb->pRefIndex[2];
    pMbCache->iSadCost[2] = iRightTopMb->pSadCost[0];

    if (iRightTopMb->uiMbType == MB_TYPE_SKIP  && pVaaBgMbFlag[-iMbWidth + 1] == 0) {
      pMbCache->bMbTypeSkip[2] = 1;
      pMbCache->iSadCostSkip[2] = pMbCache->pEncSad[-iMbWidth + 1];
    } else {
      pMbCache->bMbTypeSkip[2] = 0;
      pMbCache->iSadCostSkip[2] = 0;
    }
  } else { //unavail
    ST32 (&pMvComp->sMotionVectorCache[5], 0);
    pMvComp->iRefIndexCache[5] = (uiNeighborAvail & TOPRIGHT_MB_POS) ? REF_NOT_IN_LIST : REF_NOT_AVAIL;
    pMbCache->iSadCost[2] = 0;
    pMbCache->bMbTypeSkip[2] = 0;
    pMbCache->iSadCostSkip[2] = 0;
  }

  //right-top 4*4 pBlock unavailable
  ST32 (&pMvComp->sMotionVectorCache[ 9], 0);
  ST32 (&pMvComp->sMotionVectorCache[21], 0);
  ST32 (&pMvComp->sMotionVectorCache[11], 0);
  ST32 (&pMvComp->sMotionVectorCache[17], 0);
  ST32 (&pMvComp->sMotionVectorCache[23], 0);
  pMvComp->iRefIndexCache[ 9] =
    pMvComp->iRefIndexCache[11] =
      pMvComp->iRefIndexCache[17] =
        pMvComp->iRefIndexCache[21] =
          pMvComp->iRefIndexCache[23] = REF_NOT_AVAIL;
}

void InitFillNeighborCacheInterFunc (SWelsFuncPtrList* pFuncList, const int32_t kiFlag) {
  pFuncList->pfFillInterNeighborCache = kiFlag ? FillNeighborCacheInterWithBGD : FillNeighborCacheInterWithoutBGD;
}

void UpdateMbMv_c (SMVUnitXY* pMvBuffer, const SMVUnitXY ksMv) {
  int32_t k = 0;
  for (; k < MB_BLOCK4x4_NUM; k += 4) {
    pMvBuffer[k  ] =
      pMvBuffer[k + 1] =
        pMvBuffer[k + 2] =
          pMvBuffer[k + 3] = ksMv;
  }
}


uint8_t MdInterAnalysisVaaInfo_c (int32_t* pSad8x8) {
  int32_t iSadBlock[4], iAverageSadBlock[4];
  int32_t iAverageSad, iVarianceSad;

  iSadBlock[0] = pSad8x8[0];
  iAverageSad = iSadBlock[0];

  iSadBlock[1] = pSad8x8[1];
  iAverageSad += iSadBlock[1];

  iSadBlock[2] = pSad8x8[2];
  iAverageSad += iSadBlock[2];

  iSadBlock[3] = pSad8x8[3];
  iAverageSad += iSadBlock[3];

  iAverageSad = iAverageSad >> 2;

  iAverageSadBlock[0] = (iSadBlock[0] >> 6) - (iAverageSad >> 6);
  iVarianceSad = iAverageSadBlock[0] * iAverageSadBlock[0];

  iAverageSadBlock[1] = (iSadBlock[1] >> 6) - (iAverageSad >> 6);
  iVarianceSad += iAverageSadBlock[1] * iAverageSadBlock[1];

  iAverageSadBlock[2] = (iSadBlock[2] >> 6) - (iAverageSad >> 6);
  iVarianceSad += iAverageSadBlock[2] * iAverageSadBlock[2];

  iAverageSadBlock[3] = (iSadBlock[3] >> 6) - (iAverageSad >> 6);
  iVarianceSad += iAverageSadBlock[3] * iAverageSadBlock[3];

  if (iVarianceSad < INTER_VARIANCE_SAD_THRESHOLD) {
    return 15;
  }

  uint8_t uiMbSign = 0;
  if (iSadBlock[0] > iAverageSad)
    uiMbSign |= 0x08;
  if (iSadBlock[1] > iAverageSad)
    uiMbSign |= 0x04;
  if (iSadBlock[2] > iAverageSad)
    uiMbSign |= 0x02;
  if (iSadBlock[3] > iAverageSad)
    uiMbSign |= 0x01;
  return (uiMbSign);
}

int32_t AnalysisVaaInfoIntra_c (uint8_t* pDataY, const int32_t kiLineSize) {
  ENFORCE_STACK_ALIGN_1D (uint16_t, uiAvgBlock, 16, 16)
  uint16_t* pBlock = &uiAvgBlock[0];
  uint8_t* pEncData         = pDataY;
  const int32_t kiLineSize2 = kiLineSize << 1;
  const int32_t kiLineSize3 = kiLineSize + kiLineSize2;
  const int32_t kiLineSize4 = kiLineSize << 2;
  int32_t i = 0, j = 0, num = 0;
  int32_t iSumAvg = 0, iSumSqr = 0;

//  analysis_vaa_info_intra_core_c( pDataY, iLineSize, pBlock );
  for (; j < 16; j += 4) {
    num = 0;
    for (i = 0; i < 16; i += 4, num ++) {
      pBlock[num] =  pEncData[i              ] + pEncData[i + 1              ] + pEncData[i + 2              ] + pEncData[i +
                     3              ];
      pBlock[num] += pEncData[i + kiLineSize ] + pEncData[i + kiLineSize  + 1] + pEncData[i + kiLineSize  + 2] + pEncData[i +
                     kiLineSize  + 3];
      pBlock[num] += pEncData[i + kiLineSize2] + pEncData[i + kiLineSize2 + 1] + pEncData[i + kiLineSize2 + 2] + pEncData[i +
                     kiLineSize2 + 3];
      pBlock[num] += pEncData[i + kiLineSize3] + pEncData[i + kiLineSize3 + 1] + pEncData[i + kiLineSize3 + 2] + pEncData[i +
                     kiLineSize3 + 3];
      pBlock[num] >>=  4;
    }
    pBlock += 4;
    pEncData += kiLineSize4;
  }

  pBlock = &uiAvgBlock[0];
  i = 4;
  for (; i > 0; --i) {
    iSumAvg += pBlock[0] + pBlock[1] + pBlock[2] + pBlock[3];
    iSumSqr += pBlock[0] * pBlock[0] + pBlock[1] * pBlock[1] + pBlock[2] * pBlock[2] + pBlock[3] * pBlock[3];

    pBlock += 4;
  }


  return /*variance =*/ (iSumSqr - ((iSumAvg * iSumAvg) >> 4));
}

// for pfGetVarianceFromIntraVaa function ptr adaptive by CPU features, 6/7/2010
void InitIntraAnalysisVaaInfo (SWelsFuncPtrList* pFuncList, const uint32_t kuiCpuFlag) {
  pFuncList->pfGetVarianceFromIntraVaa      = AnalysisVaaInfoIntra_c;
  pFuncList->pfGetMbSignFromInterVaa        = MdInterAnalysisVaaInfo_c;
  pFuncList->pfUpdateMbMv                   = UpdateMbMv_c;

#if defined(X86_ASM)
  if ((kuiCpuFlag & WELS_CPU_SSE2) == WELS_CPU_SSE2) {
    pFuncList->pfGetVarianceFromIntraVaa    = AnalysisVaaInfoIntra_sse2;
    pFuncList->pfGetMbSignFromInterVaa      = MdInterAnalysisVaaInfo_sse2;
    pFuncList->pfUpdateMbMv                 = UpdateMbMv_sse2;
  }
  if ((kuiCpuFlag & WELS_CPU_SSSE3) == WELS_CPU_SSSE3) {
    pFuncList->pfGetVarianceFromIntraVaa    = AnalysisVaaInfoIntra_ssse3;
  }
  if ((kuiCpuFlag & WELS_CPU_SSE41) == WELS_CPU_SSE41) {
    pFuncList->pfGetMbSignFromInterVaa      = MdInterAnalysisVaaInfo_sse41;
  }
#endif//X86_ASM
}

bool MdIntraAnalysisVaaInfo (sWelsEncCtx* pEncCtx, uint8_t* pEncMb) {

  SDqLayer* pCurDqLayer     = pEncCtx->pCurDqLayer;
  const int32_t kiLineSize  = pCurDqLayer->iEncStride[0];
  const int32_t kiVariance  = pEncCtx->pFuncList->pfGetVarianceFromIntraVaa (pEncMb, kiLineSize);
  return (kiVariance >= INTRA_VARIANCE_SAD_THRESHOLD);
}

void InitMeRefinePointer (SMeRefinePointer* pMeRefine, SMbCache* pMbCache, int32_t iStride) {
  pMeRefine->pHalfPixH    = &pMbCache->pBufferInterPredMe[0] + iStride;
  pMeRefine->pHalfPixV    = &pMbCache->pBufferInterPredMe[640] + iStride;

  pMeRefine->pQuarPixBest = &pMbCache->pBufferInterPredMe[1280] + iStride;
  pMeRefine->pQuarPixTmp  = &pMbCache->pBufferInterPredMe[1920] + iStride;
}
typedef struct TagQuarParams {
  int32_t iBestCost;
  int32_t iBestHalfPix;
  int32_t iStrideA;
  int32_t iStrideB;
  uint8_t* pRef;
  uint8_t* pSrcB[4];
  uint8_t* pSrcA[4];
  int32_t iLms[4];
  int32_t iBestQuarPix;
} SQuarRefineParams;

#define SWITCH_BEST_TMP_BUF(prev_best, curr_best){\
  pParams->iBestCost = iCurCost;\
  pTmp = prev_best;\
  prev_best = curr_best;\
  curr_best = pTmp;\
}
#define CALC_COST(me_buf, lm) ( pFunc->sSampleDealingFuncs.pfMeCost[kuiPixel](pEncMb, iStrideEnc, me_buf, ME_REFINE_BUF_STRIDE) + lm )

inline void MeRefineQuarPixel (SWelsFuncPtrList* pFunc, SWelsME* pMe, SMeRefinePointer* pMeRefine,
                               const int32_t kiWidth, const int32_t kiHeight, SQuarRefineParams* pParams, int32_t iStrideEnc) {
  PWelsSampleAveragingFunc pSampleAvg   = pFunc->sMcFuncs.pfSampleAveraging;
  int32_t iCurCost;
  uint8_t* pEncMb                       = pMe->pEncMb;
  uint8_t* pTmp                         = NULL;
  const uint8_t kuiPixel                = pMe->uiBlockSize;

  pSampleAvg (pMeRefine->pQuarPixTmp, ME_REFINE_BUF_STRIDE, pParams->pSrcA[0], ME_REFINE_BUF_STRIDE,
              pParams->pSrcB[0], pParams->iStrideA, kiWidth, kiHeight);

  iCurCost = CALC_COST (pMeRefine->pQuarPixTmp, pParams->iLms[0]);
  if (iCurCost < pParams->iBestCost) {
    pParams->iBestQuarPix = ME_QUAR_PIXEL_TOP;
    SWITCH_BEST_TMP_BUF (pMeRefine->pQuarPixBest, pMeRefine->pQuarPixTmp);
  }
  //=========================(0, 1)=======================//
  pSampleAvg (pMeRefine->pQuarPixTmp, ME_REFINE_BUF_STRIDE, pParams->pSrcA[1],
              ME_REFINE_BUF_STRIDE, pParams->pSrcB[1], pParams->iStrideA, kiWidth, kiHeight);
  iCurCost = CALC_COST (pMeRefine->pQuarPixTmp, pParams->iLms[1]);
  if (iCurCost < pParams->iBestCost) {
    pParams->iBestQuarPix = ME_QUAR_PIXEL_BOTTOM;
    SWITCH_BEST_TMP_BUF (pMeRefine->pQuarPixBest, pMeRefine->pQuarPixTmp);
  }
  //==========================(-1, 0)=========================//
  pSampleAvg (pMeRefine->pQuarPixTmp, ME_REFINE_BUF_STRIDE, pParams->pSrcA[2],
              ME_REFINE_BUF_STRIDE, pParams->pSrcB[2], pParams->iStrideB, kiWidth, kiHeight);
  iCurCost = CALC_COST (pMeRefine->pQuarPixTmp, pParams->iLms[2]);
  if (iCurCost < pParams->iBestCost) {
    pParams->iBestQuarPix = ME_QUAR_PIXEL_LEFT;
    SWITCH_BEST_TMP_BUF (pMeRefine->pQuarPixBest, pMeRefine->pQuarPixTmp);
  }
  //==========================(1, 0)=========================//
  pSampleAvg (pMeRefine->pQuarPixTmp, ME_REFINE_BUF_STRIDE, pParams->pSrcA[3],
              ME_REFINE_BUF_STRIDE, pParams->pSrcB[3], pParams->iStrideB,  kiWidth, kiHeight);

  iCurCost = CALC_COST (pMeRefine->pQuarPixTmp, pParams->iLms[3]);
  if (iCurCost < pParams->iBestCost) {
    pParams->iBestQuarPix = ME_QUAR_PIXEL_RIGHT;
    SWITCH_BEST_TMP_BUF (pMeRefine->pQuarPixBest, pMeRefine->pQuarPixTmp);
  }
}

void MeRefineFracPixel (sWelsEncCtx* pEncCtx, uint8_t* pMemPredInterMb, SWelsME* pMe,
                        SMeRefinePointer* pMeRefine, int32_t iWidth, int32_t iHeight) {
  SWelsFuncPtrList* pFunc = pEncCtx->pFuncList;
  int16_t iMvx = pMe->sMv.iMvX;
  int16_t iMvy = pMe->sMv.iMvY;

  int16_t iHalfMvx = iMvx;
  int16_t iHalfMvy = iMvy;
  const int32_t kiStrideEnc = pEncCtx->pCurDqLayer->iEncStride[0];
  const int32_t kiStrideRef = pEncCtx->pCurDqLayer->pRefPic->iLineSize[0];

  uint8_t* pEncData = pMe->pEncMb;
  uint8_t* pRef = pMe->pRefMb;//091010

  int32_t iBestQuarPix = ME_NO_BEST_QUAR_PIXEL;

  SQuarRefineParams sParams;
  static const int32_t iMvQuarAddX[10] = {0, 0, -1, 1, 0, 0, 0, -1, 1, 0};
  const int32_t* pMvQuarAddY = iMvQuarAddX + 3;
  uint8_t* pBestPredInter = pRef;
  int32_t iInterBlk4Stride = ME_REFINE_BUF_STRIDE;

  int32_t iBestCost;
  int32_t iCurCost;
  int32_t iBestHalfPix;

  if (pEncCtx->pCurDqLayer->bSatdInMdFlag) {
    iBestCost = pMe->uSadPredISatd.uiSatd + COST_MVD (pMe->pMvdCost, iMvx - pMe->sMvp.iMvX, iMvy - pMe->sMvp.iMvY);
  } else {
    iBestCost = pFunc->sSampleDealingFuncs.pfMeCost[pMe->uiBlockSize] (pEncData, kiStrideEnc, pRef, kiStrideRef) +
                COST_MVD (pMe->pMvdCost, iMvx - pMe->sMvp.iMvX, iMvy - pMe->sMvp.iMvY);
  }

  iBestHalfPix = REFINE_ME_NO_BEST_HALF_PIXEL;

  pFunc->sMcFuncs.pfLumaHalfpelVer (pRef - kiStrideRef, kiStrideRef, pMeRefine->pHalfPixV, ME_REFINE_BUF_STRIDE, iWidth,
                                    iHeight + 1);

  //step 1: get [iWidth][iHeight+1] half pixel from vertical filter
  //===========================(0, -2)==============================//
  iCurCost = pFunc->sSampleDealingFuncs.pfMeCost[pMe->uiBlockSize] (pEncData, kiStrideEnc, pMeRefine->pHalfPixV,
             ME_REFINE_BUF_STRIDE) +
             COST_MVD (pMe->pMvdCost, iMvx - pMe->sMvp.iMvX, iMvy - 2 - pMe->sMvp.iMvY);
  if (iCurCost < iBestCost) {
    iBestCost = iCurCost;
    iBestHalfPix = REFINE_ME_HALF_PIXEL_TOP;
    pBestPredInter = pMeRefine->pHalfPixV;
  }
  //===========================(0, 2)==============================//
  iCurCost = pFunc->sSampleDealingFuncs.pfMeCost[pMe->uiBlockSize] (pEncData, kiStrideEnc,
             pMeRefine->pHalfPixV + ME_REFINE_BUF_STRIDE, ME_REFINE_BUF_STRIDE) +
             COST_MVD (pMe->pMvdCost, iMvx - pMe->sMvp.iMvX, iMvy + 2 - pMe->sMvp.iMvY);
  if (iCurCost < iBestCost) {
    iBestCost = iCurCost;
    iBestHalfPix = REFINE_ME_HALF_PIXEL_BOTTOM;
    pBestPredInter = pMeRefine->pHalfPixV + ME_REFINE_BUF_STRIDE;
  }
  pFunc->sMcFuncs.pfLumaHalfpelHor (pRef - 1, kiStrideRef, pMeRefine->pHalfPixH, ME_REFINE_BUF_STRIDE, iWidth + 1,
                                    iHeight);
  //step 2: get [iWidth][iHeight+1] half pixel from horizon filter

  //===========================(-2, 0)==============================//
  iCurCost = pFunc->sSampleDealingFuncs.pfMeCost[pMe->uiBlockSize] (pEncData, kiStrideEnc, pMeRefine->pHalfPixH,
             ME_REFINE_BUF_STRIDE) +
             COST_MVD (pMe->pMvdCost, iMvx - 2 - pMe->sMvp.iMvX, iMvy - pMe->sMvp.iMvY);
  if (iCurCost < iBestCost) {
    iBestCost = iCurCost;
    iBestHalfPix = REFINE_ME_HALF_PIXEL_LEFT;
    pBestPredInter = pMeRefine->pHalfPixH;
  }
  //===========================(2, 0)===============================//
  iCurCost = pFunc->sSampleDealingFuncs.pfMeCost[pMe->uiBlockSize] (pEncData, kiStrideEnc, pMeRefine->pHalfPixH + 1,
             ME_REFINE_BUF_STRIDE) +
             COST_MVD (pMe->pMvdCost, iMvx + 2 - pMe->sMvp.iMvX, iMvy - pMe->sMvp.iMvY);
  if (iCurCost < iBestCost) {
    iBestCost = iCurCost;
    iBestHalfPix = REFINE_ME_HALF_PIXEL_RIGHT;
    pBestPredInter = pMeRefine->pHalfPixH + 1;
  }

  sParams.iBestCost = iBestCost;
  sParams.iBestHalfPix = iBestHalfPix;
  sParams.pRef = pRef;
  sParams.iBestQuarPix = ME_NO_BEST_QUAR_PIXEL;

  //step 5: if no best half-pixel prediction, try quarter pixel prediction
  //        if yes, must get [X+1][X+1] half-pixel from (2, 2) horizontal and vertical filter
  if (REFINE_ME_NO_BEST_HALF_PIXEL == iBestHalfPix) {
    sParams.iStrideA = kiStrideRef;
    sParams.iStrideB = kiStrideRef;
    sParams.pSrcA[0] = pMeRefine->pHalfPixV;
    sParams.pSrcA[1] = pMeRefine->pHalfPixV + ME_REFINE_BUF_STRIDE;
    sParams.pSrcA[2] = pMeRefine->pHalfPixH;
    sParams.pSrcA[3] = pMeRefine->pHalfPixH + 1;

    sParams.pSrcB[0] = sParams.pSrcB[1] = sParams.pSrcB[2] = sParams.pSrcB[3] = pRef;

    sParams.iLms[0] = COST_MVD (pMe->pMvdCost, iHalfMvx - pMe->sMvp.iMvX, iHalfMvy - 1 - pMe->sMvp.iMvY);
    sParams.iLms[1] = COST_MVD (pMe->pMvdCost, iHalfMvx - pMe->sMvp.iMvX, iHalfMvy + 1 - pMe->sMvp.iMvY);
    sParams.iLms[2] = COST_MVD (pMe->pMvdCost, iHalfMvx - 1 - pMe->sMvp.iMvX, iHalfMvy - pMe->sMvp.iMvY);
    sParams.iLms[3] = COST_MVD (pMe->pMvdCost, iHalfMvx + 1 - pMe->sMvp.iMvX, iHalfMvy - pMe->sMvp.iMvY);
  } else { //must get [X+1][X+1] half-pixel from (2, 2) horizontal and vertical filter
    switch (iBestHalfPix) {
    case REFINE_ME_HALF_PIXEL_LEFT: {
      pMeRefine->pHalfPixHV = pMeRefine->pHalfPixV;//reuse pBuffer, here only h&hv
      pFunc->sMcFuncs.pfLumaHalfpelCen (pRef - 1 - kiStrideRef, kiStrideRef, pMeRefine->pHalfPixHV, ME_REFINE_BUF_STRIDE,
                                        iWidth + 1, iHeight + 1);

      iHalfMvx -= 2;
      sParams.iStrideA = ME_REFINE_BUF_STRIDE;
      sParams.iStrideB = kiStrideRef;
      sParams.pSrcA[0] = pMeRefine->pHalfPixH;
      sParams.pSrcA[3] = sParams.pSrcA[2] = sParams.pSrcA[1] = sParams.pSrcA[0];
      sParams.pSrcB[0] = pMeRefine->pHalfPixHV;
      sParams.pSrcB[1] = pMeRefine->pHalfPixHV + ME_REFINE_BUF_STRIDE;
      sParams.pSrcB[2] = pRef - 1;
      sParams.pSrcB[3] = pRef;

    }
    break;
    case REFINE_ME_HALF_PIXEL_RIGHT: {
      pMeRefine->pHalfPixHV = pMeRefine->pHalfPixV;//reuse pBuffer, here only h&hv
      pFunc->sMcFuncs.pfLumaHalfpelCen (pRef - 1 - kiStrideRef, kiStrideRef, pMeRefine->pHalfPixHV, ME_REFINE_BUF_STRIDE,
                                        iWidth + 1, iHeight + 1);
      iHalfMvx += 2;
      sParams.iStrideA = ME_REFINE_BUF_STRIDE;
      sParams.iStrideB = kiStrideRef;
      sParams.pSrcA[0] = pMeRefine->pHalfPixH + 1;
      sParams.pSrcA[3] = sParams.pSrcA[2] = sParams.pSrcA[1] = sParams.pSrcA[0];
      sParams.pSrcB[0] = pMeRefine->pHalfPixHV + 1;
      sParams.pSrcB[1] = pMeRefine->pHalfPixHV + 1 + ME_REFINE_BUF_STRIDE;
      sParams.pSrcB[2] = pRef;
      sParams.pSrcB[3] = pRef + 1;
    }
    break;
    case REFINE_ME_HALF_PIXEL_TOP: {
      pMeRefine->pHalfPixHV = pMeRefine->pHalfPixH;//reuse pBuffer, here only v&hv
      pFunc->sMcFuncs.pfLumaHalfpelCen (pRef - 1 - kiStrideRef, kiStrideRef, pMeRefine->pHalfPixHV, ME_REFINE_BUF_STRIDE,
                                        iWidth + 1, iHeight + 1);

      iHalfMvy -= 2;
      sParams.iStrideA = kiStrideRef;
      sParams.iStrideB = ME_REFINE_BUF_STRIDE;
      sParams.pSrcA[0] = pMeRefine->pHalfPixV;
      sParams.pSrcA[3] = sParams.pSrcA[2] = sParams.pSrcA[1] = sParams.pSrcA[0];
      sParams.pSrcB[0] = pRef - kiStrideRef;
      sParams.pSrcB[1] = pRef;
      sParams.pSrcB[2] = pMeRefine->pHalfPixHV;
      sParams.pSrcB[3] = pMeRefine->pHalfPixHV + 1;
    }
    break;
    case REFINE_ME_HALF_PIXEL_BOTTOM: {
      pMeRefine->pHalfPixHV = pMeRefine->pHalfPixH;//reuse pBuffer, here only v&hv
      pFunc->sMcFuncs.pfLumaHalfpelCen (pRef - 1 - kiStrideRef, kiStrideRef, pMeRefine->pHalfPixHV, ME_REFINE_BUF_STRIDE,
                                        iWidth + 1, iHeight + 1);
      iHalfMvy += 2;
      sParams.iStrideA = kiStrideRef;
      sParams.iStrideB = ME_REFINE_BUF_STRIDE;
      sParams.pSrcA[0] = pMeRefine->pHalfPixV + ME_REFINE_BUF_STRIDE;
      sParams.pSrcA[3] = sParams.pSrcA[2] = sParams.pSrcA[1] = sParams.pSrcA[0];
      sParams.pSrcB[0] = pRef;
      sParams.pSrcB[1] = pRef + kiStrideRef;
      sParams.pSrcB[2] = pMeRefine->pHalfPixHV + ME_REFINE_BUF_STRIDE;
      sParams.pSrcB[3] = pMeRefine->pHalfPixHV + ME_REFINE_BUF_STRIDE + 1;
    }
    break;
    default:
      break;
    }
    sParams.iLms[0] = COST_MVD (pMe->pMvdCost, iHalfMvx - pMe->sMvp.iMvX, iHalfMvy - 1 - pMe->sMvp.iMvY);
    sParams.iLms[1] = COST_MVD (pMe->pMvdCost, iHalfMvx - pMe->sMvp.iMvX, iHalfMvy + 1 - pMe->sMvp.iMvY);
    sParams.iLms[2] = COST_MVD (pMe->pMvdCost, iHalfMvx - 1 - pMe->sMvp.iMvX, iHalfMvy - pMe->sMvp.iMvY);
    sParams.iLms[3] = COST_MVD (pMe->pMvdCost, iHalfMvx + 1 - pMe->sMvp.iMvX, iHalfMvy - pMe->sMvp.iMvY);
  }
  MeRefineQuarPixel (pFunc, pMe, pMeRefine, iWidth, iHeight, &sParams, kiStrideEnc);

  if (iBestCost > sParams.iBestCost) {
    pBestPredInter = pMeRefine->pQuarPixBest;
    iBestCost = sParams.iBestCost;
  }
  iBestQuarPix = sParams.iBestQuarPix;

  //update final best MV
  pMe->sMv.iMvX = iHalfMvx + iMvQuarAddX[iBestQuarPix];
  pMe->sMv.iMvY = iHalfMvy + pMvQuarAddY[iBestQuarPix];
  pMe->uiSatdCost = iBestCost;

  //No half or quarter pixel best, so do MC with integer pixel MV
  if (iBestHalfPix + iBestQuarPix == NO_BEST_FRAC_PIX) {
    pBestPredInter = pRef;
    iInterBlk4Stride = kiStrideRef;
  }
  pMeRefine->pfCopyBlockByMode (pMemPredInterMb, MB_WIDTH_LUMA, pBestPredInter,
                                iInterBlk4Stride);
}

void InitBlkStrideWithRef (int32_t* pBlkStride, const int32_t kiStrideRef) {
  static const uint8_t kuiStrideX[16] = {
    0, 4 , 0, 4 ,
    8, 12, 8, 12,
    0, 4 , 0, 4 ,
    8, 12, 8, 12
  };
  static const uint8_t kuiStrideY[16] = {
    0, 0, 4 , 4 ,
    0, 0, 4 , 4 ,
    8, 8, 12, 12,
    8, 8, 12, 12
  };
  int32_t i;

  for (i = 0; i < 16; i += 4) {
    pBlkStride[i  ] = kuiStrideX[i  ] + kuiStrideY[i  ] * kiStrideRef;
    pBlkStride[i + 1] = kuiStrideX[i + 1] + kuiStrideY[i + 1] * kiStrideRef;
    pBlkStride[i + 2] = kuiStrideX[i + 2] + kuiStrideY[i + 2] * kiStrideRef;
    pBlkStride[i + 3] = kuiStrideX[i + 3] + kuiStrideY[i + 3] * kiStrideRef;
  }
}

/*
 * iMvdSz = (648*2+1) or (972*2+1);
 */
void MvdCostInit (uint16_t* pMvdCostInter, const int32_t kiMvdSz) {
  const int32_t kiSz        = kiMvdSz >> 1;
  uint16_t* pNegMvd         = pMvdCostInter;
  uint16_t* pPosMvd         = pMvdCostInter + kiSz + 1;
  const int32_t* kpQpLambda = &g_kiQpCostTable[0];
  int32_t i, j;

  for (i = 0; i < 52; ++ i) {
    const uint16_t kiLambda = kpQpLambda[i];
    int32_t iNegSe = -kiSz;
    int32_t iPosSe = 1;

    for (j = 0; j < kiSz; j += 4) {
      *pNegMvd++ = kiLambda * BsSizeSE (iNegSe++);
      *pNegMvd++ = kiLambda * BsSizeSE (iNegSe++);
      *pNegMvd++ = kiLambda * BsSizeSE (iNegSe++);
      *pNegMvd++ = kiLambda * BsSizeSE (iNegSe++);

      *pPosMvd++ = kiLambda * BsSizeSE (iPosSe++);
      *pPosMvd++ = kiLambda * BsSizeSE (iPosSe++);
      *pPosMvd++ = kiLambda * BsSizeSE (iPosSe++);
      *pPosMvd++ = kiLambda * BsSizeSE (iPosSe++);
    }
    *pNegMvd = kiLambda;
    pNegMvd += kiSz + 1;
    pPosMvd += kiSz + 1;
  }
}

void PredictSad (int8_t* pRefIndexCache, int32_t* pSadCostCache, int32_t uiRef, int32_t* pSadPred) {
  const int32_t kiRefB  = pRefIndexCache[1];//top g_uiCache12_8x8RefIdx[0] - 4
  int32_t iRefC         = pRefIndexCache[5];//top-right g_uiCache12_8x8RefIdx[0] - 2
  const int32_t kiRefA  = pRefIndexCache[6];//left g_uiCache12_8x8RefIdx[0] - 1
  const int32_t kiSadB  = pSadCostCache[1];
  int32_t iSadC         = pSadCostCache[2];
  const int32_t kiSadA  = pSadCostCache[3];

  int32_t iCount;

  if (iRefC == REF_NOT_AVAIL) {
    iRefC = pRefIndexCache[0];//top-left g_uiCache12_8x8RefIdx[0] - 4 - 1
    iSadC  = pSadCostCache[0];
  }

  if (kiRefB == REF_NOT_AVAIL && iRefC == REF_NOT_AVAIL && kiRefA != REF_NOT_AVAIL) {
    * pSadPred = kiSadA;
  } else {
    iCount  = (uiRef == kiRefA) << MB_LEFT_BIT;
    iCount |= (uiRef == kiRefB) << MB_TOP_BIT;
    iCount |= (uiRef == iRefC) << MB_TOPRIGHT_BIT;
    switch (iCount) {
    case LEFT_MB_POS:// A
      *pSadPred = kiSadA;
      break;
    case TOP_MB_POS:// B
      *pSadPred = kiSadB;
      break;
    case TOPRIGHT_MB_POS:// C or D
      *pSadPred = iSadC;
      break;
    default:
      *pSadPred = WelsMedian (kiSadA, kiSadB, iSadC);
      break;
    }
  }

#define REPLACE_SAD_MULTIPLY(x)   ((x) - (x>>3) + (x >>5))    // it's 0.90625, very close with 0.9
  iCount = (*pSadPred) << 6;  // here *64 will not overflow. SAD range 0~ 255*256(max 2^16), int32_t is enough
  *pSadPred = (REPLACE_SAD_MULTIPLY (iCount) + 32) >> 6;
#undef REPLACE_SAD_MULTIPLY
}


void PredictSadSkip (int8_t* pRefIndexCache, bool* pMbSkipCache, int32_t* pSadCostCache, int32_t uiRef,
                     int32_t* iSadPredSkip) {
  const int32_t kiRefB  = pRefIndexCache[1];//top g_uiCache12_8x8RefIdx[0] - 4
  int32_t iRefC         = pRefIndexCache[5];//top-right g_uiCache12_8x8RefIdx[0] - 2
  const int32_t kiRefA  = pRefIndexCache[6];//left g_uiCache12_8x8RefIdx[0] - 1
  const int32_t kiSadB  = (pMbSkipCache[1] == 1 ? pSadCostCache[1] : 0);
  int32_t iSadC         = (pMbSkipCache[2] == 1 ? pSadCostCache[2] : 0);
  const int32_t kiSadA  = (pMbSkipCache[3] == 1 ? pSadCostCache[3] : 0);
  int32_t iRefSkip      = pMbSkipCache[2];

  int32_t iCount = 0;

  if (iRefC == REF_NOT_AVAIL) {
    iRefC = pRefIndexCache[0];//top-left g_uiCache12_8x8RefIdx[0] - 4 - 1
    iSadC  = (pMbSkipCache[0] == 1 ? pSadCostCache[0] : 0);
    iRefSkip = pMbSkipCache[0];
  }

  if (kiRefB == REF_NOT_AVAIL && iRefC == REF_NOT_AVAIL && kiRefA != REF_NOT_AVAIL) {
    * iSadPredSkip = kiSadA;
  } else {
    iCount  = ((uiRef == kiRefA) && (pMbSkipCache[3] == 1)) << MB_LEFT_BIT;
    iCount |= ((uiRef == kiRefB) && (pMbSkipCache[1] == 1)) << MB_TOP_BIT;
    iCount |= ((uiRef == iRefC) && (iRefSkip == 1)) << MB_TOPRIGHT_BIT;
    switch (iCount) {
    case LEFT_MB_POS:// A
      *iSadPredSkip = kiSadA;
      break;
    case TOP_MB_POS:// B
      *iSadPredSkip = kiSadB;
      break;
    case TOPRIGHT_MB_POS:// C or D
      *iSadPredSkip = iSadC;
      break;
    default:
      *iSadPredSkip = WelsMedian (kiSadA, kiSadB, iSadC);
      break;
    }
  }
}
}
