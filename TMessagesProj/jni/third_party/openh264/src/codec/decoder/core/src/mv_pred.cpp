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
#include "mb_cache.h"
#include "parse_mb_syn_cabac.h"

namespace WelsDec {

static inline  void SetRectBlock (void* vp, int32_t w, const int32_t h, int32_t stride, const uint32_t val,
                                  const int32_t size) {
  uint8_t* p = (uint8_t*)vp;
  w *= size;
  if (w == 1 && h == 4) {
    * (uint8_t*) (p + 0 * stride) =
      * (uint8_t*) (p + 1 * stride) =
        * (uint8_t*) (p + 2 * stride) =
          * (uint8_t*) (p + 3 * stride) = (uint8_t)val;
  } else if (w == 2 && h == 2) {
    * (uint16_t*) (p + 0 * stride) =
      * (uint16_t*) (p + 1 * stride) = size == 4 ? (uint16_t)val : (uint16_t) (val * 0x0101U);
  } else if (w == 2 && h == 4) {
    * (uint16_t*) (p + 0 * stride) =
      * (uint16_t*) (p + 1 * stride) =
        * (uint16_t*) (p + 2 * stride) =
          * (uint16_t*) (p + 3 * stride) = size == 4 ? (uint16_t)val : (uint16_t) (val * 0x0101U);
  } else if (w == 4 && h == 2) {
    * (uint32_t*) (p + 0 * stride) =
      * (uint32_t*) (p + 1 * stride) = size == 4 ? val : (uint32_t) (val * 0x01010101UL);
  } else if (w == 4 && h == 4) {
    * (uint32_t*) (p + 0 * stride) =
      * (uint32_t*) (p + 1 * stride) =
        * (uint32_t*) (p + 2 * stride) =
          * (uint32_t*) (p + 3 * stride) = size == 4 ? val : (uint32_t) (val * 0x01010101UL);
  } else if (w == 8 && h == 1) {
    * (uint32_t*) (p + 0 * stride) =
      * (uint32_t*) (p + 0 * stride + 4) = size == 4 ? val : (uint32_t) (val * 0x01010101UL);
  } else if (w == 8 && h == 2) {
    * (uint32_t*) (p + 0 * stride) =
      * (uint32_t*) (p + 0 * stride + 4) =
        * (uint32_t*) (p + 1 * stride) =
          * (uint32_t*) (p + 1 * stride + 4) = size == 4 ? val : (uint32_t) (val * 0x01010101UL);
  } else if (w == 8 && h == 4) {
    * (uint32_t*) (p + 0 * stride) =
      * (uint32_t*) (p + 0 * stride + 4) =
        * (uint32_t*) (p + 1 * stride) =
          * (uint32_t*) (p + 1 * stride + 4) =
            * (uint32_t*) (p + 2 * stride) =
              * (uint32_t*) (p + 2 * stride + 4) =
                * (uint32_t*) (p + 3 * stride) =
                  * (uint32_t*) (p + 3 * stride + 4) = size == 4 ? val : (uint32_t) (val * 0x01010101UL);
  } else if (w == 16 && h == 2) {
    * (uint32_t*) (p + 0 * stride + 0) =
      * (uint32_t*) (p + 0 * stride + 4) =
        * (uint32_t*) (p + 0 * stride + 8) =
          * (uint32_t*) (p + 0 * stride + 12) =
            * (uint32_t*) (p + 1 * stride + 0) =
              * (uint32_t*) (p + 1 * stride + 4) =
                * (uint32_t*) (p + 1 * stride + 8) =
                  * (uint32_t*) (p + 1 * stride + 12) = size == 4 ? val : (uint32_t) (val * 0x01010101UL);
  } else if (w == 16 && h == 3) {
    * (uint32_t*) (p + 0 * stride + 0) =
      * (uint32_t*) (p + 0 * stride + 4) =
        * (uint32_t*) (p + 0 * stride + 8) =
          * (uint32_t*) (p + 0 * stride + 12) =
            * (uint32_t*) (p + 1 * stride + 0) =
              * (uint32_t*) (p + 1 * stride + 4) =
                * (uint32_t*) (p + 1 * stride + 8) =
                  * (uint32_t*) (p + 1 * stride + 12) =
                    * (uint32_t*) (p + 2 * stride + 0) =
                      * (uint32_t*) (p + 2 * stride + 4) =
                        * (uint32_t*) (p + 2 * stride + 8) =
                          * (uint32_t*) (p + 2 * stride + 12) = size == 4 ? val : (uint32_t) (val * 0x01010101UL);
  } else if (w == 16 && h == 4) {
    * (uint32_t*) (p + 0 * stride + 0) =
      * (uint32_t*) (p + 0 * stride + 4) =
        * (uint32_t*) (p + 0 * stride + 8) =
          * (uint32_t*) (p + 0 * stride + 12) =
            * (uint32_t*) (p + 1 * stride + 0) =
              * (uint32_t*) (p + 1 * stride + 4) =
                * (uint32_t*) (p + 1 * stride + 8) =
                  * (uint32_t*) (p + 1 * stride + 12) =
                    * (uint32_t*) (p + 2 * stride + 0) =
                      * (uint32_t*) (p + 2 * stride + 4) =
                        * (uint32_t*) (p + 2 * stride + 8) =
                          * (uint32_t*) (p + 2 * stride + 12) =
                            * (uint32_t*) (p + 3 * stride + 0) =
                              * (uint32_t*) (p + 3 * stride + 4) =
                                * (uint32_t*) (p + 3 * stride + 8) =
                                  * (uint32_t*) (p + 3 * stride + 12) = size == 4 ? val : (uint32_t) (val * 0x01010101UL);
  }
}
void CopyRectBlock4Cols (void* vdst, void* vsrc, const int32_t stride_dst, const int32_t stride_src, int32_t w,
                         const int32_t size) {
  uint8_t* dst = (uint8_t*)vdst;
  uint8_t* src = (uint8_t*)vsrc;
  w *= size;
  if (w == 1) {
    dst[stride_dst * 0] = src[stride_src * 0];
    dst[stride_dst * 1] = src[stride_src * 1];
    dst[stride_dst * 2] = src[stride_src * 2];
    dst[stride_dst * 3] = src[stride_src * 3];
  } else if (w == 2) {
    * (uint16_t*) (&dst[stride_dst * 0]) = * (uint16_t*) (&src[stride_src * 0]);
    * (uint16_t*) (&dst[stride_dst * 1]) = * (uint16_t*) (&src[stride_src * 1]);
    * (uint16_t*) (&dst[stride_dst * 2]) = * (uint16_t*) (&src[stride_src * 2]);
    * (uint16_t*) (&dst[stride_dst * 3]) = * (uint16_t*) (&src[stride_src * 3]);
  } else if (w == 4) {
    * (uint32_t*) (&dst[stride_dst * 0]) = * (uint32_t*) (&src[stride_src * 0]);
    * (uint32_t*) (&dst[stride_dst * 1]) = * (uint32_t*) (&src[stride_src * 1]);
    * (uint32_t*) (&dst[stride_dst * 2]) = * (uint32_t*) (&src[stride_src * 2]);
    * (uint32_t*) (&dst[stride_dst * 3]) = * (uint32_t*) (&src[stride_src * 3]);
  } else if (w == 16) {
    memcpy (&dst[stride_dst * 0], &src[stride_src * 0], 16);
    memcpy (&dst[stride_dst * 1], &src[stride_src * 1], 16);
    memcpy (&dst[stride_dst * 2], &src[stride_src * 2], 16);
    memcpy (&dst[stride_dst * 3], &src[stride_src * 3], 16);
  }
}
void PredPSkipMvFromNeighbor (PDqLayer pCurDqLayer, int16_t iMvp[2]) {
  bool bTopAvail, bLeftTopAvail, bRightTopAvail, bLeftAvail;

  int32_t iCurSliceIdc, iTopSliceIdc, iLeftTopSliceIdc, iRightTopSliceIdc, iLeftSliceIdc;
  int32_t iLeftTopType, iRightTopType, iTopType, iLeftType;
  int32_t iCurX, iCurY, iCurXy, iLeftXy, iTopXy = 0, iLeftTopXy = 0, iRightTopXy = 0;

  int8_t iLeftRef;
  int8_t iTopRef;
  int8_t iRightTopRef;
  int8_t iLeftTopRef;
  int8_t iDiagonalRef;
  int8_t iMatchRef;
  int16_t iMvA[2], iMvB[2], iMvC[2], iMvD[2];

  iCurXy = pCurDqLayer->iMbXyIndex;
  iCurX  = pCurDqLayer->iMbX;
  iCurY  = pCurDqLayer->iMbY;
  iCurSliceIdc = pCurDqLayer->pSliceIdc[iCurXy];

  if (iCurX != 0) {
    iLeftXy = iCurXy - 1;
    iLeftSliceIdc = pCurDqLayer->pSliceIdc[iLeftXy];
    bLeftAvail = (iLeftSliceIdc == iCurSliceIdc);
  } else {
    bLeftAvail = 0;
    bLeftTopAvail = 0;
  }

  if (iCurY != 0) {
    iTopXy = iCurXy - pCurDqLayer->iMbWidth;
    iTopSliceIdc = pCurDqLayer->pSliceIdc[iTopXy];
    bTopAvail = (iTopSliceIdc == iCurSliceIdc);
    if (iCurX != 0) {
      iLeftTopXy = iTopXy - 1;
      iLeftTopSliceIdc = pCurDqLayer->pSliceIdc[iLeftTopXy];
      bLeftTopAvail = (iLeftTopSliceIdc  == iCurSliceIdc);
    } else {
      bLeftTopAvail = 0;
    }
    if (iCurX != (pCurDqLayer->iMbWidth - 1)) {
      iRightTopXy = iTopXy + 1;
      iRightTopSliceIdc = pCurDqLayer->pSliceIdc[iRightTopXy];
      bRightTopAvail = (iRightTopSliceIdc == iCurSliceIdc);
    } else {
      bRightTopAvail = 0;
    }
  } else {
    bTopAvail = 0;
    bLeftTopAvail = 0;
    bRightTopAvail = 0;
  }

  iLeftType = ((iCurX != 0 && bLeftAvail) ? GetMbType (pCurDqLayer)[iLeftXy] : 0);
  iTopType = ((iCurY != 0 && bTopAvail) ? GetMbType (pCurDqLayer)[iTopXy] : 0);
  iLeftTopType = ((iCurX != 0 && iCurY != 0 && bLeftTopAvail)
                  ? GetMbType (pCurDqLayer)[iLeftTopXy] : 0);
  iRightTopType = ((iCurX != pCurDqLayer->iMbWidth - 1 && iCurY != 0 && bRightTopAvail)
                   ? GetMbType (pCurDqLayer)[iRightTopXy] : 0);

  /*get neb mv&iRefIdxArray*/
  /*left*/
  if (bLeftAvail && IS_INTER (iLeftType)) {
    ST32 (iMvA, LD32 (pCurDqLayer->pDec ? pCurDqLayer->pDec->pMv[0][iLeftXy][3] : pCurDqLayer->pMv[0][iLeftXy][3]));
    iLeftRef = pCurDqLayer->pDec ? pCurDqLayer->pDec->pRefIndex[0][iLeftXy][3] : pCurDqLayer->pRefIndex[0][iLeftXy][3];
  } else {
    ST32 (iMvA, 0);
    if (0 == bLeftAvail) { //not available
      iLeftRef = REF_NOT_AVAIL;
    } else { //available but is intra mb type
      iLeftRef = REF_NOT_IN_LIST;
    }
  }
  if (REF_NOT_AVAIL == iLeftRef ||
      (0 == iLeftRef && 0 == * (int32_t*)iMvA)) {
    ST32 (iMvp, 0);
    return;
  }

  /*top*/
  if (bTopAvail && IS_INTER (iTopType)) {
    ST32 (iMvB, LD32 (pCurDqLayer->pDec ? pCurDqLayer->pDec->pMv[0][iTopXy][12] : pCurDqLayer->pMv[0][iTopXy][12]));
    iTopRef = pCurDqLayer->pDec ? pCurDqLayer->pDec->pRefIndex[0][iTopXy][12] : pCurDqLayer->pRefIndex[0][iTopXy][12];
  } else {
    ST32 (iMvB, 0);
    if (0 == bTopAvail) { //not available
      iTopRef = REF_NOT_AVAIL;
    } else { //available but is intra mb type
      iTopRef = REF_NOT_IN_LIST;
    }
  }
  if (REF_NOT_AVAIL == iTopRef ||
      (0 == iTopRef  && 0 == * (int32_t*)iMvB)) {
    ST32 (iMvp, 0);
    return;
  }

  /*right_top*/
  if (bRightTopAvail && IS_INTER (iRightTopType)) {
    ST32 (iMvC, LD32 (pCurDqLayer->pDec ? pCurDqLayer->pDec->pMv[0][iRightTopXy][12] :
                      pCurDqLayer->pMv[0][iRightTopXy][12]));
    iRightTopRef = pCurDqLayer->pDec ? pCurDqLayer->pDec->pRefIndex[0][iRightTopXy][12] :
                   pCurDqLayer->pRefIndex[0][iRightTopXy][12];
  } else {
    ST32 (iMvC, 0);
    if (0 == bRightTopAvail) { //not available
      iRightTopRef = REF_NOT_AVAIL;
    } else { //available but is intra mb type
      iRightTopRef = REF_NOT_IN_LIST;
    }
  }

  /*left_top*/
  if (bLeftTopAvail && IS_INTER (iLeftTopType)) {
    ST32 (iMvD, LD32 (pCurDqLayer->pDec ? pCurDqLayer->pDec->pMv[0][iLeftTopXy][15] : pCurDqLayer->pMv[0][iLeftTopXy][15]));
    iLeftTopRef = pCurDqLayer->pDec ? pCurDqLayer->pDec->pRefIndex[0][iLeftTopXy][15] :
                  pCurDqLayer->pRefIndex[0][iLeftTopXy][15];
  } else {
    ST32 (iMvD, 0);
    if (0 == bLeftTopAvail) { //not available
      iLeftTopRef = REF_NOT_AVAIL;
    } else { //available but is intra mb type
      iLeftTopRef = REF_NOT_IN_LIST;
    }
  }

  iDiagonalRef = iRightTopRef;
  if (REF_NOT_AVAIL == iDiagonalRef) {
    iDiagonalRef = iLeftTopRef;
    * (int32_t*)iMvC = * (int32_t*)iMvD;
  }

  if (REF_NOT_AVAIL == iTopRef && REF_NOT_AVAIL == iDiagonalRef && iLeftRef >= REF_NOT_IN_LIST) {
    ST32 (iMvp, LD32 (iMvA));
    return;
  }

  iMatchRef = (0 == iLeftRef) + (0 == iTopRef) + (0 == iDiagonalRef);
  if (1 == iMatchRef) {
    if (0 == iLeftRef) {
      ST32 (iMvp, LD32 (iMvA));
    } else if (0 == iTopRef) {
      ST32 (iMvp, LD32 (iMvB));
    } else {
      ST32 (iMvp, LD32 (iMvC));
    }
  } else {
    iMvp[0] = WelsMedian (iMvA[0], iMvB[0], iMvC[0]);
    iMvp[1] = WelsMedian (iMvA[1], iMvB[1], iMvC[1]);
  }
}

int32_t GetColocatedMb (PWelsDecoderContext pCtx, MbType& mbType, SubMbType& subMbType) {
  PDqLayer pCurDqLayer = pCtx->pCurDqLayer;
  int32_t iMbXy = pCurDqLayer->iMbXyIndex;

  uint32_t is8x8 = IS_Inter_8x8 (GetMbType (pCurDqLayer)[iMbXy]);
  mbType = GetMbType (pCurDqLayer)[iMbXy];

  PPicture colocPic = pCtx->sRefPic.pRefList[LIST_1][0];
  if (GetThreadCount (pCtx) > 1) {
    if (16 * pCurDqLayer->iMbY > pCtx->lastReadyHeightOffset[1][0]) {
      if (colocPic->pReadyEvent[pCurDqLayer->iMbY].isSignaled != 1) {
        WAIT_EVENT (&colocPic->pReadyEvent[pCurDqLayer->iMbY], WELS_DEC_THREAD_WAIT_INFINITE);
      }
      pCtx->lastReadyHeightOffset[1][0] = 16 * pCurDqLayer->iMbY;
    }
  }

  if (colocPic == NULL) {
    SLogContext* pLogCtx = & (pCtx->sLogCtx);
    WelsLog (pLogCtx, WELS_LOG_ERROR, "Colocated Ref Picture for B-Slice is lost, B-Slice decoding cannot be continued!");
    return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_DATA, ERR_INFO_REFERENCE_PIC_LOST);
  }

