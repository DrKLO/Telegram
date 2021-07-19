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

#include "imagerotate.h"

WELSVP_NAMESPACE_BEGIN

///////////////////////////////////////////////////////////////////////////////////////////////////////////////

CImageRotating::CImageRotating (int32_t iCpuFlag) {
  m_iCPUFlag = iCpuFlag;
  m_eMethod   = METHOD_IMAGE_ROTATE;
  WelsMemset (&m_pfRotateImage, 0, sizeof (m_pfRotateImage));
  InitImageRotateFuncs (m_pfRotateImage, m_iCPUFlag);
}

CImageRotating::~CImageRotating() {
}

void CImageRotating::InitImageRotateFuncs (SImageRotateFuncs& sImageRotateFuncs, int32_t iCpuFlag) {
  sImageRotateFuncs.pfImageRotate90D = ImageRotate90D_c;
  sImageRotateFuncs.pfImageRotate180D = ImageRotate180D_c;
  sImageRotateFuncs.pfImageRotate270D = ImageRotate270D_c;
}
EResult CImageRotating::ProcessImageRotate (int32_t iType, uint8_t* pSrc, uint32_t uiBytesPerPixel, uint32_t iWidth,
    uint32_t iHeight, uint8_t* pDst) {
  if (iType == 90) {
    m_pfRotateImage.pfImageRotate90D (pSrc, uiBytesPerPixel, iWidth, iHeight, pDst);
  } else if (iType == 180) {
    m_pfRotateImage.pfImageRotate180D (pSrc, uiBytesPerPixel, iWidth, iHeight, pDst);
  } else if (iType == 270) {
    m_pfRotateImage.pfImageRotate270D (pSrc, uiBytesPerPixel, iWidth, iHeight, pDst);
  } else {
    return RET_NOTSUPPORTED;
  }
  return RET_SUCCESS;
}

EResult CImageRotating::Process (int32_t iType, SPixMap* pSrc, SPixMap* pDst) {
  EResult eReturn = RET_INVALIDPARAM;

  if ((pSrc->eFormat == VIDEO_FORMAT_RGBA) ||
      (pSrc->eFormat == VIDEO_FORMAT_BGRA) ||
      (pSrc->eFormat == VIDEO_FORMAT_ABGR) ||
      (pSrc->eFormat == VIDEO_FORMAT_ARGB)) {
    eReturn = ProcessImageRotate (iType, (uint8_t*)pSrc->pPixel[0], pSrc->iSizeInBits * 8, pSrc->sRect.iRectWidth,
                                  pSrc->sRect.iRectHeight, (uint8_t*)pDst->pPixel[0]);
  } else if (pSrc->eFormat == VIDEO_FORMAT_I420) {
    ProcessImageRotate (iType, (uint8_t*)pSrc->pPixel[0], pSrc->iSizeInBits * 8, pSrc->sRect.iRectWidth,
                        pSrc->sRect.iRectHeight, (uint8_t*)pDst->pPixel[0]);
    ProcessImageRotate (iType, (uint8_t*)pSrc->pPixel[1], pSrc->iSizeInBits * 8, (pSrc->sRect.iRectWidth >> 1),
                        (pSrc->sRect.iRectHeight >> 1), (uint8_t*)pDst->pPixel[1]);
    eReturn = ProcessImageRotate (iType, (uint8_t*)pSrc->pPixel[2], pSrc->iSizeInBits * 8, (pSrc->sRect.iRectWidth >> 1),
                                  (pSrc->sRect.iRectHeight >> 1), (uint8_t*)pDst->pPixel[2]);
  } else {
    eReturn = RET_NOTSUPPORTED;
  }

  return eReturn;
}


WELSVP_NAMESPACE_END
