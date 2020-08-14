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
 * pitch_gain_tables.h
 *
 * This file contains tables for the pitch filter side-info in the entropy
 * coder.
 *
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_PITCH_GAIN_TABLES_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_PITCH_GAIN_TABLES_H_

#include <stdint.h>

/********************* Pitch Filter Gain Coefficient Tables
 * ************************/
/* cdf for quantized pitch filter gains */
extern const uint16_t WebRtcIsacfix_kPitchGainCdf[255];

/* index limits and ranges */
extern const int16_t WebRtcIsacfix_kLowerlimiGain[3];
extern const int16_t WebRtcIsacfix_kUpperlimitGain[3];
extern const uint16_t WebRtcIsacfix_kMultsGain[2];

/* mean values of pitch filter gains in Q12*/
extern const int16_t WebRtcIsacfix_kPitchGain1[144];
extern const int16_t WebRtcIsacfix_kPitchGain2[144];
extern const int16_t WebRtcIsacfix_kPitchGain3[144];
extern const int16_t WebRtcIsacfix_kPitchGain4[144];

/* size of cdf table */
extern const uint16_t WebRtcIsacfix_kCdfTableSizeGain[1];

/* transform matrix */
extern const int16_t WebRtcIsacfix_kTransform[4][4];

#endif /* MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_PITCH_GAIN_TABLES_H_ */
