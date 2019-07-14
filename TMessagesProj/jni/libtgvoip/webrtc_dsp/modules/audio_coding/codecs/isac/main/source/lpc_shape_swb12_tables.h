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
 * lpc_shape_swb12_tables.h
 *
 * This file declares tables used for entropy coding of LPC shape of
 * upper-band signal if the bandwidth is 12 kHz.
 *
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_LPC_SHAPE_SWB12_TABLES_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_LPC_SHAPE_SWB12_TABLES_H_

#include <stdint.h>

#include "modules/audio_coding/codecs/isac/main/source/settings.h"

extern const double WebRtcIsac_kMeanLarUb12[UB_LPC_ORDER];

extern const double WebRtcIsac_kMeanLpcGain;

extern const double WebRtcIsac_kIntraVecDecorrMatUb12[UB_LPC_ORDER]
                                                     [UB_LPC_ORDER];

extern const double WebRtcIsac_kInterVecDecorrMatUb12[UB_LPC_VEC_PER_FRAME]
                                                     [UB_LPC_VEC_PER_FRAME];

extern const double WebRtcIsac_kLpcShapeQStepSizeUb12;

extern const double
    WebRtcIsac_kLpcShapeLeftRecPointUb12[UB_LPC_ORDER * UB_LPC_VEC_PER_FRAME];

extern const int16_t
    WebRtcIsac_kLpcShapeNumRecPointUb12[UB_LPC_ORDER * UB_LPC_VEC_PER_FRAME];

extern const uint16_t
    WebRtcIsac_kLpcShapeEntropySearchUb12[UB_LPC_ORDER * UB_LPC_VEC_PER_FRAME];

extern const uint16_t WebRtcIsac_kLpcShapeCdfVec0Ub12[14];

extern const uint16_t WebRtcIsac_kLpcShapeCdfVec1Ub12[16];

extern const uint16_t WebRtcIsac_kLpcShapeCdfVec2Ub12[20];

extern const uint16_t WebRtcIsac_kLpcShapeCdfVec3Ub12[28];

extern const uint16_t WebRtcIsac_kLpcShapeCdfVec4Ub12[20];

extern const uint16_t WebRtcIsac_kLpcShapeCdfVec5Ub12[25];

extern const uint16_t WebRtcIsac_kLpcShapeCdfVec6Ub12[33];

extern const uint16_t WebRtcIsac_kLpcShapeCdfVec7Ub12[49];

extern const uint16_t*
    WebRtcIsac_kLpcShapeCdfMatUb12[UB_LPC_ORDER * UB_LPC_VEC_PER_FRAME];

#endif  // MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_LPC_SHAPE_SWB12_TABLES_H_
