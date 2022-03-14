/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_TRANSIENT_DYADIC_DECIMATOR_H_
#define MODULES_AUDIO_PROCESSING_TRANSIENT_DYADIC_DECIMATOR_H_

#include <cstdlib>

// Provides a set of static methods to perform dyadic decimations.

namespace webrtc {

// Returns the proper length of the output buffer that you should use for the
// given `in_length` and decimation `odd_sequence`.
// Return -1 on error.
inline size_t GetOutLengthToDyadicDecimate(size_t in_length,
                                           bool odd_sequence) {
  size_t out_length = in_length / 2;

  if (in_length % 2 == 1 && !odd_sequence) {
    ++out_length;
  }

  return out_length;
}

// Performs a dyadic decimation: removes every odd/even member of a sequence
// halving its overall length.
// Arguments:
//    in: array of `in_length`.
//    odd_sequence: If false, the odd members will be removed (1, 3, 5, ...);
//                  if true, the even members will be removed (0, 2, 4, ...).
//    out: array of `out_length`. `out_length` must be large enough to
//         hold the decimated output. The necessary length can be provided by
//         GetOutLengthToDyadicDecimate().
//         Must be previously allocated.
// Returns the number of output samples, -1 on error.
template <typename T>
static size_t DyadicDecimate(const T* in,
                             size_t in_length,
                             bool odd_sequence,
                             T* out,
                             size_t out_length) {
  size_t half_length = GetOutLengthToDyadicDecimate(in_length, odd_sequence);

  if (!in || !out || in_length <= 0 || out_length < half_length) {
    return 0;
  }

  size_t output_samples = 0;
  size_t index_adjustment = odd_sequence ? 1 : 0;
  for (output_samples = 0; output_samples < half_length; ++output_samples) {
    out[output_samples] = in[output_samples * 2 + index_adjustment];
  }

  return output_samples;
}

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_TRANSIENT_DYADIC_DECIMATOR_H_
