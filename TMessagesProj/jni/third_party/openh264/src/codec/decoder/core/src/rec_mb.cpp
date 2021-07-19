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
 * \file    rec_mb.c
 *
 * \brief   implementation for all macroblock decoding process after mb syntax parsing and residual decoding with cavlc.
 *
 * \date    3/18/2009 Created
 *
 *************************************************************************************
 */


#include "rec_mb.h"
#include "decode_slice.h"

namespace WelsDec {

void WelsFillRecNeededMbInfo (PWelsDecoderContext pCtx, bool bOutput, PDqLayer pCurDqLayer) {
  PPicture pCurPic = pCtx->pDec;
  int32_t iLumaStride   = pCurPic->iLinesize[0];
  int32_t iChromaStride = pCurPic->iLinesize[1];
  int32_t iMbX = pCurDqLayer->iMbX;
  int32_t iMbY = pCurDqLayer->iMbY;

  pCurDqLayer->iLumaStride = iLumaStride;
  pCurDqLayer->iChromaStride = iChromaStride;

  if (bOutput) {
    pCurDqLayer->pPred[0] = pCurPic->pData[0] + ((iMbY * iLumaStride + iMbX) << 4);
    pCurDqLayer->pPred[1] = pCurPic->pData[1] + ((iMbY * iChromaStride + iMbX) << 3);
    pCurDqLayer->pPred[2] = pCurPic->pData[2] + ((iMbY * iChromaStride + iMbX) << 3);
  }
}

int32_t RecI8x8Mb (int32_t iMbXy, PWelsDecoderContext pCtx, int16_t* pScoeffLevel, PDqLayer pDqLayer) {
  RecI8x8Luma (iMbXy, pCtx, pScoeffLevel, pDqLayer);
  RecI4x4Chroma (iMbXy, pCtx, pScoeffLevel, pDqLayer);
  return ERR_NONE;
}

int32_t RecI8x8Luma (int32_t iMbXy, PWelsDecoderContext pCtx, int16_t* pScoeffLevel, PDqLayer pDqLayer) {
  /*****get local variable from outer variable********/
  /*prediction info*/
  uint8_t* pPred = pDqLayer->pPred[0];

  int32_t iLumaStride = pDqLayer->iLumaStride;
  int32_t* pBlockOffset = pCtx->iDecBlockOffsetArray;
  PGetIntraPred8x8Func* pGetI8x8LumaPredFunc = pCtx->pGetI8x8LumaPredFunc;

  int8_t* pIntra8x8PredMode = pDqLayer->pIntra4x4FinalMode[iMbXy]; // I_NxN
  int16_t* pRS = pScoeffLevel;
  /*itransform info*/
  PIdctResAddPredFunc pIdctResAddPredFunc = pCtx->pIdctResAddPredFunc8x8;

  /*************local variable********************/
  uint8_t i = 0;
  bool bTLAvail[4], bTRAvail[4];
  // Top-Right : Left : Top-Left : Top
  bTLAvail[0] = !! (pDqLayer->pIntraNxNAvailFlag[iMbXy] & 0x02);
  bTLAvail[1] = !! (pDqLayer->pIntraNxNAvailFlag[iMbXy] & 0x01);
  bTLAvail[2] = !! (pDqLayer->pIntraNxNAvailFlag[iMbXy] & 0x04);
  bTLAvail[3] = true;

  bTRAvail[0] = !! (pDqLayer->pIntraNxNAvailFlag[iMbXy] & 0x01);
  bTRAvail[1] = !! (pDqLayer->pIntraNxNAvailFlag[iMbXy] & 0x08);
  bTRAvail[2] = true;
  bTRAvail[3] = false;

  /*************real process*********************/
  for (i = 0; i < 4; i++) {

    uint8_t* pPredI8x8 = pPred + pBlockOffset[i << 2];
    uint8_t uiMode = pIntra8x8PredMode[g_kuiScan4[i << 2]];

    pGetI8x8LumaPredFunc[uiMode] (pPredI8x8, iLumaStride, bTLAvail[i], bTRAvail[i]);

    int32_t iIndex = g_kuiMbCountScan4Idx[i << 2];
    if (pDqLayer->pNzc[iMbXy][iIndex] || pDqLayer->pNzc[iMbXy][iIndex + 1] || pDqLayer->pNzc[iMbXy][iIndex + 4]
        || pDqLayer->pNzc[iMbXy][iIndex + 5]) {
      int16_t* pRSI8x8 = &pRS[i << 6];
      pIdctResAddPredFunc (pPredI8x8, iLumaStride, pRSI8x8);
    }
  }

  return ERR_NONE;
}

int32_t RecI4x4Mb (int32_t iMBXY, PWelsDecoderContext pCtx, int16_t* pScoeffLevel, PDqLayer pDqLayer) {
  RecI4x4Luma (iMBXY, pCtx, pScoeffLevel, pDqLayer);
  RecI4x4Chroma (iMBXY, pCtx, pScoeffLevel, pDqLayer);
  return ERR_NONE;
}


int32_t RecI4x4Luma (int32_t iMBXY, PWelsDecoderContext pCtx, int16_t* pScoeffLevel, PDqLayer pDqLayer) {
  /*****get local variable from outer variable********/
  /*prediction info*/
  uint8_t* pPred = pDqLayer->pPred[0];

  int32_t iLumaStride = pDqLayer->iLumaStride;
  int32_t* pBlockOffset = pCtx->iDecBlockOffsetArray;
  PGetIntraPredFunc* pGetI4x4LumaPredFunc = pCtx->pGetI4x4LumaPredFunc;

  int8_t* pIntra4x4PredMode = pDqLayer->pIntra4x4FinalMode[iMBXY];
  int16_t* pRS = pScoeffLevel;
  /*itransform info*/
  PIdctResAddPredFunc pIdctResAddPredFunc = pCtx->pIdctResAddPredFunc;


  /*************local variable********************/
  uint8_t i = 0;

  /*************real process*********************/
  for (i = 0; i < 16; i++) {

    uint8_t* pPredI4x4 = pPred + pBlockOffset[i];
    uint8_t uiMode = pIntra4x4PredMode[g_kuiScan4[i]];

    pGetI4x4LumaPredFunc[uiMode] (pPredI4x4, iLumaStride);

    if (pDqLayer->pNzc[iMBXY][g_kuiMbCountScan4Idx[i]]) {
      int16_t* pRSI4x4 = &pRS[i << 4];
      pIdctResAddPredFunc (pPredI4x4, iLumaStride, pRSI4x4);
    }
  }

  return ERR_NONE;
}


int32_t RecI4x4Chroma (int32_t iMBXY, PWelsDecoderContext pCtx, int16_t* pScoeffLevel, PDqLayer pDqLayer) {
  int32_t iChromaStride = pCtx->pCurDqLayer->pDec->iLinesize[1];

  int8_t iChromaPredMode = pDqLayer->pChromaPredMode[iMBXY];

  PGetIntraPredFunc* pGetIChromaPredFunc = pCtx->pGetIChromaPredFunc;

  uint8_t* pPred = pDqLayer->pPred[1];

  pGetIChromaPredFunc[iChromaPredMode] (pPred, iChromaStride);
  pPred = pDqLayer->pPred[2];
  pGetIChromaPredFunc[iChromaPredMode] (pPred, iChromaStride);

  RecChroma (iMBXY, pCtx, pScoeffLevel, pDqLayer);

  return ERR_NONE;
}


int32_t RecI16x16Mb (int32_t iMBXY, PWelsDecoderContext pCtx, int16_t* pScoeffLevel, PDqLayer pDqLayer) {
  /*decoder use, encoder no use*/
  int8_t iI16x16PredMode = pDqLayer->pIntraPredMode[iMBXY][7];
  int8_t iChromaPredMode = pDqLayer->pChromaPredMode[iMBXY];
  PGetIntraPredFunc* pGetIChromaPredFunc = pCtx->pGetIChromaPredFunc;
  PGetIntraPredFunc* pGetI16x16LumaPredFunc = pCtx->pGetI16x16LumaPredFunc;
  int32_t iUVStride = pCtx->pCurDqLayer->pDec->iLinesize[1];

  /*common use by decoder&encoder*/
  int32_t iYStride = pDqLayer->iLumaStride;
  int16_t* pRS = pScoeffLevel;

  uint8_t* pPred = pDqLayer->pPred[0];

  PIdctFourResAddPredFunc pIdctFourResAddPredFunc = pCtx->pIdctFourResAddPredFunc;

  /*decode i16x16 y*/
  pGetI16x16LumaPredFunc[iI16x16PredMode] (pPred, iYStride);

  /*1 mb is divided 16 4x4_block to idct*/
  const int8_t* pNzc = pDqLayer->pNzc[iMBXY];
  pIdctFourResAddPredFunc (pPred + 0 * iYStride + 0, iYStride, pRS + 0 * 64, pNzc +  0);
  pIdctFourResAddPredFunc (pPred + 0 * iYStride + 8, iYStride, pRS + 1 * 64, pNzc +  2);
  pIdctFourResAddPredFunc (pPred + 8 * iYStride + 0, iYStride, pRS + 2 * 64, pNzc +  8);
  pIdctFourResAddPredFunc (pPred + 8 * iYStride + 8, iYStride, pRS + 3 * 64, pNzc + 10);

  /*decode intra mb cb&cr*/
  pPred = pDqLayer->pPred[1];
  pGetIChromaPredFunc[iChromaPredMode] (pPred, iUVStride);
  pPred = pDqLayer->pPred[2];
  pGetIChromaPredFunc[iChromaPredMode] (pPred, iUVStride);
  RecChroma (iMBXY, pCtx, pScoeffLevel, pDqLayer);

  return ERR_NONE;
}


//according to current 8*8 block ref_index to gain reference picture
static inline int32_t GetRefPic (sMCRefMember* pMCRefMem, PWelsDecoderContext pCtx, const int8_t& iRefIdx,
                                 int32_t listIdx) {
  PPicture pRefPic;

  if (iRefIdx >= 0) {
    pRefPic = pCtx->sRefPic.pRefList[listIdx][iRefIdx];

    if (pRefPic != NULL) {
      pMCRefMem->iSrcLineLuma = pRefPic->iLinesize[0];
      pMCRefMem->iSrcLineChroma = pRefPic->iLinesize[1];

      pMCRefMem->pSrcY = pRefPic->pData[0];
      pMCRefMem->pSrcU = pRefPic->pData[1];
      pMCRefMem->pSrcV = pRefPic->pData[2];
      if (!pMCRefMem->pSrcY || !pMCRefMem->pSrcU || !pMCRefMem->pSrcV) {
        return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_DATA, ERR_INFO_REFERENCE_PIC_LOST);
      }
      return ERR_NONE;
    }
  }
  return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_DATA, ERR_INFO_REFERENCE_PIC_LOST);
}


