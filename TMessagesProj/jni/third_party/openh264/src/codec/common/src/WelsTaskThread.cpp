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
 * \file    WelsTaskThread.cpp
 *
 * \brief   functions for TaskThread
 *
 * \date    5/09/2012 Created
 *
 *************************************************************************************
 */
#include "WelsTaskThread.h"

namespace WelsCommon {

CWelsTaskThread::CWelsTaskThread (IWelsTaskThreadSink* pSink) : m_pSink (pSink) {
  WelsThreadSetName ("CWelsTaskThread");

  m_uiID = (uintptr_t) (this);
  m_pTask = NULL;
}


CWelsTaskThread::~CWelsTaskThread() {
}

void CWelsTaskThread::ExecuteTask() {
  CWelsAutoLock cLock (m_cLockTask);
  if (m_pSink) {
    m_pSink->OnTaskStart (this, m_pTask);
  }

  if (m_pTask) {
    m_pTask->Execute();
  }

  if (m_pSink) {
    m_pSink->OnTaskStop (this, m_pTask);
  }

  m_pTask = NULL;
}

WELS_THREAD_ERROR_CODE CWelsTaskThread::SetTask (WelsCommon::IWelsTask* pTask) {
  CWelsAutoLock cLock (m_cLockTask);

  if (!GetRunning()) {
    return WELS_THREAD_ERROR_GENERAL;
  }
  WelsMutexLock(&m_hMutex);
  m_pTask = pTask;
  WelsMutexUnlock(&m_hMutex);
  SignalThread();

  return WELS_THREAD_ERROR_OK;
}


}

