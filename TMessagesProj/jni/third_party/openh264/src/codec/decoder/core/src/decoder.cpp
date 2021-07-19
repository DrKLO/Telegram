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
 * \file    decoder.c
 *
 * \brief   Interfaces implementation introduced in decoder system architecture
 *
 * \date    03/10/2009 Created
 *
 *************************************************************************************
 */
#include "codec_def.h"
#include "decoder.h"
#include "cpu.h"
#include "au_parser.h"
#include "get_intra_predictor.h"
#include "rec_mb.h"
#include "mc.h"
#include "decode_mb_aux.h"
#include "manage_dec_ref.h"
#include "decoder_core.h"
#include "deblocking.h"
#include "expand_pic.h"
#include "decode_slice.h"
#include "error_concealment.h"
#include "memory_align.h"
#include "wels_decoder_thread.h"

namespace WelsDec {

extern PPicture AllocPicture (PWelsDecoderContext pCtx, const int32_t kiPicWidth, const int32_t kiPicHeight);

extern void FreePicture (PPicture pPic, CMemoryAlign* pMa);

static int32_t CreatePicBuff (PWelsDecoderContext pCtx, PPicBuff* ppPicBuf, const int32_t kiSize,
                              const int32_t kiPicWidth, const int32_t kiPicHeight) {

  PPicBuff pPicBuf = NULL;
  int32_t iPicIdx = 0;
  if (kiSize <= 0 || kiPicWidth <= 0 || kiPicHeight <= 0) {
    return ERR_INFO_INVALID_PARAM;
  }

  CMemoryAlign* pMa = pCtx->pMemAlign;

  pPicBuf = (PPicBuff)pMa->WelsMallocz (sizeof (SPicBuff), "PPicBuff");

  if (NULL == pPicBuf) {
    return ERR_INFO_OUT_OF_MEMORY;
  }

  pPicBuf->ppPic = (PPicture*)pMa->WelsMallocz (kiSize * sizeof (PPicture), "PPicture*");

  if (NULL == pPicBuf->ppPic) {
    pPicBuf->iCapacity = 0;
    DestroyPicBuff (pCtx, &pPicBuf, pMa);
    return ERR_INFO_OUT_OF_MEMORY;
  }

  for (iPicIdx = 0; iPicIdx < kiSize; ++ iPicIdx) {
    PPicture pPic = AllocPicture (pCtx, kiPicWidth, kiPicHeight);
    if (NULL == pPic) {
      // init capacity first for free memory
      pPicBuf->iCapacity = iPicIdx;
      DestroyPicBuff (pCtx, &pPicBuf, pMa);
      return ERR_INFO_OUT_OF_MEMORY;
    }
    pPicBuf->ppPic[iPicIdx] = pPic;
  }

// initialize context in queue
  pPicBuf->iCapacity   = kiSize;
  pPicBuf->iCurrentIdx = 0;
  * ppPicBuf           = pPicBuf;

  return ERR_NONE;
}

static int32_t IncreasePicBuff (PWelsDecoderContext pCtx, PPicBuff* ppPicBuf, const int32_t kiOldSize,
                                const int32_t kiPicWidth, const int32_t kiPicHeight, const int32_t kiNewSize) {
  PPicBuff pPicOldBuf = *ppPicBuf;
  PPicBuff pPicNewBuf = NULL;
  int32_t iPicIdx = 0;
  if (kiOldSize <= 0 || kiNewSize <= 0 || kiPicWidth <= 0 || kiPicHeight <= 0) {
    return ERR_INFO_INVALID_PARAM;
  }

  CMemoryAlign* pMa = pCtx->pMemAlign;
  pPicNewBuf = (PPicBuff)pMa->WelsMallocz (sizeof (SPicBuff), "PPicBuff");

  if (NULL == pPicNewBuf) {
    return ERR_INFO_OUT_OF_MEMORY;
  }

  pPicNewBuf->ppPic = (PPicture*)pMa->WelsMallocz (kiNewSize * sizeof (PPicture), "PPicture*");

  if (NULL == pPicNewBuf->ppPic) {
    pPicNewBuf->iCapacity = 0;
    DestroyPicBuff (pCtx, &pPicNewBuf, pMa);
    return ERR_INFO_OUT_OF_MEMORY;
  }

  // increase new PicBuf
  for (iPicIdx = kiOldSize; iPicIdx < kiNewSize; ++ iPicIdx) {
    PPicture pPic = AllocPicture (pCtx, kiPicWidth, kiPicHeight);
    if (NULL == pPic) {
      // Set maximum capacity as the new malloc memory at the tail
      pPicNewBuf->iCapacity = iPicIdx;
      DestroyPicBuff (pCtx, &pPicNewBuf, pMa);
      return ERR_INFO_OUT_OF_MEMORY;
    }
    pPicNewBuf->ppPic[iPicIdx] = pPic;
  }

  // copy old PicBuf to new PicBuf
  memcpy (pPicNewBuf->ppPic, pPicOldBuf->ppPic, kiOldSize * sizeof (PPicture));

// initialize context in queue
  pPicNewBuf->iCapacity   = kiNewSize;
  pPicNewBuf->iCurrentIdx = pPicOldBuf->iCurrentIdx;
  * ppPicBuf              = pPicNewBuf;

  for (int32_t i = 0; i < pPicNewBuf->iCapacity; i++) {
    pPicNewBuf->ppPic[i]->bUsedAsRef = false;
    pPicNewBuf->ppPic[i]->bIsLongRef = false;
    pPicNewBuf->ppPic[i]->iRefCount = 0;
    pPicNewBuf->ppPic[i]->bIsComplete = false;
  }
// remove old PicBuf
  if (pPicOldBuf->ppPic != NULL) {
    pMa->WelsFree (pPicOldBuf->ppPic, "pPicOldBuf->queue");
    pPicOldBuf->ppPic = NULL;
  }
  pPicOldBuf->iCapacity = 0;
  pPicOldBuf->iCurrentIdx = 0;
  pMa->WelsFree (pPicOldBuf, "pPicOldBuf");
  pPicOldBuf = NULL;
  return ERR_NONE;
}

static int32_t DecreasePicBuff (PWelsDecoderContext pCtx, PPicBuff* ppPicBuf, const int32_t kiOldSize,
                                const int32_t kiPicWidth, const int32_t kiPicHeight, const int32_t kiNewSize) {
  PPicBuff pPicOldBuf = *ppPicBuf;
  PPicBuff pPicNewBuf = NULL;
  int32_t iPicIdx = 0;
  if (kiOldSize <= 0 || kiNewSize <= 0 || kiPicWidth <= 0 || kiPicHeight <= 0) {
    return ERR_INFO_INVALID_PARAM;
  }

  CMemoryAlign* pMa = pCtx->pMemAlign;

  pPicNewBuf = (PPicBuff)pMa->WelsMallocz (sizeof (SPicBuff), "PPicBuff");

  if (NULL == pPicNewBuf) {
    return ERR_INFO_OUT_OF_MEMORY;
  }

  pPicNewBuf->ppPic = (PPicture*)pMa->WelsMallocz (kiNewSize * sizeof (PPicture), "PPicture*");

  if (NULL == pPicNewBuf->ppPic) {
    pPicNewBuf->iCapacity = 0;
    DestroyPicBuff (pCtx, &pPicNewBuf, pMa);
    return ERR_INFO_OUT_OF_MEMORY;
  }

  ResetReorderingPictureBuffers (pCtx->pPictReoderingStatus, pCtx->pPictInfoList, false);

  int32_t iPrevPicIdx = -1;
  for (iPrevPicIdx = 0; iPrevPicIdx < kiOldSize; ++iPrevPicIdx) {
    if (pCtx->pLastDecPicInfo->pPreviousDecodedPictureInDpb == pPicOldBuf->ppPic[iPrevPicIdx]) {
      break;
    }
  }
  int32_t iDelIdx;
  if (iPrevPicIdx < kiOldSize && iPrevPicIdx >= kiNewSize) {
    // found pPreviousDecodedPictureInDpb,
    pPicNewBuf->ppPic[0] = pPicOldBuf->ppPic[iPrevPicIdx];
    pPicNewBuf->iCurrentIdx = 0;
    memcpy (pPicNewBuf->ppPic + 1, pPicOldBuf->ppPic, (kiNewSize - 1) * sizeof (PPicture));
    iDelIdx = kiNewSize - 1;
  } else {
    memcpy (pPicNewBuf->ppPic, pPicOldBuf->ppPic, kiNewSize * sizeof (PPicture));
    pPicNewBuf->iCurrentIdx = iPrevPicIdx < kiNewSize ? iPrevPicIdx : 0;
    iDelIdx = kiNewSize;
  }

  //update references due to allocation changes
  //all references' references have to be reset oss-buzz 14423
  for (int32_t i = 0; i < kiNewSize; i++) {
    for (int32_t listIdx = LIST_0; listIdx < LIST_A; ++listIdx) {
      int32_t j = -1;
      while (++j < MAX_DPB_COUNT && pPicNewBuf->ppPic[i]->pRefPic[listIdx][j] != NULL) {
        pPicNewBuf->ppPic[i]->pRefPic[listIdx][j] = NULL;
      }
    }
  }

  for (iPicIdx = iDelIdx; iPicIdx < kiOldSize; iPicIdx++) {
    if (iPrevPicIdx != iPicIdx) {
      if (pPicOldBuf->ppPic[iPicIdx] != NULL) {
        FreePicture (pPicOldBuf->ppPic[iPicIdx], pMa);
        pPicOldBuf->ppPic[iPicIdx] = NULL;
      }
    }
  }

  // initialize context in queue
  pPicNewBuf->iCapacity = kiNewSize;
  * ppPicBuf             = pPicNewBuf;

  for (int32_t i = 0; i < pPicNewBuf->iCapacity; i++) {
    pPicNewBuf->ppPic[i]->bUsedAsRef = false;
    pPicNewBuf->ppPic[i]->bIsLongRef = false;
    pPicNewBuf->ppPic[i]->iRefCount = 0;
    pPicNewBuf->ppPic[i]->bIsComplete = false;
  }
  // remove old PicBuf
  if (pPicOldBuf->ppPic != NULL) {
    pMa->WelsFree (pPicOldBuf->ppPic, "pPicOldBuf->queue");
    pPicOldBuf->ppPic = NULL;
  }
  pPicOldBuf->iCapacity = 0;
  pPicOldBuf->iCurrentIdx = 0;
  pMa->WelsFree (pPicOldBuf, "pPicOldBuf");
  pPicOldBuf = NULL;

  return ERR_NONE;
}

void DestroyPicBuff (PWelsDecoderContext pCtx, PPicBuff* ppPicBuf, CMemoryAlign* pMa) {
  PPicBuff pPicBuf = NULL;

  ResetReorderingPictureBuffers (pCtx->pPictReoderingStatus, pCtx->pPictInfoList, false);
  if (pCtx->pDstInfo) pCtx->pDstInfo->iBufferStatus = 0;

  if (NULL == ppPicBuf || NULL == *ppPicBuf)
    return;

  pPicBuf = *ppPicBuf;
  while (pPicBuf->ppPic != NULL) {
    int32_t iPicIdx = 0;
    while (iPicIdx < pPicBuf->iCapacity) {
      PPicture pPic = pPicBuf->ppPic[iPicIdx];
      if (pPic != NULL) {
        FreePicture (pPic, pMa);
      }
      pPic = NULL;
      ++ iPicIdx;
    }

    pMa->WelsFree (pPicBuf->ppPic, "pPicBuf->queue");

    pPicBuf->ppPic = NULL;
  }
  pPicBuf->iCapacity = 0;
  pPicBuf->iCurrentIdx = 0;

  pMa->WelsFree (pPicBuf, "pPicBuf");

  pPicBuf = NULL;
  *ppPicBuf = NULL;
}

//reset picture reodering buffer list
void ResetReorderingPictureBuffers (PPictReoderingStatus pPictReoderingStatus, PPictInfo pPictInfo,
                                    const bool& fullReset) {
  if (pPictReoderingStatus != NULL && pPictInfo != NULL) {
    int32_t pictInfoListCount = fullReset ? 16 : (pPictReoderingStatus->iLargestBufferedPicIndex + 1);
    pPictReoderingStatus->iPictInfoIndex = 0;
    pPictReoderingStatus->iMinPOC = IMinInt32;
    pPictReoderingStatus->iNumOfPicts = 0;
    pPictReoderingStatus->iLastGOPRemainPicts = 0;
    pPictReoderingStatus->iLastWrittenPOC = IMinInt32;
    pPictReoderingStatus->iLargestBufferedPicIndex = 0;
    for (int32_t i = 0; i < pictInfoListCount; ++i) {
      pPictInfo[i].bLastGOP = false;
      pPictInfo[i].iPOC = IMinInt32;
    }
    pPictInfo->sBufferInfo.iBufferStatus = 0;
  }
}

/*
 * fill data fields in default for decoder context
 */
void WelsDecoderDefaults (PWelsDecoderContext pCtx, SLogContext* pLogCtx) {
  int32_t iCpuCores               = 1;
  pCtx->sLogCtx = *pLogCtx;

  pCtx->pArgDec                   = NULL;

  pCtx->bHaveGotMemory            = false;              // not ever request memory blocks for decoder context related
  pCtx->uiCpuFlag                 = 0;

  pCtx->bAuReadyFlag              = 0;                  // au data is not ready
  pCtx->bCabacInited = false;

  pCtx->uiCpuFlag = WelsCPUFeatureDetect (&iCpuCores);

  pCtx->iImgWidthInPixel          = 0;
  pCtx->iImgHeightInPixel         = 0;                  // alloc picture data when picture size is available
  pCtx->iLastImgWidthInPixel      = 0;
  pCtx->iLastImgHeightInPixel     = 0;
  pCtx->bFreezeOutput = true;

  pCtx->iFrameNum                 = -1;
  pCtx->pLastDecPicInfo->iPrevFrameNum             = -1;
  pCtx->iErrorCode                = ERR_NONE;

  pCtx->pDec                      = NULL;

  pCtx->pTempDec                  = NULL;

  WelsResetRefPic (pCtx);

  pCtx->iActiveFmoNum             = 0;

  pCtx->pPicBuff          = NULL;

  //pCtx->sSpsPpsCtx.bAvcBasedFlag             = true;
  pCtx->pLastDecPicInfo->pPreviousDecodedPictureInDpb = NULL;
  pCtx->pDecoderStatistics->iAvgLumaQp = -1;
  pCtx->pDecoderStatistics->iStatisticsLogInterval = 1000;
  pCtx->bUseScalingList = false;
  /*pCtx->sSpsPpsCtx.iSpsErrorIgnored = 0;
  pCtx->sSpsPpsCtx.iSubSpsErrorIgnored = 0;
  pCtx->sSpsPpsCtx.iPpsErrorIgnored = 0;
  pCtx->sSpsPpsCtx.iPPSInvalidNum = 0;
  pCtx->sSpsPpsCtx.iPPSLastInvalidId = -1;
  pCtx->sSpsPpsCtx.iSPSInvalidNum = 0;
  pCtx->sSpsPpsCtx.iSPSLastInvalidId = -1;
  pCtx->sSpsPpsCtx.iSubSPSInvalidNum = 0;
  pCtx->sSpsPpsCtx.iSubSPSLastInvalidId = -1;
  */
  pCtx->iFeedbackNalRefIdc = -1; //initialize
  pCtx->pLastDecPicInfo->iPrevPicOrderCntMsb = 0;
  pCtx->pLastDecPicInfo->iPrevPicOrderCntLsb = 0;

}

/*
* fill data fields in SPS and PPS default for decoder context
*/
void WelsDecoderSpsPpsDefaults (SWelsDecoderSpsPpsCTX& sSpsPpsCtx) {
  sSpsPpsCtx.bSpsExistAheadFlag = false;
  sSpsPpsCtx.bSubspsExistAheadFlag = false;
  sSpsPpsCtx.bPpsExistAheadFlag = false;
  sSpsPpsCtx.bAvcBasedFlag = true;
  sSpsPpsCtx.iSpsErrorIgnored = 0;
  sSpsPpsCtx.iSubSpsErrorIgnored = 0;
  sSpsPpsCtx.iPpsErrorIgnored = 0;
  sSpsPpsCtx.iPPSInvalidNum = 0;
  sSpsPpsCtx.iPPSLastInvalidId = -1;
  sSpsPpsCtx.iSPSInvalidNum = 0;
  sSpsPpsCtx.iSPSLastInvalidId = -1;
  sSpsPpsCtx.iSubSPSInvalidNum = 0;
  sSpsPpsCtx.iSubSPSLastInvalidId = -1;
  sSpsPpsCtx.iSeqId = -1;
}

/*
* fill last decoded picture info
*/
void WelsDecoderLastDecPicInfoDefaults (SWelsLastDecPicInfo& sLastDecPicInfo) {
  sLastDecPicInfo.iPrevPicOrderCntMsb = 0;
  sLastDecPicInfo.iPrevPicOrderCntLsb = 0;
  sLastDecPicInfo.pPreviousDecodedPictureInDpb = NULL;
  sLastDecPicInfo.iPrevFrameNum = -1;
  sLastDecPicInfo.bLastHasMmco5 = false;
  sLastDecPicInfo.uiDecodingTimeStamp = 0;
}

/*!
* \brief   copy SpsPps from one Ctx to another ctx for threaded code
*/
void CopySpsPps (PWelsDecoderContext pFromCtx, PWelsDecoderContext pToCtx) {
  pToCtx->sSpsPpsCtx = pFromCtx->sSpsPpsCtx;
  PAccessUnit pFromCurAu = pFromCtx->pAccessUnitList;
  PSps pTmpLayerSps[MAX_LAYER_NUM];
  for (int i = 0; i < MAX_LAYER_NUM; i++) {
    pTmpLayerSps[i] = NULL;
  }
  // track the layer sps for the current au
  for (unsigned int i = pFromCurAu->uiStartPos; i <= pFromCurAu->uiEndPos; i++) {
    uint32_t uiDid = pFromCurAu->pNalUnitsList[i]->sNalHeaderExt.uiDependencyId;
    pTmpLayerSps[uiDid] = pFromCurAu->pNalUnitsList[i]->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader.pSps;
    for (unsigned int j = 0; j < MAX_SPS_COUNT + 1; ++j) {
      if (&pFromCtx->sSpsPpsCtx.sSpsBuffer[j] == pTmpLayerSps[uiDid]) {
        pTmpLayerSps[uiDid] = &pToCtx->sSpsPpsCtx.sSpsBuffer[j];
        break;
      }
    }
  }
  for (int i = 0; i < MAX_LAYER_NUM; i++) {
    if (pTmpLayerSps[i] != NULL) {
      pToCtx->sSpsPpsCtx.pActiveLayerSps[i] = pTmpLayerSps[i];
    }
  }
}

/*
 *  destory_mb_blocks
 */

/*
 *  get size of reference picture list in target layer incoming, = (iNumRefFrames
 */
static inline int32_t GetTargetRefListSize (PWelsDecoderContext pCtx) {
  int32_t iNumRefFrames = 0;
  // +2 for EC MV Copy buffer exchange
  if ((pCtx == NULL) || (pCtx->pSps == NULL)) {
    iNumRefFrames = MAX_REF_PIC_COUNT + 2;
  } else {
    iNumRefFrames = pCtx->pSps->iNumRefFrames + 2;
    int32_t  iThreadCount = GetThreadCount (pCtx);
    if (iThreadCount > 1) {
      iNumRefFrames = MAX_REF_PIC_COUNT;
    }
  }

#ifdef LONG_TERM_REF
  //pic_queue size minimum set 2
  if (iNumRefFrames < 2) {
    iNumRefFrames = 2;
  }
#endif

  return iNumRefFrames;
}

/*
 *  request memory blocks for decoder avc part
 */
int32_t WelsRequestMem (PWelsDecoderContext pCtx, const int32_t kiMbWidth, const int32_t kiMbHeight,
                        bool& bReallocFlag) {
  const int32_t kiPicWidth      = kiMbWidth << 4;
  const int32_t kiPicHeight     = kiMbHeight << 4;
  int32_t iErr = ERR_NONE;

  int32_t iPicQueueSize         = 0;    // adaptive size of picture queue, = (pSps->iNumRefFrames x 2)
  bReallocFlag                  = false;
  bool  bNeedChangePicQueue     = true;
  CMemoryAlign* pMa = pCtx->pMemAlign;

  WELS_VERIFY_RETURN_IF (ERR_INFO_INVALID_PARAM, (NULL == pCtx || kiPicWidth <= 0 || kiPicHeight <= 0))

  // Fixed the issue about different gop size over last, 5/17/2010
  // get picture queue size currently
  iPicQueueSize = GetTargetRefListSize (pCtx);  // adaptive size of picture queue, = (pSps->iNumRefFrames x 2)
  pCtx->iPicQueueNumber = iPicQueueSize;
  if (pCtx->pPicBuff != NULL
      && pCtx->pPicBuff->iCapacity ==
      iPicQueueSize) // comparing current picture queue size requested and previous allocation picture queue
    bNeedChangePicQueue = false;
  // HD based pic buffer need consider memory size consumed when switch from 720p to other lower size
  WELS_VERIFY_RETURN_IF (ERR_NONE, pCtx->bHaveGotMemory && (kiPicWidth == pCtx->iImgWidthInPixel
                         && kiPicHeight == pCtx->iImgHeightInPixel) && (!bNeedChangePicQueue)) // have same scaled buffer

  // sync update pRefList
  if (GetThreadCount (pCtx) <= 1) {
    WelsResetRefPic (pCtx); // added to sync update ref list due to pictures are free
  }

  if (pCtx->bHaveGotMemory && (kiPicWidth == pCtx->iImgWidthInPixel && kiPicHeight == pCtx->iImgHeightInPixel)
      && pCtx->pPicBuff != NULL && pCtx->pPicBuff->iCapacity != iPicQueueSize) {
    // currently only active for LIST_0 due to have no B frames
    // Actually just need one memory allocation for the PicBuff. While it needs two pointer list (LIST_0 and LIST_1).
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_INFO,
             "WelsRequestMem(): memory re-alloc for no resolution change (size = %d * %d), ref list size change from %d to %d",
             kiPicWidth, kiPicHeight, pCtx->pPicBuff->iCapacity, iPicQueueSize);
    if (pCtx->pPicBuff->iCapacity < iPicQueueSize) {
      iErr = IncreasePicBuff (pCtx, &pCtx->pPicBuff, pCtx->pPicBuff->iCapacity, kiPicWidth, kiPicHeight,
                              iPicQueueSize);
    } else {
      iErr = DecreasePicBuff (pCtx, &pCtx->pPicBuff, pCtx->pPicBuff->iCapacity, kiPicWidth, kiPicHeight,
                              iPicQueueSize);
    }
  } else {
    if (pCtx->bHaveGotMemory)
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_INFO,
               "WelsRequestMem(): memory re-alloc for resolution change, size change from %d * %d to %d * %d, ref list size change from %d to %d",
               pCtx->iImgWidthInPixel, pCtx->iImgHeightInPixel, kiPicWidth, kiPicHeight, pCtx->pPicBuff->iCapacity,
               iPicQueueSize);
    else
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_INFO, "WelsRequestMem(): memory alloc size = %d * %d, ref list size = %d",
               kiPicWidth, kiPicHeight, iPicQueueSize);
    // for Recycled_Pic_Queue
    PPicBuff* ppPic = &pCtx->pPicBuff;
    if (NULL != ppPic && NULL != *ppPic) {
      DestroyPicBuff (pCtx, ppPic, pMa);
    }


    pCtx->pLastDecPicInfo->pPreviousDecodedPictureInDpb = NULL;

    // currently only active for LIST_0 due to have no B frames
    iErr = CreatePicBuff (pCtx, &pCtx->pPicBuff, iPicQueueSize, kiPicWidth, kiPicHeight);
  }

  if (iErr != ERR_NONE)
    return iErr;


  pCtx->iImgWidthInPixel    = kiPicWidth;   // target width of image to be reconstruted while decoding
  pCtx->iImgHeightInPixel   = kiPicHeight;  // target height of image to be reconstruted while decoding

  pCtx->bHaveGotMemory      = true;         // global memory for decoder context related is requested
  pCtx->pDec                = NULL;         // need prefetch a new pic due to spatial size changed

  if (pCtx->pCabacDecEngine == NULL)
    pCtx->pCabacDecEngine = (SWelsCabacDecEngine*) pMa->WelsMallocz (sizeof (SWelsCabacDecEngine), "pCtx->pCabacDecEngine");
  WELS_VERIFY_RETURN_IF (ERR_INFO_OUT_OF_MEMORY, (NULL == pCtx->pCabacDecEngine))

  bReallocFlag              = true;         // memory re-allocation successfully finished
  return ERR_NONE;
}

