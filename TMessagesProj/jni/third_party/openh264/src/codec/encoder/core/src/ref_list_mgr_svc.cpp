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

// ref_list_mgr_svc.c
#include "ref_list_mgr_svc.h"
#include "utils.h"
#include "picture_handle.h"
namespace WelsEnc {

#define STR_ROOM 1
/*
*   reset LTR marking , recovery ,feedback state to default
*/
void ResetLtrState (SLTRState* pLtr) {
  pLtr->bReceivedT0LostFlag = false;
  pLtr->iLastRecoverFrameNum = 0;
  pLtr->iLastCorFrameNumDec = -1;
  pLtr->iCurFrameNumInDec = -1;

  // LTR mark
  pLtr->iLTRMarkMode = LTR_DIRECT_MARK;
  pLtr->iLTRMarkSuccessNum = 0; //successful marked num
  pLtr->bLTRMarkingFlag = false; //decide whether current frame marked as LTR
  pLtr->bLTRMarkEnable = false; //when LTR is confirmed and the interval is no smaller than the marking period
  pLtr->iCurLtrIdx = 0;
  memset (&pLtr->iLastLtrIdx , 0 , sizeof (pLtr->iLastLtrIdx)) ;
  pLtr->uiLtrMarkInterval = 0;

  // LTR mark feedback
  pLtr->uiLtrMarkState = NO_LTR_MARKING_FEEDBACK ;
  pLtr->iLtrMarkFbFrameNum = -1;
}

/*
 *  reset reference picture list
 */
void WelsResetRefList (sWelsEncCtx* pCtx) {
  SRefList* pRefList = pCtx->ppRefPicListExt[pCtx->uiDependencyId];
  int32_t i;

  for (i = 0; i < MAX_SHORT_REF_COUNT + 1; i++)
    pRefList->pShortRefList[i] = NULL;
  for (i = 0; i < pCtx->pSvcParam->iLTRRefNum + 1; i++)
    pRefList->pLongRefList[i] = NULL;
  for (i = 0; i < pCtx->pSvcParam->iNumRefFrame + 1; i++)
    pRefList->pRef[i]->SetUnref();

  pRefList->uiLongRefCount = 0;
  pRefList->uiShortRefCount = 0;
  pRefList->pNextBuffer = pRefList->pRef[0];
}

static inline void DeleteLTRFromLongList (sWelsEncCtx* pCtx, int32_t iIdx) {
  SRefList* pRefList = pCtx->ppRefPicListExt[pCtx->uiDependencyId];
  int32_t k ;

  for (k = iIdx; k < pRefList->uiLongRefCount - 1; k++) {
    pRefList->pLongRefList[k] = pRefList->pLongRefList[k + 1];
  }
  pRefList->pLongRefList[k] = NULL;
  pRefList->uiLongRefCount--;

}
static inline void DeleteSTRFromShortList (sWelsEncCtx* pCtx, int32_t iIdx) {
  SRefList* pRefList = pCtx->ppRefPicListExt[pCtx->uiDependencyId];
  int32_t k ;

  for (k = iIdx; k < pRefList->uiShortRefCount - 1; k++) {
    pRefList->pShortRefList[k] = pRefList->pShortRefList[k + 1];
  }
  pRefList->pShortRefList[k] = NULL;
  pRefList->uiShortRefCount--;

}
static void DeleteNonSceneLTR (sWelsEncCtx* pCtx) {
  SRefList* pRefList = pCtx->ppRefPicListExt[pCtx->uiDependencyId];
  for (int32_t i = 0; i < pCtx->pSvcParam->iNumRefFrame; ++i) {
    SPicture* pRef = pRefList->pLongRefList[i];
    if (pRef != NULL &&  pRef->bUsedAsRef && pRef->bIsLongRef && (!pRef->bIsSceneLTR) &&
        (pCtx->uiTemporalId < pRef->uiTemporalId || pCtx->bCurFrameMarkedAsSceneLtr)) {
      //this is our strategy to Unref all non-sceneLTR when the the current frame is sceneLTR
      pRef->SetUnref();
      DeleteLTRFromLongList (pCtx, i);
      i--;
    }
  }
}

static inline int32_t CompareFrameNum (int32_t iFrameNumA, int32_t iFrameNumB, int32_t iMaxFrameNumPlus1) {
  int64_t iNumA, iNumB, iDiffAB, iDiffMin;
  if (iFrameNumA > iMaxFrameNumPlus1 || iFrameNumB > iMaxFrameNumPlus1) {
    return -2;
  }
#define  WelsAbsDiffInt64(a,b) ( (a) > (b) )?( a - b ):( b - a )

  iDiffAB = WelsAbsDiffInt64 ((int64_t) (iFrameNumA), (int64_t) (iFrameNumB));

  iDiffMin = iDiffAB;
  if (iDiffMin == 0) {
    return FRAME_NUM_EQUAL;
  }

  iNumA = WelsAbsDiffInt64 ((int64_t) (iFrameNumA + iMaxFrameNumPlus1), (int64_t) (iFrameNumB));
  if (iNumA == 0) {
    return FRAME_NUM_EQUAL;
  } else if (iDiffMin > iNumA) {
    return FRAME_NUM_BIGGER;
  }

  iNumB = WelsAbsDiffInt64 ((int64_t) (iFrameNumB + iMaxFrameNumPlus1), (int64_t) (iFrameNumA));
  if (iNumB == 0) {
    return FRAME_NUM_EQUAL;
  } else if (iDiffMin > iNumB) {
    return FRAME_NUM_SMALLER;
  }

  return (iFrameNumA > iFrameNumB) ? (FRAME_NUM_BIGGER) : (FRAME_NUM_SMALLER);

}
/*
*   delete failed mark according LTR recovery pRequest
*/
static inline void DeleteInvalidLTR (sWelsEncCtx* pCtx) {
  SRefList* pRefList = pCtx->ppRefPicListExt[pCtx->uiDependencyId];
  SPicture** pLongRefList = pRefList->pLongRefList;
  SLTRState* pLtr = &pCtx->pLtr[pCtx->uiDependencyId];
  int32_t iMaxFrameNumPlus1 = (1 << pCtx->pSps->uiLog2MaxFrameNum);
  int32_t i;
  SSpatialLayerInternal* pParamInternal = &pCtx->pSvcParam->sDependencyLayers[pCtx->uiDependencyId];
  SLogContext* pLogCtx = & (pCtx->sLogCtx);

  for (i = 0; i < LONG_TERM_REF_NUM; i++) {
    if (pLongRefList[i] != NULL) {
      if (CompareFrameNum (pLongRefList[i]->iFrameNum , pLtr->iLastCorFrameNumDec, iMaxFrameNumPlus1) == FRAME_NUM_BIGGER
          && (CompareFrameNum (pLongRefList[i]->iFrameNum , pLtr->iCurFrameNumInDec,
                               iMaxFrameNumPlus1) & (FRAME_NUM_EQUAL | FRAME_NUM_SMALLER))) {
        WelsLog (pLogCtx, WELS_LOG_WARNING, "LTR ,invalid LTR delete ,long_term_idx = %d , iFrameNum =%d ",
                 pLongRefList[i]->iLongTermPicNum, pLongRefList[i]->iFrameNum);
        pLongRefList[i]->SetUnref();
        DeleteLTRFromLongList (pCtx, i);
        pLtr->bLTRMarkEnable = true;
        if (pRefList->uiLongRefCount == 0) {
          pParamInternal->bEncCurFrmAsIdrFlag = true;
        }
      } else if (CompareFrameNum (pLongRefList[i]->iMarkFrameNum , pLtr->iLastCorFrameNumDec ,
                                  iMaxFrameNumPlus1) == FRAME_NUM_BIGGER
                 && (CompareFrameNum (pLongRefList[i]->iMarkFrameNum, pLtr->iCurFrameNumInDec ,
                                      iMaxFrameNumPlus1) & (FRAME_NUM_EQUAL | FRAME_NUM_SMALLER))
                 && pLtr->iLTRMarkMode == LTR_DELAY_MARK) {
        WelsLog (pLogCtx, WELS_LOG_WARNING, "LTR ,iMarkFrameNum invalid LTR delete ,long_term_idx = %d , iFrameNum =%d ",
                 pLongRefList[i]->iLongTermPicNum, pLongRefList[i]->iFrameNum);
        pLongRefList[i]->SetUnref();
        DeleteLTRFromLongList (pCtx, i);
        pLtr->bLTRMarkEnable = true;
        if (pRefList->uiLongRefCount == 0) {
          pParamInternal->bEncCurFrmAsIdrFlag = true;
        }
      }
    }
  }

}
/*
*   handle LTR Mark feedback message
*/
static inline void HandleLTRMarkFeedback (sWelsEncCtx* pCtx) {
  SRefList* pRefList            = pCtx->ppRefPicListExt[pCtx->uiDependencyId];
  SPicture** pLongRefList       = pRefList->pLongRefList;
  SLTRState* pLtr = &pCtx->pLtr[pCtx->uiDependencyId];
  SSpatialLayerInternal* pParamInternal = &pCtx->pSvcParam->sDependencyLayers[pCtx->uiDependencyId];
  int32_t i, j;

  if (pLtr->uiLtrMarkState == LTR_MARKING_SUCCESS) {
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING,
             "pLtr->uiLtrMarkState = %d, pLtr.iCurLtrIdx = %d , pLtr->iLtrMarkFbFrameNum = %d ,pCtx->iFrameNum = %d ",
             pLtr->uiLtrMarkState, pLtr->iCurLtrIdx, pLtr->iLtrMarkFbFrameNum, pParamInternal->iFrameNum);
    for (i = 0; i < pRefList->uiLongRefCount; i++) {
      if (pLongRefList[i]->iFrameNum == pLtr->iLtrMarkFbFrameNum && pLongRefList[i]->uiRecieveConfirmed != RECIEVE_SUCCESS) {

        pLongRefList[i]->uiRecieveConfirmed = RECIEVE_SUCCESS;
        pCtx->pVaa->uiValidLongTermPicIdx = pLongRefList[i]->iLongTermPicNum;

        pLtr->iCurFrameNumInDec  =
          pLtr->iLastRecoverFrameNum =
            pLtr->iLastCorFrameNumDec = pLtr->iLtrMarkFbFrameNum;

        for (j = 0; j < pRefList->uiLongRefCount; j++) {
          if (pLongRefList[j]->iLongTermPicNum != pLtr->iCurLtrIdx) {
            pLongRefList[j]->SetUnref();
            DeleteLTRFromLongList (pCtx, j);
          }
        }

        pLtr->iLTRMarkSuccessNum++;
        pLtr->iCurLtrIdx = (pLtr->iCurLtrIdx + 1) % LONG_TERM_REF_NUM;
        pLtr->iLTRMarkMode = (pLtr->iLTRMarkSuccessNum >= (LONG_TERM_REF_NUM)) ? (LTR_DELAY_MARK) : (LTR_DIRECT_MARK);
        WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, "LTR mark mode =%d", pLtr->iLTRMarkMode);
        pLtr->bLTRMarkEnable = true;
        break;
      }
    }
    pLtr->uiLtrMarkState = NO_LTR_MARKING_FEEDBACK;
  } else if (pLtr->uiLtrMarkState == LTR_MARKING_FAILED) {
    for (i = 0; i < pRefList->uiLongRefCount; i++) {
      if (pLongRefList[i]->iFrameNum == pLtr->iLtrMarkFbFrameNum) {
        pLongRefList[i]->SetUnref();
        DeleteLTRFromLongList (pCtx, i);
        break;
      }
    }
    pLtr->uiLtrMarkState = NO_LTR_MARKING_FEEDBACK;
    pLtr->bLTRMarkEnable = true;

    if (pLtr->iLTRMarkSuccessNum == 0) {
      pParamInternal->bEncCurFrmAsIdrFlag = true; // no LTR , means IDR recieve failed, force next frame IDR
    }
  }
}
/*
 *  LTR mark process
 */