  MbType coloc_mbType = colocPic->pMbType[iMbXy];
  if (coloc_mbType == MB_TYPE_SKIP) {
    //This indicates the colocated MB is P SKIP MB
    coloc_mbType |= MB_TYPE_16x16 | MB_TYPE_P0L0 | MB_TYPE_P1L0;
  }
  if (IS_Inter_8x8 (coloc_mbType) && !pCtx->pSps->bDirect8x8InferenceFlag) {
    subMbType = SUB_MB_TYPE_4x4 | MB_TYPE_P0L0 | MB_TYPE_P0L1 | MB_TYPE_DIRECT;
    mbType |= MB_TYPE_8x8 | MB_TYPE_L0 | MB_TYPE_L1;
  } else if (!is8x8 && (IS_INTER_16x16 (coloc_mbType) || IS_INTRA (coloc_mbType)/* || IS_SKIP(coloc_mbType)*/)) {
    subMbType = SUB_MB_TYPE_8x8 | MB_TYPE_P0L0 | MB_TYPE_P0L1 | MB_TYPE_DIRECT;
    mbType |= MB_TYPE_16x16 | MB_TYPE_L0 | MB_TYPE_L1;
  } else {
    subMbType = SUB_MB_TYPE_8x8 | MB_TYPE_P0L0 | MB_TYPE_P0L1 | MB_TYPE_DIRECT;
    mbType |= MB_TYPE_8x8 | MB_TYPE_L0 | MB_TYPE_L1;
  }

