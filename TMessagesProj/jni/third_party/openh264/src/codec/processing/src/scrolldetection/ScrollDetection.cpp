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
 */


#include "ScrollDetection.h"
#include "ScrollDetectionFuncs.h"
#include "cpu.h"

WELSVP_NAMESPACE_BEGIN

EResult CScrollDetection::Process (int32_t iType, SPixMap* pSrcPixMap, SPixMap* pRefPixMap) {
  if (pRefPixMap->pPixel[0] == NULL || pSrcPixMap->pPixel[0] == NULL ||
      pRefPixMap->sRect.iRectWidth != pSrcPixMap->sRect.iRectWidth
      || pRefPixMap->sRect.iRectHeight != pSrcPixMap->sRect.iRectHeight) {
    return RET_INVALIDPARAM;
  }

  if (!m_sScrollDetectionParam.bMaskInfoAvailable)
    ScrollDetectionWithoutMask (pSrcPixMap, pRefPixMap);
  else
    ScrollDetectionWithMask (pSrcPixMap, pRefPixMap);

  return RET_SUCCESS;
}

EResult CScrollDetection::Set (int32_t iType, void* pParam) {
  if (pParam == NULL) {
    return RET_INVALIDPARAM;
  }
  m_sScrollDetectionParam = * ((SScrollDetectionParam*)pParam);
  return RET_SUCCESS;
}

EResult CScrollDetection::Get (int32_t iType, void* pParam) {
  if (pParam == NULL) {
    return RET_INVALIDPARAM;
  }
  * ((SScrollDetectionParam*)pParam) = m_sScrollDetectionParam;
  return RET_SUCCESS;
}

void CScrollDetection::ScrollDetectionWithMask (SPixMap* pSrcPixMap, SPixMap* pRefPixMap) {
  int32_t iStartX, iStartY, iWidth, iHeight;

  iStartX = m_sScrollDetectionParam.sMaskRect.iRectLeft;
  iStartY = m_sScrollDetectionParam.sMaskRect.iRectTop;
  iWidth = m_sScrollDetectionParam.sMaskRect.iRectWidth;
  iHeight = m_sScrollDetectionParam.sMaskRect.iRectHeight;

  iWidth /= 2;
  iStartX += iWidth / 2;

  m_sScrollDetectionParam.iScrollMvX = 0;
  m_sScrollDetectionParam.iScrollMvY = 0;
  m_sScrollDetectionParam.bScrollDetectFlag = false;

  if (iStartX >= 0 && iWidth > MINIMUM_DETECT_WIDTH && iHeight > 2 * CHECK_OFFSET) {
    ScrollDetectionCore (pSrcPixMap, pRefPixMap, iWidth, iHeight, iStartX, iStartY, m_sScrollDetectionParam);
  }
}

void CScrollDetection::ScrollDetectionWithoutMask (SPixMap* pSrcPixMap, SPixMap* pRefPixMap) {
  int32_t iStartX, iStartY, iWidth, iHeight;

  const int32_t kiPicBorderWidth = pSrcPixMap->sRect.iRectHeight >> 4;
  const int32_t kiRegionWidth = (int) (pSrcPixMap->sRect.iRectWidth - (kiPicBorderWidth << 1)) / 3;
  const int32_t kiRegionHeight = (pSrcPixMap->sRect.iRectHeight * 7) >> 3;
  const int32_t kiHieghtStride = (int) pSrcPixMap->sRect.iRectHeight * 5 / 24;

  for (int32_t i = 0; i < REGION_NUMBER; i++) {
    iStartX = kiPicBorderWidth + (i % 3) * kiRegionWidth;
    iStartY = -pSrcPixMap->sRect.iRectHeight * 7 / 48 + (int) (i / 3) * (kiHieghtStride);
    iWidth = kiRegionWidth;
    iHeight = kiRegionHeight;

    iWidth /= 2;
    iStartX += iWidth / 2;

    ScrollDetectionCore (pSrcPixMap, pRefPixMap, iWidth, iHeight, iStartX, iStartY, m_sScrollDetectionParam);

    if (m_sScrollDetectionParam.bScrollDetectFlag && m_sScrollDetectionParam.iScrollMvY)
      break;
  }
}

WELSVP_NAMESPACE_END
