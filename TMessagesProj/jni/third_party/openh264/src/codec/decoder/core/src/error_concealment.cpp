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
 *      error_concealment.cpp:  Wels decoder error concealment implementation
 */

#include "error_code.h"
#include "expand_pic.h"
#include "manage_dec_ref.h"
#include "copy_mb.h"
#include "error_concealment.h"
#include "cpu_core.h"

namespace WelsDec {
//Init
void InitErrorCon (PWelsDecoderContext pCtx) {
  if ((pCtx->pParam->eEcActiveIdc == ERROR_CON_SLICE_COPY)
      || (pCtx->pParam->eEcActiveIdc == ERROR_CON_SLICE_COPY_CROSS_IDR)
      || (pCtx->pParam->eEcActiveIdc == ERROR_CON_SLICE_MV_COPY_CROSS_IDR)
      || (pCtx->pParam->eEcActiveIdc == ERROR_CON_SLICE_MV_COPY_CROSS_IDR_FREEZE_RES_CHANGE)
      || (pCtx->pParam->eEcActiveIdc == ERROR_CON_SLICE_COPY_CROSS_IDR_FREEZE_RES_CHANGE)) {
    if ((pCtx->pParam->eEcActiveIdc != ERROR_CON_SLICE_MV_COPY_CROSS_IDR_FREEZE_RES_CHANGE)
        && (pCtx->pParam->eEcActiveIdc != ERROR_CON_SLICE_COPY_CROSS_IDR_FREEZE_RES_CHANGE)) {
      pCtx->bFreezeOutput = false;
    }
    pCtx->sCopyFunc.pCopyLumaFunc = WelsCopy16x16_c;
    pCtx->sCopyFunc.pCopyChromaFunc = WelsCopy8x8_c;

#if defined(X86_ASM)
    if (pCtx->uiCpuFlag & WELS_CPU_MMXEXT) {
      pCtx->sCopyFunc.pCopyChromaFunc = WelsCopy8x8_mmx; //aligned
    }

    if (pCtx->uiCpuFlag & WELS_CPU_SSE2) {
      pCtx->sCopyFunc.pCopyLumaFunc = WelsCopy16x16_sse2; //this is aligned copy;
    }
#endif //X86_ASM

#if defined(HAVE_NEON)
    if (pCtx->uiCpuFlag & WELS_CPU_NEON) {
      pCtx->sCopyFunc.pCopyLumaFunc     = WelsCopy16x16_neon; //aligned
      pCtx->sCopyFunc.pCopyChromaFunc   = WelsCopy8x8_neon; //aligned
    }
#endif //HAVE_NEON

#if defined(HAVE_NEON_AARCH64)
    if (pCtx->uiCpuFlag & WELS_CPU_NEON) {
      pCtx->sCopyFunc.pCopyLumaFunc     = WelsCopy16x16_AArch64_neon; //aligned
      pCtx->sCopyFunc.pCopyChromaFunc   = WelsCopy8x8_AArch64_neon; //aligned
    }
#endif //HAVE_NEON_AARCH64
  } //TODO add more methods here
  return;
}

//Do error concealment using frame copy method
void DoErrorConFrameCopy (PWelsDecoderContext pCtx) {
  PPicture pDstPic = pCtx->pDec;
  PPicture pSrcPic = pCtx->pLastDecPicInfo->pPreviousDecodedPictureInDpb;
  uint32_t uiHeightInPixelY = (pCtx->pSps->iMbHeight) << 4;
  int32_t iStrideY = pDstPic->iLinesize[0];
  int32_t iStrideUV = pDstPic->iLinesize[1];
  pCtx->pDec->iMbEcedNum = pCtx->pSps->iMbWidth * pCtx->pSps->iMbHeight;
  if ((pCtx->pParam->eEcActiveIdc == ERROR_CON_FRAME_COPY) && (pCtx->pCurDqLayer->sLayerInfo.sNalHeaderExt.bIdrFlag))
    pSrcPic = NULL; //no cross IDR method, should fill in data instead of copy
  if (pSrcPic == NULL) { //no ref pic, assign specific data to picture
    memset (pDstPic->pData[0], 128, uiHeightInPixelY * iStrideY);
    memset (pDstPic->pData[1], 128, (uiHeightInPixelY >> 1) * iStrideUV);
    memset (pDstPic->pData[2], 128, (uiHeightInPixelY >> 1) * iStrideUV);
  } else if (pSrcPic == pDstPic) {
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, "DoErrorConFrameCopy()::EC memcpy overlap.");
  } else { //has ref pic here
    memcpy (pDstPic->pData[0], pSrcPic->pData[0], uiHeightInPixelY * iStrideY);
    memcpy (pDstPic->pData[1], pSrcPic->pData[1], (uiHeightInPixelY >> 1) * iStrideUV);
    memcpy (pDstPic->pData[2], pSrcPic->pData[2], (uiHeightInPixelY >> 1) * iStrideUV);
  }
}


