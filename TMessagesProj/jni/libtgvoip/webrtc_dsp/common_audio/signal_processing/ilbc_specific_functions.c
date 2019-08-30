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
 * This file contains implementations of the iLBC specific functions
 * WebRtcSpl_ReverseOrderMultArrayElements()
 * WebRtcSpl_ElementwiseVectorMult()
 * WebRtcSpl_AddVectorsAndShift()
 * WebRtcSpl_AddAffineVectorToVector()
 * WebRtcSpl_AffineTransformVector()
 *
 */

#include "common_audio/signal_processing/include/signal_processing_library.h"

void WebRtcSpl_ReverseOrderMultArrayElements(int16_t *out, const int16_t *in,
                                             const int16_t *win,
                                             size_t vector_length,
                                             int16_t right_shifts)
{
    size_t i;
    int16_t *outptr = out;
    const int16_t *inptr = in;
    const int16_t *winptr = win;
    for (i = 0; i < vector_length; i++)
    {
      *outptr++ = (int16_t)((*inptr++ * *winptr--) >> right_shifts);
    }
}

void WebRtcSpl_ElementwiseVectorMult(int16_t *out, const int16_t *in,
                                     const int16_t *win, size_t vector_length,
                                     int16_t right_shifts)
{
    size_t i;
    int16_t *outptr = out;
    const int16_t *inptr = in;
    const int16_t *winptr = win;
    for (i = 0; i < vector_length; i++)
    {
      *outptr++ = (int16_t)((*inptr++ * *winptr++) >> right_shifts);
    }
}

void WebRtcSpl_AddVectorsAndShift(int16_t *out, const int16_t *in1,
                                  const int16_t *in2, size_t vector_length,
                                  int16_t right_shifts)
{
    size_t i;
    int16_t *outptr = out;
    const int16_t *in1ptr = in1;
    const int16_t *in2ptr = in2;
    for (i = vector_length; i > 0; i--)
    {
        (*outptr++) = (int16_t)(((*in1ptr++) + (*in2ptr++)) >> right_shifts);
    }
}

void WebRtcSpl_AddAffineVectorToVector(int16_t *out, int16_t *in,
                                       int16_t gain, int32_t add_constant,
                                       int16_t right_shifts,
                                       size_t vector_length)
{
    size_t i;

    for (i = 0; i < vector_length; i++)
    {
      out[i] += (int16_t)((in[i] * gain + add_constant) >> right_shifts);
    }
}

void WebRtcSpl_AffineTransformVector(int16_t *out, int16_t *in,
                                     int16_t gain, int32_t add_constant,
                                     int16_t right_shifts, size_t vector_length)
{
    size_t i;

    for (i = 0; i < vector_length; i++)
    {
      out[i] = (int16_t)((in[i] * gain + add_constant) >> right_shifts);
    }
}
