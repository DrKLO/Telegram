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
 * lpc_shape_swb16_tables.h
 *
 * This file declares tables used for entropy coding of LPC shape of
 * upper-band signal if the bandwidth is 16 kHz.
 *
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_LPC_SHAPE_SWB16_TABLES_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_LPC_SHAPE_SWB16_TABLES_H_

#include <stdint.h>

#include "modules/audio_coding/codecs/isac/main/source/settings.h"

extern const double WebRtcIsac_kMeanLarUb16[UB_LPC_ORDER];

extern const double WebRtcIsac_kIintraVecDecorrMatUb16[UB_LPC_ORDER]
                                                      [UB_LPC_ORDER];

extern const double WebRtcIsac_kInterVecDecorrMatUb16[UB16_LPC_VEC_PER_FRAME]
                                                     [UB16_LPC_VEC_PER_FRAME];

extern const uint16_t WebRtcIsac_kLpcShapeCdfVec01Ub16[14];

extern const uint16_t WebRtcIsac_kLpcShapeCdfVec1Ub16[16];

extern const uint16_t WebRtcIsac_kLpcShapeCdfVec2Ub16[18];

extern const uint16_t WebRtcIsac_kLpcShapeCdfVec3Ub16[30];

extern const uint16_t WebRtcIsac_kLpcShapeCdfVec4Ub16[16];

extern const uint16_t WebRtcIsac_kLpcShapeCdfVec5Ub16[17];

extern const uint16_t WebRtcIsac_kLpcShapeCdfVec6Ub16[21];

extern const uint16_t WebRtcIsac_kLpcShapeCdfVec7Ub16[36];

extern const uint16_t WebRtcIsac_kLpcShapeCdfVec8Ub16[21];

extern const uint16_t WebRtcIsac_kLpcShapeCdfVec01Ub160[21];

extern const uint16_t WebRtcIsac_kLpcShapeCdfVec01Ub161[28];

extern const uint16_t WebRtcIsac_kLpcShapeCdfVec01Ub162[55];

extern const uint16_t WebRtcIsac_kLpcShapeCdfVec01Ub163[26];

extern const uint16_t WebRtcIsac_kLpcShapeCdfVec01Ub164[28];

extern const uint16_t WebRtcIsac_kLpcShapeCdfVec01Ub165[34];

extern const uint16_t WebRtcIsac_kLpcShapeCdfVec01Ub166[71];

extern const uint16_t*
    WebRtcIsac_kLpcShapeCdfMatUb16[UB_LPC_ORDER * UB16_LPC_VEC_PER_FRAME];

extern const double
    WebRtcIsac_kLpcShapeLeftRecPointUb16[UB_LPC_ORDER * UB16_LPC_VEC_PER_FRAME];

extern const int16_t
    WebRtcIsac_kLpcShapeNumRecPointUb16[UB_LPC_ORDER * UB16_LPC_VEC_PER_FRAME];

extern const uint16_t
    WebRtcIsac_kLpcShapeEntropySearchUb16[UB_LPC_ORDER *
                                          UB16_LPC_VEC_PER_FRAME];

extern const double WebRtcIsac_kLpcShapeQStepSizeUb16;

#endif  // MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_LPC_SHAPE_SWB16_TABLES_H_
