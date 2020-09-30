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
 * SWB_KLT_Tables_LPCGain.h
 *
 * This file declares tables used for entropy coding of LPC Gain
 * of upper-band.
 *
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_LPC_GAIN_SWB_TABLES_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_LPC_GAIN_SWB_TABLES_H_

#include <stdint.h>

#include "modules/audio_coding/codecs/isac/main/source/settings.h"

extern const double WebRtcIsac_kQSizeLpcGain;

extern const double WebRtcIsac_kLeftRecPointLpcGain[SUBFRAMES];

extern const int16_t WebRtcIsac_kNumQCellLpcGain[SUBFRAMES];

extern const uint16_t WebRtcIsac_kLpcGainEntropySearch[SUBFRAMES];

extern const uint16_t WebRtcIsac_kLpcGainCdfVec0[18];

extern const uint16_t WebRtcIsac_kLpcGainCdfVec1[21];

extern const uint16_t WebRtcIsac_kLpcGainCdfVec2[26];

extern const uint16_t WebRtcIsac_kLpcGainCdfVec3[46];

extern const uint16_t WebRtcIsac_kLpcGainCdfVec4[78];

extern const uint16_t WebRtcIsac_kLpcGainCdfVec5[171];

extern const uint16_t* WebRtcIsac_kLpcGainCdfMat[SUBFRAMES];

extern const double WebRtcIsac_kLpcGainDecorrMat[SUBFRAMES][SUBFRAMES];

#endif  // MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_LPC_GAIN_SWB_TABLES_H_