/*
 *  free memory dynamically allocated during decoder
 */
void WelsFreeDynamicMemory (PWelsDecoderContext pCtx) {

  CMemoryAlign* pMa = pCtx->pMemAlign;

  //free dq layer memory
  UninitialDqLayersContext (pCtx);

  //free FMO memory
  ResetFmoList (pCtx);

  //free ref-pic list & picture memory
  WelsResetRefPic (pCtx);

  PPicBuff* pPicBuff = &pCtx->pPicBuff;
  if (NULL != pPicBuff && NULL != *pPicBuff) {
    DestroyPicBuff (pCtx, pPicBuff, pMa);
  }
  if (GetThreadCount (pCtx) > 1) {
    //prevent from double destruction of PPicBuff
    PWelsDecoderThreadCTX pThreadCtx = (PWelsDecoderThreadCTX) (pCtx->pThreadCtx);
    int32_t threadCount = pThreadCtx->sThreadInfo.uiThrMaxNum;
    int32_t  id = pThreadCtx->sThreadInfo.uiThrNum;
    for (int32_t i = 0; i < threadCount; ++i) {
      if (pThreadCtx[i - id].pCtx != NULL) {
        pThreadCtx[i - id].pCtx->pPicBuff = NULL;
      }
    }
  }

  if (pCtx->pTempDec) {
    FreePicture (pCtx->pTempDec, pCtx->pMemAlign);
    pCtx->pTempDec = NULL;
  }

  // added for safe memory
  pCtx->iImgWidthInPixel  = 0;
  pCtx->iImgHeightInPixel = 0;
  pCtx->iLastImgWidthInPixel  = 0;
  pCtx->iLastImgHeightInPixel = 0;
  pCtx->bFreezeOutput = true;
  pCtx->bHaveGotMemory = false;

  //free CABAC memory
  pMa->WelsFree (pCtx->pCabacDecEngine, "pCtx->pCabacDecEngine");
}

