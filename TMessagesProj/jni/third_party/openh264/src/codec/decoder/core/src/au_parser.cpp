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
 * \file    au_parser.c
 *
 * \brief   Interfaces introduced in Access Unit level based parser
 *
 * \date    03/10/2009 Created
 *
 *************************************************************************************
 */
#include "codec_def.h"
#include "au_parser.h"
#include "decoder.h"
#include "error_code.h"
#include "memmgr_nal_unit.h"
#include "decoder_core.h"
#include "bit_stream.h"
#include "memory_align.h"

#define _PARSE_NALHRD_VCLHRD_PARAMS_ 1

namespace WelsDec {
/*!
 *************************************************************************************
 * \brief   Start Code Prefix (0x 00 00 00 01) detection
 *
 * \param   pBuf        bitstream payload buffer
 * \param   pOffset     offset between NAL rbsp and original bitsteam that
 *                      start code prefix is seperated from.
 * \param   iBufSize    count size of buffer
 *
 * \return  RBSP buffer of start code prefix exclusive
 *
 * \note    N/A
 *************************************************************************************
 */
uint8_t* DetectStartCodePrefix (const uint8_t* kpBuf, int32_t* pOffset, int32_t iBufSize) {
  uint8_t* pBits = (uint8_t*)kpBuf;

  do {
    int32_t iIdx = 0;
    while ((iIdx < iBufSize) && (! (*pBits))) {
      ++ pBits;
      ++ iIdx;
    }
    if (iIdx >= iBufSize)  break;

    ++ iIdx;
    ++ pBits;

    if ((iIdx >= 3) && ((* (pBits - 1)) == 0x1)) {
      *pOffset = (int32_t) (((uintptr_t)pBits) - ((uintptr_t)kpBuf));
      return pBits;
    }

    iBufSize -= iIdx;
  }  while (1);

  return NULL;
}

/*!
 *************************************************************************************
 * \brief   to parse nal unit
 *
 * \param   pCtx            decoder context
 * \param   pNalUnitHeader  parsed result of NAL Unit Header to output
 * \param   pSrcRbsp        bitstream buffer to input
 * \param   iSrcRbspLen     length size of bitstream buffer payload
 * \param   pSrcNal
 * \param   iSrcNalLen
 * \param   pConsumedBytes  consumed bytes during parsing
 *
 * \return  decoded bytes payload, might be (pSrcRbsp+1) if no escapes
 *
 * \note    N/A
 *************************************************************************************
 */
uint8_t* ParseNalHeader (PWelsDecoderContext pCtx, SNalUnitHeader* pNalUnitHeader, uint8_t* pSrcRbsp,
                         int32_t iSrcRbspLen, uint8_t* pSrcNal, int32_t iSrcNalLen, int32_t* pConsumedBytes) {
  PNalUnit pCurNal = NULL;
  uint8_t* pNal     = pSrcRbsp;
  int32_t iNalSize  = iSrcRbspLen;
  PBitStringAux pBs = NULL;
  bool bExtensionFlag = false;
  int32_t iErr = ERR_NONE;
  int32_t iBitSize = 0;
  SDataBuffer* pSavedData = &pCtx->sSavedData;
  SLogContext* pLogCtx = & (pCtx->sLogCtx);
  pNalUnitHeader->eNalUnitType = NAL_UNIT_UNSPEC_0;//SHOULD init it. because pCtx->sCurNalHead is common variable.

  //remove the consecutive ZERO at the end of current NAL in the reverse order.--2011.6.1
  {
    int32_t iIndex = iSrcRbspLen - 1;
    uint8_t uiBsZero = 0;
    while (iIndex >= 0) {
      uiBsZero = pSrcRbsp[iIndex];
      if (0 == uiBsZero) {
        --iNalSize;
        ++ (*pConsumedBytes);
        --iIndex;
      } else {
        break;
      }
    }
  }

  pNalUnitHeader->uiForbiddenZeroBit = (uint8_t) (pNal[0] >> 7); // uiForbiddenZeroBit
  if (pNalUnitHeader->uiForbiddenZeroBit) { //2010.4.14
    pCtx->iErrorCode |= dsBitstreamError;
    return NULL; //uiForbiddenZeroBit should always equal to 0
  }

  pNalUnitHeader->uiNalRefIdc   = (uint8_t) (pNal[0] >> 5);             // uiNalRefIdc
  pNalUnitHeader->eNalUnitType  = (EWelsNalUnitType) (pNal[0] & 0x1f);  // eNalUnitType

  ++pNal;
  --iNalSize;
  ++ (*pConsumedBytes);

  if (! (IS_SEI_NAL (pNalUnitHeader->eNalUnitType) || IS_SPS_NAL (pNalUnitHeader->eNalUnitType)
         || IS_AU_DELIMITER_NAL (pNalUnitHeader->eNalUnitType) || pCtx->sSpsPpsCtx.bSpsExistAheadFlag)) {
    if (pCtx->bPrintFrameErrorTraceFlag && pCtx->sSpsPpsCtx.iSpsErrorIgnored == 0) {
      WelsLog (pLogCtx, WELS_LOG_WARNING,
               "parse_nal(), no exist Sequence Parameter Sets ahead of sequence when try to decode NAL(type:%d).",
               pNalUnitHeader->eNalUnitType);
    } else {
      pCtx->sSpsPpsCtx.iSpsErrorIgnored++;
    }
    pCtx->pDecoderStatistics->iSpsNoExistNalNum++;
    pCtx->iErrorCode = dsNoParamSets;
    return NULL;
  }
  pCtx->sSpsPpsCtx.iSpsErrorIgnored = 0;
  if (! (IS_SEI_NAL (pNalUnitHeader->eNalUnitType) || IS_PARAM_SETS_NALS (pNalUnitHeader->eNalUnitType)
         || IS_AU_DELIMITER_NAL (pNalUnitHeader->eNalUnitType) || pCtx->sSpsPpsCtx.bPpsExistAheadFlag)) {
    if (pCtx->bPrintFrameErrorTraceFlag && pCtx->sSpsPpsCtx.iPpsErrorIgnored == 0) {
      WelsLog (pLogCtx, WELS_LOG_WARNING,
               "parse_nal(), no exist Picture Parameter Sets ahead of sequence when try to decode NAL(type:%d).",
               pNalUnitHeader->eNalUnitType);
    } else {
      pCtx->sSpsPpsCtx.iPpsErrorIgnored++;
    }
    pCtx->pDecoderStatistics->iPpsNoExistNalNum++;
    pCtx->iErrorCode = dsNoParamSets;
    return NULL;
  }
  pCtx->sSpsPpsCtx.iPpsErrorIgnored = 0;
  if ((IS_VCL_NAL_AVC_BASE (pNalUnitHeader->eNalUnitType) && ! (pCtx->sSpsPpsCtx.bSpsExistAheadFlag
       || pCtx->sSpsPpsCtx.bPpsExistAheadFlag)) ||
      (IS_NEW_INTRODUCED_SVC_NAL (pNalUnitHeader->eNalUnitType) && ! (pCtx->sSpsPpsCtx.bSpsExistAheadFlag
          || pCtx->sSpsPpsCtx.bSubspsExistAheadFlag
          || pCtx->sSpsPpsCtx.bPpsExistAheadFlag))) {
    if (pCtx->bPrintFrameErrorTraceFlag && pCtx->sSpsPpsCtx.iSubSpsErrorIgnored == 0) {
      WelsLog (pLogCtx, WELS_LOG_WARNING,
               "ParseNalHeader(), no exist Parameter Sets ahead of sequence when try to decode slice(type:%d).",
               pNalUnitHeader->eNalUnitType);
    } else {
      pCtx->sSpsPpsCtx.iSubSpsErrorIgnored++;
    }
    pCtx->pDecoderStatistics->iSubSpsNoExistNalNum++;
    pCtx->iErrorCode    |= dsNoParamSets;
    return NULL;
  }
  pCtx->sSpsPpsCtx.iSubSpsErrorIgnored = 0;

  switch (pNalUnitHeader->eNalUnitType) {
  case NAL_UNIT_AU_DELIMITER:
  case NAL_UNIT_SEI:
    if (pCtx->pAccessUnitList->uiAvailUnitsNum > 0) {
      pCtx->pAccessUnitList->uiEndPos = pCtx->pAccessUnitList->uiAvailUnitsNum - 1;
      pCtx->bAuReadyFlag = true;
    }
    break;

  case NAL_UNIT_PREFIX:
    pCurNal = &pCtx->sSpsPpsCtx.sPrefixNal;
    pCurNal->uiTimeStamp = pCtx->uiTimeStamp;

    if (iNalSize < NAL_UNIT_HEADER_EXT_SIZE) {
      PAccessUnit pCurAu = pCtx->pAccessUnitList;
      uint32_t uiAvailNalNum = pCurAu->uiAvailUnitsNum;

      if (uiAvailNalNum > 0) {
        pCurAu->uiEndPos = uiAvailNalNum - 1;
        if (pCtx->pParam->eEcActiveIdc == ERROR_CON_DISABLE) {
          pCtx->bAuReadyFlag = true;
        }
      }
      pCurNal->sNalData.sPrefixNal.bPrefixNalCorrectFlag = false;
      pCtx->iErrorCode |= dsBitstreamError;
      return NULL;
    }

    DecodeNalHeaderExt (pCurNal, pNal);
    if ((pCurNal->sNalHeaderExt.uiQualityId != 0) || (pCurNal->sNalHeaderExt.bUseRefBasePicFlag != 0)) {
      WelsLog (pLogCtx, WELS_LOG_WARNING,
               "ParseNalHeader() in Prefix Nal Unit:uiQualityId (%d) != 0, bUseRefBasePicFlag (%d) != 0, not supported!",
               pCurNal->sNalHeaderExt.uiQualityId, pCurNal->sNalHeaderExt.bUseRefBasePicFlag);
      PAccessUnit pCurAu = pCtx->pAccessUnitList;
      uint32_t uiAvailNalNum = pCurAu->uiAvailUnitsNum;

      if (uiAvailNalNum > 0) {
        pCurAu->uiEndPos = uiAvailNalNum - 1;
        if (pCtx->pParam->eEcActiveIdc == ERROR_CON_DISABLE) {
          pCtx->bAuReadyFlag = true;
        }
      }
      pCurNal->sNalData.sPrefixNal.bPrefixNalCorrectFlag = false;
      pCtx->iErrorCode |= dsBitstreamError;
      return NULL;
    }

    pNal            += NAL_UNIT_HEADER_EXT_SIZE;
    iNalSize        -= NAL_UNIT_HEADER_EXT_SIZE;
    *pConsumedBytes += NAL_UNIT_HEADER_EXT_SIZE;

    pCurNal->sNalHeaderExt.sNalUnitHeader.uiForbiddenZeroBit = pNalUnitHeader->uiForbiddenZeroBit;
    pCurNal->sNalHeaderExt.sNalUnitHeader.uiNalRefIdc        = pNalUnitHeader->uiNalRefIdc;
    pCurNal->sNalHeaderExt.sNalUnitHeader.eNalUnitType       = pNalUnitHeader->eNalUnitType;
    if (pNalUnitHeader->uiNalRefIdc != 0) {
      pBs = &pCtx->sBs;
      iBitSize = (iNalSize << 3) - BsGetTrailingBits (pNal + iNalSize - 1); // convert into bit

      iErr = DecInitBits (pBs, pNal, iBitSize);
      if (iErr) {
        WelsLog (pLogCtx, WELS_LOG_ERROR, "NAL_UNIT_PREFIX: DecInitBits() fail due invalid access.");
        pCtx->iErrorCode |= dsBitstreamError;
        return NULL;
      }
      ParsePrefixNalUnit (pCtx, pBs);
    }
    pCurNal->sNalData.sPrefixNal.bPrefixNalCorrectFlag = true;

    break;
  case NAL_UNIT_CODED_SLICE_EXT:
    bExtensionFlag = true;
  case NAL_UNIT_CODED_SLICE:
  case NAL_UNIT_CODED_SLICE_IDR: {
    PAccessUnit pCurAu = NULL;
    uint32_t uiAvailNalNum;
    pCurNal = MemGetNextNal (&pCtx->pAccessUnitList, pCtx->pMemAlign);
    if (NULL == pCurNal) {
      WelsLog (pLogCtx, WELS_LOG_ERROR, "MemGetNextNal() fail due out of memory.");
      pCtx->iErrorCode |= dsOutOfMemory;
      return NULL;
    }
    pCurNal->uiTimeStamp = pCtx->uiTimeStamp;
    pCurNal->sNalHeaderExt.sNalUnitHeader.uiForbiddenZeroBit = pNalUnitHeader->uiForbiddenZeroBit;
    pCurNal->sNalHeaderExt.sNalUnitHeader.uiNalRefIdc        = pNalUnitHeader->uiNalRefIdc;
    pCurNal->sNalHeaderExt.sNalUnitHeader.eNalUnitType       = pNalUnitHeader->eNalUnitType;
    pCurAu        = pCtx->pAccessUnitList;
    uiAvailNalNum = pCurAu->uiAvailUnitsNum;


    if (pNalUnitHeader->eNalUnitType == NAL_UNIT_CODED_SLICE_EXT) {
      if (iNalSize < NAL_UNIT_HEADER_EXT_SIZE) {
        ForceClearCurrentNal (pCurAu);

        if (uiAvailNalNum > 1) {
          pCurAu->uiEndPos = uiAvailNalNum - 2;
          if (pCtx->pParam->eEcActiveIdc == ERROR_CON_DISABLE) {
            pCtx->bAuReadyFlag = true;
          }
        }
        pCtx->iErrorCode |= dsBitstreamError;
        return NULL;
      }

      DecodeNalHeaderExt (pCurNal, pNal);
      if (pCurNal->sNalHeaderExt.uiQualityId != 0 ||
          pCurNal->sNalHeaderExt.bUseRefBasePicFlag) {
        if (pCurNal->sNalHeaderExt.uiQualityId != 0)
          WelsLog (pLogCtx, WELS_LOG_WARNING, "ParseNalHeader():uiQualityId (%d) != 0, MGS not supported!",
                   pCurNal->sNalHeaderExt.uiQualityId);
        if (pCurNal->sNalHeaderExt.bUseRefBasePicFlag != 0)
          WelsLog (pLogCtx, WELS_LOG_WARNING, "ParseNalHeader():bUseRefBasePicFlag (%d) != 0, MGS not supported!",
                   pCurNal->sNalHeaderExt.bUseRefBasePicFlag);

        ForceClearCurrentNal (pCurAu);

        if (uiAvailNalNum > 1) {
          pCurAu->uiEndPos = uiAvailNalNum - 2;
          if (pCtx->pParam->eEcActiveIdc == ERROR_CON_DISABLE) {
            pCtx->bAuReadyFlag = true;
          }
        }
        pCtx->iErrorCode |= dsBitstreamError;
        return NULL;
      }
      pNal            += NAL_UNIT_HEADER_EXT_SIZE;
      iNalSize        -= NAL_UNIT_HEADER_EXT_SIZE;
      *pConsumedBytes += NAL_UNIT_HEADER_EXT_SIZE;

      if (pCtx->pParam->bParseOnly) {
        pCurNal->sNalData.sVclNal.pNalPos = pSavedData->pCurPos;
        int32_t iTrailingZeroByte = 0;
        while (pSrcNal[iSrcNalLen - iTrailingZeroByte - 1] == 0x0) //remove final trailing 0 bytes
          iTrailingZeroByte++;
        int32_t iActualLen = iSrcNalLen - iTrailingZeroByte;
        pCurNal->sNalData.sVclNal.iNalLength = iActualLen - NAL_UNIT_HEADER_EXT_SIZE;
        //unify start code as 0x0001
        int32_t iCurrStartByte = 4; //4 for 0x0001, 3 for 0x001
        if (pSrcNal[0] == 0x0 && pSrcNal[1] == 0x0 && pSrcNal[2] == 0x1) { //if 0x001
          iCurrStartByte = 3;
          pCurNal->sNalData.sVclNal.iNalLength++;
        }
        if (pCurNal->sNalHeaderExt.bIdrFlag) {
          * (pSrcNal + iCurrStartByte) &= 0xE0;
          * (pSrcNal + iCurrStartByte) |= 0x05;
        } else {
          * (pSrcNal + iCurrStartByte) &= 0xE0;
          * (pSrcNal + iCurrStartByte) |= 0x01;
        }
        pSavedData->pCurPos[0] = pSavedData->pCurPos[1] = pSavedData->pCurPos[2] = 0x0;
        pSavedData->pCurPos[3] = 0x1;
        pSavedData->pCurPos[4] = * (pSrcNal + iCurrStartByte);
        pSavedData->pCurPos += 5;
        int32_t iOffset = iCurrStartByte + 1 + NAL_UNIT_HEADER_EXT_SIZE;
        memcpy (pSavedData->pCurPos, pSrcNal + iOffset, iActualLen - iOffset);
        pSavedData->pCurPos += iActualLen - iOffset;
      }
    } else {
      if (pCtx->pParam->bParseOnly) {
        pCurNal->sNalData.sVclNal.pNalPos = pSavedData->pCurPos;
        int32_t iTrailingZeroByte = 0;
        while (pSrcNal[iSrcNalLen - iTrailingZeroByte - 1] == 0x0) //remove final trailing 0 bytes
          iTrailingZeroByte++;
        int32_t iActualLen = iSrcNalLen - iTrailingZeroByte;
        pCurNal->sNalData.sVclNal.iNalLength = iActualLen;
        //unify start code as 0x0001
        int32_t iStartDeltaByte = 0; //0 for 0x0001, 1 for 0x001
        if (pSrcNal[0] == 0x0 && pSrcNal[1] == 0x0 && pSrcNal[2] == 0x1) { //if 0x001
          pSavedData->pCurPos[0] = 0x0;
          iStartDeltaByte = 1;
          pCurNal->sNalData.sVclNal.iNalLength++;
        }
        memcpy (pSavedData->pCurPos + iStartDeltaByte, pSrcNal, iActualLen);
        pSavedData->pCurPos += iStartDeltaByte + iActualLen;
      }
      if (NAL_UNIT_PREFIX == pCtx->sSpsPpsCtx.sPrefixNal.sNalHeaderExt.sNalUnitHeader.eNalUnitType) {
        if (pCtx->sSpsPpsCtx.sPrefixNal.sNalData.sPrefixNal.bPrefixNalCorrectFlag) {
          PrefetchNalHeaderExtSyntax (pCtx, pCurNal, &pCtx->sSpsPpsCtx.sPrefixNal);
        }
      }

      pCurNal->sNalHeaderExt.bIdrFlag = (NAL_UNIT_CODED_SLICE_IDR == pNalUnitHeader->eNalUnitType) ? true :
                                        false;   //SHOULD update this flag for AVC if no prefix NAL
      pCurNal->sNalHeaderExt.iNoInterLayerPredFlag = 1;
    }

    pBs = &pCurAu->pNalUnitsList[uiAvailNalNum - 1]->sNalData.sVclNal.sSliceBitsRead;
    iBitSize = (iNalSize << 3) - BsGetTrailingBits (pNal + iNalSize - 1); // convert into bit
    iErr = DecInitBits (pBs, pNal, iBitSize);
    if (iErr) {
      ForceClearCurrentNal (pCurAu);
      if (uiAvailNalNum > 1) {
        pCurAu->uiEndPos = uiAvailNalNum - 2;
        if (pCtx->pParam->eEcActiveIdc == ERROR_CON_DISABLE) {
          pCtx->bAuReadyFlag = true;
        }
      }
      WelsLog (pLogCtx, WELS_LOG_ERROR, "NAL_UNIT_CODED_SLICE: DecInitBits() fail due invalid access.");
      pCtx->iErrorCode |= dsBitstreamError;
      return NULL;
    }
    iErr = ParseSliceHeaderSyntaxs (pCtx, pBs, bExtensionFlag);
    if (iErr != ERR_NONE) {
      if ((uiAvailNalNum == 1) && (pCurNal->sNalHeaderExt.bIdrFlag)) { //IDR parse error
        ResetActiveSPSForEachLayer (pCtx);
      }
      //if current NAL occur error when parsing, should clean it from pNalUnitsList
      //otherwise, when Next good NAL decoding, this corrupt NAL is considered as normal NAL and lead to decoder crash
      ForceClearCurrentNal (pCurAu);

      if (uiAvailNalNum > 1) {
        pCurAu->uiEndPos = uiAvailNalNum - 2;
        if (pCtx->pParam->eEcActiveIdc == ERROR_CON_DISABLE) {
          pCtx->bAuReadyFlag = true;
        }
      }
      pCtx->iErrorCode |= dsBitstreamError;
      return NULL;
    }

    if ((uiAvailNalNum == 1)
        && CheckNextAuNewSeq (pCtx, pCurNal, pCurNal->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader.pSps)) {
      ResetActiveSPSForEachLayer (pCtx);
    }
    if ((uiAvailNalNum > 1) &&
        CheckAccessUnitBoundary (pCtx, pCurAu->pNalUnitsList[uiAvailNalNum - 1], pCurAu->pNalUnitsList[uiAvailNalNum - 2],
                                 pCurAu->pNalUnitsList[uiAvailNalNum - 1]->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader.pSps)) {
      pCurAu->uiEndPos = uiAvailNalNum - 2;
      pCtx->bAuReadyFlag = true;
      pCtx->bNextNewSeqBegin = CheckNextAuNewSeq (pCtx, pCurNal, pCurNal->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader.pSps);

    }
  }
  break;
  default:
    break;
  }

  return pNal;
}


bool CheckAccessUnitBoundaryExt (PNalUnitHeaderExt pLastNalHdrExt, PNalUnitHeaderExt pCurNalHeaderExt,
                                 PSliceHeader pLastSliceHeader, PSliceHeader pCurSliceHeader) {
  const PSps kpSps = pCurSliceHeader->pSps;

  //Sub-clause 7.1.4.1.1 temporal_id
  if (pLastNalHdrExt->uiTemporalId != pCurNalHeaderExt->uiTemporalId) {
    return true;
  }

  // Subclause 7.4.1.2.5
  if (pLastSliceHeader->iRedundantPicCnt > pCurSliceHeader->iRedundantPicCnt)
    return true;

  // Subclause G7.4.1.2.4
  if (pLastNalHdrExt->uiDependencyId > pCurNalHeaderExt->uiDependencyId)
    return true;
  if (pLastNalHdrExt->uiQualityId > pCurNalHeaderExt->uiQualityId)
    return true;

  // Subclause 7.4.1.2.4
  if (pLastSliceHeader->iFrameNum != pCurSliceHeader->iFrameNum)
    return true;
  if (pLastSliceHeader->iPpsId != pCurSliceHeader->iPpsId)
    return true;
  if (pLastSliceHeader->pSps->iSpsId != pCurSliceHeader->pSps->iSpsId)
    return true;
  if (pLastSliceHeader->bFieldPicFlag != pCurSliceHeader->bFieldPicFlag)
    return true;
  if (pLastSliceHeader->bBottomFiledFlag != pCurSliceHeader->bBottomFiledFlag)
    return true;
  if ((pLastNalHdrExt->sNalUnitHeader.uiNalRefIdc != NRI_PRI_LOWEST) != (pCurNalHeaderExt->sNalUnitHeader.uiNalRefIdc !=
      NRI_PRI_LOWEST))
    return true;
  if (pLastNalHdrExt->bIdrFlag != pCurNalHeaderExt->bIdrFlag)
    return true;
  if (pCurNalHeaderExt->bIdrFlag) {
    if (pLastSliceHeader->uiIdrPicId != pCurSliceHeader->uiIdrPicId)
      return true;
  }
  if (kpSps->uiPocType == 0) {
    if (pLastSliceHeader->iPicOrderCntLsb != pCurSliceHeader->iPicOrderCntLsb)
      return true;
    if (pLastSliceHeader->iDeltaPicOrderCntBottom != pCurSliceHeader->iDeltaPicOrderCntBottom)
      return true;
  } else if (kpSps->uiPocType == 1) {
    if (pLastSliceHeader->iDeltaPicOrderCnt[0] != pCurSliceHeader->iDeltaPicOrderCnt[0])
      return true;
    if (pLastSliceHeader->iDeltaPicOrderCnt[1] != pCurSliceHeader->iDeltaPicOrderCnt[1])
      return true;
  }
  if (memcmp (pLastSliceHeader->pPps, pCurSliceHeader->pPps, sizeof (SPps)) != 0
      || memcmp (pLastSliceHeader->pSps, pCurSliceHeader->pSps, sizeof (SSps)) != 0) {
    return true;
  }
  return false;
}


bool CheckAccessUnitBoundary (PWelsDecoderContext pCtx, const PNalUnit kpCurNal, const PNalUnit kpLastNal,
                              const PSps kpSps) {
  const PNalUnitHeaderExt kpLastNalHeaderExt = &kpLastNal->sNalHeaderExt;
  const PNalUnitHeaderExt kpCurNalHeaderExt = &kpCurNal->sNalHeaderExt;
  const SSliceHeader* kpLastSliceHeader = &kpLastNal->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader;
  const SSliceHeader* kpCurSliceHeader = &kpCurNal->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader;
  if (pCtx->sSpsPpsCtx.pActiveLayerSps[kpCurNalHeaderExt->uiDependencyId] != NULL
      && pCtx->sSpsPpsCtx.pActiveLayerSps[kpCurNalHeaderExt->uiDependencyId] != kpSps) {
    return true; // the active sps changed, new sequence begins, so the current au is ready
  }

  //Sub-clause 7.1.4.1.1 temporal_id
  if (kpLastNalHeaderExt->uiTemporalId != kpCurNalHeaderExt->uiTemporalId) {
    return true;
  }
  if (kpLastSliceHeader->iFrameNum != kpCurSliceHeader->iFrameNum)
    return true;
  // Subclause 7.4.1.2.5
  if (kpLastSliceHeader->iRedundantPicCnt > kpCurSliceHeader->iRedundantPicCnt)
    return true;

  // Subclause G7.4.1.2.4
  if (kpLastNalHeaderExt->uiDependencyId > kpCurNalHeaderExt->uiDependencyId)
    return true;
  // Subclause 7.4.1.2.4
  if (kpLastNalHeaderExt->uiDependencyId == kpCurNalHeaderExt->uiDependencyId
      && kpLastSliceHeader->iPpsId != kpCurSliceHeader->iPpsId)
    return true;
  if (kpLastSliceHeader->bFieldPicFlag != kpCurSliceHeader->bFieldPicFlag)
    return true;
  if (kpLastSliceHeader->bBottomFiledFlag != kpCurSliceHeader->bBottomFiledFlag)
    return true;
  if ((kpLastNalHeaderExt->sNalUnitHeader.uiNalRefIdc != NRI_PRI_LOWEST) != (kpCurNalHeaderExt->sNalUnitHeader.uiNalRefIdc
      != NRI_PRI_LOWEST))
    return true;
  if (kpLastNalHeaderExt->bIdrFlag != kpCurNalHeaderExt->bIdrFlag)
    return true;
  if (kpCurNalHeaderExt->bIdrFlag) {
    if (kpLastSliceHeader->uiIdrPicId != kpCurSliceHeader->uiIdrPicId)
      return true;
  }
  if (kpSps->uiPocType == 0) {
    if (kpLastSliceHeader->iPicOrderCntLsb != kpCurSliceHeader->iPicOrderCntLsb)
      return true;
    if (kpLastSliceHeader->iDeltaPicOrderCntBottom != kpCurSliceHeader->iDeltaPicOrderCntBottom)
      return true;
  } else if (kpSps->uiPocType == 1) {
    if (kpLastSliceHeader->iDeltaPicOrderCnt[0] != kpCurSliceHeader->iDeltaPicOrderCnt[0])
      return true;
    if (kpLastSliceHeader->iDeltaPicOrderCnt[1] != kpCurSliceHeader->iDeltaPicOrderCnt[1])
      return true;
  }

  return false;
}

bool CheckNextAuNewSeq (PWelsDecoderContext pCtx, const PNalUnit kpCurNal, const PSps kpSps) {
  const PNalUnitHeaderExt kpCurNalHeaderExt = &kpCurNal->sNalHeaderExt;
  if (pCtx->sSpsPpsCtx.pActiveLayerSps[kpCurNalHeaderExt->uiDependencyId] != NULL
      && pCtx->sSpsPpsCtx.pActiveLayerSps[kpCurNalHeaderExt->uiDependencyId] != kpSps)
    return true;
  if (kpCurNalHeaderExt->bIdrFlag)
    return true;

  return false;
}

/*!
 *************************************************************************************
 * \brief   to parse NON VCL NAL Units
 *
 * \param   pCtx        decoder context
 * \param   rbsp        rbsp buffer of NAL Unit
 * \param   src_len     length of rbsp buffer
 *
 * \return  0 - successed
 *          1 - failed
 *
 *************************************************************************************
 */
int32_t ParseNonVclNal (PWelsDecoderContext pCtx, uint8_t* pRbsp, const int32_t kiSrcLen, uint8_t* pSrcNal,
                        const int32_t kSrcNalLen) {
  PBitStringAux pBs = NULL;
  EWelsNalUnitType eNalType     = NAL_UNIT_UNSPEC_0; // make initial value as unspecified
  int32_t iPicWidth             = 0;
  int32_t iPicHeight            = 0;
  int32_t iBitSize              = 0;
  int32_t iErr                  = ERR_NONE;
  if (kiSrcLen <= 0)
    return iErr;

  pBs = &pCtx->sBs; // SBitStringAux instance for non VCL NALs decoding
  iBitSize = (kiSrcLen << 3) - BsGetTrailingBits (pRbsp + kiSrcLen - 1); // convert into bit
  eNalType = pCtx->sCurNalHead.eNalUnitType;

  switch (eNalType) {
  case NAL_UNIT_SPS:
  case NAL_UNIT_SUBSET_SPS:
    if (iBitSize > 0) {
      iErr = DecInitBits (pBs, pRbsp, iBitSize);
      if (ERR_NONE != iErr) {
        if (pCtx->pParam->eEcActiveIdc == ERROR_CON_DISABLE)
          pCtx->iErrorCode |= dsNoParamSets;
        else
          pCtx->iErrorCode |= dsBitstreamError;
        return iErr;
      }
    }
    iErr = ParseSps (pCtx, pBs, &iPicWidth, &iPicHeight, pSrcNal, kSrcNalLen);
    if (ERR_NONE != iErr) { // modified for pSps/pSubsetSps invalid, 12/1/2009
      if (pCtx->pParam->eEcActiveIdc == ERROR_CON_DISABLE)
        pCtx->iErrorCode |= dsNoParamSets;
      else
        pCtx->iErrorCode |= dsBitstreamError;
      return iErr;
    }
    pCtx->bHasNewSps = true;
    break;

  case NAL_UNIT_PPS:
    if (iBitSize > 0) {
      iErr = DecInitBits (pBs, pRbsp, iBitSize);
      if (ERR_NONE != iErr) {
        if (pCtx->pParam->eEcActiveIdc == ERROR_CON_DISABLE)
          pCtx->iErrorCode |= dsNoParamSets;
        else
          pCtx->iErrorCode |= dsBitstreamError;
        return iErr;
      }
    }
    iErr = ParsePps (pCtx, &pCtx->sSpsPpsCtx.sPpsBuffer[0], pBs, pSrcNal, kSrcNalLen);
    if (ERR_NONE != iErr) { // modified for pps invalid, 12/1/2009
      if (pCtx->pParam->eEcActiveIdc == ERROR_CON_DISABLE)
        pCtx->iErrorCode |= dsNoParamSets;
      else
        pCtx->iErrorCode |= dsBitstreamError;
      pCtx->bHasNewSps = false;
      return iErr;
    }

    pCtx->sSpsPpsCtx.bPpsExistAheadFlag = true;
    ++ (pCtx->sSpsPpsCtx.iSeqId);
    break;

  case NAL_UNIT_SEI:

    break;

  case NAL_UNIT_PREFIX:
    break;
  case NAL_UNIT_CODED_SLICE_DPA:
  case NAL_UNIT_CODED_SLICE_DPB:
  case NAL_UNIT_CODED_SLICE_DPC:

    break;

  default:
    break;
  }

  return iErr;
}

int32_t ParseRefBasePicMarking (PBitStringAux pBs, PRefBasePicMarking pRefBasePicMarking) {
  uint32_t uiCode;
  WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //adaptive_ref_base_pic_marking_mode_flag
  const bool kbAdaptiveMarkingModeFlag = !!uiCode;
  pRefBasePicMarking->bAdaptiveRefBasePicMarkingModeFlag = kbAdaptiveMarkingModeFlag;
  if (kbAdaptiveMarkingModeFlag) {
    int32_t iIdx = 0;
    do {
      WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //MMCO_base
      const uint32_t kuiMmco = uiCode;

      pRefBasePicMarking->mmco_base[iIdx].uiMmcoType = kuiMmco;

      if (kuiMmco == MMCO_END)
        break;

      if (kuiMmco == MMCO_SHORT2UNUSED) {
        WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //difference_of_base_pic_nums_minus1
        pRefBasePicMarking->mmco_base[iIdx].uiDiffOfPicNums = 1 + uiCode;
        pRefBasePicMarking->mmco_base[iIdx].iShortFrameNum  = 0;
      } else if (kuiMmco == MMCO_LONG2UNUSED) {
        WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //long_term_base_pic_num
        pRefBasePicMarking->mmco_base[iIdx].uiLongTermPicNum = uiCode;
      }
      ++ iIdx;
    } while (iIdx < MAX_MMCO_COUNT);
  }
  return ERR_NONE;
}

int32_t ParsePrefixNalUnit (PWelsDecoderContext pCtx, PBitStringAux pBs) {
  PNalUnit pCurNal = &pCtx->sSpsPpsCtx.sPrefixNal;
  uint32_t uiCode;

  if (pCurNal->sNalHeaderExt.sNalUnitHeader.uiNalRefIdc != 0) {
    PNalUnitHeaderExt head_ext = &pCurNal->sNalHeaderExt;
    PPrefixNalUnit sPrefixNal = &pCurNal->sNalData.sPrefixNal;
    WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //store_ref_base_pic_flag
    sPrefixNal->bStoreRefBasePicFlag = !!uiCode;
    if ((head_ext->bUseRefBasePicFlag || sPrefixNal->bStoreRefBasePicFlag) && !head_ext->bIdrFlag) {
      WELS_READ_VERIFY (ParseRefBasePicMarking (pBs, &sPrefixNal->sRefPicBaseMarking));
    }
    WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //additional_prefix_nal_unit_extension_flag
    sPrefixNal->bPrefixNalUnitAdditionalExtFlag = !!uiCode;
    if (sPrefixNal->bPrefixNalUnitAdditionalExtFlag) {
      WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //additional_prefix_nal_unit_extension_data_flag
      sPrefixNal->bPrefixNalUnitExtFlag = !!uiCode;
    }
  }
  return ERR_NONE;
}

#define SUBSET_SPS_SEQ_SCALED_REF_LAYER_LEFT_OFFSET_MIN -32768
#define SUBSET_SPS_SEQ_SCALED_REF_LAYER_LEFT_OFFSET_MAX 32767
#define SUBSET_SPS_SEQ_SCALED_REF_LAYER_TOP_OFFSET_MIN -32768
#define SUBSET_SPS_SEQ_SCALED_REF_LAYER_TOP_OFFSET_MAX 32767
#define SUBSET_SPS_SEQ_SCALED_REF_LAYER_RIGHT_OFFSET_MIN -32768
#define SUBSET_SPS_SEQ_SCALED_REF_LAYER_RIGHT_OFFSET_MAX 32767
#define SUBSET_SPS_SEQ_SCALED_REF_LAYER_BOTTOM_OFFSET_MIN -32768
#define SUBSET_SPS_SEQ_SCALED_REF_LAYER_BOTTOM_OFFSET_MAX 32767




int32_t DecodeSpsSvcExt (PWelsDecoderContext pCtx, PSubsetSps pSpsExt, PBitStringAux pBs) {
  PSpsSvcExt  pExt = NULL;
  uint32_t uiCode;
  int32_t iCode;

  pExt = &pSpsExt->sSpsSvcExt;

  WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //inter_layer_deblocking_filter_control_present_flag
  pExt->bInterLayerDeblockingFilterCtrlPresentFlag = !!uiCode;
  WELS_READ_VERIFY (BsGetBits (pBs, 2, &uiCode)); //extended_spatial_scalability_idc
  pExt->uiExtendedSpatialScalability = uiCode;
  if (pExt->uiExtendedSpatialScalability > 2) {
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING,
             "DecodeSpsSvcExt():extended_spatial_scalability (%d) != 0, ESS not supported!",
             pExt->uiExtendedSpatialScalability);
    return GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_INVALID_ESS);
  }

  pExt->uiChromaPhaseXPlus1Flag =
    0;  // FIXME: Incoherent with JVT X201 standard (= 1), but conformance to JSVM (= 0) implementation.
  pExt->uiChromaPhaseYPlus1 = 1;

  WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //chroma_phase_x_plus1_flag
  pExt->uiChromaPhaseXPlus1Flag = uiCode;
  WELS_READ_VERIFY (BsGetBits (pBs, 2, &uiCode)); //chroma_phase_y_plus1
  pExt->uiChromaPhaseYPlus1 = uiCode;

  pExt->uiSeqRefLayerChromaPhaseXPlus1Flag = pExt->uiChromaPhaseXPlus1Flag;
  pExt->uiSeqRefLayerChromaPhaseYPlus1     = pExt->uiChromaPhaseYPlus1;
  memset (&pExt->sSeqScaledRefLayer, 0, sizeof (SPosOffset));

  if (pExt->uiExtendedSpatialScalability == 1) {
    SPosOffset* const kpPos = &pExt->sSeqScaledRefLayer;
    WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //seq_ref_layer_chroma_phase_x_plus1_flag
    pExt->uiSeqRefLayerChromaPhaseXPlus1Flag = uiCode;
    WELS_READ_VERIFY (BsGetBits (pBs, 2, &uiCode)); //seq_ref_layer_chroma_phase_y_plus1
    pExt->uiSeqRefLayerChromaPhaseYPlus1 = uiCode;

    WELS_READ_VERIFY (BsGetSe (pBs, &iCode)); //seq_scaled_ref_layer_left_offset
    kpPos->iLeftOffset = iCode;
    WELS_CHECK_SE_BOTH_WARNING (kpPos->iLeftOffset, SUBSET_SPS_SEQ_SCALED_REF_LAYER_LEFT_OFFSET_MIN,
                                SUBSET_SPS_SEQ_SCALED_REF_LAYER_LEFT_OFFSET_MAX, "seq_scaled_ref_layer_left_offset");
    WELS_READ_VERIFY (BsGetSe (pBs, &iCode)); //seq_scaled_ref_layer_top_offset
    kpPos->iTopOffset = iCode;
    WELS_CHECK_SE_BOTH_WARNING (kpPos->iTopOffset, SUBSET_SPS_SEQ_SCALED_REF_LAYER_TOP_OFFSET_MIN,
                                SUBSET_SPS_SEQ_SCALED_REF_LAYER_TOP_OFFSET_MAX, "seq_scaled_ref_layer_top_offset");
    WELS_READ_VERIFY (BsGetSe (pBs, &iCode)); //seq_scaled_ref_layer_right_offset
    kpPos->iRightOffset = iCode;
    WELS_CHECK_SE_BOTH_WARNING (kpPos->iRightOffset, SUBSET_SPS_SEQ_SCALED_REF_LAYER_RIGHT_OFFSET_MIN,
                                SUBSET_SPS_SEQ_SCALED_REF_LAYER_RIGHT_OFFSET_MAX, "seq_scaled_ref_layer_right_offset");
    WELS_READ_VERIFY (BsGetSe (pBs, &iCode)); //seq_scaled_ref_layer_bottom_offset
    kpPos->iBottomOffset = iCode;
    WELS_CHECK_SE_BOTH_WARNING (kpPos->iBottomOffset, SUBSET_SPS_SEQ_SCALED_REF_LAYER_BOTTOM_OFFSET_MIN,
                                SUBSET_SPS_SEQ_SCALED_REF_LAYER_BOTTOM_OFFSET_MAX, "seq_scaled_ref_layer_bottom_offset");
  }

  WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //seq_tcoeff_level_prediction_flag
  pExt->bSeqTCoeffLevelPredFlag = !!uiCode;
  pExt->bAdaptiveTCoeffLevelPredFlag = false;
  if (pExt->bSeqTCoeffLevelPredFlag) {
    WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //adaptive_tcoeff_level_prediction_flag
    pExt->bAdaptiveTCoeffLevelPredFlag = !!uiCode;
  }
  WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //slice_header_restriction_flag
  pExt->bSliceHeaderRestrictionFlag = !!uiCode;



  return ERR_NONE;
}