//Do error concealment using slice copy method
void DoErrorConSliceCopy (PWelsDecoderContext pCtx) {
  int32_t iMbWidth = (int32_t) pCtx->pSps->iMbWidth;
  int32_t iMbHeight = (int32_t) pCtx->pSps->iMbHeight;
  PPicture pDstPic = pCtx->pDec;
  PPicture pSrcPic = pCtx->pLastDecPicInfo->pPreviousDecodedPictureInDpb;
  if ((pCtx->pParam->eEcActiveIdc == ERROR_CON_SLICE_COPY) && (pCtx->pCurDqLayer->sLayerInfo.sNalHeaderExt.bIdrFlag))
    pSrcPic = NULL; //no cross IDR method, should fill in data instead of copy

  //uint8_t *pDstData[3], *pSrcData[3];
  bool* pMbCorrectlyDecodedFlag = pCtx->pCurDqLayer->pMbCorrectlyDecodedFlag;
  //Do slice copy late
  int32_t iMbXyIndex;
  uint8_t* pSrcData, *pDstData;
  uint32_t iSrcStride; // = pSrcPic->iLinesize[0];
  uint32_t iDstStride = pDstPic->iLinesize[0];
  if (pSrcPic == pDstPic) {
    WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, "DoErrorConSliceCopy()::EC memcpy overlap.");
    return;
  }
  for (int32_t iMbY = 0; iMbY < iMbHeight; ++iMbY) {
    for (int32_t iMbX = 0; iMbX < iMbWidth; ++iMbX) {
      iMbXyIndex = iMbY * iMbWidth + iMbX;
      if (!pMbCorrectlyDecodedFlag[iMbXyIndex]) {
        pCtx->pDec->iMbEcedNum++;
        if (pSrcPic != NULL) {
          iSrcStride = pSrcPic->iLinesize[0];
          //Y component
          pDstData = pDstPic->pData[0] + iMbY * 16 * iDstStride + iMbX * 16;
          pSrcData = pSrcPic->pData[0] + iMbY * 16 * iSrcStride + iMbX * 16;
          pCtx->sCopyFunc.pCopyLumaFunc (pDstData, iDstStride, pSrcData, iSrcStride);
          //U component
          pDstData = pDstPic->pData[1] + iMbY * 8 * iDstStride / 2 + iMbX * 8;
          pSrcData = pSrcPic->pData[1] + iMbY * 8 * iSrcStride / 2 + iMbX * 8;
          pCtx->sCopyFunc.pCopyChromaFunc (pDstData, iDstStride / 2, pSrcData, iSrcStride / 2);
          //V component
          pDstData = pDstPic->pData[2] + iMbY * 8 * iDstStride / 2 + iMbX * 8;
          pSrcData = pSrcPic->pData[2] + iMbY * 8 * iSrcStride / 2 + iMbX * 8;
          pCtx->sCopyFunc.pCopyChromaFunc (pDstData, iDstStride / 2, pSrcData, iSrcStride / 2);
        } else { //pSrcPic == NULL
          //Y component
          pDstData = pDstPic->pData[0] + iMbY * 16 * iDstStride + iMbX * 16;
          for (int32_t i = 0; i < 16; ++i) {
            memset (pDstData, 128, 16);
            pDstData += iDstStride;
          }
          //U component
          pDstData = pDstPic->pData[1] + iMbY * 8 * iDstStride / 2 + iMbX * 8;
          for (int32_t i = 0; i < 8; ++i) {
            memset (pDstData, 128, 8);
            pDstData += iDstStride / 2;
          }
          //V component
          pDstData = pDstPic->pData[2] + iMbY * 8 * iDstStride / 2 + iMbX * 8;
          for (int32_t i = 0; i < 8; ++i) {
            memset (pDstData, 128, 8);
            pDstData += iDstStride / 2;
          }
        } //
      } //!pMbCorrectlyDecodedFlag[iMbXyIndex]
    } //iMbX
  } //iMbY
}

