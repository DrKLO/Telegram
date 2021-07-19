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
 *      decoder_core.c: Wels decoder framework core implementation
 */

#include "decoder_core.h"
#include "error_code.h"
#include "memmgr_nal_unit.h"
#include "au_parser.h"
#include "decode_slice.h"
#include "manage_dec_ref.h"
#include "expand_pic.h"
#include "decoder.h"
#include "decode_mb_aux.h"
#include "memory_align.h"
#include "error_concealment.h"

namespace WelsDec {
static inline int32_t DecodeFrameConstruction (PWelsDecoderContext pCtx, uint8_t** ppDst, SBufferInfo* pDstInfo) {
  PDqLayer pCurDq = pCtx->pCurDqLayer;
  PPicture pPic = pCtx->pDec;

  const int32_t kiWidth = pCurDq->iMbWidth << 4;
  const int32_t kiHeight = pCurDq->iMbHeight << 4;

  const int32_t kiTotalNumMbInCurLayer = pCurDq->iMbWidth * pCurDq->iMbHeight;
  bool bFrameCompleteFlag = true;

  if (pPic->bNewSeqBegin) {
    memcpy (& (pCtx->sFrameCrop), & (pCurDq->sLayerInfo.sSliceInLayer.sSliceHeaderExt.sSliceHeader.pSps->sFrameCrop),
            sizeof (SPosOffset)); //confirmed_safe_unsafe_usage
#ifdef LONG_TERM_REF
    pCtx->bParamSetsLostFlag      = false;
#else
    pCtx->bReferenceLostAtT0Flag = false; // need initialize it due new seq, 6/4/2010
#endif //LONG_TERM_REF
    if (pCtx->iTotalNumMbRec == kiTotalNumMbInCurLayer) {
      pCtx->bPrintFrameErrorTraceFlag = true;
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_INFO,
               "DecodeFrameConstruction(): will output first frame of new sequence, %d x %d, crop_left:%d, crop_right:%d, crop_top:%d, crop_bottom:%d, ignored error packet:%d.",
               kiWidth, kiHeight, pCtx->sFrameCrop.iLeftOffset, pCtx->sFrameCrop.iRightOffset, pCtx->sFrameCrop.iTopOffset,
               pCtx->sFrameCrop.iBottomOffset, pCtx->iIgnoredErrorInfoPacketCount);
      pCtx->iIgnoredErrorInfoPacketCount = 0;
    }
  }

  const int32_t kiActualWidth = kiWidth - (pCtx->sFrameCrop.iLeftOffset + pCtx->sFrameCrop.iRightOffset) * 2;
  const int32_t kiActualHeight = kiHeight - (pCtx->sFrameCrop.iTopOffset + pCtx->sFrameCrop.iBottomOffset) * 2;


  if (pCtx->pParam->eEcActiveIdc == ERROR_CON_DISABLE) {
    if ((pCtx->pDecoderStatistics->uiWidth != (unsigned int) kiActualWidth)
        || (pCtx->pDecoderStatistics->uiHeight != (unsigned int) kiActualHeight)) {
      pCtx->pDecoderStatistics->uiResolutionChangeTimes++;
      pCtx->pDecoderStatistics->uiWidth = kiActualWidth;
      pCtx->pDecoderStatistics->uiHeight = kiActualHeight;
    }
    UpdateDecStatNoFreezingInfo (pCtx);
  }

  if (pCtx->pParam->bParseOnly) { //should exit for parse only to prevent access NULL pDstInfo
    PAccessUnit pCurAu = pCtx->pAccessUnitList;
    if (dsErrorFree == pCtx->iErrorCode) { //correct decoding, add to data buffer
      SParserBsInfo* pParser = pCtx->pParserBsInfo;
      SNalUnit* pCurNal = NULL;
      int32_t iTotalNalLen = 0;
      int32_t iNalLen = 0;
      int32_t iNum = 0;
      while (iNum < pParser->iNalNum) {
        iTotalNalLen += pParser->pNalLenInByte[iNum++];
      }
      uint8_t* pDstBuf = pParser->pDstBuff + iTotalNalLen;
      int32_t iIdx = pCurAu->uiStartPos;
      int32_t iEndIdx = pCurAu->uiEndPos;
      uint8_t* pNalBs = NULL;
      pParser->uiOutBsTimeStamp = (pCurAu->pNalUnitsList [iIdx]) ? pCurAu->pNalUnitsList [iIdx]->uiTimeStamp : 0;
      //pParser->iNalNum = 0;
      pParser->iSpsWidthInPixel = (pCtx->pSps->iMbWidth << 4) - ((pCtx->pSps->sFrameCrop.iLeftOffset +
                                  pCtx->pSps->sFrameCrop.iRightOffset) << 1);
      pParser->iSpsHeightInPixel = (pCtx->pSps->iMbHeight << 4) - ((pCtx->pSps->sFrameCrop.iTopOffset +
                                   pCtx->pSps->sFrameCrop.iBottomOffset) << 1);

      if (pCurAu->pNalUnitsList [iIdx]->sNalHeaderExt.bIdrFlag) { //IDR
        if (pCtx->bFrameFinish) { //add required sps/pps
          if (pParser->iNalNum > pCtx->iMaxNalNum - 2) { //2 reserved for sps+pps
            WelsLog (& (pCtx->sLogCtx), WELS_LOG_INFO,
                     "DecodeFrameConstruction(): current NAL num (%d) plus sps & pps exceeds permitted num (%d). Will expand",
                     pParser->iNalNum, pCtx->iMaxNalNum);
            WELS_VERIFY_RETURN_IF (ERR_INFO_OUT_OF_MEMORY, ExpandBsLenBuffer (pCtx, pParser->iNalNum + 2))
          }
          bool bSubSps = (NAL_UNIT_CODED_SLICE_EXT == pCurAu->pNalUnitsList [iIdx]->sNalHeaderExt.sNalUnitHeader.eNalUnitType);
          SSpsBsInfo* pSpsBs = NULL;
          SPpsBsInfo* pPpsBs = NULL;
          int32_t iSpsId = pCtx->pSps->iSpsId;
          int32_t iPpsId = pCtx->pPps->iPpsId;
          pCtx->bParamSetsLostFlag = false;
          //find required sps, pps and write into dst buff
          pSpsBs = bSubSps ? &pCtx->sSubsetSpsBsInfo [iSpsId] : &pCtx->sSpsBsInfo [iSpsId];
          pPpsBs = &pCtx->sPpsBsInfo [iPpsId];
          if (pDstBuf - pParser->pDstBuff + pSpsBs->uiSpsBsLen + pPpsBs->uiPpsBsLen >= MAX_ACCESS_UNIT_CAPACITY) {
            WelsLog (& (pCtx->sLogCtx), WELS_LOG_ERROR,
                     "DecodeFrameConstruction(): sps pps size: (%d %d) too large. Failed to parse. \n", pSpsBs->uiSpsBsLen,
                     pPpsBs->uiPpsBsLen);
            pCtx->iErrorCode |= dsOutOfMemory;
            pCtx->pParserBsInfo->iNalNum = 0;
            return ERR_INFO_OUT_OF_MEMORY;
          }
          memcpy (pDstBuf, pSpsBs->pSpsBsBuf, pSpsBs->uiSpsBsLen);
          pParser->pNalLenInByte [pParser->iNalNum ++] = pSpsBs->uiSpsBsLen;
          pDstBuf += pSpsBs->uiSpsBsLen;
          memcpy (pDstBuf, pPpsBs->pPpsBsBuf, pPpsBs->uiPpsBsLen);
          pParser->pNalLenInByte [pParser->iNalNum ++] = pPpsBs->uiPpsBsLen;
          pDstBuf += pPpsBs->uiPpsBsLen;
          pCtx->bFrameFinish = false;
        }
      }
      //then VCL data re-write
      if (pParser->iNalNum + iEndIdx - iIdx + 1 > pCtx->iMaxNalNum) { //calculate total NAL num
        WelsLog (& (pCtx->sLogCtx), WELS_LOG_INFO,
                 "DecodeFrameConstruction(): current NAL num (%d) exceeds permitted num (%d). Will expand",
                 pParser->iNalNum + iEndIdx - iIdx + 1, pCtx->iMaxNalNum);
        WELS_VERIFY_RETURN_IF (ERR_INFO_OUT_OF_MEMORY, ExpandBsLenBuffer (pCtx, pParser->iNalNum + iEndIdx - iIdx + 1))
      }
      while (iIdx <= iEndIdx) {
        pCurNal = pCurAu->pNalUnitsList [iIdx ++];
        iNalLen = pCurNal->sNalData.sVclNal.iNalLength;
        pNalBs = pCurNal->sNalData.sVclNal.pNalPos;
        pParser->pNalLenInByte [pParser->iNalNum ++] = iNalLen;
        if (pDstBuf - pParser->pDstBuff + iNalLen >= MAX_ACCESS_UNIT_CAPACITY) {
          WelsLog (& (pCtx->sLogCtx), WELS_LOG_ERROR,
                   "DecodeFrameConstruction(): composed output size (%ld) exceeds (%d). Failed to parse. current data pos %d out of %d:, previously accumulated num: %d, total num: %d, previously accumulated len: %d, current len: %d, current buf pos: %p, header buf pos: %p \n",
                   (long) (pDstBuf - pParser->pDstBuff + iNalLen), MAX_ACCESS_UNIT_CAPACITY, iIdx, iEndIdx, iNum, pParser->iNalNum,
                   iTotalNalLen, iNalLen, pDstBuf, pParser->pDstBuff);
          pCtx->iErrorCode |= dsOutOfMemory;
          pCtx->pParserBsInfo->iNalNum = 0;
          return ERR_INFO_OUT_OF_MEMORY;
        }

        memcpy (pDstBuf, pNalBs, iNalLen);
        pDstBuf += iNalLen;
      }
      if (pCtx->iTotalNumMbRec == kiTotalNumMbInCurLayer) { //frame complete
        pCtx->iTotalNumMbRec = 0;
        pCtx->bFramePending = false;
        pCtx->bFrameFinish = true; //finish current frame and mark it
      } else if (pCtx->iTotalNumMbRec != 0) { //frame incomplete
        pCtx->bFramePending = true;
        pCtx->pDec->bIsComplete = false;
        pCtx->bFrameFinish = false; //current frame not finished
        pCtx->iErrorCode |= dsFramePending;
        return ERR_INFO_PARSEONLY_PENDING;
        //pCtx->pParserBsInfo->iNalNum = 0;
      }
    } else { //error
      pCtx->pParserBsInfo->uiOutBsTimeStamp = 0;
      pCtx->pParserBsInfo->iNalNum = 0;
      pCtx->pParserBsInfo->iSpsWidthInPixel = 0;
      pCtx->pParserBsInfo->iSpsHeightInPixel = 0;
      return ERR_INFO_PARSEONLY_ERROR;
    }
    return ERR_NONE;
  }

  if (pCtx->iTotalNumMbRec != kiTotalNumMbInCurLayer) {
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_DEBUG,
             "DecodeFrameConstruction(): iTotalNumMbRec:%d, total_num_mb_sps:%d, cur_layer_mb_width:%d, cur_layer_mb_height:%d ",
             pCtx->iTotalNumMbRec, kiTotalNumMbInCurLayer, pCurDq->iMbWidth, pCurDq->iMbHeight);
    bFrameCompleteFlag = false; //return later after output buffer is done
    if (pCtx->bInstantDecFlag) { //no-delay decoding, wait for new slice
      return ERR_INFO_MB_NUM_INADEQUATE;
    }
  } else if (pCurDq->sLayerInfo.sNalHeaderExt.bIdrFlag
             && (pCtx->iErrorCode == dsErrorFree)) { //complete non-ECed IDR frame done
    pCtx->pDec->bIsComplete = true;
    pCtx->bFreezeOutput = false;
  }

  pCtx->iTotalNumMbRec = 0;

  //////output:::normal path
  pDstInfo->uiOutYuvTimeStamp = pPic->uiTimeStamp;
  ppDst[0]      = pPic->pData[0];
  ppDst[1]      = pPic->pData[1];
  ppDst[2]      = pPic->pData[2];

  pDstInfo->UsrData.sSystemBuffer.iFormat = videoFormatI420;

  pDstInfo->UsrData.sSystemBuffer.iWidth = kiActualWidth;
  pDstInfo->UsrData.sSystemBuffer.iHeight = kiActualHeight;
  pDstInfo->UsrData.sSystemBuffer.iStride[0] = pPic->iLinesize[0];
  pDstInfo->UsrData.sSystemBuffer.iStride[1] = pPic->iLinesize[1];
  ppDst[0] = ppDst[0] + pCtx->sFrameCrop.iTopOffset * 2 * pPic->iLinesize[0] + pCtx->sFrameCrop.iLeftOffset * 2;
  ppDst[1] = ppDst[1] + pCtx->sFrameCrop.iTopOffset  * pPic->iLinesize[1] + pCtx->sFrameCrop.iLeftOffset;
  ppDst[2] = ppDst[2] + pCtx->sFrameCrop.iTopOffset  * pPic->iLinesize[1] + pCtx->sFrameCrop.iLeftOffset;
  for (int i = 0; i < 3; ++i) {
    pDstInfo->pDst[i] = ppDst[i];
  }
  pDstInfo->iBufferStatus = 1;
  if (GetThreadCount (pCtx) > 1 && pPic->bIsComplete == false) {
    pPic->bIsComplete = true;
  }
  if (GetThreadCount (pCtx) > 1) {
    uint32_t uiMbHeight = (pCtx->pDec->iHeightInPixel + 15) >> 4;
    for (uint32_t i = 0; i < uiMbHeight; ++i) {
      SET_EVENT (&pCtx->pDec->pReadyEvent[i]);
    }
  }
  bool bOutResChange = false;
  if (GetThreadCount (pCtx) <= 1 || pCtx->pLastThreadCtx == NULL) {
    bOutResChange = (pCtx->iLastImgWidthInPixel != pDstInfo->UsrData.sSystemBuffer.iWidth)
                    || (pCtx->iLastImgHeightInPixel != pDstInfo->UsrData.sSystemBuffer.iHeight);
  } else {
    if (pCtx->pLastThreadCtx != NULL) {
      PWelsDecoderThreadCTX pLastThreadCtx = (PWelsDecoderThreadCTX) (pCtx->pLastThreadCtx);
      bOutResChange = (pLastThreadCtx->pCtx->iLastImgWidthInPixel != pDstInfo->UsrData.sSystemBuffer.iWidth)
                      || (pLastThreadCtx->pCtx->iLastImgHeightInPixel != pDstInfo->UsrData.sSystemBuffer.iHeight);
    }
  }
  pCtx->iLastImgWidthInPixel = pDstInfo->UsrData.sSystemBuffer.iWidth;
  pCtx->iLastImgHeightInPixel = pDstInfo->UsrData.sSystemBuffer.iHeight;
  if (pCtx->pParam->eEcActiveIdc == ERROR_CON_DISABLE) //no buffer output if EC is disabled and frame incomplete
    pDstInfo->iBufferStatus = (int32_t) (bFrameCompleteFlag
                                         && pPic->bIsComplete); // When EC disable, ECed picture not output
  else if ((pCtx->pParam->eEcActiveIdc == ERROR_CON_SLICE_COPY_CROSS_IDR_FREEZE_RES_CHANGE
            || pCtx->pParam->eEcActiveIdc == ERROR_CON_SLICE_MV_COPY_CROSS_IDR_FREEZE_RES_CHANGE)
           && pCtx->iErrorCode && bOutResChange)
    pCtx->bFreezeOutput = true;

  if (pDstInfo->iBufferStatus == 0) {
    if (!bFrameCompleteFlag)
      pCtx->iErrorCode |= dsBitstreamError;
    return ERR_INFO_MB_NUM_INADEQUATE;
  }
  if (pCtx->bFreezeOutput) {
    pDstInfo->iBufferStatus = 0;
    if (pPic->bNewSeqBegin) {
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_INFO,
               "DecodeFrameConstruction():New sequence detected, but freezed, correct MBs (%d) out of whole MBs (%d).",
               kiTotalNumMbInCurLayer - pCtx->iMbEcedNum, kiTotalNumMbInCurLayer);
    }
  }
  pCtx->iMbEcedNum = pPic->iMbEcedNum;
  pCtx->iMbNum = pPic->iMbNum;
  pCtx->iMbEcedPropNum = pPic->iMbEcedPropNum;
  if (pCtx->pParam->eEcActiveIdc != ERROR_CON_DISABLE) {
    if (pDstInfo->iBufferStatus && ((pCtx->pDecoderStatistics->uiWidth != (unsigned int) kiActualWidth)
                                    || (pCtx->pDecoderStatistics->uiHeight != (unsigned int) kiActualHeight))) {
      pCtx->pDecoderStatistics->uiResolutionChangeTimes++;
      pCtx->pDecoderStatistics->uiWidth = kiActualWidth;
      pCtx->pDecoderStatistics->uiHeight = kiActualHeight;
    }
    UpdateDecStat (pCtx, pDstInfo->iBufferStatus != 0);
  }
  return ERR_NONE;
}

inline bool    CheckSliceNeedReconstruct (uint8_t uiLayerDqId, uint8_t uiTargetDqId) {
  return (uiLayerDqId == uiTargetDqId); // target layer
}

inline uint8_t GetTargetDqId (uint8_t uiTargetDqId,  SDecodingParam* psParam) {
  uint8_t  uiRequiredDqId = psParam ? psParam->uiTargetDqLayer : (uint8_t)255;

  return WELS_MIN (uiTargetDqId, uiRequiredDqId);
}


inline void    HandleReferenceLostL0 (PWelsDecoderContext pCtx, PNalUnit pCurNal) {
  if (0 == pCurNal->sNalHeaderExt.uiTemporalId) {
    pCtx->bReferenceLostAtT0Flag = true;
  }
  pCtx->iErrorCode |= dsBitstreamError;
}

inline void    HandleReferenceLost (PWelsDecoderContext pCtx, PNalUnit pCurNal) {
  if ((0 == pCurNal->sNalHeaderExt.uiTemporalId) || (1 == pCurNal->sNalHeaderExt.uiTemporalId)) {
    pCtx->bReferenceLostAtT0Flag = true;
  }
  pCtx->iErrorCode |= dsRefLost;
}

inline int32_t  WelsDecodeConstructSlice (PWelsDecoderContext pCtx, PNalUnit pCurNal) {
  int32_t  iRet = WelsTargetSliceConstruction (pCtx);

  if (iRet) {
    HandleReferenceLostL0 (pCtx, pCurNal);
  }

  return iRet;
}

int32_t ParsePredWeightedTable (PBitStringAux pBs, PSliceHeader pSh) {
  uint32_t uiCode;
  int32_t iList = 0;
  int32_t iCode;

  WELS_READ_VERIFY (BsGetUe (pBs, &uiCode));
  WELS_CHECK_SE_BOTH_ERROR_NOLOG (uiCode, 0, 7, "luma_log2_weight_denom",
                                  GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_LUMA_LOG2_WEIGHT_DENOM));
  pSh->sPredWeightTable.uiLumaLog2WeightDenom = uiCode;
  if (pSh->pSps->uiChromaArrayType != 0) {
    WELS_READ_VERIFY (BsGetUe (pBs, &uiCode));
    WELS_CHECK_SE_BOTH_ERROR_NOLOG (uiCode, 0, 7, "chroma_log2_weight_denom",
                                    GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_CHROMA_LOG2_WEIGHT_DENOM));
    pSh->sPredWeightTable.uiChromaLog2WeightDenom = uiCode;
  }

  if ((pSh->sPredWeightTable.uiLumaLog2WeightDenom | pSh->sPredWeightTable.uiChromaLog2WeightDenom) > 7)
    return ERR_NONE;

  do {

    for (int i = 0; i < pSh->uiRefCount[iList]; i++) {
      //luma
      WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode));
      if (!!uiCode) {

        WELS_READ_VERIFY (BsGetSe (pBs, &iCode));
        WELS_CHECK_SE_BOTH_ERROR_NOLOG (iCode, -128, 127, "luma_weight",
                                        GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_LUMA_WEIGHT));
        pSh->sPredWeightTable.sPredList[iList].iLumaWeight[i] = iCode;

        WELS_READ_VERIFY (BsGetSe (pBs, &iCode));
        WELS_CHECK_SE_BOTH_ERROR_NOLOG (iCode, -128, 127, "luma_offset",
                                        GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_LUMA_OFFSET));
        pSh->sPredWeightTable.sPredList[iList].iLumaOffset[i] = iCode;
      } else {
        pSh->sPredWeightTable.sPredList[iList].iLumaWeight[i] = 1 << (pSh->sPredWeightTable.uiLumaLog2WeightDenom);
        pSh->sPredWeightTable.sPredList[iList].iLumaOffset[i] = 0;

      }
      //chroma
      if (pSh->pSps->uiChromaArrayType == 0)
        continue;

      WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode));
      if (!!uiCode) {
        for (int j = 0; j < 2; j++) {


          WELS_READ_VERIFY (BsGetSe (pBs, &iCode));
          WELS_CHECK_SE_BOTH_ERROR_NOLOG (iCode, -128, 127, "chroma_weight",
                                          GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_CHROMA_WEIGHT));
          pSh->sPredWeightTable.sPredList[iList].iChromaWeight[i][j] = iCode;

          WELS_READ_VERIFY (BsGetSe (pBs, &iCode));
          WELS_CHECK_SE_BOTH_ERROR_NOLOG (iCode, -128, 127, "chroma_offset",
                                          GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_CHROMA_OFFSET));
          pSh->sPredWeightTable.sPredList[iList].iChromaOffset[i][j] = iCode;
        }
      } else {
        for (int j = 0; j < 2; j++) {


          pSh->sPredWeightTable.sPredList[iList].iChromaWeight[i][j] = 1 << (pSh->sPredWeightTable.uiChromaLog2WeightDenom);
          pSh->sPredWeightTable.sPredList[iList].iChromaOffset[i][j] = 0;
        }
      }

    }
    ++iList;
    if (pSh->eSliceType != B_SLICE) {
      break;
    }
  } while (iList < LIST_A);//TODO: SUPPORT LIST_A
  return ERR_NONE;
}

