/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/******************************************************************

 iLBC Speech Coder ANSI-C Source Code

 WebRtcIlbcfix_CbMemEnergyAugmentation.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/cb_mem_energy_augmentation.h"

#include "modules/audio_coding/codecs/ilbc/constants.h"
#include "modules/audio_coding/codecs/ilbc/defines.h"

void WebRtcIlbcfix_CbMemEnergyAugmentation(
    int16_t *interpSamples, /* (i) The interpolated samples */
    int16_t *CBmem,   /* (i) The CB memory */
    int scale,   /* (i) The scaling of all energy values */
    size_t base_size,  /* (i) Index to where energy values should be stored */
    int16_t *energyW16,  /* (o) Energy in the CB vectors */
    int16_t *energyShifts /* (o) Shift value of the energy */
                                           ){
  int32_t energy, tmp32;
  int16_t *ppe, *pp, *interpSamplesPtr;
  int16_t *CBmemPtr;
  size_t lagcount;
  int16_t *enPtr=&energyW16[base_size-20];
  int16_t *enShPtr=&energyShifts[base_size-20];
  int32_t nrjRecursive;

  CBmemPtr = CBmem+147;
  interpSamplesPtr = interpSamples;

  /* Compute the energy for the first (low-5) noninterpolated samples */
  nrjRecursive = WebRtcSpl_DotProductWithScale( CBmemPtr-19, CBmemPtr-19, 15, scale);
  ppe = CBmemPtr - 20;

  for (lagcount=20; lagcount<=39; lagcount++) {

    /* Update the energy recursively to save complexity */
    nrjRecursive += (*ppe * *ppe) >> scale;
    ppe--;
    energy = nrjRecursive;

    /* interpolation */
    energy += WebRtcSpl_DotProductWithScale(interpSamplesPtr, interpSamplesPtr, 4, scale);
    interpSamplesPtr += 4;

    /* Compute energy for the remaining samples */
    pp = CBmemPtr - lagcount;
    energy += WebRtcSpl_DotProductWithScale(pp, pp, SUBL-lagcount, scale);

    /* Normalize the energy and store the number of shifts */
    (*enShPtr) = (int16_t)WebRtcSpl_NormW32(energy);
    tmp32 = energy << *enShPtr;
    *enPtr = (int16_t)(tmp32 >> 16);
    enShPtr++;
    enPtr++;
  }
}