  if (IS_INTRA (coloc_mbType)) {
    SetRectBlock (pCurDqLayer->iColocIntra, 4, 4, 4 * sizeof (int8_t), 1, sizeof (int8_t));
    return ERR_NONE;
  }
  SetRectBlock (pCurDqLayer->iColocIntra, 4, 4, 4 * sizeof (int8_t), 0, sizeof (int8_t));

  if (IS_INTER_16x16 (mbType)) {
    int16_t iMVZero[2] = { 0 };
    int16_t* pMv = IS_TYPE_L1 (coloc_mbType) ? colocPic->pMv[LIST_1][iMbXy][0] : iMVZero;
    ST32 (pCurDqLayer->iColocMv[LIST_0][0], LD32 (colocPic->pMv[LIST_0][iMbXy][0]));
    ST32 (pCurDqLayer->iColocMv[LIST_1][0], LD32 (pMv));
    pCurDqLayer->iColocRefIndex[LIST_0][0] = colocPic->pRefIndex[LIST_0][iMbXy][0];
    pCurDqLayer->iColocRefIndex[LIST_1][0] = IS_TYPE_L1 (coloc_mbType) ? colocPic->pRefIndex[LIST_1][iMbXy][0] :
        REF_NOT_IN_LIST;
  } else {
    if (!pCtx->pSps->bDirect8x8InferenceFlag) {
      CopyRectBlock4Cols (pCurDqLayer->iColocMv[LIST_0], colocPic->pMv[LIST_0][iMbXy], 16, 16, 4, 4);
      CopyRectBlock4Cols (pCurDqLayer->iColocRefIndex[LIST_0], colocPic->pRefIndex[LIST_0][iMbXy], 4, 4, 4, 1);
      if (IS_TYPE_L1 (coloc_mbType)) {
        CopyRectBlock4Cols (pCurDqLayer->iColocMv[LIST_1], colocPic->pMv[LIST_1][iMbXy], 16, 16, 4, 4);
        CopyRectBlock4Cols (pCurDqLayer->iColocRefIndex[LIST_1], colocPic->pRefIndex[LIST_1][iMbXy], 4, 4, 4, 1);
      } else { // only forward prediction
        SetRectBlock (pCurDqLayer->iColocRefIndex[LIST_1], 4, 4, 4, (uint8_t)REF_NOT_IN_LIST, 1);
      }
    } else {
      for (int32_t listIdx = 0; listIdx < 1 + !! (coloc_mbType & MB_TYPE_L1); listIdx++) {
        SetRectBlock (pCurDqLayer->iColocMv[listIdx][0], 2, 2, 16, LD32 (colocPic->pMv[listIdx][iMbXy][0]), 4);
        SetRectBlock (pCurDqLayer->iColocMv[listIdx][2], 2, 2, 16, LD32 (colocPic->pMv[listIdx][iMbXy][3]), 4);
        SetRectBlock (pCurDqLayer->iColocMv[listIdx][8], 2, 2, 16, LD32 (colocPic->pMv[listIdx][iMbXy][12]), 4);
        SetRectBlock (pCurDqLayer->iColocMv[listIdx][10], 2, 2, 16, LD32 (colocPic->pMv[listIdx][iMbXy][15]), 4);

        SetRectBlock (&pCurDqLayer->iColocRefIndex[listIdx][0], 2, 2, 4, colocPic->pRefIndex[listIdx][iMbXy][0], 1);
        SetRectBlock (&pCurDqLayer->iColocRefIndex[listIdx][2], 2, 2, 4, colocPic->pRefIndex[listIdx][iMbXy][3], 1);
        SetRectBlock (&pCurDqLayer->iColocRefIndex[listIdx][8], 2, 2, 4, colocPic->pRefIndex[listIdx][iMbXy][12], 1);
        SetRectBlock (&pCurDqLayer->iColocRefIndex[listIdx][10], 2, 2, 4, colocPic->pRefIndex[listIdx][iMbXy][15], 1);
      }
      if (! (coloc_mbType & MB_TYPE_L1)) // only forward prediction
        SetRectBlock (&pCurDqLayer->iColocRefIndex[1][0], 4, 4, 4, (uint8_t)REF_NOT_IN_LIST, 1);
    }
  }
  return ERR_NONE;
}