const SLevelLimits* GetLevelLimits (int32_t iLevelIdx, bool bConstraint3) {
  switch (iLevelIdx) {
  case 9:
    return &g_ksLevelLimits[1];
  case 10:
    return &g_ksLevelLimits[0];
  case 11:
    if (bConstraint3)
      return &g_ksLevelLimits[1];
    else
      return &g_ksLevelLimits[2];
  case 12:
    return &g_ksLevelLimits[3];
  case 13:
    return &g_ksLevelLimits[4];
  case 20:
    return &g_ksLevelLimits[5];
  case 21:
    return &g_ksLevelLimits[6];
  case 22:
    return &g_ksLevelLimits[7];
  case 30:
    return &g_ksLevelLimits[8];
  case 31:
    return &g_ksLevelLimits[9];
  case 32:
    return &g_ksLevelLimits[10];
  case 40:
    return &g_ksLevelLimits[11];
  case 41:
    return &g_ksLevelLimits[12];
  case 42:
    return &g_ksLevelLimits[13];
  case 50:
    return &g_ksLevelLimits[14];
  case 51:
    return &g_ksLevelLimits[15];
  case 52:
    return &g_ksLevelLimits[16];
  default:
    return NULL;
  }
  return NULL;
}

bool CheckSpsActive (PWelsDecoderContext pCtx, PSps pSps, bool bUseSubsetFlag) {
  for (int i = 0; i < MAX_LAYER_NUM; i++) {
    if (pCtx->sSpsPpsCtx.pActiveLayerSps[i] == pSps)
      return true;
  }
  // Pre-active, will be used soon
  if (bUseSubsetFlag) {
    if (pSps->iMbWidth > 0 && pSps->iMbHeight > 0 && pCtx->sSpsPpsCtx.bSubspsAvailFlags[pSps->iSpsId]) {
      if (pCtx->iTotalNumMbRec > 0) {
        return true;
      }
      if (pCtx->pAccessUnitList->uiAvailUnitsNum > 0) {
        int i = 0, iNum = (int32_t) pCtx->pAccessUnitList->uiAvailUnitsNum;
        while (i < iNum) {
          PNalUnit pNalUnit = pCtx->pAccessUnitList->pNalUnitsList[i];
          if (pNalUnit->sNalData.sVclNal.bSliceHeaderExtFlag) { //ext data
            PSps pNextUsedSps = pNalUnit->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader.pSps;
            if (pNextUsedSps->iSpsId == pSps->iSpsId)
              return true;
          }
          ++i;
        }
      }
    }
  } else {
    if (pSps->iMbWidth > 0 && pSps->iMbHeight > 0 && pCtx->sSpsPpsCtx.bSpsAvailFlags[pSps->iSpsId]) {
      if (pCtx->iTotalNumMbRec > 0) {
        return true;
      }
      if (pCtx->pAccessUnitList->uiAvailUnitsNum > 0) {
        int i = 0, iNum = (int32_t) pCtx->pAccessUnitList->uiAvailUnitsNum;
        while (i < iNum) {
          PNalUnit pNalUnit = pCtx->pAccessUnitList->pNalUnitsList[i];
          if (!pNalUnit->sNalData.sVclNal.bSliceHeaderExtFlag) { //non-ext data
            PSps pNextUsedSps = pNalUnit->sNalData.sVclNal.sSliceHeaderExt.sSliceHeader.pSps;
            if (pNextUsedSps->iSpsId == pSps->iSpsId)
              return true;
          }
          ++i;
        }
      }
    }
  }
  return false;
}

