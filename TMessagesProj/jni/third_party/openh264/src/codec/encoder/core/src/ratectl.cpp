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
 *  ratectl.c
 *
 *  Abstract
 *      Rate Control
 *
 *  History
 *      9/8/2009 Created
 *    12/26/2011 Modified
 *
 *
 *
 *************************************************************************/
#include "rc.h"
#include "encoder_context.h"
#include "utils.h"
#include "svc_enc_golomb.h"


namespace WelsEnc {

const int32_t g_kiQpToQstepTable[52] = {
  63,   71,   79,   89,  100,  112,  126,  141,  159,  178,
  200,  224,  252,  283,  317,  356,  400,  449,  504,  566,
  635,  713,  800,  898, 1008, 1131, 1270, 1425, 1600, 1796,
  2016, 2263, 2540, 2851, 3200, 3592, 4032, 4525, 5080, 5702,
  6400, 7184, 8063, 9051, 10159, 11404, 12800, 14368, 16127, 18102,
  20319, 22807
}; //WELS_ROUND(INT_MULTIPLY*pow (2.0, (iQP - 4.0) / 6.0))

void RcInitLayerMemory (SWelsSvcRc* pWelsSvcRc, CMemoryAlign* pMA, const int32_t kiMaxTl) {
  const int32_t kiGomSize       = pWelsSvcRc->iGomSize;
  const int32_t kiGomSizeD      = kiGomSize * sizeof (double);
  const int32_t kiGomSizeI      = kiGomSize * sizeof (int32_t);
  const int32_t kiLayerRcSize   = kiGomSizeD + (kiGomSizeI * 3) +  sizeof (SRCTemporal) * kiMaxTl;
  uint8_t* pBaseMem             = (uint8_t*)pMA->WelsMalloc (kiLayerRcSize, "pWelsSvcRc->pTemporalOverRc");

  if (NULL == pBaseMem)
    return;

  pWelsSvcRc->pTemporalOverRc   = (SRCTemporal*)pBaseMem;
  pBaseMem += sizeof (SRCTemporal) * kiMaxTl;
  pWelsSvcRc->pGomComplexity    = (double*)pBaseMem;
  pBaseMem += kiGomSizeD;
  pWelsSvcRc->pGomForegroundBlockNum    = (int32_t*)pBaseMem;
  pBaseMem += kiGomSizeI;
  pWelsSvcRc->pCurrentFrameGomSad       = (int32_t*)pBaseMem;
  pBaseMem += kiGomSizeI;
  pWelsSvcRc->pGomCost          = (int32_t*)pBaseMem;

}

void RcFreeLayerMemory (SWelsSvcRc* pWelsSvcRc, CMemoryAlign* pMA) {
  if (pWelsSvcRc != NULL && pWelsSvcRc->pTemporalOverRc != NULL) {
    pMA->WelsFree (pWelsSvcRc->pTemporalOverRc, "pWelsSvcRc->pTemporalOverRc");
    pWelsSvcRc->pTemporalOverRc         = NULL;
    pWelsSvcRc->pGomComplexity          = NULL;
    pWelsSvcRc->pGomForegroundBlockNum  = NULL;
    pWelsSvcRc->pCurrentFrameGomSad     = NULL;
    pWelsSvcRc->pGomCost                = NULL;
  }
}

static inline int32_t RcConvertQp2QStep (int32_t iQP) {
  return g_kiQpToQstepTable[iQP];
}
static inline int32_t RcConvertQStep2Qp (int32_t iQpStep) {
  if (iQpStep <= g_kiQpToQstepTable[0]) //Qp step too small, return qp=0
    return 0;
  return WELS_ROUND ((6 * log (iQpStep * 1.0f / INT_MULTIPLY) / log (2.0) + 4.0));
}

void RcInitSequenceParameter (sWelsEncCtx* pEncCtx) {
  SWelsSvcRc* pWelsSvcRc = NULL;
  SSpatialLayerConfig* pDLayerParam = NULL;

  int32_t j = 0;
  int32_t iMbWidth = 0;

  bool bMultiSliceMode = false;
  int32_t iGomRowMode0 = 1, iGomRowMode1 = 1;
  for (j = 0; j < pEncCtx->pSvcParam->iSpatialLayerNum; j++) {
    pWelsSvcRc  = &pEncCtx->pWelsSvcRc[j];
    pDLayerParam = &pEncCtx->pSvcParam->sSpatialLayers[j];
    iMbWidth     = (pDLayerParam->iVideoWidth >> 4);
    pWelsSvcRc->iNumberMbFrame = iMbWidth * (pDLayerParam->iVideoHeight >> 4);

    pWelsSvcRc->iRcVaryPercentage = pEncCtx->pSvcParam->iBitsVaryPercentage; // % -- for temp
    pWelsSvcRc->iRcVaryRatio = pWelsSvcRc->iRcVaryPercentage;

    pWelsSvcRc->iBufferFullnessSkip = 0;
    pWelsSvcRc->uiLastTimeStamp = 0;
    pWelsSvcRc->iCost2BitsIntra = 1;
    pWelsSvcRc->iAvgCost2Bits = 1;
    pWelsSvcRc->iSkipBufferRatio  = SKIP_RATIO;
    pWelsSvcRc->iContinualSkipFrames = 0;
    pWelsSvcRc->iQpRangeUpperInFrame = (QP_RANGE_UPPER_MODE1 * MAX_BITS_VARY_PERCENTAGE - ((
                                          QP_RANGE_UPPER_MODE1 - QP_RANGE_MODE0) *
                                        pWelsSvcRc->iRcVaryRatio)) / MAX_BITS_VARY_PERCENTAGE;
    pWelsSvcRc->iQpRangeLowerInFrame = (QP_RANGE_LOWER_MODE1 * MAX_BITS_VARY_PERCENTAGE - ((
                                          QP_RANGE_LOWER_MODE1 - QP_RANGE_MODE0) *
                                        pWelsSvcRc->iRcVaryRatio)) / MAX_BITS_VARY_PERCENTAGE;

    if (iMbWidth <= MB_WIDTH_THRESHOLD_90P) {
      pWelsSvcRc->iSkipQpValue = SKIP_QP_90P;
      iGomRowMode0 = GOM_ROW_MODE0_90P;
      iGomRowMode1 = GOM_ROW_MODE1_90P;
    } else if (iMbWidth <= MB_WIDTH_THRESHOLD_180P) {
      pWelsSvcRc->iSkipQpValue = SKIP_QP_180P;
      iGomRowMode0 = GOM_ROW_MODE0_180P;
      iGomRowMode1 = GOM_ROW_MODE1_180P;
    } else if (iMbWidth <= MB_WIDTH_THRESHOLD_360P) {
      pWelsSvcRc->iSkipQpValue = SKIP_QP_360P;
      iGomRowMode0 = GOM_ROW_MODE0_360P;
      iGomRowMode1 = GOM_ROW_MODE1_360P;
    } else {
      pWelsSvcRc->iSkipQpValue = SKIP_QP_720P;
      iGomRowMode0 = GOM_ROW_MODE0_720P;
      iGomRowMode1 = GOM_ROW_MODE1_720P;
    }
    iGomRowMode0 = iGomRowMode1 + ((iGomRowMode0 - iGomRowMode1) * pWelsSvcRc->iRcVaryRatio / MAX_BITS_VARY_PERCENTAGE);

    pWelsSvcRc->iNumberMbGom   = iMbWidth * iGomRowMode0;

    pWelsSvcRc->iMinQp = pEncCtx->pSvcParam->iMinQp;

    pWelsSvcRc->iMaxQp = pEncCtx->pSvcParam->iMaxQp;

    pWelsSvcRc->iFrameDeltaQpUpper = LAST_FRAME_QP_RANGE_UPPER_MODE1 - ((LAST_FRAME_QP_RANGE_UPPER_MODE1 -
                                     LAST_FRAME_QP_RANGE_UPPER_MODE0) * pWelsSvcRc->iRcVaryRatio / MAX_BITS_VARY_PERCENTAGE);
    pWelsSvcRc->iFrameDeltaQpLower = LAST_FRAME_QP_RANGE_LOWER_MODE1 - ((LAST_FRAME_QP_RANGE_LOWER_MODE1 -
                                     LAST_FRAME_QP_RANGE_LOWER_MODE0) * pWelsSvcRc->iRcVaryRatio / MAX_BITS_VARY_PERCENTAGE);

    pWelsSvcRc->iSkipFrameNum = 0;
    pWelsSvcRc->iGomSize = (pWelsSvcRc->iNumberMbFrame + pWelsSvcRc->iNumberMbGom - 1) / pWelsSvcRc->iNumberMbGom;
    pWelsSvcRc->bEnableGomQp = true;

    RcInitLayerMemory (pWelsSvcRc, pEncCtx->pMemAlign, 1 + pEncCtx->pSvcParam->sDependencyLayers[j].iHighestTemporalId);

    bMultiSliceMode = ((SM_RASTER_SLICE == pDLayerParam->sSliceArgument.uiSliceMode) ||
                       (SM_SIZELIMITED_SLICE    == pDLayerParam->sSliceArgument.uiSliceMode));
    if (bMultiSliceMode)
      pWelsSvcRc->iNumberMbGom = pWelsSvcRc->iNumberMbFrame;
  }
}


void RcInitTlWeight (sWelsEncCtx* pEncCtx) {
  SWelsSvcRc* pWelsSvcRc = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  SRCTemporal* pTOverRc = pWelsSvcRc->pTemporalOverRc;
  SSpatialLayerInternal* pDLayerParam =  &pEncCtx->pSvcParam->sDependencyLayers[pEncCtx->uiDependencyId];
  const int32_t kiDecompositionStages = pDLayerParam->iDecompositionStages;
  const int32_t kiHighestTid = pDLayerParam->iHighestTemporalId;

//Index 0:Virtual GOP size, Index 1:Frame rate
//double WeightArray[4][4] = { {1.0, 0, 0, 0}, {0.6, 0.4, 0, 0}, {0.4, 0.3, 0.15, 0}, {0.25, 0.15, 0.125, 0.0875}};
  int32_t iWeightArray[4][4] = { {2000, 0, 0, 0}, {1200, 800, 0, 0}, {800, 600, 300, 0}, {500, 300, 250, 175}}; // original*WEIGHT_MULTIPLY
  const int32_t kiGopSize = (1 << kiDecompositionStages);
  int32_t i, k, n;

  n = 0;
  while (n <= kiHighestTid) {
    pTOverRc[n].iTlayerWeight = iWeightArray[kiDecompositionStages][n];
    pTOverRc[n].iMinQp = pWelsSvcRc->iMinQp + (n << 1);
    pTOverRc[n].iMinQp = WELS_CLIP3 (pTOverRc[n].iMinQp, 0, 51);
    pTOverRc[n].iMaxQp = pWelsSvcRc->iMaxQp + (n << 1);
    pTOverRc[n].iMaxQp = WELS_CLIP3 (pTOverRc[n].iMaxQp, pTOverRc[n].iMinQp, 51);
    ++ n;
  }
//Calculate the frame index for the current frame and its reference frame
  for (n = 0; n < VGOP_SIZE; n += kiGopSize) {
    pWelsSvcRc->iTlOfFrames[n] = 0;
    for (i = 1; i <= kiDecompositionStages; i++) {
      for (k = 1 << (kiDecompositionStages - i); k < kiGopSize; k += (kiGopSize >> (i - 1))) {
        pWelsSvcRc->iTlOfFrames[k + n] = i;
      }
    }
  }
  pWelsSvcRc->iPreviousGopSize = kiGopSize;
  pWelsSvcRc->iGopNumberInVGop = VGOP_SIZE / kiGopSize;
}

void RcUpdateBitrateFps (sWelsEncCtx* pEncCtx) {
  SWelsSvcRc* pWelsSvcRc    = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  SRCTemporal* pTOverRc     = pWelsSvcRc->pTemporalOverRc;

  SSpatialLayerConfig* pDLayerParam     = &pEncCtx->pSvcParam->sSpatialLayers[pEncCtx->uiDependencyId];
  SSpatialLayerInternal* pDLayerParamInternal     = &pEncCtx->pSvcParam->sDependencyLayers[pEncCtx->uiDependencyId];
  const int32_t kiGopSize = (1 << pDLayerParamInternal->iDecompositionStages);
  const int32_t kiHighestTid = pDLayerParamInternal->iHighestTemporalId;
  const int32_t input_iBitsPerFrame = WELS_DIV_ROUND (pDLayerParam->iSpatialBitrate,
                                      pDLayerParamInternal->fOutputFrameRate);
  const int64_t kiGopBits = input_iBitsPerFrame * kiGopSize;
  int32_t i;

  pWelsSvcRc->iBitRate   = pDLayerParam->iSpatialBitrate;
  pWelsSvcRc->fFrameRate = pDLayerParamInternal->fOutputFrameRate;

  int32_t iTargetVaryRange = ((MAX_BITS_VARY_PERCENTAGE - pWelsSvcRc->iRcVaryRatio) >> 1);
  int32_t iMinBitsRatio = MAX_BITS_VARY_PERCENTAGE - iTargetVaryRange;
  int32_t iMaxBitsRatio = MAX_BITS_VARY_PERCENTAGE_x3d2;

  for (i = 0; i <= kiHighestTid; i++) {
    const int64_t kdConstraitBits = kiGopBits * pTOverRc[i].iTlayerWeight;
    pTOverRc[i].iMinBitsTl = WELS_DIV_ROUND (kdConstraitBits * iMinBitsRatio,
                             MAX_BITS_VARY_PERCENTAGE * WEIGHT_MULTIPLY);
    pTOverRc[i].iMaxBitsTl = WELS_DIV_ROUND (kdConstraitBits * iMaxBitsRatio,
                             MAX_BITS_VARY_PERCENTAGE * WEIGHT_MULTIPLY);
  }
//When bitrate is changed, pBuffer size should be updated
  pWelsSvcRc->iBufferSizeSkip = WELS_DIV_ROUND (pWelsSvcRc->iBitRate * pWelsSvcRc->iSkipBufferRatio, INT_MULTIPLY);
  pWelsSvcRc->iBufferSizePadding = WELS_DIV_ROUND (pWelsSvcRc->iBitRate * PADDING_BUFFER_RATIO, INT_MULTIPLY);

//change remaining bits
  if (pWelsSvcRc->iBitsPerFrame > REMAIN_BITS_TH) {
    pWelsSvcRc->iRemainingBits = WELS_DIV_ROUND (static_cast<int64_t> (pWelsSvcRc->iRemainingBits) * input_iBitsPerFrame,
                                 pWelsSvcRc->iBitsPerFrame);
  }
  pWelsSvcRc->iBitsPerFrame = input_iBitsPerFrame;
  pWelsSvcRc->iMaxBitsPerFrame = WELS_DIV_ROUND (pDLayerParam->iMaxSpatialBitrate,
                                 pDLayerParamInternal->fOutputFrameRate);
}


void RcInitVGop (sWelsEncCtx* pEncCtx) {
  const int32_t kiDid = pEncCtx->uiDependencyId;
  SWelsSvcRc* pWelsSvcRc = &pEncCtx->pWelsSvcRc[kiDid];
  SRCTemporal* pTOverRc = pWelsSvcRc->pTemporalOverRc;
  const int32_t kiHighestTid = pEncCtx->pSvcParam->sDependencyLayers[kiDid].iHighestTemporalId;

  pWelsSvcRc->iRemainingBits = VGOP_SIZE * pWelsSvcRc->iBitsPerFrame;
  pWelsSvcRc->iRemainingWeights = pWelsSvcRc->iGopNumberInVGop * WEIGHT_MULTIPLY;

  pWelsSvcRc->iFrameCodedInVGop = 0;
  pWelsSvcRc->iGopIndexInVGop = 0;

  for (int32_t i = 0; i <= kiHighestTid; ++ i)
    pTOverRc[i].iGopBitsDq = 0;
  pWelsSvcRc->iSkipFrameInVGop = 0;
}

void RcInitRefreshParameter (sWelsEncCtx* pEncCtx) {
  const int32_t kiDid                         = pEncCtx->uiDependencyId;
  SWelsSvcRc* pWelsSvcRc                      = &pEncCtx->pWelsSvcRc[kiDid];
  SRCTemporal* pTOverRc                       = pWelsSvcRc->pTemporalOverRc;
  SSpatialLayerConfig* pDLayerParam           = &pEncCtx->pSvcParam->sSpatialLayers[kiDid];
  SSpatialLayerInternal* pDLayerParamInternal = &pEncCtx->pSvcParam->sDependencyLayers[kiDid];
  const int32_t kiHighestTid = pDLayerParamInternal->iHighestTemporalId;
  int32_t i;

//I frame R-Q Model
  pWelsSvcRc->iIntraComplexity = 0;
  pWelsSvcRc->iIntraMbCount = 0;
  pWelsSvcRc->iIntraComplxMean = 0;
//P frame R-Q Model
  for (i = 0; i <= kiHighestTid; i++) {
    pTOverRc[i].iPFrameNum = 0;
    pTOverRc[i].iLinearCmplx = 0;
    pTOverRc[i].iFrameCmplxMean = 0;
  }

  pWelsSvcRc->iBufferFullnessSkip = 0;
  pWelsSvcRc->iBufferMaxBRFullness[EVEN_TIME_WINDOW] = 0;
  pWelsSvcRc->iBufferMaxBRFullness[ODD_TIME_WINDOW] = 0;
  pWelsSvcRc->iPredFrameBit = 0;
  pWelsSvcRc->iBufferFullnessPadding = 0;

  pWelsSvcRc->iGopIndexInVGop = 0;
  pWelsSvcRc->iRemainingBits = 0;
  pWelsSvcRc->iBitsPerFrame = 0;

//Backup the initial bitrate and fps
  pWelsSvcRc->iPreviousBitrate  = pDLayerParam->iSpatialBitrate;
  pWelsSvcRc->dPreviousFps      = pDLayerParamInternal->fOutputFrameRate;

  memset (pWelsSvcRc->pCurrentFrameGomSad, 0, pWelsSvcRc->iGomSize * sizeof (int32_t));

  RcInitTlWeight (pEncCtx);
  RcUpdateBitrateFps (pEncCtx);
  RcInitVGop (pEncCtx);
}

bool RcJudgeBitrateFpsUpdate (sWelsEncCtx* pEncCtx) {
  int32_t iCurDid = pEncCtx->uiDependencyId;
  SWelsSvcRc* pWelsSvcRc       = &pEncCtx->pWelsSvcRc[iCurDid];
  SSpatialLayerInternal* pDLayerParamInternal    = &pEncCtx->pSvcParam->sDependencyLayers[iCurDid];
  SSpatialLayerConfig* pDLayerParam    = &pEncCtx->pSvcParam->sSpatialLayers[iCurDid];

  if ((pWelsSvcRc->iPreviousBitrate != pDLayerParam->iSpatialBitrate) ||
      (pWelsSvcRc->dPreviousFps - pDLayerParamInternal->fOutputFrameRate) > EPSN ||
      (pWelsSvcRc->dPreviousFps - pDLayerParamInternal->fOutputFrameRate) < -EPSN) {
    pWelsSvcRc->iPreviousBitrate = pDLayerParam->iSpatialBitrate;
    pWelsSvcRc->dPreviousFps = pDLayerParamInternal->fOutputFrameRate;
    return true;
  } else
    return false;
}

#if GOM_TRACE_FLAG
void RcTraceVGopBitrate (sWelsEncCtx* pEncCtx) {
  const int32_t kiDid    = pEncCtx->uiDependencyId;
  SWelsSvcRc* pWelsSvcRc = &pEncCtx->pWelsSvcRc[kiDid];

  if (pWelsSvcRc->iFrameCodedInVGop) {
    const int32_t kiHighestTid  = pEncCtx->pSvcParam->sDependencyLayers[kiDid].iHighestTemporalId;
    SRCTemporal* pTOverRc       = pWelsSvcRc->pTemporalOverRc;
    int32_t iVGopBitrate = 0;
    int32_t iTotalBits = pWelsSvcRc->iPaddingBitrateStat;
    int32_t iTid = 0;
    while (iTid <= kiHighestTid) {
      iTotalBits += pTOverRc[iTid].iGopBitsDq;
      ++ iTid;
    }
    int32_t iFrameInVGop = pWelsSvcRc->iFrameCodedInVGop + pWelsSvcRc->iSkipFrameInVGop;
    if (0 != iFrameInVGop)
      iVGopBitrate = WELS_ROUND (iTotalBits / iFrameInVGop * pWelsSvcRc->fFrameRate);
    WelsLog (& (pEncCtx->sLogCtx), WELS_LOG_DEBUG, "[Rc] VGOPbitrate%d: %d ", kiDid, iVGopBitrate);
    if (iTotalBits > 0) {
      iTid = 0;
      while (iTid <= kiHighestTid) {
        WelsLog (& (pEncCtx->sLogCtx), WELS_LOG_DEBUG, "T%d=%8.3f ", iTid, (double) (pTOverRc[iTid].iGopBitsDq / iTotalBits));
        ++ iTid;
      }
    }
  }
}
#endif

void RcUpdateTemporalZero (sWelsEncCtx* pEncCtx) {
  const int32_t kiDid                   = pEncCtx->uiDependencyId;
  SWelsSvcRc* pWelsSvcRc                = &pEncCtx->pWelsSvcRc[kiDid];
  SSpatialLayerInternal* pDLayerParam   = &pEncCtx->pSvcParam->sDependencyLayers[kiDid];
  const int32_t kiGopSize               = (1 << pDLayerParam->iDecompositionStages);

  if (pWelsSvcRc->iPreviousGopSize  != kiGopSize) {
#if GOM_TRACE_FLAG
    RcTraceVGopBitrate (pEncCtx);
#endif
    RcInitTlWeight (pEncCtx);
    RcInitVGop (pEncCtx);
  } else if (pWelsSvcRc->iGopIndexInVGop == pWelsSvcRc->iGopNumberInVGop || pEncCtx->eSliceType == I_SLICE) {
#if GOM_TRACE_FLAG
    RcTraceVGopBitrate (pEncCtx);
#endif
    RcInitVGop (pEncCtx);
  }
  pWelsSvcRc->iGopIndexInVGop++;
}


void RcCalculateIdrQp (sWelsEncCtx* pEncCtx) {
  double dBpp = 0;
  int32_t i;

//64k@6fps for 90p:     bpp 0.74    QP:24
//192k@12fps for 180p:  bpp 0.28    QP:26
//512k@24fps for 360p:  bpp 0.09    QP:30
//1500k@30fps for 720p: bpp 0.05    QP:32
  double dBppArray[4][3] = {{0.5, 0.75, 1.0}, {0.2, 0.3, 0.4}, {0.05, 0.09, 0.13}, {0.03, 0.06, 0.1}};
  int32_t dInitialQPArray[4][4] = {{28, 26, 24, 22}, {30, 28, 26, 24}, {32, 30, 28, 26}, {34, 32, 30, 28}};
  int32_t iBppIndex = 0;
  int32_t iQpRangeArray[4][2] = {{37, 25}, {36, 24}, {35, 23}, {34, 22}};
  int64_t iFrameComplexity = pEncCtx->pVaa->sComplexityAnalysisParam.iFrameComplexity;
  if (pEncCtx->pSvcParam->iUsageType == SCREEN_CONTENT_REAL_TIME) {
    SVAAFrameInfoExt* pVaa = static_cast<SVAAFrameInfoExt*> (pEncCtx->pVaa);
    iFrameComplexity = pVaa->sComplexityScreenParam.iFrameComplexity;
  }
  SWelsSvcRc* pWelsSvcRc                = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  SSpatialLayerConfig* pDLayerParam     = &pEncCtx->pSvcParam->sSpatialLayers[pEncCtx->uiDependencyId];
  SSpatialLayerInternal* pDLayerParamInternal       = &pEncCtx->pSvcParam->sDependencyLayers[pEncCtx->uiDependencyId];
  if (pDLayerParamInternal->fOutputFrameRate > EPSN && pDLayerParam->iVideoWidth && pDLayerParam->iVideoHeight)
    dBpp = (double) (pDLayerParam->iSpatialBitrate) / (double) (pDLayerParamInternal->fOutputFrameRate *
           pDLayerParam->iVideoWidth *
           pDLayerParam->iVideoHeight);
  else
    dBpp = 0.1;
//Area*2
  if (pDLayerParam->iVideoWidth * pDLayerParam->iVideoHeight <= 28800) // 90p video:160*90
    iBppIndex = 0;
  else if (pDLayerParam->iVideoWidth * pDLayerParam->iVideoHeight <= 115200) // 180p video:320*180
    iBppIndex = 1;
  else if (pDLayerParam->iVideoWidth * pDLayerParam->iVideoHeight <= 460800) // 360p video:640*360
    iBppIndex = 2;
  else
    iBppIndex = 3;

//Search
  for (i = 0; i < 3; i++) {
    if (dBpp <= dBppArray[iBppIndex][i])
      break;
  }
  int32_t iMaxQp = iQpRangeArray[i][0];
  int32_t iMinQp = iQpRangeArray[i][1];
  iMinQp = WELS_CLIP3 (iMinQp, pWelsSvcRc->iMinQp, pWelsSvcRc->iMaxQp);
  iMaxQp = WELS_CLIP3 (iMaxQp, pWelsSvcRc->iMinQp, pWelsSvcRc->iMaxQp);
  if (0 == pWelsSvcRc->iIdrNum) { //the first IDR frame
    pWelsSvcRc->iInitialQp = dInitialQPArray[iBppIndex][i];
  } else {

    //obtain the idr qp using previous idr complexity
    if (pWelsSvcRc->iNumberMbFrame != pWelsSvcRc->iIntraMbCount) {
      pWelsSvcRc->iIntraComplexity = pWelsSvcRc->iIntraComplexity * pWelsSvcRc->iNumberMbFrame /
                                     pWelsSvcRc->iIntraMbCount;
    }

    int64_t iCmplxRatio = WELS_DIV_ROUND64 (iFrameComplexity * INT_MULTIPLY,
                                            pWelsSvcRc->iIntraComplxMean);
    iCmplxRatio = WELS_CLIP3 (iCmplxRatio, INT_MULTIPLY - FRAME_CMPLX_RATIO_RANGE, INT_MULTIPLY + FRAME_CMPLX_RATIO_RANGE);
    pWelsSvcRc->iQStep = WELS_DIV_ROUND ((pWelsSvcRc->iIntraComplexity * iCmplxRatio),
                                         (pWelsSvcRc->iTargetBits * INT_MULTIPLY));
    pWelsSvcRc->iInitialQp = RcConvertQStep2Qp (pWelsSvcRc->iQStep);
  }

  pWelsSvcRc->iInitialQp = WELS_CLIP3 (pWelsSvcRc->iInitialQp, iMinQp, iMaxQp);
  pEncCtx->iGlobalQp = pWelsSvcRc->iInitialQp;
  pWelsSvcRc->iQStep = RcConvertQp2QStep (pEncCtx->iGlobalQp);
  pWelsSvcRc->iLastCalculatedQScale = pEncCtx->iGlobalQp;
  pWelsSvcRc->iMinFrameQp = WELS_CLIP3 (pEncCtx->iGlobalQp - DELTA_QP_BGD_THD, iMinQp, iMaxQp);
  pWelsSvcRc->iMaxFrameQp = WELS_CLIP3 (pEncCtx->iGlobalQp + DELTA_QP_BGD_THD, iMinQp, iMaxQp);

}

void RcCalculatePictureQp (sWelsEncCtx* pEncCtx) {
  SWelsSvcRc* pWelsSvcRc        = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  int32_t iTl                   = pEncCtx->uiTemporalId;
  SRCTemporal* pTOverRc         = &pWelsSvcRc->pTemporalOverRc[iTl];
  int32_t iLumaQp = 0;
  int32_t iDeltaQpTemporal = 0;
  int64_t iFrameComplexity = pEncCtx->pVaa->sComplexityAnalysisParam.iFrameComplexity;
  if (pEncCtx->pSvcParam->iUsageType == SCREEN_CONTENT_REAL_TIME) {
    SVAAFrameInfoExt* pVaa = static_cast<SVAAFrameInfoExt*> (pEncCtx->pVaa);
    iFrameComplexity = pVaa->sComplexityScreenParam.iFrameComplexity;
  }
  if (0 == pTOverRc->iPFrameNum) {
    iLumaQp = pWelsSvcRc->iInitialQp;
  } else if (pWelsSvcRc->iCurrentBitsLevel == BITS_EXCEEDED) {
    iLumaQp = pWelsSvcRc->iLastCalculatedQScale + DELTA_QP_BGD_THD;
//limit QP
    int32_t iLastIdxCodecInVGop = pWelsSvcRc->iFrameCodedInVGop - 1;
    if (iLastIdxCodecInVGop < 0)
      iLastIdxCodecInVGop += VGOP_SIZE;
    int32_t iTlLast = pWelsSvcRc->iTlOfFrames[iLastIdxCodecInVGop];
    iDeltaQpTemporal = iTl - iTlLast;
    if (0 == iTlLast && iTl > 0)
      iDeltaQpTemporal += 1;
    else if (0 == iTl && iTlLast > 0)
      iDeltaQpTemporal -= 1;

  } else {
    int64_t iCmplxRatio = WELS_DIV_ROUND64 (iFrameComplexity * INT_MULTIPLY,
                                            pTOverRc->iFrameCmplxMean);
    iCmplxRatio = WELS_CLIP3 (iCmplxRatio, INT_MULTIPLY - FRAME_CMPLX_RATIO_RANGE, INT_MULTIPLY + FRAME_CMPLX_RATIO_RANGE);

    pWelsSvcRc->iQStep = WELS_DIV_ROUND ((pTOverRc->iLinearCmplx * iCmplxRatio), (pWelsSvcRc->iTargetBits * INT_MULTIPLY));
    iLumaQp = RcConvertQStep2Qp (pWelsSvcRc->iQStep);
    WelsLog (& (pEncCtx->sLogCtx), WELS_LOG_DEBUG,
             "iCmplxRatio = %d,frameComplexity = %" PRId64 ",iFrameCmplxMean = %" PRId64 ",iQStep = %d,iLumaQp = %d", (int)iCmplxRatio,
             iFrameComplexity, pTOverRc->iFrameCmplxMean, pWelsSvcRc->iQStep, iLumaQp);
//limit QP
    int32_t iLastIdxCodecInVGop = pWelsSvcRc->iFrameCodedInVGop - 1;
    if (iLastIdxCodecInVGop < 0)
      iLastIdxCodecInVGop += VGOP_SIZE;
    int32_t iTlLast = pWelsSvcRc->iTlOfFrames[iLastIdxCodecInVGop];
    int32_t iDeltaQpTemporal = iTl - iTlLast;
    if (0 == iTlLast && iTl > 0)
      iDeltaQpTemporal += 1;
    else if (0 == iTl && iTlLast > 0)
      iDeltaQpTemporal -= 1;
  }
  pWelsSvcRc->iMinFrameQp =  WELS_CLIP3 (pWelsSvcRc->iLastCalculatedQScale - pWelsSvcRc->iFrameDeltaQpLower +
                                         iDeltaQpTemporal, pTOverRc->iMinQp, pTOverRc->iMaxQp) ;
  pWelsSvcRc->iMaxFrameQp =  WELS_CLIP3 (pWelsSvcRc->iLastCalculatedQScale + pWelsSvcRc->iFrameDeltaQpUpper +
                                         iDeltaQpTemporal, pTOverRc->iMinQp, pTOverRc->iMaxQp);

  iLumaQp = WELS_CLIP3 (iLumaQp, pWelsSvcRc->iMinFrameQp, pWelsSvcRc->iMaxFrameQp);

  if (pEncCtx->pSvcParam->bEnableAdaptiveQuant) {

    iLumaQp =  WELS_DIV_ROUND (iLumaQp * INT_MULTIPLY - pEncCtx->pVaa->sAdaptiveQuantParam.iAverMotionTextureIndexToDeltaQp,
                               INT_MULTIPLY);
    iLumaQp = WELS_CLIP3 (iLumaQp, pWelsSvcRc->iMinFrameQp, pWelsSvcRc->iMaxFrameQp);
  }
  pWelsSvcRc->iQStep = RcConvertQp2QStep (iLumaQp);
  pWelsSvcRc->iLastCalculatedQScale = iLumaQp;
  pEncCtx->iGlobalQp = iLumaQp;
}

void GomRCInitForOneSlice (SSlice* pSlice, const int32_t kiBitsPerMb) {
  SRCSlicing* pSOverRc        = &pSlice->sSlicingOverRc;
  pSOverRc->iStartMbSlice     = pSlice->sSliceHeaderExt.sSliceHeader.iFirstMbInSlice;
  pSOverRc->iEndMbSlice       = pSOverRc->iStartMbSlice + pSlice->iCountMbNumInSlice - 1;
  pSOverRc->iTargetBitsSlice  = WELS_DIV_ROUND (static_cast<int64_t> (kiBitsPerMb) * pSlice->iCountMbNumInSlice,
                                INT_MULTIPLY);
}

void RcInitSliceInformation (sWelsEncCtx* pEncCtx) {
  SSlice** ppSliceInLayer   = pEncCtx->pCurDqLayer->ppSliceInLayer;
  SWelsSvcRc* pWelsSvcRc    = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  const int32_t kiSliceNum  = pEncCtx->pCurDqLayer->iMaxSliceNum;
  pWelsSvcRc->iBitsPerMb    = WELS_DIV_ROUND (static_cast<int64_t> (pWelsSvcRc->iTargetBits) * INT_MULTIPLY,
                              pWelsSvcRc->iNumberMbFrame);
  pWelsSvcRc->bGomRC        = (RC_OFF_MODE == pEncCtx->pSvcParam->iRCMode ||
                               RC_BUFFERBASED_MODE == pEncCtx->pSvcParam->iRCMode) ? false : true;
  for (int32_t i = 0; i < kiSliceNum; i++) {
    SRCSlicing* pSOverRc        = &ppSliceInLayer[i]->sSlicingOverRc;
    pSOverRc->iTotalQpSlice     = 0;
    pSOverRc->iTotalMbSlice     = 0;
    pSOverRc->iFrameBitsSlice   = 0;
    pSOverRc->iGomBitsSlice     = 0;
    pSOverRc->iStartMbSlice     = 0;
    pSOverRc->iEndMbSlice       = 0;
    pSOverRc->iTargetBitsSlice  = 0;
  }
}

void RcDecideTargetBits (sWelsEncCtx* pEncCtx) {
  SWelsSvcRc* pWelsSvcRc        = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  SRCTemporal* pTOverRc         = &pWelsSvcRc->pTemporalOverRc[pEncCtx->uiTemporalId];

  pWelsSvcRc->iCurrentBitsLevel = BITS_NORMAL;
  //allocate bits
  if (pEncCtx->eSliceType == I_SLICE) {
    pWelsSvcRc->iTargetBits = pWelsSvcRc->iBitsPerFrame * IDR_BITRATE_RATIO;
  } else {
    if (pWelsSvcRc->iRemainingWeights > pTOverRc->iTlayerWeight)
      pWelsSvcRc->iTargetBits = WELS_DIV_ROUND (static_cast<int64_t> (pWelsSvcRc->iRemainingBits) * pTOverRc->iTlayerWeight,
                                pWelsSvcRc->iRemainingWeights);
    else //this case should be not hit. needs to more test case to verify this
      pWelsSvcRc->iTargetBits = pWelsSvcRc->iRemainingBits;
    if ((pWelsSvcRc->iTargetBits <= 0) && ((pEncCtx->pSvcParam->iRCMode == RC_BITRATE_MODE)
                                           && (pEncCtx->pSvcParam->bEnableFrameSkip == false))) {
      pWelsSvcRc->iCurrentBitsLevel = BITS_EXCEEDED;
    }
    pWelsSvcRc->iTargetBits = WELS_CLIP3 (pWelsSvcRc->iTargetBits, pTOverRc->iMinBitsTl, pTOverRc->iMaxBitsTl);
  }
  pWelsSvcRc->iRemainingWeights -= pTOverRc->iTlayerWeight;

}

void RcDecideTargetBitsTimestamp (sWelsEncCtx* pEncCtx) {
  //decide one frame bits allocated
  SWelsSvcRc* pWelsSvcRc = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  SSpatialLayerConfig* pDLayerParam = &pEncCtx->pSvcParam->sSpatialLayers[pEncCtx->uiDependencyId];
  int32_t iTl                 = pEncCtx->uiTemporalId;
  SRCTemporal* pTOverRc       = &pWelsSvcRc->pTemporalOverRc[iTl];
  pWelsSvcRc->iCurrentBitsLevel = BITS_NORMAL;

  if (pEncCtx->eSliceType == I_SLICE) {
    int32_t iBufferTh = static_cast<int32_t> (pWelsSvcRc->iBufferSizeSkip - pWelsSvcRc->iBufferFullnessSkip);
    if (iBufferTh <= 0) {
      pWelsSvcRc->iCurrentBitsLevel = BITS_EXCEEDED;
      pWelsSvcRc->iTargetBits = pTOverRc->iMinBitsTl;
    } else {
      int32_t iMaxTh = iBufferTh * 3 / 4;
      int32_t iMinTh = static_cast<int32_t>((pDLayerParam->fFrameRate < 8) ? iBufferTh * 1.0 / 4 : iBufferTh * 2 / pDLayerParam->fFrameRate);
      if (pDLayerParam->fFrameRate < (IDR_BITRATE_RATIO + 1))
        pWelsSvcRc->iTargetBits = static_cast<int32_t> (((double) (pDLayerParam->iSpatialBitrate) / (double) (
                                    pDLayerParam->fFrameRate)));
      else
        pWelsSvcRc->iTargetBits = static_cast<int32_t> (((double) (pDLayerParam->iSpatialBitrate) / (double) (
                                    pDLayerParam->fFrameRate) * IDR_BITRATE_RATIO));
      WelsLog (& (pEncCtx->sLogCtx), WELS_LOG_DEBUG,
               "iMaxTh = %d,iMinTh = %d,pWelsSvcRc->iTargetBits = %d,pWelsSvcRc->iBufferSizeSkip = %d, pWelsSvcRc->iBufferFullnessSkip= %"
               PRId64,
               iMaxTh, iMinTh, pWelsSvcRc->iTargetBits, pWelsSvcRc->iBufferSizeSkip, pWelsSvcRc->iBufferFullnessSkip);
      pWelsSvcRc->iTargetBits = WELS_CLIP3 (pWelsSvcRc->iTargetBits, iMinTh, iMaxTh);
    }

  } else {
    int32_t iBufferTh = static_cast<int32_t> (pWelsSvcRc->iBufferSizeSkip - pWelsSvcRc->iBufferFullnessSkip);
    if (iBufferTh <= 0) {
      pWelsSvcRc->iCurrentBitsLevel = BITS_EXCEEDED;
      pWelsSvcRc->iTargetBits = pTOverRc->iMinBitsTl;
      WelsLog (& (pEncCtx->sLogCtx), WELS_LOG_DEBUG,
               "iMaxTh = %d,pWelsSvcRc->iTargetBits = %d,pWelsSvcRc->iBufferSizeSkip = %d, pWelsSvcRc->iBufferFullnessSkip= %" PRId64,
               iBufferTh, pWelsSvcRc->iTargetBits, pWelsSvcRc->iBufferSizeSkip, pWelsSvcRc->iBufferFullnessSkip);
    } else {

      SSpatialLayerInternal* pDLayerParamInternal = &pEncCtx->pSvcParam->sDependencyLayers[pEncCtx->uiDependencyId];
      const int32_t kiGopSize   = (1 << pDLayerParamInternal->iDecompositionStages);
      int32_t iAverageFrameSize = (int32_t) ((double) (pDLayerParam->iSpatialBitrate) / (double) (pDLayerParam->fFrameRate));
      const int32_t kiGopBits   = iAverageFrameSize * kiGopSize;
      pWelsSvcRc->iTargetBits = WELS_DIV_ROUND (pTOverRc->iTlayerWeight * kiGopBits, INT_MULTIPLY * 10 * 2);

      int32_t iMaxTh = iBufferTh / 2;
      int32_t iMinTh = static_cast<int32_t>((pDLayerParam->fFrameRate < 8) ? iBufferTh * 1.0 / 4 : iBufferTh * 2 / pDLayerParam->fFrameRate);
      WelsLog (& (pEncCtx->sLogCtx), WELS_LOG_DEBUG,
               "iMaxTh = %d,iMinTh = %d,pWelsSvcRc->iTargetBits = %d,pWelsSvcRc->iBufferSizeSkip = %d, pWelsSvcRc->iBufferFullnessSkip= % "
               PRId64,
               iMaxTh, iMinTh, pWelsSvcRc->iTargetBits, pWelsSvcRc->iBufferSizeSkip, pWelsSvcRc->iBufferFullnessSkip);
      pWelsSvcRc->iTargetBits = WELS_CLIP3 (pWelsSvcRc->iTargetBits, iMinTh, iMaxTh);
    }
  }
}

void RcInitGomParameters (sWelsEncCtx* pEncCtx) {
  SSlice** ppSliceInLayer     = pEncCtx->pCurDqLayer->ppSliceInLayer;
  SWelsSvcRc* pWelsSvcRc      = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  SRCSlicing* pSOverRc        = &ppSliceInLayer[0]->sSlicingOverRc;
  const int32_t kiSliceNum    = pEncCtx->pCurDqLayer->iMaxSliceNum;
  const int32_t kiGlobalQp    = pEncCtx->iGlobalQp;

  pWelsSvcRc->iAverageFrameQp = 0;
  for (int32_t i = 0; i < kiSliceNum; ++i) {
    pSOverRc                            = &ppSliceInLayer[i]->sSlicingOverRc;
    pSOverRc->iComplexityIndexSlice     = 0;
    pSOverRc->iCalculatedQpSlice        = kiGlobalQp;
  }
  memset (pWelsSvcRc->pGomComplexity, 0, pWelsSvcRc->iGomSize * sizeof (double));
  memset (pWelsSvcRc->pGomCost, 0, pWelsSvcRc->iGomSize * sizeof (int32_t));
}

void RcCalculateMbQp (sWelsEncCtx* pEncCtx, SSlice* pSlice, SMB* pCurMb) {
  SWelsSvcRc* pWelsSvcRc        = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  SRCSlicing* pSOverRc          = &pSlice->sSlicingOverRc;

  int32_t iLumaQp               = pSOverRc->iCalculatedQpSlice;
  SDqLayer* pCurLayer           = pEncCtx->pCurDqLayer;
  const uint8_t kuiChromaQpIndexOffset = pCurLayer->sLayerInfo.pPpsP->uiChromaQpIndexOffset;
  if (pEncCtx->pSvcParam->bEnableAdaptiveQuant) {
    iLumaQp   = (int8_t)WELS_CLIP3 (iLumaQp +
                                    pEncCtx->pVaa->sAdaptiveQuantParam.pMotionTextureIndexToDeltaQp[pCurMb->iMbXY], pWelsSvcRc->iMinFrameQp,
                                    pWelsSvcRc->iMaxFrameQp);
  }
  pCurMb->uiChromaQp    = g_kuiChromaQpTable[CLIP3_QP_0_51 (iLumaQp + kuiChromaQpIndexOffset)];
  pCurMb->uiLumaQp      = iLumaQp;
}

SWelsSvcRc* RcJudgeBaseUsability (sWelsEncCtx* pEncCtx) {
  SWelsSvcRc* pWelsSvcRc  = NULL, *pWelsSvcRc_Base = NULL;
  SSpatialLayerConfig* pDlpBase = NULL, *pDLayerParam = NULL;
  SSpatialLayerInternal* pDlpBaseInternal = NULL;
  if (pEncCtx->uiDependencyId <= 0)
    return NULL;
  pDlpBaseInternal = &pEncCtx->pSvcParam->sDependencyLayers[pEncCtx->uiDependencyId - 1];
  pDlpBase = &pEncCtx->pSvcParam->sSpatialLayers[pEncCtx->uiDependencyId - 1];
  pWelsSvcRc_Base = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId - 1];
  if (pEncCtx->uiTemporalId <= pDlpBaseInternal->iDecompositionStages) {
    pWelsSvcRc      = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
    pWelsSvcRc_Base = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId - 1];
    pDLayerParam             = &pEncCtx->pSvcParam->sSpatialLayers[pEncCtx->uiDependencyId];
    pDlpBase        = &pEncCtx->pSvcParam->sSpatialLayers[pEncCtx->uiDependencyId - 1];
    if ((pDLayerParam->iVideoWidth * pDLayerParam->iVideoHeight / pWelsSvcRc->iNumberMbGom) ==
        (pDlpBase->iVideoWidth * pDlpBase->iVideoHeight / pWelsSvcRc_Base->iNumberMbGom))
      return pWelsSvcRc_Base;
    else
      return NULL;
  } else
    return NULL;
}