#ifndef MC_FLOW_SIMPLE_JUDGE
#define MC_FLOW_SIMPLE_JUDGE 1
#endif //MC_FLOW_SIMPLE_JUDGE
void BaseMC (PWelsDecoderContext pCtx, sMCRefMember* pMCRefMem, const int32_t& listIdx, const int8_t& iRefIdx,
             int32_t iXOffset, int32_t iYOffset,
             SMcFunc* pMCFunc,
             int32_t iBlkWidth, int32_t iBlkHeight, int16_t iMVs[2]) {
  int32_t iFullMVx = (iXOffset << 2) + iMVs[0]; //quarter pixel
  int32_t iFullMVy = (iYOffset << 2) + iMVs[1];
  iFullMVx = WELS_CLIP3 (iFullMVx, ((-PADDING_LENGTH + 2) * (1 << 2)),
                         ((pMCRefMem->iPicWidth + PADDING_LENGTH - 19) * (1 << 2)));
  iFullMVy = WELS_CLIP3 (iFullMVy, ((-PADDING_LENGTH + 2) * (1 << 2)),
                         ((pMCRefMem->iPicHeight + PADDING_LENGTH - 19) * (1 << 2)));

  if (GetThreadCount (pCtx) > 1 && iRefIdx >= 0) {
    // wait for the lines of reference macroblock (3 + 16).
    PPicture pRefPic = pCtx->sRefPic.pRefList[listIdx][iRefIdx];
    if (pCtx->bNewSeqBegin && (pCtx->iErrorCode & dsRefLost)) {
      //set event if refpic is lost to prevent from infinite waiting.
      if (!pRefPic->pReadyEvent[0].isSignaled) {
        for (uint32_t ln = 0; ln < pCtx->sMb.iMbHeight; ++ln) {
          SET_EVENT (&pRefPic->pReadyEvent[ln]);
        }
      }
    }
    int32_t offset = (iFullMVy >> 2) + iBlkHeight + 3 + 16;
    if (offset > pCtx->lastReadyHeightOffset[listIdx][iRefIdx]) {
      const int32_t down_line = WELS_MIN (offset >> 4, int32_t (pCtx->sMb.iMbHeight) - 1);
      if (pRefPic->pReadyEvent[down_line].isSignaled != 1) {
        WAIT_EVENT (&pRefPic->pReadyEvent[down_line], WELS_DEC_THREAD_WAIT_INFINITE);
      }
      pCtx->lastReadyHeightOffset[listIdx][iRefIdx] = offset;
    }
  }

  int32_t iSrcPixOffsetLuma = (iFullMVx >> 2) + (iFullMVy >> 2) * pMCRefMem->iSrcLineLuma;
  int32_t iSrcPixOffsetChroma = (iFullMVx >> 3) + (iFullMVy >> 3) * pMCRefMem->iSrcLineChroma;

  int32_t iBlkWidthChroma = iBlkWidth >> 1;
  int32_t iBlkHeightChroma = iBlkHeight >> 1;

  uint8_t* pSrcY = pMCRefMem->pSrcY + iSrcPixOffsetLuma;
  uint8_t* pSrcU = pMCRefMem->pSrcU + iSrcPixOffsetChroma;
  uint8_t* pSrcV = pMCRefMem->pSrcV + iSrcPixOffsetChroma;
  uint8_t* pDstY = pMCRefMem->pDstY;
  uint8_t* pDstU = pMCRefMem->pDstU;
  uint8_t* pDstV = pMCRefMem->pDstV;

  pMCFunc->pMcLumaFunc (pSrcY, pMCRefMem->iSrcLineLuma, pDstY, pMCRefMem->iDstLineLuma, iFullMVx, iFullMVy, iBlkWidth,
                        iBlkHeight);
  pMCFunc->pMcChromaFunc (pSrcU, pMCRefMem->iSrcLineChroma, pDstU, pMCRefMem->iDstLineChroma, iFullMVx, iFullMVy,
                          iBlkWidthChroma, iBlkHeightChroma);
  pMCFunc->pMcChromaFunc (pSrcV, pMCRefMem->iSrcLineChroma, pDstV, pMCRefMem->iDstLineChroma, iFullMVx, iFullMVy,
                          iBlkWidthChroma, iBlkHeightChroma);

}

static void WeightPrediction (PDqLayer pCurDqLayer, sMCRefMember* pMCRefMem, int32_t listIdx, int32_t iRefIdx,
                              int32_t iBlkWidth,
                              int32_t iBlkHeight) {


  int32_t iLog2denom, iWoc, iOoc;
  int32_t iPredTemp, iLineStride;
  int32_t iPixel = 0;
  uint8_t* pDst;
  //luma
  iLog2denom = pCurDqLayer->pPredWeightTable->uiLumaLog2WeightDenom;
  iWoc = pCurDqLayer->pPredWeightTable->sPredList[listIdx].iLumaWeight[iRefIdx];
  iOoc = pCurDqLayer->pPredWeightTable->sPredList[listIdx].iLumaOffset[iRefIdx];
  iLineStride = pMCRefMem->iDstLineLuma;

  for (int i = 0; i < iBlkHeight; i++) {
    for (int j = 0; j < iBlkWidth; j++) {
      iPixel = j + i * (iLineStride);
      if (iLog2denom >= 1) {
        iPredTemp = ((pMCRefMem->pDstY[iPixel] * iWoc + (1 << (iLog2denom - 1))) >> iLog2denom) + iOoc;

        pMCRefMem->pDstY[iPixel] = WELS_CLIP3 (iPredTemp, 0, 255);
      } else {
        iPredTemp = pMCRefMem->pDstY[iPixel] * iWoc + iOoc;

        pMCRefMem->pDstY[iPixel] = WELS_CLIP3 (iPredTemp, 0, 255);

      }
    }
  }


  //UV
  iBlkWidth = iBlkWidth >> 1;
  iBlkHeight = iBlkHeight >> 1;
  iLog2denom = pCurDqLayer->pPredWeightTable->uiChromaLog2WeightDenom;
  iLineStride = pMCRefMem->iDstLineChroma;

  for (int i = 0; i < 2; i++) {


    //iLog2denom = pCurDqLayer->pPredWeightTable->uiChromaLog2WeightDenom;
    iWoc =  pCurDqLayer->pPredWeightTable->sPredList[listIdx].iChromaWeight[iRefIdx][i];
    iOoc = pCurDqLayer->pPredWeightTable->sPredList[listIdx].iChromaOffset[iRefIdx][i];
    pDst = i ? pMCRefMem->pDstV : pMCRefMem->pDstU;
    //iLineStride = pMCRefMem->iDstLineChroma;

    for (int i = 0; i < iBlkHeight ; i++) {
      for (int j = 0; j < iBlkWidth; j++) {
        iPixel = j + i * (iLineStride);
        if (iLog2denom >= 1) {
          iPredTemp = ((pDst[iPixel] * iWoc + (1 << (iLog2denom - 1))) >> iLog2denom) + iOoc;

          pDst[iPixel] = WELS_CLIP3 (iPredTemp, 0, 255);
        } else {
          iPredTemp = pDst[iPixel] * iWoc + iOoc;

          pDst[iPixel] = WELS_CLIP3 (iPredTemp, 0, 255);

        }
      }

    }


  }
}