#define  SPS_LOG2_MAX_FRAME_NUM_MINUS4_MAX 12
#define  SPS_LOG2_MAX_PIC_ORDER_CNT_LSB_MINUS4_MAX 12
#define  SPS_NUM_REF_FRAMES_IN_PIC_ORDER_CNT_CYCLE_MAX 255
#define  SPS_MAX_NUM_REF_FRAMES_MAX 16
#define  PPS_PIC_INIT_QP_QS_MIN 0
#define  PPS_PIC_INIT_QP_QS_MAX 51
#define  PPS_CHROMA_QP_INDEX_OFFSET_MIN -12
#define  PPS_CHROMA_QP_INDEX_OFFSET_MAX 12
#define  SCALING_LIST_DELTA_SCALE_MAX 127
#define SCALING_LIST_DELTA_SCALE_MIN -128

/*!
 *************************************************************************************
 * \brief   to parse Sequence Parameter Set (SPS)
 *
 * \param   pCtx        Decoder context
 * \param   pBsAux      bitstream reader auxiliary
 * \param   pPicWidth   picture width current Sps represented
 * \param   pPicHeight  picture height current Sps represented
 *
 * \return  0 - successed
 *      1 - failed
 *
 * \note    Call it in case eNalUnitType is SPS.
 *************************************************************************************
 */