static inline void LTRMarkProcess (sWelsEncCtx* pCtx) {
  SRefList* pRefList = pCtx->ppRefPicListExt[pCtx->uiDependencyId];
  SPicture** pLongRefList = pRefList->pLongRefList;
  SPicture** pShortRefList = pRefList->pShortRefList;
  SLTRState* pLtr = &pCtx->pLtr[pCtx->uiDependencyId];
  int32_t iGoPFrameNumInterval = ((pCtx->pSvcParam->uiGopSize >> 1) > 1) ? (pCtx->pSvcParam->uiGopSize >> 1) : (1);
  int32_t iMaxFrameNumPlus1 = (1 << pCtx->pSps->uiLog2MaxFrameNum);
  int32_t i = 0;
  int32_t j = 0;
  bool bMoveLtrFromShortToLong = false;
  SSpatialLayerInternal* pParamInternal = &pCtx->pSvcParam->sDependencyLayers[pCtx->uiDependencyId];

  if (pCtx->eSliceType == I_SLICE) {
    i = 0;
    pShortRefList[i]->uiRecieveConfirmed = RECIEVE_SUCCESS;
  } else if (pLtr->bLTRMarkingFlag) {
    pCtx->pVaa->uiMarkLongTermPicIdx = pLtr->iCurLtrIdx;

    if (pLtr->iLTRMarkMode == LTR_DELAY_MARK) {
      for (i = 0; i < pRefList->uiShortRefCount; i++) {
        if (CompareFrameNum (pParamInternal->iFrameNum, pShortRefList[i]->iFrameNum + iGoPFrameNumInterval,
                             iMaxFrameNumPlus1) == FRAME_NUM_EQUAL) {
          break;
        }
      }
    }
  }

  if (pCtx->eSliceType == I_SLICE || pLtr->bLTRMarkingFlag) {
    pShortRefList[i]->bIsLongRef = true;
    pShortRefList[i]->iLongTermPicNum = pLtr->iCurLtrIdx;
    pShortRefList[i]->iMarkFrameNum = pParamInternal->iFrameNum;
  }

  // delay one gop to move LTR from int16_t list to int32_t list
  if (pLtr->iLTRMarkMode == LTR_DIRECT_MARK && pCtx->eSliceType != I_SLICE && !pLtr->bLTRMarkingFlag) {
    for (j = 0; j < pRefList->uiShortRefCount; j++) {
      if (pRefList->pShortRefList[j]->bIsLongRef) {
        i = j;
        bMoveLtrFromShortToLong = true;
        break;
      }
    }
  }

  if ((pLtr->iLTRMarkMode == LTR_DELAY_MARK && pLtr->bLTRMarkingFlag)
      || ((pLtr->iLTRMarkMode == LTR_DIRECT_MARK) && (bMoveLtrFromShortToLong))) {
    pCtx->bRefOfCurTidIsLtr[pCtx->uiDependencyId][pCtx->uiTemporalId] = true;

    if (pRefList->uiLongRefCount > 0) {
      memmove (&pRefList->pLongRefList[1], &pRefList->pLongRefList[0],
               pRefList->uiLongRefCount * sizeof (SPicture*)); // confirmed_safe_unsafe_usage
    }
    pLongRefList[0] = pShortRefList[i];
    pRefList->uiLongRefCount++;
    if (pRefList->uiLongRefCount > pCtx->pSvcParam->iLTRRefNum) {
      pRefList->pLongRefList[pRefList->uiLongRefCount - 1]->SetUnref();
      DeleteLTRFromLongList (pCtx, pRefList->uiLongRefCount - 1);
    }
    DeleteSTRFromShortList (pCtx, i);
  }
}

