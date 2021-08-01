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

#include "memory.h"

WELSVP_NAMESPACE_BEGIN
/////////////////////////////////////////////////////////////////////////////////

void* WelsMalloc (const uint32_t kuiSize, char* pTag) {
  const int32_t kiSizeVoidPointer       = sizeof (void**);
  const int32_t kiSizeInt32             = sizeof (int32_t);
  const int32_t kiAlignedBytes          = ALIGNBYTES - 1;

  uint8_t* pBuf         = (uint8_t*) ::malloc (kuiSize + kiAlignedBytes + kiSizeVoidPointer + kiSizeInt32);
  uint8_t* pAlignedBuf = NULL;

  if (NULL == pBuf)
    return NULL;

  // to fill zero values
  WelsMemset (pBuf, 0, kuiSize + kiAlignedBytes + kiSizeVoidPointer + kiSizeInt32);

  pAlignedBuf = pBuf + kiAlignedBytes + kiSizeVoidPointer + kiSizeInt32;
  pAlignedBuf -= WelsCastFromPointer (pAlignedBuf) & kiAlignedBytes;
  * ((void**) (pAlignedBuf - kiSizeVoidPointer)) = pBuf;
  * ((int32_t*) (pAlignedBuf - (kiSizeVoidPointer + kiSizeInt32))) = kuiSize;

  return (pAlignedBuf);
}

/////////////////////////////////////////////////////////////////////////////

void WelsFree (void* pPointer, char* pTag) {
  if (pPointer) {
    ::free (* (((void**) pPointer) - 1));
  }
}

/////////////////////////////////////////////////////////////////////////////

void* InternalReallocate (void* pPointer, const uint32_t kuiSize, char* pTag) {
  uint32_t iOldSize = 0;
  uint8_t* pNew = NULL;
  if (pPointer != NULL)
    iOldSize = * ((int32_t*) ((uint8_t*) pPointer - sizeof (void**) - sizeof (int32_t)));
  else
    return WelsMalloc (kuiSize, pTag);

  pNew = (uint8_t*)WelsMalloc (kuiSize, pTag);
  if (0 == pNew) {
    if (iOldSize > 0 && kuiSize > 0 && iOldSize >= kuiSize)
      return (pPointer);
    return 0;
  } else if (iOldSize > 0 && kuiSize > 0)
    memcpy (pNew, pPointer, (iOldSize < kuiSize) ? iOldSize : kuiSize);
  else
    return 0;

  WelsFree (pPointer, pTag);
  return (pNew);
}

/////////////////////////////////////////////////////////////////////////////

void* WelsRealloc (void* pPointer, uint32_t* pRealSize, const uint32_t kuiSize, char* pTag) {
  const uint32_t kuiOldSize = *pRealSize;
  uint32_t kuiNewSize = 0;
  void* pLocalPointer = NULL;
  if (kuiOldSize >= kuiSize) // large enough of original block, so do nothing
    return (pPointer);

  // new request
  kuiNewSize = kuiSize + 15;
  kuiNewSize -= (kuiNewSize & 15);
  kuiNewSize += 32;

  pLocalPointer = InternalReallocate (pPointer, kuiNewSize, pTag);
  if (NULL != pLocalPointer) {
    *pRealSize = kuiNewSize;
    return (pLocalPointer);
  } else {
    return NULL;
  }

  return NULL; // something wrong
}

WELSVP_NAMESPACE_END