/*!
 * \brief   Open decoder
 */
int32_t WelsOpenDecoder (PWelsDecoderContext pCtx, SLogContext* pLogCtx) {
  int iRet = ERR_NONE;
  // function pointers
  InitDecFuncs (pCtx, pCtx->uiCpuFlag);

  // vlc tables
  InitVlcTable (pCtx->pVlcTable);

  // static memory
  iRet = WelsInitStaticMemory (pCtx);
  if (ERR_NONE != iRet) {
    pCtx->iErrorCode |= dsOutOfMemory;
    WelsLog (pLogCtx, WELS_LOG_ERROR, "WelsInitStaticMemory() failed in WelsOpenDecoder().");
    return iRet;
  }

#ifdef LONG_TERM_REF
  pCtx->bParamSetsLostFlag = true;
#else
  pCtx->bReferenceLostAtT0Flag = true; // should be true to waiting IDR at incoming AU bits following, 6/4/2010
#endif //LONG_TERM_REF
  pCtx->bNewSeqBegin = true;
  pCtx->bPrintFrameErrorTraceFlag = true;
  pCtx->iIgnoredErrorInfoPacketCount = 0;
  pCtx->bFrameFinish = true;
  return iRet;
}

/*!
 * \brief   Close decoder
 */
void WelsCloseDecoder (PWelsDecoderContext pCtx) {
  WelsFreeDynamicMemory (pCtx);

  WelsFreeStaticMemory (pCtx);

#ifdef LONG_TERM_REF
  pCtx->bParamSetsLostFlag       = false;
#else
  pCtx->bReferenceLostAtT0Flag = false;
#endif
  pCtx->bNewSeqBegin = false;
  pCtx->bPrintFrameErrorTraceFlag = false;
}

