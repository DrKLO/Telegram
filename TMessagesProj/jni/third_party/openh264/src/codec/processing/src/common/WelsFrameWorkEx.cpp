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

#include "WelsFrameWork.h"

///////////////////////////////////////////////////////////////////////

WELSVP_NAMESPACE_BEGIN

EResult Init (void* pCtx, int32_t iType, void* pCfg) {
  return pCtx ? WelsStaticCast (IWelsVP*, pCtx)->Init (iType, pCfg) : RET_INVALIDPARAM;
}
EResult Uninit (void* pCtx, int32_t iType) {
  return pCtx ? WelsStaticCast (IWelsVP*, pCtx)->Uninit (iType) : RET_INVALIDPARAM;
}
EResult Flush (void* pCtx, int32_t iType) {
  return pCtx ? WelsStaticCast (IWelsVP*, pCtx)->Flush (iType) : RET_INVALIDPARAM;
}
EResult Process (void* pCtx, int32_t iType, SPixMap* pSrc, SPixMap* dst) {
  return pCtx ? WelsStaticCast (IWelsVP*, pCtx)->Process (iType, pSrc, dst) : RET_INVALIDPARAM;
}
EResult Get (void* pCtx, int32_t iType, void* pParam) {
  return pCtx ? WelsStaticCast (IWelsVP*, pCtx)->Get (iType, pParam) : RET_INVALIDPARAM;
}
EResult Set (void* pCtx, int32_t iType, void* pParam) {
  return pCtx ? WelsStaticCast (IWelsVP*, pCtx)->Set (iType, pParam) : RET_INVALIDPARAM;
}
EResult SpecialFeature (void* pCtx, int32_t iType, void* pIn, void* pOut) {
  return pCtx ? WelsStaticCast (IWelsVP*, pCtx)->SpecialFeature (iType, pIn, pOut) : RET_INVALIDPARAM;
}

///////////////////////////////////////////////////////////////////////////////

EResult CreateSpecificVpInterface (IWelsVPc** pCtx) {
  EResult  ret     = RET_FAILED;
  IWelsVP* pWelsVP = NULL;

  ret = CreateSpecificVpInterface (&pWelsVP);
  if (ret == RET_SUCCESS) {
    IWelsVPc* pVPc = new IWelsVPc;
    if (pVPc) {
      pVPc->Init    = Init;
      pVPc->Uninit  = Uninit;
      pVPc->Flush   = Flush;
      pVPc->Process = Process;
      pVPc->Get     = Get;
      pVPc->Set     = Set;
      pVPc->SpecialFeature = SpecialFeature;
      pVPc->pCtx       = WelsStaticCast (void*, pWelsVP);
      *pCtx            = pVPc;
    } else
      ret = RET_OUTOFMEMORY;
  }

  return ret;
}

EResult DestroySpecificVpInterface (IWelsVPc* pCtx) {
  if (pCtx) {
    DestroySpecificVpInterface (WelsStaticCast (IWelsVP*, pCtx->pCtx));
    delete pCtx;
  }

  return RET_SUCCESS;
}

WELSVP_NAMESPACE_END
