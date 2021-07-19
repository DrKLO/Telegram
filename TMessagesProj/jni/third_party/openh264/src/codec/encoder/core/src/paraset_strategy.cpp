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

#include "au_set.h"
#include "encoder_context.h"
#include "paraset_strategy.h"

namespace WelsEnc {


IWelsParametersetStrategy*   IWelsParametersetStrategy::CreateParametersetStrategy (
  EParameterSetStrategy eSpsPpsIdStrategy, bool bSimulcastAVC,
  const int32_t kiSpatialLayerNum) {

  IWelsParametersetStrategy* pParametersetStrategy = NULL;
  switch (eSpsPpsIdStrategy) {
  case INCREASING_ID:
    pParametersetStrategy = WELS_NEW_OP (CWelsParametersetIdIncreasing (bSimulcastAVC, kiSpatialLayerNum),
                                         CWelsParametersetIdIncreasing);
    WELS_VERIFY_RETURN_IF (NULL, NULL == pParametersetStrategy)
    break;
  case SPS_LISTING:
    pParametersetStrategy = WELS_NEW_OP (CWelsParametersetSpsListing (bSimulcastAVC, kiSpatialLayerNum),
                                         CWelsParametersetSpsListing);
    WELS_VERIFY_RETURN_IF (NULL, NULL == pParametersetStrategy)
    break;
  case SPS_LISTING_AND_PPS_INCREASING:
    pParametersetStrategy = WELS_NEW_OP (CWelsParametersetSpsListingPpsIncreasing (bSimulcastAVC, kiSpatialLayerNum),
                                         CWelsParametersetSpsListingPpsIncreasing);
    WELS_VERIFY_RETURN_IF (NULL, NULL == pParametersetStrategy)
    break;
  case SPS_PPS_LISTING:
    pParametersetStrategy = WELS_NEW_OP (CWelsParametersetSpsPpsListing (bSimulcastAVC, kiSpatialLayerNum),
                                         CWelsParametersetSpsPpsListing);
    WELS_VERIFY_RETURN_IF (NULL, NULL == pParametersetStrategy)
    break;
  case CONSTANT_ID:
  default:
    pParametersetStrategy = WELS_NEW_OP (CWelsParametersetIdConstant (bSimulcastAVC, kiSpatialLayerNum),
                                         CWelsParametersetIdConstant);
    WELS_VERIFY_RETURN_IF (NULL, NULL == pParametersetStrategy)
    break;
  }

  return pParametersetStrategy;
}


static int32_t WelsGenerateNewSps (sWelsEncCtx* pCtx, const bool kbUseSubsetSps, const int32_t iDlayerIndex,
                                   const int32_t iDlayerCount, const int32_t kiSpsId,
                                   SWelsSPS*& pSps, SSubsetSps*& pSubsetSps, bool bSVCBaselayer) {
  int32_t iRet = 0;

  if (!kbUseSubsetSps) {
    pSps        = & (pCtx->pSpsArray[kiSpsId]);
  } else {
    pSubsetSps  = & (pCtx->pSubsetArray[kiSpsId]);
    pSps        = &pSubsetSps->pSps;
  }

  SWelsSvcCodingParam* pParam = pCtx->pSvcParam;
  SSpatialLayerConfig* pDlayerParam = &pParam->sSpatialLayers[iDlayerIndex];
  // Need port pSps/pPps initialization due to spatial scalability changed
  if (!kbUseSubsetSps) {
    iRet = WelsInitSps (pSps, pDlayerParam, &pParam->sDependencyLayers[iDlayerIndex], pParam->uiIntraPeriod,
                        pParam->iMaxNumRefFrame,
                        kiSpsId, pParam->bEnableFrameCroppingFlag, pParam->iRCMode != RC_OFF_MODE, iDlayerCount,
                        bSVCBaselayer);
  } else {
    iRet = WelsInitSubsetSps (pSubsetSps, pDlayerParam, &pParam->sDependencyLayers[iDlayerIndex], pParam->uiIntraPeriod,
                              pParam->iMaxNumRefFrame,
                              kiSpsId, pParam->bEnableFrameCroppingFlag, pParam->iRCMode != RC_OFF_MODE, iDlayerCount);
  }
  return iRet;
}

static bool CheckMatchedSps (SWelsSPS* const pSps1, SWelsSPS* const pSps2) {

  if ((pSps1->iMbWidth != pSps2->iMbWidth)
      || (pSps1->iMbHeight != pSps2->iMbHeight)) {
    return false;
  }

  if ((pSps1->uiLog2MaxFrameNum != pSps2->uiLog2MaxFrameNum)
      || (pSps1->iLog2MaxPocLsb != pSps2->iLog2MaxPocLsb)) {
    return false;
  }

  if (pSps1->iNumRefFrames != pSps2->iNumRefFrames) {
    return false;
  }

  if ((pSps1->bFrameCroppingFlag != pSps2->bFrameCroppingFlag)
      || (pSps1->sFrameCrop.iCropLeft != pSps2->sFrameCrop.iCropLeft)
      || (pSps1->sFrameCrop.iCropRight != pSps2->sFrameCrop.iCropRight)
      || (pSps1->sFrameCrop.iCropTop != pSps2->sFrameCrop.iCropTop)
      || (pSps1->sFrameCrop.iCropBottom != pSps2->sFrameCrop.iCropBottom)
     ) {
    return false;
  }

  if ((pSps1->uiProfileIdc != pSps2->uiProfileIdc)
      || (pSps1->bConstraintSet0Flag != pSps2->bConstraintSet0Flag)
      || (pSps1->bConstraintSet1Flag != pSps2->bConstraintSet1Flag)
      || (pSps1->bConstraintSet2Flag != pSps2->bConstraintSet2Flag)
      || (pSps1->bConstraintSet3Flag != pSps2->bConstraintSet3Flag)
      || (pSps1->iLevelIdc != pSps2->iLevelIdc)) {
    return false;
  }

  return true;
}

static bool CheckMatchedSubsetSps (SSubsetSps* const pSubsetSps1, SSubsetSps* const pSubsetSps2) {
  if (!CheckMatchedSps (&pSubsetSps1->pSps, &pSubsetSps2->pSps)) {
    return false;
  }

  if ((pSubsetSps1->sSpsSvcExt.iExtendedSpatialScalability      != pSubsetSps2->sSpsSvcExt.iExtendedSpatialScalability)
      || (pSubsetSps1->sSpsSvcExt.bAdaptiveTcoeffLevelPredFlag  != pSubsetSps2->sSpsSvcExt.bAdaptiveTcoeffLevelPredFlag)
      || (pSubsetSps1->sSpsSvcExt.bSeqTcoeffLevelPredFlag != pSubsetSps2->sSpsSvcExt.bSeqTcoeffLevelPredFlag)
      || (pSubsetSps1->sSpsSvcExt.bSliceHeaderRestrictionFlag != pSubsetSps2->sSpsSvcExt.bSliceHeaderRestrictionFlag)) {
    return false;
  }

  return true;
}

/*!
 * \brief   check if the current parameter can found a presenting sps
 * \param   pParam          the current encoding paramter in SWelsSvcCodingParam
 * \param   kbUseSubsetSps  bool
 * \param   iDlayerIndex    int, the index of current D layer
 * \param   iDlayerCount    int, the number of total D layer
 * \param pSpsArray         array of all the stored SPSs
 * \param   pSubsetArray    array of all the stored Subset-SPSs
 * \return  0 - successful
 *         -1 - cannot find existing SPS for current encoder parameter
 */
int32_t FindExistingSps (SWelsSvcCodingParam* pParam, const bool kbUseSubsetSps, const int32_t iDlayerIndex,
                         const int32_t iDlayerCount, const int32_t iSpsNumInUse,
                         SWelsSPS* pSpsArray,
                         SSubsetSps* pSubsetArray, bool bSVCBaseLayer) {
  SSpatialLayerConfig* pDlayerParam = &pParam->sSpatialLayers[iDlayerIndex];

  assert (iSpsNumInUse <= MAX_SPS_COUNT);
  if (!kbUseSubsetSps) {
    SWelsSPS sTmpSps;
    WelsInitSps (&sTmpSps, pDlayerParam, &pParam->sDependencyLayers[iDlayerIndex], pParam->uiIntraPeriod,
                 pParam->iMaxNumRefFrame,
                 0, pParam->bEnableFrameCroppingFlag, pParam->iRCMode != RC_OFF_MODE, iDlayerCount,
                 bSVCBaseLayer);
    for (int32_t iId = 0; iId < iSpsNumInUse; iId++) {
      if (CheckMatchedSps (&sTmpSps, &pSpsArray[iId])) {
        return iId;
      }
    }
  } else {
    SSubsetSps sTmpSubsetSps;
    WelsInitSubsetSps (&sTmpSubsetSps, pDlayerParam, &pParam->sDependencyLayers[iDlayerIndex], pParam->uiIntraPeriod,
                       pParam->iMaxNumRefFrame,
                       0, pParam->bEnableFrameCroppingFlag, pParam->iRCMode != RC_OFF_MODE, iDlayerCount);

    for (int32_t iId = 0; iId < iSpsNumInUse; iId++) {
      if (CheckMatchedSubsetSps (&sTmpSubsetSps, &pSubsetArray[iId])) {
        return iId;
      }
    }
  }

  return INVALID_ID;
}

CWelsParametersetIdConstant::CWelsParametersetIdConstant (const bool bSimulcastAVC, const int32_t kiSpatialLayerNum) {
  memset (&m_sParaSetOffset, 0, sizeof (m_sParaSetOffset));

  m_bSimulcastAVC = bSimulcastAVC;
  m_iSpatialLayerNum = kiSpatialLayerNum;

  m_iBasicNeededSpsNum = 1;
  m_iBasicNeededPpsNum = (1 + m_iSpatialLayerNum);
}

CWelsParametersetIdConstant::~CWelsParametersetIdConstant() {
}

int32_t CWelsParametersetIdConstant::GetPpsIdOffset (const int32_t iPpsId) {
  return 0;
};
int32_t CWelsParametersetIdConstant::GetSpsIdOffset (const int32_t iPpsId, const int32_t iSpsId) {
  return 0;
};

int32_t* CWelsParametersetIdConstant::GetSpsIdOffsetList (const int iParasetType) {
  return & (m_sParaSetOffset.sParaSetOffsetVariable[iParasetType].iParaSetIdDelta[0]);
}

uint32_t CWelsParametersetIdConstant::GetAllNeededParasetNum() {
  return (GetNeededSpsNum()
          + GetNeededSubsetSpsNum()
          + GetNeededPpsNum());
}

uint32_t CWelsParametersetIdConstant::GetNeededSpsNum() {
  if (0 >= m_sParaSetOffset.uiNeededSpsNum) {
    m_sParaSetOffset.uiNeededSpsNum = m_iBasicNeededSpsNum * ((m_bSimulcastAVC) ? (m_iSpatialLayerNum) : (1));
  }
  return m_sParaSetOffset.uiNeededSpsNum;
}


uint32_t CWelsParametersetIdConstant::GetNeededSubsetSpsNum() {
  if (0 >= m_sParaSetOffset.uiNeededSubsetSpsNum) {
    m_sParaSetOffset.uiNeededSubsetSpsNum = (m_bSimulcastAVC ? 0 : (m_iSpatialLayerNum - 1));
  }
  return m_sParaSetOffset.uiNeededSubsetSpsNum;
}

uint32_t CWelsParametersetIdConstant::GetNeededPpsNum() {
  if (0 == m_sParaSetOffset.uiNeededPpsNum) {
    m_sParaSetOffset.uiNeededPpsNum = m_iBasicNeededPpsNum * ((m_bSimulcastAVC) ? (m_iSpatialLayerNum) :
                                      (1));
  }
  return m_sParaSetOffset.uiNeededPpsNum;
}

void CWelsParametersetIdConstant::LoadPrevious (SExistingParasetList* pExistingParasetList,  SWelsSPS* pSpsArray,
    SSubsetSps*       pSubsetArray, SWelsPPS* pPpsArray) {
  return;
}

void CWelsParametersetIdConstant::Update (const uint32_t kuiId, const int iParasetType) {
  memset (&m_sParaSetOffset, 0, sizeof (SParaSetOffset));
}

uint32_t CWelsParametersetIdConstant::GenerateNewSps (sWelsEncCtx* pCtx, const bool kbUseSubsetSps,
    const int32_t iDlayerIndex,
    const int32_t iDlayerCount, uint32_t kuiSpsId,
    SWelsSPS*& pSps, SSubsetSps*& pSubsetSps, bool bSVCBaselayer) {
  WelsGenerateNewSps (pCtx, kbUseSubsetSps, iDlayerIndex,
                      iDlayerCount, kuiSpsId,
                      pSps, pSubsetSps, bSVCBaselayer);
  return kuiSpsId;
}


uint32_t CWelsParametersetIdConstant::InitPps (sWelsEncCtx* pCtx, uint32_t kiSpsId,
    SWelsSPS* pSps,
    SSubsetSps* pSubsetSps,
    uint32_t kuiPpsId,
    const bool kbDeblockingFilterPresentFlag,
    const bool kbUsingSubsetSps,
    const bool kbEntropyCodingModeFlag) {
  WelsInitPps (& pCtx->pPPSArray[kuiPpsId], pSps, pSubsetSps, kuiPpsId, true, kbUsingSubsetSps, kbEntropyCodingModeFlag);
  SetUseSubsetFlag (kuiPpsId, kbUsingSubsetSps);
  return kuiPpsId;
}

void CWelsParametersetIdConstant::SetUseSubsetFlag (const uint32_t iPpsId, const bool bUseSubsetSps) {
  m_sParaSetOffset.bPpsIdMappingIntoSubsetsps[iPpsId] = bUseSubsetSps;
}

void CWelsParametersetIdNonConstant::OutputCurrentStructure (SParaSetOffsetVariable* pParaSetOffsetVariable,
    int32_t* pPpsIdList, sWelsEncCtx* pCtx, SExistingParasetList* pExistingParasetList) {
  for (int32_t k = 0; k < PARA_SET_TYPE; k++) {
    memset ((m_sParaSetOffset.sParaSetOffsetVariable[k].bUsedParaSetIdInBs), 0, MAX_PPS_COUNT * sizeof (bool));
  }
  memcpy (pParaSetOffsetVariable, m_sParaSetOffset.sParaSetOffsetVariable,
          (PARA_SET_TYPE)*sizeof (SParaSetOffsetVariable)); // confirmed_safe_unsafe_usage
}
void CWelsParametersetIdNonConstant::LoadPreviousStructure (SParaSetOffsetVariable* pParaSetOffsetVariable,
    int32_t* pPpsIdList) {
  memcpy (m_sParaSetOffset.sParaSetOffsetVariable, pParaSetOffsetVariable,
          (PARA_SET_TYPE)*sizeof (SParaSetOffsetVariable)); // confirmed_safe_unsafe_usage
}

//
//CWelsParametersetIdIncreasing
//

void CWelsParametersetIdIncreasing::DebugSpsPps (const int32_t kiPpsId, const int32_t kiSpsId) {
#if _DEBUG
  //SParaSetOffset use, 110421
  //if ( (INCREASING_ID & eSpsPpsIdStrategy)) {
  const int32_t kiParameterSetType = (m_sParaSetOffset.bPpsIdMappingIntoSubsetsps[kiPpsId] ?
                                      PARA_SET_TYPE_SUBSETSPS : PARA_SET_TYPE_AVCSPS) ;

  const int32_t kiTmpSpsIdInBs = kiSpsId +
                                 m_sParaSetOffset.sParaSetOffsetVariable[kiParameterSetType].iParaSetIdDelta[kiSpsId];
  const int32_t tmp_pps_id_in_bs = kiPpsId +
                                   m_sParaSetOffset.sParaSetOffsetVariable[PARA_SET_TYPE_PPS].iParaSetIdDelta[kiPpsId];
  assert (MAX_SPS_COUNT > kiTmpSpsIdInBs);
  assert (MAX_PPS_COUNT > tmp_pps_id_in_bs);
  assert (m_sParaSetOffset.sParaSetOffsetVariable[kiParameterSetType].bUsedParaSetIdInBs[kiTmpSpsIdInBs]);
  //}
#endif
}
void CWelsParametersetIdIncreasing::DebugPps (const int32_t kiPpsId) {
#if _DEBUG
  const int32_t kiTmpPpsIdInBs = kiPpsId +
                                 m_sParaSetOffset.sParaSetOffsetVariable[PARA_SET_TYPE_PPS].iParaSetIdDelta[ kiPpsId ];
  assert (MAX_PPS_COUNT > kiTmpPpsIdInBs);

  //when activated need to sure there is avialable PPS
  assert (m_sParaSetOffset.sParaSetOffsetVariable[PARA_SET_TYPE_PPS].bUsedParaSetIdInBs[kiTmpPpsIdInBs]);
#endif
}

void ParasetIdAdditionIdAdjust (SParaSetOffsetVariable* sParaSetOffsetVariable,
                                const int32_t kiCurEncoderParaSetId,
                                const uint32_t kuiMaxIdInBs) { //paraset_type = 0: SPS; =1: PPS
  //SPS_ID in avc_sps and pSubsetSps will be different using this
  //SPS_ID case example:
  //1st enter:  next_spsid_in_bs == 0; spsid == 0; delta==0;            //actual spsid_in_bs == 0
  //1st finish: next_spsid_in_bs == 1;
  //2nd enter:  next_spsid_in_bs == 1; spsid == 0; delta==1;            //actual spsid_in_bs == 1
  //2nd finish: next_spsid_in_bs == 2;
  //31st enter: next_spsid_in_bs == 31; spsid == 0~2; delta==31~29;     //actual spsid_in_bs == 31
  //31st finish:next_spsid_in_bs == 0;
  //31st enter: next_spsid_in_bs == 0; spsid == 0~2; delta==-2~0;       //actual spsid_in_bs == 0
  //31st finish:next_spsid_in_bs == 1;

  const int32_t kiEncId = kiCurEncoderParaSetId;
  uint32_t uiNextIdInBs = sParaSetOffsetVariable->uiNextParaSetIdToUseInBs;

  //update current layer's pCodingParam
  sParaSetOffsetVariable->iParaSetIdDelta[kiEncId] = uiNextIdInBs -
      kiEncId;  //for current parameter set, change its id_delta
  //write pso pData for next update:
  sParaSetOffsetVariable->bUsedParaSetIdInBs[uiNextIdInBs] = true; //   update current used_id

  //prepare for next update:
  //   find the next avaibable iId
  ++uiNextIdInBs;
  if (uiNextIdInBs >= kuiMaxIdInBs) {
    uiNextIdInBs = 0;//ensure the SPS_ID wound not exceed MAX_SPS_COUNT
  }
  //   update next_id
  sParaSetOffsetVariable->uiNextParaSetIdToUseInBs = uiNextIdInBs;
}

void CWelsParametersetIdIncreasing::Update (const uint32_t kuiId, const int iParasetType) {
#if _DEBUG
  m_sParaSetOffset.eSpsPpsIdStrategy = INCREASING_ID;
  assert (kuiId < MAX_DQ_LAYER_NUM);
#endif

  ParasetIdAdditionIdAdjust (& (m_sParaSetOffset.sParaSetOffsetVariable[iParasetType]),
                             kuiId,
                             (iParasetType != PARA_SET_TYPE_PPS) ? MAX_SPS_COUNT : MAX_PPS_COUNT);
}
//((SPS_PPS_LISTING != pEncCtx->pSvcParam->eSpsPpsIdStrategy) ? (&
//  (pEncCtx->sPSOVector.sParaSetOffsetVariable[PARA_SET_TYPE_PPS].iParaSetIdDelta[0])) : NULL)

int32_t CWelsParametersetIdIncreasing::GetPpsIdOffset (const int32_t kiPpsId) {
#if _DEBUG
  DebugPps (kiPpsId);
#endif
  return (m_sParaSetOffset.sParaSetOffsetVariable[PARA_SET_TYPE_PPS].iParaSetIdDelta[kiPpsId]);
}

int32_t CWelsParametersetIdIncreasing::GetSpsIdOffset (const int32_t kiPpsId, const int32_t kiSpsId) {
  const int32_t kiParameterSetType = (m_sParaSetOffset.bPpsIdMappingIntoSubsetsps[kiPpsId] ?
                                      PARA_SET_TYPE_SUBSETSPS : PARA_SET_TYPE_AVCSPS);
#if _DEBUG
  DebugSpsPps (kiPpsId, kiSpsId);
#endif
  return (m_sParaSetOffset.sParaSetOffsetVariable[kiParameterSetType].iParaSetIdDelta[kiSpsId]);
}

//
//CWelsParametersetSpsListing
//

CWelsParametersetSpsListing::CWelsParametersetSpsListing (const bool bSimulcastAVC,
    const int32_t kiSpatialLayerNum) : CWelsParametersetIdNonConstant (bSimulcastAVC, kiSpatialLayerNum) {
  memset (&m_sParaSetOffset, 0, sizeof (m_sParaSetOffset));

  m_bSimulcastAVC = bSimulcastAVC;
  m_iSpatialLayerNum = kiSpatialLayerNum;

  m_iBasicNeededSpsNum = MAX_SPS_COUNT;
  m_iBasicNeededPpsNum = 1;
}

uint32_t CWelsParametersetSpsListing::GetNeededSubsetSpsNum() {
  if (0 >= m_sParaSetOffset.uiNeededSubsetSpsNum) {
    //      sPSOVector.uiNeededSubsetSpsNum = ((pSvcParam->bSimulcastAVC) ? (0) :((SPS_LISTING & pSvcParam->eSpsPpsIdStrategy) ? (MAX_SPS_COUNT) : (pSvcParam->iSpatialLayerNum - 1)));
    m_sParaSetOffset.uiNeededSubsetSpsNum = ((m_bSimulcastAVC) ? (0) :
                                            (MAX_SPS_COUNT));
  }
  return m_sParaSetOffset.uiNeededSubsetSpsNum;
}

void CWelsParametersetSpsListing::LoadPreviousSps (SExistingParasetList* pExistingParasetList, SWelsSPS* pSpsArray,
    SSubsetSps*       pSubsetArray) {
  //if ((SPS_LISTING & pParam->eSpsPpsIdStrategy) && (NULL != pExistingParasetList)) {
  m_sParaSetOffset.uiInUseSpsNum = pExistingParasetList->uiInUseSpsNum;
  memcpy (pSpsArray, pExistingParasetList->sSps, MAX_SPS_COUNT * sizeof (SWelsSPS));

  if (GetNeededSubsetSpsNum() > 0) {
    m_sParaSetOffset.uiInUseSubsetSpsNum = pExistingParasetList->uiInUseSubsetSpsNum;
    memcpy (pSubsetArray, pExistingParasetList->sSubsetSps, MAX_SPS_COUNT * sizeof (SSubsetSps));
  } else {
    m_sParaSetOffset.uiInUseSubsetSpsNum = 0;
  }
  //}

}
void CWelsParametersetSpsListing::LoadPrevious (SExistingParasetList* pExistingParasetList,  SWelsSPS* pSpsArray,
    SSubsetSps*       pSubsetArray, SWelsPPS* pPpsArray) {
  if (NULL == pExistingParasetList) {
    return;
  }
  LoadPreviousSps (pExistingParasetList, pSpsArray, pSubsetArray);
  LoadPreviousPps (pExistingParasetList, pPpsArray);
}

bool CWelsParametersetSpsListing::CheckParamCompatibility (SWelsSvcCodingParam* pCodingParam, SLogContext* pLogCtx) {
  if (pCodingParam->iSpatialLayerNum > 1 && (!pCodingParam->bSimulcastAVC)) {
    WelsLog (pLogCtx, WELS_LOG_WARNING,
             "ParamValidationExt(), eSpsPpsIdStrategy setting (%d) with multiple svc SpatialLayers (%d) not supported! eSpsPpsIdStrategy adjusted to CONSTANT_ID",
             pCodingParam->eSpsPpsIdStrategy, pCodingParam->iSpatialLayerNum);
    pCodingParam->eSpsPpsIdStrategy = CONSTANT_ID;
    return false;
  }
  return true;
}

bool CWelsParametersetSpsListing::CheckPpsGenerating() {
  return true;
}
int32_t CWelsParametersetSpsListing::SpsReset (sWelsEncCtx* pCtx, bool kbUseSubsetSps) {

  // reset current list
  if (!kbUseSubsetSps) {
    m_sParaSetOffset.uiInUseSpsNum = 1;
    memset (pCtx->pSpsArray, 0, MAX_SPS_COUNT * sizeof (SWelsSPS));
  } else {
    m_sParaSetOffset.uiInUseSubsetSpsNum = 1;
    memset (pCtx->pSubsetArray, 0, MAX_SPS_COUNT * sizeof (SSubsetSps));
  }

  //iSpsId = 0;
  return 0;
}
uint32_t CWelsParametersetSpsListing::GenerateNewSps (sWelsEncCtx* pCtx, const bool kbUseSubsetSps,
    const int32_t iDlayerIndex,
    const int32_t iDlayerCount, uint32_t kuiSpsId,
    SWelsSPS*& pSps, SSubsetSps*& pSubsetSps, bool bSvcBaselayer) {
  //check if the current param can fit in an existing SPS
  const int32_t kiFoundSpsId = FindExistingSps (pCtx->pSvcParam, kbUseSubsetSps, iDlayerIndex, iDlayerCount,
                               kbUseSubsetSps ? (m_sParaSetOffset.uiInUseSubsetSpsNum) : (m_sParaSetOffset.uiInUseSpsNum),
                               pCtx->pSpsArray,
                               pCtx->pSubsetArray, bSvcBaselayer);


  if (INVALID_ID != kiFoundSpsId) {
    //if yes, set pSps or pSubsetSps to it
    kuiSpsId = kiFoundSpsId;
    if (!kbUseSubsetSps) {
      pSps = & (pCtx->pSpsArray[kiFoundSpsId]);
    } else {
      pSubsetSps = & (pCtx->pSubsetArray[kiFoundSpsId]);
    }
  } else {
    //if no, generate a new SPS as usual
    if (!CheckPpsGenerating()) {
      return -1;
    }

    kuiSpsId = (!kbUseSubsetSps) ? (m_sParaSetOffset.uiInUseSpsNum++) : (m_sParaSetOffset.uiInUseSubsetSpsNum++);
    if (kuiSpsId >= MAX_SPS_COUNT) {
      if (SpsReset (pCtx, kbUseSubsetSps) < 0) {
        return -1;
      }
      kuiSpsId = 0;
    }

    WelsGenerateNewSps (pCtx, kbUseSubsetSps, iDlayerIndex,
                        iDlayerCount, kuiSpsId, pSps, pSubsetSps, bSvcBaselayer);
  }
  return kuiSpsId;
}

void CWelsParametersetSpsListing::UpdateParaSetNum (sWelsEncCtx* pCtx) {
  pCtx->iSpsNum = m_sParaSetOffset.uiInUseSpsNum;
  pCtx->iSubsetSpsNum = m_sParaSetOffset.uiInUseSubsetSpsNum;
};

void CWelsParametersetSpsListing::OutputCurrentStructure (SParaSetOffsetVariable* pParaSetOffsetVariable,
    int32_t* pPpsIdList, sWelsEncCtx* pCtx, SExistingParasetList* pExistingParasetList) {
  CWelsParametersetIdNonConstant::OutputCurrentStructure (pParaSetOffsetVariable, pPpsIdList, pCtx, pExistingParasetList);
  pExistingParasetList->uiInUseSpsNum = m_sParaSetOffset.uiInUseSpsNum;

  memcpy (pExistingParasetList->sSps, pCtx->pSpsArray, MAX_SPS_COUNT * sizeof (SWelsSPS));
  if (NULL != pCtx->pSubsetArray) {
    pExistingParasetList->uiInUseSubsetSpsNum = m_sParaSetOffset.uiInUseSubsetSpsNum;
    memcpy (pExistingParasetList->sSubsetSps, pCtx->pSubsetArray, MAX_SPS_COUNT * sizeof (SSubsetSps));
  } else {
    pExistingParasetList->uiInUseSubsetSpsNum = 0;
  }
}

//
//CWelsParametersetSpsPpsListing
//

CWelsParametersetSpsPpsListing::CWelsParametersetSpsPpsListing (const bool bSimulcastAVC,
    const int32_t kiSpatialLayerNum): CWelsParametersetSpsListing (bSimulcastAVC, kiSpatialLayerNum) {
  memset (&m_sParaSetOffset, 0, sizeof (m_sParaSetOffset));

  m_bSimulcastAVC = bSimulcastAVC;
  m_iSpatialLayerNum = kiSpatialLayerNum;

  m_iBasicNeededSpsNum = MAX_SPS_COUNT;
  m_iBasicNeededPpsNum = MAX_PPS_COUNT;
}

void CWelsParametersetSpsPpsListing::LoadPreviousPps (SExistingParasetList* pExistingParasetList, SWelsPPS* pPpsArray) {
  // copy from existing if the pointer exists
  //if ((SPS_PPS_LISTING == pParam->eSpsPpsIdStrategy) && (NULL != pExistingParasetList)) {
  m_sParaSetOffset.uiInUsePpsNum = pExistingParasetList->uiInUsePpsNum;
  memcpy (pPpsArray, pExistingParasetList->sPps, MAX_PPS_COUNT * sizeof (SWelsPPS));
  //}
}

/*  if ((SPS_PPS_LISTING == pCtx->pSvcParam->eSpsPpsIdStrategy) && (pCtx->iPpsNum < MAX_PPS_COUNT)) {
 UpdatePpsList (pCtx);
 }*/
void CWelsParametersetSpsPpsListing::UpdatePpsList (sWelsEncCtx* pCtx) {
  if (pCtx->iPpsNum >= MAX_PPS_COUNT) {
    return;
  }
  assert (pCtx->iPpsNum <= MAX_DQ_LAYER_NUM);

  //Generate PPS LIST
  int32_t iPpsId = 0, iUsePpsNum = pCtx->iPpsNum;

  for (int32_t iIdrRound = 0; iIdrRound < MAX_PPS_COUNT; iIdrRound++) {
    for (iPpsId = 0; iPpsId < pCtx->iPpsNum; iPpsId++) {
      m_sParaSetOffset.iPpsIdList[iPpsId][iIdrRound] = ((iIdrRound * iUsePpsNum + iPpsId) % MAX_PPS_COUNT);
    }
  }

  for (iPpsId = iUsePpsNum; iPpsId < MAX_PPS_COUNT; iPpsId++) {
    memcpy (& (pCtx->pPPSArray[iPpsId]), & (pCtx->pPPSArray[iPpsId % iUsePpsNum]), sizeof (SWelsPPS));
    pCtx->pPPSArray[iPpsId].iPpsId = iPpsId;
    pCtx->iPpsNum++;
  }

  assert (pCtx->iPpsNum == MAX_PPS_COUNT);
  m_sParaSetOffset.uiInUsePpsNum = pCtx->iPpsNum;
}


bool CWelsParametersetSpsPpsListing::CheckPpsGenerating() {
  /*if ((SPS_PPS_LISTING == pCtx->pSvcParam->eSpsPpsIdStrategy) && (MAX_PPS_COUNT <= pCtx->sPSOVector.uiInUsePpsNum)) {
    //check if we can generate new SPS or not
    WelsLog (& pCtx->sLogCtx, WELS_LOG_ERROR,
             "InitDqLayers(), cannot generate new SPS under the SPS_PPS_LISTING mode!");
    return ENC_RETURN_UNSUPPORTED_PARA;
  }*/
  if (MAX_PPS_COUNT <= m_sParaSetOffset.uiInUsePpsNum) {
    return false;
  }

  return true;
}
int32_t CWelsParametersetSpsPpsListing::SpsReset (sWelsEncCtx* pCtx, bool kbUseSubsetSps) {
  /*        if (SPS_PPS_LISTING == pParam->eSpsPpsIdStrategy) {
   WelsLog (& (*ppCtx)->sLogCtx, WELS_LOG_ERROR,
   "InitDqLayers(), cannot generate new SPS under the SPS_PPS_LISTING mode!");
   return ENC_RETURN_UNSUPPORTED_PARA;
   }*/
  return -1;
}

int32_t FindExistingPps (SWelsSPS* pSps, SSubsetSps* pSubsetSps, const bool kbUseSubsetSps, const int32_t iSpsId,
                         const bool kbEntropyCodingFlag, const int32_t iPpsNumInUse,
                         SWelsPPS* pPpsArray) {
#if !defined(DISABLE_FMO_FEATURE)
  // feature not supported yet
  return INVALID_ID;
#endif//!DISABLE_FMO_FEATURE

  SWelsPPS sTmpPps;
  WelsInitPps (&sTmpPps,
               pSps,
               pSubsetSps,
               0,
               true,
               kbUseSubsetSps,
               kbEntropyCodingFlag);

  assert (iPpsNumInUse <= MAX_PPS_COUNT);
  for (int32_t iId = 0; iId < iPpsNumInUse; iId++) {
    if ((sTmpPps.iSpsId == pPpsArray[iId].iSpsId)
        && (sTmpPps.bEntropyCodingModeFlag == pPpsArray[iId].bEntropyCodingModeFlag)
        && (sTmpPps.iPicInitQp == pPpsArray[iId].iPicInitQp)
        && (sTmpPps.iPicInitQs == pPpsArray[iId].iPicInitQs)
        && (sTmpPps.uiChromaQpIndexOffset == pPpsArray[iId].uiChromaQpIndexOffset)
        && (sTmpPps.bDeblockingFilterControlPresentFlag == pPpsArray[iId].bDeblockingFilterControlPresentFlag)
       ) {
      return iId;
    }
  }

  return INVALID_ID;
}

uint32_t CWelsParametersetSpsPpsListing::InitPps (sWelsEncCtx* pCtx, uint32_t kiSpsId,
    SWelsSPS* pSps,
    SSubsetSps* pSubsetSps,
    uint32_t kuiPpsId,
    const bool kbDeblockingFilterPresentFlag,
    const bool kbUsingSubsetSps,
    const bool kbEntropyCodingModeFlag) {
  const int32_t kiFoundPpsId = FindExistingPps (pSps, pSubsetSps, kbUsingSubsetSps, kiSpsId,
                               kbEntropyCodingModeFlag,
                               m_sParaSetOffset.uiInUsePpsNum,
                               pCtx->pPPSArray);


  if (INVALID_ID != kiFoundPpsId) {
    //if yes, set pPps to it
    kuiPpsId = kiFoundPpsId;
  } else {
    kuiPpsId = (m_sParaSetOffset.uiInUsePpsNum++);
    WelsInitPps (& pCtx->pPPSArray[kuiPpsId], pSps, pSubsetSps, kuiPpsId, true, kbUsingSubsetSps, kbEntropyCodingModeFlag);
  }
  SetUseSubsetFlag (kuiPpsId, kbUsingSubsetSps);
  return kuiPpsId;
}

void CWelsParametersetSpsPpsListing::UpdateParaSetNum (sWelsEncCtx* pCtx) {
  CWelsParametersetSpsListing::UpdateParaSetNum (pCtx);

  //UpdatePpsList (pCtx);
  pCtx->iPpsNum = m_sParaSetOffset.uiInUsePpsNum;
}

int32_t CWelsParametersetSpsPpsListing::GetCurrentPpsId (const int32_t iPpsId, const int32_t iIdrLoop) {
  return m_sParaSetOffset.iPpsIdList[iPpsId][iIdrLoop];
}

void CWelsParametersetSpsPpsListing::LoadPreviousStructure (SParaSetOffsetVariable* pParaSetOffsetVariable,
    int32_t* pPpsIdList) {
  memcpy (m_sParaSetOffset.sParaSetOffsetVariable, pParaSetOffsetVariable,
          (PARA_SET_TYPE)*sizeof (SParaSetOffsetVariable)); // confirmed_safe_unsafe_usage

  memcpy ((m_sParaSetOffset.iPpsIdList), pPpsIdList, MAX_DQ_LAYER_NUM * MAX_PPS_COUNT * sizeof (int32_t));

}

void CWelsParametersetSpsPpsListing::OutputCurrentStructure (SParaSetOffsetVariable* pParaSetOffsetVariable,
    int32_t* pPpsIdList, sWelsEncCtx* pCtx, SExistingParasetList* pExistingParasetList) {
  CWelsParametersetSpsListing::OutputCurrentStructure (pParaSetOffsetVariable, pPpsIdList, pCtx, pExistingParasetList);

  pExistingParasetList->uiInUsePpsNum = m_sParaSetOffset.uiInUsePpsNum;
  memcpy (pExistingParasetList->sPps, pCtx->pPps, MAX_PPS_COUNT * sizeof (SWelsPPS));
  memcpy (pPpsIdList, (m_sParaSetOffset.iPpsIdList), MAX_DQ_LAYER_NUM * MAX_PPS_COUNT * sizeof (int32_t));
}

//
//CWelsParametersetSpsListingPpsIncreasing
//

int32_t CWelsParametersetSpsListingPpsIncreasing::GetPpsIdOffset (const int32_t kiPpsId) {
  //same as CWelsParametersetIdIncreasing::GetPpsIdOffset
  return (m_sParaSetOffset.sParaSetOffsetVariable[PARA_SET_TYPE_PPS].iParaSetIdDelta[kiPpsId]);
}

void CWelsParametersetSpsListingPpsIncreasing::Update (const uint32_t kuiId, const int iParasetType) {
  //same as CWelsParametersetIdIncreasing::Update
#if _DEBUG
  assert (kuiId < MAX_DQ_LAYER_NUM);
#endif

  ParasetIdAdditionIdAdjust (& (m_sParaSetOffset.sParaSetOffsetVariable[iParasetType]),
                             kuiId,
                             (iParasetType != PARA_SET_TYPE_PPS) ? MAX_SPS_COUNT : MAX_PPS_COUNT);
}
}
