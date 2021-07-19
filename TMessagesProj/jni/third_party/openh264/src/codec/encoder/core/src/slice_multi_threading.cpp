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
 *
 * \file    slice_multi_threading.h
 *
 * \brief   pSlice based multiple threading
 *
 * \date    04/16/2010 Created
 *
 *************************************************************************************
 */


#include <assert.h>
#if !defined(_WIN32)
#include <semaphore.h>
#include <unistd.h>
#endif//!_WIN32
#ifndef SEM_NAME_MAX
// length of semaphore name should be system constrained at least on mac 10.7
#define  SEM_NAME_MAX 32
#endif//SEM_NAME_MAX
#include "slice_multi_threading.h"
#include "mt_defs.h"
#include "nal_encap.h"
#include "utils.h"
#include "encoder.h"
#include "svc_encode_slice.h"
#include "deblocking.h"
#include "svc_enc_golomb.h"
#include "crt_util_safe_x.h" // for safe crt like calls
#include "rc.h"

#include "cpu.h"

#include "measure_time.h"
#include "wels_task_management.h"

#if defined(ENABLE_TRACE_MT)
#define MT_TRACE_LOG(pLog, x, ...) WelsLog(pLog, x, __VA_ARGS__)
#else
#define MT_TRACE_LOG(x, ...)
#endif

