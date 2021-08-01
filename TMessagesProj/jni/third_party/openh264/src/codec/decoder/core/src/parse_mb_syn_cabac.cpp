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
 *      parse_mb_syn_cabac.cpp: cabac parse for syntax elements
 */
#include "parse_mb_syn_cabac.h"
#include "decode_slice.h"
#include "mv_pred.h"
#include "error_code.h"
#include <stdio.h>

namespace WelsDec {
#define IDX_UNUSED -1

static const int16_t g_kMaxPos       [] = {IDX_UNUSED, 15, 14, 15, 3, 14, 63, 3, 3, 14, 14};
static const int16_t g_kMaxC2       [] = {IDX_UNUSED, 4, 4, 4, 3, 4, 4, 3, 3, 4, 4};
static const int16_t g_kBlockCat2CtxOffsetCBF[] = {IDX_UNUSED, 0, 4, 8, 12, 16, 0, 12, 12, 16, 16};
static const int16_t g_kBlockCat2CtxOffsetMap [] = {IDX_UNUSED, 0, 15, 29, 44, 47, 0, 44, 44, 47, 47};
static const int16_t g_kBlockCat2CtxOffsetLast[] = {IDX_UNUSED, 0, 15, 29, 44, 47, 0, 44, 44, 47, 47};
static const int16_t g_kBlockCat2CtxOffsetOne [] = {IDX_UNUSED, 0, 10, 20, 30, 39, 0, 30, 30, 39, 39};
static const int16_t g_kBlockCat2CtxOffsetAbs [] = {IDX_UNUSED, 0, 10, 20, 30, 39, 0, 30, 30, 39, 39};

const uint8_t g_kTopBlkInsideMb[24] = { //for index with z-order 0~23
  //  0   1 | 4  5      luma 8*8 block           pNonZeroCount[16+8]
  0,  0,  1,  1,   //  2   3 | 6  7        0  |  1                  0   1   2   3
  0,  0,  1,  1,   //---------------      ---------                 4   5   6   7
  1,  1,  1,  1,   //  8   9 | 12 13       2  |  3                  8   9  10  11
  1,  1,  1,  1,  // 10  11 | 14 15-----------------------------> 12  13  14  15
  0,  0,  1,  1,   //----------------    chroma 8*8 block          16  17  18  19
  0,  0,  1,  1   // 16  17 | 20 21        0    1                 20  21  22  23
  // 18  19 | 22 23
};

const uint8_t g_kLeftBlkInsideMb[24] = { //for index with z-order 0~23
  //  0   1 | 4  5      luma 8*8 block           pNonZeroCount[16+8]
  0,  1,  0,  1,   //  2   3 | 6  7        0  |  1                  0   1   2   3
  1,  1,  1,  1,   //---------------      ---------                 4   5   6   7
  0,  1,  0,  1,   //  8   9 | 12 13       2  |  3                  8   9  10  11
  1,  1,  1,  1,  // 10  11 | 14 15-----------------------------> 12  13  14  15
  0,  1,  0,  1,   //----------------    chroma 8*8 block          16  17  18  19
  0,  1,  0,  1   // 16  17 | 20 21        0    1                 20  21  22  23
  // 18  19 | 22 23
};

static uint32_t DecodeCabacIntraMbType (PWelsDecoderContext pCtx, PWelsNeighAvail pNeighAvail, int ctx_base) {
  uint32_t uiCode;
  uint32_t uiMbType = 0;

  PWelsCabacDecEngine pCabacDecEngine = pCtx->pCabacDecEngine;
  PWelsCabacCtx pBinCtx = pCtx->pCabacCtx + ctx_base;

  WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx, uiCode));
  if (!uiCode) {
    return 0; /* I4x4 */
  }

  WELS_READ_VERIFY (DecodeTerminateCabac (pCabacDecEngine, uiCode));
  if (uiCode) {
    return 25; /* PCM */
  }
  uiMbType = 1; /* I16x16 */
  WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 1, uiCode)); /* cbp_luma != 0 */
  uiMbType += 12 * uiCode;

  WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 2, uiCode));
  if (uiCode) {
    WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 2, uiCode));
    uiMbType += 4 + 4 * uiCode;
  }
  WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 3, uiCode));
  uiMbType += 2 * uiCode;
  WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 3, uiCode));
  uiMbType += 1 * uiCode;
  return uiMbType;
}

void UpdateP16x8RefIdxCabac (PDqLayer pCurDqLayer, int8_t pRefIndex[LIST_A][30], int32_t iPartIdx, const int8_t iRef,
                             const int8_t iListIdx) {
  uint32_t iRef32Bit = (uint32_t) iRef;
  const int32_t iRef4Bytes = (iRef32Bit << 24) | (iRef32Bit << 16) | (iRef32Bit << 8) | iRef32Bit;
  int32_t iMbXy = pCurDqLayer->iMbXyIndex;
  const uint8_t iScan4Idx = g_kuiScan4[iPartIdx];
  const uint8_t iScan4Idx4 = 4 + iScan4Idx;
  const uint8_t iCacheIdx = g_kuiCache30ScanIdx[iPartIdx];
  const uint8_t iCacheIdx6 = 6 + iCacheIdx;
  //mb
  ST32 (&pCurDqLayer->pDec->pRefIndex[iListIdx][iMbXy][iScan4Idx ], iRef4Bytes);
  ST32 (&pCurDqLayer->pDec->pRefIndex[iListIdx][iMbXy][iScan4Idx4], iRef4Bytes);
  //cache
  ST32 (&pRefIndex[iListIdx][iCacheIdx ], iRef4Bytes);
  ST32 (&pRefIndex[iListIdx][iCacheIdx6], iRef4Bytes);
}

void UpdateP8x16RefIdxCabac (PDqLayer pCurDqLayer, int8_t pRefIndex[LIST_A][30], int32_t iPartIdx, const int8_t iRef,
                             const int8_t iListIdx) {
  uint16_t iRef16Bit = (uint16_t) iRef;
  const int16_t iRef2Bytes = (iRef16Bit << 8) | iRef16Bit;
  int32_t i;
  int32_t iMbXy = pCurDqLayer->iMbXyIndex;
  for (i = 0; i < 2; i++, iPartIdx += 8) {
    const uint8_t iScan4Idx = g_kuiScan4[iPartIdx];
    const uint8_t iCacheIdx = g_kuiCache30ScanIdx[iPartIdx];
    const uint8_t iScan4Idx4 = 4 + iScan4Idx;
    const uint8_t iCacheIdx6 = 6 + iCacheIdx;
    //mb
    ST16 (&pCurDqLayer->pDec->pRefIndex[iListIdx][iMbXy][iScan4Idx ], iRef2Bytes);
    ST16 (&pCurDqLayer->pDec->pRefIndex[iListIdx][iMbXy][iScan4Idx4], iRef2Bytes);
    //cache
    ST16 (&pRefIndex[iListIdx][iCacheIdx ], iRef2Bytes);
    ST16 (&pRefIndex[iListIdx][iCacheIdx6], iRef2Bytes);
  }
}

void UpdateP8x8RefIdxCabac (PDqLayer pCurDqLayer, int8_t pRefIndex[LIST_A][30], int32_t iPartIdx, const int8_t iRef,
                            const int8_t iListIdx) {
  int32_t iMbXy = pCurDqLayer->iMbXyIndex;
  const uint8_t iScan4Idx = g_kuiScan4[iPartIdx];
  pCurDqLayer->pDec->pRefIndex[iListIdx][iMbXy][iScan4Idx] = pCurDqLayer->pDec->pRefIndex[iListIdx][iMbXy][iScan4Idx + 1]
      =
        pCurDqLayer->pDec->pRefIndex[iListIdx][iMbXy][iScan4Idx + 4] = pCurDqLayer->pDec->pRefIndex[iListIdx][iMbXy][iScan4Idx +
            5] = iRef;
}

void UpdateP8x8DirectCabac (PDqLayer pCurDqLayer, int32_t iPartIdx) {
  int32_t iMbXy = pCurDqLayer->iMbXyIndex;
  const uint8_t iScan4Idx = g_kuiScan4[iPartIdx];
  pCurDqLayer->pDirect[iMbXy][iScan4Idx] = pCurDqLayer->pDirect[iMbXy][iScan4Idx + 1] =
        pCurDqLayer->pDirect[iMbXy][iScan4Idx + 4] = pCurDqLayer->pDirect[iMbXy][iScan4Idx + 5] = 1;
}

void UpdateP16x16DirectCabac (PDqLayer pCurDqLayer) {
  int32_t i;
  int32_t iMbXy = pCurDqLayer->iMbXyIndex;
  const int16_t direct = (1 << 8) | 1;
  for (i = 0; i < 16; i += 4) {
    const uint8_t kuiScan4Idx = g_kuiScan4[i];
    const uint8_t kuiScan4IdxPlus4 = 4 + kuiScan4Idx;
    ST16 (&pCurDqLayer->pDirect[iMbXy][kuiScan4Idx], direct);
    ST16 (&pCurDqLayer->pDirect[iMbXy][kuiScan4IdxPlus4], direct);
  }
}

void UpdateP16x16MvdCabac (SDqLayer* pCurDqLayer, int16_t pMvd[2], const int8_t iListIdx) {
  int32_t pMvd32[2];
  ST32 (&pMvd32[0], LD32 (pMvd));
  ST32 (&pMvd32[1], LD32 (pMvd));
  int32_t i;
  int32_t iMbXy = pCurDqLayer->iMbXyIndex;
  for (i = 0; i < 16; i += 2) {
    ST64 (pCurDqLayer->pMvd[iListIdx][iMbXy][i], LD64 (pMvd32));
  }
}

void UpdateP16x8MvdCabac (SDqLayer* pCurDqLayer, int16_t pMvdCache[LIST_A][30][MV_A], int32_t iPartIdx, int16_t pMvd[2],
                          const int8_t iListIdx) {
  int32_t pMvd32[2];
  ST32 (&pMvd32[0], LD32 (pMvd));
  ST32 (&pMvd32[1], LD32 (pMvd));
  int32_t i;
  int32_t iMbXy = pCurDqLayer->iMbXyIndex;
  for (i = 0; i < 2; i++, iPartIdx += 4) {
    const uint8_t iScan4Idx = g_kuiScan4[iPartIdx];
    const uint8_t iScan4Idx4 = 4 + iScan4Idx;
    const uint8_t iCacheIdx = g_kuiCache30ScanIdx[iPartIdx];
    const uint8_t iCacheIdx6 = 6 + iCacheIdx;
    //mb
    ST64 (pCurDqLayer->pMvd[iListIdx][iMbXy][  iScan4Idx ], LD64 (pMvd32));
    ST64 (pCurDqLayer->pMvd[iListIdx][iMbXy][  iScan4Idx4], LD64 (pMvd32));
    //cache
    ST64 (pMvdCache[iListIdx][  iCacheIdx ], LD64 (pMvd32));
    ST64 (pMvdCache[iListIdx][  iCacheIdx6], LD64 (pMvd32));
  }
}

void UpdateP8x16MvdCabac (SDqLayer* pCurDqLayer, int16_t pMvdCache[LIST_A][30][MV_A], int32_t iPartIdx, int16_t pMvd[2],
                          const int8_t iListIdx) {
  int32_t pMvd32[2];
  ST32 (&pMvd32[0], LD32 (pMvd));
  ST32 (&pMvd32[1], LD32 (pMvd));
  int32_t i;
  int32_t iMbXy = pCurDqLayer->iMbXyIndex;

  for (i = 0; i < 2; i++, iPartIdx += 8) {
    const uint8_t iScan4Idx = g_kuiScan4[iPartIdx];
    const uint8_t iCacheIdx = g_kuiCache30ScanIdx[iPartIdx];
    const uint8_t iScan4Idx4 = 4 + iScan4Idx;
    const uint8_t iCacheIdx6 = 6 + iCacheIdx;
    //mb
    ST64 (pCurDqLayer->pMvd[iListIdx][iMbXy][  iScan4Idx ], LD64 (pMvd32));
    ST64 (pCurDqLayer->pMvd[iListIdx][iMbXy][  iScan4Idx4], LD64 (pMvd32));
    //cache
    ST64 (pMvdCache[iListIdx][  iCacheIdx ], LD64 (pMvd32));
    ST64 (pMvdCache[iListIdx][  iCacheIdx6], LD64 (pMvd32));
  }
}

