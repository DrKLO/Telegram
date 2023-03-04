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
 * arith_routines.h
 *
 * Functions for arithmetic coding.
 *
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_ARITH_ROUTINES_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_ARITH_ROUTINES_H_

#include "modules/audio_coding/codecs/isac/main/source/structs.h"

int WebRtcIsac_EncLogisticMulti2(
    Bitstr* streamdata, /* in-/output struct containing bitstream */
    int16_t* dataQ7,    /* input: data vector */
    const uint16_t*
        env, /* input: side info vector defining the width of the pdf */
    int N,   /* input: data vector length */
    int16_t isSWB12kHz); /* if the codec is working in 12kHz bandwidth */

/* returns the number of bytes in the stream */
int WebRtcIsac_EncTerminate(
    Bitstr* streamdata); /* in-/output struct containing bitstream */

/* returns the number of bytes in the stream so far */
int WebRtcIsac_DecLogisticMulti2(
    int16_t* data,      /* output: data vector */
    Bitstr* streamdata, /* in-/output struct containing bitstream */
    const uint16_t*
        env, /* input: side info vector defining the width of the pdf */
    const int16_t* dither, /* input: dither vector */
    int N,                 /* input: data vector length */
    int16_t isSWB12kHz);   /* if the codec is working in 12kHz bandwidth */

void WebRtcIsac_EncHistMulti(
    Bitstr* streamdata,         /* in-/output struct containing bitstream */
    const int* data,            /* input: data vector */
    const uint16_t* const* cdf, /* input: array of cdf arrays */
    int N);                     /* input: data vector length */

int WebRtcIsac_DecHistBisectMulti(
    int* data,                  /* output: data vector */
    Bitstr* streamdata,         /* in-/output struct containing bitstream */
    const uint16_t* const* cdf, /* input: array of cdf arrays */
    const uint16_t*
        cdf_size, /* input: array of cdf table sizes+1 (power of two: 2^k) */
    int N);       /* input: data vector length */

int WebRtcIsac_DecHistOneStepMulti(
    int* data,                  /* output: data vector */
    Bitstr* streamdata,         /* in-/output struct containing bitstream */
    const uint16_t* const* cdf, /* input: array of cdf arrays */
    const uint16_t*
        init_index, /* input: vector of initial cdf table search entries */
    int N);         /* input: data vector length */

#endif /* MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_ARITH_ROUTINES_H_ */
