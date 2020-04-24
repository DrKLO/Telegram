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
 * encode_lpc_swb.h
 *
 * This file contains declaration of functions used to
 * encode LPC parameters (Shape & gain) of the upper band.
 *
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_ENCODE_LPC_SWB_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_ENCODE_LPC_SWB_H_

#include "modules/audio_coding/codecs/isac/main/source/settings.h"
#include "modules/audio_coding/codecs/isac/main/source/structs.h"

/******************************************************************************
 * WebRtcIsac_RemoveLarMean()
 *
 * Remove the means from LAR coefficients.
 *
 * Input:
 *      -lar                : pointer to lar vectors. LAR vectors are
 *                            concatenated.
 *      -bandwidth          : indicates if the given LAR vectors belong
 *                            to SWB-12kHz or SWB-16kHz.
 *
 * Output:
 *      -lar                : pointer to mean-removed LAR:s.
 *
 *
 */
int16_t WebRtcIsac_RemoveLarMean(double* lar, int16_t bandwidth);

/******************************************************************************
 * WebRtcIsac_DecorrelateIntraVec()
 *
 * Remove the correlation amonge the components of LAR vectors. If LAR vectors
 * of one frame are put in a matrix where each column is a LAR vector of a
 * sub-frame, then this is equivalent to multiplying the LAR matrix with
 * a decorrelting mtrix from left.
 *
 * Input:
 *      -inLar              : pointer to mean-removed LAR vecrtors.
 *      -bandwidth          : indicates if the given LAR vectors belong
 *                            to SWB-12kHz or SWB-16kHz.
 *
 * Output:
 *      -out                : decorrelated LAR vectors.
 */
int16_t WebRtcIsac_DecorrelateIntraVec(const double* inLAR,
                                       double* out,
                                       int16_t bandwidth);

/******************************************************************************
 * WebRtcIsac_DecorrelateInterVec()
 *
 * Remover the correlation among mean-removed LAR vectors. If LAR vectors
 * of one frame are put in a matrix where each column is a LAR vector of a
 * sub-frame, then this is equivalent to multiplying the LAR matrix with
 * a decorrelting mtrix from right.
 *
 * Input:
 *      -data               : pointer to matrix of LAR vectors. The matrix
 *                            is stored column-wise.
 *      -bandwidth          : indicates if the given LAR vectors belong
 *                            to SWB-12kHz or SWB-16kHz.
 *
 * Output:
 *      -out                : decorrelated LAR vectors.
 */
int16_t WebRtcIsac_DecorrelateInterVec(const double* data,
                                       double* out,
                                       int16_t bandwidth);

/******************************************************************************
 * WebRtcIsac_QuantizeUncorrLar()
 *
 * Quantize the uncorrelated parameters.
 *
 * Input:
 *      -data               : uncorrelated LAR vectors.
 *      -bandwidth          : indicates if the given LAR vectors belong
 *                            to SWB-12kHz or SWB-16kHz.
 *
 * Output:
 *      -data               : quantized version of the input.
 *      -idx                : pointer to quantization indices.
 */
double WebRtcIsac_QuantizeUncorrLar(double* data, int* idx, int16_t bandwidth);

/******************************************************************************
 * WebRtcIsac_CorrelateIntraVec()
 *
 * This is the inverse of WebRtcIsac_DecorrelateIntraVec().
 *
 * Input:
 *      -data               : uncorrelated parameters.
 *      -bandwidth          : indicates if the given LAR vectors belong
 *                            to SWB-12kHz or SWB-16kHz.
 *
 * Output:
 *      -out                : correlated parametrs.
 */
int16_t WebRtcIsac_CorrelateIntraVec(const double* data,
                                     double* out,
                                     int16_t bandwidth);

/******************************************************************************
 * WebRtcIsac_CorrelateInterVec()
 *
 * This is the inverse of WebRtcIsac_DecorrelateInterVec().
 *
 * Input:
 *      -data
 *      -bandwidth          : indicates if the given LAR vectors belong
 *                            to SWB-12kHz or SWB-16kHz.
 *
 * Output:
 *      -out                : correlated parametrs.
 */