int32_t ParseEndOfSliceCabac (PWelsDecoderContext pCtx, uint32_t& uiBinVal) {
  uiBinVal = 0;
  WELS_READ_VERIFY (DecodeTerminateCabac (pCtx->pCabacDecEngine, uiBinVal));
  return ERR_NONE;
}

int32_t ParseSkipFlagCabac (PWelsDecoderContext pCtx, PWelsNeighAvail pNeighAvail, uint32_t& uiSkip) {
  uiSkip = 0;
  int32_t iCtxInc = NEW_CTX_OFFSET_SKIP;
  iCtxInc += (pNeighAvail->iLeftAvail && !IS_SKIP (pNeighAvail->iLeftType)) + (pNeighAvail->iTopAvail
             && !IS_SKIP (pNeighAvail->iTopType));
  if (B_SLICE == pCtx->eSliceType)
    iCtxInc += 13;
  PWelsCabacCtx pBinCtx = (pCtx->pCabacCtx + iCtxInc);
  WELS_READ_VERIFY (DecodeBinCabac (pCtx->pCabacDecEngine, pBinCtx, uiSkip));
  return ERR_NONE;
}


int32_t ParseMBTypeISliceCabac (PWelsDecoderContext pCtx, PWelsNeighAvail pNeighAvail, uint32_t& uiBinVal) {
  uint32_t uiCode;
  int32_t iIdxA = 0, iIdxB = 0;
  int32_t iCtxInc;
  uiBinVal = 0;
  PWelsCabacDecEngine pCabacDecEngine = pCtx->pCabacDecEngine;
  PWelsCabacCtx pBinCtx = pCtx->pCabacCtx + NEW_CTX_OFFSET_MB_TYPE_I; //I mode in I slice
  iIdxA = (pNeighAvail->iLeftAvail) && (pNeighAvail->iLeftType != MB_TYPE_INTRA4x4
                                        && pNeighAvail->iLeftType != MB_TYPE_INTRA8x8);
  iIdxB = (pNeighAvail->iTopAvail) && (pNeighAvail->iTopType != MB_TYPE_INTRA4x4
                                       && pNeighAvail->iTopType != MB_TYPE_INTRA8x8);
  iCtxInc = iIdxA + iIdxB;
  WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + iCtxInc, uiCode));
  uiBinVal = uiCode;
  if (uiBinVal != 0) {  //I16x16
    WELS_READ_VERIFY (DecodeTerminateCabac (pCabacDecEngine, uiCode));
    if (uiCode == 1)
      uiBinVal = 25; //I_PCM
    else {
      WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 3, uiCode));
      uiBinVal = 1 + uiCode * 12;
      //decoding of uiCbp:0,1,2
      WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 4, uiCode));
      if (uiCode != 0) {
        WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 5, uiCode));
        uiBinVal += 4;
        if (uiCode != 0)
          uiBinVal += 4;
      }
      //decoding of I pred-mode: 0,1,2,3
      WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 6, uiCode));
      uiBinVal += (uiCode << 1);
      WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 7, uiCode));
      uiBinVal += uiCode;
    }
  }
  //I4x4
  return ERR_NONE;
}

int32_t ParseMBTypePSliceCabac (PWelsDecoderContext pCtx, PWelsNeighAvail pNeighAvail, uint32_t& uiMbType) {
  uint32_t uiCode;
  uiMbType = 0;
  PWelsCabacDecEngine pCabacDecEngine = pCtx->pCabacDecEngine;

  PWelsCabacCtx pBinCtx = pCtx->pCabacCtx + NEW_CTX_OFFSET_SKIP;
  WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 3, uiCode));
  if (uiCode) {
    // Intra MB
    WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 6, uiCode));
    if (uiCode) { // Intra 16x16
      WELS_READ_VERIFY (DecodeTerminateCabac (pCabacDecEngine, uiCode));
      if (uiCode) {
        uiMbType = 30;
        return ERR_NONE;//MB_TYPE_INTRA_PCM;
      }

      WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 7, uiCode));
      uiMbType = 6 + uiCode * 12;

      //uiCbp: 0,1,2
      WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 8, uiCode));
      if (uiCode) {
        uiMbType += 4;
        WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 8, uiCode));
        if (uiCode)
          uiMbType += 4;
      }

      //IPredMode: 0,1,2,3
      WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 9, uiCode));
      uiMbType += (uiCode << 1);
      WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 9, uiCode));
      uiMbType += uiCode;
    } else
      // Intra 4x4
      uiMbType = 5;
  } else { // P MB
    WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 4, uiCode));
    if (uiCode) { //second bit
      WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 6, uiCode));
      if (uiCode)
        uiMbType = 1;
      else
        uiMbType = 2;
    } else {
      WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 5, uiCode));
      if (uiCode)
        uiMbType = 3;
      else
        uiMbType = 0;
    }
  }
  return ERR_NONE;
}

int32_t ParseMBTypeBSliceCabac (PWelsDecoderContext pCtx, PWelsNeighAvail pNeighAvail, uint32_t& uiMbType) {
  uint32_t uiCode;
  uiMbType = 0;
  int32_t iIdxA = 0, iIdxB = 0;
  int32_t iCtxInc;

  PWelsCabacDecEngine pCabacDecEngine = pCtx->pCabacDecEngine;
  PWelsCabacCtx pBinCtx = pCtx->pCabacCtx + 27; //B slice

  iIdxA = (pNeighAvail->iLeftAvail) && !IS_DIRECT (pNeighAvail->iLeftType);
  iIdxB = (pNeighAvail->iTopAvail) && !IS_DIRECT (pNeighAvail->iTopType);

  iCtxInc = iIdxA + iIdxB;
  WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + iCtxInc, uiCode));
  if (!uiCode)
    uiMbType = 0; // Bi_Direct
  else {
    WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 3, uiCode));
    if (!uiCode) {
      WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 5, uiCode));
      uiMbType = 1 + uiCode; // 16x16 L0L1
    } else {
      WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 4, uiCode));
      uiMbType = uiCode << 3;
      WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 5, uiCode));
      uiMbType |= uiCode << 2;
      WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 5, uiCode));
      uiMbType |= uiCode << 1;
      WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 5, uiCode));
      uiMbType |= uiCode;
      if (uiMbType < 8) {
        uiMbType += 3;
        return ERR_NONE;
      } else if (uiMbType == 13) {
        uiMbType = DecodeCabacIntraMbType (pCtx, pNeighAvail, 32) + 23;
        return ERR_NONE;
      } else if (uiMbType == 14) {
        uiMbType = 11; // Bi8x16
        return ERR_NONE;
      } else if (uiMbType == 15) {
        uiMbType = 22; // 8x8
        return ERR_NONE;
      }
      uiMbType <<= 1;
      WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 5, uiCode));
      uiMbType |= uiCode;
      uiMbType -= 4;
    }
  }
  return ERR_NONE;
}

int32_t ParseTransformSize8x8FlagCabac (PWelsDecoderContext pCtx, PWelsNeighAvail pNeighAvail,
                                        bool& bTransformSize8x8Flag) {
  uint32_t uiCode;
  int32_t iIdxA, iIdxB;
  int32_t iCtxInc;
  PWelsCabacDecEngine pCabacDecEngine = pCtx->pCabacDecEngine;
  PWelsCabacCtx pBinCtx = pCtx->pCabacCtx + NEW_CTX_OFFSET_TS_8x8_FLAG;
  iIdxA = (pNeighAvail->iLeftAvail) && (pCtx->pCurDqLayer->pTransformSize8x8Flag[pCtx->pCurDqLayer->iMbXyIndex - 1]);
  iIdxB = (pNeighAvail->iTopAvail)
          && (pCtx->pCurDqLayer->pTransformSize8x8Flag[pCtx->pCurDqLayer->iMbXyIndex - pCtx->pCurDqLayer->iMbWidth]);
  iCtxInc = iIdxA + iIdxB;
  WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + iCtxInc, uiCode));
  bTransformSize8x8Flag = !!uiCode;

  return ERR_NONE;
}

int32_t ParseSubMBTypeCabac (PWelsDecoderContext pCtx, PWelsNeighAvail pNeighAvail, uint32_t& uiSubMbType) {
  uint32_t uiCode;
  PWelsCabacDecEngine pCabacDecEngine = pCtx->pCabacDecEngine;
  PWelsCabacCtx pBinCtx = pCtx->pCabacCtx + NEW_CTX_OFFSET_SUBMB_TYPE;
  WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx, uiCode));
  if (uiCode)
    uiSubMbType = 0;
  else {
    WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 1, uiCode));
    if (uiCode) {
      WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 2, uiCode));
      uiSubMbType = 3 - uiCode;
    } else {
      uiSubMbType = 1;
    }
  }
  return ERR_NONE;
}

int32_t ParseBSubMBTypeCabac (PWelsDecoderContext pCtx, PWelsNeighAvail pNeighAvail, uint32_t& uiSubMbType) {
  uint32_t uiCode;
  PWelsCabacDecEngine pCabacDecEngine = pCtx->pCabacDecEngine;
  PWelsCabacCtx pBinCtx = pCtx->pCabacCtx + NEW_CTX_OFFSET_B_SUBMB_TYPE;
  WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx, uiCode));
  if (!uiCode) {
    uiSubMbType = 0; /* B_Direct_8x8 */
    return ERR_NONE;
  }
  WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 1, uiCode));
  if (!uiCode) {
    WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 3, uiCode));
    uiSubMbType = 1 + uiCode; /* B_L0_8x8, B_L1_8x8 */
    return ERR_NONE;
  }
  uiSubMbType = 3;
  WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 2, uiCode));
  if (uiCode) {
    WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 3, uiCode));
    if (uiCode) {
      WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 3, uiCode));
      uiSubMbType = 11 + uiCode; /* B_L1_4x4, B_Bi_4x4 */
      return ERR_NONE;
    }
    uiSubMbType += 4;
  }
  WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 3, uiCode));
  uiSubMbType += 2 * uiCode;
  WELS_READ_VERIFY (DecodeBinCabac (pCabacDecEngine, pBinCtx + 3, uiCode));
  uiSubMbType += uiCode;

  return ERR_NONE;
}

int32_t ParseIntraPredModeLumaCabac (PWelsDecoderContext pCtx, int32_t& iBinVal) {
  uint32_t uiCode;
  iBinVal = 0;
  WELS_READ_VERIFY (DecodeBinCabac (pCtx->pCabacDecEngine, pCtx->pCabacCtx + NEW_CTX_OFFSET_IPR, uiCode));
  if (uiCode == 1)
    iBinVal = -1;
  else {
    WELS_READ_VERIFY (DecodeBinCabac (pCtx->pCabacDecEngine, pCtx->pCabacCtx + NEW_CTX_OFFSET_IPR + 1, uiCode));
    iBinVal |= uiCode;
    WELS_READ_VERIFY (DecodeBinCabac (pCtx->pCabacDecEngine, pCtx->pCabacCtx + NEW_CTX_OFFSET_IPR + 1, uiCode));
    iBinVal |= (uiCode << 1);
    WELS_READ_VERIFY (DecodeBinCabac (pCtx->pCabacDecEngine, pCtx->pCabacCtx + NEW_CTX_OFFSET_IPR + 1, uiCode));
    iBinVal |= (uiCode << 2);
  }
  return ERR_NONE;
}

