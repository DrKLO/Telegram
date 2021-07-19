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
 * \file    crt_utils_safe_x.cpp
 *
 * \brief   common tool/function utilization
 *
 * \date    03/10/2009 Created
 *
 *************************************************************************************
 */

#include <string.h>
#include <stdlib.h>
#include <math.h>
#include <time.h>
#if defined(_WIN32)
#include <windows.h>
#include <sys/types.h>
#include <sys/timeb.h>
#ifndef _MSC_VER
#include <sys/time.h>
#endif //!_MSC_VER
#else
#include <sys/time.h>
#endif //_WIN32

#include "macros.h"
#include "crt_util_safe_x.h" // Safe CRT routines like utils for cross platforms

#if defined(_WIN32) && defined(_MSC_VER)

#if defined(_MSC_VER) && (_MSC_VER>=1500)

int32_t WelsSnprintf (char* pBuffer,  int32_t iSizeOfBuffer, const char* kpFormat, ...) {
  va_list  pArgPtr;
  int32_t  iRc;

  va_start (pArgPtr, kpFormat);

  iRc = vsnprintf_s (pBuffer, iSizeOfBuffer, _TRUNCATE, kpFormat, pArgPtr);
  if (iRc < 0)
    iRc = iSizeOfBuffer;

  va_end (pArgPtr);

  return iRc;
}

char* WelsStrncpy (char* pDest, int32_t iSizeInBytes, const char* kpSrc) {
  strncpy_s (pDest, iSizeInBytes, kpSrc, _TRUNCATE);

  return pDest;
}

int32_t WelsVsnprintf (char* pBuffer, int32_t iSizeOfBuffer, const char* kpFormat, va_list pArgPtr) {
  int32_t iRc = vsnprintf_s (pBuffer, iSizeOfBuffer, _TRUNCATE, kpFormat, pArgPtr);
  if (iRc < 0)
    iRc = iSizeOfBuffer;
  return iRc;
}

WelsFileHandle* WelsFopen (const char* kpFilename,  const char* kpMode) {
  WelsFileHandle* pFp = NULL;
  if (fopen_s (&pFp, kpFilename, kpMode) != 0) {
    return NULL;
  }

  return pFp;
}

int32_t WelsFclose (WelsFileHandle* pFp) {
  return fclose (pFp);
}

int32_t WelsGetTimeOfDay (SWelsTime* pTp) {
  return _ftime_s (pTp);
}

int32_t WelsStrftime (char* pBuffer, int32_t iSize, const char* kpFormat, const SWelsTime* kpTp) {
  struct tm   sTimeNow;
  int32_t iRc;

  localtime_s (&sTimeNow, &kpTp->time);

  iRc = (int32_t)strftime (pBuffer, iSize, kpFormat, &sTimeNow);
  if (iRc == 0)
    pBuffer[0] = '\0';
  return iRc;
}

#else

int32_t WelsSnprintf (char* pBuffer,  int32_t iSizeOfBuffer, const char* kpFormat, ...) {
  va_list pArgPtr;
  int32_t iRc;

  va_start (pArgPtr, kpFormat);

  iRc = vsnprintf (pBuffer, iSizeOfBuffer, kpFormat, pArgPtr); //confirmed_safe_unsafe_usage
  if (iRc < 0) {
    pBuffer[iSizeOfBuffer - 1] = '\0';
    iRc = iSizeOfBuffer;
  }

  va_end (pArgPtr);

  return iRc;
}

char* WelsStrncpy (char* pDest, int32_t iSizeInBytes, const char* kpSrc) {
  strncpy (pDest, kpSrc, iSizeInBytes); //confirmed_safe_unsafe_usage
  pDest[iSizeInBytes - 1] = '\0';

  return pDest;
}