void RcGomTargetBits (sWelsEncCtx* pEncCtx, SSlice* pSlice) {
  SWelsSvcRc* pWelsSvcRc        = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  SWelsSvcRc* pWelsSvcRc_Base   = NULL;
  SRCSlicing* pSOverRc          = &pSlice->sSlicingOverRc;

  int32_t iAllocateBits = 0;
  int32_t iSumSad = 0;
  int32_t iLastGomIndex = 0;
  int32_t iLeftBits = 0;
  const int32_t kiComplexityIndex = pSOverRc->iComplexityIndexSlice;
  int32_t i;

  iLastGomIndex  = pSOverRc->iEndMbSlice / pWelsSvcRc->iNumberMbGom;
  iLeftBits = pSOverRc->iTargetBitsSlice - pSOverRc->iFrameBitsSlice;
  if (iLeftBits <= 0) {
    pSOverRc->iGomTargetBits = 0;
    return;
  } else if (kiComplexityIndex >= iLastGomIndex) {
    iAllocateBits = iLeftBits;
  } else {
    pWelsSvcRc_Base = RcJudgeBaseUsability (pEncCtx);
    pWelsSvcRc_Base = (pWelsSvcRc_Base) ? pWelsSvcRc_Base : pWelsSvcRc;
    for (i = kiComplexityIndex + 1; i <= iLastGomIndex; i++) {
      iSumSad += pWelsSvcRc_Base->pCurrentFrameGomSad[i];
    }

    if (0 == iSumSad)
      iAllocateBits = WELS_DIV_ROUND (iLeftBits, (iLastGomIndex - kiComplexityIndex));
    else
      iAllocateBits = WELS_DIV_ROUND ((int64_t)iLeftBits * pWelsSvcRc_Base->pCurrentFrameGomSad[kiComplexityIndex + 1],
                                      iSumSad);
  }
  pSOverRc->iGomTargetBits = iAllocateBits;
}