int32_t ParseSps (PWelsDecoderContext pCtx, PBitStringAux pBsAux, int32_t* pPicWidth, int32_t* pPicHeight,
                  uint8_t* pSrcNal, const int32_t kSrcNalLen) {
  PBitStringAux pBs = pBsAux;
  SSubsetSps sTempSubsetSps;
  PSps pSps = NULL;
  PSubsetSps pSubsetSps = NULL;
  SNalUnitHeader* pNalHead = &pCtx->sCurNalHead;
  ProfileIdc uiProfileIdc;
  uint8_t uiLevelIdc;
  int32_t iSpsId;
  uint32_t uiCode;
  int32_t iCode;
  int32_t iRet = ERR_NONE;
  bool bConstraintSetFlags[6] = { false };
  const bool kbUseSubsetFlag   = IS_SUBSET_SPS_NAL (pNalHead->eNalUnitType);

  WELS_READ_VERIFY (BsGetBits (pBs, 8, &uiCode)); //profile_idc
  uiProfileIdc = uiCode;
  if (uiProfileIdc != PRO_BASELINE && uiProfileIdc != PRO_MAIN && uiProfileIdc != PRO_SCALABLE_BASELINE
      && uiProfileIdc != PRO_SCALABLE_HIGH
      && uiProfileIdc != PRO_EXTENDED && uiProfileIdc != PRO_HIGH) {
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, "SPS ID can not be supported!\n");
    return false;
  }
  WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //constraint_set0_flag
  bConstraintSetFlags[0] = !!uiCode;
  WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //constraint_set1_flag
  bConstraintSetFlags[1] = !!uiCode;
  WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //constraint_set2_flag
  bConstraintSetFlags[2] = !!uiCode;
  WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //constraint_set3_flag
  bConstraintSetFlags[3] = !!uiCode;
  WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //constraint_set4_flag
  bConstraintSetFlags[4] = !!uiCode;
  WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //constraint_set5_flag
  bConstraintSetFlags[5] = !!uiCode;
  WELS_READ_VERIFY (BsGetBits (pBs, 2, &uiCode)); // reserved_zero_2bits, equal to 0
  WELS_READ_VERIFY (BsGetBits (pBs, 8, &uiCode)); // level_idc
  uiLevelIdc = uiCode;
  WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //seq_parameter_set_id
  if (uiCode >= MAX_SPS_COUNT) { // Modified to check invalid negative iSpsId, 12/1/2009
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, " iSpsId is out of range! \n");
    return GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_SPS_ID_OVERFLOW);
  }
  iSpsId = uiCode;
  pSubsetSps = &sTempSubsetSps;
  pSps = &sTempSubsetSps.sSps;
  memset (pSubsetSps, 0, sizeof (SSubsetSps));
  // Use the level 5.2 for compatibility
  const SLevelLimits* pSMaxLevelLimits = GetLevelLimits (52, false);
  const SLevelLimits* pSLevelLimits = GetLevelLimits (uiLevelIdc, bConstraintSetFlags[3]);
  if (NULL == pSLevelLimits) {
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, "ParseSps(): level_idx (%d).\n", uiLevelIdc);
    return GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_UNSUPPORTED_NON_BASELINE);
  } else pSps->pSLevelLimits = pSLevelLimits;
  // syntax elements in default
  pSps->uiChromaFormatIdc = 1;
  pSps->uiChromaArrayType = 1;

  pSps->uiProfileIdc = uiProfileIdc;
  pSps->uiLevelIdc   = uiLevelIdc;
  pSps->iSpsId       = iSpsId;

  if (PRO_SCALABLE_BASELINE == uiProfileIdc || PRO_SCALABLE_HIGH == uiProfileIdc ||
      PRO_HIGH == uiProfileIdc || PRO_HIGH10 == uiProfileIdc ||
      PRO_HIGH422 == uiProfileIdc || PRO_HIGH444 == uiProfileIdc ||
      PRO_CAVLC444 == uiProfileIdc || 44 == uiProfileIdc) {

    WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //chroma_format_idc
    pSps->uiChromaFormatIdc = uiCode;
//    if (pSps->uiChromaFormatIdc != 1) {
//      WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, "ParseSps(): chroma_format_idc (%d) = 1 supported.",
//               pSps->uiChromaFormatIdc);
//      return GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_UNSUPPORTED_NON_BASELINE);
//    }
    if (pSps->uiChromaFormatIdc > 1) {
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, "ParseSps(): chroma_format_idc (%d) <=1 supported.",
               pSps->uiChromaFormatIdc);
      return GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_UNSUPPORTED_NON_BASELINE);

    }// To support 4:0:0; 4:2:0
    pSps->uiChromaArrayType = pSps->uiChromaFormatIdc;
    WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //bit_depth_luma_minus8
    if (uiCode != 0) {
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, "ParseSps(): bit_depth_luma (%d) Only 8 bit supported.", 8 + uiCode);
      return GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_UNSUPPORTED_NON_BASELINE);
    }
    pSps->uiBitDepthLuma = 8;

    WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //bit_depth_chroma_minus8
    if (uiCode != 0) {
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, "ParseSps(): bit_depth_chroma (%d). Only 8 bit supported.", 8 + uiCode);
      return GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_UNSUPPORTED_NON_BASELINE);
    }
    pSps->uiBitDepthChroma = 8;

    WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //qpprime_y_zero_transform_bypass_flag
    pSps->bQpPrimeYZeroTransfBypassFlag = !!uiCode;
    WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //seq_scaling_matrix_present_flag
    pSps->bSeqScalingMatrixPresentFlag = !!uiCode;

    if (pSps->bSeqScalingMatrixPresentFlag) {
      WELS_READ_VERIFY (ParseScalingList (pSps, pBs, 0, 0, pSps->bSeqScalingListPresentFlag, pSps->iScalingList4x4,
                                          pSps->iScalingList8x8));
    }
  }
  WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //log2_max_frame_num_minus4
  WELS_CHECK_SE_UPPER_ERROR (uiCode, SPS_LOG2_MAX_FRAME_NUM_MINUS4_MAX, "log2_max_frame_num_minus4",
                             GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_INVALID_LOG2_MAX_FRAME_NUM_MINUS4));
  pSps->uiLog2MaxFrameNum = LOG2_MAX_FRAME_NUM_OFFSET + uiCode;
  WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //pic_order_cnt_type
  pSps->uiPocType = uiCode;

  if (0 == pSps->uiPocType) {
    WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //log2_max_pic_order_cnt_lsb_minus4
    // log2_max_pic_order_cnt_lsb_minus4 should be in range 0 to 12, inclusive. (sec. 7.4.3)
    WELS_CHECK_SE_UPPER_ERROR (uiCode, SPS_LOG2_MAX_PIC_ORDER_CNT_LSB_MINUS4_MAX, "log2_max_pic_order_cnt_lsb_minus4",
                               GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_INVALID_LOG2_MAX_PIC_ORDER_CNT_LSB_MINUS4));
    pSps->iLog2MaxPocLsb = LOG2_MAX_PIC_ORDER_CNT_LSB_OFFSET + uiCode; // log2_max_pic_order_cnt_lsb_minus4

  } else if (1 == pSps->uiPocType) {
    int32_t i;
    WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //delta_pic_order_always_zero_flag
    pSps->bDeltaPicOrderAlwaysZeroFlag = !!uiCode;
    WELS_READ_VERIFY (BsGetSe (pBs, &iCode)); //offset_for_non_ref_pic
    pSps->iOffsetForNonRefPic = iCode;
    WELS_READ_VERIFY (BsGetSe (pBs, &iCode)); //offset_for_top_to_bottom_field
    pSps->iOffsetForTopToBottomField = iCode;
    WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //num_ref_frames_in_pic_order_cnt_cycle
    WELS_CHECK_SE_UPPER_ERROR (uiCode, SPS_NUM_REF_FRAMES_IN_PIC_ORDER_CNT_CYCLE_MAX,
                               "num_ref_frames_in_pic_order_cnt_cycle", GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS,
                                   ERR_INFO_INVALID_NUM_REF_FRAME_IN_PIC_ORDER_CNT_CYCLE));
    pSps->iNumRefFramesInPocCycle = uiCode;
    for (i = 0; i < pSps->iNumRefFramesInPocCycle; i++) {
      WELS_READ_VERIFY (BsGetSe (pBs, &iCode)); //offset_for_ref_frame[ i ]
      pSps->iOffsetForRefFrame[ i ] = iCode;
    }
  }
  if (pSps->uiPocType > 2) {
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, " illegal pic_order_cnt_type: %d ! ", pSps->uiPocType);
    return GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_INVALID_POC_TYPE);
  }

  WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //max_num_ref_frames
  pSps->iNumRefFrames = uiCode;
  WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //gaps_in_frame_num_value_allowed_flag
  pSps->bGapsInFrameNumValueAllowedFlag = !!uiCode;
  WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //pic_width_in_mbs_minus1
  pSps->iMbWidth = PIC_WIDTH_IN_MBS_OFFSET + uiCode;
  if (pSps->iMbWidth > MAX_MB_SIZE || pSps->iMbWidth == 0) {
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_ERROR, "pic_width_in_mbs(%d) invalid!", pSps->iMbWidth);
    return  GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_INVALID_MAX_MB_SIZE);
  }
  if (((uint64_t)pSps->iMbWidth * (uint64_t)pSps->iMbWidth) > (uint64_t) (8 * pSLevelLimits->uiMaxFS)) {
    if (((uint64_t)pSps->iMbWidth * (uint64_t)pSps->iMbWidth) > (uint64_t) (8 * pSMaxLevelLimits->uiMaxFS)) {
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_ERROR, "the pic_width_in_mbs exceeds the level limits!");
      return GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_INVALID_MAX_MB_SIZE);
    } else {
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, "the pic_width_in_mbs exceeds the level limits!");
    }
  }
  WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //pic_height_in_map_units_minus1
  pSps->iMbHeight = PIC_HEIGHT_IN_MAP_UNITS_OFFSET + uiCode;
  if (pSps->iMbHeight > MAX_MB_SIZE || pSps->iMbHeight == 0) {
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_ERROR, "pic_height_in_mbs(%d) invalid!", pSps->iMbHeight);
    return  GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_INVALID_MAX_MB_SIZE);
  }
  if (((uint64_t)pSps->iMbHeight * (uint64_t)pSps->iMbHeight) > (uint64_t) (8 * pSLevelLimits->uiMaxFS)) {
    if (((uint64_t)pSps->iMbHeight * (uint64_t)pSps->iMbHeight) > (uint64_t) (8 * pSMaxLevelLimits->uiMaxFS)) {
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_ERROR, "the pic_height_in_mbs exceeds the level limits!");
      return GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_INVALID_MAX_MB_SIZE);
    } else {
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, "the pic_height_in_mbs exceeds the level limits!");
    }
  }
  uint64_t uiTmp64 = (uint64_t)pSps->iMbWidth * (uint64_t)pSps->iMbHeight;
  if (uiTmp64 > (uint64_t)pSLevelLimits->uiMaxFS) {
    if (uiTmp64 > (uint64_t)pSMaxLevelLimits->uiMaxFS) {
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_ERROR, "the total count of mb exceeds the level limits!");
      return GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_INVALID_MAX_MB_SIZE);
    } else {
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, "the total count of mb exceeds the level limits!");
    }
  }
  pSps->uiTotalMbCount = (uint32_t)uiTmp64;
  WELS_CHECK_SE_UPPER_ERROR (pSps->iNumRefFrames, SPS_MAX_NUM_REF_FRAMES_MAX, "max_num_ref_frames",
                             GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_INVALID_MAX_NUM_REF_FRAMES));
  // here we check max_num_ref_frames
  uint32_t uiMaxDpbMbs = pSLevelLimits->uiMaxDPBMbs;
  uint32_t uiMaxDpbFrames = uiMaxDpbMbs / pSps->uiTotalMbCount;
  if (uiMaxDpbFrames > SPS_MAX_NUM_REF_FRAMES_MAX)
    uiMaxDpbFrames = SPS_MAX_NUM_REF_FRAMES_MAX;
  if ((uint32_t)pSps->iNumRefFrames > uiMaxDpbFrames) {
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, " max_num_ref_frames exceeds level limits!");
  }
  WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //frame_mbs_only_flag
  pSps->bFrameMbsOnlyFlag = !!uiCode;
  if (!pSps->bFrameMbsOnlyFlag) {
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, "ParseSps(): frame_mbs_only_flag (%d) not supported.",
             pSps->bFrameMbsOnlyFlag);
    return GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_UNSUPPORTED_MBAFF);
  }
  WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //direct_8x8_inference_flag
  pSps->bDirect8x8InferenceFlag = !!uiCode;
  WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //frame_cropping_flag
  pSps->bFrameCroppingFlag = !!uiCode;
  if (pSps->bFrameCroppingFlag) {
    WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //frame_crop_left_offset
    pSps->sFrameCrop.iLeftOffset = uiCode;
    WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //frame_crop_right_offset
    pSps->sFrameCrop.iRightOffset = uiCode;
    if ((pSps->sFrameCrop.iLeftOffset + pSps->sFrameCrop.iRightOffset) > ((int32_t)pSps->iMbWidth * 16 / 2)) {
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_ERROR, "frame_crop_left_offset + frame_crop_right_offset exceeds limits!");
      return GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_INVALID_CROPPING_DATA);
    }
    WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //frame_crop_top_offset
    pSps->sFrameCrop.iTopOffset = uiCode;
    WELS_READ_VERIFY (BsGetUe (pBs, &uiCode)); //frame_crop_bottom_offset
    pSps->sFrameCrop.iBottomOffset = uiCode;
    if ((pSps->sFrameCrop.iTopOffset + pSps->sFrameCrop.iBottomOffset) > ((int32_t)pSps->iMbHeight * 16 / 2)) {
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_ERROR, "frame_crop_top_offset + frame_crop_right_offset exceeds limits!");
      return GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_INVALID_CROPPING_DATA);
    }
  } else {
    pSps->sFrameCrop.iLeftOffset   = 0; // frame_crop_left_offset
    pSps->sFrameCrop.iRightOffset  = 0; // frame_crop_right_offset
    pSps->sFrameCrop.iTopOffset    = 0; // frame_crop_top_offset
    pSps->sFrameCrop.iBottomOffset = 0; // frame_crop_bottom_offset
  }
  WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //vui_parameters_present_flag
  pSps->bVuiParamPresentFlag = !!uiCode;
  if (pSps->bVuiParamPresentFlag) {
    int iRetVui = ParseVui (pCtx, pSps, pBsAux);
    if (iRetVui == GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_UNSUPPORTED_VUI_HRD)) {
      if (kbUseSubsetFlag) { //Currently do no support VUI with HRD enable in subsetSPS
        WelsLog (& (pCtx->sLogCtx), WELS_LOG_ERROR, "hrd parse in vui of subsetSPS is not supported!");
        return iRetVui;
      }
    } else {
      WELS_READ_VERIFY (iRetVui);
    }
  }

  if (pCtx->pParam->bParseOnly) {
    if (kSrcNalLen >= SPS_PPS_BS_SIZE - 4) { //sps bs exceeds!
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, "sps payload size (%d) too large for parse only (%d), not supported!",
               kSrcNalLen, SPS_PPS_BS_SIZE - 4);
      pCtx->iErrorCode |= dsBitstreamError;
      return GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_OUT_OF_MEMORY);
    }
    if (!kbUseSubsetFlag) { //SPS
      SSpsBsInfo* pSpsBs = &pCtx->sSpsBsInfo [iSpsId];
      pSpsBs->iSpsId = iSpsId;
      int32_t iTrailingZeroByte = 0;
      while (pSrcNal[kSrcNalLen - iTrailingZeroByte - 1] == 0x0) //remove final trailing 0 bytes
        iTrailingZeroByte++;
      int32_t iActualLen = kSrcNalLen - iTrailingZeroByte;
      pSpsBs->uiSpsBsLen = (uint16_t) iActualLen;
      //unify start code as 0x0001
      int32_t iStartDeltaByte = 0; //0 for 0x0001, 1 for 0x001
      if (pSrcNal[0] == 0x0 && pSrcNal[1] == 0x0 && pSrcNal[2] == 0x1) { //if 0x001
        pSpsBs->pSpsBsBuf[0] = 0x0; //add 0 to form 0x0001
        iStartDeltaByte++;
        pSpsBs->uiSpsBsLen++;
      }
      memcpy (pSpsBs->pSpsBsBuf + iStartDeltaByte, pSrcNal, iActualLen);
    } else { //subset SPS
      SSpsBsInfo* pSpsBs = &pCtx->sSubsetSpsBsInfo [iSpsId];
      pSpsBs->iSpsId = iSpsId;
      pSpsBs->pSpsBsBuf [0] = pSpsBs->pSpsBsBuf [1] = pSpsBs->pSpsBsBuf [2] = 0x00;
      pSpsBs->pSpsBsBuf [3] = 0x01;
      pSpsBs->pSpsBsBuf [4] = 0x67;

      //re-write subset SPS to SPS
      SBitStringAux sSubsetSpsBs;
      CMemoryAlign* pMa = pCtx->pMemAlign;

      uint8_t* pBsBuf = static_cast<uint8_t*> (pMa->WelsMallocz (SPS_PPS_BS_SIZE + 4,
                        "Temp buffer for parse only usage.")); //to reserve 4 bytes for UVLC writing buffer
      if (NULL == pBsBuf) {
        WelsLog (& (pCtx->sLogCtx), WELS_LOG_ERROR, "sps buffer alloc failed for parse only!");
        pCtx->iErrorCode |= dsOutOfMemory;
        return pCtx->iErrorCode;
      }
      InitBits (&sSubsetSpsBs, pBsBuf, (int32_t) (pBs->pEndBuf - pBs->pStartBuf));
      BsWriteBits (&sSubsetSpsBs, 8, 77); //profile_idc, forced to Main profile
      BsWriteOneBit (&sSubsetSpsBs, pSps->bConstraintSet0Flag); // constraint_set0_flag
      BsWriteOneBit (&sSubsetSpsBs, pSps->bConstraintSet1Flag); // constraint_set1_flag
      BsWriteOneBit (&sSubsetSpsBs, pSps->bConstraintSet2Flag); // constraint_set2_flag
      BsWriteOneBit (&sSubsetSpsBs, pSps->bConstraintSet3Flag); // constraint_set3_flag
      BsWriteBits (&sSubsetSpsBs, 4, 0); //constraint_set4_flag, constraint_set5_flag, reserved_zero_2bits
      BsWriteBits (&sSubsetSpsBs, 8, pSps->uiLevelIdc); //level_idc
      BsWriteUE (&sSubsetSpsBs, pSps->iSpsId); //sps_id
      BsWriteUE (&sSubsetSpsBs, pSps->uiLog2MaxFrameNum - 4); //log2_max_frame_num_minus4
      BsWriteUE (&sSubsetSpsBs, pSps->uiPocType); //pic_order_cnt_type
      if (pSps->uiPocType == 0) {
        BsWriteUE (&sSubsetSpsBs, pSps->iLog2MaxPocLsb - 4); //log2_max_pic_order_cnt_lsb_minus4
      } else if (pSps->uiPocType == 1) {
        BsWriteOneBit (&sSubsetSpsBs, pSps->bDeltaPicOrderAlwaysZeroFlag); //delta_pic_order_always_zero_flag
        BsWriteSE (&sSubsetSpsBs, pSps->iOffsetForNonRefPic); //offset_for_no_ref_pic
        BsWriteSE (&sSubsetSpsBs, pSps->iOffsetForTopToBottomField); //offset_for_top_to_bottom_field
        BsWriteUE (&sSubsetSpsBs, pSps->iNumRefFramesInPocCycle); //num_ref_frames_in_pic_order_cnt_cycle
        for (int32_t i = 0; i < pSps->iNumRefFramesInPocCycle; ++i) {
          BsWriteSE (&sSubsetSpsBs, pSps->iOffsetForRefFrame[i]); //offset_for_ref_frame[i]
        }
      }
      BsWriteUE (&sSubsetSpsBs, pSps->iNumRefFrames); //max_num_ref_frames
      BsWriteOneBit (&sSubsetSpsBs, pSps->bGapsInFrameNumValueAllowedFlag); //gaps_in_frame_num_value_allowed_flag
      BsWriteUE (&sSubsetSpsBs, pSps->iMbWidth - 1); //pic_width_in_mbs_minus1
      BsWriteUE (&sSubsetSpsBs, pSps->iMbHeight - 1); //pic_height_in_map_units_minus1
      BsWriteOneBit (&sSubsetSpsBs, pSps->bFrameMbsOnlyFlag); //frame_mbs_only_flag
      if (!pSps->bFrameMbsOnlyFlag) {
        BsWriteOneBit (&sSubsetSpsBs, pSps->bMbaffFlag); //mb_adaptive_frame_field_flag
      }
      BsWriteOneBit (&sSubsetSpsBs, pSps->bDirect8x8InferenceFlag); //direct_8x8_inference_flag
      BsWriteOneBit (&sSubsetSpsBs, pSps->bFrameCroppingFlag); //frame_cropping_flag
      if (pSps->bFrameCroppingFlag) {
        BsWriteUE (&sSubsetSpsBs, pSps->sFrameCrop.iLeftOffset); //frame_crop_left_offset
        BsWriteUE (&sSubsetSpsBs, pSps->sFrameCrop.iRightOffset); //frame_crop_right_offset
        BsWriteUE (&sSubsetSpsBs, pSps->sFrameCrop.iTopOffset); //frame_crop_top_offset
        BsWriteUE (&sSubsetSpsBs, pSps->sFrameCrop.iBottomOffset); //frame_crop_bottom_offset
      }
      BsWriteOneBit (&sSubsetSpsBs, 0); //vui_parameters_present_flag
      BsRbspTrailingBits (&sSubsetSpsBs); //finished, rbsp trailing bit
      int32_t iRbspSize = (int32_t) (sSubsetSpsBs.pCurBuf - sSubsetSpsBs.pStartBuf);
      RBSP2EBSP (pSpsBs->pSpsBsBuf + 5, sSubsetSpsBs.pStartBuf, iRbspSize);
      pSpsBs->uiSpsBsLen = (uint16_t) (sSubsetSpsBs.pCurBuf - sSubsetSpsBs.pStartBuf + 5);
      if (pBsBuf) {
        pMa->WelsFree (pBsBuf, "pBsBuf for parse only usage");
      }
    }
  }
  // Check if SPS SVC extension applicated
  if (kbUseSubsetFlag && (PRO_SCALABLE_BASELINE == uiProfileIdc || PRO_SCALABLE_HIGH == uiProfileIdc)) {
    if ((iRet = DecodeSpsSvcExt (pCtx, pSubsetSps, pBs)) != ERR_NONE) {
      return iRet;
    }

    WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode)); //svc_vui_parameters_present_flag
    pSubsetSps->bSvcVuiParamPresentFlag = !!uiCode;
    if (pSubsetSps->bSvcVuiParamPresentFlag) {
    }
  }


  if (PRO_SCALABLE_BASELINE == uiProfileIdc || PRO_SCALABLE_HIGH == uiProfileIdc)
    pCtx->sSpsPpsCtx.bAvcBasedFlag = false;

  *pPicWidth  = pSps->iMbWidth << 4;
  *pPicHeight = pSps->iMbHeight << 4;
  PSps pTmpSps = NULL;
  if (kbUseSubsetFlag) {
    pTmpSps = &pCtx->sSpsPpsCtx.sSubsetSpsBuffer[iSpsId].sSps;
  } else {
    pTmpSps = &pCtx->sSpsPpsCtx.sSpsBuffer[iSpsId];
  }
  if (CheckSpsActive (pCtx, pTmpSps, kbUseSubsetFlag)) {
    // we are overwriting the active sps, copy a temp buffer
    if (kbUseSubsetFlag) {
      if (memcmp (&pCtx->sSpsPpsCtx.sSubsetSpsBuffer[iSpsId], pSubsetSps, sizeof (SSubsetSps)) != 0) {
        if (pCtx->pAccessUnitList->uiAvailUnitsNum > 0) {
          memcpy (&pCtx->sSpsPpsCtx.sSubsetSpsBuffer[MAX_SPS_COUNT], pSubsetSps, sizeof (SSubsetSps));
          pCtx->bAuReadyFlag = true;
          pCtx->pAccessUnitList->uiEndPos = pCtx->pAccessUnitList->uiAvailUnitsNum - 1;
          pCtx->sSpsPpsCtx.iOverwriteFlags |= OVERWRITE_SUBSETSPS;
        } else if ((pCtx->pSps != NULL) && (pCtx->pSps->iSpsId == pSubsetSps->sSps.iSpsId)) {
          memcpy (&pCtx->sSpsPpsCtx.sSubsetSpsBuffer[MAX_SPS_COUNT], pSubsetSps, sizeof (SSubsetSps));
          pCtx->sSpsPpsCtx.iOverwriteFlags |= OVERWRITE_SUBSETSPS;
        } else {
          memcpy (&pCtx->sSpsPpsCtx.sSubsetSpsBuffer[iSpsId], pSubsetSps, sizeof (SSubsetSps));
        }
      }
    } else {
      if (memcmp (&pCtx->sSpsPpsCtx.sSpsBuffer[iSpsId], pSps, sizeof (SSps)) != 0) {
        if (pCtx->pAccessUnitList->uiAvailUnitsNum > 0) {
          memcpy (&pCtx->sSpsPpsCtx.sSpsBuffer[MAX_SPS_COUNT], pSps, sizeof (SSps));
          pCtx->sSpsPpsCtx.iOverwriteFlags |= OVERWRITE_SPS;
          pCtx->bAuReadyFlag = true;
          pCtx->pAccessUnitList->uiEndPos = pCtx->pAccessUnitList->uiAvailUnitsNum - 1;
        } else if ((pCtx->pSps != NULL) && (pCtx->pSps->iSpsId == pSps->iSpsId)) {
          memcpy (&pCtx->sSpsPpsCtx.sSpsBuffer[MAX_SPS_COUNT], pSps, sizeof (SSps));
          pCtx->sSpsPpsCtx.iOverwriteFlags |= OVERWRITE_SPS;
        } else {
          memcpy (&pCtx->sSpsPpsCtx.sSpsBuffer[iSpsId], pSps, sizeof (SSps));
        }
      }
    }
  }
  // Not overwrite active sps, just copy to final place
  else if (kbUseSubsetFlag) {
    memcpy (&pCtx->sSpsPpsCtx.sSubsetSpsBuffer[iSpsId], pSubsetSps, sizeof (SSubsetSps));
    pCtx->sSpsPpsCtx.bSubspsAvailFlags[iSpsId] = true;
    pCtx->sSpsPpsCtx.bSubspsExistAheadFlag = true;
  } else {
    memcpy (&pCtx->sSpsPpsCtx.sSpsBuffer[iSpsId], pSps, sizeof (SSps));
    pCtx->sSpsPpsCtx.bSpsAvailFlags[iSpsId] = true;
    pCtx->sSpsPpsCtx.bSpsExistAheadFlag = true;
  }
  return ERR_NONE;
}

