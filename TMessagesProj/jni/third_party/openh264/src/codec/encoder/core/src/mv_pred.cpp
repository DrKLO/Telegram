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
 * \file    mv_pred.c
 *
 * \brief   Get MV predictor and update motion vector of mb cache
 *
 * \date    05/22/2009 Created
 *
 *************************************************************************************
 */

#include "mv_pred.h"
#include "ls_defines.h"
namespace WelsEnc {
//basic pMv prediction unit for pMv width (4, 2, 1)
void PredMv (const SMVComponentUnit* kpMvComp, int8_t iPartIdx, int8_t iPartW, int32_t iRef, SMVUnitXY* sMvp) {
  const uint8_t kuiLeftIdx = g_kuiCache30ScanIdx[iPartIdx] - 1;
  const uint8_t kuiTopIdx  = g_kuiCache30ScanIdx[iPartIdx] - 6;

  int32_t iMatchRef;
  int32_t iLeftRef = kpMvComp->iRefIndexCache[kuiLeftIdx];
  int32_t iTopRef  = kpMvComp->iRefIndexCache[ kuiTopIdx];
  int32_t iRightTopRef = kpMvComp->iRefIndexCache[kuiTopIdx + iPartW];
  int32_t iDiagonalRef;
  SMVUnitXY sMvA (kpMvComp->sMotionVectorCache[kuiLeftIdx]);
  SMVUnitXY sMvB (kpMvComp->sMotionVectorCache[kuiTopIdx]);
  SMVUnitXY sMvC;

  if (REF_NOT_AVAIL == iRightTopRef) {
    iDiagonalRef = kpMvComp->iRefIndexCache[ kuiTopIdx - 1];// left_top;
    sMvC = kpMvComp->sMotionVectorCache[kuiTopIdx - 1];
  } else {
    iDiagonalRef = iRightTopRef;// right_top;
    sMvC = kpMvComp->sMotionVectorCache[kuiTopIdx + iPartW];
  }

  if ((REF_NOT_AVAIL == iTopRef) && (REF_NOT_AVAIL == iDiagonalRef) && iLeftRef != REF_NOT_AVAIL) {
    *sMvp = sMvA;
    return;
  }

  // b2[diag] b1[top] b0[left] is available!
  iMatchRef  = (iRef == iLeftRef)     << MB_LEFT_BIT;
  iMatchRef |= (iRef == iTopRef)      << MB_TOP_BIT;
  iMatchRef |= (iRef == iDiagonalRef) << MB_TOPRIGHT_BIT;
  switch (iMatchRef) {
  case LEFT_MB_POS:// A
    *sMvp = sMvA;
    break;
  case TOP_MB_POS:// B
    *sMvp = sMvB;
    break;
  case TOPRIGHT_MB_POS:// C or D
    *sMvp = sMvC;
    break;
  default:
    sMvp->iMvX = WelsMedian (sMvA.iMvX, sMvB.iMvX, sMvC.iMvX);
    sMvp->iMvY = WelsMedian (sMvA.iMvY, sMvB.iMvY, sMvC.iMvY);
    break;
  }
}
void PredInter8x16Mv (SMbCache* pMbCache, int32_t iPartIdx, int8_t iRef, SMVUnitXY* sMvp) {
  const SMVComponentUnit* kpMvComp = &pMbCache->sMvComponents;
  if (0 == iPartIdx) {
    const int8_t kiLeftRef = kpMvComp->iRefIndexCache[6];
    if (iRef == kiLeftRef) {
      *sMvp = kpMvComp->sMotionVectorCache[6];
      return;
    }
  } else { // 1 == iPartIdx
    int8_t iDiagonalRef = kpMvComp->iRefIndexCache[5]; //top-right
    int8_t iIndex = 5;
    if (REF_NOT_AVAIL == iDiagonalRef) {
      iDiagonalRef = kpMvComp->iRefIndexCache[2]; //top-left for 8*8 block(iIndex 1)
      iIndex = 2;
    }
    if (iRef == iDiagonalRef) {
      *sMvp = kpMvComp->sMotionVectorCache[iIndex];
      return;
    }
  }

  PredMv (kpMvComp, iPartIdx, 2, iRef, sMvp);
}
void PredInter16x8Mv (SMbCache* pMbCache, int32_t iPartIdx, int8_t iRef, SMVUnitXY* sMvp) {
  const SMVComponentUnit* kpMvComp = &pMbCache->sMvComponents;
  if (0 == iPartIdx) {
    const int8_t kiTopRef = kpMvComp->iRefIndexCache[1];
    if (iRef == kiTopRef) {
      *sMvp = kpMvComp->sMotionVectorCache[1];
      return;
    }
  } else { // 8 == iPartIdx
    const int8_t kiLeftRef = kpMvComp->iRefIndexCache[18];
    if (iRef == kiLeftRef) {
      *sMvp = kpMvComp->sMotionVectorCache[18];
      return;
    }
  }

  PredMv (kpMvComp, iPartIdx, 4, iRef, sMvp);
}
void PredSkipMv (SMbCache* pMbCache, SMVUnitXY* sMvp) {
  const SMVComponentUnit* kpMvComp = &pMbCache->sMvComponents;
  const int8_t kiLeftRef = kpMvComp->iRefIndexCache[6]; //A
  const int8_t kiTopRef  = kpMvComp->iRefIndexCache[1]; //B

  if (REF_NOT_AVAIL == kiLeftRef  || REF_NOT_AVAIL == kiTopRef ||
      (0 == kiLeftRef && 0 == * (int32_t*) (&kpMvComp->sMotionVectorCache[6])) ||
      (0 == kiTopRef  && 0 == * (int32_t*) (&kpMvComp->sMotionVectorCache[1]))) {
    ST32 (sMvp, 0);
    return;
  }

  PredMv (kpMvComp, 0, 4, 0, sMvp);
}

//update pMv and uiRefIndex cache for current MB, only for P_16*16 (SKIP inclusive)
void UpdateP16x16MotionInfo (SMbCache* pMbCache, SMB* pCurMb, const int8_t kiRef, SMVUnitXY* pMv) {
  // optimized 11/25/2011
  SMVComponentUnit* pMvComp     = &pMbCache->sMvComponents;
  const uint32_t kuiMv32        = LD32 (pMv);
  const uint64_t kuiMv64        = BUTTERFLY4x8 (kuiMv32);
  uint64_t uiMvBuf[8]           = { kuiMv64, kuiMv64, kuiMv64, kuiMv64, kuiMv64, kuiMv64, kuiMv64, kuiMv64 };
  const uint16_t kuiRef16       = BUTTERFLY1x2 (kiRef);
  const uint32_t kuiRef32       = BUTTERFLY2x4 (kuiRef16);

  ST32 (pCurMb->pRefIndex, kuiRef32);
  // update pMv range from 0~15
  memcpy (pCurMb->sMv, uiMvBuf, sizeof (uiMvBuf)); // confirmed_safe_unsafe_usage

  /*
   * blocks 0: 7~10, 1: 13~16, 2: 19~22, 3: 25~28
   */
  pMvComp->iRefIndexCache[7]    = kiRef;
  ST16 (&pMvComp->iRefIndexCache[8], kuiRef16);
  pMvComp->iRefIndexCache[10]   = kiRef;
  pMvComp->iRefIndexCache[13]   = kiRef;
  ST16 (&pMvComp->iRefIndexCache[14], kuiRef16);
  pMvComp->iRefIndexCache[16]   = kiRef;
  pMvComp->iRefIndexCache[19]   = kiRef;
  ST16 (&pMvComp->iRefIndexCache[20], kuiRef16);
  pMvComp->iRefIndexCache[22]   = kiRef;
  pMvComp->iRefIndexCache[25]   = kiRef;
  ST16 (&pMvComp->iRefIndexCache[26], kuiRef16);
  pMvComp->iRefIndexCache[28]   = kiRef;

  /*
  * blocks 0: 7~10, 1: 13~16, 2: 19~22, 3: 25~28
  */
  pMvComp->sMotionVectorCache[7]  = *pMv;
  ST64 (&pMvComp->sMotionVectorCache[8], kuiMv64);
  pMvComp->sMotionVectorCache[10] = *pMv;
  pMvComp->sMotionVectorCache[13] = *pMv;
  ST64 (&pMvComp->sMotionVectorCache[14], kuiMv64);
  pMvComp->sMotionVectorCache[16] = *pMv;
  pMvComp->sMotionVectorCache[19] = *pMv;
  ST64 (&pMvComp->sMotionVectorCache[20], kuiMv64);
  pMvComp->sMotionVectorCache[22] = *pMv;
  pMvComp->sMotionVectorCache[25] = *pMv;
  ST64 (&pMvComp->sMotionVectorCache[26], kuiMv64);
  pMvComp->sMotionVectorCache[28] = *pMv;
}

//update uiRefIndex and pMv of both SMB and Mb_cache, only for P16x8
void UpdateP16x8MotionInfo (SMbCache* pMbCache, SMB* pCurMb, const int32_t kiPartIdx, const int8_t kiRef,
                            SMVUnitXY* pMv) {
  // optimized 11/25/2011
  SMVComponentUnit* pMvComp     = &pMbCache->sMvComponents;
  const uint32_t kuiMv32        = LD32 (pMv);
  const uint64_t kuiMv64        = BUTTERFLY4x8 (kuiMv32);
  uint64_t uiMvBuf[4]           = { kuiMv64, kuiMv64, kuiMv64, kuiMv64 };
  const int16_t kiScan4Idx      = g_kuiMbCountScan4Idx[kiPartIdx];
  const int16_t kiCacheIdx      = g_kuiCache30ScanIdx[kiPartIdx];
  const int16_t kiCacheIdx1     = 1 + kiCacheIdx;
  const int16_t kiCacheIdx3     = 3 + kiCacheIdx;
  const int16_t kiCacheIdx6     = 6 + kiCacheIdx;
  const int16_t kiCacheIdx7     = 7 + kiCacheIdx;
  const int16_t kiCacheIdx9     = 9 + kiCacheIdx;
  const uint16_t kuiRef16       = BUTTERFLY1x2 (kiRef);

  ST16 (&pCurMb->pRefIndex[ (kiPartIdx >> 2)], kuiRef16);
  memcpy (&pCurMb->sMv[kiScan4Idx], uiMvBuf, sizeof (uiMvBuf)); // confirmed_safe_unsafe_usage

  /*
  * blocks 0: g_kuiCache30ScanIdx[iPartIdx]~g_kuiCache30ScanIdx[iPartIdx]+3, 1: g_kuiCache30ScanIdx[iPartIdx]+6~g_kuiCache30ScanIdx[iPartIdx]+9
  */
  pMvComp->iRefIndexCache[kiCacheIdx]  = kiRef;
  ST16 (&pMvComp->iRefIndexCache[kiCacheIdx1], kuiRef16);
  pMvComp->iRefIndexCache[kiCacheIdx3] = kiRef;
  pMvComp->iRefIndexCache[kiCacheIdx6] = kiRef;
  ST16 (&pMvComp->iRefIndexCache[kiCacheIdx7], kuiRef16);
  pMvComp->iRefIndexCache[kiCacheIdx9] = kiRef;

  /*
  * blocks 0: g_kuiCache30ScanIdx[iPartIdx]~g_kuiCache30ScanIdx[iPartIdx]+3, 1: g_kuiCache30ScanIdx[iPartIdx]+6~g_kuiCache30ScanIdx[iPartIdx]+9
  */
  pMvComp->sMotionVectorCache[kiCacheIdx]  = *pMv;
  ST64 (&pMvComp->sMotionVectorCache[kiCacheIdx1], kuiMv64);
  pMvComp->sMotionVectorCache[kiCacheIdx3] = *pMv;
  pMvComp->sMotionVectorCache[kiCacheIdx6] = *pMv;
  ST64 (&pMvComp->sMotionVectorCache[kiCacheIdx7], kuiMv64);
  pMvComp->sMotionVectorCache[kiCacheIdx9] = *pMv;
}
//update uiRefIndex and pMv of both SMB and Mb_cache, only for P8x16
void update_P8x16_motion_info (SMbCache* pMbCache, SMB* pCurMb, const int32_t kiPartIdx, const int8_t kiRef,
                               SMVUnitXY* pMv) {
  // optimized 11/25/2011
  SMVComponentUnit* pMvComp     = &pMbCache->sMvComponents;
  const uint32_t kuiMv32        = LD32 (pMv);
  const uint64_t kuiMv64        = BUTTERFLY4x8 (kuiMv32);
  const int16_t kiScan4Idx      = g_kuiMbCountScan4Idx[kiPartIdx];
  const int16_t kiCacheIdx      = g_kuiCache30ScanIdx[kiPartIdx];
  const int16_t kiCacheIdx1     = 1 + kiCacheIdx;
  const int16_t kiCacheIdx3     = 3 + kiCacheIdx;
  const int16_t kiCacheIdx12    = 12 + kiCacheIdx;
  const int16_t kiCacheIdx13    = 13 + kiCacheIdx;
  const int16_t kiCacheIdx15    = 15 + kiCacheIdx;
  const int16_t kiBlkIdx        = kiPartIdx >> 2;
  const uint16_t kuiRef16       = BUTTERFLY1x2 (kiRef);

  pCurMb->pRefIndex[kiBlkIdx]     = kiRef;
  pCurMb->pRefIndex[2 + kiBlkIdx] = kiRef;
  ST64 (&pCurMb->sMv[kiScan4Idx], kuiMv64);
  ST64 (&pCurMb->sMv[4 + kiScan4Idx], kuiMv64);
  ST64 (&pCurMb->sMv[8 + kiScan4Idx], kuiMv64);
  ST64 (&pCurMb->sMv[12 + kiScan4Idx], kuiMv64);

  /*
  * blocks 0: g_kuiCache30ScanIdx[iPartIdx]~g_kuiCache30ScanIdx[iPartIdx]+3, 1: g_kuiCache30ScanIdx[iPartIdx]+6~g_kuiCache30ScanIdx[iPartIdx]+9
  */
  pMvComp->iRefIndexCache[kiCacheIdx]   = kiRef;
  ST16 (&pMvComp->iRefIndexCache[kiCacheIdx1], kuiRef16);
  pMvComp->iRefIndexCache[kiCacheIdx3]  = kiRef;
  pMvComp->iRefIndexCache[kiCacheIdx12] = kiRef;
  ST16 (&pMvComp->iRefIndexCache[kiCacheIdx13], kuiRef16);
  pMvComp->iRefIndexCache[kiCacheIdx15] = kiRef;

  /*
  * blocks 0: g_kuiCache30ScanIdx[iPartIdx]~g_kuiCache30ScanIdx[iPartIdx]+3, 1: g_kuiCache30ScanIdx[iPartIdx]+6~g_kuiCache30ScanIdx[iPartIdx]+9
  */
  pMvComp->sMotionVectorCache[kiCacheIdx]  = *pMv;
  ST64 (&pMvComp->sMotionVectorCache[kiCacheIdx1], kuiMv64);
  pMvComp->sMotionVectorCache[kiCacheIdx3] = *pMv;
  pMvComp->sMotionVectorCache[kiCacheIdx12] = *pMv;
  ST64 (&pMvComp->sMotionVectorCache[kiCacheIdx13], kuiMv64);
  pMvComp->sMotionVectorCache[kiCacheIdx15] = *pMv;
}
//update uiRefIndex and pMv of both SMB and Mb_cache, only for P8x8
void UpdateP8x8MotionInfo (SMbCache* pMbCache, SMB* pCurMb, const int32_t kiPartIdx, const int8_t kiRef,
                           SMVUnitXY* pMv) {
  SMVComponentUnit* pMvComp = &pMbCache->sMvComponents;
  const uint32_t kuiMv32        = LD32 (pMv);
  const uint64_t kuiMv64        = BUTTERFLY4x8 (kuiMv32);
  const int16_t kiScan4Idx      = g_kuiMbCountScan4Idx[kiPartIdx];
  const int16_t kiCacheIdx      = g_kuiCache30ScanIdx[kiPartIdx];
  const int16_t kiCacheIdx1     = 1 + kiCacheIdx;
  const int16_t kiCacheIdx6     = 6 + kiCacheIdx;
  const int16_t kiCacheIdx7     = 7 + kiCacheIdx;

  //mb
  ST64 (&pCurMb->sMv[  kiScan4Idx], kuiMv64);
  ST64 (&pCurMb->sMv[4 + kiScan4Idx], kuiMv64);

  //cache
  pMvComp->iRefIndexCache[kiCacheIdx ] =
    pMvComp->iRefIndexCache[kiCacheIdx1] =
      pMvComp->iRefIndexCache[kiCacheIdx6] =
        pMvComp->iRefIndexCache[kiCacheIdx7] = kiRef;
  pMvComp->sMotionVectorCache[kiCacheIdx ] =
    pMvComp->sMotionVectorCache[kiCacheIdx1] =
      pMvComp->sMotionVectorCache[kiCacheIdx6] =
        pMvComp->sMotionVectorCache[kiCacheIdx7] = *pMv;
}
//update uiRefIndex and pMv of both SMB and Mb_cache, only for P4x4
void UpdateP4x4MotionInfo (SMbCache* pMbCache, SMB* pCurMb, const int32_t kiPartIdx, const int8_t kiRef,
                           SMVUnitXY* pMv) {
  SMVComponentUnit* pMvComp = &pMbCache->sMvComponents;
  const int16_t kiScan4Idx  = g_kuiMbCountScan4Idx[kiPartIdx];
  const int16_t kiCacheIdx  = g_kuiCache30ScanIdx[kiPartIdx];

  //mb
  pCurMb->sMv[kiScan4Idx] = *pMv;
  //cache
  pMvComp->iRefIndexCache[kiCacheIdx] = kiRef;
  pMvComp->sMotionVectorCache[kiCacheIdx] = *pMv;
}
//update uiRefIndex and pMv of both SMB and Mb_cache, only for P8x4
void UpdateP8x4MotionInfo (SMbCache* pMbCache, SMB* pCurMb, const int32_t kiPartIdx, const int8_t kiRef,
                           SMVUnitXY* pMv) {
  SMVComponentUnit* pMvComp = &pMbCache->sMvComponents;
  const int16_t kiScan4Idx  = g_kuiMbCountScan4Idx[kiPartIdx];
  const int16_t kiCacheIdx  = g_kuiCache30ScanIdx[kiPartIdx];

  //mb
  pCurMb->sMv[    kiScan4Idx] = *pMv;
  pCurMb->sMv[1 + kiScan4Idx] = *pMv;
  //cache
  pMvComp->iRefIndexCache[    kiCacheIdx] = kiRef;
  pMvComp->iRefIndexCache[1 + kiCacheIdx] = kiRef;
  pMvComp->sMotionVectorCache[    kiCacheIdx] = *pMv;
  pMvComp->sMotionVectorCache[1 + kiCacheIdx] = *pMv;
}
//update uiRefIndex and pMv of both SMB and Mb_cache, only for P4x8
void UpdateP4x8MotionInfo (SMbCache* pMbCache, SMB* pCurMb, const int32_t kiPartIdx, const int8_t kiRef,
                           SMVUnitXY* pMv) {
  SMVComponentUnit* pMvComp = &pMbCache->sMvComponents;
  const int16_t kiScan4Idx  = g_kuiMbCountScan4Idx[kiPartIdx];
  const int16_t kiCacheIdx  = g_kuiCache30ScanIdx[kiPartIdx];

  //mb
  pCurMb->sMv[    kiScan4Idx] = *pMv;
  pCurMb->sMv[4 + kiScan4Idx] = *pMv;
  //cache
  pMvComp->iRefIndexCache[    kiCacheIdx] = kiRef;
  pMvComp->iRefIndexCache[6 + kiCacheIdx] = kiRef;
  pMvComp->sMotionVectorCache[    kiCacheIdx] = *pMv;
  pMvComp->sMotionVectorCache[6 + kiCacheIdx] = *pMv;
}
//=========================update motion info(MV and ref_idx) into Mb_cache==========================
//update pMv and uiRefIndex cache only for Mb_cache, only for P_16*16 (SKIP inclusive)

//update uiRefIndex and pMv of only Mb_cache, only for P16x8
void UpdateP16x8Motion2Cache (SMbCache* pMbCache, int32_t iPartIdx, int8_t iRef, SMVUnitXY* pMv) {
  SMVComponentUnit* pMvComp = &pMbCache->sMvComponents;
  int32_t i;

  for (i = 0; i < 2; i++, iPartIdx += 4) {
    //cache
    const uint8_t kuiCacheIdx = g_kuiCache30ScanIdx[iPartIdx];

    pMvComp->iRefIndexCache[  kuiCacheIdx] =
      pMvComp->iRefIndexCache[1 + kuiCacheIdx] =
        pMvComp->iRefIndexCache[6 + kuiCacheIdx] =
          pMvComp->iRefIndexCache[7 + kuiCacheIdx] = iRef;
    pMvComp->sMotionVectorCache[  kuiCacheIdx] =
      pMvComp->sMotionVectorCache[1 + kuiCacheIdx] =
        pMvComp->sMotionVectorCache[6 + kuiCacheIdx] =
          pMvComp->sMotionVectorCache[7 + kuiCacheIdx] = *pMv;
  }
}
//update uiRefIndex and pMv of only Mb_cache, only for P8x16
void UpdateP8x16Motion2Cache (SMbCache* pMbCache, int32_t iPartIdx, int8_t iRef, SMVUnitXY* pMv) {
  SMVComponentUnit* pMvComp = &pMbCache->sMvComponents;
  int32_t i;

  for (i = 0; i < 2; i++, iPartIdx += 8) {
    //cache
    const uint8_t kuiCacheIdx = g_kuiCache30ScanIdx[iPartIdx];

    pMvComp->iRefIndexCache[  kuiCacheIdx] =
      pMvComp->iRefIndexCache[1 + kuiCacheIdx] =
        pMvComp->iRefIndexCache[6 + kuiCacheIdx] =
          pMvComp->iRefIndexCache[7 + kuiCacheIdx] = iRef;
    pMvComp->sMotionVectorCache[  kuiCacheIdx] =
      pMvComp->sMotionVectorCache[1 + kuiCacheIdx] =
        pMvComp->sMotionVectorCache[6 + kuiCacheIdx] =
          pMvComp->sMotionVectorCache[7 + kuiCacheIdx] = *pMv;
  }
}

//update uiRefIndex and pMv of only Mb_cache, only for P8x8
void UpdateP8x8Motion2Cache (SMbCache* pMbCache, int32_t iPartIdx, int8_t pRef, SMVUnitXY* pMv) {
  SMVComponentUnit* pMvComp = &pMbCache->sMvComponents;
  const uint8_t kuiCacheIdx = g_kuiCache30ScanIdx[iPartIdx];

  pMvComp->iRefIndexCache[  kuiCacheIdx] =
    pMvComp->iRefIndexCache[1 + kuiCacheIdx] =
      pMvComp->iRefIndexCache[6 + kuiCacheIdx] =
        pMvComp->iRefIndexCache[7 + kuiCacheIdx] = pRef;
  pMvComp->sMotionVectorCache[  kuiCacheIdx] =
    pMvComp->sMotionVectorCache[1 + kuiCacheIdx] =
      pMvComp->sMotionVectorCache[6 + kuiCacheIdx] =
        pMvComp->sMotionVectorCache[7 + kuiCacheIdx] = *pMv;
}

//update uiRefIndex and pMv of only Mb_cache, for P4x4
void UpdateP4x4Motion2Cache (SMbCache* pMbCache, int32_t iPartIdx, int8_t pRef, SMVUnitXY* pMv) {
  SMVComponentUnit* pMvComp = &pMbCache->sMvComponents;
  const uint8_t kuiCacheIdx = g_kuiCache30ScanIdx[iPartIdx];

  pMvComp->iRefIndexCache    [kuiCacheIdx] = pRef;
  pMvComp->sMotionVectorCache[kuiCacheIdx] = *pMv;
}

//update uiRefIndex and pMv of only Mb_cache, for P8x4
void UpdateP8x4Motion2Cache (SMbCache* pMbCache, int32_t iPartIdx, int8_t pRef, SMVUnitXY* pMv) {
  SMVComponentUnit* pMvComp = &pMbCache->sMvComponents;
  const uint8_t kuiCacheIdx = g_kuiCache30ScanIdx[iPartIdx];

  pMvComp->iRefIndexCache      [    kuiCacheIdx] =
    pMvComp->iRefIndexCache    [1 + kuiCacheIdx] = pRef;
  pMvComp->sMotionVectorCache  [    kuiCacheIdx] =
    pMvComp->sMotionVectorCache[1 + kuiCacheIdx] = *pMv;
}

//update uiRefIndex and pMv of only Mb_cache, for P4x8
void UpdateP4x8Motion2Cache (SMbCache* pMbCache, int32_t iPartIdx, int8_t pRef, SMVUnitXY* pMv) {
  SMVComponentUnit* pMvComp = &pMbCache->sMvComponents;
  const uint8_t kuiCacheIdx = g_kuiCache30ScanIdx[iPartIdx];

  pMvComp->iRefIndexCache      [    kuiCacheIdx] =
    pMvComp->iRefIndexCache    [6 + kuiCacheIdx] = pRef;
  pMvComp->sMotionVectorCache  [    kuiCacheIdx] =
    pMvComp->sMotionVectorCache[6 + kuiCacheIdx] = *pMv;
}
} // namespace WelsEnc