namespace WelsEnc {
void UpdateMbListNeighborParallel (SDqLayer* pCurDq,
                                   SMB* pMbList,
                                   const int32_t uiSliceIdc) {
  SSliceCtx* pSliceCtx           = &pCurDq->sSliceEncCtx;
  const int32_t kiMbWidth        = pSliceCtx->iMbWidth;
  int32_t iIdx                   = pCurDq->pFirstMbIdxOfSlice[uiSliceIdc];
  const int32_t kiEndMbInSlice   = iIdx + pCurDq->pCountMbNumInSlice[uiSliceIdc] - 1;

  do {
    UpdateMbNeighbor (pCurDq, &pMbList[iIdx], kiMbWidth, uiSliceIdc);
    ++ iIdx;
  } while (iIdx <= kiEndMbInSlice);
}

void CalcSliceComplexRatio (SDqLayer* pCurDq) {
  SSliceCtx* pSliceCtx          = &pCurDq->sSliceEncCtx;
  SSlice** ppSliceInLayer       = pCurDq->ppSliceInLayer;
  int32_t iSumAv                = 0;
  const int32_t kiSliceCount    = pSliceCtx->iSliceNumInFrame;
  int32_t iSliceIdx             = 0;
  int32_t iAvI[MAX_SLICES_NUM];

  assert (kiSliceCount <= MAX_SLICES_NUM);
  WelsEmms();

  while (iSliceIdx < kiSliceCount) {
    iAvI[iSliceIdx] = WELS_DIV_ROUND (INT_MULTIPLY * ppSliceInLayer[iSliceIdx]->iCountMbNumInSlice,
                                      ppSliceInLayer[iSliceIdx]->uiSliceConsumeTime);
    MT_TRACE_LOG (NULL, WELS_LOG_DEBUG, "[MT] CalcSliceComplexRatio(), uiSliceConsumeTime[%d]= %d us, slice_run= %d",
                  iSliceIdx,
                  ppSliceInLayer[iSliceIdx]->uiSliceConsumeTime, ppSliceInLayer[iSliceIdx]->iCountMbNumInSlice);
    iSumAv += iAvI[iSliceIdx];

    ++ iSliceIdx;
  }
  while (-- iSliceIdx >= 0) {
    ppSliceInLayer[iSliceIdx]->iSliceComplexRatio = WELS_DIV_ROUND (INT_MULTIPLY * iAvI[iSliceIdx], iSumAv);
  }
}

int32_t NeedDynamicAdjust (SSlice** ppSliceInLayer, const int32_t iSliceNum) {
  if (NULL == ppSliceInLayer) {
    return false;
  }

  uint32_t uiTotalConsume       = 0;
  int32_t iSliceIdx             = 0;
  int32_t iNeedAdj              = false;

  WelsEmms();

  while (iSliceIdx < iSliceNum) {
    if (NULL == ppSliceInLayer[iSliceIdx]) {
      return false;
    }

    uiTotalConsume += ppSliceInLayer[iSliceIdx]->uiSliceConsumeTime;
    iSliceIdx ++;
  }
  if (uiTotalConsume == 0) {
    MT_TRACE_LOG (NULL, WELS_LOG_DEBUG,
                  "[MT] NeedDynamicAdjust(), herein do no adjust due first picture, iCountSliceNum= %d",
                  iSliceNum);
    return false;
  }

  iSliceIdx = 0;
  float fThr                    = EPSN; // threshold for various cores cases
  float fRmse                   = .0f;  // root mean square error of pSlice consume ratios
  const float kfMeanRatio       = 1.0f / iSliceNum;
  do {
    const float fRatio = 1.0f * ppSliceInLayer[iSliceIdx]->uiSliceConsumeTime / uiTotalConsume;
    const float fDiffRatio = fRatio - kfMeanRatio;
    fRmse += (fDiffRatio * fDiffRatio);
    ++ iSliceIdx;
  } while (iSliceIdx + 1 < iSliceNum);
  fRmse = sqrtf (fRmse / iSliceNum);
  if (iSliceNum >= 8) {
    fThr += THRESHOLD_RMSE_CORE8;
  } else if (iSliceNum >= 4) {
    fThr += THRESHOLD_RMSE_CORE4;
  } else if (iSliceNum >= 2) {
    fThr += THRESHOLD_RMSE_CORE2;
  } else
    fThr = 1.0f;
  if (fRmse > fThr)
    iNeedAdj = true;
  MT_TRACE_LOG (NULL, WELS_LOG_DEBUG,
                "[MT] NeedDynamicAdjust(), herein adjustment decision is made (iNeedAdj= %d) by: fRmse of pSlice complexity ratios %.6f, the corresponding threshold %.6f, iCountSliceNum %d",
                iNeedAdj, fRmse, fThr, iSliceNum);

  return iNeedAdj;
}

void DynamicAdjustSlicing (sWelsEncCtx* pCtx,
                           SDqLayer* pCurDqLayer,
                           int32_t iCurDid) {
  SSliceCtx* pSliceCtx          = &pCurDqLayer->sSliceEncCtx;
  SSlice** ppSliceInLayer       = pCurDqLayer->ppSliceInLayer;
  const int32_t kiCountSliceNum = pSliceCtx->iSliceNumInFrame;
  const int32_t kiCountNumMb    = pSliceCtx->iMbNumInFrame;
  int32_t iMinimalMbNum         =
    pSliceCtx->iMbWidth;  // in theory we need only 1 SMB, here let it as one SMB row required
  int32_t iMaximalMbNum         = 0;    // dynamically assign later
  int32_t iMbNumLeft            = kiCountNumMb;
  int32_t iRunLen[MAX_THREADS_NUM] = {0};
  int32_t iSliceIdx             = 0;

  int32_t iNumMbInEachGom = 0;
  SWelsSvcRc* pWelsSvcRc = &pCtx->pWelsSvcRc[iCurDid];
  if (pCtx->pSvcParam->iRCMode != RC_OFF_MODE) {
    iNumMbInEachGom = pWelsSvcRc->iNumberMbGom;

    if (iNumMbInEachGom <= 0) {
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_ERROR,
               "[MT] DynamicAdjustSlicing(), invalid iNumMbInEachGom= %d from RC, iDid= %d, iCountNumMb= %d", iNumMbInEachGom,
               iCurDid, kiCountNumMb);
      return;
    }

    // do not adjust in case no extra iNumMbInEachGom based left for slicing adjustment,
    // extra MB of non integrated GOM assigned at the last pSlice in default, keep up on early initial result.
    if (iNumMbInEachGom * kiCountSliceNum >= kiCountNumMb) {
      return;
    }
    iMinimalMbNum = iNumMbInEachGom;
  }

  if (kiCountSliceNum < 2 || (kiCountSliceNum & 0x01)) // we need suppose uiSliceNum is even for multiple threading
    return;

  iMaximalMbNum = kiCountNumMb - (kiCountSliceNum - 1) * iMinimalMbNum;

  WelsEmms();