int32_t ParseIntraPredModeChromaCabac (PWelsDecoderContext pCtx, uint8_t uiNeighAvail, int32_t& iBinVal) {
  uint32_t uiCode;
  int32_t iIdxA, iIdxB, iCtxInc;
  int8_t* pChromaPredMode = pCtx->pCurDqLayer->pChromaPredMode;
  uint32_t* pMbType = pCtx->pCurDqLayer->pDec->pMbType;
  int32_t iLeftAvail     = uiNeighAvail & 0x04;
  int32_t iTopAvail      = uiNeighAvail & 0x01;

  int32_t iMbXy = pCtx->pCurDqLayer->iMbXyIndex;
  int32_t iMbXyTop = iMbXy - pCtx->pCurDqLayer->iMbWidth;
  int32_t iMbXyLeft = iMbXy - 1;

  iBinVal = 0;

  iIdxB = iTopAvail  && (pChromaPredMode[iMbXyTop] > 0 && pChromaPredMode[iMbXyTop] <= 3)
          && pMbType[iMbXyTop]  != MB_TYPE_INTRA_PCM;
  iIdxA = iLeftAvail && (pChromaPredMode[iMbXyLeft] > 0 && pChromaPredMode[iMbXyLeft] <= 3)
          && pMbType[iMbXyLeft] != MB_TYPE_INTRA_PCM;
  iCtxInc = iIdxA + iIdxB;
  WELS_READ_VERIFY (DecodeBinCabac (pCtx->pCabacDecEngine, pCtx->pCabacCtx + NEW_CTX_OFFSET_CIPR + iCtxInc, uiCode));
  iBinVal = uiCode;
  if (iBinVal != 0) {
    uint32_t iSym;
    WELS_READ_VERIFY (DecodeBinCabac (pCtx->pCabacDecEngine, pCtx->pCabacCtx + NEW_CTX_OFFSET_CIPR + 3, iSym));
    if (iSym == 0) {
      iBinVal = (iSym + 1);
      return ERR_NONE;
    }
    iSym = 0;
    do {
      WELS_READ_VERIFY (DecodeBinCabac (pCtx->pCabacDecEngine, pCtx->pCabacCtx + NEW_CTX_OFFSET_CIPR + 3, uiCode));
      ++iSym;
    } while ((uiCode != 0) && (iSym < 1));

    if ((uiCode != 0) && (iSym == 1))
      ++ iSym;
    iBinVal = (iSym + 1);
    return ERR_NONE;
  }
  return ERR_NONE;
}

int32_t ParseInterPMotionInfoCabac (PWelsDecoderContext pCtx, PWelsNeighAvail pNeighAvail, uint8_t* pNonZeroCount,
                                    int16_t pMotionVector[LIST_A][30][MV_A], int16_t pMvdCache[LIST_A][30][MV_A], int8_t pRefIndex[LIST_A][30]) {
  PSlice pSlice                 = &pCtx->pCurDqLayer->sLayerInfo.sSliceInLayer;
  PSliceHeader pSliceHeader     = &pSlice->sSliceHeaderExt.sSliceHeader;
  PDqLayer pCurDqLayer = pCtx->pCurDqLayer;
  PPicture* ppRefPic = pCtx->sRefPic.pRefList[LIST_0];
  int32_t pRefCount[2];
  int32_t i, j;
  int32_t iMbXy = pCurDqLayer->iMbXyIndex;
  int16_t pMv[4] = {0};
  int16_t pMvd[4] = {0};
  int8_t iRef[2] = {0};
  int32_t iPartIdx;
  int16_t iMinVmv = pSliceHeader->pSps->pSLevelLimits->iMinVmv;
  int16_t iMaxVmv = pSliceHeader->pSps->pSLevelLimits->iMaxVmv;
  pRefCount[0] = pSliceHeader->uiRefCount[0];
  pRefCount[1] = pSliceHeader->uiRefCount[1];

  bool bIsPending = GetThreadCount (pCtx) > 1;

  switch (pCurDqLayer->pDec->pMbType[iMbXy]) {
  case MB_TYPE_16x16: {
    iPartIdx = 0;
    WELS_READ_VERIFY (ParseRefIdxCabac (pCtx, pNeighAvail, pNonZeroCount, pRefIndex, 0, LIST_0, iPartIdx, pRefCount[0], 0,
                                        iRef[0]));
    if ((iRef[0] < 0) || (iRef[0] >= pRefCount[0]) || (ppRefPic[iRef[0]] == NULL)) { //error ref_idx
      pCtx->bMbRefConcealed = true;
      if (pCtx->pParam->eEcActiveIdc != ERROR_CON_DISABLE) {
        iRef[0] = 0;
        pCtx->iErrorCode |= dsBitstreamError;
      } else {
        return GENERATE_ERROR_NO (ERR_LEVEL_MB_DATA, ERR_INFO_INVALID_REF_INDEX);
      }
    }
    pCtx->bMbRefConcealed = pCtx->bRPLRError || pCtx->bMbRefConcealed || ! (ppRefPic[iRef[0]]
                            && (ppRefPic[iRef[0]]->bIsComplete || bIsPending));
    PredMv (pMotionVector, pRefIndex, LIST_0, 0, 4, iRef[0], pMv);
    WELS_READ_VERIFY (ParseMvdInfoCabac (pCtx, pNeighAvail, pRefIndex, pMvdCache, iPartIdx, LIST_0, 0, pMvd[0]));
    WELS_READ_VERIFY (ParseMvdInfoCabac (pCtx, pNeighAvail, pRefIndex, pMvdCache, iPartIdx, LIST_0, 1, pMvd[1]));
    pMv[0] += pMvd[0];
    pMv[1] += pMvd[1];
    WELS_CHECK_SE_BOTH_WARNING (pMv[1], iMinVmv, iMaxVmv, "vertical mv");
    UpdateP16x16MotionInfo (pCurDqLayer, LIST_0, iRef[0], pMv);
    UpdateP16x16MvdCabac (pCurDqLayer, pMvd, LIST_0);
  }
  break;
  case MB_TYPE_16x8:
    for (i = 0; i < 2; i++) {
      iPartIdx = i << 3;
      WELS_READ_VERIFY (ParseRefIdxCabac (pCtx, pNeighAvail, pNonZeroCount, pRefIndex, 0, LIST_0, iPartIdx, pRefCount[0], 0,
                                          iRef[i]));
      if ((iRef[i] < 0) || (iRef[i] >= pRefCount[0]) || (ppRefPic[iRef[i]] == NULL)) { //error ref_idx
        pCtx->bMbRefConcealed = true;
        if (pCtx->pParam->eEcActiveIdc != ERROR_CON_DISABLE) {
          iRef[i] = 0;
          pCtx->iErrorCode |= dsBitstreamError;
        } else {
          return GENERATE_ERROR_NO (ERR_LEVEL_MB_DATA, ERR_INFO_INVALID_REF_INDEX);
        }
      }
      pCtx->bMbRefConcealed = pCtx->bRPLRError || pCtx->bMbRefConcealed || ! (ppRefPic[iRef[i]]
                              && (ppRefPic[iRef[i]]->bIsComplete || bIsPending));
      UpdateP16x8RefIdxCabac (pCurDqLayer, pRefIndex, iPartIdx, iRef[i], LIST_0);
    }
    for (i = 0; i < 2; i++) {
      iPartIdx = i << 3;
      PredInter16x8Mv (pMotionVector, pRefIndex, LIST_0, iPartIdx, iRef[i], pMv);
      WELS_READ_VERIFY (ParseMvdInfoCabac (pCtx, pNeighAvail, pRefIndex, pMvdCache, iPartIdx, LIST_0, 0, pMvd[0]));
      WELS_READ_VERIFY (ParseMvdInfoCabac (pCtx, pNeighAvail, pRefIndex, pMvdCache, iPartIdx, LIST_0, 1, pMvd[1]));
      pMv[0] += pMvd[0];
      pMv[1] += pMvd[1];
      WELS_CHECK_SE_BOTH_WARNING (pMv[1], iMinVmv, iMaxVmv, "vertical mv");
      UpdateP16x8MotionInfo (pCurDqLayer, pMotionVector, pRefIndex, LIST_0, iPartIdx, iRef[i], pMv);
      UpdateP16x8MvdCabac (pCurDqLayer, pMvdCache, iPartIdx, pMvd, LIST_0);
    }
    break;
  case MB_TYPE_8x16:
    for (i = 0; i < 2; i++) {
      iPartIdx = i << 2;
      WELS_READ_VERIFY (ParseRefIdxCabac (pCtx, pNeighAvail, pNonZeroCount, pRefIndex, 0, LIST_0, iPartIdx, pRefCount[0], 0,
                                          iRef[i]));
      if ((iRef[i] < 0) || (iRef[i] >= pRefCount[0]) || (ppRefPic[iRef[i]] == NULL)) { //error ref_idx
        pCtx->bMbRefConcealed = true;
        if (pCtx->pParam->eEcActiveIdc != ERROR_CON_DISABLE) {
          iRef[i] = 0;
          pCtx->iErrorCode |= dsBitstreamError;
        } else {
          return GENERATE_ERROR_NO (ERR_LEVEL_MB_DATA, ERR_INFO_INVALID_REF_INDEX);
        }
      }
      pCtx->bMbRefConcealed = pCtx->bRPLRError || pCtx->bMbRefConcealed || ! (ppRefPic[iRef[i]]
                              && (ppRefPic[iRef[i]]->bIsComplete || bIsPending));
      UpdateP8x16RefIdxCabac (pCurDqLayer, pRefIndex, iPartIdx, iRef[i], LIST_0);
    }
    for (i = 0; i < 2; i++) {
      iPartIdx = i << 2;
      PredInter8x16Mv (pMotionVector, pRefIndex, LIST_0, i << 2, iRef[i], pMv/*&mv[0], &mv[1]*/);

      WELS_READ_VERIFY (ParseMvdInfoCabac (pCtx, pNeighAvail, pRefIndex, pMvdCache, iPartIdx, LIST_0, 0, pMvd[0]));
      WELS_READ_VERIFY (ParseMvdInfoCabac (pCtx, pNeighAvail, pRefIndex, pMvdCache, iPartIdx, LIST_0, 1, pMvd[1]));
      pMv[0] += pMvd[0];
      pMv[1] += pMvd[1];
      WELS_CHECK_SE_BOTH_WARNING (pMv[1], iMinVmv, iMaxVmv, "vertical mv");
      UpdateP8x16MotionInfo (pCurDqLayer, pMotionVector, pRefIndex, LIST_0, iPartIdx, iRef[i], pMv);
      UpdateP8x16MvdCabac (pCurDqLayer, pMvdCache, iPartIdx, pMvd, LIST_0);
    }
    break;
  case MB_TYPE_8x8:
  case MB_TYPE_8x8_REF0: {
    int8_t pRefIdx[4] = {0}, pSubPartCount[4], pPartW[4];
    uint32_t uiSubMbType;
    //sub_mb_type, partition
    for (i = 0; i < 4; i++) {
      WELS_READ_VERIFY (ParseSubMBTypeCabac (pCtx, pNeighAvail, uiSubMbType));
      if (uiSubMbType >= 4) { //invalid sub_mb_type
        return GENERATE_ERROR_NO (ERR_LEVEL_MB_DATA, ERR_INFO_INVALID_SUB_MB_TYPE);
      }
      pCurDqLayer->pSubMbType[iMbXy][i] = g_ksInterPSubMbTypeInfo[uiSubMbType].iType;
      pSubPartCount[i] = g_ksInterPSubMbTypeInfo[uiSubMbType].iPartCount;
      pPartW[i] = g_ksInterPSubMbTypeInfo[uiSubMbType].iPartWidth;

      // Need modification when B picture add in, reference to 7.3.5
      pCurDqLayer->pNoSubMbPartSizeLessThan8x8Flag[iMbXy] &= (uiSubMbType == 0);
    }

    for (i = 0; i < 4; i++) {
      int16_t iIdx8 = i << 2;
      WELS_READ_VERIFY (ParseRefIdxCabac (pCtx, pNeighAvail, pNonZeroCount, pRefIndex, 0, LIST_0, iIdx8, pRefCount[0], 1,
                                          pRefIdx[i]));
      if ((pRefIdx[i] < 0) || (pRefIdx[i] >= pRefCount[0]) || (ppRefPic[pRefIdx[i]] == NULL)) { //error ref_idx
        pCtx->bMbRefConcealed = true;
        if (pCtx->pParam->eEcActiveIdc != ERROR_CON_DISABLE) {
          pRefIdx[i] = 0;
          pCtx->iErrorCode |= dsBitstreamError;
        } else {
          return GENERATE_ERROR_NO (ERR_LEVEL_MB_DATA, ERR_INFO_INVALID_REF_INDEX);
        }
      }
      pCtx->bMbRefConcealed = pCtx->bRPLRError || pCtx->bMbRefConcealed || ! (ppRefPic[pRefIdx[i]]
                              && (ppRefPic[pRefIdx[i]]->bIsComplete || bIsPending));
      UpdateP8x8RefIdxCabac (pCurDqLayer, pRefIndex, iIdx8, pRefIdx[i], LIST_0);
    }
    //mv
    for (i = 0; i < 4; i++) {
      int8_t iPartCount = pSubPartCount[i];
      uiSubMbType = pCurDqLayer->pSubMbType[iMbXy][i];
      int16_t iPartIdx, iBlockW = pPartW[i];
      uint8_t iScan4Idx, iCacheIdx;
      iCacheIdx = g_kuiCache30ScanIdx[i << 2];
      pRefIndex[0][iCacheIdx ] = pRefIndex[0][iCacheIdx + 1]
                                 = pRefIndex[0][iCacheIdx + 6] = pRefIndex[0][iCacheIdx + 7] = pRefIdx[i];

      for (j = 0; j < iPartCount; j++) {
        iPartIdx = (i << 2) + j * iBlockW;
        iScan4Idx = g_kuiScan4[iPartIdx];
        iCacheIdx = g_kuiCache30ScanIdx[iPartIdx];
        PredMv (pMotionVector, pRefIndex, LIST_0, iPartIdx, iBlockW, pRefIdx[i], pMv);
        WELS_READ_VERIFY (ParseMvdInfoCabac (pCtx, pNeighAvail, pRefIndex, pMvdCache, iPartIdx, LIST_0, 0, pMvd[0]));
        WELS_READ_VERIFY (ParseMvdInfoCabac (pCtx, pNeighAvail, pRefIndex, pMvdCache, iPartIdx, LIST_0, 1, pMvd[1]));
        pMv[0] += pMvd[0];
        pMv[1] += pMvd[1];
        WELS_CHECK_SE_BOTH_WARNING (pMv[1], iMinVmv, iMaxVmv, "vertical mv");
        if (SUB_MB_TYPE_8x8 == uiSubMbType) {
          ST32 ((pMv + 2), LD32 (pMv));
          ST32 ((pMvd + 2), LD32 (pMvd));
          ST64 (pCurDqLayer->pDec->pMv[0][iMbXy][iScan4Idx], LD64 (pMv));
          ST64 (pCurDqLayer->pDec->pMv[0][iMbXy][iScan4Idx + 4], LD64 (pMv));
          ST64 (pCurDqLayer->pMvd[0][iMbXy][iScan4Idx], LD64 (pMvd));
          ST64 (pCurDqLayer->pMvd[0][iMbXy][iScan4Idx + 4], LD64 (pMvd));
          ST64 (pMotionVector[0][iCacheIdx  ], LD64 (pMv));
          ST64 (pMotionVector[0][iCacheIdx + 6], LD64 (pMv));
          ST64 (pMvdCache[0][iCacheIdx  ], LD64 (pMvd));
          ST64 (pMvdCache[0][iCacheIdx + 6], LD64 (pMvd));
        } else if (SUB_MB_TYPE_8x4 == uiSubMbType) {
          ST32 ((pMv + 2), LD32 (pMv));
          ST32 ((pMvd + 2), LD32 (pMvd));
          ST64 (pCurDqLayer->pDec->pMv[0][iMbXy][iScan4Idx  ], LD64 (pMv));
          ST64 (pCurDqLayer->pMvd[0][iMbXy][iScan4Idx  ], LD64 (pMvd));
          ST64 (pMotionVector[0][iCacheIdx  ], LD64 (pMv));
          ST64 (pMvdCache[0][iCacheIdx  ], LD64 (pMvd));
        } else if (SUB_MB_TYPE_4x8 == uiSubMbType) {
          ST32 (pCurDqLayer->pDec->pMv[0][iMbXy][iScan4Idx  ], LD32 (pMv));
          ST32 (pCurDqLayer->pDec->pMv[0][iMbXy][iScan4Idx + 4], LD32 (pMv));
          ST32 (pCurDqLayer->pMvd[0][iMbXy][iScan4Idx  ], LD32 (pMvd));
          ST32 (pCurDqLayer->pMvd[0][iMbXy][iScan4Idx + 4], LD32 (pMvd));
          ST32 (pMotionVector[0][iCacheIdx  ], LD32 (pMv));
          ST32 (pMotionVector[0][iCacheIdx + 6], LD32 (pMv));
          ST32 (pMvdCache[0][iCacheIdx  ], LD32 (pMvd));
          ST32 (pMvdCache[0][iCacheIdx + 6], LD32 (pMvd));
        } else {  //SUB_MB_TYPE_4x4
          ST32 (pCurDqLayer->pDec->pMv[0][iMbXy][iScan4Idx  ], LD32 (pMv));
          ST32 (pCurDqLayer->pMvd[0][iMbXy][iScan4Idx  ], LD32 (pMvd));
          ST32 (pMotionVector[0][iCacheIdx  ], LD32 (pMv));
          ST32 (pMvdCache[0][iCacheIdx  ], LD32 (pMvd));
        }
      }
    }
  }
  break;
  default:
    break;
  }
  return ERR_NONE;
}