void CreateImplicitWeightTable (PWelsDecoderContext pCtx) {

  PSlice pSlice = &pCtx->pCurDqLayer->sLayerInfo.sSliceInLayer;
  PSliceHeader pSliceHeader = &pSlice->sSliceHeaderExt.sSliceHeader;
  PDqLayer pCurDqLayer = pCtx->pCurDqLayer;
  if (pCurDqLayer->bUseWeightedBiPredIdc && pSliceHeader->pPps->uiWeightedBipredIdc == 2) {
    int32_t iPoc = pSliceHeader->iPicOrderCntLsb;

    //fix Bugzilla 1485229 check if pointers are NULL
    if (pCtx->sRefPic.pRefList[LIST_0][0] && pCtx->sRefPic.pRefList[LIST_1][0]) {
      if (pSliceHeader->uiRefCount[0] == 1 && pSliceHeader->uiRefCount[1] == 1
          && int64_t(pCtx->sRefPic.pRefList[LIST_0][0]->iFramePoc) + int64_t(pCtx->sRefPic.pRefList[LIST_1][0]->iFramePoc) == 2 * int64_t(iPoc)) {
        pCurDqLayer->bUseWeightedBiPredIdc = false;
        return;
      }
    }

    pCurDqLayer->pPredWeightTable->uiLumaLog2WeightDenom = 5;
    pCurDqLayer->pPredWeightTable->uiChromaLog2WeightDenom = 5;
    for (int32_t iRef0 = 0; iRef0 < pSliceHeader->uiRefCount[0]; iRef0++) {
      if (pCtx->sRefPic.pRefList[LIST_0][iRef0]) {
        const int32_t iPoc0 = pCtx->sRefPic.pRefList[LIST_0][iRef0]->iFramePoc;
        bool bIsLongRef0 = pCtx->sRefPic.pRefList[LIST_0][iRef0]->bIsLongRef;
        for (int32_t iRef1 = 0; iRef1 < pSliceHeader->uiRefCount[1]; iRef1++) {
          if (pCtx->sRefPic.pRefList[LIST_1][iRef1]) {
            const int32_t iPoc1 = pCtx->sRefPic.pRefList[LIST_1][iRef1]->iFramePoc;
            bool bIsLongRef1 = pCtx->sRefPic.pRefList[LIST_1][iRef1]->bIsLongRef;
            pCurDqLayer->pPredWeightTable->iImplicitWeight[iRef0][iRef1] = 32;
            if (!bIsLongRef0 && !bIsLongRef1) {
              const int32_t iTd = WELS_CLIP3 (iPoc1 - iPoc0, -128, 127);
              if (iTd) {
                int32_t iTb = WELS_CLIP3 (iPoc - iPoc0, -128, 127);
                int32_t iTx = (16384 + (WELS_ABS (iTd) >> 1)) / iTd;
                int32_t iDistScaleFactor = (iTb * iTx + 32) >> 8;
                if (iDistScaleFactor >= -64 && iDistScaleFactor <= 128) {
                  pCurDqLayer->pPredWeightTable->iImplicitWeight[iRef0][iRef1] = 64 - iDistScaleFactor;
                }
              }
            }
          }
        }
      }
    }
  }
  return;
}

/*
 *  Predeclared function routines ..
 */
int32_t ParseRefPicListReordering (PBitStringAux pBs, PSliceHeader pSh) {
  int32_t iList = 0;
  const EWelsSliceType keSt = pSh->eSliceType;
  PRefPicListReorderSyn pRefPicListReordering = &pSh->pRefPicListReordering;
  PSps pSps = pSh->pSps;
  uint32_t uiCode;
  if (keSt == I_SLICE || keSt == SI_SLICE)
    return ERR_NONE;

  // Common syntaxs for P or B slices: list0, list1 followed if B slices used.
  do {
    WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //ref_pic_list_modification_flag_l0
    pRefPicListReordering->bRefPicListReorderingFlag[iList] = !!uiCode;

    if (pRefPicListReordering->bRefPicListReorderingFlag[iList]) {
      int32_t iIdx = 0;
      do {
        WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //modification_of_pic_nums_idc
        const uint32_t kuiIdc = uiCode;

        //Fixed the referrence list reordering crash issue.(fault kIdc value > 3 case)---
        if ((iIdx >= MAX_REF_PIC_COUNT) || (kuiIdc > 3)) {
          return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_REF_REORDERING);
        }
        pRefPicListReordering->sReorderingSyn[iList][iIdx].uiReorderingOfPicNumsIdc = kuiIdc;
        if (kuiIdc == 3)
          break;

        if (iIdx >= pSh->uiRefCount[iList] || iIdx >= MAX_REF_PIC_COUNT)
          return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_REF_REORDERING);

        if (kuiIdc == 0 || kuiIdc == 1) {
          // abs_diff_pic_num_minus1 should be in range 0 to MaxPicNum-1, MaxPicNum is derived as
          // 2^(4+log2_max_frame_num_minus4)
          WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //abs_diff_pic_num_minus1
          WELS_CHECK_SE_UPPER_ERROR_NOLOG (uiCode, (uint32_t) (1 << pSps->uiLog2MaxFrameNum), "abs_diff_pic_num_minus1",
                                           GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_REF_REORDERING));
          pRefPicListReordering->sReorderingSyn[iList][iIdx].uiAbsDiffPicNumMinus1 = uiCode; // uiAbsDiffPicNumMinus1
        } else if (kuiIdc == 2) {
          WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //long_term_pic_num
          pRefPicListReordering->sReorderingSyn[iList][iIdx].uiLongTermPicNum = uiCode;
        }

        ++ iIdx;
      } while (true);
    }
    if (keSt != B_SLICE)
      break;
    ++ iList;
  } while (iList < LIST_A);

  return ERR_NONE;
}

int32_t ParseDecRefPicMarking (PWelsDecoderContext pCtx, PBitStringAux pBs, PSliceHeader pSh, PSps pSps,
                               const bool kbIdrFlag) {
  PRefPicMarking const kpRefMarking = &pSh->sRefMarking;
  uint32_t uiCode;
  if (kbIdrFlag) {
    WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //no_output_of_prior_pics_flag
    kpRefMarking->bNoOutputOfPriorPicsFlag = !!uiCode;
    WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //long_term_reference_flag
    kpRefMarking->bLongTermRefFlag = !!uiCode;
  } else {
    WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //adaptive_ref_pic_marking_mode_flag
    kpRefMarking->bAdaptiveRefPicMarkingModeFlag = !!uiCode;
    if (kpRefMarking->bAdaptiveRefPicMarkingModeFlag) {
      int32_t iIdx = 0;
      bool bAllowMmco5 = true, bMmco4Exist = false, bMmco5Exist = false, bMmco6Exist = false;
      do {
        WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //memory_management_control_operation
        const uint32_t kuiMmco = uiCode;

        kpRefMarking->sMmcoRef[iIdx].uiMmcoType = kuiMmco;
        if (kuiMmco == MMCO_END)
          break;

        if (kuiMmco == MMCO_SHORT2UNUSED || kuiMmco == MMCO_SHORT2LONG) {
          bAllowMmco5 = false;
          WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //difference_of_pic_nums_minus1
          kpRefMarking->sMmcoRef[iIdx].iDiffOfPicNum = 1 + uiCode;
          kpRefMarking->sMmcoRef[iIdx].iShortFrameNum = (pSh->iFrameNum - kpRefMarking->sMmcoRef[iIdx].iDiffOfPicNum) & ((
                1 << pSps->uiLog2MaxFrameNum) - 1);
        } else if (kuiMmco == MMCO_LONG2UNUSED) {
          bAllowMmco5 = false;
          WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //long_term_pic_num
          kpRefMarking->sMmcoRef[iIdx].uiLongTermPicNum = uiCode;
        }
        if (kuiMmco == MMCO_SHORT2LONG || kuiMmco == MMCO_LONG) {
          if (kuiMmco == MMCO_LONG) {
            WELS_VERIFY_RETURN_IF (-1, bMmco6Exist);
            bMmco6Exist = true;
          }
          WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //long_term_frame_idx
          kpRefMarking->sMmcoRef[iIdx].iLongTermFrameIdx = uiCode;
        } else if (kuiMmco == MMCO_SET_MAX_LONG) {
          WELS_VERIFY_RETURN_IF (-1, bMmco4Exist);
          bMmco4Exist = true;
          WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //max_long_term_frame_idx_plus1
          int32_t iMaxLongTermFrameIdx = -1 + uiCode;
          if (iMaxLongTermFrameIdx > int32_t (pSps->uiLog2MaxFrameNum)) {
            //ISO/IEC 14496-10:2009(E) 7.4.3.3 Decoded reference picture marking semantics page 96
            return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_REF_MARKING);
          }
          kpRefMarking->sMmcoRef[iIdx].iMaxLongTermFrameIdx = iMaxLongTermFrameIdx;
        } else if (kuiMmco == MMCO_RESET) {
          WELS_VERIFY_RETURN_IF (-1, (!bAllowMmco5 || bMmco5Exist));
          bMmco5Exist = true;

          pCtx->pLastDecPicInfo->iPrevPicOrderCntLsb = 0;
          pCtx->pLastDecPicInfo->iPrevPicOrderCntMsb = 0;
          pSh->iPicOrderCntLsb = 0;
          if (pCtx->pSliceHeader)
            pCtx->pSliceHeader->iPicOrderCntLsb = 0;
        }
        ++ iIdx;

      } while (iIdx < MAX_MMCO_COUNT);
    }
  }

  return ERR_NONE;
}

bool FillDefaultSliceHeaderExt (PSliceHeaderExt pShExt, PNalUnitHeaderExt pNalExt) {
  if (pShExt == NULL || pNalExt == NULL)
    return false;

  if (pNalExt->iNoInterLayerPredFlag || pNalExt->uiQualityId > 0)
    pShExt->bBasePredWeightTableFlag = false;
  else
    pShExt->bBasePredWeightTableFlag = true;
  pShExt->uiRefLayerDqId = (uint8_t) - 1;
  pShExt->uiDisableInterLayerDeblockingFilterIdc        = 0;
  pShExt->iInterLayerSliceAlphaC0Offset                 = 0;
  pShExt->iInterLayerSliceBetaOffset                    = 0;
  pShExt->bConstrainedIntraResamplingFlag               = false;
  pShExt->uiRefLayerChromaPhaseXPlus1Flag               = 0;
  pShExt->uiRefLayerChromaPhaseYPlus1                   = 1;
  //memset(&pShExt->sScaledRefLayer, 0, sizeof(SPosOffset));

  pShExt->iScaledRefLayerPicWidthInSampleLuma   = pShExt->sSliceHeader.iMbWidth << 4;
  pShExt->iScaledRefLayerPicHeightInSampleLuma  = pShExt->sSliceHeader.iMbHeight << 4;

  pShExt->bSliceSkipFlag                = false;
  pShExt->bAdaptiveBaseModeFlag         = false;
  pShExt->bDefaultBaseModeFlag          = false;
  pShExt->bAdaptiveMotionPredFlag       = false;
  pShExt->bDefaultMotionPredFlag        = false;
  pShExt->bAdaptiveResidualPredFlag     = false;
  pShExt->bDefaultResidualPredFlag      = false;
  pShExt->bTCoeffLevelPredFlag          = false;
  pShExt->uiScanIdxStart                = 0;
  pShExt->uiScanIdxEnd                  = 15;

  return true;
}

int32_t InitBsBuffer (PWelsDecoderContext pCtx) {
  if (pCtx == NULL)
    return ERR_INFO_INVALID_PTR;

  CMemoryAlign* pMa = pCtx->pMemAlign;

  pCtx->iMaxBsBufferSizeInByte = MIN_ACCESS_UNIT_CAPACITY * MAX_BUFFERED_NUM;
  if ((pCtx->sRawData.pHead = static_cast<uint8_t*> (pMa->WelsMallocz (pCtx->iMaxBsBufferSizeInByte,
                              "pCtx->sRawData.pHead"))) == NULL) {
    return ERR_INFO_OUT_OF_MEMORY;
  }
  pCtx->sRawData.pStartPos = pCtx->sRawData.pCurPos = pCtx->sRawData.pHead;
  pCtx->sRawData.pEnd = pCtx->sRawData.pHead + pCtx->iMaxBsBufferSizeInByte;
  if (pCtx->pParam->bParseOnly) {
    pCtx->pParserBsInfo = static_cast<SParserBsInfo*> (pMa->WelsMallocz (sizeof (SParserBsInfo), "pCtx->pParserBsInfo"));
    if (pCtx->pParserBsInfo == NULL) {
      return ERR_INFO_OUT_OF_MEMORY;
    }
    memset (pCtx->pParserBsInfo, 0, sizeof (SParserBsInfo));
    pCtx->pParserBsInfo->pDstBuff = static_cast<uint8_t*> (pMa->WelsMallocz (MAX_ACCESS_UNIT_CAPACITY * sizeof (uint8_t),
                                    "pCtx->pParserBsInfo->pDstBuff"));
    if (pCtx->pParserBsInfo->pDstBuff == NULL) {
      return ERR_INFO_OUT_OF_MEMORY;
    }
    memset (pCtx->pParserBsInfo->pDstBuff, 0, MAX_ACCESS_UNIT_CAPACITY * sizeof (uint8_t));

    if ((pCtx->sSavedData.pHead = static_cast<uint8_t*> (pMa->WelsMallocz (pCtx->iMaxBsBufferSizeInByte,
                                  "pCtx->sSavedData.pHead"))) == NULL) {
      return ERR_INFO_OUT_OF_MEMORY;
    }
    pCtx->sSavedData.pStartPos = pCtx->sSavedData.pCurPos = pCtx->sSavedData.pHead;
    pCtx->sSavedData.pEnd = pCtx->sSavedData.pHead + pCtx->iMaxBsBufferSizeInByte;

    pCtx->iMaxNalNum = MAX_NAL_UNITS_IN_LAYER + 2; //2 reserved for SPS+PPS
    pCtx->pParserBsInfo->pNalLenInByte = static_cast<int*> (pMa->WelsMallocz (pCtx->iMaxNalNum * sizeof (int),
                                         "pCtx->pParserBsInfo->pNalLenInByte"));
    if (pCtx->pParserBsInfo->pNalLenInByte == NULL) {
      return ERR_INFO_OUT_OF_MEMORY;
    }
  }
  return ERR_NONE;
}

int32_t ExpandBsBuffer (PWelsDecoderContext pCtx, const int kiSrcLen) {
  if (pCtx == NULL)
    return ERR_INFO_INVALID_PTR;
  int32_t iExpandStepShift = 1;
  int32_t iNewBuffLen = WELS_MAX ((kiSrcLen * MAX_BUFFERED_NUM), (pCtx->iMaxBsBufferSizeInByte << iExpandStepShift));
  //allocate new bs buffer
  CMemoryAlign* pMa = pCtx->pMemAlign;

  //Realloc sRawData
  uint8_t* pNewBsBuff = static_cast<uint8_t*> (pMa->WelsMallocz (iNewBuffLen, "pCtx->sRawData.pHead"));
  if (pNewBsBuff == NULL) {
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_ERROR, "ExpandBsBuffer() Failed for malloc pNewBsBuff (%d)", iNewBuffLen);
    pCtx->iErrorCode |= dsOutOfMemory;
    return ERR_INFO_OUT_OF_MEMORY;
  }

  //Calculate and set the bs start and end position
  for (uint32_t i = 0; i <= pCtx->pAccessUnitList->uiActualUnitsNum; i++) {
    PBitStringAux pSliceBitsRead = &pCtx->pAccessUnitList->pNalUnitsList[i]->sNalData.sVclNal.sSliceBitsRead;
    pSliceBitsRead->pStartBuf = pSliceBitsRead->pStartBuf - pCtx->sRawData.pHead + pNewBsBuff;
    pSliceBitsRead->pEndBuf   = pSliceBitsRead->pEndBuf   - pCtx->sRawData.pHead + pNewBsBuff;
    pSliceBitsRead->pCurBuf   = pSliceBitsRead->pCurBuf   - pCtx->sRawData.pHead + pNewBsBuff;
  }

  //Copy current buffer status to new buffer
  memcpy (pNewBsBuff, pCtx->sRawData.pHead, pCtx->iMaxBsBufferSizeInByte);
  pCtx->sRawData.pStartPos = pNewBsBuff + (pCtx->sRawData.pStartPos - pCtx->sRawData.pHead);
  pCtx->sRawData.pCurPos   = pNewBsBuff + (pCtx->sRawData.pCurPos   - pCtx->sRawData.pHead);
  pCtx->sRawData.pEnd      = pNewBsBuff + iNewBuffLen;
  pMa->WelsFree (pCtx->sRawData.pHead, "pCtx->sRawData.pHead");
  pCtx->sRawData.pHead = pNewBsBuff;

  if (pCtx->pParam->bParseOnly) {
    //Realloc sSavedData
    uint8_t* pNewSavedBsBuff = static_cast<uint8_t*> (pMa->WelsMallocz (iNewBuffLen, "pCtx->sSavedData.pHead"));
    if (pNewSavedBsBuff == NULL) {
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_ERROR, "ExpandBsBuffer() Failed for malloc pNewSavedBsBuff (%d)", iNewBuffLen);
      pCtx->iErrorCode |= dsOutOfMemory;
      return ERR_INFO_OUT_OF_MEMORY;
    }

    //Copy current buffer status to new buffer
    memcpy (pNewSavedBsBuff, pCtx->sSavedData.pHead, pCtx->iMaxBsBufferSizeInByte);
    pCtx->sSavedData.pStartPos = pNewSavedBsBuff + (pCtx->sSavedData.pStartPos - pCtx->sSavedData.pHead);
    pCtx->sSavedData.pCurPos   = pNewSavedBsBuff + (pCtx->sSavedData.pCurPos   - pCtx->sSavedData.pHead);
    pCtx->sSavedData.pEnd      = pNewSavedBsBuff + iNewBuffLen;
    pMa->WelsFree (pCtx->sSavedData.pHead, "pCtx->sSavedData.pHead");
    pCtx->sSavedData.pHead = pNewSavedBsBuff;
  }

  pCtx->iMaxBsBufferSizeInByte = iNewBuffLen;
  return ERR_NONE;
}

int32_t ExpandBsLenBuffer (PWelsDecoderContext pCtx, const int kiCurrLen) {
  SParserBsInfo* pParser = pCtx->pParserBsInfo;
  if (!pParser->pNalLenInByte)
    return ERR_INFO_INVALID_ACCESS;

  int iNewLen = kiCurrLen;
  if (kiCurrLen >= MAX_MB_SIZE + 2) { //exceeds the max MB number of level 5.2
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, "Current nal num (%d) exceededs %d.", kiCurrLen, MAX_MB_SIZE);
    pCtx->iErrorCode |= dsOutOfMemory;
    return ERR_INFO_OUT_OF_MEMORY;
  } else {
    iNewLen = kiCurrLen << 1;
    iNewLen = WELS_MIN (iNewLen, MAX_MB_SIZE + 2);
  }

  CMemoryAlign* pMa = pCtx->pMemAlign;
  int* pNewLenBuffer = static_cast<int*> (pMa->WelsMallocz (iNewLen * sizeof (int),
                                          "pCtx->pParserBsInfo->pNalLenInByte"));
  if (pNewLenBuffer == NULL) {
    pCtx->iErrorCode |= dsOutOfMemory;
    return ERR_INFO_OUT_OF_MEMORY;
  }

  //copy existing data from old length buffer to new
  memcpy (pNewLenBuffer, pParser->pNalLenInByte, pCtx->iMaxNalNum * sizeof (int));
  pMa->WelsFree (pParser->pNalLenInByte, "pCtx->pParserBsInfo->pNalLenInByte");
  pParser->pNalLenInByte = pNewLenBuffer;
  pCtx->iMaxNalNum = iNewLen;
  return ERR_NONE;
}

int32_t CheckBsBuffer (PWelsDecoderContext pCtx, const int32_t kiSrcLen) {
  if (kiSrcLen > MAX_ACCESS_UNIT_CAPACITY) { //exceeds max allowed data
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, "Max AU size exceeded. Allowed size = %d, current size = %d",
             MAX_ACCESS_UNIT_CAPACITY,
             kiSrcLen);
    pCtx->iErrorCode |= dsBitstreamError;
    return ERR_INFO_INVALID_ACCESS;
  } else if (kiSrcLen > pCtx->iMaxBsBufferSizeInByte /
             MAX_BUFFERED_NUM) { //may lead to buffer overwrite, prevent it by expanding buffer
    if (ExpandBsBuffer (pCtx, kiSrcLen)) {
      return ERR_INFO_OUT_OF_MEMORY;
    }
  }

  return ERR_NONE;
}

/*
 * WelsInitStaticMemory
 * Memory request for new introduced data
 * Especially for:
 * rbsp_au_buffer, cur_dq_layer_ptr and ref_dq_layer_ptr in MB info cache.
 * return:
 *  0 - success; otherwise returned error_no defined in error_no.h.
*/
int32_t WelsInitStaticMemory (PWelsDecoderContext pCtx) {
  if (pCtx == NULL) {
    return ERR_INFO_INVALID_PTR;
  }

  if (MemInitNalList (&pCtx->pAccessUnitList, MAX_NAL_UNIT_NUM_IN_AU, pCtx->pMemAlign) != 0)
    return ERR_INFO_OUT_OF_MEMORY;

  if (InitBsBuffer (pCtx) != 0)
    return ERR_INFO_OUT_OF_MEMORY;

  pCtx->uiTargetDqId            = (uint8_t) - 1;
  pCtx->bEndOfStreamFlag        = false;

  return ERR_NONE;
}

