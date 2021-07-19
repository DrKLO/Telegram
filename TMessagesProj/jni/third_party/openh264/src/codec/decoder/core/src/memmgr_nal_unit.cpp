/*!
 * \copy
 *     Copyright (c)  2008-2013, Cisco Systems
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
 *  memmgr_nal_unit.c
 *
 *  Abstract
 *      memory manager utils for NAL Unit list available
 *
 *  History
 *      07/10/2008 Created
 *
 *****************************************************************************/
#include "memmgr_nal_unit.h"
#include "memory_align.h"
#include "error_code.h"

namespace WelsDec {

int32_t MemInitNalList (PAccessUnit* ppAu, const uint32_t kuiSize, CMemoryAlign* pMa) {
  uint32_t uiIdx = 0;
  uint8_t* pBase = NULL, *pPtr = NULL;
  const uint32_t kuiSizeAu = sizeof (SAccessUnit);
  const uint32_t kuiSizeNalUnitPtr = kuiSize * sizeof (PNalUnit);
  const uint32_t kuiSizeNalUnit = sizeof (SNalUnit);
  const uint32_t kuiCountSize = (kuiSizeAu + kuiSizeNalUnitPtr + kuiSize * kuiSizeNalUnit) * sizeof (uint8_t);

  if (kuiSize == 0)
    return ERR_INFO_INVALID_PARAM;

  if (*ppAu != NULL) {
    MemFreeNalList (ppAu, pMa);
  }

  pBase = (uint8_t*)pMa->WelsMallocz (kuiCountSize, "Access Unit");
  if (pBase == NULL)
    return ERR_INFO_OUT_OF_MEMORY;
  pPtr = pBase;
  *ppAu = (PAccessUnit)pPtr;
  pPtr += kuiSizeAu;
  (*ppAu)->pNalUnitsList = (PNalUnit*)pPtr;
  pPtr += kuiSizeNalUnitPtr;
  do {
    (*ppAu)->pNalUnitsList[uiIdx] = (PNalUnit)pPtr;
    pPtr += kuiSizeNalUnit;
    ++ uiIdx;
  } while (uiIdx < kuiSize);

  (*ppAu)->uiCountUnitsNum      = kuiSize;
  (*ppAu)->uiAvailUnitsNum      = 0;
  (*ppAu)->uiActualUnitsNum     = 0;
  (*ppAu)->uiStartPos           = 0;
  (*ppAu)->uiEndPos             = 0;
  (*ppAu)->bCompletedAuFlag     = false;

  return ERR_NONE;
}

int32_t MemFreeNalList (PAccessUnit* ppAu, CMemoryAlign* pMa) {
  if (ppAu != NULL) {
    PAccessUnit pAu = *ppAu;
    if (pAu != NULL) {
      pMa->WelsFree (pAu, "Access Unit");
      *ppAu = NULL;
    }
  }
  return ERR_NONE;
}


int32_t ExpandNalUnitList (PAccessUnit* ppAu, const int32_t kiOrgSize, const int32_t kiExpSize, CMemoryAlign* pMa) {
  if (kiExpSize <= kiOrgSize)
    return ERR_INFO_INVALID_PARAM;
  else {
    PAccessUnit pTmp = NULL;
    int32_t iIdx = 0;
    int32_t iRet = ERR_NONE;
    if ((iRet = MemInitNalList (&pTmp, kiExpSize, pMa)) != ERR_NONE) // request new list with expanding
      return iRet;

    do {
      memcpy (pTmp->pNalUnitsList[iIdx], (*ppAu)->pNalUnitsList[iIdx], sizeof (SNalUnit)); //confirmed_safe_unsafe_usage
      ++ iIdx;
    } while (iIdx < kiOrgSize);

    pTmp->uiCountUnitsNum       = kiExpSize;
    pTmp->uiAvailUnitsNum       = (*ppAu)->uiAvailUnitsNum;
    pTmp->uiActualUnitsNum      = (*ppAu)->uiActualUnitsNum;
    pTmp->uiEndPos              = (*ppAu)->uiEndPos;
    pTmp->bCompletedAuFlag      = (*ppAu)->bCompletedAuFlag;

    MemFreeNalList (ppAu, pMa); // free old list
    *ppAu = pTmp;
    return ERR_NONE;
  }
}

/*
 *  MemGetNextNal
 *  Get next NAL Unit for using.
 *  Need expand NAL Unit list if exceeding count number of available NAL Units withing an Access Unit
 */
PNalUnit MemGetNextNal (PAccessUnit* ppAu, CMemoryAlign* pMa) {
  PAccessUnit pAu = *ppAu;
  PNalUnit pNu = NULL;

  if (pAu->uiAvailUnitsNum >= pAu->uiCountUnitsNum) { // need expand list
    const uint32_t kuiExpandingSize = pAu->uiCountUnitsNum + (MAX_NAL_UNIT_NUM_IN_AU >> 1);
    if (ExpandNalUnitList (ppAu, pAu->uiCountUnitsNum, kuiExpandingSize, pMa))
      return NULL; // out of memory
    pAu = *ppAu;
  }

  pNu = pAu->pNalUnitsList[pAu->uiAvailUnitsNum++]; // ready for next nal position

  memset (pNu, 0, sizeof (SNalUnit)); // Please do not remove this for cache intend!!

  return pNu;
}

} // namespace WelsDec
