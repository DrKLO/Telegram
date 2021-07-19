/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * initialize.c
 *
 * Internal initfunctions
 *
 */

#include "modules/audio_coding/codecs/isac/fix/source/codec.h"
#include "modules/audio_coding/codecs/isac/fix/source/structs.h"
#include "modules/audio_coding/codecs/isac/fix/source/pitch_estimator.h"


void WebRtcIsacfix_InitMaskingEnc(MaskFiltstr_enc *maskdata) {

  int k;

  for (k = 0; k < WINLEN; k++) {
    maskdata->DataBufferLoQ0[k] = (int16_t) 0;
    maskdata->DataBufferHiQ0[k] = (int16_t) 0;
  }
  for (k = 0; k < ORDERLO+1; k++) {
    maskdata->CorrBufLoQQ[k] = (int32_t) 0;
    maskdata->CorrBufLoQdom[k] = 0;

    maskdata->PreStateLoGQ15[k] = 0;

  }
  for (k = 0; k < ORDERHI+1; k++) {
    maskdata->CorrBufHiQQ[k] = (int32_t) 0;
    maskdata->CorrBufHiQdom[k] = 0;
    maskdata->PreStateHiGQ15[k] = 0;
  }

  maskdata->OldEnergy = 10;

  return;
}

void WebRtcIsacfix_InitMaskingDec(MaskFiltstr_dec *maskdata) {

  int k;

  for (k = 0; k < ORDERLO+1; k++)
  {
    maskdata->PostStateLoGQ0[k] = 0;
  }
  for (k = 0; k < ORDERHI+1; k++)
  {
    maskdata->PostStateHiGQ0[k] = 0;
  }

  maskdata->OldEnergy = 10;

  return;
}







void WebRtcIsacfix_InitPreFilterbank(PreFiltBankstr *prefiltdata)
{
  int k;

  for (k = 0; k < QLOOKAHEAD; k++) {
    prefiltdata->INLABUF1_fix[k] = 0;
    prefiltdata->INLABUF2_fix[k] = 0;
  }
  for (k = 0; k < 2 * (QORDER - 1); k++) {
    prefiltdata->INSTAT1_fix[k] = 0;
    prefiltdata->INSTAT2_fix[k] = 0;
  }

  /* High pass filter states */
  prefiltdata->HPstates_fix[0] = 0;
  prefiltdata->HPstates_fix[1] = 0;

  return;
}

void WebRtcIsacfix_InitPostFilterbank(PostFiltBankstr *postfiltdata)
{
  int k;

  for (k = 0; k < 2 * POSTQORDER; k++) {
    postfiltdata->STATE_0_LOWER_fix[k] = 0;
    postfiltdata->STATE_0_UPPER_fix[k] = 0;
  }

  /* High pass filter states */

  postfiltdata->HPstates1_fix[0] = 0;
  postfiltdata->HPstates1_fix[1] = 0;

  postfiltdata->HPstates2_fix[0] = 0;
  postfiltdata->HPstates2_fix[1] = 0;

  return;
}


void WebRtcIsacfix_InitPitchFilter(PitchFiltstr *pitchfiltdata)
{
  int k;

  for (k = 0; k < PITCH_BUFFSIZE; k++)
    pitchfiltdata->ubufQQ[k] = 0;
  for (k = 0; k < (PITCH_DAMPORDER); k++)
    pitchfiltdata->ystateQQ[k] = 0;

  pitchfiltdata->oldlagQ7 = 6400; /* 50.0 in Q7 */
  pitchfiltdata->oldgainQ12 = 0;
}

void WebRtcIsacfix_InitPitchAnalysis(PitchAnalysisStruct *State)
{
  int k;

  for (k = 0; k < PITCH_CORR_LEN2+PITCH_CORR_STEP2+PITCH_MAX_LAG/2-PITCH_FRAME_LEN/2+2; k++) {
    State->dec_buffer16[k] = 0;
  }
  for (k = 0; k < 2 * ALLPASSSECTIONS + 1; k++) {
    State->decimator_state32[k] = 0;
  }

  for (k = 0; k < QLOOKAHEAD; k++)
    State->inbuf[k] = 0;

  WebRtcIsacfix_InitPitchFilter(&(State->PFstr_wght));

  WebRtcIsacfix_InitPitchFilter(&(State->PFstr));
}


void WebRtcIsacfix_InitPlc( PLCstr *State )
{
  State->decayCoeffPriodic = WEBRTC_SPL_WORD16_MAX;
  State->decayCoeffNoise = WEBRTC_SPL_WORD16_MAX;

  State->used = PLC_WAS_USED;

  WebRtcSpl_ZerosArrayW16(State->overlapLP, RECOVERY_OVERLAP);
  WebRtcSpl_ZerosArrayW16(State->lofilt_coefQ15, ORDERLO);
  WebRtcSpl_ZerosArrayW16(State->hifilt_coefQ15, ORDERHI );

  State->AvgPitchGain_Q12 = 0;
  State->lastPitchGain_Q12 = 0;
  State->lastPitchLag_Q7 = 0;
  State->gain_lo_hiQ17[0]=State->gain_lo_hiQ17[1] = 0;
  WebRtcSpl_ZerosArrayW16(State->prevPitchInvIn, FRAMESAMPLES/2);
  WebRtcSpl_ZerosArrayW16(State->prevPitchInvOut, PITCH_MAX_LAG + 10 );
  WebRtcSpl_ZerosArrayW32(State->prevHP, PITCH_MAX_LAG + 10 );
  State->pitchCycles = 0;
  State->A = 0;
  State->B = 0;
  State->pitchIndex = 0;
  State->stretchLag = 240;
  State->seed = 4447;


}
