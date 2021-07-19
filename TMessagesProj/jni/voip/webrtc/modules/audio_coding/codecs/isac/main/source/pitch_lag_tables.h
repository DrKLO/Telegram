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
 * pitch_lag_tables.h
 *
 * This file contains tables for the pitch filter side-info in the entropy
 * coder.
 *
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_PITCH_LAG_TABLES_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_PITCH_LAG_TABLES_H_

#include <stdint.h>

/* header file for coding tables for the pitch filter side-info in the entropy
 * coder */
/********************* Pitch Filter Lag Coefficient Tables
 * ************************/

/* tables for use with small pitch gain */

/* cdfs for quantized pitch lags */
extern const uint16_t WebRtcIsac_kQPitchLagCdf1Lo[127];
extern const uint16_t WebRtcIsac_kQPitchLagCdf2Lo[20];
extern const uint16_t WebRtcIsac_kQPitchLagCdf3Lo[2];
extern const uint16_t WebRtcIsac_kQPitchLagCdf4Lo[10];

extern const uint16_t* WebRtcIsac_kQPitchLagCdfPtrLo[4];

/* size of first cdf table */
extern const uint16_t WebRtcIsac_kQPitchLagCdfSizeLo[1];

/* index limits and ranges */
extern const int16_t WebRtcIsac_kQIndexLowerLimitLagLo[4];
extern const int16_t WebRtcIsac_kQIndexUpperLimitLagLo[4];

/* initial index for arithmetic decoder */
extern const uint16_t WebRtcIsac_kQInitIndexLagLo[3];

/* mean values of pitch filter lags */
extern const double WebRtcIsac_kQMeanLag2Lo[19];
extern const double WebRtcIsac_kQMeanLag3Lo[1];
extern const double WebRtcIsac_kQMeanLag4Lo[9];

extern const double WebRtcIsac_kQPitchLagStepsizeLo;

/* tables for use with medium pitch gain */

/* cdfs for quantized pitch lags */
extern const uint16_t WebRtcIsac_kQPitchLagCdf1Mid[255];
extern const uint16_t WebRtcIsac_kQPitchLagCdf2Mid[36];
extern const uint16_t WebRtcIsac_kQPitchLagCdf3Mid[2];
extern const uint16_t WebRtcIsac_kQPitchLagCdf4Mid[20];

extern const uint16_t* WebRtcIsac_kQPitchLagCdfPtrMid[4];

/* size of first cdf table */
extern const uint16_t WebRtcIsac_kQPitchLagCdfSizeMid[1];

/* index limits and ranges */
extern const int16_t WebRtcIsac_kQIndexLowerLimitLagMid[4];
extern const int16_t WebRtcIsac_kQIndexUpperLimitLagMid[4];

/* initial index for arithmetic decoder */
extern const uint16_t WebRtcIsac_kQInitIndexLagMid[3];

/* mean values of pitch filter lags */
extern const double WebRtcIsac_kQMeanLag2Mid[35];
extern const double WebRtcIsac_kQMeanLag3Mid[1];
extern const double WebRtcIsac_kQMeanLag4Mid[19];

extern const double WebRtcIsac_kQPitchLagStepsizeMid;

/* tables for use with large pitch gain */

/* cdfs for quantized pitch lags */
extern const uint16_t WebRtcIsac_kQPitchLagCdf1Hi[511];
extern const uint16_t WebRtcIsac_kQPitchLagCdf2Hi[68];
extern const uint16_t WebRtcIsac_kQPitchLagCdf3Hi[2];
extern const uint16_t WebRtcIsac_kQPitchLagCdf4Hi[35];

extern const uint16_t* WebRtcIsac_kQPitchLagCdfPtrHi[4];

/* size of first cdf table */
extern const uint16_t WebRtcIsac_kQPitchLagCdfSizeHi[1];

/* index limits and ranges */
extern const int16_t WebRtcIsac_kQindexLowerLimitLagHi[4];
extern const int16_t WebRtcIsac_kQindexUpperLimitLagHi[4];

/* initial index for arithmetic decoder */
extern const uint16_t WebRtcIsac_kQInitIndexLagHi[3];

/* mean values of pitch filter lags */
extern const double WebRtcIsac_kQMeanLag2Hi[67];
extern const double WebRtcIsac_kQMeanLag3Hi[1];
extern const double WebRtcIsac_kQMeanLag4Hi[34];

extern const double WebRtcIsac_kQPitchLagStepsizeHi;

/* transform matrix */
extern const double WebRtcIsac_kTransform[4][4];

/* transpose transform matrix */
extern const double WebRtcIsac_kTransformTranspose[4][4];

#endif /* MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_PITCH_LAG_TABLES_H_ */