/*
 * WelsFreeStaticMemory
 * Free memory introduced in WelsInitStaticMemory at destruction of decoder.
 *
 */
void WelsFreeStaticMemory (PWelsDecoderContext pCtx) {
  if (pCtx == NULL)
    return;

  CMemoryAlign* pMa = pCtx->pMemAlign;

  MemFreeNalList (&pCtx->pAccessUnitList, pMa);

  if (pCtx->sRawData.pHead) {
    pMa->WelsFree (pCtx->sRawData.pHead, "pCtx->sRawData->pHead");
  }
  pCtx->sRawData.pHead                = NULL;
  pCtx->sRawData.pEnd                 = NULL;
  pCtx->sRawData.pStartPos            = NULL;
  pCtx->sRawData.pCurPos              = NULL;
  if (pCtx->pParam->bParseOnly) {
    if (pCtx->sSavedData.pHead) {
      pMa->WelsFree (pCtx->sSavedData.pHead, "pCtx->sSavedData->pHead");
    }
    pCtx->sSavedData.pHead                = NULL;
    pCtx->sSavedData.pEnd                 = NULL;
    pCtx->sSavedData.pStartPos            = NULL;
    pCtx->sSavedData.pCurPos              = NULL;
    if (pCtx->pParserBsInfo) {
      if (pCtx->pParserBsInfo->pNalLenInByte) {
        pMa->WelsFree (pCtx->pParserBsInfo->pNalLenInByte, "pCtx->pParserBsInfo->pNalLenInByte");
        pCtx->pParserBsInfo->pNalLenInByte = NULL;
        pCtx->iMaxNalNum = 0;
      }
      if (pCtx->pParserBsInfo->pDstBuff) {
        pMa->WelsFree (pCtx->pParserBsInfo->pDstBuff, "pCtx->pParserBsInfo->pDstBuff");
        pCtx->pParserBsInfo->pDstBuff = NULL;
      }
      pMa->WelsFree (pCtx->pParserBsInfo, "pCtx->pParserBsInfo");
      pCtx->pParserBsInfo = NULL;
    }
  }

  if (NULL != pCtx->pParam) {
    pMa->WelsFree (pCtx->pParam, "pCtx->pParam");

    pCtx->pParam = NULL;
  }
}
/*
 *  DecodeNalHeaderExt
 *  Trigger condition: NAL_UNIT_TYPE = NAL_UNIT_PREFIX or NAL_UNIT_CODED_SLICE_EXT
 *  Parameter:
 *  pNal:   target NALUnit ptr
 *  pSrc:   NAL Unit bitstream
 */
void DecodeNalHeaderExt (PNalUnit pNal, uint8_t* pSrc) {
  PNalUnitHeaderExt pHeaderExt = &pNal->sNalHeaderExt;

  uint8_t uiCurByte = *pSrc;
  pHeaderExt->bIdrFlag              = !! (uiCurByte & 0x40);
  pHeaderExt->uiPriorityId          = uiCurByte & 0x3F;

  uiCurByte = * (++pSrc);
  pHeaderExt->iNoInterLayerPredFlag = uiCurByte >> 7;
  pHeaderExt->uiDependencyId        = (uiCurByte & 0x70) >> 4;
  pHeaderExt->uiQualityId           = uiCurByte & 0x0F;
  uiCurByte = * (++pSrc);
  pHeaderExt->uiTemporalId          = uiCurByte >> 5;
  pHeaderExt->bUseRefBasePicFlag    = !! (uiCurByte & 0x10);
  pHeaderExt->bDiscardableFlag      = !! (uiCurByte & 0x08);
  pHeaderExt->bOutputFlag           = !! (uiCurByte & 0x04);
  pHeaderExt->uiReservedThree2Bits  = uiCurByte & 0x03;
  pHeaderExt->uiLayerDqId           = (pHeaderExt->uiDependencyId << 4) | pHeaderExt->uiQualityId;
}


void UpdateDecoderStatisticsForActiveParaset (SDecoderStatistics* pDecoderStatistics,
    PSps pSps, PPps pPps) {
  pDecoderStatistics->iCurrentActiveSpsId = pSps->iSpsId;

  pDecoderStatistics->iCurrentActivePpsId = pPps->iPpsId;
  pDecoderStatistics->uiProfile = static_cast<unsigned int> (pSps->uiProfileIdc);
  pDecoderStatistics->uiLevel = pSps->uiLevelIdc;
}

#define SLICE_HEADER_IDR_PIC_ID_MAX 65535
#define SLICE_HEADER_REDUNDANT_PIC_CNT_MAX 127
#define SLICE_HEADER_ALPHAC0_BETA_OFFSET_MIN -12
#define SLICE_HEADER_ALPHAC0_BETA_OFFSET_MAX 12
#define SLICE_HEADER_INTER_LAYER_ALPHAC0_BETA_OFFSET_MIN -12
#define SLICE_HEADER_INTER_LAYER_ALPHAC0_BETA_OFFSET_MAX 12
#define MAX_NUM_REF_IDX_L0_ACTIVE_MINUS1 15
#define MAX_NUM_REF_IDX_L1_ACTIVE_MINUS1 15
#define SLICE_HEADER_CABAC_INIT_IDC_MAX 2
/*
 *  decode_slice_header_avc
 *  Parse slice header of bitstream in avc for storing data structure
 */
int32_t ParseSliceHeaderSyntaxs (PWelsDecoderContext pCtx, PBitStringAux pBs, const bool kbExtensionFlag) {
  PNalUnit const kpCurNal               =
    pCtx->pAccessUnitList->pNalUnitsList[pCtx->pAccessUnitList->uiAvailUnitsNum -
                                                                                1];

  PNalUnitHeaderExt pNalHeaderExt       = NULL;
  PSliceHeader pSliceHead               = NULL;
  PSliceHeaderExt pSliceHeadExt         = NULL;
  PSubsetSps pSubsetSps                 = NULL;
  PSps pSps                             = NULL;
  PPps pPps                             = NULL;
  EWelsNalUnitType eNalType             = static_cast<EWelsNalUnitType> (0);
  int32_t iPpsId                        = 0;
  int32_t iRet                          = ERR_NONE;
  uint8_t uiSliceType                   = 0;
  uint8_t uiQualityId                   = BASE_QUALITY_ID;
  bool  bIdrFlag                        = false;
  bool  bSgChangeCycleInvolved          = false;        // involved slice group change cycle ?
  uint32_t uiCode;
  int32_t iCode;
  SLogContext* pLogCtx = & (pCtx->sLogCtx);

  if (kpCurNal == NULL) {
    return ERR_INFO_OUT_OF_MEMORY;
  }

  pNalHeaderExt = &kpCurNal->sNalHeaderExt;
  pSliceHead    = &kpCurNal->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader;
  eNalType      = pNalHeaderExt->sNalUnitHeader.eNalUnitType;

  pSliceHeadExt = &kpCurNal->sNalData.sVclNal.sSliceHeaderExt;

  if (pSliceHeadExt) {
    SRefBasePicMarking sBaseMarking;
    const bool kbStoreRefBaseFlag = pSliceHeadExt->bStoreRefBasePicFlag;
    memcpy (&sBaseMarking, &pSliceHeadExt->sRefBasePicMarking, sizeof (SRefBasePicMarking)); //confirmed_safe_unsafe_usage
    memset (pSliceHeadExt, 0, sizeof (SSliceHeaderExt));
    pSliceHeadExt->bStoreRefBasePicFlag = kbStoreRefBaseFlag;
    memcpy (&pSliceHeadExt->sRefBasePicMarking, &sBaseMarking, sizeof (SRefBasePicMarking)); //confirmed_safe_unsafe_usage
  }

  kpCurNal->sNalData.sVclNal.bSliceHeaderExtFlag = kbExtensionFlag;

  // first_mb_in_slice
  WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //first_mb_in_slice
  WELS_CHECK_SE_UPPER_ERROR (uiCode, 36863u, "first_mb_in_slice", GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER,
                             ERR_INFO_INVALID_FIRST_MB_IN_SLICE));
  pSliceHead->iFirstMbInSlice = uiCode;

  WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //slice_type
  uiSliceType = uiCode;
  if (uiSliceType > 9) {
    WelsLog (pLogCtx, WELS_LOG_WARNING, "slice type too large (%d) at first_mb(%d)", uiSliceType,
             pSliceHead->iFirstMbInSlice);
    return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_SLICE_TYPE);
  }
  if (uiSliceType > 4)
    uiSliceType -= 5;

  if ((NAL_UNIT_CODED_SLICE_IDR == eNalType) && (I_SLICE != uiSliceType)) {
    WelsLog (pLogCtx, WELS_LOG_WARNING, "Invalid slice type(%d) in IDR picture. ", uiSliceType);
    return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_SLICE_TYPE);
  }

  if (kbExtensionFlag) {
    if (uiSliceType > 2) {
      WelsLog (pLogCtx, WELS_LOG_WARNING, "Invalid slice type(%d).", uiSliceType);
      return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_SLICE_TYPE);
    }
  }

  pSliceHead->eSliceType = static_cast <EWelsSliceType> (uiSliceType);

  WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //pic_parameter_set_id
  WELS_CHECK_SE_UPPER_ERROR (uiCode, (MAX_PPS_COUNT - 1), "iPpsId out of range",
                             GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER,
                                 ERR_INFO_PPS_ID_OVERFLOW));
  iPpsId = uiCode;

  //add check PPS available here
  if (pCtx->sSpsPpsCtx.bPpsAvailFlags[iPpsId] == false) {
    pCtx->pDecoderStatistics->iPpsReportErrorNum++;
    if (pCtx->sSpsPpsCtx.iPPSLastInvalidId != iPpsId) {
      WelsLog (pLogCtx, WELS_LOG_ERROR, "PPS id (%d) is invalid, previous id (%d) error ignored (%d)!", iPpsId,
               pCtx->sSpsPpsCtx.iPPSLastInvalidId, pCtx->sSpsPpsCtx.iPPSInvalidNum);
      pCtx->sSpsPpsCtx.iPPSLastInvalidId = iPpsId;
      pCtx->sSpsPpsCtx.iPPSInvalidNum = 0;
    } else {
      pCtx->sSpsPpsCtx.iPPSInvalidNum++;
    }
    pCtx->iErrorCode |= dsNoParamSets;
    return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_PPS_ID);
  }
  pCtx->sSpsPpsCtx.iPPSLastInvalidId = -1;

  pPps    = &pCtx->sSpsPpsCtx.sPpsBuffer[iPpsId];

  if (pPps->uiNumSliceGroups == 0) {
    WelsLog (pLogCtx, WELS_LOG_WARNING, "Invalid PPS referenced");
    pCtx->iErrorCode |= dsNoParamSets;
    return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_NO_PARAM_SETS);
  }

  if (kbExtensionFlag) {
    pSubsetSps      = &pCtx->sSpsPpsCtx.sSubsetSpsBuffer[pPps->iSpsId];
    pSps            = &pSubsetSps->sSps;
    if (pCtx->sSpsPpsCtx.bSubspsAvailFlags[pPps->iSpsId] == false) {
      pCtx->pDecoderStatistics->iSubSpsReportErrorNum++;
      if (pCtx->sSpsPpsCtx.iSubSPSLastInvalidId != pPps->iSpsId) {
        WelsLog (pLogCtx, WELS_LOG_ERROR, "Sub SPS id (%d) is invalid, previous id (%d) error ignored (%d)!", pPps->iSpsId,
                 pCtx->sSpsPpsCtx.iSubSPSLastInvalidId, pCtx->sSpsPpsCtx.iSubSPSInvalidNum);
        pCtx->sSpsPpsCtx.iSubSPSLastInvalidId = pPps->iSpsId;
        pCtx->sSpsPpsCtx.iSubSPSInvalidNum = 0;
      } else {
        pCtx->sSpsPpsCtx.iSubSPSInvalidNum++;
      }
      pCtx->iErrorCode |= dsNoParamSets;
      return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_SPS_ID);
    }
    pCtx->sSpsPpsCtx.iSubSPSLastInvalidId = -1;
  } else {
    if (pCtx->sSpsPpsCtx.bSpsAvailFlags[pPps->iSpsId] == false) {
      pCtx->pDecoderStatistics->iSpsReportErrorNum++;
      if (pCtx->sSpsPpsCtx.iSPSLastInvalidId != pPps->iSpsId) {
        WelsLog (pLogCtx, WELS_LOG_ERROR, "SPS id (%d) is invalid, previous id (%d) error ignored (%d)!", pPps->iSpsId,
                 pCtx->sSpsPpsCtx.iSPSLastInvalidId, pCtx->sSpsPpsCtx.iSPSInvalidNum);
        pCtx->sSpsPpsCtx.iSPSLastInvalidId = pPps->iSpsId;
        pCtx->sSpsPpsCtx.iSPSInvalidNum = 0;
      } else {
        pCtx->sSpsPpsCtx.iSPSInvalidNum++;
      }
      pCtx->iErrorCode |= dsNoParamSets;
      return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_SPS_ID);
    }
    pCtx->sSpsPpsCtx.iSPSLastInvalidId = -1;
    pSps = &pCtx->sSpsPpsCtx.sSpsBuffer[pPps->iSpsId];
  }
  pSliceHead->iPpsId = iPpsId;
  pSliceHead->iSpsId = pPps->iSpsId;
  pSliceHead->pPps   = pPps;
  pSliceHead->pSps   = pSps;

  pSliceHeadExt->pSubsetSps = pSubsetSps;

  if (pSps->iNumRefFrames == 0) {
    if ((uiSliceType != I_SLICE) && (uiSliceType != SI_SLICE)) {
      WelsLog (pLogCtx, WELS_LOG_WARNING, "slice_type (%d) not supported for num_ref_frames = 0.", uiSliceType);
      return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_SLICE_TYPE);
    }
  }

  bIdrFlag = (!kbExtensionFlag && eNalType == NAL_UNIT_CODED_SLICE_IDR) || (kbExtensionFlag && pNalHeaderExt->bIdrFlag);
  pSliceHead->bIdrFlag = bIdrFlag;

  if (pSps->uiLog2MaxFrameNum == 0) {
    WelsLog (pLogCtx, WELS_LOG_WARNING, "non existing SPS referenced");
    return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_NO_PARAM_SETS);
  }
  // check first_mb_in_slice
  WELS_CHECK_SE_UPPER_ERROR ((uint32_t) (pSliceHead->iFirstMbInSlice), (pSps->uiTotalMbCount - 1), "first_mb_in_slice",
                             GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_FIRST_MB_IN_SLICE));
  WELS_READ_VERIFY (BsGetBits (pBs, pSps->uiLog2MaxFrameNum, &uiCode)); //frame_num
  pSliceHead->iFrameNum = uiCode;

  pSliceHead->bFieldPicFlag    = false;
  pSliceHead->bBottomFiledFlag = false;
  if (!pSps->bFrameMbsOnlyFlag) {
    WelsLog (pLogCtx, WELS_LOG_WARNING, "ParseSliceHeaderSyntaxs(): frame_mbs_only_flag = %d not supported. ",
             pSps->bFrameMbsOnlyFlag);
    return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_UNSUPPORTED_MBAFF);
  }
  pSliceHead->iMbWidth  = pSps->iMbWidth;
  pSliceHead->iMbHeight = pSps->iMbHeight / (1 + pSliceHead->bFieldPicFlag);

  if (bIdrFlag) {
    if (pSliceHead->iFrameNum != 0) {
      WelsLog (pLogCtx, WELS_LOG_WARNING,
               "ParseSliceHeaderSyntaxs(), invaild frame number: %d due to IDR frame introduced!",
               pSliceHead->iFrameNum);
      return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_FRAME_NUM);
    }
    WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //idr_pic_id
    // standard 7.4.3 idr_pic_id should be in range 0 to 65535, inclusive.
    WELS_CHECK_SE_UPPER_ERROR (uiCode, SLICE_HEADER_IDR_PIC_ID_MAX, "idr_pic_id", GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER,
                               ERR_INFO_INVALID_IDR_PIC_ID));
    pSliceHead->uiIdrPicId = uiCode; /* uiIdrPicId */
#ifdef LONG_TERM_REF
    pCtx->uiCurIdrPicId = pSliceHead->uiIdrPicId;
