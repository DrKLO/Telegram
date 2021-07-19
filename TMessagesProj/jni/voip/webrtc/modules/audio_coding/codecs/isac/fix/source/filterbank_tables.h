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
 * filterbank_tables.h
 *
 * Header file for variables that are defined in
 * filterbank_tables.c.
 *
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_FILTERBANK_TABLES_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_FILTERBANK_TABLES_H_

#include <stdint.h>

#if defined(__cplusplus) || defined(c_plusplus)
extern "C" {
#endif

/********************* Coefficient Tables ************************/

/* HPstcoeff_in_Q14 = {a1, a2, b1 - b0 * a1, b2 - b0 * a2}; */
/* [Q30lo Q30hi Q30lo Q30hi Q35lo Q35hi Q35lo Q35hi] */
extern const int16_t WebRtcIsacfix_kHpStCoeffInQ30[8];

/* HPstcoeff_out_1_Q14 = {a1, a2, b1 - b0 * a1, b2 - b0 * a2}; */
/* [Q30lo Q30hi Q30lo Q30hi Q35lo Q35hi Q35lo Q35hi] */
extern const int16_t WebRtcIsacfix_kHPStCoeffOut1Q30[8];

/* HPstcoeff_out_2_Q14 = {a1, a2, b1 - b0 * a1, b2 - b0 * a2}; */
/* [Q30lo Q30hi Q30lo Q30hi Q35lo Q35hi Q35lo Q35hi] */
extern const int16_t WebRtcIsacfix_kHPStCoeffOut2Q30[8];

/* The upper channel all-pass filter factors */
extern const int16_t WebRtcIsacfix_kUpperApFactorsQ15[2];

/* The lower channel all-pass filter factors */
extern const int16_t WebRtcIsacfix_kLowerApFactorsQ15[2];

#if defined(__cplusplus) || defined(c_plusplus)
}
#endif

#endif /* MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_FILTERBANK_TABLES_H_ */