int32_t WelsVsnprintf (char* pBuffer, int32_t iSizeOfBuffer, const char* kpFormat, va_list pArgPtr) {
  int32_t iRc = vsnprintf (pBuffer, iSizeOfBuffer, kpFormat, pArgPtr); //confirmed_safe_unsafe_usage
  if (iRc < 0) {
    pBuffer[iSizeOfBuffer - 1] = '\0';
    iRc = iSizeOfBuffer;
  }
  return iRc;
}


WelsFileHandle* WelsFopen (const char* kpFilename,  const char* kpMode) {
  return fopen (kpFilename, kpMode);
}

int32_t WelsFclose (WelsFileHandle* pFp) {
  return fclose (pFp);
}

int32_t WelsGetTimeOfDay (SWelsTime* pTp) {
  _ftime (pTp);
  return 0;
}

int32_t WelsStrftime (char* pBuffer, int32_t iSize, const char* kpFormat, const SWelsTime* kpTp) {
  struct tm*   pTnow;
  int32_t iRc;

  pTnow = localtime (&kpTp->time);

  iRc = strftime (pBuffer, iSize, kpFormat, pTnow);
  if (iRc == 0)
    pBuffer[0] = '\0';
  return iRc;
}


#endif // _MSC_VER

#else  //GCC

int32_t WelsSnprintf (char* pBuffer,  int32_t iSizeOfBuffer, const char* kpFormat, ...) {
  va_list pArgPtr;
  int32_t iRc;

  va_start (pArgPtr, kpFormat);

  iRc = vsnprintf (pBuffer, iSizeOfBuffer, kpFormat, pArgPtr);

  va_end (pArgPtr);

  return iRc;
}

char* WelsStrncpy (char* pDest, int32_t iSizeInBytes, const char* kpSrc) {
  strncpy (pDest, kpSrc, iSizeInBytes); //confirmed_safe_unsafe_usage
  pDest[iSizeInBytes - 1] = '\0';
  return pDest;
}

int32_t WelsVsnprintf (char* pBuffer, int32_t iSizeOfBuffer, const char* kpFormat, va_list pArgPtr) {
  return vsnprintf (pBuffer, iSizeOfBuffer, kpFormat, pArgPtr); //confirmed_safe_unsafe_usage
}

WelsFileHandle* WelsFopen (const char* kpFilename,  const char* kpMode) {
  return fopen (kpFilename, kpMode);
}

int32_t WelsFclose (WelsFileHandle*   pFp) {
  return fclose (pFp);
}

int32_t WelsGetTimeOfDay (SWelsTime* pTp) {
  struct timeval  sTv;

  if (gettimeofday (&sTv, NULL)) {
    return -1;
  }

  pTp->time = sTv.tv_sec;
  pTp->millitm = (uint16_t)sTv.tv_usec / 1000;

  return 0;
}

int32_t WelsStrftime (char* pBuffer, int32_t iSize, const char* kpFormat, const SWelsTime* kpTp) {
  struct tm*   pTnow;
  int32_t iRc;

  pTnow = localtime (&kpTp->time);

  iRc = (int32_t) strftime (pBuffer, iSize, kpFormat, pTnow);
  if (iRc == 0)
    pBuffer[0] = '\0';
  return iRc;
}

#endif


char* WelsStrcat (char* pDest, uint32_t uiSizeInBytes, const char* kpSrc) {
  uint32_t uiCurLen = (uint32_t) strlen (pDest);
  if (uiSizeInBytes > uiCurLen)
    return WelsStrncpy (pDest + uiCurLen, uiSizeInBytes - uiCurLen, kpSrc);
  return pDest;
}

int32_t WelsFwrite (const void* kpBuffer, int32_t iSize, int32_t iCount, WelsFileHandle* pFp) {
  return (int32_t)fwrite (kpBuffer, iSize, iCount, pFp);
}

uint16_t WelsGetMillisecond (const SWelsTime* kpTp) {
  return kpTp->millitm;
}

int32_t WelsFseek (WelsFileHandle* fp, int32_t offset, int32_t origin) {
  return fseek (fp, offset, origin);
}

int32_t WelsFflush (WelsFileHandle* pFp) {
  return fflush (pFp);
}
