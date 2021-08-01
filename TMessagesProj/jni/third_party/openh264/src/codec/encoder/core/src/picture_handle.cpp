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
 * \file    picture_handle.c
 *
 * \brief   picture pData handling
 *
 * \date    5/20/2009 Created
 *
 *************************************************************************************/
#include "picture_handle.h"
#include "svc_motion_estimate.h"

namespace WelsEnc {
/*!
 * \brief   alloc picture pData with borders for each plane based width and height of picture
 * \param   cx              width of picture in pixels
 * \param   cy              height of picture in pixels
 * \param   need_data       need pData allocation
 * \pram    need_expand     need borders expanding
 * \return  successful if effective picture pointer returned, otherwise failed with NULL
 */
SPicture* AllocPicture (CMemoryAlign* pMa, const int32_t kiWidth , const int32_t kiHeight,
                        bool bNeedMbInfo, int32_t iNeedFeatureStorage) {
  SPicture* pPic = NULL;
  int32_t iPicWidth = 0;
  int32_t iPicHeight = 0;

  int32_t iPicChromaWidth       = 0;
  int32_t iPicChromaHeight      = 0;
  int32_t iLumaSize             = 0;
  int32_t iChromaSize           = 0;

  pPic = static_cast<SPicture*> (pMa->WelsMallocz (sizeof (SPicture), "pPic"));

  WELS_VERIFY_RETURN_IF (NULL, NULL == pPic);

  iPicWidth         = WELS_ALIGN (kiWidth, MB_WIDTH_LUMA) + (PADDING_LENGTH << 1);  // with width of horizon
  iPicHeight        = WELS_ALIGN (kiHeight, MB_HEIGHT_LUMA) + (PADDING_LENGTH << 1);        // with height of vertical
  iPicChromaWidth   = iPicWidth >> 1;
  iPicChromaHeight  = iPicHeight >> 1;
  iPicWidth         = WELS_ALIGN (iPicWidth,
                                  32);  // 32(or 16 for chroma below) to match original imp. here instead of cache_line_size
  iPicChromaWidth   = WELS_ALIGN (iPicChromaWidth, 16);
  iLumaSize         = iPicWidth * iPicHeight;
  iChromaSize       = iPicChromaWidth * iPicChromaHeight;

  pPic->pBuffer = (uint8_t*)pMa->WelsMalloc (iLumaSize /* luma */
                  + (iChromaSize << 1) /* Cb,Cr */
                  , "pPic->pBuffer");
  WELS_VERIFY_RETURN_PROC_IF (NULL, NULL == pPic->pBuffer, FreePicture (pMa, &pPic));
  pPic->iLineSize[0]    = iPicWidth;
  pPic->iLineSize[1]    = pPic->iLineSize[2]    = iPicChromaWidth;
  pPic->pData[0]        = pPic->pBuffer + (1 + pPic->iLineSize[0]) * PADDING_LENGTH;
  pPic->pData[1]        = pPic->pBuffer + iLumaSize + (((1 + pPic->iLineSize[1]) * PADDING_LENGTH) >> 1);
  pPic->pData[2]        = pPic->pBuffer + iLumaSize + iChromaSize + (((1 + pPic->iLineSize[2]) * PADDING_LENGTH) >> 1);

  pPic->iWidthInPixel   = kiWidth;
  pPic->iHeightInPixel  = kiHeight;
  pPic->iFrameNum       = -1;

  pPic->bIsLongRef      = false;
  pPic->iLongTermPicNum = -1;
  pPic->uiRecieveConfirmed = 0;
  pPic->iMarkFrameNum   = -1;

  if (bNeedMbInfo) {
    const uint32_t kuiCountMbNum = ((15 + kiWidth) >> 4) * ((15 + kiHeight) >> 4);

    pPic->uiRefMbType = (uint32_t*)pMa->WelsMallocz (kuiCountMbNum * sizeof (uint32_t), "pPic->uiRefMbType");
    WELS_VERIFY_RETURN_PROC_IF (NULL, NULL == pPic->uiRefMbType, FreePicture (pMa, &pPic));

    pPic->pRefMbQp = (uint8_t*)pMa->WelsMallocz (kuiCountMbNum * sizeof (uint8_t), "pPic->pRefMbQp");
    WELS_VERIFY_RETURN_PROC_IF (NULL, NULL == pPic->pRefMbQp, FreePicture (pMa, &pPic));

    pPic->sMvList           = static_cast<SMVUnitXY*> (pMa->WelsMallocz (kuiCountMbNum * sizeof (SMVUnitXY),
                              "pPic->sMvList"));
    WELS_VERIFY_RETURN_PROC_IF (NULL, NULL == pPic->sMvList, FreePicture (pMa, &pPic));

    pPic->pMbSkipSad       = (int32_t*)pMa->WelsMallocz (kuiCountMbNum * sizeof (int32_t), "pPic->pMbSkipSad");
    WELS_VERIFY_RETURN_PROC_IF (NULL, NULL == pPic->pMbSkipSad, FreePicture (pMa, &pPic));
  }

  if (iNeedFeatureStorage) {
    pPic->pScreenBlockFeatureStorage = static_cast<SScreenBlockFeatureStorage*> (pMa->WelsMallocz (sizeof (
                                         SScreenBlockFeatureStorage), "pScreenBlockFeatureStorage"));
    int32_t iReturn = RequestScreenBlockFeatureStorage (pMa, kiWidth,  kiHeight, iNeedFeatureStorage,
                      pPic->pScreenBlockFeatureStorage);
    WELS_VERIFY_RETURN_PROC_IF (NULL, ENC_RETURN_SUCCESS != iReturn, FreePicture (pMa, &pPic));
  } else {
    pPic->pScreenBlockFeatureStorage = NULL;
  }
  return pPic;
}

/*!
 * \brief   free picture pData planes
 * \param   pPic        picture pointer to be destoryed
 * \return  none
 */
void FreePicture (CMemoryAlign* pMa, SPicture** ppPic) {
  if (NULL != ppPic && NULL != *ppPic) {
    SPicture* pPic = *ppPic;

    if (NULL != pPic->pBuffer) {
      pMa->WelsFree (pPic->pBuffer, "pPic->pBuffer");
      pPic->pBuffer = NULL;
    }
    pPic->pBuffer          = NULL;
    pPic->pData[0]         =
      pPic->pData[1]       =
        pPic->pData[2]     = NULL;
    pPic->iLineSize[0]     =
      pPic->iLineSize[1]   =
        pPic->iLineSize[2] = 0;

    pPic->iWidthInPixel         = 0;
    pPic->iHeightInPixel        = 0;
    pPic->iFrameNum             = -1;

    pPic->bIsLongRef            = false;
    pPic->uiRecieveConfirmed    = 0;
    pPic->iLongTermPicNum       = -1;
    pPic->iMarkFrameNum         = -1;

    if (pPic->uiRefMbType) {
      pMa->WelsFree (pPic->uiRefMbType, "pPic->uiRefMbType");
      pPic->uiRefMbType = NULL;
    }
    if (pPic->pRefMbQp) {
      pMa->WelsFree (pPic->pRefMbQp, "pPic->pRefMbQp");
      pPic->pRefMbQp = NULL;
    }

    if (pPic->sMvList) {
      pMa->WelsFree (pPic->sMvList, "pPic->sMvList");
      pPic->sMvList = NULL;
    }
    if (pPic->pMbSkipSad) {
      pMa->WelsFree (pPic->pMbSkipSad, "pPic->pMbSkipSad");
      pPic->pMbSkipSad = NULL;
    }

    if (pPic->pScreenBlockFeatureStorage) {
      ReleaseScreenBlockFeatureStorage (pMa, pPic->pScreenBlockFeatureStorage);
      pMa->WelsFree (pPic->pScreenBlockFeatureStorage, "pPic->pScreenBlockFeatureStorage");
      pPic->pScreenBlockFeatureStorage = NULL;
    }

    pMa->WelsFree (*ppPic, "pPic");
    *ppPic = NULL;
  }
}

} // namespace WelsEnc