void RcCalculateGomQp (sWelsEncCtx* pEncCtx, SSlice* pSlice, SMB* pCurMb) {
  SWelsSvcRc* pWelsSvcRc    = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  SRCSlicing* pSOverRc      = &pSlice->sSlicingOverRc;
  int64_t iBitsRatio        = 1;

  int64_t iLeftBits         = pSOverRc->iTargetBitsSlice - pSOverRc->iFrameBitsSlice;
  int64_t iTargetLeftBits   = iLeftBits + pSOverRc->iGomBitsSlice - pSOverRc->iGomTargetBits;
  if ((iLeftBits <= 0) || (iTargetLeftBits <= 0)) {
    pSOverRc->iCalculatedQpSlice += 2;
  } else {
//globe decision
    iBitsRatio = 10000 * iLeftBits / (iTargetLeftBits + 1);
    if (iBitsRatio < 8409)              //2^(-1.5/6)*10000
      pSOverRc->iCalculatedQpSlice += 2;
    else if (iBitsRatio < 9439)         //2^(-0.5/6)*10000
      pSOverRc->iCalculatedQpSlice += 1;
    else if (iBitsRatio > 10600)        //2^(0.5/6)*10000
      pSOverRc->iCalculatedQpSlice -= 1;
    else if (iBitsRatio > 11900)        //2^(1.5/6)*10000
      pSOverRc->iCalculatedQpSlice -= 2;
  }
  pSOverRc->iCalculatedQpSlice = WELS_CLIP3 (pSOverRc->iCalculatedQpSlice, pWelsSvcRc->iMinFrameQp,
                                 pWelsSvcRc->iMaxFrameQp);
// WelsLog (& (pEncCtx->sLogCtx), WELS_LOG_DEBUG,"iCalculatedQpSlice =%d,iBitsRatio = %d\n",pSOverRc->iCalculatedQpSlice,iBitsRatio);
  pSOverRc->iGomBitsSlice = 0;

}

