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
 */
#include "AdaptiveQuantization.h"
#include "macros.h"
WELSVP_NAMESPACE_BEGIN



#define AVERAGE_TIME_MOTION                   (3000) //0.3046875 // 1/4 + 1/16 - 1/128 ~ 0.3 *AQ_TIME_INT_MULTIPLY
#define AVERAGE_TIME_TEXTURE_QUALITYMODE  (10000) //0.5 // 1/2 *AQ_TIME_INT_MULTIPLY
#define AVERAGE_TIME_TEXTURE_BITRATEMODE  (8750) //0.5 // 1/2 *AQ_TIME_INT_MULTIPLY
#define MODEL_ALPHA                           (9910) //1.5 //1.1102 *AQ_TIME_INT_MULTIPLY
#define MODEL_TIME                            (58185) //9.0 //5.9842 *AQ_TIME_INT_MULTIPLY

///////////////////////////////////////////////////////////////////////////////////////////////////////////////

CAdaptiveQuantization::CAdaptiveQuantization (int32_t iCpuFlag) {
  m_CPUFlag = iCpuFlag;
  m_eMethod   = METHOD_ADAPTIVE_QUANT;
  m_pfVar   = NULL;
  WelsMemset (&m_sAdaptiveQuantParam, 0, sizeof (m_sAdaptiveQuantParam));
  WelsInitVarFunc (m_pfVar, m_CPUFlag);
}

CAdaptiveQuantization::~CAdaptiveQuantization() {
}

