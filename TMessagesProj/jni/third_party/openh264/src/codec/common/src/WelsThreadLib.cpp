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
 * \file    WelsThreadLib.c
 *
 * \brief   Interfaces introduced in thread programming
 *
 * \date    11/17/2009 Created
 *
 *************************************************************************************
 */


#ifdef __linux__
#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include <sched.h>
#elif !defined(_WIN32) && !defined(__CYGWIN__)
#include <sys/types.h>
#include <sys/param.h>
#include <unistd.h>
#ifndef __Fuchsia__
#include <sys/sysctl.h>
#endif
#ifdef __APPLE__
#define HW_NCPU_NAME "hw.logicalcpu"
#else
#define HW_NCPU_NAME "hw.ncpu"
#endif
#endif
#ifdef ANDROID_NDK
#include <cpu-features.h>
#endif
#ifdef __ANDROID__
#include <android/api-level.h>
#endif

#include "WelsThreadLib.h"
#include <stdio.h>
#include <stdlib.h>


#if defined(_WIN32) || defined(__CYGWIN__)

WELS_THREAD_ERROR_CODE    WelsMutexInit (WELS_MUTEX*    mutex) {
  InitializeCriticalSection (mutex);

  return WELS_THREAD_ERROR_OK;
}

WELS_THREAD_ERROR_CODE    WelsMutexLock (WELS_MUTEX*    mutex) {
  EnterCriticalSection (mutex);

  return WELS_THREAD_ERROR_OK;
}

WELS_THREAD_ERROR_CODE    WelsMutexUnlock (WELS_MUTEX* mutex) {
  LeaveCriticalSection (mutex);

  return WELS_THREAD_ERROR_OK;
}

WELS_THREAD_ERROR_CODE    WelsMutexDestroy (WELS_MUTEX* mutex) {
  DeleteCriticalSection (mutex);

  return WELS_THREAD_ERROR_OK;
}

#else /* _WIN32 */

WELS_THREAD_ERROR_CODE    WelsMutexInit (WELS_MUTEX*    mutex) {
  return pthread_mutex_init (mutex, NULL);
}

WELS_THREAD_ERROR_CODE    WelsMutexLock (WELS_MUTEX*    mutex) {
  return pthread_mutex_lock (mutex);
}

WELS_THREAD_ERROR_CODE    WelsMutexUnlock (WELS_MUTEX* mutex) {
  return pthread_mutex_unlock (mutex);
}

WELS_THREAD_ERROR_CODE    WelsMutexDestroy (WELS_MUTEX* mutex) {
  return pthread_mutex_destroy (mutex);
}

#endif /* !_WIN32 */

#if defined(_WIN32) || defined(__CYGWIN__)

WELS_THREAD_ERROR_CODE    WelsEventOpen (WELS_EVENT* event, const char* event_name) {
  WELS_EVENT   h = CreateEvent (NULL, FALSE, FALSE, NULL);
  *event = h;
  if (h == NULL) {
    return WELS_THREAD_ERROR_GENERAL;
  }
  return WELS_THREAD_ERROR_OK;
}

WELS_THREAD_ERROR_CODE    WelsEventSignal (WELS_EVENT* event, WELS_MUTEX *pMutex, int* iCondition) {
  (*iCondition) --;
  if ((*iCondition) <= 0) {
    if (SetEvent (*event)) {
      return WELS_THREAD_ERROR_OK;
    }
  }
  return WELS_THREAD_ERROR_GENERAL;
}

WELS_THREAD_ERROR_CODE    WelsEventWait (WELS_EVENT* event, WELS_MUTEX* pMutex, int& iCondition) {
  return WaitForSingleObject (*event, INFINITE);
}

WELS_THREAD_ERROR_CODE    WelsEventWaitWithTimeOut (WELS_EVENT* event, uint32_t dwMilliseconds, WELS_MUTEX* pMutex) {
  return WaitForSingleObject (*event, dwMilliseconds);
}

WELS_THREAD_ERROR_CODE    WelsMultipleEventsWaitSingleBlocking (uint32_t nCount,
    WELS_EVENT* event_list, WELS_EVENT* master_even, WELS_MUTEX* pMutext) {
  // Don't need/use the master event for anything, since windows has got WaitForMultipleObjects
  return WaitForMultipleObjects (nCount, event_list, FALSE, INFINITE);
}

WELS_THREAD_ERROR_CODE    WelsEventClose (WELS_EVENT* event, const char* event_name) {
  CloseHandle (*event);

  *event = NULL;
  return WELS_THREAD_ERROR_OK;
}

