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
 * \file    set_mb_syn_cabac.cpp
 *
 * \brief   cabac coding engine
 *
 * \date    10/11/2014 Created
 *
 *************************************************************************************
 */
#include <string.h>
#include "typedefs.h"
#include "macros.h"
#include "set_mb_syn_cabac.h"
#include "encoder.h"
#include "golomb_common.h"

namespace {

const int8_t g_kiClz5Table[32] = {
  6, 5, 4, 4, 3, 3, 3, 3, 2, 2, 2, 2, 2, 2, 2, 2,
  1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1
};

void PropagateCarry (uint8_t* pBufCur, uint8_t* pBufStart) {
  for (; pBufCur > pBufStart; --pBufCur)
    if (++ * (pBufCur - 1))
      break;
}

} // anon ns.

namespace WelsEnc {

void WelsCabacInit (void* pCtx) {
  sWelsEncCtx* pEncCtx = (sWelsEncCtx*)pCtx;
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
        pEncCtx->sWelsCabacContexts[iModel][iQp][iIdx].Set (uiStateIdx, uiValMps);
      }
  }
}

void WelsCabacContextInit (void* pCtx, SCabacCtx* pCbCtx, int32_t iModel) {
  sWelsEncCtx* pEncCtx = (sWelsEncCtx*)pCtx;
  int32_t iIdx =  pEncCtx->eSliceType == WelsCommon::I_SLICE ? 0 : iModel + 1;
  int32_t iQp = pEncCtx->iGlobalQp;
  memcpy (pCbCtx->m_sStateCtx, pEncCtx->sWelsCabacContexts[iIdx][iQp],
          WELS_CONTEXT_COUNT * sizeof (SStateCtx));
}

void  WelsCabacEncodeInit (SCabacCtx* pCbCtx, uint8_t* pBuf,  uint8_t* pEnd) {
  pCbCtx->m_uiLow     = 0;
  pCbCtx->m_iLowBitCnt = 9;
  pCbCtx->m_iRenormCnt = 0;
  pCbCtx->m_uiRange   = 510;
  pCbCtx->m_pBufStart = pBuf;
  pCbCtx->m_pBufEnd = pEnd;
  pCbCtx->m_pBufCur = pBuf;
}

void WelsCabacEncodeUpdateLowNontrivial_ (SCabacCtx* pCbCtx) {
  int32_t iLowBitCnt = pCbCtx->m_iLowBitCnt;
  int32_t iRenormCnt = pCbCtx->m_iRenormCnt;
  cabac_low_t uiLow = pCbCtx->m_uiLow;

  do {
    uint8_t* pBufCur = pCbCtx->m_pBufCur;
    const int32_t kiInc = CABAC_LOW_WIDTH - 1 - iLowBitCnt;

    uiLow <<= kiInc;
    if (uiLow & cabac_low_t (1) << (CABAC_LOW_WIDTH - 1))
      PropagateCarry (pBufCur, pCbCtx->m_pBufStart);

    if (CABAC_LOW_WIDTH > 32) {
      WRITE_BE_32 (pBufCur, (uint32_t) (uiLow >> 31));
      pBufCur += 4;
    }
    *pBufCur++ = (uint8_t) (uiLow >> 23);
    *pBufCur++ = (uint8_t) (uiLow >> 15);
    iRenormCnt -= kiInc;
    iLowBitCnt = 15;
    uiLow &= (1u << iLowBitCnt) - 1;
    pCbCtx->m_pBufCur = pBufCur;
  } while (iLowBitCnt + iRenormCnt > CABAC_LOW_WIDTH - 1);

  pCbCtx->m_iLowBitCnt = iLowBitCnt + iRenormCnt;
  pCbCtx->m_uiLow = uiLow << iRenormCnt;
}

void WelsCabacEncodeDecisionLps_ (SCabacCtx* pCbCtx, int32_t iCtx) {
  const int32_t kiState = pCbCtx->m_sStateCtx[iCtx].State();
  uint32_t uiRange = pCbCtx->m_uiRange;
  uint32_t uiRangeLps = g_kuiCabacRangeLps[kiState][ (uiRange & 0xff) >> 6];
  uiRange -= uiRangeLps;
  pCbCtx->m_sStateCtx[iCtx].Set (g_kuiStateTransTable[kiState][0],
                                 pCbCtx->m_sStateCtx[iCtx].Mps() ^ (kiState == 0));

  WelsCabacEncodeUpdateLow_ (pCbCtx);
  pCbCtx->m_uiLow += uiRange;

  const int32_t kiRenormAmount = g_kiClz5Table[uiRangeLps >> 3];
  pCbCtx->m_uiRange = uiRangeLps << kiRenormAmount;
  pCbCtx->m_iRenormCnt = kiRenormAmount;
}

void WelsCabacEncodeTerminate (SCabacCtx* pCbCtx, uint32_t uiBin) {
  pCbCtx->m_uiRange -= 2;
  if (uiBin) {
    WelsCabacEncodeUpdateLow_ (pCbCtx);
    pCbCtx->m_uiLow  += pCbCtx->m_uiRange;

    const int32_t kiRenormAmount = 7;
    pCbCtx->m_uiRange = 2 << kiRenormAmount;
    pCbCtx->m_iRenormCnt = kiRenormAmount;

    WelsCabacEncodeUpdateLow_ (pCbCtx);
    pCbCtx->m_uiLow |= 0x80;
  } else {
    const int32_t kiRenormAmount = pCbCtx->m_uiRange >> 8 ^ 1;
    pCbCtx->m_uiRange = pCbCtx->m_uiRange << kiRenormAmount;
    pCbCtx->m_iRenormCnt += kiRenormAmount;
  }
}
void WelsCabacEncodeUeBypass (SCabacCtx* pCbCtx, int32_t iExpBits, uint32_t uiVal) {
  int32_t iSufS = uiVal;
  int32_t iStopLoop = 0;
  int32_t k = iExpBits;
  do {
    if (iSufS >= (1 << k)) {
      WelsCabacEncodeBypassOne (pCbCtx, 1);
      iSufS = iSufS - (1 << k);
      k++;
    } else {
      WelsCabacEncodeBypassOne (pCbCtx, 0);
      while (k--)
        WelsCabacEncodeBypassOne (pCbCtx, (iSufS >> k) & 1);
      iStopLoop = 1;
    }
  } while (!iStopLoop);
}

void WelsCabacEncodeFlush (SCabacCtx* pCbCtx) {
  WelsCabacEncodeTerminate (pCbCtx, 1);

  cabac_low_t uiLow = pCbCtx->m_uiLow;
  int32_t iLowBitCnt = pCbCtx->m_iLowBitCnt;
  uint8_t* pBufCur = pCbCtx->m_pBufCur;

  uiLow <<= CABAC_LOW_WIDTH - 1 - iLowBitCnt;
  if (uiLow & cabac_low_t (1) << (CABAC_LOW_WIDTH - 1))
    PropagateCarry (pBufCur, pCbCtx->m_pBufStart);
  for (; (iLowBitCnt -= 8) >= 0; uiLow <<= 8)
    * pBufCur++ = (uint8_t) (uiLow >> (CABAC_LOW_WIDTH - 9));

  pCbCtx->m_pBufCur = pBufCur;
}

uint8_t* WelsCabacEncodeGetPtr (SCabacCtx* pCbCtx) {
  return pCbCtx->m_pBufCur;
}
}