  MT_TRACE_LOG (& (pCtx->sLogCtx), WELS_LOG_DEBUG, "[MT] DynamicAdjustSlicing(), iDid= %d, iCountNumMb= %d", iCurDid,
                kiCountNumMb);

  iSliceIdx = 0;
  while (iSliceIdx + 1 < kiCountSliceNum) {
    int32_t iNumMbAssigning = WELS_DIV_ROUND (kiCountNumMb * ppSliceInLayer[iSliceIdx]->iSliceComplexRatio, INT_MULTIPLY);

    // GOM boundary aligned
    if (pCtx->pSvcParam->iRCMode != RC_OFF_MODE) {
      iNumMbAssigning = iNumMbAssigning / iNumMbInEachGom * iNumMbInEachGom;
    }

    // make sure one GOM at least in each pSlice for safe
    if (iNumMbAssigning < iMinimalMbNum)
      iNumMbAssigning = iMinimalMbNum;
    else if (iNumMbAssigning > iMaximalMbNum)
      iNumMbAssigning = iMaximalMbNum;

    assert (iNumMbAssigning > 0);

    iMbNumLeft -= iNumMbAssigning;
    if (iMbNumLeft <= 0) { // error due to we can not support slice_skip now yet, do not adjust this time
      assert (0);
      return;
    }
    iRunLen[iSliceIdx] = iNumMbAssigning;
    MT_TRACE_LOG (& (pCtx->sLogCtx), WELS_LOG_DEBUG,
                  "[MT] DynamicAdjustSlicing(), iSliceIdx= %d, iSliceComplexRatio= %.2f, slice_run_org= %d, slice_run_adj= %d",
                  iSliceIdx, ppSliceInLayer[iSliceIdx]->iSliceComplexRatio * 1.0f / INT_MULTIPLY,
                  ppSliceInLayer[iSliceIdx]->iCountMbNumInSlice,
                  iNumMbAssigning);
    ++ iSliceIdx;
    iMaximalMbNum = iMbNumLeft - (kiCountSliceNum - iSliceIdx - 1) * iMinimalMbNum; // get maximal num_mb in left parts
  }
  iRunLen[iSliceIdx] = iMbNumLeft;
  MT_TRACE_LOG (& (pCtx->sLogCtx), WELS_LOG_DEBUG,
                "[MT] DynamicAdjustSlicing(), iSliceIdx= %d, pSliceComplexRatio= %.2f, slice_run_org= %d, slice_run_adj= %d",
                iSliceIdx, ppSliceInLayer[iSliceIdx]->iSliceComplexRatio * 1.0f / INT_MULTIPLY,
                ppSliceInLayer[iSliceIdx]->iCountMbNumInSlice, iMbNumLeft);
  pCurDqLayer->bNeedAdjustingSlicing = !DynamicAdjustSlicePEncCtxAll (pCurDqLayer, iRunLen);
}

