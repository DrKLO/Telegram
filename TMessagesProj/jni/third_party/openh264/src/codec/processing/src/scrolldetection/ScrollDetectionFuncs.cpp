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
#include "ls_defines.h"

WELSVP_NAMESPACE_BEGIN

int32_t CheckLine (uint8_t* pData, int32_t iWidth) {
  int32_t iQualified = 0;
  int32_t iColorMap[8] = {0};
  int32_t iChangedTimes = 0;
  int32_t iColorCounts = 0;

  RECORD_COLOR (pData[0], iColorMap);

  for (int32_t i = 1; i < iWidth; i++) {
    RECORD_COLOR (pData[i], iColorMap);
    iChangedTimes += (pData[i] != pData[i - 1]);
  }
  for (int32_t i = 0; i < 8; i++)
    for (int32_t j = 0; j < 32; j++)
      iColorCounts += ((iColorMap[i] >> j) & 1);

  switch (iColorCounts) {
  case 1:
    iQualified = 0;
    break;
  case 2:
  case 3:
    iQualified = (iChangedTimes > 3);
    break;
  default:
    iQualified = 1;
    break;
  }
  return iQualified;
}

int32_t SelectTestLine (uint8_t* pY, int32_t iWidth, int32_t iHeight, int32_t iPicHeight,
                        int32_t iStride, int32_t iOffsetX, int32_t iOffsetY) {
  const int32_t kiHalfHeight    = iHeight >> 1;
  const int32_t kiMidPos        = iOffsetY + kiHalfHeight;
  int32_t TestPos               = kiMidPos;
  int32_t iOffsetAbs;
  uint8_t* pTmp;

  for (iOffsetAbs = 0; iOffsetAbs < kiHalfHeight; iOffsetAbs++) {
    TestPos = kiMidPos + iOffsetAbs;
    if (TestPos < iPicHeight) {
      pTmp = pY + TestPos * iStride + iOffsetX;
      if (CheckLine (pTmp, iWidth)) break;
    }
    TestPos = kiMidPos - iOffsetAbs;
    if (TestPos >= 0) {
      pTmp = pY + TestPos * iStride + iOffsetX;
      if (CheckLine (pTmp, iWidth)) break;
    }
  }
  if (iOffsetAbs == kiHalfHeight)
    TestPos = -1;
  return TestPos;
}

/*
 * compare pixel line between previous and current one
 * return: 0 for totally equal, otherwise 1
 */
int32_t CompareLine (uint8_t* pYSrc, uint8_t* pYRef, const int32_t kiWidth) {
  int32_t iCmp = 1;

  if (LD32 (pYSrc) != LD32 (pYRef)) return 1;
  if (LD32 (pYSrc + 4) != LD32 (pYRef + 4)) return 1;
  if (LD32 (pYSrc + 8) != LD32 (pYRef + 8)) return 1;
  if (kiWidth > 12)
    iCmp = WelsMemcmp (pYSrc + 12, pYRef + 12, kiWidth - 12);
  return iCmp;
}

void ScrollDetectionCore (SPixMap* pSrcPixMap, SPixMap* pRefPixMap, int32_t iWidth, int32_t iHeight,
                          int32_t iOffsetX, int32_t iOffsetY, SScrollDetectionParam& sScrollDetectionParam) {
  bool bScrollDetected = 0;
  uint8_t* pYLine;
  uint8_t* pYTmp;
  int32_t iTestPos, iSearchPos = 0, iOffsetAbs, iMaxAbs;
  int32_t iPicHeight = pRefPixMap->sRect.iRectHeight;
  int32_t iMinHeight = WELS_MAX (iOffsetY, 0);
  int32_t iMaxHeight = WELS_MIN (iOffsetY + iHeight - 1, iPicHeight - 1) ; //offset_y + height - 1;//
  uint8_t* pYRef, *pYSrc;
  int32_t iYStride;

  pYRef = (uint8_t*)pRefPixMap->pPixel[0];
  pYSrc = (uint8_t*)pSrcPixMap->pPixel[0];
  iYStride = pRefPixMap->iStride[0];

  iTestPos = SelectTestLine (pYSrc, iWidth, iHeight, iPicHeight, iYStride, iOffsetX, iOffsetY);

  if (iTestPos == -1) {
    sScrollDetectionParam.bScrollDetectFlag = 0;
    return;
  }
  pYLine = pYSrc + iYStride * iTestPos + iOffsetX;
  iMaxAbs = WELS_MIN (WELS_MAX (iTestPos - iMinHeight - 1, iMaxHeight - iTestPos), MAX_SCROLL_MV_Y);
  iSearchPos = iTestPos;
  for (iOffsetAbs = 0; iOffsetAbs <= iMaxAbs; iOffsetAbs++) {
    iSearchPos = iTestPos + iOffsetAbs;
    if (iSearchPos <= iMaxHeight) {
      pYTmp = pYRef + iSearchPos * iYStride + iOffsetX;
      if (!CompareLine (pYLine, pYTmp, iWidth)) {
        uint8_t* pYUpper, *pYLineUpper;
        int32_t iCheckedLines;
        int32_t iLowOffset = WELS_MIN (iMaxHeight - iSearchPos, CHECK_OFFSET);
        int32_t i;

        iCheckedLines = WELS_MIN (iTestPos - iMinHeight + iLowOffset, 2 * CHECK_OFFSET);
        pYUpper = pYTmp - (iCheckedLines - iLowOffset) * iYStride;
        pYLineUpper = pYLine - (iCheckedLines - iLowOffset) * iYStride;

        for (i = 0; i < iCheckedLines; i ++) {
          if (CompareLine (pYLineUpper, pYUpper, iWidth)) {
            break;
          }
          pYUpper += iYStride;
          pYLineUpper += iYStride;
        }
        if (i == iCheckedLines) {
          bScrollDetected = 1;
          break;
        }
      }
    }

    iSearchPos = iTestPos - iOffsetAbs - 1;
    if (iSearchPos >= iMinHeight) {
      pYTmp = pYRef + iSearchPos * iYStride + iOffsetX;
      if (!CompareLine (pYLine, pYTmp, iWidth)) {
        uint8_t* pYUpper, *pYLineUpper;
        int32_t iCheckedLines;
        int32_t iUpOffset = WELS_MIN (iSearchPos - iMinHeight, CHECK_OFFSET);
        int32_t i;

        pYUpper = pYTmp - iUpOffset * iYStride;
        pYLineUpper = pYLine - iUpOffset * iYStride;
        iCheckedLines = WELS_MIN (iMaxHeight - iTestPos + iUpOffset, 2 * CHECK_OFFSET);

        for (i = 0; i < iCheckedLines; i ++) {
          if (CompareLine (pYLineUpper, pYUpper, iWidth)) {
            break;
          }
          pYUpper += iYStride;
          pYLineUpper += iYStride;
        }
        if (i == iCheckedLines) {
          bScrollDetected = 1;
          break;
        }
      }
    }
  }

  if (!bScrollDetected) {
    sScrollDetectionParam.bScrollDetectFlag = 0;
  } else {
    sScrollDetectionParam.bScrollDetectFlag = 1;
    sScrollDetectionParam.iScrollMvY = iSearchPos - iTestPos; // pre_pos - cur_pos, change to mv
    sScrollDetectionParam.iScrollMvX = 0;
  }
}

WELSVP_NAMESPACE_END
