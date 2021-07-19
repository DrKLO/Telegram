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

#include "downsample.h"
#include "cpu.h"
#include <assert.h>

WELSVP_NAMESPACE_BEGIN
#define MAX_SAMPLE_WIDTH 1920
#define MAX_SAMPLE_HEIGHT 1088

///////////////////////////////////////////////////////////////////////////////////////////////////////////////

CDownsampling::CDownsampling (int32_t iCpuFlag) {
  m_iCPUFlag = iCpuFlag;
  m_eMethod   = METHOD_DOWNSAMPLE;
  WelsMemset (&m_pfDownsample, 0, sizeof (m_pfDownsample));
  InitDownsampleFuncs (m_pfDownsample, m_iCPUFlag);
  WelsMemset(m_pSampleBuffer,0,sizeof(m_pSampleBuffer));
  m_bNoSampleBuffer = AllocateSampleBuffer();
}

CDownsampling::~CDownsampling() {
  FreeSampleBuffer();
}
bool CDownsampling::AllocateSampleBuffer() {
  for (int32_t i = 0; i < 2; i++) {
    m_pSampleBuffer[i][0] = (uint8_t*)WelsMalloc (MAX_SAMPLE_WIDTH * MAX_SAMPLE_HEIGHT);
    if (!m_pSampleBuffer[i][0])
      goto FREE_RET;
    m_pSampleBuffer[i][1] = (uint8_t*)WelsMalloc (MAX_SAMPLE_WIDTH * MAX_SAMPLE_HEIGHT / 4);
    if (!m_pSampleBuffer[i][1])
      goto FREE_RET;
    m_pSampleBuffer[i][2] = (uint8_t*)WelsMalloc (MAX_SAMPLE_WIDTH * MAX_SAMPLE_HEIGHT / 4);
    if (!m_pSampleBuffer[i][2])
      goto FREE_RET;
  }
  return false;
FREE_RET:
  FreeSampleBuffer();
  return true;

}
void CDownsampling::FreeSampleBuffer() {
  for (int32_t i = 0; i < 2; i++) {
    WelsFree (m_pSampleBuffer[i][0]);
    m_pSampleBuffer[i][0] = NULL;
    WelsFree (m_pSampleBuffer[i][1]);
    m_pSampleBuffer[i][1] = NULL;
    WelsFree (m_pSampleBuffer[i][2]);
    m_pSampleBuffer[i][2] = NULL;
  }
}