/*!
 *************************************************************************************
 * \brief   to parse Picture Parameter Set (PPS)
 *
 * \param   pCtx        Decoder context
 * \param   pPpsList    pps list
 * \param   pBsAux      bitstream reader auxiliary
 *
 * \return  0 - successed
 *          1 - failed
 *
 * \note    Call it in case eNalUnitType is PPS.
 *************************************************************************************
 */
int32_t ParsePps (PWelsDecoderContext pCtx, PPps pPpsList, PBitStringAux pBsAux, uint8_t* pSrcNal,
                  const int32_t kSrcNalLen) {

  PPps pPps = NULL;
  SPps sTempPps;
  uint32_t uiPpsId = 0;
  uint32_t iTmp;
  uint32_t uiCode;
  int32_t iCode;

  WELS_READ_VERIFY (BsGetUe (pBsAux, &uiCode)); //pic_parameter_set_id
  uiPpsId = uiCode;
  if (uiPpsId >= MAX_PPS_COUNT) {
    return GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_PPS_ID_OVERFLOW);
  }
  pPps = &sTempPps;
  memset (pPps, 0, sizeof (SPps));

  pPps->iPpsId = uiPpsId;
  WELS_READ_VERIFY (BsGetUe (pBsAux, &uiCode)); //seq_parameter_set_id
  pPps->iSpsId = uiCode;

  if (pPps->iSpsId >= MAX_SPS_COUNT) {
    return GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_SPS_ID_OVERFLOW);
  }

  WELS_READ_VERIFY (BsGetOneBit (pBsAux, &uiCode)); //entropy_coding_mode_flag
  pPps->bEntropyCodingModeFlag = !!uiCode;
  WELS_READ_VERIFY (BsGetOneBit (pBsAux, &uiCode)); //bottom_field_pic_order_in_frame_present_flag
  pPps->bPicOrderPresentFlag   = !!uiCode;

  WELS_READ_VERIFY (BsGetUe (pBsAux, &uiCode)); //num_slice_groups_minus1
  pPps->uiNumSliceGroups = NUM_SLICE_GROUPS_OFFSET + uiCode;

  if (pPps->uiNumSliceGroups > MAX_SLICEGROUP_IDS) {
    return GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_INVALID_SLICEGROUP);
  }

  if (pPps->uiNumSliceGroups > 1) {
    WELS_READ_VERIFY (BsGetUe (pBsAux, &uiCode)); //slice_group_map_type
    pPps->uiSliceGroupMapType = uiCode;
    if (pPps->uiSliceGroupMapType > 1) {
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, "ParsePps(): slice_group_map_type (%d): support only 0,1.",
               pPps->uiSliceGroupMapType);
      return GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_UNSUPPORTED_FMOTYPE);
    }

    switch (pPps->uiSliceGroupMapType) {
    case 0:
      for (iTmp = 0; iTmp < pPps->uiNumSliceGroups; iTmp++) {
        WELS_READ_VERIFY (BsGetUe (pBsAux, &uiCode)); //run_length_minus1[ iGroup ]
        pPps->uiRunLength[iTmp] = RUN_LENGTH_OFFSET + uiCode;
      }
      break;
    default:
      break;
    }
  }

  WELS_READ_VERIFY (BsGetUe (pBsAux, &uiCode)); //num_ref_idx_l0_default_active_minus1
  pPps->uiNumRefIdxL0Active = NUM_REF_IDX_L0_DEFAULT_ACTIVE_OFFSET + uiCode;
  WELS_READ_VERIFY (BsGetUe (pBsAux, &uiCode)); //num_ref_idx_l1_default_active_minus1
  pPps->uiNumRefIdxL1Active = NUM_REF_IDX_L1_DEFAULT_ACTIVE_OFFSET + uiCode;

  if (pPps->uiNumRefIdxL0Active > MAX_REF_PIC_COUNT ||
      pPps->uiNumRefIdxL1Active > MAX_REF_PIC_COUNT) {
    return GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_REF_COUNT_OVERFLOW);
  }

  WELS_READ_VERIFY (BsGetOneBit (pBsAux, &uiCode)); //weighted_pred_flag
  pPps->bWeightedPredFlag  = !!uiCode;
  WELS_READ_VERIFY (BsGetBits (pBsAux, 2, &uiCode)); //weighted_bipred_idc
  pPps->uiWeightedBipredIdc = uiCode;
  // weighted_bipred_idc > 0 NOT supported now, but no impact when we ignore it

  WELS_READ_VERIFY (BsGetSe (pBsAux, &iCode)); //pic_init_qp_minus26
  pPps->iPicInitQp = PIC_INIT_QP_OFFSET + iCode;
  WELS_CHECK_SE_BOTH_ERROR (pPps->iPicInitQp, PPS_PIC_INIT_QP_QS_MIN, PPS_PIC_INIT_QP_QS_MAX, "pic_init_qp_minus26 + 26",
                            GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_INVALID_PIC_INIT_QP));
  WELS_READ_VERIFY (BsGetSe (pBsAux, &iCode)); //pic_init_qs_minus26
  pPps->iPicInitQs = PIC_INIT_QS_OFFSET + iCode;
  WELS_CHECK_SE_BOTH_ERROR (pPps->iPicInitQs, PPS_PIC_INIT_QP_QS_MIN, PPS_PIC_INIT_QP_QS_MAX, "pic_init_qs_minus26 + 26",
                            GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_INVALID_PIC_INIT_QS));
  WELS_READ_VERIFY (BsGetSe (pBsAux, &iCode)); //chroma_qp_index_offset,cb
  pPps->iChromaQpIndexOffset[0]                  = iCode;
  WELS_CHECK_SE_BOTH_ERROR (pPps->iChromaQpIndexOffset[0], PPS_CHROMA_QP_INDEX_OFFSET_MIN, PPS_CHROMA_QP_INDEX_OFFSET_MAX,
                            "chroma_qp_index_offset", GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_INVALID_CHROMA_QP_INDEX_OFFSET));
  pPps->iChromaQpIndexOffset[1] = pPps->iChromaQpIndexOffset[0];//init cr qp offset
  WELS_READ_VERIFY (BsGetOneBit (pBsAux, &uiCode)); //deblocking_filter_control_present_flag
  pPps->bDeblockingFilterControlPresentFlag   = !!uiCode;
  WELS_READ_VERIFY (BsGetOneBit (pBsAux, &uiCode)); //constrained_intra_pred_flag
  pPps->bConstainedIntraPredFlag              = !!uiCode;
  WELS_READ_VERIFY (BsGetOneBit (pBsAux, &uiCode)); //redundant_pic_cnt_present_flag
  pPps->bRedundantPicCntPresentFlag           = !!uiCode;

  if (CheckMoreRBSPData (pBsAux)) {
    WELS_READ_VERIFY (BsGetOneBit (pBsAux, &uiCode)); //transform_8x8_mode_flag
    pPps->bTransform8x8ModeFlag = !!uiCode;
    WELS_READ_VERIFY (BsGetOneBit (pBsAux, &uiCode)); //pic_scaling_matrix_present_flag
    pPps->bPicScalingMatrixPresentFlag = !!uiCode;
    if (pPps->bPicScalingMatrixPresentFlag) {
      if (pCtx->sSpsPpsCtx.bSpsAvailFlags[pPps->iSpsId]) {
        WELS_READ_VERIFY (ParseScalingList (&pCtx->sSpsPpsCtx.sSpsBuffer[pPps->iSpsId], pBsAux, 1, pPps->bTransform8x8ModeFlag,
                                            pPps->bPicScalingListPresentFlag, pPps->iScalingList4x4, pPps->iScalingList8x8));
      } else {
        WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING,
                 "ParsePps(): sps_id (%d) does not exist for scaling_list. This PPS (%d) is marked as invalid.", pPps->iSpsId,
                 pPps->iPpsId);
        return GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_INVALID_SPS_ID);
      }
    }
    WELS_READ_VERIFY (BsGetSe (pBsAux, &iCode)); //second_chroma_qp_index_offset
    pPps->iChromaQpIndexOffset[1] = iCode;
    WELS_CHECK_SE_BOTH_ERROR (pPps->iChromaQpIndexOffset[1], PPS_CHROMA_QP_INDEX_OFFSET_MIN,
                              PPS_CHROMA_QP_INDEX_OFFSET_MAX, "chroma_qp_index_offset", GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS,
                                  ERR_INFO_INVALID_CHROMA_QP_INDEX_OFFSET));
  }

  if (pCtx->pPps != NULL && pCtx->pPps->iPpsId == pPps->iPpsId) {
    if (memcmp (pCtx->pPps, pPps, sizeof (*pPps)) != 0) {
      memcpy (&pCtx->sSpsPpsCtx.sPpsBuffer[MAX_PPS_COUNT], pPps, sizeof (SPps));
      pCtx->sSpsPpsCtx.iOverwriteFlags |= OVERWRITE_PPS;
      if (pCtx->pAccessUnitList->uiAvailUnitsNum > 0) {
        pCtx->bAuReadyFlag = true;
        pCtx->pAccessUnitList->uiEndPos = pCtx->pAccessUnitList->uiAvailUnitsNum - 1;
      }
    }
  } else {
    memcpy (&pCtx->sSpsPpsCtx.sPpsBuffer[uiPpsId], pPps, sizeof (SPps));
    pCtx->sSpsPpsCtx.bPpsAvailFlags[uiPpsId] = true;
  }
  if (pCtx->pParam->bParseOnly) {
    if (kSrcNalLen >= SPS_PPS_BS_SIZE - 4) { //pps bs exceeds
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, "pps payload size (%d) too large for parse only (%d), not supported!",
               kSrcNalLen, SPS_PPS_BS_SIZE - 4);
      pCtx->iErrorCode |= dsBitstreamError;
      return GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_OUT_OF_MEMORY);
    }
    SPpsBsInfo* pPpsBs = &pCtx->sPpsBsInfo [uiPpsId];
    pPpsBs->iPpsId = (int32_t) uiPpsId;
    int32_t iTrailingZeroByte = 0;
    while (pSrcNal[kSrcNalLen - iTrailingZeroByte - 1] == 0x0) //remove final trailing 0 bytes
      iTrailingZeroByte++;
    int32_t iActualLen = kSrcNalLen - iTrailingZeroByte;
    pPpsBs->uiPpsBsLen = (uint16_t) iActualLen;
    //unify start code as 0x0001
    int32_t iStartDeltaByte = 0; //0 for 0x0001, 1 for 0x001
    if (pSrcNal[0] == 0x0 && pSrcNal[1] == 0x0 && pSrcNal[2] == 0x1) { //if 0x001
      pPpsBs->pPpsBsBuf[0] = 0x0; //add 0 to form 0x0001
      iStartDeltaByte++;
      pPpsBs->uiPpsBsLen++;
    }
    memcpy (pPpsBs->pPpsBsBuf + iStartDeltaByte, pSrcNal, iActualLen);
  }
  return ERR_NONE;
}