int32_t PredMvBDirectSpatial (PWelsDecoderContext pCtx, int16_t iMvp[LIST_A][2], int8_t ref[LIST_A],
                              SubMbType& subMbType) {

  int32_t ret = ERR_NONE;
  PDqLayer pCurDqLayer = pCtx->pCurDqLayer;
  int32_t iMbXy = pCurDqLayer->iMbXyIndex;
  bool bSkipOrDirect = (IS_SKIP (GetMbType (pCurDqLayer)[iMbXy]) | IS_DIRECT (GetMbType (pCurDqLayer)[iMbXy])) > 0;

  MbType mbType;
  ret = GetColocatedMb (pCtx, mbType, subMbType);
  if (ret != ERR_NONE) {
    return ret;
  }

  bool bTopAvail, bLeftTopAvail, bRightTopAvail, bLeftAvail;
  int32_t iLeftTopType, iRightTopType, iTopType, iLeftType;
  int32_t iCurSliceIdc, iTopSliceIdc, iLeftTopSliceIdc, iRightTopSliceIdc, iLeftSliceIdc;
  int32_t iCurX, iCurY, iCurXy, iLeftXy = 0, iTopXy = 0, iLeftTopXy = 0, iRightTopXy = 0;

  int8_t iLeftRef[LIST_A];
  int8_t iTopRef[LIST_A];
  int8_t iRightTopRef[LIST_A];
  int8_t iLeftTopRef[LIST_A];
  int8_t iDiagonalRef[LIST_A];
  int16_t iMvA[LIST_A][2], iMvB[LIST_A][2], iMvC[LIST_A][2], iMvD[LIST_A][2];

  iCurXy = pCurDqLayer->iMbXyIndex;

  iCurX = pCurDqLayer->iMbX;
  iCurY = pCurDqLayer->iMbY;
  iCurSliceIdc = pCurDqLayer->pSliceIdc[iCurXy];

  if (iCurX != 0) {
    iLeftXy = iCurXy - 1;
    iLeftSliceIdc = pCurDqLayer->pSliceIdc[iLeftXy];
    bLeftAvail = (iLeftSliceIdc == iCurSliceIdc);
  } else {
    bLeftAvail = 0;
    bLeftTopAvail = 0;
  }

  if (iCurY != 0) {
    iTopXy = iCurXy - pCurDqLayer->iMbWidth;
    iTopSliceIdc = pCurDqLayer->pSliceIdc[iTopXy];
    bTopAvail = (iTopSliceIdc == iCurSliceIdc);
    if (iCurX != 0) {
      iLeftTopXy = iTopXy - 1;
      iLeftTopSliceIdc = pCurDqLayer->pSliceIdc[iLeftTopXy];
      bLeftTopAvail = (iLeftTopSliceIdc == iCurSliceIdc);
    } else {
      bLeftTopAvail = 0;
    }
    if (iCurX != (pCurDqLayer->iMbWidth - 1)) {
      iRightTopXy = iTopXy + 1;
      iRightTopSliceIdc = pCurDqLayer->pSliceIdc[iRightTopXy];
      bRightTopAvail = (iRightTopSliceIdc == iCurSliceIdc);
    } else {
      bRightTopAvail = 0;
    }
  } else {
    bTopAvail = 0;
    bLeftTopAvail = 0;
    bRightTopAvail = 0;
  }

  iLeftType = ((iCurX != 0 && bLeftAvail) ? GetMbType (pCurDqLayer)[iLeftXy] : 0);
  iTopType = ((iCurY != 0 && bTopAvail) ? GetMbType (pCurDqLayer)[iTopXy] : 0);
  iLeftTopType = ((iCurX != 0 && iCurY != 0 && bLeftTopAvail)
                  ? GetMbType (pCurDqLayer)[iLeftTopXy] : 0);
  iRightTopType = ((iCurX != pCurDqLayer->iMbWidth - 1 && iCurY != 0 && bRightTopAvail)
                   ? GetMbType (pCurDqLayer)[iRightTopXy] : 0);

  /*get neb mv&iRefIdxArray*/
  for (int32_t listIdx = LIST_0; listIdx < LIST_A; ++listIdx) {

    /*left*/
    if (bLeftAvail && IS_INTER (iLeftType)) {
      ST32 (iMvA[listIdx], LD32 (pCurDqLayer->pDec ? pCurDqLayer->pDec->pMv[listIdx][iLeftXy][3] :
                                 pCurDqLayer->pMv[listIdx][iLeftXy][3]));
      iLeftRef[listIdx] = pCurDqLayer->pDec ? pCurDqLayer->pDec->pRefIndex[listIdx][iLeftXy][3] :
                          pCurDqLayer->pRefIndex[listIdx][iLeftXy][3];
    } else {
      ST32 (iMvA[listIdx], 0);
      if (0 == bLeftAvail) { //not available
        iLeftRef[listIdx] = REF_NOT_AVAIL;
      } else { //available but is intra mb type
        iLeftRef[listIdx] = REF_NOT_IN_LIST;
      }
    }

    /*top*/
    if (bTopAvail && IS_INTER (iTopType)) {
      ST32 (iMvB[listIdx], LD32 (pCurDqLayer->pDec ? pCurDqLayer->pDec->pMv[listIdx][iTopXy][12] :
                                 pCurDqLayer->pMv[listIdx][iTopXy][12]));
      iTopRef[listIdx] = pCurDqLayer->pDec ? pCurDqLayer->pDec->pRefIndex[listIdx][iTopXy][12] :
                         pCurDqLayer->pRefIndex[listIdx][iTopXy][12];
    } else {
      ST32 (iMvB[listIdx], 0);
      if (0 == bTopAvail) { //not available
        iTopRef[listIdx] = REF_NOT_AVAIL;
      } else { //available but is intra mb type
        iTopRef[listIdx] = REF_NOT_IN_LIST;
      }
    }

    /*right_top*/
    if (bRightTopAvail && IS_INTER (iRightTopType)) {
      ST32 (iMvC[listIdx], LD32 (pCurDqLayer->pDec ? pCurDqLayer->pDec->pMv[listIdx][iRightTopXy][12] :
                                 pCurDqLayer->pMv[listIdx][iRightTopXy][12]));
      iRightTopRef[listIdx] = pCurDqLayer->pDec ? pCurDqLayer->pDec->pRefIndex[listIdx][iRightTopXy][12] :
                              pCurDqLayer->pRefIndex[listIdx][iRightTopXy][12];
    } else {
      ST32 (iMvC[listIdx], 0);
      if (0 == bRightTopAvail) { //not available
        iRightTopRef[listIdx] = REF_NOT_AVAIL;
      } else { //available but is intra mb type
        iRightTopRef[listIdx] = REF_NOT_IN_LIST;
      }
    }
    /*left_top*/
    if (bLeftTopAvail && IS_INTER (iLeftTopType)) {
      ST32 (iMvD[listIdx], LD32 (pCurDqLayer->pDec ? pCurDqLayer->pDec->pMv[listIdx][iLeftTopXy][15] :
                                 pCurDqLayer->pMv[listIdx][iLeftTopXy][15]));
      iLeftTopRef[listIdx] = pCurDqLayer->pDec ? pCurDqLayer->pDec->pRefIndex[listIdx][iLeftTopXy][15] :
                             pCurDqLayer->pRefIndex[listIdx][iLeftTopXy][15];
    } else {
      ST32 (iMvD[listIdx], 0);
      if (0 == bLeftTopAvail) { //not available
        iLeftTopRef[listIdx] = REF_NOT_AVAIL;
      } else { //available but is intra mb type
        iLeftTopRef[listIdx] = REF_NOT_IN_LIST;
      }
    }

    iDiagonalRef[listIdx] = iRightTopRef[listIdx];
    if (REF_NOT_AVAIL == iDiagonalRef[listIdx]) {
      iDiagonalRef[listIdx] = iLeftTopRef[listIdx];
      ST32 (iMvC[listIdx], LD32 (iMvD[listIdx]));
    }

    int8_t ref_temp = WELS_MIN_POSITIVE (iTopRef[listIdx], iDiagonalRef[listIdx]);
    ref[listIdx] = WELS_MIN_POSITIVE (iLeftRef[listIdx], ref_temp);
    if (ref[listIdx] >= 0) {

      uint32_t match_count = (iLeftRef[listIdx] == ref[listIdx]) + (iTopRef[listIdx] == ref[listIdx]) +
                             (iDiagonalRef[listIdx] == ref[listIdx]);
      if (match_count == 1) {
        if (iLeftRef[listIdx] == ref[listIdx]) {
          ST32 (iMvp[listIdx], LD32 (iMvA[listIdx]));
        } else if (iTopRef[listIdx] == ref[listIdx]) {
          ST32 (iMvp[listIdx], LD32 (iMvB[listIdx]));
        } else {
          ST32 (iMvp[listIdx], LD32 (iMvC[listIdx]));
        }
      } else {
        iMvp[listIdx][0] = WelsMedian (iMvA[listIdx][0], iMvB[listIdx][0], iMvC[listIdx][0]);
        iMvp[listIdx][1] = WelsMedian (iMvA[listIdx][1], iMvB[listIdx][1], iMvC[listIdx][1]);
      }
    } else {
      iMvp[listIdx][0] = 0;
      iMvp[listIdx][1] = 0;
      ref[listIdx] = REF_NOT_IN_LIST;
    }
  }
  if (ref[LIST_0] <= REF_NOT_IN_LIST && ref[LIST_1] <= REF_NOT_IN_LIST) {
    ref[LIST_0] = ref[LIST_1] = 0;
  } else if (ref[LIST_1] < 0) {
    mbType &= ~MB_TYPE_L1;
    subMbType &= ~MB_TYPE_L1;
  } else if (ref[LIST_0] < 0) {
    mbType &= ~MB_TYPE_L0;
    subMbType &= ~MB_TYPE_L0;
  }
  GetMbType (pCurDqLayer)[iMbXy] = mbType;

  int16_t pMvd[4] = { 0 };

  bool bIsLongRef = pCtx->sRefPic.pRefList[LIST_1][0]->bIsLongRef;

  if (IS_INTER_16x16 (mbType)) {
    if ((* (int32_t*)iMvp[LIST_0] | * (int32_t*)iMvp[LIST_1])) {
      if (0 == pCurDqLayer->iColocIntra[0] && !bIsLongRef
          && ((pCurDqLayer->iColocRefIndex[LIST_0][0] == 0 && (unsigned) (pCurDqLayer->iColocMv[LIST_0][0][0] + 1) <= 2
               && (unsigned) (pCurDqLayer->iColocMv[LIST_0][0][1] + 1) <= 2)
              || (pCurDqLayer->iColocRefIndex[LIST_0][0] < 0 && pCurDqLayer->iColocRefIndex[LIST_1][0] == 0
                  && (unsigned) (pCurDqLayer->iColocMv[LIST_1][0][0] + 1) <= 2
                  && (unsigned) (pCurDqLayer->iColocMv[LIST_1][0][1] + 1) <= 2))) {
        if (0 >= ref[0])  * (uint32_t*)iMvp[LIST_0] = 0;
        if (0 >= ref[1])  * (uint32_t*)iMvp[LIST_1] = 0;
      }
    }
    UpdateP16x16DirectCabac (pCurDqLayer);
    for (int32_t listIdx = LIST_0; listIdx < LIST_A; ++listIdx) {
      UpdateP16x16MotionInfo (pCurDqLayer, listIdx, ref[listIdx], iMvp[listIdx]);
      UpdateP16x16MvdCabac (pCurDqLayer, pMvd, listIdx);
    }
  } else {
    if (bSkipOrDirect) {
      int8_t pSubPartCount[4], pPartW[4];
      for (int32_t i = 0; i < 4; i++) { //Direct 8x8 Ref and mv
        int16_t iIdx8 = i << 2;
        pCurDqLayer->pSubMbType[iMbXy][i] = subMbType;
        int8_t pRefIndex[LIST_A][30];
        UpdateP8x8RefIdxCabac (pCurDqLayer, pRefIndex, iIdx8, ref[LIST_0], LIST_0);
        UpdateP8x8RefIdxCabac (pCurDqLayer, pRefIndex, iIdx8, ref[LIST_1], LIST_1);
        UpdateP8x8DirectCabac (pCurDqLayer, iIdx8);

        pSubPartCount[i] = g_ksInterBSubMbTypeInfo[0].iPartCount;
        pPartW[i] = g_ksInterBSubMbTypeInfo[0].iPartWidth;

        if (IS_SUB_4x4 (subMbType)) {
          pSubPartCount[i] = 4;
          pPartW[i] = 1;
        }
        FillSpatialDirect8x8Mv (pCurDqLayer, iIdx8, pSubPartCount[i], pPartW[i], subMbType, bIsLongRef, iMvp, ref, NULL, NULL);
      }
    }
  }
  return ret;
}