int32_t RequestMtResource (sWelsEncCtx** ppCtx, SWelsSvcCodingParam* pCodingParam, const int32_t iCountBsLen,
                           const int32_t iMaxSliceBufferSize, bool bDynamicSlice) {
  CMemoryAlign* pMa             = NULL;
  SWelsSvcCodingParam* pPara    = NULL;
  SSliceThreading* pSmt         = NULL;
  int32_t iNumSpatialLayers     = 0;
  int32_t iThreadNum            = 0;
  int32_t iIdx                  = 0;
  int32_t iReturn               = ENC_RETURN_SUCCESS;

  if (NULL == ppCtx || NULL == pCodingParam || NULL == *ppCtx || iCountBsLen <= 0)
    return 1;
#if defined(ENABLE_TRACE_MT)
  SLogContext* pLogCtx = & ((*ppCtx)->sLogCtx);
#endif
  pMa                  = (*ppCtx)->pMemAlign;
  pPara                = pCodingParam;
  iNumSpatialLayers    = pPara->iSpatialLayerNum;
  iThreadNum           = pPara->iMultipleThreadIdc;

  assert (iThreadNum > 0);

  pSmt = (SSliceThreading*)pMa->WelsMalloc (sizeof (SSliceThreading), "SSliceThreading");
  WELS_VERIFY_RETURN_IF (1, (NULL == pSmt))
  memset (pSmt, 0, sizeof (SSliceThreading));
  (*ppCtx)->pSliceThreading = pSmt;
  pSmt->pThreadPEncCtx = (SSliceThreadPrivateData*)pMa->WelsMalloc (sizeof (SSliceThreadPrivateData) * iThreadNum,
                         "pThreadPEncCtx");
  WELS_VERIFY_RETURN_IF (1, (NULL == pSmt->pThreadPEncCtx))

#ifdef _WIN32
  // Dummy event namespace, the windows events don't actually use this
  WelsSnprintf (pSmt->eventNamespace, sizeof (pSmt->eventNamespace), "%p", (void*) *ppCtx);
#else
  WelsSnprintf (pSmt->eventNamespace, sizeof (pSmt->eventNamespace), "%p%x", (void*) *ppCtx, getpid());
#endif//!_WIN32

#ifdef MT_DEBUG
  // file handle for MT debug
  pSmt->pFSliceDiff = NULL;

  if (pSmt->pFSliceDiff) {
    fclose (pSmt->pFSliceDiff);
    pSmt->pFSliceDiff = NULL;
  }
  pSmt->pFSliceDiff = fopen ("slice_time.txt", "wt+");
#endif//MT_DEBUG

  MT_TRACE_LOG (pLogCtx, WELS_LOG_INFO, "encpEncCtx= 0x%p", (void*) *ppCtx);

  char name[SEM_NAME_MAX] = {0};
  WELS_GCC_UNUSED WELS_THREAD_ERROR_CODE err = 0;

  iIdx = 0;
  while (iIdx < iThreadNum) {
    pSmt->pThreadPEncCtx[iIdx].pWelsPEncCtx   = (void*) *ppCtx;
    pSmt->pThreadPEncCtx[iIdx].iSliceIndex    = iIdx;
    pSmt->pThreadPEncCtx[iIdx].iThreadIndex   = iIdx;
    pSmt->pThreadHandles[iIdx]                = 0;

    // length of semaphore name should be system constrained at least on mac 10.7
    WelsSnprintf (name, SEM_NAME_MAX, "ud%d%s", iIdx, pSmt->eventNamespace);
    err = WelsEventOpen (&pSmt->pUpdateMbListEvent[iIdx], name);
    MT_TRACE_LOG (pLogCtx, WELS_LOG_INFO, "[MT] Open pUpdateMbListEvent%d named(%s) ret%d err%d", iIdx, name, err, errno);
    WelsSnprintf (name, SEM_NAME_MAX, "fu%d%s", iIdx, pSmt->eventNamespace);
    err = WelsEventOpen (&pSmt->pFinUpdateMbListEvent[iIdx], name);
    MT_TRACE_LOG (pLogCtx, WELS_LOG_INFO, "[MT] Open pFinUpdateMbListEvent%d named(%s) ret%d err%d", iIdx, name, err,
                  errno);
    WelsSnprintf (name, SEM_NAME_MAX, "sc%d%s", iIdx, pSmt->eventNamespace);
    err = WelsEventOpen (&pSmt->pSliceCodedEvent[iIdx], name);
    MT_TRACE_LOG (pLogCtx, WELS_LOG_INFO, "[MT] Open pSliceCodedEvent%d named(%s) ret%d err%d", iIdx, name, err, errno);
    WelsSnprintf (name, SEM_NAME_MAX, "rc%d%s", iIdx, pSmt->eventNamespace);
    err = WelsEventOpen (&pSmt->pReadySliceCodingEvent[iIdx], name);
    MT_TRACE_LOG (pLogCtx, WELS_LOG_INFO, "[MT] Open pReadySliceCodingEvent%d = 0x%p named(%s) ret%d err%d", iIdx,
                  (void*)pSmt->pReadySliceCodingEvent[iIdx], name, err, errno);
    ++ iIdx;
  }

  WelsSnprintf (name, SEM_NAME_MAX, "scm%s", pSmt->eventNamespace);
  err = WelsEventOpen (&pSmt->pSliceCodedMasterEvent, name);
  MT_TRACE_LOG (pLogCtx, WELS_LOG_INFO, "[MT] Open pSliceCodedMasterEvent named(%s) ret%d err%d", name, err, errno);

  iReturn = WelsMutexInit (&pSmt->mutexSliceNumUpdate);
  WELS_VERIFY_RETURN_IF (1, (WELS_THREAD_ERROR_OK != iReturn))

  (*ppCtx)->pTaskManage = IWelsTaskManage::CreateTaskManage (*ppCtx, iNumSpatialLayers, bDynamicSlice);
  WELS_VERIFY_RETURN_IF (1, (NULL == (*ppCtx)->pTaskManage))

  int32_t iThreadBufferNum = WELS_MIN ((*ppCtx)->pTaskManage->GetThreadPoolThreadNum(), MAX_THREADS_NUM);

  for (iIdx = 0; iIdx < iThreadBufferNum; iIdx++) {
    pSmt->pThreadBsBuffer[iIdx] = (uint8_t*)pMa->WelsMallocz (iCountBsLen, "pSmt->pThreadBsBuffer");
    WELS_VERIFY_RETURN_IF (1, (NULL == pSmt->pThreadBsBuffer[iIdx]))
  }
  iReturn = WelsMutexInit (&pSmt->mutexThreadBsBufferUsage);
  WELS_VERIFY_RETURN_PROC_IF (1, (WELS_THREAD_ERROR_OK != iReturn), FreeMemorySvc (ppCtx))

  iReturn = WelsMutexInit (&pSmt->mutexEvent);
  WELS_VERIFY_RETURN_PROC_IF (1, (WELS_THREAD_ERROR_OK != iReturn), FreeMemorySvc (ppCtx));

  iReturn = WelsMutexInit (&pSmt->mutexThreadSlcBuffReallocate);
  WELS_VERIFY_RETURN_PROC_IF (1, (WELS_THREAD_ERROR_OK != iReturn), FreeMemorySvc (ppCtx))

  iReturn = WelsMutexInit (& (*ppCtx)->mutexEncoderError);
  WELS_VERIFY_RETURN_IF (1, (WELS_THREAD_ERROR_OK != iReturn))

  MT_TRACE_LOG (pLogCtx, WELS_LOG_INFO, "RequestMtResource(), iThreadNum=%d, iMultipleThreadIdc= %d",
                pPara->iMultipleThreadIdc,
                (*ppCtx)->iMaxSliceCount);
  return 0;
}

