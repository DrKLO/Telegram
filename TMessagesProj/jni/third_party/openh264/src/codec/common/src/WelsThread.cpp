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
 * \file    WelsThreadPool.cpp
 *
 * \brief   functions for Thread Pool
 *
 * \date    5/09/2012 Created
 *
 *************************************************************************************
 */

#include "WelsThread.h"

namespace WelsCommon {

CWelsThread::CWelsThread() :
  m_hThread (0),
  m_bRunning (false),
  m_bEndFlag (false) {

  WelsEventOpen (&m_hEvent);
  WelsMutexInit(&m_hMutex);
  m_iConVar = 1;
}

CWelsThread::~CWelsThread() {
  Kill();
  WelsEventClose (&m_hEvent);
  WelsMutexDestroy(&m_hMutex);
}

void CWelsThread::Thread() {
  while (true) {
    WelsEventWait (&m_hEvent,&m_hMutex,m_iConVar);

    if (GetEndFlag()) {
      break;
    }

    m_iConVar = 1;
    ExecuteTask();//in ExecuteTask there will be OnTaskStop which opens the potential new Signaling of next run, so the setting of m_iConVar = 1 should be before ExecuteTask()
  }

  SetRunning (false);
}

WELS_THREAD_ERROR_CODE CWelsThread::Start() {
#ifndef __APPLE__
  if (NULL == m_hEvent) {
    return WELS_THREAD_ERROR_GENERAL;
  }
#endif
  if (GetRunning()) {
    return WELS_THREAD_ERROR_OK;
  }

  SetEndFlag (false);

  WELS_THREAD_ERROR_CODE rc = WelsThreadCreate (&m_hThread,
                              (LPWELS_THREAD_ROUTINE)TheThread, this, 0);

  if (WELS_THREAD_ERROR_OK != rc) {
    return rc;
  }

  while (!GetRunning()) {
    WelsSleep (1);
  }

  return WELS_THREAD_ERROR_OK;
}

void CWelsThread::Kill() {
  if (!GetRunning()) {
    return;
  }

  SetEndFlag (true);

  WelsEventSignal (&m_hEvent,&m_hMutex,&m_iConVar);
  WelsThreadJoin (m_hThread);
  return;
}

WELS_THREAD_ROUTINE_TYPE  CWelsThread::TheThread (void* pParam) {
  CWelsThread* pThis = static_cast<CWelsThread*> (pParam);

  pThis->SetRunning (true);

  pThis->Thread();

  WELS_THREAD_ROUTINE_RETURN (NULL);
}

}


