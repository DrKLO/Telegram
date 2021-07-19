/*!
 * \copy
 *     Copyright (c)  2009-2015, Cisco Systems
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
 * \file    wels_task_encoder.h
 *
 * \brief   interface for encoder tasks
 *
 * \date    07/06/2015 Created
 *
 *************************************************************************************
 */

#include <string.h>
#include <assert.h>

#include "typedefs.h"
#include "utils.h"
#include "measure_time.h"
#include "WelsTask.h"

#include "wels_task_base.h"
#include "wels_task_encoder.h"

#include "svc_enc_golomb.h"
#include "svc_encode_slice.h"
#include "slice_multi_threading.h"

namespace WelsEnc {

CWelsSliceEncodingTask::CWelsSliceEncodingTask (WelsCommon::IWelsTaskSink* pSink, sWelsEncCtx* pCtx,
    const int32_t iSliceIdx) : CWelsBaseTask (pSink), m_eTaskResult (ENC_RETURN_SUCCESS) {
  m_pCtx = pCtx;
  m_iSliceIdx = iSliceIdx;
}

CWelsSliceEncodingTask::~CWelsSliceEncodingTask() {
}

WelsErrorType CWelsSliceEncodingTask::Execute() {
  //fprintf(stdout, "OpenH264Enc_CWelsSliceEncodingTask_Execute, %x, sink=%x\n", this, m_pSink);

  m_eTaskResult = InitTask();
  WELS_VERIFY_RETURN_IFNEQ (m_eTaskResult, ENC_RETURN_SUCCESS)

  m_eTaskResult = ExecuteTask();

  FinishTask();

  //fprintf(stdout, "OpenH264Enc_CWelsSliceEncodingTask_Execute Ends\n");
  return m_eTaskResult;
}

WelsErrorType CWelsSliceEncodingTask::SetBoundary (int32_t iStartIdx,  int32_t iEndIdx) {
  m_iStartMbIdx = iStartIdx;
  m_iEndMbIdx = iEndIdx;
  return ENC_RETURN_SUCCESS;
}

int32_t CWelsSliceEncodingTask::QueryEmptyThread (bool* pThreadBsBufferUsage) {
  for (int32_t k = 0; k < MAX_THREADS_NUM; k++) {
    if (pThreadBsBufferUsage[k] == false) {
      pThreadBsBufferUsage[k] = true;
      return k;
    }
  }
  return -1;
}

WelsErrorType CWelsSliceEncodingTask::InitTask() {
  m_eNalType          = m_pCtx->eNalType;
  m_eNalRefIdc        = m_pCtx->eNalPriority;
  m_bNeedPrefix       = m_pCtx->bNeedPrefixNalFlag;

  WelsMutexLock (&m_pCtx->pSliceThreading->mutexThreadBsBufferUsage);
  m_iThreadIdx = QueryEmptyThread (m_pCtx->pSliceThreading->bThreadBsBufferUsage);
  WelsMutexUnlock (&m_pCtx->pSliceThreading->mutexThreadBsBufferUsage);

  WelsLog (&m_pCtx->sLogCtx, WELS_LOG_DEBUG,
           "[MT] CWelsSliceEncodingTask()InitTask for m_iSliceIdx %d, lock thread %d",
           m_iSliceIdx, m_iThreadIdx);
  if (m_iThreadIdx < 0) {
    WelsLog (&m_pCtx->sLogCtx, WELS_LOG_WARNING,
             "[MT] CWelsSliceEncodingTask InitTask(), Cannot find available thread for m_iSliceIdx = %d", m_iSliceIdx);
    return ENC_RETURN_UNEXPECTED;
  }

  int32_t iReturn = InitOneSliceInThread (m_pCtx, m_pSlice, m_iThreadIdx, m_pCtx->uiDependencyId, m_iSliceIdx);
  WELS_VERIFY_RETURN_IFNEQ (iReturn, ENC_RETURN_SUCCESS)
  m_pSliceBs = &m_pSlice->sSliceBs;

  iReturn   = SetSliceBoundaryInfo (m_pCtx->pCurDqLayer, m_pSlice, m_iSliceIdx);
  WELS_VERIFY_RETURN_IFNEQ (iReturn, ENC_RETURN_SUCCESS)

  SetOneSliceBsBufferUnderMultithread (m_pCtx, m_iThreadIdx, m_pSlice);

  assert ((void*) (&m_pSliceBs->sBsWrite) == (void*)m_pSlice->pSliceBsa);
  InitBits (&m_pSliceBs->sBsWrite, m_pSliceBs->pBsBuffer, m_pSliceBs->uiSize);
  //printf ("CWelsSliceEncodingTask_InitTask slice %d\n", m_iSliceIdx);

  return ENC_RETURN_SUCCESS;
}

void CWelsSliceEncodingTask::FinishTask() {
  WelsMutexLock (&m_pCtx->pSliceThreading->mutexThreadBsBufferUsage);
  m_pCtx->pSliceThreading->bThreadBsBufferUsage[m_iThreadIdx] = false;
  WelsMutexUnlock (&m_pCtx->pSliceThreading->mutexThreadBsBufferUsage);

  WelsLog (&m_pCtx->sLogCtx, WELS_LOG_DEBUG,
           "[MT] CWelsSliceEncodingTask()FinishTask for m_iSliceIdx %d, unlock thread %d",
           m_iSliceIdx, m_iThreadIdx);

  //sync multi-threading error
  WelsMutexLock (&m_pCtx->mutexEncoderError);
  if (ENC_RETURN_SUCCESS != m_eTaskResult) {
    m_pCtx->iEncoderError |= m_eTaskResult;
  }
  WelsMutexUnlock (&m_pCtx->mutexEncoderError);
}

WelsErrorType CWelsSliceEncodingTask::ExecuteTask() {

#if MT_DEBUG_BS_WR
  m_pSliceBs->bSliceCodedFlag = false;
#endif//MT_DEBUG_BS_WR
  SSpatialLayerInternal* pParamInternal = &m_pCtx->pSvcParam->sDependencyLayers[m_pCtx->uiDependencyId];
  if (m_bNeedPrefix) {
    if (m_eNalRefIdc != NRI_PRI_LOWEST) {
      WelsLoadNalForSlice (m_pSliceBs, NAL_UNIT_PREFIX, m_eNalRefIdc);
      WelsWriteSVCPrefixNal (&m_pSliceBs->sBsWrite, m_eNalRefIdc, (NAL_UNIT_CODED_SLICE_IDR == m_eNalType));
      WelsUnloadNalForSlice (m_pSliceBs);
    } else { // No Prefix NAL Unit RBSP syntax here, but need add NAL Unit Header extension
      WelsLoadNalForSlice (m_pSliceBs, NAL_UNIT_PREFIX, m_eNalRefIdc);
      // No need write any syntax of prefix NAL Unit RBSP here
      WelsUnloadNalForSlice (m_pSliceBs);
    }
  }

  WelsLoadNalForSlice (m_pSliceBs, m_eNalType, m_eNalRefIdc);
  assert (m_iSliceIdx == (int) m_pSlice->iSliceIdx);
  int32_t iReturn = WelsCodeOneSlice (m_pCtx, m_pSlice, m_eNalType);
  if (ENC_RETURN_SUCCESS != iReturn) {
    return iReturn;
  }
  WelsUnloadNalForSlice (m_pSliceBs);

  m_iSliceSize = 0;
  iReturn      = WriteSliceBs (m_pCtx, m_pSliceBs, m_iSliceIdx, m_iSliceSize);
  if (ENC_RETURN_SUCCESS != iReturn) {
    WelsLog (&m_pCtx->sLogCtx, WELS_LOG_WARNING,
             "[MT] CWelsSliceEncodingTask ExecuteTask(), WriteSliceBs not successful: coding_idx %d, um_iSliceIdx %d",
             pParamInternal->iCodingIndex,
             m_iSliceIdx);
    return iReturn;
  }

  m_pCtx->pFuncList->pfDeblocking.pfDeblockingFilterSlice (m_pCtx->pCurDqLayer, m_pCtx->pFuncList, m_pSlice);

  WelsLog (&m_pCtx->sLogCtx, WELS_LOG_DETAIL,
           "@pSlice=%-6d sliceType:%c idc:%d size:%-6d",  m_iSliceIdx,
           (m_pCtx->eSliceType == P_SLICE ? 'P' : 'I'),
           m_eNalRefIdc,
           m_iSliceSize);

#if MT_DEBUG_BS_WR
  m_pSliceBs->bSliceCodedFlag = true;
#endif//MT_DEBUG_BS_WR

  return ENC_RETURN_SUCCESS;
}


// CWelsLoadBalancingSlicingEncodingTask
WelsErrorType CWelsLoadBalancingSlicingEncodingTask::InitTask() {
  WelsErrorType iReturn = CWelsSliceEncodingTask::InitTask();
  if (ENC_RETURN_SUCCESS != iReturn) {
    return iReturn;
  }

  m_iSliceStart = WelsTime();
  WelsLog (&m_pCtx->sLogCtx, WELS_LOG_DEBUG,
           "[MT] CWelsLoadBalancingSlicingEncodingTask()InitTask for m_iSliceIdx %d at time=%" PRId64,
           m_iSliceIdx, m_iSliceStart);

  return ENC_RETURN_SUCCESS;
}

void CWelsLoadBalancingSlicingEncodingTask::FinishTask() {
  CWelsSliceEncodingTask::FinishTask();
  SSpatialLayerInternal* pParamInternal = &m_pCtx->pSvcParam->sDependencyLayers[m_pCtx->uiDependencyId];
  m_pSlice->uiSliceConsumeTime = (uint32_t) (WelsTime() - m_iSliceStart);
  WelsLog (&m_pCtx->sLogCtx, WELS_LOG_DEBUG,
           "[MT] CWelsLoadBalancingSlicingEncodingTask()FinishTask, coding_idx %d, um_iSliceIdx %d, uiSliceConsumeTime %d, m_iSliceSize %d, iFirstMbInSlice %d, count_num_mb_in_slice %d at time=%"
           PRId64,
           pParamInternal->iCodingIndex,
           m_iSliceIdx,
           m_pSlice->uiSliceConsumeTime,
           m_iSliceSize,
           m_pSlice->sSliceHeaderExt.sSliceHeader.iFirstMbInSlice,
           m_pSlice->iCountMbNumInSlice,
           (m_pSlice->uiSliceConsumeTime + m_iSliceStart));
}

//CWelsConstrainedSizeSlicingEncodingTask
WelsErrorType CWelsConstrainedSizeSlicingEncodingTask::ExecuteTask() {

  SDqLayer* pCurDq                        = m_pCtx->pCurDqLayer;
  const int32_t kiSliceIdxStep            = m_pCtx->iActiveThreadsNum;
  SSpatialLayerInternal* pParamInternal   = &m_pCtx->pSvcParam->sDependencyLayers[m_pCtx->uiDependencyId];
  const int32_t kiPartitionId             = m_iSliceIdx % kiSliceIdxStep;
  const int32_t kiFirstMbInPartition      = pCurDq->FirstMbIdxOfPartition[kiPartitionId];
  const int32_t kiEndMbIdxInPartition     = pCurDq->EndMbIdxOfPartition[kiPartitionId];
  const int32_t kiCodedSliceNumByThread   = pCurDq->sSliceBufferInfo[m_iThreadIdx].iCodedSliceNum;
  m_pSlice                                = &pCurDq->sSliceBufferInfo[m_iThreadIdx].pSliceBuffer[kiCodedSliceNumByThread];
  m_pSlice->sSliceHeaderExt.sSliceHeader.iFirstMbInSlice  = kiFirstMbInPartition;
  int32_t iReturn      = 0;
  bool bNeedReallocate = false;

  int32_t iDiffMbIdx = kiEndMbIdxInPartition - kiFirstMbInPartition;
  if (0 == iDiffMbIdx) {
    m_pSlice->iSliceIdx = -1;
    return ENC_RETURN_SUCCESS;
  }

  int32_t iAnyMbLeftInPartition = iDiffMbIdx + 1;
  int32_t iLocalSliceIdx = m_iSliceIdx;
  while (iAnyMbLeftInPartition > 0) {
    bNeedReallocate = (pCurDq->sSliceBufferInfo[m_iThreadIdx].iCodedSliceNum
                       >=  pCurDq->sSliceBufferInfo[m_iThreadIdx].iMaxSliceNum - 1) ? true : false;
    if (bNeedReallocate) {
      WelsMutexLock (&m_pCtx->pSliceThreading->mutexThreadSlcBuffReallocate);
      //for memory statistic variable
      iReturn = ReallocateSliceInThread (m_pCtx, pCurDq, m_pCtx->uiDependencyId, m_iThreadIdx);
      WelsMutexUnlock (&m_pCtx->pSliceThreading->mutexThreadSlcBuffReallocate);
      if (ENC_RETURN_SUCCESS != iReturn) {
        return iReturn;
      }
    }

    iReturn = InitOneSliceInThread (m_pCtx, m_pSlice, m_iThreadIdx, m_pCtx->uiDependencyId, iLocalSliceIdx);
    WELS_VERIFY_RETURN_IFNEQ (iReturn, ENC_RETURN_SUCCESS)
    m_pSliceBs = &m_pSlice->sSliceBs;
    InitBits (&m_pSliceBs->sBsWrite, m_pSliceBs->pBsBuffer, m_pSliceBs->uiSize);

    if (m_bNeedPrefix) {
      if (m_eNalRefIdc != NRI_PRI_LOWEST) {
        WelsLoadNalForSlice (m_pSliceBs, NAL_UNIT_PREFIX, m_eNalRefIdc);
        WelsWriteSVCPrefixNal (&m_pSliceBs->sBsWrite, m_eNalRefIdc, (NAL_UNIT_CODED_SLICE_IDR == m_eNalType));
        WelsUnloadNalForSlice (m_pSliceBs);
      } else { // No Prefix NAL Unit RBSP syntax here, but need add NAL Unit Header extension
        WelsLoadNalForSlice (m_pSliceBs, NAL_UNIT_PREFIX, m_eNalRefIdc);
        // No need write any syntax of prefix NAL Unit RBSP here
        WelsUnloadNalForSlice (m_pSliceBs);
      }
    }

    WelsLoadNalForSlice (m_pSliceBs, m_eNalType, m_eNalRefIdc);

    assert (iLocalSliceIdx == (int) m_pSlice->iSliceIdx);
    int32_t iReturn = WelsCodeOneSlice (m_pCtx, m_pSlice, m_eNalType);
    if (ENC_RETURN_SUCCESS != iReturn) {
      return iReturn;
    }
    WelsUnloadNalForSlice (m_pSliceBs);

    iReturn    = WriteSliceBs (m_pCtx, m_pSliceBs, iLocalSliceIdx, m_iSliceSize);
    if (ENC_RETURN_SUCCESS != iReturn) {
      WelsLog (&m_pCtx->sLogCtx, WELS_LOG_WARNING,
               "[MT] CWelsConstrainedSizeSlicingEncodingTask ExecuteTask(), WriteSliceBs not successful: coding_idx %d, uiLocalSliceIdx %d, BufferSize %d, m_iSliceSize %d, iPayloadSize %d",
               pParamInternal->iCodingIndex,
               iLocalSliceIdx, m_pSliceBs->uiSize, m_iSliceSize, m_pSliceBs->sNalList[0].iPayloadSize);
      return iReturn;
    }
    m_pCtx->pFuncList->pfDeblocking.pfDeblockingFilterSlice (pCurDq, m_pCtx->pFuncList, m_pSlice);

    WelsLog (&m_pCtx->sLogCtx, WELS_LOG_DETAIL,
             "@pSlice=%-6d sliceType:%c idc:%d size:%-6d\n",
             iLocalSliceIdx,
             (m_pCtx->eSliceType == P_SLICE ? 'P' : 'I'),
             m_eNalRefIdc,
             m_iSliceSize
            );

    WelsLog (&m_pCtx->sLogCtx, WELS_LOG_DEBUG,
             "[MT] CWelsConstrainedSizeSlicingEncodingTask(), coding_idx %d, iPartitionId %d, m_iThreadIdx %d, iLocalSliceIdx %d, m_iSliceSize %d, ParamValidationExt(), invalid uiMaxNalSizeiEndMbInPartition %d, pCurDq->LastCodedMbIdxOfPartition[%d] %d\n",
             pParamInternal->iCodingIndex, kiPartitionId, m_iThreadIdx, iLocalSliceIdx, m_iSliceSize,
             kiEndMbIdxInPartition, kiPartitionId, pCurDq->LastCodedMbIdxOfPartition[kiPartitionId]);

    iAnyMbLeftInPartition = kiEndMbIdxInPartition - pCurDq->LastCodedMbIdxOfPartition[kiPartitionId];
    iLocalSliceIdx += kiSliceIdxStep;
    m_pCtx->pCurDqLayer->sSliceBufferInfo[m_iThreadIdx].iCodedSliceNum ++;
  }

  return ENC_RETURN_SUCCESS;
}


CWelsUpdateMbMapTask::CWelsUpdateMbMapTask (WelsCommon::IWelsTaskSink* pSink, sWelsEncCtx* pCtx,
    const int32_t iSliceIdx): CWelsBaseTask (pSink) {
  m_pCtx = pCtx;
  m_iSliceIdx = iSliceIdx;
}

CWelsUpdateMbMapTask::~CWelsUpdateMbMapTask() {
}

WelsErrorType CWelsUpdateMbMapTask::Execute() {
  UpdateMbListNeighborParallel (m_pCtx->pCurDqLayer, m_pCtx->pCurDqLayer->sMbDataP, m_iSliceIdx);
  return ENC_RETURN_SUCCESS;
}

}