void ReleaseMtResource (sWelsEncCtx** ppCtx) {
  SSliceThreading* pSmt                 = NULL;
  CMemoryAlign* pMa                     = NULL;
  int32_t iIdx                          = 0;
  int32_t iThreadNum                    = 0;

  if (NULL == ppCtx || NULL == *ppCtx)
    return;

  pMa           = (*ppCtx)->pMemAlign;
  iThreadNum    = (*ppCtx)->pSvcParam->iMultipleThreadIdc;
  pSmt          = (*ppCtx)->pSliceThreading;

  if (NULL == pSmt)
    return;

  char ename[SEM_NAME_MAX] = {0};
  while (iIdx < iThreadNum) {
    // length of semaphore name should be system constrained at least on mac 10.7
    WelsSnprintf (ename, SEM_NAME_MAX, "sc%d%s", iIdx, pSmt->eventNamespace);
    WelsEventClose (&pSmt->pSliceCodedEvent[iIdx], ename);
    WelsSnprintf (ename, SEM_NAME_MAX, "rc%d%s", iIdx, pSmt->eventNamespace);
    WelsEventClose (&pSmt->pReadySliceCodingEvent[iIdx], ename);
    WelsSnprintf (ename, SEM_NAME_MAX, "ud%d%s", iIdx, pSmt->eventNamespace);
    WelsEventClose (&pSmt->pUpdateMbListEvent[iIdx], ename);
    WelsSnprintf (ename, SEM_NAME_MAX, "fu%d%s", iIdx, pSmt->eventNamespace);
    WelsEventClose (&pSmt->pFinUpdateMbListEvent[iIdx], ename);

    ++ iIdx;
  }
  WelsSnprintf (ename, SEM_NAME_MAX, "scm%s", pSmt->eventNamespace);
  WelsEventClose (&pSmt->pSliceCodedMasterEvent, ename);

  WelsMutexDestroy (&pSmt->mutexSliceNumUpdate);
  WelsMutexDestroy (&pSmt->mutexThreadBsBufferUsage);
  WelsMutexDestroy (&pSmt->mutexThreadSlcBuffReallocate);
  WelsMutexDestroy (& ((*ppCtx)->mutexEncoderError));
  WelsMutexDestroy (&pSmt->mutexEvent);
  if (pSmt->pThreadPEncCtx != NULL) {
    pMa->WelsFree (pSmt->pThreadPEncCtx, "pThreadPEncCtx");
    pSmt->pThreadPEncCtx = NULL;
  }

  for (int i = 0; i < MAX_THREADS_NUM; i++) {
    if (pSmt->pThreadBsBuffer[i]) {
      pMa->WelsFree (pSmt->pThreadBsBuffer[i], "pSmt->pThreadBsBuffer");
      pSmt->pThreadBsBuffer[i] = NULL;
    }
  }
  memset (&pSmt->bThreadBsBufferUsage, 0, MAX_THREADS_NUM * sizeof (bool));

  if ((*ppCtx)->pTaskManage != NULL) {
    WELS_DELETE_OP ((*ppCtx)->pTaskManage);
  }

#ifdef MT_DEBUG
  // file handle for debug
  if (pSmt->pFSliceDiff) {
    fclose (pSmt->pFSliceDiff);
    pSmt->pFSliceDiff = NULL;
  }
#endif//MT_DEBUG
  pMa->WelsFree ((*ppCtx)->pSliceThreading, "SSliceThreading");
  (*ppCtx)->pSliceThreading = NULL;
}

