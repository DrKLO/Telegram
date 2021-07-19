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
 * \file    wels_task_management.cpp
 *
 * \brief   function for task management
 *
 * \date    5/14/2012 Created
 *
 *************************************************************************************
 */
#include <string.h>
#include <assert.h>

#include "typedefs.h"
#include "utils.h"
#include "WelsLock.h"
#include "memory_align.h"

#include "wels_common_basis.h"
#include "encoder_context.h"
#include "wels_task_base.h"
#include "wels_task_encoder.h"
#include "wels_task_management.h"

namespace WelsEnc {



IWelsTaskManage*   IWelsTaskManage::CreateTaskManage (sWelsEncCtx* pCtx, const int32_t iSpatialLayer,
    const bool bNeedLock) {
  if (NULL == pCtx) {
    return NULL;
  }

  IWelsTaskManage* pTaskManage;
  pTaskManage = WELS_NEW_OP (CWelsTaskManageBase(), CWelsTaskManageBase);
  WELS_VERIFY_RETURN_IF (NULL, NULL == pTaskManage)

  if (ENC_RETURN_SUCCESS != pTaskManage->Init (pCtx)) {
    pTaskManage->Uninit();
    WELS_DELETE_OP (pTaskManage);
  }
  return pTaskManage;
}


CWelsTaskManageBase::CWelsTaskManageBase()
  : m_pEncCtx (NULL),
    m_pThreadPool (NULL),
    m_iWaitTaskNum (0) {

  for (int32_t iDid = 0; iDid < MAX_DEPENDENCY_LAYER; iDid++) {
    m_iTaskNum[iDid] = 0;
    m_cEncodingTaskList[iDid] = new TASKLIST_TYPE();
    m_cPreEncodingTaskList[iDid] = new TASKLIST_TYPE();
  }

  WelsEventOpen (&m_hTaskEvent);
  WelsMutexInit (&m_hEventMutex);
}

CWelsTaskManageBase::~CWelsTaskManageBase() {
  //fprintf(stdout, "~CWelsTaskManageBase\n");
  Uninit();
}

WelsErrorType CWelsTaskManageBase::Init (sWelsEncCtx* pEncCtx) {
  m_pEncCtx = pEncCtx;
  m_iThreadNum = m_pEncCtx->pSvcParam->iMultipleThreadIdc;

  int32_t iReturn = ENC_RETURN_SUCCESS;
  //fprintf(stdout, "m_pThreadPool = &(CWelsThreadPool::GetInstance, this=%x\n", this);
  iReturn = CWelsThreadPool::SetThreadNum (m_iThreadNum);
  m_pThreadPool = (CWelsThreadPool::AddReference());
  if ((iReturn != ENC_RETURN_SUCCESS) && pEncCtx) {
    WelsLog (& (pEncCtx->sLogCtx), WELS_LOG_WARNING, "Set Thread Num to %d did not succeed, current thread num in use: %d",
             m_iThreadNum, m_pThreadPool->GetThreadNum());
  }
  WELS_VERIFY_RETURN_IF (ENC_RETURN_MEMALLOCERR, NULL == m_pThreadPool)
  //fprintf(stdout, "m_pThreadPool = &(CWelsThreadPool::GetInstance3\n");

  iReturn = ENC_RETURN_SUCCESS;
  for (int32_t iDid = 0; iDid < MAX_DEPENDENCY_LAYER; iDid++) {
    m_pcAllTaskList[CWelsBaseTask::WELS_ENC_TASK_ENCODING][iDid] = m_cEncodingTaskList[iDid];
    m_pcAllTaskList[CWelsBaseTask::WELS_ENC_TASK_UPDATEMBMAP][iDid] = m_cPreEncodingTaskList[iDid];
    iReturn |= CreateTasks (pEncCtx, iDid);
  }

  //fprintf(stdout, "CWelsTaskManageBase Init m_iThreadNum %d m_iCurrentTaskNum %d pEncCtx->iMaxSliceCount %d\n", m_iThreadNum, m_iCurrentTaskNum, pEncCtx->iMaxSliceCount);
  return iReturn;
}

void   CWelsTaskManageBase::Uninit() {
  DestroyTasks();
  //fprintf(stdout, "m_pThreadPool = m_pThreadPool->RemoveInstance\n");
  if (m_pThreadPool)
    m_pThreadPool->RemoveInstance();
  //WELS_DELETE_OP (m_pThreadPool);

  //fprintf(stdout, "m_pThreadPool = m_pThreadPool->RemoveInstance2\n");

  for (int32_t iDid = 0; iDid < MAX_DEPENDENCY_LAYER; iDid++) {
    WELS_DELETE_OP (m_cEncodingTaskList[iDid]);
    WELS_DELETE_OP (m_cPreEncodingTaskList[iDid]);
  }
  WelsEventClose (&m_hTaskEvent);
  WelsMutexDestroy (&m_hEventMutex);
}

WelsErrorType CWelsTaskManageBase::CreateTasks (sWelsEncCtx* pEncCtx, const int32_t kiCurDid) {
  CWelsBaseTask* pTask = NULL;
  int32_t kiTaskCount;
  uint32_t uiSliceMode = pEncCtx->pSvcParam->sSpatialLayers[kiCurDid].sSliceArgument.uiSliceMode;

  if (uiSliceMode != SM_SIZELIMITED_SLICE) {
    kiTaskCount = m_iTaskNum[kiCurDid] = pEncCtx->pSvcParam->sSpatialLayers[kiCurDid].sSliceArgument.uiSliceNum;
  } else {
    kiTaskCount = m_iTaskNum[kiCurDid] = pEncCtx->iActiveThreadsNum;
  }

  for (int idx = 0; idx < kiTaskCount; idx++) {
    pTask = WELS_NEW_OP (CWelsUpdateMbMapTask (this, pEncCtx, idx), CWelsUpdateMbMapTask);
    WELS_VERIFY_RETURN_IF (ENC_RETURN_MEMALLOCERR, NULL == pTask)
    WELS_VERIFY_RETURN_IF (ENC_RETURN_MEMALLOCERR, true != m_cPreEncodingTaskList[kiCurDid]->push_back (pTask));
  }

  for (int idx = 0; idx < kiTaskCount; idx++) {
    if (uiSliceMode == SM_SIZELIMITED_SLICE) {
      pTask = WELS_NEW_OP (CWelsConstrainedSizeSlicingEncodingTask (this, pEncCtx, idx),
                           CWelsConstrainedSizeSlicingEncodingTask);
    } else {
      if (pEncCtx->pSvcParam->bUseLoadBalancing) {
        pTask = WELS_NEW_OP (CWelsLoadBalancingSlicingEncodingTask (this, pEncCtx, idx), CWelsLoadBalancingSlicingEncodingTask);
      } else {
        pTask = WELS_NEW_OP (CWelsSliceEncodingTask (this, pEncCtx, idx), CWelsSliceEncodingTask);
      }
    }
    WELS_VERIFY_RETURN_IF (ENC_RETURN_MEMALLOCERR, NULL == pTask)
    WELS_VERIFY_RETURN_IF (ENC_RETURN_MEMALLOCERR, true != m_cEncodingTaskList[kiCurDid]->push_back (pTask));
  }

  //fprintf(stdout, "CWelsTaskManageBase CreateTasks m_iThreadNum %d kiTaskCount=%d\n", m_iThreadNum, kiTaskCount);
  return ENC_RETURN_SUCCESS;
}

void CWelsTaskManageBase::DestroyTaskList (TASKLIST_TYPE* pTargetTaskList) {
  //fprintf(stdout, "CWelsTaskManageBase: pTargetTaskList size=%d m_iTotalTaskNum=%d\n", static_cast<int32_t> (pTargetTaskList->size()), m_iTotalTaskNum);
  while (NULL != pTargetTaskList->begin()) {
    CWelsBaseTask* pTask = pTargetTaskList->begin();
    WELS_DELETE_OP (pTask);
    pTargetTaskList->pop_front();
  }
  pTargetTaskList = NULL;
}

void CWelsTaskManageBase::DestroyTasks() {
  for (int32_t iDid = 0; iDid < MAX_DEPENDENCY_LAYER; iDid++)  {
    if (m_iTaskNum[iDid] > 0) {
      DestroyTaskList (m_cEncodingTaskList[iDid]);
      DestroyTaskList (m_cPreEncodingTaskList[iDid]);
      m_iTaskNum[iDid] = 0;
      m_pcAllTaskList[CWelsBaseTask::WELS_ENC_TASK_ENCODING][iDid] = NULL;
    }
  }
  //fprintf(stdout, "[MT] CWelsTaskManageBase() DestroyTasks, cleaned %d tasks\n", m_iTotalTaskNum);
}

void  CWelsTaskManageBase::OnTaskMinusOne() {
  //fprintf(stdout, "OnTaskMinusOne event %x m_iWaitTaskNum=%d\n", &m_hEventMutex, m_iWaitTaskNum);
  WelsCommon::CWelsAutoLock cAutoLock (m_cWaitTaskNumLock);
  WelsEventSignal (&m_hTaskEvent, &m_hEventMutex, &m_iWaitTaskNum);
  /*WelsMutexLock(&m_hEventMutex);
  m_iWaitTaskNum --;
  WelsMutexUnlock(&m_hEventMutex);

  if (m_iWaitTaskNum <= 0) {
    WelsEventSignal (&m_hTaskEvent);
    fprintf(stdout, "OnTaskMinusOne WelsEventSignal m_iWaitTaskNum=%d\n", m_iWaitTaskNum);
  }*/
  //fprintf(stdout, "OnTaskMinusOne m_iWaitTaskNum=%d\n", m_iWaitTaskNum);
}

WelsErrorType  CWelsTaskManageBase::OnTaskCancelled() {
  OnTaskMinusOne();
  return ENC_RETURN_SUCCESS;
}

WelsErrorType  CWelsTaskManageBase::OnTaskExecuted() {
  OnTaskMinusOne();
  return ENC_RETURN_SUCCESS;
}

WelsErrorType  CWelsTaskManageBase::ExecuteTaskList (TASKLIST_TYPE** pTaskList) {
  m_iWaitTaskNum = m_iTaskNum[m_iCurDid];
  TASKLIST_TYPE* pTargetTaskList = (pTaskList[m_iCurDid]);
  //fprintf(stdout, "ExecuteTaskList m_iWaitTaskNum=%d\n", m_iWaitTaskNum);
  if (0 == m_iWaitTaskNum) {
    return ENC_RETURN_SUCCESS;
  }

  int32_t iCurrentTaskCount = m_iWaitTaskNum; //if directly use m_iWaitTaskNum in the loop make cause sync problem
  int32_t iIdx = 0;
  while (iIdx < iCurrentTaskCount) {
    m_pThreadPool->QueueTask (pTargetTaskList->getNode (iIdx));
    iIdx ++;
  }

  WelsEventWait (&m_hTaskEvent, &m_hEventMutex, m_iWaitTaskNum);

  return ENC_RETURN_SUCCESS;
}

void CWelsTaskManageBase::InitFrame (const int32_t kiCurDid) {
  m_iCurDid = kiCurDid;
  if (m_pEncCtx->pCurDqLayer->bNeedAdjustingSlicing) {
    ExecuteTaskList (m_pcAllTaskList[CWelsBaseTask::WELS_ENC_TASK_UPDATEMBMAP]);
  }
}

WelsErrorType  CWelsTaskManageBase::ExecuteTasks (const CWelsBaseTask::ETaskType iTaskType) {
  return ExecuteTaskList (m_pcAllTaskList[iTaskType]);
}

int32_t  CWelsTaskManageBase::GetThreadPoolThreadNum() {
  return m_pThreadPool->GetThreadNum();
}

// CWelsTaskManageOne is for test
WelsErrorType CWelsTaskManageOne::Init (sWelsEncCtx* pEncCtx) {
  m_pEncCtx = pEncCtx;

  return CreateTasks (pEncCtx, pEncCtx->iMaxSliceCount);
}

WelsErrorType  CWelsTaskManageOne::ExecuteTasks (const CWelsBaseTask::ETaskType iTaskType) {
  while (NULL != m_cEncodingTaskList[0]->begin()) {
    (m_cEncodingTaskList[0]->begin())->Execute();
    m_cEncodingTaskList[0]->pop_front();
  }
  return ENC_RETURN_SUCCESS;
}
// CWelsTaskManageOne is for test

}