EResult CAdaptiveQuantization::Process (int32_t iType, SPixMap* pSrcPixMap, SPixMap* pRefPixMap) {
  EResult eReturn = RET_INVALIDPARAM;

  int32_t iWidth     = pSrcPixMap->sRect.iRectWidth;
  int32_t iHeight    = pSrcPixMap->sRect.iRectHeight;
  int32_t iMbWidth  = iWidth  >> 4;
  int32_t iMbHeight = iHeight >> 4;
  int32_t iMbTotalNum    = iMbWidth * iMbHeight;

  SMotionTextureUnit* pMotionTexture = NULL;
  SVAACalcResult*     pVaaCalcResults = NULL;
  int32_t   iMotionTextureIndexToDeltaQp = 0;
  int32_t iAverMotionTextureIndexToDeltaQp = 0;  // double to uint32
  int64_t iAverageMotionIndex = 0;      // double to float
  int64_t iAverageTextureIndex = 0;

  int64_t iQStep = 0;
  int64_t iLumaMotionDeltaQp = 0;
  int64_t iLumaTextureDeltaQp = 0;

  uint8_t* pRefFrameY = NULL, *pCurFrameY = NULL;
  int32_t iRefStride = 0, iCurStride = 0;

  uint8_t* pRefFrameTmp = NULL, *pCurFrameTmp = NULL;
  int32_t i = 0, j = 0;

  pRefFrameY = (uint8_t*)pRefPixMap->pPixel[0];
  pCurFrameY = (uint8_t*)pSrcPixMap->pPixel[0];

  iRefStride  = pRefPixMap->iStride[0];
  iCurStride  = pSrcPixMap->iStride[0];

  /////////////////////////////////////// motion //////////////////////////////////
  //  motion MB residual variance
  iAverageMotionIndex = 0;
  iAverageTextureIndex = 0;
  pMotionTexture = m_sAdaptiveQuantParam.pMotionTextureUnit;
  pVaaCalcResults = m_sAdaptiveQuantParam.pCalcResult;

  if (pVaaCalcResults->pRefY == pRefFrameY && pVaaCalcResults->pCurY == pCurFrameY) {
    int32_t iMbIndex = 0;
    int32_t iSumDiff, iSQDiff, uiSum, iSQSum;
    for (j = 0; j < iMbHeight; j ++) {
      pRefFrameTmp  = pRefFrameY;
      pCurFrameTmp  = pCurFrameY;
      for (i = 0; i < iMbWidth; i++) {
        iSumDiff =  pVaaCalcResults->pSad8x8[iMbIndex][0];
        iSumDiff += pVaaCalcResults->pSad8x8[iMbIndex][1];
        iSumDiff += pVaaCalcResults->pSad8x8[iMbIndex][2];
        iSumDiff += pVaaCalcResults->pSad8x8[iMbIndex][3];

        iSQDiff = pVaaCalcResults->pSsd16x16[iMbIndex];
        uiSum = pVaaCalcResults->pSum16x16[iMbIndex];
        iSQSum = pVaaCalcResults->pSumOfSquare16x16[iMbIndex];

        iSumDiff = iSumDiff >> 8;
        pMotionTexture->uiMotionIndex = (iSQDiff >> 8) - (iSumDiff * iSumDiff);

        uiSum = uiSum >> 8;
        pMotionTexture->uiTextureIndex = (iSQSum >> 8) - (uiSum * uiSum);

        iAverageMotionIndex += pMotionTexture->uiMotionIndex;
        iAverageTextureIndex += pMotionTexture->uiTextureIndex;
        pMotionTexture++;
        ++iMbIndex;
        pRefFrameTmp += MB_WIDTH_LUMA;
        pCurFrameTmp += MB_WIDTH_LUMA;
      }
      pRefFrameY += (iRefStride) << 4;
      pCurFrameY += (iCurStride) << 4;
    }
  } else {
    for (j = 0; j < iMbHeight; j ++) {
      pRefFrameTmp  = pRefFrameY;
      pCurFrameTmp  = pCurFrameY;
      for (i = 0; i < iMbWidth; i++) {
        m_pfVar (pRefFrameTmp, iRefStride, pCurFrameTmp, iCurStride, pMotionTexture);
        iAverageMotionIndex += pMotionTexture->uiMotionIndex;
        iAverageTextureIndex += pMotionTexture->uiTextureIndex;
        pMotionTexture++;
        pRefFrameTmp += MB_WIDTH_LUMA;
        pCurFrameTmp += MB_WIDTH_LUMA;

      }
      pRefFrameY += (iRefStride) << 4;
      pCurFrameY += (iCurStride) << 4;
    }
  }
  iAverageMotionIndex = WELS_DIV_ROUND64 (iAverageMotionIndex * AQ_INT_MULTIPLY, iMbTotalNum);
  iAverageTextureIndex = WELS_DIV_ROUND64 (iAverageTextureIndex * AQ_INT_MULTIPLY, iMbTotalNum);
  if ((iAverageMotionIndex <= AQ_PESN) && (iAverageMotionIndex >= -AQ_PESN)) {
    iAverageMotionIndex = AQ_INT_MULTIPLY;
  }
  if ((iAverageTextureIndex <= AQ_PESN) && (iAverageTextureIndex >= -AQ_PESN)) {
    iAverageTextureIndex = AQ_INT_MULTIPLY;
  }
  //  motion mb residual map to QP
  //  texture mb original map to QP
  iAverMotionTextureIndexToDeltaQp = 0;
  iAverageMotionIndex = WELS_DIV_ROUND64 (AVERAGE_TIME_MOTION * iAverageMotionIndex, AQ_TIME_INT_MULTIPLY);

  if (m_sAdaptiveQuantParam.iAdaptiveQuantMode == AQ_QUALITY_MODE) {
    iAverageTextureIndex = WELS_DIV_ROUND64 (AVERAGE_TIME_TEXTURE_QUALITYMODE * iAverageTextureIndex, AQ_TIME_INT_MULTIPLY);
  } else {
    iAverageTextureIndex = WELS_DIV_ROUND64 (AVERAGE_TIME_TEXTURE_BITRATEMODE * iAverageTextureIndex, AQ_TIME_INT_MULTIPLY);
  }

  int64_t iAQ_EPSN = - ((int64_t)AQ_PESN * AQ_TIME_INT_MULTIPLY * AQ_QSTEP_INT_MULTIPLY / AQ_INT_MULTIPLY);
  pMotionTexture = m_sAdaptiveQuantParam.pMotionTextureUnit;
  for (j = 0; j < iMbHeight; j ++) {
    for (i = 0; i < iMbWidth; i++) {
      int64_t a = WELS_DIV_ROUND64 ((int64_t) (pMotionTexture->uiTextureIndex) * AQ_INT_MULTIPLY * AQ_TIME_INT_MULTIPLY,
                                    iAverageTextureIndex);
      iQStep = WELS_DIV_ROUND64 ((a - AQ_TIME_INT_MULTIPLY) * AQ_QSTEP_INT_MULTIPLY, (a + MODEL_ALPHA));
      iLumaTextureDeltaQp = MODEL_TIME * iQStep;// range +- 6

      iMotionTextureIndexToDeltaQp = ((int32_t) (iLumaTextureDeltaQp / (AQ_TIME_INT_MULTIPLY)));

      a = WELS_DIV_ROUND64 (((int64_t)pMotionTexture->uiMotionIndex) * AQ_INT_MULTIPLY * AQ_TIME_INT_MULTIPLY,
                            iAverageMotionIndex);
      iQStep = WELS_DIV_ROUND64 ((a - AQ_TIME_INT_MULTIPLY) * AQ_QSTEP_INT_MULTIPLY, (a + MODEL_ALPHA));
      iLumaMotionDeltaQp = MODEL_TIME * iQStep;// range +- 6

      if ((m_sAdaptiveQuantParam.iAdaptiveQuantMode == AQ_QUALITY_MODE && iLumaMotionDeltaQp < iAQ_EPSN)
          || (m_sAdaptiveQuantParam.iAdaptiveQuantMode == AQ_BITRATE_MODE)) {
        iMotionTextureIndexToDeltaQp += ((int32_t) (iLumaMotionDeltaQp / (AQ_TIME_INT_MULTIPLY)));
      }

      m_sAdaptiveQuantParam.pMotionTextureIndexToDeltaQp[j * iMbWidth + i] = (int8_t) (iMotionTextureIndexToDeltaQp /
          AQ_QSTEP_INT_MULTIPLY);
      iAverMotionTextureIndexToDeltaQp += iMotionTextureIndexToDeltaQp;
      pMotionTexture++;
    }
  }

  m_sAdaptiveQuantParam.iAverMotionTextureIndexToDeltaQp = iAverMotionTextureIndexToDeltaQp / iMbTotalNum;

  eReturn = RET_SUCCESS;

  return eReturn;
}