int32_t ParseInterBMotionInfoCabac (PWelsDecoderContext pCtx, PWelsNeighAvail pNeighAvail, uint8_t* pNonZeroCount,
                                    int16_t pMotionVector[LIST_A][30][MV_A], int16_t pMvdCache[LIST_A][30][MV_A], int8_t pRefIndex[LIST_A][30],
                                    int8_t pDirect[30]) {
  PSlice pSlice = &pCtx->pCurDqLayer->sLayerInfo.sSliceInLayer;
  PSliceHeader pSliceHeader = &pSlice->sSliceHeaderExt.sSliceHeader;
  PDqLayer pCurDqLayer = pCtx->pCurDqLayer;
  int32_t pRefCount[LIST_A];
  int32_t iMbXy = pCurDqLayer->iMbXyIndex;
  int16_t pMv[4] = { 0 };
  int16_t pMvd[4] = { 0 };
  int8_t iRef[LIST_A] = { 0 };
  int32_t iPartIdx;
  int16_t iMinVmv = pSliceHeader->pSps->pSLevelLimits->iMinVmv;
  int16_t iMaxVmv = pSliceHeader->pSps->pSLevelLimits->iMaxVmv;
  pRefCount[0] = pSliceHeader->uiRefCount[0];
  pRefCount[1] = pSliceHeader->uiRefCount[1];

  MbType mbType = pCurDqLayer->pDec->pMbType[iMbXy];

  bool bIsPending = GetThreadCount (pCtx) > 1;

  if (IS_DIRECT (mbType)) {

    int16_t pMvDirect[LIST_A][2] = { { 0, 0 }, { 0, 0 } };
    SubMbType subMbType;
    if (pSliceHeader->iDirectSpatialMvPredFlag) {
      //predict direct spatial mv
      int32_t ret = PredMvBDirectSpatial (pCtx, pMvDirect, iRef, subMbType);
      if (ret != ERR_NONE) {
        return ret;
      }
    } else {
      //temporal direct 16x16 mode
      int32_t ret = PredBDirectTemporal (pCtx, pMvDirect, iRef, subMbType);
      if (ret != ERR_NONE) {
        return ret;
      }
    }
  } else if (IS_INTER_16x16 (mbType)) {
    iPartIdx = 0;
    for (int32_t listIdx = LIST_0; listIdx < LIST_A; ++listIdx) {
      iRef[listIdx] = REF_NOT_IN_LIST;
      if (IS_DIR (mbType, 0, listIdx)) {
        WELS_READ_VERIFY (ParseRefIdxCabac (pCtx, pNeighAvail, pNonZeroCount, pRefIndex, pDirect, listIdx, iPartIdx,
                                            pRefCount[listIdx], 0,
                                            iRef[listIdx]));
        if ((iRef[listIdx] < 0) || (iRef[listIdx] >= pRefCount[listIdx])
            || (pCtx->sRefPic.pRefList[listIdx][iRef[listIdx]] == NULL)) { //error ref_idx
          pCtx->bMbRefConcealed = true;
          if (pCtx->pParam->eEcActiveIdc != ERROR_CON_DISABLE) {
            iRef[listIdx] = 0;
            pCtx->iErrorCode |= dsBitstreamError;
            RETURN_ERR_IF_NULL(pCtx->sRefPic.pRefList[listIdx][iRef[listIdx]]);
          } else {
            return GENERATE_ERROR_NO (ERR_LEVEL_MB_DATA, ERR_INFO_INVALID_REF_INDEX);
          }
        }
        pCtx->bMbRefConcealed = pCtx->bRPLRError || pCtx->bMbRefConcealed || ! (pCtx->sRefPic.pRefList[listIdx][iRef[listIdx]]
                                && (pCtx->sRefPic.pRefList[listIdx][iRef[listIdx]]->bIsComplete || bIsPending));
      }
    }
    for (int32_t listIdx = LIST_0; listIdx < LIST_A; ++listIdx) {
      if (IS_DIR (mbType, 0, listIdx)) {
        PredMv (pMotionVector, pRefIndex, listIdx, 0, 4, iRef[listIdx], pMv);
        WELS_READ_VERIFY (ParseMvdInfoCabac (pCtx, pNeighAvail, pRefIndex, pMvdCache, iPartIdx, listIdx, 0, pMvd[0]));
        WELS_READ_VERIFY (ParseMvdInfoCabac (pCtx, pNeighAvail, pRefIndex, pMvdCache, iPartIdx, listIdx, 1, pMvd[1]));
        pMv[0] += pMvd[0];
        pMv[1] += pMvd[1];
        WELS_CHECK_SE_BOTH_WARNING (pMv[1], iMinVmv, iMaxVmv, "vertical mv");
      } else {
        * (uint32_t*)pMv = * (uint32_t*)pMvd = 0;
      }
      UpdateP16x16MotionInfo (pCurDqLayer, listIdx, iRef[listIdx], pMv);
      UpdateP16x16MvdCabac (pCurDqLayer, pMvd, listIdx);
    }
  } else if (IS_INTER_16x8 (mbType)) {
    int8_t ref_idx_list[LIST_A][2] = { {REF_NOT_IN_LIST, REF_NOT_IN_LIST}, { REF_NOT_IN_LIST, REF_NOT_IN_LIST } };
    for (int32_t listIdx = LIST_0; listIdx < LIST_A; ++listIdx) {
      for (int32_t i = 0; i < 2; ++i) {
        iPartIdx = i << 3;
        int8_t ref_idx = REF_NOT_IN_LIST;
        if (IS_DIR (mbType, i, listIdx)) {
          WELS_READ_VERIFY (ParseRefIdxCabac (pCtx, pNeighAvail, pNonZeroCount, pRefIndex, pDirect, listIdx, iPartIdx,
                                              pRefCount[listIdx], 0, ref_idx));
          if ((ref_idx < 0) || (ref_idx >= pRefCount[listIdx])
              || (pCtx->sRefPic.pRefList[listIdx][ref_idx] == NULL)) { //error ref_idx
            pCtx->bMbRefConcealed = true;
            if (pCtx->pParam->eEcActiveIdc != ERROR_CON_DISABLE) {
              ref_idx = 0;
              pCtx->iErrorCode |= dsBitstreamError;
              RETURN_ERR_IF_NULL(pCtx->sRefPic.pRefList[listIdx][ref_idx]);
            } else {
              return GENERATE_ERROR_NO (ERR_LEVEL_MB_DATA, ERR_INFO_INVALID_REF_INDEX);
            }
          }
          pCtx->bMbRefConcealed = pCtx->bRPLRError || pCtx->bMbRefConcealed || ! (pCtx->sRefPic.pRefList[listIdx][ref_idx]
                                  && (pCtx->sRefPic.pRefList[listIdx][ref_idx]->bIsComplete || bIsPending));
        }
        UpdateP16x8RefIdxCabac (pCurDqLayer, pRefIndex, iPartIdx, ref_idx, listIdx);
        ref_idx_list[listIdx][i] = ref_idx;
      }
    }
    for (int32_t listIdx = LIST_0; listIdx < LIST_A; ++listIdx) {
      for (int32_t i = 0; i < 2; ++i) {
        iPartIdx = i << 3;
        int8_t ref_idx = ref_idx_list[listIdx][i];
        if (IS_DIR (mbType, i, listIdx)) {
          PredInter16x8Mv (pMotionVector, pRefIndex, listIdx, iPartIdx, ref_idx, pMv);
          WELS_READ_VERIFY (ParseMvdInfoCabac (pCtx, pNeighAvail, pRefIndex, pMvdCache, iPartIdx, listIdx, 0, pMvd[0]));
          WELS_READ_VERIFY (ParseMvdInfoCabac (pCtx, pNeighAvail, pRefIndex, pMvdCache, iPartIdx, listIdx, 1, pMvd[1]));
          pMv[0] += pMvd[0];
          pMv[1] += pMvd[1];
          WELS_CHECK_SE_BOTH_WARNING (pMv[1], iMinVmv, iMaxVmv, "vertical mv");
        } else {
          * (uint32_t*)pMv = * (uint32_t*)pMvd = 0;
        }
        UpdateP16x8MotionInfo (pCurDqLayer, pMotionVector, pRefIndex, listIdx, iPartIdx, ref_idx, pMv);
        UpdateP16x8MvdCabac (pCurDqLayer, pMvdCache, iPartIdx, pMvd, listIdx);
      }
    }
  } else if (IS_INTER_8x16 (mbType)) {
    int8_t ref_idx_list[LIST_A][2] = { { REF_NOT_IN_LIST, REF_NOT_IN_LIST }, { REF_NOT_IN_LIST, REF_NOT_IN_LIST } };
    for (int32_t listIdx = LIST_0; listIdx < LIST_A; ++listIdx) {
      for (int32_t i = 0; i < 2; ++i) {
        iPartIdx = i << 2;
        int8_t ref_idx = REF_NOT_IN_LIST;
        if (IS_DIR (mbType, i, listIdx)) {
          WELS_READ_VERIFY (ParseRefIdxCabac (pCtx, pNeighAvail, pNonZeroCount, pRefIndex, pDirect, listIdx, iPartIdx,
                                              pRefCount[listIdx], 0, ref_idx));
          if ((ref_idx < 0) || (ref_idx >= pRefCount[listIdx])
              || (pCtx->sRefPic.pRefList[listIdx][ref_idx] == NULL)) { //error ref_idx
            pCtx->bMbRefConcealed = true;
            if (pCtx->pParam->eEcActiveIdc != ERROR_CON_DISABLE) {
              ref_idx = 0;
              pCtx->iErrorCode |= dsBitstreamError;
              RETURN_ERR_IF_NULL(pCtx->sRefPic.pRefList[listIdx][ref_idx]);
            } else {
              return GENERATE_ERROR_NO (ERR_LEVEL_MB_DATA, ERR_INFO_INVALID_REF_INDEX);
            }
          }
          pCtx->bMbRefConcealed = pCtx->bRPLRError || pCtx->bMbRefConcealed || ! (pCtx->sRefPic.pRefList[listIdx][ref_idx]
                                  && (pCtx->sRefPic.pRefList[listIdx][ref_idx]->bIsComplete || bIsPending));
        }
        UpdateP8x16RefIdxCabac (pCurDqLayer, pRefIndex, iPartIdx, ref_idx, listIdx);
        ref_idx_list[listIdx][i] = ref_idx;
      }
    }
    for (int32_t listIdx = LIST_0; listIdx < LIST_A; ++listIdx) {
      for (int32_t i = 0; i < 2; ++i) {
        iPartIdx = i << 2;
        int8_t ref_idx = ref_idx_list[listIdx][i];
        if (IS_DIR (mbType, i, listIdx)) {
          PredInter8x16Mv (pMotionVector, pRefIndex, listIdx, iPartIdx, ref_idx, pMv);
          WELS_READ_VERIFY (ParseMvdInfoCabac (pCtx, pNeighAvail, pRefIndex, pMvdCache, iPartIdx, listIdx, 0, pMvd[0]));
          WELS_READ_VERIFY (ParseMvdInfoCabac (pCtx, pNeighAvail, pRefIndex, pMvdCache, iPartIdx, listIdx, 1, pMvd[1]));
          pMv[0] += pMvd[0];
          pMv[1] += pMvd[1];
          WELS_CHECK_SE_BOTH_WARNING (pMv[1], iMinVmv, iMaxVmv, "vertical mv");
        } else {
          * (uint32_t*)pMv = * (uint32_t*)pMvd = 0;
        }
        UpdateP8x16MotionInfo (pCurDqLayer, pMotionVector, pRefIndex, listIdx, iPartIdx, ref_idx, pMv);
        UpdateP8x16MvdCabac (pCurDqLayer, pMvdCache, iPartIdx, pMvd, listIdx);
      }
    }
  } else if (IS_Inter_8x8 (mbType)) {
    int8_t pSubPartCount[4], pPartW[4];
    uint32_t uiSubMbType;
    //sub_mb_type, partition
    int16_t pMvDirect[LIST_A][2] = { {0, 0}, {0, 0} };
    if (pCtx->sRefPic.pRefList[LIST_1][0] == NULL) {
      SLogContext* pLogCtx = & (pCtx->sLogCtx);
      WelsLog (pLogCtx, WELS_LOG_ERROR, "Colocated Ref Picture for B-Slice is lost, B-Slice decoding cannot be continued!");
      return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_DATA, ERR_INFO_REFERENCE_PIC_LOST);
    }
    bool bIsLongRef = pCtx->sRefPic.pRefList[LIST_1][0]->bIsLongRef;
    const int32_t ref0Count = WELS_MIN (pSliceHeader->uiRefCount[LIST_0], pCtx->sRefPic.uiRefCount[LIST_0]);
    bool has_direct_called = false;
    SubMbType directSubMbType = 0;
    for (int32_t i = 0; i < 4; i++) {
      WELS_READ_VERIFY (ParseBSubMBTypeCabac (pCtx, pNeighAvail, uiSubMbType));
      if (uiSubMbType >= 13) { //invalid sub_mb_type
        return GENERATE_ERROR_NO (ERR_LEVEL_MB_DATA, ERR_INFO_INVALID_SUB_MB_TYPE);
      }
//      pCurDqLayer->pSubMbType[iMbXy][i] = g_ksInterBSubMbTypeInfo[uiSubMbType].iType;
      pSubPartCount[i] = g_ksInterBSubMbTypeInfo[uiSubMbType].iPartCount;
      pPartW[i] = g_ksInterBSubMbTypeInfo[uiSubMbType].iPartWidth;

      // Need modification when B picture add in, reference to 7.3.5
      if (pSubPartCount[i] > 1)
        pCurDqLayer->pNoSubMbPartSizeLessThan8x8Flag[iMbXy] = false;

      if (IS_DIRECT (g_ksInterBSubMbTypeInfo[uiSubMbType].iType)) {
        if (!has_direct_called) {
          if (pSliceHeader->iDirectSpatialMvPredFlag) {
            int32_t ret = PredMvBDirectSpatial (pCtx, pMvDirect, iRef, directSubMbType);
            if (ret != ERR_NONE) {
              return ret;
            }

          } else {
            //temporal direct mode
            int32_t ret = PredBDirectTemporal (pCtx, pMvDirect, iRef, directSubMbType);
            if (ret != ERR_NONE) {
              return ret;
            }
          }
          has_direct_called = true;
        }
        pCurDqLayer->pSubMbType[iMbXy][i] = directSubMbType;
        if (IS_SUB_4x4 (pCurDqLayer->pSubMbType[iMbXy][i])) {
          pSubPartCount[i] = 4;
          pPartW[i] = 1;
        }
      } else {
        pCurDqLayer->pSubMbType[iMbXy][i] = g_ksInterBSubMbTypeInfo[uiSubMbType].iType;
      }
    }
    for (int32_t i = 0; i < 4; i++) { //Direct 8x8 Ref and mv
      int16_t iIdx8 = i << 2;
      if (IS_DIRECT (pCurDqLayer->pSubMbType[iMbXy][i])) {
        if (pSliceHeader->iDirectSpatialMvPredFlag) {
          FillSpatialDirect8x8Mv (pCurDqLayer, iIdx8, pSubPartCount[i], pPartW[i], directSubMbType, bIsLongRef, pMvDirect, iRef,
                                  pMotionVector, pMvdCache);
        } else {
          int16_t (*mvColoc)[2] = pCurDqLayer->iColocMv[LIST_0];
          iRef[LIST_1] = 0;
          iRef[LIST_0] = 0;
          const uint8_t uiColoc4Idx = g_kuiScan4[iIdx8];
          if (!pCurDqLayer->iColocIntra[uiColoc4Idx]) {
            iRef[LIST_0] = 0;
            int8_t colocRefIndexL0 = pCurDqLayer->iColocRefIndex[LIST_0][uiColoc4Idx];
            if (colocRefIndexL0 >= 0) {
              iRef[LIST_0] = MapColToList0 (pCtx, colocRefIndexL0, ref0Count);
            } else {
              mvColoc = pCurDqLayer->iColocMv[LIST_1];
            }
          }
          Update8x8RefIdx (pCurDqLayer, iIdx8, LIST_0, iRef[LIST_0]);
          Update8x8RefIdx (pCurDqLayer, iIdx8, LIST_1, iRef[LIST_1]);
          UpdateP8x8RefCacheIdxCabac (pRefIndex, iIdx8, LIST_0, iRef[LIST_0]);
          UpdateP8x8RefCacheIdxCabac (pRefIndex, iIdx8, LIST_1, iRef[LIST_1]);
          FillTemporalDirect8x8Mv (pCurDqLayer, iIdx8, pSubPartCount[i], pPartW[i], directSubMbType, iRef, mvColoc, pMotionVector,
                                   pMvdCache);
        }
      }
    }
    //ref no-direct
    int8_t ref_idx_list[LIST_A][4] = { {REF_NOT_IN_LIST, REF_NOT_IN_LIST}, { REF_NOT_IN_LIST, REF_NOT_IN_LIST } };
    for (int32_t listIdx = LIST_0; listIdx < LIST_A; ++listIdx) {
      for (int32_t i = 0; i < 4; i++) {
        int16_t iIdx8 = i << 2;
        int32_t subMbType = pCurDqLayer->pSubMbType[iMbXy][i];
        int8_t iref = REF_NOT_IN_LIST;
        if (IS_DIRECT (subMbType)) {
          if (pSliceHeader->iDirectSpatialMvPredFlag) {
            Update8x8RefIdx (pCurDqLayer, iIdx8, listIdx, iRef[listIdx]);
            ref_idx_list[listIdx][i] = iRef[listIdx];
          }
          UpdateP8x8DirectCabac (pCurDqLayer, iIdx8);
        } else {
          if (IS_DIR (subMbType, 0, listIdx)) {
            WELS_READ_VERIFY (ParseRefIdxCabac (pCtx, pNeighAvail, pNonZeroCount, pRefIndex, pDirect, listIdx, iIdx8,
                                                pRefCount[listIdx], 1,
                                                iref));
            if ((iref < 0) || (iref >= pRefCount[listIdx]) || (pCtx->sRefPic.pRefList[listIdx][iref] == NULL)) { //error ref_idx
              pCtx->bMbRefConcealed = true;
              if (pCtx->pParam->eEcActiveIdc != ERROR_CON_DISABLE) {
                iref = 0;
                pCtx->iErrorCode |= dsBitstreamError;
                RETURN_ERR_IF_NULL(pCtx->sRefPic.pRefList[listIdx][iref]);
              } else {
                return GENERATE_ERROR_NO (ERR_LEVEL_MB_DATA, ERR_INFO_INVALID_REF_INDEX);
              }
            }
            pCtx->bMbRefConcealed = pCtx->bRPLRError || pCtx->bMbRefConcealed || ! (pCtx->sRefPic.pRefList[listIdx][iref]
                                    && (pCtx->sRefPic.pRefList[listIdx][iref]->bIsComplete || bIsPending));
          }
          Update8x8RefIdx (pCurDqLayer, iIdx8, listIdx, iref);
          ref_idx_list[listIdx][i] = iref;
        }
      }
    }
    //mv
    for (int32_t listIdx = LIST_0; listIdx < LIST_A; ++listIdx) {
      for (int32_t i = 0; i < 4; i++) {
        int16_t iIdx8 = i << 2;

        uint32_t subMbType = pCurDqLayer->pSubMbType[iMbXy][i];
        if (IS_DIRECT (subMbType) && !pSliceHeader->iDirectSpatialMvPredFlag)
          continue;

        int8_t iref = ref_idx_list[listIdx][i];
        UpdateP8x8RefCacheIdxCabac (pRefIndex, iIdx8, listIdx, iref);

        if (IS_DIRECT (subMbType))
          continue;

        bool is_dir = IS_DIR (subMbType, 0, listIdx) > 0;
        int8_t iPartCount = pSubPartCount[i];
        int16_t iBlockW = pPartW[i];
        uint8_t iScan4Idx, iCacheIdx;
        for (int32_t j = 0; j < iPartCount; j++) {
          iPartIdx = (i << 2) + j * iBlockW;
          iScan4Idx = g_kuiScan4[iPartIdx];
          iCacheIdx = g_kuiCache30ScanIdx[iPartIdx];
          if (is_dir) {
            PredMv (pMotionVector, pRefIndex, listIdx, iPartIdx, iBlockW, iref, pMv);
            WELS_READ_VERIFY (ParseMvdInfoCabac (pCtx, pNeighAvail, pRefIndex, pMvdCache, iPartIdx, listIdx, 0, pMvd[0]));
            WELS_READ_VERIFY (ParseMvdInfoCabac (pCtx, pNeighAvail, pRefIndex, pMvdCache, iPartIdx, listIdx, 1, pMvd[1]));
            pMv[0] += pMvd[0];
            pMv[1] += pMvd[1];
            WELS_CHECK_SE_BOTH_WARNING (pMv[1], iMinVmv, iMaxVmv, "vertical mv");
          } else {
            * (uint32_t*)pMv = * (uint32_t*)pMvd = 0;
          }
          if (IS_SUB_8x8 (subMbType)) { //MB_TYPE_8x8
            ST32 ((pMv + 2), LD32 (pMv));
            ST32 ((pMvd + 2), LD32 (pMvd));
            ST64 (pCurDqLayer->pDec->pMv[listIdx][iMbXy][iScan4Idx], LD64 (pMv));
            ST64 (pCurDqLayer->pDec->pMv[listIdx][iMbXy][iScan4Idx + 4], LD64 (pMv));
            ST64 (pCurDqLayer->pMvd[listIdx][iMbXy][iScan4Idx], LD64 (pMvd));
            ST64 (pCurDqLayer->pMvd[listIdx][iMbXy][iScan4Idx + 4], LD64 (pMvd));
            ST64 (pMotionVector[listIdx][iCacheIdx], LD64 (pMv));
            ST64 (pMotionVector[listIdx][iCacheIdx + 6], LD64 (pMv));
            ST64 (pMvdCache[listIdx][iCacheIdx], LD64 (pMvd));
            ST64 (pMvdCache[listIdx][iCacheIdx + 6], LD64 (pMvd));
          } else if (IS_SUB_4x4 (subMbType)) { //MB_TYPE_4x4
            ST32 (pCurDqLayer->pDec->pMv[listIdx][iMbXy][iScan4Idx], LD32 (pMv));
            ST32 (pCurDqLayer->pMvd[listIdx][iMbXy][iScan4Idx], LD32 (pMvd));
            ST32 (pMotionVector[listIdx][iCacheIdx], LD32 (pMv));
            ST32 (pMvdCache[listIdx][iCacheIdx], LD32 (pMvd));
          } else if (IS_SUB_4x8 (subMbType)) { //MB_TYPE_4x8 5, 7, 9
            ST32 (pCurDqLayer->pDec->pMv[listIdx][iMbXy][iScan4Idx], LD32 (pMv));
            ST32 (pCurDqLayer->pDec->pMv[listIdx][iMbXy][iScan4Idx + 4], LD32 (pMv));
            ST32 (pCurDqLayer->pMvd[listIdx][iMbXy][iScan4Idx], LD32 (pMvd));
            ST32 (pCurDqLayer->pMvd[listIdx][iMbXy][iScan4Idx + 4], LD32 (pMvd));
            ST32 (pMotionVector[listIdx][iCacheIdx], LD32 (pMv));
            ST32 (pMotionVector[listIdx][iCacheIdx + 6], LD32 (pMv));
            ST32 (pMvdCache[listIdx][iCacheIdx], LD32 (pMvd));
            ST32 (pMvdCache[listIdx][iCacheIdx + 6], LD32 (pMvd));
          } else { //MB_TYPE_8x4 4, 6, 8
            ST32 ((pMv + 2), LD32 (pMv));
            ST32 ((pMvd + 2), LD32 (pMvd));
            ST64 (pCurDqLayer->pDec->pMv[listIdx][iMbXy][iScan4Idx], LD64 (pMv));
            ST64 (pCurDqLayer->pMvd[listIdx][iMbXy][iScan4Idx], LD64 (pMvd));
            ST64 (pMotionVector[listIdx][iCacheIdx], LD64 (pMv));
            ST64 (pMvdCache[listIdx][iCacheIdx], LD64 (pMvd));
          }
        }
      }
    }
  }
  return ERR_NONE;
}

