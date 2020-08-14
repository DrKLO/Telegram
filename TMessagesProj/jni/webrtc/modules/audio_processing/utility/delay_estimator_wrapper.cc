/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/utility/delay_estimator_wrapper.h"

#include <stdlib.h>
#include <string.h>

#include "modules/audio_processing/utility/delay_estimator.h"
#include "modules/audio_processing/utility/delay_estimator_internal.h"
#include "rtc_base/checks.h"

namespace webrtc {

// Only bit |kBandFirst| through bit |kBandLast| are processed and
// |kBandFirst| - |kBandLast| must be < 32.
enum { kBandFirst = 12 };
enum { kBandLast = 43 };

static __inline uint32_t SetBit(uint32_t in, int pos) {
  uint32_t mask = (1 << pos);
  uint32_t out = (in | mask);

  return out;
}

// Calculates the mean recursively. Same version as WebRtc_MeanEstimatorFix(),
// but for float.
//
// Inputs:
//    - new_value             : New additional value.
//    - scale                 : Scale for smoothing (should be less than 1.0).
//
// Input/Output:
//    - mean_value            : Pointer to the mean value for updating.
//
static void MeanEstimatorFloat(float new_value,
                               float scale,
                               float* mean_value) {
  RTC_DCHECK_LT(scale, 1.0f);
  *mean_value += (new_value - *mean_value) * scale;
}

// Computes the binary spectrum by comparing the input |spectrum| with a
// |threshold_spectrum|. Float and fixed point versions.
//
// Inputs:
//      - spectrum            : Spectrum of which the binary spectrum should be
//                              calculated.
//      - threshold_spectrum  : Threshold spectrum with which the input
//                              spectrum is compared.
// Return:
//      - out                 : Binary spectrum.
//
static uint32_t BinarySpectrumFix(const uint16_t* spectrum,
                                  SpectrumType* threshold_spectrum,
                                  int q_domain,
                                  int* threshold_initialized) {
  int i = kBandFirst;
  uint32_t out = 0;

  RTC_DCHECK_LT(q_domain, 16);

  if (!(*threshold_initialized)) {
    // Set the |threshold_spectrum| to half the input |spectrum| as starting
    // value. This speeds up the convergence.
    for (i = kBandFirst; i <= kBandLast; i++) {
      if (spectrum[i] > 0) {
        // Convert input spectrum from Q(|q_domain|) to Q15.
        int32_t spectrum_q15 = ((int32_t)spectrum[i]) << (15 - q_domain);
        threshold_spectrum[i].int32_ = (spectrum_q15 >> 1);
        *threshold_initialized = 1;
      }
    }
  }
  for (i = kBandFirst; i <= kBandLast; i++) {
    // Convert input spectrum from Q(|q_domain|) to Q15.
    int32_t spectrum_q15 = ((int32_t)spectrum[i]) << (15 - q_domain);
    // Update the |threshold_spectrum|.
    WebRtc_MeanEstimatorFix(spectrum_q15, 6, &(threshold_spectrum[i].int32_));
    // Convert |spectrum| at current frequency bin to a binary value.
    if (spectrum_q15 > threshold_spectrum[i].int32_) {
      out = SetBit(out, i - kBandFirst);
    }
  }

  return out;
}

static uint32_t BinarySpectrumFloat(const float* spectrum,
                                    SpectrumType* threshold_spectrum,
                                    int* threshold_initialized) {
  int i = kBandFirst;
  uint32_t out = 0;
  const float kScale = 1 / 64.0;

  if (!(*threshold_initialized)) {
    // Set the |threshold_spectrum| to half the input |spectrum| as starting
    // value. This speeds up the convergence.
    for (i = kBandFirst; i <= kBandLast; i++) {
      if (spectrum[i] > 0.0f) {
        threshold_spectrum[i].float_ = (spectrum[i] / 2);
        *threshold_initialized = 1;
      }
    }
  }

  for (i = kBandFirst; i <= kBandLast; i++) {
    // Update the |threshold_spectrum|.
    MeanEstimatorFloat(spectrum[i], kScale, &(threshold_spectrum[i].float_));
    // Convert |spectrum| at current frequency bin to a binary value.
    if (spectrum[i] > threshold_spectrum[i].float_) {
      out = SetBit(out, i - kBandFirst);
    }
  }

  return out;
}

void WebRtc_FreeDelayEstimatorFarend(void* handle) {
  DelayEstimatorFarend* self = (DelayEstimatorFarend*)handle;

  if (handle == NULL) {
    return;
  }

  free(self->mean_far_spectrum);
  self->mean_far_spectrum = NULL;

  WebRtc_FreeBinaryDelayEstimatorFarend(self->binary_farend);
  self->binary_farend = NULL;

  free(self);
}

void* WebRtc_CreateDelayEstimatorFarend(int spectrum_size, int history_size) {
  DelayEstimatorFarend* self = NULL;

  // Check if the sub band used in the delay estimation is small enough to fit
  // the binary spectra in a uint32_t.
  static_assert(kBandLast - kBandFirst < 32, "");

  if (spectrum_size >= kBandLast) {
    self = static_cast<DelayEstimatorFarend*>(
        malloc(sizeof(DelayEstimatorFarend)));
  }

  if (self != NULL) {
    int memory_fail = 0;

    // Allocate memory for the binary far-end spectrum handling.
    self->binary_farend = WebRtc_CreateBinaryDelayEstimatorFarend(history_size);
    memory_fail |= (self->binary_farend == NULL);

    // Allocate memory for spectrum buffers.
    self->mean_far_spectrum = static_cast<SpectrumType*>(
        malloc(spectrum_size * sizeof(SpectrumType)));
    memory_fail |= (self->mean_far_spectrum == NULL);

    self->spectrum_size = spectrum_size;

    if (memory_fail) {
      WebRtc_FreeDelayEstimatorFarend(self);
      self = NULL;
    }
  }

  return self;
}

int WebRtc_InitDelayEstimatorFarend(void* handle) {
  DelayEstimatorFarend* self = (DelayEstimatorFarend*)handle;

  if (self == NULL) {
    return -1;
  }

  // Initialize far-end part of binary delay estimator.
  WebRtc_InitBinaryDelayEstimatorFarend(self->binary_farend);

  // Set averaged far and near end spectra to zero.
  memset(self->mean_far_spectrum, 0,
         sizeof(SpectrumType) * self->spectrum_size);
  // Reset initialization indicators.
  self->far_spectrum_initialized = 0;

  return 0;
}

void WebRtc_SoftResetDelayEstimatorFarend(void* handle, int delay_shift) {
  DelayEstimatorFarend* self = (DelayEstimatorFarend*)handle;
  RTC_DCHECK(self);
  WebRtc_SoftResetBinaryDelayEstimatorFarend(self->binary_farend, delay_shift);
}

int WebRtc_AddFarSpectrumFix(void* handle,
                             const uint16_t* far_spectrum,
                             int spectrum_size,
                             int far_q) {
  DelayEstimatorFarend* self = (DelayEstimatorFarend*)handle;
  uint32_t binary_spectrum = 0;

  if (self == NULL) {
    return -1;
  }
  if (far_spectrum == NULL) {
    // Empty far end spectrum.
    return -1;
  }
  if (spectrum_size != self->spectrum_size) {
    // Data sizes don't match.
    return -1;
  }
  if (far_q > 15) {
    // If |far_q| is larger than 15 we cannot guarantee no wrap around.
    return -1;
  }

  // Get binary spectrum.
  binary_spectrum = BinarySpectrumFix(far_spectrum, self->mean_far_spectrum,
                                      far_q, &(self->far_spectrum_initialized));
  WebRtc_AddBinaryFarSpectrum(self->binary_farend, binary_spectrum);

  return 0;
}

int WebRtc_AddFarSpectrumFloat(void* handle,
                               const float* far_spectrum,
                               int spectrum_size) {
  DelayEstimatorFarend* self = (DelayEstimatorFarend*)handle;
  uint32_t binary_spectrum = 0;

  if (self == NULL) {
    return -1;
  }
  if (far_spectrum == NULL) {
    // Empty far end spectrum.
    return -1;
  }
  if (spectrum_size != self->spectrum_size) {
    // Data sizes don't match.
    return -1;
  }

  // Get binary spectrum.
  binary_spectrum = BinarySpectrumFloat(far_spectrum, self->mean_far_spectrum,
                                        &(self->far_spectrum_initialized));
  WebRtc_AddBinaryFarSpectrum(self->binary_farend, binary_spectrum);

  return 0;
}

void WebRtc_FreeDelayEstimator(void* handle) {
  DelayEstimator* self = (DelayEstimator*)handle;

  if (handle == NULL) {
    return;
  }

  free(self->mean_near_spectrum);
  self->mean_near_spectrum = NULL;

  WebRtc_FreeBinaryDelayEstimator(self->binary_handle);
  self->binary_handle = NULL;

  free(self);
}

void* WebRtc_CreateDelayEstimator(void* farend_handle, int max_lookahead) {
  DelayEstimator* self = NULL;
  DelayEstimatorFarend* farend = (DelayEstimatorFarend*)farend_handle;

  if (farend_handle != NULL) {
    self = static_cast<DelayEstimator*>(malloc(sizeof(DelayEstimator)));
  }

  if (self != NULL) {
    int memory_fail = 0;

    // Allocate memory for the farend spectrum handling.
    self->binary_handle =
        WebRtc_CreateBinaryDelayEstimator(farend->binary_farend, max_lookahead);
    memory_fail |= (self->binary_handle == NULL);

    // Allocate memory for spectrum buffers.
    self->mean_near_spectrum = static_cast<SpectrumType*>(
        malloc(farend->spectrum_size * sizeof(SpectrumType)));
    memory_fail |= (self->mean_near_spectrum == NULL);

    self->spectrum_size = farend->spectrum_size;

    if (memory_fail) {
      WebRtc_FreeDelayEstimator(self);
      self = NULL;
    }
  }

  return self;
}

int WebRtc_InitDelayEstimator(void* handle) {
  DelayEstimator* self = (DelayEstimator*)handle;

  if (self == NULL) {
    return -1;
  }

  // Initialize binary delay estimator.
  WebRtc_InitBinaryDelayEstimator(self->binary_handle);

  // Set averaged far and near end spectra to zero.
  memset(self->mean_near_spectrum, 0,
         sizeof(SpectrumType) * self->spectrum_size);
  // Reset initialization indicators.
  self->near_spectrum_initialized = 0;

  return 0;
}

int WebRtc_SoftResetDelayEstimator(void* handle, int delay_shift) {
  DelayEstimator* self = (DelayEstimator*)handle;
  RTC_DCHECK(self);
  return WebRtc_SoftResetBinaryDelayEstimator(self->binary_handle, delay_shift);
}

int WebRtc_set_history_size(void* handle, int history_size) {
  DelayEstimator* self = static_cast<DelayEstimator*>(handle);

  if ((self == NULL) || (history_size <= 1)) {
    return -1;
  }
  return WebRtc_AllocateHistoryBufferMemory(self->binary_handle, history_size);
}

int WebRtc_history_size(const void* handle) {
  const DelayEstimator* self = static_cast<const DelayEstimator*>(handle);

  if (self == NULL) {
    return -1;
  }
  if (self->binary_handle->farend->history_size !=
      self->binary_handle->history_size) {
    // Non matching history sizes.
    return -1;
  }
  return self->binary_handle->history_size;
}

int WebRtc_set_lookahead(void* handle, int lookahead) {
  DelayEstimator* self = (DelayEstimator*)handle;
  RTC_DCHECK(self);
  RTC_DCHECK(self->binary_handle);
  if ((lookahead > self->binary_handle->near_history_size - 1) ||
      (lookahead < 0)) {
    return -1;
  }
  self->binary_handle->lookahead = lookahead;
  return self->binary_handle->lookahead;
}

int WebRtc_lookahead(void* handle) {
  DelayEstimator* self = (DelayEstimator*)handle;
  RTC_DCHECK(self);
  RTC_DCHECK(self->binary_handle);
  return self->binary_handle->lookahead;
}

int WebRtc_set_allowed_offset(void* handle, int allowed_offset) {
  DelayEstimator* self = (DelayEstimator*)handle;

  if ((self == NULL) || (allowed_offset < 0)) {
    return -1;
  }
  self->binary_handle->allowed_offset = allowed_offset;
  return 0;
}

int WebRtc_get_allowed_offset(const void* handle) {
  const DelayEstimator* self = (const DelayEstimator*)handle;

  if (self == NULL) {
    return -1;
  }
  return self->binary_handle->allowed_offset;
}

int WebRtc_enable_robust_validation(void* handle, int enable) {
  DelayEstimator* self = (DelayEstimator*)handle;

  if (self == NULL) {
    return -1;
  }
  if ((enable < 0) || (enable > 1)) {
    return -1;
  }
  RTC_DCHECK(self->binary_handle);
  self->binary_handle->robust_validation_enabled = enable;
  return 0;
}

int WebRtc_is_robust_validation_enabled(const void* handle) {
  const DelayEstimator* self = (const DelayEstimator*)handle;

  if (self == NULL) {
    return -1;
  }
  return self->binary_handle->robust_validation_enabled;
}

int WebRtc_DelayEstimatorProcessFix(void* handle,
                                    const uint16_t* near_spectrum,
                                    int spectrum_size,
                                    int near_q) {
  DelayEstimator* self = (DelayEstimator*)handle;
  uint32_t binary_spectrum = 0;

  if (self == NULL) {
    return -1;
  }
  if (near_spectrum == NULL) {
    // Empty near end spectrum.
    return -1;
  }
  if (spectrum_size != self->spectrum_size) {
    // Data sizes don't match.
    return -1;
  }
  if (near_q > 15) {
    // If |near_q| is larger than 15 we cannot guarantee no wrap around.
    return -1;
  }

  // Get binary spectra.
  binary_spectrum =
      BinarySpectrumFix(near_spectrum, self->mean_near_spectrum, near_q,
                        &(self->near_spectrum_initialized));

  return WebRtc_ProcessBinarySpectrum(self->binary_handle, binary_spectrum);
}

int WebRtc_DelayEstimatorProcessFloat(void* handle,
                                      const float* near_spectrum,
                                      int spectrum_size) {
  DelayEstimator* self = (DelayEstimator*)handle;
  uint32_t binary_spectrum = 0;

  if (self == NULL) {
    return -1;
  }
  if (near_spectrum == NULL) {
    // Empty near end spectrum.
    return -1;
  }
  if (spectrum_size != self->spectrum_size) {
    // Data sizes don't match.
    return -1;
  }

  // Get binary spectrum.
  binary_spectrum = BinarySpectrumFloat(near_spectrum, self->mean_near_spectrum,
                                        &(self->near_spectrum_initialized));

  return WebRtc_ProcessBinarySpectrum(self->binary_handle, binary_spectrum);
}

int WebRtc_last_delay(void* handle) {
  DelayEstimator* self = (DelayEstimator*)handle;

  if (self == NULL) {
    return -1;
  }

  return WebRtc_binary_last_delay(self->binary_handle);
}

float WebRtc_last_delay_quality(void* handle) {
  DelayEstimator* self = (DelayEstimator*)handle;
  RTC_DCHECK(self);
  return WebRtc_binary_last_delay_quality(self->binary_handle);
}

}  // namespace webrtc