//Do error concealment using slice MV copy method
void DoMbECMvCopy (PWelsDecoderContext pCtx, PPicture pDec, PPicture pRef, int32_t iMbXy, int32_t iMbX, int32_t iMbY,
                   sMCRefMember* pMCRefMem) {
  if (pDec == pRef) {
    return; // for protection, shall never go into this logic, error info printed outside.
  }
  int16_t iMVs[2];
  int32_t iMbXInPix = iMbX << 4;
  int32_t iMbYInPix = iMbY << 4;
  int32_t iScale0;
  int32_t iScale1;
  uint8_t* pDst[3];
  int32_t iCurrPoc = pDec->iFramePoc;
  pDst[0] = pDec->pData[0] + iMbXInPix + iMbYInPix * pMCRefMem->iDstLineLuma;
  pDst[1] = pDec->pData[1] + (iMbXInPix >> 1) + (iMbYInPix >> 1) * pMCRefMem->iDstLineChroma;
  pDst[2] = pDec->pData[2] + (iMbXInPix >> 1) + (iMbYInPix >> 1) * pMCRefMem->iDstLineChroma;
  if (pDec->bIdrFlag == true || pCtx->pECRefPic[0] == NULL) {
    uint8_t* pSrcData;
    //Y component
    pSrcData = pMCRefMem->pSrcY + iMbY * 16 * pMCRefMem->iSrcLineLuma + iMbX * 16;
    pCtx->sCopyFunc.pCopyLumaFunc (pDst[0], pMCRefMem->iDstLineLuma, pSrcData, pMCRefMem->iSrcLineLuma);
    //U component
    pSrcData = pMCRefMem->pSrcU + iMbY * 8 * pMCRefMem->iSrcLineChroma + iMbX * 8;
    pCtx->sCopyFunc.pCopyChromaFunc (pDst[1], pMCRefMem->iDstLineChroma, pSrcData, pMCRefMem->iSrcLineChroma);
    //V component
    pSrcData = pMCRefMem->pSrcV + iMbY * 8 * pMCRefMem->iSrcLineChroma + iMbX * 8;
    pCtx->sCopyFunc.pCopyChromaFunc (pDst[2], pMCRefMem->iDstLineChroma, pSrcData, pMCRefMem->iSrcLineChroma);
    return;
  }

  if (pCtx->pECRefPic[0]) {
    if (pCtx->pECRefPic[0] == pRef) {
      iMVs[0] = pCtx->iECMVs[0][0];
      iMVs[1] = pCtx->iECMVs[0][1];
    } else {
      iScale0 = pCtx->pECRefPic[0]->iFramePoc - iCurrPoc;
      iScale1 = pRef->iFramePoc - iCurrPoc;
      iMVs[0] = iScale0 == 0 ? 0 : pCtx->iECMVs[0][0] * iScale1 / iScale0;
      iMVs[1] = iScale0 == 0 ? 0 : pCtx->iECMVs[0][1] * iScale1 / iScale0;
    }
    pMCRefMem->pDstY = pDst[0];
    pMCRefMem->pDstU = pDst[1];
    pMCRefMem->pDstV = pDst[2];
    int32_t iFullMVx = (iMbXInPix << 2) + iMVs[0]; //quarter pixel
    int32_t iFullMVy = (iMbYInPix << 2) + iMVs[1];
    // only use to be output pixels to EC;
    int32_t iPicWidthLeftLimit = 0;
    int32_t iPicHeightTopLimit = 0;
    int32_t iPicWidthRightLimit = pMCRefMem->iPicWidth;
    int32_t iPicHeightBottomLimit = pMCRefMem->iPicHeight;
    if (pCtx->pSps->bFrameCroppingFlag) {
      iPicWidthLeftLimit = 0 + pCtx->sFrameCrop.iLeftOffset * 2;
      iPicWidthRightLimit = (pMCRefMem->iPicWidth - pCtx->sFrameCrop.iRightOffset * 2);
      iPicHeightTopLimit = 0 + pCtx->sFrameCrop.iTopOffset * 2;
      iPicHeightBottomLimit = (pMCRefMem->iPicHeight - pCtx->sFrameCrop.iTopOffset * 2);
    }
    // further make sure no need to expand picture
    int32_t iMinLeftOffset = (iPicWidthLeftLimit + 2) * (1 << 2);
    int32_t iMaxRightOffset = ((iPicWidthRightLimit - 18) * (1 << 2));
    int32_t iMinTopOffset = (iPicHeightTopLimit + 2) * (1 << 2);
    int32_t iMaxBottomOffset = ((iPicHeightBottomLimit - 18) * (1 << 2));
    if (iFullMVx < iMinLeftOffset) {
      iFullMVx = (iFullMVx >> 2) * (1 << 2);
      iFullMVx = WELS_MAX (iPicWidthLeftLimit, iFullMVx);
    } else if (iFullMVx > iMaxRightOffset) {
      iFullMVx = (iFullMVx >> 2) * (1 << 2);
      iFullMVx = WELS_MIN (((iPicWidthRightLimit - 16) * (1 << 2)), iFullMVx);
    }
    if (iFullMVy < iMinTopOffset) {
      iFullMVy = (iFullMVy >> 2) * (1 << 2);
      iFullMVy = WELS_MAX (iPicHeightTopLimit, iFullMVy);
    } else if (iFullMVy > iMaxBottomOffset) {
      iFullMVy = (iFullMVy >> 2) * (1 << 2);
      iFullMVy = WELS_MIN (((iPicHeightBottomLimit - 16) * (1 << 2)), iFullMVy);
    }
    iMVs[0] = iFullMVx - (iMbXInPix << 2);
    iMVs[1] = iFullMVy - (iMbYInPix << 2);
    BaseMC (pCtx, pMCRefMem, -1, -1, iMbXInPix, iMbYInPix, &pCtx->sMcFunc, 16, 16, iMVs);
  }
  return;
}

