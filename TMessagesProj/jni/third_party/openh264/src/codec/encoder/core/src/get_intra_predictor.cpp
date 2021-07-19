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
 * \file    get_intra_predictor.c
 *
 * \brief   implementation for get intra predictor about 16x16, 4x4, chroma.
 *
 * \date    4/2/2009 Created
 *          9/14/2009 C level based optimization with high performance gained.
 *              [const, using ST32/ST64 to replace memset, memcpy and memmove etc.]
 *
 *************************************************************************************
 */
#include "ls_defines.h"
#include "cpu_core.h"
#include "intra_pred_common.h"
#include "get_intra_predictor.h"

namespace WelsEnc {
#define I4x4_COUNT 4
#define I8x8_COUNT 8
#define I16x16_COUNT 16

typedef void (*PFillingPred) (uint8_t* pPred, uint8_t* pSrc);
typedef void (*PFillingPred1to16) (uint8_t* pPred, const uint8_t kuiSrc);

static inline void WelsFillingPred8to16_c (uint8_t* pPred, uint8_t* pSrc) {
  ST64 (pPred  , LD64 (pSrc));
  ST64 (pPred + 8, LD64 (pSrc));
}
static inline void WelsFillingPred8x2to16_c (uint8_t* pPred, uint8_t* pSrc) {
  ST64 (pPred  , LD64 (pSrc));
  ST64 (pPred + 8, LD64 (pSrc + 8));
}
static inline void WelsFillingPred1to16_c (uint8_t* pPred, const uint8_t kuiSrc) {
  const uint8_t kuiSrc8[8] = { kuiSrc, kuiSrc, kuiSrc, kuiSrc, kuiSrc, kuiSrc, kuiSrc, kuiSrc };
  ST64 (pPred  , LD64 (kuiSrc8));
  ST64 (pPred + 8, LD64 (kuiSrc8));
}

#define WelsFillingPred8to16 WelsFillingPred8to16_c
#define WelsFillingPred8x2to16 WelsFillingPred8x2to16_c
#define WelsFillingPred1to16 WelsFillingPred1to16_c



#define I4x4_PRED_STRIDE 4
#define I4x4_PRED_STRIDE2 8
#define I4x4_PRED_STRIDE3 12

void WelsI4x4LumaPredV_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  const uint32_t kuiSrc = LD32 (&pRef[-kiStride]);
  ENFORCE_STACK_ALIGN_1D (uint32_t, uiSrcx2, 2, 16)
  uiSrcx2[0] = uiSrcx2[1] = kuiSrc;

  WelsFillingPred8to16 (pPred, (uint8_t*)&uiSrcx2[0]);
}

void WelsI4x4LumaPredH_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  const uint32_t kiStridex2Left = (kiStride << 1) - 1;
  const uint32_t kiStridex3Left = kiStride + kiStridex2Left;
  const uint8_t kuiHor1 = pRef[-1];
  const uint8_t kuiHor2 = pRef[kiStride - 1];
  const uint8_t kuiHor3 = pRef[kiStridex2Left];
  const uint8_t kuiHor4 = pRef[kiStridex3Left];
  const uint8_t kuiVec1[4] = {kuiHor1, kuiHor1, kuiHor1, kuiHor1};
  const uint8_t kuiVec2[4] = {kuiHor2, kuiHor2, kuiHor2, kuiHor2};
  const uint8_t kuiVec3[4] = {kuiHor3, kuiHor3, kuiHor3, kuiHor3};
  const uint8_t kuiVec4[4] = {kuiHor4, kuiHor4, kuiHor4, kuiHor4};
  ENFORCE_STACK_ALIGN_1D (uint8_t, uiSrc, 16, 16) // TobeCont'd about assign opt as follows
  ST32 (&uiSrc[0], LD32 (kuiVec1));
  ST32 (&uiSrc[4], LD32 (kuiVec2));
  ST32 (&uiSrc[8], LD32 (kuiVec3));
  ST32 (&uiSrc[12], LD32 (kuiVec4));

  WelsFillingPred8x2to16 (pPred, uiSrc);
}
void WelsI4x4LumaPredDc_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  const uint8_t kuiDcValue = (pRef[-1] + pRef[kiStride - 1] + pRef[ (kiStride << 1) - 1] + pRef[ (kiStride << 1) +
                              kiStride - 1] +
                              pRef[-kiStride] + pRef[1 - kiStride] + pRef[2 - kiStride] + pRef[3 - kiStride] + 4) >> 3;

  WelsFillingPred1to16 (pPred, kuiDcValue);
}

void WelsI4x4LumaPredDcLeft_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  const uint8_t kuiDcValue = (pRef[-1] + pRef[kiStride - 1] + pRef[ (kiStride << 1) - 1] + pRef[ (kiStride << 1) +
                              kiStride - 1] + 2) >> 2;

  WelsFillingPred1to16 (pPred, kuiDcValue);
}

void WelsI4x4LumaPredDcTop_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  const uint8_t kuiDcValue = (pRef[-kiStride] + pRef[1 - kiStride] + pRef[2 - kiStride] + pRef[3 - kiStride] + 2) >> 2;

  WelsFillingPred1to16 (pPred, kuiDcValue);
}

void WelsI4x4LumaPredDcNA_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  const uint8_t kuiDcValue = 0x80;

  WelsFillingPred1to16 (pPred, kuiDcValue);
}

