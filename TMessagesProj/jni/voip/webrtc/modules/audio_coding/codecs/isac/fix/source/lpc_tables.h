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
 * lpc_tables.h
 *
 * header file for coding tables for the LPC coefficients
 *
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_LPC_TABLES_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_LPC_TABLES_H_

#include <stdint.h>

#include "modules/audio_coding/codecs/isac/fix/source/settings.h"

/* indices of KLT coefficients used */
extern const uint16_t WebRtcIsacfix_kSelIndGain[12];

extern const uint16_t WebRtcIsacfix_kSelIndShape[108];

/* cdf array for model indicator */
extern const uint16_t WebRtcIsacfix_kModelCdf[KLT_NUM_MODELS + 1];

/* pointer to cdf array for model indicator */
extern const uint16_t* WebRtcIsacfix_kModelCdfPtr[1];

/* initial cdf index for decoder of model indicator */
extern const uint16_t WebRtcIsacfix_kModelInitIndex[1];

/* offset to go from rounded value to quantization index */
extern const int16_t WebRtcIsacfix_kQuantMinGain[12];

extern const int16_t WebRtcIsacfix_kQuantMinShape[108];

/* maximum quantization index */
extern const uint16_t WebRtcIsacfix_kMaxIndGain[12];

extern const uint16_t WebRtcIsacfix_kMaxIndShape[108];

/* index offset */
extern const uint16_t WebRtcIsacfix_kOffsetGain[KLT_NUM_MODELS][12];

extern const uint16_t WebRtcIsacfix_kOffsetShape[KLT_NUM_MODELS][108];

/* initial cdf index for KLT coefficients */
extern const uint16_t WebRtcIsacfix_kInitIndexGain[KLT_NUM_MODELS][12];

extern const uint16_t WebRtcIsacfix_kInitIndexShape[KLT_NUM_MODELS][108];

/* offsets for quantizer representation levels */
extern const uint16_t WebRtcIsacfix_kOfLevelsGain[3];

extern const uint16_t WebRtcIsacfix_kOfLevelsShape[3];

/* quantizer representation levels */
extern const int32_t WebRtcIsacfix_kLevelsGainQ17[1176];

extern const int16_t WebRtcIsacfix_kLevelsShapeQ10[1735];

/* cdf tables for quantizer indices */
extern const uint16_t WebRtcIsacfix_kCdfGain[1212];

extern const uint16_t WebRtcIsacfix_kCdfShape[2059];

/* pointers to cdf tables for quantizer indices */
extern const uint16_t* WebRtcIsacfix_kCdfGainPtr[KLT_NUM_MODELS][12];

extern const uint16_t* WebRtcIsacfix_kCdfShapePtr[KLT_NUM_MODELS][108];

/* code length for all coefficients using different models */
extern const int16_t WebRtcIsacfix_kCodeLenGainQ11[392];

extern const int16_t WebRtcIsacfix_kCodeLenShapeQ11[578];

/* left KLT transforms */
extern const int16_t WebRtcIsacfix_kT1GainQ15[KLT_NUM_MODELS][4];

extern const int16_t WebRtcIsacfix_kT1ShapeQ15[KLT_NUM_MODELS][324];

/* right KLT transforms */
extern const int16_t WebRtcIsacfix_kT2GainQ15[KLT_NUM_MODELS][36];

extern const int16_t WebRtcIsacfix_kT2ShapeQ15[KLT_NUM_MODELS][36];

/* means of log gains and LAR coefficients */
extern const int16_t WebRtcIsacfix_kMeansGainQ8[KLT_NUM_MODELS][12];

extern const int32_t WebRtcIsacfix_kMeansShapeQ17[3][108];

#endif /* MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_LPC_TABLES_H_ */
