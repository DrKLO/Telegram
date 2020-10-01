/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * pitch_estimator.h
 *
 * Pitch functions
 *
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_PITCH_ESTIMATOR_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_PITCH_ESTIMATOR_H_

#include "modules/audio_coding/codecs/isac/fix/source/structs.h"

void WebRtcIsacfix_PitchAnalysis(
    const int16_t* in, /* PITCH_FRAME_LEN samples */
    int16_t* outQ0,    /* PITCH_FRAME_LEN+QLOOKAHEAD samples */
    PitchAnalysisStruct* State,
    int16_t* lagsQ7,
    int16_t* PitchGains_Q12);

void WebRtcIsacfix_InitialPitch(const int16_t* in,
                                PitchAnalysisStruct* State,
                                int16_t* qlags);

void WebRtcIsacfix_PitchFilter(int16_t* indatFix,
                               int16_t* outdatQQ,
                               PitchFiltstr* pfp,
                               int16_t* lagsQ7,
                               int16_t* gainsQ12,
                               int16_t type);

void WebRtcIsacfix_PitchFilterCore(int loopNumber,
                                   int16_t gain,
                                   size_t index,
                                   int16_t sign,
                                   int16_t* inputState,
                                   int16_t* outputBuff2,
                                   const int16_t* coefficient,
                                   int16_t* inputBuf,
                                   int16_t* outputBuf,
                                   int* index2);

void WebRtcIsacfix_PitchFilterGains(const int16_t* indatQ0,
                                    PitchFiltstr* pfp,
                                    int16_t* lagsQ7,
                                    int16_t* gainsQ12);

void WebRtcIsacfix_DecimateAllpass32(
    const int16_t* in,
    int32_t* state_in, /* array of size: 2*ALLPASSSECTIONS+1 */
    int16_t N,         /* number of input samples */
    int16_t* out);     /* array of size N/2 */

int32_t WebRtcIsacfix_Log2Q8(uint32_t x);

void WebRtcIsacfix_PCorr2Q32(const int16_t* in, int32_t* logcorQ8);

#endif /* MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_PITCH_ESTIMATOR_H_ */
