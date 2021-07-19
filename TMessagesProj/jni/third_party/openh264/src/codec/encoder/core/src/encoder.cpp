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
 * \file    encoder.c
 *
 * \brief   core encoder
 *
 * \date    5/14/2009 Created
 *
 *************************************************************************************
 */
#include "encoder.h"
#include "cpu_core.h"

#include "decode_mb_aux.h"
#include "get_intra_predictor.h"

#include "deblocking.h"
#include "ref_list_mgr_svc.h"
#include "mc.h"
#include "paraset_strategy.h"
#include "sample.h"

#include "svc_enc_golomb.h"
#include "svc_base_layer_md.h"
#include "svc_mode_decision.h"
#include "set_mb_syn_cavlc.h"
#include "crt_util_safe_x.h" // Safe CRT routines like utils for cross_platforms
#include "slice_multi_threading.h"

//  global   function  pointers  definition
namespace WelsEnc {
/* Motion compensation */


/*!
 * \brief   initialize source picture body
 * \param   pSrc        SSourcePicture*
 * \param   csp         internal csp format
 * \param   iWidth      widht of picture in pixels
 * \param   iHeight     iHeight of picture in pixels
 * \return  successful - 0; otherwise none 0 for failed
 */
int32_t InitPic (const void* kpSrc, const int32_t kiColorspace, const int32_t kiWidth, const int32_t kiHeight) {
  SSourcePicture* pSrcPic = (SSourcePicture*)kpSrc;

  if (NULL == pSrcPic || kiWidth == 0 || kiHeight == 0)
    return 1;

  pSrcPic->iColorFormat = kiColorspace;
  pSrcPic->iPicWidth    = kiWidth;
  pSrcPic->iPicHeight   = kiHeight;

  //currently encoder only supports videoFormatI420.
  if ((kiColorspace & (~videoFormatVFlip)) != videoFormatI420)
    return 2;
  switch (kiColorspace & (~videoFormatVFlip)) {
  case videoFormatI420:
  case videoFormatYV12:
    pSrcPic->pData[0]   = NULL;
    pSrcPic->pData[1]   = NULL;
    pSrcPic->pData[2]   = NULL;
    pSrcPic->pData[3]   = NULL;
    pSrcPic->iStride[0] = kiWidth;
    pSrcPic->iStride[2] = pSrcPic->iStride[1] = kiWidth >> 1;
    pSrcPic->iStride[3] = 0;
    break;
  case videoFormatYUY2:
  case videoFormatYVYU:
  case videoFormatUYVY:
    pSrcPic->pData[0]   = NULL;
    pSrcPic->pData[1]   = NULL;
    pSrcPic->pData[2]   = NULL;
    pSrcPic->pData[3]   = NULL;
    pSrcPic->iStride[0] = CALC_BI_STRIDE (kiWidth,  16);
    pSrcPic->iStride[3] = pSrcPic->iStride[2] = pSrcPic->iStride[1] = 0;
    break;
  case videoFormatRGB:
  case videoFormatBGR:
    pSrcPic->pData[0]   = NULL;
    pSrcPic->pData[1]   = NULL;
    pSrcPic->pData[2]   = NULL;
    pSrcPic->pData[3]   = NULL;
    pSrcPic->iStride[0] = CALC_BI_STRIDE (kiWidth, 24);
    pSrcPic->iStride[3] = pSrcPic->iStride[2] = pSrcPic->iStride[1] = 0;
    if (kiColorspace & videoFormatVFlip)
      pSrcPic->iColorFormat = kiColorspace & (~videoFormatVFlip);
    else
      pSrcPic->iColorFormat = kiColorspace | videoFormatVFlip;
    break;
  case videoFormatBGRA:
  case videoFormatRGBA:
  case videoFormatARGB:
  case videoFormatABGR:
    pSrcPic->pData[0]   = NULL;
    pSrcPic->pData[1]   = NULL;
    pSrcPic->pData[2]   = NULL;
    pSrcPic->pData[3]   = NULL;
    pSrcPic->iStride[0] = kiWidth << 2;
    pSrcPic->iStride[3] = pSrcPic->iStride[2] = pSrcPic->iStride[1] = 0;
    if (kiColorspace & videoFormatVFlip)
      pSrcPic->iColorFormat = kiColorspace & (~videoFormatVFlip);
    else
      pSrcPic->iColorFormat = kiColorspace | videoFormatVFlip;
    break;
  default:
    return 2; // any else?
  }

  return 0;
}


void WelsInitBGDFunc (SWelsFuncPtrList* pFuncList, const bool kbEnableBackgroundDetection) {
  if (kbEnableBackgroundDetection) {
    pFuncList->pfInterMdBackgroundDecision = WelsMdInterJudgeBGDPskip;
    pFuncList->pfMdBackgroundInfoUpdate = WelsMdUpdateBGDInfo;
  } else {
    pFuncList->pfInterMdBackgroundDecision = WelsMdInterJudgeBGDPskipFalse;
    pFuncList->pfMdBackgroundInfoUpdate = WelsMdUpdateBGDInfoNULL;
  }
}

/*!
 * \brief   initialize function pointers that potentially used in Wels encoding
 * \param   pEncCtx     sWelsEncCtx*
 * \return  successful - 0; otherwise none 0 for failed
 */
int32_t InitFunctionPointers (sWelsEncCtx* pEncCtx, SWelsSvcCodingParam* pParam, uint32_t uiCpuFlag) {
  int32_t iReturn = ENC_RETURN_SUCCESS;
  SWelsFuncPtrList* pFuncList = pEncCtx->pFuncList;
  bool bScreenContent = (SCREEN_CONTENT_REAL_TIME == pParam->iUsageType);

  /* Functionality utilization of CPU instructions dependency */
  pFuncList->pfSetMemZeroSize8              = WelsSetMemZero_c;             // confirmed_safe_unsafe_usage
  pFuncList->pfSetMemZeroSize64Aligned16    = WelsSetMemZero_c;     // confirmed_safe_unsafe_usage
  pFuncList->pfSetMemZeroSize64             = WelsSetMemZero_c;     // confirmed_safe_unsafe_usage
#if defined(X86_ASM)
  if (uiCpuFlag & WELS_CPU_MMXEXT) {
    pFuncList->pfSetMemZeroSize8            = WelsSetMemZeroSize8_mmx;              // confirmed_safe_unsafe_usage
    pFuncList->pfSetMemZeroSize64Aligned16  = WelsSetMemZeroSize64_mmx;     // confirmed_safe_unsafe_usage
    pFuncList->pfSetMemZeroSize64           = WelsSetMemZeroSize64_mmx;     // confirmed_safe_unsafe_usage
  }
  if (uiCpuFlag & WELS_CPU_SSE2) {
    pFuncList->pfSetMemZeroSize64Aligned16  = WelsSetMemZeroAligned64_sse2; // confirmed_safe_unsafe_usage
  }
#endif//X86_ASM

#if defined(HAVE_NEON)
  if (uiCpuFlag & WELS_CPU_NEON) {
    pFuncList->pfSetMemZeroSize8            = WelsSetMemZero_neon;
    pFuncList->pfSetMemZeroSize64Aligned16  = WelsSetMemZero_neon;
    pFuncList->pfSetMemZeroSize64           = WelsSetMemZero_neon;
  }
#endif

#if defined(HAVE_NEON_AARCH64)
  if (uiCpuFlag & WELS_CPU_NEON) {
    pFuncList->pfSetMemZeroSize8            = WelsSetMemZero_AArch64_neon;
    pFuncList->pfSetMemZeroSize64Aligned16  = WelsSetMemZero_AArch64_neon;
    pFuncList->pfSetMemZeroSize64           = WelsSetMemZero_AArch64_neon;
  }
#endif

  InitExpandPictureFunc (& (pFuncList->sExpandPicFunc), uiCpuFlag);

  /* Intra_Prediction_fn*/
  WelsInitIntraPredFuncs (pFuncList, uiCpuFlag);

  /* ME func */
  WelsInitMeFunc (pFuncList, uiCpuFlag, bScreenContent);

  /* sad, satd, average */
  WelsInitSampleSadFunc (pFuncList, uiCpuFlag);

  //
  WelsInitBGDFunc (pFuncList, pParam->bEnableBackgroundDetection);
  WelsInitSCDPskipFunc (pFuncList, bScreenContent && (pParam->bEnableSceneChangeDetect));

  // for pfGetVarianceFromIntraVaa function ptr adaptive by CPU features, 6/7/2010
  InitIntraAnalysisVaaInfo (pFuncList, uiCpuFlag);

  /* Motion compensation */
  /*init pixel average function*/
  /*get one column or row pixel when refinement*/
  InitMcFunc (&pFuncList->sMcFuncs, uiCpuFlag);
  InitCoeffFunc (pFuncList, uiCpuFlag, pParam->iEntropyCodingModeFlag);

  WelsInitEncodingFuncs (pFuncList, uiCpuFlag);
  WelsInitReconstructionFuncs (pFuncList, uiCpuFlag);

  DeblockingInit (&pFuncList->pfDeblocking, uiCpuFlag);
  WelsBlockFuncInit (&pFuncList->pfSetNZCZero, uiCpuFlag);

  InitFillNeighborCacheInterFunc (pFuncList, pParam->bEnableBackgroundDetection);

  pFuncList->pParametersetStrategy = IWelsParametersetStrategy::CreateParametersetStrategy (pParam->eSpsPpsIdStrategy,
                                     pParam->bSimulcastAVC, pParam->iSpatialLayerNum);
  WELS_VERIFY_RETURN_IF (ENC_RETURN_MEMALLOCERR, (NULL == pFuncList->pParametersetStrategy))

  return iReturn;
}

void UpdateFrameNum (sWelsEncCtx* pEncCtx, const int32_t kiDidx) {
  SSpatialLayerInternal* pParamInternal = &pEncCtx->pSvcParam->sDependencyLayers[kiDidx];
  bool bNeedFrameNumIncreasing = false;

  if (NRI_PRI_LOWEST != pEncCtx->eLastNalPriority[kiDidx]) {
    bNeedFrameNumIncreasing = true;
  }

  if (bNeedFrameNumIncreasing) {
    if (pParamInternal->iFrameNum < (1 << pEncCtx->pSps->uiLog2MaxFrameNum) - 1)
      ++ pParamInternal->iFrameNum;
    else
      pParamInternal->iFrameNum = 0;    // if iFrameNum overflow
  }

  pEncCtx->eLastNalPriority[kiDidx] = NRI_PRI_LOWEST;
}


void LoadBackFrameNum (sWelsEncCtx* pEncCtx, const int32_t kiDidx) {
  SSpatialLayerInternal* pParamInternal = &pEncCtx->pSvcParam->sDependencyLayers[kiDidx];
  bool bNeedFrameNumIncreasing = false;

  if (NRI_PRI_LOWEST != pEncCtx->eLastNalPriority[kiDidx]) {
    bNeedFrameNumIncreasing = true;
  }

  if (bNeedFrameNumIncreasing) {
    if (pParamInternal->iFrameNum != 0) {
      pParamInternal->iFrameNum --;
    } else {
      pParamInternal->iFrameNum = (1 << pEncCtx->pSps->uiLog2MaxFrameNum) - 1;
    }
  }
}

void InitBitStream (sWelsEncCtx* pEncCtx) {
  // for bitstream writing
  pEncCtx->iPosBsBuffer         = 0;    // reset bs pBuffer position
  pEncCtx->pOut->iNalIndex      = 0;    // reset NAL index
  pEncCtx->pOut->iLayerBsIndex  = 0;    // reset index of Layer Bs

  InitBits (&pEncCtx->pOut->sBsWrite, pEncCtx->pOut->pBsBuffer, pEncCtx->pOut->uiSize);
}
/*!
 * \brief   initialize frame coding
 */
void InitFrameCoding (sWelsEncCtx* pEncCtx, const EVideoFrameType keFrameType, const int32_t kiDidx) {
  SSpatialLayerInternal* pParamInternal = &pEncCtx->pSvcParam->sDependencyLayers[kiDidx];
  if (keFrameType == videoFrameTypeP) {
    ++pParamInternal->iFrameIndex;

    if (pParamInternal->iPOC < (1 << pEncCtx->pSps->iLog2MaxPocLsb) -
        2)     // if iPOC type is no 0, this need be modification
      pParamInternal->iPOC += 2;   // for POC type 0
    else
      pParamInternal->iPOC = 0;

    UpdateFrameNum (pEncCtx, kiDidx);

    pEncCtx->eNalType           = NAL_UNIT_CODED_SLICE;
    pEncCtx->eSliceType         = P_SLICE;
    pEncCtx->eNalPriority       = NRI_PRI_HIGH;
  } else if (keFrameType == videoFrameTypeIDR) {
    pParamInternal->iFrameNum          = 0;
    pParamInternal->iPOC               = 0;
    pParamInternal->bEncCurFrmAsIdrFlag = false;
    pParamInternal->iFrameIndex = 0;

    pEncCtx->eNalType           = NAL_UNIT_CODED_SLICE_IDR;
    pEncCtx->eSliceType         = I_SLICE;
    pEncCtx->eNalPriority       = NRI_PRI_HIGHEST;

    pParamInternal->iCodingIndex       = 0;

    // reset_ref_list

    // rc_init_gop
  } else if (keFrameType == videoFrameTypeI) {
    if (pParamInternal->iPOC < (1 << pEncCtx->pSps->iLog2MaxPocLsb) -
        2)     // if iPOC type is no 0, this need be modification
      pParamInternal->iPOC += 2;   // for POC type 0
    else
      pParamInternal->iPOC = 0;

    UpdateFrameNum (pEncCtx, kiDidx);

    pEncCtx->eNalType     = NAL_UNIT_CODED_SLICE;
    pEncCtx->eSliceType   = I_SLICE;
    pEncCtx->eNalPriority = NRI_PRI_HIGHEST;

    // rc_init_gop
  } else { // B pictures are not supported now, any else?
    assert (0);
  }

#if defined(STAT_OUTPUT)
  memset (&pEncCtx->sPerInfo, 0, sizeof (SStatSliceInfo));
#endif//FRAME_INFO_OUTPUT
}

EVideoFrameType DecideFrameType (sWelsEncCtx* pEncCtx, const int8_t kiSpatialNum, const int32_t kiDidx,
                                 bool bSkipFrameFlag) {
  SWelsSvcCodingParam* pSvcParam = pEncCtx->pSvcParam;
  SSpatialLayerInternal* pParamInternal = &pEncCtx->pSvcParam->sDependencyLayers[kiDidx];
  EVideoFrameType iFrameType = videoFrameTypeInvalid;
  bool bSceneChangeFlag = false;
  if (pSvcParam->iUsageType == SCREEN_CONTENT_REAL_TIME) {
    if ((!pSvcParam->bEnableSceneChangeDetect) || pEncCtx->pVaa->bIdrPeriodFlag ||
        (kiSpatialNum < pSvcParam->iSpatialLayerNum)) {
      bSceneChangeFlag = false;
    } else {
      bSceneChangeFlag = pEncCtx->pVaa->bSceneChangeFlag;
    }
    if (pEncCtx->pVaa->bIdrPeriodFlag || pParamInternal->bEncCurFrmAsIdrFlag || (!pSvcParam->bEnableLongTermReference
        && bSceneChangeFlag && !bSkipFrameFlag)) {
      iFrameType = videoFrameTypeIDR;
    } else if (pSvcParam->bEnableLongTermReference && (bSceneChangeFlag
               || pEncCtx->pVaa->eSceneChangeIdc == LARGE_CHANGED_SCENE)) {
      int iActualLtrcount = 0;
      SPicture** pLongTermRefList = pEncCtx->ppRefPicListExt[0]->pLongRefList;
      for (int i = 0; i < pSvcParam->iLTRRefNum; ++i) {
        if (NULL != pLongTermRefList[i] && pLongTermRefList[i]->bUsedAsRef && pLongTermRefList[i]->bIsLongRef
            && pLongTermRefList[i]->bIsSceneLTR) {
          ++iActualLtrcount;
        }
      }
      if (iActualLtrcount == pSvcParam->iLTRRefNum && bSceneChangeFlag) {
        iFrameType = videoFrameTypeIDR;
      } else {
        iFrameType = videoFrameTypeP;
        pEncCtx->bCurFrameMarkedAsSceneLtr = true;
      }
    } else {
      iFrameType = videoFrameTypeP;
    }
    if (videoFrameTypeP == iFrameType && bSkipFrameFlag) {
      iFrameType = videoFrameTypeSkip;
    } else if (videoFrameTypeIDR == iFrameType) {
      pParamInternal->iCodingIndex = 0;
      pEncCtx->bCurFrameMarkedAsSceneLtr   = true;
    }

  } else {
    // perform scene change detection
    if ((!pSvcParam->bEnableSceneChangeDetect) || pEncCtx->pVaa->bIdrPeriodFlag ||
        (kiSpatialNum < pSvcParam->iSpatialLayerNum)
        || (pParamInternal->iFrameIndex < (VGOP_SIZE << 1))) { // avoid too frequent I frame coding, rc control
      bSceneChangeFlag = false;
    } else {
      bSceneChangeFlag = pEncCtx->pVaa->bSceneChangeFlag;
    }

    //scene_changed_flag: RC enable && iSpatialNum == pSvcParam->iSpatialLayerNum
    //bIdrPeriodFlag: RC disable || iSpatialNum != pSvcParam->iSpatialLayerNum
    //pEncCtx->bEncCurFrmAsIdrFlag: 1. first frame should be IDR; 2. idr pause; 3. idr request
    iFrameType = (pEncCtx->pVaa->bIdrPeriodFlag || bSceneChangeFlag
                  || pParamInternal->bEncCurFrmAsIdrFlag) ? videoFrameTypeIDR : videoFrameTypeP;
    if ( videoFrameTypeIDR == iFrameType ) {
      WelsLog (& (pEncCtx->sLogCtx), WELS_LOG_DEBUG,
               "encoding videoFrameTypeIDR due to ( bIdrPeriodFlag %d, bSceneChangeFlag %d, bEncCurFrmAsIdrFlag %d )",
               pEncCtx->pVaa->bIdrPeriodFlag,
               bSceneChangeFlag,
               pParamInternal->bEncCurFrmAsIdrFlag);
    }

    if (videoFrameTypeP == iFrameType && bSkipFrameFlag) {  // for frame skip, 1/5/2010
      iFrameType = videoFrameTypeSkip;
    } else if (videoFrameTypeIDR == iFrameType) {
      pParamInternal->iCodingIndex = 0;
    }
  }
  return iFrameType;
}

/*!
 * \brief   Dump reconstruction for dependency layer
 */

extern "C" void DumpDependencyRec (SPicture* pCurPicture, const char* kpFileName, const int8_t kiDid, bool bAppend,
                                   SDqLayer* pDqLayer, bool bSimulCastAVC) {
  WelsFileHandle* pDumpRecFile = NULL;
  int32_t iWrittenSize = 0;
  const char* openMode = bAppend ? "ab" : "wb";
  SWelsSPS* pSpsTmp = NULL;
  if (bSimulCastAVC || (kiDid == BASE_DEPENDENCY_ID)) {
    pSpsTmp = pDqLayer->sLayerInfo.pSpsP;
  } else {
    pSpsTmp = & (pDqLayer->sLayerInfo.pSubsetSpsP->pSps);
  }
  bool bFrameCroppingFlag = pSpsTmp->bFrameCroppingFlag;
  SCropOffset* pFrameCrop = &pSpsTmp->sFrameCrop;

  if (NULL == pCurPicture || NULL == kpFileName || kiDid >= MAX_DEPENDENCY_LAYER)
    return;
  if (strlen (kpFileName) > 0) // confirmed_safe_unsafe_usage
    pDumpRecFile = WelsFopen (kpFileName, openMode);
  else {
    char sDependencyRecFileName[16] = {0};
    WelsSnprintf (sDependencyRecFileName, 16, "rec%d.yuv", kiDid); // confirmed_safe_unsafe_usage
    pDumpRecFile = WelsFopen (sDependencyRecFileName, openMode);
  }
  if (NULL != pDumpRecFile && bAppend)
    WelsFseek (pDumpRecFile, 0, SEEK_END);

  if (NULL != pDumpRecFile) {
    int32_t i = 0;
    int32_t j = 0;
    const int32_t kiStrideY      = pCurPicture->iLineSize[0];
    const int32_t kiLumaWidth    = bFrameCroppingFlag ? (pCurPicture->iWidthInPixel - ((pFrameCrop->iCropLeft +
                                   pFrameCrop->iCropRight) << 1)) : pCurPicture->iWidthInPixel;
    const int32_t kiLumaHeight   = bFrameCroppingFlag ? (pCurPicture->iHeightInPixel - ((pFrameCrop->iCropTop +
                                   pFrameCrop->iCropBottom) << 1)) : pCurPicture->iHeightInPixel;
    const int32_t kiChromaWidth  = kiLumaWidth >> 1;
    const int32_t kiChromaHeight = kiLumaHeight >> 1;
    uint8_t* pSrc = NULL;
    pSrc = bFrameCroppingFlag ? (pCurPicture->pData[0] + kiStrideY * (pFrameCrop->iCropTop << 1) +
                                 (pFrameCrop->iCropLeft << 1)) : pCurPicture->pData[0];
    for (j = 0; j < kiLumaHeight; ++ j) {
      iWrittenSize = WelsFwrite (pSrc + j * kiStrideY, 1, kiLumaWidth, pDumpRecFile);
      assert (iWrittenSize == kiLumaWidth);
      if (iWrittenSize < kiLumaWidth) {
        assert (0); // make no sense for us if writing failed
        WelsFclose (pDumpRecFile);
        return;
      }
    }
    for (i = 1; i < I420_PLANES; ++ i) {
      const int32_t kiStrideUV = pCurPicture->iLineSize[i];
      pSrc = bFrameCroppingFlag ? (pCurPicture->pData[i] + kiStrideUV * pFrameCrop->iCropTop + pFrameCrop->iCropLeft) :
             pCurPicture->pData[i];
      for (j = 0; j < kiChromaHeight; ++ j) {
        iWrittenSize = WelsFwrite (pSrc + j * kiStrideUV, 1, kiChromaWidth, pDumpRecFile);
        assert (iWrittenSize == kiChromaWidth);
        if (iWrittenSize < kiChromaWidth) {
          assert (0); // make no sense for us if writing failed
          WelsFclose (pDumpRecFile);
          return;
        }
      }
    }
    WelsFclose (pDumpRecFile);
    pDumpRecFile = NULL;
  }
}

/*!
 * \brief   Dump the reconstruction pictures
 */

void DumpRecFrame (SPicture* pCurPicture, const char* kpFileName, const int8_t kiDid, bool bAppend,
                   SDqLayer* pDqLayer) {
  WelsFileHandle* pDumpRecFile = NULL;
  SWelsSPS* pSpsTmp = (kiDid > BASE_DEPENDENCY_ID) ? & (pDqLayer->sLayerInfo.pSubsetSpsP->pSps) :
                      pDqLayer->sLayerInfo.pSpsP;
  bool bFrameCroppingFlag = pSpsTmp->bFrameCroppingFlag;
  SCropOffset* pFrameCrop = &pSpsTmp->sFrameCrop;

  int32_t iWrittenSize = 0;
  const char* openMode = bAppend ? "ab" : "wb";

  if (NULL == pCurPicture || NULL == kpFileName)
    return;

  if (strlen (kpFileName) > 0) { // confirmed_safe_unsafe_usage
    pDumpRecFile = WelsFopen (kpFileName, openMode);
  } else {
    pDumpRecFile = WelsFopen ("rec.yuv", openMode);
  }
  if (NULL != pDumpRecFile && bAppend)
    WelsFseek (pDumpRecFile, 0, SEEK_END);

  if (NULL != pDumpRecFile) {
    int32_t i = 0;
    int32_t j = 0;
    const int32_t kiStrideY      = pCurPicture->iLineSize[0];
    const int32_t kiLumaWidth    = bFrameCroppingFlag ? (pCurPicture->iWidthInPixel - ((pFrameCrop->iCropLeft +
                                   pFrameCrop->iCropRight) << 1)) : pCurPicture->iWidthInPixel;
    const int32_t kiLumaHeight   = bFrameCroppingFlag ? (pCurPicture->iHeightInPixel - ((pFrameCrop->iCropTop +
                                   pFrameCrop->iCropBottom) << 1)) : pCurPicture->iHeightInPixel;
    const int32_t kiChromaWidth  = kiLumaWidth >> 1;
    const int32_t kiChromaHeight = kiLumaHeight >> 1;
    uint8_t* pSrc = NULL;
    pSrc = bFrameCroppingFlag ? (pCurPicture->pData[0] + kiStrideY * (pFrameCrop->iCropTop << 1) +
                                 (pFrameCrop->iCropLeft << 1)) : pCurPicture->pData[0];
    for (j = 0; j < kiLumaHeight; ++ j) {
      iWrittenSize = WelsFwrite (pSrc + j * kiStrideY, 1, kiLumaWidth, pDumpRecFile);
      assert (iWrittenSize == kiLumaWidth);
      if (iWrittenSize < kiLumaWidth) {
        assert (0); // make no sense for us if writing failed
        WelsFclose (pDumpRecFile);
        return;
      }
    }
    for (i = 1; i < I420_PLANES; ++ i) {
      const int32_t kiStrideUV = pCurPicture->iLineSize[i];
      pSrc = bFrameCroppingFlag ? (pCurPicture->pData[i] + kiStrideUV * pFrameCrop->iCropTop + pFrameCrop->iCropLeft) :
             pCurPicture->pData[i];
      for (j = 0; j < kiChromaHeight; ++ j) {
        iWrittenSize = WelsFwrite (pSrc + j * kiStrideUV, 1, kiChromaWidth, pDumpRecFile);
        assert (iWrittenSize == kiChromaWidth);
        if (iWrittenSize < kiChromaWidth) {
          assert (0); // make no sense for us if writing failed
          WelsFclose (pDumpRecFile);
          return;
        }
      }
    }
    WelsFclose (pDumpRecFile);
    pDumpRecFile = NULL;
  }
}



/***********************************************************************************/
void WelsSetMemZero_c (void* pDst, int32_t iSize) { // confirmed_safe_unsafe_usage
  memset (pDst, 0, iSize);
}
}