int32_t AppendSliceToFrameBs (sWelsEncCtx* pCtx, SLayerBSInfo* pLbi, const int32_t iSliceCount) {
  SSlice** ppSliceInlayer = pCtx->pCurDqLayer->ppSliceInLayer;
  SWelsSliceBs* pSliceBs  = NULL;
  int32_t iLayerSize      = 0;
  int32_t iNalIdxBase     = pLbi->iNalCount;
  int32_t iSliceIdx       = 0;

  iNalIdxBase  = pLbi->iNalCount = 0;
  while (iSliceIdx < iSliceCount) {
    pSliceBs    = &ppSliceInlayer[iSliceIdx]->sSliceBs;
    if (pSliceBs != NULL && pSliceBs->uiBsPos > 0) {
      int32_t iNalIdx = 0;
      const int32_t iCountNal = pSliceBs->iNalIndex;

#if MT_DEBUG_BS_WR
      assert (pSliceBs->bSliceCodedFlag);
#endif//MT_DEBUG_BS_WR

      memmove (pCtx->pFrameBs + pCtx->iPosBsBuffer, pSliceBs->pBs, pSliceBs->uiBsPos); // confirmed_safe_unsafe_usage
      pCtx->iPosBsBuffer += pSliceBs->uiBsPos;

      iLayerSize += pSliceBs->uiBsPos;

      while (iNalIdx < iCountNal) {
        pLbi->pNalLengthInByte[iNalIdxBase + iNalIdx] = pSliceBs->iNalLen[iNalIdx];
        ++ iNalIdx;
      }
      pLbi->iNalCount += iCountNal;
      iNalIdxBase     += iCountNal;
    }
    ++ iSliceIdx;
  }

  return iLayerSize;
}

