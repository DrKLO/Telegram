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
 * \file    set_mb_syn_cavlc.h
 *
 * \brief   Seting all syntax elements of mb and decoding residual with cavlc
 *
 * \date    05/19/2009 Created
 *
 *************************************************************************************
 */

#include "svc_set_mb_syn.h"
#include "vlc_encoder.h"
#include "cpu_core.h"
#include "wels_const.h"

namespace WelsEnc {

const  ALIGNED_DECLARE (uint8_t, g_kuiZeroLeftMap[16], 16) = {
  0, 1, 2, 3, 4, 5, 6, 7, 7, 7, 7, 7, 7, 7, 7, 7
};


/*
 *  Exponential Golomb codes encoding routines
 */

#define    CAVLC_BS_INIT( pBs )  \
  uint8_t  * pBufPtr = pBs->pCurBuf; \
  uint32_t   uiCurBits = pBs->uiCurBits; \
  int32_t    iLeftBits = pBs->iLeftBits;

#define    CAVLC_BS_UNINIT( pBs ) \
  pBs->pCurBuf = pBufPtr;  \
  pBs->uiCurBits = uiCurBits;  \
  pBs->iLeftBits = iLeftBits;

#define    CAVLC_BS_WRITE( n,  v ) \
  {  \
  if ( (n) < iLeftBits ) {\
  uiCurBits = (uiCurBits<<(n))|(v);\
  iLeftBits -= (n);\
  }\
  else {\
  (n) -= iLeftBits;\
  uiCurBits = (uiCurBits<<iLeftBits) | ((v)>>(n));\
  WRITE_BE_32(pBufPtr, uiCurBits);\
  pBufPtr += 4;\
  uiCurBits = (v) & ((1<<(n))-1);\
  iLeftBits = 32 - (n);\
  }\
  } ;


int32_t CavlcParamCal_c (int16_t* pCoffLevel, uint8_t* pRun, int16_t* pLevel, int32_t* pTotalCoeff ,
                         int32_t iLastIndex) {
  int32_t iTotalZeros = 0;
  int32_t iTotalCoeffs = 0;

  while (iLastIndex >= 0 && pCoffLevel[iLastIndex] == 0) {
    -- iLastIndex;
  }

  while (iLastIndex >= 0) {
    int32_t iCountZero = 0;
    pLevel[iTotalCoeffs] = pCoffLevel[iLastIndex--];

    while (iLastIndex >= 0 && pCoffLevel[iLastIndex] == 0) {
      ++ iCountZero;
      -- iLastIndex;
    }
    iTotalZeros += iCountZero;
    pRun[iTotalCoeffs++] = iCountZero;
  }
  *pTotalCoeff = iTotalCoeffs;
  return iTotalZeros;
}

int32_t  WriteBlockResidualCavlc (SWelsFuncPtrList* pFuncList, int16_t* pCoffLevel, int32_t iEndIdx,
                                  int32_t iCalRunLevelFlag,
                                  int32_t iResidualProperty, int8_t iNC, SBitStringAux* pBs) {
  ENFORCE_STACK_ALIGN_1D (int16_t, iLevel, 16, 16)
  ENFORCE_STACK_ALIGN_1D (uint8_t, uiRun, 16, 16)

  int32_t iTotalCoeffs = 0;
  int32_t iTrailingOnes = 0;
  int32_t iTotalZeros = 0, iZerosLeft = 0;
  uint32_t uiSign = 0;
  int32_t iLevelCode = 0, iLevelPrefix = 0, iLevelSuffix = 0, uiSuffixLength = 0, iLevelSuffixSize = 0;
  int32_t iValue = 0, iThreshold, iZeroLeft;
  int32_t n = 0;
  int32_t i = 0;


  CAVLC_BS_INIT (pBs);

  /*Step 1: calculate iLevel and iRun and total */

  if (iCalRunLevelFlag) {
    int32_t iCount = 0;
    iTotalZeros = pFuncList->pfCavlcParamCal (pCoffLevel, uiRun, iLevel, &iTotalCoeffs, iEndIdx);
    iCount = (iTotalCoeffs > 3) ? 3 : iTotalCoeffs;
    for (i = 0; i < iCount ; i++) {
      if (WELS_ABS (iLevel[i]) == 1) {
        iTrailingOnes ++;
        uiSign <<= 1;
        if (iLevel[i] < 0)
          uiSign |= 1;
      } else {
        break;

      }
    }
  }
  /*Step 3: coeff token */
  const uint8_t* upCoeffToken = &g_kuiVlcCoeffToken[g_kuiEncNcMapTable[iNC]][iTotalCoeffs][iTrailingOnes][0];
  iValue = upCoeffToken[0];
  n = upCoeffToken[1];

  if (iTotalCoeffs == 0) {
    CAVLC_BS_WRITE (n, iValue);

    CAVLC_BS_UNINIT (pBs);
    return ENC_RETURN_SUCCESS;
  }

  /* Step 4: */
  /*  trailing */
  n += iTrailingOnes;
  iValue = (iValue << iTrailingOnes) + uiSign;
  CAVLC_BS_WRITE (n, iValue);

  /*  levels */
  uiSuffixLength = (iTotalCoeffs > 10 && iTrailingOnes < 3) ? 1 : 0;

  for (i = iTrailingOnes; i < iTotalCoeffs; i++) {
    int32_t iVal = iLevel[i];

    iLevelCode = (iVal - 1) * (1 << 1);
    uiSign = (iLevelCode >> 31);
    iLevelCode = (iLevelCode ^ uiSign) + (uiSign << 1);
    iLevelCode -= ((i == iTrailingOnes) && (iTrailingOnes < 3)) << 1;

    iLevelPrefix = iLevelCode >> uiSuffixLength;
    iLevelSuffixSize = uiSuffixLength;
    iLevelSuffix = iLevelCode - (iLevelPrefix << uiSuffixLength);

    if (iLevelPrefix >= 14 && iLevelPrefix < 30 && uiSuffixLength == 0) {
      iLevelPrefix = 14;
      iLevelSuffix = iLevelCode - iLevelPrefix;
      iLevelSuffixSize = 4;
    } else if (iLevelPrefix >= 15) {
      iLevelPrefix = 15;
      iLevelSuffix = iLevelCode - (iLevelPrefix << uiSuffixLength);
      //for baseline profile,overflow when the length of iLevelSuffix is larger than 11.
      if (iLevelSuffix >> 11)
        return ENC_RETURN_VLCOVERFLOWFOUND;
      if (uiSuffixLength == 0) {
        iLevelSuffix -= 15;
      }
      iLevelSuffixSize = 12;
    }

    n = iLevelPrefix + 1 + iLevelSuffixSize;
    iValue = ((1 << iLevelSuffixSize) | iLevelSuffix);
    CAVLC_BS_WRITE (n, iValue);

    uiSuffixLength += !uiSuffixLength;
    iThreshold = 3 << (uiSuffixLength - 1);
    uiSuffixLength += ((iVal > iThreshold) || (iVal < -iThreshold)) && (uiSuffixLength < 6);

  }

  /* Step 5: total zeros */

  if (iTotalCoeffs < iEndIdx + 1) {
    if (CHROMA_DC != iResidualProperty) {
      const uint8_t* upTotalZeros = &g_kuiVlcTotalZeros[iTotalCoeffs][iTotalZeros][0];
      n = upTotalZeros[1];
      iValue = upTotalZeros[0];
      CAVLC_BS_WRITE (n, iValue);
    } else {
      const uint8_t* upTotalZeros = &g_kuiVlcTotalZerosChromaDc[iTotalCoeffs][iTotalZeros][0];
      n = upTotalZeros[1];
      iValue = upTotalZeros[0];
      CAVLC_BS_WRITE (n, iValue);
    }
  }

  /* Step 6: pRun before */
  iZerosLeft = iTotalZeros;
  for (i = 0; i + 1 < iTotalCoeffs && iZerosLeft > 0; ++ i) {
    const uint8_t uirun = uiRun[i];
    iZeroLeft = g_kuiZeroLeftMap[iZerosLeft];
    n = g_kuiVlcRunBefore[iZeroLeft][uirun][1];
    iValue = g_kuiVlcRunBefore[iZeroLeft][uirun][0];
    CAVLC_BS_WRITE (n, iValue);
    iZerosLeft -= uirun;
  }

  CAVLC_BS_UNINIT (pBs);
  return ENC_RETURN_SUCCESS;
}

void StashMBStatusCavlc (SDynamicSlicingStack* pDss, SSlice* pSlice, int32_t iMbSkipRun) {
  SBitStringAux* pBs = pSlice->pSliceBsa;
  pDss->pBsStackBufPtr          = pBs->pCurBuf;
  pDss->uiBsStackCurBits        = pBs->uiCurBits;
  pDss->iBsStackLeftBits        = pBs->iLeftBits;
  pDss->uiLastMbQp              = pSlice->uiLastMbQp;
  pDss->iMbSkipRunStack = iMbSkipRun;
}
int32_t StashPopMBStatusCavlc (SDynamicSlicingStack* pDss, SSlice* pSlice) {
  SBitStringAux* pBs = pSlice->pSliceBsa;
  pBs->pCurBuf          = pDss->pBsStackBufPtr;
  pBs->uiCurBits        = pDss->uiBsStackCurBits;
  pBs->iLeftBits        = pDss->iBsStackLeftBits;
  pSlice->uiLastMbQp    = pDss->uiLastMbQp;
  return pDss->iMbSkipRunStack;
}
void StashMBStatusCabac (SDynamicSlicingStack* pDss, SSlice* pSlice, int32_t iMbSkipRun) {
  SCabacCtx* pCtx = &pSlice->sCabacCtx;
  memcpy (&pDss->sStoredCabac, pCtx, sizeof (SCabacCtx));
  if (pDss->pRestoreBuffer) {
    int32_t iPosBitOffset =  GetBsPosCabac (pSlice) - pDss->iStartPos;
    int32_t iLen = ((iPosBitOffset >> 3) + ((iPosBitOffset & 0x07) ? 1 : 0));
    memcpy (pDss->pRestoreBuffer, pCtx->m_pBufStart, iLen);
  }
  pDss->uiLastMbQp =  pSlice->uiLastMbQp;
  pDss->iMbSkipRunStack = iMbSkipRun;
}
int32_t StashPopMBStatusCabac (SDynamicSlicingStack* pDss, SSlice* pSlice) {
  SCabacCtx* pCtx = &pSlice->sCabacCtx;
  memcpy (pCtx, &pDss->sStoredCabac, sizeof (SCabacCtx));
  if (pDss->pRestoreBuffer) {
    int32_t iPosBitOffset = GetBsPosCabac (pSlice) - pDss->iStartPos;
    int32_t iLen = ((iPosBitOffset >> 3) + ((iPosBitOffset & 0x07) ? 1 : 0));
    memcpy (pCtx->m_pBufStart, pDss->pRestoreBuffer, iLen);
  }
  pSlice->uiLastMbQp = pDss->uiLastMbQp;
  return pDss->iMbSkipRunStack;
}
int32_t GetBsPosCavlc (SSlice* pSlice) {
  return BsGetBitsPos (pSlice->pSliceBsa);
}
int32_t GetBsPosCabac (SSlice* pSlice) {
  return (int32_t) ((pSlice->sCabacCtx.m_pBufCur - pSlice->sCabacCtx.m_pBufStart) << 3) +
         (pSlice->sCabacCtx.m_iLowBitCnt - 9);
}
void WelsWriteSliceEndSyn (SSlice* pSlice, bool bEntropyCodingModeFlag) {
  SBitStringAux* pBs = pSlice->pSliceBsa;
  if (bEntropyCodingModeFlag) {
    WelsCabacEncodeFlush (&pSlice->sCabacCtx);
    pBs->pCurBuf = WelsCabacEncodeGetPtr (&pSlice->sCabacCtx);

  } else {
    BsRbspTrailingBits (pBs);
    BsFlush (pBs);
  }
}
void InitCoeffFunc (SWelsFuncPtrList* pFuncList, const uint32_t uiCpuFlag, int32_t iEntropyCodingModeFlag) {
  pFuncList->pfCavlcParamCal = CavlcParamCal_c;

#if defined(X86_32_ASM)
  if (uiCpuFlag & WELS_CPU_SSE2) {
    pFuncList->pfCavlcParamCal = CavlcParamCal_sse2;
  }
#endif

#ifdef X86_ASM
  if (uiCpuFlag & WELS_CPU_SSE42) {
    pFuncList->pfCavlcParamCal = CavlcParamCal_sse42;
  }
#endif
  if (iEntropyCodingModeFlag) {
    pFuncList->pfStashMBStatus = StashMBStatusCabac;
    pFuncList->pfStashPopMBStatus = StashPopMBStatusCabac;
    pFuncList->pfWelsSpatialWriteMbSyn = WelsSpatialWriteMbSynCabac;
    pFuncList->pfGetBsPosition = GetBsPosCabac;
  } else {
    pFuncList->pfStashMBStatus = StashMBStatusCavlc;
    pFuncList->pfStashPopMBStatus = StashPopMBStatusCavlc;
    pFuncList->pfWelsSpatialWriteMbSyn = WelsSpatialWriteMbSyn;
    pFuncList->pfGetBsPosition = GetBsPosCavlc;
  }
}


} // namespace WelsEnc
