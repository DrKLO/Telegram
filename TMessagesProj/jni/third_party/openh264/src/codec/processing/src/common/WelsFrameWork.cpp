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
#include "../denoise/denoise.h"
#include "../downsample/downsample.h"
#include "../scrolldetection/ScrollDetection.h"
#include "../scenechangedetection/SceneChangeDetection.h"
#include "../vaacalc/vaacalculation.h"
#include "../backgrounddetection/BackgroundDetection.h"
#include "../adaptivequantization/AdaptiveQuantization.h"
#include "../complexityanalysis/ComplexityAnalysis.h"
#include "../imagerotate/imagerotate.h"
#include "util.h"

/* interface API implement */

EResult WelsCreateVpInterface (void** ppCtx, int iVersion) {
  if (iVersion & 0x8000)
    return WelsVP::CreateSpecificVpInterface ((IWelsVP**)ppCtx);
  else if (iVersion & 0x7fff)
    return WelsVP::CreateSpecificVpInterface ((IWelsVPc**)ppCtx);
  else
    return RET_INVALIDPARAM;
}

EResult WelsDestroyVpInterface (void* pCtx, int iVersion) {
  if (iVersion & 0x8000)
    return WelsVP::DestroySpecificVpInterface ((IWelsVP*)pCtx);
  else if (iVersion & 0x7fff)
    return WelsVP::DestroySpecificVpInterface ((IWelsVPc*)pCtx);
  else
    return RET_INVALIDPARAM;
}

WELSVP_NAMESPACE_BEGIN

///////////////////////////////////////////////////////////////////////

EResult CreateSpecificVpInterface (IWelsVP** ppCtx) {
  EResult  eReturn = RET_FAILED;

  CVpFrameWork* pFr = new CVpFrameWork (1, eReturn);
  if (pFr) {
    *ppCtx  = (IWelsVP*)pFr;
    eReturn = RET_SUCCESS;
  }

  return eReturn;
}

EResult DestroySpecificVpInterface (IWelsVP* pCtx) {
  delete pCtx;

  return RET_SUCCESS;
}

///////////////////////////////////////////////////////////////////////////////

CVpFrameWork::CVpFrameWork (uint32_t uiThreadsNum, EResult& eReturn) {
  int32_t iCoreNum = 1;
  uint32_t uiCPUFlag = WelsCPUFeatureDetect (&iCoreNum);

  for (int32_t i = 0; i < MAX_STRATEGY_NUM; i++) {
    m_pStgChain[i] = CreateStrategy (WelsStaticCast (EMethods, i + 1), uiCPUFlag);
  }

  WelsMutexInit (&m_mutes);

  eReturn = RET_SUCCESS;
}

CVpFrameWork::~CVpFrameWork() {
  for (int32_t i = 0; i < MAX_STRATEGY_NUM; i++) {
    if (m_pStgChain[i]) {
      Uninit (m_pStgChain[i]->m_eMethod);
      delete m_pStgChain[i];
    }
  }

  WelsMutexDestroy (&m_mutes);
}

EResult CVpFrameWork::Init (int32_t iType, void* pCfg) {
  EResult eReturn   = RET_SUCCESS;
  int32_t iCurIdx    = WelsStaticCast (int32_t, WelsVpGetValidMethod (iType)) - 1;

  Uninit (iType);

  WelsMutexLock (&m_mutes);

  IStrategy* pStrategy = m_pStgChain[iCurIdx];
  if (pStrategy)
    eReturn = pStrategy->Init (0, pCfg);

  WelsMutexUnlock (&m_mutes);

  return eReturn;
}

EResult CVpFrameWork::Uninit (int32_t iType) {
  EResult eReturn        = RET_SUCCESS;
  int32_t iCurIdx    = WelsStaticCast (int32_t, WelsVpGetValidMethod (iType)) - 1;

  WelsMutexLock (&m_mutes);

  IStrategy* pStrategy = m_pStgChain[iCurIdx];
  if (pStrategy)
    eReturn = pStrategy->Uninit (0);

  WelsMutexUnlock (&m_mutes);

  return eReturn;
}

EResult CVpFrameWork::Flush (int32_t iType) {
  EResult eReturn        = RET_SUCCESS;

  return eReturn;
}

EResult CVpFrameWork::Process (int32_t iType, SPixMap* pSrcPixMap, SPixMap* pDstPixMap) {
  EResult eReturn        = RET_NOTSUPPORTED;
  EMethods eMethod    = WelsVpGetValidMethod (iType);
  int32_t iCurIdx    = WelsStaticCast (int32_t, eMethod) - 1;
  SPixMap sSrcPic;
  SPixMap sDstPic;
  memset (&sSrcPic, 0, sizeof (sSrcPic)); // confirmed_safe_unsafe_usage
  memset (&sDstPic, 0, sizeof (sDstPic)); // confirmed_safe_unsafe_usage

  if (pSrcPixMap) sSrcPic = *pSrcPixMap;
  if (pDstPixMap) sDstPic = *pDstPixMap;
  if (!CheckValid (eMethod, sSrcPic, sDstPic))
    return RET_INVALIDPARAM;

  WelsMutexLock (&m_mutes);

  IStrategy* pStrategy = m_pStgChain[iCurIdx];
  if (pStrategy)
    eReturn = pStrategy->Process (0, &sSrcPic, &sDstPic);

  WelsMutexUnlock (&m_mutes);

  return eReturn;
}