static inline void LTRMarkProcessScreen (sWelsEncCtx* pCtx) {
  SRefList* pRefList = pCtx->ppRefPicListExt[pCtx->uiDependencyId];
  SPicture** pLongRefList = pRefList->pLongRefList;
  int32_t iLtrIdx =  pCtx->pDecPic->iLongTermPicNum;
  pCtx->pVaa->uiMarkLongTermPicIdx = pCtx->pDecPic->iLongTermPicNum;

  assert (CheckInRangeCloseOpen (iLtrIdx, 0, MAX_REF_PIC_COUNT));
  if (pLongRefList[iLtrIdx] != NULL) {
    pLongRefList[iLtrIdx]->SetUnref();
  } else {
    pRefList->uiLongRefCount++;
  }
  pLongRefList[iLtrIdx] = pCtx->pDecPic;
}

static void PrefetchNextBuffer (sWelsEncCtx* pCtx) {
  SRefList* pRefList            = pCtx->ppRefPicListExt[pCtx->uiDependencyId];
  const int32_t kiNumRef        = pCtx->pSvcParam->iNumRefFrame;
  int32_t i;

  pRefList->pNextBuffer = NULL;
  for (i = 0; i < kiNumRef + 1; i++) {
    if (!pRefList->pRef[i]->bUsedAsRef) {
      pRefList->pNextBuffer = pRefList->pRef[i];
      break;
    }
  }

  if (pRefList->pNextBuffer == NULL && pRefList->uiShortRefCount > 0) {
    pRefList->pNextBuffer = pRefList->pShortRefList[pRefList->uiShortRefCount - 1];
    pRefList->pNextBuffer->SetUnref();
  }

  pCtx->pDecPic = pRefList->pNextBuffer;
}

/*
 *  update reference picture list
 */
bool WelsUpdateRefList (sWelsEncCtx* pCtx) {
  SRefList* pRefList                = pCtx->ppRefPicListExt[pCtx->uiDependencyId];
  SLTRState* pLtr                   = &pCtx->pLtr[pCtx->uiDependencyId];
  SSpatialLayerInternal* pParamD    = &pCtx->pSvcParam->sDependencyLayers[pCtx->uiDependencyId];

  int32_t iRefIdx                   = 0;
  const uint8_t kuiTid              = pCtx->uiTemporalId;
  const uint8_t kuiDid              = pCtx->uiDependencyId;
  const EWelsSliceType keSliceType  = pCtx->eSliceType;
  uint32_t i = 0;
  // Need update pRef list in case store base layer or target dependency layer construction
  if (NULL == pCtx->pCurDqLayer)
    return false;

  if (NULL == pRefList || NULL == pRefList->pRef[0])
    return false;

  if (NULL != pCtx->pDecPic) {
#if !defined(ENABLE_FRAME_DUMP) // to save complexity, 1/6/2009
    if ((pParamD->iHighestTemporalId == 0) || (kuiTid < pParamD->iHighestTemporalId))
#endif// !ENABLE_FRAME_DUMP
      // Expanding picture for future reference
      ExpandReferencingPicture (pCtx->pDecPic->pData, pCtx->pDecPic->iWidthInPixel, pCtx->pDecPic->iHeightInPixel,
                                pCtx->pDecPic->iLineSize,
                                pCtx->pFuncList->sExpandPicFunc.pfExpandLumaPicture, pCtx->pFuncList->sExpandPicFunc.pfExpandChromaPicture);

    // move picture in list
    pCtx->pDecPic->uiTemporalId = kuiTid;
    pCtx->pDecPic->uiSpatialId  = kuiDid;
    pCtx->pDecPic->iFrameNum    = pParamD->iFrameNum;
    pCtx->pDecPic->iFramePoc    = pParamD->iPOC;
    pCtx->pDecPic->uiRecieveConfirmed = RECIEVE_UNKOWN;
    pCtx->pDecPic->bUsedAsRef   = true;

    for (iRefIdx = pRefList->uiShortRefCount - 1; iRefIdx >= 0; --iRefIdx) {
      pRefList->pShortRefList[iRefIdx + 1] = pRefList->pShortRefList[iRefIdx];
    }
    pRefList->pShortRefList[0] = pCtx->pDecPic;
    pRefList->uiShortRefCount++;
  }

  if (keSliceType == P_SLICE) {
    if (pCtx->uiTemporalId == 0) {
      if (pCtx->pSvcParam->bEnableLongTermReference) {
        LTRMarkProcess (pCtx);
        DeleteInvalidLTR (pCtx);
        HandleLTRMarkFeedback (pCtx);

        pLtr->bReceivedT0LostFlag = false; // reset to false due to the recovery is finished
        pLtr->bLTRMarkingFlag = false;
        ++pLtr->uiLtrMarkInterval;
      }

      for (i = pRefList->uiShortRefCount - 1; i > 0; i--) {
        pRefList->pShortRefList[i]->SetUnref();
        DeleteSTRFromShortList (pCtx, i);
      }
      if (pRefList->uiShortRefCount > 0 && (pRefList->pShortRefList[0]->uiTemporalId > 0
                                            || pRefList->pShortRefList[0]->iFrameNum != pParamD->iFrameNum)) {
        pRefList->pShortRefList[0]->SetUnref();
        DeleteSTRFromShortList (pCtx, 0);
      }
    }
  } else { // in case IDR currently coding
    if (pCtx->pSvcParam->bEnableLongTermReference) {
      LTRMarkProcess (pCtx);

      pLtr->iCurLtrIdx = (pLtr->iCurLtrIdx + 1) % LONG_TERM_REF_NUM;
      pLtr->iLTRMarkSuccessNum = 1; //IDR default suceess
      pLtr->bLTRMarkEnable =  true;
      pLtr->uiLtrMarkInterval = 0;

      pCtx->pVaa->uiValidLongTermPicIdx = 0;
      pCtx->pVaa->uiMarkLongTermPicIdx = 0;
    }
  }
  pCtx->pReferenceStrategy->EndofUpdateRefList();
  return true;
}

bool CheckCurMarkFrameNumUsed (sWelsEncCtx* pCtx) {
  SLTRState* pLtr = &pCtx->pLtr[pCtx->uiDependencyId];
  SRefList* pRefList = pCtx->ppRefPicListExt[pCtx->uiDependencyId];
  SPicture** pLongRefList = pRefList->pLongRefList;
  int32_t iGoPFrameNumInterval = ((pCtx->pSvcParam->uiGopSize >> 1) > 1) ? (pCtx->pSvcParam->uiGopSize >> 1) : (1);
  int32_t iMaxFrameNumPlus1 = (1 << pCtx->pSps->uiLog2MaxFrameNum);
  SSpatialLayerInternal* pParamInternal = &pCtx->pSvcParam->sDependencyLayers[pCtx->uiDependencyId];
  int32_t i;

  for (i = 0; i < pRefList->uiLongRefCount; i++) {
    if ((pParamInternal->iFrameNum == pLongRefList[i]->iFrameNum && pLtr->iLTRMarkMode == LTR_DIRECT_MARK) ||
        (CompareFrameNum (pParamInternal->iFrameNum + iGoPFrameNumInterval, pLongRefList[i]->iFrameNum,
                          iMaxFrameNumPlus1) == FRAME_NUM_EQUAL  && pLtr->iLTRMarkMode == LTR_DELAY_MARK)) {
      return false;
    }
  }

  return true;
}

