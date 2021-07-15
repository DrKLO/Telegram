/*!
 * \copy
 *     Copyright (c)  2013, Cisco Systems
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
 */

#ifdef _WIN32
#include <windows.h>
#include <tchar.h>
#endif

#include <stdio.h>
#include <stdarg.h>
#include <string.h>

#include "crt_util_safe_x.h" // Safe CRT routines like utils for cross platforms

#include "welsCodecTrace.h"
#include "utils.h"



static void welsStderrTrace (void* ctx, int level, const char* string) {
  fprintf (stderr, "%s\n", string);
}

welsCodecTrace::welsCodecTrace() {

  m_iTraceLevel = WELS_LOG_DEFAULT;
  m_fpTrace = welsStderrTrace;
  m_pTraceCtx = NULL;

  m_sLogCtx.pLogCtx = this;
  m_sLogCtx.pfLog = StaticCodecTrace;
  m_sLogCtx.pCodecInstance = NULL;
}

welsCodecTrace::~welsCodecTrace() {
  m_fpTrace = NULL;
}



void welsCodecTrace::StaticCodecTrace (void* pCtx, const int32_t iLevel, const char* Str_Format, va_list vl) {
  welsCodecTrace* self = (welsCodecTrace*) pCtx;
  self->CodecTrace (iLevel, Str_Format, vl);
}

void welsCodecTrace::CodecTrace (const int32_t iLevel, const char* Str_Format, va_list vl) {
  if (m_iTraceLevel < iLevel) {
    return;
  }

  char pBuf[MAX_LOG_SIZE] = {0};
  WelsVsnprintf (pBuf, MAX_LOG_SIZE, Str_Format, vl); // confirmed_safe_unsafe_usage
  if (m_fpTrace) {
    m_fpTrace (m_pTraceCtx, iLevel, pBuf);
  }
}

void welsCodecTrace::SetCodecInstance (void* pCodecInstance) {
  m_sLogCtx.pCodecInstance = pCodecInstance;
}

void welsCodecTrace::SetTraceLevel (const int32_t iLevel) {
  if (iLevel >= 0)
    m_iTraceLevel = iLevel;
}

void welsCodecTrace::SetTraceCallback (WelsTraceCallback func) {
  m_fpTrace = func;
}

void welsCodecTrace::SetTraceCallbackContext (void* ctx) {
  m_pTraceCtx = ctx;
}