void   RcVBufferCalculationSkip (sWelsEncCtx* pEncCtx) {
  SWelsSvcRc* pWelsSvcRc = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  SRCTemporal* pTOverRc = pWelsSvcRc->pTemporalOverRc;
  const int32_t kiOutputBits = pWelsSvcRc->iBitsPerFrame;
  const int32_t kiOutputMaxBits = pWelsSvcRc->iMaxBitsPerFrame;
//condition 1: whole pBuffer fullness
  pWelsSvcRc->iBufferFullnessSkip += (pWelsSvcRc->iFrameDqBits - kiOutputBits);
  pWelsSvcRc->iBufferMaxBRFullness[EVEN_TIME_WINDOW] += (pWelsSvcRc->iFrameDqBits - kiOutputMaxBits);
  pWelsSvcRc->iBufferMaxBRFullness[ODD_TIME_WINDOW] += (pWelsSvcRc->iFrameDqBits - kiOutputMaxBits);

  WelsLog (& (pEncCtx->sLogCtx), WELS_LOG_DEBUG,
           "[Rc] bits in buffer = %" PRId64 ", bits in Max bitrate buffer = %" PRId64,
           pWelsSvcRc->iBufferFullnessSkip, pWelsSvcRc->iBufferMaxBRFullness[EVEN_TIME_WINDOW]);
//condition 2: VGOP bits constraint
  int64_t iVGopBitsPred = 0;
  for (int32_t i = pWelsSvcRc->iFrameCodedInVGop + 1; i < VGOP_SIZE; i++)
    iVGopBitsPred += pTOverRc[pWelsSvcRc->iTlOfFrames[i]].iMinBitsTl;
  iVGopBitsPred -= pWelsSvcRc->iRemainingBits;
  double dIncPercent = iVGopBitsPred * 100.0 / (pWelsSvcRc->iBitsPerFrame * VGOP_SIZE) -
                       (double)VGOP_BITS_PERCENTAGE_DIFF;

  if ((pWelsSvcRc->iBufferFullnessSkip > pWelsSvcRc->iBufferSizeSkip
       && pWelsSvcRc->iAverageFrameQp > pWelsSvcRc->iSkipQpValue)
      || (dIncPercent > pWelsSvcRc->iRcVaryPercentage)) {
    pWelsSvcRc->bSkipFlag = true;
  }
  WelsLog (& (pEncCtx->sLogCtx), WELS_LOG_DEBUG,
           "[Rc] VBV_Skip,dIncPercent = %f,iRcVaryPercentage = %d,pWelsSvcRc->bSkipFlag = %d", dIncPercent,
           pWelsSvcRc->iRcVaryPercentage, pWelsSvcRc->bSkipFlag);
}
void CheckFrameSkipBasedMaxbr (sWelsEncCtx* pEncCtx, const long long uiTimeStamp, int32_t iDidIdx) {
  SWelsSvcRc* pWelsSvcRc = &pEncCtx->pWelsSvcRc[iDidIdx];
  SSpatialLayerConfig* pDLayerParam     = &pEncCtx->pSvcParam->sSpatialLayers[iDidIdx];
  //SSpatialLayerInternal* pDLayerParamInternal     = &pEncCtx->pSvcParam->sDependencyLayers[pEncCtx->uiDependencyId];
  if (!pEncCtx->pSvcParam->bEnableFrameSkip)
    return;
  const int32_t iSentBits = pWelsSvcRc->iBitsPerFrame;
  const int32_t kiOutputMaxBits = pWelsSvcRc->iMaxBitsPerFrame;
  const int64_t kiMaxSpatialBitRate = pDLayerParam->iMaxSpatialBitrate;

//estimate allowed continual skipped frames in the sequence
  const int32_t iPredSkipFramesTarBr = (WELS_DIV_ROUND (pWelsSvcRc->iBufferFullnessSkip, iSentBits) + 1) >> 1;
  const int32_t iPredSkipFramesMaxBr = (WELS_MAX (WELS_DIV_ROUND (pWelsSvcRc->iBufferMaxBRFullness[EVEN_TIME_WINDOW],
                                        kiOutputMaxBits), 0) + 1) >> 1;

//calculate the remaining bits in TIME_CHECK_WINDOW
  const int32_t iAvailableBitsInTimeWindow = WELS_DIV_ROUND ((TIME_CHECK_WINDOW - pEncCtx->iCheckWindowInterval) *
      kiMaxSpatialBitRate, 1000);
  const int32_t iAvailableBitsInShiftTimeWindow = WELS_DIV_ROUND ((TIME_CHECK_WINDOW - pEncCtx->iCheckWindowIntervalShift)
      * kiMaxSpatialBitRate, 1000);

  bool bJudgeMaxBRbSkip[TIME_WINDOW_TOTAL];//0: EVEN_TIME_WINDOW; 1: ODD_TIME_WINDOW

  /* 4 cases for frame skipping
  1:skipping when buffer size larger than target threshold and current continual skip frames is allowed
  2:skipping when MaxBr buffer size + predict frame size - remaining bits in time window < 0 and current continual skip frames is allowed
  3:if in last ODD_TIME_WINDOW the MAX Br is overflowed, make more strict skipping conditions
  4:such as case 3 in the other window
  */
  bool bJudgeBufferFullSkip = (pWelsSvcRc->iContinualSkipFrames <= iPredSkipFramesTarBr)
                              && (pWelsSvcRc->iBufferFullnessSkip > pWelsSvcRc->iBufferSizeSkip);
  bool bJudgeMaxBRbufferFullSkip = (pWelsSvcRc->iContinualSkipFrames <= iPredSkipFramesMaxBr)
                                   && (pEncCtx->iCheckWindowInterval > TIME_CHECK_WINDOW / 2)
                                   && (pWelsSvcRc->iBufferMaxBRFullness[EVEN_TIME_WINDOW] + pWelsSvcRc->iPredFrameBit - iAvailableBitsInTimeWindow > 0);
  bJudgeMaxBRbSkip[EVEN_TIME_WINDOW] = (pEncCtx->iCheckWindowInterval > TIME_CHECK_WINDOW / 2)
                                       && (pWelsSvcRc->bNeedShiftWindowCheck[EVEN_TIME_WINDOW])
                                       && (pWelsSvcRc->iBufferMaxBRFullness[EVEN_TIME_WINDOW] + pWelsSvcRc->iPredFrameBit - iAvailableBitsInTimeWindow +
                                           kiOutputMaxBits > 0);
  bJudgeMaxBRbSkip[ODD_TIME_WINDOW] = (pEncCtx->iCheckWindowIntervalShift > TIME_CHECK_WINDOW / 2)
                                      && (pWelsSvcRc->bNeedShiftWindowCheck[ODD_TIME_WINDOW])
                                      && (pWelsSvcRc->iBufferMaxBRFullness[ODD_TIME_WINDOW] + pWelsSvcRc->iPredFrameBit - iAvailableBitsInShiftTimeWindow +
                                          kiOutputMaxBits > 0);

  pWelsSvcRc->bSkipFlag = false;
  if (bJudgeBufferFullSkip || bJudgeMaxBRbufferFullSkip || bJudgeMaxBRbSkip[EVEN_TIME_WINDOW]
      || bJudgeMaxBRbSkip[ODD_TIME_WINDOW]) {
    pWelsSvcRc->bSkipFlag = true;
    pWelsSvcRc->iSkipFrameNum++;
    pWelsSvcRc->iSkipFrameInVGop++;
    pWelsSvcRc->iBufferFullnessSkip -= iSentBits;
    pWelsSvcRc->iRemainingBits +=  iSentBits;
    pWelsSvcRc->iBufferMaxBRFullness[EVEN_TIME_WINDOW] -= kiOutputMaxBits;
    pWelsSvcRc->iBufferMaxBRFullness[ODD_TIME_WINDOW] -= kiOutputMaxBits;
    WelsLog (& (pEncCtx->sLogCtx), WELS_LOG_DEBUG,
             "[Rc] bits in buffer = %" PRId64 ", bits in Max bitrate buffer = %" PRId64 ", Predict skip frames = %d and %d",
             pWelsSvcRc->iBufferFullnessSkip, pWelsSvcRc->iBufferMaxBRFullness[EVEN_TIME_WINDOW], iPredSkipFramesTarBr,
             iPredSkipFramesMaxBr);
    pWelsSvcRc->iBufferFullnessSkip = WELS_MAX (pWelsSvcRc->iBufferFullnessSkip, 0);
  }
}