static void BiWeightPrediction (PDqLayer pCurDqLayer, sMCRefMember* pMCRefMem, sMCRefMember* pTempMCRefMem,
                                int32_t iRefIdx1, int32_t iRefIdx2, bool bWeightedBipredIdcIs1, int32_t iBlkWidth,
                                int32_t iBlkHeight) {
  int32_t iWoc1 = 0, iOoc1 = 0, iWoc2 = 0, iOoc2 = 0;
  int32_t iPredTemp, iLineStride;
  int32_t iPixel = 0;
  //luma
  int32_t iLog2denom = pCurDqLayer->pPredWeightTable->uiLumaLog2WeightDenom;
  if (bWeightedBipredIdcIs1) {
    iWoc1 = pCurDqLayer->pPredWeightTable->sPredList[LIST_0].iLumaWeight[iRefIdx1];
    iOoc1 = pCurDqLayer->pPredWeightTable->sPredList[LIST_0].iLumaOffset[iRefIdx1];
    iWoc2 = pCurDqLayer->pPredWeightTable->sPredList[LIST_1].iLumaWeight[iRefIdx2];
    iOoc2 = pCurDqLayer->pPredWeightTable->sPredList[LIST_1].iLumaOffset[iRefIdx2];
  } else {
    iWoc1 = pCurDqLayer->pPredWeightTable->iImplicitWeight[iRefIdx1][iRefIdx2];
    iWoc2 = 64 - iWoc1;
  }
  iLineStride = pMCRefMem->iDstLineLuma;

  for (int i = 0; i < iBlkHeight; i++) {
    for (int j = 0; j < iBlkWidth; j++) {
      iPixel = j + i * (iLineStride);
      iPredTemp = ((pMCRefMem->pDstY[iPixel] * iWoc1 + pTempMCRefMem->pDstY[iPixel] * iWoc2 + (1 << iLog2denom)) >>
                   (iLog2denom + 1)) + ((iOoc1 + iOoc2 + 1) >> 1);
      pMCRefMem->pDstY[iPixel] = WELS_CLIP3 (iPredTemp, 0, 255);
    }
  }

  //UV
  iBlkWidth = iBlkWidth >> 1;
  iBlkHeight = iBlkHeight >> 1;
  iLog2denom = pCurDqLayer->pPredWeightTable->uiChromaLog2WeightDenom;
  iLineStride = pMCRefMem->iDstLineChroma;

  uint8_t* pDst;
  uint8_t* pTempDst;
  for (int k = 0; k < 2; k++) {
    //iLog2denom = pCurDqLayer->pPredWeightTable->uiChromaLog2WeightDenom;
    if (bWeightedBipredIdcIs1) {
      iWoc1 = pCurDqLayer->pPredWeightTable->sPredList[LIST_0].iChromaWeight[iRefIdx1][k];
      iOoc1 = pCurDqLayer->pPredWeightTable->sPredList[LIST_0].iChromaOffset[iRefIdx1][k];
      iWoc2 = pCurDqLayer->pPredWeightTable->sPredList[LIST_1].iChromaWeight[iRefIdx2][k];
      iOoc2 = pCurDqLayer->pPredWeightTable->sPredList[LIST_1].iChromaOffset[iRefIdx2][k];
    }
    pDst  = k ? pMCRefMem->pDstV : pMCRefMem->pDstU;
    pTempDst = k ? pTempMCRefMem->pDstV : pTempMCRefMem->pDstU;
    //iLineStride = pMCRefMem->iDstLineChroma;

    for (int i = 0; i < iBlkHeight; i++) {
      for (int j = 0; j < iBlkWidth; j++) {
        iPixel = j + i * (iLineStride);
        iPredTemp = ((pDst[iPixel] * iWoc1 + pTempDst[iPixel] * iWoc2 + (1 << iLog2denom)) >> (iLog2denom + 1)) + ((
                      iOoc1 + iOoc2 + 1) >> 1);
        pDst[iPixel] = WELS_CLIP3 (iPredTemp, 0, 255);
      }
    }
  }
}

static void BiPrediction (PDqLayer pCurDqLayer, sMCRefMember* pMCRefMem, sMCRefMember* pTempMCRefMem, int32_t iBlkWidth,
                          int32_t iBlkHeight) {
  int32_t iPredTemp, iLineStride;
  int32_t iPixel = 0;
  //luma
  iLineStride = pMCRefMem->iDstLineLuma;

  for (int i = 0; i < iBlkHeight; i++) {
    for (int j = 0; j < iBlkWidth; j++) {
      iPixel = j + i * (iLineStride);
      iPredTemp = (pMCRefMem->pDstY[iPixel] + pTempMCRefMem->pDstY[iPixel] + 1) >> 1;
      pMCRefMem->pDstY[iPixel] = WELS_CLIP3 (iPredTemp, 0, 255);
    }
  }

  //UV
  iBlkWidth = iBlkWidth >> 1;
  iBlkHeight = iBlkHeight >> 1;
  iLineStride = pMCRefMem->iDstLineChroma;

  uint8_t* pDst;
  uint8_t* pTempDst;
  for (int k = 0; k < 2; k++) {
    pDst = k ? pMCRefMem->pDstV : pMCRefMem->pDstU;
    pTempDst = k ? pTempMCRefMem->pDstV : pTempMCRefMem->pDstU;
    //iLineStride = pMCRefMem->iDstLineChroma;

    for (int i = 0; i < iBlkHeight; i++) {
      for (int j = 0; j < iBlkWidth; j++) {
        iPixel = j + i * (iLineStride);
        iPredTemp = (pDst[iPixel] + pTempDst[iPixel] + 1) >> 1;
        pDst[iPixel] = WELS_CLIP3 (iPredTemp, 0, 255);
      }
    }
  }
}