void GetAvilInfoFromCorrectMb (PWelsDecoderContext pCtx) {
  int32_t iMbWidth = (int32_t) pCtx->pSps->iMbWidth;
  int32_t iMbHeight = (int32_t) pCtx->pSps->iMbHeight;
  bool* pMbCorrectlyDecodedFlag = pCtx->pCurDqLayer->pMbCorrectlyDecodedFlag;
  PDqLayer pCurDqLayer = pCtx->pCurDqLayer;
  int32_t iInterMbCorrectNum[16];
  int32_t iMbXyIndex;

  int8_t iRefIdx;
  memset (pCtx->iECMVs, 0, sizeof (int32_t) * 32);
  memset (pCtx->pECRefPic, 0, sizeof (PPicture) * 16);
  memset (iInterMbCorrectNum, 0, sizeof (int32_t) * 16);

  for (int32_t iMbY = 0; iMbY < iMbHeight; ++iMbY) {
    for (int32_t iMbX = 0; iMbX < iMbWidth; ++iMbX) {
      iMbXyIndex = iMbY * iMbWidth + iMbX;
      if (pMbCorrectlyDecodedFlag[iMbXyIndex] && IS_INTER (pCurDqLayer->pDec->pMbType[iMbXyIndex])) {
        uint32_t iMBType = pCurDqLayer->pDec->pMbType[iMbXyIndex];
        switch (iMBType) {
        case MB_TYPE_SKIP:
        case MB_TYPE_16x16:
          iRefIdx = pCurDqLayer->pDec->pRefIndex[0][iMbXyIndex][0];
          pCtx->iECMVs[iRefIdx][0] += pCurDqLayer->pDec->pMv[0][iMbXyIndex][0][0];
          pCtx->iECMVs[iRefIdx][1] += pCurDqLayer->pDec->pMv[0][iMbXyIndex][0][1];
          pCtx->pECRefPic[iRefIdx] = pCtx->sRefPic.pRefList[LIST_0][iRefIdx];
          iInterMbCorrectNum[iRefIdx]++;
          break;
        case MB_TYPE_16x8:
          iRefIdx = pCurDqLayer->pDec->pRefIndex[0][iMbXyIndex][0];
          pCtx->iECMVs[iRefIdx][0] += pCurDqLayer->pDec->pMv[0][iMbXyIndex][0][0];
          pCtx->iECMVs[iRefIdx][1] += pCurDqLayer->pDec->pMv[0][iMbXyIndex][0][1];
          pCtx->pECRefPic[iRefIdx] = pCtx->sRefPic.pRefList[LIST_0][iRefIdx];
          iInterMbCorrectNum[iRefIdx]++;

          iRefIdx = pCurDqLayer->pDec->pRefIndex[0][iMbXyIndex][8];
          pCtx->iECMVs[iRefIdx][0] += pCurDqLayer->pDec->pMv[0][iMbXyIndex][8][0];
          pCtx->iECMVs[iRefIdx][1] += pCurDqLayer->pDec->pMv[0][iMbXyIndex][8][1];
          pCtx->pECRefPic[iRefIdx] = pCtx->sRefPic.pRefList[LIST_0][iRefIdx];
          iInterMbCorrectNum[iRefIdx]++;
          break;
        case MB_TYPE_8x16:
          iRefIdx = pCurDqLayer->pDec->pRefIndex[0][iMbXyIndex][0];
          pCtx->iECMVs[iRefIdx][0] += pCurDqLayer->pDec->pMv[0][iMbXyIndex][0][0];
          pCtx->iECMVs[iRefIdx][1] += pCurDqLayer->pDec->pMv[0][iMbXyIndex][0][1];
          pCtx->pECRefPic[iRefIdx] = pCtx->sRefPic.pRefList[LIST_0][iRefIdx];
          iInterMbCorrectNum[iRefIdx]++;

          iRefIdx = pCurDqLayer->pDec->pRefIndex[0][iMbXyIndex][2];
          pCtx->iECMVs[iRefIdx][0] += pCurDqLayer->pDec->pMv[0][iMbXyIndex][2][0];
          pCtx->iECMVs[iRefIdx][1] += pCurDqLayer->pDec->pMv[0][iMbXyIndex][2][1];
          pCtx->pECRefPic[iRefIdx] = pCtx->sRefPic.pRefList[LIST_0][iRefIdx];
          iInterMbCorrectNum[iRefIdx]++;
          break;
        case MB_TYPE_8x8:
        case MB_TYPE_8x8_REF0: {
          uint32_t iSubMBType;
          int32_t i, j, iIIdx, iJIdx;

          for (i = 0; i < 4; i++) {
            iSubMBType = pCurDqLayer->pSubMbType[iMbXyIndex][i];
            iIIdx = ((i >> 1) << 3) + ((i & 1) << 1);
            iRefIdx = pCurDqLayer->pDec->pRefIndex[0][iMbXyIndex][iIIdx];
            pCtx->pECRefPic[iRefIdx] = pCtx->sRefPic.pRefList[LIST_0][iRefIdx];
            switch (iSubMBType) {
            case SUB_MB_TYPE_8x8:
              pCtx->iECMVs[iRefIdx][0] += pCurDqLayer->pDec->pMv[0][iMbXyIndex][iIIdx][0];
              pCtx->iECMVs[iRefIdx][1] += pCurDqLayer->pDec->pMv[0][iMbXyIndex][iIIdx][1];
              iInterMbCorrectNum[iRefIdx]++;

              break;
            case SUB_MB_TYPE_8x4:
              pCtx->iECMVs[iRefIdx][0] += pCurDqLayer->pDec->pMv[0][iMbXyIndex][iIIdx][0];
              pCtx->iECMVs[iRefIdx][1] += pCurDqLayer->pDec->pMv[0][iMbXyIndex][iIIdx][1];


              pCtx->iECMVs[iRefIdx][0] += pCurDqLayer->pDec->pMv[0][iMbXyIndex][iIIdx + 4][0];
              pCtx->iECMVs[iRefIdx][1] += pCurDqLayer->pDec->pMv[0][iMbXyIndex][iIIdx + 4][1];
              iInterMbCorrectNum[iRefIdx] += 2;

              break;
            case SUB_MB_TYPE_4x8:
              pCtx->iECMVs[iRefIdx][0] += pCurDqLayer->pDec->pMv[0][iMbXyIndex][iIIdx][0];
              pCtx->iECMVs[iRefIdx][1] += pCurDqLayer->pDec->pMv[0][iMbXyIndex][iIIdx][1];


              pCtx->iECMVs[iRefIdx][0] += pCurDqLayer->pDec->pMv[0][iMbXyIndex][iIIdx + 1][0];
              pCtx->iECMVs[iRefIdx][1] += pCurDqLayer->pDec->pMv[0][iMbXyIndex][iIIdx + 1][1];
              iInterMbCorrectNum[iRefIdx] += 2;
              break;
            case SUB_MB_TYPE_4x4: {
              for (j = 0; j < 4; j++) {
                iJIdx = ((j >> 1) << 2) + (j & 1);
                pCtx->iECMVs[iRefIdx][0] += pCurDqLayer->pDec->pMv[0][iMbXyIndex][iIIdx + iJIdx][0];
                pCtx->iECMVs[iRefIdx][1] += pCurDqLayer->pDec->pMv[0][iMbXyIndex][iIIdx + iJIdx][1];
              }
              iInterMbCorrectNum[iRefIdx] += 4;
            }
            break;
            default:
              break;
            }
          }
        }
        break;
        default:
          break;
        }
      } //pMbCorrectlyDecodedFlag[iMbXyIndex]
    } //iMbX
  } //iMbY
  for (int32_t i = 0; i < 16; i++) {
    if (iInterMbCorrectNum[i]) {
      pCtx->iECMVs[i][0] = pCtx->iECMVs[i][0] / iInterMbCorrectNum[i];
      pCtx->iECMVs[i][1] = pCtx->iECMVs[i][1] / iInterMbCorrectNum[i];
    }
  }
}