bool WelsRcCheckFrameStatus (sWelsEncCtx* pEncCtx, long long uiTimeStamp, int32_t iSpatialNum, int32_t iCurDid) {

  bool bSkipMustFlag = false;

  SSpatialPicIndex* pSpatialIndexMap = &pEncCtx->sSpatialIndexMap[0];

  //simul_cast AVC control
  if (pEncCtx->pSvcParam->bSimulcastAVC) {
    //check target_br skip and update info
    int32_t iDidIdx = iCurDid;
    if (pEncCtx->pFuncList->pfRc.pfWelsRcPicDelayJudge) {
      pEncCtx->pFuncList->pfRc.pfWelsRcPicDelayJudge (pEncCtx, uiTimeStamp, iDidIdx);
    }
    if (true == pEncCtx->pWelsSvcRc[iDidIdx].bSkipFlag) {
      bSkipMustFlag = true;
    }
    //check max_br skip
    if (pEncCtx->pFuncList->pfRc.pfWelsCheckSkipBasedMaxbr) {
      if ((!bSkipMustFlag) && (pEncCtx->pSvcParam->sSpatialLayers[iDidIdx].iMaxSpatialBitrate != UNSPECIFIED_BIT_RATE)) {
        pEncCtx->pFuncList->pfRc.pfWelsCheckSkipBasedMaxbr (pEncCtx, uiTimeStamp, iDidIdx);
        if (true == pEncCtx->pWelsSvcRc[iDidIdx].bSkipFlag) {
          bSkipMustFlag = true;

        }
      }
    }
    if (bSkipMustFlag) {
      pEncCtx->pWelsSvcRc[iDidIdx].uiLastTimeStamp = uiTimeStamp;
      pEncCtx->pWelsSvcRc[iDidIdx].bSkipFlag = false;
      pEncCtx->pWelsSvcRc[iDidIdx].iContinualSkipFrames++;
      return true;
    }
  } else { //SVC control
    for (int32_t i = 0; i < iSpatialNum; i++) {
      int32_t iDidIdx = (pSpatialIndexMap + i)->iDid;
      //check target_br skip and update info

      if (pEncCtx->pFuncList->pfRc.pfWelsRcPicDelayJudge) {
        pEncCtx->pFuncList->pfRc.pfWelsRcPicDelayJudge (pEncCtx, uiTimeStamp, iDidIdx);
      }
      if (true == pEncCtx->pWelsSvcRc[iDidIdx].bSkipFlag) {
        bSkipMustFlag = true;
      }
      //check max_br skip
      if (pEncCtx->pFuncList->pfRc.pfWelsCheckSkipBasedMaxbr) {
        if ((!bSkipMustFlag) && (pEncCtx->pSvcParam->sSpatialLayers[iDidIdx].iMaxSpatialBitrate != UNSPECIFIED_BIT_RATE)) {
          pEncCtx->pFuncList->pfRc.pfWelsCheckSkipBasedMaxbr (pEncCtx, uiTimeStamp, iDidIdx);
          if (true == pEncCtx->pWelsSvcRc[iDidIdx].bSkipFlag) {
            bSkipMustFlag = true;
          }
        }
      }
      if (bSkipMustFlag) {
        break;
      }
    }

    if (bSkipMustFlag) {
      for (int32_t i = 0; i < iSpatialNum; i++) {
        int32_t iDidIdx = (pSpatialIndexMap + i)->iDid;
        pEncCtx->pWelsSvcRc[iDidIdx].uiLastTimeStamp = uiTimeStamp;
        pEncCtx->pWelsSvcRc[iDidIdx].bSkipFlag = false;
        pEncCtx->pWelsSvcRc[iDidIdx].iContinualSkipFrames++;
      }
      return true;
    }
  }
  return false;
}
void UpdateBufferWhenFrameSkipped (sWelsEncCtx* pEncCtx, int32_t iCurDid) {
  SWelsSvcRc* pWelsSvcRc = &pEncCtx->pWelsSvcRc[iCurDid];
  const int32_t kiOutputBits = pWelsSvcRc->iBitsPerFrame;
  const int32_t kiOutputMaxBits = pWelsSvcRc->iMaxBitsPerFrame;
  pWelsSvcRc->iBufferFullnessSkip = pWelsSvcRc->iBufferFullnessSkip - kiOutputBits;
  pWelsSvcRc->iBufferMaxBRFullness[EVEN_TIME_WINDOW] -= kiOutputMaxBits;
  pWelsSvcRc->iBufferMaxBRFullness[ODD_TIME_WINDOW] -= kiOutputMaxBits;
  WelsLog (& (pEncCtx->sLogCtx), WELS_LOG_DEBUG,
           "[Rc] iDid = %d,bits in buffer = %" PRId64 ", bits in Max bitrate buffer = %" PRId64,
           iCurDid, pWelsSvcRc->iBufferFullnessSkip, pWelsSvcRc->iBufferMaxBRFullness[EVEN_TIME_WINDOW]);

  pWelsSvcRc->iBufferFullnessSkip = WELS_MAX (pWelsSvcRc->iBufferFullnessSkip, 0);

  pWelsSvcRc->iRemainingBits +=  kiOutputBits;
  pWelsSvcRc->iSkipFrameNum++;
  pWelsSvcRc->iSkipFrameInVGop++;

  if ((pWelsSvcRc->iContinualSkipFrames % 3) == 0) {
    //output a warning when iContinualSkipFrames is large enough, which may indicate subjective quality problem
    //note that here iContinualSkipFrames must be >0, so the log output will be 3/6/....
    WelsLog (& (pEncCtx->sLogCtx), WELS_LOG_WARNING, "[Rc] iDid = %d,iContinualSkipFrames(%d) is large",
             iCurDid, pWelsSvcRc->iContinualSkipFrames);
  }
}
void UpdateMaxBrCheckWindowStatus (sWelsEncCtx* pEncCtx, int32_t iSpatialNum, const long long uiTimeStamp) {
  SSpatialPicIndex* pSpatialIndexMap = &pEncCtx->sSpatialIndexMap[0];
  if (pEncCtx->bCheckWindowStatusRefreshFlag) {
    pEncCtx->iCheckWindowCurrentTs = uiTimeStamp;
  } else {
    pEncCtx->iCheckWindowCurrentTs = pEncCtx->iCheckWindowStartTs = uiTimeStamp;
    pEncCtx->bCheckWindowStatusRefreshFlag = true;
    for (int32_t i = 0; i < iSpatialNum; i++) {
      int32_t iCurDid = (pSpatialIndexMap + i)->iDid;
      pEncCtx->pWelsSvcRc[iCurDid].iBufferFullnessSkip = 0;
      pEncCtx->pWelsSvcRc[iCurDid].iBufferMaxBRFullness[ODD_TIME_WINDOW] = 0;
      pEncCtx->pWelsSvcRc[iCurDid].iBufferMaxBRFullness[EVEN_TIME_WINDOW] = 0;
      pEncCtx->pWelsSvcRc[iCurDid].bNeedShiftWindowCheck[ODD_TIME_WINDOW] = false;
      pEncCtx->pWelsSvcRc[iCurDid].bNeedShiftWindowCheck[EVEN_TIME_WINDOW] = false;
    }

  }
  pEncCtx->iCheckWindowInterval = (int32_t) (pEncCtx->iCheckWindowCurrentTs - pEncCtx->iCheckWindowStartTs);
  if (pEncCtx->iCheckWindowInterval >= (TIME_CHECK_WINDOW >> 1) && !pEncCtx->bCheckWindowShiftResetFlag) {
    pEncCtx->bCheckWindowShiftResetFlag = true;
    for (int32_t i = 0; i < iSpatialNum; i++) {
      int32_t iCurDid = (pSpatialIndexMap + i)->iDid;
      if (pEncCtx->pWelsSvcRc[iCurDid].iBufferMaxBRFullness[ODD_TIME_WINDOW] > 0
          && pEncCtx->pWelsSvcRc[iCurDid].iBufferMaxBRFullness[ODD_TIME_WINDOW] !=
          pEncCtx->pWelsSvcRc[iCurDid].iBufferMaxBRFullness[0]) {
        pEncCtx->pWelsSvcRc[iCurDid].bNeedShiftWindowCheck[EVEN_TIME_WINDOW] = true;
      } else {
        pEncCtx->pWelsSvcRc[iCurDid].bNeedShiftWindowCheck[EVEN_TIME_WINDOW] = false;
      }
      pEncCtx->pWelsSvcRc[iCurDid].iBufferMaxBRFullness[ODD_TIME_WINDOW] = 0;
    }
  }
  pEncCtx->iCheckWindowIntervalShift = pEncCtx->iCheckWindowInterval >= (TIME_CHECK_WINDOW >> 1) ?
                                       pEncCtx->iCheckWindowInterval - (TIME_CHECK_WINDOW >> 1) : pEncCtx->iCheckWindowInterval + (TIME_CHECK_WINDOW >> 1);

  if (pEncCtx->iCheckWindowInterval >= TIME_CHECK_WINDOW || pEncCtx->iCheckWindowInterval == 0) {
    pEncCtx->iCheckWindowStartTs = pEncCtx->iCheckWindowCurrentTs;
    pEncCtx->iCheckWindowInterval = 0;
    pEncCtx->bCheckWindowShiftResetFlag = false;
    for (int32_t i = 0; i < iSpatialNum; i++) {
      int32_t iCurDid = (pSpatialIndexMap + i)->iDid;
      if (pEncCtx->pWelsSvcRc[iCurDid].iBufferMaxBRFullness[EVEN_TIME_WINDOW] > 0) {
        pEncCtx->pWelsSvcRc[iCurDid].bNeedShiftWindowCheck[ODD_TIME_WINDOW] = true;
      } else {
        pEncCtx->pWelsSvcRc[iCurDid].bNeedShiftWindowCheck[ODD_TIME_WINDOW] = false;
      }
      pEncCtx->pWelsSvcRc[iCurDid].iBufferMaxBRFullness[EVEN_TIME_WINDOW] = 0;
    }
  }
  return;
}