void CDownsampling::InitDownsampleFuncs (SDownsampleFuncs& sDownsampleFunc,  int32_t iCpuFlag) {
  sDownsampleFunc.pfHalfAverageWidthx32 = DyadicBilinearDownsampler_c;
  sDownsampleFunc.pfHalfAverageWidthx16 = DyadicBilinearDownsampler_c;
  sDownsampleFunc.pfOneThirdDownsampler = DyadicBilinearOneThirdDownsampler_c;
  sDownsampleFunc.pfQuarterDownsampler  = DyadicBilinearQuarterDownsampler_c;
  sDownsampleFunc.pfGeneralRatioChroma  = GeneralBilinearAccurateDownsampler_c;
  sDownsampleFunc.pfGeneralRatioLuma    = GeneralBilinearFastDownsampler_c;
#if defined(X86_ASM)
  if (iCpuFlag & WELS_CPU_SSE) {
    sDownsampleFunc.pfHalfAverageWidthx32 = DyadicBilinearDownsamplerWidthx32_sse;
    sDownsampleFunc.pfHalfAverageWidthx16 = DyadicBilinearDownsamplerWidthx16_sse;
    sDownsampleFunc.pfQuarterDownsampler = DyadicBilinearQuarterDownsampler_sse;
  }
  if (iCpuFlag & WELS_CPU_SSE2) {
    sDownsampleFunc.pfGeneralRatioChroma = GeneralBilinearAccurateDownsamplerWrap_sse2;
    sDownsampleFunc.pfGeneralRatioLuma   = GeneralBilinearFastDownsamplerWrap_sse2;
  }
  if (iCpuFlag & WELS_CPU_SSSE3) {
    sDownsampleFunc.pfHalfAverageWidthx32 = DyadicBilinearDownsamplerWidthx32_ssse3;
    sDownsampleFunc.pfHalfAverageWidthx16 = DyadicBilinearDownsamplerWidthx16_ssse3;
    sDownsampleFunc.pfOneThirdDownsampler = DyadicBilinearOneThirdDownsampler_ssse3;
    sDownsampleFunc.pfQuarterDownsampler  = DyadicBilinearQuarterDownsampler_ssse3;
    sDownsampleFunc.pfGeneralRatioLuma    = GeneralBilinearFastDownsamplerWrap_ssse3;
  }
  if (iCpuFlag & WELS_CPU_SSE41) {
    sDownsampleFunc.pfOneThirdDownsampler = DyadicBilinearOneThirdDownsampler_sse4;
    sDownsampleFunc.pfQuarterDownsampler  = DyadicBilinearQuarterDownsampler_sse4;
    sDownsampleFunc.pfGeneralRatioChroma  = GeneralBilinearAccurateDownsamplerWrap_sse41;
  }
#ifdef HAVE_AVX2
  if (iCpuFlag & WELS_CPU_AVX2) {
    sDownsampleFunc.pfGeneralRatioChroma = GeneralBilinearAccurateDownsamplerWrap_avx2;
    sDownsampleFunc.pfGeneralRatioLuma   = GeneralBilinearFastDownsamplerWrap_avx2;
  }
#endif
#endif//X86_ASM

#if defined(HAVE_NEON)
  if (iCpuFlag & WELS_CPU_NEON) {
    sDownsampleFunc.pfHalfAverageWidthx32 = DyadicBilinearDownsamplerWidthx32_neon;
    sDownsampleFunc.pfHalfAverageWidthx16 = DyadicBilinearDownsampler_neon;
    sDownsampleFunc.pfOneThirdDownsampler = DyadicBilinearOneThirdDownsampler_neon;
    sDownsampleFunc.pfQuarterDownsampler  = DyadicBilinearQuarterDownsampler_neon;
    sDownsampleFunc.pfGeneralRatioChroma = GeneralBilinearAccurateDownsamplerWrap_neon;
    sDownsampleFunc.pfGeneralRatioLuma   = GeneralBilinearAccurateDownsamplerWrap_neon;
  }
#endif

#if defined(HAVE_NEON_AARCH64)
  if (iCpuFlag & WELS_CPU_NEON) {
    sDownsampleFunc.pfHalfAverageWidthx32 = DyadicBilinearDownsamplerWidthx32_AArch64_neon;
    sDownsampleFunc.pfHalfAverageWidthx16 = DyadicBilinearDownsampler_AArch64_neon;
    sDownsampleFunc.pfOneThirdDownsampler = DyadicBilinearOneThirdDownsampler_AArch64_neon;
    sDownsampleFunc.pfQuarterDownsampler  = DyadicBilinearQuarterDownsampler_AArch64_neon;
    sDownsampleFunc.pfGeneralRatioChroma = GeneralBilinearAccurateDownsamplerWrap_AArch64_neon;
    sDownsampleFunc.pfGeneralRatioLuma   = GeneralBilinearAccurateDownsamplerWrap_AArch64_neon;
  }
#endif
}