/*!
 * \brief   configure decoder parameters
 */
int32_t DecoderConfigParam (PWelsDecoderContext pCtx, const SDecodingParam* kpParam) {
  if (NULL == pCtx || NULL == kpParam)
    return ERR_INFO_INVALID_PARAM;

  memcpy (pCtx->pParam, kpParam, sizeof (SDecodingParam));
  if ((pCtx->pParam->eEcActiveIdc > ERROR_CON_SLICE_MV_COPY_CROSS_IDR_FREEZE_RES_CHANGE)
      || (pCtx->pParam->eEcActiveIdc < ERROR_CON_DISABLE)) {
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING,
             "eErrorConMethod (%d) not in range: (%d - %d). Set as default value: (%d).", pCtx->pParam->eEcActiveIdc,
             ERROR_CON_DISABLE, ERROR_CON_SLICE_MV_COPY_CROSS_IDR_FREEZE_RES_CHANGE,
             ERROR_CON_SLICE_MV_COPY_CROSS_IDR_FREEZE_RES_CHANGE);
    pCtx->pParam->eEcActiveIdc = ERROR_CON_SLICE_MV_COPY_CROSS_IDR_FREEZE_RES_CHANGE;
  }

  if (pCtx->pParam->bParseOnly) //parse only, disable EC method
    pCtx->pParam->eEcActiveIdc = ERROR_CON_DISABLE;
  InitErrorCon (pCtx);

  if (VIDEO_BITSTREAM_SVC == pCtx->pParam->sVideoProperty.eVideoBsType ||
      VIDEO_BITSTREAM_AVC == pCtx->pParam->sVideoProperty.eVideoBsType) {
    pCtx->eVideoType = pCtx->pParam->sVideoProperty.eVideoBsType;
  } else {
    pCtx->eVideoType = VIDEO_BITSTREAM_DEFAULT;
  }

  WelsLog (& (pCtx->sLogCtx), WELS_LOG_INFO, "eVideoType: %d", pCtx->eVideoType);

  return ERR_NONE;
}

/*!
 *************************************************************************************
 * \brief   Initialize Wels decoder parameters and memory
 *
 * \param   pCtx input context to be initialized at first stage
 *
 * \return  0 - successed
 * \return  1 - failed
 *
 * \note    N/A
 *************************************************************************************
 */
int32_t WelsInitDecoder (PWelsDecoderContext pCtx, SLogContext* pLogCtx) {
  if (pCtx == NULL) {
    return ERR_INFO_INVALID_PTR;
  }

  // open decoder
  return WelsOpenDecoder (pCtx, pLogCtx);
}

/*!
 *************************************************************************************
 * \brief   Uninitialize Wels decoder parameters and memory
 *
 * \param   pCtx input context to be uninitialized at release stage
 *
 * \return  NONE
 *
 * \note    N/A
 *************************************************************************************
 */
void WelsEndDecoder (PWelsDecoderContext pCtx) {
  // close decoder
  WelsCloseDecoder (pCtx);
}

void GetVclNalTemporalId (PWelsDecoderContext pCtx) {
  PAccessUnit pAccessUnit = pCtx->pAccessUnitList;
  int32_t idx = pAccessUnit->uiStartPos;

  pCtx->iFeedbackVclNalInAu = FEEDBACK_VCL_NAL;
  pCtx->iFeedbackTidInAu    = pAccessUnit->pNalUnitsList[idx]->sNalHeaderExt.uiTemporalId;
  pCtx->iFeedbackNalRefIdc  = pAccessUnit->pNalUnitsList[idx]->sNalHeaderExt.sNalUnitHeader.uiNalRefIdc;
}

/*!
 *************************************************************************************
 * \brief   First entrance to decoding core interface.
 *
 * \param   pCtx            decoder context
 * \param   pBufBs          bit streaming buffer
 * \param   kBsLen          size in bytes length of bit streaming buffer input
 * \param   ppDst           picture payload data to be output
 * \param   pDstBufInfo     buf information of ouput data
 *
 * \return  0 - successed
 * \return  1 - failed
 *
 * \note    N/A
 *************************************************************************************
 */