#endif
  }

  pSliceHead->iDeltaPicOrderCntBottom = 0;
  pSliceHead->iDeltaPicOrderCnt[0] =
    pSliceHead->iDeltaPicOrderCnt[1] = 0;
  if (pSps->uiPocType == 0) {
    WELS_READ_VERIFY (BsGetBits (pBs, pSps->iLog2MaxPocLsb, &uiCode)); //pic_order_cnt_lsb
    const int32_t iMaxPocLsb = 1 << (pSps->iLog2MaxPocLsb);
    pSliceHead->iPicOrderCntLsb = uiCode;
    if (pPps->bPicOrderPresentFlag && !pSliceHead->bFieldPicFlag) {
      WELS_READ_VERIFY (BsGetSe (pBs, &iCode)); //delta_pic_order_cnt_bottom
      pSliceHead->iDeltaPicOrderCntBottom = iCode;
    }
    //Calculate poc if necessary
    int32_t pocLsb = pSliceHead->iPicOrderCntLsb;
    if (pSliceHead->bIdrFlag || kpCurNal->sNalHeaderExt.sNalUnitHeader.eNalUnitType == NAL_UNIT_CODED_SLICE_IDR) {
      pCtx->pLastDecPicInfo->iPrevPicOrderCntMsb = 0;
      pCtx->pLastDecPicInfo->iPrevPicOrderCntLsb = 0;
    }
    int32_t pocMsb;
    if (pocLsb < pCtx->pLastDecPicInfo->iPrevPicOrderCntLsb
        && pCtx->pLastDecPicInfo->iPrevPicOrderCntLsb - pocLsb >= iMaxPocLsb / 2)
      pocMsb = pCtx->pLastDecPicInfo->iPrevPicOrderCntMsb + iMaxPocLsb;
    else if (pocLsb > pCtx->pLastDecPicInfo->iPrevPicOrderCntLsb
             && pocLsb - pCtx->pLastDecPicInfo->iPrevPicOrderCntLsb > iMaxPocLsb / 2)
      pocMsb = pCtx->pLastDecPicInfo->iPrevPicOrderCntMsb - iMaxPocLsb;
    else
      pocMsb = pCtx->pLastDecPicInfo->iPrevPicOrderCntMsb;
    pSliceHead->iPicOrderCntLsb = pocMsb + pocLsb;

    if (pPps->bPicOrderPresentFlag && !pSliceHead->bFieldPicFlag) {
      pSliceHead->iPicOrderCntLsb += pSliceHead->iDeltaPicOrderCntBottom;
    }

    if (kpCurNal->sNalHeaderExt.sNalUnitHeader.uiNalRefIdc != 0) {
      pCtx->pLastDecPicInfo->iPrevPicOrderCntLsb = pocLsb;
      pCtx->pLastDecPicInfo->iPrevPicOrderCntMsb = pocMsb;
    }
    //End of Calculating poc
  } else if (pSps->uiPocType == 1 && !pSps->bDeltaPicOrderAlwaysZeroFlag) {
    WELS_READ_VERIFY (BsGetSe (pBs, &iCode)); //delta_pic_order_cnt[ 0 ]
    pSliceHead->iDeltaPicOrderCnt[0] = iCode;
    if (pPps->bPicOrderPresentFlag && !pSliceHead->bFieldPicFlag) {
      WELS_READ_VERIFY (BsGetSe (pBs, &iCode)); //delta_pic_order_cnt[ 1 ]
      pSliceHead->iDeltaPicOrderCnt[1] = iCode;
    }
  }
  pSliceHead->iRedundantPicCnt = 0;
  if (pPps->bRedundantPicCntPresentFlag) {
    WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //redundant_pic_cnt
    // standard section 7.4.3, redundant_pic_cnt should be in range 0 to 127, inclusive.
    WELS_CHECK_SE_UPPER_ERROR (uiCode, SLICE_HEADER_REDUNDANT_PIC_CNT_MAX, "redundant_pic_cnt",
                               GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_REDUNDANT_PIC_CNT));
    pSliceHead->iRedundantPicCnt = uiCode;
    if (pSliceHead->iRedundantPicCnt > 0) {
      WelsLog (pLogCtx, WELS_LOG_WARNING, "Redundant picture not supported!");
      return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_REDUNDANT_PIC_CNT);
    }
  }

  if (B_SLICE == uiSliceType) {
    //fix me: it needs to use the this flag somewhere for B-Sclice
    WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //direct_spatial_mv_pred_flag
    pSliceHead->iDirectSpatialMvPredFlag = uiCode;
  }

  //set defaults, might be overriden a few line later
  pSliceHead->uiRefCount[0] = pPps->uiNumRefIdxL0Active;
  pSliceHead->uiRefCount[1] = pPps->uiNumRefIdxL1Active;

  bool bReadNumRefFlag = (P_SLICE == uiSliceType || B_SLICE == uiSliceType);
  if (kbExtensionFlag) {
    bReadNumRefFlag &= (BASE_QUALITY_ID == pNalHeaderExt->uiQualityId);
  }
  if (bReadNumRefFlag) {
    WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //num_ref_idx_active_override_flag
    pSliceHead->bNumRefIdxActiveOverrideFlag = !!uiCode;
    if (pSliceHead->bNumRefIdxActiveOverrideFlag) {
      WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //num_ref_idx_l0_active_minus1
      WELS_CHECK_SE_UPPER_ERROR (uiCode, MAX_NUM_REF_IDX_L0_ACTIVE_MINUS1, "num_ref_idx_l0_active_minus1",
                                 GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_NUM_REF_IDX_L0_ACTIVE_MINUS1));
      pSliceHead->uiRefCount[0] = 1 + uiCode;
      if (B_SLICE == uiSliceType) {
        WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //num_ref_idx_l1_active_minus1
        WELS_CHECK_SE_UPPER_ERROR (uiCode, MAX_NUM_REF_IDX_L1_ACTIVE_MINUS1, "num_ref_idx_l1_active_minus1",
                                   GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_NUM_REF_IDX_L1_ACTIVE_MINUS1));
        pSliceHead->uiRefCount[1] = 1 + uiCode;
      }
    }
  }

  if (pSliceHead->uiRefCount[0] > MAX_REF_PIC_COUNT || pSliceHead->uiRefCount[1] > MAX_REF_PIC_COUNT) {
    WelsLog (pLogCtx, WELS_LOG_WARNING, "reference overflow");
    return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_REF_COUNT_OVERFLOW);
  }

  if (BASE_QUALITY_ID == uiQualityId) {
    iRet = ParseRefPicListReordering (pBs, pSliceHead);
    if (iRet != ERR_NONE) {
      WelsLog (pLogCtx, WELS_LOG_WARNING, "invalid ref pPic list reordering syntaxs!");
      return iRet;
    }

    if ((pPps->bWeightedPredFlag && uiSliceType == P_SLICE) || (pPps->uiWeightedBipredIdc == 1 && uiSliceType == B_SLICE)) {
      iRet = ParsePredWeightedTable (pBs, pSliceHead);
      if (iRet != ERR_NONE) {
        WelsLog (pLogCtx, WELS_LOG_WARNING, "invalid weighted prediction syntaxs!");
        return iRet;
      }
    }

    if (kbExtensionFlag) {
      if (pNalHeaderExt->iNoInterLayerPredFlag || pNalHeaderExt->uiQualityId > 0)
        pSliceHeadExt->bBasePredWeightTableFlag = false;
      else
        pSliceHeadExt->bBasePredWeightTableFlag = true;
    }

    if (kpCurNal->sNalHeaderExt.sNalUnitHeader.uiNalRefIdc != 0) {
      iRet = ParseDecRefPicMarking (pCtx, pBs, pSliceHead, pSps, bIdrFlag);
      if (iRet != ERR_NONE) {
        return iRet;
      }

      if (kbExtensionFlag && !pSubsetSps->sSpsSvcExt.bSliceHeaderRestrictionFlag) {
        WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //store_ref_base_pic_flag
        pSliceHeadExt->bStoreRefBasePicFlag = !!uiCode;
        if ((pNalHeaderExt->bUseRefBasePicFlag || pSliceHeadExt->bStoreRefBasePicFlag) && !bIdrFlag) {
          WelsLog (pLogCtx, WELS_LOG_WARNING,
                   "ParseSliceHeaderSyntaxs(): bUseRefBasePicFlag or bStoreRefBasePicFlag = 1 not supported.");
          return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_UNSUPPORTED_ILP);
        }
      }
    }
  }

  if (pPps->bEntropyCodingModeFlag) {
    if (pSliceHead->eSliceType != I_SLICE && pSliceHead->eSliceType != SI_SLICE) {
      WELS_READ_VERIFY (BsGetUe (pBs, &uiCode));
      WELS_CHECK_SE_UPPER_ERROR (uiCode, SLICE_HEADER_CABAC_INIT_IDC_MAX, "cabac_init_idc", ERR_INFO_INVALID_CABAC_INIT_IDC);
      pSliceHead->iCabacInitIdc = uiCode;
    } else
      pSliceHead->iCabacInitIdc = 0;
  }

  WELS_READ_VERIFY (BsGetSe (pBs, &iCode)); //slice_qp_delta
  pSliceHead->iSliceQpDelta     = iCode;
  pSliceHead->iSliceQp          = pPps->iPicInitQp + pSliceHead->iSliceQpDelta;
  if (pSliceHead->iSliceQp < 0 || pSliceHead->iSliceQp > 51) {
    WelsLog (pLogCtx, WELS_LOG_WARNING, "QP %d out of range", pSliceHead->iSliceQp);
    return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_QP);
  }

  //FIXME qscale / qp ... stuff
  if (!kbExtensionFlag) {
    if (uiSliceType == SP_SLICE || uiSliceType == SI_SLICE) {
      WelsLog (pLogCtx, WELS_LOG_WARNING, "SP/SI not supported");
      return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_UNSUPPORTED_SPSI);
    }
  }

  pSliceHead->uiDisableDeblockingFilterIdc = 0;
  pSliceHead->iSliceAlphaC0Offset          = 0;
  pSliceHead->iSliceBetaOffset             = 0;
  if (pPps->bDeblockingFilterControlPresentFlag) {
    WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //disable_deblocking_filter_idc
    pSliceHead->uiDisableDeblockingFilterIdc = uiCode;
    //refer to JVT-X201wcm1.doc G.7.4.3.4--2010.4.20
    if (pSliceHead->uiDisableDeblockingFilterIdc > 6) {
      WelsLog (pLogCtx, WELS_LOG_WARNING, "disable_deblock_filter_idc (%d) out of range [0, 6]",
               pSliceHead->uiDisableDeblockingFilterIdc);
      return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_DBLOCKING_IDC);
    }
    if (pSliceHead->uiDisableDeblockingFilterIdc != 1) {
      WELS_READ_VERIFY (BsGetSe (pBs, &iCode)); //slice_alpha_c0_offset_div2
      pSliceHead->iSliceAlphaC0Offset = iCode * 2;
      WELS_CHECK_SE_BOTH_ERROR (pSliceHead->iSliceAlphaC0Offset, SLICE_HEADER_ALPHAC0_BETA_OFFSET_MIN,
                                SLICE_HEADER_ALPHAC0_BETA_OFFSET_MAX, "slice_alpha_c0_offset_div2 * 2", GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER,
                                    ERR_INFO_INVALID_SLICE_ALPHA_C0_OFFSET_DIV2));
      WELS_READ_VERIFY (BsGetSe (pBs, &iCode)); //slice_beta_offset_div2
      pSliceHead->iSliceBetaOffset = iCode * 2;
      WELS_CHECK_SE_BOTH_ERROR (pSliceHead->iSliceBetaOffset, SLICE_HEADER_ALPHAC0_BETA_OFFSET_MIN,
                                SLICE_HEADER_ALPHAC0_BETA_OFFSET_MAX, "slice_beta_offset_div2 * 2", GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER,
                                    ERR_INFO_INVALID_SLICE_BETA_OFFSET_DIV2));
    }
  }

  bSgChangeCycleInvolved = (pPps->uiNumSliceGroups > 1 && pPps->uiSliceGroupMapType >= 3
                            && pPps->uiSliceGroupMapType <= 5);
  if (kbExtensionFlag && bSgChangeCycleInvolved)
    bSgChangeCycleInvolved = (bSgChangeCycleInvolved && (uiQualityId == BASE_QUALITY_ID));
  if (bSgChangeCycleInvolved) {
    if (pPps->uiSliceGroupChangeRate > 0) {
      const int32_t kiNumBits = (int32_t)WELS_CEIL (log (static_cast<double> (1 + pPps->uiPicSizeInMapUnits /
                                pPps->uiSliceGroupChangeRate)));
      WELS_READ_VERIFY (BsGetBits (pBs, kiNumBits, &uiCode)); //lice_group_change_cycle
      pSliceHead->iSliceGroupChangeCycle = uiCode;
    } else
      pSliceHead->iSliceGroupChangeCycle = 0;
  }

  if (!kbExtensionFlag) {
    FillDefaultSliceHeaderExt (pSliceHeadExt, pNalHeaderExt);
  } else {
    /* Extra syntax elements newly introduced */
    pSliceHeadExt->pSubsetSps = pSubsetSps;

    if (!pNalHeaderExt->iNoInterLayerPredFlag && BASE_QUALITY_ID == uiQualityId) {
      //the following should be deleted for CODE_CLEAN
      WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //ref_layer_dq_id
      pSliceHeadExt->uiRefLayerDqId = uiCode;
      if (pSubsetSps->sSpsSvcExt.bInterLayerDeblockingFilterCtrlPresentFlag) {
        WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //disable_inter_layer_deblocking_filter_idc
        pSliceHeadExt->uiDisableInterLayerDeblockingFilterIdc = uiCode;
        //refer to JVT-X201wcm1.doc G.7.4.3.4--2010.4.20
        if (pSliceHeadExt->uiDisableInterLayerDeblockingFilterIdc > 6) {
          WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, "disable_inter_layer_deblock_filter_idc (%d) out of range [0, 6]",
                   pSliceHeadExt->uiDisableInterLayerDeblockingFilterIdc);
          return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_DBLOCKING_IDC);
        }
        if (pSliceHeadExt->uiDisableInterLayerDeblockingFilterIdc != 1) {
          WELS_READ_VERIFY (BsGetSe (pBs, &iCode)); //inter_layer_slice_alpha_c0_offset_div2
          pSliceHeadExt->iInterLayerSliceAlphaC0Offset = iCode * 2;
          WELS_CHECK_SE_BOTH_ERROR (pSliceHeadExt->iInterLayerSliceAlphaC0Offset,
                                    SLICE_HEADER_INTER_LAYER_ALPHAC0_BETA_OFFSET_MIN, SLICE_HEADER_INTER_LAYER_ALPHAC0_BETA_OFFSET_MAX,
                                    "inter_layer_alpha_c0_offset_div2 * 2", GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER,
                                        ERR_INFO_INVALID_SLICE_ALPHA_C0_OFFSET_DIV2));
          WELS_READ_VERIFY (BsGetSe (pBs, &iCode)); //inter_layer_slice_beta_offset_div2
          pSliceHeadExt->iInterLayerSliceBetaOffset = iCode * 2;
          WELS_CHECK_SE_BOTH_ERROR (pSliceHeadExt->iInterLayerSliceBetaOffset, SLICE_HEADER_INTER_LAYER_ALPHAC0_BETA_OFFSET_MIN,
                                    SLICE_HEADER_INTER_LAYER_ALPHAC0_BETA_OFFSET_MAX, "inter_layer_slice_beta_offset_div2 * 2",
                                    GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_INVALID_SLICE_BETA_OFFSET_DIV2));
        }
      }

      pSliceHeadExt->uiRefLayerChromaPhaseXPlus1Flag = pSubsetSps->sSpsSvcExt.uiSeqRefLayerChromaPhaseXPlus1Flag;
      pSliceHeadExt->uiRefLayerChromaPhaseYPlus1     = pSubsetSps->sSpsSvcExt.uiSeqRefLayerChromaPhaseYPlus1;

      WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //constrained_intra_resampling_flag
      pSliceHeadExt->bConstrainedIntraResamplingFlag = !!uiCode;

      {
        SPosOffset pos;
        pos.iLeftOffset   = pSubsetSps->sSpsSvcExt.sSeqScaledRefLayer.iLeftOffset;
        pos.iTopOffset    = pSubsetSps->sSpsSvcExt.sSeqScaledRefLayer.iTopOffset * (2 - pSps->bFrameMbsOnlyFlag);
        pos.iRightOffset  = pSubsetSps->sSpsSvcExt.sSeqScaledRefLayer.iRightOffset;
        pos.iBottomOffset = pSubsetSps->sSpsSvcExt.sSeqScaledRefLayer.iBottomOffset * (2 - pSps->bFrameMbsOnlyFlag);
        //memcpy(&pSliceHeadExt->sScaledRefLayer, &pos, sizeof(SPosOffset));//confirmed_safe_unsafe_usage
        pSliceHeadExt->iScaledRefLayerPicWidthInSampleLuma  = (pSliceHead->iMbWidth << 4) -
            (pos.iLeftOffset + pos.iRightOffset);
        pSliceHeadExt->iScaledRefLayerPicHeightInSampleLuma = (pSliceHead->iMbHeight << 4) -
            (pos.iTopOffset + pos.iBottomOffset) / (1 + pSliceHead->bFieldPicFlag);
      }
    } else if (uiQualityId > BASE_QUALITY_ID) {
      WelsLog (pLogCtx, WELS_LOG_WARNING, "MGS not supported.");
      return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_UNSUPPORTED_MGS);
    } else {
      pSliceHeadExt->uiRefLayerDqId = (uint8_t) - 1;
    }

    pSliceHeadExt->bSliceSkipFlag            = false;
    pSliceHeadExt->bAdaptiveBaseModeFlag     = false;
    pSliceHeadExt->bDefaultBaseModeFlag      = false;
    pSliceHeadExt->bAdaptiveMotionPredFlag   = false;
    pSliceHeadExt->bDefaultMotionPredFlag    = false;
    pSliceHeadExt->bAdaptiveResidualPredFlag = false;
    pSliceHeadExt->bDefaultResidualPredFlag  = false;
    if (pNalHeaderExt->iNoInterLayerPredFlag)
      pSliceHeadExt->bTCoeffLevelPredFlag    = false;
    else
      pSliceHeadExt->bTCoeffLevelPredFlag    = pSubsetSps->sSpsSvcExt.bSeqTCoeffLevelPredFlag;

    if (!pNalHeaderExt->iNoInterLayerPredFlag) {
      WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //slice_skip_flag
      pSliceHeadExt->bSliceSkipFlag = !!uiCode;
      if (pSliceHeadExt->bSliceSkipFlag) {
        WelsLog (pLogCtx, WELS_LOG_WARNING, "bSliceSkipFlag == 1 not supported.");
        return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_UNSUPPORTED_SLICESKIP);
      } else {
        WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //adaptive_base_mode_flag
        pSliceHeadExt->bAdaptiveBaseModeFlag = !!uiCode;
        if (!pSliceHeadExt->bAdaptiveBaseModeFlag) {
          WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //default_base_mode_flag
          pSliceHeadExt->bDefaultBaseModeFlag = !!uiCode;
        }
        if (!pSliceHeadExt->bDefaultBaseModeFlag) {
          WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //adaptive_motion_prediction_flag
          pSliceHeadExt->bAdaptiveMotionPredFlag = !!uiCode;
          if (!pSliceHeadExt->bAdaptiveMotionPredFlag) {
            WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //default_motion_prediction_flag
            pSliceHeadExt->bDefaultMotionPredFlag = !!uiCode;
          }
        }

        WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //adaptive_residual_prediction_flag
        pSliceHeadExt->bAdaptiveResidualPredFlag = !!uiCode;
        if (!pSliceHeadExt->bAdaptiveResidualPredFlag) {
          WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //default_residual_prediction_flag
          pSliceHeadExt->bDefaultResidualPredFlag = !!uiCode;
        }
      }
      if (pSubsetSps->sSpsSvcExt.bAdaptiveTCoeffLevelPredFlag) {
        WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //tcoeff_level_prediction_flag
        pSliceHeadExt->bTCoeffLevelPredFlag = !!uiCode;
      }
    }

    if (!pSubsetSps->sSpsSvcExt.bSliceHeaderRestrictionFlag) {
      WELS_READ_VERIFY (BsGetBits (pBs, 4, &uiCode)); //scan_idx_start
      pSliceHeadExt->uiScanIdxStart = uiCode;
      WELS_READ_VERIFY (BsGetBits (pBs, 4, &uiCode)); //scan_idx_end
      pSliceHeadExt->uiScanIdxEnd = uiCode;
      if (pSliceHeadExt->uiScanIdxStart != 0 || pSliceHeadExt->uiScanIdxEnd != 15) {
        WelsLog (pLogCtx, WELS_LOG_WARNING, "uiScanIdxStart (%d) != 0 and uiScanIdxEnd (%d) !=15 not supported here",
                 pSliceHeadExt->uiScanIdxStart, pSliceHeadExt->uiScanIdxEnd);
        return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_UNSUPPORTED_MGS);
      }
    } else {
      pSliceHeadExt->uiScanIdxStart = 0;
      pSliceHeadExt->uiScanIdxEnd   = 15;
    }
  }

  return ERR_NONE;
}

/*
 *  Copy relative syntax elements of NALUnitHeaderExt, sRefPicBaseMarking and bStoreRefBasePicFlag in prefix nal unit.
 *  pSrc:   mark as decoded prefix NAL
 *  ppDst:  succeeded VCL NAL based AVC (I/P Slice)
 */
bool PrefetchNalHeaderExtSyntax (PWelsDecoderContext pCtx, PNalUnit const kppDst, PNalUnit const kpSrc) {
  PNalUnitHeaderExt pNalHdrExtD = NULL, pNalHdrExtS = NULL;
  PSliceHeaderExt pShExtD = NULL;
  PPrefixNalUnit pPrefixS = NULL;
  PSps pSps = NULL;
  int32_t iIdx = 0;

  if (kppDst == NULL || kpSrc == NULL)
    return false;

  pNalHdrExtD   = &kppDst->sNalHeaderExt;
  pNalHdrExtS   = &kpSrc->sNalHeaderExt;
  pShExtD       = &kppDst->sNalData.sVclNal.sSliceHeaderExt;
  pPrefixS      = &kpSrc->sNalData.sPrefixNal;
  pSps          = &pCtx->sSpsPpsCtx.sSpsBuffer[pCtx->sSpsPpsCtx.sPpsBuffer[pShExtD->sSliceHeader.iPpsId].iSpsId];

  pNalHdrExtD->uiDependencyId           = pNalHdrExtS->uiDependencyId;
  pNalHdrExtD->uiQualityId              = pNalHdrExtS->uiQualityId;
  pNalHdrExtD->uiTemporalId             = pNalHdrExtS->uiTemporalId;
  pNalHdrExtD->uiPriorityId             = pNalHdrExtS->uiPriorityId;
  pNalHdrExtD->bIdrFlag                 = pNalHdrExtS->bIdrFlag;
  pNalHdrExtD->iNoInterLayerPredFlag    = pNalHdrExtS->iNoInterLayerPredFlag;
  pNalHdrExtD->bDiscardableFlag         = pNalHdrExtS->bDiscardableFlag;
  pNalHdrExtD->bOutputFlag              = pNalHdrExtS->bOutputFlag;
  pNalHdrExtD->bUseRefBasePicFlag       = pNalHdrExtS->bUseRefBasePicFlag;
  pNalHdrExtD->uiLayerDqId              = pNalHdrExtS->uiLayerDqId;

  pShExtD->bStoreRefBasePicFlag         = pPrefixS->bStoreRefBasePicFlag;
  memcpy (&pShExtD->sRefBasePicMarking, &pPrefixS->sRefPicBaseMarking,
          sizeof (SRefBasePicMarking)); //confirmed_safe_unsafe_usage
  if (pShExtD->sRefBasePicMarking.bAdaptiveRefBasePicMarkingModeFlag) {
    PRefBasePicMarking pRefBasePicMarking = &pShExtD->sRefBasePicMarking;
    iIdx = 0;
    do {
      if (pRefBasePicMarking->mmco_base[iIdx].uiMmcoType == MMCO_END)
        break;
      if (pRefBasePicMarking->mmco_base[iIdx].uiMmcoType == MMCO_SHORT2UNUSED)
        pRefBasePicMarking->mmco_base[iIdx].iShortFrameNum = (pShExtD->sSliceHeader.iFrameNum -
            pRefBasePicMarking->mmco_base[iIdx].uiDiffOfPicNums) & ((1 << pSps->uiLog2MaxFrameNum) - 1);
      ++ iIdx;
    } while (iIdx < MAX_MMCO_COUNT);
  }

  return true;
}



int32_t UpdateAccessUnit (PWelsDecoderContext pCtx) {
  PAccessUnit pCurAu   = pCtx->pAccessUnitList;
  int32_t iIdx         = pCurAu->uiEndPos;

  // Conversed iterator
  pCtx->uiTargetDqId = pCurAu->pNalUnitsList[iIdx]->sNalHeaderExt.uiLayerDqId;
  pCurAu->uiActualUnitsNum  = iIdx + 1;
  pCurAu->bCompletedAuFlag = true;

  // Added for mosaic avoidance, 11/19/2009
#ifdef LONG_TERM_REF
  if (pCtx->bParamSetsLostFlag || pCtx->bNewSeqBegin)
#else
  if (pCtx->bReferenceLostAtT0Flag || pCtx->bNewSeqBegin)
#endif
  {
    uint32_t uiActualIdx = 0;
    while (uiActualIdx < pCurAu->uiActualUnitsNum) {
      PNalUnit nal = pCurAu->pNalUnitsList[uiActualIdx];

      if (nal->sNalHeaderExt.sNalUnitHeader.eNalUnitType == NAL_UNIT_CODED_SLICE_IDR || nal->sNalHeaderExt.bIdrFlag) {
        break;
      }
      ++ uiActualIdx;
    }
    if (uiActualIdx ==
        pCurAu->uiActualUnitsNum) { // no found IDR nal within incoming AU, need exit to avoid mosaic issue, 11/19/2009

      pCtx->pDecoderStatistics->uiIDRLostNum++;
      if (!pCtx->bParamSetsLostFlag)
        WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING,
                 "UpdateAccessUnit():::::Key frame lost.....CAN NOT find IDR from current AU.");
      pCtx->iErrorCode |= dsRefLost;
      if (pCtx->pParam->eEcActiveIdc == ERROR_CON_DISABLE) {
#ifdef LONG_TERM_REF
        pCtx->iErrorCode |= dsNoParamSets;
        return dsNoParamSets;
#else
        pCtx->iErrorCode |= dsRefLost;
        return ERR_INFO_REFERENCE_PIC_LOST;
#endif
      }
    }
  }

  return ERR_NONE;
}