#ifndef WP80
void WelsSleep (uint32_t dwMilliSecond) {
  ::Sleep (dwMilliSecond);
}
#else
void WelsSleep (uint32_t dwMilliSecond) {
  static WELS_EVENT hSleepEvent = NULL;
  if (!hSleepEvent) {
    WELS_EVENT hLocalSleepEvent = NULL;
    WELS_THREAD_ERROR_CODE ret = WelsEventOpen (&hLocalSleepEvent);
    if (WELS_THREAD_ERROR_OK != ret) {
      return;
    }
    WELS_EVENT hPreviousEvent = InterlockedCompareExchangePointerRelease (&hSleepEvent, hLocalSleepEvent, NULL);
    if (hPreviousEvent) {
      WelsEventClose (&hLocalSleepEvent);
    }
    //On this singleton usage idea of using InterlockedCompareExchangePointerRelease:
    //   similar idea of can be found at msdn blog when introducing InterlockedCompareExchangePointerRelease
  }

  WaitForSingleObject (hSleepEvent, dwMilliSecond);
}
#endif

WELS_THREAD_ERROR_CODE    WelsThreadCreate (WELS_THREAD_HANDLE* thread,  LPWELS_THREAD_ROUTINE  routine,
    void* arg, WELS_THREAD_ATTR attr) {
  WELS_THREAD_HANDLE   h = CreateThread (NULL, 0, routine, arg, 0, NULL);

  if (h == NULL) {
    return WELS_THREAD_ERROR_GENERAL;
  }
  * thread = h;

  return WELS_THREAD_ERROR_OK;
}

WELS_THREAD_ERROR_CODE WelsThreadSetName (const char* thread_name) {
  // do nothing
  return WELS_THREAD_ERROR_OK;
}


WELS_THREAD_ERROR_CODE    WelsThreadJoin (WELS_THREAD_HANDLE  thread) {
  WaitForSingleObject (thread, INFINITE);
  CloseHandle (thread);

  return WELS_THREAD_ERROR_OK;
}


WELS_THREAD_HANDLE        WelsThreadSelf() {
  return GetCurrentThread();
}

WELS_THREAD_ERROR_CODE    WelsQueryLogicalProcessInfo (WelsLogicalProcessInfo* pInfo) {
  SYSTEM_INFO  si;

  GetSystemInfo (&si);

  pInfo->ProcessorCount = si.dwNumberOfProcessors;

  return WELS_THREAD_ERROR_OK;
}

#else //platform: #ifdef _WIN32

WELS_THREAD_ERROR_CODE    WelsThreadCreate (WELS_THREAD_HANDLE* thread,  LPWELS_THREAD_ROUTINE  routine,
    void* arg, WELS_THREAD_ATTR attr) {
  WELS_THREAD_ERROR_CODE err = 0;

  pthread_attr_t at;
  err = pthread_attr_init (&at);
  if (err)
    return err;
#if !defined(__ANDROID__) && !defined(__Fuchsia__)
  err = pthread_attr_setscope (&at, PTHREAD_SCOPE_SYSTEM);
  if (err)
    return err;
  err = pthread_attr_setschedpolicy (&at, SCHED_FIFO);
  if (err)
    return err;
#endif
  err = pthread_create (thread, &at, routine, arg);

  pthread_attr_destroy (&at);

  return err;
}

WELS_THREAD_ERROR_CODE WelsThreadSetName (const char* thread_name) {
#ifdef APPLE_IOS
  pthread_setname_np (thread_name);
#endif
#if defined(__ANDROID__) && __ANDROID_API__ >= 9
  pthread_setname_np (pthread_self(), thread_name);
#endif
  // do nothing
  return WELS_THREAD_ERROR_OK;
}

WELS_THREAD_ERROR_CODE    WelsThreadJoin (WELS_THREAD_HANDLE  thread) {
  return pthread_join (thread, NULL);
}

WELS_THREAD_HANDLE        WelsThreadSelf() {
  return pthread_self();
}

// unnamed semaphores aren't supported on OS X