#define VUI_MAX_CHROMA_LOG_TYPE_TOP_BOTTOM_FIELD_MAX 5
#define VUI_NUM_UNITS_IN_TICK_MIN 1
#define VUI_TIME_SCALE_MIN 1
#define VUI_MAX_BYTES_PER_PIC_DENOM_MAX 16
#define VUI_MAX_BITS_PER_MB_DENOM_MAX 16
#define VUI_LOG2_MAX_MV_LENGTH_HOR_MAX 16
#define VUI_LOG2_MAX_MV_LENGTH_VER_MAX 16
#define VUI_MAX_DEC_FRAME_BUFFERING_MAX 16
int32_t ParseVui (PWelsDecoderContext pCtx, PSps pSps, PBitStringAux pBsAux) {
  uint32_t uiCode;
  PVui pVui = &pSps->sVui;
  WELS_READ_VERIFY (BsGetOneBit (pBsAux, &uiCode)); //aspect_ratio_info_present_flag
  pVui->bAspectRatioInfoPresentFlag = !!uiCode;
  if (pSps->sVui.bAspectRatioInfoPresentFlag) {
    WELS_READ_VERIFY (BsGetBits (pBsAux, 8, &uiCode)); //aspect_ratio_idc
    pVui->uiAspectRatioIdc = uiCode;
    if (pVui->uiAspectRatioIdc < 17) {
      pVui->uiSarWidth  = g_ksVuiSampleAspectRatio[pVui->uiAspectRatioIdc].uiWidth;
      pVui->uiSarHeight = g_ksVuiSampleAspectRatio[pVui->uiAspectRatioIdc].uiHeight;
    } else if (pVui->uiAspectRatioIdc == EXTENDED_SAR) {
      WELS_READ_VERIFY (BsGetBits (pBsAux, 16, &uiCode)); //sar_width
      pVui->uiSarWidth = uiCode;
      WELS_READ_VERIFY (BsGetBits (pBsAux, 16, &uiCode)); //sar_height
      pVui->uiSarHeight = uiCode;
    }
  }
  WELS_READ_VERIFY (BsGetOneBit (pBsAux, &uiCode)); //overscan_info_present_flag
  pVui->bOverscanInfoPresentFlag = !!uiCode;
  if (pVui->bOverscanInfoPresentFlag) {
    WELS_READ_VERIFY (BsGetOneBit (pBsAux, &uiCode)); //overscan_appropriate_flag
    pVui->bOverscanAppropriateFlag = !!uiCode;
  }
  WELS_READ_VERIFY (BsGetOneBit (pBsAux, &uiCode)); //video_signal_type_present_flag
  pVui->bVideoSignalTypePresentFlag = !!uiCode;
  if (pVui->bVideoSignalTypePresentFlag) {
    WELS_READ_VERIFY (BsGetBits (pBsAux, 3, &uiCode)); //video_format
    pVui->uiVideoFormat = uiCode;
    WELS_READ_VERIFY (BsGetOneBit (pBsAux, &uiCode)); //video_full_range_flag
    pVui->bVideoFullRangeFlag = !!uiCode;
    WELS_READ_VERIFY (BsGetOneBit (pBsAux, &uiCode)); //colour_description_present_flag
    pVui->bColourDescripPresentFlag = !!uiCode;
    if (pVui->bColourDescripPresentFlag) {
      WELS_READ_VERIFY (BsGetBits (pBsAux, 8, &uiCode)); //colour_primaries
      pVui->uiColourPrimaries = uiCode;
      WELS_READ_VERIFY (BsGetBits (pBsAux, 8, &uiCode)); //transfer_characteristics
      pVui->uiTransferCharacteristics = uiCode;
      WELS_READ_VERIFY (BsGetBits (pBsAux, 8, &uiCode)); //matrix_coefficients
      pVui->uiMatrixCoeffs = uiCode;
    }
  }
  WELS_READ_VERIFY (BsGetOneBit (pBsAux, &uiCode)); //chroma_loc_info_present_flag
  pVui->bChromaLocInfoPresentFlag = !!uiCode;
  if (pVui->bChromaLocInfoPresentFlag) {
    WELS_READ_VERIFY (BsGetUe (pBsAux, &uiCode)); //chroma_sample_loc_type_top_field
    pVui->uiChromaSampleLocTypeTopField = uiCode;
    WELS_CHECK_SE_UPPER_WARNING (pVui->uiChromaSampleLocTypeTopField, VUI_MAX_CHROMA_LOG_TYPE_TOP_BOTTOM_FIELD_MAX,
                                 "chroma_sample_loc_type_top_field");
    WELS_READ_VERIFY (BsGetUe (pBsAux, &uiCode)); //chroma_sample_loc_type_bottom_field
    pVui->uiChromaSampleLocTypeBottomField = uiCode;
    WELS_CHECK_SE_UPPER_WARNING (pVui->uiChromaSampleLocTypeBottomField, VUI_MAX_CHROMA_LOG_TYPE_TOP_BOTTOM_FIELD_MAX,
                                 "chroma_sample_loc_type_bottom_field");
  }
  WELS_READ_VERIFY (BsGetOneBit (pBsAux, &uiCode)); //timing_info_present_flag
  pVui->bTimingInfoPresentFlag = !!uiCode;
  if (pVui->bTimingInfoPresentFlag) {
    uint32_t uiTmp = 0;
    WELS_READ_VERIFY (BsGetBits (pBsAux, 16, &uiCode)); //num_units_in_tick
    uiTmp = (uiCode << 16);
    WELS_READ_VERIFY (BsGetBits (pBsAux, 16, &uiCode)); //num_units_in_tick
    uiTmp |= uiCode;
    pVui->uiNumUnitsInTick = uiTmp;
    WELS_CHECK_SE_LOWER_WARNING (pVui->uiNumUnitsInTick, VUI_NUM_UNITS_IN_TICK_MIN, "num_units_in_tick");
    WELS_READ_VERIFY (BsGetBits (pBsAux, 16, &uiCode)); //time_scale
    uiTmp = (uiCode << 16);
    WELS_READ_VERIFY (BsGetBits (pBsAux, 16, &uiCode)); //time_scale
    uiTmp |= uiCode;
    pVui->uiTimeScale = uiTmp;
    WELS_CHECK_SE_LOWER_WARNING (pVui->uiNumUnitsInTick, VUI_TIME_SCALE_MIN, "time_scale");
    WELS_READ_VERIFY (BsGetOneBit (pBsAux, &uiCode)); //fixed_frame_rate_flag
    pVui->bFixedFrameRateFlag = !!uiCode;
  }
  WELS_READ_VERIFY (BsGetOneBit (pBsAux, &uiCode)); //nal_hrd_parameters_present_flag
  pVui->bNalHrdParamPresentFlag = !!uiCode;
  if (pVui->bNalHrdParamPresentFlag) { //Add HRD parse. the values are not being used though.
#ifdef _PARSE_NALHRD_VCLHRD_PARAMS_
    int32_t cpb_cnt_minus1 = BsGetUe (pBsAux, &uiCode);
    /*bit_rate_scale = */BsGetBits (pBsAux, 4, &uiCode);
    /*cpb_size_scale = */BsGetBits (pBsAux, 4, &uiCode);
    for (int32_t i = 0; i <= cpb_cnt_minus1; i++) {
      /*bit_rate_value_minus1[i] = */BsGetUe (pBsAux, &uiCode);
      /*cpb_size_value_minus1[i] = */BsGetUe (pBsAux, &uiCode);
      /*cbr_flag[i] = */BsGetOneBit (pBsAux, &uiCode);
    }
    /*initial_cpb_removal_delay_length_minus1 = */BsGetBits (pBsAux, 5, &uiCode);
    /*cpb_removal_delay_length_minus1 = */BsGetBits (pBsAux, 5, &uiCode);
    /*dpb_output_delay_length_minus1 = */BsGetBits (pBsAux, 5, &uiCode);
    /*time_offset_length = */BsGetBits (pBsAux, 5, &uiCode);
#else
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, "nal_hrd_parameters_present_flag = 1 not supported.");
    return GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_UNSUPPORTED_VUI_HRD);