int32_t ParseRefIdxCabac (PWelsDecoderContext pCtx, PWelsNeighAvail pNeighAvail, uint8_t* nzc,
                          int8_t ref_idx[LIST_A][30], int8_t direct[30],
                          int32_t iListIdx, int32_t iZOrderIdx, int32_t iActiveRefNum, int32_t b8mode, int8_t& iRefIdxVal) {
  if (iActiveRefNum == 1) {
    iRefIdxVal = 0;
    return ERR_NONE;
  }
  uint32_t uiCode;
  int32_t iIdxA = 0, iIdxB = 0;
  int32_t iCtxInc = 0;
  int8_t* pRefIdxInMB = pCtx->pCurDqLayer->pDec->pRefIndex[iListIdx][pCtx->pCurDqLayer->iMbXyIndex];
  int8_t* pDirect = pCtx->pCurDqLayer->pDirect[pCtx->pCurDqLayer->iMbXyIndex];
  if (iZOrderIdx == 0) {
    iIdxB = (pNeighAvail->iTopAvail && pNeighAvail->iTopType != MB_TYPE_INTRA_PCM
             && ref_idx[iListIdx][g_kuiCache30ScanIdx[iZOrderIdx] - 6] > 0);
    iIdxA = (pNeighAvail->iLeftAvail && pNeighAvail->iLeftType != MB_TYPE_INTRA_PCM
             && ref_idx[iListIdx][g_kuiCache30ScanIdx[iZOrderIdx] - 1] > 0);
    if (pCtx->eSliceType == B_SLICE) {
      if (iIdxB > 0 && direct[g_kuiCache30ScanIdx[iZOrderIdx] - 6] == 0) {
        iCtxInc += 2;
      }
      if (iIdxA > 0 && direct[g_kuiCache30ScanIdx[iZOrderIdx] - 1] == 0) {
        iCtxInc++;
      }
    }
  } else if (iZOrderIdx == 4) {
    iIdxB = (pNeighAvail->iTopAvail && pNeighAvail->iTopType != MB_TYPE_INTRA_PCM
             && ref_idx[iListIdx][g_kuiCache30ScanIdx[iZOrderIdx] - 6] > 0);
    iIdxA = pRefIdxInMB[g_kuiScan4[iZOrderIdx] - 1] > 0;
    if (pCtx->eSliceType == B_SLICE) {
      if (iIdxB > 0 && direct[g_kuiCache30ScanIdx[iZOrderIdx] - 6] == 0) {
        iCtxInc += 2;
      }
      if (iIdxA > 0 && pDirect[g_kuiScan4[iZOrderIdx] - 1] == 0) {
        iCtxInc ++;
      }
    }
  } else if (iZOrderIdx == 8) {

    iIdxB = pRefIdxInMB[g_kuiScan4[iZOrderIdx] - 4] > 0;
    iIdxA = (pNeighAvail->iLeftAvail && pNeighAvail->iLeftType != MB_TYPE_INTRA_PCM
             && ref_idx[iListIdx][g_kuiCache30ScanIdx[iZOrderIdx] - 1] > 0);
    if (pCtx->eSliceType == B_SLICE) {
      if (iIdxB > 0 && pDirect[g_kuiScan4[iZOrderIdx] - 4] == 0) {
        iCtxInc += 2;
      }
      if (iIdxA > 0 && direct[g_kuiCache30ScanIdx[iZOrderIdx] - 1] == 0) {
        iCtxInc++;
      }
    }
  } else {
    iIdxB = pRefIdxInMB[g_kuiScan4[iZOrderIdx] - 4] > 0;
    iIdxA = pRefIdxInMB[g_kuiScan4[iZOrderIdx] - 1] > 0;
    if (pCtx->eSliceType == B_SLICE) {
      if (iIdxB > 0 && pDirect[g_kuiScan4[iZOrderIdx] - 4] == 0) {
        iCtxInc += 2;
      }
      if (iIdxA > 0 && pDirect[g_kuiScan4[iZOrderIdx] - 1] == 0) {
        iCtxInc++;
      }
    }
  }
  if (pCtx->eSliceType != B_SLICE) {
    iCtxInc = iIdxA + (iIdxB << 1);
  }

  WELS_READ_VERIFY (DecodeBinCabac (pCtx->pCabacDecEngine, pCtx->pCabacCtx + NEW_CTX_OFFSET_REF_NO + iCtxInc, uiCode));
  if (uiCode) {
    WELS_READ_VERIFY (DecodeUnaryBinCabac (pCtx->pCabacDecEngine, pCtx->pCabacCtx + NEW_CTX_OFFSET_REF_NO + 4, 1, uiCode));
    ++uiCode;
  }
  iRefIdxVal = (int8_t) uiCode;
  return ERR_NONE;
}