void DoErrorConSliceMVCopy (PWelsDecoderContext pCtx) {
  int32_t iMbWidth = (int32_t) pCtx->pSps->iMbWidth;
  int32_t iMbHeight = (int32_t) pCtx->pSps->iMbHeight;
  PPicture pDstPic = pCtx->pDec;
  PPicture pSrcPic = pCtx->pLastDecPicInfo->pPreviousDecodedPictureInDpb;

  bool* pMbCorrectlyDecodedFlag = pCtx->pCurDqLayer->pMbCorrectlyDecodedFlag;
  int32_t iMbXyIndex;
  uint8_t* pDstData;
  uint32_t iDstStride = pDstPic->iLinesize[0];
  sMCRefMember sMCRefMem;
  if (pSrcPic != NULL) {
    sMCRefMem.iSrcLineLuma   = pSrcPic->iLinesize[0];
    sMCRefMem.iSrcLineChroma = pSrcPic->iLinesize[1];
    sMCRefMem.pSrcY = pSrcPic->pData[0];
    sMCRefMem.pSrcU = pSrcPic->pData[1];
    sMCRefMem.pSrcV = pSrcPic->pData[2];
    sMCRefMem.iDstLineLuma   = pDstPic->iLinesize[0];
    sMCRefMem.iDstLineChroma = pDstPic->iLinesize[1];
    sMCRefMem.iPicWidth = pDstPic->iWidthInPixel;
    sMCRefMem.iPicHeight = pDstPic->iHeightInPixel;
    if (pDstPic == pSrcPic) {
      // output error info, EC will be ignored in DoMbECMvCopy
      WelsLog (& (pCtx->sLogCtx), WELS_LOG_WARNING, "DoErrorConSliceMVCopy()::EC memcpy overlap.");
      return;
    }
  }

  for (int32_t iMbY = 0; iMbY < iMbHeight; ++iMbY) {
    for (int32_t iMbX = 0; iMbX < iMbWidth; ++iMbX) {
      iMbXyIndex = iMbY * iMbWidth + iMbX;
      if (!pMbCorrectlyDecodedFlag[iMbXyIndex]) {
        pCtx->pDec->iMbEcedNum++;
        if (pSrcPic != NULL) {
          DoMbECMvCopy (pCtx, pDstPic, pSrcPic, iMbXyIndex, iMbX, iMbY, &sMCRefMem);
        } else { //pSrcPic == NULL
          //Y component
          pDstData = pDstPic->pData[0] + iMbY * 16 * iDstStride + iMbX * 16;
          for (int32_t i = 0; i < 16; ++i) {
            memset (pDstData, 128, 16);
            pDstData += iDstStride;
          }
          //U component
          pDstData = pDstPic->pData[1] + iMbY * 8 * iDstStride / 2 + iMbX * 8;
          for (int32_t i = 0; i < 8; ++i) {
            memset (pDstData, 128, 8);
            pDstData += iDstStride / 2;
          }
          //V component
          pDstData = pDstPic->pData[2] + iMbY * 8 * iDstStride / 2 + iMbX * 8;
          for (int32_t i = 0; i < 8; ++i) {
            memset (pDstData, 128, 8);
            pDstData += iDstStride / 2;
          }
        } //

      } //!pMbCorrectlyDecodedFlag[iMbXyIndex]
    } //iMbX
  } //iMbY
}