/*down pLeft*/
void WelsI4x4LumaPredDDL_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  /*get pTop*/
  const uint8_t kuiT0   = pRef[-kiStride];
  const uint8_t kuiT1   = pRef[1 - kiStride];
  const uint8_t kuiT2   = pRef[2 - kiStride];
  const uint8_t kuiT3   = pRef[3 - kiStride];
  const uint8_t kuiT4   = pRef[4 - kiStride];
  const uint8_t kuiT5   = pRef[5 - kiStride];
  const uint8_t kuiT6   = pRef[6 - kiStride];
  const uint8_t kuiT7   = pRef[7 - kiStride];
  const uint8_t kuiDDL0 = (2 + kuiT0 + kuiT2 + (kuiT1 << 1)) >> 2;      // uiDDL0
  const uint8_t kuiDDL1 = (2 + kuiT1 + kuiT3 + (kuiT2 << 1)) >> 2;      // uiDDL1
  const uint8_t kuiDDL2 = (2 + kuiT2 + kuiT4 + (kuiT3 << 1)) >> 2;      // uiDDL2
  const uint8_t kuiDDL3 = (2 + kuiT3 + kuiT5 + (kuiT4 << 1)) >> 2;      // uiDDL3
  const uint8_t kuiDDL4 = (2 + kuiT4 + kuiT6 + (kuiT5 << 1)) >> 2;      // uiDDL4
  const uint8_t kuiDDL5 = (2 + kuiT5 + kuiT7 + (kuiT6 << 1)) >> 2;      // uiDDL5
  const uint8_t kuiDDL6 = (2 + kuiT6 + kuiT7 + (kuiT7 << 1)) >> 2;      // uiDDL6
  ENFORCE_STACK_ALIGN_1D (uint8_t, uiSrc, 16, 16) // TobeCont'd about assign opt as follows
  uiSrc[0] = kuiDDL0;
  uiSrc[1] = uiSrc[4] = kuiDDL1;
  uiSrc[2] = uiSrc[5] = uiSrc[8] = kuiDDL2;
  uiSrc[3] = uiSrc[6] = uiSrc[9] = uiSrc[12] = kuiDDL3;
  uiSrc[7] = uiSrc[10] = uiSrc[13] = kuiDDL4;
  uiSrc[11] = uiSrc[14] = kuiDDL5;
  uiSrc[15] = kuiDDL6;

  WelsFillingPred8x2to16 (pPred, uiSrc);
}

/*down pLeft*/
void WelsI4x4LumaPredDDLTop_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  /*get pTop*/
  const uint8_t kuiT0   = pRef[-kiStride];
  const uint8_t kuiT1   = pRef[1 - kiStride];
  const uint8_t kuiT2   = pRef[2 - kiStride];
  const uint8_t kuiT3   = pRef[3 - kiStride];
  const uint8_t kuiDLT0 = (2 + kuiT0 + kuiT2 + (kuiT1 << 1)) >> 2;      // uiDLT0
  const uint8_t kuiDLT1 = (2 + kuiT1 + kuiT3 + (kuiT2 << 1)) >> 2;      // uiDLT1
  const uint8_t kuiDLT2 = (2 + kuiT2 + kuiT3 + (kuiT3 << 1)) >> 2;      // uiDLT2
  const uint8_t kuiDLT3 = (2 + (kuiT3 << 2)) >> 2;                      // uiDLT3
  ENFORCE_STACK_ALIGN_1D (uint8_t, uiSrc, 16, 16) // TobeCont'd about assign opt as follows
  memset (&uiSrc[6], kuiDLT3, 10 * sizeof (uint8_t));
  uiSrc[0] = kuiDLT0;
  uiSrc[1] = uiSrc[4] = kuiDLT1;
  uiSrc[2] = uiSrc[5] = uiSrc[8] = kuiDLT2;
  uiSrc[3] = kuiDLT3;

  WelsFillingPred8x2to16 (pPred, uiSrc);
}


/*down right*/
void WelsI4x4LumaPredDDR_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  const int32_t kiStridex2  = kiStride << 1;
  const int32_t kiStridex3  = kiStride + kiStridex2;
  const uint8_t kuiLT       = pRef[-kiStride - 1];  // pTop-pLeft
  /*get pLeft and pTop*/
  const uint8_t kuiL0       = pRef[-1];
  const uint8_t kuiL1       = pRef[kiStride - 1];
  const uint8_t kuiL2       = pRef[kiStridex2 - 1];
  const uint8_t kuiL3       = pRef[kiStridex3 - 1];
  const uint8_t kuiT0       = pRef[-kiStride];
  const uint8_t kuiT1       = pRef[1 - kiStride];
  const uint8_t kuiT2       = pRef[2 - kiStride];
  const uint8_t kuiT3       = pRef[3 - kiStride];
  const uint16_t kuiTL0     = 1 + kuiLT + kuiL0;
  const uint16_t kuiLT0     = 1 + kuiLT + kuiT0;
  const uint16_t kuiT01     = 1 + kuiT0 + kuiT1;
  const uint16_t kuiT12     = 1 + kuiT1 + kuiT2;
  const uint16_t kuiT23     = 1 + kuiT2 + kuiT3;
  const uint16_t kuiL01     = 1 + kuiL0 + kuiL1;
  const uint16_t kuiL12     = 1 + kuiL1 + kuiL2;
  const uint16_t kuiL23     = 1 + kuiL2 + kuiL3;
  const uint8_t kuiDDR0     = (kuiTL0 + kuiLT0) >> 2;
  const uint8_t kuiDDR1     = (kuiLT0 + kuiT01) >> 2;
  const uint8_t kuiDDR2     = (kuiT01 + kuiT12) >> 2;
  const uint8_t kuiDDR3     = (kuiT12 + kuiT23) >> 2;
  const uint8_t kuiDDR4     = (kuiTL0 + kuiL01) >> 2;
  const uint8_t kuiDDR5     = (kuiL01 + kuiL12) >> 2;
  const uint8_t kuiDDR6     = (kuiL12 + kuiL23) >> 2;
  ENFORCE_STACK_ALIGN_1D (uint8_t, uiSrc, 16, 16) // TobeCont'd about assign opt as follows
  uiSrc[0] = uiSrc[5] = uiSrc[10] = uiSrc[15] = kuiDDR0;
  uiSrc[1] = uiSrc[6] = uiSrc[11] = kuiDDR1;
  uiSrc[2] = uiSrc[7] = kuiDDR2;
  uiSrc[3] = kuiDDR3;
  uiSrc[4] = uiSrc[9] = uiSrc[14] = kuiDDR4;
  uiSrc[8] = uiSrc[13] = kuiDDR5;
  uiSrc[12] = kuiDDR6;

  WelsFillingPred8x2to16 (pPred, uiSrc);
}