#endif
  }
  WELS_READ_VERIFY (BsGetOneBit (pBsAux, &uiCode)); //vcl_hrd_parameters_present_flag
  pVui->bVclHrdParamPresentFlag = !!uiCode;
  if (pVui->bVclHrdParamPresentFlag) {//Add HRD parse. the values are not being used though.
#ifdef _PARSE_NALHRD_VCLHRD_PARAMS_
    int32_t cpb_cnt_minus1 = BsGetUe (pBsAux, &uiCode);
    /*bit_rate_scale = */BsGetBits (pBsAux, 4, &uiCode);
    /*cpb_size_scale = */BsGetBits (pBsAux, 4, &uiCode);
    for (int32_t i = 0; i <= cpb_cnt_minus1; i++) {
      /*bit_rate_value_minus1[i] = */BsGetUe (pBsAux, &uiCode);
      /*cpb_size_value_minus1[i] = */BsGetUe (pBsAux, &uiCode);
      /*cbr_flag[i] = */BsGetOneBit (pBsAux, &uiCode);
    }
    /*initial_cpb_removal_delay_length_minus1 = */BsGetBits (pBsAux, 5, &uiCode);
    /*cpb_removal_delay_length_minus1 = */BsGetBits (pBsAux, 5, &uiCode);
    /*dpb_output_delay_length_minus1 = */BsGetBits (pBsAux, 5, &uiCode);
    /*time_offset_length = */BsGetBits (pBsAux, 5, &uiCode);
#else
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, "vcl_hrd_parameters_present_flag = 1 not supported.");
    return GENERATE_ERROR_NO (ERR_LEVEL_PARAM_SETS, ERR_INFO_UNSUPPORTED_VUI_HRD);
