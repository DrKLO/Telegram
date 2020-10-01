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
 * arith_routins.h
 *
 * Functions for arithmetic coding.
 *
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_ARITH_ROUTINS_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_ARITH_ROUTINS_H_

#include "modules/audio_coding/codecs/isac/fix/source/structs.h"

/****************************************************************************
 * WebRtcIsacfix_EncLogisticMulti2(...)
 *
 * Arithmetic coding of spectrum.
 *
 * Input:
 *      - streamData        : in-/output struct containing bitstream
 *      - dataQ7            : data vector in Q7
 *      - envQ8             : side info vector defining the width of the pdf
 *                            in Q8
 *      - lenData           : data vector length
 *
 * Return value             :  0 if ok,
 *                             <0 otherwise.
 */
int WebRtcIsacfix_EncLogisticMulti2(Bitstr_enc* streamData,
                                    int16_t* dataQ7,
                                    const uint16_t* env,
                                    const int16_t lenData);

/****************************************************************************
 * WebRtcIsacfix_EncTerminate(...)
 *
 * Final call to the arithmetic coder for an encoder call. This function
 * terminates and return byte stream.
 *
 * Input:
 *      - streamData        : in-/output struct containing bitstream
 *
 * Return value             : number of bytes in the stream
 */
int16_t WebRtcIsacfix_EncTerminate(Bitstr_enc* streamData);

/****************************************************************************
 * WebRtcIsacfix_DecLogisticMulti2(...)
 *
 * Arithmetic decoding of spectrum.
 *
 * Input:
 *      - streamData        : in-/output struct containing bitstream
 *      - envQ8             : side info vector defining the width of the pdf
 *                            in Q8
 *      - lenData           : data vector length
 *
 * Input/Output:
 *      - dataQ7            : input: dither vector, output: data vector, in Q7
 *
 * Return value             : number of bytes in the stream so far
 *                            <0 if error detected
 */
int WebRtcIsacfix_DecLogisticMulti2(int16_t* data,
                                    Bitstr_dec* streamData,
                                    const int32_t* env,
                                    const int16_t lenData);

/****************************************************************************
 * WebRtcIsacfix_EncHistMulti(...)
 *
 * Encode the histogram interval
 *
 * Input:
 *      - streamData        : in-/output struct containing bitstream
 *      - data              : data vector
 *      - cdf               : array of cdf arrays
 *      - lenData           : data vector length
 *
 * Return value             : 0 if ok
 *                            <0 if error detected
 */
int WebRtcIsacfix_EncHistMulti(Bitstr_enc* streamData,
                               const int16_t* data,
                               const uint16_t* const* cdf,
                               const int16_t lenData);

/****************************************************************************
 * WebRtcIsacfix_DecHistBisectMulti(...)
 *
 * Function to decode more symbols from the arithmetic bytestream, using
 * method of bisection.
 * C df tables should be of size 2^k-1 (which corresponds to an
 * alphabet size of 2^k-2)
 *
 * Input:
 *      - streamData        : in-/output struct containing bitstream
 *      - cdf               : array of cdf arrays
 *      - cdfSize           : array of cdf table sizes+1 (power of two: 2^k)
 *      - lenData           : data vector length
 *
 * Output:
 *      - data              : data vector
 *
 * Return value             : number of bytes in the stream
 *                            <0 if error detected
 */
int16_t WebRtcIsacfix_DecHistBisectMulti(int16_t* data,
                                         Bitstr_dec* streamData,
                                         const uint16_t* const* cdf,
                                         const uint16_t* cdfSize,
                                         const int16_t lenData);

/****************************************************************************
 * WebRtcIsacfix_DecHistOneStepMulti(...)
 *
 * Function to decode more symbols from the arithmetic bytestream, taking
 * single step up or down at a time.
 * cdf tables can be of arbitrary size, but large tables may take a lot of
 * iterations.
 *
 * Input:
 *      - streamData        : in-/output struct containing bitstream
 *      - cdf               : array of cdf arrays
 *      - initIndex         : vector of initial cdf table search entries
 *      - lenData           : data vector length
 *
 * Output:
 *      - data              : data vector
 *
 * Return value             : number of bytes in original stream
 *                            <0 if error detected
 */
int16_t WebRtcIsacfix_DecHistOneStepMulti(int16_t* data,
                                          Bitstr_dec* streamData,
                                          const uint16_t* const* cdf,
                                          const uint16_t* initIndex,
                                          const int16_t lenData);

#endif /* MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_ARITH_ROUTINS_H_ */