int32_t InitialDqLayersContext (PWelsDecoderContext pCtx, const int32_t kiMaxWidth, const int32_t kiMaxHeight) {
  int32_t i = 0;
  WELS_VERIFY_RETURN_IF (ERR_INFO_INVALID_PARAM, (NULL == pCtx || kiMaxWidth <= 0 || kiMaxHeight <= 0))
  pCtx->sMb.iMbWidth  = (kiMaxWidth + 15) >> 4;
  pCtx->sMb.iMbHeight = (kiMaxHeight + 15) >> 4;

  if (pCtx->bInitialDqLayersMem && kiMaxWidth <= pCtx->iPicWidthReq
      && kiMaxHeight <= pCtx->iPicHeightReq) // have same dimension memory, skipped
    return ERR_NONE;

  CMemoryAlign* pMa = pCtx->pMemAlign;

  UninitialDqLayersContext (pCtx);

  do {
    PDqLayer pDq = (PDqLayer)pMa->WelsMallocz (sizeof (SDqLayer), "PDqLayer");

    if (pDq == NULL)
      return ERR_INFO_OUT_OF_MEMORY;

    pCtx->pDqLayersList[i] = pDq; //to keep consistence with in UninitialDqLayersContext()
    memset (pDq, 0, sizeof (SDqLayer));

    pCtx->sMb.pMbType[i] = (uint32_t*)pMa->WelsMallocz (pCtx->sMb.iMbWidth * pCtx->sMb.iMbHeight * sizeof (uint32_t),
                           "pCtx->sMb.pMbType[]");
    pCtx->sMb.pMv[i][LIST_0] = (int16_t (*)[16][2])pMa->WelsMallocz (pCtx->sMb.iMbWidth * pCtx->sMb.iMbHeight * sizeof (
                                 int16_t) * MV_A * MB_BLOCK4x4_NUM, "pCtx->sMb.pMv[][]");
    pCtx->sMb.pMv[i][LIST_1] = (int16_t (*)[16][2])pMa->WelsMallocz (pCtx->sMb.iMbWidth * pCtx->sMb.iMbHeight * sizeof (
                                 int16_t) * MV_A * MB_BLOCK4x4_NUM, "pCtx->sMb.pMv[][]");

    pCtx->sMb.pRefIndex[i][LIST_0] = (int8_t (*)[MB_BLOCK4x4_NUM])pMa->WelsMallocz (pCtx->sMb.iMbWidth *
                                     pCtx->sMb.iMbHeight *
                                     sizeof (
                                       int8_t) * MB_BLOCK4x4_NUM, "pCtx->sMb.pRefIndex[][]");
    pCtx->sMb.pRefIndex[i][LIST_1] = (int8_t (*)[MB_BLOCK4x4_NUM])pMa->WelsMallocz (pCtx->sMb.iMbWidth *
                                     pCtx->sMb.iMbHeight *
                                     sizeof (
                                       int8_t) * MB_BLOCK4x4_NUM, "pCtx->sMb.pRefIndex[][]");
    pCtx->sMb.pDirect[i] = (int8_t (*)[MB_BLOCK4x4_NUM])pMa->WelsMallocz (pCtx->sMb.iMbWidth * pCtx->sMb.iMbHeight *
                           sizeof (
                             int8_t) * MB_BLOCK4x4_NUM, "pCtx->sMb.pDirect[]");
    pCtx->sMb.pLumaQp[i] = (int8_t*)pMa->WelsMallocz (pCtx->sMb.iMbWidth * pCtx->sMb.iMbHeight * sizeof (int8_t),
                           "pCtx->sMb.pLumaQp[]");
    pCtx->sMb.pNoSubMbPartSizeLessThan8x8Flag[i] = (bool*)pMa->WelsMallocz (pCtx->sMb.iMbWidth * pCtx->sMb.iMbHeight *
        sizeof (
          bool),
        "pCtx->sMb.pNoSubMbPartSizeLessThan8x8Flag[]");
    pCtx->sMb.pTransformSize8x8Flag[i] = (bool*)pMa->WelsMallocz (pCtx->sMb.iMbWidth * pCtx->sMb.iMbHeight * sizeof (
                                           bool),
                                         "pCtx->sMb.pTransformSize8x8Flag[]");
    pCtx->sMb.pChromaQp[i] = (int8_t (*)[2])pMa->WelsMallocz (pCtx->sMb.iMbWidth * pCtx->sMb.iMbHeight * sizeof (
                               int8_t) * 2,
                             "pCtx->sMb.pChromaQp[]");
    pCtx->sMb.pMvd[i][LIST_0] = (int16_t (*)[16][2])pMa->WelsMallocz (pCtx->sMb.iMbWidth * pCtx->sMb.iMbHeight * sizeof (
                                  int16_t) * MV_A * MB_BLOCK4x4_NUM, "pCtx->sMb.pMvd[][]");
    pCtx->sMb.pMvd[i][LIST_1] = (int16_t (*)[16][2])pMa->WelsMallocz (pCtx->sMb.iMbWidth * pCtx->sMb.iMbHeight * sizeof (
                                  int16_t) * MV_A * MB_BLOCK4x4_NUM, "pCtx->sMb.pMvd[][]");
    pCtx->sMb.pCbfDc[i] = (uint16_t*)pMa->WelsMallocz (pCtx->sMb.iMbWidth * pCtx->sMb.iMbHeight * sizeof (uint16_t),
                          "pCtx->sMb.pCbfDc[]");
    pCtx->sMb.pNzc[i] = (int8_t (*)[24])pMa->WelsMallocz (pCtx->sMb.iMbWidth * pCtx->sMb.iMbHeight * sizeof (
                          int8_t) * 24,
                        "pCtx->sMb.pNzc[]");
    pCtx->sMb.pNzcRs[i] = (int8_t (*)[24])pMa->WelsMallocz (pCtx->sMb.iMbWidth * pCtx->sMb.iMbHeight * sizeof (
                            int8_t) * 24,
                          "pCtx->sMb.pNzcRs[]");
    pCtx->sMb.pScaledTCoeff[i] = (int16_t (*)[MB_COEFF_LIST_SIZE])pMa->WelsMallocz (pCtx->sMb.iMbWidth *
                                 pCtx->sMb.iMbHeight *
                                 sizeof (int16_t) * MB_COEFF_LIST_SIZE, "pCtx->sMb.pScaledTCoeff[]");
    pCtx->sMb.pIntraPredMode[i] = (int8_t (*)[8])pMa->WelsMallocz (pCtx->sMb.iMbWidth * pCtx->sMb.iMbHeight * sizeof (
                                    int8_t) * 8,
                                  "pCtx->sMb.pIntraPredMode[]");
    pCtx->sMb.pIntra4x4FinalMode[i] = (int8_t (*)[MB_BLOCK4x4_NUM])pMa->WelsMallocz (pCtx->sMb.iMbWidth *
                                      pCtx->sMb.iMbHeight *
                                      sizeof (int8_t) * MB_BLOCK4x4_NUM, "pCtx->sMb.pIntra4x4FinalMode[]");
    pCtx->sMb.pIntraNxNAvailFlag[i] = (uint8_t (*))pMa->WelsMallocz (pCtx->sMb.iMbWidth * pCtx->sMb.iMbHeight * sizeof (
                                        int8_t),
                                      "pCtx->sMb.pIntraNxNAvailFlag");
    pCtx->sMb.pChromaPredMode[i] = (int8_t*)pMa->WelsMallocz (pCtx->sMb.iMbWidth * pCtx->sMb.iMbHeight * sizeof (int8_t),
                                   "pCtx->sMb.pChromaPredMode[]");
    pCtx->sMb.pCbp[i] = (int8_t*)pMa->WelsMallocz (pCtx->sMb.iMbWidth * pCtx->sMb.iMbHeight * sizeof (int8_t),
                        "pCtx->sMb.pCbp[]");
    pCtx->sMb.pSubMbType[i] = (uint32_t (*)[MB_PARTITION_SIZE])pMa->WelsMallocz (pCtx->sMb.iMbWidth *
                              pCtx->sMb.iMbHeight *
                              sizeof (
                                uint32_t) * MB_PARTITION_SIZE, "pCtx->sMb.pSubMbType[]");
    pCtx->sMb.pSliceIdc[i] = (int32_t*) pMa->WelsMallocz (pCtx->sMb.iMbWidth * pCtx->sMb.iMbHeight * sizeof (int32_t),
                             "pCtx->sMb.pSliceIdc[]"); // using int32_t for slice_idc, 4/21/2010
    pCtx->sMb.pResidualPredFlag[i] = (int8_t*) pMa->WelsMallocz (pCtx->sMb.iMbWidth * pCtx->sMb.iMbHeight * sizeof (
                                       int8_t),
                                     "pCtx->sMb.pResidualPredFlag[]");
    pCtx->sMb.pInterPredictionDoneFlag[i] = (int8_t*) pMa->WelsMallocz (pCtx->sMb.iMbWidth * pCtx->sMb.iMbHeight *
                                            sizeof (
                                                int8_t), "pCtx->sMb.pInterPredictionDoneFlag[]");

    pCtx->sMb.pMbCorrectlyDecodedFlag[i] = (bool*) pMa->WelsMallocz (pCtx->sMb.iMbWidth * pCtx->sMb.iMbHeight * sizeof (
        bool),
                                           "pCtx->sMb.pMbCorrectlyDecodedFlag[]");
    pCtx->sMb.pMbRefConcealedFlag[i] = (bool*) pMa->WelsMallocz (pCtx->sMb.iMbWidth * pCtx->sMb.iMbHeight * sizeof (
                                         bool),
                                       "pCtx->pMbRefConcealedFlag[]");

    // check memory block valid due above allocated..
    WELS_VERIFY_RETURN_IF (ERR_INFO_OUT_OF_MEMORY,
                           ((NULL == pCtx->sMb.pMbType[i]) ||
                            (NULL == pCtx->sMb.pMv[i][LIST_0]) ||
                            (NULL == pCtx->sMb.pMv[i][LIST_1]) ||
                            (NULL == pCtx->sMb.pRefIndex[i][LIST_0]) ||
                            (NULL == pCtx->sMb.pRefIndex[i][LIST_1]) ||
                            (NULL == pCtx->sMb.pDirect[i]) ||
                            (NULL == pCtx->sMb.pLumaQp[i]) ||
                            (NULL == pCtx->sMb.pNoSubMbPartSizeLessThan8x8Flag[i]) ||
                            (NULL == pCtx->sMb.pTransformSize8x8Flag[i]) ||
                            (NULL == pCtx->sMb.pChromaQp[i]) ||
                            (NULL == pCtx->sMb.pMvd[i][LIST_0]) ||
                            (NULL == pCtx->sMb.pMvd[i][LIST_1]) ||
                            (NULL == pCtx->sMb.pCbfDc[i]) ||
                            (NULL == pCtx->sMb.pNzc[i]) ||
                            (NULL == pCtx->sMb.pNzcRs[i]) ||
                            (NULL == pCtx->sMb.pScaledTCoeff[i]) ||
                            (NULL == pCtx->sMb.pIntraPredMode[i]) ||
                            (NULL == pCtx->sMb.pIntra4x4FinalMode[i]) ||
                            (NULL == pCtx->sMb.pIntraNxNAvailFlag[i]) ||
                            (NULL == pCtx->sMb.pChromaPredMode[i]) ||
                            (NULL == pCtx->sMb.pCbp[i]) ||
                            (NULL == pCtx->sMb.pSubMbType[i]) ||
                            (NULL == pCtx->sMb.pSliceIdc[i]) ||
                            (NULL == pCtx->sMb.pResidualPredFlag[i]) ||
                            (NULL == pCtx->sMb.pInterPredictionDoneFlag[i]) ||
                            (NULL == pCtx->sMb.pMbRefConcealedFlag[i]) ||
                            (NULL == pCtx->sMb.pMbCorrectlyDecodedFlag[i])
                           )
                          )

    memset (pCtx->sMb.pSliceIdc[i], 0xff, (pCtx->sMb.iMbWidth * pCtx->sMb.iMbHeight * sizeof (int32_t)));

    ++ i;
  } while (i < LAYER_NUM_EXCHANGEABLE);

  pCtx->bInitialDqLayersMem     = true;
  pCtx->iPicWidthReq            = kiMaxWidth;
  pCtx->iPicHeightReq           = kiMaxHeight;

  return ERR_NONE;
}



void UninitialDqLayersContext (PWelsDecoderContext pCtx) {
  int32_t i = 0;
  CMemoryAlign* pMa = pCtx->pMemAlign;

  do {
    PDqLayer pDq = pCtx->pDqLayersList[i];
    if (pDq == NULL) {
      ++ i;
      continue;
    }

    if (pCtx->sMb.pMbType[i]) {
      pMa->WelsFree (pCtx->sMb.pMbType[i], "pCtx->sMb.pMbType[]");

      pCtx->sMb.pMbType[i] = NULL;
    }

    for (int32_t listIdx = LIST_0; listIdx < LIST_A; ++listIdx) {
      if (pCtx->sMb.pMv[i][listIdx]) {
        pMa->WelsFree (pCtx->sMb.pMv[i][listIdx], "pCtx->sMb.pMv[][]");
        pCtx->sMb.pMv[i][listIdx] = NULL;
      }

      if (pCtx->sMb.pRefIndex[i][listIdx]) {
        pMa->WelsFree (pCtx->sMb.pRefIndex[i][listIdx], "pCtx->sMb.pRefIndex[][]");
        pCtx->sMb.pRefIndex[i][listIdx] = NULL;
      }

      if (pCtx->sMb.pDirect[i]) {
        pMa->WelsFree (pCtx->sMb.pDirect[i], "pCtx->sMb.pDirect[]");
        pCtx->sMb.pDirect[i] = NULL;
      }

      if (pCtx->sMb.pMvd[i][listIdx]) {
        pMa->WelsFree (pCtx->sMb.pMvd[i][listIdx], "pCtx->sMb.pMvd[][]");
        pCtx->sMb.pMvd[i][listIdx] = NULL;
      }
    }

    if (pCtx->sMb.pNoSubMbPartSizeLessThan8x8Flag[i]) {
      pMa->WelsFree (pCtx->sMb.pNoSubMbPartSizeLessThan8x8Flag[i], "pCtx->sMb.pNoSubMbPartSizeLessThan8x8Flag[]");

      pCtx->sMb.pNoSubMbPartSizeLessThan8x8Flag[i] = NULL;
    }

    if (pCtx->sMb.pTransformSize8x8Flag[i]) {
      pMa->WelsFree (pCtx->sMb.pTransformSize8x8Flag[i], "pCtx->sMb.pTransformSize8x8Flag[]");

      pCtx->sMb.pTransformSize8x8Flag[i] = NULL;
    }

    if (pCtx->sMb.pLumaQp[i]) {
      pMa->WelsFree (pCtx->sMb.pLumaQp[i], "pCtx->sMb.pLumaQp[]");

      pCtx->sMb.pLumaQp[i] = NULL;
    }

    if (pCtx->sMb.pChromaQp[i]) {
      pMa->WelsFree (pCtx->sMb.pChromaQp[i], "pCtx->sMb.pChromaQp[]");

      pCtx->sMb.pChromaQp[i] = NULL;
    }

    if (pCtx->sMb.pCbfDc[i]) {
      pMa->WelsFree (pCtx->sMb.pCbfDc[i], "pCtx->sMb.pCbfDc[]");
      pCtx->sMb.pCbfDc[i] = NULL;
    }

    if (pCtx->sMb.pNzc[i]) {
      pMa->WelsFree (pCtx->sMb.pNzc[i], "pCtx->sMb.pNzc[]");

      pCtx->sMb.pNzc[i] = NULL;
    }

    if (pCtx->sMb.pNzcRs[i]) {
      pMa->WelsFree (pCtx->sMb.pNzcRs[i], "pCtx->sMb.pNzcRs[]");

      pCtx->sMb.pNzcRs[i] = NULL;
    }

    if (pCtx->sMb.pScaledTCoeff[i]) {
      pMa->WelsFree (pCtx->sMb.pScaledTCoeff[i], "pCtx->sMb.pScaledTCoeff[]");

      pCtx->sMb.pScaledTCoeff[i] = NULL;
    }

    if (pCtx->sMb.pIntraPredMode[i]) {
      pMa->WelsFree (pCtx->sMb.pIntraPredMode[i], "pCtx->sMb.pIntraPredMode[]");

      pCtx->sMb.pIntraPredMode[i] = NULL;
    }

    if (pCtx->sMb.pIntra4x4FinalMode[i]) {
      pMa->WelsFree (pCtx->sMb.pIntra4x4FinalMode[i], "pCtx->sMb.pIntra4x4FinalMode[]");

      pCtx->sMb.pIntra4x4FinalMode[i] = NULL;
    }

    if (pCtx->sMb.pIntraNxNAvailFlag[i]) {
      pMa->WelsFree (pCtx->sMb.pIntraNxNAvailFlag[i], "pCtx->sMb.pIntraNxNAvailFlag");

      pCtx->sMb.pIntraNxNAvailFlag[i] = NULL;
    }

    if (pCtx->sMb.pChromaPredMode[i]) {
      pMa->WelsFree (pCtx->sMb.pChromaPredMode[i], "pCtx->sMb.pChromaPredMode[]");

      pCtx->sMb.pChromaPredMode[i] = NULL;
    }

    if (pCtx->sMb.pCbp[i]) {
      pMa->WelsFree (pCtx->sMb.pCbp[i], "pCtx->sMb.pCbp[]");

      pCtx->sMb.pCbp[i] = NULL;
    }

    //      if (pCtx->sMb.pMotionPredFlag[i])
    //{
    //  pMa->WelsFree( pCtx->sMb.pMotionPredFlag[i], "pCtx->sMb.pMotionPredFlag[]" );

    //  pCtx->sMb.pMotionPredFlag[i] = NULL;
    //}

    if (pCtx->sMb.pSubMbType[i]) {
      pMa->WelsFree (pCtx->sMb.pSubMbType[i], "pCtx->sMb.pSubMbType[]");

      pCtx->sMb.pSubMbType[i] = NULL;
    }

    if (pCtx->sMb.pSliceIdc[i]) {
      pMa->WelsFree (pCtx->sMb.pSliceIdc[i], "pCtx->sMb.pSliceIdc[]");

      pCtx->sMb.pSliceIdc[i] = NULL;
    }

    if (pCtx->sMb.pResidualPredFlag[i]) {
      pMa->WelsFree (pCtx->sMb.pResidualPredFlag[i], "pCtx->sMb.pResidualPredFlag[]");

      pCtx->sMb.pResidualPredFlag[i] = NULL;
    }

    if (pCtx->sMb.pInterPredictionDoneFlag[i]) {
      pMa->WelsFree (pCtx->sMb.pInterPredictionDoneFlag[i], "pCtx->sMb.pInterPredictionDoneFlag[]");

      pCtx->sMb.pInterPredictionDoneFlag[i] = NULL;
    }

    if (pCtx->sMb.pMbCorrectlyDecodedFlag[i]) {
      pMa->WelsFree (pCtx->sMb.pMbCorrectlyDecodedFlag[i], "pCtx->sMb.pMbCorrectlyDecodedFlag[]");
      pCtx->sMb.pMbCorrectlyDecodedFlag[i] = NULL;
    }

    if (pCtx->sMb.pMbRefConcealedFlag[i]) {
      pMa->WelsFree (pCtx->sMb.pMbRefConcealedFlag[i], "pCtx->sMb.pMbRefConcealedFlag[]");
      pCtx->sMb.pMbRefConcealedFlag[i] = NULL;
    }
    pMa->WelsFree (pDq, "pDq");

    pDq = NULL;
    pCtx->pDqLayersList[i] = NULL;

    ++ i;
  } while (i < LAYER_NUM_EXCHANGEABLE);

  pCtx->iPicWidthReq            = 0;
  pCtx->iPicHeightReq           = 0;
  pCtx->bInitialDqLayersMem     = false;
}

void ResetCurrentAccessUnit (PWelsDecoderContext pCtx) {
  PAccessUnit pCurAu = pCtx->pAccessUnitList;
  pCurAu->uiStartPos            = 0;
  pCurAu->uiEndPos              = 0;
  pCurAu->bCompletedAuFlag      = false;
  if (pCurAu->uiActualUnitsNum > 0) {
    uint32_t iIdx = 0;
    const uint32_t kuiActualNum = pCurAu->uiActualUnitsNum;
    // a more simpler method to do nal units list management prefered here
    const uint32_t kuiAvailNum  = pCurAu->uiAvailUnitsNum;
    const uint32_t kuiLeftNum   = kuiAvailNum - kuiActualNum;

    // Swapping active nal unit nodes of succeeding AU with leading of list
    while (iIdx < kuiLeftNum) {
      PNalUnit t = pCurAu->pNalUnitsList[kuiActualNum + iIdx];
      pCurAu->pNalUnitsList[kuiActualNum + iIdx] = pCurAu->pNalUnitsList[iIdx];
      pCurAu->pNalUnitsList[iIdx] = t;
      ++ iIdx;
    }
    pCurAu->uiActualUnitsNum = pCurAu->uiAvailUnitsNum = kuiLeftNum;
  }
}

/*!
 * \brief   Force reset current Acess Unit Nal list in case error parsing/decoding in current AU
 * \author
 * \history 11/16/2009
 */
void ForceResetCurrentAccessUnit (PAccessUnit pAu) {
  uint32_t uiSucAuIdx = pAu->uiEndPos + 1;
  uint32_t uiCurAuIdx = 0;

  // swap the succeeding AU's nal units to the front
  while (uiSucAuIdx < pAu->uiAvailUnitsNum) {
    PNalUnit t = pAu->pNalUnitsList[uiSucAuIdx];
    pAu->pNalUnitsList[uiSucAuIdx] = pAu->pNalUnitsList[uiCurAuIdx];
    pAu->pNalUnitsList[uiCurAuIdx] = t;
    ++ uiSucAuIdx;
    ++ uiCurAuIdx;
  }

  // Update avail/actual units num accordingly for next AU parsing
  if (pAu->uiAvailUnitsNum > pAu->uiEndPos)
    pAu->uiAvailUnitsNum -= (pAu->uiEndPos + 1);
  else
    pAu->uiAvailUnitsNum = 0;
  pAu->uiActualUnitsNum = 0;
  pAu->uiStartPos       = 0;
  pAu->uiEndPos         = 0;
  pAu->bCompletedAuFlag = false;
}