static inline void WelsMarkMMCORefInfoWithBase (SSlice** ppSliceList,
    SSlice* pBaseSlice,
    const int32_t kiCountSliceNum) {
  int32_t iSliceIdx = 0;
  SSliceHeaderExt* pSliceHdrExt = NULL;
  SSliceHeaderExt* pBaseSHExt   = &pBaseSlice->sSliceHeaderExt;

  for (iSliceIdx = 0; iSliceIdx < kiCountSliceNum; iSliceIdx++) {
    pSliceHdrExt = &ppSliceList[iSliceIdx]->sSliceHeaderExt;
    memcpy (&pSliceHdrExt->sSliceHeader.sRefMarking, &pBaseSHExt->sSliceHeader.sRefMarking, sizeof (SRefPicMarking));
  }
}

void WelsMarkMMCORefInfo (sWelsEncCtx* pCtx, SLTRState* pLtr,
                          SSlice** ppSliceList, const int32_t kiCountSliceNum) {
  SSlice* pBaseSlice            = ppSliceList[0];
  SRefPicMarking* pRefPicMark   = &pBaseSlice->sSliceHeaderExt.sSliceHeader.sRefMarking;
  int32_t iGoPFrameNumInterval  = ((pCtx->pSvcParam->uiGopSize >> 1) > 1) ? (pCtx->pSvcParam->uiGopSize >> 1) : (1);

  memset (pRefPicMark, 0, sizeof (SRefPicMarking));

  if (pCtx->pSvcParam->bEnableLongTermReference && pLtr->bLTRMarkingFlag) {
    if (pLtr->iLTRMarkMode == LTR_DIRECT_MARK) {
      pRefPicMark->SMmcoRef[pRefPicMark->uiMmcoCount].iMaxLongTermFrameIdx = LONG_TERM_REF_NUM - 1;
      pRefPicMark->SMmcoRef[pRefPicMark->uiMmcoCount++].iMmcoType = MMCO_SET_MAX_LONG;

      pRefPicMark->SMmcoRef[pRefPicMark->uiMmcoCount].iDiffOfPicNum = iGoPFrameNumInterval;
      pRefPicMark->SMmcoRef[pRefPicMark->uiMmcoCount++].iMmcoType = MMCO_SHORT2UNUSED;

      pRefPicMark->SMmcoRef[pRefPicMark->uiMmcoCount].iLongTermFrameIdx = pLtr->iCurLtrIdx;
      pRefPicMark->SMmcoRef[pRefPicMark->uiMmcoCount++].iMmcoType = MMCO_LONG;
    } else if (pLtr->iLTRMarkMode == LTR_DELAY_MARK) {
      pRefPicMark->SMmcoRef[pRefPicMark->uiMmcoCount].iDiffOfPicNum = iGoPFrameNumInterval;
      pRefPicMark->SMmcoRef[pRefPicMark->uiMmcoCount].iLongTermFrameIdx = pLtr->iCurLtrIdx;
      pRefPicMark->SMmcoRef[pRefPicMark->uiMmcoCount++].iMmcoType = MMCO_SHORT2LONG;
    }
  }

  WelsMarkMMCORefInfoWithBase (ppSliceList, pBaseSlice, kiCountSliceNum);
}

void WelsMarkPic (sWelsEncCtx* pCtx) {
  SLTRState* pLtr               = &pCtx->pLtr[pCtx->uiDependencyId];
  const int32_t kiCountSliceNum = pCtx->pCurDqLayer->iMaxSliceNum;

  if (pCtx->pSvcParam->bEnableLongTermReference && pLtr->bLTRMarkEnable && pCtx->uiTemporalId == 0) {
    if (!pLtr->bReceivedT0LostFlag && pLtr->uiLtrMarkInterval > pCtx->pSvcParam->iLtrMarkPeriod
        && CheckCurMarkFrameNumUsed (pCtx)) {
      pLtr->bLTRMarkingFlag = true;
      pLtr->bLTRMarkEnable = false;
      pLtr->uiLtrMarkInterval = 0;
      for (int32_t i = 0 ; i < MAX_TEMPORAL_LAYER_NUM; ++i) {
        if (pCtx->uiTemporalId < i || pCtx->uiTemporalId == 0) {
          pLtr->iLastLtrIdx[i] = pLtr->iCurLtrIdx;
        }
      }
    } else {
      pLtr->bLTRMarkingFlag = false;
    }
  }

  WelsMarkMMCORefInfo (pCtx, pLtr, pCtx->pCurDqLayer->ppSliceInLayer, kiCountSliceNum);
}

int32_t FilterLTRRecoveryRequest (sWelsEncCtx* pCtx, SLTRRecoverRequest* pLTRRecoverRequest) {
  //if disable LTR, force IDR
  if (!pCtx->pSvcParam->bEnableLongTermReference) {
    for (int32_t iDid = 0; iDid < pCtx->pSvcParam->iSpatialLayerNum; iDid++) {
      SSpatialLayerInternal* pParamInternal = &pCtx->pSvcParam->sDependencyLayers[iDid];
      pParamInternal->bEncCurFrmAsIdrFlag = true;
    }
  } else {
    SLTRRecoverRequest* pRequest = pLTRRecoverRequest;
    int32_t iLayerId = pLTRRecoverRequest->iLayerId;
    if ((iLayerId < 0) || (iLayerId >= pCtx->pSvcParam->iSpatialLayerNum))
      return false;

    SLTRState* pLtr = &pCtx->pLtr[iLayerId];
    int32_t iMaxFrameNumPlus1 = (1 << pCtx->pSps->uiLog2MaxFrameNum);
    SSpatialLayerInternal* pParamInternal    = &pCtx->pSvcParam->sDependencyLayers[iLayerId];
    if (pRequest->uiFeedbackType == LTR_RECOVERY_REQUEST &&  pRequest->uiIDRPicId == pParamInternal->uiIdrPicId) {
      if (pRequest->iLastCorrectFrameNum == -1) {
        pParamInternal->bEncCurFrmAsIdrFlag = true;
        return true;
      } else if (pRequest->iCurrentFrameNum == -1) {
        pLtr->bReceivedT0LostFlag = true;
        return true;
      } else if ((CompareFrameNum (pLtr->iLastRecoverFrameNum , pRequest->iLastCorrectFrameNum,
                                   iMaxFrameNumPlus1) & (FRAME_NUM_EQUAL | FRAME_NUM_SMALLER)) // t0 lost
                 || ((CompareFrameNum (pLtr->iLastRecoverFrameNum , pRequest->iCurrentFrameNum,
                                       iMaxFrameNumPlus1) & (FRAME_NUM_EQUAL | FRAME_NUM_SMALLER)) &&
                     CompareFrameNum (pLtr->iLastRecoverFrameNum , pRequest->iLastCorrectFrameNum,
                                      iMaxFrameNumPlus1) == FRAME_NUM_BIGGER)) { // recovery failed

        pLtr->bReceivedT0LostFlag = true;
        pLtr->iLastCorFrameNumDec = pRequest->iLastCorrectFrameNum;
        pLtr->iCurFrameNumInDec = pRequest->iCurrentFrameNum;
        WelsLog (& (pCtx->sLogCtx), WELS_LOG_INFO,
                 "Receive valid LTR recovery pRequest,feedback_type = %d ,uiIdrPicId = %d , current_frame_num = %d , last correct frame num = %d"
                 , pRequest->uiFeedbackType, pRequest->uiIDRPicId, pRequest->iCurrentFrameNum, pRequest->iLastCorrectFrameNum);
      }

      WelsLog (& (pCtx->sLogCtx), WELS_LOG_INFO,
               "Receive LTR recovery pRequest,feedback_type = %d ,uiIdrPicId = %d , current_frame_num = %d , last correct frame num = %d"
               , pRequest->uiFeedbackType, pRequest->uiIDRPicId, pRequest->iCurrentFrameNum, pRequest->iLastCorrectFrameNum);
    }
  }

  return true;
}
void FilterLTRMarkingFeedback (sWelsEncCtx* pCtx, SLTRMarkingFeedback* pLTRMarkingFeedback) {
  int32_t iLayerId = pLTRMarkingFeedback->iLayerId;
  if ((iLayerId < 0) || (iLayerId >= pCtx->pSvcParam->iSpatialLayerNum)) {
    return;
  }
  SLTRState* pLtr = &pCtx->pLtr[iLayerId];
  assert (pLTRMarkingFeedback);
  if (pCtx->pSvcParam->bEnableLongTermReference) {
    SSpatialLayerInternal* pParamInternal    = &pCtx->pSvcParam->sDependencyLayers[iLayerId];
    if (pLTRMarkingFeedback->uiIDRPicId == pParamInternal->uiIdrPicId
        && (pLTRMarkingFeedback->uiFeedbackType == LTR_MARKING_SUCCESS
            || pLTRMarkingFeedback->uiFeedbackType == LTR_MARKING_FAILED)) { // avoid error pData
      pLtr->uiLtrMarkState = pLTRMarkingFeedback->uiFeedbackType;
      pLtr->iLtrMarkFbFrameNum =  pLTRMarkingFeedback->iLTRFrameNum ;
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_INFO,
               "Receive valid LTR marking feedback, feedback_type = %d , uiIdrPicId = %d , LTR_frame_num = %d , cur_idr_pic_id = %d",
               pLTRMarkingFeedback->uiFeedbackType, pLTRMarkingFeedback->uiIDRPicId, pLTRMarkingFeedback->iLTRFrameNum ,
               pParamInternal->uiIdrPicId);

    } else {
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_INFO,
               "Receive LTR marking feedback, feedback_type = %d , uiIdrPicId = %d , LTR_frame_num = %d , cur_idr_pic_id = %d",
               pLTRMarkingFeedback->uiFeedbackType, pLTRMarkingFeedback->uiIDRPicId, pLTRMarkingFeedback->iLTRFrameNum ,
               pParamInternal->uiIdrPicId);
    }
  }
}

