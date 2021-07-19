/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/echo_detector/circular_buffer.h"

#include <algorithm>

#include "rtc_base/checks.h"

namespace webrtc {

CircularBuffer::CircularBuffer(size_t size) : buffer_(size) {}
CircularBuffer::~CircularBuffer() = default;

void CircularBuffer::Push(float value) {
  buffer_[next_insertion_index_] = value;
  ++next_insertion_index_;
  next_insertion_index_ %= buffer_.size();
  RTC_DCHECK_LT(next_insertion_index_, buffer_.size());
  nr_elements_in_buffer_ = std::min(nr_elements_in_buffer_ + 1, buffer_.size());
  RTC_DCHECK_LE(nr_elements_in_buffer_, buffer_.size());
}

absl::optional<float> CircularBuffer::Pop() {
  if (nr_elements_in_buffer_ == 0) {
    return absl::nullopt;
  }
  const size_t index =
      (buffer_.size() + next_insertion_index_ - nr_elements_in_buffer_) %
      buffer_.size();
  RTC_DCHECK_LT(index, buffer_.size());
  --nr_elements_in_buffer_;
  return buffer_[index];
}

void CircularBuffer::Clear() {
  std::fill(buffer_.begin(), buffer_.end(), 0.f);
  next_insertion_index_ = 0;
  nr_elements_in_buffer_ = 0;
}

}  // namespace webrtc