bool WelsRcPostFrameSkipping (sWelsEncCtx* pCtx, const int32_t iDid, const long long uiTimeStamp) {
  //TODO: put in the decision of rate-control
  return false;
}

void WelsRcPostFrameSkippedUpdate (sWelsEncCtx* pCtx, const int32_t iDid) {
  //TODO: do something to update buffers after post-skipping is done
  //let RC know post-skipping happened and adjust strategy accordingly
}

void RcVBufferCalculationPadding (sWelsEncCtx* pEncCtx) {
  SWelsSvcRc* pWelsSvcRc = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  const int32_t kiOutputBits = pWelsSvcRc->iBitsPerFrame;
  const int32_t kiBufferThreshold = WELS_DIV_ROUND (PADDING_THRESHOLD * (-pWelsSvcRc->iBufferSizePadding), INT_MULTIPLY);

  pWelsSvcRc->iBufferFullnessPadding += (pWelsSvcRc->iFrameDqBits - kiOutputBits);

  if (pWelsSvcRc->iBufferFullnessPadding < kiBufferThreshold) {
    pWelsSvcRc->iPaddingSize = -pWelsSvcRc->iBufferFullnessPadding;
    pWelsSvcRc->iPaddingSize >>= 3; // /8
    pWelsSvcRc->iBufferFullnessPadding = 0;
  } else
    pWelsSvcRc->iPaddingSize = 0;
}


void RcTraceFrameBits (sWelsEncCtx* pEncCtx, long long uiTimeStamp, int32_t iFrameSize) {
  SWelsSvcRc* pWelsSvcRc = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  SSpatialLayerInternal* pParamInternal = &pEncCtx->pSvcParam->sDependencyLayers[pEncCtx->uiDependencyId];
  if (pWelsSvcRc->iPredFrameBit != 0)
    pWelsSvcRc->iPredFrameBit = (int32_t) (LAST_FRAME_PREDICT_WEIGHT * pWelsSvcRc->iFrameDqBits +
                                           (1 - LAST_FRAME_PREDICT_WEIGHT) * pWelsSvcRc->iPredFrameBit);
  else
    pWelsSvcRc->iPredFrameBit = pWelsSvcRc->iFrameDqBits;

  WelsLog (& (pEncCtx->sLogCtx), WELS_LOG_DEBUG,
           "[Rc]Layer %d: Frame timestamp = %lld, Frame type = %d, encoding_qp = %d, average qp = %d, max qp = %d, min qp = %d, index = %d, "
           "iTid = %d, used = %d, bitsperframe = %d, target = %d, remainingbits = %d, skipbuffersize = %d",
           pEncCtx->uiDependencyId, uiTimeStamp, pEncCtx->eSliceType, pEncCtx->iGlobalQp, pWelsSvcRc->iAverageFrameQp,
           pWelsSvcRc->iMaxFrameQp,
           pWelsSvcRc->iMinFrameQp,
           pParamInternal->iFrameIndex, pEncCtx->uiTemporalId,
           ( pWelsSvcRc->iFrameDqBits > 0 ) ? pWelsSvcRc->iFrameDqBits : (iFrameSize<<3) ,
           pWelsSvcRc->iBitsPerFrame,
           pWelsSvcRc->iTargetBits, pWelsSvcRc->iRemainingBits, pWelsSvcRc->iBufferSizeSkip);

}

void RcUpdatePictureQpBits (sWelsEncCtx* pEncCtx, int32_t iCodedBits) {
  SSlice** ppSliceInLayer = pEncCtx->pCurDqLayer->ppSliceInLayer;
  SRCSlicing* pSOverRc    = &ppSliceInLayer[0]->sSlicingOverRc;
  SWelsSvcRc* pWelsSvcRc  = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  SSliceCtx* pCurSliceCtx = &pEncCtx->pCurDqLayer->sSliceEncCtx;
  int32_t iTotalQp = 0, iTotalMb = 0;
  int32_t i;

  if (pEncCtx->eSliceType == P_SLICE) {
    for (i = 0; i < pCurSliceCtx->iSliceNumInFrame; i++) {
      pSOverRc    = &ppSliceInLayer[i]->sSlicingOverRc;
      iTotalQp += pSOverRc->iTotalQpSlice;
      iTotalMb += pSOverRc->iTotalMbSlice;
    }
    if (iTotalMb > 0)
      pWelsSvcRc->iAverageFrameQp = WELS_DIV_ROUND (INT_MULTIPLY * iTotalQp, iTotalMb * INT_MULTIPLY);
    else
      pWelsSvcRc->iAverageFrameQp = pEncCtx->iGlobalQp;
  } else {
    pWelsSvcRc->iAverageFrameQp = pEncCtx->iGlobalQp;
  }
  pWelsSvcRc->iFrameDqBits = iCodedBits;
  pWelsSvcRc->iLastCalculatedQScale = pWelsSvcRc->iAverageFrameQp;
  pWelsSvcRc->pTemporalOverRc[pEncCtx->uiTemporalId].iGopBitsDq += pWelsSvcRc->iFrameDqBits;
}

void RcUpdateIntraComplexity (sWelsEncCtx* pEncCtx) {
  SWelsSvcRc* pWelsSvcRc = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  int32_t iAlpha = WELS_DIV_ROUND (INT_MULTIPLY, (1 + pWelsSvcRc->iIdrNum));
  if (iAlpha < (INT_MULTIPLY / 4)) iAlpha = INT_MULTIPLY / 4;
  int32_t iQStep = RcConvertQp2QStep (pWelsSvcRc->iAverageFrameQp);
  int64_t iIntraCmplx = iQStep * static_cast<int64_t> (pWelsSvcRc->iFrameDqBits);
  int64_t iFrameComplexity = pEncCtx->pVaa->sComplexityAnalysisParam.iFrameComplexity;
  if (pEncCtx->pSvcParam->iUsageType == SCREEN_CONTENT_REAL_TIME) {
    SVAAFrameInfoExt* pVaa = static_cast<SVAAFrameInfoExt*> (pEncCtx->pVaa);
    iFrameComplexity = pVaa->sComplexityScreenParam.iFrameComplexity;
  }
  if (pWelsSvcRc->iIdrNum == 0) {
    pWelsSvcRc->iIntraComplexity = iIntraCmplx;
    pWelsSvcRc->iIntraComplxMean = iFrameComplexity;
  } else {
    pWelsSvcRc->iIntraComplexity = WELS_DIV_ROUND64 (((LINEAR_MODEL_DECAY_FACTOR) * pWelsSvcRc->iIntraComplexity +
                                   (INT_MULTIPLY - LINEAR_MODEL_DECAY_FACTOR) *
                                   iIntraCmplx), INT_MULTIPLY);


    pWelsSvcRc->iIntraComplxMean = WELS_DIV_ROUND64 (((LINEAR_MODEL_DECAY_FACTOR) * static_cast<int64_t>
                                   (pWelsSvcRc->iIntraComplxMean)
                                   + (INT_MULTIPLY - LINEAR_MODEL_DECAY_FACTOR) * (iFrameComplexity)),
                                   INT_MULTIPLY);
  }

  pWelsSvcRc->iIntraMbCount = pWelsSvcRc->iNumberMbFrame;
  pWelsSvcRc->iIdrNum++;
  if (pWelsSvcRc->iIdrNum > 255)
    pWelsSvcRc->iIdrNum = 255;
  WelsLog (& (pEncCtx->sLogCtx), WELS_LOG_DEBUG,
           "RcUpdateIntraComplexity iFrameDqBits = %d,iQStep= %d,iIntraCmplx = %" PRId64,
           pWelsSvcRc->iFrameDqBits, pWelsSvcRc->iQStep, pWelsSvcRc->iIntraComplexity);

}

void RcUpdateFrameComplexity (sWelsEncCtx* pEncCtx) {
  SWelsSvcRc* pWelsSvcRc        = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  const int32_t kiTl            = pEncCtx->uiTemporalId;
  SRCTemporal* pTOverRc         = &pWelsSvcRc->pTemporalOverRc[kiTl];

  int64_t iFrameComplexity = pEncCtx->pVaa->sComplexityAnalysisParam.iFrameComplexity;
  if (pEncCtx->pSvcParam->iUsageType == SCREEN_CONTENT_REAL_TIME) {
    SVAAFrameInfoExt* pVaa = static_cast<SVAAFrameInfoExt*> (pEncCtx->pVaa);
    iFrameComplexity = pVaa->sComplexityScreenParam.iFrameComplexity;
  }
  int32_t iQStep = RcConvertQp2QStep (pWelsSvcRc->iAverageFrameQp);
  int32_t iAlpha = WELS_DIV_ROUND (INT_MULTIPLY, (1 + pTOverRc->iPFrameNum));
  if (iAlpha < SMOOTH_FACTOR_MIN_VALUE)
    iAlpha = SMOOTH_FACTOR_MIN_VALUE;
  if (0 == pTOverRc->iPFrameNum) {
    pTOverRc->iLinearCmplx = ((int64_t)pWelsSvcRc->iFrameDqBits) * iQStep;
    pTOverRc->iFrameCmplxMean = (int32_t)iFrameComplexity;
  } else {
    pTOverRc->iLinearCmplx = WELS_DIV_ROUND64 (((LINEAR_MODEL_DECAY_FACTOR) * (int64_t)pTOverRc->iLinearCmplx
                             + (INT_MULTIPLY - LINEAR_MODEL_DECAY_FACTOR) * ((int64_t)pWelsSvcRc->iFrameDqBits * iQStep)),
                             INT_MULTIPLY);
    pTOverRc->iFrameCmplxMean = WELS_DIV_ROUND64 (((LINEAR_MODEL_DECAY_FACTOR) * static_cast<int64_t>
                                (pTOverRc->iFrameCmplxMean)
                                + (INT_MULTIPLY - LINEAR_MODEL_DECAY_FACTOR) * iFrameComplexity),
                                INT_MULTIPLY);
  }


  pTOverRc->iPFrameNum++;
  if (pTOverRc->iPFrameNum > 255)
    pTOverRc->iPFrameNum = 255;
  WelsLog (& (pEncCtx->sLogCtx), WELS_LOG_DEBUG,
           "RcUpdateFrameComplexity iFrameDqBits = %d,iQStep= %d,pWelsSvcRc->iQStep= %d,pTOverRc->iLinearCmplx = %" PRId64,
           pWelsSvcRc->iFrameDqBits,
           iQStep, pWelsSvcRc->iQStep, pTOverRc->iLinearCmplx);
  WelsLog (& (pEncCtx->sLogCtx), WELS_LOG_DEBUG, "iFrameCmplxMean = %" PRId64 ",iFrameComplexity = %" PRId64,
           pTOverRc->iFrameCmplxMean, iFrameComplexity);
}

int32_t RcCalculateCascadingQp (struct TagWelsEncCtx* pEncCtx, int32_t iQp) {
  int32_t iTemporalQp = 0;
  if (pEncCtx->pSvcParam->iDecompStages) {
    if (pEncCtx->uiTemporalId == 0)
      iTemporalQp = iQp - 3 - (pEncCtx->pSvcParam->iDecompStages - 1);
    else
      iTemporalQp = iQp - (pEncCtx->pSvcParam->iDecompStages - pEncCtx->uiTemporalId);
    iTemporalQp = WELS_CLIP3 (iTemporalQp, 1, 51);
  } else
    iTemporalQp = iQp;
  return iTemporalQp;
}

