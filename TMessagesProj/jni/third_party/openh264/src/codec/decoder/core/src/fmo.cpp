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
 * \file    fmo.c
 *
 * \brief   Flexible Macroblock Ordering implementation
 *
 * \date    2/4/2009 Created
 *
 *************************************************************************************
 */

#include "fmo.h"
#include "memory_align.h"
#include "error_code.h"

namespace WelsDec {

/*!
 * \brief   Generate MB allocated map for interleaved slice group (TYPE 0)
 *
 * \param   pFmo    fmo context
 * \param   pPps    pps context
 *
 * \return  0 - successful; none 0 - failed
 */
static inline int32_t FmoGenerateMbAllocMapType0 (PFmo pFmo, PPps pPps) {
  uint32_t uiNumSliceGroups = 0;
  int32_t iMbNum = 0;
  int32_t i = 0;

  WELS_VERIFY_RETURN_IF (ERR_INFO_INVALID_PARAM, (NULL == pFmo || NULL == pPps))
  uiNumSliceGroups = pPps->uiNumSliceGroups;
  iMbNum = pFmo->iCountMbNum;
  WELS_VERIFY_RETURN_IF (ERR_INFO_INVALID_PARAM, (NULL == pFmo->pMbAllocMap || iMbNum <= 0
                         || uiNumSliceGroups > MAX_SLICEGROUP_IDS))

  do {
    uint8_t uiGroup = 0;
    do {
      const int32_t kiRunIdx = pPps->uiRunLength[uiGroup];
      int32_t j = 0;
      do {
        pFmo->pMbAllocMap[i + j] = uiGroup;
        ++ j;
      } while (j < kiRunIdx && i + j < iMbNum);
      i += kiRunIdx;
      ++ uiGroup;
    } while (uiGroup < uiNumSliceGroups && i < iMbNum);
  } while (i < iMbNum);

  return ERR_NONE; // well here
}

/*!
 * \brief   Generate MB allocated map for dispersed slice group (TYPE 1)
 *
 * \param   pFmo        fmo context
 * \param   pPps        pps context
 * \param   iMbWidth    MB width
 *
 * \return  0 - successful; none 0 - failed
 */
static inline int32_t FmoGenerateMbAllocMapType1 (PFmo pFmo, PPps pPps, const int32_t kiMbWidth) {
  uint32_t uiNumSliceGroups = 0;
  int32_t iMbNum = 0;
  int32_t i = 0;
  WELS_VERIFY_RETURN_IF (ERR_INFO_INVALID_PARAM, (NULL == pFmo || NULL == pPps))
  uiNumSliceGroups = pPps->uiNumSliceGroups;
  iMbNum = pFmo->iCountMbNum;
  WELS_VERIFY_RETURN_IF (ERR_INFO_INVALID_PARAM, (NULL == pFmo->pMbAllocMap || iMbNum <= 0 || kiMbWidth == 0
                         || uiNumSliceGroups > MAX_SLICEGROUP_IDS))

  do {
    pFmo->pMbAllocMap[i] = (uint8_t) (((i % kiMbWidth) + (((i / kiMbWidth) * uiNumSliceGroups) >> 1)) % uiNumSliceGroups);
    ++ i;
  } while (i < iMbNum);

  return ERR_NONE; // well here
}

/*!
 * \brief   Generate MB allocated map for various type of slice group cases (TYPE 0, .., 6)
 *
 * \param   pFmo        fmo context
 * \param   pPps        pps context
 * \param   kiMbWidth   MB width
 * \param   kiMbHeight  MB height
 *
 * \return  0 - successful; none 0 - failed
 */
static inline int32_t FmoGenerateSliceGroup (PFmo pFmo, const PPps kpPps, const int32_t kiMbWidth,
    const int32_t kiMbHeight, CMemoryAlign* pMa) {
  int32_t iNumMb = 0;
  int32_t iErr   = 0;
  bool bResolutionChanged = false;

  // the cases we would not like
  WELS_VERIFY_RETURN_IF (ERR_INFO_INVALID_PARAM, (NULL == pFmo || NULL == kpPps))

  iNumMb = kiMbWidth * kiMbHeight;

  if (0 == iNumMb)
    return ERR_INFO_INVALID_PARAM;

  pMa->WelsFree (pFmo->pMbAllocMap, "_fmo->pMbAllocMap");
  pFmo->pMbAllocMap = (uint8_t*)pMa->WelsMallocz (iNumMb * sizeof (uint8_t), "_fmo->pMbAllocMap");
  WELS_VERIFY_RETURN_IF (ERR_INFO_OUT_OF_MEMORY, (NULL == pFmo->pMbAllocMap)) // out of memory

  pFmo->iCountMbNum = iNumMb;

  if (kpPps->uiNumSliceGroups < 2 && iNumMb > 0) { // only one slice group, exactly it is single slice based
    memset (pFmo->pMbAllocMap, 0,  iNumMb * sizeof (int8_t));   // for safe

    pFmo->iSliceGroupCount = 1;

    return ERR_NONE;
  }

  if (bResolutionChanged || ((int32_t)kpPps->uiSliceGroupMapType != pFmo->iSliceGroupType)
      || ((int32_t)kpPps->uiNumSliceGroups != pFmo->iSliceGroupCount)) {
    switch (kpPps->uiSliceGroupMapType) {
    case 0:
      iErr = FmoGenerateMbAllocMapType0 (pFmo, kpPps);
      break;
    case 1:
      iErr = FmoGenerateMbAllocMapType1 (pFmo, kpPps, kiMbWidth);
      break;
    case 2:
    case 3:
    case 4:
    case 5:
    case 6:
      // Reserve for others slice group type
      iErr = 1;
      break;
    default:
      return ERR_INFO_UNSUPPORTED_FMOTYPE;
    }
  }

  if (0 == iErr) {      // well now
    pFmo->iSliceGroupCount = kpPps->uiNumSliceGroups;
    pFmo->iSliceGroupType  = kpPps->uiSliceGroupMapType;
  }

  return iErr;
}

/*!
 * \brief   Initialize Wels Flexible Macroblock Ordering (FMO)
 *
 * \param   pFmo        Wels fmo to be initialized
 * \param   pPps        pps argument
 * \param   kiMbWidth   mb width
 * \param   kiMbHeight  mb height
 *
 * \return  0 - successful; none 0 - failed;
 */
int32_t InitFmo (PFmo pFmo, PPps pPps, const int32_t kiMbWidth, const int32_t kiMbHeight, CMemoryAlign* pMa) {
  return FmoGenerateSliceGroup (pFmo, pPps, kiMbWidth, kiMbHeight, pMa);
}


/*!
 * \brief   Uninitialize Wels Flexible Macroblock Ordering (FMO) list
 *
 * \param   pFmo        Wels base fmo ptr to be uninitialized
 * \param   kiCnt       count number of PPS per list
 * \param   kiAvail     count available number of PPS in list
 *
 * \return  NONE
 */
void UninitFmoList (PFmo pFmo, const int32_t kiCnt, const int32_t kiAvail, CMemoryAlign* pMa) {
  PFmo pIter = pFmo;
  int32_t i = 0;
  int32_t iFreeNodes = 0;

  if (NULL == pIter || kiAvail <= 0 || kiCnt < kiAvail)
    return;

  while (i < kiCnt) {
    if (pIter != NULL && pIter->bActiveFlag) {
      if (NULL != pIter->pMbAllocMap) {
        pMa->WelsFree (pIter->pMbAllocMap, "pIter->pMbAllocMap");

        pIter->pMbAllocMap = NULL;
      }
      pIter->iSliceGroupCount   = 0;
      pIter->iSliceGroupType    = -1;
      pIter->iCountMbNum        = 0;
      pIter->bActiveFlag        = false;
      ++ iFreeNodes;
      if (iFreeNodes >= kiAvail)
        break;
    }
    ++ pIter;
    ++ i;
  }
}

/*!
 * \brief   detect parameter sets are changed or not
 *
 * \param   pFmo                fmo context
 * \param   kiCountNumMb        (iMbWidth * iMbHeight) in Sps
 * \param   iSliceGroupType     slice group type if fmo is exactly enabled
 * \param   iSliceGroupCount    slice group count if fmo is exactly enabled
 *
 * \return  true - changed or not initialized yet; false - not change at all
 */
bool FmoParamSetsChanged (PFmo pFmo, const int32_t kiCountNumMb, const int32_t kiSliceGroupType,
                          const int32_t kiSliceGroupCount) {
  WELS_VERIFY_RETURN_IF (false, (NULL == pFmo))

  return ((!pFmo->bActiveFlag)
          || (kiCountNumMb != pFmo->iCountMbNum)
          || (kiSliceGroupType != pFmo->iSliceGroupType)
          || (kiSliceGroupCount != pFmo->iSliceGroupCount));
}

/*!
 * \brief   update/insert FMO parameter unit
 *
 * \param   _fmo    FMO context
 * \param   _sps    PSps
 * \param   _pps    PPps
 * \param   pActiveFmoNum   int32_t* [in/out]
 *
 * \return  true - update/insert successfully; false - failed;
 */
int32_t FmoParamUpdate (PFmo pFmo, PSps pSps, PPps pPps, int32_t* pActiveFmoNum, CMemoryAlign* pMa) {
  const uint32_t kuiMbWidth = pSps->iMbWidth;
  const uint32_t kuiMbHeight = pSps->iMbHeight;
  int32_t iRet = ERR_NONE;
  if (FmoParamSetsChanged (pFmo, kuiMbWidth * kuiMbHeight, pPps->uiSliceGroupMapType, pPps->uiNumSliceGroups)) {
    iRet = InitFmo (pFmo, pPps, kuiMbWidth, kuiMbHeight, pMa);
    WELS_VERIFY_RETURN_IF (iRet, iRet);

    if (!pFmo->bActiveFlag && *pActiveFmoNum < MAX_PPS_COUNT) {
      ++ (*pActiveFmoNum);
      pFmo->bActiveFlag = true;
    }
  }
  return iRet;
}

/*!
 * \brief   Convert kMbXy to slice group idc correspondingly
 *
 * \param   pFmo        Wels fmo context
 * \param   kMbXy       kMbXy to be converted
 *
 * \return  slice group idc - successful; -1 - failed;
 */
int32_t FmoMbToSliceGroup (PFmo pFmo, const MB_XY_T kiMbXy) {
  const int32_t kiMbNum  = pFmo->iCountMbNum;
  const uint8_t* kpMbMap = pFmo->pMbAllocMap;

  if (kiMbXy < 0 || kiMbXy >= kiMbNum || kpMbMap == NULL)
    return -1;

  return kpMbMap[ kiMbXy ];
}

/*!
 * \brief   Get successive mb to be processed with given current kMbXy
 *
 * \param   pFmo            Wels fmo context
 * \param   kMbXy           current kMbXy
 *
 * \return  iNextMb - successful; -1 - failed;
 */
MB_XY_T FmoNextMb (PFmo pFmo, const MB_XY_T kiMbXy) {
  const int32_t kiTotalMb               = pFmo->iCountMbNum;
  const uint8_t* kpMbMap                = pFmo->pMbAllocMap;
  MB_XY_T iNextMb                       = kiMbXy;
  const uint8_t kuiSliceGroupIdc        = (uint8_t)FmoMbToSliceGroup (pFmo, kiMbXy);

  if (kuiSliceGroupIdc == (uint8_t) (-1))
    return -1;

  do {
    ++ iNextMb;
    if (iNextMb >= kiTotalMb) {
      iNextMb = -1;
      break;
    }
    if (kpMbMap[iNextMb] == kuiSliceGroupIdc) {
      break;
    }
  } while (1);

  // -1: No further MB in this slice (could be end of picture)
  return iNextMb;
}

} // namespace WelsDec