int32_t ParseMvdInfoCabac (PWelsDecoderContext pCtx, PWelsNeighAvail pNeighAvail, int8_t pRefIndex[LIST_A][30],
                           int16_t pMvdCache[LIST_A][30][2], int32_t index, int8_t iListIdx, int8_t iMvComp, int16_t& iMvdVal) {
  uint32_t uiCode;
  int32_t iIdxA = 0;
  //int32_t sym;
  PWelsCabacCtx pBinCtx = pCtx->pCabacCtx + NEW_CTX_OFFSET_MVD + iMvComp * CTX_NUM_MVD;
  iMvdVal = 0;

  if (pRefIndex[iListIdx][g_kuiCache30ScanIdx[index] - 6] >= 0)
    iIdxA = WELS_ABS (pMvdCache[iListIdx][g_kuiCache30ScanIdx[index] - 6][iMvComp]);
  if (pRefIndex[iListIdx][g_kuiCache30ScanIdx[index] - 1] >= 0)
    iIdxA += WELS_ABS (pMvdCache[iListIdx][g_kuiCache30ScanIdx[index] - 1][iMvComp]);

  int32_t iCtxInc = 0;
  if (iIdxA >= 3)
    iCtxInc = 1 + (iIdxA > 32);

  WELS_READ_VERIFY (DecodeBinCabac (pCtx->pCabacDecEngine,  pBinCtx + iCtxInc, uiCode));
  if (uiCode) {
    WELS_READ_VERIFY (DecodeUEGMvCabac (pCtx->pCabacDecEngine, pBinCtx + 3, 3, uiCode));
    iMvdVal = (int16_t) (uiCode + 1);
    WELS_READ_VERIFY (DecodeBypassCabac (pCtx->pCabacDecEngine, uiCode));
    if (uiCode) {
      iMvdVal = -iMvdVal;
    }
  } else {
    iMvdVal = 0;
  }
  return ERR_NONE;
}