/*
 *  build reference picture list
 */
bool WelsBuildRefList (sWelsEncCtx* pCtx, const int32_t iPOC, int32_t iBestLtrRefIdx) {
  SRefList* pRefList            = pCtx->ppRefPicListExt[pCtx->uiDependencyId];
  SLTRState* pLtr               = &pCtx->pLtr[pCtx->uiDependencyId];
  const int32_t kiNumRef        = pCtx->pSvcParam->iNumRefFrame;
  const uint8_t kuiTid          = pCtx->uiTemporalId;
  uint32_t i                    = 0;
  SSpatialLayerInternal* pParamD    = &pCtx->pSvcParam->sDependencyLayers[pCtx->uiDependencyId];
  // to support any type of cur_dq->mgs_control
  //    [ 0:    using current layer to do ME/MC;
  //     -1:    using store base layer to do ME/MC;
  //      2:    using highest layer to do ME/MC; ]

  // build reference list 0/1 if applicable

  pCtx->iNumRef0 = 0;
  if (pCtx->eSliceType != I_SLICE) {
    if (pCtx->pSvcParam->bEnableLongTermReference && pLtr->bReceivedT0LostFlag && pCtx->uiTemporalId == 0) {
      for (i = 0; i < pRefList->uiLongRefCount; i++) {
        if (pRefList->pLongRefList[i]->uiRecieveConfirmed == RECIEVE_SUCCESS) {
          pCtx->pCurDqLayer->pRefOri[pCtx->iNumRef0] = pRefList->pLongRefList[i];
          pCtx->pRefList0[pCtx->iNumRef0++] = pRefList->pLongRefList[i];
          pLtr->iLastRecoverFrameNum = pParamD->iFrameNum;
          WelsLog (& (pCtx->sLogCtx), WELS_LOG_INFO,
                   "pRef is int32_t !iLastRecoverFrameNum = %d, pRef iFrameNum = %d,LTR number = %d,",
                   pLtr->iLastRecoverFrameNum, pCtx->pRefList0[0]->iFrameNum, pRefList->uiLongRefCount);
          break;
        }
      }
    } else {
      for (i = 0; i < pRefList->uiShortRefCount; ++ i) {
        SPicture* pRef = pRefList->pShortRefList[i];
        if (pRef != NULL && pRef->bUsedAsRef && pRef->iFramePoc >= 0 && pRef->uiTemporalId <= kuiTid) {
          pCtx->pCurDqLayer->pRefOri[pCtx->iNumRef0] = pRef;
          pCtx->pRefList0[pCtx->iNumRef0++] = pRef;
          WelsLog (& (pCtx->sLogCtx), WELS_LOG_DETAIL,
                   "WelsBuildRefList pCtx->uiTemporalId = %d,pRef->iFrameNum = %d,pRef->uiTemporalId = %d",
                   pCtx->uiTemporalId, pRef->iFrameNum, pRef->uiTemporalId);
        }
      }
    }
  } else { // safe for IDR
    WelsResetRefList (pCtx);  //for IDR, SHOULD reset pRef list.
    ResetLtrState (&pCtx->pLtr[pCtx->uiDependencyId]); //SHOULD update it when IDR.
    for (int32_t k = 0; k < MAX_TEMPORAL_LEVEL; k++) {
      pCtx->bRefOfCurTidIsLtr[pCtx->uiDependencyId][k] = false;
    }
    pCtx->pRefList0[0] = NULL;
  }

  if (pCtx->iNumRef0 > kiNumRef)
    pCtx->iNumRef0 = kiNumRef;
  return (pCtx->iNumRef0 > 0 || pCtx->eSliceType == I_SLICE) ? (true) : (false);
}

static void UpdateBlockStatic (sWelsEncCtx* pCtx) {
  SVAAFrameInfoExt* pVaaExt = static_cast<SVAAFrameInfoExt*> (pCtx->pVaa);
  assert (pCtx->iNumRef0 == 1); //multi-ref is not support yet?
  for (int32_t idx = 0; idx < pCtx->iNumRef0; idx++) {
    //TODO: we need to re-factor the source picture storage first,
    //and then use original frame of the ref to do this calculation for better vaa algo implementation
    SPicture* pRef = pCtx->pRefList0[idx];
    if (pVaaExt->iVaaBestRefFrameNum != pRef->iFrameNum) {
      //re-do the calculation
      pCtx->pVpp->UpdateBlockIdcForScreen (pVaaExt->pVaaBestBlockStaticIdc, pRef, pCtx->pEncPic);
    }
  }
}

