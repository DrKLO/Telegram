/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef COMMON_AUDIO_SPARSE_FIR_FILTER_H_
#define COMMON_AUDIO_SPARSE_FIR_FILTER_H_

#include <cstring>
#include <vector>

#include "rtc_base/constructormagic.h"

namespace webrtc {

// A Finite Impulse Response filter implementation which takes advantage of a
// sparse structure with uniformly distributed non-zero coefficients.
class SparseFIRFilter final {
 public:
  // |num_nonzero_coeffs| is the number of non-zero coefficients,
  // |nonzero_coeffs|. They are assumed to be uniformly distributed every
  // |sparsity| samples and with an initial |offset|. The rest of the filter
  // coefficients will be assumed zeros. For example, with sparsity = 3, and
  // offset = 1 the filter coefficients will be:
  // B = [0 coeffs[0] 0 0 coeffs[1] 0 0 coeffs[2] ... ]
  // All initial state values will be zeros.
  SparseFIRFilter(const float* nonzero_coeffs,
                  size_t num_nonzero_coeffs,
                  size_t sparsity,
                  size_t offset);
  ~SparseFIRFilter();

  // Filters the |in| data supplied.
  // |out| must be previously allocated and it must be at least of |length|.
  void Filter(const float* in, size_t length, float* out);

 private:
  const size_t sparsity_;
  const size_t offset_;
  const std::vector<float> nonzero_coeffs_;
  std::vector<float> state_;

  RTC_DISALLOW_COPY_AND_ASSIGN(SparseFIRFilter);
};

}  // namespace webrtc

#endif  // COMMON_AUDIO_SPARSE_FIR_FILTER_H_