int32_t PredBDirectTemporal (PWelsDecoderContext pCtx, int16_t iMvp[LIST_A][2], int8_t ref[LIST_A],
                             SubMbType& subMbType) {
  int32_t ret = ERR_NONE;
  PDqLayer pCurDqLayer = pCtx->pCurDqLayer;
  int32_t iMbXy = pCurDqLayer->iMbXyIndex;
  bool bSkipOrDirect = (IS_SKIP (GetMbType (pCurDqLayer)[iMbXy]) | IS_DIRECT (GetMbType (pCurDqLayer)[iMbXy])) > 0;

  MbType mbType;
  ret = GetColocatedMb (pCtx, mbType, subMbType);
  if (ret != ERR_NONE) {
    return ret;
  }

  GetMbType (pCurDqLayer)[iMbXy] = mbType;

  PSlice pSlice = &pCurDqLayer->sLayerInfo.sSliceInLayer;
  PSliceHeader pSliceHeader = &pSlice->sSliceHeaderExt.sSliceHeader;
  int16_t pMvd[4] = { 0 };
  const int32_t ref0Count = WELS_MIN (pSliceHeader->uiRefCount[LIST_0], pCtx->sRefPic.uiRefCount[LIST_0]);
  if (IS_INTER_16x16 (mbType)) {
    ref[LIST_0] = 0;
    ref[LIST_1] = 0;
    UpdateP16x16DirectCabac (pCurDqLayer);
    UpdateP16x16RefIdx (pCurDqLayer, LIST_1, ref[LIST_1]);
    ST64 (iMvp,  0);
    if (pCurDqLayer->iColocIntra[0]) {
      UpdateP16x16MotionOnly (pCurDqLayer, LIST_0, iMvp[LIST_0]);
      UpdateP16x16MotionOnly (pCurDqLayer, LIST_1, iMvp[LIST_1]);
      UpdateP16x16RefIdx (pCurDqLayer, LIST_0, ref[LIST_0]);
    } else {
      ref[LIST_0] = 0;
      int16_t* mv = pCurDqLayer->iColocMv[LIST_0][0];
      int8_t colocRefIndexL0 = pCurDqLayer->iColocRefIndex[LIST_0][0];
      if (colocRefIndexL0 >= 0) {
        ref[LIST_0] = MapColToList0 (pCtx, colocRefIndexL0, ref0Count);
      } else {
        mv = pCurDqLayer->iColocMv[LIST_1][0];
      }
      UpdateP16x16RefIdx (pCurDqLayer, LIST_0, ref[LIST_0]);

      iMvp[LIST_0][0] = (pSlice->iMvScale[LIST_0][ref[LIST_0]] * mv[0] + 128) >> 8;
      iMvp[LIST_0][1] = (pSlice->iMvScale[LIST_0][ref[LIST_0]] * mv[1] + 128) >> 8;
      UpdateP16x16MotionOnly (pCurDqLayer, LIST_0, iMvp[LIST_0]);
      iMvp[LIST_1][0] = iMvp[LIST_0][0] - mv[0];
      iMvp[LIST_1][1] = iMvp[LIST_0][1] - mv[1];
      UpdateP16x16MotionOnly (pCurDqLayer, LIST_1, iMvp[LIST_1]);
    }
    UpdateP16x16MvdCabac (pCurDqLayer, pMvd, LIST_0);
    UpdateP16x16MvdCabac (pCurDqLayer, pMvd, LIST_1);
  } else {
    if (bSkipOrDirect) {
      int8_t pSubPartCount[4], pPartW[4];
      int8_t pRefIndex[LIST_A][30];
      for (int32_t i = 0; i < 4; i++) {
        int16_t iIdx8 = i << 2;
        const uint8_t iScan4Idx = g_kuiScan4[iIdx8];
        pCurDqLayer->pSubMbType[iMbXy][i] = subMbType;

        int16_t (*mvColoc)[2] = pCurDqLayer->iColocMv[LIST_0];

        ref[LIST_1] = 0;
        UpdateP8x8RefIdxCabac (pCurDqLayer, pRefIndex, iIdx8, ref[LIST_1], LIST_1);
        if (pCurDqLayer->iColocIntra[iScan4Idx]) {
          ref[LIST_0] = 0;
          UpdateP8x8RefIdxCabac (pCurDqLayer, pRefIndex, iIdx8, ref[LIST_0], LIST_0);
          ST64 (iMvp, 0);
        } else {
          ref[LIST_0] = 0;
          int8_t colocRefIndexL0 = pCurDqLayer->iColocRefIndex[LIST_0][iScan4Idx];
          if (colocRefIndexL0 >= 0) {
            ref[LIST_0] = MapColToList0 (pCtx, colocRefIndexL0, ref0Count);
          } else {
            mvColoc = pCurDqLayer->iColocMv[LIST_1];
          }
          UpdateP8x8RefIdxCabac (pCurDqLayer, pRefIndex, iIdx8, ref[LIST_0], LIST_0);
        }
        UpdateP8x8DirectCabac (pCurDqLayer, iIdx8);

        pSubPartCount[i] = g_ksInterBSubMbTypeInfo[0].iPartCount;
        pPartW[i] = g_ksInterBSubMbTypeInfo[0].iPartWidth;

        if (IS_SUB_4x4 (subMbType)) {
          pSubPartCount[i] = 4;
          pPartW[i] = 1;
        }
        FillTemporalDirect8x8Mv (pCurDqLayer, iIdx8, pSubPartCount[i], pPartW[i], subMbType, ref, mvColoc, NULL, NULL);
      }
    }
  }
  return ret;
}

//basic iMVs prediction unit for iMVs partition width (4, 2, 1)
void PredMv (int16_t iMotionVector[LIST_A][30][MV_A], int8_t iRefIndex[LIST_A][30],
             int32_t listIdx, int32_t iPartIdx, int32_t iPartWidth, int8_t iRef, int16_t iMVP[2]) {
  const uint8_t kuiLeftIdx      = g_kuiCache30ScanIdx[iPartIdx] - 1;
  const uint8_t kuiTopIdx       = g_kuiCache30ScanIdx[iPartIdx] - 6;
  const uint8_t kuiRightTopIdx  = kuiTopIdx + iPartWidth;
  const uint8_t kuiLeftTopIdx   = kuiTopIdx - 1;

  const int8_t kiLeftRef      = iRefIndex[listIdx][kuiLeftIdx];
  const int8_t kiTopRef       = iRefIndex[listIdx][ kuiTopIdx];
  const int8_t kiRightTopRef  = iRefIndex[listIdx][kuiRightTopIdx];
  const int8_t kiLeftTopRef   = iRefIndex[listIdx][ kuiLeftTopIdx];
  int8_t iDiagonalRef  = kiRightTopRef;

  int8_t iMatchRef = 0;


  int16_t iAMV[2], iBMV[2], iCMV[2];

  ST32 (iAMV, LD32 (iMotionVector[listIdx][     kuiLeftIdx]));
  ST32 (iBMV, LD32 (iMotionVector[listIdx][      kuiTopIdx]));
  ST32 (iCMV, LD32 (iMotionVector[listIdx][kuiRightTopIdx]));

  if (REF_NOT_AVAIL == iDiagonalRef) {
    iDiagonalRef = kiLeftTopRef;
    ST32 (iCMV, LD32 (iMotionVector[listIdx][kuiLeftTopIdx]));
  }

  iMatchRef = (iRef == kiLeftRef) + (iRef == kiTopRef) + (iRef == iDiagonalRef);

  if (REF_NOT_AVAIL == kiTopRef && REF_NOT_AVAIL == iDiagonalRef && kiLeftRef >= REF_NOT_IN_LIST) {
    ST32 (iMVP, LD32 (iAMV));
    return;
  }

  if (1 == iMatchRef) {
    if (iRef == kiLeftRef) {
      ST32 (iMVP, LD32 (iAMV));
    } else if (iRef == kiTopRef) {
      ST32 (iMVP, LD32 (iBMV));
    } else {
      ST32 (iMVP, LD32 (iCMV));
    }
  } else {
    iMVP[0] = WelsMedian (iAMV[0], iBMV[0], iCMV[0]);
    iMVP[1] = WelsMedian (iAMV[1], iBMV[1], iCMV[1]);
  }
}
void PredInter8x16Mv (int16_t iMotionVector[LIST_A][30][MV_A], int8_t iRefIndex[LIST_A][30],
                      int32_t listIdx, int32_t iPartIdx, int8_t iRef, int16_t iMVP[2]) {
  if (0 == iPartIdx) {
    const int8_t kiLeftRef = iRefIndex[listIdx][6];
    if (iRef == kiLeftRef) {
      ST32 (iMVP, LD32 (&iMotionVector[listIdx][6][0]));
      return;
    }
  } else { // 1 == iPartIdx
    int8_t iDiagonalRef = iRefIndex[listIdx][5]; //top-right
    int8_t index = 5;
    if (REF_NOT_AVAIL == iDiagonalRef) {
      iDiagonalRef = iRefIndex[listIdx][2]; //top-left for 8*8 block(index 1)
      index = 2;
    }
    if (iRef == iDiagonalRef) {
      ST32 (iMVP, LD32 (&iMotionVector[listIdx][index][0]));
      return;
    }
  }

  PredMv (iMotionVector, iRefIndex, listIdx, iPartIdx, 2, iRef, iMVP);
}
void PredInter16x8Mv (int16_t iMotionVector[LIST_A][30][MV_A], int8_t iRefIndex[LIST_A][30],
                      int32_t listIdx, int32_t iPartIdx, int8_t iRef, int16_t iMVP[2]) {
  if (0 == iPartIdx) {
    const int8_t kiTopRef = iRefIndex[listIdx][1];
    if (iRef == kiTopRef) {
      ST32 (iMVP, LD32 (&iMotionVector[listIdx][1][0]));
      return;
    }
  } else { // 8 == iPartIdx
    const int8_t kiLeftRef = iRefIndex[listIdx][18];
    if (iRef == kiLeftRef) {
      ST32 (iMVP, LD32 (&iMotionVector[listIdx][18][0]));
      return;
    }
  }

  PredMv (iMotionVector, iRefIndex, listIdx, iPartIdx, 4, iRef, iMVP);
}

