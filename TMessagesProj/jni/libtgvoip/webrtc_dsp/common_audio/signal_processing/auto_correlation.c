/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_audio/signal_processing/include/signal_processing_library.h"

#include "rtc_base/checks.h"

size_t WebRtcSpl_AutoCorrelation(const int16_t* in_vector,
                                 size_t in_vector_length,
                                 size_t order,
                                 int32_t* result,
                                 int* scale) {
  int32_t sum = 0;
  size_t i = 0, j = 0;
  int16_t smax = 0;
  int scaling = 0;

  RTC_DCHECK_LE(order, in_vector_length);

  // Find the maximum absolute value of the samples.
  smax = WebRtcSpl_MaxAbsValueW16(in_vector, in_vector_length);

  // In order to avoid overflow when computing the sum we should scale the
  // samples so that (in_vector_length * smax * smax) will not overflow.
  if (smax == 0) {
    scaling = 0;
  } else {
    // Number of bits in the sum loop.
    int nbits = WebRtcSpl_GetSizeInBits((uint32_t)in_vector_length);
    // Number of bits to normalize smax.
    int t = WebRtcSpl_NormW32(WEBRTC_SPL_MUL(smax, smax));

    if (t > nbits) {
      scaling = 0;
    } else {
      scaling = nbits - t;
    }
  }

  // Perform the actual correlation calculation.
  for (i = 0; i < order + 1; i++) {
    sum = 0;
    /* Unroll the loop to improve performance. */
    for (j = 0; i + j + 3 < in_vector_length; j += 4) {
      sum += (in_vector[j + 0] * in_vector[i + j + 0]) >> scaling;
      sum += (in_vector[j + 1] * in_vector[i + j + 1]) >> scaling;
      sum += (in_vector[j + 2] * in_vector[i + j + 2]) >> scaling;
      sum += (in_vector[j + 3] * in_vector[i + j + 3]) >> scaling;
    }
    for (; j < in_vector_length - i; j++) {
      sum += (in_vector[j] * in_vector[i + j]) >> scaling;
    }
    *result++ = sum;
  }

  *scale = scaling;
  return order + 1;
}