/*vertical pLeft*/
void WelsI4x4LumaPredVL_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  /*get pTop*/
  const uint8_t kuiT0   = pRef[-kiStride];
  const uint8_t kuiT1   = pRef[1 - kiStride];
  const uint8_t kuiT2   = pRef[2 - kiStride];
  const uint8_t kuiT3   = pRef[3 - kiStride];
  const uint8_t kuiT4   = pRef[4 - kiStride];
  const uint8_t kuiT5   = pRef[5 - kiStride];
  const uint8_t kuiT6   = pRef[6 - kiStride];
  const uint8_t kuiVL0  = (1 + kuiT0 + kuiT1) >> 1;                     // uiVL0
  const uint8_t kuiVL1  = (1 + kuiT1 + kuiT2) >> 1;                     // uiVL1
  const uint8_t kuiVL2  = (1 + kuiT2 + kuiT3) >> 1;                     // uiVL2
  const uint8_t kuiVL3  = (1 + kuiT3 + kuiT4) >> 1;                     // uiVL3
  const uint8_t kuiVL4  = (1 + kuiT4 + kuiT5) >> 1;                     // uiVL4
  const uint8_t kuiVL5  = (2 + kuiT0 + (kuiT1 << 1) + kuiT2) >> 2;      // uiVL5
  const uint8_t kuiVL6  = (2 + kuiT1 + (kuiT2 << 1) + kuiT3) >> 2;      // uiVL6
  const uint8_t kuiVL7  = (2 + kuiT2 + (kuiT3 << 1) + kuiT4) >> 2;      // uiVL7
  const uint8_t kuiVL8  = (2 + kuiT3 + (kuiT4 << 1) + kuiT5) >> 2;      // uiVL8
  const uint8_t kuiVL9  = (2 + kuiT4 + (kuiT5 << 1) + kuiT6) >> 2;      // uiVL9
  ENFORCE_STACK_ALIGN_1D (uint8_t, uiSrc, 16, 16) // TobeCont'd about assign opt as follows
  uiSrc[0] = kuiVL0;
  uiSrc[1] = uiSrc[8] = kuiVL1;
  uiSrc[2] = uiSrc[9] = kuiVL2;
  uiSrc[3] = uiSrc[10] = kuiVL3;
  uiSrc[4] = kuiVL5;
  uiSrc[5] = uiSrc[12] = kuiVL6;
  uiSrc[6] = uiSrc[13] = kuiVL7;
  uiSrc[7] = uiSrc[14] = kuiVL8;
  uiSrc[11] = kuiVL4;
  uiSrc[15] = kuiVL9;

  WelsFillingPred8x2to16 (pPred, uiSrc);
}



/*vertical pLeft*/
void WelsI4x4LumaPredVLTop_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  uint8_t* pTopLeft     = &pRef[-kiStride - 1]; // pTop-pLeft
  /*get pTop*/
  const uint8_t kuiT0   = * (pTopLeft + 1);
  const uint8_t kuiT1   = * (pTopLeft + 2);
  const uint8_t kuiT2   = * (pTopLeft + 3);
  const uint8_t kuiT3   = * (pTopLeft + 4);
  const uint8_t kuiVLT0 = (1 + kuiT0 + kuiT1) >> 1;                     // uiVLT0
  const uint8_t kuiVLT1 = (1 + kuiT1 + kuiT2) >> 1;                     // uiVLT1
  const uint8_t kuiVLT2 = (1 + kuiT2 + kuiT3) >> 1;                     // uiVLT2
  const uint8_t kuiVLT3 = (1 + (kuiT3 << 1)) >> 1;                      // uiVLT3
  const uint8_t kuiVLT4 = (2 + kuiT0 + (kuiT1 << 1) + kuiT2) >> 2;      // uiVLT4
  const uint8_t kuiVLT5 = (2 + kuiT1 + (kuiT2 << 1) + kuiT3) >> 2;      // uiVLT5
  const uint8_t kuiVLT6 = (2 + kuiT2 + (kuiT3 << 1) + kuiT3) >> 2;      // uiVLT6
  const uint8_t kuiVLT7 = (2 + (kuiT3 << 2)) >> 2;                      // uiVLT7
  ENFORCE_STACK_ALIGN_1D (uint8_t, uiSrc, 16, 16) // TobeCont'd about assign opt as follows
  uiSrc[0] = kuiVLT0;
  uiSrc[1] = uiSrc[8] = kuiVLT1;
  uiSrc[2] = uiSrc[9] = kuiVLT2;
  uiSrc[3] = uiSrc[10] = uiSrc[11] = kuiVLT3;
  uiSrc[4] = kuiVLT4;
  uiSrc[5] = uiSrc[12] = kuiVLT5;
  uiSrc[6] = uiSrc[13] = kuiVLT6;
  uiSrc[7] = uiSrc[14] = uiSrc[15] = kuiVLT7;

  WelsFillingPred8x2to16 (pPred, uiSrc);
}

