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
 * \file    encode_mb.c
 *
 * \brief   Implementaion for pCurMb encoding
 *
 * \date    05/19/2009 Created
 *************************************************************************************
 */


#include "svc_encode_mb.h"
#include "encode_mb_aux.h"
#include "decode_mb_aux.h"
#include "ls_defines.h"

namespace WelsEnc {
void WelsDctMb (int16_t* pRes, uint8_t* pEncMb, int32_t iEncStride, uint8_t* pBestPred, PDctFunc pfDctFourT4) {
  pfDctFourT4 (pRes,       pEncMb,                      iEncStride, pBestPred,       16);
  pfDctFourT4 (pRes + 64,  pEncMb + 8,                  iEncStride, pBestPred + 8,   16);
  pfDctFourT4 (pRes + 128, pEncMb + 8 * iEncStride,     iEncStride, pBestPred + 128, 16);
  pfDctFourT4 (pRes + 192, pEncMb + 8 * iEncStride + 8, iEncStride, pBestPred + 136, 16);
}

void WelsEncRecI16x16Y (sWelsEncCtx* pEncCtx, SMB* pCurMb, SMbCache* pMbCache) {
  ENFORCE_STACK_ALIGN_1D (int16_t, aDctT4Dc, 16, 16)
  SWelsFuncPtrList* pFuncList   = pEncCtx->pFuncList;
  SDqLayer* pCurDqLayer         = pEncCtx->pCurDqLayer;
  const int32_t kiEncStride     = pCurDqLayer->iEncStride[0];
  int16_t* pRes                 = pMbCache->pCoeffLevel;
  uint8_t* pPred                = pMbCache->SPicData.pCsMb[0];
  const int32_t kiRecStride     = pCurDqLayer->iCsStride[0];
  int16_t* pBlock               = pMbCache->pDct->iLumaBlock[0];
  uint8_t* pBestPred            = pMbCache->pMemPredLuma;
  const uint8_t* kpNoneZeroCountIdx = &g_kuiMbCountScan4Idx[0];
  uint8_t i, uiQp               = pCurMb->uiLumaQp;
  uint32_t uiNoneZeroCount, uiNoneZeroCountMbAc = 0, uiCountI16x16Dc;

  const int16_t* pMF = g_kiQuantMF[uiQp];
  const int16_t* pFF = g_iQuantIntraFF[uiQp];

  WelsDctMb (pRes,  pMbCache->SPicData.pEncMb[0], kiEncStride, pBestPred, pEncCtx->pFuncList->pfDctFourT4);

  pFuncList->pfTransformHadamard4x4Dc (aDctT4Dc, pRes);
  pFuncList->pfQuantizationDc4x4 (aDctT4Dc, pFF[0] << 1, pMF[0]>>1);
  pFuncList->pfScan4x4 (pMbCache->pDct->iLumaI16x16Dc, aDctT4Dc);
  uiCountI16x16Dc = pFuncList->pfGetNoneZeroCount (pMbCache->pDct->iLumaI16x16Dc);

  for (i = 0; i < 4; i++) {
    pFuncList->pfQuantizationFour4x4 (pRes, pFF,  pMF);
    pFuncList->pfScan4x4Ac (pBlock,      pRes);
    pFuncList->pfScan4x4Ac (pBlock + 16, pRes + 16);
    pFuncList->pfScan4x4Ac (pBlock + 32, pRes + 32);
    pFuncList->pfScan4x4Ac (pBlock + 48, pRes + 48);
    pRes += 64;
    pBlock += 64;
  }
  pRes -= 256;
  pBlock -= 256;

  for (i = 0; i < 16; i++) {
    uiNoneZeroCount = pFuncList->pfGetNoneZeroCount (pBlock);
    pCurMb->pNonZeroCount[*kpNoneZeroCountIdx++] = uiNoneZeroCount;
    uiNoneZeroCountMbAc += uiNoneZeroCount;
    pBlock += 16;
  }

  if (uiCountI16x16Dc > 0) {
    if (uiQp < 12) {
      WelsIHadamard4x4Dc (aDctT4Dc);
      WelsDequantLumaDc4x4 (aDctT4Dc, uiQp);
    } else
      pFuncList->pfDequantizationIHadamard4x4 (aDctT4Dc, g_kuiDequantCoeff[uiQp][0] >> 2);
  }

  if (uiNoneZeroCountMbAc > 0) {
    pCurMb->uiCbp = 15;
    pFuncList->pfDequantizationFour4x4 (pRes, g_kuiDequantCoeff[uiQp]);
    pFuncList->pfDequantizationFour4x4 (pRes + 64, g_kuiDequantCoeff[uiQp]);
    pFuncList->pfDequantizationFour4x4 (pRes + 128, g_kuiDequantCoeff[uiQp]);
    pFuncList->pfDequantizationFour4x4 (pRes + 192, g_kuiDequantCoeff[uiQp]);

    pRes[0]  = aDctT4Dc[0];
    pRes[16] = aDctT4Dc[1];
    pRes[32] = aDctT4Dc[4];
    pRes[48] = aDctT4Dc[5];
    pRes[64] = aDctT4Dc[2];
    pRes[80] = aDctT4Dc[3];
    pRes[96] = aDctT4Dc[6];
    pRes[112] = aDctT4Dc[7];
    pRes[128] = aDctT4Dc[8];
    pRes[144] = aDctT4Dc[9];
    pRes[160] = aDctT4Dc[12];
    pRes[176] = aDctT4Dc[13];
    pRes[192] = aDctT4Dc[10];
    pRes[208] = aDctT4Dc[11];
    pRes[224] = aDctT4Dc[14];
    pRes[240] = aDctT4Dc[15];

    pFuncList->pfIDctFourT4 (pPred,                       kiRecStride, pBestPred,        16, pRes);
    pFuncList->pfIDctFourT4 (pPred + 8,                   kiRecStride, pBestPred + 8,    16, pRes + 64);
    pFuncList->pfIDctFourT4 (pPred + kiRecStride * 8,     kiRecStride, pBestPred + 128,  16, pRes + 128);
    pFuncList->pfIDctFourT4 (pPred + kiRecStride * 8 + 8, kiRecStride, pBestPred + 136,  16, pRes + 192);
  } else if (uiCountI16x16Dc > 0) {
    pFuncList->pfIDctI16x16Dc (pPred, kiRecStride, pBestPred, 16, aDctT4Dc);
  } else {
    pFuncList->pfCopy16x16Aligned (pPred, kiRecStride, pBestPred, 16);
  }
}
void WelsEncRecI4x4Y (sWelsEncCtx* pEncCtx, SMB* pCurMb, SMbCache* pMbCache, uint8_t uiI4x4Idx) {
  SWelsFuncPtrList* pFuncList   = pEncCtx->pFuncList;
  SDqLayer* pCurDqLayer         = pEncCtx->pCurDqLayer;
  int32_t iEncStride            = pCurDqLayer->iEncStride[0];
  uint8_t uiQp                  = pCurMb->uiLumaQp;

  int16_t* pResI4x4 = pMbCache->pCoeffLevel;
  uint8_t* pPredI4x4;

  uint8_t* pPred     = pMbCache->SPicData.pCsMb[0];
  int32_t iRecStride = pCurDqLayer->iCsStride[0];

  uint32_t uiOffset = g_kuiMbCountScan4Idx[uiI4x4Idx];
  uint8_t* pEncMb = pMbCache->SPicData.pEncMb[0];
  uint8_t* pBestPred = pMbCache->pBestPredI4x4Blk4;
  int16_t* pBlock = pMbCache->pDct->iLumaBlock[uiI4x4Idx];

  const int16_t* pMF = g_kiQuantMF[uiQp];
  const int16_t* pFF = g_iQuantIntraFF[uiQp];

  int32_t* pStrideEncBlockOffset = pEncCtx->pStrideTab->pStrideEncBlockOffset[pEncCtx->uiDependencyId];
  int32_t* pStrideDecBlockOffset = pEncCtx->pStrideTab->pStrideDecBlockOffset[pEncCtx->uiDependencyId][0 ==
                                   pEncCtx->uiTemporalId];
  int32_t iNoneZeroCount = 0;

  pFuncList->pfDctT4 (pResI4x4, & (pEncMb[pStrideEncBlockOffset[uiI4x4Idx]]), iEncStride, pBestPred, 4);
  pFuncList->pfQuantization4x4 (pResI4x4, pFF, pMF);
  pFuncList->pfScan4x4 (pBlock, pResI4x4);

  iNoneZeroCount = pFuncList->pfGetNoneZeroCount (pBlock);
  pCurMb->pNonZeroCount[uiOffset] = iNoneZeroCount;

  pPredI4x4 = pPred + pStrideDecBlockOffset[uiI4x4Idx];
  if (iNoneZeroCount > 0) {
    pCurMb->uiCbp |= 1 << (uiI4x4Idx >> 2);
    pFuncList->pfDequantization4x4 (pResI4x4, g_kuiDequantCoeff[uiQp]);
    pFuncList->pfIDctT4 (pPredI4x4, iRecStride, pBestPred, 4, pResI4x4);
  } else
    pFuncList->pfCopy4x4 (pPredI4x4, iRecStride, pBestPred, 4);
}

void WelsEncInterY (SWelsFuncPtrList* pFuncList, SMB* pCurMb, SMbCache* pMbCache) {
  PQuantizationMaxFunc pfQuantizationFour4x4Max         = pFuncList->pfQuantizationFour4x4Max;
  PSetMemoryZero pfSetMemZeroSize8                      = pFuncList->pfSetMemZeroSize8;
  PSetMemoryZero pfSetMemZeroSize64                     = pFuncList->pfSetMemZeroSize64;
  PScanFunc pfScan4x4                                   = pFuncList->pfScan4x4;
  PCalculateSingleCtrFunc pfCalculateSingleCtr4x4       = pFuncList->pfCalculateSingleCtr4x4;
  PGetNoneZeroCountFunc pfGetNoneZeroCount              = pFuncList->pfGetNoneZeroCount;
  PDeQuantizationFunc pfDequantizationFour4x4           = pFuncList->pfDequantizationFour4x4;
  int16_t* pRes = pMbCache->pCoeffLevel;
  int32_t iSingleCtrMb = 0, iSingleCtr8x8[4];
  int16_t* pBlock = pMbCache->pDct->iLumaBlock[0];
  uint8_t uiQp = pCurMb->uiLumaQp;
  const int16_t* pMF = g_kiQuantMF[uiQp];
  const int16_t* pFF = g_kiQuantInterFF[uiQp];
  int16_t aMax[16];
  int32_t i, j, iNoneZeroCountMbDcAc = 0, iNoneZeroCount = 0;

  for (i = 0; i < 4; i++) {
    pfQuantizationFour4x4Max (pRes, pFF,  pMF, aMax + (i << 2));
    iSingleCtr8x8[i] = 0;
    for (j = 0; j < 4; j++) {
      if (aMax[ (i << 2) + j] == 0)
        pfSetMemZeroSize8 (pBlock, 32);
      else {
        pfScan4x4 (pBlock, pRes);
        if (aMax[ (i << 2) + j] > 1)
          iSingleCtr8x8[i] += 9;
        else if (iSingleCtr8x8[i] < 6)
          iSingleCtr8x8[i] += pfCalculateSingleCtr4x4 (pBlock);
      }
      pRes += 16;
      pBlock += 16;
    }
    iSingleCtrMb += iSingleCtr8x8[i];
  }
  pBlock -= 256;
  pRes -= 256;

  memset (pCurMb->pNonZeroCount, 0, 16);


  if (iSingleCtrMb < 6) {  //from JVT-O079
    iNoneZeroCountMbDcAc = 0;
    pfSetMemZeroSize64 (pRes,  768); // confirmed_safe_unsafe_usage
  } else {
    const uint8_t* kpNoneZeroCountIdx = g_kuiMbCountScan4Idx;
    for (i = 0; i < 4; i++) {
      if (iSingleCtr8x8[i] >= 4) {
        for (j = 0; j < 4; j++) {
          iNoneZeroCount = pfGetNoneZeroCount (pBlock);
          pCurMb->pNonZeroCount[*kpNoneZeroCountIdx++] = iNoneZeroCount;
          iNoneZeroCountMbDcAc += iNoneZeroCount;
          pBlock += 16;
        }
        pfDequantizationFour4x4 (pRes, g_kuiDequantCoeff[uiQp]);
        pCurMb->uiCbp |= 1 << i;
      } else { // set zero for an 8x8 pBlock
        pfSetMemZeroSize64 (pRes, 128); // confirmed_safe_unsafe_usage
        kpNoneZeroCountIdx += 4;
        pBlock += 64;
      }
      pRes += 64;
    }
  }
}

void    WelsEncRecUV (SWelsFuncPtrList* pFuncList, SMB* pCurMb, SMbCache* pMbCache, int16_t* pRes, int32_t iUV) {
  PQuantizationHadamardFunc pfQuantizationHadamard2x2   = pFuncList->pfQuantizationHadamard2x2;
  PQuantizationMaxFunc pfQuantizationFour4x4Max         = pFuncList->pfQuantizationFour4x4Max;
  PSetMemoryZero pfSetMemZeroSize8                      = pFuncList->pfSetMemZeroSize8;
  PSetMemoryZero pfSetMemZeroSize64                     = pFuncList->pfSetMemZeroSize64;
  PScanFunc pfScan4x4Ac                                 = pFuncList->pfScan4x4Ac;
  PCalculateSingleCtrFunc pfCalculateSingleCtr4x4       = pFuncList->pfCalculateSingleCtr4x4;
  PGetNoneZeroCountFunc pfGetNoneZeroCount              = pFuncList->pfGetNoneZeroCount;
  PDeQuantizationFunc pfDequantizationFour4x4           = pFuncList->pfDequantizationFour4x4;
  const int32_t kiInterFlag                             = !IS_INTRA (pCurMb->uiMbType);
  const uint8_t kiQp                                    = pCurMb->uiChromaQp;
  uint8_t i, uiNoneZeroCount, uiNoneZeroCountMbAc       = 0, uiNoneZeroCountMbDc = 0;
  uint8_t uiNoneZeroCountOffset                         = (iUV - 1) << 1;   //UV==1 or 2
  uint8_t uiSubMbIdx                                    = 16 + ((iUV - 1) << 2); //uiSubMbIdx == 16 or 20
  int16_t* iChromaDc = pMbCache->pDct->iChromaDc[iUV - 1], *pBlock = pMbCache->pDct->iChromaBlock[ (iUV - 1) << 2];
  int16_t aDct2x2[4], j, aMax[4];
  int32_t iSingleCtr8x8 = 0;
  const int16_t* pMF = g_kiQuantMF[kiQp];
  const int16_t* pFF = g_kiQuantInterFF[ (!kiInterFlag) * 6 + kiQp];

  uiNoneZeroCountMbDc = pfQuantizationHadamard2x2 (pRes, pFF[0] << 1, pMF[0]>>1, aDct2x2, iChromaDc);

  pfQuantizationFour4x4Max (pRes, pFF,  pMF, aMax);

  for (j = 0; j < 4; j++) {
    if (aMax[j] == 0)
      pfSetMemZeroSize8 (pBlock, 32);
    else {
      pfScan4x4Ac (pBlock, pRes);
      if (kiInterFlag) {
        if (aMax[j] > 1)
          iSingleCtr8x8 += 9;
        else if (iSingleCtr8x8 < 7)
          iSingleCtr8x8 += pfCalculateSingleCtr4x4 (pBlock);
      } else
        iSingleCtr8x8 = INT_MAX;
    }
    pRes += 16;
    pBlock += 16;
  }
  pRes -= 64;

  if (iSingleCtr8x8 < 7) { //from JVT-O079
    pfSetMemZeroSize64 (pRes, 128); // confirmed_safe_unsafe_usage
    ST16 (&pCurMb->pNonZeroCount[16 + uiNoneZeroCountOffset], 0);
    ST16 (&pCurMb->pNonZeroCount[20 + uiNoneZeroCountOffset], 0);
  } else {
    const uint8_t* kpNoneZeroCountIdx = &g_kuiMbCountScan4Idx[uiSubMbIdx];
    pBlock -= 64;
    for (i = 0; i < 4; i++) {
      uiNoneZeroCount = pfGetNoneZeroCount (pBlock);
      pCurMb->pNonZeroCount[*kpNoneZeroCountIdx++] = uiNoneZeroCount;
      uiNoneZeroCountMbAc += uiNoneZeroCount;
      pBlock += 16;
    }
    pfDequantizationFour4x4 (pRes, g_kuiDequantCoeff[pCurMb->uiChromaQp]);
    pCurMb->uiCbp &= 0x0F;
    pCurMb->uiCbp |= 0x20;
  }

  if (uiNoneZeroCountMbDc > 0) {
    WelsDequantIHadamard2x2Dc (aDct2x2, g_kuiDequantCoeff[kiQp][0]);
    if (2 != (pCurMb->uiCbp >> 4))
      pCurMb->uiCbp |= (0x01 << 4) ;
    pRes[0]  = aDct2x2[0];
    pRes[16] = aDct2x2[1];
    pRes[32] = aDct2x2[2];
    pRes[48] = aDct2x2[3];
  }
}


void    WelsRecPskip (SDqLayer* pCurLayer, SWelsFuncPtrList* pFuncList, SMB* pCurMb, SMbCache* pMbCache) {
  int32_t* iRecStride   = pCurLayer->iCsStride;
  uint8_t** pCsMb       = &pMbCache->SPicData.pCsMb[0];

  pFuncList->pfCopy16x16Aligned (pCsMb[0],  *iRecStride++,  pMbCache->pSkipMb,       16);
  pFuncList->pfCopy8x8Aligned (pCsMb[1],    *iRecStride++,  pMbCache->pSkipMb + 256, 8);
  pFuncList->pfCopy8x8Aligned (pCsMb[2],    *iRecStride,    pMbCache->pSkipMb + 320, 8);
  pFuncList->pfSetMemZeroSize8 (pCurMb->pNonZeroCount,  24);
}

bool WelsTryPYskip (sWelsEncCtx* pEncCtx, SMB* pCurMb, SMbCache* pMbCache) {
  int32_t iSingleCtrMb = 0;
  int16_t* pRes = pMbCache->pCoeffLevel;
  const uint8_t kuiQp = pCurMb->uiLumaQp;

  int16_t* pBlock = pMbCache->pDct->iLumaBlock[0];
  uint16_t aMax[4], i, j;
  const int16_t* pMF = g_kiQuantMF[kuiQp];
  const int16_t* pFF = g_kiQuantInterFF[kuiQp];

  for (i = 0; i < 4; i++) {
    pEncCtx->pFuncList->pfQuantizationFour4x4Max (pRes, pFF,  pMF, (int16_t*)aMax);

    for (j = 0; j < 4; j++) {
      if (aMax[j] > 1) return false; // iSingleCtrMb += 9, can't be P_SKIP
      else if (aMax[j] == 1) {
        pEncCtx->pFuncList->pfScan4x4 (pBlock, pRes); //
        iSingleCtrMb += pEncCtx->pFuncList->pfCalculateSingleCtr4x4 (pBlock);
      }
      if (iSingleCtrMb >= 6) return false; //from JVT-O079
      pRes += 16;
      pBlock += 16;
    }
  }
  return true;
}

bool    WelsTryPUVskip (sWelsEncCtx* pEncCtx, SMB* pCurMb, SMbCache* pMbCache, int32_t iUV) {
  int16_t* pRes = ((iUV == 1) ? & (pMbCache->pCoeffLevel[256]) : & (pMbCache->pCoeffLevel[256 + 64]));

  const uint8_t kuiQp = g_kuiChromaQpTable[CLIP3_QP_0_51 (pCurMb->uiLumaQp +
                        pEncCtx->pCurDqLayer->sLayerInfo.pPpsP->uiChromaQpIndexOffset)];

  const int16_t* pMF = g_kiQuantMF[kuiQp];
  const int16_t* pFF = g_kiQuantInterFF[kuiQp];

  if (pEncCtx->pFuncList->pfQuantizationHadamard2x2Skip (pRes, pFF[0] << 1, pMF[0]>>1))
    return false;
  else {
    uint16_t aMax[4], j;
    int32_t iSingleCtrMb = 0;
    int16_t* pBlock = pMbCache->pDct->iChromaBlock[ (iUV - 1) << 2];
    pEncCtx->pFuncList->pfQuantizationFour4x4Max (pRes, pFF,  pMF, (int16_t*)aMax);

    for (j = 0; j < 4; j++) {
      if (aMax[j] > 1) return false;   // iSingleCtrMb += 9, can't be P_SKIP
      else if (aMax[j] == 1) {
        pEncCtx->pFuncList->pfScan4x4Ac (pBlock, pRes);
        iSingleCtrMb += pEncCtx->pFuncList->pfCalculateSingleCtr4x4 (pBlock);
      }
      if (iSingleCtrMb >= 7) return false; //from JVT-O079
      pRes += 16;
      pBlock += 16;
    }
    return true;
  }
}

} // namespace WelsEnc
