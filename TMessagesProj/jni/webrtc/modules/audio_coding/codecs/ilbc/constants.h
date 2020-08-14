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

 constants.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_CONSTANTS_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_CONSTANTS_H_

#include "modules/audio_coding/codecs/ilbc/defines.h"

/* high pass filters */

extern const int16_t WebRtcIlbcfix_kHpInCoefs[];
extern const int16_t WebRtcIlbcfix_kHpOutCoefs[];

/* Window for start state decision */
extern const int16_t WebRtcIlbcfix_kStartSequenceEnrgWin[];

/* low pass filter used for downsampling */
extern const int16_t WebRtcIlbcfix_kLpFiltCoefs[];

/* LPC analysis and quantization */

extern const int16_t WebRtcIlbcfix_kLpcWin[];
extern const int16_t WebRtcIlbcfix_kLpcAsymWin[];
extern const int32_t WebRtcIlbcfix_kLpcLagWin[];
extern const int16_t WebRtcIlbcfix_kLpcChirpSyntDenum[];
extern const int16_t WebRtcIlbcfix_kLpcChirpWeightDenum[];
extern const int16_t WebRtcIlbcfix_kLsfDimCb[];
extern const int16_t WebRtcIlbcfix_kLsfSizeCb[];
extern const int16_t WebRtcIlbcfix_kLsfCb[];
extern const int16_t WebRtcIlbcfix_kLsfWeight20ms[];
extern const int16_t WebRtcIlbcfix_kLsfWeight30ms[];
extern const int16_t WebRtcIlbcfix_kLsfMean[];
extern const int16_t WebRtcIlbcfix_kLspMean[];
extern const int16_t WebRtcIlbcfix_kCos[];
extern const int16_t WebRtcIlbcfix_kCosDerivative[];
extern const int16_t WebRtcIlbcfix_kCosGrid[];
extern const int16_t WebRtcIlbcfix_kAcosDerivative[];

/* state quantization tables */

extern const int16_t WebRtcIlbcfix_kStateSq3[];
extern const int32_t WebRtcIlbcfix_kChooseFrgQuant[];
extern const int16_t WebRtcIlbcfix_kScale[];
extern const int16_t WebRtcIlbcfix_kFrgQuantMod[];

/* Ranges for search and filters at different subframes */

extern const size_t WebRtcIlbcfix_kSearchRange[5][CB_NSTAGES];
extern const size_t WebRtcIlbcfix_kFilterRange[];

/* gain quantization tables */

extern const int16_t WebRtcIlbcfix_kGainSq3[];
extern const int16_t WebRtcIlbcfix_kGainSq4[];
extern const int16_t WebRtcIlbcfix_kGainSq5[];
extern const int16_t WebRtcIlbcfix_kGainSq5Sq[];
extern const int16_t* const WebRtcIlbcfix_kGain[];

/* adaptive codebook definitions */

extern const int16_t WebRtcIlbcfix_kCbFiltersRev[];
extern const int16_t WebRtcIlbcfix_kAlpha[];

/* enhancer definitions */

extern const int16_t WebRtcIlbcfix_kEnhPolyPhaser[ENH_UPS0]
                                                 [ENH_FLO_MULT2_PLUS1];
extern const int16_t WebRtcIlbcfix_kEnhWt[];
extern const size_t WebRtcIlbcfix_kEnhPlocs[];

/* PLC tables */

extern const int16_t WebRtcIlbcfix_kPlcPerSqr[];
extern const int16_t WebRtcIlbcfix_kPlcPitchFact[];
extern const int16_t WebRtcIlbcfix_kPlcPfSlope[];

#endif