int32_t WelsDecodeBs (PWelsDecoderContext pCtx, const uint8_t* kpBsBuf, const int32_t kiBsLen,
                      uint8_t** ppDst, SBufferInfo* pDstBufInfo, SParserBsInfo* pDstBsInfo) {
  if (!pCtx->bEndOfStreamFlag) {
    SDataBuffer* pRawData   = &pCtx->sRawData;
    SDataBuffer* pSavedData = NULL;

    int32_t iSrcIdx        = 0; //the index of source bit-stream till now after parsing one or more NALs
    int32_t iSrcConsumed   = 0; // consumed bit count of source bs
    int32_t iDstIdx        = 0; //the size of current NAL after 0x03 removal and 00 00 01 removal
    int32_t iSrcLength     = 0; //the total size of current AU or NAL
    int32_t iRet = 0;
    int32_t iConsumedBytes = 0;
    int32_t iOffset        = 0;

    uint8_t* pSrcNal       = NULL;
    uint8_t* pDstNal       = NULL;
    uint8_t* pNalPayload   = NULL;


    if (NULL == DetectStartCodePrefix (kpBsBuf, &iOffset,
                                       kiBsLen)) {  //CAN'T find the 00 00 01 start prefix from the source buffer
      pCtx->iErrorCode |= dsBitstreamError;
      return dsBitstreamError;
    }

    pSrcNal    = const_cast<uint8_t*> (kpBsBuf) + iOffset;
    iSrcLength = kiBsLen - iOffset;

    if ((kiBsLen + 4) > (pRawData->pEnd - pRawData->pCurPos)) {
      pRawData->pCurPos = pRawData->pHead;
    }

    if (pCtx->pParam->bParseOnly) {
      pSavedData = &pCtx->sSavedData;
      if ((kiBsLen + 4) > (pSavedData->pEnd - pSavedData->pCurPos)) {
        pSavedData->pCurPos = pSavedData->pHead;
      }
    }
    //copy raw data from source buffer (application) to raw data buffer (codec inside)
    //0x03 removal and extract all of NAL Unit from current raw data
    pDstNal = pRawData->pCurPos;

    bool bNalStartBytes = false;

    while (iSrcConsumed < iSrcLength) {
      if ((2 + iSrcConsumed < iSrcLength) && (0 == LD16 (pSrcNal + iSrcIdx)) && (pSrcNal[2 + iSrcIdx] <= 0x03)) {
        if (bNalStartBytes && (pSrcNal[2 + iSrcIdx] != 0x00 && pSrcNal[2 + iSrcIdx] != 0x01)) {
          pCtx->iErrorCode |= dsBitstreamError;
          return pCtx->iErrorCode;
        }

        if (pSrcNal[2 + iSrcIdx] == 0x02) {
          pCtx->iErrorCode |= dsBitstreamError;
          return pCtx->iErrorCode;
        } else if (pSrcNal[2 + iSrcIdx] == 0x00) {
          pDstNal[iDstIdx++] = pSrcNal[iSrcIdx++];
          iSrcConsumed++;
          bNalStartBytes = true;
        } else if (pSrcNal[2 + iSrcIdx] == 0x03) {
          if ((3 + iSrcConsumed < iSrcLength) && pSrcNal[3 + iSrcIdx] > 0x03) {
            pCtx->iErrorCode |= dsBitstreamError;
            return pCtx->iErrorCode;
          } else {
            ST16 (pDstNal + iDstIdx, 0);
            iDstIdx      += 2;
            iSrcIdx      += 3;
            iSrcConsumed += 3;
          }
        } else { // 0x01
          bNalStartBytes = false;

          iConsumedBytes = 0;
          pDstNal[iDstIdx] = pDstNal[iDstIdx + 1] = pDstNal[iDstIdx + 2] = pDstNal[iDstIdx + 3] =
                               0; // set 4 reserved bytes to zero
          pNalPayload = ParseNalHeader (pCtx, &pCtx->sCurNalHead, pDstNal, iDstIdx, pSrcNal - 3, iSrcIdx + 3, &iConsumedBytes);
          if (pNalPayload) { //parse correct
            if (IS_PARAM_SETS_NALS (pCtx->sCurNalHead.eNalUnitType)) {
              iRet = ParseNonVclNal (pCtx, pNalPayload, iDstIdx - iConsumedBytes, pSrcNal - 3, iSrcIdx + 3);
            }
            CheckAndFinishLastPic (pCtx, ppDst, pDstBufInfo);
            if (pCtx->bAuReadyFlag && pCtx->pAccessUnitList->uiAvailUnitsNum != 0) {
              if (GetThreadCount (pCtx) <= 1) {
                ConstructAccessUnit (pCtx, ppDst, pDstBufInfo);
              } else {
                pCtx->pAccessUnitList->uiAvailUnitsNum = 1;
              }
            }
          }
          DecodeFinishUpdate (pCtx);

          if ((dsOutOfMemory | dsNoParamSets) & pCtx->iErrorCode) {
#ifdef LONG_TERM_REF
            pCtx->bParamSetsLostFlag = true;
#else
            pCtx->bReferenceLostAtT0Flag = true;
#endif
            if (dsOutOfMemory & pCtx->iErrorCode) {
              return pCtx->iErrorCode;
            }
          }
          if (iRet) {
            iRet = 0;
            if (dsNoParamSets & pCtx->iErrorCode) {
#ifdef LONG_TERM_REF
              pCtx->bParamSetsLostFlag = true;
#else
              pCtx->bReferenceLostAtT0Flag = true;
#endif
            }
            return pCtx->iErrorCode;
          }

          pDstNal += (iDstIdx + 4); //init, increase 4 reserved zero bytes, used to store the next NAL
          if ((iSrcLength - iSrcConsumed + 4) > (pRawData->pEnd - pDstNal)) {
            pDstNal = pRawData->pCurPos = pRawData->pHead;
          } else {
            pRawData->pCurPos = pDstNal;
          }

          pSrcNal += iSrcIdx + 3;
          iSrcConsumed += 3;
          iSrcIdx = 0;
          iDstIdx  = 0; //reset 0, used to statistic the length of next NAL
        }
        continue;
      }
      pDstNal[iDstIdx++] = pSrcNal[iSrcIdx++];
      iSrcConsumed++;
    }

    //last NAL decoding

    iConsumedBytes = 0;
    pDstNal[iDstIdx] = pDstNal[iDstIdx + 1] = pDstNal[iDstIdx + 2] = pDstNal[iDstIdx + 3] =
                         0; // set 4 reserved bytes to zero
    pRawData->pCurPos = pDstNal + iDstIdx + 4; //init, increase 4 reserved zero bytes, used to store the next NAL
    pNalPayload = ParseNalHeader (pCtx, &pCtx->sCurNalHead, pDstNal, iDstIdx, pSrcNal - 3, iSrcIdx + 3, &iConsumedBytes);
    if (pNalPayload) { //parse correct
      if (IS_PARAM_SETS_NALS (pCtx->sCurNalHead.eNalUnitType)) {
        iRet = ParseNonVclNal (pCtx, pNalPayload, iDstIdx - iConsumedBytes, pSrcNal - 3, iSrcIdx + 3);
      }
      if (GetThreadCount (pCtx) <= 1) {
        CheckAndFinishLastPic (pCtx, ppDst, pDstBufInfo);
      }
      if (pCtx->bAuReadyFlag && pCtx->pAccessUnitList->uiAvailUnitsNum != 0) {
        if (GetThreadCount (pCtx) <= 1) {
          ConstructAccessUnit (pCtx, ppDst, pDstBufInfo);
        } else {
          pCtx->pAccessUnitList->uiAvailUnitsNum = 1;
        }
      }
    }
    DecodeFinishUpdate (pCtx);

    if ((dsOutOfMemory | dsNoParamSets) & pCtx->iErrorCode) {
#ifdef LONG_TERM_REF
      pCtx->bParamSetsLostFlag = true;
#else
      pCtx->bReferenceLostAtT0Flag = true;
#endif
      return pCtx->iErrorCode;
    }
    if (iRet) {
      iRet = 0;
      if (dsNoParamSets & pCtx->iErrorCode) {
#ifdef LONG_TERM_REF
        pCtx->bParamSetsLostFlag = true;
#else
        pCtx->bReferenceLostAtT0Flag = true;
#endif
      }
      return pCtx->iErrorCode;
    }
  } else { /* no supplementary picture payload input, but stored a picture */
    PAccessUnit pCurAu =
      pCtx->pAccessUnitList; // current access unit, it will never point to NULL after decode's successful initialization

    if (pCurAu->uiAvailUnitsNum == 0) {
      return pCtx->iErrorCode;
    } else {
      pCtx->pAccessUnitList->uiEndPos = pCtx->pAccessUnitList->uiAvailUnitsNum - 1;

      ConstructAccessUnit (pCtx, ppDst, pDstBufInfo);
    }
    DecodeFinishUpdate (pCtx);

    if ((dsOutOfMemory | dsNoParamSets) & pCtx->iErrorCode) {
#ifdef LONG_TERM_REF
      pCtx->bParamSetsLostFlag = true;
#else
      pCtx->bReferenceLostAtT0Flag = true;
#endif
      return pCtx->iErrorCode;
    }
  }

  return pCtx->iErrorCode;
}

