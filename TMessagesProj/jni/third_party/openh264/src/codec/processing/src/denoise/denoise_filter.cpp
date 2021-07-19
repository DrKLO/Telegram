/*!
 * \copy
 *     Copyright (c)  2010-2013, Cisco Systems
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
 * \file    svc_preprocess.h
 *
 * \brief   svc denoising
 *
 * \date    4/1/2010 Created
 *
 */

#include "denoise.h"

WELSVP_NAMESPACE_BEGIN

void BilateralLumaFilter8_c (uint8_t* pSample, int32_t iStride) {
  int32_t nSum = 0, nTotWeight = 0;
  int32_t iCenterSample = *pSample;
  uint8_t* pCurLine = pSample - iStride - DENOISE_GRAY_RADIUS;
  int32_t x, y;
  int32_t iCurSample, iCurWeight, iGreyDiff;
  uint8_t aSample[8];

  for (int32_t i = 0; i < 8; i++) {
    nSum = 0;
    nTotWeight = 0;
    iCenterSample = *pSample;
    pCurLine = pSample - iStride - DENOISE_GRAY_RADIUS;
    for (y = 0; y < 3; y++) {
      for (x = 0; x < 3; x++) {
        if (x == 1 && y == 1) continue; // except center point
        iCurSample = pCurLine[x];
        iCurWeight = WELS_ABS (iCurSample - iCenterSample);
        iGreyDiff = 32 - iCurWeight;
        if (iGreyDiff < 0) continue;
        else iCurWeight = (iGreyDiff * iGreyDiff) >> 5;
        nSum += iCurSample * iCurWeight;
        nTotWeight +=  iCurWeight;
      }
      pCurLine += iStride;
    }
    nTotWeight = 256 - nTotWeight;
    nSum += iCenterSample * nTotWeight;
    aSample[i] = nSum >> 8;
    pSample++;
  }
  WelsMemcpy (pSample - 8, aSample, 8);
}


/***************************************************************************
5x5 filter:
1   1   2   1   1
1   2   4   2   1
2   4   20  4   2
1   2   4   2   1
1   1   2   1   1
***************************************************************************/
#define SUM_LINE1(pSample)       (pSample[0]     +(pSample[1])    +(pSample[2]<<1)  + pSample[3]     + pSample[4])
#define SUM_LINE2(pSample)       (pSample[0]     +(pSample[1]<<1) +(pSample[2]<<2)  +(pSample[3]<<1) + pSample[4])
#define SUM_LINE3(pSample)      ((pSample[0]<<1) +(pSample[1]<<2) +(pSample[2]*20)  +(pSample[3]<<2) +(pSample[4]<<1))
void WaverageChromaFilter8_c (uint8_t* pSample, int32_t iStride) {
  int32_t sum;
  uint8_t* pStartPixels = pSample - UV_WINDOWS_RADIUS * iStride - UV_WINDOWS_RADIUS;
  uint8_t* pCurLine1 = pStartPixels;
  uint8_t* pCurLine2 = pCurLine1 + iStride;
  uint8_t* pCurLine3 = pCurLine2 + iStride;
  uint8_t* pCurLine4 = pCurLine3 + iStride;
  uint8_t* pCurLine5 = pCurLine4 + iStride;
  uint8_t aSample[8];

  for (int32_t i = 0; i < 8; i++) {
    sum = SUM_LINE1 ((pCurLine1 + i)) + SUM_LINE2 ((pCurLine2 + i)) + SUM_LINE3 ((pCurLine3 + i))
          + SUM_LINE2 ((pCurLine4 + i)) + SUM_LINE1 ((pCurLine5 + i));
    aSample[i] = (sum >> 6);
    pSample++;
  }
  WelsMemcpy (pSample - 8, aSample, 8);
}

/***************************************************************************
edge of y/uv use a 3x3 Gauss filter, radius = 1:
1   2   1
2   4   2
1   2   1
***************************************************************************/
void Gauss3x3Filter (uint8_t* pSrc, int32_t iStride) {
  int32_t nSum = 0;
  uint8_t* pCurLine1 = pSrc - iStride - 1;
  uint8_t* pCurLine2 = pCurLine1 + iStride;
  uint8_t* pCurLine3 = pCurLine2 + iStride;

  nSum =  pCurLine1[0]       + (pCurLine1[1] << 1) +  pCurLine1[2]       +
         (pCurLine2[0] << 1) + (pCurLine2[1] << 2) + (pCurLine2[2] << 1) +
          pCurLine3[0]       + (pCurLine3[1] << 1) +  pCurLine3[2];
  *pSrc = nSum >> 4;
}

WELSVP_NAMESPACE_END