int32_t GetInterPred (uint8_t* pPredY, uint8_t* pPredCb, uint8_t* pPredCr, PWelsDecoderContext pCtx) {
  sMCRefMember pMCRefMem;
  PDqLayer pCurDqLayer = pCtx->pCurDqLayer;
  SMcFunc* pMCFunc = &pCtx->sMcFunc;

  int32_t iMBXY = pCurDqLayer->iMbXyIndex;

  int16_t iMVs[2] = {0};

  uint32_t iMBType = pCurDqLayer->pDec->pMbType[iMBXY];

  int32_t iMBOffsetX = pCurDqLayer->iMbX << 4;
  int32_t iMBOffsetY = pCurDqLayer->iMbY << 4;

  int32_t iDstLineLuma   = pCtx->pDec->iLinesize[0];
  int32_t iDstLineChroma = pCtx->pDec->iLinesize[1];

  int32_t iBlk8X, iBlk8Y, iBlk4X, iBlk4Y, i, j, iIIdx, iJIdx;

  pMCRefMem.iPicWidth = (pCurDqLayer->sLayerInfo.sSliceInLayer.sSliceHeaderExt.sSliceHeader.iMbWidth << 4);
  pMCRefMem.iPicHeight = (pCurDqLayer->sLayerInfo.sSliceInLayer.sSliceHeaderExt.sSliceHeader.iMbHeight << 4);

  pMCRefMem.pDstY = pPredY;
  pMCRefMem.pDstU = pPredCb;
  pMCRefMem.pDstV = pPredCr;

  pMCRefMem.iDstLineLuma   = iDstLineLuma;
  pMCRefMem.iDstLineChroma = iDstLineChroma;

  int8_t iRefIndex = 0;

  switch (iMBType) {
  case MB_TYPE_SKIP:
  case MB_TYPE_16x16:
    iMVs[0] = pCurDqLayer->pDec->pMv[0][iMBXY][0][0];
    iMVs[1] = pCurDqLayer->pDec->pMv[0][iMBXY][0][1];
    iRefIndex = pCurDqLayer->pDec->pRefIndex[0][iMBXY][0];
    WELS_B_MB_REC_VERIFY (GetRefPic (&pMCRefMem, pCtx, iRefIndex, LIST_0));
    BaseMC (pCtx, &pMCRefMem, LIST_0, iRefIndex, iMBOffsetX, iMBOffsetY, pMCFunc, 16, 16, iMVs);

    if (pCurDqLayer->bUseWeightPredictionFlag) {
      iRefIndex = pCurDqLayer->pDec->pRefIndex[0][iMBXY][0];
      WeightPrediction (pCurDqLayer, &pMCRefMem, LIST_0, iRefIndex, 16, 16);
    }
    break;
  case MB_TYPE_16x8:
    iMVs[0] = pCurDqLayer->pDec->pMv[0][iMBXY][0][0];
    iMVs[1] = pCurDqLayer->pDec->pMv[0][iMBXY][0][1];
    iRefIndex = pCurDqLayer->pDec->pRefIndex[0][iMBXY][0];
    WELS_B_MB_REC_VERIFY (GetRefPic (&pMCRefMem, pCtx, iRefIndex, LIST_0));
    BaseMC (pCtx, &pMCRefMem, LIST_0, iRefIndex, iMBOffsetX, iMBOffsetY, pMCFunc, 16, 8, iMVs);

    if (pCurDqLayer->bUseWeightPredictionFlag) {
      WeightPrediction (pCurDqLayer, &pMCRefMem, LIST_0, iRefIndex, 16, 8);
    }

    iMVs[0] = pCurDqLayer->pDec->pMv[0][iMBXY][8][0];
    iMVs[1] = pCurDqLayer->pDec->pMv[0][iMBXY][8][1];
    iRefIndex = pCurDqLayer->pDec->pRefIndex[0][iMBXY][8];
    WELS_B_MB_REC_VERIFY (GetRefPic (&pMCRefMem, pCtx, iRefIndex, LIST_0));
    pMCRefMem.pDstY = pPredY  + (iDstLineLuma << 3);
    pMCRefMem.pDstU = pPredCb + (iDstLineChroma << 2);
    pMCRefMem.pDstV = pPredCr + (iDstLineChroma << 2);
    BaseMC (pCtx, &pMCRefMem, LIST_0, iRefIndex, iMBOffsetX, iMBOffsetY + 8, pMCFunc, 16, 8, iMVs);

    if (pCurDqLayer->bUseWeightPredictionFlag) {
      WeightPrediction (pCurDqLayer, &pMCRefMem, LIST_0, iRefIndex, 16, 8);
    }
    break;
  case MB_TYPE_8x16:
    iMVs[0] = pCurDqLayer->pDec->pMv[0][iMBXY][0][0];
    iMVs[1] = pCurDqLayer->pDec->pMv[0][iMBXY][0][1];
    iRefIndex = pCurDqLayer->pDec->pRefIndex[0][iMBXY][0];
    WELS_B_MB_REC_VERIFY (GetRefPic (&pMCRefMem, pCtx, iRefIndex, LIST_0));
    BaseMC (pCtx, &pMCRefMem, LIST_0, iRefIndex, iMBOffsetX, iMBOffsetY, pMCFunc, 8, 16, iMVs);
    if (pCurDqLayer->bUseWeightPredictionFlag) {
      WeightPrediction (pCurDqLayer, &pMCRefMem, LIST_0, iRefIndex, 8, 16);
    }

    iMVs[0] = pCurDqLayer->pDec->pMv[0][iMBXY][2][0];
    iMVs[1] = pCurDqLayer->pDec->pMv[0][iMBXY][2][1];
    iRefIndex = pCurDqLayer->pDec->pRefIndex[0][iMBXY][2];
    WELS_B_MB_REC_VERIFY (GetRefPic (&pMCRefMem, pCtx, iRefIndex, LIST_0));
    pMCRefMem.pDstY = pPredY + 8;
    pMCRefMem.pDstU = pPredCb + 4;
    pMCRefMem.pDstV = pPredCr + 4;
    BaseMC (pCtx, &pMCRefMem, LIST_0, iRefIndex, iMBOffsetX + 8, iMBOffsetY, pMCFunc, 8, 16, iMVs);

    if (pCurDqLayer->bUseWeightPredictionFlag) {
      WeightPrediction (pCurDqLayer, &pMCRefMem, LIST_0, iRefIndex, 8, 16);
    }
    break;
  case MB_TYPE_8x8:
  case MB_TYPE_8x8_REF0: {
    uint32_t iSubMBType;
    int32_t iXOffset, iYOffset;
    uint8_t* pDstY, *pDstU, *pDstV;
    for (i = 0; i < 4; i++) {
      iSubMBType = pCurDqLayer->pSubMbType[iMBXY][i];
      iBlk8X = (i & 1) << 3;
      iBlk8Y = (i >> 1) << 3;
      iXOffset = iMBOffsetX + iBlk8X;
      iYOffset = iMBOffsetY + iBlk8Y;

      iIIdx = ((i >> 1) << 3) + ((i & 1) << 1);
      iRefIndex = pCurDqLayer->pDec->pRefIndex[0][iMBXY][iIIdx];
      WELS_B_MB_REC_VERIFY (GetRefPic (&pMCRefMem, pCtx, iRefIndex, LIST_0));
      pDstY = pPredY + iBlk8X + iBlk8Y * iDstLineLuma;
      pDstU = pPredCb + (iBlk8X >> 1) + (iBlk8Y >> 1) * iDstLineChroma;
      pDstV = pPredCr + (iBlk8X >> 1) + (iBlk8Y >> 1) * iDstLineChroma;
      pMCRefMem.pDstY = pDstY;
      pMCRefMem.pDstU = pDstU;
      pMCRefMem.pDstV = pDstV;
      switch (iSubMBType) {
      case SUB_MB_TYPE_8x8:
        iMVs[0] = pCurDqLayer->pDec->pMv[0][iMBXY][iIIdx][0];
        iMVs[1] = pCurDqLayer->pDec->pMv[0][iMBXY][iIIdx][1];
        BaseMC (pCtx, &pMCRefMem, LIST_0, iRefIndex, iXOffset, iYOffset, pMCFunc, 8, 8, iMVs);
        if (pCurDqLayer->bUseWeightPredictionFlag) {

          WeightPrediction (pCurDqLayer, &pMCRefMem, LIST_0, iRefIndex, 8, 8);
        }

        break;
      case SUB_MB_TYPE_8x4:
        iMVs[0] = pCurDqLayer->pDec->pMv[0][iMBXY][iIIdx][0];
        iMVs[1] = pCurDqLayer->pDec->pMv[0][iMBXY][iIIdx][1];
        BaseMC (pCtx, &pMCRefMem, LIST_0, iRefIndex, iXOffset, iYOffset, pMCFunc, 8, 4, iMVs);
        if (pCurDqLayer->bUseWeightPredictionFlag) {

          WeightPrediction (pCurDqLayer, &pMCRefMem, LIST_0, iRefIndex, 8, 4);
        }


        iMVs[0] = pCurDqLayer->pDec->pMv[0][iMBXY][iIIdx + 4][0];
        iMVs[1] = pCurDqLayer->pDec->pMv[0][iMBXY][iIIdx + 4][1];
        pMCRefMem.pDstY += (iDstLineLuma << 2);
        pMCRefMem.pDstU += (iDstLineChroma << 1);
        pMCRefMem.pDstV += (iDstLineChroma << 1);
        BaseMC (pCtx, &pMCRefMem, LIST_0, iRefIndex, iXOffset, iYOffset + 4, pMCFunc, 8, 4, iMVs);
        if (pCurDqLayer->bUseWeightPredictionFlag) {

          WeightPrediction (pCurDqLayer, &pMCRefMem, LIST_0, iRefIndex, 8, 4);
        }

        break;
      case SUB_MB_TYPE_4x8:
        iMVs[0] = pCurDqLayer->pDec->pMv[0][iMBXY][iIIdx][0];
        iMVs[1] = pCurDqLayer->pDec->pMv[0][iMBXY][iIIdx][1];
        BaseMC (pCtx, &pMCRefMem, LIST_0, iRefIndex, iXOffset, iYOffset, pMCFunc, 4, 8, iMVs);
        if (pCurDqLayer->bUseWeightPredictionFlag) {

          WeightPrediction (pCurDqLayer, &pMCRefMem, LIST_0, iRefIndex, 4, 8);
        }


        iMVs[0] = pCurDqLayer->pDec->pMv[0][iMBXY][iIIdx + 1][0];
        iMVs[1] = pCurDqLayer->pDec->pMv[0][iMBXY][iIIdx + 1][1];
        pMCRefMem.pDstY += 4;
        pMCRefMem.pDstU += 2;
        pMCRefMem.pDstV += 2;
        BaseMC (pCtx, &pMCRefMem, LIST_0, iRefIndex, iXOffset + 4, iYOffset, pMCFunc, 4, 8, iMVs);
        if (pCurDqLayer->bUseWeightPredictionFlag) {

          WeightPrediction (pCurDqLayer, &pMCRefMem, LIST_0, iRefIndex, 4, 8);
        }

        break;
      case SUB_MB_TYPE_4x4: {
        for (j = 0; j < 4; j++) {
          int32_t iUVLineStride;
          iJIdx = ((j >> 1) << 2) + (j & 1);

          iBlk4X = (j & 1) << 2;
          iBlk4Y = (j >> 1) << 2;

          iUVLineStride = (iBlk4X >> 1) + (iBlk4Y >> 1) * iDstLineChroma;
          pMCRefMem.pDstY = pDstY + iBlk4X + iBlk4Y * iDstLineLuma;
          pMCRefMem.pDstU = pDstU + iUVLineStride;
          pMCRefMem.pDstV = pDstV + iUVLineStride;

          iMVs[0] = pCurDqLayer->pDec->pMv[0][iMBXY][iIIdx + iJIdx][0];
          iMVs[1] = pCurDqLayer->pDec->pMv[0][iMBXY][iIIdx + iJIdx][1];
          BaseMC (pCtx, &pMCRefMem, LIST_0, iRefIndex, iXOffset + iBlk4X, iYOffset + iBlk4Y, pMCFunc, 4, 4, iMVs);
          if (pCurDqLayer->bUseWeightPredictionFlag) {

            WeightPrediction (pCurDqLayer, &pMCRefMem, LIST_0, iRefIndex, 4, 4);
          }

        }
      }
      break;
      default:
        break;
      }
    }
  }
  break;
  default:
    break;
  }
  return ERR_NONE;
}