/*!
 * \brief   make sure synchonozization picture resolution (get from slice header) among different parts (i.e, memory related and so on)
 *          over decoder internal
 * ( MB coordinate and parts of data within decoder context structure )
 * \param   pCtx        Wels decoder context
 * \param   iMbWidth    MB width
 * \pram    iMbHeight   MB height
 * \return  0 - successful; none 0 - something wrong
 */
int32_t SyncPictureResolutionExt (PWelsDecoderContext pCtx, const int32_t kiMbWidth, const int32_t kiMbHeight) {
  int32_t iErr = ERR_NONE;
  const int32_t kiPicWidth    = kiMbWidth << 4;
  const int32_t kiPicHeight   = kiMbHeight << 4;
  //fix Bugzilla Bug1479656 reallocate temp dec picture
  if (pCtx->pTempDec != NULL && (pCtx->pTempDec->iWidthInPixel != kiPicWidth
                                 || pCtx->pTempDec->iHeightInPixel != kiPicHeight)) {
    FreePicture (pCtx->pTempDec, pCtx->pMemAlign);
    pCtx->pTempDec = AllocPicture (pCtx, pCtx->pSps->iMbWidth << 4, pCtx->pSps->iMbHeight << 4);
  }
  bool bReallocFlag = false;
  iErr = WelsRequestMem (pCtx, kiMbWidth, kiMbHeight, bReallocFlag); // common memory used
  if (ERR_NONE != iErr) {
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_ERROR,
             "SyncPictureResolutionExt()::WelsRequestMem--buffer allocated failure.");
    pCtx->iErrorCode |= dsOutOfMemory;
    return iErr;
  }

  iErr = InitialDqLayersContext (pCtx, kiPicWidth, kiPicHeight);
  if (ERR_NONE != iErr) {
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_ERROR,
             "SyncPictureResolutionExt()::InitialDqLayersContext--buffer allocated failure.");
    pCtx->iErrorCode |= dsOutOfMemory;
  }
#if defined(MEMORY_MONITOR)
  if (bReallocFlag) {
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_INFO, "SyncPictureResolutionExt(), overall memory usage: %llu bytes",
             static_cast<unsigned long long> (sizeof (SWelsDecoderContext) + pCtx->pMemAlign->WelsGetMemoryUsage()));
  }
#endif//MEMORY_MONITOR
  return iErr;
}

void InitDecFuncs (PWelsDecoderContext pCtx, uint32_t uiCpuFlag) {
  WelsBlockFuncInit (&pCtx->sBlockFunc, uiCpuFlag);
  InitPredFunc (pCtx, uiCpuFlag);
  InitMcFunc (& (pCtx->sMcFunc), uiCpuFlag);
  InitExpandPictureFunc (& (pCtx->sExpandPicFunc), uiCpuFlag);
  DeblockingInit (&pCtx->sDeblockingFunc, uiCpuFlag);
}

namespace {

template<void pfIdctResAddPred (uint8_t* pPred, int32_t iStride, int16_t* pRs)>
void IdctFourResAddPred_ (uint8_t* pPred, int32_t iStride, int16_t* pRs, const int8_t* pNzc) {
  if (pNzc[0] || pRs[0 * 16])
    pfIdctResAddPred (pPred + 0 * iStride + 0, iStride, pRs + 0 * 16);
  if (pNzc[1] || pRs[1 * 16])
    pfIdctResAddPred (pPred + 0 * iStride + 4, iStride, pRs + 1 * 16);
  if (pNzc[4] || pRs[2 * 16])
    pfIdctResAddPred (pPred + 4 * iStride + 0, iStride, pRs + 2 * 16);
  if (pNzc[5] || pRs[3 * 16])
    pfIdctResAddPred (pPred + 4 * iStride + 4, iStride, pRs + 3 * 16);
}

} // anon ns

