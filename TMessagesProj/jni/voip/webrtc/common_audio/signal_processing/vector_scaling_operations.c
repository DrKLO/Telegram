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
 * This file contains implementations of the functions
 * WebRtcSpl_VectorBitShiftW16()
 * WebRtcSpl_VectorBitShiftW32()
 * WebRtcSpl_VectorBitShiftW32ToW16()
 * WebRtcSpl_ScaleVector()
 * WebRtcSpl_ScaleVectorWithSat()
 * WebRtcSpl_ScaleAndAddVectors()
 * WebRtcSpl_ScaleAndAddVectorsWithRoundC()
 */

#include "common_audio/signal_processing/include/signal_processing_library.h"

void WebRtcSpl_VectorBitShiftW16(int16_t *res, size_t length,
                                 const int16_t *in, int16_t right_shifts)
{
    size_t i;

    if (right_shifts > 0)
    {
        for (i = length; i > 0; i--)
        {
            (*res++) = ((*in++) >> right_shifts);
        }
    } else
    {
        for (i = length; i > 0; i--)
        {
            (*res++) = ((*in++) * (1 << (-right_shifts)));
        }
    }
}

void WebRtcSpl_VectorBitShiftW32(int32_t *out_vector,
                                 size_t vector_length,
                                 const int32_t *in_vector,
                                 int16_t right_shifts)
{
    size_t i;

    if (right_shifts > 0)
    {
        for (i = vector_length; i > 0; i--)
        {
            (*out_vector++) = ((*in_vector++) >> right_shifts);
        }
    } else
    {
        for (i = vector_length; i > 0; i--)
        {
            (*out_vector++) = ((*in_vector++) << (-right_shifts));
        }
    }
}

void WebRtcSpl_VectorBitShiftW32ToW16(int16_t* out, size_t length,
                                      const int32_t* in, int right_shifts) {
  size_t i;
  int32_t tmp_w32;

  if (right_shifts >= 0) {
    for (i = length; i > 0; i--) {
      tmp_w32 = (*in++) >> right_shifts;
      (*out++) = WebRtcSpl_SatW32ToW16(tmp_w32);
    }
  } else {
    int left_shifts = -right_shifts;
    for (i = length; i > 0; i--) {
      tmp_w32 = (*in++) << left_shifts;
      (*out++) = WebRtcSpl_SatW32ToW16(tmp_w32);
    }
  }
}

void WebRtcSpl_ScaleVector(const int16_t *in_vector, int16_t *out_vector,
                           int16_t gain, size_t in_vector_length,
                           int16_t right_shifts)
{
    // Performs vector operation: out_vector = (gain*in_vector)>>right_shifts
    size_t i;
    const int16_t *inptr;
    int16_t *outptr;

    inptr = in_vector;
    outptr = out_vector;

    for (i = 0; i < in_vector_length; i++)
    {
      *outptr++ = (int16_t)((*inptr++ * gain) >> right_shifts);
    }
}

void WebRtcSpl_ScaleVectorWithSat(const int16_t *in_vector, int16_t *out_vector,
                                 int16_t gain, size_t in_vector_length,
                                 int16_t right_shifts)
{
    // Performs vector operation: out_vector = (gain*in_vector)>>right_shifts
    size_t i;
    const int16_t *inptr;
    int16_t *outptr;

    inptr = in_vector;
    outptr = out_vector;

    for (i = 0; i < in_vector_length; i++) {
      *outptr++ = WebRtcSpl_SatW32ToW16((*inptr++ * gain) >> right_shifts);
    }
}

void WebRtcSpl_ScaleAndAddVectors(const int16_t *in1, int16_t gain1, int shift1,
                                  const int16_t *in2, int16_t gain2, int shift2,
                                  int16_t *out, size_t vector_length)
{
    // Performs vector operation: out = (gain1*in1)>>shift1 + (gain2*in2)>>shift2
    size_t i;
    const int16_t *in1ptr;
    const int16_t *in2ptr;
    int16_t *outptr;

    in1ptr = in1;
    in2ptr = in2;
    outptr = out;

    for (i = 0; i < vector_length; i++)
    {
      *outptr++ = (int16_t)((gain1 * *in1ptr++) >> shift1) +
          (int16_t)((gain2 * *in2ptr++) >> shift2);
    }
}

// C version of WebRtcSpl_ScaleAndAddVectorsWithRound() for generic platforms.
int WebRtcSpl_ScaleAndAddVectorsWithRoundC(const int16_t* in_vector1,
                                           int16_t in_vector1_scale,
                                           const int16_t* in_vector2,
                                           int16_t in_vector2_scale,
                                           int right_shifts,
                                           int16_t* out_vector,
                                           size_t length) {
  size_t i = 0;
  int round_value = (1 << right_shifts) >> 1;

  if (in_vector1 == NULL || in_vector2 == NULL || out_vector == NULL ||
      length == 0 || right_shifts < 0) {
    return -1;
  }

  for (i = 0; i < length; i++) {
    out_vector[i] = (int16_t)((
        in_vector1[i] * in_vector1_scale + in_vector2[i] * in_vector2_scale +
        round_value) >> right_shifts);
  }

  return 0;
}
