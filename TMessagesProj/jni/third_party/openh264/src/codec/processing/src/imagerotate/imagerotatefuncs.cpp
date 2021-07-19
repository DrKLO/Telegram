/*!
 * \copy
 *     Copyright (c)  2011-2013, Cisco Systems
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
 *  image_rotate.c
 *
 *  Created on 11-2-21.
 *
 */

#include "imagerotate.h"

WELSVP_NAMESPACE_BEGIN

void ImageRotate90D_c (uint8_t* pSrc, uint32_t uiBytesPerPixel, uint32_t iWidth, uint32_t iHeight, uint8_t* pDst) {
  for (uint32_t j = 0; j < iHeight; j++) {
    for (uint32_t i = 0; i < iWidth; i++) {
      for (uint32_t n = 0; n < uiBytesPerPixel; n++)
        pDst[ (i * iHeight + iHeight - 1 - j)*uiBytesPerPixel + n] = pSrc[ (iWidth * j + i) * uiBytesPerPixel + n];
    }
  }
}
void ImageRotate180D_c (uint8_t* pSrc, uint32_t uiBytesPerPixel, uint32_t iWidth, uint32_t iHeight, uint8_t* pDst) {
  for (uint32_t j = 0; j < iHeight; j++) {
    for (uint32_t i = 0; i < iWidth; i++) {
      for (uint32_t n = 0; n < uiBytesPerPixel; n++)
        pDst[ ((iHeight - 1 - j)*iWidth + iWidth - 1 - i)*uiBytesPerPixel + n] = pSrc[ (iWidth * j + i) * uiBytesPerPixel + n];
    }
  }
}
void ImageRotate270D_c (uint8_t* pSrc, uint32_t uiBytesPerPixel, uint32_t iWidth, uint32_t iHeight, uint8_t* pDst) {
  for (uint32_t j = 0; j < iWidth; j++) {
    for (uint32_t i = 0; i < iHeight; i++) {
      for (uint32_t n = 0; n < uiBytesPerPixel; n++)
        pDst[ ((iWidth - 1 - j)*iHeight + i)*uiBytesPerPixel + n] = pSrc[ (iWidth * i + j) * uiBytesPerPixel + n];
    }
  }
}
WELSVP_NAMESPACE_END
