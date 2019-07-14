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
 * spectrum_ar_model_tables.h
 *
 * This file contains definitions of tables with AR coefficients,
 * Gain coefficients and cosine tables.
 *
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_SPECTRUM_AR_MODEL_TABLES_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_SPECTRUM_AR_MODEL_TABLES_H_

#include "modules/audio_coding/codecs/isac/main/source/structs.h"

#define NUM_AR_RC_QUANT_BAUNDARY 12

/********************* AR Coefficient Tables ************************/
/* cdf for quantized reflection coefficient 1 */
extern const uint16_t WebRtcIsac_kQArRc1Cdf[NUM_AR_RC_QUANT_BAUNDARY];

/* cdf for quantized reflection coefficient 2 */
extern const uint16_t WebRtcIsac_kQArRc2Cdf[NUM_AR_RC_QUANT_BAUNDARY];

/* cdf for quantized reflection coefficient 3 */
extern const uint16_t WebRtcIsac_kQArRc3Cdf[NUM_AR_RC_QUANT_BAUNDARY];

/* cdf for quantized reflection coefficient 4 */
extern const uint16_t WebRtcIsac_kQArRc4Cdf[NUM_AR_RC_QUANT_BAUNDARY];

/* cdf for quantized reflection coefficient 5 */
extern const uint16_t WebRtcIsac_kQArRc5Cdf[NUM_AR_RC_QUANT_BAUNDARY];

/* cdf for quantized reflection coefficient 6 */
extern const uint16_t WebRtcIsac_kQArRc6Cdf[NUM_AR_RC_QUANT_BAUNDARY];

/* quantization boundary levels for reflection coefficients */
extern const int16_t WebRtcIsac_kQArBoundaryLevels[NUM_AR_RC_QUANT_BAUNDARY];

/* initial indices for AR reflection coefficient quantizer and cdf table search
 */
extern const uint16_t WebRtcIsac_kQArRcInitIndex[AR_ORDER];

/* pointers to AR cdf tables */
extern const uint16_t* WebRtcIsac_kQArRcCdfPtr[AR_ORDER];

/* pointers to AR representation levels tables */
extern const int16_t* WebRtcIsac_kQArRcLevelsPtr[AR_ORDER];

/******************** GAIN Coefficient Tables ***********************/
/* cdf for Gain coefficient */
extern const uint16_t WebRtcIsac_kQGainCdf[19];

/* representation levels for quantized Gain coefficient */
extern const int32_t WebRtcIsac_kQGain2Levels[18];

/* squared quantization boundary levels for Gain coefficient */
extern const int32_t WebRtcIsac_kQGain2BoundaryLevels[19];

/* pointer to Gain cdf table */
extern const uint16_t* WebRtcIsac_kQGainCdf_ptr[1];

/* Gain initial index for gain quantizer and cdf table search */
extern const uint16_t WebRtcIsac_kQGainInitIndex[1];

/************************* Cosine Tables ****************************/
/* Cosine table */
extern const int16_t WebRtcIsac_kCos[6][60];

#endif /* MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_SPECTRUM_AR_MODEL_TABLES_H_ \
        */