int32_t ParseCbpInfoCabac (PWelsDecoderContext pCtx, PWelsNeighAvail pNeighAvail, uint32_t& uiCbp) {
  int32_t iIdxA = 0, iIdxB = 0, pALeftMb[2], pBTopMb[2];
  uiCbp = 0;
  uint32_t pCbpBit[6];
  int32_t iCtxInc;

  //Luma: bit by bit for 4 8x8 blocks in z-order
  pBTopMb[0]  = pNeighAvail->iTopAvail  && pNeighAvail->iTopType  != MB_TYPE_INTRA_PCM
                && ((pNeighAvail->iTopCbp  & (1 << 2)) == 0);
  pBTopMb[1]  = pNeighAvail->iTopAvail  && pNeighAvail->iTopType  != MB_TYPE_INTRA_PCM
                && ((pNeighAvail->iTopCbp  & (1 << 3)) == 0);
  pALeftMb[0] = pNeighAvail->iLeftAvail && pNeighAvail->iLeftType != MB_TYPE_INTRA_PCM
                && ((pNeighAvail->iLeftCbp & (1 << 1)) == 0);
  pALeftMb[1] = pNeighAvail->iLeftAvail && pNeighAvail->iLeftType != MB_TYPE_INTRA_PCM
                && ((pNeighAvail->iLeftCbp & (1 << 3)) == 0);

  //left_top 8x8 block
  iCtxInc = pALeftMb[0] + (pBTopMb[0] << 1);
  WELS_READ_VERIFY (DecodeBinCabac (pCtx->pCabacDecEngine, pCtx->pCabacCtx + NEW_CTX_OFFSET_CBP + iCtxInc, pCbpBit[0]));
  if (pCbpBit[0])
    uiCbp += 0x01;

  //right_top 8x8 block
  iIdxA = !pCbpBit[0];
  iCtxInc = iIdxA + (pBTopMb[1] << 1);
  WELS_READ_VERIFY (DecodeBinCabac (pCtx->pCabacDecEngine, pCtx->pCabacCtx + NEW_CTX_OFFSET_CBP + iCtxInc, pCbpBit[1]));
  if (pCbpBit[1])
    uiCbp += 0x02;

  //left_bottom 8x8 block
  iIdxB = !pCbpBit[0];
  iCtxInc = pALeftMb[1] + (iIdxB << 1);
  WELS_READ_VERIFY (DecodeBinCabac (pCtx->pCabacDecEngine, pCtx->pCabacCtx + NEW_CTX_OFFSET_CBP + iCtxInc, pCbpBit[2]));
  if (pCbpBit[2])
    uiCbp += 0x04;

  //right_bottom 8x8 block
  iIdxB = !pCbpBit[1];
  iIdxA = !pCbpBit[2];
  iCtxInc = iIdxA + (iIdxB << 1);
  WELS_READ_VERIFY (DecodeBinCabac (pCtx->pCabacDecEngine, pCtx->pCabacCtx + NEW_CTX_OFFSET_CBP + iCtxInc, pCbpBit[3]));
  if (pCbpBit[3])
    uiCbp += 0x08;

  if (pCtx->pSps->uiChromaFormatIdc == 0)//monochroma
    return ERR_NONE;


  //Chroma: bit by bit
  iIdxB = pNeighAvail->iTopAvail  && (pNeighAvail->iTopType  == MB_TYPE_INTRA_PCM || (pNeighAvail->iTopCbp  >> 4));
  iIdxA = pNeighAvail->iLeftAvail && (pNeighAvail->iLeftType == MB_TYPE_INTRA_PCM || (pNeighAvail->iLeftCbp >> 4));

  //BitIdx = 0
  iCtxInc = iIdxA + (iIdxB << 1);
  WELS_READ_VERIFY (DecodeBinCabac (pCtx->pCabacDecEngine, pCtx->pCabacCtx + NEW_CTX_OFFSET_CBP + CTX_NUM_CBP + iCtxInc,
                                    pCbpBit[4]));

  //BitIdx = 1
  if (pCbpBit[4]) {
    iIdxB = pNeighAvail->iTopAvail  && (pNeighAvail->iTopType  == MB_TYPE_INTRA_PCM || (pNeighAvail->iTopCbp  >> 4) == 2);
    iIdxA = pNeighAvail->iLeftAvail && (pNeighAvail->iLeftType == MB_TYPE_INTRA_PCM || (pNeighAvail->iLeftCbp >> 4) == 2);
    iCtxInc = iIdxA + (iIdxB << 1);
    WELS_READ_VERIFY (DecodeBinCabac (pCtx->pCabacDecEngine,
                                      pCtx->pCabacCtx + NEW_CTX_OFFSET_CBP + 2 * CTX_NUM_CBP + iCtxInc,
                                      pCbpBit[5]));
    uiCbp += 1 << (4 + pCbpBit[5]);

  }

  return ERR_NONE;
}

int32_t ParseDeltaQpCabac (PWelsDecoderContext pCtx, int32_t& iQpDelta) {
  uint32_t uiCode;
  PSlice pCurrSlice = & (pCtx->pCurDqLayer->sLayerInfo.sSliceInLayer);
  iQpDelta = 0;
  PWelsCabacCtx pBinCtx = pCtx->pCabacCtx + NEW_CTX_OFFSET_DELTA_QP;
  int32_t iCtxInc = (pCurrSlice->iLastDeltaQp != 0);
  WELS_READ_VERIFY (DecodeBinCabac (pCtx->pCabacDecEngine, pBinCtx + iCtxInc, uiCode));
  if (uiCode != 0) {
    WELS_READ_VERIFY (DecodeUnaryBinCabac (pCtx->pCabacDecEngine, pBinCtx + 2, 1, uiCode));
    uiCode++;
    iQpDelta = (uiCode + 1) >> 1;
    if ((uiCode & 1) == 0)
      iQpDelta = - iQpDelta;
  }
  pCurrSlice->iLastDeltaQp = iQpDelta;
  return ERR_NONE;
}

int32_t ParseCbfInfoCabac (PWelsNeighAvail pNeighAvail, uint8_t* pNzcCache, int32_t iZIndex, int32_t iResProperty,
                           PWelsDecoderContext pCtx, uint32_t& uiCbfBit) {
  int8_t nA, nB/*, zigzag_idx = 0*/;
  int32_t iCurrBlkXy = pCtx->pCurDqLayer->iMbXyIndex;
  int32_t iTopBlkXy = iCurrBlkXy - pCtx->pCurDqLayer->iMbWidth; //default value: MB neighboring
  int32_t iLeftBlkXy = iCurrBlkXy - 1; //default value: MB neighboring
  uint16_t* pCbfDc = pCtx->pCurDqLayer->pCbfDc;
  uint32_t* pMbType = pCtx->pCurDqLayer->pDec->pMbType;
  int32_t iCtxInc;
  uiCbfBit = 0;
  nA = nB = (int8_t)!!IS_INTRA (pMbType[iCurrBlkXy]);

  if (iResProperty == I16_LUMA_DC || iResProperty == CHROMA_DC_U || iResProperty == CHROMA_DC_V) { //DC
    if (pNeighAvail->iTopAvail)
      nB = (pMbType[iTopBlkXy] == MB_TYPE_INTRA_PCM) || ((pCbfDc[iTopBlkXy] >> iResProperty) & 1);
    if (pNeighAvail->iLeftAvail)
      nA = (pMbType[iLeftBlkXy] == MB_TYPE_INTRA_PCM) || ((pCbfDc[iLeftBlkXy] >> iResProperty) & 1);
    iCtxInc = nA + (nB << 1);
    WELS_READ_VERIFY (DecodeBinCabac (pCtx->pCabacDecEngine,
                                      pCtx->pCabacCtx + NEW_CTX_OFFSET_CBF + g_kBlockCat2CtxOffsetCBF[iResProperty] + iCtxInc, uiCbfBit));
    if (uiCbfBit)
      pCbfDc[iCurrBlkXy] |= (1 << iResProperty);
  } else { //AC
    //for 4x4 blk, make sure blk-idx is correct
    if (pNzcCache[g_kCacheNzcScanIdx[iZIndex] - 8] != 0xff) { //top blk available
      if (g_kTopBlkInsideMb[iZIndex])
        iTopBlkXy = iCurrBlkXy;
      nB = pNzcCache[g_kCacheNzcScanIdx[iZIndex] - 8] || pMbType[iTopBlkXy]  == MB_TYPE_INTRA_PCM;
    }
    if (pNzcCache[g_kCacheNzcScanIdx[iZIndex] - 1] != 0xff) { //left blk available
      if (g_kLeftBlkInsideMb[iZIndex])
        iLeftBlkXy = iCurrBlkXy;
      nA = pNzcCache[g_kCacheNzcScanIdx[iZIndex] - 1] || pMbType[iLeftBlkXy] == MB_TYPE_INTRA_PCM;
    }

    iCtxInc = nA + (nB << 1);
    WELS_READ_VERIFY (DecodeBinCabac (pCtx->pCabacDecEngine,
                                      pCtx->pCabacCtx + NEW_CTX_OFFSET_CBF + g_kBlockCat2CtxOffsetCBF[iResProperty] + iCtxInc, uiCbfBit));
  }
  return ERR_NONE;
}

int32_t ParseSignificantMapCabac (int32_t* pSignificantMap, int32_t iResProperty, PWelsDecoderContext pCtx,
                                  uint32_t& uiCoeffNum) {
  uint32_t uiCode;

  PWelsCabacCtx pMapCtx  = pCtx->pCabacCtx + (iResProperty == LUMA_DC_AC_8 ? NEW_CTX_OFFSET_MAP_8x8 : NEW_CTX_OFFSET_MAP)
                           + g_kBlockCat2CtxOffsetMap [iResProperty];
  PWelsCabacCtx pLastCtx = pCtx->pCabacCtx + (iResProperty == LUMA_DC_AC_8 ? NEW_CTX_OFFSET_LAST_8x8 :
                           NEW_CTX_OFFSET_LAST) + g_kBlockCat2CtxOffsetLast[iResProperty];


  int32_t i;
  uiCoeffNum = 0;
  int32_t i0 = 0;
  int32_t i1 = g_kMaxPos[iResProperty];

  int32_t iCtx;

  for (i = i0; i < i1; ++i) {
    iCtx = (iResProperty == LUMA_DC_AC_8 ? g_kuiIdx2CtxSignificantCoeffFlag8x8[i] : i);
    //read significant
    WELS_READ_VERIFY (DecodeBinCabac (pCtx->pCabacDecEngine, pMapCtx + iCtx, uiCode));
    if (uiCode) {
      * (pSignificantMap++) = 1;
      ++ uiCoeffNum;
      //read last significant
      iCtx = (iResProperty == LUMA_DC_AC_8 ? g_kuiIdx2CtxLastSignificantCoeffFlag8x8[i] : i);
      WELS_READ_VERIFY (DecodeBinCabac (pCtx->pCabacDecEngine, pLastCtx + iCtx, uiCode));
      if (uiCode) {
        memset (pSignificantMap, 0, (i1 - i) * sizeof (int32_t));
        return ERR_NONE;
      }
    } else
      * (pSignificantMap++) = 0;
  }

  //deal with last pSignificantMap if no data
  //if(i < i1+1)
  {
    *pSignificantMap = 1;
    ++uiCoeffNum;
  }

  return ERR_NONE;
}