WELS_THREAD_ERROR_CODE    WelsEventOpen (WELS_EVENT* p_event, const char* event_name) {
#ifdef __APPLE__
  WELS_THREAD_ERROR_CODE err= pthread_cond_init (p_event, NULL);
  return err;
#else
  WELS_EVENT event = (WELS_EVENT) malloc (sizeof (*event));
  if (event == NULL){
    *p_event = NULL;
    return WELS_THREAD_ERROR_GENERAL;
  }
  WELS_THREAD_ERROR_CODE err = sem_init (event, 0, 0);
  if (!err) {
    *p_event = event;
    return err;
  }
  free (event);
  *p_event = NULL;
  return err;
#endif
}
WELS_THREAD_ERROR_CODE    WelsEventClose (WELS_EVENT* event, const char* event_name) {
  //printf("event_close:%x, %s\n", event, event_name);
#ifdef __APPLE__
  WELS_THREAD_ERROR_CODE err = pthread_cond_destroy (event);
  return err;
#else
  WELS_THREAD_ERROR_CODE err = sem_destroy (*event); // match with sem_init
  free (*event);
  *event = NULL;
  return err;
#endif
}

void WelsSleep (uint32_t dwMilliSecond) {
  usleep (dwMilliSecond * 1000);
}

WELS_THREAD_ERROR_CODE   WelsEventSignal (WELS_EVENT* event, WELS_MUTEX *pMutex, int* iCondition) {
  WELS_THREAD_ERROR_CODE err = 0;
  //fprintf( stderr, "before signal it, event=%x iCondition= %d..\n", event, *iCondition );
#ifdef __APPLE__
  WelsMutexLock (pMutex);
  (*iCondition) --;
  WelsMutexUnlock (pMutex);
  if ((*iCondition) <= 0) {
  err = pthread_cond_signal (event);
  //fprintf( stderr, "signal it, event=%x iCondition= %d..\n",event, *iCondition );

  }
#else
    (*iCondition) --;
    if ((*iCondition) <= 0) {
//  int32_t val = 0;
//  sem_getvalue(event, &val);
//  fprintf( stderr, "before signal it, val= %d..\n",val );
  if (event != NULL)
    err = sem_post (*event);
//  sem_getvalue(event, &val);
    //fprintf( stderr, "signal it, event=%x iCondition= %d..\n",event, *iCondition );
    }
#endif
  //fprintf( stderr, "after signal it, event=%x  iCondition= %d..\n",event, *iCondition );
  return err;
}

WELS_THREAD_ERROR_CODE WelsEventWait (WELS_EVENT* event, WELS_MUTEX* pMutex, int& iCondition) {
#ifdef __APPLE__
  int err = 0;
  WelsMutexLock(pMutex);
  //fprintf( stderr, "WelsEventWait event %x %d..\n", event, iCondition );
  while (iCondition>0) {
    err = pthread_cond_wait (event, pMutex);
  }
  WelsMutexUnlock(pMutex);
  return err;
#else
  return sem_wait (*event); // blocking until signaled
#endif
}

WELS_THREAD_ERROR_CODE    WelsEventWaitWithTimeOut (WELS_EVENT* event, uint32_t dwMilliseconds, WELS_MUTEX* pMutex) {

  if (dwMilliseconds != (uint32_t) - 1) {
#if defined(__APPLE__)
    return pthread_cond_wait (event, pMutex);
#else
    return sem_wait (*event);
#endif
  } else {
    struct timespec ts;
    struct timeval tv;

    gettimeofday (&tv, 0);

    ts.tv_nsec = tv.tv_usec * 1000 + dwMilliseconds * 1000000;
    ts.tv_sec = tv.tv_sec + ts.tv_nsec / 1000000000;
    ts.tv_nsec %= 1000000000;

#if defined(__APPLE__)
    return pthread_cond_timedwait (event, pMutex, &ts);
#else
    return sem_timedwait (*event, &ts);
#endif
  }

}