void InitPredFunc (PWelsDecoderContext pCtx, uint32_t uiCpuFlag) {
  pCtx->pGetI16x16LumaPredFunc[I16_PRED_V     ] = WelsI16x16LumaPredV_c;
  pCtx->pGetI16x16LumaPredFunc[I16_PRED_H     ] = WelsI16x16LumaPredH_c;
  pCtx->pGetI16x16LumaPredFunc[I16_PRED_DC    ] = WelsI16x16LumaPredDc_c;
  pCtx->pGetI16x16LumaPredFunc[I16_PRED_P     ] = WelsI16x16LumaPredPlane_c;
  pCtx->pGetI16x16LumaPredFunc[I16_PRED_DC_L  ] = WelsI16x16LumaPredDcLeft_c;
  pCtx->pGetI16x16LumaPredFunc[I16_PRED_DC_T  ] = WelsI16x16LumaPredDcTop_c;
  pCtx->pGetI16x16LumaPredFunc[I16_PRED_DC_128] = WelsI16x16LumaPredDcNA_c;

  pCtx->pGetI4x4LumaPredFunc[I4_PRED_V     ] = WelsI4x4LumaPredV_c;
  pCtx->pGetI4x4LumaPredFunc[I4_PRED_H     ] = WelsI4x4LumaPredH_c;
  pCtx->pGetI4x4LumaPredFunc[I4_PRED_DC    ] = WelsI4x4LumaPredDc_c;
  pCtx->pGetI4x4LumaPredFunc[I4_PRED_DC_L  ] = WelsI4x4LumaPredDcLeft_c;
  pCtx->pGetI4x4LumaPredFunc[I4_PRED_DC_T  ] = WelsI4x4LumaPredDcTop_c;
  pCtx->pGetI4x4LumaPredFunc[I4_PRED_DC_128] = WelsI4x4LumaPredDcNA_c;
  pCtx->pGetI4x4LumaPredFunc[I4_PRED_DDL    ] = WelsI4x4LumaPredDDL_c;
  pCtx->pGetI4x4LumaPredFunc[I4_PRED_DDL_TOP] = WelsI4x4LumaPredDDLTop_c;
  pCtx->pGetI4x4LumaPredFunc[I4_PRED_DDR    ] = WelsI4x4LumaPredDDR_c;
  pCtx->pGetI4x4LumaPredFunc[I4_PRED_VL    ] = WelsI4x4LumaPredVL_c;
  pCtx->pGetI4x4LumaPredFunc[I4_PRED_VL_TOP] = WelsI4x4LumaPredVLTop_c;
  pCtx->pGetI4x4LumaPredFunc[I4_PRED_VR    ] = WelsI4x4LumaPredVR_c;
  pCtx->pGetI4x4LumaPredFunc[I4_PRED_HU    ] = WelsI4x4LumaPredHU_c;
  pCtx->pGetI4x4LumaPredFunc[I4_PRED_HD    ] = WelsI4x4LumaPredHD_c;

  pCtx->pGetI8x8LumaPredFunc[I4_PRED_V     ] = WelsI8x8LumaPredV_c;
  pCtx->pGetI8x8LumaPredFunc[I4_PRED_H     ] = WelsI8x8LumaPredH_c;
  pCtx->pGetI8x8LumaPredFunc[I4_PRED_DC    ] = WelsI8x8LumaPredDc_c;
  pCtx->pGetI8x8LumaPredFunc[I4_PRED_DC_L  ] = WelsI8x8LumaPredDcLeft_c;
  pCtx->pGetI8x8LumaPredFunc[I4_PRED_DC_T  ] = WelsI8x8LumaPredDcTop_c;
  pCtx->pGetI8x8LumaPredFunc[I4_PRED_DC_128] = WelsI8x8LumaPredDcNA_c;
  pCtx->pGetI8x8LumaPredFunc[I4_PRED_DDL    ] = WelsI8x8LumaPredDDL_c;
  pCtx->pGetI8x8LumaPredFunc[I4_PRED_DDL_TOP] = WelsI8x8LumaPredDDLTop_c;
  pCtx->pGetI8x8LumaPredFunc[I4_PRED_DDR    ] = WelsI8x8LumaPredDDR_c;
  pCtx->pGetI8x8LumaPredFunc[I4_PRED_VL    ] = WelsI8x8LumaPredVL_c;
  pCtx->pGetI8x8LumaPredFunc[I4_PRED_VL_TOP] = WelsI8x8LumaPredVLTop_c;
  pCtx->pGetI8x8LumaPredFunc[I4_PRED_VR    ] = WelsI8x8LumaPredVR_c;
  pCtx->pGetI8x8LumaPredFunc[I4_PRED_HU    ] = WelsI8x8LumaPredHU_c;
  pCtx->pGetI8x8LumaPredFunc[I4_PRED_HD    ] = WelsI8x8LumaPredHD_c;

  pCtx->pGetIChromaPredFunc[C_PRED_DC    ] = WelsIChromaPredDc_c;
  pCtx->pGetIChromaPredFunc[C_PRED_H     ] = WelsIChromaPredH_c;
  pCtx->pGetIChromaPredFunc[C_PRED_V     ] = WelsIChromaPredV_c;
  pCtx->pGetIChromaPredFunc[C_PRED_P     ] = WelsIChromaPredPlane_c;
  pCtx->pGetIChromaPredFunc[C_PRED_DC_L  ] = WelsIChromaPredDcLeft_c;
  pCtx->pGetIChromaPredFunc[C_PRED_DC_T  ] = WelsIChromaPredDcTop_c;
  pCtx->pGetIChromaPredFunc[C_PRED_DC_128] = WelsIChromaPredDcNA_c;

  pCtx->pIdctResAddPredFunc     = IdctResAddPred_c;
  pCtx->pIdctFourResAddPredFunc = IdctFourResAddPred_<IdctResAddPred_c>;

  pCtx->pIdctResAddPredFunc8x8  = IdctResAddPred8x8_c;

#if defined(HAVE_NEON)
  if (uiCpuFlag & WELS_CPU_NEON) {
    pCtx->pIdctResAddPredFunc   = IdctResAddPred_neon;
    pCtx->pIdctFourResAddPredFunc = IdctFourResAddPred_<IdctResAddPred_neon>;

    pCtx->pGetI16x16LumaPredFunc[I16_PRED_DC] = WelsDecoderI16x16LumaPredDc_neon;
    pCtx->pGetI16x16LumaPredFunc[I16_PRED_P]  = WelsDecoderI16x16LumaPredPlane_neon;
    pCtx->pGetI16x16LumaPredFunc[I16_PRED_H]  = WelsDecoderI16x16LumaPredH_neon;
    pCtx->pGetI16x16LumaPredFunc[I16_PRED_V]  = WelsDecoderI16x16LumaPredV_neon;

    pCtx->pGetI4x4LumaPredFunc[I4_PRED_V    ] = WelsDecoderI4x4LumaPredV_neon;
    pCtx->pGetI4x4LumaPredFunc[I4_PRED_H    ] = WelsDecoderI4x4LumaPredH_neon;
    pCtx->pGetI4x4LumaPredFunc[I4_PRED_DDL  ] = WelsDecoderI4x4LumaPredDDL_neon;
    pCtx->pGetI4x4LumaPredFunc[I4_PRED_DDR  ] = WelsDecoderI4x4LumaPredDDR_neon;
    pCtx->pGetI4x4LumaPredFunc[I4_PRED_VL   ] = WelsDecoderI4x4LumaPredVL_neon;
    pCtx->pGetI4x4LumaPredFunc[I4_PRED_VR   ] = WelsDecoderI4x4LumaPredVR_neon;
    pCtx->pGetI4x4LumaPredFunc[I4_PRED_HU   ] = WelsDecoderI4x4LumaPredHU_neon;
    pCtx->pGetI4x4LumaPredFunc[I4_PRED_HD   ] = WelsDecoderI4x4LumaPredHD_neon;

    pCtx->pGetIChromaPredFunc[C_PRED_H]       = WelsDecoderIChromaPredH_neon;
    pCtx->pGetIChromaPredFunc[C_PRED_V]       = WelsDecoderIChromaPredV_neon;
    pCtx->pGetIChromaPredFunc[C_PRED_P ]      = WelsDecoderIChromaPredPlane_neon;
    pCtx->pGetIChromaPredFunc[C_PRED_DC]      = WelsDecoderIChromaPredDc_neon;
  }
#endif//HAVE_NEON

#if defined(HAVE_NEON_AARCH64)
  if (uiCpuFlag & WELS_CPU_NEON) {
    pCtx->pIdctResAddPredFunc   = IdctResAddPred_AArch64_neon;
    pCtx->pIdctFourResAddPredFunc = IdctFourResAddPred_<IdctResAddPred_AArch64_neon>;

    pCtx->pGetI16x16LumaPredFunc[I16_PRED_DC] = WelsDecoderI16x16LumaPredDc_AArch64_neon;
    pCtx->pGetI16x16LumaPredFunc[I16_PRED_P]  = WelsDecoderI16x16LumaPredPlane_AArch64_neon;
    pCtx->pGetI16x16LumaPredFunc[I16_PRED_H]  = WelsDecoderI16x16LumaPredH_AArch64_neon;
    pCtx->pGetI16x16LumaPredFunc[I16_PRED_V]  = WelsDecoderI16x16LumaPredV_AArch64_neon;
    pCtx->pGetI16x16LumaPredFunc[I16_PRED_DC_L]  = WelsDecoderI16x16LumaPredDcLeft_AArch64_neon;
    pCtx->pGetI16x16LumaPredFunc[I16_PRED_DC_T]  = WelsDecoderI16x16LumaPredDcTop_AArch64_neon;

    pCtx->pGetI4x4LumaPredFunc[I4_PRED_H    ] = WelsDecoderI4x4LumaPredH_AArch64_neon;
    pCtx->pGetI4x4LumaPredFunc[I4_PRED_DDL  ] = WelsDecoderI4x4LumaPredDDL_AArch64_neon;
    pCtx->pGetI4x4LumaPredFunc[I4_PRED_DDL_TOP] = WelsDecoderI4x4LumaPredDDLTop_AArch64_neon;
    pCtx->pGetI4x4LumaPredFunc[I4_PRED_VL   ] = WelsDecoderI4x4LumaPredVL_AArch64_neon;
    pCtx->pGetI4x4LumaPredFunc[I4_PRED_VL_TOP ] = WelsDecoderI4x4LumaPredVLTop_AArch64_neon;
    pCtx->pGetI4x4LumaPredFunc[I4_PRED_VR   ] = WelsDecoderI4x4LumaPredVR_AArch64_neon;
    pCtx->pGetI4x4LumaPredFunc[I4_PRED_HU   ] = WelsDecoderI4x4LumaPredHU_AArch64_neon;
    pCtx->pGetI4x4LumaPredFunc[I4_PRED_HD   ] = WelsDecoderI4x4LumaPredHD_AArch64_neon;
    pCtx->pGetI4x4LumaPredFunc[I4_PRED_DC   ] = WelsDecoderI4x4LumaPredDc_AArch64_neon;
    pCtx->pGetI4x4LumaPredFunc[I4_PRED_DC_T   ] = WelsDecoderI4x4LumaPredDcTop_AArch64_neon;

    pCtx->pGetIChromaPredFunc[C_PRED_H]       = WelsDecoderIChromaPredH_AArch64_neon;
    pCtx->pGetIChromaPredFunc[C_PRED_V]       = WelsDecoderIChromaPredV_AArch64_neon;
    pCtx->pGetIChromaPredFunc[C_PRED_P ]      = WelsDecoderIChromaPredPlane_AArch64_neon;
    pCtx->pGetIChromaPredFunc[C_PRED_DC]      = WelsDecoderIChromaPredDc_AArch64_neon;
    pCtx->pGetIChromaPredFunc[C_PRED_DC_T]      = WelsDecoderIChromaPredDcTop_AArch64_neon;
  }
#endif//HAVE_NEON_AARCH64

#if defined(X86_ASM)
  if (uiCpuFlag & WELS_CPU_MMXEXT) {
    pCtx->pIdctResAddPredFunc   = IdctResAddPred_mmx;
    pCtx->pIdctFourResAddPredFunc = IdctFourResAddPred_<IdctResAddPred_mmx>;

    ///////mmx code opt---
    pCtx->pGetIChromaPredFunc[C_PRED_H]      = WelsDecoderIChromaPredH_mmx;
    pCtx->pGetIChromaPredFunc[C_PRED_V]      = WelsDecoderIChromaPredV_mmx;
    pCtx->pGetIChromaPredFunc[C_PRED_DC_L  ] = WelsDecoderIChromaPredDcLeft_mmx;
    pCtx->pGetIChromaPredFunc[C_PRED_DC_128] = WelsDecoderIChromaPredDcNA_mmx;
    pCtx->pGetI4x4LumaPredFunc[I4_PRED_DDR]  = WelsDecoderI4x4LumaPredDDR_mmx;
    pCtx->pGetI4x4LumaPredFunc[I4_PRED_HD ]  = WelsDecoderI4x4LumaPredHD_mmx;
    pCtx->pGetI4x4LumaPredFunc[I4_PRED_HU ]  = WelsDecoderI4x4LumaPredHU_mmx;
    pCtx->pGetI4x4LumaPredFunc[I4_PRED_VR ]  = WelsDecoderI4x4LumaPredVR_mmx;
    pCtx->pGetI4x4LumaPredFunc[I4_PRED_DDL]  = WelsDecoderI4x4LumaPredDDL_mmx;
    pCtx->pGetI4x4LumaPredFunc[I4_PRED_VL ]  = WelsDecoderI4x4LumaPredVL_mmx;
  }
  if (uiCpuFlag & WELS_CPU_SSE2) {
    pCtx->pIdctResAddPredFunc     = IdctResAddPred_sse2;
    pCtx->pIdctFourResAddPredFunc = IdctFourResAddPred_<IdctResAddPred_sse2>;

    pCtx->pGetI16x16LumaPredFunc[I16_PRED_DC] = WelsDecoderI16x16LumaPredDc_sse2;
    pCtx->pGetI16x16LumaPredFunc[I16_PRED_P]  = WelsDecoderI16x16LumaPredPlane_sse2;
    pCtx->pGetI16x16LumaPredFunc[I16_PRED_H]  = WelsDecoderI16x16LumaPredH_sse2;
    pCtx->pGetI16x16LumaPredFunc[I16_PRED_V]  = WelsDecoderI16x16LumaPredV_sse2;
    pCtx->pGetI16x16LumaPredFunc[I16_PRED_DC_T  ] = WelsDecoderI16x16LumaPredDcTop_sse2;
    pCtx->pGetI16x16LumaPredFunc[I16_PRED_DC_128] = WelsDecoderI16x16LumaPredDcNA_sse2;
    pCtx->pGetIChromaPredFunc[C_PRED_P ]      = WelsDecoderIChromaPredPlane_sse2;
    pCtx->pGetIChromaPredFunc[C_PRED_DC]      = WelsDecoderIChromaPredDc_sse2;
    pCtx->pGetIChromaPredFunc[C_PRED_DC_T]    = WelsDecoderIChromaPredDcTop_sse2;
    pCtx->pGetI4x4LumaPredFunc[I4_PRED_H]     = WelsDecoderI4x4LumaPredH_sse2;
  }
#if defined(HAVE_AVX2)
  if (uiCpuFlag & WELS_CPU_AVX2) {
    pCtx->pIdctResAddPredFunc     = IdctResAddPred_avx2;
    pCtx->pIdctFourResAddPredFunc = IdctFourResAddPred_avx2;
  }
#endif

#endif

#if defined(HAVE_MMI)
  if (uiCpuFlag & WELS_CPU_MMI) {
    pCtx->pIdctResAddPredFunc   = IdctResAddPred_mmi;
    pCtx->pIdctFourResAddPredFunc = IdctFourResAddPred_<IdctResAddPred_mmi>;

    pCtx->pGetI16x16LumaPredFunc[I16_PRED_DC] = WelsDecoderI16x16LumaPredDc_mmi;
    pCtx->pGetI16x16LumaPredFunc[I16_PRED_P]  = WelsDecoderI16x16LumaPredPlane_mmi;
    pCtx->pGetI16x16LumaPredFunc[I16_PRED_H]  = WelsDecoderI16x16LumaPredH_mmi;
    pCtx->pGetI16x16LumaPredFunc[I16_PRED_V]  = WelsDecoderI16x16LumaPredV_mmi;
    pCtx->pGetI16x16LumaPredFunc[I16_PRED_DC_T  ] = WelsDecoderI16x16LumaPredDcTop_mmi;
    pCtx->pGetI16x16LumaPredFunc[I16_PRED_DC_128] = WelsDecoderI16x16LumaPredDcNA_mmi;
    pCtx->pGetIChromaPredFunc[C_PRED_P ]      = WelsDecoderIChromaPredPlane_mmi;
    pCtx->pGetIChromaPredFunc[C_PRED_DC]      = WelsDecoderIChromaPredDc_mmi;
    pCtx->pGetIChromaPredFunc[C_PRED_DC_T]    = WelsDecoderIChromaPredDcTop_mmi;
    pCtx->pGetI4x4LumaPredFunc[I4_PRED_H]     = WelsDecoderI4x4LumaPredH_mmi;
  }
#endif//HAVE_MMI
}

