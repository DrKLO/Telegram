/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_audio/sparse_fir_filter.h"

#include "rtc_base/checks.h"

namespace webrtc {

SparseFIRFilter::SparseFIRFilter(const float* nonzero_coeffs,
                                 size_t num_nonzero_coeffs,
                                 size_t sparsity,
                                 size_t offset)
    : sparsity_(sparsity),
      offset_(offset),
      nonzero_coeffs_(nonzero_coeffs, nonzero_coeffs + num_nonzero_coeffs),
      state_(sparsity_ * (num_nonzero_coeffs - 1) + offset_, 0.f) {
  RTC_CHECK_GE(num_nonzero_coeffs, 1);
  RTC_CHECK_GE(sparsity, 1);
}

SparseFIRFilter::~SparseFIRFilter() = default;

void SparseFIRFilter::Filter(const float* in, size_t length, float* out) {
  // Convolves the input signal |in| with the filter kernel |nonzero_coeffs_|
  // taking into account the previous state.
  for (size_t i = 0; i < length; ++i) {
    out[i] = 0.f;
    size_t j;
    for (j = 0; i >= j * sparsity_ + offset_ && j < nonzero_coeffs_.size();
         ++j) {
      out[i] += in[i - j * sparsity_ - offset_] * nonzero_coeffs_[j];
    }
    for (; j < nonzero_coeffs_.size(); ++j) {
      out[i] += state_[i + (nonzero_coeffs_.size() - j - 1) * sparsity_] *
                nonzero_coeffs_[j];
    }
  }

  // Update current state.
  if (state_.size() > 0u) {
    if (length >= state_.size()) {
      std::memcpy(&state_[0], &in[length - state_.size()],
                  state_.size() * sizeof(*in));
    } else {
      std::memmove(&state_[0], &state_[length],
                   (state_.size() - length) * sizeof(state_[0]));
      std::memcpy(&state_[state_.size() - length], in, length * sizeof(*in));
    }
  }
}

}  // namespace webrtc