/*vertical right*/
void WelsI4x4LumaPredVR_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  const int32_t kiStridex2  = kiStride << 1;
  const uint8_t kuiLT       = pRef[-kiStride - 1];  // pTop-pLeft
  /*get pLeft and pTop*/
  const uint8_t kuiL0       = pRef[-1];
  const uint8_t kuiL1       = pRef[kiStride - 1];
  const uint8_t kuiL2       = pRef[kiStridex2 - 1];
  const uint8_t kuiT0       = pRef[-kiStride];
  const uint8_t kuiT1       = pRef[1 - kiStride];
  const uint8_t kuiT2       = pRef[2 - kiStride];
  const uint8_t kuiT3       = pRef[3 - kiStride];
  const uint8_t kuiVR0      = (1 + kuiLT + kuiT0) >> 1;
  const uint8_t kuiVR1      = (1 + kuiT0 + kuiT1) >> 1;
  const uint8_t kuiVR2      = (1 + kuiT1 + kuiT2) >> 1;
  const uint8_t kuiVR3      = (1 + kuiT2 + kuiT3) >> 1;
  const uint8_t kuiVR4      = (2 + kuiL0 + (kuiLT << 1) + kuiT0) >> 2;
  const uint8_t kuiVR5      = (2 + kuiLT + (kuiT0 << 1) + kuiT1) >> 2;
  const uint8_t kuiVR6      = (2 + kuiT0 + (kuiT1 << 1) + kuiT2) >> 2;
  const uint8_t kuiVR7      = (2 + kuiT1 + (kuiT2 << 1) + kuiT3) >> 2;
  const uint8_t kuiVR8      = (2 + kuiLT + (kuiL0 << 1) + kuiL1) >> 2;
  const uint8_t kuiVR9      = (2 + kuiL0 + (kuiL1 << 1) + kuiL2) >> 2;
  ENFORCE_STACK_ALIGN_1D (uint8_t, uiSrc, 16, 16) // TobeCont'd about assign opt as follows
  uiSrc[0] = uiSrc[9] = kuiVR0;
  uiSrc[1] = uiSrc[10] = kuiVR1;
  uiSrc[2] = uiSrc[11] = kuiVR2;
  uiSrc[3] = kuiVR3;
  uiSrc[4] = uiSrc[13] = kuiVR4;
  uiSrc[5] = uiSrc[14] = kuiVR5;
  uiSrc[6] = uiSrc[15] = kuiVR6;
  uiSrc[7] = kuiVR7;
  uiSrc[8] = kuiVR8;
  uiSrc[12] = kuiVR9;

  WelsFillingPred8x2to16 (pPred, uiSrc);
}


/*horizontal up*/
void WelsI4x4LumaPredHU_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  const int32_t kiStridex2  = kiStride << 1;
  const int32_t kiStridex3  = kiStride + kiStridex2;
  /*get pLeft*/
  const uint8_t kuiL0       = pRef[-1];
  const uint8_t kuiL1       = pRef[kiStride - 1];
  const uint8_t kuiL2       = pRef[kiStridex2 - 1];
  const uint8_t kuiL3       = pRef[kiStridex3 - 1];
  const uint16_t kuiL01     = (1 + kuiL0 + kuiL1);
  const uint16_t kuiL12     = (1 + kuiL1 + kuiL2);
  const uint16_t kuiL23     = (1 + kuiL2 + kuiL3);
  const uint8_t kuiHU0      = kuiL01 >> 1;
  const uint8_t kuiHU1      = (kuiL01 + kuiL12) >> 2;
  const uint8_t kuiHU2      = kuiL12 >> 1;
  const uint8_t kuiHU3      = (kuiL12 + kuiL23) >> 2;
  const uint8_t kuiHU4      = kuiL23 >> 1;
  const uint8_t kuiHU5      = (1 + kuiL23 + (kuiL3 << 1)) >> 2;
  ENFORCE_STACK_ALIGN_1D (uint8_t, uiSrc, 16, 16) // TobeCont'd about assign opt as follows
  uiSrc[0] = kuiHU0;
  uiSrc[1] = kuiHU1;
  uiSrc[2] = uiSrc[4] = kuiHU2;
  uiSrc[3] = uiSrc[5] = kuiHU3;
  uiSrc[6] = uiSrc[8] = kuiHU4;
  uiSrc[7] = uiSrc[9] = kuiHU5;
  memset (&uiSrc[10], kuiL3, 6 * sizeof (uint8_t));

  WelsFillingPred8x2to16 (pPred, uiSrc);
}