//clear current corrupted NAL from pNalUnitsList
void ForceClearCurrentNal (PAccessUnit pAu) {
  if (pAu->uiAvailUnitsNum > 0)
    -- pAu->uiAvailUnitsNum;
}

void ForceResetParaSetStatusAndAUList (PWelsDecoderContext pCtx) {
  pCtx->sSpsPpsCtx.bSpsExistAheadFlag = false;
  pCtx->sSpsPpsCtx.bSubspsExistAheadFlag = false;
  pCtx->sSpsPpsCtx.bPpsExistAheadFlag = false;

  // Force clear the AU list
  pCtx->pAccessUnitList->uiAvailUnitsNum        = 0;
  pCtx->pAccessUnitList->uiActualUnitsNum       = 0;
  pCtx->pAccessUnitList->uiStartPos             = 0;
  pCtx->pAccessUnitList->uiEndPos               = 0;
  pCtx->pAccessUnitList->bCompletedAuFlag       = false;
}

void CheckAvailNalUnitsListContinuity (PWelsDecoderContext pCtx, int32_t iStartIdx, int32_t iEndIdx) {
  PAccessUnit pCurAu = pCtx->pAccessUnitList;

  uint8_t uiLastNuDependencyId, uiLastNuLayerDqId;
  uint8_t uiCurNuDependencyId, uiCurNuQualityId, uiCurNuLayerDqId, uiCurNuRefLayerDqId;

  int32_t iCurNalUnitIdx = 0;

  //check the continuity of pNalUnitsList forwards (from pIdxNoInterLayerPred to end_postion)
  uiLastNuDependencyId = pCurAu->pNalUnitsList[iStartIdx]->sNalHeaderExt.uiDependencyId;//starting nal unit
  uiLastNuLayerDqId   = pCurAu->pNalUnitsList[iStartIdx]->sNalHeaderExt.uiLayerDqId;//starting nal unit
  iCurNalUnitIdx = iStartIdx + 1;//current nal unit
  while (iCurNalUnitIdx <= iEndIdx) {
    uiCurNuDependencyId   = pCurAu->pNalUnitsList[iCurNalUnitIdx]->sNalHeaderExt.uiDependencyId;
    uiCurNuQualityId      = pCurAu->pNalUnitsList[iCurNalUnitIdx]->sNalHeaderExt.uiQualityId;
    uiCurNuLayerDqId     = pCurAu->pNalUnitsList[iCurNalUnitIdx]->sNalHeaderExt.uiLayerDqId;
    uiCurNuRefLayerDqId = pCurAu->pNalUnitsList[iCurNalUnitIdx]->sNalData.sVclNal.sSliceHeaderExt.uiRefLayerDqId;

    if (uiCurNuDependencyId == uiLastNuDependencyId) {
      uiLastNuLayerDqId = uiCurNuLayerDqId;
      ++ iCurNalUnitIdx;
    } else { //uiCurNuDependencyId != uiLastNuDependencyId, new dependency arrive
      if (uiCurNuQualityId == 0) {
        uiLastNuDependencyId = uiCurNuDependencyId;
        if (uiCurNuRefLayerDqId == uiLastNuLayerDqId) {
          uiLastNuLayerDqId = uiCurNuLayerDqId;
          ++ iCurNalUnitIdx;
        } else { //cur_nu_layer_id != next_nu_ref_layer_dq_id, the chain is broken at this point
          break;
        }
      } else { //new dependency arrive, but no base quality layer, so we must stop in this point
        break;
      }
    }
  }

  -- iCurNalUnitIdx;
  pCurAu->uiEndPos = iCurNalUnitIdx;
  pCtx->uiTargetDqId = pCurAu->pNalUnitsList[iCurNalUnitIdx]->sNalHeaderExt.uiLayerDqId;
}

//main purpose: to support multi-slice and to include all slice which have the same uiDependencyId, uiQualityId and frame_num
//for single slice, pIdxNoInterLayerPred SHOULD NOT be modified
void RefineIdxNoInterLayerPred (PAccessUnit pCurAu, int32_t* pIdxNoInterLayerPred) {
  int32_t iLastNalDependId  = pCurAu->pNalUnitsList[*pIdxNoInterLayerPred]->sNalHeaderExt.uiDependencyId;
  int32_t iLastNalQualityId = pCurAu->pNalUnitsList[*pIdxNoInterLayerPred]->sNalHeaderExt.uiQualityId;
  uint8_t uiLastNalTId       = pCurAu->pNalUnitsList[*pIdxNoInterLayerPred]->sNalHeaderExt.uiTemporalId;
  int32_t iLastNalFrameNum  =
    pCurAu->pNalUnitsList[*pIdxNoInterLayerPred]->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader.iFrameNum;
  int32_t iLastNalPoc        =
    pCurAu->pNalUnitsList[*pIdxNoInterLayerPred]->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader.iPicOrderCntLsb;
  int32_t iLastNalFirstMb   =
    pCurAu->pNalUnitsList[*pIdxNoInterLayerPred]->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader.iFirstMbInSlice;
  int32_t iCurNalDependId, iCurNalQualityId, iCurNalTId, iCurNalFrameNum, iCurNalPoc, iCurNalFirstMb, iCurIdx,
          iFinalIdxNoInterLayerPred;

  bool  bMultiSliceFind = false;

  iFinalIdxNoInterLayerPred = 0;
  iCurIdx = *pIdxNoInterLayerPred - 1;
  while (iCurIdx >= 0) {
    if (pCurAu->pNalUnitsList[iCurIdx]->sNalHeaderExt.iNoInterLayerPredFlag) {
      iCurNalDependId  = pCurAu->pNalUnitsList[iCurIdx]->sNalHeaderExt.uiDependencyId;
      iCurNalQualityId = pCurAu->pNalUnitsList[iCurIdx]->sNalHeaderExt.uiQualityId;
      iCurNalTId       = pCurAu->pNalUnitsList[iCurIdx]->sNalHeaderExt.uiTemporalId;
      iCurNalFrameNum  = pCurAu->pNalUnitsList[iCurIdx]->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader.iFrameNum;
      iCurNalPoc        = pCurAu->pNalUnitsList[iCurIdx]->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader.iPicOrderCntLsb;
      iCurNalFirstMb   = pCurAu->pNalUnitsList[iCurIdx]->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader.iFirstMbInSlice;

      if (iCurNalDependId == iLastNalDependId  &&
          iCurNalQualityId == iLastNalQualityId &&
          iCurNalTId       == uiLastNalTId       &&
          iCurNalFrameNum  == iLastNalFrameNum  &&
          iCurNalPoc        == iLastNalPoc        &&
          iCurNalFirstMb   != iLastNalFirstMb) {
        bMultiSliceFind = true;
        iFinalIdxNoInterLayerPred = iCurIdx;
        --iCurIdx;
        continue;
      } else {
        break;
      }
    }
    --iCurIdx;
  }

  if (bMultiSliceFind && *pIdxNoInterLayerPred != iFinalIdxNoInterLayerPred) {
    *pIdxNoInterLayerPred = iFinalIdxNoInterLayerPred;
  }
}

bool CheckPocOfCurValidNalUnits (PAccessUnit pCurAu, int32_t pIdxNoInterLayerPred) {
  int32_t iEndIdx    = pCurAu->uiEndPos;
  int32_t iCurAuPoc =
    pCurAu->pNalUnitsList[pIdxNoInterLayerPred]->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader.iPicOrderCntLsb;
  int32_t iTmpPoc, i;
  for (i = pIdxNoInterLayerPred + 1; i < iEndIdx; i++) {
    iTmpPoc = pCurAu->pNalUnitsList[i]->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader.iPicOrderCntLsb;
    if (iTmpPoc != iCurAuPoc) {
      return false;
    }
  }

  return true;
}

bool CheckIntegrityNalUnitsList (PWelsDecoderContext pCtx) {
  PAccessUnit pCurAu = pCtx->pAccessUnitList;
  const int32_t kiEndPos = pCurAu->uiEndPos;
  int32_t iIdxNoInterLayerPred = 0;

  if (!pCurAu->bCompletedAuFlag)
    return false;

  if (pCtx->bNewSeqBegin) {
    pCurAu->uiStartPos = 0;
    //step1: search the pNalUnit whose iNoInterLayerPredFlag equal to 1 backwards (from uiEndPos to 0)
    iIdxNoInterLayerPred = kiEndPos;
    while (iIdxNoInterLayerPred >= 0) {
      if (pCurAu->pNalUnitsList[iIdxNoInterLayerPred]->sNalHeaderExt.iNoInterLayerPredFlag) {
        break;
      }
      --iIdxNoInterLayerPred;
    }
    if (iIdxNoInterLayerPred < 0) {
      //can not find the Nal Unit whose no_inter_pred_falg equal to 1, MUST STOP decode
      return false;
    }

    //step2: support multi-slice, to include all base layer slice
    RefineIdxNoInterLayerPred (pCurAu, &iIdxNoInterLayerPred);
    pCurAu->uiStartPos = iIdxNoInterLayerPred;
    CheckAvailNalUnitsListContinuity (pCtx, iIdxNoInterLayerPred, kiEndPos);

    if (!CheckPocOfCurValidNalUnits (pCurAu, iIdxNoInterLayerPred)) {
      return false;
    }

    pCtx->iCurSeqIntervalTargetDependId = pCurAu->pNalUnitsList[pCurAu->uiEndPos]->sNalHeaderExt.uiDependencyId;
    pCtx->iCurSeqIntervalMaxPicWidth  =
      pCurAu->pNalUnitsList[pCurAu->uiEndPos]->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader.iMbWidth << 4;
    pCtx->iCurSeqIntervalMaxPicHeight =
      pCurAu->pNalUnitsList[pCurAu->uiEndPos]->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader.iMbHeight << 4;
  } else { //P_SLICE
    //step 1: search uiDependencyId equal to pCtx->cur_seq_interval_target_dependency_id
    bool bGetDependId = false;
    int32_t iIdxDependId = 0;

    iIdxDependId = kiEndPos;
    while (iIdxDependId >= 0) {
      if (pCtx->iCurSeqIntervalTargetDependId == pCurAu->pNalUnitsList[iIdxDependId]->sNalHeaderExt.uiDependencyId) {
        bGetDependId = true;
        break;
      } else {
        --iIdxDependId;
      }
    }

    //step 2: switch according to whether or not find the index of pNalUnit whose uiDependencyId equal to iCurSeqIntervalTargetDependId
    if (bGetDependId) { //get the index of pNalUnit whose uiDependencyId equal to iCurSeqIntervalTargetDependId
      bool bGetNoInterPredFront = false;
      //step 2a: search iNoInterLayerPredFlag [0....iIdxDependId]
      iIdxNoInterLayerPred = iIdxDependId;
      while (iIdxNoInterLayerPred >= 0) {
        if (pCurAu->pNalUnitsList[iIdxNoInterLayerPred]->sNalHeaderExt.iNoInterLayerPredFlag) {
          bGetNoInterPredFront = true;
          break;
        }
        --iIdxNoInterLayerPred;
      }
      //step 2b: switch, whether or not find the NAL unit whose no_inter_pred_flag equal to 1 among [0....iIdxDependId]
      if (bGetNoInterPredFront) { //YES
        RefineIdxNoInterLayerPred (pCurAu, &iIdxNoInterLayerPred);
        pCurAu->uiStartPos = iIdxNoInterLayerPred;
        CheckAvailNalUnitsListContinuity (pCtx, iIdxNoInterLayerPred, iIdxDependId);

        if (!CheckPocOfCurValidNalUnits (pCurAu, iIdxNoInterLayerPred)) {
          return false;
        }
      } else { //NO, should find the NAL unit whose no_inter_pred_flag equal to 1 among [iIdxDependId....uiEndPos]
        iIdxNoInterLayerPred = iIdxDependId;
        while (iIdxNoInterLayerPred <= kiEndPos) {
          if (pCurAu->pNalUnitsList[iIdxNoInterLayerPred]->sNalHeaderExt.iNoInterLayerPredFlag) {
            break;
          }
          ++iIdxNoInterLayerPred;
        }

        if (iIdxNoInterLayerPred > kiEndPos) {
          return false; //cann't find the index of pNalUnit whose no_inter_pred_flag = 1
        }

        RefineIdxNoInterLayerPred (pCurAu, &iIdxNoInterLayerPred);
        pCurAu->uiStartPos = iIdxNoInterLayerPred;
        CheckAvailNalUnitsListContinuity (pCtx, iIdxNoInterLayerPred, kiEndPos);

        if (!CheckPocOfCurValidNalUnits (pCurAu, iIdxNoInterLayerPred)) {
          return false;
        }
      }
    } else { //without the index of pNalUnit, should process this AU as common case
      iIdxNoInterLayerPred = kiEndPos;
      while (iIdxNoInterLayerPred >= 0) {
        if (pCurAu->pNalUnitsList[iIdxNoInterLayerPred]->sNalHeaderExt.iNoInterLayerPredFlag) {
          break;
        }
        --iIdxNoInterLayerPred;
      }
      if (iIdxNoInterLayerPred < 0) {
        return false; //cann't find the index of pNalUnit whose iNoInterLayerPredFlag = 1
      }

      RefineIdxNoInterLayerPred (pCurAu, &iIdxNoInterLayerPred);
      pCurAu->uiStartPos = iIdxNoInterLayerPred;
      CheckAvailNalUnitsListContinuity (pCtx, iIdxNoInterLayerPred, kiEndPos);

      if (!CheckPocOfCurValidNalUnits (pCurAu, iIdxNoInterLayerPred)) {
        return false;
      }
    }
  }

  return true;
}

void CheckOnlyOneLayerInAu (PWelsDecoderContext pCtx) {
  PAccessUnit pCurAu = pCtx->pAccessUnitList;

  int32_t iEndIdx = pCurAu->uiEndPos;
  int32_t iCurIdx = pCurAu->uiStartPos;
  uint8_t uiDId = pCurAu->pNalUnitsList[iCurIdx]->sNalHeaderExt.uiDependencyId;
  uint8_t uiQId = pCurAu->pNalUnitsList[iCurIdx]->sNalHeaderExt.uiQualityId;
  uint8_t uiTId = pCurAu->pNalUnitsList[iCurIdx]->sNalHeaderExt.uiTemporalId;

  uint8_t uiCurDId, uiCurQId, uiCurTId;

  pCtx->bOnlyOneLayerInCurAuFlag = true;

  if (iEndIdx == iCurIdx) { //only one NAL in pNalUnitsList
    return;
  }

  ++iCurIdx;
  while (iCurIdx <= iEndIdx) {
    uiCurDId = pCurAu->pNalUnitsList[iCurIdx]->sNalHeaderExt.uiDependencyId;
    uiCurQId = pCurAu->pNalUnitsList[iCurIdx]->sNalHeaderExt.uiQualityId;
    uiCurTId = pCurAu->pNalUnitsList[iCurIdx]->sNalHeaderExt.uiTemporalId;

    if (uiDId != uiCurDId || uiQId != uiCurQId || uiTId != uiCurTId) {
      pCtx->bOnlyOneLayerInCurAuFlag = false;
      return;
    }

    ++iCurIdx;
  }
}

int32_t WelsDecodeAccessUnitStart (PWelsDecoderContext pCtx) {
  // Roll back NAL units not being belong to current access unit list for proceeded access unit
  int32_t iRet = UpdateAccessUnit (pCtx);
  if (iRet != ERR_NONE)
    return iRet;

  pCtx->pAccessUnitList->uiStartPos = 0;
  if (!pCtx->sSpsPpsCtx.bAvcBasedFlag && !CheckIntegrityNalUnitsList (pCtx)) {
    pCtx->iErrorCode |= dsBitstreamError;
    return dsBitstreamError;
  }

  //check current AU has only one layer or not
  //If YES, can use deblocking based on AVC
  if (!pCtx->sSpsPpsCtx.bAvcBasedFlag) {
    CheckOnlyOneLayerInAu (pCtx);
  }

  return ERR_NONE;
}

void WelsDecodeAccessUnitEnd (PWelsDecoderContext pCtx) {
  //save previous header info
  PAccessUnit pCurAu = pCtx->pAccessUnitList;
  PNalUnit pCurNal = pCurAu->pNalUnitsList[pCurAu->uiEndPos];
  memcpy (&pCtx->pLastDecPicInfo->sLastNalHdrExt, &pCurNal->sNalHeaderExt, sizeof (SNalUnitHeaderExt));
  memcpy (&pCtx->pLastDecPicInfo->sLastSliceHeader,
          &pCurNal->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader, sizeof (SSliceHeader));
  // uninitialize context of current access unit and rbsp buffer clean
  ResetCurrentAccessUnit (pCtx);
}

/* CheckNewSeqBeginAndUpdateActiveLayerSps
 * return:
 * true - the AU to be construct is the start of new sequence; false - not
 */
static bool CheckNewSeqBeginAndUpdateActiveLayerSps (PWelsDecoderContext pCtx) {
  bool bNewSeq = false;
  PAccessUnit pCurAu = pCtx->pAccessUnitList;
  PSps pTmpLayerSps[MAX_LAYER_NUM];
  for (int i = 0; i < MAX_LAYER_NUM; i++) {
    pTmpLayerSps[i] = NULL;
  }
  // track the layer sps for the current au
  for (unsigned int i = pCurAu->uiStartPos; i <= pCurAu->uiEndPos; i++) {
    uint32_t uiDid = pCurAu->pNalUnitsList[i]->sNalHeaderExt.uiDependencyId;
    pTmpLayerSps[uiDid] = pCurAu->pNalUnitsList[i]->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader.pSps;
    if ((pCurAu->pNalUnitsList[i]->sNalHeaderExt.sNalUnitHeader.eNalUnitType == NAL_UNIT_CODED_SLICE_IDR)
        || (pCurAu->pNalUnitsList[i]->sNalHeaderExt.bIdrFlag))
      bNewSeq = true;
  }
  int iMaxActiveLayer = 0, iMaxCurrentLayer = 0;
  for (int i = MAX_LAYER_NUM - 1; i >= 0; i--) {
    if (pCtx->sSpsPpsCtx.pActiveLayerSps[i] != NULL) {
      iMaxActiveLayer = i;
      break;
    }
  }
  for (int i = MAX_LAYER_NUM - 1; i >= 0; i--) {
    if (pTmpLayerSps[i] != NULL) {
      iMaxCurrentLayer = i;
      break;
    }
  }
  if ((iMaxCurrentLayer != iMaxActiveLayer)
      || (pTmpLayerSps[iMaxCurrentLayer]  != pCtx->sSpsPpsCtx.pActiveLayerSps[iMaxActiveLayer])) {
    bNewSeq = true;
  }
  // fill active sps if the current sps is not null while active layer is null
  if (!bNewSeq) {
    for (int i = 0; i < MAX_LAYER_NUM; i++) {
      if (pCtx->sSpsPpsCtx.pActiveLayerSps[i] == NULL && pTmpLayerSps[i] != NULL) {
        pCtx->sSpsPpsCtx.pActiveLayerSps[i] = pTmpLayerSps[i];
      }
    }
  } else {
    // UpdateActiveLayerSps if new sequence start
    memcpy (&pCtx->sSpsPpsCtx.pActiveLayerSps[0], &pTmpLayerSps[0], MAX_LAYER_NUM * sizeof (PSps));
  }
  return bNewSeq;
}

static void WriteBackActiveParameters (PWelsDecoderContext pCtx) {
  if (pCtx->sSpsPpsCtx.iOverwriteFlags & OVERWRITE_PPS) {
    memcpy (&pCtx->sSpsPpsCtx.sPpsBuffer[pCtx->sSpsPpsCtx.sPpsBuffer[MAX_PPS_COUNT].iPpsId],
            &pCtx->sSpsPpsCtx.sPpsBuffer[MAX_PPS_COUNT], sizeof (SPps));
  }
  if (pCtx->sSpsPpsCtx.iOverwriteFlags & OVERWRITE_SPS) {
    memcpy (&pCtx->sSpsPpsCtx.sSpsBuffer[pCtx->sSpsPpsCtx.sSpsBuffer[MAX_SPS_COUNT].iSpsId],
            &pCtx->sSpsPpsCtx.sSpsBuffer[MAX_SPS_COUNT], sizeof (SSps));
    pCtx->bNewSeqBegin = true;
  }
  if (pCtx->sSpsPpsCtx.iOverwriteFlags & OVERWRITE_SUBSETSPS) {
    memcpy (&pCtx->sSpsPpsCtx.sSubsetSpsBuffer[pCtx->sSpsPpsCtx.sSubsetSpsBuffer[MAX_SPS_COUNT].sSps.iSpsId],
            &pCtx->sSpsPpsCtx.sSubsetSpsBuffer[MAX_SPS_COUNT], sizeof (SSubsetSps));
    pCtx->bNewSeqBegin = true;
  }
  pCtx->sSpsPpsCtx.iOverwriteFlags = OVERWRITE_NONE;
}

/*
 * DecodeFinishUpdate
 * decoder finish decoding, update active parameter sets and new seq status
 *
 */

void DecodeFinishUpdate (PWelsDecoderContext pCtx) {
  pCtx->bNewSeqBegin = false;
  WriteBackActiveParameters (pCtx);
  pCtx->bNewSeqBegin = pCtx->bNewSeqBegin || pCtx->bNextNewSeqBegin;
  pCtx->bNextNewSeqBegin = false; // reset it
  if (pCtx->bNewSeqBegin)
    ResetActiveSPSForEachLayer (pCtx);
}

