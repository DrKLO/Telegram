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
 * \file    utils.c
 *
 * \brief   common tool/function utilization
 *
 * \date    03/10/2009 Created
 *
 *************************************************************************************
 */
#include "utils.h"
#include "crt_util_safe_x.h" // Safe CRT routines like utils for cross platforms
#include "codec_app_def.h"
float WelsCalcPsnr (const void* kpTarPic,
                    const int32_t kiTarStride,
                    const void* kpRefPic,
                    const int32_t kiRefStride,
                    const int32_t kiWidth,
                    const int32_t kiHeight);


void WelsLog (SLogContext* logCtx, int32_t iLevel, const char* kpFmt, ...) {
  va_list vl;
  char pTraceTag[MAX_LOG_SIZE] = {0};
  switch (iLevel) {
  case WELS_LOG_ERROR:
    WelsSnprintf (pTraceTag, MAX_LOG_SIZE, "[OpenH264] this = 0x%p, Error:", logCtx->pCodecInstance);
    break;
  case WELS_LOG_WARNING:
    WelsSnprintf (pTraceTag, MAX_LOG_SIZE, "[OpenH264] this = 0x%p, Warning:", logCtx->pCodecInstance);
    break;
  case WELS_LOG_INFO:
    WelsSnprintf (pTraceTag, MAX_LOG_SIZE, "[OpenH264] this = 0x%p, Info:", logCtx->pCodecInstance);
    break;
  case WELS_LOG_DEBUG:
    WelsSnprintf (pTraceTag, MAX_LOG_SIZE, "[OpenH264] this = 0x%p, Debug:", logCtx->pCodecInstance);
    break;
  default:
    WelsSnprintf (pTraceTag, MAX_LOG_SIZE, "[OpenH264] this = 0x%p, Detail:", logCtx->pCodecInstance);
    break;
  }
  WelsStrcat (pTraceTag, MAX_LOG_SIZE, kpFmt);
  va_start (vl, kpFmt);
  logCtx->pfLog (logCtx->pLogCtx, iLevel, pTraceTag, vl);
  va_end (vl);
}

#ifndef CALC_PSNR
#define CONST_FACTOR_PSNR       (10.0 / log(10.0))      // for good computation
#define CALC_PSNR(w, h, s)      ((float)(CONST_FACTOR_PSNR * log( 65025.0 * w * h / s )))
#endif//CALC_PSNR

/*
 *  PSNR calculation routines
 */
/*!
 *************************************************************************************
 * \brief   PSNR calculation utilization in Wels
 *
 * \param   pTarPic     target picture to be calculated in Picture pData format
 * \param   iTarStride  stride of target picture pData pBuffer
 * \param   pRefPic     base referencing picture samples
 * \param   iRefStride  stride of reference picture pData pBuffer
 * \param   iWidth      picture iWidth in pixel
 * \param   iHeight     picture iHeight in pixel
 *
 * \return  actual PSNR result;
 *
 * \note    N/A
 *************************************************************************************
 */
float WelsCalcPsnr (const void* kpTarPic,
                    const int32_t kiTarStride,
                    const void* kpRefPic,
                    const int32_t kiRefStride,
                    const int32_t kiWidth,
                    const int32_t kiHeight) {
  int64_t iSqe = 0;
  int32_t x, y;
  uint8_t* pTar = (uint8_t*)kpTarPic;
  uint8_t* pRef = (uint8_t*)kpRefPic;

  if (NULL == pTar || NULL == pRef)
    return (-1.0f);

  for (y = 0; y < kiHeight; ++ y) { // OPTable !!
    for (x = 0; x < kiWidth; ++ x) {
      const int32_t kiT = pTar[y * kiTarStride + x] - pRef[y * kiRefStride + x];
      iSqe += kiT * kiT;
    }
  }
  if (0 == iSqe) {
    return (99.99f);
  }
  return CALC_PSNR (kiWidth, kiHeight, iSqe);
}

