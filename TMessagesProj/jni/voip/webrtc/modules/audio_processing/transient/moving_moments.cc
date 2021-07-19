/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/transient/moving_moments.h"

#include <algorithm>

#include "rtc_base/checks.h"

namespace webrtc {

MovingMoments::MovingMoments(size_t length)
    : length_(length), queue_(), sum_(0.0), sum_of_squares_(0.0) {
  RTC_DCHECK_GT(length, 0);
  for (size_t i = 0; i < length; ++i) {
    queue_.push(0.0);
  }
}

MovingMoments::~MovingMoments() {}

void MovingMoments::CalculateMoments(const float* in,
                                     size_t in_length,
                                     float* first,
                                     float* second) {
  RTC_DCHECK(in);
  RTC_DCHECK_GT(in_length, 0);
  RTC_DCHECK(first);
  RTC_DCHECK(second);

  for (size_t i = 0; i < in_length; ++i) {
    const float old_value = queue_.front();
    queue_.pop();
    queue_.push(in[i]);

    sum_ += in[i] - old_value;
    sum_of_squares_ += in[i] * in[i] - old_value * old_value;
    first[i] = sum_ / length_;
    second[i] = std::max(0.f, sum_of_squares_ / length_);
  }
}

}  // namespace webrtc