int32_t WriteSliceBs (sWelsEncCtx* pCtx, SWelsSliceBs* pSliceBs, const int32_t iSliceIdx, int32_t& iSliceSize) {
  const int32_t kiNalCnt        = pSliceBs->iNalIndex;
  int32_t iNalIdx               = 0;
  int32_t iNalSize              = 0;
  int32_t iReturn               = ENC_RETURN_SUCCESS;
  int32_t iTotalLeftLength      = pSliceBs->uiSize - pSliceBs->uiBsPos;
  SNalUnitHeaderExt* pNalHdrExt = &pCtx->pCurDqLayer->sLayerInfo.sNalHeaderExt;
  uint8_t* pDst                 = pSliceBs->pBs;

  assert (kiNalCnt <= 2);
  if (kiNalCnt > 2)
    return 0;

  iSliceSize = 0;
  while (iNalIdx < kiNalCnt) {
    iNalSize = 0;
    iReturn = WelsEncodeNal (&pSliceBs->sNalList[iNalIdx], pNalHdrExt, iTotalLeftLength - iSliceSize,
                             pDst, &iNalSize);
    WELS_VERIFY_RETURN_IFNEQ (iReturn, ENC_RETURN_SUCCESS)

    pSliceBs->iNalLen[iNalIdx] = iNalSize;
    iSliceSize                += iNalSize;
    pDst                      += iNalSize;
    ++ iNalIdx;
  }
  pSliceBs->uiBsPos = iSliceSize;

  return iReturn;
}

// thread process for coding one pSlice
int32_t DynamicDetectCpuCores() {
  WelsLogicalProcessInfo  info;
  WelsQueryLogicalProcessInfo (&info);
  return info.ProcessorCount;
}

int32_t AdjustBaseLayer (sWelsEncCtx* pCtx) {
  SDqLayer* pCurDq      = pCtx->ppDqLayerList[0];
  int32_t iNeedAdj      = 1;
#ifdef MT_DEBUG
  int64_t iT0 = WelsTime();
#endif//MT_DEBUG

  pCtx->pCurDqLayer = pCurDq;

  // do not need adjust due to not different at both slices of consumed time
  iNeedAdj = NeedDynamicAdjust (pCtx->ppDqLayerList[0]->ppSliceInLayer
                                , pCurDq->sSliceEncCtx.iSliceNumInFrame);
  if (iNeedAdj)
    DynamicAdjustSlicing (pCtx,
                          pCurDq,
                          0);
#ifdef MT_DEBUG
  iT0 = WelsTime() - iT0;
  if (pCtx->pSliceThreading->pFSliceDiff) {
    fprintf (pCtx->pSliceThreading->pFSliceDiff,
             "%6" PRId64" us adjust time at base spatial layer, iNeedAdj %d, DynamicAdjustSlicing()\n",
             iT0, iNeedAdj);
  }
#endif//MT_DEBUG

  return iNeedAdj;
}

int32_t AdjustEnhanceLayer (sWelsEncCtx* pCtx, int32_t iCurDid) {
#ifdef MT_DEBUG
  int64_t iT1 = WelsTime();
#endif//MT_DEBUG
  int32_t iNeedAdj = 1;
  // uiSliceMode of referencing spatial should be SM_FIXEDSLCNUM_SLICE
  // if using spatial base layer for complexity estimation

  const bool kbModelingFromSpatial = (pCtx->pCurDqLayer->pRefLayer != NULL && iCurDid > 0)
                                     && (pCtx->pSvcParam->sSpatialLayers[iCurDid - 1].sSliceArgument.uiSliceMode == SM_FIXEDSLCNUM_SLICE
                                         && pCtx->pSvcParam->iMultipleThreadIdc >= pCtx->pSvcParam->sSpatialLayers[iCurDid -
                                             1].sSliceArgument.uiSliceNum);

  if (kbModelingFromSpatial) { // using spatial base layer for complexity estimation
    // do not need adjust due to not different at both slices of consumed time
    iNeedAdj = NeedDynamicAdjust (pCtx->ppDqLayerList[iCurDid - 1]->ppSliceInLayer,
                                  pCtx->pCurDqLayer->sSliceEncCtx.iSliceNumInFrame);
    if (iNeedAdj)
      DynamicAdjustSlicing (pCtx,
                            pCtx->pCurDqLayer,
                            iCurDid
                           );
  } else { // use temporal layer for complexity estimation
    // do not need adjust due to not different at both slices of consumed time
    iNeedAdj = NeedDynamicAdjust (pCtx->ppDqLayerList[iCurDid]->ppSliceInLayer,
                                  pCtx->pCurDqLayer->sSliceEncCtx.iSliceNumInFrame);
    if (iNeedAdj)
      DynamicAdjustSlicing (pCtx,
                            pCtx->pCurDqLayer,
                            iCurDid
                           );
  }

#ifdef MT_DEBUG
  iT1 = WelsTime() - iT1;
  if (pCtx->pSliceThreading->pFSliceDiff) {
    fprintf (pCtx->pSliceThreading->pFSliceDiff,
             "%6" PRId64" us adjust time at spatial layer %d, iNeedAdj %d, DynamicAdjustSlicing()\n",
             iT1, iCurDid, iNeedAdj);
  }
#endif//MT_DEBUG

  return iNeedAdj;
}



