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
 * lpc_tables.h
 *
 * header file for coding tables for the LPC coefficients
 *
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_LPC_TABLES_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_LPC_TABLES_H_

#include "modules/audio_coding/codecs/isac/main/source/structs.h"

#include "modules/audio_coding/codecs/isac/main/source/settings.h"

#define KLT_STEPSIZE 1.00000000
#define KLT_NUM_AVG_GAIN 0
#define KLT_NUM_AVG_SHAPE 0
#define KLT_NUM_MODELS 3
#define LPC_GAIN_SCALE 4.000f
#define LPC_LOBAND_SCALE 2.100f
#define LPC_LOBAND_ORDER ORDERLO
#define LPC_HIBAND_SCALE 0.450f
#define LPC_HIBAND_ORDER ORDERHI
#define LPC_GAIN_ORDER 2

#define LPC_SHAPE_ORDER (LPC_LOBAND_ORDER + LPC_HIBAND_ORDER)

#define KLT_ORDER_GAIN (LPC_GAIN_ORDER * SUBFRAMES)
#define KLT_ORDER_SHAPE (LPC_SHAPE_ORDER * SUBFRAMES)

/* cdf array for model indicator */
extern const uint16_t WebRtcIsac_kQKltModelCdf[KLT_NUM_MODELS + 1];

/* pointer to cdf array for model indicator */
extern const uint16_t* WebRtcIsac_kQKltModelCdfPtr[1];

/* initial cdf index for decoder of model indicator */
extern const uint16_t WebRtcIsac_kQKltModelInitIndex[1];

/* offset to go from rounded value to quantization index */
extern const short WebRtcIsac_kQKltQuantMinGain[12];

extern const short WebRtcIsac_kQKltQuantMinShape[108];

/* maximum quantization index */
extern const uint16_t WebRtcIsac_kQKltMaxIndGain[12];

extern const uint16_t WebRtcIsac_kQKltMaxIndShape[108];

/* index offset */
extern const uint16_t WebRtcIsac_kQKltOffsetGain[12];

extern const uint16_t WebRtcIsac_kQKltOffsetShape[108];

/* initial cdf index for KLT coefficients */
extern const uint16_t WebRtcIsac_kQKltInitIndexGain[12];

extern const uint16_t WebRtcIsac_kQKltInitIndexShape[108];

/* quantizer representation levels */
extern const double WebRtcIsac_kQKltLevelsGain[392];

extern const double WebRtcIsac_kQKltLevelsShape[578];

/* cdf tables for quantizer indices */
extern const uint16_t WebRtcIsac_kQKltCdfGain[404];

extern const uint16_t WebRtcIsac_kQKltCdfShape[686];

/* pointers to cdf tables for quantizer indices */
extern const uint16_t* WebRtcIsac_kQKltCdfPtrGain[12];

extern const uint16_t* WebRtcIsac_kQKltCdfPtrShape[108];

/* left KLT transforms */
extern const double WebRtcIsac_kKltT1Gain[4];

extern const double WebRtcIsac_kKltT1Shape[324];

/* right KLT transforms */
extern const double WebRtcIsac_kKltT2Gain[36];

extern const double WebRtcIsac_kKltT2Shape[36];

/* means of log gains and LAR coefficients */
extern const double WebRtcIsac_kLpcMeansGain[12];

extern const double WebRtcIsac_kLpcMeansShape[108];

#endif /* MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_LPC_TABLES_H_ */