int32_t GetInterBPred (uint8_t* pPredYCbCr[3], uint8_t* pTempPredYCbCr[3], PWelsDecoderContext pCtx) {
  sMCRefMember pMCRefMem;
  sMCRefMember pTempMCRefMem;

  PDqLayer pCurDqLayer = pCtx->pCurDqLayer;
  SMcFunc* pMCFunc = &pCtx->sMcFunc;

  int32_t iMBXY = pCurDqLayer->iMbXyIndex;

  int16_t iMVs[2] = { 0 };

  uint32_t iMBType = pCurDqLayer->pDec->pMbType[iMBXY];

  int32_t iMBOffsetX = pCurDqLayer->iMbX << 4;
  int32_t iMBOffsetY = pCurDqLayer->iMbY << 4;

  int32_t iDstLineLuma = pCtx->pDec->iLinesize[0];
  int32_t iDstLineChroma = pCtx->pDec->iLinesize[1];


  pMCRefMem.iPicWidth = (pCurDqLayer->sLayerInfo.sSliceInLayer.sSliceHeaderExt.sSliceHeader.iMbWidth << 4);
  pMCRefMem.iPicHeight = (pCurDqLayer->sLayerInfo.sSliceInLayer.sSliceHeaderExt.sSliceHeader.iMbHeight << 4);

  pMCRefMem.pDstY = pPredYCbCr[0];
  pMCRefMem.pDstU = pPredYCbCr[1];
  pMCRefMem.pDstV = pPredYCbCr[2];

  pMCRefMem.iDstLineLuma = iDstLineLuma;
  pMCRefMem.iDstLineChroma = iDstLineChroma;

  pTempMCRefMem = pMCRefMem;
  pTempMCRefMem.pDstY = pTempPredYCbCr[0];
  pTempMCRefMem.pDstU = pTempPredYCbCr[1];
  pTempMCRefMem.pDstV = pTempPredYCbCr[2];


  int8_t iRefIndex0 = 0;
  int8_t iRefIndex1 = 0;
  int8_t iRefIndex = 0;

  bool bWeightedBipredIdcIs1 = pCurDqLayer->sLayerInfo.pPps->uiWeightedBipredIdc == 1;

  if (IS_INTER_16x16 (iMBType)) {
    if (IS_TYPE_L0 (iMBType) && IS_TYPE_L1 (iMBType)) {
      iMVs[0] = pCurDqLayer->pDec->pMv[LIST_0][iMBXY][0][0];
      iMVs[1] = pCurDqLayer->pDec->pMv[LIST_0][iMBXY][0][1];
      iRefIndex0 = pCurDqLayer->pDec->pRefIndex[LIST_0][iMBXY][0];
      WELS_B_MB_REC_VERIFY (GetRefPic (&pMCRefMem, pCtx, iRefIndex0, LIST_0));
      BaseMC (pCtx, &pMCRefMem, LIST_0, iRefIndex0, iMBOffsetX, iMBOffsetY, pMCFunc, 16, 16, iMVs);

      iMVs[0] = pCurDqLayer->pDec->pMv[LIST_1][iMBXY][0][0];
      iMVs[1] = pCurDqLayer->pDec->pMv[LIST_1][iMBXY][0][1];
      iRefIndex1 = pCurDqLayer->pDec->pRefIndex[LIST_1][iMBXY][0];
      WELS_B_MB_REC_VERIFY (GetRefPic (&pTempMCRefMem, pCtx, iRefIndex1, LIST_1));
      BaseMC (pCtx, &pTempMCRefMem, LIST_1, iRefIndex1, iMBOffsetX, iMBOffsetY, pMCFunc, 16, 16, iMVs);
      if (pCurDqLayer->bUseWeightedBiPredIdc) {
        BiWeightPrediction (pCurDqLayer, &pMCRefMem, &pTempMCRefMem, iRefIndex0, iRefIndex1, bWeightedBipredIdcIs1, 16, 16);
      } else {
        BiPrediction (pCurDqLayer, &pMCRefMem, &pTempMCRefMem,  16, 16);
      }
    } else {
      int32_t listIdx = (iMBType & MB_TYPE_P0L0) ? LIST_0 : LIST_1;
      iMVs[0] = pCurDqLayer->pDec->pMv[listIdx][iMBXY][0][0];
      iMVs[1] = pCurDqLayer->pDec->pMv[listIdx][iMBXY][0][1];
      iRefIndex = pCurDqLayer->pDec->pRefIndex[listIdx][iMBXY][0];
      WELS_B_MB_REC_VERIFY (GetRefPic (&pMCRefMem, pCtx, iRefIndex, listIdx));
      BaseMC (pCtx, &pMCRefMem, listIdx, iRefIndex, iMBOffsetX, iMBOffsetY, pMCFunc, 16, 16, iMVs);
      if (bWeightedBipredIdcIs1) {
        WeightPrediction (pCurDqLayer, &pMCRefMem, listIdx, iRefIndex, 16, 16);
      }
    }
  } else if (IS_INTER_16x8 (iMBType)) {
    for (int32_t i = 0; i < 2; ++i) {
      int32_t iPartIdx = i << 3;
      uint32_t listCount = 0;
      int32_t lastListIdx = LIST_0;
      for (int32_t listIdx = LIST_0; listIdx < LIST_A; ++listIdx) {
        if (IS_DIR (iMBType, i, listIdx)) {
          lastListIdx = listIdx;
          iMVs[0] = pCurDqLayer->pDec->pMv[listIdx][iMBXY][iPartIdx][0];
          iMVs[1] = pCurDqLayer->pDec->pMv[listIdx][iMBXY][iPartIdx][1];
          iRefIndex = pCurDqLayer->pDec->pRefIndex[listIdx][iMBXY][iPartIdx];
          WELS_B_MB_REC_VERIFY (GetRefPic (&pMCRefMem, pCtx, iRefIndex, listIdx));
          if (i) {
            pMCRefMem.pDstY += (iDstLineLuma << 3);
            pMCRefMem.pDstU += (iDstLineChroma << 2);
            pMCRefMem.pDstV += (iDstLineChroma << 2);
          }
          BaseMC (pCtx, &pMCRefMem, listIdx, iRefIndex, iMBOffsetX, iMBOffsetY + iPartIdx, pMCFunc, 16, 8, iMVs);
          if (++listCount == 2) {
            iMVs[0] = pCurDqLayer->pDec->pMv[LIST_1][iMBXY][iPartIdx][0];
            iMVs[1] = pCurDqLayer->pDec->pMv[LIST_1][iMBXY][iPartIdx][1];
            iRefIndex1 = pCurDqLayer->pDec->pRefIndex[LIST_1][iMBXY][iPartIdx];
            WELS_B_MB_REC_VERIFY (GetRefPic (&pTempMCRefMem, pCtx, iRefIndex1, LIST_1));
            if (i) {
              pTempMCRefMem.pDstY += (iDstLineLuma << 3);
              pTempMCRefMem.pDstU += (iDstLineChroma << 2);
              pTempMCRefMem.pDstV += (iDstLineChroma << 2);
            }
            BaseMC (pCtx, &pTempMCRefMem, LIST_1, iRefIndex1, iMBOffsetX, iMBOffsetY + iPartIdx, pMCFunc, 16, 8, iMVs);
            if (pCurDqLayer->bUseWeightedBiPredIdc) {
              iRefIndex0 = pCurDqLayer->pDec->pRefIndex[LIST_0][iMBXY][iPartIdx];
              iRefIndex1 = pCurDqLayer->pDec->pRefIndex[LIST_1][iMBXY][iPartIdx];
              BiWeightPrediction (pCurDqLayer, &pMCRefMem, &pTempMCRefMem, iRefIndex0, iRefIndex1, bWeightedBipredIdcIs1, 16, 8);
            } else {
              BiPrediction (pCurDqLayer, &pMCRefMem, &pTempMCRefMem, 16, 8);
            }
          }
        }
      }
      if (listCount == 1) {
        if (bWeightedBipredIdcIs1) {
          iRefIndex = pCurDqLayer->pDec->pRefIndex[lastListIdx][iMBXY][iPartIdx];
          WeightPrediction (pCurDqLayer, &pMCRefMem, lastListIdx, iRefIndex, 16, 8);
        }
      }
    }
  } else if (IS_INTER_8x16 (iMBType)) {
    for (int32_t i = 0; i < 2; ++i) {
      uint32_t listCount = 0;
      int32_t lastListIdx = LIST_0;
      for (int32_t listIdx = LIST_0; listIdx < LIST_A; ++listIdx) {
        if (IS_DIR (iMBType, i, listIdx)) {
          lastListIdx = listIdx;
          iMVs[0] = pCurDqLayer->pDec->pMv[listIdx][iMBXY][i << 1][0];
          iMVs[1] = pCurDqLayer->pDec->pMv[listIdx][iMBXY][i << 1][1];
          iRefIndex = pCurDqLayer->pDec->pRefIndex[listIdx][iMBXY][i << 1];
          WELS_B_MB_REC_VERIFY (GetRefPic (&pMCRefMem, pCtx, iRefIndex, listIdx));
          if (i) {
            pMCRefMem.pDstY += 8;
            pMCRefMem.pDstU += 4;
            pMCRefMem.pDstV += 4;
          }
          BaseMC (pCtx, &pMCRefMem, listIdx, iRefIndex, iMBOffsetX + (i ? 8 : 0), iMBOffsetY, pMCFunc, 8, 16, iMVs);
          if (++listCount == 2) {
            iMVs[0] = pCurDqLayer->pDec->pMv[LIST_1][iMBXY][i << 1][0];
            iMVs[1] = pCurDqLayer->pDec->pMv[LIST_1][iMBXY][i << 1][1];
            iRefIndex1 = pCurDqLayer->pDec->pRefIndex[LIST_1][iMBXY][i << 1];
            WELS_B_MB_REC_VERIFY (GetRefPic (&pTempMCRefMem, pCtx, iRefIndex1, LIST_1));
            if (i) {
              pTempMCRefMem.pDstY += 8;
              pTempMCRefMem.pDstU += 4;
              pTempMCRefMem.pDstV += 4;
            }
            BaseMC (pCtx, &pTempMCRefMem, LIST_1, iRefIndex1, iMBOffsetX + (i ? 8 : 0), iMBOffsetY, pMCFunc, 8, 16, iMVs);
            if (pCurDqLayer->bUseWeightedBiPredIdc) {
              iRefIndex0 = pCurDqLayer->pDec->pRefIndex[LIST_0][iMBXY][i << 1];
              iRefIndex1 = pCurDqLayer->pDec->pRefIndex[LIST_1][iMBXY][i << 1];
              BiWeightPrediction (pCurDqLayer, &pMCRefMem, &pTempMCRefMem, iRefIndex0, iRefIndex1, bWeightedBipredIdcIs1, 8, 16);
            } else {
              BiPrediction (pCurDqLayer, &pMCRefMem, &pTempMCRefMem, 8, 16);
            }
          }
        }
      }
      if (listCount == 1) {
        if (bWeightedBipredIdcIs1) {
          iRefIndex = pCurDqLayer->pDec->pRefIndex[lastListIdx][iMBXY][i << 1];
          WeightPrediction (pCurDqLayer, &pMCRefMem, lastListIdx, iRefIndex, 8, 16);
        }
      }
    }
  } else if (IS_Inter_8x8 (iMBType)) {
    int32_t iBlk8X, iBlk8Y, iBlk4X, iBlk4Y, iIIdx, iJIdx;
    uint32_t iSubMBType;
    int32_t iXOffset, iYOffset;
    uint8_t* pDstY, *pDstU, *pDstV;
    uint8_t* pDstY2, *pDstU2, *pDstV2;
    for (int32_t i = 0; i < 4; i++) {
      iSubMBType = pCurDqLayer->pSubMbType[iMBXY][i];
      iBlk8X = (i & 1) << 3;
      iBlk8Y = (i >> 1) << 3;
      iXOffset = iMBOffsetX + iBlk8X;
      iYOffset = iMBOffsetY + iBlk8Y;

      iIIdx = ((i >> 1) << 3) + ((i & 1) << 1);

      pDstY = pPredYCbCr[0] + iBlk8X + iBlk8Y * iDstLineLuma;
      pDstU = pPredYCbCr[1] + (iBlk8X >> 1) + (iBlk8Y >> 1) * iDstLineChroma;
      pDstV = pPredYCbCr[2] + (iBlk8X >> 1) + (iBlk8Y >> 1) * iDstLineChroma;
      pMCRefMem.pDstY = pDstY;
      pMCRefMem.pDstU = pDstU;
      pMCRefMem.pDstV = pDstV;

      pTempMCRefMem = pMCRefMem;
      pDstY2 = pTempPredYCbCr[0] + iBlk8X + iBlk8Y * iDstLineLuma;
      pDstU2 = pTempPredYCbCr[1] + (iBlk8X >> 1) + (iBlk8Y >> 1) * iDstLineChroma;
      pDstV2 = pTempPredYCbCr[2] + (iBlk8X >> 1) + (iBlk8Y >> 1) * iDstLineChroma;

      pTempMCRefMem.pDstY = pDstY2;
      pTempMCRefMem.pDstU = pDstU2;
      pTempMCRefMem.pDstV = pDstV2;

      if ((IS_TYPE_L0 (iSubMBType) && IS_TYPE_L1 (iSubMBType))) {
        iRefIndex0 = pCurDqLayer->pDec->pRefIndex[LIST_0][iMBXY][iIIdx];
        WELS_B_MB_REC_VERIFY (GetRefPic (&pMCRefMem, pCtx, iRefIndex0, LIST_0));

        iRefIndex1 = pCurDqLayer->pDec->pRefIndex[LIST_1][iMBXY][iIIdx];
        WELS_B_MB_REC_VERIFY (GetRefPic (&pTempMCRefMem, pCtx, iRefIndex1, LIST_1));
      } else {
        int32_t listIdx = IS_TYPE_L0 (iSubMBType) ? LIST_0 : LIST_1;
        iRefIndex = pCurDqLayer->pDec->pRefIndex[listIdx][iMBXY][iIIdx];
        WELS_B_MB_REC_VERIFY (GetRefPic (&pMCRefMem, pCtx, iRefIndex, listIdx));
      }

      if (IS_SUB_8x8 (iSubMBType)) {
        if (IS_TYPE_L0 (iSubMBType) && IS_TYPE_L1 (iSubMBType)) {
          iMVs[0] = pCurDqLayer->pDec->pMv[LIST_0][iMBXY][iIIdx][0];
          iMVs[1] = pCurDqLayer->pDec->pMv[LIST_0][iMBXY][iIIdx][1];
          BaseMC (pCtx, &pMCRefMem, LIST_0, iRefIndex0, iXOffset, iYOffset, pMCFunc, 8, 8, iMVs);

          iMVs[0] = pCurDqLayer->pDec->pMv[LIST_1][iMBXY][iIIdx][0];
          iMVs[1] = pCurDqLayer->pDec->pMv[LIST_1][iMBXY][iIIdx][1];
          BaseMC (pCtx, &pTempMCRefMem, LIST_1, iRefIndex1, iXOffset, iYOffset, pMCFunc, 8, 8, iMVs);

          if (pCurDqLayer->bUseWeightedBiPredIdc) {
            BiWeightPrediction (pCurDqLayer, &pMCRefMem, &pTempMCRefMem, iRefIndex0, iRefIndex1, bWeightedBipredIdcIs1, 8, 8);
          } else {
            BiPrediction (pCurDqLayer, &pMCRefMem, &pTempMCRefMem,  8, 8);
          }
        } else {
          int32_t listIdx = IS_TYPE_L0 (iSubMBType) ? LIST_0 : LIST_1;
          iMVs[0] = pCurDqLayer->pDec->pMv[listIdx][iMBXY][iIIdx][0];
          iMVs[1] = pCurDqLayer->pDec->pMv[listIdx][iMBXY][iIIdx][1];
          iRefIndex = pCurDqLayer->pDec->pRefIndex[listIdx][iMBXY][iIIdx];
          BaseMC (pCtx, &pMCRefMem, listIdx, iRefIndex, iXOffset, iYOffset, pMCFunc, 8, 8, iMVs);
          if (bWeightedBipredIdcIs1) {
            WeightPrediction (pCurDqLayer, &pMCRefMem, listIdx, iRefIndex, 8, 8);
          }
        }
      } else if (IS_SUB_8x4 (iSubMBType)) {
        if (IS_TYPE_L0 (iSubMBType) && IS_TYPE_L1 (iSubMBType)) { //B_Bi_8x4
          iMVs[0] = pCurDqLayer->pDec->pMv[LIST_0][iMBXY][iIIdx][0];
          iMVs[1] = pCurDqLayer->pDec->pMv[LIST_0][iMBXY][iIIdx][1];
          BaseMC (pCtx, &pMCRefMem, LIST_0, iRefIndex0, iXOffset, iYOffset, pMCFunc, 8, 4, iMVs);
          iMVs[0] = pCurDqLayer->pDec->pMv[LIST_1][iMBXY][iIIdx][0];
          iMVs[1] = pCurDqLayer->pDec->pMv[LIST_1][iMBXY][iIIdx][1];
          BaseMC (pCtx, &pTempMCRefMem, LIST_1, iRefIndex1, iXOffset, iYOffset, pMCFunc, 8, 4, iMVs);

          if (pCurDqLayer->bUseWeightedBiPredIdc) {
            BiWeightPrediction (pCurDqLayer, &pMCRefMem, &pTempMCRefMem, iRefIndex0, iRefIndex1, bWeightedBipredIdcIs1, 8, 4);
          } else {
            BiPrediction (pCurDqLayer, &pMCRefMem, &pTempMCRefMem,  8, 4);
          }

          pMCRefMem.pDstY += (iDstLineLuma << 2);
          pMCRefMem.pDstU += (iDstLineChroma << 1);
          pMCRefMem.pDstV += (iDstLineChroma << 1);
          iMVs[0] = pCurDqLayer->pDec->pMv[LIST_0][iMBXY][iIIdx + 4][0];
          iMVs[1] = pCurDqLayer->pDec->pMv[LIST_0][iMBXY][iIIdx + 4][1];
          BaseMC (pCtx, &pMCRefMem, LIST_0, iRefIndex0, iXOffset, iYOffset + 4, pMCFunc, 8, 4, iMVs);

          pTempMCRefMem.pDstY += (iDstLineLuma << 2);
          pTempMCRefMem.pDstU += (iDstLineChroma << 1);
          pTempMCRefMem.pDstV += (iDstLineChroma << 1);
          iMVs[0] = pCurDqLayer->pDec->pMv[LIST_1][iMBXY][iIIdx + 4][0];
          iMVs[1] = pCurDqLayer->pDec->pMv[LIST_1][iMBXY][iIIdx + 4][1];
          BaseMC (pCtx, &pTempMCRefMem, LIST_1, iRefIndex1, iXOffset, iYOffset + 4, pMCFunc, 8, 4, iMVs);

          if (pCurDqLayer->bUseWeightedBiPredIdc) {
            BiWeightPrediction (pCurDqLayer, &pMCRefMem, &pTempMCRefMem, iRefIndex0, iRefIndex1, bWeightedBipredIdcIs1, 8, 4);
          } else {
            BiPrediction (pCurDqLayer, &pMCRefMem, &pTempMCRefMem,  8, 4);
          }
        } else { //B_L0_8x4 B_L1_8x4
          int32_t listIdx = IS_TYPE_L0 (iSubMBType) ? LIST_0 : LIST_1;
          iMVs[0] = pCurDqLayer->pDec->pMv[listIdx][iMBXY][iIIdx][0];
          iMVs[1] = pCurDqLayer->pDec->pMv[listIdx][iMBXY][iIIdx][1];
          iRefIndex = pCurDqLayer->pDec->pRefIndex[listIdx][iMBXY][iIIdx];
          BaseMC (pCtx, &pMCRefMem, listIdx, iRefIndex, iXOffset, iYOffset, pMCFunc, 8, 4, iMVs);
          pMCRefMem.pDstY += (iDstLineLuma << 2);
          pMCRefMem.pDstU += (iDstLineChroma << 1);
          pMCRefMem.pDstV += (iDstLineChroma << 1);
          iMVs[0] = pCurDqLayer->pDec->pMv[listIdx][iMBXY][iIIdx + 4][0];
          iMVs[1] = pCurDqLayer->pDec->pMv[listIdx][iMBXY][iIIdx + 4][1];
          BaseMC (pCtx, &pMCRefMem, listIdx, iRefIndex, iXOffset, iYOffset + 4, pMCFunc, 8, 4, iMVs);
          if (bWeightedBipredIdcIs1) {
            WeightPrediction (pCurDqLayer, &pMCRefMem, listIdx, iRefIndex, 8, 4);
          }
        }
      } else if (IS_SUB_4x8 (iSubMBType)) {
        if (IS_TYPE_L0 (iSubMBType) && IS_TYPE_L1 (iSubMBType)) { //B_Bi_4x8
          iMVs[0] = pCurDqLayer->pDec->pMv[LIST_0][iMBXY][iIIdx][0];
          iMVs[1] = pCurDqLayer->pDec->pMv[LIST_0][iMBXY][iIIdx][1];
          BaseMC (pCtx, &pMCRefMem, LIST_0, iRefIndex0, iXOffset, iYOffset, pMCFunc, 4, 8, iMVs);
          iMVs[0] = pCurDqLayer->pDec->pMv[LIST_1][iMBXY][iIIdx][0];
          iMVs[1] = pCurDqLayer->pDec->pMv[LIST_1][iMBXY][iIIdx][1];
          BaseMC (pCtx, &pTempMCRefMem, LIST_1, iRefIndex1, iXOffset, iYOffset, pMCFunc, 4, 8, iMVs);

          if (pCurDqLayer->bUseWeightedBiPredIdc) {
            BiWeightPrediction (pCurDqLayer, &pMCRefMem, &pTempMCRefMem, iRefIndex0, iRefIndex1, bWeightedBipredIdcIs1, 4, 8);
          } else {
            BiPrediction (pCurDqLayer, &pMCRefMem, &pTempMCRefMem,  4, 8);
          }

          pMCRefMem.pDstY += 4;
          pMCRefMem.pDstU += 2;
          pMCRefMem.pDstV += 2;
          iMVs[0] = pCurDqLayer->pDec->pMv[LIST_0][iMBXY][iIIdx + 1][0];
          iMVs[1] = pCurDqLayer->pDec->pMv[LIST_0][iMBXY][iIIdx + 1][1];
          BaseMC (pCtx, &pMCRefMem, LIST_0, iRefIndex0, iXOffset + 4, iYOffset, pMCFunc, 4, 8, iMVs);

          pTempMCRefMem.pDstY += 4;
          pTempMCRefMem.pDstU += 2;
          pTempMCRefMem.pDstV += 2;
          iMVs[0] = pCurDqLayer->pDec->pMv[LIST_1][iMBXY][iIIdx + 1][0];
          iMVs[1] = pCurDqLayer->pDec->pMv[LIST_1][iMBXY][iIIdx + 1][1];
          BaseMC (pCtx, &pTempMCRefMem, LIST_1, iRefIndex1, iXOffset + 4, iYOffset, pMCFunc, 4, 8, iMVs);

          if (pCurDqLayer->bUseWeightedBiPredIdc) {
            BiWeightPrediction (pCurDqLayer, &pMCRefMem, &pTempMCRefMem, iRefIndex0, iRefIndex1, bWeightedBipredIdcIs1, 4, 8);
          } else {
            BiPrediction (pCurDqLayer, &pMCRefMem, &pTempMCRefMem, 4, 8);
          }
        } else { //B_L0_4x8 B_L1_4x8
          int32_t listIdx = IS_TYPE_L0 (iSubMBType) ? LIST_0 : LIST_1;
          iMVs[0] = pCurDqLayer->pDec->pMv[listIdx][iMBXY][iIIdx][0];
          iMVs[1] = pCurDqLayer->pDec->pMv[listIdx][iMBXY][iIIdx][1];
          iRefIndex = pCurDqLayer->pDec->pRefIndex[listIdx][iMBXY][iIIdx];
          BaseMC (pCtx, &pMCRefMem, listIdx, iRefIndex, iXOffset, iYOffset, pMCFunc, 4, 8, iMVs);
          pMCRefMem.pDstY += 4;
          pMCRefMem.pDstU += 2;
          pMCRefMem.pDstV += 2;
          iMVs[0] = pCurDqLayer->pDec->pMv[listIdx][iMBXY][iIIdx + 1][0];
          iMVs[1] = pCurDqLayer->pDec->pMv[listIdx][iMBXY][iIIdx + 1][1];
          BaseMC (pCtx, &pMCRefMem, listIdx, iRefIndex, iXOffset + 4, iYOffset, pMCFunc, 4, 8, iMVs);
          if (bWeightedBipredIdcIs1) {
            WeightPrediction (pCurDqLayer, &pMCRefMem, listIdx, iRefIndex, 4, 8);
          }
        }
      } else if (IS_SUB_4x4 (iSubMBType)) {
        if (IS_TYPE_L0 (iSubMBType) && IS_TYPE_L1 (iSubMBType)) {
          for (int32_t j = 0; j < 4; j++) {
            int32_t iUVLineStride;
            iJIdx = ((j >> 1) << 2) + (j & 1);

            iBlk4X = (j & 1) << 2;
            iBlk4Y = (j >> 1) << 2;

            iUVLineStride = (iBlk4X >> 1) + (iBlk4Y >> 1) * iDstLineChroma;
            pMCRefMem.pDstY = pDstY + iBlk4X + iBlk4Y * iDstLineLuma;
            pMCRefMem.pDstU = pDstU + iUVLineStride;
            pMCRefMem.pDstV = pDstV + iUVLineStride;

            iMVs[0] = pCurDqLayer->pDec->pMv[LIST_0][iMBXY][iIIdx + iJIdx][0];
            iMVs[1] = pCurDqLayer->pDec->pMv[LIST_0][iMBXY][iIIdx + iJIdx][1];
            BaseMC (pCtx, &pMCRefMem, LIST_0, iRefIndex0, iXOffset + iBlk4X, iYOffset + iBlk4Y, pMCFunc, 4, 4, iMVs);

            pTempMCRefMem.pDstY = pDstY2 + iBlk8X + iBlk8Y * iDstLineLuma;
            pTempMCRefMem.pDstU = pDstU2 + iUVLineStride;
            pTempMCRefMem.pDstV = pDstV2 + iUVLineStride;;

            iMVs[0] = pCurDqLayer->pDec->pMv[LIST_1][iMBXY][iIIdx + iJIdx][0];
            iMVs[1] = pCurDqLayer->pDec->pMv[LIST_1][iMBXY][iIIdx + iJIdx][1];
            BaseMC (pCtx, &pTempMCRefMem, LIST_1, iRefIndex1, iXOffset + iBlk4X, iYOffset + iBlk4Y, pMCFunc, 4, 4, iMVs);

            if (pCurDqLayer->bUseWeightedBiPredIdc) {
              BiWeightPrediction (pCurDqLayer, &pMCRefMem, &pTempMCRefMem, iRefIndex0, iRefIndex1, bWeightedBipredIdcIs1, 4, 4);
            } else {
              BiPrediction (pCurDqLayer, &pMCRefMem, &pTempMCRefMem,  4, 4);
            }
          }
        } else {
          int32_t listIdx = IS_TYPE_L0 (iSubMBType) ? LIST_0 : LIST_1;
          iRefIndex = pCurDqLayer->pDec->pRefIndex[listIdx][iMBXY][iIIdx];
          for (int32_t j = 0; j < 4; j++) {
            int32_t iUVLineStride;
            iJIdx = ((j >> 1) << 2) + (j & 1);

            iBlk4X = (j & 1) << 2;
            iBlk4Y = (j >> 1) << 2;

            iUVLineStride = (iBlk4X >> 1) + (iBlk4Y >> 1) * iDstLineChroma;
            pMCRefMem.pDstY = pDstY + iBlk4X + iBlk4Y * iDstLineLuma;
            pMCRefMem.pDstU = pDstU + iUVLineStride;
            pMCRefMem.pDstV = pDstV + iUVLineStride;

            iMVs[0] = pCurDqLayer->pDec->pMv[listIdx][iMBXY][iIIdx + iJIdx][0];
            iMVs[1] = pCurDqLayer->pDec->pMv[listIdx][iMBXY][iIIdx + iJIdx][1];
            BaseMC (pCtx, &pMCRefMem, listIdx, iRefIndex, iXOffset + iBlk4X, iYOffset + iBlk4Y, pMCFunc, 4, 4, iMVs);
            if (bWeightedBipredIdcIs1) {
              WeightPrediction (pCurDqLayer, &pMCRefMem, listIdx, iRefIndex, 4, 4);
            }
          }
        }
      }
    }
  }
  return ERR_NONE;
}

int32_t RecChroma (int32_t iMBXY, PWelsDecoderContext pCtx, int16_t* pScoeffLevel, PDqLayer pDqLayer) {
  int32_t iChromaStride = pCtx->pCurDqLayer->pDec->iLinesize[1];
  PIdctFourResAddPredFunc pIdctFourResAddPredFunc = pCtx->pIdctFourResAddPredFunc;

  uint8_t i = 0;
  uint8_t uiCbpC = pDqLayer->pCbp[iMBXY] >> 4;

  if (1 == uiCbpC || 2 == uiCbpC) {
    for (i = 0; i < 2; i++) {
      int16_t* pRS = pScoeffLevel + 256 + (i << 6);
      uint8_t* pPred = pDqLayer->pPred[i + 1];
      const int8_t* pNzc = pDqLayer->pNzc[iMBXY] + 16 + 2 * i;

      /*1 chroma is divided 4 4x4_block to idct*/
      pIdctFourResAddPredFunc (pPred, iChromaStride, pRS, pNzc);
    }
  }

  return ERR_NONE;
}

} // namespace WelsDec