#endif
  }
#ifdef _PARSE_NALHRD_VCLHRD_PARAMS_
  if (pVui->bNalHrdParamPresentFlag | pVui->bVclHrdParamPresentFlag) {
    /*low_delay_hrd_flag = */BsGetOneBit (pBsAux, &uiCode);
  }
#endif
  WELS_READ_VERIFY (BsGetOneBit (pBsAux, &uiCode)); //pic_struct_present_flag
  pVui->bPicStructPresentFlag = !!uiCode;
  WELS_READ_VERIFY (BsGetOneBit (pBsAux, &uiCode)); //bitstream_restriction_flag
  pVui->bBitstreamRestrictionFlag = !!uiCode;
  if (pVui->bBitstreamRestrictionFlag) {
    WELS_READ_VERIFY (BsGetOneBit (pBsAux, &uiCode)); //motion_vectors_over_pic_boundaries_flag
    pVui->bMotionVectorsOverPicBoundariesFlag = !!uiCode;
    WELS_READ_VERIFY (BsGetUe (pBsAux, &uiCode)); //max_bytes_per_pic_denom
    pVui->uiMaxBytesPerPicDenom = uiCode;
    WELS_CHECK_SE_UPPER_WARNING (pVui->uiMaxBytesPerPicDenom, VUI_MAX_BYTES_PER_PIC_DENOM_MAX,
                                 "max_bytes_per_pic_denom");
    WELS_READ_VERIFY (BsGetUe (pBsAux, &uiCode)); //max_bits_per_mb_denom
    pVui->uiMaxBitsPerMbDenom = uiCode;
    WELS_CHECK_SE_UPPER_WARNING (pVui->uiMaxBitsPerMbDenom, VUI_MAX_BITS_PER_MB_DENOM_MAX,
                                 "max_bits_per_mb_denom");
    WELS_READ_VERIFY (BsGetUe (pBsAux, &uiCode)); //log2_max_mv_length_horizontal
    pVui->uiLog2MaxMvLengthHorizontal = uiCode;
    WELS_CHECK_SE_UPPER_WARNING (pVui->uiLog2MaxMvLengthHorizontal, VUI_LOG2_MAX_MV_LENGTH_HOR_MAX,
                                 "log2_max_mv_length_horizontal");
    WELS_READ_VERIFY (BsGetUe (pBsAux, &uiCode)); //log2_max_mv_length_vertical
    pVui->uiLog2MaxMvLengthVertical = uiCode;
    WELS_CHECK_SE_UPPER_WARNING (pVui->uiLog2MaxMvLengthVertical, VUI_LOG2_MAX_MV_LENGTH_VER_MAX,
                                 "log2_max_mv_length_vertical");
    WELS_READ_VERIFY (BsGetUe (pBsAux, &uiCode)); //max_num_reorder_frames
    pVui->uiMaxNumReorderFrames = uiCode;
    WELS_CHECK_SE_UPPER_WARNING (pVui->uiMaxNumReorderFrames, VUI_MAX_DEC_FRAME_BUFFERING_MAX,
                                 "max_num_reorder_frames");
    WELS_READ_VERIFY (BsGetUe (pBsAux, &uiCode)); //max_dec_frame_buffering
    pVui->uiMaxDecFrameBuffering = uiCode;
    WELS_CHECK_SE_UPPER_WARNING (pVui->uiMaxDecFrameBuffering, VUI_MAX_DEC_FRAME_BUFFERING_MAX,
                                 "max_num_reorder_frames");
  }
  return ERR_NONE;
}
/*!
 *************************************************************************************
 * \brief   to parse SEI message payload
 *
 * \param   pSei        sei message to be parsed output
 * \param   pBsAux      bitstream reader auxiliary
 *
 * \return  0 - successed
 *          1 - failed
 *
 * \note    Call it in case eNalUnitType is NAL_UNIT_SEI.
 *************************************************************************************
 */
int32_t ParseSei (void* pSei, PBitStringAux pBsAux) { // reserved Sei_Msg type


  return ERR_NONE;
}
/*
 *************************************************************************************
 * \brief   to parse scalinglist message payload
 *
 * \param   pps sps scaling list matrix      message to be parsed output
 * \param   pBsAux      bitstream reader auxiliary
 *
 * \return  0 - successed
 *          1 - failed
 *
 * \note    Call it in case scaling list matrix present at sps or pps level
 *************************************************************************************
 */
int32_t SetScalingListValue (uint8_t* pScalingList, int iScalingListNum, bool* bUseDefaultScalingMatrixFlag,
                             PBitStringAux pBsAux) { // reserved Sei_Msg type
  int iLastScale = 8;
  int iNextScale = 8;
  int iDeltaScale;
  int32_t iCode;
  int32_t iIdx;
  for (int j = 0; j < iScalingListNum; j++) {
    if (iNextScale != 0) {
      WELS_READ_VERIFY (BsGetSe (pBsAux, &iCode));
      WELS_CHECK_SE_BOTH_ERROR_NOLOG (iCode, SCALING_LIST_DELTA_SCALE_MIN, SCALING_LIST_DELTA_SCALE_MAX, "DeltaScale",
                                      ERR_SCALING_LIST_DELTA_SCALE);
      iDeltaScale = iCode;
      iNextScale = (iLastScale + iDeltaScale + 256) % 256;
      *bUseDefaultScalingMatrixFlag = (j == 0 && iNextScale == 0);
      if (*bUseDefaultScalingMatrixFlag)
        break;
    }
    iIdx = iScalingListNum == 16 ? g_kuiZigzagScan[j] : g_kuiZigzagScan8x8[j];
    pScalingList[iIdx] = (iNextScale == 0) ? iLastScale : iNextScale;
    iLastScale = pScalingList[iIdx];
  }


  return ERR_NONE;
}

int32_t ParseScalingList (PSps pSps, PBitStringAux pBs, bool bPPS, const bool kbTrans8x8ModeFlag,
                          bool* pScalingListPresentFlag, uint8_t (*iScalingList4x4)[16], uint8_t (*iScalingList8x8)[64]) {
  uint32_t uiScalingListNum;
  uint32_t uiCode;

  bool bUseDefaultScalingMatrixFlag4x4 = false;
  bool bUseDefaultScalingMatrixFlag8x8 = false;
  bool bInit = false;
  const uint8_t* defaultScaling[4];

  if (!bPPS) { //sps scaling_list
    uiScalingListNum = (pSps->uiChromaFormatIdc != 3) ? 8 : 12;
  } else { //pps scaling_list
    uiScalingListNum = 6 + (int32_t) kbTrans8x8ModeFlag * ((pSps->uiChromaFormatIdc != 3) ? 2 : 6);
    bInit = pSps->bSeqScalingMatrixPresentFlag;
  }

//Init default_scaling_list value for sps or pps
  defaultScaling[0] = bInit ? pSps->iScalingList4x4[0] : g_kuiDequantScaling4x4Default[0];
  defaultScaling[1] = bInit ? pSps->iScalingList4x4[3] : g_kuiDequantScaling4x4Default[1];
  defaultScaling[2] = bInit ? pSps->iScalingList8x8[0] : g_kuiDequantScaling8x8Default[0];
  defaultScaling[3] = bInit ? pSps->iScalingList8x8[1] : g_kuiDequantScaling8x8Default[1];

  for (unsigned int i = 0; i < uiScalingListNum; i++) {
    WELS_READ_VERIFY (BsGetOneBit (pBs, &uiCode));
    pScalingListPresentFlag[i] = !!uiCode;
    if (!!uiCode) {
      if (i < 6) {//4x4 scaling list
        WELS_READ_VERIFY (SetScalingListValue (iScalingList4x4[i], 16, &bUseDefaultScalingMatrixFlag4x4, pBs));
        if (bUseDefaultScalingMatrixFlag4x4) {
          bUseDefaultScalingMatrixFlag4x4 = false;
          memcpy (iScalingList4x4[i], g_kuiDequantScaling4x4Default[i / 3], sizeof (uint8_t) * 16);
        }


      } else {
        WELS_READ_VERIFY (SetScalingListValue (iScalingList8x8[i - 6], 64, &bUseDefaultScalingMatrixFlag8x8, pBs));

        if (bUseDefaultScalingMatrixFlag8x8) {
          bUseDefaultScalingMatrixFlag8x8 = false;
          memcpy (iScalingList8x8[i - 6], g_kuiDequantScaling8x8Default[ (i - 6) & 1], sizeof (uint8_t) * 64);
        }
      }

    } else {
      if (i < 6) {
        if ((i != 0) && (i != 3))
          memcpy (iScalingList4x4[i], iScalingList4x4[i - 1], sizeof (uint8_t) * 16);
        else
          memcpy (iScalingList4x4[i], defaultScaling[i / 3], sizeof (uint8_t) * 16);

      } else {
        if ((i == 6) || (i == 7))
          memcpy (iScalingList8x8[i - 6], defaultScaling[ (i & 1) + 2], sizeof (uint8_t) * 64);
        else
          memcpy (iScalingList8x8[i - 6], iScalingList8x8[i - 8], sizeof (uint8_t) * 64);

      }
    }
  }
  return ERR_NONE;

}

/*!
 *************************************************************************************
 * \brief   reset fmo list due to got Sps now
 *
 * \param   pCtx    decoder context
 *
 * \return  count number of fmo context units are reset
 *************************************************************************************
 */
int32_t ResetFmoList (PWelsDecoderContext pCtx) {
  int32_t iCountNum = 0;
  if (NULL != pCtx) {
    // Fixed memory leak due to PPS_ID might not be continuous sometimes, 1/5/2010
    UninitFmoList (&pCtx->sFmoList[0], MAX_PPS_COUNT, pCtx->iActiveFmoNum, pCtx->pMemAlign);
    iCountNum = pCtx->iActiveFmoNum;
    pCtx->iActiveFmoNum = 0;
  }
  return iCountNum;
}

} // namespace WelsDec