/*horizontal down*/
void WelsI4x4LumaPredHD_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  const int32_t kiStridex2  = kiStride << 1;
  const int32_t kiStridex3  = kiStride + kiStridex2;
  const uint8_t kuiLT       = pRef[-kiStride - 1];  // pTop-pLeft
  /*get pLeft and pTop*/
  const uint8_t kuiL0       = pRef[-1];
  const uint8_t kuiL1       = pRef[kiStride - 1];
  const uint8_t kuiL2       = pRef[kiStridex2 - 1];
  const uint8_t kuiL3       = pRef[kiStridex3 - 1];
  const uint8_t kuiT0       = pRef[-kiStride];
  const uint8_t kuiT1       = pRef[1 - kiStride];
  const uint8_t kuiT2       = pRef[2 - kiStride];
  const uint8_t kuiHD0      = (1 + kuiLT + kuiL0) >> 1;                     // uiHD0
  const uint8_t kuiHD1      = (2 + kuiL0 + (kuiLT << 1) + kuiT0) >> 2;      // uiHD1
  const uint8_t kuiHD2      = (2 + kuiLT + (kuiT0 << 1) + kuiT1) >> 2;      // uiHD2
  const uint8_t kuiHD3      = (2 + kuiT0 + (kuiT1 << 1) + kuiT2) >> 2;      // uiHD3
  const uint8_t kuiHD4      = (1 + kuiL0 + kuiL1) >> 1;                     // uiHD4
  const uint8_t kuiHD5      = (2 + kuiLT + (kuiL0 << 1) + kuiL1) >> 2;      // uiHD5
  const uint8_t kuiHD6      = (1 + kuiL1 + kuiL2) >> 1;                     // uiHD6
  const uint8_t kuiHD7      = (2 + kuiL0 + (kuiL1 << 1) + kuiL2) >> 2;      // uiHD7
  const uint8_t kuiHD8      = (1 + kuiL2 + kuiL3) >> 1;                     // uiHD8
  const uint8_t kuiHD9      = (2 + kuiL1 + (kuiL2 << 1) + kuiL3) >> 2;      // uiHD9
  ENFORCE_STACK_ALIGN_1D (uint8_t, uiSrc, 16, 16) // TobeCont'd about assign opt as follows
  uiSrc[0] = uiSrc[6] = kuiHD0;
  uiSrc[1] = uiSrc[7] = kuiHD1;
  uiSrc[2] = kuiHD2;
  uiSrc[3] = kuiHD3;
  uiSrc[4] = uiSrc[10] = kuiHD4;
  uiSrc[5] = uiSrc[11] = kuiHD5;
  uiSrc[8] = uiSrc[14] = kuiHD6;
  uiSrc[9] = uiSrc[15] = kuiHD7;
  uiSrc[12] = kuiHD8;
  uiSrc[13] = kuiHD9;

  WelsFillingPred8x2to16 (pPred, uiSrc);
}



#define I8x8_PRED_STRIDE 8

void WelsIChromaPredV_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  const uint64_t kuiSrc64 = LD64 (&pRef[-kiStride]);

  ST64 (pPred     , kuiSrc64);
  ST64 (pPred + 8 , kuiSrc64);
  ST64 (pPred + 16, kuiSrc64);
  ST64 (pPred + 24, kuiSrc64);
  ST64 (pPred + 32, kuiSrc64);
  ST64 (pPred + 40, kuiSrc64);
  ST64 (pPred + 48, kuiSrc64);
  ST64 (pPred + 56, kuiSrc64);
}

void WelsIChromaPredH_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  int32_t iStridex7 = (kiStride << 3) - kiStride;
  int32_t iI8x8Stridex7 = (I8x8_PRED_STRIDE << 3) - I8x8_PRED_STRIDE;
  uint8_t i = 7;

  do {
    const uint8_t kuiLeft = pRef[iStridex7 - 1]; // pLeft value
    uint64_t kuiSrc64 = (uint64_t) (0x0101010101010101ULL * kuiLeft);
    ST64 (pPred + iI8x8Stridex7, kuiSrc64);

    iStridex7 -= kiStride;
    iI8x8Stridex7 -= I8x8_PRED_STRIDE;
  } while (i-- > 0);
}


void WelsIChromaPredPlane_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  int32_t iLTshift = 0, iTopshift = 0, iLeftshift = 0, iTopSum = 0, iLeftSum = 0;
  int32_t i, j;
  uint8_t* pTop = &pRef[-kiStride];
  uint8_t* pLeft = &pRef[-1];

  for (i = 0 ; i < 4 ; i ++) {
    iTopSum += (i + 1) * (pTop[4 + i] - pTop[2 - i]);
    iLeftSum += (i + 1) * (pLeft[ (4 + i) * kiStride] - pLeft[ (2 - i) * kiStride]);
  }

  iLTshift = (pLeft[7 * kiStride] + pTop[7]) << 4;
  iTopshift = (17 * iTopSum + 16) >> 5;
  iLeftshift = (17 * iLeftSum + 16) >> 5;

  for (i = 0 ; i < 8 ; i ++) {
    for (j = 0 ; j < 8 ; j ++) {
      pPred[j] = WelsClip1 ((iLTshift + iTopshift * (j - 3) + iLeftshift * (i - 3) + 16) >> 5);
    }
    pPred += I8x8_PRED_STRIDE;
  }
}


void WelsIChromaPredDc_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  const int32_t kuiL1 = kiStride - 1;
  const int32_t kuiL2 = kuiL1 + kiStride;
  const int32_t kuiL3 = kuiL2 + kiStride;
  const int32_t kuiL4 = kuiL3 + kiStride;
  const int32_t kuiL5 = kuiL4 + kiStride;
  const int32_t kuiL6 = kuiL5 + kiStride;
  const int32_t kuiL7 = kuiL6 + kiStride;
  /*caculate the iMean value*/
  const uint8_t kuiMean1 = (pRef[-kiStride] + pRef[1 - kiStride] + pRef[2 - kiStride] + pRef[3 - kiStride] +
                            pRef[-1] + pRef[kuiL1] + pRef[kuiL2] + pRef[kuiL3] + 4) >> 3;
  const uint32_t kuiSum2 = pRef[4 - kiStride] + pRef[5 - kiStride] + pRef[6 - kiStride] + pRef[7 - kiStride];
  const uint32_t kuiSum3 = pRef[kuiL4] + pRef[kuiL5] + pRef[kuiL6] + pRef[kuiL7];
  const uint8_t kuiMean2 = (kuiSum2 + 2) >> 2;
  const uint8_t kuiMean3 = (kuiSum3 + 2) >> 2;
  const uint8_t kuiMean4 = (kuiSum2 + kuiSum3 + 4) >> 3;

  const uint8_t kuiTopMean[8] = {kuiMean1, kuiMean1, kuiMean1, kuiMean1, kuiMean2, kuiMean2, kuiMean2, kuiMean2};
  const uint8_t kuiBottomMean[8] = {kuiMean3, kuiMean3, kuiMean3, kuiMean3, kuiMean4, kuiMean4, kuiMean4, kuiMean4};
  const uint64_t kuiTopMean64 = LD64 (kuiTopMean);
  const uint64_t kuiBottomMean64 = LD64 (kuiBottomMean);

  ST64 (pPred     , kuiTopMean64);
  ST64 (pPred + 8 , kuiTopMean64);
  ST64 (pPred + 16, kuiTopMean64);
  ST64 (pPred + 24, kuiTopMean64);
  ST64 (pPred + 32, kuiBottomMean64);
  ST64 (pPred + 40, kuiBottomMean64);
  ST64 (pPred + 48, kuiBottomMean64);
  ST64 (pPred + 56, kuiBottomMean64);
}