//reset decoder number related statistics info
void ResetDecStatNums (SDecoderStatistics* pDecStat) {
  uint32_t uiWidth = pDecStat->uiWidth;
  uint32_t uiHeight = pDecStat->uiHeight;
  int32_t iAvgLumaQp = pDecStat->iAvgLumaQp;
  uint32_t iLogInterval = pDecStat->iStatisticsLogInterval;
  uint32_t uiProfile = pDecStat->uiProfile;
  uint32_t uiLevel = pDecStat->uiLevel;
  memset (pDecStat, 0, sizeof (SDecoderStatistics));
  pDecStat->uiWidth = uiWidth;
  pDecStat->uiHeight = uiHeight;
  pDecStat->iAvgLumaQp = iAvgLumaQp;
  pDecStat->iStatisticsLogInterval = iLogInterval;
  pDecStat->uiProfile = uiProfile;
  pDecStat->uiLevel = uiLevel;
}

//update information when freezing occurs, including IDR/non-IDR number
void UpdateDecStatFreezingInfo (const bool kbIdrFlag, SDecoderStatistics* pDecStat) {
  if (kbIdrFlag)
    pDecStat->uiFreezingIDRNum++;
  else
    pDecStat->uiFreezingNonIDRNum++;
}

//update information when no freezing occurs, including QP, correct IDR number, ECed IDR number
void UpdateDecStatNoFreezingInfo (PWelsDecoderContext pCtx) {
  PDqLayer pCurDq = pCtx->pCurDqLayer;
  PPicture pPic = pCtx->pDec;
  SDecoderStatistics* pDecStat = pCtx->pDecoderStatistics;

  if (pDecStat->iAvgLumaQp == -1) //first correct frame received
    pDecStat->iAvgLumaQp = 0;

  //update QP info
  int32_t iTotalQp = 0;
  const int32_t kiMbNum = pCurDq->iMbWidth * pCurDq->iMbHeight;
  if (pCtx->pParam->eEcActiveIdc == ERROR_CON_DISABLE) { //all correct
    for (int32_t iMb = 0; iMb < kiMbNum; ++iMb) {
      iTotalQp += pCurDq->pLumaQp[iMb];
    }
    iTotalQp /= kiMbNum;
  } else {
    int32_t iCorrectMbNum = 0;
    for (int32_t iMb = 0; iMb < kiMbNum; ++iMb) {
      iCorrectMbNum += (int32_t) pCurDq->pMbCorrectlyDecodedFlag[iMb];
      iTotalQp += pCurDq->pLumaQp[iMb] * pCurDq->pMbCorrectlyDecodedFlag[iMb];
    }
    if (iCorrectMbNum == 0) //non MB is correct, should remain QP statistic info
      iTotalQp = pDecStat->iAvgLumaQp;
    else
      iTotalQp /= iCorrectMbNum;
  }
  if (pDecStat->uiDecodedFrameCount + 1 == 0) { //maximum uint32_t reached
    ResetDecStatNums (pDecStat);
    pDecStat->iAvgLumaQp = iTotalQp;
  } else
    pDecStat->iAvgLumaQp = (int) ((uint64_t) (pDecStat->iAvgLumaQp * pDecStat->uiDecodedFrameCount + iTotalQp) /
                                  (pDecStat->uiDecodedFrameCount + 1));

  //update IDR number
  if (pCurDq->sLayerInfo.sNalHeaderExt.bIdrFlag) {
    pDecStat->uiIDRCorrectNum += (pPic->bIsComplete);
    if (pCtx->pParam->eEcActiveIdc != ERROR_CON_DISABLE)
      pDecStat->uiEcIDRNum += (!pPic->bIsComplete);
  }
}

//update decoder statistics information
void UpdateDecStat (PWelsDecoderContext pCtx, const bool kbOutput) {
  if (pCtx->bFreezeOutput)
    UpdateDecStatFreezingInfo (pCtx->pCurDqLayer->sLayerInfo.sNalHeaderExt.bIdrFlag, pCtx->pDecoderStatistics);
  else if (kbOutput)
    UpdateDecStatNoFreezingInfo (pCtx);
}


} // namespace WelsDec