#if defined(MT_DEBUG)
void TrackSliceComplexities (sWelsEncCtx* pCtx, const int32_t iCurDid) {
  const int32_t kiCountSliceNum = pCtx->pCurDqLayer->sSliceEncCtx.iSliceNumInFrame;
  SSlice** ppSliceInLayer = pCtx->pCurDqLayer->ppSliceInLayer;
  if (kiCountSliceNum > 0) {
    int32_t iSliceIdx = 0;
    do {
      fprintf (pCtx->pSliceThreading->pFSliceDiff, "%6.3f complexity pRatio at iDid %d pSlice %d\n",
               ppSliceInLayer[iSliceIdx]->iSliceComplexRatio, iCurDid, iSliceIdx);
      ++ iSliceIdx;
    } while (iSliceIdx < kiCountSliceNum);
  }
}
#endif

#if defined(MT_DEBUG)
void TrackSliceConsumeTime (sWelsEncCtx* pCtx, int32_t* pDidList, const int32_t iSpatialNum) {
  SWelsSvcCodingParam* pPara = NULL;
  int32_t iSpatialIdx = 0;

  if (iSpatialNum > MAX_DEPENDENCY_LAYER)
    return;

  pPara = pCtx->pSvcParam;
  while (iSpatialIdx < iSpatialNum) {
    const int32_t kiDid             = pDidList[iSpatialIdx];
    SSliceArgument* pSliceArgument  = &pPara->sSpatialLayers[kiDid].sSliceArgument;
    SDqLayer* pCurDq                = pCtx->ppDqLayerList[kiDid];
    SSlice** ppSliceInLayer         = pCurDq->ppSliceInLayer;
    SSliceCtx* pSliceCtx            = &pCurDq->sSliceEncCtx;
    const uint32_t kuiCountSliceNum = pSliceCtx->iSliceNumInFrame;
    if (pCtx->pSliceThreading) {
      if (pCtx->pSliceThreading->pFSliceDiff
          && ((pSliceArgument->uiSliceMode == SM_FIXEDSLCNUM_SLICE) || (pSliceArgument->uiSliceMode == SM_SIZELIMITED_SLICE))
          && pPara->iMultipleThreadIdc > 1
          && pPara->iMultipleThreadIdc >= kuiCountSliceNum) {
        uint32_t i = 0;
        uint32_t uiMaxT = 0;
        int32_t iMaxI = 0;
        while (i < kuiCountSliceNum) {
          fprintf (pCtx->pSliceThreading->pFSliceDiff, "%6d us consume_time coding_idx %d iDid %d pSlice %d\n",
                   ppSliceInLayer[i]->uiSliceConsumeTime, pCtx->iCodingIndex, kiDid, i /*/ 1000*/);
          if (ppSliceInLayer[i]->uiSliceConsumeTime > uiMaxT) {
            uiMaxT = ppSliceInLayer[i]->uiSliceConsumeTime;
            iMaxI = i;
          }
          ++ i;
        }
        fprintf (pCtx->pSliceThreading->pFSliceDiff, "%6d us consume_time_max coding_idx %d iDid %d pSlice %d\n", uiMaxT,
                 pCtx->iCodingIndex, kiDid, iMaxI /*/ 1000*/);
      }
    }
    ++ iSpatialIdx;
  }
}
#endif//#if defined(MT_DEBUG)

void SetOneSliceBsBufferUnderMultithread (sWelsEncCtx* pCtx, const int32_t kiThreadIdx, SSlice* pSlice) {
  SWelsSliceBs* pSliceBs  = &pSlice->sSliceBs;
  pSliceBs->pBsBuffer     = pCtx->pSliceThreading->pThreadBsBuffer[kiThreadIdx];
  pSliceBs->uiBsPos       = 0;
}
}

