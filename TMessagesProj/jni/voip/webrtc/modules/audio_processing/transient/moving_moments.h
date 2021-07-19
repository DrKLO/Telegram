/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_TRANSIENT_MOVING_MOMENTS_H_
#define MODULES_AUDIO_PROCESSING_TRANSIENT_MOVING_MOMENTS_H_

#include <stddef.h>

#include <queue>

namespace webrtc {

// Calculates the first and second moments for each value of a buffer taking
// into account a given number of previous values.
// It preserves its state, so it can be multiple-called.
// TODO(chadan): Implement a function that takes a buffer of first moments and a
// buffer of second moments; and calculates the variances. When needed.
// TODO(chadan): Add functionality to update with a buffer but only output are
// the last values of the moments. When needed.
class MovingMoments {
 public:
  // Creates a Moving Moments object, that uses the last |length| values
  // (including the new value introduced in every new calculation).
  explicit MovingMoments(size_t length);
  ~MovingMoments();

  // Calculates the new values using |in|. Results will be in the out buffers.
  // |first| and |second| must be allocated with at least |in_length|.
  void CalculateMoments(const float* in,
                        size_t in_length,
                        float* first,
                        float* second);

 private:
  size_t length_;
  // A queue holding the |length_| latest input values.
  std::queue<float> queue_;
  // Sum of the values of the queue.
  float sum_;
  // Sum of the squares of the values of the queue.
  float sum_of_squares_;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_TRANSIENT_MOVING_MOMENTS_H_