//Mark erroneous frame as Ref Pic into DPB
int32_t MarkECFrameAsRef (PWelsDecoderContext pCtx) {
  int32_t iRet = WelsMarkAsRef (pCtx);
  // Under EC mode, the ERR_INFO_DUPLICATE_FRAME_NUM does not need to be process
  if (iRet != ERR_NONE) {
    return iRet;
  }
  ExpandReferencingPicture (pCtx->pDec->pData, pCtx->pDec->iWidthInPixel, pCtx->pDec->iHeightInPixel,
                            pCtx->pDec->iLinesize,
                            pCtx->sExpandPicFunc.pfExpandLumaPicture, pCtx->sExpandPicFunc.pfExpandChromaPicture);

  return ERR_NONE;
}

bool NeedErrorCon (PWelsDecoderContext pCtx) {
  bool bNeedEC = false;
  int32_t iMbNum = pCtx->pSps->iMbWidth * pCtx->pSps->iMbHeight;
  for (int32_t i = 0; i < iMbNum; ++i) {
    if (!pCtx->pCurDqLayer->pMbCorrectlyDecodedFlag[i]) {
      bNeedEC = true;
      break;
    }
  }
  return bNeedEC;
}

// ImplementErrorConceal
// Do actual error concealment
void ImplementErrorCon (PWelsDecoderContext pCtx) {
  if (ERROR_CON_DISABLE == pCtx->pParam->eEcActiveIdc) {
    pCtx->iErrorCode |= dsBitstreamError;
    return;
  } else if ((ERROR_CON_FRAME_COPY == pCtx->pParam->eEcActiveIdc)
             || (ERROR_CON_FRAME_COPY_CROSS_IDR == pCtx->pParam->eEcActiveIdc)) {
    DoErrorConFrameCopy (pCtx);
  } else if ((ERROR_CON_SLICE_COPY == pCtx->pParam->eEcActiveIdc)
             || (ERROR_CON_SLICE_COPY_CROSS_IDR == pCtx->pParam->eEcActiveIdc)
             || (ERROR_CON_SLICE_COPY_CROSS_IDR_FREEZE_RES_CHANGE == pCtx->pParam->eEcActiveIdc)) {
    DoErrorConSliceCopy (pCtx);
  } else if ((ERROR_CON_SLICE_MV_COPY_CROSS_IDR == pCtx->pParam->eEcActiveIdc)
             || (ERROR_CON_SLICE_MV_COPY_CROSS_IDR_FREEZE_RES_CHANGE == pCtx->pParam->eEcActiveIdc)) {
    GetAvilInfoFromCorrectMb (pCtx);
    DoErrorConSliceMVCopy (pCtx);
  } //TODO add other EC methods here in the future
  pCtx->iErrorCode |= dsDataErrorConcealed;
  pCtx->pDec->bIsComplete = false; // Set complete flag to false after do EC.
}

} // namespace WelsDec