void WelsIChromaPredDcLeft_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  const int32_t kuiL1   = kiStride - 1;
  const int32_t kuiL2   = kuiL1 + kiStride;
  const int32_t kuiL3   = kuiL2 + kiStride;
  const int32_t kuiL4   = kuiL3 + kiStride;
  const int32_t kuiL5   = kuiL4 + kiStride;
  const int32_t kuiL6   = kuiL5 + kiStride;
  const int32_t kuiL7   = kuiL6 + kiStride;
  /*caculate the iMean value*/
  const uint8_t kuiTopMean          = (pRef[-1] + pRef[kuiL1] + pRef[kuiL2] + pRef[kuiL3] + 2) >> 2 ;
  const uint8_t kuiBottomMean       = (pRef[kuiL4] + pRef[kuiL5] + pRef[kuiL6] + pRef[kuiL7] + 2) >> 2;
  const uint64_t kuiTopMean64       = (uint64_t) (0x0101010101010101ULL * kuiTopMean);
  const uint64_t kuiBottomMean64    = (uint64_t) (0x0101010101010101ULL * kuiBottomMean);
  ST64 (pPred     , kuiTopMean64);
  ST64 (pPred + 8 , kuiTopMean64);
  ST64 (pPred + 16, kuiTopMean64);
  ST64 (pPred + 24, kuiTopMean64);
  ST64 (pPred + 32, kuiBottomMean64);
  ST64 (pPred + 40, kuiBottomMean64);
  ST64 (pPred + 48, kuiBottomMean64);
  ST64 (pPred + 56, kuiBottomMean64);
}

void WelsIChromaPredDcTop_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  /*caculate the iMean value*/
  const uint8_t kuiMean1 = (pRef[-kiStride] + pRef[1 - kiStride] + pRef[2 - kiStride] + pRef[3 - kiStride] + 2) >> 2;
  const uint8_t kuiMean2 = (pRef[4 - kiStride] + pRef[5 - kiStride] + pRef[6 - kiStride] + pRef[7 - kiStride] + 2) >> 2;
  const uint8_t kuiMean[8] = {kuiMean1, kuiMean1, kuiMean1, kuiMean1, kuiMean2, kuiMean2, kuiMean2, kuiMean2};
  const uint64_t kuiMean64 = LD64 (kuiMean);

  ST64 (pPred     , kuiMean64);
  ST64 (pPred + 8 , kuiMean64);
  ST64 (pPred + 16, kuiMean64);
  ST64 (pPred + 24, kuiMean64);
  ST64 (pPred + 32, kuiMean64);
  ST64 (pPred + 40, kuiMean64);
  ST64 (pPred + 48, kuiMean64);
  ST64 (pPred + 56, kuiMean64);
}

void WelsIChromaPredDcNA_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  const uint64_t kuiDcValue64 = (uint64_t)0x8080808080808080ULL;
  ST64 (pPred     , kuiDcValue64);
  ST64 (pPred + 8 , kuiDcValue64);
  ST64 (pPred + 16, kuiDcValue64);
  ST64 (pPred + 24, kuiDcValue64);
  ST64 (pPred + 32, kuiDcValue64);
  ST64 (pPred + 40, kuiDcValue64);
  ST64 (pPred + 48, kuiDcValue64);
  ST64 (pPred + 56, kuiDcValue64);
}


void WelsI16x16LumaPredPlane_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  int32_t iLTshift = 0, iTopshift = 0, iLeftshift = 0, iTopSum = 0, iLeftSum = 0;
  int32_t i, j;
  uint8_t* pTop = &pRef[-kiStride];
  uint8_t* pLeft = &pRef[-1];
  int32_t iPredStride = 16;

  for (i = 0 ; i < 8 ; i ++) {
    iTopSum += (i + 1) * (pTop[8 + i] - pTop[6 - i]);
    iLeftSum += (i + 1) * (pLeft[ (8 + i) * kiStride] - pLeft[ (6 - i) * kiStride]);
  }

  iLTshift = (pLeft[15 * kiStride] + pTop[15]) << 4;
  iTopshift = (5 * iTopSum + 32) >> 6;
  iLeftshift = (5 * iLeftSum + 32) >> 6;

  for (i = 0 ; i < 16 ; i ++) {
    for (j = 0 ; j < 16 ; j ++) {
      pPred[j] = WelsClip1 ((iLTshift + iTopshift * (j - 7) + iLeftshift * (i - 7) + 16) >> 5);
    }
    pPred += iPredStride;
  }
}

void WelsI16x16LumaPredDc_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  int32_t iStridex15 = (kiStride << 4) - kiStride;
  int32_t iSum = 0;
  uint8_t i = 15;
  uint8_t iMean = 0;

  /*caculate the iMean value*/
  do {
    iSum += pRef[-1 + iStridex15] + pRef[-kiStride + i];
    iStridex15 -= kiStride;
  } while (i-- > 0);
  iMean = (16 + iSum) >> 5;
  memset (pPred, iMean, 256);
}