void WelsUpdateSliceHeaderSyntax (sWelsEncCtx* pCtx,  const int32_t iAbsDiffPicNumMinus1,
                                  SSlice** ppSliceList, const int32_t uiFrameType) {
  const int32_t kiCountSliceNum = pCtx->pCurDqLayer->iMaxSliceNum;
  SLTRState* pLtr               = &pCtx->pLtr[pCtx->uiDependencyId];
  int32_t iIdx = 0;

  assert (kiCountSliceNum > 0);

  for (iIdx = 0; iIdx < kiCountSliceNum; iIdx++) {
    SSliceHeaderExt*    pSliceHdrExt = &ppSliceList[iIdx]->sSliceHeaderExt;
    SSliceHeader*       pSliceHdr = &pSliceHdrExt->sSliceHeader;
    SRefPicListReorderSyntax* pRefReorder = &pSliceHdr->sRefReordering;
    SRefPicMarking* pRefPicMark = &pSliceHdr->sRefMarking;

    /*syntax for num_ref_idx_l0_active_minus1*/
    pSliceHdr->uiRefCount = pCtx->iNumRef0;
    if (pCtx->iNumRef0 > 0) {
      if ((!pCtx->pRefList0[0]->bIsLongRef) || (!pCtx->pSvcParam->bEnableLongTermReference)) {
        pRefReorder->SReorderingSyntax[0].uiReorderingOfPicNumsIdc = 0;
        pRefReorder->SReorderingSyntax[0].uiAbsDiffPicNumMinus1 = iAbsDiffPicNumMinus1;
        pRefReorder->SReorderingSyntax[1].uiReorderingOfPicNumsIdc = 3;
      } else {
        int32_t iRefIdx = 0;
        for (iRefIdx = 0; iRefIdx < pCtx->iNumRef0; iRefIdx++) {
          pRefReorder->SReorderingSyntax[iRefIdx].uiReorderingOfPicNumsIdc = 2;
          pRefReorder->SReorderingSyntax[iRefIdx].iLongTermPicNum = pCtx->pRefList0[iRefIdx]->iLongTermPicNum;
        }
        pRefReorder->SReorderingSyntax[iRefIdx].uiReorderingOfPicNumsIdc = 3;
      }
    }

    /*syntax for dec_ref_pic_marking()*/
    if (videoFrameTypeIDR == uiFrameType) {
      pRefPicMark->bNoOutputOfPriorPicsFlag = false;
      pRefPicMark->bLongTermRefFlag = pCtx->pSvcParam->bEnableLongTermReference;
    } else {
      if (pCtx->pSvcParam->iUsageType == SCREEN_CONTENT_REAL_TIME)
        pRefPicMark->bAdaptiveRefPicMarkingModeFlag = pCtx->pSvcParam->bEnableLongTermReference;
      else
        pRefPicMark->bAdaptiveRefPicMarkingModeFlag = (pCtx->pSvcParam->bEnableLongTermReference
            && pLtr->bLTRMarkingFlag) ? (true) : (false);
    }
  }
}

/*
 *  update syntax for reference base related
 */
void WelsUpdateRefSyntax (sWelsEncCtx* pCtx, const int32_t iPOC, const int32_t uiFrameType) {
  int32_t iAbsDiffPicNumMinus1   = -1;
  SSpatialLayerInternal* pParamD    = &pCtx->pSvcParam->sDependencyLayers[pCtx->uiDependencyId];
  /*syntax for ref_pic_list_reordering()*/
  if (pCtx->iNumRef0 > 0) {
    iAbsDiffPicNumMinus1 = pParamD->iFrameNum - (pCtx->pRefList0[0]->iFrameNum) - 1;

    if (iAbsDiffPicNumMinus1 < 0) {
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_INFO, "WelsUpdateRefSyntax():::uiAbsDiffPicNumMinus1:%d", iAbsDiffPicNumMinus1);
      iAbsDiffPicNumMinus1 += (1 << (pCtx->pSps->uiLog2MaxFrameNum));
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_INFO, "WelsUpdateRefSyntax():::uiAbsDiffPicNumMinus1< 0, update as:%d",
               iAbsDiffPicNumMinus1);
    }
  }

  WelsUpdateSliceHeaderSyntax (pCtx, iAbsDiffPicNumMinus1, pCtx->pCurDqLayer->ppSliceInLayer, uiFrameType);
}

static inline void UpdateOriginalPicInfo (SPicture* pOrigPic, SPicture* pReconPic) {
  if (!pOrigPic)
    return;

  pOrigPic->iPictureType = pReconPic->iPictureType;
  pOrigPic->iFramePoc = pReconPic->iFramePoc;
  pOrigPic->iFrameNum = pReconPic->iFrameNum;
  pOrigPic->uiSpatialId = pReconPic->uiSpatialId;
  pOrigPic->uiTemporalId = pReconPic->uiTemporalId;
  pOrigPic->iLongTermPicNum = pReconPic->iLongTermPicNum;
  pOrigPic->bUsedAsRef = pReconPic->bUsedAsRef;
  pOrigPic->bIsLongRef = pReconPic->bIsLongRef;
  pOrigPic->bIsSceneLTR = pReconPic->bIsSceneLTR;
  pOrigPic->iFrameAverageQp = pReconPic->iFrameAverageQp;
}

static void UpdateSrcPicListLosslessScreenRefSelectionWithLtr (sWelsEncCtx* pCtx) {
  int32_t iDIdx = pCtx->uiDependencyId;
  //update info in src list
  UpdateOriginalPicInfo (pCtx->pEncPic, pCtx->pDecPic);
  PrefetchNextBuffer (pCtx);
  pCtx->pVpp->UpdateSrcListLosslessScreenRefSelectionWithLtr (pCtx->pEncPic, iDIdx,  pCtx->pVaa->uiMarkLongTermPicIdx,
      pCtx->ppRefPicListExt[iDIdx]->pLongRefList);
}

static void UpdateSrcPicList (sWelsEncCtx* pCtx) {
  int32_t iDIdx = pCtx->uiDependencyId;
  //update info in src list
  UpdateOriginalPicInfo (pCtx->pEncPic, pCtx->pDecPic);
  PrefetchNextBuffer (pCtx);
  pCtx->pVpp->UpdateSrcList (pCtx->pEncPic, iDIdx, pCtx->ppRefPicListExt[iDIdx]->pShortRefList,
                             pCtx->ppRefPicListExt[iDIdx]->uiShortRefCount);
}

bool WelsUpdateRefListScreen (sWelsEncCtx* pCtx) {
  SRefList* pRefList                = pCtx->ppRefPicListExt[pCtx->uiDependencyId];
  SLTRState* pLtr                   = &pCtx->pLtr[pCtx->uiDependencyId];
  SSpatialLayerInternal* pParamD    = &pCtx->pSvcParam->sDependencyLayers[pCtx->uiDependencyId];
  const uint8_t kuiTid              = pCtx->uiTemporalId;
  // Need update ref list in case store base layer or target dependency layer construction
  if (NULL == pCtx->pCurDqLayer)
    return false;

  if (NULL == pRefList || NULL == pRefList->pRef[0])
    return false;

  if (NULL != pCtx->pDecPic) {
#if !defined(ENABLE_FRAME_DUMP) // to save complexity, 1/6/2009
    if ((pParamD->iHighestTemporalId == 0) || (kuiTid < pParamD->iHighestTemporalId))
#endif// !ENABLE_FRAME_DUMP
      // Expanding picture for future reference
      ExpandReferencingPicture (pCtx->pDecPic->pData, pCtx->pDecPic->iWidthInPixel, pCtx->pDecPic->iHeightInPixel,
                                pCtx->pDecPic->iLineSize,
                                pCtx->pFuncList->sExpandPicFunc.pfExpandLumaPicture, pCtx->pFuncList->sExpandPicFunc.pfExpandChromaPicture);

    // move picture in list
    pCtx->pDecPic->uiTemporalId = pCtx->uiTemporalId;
    pCtx->pDecPic->uiSpatialId  = pCtx->uiDependencyId;
    pCtx->pDecPic->iFrameNum    = pParamD->iFrameNum;
    pCtx->pDecPic->iFramePoc    = pParamD->iPOC;
    pCtx->pDecPic->bUsedAsRef   = true;
    pCtx->pDecPic->bIsLongRef   = true;
    pCtx->pDecPic->bIsSceneLTR  = pLtr->bLTRMarkingFlag || (pCtx->pSvcParam->bEnableLongTermReference
                                  && pCtx->eSliceType == I_SLICE);
    pCtx->pDecPic->iLongTermPicNum = pLtr->iCurLtrIdx;
  }
  if (pCtx->eSliceType == P_SLICE) {
    DeleteNonSceneLTR (pCtx);
    LTRMarkProcessScreen (pCtx);
    pLtr->bLTRMarkingFlag = false;
    ++pLtr->uiLtrMarkInterval;
  } else { // in case IDR currently coding
    LTRMarkProcessScreen (pCtx);
    pLtr->iCurLtrIdx = 1;
    pLtr->iSceneLtrIdx = 1;
    pLtr->uiLtrMarkInterval = 0;
    pCtx->pVaa->uiValidLongTermPicIdx = 0;
  }

  pCtx->pReferenceStrategy->EndofUpdateRefList();
  return true;
}

