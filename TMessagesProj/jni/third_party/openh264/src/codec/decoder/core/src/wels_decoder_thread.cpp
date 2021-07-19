/*!
 * \copy
 *     Copyright (c)  2009-2019, Cisco Systems
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
 * \file    wels_decoder_thread.cpp
 *
 * \brief   Interfaces introduced in thread programming
 *
 * \date    08/06/2018 Created
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

#include "wels_decoder_thread.h"
#include <stdio.h>
#include <stdlib.h>

int32_t GetCPUCount() {
  WelsLogicalProcessInfo pInfo;
  pInfo.ProcessorCount = 1;
  WelsQueryLogicalProcessInfo (&pInfo);
  return pInfo.ProcessorCount;
}

int ThreadCreate (SWelsDecThread* t, LPWELS_THREAD_ROUTINE tf, void* ta) {
  WELS_THREAD_ATTR attr = 0;
  return WelsThreadCreate (& (t->h), tf, ta, attr);
}

int ThreadWait (SWelsDecThread* t) {
  return WelsThreadJoin (t->h);
}

#if defined(_WIN32) || defined(__CYGWIN__)

int EventCreate (SWelsDecEvent* e, int manualReset, int initialState) {
  e->h = CreateEvent (NULL, manualReset, initialState, NULL);
  e->isSignaled = initialState;
  return (e->h != NULL) ? 0 : 1;
}

void EventReset (SWelsDecEvent* e) {
  ResetEvent (e->h);
  e->isSignaled = 0;
}

void EventPost (SWelsDecEvent* e) {
  SetEvent (e->h);
  e->isSignaled = 1;
}

int EventWait (SWelsDecEvent* e, int32_t timeout) {
  DWORD result;
  if ((uint32_t)timeout == WELS_DEC_THREAD_WAIT_INFINITE || timeout < 0)
    result = WaitForSingleObject (e->h, INFINITE);
  else
    result = WaitForSingleObject (e->h, timeout);

  if (result == WAIT_OBJECT_0)
    return WELS_DEC_THREAD_WAIT_SIGNALED;
  else
    return WAIT_TIMEOUT;
}

void EventDestroy (SWelsDecEvent* e) {
  CloseHandle (e->h);
  e->h = NULL;
}

int SemCreate (SWelsDecSemphore* s, long value, long max) {
  s->h = CreateSemaphore (NULL, value, max, NULL);
  return (s->h != NULL) ? 0 : 1;
}

int SemWait (SWelsDecSemphore* s, int32_t timeout) {
  DWORD result;
  if ((uint32_t)timeout == WELS_DEC_THREAD_WAIT_INFINITE || timeout < 0)
    result = WaitForSingleObject (s->h, INFINITE);
  else
    result = WaitForSingleObject (s->h, timeout);

  if (result == WAIT_OBJECT_0) {
    return WELS_DEC_THREAD_WAIT_SIGNALED;
  } else {
    return WELS_DEC_THREAD_WAIT_TIMEDOUT;
  }
}

void SemRelease (SWelsDecSemphore* s, long* prevcount) {
  ReleaseSemaphore (s->h, 1, prevcount);
}

void SemDestroy (SWelsDecSemphore* s) {
  CloseHandle (s->h);
  s->h = NULL;
}

#else /* _WIN32 */

static void getTimespecFromTimeout (struct timespec* ts, int32_t timeout) {
  struct timeval tv;
  gettimeofday (&tv, 0);
  ts->tv_nsec = tv.tv_usec * 1000 + timeout * 1000000;
  ts->tv_sec = tv.tv_sec + ts->tv_nsec / 1000000000;
  ts->tv_nsec %= 1000000000;
}
int EventCreate (SWelsDecEvent* e, int manualReset, int initialState) {
  if (pthread_mutex_init (& (e->m), NULL))
    return 1;
  if (pthread_cond_init (& (e->c), NULL))
    return 2;

  e->isSignaled = initialState;
  e->manualReset = manualReset;

  return 0;
}

void EventReset (SWelsDecEvent* e) {
  pthread_mutex_lock (& (e->m));
  e->isSignaled = 0;
  pthread_mutex_unlock (& (e->m));
}

