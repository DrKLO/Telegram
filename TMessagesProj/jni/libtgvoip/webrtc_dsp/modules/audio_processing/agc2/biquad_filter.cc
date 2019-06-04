/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/biquad_filter.h"

#include <stddef.h>

namespace webrtc {

// Transposed direct form I implementation of a bi-quad filter applied to an
// input signal |x| to produce an output signal |y|.
void BiQuadFilter::Process(rtc::ArrayView<const float> x,
                           rtc::ArrayView<float> y) {
  for (size_t k = 0; k < x.size(); ++k) {
    // Use temporary variable for x[k] to allow in-place function call
    // (that x and y refer to the same array).
    const float tmp = x[k];
    y[k] = coefficients_.b[0] * tmp + coefficients_.b[1] * biquad_state_.b[0] +
           coefficients_.b[2] * biquad_state_.b[1] -
           coefficients_.a[0] * biquad_state_.a[0] -
           coefficients_.a[1] * biquad_state_.a[1];
    biquad_state_.b[1] = biquad_state_.b[0];
    biquad_state_.b[0] = tmp;
    biquad_state_.a[1] = biquad_state_.a[0];
    biquad_state_.a[0] = y[k];
  }
}

}  // namespace webrtc