EResult CVpFrameWork::Get (int32_t iType, void* pParam) {
  EResult eReturn        = RET_SUCCESS;
  int32_t iCurIdx    = WelsStaticCast (int32_t, WelsVpGetValidMethod (iType)) - 1;

  if (!pParam)
    return RET_INVALIDPARAM;

  WelsMutexLock (&m_mutes);

  IStrategy* pStrategy = m_pStgChain[iCurIdx];
  if (pStrategy)
    eReturn = pStrategy->Get (0, pParam);

  WelsMutexUnlock (&m_mutes);

  return eReturn;
}

EResult CVpFrameWork::Set (int32_t iType, void* pParam) {
  EResult eReturn        = RET_SUCCESS;
  int32_t iCurIdx    = WelsStaticCast (int32_t, WelsVpGetValidMethod (iType)) - 1;

  if (!pParam)
    return RET_INVALIDPARAM;

  WelsMutexLock (&m_mutes);

  IStrategy* pStrategy = m_pStgChain[iCurIdx];
  if (pStrategy)
    eReturn = pStrategy->Set (0, pParam);

  WelsMutexUnlock (&m_mutes);

  return eReturn;
}

EResult CVpFrameWork::SpecialFeature (int32_t iType, void* pIn, void* pOut) {
  EResult eReturn        = RET_SUCCESS;

  return eReturn;
}

bool  CVpFrameWork::CheckValid (EMethods eMethod, SPixMap& pSrcPixMap, SPixMap& pDstPixMap) {
  bool eReturn = false;

  if (eMethod == METHOD_NULL)
    goto exit;

  if (eMethod != METHOD_COLORSPACE_CONVERT) {
    if (pSrcPixMap.pPixel[0]) {
      if (pSrcPixMap.eFormat != VIDEO_FORMAT_I420 && pSrcPixMap.eFormat != VIDEO_FORMAT_YV12)
        goto exit;
    }
    if (pSrcPixMap.pPixel[0] && pDstPixMap.pPixel[0]) {
      if (pDstPixMap.eFormat != pSrcPixMap.eFormat)
        goto exit;
    }
  }

  if (pSrcPixMap.pPixel[0]) {
    if (pSrcPixMap.sRect.iRectWidth <= 0 || pSrcPixMap.sRect.iRectHeight <= 0
        || pSrcPixMap.sRect.iRectWidth * pSrcPixMap.sRect.iRectHeight > (MAX_MBS_PER_FRAME << 8))
      goto exit;
    if (pSrcPixMap.sRect.iRectTop >= pSrcPixMap.sRect.iRectHeight
        || pSrcPixMap.sRect.iRectLeft >= pSrcPixMap.sRect.iRectWidth || pSrcPixMap.sRect.iRectWidth > pSrcPixMap.iStride[0])
      goto exit;
  }
  if (pDstPixMap.pPixel[0]) {
    if (pDstPixMap.sRect.iRectWidth <= 0 || pDstPixMap.sRect.iRectHeight <= 0
        || pDstPixMap.sRect.iRectWidth * pDstPixMap.sRect.iRectHeight > (MAX_MBS_PER_FRAME << 8))
      goto exit;
    if (pDstPixMap.sRect.iRectTop >= pDstPixMap.sRect.iRectHeight
        || pDstPixMap.sRect.iRectLeft >= pDstPixMap.sRect.iRectWidth || pDstPixMap.sRect.iRectWidth > pDstPixMap.iStride[0])
      goto exit;
  }
  eReturn = true;

exit:
  return eReturn;
}

IStrategy* CVpFrameWork::CreateStrategy (EMethods m_eMethod, int32_t iCpuFlag) {
  IStrategy* pStrategy = NULL;

  switch (m_eMethod) {
  case METHOD_COLORSPACE_CONVERT:
    //not support yet
    break;
  case METHOD_DENOISE:
    pStrategy = WelsDynamicCast (IStrategy*, new CDenoiser (iCpuFlag));
    break;
  case METHOD_SCROLL_DETECTION:
    pStrategy = WelsDynamicCast (IStrategy*, new CScrollDetection (iCpuFlag));
    break;
  case METHOD_SCENE_CHANGE_DETECTION_VIDEO:
  case METHOD_SCENE_CHANGE_DETECTION_SCREEN:
    pStrategy = BuildSceneChangeDetection (m_eMethod, iCpuFlag);
    break;
  case METHOD_DOWNSAMPLE:
    pStrategy = WelsDynamicCast (IStrategy*, new CDownsampling (iCpuFlag));
    break;
  case METHOD_VAA_STATISTICS:
    pStrategy = WelsDynamicCast (IStrategy*, new CVAACalculation (iCpuFlag));
    break;
  case METHOD_BACKGROUND_DETECTION:
    pStrategy = WelsDynamicCast (IStrategy*, new CBackgroundDetection (iCpuFlag));
    break;
  case METHOD_ADAPTIVE_QUANT:
    pStrategy = WelsDynamicCast (IStrategy*, new CAdaptiveQuantization (iCpuFlag));
    break;
  case METHOD_COMPLEXITY_ANALYSIS:
    pStrategy = WelsDynamicCast (IStrategy*, new CComplexityAnalysis (iCpuFlag));
    break;
  case METHOD_COMPLEXITY_ANALYSIS_SCREEN:
    pStrategy = WelsDynamicCast (IStrategy*, new CComplexityAnalysisScreen (iCpuFlag));
    break;
  case METHOD_IMAGE_ROTATE:
    pStrategy = WelsDynamicCast (IStrategy*, new CImageRotating (iCpuFlag));
    break;
  default:
    break;
  }

  return pStrategy;
}

WELSVP_NAMESPACE_END