int16_t WebRtcIsac_CorrelateInterVec(const double* data,
                                     double* out,
                                     int16_t bandwidth);

/******************************************************************************
 * WebRtcIsac_AddLarMean()
 *
 * This is the inverse of WebRtcIsac_RemoveLarMean()
 *
 * Input:
 *      -data               : pointer to mean-removed LAR:s.
 *      -bandwidth          : indicates if the given LAR vectors belong
 *                            to SWB-12kHz or SWB-16kHz.
 *
 * Output:
 *      -data               : pointer to LARs.
 */
int16_t WebRtcIsac_AddLarMean(double* data, int16_t bandwidth);

/******************************************************************************
 * WebRtcIsac_DequantizeLpcParam()
 *
 * Get the quantized value of uncorrelated LARs given the quantization indices.
 *
 * Input:
 *      -idx                : pointer to quantiztion indices.
 *      -bandwidth          : indicates if the given LAR vectors belong
 *                            to SWB-12kHz or SWB-16kHz.
 *
 * Output:
 *      -out                : pointer to quantized values.
 */
int16_t WebRtcIsac_DequantizeLpcParam(const int* idx,
                                      double* out,
                                      int16_t bandwidth);

/******************************************************************************
 * WebRtcIsac_ToLogDomainRemoveMean()
 *
 * Transform the LPC gain to log domain then remove the mean value.
 *
 * Input:
 *      -lpcGain            : pointer to LPC Gain, expecting 6 LPC gains
 *
 * Output:
 *      -lpcGain            : mean-removed in log domain.
 */
int16_t WebRtcIsac_ToLogDomainRemoveMean(double* lpGains);

/******************************************************************************
 * WebRtcIsac_DecorrelateLPGain()
 *
 * Decorrelate LPC gains. There are 6 LPC Gains per frame. This is like
 * multiplying gain vector with decorrelating matrix.
 *
 * Input:
 *      -data               : LPC gain in log-domain with mean removed.
 *
 * Output:
 *      -out                : decorrelated parameters.
 */
int16_t WebRtcIsac_DecorrelateLPGain(const double* data, double* out);

/******************************************************************************
 * WebRtcIsac_QuantizeLpcGain()
 *
 * Quantize the decorrelated log-domain gains.
 *
 * Input:
 *      -lpcGain            : uncorrelated LPC gains.
 *
 * Output:
 *      -idx                : quantization indices
 *      -lpcGain            : quantized value of the inpt.
 */
double WebRtcIsac_QuantizeLpcGain(double* lpGains, int* idx);

/******************************************************************************
 * WebRtcIsac_DequantizeLpcGain()
 *
 * Get the quantized values given the quantization indices.
 *
 * Input:
 *      -idx                : pointer to quantization indices.
 *
 * Output:
 *      -lpcGains           : quantized values of the given parametes.
 */
int16_t WebRtcIsac_DequantizeLpcGain(const int* idx, double* lpGains);

/******************************************************************************
 * WebRtcIsac_CorrelateLpcGain()
 *
 * This is the inverse of WebRtcIsac_DecorrelateLPGain().
 *
 * Input:
 *      -data               : decorrelated parameters.
 *
 * Output:
 *      -out                : correlated parameters.
 */
int16_t WebRtcIsac_CorrelateLpcGain(const double* data, double* out);

/******************************************************************************
 * WebRtcIsac_AddMeanToLinearDomain()
 *
 * This is the inverse of WebRtcIsac_ToLogDomainRemoveMean().
 *
 * Input:
 *      -lpcGain            : LPC gain in log-domain & mean removed
 *
 * Output:
 *      -lpcGain            : LPC gain in normal domain.
 */
int16_t WebRtcIsac_AddMeanToLinearDomain(double* lpcGains);

#endif  // MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_ENCODE_LPC_SWB_H_