WELS_THREAD_ERROR_CODE    WelsMultipleEventsWaitSingleBlocking (uint32_t nCount,
    WELS_EVENT* event_list, WELS_EVENT* master_event, WELS_MUTEX* pMutex) {
  uint32_t nIdx = 0;
  uint32_t uiAccessTime = 2; // 2 us once

  if (nCount == 0)
    return WELS_THREAD_ERROR_WAIT_FAILED;
#if defined(__APPLE__)
  if (master_event != NULL) {
    // This design relies on the events actually being semaphores;
    // if multiple events in the list have been signalled, the master
    // event should have a similar count (events in windows can't keep
    // track of the actual count, but the master event isn't needed there
    // since it uses WaitForMultipleObjects).
    int32_t err = pthread_cond_wait (master_event, pMutex);
    if (err != WELS_THREAD_ERROR_OK)
      return err;
    uiAccessTime = 0; // no blocking, just quickly loop through all to find the one that was signalled
  }

  while (1) {
    nIdx = 0; // access each event by order
    while (nIdx < nCount) {
      int32_t err = 0;
      int32_t wait_count = 0;

      /*
       * although such interface is not used in __GNUC__ like platform, to use
       * pthread_cond_timedwait() might be better choice if need
       */
      do {
        err = pthread_cond_wait (&event_list[nIdx], pMutex);
        if (WELS_THREAD_ERROR_OK == err)
          return WELS_THREAD_ERROR_WAIT_OBJECT_0 + nIdx;
        else if (wait_count > 0 || uiAccessTime == 0)
          break;
        usleep (uiAccessTime);
        ++ wait_count;
      } while (1);
      // we do need access next event next time
      ++ nIdx;
    }
    usleep (1); // switch to working threads
    if (master_event != NULL) {
      // A master event was used and was signalled, but none of the events in the
      // list was found to be signalled, thus wait a little more when rechecking
      // the list to avoid busylooping here.
      // If we ever hit this codepath it's mostly a bug in the code that signals
      // the events.
      uiAccessTime = 2;
    }
  }
#else
  if (master_event != NULL) {
    // This design relies on the events actually being semaphores;
    // if multiple events in the list have been signalled, the master
    // event should have a similar count (events in windows can't keep
    // track of the actual count, but the master event isn't needed there
    // since it uses WaitForMultipleObjects).
    int32_t err = sem_wait (*master_event);
    if (err != WELS_THREAD_ERROR_OK)
      return err;
    uiAccessTime = 0; // no blocking, just quickly loop through all to find the one that was signalled
  }

  while (1) {
    nIdx = 0; // access each event by order
    while (nIdx < nCount) {
      int32_t err = 0;
      int32_t wait_count = 0;

      /*
       * although such interface is not used in __GNUC__ like platform, to use
       * pthread_cond_timedwait() might be better choice if need
       */
      do {
        err = sem_trywait (event_list[nIdx]);
        if (WELS_THREAD_ERROR_OK == err)
          return WELS_THREAD_ERROR_WAIT_OBJECT_0 + nIdx;
        else if (wait_count > 0 || uiAccessTime == 0)
          break;
        usleep (uiAccessTime);
        ++ wait_count;
      } while (1);
      // we do need access next event next time
      ++ nIdx;
    }
    usleep (1); // switch to working threads
    if (master_event != NULL) {
      // A master event was used and was signalled, but none of the events in the
      // list was found to be signalled, thus wait a little more when rechecking
      // the list to avoid busylooping here.
      // If we ever hit this codepath it's mostly a bug in the code that signals
      // the events.
      uiAccessTime = 2;
    }
  }

#endif
  return WELS_THREAD_ERROR_WAIT_FAILED;
}

WELS_THREAD_ERROR_CODE    WelsQueryLogicalProcessInfo (WelsLogicalProcessInfo* pInfo) {
#ifdef ANDROID_NDK
  pInfo->ProcessorCount = android_getCpuCount();
  return WELS_THREAD_ERROR_OK;
#elif defined(__linux__)

  cpu_set_t cpuset;

  CPU_ZERO (&cpuset);

  if (!sched_getaffinity (0, sizeof (cpuset), &cpuset)) {
#ifdef CPU_COUNT
    pInfo->ProcessorCount = CPU_COUNT (&cpuset);
#else
    int32_t count = 0;
    for (int i = 0; i < CPU_SETSIZE; i++) {
      if (CPU_ISSET (i, &cpuset)) {
        count++;
      }
    }
    pInfo->ProcessorCount = count;
#endif
  } else {
    pInfo->ProcessorCount = 1;
  }

  return WELS_THREAD_ERROR_OK;

#elif defined(__EMSCRIPTEN__)

  // There is not yet a way to determine CPU count in emscripten JS environment.
  pInfo->ProcessorCount = 1;
  return WELS_THREAD_ERROR_OK;

#elif defined(__Fuchsia__)

  pInfo->ProcessorCount = sysconf(_SC_NPROCESSORS_ONLN);
  return WELS_THREAD_ERROR_OK;
#else

  size_t len = sizeof (pInfo->ProcessorCount);

#if defined(__OpenBSD__)
  int scname[] = { CTL_HW, HW_NCPU };
  if (sysctl (scname, 2, &pInfo->ProcessorCount, &len, NULL, 0) == -1)
#else
  if (sysctlbyname (HW_NCPU_NAME, &pInfo->ProcessorCount, &len, NULL, 0) == -1)
#endif
    pInfo->ProcessorCount = 1;

  return WELS_THREAD_ERROR_OK;

#endif//__linux__
}

#endif
