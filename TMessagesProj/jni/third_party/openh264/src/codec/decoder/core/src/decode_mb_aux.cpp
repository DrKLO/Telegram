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

#include <string.h>

#include "decode_mb_aux.h"
#include "wels_common_basis.h"

namespace WelsDec {

//NOTE::: p_RS should NOT be modified and it will lead to mismatch with JSVM.
//        so should allocate kA array to store the temporary value (idct).
void IdctResAddPred_c (uint8_t* pPred, const int32_t kiStride, int16_t* pRs) {
  int16_t iSrc[16];

  uint8_t* pDst           = pPred;
  const int32_t kiStride2 = kiStride << 1;
  const int32_t kiStride3 = kiStride + kiStride2;
  int32_t i;

  for (i = 0; i < 4; i++) {
    const int32_t kiY  = i << 2;
    const int32_t kiT0 = pRs[kiY] + pRs[kiY + 2];
    const int32_t kiT1 = pRs[kiY] - pRs[kiY + 2];
    const int32_t kiT2 = (pRs[kiY + 1] >> 1) - pRs[kiY + 3];
    const int32_t kiT3 = pRs[kiY + 1] + (pRs[kiY + 3] >> 1);

    iSrc[kiY] = kiT0 + kiT3;
    iSrc[kiY + 1] = kiT1 + kiT2;
    iSrc[kiY + 2] = kiT1 - kiT2;
    iSrc[kiY + 3] = kiT0 - kiT3;
  }

  for (i = 0; i < 4; i++) {
    int32_t kT1 = iSrc[i]     +  iSrc[i + 8];
    int32_t kT2 = iSrc[i + 4] + (iSrc[i + 12] >> 1);
    int32_t kT3 = (32 + kT1 + kT2) >> 6;
    int32_t kT4 = (32 + kT1 - kT2) >> 6;

    pDst[i] = WelsClip1 (kT3 + pPred[i]);
    pDst[i + kiStride3] = WelsClip1 (kT4 + pPred[i + kiStride3]);

    kT1 = iSrc[i] - iSrc[i + 8];
    kT2 = (iSrc[i + 4] >> 1) - iSrc[i + 12];
    pDst[i + kiStride] = WelsClip1 (((32 + kT1 + kT2) >> 6) + pDst[i + kiStride]);
    pDst[i + kiStride2] = WelsClip1 (((32 + kT1 - kT2) >> 6) + pDst[i + kiStride2]);
  }
}

void IdctResAddPred8x8_c (uint8_t* pPred, const int32_t kiStride, int16_t* pRs) {
  // To make the ASM code easy to write, should using one funciton to apply hor and ver together, such as we did on HEVC
  // Ugly code, just for easy debug, the final version need optimization
  int16_t p[8], b[8];
  int16_t a[4];

  int16_t iTmp[64];
  int16_t iRes[64];

  // Horizontal
  for (int i = 0; i < 8; i++) {
    for (int j = 0; j < 8; j++) {
      p[j] = pRs[j + (i << 3)];
    }
    a[0] = p[0] + p[4];
    a[1] = p[0] - p[4];
    a[2] = p[6] - (p[2] >> 1);
    a[3] = p[2] + (p[6] >> 1);

    b[0] =  a[0] + a[3];
    b[2] =  a[1] - a[2];
    b[4] =  a[1] + a[2];
    b[6] =  a[0] - a[3];

    a[0] = -p[3] + p[5] - p[7] - (p[7] >> 1);
    a[1] =  p[1] + p[7] - p[3] - (p[3] >> 1);
    a[2] = -p[1] + p[7] + p[5] + (p[5] >> 1);
    a[3] =  p[3] + p[5] + p[1] + (p[1] >> 1);

    b[1] =  a[0] + (a[3] >> 2);
    b[3] =  a[1] + (a[2] >> 2);
    b[5] =  a[2] - (a[1] >> 2);
    b[7] =  a[3] - (a[0] >> 2);

    iTmp[0 + (i << 3)] = b[0] + b[7];
    iTmp[1 + (i << 3)] = b[2] - b[5];
    iTmp[2 + (i << 3)] = b[4] + b[3];
    iTmp[3 + (i << 3)] = b[6] + b[1];
    iTmp[4 + (i << 3)] = b[6] - b[1];
    iTmp[5 + (i << 3)] = b[4] - b[3];
    iTmp[6 + (i << 3)] = b[2] + b[5];
    iTmp[7 + (i << 3)] = b[0] - b[7];
  }

  //Vertical
  for (int i = 0; i < 8; i++) {
    for (int j = 0; j < 8; j++) {
      p[j] = iTmp[i + (j << 3)];
    }

    a[0] =  p[0] + p[4];
    a[1] =  p[0] - p[4];
    a[2] =  p[6] - (p[2] >> 1);
    a[3] =  p[2] + (p[6] >> 1);

    b[0] = a[0] + a[3];
    b[2] = a[1] - a[2];
    b[4] = a[1] + a[2];
    b[6] = a[0] - a[3];

    a[0] = -p[3] + p[5] - p[7] - (p[7] >> 1);
    a[1] =  p[1] + p[7] - p[3] - (p[3] >> 1);
    a[2] = -p[1] + p[7] + p[5] + (p[5] >> 1);
    a[3] =  p[3] + p[5] + p[1] + (p[1] >> 1);


    b[1] =  a[0] + (a[3] >> 2);
    b[7] =  a[3] - (a[0] >> 2);
    b[3] =  a[1] + (a[2] >> 2);
    b[5] =  a[2] - (a[1] >> 2);

    iRes[ (0 << 3) + i] = b[0] + b[7];
    iRes[ (1 << 3) + i] = b[2] - b[5];
    iRes[ (2 << 3) + i] = b[4] + b[3];
    iRes[ (3 << 3) + i] = b[6] + b[1];
    iRes[ (4 << 3) + i] = b[6] - b[1];
    iRes[ (5 << 3) + i] = b[4] - b[3];
    iRes[ (6 << 3) + i] = b[2] + b[5];
    iRes[ (7 << 3) + i] = b[0] - b[7];
  }

  uint8_t* pDst = pPred;
  for (int i = 0; i < 8; i++) {
    for (int j = 0; j < 8; j++) {
      pDst[i * kiStride + j] = WelsClip1 (((32 + iRes[ (i << 3) + j]) >> 6) + pDst[i * kiStride + j]);
    }
  }

}

void GetI4LumaIChromaAddrTable (int32_t* pBlockOffset, const int32_t kiYStride, const int32_t kiUVStride) {
  int32_t* pOffset = pBlockOffset;
  int32_t i;
  const uint8_t kuiScan0 = g_kuiScan8[0];

  for (i = 0; i < 16; i++) {
    const uint32_t kuiA = g_kuiScan8[i] - kuiScan0;
    const uint32_t kuiX = kuiA & 0x07;
    const uint32_t kuiY = kuiA >> 3;

    pOffset[i] = (kuiX + kiYStride * kuiY) << 2;
  }

  for (i = 0; i < 4; i++) {
    const uint32_t kuiA = g_kuiScan8[i] - kuiScan0;

    pOffset[16 + i] =
      pOffset[20 + i] = ((kuiA & 0x07) + (kiUVStride/*>>1*/) * (kuiA >> 3)) << 2;
  }
}

} // namespace WelsDec