EResult CAdaptiveQuantization::Set (int32_t iType, void* pParam) {
  if (pParam == NULL) {
    return RET_INVALIDPARAM;
  }

  m_sAdaptiveQuantParam = * (SAdaptiveQuantizationParam*)pParam;

  return RET_SUCCESS;
}

EResult CAdaptiveQuantization::Get (int32_t iType, void* pParam) {
  if (pParam == NULL) {
    return RET_INVALIDPARAM;
  }

  SAdaptiveQuantizationParam* sAdaptiveQuantParam = (SAdaptiveQuantizationParam*)pParam;

  sAdaptiveQuantParam->iAverMotionTextureIndexToDeltaQp = m_sAdaptiveQuantParam.iAverMotionTextureIndexToDeltaQp;

  return RET_SUCCESS;
}

///////////////////////////////////////////////////////////////////////////////////////////////

void CAdaptiveQuantization::WelsInitVarFunc (PVarFunc& pfVar,  int32_t iCpuFlag) {
  pfVar = SampleVariance16x16_c;

#ifdef X86_ASM
  if (iCpuFlag & WELS_CPU_SSE2) {
    pfVar = SampleVariance16x16_sse2;
  }
#endif
#ifdef HAVE_NEON
  if (iCpuFlag & WELS_CPU_NEON) {
    pfVar = SampleVariance16x16_neon;
  }
#endif
#ifdef HAVE_NEON_AARCH64
  if (iCpuFlag & WELS_CPU_NEON) {
    pfVar = SampleVariance16x16_AArch64_neon;
  }
#endif
}

void SampleVariance16x16_c (uint8_t* pRefY, int32_t iRefStride, uint8_t* pSrcY, int32_t iSrcStride,
                            SMotionTextureUnit* pMotionTexture) {
  uint32_t uiCurSquare = 0,  uiSquare = 0;
  uint16_t uiCurSum = 0,  uiSum = 0;

  for (int32_t y = 0; y < MB_WIDTH_LUMA; y++) {
    for (int32_t x = 0; x < MB_WIDTH_LUMA; x++) {
      uint32_t uiDiff = WELS_ABS (pRefY[x] - pSrcY[x]);
      uiSum += uiDiff;
      uiSquare += uiDiff * uiDiff;

      uiCurSum += pSrcY[x];
      uiCurSquare += pSrcY[x] * pSrcY[x];
    }
    pRefY += iRefStride;
    pSrcY += iSrcStride;
  }

  uiSum = uiSum >> 8;
  pMotionTexture->uiMotionIndex = (uiSquare >> 8) - (uiSum * uiSum);

  uiCurSum = uiCurSum >> 8;
  pMotionTexture->uiTextureIndex = (uiCurSquare >> 8) - (uiCurSum * uiCurSum);
}

WELSVP_NAMESPACE_END
