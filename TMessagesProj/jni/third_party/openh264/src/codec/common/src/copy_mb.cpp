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
 * \file    copy_mb.cpp
 *
 * \brief   copy MB YUV data
 *
 * \date    2014.04.14 Created
 *
 *************************************************************************************
 */

#include "copy_mb.h"
#include "macros.h"
#include "ls_defines.h"

/****************************************************************************
 * Copy functions
 ****************************************************************************/
void WelsCopy4x4_c (uint8_t* pDst, int32_t iStrideD, uint8_t* pSrc, int32_t iStrideS) {
  const int32_t kiSrcStride2 = iStrideS << 1;
  const int32_t kiSrcStride3 = iStrideS + kiSrcStride2;
  const int32_t kiDstStride2 = iStrideD << 1;
  const int32_t kiDstStride3 = iStrideD + kiDstStride2;

  ST32 (pDst,                LD32 (pSrc));
  ST32 (pDst + iStrideD,     LD32 (pSrc + iStrideS));
  ST32 (pDst + kiDstStride2, LD32 (pSrc + kiSrcStride2));
  ST32 (pDst + kiDstStride3, LD32 (pSrc + kiSrcStride3));
}
void WelsCopy8x4_c (uint8_t* pDst, int32_t iStrideD, uint8_t* pSrc, int32_t iStrideS) {
  WelsCopy4x4_c (pDst, iStrideD, pSrc, iStrideS);
  WelsCopy4x4_c (pDst + 4, iStrideD, pSrc + 4, iStrideS);
}
void WelsCopy4x8_c (uint8_t* pDst, int32_t iStrideD, uint8_t* pSrc, int32_t iStrideS) {
  WelsCopy4x4_c (pDst, iStrideD, pSrc, iStrideS);
  WelsCopy4x4_c (pDst + (iStrideD << 2), iStrideD, pSrc + (iStrideS << 2), iStrideS);
}
void WelsCopy8x8_c (uint8_t* pDst, int32_t iStrideD, uint8_t* pSrc, int32_t iStrideS) {
  int32_t i;
  for (i = 0; i < 4; i++) {
    ST32 (pDst,                 LD32 (pSrc));
    ST32 (pDst + 4 ,            LD32 (pSrc + 4));
    ST32 (pDst + iStrideD,      LD32 (pSrc + iStrideS));
    ST32 (pDst + iStrideD + 4 , LD32 (pSrc + iStrideS + 4));
    pDst += iStrideD << 1;
    pSrc += iStrideS << 1;
  }
}
void WelsCopy8x16_c (uint8_t* pDst, int32_t iStrideD, uint8_t* pSrc, int32_t iStrideS) {
  int32_t i;
  for (i = 0; i < 8; ++i) {
    ST32 (pDst,                 LD32 (pSrc));
    ST32 (pDst + 4 ,            LD32 (pSrc + 4));
    ST32 (pDst + iStrideD,      LD32 (pSrc + iStrideS));
    ST32 (pDst + iStrideD + 4 , LD32 (pSrc + iStrideS + 4));
    pDst += iStrideD << 1;
    pSrc += iStrideS << 1;
  }
}
void WelsCopy16x8_c (uint8_t* pDst, int32_t iStrideD, uint8_t* pSrc, int32_t iStrideS) {
  int32_t i;
  for (i = 0; i < 8; i++) {
    ST32 (pDst,         LD32 (pSrc));
    ST32 (pDst + 4 ,    LD32 (pSrc + 4));
    ST32 (pDst + 8 ,    LD32 (pSrc + 8));
    ST32 (pDst + 12 ,   LD32 (pSrc + 12));
    pDst += iStrideD ;
    pSrc += iStrideS;
  }
}
void WelsCopy16x16_c (uint8_t* pDst, int32_t iStrideD, uint8_t* pSrc, int32_t iStrideS) {
  int32_t i;
  for (i = 0; i < 16; i++) {
    ST32 (pDst,         LD32 (pSrc));
    ST32 (pDst + 4 ,    LD32 (pSrc + 4));
    ST32 (pDst + 8 ,    LD32 (pSrc + 8));
    ST32 (pDst + 12 ,   LD32 (pSrc + 12));
    pDst += iStrideD ;
    pSrc += iStrideS;
  }
}