/*
* WelsDecodeInitAccessUnitStart
* check and (re)allocate picture buffers on new sequence begin
*  bit_len:    size in bit length of data
*  buf_len:    size in byte length of data
*  coded_au:   mark an Access Unit decoding finished
* return:
*  0 - success; otherwise returned error_no defined in error_no.h
*/
int32_t WelsDecodeInitAccessUnitStart (PWelsDecoderContext pCtx, SBufferInfo* pDstInfo) {
  int32_t iErr = ERR_NONE;
  PAccessUnit pCurAu = pCtx->pAccessUnitList;
  pCtx->bAuReadyFlag = false;
  pCtx->pLastDecPicInfo->bLastHasMmco5 = false;
  bool bTmpNewSeqBegin = CheckNewSeqBeginAndUpdateActiveLayerSps (pCtx);
  pCtx->bNewSeqBegin = pCtx->bNewSeqBegin || bTmpNewSeqBegin;
  iErr = WelsDecodeAccessUnitStart (pCtx);
  GetVclNalTemporalId (pCtx);

  if (ERR_NONE != iErr) {
    ForceResetCurrentAccessUnit (pCtx->pAccessUnitList);
    if (!pCtx->pParam->bParseOnly)
      pDstInfo->iBufferStatus = 0;
    pCtx->bNewSeqBegin = pCtx->bNewSeqBegin || pCtx->bNextNewSeqBegin;
    pCtx->bNextNewSeqBegin = false; // reset it
    if (pCtx->bNewSeqBegin)
      ResetActiveSPSForEachLayer (pCtx);
    return iErr;
  }

  pCtx->pSps = pCurAu->pNalUnitsList[pCurAu->uiStartPos]->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader.pSps;
  pCtx->pPps = pCurAu->pNalUnitsList[pCurAu->uiStartPos]->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader.pPps;

  return iErr;
}

/*
* AllocPicBuffOnNewSeqBegin
* check and (re)allocate picture buffers on new sequence begin
* return:
*  0 - success; otherwise returned error_no defined in error_no.h
*/
int32_t AllocPicBuffOnNewSeqBegin (PWelsDecoderContext pCtx) {
  //try to allocate or relocate DPB memory only when new sequence is coming.
  if (GetThreadCount (pCtx) <= 1) {
    WelsResetRefPic (pCtx); //clear ref pPic when IDR NAL
  }
  int32_t iErr = SyncPictureResolutionExt (pCtx, pCtx->pSps->iMbWidth, pCtx->pSps->iMbHeight);

  if (ERR_NONE != iErr) {
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, "sync picture resolution ext failed,  the error is %d", iErr);
    return iErr;
  }

  return iErr;
}

/*
* InitConstructAccessUnit
* Init before constructing an access unit for given input bitstream, maybe partial NAL Unit, one or more Units are involved to
* joint a collective access unit.
* parameter\
*  SBufferInfo:    Buffer info
* return:
*  0 - success; otherwise returned error_no defined in error_no.h
*/
int32_t InitConstructAccessUnit (PWelsDecoderContext pCtx, SBufferInfo* pDstInfo) {
  int32_t iErr = ERR_NONE;

  iErr = WelsDecodeInitAccessUnitStart (pCtx, pDstInfo);
  if (ERR_NONE != iErr) {
    return iErr;
  }
  if (pCtx->bNewSeqBegin) {
    iErr = AllocPicBuffOnNewSeqBegin (pCtx);
    if (ERR_NONE != iErr) {
      return iErr;
    }
  }

  return iErr;
}

/*
 * ConstructAccessUnit
 * construct an access unit for given input bitstream, maybe partial NAL Unit, one or more Units are involved to
 * joint a collective access unit.
 * parameter\
 *  buf:        bitstream data buffer
 *  bit_len:    size in bit length of data
 *  buf_len:    size in byte length of data
 *  coded_au:   mark an Access Unit decoding finished
 * return:
 *  0 - success; otherwise returned error_no defined in error_no.h
 */
int32_t ConstructAccessUnit (PWelsDecoderContext pCtx, uint8_t** ppDst, SBufferInfo* pDstInfo) {
  int32_t iErr = ERR_NONE;
  if (GetThreadCount (pCtx) <= 1) {
    iErr = InitConstructAccessUnit (pCtx, pDstInfo);
    if (ERR_NONE != iErr) {
      return iErr;
    }
  }
  if (pCtx->pCabacDecEngine == NULL) {
    pCtx->pCabacDecEngine = (SWelsCabacDecEngine*)pCtx->pMemAlign->WelsMallocz (sizeof (SWelsCabacDecEngine),
                            "pCtx->pCabacDecEngine");
    WELS_VERIFY_RETURN_IF (ERR_INFO_OUT_OF_MEMORY, (NULL == pCtx->pCabacDecEngine))
  }

  iErr = DecodeCurrentAccessUnit (pCtx, ppDst, pDstInfo);

  WelsDecodeAccessUnitEnd (pCtx);

  if (ERR_NONE != iErr) {
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_DEBUG, "returned error from decoding:[0x%x]", iErr);
    return iErr;
  }

  return ERR_NONE;
}

static inline void InitDqLayerInfo (PDqLayer pDqLayer, PLayerInfo pLayerInfo, PNalUnit pNalUnit, PPicture pPicDec) {
  PNalUnitHeaderExt pNalHdrExt    = &pNalUnit->sNalHeaderExt;
  PSliceHeaderExt pShExt          = &pNalUnit->sNalData.sVclNal.sSliceHeaderExt;
  PSliceHeader pSh                = &pShExt->sSliceHeader;
  const uint8_t kuiQualityId      = pNalHdrExt->uiQualityId;

  memcpy (&pDqLayer->sLayerInfo, pLayerInfo, sizeof (SLayerInfo)); //confirmed_safe_unsafe_usage

  pDqLayer->pDec        = pPicDec;
  pDqLayer->iMbWidth    = pSh->iMbWidth;        // MB width of this picture
  pDqLayer->iMbHeight   = pSh->iMbHeight;// MB height of this picture

  pDqLayer->iSliceIdcBackup = (pSh->iFirstMbInSlice << 7) | (pNalHdrExt->uiDependencyId << 4) | (pNalHdrExt->uiQualityId);

  /* Common syntax elements across all slices of a DQLayer */
  pDqLayer->uiPpsId                                     = pLayerInfo->pPps->iPpsId;
  pDqLayer->uiDisableInterLayerDeblockingFilterIdc      = pShExt->uiDisableInterLayerDeblockingFilterIdc;
  pDqLayer->iInterLayerSliceAlphaC0Offset               = pShExt->iInterLayerSliceAlphaC0Offset;
  pDqLayer->iInterLayerSliceBetaOffset                  = pShExt->iInterLayerSliceBetaOffset;
  pDqLayer->iSliceGroupChangeCycle                      = pSh->iSliceGroupChangeCycle;
  pDqLayer->bStoreRefBasePicFlag                        = pShExt->bStoreRefBasePicFlag;
  pDqLayer->bTCoeffLevelPredFlag                        = pShExt->bTCoeffLevelPredFlag;
  pDqLayer->bConstrainedIntraResamplingFlag             = pShExt->bConstrainedIntraResamplingFlag;
  pDqLayer->uiRefLayerDqId                              = pShExt->uiRefLayerDqId;
  pDqLayer->uiRefLayerChromaPhaseXPlus1Flag             = pShExt->uiRefLayerChromaPhaseXPlus1Flag;
  pDqLayer->uiRefLayerChromaPhaseYPlus1                 = pShExt->uiRefLayerChromaPhaseYPlus1;
  pDqLayer->bUseWeightPredictionFlag                    = false;
  pDqLayer->bUseWeightedBiPredIdc = false;
  //memcpy(&pDqLayer->sScaledRefLayer, &pShExt->sScaledRefLayer, sizeof(SPosOffset));//confirmed_safe_unsafe_usage

  if (kuiQualityId == BASE_QUALITY_ID) {
    pDqLayer->pRefPicListReordering = &pSh->pRefPicListReordering;
    pDqLayer->pRefPicMarking = &pSh->sRefMarking;

    pDqLayer->bUseWeightPredictionFlag = pSh->pPps->bWeightedPredFlag;
    pDqLayer->bUseWeightedBiPredIdc = pSh->pPps->uiWeightedBipredIdc != 0;
    if (pSh->pPps->bWeightedPredFlag || pSh->pPps->uiWeightedBipredIdc) {
      pDqLayer->pPredWeightTable = &pSh->sPredWeightTable;
    }
    pDqLayer->pRefPicBaseMarking        = &pShExt->sRefBasePicMarking;
  }

  pDqLayer->uiLayerDqId                 = pNalHdrExt->uiLayerDqId;      // dq_id of current layer
  pDqLayer->bUseRefBasePicFlag          = pNalHdrExt->bUseRefBasePicFlag;
}

void WelsDqLayerDecodeStart (PWelsDecoderContext pCtx, PNalUnit pCurNal, PSps pSps, PPps pPps) {
  PSliceHeader pSh = &pCurNal->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader;

  pCtx->eSliceType   = pSh->eSliceType;
  pCtx->pSliceHeader = pSh;
  pCtx->bUsedAsRef   = false;

  pCtx->iFrameNum    = pSh->iFrameNum;
  UpdateDecoderStatisticsForActiveParaset (pCtx->pDecoderStatistics, pSps, pPps);
}

int32_t InitRefPicList (PWelsDecoderContext pCtx, const uint8_t kuiNRi, int32_t iPoc) {
  int32_t iRet = ERR_NONE;
  if (pCtx->eSliceType == B_SLICE) {
    iRet = WelsInitBSliceRefList (pCtx, iPoc);
    CreateImplicitWeightTable (pCtx);
  } else
    iRet = WelsInitRefList (pCtx, iPoc);
  if ((pCtx->eSliceType != I_SLICE && pCtx->eSliceType != SI_SLICE)) {
#if 0
    if (pCtx->pSps->uiProfileIdc != 66 && pCtx->pPps->bEntropyCodingModeFlag)
      iRet = WelsReorderRefList2 (pCtx);
    else
#endif
      iRet = WelsReorderRefList (pCtx);
  }

  return iRet;
}

void InitCurDqLayerData (PWelsDecoderContext pCtx, PDqLayer pCurDq) {
  if (NULL != pCtx && NULL != pCurDq) {
    pCurDq->pMbType         = pCtx->sMb.pMbType[0];
    pCurDq->pSliceIdc       = pCtx->sMb.pSliceIdc[0];
    pCurDq->pMv[LIST_0]         = pCtx->sMb.pMv[0][LIST_0];
    pCurDq->pMv[LIST_1]         = pCtx->sMb.pMv[0][LIST_1];
    pCurDq->pRefIndex[LIST_0]    = pCtx->sMb.pRefIndex[0][LIST_0];
    pCurDq->pRefIndex[LIST_1]   = pCtx->sMb.pRefIndex[0][LIST_1];
    pCurDq->pDirect             = pCtx->sMb.pDirect[0];
    pCurDq->pNoSubMbPartSizeLessThan8x8Flag = pCtx->sMb.pNoSubMbPartSizeLessThan8x8Flag[0];
    pCurDq->pTransformSize8x8Flag = pCtx->sMb.pTransformSize8x8Flag[0];
    pCurDq->pLumaQp         = pCtx->sMb.pLumaQp[0];
    pCurDq->pChromaQp       = pCtx->sMb.pChromaQp[0];
    pCurDq->pMvd[LIST_0]         = pCtx->sMb.pMvd[0][LIST_0];
    pCurDq->pMvd[LIST_1]          = pCtx->sMb.pMvd[0][LIST_1];
    pCurDq->pCbfDc          = pCtx->sMb.pCbfDc[0];
    pCurDq->pNzc            = pCtx->sMb.pNzc[0];
    pCurDq->pNzcRs          = pCtx->sMb.pNzcRs[0];
    pCurDq->pScaledTCoeff   = pCtx->sMb.pScaledTCoeff[0];
    pCurDq->pIntraPredMode  = pCtx->sMb.pIntraPredMode[0];
    pCurDq->pIntra4x4FinalMode = pCtx->sMb.pIntra4x4FinalMode[0];
    pCurDq->pIntraNxNAvailFlag = pCtx->sMb.pIntraNxNAvailFlag[0];
    pCurDq->pChromaPredMode = pCtx->sMb.pChromaPredMode[0];
    pCurDq->pCbp            = pCtx->sMb.pCbp[0];
    pCurDq->pSubMbType      = pCtx->sMb.pSubMbType[0];
    pCurDq->pInterPredictionDoneFlag = pCtx->sMb.pInterPredictionDoneFlag[0];
    pCurDq->pResidualPredFlag = pCtx->sMb.pResidualPredFlag[0];
    pCurDq->pMbCorrectlyDecodedFlag = pCtx->sMb.pMbCorrectlyDecodedFlag[0];
    pCurDq->pMbRefConcealedFlag = pCtx->sMb.pMbRefConcealedFlag[0];
  }
}

/*
 * DecodeCurrentAccessUnit
 * Decode current access unit when current AU is completed.
 */
int32_t DecodeCurrentAccessUnit (PWelsDecoderContext pCtx, uint8_t** ppDst, SBufferInfo* pDstInfo) {
  PNalUnit pNalCur = pCtx->pNalCur = NULL;
  PAccessUnit pCurAu = pCtx->pAccessUnitList;

  int32_t iIdx = pCurAu->uiStartPos;
  int32_t iEndIdx = pCurAu->uiEndPos;

  //get current thread ctx
  PWelsDecoderThreadCTX pThreadCtx = NULL;
  if (pCtx->pThreadCtx != NULL) {
    pThreadCtx = (PWelsDecoderThreadCTX)pCtx->pThreadCtx;
  }
  //get last thread ctx
  PWelsDecoderThreadCTX pLastThreadCtx = NULL;
  if (pCtx->pLastThreadCtx != NULL) {
    pLastThreadCtx = (PWelsDecoderThreadCTX) (pCtx->pLastThreadCtx);
    if (pLastThreadCtx->pDec == NULL) {
      pLastThreadCtx->pDec = PrefetchLastPicForThread (pCtx->pPicBuff,
                             pLastThreadCtx->iPicBuffIdx);
    }
  }
  int32_t iThreadCount = GetThreadCount (pCtx);
  int32_t iPpsId = 0;
  int32_t iRet = ERR_NONE;

  bool bAllRefComplete = true; // Assume default all ref picutres are complete

  const uint8_t kuiTargetLayerDqId = GetTargetDqId (pCtx->uiTargetDqId, pCtx->pParam);
  const uint8_t kuiDependencyIdMax = (kuiTargetLayerDqId & 0x7F) >> 4;
  int16_t iLastIdD = -1, iLastIdQ = -1;
  int16_t iCurrIdD = 0, iCurrIdQ = 0;
  pCtx->uiNalRefIdc = 0;
  bool bFreshSliceAvailable =
    true; // Another fresh slice comingup for given dq layer, for multiple slices in case of header parts of slices sometimes loss over error-prone channels, 8/14/2008

  //update pCurDqLayer at the starting of AU decoding
  if (pCtx->bInitialDqLayersMem || pCtx->pCurDqLayer == NULL) {
    pCtx->pCurDqLayer = pCtx->pDqLayersList[0];
  }

  InitCurDqLayerData (pCtx, pCtx->pCurDqLayer);

  pNalCur = pCurAu->pNalUnitsList[iIdx];
  while (iIdx <= iEndIdx) {
    PDqLayer dq_cur = pCtx->pCurDqLayer;
    SLayerInfo pLayerInfo;
    PSliceHeaderExt pShExt = NULL;
    PSliceHeader pSh = NULL;
    bool isNewFrame = true;
    if (iThreadCount > 1) {
      isNewFrame = pCtx->pDec == NULL;
    }
    if (pCtx->pDec == NULL) {
      //make call PrefetchPic first before updating reference lists in threaded mode
      //this prevents from possible thread-decoding hanging
      pCtx->pDec = PrefetchPic (pCtx->pPicBuff);
      if (pLastThreadCtx != NULL) {
        pLastThreadCtx->pDec->bUsedAsRef = pLastThreadCtx->pCtx->uiNalRefIdc > 0;
        if (pLastThreadCtx->pDec->bUsedAsRef) {
          for (int32_t listIdx = LIST_0; listIdx < LIST_A; ++listIdx) {
            uint32_t i = 0;
            while (i < MAX_REF_PIC_COUNT && pLastThreadCtx->pCtx->sRefPic.pRefList[listIdx][i]) {
              pLastThreadCtx->pDec->pRefPic[listIdx][i] = pLastThreadCtx->pCtx->sRefPic.pRefList[listIdx][i];
              ++i;
            }
          }
          pLastThreadCtx->pCtx->sTmpRefPic = pLastThreadCtx->pCtx->sRefPic;
          WelsMarkAsRef (pLastThreadCtx->pCtx, pLastThreadCtx->pDec);
          pCtx->sRefPic = pLastThreadCtx->pCtx->sTmpRefPic;
        } else {
          pCtx->sRefPic = pLastThreadCtx->pCtx->sRefPic;
        }
      }
      //WelsResetRefPic needs to be called when a new sequence is encountered
      //Otherwise artifacts is observed in decoded yuv in couple of unit tests with multiple-slice frame
      if (GetThreadCount (pCtx) > 1 && pCtx->bNewSeqBegin) {
        WelsResetRefPic (pCtx);
      }
      if (pCtx->iTotalNumMbRec != 0)
        pCtx->iTotalNumMbRec = 0;

      if (NULL == pCtx->pDec) {
        WelsLog (& (pCtx->sLogCtx), WELS_LOG_ERROR,
                 "DecodeCurrentAccessUnit()::::::PrefetchPic ERROR, pSps->iNumRefFrames:%d.",
                 pCtx->pSps->iNumRefFrames);
        // The error code here need to be separated from the dsOutOfMemory
        pCtx->iErrorCode |= dsOutOfMemory;
        return ERR_INFO_REF_COUNT_OVERFLOW;
      }
      if (pThreadCtx != NULL) {
        pThreadCtx->pDec = pCtx->pDec;
        if (iThreadCount > 1) ++pCtx->pDec->iRefCount;
        uint32_t uiMbHeight = (pCtx->pDec->iHeightInPixel + 15) >> 4;
        for (uint32_t i = 0; i < uiMbHeight; ++i) {
          RESET_EVENT (&pCtx->pDec->pReadyEvent[i]);
        }
      }
      pCtx->pDec->bNewSeqBegin = pCtx->bNewSeqBegin; //set flag for start decoding
    } else if (pCtx->iTotalNumMbRec == 0) { //pDec != NULL, already start
      pCtx->pDec->bNewSeqBegin = pCtx->bNewSeqBegin; //set flag for start decoding
    }
    pCtx->pDec->uiTimeStamp = pNalCur->uiTimeStamp;
    pCtx->pDec->uiDecodingTimeStamp = pCtx->uiDecodingTimeStamp;
    if (pThreadCtx != NULL) {
      pThreadCtx->iPicBuffIdx = pCtx->pDec->iPicBuffIdx;
      pCtx->pCurDqLayer->pMbCorrectlyDecodedFlag = pCtx->pDec->pMbCorrectlyDecodedFlag;
    }

    if (pCtx->iTotalNumMbRec == 0) { //Picture start to decode
      for (int32_t i = 0; i < LAYER_NUM_EXCHANGEABLE; ++ i)
        memset (pCtx->sMb.pSliceIdc[i], 0xff, (pCtx->sMb.iMbWidth * pCtx->sMb.iMbHeight * sizeof (int32_t)));
      memset (pCtx->pCurDqLayer->pMbCorrectlyDecodedFlag, 0, pCtx->pSps->iMbWidth * pCtx->pSps->iMbHeight * sizeof (bool));
      memset (pCtx->pCurDqLayer->pMbRefConcealedFlag, 0, pCtx->pSps->iMbWidth * pCtx->pSps->iMbHeight * sizeof (bool));
      memset (pCtx->pDec->pRefPic[LIST_0], 0, sizeof (PPicture) * MAX_DPB_COUNT);
      memset (pCtx->pDec->pRefPic[LIST_1], 0, sizeof (PPicture) * MAX_DPB_COUNT);
      pCtx->pDec->iMbNum = pCtx->pSps->iMbWidth * pCtx->pSps->iMbHeight;
      pCtx->pDec->iMbEcedNum = 0;
      pCtx->pDec->iMbEcedPropNum = 0;
    }
    pCtx->bRPLRError = false;
    GetI4LumaIChromaAddrTable (pCtx->iDecBlockOffsetArray, pCtx->pDec->iLinesize[0], pCtx->pDec->iLinesize[1]);

    if (pNalCur->sNalHeaderExt.uiLayerDqId > kuiTargetLayerDqId) { // confirmed pNalCur will never be NULL
      break; // Per formance it need not to decode the remaining bits any more due to given uiLayerDqId required, 9/2/2009
    }

    memset (&pLayerInfo, 0, sizeof (SLayerInfo));

    /*
     *  Loop decoding for slices (even FMO and/ multiple slices) within a dq layer
     */
    while (iIdx <= iEndIdx) {
      bool         bReconstructSlice;
      iCurrIdQ  = pNalCur->sNalHeaderExt.uiQualityId;
      iCurrIdD  = pNalCur->sNalHeaderExt.uiDependencyId;
      pSh       = &pNalCur->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader;
      pShExt    = &pNalCur->sNalData.sVclNal.sSliceHeaderExt;
      pCtx->bRPLRError = false;
      bReconstructSlice = CheckSliceNeedReconstruct (pNalCur->sNalHeaderExt.uiLayerDqId, kuiTargetLayerDqId);

      memcpy (&pLayerInfo.sNalHeaderExt, &pNalCur->sNalHeaderExt, sizeof (SNalUnitHeaderExt)); //confirmed_safe_unsafe_usage

      pCtx->pDec->iFrameNum = pSh->iFrameNum;
      pCtx->pDec->iFramePoc = pSh->iPicOrderCntLsb; // still can not obtain correct, because current do not support POCtype 2
      pCtx->pDec->bIdrFlag = pNalCur->sNalHeaderExt.bIdrFlag;
      pCtx->pDec->eSliceType = pSh->eSliceType;

      memcpy (&pLayerInfo.sSliceInLayer.sSliceHeaderExt, pShExt, sizeof (SSliceHeaderExt)); //confirmed_safe_unsafe_usage
      pLayerInfo.sSliceInLayer.bSliceHeaderExtFlag      = pNalCur->sNalData.sVclNal.bSliceHeaderExtFlag;
      pLayerInfo.sSliceInLayer.eSliceType               = pSh->eSliceType;
      pLayerInfo.sSliceInLayer.iLastMbQp                = pSh->iSliceQp;
      dq_cur->pBitStringAux = &pNalCur->sNalData.sVclNal.sSliceBitsRead;

      pCtx->uiNalRefIdc = pNalCur->sNalHeaderExt.sNalUnitHeader.uiNalRefIdc;

      iPpsId = pSh->iPpsId;

      pLayerInfo.pPps = pSh->pPps;
      pLayerInfo.pSps = pSh->pSps;
      pLayerInfo.pSubsetSps = pShExt->pSubsetSps;

      pCtx->pFmo = &pCtx->sFmoList[iPpsId];
      iRet = FmoParamUpdate (pCtx->pFmo, pLayerInfo.pSps, pLayerInfo.pPps, &pCtx->iActiveFmoNum, pCtx->pMemAlign);
      if (ERR_NONE != iRet) {
        if (iRet == ERR_INFO_OUT_OF_MEMORY) {
          pCtx->iErrorCode |= dsOutOfMemory;
          WelsLog (& (pCtx->sLogCtx), WELS_LOG_ERROR, "DecodeCurrentAccessUnit(), Fmo param alloc failed");
        } else {
          pCtx->iErrorCode |= dsBitstreamError;
          WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, "DecodeCurrentAccessUnit(), FmoParamUpdate failed, eSliceType: %d.",
                   pSh->eSliceType);
        }
        return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_FMO_INIT_FAIL);
      }

      bFreshSliceAvailable = (iCurrIdD != iLastIdD
                              || iCurrIdQ != iLastIdQ);        // do not need condition of (first_mb == 0) due multiple slices might be disorder


      WelsDqLayerDecodeStart (pCtx, pNalCur, pLayerInfo.pSps, pLayerInfo.pPps);


      if ((iLastIdD < 0) ||  //case 1: first layer
          (iLastIdD == iCurrIdD)) { //case 2: same uiDId
        InitDqLayerInfo (dq_cur, &pLayerInfo, pNalCur, pCtx->pDec);

        if (!dq_cur->sLayerInfo.pSps->bGapsInFrameNumValueAllowedFlag) {
          const bool kbIdrFlag = dq_cur->sLayerInfo.sNalHeaderExt.bIdrFlag
                                 || (dq_cur->sLayerInfo.sNalHeaderExt.sNalUnitHeader.eNalUnitType == NAL_UNIT_CODED_SLICE_IDR);
          // Subclause 8.2.5.2 Decoding process for gaps in frame_num
          int32_t iPrevFrameNum = pCtx->pLastDecPicInfo->iPrevFrameNum;
          if (pLastThreadCtx != NULL) {
            //call GetPrevFrameNum() to get correct iPrevFrameNum to prevent frame gap warning
            iPrevFrameNum = pCtx->bNewSeqBegin ? 0 : GetPrevFrameNum (pCtx);
          }
          if (!kbIdrFlag  &&
              pSh->iFrameNum != iPrevFrameNum &&
              pSh->iFrameNum != ((iPrevFrameNum + 1) & ((1 << dq_cur->sLayerInfo.pSps->uiLog2MaxFrameNum) -
                                 1))) {
            WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING,
                     "referencing pictures lost due frame gaps exist, prev_frame_num: %d, curr_frame_num: %d",
                     iPrevFrameNum,
                     pSh->iFrameNum);

            bAllRefComplete = false;
            pCtx->iErrorCode |= dsRefLost;
            if (pCtx->pParam->eEcActiveIdc == ERROR_CON_DISABLE) {
#ifdef LONG_TERM_REF
              pCtx->bParamSetsLostFlag = true;
#else
              pCtx->bReferenceLostAtT0Flag = true;
#endif
              return GENERATE_ERROR_NO (ERR_LEVEL_SLICE_HEADER, ERR_INFO_REFERENCE_PIC_LOST);
            }
          }
        }

        if (iCurrIdD == kuiDependencyIdMax && iCurrIdQ == BASE_QUALITY_ID && isNewFrame) {
          iRet = InitRefPicList (pCtx, pCtx->uiNalRefIdc, pSh->iPicOrderCntLsb);
          if (iThreadCount > 1) isNewFrame = false;
          if (iRet) {
            pCtx->bRPLRError = true;
            bAllRefComplete = false; // RPLR error, set ref pictures complete flag false
            HandleReferenceLost (pCtx, pNalCur);
            WelsLog (& (pCtx->sLogCtx), WELS_LOG_DEBUG,
                     "reference picture introduced by this frame is lost during transmission! uiTId: %d",
                     pNalCur->sNalHeaderExt.uiTemporalId);
            if (pCtx->pParam->eEcActiveIdc == ERROR_CON_DISABLE) {
              if (pCtx->iTotalNumMbRec == 0)
                pCtx->pDec = NULL;
              return iRet;
            }
          }
        }
        //calculate Colocated mv scaling factor for temporal direct prediction
        if (pSh->eSliceType == B_SLICE && !pSh->iDirectSpatialMvPredFlag)
          ComputeColocatedTemporalScaling (pCtx);

        if (iThreadCount > 1) {
          if (iIdx == 0) {
            memset (&pCtx->lastReadyHeightOffset[0][0], -1, LIST_A * MAX_REF_PIC_COUNT * sizeof (int16_t));
            SET_EVENT (&pThreadCtx->sSliceDecodeStart);
          }
          iRet = WelsDecodeAndConstructSlice (pCtx);
        } else {
          iRet = WelsDecodeSlice (pCtx, bFreshSliceAvailable, pNalCur);
        }

        //Output good store_base reconstruction when enhancement quality layer occurred error for MGS key picture case
        if (iRet != ERR_NONE) {
          WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING,
                   "DecodeCurrentAccessUnit() failed (%d) in frame: %d uiDId: %d uiQId: %d",
                   iRet, pSh->iFrameNum, iCurrIdD, iCurrIdQ);
          bAllRefComplete = false;
          HandleReferenceLostL0 (pCtx, pNalCur);
          if (pCtx->pParam->eEcActiveIdc == ERROR_CON_DISABLE) {
            if (pCtx->iTotalNumMbRec == 0)
              pCtx->pDec = NULL;
            return iRet;
          }
        }

        if (iThreadCount <= 1 && bReconstructSlice) {
          if ((iRet = WelsDecodeConstructSlice (pCtx, pNalCur)) != ERR_NONE) {
            pCtx->pDec->bIsComplete = false; // reconstruction error, directly set the flag false
            return iRet;
          }
        }
        if (bAllRefComplete && pCtx->eSliceType != I_SLICE) {
          if (iThreadCount <= 1) {
            if (pCtx->sRefPic.uiRefCount[LIST_0] > 0) {
              bAllRefComplete &= CheckRefPicturesComplete (pCtx);
            } else {
              bAllRefComplete = false;
            }
          }
        }
      }