void WelsI16x16LumaPredDcTop_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  int32_t iSum = 0;
  uint8_t i = 15;
  uint8_t iMean = 0;

  /*caculate the iMean value*/
  do {
    iSum += pRef[-kiStride + i];
  } while (i-- > 0);
  iMean = (8 + iSum) >> 4;
  memset (pPred, iMean, 256);
}

void WelsI16x16LumaPredDcLeft_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  int32_t iStridex15 = (kiStride << 4) - kiStride;
  int32_t iSum = 0;
  uint8_t i = 15;
  uint8_t iMean = 0;

  /*caculate the iMean value*/
  do {
    iSum += pRef[-1 + iStridex15];
    iStridex15 -= kiStride;
  } while (i-- > 0);
  iMean = (8 + iSum) >> 4;
  memset (pPred, iMean, 256);
}

void WelsI16x16LumaPredDcNA_c (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride) {
  memset (pPred, 0x80, 256);
}

void WelsInitIntraPredFuncs (SWelsFuncPtrList* pFuncList, const uint32_t kuiCpuFlag) {
  pFuncList->pfGetLumaI16x16Pred[I16_PRED_V] =      WelsI16x16LumaPredV_c;
  pFuncList->pfGetLumaI16x16Pred[I16_PRED_H] =      WelsI16x16LumaPredH_c;
  pFuncList->pfGetLumaI16x16Pred[I16_PRED_DC] =     WelsI16x16LumaPredDc_c;
  pFuncList->pfGetLumaI16x16Pred[I16_PRED_P] =      WelsI16x16LumaPredPlane_c;
  pFuncList->pfGetLumaI16x16Pred[I16_PRED_DC_L] =   WelsI16x16LumaPredDcLeft_c;
  pFuncList->pfGetLumaI16x16Pred[I16_PRED_DC_T] =   WelsI16x16LumaPredDcTop_c;
  pFuncList->pfGetLumaI16x16Pred[I16_PRED_DC_128] = WelsI16x16LumaPredDcNA_c;

  pFuncList->pfGetLumaI4x4Pred[I4_PRED_V] = WelsI4x4LumaPredV_c;
  pFuncList->pfGetLumaI4x4Pred[I4_PRED_H] = WelsI4x4LumaPredH_c;
  pFuncList->pfGetLumaI4x4Pred[I4_PRED_DC] = WelsI4x4LumaPredDc_c;
  pFuncList->pfGetLumaI4x4Pred[I4_PRED_DC_L] = WelsI4x4LumaPredDcLeft_c;
  pFuncList->pfGetLumaI4x4Pred[I4_PRED_DC_T] = WelsI4x4LumaPredDcTop_c;
  pFuncList->pfGetLumaI4x4Pred[I4_PRED_DC_128] = WelsI4x4LumaPredDcNA_c;

  pFuncList->pfGetLumaI4x4Pred[I4_PRED_DDL] = WelsI4x4LumaPredDDL_c;
  pFuncList->pfGetLumaI4x4Pred[I4_PRED_DDL_TOP] = WelsI4x4LumaPredDDLTop_c;
  pFuncList->pfGetLumaI4x4Pred[I4_PRED_DDR] = WelsI4x4LumaPredDDR_c;

  pFuncList->pfGetLumaI4x4Pred[I4_PRED_VL] = WelsI4x4LumaPredVL_c;
  pFuncList->pfGetLumaI4x4Pred[I4_PRED_VL_TOP] = WelsI4x4LumaPredVLTop_c;
  pFuncList->pfGetLumaI4x4Pred[I4_PRED_VR] = WelsI4x4LumaPredVR_c;
  pFuncList->pfGetLumaI4x4Pred[I4_PRED_HU] = WelsI4x4LumaPredHU_c;
  pFuncList->pfGetLumaI4x4Pred[I4_PRED_HD] = WelsI4x4LumaPredHD_c;

  pFuncList->pfGetChromaPred[C_PRED_DC] = WelsIChromaPredDc_c;
  pFuncList->pfGetChromaPred[C_PRED_H] = WelsIChromaPredH_c;
  pFuncList->pfGetChromaPred[C_PRED_V] = WelsIChromaPredV_c;
  pFuncList->pfGetChromaPred[C_PRED_P] = WelsIChromaPredPlane_c;
  pFuncList->pfGetChromaPred[C_PRED_DC_L] = WelsIChromaPredDcLeft_c;
  pFuncList->pfGetChromaPred[C_PRED_DC_T] = WelsIChromaPredDcTop_c;
  pFuncList->pfGetChromaPred[C_PRED_DC_128] = WelsIChromaPredDcNA_c;
#ifdef HAVE_NEON
  if (kuiCpuFlag & WELS_CPU_NEON) {
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_DDR] = WelsI4x4LumaPredDDR_neon;
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_HD]  = WelsI4x4LumaPredHD_neon;
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_HU]  = WelsI4x4LumaPredHU_neon;
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_VR]  = WelsI4x4LumaPredVR_neon;
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_DDL] = WelsI4x4LumaPredDDL_neon;
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_VL]  = WelsI4x4LumaPredVL_neon;
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_H] = WelsI4x4LumaPredH_neon;
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_V] = WelsI4x4LumaPredV_neon;

    pFuncList->pfGetLumaI16x16Pred[I16_PRED_V] = WelsI16x16LumaPredV_neon;
    pFuncList->pfGetLumaI16x16Pred[I16_PRED_H] = WelsI16x16LumaPredH_neon;
    pFuncList->pfGetLumaI16x16Pred[I16_PRED_DC] = WelsI16x16LumaPredDc_neon;
    pFuncList->pfGetLumaI16x16Pred[I16_PRED_P] = WelsI16x16LumaPredPlane_neon;

    pFuncList->pfGetChromaPred[C_PRED_DC]   = WelsIChromaPredDc_neon;
    pFuncList->pfGetChromaPred[C_PRED_V]    = WelsIChromaPredV_neon;
    pFuncList->pfGetChromaPred[C_PRED_P]    = WelsIChromaPredPlane_neon;
    pFuncList->pfGetChromaPred[C_PRED_H]    = WelsIChromaPredH_neon;
  }
