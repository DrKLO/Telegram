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
 * \file    svc_base_layer_md.c
 *
 * \brief   mode decision
 *
 * \date    2009.08.10 Created
 *
 *************************************************************************************
 */
#include "ls_defines.h"
#include "mv_pred.h"
#include "svc_enc_golomb.h"
#include "svc_base_layer_md.h"
#include "encoder.h"
#include "svc_encode_mb.h"
#include "svc_encode_slice.h"
namespace WelsEnc {
static const ALIGNED_DECLARE (int8_t, g_kiIntra16AvaliMode[8][5], 16) = {
  { I16_PRED_DC_128, I16_PRED_INVALID, I16_PRED_INVALID, I16_PRED_INVALID, 1 },
  { I16_PRED_DC_L,   I16_PRED_H,       I16_PRED_INVALID, I16_PRED_INVALID, 2 },
  { I16_PRED_DC_T,   I16_PRED_V,       I16_PRED_INVALID, I16_PRED_INVALID, 2 },
  { I16_PRED_V,      I16_PRED_H,       I16_PRED_DC,      I16_PRED_INVALID, 3 },
  { I16_PRED_DC_128, I16_PRED_INVALID, I16_PRED_INVALID, I16_PRED_INVALID, 1 },
  { I16_PRED_DC_L,   I16_PRED_H,       I16_PRED_INVALID, I16_PRED_INVALID, 2 },
  { I16_PRED_DC_T,   I16_PRED_V,       I16_PRED_INVALID, I16_PRED_INVALID, 2 },
  { I16_PRED_V,      I16_PRED_H,       I16_PRED_DC,      I16_PRED_P,       4 }
};

static const ALIGNED_DECLARE (uint8_t, g_kiIntra4AvailCount[16], 16) = {
#ifndef  I4_PRED_MODE_EXTEND
  1, 3, 2, 4, 1, 3, 2, 7, 1, 3, 4, 6, 1, 3, 4, 9
#else
  1, 3, 4, 4, 1, 3, 4, 7, 1, 3, 4, 6, 1, 3, 4, 9
#endif  //I4_PRED_MODE_EXTEND
};

//left_avail | (top_avail<<1) | (left_top_avail<<2) | (right_top_avail<<3);
static const ALIGNED_DECLARE (uint8_t, g_kiIntra4AvailMode[16][16], 16) = {
  {
    I4_PRED_DC_128,  I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID
  },  //  0000

  {
    I4_PRED_DC_L,    I4_PRED_H,       I4_PRED_HU,      I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID
  },  //  0001

#ifndef  I4_PRED_MODE_EXTEND
  {
    I4_PRED_DC_T,    I4_PRED_V,       I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID
  }, //  0010
#else
  {
    I4_PRED_DC_T,    I4_PRED_V,       I4_PRED_DDL_TOP, I4_PRED_VL_TOP,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID
  }, //  0010
#endif //I4_PRED_MODE_EXTEND

  {
    I4_PRED_DC,      I4_PRED_H,       I4_PRED_V,       I4_PRED_HU,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID
  }, //  0011

  {
    I4_PRED_DC_128,  I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID
  },  //  0100

  {
    I4_PRED_DC_L,    I4_PRED_H,       I4_PRED_HU,      I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID
  },    //  0101

#ifndef  I4_PRED_MODE_EXTEND
  {
    I4_PRED_DC_T,    I4_PRED_V,       I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID
  },     //  0110
#else
  {
    I4_PRED_DC_T,  I4_PRED_V,       I4_PRED_DDL_TOP, I4_PRED_VL_TOP,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID
  },     //  0110
#endif //I4_PRED_MODE_EXTEND

  {
    I4_PRED_DC,      I4_PRED_H,       I4_PRED_V,       I4_PRED_HU,
    I4_PRED_DDR,     I4_PRED_VR,      I4_PRED_HD,      I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID
  },               //  0111

  {
    I4_PRED_DC_128,   I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID,  I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID,  I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID,  I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID
  },  //  1000

  {
    I4_PRED_DC_L,    I4_PRED_H,       I4_PRED_HU,      I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID
  },    //  1001

  {
    I4_PRED_DC_T,    I4_PRED_V,       I4_PRED_DDL,     I4_PRED_VL,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID
  },     //  1010

  {
    I4_PRED_DC,      I4_PRED_H,       I4_PRED_V,       I4_PRED_HU,
    I4_PRED_DDL,     I4_PRED_VL,      I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID
  },          //  1011

  {
    I4_PRED_DC_128,  I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID
  },  //  1100

  {
    I4_PRED_DC_L,    I4_PRED_H,       I4_PRED_HU,      I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID
  },    //  1101

  {
    I4_PRED_DC_T,    I4_PRED_V,       I4_PRED_DDL,     I4_PRED_VL,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID
  },     //  1110

  {
    I4_PRED_DC,      I4_PRED_H,       I4_PRED_V,       I4_PRED_HU,
    I4_PRED_DDL,     I4_PRED_VL,      I4_PRED_DDR,     I4_PRED_VR,
    I4_PRED_HD,      I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID,
    I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID, I4_PRED_INVALID
  }                          //  1111

};
static const ALIGNED_DECLARE (int8_t, g_kiIntraChromaAvailMode[8][5], 16) = {
  { C_PRED_DC_128, C_PRED_INVALID, C_PRED_INVALID, C_PRED_INVALID, 1 },
  { C_PRED_DC_L,   C_PRED_H,       C_PRED_INVALID, C_PRED_INVALID, 2 },
  { C_PRED_DC_T,   C_PRED_V,       C_PRED_INVALID, C_PRED_INVALID, 2 },
  { C_PRED_V,      C_PRED_H,       C_PRED_DC,      C_PRED_INVALID, 3 },
  { C_PRED_DC_128, C_PRED_INVALID, C_PRED_INVALID, C_PRED_INVALID, 1 },
  { C_PRED_DC_L,   C_PRED_H,       C_PRED_INVALID, C_PRED_INVALID, 2 },
  { C_PRED_DC_T,   C_PRED_V,       C_PRED_INVALID, C_PRED_INVALID, 2 },
  { C_PRED_V,      C_PRED_H,       C_PRED_DC,      C_PRED_P,       4 }
};

// for cache hit, two table are total sizeof 64 Bytes
const int8_t g_kiCoordinateIdx4x4X[16] = { 0, 4, 0, 4,
                                           8, 12, 8, 12,
                                           0, 4, 0, 4,
                                           8, 12, 8, 12
                                         };

const int8_t g_kiCoordinateIdx4x4Y[16] = { 0, 0, 4, 4,
                                           0, 0, 4, 4,
                                           8, 8, 12, 12,
                                           8, 8, 12, 12
                                         };
static const ALIGNED_DECLARE (int8_t, g_kiNeighborIntraToI4x4[16][16], 16) = {
  { 0,  1,  10, 7,  1,  1,  15, 7,  10, 15, 10, 7,  15, 7,  15, 7},
  { 1,  1,  15, 7,  1,  1,  15, 7,  15, 15, 15, 7,  15, 7,  15, 7},
  { 10, 15, 10, 7,  15, 7,  15, 7,  10, 15, 10, 7,  15, 7,  15, 7},
  { 11, 15, 15, 7,  15, 7,  15, 7,  15, 15, 15, 7,  15, 7,  15, 7},
  { 4,  1,  10, 7,  1,  1,  15, 7,  10, 15, 10, 7,  15, 7,  15, 7},
  { 5,  1,  15, 7,  1,  1,  15, 7,  15, 15, 15, 7,  15, 7,  15, 7},
  { 14, 15, 10, 7,  15, 7,  15, 7,  10, 15, 10, 7,  15, 7,  15, 7},
  { 15, 15, 15, 7,  15, 7,  15, 7,  15, 15, 15, 7,  15, 7,  15, 7},
  { 0,  1,  10, 7,  1,  9,  15, 7,  10, 15, 10, 7,  15, 7,  15, 7},
  { 1,  1,  15, 7,  1,  9,  15, 7,  15, 15, 15, 7,  15, 7,  15, 7},
  { 10, 15, 10, 7,  15, 15, 15, 7,  10, 15, 10, 7,  15, 7,  15, 7},
  { 11, 15, 15, 7,  15, 15, 15, 7,  15, 15, 15, 7,  15, 7,  15, 7},
  { 4,  1,  10, 7,  1,  9,  15, 7,  10, 15, 10, 7,  15, 7,  15, 7},
  { 5,  1,  15, 7,  1,  9,  15, 7,  15, 15, 15, 7,  15, 7,  15, 7},
  { 14, 15, 10, 7,  15, 15, 15, 7,  10, 15, 10, 7,  15, 7,  15, 7},
  { 15, 15, 15, 7,  15, 15, 15, 7,  15, 15, 15, 7,  15, 7,  15, 7},
};

ALIGNED_DECLARE (const int8_t, g_kiMapModeI4x4[14], 16) = {
  0, 1, 2, 3, 4, 5, 6, 7, 8, 2, 2, 2, 3, 7
};

int32_t PredIntra4x4Mode (int8_t* pIntraPredMode, int32_t iIdx4) {
  int8_t iTopMode = pIntraPredMode[iIdx4 - 8];
  int8_t iLeftMode = pIntraPredMode[iIdx4 - 1];
  int8_t iBestMode;

  if (-1 == iLeftMode || -1 == iTopMode) {
    iBestMode = 2;
  } else {
    iBestMode = WELS_MIN (iLeftMode, iTopMode);
  }
  return iBestMode;
}

void WelsMdIntraInit (sWelsEncCtx* pEncCtx, SMB* pCurMb, SMbCache* pMbCache, const int32_t iSliceFirstMbXY) {
  SDqLayer* pCurLayer = pEncCtx->pCurDqLayer;

  const int32_t kiMbX  = pCurMb->iMbX;
  const int32_t kiMbY  = pCurMb->iMbY;
  const int32_t kiMbXY = pCurMb->iMbXY;

  // step 3. locating current pEnc and pDec
  // unroll loops here
  if (0 == kiMbX || iSliceFirstMbXY == kiMbXY) {
    int32_t iStrideY, iStrideUV;
    int32_t iOffsetY, iOffsetUV;

    iStrideY    = pCurLayer->iEncStride[0];
    iStrideUV   = pCurLayer->iEncStride[1];
    iOffsetY    = (kiMbX + kiMbY * iStrideY) << 4;
    iOffsetUV   = (kiMbX + kiMbY * iStrideUV) << 3;
    pMbCache->SPicData.pEncMb[0]        = pCurLayer->pEncData[0] + iOffsetY;
    pMbCache->SPicData.pEncMb[1]        = pCurLayer->pEncData[1] + iOffsetUV;
    pMbCache->SPicData.pEncMb[2]        = pCurLayer->pEncData[2] + iOffsetUV;

    iStrideY    = pCurLayer->iCsStride[0];
    iStrideUV   = pCurLayer->iCsStride[1];
    iOffsetY    = (kiMbX + kiMbY * iStrideY) << 4;
    iOffsetUV   = (kiMbX + kiMbY * iStrideUV) << 3;
    pMbCache->SPicData.pCsMb[0]         = pCurLayer->pCsData[0] + iOffsetY;
    pMbCache->SPicData.pCsMb[1]         = pCurLayer->pCsData[1] + iOffsetUV;
    pMbCache->SPicData.pCsMb[2]         = pCurLayer->pCsData[2] + iOffsetUV;

    iStrideY    = pCurLayer->pDecPic->iLineSize[0];
    iStrideUV   = pCurLayer->pDecPic->iLineSize[1];
    iOffsetY    = (kiMbX + kiMbY * iStrideY) << 4;
    iOffsetUV   = (kiMbX + kiMbY * iStrideUV) << 3;
    pMbCache->SPicData.pDecMb[0]        = pCurLayer->pDecPic->pData[0] + iOffsetY;
    pMbCache->SPicData.pDecMb[1]        = pCurLayer->pDecPic->pData[1] + iOffsetUV;
    pMbCache->SPicData.pDecMb[2]        = pCurLayer->pDecPic->pData[2] + iOffsetUV;
  } else {
    pMbCache->SPicData.pEncMb[0]        += MB_WIDTH_LUMA;
    pMbCache->SPicData.pEncMb[1]        += MB_WIDTH_CHROMA;
    pMbCache->SPicData.pEncMb[2]        += MB_WIDTH_CHROMA;

    pMbCache->SPicData.pDecMb[0]        += MB_WIDTH_LUMA;
    pMbCache->SPicData.pDecMb[1]        += MB_WIDTH_CHROMA;
    pMbCache->SPicData.pDecMb[2]        += MB_WIDTH_CHROMA;

    pMbCache->SPicData.pCsMb[0]         += MB_WIDTH_LUMA;
    pMbCache->SPicData.pCsMb[1]         += MB_WIDTH_CHROMA;
    pMbCache->SPicData.pCsMb[2]         += MB_WIDTH_CHROMA;
  }

  //step 2. initial pWelsMd
  pCurMb->uiCbp = 0;

  //step 4: locating scaled_tcoeff

  //step 1. load neighbor cache
  FillNeighborCacheIntra (pMbCache, pCurMb, pCurLayer->iMbWidth);
  pMbCache->pMemPredLuma = pMbCache->pMemPredMb;// in WelsMdI16x16() will be changed, so re-init here!
  pMbCache->pMemPredChroma = pMbCache->pMemPredMb +
                             256;// Init with default, maybe change in WelsMdI16x16 and svc_md_i16x16_sad
}

void WelsMdInterInit (sWelsEncCtx* pEncCtx, SSlice* pSlice, SMB* pCurMb, const int32_t iSliceFirstMbXY) {
  SDqLayer* pCurLayer = pEncCtx->pCurDqLayer;
  SMbCache* pMbCache  = &pSlice->sMbCacheInfo;
  const int32_t kiMbX  = pCurMb->iMbX;
  const int32_t kiMbY  = pCurMb->iMbY;
  const int32_t kiMbXY = pCurMb->iMbXY;
  const int32_t kiMbWidth = pCurLayer->iMbWidth;
  const int32_t kiMbHeight = pCurLayer->iMbHeight;

  pMbCache->pEncSad = &pCurLayer->pDecPic->pMbSkipSad[kiMbXY];

  //step 1. load neighbor cache
  pEncCtx->pFuncList->pfFillInterNeighborCache (pMbCache, pCurMb, kiMbWidth,
      pEncCtx->pVaa->pVaaBackgroundMbFlag + kiMbXY); //BGD spatial pFunc

  //step 3: initial cost

  //step 4. locating current p_ref
  // merge loops
  if (0 == kiMbX || iSliceFirstMbXY == kiMbXY) {
    const int32_t kiRefStrideY          = pCurLayer->pRefPic->iLineSize[0];
    const int32_t kiRefStrideUV         = pCurLayer->pRefPic->iLineSize[1];
    const int32_t kiCurStrideY          = (kiMbX + kiMbY * kiRefStrideY) << 4;
    const int32_t kiCurStrideUV         = (kiMbX + kiMbY * kiRefStrideUV) << 3;
    pMbCache->SPicData.pRefMb[0]        = pCurLayer->pRefPic->pData[0] + kiCurStrideY;
    pMbCache->SPicData.pRefMb[1]        = pCurLayer->pRefPic->pData[1] + kiCurStrideUV;
    pMbCache->SPicData.pRefMb[2]        = pCurLayer->pRefPic->pData[2] + kiCurStrideUV;
  } else {
    pMbCache->SPicData.pRefMb[0]        += MB_WIDTH_LUMA;
    pMbCache->SPicData.pRefMb[1]        += MB_WIDTH_CHROMA;
    pMbCache->SPicData.pRefMb[2]        += MB_WIDTH_CHROMA;
  }

  pMbCache->uiRefMbType = pCurLayer->pRefPic->uiRefMbType[kiMbXY];
  pMbCache->bCollocatedPredFlag = false;

  //comment: sometimes, mode decision process may skip the md_p16x16 and md_pskip function,
  ST32 (&pCurMb->sP16x16Mv, 0);
  ST32 (&pCurLayer->pDecPic->sMvList[kiMbXY], 0);

  SetMvWithinIntegerMvRange (kiMbWidth, kiMbHeight, kiMbX, kiMbY, pEncCtx->iMvRange, & (pSlice->sMvStartMin),
                             & (pSlice->sMvStartMax));
}

int32_t WelsMdI16x16 (SWelsFuncPtrList* pFunc, SDqLayer* pCurDqLayer, SMbCache* pMbCache, int32_t iLambda) {
  const int8_t*  kpAvailMode;
  int32_t iAvailCount;
  int32_t iIdx = 0;
  uint8_t* pPredI16x16[2] = {pMbCache->pMemPredMb, pMbCache->pMemPredMb + 256};
  uint8_t* pDst       = pPredI16x16[0];
  uint8_t* pDec       = pMbCache->SPicData.pCsMb[0];
  uint8_t* pEnc       = pMbCache->SPicData.pEncMb[0];
  int32_t iLineSizeDec = pCurDqLayer->iCsStride[0];
  int32_t iLineSizeEnc = pCurDqLayer->iEncStride[0];
  int32_t i, iCurCost, iCurMode, iBestMode, iBestCost = INT_MAX;

  int32_t iOffset = pMbCache->uiNeighborIntra & 0x07;
  iAvailCount = g_kiIntra16AvaliMode[iOffset][4];
  kpAvailMode = g_kiIntra16AvaliMode[iOffset];
  if (iAvailCount > 3 && pFunc->sSampleDealingFuncs.pfIntra16x16Combined3) {
    iBestCost = pFunc->sSampleDealingFuncs.pfIntra16x16Combined3 (pDec, iLineSizeDec, pEnc, iLineSizeEnc, &iBestMode,
                iLambda, pDst/*temp*/);
    iCurMode = kpAvailMode[3];
    pFunc->pfGetLumaI16x16Pred[iCurMode] (pDst, pDec, iLineSizeDec);
    iCurCost = pFunc->sSampleDealingFuncs.pfMdCost[BLOCK_16x16] (pDst, 16, pEnc, iLineSizeEnc) + iLambda * 4 ;
    if (iCurCost < iBestCost) {
      iBestMode = iCurMode;
      iBestCost = iCurCost;
    } else {
      pFunc->pfGetLumaI16x16Pred[iBestMode] (pDst, pDec, iLineSizeDec);
    }
    iIdx = 1;
    iBestCost += iLambda;
  } else {
    iBestMode = kpAvailMode[0];
    for (i = 0; i < iAvailCount; ++ i) {
      iCurMode = kpAvailMode[i];

      assert (iCurMode >= 0 && iCurMode < 7);

      pFunc->pfGetLumaI16x16Pred[iCurMode] (pDst, pDec, iLineSizeDec);
      iCurCost = pFunc->sSampleDealingFuncs.pfMdCost[BLOCK_16x16] (pDst, 16, pEnc, iLineSizeEnc);
      iCurCost += iLambda * (BsSizeUE (g_kiMapModeI16x16[iCurMode]));
      if (iCurCost < iBestCost) {
        iBestMode = iCurMode;
        iBestCost = iCurCost;
        iIdx = iIdx ^ 0x01;
        pDst = pPredI16x16[iIdx];
      }
    }
  }
  pMbCache->pMemPredChroma = pPredI16x16[iIdx];

  pMbCache->pMemPredLuma = pPredI16x16[iIdx ^ 0x01];
  pMbCache->uiLumaI16x16Mode  = iBestMode;
  return iBestCost;
}
int32_t WelsMdI4x4 (sWelsEncCtx* pEncCtx, SWelsMD* pWelsMd, SMB* pCurMb, SMbCache* pMbCache) {
  SWelsFuncPtrList* pFunc       = pEncCtx->pFuncList;
  SDqLayer* pCurDqLayer         = pEncCtx->pCurDqLayer;
  int32_t iLambda               = pWelsMd->iLambda;
  int32_t iBestCostLuma         = pWelsMd->iCostLuma;
  uint8_t* pEncMb               = pMbCache->SPicData.pEncMb[0];
  uint8_t* pDecMb               = pMbCache->SPicData.pCsMb[0];
  const int32_t kiLineSizeEnc   = pCurDqLayer->iEncStride[0];
  const int32_t kiLineSizeDec   = pCurDqLayer->iCsStride[0];

  uint8_t* pCurEnc, *pCurDec, *pDst;

  int32_t iPredMode, iCurMode, iBestMode, iFinalMode;
  int32_t iCurCost, iBestCost;
  int32_t iAvailCount;
  const uint8_t* kpAvailMode;
  int32_t i, j, iCoordinateX, iCoordinateY, iIdxStrideEnc, iIdxStrideDec;
  int32_t lambda[2] = {iLambda << 2, iLambda};
  bool* pPrevIntra4x4PredModeFlag       = pMbCache->pPrevIntra4x4PredModeFlag;
  int8_t* pRemIntra4x4PredModeFlag      = pMbCache->pRemIntra4x4PredModeFlag;
  const uint8_t* kpIntra4x4AvailCount   = &g_kiIntra4AvailCount[0];
  const uint8_t* kpCache48CountScan4    = &g_kuiCache48CountScan4Idx[0];
  const int8_t* kpNeighborIntraToI4x4   = g_kiNeighborIntraToI4x4[pMbCache->uiNeighborIntra];
  const int8_t* kpCoordinateIdxX        = &g_kiCoordinateIdx4x4X[0];
  const int8_t* kpCoordinateIdxY        = &g_kiCoordinateIdx4x4Y[0];
  int32_t iBestPredBufferNum            = 0;
  int32_t iCosti4x4                     = 0;

#if defined(X86_ASM)
  WelsPrefetchZero_mmx (g_kiMapModeI4x4);
  WelsPrefetchZero_mmx ((int8_t*)&pFunc->pfGetLumaI4x4Pred);
#endif//X86_ASM

  for (i = 0; i < 16; i++) {
    const int32_t kiOffset = kpNeighborIntraToI4x4[i];

    //step 1: locating current 4x4 block position in pEnc and pDecMb
    iCoordinateX = kpCoordinateIdxX[i];
    iCoordinateY = kpCoordinateIdxY[i];

    iIdxStrideEnc = (iCoordinateY * kiLineSizeEnc) + iCoordinateX;
    pCurEnc = pEncMb + iIdxStrideEnc;
    iIdxStrideDec = (iCoordinateY * kiLineSizeDec) + iCoordinateX;
    pCurDec = pDecMb + iIdxStrideDec;

    //step 2: get predicted mode from neighbor
    iPredMode = PredIntra4x4Mode (pMbCache->iIntraPredMode, kpCache48CountScan4[i]);

    //step 3: collect candidates of iPredMode
    iAvailCount = kpIntra4x4AvailCount[kiOffset];
    kpAvailMode = g_kiIntra4AvailMode[kiOffset];

    //step 4: gain the best pred mode
    iBestCost = INT_MAX;
    iBestMode = kpAvailMode[0];

    if (pFunc->sSampleDealingFuncs.pfIntra4x4Combined3 && (iAvailCount >= 6)) {
      pDst = &pMbCache->pMemPredBlk4[iBestPredBufferNum << 4];

      iBestCost = pFunc->sSampleDealingFuncs.pfIntra4x4Combined3 (pCurDec, kiLineSizeDec, pCurEnc, kiLineSizeEnc, pDst,
                  &iBestMode,
                  lambda[iPredMode == 2], lambda[iPredMode == 1], lambda[iPredMode == 0]);
      //     ST64(&pMbCache->pMemPredBlk4[iBestMode<<4], LD64(mem_pred_blk4_temp));
      //     ST64(&pMbCache->pMemPredBlk4[8+(iBestMode<<4)], LD64(mem_pred_blk4_temp+8));

      for (j = 3; j < iAvailCount; ++ j) {
        iCurMode = kpAvailMode[j];

        assert (iCurMode >= 0 && iCurMode < 14);

        pDst = &pMbCache->pMemPredBlk4[ (1 - iBestPredBufferNum) << 4];

        pFunc->pfGetLumaI4x4Pred[iCurMode] (pDst, pCurDec, kiLineSizeDec);
        iCurCost = pFunc->sSampleDealingFuncs.pfSampleSatd[BLOCK_4x4] (pDst, 4, pCurEnc, kiLineSizeEnc) +
                   lambda[iPredMode == g_kiMapModeI4x4[iCurMode]];

        if (iCurCost < iBestCost) {
          iBestMode = iCurMode;
          iBestCost = iCurCost;
          iBestPredBufferNum = 1 - iBestPredBufferNum;
        }
      }
    } else {
      for (j = 0; j < iAvailCount; ++ j) {
        iCurMode = kpAvailMode[j];

        assert (iCurMode >= 0 && iCurMode < 14);

        pDst = &pMbCache->pMemPredBlk4[ (1 - iBestPredBufferNum) << 4];

        pFunc->pfGetLumaI4x4Pred[iCurMode] (pDst, pCurDec, kiLineSizeDec);
        iCurCost = pFunc->sSampleDealingFuncs.pfSampleSatd[BLOCK_4x4] (pDst, 4, pCurEnc, kiLineSizeEnc) +
                   lambda[iPredMode == g_kiMapModeI4x4[iCurMode]];

        if (iCurCost < iBestCost) {
          iBestMode = iCurMode;
          iBestCost = iCurCost;
          iBestPredBufferNum = 1 - iBestPredBufferNum;
        }
      }
    }
    pMbCache->pBestPredI4x4Blk4 = &pMbCache->pMemPredBlk4[iBestPredBufferNum << 4];
    iCosti4x4 += iBestCost;
    if (iCosti4x4 >= iBestCostLuma) {
      break;
    }

    //step 5: update pred mode and sample avail cache
    iFinalMode = g_kiMapModeI4x4[iBestMode];
    if (iPredMode == iFinalMode) {
      *pPrevIntra4x4PredModeFlag++ = true;
    } else {
      *pPrevIntra4x4PredModeFlag++ = false;
      *pRemIntra4x4PredModeFlag  = (iFinalMode < iPredMode ? iFinalMode : (iFinalMode - 1));
    }
    pRemIntra4x4PredModeFlag++;
    // pCurMb->pIntra4x4PredMode[g_kuiMbCountScan4Idx[i]] = iFinalMode;
    pMbCache->iIntraPredMode[kpCache48CountScan4[i]] = iFinalMode;

    //step 6: encoding I_4x4
    WelsEncRecI4x4Y (pEncCtx, pCurMb, pMbCache, i);
  }
  ST32 (pCurMb->pIntra4x4PredMode, LD32 (&pMbCache->iIntraPredMode[33]));
  pCurMb->pIntra4x4PredMode[4] = pMbCache->iIntraPredMode[12];
  pCurMb->pIntra4x4PredMode[5] = pMbCache->iIntraPredMode[20];
  pCurMb->pIntra4x4PredMode[6] = pMbCache->iIntraPredMode[28];
  iCosti4x4 += (iLambda << 4) + (iLambda << 3); //4*6*lambda from JVT SATD0
  return iCosti4x4;
}

int32_t WelsMdI4x4Fast (sWelsEncCtx* pEncCtx, SWelsMD* pWelsMd, SMB* pCurMb, SMbCache* pMbCache) {
  SWelsFuncPtrList* pFunc       = pEncCtx->pFuncList;
  SDqLayer* pCurDqLayer         = pEncCtx->pCurDqLayer;
  int32_t iLambda               = pWelsMd->iLambda;
  int32_t iBestCostLuma         = pWelsMd->iCostLuma;
  uint8_t* pEncMb               = pMbCache->SPicData.pEncMb[0];
  uint8_t* pDecMb               = pMbCache->SPicData.pCsMb[0];
  const int32_t kiLineSizeEnc   = pCurDqLayer->iEncStride[0];
  const int32_t kiLineSizeDec   = pCurDqLayer->iCsStride[0];

  uint8_t* pCurEnc, *pCurDec, *pDst;
  int8_t iPredMode, iCurMode, iBestMode, iFinalMode;
  int32_t iCurCost, iBestCost;
  int32_t iAvailCount;
  const uint8_t* kpAvailMode;
  int32_t i, j, iCoordinateX, iCoordinateY, iIdxStrideEnc, iIdxStrideDec;
  int32_t iCostH, iCostV, iCostVR, iCostHD, iCostVL, iCostHU, iBestModeFake;
  int32_t lambda[2] = {iLambda << 2, iLambda};
  bool* pPrevIntra4x4PredModeFlag       = pMbCache->pPrevIntra4x4PredModeFlag;
  int8_t* pRemIntra4x4PredModeFlag      = pMbCache->pRemIntra4x4PredModeFlag;
  const uint8_t* kpIntra4x4AvailCount   = &g_kiIntra4AvailCount[0];
  const uint8_t* kpCache48CountScan4    = &g_kuiCache48CountScan4Idx[0];
  const int8_t* kpNeighborIntraToI4x4   = g_kiNeighborIntraToI4x4[pMbCache->uiNeighborIntra];
  const int8_t* kpCoordinateIdxX        = &g_kiCoordinateIdx4x4X[0];
  const int8_t* kpCoordinateIdxY        = &g_kiCoordinateIdx4x4Y[0];
  int32_t iBestPredBufferNum            = 0;
  int32_t iCosti4x4                     = 0;
#if defined(X86_ASM)
  WelsPrefetchZero_mmx (g_kiMapModeI4x4);
  WelsPrefetchZero_mmx ((int8_t*)&pFunc->pfGetLumaI4x4Pred);
#endif//X86_ASM

  for (i = 0; i < 16; i++) {
    const int32_t kiOffset = kpNeighborIntraToI4x4[i];
//    const int32_t i_next = (1+i) & 15; // next loop
//    const uint8_t dummy_byte= pIntra4x4AvailCount[pNeighborIntraToI4x4[i_next]]; // prefetch pIntra4x4AvailCount of next loop to avoid cache missed

    //step 1: locating current 4x4 block position in pEnc and pDecMb
    iCoordinateX = kpCoordinateIdxX[i];
    iCoordinateY = kpCoordinateIdxY[i];

    iIdxStrideEnc = (iCoordinateY * kiLineSizeEnc) + iCoordinateX;
    pCurEnc = pEncMb + iIdxStrideEnc;
    iIdxStrideDec = (iCoordinateY * kiLineSizeDec) + iCoordinateX;
    pCurDec = pDecMb + iIdxStrideDec;

    //step 2: get predicted mode from neighbor
    iPredMode = PredIntra4x4Mode (pMbCache->iIntraPredMode, kpCache48CountScan4[i]);
    //step 3: collect candidates of iPredMode
    iAvailCount = kpIntra4x4AvailCount[kiOffset];
    kpAvailMode = g_kiIntra4AvailMode[kiOffset];

    if (iAvailCount == 9 || iAvailCount == 7) {
      //I4_PRED_DC(2)

      iBestMode = I4_PRED_DC;

      pDst = &pMbCache->pMemPredBlk4[iBestPredBufferNum << 4];

      pFunc->pfGetLumaI4x4Pred[I4_PRED_DC] (pDst, pCurDec, kiLineSizeDec);
      iBestCost = pFunc->sSampleDealingFuncs.pfMdCost[BLOCK_4x4] (pDst, 4, pCurEnc, kiLineSizeEnc) +
                  lambda[iPredMode == g_kiMapModeI4x4[iBestMode]];

      //I4_PRED_H(1)
      iCurMode = I4_PRED_H;

      pDst = &pMbCache->pMemPredBlk4[ (1 - iBestPredBufferNum) << 4];

      pFunc->pfGetLumaI4x4Pred[iCurMode] (pDst, pCurDec, kiLineSizeDec);
      iCostH = iCurCost = pFunc->sSampleDealingFuncs.pfMdCost[BLOCK_4x4] (pDst, 4, pCurEnc, kiLineSizeEnc) +
                          lambda[iPredMode == g_kiMapModeI4x4[iCurMode]];

      if (iCurCost < iBestCost) {
        iBestMode = iCurMode;
        iBestCost = iCurCost;
        iBestPredBufferNum = 1 - iBestPredBufferNum;
      }

      //I4_PRED_V(0)
      iCurMode = I4_PRED_V;

      pDst = &pMbCache->pMemPredBlk4[ (1 - iBestPredBufferNum) << 4];

      pFunc->pfGetLumaI4x4Pred[iCurMode] (pDst, pCurDec, kiLineSizeDec);
      iCostV = iCurCost = pFunc->sSampleDealingFuncs.pfMdCost[BLOCK_4x4] (pDst, 4, pCurEnc, kiLineSizeEnc) +
                          lambda[iPredMode == g_kiMapModeI4x4[iCurMode]];

      if (iCurCost < iBestCost) {
        iBestMode = iCurMode;
        iBestCost = iCurCost;
        iBestPredBufferNum = 1 - iBestPredBufferNum;
      }
      if (iCostV < iCostH) {
        if (iAvailCount == 9) {
          iBestModeFake = true; //indicating whether V is the best fake mode

          //I4_PRED_VR(5) and I4_PRED_VL(7)
          iCurMode = I4_PRED_VR;

          pDst = &pMbCache->pMemPredBlk4[ (1 - iBestPredBufferNum) << 4];

          pFunc->pfGetLumaI4x4Pred[iCurMode] (pDst, pCurDec, kiLineSizeDec);
          iCostVR = iCurCost = pFunc->sSampleDealingFuncs.pfMdCost[BLOCK_4x4] (pDst, 4, pCurEnc, kiLineSizeEnc) +
                               lambda[iPredMode == g_kiMapModeI4x4[iCurMode]];

          if (iCurCost < iBestCost) {
            iBestMode = iCurMode;
            iBestCost = iCurCost;
            iBestPredBufferNum = 1 - iBestPredBufferNum;
          }

          if (iCurCost < iCostV)
            iBestModeFake = false;

          iCurMode = I4_PRED_VL;

          pDst = &pMbCache->pMemPredBlk4[ (1 - iBestPredBufferNum) << 4];

          pFunc->pfGetLumaI4x4Pred[iCurMode] (pDst, pCurDec, kiLineSizeDec);
          iCostVL = iCurCost = pFunc->sSampleDealingFuncs.pfMdCost[BLOCK_4x4] (pDst, 4, pCurEnc, kiLineSizeEnc) +
                               lambda[iPredMode == g_kiMapModeI4x4[iCurMode]];

          if (iCurCost < iBestCost) {
            iBestMode = iCurMode;
            iBestCost = iCurCost;
            iBestPredBufferNum = 1 - iBestPredBufferNum;
          }

          if (iCurCost < iCostV)
            iBestModeFake = false;

          //Vertical Early Determination
          if (!iBestModeFake) { //Vertical is not the best, go on checking...
            //select the best one from VL and VR
            if (iCostVR < iCostVL) {
              //I4_PRED_DDR(4)
              iCurMode = I4_PRED_DDR;

              pDst = &pMbCache->pMemPredBlk4[ (1 - iBestPredBufferNum) << 4];

              pFunc->pfGetLumaI4x4Pred[iCurMode] (pDst, pCurDec, kiLineSizeDec);

              iCurCost = pFunc->sSampleDealingFuncs.pfMdCost[BLOCK_4x4] (pDst, 4, pCurEnc, kiLineSizeEnc) +
                         lambda[iPredMode == g_kiMapModeI4x4[iCurMode]];

              if (iCurCost < iBestCost) {
                iBestMode = iCurMode;
                iBestCost = iCurCost;
                iBestPredBufferNum = 1 - iBestPredBufferNum;
              }
            } else {
              //I4_PRED_DDL(3)
              iCurMode = I4_PRED_DDL;

              pDst = &pMbCache->pMemPredBlk4[ (1 - iBestPredBufferNum) << 4];

              pFunc->pfGetLumaI4x4Pred[iCurMode] (pDst, pCurDec, kiLineSizeDec);

              iCurCost = pFunc->sSampleDealingFuncs.pfMdCost[BLOCK_4x4] (pDst, 4, pCurEnc, kiLineSizeEnc) +
                         lambda[iPredMode == g_kiMapModeI4x4[iCurMode]];

              if (iCurCost < iBestCost) {
                iBestMode = iCurMode;
                iBestCost = iCurCost;
                iBestPredBufferNum = 1 - iBestPredBufferNum;
              }
            }
          }
        } else if (iAvailCount == 7) {
          iCurMode = I4_PRED_DDR;

          pDst = &pMbCache->pMemPredBlk4[ (1 - iBestPredBufferNum) << 4];

          pFunc->pfGetLumaI4x4Pred[iCurMode] (pDst, pCurDec, kiLineSizeDec);
          iCurCost = pFunc->sSampleDealingFuncs.pfMdCost[BLOCK_4x4] (pDst, 4, pCurEnc, kiLineSizeEnc) +
                     lambda[iPredMode == g_kiMapModeI4x4[iCurMode]];

          if (iCurCost < iBestCost) {
            iBestMode = iCurMode;
            iBestCost = iCurCost;
            iBestPredBufferNum = 1 - iBestPredBufferNum;
          }

          iCurMode = I4_PRED_VR;

          pDst = &pMbCache->pMemPredBlk4[ (1 - iBestPredBufferNum) << 4];

          pFunc->pfGetLumaI4x4Pred[iCurMode] (pDst, pCurDec, kiLineSizeDec);

          iCurCost = pFunc->sSampleDealingFuncs.pfMdCost[BLOCK_4x4] (pDst, 4, pCurEnc, kiLineSizeEnc) +
                     lambda[iPredMode == g_kiMapModeI4x4[iCurMode]];

          if (iCurCost < iBestCost) {
            iBestMode = iCurMode;
            iBestCost = iCurCost;
            iBestPredBufferNum = 1 - iBestPredBufferNum;
          }
        }
      } else {
        iBestModeFake = true; //indicating whether H is the best fake mode
        //I4_PRED_HD(6) and I4_PRED_HU(8)
        iCurMode = I4_PRED_HD;

        pDst = &pMbCache->pMemPredBlk4[ (1 - iBestPredBufferNum) << 4];

        pFunc->pfGetLumaI4x4Pred[iCurMode] (pDst, pCurDec, kiLineSizeDec);
        iCostHD = iCurCost = pFunc->sSampleDealingFuncs.pfMdCost[BLOCK_4x4] (pDst, 4, pCurEnc, kiLineSizeEnc) +
                             lambda[iPredMode == g_kiMapModeI4x4[iCurMode]];

        if (iCurCost < iBestCost) {
          iBestMode = iCurMode;
          iBestCost = iCurCost;
          iBestPredBufferNum = 1 - iBestPredBufferNum;
        }

        if (iCurCost < iCostH)
          iBestModeFake = false;

        iCurMode = I4_PRED_HU;

        pDst = &pMbCache->pMemPredBlk4[ (1 - iBestPredBufferNum) << 4];

        pFunc->pfGetLumaI4x4Pred[iCurMode] (pDst, pCurDec, kiLineSizeDec);
        iCostHU = iCurCost = pFunc->sSampleDealingFuncs.pfMdCost[BLOCK_4x4] (pDst, 4, pCurEnc, kiLineSizeEnc) +
                             lambda[iPredMode == g_kiMapModeI4x4[iCurMode]];

        if (iCurCost < iBestCost) {
          iBestMode = iCurMode;
          iBestCost = iCurCost;
          iBestPredBufferNum = 1 - iBestPredBufferNum;
        }

        if (iCurCost < iCostH)
          iBestModeFake = false;

        if (!iBestModeFake) { //Horizontal is not the best, go on checking...
          //select the best one from VL and VR
          if (iCostHD < iCostHU) {
            //I4_PRED_DDR(4)
            iCurMode = I4_PRED_DDR;

            pDst = &pMbCache->pMemPredBlk4[ (1 - iBestPredBufferNum) << 4];

            pFunc->pfGetLumaI4x4Pred[iCurMode] (pDst, pCurDec, kiLineSizeDec);
            iCurCost = pFunc->sSampleDealingFuncs.pfMdCost[BLOCK_4x4] (pDst, 4, pCurEnc, kiLineSizeEnc) +
                       lambda[iPredMode == g_kiMapModeI4x4[iCurMode]];

            if (iCurCost < iBestCost) {
              iBestMode = iCurMode;
              iBestCost = iCurCost;
              iBestPredBufferNum = 1 - iBestPredBufferNum;
            }
          } else if (iAvailCount == 9) {
            //I4_PRED_DDL(3)
            iCurMode = I4_PRED_DDL;

            pDst = &pMbCache->pMemPredBlk4[ (1 - iBestPredBufferNum) << 4];
            pFunc->pfGetLumaI4x4Pred[iCurMode] (pDst, pCurDec, kiLineSizeDec);

            iCurCost = pFunc->sSampleDealingFuncs.pfMdCost[BLOCK_4x4] (pDst, 4, pCurEnc, kiLineSizeEnc) +
                       lambda[iPredMode == g_kiMapModeI4x4[iCurMode]];

            if (iCurCost < iBestCost) {
              iBestMode = iCurMode;
              iBestCost = iCurCost;
              iBestPredBufferNum = 1 - iBestPredBufferNum;
            }

          }
        }
      }
    } else {
      iBestCost = INT_MAX;
      iBestMode = I4_PRED_INVALID;
      for (j = 0; j < iAvailCount; j++) {
        // I4x4_MODE_CHECK(pAvailMode[j], iCurCost);
        iCurMode = kpAvailMode[j];

        pDst = &pMbCache->pMemPredBlk4[ (1 - iBestPredBufferNum) << 4];

        pFunc->pfGetLumaI4x4Pred[iCurMode] (pDst, pCurDec, kiLineSizeDec);
        iCurCost = pFunc->sSampleDealingFuncs.pfMdCost[BLOCK_4x4] (pDst, 4, pCurEnc, kiLineSizeEnc) +
                   lambda[iPredMode == g_kiMapModeI4x4[iCurMode]];

        if (iCurCost < iBestCost) {
          iBestMode = iCurMode;
          iBestCost = iCurCost;
          iBestPredBufferNum = 1 - iBestPredBufferNum;
        }
      }
    }
    pMbCache->pBestPredI4x4Blk4 = &pMbCache->pMemPredBlk4[iBestPredBufferNum << 4];
    iCosti4x4 += iBestCost;
    if (iCosti4x4 >= iBestCostLuma) {
      break;
    }

    //step 5: update pred mode and sample avail cache
    iFinalMode = g_kiMapModeI4x4[iBestMode];
    if (iPredMode == iFinalMode) {
      *pPrevIntra4x4PredModeFlag++ = true;
    } else {
      *pPrevIntra4x4PredModeFlag++ = false;
      *pRemIntra4x4PredModeFlag  = (iFinalMode < iPredMode ? iFinalMode : (iFinalMode - 1));
    }
    pRemIntra4x4PredModeFlag++;
    // pCurMb->pIntra4x4PredMode[scan4[i]] = iFinalMode;
    pMbCache->iIntraPredMode[kpCache48CountScan4[i]] = iFinalMode;
    //step 6: encoding I_4x4
    WelsEncRecI4x4Y (pEncCtx, pCurMb, pMbCache, i);
  }
  ST32 (pCurMb->pIntra4x4PredMode, LD32 (&pMbCache->iIntraPredMode[33]));
  pCurMb->pIntra4x4PredMode[4] = pMbCache->iIntraPredMode[12];
  pCurMb->pIntra4x4PredMode[5] = pMbCache->iIntraPredMode[20];
  pCurMb->pIntra4x4PredMode[6] = pMbCache->iIntraPredMode[28];
  iCosti4x4 += (iLambda << 4) + (iLambda << 3); //4*6*lambda from JVT SATD0
  return iCosti4x4;
}

int32_t WelsMdIntraChroma (SWelsFuncPtrList* pFunc, SDqLayer* pCurDqLayer, SMbCache* pMbCache, int32_t iLambda) {
  const int8_t* kpAvailMode;
  int32_t iAvailCount = 0;
  int32_t iChmaIdx = 0;
  uint8_t* pPredIntraChma[2]    = {pMbCache->pMemPredChroma, pMbCache->pMemPredChroma + 128};
  uint8_t* pDstChma             = pPredIntraChma[0];
  uint8_t* pEncCb               = pMbCache->SPicData.pEncMb[1];
  uint8_t* pEncCr               = pMbCache->SPicData.pEncMb[2];
  uint8_t* pDecCb               = pMbCache->SPicData.pCsMb[1];//pMbCache->SPicData.pDecMb[1];
  uint8_t* pDecCr               = pMbCache->SPicData.pCsMb[2];//pMbCache->SPicData.pDecMb[2];
  const int32_t kiLineSizeEnc   = pCurDqLayer->iEncStride[1];
  const int32_t kiLineSizeDec   = pCurDqLayer->iCsStride[1];//pMbCache->SPicData.i_stride_dec[1];

  int32_t i, iCurMode, iCurCost, iBestMode, iBestCost = INT_MAX;

  int32_t iOffset = pMbCache->uiNeighborIntra & 0x07;
  iAvailCount = g_kiIntraChromaAvailMode[iOffset][4];
  kpAvailMode = g_kiIntraChromaAvailMode[iOffset];
  if (iAvailCount > 3 && pFunc->sSampleDealingFuncs.pfIntra8x8Combined3) {
    iBestCost = pFunc->sSampleDealingFuncs.pfIntra8x8Combined3 (pDecCb, kiLineSizeDec, pEncCb, kiLineSizeEnc, &iBestMode,
                iLambda, pDstChma, pDecCr, pEncCr);
    iCurMode = kpAvailMode[3];
    pFunc->pfGetChromaPred[iCurMode] (pDstChma, pDecCb, kiLineSizeDec); //Cb
    pFunc->pfGetChromaPred[iCurMode] (pDstChma + 64, pDecCr, kiLineSizeDec); //Cr

    iCurCost = pFunc->sSampleDealingFuncs.pfMdCost[BLOCK_8x8] (pDstChma, 8, pEncCb, kiLineSizeEnc) +
               pFunc->sSampleDealingFuncs.pfMdCost[BLOCK_8x8] (pDstChma + 64, 8, pEncCr, kiLineSizeEnc) +
               iLambda * 4;
    if (iCurCost < iBestCost) {
      iBestMode = iCurMode;
      iBestCost = iCurCost;
    } else {
      pFunc->pfGetChromaPred[iBestMode] (pDstChma, pDecCb, kiLineSizeDec); //Cb
      pFunc->pfGetChromaPred[iBestMode] (pDstChma + 64, pDecCr, kiLineSizeDec); //Cr
    }
    iBestCost += iLambda;
    iChmaIdx = 1;
  } else {
    iBestMode = kpAvailMode[0];
    for (i = 0; i < iAvailCount; ++ i) {
      iCurMode = kpAvailMode[i];

      assert (iCurMode >= 0 && iCurMode < 7);

      // pDstCb = &pMbCache->mem_pred_intra_cb[iCurMode<<6];
      // pDstCr = &pMbCache->mem_pred_intra_cr[iCurMode<<6];
      pFunc->pfGetChromaPred[iCurMode] (pDstChma, pDecCb, kiLineSizeDec); //Cb
      iCurCost = pFunc->sSampleDealingFuncs.pfMdCost[BLOCK_8x8] (pDstChma, 8, pEncCb, kiLineSizeEnc);

      pFunc->pfGetChromaPred[iCurMode] (pDstChma + 64, pDecCr, kiLineSizeDec); //Cr
      iCurCost += pFunc->sSampleDealingFuncs.pfMdCost[BLOCK_8x8] (pDstChma + 64, 8, pEncCr, kiLineSizeEnc) +
                  iLambda * BsSizeUE (g_kiMapModeIntraChroma[iCurMode]);
      if (iCurCost < iBestCost) {
        iBestMode = iCurMode;
        iBestCost = iCurCost;
        iChmaIdx = iChmaIdx ^ 0x01;
        pDstChma = pPredIntraChma[iChmaIdx];
      }
    }
  }

  pMbCache->pBestPredIntraChroma = pPredIntraChma[iChmaIdx ^ 0x01];
  pMbCache->uiChmaI8x8Mode = iBestMode;
  return iBestCost;
}
int32_t WelsMdIntraFinePartition (sWelsEncCtx* pEncCtx, SWelsMD* pWelsMd, SMB* pCurMb, SMbCache* pMbCache) {
  int32_t iCosti4x4 = WelsMdI4x4 (pEncCtx, pWelsMd, pCurMb, pMbCache);

  if (iCosti4x4 < pWelsMd->iCostLuma) {
    pCurMb->uiMbType = MB_TYPE_INTRA4x4;
    pWelsMd->iCostLuma = iCosti4x4;
  }
  return pWelsMd->iCostLuma;
}

int32_t WelsMdIntraFinePartitionVaa (sWelsEncCtx* pEncCtx, SWelsMD* pWelsMd, SMB* pCurMb, SMbCache* pMbCache) {

  if (MdIntraAnalysisVaaInfo (pEncCtx, pMbCache->SPicData.pEncMb[0])) {
    int32_t iCosti4x4 = WelsMdI4x4Fast (pEncCtx, pWelsMd, pCurMb, pMbCache);

    if (iCosti4x4 < pWelsMd->iCostLuma) {
      pCurMb->uiMbType = MB_TYPE_INTRA4x4;
      pWelsMd->iCostLuma = iCosti4x4;
    }
  }

  return pWelsMd->iCostLuma;
}

void WelsMdIntraMb (sWelsEncCtx* pEncCtx, SWelsMD* pWelsMd, SMB* pCurMb, SMbCache* pMbCache) {
  //initial prediction memory for I_16x16
  pWelsMd->iCostLuma = WelsMdI16x16 (pEncCtx->pFuncList, pEncCtx->pCurDqLayer, pMbCache, pWelsMd->iLambda);
  pCurMb->uiMbType = MB_TYPE_INTRA16x16;

  WelsMdIntraSecondaryModesEnc (pEncCtx, pWelsMd, pCurMb, pMbCache);
}

static inline void InitMe (const SWelsMD& sWelsMd, const int32_t iBlockSize, uint8_t* pEnc, uint8_t* pRef,
                           SScreenBlockFeatureStorage* pRefFeatureStorage,
                           SWelsME& sWelsMe) {
  sWelsMe.iCurMeBlockPixX = sWelsMd.iMbPixX;
  sWelsMe.iCurMeBlockPixY = sWelsMd.iMbPixY;
  sWelsMe.uiBlockSize = iBlockSize;
  sWelsMe.pMvdCost = sWelsMd.pMvdCost;

  sWelsMe.pEncMb = pEnc;
  sWelsMe.pRefMb = sWelsMe.pColoRefMb = pRef;

  sWelsMe.pRefFeatureStorage = pRefFeatureStorage;
}

int32_t WelsMdP16x16 (SWelsFuncPtrList* pFunc, SDqLayer* pCurLayer, SWelsMD* pWelsMd, SSlice* pSlice, SMB* pCurMb) {
  SMbCache* pMbCache = &pSlice->sMbCacheInfo;
  SWelsME* pMe16x16 = &pWelsMd->sMe.sMe16x16;
  uint32_t uiNeighborAvail = pCurMb->uiNeighborAvail;
  const int32_t kiMbWidth  = pCurLayer->iMbWidth;  // for assign once
  const int32_t kiMbHeight = pCurLayer->iMbHeight;
  InitMe (*pWelsMd, BLOCK_16x16, pMbCache->SPicData.pEncMb[0], pMbCache->SPicData.pRefMb[0],
          pCurLayer->pRefPic->pScreenBlockFeatureStorage,
          *pMe16x16);
  //not putting the line below into InitMe to avoid judging mode in InitMe
  pMe16x16->uSadPredISatd.uiSadPred = pWelsMd->iSadPredMb;

  pSlice->uiMvcNum = 0;
  pSlice->sMvc[pSlice->uiMvcNum++] = pMe16x16->sMvBase;
  //spatial motion vector predictors
  if (uiNeighborAvail & LEFT_MB_POS) { //left available
    pSlice->sMvc[pSlice->uiMvcNum++] = (pCurMb - 1)->sP16x16Mv;
  }
  if (uiNeighborAvail & TOP_MB_POS) { //top available
    pSlice->sMvc[pSlice->uiMvcNum++] = (pCurMb - kiMbWidth)->sP16x16Mv;
  }
  //temporal motion vector predictors
  if (pCurLayer->pRefPic->iPictureType == P_SLICE) {
    if (pCurMb->iMbX < kiMbWidth - 1) {
      SMVUnitXY sTempMv = pCurLayer->pRefPic->sMvList[pCurMb->iMbXY + 1];
      pSlice->sMvc[pSlice->uiMvcNum].iMvX = sTempMv.iMvX >> pSlice->sScaleShift;
      pSlice->sMvc[pSlice->uiMvcNum].iMvY = sTempMv.iMvY >> pSlice->sScaleShift;
      ++ pSlice->uiMvcNum;
    }
    if (pCurMb->iMbY < kiMbHeight - 1) {
      SMVUnitXY sTempMv = pCurLayer->pRefPic->sMvList[pCurMb->iMbXY + kiMbWidth];
      pSlice->sMvc[pSlice->uiMvcNum].iMvX = sTempMv.iMvX >> pSlice->sScaleShift;
      pSlice->sMvc[pSlice->uiMvcNum].iMvY = sTempMv.iMvY >> pSlice->sScaleShift;
      ++ pSlice->uiMvcNum;
    }
  }

  PredMv (&pMbCache->sMvComponents, 0, 4, 0, & (pMe16x16->sMvp));
  pFunc->pfMotionSearch[0] (pFunc, pCurLayer, pMe16x16, pSlice);

  pCurMb->sP16x16Mv = pMe16x16->sMv;
  pCurLayer->pDecPic->sMvList[pCurMb->iMbXY] = pMe16x16->sMv;

  return pMe16x16->uiSatdCost;
}
int32_t WelsMdP16x8 (SWelsFuncPtrList* pFunc, SDqLayer* pCurDqLayer, SWelsMD* pWelsMd, SSlice* pSlice) {
  SMbCache* pMbCache = &pSlice->sMbCacheInfo;
  int32_t iStrideEnc = pCurDqLayer->iEncStride[0];
  int32_t iStrideRef = pCurDqLayer->pRefPic->iLineSize[0];
  SWelsME* sMe16x8;
  int32_t i = 0, iPixelY;
  int32_t iCostP16x8 = 0;
  do {
    sMe16x8 = &pWelsMd->sMe.sMe16x8[i];
    iPixelY = (i << 3);
    InitMe (*pWelsMd, BLOCK_16x8,
            pMbCache->SPicData.pEncMb[0] + (iPixelY * iStrideEnc),
            pMbCache->SPicData.pRefMb[0] + (iPixelY * iStrideRef),
            pCurDqLayer->pRefPic->pScreenBlockFeatureStorage,
            *sMe16x8);
    //not putting the lines below into InitMe to avoid judging mode in InitMe
    sMe16x8->iCurMeBlockPixY = pWelsMd->iMbPixY + iPixelY;
    sMe16x8->uSadPredISatd.uiSadPred = pWelsMd->iSadPredMb >> 1;

    pSlice->sMvc[0] = sMe16x8->sMvBase;
    pSlice->uiMvcNum = 1;

    PredInter16x8Mv (pMbCache, i << 3, 0, & (sMe16x8->sMvp));
    pFunc->pfMotionSearch[0] (pFunc, pCurDqLayer, sMe16x8, pSlice);
    UpdateP16x8Motion2Cache (pMbCache, i << 3, pWelsMd->uiRef, & (sMe16x8->sMv));
    iCostP16x8 += sMe16x8->uiSatdCost;
    ++i;
  } while (i < 2);
  return iCostP16x8;
}
int32_t WelsMdP8x16 (SWelsFuncPtrList* pFunc, SDqLayer* pCurLayer, SWelsMD* pWelsMd, SSlice* pSlice) {
  SMbCache* pMbCache = &pSlice->sMbCacheInfo;
  SWelsME* sMe8x16;
  int32_t i = 0, iPixelX;
  int32_t iCostP8x16 = 0;
  do {
    iPixelX = (i << 3);
    sMe8x16 = &pWelsMd->sMe.sMe8x16[i];
    InitMe (*pWelsMd, BLOCK_8x16,
            pMbCache->SPicData.pEncMb[0] + iPixelX,
            pMbCache->SPicData.pRefMb[0] + iPixelX,
            pCurLayer->pRefPic->pScreenBlockFeatureStorage,
            *sMe8x16);
    //not putting the lines below into InitMe to avoid judging mode in InitMe
    sMe8x16->iCurMeBlockPixX = pWelsMd->iMbPixX + iPixelX;
    sMe8x16->uSadPredISatd.uiSadPred = pWelsMd->iSadPredMb >> 1;

    pSlice->sMvc[0] = sMe8x16->sMvBase;
    pSlice->uiMvcNum = 1;

    PredInter8x16Mv (pMbCache, i << 2, 0, & (sMe8x16->sMvp));
    pFunc->pfMotionSearch[0] (pFunc, pCurLayer, sMe8x16, pSlice);
    UpdateP8x16Motion2Cache (pMbCache, i << 2, pWelsMd->uiRef, & (sMe8x16->sMv));
    iCostP8x16 += sMe8x16->uiSatdCost;
    ++i;
  } while (i < 2);
  return iCostP8x16;
}
int32_t WelsMdP8x8 (SWelsFuncPtrList* pFunc, SDqLayer* pCurDqLayer, SWelsMD* pWelsMd, SSlice* pSlice) {
  SMbCache* pMbCache = &pSlice->sMbCacheInfo;
  int32_t iLineSizeEnc = pCurDqLayer->iEncStride[0];
  int32_t iLineSizeRef = pCurDqLayer->pRefPic->iLineSize[0];
  SWelsME* sMe8x8;
  int32_t i, iIdxX, iIdxY, iPixelX, iPixelY, iStrideEnc, iStrideRef;
  int32_t iCostP8x8 = 0;
  for (i = 0; i < 4; i++) {
    iIdxX = i & 1;
    iIdxY = i >> 1;
    iPixelX = (iIdxX << 3);
    iPixelY = (iIdxY << 3);
    iStrideEnc = iPixelX + (iPixelY * iLineSizeEnc);
    iStrideRef = iPixelX + (iPixelY * iLineSizeRef);

    sMe8x8 = &pWelsMd->sMe.sMe8x8[i];
    InitMe (*pWelsMd, BLOCK_8x8,
            pMbCache->SPicData.pEncMb[0] + iStrideEnc,
            pMbCache->SPicData.pRefMb[0] + iStrideRef,
            pCurDqLayer->pRefPic->pScreenBlockFeatureStorage,
            *sMe8x8);
    //not putting these three lines below into InitMe to avoid judging mode in InitMe
    sMe8x8->iCurMeBlockPixX = pWelsMd->iMbPixX + iPixelX;
    sMe8x8->iCurMeBlockPixY = pWelsMd->iMbPixY + iPixelY;
    sMe8x8->uSadPredISatd.uiSadPred = pWelsMd->iSadPredMb >> 2;


    pSlice->sMvc[0] = sMe8x8->sMvBase;
    pSlice->uiMvcNum = 1;

    PredMv (&pMbCache->sMvComponents, i << 2, 2, pWelsMd->uiRef, & (sMe8x8->sMvp));
    pFunc->pfMotionSearch[pWelsMd->iBlock8x8StaticIdc[i]] (pFunc, pCurDqLayer, sMe8x8, pSlice);
    UpdateP8x8Motion2Cache (pMbCache, i << 2, pWelsMd->uiRef, & (sMe8x8->sMv));
    iCostP8x8 += sMe8x8->uiSatdCost;
//    sMe8x8++;
  }
  return iCostP8x8;
}

int32_t WelsMdP4x4 (SWelsFuncPtrList* pFunc, SDqLayer* pCurDqLayer, SWelsMD* pWelsMd, SSlice* pSlice,
                    const int32_t ki8x8Idx) {
  SMbCache* pMbCache = &pSlice->sMbCacheInfo;
  int32_t iLineSizeEnc = pCurDqLayer->iEncStride[0];
  int32_t iLineSizeRef = pCurDqLayer->pRefPic->iLineSize[0];
  SWelsME* sMe4x4;
  int32_t i4x4Idx, iIdxX, iIdxY, iPixelX, iPixelY, iStrideEnc, iStrideRef;
  int32_t iCostP4x4 = 0;
  for (i4x4Idx = 0; i4x4Idx < 4; ++i4x4Idx) {
    int32_t iPartIdx = (ki8x8Idx << 2) + i4x4Idx;
    iIdxX = ((ki8x8Idx & 1) << 1) + (i4x4Idx & 1);
    iIdxY = ((ki8x8Idx >> 1) << 1) + (i4x4Idx >> 1);
    iPixelX = (iIdxX << 2);
    iPixelY = (iIdxY << 2);
    iStrideEnc = iPixelX + (iPixelY * iLineSizeEnc);
    iStrideRef = iPixelX + (iPixelY * iLineSizeRef);

    sMe4x4 = &pWelsMd->sMe.sMe4x4[ki8x8Idx][i4x4Idx];
    InitMe (*pWelsMd, BLOCK_4x4,
            pMbCache->SPicData.pEncMb[0] + iStrideEnc,
            pMbCache->SPicData.pRefMb[0] + iStrideRef,
            pCurDqLayer->pRefPic->pScreenBlockFeatureStorage,
            *sMe4x4);
    //not putting these three lines below into InitMe to avoid judging mode in InitMe
    sMe4x4->iCurMeBlockPixX = pWelsMd->iMbPixX + iPixelX;
    sMe4x4->iCurMeBlockPixY = pWelsMd->iMbPixY + iPixelY;
    sMe4x4->uSadPredISatd.uiSadPred = pWelsMd->iSadPredMb >> 2;

    pSlice->sMvc[0] = sMe4x4->sMvBase;
    pSlice->uiMvcNum = 1;

    PredMv (&pMbCache->sMvComponents, iPartIdx, 1, pWelsMd->uiRef, & (sMe4x4->sMvp));
    pFunc->pfMotionSearch[0] (pFunc, pCurDqLayer, sMe4x4, pSlice);
    UpdateP4x4Motion2Cache (pMbCache, iPartIdx, pWelsMd->uiRef, & (sMe4x4->sMv));
    iCostP4x4 += sMe4x4->uiSatdCost;
  }
  return iCostP4x4;
}

int32_t WelsMdP8x4 (SWelsFuncPtrList* pFunc, SDqLayer* pCurDqLayer, SWelsMD* pWelsMd, SSlice* pSlice,
                    const int32_t ki8x8Idx) {
  SMbCache* pMbCache = &pSlice->sMbCacheInfo;
  int32_t iLineSizeEnc = pCurDqLayer->iEncStride[0];
  int32_t iLineSizeRef = pCurDqLayer->pRefPic->iLineSize[0];
  SWelsME* sMe8x4;
  int32_t i8x4Idx, iIdxX, iIdxY, iPixelX, iPixelY, iStrideEnc, iStrideRef;
  int32_t iCostP8x4 = 0;
  for (i8x4Idx = 0; i8x4Idx < 2; ++i8x4Idx) {
    int32_t iPartIdx = (ki8x8Idx << 2) + (i8x4Idx << 1);
    iIdxX = ((ki8x8Idx & 1) << 1);
    iIdxY = ((ki8x8Idx >> 1) << 1) + i8x4Idx;
    iPixelX = (iIdxX << 2);
    iPixelY = (iIdxY << 2);
    iStrideEnc = iPixelX + (iPixelY * iLineSizeEnc);
    iStrideRef = iPixelX + (iPixelY * iLineSizeRef);

    sMe8x4 = &pWelsMd->sMe.sMe8x4[ki8x8Idx][i8x4Idx];
    InitMe (*pWelsMd, BLOCK_8x4,
            pMbCache->SPicData.pEncMb[0] + iStrideEnc,
            pMbCache->SPicData.pRefMb[0] + iStrideRef,
            pCurDqLayer->pRefPic->pScreenBlockFeatureStorage,
            *sMe8x4);
    //not putting these three lines below into InitMe to avoid judging mode in InitMe
    sMe8x4->iCurMeBlockPixX = pWelsMd->iMbPixX + iPixelX;
    sMe8x4->iCurMeBlockPixY = pWelsMd->iMbPixY + iPixelY;
    sMe8x4->uSadPredISatd.uiSadPred = pWelsMd->iSadPredMb >> 2;

    pSlice->sMvc[0] = sMe8x4->sMvBase;
    pSlice->uiMvcNum = 1;

    PredMv (&pMbCache->sMvComponents, iPartIdx, 2, pWelsMd->uiRef, & (sMe8x4->sMvp));
    pFunc->pfMotionSearch[0] (pFunc, pCurDqLayer, sMe8x4, pSlice);
    UpdateP8x4Motion2Cache (pMbCache, iPartIdx, pWelsMd->uiRef, & (sMe8x4->sMv));
    iCostP8x4 += sMe8x4->uiSatdCost;
  }
  return iCostP8x4;
}

int32_t WelsMdP4x8 (SWelsFuncPtrList* pFunc, SDqLayer* pCurDqLayer, SWelsMD* pWelsMd, SSlice* pSlice,
                    const int32_t ki8x8Idx) {
  //Wayne, to be modified
  SMbCache* pMbCache = &pSlice->sMbCacheInfo;
  int32_t iLineSizeEnc = pCurDqLayer->iEncStride[0];
  int32_t iLineSizeRef = pCurDqLayer->pRefPic->iLineSize[0];
  SWelsME* sMe4x8;
  int32_t i4x8Idx, iIdxX, iIdxY, iPixelX, iPixelY, iStrideEnc, iStrideRef;
  int32_t iCostP4x8 = 0;
  for (i4x8Idx = 0; i4x8Idx < 2; ++i4x8Idx) {
    int32_t iPartIdx = (ki8x8Idx << 2) + i4x8Idx;
    iIdxX = ((ki8x8Idx & 1) << 1) + i4x8Idx;
    iIdxY = ((ki8x8Idx >> 1) << 1);
    iPixelX = (iIdxX << 2);
    iPixelY = (iIdxY << 2);
    iStrideEnc = iPixelX + (iPixelY * iLineSizeEnc);
    iStrideRef = iPixelX + (iPixelY * iLineSizeRef);

    sMe4x8 = &pWelsMd->sMe.sMe4x8[ki8x8Idx][i4x8Idx];
    InitMe (*pWelsMd, BLOCK_4x8,
            pMbCache->SPicData.pEncMb[0] + iStrideEnc,
            pMbCache->SPicData.pRefMb[0] + iStrideRef,
            pCurDqLayer->pRefPic->pScreenBlockFeatureStorage,
            *sMe4x8);
    //not putting these three lines below into InitMe to avoid judging mode in InitMe
    sMe4x8->iCurMeBlockPixX = pWelsMd->iMbPixX + iPixelX;
    sMe4x8->iCurMeBlockPixY = pWelsMd->iMbPixY + iPixelY;
    sMe4x8->uSadPredISatd.uiSadPred = pWelsMd->iSadPredMb >> 2;

    pSlice->sMvc[0] = sMe4x8->sMvBase;
    pSlice->uiMvcNum = 1;

    PredMv (&pMbCache->sMvComponents, iPartIdx, 1, pWelsMd->uiRef, & (sMe4x8->sMvp));
    pFunc->pfMotionSearch[0] (pFunc, pCurDqLayer, sMe4x8, pSlice);
    UpdateP4x8Motion2Cache (pMbCache, iPartIdx, pWelsMd->uiRef, & (sMe4x8->sMv));
    iCostP4x8 += sMe4x8->uiSatdCost;
  }
  return iCostP4x8;
}

void WelsMdInterFinePartition (sWelsEncCtx* pEncCtx, SWelsMD* pWelsMd, SSlice* pSlice, SMB* pCurMb, int32_t iBestCost) {
  SDqLayer* pCurDqLayer = pEncCtx->pCurDqLayer;
//  SMbCache *pMbCache = &pSlice->sMbCacheInfo;
  int32_t iCost = 0;

//  WelsLog( pEncCtx, WELS_LOG_INFO, "WelsMdP8x8, p_ref[0]= 0x%p", pMbCache->SPicData.pRefMb[0]);

  iCost = WelsMdP8x8 (pEncCtx->pFuncList, pCurDqLayer, pWelsMd, pSlice);

  if (iCost < iBestCost) {
    int32_t iCostPart;
    pCurMb->uiMbType = MB_TYPE_8x8;
    memset (pCurMb->uiSubMbType, SUB_MB_TYPE_8x8, 4);

//    WelsLog( pEncCtx, WELS_LOG_INFO, "WelsMdP16x8, p_ref[0]= 0x%p", pMbCache->SPicData.pRefMb[0]);
    iCostPart = WelsMdP16x8 (pEncCtx->pFuncList, pCurDqLayer, pWelsMd, pSlice);
    if (iCostPart <= iCost) {
      iCost = iCostPart;
      pCurMb->uiMbType = MB_TYPE_16x8;
      //pCurMb->mb_partition = 2;
    }

//    WelsLog( pEncCtx, WELS_LOG_INFO, "WelsMdP8x16, p_ref[0]= 0x%p", pMbCache->SPicData.pRefMb[0]);
    iCostPart = WelsMdP8x16 (pEncCtx->pFuncList, pCurDqLayer, pWelsMd, pSlice);
    if (iCostPart <= iCost) {
      iCost = iCostPart;
      pCurMb->uiMbType = MB_TYPE_8x16;
      //pCurMb->mb_partition = 2;
    }
  }
}

void WelsMdInterFinePartitionVaa (sWelsEncCtx* pEncCtx, SWelsMD* pWelsMd, SSlice* pSlice, SMB* pCurMb,
                                  int32_t iBestCost) {
  SDqLayer* pCurDqLayer = pEncCtx->pCurDqLayer;
//  SMbCache *pMbCache = &pSlice->sMbCacheInfo;
  int32_t iCostP8x16, iCostP16x8, iCostP8x8;
  uint8_t uiMbSign = pEncCtx->pFuncList->pfGetMbSignFromInterVaa (&pEncCtx->pVaa->sVaaCalcInfo.pSad8x8[pCurMb->iMbXY][0]);

  if (uiMbSign == 15) {
    return;
  }

//  iCost = pWelsMd->sMe16x16.uiSatdCost;

  switch (uiMbSign) {
  case 3:
  case 12:
//    WelsLog( pEncCtx, WELS_LOG_INFO, "WelsMdP16x8, p_ref[0]= 0x%p", pMbCache->SPicData.pRefMb[0]);
    iCostP16x8 = WelsMdP16x8 (pEncCtx->pFuncList, pCurDqLayer, pWelsMd, pSlice);
    if (iCostP16x8 < iBestCost) {
      iBestCost = iCostP16x8;
      pCurMb->uiMbType = MB_TYPE_16x8;
      //pCurMb->mb_partition = 2;
    }
    break;

  case 5:
  case 10:
//    WelsLog( pEncCtx, WELS_LOG_INFO, "WelsMdP8x16, p_ref[0]= 0x%p", pMbCache->SPicData.pRefMb[0]);
    iCostP8x16 = WelsMdP8x16 (pEncCtx->pFuncList, pCurDqLayer, pWelsMd, pSlice);
    if (iCostP8x16 < iBestCost) {
      iBestCost = iCostP8x16;
      pCurMb->uiMbType = MB_TYPE_8x16;
      //pCurMb->mb_partition = 2;
    }
    break;

  case 6:
  case 9:
    iCostP8x8 = WelsMdP8x8 (pEncCtx->pFuncList, pCurDqLayer, pWelsMd, pSlice);
    if (iCostP8x8 < iBestCost) {
      iBestCost = iCostP8x8;
      pCurMb->uiMbType = MB_TYPE_8x8;
      memset (pCurMb->uiSubMbType, SUB_MB_TYPE_8x8, 4);
    }
    break;

  default:
    iCostP8x8 = WelsMdP8x8 (pEncCtx->pFuncList, pCurDqLayer, pWelsMd, pSlice);
    if (iCostP8x8 < iBestCost) {
      iBestCost = iCostP8x8;
      pCurMb->uiMbType = MB_TYPE_8x8;
      memset (pCurMb->uiSubMbType, SUB_MB_TYPE_8x8, 4);

      iCostP16x8 = WelsMdP16x8 (pEncCtx->pFuncList, pCurDqLayer, pWelsMd, pSlice);
      if (iCostP16x8 <= iBestCost) {
        iBestCost = iCostP16x8;
        pCurMb->uiMbType = MB_TYPE_16x8;
      }

      iCostP8x16 = WelsMdP8x16 (pEncCtx->pFuncList, pCurDqLayer, pWelsMd, pSlice);
      if (iCostP8x16 <= iBestCost) {
        iBestCost = iCostP8x16;
        pCurMb->uiMbType = MB_TYPE_8x16;
      }
    }
    break;
  }
  pWelsMd->iCostLuma = iBestCost;
}


inline void VaaBackgroundMbDataUpdate (SWelsFuncPtrList* pFunc, SVAAFrameInfo* pVaaInfo, SMB* pCurMb) {
  const int32_t kiPicStride     = pVaaInfo->iPicStride;
  const int32_t kiPicStrideUV   = pVaaInfo->iPicStrideUV;
  const int32_t kiOffsetY       = (pCurMb->iMbY * kiPicStride + pCurMb->iMbX) << 4;
  const int32_t kiOffsetUV      = (pCurMb->iMbY * kiPicStrideUV + pCurMb->iMbX) << 3;

  pFunc->pfCopy16x16Aligned (pVaaInfo->pCurY + kiOffsetY, kiPicStride, pVaaInfo->pRefY + kiOffsetY, kiPicStride);
  pFunc->pfCopy8x8Aligned (pVaaInfo->pCurU + kiOffsetUV, kiPicStrideUV, pVaaInfo->pRefU + kiOffsetUV, kiPicStrideUV);
  pFunc->pfCopy8x8Aligned (pVaaInfo->pCurV + kiOffsetUV, kiPicStrideUV, pVaaInfo->pRefV + kiOffsetUV, kiPicStrideUV);
}

void WelsMdBackgroundMbEnc (sWelsEncCtx* pEncCtx, SWelsMD* pWelsMd, SMB* pCurMb, SMbCache* pMbCache, SSlice* pSlice,
                            bool bSkipMbFlag) {
  SDqLayer* pCurDqLayer         = pEncCtx->pCurDqLayer;
  SWelsFuncPtrList* pFunc       = pEncCtx->pFuncList;
  SMVUnitXY sMvp                = { 0 };
  uint8_t* pRefLuma             = pMbCache->SPicData.pRefMb[0];
  uint8_t* pRefCb               = pMbCache->SPicData.pRefMb[1];
  uint8_t* pRefCr               = pMbCache->SPicData.pRefMb[2];
  int32_t iLineSizeY            = pCurDqLayer->pRefPic->iLineSize[0];
  int32_t iLineSizeUV           = pCurDqLayer->pRefPic->iLineSize[1];
  uint8_t* pDstLuma             = pMbCache->pSkipMb;
  uint8_t* pDstCb               = pMbCache->pSkipMb + 256;
  uint8_t* pDstCr               = pMbCache->pSkipMb + 256 + 64;

  if (!bSkipMbFlag) {
    pDstLuma    = pMbCache->pMemPredLuma;
    pDstCb      = pMbCache->pMemPredChroma;
    pDstCr      = pMbCache->pMemPredChroma + 64;
  }
  //MC
  pFunc->sMcFuncs.pMcLumaFunc (pRefLuma, iLineSizeY, pDstLuma, 16, 0, 0, 16, 16);
  pFunc->sMcFuncs.pMcChromaFunc (pRefCb, iLineSizeUV, pDstCb, 8, sMvp.iMvX, sMvp.iMvY, 8, 8); //Cb
  pFunc->sMcFuncs.pMcChromaFunc (pRefCr, iLineSizeUV, pDstCr, 8, sMvp.iMvX, sMvp.iMvY, 8, 8); //Cr

  pCurMb->uiCbp = 0;
  pMbCache->bCollocatedPredFlag = true;
  pWelsMd->iCostLuma = 0;//BGD&RC integration
  pCurMb->pSadCost[0] = pFunc->sSampleDealingFuncs.pfSampleSad[BLOCK_16x16] (pMbCache->SPicData.pEncMb[0],
                        pCurDqLayer->iEncStride[0], pRefLuma, iLineSizeY);
  ST32 (&pCurMb->sP16x16Mv, 0);
  ST32 (&pCurDqLayer->pDecPic->sMvList[pCurMb->iMbXY], 0);

  if (bSkipMbFlag) {
    pCurMb->uiMbType = MB_TYPE_BACKGROUND;

    //update motion info to current MB
    ST32 (pCurMb->pRefIndex, 0);
    pFunc->pfUpdateMbMv (pCurMb->sMv, sMvp);

    pCurMb->uiLumaQp   = pSlice->uiLastMbQp;
    pCurMb->uiChromaQp = g_kuiChromaQpTable[CLIP3_QP_0_51 (pCurMb->uiLumaQp +
                                                          pCurDqLayer->sLayerInfo.pPpsP->uiChromaQpIndexOffset)];

    WelsRecPskip (pCurDqLayer, pEncCtx->pFuncList, pCurMb, pMbCache);
    VaaBackgroundMbDataUpdate (pEncCtx->pFuncList, pEncCtx->pVaa, pCurMb);
    return;
  }

  pCurMb->uiMbType = MB_TYPE_16x16;

  pWelsMd->sMe.sMe16x16.sMv.iMvX = 0;
  pWelsMd->sMe.sMe16x16.sMv.iMvY = 0;
  PredMv (&pMbCache->sMvComponents, 0, 4, pWelsMd->uiRef, &pWelsMd->sMe.sMe16x16.sMvp);
  pMbCache->sMbMvp[0] = pWelsMd->sMe.sMe16x16.sMvp;

  UpdateP16x16MotionInfo (pMbCache, pCurMb, pWelsMd->uiRef, &pWelsMd->sMe.sMe16x16.sMv);

  if (pWelsMd->bMdUsingSad)
    pWelsMd->iCostLuma = pCurMb->pSadCost[0];
  else
    pWelsMd->iCostLuma = pFunc->sSampleDealingFuncs.pfSampleSatd[BLOCK_16x16] (pMbCache->SPicData.pEncMb[0],
                         pCurDqLayer->iEncStride[0], pRefLuma, iLineSizeY);

  WelsInterMbEncode (pEncCtx, pSlice, pCurMb);
  WelsPMbChromaEncode (pEncCtx, pSlice, pCurMb);

  pFunc->pfCopy16x16Aligned (pMbCache->SPicData.pCsMb[0], pCurDqLayer->iCsStride[0], pMbCache->pMemPredLuma,     16);
  pFunc->pfCopy8x8Aligned (pMbCache->SPicData.pCsMb[1], pCurDqLayer->iCsStride[1], pMbCache->pMemPredChroma,    8);
  pFunc->pfCopy8x8Aligned (pMbCache->SPicData.pCsMb[2], pCurDqLayer->iCsStride[1], pMbCache->pMemPredChroma + 64, 8);
}

bool WelsMdPSkipEnc (sWelsEncCtx* pEncCtx, SWelsMD* pWelsMd, SMB* pCurMb, SMbCache* pMbCache) {
  SDqLayer* pCurLayer           = pEncCtx->pCurDqLayer;
  SWelsFuncPtrList* pFunc       = pEncCtx->pFuncList;

  uint8_t* pRefLuma = pMbCache->SPicData.pRefMb[0];
  uint8_t* pRefCb   = pMbCache->SPicData.pRefMb[1];
  uint8_t* pRefCr   = pMbCache->SPicData.pRefMb[2];
  int32_t iLineSizeY  = pCurLayer->pRefPic->iLineSize[0];
  int32_t iLineSizeUV = pCurLayer->pRefPic->iLineSize[1];

  uint8_t* pDstLuma = pMbCache->pSkipMb;
  uint8_t* pDstCb   = pMbCache->pSkipMb + 256;
  uint8_t* pDstCr   = pMbCache->pSkipMb + 256 + 64;

  SMVUnitXY sMvp = { 0 };
  int32_t n;

  int32_t iEncStride = pCurLayer->iEncStride[0];
  uint8_t* pEncMb = pMbCache->SPicData.pEncMb[0];
  int32_t* pStrideEncBlockOffset = pEncCtx->pStrideTab->pStrideEncBlockOffset[pEncCtx->uiDependencyId];
  int32_t* pEncBlockOffset;

  int32_t iSadCostLuma = 0;
  int32_t iSadCostChroma = 0;
  int32_t iSadCostMb = 0;

  PredSkipMv (pMbCache, &sMvp);

  // Special case, need to clip the vector //
  SMVUnitXY sQpelMvp = { static_cast<int16_t> (sMvp.iMvX >> 2), static_cast<int16_t> (sMvp.iMvY >> 2) };
  n = (pCurMb->iMbX << 4) + sQpelMvp.iMvX;
  if (n < -29)
    return false;
  else if (n > (int32_t) ((pCurLayer->iMbWidth << 4) + 12))
    return false;

  n = (pCurMb->iMbY << 4) + sQpelMvp.iMvY;
  if (n < -29)
    return false;
  else if (n > (int32_t) ((pCurLayer->iMbHeight << 4) + 12))
    return false;

  //luma
  pRefLuma += sQpelMvp.iMvY * iLineSizeY + sQpelMvp.iMvX;
  pFunc->sMcFuncs.pMcLumaFunc (pRefLuma, iLineSizeY, pDstLuma, 16, sMvp.iMvX, sMvp.iMvY, 16, 16);
  iSadCostLuma    = pFunc->sSampleDealingFuncs.pfSampleSad[BLOCK_16x16] (pMbCache->SPicData.pEncMb[0],
                    pCurLayer->iEncStride[0], pDstLuma, 16);

  const int32_t iStrideUV = (sQpelMvp.iMvY >> 1) * iLineSizeUV + (sQpelMvp.iMvX >> 1);
  pRefCb += iStrideUV;
  pFunc->sMcFuncs.pMcChromaFunc (pRefCb, iLineSizeUV, pDstCb, 8, sMvp.iMvX, sMvp.iMvY, 8, 8); //Cb
  iSadCostChroma  = pFunc->sSampleDealingFuncs.pfSampleSad[BLOCK_8x8] (pMbCache->SPicData.pEncMb[1],
                    pCurLayer->iEncStride[1], pDstCb, 8);

  pRefCr += iStrideUV;
  pFunc->sMcFuncs.pMcChromaFunc (pRefCr, iLineSizeUV, pDstCr, 8, sMvp.iMvX, sMvp.iMvY, 8, 8); //Cr
  iSadCostChroma += pFunc->sSampleDealingFuncs.pfSampleSad[BLOCK_8x8] (pMbCache->SPicData.pEncMb[2],
                    pCurLayer->iEncStride[2], pDstCr, 8);

  iSadCostMb = iSadCostLuma + iSadCostChroma;

  if (iSadCostMb == 0                             ||
      iSadCostMb < pWelsMd->iSadPredSkip   ||
      (pCurLayer->pRefPic->iPictureType == P_SLICE     &&
       pMbCache->uiRefMbType == MB_TYPE_SKIP    &&
       iSadCostMb < pCurLayer->pRefPic->pMbSkipSad[pCurMb->iMbXY])) {
    //update motion info to current MB
    ST32 (pCurMb->pRefIndex, 0);
    pFunc->pfUpdateMbMv (pCurMb->sMv, sMvp);

    if (pWelsMd->bMdUsingSad) {
      pCurMb->pSadCost[0] = iSadCostLuma;
      pWelsMd->iCostLuma = pCurMb->pSadCost[0];
    } else
      pWelsMd->iCostLuma = pFunc->sSampleDealingFuncs.pfSampleSatd[BLOCK_16x16] (pMbCache->SPicData.pEncMb[0],
                           pCurLayer->iEncStride[0], pDstLuma, 16);

    pWelsMd->iCostSkipMb = iSadCostMb;

    pCurMb->sP16x16Mv = sMvp;
    pCurLayer->pDecPic->sMvList[pCurMb->iMbXY] = sMvp;

    return true;
  }

  WelsDctMb (pMbCache->pCoeffLevel,  pEncMb, iEncStride, pDstLuma, pEncCtx->pFuncList->pfDctFourT4);

  if (WelsTryPYskip (pEncCtx, pCurMb, pMbCache)) {
    iEncStride = pEncCtx->pCurDqLayer->iEncStride[1];
    pEncMb = pMbCache->SPicData.pEncMb[1];
    pEncBlockOffset = pStrideEncBlockOffset + 16;
    pFunc->pfDctFourT4 (pMbCache->pCoeffLevel + 256, & (pEncMb[*pEncBlockOffset]), iEncStride, pMbCache->pSkipMb + 256, 8);
    if (WelsTryPUVskip (pEncCtx, pCurMb, pMbCache, 1)) {
      pEncMb = pMbCache->SPicData.pEncMb[2];
      pEncBlockOffset = pStrideEncBlockOffset + 20;
      pFunc->pfDctFourT4 (pMbCache->pCoeffLevel + 320, & (pEncMb[*pEncBlockOffset]), iEncStride, pMbCache->pSkipMb + 320, 8);
      if (WelsTryPUVskip (pEncCtx, pCurMb, pMbCache, 2)) {
        //update motion info to current MB
        ST32 (pCurMb->pRefIndex, 0);
        pFunc->pfUpdateMbMv (pCurMb->sMv, sMvp);

        if (pWelsMd->bMdUsingSad) {
          pCurMb->pSadCost[0] = iSadCostLuma;
          pWelsMd->iCostLuma = pCurMb->pSadCost[0];
        } else
          pWelsMd->iCostLuma = pFunc->sSampleDealingFuncs.pfSampleSatd[BLOCK_16x16] (pMbCache->SPicData.pEncMb[0],
                               pCurLayer->iEncStride[0], pDstLuma, 16);

        pWelsMd->iCostSkipMb = iSadCostMb;

        pCurMb->sP16x16Mv = sMvp;
        pCurLayer->pDecPic->sMvList[pCurMb->iMbXY] = sMvp;

        return true;
      }
    }
  }
  return false;
}

const int32_t g_kiPixStrideIdx8x8[4] = {  0,                                             ME_REFINE_BUF_WIDTH_BLK8,
                                          ME_REFINE_BUF_STRIDE_BLK8, ME_REFINE_BUF_STRIDE_BLK8 + ME_REFINE_BUF_WIDTH_BLK8
                                       };
const int32_t g_kiPixStrideIdx4x4[4][4] = {
  {
    0,
    0 + ME_REFINE_BUF_WIDTH_BLK4,
    0 + ME_REFINE_BUF_STRIDE_BLK4,
    0 + ME_REFINE_BUF_WIDTH_BLK4 + ME_REFINE_BUF_STRIDE_BLK4
  }, //[0][]
  {
    ME_REFINE_BUF_WIDTH_BLK8,
    ME_REFINE_BUF_WIDTH_BLK8 + ME_REFINE_BUF_WIDTH_BLK4,
    ME_REFINE_BUF_WIDTH_BLK8 + ME_REFINE_BUF_STRIDE_BLK4,
    ME_REFINE_BUF_WIDTH_BLK8 + ME_REFINE_BUF_WIDTH_BLK4 + ME_REFINE_BUF_STRIDE_BLK4
  }, //[1][]
  {
    ME_REFINE_BUF_STRIDE_BLK8,
    ME_REFINE_BUF_STRIDE_BLK8 + ME_REFINE_BUF_WIDTH_BLK4,
    ME_REFINE_BUF_STRIDE_BLK8 + ME_REFINE_BUF_STRIDE_BLK4,
    ME_REFINE_BUF_STRIDE_BLK8 + ME_REFINE_BUF_WIDTH_BLK4 + ME_REFINE_BUF_STRIDE_BLK4
  }, //[2][]
  {
    ME_REFINE_BUF_STRIDE_BLK8 + ME_REFINE_BUF_WIDTH_BLK8,
    ME_REFINE_BUF_STRIDE_BLK8 + ME_REFINE_BUF_WIDTH_BLK8 + ME_REFINE_BUF_WIDTH_BLK4,
    ME_REFINE_BUF_STRIDE_BLK8 + ME_REFINE_BUF_WIDTH_BLK8 + ME_REFINE_BUF_STRIDE_BLK4,
    ME_REFINE_BUF_STRIDE_BLK8 + ME_REFINE_BUF_WIDTH_BLK8 + ME_REFINE_BUF_WIDTH_BLK4 + ME_REFINE_BUF_STRIDE_BLK4
  } //[3][]
};

void WelsMdInterMbRefinement (sWelsEncCtx* pEncCtx, SWelsMD* pWelsMd, SMB* pCurMb, SMbCache* pMbCache) {
  SDqLayer* pCurDqLayer = pEncCtx->pCurDqLayer;
  SWelsFuncPtrList* pFunc = pEncCtx->pFuncList;
  uint8_t* pTmpRefCb, *pTmpRefCr, *pTmpDstCb, *pTmpDstCr;
  int32_t iMvStride, iRefBlk4Stride, iDstBlk4Stride;
  SMVUnitXY* pMv;
  int32_t iBestSadCost = 0, iBestSatdCost = 0;
  SMeRefinePointer sMeRefine;

  int32_t i, j, iIdx, iPixStride;

  uint8_t* pRefCb = pMbCache->SPicData.pRefMb[1];
  uint8_t* pRefCr = pMbCache->SPicData.pRefMb[2];
  uint8_t* pDstCb = pMbCache->pMemPredChroma;
  uint8_t* pDstCr = pMbCache->pMemPredChroma + 64;
  uint8_t* pDstLuma = pMbCache->pMemPredLuma;

  int32_t iLineSizeRefUV = pCurDqLayer->pRefPic->iLineSize[1];

  switch (pCurMb->uiMbType) {
  case MB_TYPE_16x16:
    //luma
    InitMeRefinePointer (&sMeRefine, pMbCache, 0);
    sMeRefine.pfCopyBlockByMode =
      pFunc->pfCopy16x16NotAligned; // dst can be align with 16 bytes, but not sure at pSrc, 12/29/2011
    MeRefineFracPixel (pEncCtx, pDstLuma, &pWelsMd->sMe.sMe16x16, &sMeRefine, 16, 16);
    UpdateP16x16MotionInfo (pMbCache, pCurMb, pWelsMd->uiRef, &pWelsMd->sMe.sMe16x16.sMv);

    pMbCache->sMbMvp[0] = pWelsMd->sMe.sMe16x16.sMvp;
    //save the best cost of final mode
    iBestSadCost  = pWelsMd->sMe.sMe16x16.uiSadCost;
    iBestSatdCost = pWelsMd->sMe.sMe16x16.uiSatdCost;

    //chroma
    pMv = &pWelsMd->sMe.sMe16x16.sMv;
    iMvStride = (pMv->iMvY >> 3) * iLineSizeRefUV + (pMv->iMvX >> 3);
    pTmpRefCb = pRefCb + iMvStride;
    pTmpRefCr = pRefCr + iMvStride;
    pEncCtx->pFuncList->sMcFuncs.pMcChromaFunc (pTmpRefCb, iLineSizeRefUV, pDstCb, 8, pMv->iMvX, pMv->iMvY, 8, 8); //Cb
    pEncCtx->pFuncList->sMcFuncs.pMcChromaFunc (pTmpRefCr, iLineSizeRefUV, pDstCr, 8, pMv->iMvX, pMv->iMvY, 8, 8); //Cr

    pWelsMd->iCostSkipMb = pEncCtx->pFuncList->sSampleDealingFuncs.pfSampleSad[BLOCK_16x16] (pMbCache->SPicData.pEncMb[0],
                           pCurDqLayer->iEncStride[0], pDstLuma, 16);
    pWelsMd->iCostSkipMb += pEncCtx->pFuncList->sSampleDealingFuncs.pfSampleSad[BLOCK_8x8] (pMbCache->SPicData.pEncMb[1],
                            pCurDqLayer->iEncStride[1], pDstCb, 8);
    pWelsMd->iCostSkipMb += pEncCtx->pFuncList->sSampleDealingFuncs.pfSampleSad[BLOCK_8x8] (pMbCache->SPicData.pEncMb[2],
                            pCurDqLayer->iEncStride[2], pDstCr, 8);
    break;

  case MB_TYPE_16x8:
    iPixStride = 0;
    sMeRefine.pfCopyBlockByMode =
      pFunc->pfCopy16x8NotAligned; // dst can be align with 16 bytes, but not sure at pSrc, 12/29/2011
    for (i = 0; i < 2; i++) {
      //luma
      iIdx = i << 3;
      InitMeRefinePointer (&sMeRefine, pMbCache, iPixStride);
      iPixStride += ME_REFINE_BUF_STRIDE_BLK8;
      PredInter16x8Mv (pMbCache, iIdx, pWelsMd->uiRef, &pWelsMd->sMe.sMe16x8[i].sMvp);
      MeRefineFracPixel (pEncCtx, pDstLuma + g_kuiSmb4AddrIn256[iIdx], &pWelsMd->sMe.sMe16x8[i], &sMeRefine, 16, 8);
      UpdateP16x8MotionInfo (pMbCache, pCurMb, iIdx, pWelsMd->uiRef, &pWelsMd->sMe.sMe16x8[i].sMv);
      pMbCache->sMbMvp[i] = pWelsMd->sMe.sMe16x8[i].sMvp;
      //save the best cost of final mode
      iBestSadCost += pWelsMd->sMe.sMe16x8[i].uiSadCost;
      iBestSatdCost += pWelsMd->sMe.sMe16x8[i].uiSatdCost;

      //chroma
      iRefBlk4Stride = (i << 2) * iLineSizeRefUV;
      iDstBlk4Stride = i << 5; // 4*8
      pMv = &pWelsMd->sMe.sMe16x8[i].sMv;
      iMvStride = (pMv->iMvY >> 3) * iLineSizeRefUV + (pMv->iMvX >> 3);
      pTmpRefCb = pRefCb + iRefBlk4Stride + iMvStride;
      pTmpRefCr = pRefCr + iRefBlk4Stride + iMvStride;
      pTmpDstCb = pDstCb + iDstBlk4Stride;
      pTmpDstCr = pDstCr + iDstBlk4Stride;
      pEncCtx->pFuncList->sMcFuncs.pMcChromaFunc (pTmpRefCb, iLineSizeRefUV, pTmpDstCb, 8, pMv->iMvX, pMv->iMvY, 8, 4); //Cb
      pEncCtx->pFuncList->sMcFuncs.pMcChromaFunc (pTmpRefCr, iLineSizeRefUV, pTmpDstCr, 8, pMv->iMvX, pMv->iMvY, 8, 4); //Cr
    }
    break;

  case MB_TYPE_8x16:
    iPixStride = 0;
    sMeRefine.pfCopyBlockByMode = pFunc->pfCopy8x16Aligned;
    for (i = 0; i < 2; i++) {
      //luma
      iIdx = i << 2;
      InitMeRefinePointer (&sMeRefine, pMbCache, iPixStride);
      iPixStride += ME_REFINE_BUF_WIDTH_BLK8;
      PredInter8x16Mv (pMbCache, iIdx, pWelsMd->uiRef, &pWelsMd->sMe.sMe8x16[i].sMvp);
      MeRefineFracPixel (pEncCtx, pDstLuma + g_kuiSmb4AddrIn256[iIdx], &pWelsMd->sMe.sMe8x16[i], &sMeRefine, 8, 16);
      update_P8x16_motion_info (pMbCache, pCurMb, iIdx, pWelsMd->uiRef, &pWelsMd->sMe.sMe8x16[i].sMv);
      pMbCache->sMbMvp[i] = pWelsMd->sMe.sMe8x16[i].sMvp;
      //save the best cost of final mode
      iBestSadCost += pWelsMd->sMe.sMe8x16[i].uiSadCost;
      iBestSatdCost += pWelsMd->sMe.sMe8x16[i].uiSatdCost;

      //chroma
      iRefBlk4Stride = iIdx; //4
      pMv = &pWelsMd->sMe.sMe8x16[i].sMv;
      iMvStride = (pMv->iMvY >> 3) * iLineSizeRefUV + (pMv->iMvX >> 3);
      pTmpRefCb = pRefCb + iRefBlk4Stride + iMvStride;
      pTmpRefCr = pRefCr + iRefBlk4Stride + iMvStride;
      pTmpDstCb = pDstCb + iRefBlk4Stride;
      pTmpDstCr = pDstCr + iRefBlk4Stride;
      pEncCtx->pFuncList->sMcFuncs.pMcChromaFunc (pTmpRefCb, iLineSizeRefUV, pTmpDstCb, 8, pMv->iMvX, pMv->iMvY, 4, 8); //Cb
      pEncCtx->pFuncList->sMcFuncs.pMcChromaFunc (pTmpRefCr, iLineSizeRefUV, pTmpDstCr, 8, pMv->iMvX, pMv->iMvY, 4, 8); //Cr
    }
    break;
  case MB_TYPE_8x8:
    pMbCache->sMvComponents.iRefIndexCache [9] = pMbCache->sMvComponents.iRefIndexCache [21] = REF_NOT_AVAIL;
    for (i = 0; i < 4; i++) {
      int32_t iBlk8Idx = i << 2; //0, 4, 8, 12
      int32_t iBlk4X, iBlk4Y, iBlk4x4Idx;

      pCurMb->pRefIndex[i] = pWelsMd->uiRef;
      switch (pCurMb->uiSubMbType[i]) {
      case SUB_MB_TYPE_8x8:
        sMeRefine.pfCopyBlockByMode = pFunc->pfCopy8x8Aligned;
        //luma
        InitMeRefinePointer (&sMeRefine, pMbCache, g_kiPixStrideIdx8x8[i]);
        PredMv (&pMbCache->sMvComponents, iBlk8Idx, 2, pWelsMd->uiRef, &pWelsMd->sMe.sMe8x8[i].sMvp);
        MeRefineFracPixel (pEncCtx, pDstLuma + g_kuiSmb4AddrIn256[iBlk8Idx], &pWelsMd->sMe.sMe8x8[i], &sMeRefine, 8, 8);
        UpdateP8x8MotionInfo (pMbCache, pCurMb, iBlk8Idx, pWelsMd->uiRef, &pWelsMd->sMe.sMe8x8[i].sMv);
        pMbCache->sMbMvp[g_kuiMbCountScan4Idx[iBlk8Idx]] = pWelsMd->sMe.sMe8x8[i].sMvp;
        iBestSadCost += pWelsMd->sMe.sMe8x8[i].uiSadCost;
        iBestSatdCost += pWelsMd->sMe.sMe8x8[i].uiSatdCost;

        //chroma
        pMv = &pWelsMd->sMe.sMe8x8[i].sMv;
        iMvStride = (pMv->iMvY >> 3) * iLineSizeRefUV + (pMv->iMvX >> 3);

        iBlk4X = (i & 1) << 2;
        iBlk4Y = (i >> 1) << 2;
        iRefBlk4Stride = iBlk4Y * iLineSizeRefUV + iBlk4X;
        iDstBlk4Stride = (iBlk4Y << 3) + iBlk4X;

        pTmpRefCb = pRefCb + iRefBlk4Stride;
        pTmpDstCb = pDstCb + iDstBlk4Stride;
        pTmpRefCr = pRefCr + iRefBlk4Stride;
        pTmpDstCr = pDstCr + iDstBlk4Stride;
        pEncCtx->pFuncList->sMcFuncs.pMcChromaFunc (pTmpRefCb + iMvStride, iLineSizeRefUV, pTmpDstCb, 8, pMv->iMvX, pMv->iMvY,
            4, 4); //Cb
        pEncCtx->pFuncList->sMcFuncs.pMcChromaFunc (pTmpRefCr + iMvStride, iLineSizeRefUV, pTmpDstCr, 8, pMv->iMvX, pMv->iMvY,
            4, 4); //Cr
        break;
      case SUB_MB_TYPE_4x4:
        sMeRefine.pfCopyBlockByMode = pFunc->pfCopy4x4;
        //luma
        for (j = 0; j < 4; ++j) {
          iBlk4x4Idx = iBlk8Idx + j;
          InitMeRefinePointer (&sMeRefine, pMbCache, g_kiPixStrideIdx4x4[i][j]);
          PredMv (&pMbCache->sMvComponents, iBlk4x4Idx, 1, pWelsMd->uiRef, &pWelsMd->sMe.sMe4x4[i][j].sMvp);
          MeRefineFracPixel (pEncCtx, pDstLuma + g_kuiSmb4AddrIn256[iBlk4x4Idx], &pWelsMd->sMe.sMe4x4[i][j], &sMeRefine, 4, 4);
          UpdateP4x4MotionInfo (pMbCache, pCurMb, iBlk4x4Idx, pWelsMd->uiRef, &pWelsMd->sMe.sMe4x4[i][j].sMv);
          pMbCache->sMbMvp[g_kuiMbCountScan4Idx[iBlk4x4Idx]] = pWelsMd->sMe.sMe4x4[i][j].sMvp;
          iBestSadCost += pWelsMd->sMe.sMe4x4[i][j].uiSadCost;
          iBestSatdCost += pWelsMd->sMe.sMe4x4[i][j].uiSatdCost;

          //chroma
          pMv = &pWelsMd->sMe.sMe4x4[i][j].sMv;
          iMvStride = (pMv->iMvY >> 3) * iLineSizeRefUV + (pMv->iMvX >> 3);

          iBlk4X = (((i & 1) << 1) + (j & 1)) << 1;
          iBlk4Y = (((i >> 1) << 1) + (j >> 1)) << 1;
          iRefBlk4Stride = iBlk4Y * iLineSizeRefUV + iBlk4X;
          iDstBlk4Stride = (iBlk4Y << 3) + iBlk4X;

          pTmpRefCb = pRefCb + iRefBlk4Stride;
          pTmpDstCb = pDstCb + iDstBlk4Stride;
          pTmpRefCr = pRefCr + iRefBlk4Stride;
          pTmpDstCr = pDstCr + iDstBlk4Stride;
          pEncCtx->pFuncList->sMcFuncs.pMcChromaFunc (pTmpRefCb + iMvStride, iLineSizeRefUV, pTmpDstCb, 8, pMv->iMvX, pMv->iMvY,
              2, 2); //Cb
          pEncCtx->pFuncList->sMcFuncs.pMcChromaFunc (pTmpRefCr + iMvStride, iLineSizeRefUV, pTmpDstCr, 8, pMv->iMvX, pMv->iMvY,
              2, 2); //Cr
        }
        break;
      case SUB_MB_TYPE_8x4:
        sMeRefine.pfCopyBlockByMode = pFunc->pfCopy8x4;
        //luma
        for (j = 0; j < 2; ++j) {
          iBlk4x4Idx = iBlk8Idx + (j << 1);
          InitMeRefinePointer (&sMeRefine, pMbCache, g_kiPixStrideIdx4x4[i][j << 1]);
          PredMv (&pMbCache->sMvComponents, iBlk4x4Idx, 2, pWelsMd->uiRef, &pWelsMd->sMe.sMe8x4[i][j].sMvp);
          MeRefineFracPixel (pEncCtx, pDstLuma + g_kuiSmb4AddrIn256[iBlk4x4Idx], &pWelsMd->sMe.sMe8x4[i][j], &sMeRefine, 8, 4);
          UpdateP8x4MotionInfo (pMbCache, pCurMb, iBlk4x4Idx, pWelsMd->uiRef, &pWelsMd->sMe.sMe8x4[i][j].sMv);
          pMbCache->sMbMvp[g_kuiMbCountScan4Idx[    iBlk4x4Idx]] = pWelsMd->sMe.sMe8x4[i][j].sMvp;
          //pMbCache->sMbMvp[g_kuiMbCountScan4Idx[1 + iBlk4x4Idx]] = pWelsMd->sMe.sMe8x4[i][j].sMvp;
          iBestSadCost += pWelsMd->sMe.sMe8x4[i][j].uiSadCost;
          iBestSatdCost += pWelsMd->sMe.sMe8x4[i][j].uiSatdCost;

          //chroma
          pMv = &pWelsMd->sMe.sMe8x4[i][j].sMv;
          iMvStride = (pMv->iMvY >> 3) * iLineSizeRefUV + (pMv->iMvX >> 3);

          iBlk4X = ((i & 1) << 1) << 1;
          iBlk4Y = (((i >> 1) << 1) + j) << 1;
          iRefBlk4Stride = iBlk4Y * iLineSizeRefUV + iBlk4X;
          iDstBlk4Stride = (iBlk4Y << 3) + iBlk4X;

          pTmpRefCb = pRefCb + iRefBlk4Stride;
          pTmpDstCb = pDstCb + iDstBlk4Stride;
          pTmpRefCr = pRefCr + iRefBlk4Stride;
          pTmpDstCr = pDstCr + iDstBlk4Stride;
          pEncCtx->pFuncList->sMcFuncs.pMcChromaFunc (pTmpRefCb + iMvStride, iLineSizeRefUV, pTmpDstCb, 8, pMv->iMvX, pMv->iMvY,
              4, 2); //Cb
          pEncCtx->pFuncList->sMcFuncs.pMcChromaFunc (pTmpRefCr + iMvStride, iLineSizeRefUV, pTmpDstCr, 8, pMv->iMvX, pMv->iMvY,
              4, 2); //Cr
        }
        break;
      case SUB_MB_TYPE_4x8:
        sMeRefine.pfCopyBlockByMode = pFunc->pfCopy4x8;
        //luma
        for (j = 0; j < 2; ++j) {
          iBlk4x4Idx = iBlk8Idx + j;
          InitMeRefinePointer (&sMeRefine, pMbCache, g_kiPixStrideIdx4x4[i][j]);
          PredMv (&pMbCache->sMvComponents, iBlk4x4Idx, 1, pWelsMd->uiRef, &pWelsMd->sMe.sMe4x8[i][j].sMvp);
          MeRefineFracPixel (pEncCtx, pDstLuma + g_kuiSmb4AddrIn256[iBlk4x4Idx], &pWelsMd->sMe.sMe4x8[i][j], &sMeRefine, 4, 8);
          UpdateP4x8MotionInfo (pMbCache, pCurMb, iBlk4x4Idx, pWelsMd->uiRef, &pWelsMd->sMe.sMe4x8[i][j].sMv);
          pMbCache->sMbMvp[g_kuiMbCountScan4Idx[    iBlk4x4Idx]] = pWelsMd->sMe.sMe4x8[i][j].sMvp;
          //pMbCache->sMbMvp[g_kuiMbCountScan4Idx[4 + iBlk4x4Idx]] = pWelsMd->sMe.sMe8x4[i][j].sMvp;
          iBestSadCost += pWelsMd->sMe.sMe4x8[i][j].uiSadCost;
          iBestSatdCost += pWelsMd->sMe.sMe4x8[i][j].uiSatdCost;

          //chroma
          pMv = &pWelsMd->sMe.sMe4x8[i][j].sMv;
          iMvStride = (pMv->iMvY >> 3) * iLineSizeRefUV + (pMv->iMvX >> 3);

          iBlk4X = (((i & 1) << 1) + j) << 1;
          iBlk4Y = (((i >> 1) << 1)) << 1;
          iRefBlk4Stride = iBlk4Y * iLineSizeRefUV + iBlk4X;
          iDstBlk4Stride = (iBlk4Y << 3) + iBlk4X;

          pTmpRefCb = pRefCb + iRefBlk4Stride;
          pTmpDstCb = pDstCb + iDstBlk4Stride;
          pTmpRefCr = pRefCr + iRefBlk4Stride;
          pTmpDstCr = pDstCr + iDstBlk4Stride;
          pEncCtx->pFuncList->sMcFuncs.pMcChromaFunc (pTmpRefCb + iMvStride, iLineSizeRefUV, pTmpDstCb, 8, pMv->iMvX, pMv->iMvY,
              2, 4); //Cb
          pEncCtx->pFuncList->sMcFuncs.pMcChromaFunc (pTmpRefCr + iMvStride, iLineSizeRefUV, pTmpDstCr, 8, pMv->iMvX, pMv->iMvY,
              2, 4); //Cr
        }
        break;
      }
    }
    break;
  default:
    break;
  }
  pCurMb->pSadCost[0] = iBestSadCost;
  if (pWelsMd->bMdUsingSad)
    pWelsMd->iCostLuma = iBestSadCost;
  else
    pWelsMd->iCostLuma = iBestSatdCost;

}
bool WelsMdFirstIntraMode (sWelsEncCtx* pEncCtx, SWelsMD* pWelsMd, SMB* pCurMb, SMbCache* pMbCache) {
  SWelsFuncPtrList* pFunc = pEncCtx->pFuncList;

  int32_t iCostI16x16 = WelsMdI16x16 (pFunc, pEncCtx->pCurDqLayer, pMbCache, pWelsMd->iLambda);

  //compare cost_p16x16 with cost_i16x16
  if (iCostI16x16 < pWelsMd->iCostLuma) {
    pCurMb->uiMbType = MB_TYPE_INTRA16x16;
    pWelsMd->iCostLuma = iCostI16x16;

    pFunc->pfIntraFineMd (pEncCtx, pWelsMd, pCurMb, pMbCache);

    //add pEnc&rec to MD--2010.3.15
    if (IS_INTRA16x16 (pCurMb->uiMbType)) {
      pCurMb->uiCbp = 0;
      WelsEncRecI16x16Y (pEncCtx, pCurMb, pMbCache);
    }

    //chroma
    pWelsMd->iCostChroma = WelsMdIntraChroma (pFunc, pEncCtx->pCurDqLayer, pMbCache, pWelsMd->iLambda);
    WelsIMbChromaEncode (pEncCtx, pCurMb, pMbCache);  //add pEnc&rec to MD--2010.3.15
    pCurMb->uiChromPredMode = pMbCache->uiChmaI8x8Mode;
    pCurMb->pSadCost[0] = 0;
    return true; //intra_mb_type is best
  }

  return false;
}

void WelsMdInterMb (sWelsEncCtx* pEncCtx, SWelsMD* pWelsMd, SSlice* pSlice, SMB* pCurMb, SMbCache* pUnused) {
  SDqLayer* pCurDqLayer             = pEncCtx->pCurDqLayer;
  SMbCache* pMbCache                = &pSlice->sMbCacheInfo;
  const uint32_t kuiNeighborAvail   = pCurMb->uiNeighborAvail;
  const int32_t kiMbWidth           = pCurDqLayer->iMbWidth;
  const  SMB* top_mb                = pCurMb - kiMbWidth;
  const bool bMbLeftAvailPskip      = ((kuiNeighborAvail & LEFT_MB_POS) ? IS_SKIP ((pCurMb - 1)->uiMbType) : false);
  const bool bMbTopAvailPskip       = ((kuiNeighborAvail & TOP_MB_POS) ? IS_SKIP (top_mb->uiMbType) : false);
  const bool bMbTopLeftAvailPskip   = ((kuiNeighborAvail & TOPLEFT_MB_POS) ? IS_SKIP ((top_mb - 1)->uiMbType) : false);
  const bool bMbTopRightAvailPskip = ((kuiNeighborAvail & TOPRIGHT_MB_POS) ? IS_SKIP ((top_mb + 1)->uiMbType) : false);
  bool bTrySkip = bMbLeftAvailPskip || bMbTopAvailPskip || bMbTopLeftAvailPskip || bMbTopRightAvailPskip;
  bool bKeepSkip = bMbLeftAvailPskip && bMbTopAvailPskip && bMbTopRightAvailPskip;
  bool bSkip = false;

  //try BGD skip
  if (pEncCtx->pFuncList->pfInterMdBackgroundDecision (pEncCtx, pWelsMd, pSlice, pCurMb, pMbCache, &bKeepSkip)) {
    return;
  }

  //try static or scrolled Pskip
  if (pEncCtx->pFuncList->pfSCDPSkipDecision (pEncCtx, pWelsMd, pSlice, pCurMb, pMbCache)) {
    return;
  }

  //step 1: try SKIP
  bSkip = WelsMdInterJudgePskip (pEncCtx, pWelsMd, pSlice, pCurMb, pMbCache, bTrySkip);

  if (bSkip) {
    if (bKeepSkip) {
      WelsMdInterDecidedPskip (pEncCtx,  pSlice,  pCurMb, pMbCache);
      return;
    }
  } else {
    PredictSad (pMbCache->sMvComponents.iRefIndexCache, pMbCache->iSadCost, 0, &pWelsMd->iSadPredMb);

    //step 2: P_16x16
    pWelsMd->iCostLuma = WelsMdP16x16 (pEncCtx->pFuncList, pCurDqLayer, pWelsMd, pSlice, pCurMb);
    pCurMb->uiMbType = MB_TYPE_16x16;
  }

  WelsMdInterSecondaryModesEnc (pEncCtx, pWelsMd, pSlice, pCurMb, pMbCache, bSkip);
}



//////
//  try the ordinary Pskip
//////
bool WelsMdInterJudgePskip (sWelsEncCtx* pEncCtx, SWelsMD* pWelsMd, SSlice* pSlice, SMB* pCurMb, SMbCache* pMbCache,
                            bool bTrySkip) {
  bool bRet = true;
  if (((pEncCtx->pRefPic->iPictureType == P_SLICE) && (pMbCache->uiRefMbType == MB_TYPE_SKIP
       || pMbCache->uiRefMbType == MB_TYPE_BACKGROUND)) ||
      bTrySkip) {
    PredictSadSkip (pMbCache->sMvComponents.iRefIndexCache, pMbCache->bMbTypeSkip, pMbCache->iSadCostSkip, 0,
                    & (pWelsMd->iSadPredSkip));
    bRet = WelsMdPSkipEnc (pEncCtx, pWelsMd, pCurMb, pMbCache) ? true : false;
    return bRet;
  }

  return false;
}

//////
//  try the ordinary Pskip
//////
void WelsMdInterUpdatePskip (SDqLayer* pCurDqLayer, SSlice* pSlice, SMB* pCurMb, SMbCache* pMbCache) {
  //add pEnc&rec to MD--2010.3.15
  pCurMb->uiCbp = 0;
  pCurMb->uiLumaQp   = pSlice->uiLastMbQp;
  pCurMb->uiChromaQp = g_kuiChromaQpTable[CLIP3_QP_0_51 (pCurMb->uiLumaQp +
                                                        pCurDqLayer->sLayerInfo.pPpsP->uiChromaQpIndexOffset)];
  pMbCache->bCollocatedPredFlag = (LD32 (&pCurMb->sMv[0]) == 0);
}


//////
//  doublecheck if current MBTYPE is Pskip
//////
void WelsMdInterDoubleCheckPskip (SMB* pCurMb, SMbCache* pMbCache) {
  if (MB_TYPE_16x16 == pCurMb->uiMbType && 0 == pCurMb->uiCbp) {
    if (0 == pCurMb->pRefIndex[0]) {
      SMVUnitXY sMvp = { 0 };

      PredSkipMv (pMbCache, &sMvp);
      if (LD32 (&sMvp) == LD32 (&pCurMb->sMv[0])) {
        pCurMb->uiMbType = MB_TYPE_SKIP;
      }
    }
    pMbCache->bCollocatedPredFlag = (LD32 (&pCurMb->sMv[0]) == 0);
  }
}

//////
//  Pskip mb encode
//////
void WelsMdInterDecidedPskip (sWelsEncCtx* pEncCtx, SSlice* pSlice, SMB* pCurMb, SMbCache* pMbCache) {
  SDqLayer* pCurDqLayer = pEncCtx->pCurDqLayer;
  pCurMb->uiMbType = MB_TYPE_SKIP;
  WelsRecPskip (pCurDqLayer, pEncCtx->pFuncList, pCurMb, pMbCache);
  WelsMdInterUpdatePskip (pCurDqLayer, pSlice, pCurMb, pMbCache);
}

//////
//  inter mb encode
//////
void WelsMdInterEncode (sWelsEncCtx* pEncCtx, SSlice* pSlice, SMB* pCurMb, SMbCache* pMbCache) {
  SWelsFuncPtrList* pFunc = pEncCtx->pFuncList;
  SDqLayer* pCurDqLayer = pEncCtx->pCurDqLayer;

  //add pEnc&rec to MD--2010.3.15
  const int32_t kiCsStrideY = pCurDqLayer->iCsStride[0];
  const int32_t kiCsStrideUV = pCurDqLayer->iCsStride[1];

  //add pEnc&rec to MD--2010.3.15
  pCurMb->uiCbp = 0;
  WelsInterMbEncode (pEncCtx, pSlice, pCurMb);
  WelsPMbChromaEncode (pEncCtx, pSlice, pCurMb);

  pFunc->pfCopy16x16Aligned (pMbCache->SPicData.pCsMb[0], kiCsStrideY, pMbCache->pMemPredLuma,      16);
  pFunc->pfCopy8x8Aligned (pMbCache->SPicData.pCsMb[1], kiCsStrideUV, pMbCache->pMemPredChroma,    8);
  pFunc->pfCopy8x8Aligned (pMbCache->SPicData.pCsMb[2], kiCsStrideUV, pMbCache->pMemPredChroma + 64, 8);
}



//
//
//
void WelsMdInterSaveSadAndRefMbType (Mb_Type* pRefMbtypeList, SMbCache* pMbCache, const SMB*  pCurMb,
                                     const SWelsMD* pMd) {
  const Mb_Type kmtCurMbtype = pCurMb->uiMbType;

  //sad
  pMbCache->pEncSad[0] = (kmtCurMbtype == MB_TYPE_SKIP) ? pMd->iCostSkipMb : 0;
  //uiMbType
  pRefMbtypeList[pCurMb->iMbXY] = kmtCurMbtype;
}

void WelsMdInterSecondaryModesEnc (sWelsEncCtx* pEncCtx, SWelsMD* pWelsMd, SSlice* pSlice, SMB* pCurMb,
                                   SMbCache* pMbCache, const bool bSkip) {
  //step 2: Intra
  const bool kbTrySkip = pEncCtx->pFuncList->pfFirstIntraMode (pEncCtx, pWelsMd, pCurMb, pMbCache);
  if (kbTrySkip)
    return;

  if (bSkip) {
    WelsMdInterDecidedPskip (pEncCtx,  pSlice,  pCurMb, pMbCache);
  } else {
    //Step 3: SubP16 MD
    pEncCtx->pFuncList->pfSetScrollingMv (pEncCtx->pVaa, pWelsMd); //SCC
    pEncCtx->pFuncList->pfInterFineMd (pEncCtx, pWelsMd, pSlice, pCurMb, pWelsMd->iCostLuma);

    //refinement for inter type
    WelsMdInterMbRefinement (pEncCtx, pWelsMd, pCurMb, pMbCache);

    //step 7: invoke encoding
    WelsMdInterEncode (pEncCtx, pSlice, pCurMb, pMbCache);

    //step 8: double check Pskip
    WelsMdInterDoubleCheckPskip (pCurMb, pMbCache);
  }
}


void WelsMdIntraSecondaryModesEnc (sWelsEncCtx* pEncCtx, SWelsMD* pWelsMd, SMB* pCurMb, SMbCache* pMbCache) {
  SWelsFuncPtrList* pFunc = pEncCtx->pFuncList;
  //initial prediction memory for I_4x4
  pFunc->pfIntraFineMd (pEncCtx, pWelsMd, pCurMb, pMbCache); //WelsMdIntraFinePartitionVaa

  //add pEnc&rec to MD--2010.3.15
  if (IS_INTRA16x16 (pCurMb->uiMbType)) {
    pCurMb->uiCbp = 0;
    WelsEncRecI16x16Y (pEncCtx, pCurMb, pMbCache);
  }

  //chroma
  pWelsMd->iCostChroma = WelsMdIntraChroma (pFunc, pEncCtx->pCurDqLayer, pMbCache, pWelsMd->iLambda);
  WelsIMbChromaEncode (pEncCtx, pCurMb, pMbCache);  //add pEnc&rec to MD--2010.3.15
  pCurMb->uiChromPredMode = pMbCache->uiChmaI8x8Mode;
  pCurMb->pSadCost[0] = 0;
}

} // namespace WelsEnc