bool WelsBuildRefListScreen (sWelsEncCtx* pCtx, const int32_t iPOC, int32_t iBestLtrRefIdx) {
  SRefList* pRefList = pCtx->ppRefPicListExt[pCtx->uiDependencyId];
  SWelsSvcCodingParam* pParam = pCtx->pSvcParam;
  SVAAFrameInfoExt* pVaaExt = static_cast<SVAAFrameInfoExt*> (pCtx->pVaa);
  const int32_t iNumRef = pParam->iNumRefFrame;
  SSpatialLayerInternal* pParamD    = &pCtx->pSvcParam->sDependencyLayers[pCtx->uiDependencyId];
  pCtx->iNumRef0 = 0;

  if (pCtx->eSliceType != I_SLICE) {
    int iLtrRefIdx = 0;
    SPicture* pRefOri = NULL;
    for (int idx = 0; idx < pVaaExt->iNumOfAvailableRef; idx++) {
      iLtrRefIdx = pCtx->pVpp->GetRefFrameInfo (idx, pCtx->bCurFrameMarkedAsSceneLtr, pRefOri);
      if (iLtrRefIdx >= 0 && iLtrRefIdx <= pParam->iLTRRefNum) {
        SPicture* pRefPic = pRefList->pLongRefList[iLtrRefIdx];
        if (pRefPic != NULL && pRefPic->bUsedAsRef && pRefPic->bIsLongRef) {
          if (pRefPic->uiTemporalId <= pCtx->uiTemporalId && (!pCtx->bCurFrameMarkedAsSceneLtr || pRefPic->bIsSceneLTR)) {
            pCtx->pCurDqLayer->pRefOri[pCtx->iNumRef0] = pRefOri;
            pCtx->pRefList0[pCtx->iNumRef0++] = pRefPic;
            WelsLog (& (pCtx->sLogCtx), WELS_LOG_DEBUG,
                     "WelsBuildRefListScreen(), current iFrameNum = %d, current Tid = %d, ref iFrameNum = %d, ref uiTemporalId = %d, ref is Scene LTR = %d, LTR count = %d,iNumRef = %d",
                     pParamD->iFrameNum, pCtx->uiTemporalId,
                     pRefPic->iFrameNum, pRefPic->uiTemporalId, pRefPic->bIsSceneLTR,
                     pRefList->uiLongRefCount, iNumRef);
          }
        }
      } else {
        for (int32_t i = iNumRef ; i >= 0 ; --i) {
          if (pRefList->pLongRefList[i] == NULL) {
            continue;
          } else if (pRefList->pLongRefList[i]->uiTemporalId == 0
                     || pRefList->pLongRefList[i]->uiTemporalId < pCtx->uiTemporalId) {
            pCtx->pCurDqLayer->pRefOri[pCtx->iNumRef0] = pRefOri;
            pCtx->pRefList0[pCtx->iNumRef0++] = pRefList->pLongRefList[i];
            WelsLog (& (pCtx->sLogCtx), WELS_LOG_DEBUG,
                     "WelsBuildRefListScreen(), ref !current iFrameNum = %d, ref iFrameNum = %d,LTR number = %d",
                     pParamD->iFrameNum, pCtx->pRefList0[pCtx->iNumRef0 - 1]->iFrameNum, pRefList->uiLongRefCount);
            break;
          }
        }
      }
    } // end of (int idx = 0; idx < pVaaExt->iNumOfAvailableRef; idx++)

    WelsLog (& (pCtx->sLogCtx), WELS_LOG_DEBUG,
             "WelsBuildRefListScreen(), CurrentFramePoc=%d, isLTR=%d", iPOC, pCtx->bCurFrameMarkedAsSceneLtr);
    for (int j = 0; j < iNumRef; j++) {
      SPicture* pARefPicture = pRefList->pLongRefList[j];
      if (pARefPicture != NULL) {
        WelsLog (& (pCtx->sLogCtx), WELS_LOG_DEBUG,
                 "WelsBuildRefListScreen()\tRefLot[%d]: iPoc=%d, iPictureType=%d, bUsedAsRef=%d, bIsLongRef=%d, bIsSceneLTR=%d, uiTemporalId=%d, iFrameNum=%d, iMarkFrameNum=%d, iLongTermPicNum=%d, uiRecieveConfirmed=%d",
                 j,
                 pARefPicture->iFramePoc,
                 pARefPicture->iPictureType,
                 pARefPicture->bUsedAsRef,
                 pARefPicture->bIsLongRef,
                 pARefPicture->bIsSceneLTR,
                 pARefPicture->uiTemporalId,
                 pARefPicture->iFrameNum,
                 pARefPicture->iMarkFrameNum,
                 pARefPicture->iLongTermPicNum,
                 pARefPicture->uiRecieveConfirmed);
      } else {
        WelsLog (& (pCtx->sLogCtx), WELS_LOG_DEBUG, "WelsBuildRefListScreen()\tRefLot[%d]: NULL", j);
      }
    }
  } else {
    // dealing with IDR
    WelsResetRefList (pCtx);  //for IDR, SHOULD reset pRef list.
    ResetLtrState (&pCtx->pLtr[pCtx->uiDependencyId]); //SHOULD update it when IDR.
    pCtx->pRefList0[0] = NULL;
  }
  if (pCtx->iNumRef0 > iNumRef) {
    pCtx->iNumRef0 = iNumRef;
  }

  return (pCtx->iNumRef0 > 0 || pCtx->eSliceType == I_SLICE) ? (true) : (false);
}

static inline bool IsValidFrameNum (const int32_t kiFrameNum) {
  return (kiFrameNum < (1 << 30)); // TODO: use the original judge first, may be improved
}

void WelsMarkMMCORefInfoScreen (sWelsEncCtx* pCtx, SLTRState* pLtr,
                                SSlice** ppSliceList, const int32_t kiCountSliceNum) {
  SSlice* pBaseSlice          = ppSliceList[0];
  SRefPicMarking* pRefPicMark = &pBaseSlice->sSliceHeaderExt.sSliceHeader.sRefMarking;
  const int32_t iMaxLtrIdx = pCtx->pSvcParam->iNumRefFrame - STR_ROOM - 1;

  memset (pRefPicMark, 0, sizeof (SRefPicMarking));
  if (pCtx->pSvcParam->bEnableLongTermReference) {
    pRefPicMark->SMmcoRef[pRefPicMark->uiMmcoCount].iMaxLongTermFrameIdx = iMaxLtrIdx;
    pRefPicMark->SMmcoRef[pRefPicMark->uiMmcoCount++].iMmcoType = MMCO_SET_MAX_LONG;

    pRefPicMark->SMmcoRef[pRefPicMark->uiMmcoCount].iLongTermFrameIdx = pLtr->iCurLtrIdx;
    pRefPicMark->SMmcoRef[pRefPicMark->uiMmcoCount++].iMmcoType = MMCO_LONG;
  }

  WelsMarkMMCORefInfoWithBase (ppSliceList, pBaseSlice, kiCountSliceNum);
}

