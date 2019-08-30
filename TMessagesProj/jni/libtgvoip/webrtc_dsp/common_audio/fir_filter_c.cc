/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_audio/fir_filter_c.h"

#include <string.h>
#include <memory>

#include "rtc_base/checks.h"

namespace webrtc {

FIRFilterC::~FIRFilterC() {}

FIRFilterC::FIRFilterC(const float* coefficients, size_t coefficients_length)
    : coefficients_length_(coefficients_length),
      state_length_(coefficients_length - 1),
      coefficients_(new float[coefficients_length_]),
      state_(new float[state_length_]) {
  for (size_t i = 0; i < coefficients_length_; ++i) {
    coefficients_[i] = coefficients[coefficients_length_ - i - 1];
  }
  memset(state_.get(), 0, state_length_ * sizeof(state_[0]));
}

void FIRFilterC::Filter(const float* in, size_t length, float* out) {
  RTC_DCHECK_GT(length, 0);

  // Convolves the input signal |in| with the filter kernel |coefficients_|
  // taking into account the previous state.
  for (size_t i = 0; i < length; ++i) {
    out[i] = 0.f;
    size_t j;
    for (j = 0; state_length_ > i && j < state_length_ - i; ++j) {
      out[i] += state_[i + j] * coefficients_[j];
    }
    for (; j < coefficients_length_; ++j) {
      out[i] += in[j + i - state_length_] * coefficients_[j];
    }
  }

  // Update current state.
  if (length >= state_length_) {
    memcpy(state_.get(), &in[length - state_length_],
           state_length_ * sizeof(*in));
  } else {
    memmove(state_.get(), &state_[length],
            (state_length_ - length) * sizeof(state_[0]));
    memcpy(&state_[state_length_ - length], in, length * sizeof(*in));
  }
}

}  // namespace webrtc