void EventPost (SWelsDecEvent* e) {
  pthread_mutex_lock (& (e->m));
  pthread_cond_broadcast (& (e->c));
  e->isSignaled = 1;
  pthread_mutex_unlock (& (e->m));
}

int EventWait (SWelsDecEvent* e, int32_t timeout) {
  pthread_mutex_lock (& (e->m));
  int signaled = e->isSignaled;
  if (timeout == 0) {
    pthread_mutex_unlock (& (e->m));
    if (signaled)
      return WELS_DEC_THREAD_WAIT_SIGNALED;
    else
      return WELS_DEC_THREAD_WAIT_TIMEDOUT;
  }
  if (signaled) {
    if (!e->manualReset) {
      e->isSignaled = 0;
    }
    pthread_mutex_unlock (& (e->m));
    return WELS_DEC_THREAD_WAIT_SIGNALED;
  }
  int rc = 0;
  if (timeout == WELS_DEC_THREAD_WAIT_INFINITE || timeout < 0) {
    rc = pthread_cond_wait (& (e->c), & (e->m));
  } else {
    struct timespec ts;
    getTimespecFromTimeout (&ts, timeout);
    rc = pthread_cond_timedwait (& (e->c), & (e->m), &ts);
  }
  if (!e->manualReset) {
    e->isSignaled = 0;
  }
  pthread_mutex_unlock (& (e->m));
  if (rc == 0)
    return WELS_DEC_THREAD_WAIT_SIGNALED;
  else
    return WELS_DEC_THREAD_WAIT_TIMEDOUT;
}

void EventDestroy (SWelsDecEvent* e) {
  pthread_mutex_destroy (& (e->m));
  pthread_cond_destroy (& (e->c));
}

int SemCreate (SWelsDecSemphore* s, long value, long max) {
  s->v = value;
  s->max = max;
  if (pthread_mutex_init (& (s->m), NULL))
    return 1;
  const char* event_name = "";
  if (WelsEventOpen (& (s->e), event_name)) {
    return 2;
  }
  return 0;
}

int SemWait (SWelsDecSemphore* s, int32_t timeout) {
#if defined(__APPLE__)
  pthread_mutex_lock (& (s->m));
#endif
  int rc = 0;
  if (timeout != 0) {
    while ((s->v) == 0) {
      if (timeout == WELS_DEC_THREAD_WAIT_INFINITE || timeout < 0) {
        // infinite wait until released
#if defined(__APPLE__)
        rc = pthread_cond_wait (& (s->e), & (s->m));
#else
        rc = sem_wait (s->e);
        if (rc != 0) rc = errno;
#endif
      } else {
        struct timespec ts;
        getTimespecFromTimeout (&ts, timeout);
#if defined(__APPLE__)
        rc = pthread_cond_timedwait (& (s->e), & (s->m), &ts);
#else
        rc = sem_timedwait (s->e, &ts);
        if (rc != 0) rc = errno;
#endif
        if (rc != EINTR) {
          // if timed out we return to the caller
          break;
        }
      }
    }
    // only decrement counter if semaphore was signaled
    if (rc == 0)
      s->v -= 1;

  } else {
    // Special handling for timeout of 0
    if (s->v > 0) {
      s->v -= 1;
      rc = 0;
    } else {
      rc = 1;
    }
  }
#if defined(__APPLE__)
  pthread_mutex_unlock (& (s->m));
#endif
  // set return value
  if (rc == 0)
    return WELS_DEC_THREAD_WAIT_SIGNALED;
  else
    return WELS_DEC_THREAD_WAIT_TIMEDOUT;
}

void SemRelease (SWelsDecSemphore* s, long* o_pPrevCount) {
  long prevcount;
#ifdef __APPLE__
  pthread_mutex_lock (& (s->m));
  prevcount = s->v;
  if (s->v < s->max)
    s->v += 1;
  pthread_cond_signal (& (s->e));
  pthread_mutex_unlock (& (s->m));
#else
  prevcount = s->v;
  if (s->v < s->max)
    s->v += 1;
  sem_post (s->e);
#endif
  if (o_pPrevCount != NULL) {
    *o_pPrevCount = prevcount;
  }
}

void SemDestroy (SWelsDecSemphore* s) {
  pthread_mutex_destroy (& (s->m));
  const char* event_name = "";
  WelsEventClose (& (s->e), event_name);
}

#endif /* !_WIN32 */