void WelsMarkPicScreen (sWelsEncCtx* pCtx) {
  SLTRState* pLtr          = &pCtx->pLtr[pCtx->uiDependencyId];
  int32_t iMaxTid          = WELS_LOG2 (pCtx->pSvcParam->uiGopSize);
  int32_t iMaxActualLtrIdx = -1;
  SSpatialLayerInternal* pParamD    = &pCtx->pSvcParam->sDependencyLayers[pCtx->uiDependencyId];
  if (pCtx->pSvcParam->bEnableLongTermReference)
    iMaxActualLtrIdx = pCtx->pSvcParam->iNumRefFrame - STR_ROOM - 1 -  WELS_MAX (iMaxTid , 1);

  SRefList* pRefList =  pCtx->ppRefPicListExt[pCtx->uiDependencyId];
  SPicture** ppLongRefList = pRefList->pLongRefList;
  const int32_t iNumRef = pCtx->pSvcParam->iNumRefFrame;
  int32_t i;
  const int32_t iLongRefNum = iNumRef - STR_ROOM;
  const bool bIsRefListNotFull = pRefList->uiLongRefCount < iLongRefNum;

  if (!pCtx->pSvcParam->bEnableLongTermReference) {
    pLtr->iCurLtrIdx = pCtx->uiTemporalId;
  } else {
    if (iMaxActualLtrIdx != -1 && pCtx->uiTemporalId == 0 && pCtx->bCurFrameMarkedAsSceneLtr) {
      //Scene LTR
      pLtr->bLTRMarkingFlag = true;
      pLtr->uiLtrMarkInterval = 0;
      pLtr->iCurLtrIdx  = pLtr->iSceneLtrIdx % (iMaxActualLtrIdx + 1);
      pLtr->iSceneLtrIdx++;
    } else {
      pLtr->bLTRMarkingFlag = false;
      //for other LTR
      if (bIsRefListNotFull) {
        for (int32_t i = 0; i < iLongRefNum; ++i) {
          if (pRefList->pLongRefList[i] == NULL) {
            pLtr->iCurLtrIdx = i   ;
            break;
          }
        }
      } else {
        int32_t iRefNum_t[MAX_TEMPORAL_LAYER_NUM] = {0};
        for (i = 0 ; i < pRefList->uiLongRefCount ; ++i) {
          if (ppLongRefList[i]->bUsedAsRef && ppLongRefList[i]->bIsLongRef && (!ppLongRefList[i]->bIsSceneLTR)) {
            ++iRefNum_t[ ppLongRefList[i]->uiTemporalId ];
          }
        }

        int32_t iMaxMultiRefTid = (iMaxTid) ? (iMaxTid - 1) : (0) ;
        for (i = 0; i < MAX_TEMPORAL_LAYER_NUM ; ++i) {
          if (iRefNum_t[i] > 1) {
            iMaxMultiRefTid = i;
          }
        }
        int32_t iLongestDeltaFrameNum = -1;
        int32_t iMaxFrameNum = (1 << pCtx->pSps->uiLog2MaxFrameNum);

        for (i = 0 ; i < pRefList->uiLongRefCount ; ++i) {
          if (ppLongRefList[i]->bUsedAsRef && ppLongRefList[i]->bIsLongRef && (!ppLongRefList[i]->bIsSceneLTR)
              && iMaxMultiRefTid == ppLongRefList[i]->uiTemporalId) {
            if (!IsValidFrameNum (ppLongRefList[i]->iFrameNum)) {  // pLtr->iCurLtrIdx must have a value
              WelsLog (& (pCtx->sLogCtx), WELS_LOG_ERROR, "WelsMarkPicScreen, Invalid Frame Number");
              return;
            }
            int32_t iDeltaFrameNum = (pParamD->iFrameNum >= ppLongRefList[i]->iFrameNum)
                                     ? (pParamD->iFrameNum - ppLongRefList[i]->iFrameNum)
                                     : (pParamD->iFrameNum + iMaxFrameNum - ppLongRefList[i]->iFrameNum);

            if (iDeltaFrameNum > iLongestDeltaFrameNum) {
              pLtr->iCurLtrIdx = ppLongRefList[i]->iLongTermPicNum;
              iLongestDeltaFrameNum = iDeltaFrameNum;
            }
          }
        }
      }
    }
  }

  for (i = 0 ; i < MAX_TEMPORAL_LAYER_NUM; ++i) {
    if ((pCtx->uiTemporalId <  i) || (pCtx->uiTemporalId == 0)) {
      pLtr->iLastLtrIdx[i] = pLtr->iCurLtrIdx;
    }
  }

  const int32_t iSliceNum = pCtx->pCurDqLayer->iMaxSliceNum;

  WelsMarkMMCORefInfoScreen (pCtx, pLtr, pCtx->pCurDqLayer->ppSliceInLayer, iSliceNum);

  return;
}

void DoNothing (sWelsEncCtx* pointer) {
}


IWelsReferenceStrategy*   IWelsReferenceStrategy::CreateReferenceStrategy (sWelsEncCtx* pCtx,
    const EUsageType keUsageType,
    const bool kbLtrEnabled) {

  IWelsReferenceStrategy* pReferenceStrategy = NULL;
  switch (keUsageType) {
  case SCREEN_CONTENT_REAL_TIME:
    if (kbLtrEnabled) {
      pReferenceStrategy = WELS_NEW_OP (CWelsReference_LosslessWithLtr(),
                                        CWelsReference_LosslessWithLtr);
    } else {
      pReferenceStrategy = WELS_NEW_OP (CWelsReference_Screen(),
                                        CWelsReference_Screen);
    }
    WELS_VERIFY_RETURN_IF (NULL, NULL == pReferenceStrategy)
    break;
  case CAMERA_VIDEO_REAL_TIME:
  case CAMERA_VIDEO_NON_REAL_TIME:
  default:
    pReferenceStrategy = WELS_NEW_OP (CWelsReference_TemporalLayer(),
                                      CWelsReference_TemporalLayer);
    WELS_VERIFY_RETURN_IF (NULL, NULL == pReferenceStrategy)
    break;
  }
  pReferenceStrategy->Init (pCtx);
  return pReferenceStrategy;
}

void CWelsReference_TemporalLayer::Init (sWelsEncCtx* pCtx) {
  m_pEncoderCtx = pCtx;
}

bool CWelsReference_TemporalLayer::BuildRefList (const int32_t iPOC, int32_t iBestLtrRefIdx) {
  return WelsBuildRefList (m_pEncoderCtx, iPOC,  iBestLtrRefIdx);
}
void CWelsReference_TemporalLayer::MarkPic() {
  WelsMarkPic (m_pEncoderCtx);
}
bool CWelsReference_TemporalLayer::UpdateRefList() {
  return WelsUpdateRefList (m_pEncoderCtx);
}
void CWelsReference_TemporalLayer::EndofUpdateRefList() {
  PrefetchNextBuffer (m_pEncoderCtx);
}
void CWelsReference_TemporalLayer::AfterBuildRefList() {
  DoNothing (m_pEncoderCtx);
}

bool CWelsReference_Screen::BuildRefList (const int32_t iPOC, int32_t iBestLtrRefIdx) {
  return WelsBuildRefList (m_pEncoderCtx, iPOC,  iBestLtrRefIdx);
}
void CWelsReference_Screen::MarkPic() {
  WelsMarkPic (m_pEncoderCtx);
}
bool CWelsReference_Screen::UpdateRefList() {
  return WelsUpdateRefList (m_pEncoderCtx);
}
void CWelsReference_Screen::EndofUpdateRefList() {
  UpdateSrcPicList (m_pEncoderCtx);
}
void CWelsReference_Screen::AfterBuildRefList() {
  UpdateBlockStatic (m_pEncoderCtx);
}

bool CWelsReference_LosslessWithLtr::BuildRefList (const int32_t iPOC, int32_t iBestLtrRefIdx) {
  return WelsBuildRefListScreen (m_pEncoderCtx, iPOC,  iBestLtrRefIdx);
}
void CWelsReference_LosslessWithLtr::MarkPic() {
  WelsMarkPicScreen (m_pEncoderCtx);
}
bool CWelsReference_LosslessWithLtr::UpdateRefList() {
  return WelsUpdateRefListScreen (m_pEncoderCtx);
}
void CWelsReference_LosslessWithLtr::EndofUpdateRefList() {
  UpdateSrcPicListLosslessScreenRefSelectionWithLtr (m_pEncoderCtx);
}
} // namespace WelsEnc