//update iMVs and iRefIndex cache for current MB, only for P_16*16 (SKIP inclusive)
/* can be further optimized */
void UpdateP16x16MotionInfo (PDqLayer pCurDqLayer, int32_t listIdx, int8_t iRef, int16_t iMVs[2]) {
  const int16_t kiRef2 = ((uint8_t)iRef << 8) | (uint8_t)iRef;
  const int32_t kiMV32 = LD32 (iMVs);
  int32_t i;
  int32_t iMbXy = pCurDqLayer->iMbXyIndex;

  for (i = 0; i < 16; i += 4) {
    //mb
    const uint8_t kuiScan4Idx = g_kuiScan4[i];
    const uint8_t kuiScan4IdxPlus4 = 4 + kuiScan4Idx;
    if (pCurDqLayer->pDec != NULL) {
      ST16 (&pCurDqLayer->pDec->pRefIndex[listIdx][iMbXy][kuiScan4Idx], kiRef2);
      ST16 (&pCurDqLayer->pDec->pRefIndex[listIdx][iMbXy][kuiScan4IdxPlus4], kiRef2);

      ST32 (pCurDqLayer->pDec->pMv[listIdx][iMbXy][kuiScan4Idx], kiMV32);
      ST32 (pCurDqLayer->pDec->pMv[listIdx][iMbXy][1 + kuiScan4Idx], kiMV32);
      ST32 (pCurDqLayer->pDec->pMv[listIdx][iMbXy][kuiScan4IdxPlus4], kiMV32);
      ST32 (pCurDqLayer->pDec->pMv[listIdx][iMbXy][1 + kuiScan4IdxPlus4], kiMV32);
    } else {
      ST16 (&pCurDqLayer->pRefIndex[listIdx][iMbXy][kuiScan4Idx], kiRef2);
      ST16 (&pCurDqLayer->pRefIndex[listIdx][iMbXy][kuiScan4IdxPlus4], kiRef2);

      ST32 (pCurDqLayer->pMv[listIdx][iMbXy][kuiScan4Idx], kiMV32);
      ST32 (pCurDqLayer->pMv[listIdx][iMbXy][1 + kuiScan4Idx], kiMV32);
      ST32 (pCurDqLayer->pMv[listIdx][iMbXy][kuiScan4IdxPlus4], kiMV32);
      ST32 (pCurDqLayer->pMv[listIdx][iMbXy][1 + kuiScan4IdxPlus4], kiMV32);
    }
  }
}

//update iRefIndex cache for current MB, only for P_16*16 (SKIP inclusive)
/* can be further optimized */
void UpdateP16x16RefIdx (PDqLayer pCurDqLayer, int32_t listIdx, int8_t iRef) {
  const int16_t kiRef2 = ((uint8_t)iRef << 8) | (uint8_t)iRef;
  int32_t i;
  int32_t iMbXy = pCurDqLayer->iMbXyIndex;

  for (i = 0; i < 16; i += 4) {
    //mb
    const uint8_t kuiScan4Idx = g_kuiScan4[i];
    const uint8_t kuiScan4IdxPlus4 = 4 + kuiScan4Idx;

    ST16 (&pCurDqLayer->pDec->pRefIndex[listIdx][iMbXy][kuiScan4Idx], kiRef2);
    ST16 (&pCurDqLayer->pDec->pRefIndex[listIdx][iMbXy][kuiScan4IdxPlus4], kiRef2);
  }
}

//update iMVs only cache for current MB, only for P_16*16 (SKIP inclusive)
/* can be further optimized */
void UpdateP16x16MotionOnly (PDqLayer pCurDqLayer, int32_t listIdx, int16_t iMVs[2]) {
  const int32_t kiMV32 = LD32 (iMVs);
  int32_t i;
  int32_t iMbXy = pCurDqLayer->iMbXyIndex;

  for (i = 0; i < 16; i += 4) {
    //mb
    const uint8_t kuiScan4Idx = g_kuiScan4[i];
    const uint8_t kuiScan4IdxPlus4 = 4 + kuiScan4Idx;
    if (pCurDqLayer->pDec != NULL) {
      ST32 (pCurDqLayer->pDec->pMv[listIdx][iMbXy][kuiScan4Idx], kiMV32);
      ST32 (pCurDqLayer->pDec->pMv[listIdx][iMbXy][1 + kuiScan4Idx], kiMV32);
      ST32 (pCurDqLayer->pDec->pMv[listIdx][iMbXy][kuiScan4IdxPlus4], kiMV32);
      ST32 (pCurDqLayer->pDec->pMv[listIdx][iMbXy][1 + kuiScan4IdxPlus4], kiMV32);
    } else {
      ST32 (pCurDqLayer->pMv[listIdx][iMbXy][kuiScan4Idx], kiMV32);
      ST32 (pCurDqLayer->pMv[listIdx][iMbXy][1 + kuiScan4Idx], kiMV32);
      ST32 (pCurDqLayer->pMv[listIdx][iMbXy][kuiScan4IdxPlus4], kiMV32);
      ST32 (pCurDqLayer->pMv[listIdx][iMbXy][1 + kuiScan4IdxPlus4], kiMV32);
    }
  }
}

//update iRefIndex and iMVs of Mb, only for P16x8
/*need further optimization, mb_cache not work */
void UpdateP16x8MotionInfo (PDqLayer pCurDqLayer, int16_t iMotionVector[LIST_A][30][MV_A],
                            int8_t iRefIndex[LIST_A][30],
                            int32_t listIdx, int32_t iPartIdx, int8_t iRef, int16_t iMVs[2]) {
  const int16_t kiRef2 = ((uint8_t)iRef << 8) | (uint8_t)iRef;
  const int32_t kiMV32 = LD32 (iMVs);
  int32_t i;
  int32_t iMbXy = pCurDqLayer->iMbXyIndex;
  for (i = 0; i < 2; i++, iPartIdx += 4) {
    const uint8_t kuiScan4Idx      = g_kuiScan4[iPartIdx];
    const uint8_t kuiScan4IdxPlus4 = 4 + kuiScan4Idx;
    const uint8_t kuiCacheIdx      = g_kuiCache30ScanIdx[iPartIdx];
    const uint8_t kuiCacheIdxPlus6 = 6 + kuiCacheIdx;

    //mb
    if (pCurDqLayer->pDec != NULL) {
      ST16 (&pCurDqLayer->pDec->pRefIndex[listIdx][iMbXy][kuiScan4Idx], kiRef2);
      ST16 (&pCurDqLayer->pDec->pRefIndex[listIdx][iMbXy][kuiScan4IdxPlus4], kiRef2);
      ST32 (pCurDqLayer->pDec->pMv[listIdx][iMbXy][kuiScan4Idx], kiMV32);
      ST32 (pCurDqLayer->pDec->pMv[listIdx][iMbXy][1 + kuiScan4Idx], kiMV32);
      ST32 (pCurDqLayer->pDec->pMv[listIdx][iMbXy][kuiScan4IdxPlus4], kiMV32);
      ST32 (pCurDqLayer->pDec->pMv[listIdx][iMbXy][1 + kuiScan4IdxPlus4], kiMV32);
    } else {
      ST16 (&pCurDqLayer->pRefIndex[listIdx][iMbXy][kuiScan4Idx], kiRef2);
      ST16 (&pCurDqLayer->pRefIndex[listIdx][iMbXy][kuiScan4IdxPlus4], kiRef2);
      ST32 (pCurDqLayer->pMv[listIdx][iMbXy][kuiScan4Idx], kiMV32);
      ST32 (pCurDqLayer->pMv[listIdx][iMbXy][1 + kuiScan4Idx], kiMV32);
      ST32 (pCurDqLayer->pMv[listIdx][iMbXy][kuiScan4IdxPlus4], kiMV32);
      ST32 (pCurDqLayer->pMv[listIdx][iMbXy][1 + kuiScan4IdxPlus4], kiMV32);
    }
    //cache
    ST16 (&iRefIndex[listIdx][kuiCacheIdx ], kiRef2);
    ST16 (&iRefIndex[listIdx][kuiCacheIdxPlus6], kiRef2);
    ST32 (iMotionVector[listIdx][  kuiCacheIdx ], kiMV32);
    ST32 (iMotionVector[listIdx][1 + kuiCacheIdx ], kiMV32);
    ST32 (iMotionVector[listIdx][  kuiCacheIdxPlus6], kiMV32);
    ST32 (iMotionVector[listIdx][1 + kuiCacheIdxPlus6], kiMV32);
  }
}
//update iRefIndex and iMVs of both Mb and Mb_cache, only for P8x16
void UpdateP8x16MotionInfo (PDqLayer pCurDqLayer, int16_t iMotionVector[LIST_A][30][MV_A],
                            int8_t iRefIndex[LIST_A][30],
                            int32_t listIdx, int32_t iPartIdx, int8_t iRef, int16_t iMVs[2]) {
  const int16_t kiRef2 = ((uint8_t)iRef << 8) | (uint8_t)iRef;
  const int32_t kiMV32 = LD32 (iMVs);
  int32_t i;
  int32_t iMbXy = pCurDqLayer->iMbXyIndex;

  for (i = 0; i < 2; i++, iPartIdx += 8) {
    const uint8_t kuiScan4Idx = g_kuiScan4[iPartIdx];
    const uint8_t kuiCacheIdx = g_kuiCache30ScanIdx[iPartIdx];
    const uint8_t kuiScan4IdxPlus4 = 4 + kuiScan4Idx;
    const uint8_t kuiCacheIdxPlus6 = 6 + kuiCacheIdx;

    //mb
    if (pCurDqLayer->pDec != NULL) {
      ST16 (&pCurDqLayer->pDec->pRefIndex[listIdx][iMbXy][kuiScan4Idx], kiRef2);
      ST16 (&pCurDqLayer->pDec->pRefIndex[listIdx][iMbXy][kuiScan4IdxPlus4], kiRef2);
      ST32 (pCurDqLayer->pDec->pMv[listIdx][iMbXy][kuiScan4Idx], kiMV32);
      ST32 (pCurDqLayer->pDec->pMv[listIdx][iMbXy][1 + kuiScan4Idx], kiMV32);
      ST32 (pCurDqLayer->pDec->pMv[listIdx][iMbXy][kuiScan4IdxPlus4], kiMV32);
      ST32 (pCurDqLayer->pDec->pMv[listIdx][iMbXy][1 + kuiScan4IdxPlus4], kiMV32);
    } else {
      ST16 (&pCurDqLayer->pRefIndex[listIdx][iMbXy][kuiScan4Idx], kiRef2);
      ST16 (&pCurDqLayer->pRefIndex[listIdx][iMbXy][kuiScan4IdxPlus4], kiRef2);
      ST32 (pCurDqLayer->pMv[listIdx][iMbXy][kuiScan4Idx], kiMV32);
      ST32 (pCurDqLayer->pMv[listIdx][iMbXy][1 + kuiScan4Idx], kiMV32);
      ST32 (pCurDqLayer->pMv[listIdx][iMbXy][kuiScan4IdxPlus4], kiMV32);
      ST32 (pCurDqLayer->pMv[listIdx][iMbXy][1 + kuiScan4IdxPlus4], kiMV32);
    }
    //cache
    ST16 (&iRefIndex[listIdx][kuiCacheIdx ], kiRef2);
    ST16 (&iRefIndex[listIdx][kuiCacheIdxPlus6], kiRef2);
    ST32 (iMotionVector[listIdx][  kuiCacheIdx ], kiMV32);
    ST32 (iMotionVector[listIdx][1 + kuiCacheIdx ], kiMV32);
    ST32 (iMotionVector[listIdx][  kuiCacheIdxPlus6], kiMV32);
    ST32 (iMotionVector[listIdx][1 + kuiCacheIdxPlus6], kiMV32);
  }
}

