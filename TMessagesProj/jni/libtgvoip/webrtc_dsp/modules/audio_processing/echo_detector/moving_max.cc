/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/echo_detector/moving_max.h"

#include "rtc_base/checks.h"

namespace webrtc {
namespace {

// Parameter for controlling how fast the estimated maximum decays after the
// previous maximum is no longer valid. With a value of 0.99, the maximum will
// decay to 1% of its former value after 460 updates.
constexpr float kDecayFactor = 0.99f;

}  // namespace

MovingMax::MovingMax(size_t window_size) : window_size_(window_size) {
  RTC_DCHECK_GT(window_size, 0);
}

MovingMax::~MovingMax() {}

void MovingMax::Update(float value) {
  if (counter_ >= window_size_ - 1) {
    max_value_ *= kDecayFactor;
  } else {
    ++counter_;
  }
  if (value > max_value_) {
    max_value_ = value;
    counter_ = 0;
  }
}

float MovingMax::max() const {
  return max_value_;
}

void MovingMax::Clear() {
  max_value_ = 0.f;
  counter_ = 0;
}

}  // namespace webrtc
