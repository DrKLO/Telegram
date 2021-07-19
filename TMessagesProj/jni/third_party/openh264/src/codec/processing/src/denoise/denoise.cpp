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

#include "denoise.h"
#include "cpu.h"

WELSVP_NAMESPACE_BEGIN

#define CALC_BI_STRIDE(iWidth, iBitcount)  ((((iWidth) * (iBitcount) + 31) & ~31) >> 3)

///////////////////////////////////////////////////////////////////////////////////////////////////////////////

CDenoiser::CDenoiser (int32_t iCpuFlag) {
  m_CPUFlag = iCpuFlag;
  m_eMethod   = METHOD_DENOISE;
  WelsMemset (&m_pfDenoise, 0, sizeof (m_pfDenoise));

  m_uiSpaceRadius = DENOISE_GRAY_RADIUS;
  m_fSigmaGrey  = DENOISE_GRAY_SIGMA;
  m_uiType      = DENOISE_ALL_COMPONENT;
  InitDenoiseFunc (m_pfDenoise, m_CPUFlag);
}

CDenoiser::~CDenoiser() {
}

void CDenoiser::InitDenoiseFunc (SDenoiseFuncs& denoiser,  int32_t iCpuFlag) {
  denoiser.pfBilateralLumaFilter8 = BilateralLumaFilter8_c;
  denoiser.pfWaverageChromaFilter8 = WaverageChromaFilter8_c;
#if defined(X86_ASM)
  if (iCpuFlag & WELS_CPU_SSE2) {
    denoiser.pfBilateralLumaFilter8 = BilateralLumaFilter8_sse2;
    denoiser.pfWaverageChromaFilter8 = WaverageChromaFilter8_sse2;
  }
#endif
}

EResult CDenoiser::Process (int32_t iType, SPixMap* pSrc, SPixMap* dst) {
  uint8_t* pSrcY = (uint8_t*)pSrc->pPixel[0];
  uint8_t* pSrcU = (uint8_t*)pSrc->pPixel[1];
  uint8_t* pSrcV = (uint8_t*)pSrc->pPixel[2];
  if (pSrcY == NULL || pSrcU == NULL || pSrcV == NULL) {
    return RET_INVALIDPARAM;
  }

  int32_t iWidthY = pSrc->sRect.iRectWidth;
  int32_t iHeightY = pSrc->sRect.iRectHeight;
  int32_t iWidthUV = iWidthY >> 1;
  int32_t iHeightUV = iHeightY >> 1;

  if (m_uiType & DENOISE_Y_COMPONENT)
    BilateralDenoiseLuma (pSrcY, iWidthY, iHeightY, pSrc->iStride[0]);

  if (m_uiType & DENOISE_U_COMPONENT)
    WaverageDenoiseChroma (pSrcU, iWidthUV, iHeightUV, pSrc->iStride[1]);

  if (m_uiType & DENOISE_V_COMPONENT)
    WaverageDenoiseChroma (pSrcV, iWidthUV, iHeightUV, pSrc->iStride[2]);

  return RET_SUCCESS;
}

void CDenoiser::BilateralDenoiseLuma (uint8_t* pSrcY, int32_t iWidth, int32_t iHeight, int32_t iStride) {
  int32_t w;

  pSrcY = pSrcY + m_uiSpaceRadius * iStride;
  for (int32_t h = m_uiSpaceRadius; h < iHeight - m_uiSpaceRadius; h++) {
    for (w = m_uiSpaceRadius; w < iWidth - m_uiSpaceRadius - TAIL_OF_LINE8; w += 8) {
      m_pfDenoise.pfBilateralLumaFilter8 (pSrcY + w, iStride);
    }
    for (; w < iWidth - m_uiSpaceRadius; w++) {
      Gauss3x3Filter (pSrcY + w, iStride);
    }
    pSrcY += iStride;
  }
}

void CDenoiser::WaverageDenoiseChroma (uint8_t* pSrcUV, int32_t iWidth, int32_t iHeight, int32_t iStride) {
  int32_t w;

  pSrcUV = pSrcUV + UV_WINDOWS_RADIUS * iStride;
  for (int32_t h = UV_WINDOWS_RADIUS; h < iHeight - UV_WINDOWS_RADIUS; h++) {
    for (w = UV_WINDOWS_RADIUS; w < iWidth - UV_WINDOWS_RADIUS - TAIL_OF_LINE8; w += 8) {
      m_pfDenoise.pfWaverageChromaFilter8 (pSrcUV + w, iStride);
    }

    for (; w < iWidth - UV_WINDOWS_RADIUS; w++) {
      Gauss3x3Filter (pSrcUV + w, iStride);
    }
    pSrcUV += iStride;
  }
}


WELSVP_NAMESPACE_END