int32_t ParseSignificantCoeffCabac (int32_t* pSignificant, int32_t iResProperty, PWelsDecoderContext pCtx) {
  uint32_t uiCode;
  PWelsCabacCtx pOneCtx = pCtx->pCabacCtx + (iResProperty == LUMA_DC_AC_8 ? NEW_CTX_OFFSET_ONE_8x8 : NEW_CTX_OFFSET_ONE) +
                          g_kBlockCat2CtxOffsetOne[iResProperty];
  PWelsCabacCtx pAbsCtx = pCtx->pCabacCtx + (iResProperty == LUMA_DC_AC_8 ? NEW_CTX_OFFSET_ABS_8x8 : NEW_CTX_OFFSET_ABS) +
                          g_kBlockCat2CtxOffsetAbs[iResProperty];

  const int16_t iMaxType = g_kMaxC2[iResProperty];
  int32_t i = g_kMaxPos[iResProperty];
  int32_t* pCoff = pSignificant + i;
  int32_t c1 = 1;
  int32_t c2 = 0;
  for (; i >= 0; --i) {
    if (*pCoff != 0) {
      WELS_READ_VERIFY (DecodeBinCabac (pCtx->pCabacDecEngine, pOneCtx + c1, uiCode));
      *pCoff += uiCode;
      if (*pCoff == 2) {
        WELS_READ_VERIFY (DecodeUEGLevelCabac (pCtx->pCabacDecEngine, pAbsCtx + c2, uiCode));
        *pCoff += uiCode;
        ++c2;
        c2 = WELS_MIN (c2, iMaxType);
        c1 = 0;
      } else if (c1) {
        ++c1;
        c1 = WELS_MIN (c1, 4);
      }
      WELS_READ_VERIFY (DecodeBypassCabac (pCtx->pCabacDecEngine, uiCode));
      if (uiCode)
        *pCoff = - *pCoff;
    }
    pCoff--;
  }
  return ERR_NONE;
}

int32_t ParseResidualBlockCabac8x8 (PWelsNeighAvail pNeighAvail, uint8_t* pNonZeroCountCache, SBitStringAux* pBsAux,
                                    int32_t iIndex, int32_t iMaxNumCoeff, const uint8_t* pScanTable, int32_t iResProperty,
                                    short* sTCoeff, /*int mb_mode*/ uint8_t uiQp, PWelsDecoderContext pCtx) {
  uint32_t uiTotalCoeffNum = 0;
  uint32_t uiCbpBit;
  int32_t pSignificantMap[64] = {0};

  int32_t iMbResProperty = 0;
  GetMbResProperty (&iMbResProperty, &iResProperty, false);
  const uint16_t* pDeQuantMul = (pCtx->bUseScalingList) ? pCtx->pDequant_coeff8x8[iMbResProperty - 6][uiQp] :
                                g_kuiDequantCoeff8x8[uiQp];

  uiCbpBit = 1; // for 8x8, MaxNumCoeff == 64 && uiCbpBit == 1
  if (uiCbpBit) { //has coeff
    WELS_READ_VERIFY (ParseSignificantMapCabac (pSignificantMap, iResProperty, pCtx, uiTotalCoeffNum));
    WELS_READ_VERIFY (ParseSignificantCoeffCabac (pSignificantMap, iResProperty, pCtx));
  }

  pNonZeroCountCache[g_kCacheNzcScanIdx[iIndex]] =
    pNonZeroCountCache[g_kCacheNzcScanIdx[iIndex + 1]] =
      pNonZeroCountCache[g_kCacheNzcScanIdx[iIndex + 2]] =
        pNonZeroCountCache[g_kCacheNzcScanIdx[iIndex + 3]] = (uint8_t)uiTotalCoeffNum;
  if (uiTotalCoeffNum == 0) {
    return ERR_NONE;
  }
  int32_t j = 0, i;
  if (iResProperty == LUMA_DC_AC_8) {
    do {
      if (pSignificantMap[j] != 0) {
        i = pScanTable[ j ];
        sTCoeff[i] = uiQp >= 36 ? ((pSignificantMap[j] * pDeQuantMul[i]) * (1 << (uiQp / 6 - 6))) : ((
                       pSignificantMap[j] * pDeQuantMul[i] + (1 << (5 - uiQp / 6))) >> (6 - uiQp / 6));
      }
      ++j;
    } while (j < 64);
  }

  return ERR_NONE;
}

int32_t ParseResidualBlockCabac (PWelsNeighAvail pNeighAvail, uint8_t* pNonZeroCountCache, SBitStringAux* pBsAux,
                                 int32_t iIndex, int32_t iMaxNumCoeff,
                                 const uint8_t* pScanTable, int32_t iResProperty, short* sTCoeff, /*int mb_mode*/ uint8_t uiQp,
                                 PWelsDecoderContext pCtx) {
  int32_t iCurNzCacheIdx;
  uint32_t uiTotalCoeffNum = 0;
  uint32_t uiCbpBit;
  int32_t pSignificantMap[16] = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

  int32_t iMbResProperty = 0;
  GetMbResProperty (&iMbResProperty, &iResProperty, false);
  const uint16_t* pDeQuantMul = (pCtx->bUseScalingList) ? pCtx->pDequant_coeff4x4[iMbResProperty][uiQp] :
                                g_kuiDequantCoeff[uiQp];

  WELS_READ_VERIFY (ParseCbfInfoCabac (pNeighAvail, pNonZeroCountCache, iIndex, iResProperty, pCtx, uiCbpBit));
  if (uiCbpBit) { //has coeff
    WELS_READ_VERIFY (ParseSignificantMapCabac (pSignificantMap, iResProperty, pCtx, uiTotalCoeffNum));
    WELS_READ_VERIFY (ParseSignificantCoeffCabac (pSignificantMap, iResProperty, pCtx));
  }

  iCurNzCacheIdx = g_kCacheNzcScanIdx[iIndex];
  pNonZeroCountCache[iCurNzCacheIdx] = (uint8_t)uiTotalCoeffNum;
  if (uiTotalCoeffNum == 0) {
    return ERR_NONE;
  }
  int32_t j = 0;
  if (iResProperty == I16_LUMA_DC) {
    do {
      sTCoeff[pScanTable[j]] = pSignificantMap[j];
      ++j;
    } while (j < 16);
    WelsLumaDcDequantIdct (sTCoeff, uiQp, pCtx);
  } else if (iResProperty == CHROMA_DC_U || iResProperty == CHROMA_DC_V) {
    do {
      sTCoeff[pScanTable[j]] = pSignificantMap[j];
      ++j;
    } while (j < 4);
    //iHadamard2x2
    WelsChromaDcIdct (sTCoeff);
    //scaling
    if (!pCtx->bUseScalingList) {
      for (j = 0; j < 4; ++j) {
        sTCoeff[pScanTable[j]] = (int16_t) ((int64_t)sTCoeff[pScanTable[j]] * (int64_t)pDeQuantMul[0] >> 1);
      }
    } else { //with scaling list
      for (j = 0; j < 4; ++j) {
        sTCoeff[pScanTable[j]] = (int16_t) ((int64_t)sTCoeff[pScanTable[j]] * (int64_t)pDeQuantMul[0] >> 5);
      }
    }
  } else { //luma ac, chroma ac
    do {
      if (pSignificantMap[j] != 0) {
        if (!pCtx->bUseScalingList) {
          sTCoeff[pScanTable[j]] = pSignificantMap[j] * pDeQuantMul[pScanTable[j] & 0x07];
        } else {
          sTCoeff[pScanTable[j]] = (int16_t) (((int64_t)pSignificantMap[j] * (int64_t)pDeQuantMul[pScanTable[j]] + 8) >> 4);
        }
      }
      ++j;
    } while (j < 16);
  }
  return ERR_NONE;
}

int32_t ParseIPCMInfoCabac (PWelsDecoderContext pCtx) {
  int32_t i;
  PWelsCabacDecEngine pCabacDecEngine = pCtx->pCabacDecEngine;
  SBitStringAux* pBsAux = pCtx->pCurDqLayer->pBitStringAux;
  SDqLayer* pCurDqLayer = pCtx->pCurDqLayer;
  int32_t iDstStrideLuma = pCurDqLayer->pDec->iLinesize[0];
  int32_t iDstStrideChroma = pCurDqLayer->pDec->iLinesize[1];
  int32_t iMbX = pCurDqLayer->iMbX;
  int32_t iMbY = pCurDqLayer->iMbY;
  int32_t iMbXy = pCurDqLayer->iMbXyIndex;

  int32_t iMbOffsetLuma = (iMbX + iMbY * iDstStrideLuma) << 4;
  int32_t iMbOffsetChroma = (iMbX + iMbY * iDstStrideChroma) << 3;

  uint8_t* pMbDstY = pCtx->pDec->pData[0] + iMbOffsetLuma;
  uint8_t* pMbDstU = pCtx->pDec->pData[1] + iMbOffsetChroma;
  uint8_t* pMbDstV = pCtx->pDec->pData[2] + iMbOffsetChroma;

  uint8_t* pPtrSrc;

  pCurDqLayer->pDec->pMbType[iMbXy] = MB_TYPE_INTRA_PCM;
  RestoreCabacDecEngineToBS (pCabacDecEngine, pBsAux);
  intX_t iBytesLeft = pBsAux->pEndBuf - pBsAux->pCurBuf;
  if (iBytesLeft < 384) {
    return GENERATE_ERROR_NO (ERR_LEVEL_MB_DATA, ERR_CABAC_NO_BS_TO_READ);
  }
  pPtrSrc = pBsAux->pCurBuf;
  if (!pCtx->pParam->bParseOnly) {
    for (i = 0; i < 16; i++) {   //luma
      memcpy (pMbDstY, pPtrSrc, 16);
      pMbDstY += iDstStrideLuma;
      pPtrSrc += 16;
    }
    for (i = 0; i < 8; i++) {   //cb
      memcpy (pMbDstU, pPtrSrc, 8);
      pMbDstU += iDstStrideChroma;
      pPtrSrc += 8;
    }
    for (i = 0; i < 8; i++) {   //cr
      memcpy (pMbDstV, pPtrSrc, 8);
      pMbDstV += iDstStrideChroma;
      pPtrSrc += 8;
    }
  }

  pBsAux->pCurBuf += 384;

  pCurDqLayer->pLumaQp[iMbXy] = 0;
  pCurDqLayer->pChromaQp[iMbXy][0] = pCurDqLayer->pChromaQp[iMbXy][1] = 0;
  memset (pCurDqLayer->pNzc[iMbXy], 16, sizeof (pCurDqLayer->pNzc[iMbXy]));

  //step 4: cabac engine init
  WELS_READ_VERIFY (InitReadBits (pBsAux, 1));
  WELS_READ_VERIFY (InitCabacDecEngineFromBS (pCabacDecEngine, pBsAux));
  return ERR_NONE;
}
void    UpdateP8x8RefCacheIdxCabac (int8_t pRefIndex[LIST_A][30], const int16_t& iPartIdx,
                                    const int32_t& listIdx, const int8_t& iRef) {
  const uint8_t uiCacheIdx = g_kuiCache30ScanIdx[iPartIdx];
  pRefIndex[listIdx][uiCacheIdx] = pRefIndex[listIdx][uiCacheIdx + 1] = pRefIndex[listIdx][uiCacheIdx + 6] =
                                     pRefIndex[listIdx][uiCacheIdx + 7] = iRef;
}
}