EResult CDownsampling::Process (int32_t iType, SPixMap* pSrcPixMap, SPixMap* pDstPixMap) {
  int32_t iSrcWidthY = pSrcPixMap->sRect.iRectWidth;
  int32_t iSrcHeightY = pSrcPixMap->sRect.iRectHeight;
  int32_t iDstWidthY = pDstPixMap->sRect.iRectWidth;
  int32_t iDstHeightY = pDstPixMap->sRect.iRectHeight;

  int32_t iSrcWidthUV = iSrcWidthY >> 1;
  int32_t iSrcHeightUV = iSrcHeightY >> 1;
  int32_t iDstWidthUV = iDstWidthY >> 1;
  int32_t iDstHeightUV = iDstHeightY >> 1;

  if (iSrcWidthY <= iDstWidthY || iSrcHeightY <= iDstHeightY) {
    return RET_INVALIDPARAM;
  }
  if ((iSrcWidthY >> 1) > MAX_SAMPLE_WIDTH || (iSrcHeightY >> 1) > MAX_SAMPLE_HEIGHT || m_bNoSampleBuffer) {
    if ((iSrcWidthY >> 1) == iDstWidthY && (iSrcHeightY >> 1) == iDstHeightY) {
      // use half average functions
      DownsampleHalfAverage ((uint8_t*)pDstPixMap->pPixel[0], pDstPixMap->iStride[0],
          (uint8_t*)pSrcPixMap->pPixel[0], pSrcPixMap->iStride[0], iSrcWidthY, iSrcHeightY);
      DownsampleHalfAverage ((uint8_t*)pDstPixMap->pPixel[1], pDstPixMap->iStride[1],
          (uint8_t*)pSrcPixMap->pPixel[1], pSrcPixMap->iStride[1], iSrcWidthUV, iSrcHeightUV);
      DownsampleHalfAverage ((uint8_t*)pDstPixMap->pPixel[2], pDstPixMap->iStride[2],
          (uint8_t*)pSrcPixMap->pPixel[2], pSrcPixMap->iStride[2], iSrcWidthUV, iSrcHeightUV);
    } else if ((iSrcWidthY >> 2) == iDstWidthY && (iSrcHeightY >> 2) == iDstHeightY) {

      m_pfDownsample.pfQuarterDownsampler ((uint8_t*)pDstPixMap->pPixel[0], pDstPixMap->iStride[0],
                                           (uint8_t*)pSrcPixMap->pPixel[0], pSrcPixMap->iStride[0], iSrcWidthY, iSrcHeightY);

      m_pfDownsample.pfQuarterDownsampler ((uint8_t*)pDstPixMap->pPixel[1], pDstPixMap->iStride[1],
                                           (uint8_t*)pSrcPixMap->pPixel[1], pSrcPixMap->iStride[1], iSrcWidthUV, iSrcHeightUV);

      m_pfDownsample.pfQuarterDownsampler ((uint8_t*)pDstPixMap->pPixel[2], pDstPixMap->iStride[2],
                                           (uint8_t*)pSrcPixMap->pPixel[2], pSrcPixMap->iStride[2], iSrcWidthUV, iSrcHeightUV);

    } else if ((iSrcWidthY / 3) == iDstWidthY && (iSrcHeightY / 3) == iDstHeightY) {

      m_pfDownsample.pfOneThirdDownsampler ((uint8_t*)pDstPixMap->pPixel[0], pDstPixMap->iStride[0],
                                            (uint8_t*)pSrcPixMap->pPixel[0], pSrcPixMap->iStride[0], iSrcWidthY, iDstHeightY);

      m_pfDownsample.pfOneThirdDownsampler ((uint8_t*)pDstPixMap->pPixel[1], pDstPixMap->iStride[1],
                                            (uint8_t*)pSrcPixMap->pPixel[1], pSrcPixMap->iStride[1], iSrcWidthUV, iDstHeightUV);

      m_pfDownsample.pfOneThirdDownsampler ((uint8_t*)pDstPixMap->pPixel[2], pDstPixMap->iStride[2],
                                            (uint8_t*)pSrcPixMap->pPixel[2], pSrcPixMap->iStride[2], iSrcWidthUV, iDstHeightUV);

    } else {
      m_pfDownsample.pfGeneralRatioLuma ((uint8_t*)pDstPixMap->pPixel[0], pDstPixMap->iStride[0], iDstWidthY, iDstHeightY,
                                         (uint8_t*)pSrcPixMap->pPixel[0], pSrcPixMap->iStride[0], iSrcWidthY, iSrcHeightY);

      m_pfDownsample.pfGeneralRatioChroma ((uint8_t*)pDstPixMap->pPixel[1], pDstPixMap->iStride[1], iDstWidthUV, iDstHeightUV,
                                           (uint8_t*)pSrcPixMap->pPixel[1], pSrcPixMap->iStride[1], iSrcWidthUV, iSrcHeightUV);

      m_pfDownsample.pfGeneralRatioChroma ((uint8_t*)pDstPixMap->pPixel[2], pDstPixMap->iStride[2], iDstWidthUV, iDstHeightUV,
                                           (uint8_t*)pSrcPixMap->pPixel[2], pSrcPixMap->iStride[2], iSrcWidthUV, iSrcHeightUV);
    }
  } else {

    int32_t iIdx = 0;
    int32_t iHalfSrcWidth = iSrcWidthY >> 1;
    int32_t iHalfSrcHeight = iSrcHeightY >> 1;
    uint8_t* pSrcY = (uint8_t*)pSrcPixMap->pPixel[0];
    uint8_t* pSrcU = (uint8_t*)pSrcPixMap->pPixel[1];
    uint8_t* pSrcV = (uint8_t*)pSrcPixMap->pPixel[2];
    int32_t iSrcStrideY = pSrcPixMap->iStride[0];
    int32_t iSrcStrideU = pSrcPixMap->iStride[1];
    int32_t iSrcStrideV = pSrcPixMap->iStride[2];

    int32_t iDstStrideY = pDstPixMap->iStride[0];
    int32_t iDstStrideU = pDstPixMap->iStride[1];
    int32_t iDstStrideV = pDstPixMap->iStride[2];

    uint8_t* pDstY = (uint8_t*)m_pSampleBuffer[iIdx][0];
    uint8_t* pDstU = (uint8_t*)m_pSampleBuffer[iIdx][1];
    uint8_t* pDstV = (uint8_t*)m_pSampleBuffer[iIdx][2];
    iIdx++;
    do {
      if ((iHalfSrcWidth == iDstWidthY) && (iHalfSrcHeight == iDstHeightY)) { //end
        // use half average functions
        DownsampleHalfAverage ((uint8_t*)pDstPixMap->pPixel[0], pDstPixMap->iStride[0],
            (uint8_t*)pSrcY, iSrcStrideY, iSrcWidthY, iSrcHeightY);
        DownsampleHalfAverage ((uint8_t*)pDstPixMap->pPixel[1], pDstPixMap->iStride[1],
            (uint8_t*)pSrcU, iSrcStrideU, iSrcWidthUV, iSrcHeightUV);
        DownsampleHalfAverage ((uint8_t*)pDstPixMap->pPixel[2], pDstPixMap->iStride[2],
            (uint8_t*)pSrcV, iSrcStrideV, iSrcWidthUV, iSrcHeightUV);
        break;
      } else if ((iHalfSrcWidth > iDstWidthY) && (iHalfSrcHeight > iDstHeightY)){
        // use half average functions
        iDstStrideY = WELS_ALIGN (iHalfSrcWidth, 32);
        iDstStrideU = WELS_ALIGN (iHalfSrcWidth >> 1, 32);
        iDstStrideV = WELS_ALIGN (iHalfSrcWidth >> 1, 32);
        DownsampleHalfAverage ((uint8_t*)pDstY, iDstStrideY,
            (uint8_t*)pSrcY, iSrcStrideY, iSrcWidthY, iSrcHeightY);
        DownsampleHalfAverage ((uint8_t*)pDstU, iDstStrideU,
            (uint8_t*)pSrcU, iSrcStrideU, iSrcWidthUV, iSrcHeightUV);
        DownsampleHalfAverage ((uint8_t*)pDstV, iDstStrideV,
            (uint8_t*)pSrcV, iSrcStrideV, iSrcWidthUV, iSrcHeightUV);

        pSrcY = (uint8_t*)pDstY;
        pSrcU = (uint8_t*)pDstU;
        pSrcV = (uint8_t*)pDstV;


        iSrcWidthY = iHalfSrcWidth;
        iSrcWidthUV = iHalfSrcWidth >> 1;
        iSrcHeightY = iHalfSrcHeight;
        iSrcHeightUV = iHalfSrcHeight >> 1;

        iSrcStrideY = iDstStrideY;
        iSrcStrideU = iDstStrideU;
        iSrcStrideV = iDstStrideV;

        iHalfSrcWidth >>= 1;
        iHalfSrcHeight >>= 1;

        iIdx = iIdx % 2;
        pDstY = (uint8_t*)m_pSampleBuffer[iIdx][0];
        pDstU = (uint8_t*)m_pSampleBuffer[iIdx][1];
        pDstV = (uint8_t*)m_pSampleBuffer[iIdx][2];
        iIdx++;
      } else {
        m_pfDownsample.pfGeneralRatioLuma ((uint8_t*)pDstPixMap->pPixel[0], pDstPixMap->iStride[0], iDstWidthY, iDstHeightY,
                                           (uint8_t*)pSrcY, iSrcStrideY, iSrcWidthY, iSrcHeightY);

        m_pfDownsample.pfGeneralRatioChroma ((uint8_t*)pDstPixMap->pPixel[1], pDstPixMap->iStride[1], iDstWidthUV, iDstHeightUV,
                                             (uint8_t*)pSrcU, iSrcStrideU,  iSrcWidthUV, iSrcHeightUV);

        m_pfDownsample.pfGeneralRatioChroma ((uint8_t*)pDstPixMap->pPixel[2], pDstPixMap->iStride[2], iDstWidthUV, iDstHeightUV,
                                             (uint8_t*)pSrcV, iSrcStrideV, iSrcWidthUV, iSrcHeightUV);
        break;
      }
    } while (true);
  }
  return RET_SUCCESS;
}

void CDownsampling::DownsampleHalfAverage (uint8_t* pDst, int32_t iDstStride,
        uint8_t* pSrc, int32_t iSrcStride, int32_t iSrcWidth, int32_t iSrcHeight) {
  if ((iSrcStride & 31) == 0) {
    assert ((iDstStride & 15) == 0);
    m_pfDownsample.pfHalfAverageWidthx32 (pDst, iDstStride,
        pSrc, iSrcStride, WELS_ALIGN (iSrcWidth & ~1, 32), iSrcHeight);
  } else {
    assert ((iSrcStride & 15) == 0);
    assert ((iDstStride &  7) == 0);
    m_pfDownsample.pfHalfAverageWidthx16 (pDst, iDstStride,
        pSrc, iSrcStride, WELS_ALIGN (iSrcWidth & ~1, 16), iSrcHeight);
  }
}


WELSVP_NAMESPACE_END
