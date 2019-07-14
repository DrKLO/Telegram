/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_audio/signal_processing/include/real_fft.h"

#include <stdlib.h>

#include "common_audio/signal_processing/include/signal_processing_library.h"

struct RealFFT {
  int order;
};

struct RealFFT* WebRtcSpl_CreateRealFFT(int order) {
  struct RealFFT* self = NULL;

  if (order > kMaxFFTOrder || order < 0) {
    return NULL;
  }

  self = malloc(sizeof(struct RealFFT));
  if (self == NULL) {
    return NULL;
  }
  self->order = order;

  return self;
}

void WebRtcSpl_FreeRealFFT(struct RealFFT* self) {
  if (self != NULL) {
    free(self);
  }
}

// The C version FFT functions (i.e. WebRtcSpl_RealForwardFFT and
// WebRtcSpl_RealInverseFFT) are real-valued FFT wrappers for complex-valued
// FFT implementation in SPL.

int WebRtcSpl_RealForwardFFT(struct RealFFT* self,
                             const int16_t* real_data_in,
                             int16_t* complex_data_out) {
  int i = 0;
  int j = 0;
  int result = 0;
  int n = 1 << self->order;
  // The complex-value FFT implementation needs a buffer to hold 2^order
  // 16-bit COMPLEX numbers, for both time and frequency data.
  int16_t complex_buffer[2 << kMaxFFTOrder];

  // Insert zeros to the imaginary parts for complex forward FFT input.
  for (i = 0, j = 0; i < n; i += 1, j += 2) {
    complex_buffer[j] = real_data_in[i];
    complex_buffer[j + 1] = 0;
  };

  WebRtcSpl_ComplexBitReverse(complex_buffer, self->order);
  result = WebRtcSpl_ComplexFFT(complex_buffer, self->order, 1);

  // For real FFT output, use only the first N + 2 elements from
  // complex forward FFT.
  memcpy(complex_data_out, complex_buffer, sizeof(int16_t) * (n + 2));

  return result;
}

int WebRtcSpl_RealInverseFFT(struct RealFFT* self,
                             const int16_t* complex_data_in,
                             int16_t* real_data_out) {
  int i = 0;
  int j = 0;
  int result = 0;
  int n = 1 << self->order;
  // Create the buffer specific to complex-valued FFT implementation.
  int16_t complex_buffer[2 << kMaxFFTOrder];

  // For n-point FFT, first copy the first n + 2 elements into complex
  // FFT, then construct the remaining n - 2 elements by real FFT's
  // conjugate-symmetric properties.
  memcpy(complex_buffer, complex_data_in, sizeof(int16_t) * (n + 2));
  for (i = n + 2; i < 2 * n; i += 2) {
    complex_buffer[i] = complex_data_in[2 * n - i];
    complex_buffer[i + 1] = -complex_data_in[2 * n - i + 1];
  }

  WebRtcSpl_ComplexBitReverse(complex_buffer, self->order);
  result = WebRtcSpl_ComplexIFFT(complex_buffer, self->order, 1);

  // Strip out the imaginary parts of the complex inverse FFT output.
  for (i = 0, j = 0; i < n; i += 1, j += 2) {
    real_data_out[i] = complex_buffer[j];
  }

  return result;
}