void  WelsRcPictureInitGom (sWelsEncCtx* pEncCtx, long long uiTimeStamp) {
  SWelsSvcRc* pWelsSvcRc           = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  const int32_t kiSliceNum         = pEncCtx->pCurDqLayer->iMaxSliceNum;
  pWelsSvcRc->iContinualSkipFrames = 0;

  if (pEncCtx->eSliceType == I_SLICE) {
    if (0 == pWelsSvcRc->iIdrNum) { //iIdrNum == 0 means encoder has been initialed
      RcInitRefreshParameter (pEncCtx);
    }
  }
  if (RcJudgeBitrateFpsUpdate (pEncCtx)) {
    RcUpdateBitrateFps (pEncCtx);
  }
  if (pEncCtx->uiTemporalId == 0) {
    RcUpdateTemporalZero (pEncCtx);
  }
  if (pEncCtx->pSvcParam->iRCMode == RC_TIMESTAMP_MODE) {
    RcDecideTargetBitsTimestamp (pEncCtx);
    pWelsSvcRc->uiLastTimeStamp = uiTimeStamp;
  } else {
    RcDecideTargetBits (pEncCtx);
  }
  //turn off GOM QP when slicenum is larger 1
  if ((kiSliceNum > 1) || ((pEncCtx->pSvcParam->iRCMode == RC_BITRATE_MODE)
                           && (pEncCtx->eSliceType == I_SLICE))) {
    pWelsSvcRc->bEnableGomQp = false;
  } else
    pWelsSvcRc->bEnableGomQp = true;

  //decide globe_qp
  if (pEncCtx->eSliceType == I_SLICE) {
    RcCalculateIdrQp (pEncCtx);
  } else {
    RcCalculatePictureQp (pEncCtx);
  }
  RcInitSliceInformation (pEncCtx);
  RcInitGomParameters (pEncCtx);
}

void  WelsRcPictureInfoUpdateGom (sWelsEncCtx* pEncCtx, int32_t iLayerSize) {
  SWelsSvcRc* pWelsSvcRc = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  int32_t iCodedBits = (iLayerSize << 3);

  RcUpdatePictureQpBits (pEncCtx, iCodedBits);

  if (pEncCtx->eSliceType == P_SLICE) {
    RcUpdateFrameComplexity (pEncCtx);
  } else {
    RcUpdateIntraComplexity (pEncCtx);
  }
  pWelsSvcRc->iRemainingBits -= pWelsSvcRc->iFrameDqBits;

  if (pEncCtx->pSvcParam->bEnableFrameSkip /*&&
      pEncCtx->uiDependencyId == pEncCtx->pSvcParam->iSpatialLayerNum - 1*/) {
    RcVBufferCalculationSkip (pEncCtx);
  }

  if (pEncCtx->pSvcParam->iPaddingFlag)
    RcVBufferCalculationPadding (pEncCtx);
  pWelsSvcRc->iFrameCodedInVGop++;
}

void WelsRcMbInitGom (sWelsEncCtx* pEncCtx, SMB* pCurMb, SSlice* pSlice) {
  SWelsSvcRc* pWelsSvcRc        = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  SRCSlicing* pSOverRc          = &pSlice->sSlicingOverRc;
  SDqLayer* pCurLayer           = pEncCtx->pCurDqLayer;
  const uint8_t kuiChromaQpIndexOffset = pCurLayer->sLayerInfo.pPpsP->uiChromaQpIndexOffset;

  pSOverRc->iBsPosSlice = pEncCtx->pFuncList->pfGetBsPosition (pSlice);
  if (pWelsSvcRc->bEnableGomQp) {
    //calculate gom qp and target bits at the beginning of gom
    if (0 == (pCurMb->iMbXY % pWelsSvcRc->iNumberMbGom)) {
      if (pCurMb->iMbXY != pSOverRc->iStartMbSlice) {
        pSOverRc->iComplexityIndexSlice++;
        RcCalculateGomQp (pEncCtx, pSlice, pCurMb);
      }
      RcGomTargetBits (pEncCtx, pSlice);
    }

    RcCalculateMbQp (pEncCtx, pSlice, pCurMb);
  } else {
    pCurMb->uiLumaQp   = pEncCtx->iGlobalQp;
    pCurMb->uiChromaQp = g_kuiChromaQpTable[CLIP3_QP_0_51 (pCurMb->uiLumaQp + kuiChromaQpIndexOffset)];
  }

}

void WelsRcMbInfoUpdateGom (sWelsEncCtx* pEncCtx, SMB* pCurMb, int32_t iCostLuma, SSlice* pSlice) {
  SWelsSvcRc* pWelsSvcRc            = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  SRCSlicing* pSOverRc              = &pSlice->sSlicingOverRc;
  const int32_t kiComplexityIndex   = pSOverRc->iComplexityIndexSlice;

  int32_t iCurMbBits = pEncCtx->pFuncList->pfGetBsPosition (pSlice) - pSOverRc->iBsPosSlice;
  pSOverRc->iFrameBitsSlice += iCurMbBits;
  pSOverRc->iGomBitsSlice += iCurMbBits;

  pWelsSvcRc->pGomCost[kiComplexityIndex] += iCostLuma;
  if (iCurMbBits > 0) {
    pSOverRc->iTotalQpSlice += pCurMb->uiLumaQp;
    pSOverRc->iTotalMbSlice++;
  }
}

void  WelsRcPictureInitDisable (sWelsEncCtx* pEncCtx, long long uiTimeStamp) {
  SWelsSvcRc* pWelsSvcRc = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  SSpatialLayerConfig* pDLayerParam = &pEncCtx->pSvcParam->sSpatialLayers[pEncCtx->uiDependencyId];
  const int32_t kiQp = pDLayerParam->iDLayerQp;

  pEncCtx->iGlobalQp = RcCalculateCascadingQp (pEncCtx, kiQp);

  if (pEncCtx->pSvcParam->bEnableAdaptiveQuant && (pEncCtx->eSliceType == P_SLICE)) {
    pEncCtx->iGlobalQp = WELS_CLIP3 ((pEncCtx->iGlobalQp * INT_MULTIPLY -
                                      pEncCtx->pVaa->sAdaptiveQuantParam.iAverMotionTextureIndexToDeltaQp) / INT_MULTIPLY, pWelsSvcRc->iMinQp,
                                     pWelsSvcRc->iMaxQp);
  } else {
    pEncCtx->iGlobalQp = WELS_CLIP3 (pEncCtx->iGlobalQp, 0, 51);
  }

  pWelsSvcRc->iAverageFrameQp = pEncCtx->iGlobalQp;
}

void  WelsRcPictureInfoUpdateDisable (sWelsEncCtx* pEncCtx, int32_t iLayerSize) {
}

void  WelsRcMbInitDisable (sWelsEncCtx* pEncCtx, SMB* pCurMb, SSlice* pSlice) {
  int32_t iLumaQp = pEncCtx->iGlobalQp;
  SWelsSvcRc* pWelsSvcRc = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  SDqLayer* pCurLayer = pEncCtx->pCurDqLayer;

  const uint8_t kuiChromaQpIndexOffset = pCurLayer->sLayerInfo.pPpsP->uiChromaQpIndexOffset;


  if (pEncCtx->pSvcParam->bEnableAdaptiveQuant && (pEncCtx->eSliceType == P_SLICE)) {
    iLumaQp   = (int8_t)WELS_CLIP3 (iLumaQp +
                                    pEncCtx->pVaa->sAdaptiveQuantParam.pMotionTextureIndexToDeltaQp[pCurMb->iMbXY], pWelsSvcRc->iMinQp, pWelsSvcRc->iMaxQp);
  } else {
    iLumaQp = WELS_CLIP3 (iLumaQp, 0, 51);
  }
  pCurMb->uiChromaQp = g_kuiChromaQpTable[CLIP3_QP_0_51 (iLumaQp + kuiChromaQpIndexOffset)];
  pCurMb->uiLumaQp = iLumaQp;
}

void  WelsRcMbInfoUpdateDisable (sWelsEncCtx* pEncCtx, SMB* pCurMb, int32_t iCostLuma, SSlice* pSlice) {
}

void WelRcPictureInitBufferBasedQp (sWelsEncCtx* pEncCtx, long long uiTimeStamp) {

  SVAAFrameInfo* pVaa = static_cast<SVAAFrameInfo*> (pEncCtx->pVaa);
  SWelsSvcRc* pWelsSvcRc =  &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];

  int32_t iMinQp = pEncCtx->pSvcParam->iMinQp;
  if (pVaa->eSceneChangeIdc == LARGE_CHANGED_SCENE)
    iMinQp += 2;
  else if (pVaa->eSceneChangeIdc == MEDIUM_CHANGED_SCENE)
    iMinQp += 1;
  if (pEncCtx->bDeliveryFlag)
    pEncCtx->iGlobalQp -= 1;
  else
    pEncCtx->iGlobalQp += 2;
  pEncCtx->iGlobalQp = WELS_CLIP3 (pEncCtx->iGlobalQp, iMinQp, pWelsSvcRc->iMaxQp);
  pWelsSvcRc->iAverageFrameQp = pWelsSvcRc->iMaxFrameQp = pWelsSvcRc->iMinFrameQp = pEncCtx->iGlobalQp;
}
void WelRcPictureInitScc (sWelsEncCtx* pEncCtx, long long uiTimeStamp) {
  SWelsSvcRc* pWelsSvcRc =  &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  SVAAFrameInfoExt* pVaa = static_cast<SVAAFrameInfoExt*> (pEncCtx->pVaa);
  SSpatialLayerConfig* pDLayerConfig   = &pEncCtx->pSvcParam->sSpatialLayers[pEncCtx->uiDependencyId];
  SSpatialLayerInternal* pDLayerParamInternal       = &pEncCtx->pSvcParam->sDependencyLayers[pEncCtx->uiDependencyId];
  int64_t iFrameCplx = pVaa->sComplexityScreenParam.iFrameComplexity;
  int32_t iBitRate = pDLayerConfig->iSpatialBitrate;// pEncCtx->pSvcParam->target_bitrate;

  int32_t iBaseQp = pWelsSvcRc->iBaseQp;
  pEncCtx->iGlobalQp = iBaseQp;
  int32_t iDeltaQp = 0;
  if (pEncCtx->eSliceType == I_SLICE) {
    int64_t iTargetBits = iBitRate * 2 - pWelsSvcRc->iBufferFullnessSkip;
    iTargetBits = WELS_MAX (1, iTargetBits);
    int32_t iQstep = WELS_DIV_ROUND (iFrameCplx * pWelsSvcRc->iCost2BitsIntra, iTargetBits);
    int32_t iQp = RcConvertQStep2Qp (iQstep);

    pEncCtx->iGlobalQp = WELS_CLIP3 (iQp, pWelsSvcRc->iMinQp, pWelsSvcRc->iMaxQp);
  } else {
    int64_t iTargetBits = WELS_ROUND (((float)iBitRate / pDLayerParamInternal->fOutputFrameRate)); //iBitRate / 10;
    int32_t iQstep = WELS_DIV_ROUND (iFrameCplx * pWelsSvcRc->iAvgCost2Bits, iTargetBits);
    int32_t iQp = RcConvertQStep2Qp (iQstep);
    iDeltaQp = iQp - iBaseQp;
    if (pWelsSvcRc->iBufferFullnessSkip > iBitRate) {
      if (iDeltaQp > 0) {
        ++iBaseQp;
      }
    } else if (pWelsSvcRc->iBufferFullnessSkip == 0) {
      if (iDeltaQp < 0) {
        --iBaseQp;
      }
    }
    if (iDeltaQp >= 6) {
      iBaseQp += 3;
    } else if ((iDeltaQp <= -6)) {
      --iBaseQp;
    }
    iBaseQp = WELS_CLIP3 (iBaseQp, pWelsSvcRc->iMinQp, pWelsSvcRc->iMinQp);

    pEncCtx->iGlobalQp = iBaseQp;


    if (iDeltaQp < -6) {
      pEncCtx->iGlobalQp = WELS_CLIP3 (pWelsSvcRc->iBaseQp - 6, pWelsSvcRc->iMinQp, pWelsSvcRc->iMaxQp);
    }

    if (iDeltaQp > 5) {
      if (LARGE_CHANGED_SCENE == pEncCtx->pVaa->eSceneChangeIdc || pWelsSvcRc->iBufferFullnessSkip > 2 * iBitRate
          || iDeltaQp > 10) {
        pEncCtx->iGlobalQp = WELS_CLIP3 (pWelsSvcRc->iBaseQp + iDeltaQp, pWelsSvcRc->iMinQp, pWelsSvcRc->iMaxQp);
      } else if (MEDIUM_CHANGED_SCENE == pEncCtx->pVaa->eSceneChangeIdc || pWelsSvcRc->iBufferFullnessSkip > iBitRate) {
        pEncCtx->iGlobalQp = WELS_CLIP3 (pWelsSvcRc->iBaseQp + 5, pWelsSvcRc->iMinQp, pWelsSvcRc->iMaxQp);
      }
    }
    pWelsSvcRc->iBaseQp = iBaseQp;
  }
  pWelsSvcRc->iAverageFrameQp = pEncCtx->iGlobalQp;
  WelsLog (& (pEncCtx->sLogCtx), WELS_LOG_DEBUG, "WelRcPictureInitScc iLumaQp = %d\n", pEncCtx->iGlobalQp);
  pWelsSvcRc->uiLastTimeStamp = uiTimeStamp;

}
void WelsRcDropFrameUpdate (sWelsEncCtx* pEncCtx, uint32_t iDropSize) {
  SWelsSvcRc* pWelsSvcRc = &pEncCtx->pWelsSvcRc[0];

  pWelsSvcRc->iBufferFullnessSkip -= (int32_t)iDropSize;
  pWelsSvcRc->iBufferFullnessSkip = WELS_MAX (0, pWelsSvcRc->iBufferFullnessSkip);
  WelsLog (& (pEncCtx->sLogCtx), WELS_LOG_DEBUG, "[WelsRcDropFrameUpdate:\tdrop:%d\t%" PRId64 "\n", iDropSize,
           pWelsSvcRc->iBufferFullnessSkip);
}

