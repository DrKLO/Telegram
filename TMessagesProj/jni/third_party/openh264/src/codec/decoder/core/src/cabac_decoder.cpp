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
 *      cabac_decoder.cpp:      deals with cabac state transition and related functions
 */
#include "cabac_decoder.h"
namespace WelsDec {
static const int16_t g_kMvdBinPos2Ctx [8] = {0, 1, 2, 3, 3, 3, 3, 3};

void WelsCabacGlobalInit (PWelsDecoderContext pCtx) {
  for (int32_t iModel = 0; iModel < 4; iModel++) {
    for (int32_t iQp = 0; iQp <= WELS_QP_MAX; iQp++)
      for (int32_t iIdx = 0; iIdx < WELS_CONTEXT_COUNT; iIdx++) {
        int32_t m               = g_kiCabacGlobalContextIdx[iIdx][iModel][0];
        int32_t n               = g_kiCabacGlobalContextIdx[iIdx][iModel][1];
        int32_t iPreCtxState    = WELS_CLIP3 ((((m * iQp) >> 4) + n), 1, 126);
        uint8_t uiValMps         = 0;
        uint8_t uiStateIdx       = 0;
        if (iPreCtxState <= 63) {
          uiStateIdx = 63 - iPreCtxState;
          uiValMps = 0;
        } else {
          uiStateIdx = iPreCtxState - 64;
          uiValMps = 1;
        }
        pCtx->sWelsCabacContexts[iModel][iQp][iIdx].uiState = uiStateIdx;
        pCtx->sWelsCabacContexts[iModel][iQp][iIdx].uiMPS = uiValMps;
      }
  }
  pCtx->bCabacInited = true;
}

// ------------------- 1. context initialization
void WelsCabacContextInit (PWelsDecoderContext  pCtx, uint8_t eSliceType, int32_t iCabacInitIdc, int32_t iQp) {
  int32_t iIdx =  pCtx->eSliceType == WelsCommon::I_SLICE ? 0 : iCabacInitIdc + 1;
  if (!pCtx->bCabacInited) {
    WelsCabacGlobalInit (pCtx);
  }
  memcpy (pCtx->pCabacCtx, pCtx->sWelsCabacContexts[iIdx][iQp],
          WELS_CONTEXT_COUNT * sizeof (SWelsCabacCtx));
}

// ------------------- 2. decoding Engine initialization
int32_t InitCabacDecEngineFromBS (PWelsCabacDecEngine pDecEngine, PBitStringAux pBsAux) {
  int32_t iRemainingBits = - pBsAux->iLeftBits; //pBsAux->iLeftBits < 0
  int32_t iRemainingBytes = (iRemainingBits >> 3) + 2; //+2: indicating the pre-read 2 bytes
  uint8_t* pCurr;

  pCurr = pBsAux->pCurBuf - iRemainingBytes;
  if (pCurr >= (pBsAux->pEndBuf - 1)) {
    return ERR_INFO_INVALID_ACCESS;
  }
  pDecEngine->uiOffset = ((pCurr[0] << 16) | (pCurr[1] << 8) | pCurr[2]);
  pDecEngine->uiOffset <<= 16;
  pDecEngine->uiOffset |= (pCurr[3] << 8) | pCurr[4];
  pDecEngine->iBitsLeft = 31;
  pDecEngine->pBuffCurr = pCurr + 5;

  pDecEngine->uiRange = WELS_CABAC_HALF;
  pDecEngine->pBuffStart = pBsAux->pStartBuf;
  pDecEngine->pBuffEnd = pBsAux->pEndBuf;
  pBsAux->iLeftBits = 0;
  return ERR_NONE;
}

void RestoreCabacDecEngineToBS (PWelsCabacDecEngine pDecEngine, PBitStringAux pBsAux) {
  //CABAC decoding finished, changing to SBitStringAux
  pDecEngine->pBuffCurr -= (pDecEngine->iBitsLeft >> 3);
  pDecEngine->iBitsLeft = 0;     //pcm_alignment_zero_bit in CABAC
  pBsAux->iLeftBits = 0;
  pBsAux->pStartBuf = pDecEngine->pBuffStart;
  pBsAux->pCurBuf = pDecEngine->pBuffCurr;
  pBsAux->uiCurBits = 0;
  pBsAux->iIndex = 0;
}

// ------------------- 3. actual decoding
int32_t Read32BitsCabac (PWelsCabacDecEngine pDecEngine, uint32_t& uiValue, int32_t& iNumBitsRead) {
  intX_t iLeftBytes = pDecEngine->pBuffEnd - pDecEngine->pBuffCurr;
  iNumBitsRead = 0;
  uiValue = 0;
  if (iLeftBytes <= 0) {
    return GENERATE_ERROR_NO (ERR_LEVEL_MB_DATA, ERR_CABAC_NO_BS_TO_READ);
  }
  switch (iLeftBytes) {
  case 3:
    uiValue = ((pDecEngine->pBuffCurr[0]) << 16 | (pDecEngine->pBuffCurr[1]) << 8 | (pDecEngine->pBuffCurr[2]));
    pDecEngine->pBuffCurr += 3;
    iNumBitsRead = 24;
    break;
  case 2:
    uiValue = ((pDecEngine->pBuffCurr[0]) << 8 | (pDecEngine->pBuffCurr[1]));
    pDecEngine->pBuffCurr += 2;
    iNumBitsRead = 16;
    break;
  case 1:
    uiValue = pDecEngine->pBuffCurr[0];
    pDecEngine->pBuffCurr += 1;
    iNumBitsRead = 8;
    break;
  default:
    uiValue = ((pDecEngine->pBuffCurr[0] << 24) | (pDecEngine->pBuffCurr[1]) << 16 | (pDecEngine->pBuffCurr[2]) << 8 |
               (pDecEngine->pBuffCurr[3]));
    pDecEngine->pBuffCurr += 4;
    iNumBitsRead = 32;
    break;
  }
  return ERR_NONE;
}

int32_t DecodeBinCabac (PWelsCabacDecEngine pDecEngine, PWelsCabacCtx pBinCtx, uint32_t& uiBinVal) {
  int32_t iErrorInfo = ERR_NONE;
  uint32_t uiState = pBinCtx->uiState;
  uiBinVal = pBinCtx->uiMPS;
  uint64_t uiOffset = pDecEngine->uiOffset;
  uint64_t uiRange = pDecEngine->uiRange;

  int32_t iRenorm = 1;
  uint32_t uiRangeLPS = g_kuiCabacRangeLps[uiState][ (uiRange >> 6) & 0x03];
  uiRange -= uiRangeLPS;
  if (uiOffset >= (uiRange << pDecEngine->iBitsLeft)) { //LPS
    uiOffset -= (uiRange << pDecEngine->iBitsLeft);
    uiBinVal ^= 0x0001;
    if (!uiState)
      pBinCtx->uiMPS ^= 0x01;
    pBinCtx->uiState = g_kuiStateTransTable[uiState][0];
    iRenorm = g_kRenormTable256[uiRangeLPS];
    uiRange = (uiRangeLPS << iRenorm);
  } else {  //MPS
    pBinCtx->uiState = g_kuiStateTransTable[uiState][1];
    if (uiRange >= WELS_CABAC_QUARTER) {
      pDecEngine->uiRange = uiRange;
      return ERR_NONE;
    } else {
      uiRange <<= 1;
    }
  }
  //Renorm
  pDecEngine->uiRange = uiRange;
  pDecEngine->iBitsLeft -= iRenorm;
  if (pDecEngine->iBitsLeft > 0) {
    pDecEngine->uiOffset = uiOffset;
    return ERR_NONE;
  }
  uint32_t uiVal = 0;
  int32_t iNumBitsRead = 0;
  iErrorInfo = Read32BitsCabac (pDecEngine, uiVal, iNumBitsRead);
  pDecEngine->uiOffset = (uiOffset << iNumBitsRead) | uiVal;
  pDecEngine->iBitsLeft += iNumBitsRead;
  if (iErrorInfo && pDecEngine->iBitsLeft < 0) {
    return iErrorInfo;
  }
  return ERR_NONE;
}

int32_t DecodeBypassCabac (PWelsCabacDecEngine pDecEngine, uint32_t& uiBinVal) {
  int32_t iErrorInfo = ERR_NONE;
  int32_t iBitsLeft = pDecEngine->iBitsLeft;
  uint64_t uiOffset = pDecEngine->uiOffset;
  uint64_t uiRangeValue;


  if (iBitsLeft <= 0) {
    uint32_t uiVal = 0;
    int32_t iNumBitsRead = 0;
    iErrorInfo = Read32BitsCabac (pDecEngine, uiVal, iNumBitsRead);
    uiOffset = (uiOffset << iNumBitsRead) | uiVal;
    iBitsLeft = iNumBitsRead;
    if (iErrorInfo && iBitsLeft == 0) {
      return iErrorInfo;
    }
  }
  iBitsLeft--;
  uiRangeValue = (pDecEngine->uiRange << iBitsLeft);
  if (uiOffset >= uiRangeValue) {
    pDecEngine->iBitsLeft = iBitsLeft;
    pDecEngine->uiOffset = uiOffset - uiRangeValue;
    uiBinVal = 1;
    return ERR_NONE;
  }
  pDecEngine->iBitsLeft = iBitsLeft;
  pDecEngine->uiOffset = uiOffset;
  uiBinVal = 0;
  return ERR_NONE;
}

int32_t DecodeTerminateCabac (PWelsCabacDecEngine pDecEngine, uint32_t& uiBinVal) {
  int32_t iErrorInfo = ERR_NONE;
  uint64_t uiRange = pDecEngine->uiRange - 2;
  uint64_t uiOffset = pDecEngine->uiOffset;

  if (uiOffset >= (uiRange << pDecEngine->iBitsLeft)) {
    uiBinVal = 1;
  } else {
    uiBinVal = 0;
    // Renorm
    if (uiRange < WELS_CABAC_QUARTER) {
      int32_t iRenorm = g_kRenormTable256[uiRange];
      pDecEngine->uiRange = (uiRange << iRenorm);
      pDecEngine->iBitsLeft -= iRenorm;
      if (pDecEngine->iBitsLeft < 0) {
        uint32_t uiVal = 0;
        int32_t iNumBitsRead = 0;
        iErrorInfo = Read32BitsCabac (pDecEngine, uiVal, iNumBitsRead);
        pDecEngine->uiOffset = (pDecEngine->uiOffset << iNumBitsRead) | uiVal;
        pDecEngine->iBitsLeft += iNumBitsRead;
      }
      if (iErrorInfo && pDecEngine->iBitsLeft < 0) {
        return iErrorInfo;
      }
      return ERR_NONE;
    } else {
      pDecEngine->uiRange = uiRange;
      return ERR_NONE;
    }
  }
  return ERR_NONE;
}

int32_t DecodeUnaryBinCabac (PWelsCabacDecEngine pDecEngine, PWelsCabacCtx pBinCtx, int32_t iCtxOffset,
                             uint32_t& uiSymVal) {
  uiSymVal = 0;
  WELS_READ_VERIFY (DecodeBinCabac (pDecEngine, pBinCtx, uiSymVal));
  if (uiSymVal == 0) {
    return ERR_NONE;
  } else {
    uint32_t uiCode;
    pBinCtx += iCtxOffset;
    uiSymVal = 0;
    do {
      WELS_READ_VERIFY (DecodeBinCabac (pDecEngine, pBinCtx, uiCode));
      ++uiSymVal;
    } while (uiCode != 0);
    return ERR_NONE;
  }
}

int32_t DecodeExpBypassCabac (PWelsCabacDecEngine pDecEngine, int32_t iCount, uint32_t& uiSymVal) {
  uint32_t uiCode;
  int32_t iSymTmp = 0;
  int32_t iSymTmp2 = 0;
  uiSymVal = 0;
  do {
    WELS_READ_VERIFY (DecodeBypassCabac (pDecEngine, uiCode));
    if (uiCode == 1) {
      iSymTmp += (1 << iCount);
      ++iCount;
    }
  } while (uiCode != 0 && iCount != 16);
  if (iCount == 16) {
    return GENERATE_ERROR_NO (ERR_LEVEL_MB_DATA, ERR_CABAC_UNEXPECTED_VALUE);
  }

  while (iCount--) {
    WELS_READ_VERIFY (DecodeBypassCabac (pDecEngine, uiCode));
    if (uiCode == 1) {
      iSymTmp2 |= (1 << iCount);
    }
  }
  uiSymVal = (uint32_t) (iSymTmp + iSymTmp2);
  return ERR_NONE;
}

uint32_t DecodeUEGLevelCabac (PWelsCabacDecEngine pDecEngine, PWelsCabacCtx pBinCtx, uint32_t& uiCode) {
  uiCode = 0;
  WELS_READ_VERIFY (DecodeBinCabac (pDecEngine, pBinCtx, uiCode));
  if (uiCode == 0)
    return ERR_NONE;
  else {
    uint32_t uiTmp, uiCount = 1;
    uiCode = 0;
    do {
      WELS_READ_VERIFY (DecodeBinCabac (pDecEngine, pBinCtx, uiTmp));
      ++uiCode;
      ++uiCount;
    } while (uiTmp != 0 && uiCount != 13);

    if (uiTmp != 0) {
      WELS_READ_VERIFY (DecodeExpBypassCabac (pDecEngine, 0, uiTmp));
      uiCode += uiTmp + 1;
    }
    return ERR_NONE;
  }
  return ERR_NONE;
}

int32_t DecodeUEGMvCabac (PWelsCabacDecEngine pDecEngine, PWelsCabacCtx pBinCtx, uint32_t iMaxBin,  uint32_t& uiCode) {
  WELS_READ_VERIFY (DecodeBinCabac (pDecEngine, pBinCtx + g_kMvdBinPos2Ctx[0], uiCode));
  if (uiCode == 0)
    return ERR_NONE;
  else {
    uint32_t uiTmp, uiCount = 1;
    uiCode = 0;
    do {
      WELS_READ_VERIFY (DecodeBinCabac (pDecEngine, pBinCtx + g_kMvdBinPos2Ctx[uiCount++], uiTmp));
      uiCode++;
    } while (uiTmp != 0 && uiCount != 8);

    if (uiTmp != 0) {
      WELS_READ_VERIFY (DecodeExpBypassCabac (pDecEngine, 3, uiTmp));
      uiCode += (uiTmp + 1);
    }
    return ERR_NONE;
  }
}
}