void FillSpatialDirect8x8Mv (PDqLayer pCurDqLayer, const int16_t& iIdx8, const int8_t& iPartCount, const int8_t& iPartW,
                             const SubMbType& subMbType, const bool& bIsLongRef, int16_t pMvDirect[LIST_A][2], int8_t iRef[LIST_A],
                             int16_t pMotionVector[LIST_A][30][MV_A], int16_t pMvdCache[LIST_A][30][MV_A]) {
  int32_t iMbXy = pCurDqLayer->iMbXyIndex;
  for (int32_t j = 0; j < iPartCount; j++) {
    int8_t iPartIdx = iIdx8 + j * iPartW;
    uint8_t iScan4Idx = g_kuiScan4[iPartIdx];
    uint8_t iColocIdx = g_kuiScan4[iPartIdx];
    uint8_t iCacheIdx = g_kuiCache30ScanIdx[iPartIdx];

    int16_t pMV[4] = { 0 };
    if (IS_SUB_8x8 (subMbType)) {
      * (uint32_t*)pMV = * (uint32_t*)pMvDirect[LIST_0];
      ST32 ((pMV + 2), LD32 (pMV));
      ST64 (pCurDqLayer->pDec->pMv[LIST_0][iMbXy][iScan4Idx], LD64 (pMV));
      ST64 (pCurDqLayer->pDec->pMv[LIST_0][iMbXy][iScan4Idx + 4], LD64 (pMV));
      ST64 (pCurDqLayer->pMvd[LIST_0][iMbXy][iScan4Idx], 0);
      ST64 (pCurDqLayer->pMvd[LIST_0][iMbXy][iScan4Idx + 4], 0);
      if (pMotionVector != NULL) {
        ST64 (pMotionVector[LIST_0][iCacheIdx], LD64 (pMV));
        ST64 (pMotionVector[LIST_0][iCacheIdx + 6], LD64 (pMV));
      }
      if (pMvdCache != NULL) {
        ST64 (pMvdCache[LIST_0][iCacheIdx], 0);
        ST64 (pMvdCache[LIST_0][iCacheIdx + 6], 0);
      }
      * (uint32_t*)pMV = * (uint32_t*)pMvDirect[LIST_1];
      ST32 ((pMV + 2), LD32 (pMV));
      ST64 (pCurDqLayer->pDec->pMv[LIST_1][iMbXy][iScan4Idx], LD64 (pMV));
      ST64 (pCurDqLayer->pDec->pMv[LIST_1][iMbXy][iScan4Idx + 4], LD64 (pMV));
      ST64 (pCurDqLayer->pMvd[LIST_1][iMbXy][iScan4Idx], 0);
      ST64 (pCurDqLayer->pMvd[LIST_1][iMbXy][iScan4Idx + 4], 0);
      if (pMotionVector != NULL) {
        ST64 (pMotionVector[LIST_1][iCacheIdx], LD64 (pMV));
        ST64 (pMotionVector[LIST_1][iCacheIdx + 6], LD64 (pMV));
      }
      if (pMvdCache != NULL) {
        ST64 (pMvdCache[LIST_1][iCacheIdx], 0);
        ST64 (pMvdCache[LIST_1][iCacheIdx + 6], 0);
      }
    } else { //SUB_4x4
      * (uint32_t*)pMV = * (uint32_t*)pMvDirect[LIST_0];
      ST32 (pCurDqLayer->pDec->pMv[LIST_0][iMbXy][iScan4Idx], LD32 (pMV));
      ST32 (pCurDqLayer->pMvd[LIST_0][iMbXy][iScan4Idx], 0);
      if (pMotionVector != NULL) {
        ST32 (pMotionVector[LIST_0][iCacheIdx], LD32 (pMV));
      }
      if (pMvdCache != NULL) {
        ST32 (pMvdCache[LIST_0][iCacheIdx], 0);
      }
      * (uint32_t*)pMV = * (uint32_t*)pMvDirect[LIST_1];
      ST32 (pCurDqLayer->pDec->pMv[LIST_1][iMbXy][iScan4Idx], LD32 (pMV));
      ST32 (pCurDqLayer->pMvd[LIST_1][iMbXy][iScan4Idx], 0);
      if (pMotionVector != NULL) {
        ST32 (pMotionVector[LIST_1][iCacheIdx], LD32 (pMV));
      }
      if (pMvdCache != NULL) {
        ST32 (pMvdCache[LIST_1][iCacheIdx], 0);
      }
    }
    if ((* (int32_t*)pMvDirect[LIST_0] | * (int32_t*)pMvDirect[LIST_1])) {
      uint32_t uiColZeroFlag = (0 == pCurDqLayer->iColocIntra[iColocIdx]) && !bIsLongRef &&
                               (pCurDqLayer->iColocRefIndex[LIST_0][iColocIdx] == 0 || (pCurDqLayer->iColocRefIndex[LIST_0][iColocIdx] < 0
                                   && pCurDqLayer->iColocRefIndex[LIST_1][iColocIdx] == 0));
      const int16_t (*mvColoc)[2] = 0 == pCurDqLayer->iColocRefIndex[LIST_0][iColocIdx] ? pCurDqLayer->iColocMv[LIST_0] :
                                    pCurDqLayer->iColocMv[LIST_1];
      const int16_t* mv = mvColoc[iColocIdx];
      if (IS_SUB_8x8 (subMbType)) {
        if (uiColZeroFlag && ((unsigned) (mv[0] + 1) <= 2 && (unsigned) (mv[1] + 1) <= 2)) {
          if (iRef[LIST_0] == 0) {
            ST64 (pCurDqLayer->pDec->pMv[LIST_0][iMbXy][iScan4Idx], 0);
            ST64 (pCurDqLayer->pDec->pMv[LIST_0][iMbXy][iScan4Idx + 4], 0);
            ST64 (pCurDqLayer->pMvd[LIST_0][iMbXy][iScan4Idx], 0);
            ST64 (pCurDqLayer->pMvd[LIST_0][iMbXy][iScan4Idx + 4], 0);
            if (pMotionVector != NULL) {
              ST64 (pMotionVector[LIST_0][iCacheIdx], 0);
              ST64 (pMotionVector[LIST_0][iCacheIdx + 6], 0);
            }
            if (pMvdCache != NULL) {
              ST64 (pMvdCache[LIST_0][iCacheIdx], 0);
              ST64 (pMvdCache[LIST_0][iCacheIdx + 6], 0);
            }
          }

          if (iRef[LIST_1] == 0) {
            ST64 (pCurDqLayer->pDec->pMv[LIST_1][iMbXy][iScan4Idx], 0);
            ST64 (pCurDqLayer->pDec->pMv[LIST_1][iMbXy][iScan4Idx + 4], 0);
            ST64 (pCurDqLayer->pMvd[LIST_1][iMbXy][iScan4Idx], 0);
            ST64 (pCurDqLayer->pMvd[LIST_1][iMbXy][iScan4Idx + 4], 0);
            if (pMotionVector != NULL) {
              ST64 (pMotionVector[LIST_1][iCacheIdx], 0);
              ST64 (pMotionVector[LIST_1][iCacheIdx + 6], 0);
            }
            if (pMvdCache != NULL) {
              ST64 (pMvdCache[LIST_1][iCacheIdx], 0);
              ST64 (pMvdCache[LIST_1][iCacheIdx + 6], 0);
            }
          }
        }
      } else {
        if (uiColZeroFlag && ((unsigned) (mv[0] + 1) <= 2 && (unsigned) (mv[1] + 1) <= 2)) {
          if (iRef[LIST_0] == 0) {
            ST32 (pCurDqLayer->pDec->pMv[LIST_0][iMbXy][iScan4Idx], 0);
            ST32 (pCurDqLayer->pMvd[LIST_0][iMbXy][iScan4Idx], 0);
            if (pMotionVector != NULL) {
              ST32 (pMotionVector[LIST_0][iCacheIdx], 0);
            }
            if (pMvdCache != NULL) {
              ST32 (pMvdCache[LIST_0][iCacheIdx], 0);
            }
          }
          if (iRef[LIST_1] == 0) {
            ST32 (pCurDqLayer->pDec->pMv[LIST_1][iMbXy][iScan4Idx], 0);
            ST32 (pCurDqLayer->pMvd[LIST_1][iMbXy][iScan4Idx], 0);
            if (pMotionVector != NULL) {
              ST32 (pMotionVector[LIST_1][iCacheIdx], 0);
            }
            if (pMvdCache != NULL) {
              ST32 (pMvdCache[LIST_1][iCacheIdx], 0);
            }
          }
        }
      }
    }
  }
}