#endif

#if defined(HAVE_NEON_AARCH64)
  if (kuiCpuFlag & WELS_CPU_NEON) {
    pFuncList->pfGetLumaI16x16Pred[I16_PRED_DC] = WelsI16x16LumaPredDc_AArch64_neon;
    pFuncList->pfGetLumaI16x16Pred[I16_PRED_P]  = WelsI16x16LumaPredPlane_AArch64_neon;
    pFuncList->pfGetLumaI16x16Pred[I16_PRED_H]  = WelsI16x16LumaPredH_AArch64_neon;
    pFuncList->pfGetLumaI16x16Pred[I16_PRED_V]  = WelsI16x16LumaPredV_AArch64_neon;
    pFuncList->pfGetLumaI16x16Pred[I16_PRED_DC_L]  = WelsI16x16LumaPredDcLeft_AArch64_neon;
    pFuncList->pfGetLumaI16x16Pred[I16_PRED_DC_T]  = WelsI16x16LumaPredDcTop_AArch64_neon;

    pFuncList->pfGetLumaI4x4Pred[I4_PRED_H    ] = WelsI4x4LumaPredH_AArch64_neon;
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_DDL  ] = WelsI4x4LumaPredDDL_AArch64_neon;
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_DDL_TOP] = WelsI4x4LumaPredDDLTop_AArch64_neon;
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_VL   ] = WelsI4x4LumaPredVL_AArch64_neon;
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_VL_TOP ] = WelsI4x4LumaPredVLTop_AArch64_neon;
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_VR   ] = WelsI4x4LumaPredVR_AArch64_neon;
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_HU   ] = WelsI4x4LumaPredHU_AArch64_neon;
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_HD   ] = WelsI4x4LumaPredHD_AArch64_neon;
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_DC   ] = WelsI4x4LumaPredDc_AArch64_neon;
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_DC_T   ] = WelsI4x4LumaPredDcTop_AArch64_neon;

    pFuncList->pfGetChromaPred[C_PRED_H]       = WelsIChromaPredH_AArch64_neon;
    pFuncList->pfGetChromaPred[C_PRED_V]       = WelsIChromaPredV_AArch64_neon;
    pFuncList->pfGetChromaPred[C_PRED_P ]      = WelsIChromaPredPlane_AArch64_neon;
    pFuncList->pfGetChromaPred[C_PRED_DC]      = WelsIChromaPredDc_AArch64_neon;
    pFuncList->pfGetChromaPred[C_PRED_DC_T]      = WelsIChromaPredDcTop_AArch64_neon;
  }
#endif//HAVE_NEON_AARCH64

#ifdef X86_ASM
  if (kuiCpuFlag & WELS_CPU_MMXEXT) {
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_DDR] = WelsI4x4LumaPredDDR_mmx;
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_HD]  = WelsI4x4LumaPredHD_mmx;
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_HU]  = WelsI4x4LumaPredHU_mmx;
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_VR]  = WelsI4x4LumaPredVR_mmx;
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_DDL] = WelsI4x4LumaPredDDL_mmx;
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_VL]  = WelsI4x4LumaPredVL_mmx;
    pFuncList->pfGetChromaPred[C_PRED_H] = WelsIChromaPredH_mmx;
  }
  if (kuiCpuFlag & WELS_CPU_SSE2) {
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_H] = WelsI4x4LumaPredH_sse2;
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_DC] = WelsI4x4LumaPredDc_sse2;
    pFuncList->pfGetLumaI4x4Pred[I4_PRED_V] = WelsI4x4LumaPredV_sse2;

    pFuncList->pfGetLumaI16x16Pred[I16_PRED_V] = WelsI16x16LumaPredV_sse2;
    pFuncList->pfGetLumaI16x16Pred[I16_PRED_H] = WelsI16x16LumaPredH_sse2;
    pFuncList->pfGetLumaI16x16Pred[I16_PRED_DC] = WelsI16x16LumaPredDc_sse2;
    pFuncList->pfGetLumaI16x16Pred[I16_PRED_P] = WelsI16x16LumaPredPlane_sse2;

    pFuncList->pfGetChromaPred[C_PRED_DC]   = WelsIChromaPredDc_sse2;
    pFuncList->pfGetChromaPred[C_PRED_V]    = WelsIChromaPredV_sse2;
    pFuncList->pfGetChromaPred[C_PRED_P]    = WelsIChromaPredPlane_sse2;
  }
#endif

#if defined(HAVE_MMI)
  if (kuiCpuFlag & WELS_CPU_MMI) {
    pFuncList->pfGetLumaI16x16Pred[I16_PRED_V] = WelsI16x16LumaPredV_mmi;
    pFuncList->pfGetLumaI16x16Pred[I16_PRED_H] = WelsI16x16LumaPredH_mmi;
    pFuncList->pfGetLumaI16x16Pred[I16_PRED_DC] = WelsI16x16LumaPredDc_mmi;
    pFuncList->pfGetLumaI16x16Pred[I16_PRED_P] = WelsI16x16LumaPredPlane_mmi;

    pFuncList->pfGetChromaPred[C_PRED_H] = WelsIChromaPredH_mmi;
    pFuncList->pfGetChromaPred[C_PRED_DC]   = WelsIChromaPredDc_mmi;
    pFuncList->pfGetChromaPred[C_PRED_V]    = WelsIChromaPredV_mmi;
    pFuncList->pfGetChromaPred[C_PRED_P]    = WelsIChromaPredPlane_mmi;
  }
#endif//HAVE_MMI
}
}