#if defined (_DEBUG) &&  !defined (CODEC_FOR_TESTBED)
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_DEBUG, "cur_frame : %d\tiCurrIdD : %d\n ",
               dq_cur->sLayerInfo.sSliceInLayer.sSliceHeaderExt.sSliceHeader.iFrameNum, iCurrIdD);
#endif//#if !CODEC_FOR_TESTBED
      iLastIdD = iCurrIdD;
      iLastIdQ = iCurrIdQ;

      //pNalUnitsList overflow.
      ++ iIdx;
      if (iIdx <= iEndIdx) {
        pNalCur = pCurAu->pNalUnitsList[iIdx];
      } else {
        pNalCur = NULL;
      }

      if (pNalCur == NULL ||
          iLastIdD != pNalCur->sNalHeaderExt.uiDependencyId ||
          iLastIdQ != pNalCur->sNalHeaderExt.uiQualityId)
        break;
    }

    // Set the current dec picture complete flag. The flag will be reset when current picture need do ErrorCon.
    pCtx->pDec->bIsComplete = bAllRefComplete;
    if (!pCtx->pDec->bIsComplete) {  // Ref pictures ECed, result in ECed
      pCtx->iErrorCode |= dsDataErrorConcealed;
    }

    // A dq layer decoded here
#if defined (_DEBUG) &&  !defined (CODEC_FOR_TESTBED)
#undef fprintf
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_DEBUG, "POC: #%d, FRAME: #%d, D: %d, Q: %d, T: %d, P: %d, %d\n",
             pSh->iPicOrderCntLsb, pSh->iFrameNum, iCurrIdD, iCurrIdQ, dq_cur->sLayerInfo.sNalHeaderExt.uiTemporalId,
             dq_cur->sLayerInfo.sNalHeaderExt.uiPriorityId, dq_cur->sLayerInfo.sSliceInLayer.sSliceHeaderExt.sSliceHeader.iSliceQp);
#endif//#if !CODEC_FOR_TESTBED

    if (dq_cur->uiLayerDqId == kuiTargetLayerDqId) {
      if (!pCtx->bInstantDecFlag) {
        if (!pCtx->pParam->bParseOnly) {
          //Do error concealment here
          if ((NeedErrorCon (pCtx)) && (pCtx->pParam->eEcActiveIdc != ERROR_CON_DISABLE)) {
            ImplementErrorCon (pCtx);
            pCtx->iTotalNumMbRec = pCtx->pSps->iMbWidth * pCtx->pSps->iMbHeight;
            pCtx->pDec->iSpsId = pCtx->pSps->iSpsId;
            pCtx->pDec->iPpsId = pCtx->pPps->iPpsId;
          }
        }
      }

      if (iThreadCount >= 1) {
        int32_t  id = pThreadCtx->sThreadInfo.uiThrNum;
        for (int32_t i = 0; i < iThreadCount; ++i) {
          if (i == id || pThreadCtx[i - id].pCtx->uiDecodingTimeStamp == 0) continue;
          if (pThreadCtx[i - id].pCtx->uiDecodingTimeStamp < pCtx->uiDecodingTimeStamp) {
            WAIT_EVENT (&pThreadCtx[i - id].sSliceDecodeFinish, WELS_DEC_THREAD_WAIT_INFINITE);
          }
        }
        pCtx->pLastDecPicInfo->uiDecodingTimeStamp = pCtx->uiDecodingTimeStamp;
      }
      iRet = DecodeFrameConstruction (pCtx, ppDst, pDstInfo);
      if (iRet) {
        if (iThreadCount > 1) {
          SET_EVENT (&pThreadCtx->sSliceDecodeFinish);
        }
        return iRet;
      }

      pCtx->pLastDecPicInfo->pPreviousDecodedPictureInDpb = pCtx->pDec; //store latest decoded picture for EC
      pCtx->bUsedAsRef = pCtx->uiNalRefIdc > 0;
      if (iThreadCount <= 1) {
        if (pCtx->bUsedAsRef) {
          for (int32_t listIdx = LIST_0; listIdx < LIST_A; ++listIdx) {
            uint32_t i = 0;
            while (i < MAX_DPB_COUNT && pCtx->sRefPic.pRefList[listIdx][i]) {
              pCtx->pDec->pRefPic[listIdx][i] = pCtx->sRefPic.pRefList[listIdx][i];
              ++i;
            }
          }
          iRet = WelsMarkAsRef (pCtx);
          if (iRet != ERR_NONE) {
            if (iRet == ERR_INFO_DUPLICATE_FRAME_NUM)
              pCtx->iErrorCode |= dsBitstreamError;
            if (pCtx->pParam->eEcActiveIdc == ERROR_CON_DISABLE) {
              pCtx->pDec = NULL;
              return iRet;
            }
          }
          if (!pCtx->pParam->bParseOnly)
            ExpandReferencingPicture (pCtx->pDec->pData, pCtx->pDec->iWidthInPixel, pCtx->pDec->iHeightInPixel,
                                      pCtx->pDec->iLinesize,
                                      pCtx->sExpandPicFunc.pfExpandLumaPicture, pCtx->sExpandPicFunc.pfExpandChromaPicture);
        }
      } else if (iThreadCount > 1) {
        SET_EVENT (&pThreadCtx->sImageReady);
      }
      pCtx->pDec = NULL; //after frame decoding, always set to NULL
    }

    // need update frame_num due current frame is well decoded
    if (pCurAu->pNalUnitsList[pCurAu->uiStartPos]->sNalHeaderExt.sNalUnitHeader.uiNalRefIdc > 0)
      pCtx->pLastDecPicInfo->iPrevFrameNum = pSh->iFrameNum;
    if (pCtx->pLastDecPicInfo->bLastHasMmco5)
      pCtx->pLastDecPicInfo->iPrevFrameNum = 0;
    if (iThreadCount > 1) {
      int32_t  id = pThreadCtx->sThreadInfo.uiThrNum;
      for (int32_t i = 0; i < iThreadCount; ++i) {
        if (pThreadCtx[i - id].pCtx != NULL) {
          unsigned long long uiTimeStamp = pThreadCtx[i - id].pCtx->uiTimeStamp;
          if (uiTimeStamp > 0 && pThreadCtx[i - id].pCtx->sSpsPpsCtx.iSeqId > pCtx->sSpsPpsCtx.iSeqId) {
            CopySpsPps (pThreadCtx[i - id].pCtx, pCtx);
            if (pCtx->pPicBuff != pThreadCtx[i - id].pCtx->pPicBuff) {
              pCtx->pPicBuff = pThreadCtx[i - id].pCtx->pPicBuff;
            }
            InitialDqLayersContext (pCtx, pCtx->pSps->iMbWidth << 4, pCtx->pSps->iMbHeight << 4);
            break;
          }
        }
      }
    }
    if (iThreadCount > 1) {
      SET_EVENT (&pThreadCtx->sSliceDecodeFinish);
    }
  }
  return ERR_NONE;
}

bool CheckAndFinishLastPic (PWelsDecoderContext pCtx, uint8_t** ppDst, SBufferInfo* pDstInfo) {
  PAccessUnit pAu = pCtx->pAccessUnitList;
  bool bAuBoundaryFlag = false;
  if (IS_VCL_NAL (pCtx->sCurNalHead.eNalUnitType, 1)) { //VCL data, AU list should have data
    PNalUnit pCurNal = pAu->pNalUnitsList[pAu->uiEndPos];
    bAuBoundaryFlag = (pCtx->iTotalNumMbRec != 0)
                      && (CheckAccessUnitBoundaryExt (&pCtx->pLastDecPicInfo->sLastNalHdrExt, &pCurNal->sNalHeaderExt,
                          &pCtx->pLastDecPicInfo->sLastSliceHeader,
                          &pCurNal->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader));
  } else { //non VCL
    if (pCtx->sCurNalHead.eNalUnitType == NAL_UNIT_AU_DELIMITER) {
      bAuBoundaryFlag = true;
    } else if (pCtx->sCurNalHead.eNalUnitType == NAL_UNIT_SEI) {
      bAuBoundaryFlag = true;
    } else if (pCtx->sCurNalHead.eNalUnitType == NAL_UNIT_SPS) {
      bAuBoundaryFlag = !! (pCtx->sSpsPpsCtx.iOverwriteFlags & OVERWRITE_SPS);
    } else if (pCtx->sCurNalHead.eNalUnitType == NAL_UNIT_SUBSET_SPS) {
      bAuBoundaryFlag = !! (pCtx->sSpsPpsCtx.iOverwriteFlags & OVERWRITE_SUBSETSPS);
    } else if (pCtx->sCurNalHead.eNalUnitType == NAL_UNIT_PPS) {
      bAuBoundaryFlag = !! (pCtx->sSpsPpsCtx.iOverwriteFlags & OVERWRITE_PPS);
    }
    if (bAuBoundaryFlag && pCtx->pAccessUnitList->uiAvailUnitsNum != 0) { //Construct remaining data first
      ConstructAccessUnit (pCtx, ppDst, pDstInfo);
    }
  }

  //Do Error Concealment here
  if (bAuBoundaryFlag && (pCtx->iTotalNumMbRec != 0) && NeedErrorCon (pCtx)) { //AU ready but frame not completely reconed
    if (pCtx->pParam->eEcActiveIdc != ERROR_CON_DISABLE) {
      ImplementErrorCon (pCtx);
      pCtx->iTotalNumMbRec = pCtx->pSps->iMbWidth * pCtx->pSps->iMbHeight;
      pCtx->pDec->iSpsId = pCtx->pSps->iSpsId;
      pCtx->pDec->iPpsId = pCtx->pPps->iPpsId;

      DecodeFrameConstruction (pCtx, ppDst, pDstInfo);
      pCtx->pLastDecPicInfo->pPreviousDecodedPictureInDpb = pCtx->pDec; //save ECed pic for future use
      if (pCtx->pLastDecPicInfo->sLastNalHdrExt.sNalUnitHeader.uiNalRefIdc > 0) {
        if (MarkECFrameAsRef (pCtx) == ERR_INFO_INVALID_PTR) {
          pCtx->iErrorCode |= dsRefListNullPtrs;
          return false;
        }
      }
    } else if (pCtx->pParam->bParseOnly) { //clear parse only internal data status
      pCtx->pParserBsInfo->iNalNum = 0;
      pCtx->bFrameFinish = true; //clear frame pending status here!
    } else {
      if (DecodeFrameConstruction (pCtx, ppDst, pDstInfo)) {
        if ((pCtx->pLastDecPicInfo->sLastNalHdrExt.sNalUnitHeader.uiNalRefIdc > 0)
            && (pCtx->pLastDecPicInfo->sLastNalHdrExt.uiTemporalId == 0))
          pCtx->iErrorCode |= dsNoParamSets;
        else
          pCtx->iErrorCode |= dsBitstreamError;
        pCtx->pDec = NULL;
        return false;
      }
    }
    pCtx->pDec = NULL;
    if (pAu->pNalUnitsList[pAu->uiStartPos]->sNalHeaderExt.sNalUnitHeader.uiNalRefIdc > 0)
      pCtx->pLastDecPicInfo->iPrevFrameNum = pCtx->pLastDecPicInfo->sLastSliceHeader.iFrameNum; //save frame_num
    if (pCtx->pLastDecPicInfo->bLastHasMmco5)
      pCtx->pLastDecPicInfo->iPrevFrameNum = 0;
  }
  return ERR_NONE;
}

bool CheckRefPicturesComplete (PWelsDecoderContext pCtx) {
  // Multi Reference, RefIdx may differ
  bool bAllRefComplete = true;
  int32_t iRealMbIdx = pCtx->pCurDqLayer->sLayerInfo.sSliceInLayer.sSliceHeaderExt.sSliceHeader.iFirstMbInSlice;
  for (int32_t iMbIdx = 0; bAllRefComplete
       && iMbIdx < pCtx->pCurDqLayer->sLayerInfo.sSliceInLayer.iTotalMbInCurSlice; iMbIdx++) {
    switch (pCtx->pCurDqLayer->pDec->pMbType[iRealMbIdx]) {
    case MB_TYPE_SKIP:
    case MB_TYPE_16x16:
      bAllRefComplete &=
        pCtx->sRefPic.pRefList[ LIST_0 ][ pCtx->pCurDqLayer->pDec->pRefIndex[0][iRealMbIdx][0] ]->bIsComplete;
      break;

    case MB_TYPE_16x8:
      bAllRefComplete &=
        pCtx->sRefPic.pRefList[ LIST_0 ][ pCtx->pCurDqLayer->pDec->pRefIndex[0][iRealMbIdx][0] ]->bIsComplete;
      bAllRefComplete &=
        pCtx->sRefPic.pRefList[ LIST_0 ][ pCtx->pCurDqLayer->pDec->pRefIndex[0][iRealMbIdx][8] ]->bIsComplete;
      break;

    case MB_TYPE_8x16:
      bAllRefComplete &=
        pCtx->sRefPic.pRefList[ LIST_0 ][ pCtx->pCurDqLayer->pDec->pRefIndex[0][iRealMbIdx][0] ]->bIsComplete;
      bAllRefComplete &=
        pCtx->sRefPic.pRefList[ LIST_0 ][ pCtx->pCurDqLayer->pDec->pRefIndex[0][iRealMbIdx][2] ]->bIsComplete;
      break;

    case MB_TYPE_8x8:
    case MB_TYPE_8x8_REF0:
      bAllRefComplete &=
        pCtx->sRefPic.pRefList[ LIST_0 ][ pCtx->pCurDqLayer->pDec->pRefIndex[0][iRealMbIdx][0] ]->bIsComplete;
      bAllRefComplete &=
        pCtx->sRefPic.pRefList[ LIST_0 ][ pCtx->pCurDqLayer->pDec->pRefIndex[0][iRealMbIdx][2] ]->bIsComplete;
      bAllRefComplete &=
        pCtx->sRefPic.pRefList[ LIST_0 ][ pCtx->pCurDqLayer->pDec->pRefIndex[0][iRealMbIdx][8] ]->bIsComplete;
      bAllRefComplete &=
        pCtx->sRefPic.pRefList[ LIST_0 ][ pCtx->pCurDqLayer->pDec->pRefIndex[0][iRealMbIdx][10] ]->bIsComplete;
      break;

    default:
      break;
    }
    iRealMbIdx = (pCtx->pPps->uiNumSliceGroups > 1) ? FmoNextMb (pCtx->pFmo, iRealMbIdx) :
                 (pCtx->pCurDqLayer->sLayerInfo.sSliceInLayer.sSliceHeaderExt.sSliceHeader.iFirstMbInSlice + iMbIdx);
    if (iRealMbIdx == -1) //caused by abnormal return of FmoNextMb()
      return false;
  }

  return bAllRefComplete;
}
} // namespace WelsDec