void FillTemporalDirect8x8Mv (PDqLayer pCurDqLayer, const int16_t& iIdx8, const int8_t& iPartCount,
                              const int8_t& iPartW,
                              const SubMbType& subMbType, int8_t iRef[LIST_A], int16_t (*mvColoc)[2], int16_t pMotionVector[LIST_A][30][MV_A],
                              int16_t pMvdCache[LIST_A][30][MV_A]) {
  PSlice pSlice = &pCurDqLayer->sLayerInfo.sSliceInLayer;
  int32_t iMbXy = pCurDqLayer->iMbXyIndex;
  int16_t pMvDirect[LIST_A][2] = { { 0, 0 }, { 0, 0 } };
  for (int32_t j = 0; j < iPartCount; j++) {
    int8_t iPartIdx = iIdx8 + j * iPartW;
    uint8_t iScan4Idx = g_kuiScan4[iPartIdx];
    uint8_t iColocIdx = g_kuiScan4[iPartIdx];
    uint8_t iCacheIdx = g_kuiCache30ScanIdx[iPartIdx];

    int16_t* mv = mvColoc[iColocIdx];

    int16_t pMV[4] = { 0 };
    if (IS_SUB_8x8 (subMbType)) {
      if (!pCurDqLayer->iColocIntra[iColocIdx]) {
        pMvDirect[LIST_0][0] = (pSlice->iMvScale[LIST_0][iRef[LIST_0]] * mv[0] + 128) >> 8;
        pMvDirect[LIST_0][1] = (pSlice->iMvScale[LIST_0][iRef[LIST_0]] * mv[1] + 128) >> 8;
      }
      ST32 (pMV, LD32 (pMvDirect[LIST_0]));
      ST32 ((pMV + 2), LD32 (pMvDirect[LIST_0]));
      ST64 (pCurDqLayer->pDec->pMv[LIST_0][iMbXy][iScan4Idx], LD64 (pMV));
      ST64 (pCurDqLayer->pDec->pMv[LIST_0][iMbXy][iScan4Idx + 4], LD64 (pMV));
      ST64 (pCurDqLayer->pMvd[LIST_0][iMbXy][iScan4Idx], 0);
      ST64 (pCurDqLayer->pMvd[LIST_0][iMbXy][iScan4Idx + 4], 0);
      if (pMotionVector != NULL) {
        ST64 (pMotionVector[LIST_0][iCacheIdx], LD64 (pMV));
        ST64 (pMotionVector[LIST_0][iCacheIdx + 6], LD64 (pMV));
      }
      if (pMvdCache != NULL) {
        ST64 (pMvdCache[LIST_0][iCacheIdx], 0);
        ST64 (pMvdCache[LIST_0][iCacheIdx + 6], 0);
      }
      if (!pCurDqLayer->iColocIntra[g_kuiScan4[iIdx8]]) {
        pMvDirect[LIST_1][0] = pMvDirect[LIST_0][0] - mv[0];
        pMvDirect[LIST_1][1] = pMvDirect[LIST_0][1] - mv[1];
      }
      ST32 (pMV, LD32 (pMvDirect[LIST_1]));
      ST32 ((pMV + 2), LD32 (pMvDirect[LIST_1]));
      ST64 (pCurDqLayer->pDec->pMv[LIST_1][iMbXy][iScan4Idx], LD64 (pMV));
      ST64 (pCurDqLayer->pDec->pMv[LIST_1][iMbXy][iScan4Idx + 4], LD64 (pMV));
      ST64 (pCurDqLayer->pMvd[LIST_1][iMbXy][iScan4Idx], 0);
      ST64 (pCurDqLayer->pMvd[LIST_1][iMbXy][iScan4Idx + 4], 0);
      if (pMotionVector != NULL) {
        ST64 (pMotionVector[LIST_1][iCacheIdx], LD64 (pMV));
        ST64 (pMotionVector[LIST_1][iCacheIdx + 6], LD64 (pMV));
      }
      if (pMvdCache != NULL) {
        ST64 (pMvdCache[LIST_1][iCacheIdx], 0);
        ST64 (pMvdCache[LIST_1][iCacheIdx + 6], 0);
      }
    } else { //SUB_4x4
      if (!pCurDqLayer->iColocIntra[iColocIdx]) {
        pMvDirect[LIST_0][0] = (pSlice->iMvScale[LIST_0][iRef[LIST_0]] * mv[0] + 128) >> 8;
        pMvDirect[LIST_0][1] = (pSlice->iMvScale[LIST_0][iRef[LIST_0]] * mv[1] + 128) >> 8;
      }
      ST32 (pCurDqLayer->pDec->pMv[LIST_0][iMbXy][iScan4Idx], LD32 (pMvDirect[LIST_0]));
      ST32 (pCurDqLayer->pMvd[LIST_0][iMbXy][iScan4Idx], 0);
      if (pMotionVector != NULL) {
        ST32 (pMotionVector[LIST_0][iCacheIdx], LD32 (pMvDirect[LIST_0]));
      }
      if (pMvdCache != NULL) {
        ST32 (pMvdCache[LIST_0][iCacheIdx], 0);
      }
      if (!pCurDqLayer->iColocIntra[iColocIdx]) {
        pMvDirect[LIST_1][0] = pMvDirect[LIST_0][0] - mv[0];
        pMvDirect[LIST_1][1] = pMvDirect[LIST_0][1] - mv[1];
      }
      ST32 (pCurDqLayer->pDec->pMv[LIST_1][iMbXy][iScan4Idx], LD32 (pMvDirect[LIST_1]));
      ST32 (pCurDqLayer->pMvd[LIST_1][iMbXy][iScan4Idx], 0);
      if (pMotionVector != NULL) {
        ST32 (pMotionVector[LIST_1][iCacheIdx], LD32 (pMvDirect[LIST_1]));
      }
      if (pMvdCache != NULL) {
        ST32 (pMvdCache[LIST_1][iCacheIdx], 0);
      }
    }
  }
}
int8_t MapColToList0 (PWelsDecoderContext& pCtx, const int8_t& colocRefIndexL0,
                      const int32_t& ref0Count) { //ISO/IEC 14496-10:2009(E) (8-193)
  //When reference is lost, this function must be skipped.
  if ((pCtx->iErrorCode & dsRefLost) == dsRefLost) {
    return 0;
  }
  PPicture pic1 = pCtx->sRefPic.pRefList[LIST_1][0];
  if (pic1 && pic1->pRefPic[LIST_0][colocRefIndexL0]) {
    const int32_t iFramePoc = pic1->pRefPic[LIST_0][colocRefIndexL0]->iFramePoc;
    for (int32_t i = 0; i < ref0Count; i++) {
      if (pCtx->sRefPic.pRefList[LIST_0][i]->iFramePoc == iFramePoc) {
        return i;
      }
    }
  }
  return 0;
}
void Update8x8RefIdx (PDqLayer& pCurDqLayer, const int16_t& iPartIdx, const int32_t& listIdx, const int8_t& iRef) {
  int32_t iMbXy = pCurDqLayer->iMbXyIndex;
  const uint8_t iScan4Idx = g_kuiScan4[iPartIdx];
  pCurDqLayer->pDec->pRefIndex[listIdx][iMbXy][iScan4Idx] = pCurDqLayer->pDec->pRefIndex[listIdx][iMbXy][iScan4Idx + 1] =
        pCurDqLayer->pDec->pRefIndex[listIdx][iMbXy][iScan4Idx + 4] = pCurDqLayer->pDec->pRefIndex[listIdx][iMbXy][iScan4Idx +
            5] = iRef;

}
} // namespace WelsDec
