/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/cross_correlation.h"

#include <cstdlib>
#include <limits>

#include "common_audio/signal_processing/include/signal_processing_library.h"

namespace webrtc {

// This function decides the overflow-protecting scaling and calls
// WebRtcSpl_CrossCorrelation.
int CrossCorrelationWithAutoShift(const int16_t* sequence_1,
                                  const int16_t* sequence_2,
                                  size_t sequence_1_length,
                                  size_t cross_correlation_length,
                                  int cross_correlation_step,
                                  int32_t* cross_correlation) {
  // Find the element that has the maximum absolute value of sequence_1 and 2.
  // Note that these values may be negative.
  const int16_t max_1 =
      WebRtcSpl_MaxAbsElementW16(sequence_1, sequence_1_length);
  const int sequence_2_shift =
      cross_correlation_step * (static_cast<int>(cross_correlation_length) - 1);
  const int16_t* sequence_2_start =
      sequence_2_shift >= 0 ? sequence_2 : sequence_2 + sequence_2_shift;
  const size_t sequence_2_length =
      sequence_1_length + std::abs(sequence_2_shift);
  const int16_t max_2 =
      WebRtcSpl_MaxAbsElementW16(sequence_2_start, sequence_2_length);

  // In order to avoid overflow when computing the sum we should scale the
  // samples so that (in_vector_length * max_1 * max_2) will not overflow.
  const int64_t max_value =
      abs(max_1 * max_2) * static_cast<int64_t>(sequence_1_length);
  const int32_t factor = max_value >> 31;
  const int scaling = factor == 0 ? 0 : 31 - WebRtcSpl_NormW32(factor);

  WebRtcSpl_CrossCorrelation(cross_correlation, sequence_1, sequence_2,
                             sequence_1_length, cross_correlation_length,
                             scaling, cross_correlation_step);

  return scaling;
}

}  // namespace webrtc
