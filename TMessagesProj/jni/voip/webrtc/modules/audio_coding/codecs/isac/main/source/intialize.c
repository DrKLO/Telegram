/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/* encode.c  - Encoding function for the iSAC coder */

#include <math.h>

#include "modules/audio_coding/codecs/isac/main/source/structs.h"
#include "modules/audio_coding/codecs/isac/main/source/codec.h"
#include "modules/audio_coding/codecs/isac/main/source/pitch_estimator.h"

void WebRtcIsac_InitMasking(MaskFiltstr *maskdata) {

  int k;

  for (k = 0; k < WINLEN; k++) {
    maskdata->DataBufferLo[k] = 0.0;
    maskdata->DataBufferHi[k] = 0.0;
  }
  for (k = 0; k < ORDERLO+1; k++) {
    maskdata->CorrBufLo[k] = 0.0;
    maskdata->PreStateLoF[k] = 0.0;
    maskdata->PreStateLoG[k] = 0.0;
    maskdata->PostStateLoF[k] = 0.0;
    maskdata->PostStateLoG[k] = 0.0;
  }
  for (k = 0; k < ORDERHI+1; k++) {
    maskdata->CorrBufHi[k] = 0.0;
    maskdata->PreStateHiF[k] = 0.0;
    maskdata->PreStateHiG[k] = 0.0;
    maskdata->PostStateHiF[k] = 0.0;
    maskdata->PostStateHiG[k] = 0.0;
  }

  maskdata->OldEnergy = 10.0;
  return;
}

void WebRtcIsac_InitPostFilterbank(PostFiltBankstr *postfiltdata)
{
  int k;

  for (k = 0; k < 2*POSTQORDER; k++) {
    postfiltdata->STATE_0_LOWER[k] = 0;
    postfiltdata->STATE_0_UPPER[k] = 0;

    postfiltdata->STATE_0_LOWER_float[k] = 0;
    postfiltdata->STATE_0_UPPER_float[k] = 0;
  }

  /* High pass filter states */
  postfiltdata->HPstates1[0] = 0.0;
  postfiltdata->HPstates1[1] = 0.0;

  postfiltdata->HPstates2[0] = 0.0;
  postfiltdata->HPstates2[1] = 0.0;

  postfiltdata->HPstates1_float[0] = 0.0f;
  postfiltdata->HPstates1_float[1] = 0.0f;

  postfiltdata->HPstates2_float[0] = 0.0f;
  postfiltdata->HPstates2_float[1] = 0.0f;

  return;
}