void WelsRcPictureInfoUpdateScc (sWelsEncCtx* pEncCtx, int32_t iNalSize) {
  SWelsSvcRc* pWelsSvcRc = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  int32_t iFrameBits = (iNalSize << 3);
  pWelsSvcRc->iBufferFullnessSkip += iFrameBits;

  SVAAFrameInfoExt* pVaa = static_cast<SVAAFrameInfoExt*> (pEncCtx->pVaa);

  int32_t iQstep = RcConvertQp2QStep (pEncCtx->iGlobalQp);
  int64_t iCost2Bits = WELS_DIV_ROUND64 ((((int64_t)iFrameBits * iQstep)), pVaa->sComplexityScreenParam.iFrameComplexity);

  if (pEncCtx->eSliceType == P_SLICE) {
    pWelsSvcRc->iAvgCost2Bits = WELS_DIV_ROUND64 ((95 * pWelsSvcRc->iAvgCost2Bits + 5 * iCost2Bits), INT_MULTIPLY);
  } else {
    pWelsSvcRc->iCost2BitsIntra = WELS_DIV_ROUND64 ((90 * pWelsSvcRc->iCost2BitsIntra + 10 * iCost2Bits), INT_MULTIPLY);
  }
}


void WelsRcMbInitScc (sWelsEncCtx* pEncCtx, SMB* pCurMb, SSlice* pSlice) {
  /* Get delta iQp of this MB */
  pCurMb->uiLumaQp = pEncCtx->iGlobalQp;
  pCurMb->uiChromaQp = g_kuiChromaQpTable[WELS_CLIP3 (pCurMb->uiLumaQp + pEncCtx->pPps->uiChromaQpIndexOffset, 0, 51)];
}

void WelsRcFrameDelayJudgeTimeStamp (sWelsEncCtx* pEncCtx, long long uiTimeStamp, int32_t iDidIdx) {
  SWelsSvcRc* pWelsSvcRc = &pEncCtx->pWelsSvcRc[iDidIdx];
  SSpatialLayerConfig* pDLayerConfig   = &pEncCtx->pSvcParam->sSpatialLayers[iDidIdx];

  int32_t iBitRate = pDLayerConfig->iSpatialBitrate;
  int32_t iEncTimeInv = (pWelsSvcRc->uiLastTimeStamp == 0) ? 0 : (int32_t) (uiTimeStamp - pWelsSvcRc->uiLastTimeStamp);
  if ((iEncTimeInv < 0) || (iEncTimeInv > 1000)) {
    iEncTimeInv = (int32_t) (1000.0 / pDLayerConfig->fFrameRate);
    pWelsSvcRc->uiLastTimeStamp = uiTimeStamp - iEncTimeInv;
  }
  int32_t iSentBits  = (int32_t) ((double)iBitRate * iEncTimeInv * (1.0E-3) + 0.5);
  iSentBits = WELS_MAX (iSentBits, 0);

  //When bitrate is changed, pBuffer size should be updated
  pWelsSvcRc->iBufferSizeSkip = WELS_DIV_ROUND (pDLayerConfig->iSpatialBitrate * pWelsSvcRc->iSkipBufferRatio,
                                INT_MULTIPLY);
  pWelsSvcRc->iBufferSizePadding = WELS_DIV_ROUND (pDLayerConfig->iSpatialBitrate * PADDING_BUFFER_RATIO, INT_MULTIPLY);

  pWelsSvcRc->iBufferFullnessSkip -= iSentBits;
  pWelsSvcRc->iBufferFullnessSkip = WELS_MAX ((-1) * (pDLayerConfig->iSpatialBitrate / 4),
                                    pWelsSvcRc->iBufferFullnessSkip);

  if (pEncCtx->pSvcParam->bEnableFrameSkip) {
    pWelsSvcRc->bSkipFlag = true;
    if (pWelsSvcRc->iBufferFullnessSkip < pWelsSvcRc->iBufferSizeSkip) {
      pWelsSvcRc->bSkipFlag = false;
    }
    if (pWelsSvcRc->bSkipFlag) {
      pWelsSvcRc->iSkipFrameNum++;
      pWelsSvcRc->uiLastTimeStamp = uiTimeStamp;
    }
  }
  WelsLog (& (pEncCtx->sLogCtx), WELS_LOG_DEBUG,
           "WelsRcFrameDelayJudgeTimeStamp iDidIdx = %d,iSkipFrameNum = %d,buffer = %" PRId64
           ",threadhold = %d,bitrate = %d,iSentBits = %d,lasttimestamp = %lld,timestamp=%lld", iDidIdx,
           pWelsSvcRc->iSkipFrameNum, pWelsSvcRc->iBufferFullnessSkip, pWelsSvcRc->iBufferSizeSkip, iBitRate, iSentBits,
           pWelsSvcRc->uiLastTimeStamp, uiTimeStamp);
}

void  WelsRcPictureInfoUpdateGomTimeStamp (sWelsEncCtx* pEncCtx, int32_t iLayerSize) {
  SWelsSvcRc* pWelsSvcRc = &pEncCtx->pWelsSvcRc[pEncCtx->uiDependencyId];
  int32_t iCodedBits = (iLayerSize << 3);

  RcUpdatePictureQpBits (pEncCtx, iCodedBits);
  if (pEncCtx->eSliceType == P_SLICE) {
    RcUpdateFrameComplexity (pEncCtx);
  } else {
    RcUpdateIntraComplexity (pEncCtx);
  }

  pWelsSvcRc->iRemainingBits -= pWelsSvcRc->iFrameDqBits;
  //condition 1: whole pBuffer fullness
  pWelsSvcRc->iBufferFullnessSkip += pWelsSvcRc->iFrameDqBits;

  if (pEncCtx->pSvcParam->iPaddingFlag)
    RcVBufferCalculationPadding (pEncCtx);
  pWelsSvcRc->iFrameCodedInVGop++;
}

void  WelsRcInitFuncPointers (sWelsEncCtx* pEncCtx, RC_MODES iRcMode) {
  SWelsRcFunc*   pRcf = &pEncCtx->pFuncList->pfRc;
  switch (iRcMode) {
  case RC_OFF_MODE:
    pRcf->pfWelsRcPictureInit = WelsRcPictureInitDisable;
    pRcf->pfWelsRcPicDelayJudge = NULL;
    pRcf->pfWelsRcPictureInfoUpdate = WelsRcPictureInfoUpdateDisable;
    pRcf->pfWelsRcMbInit = WelsRcMbInitDisable;
    pRcf->pfWelsRcMbInfoUpdate = WelsRcMbInfoUpdateDisable;
    pRcf->pfWelsCheckSkipBasedMaxbr = NULL;
    pRcf->pfWelsUpdateBufferWhenSkip = NULL;
    pRcf->pfWelsUpdateMaxBrWindowStatus = NULL;
    pRcf->pfWelsRcPostFrameSkipping = NULL;
    break;
  case RC_BUFFERBASED_MODE:
    pRcf->pfWelsRcPictureInit = WelRcPictureInitBufferBasedQp;
    pRcf->pfWelsRcPicDelayJudge = NULL;
    pRcf->pfWelsRcPictureInfoUpdate = WelsRcPictureInfoUpdateDisable;
    pRcf->pfWelsRcMbInit = WelsRcMbInitDisable;
    pRcf->pfWelsRcMbInfoUpdate = WelsRcMbInfoUpdateDisable;
    pRcf->pfWelsCheckSkipBasedMaxbr = NULL;
    pRcf->pfWelsUpdateBufferWhenSkip = NULL;
    pRcf->pfWelsUpdateMaxBrWindowStatus = NULL;
    pRcf->pfWelsRcPostFrameSkipping = NULL;
    break;
  case RC_BITRATE_MODE:
    pRcf->pfWelsRcPictureInit = WelsRcPictureInitGom;
    pRcf->pfWelsRcPicDelayJudge = NULL;
    pRcf->pfWelsRcPictureInfoUpdate = WelsRcPictureInfoUpdateGom;
    pRcf->pfWelsRcMbInit = WelsRcMbInitGom;
    pRcf->pfWelsRcMbInfoUpdate = WelsRcMbInfoUpdateGom;
    pRcf->pfWelsCheckSkipBasedMaxbr = CheckFrameSkipBasedMaxbr;
    pRcf->pfWelsUpdateBufferWhenSkip = UpdateBufferWhenFrameSkipped;
    pRcf->pfWelsUpdateMaxBrWindowStatus = UpdateMaxBrCheckWindowStatus;
    pRcf->pfWelsRcPostFrameSkipping = WelsRcPostFrameSkipping;
    break;
  case RC_BITRATE_MODE_POST_SKIP:
    pRcf->pfWelsRcPictureInit = WelsRcPictureInitGom;
    pRcf->pfWelsRcPicDelayJudge = NULL;
    pRcf->pfWelsRcPictureInfoUpdate = WelsRcPictureInfoUpdateGom;
    pRcf->pfWelsRcMbInit = WelsRcMbInitGom;
    pRcf->pfWelsRcMbInfoUpdate = WelsRcMbInfoUpdateGom;
    pRcf->pfWelsCheckSkipBasedMaxbr = CheckFrameSkipBasedMaxbr;
    pRcf->pfWelsUpdateBufferWhenSkip = UpdateBufferWhenFrameSkipped;
    pRcf->pfWelsUpdateMaxBrWindowStatus = UpdateMaxBrCheckWindowStatus;
    pRcf->pfWelsRcPostFrameSkipping = WelsRcPostFrameSkipping;
    break;
  case RC_TIMESTAMP_MODE:

    pRcf->pfWelsRcPictureInit = WelsRcPictureInitGom;
    pRcf->pfWelsRcPictureInfoUpdate = WelsRcPictureInfoUpdateGomTimeStamp;
    pRcf->pfWelsRcMbInit = WelsRcMbInitGom;
    pRcf->pfWelsRcMbInfoUpdate = WelsRcMbInfoUpdateGom;

    pRcf->pfWelsRcPicDelayJudge = WelsRcFrameDelayJudgeTimeStamp;
    pRcf->pfWelsCheckSkipBasedMaxbr = NULL;
    pRcf->pfWelsUpdateBufferWhenSkip = NULL;
    pRcf->pfWelsUpdateMaxBrWindowStatus = NULL;
    pRcf->pfWelsRcPostFrameSkipping = NULL;
    break;
  case RC_QUALITY_MODE:
  default:
    pRcf->pfWelsRcPictureInit = WelsRcPictureInitGom;
    pRcf->pfWelsRcPicDelayJudge = NULL;
    pRcf->pfWelsRcPictureInfoUpdate = WelsRcPictureInfoUpdateGom;
    pRcf->pfWelsRcMbInit = WelsRcMbInitGom;
    pRcf->pfWelsRcMbInfoUpdate = WelsRcMbInfoUpdateGom;
    pRcf->pfWelsCheckSkipBasedMaxbr = CheckFrameSkipBasedMaxbr;
    pRcf->pfWelsUpdateBufferWhenSkip = UpdateBufferWhenFrameSkipped;
    pRcf->pfWelsUpdateMaxBrWindowStatus = UpdateMaxBrCheckWindowStatus;
    pRcf->pfWelsRcPostFrameSkipping = NULL;
    break;
  }
}

void  WelsRcInitModule (sWelsEncCtx* pEncCtx, RC_MODES iRcMode) {
  WelsRcInitFuncPointers (pEncCtx, iRcMode);
  RcInitSequenceParameter (pEncCtx);
}

void  WelsRcFreeMemory (sWelsEncCtx* pEncCtx) {
  SWelsSvcRc* pWelsSvcRc = NULL;
  int32_t i = 0;
  for (i = 0; i < pEncCtx->pSvcParam->iSpatialLayerNum; i++) {
    pWelsSvcRc  = &pEncCtx->pWelsSvcRc[i];
    RcFreeLayerMemory (pWelsSvcRc, pEncCtx->pMemAlign);
  }
}

long long GetTimestampForRc (const long long uiTimeStamp, const long long uiLastTimeStamp, const float fFrameRate) {
  if ((uiLastTimeStamp >= uiTimeStamp) || ((uiTimeStamp == 0) && (uiLastTimeStamp != -1))) {
    return (uiLastTimeStamp + (int32_t) (1000.0 / fFrameRate));
  }
  return uiTimeStamp;
}

}//end of namespace
