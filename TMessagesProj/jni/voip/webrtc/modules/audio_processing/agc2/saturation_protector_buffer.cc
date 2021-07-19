/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/saturation_protector_buffer.h"

#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_compare.h"

namespace webrtc {

SaturationProtectorBuffer::SaturationProtectorBuffer() = default;

SaturationProtectorBuffer::~SaturationProtectorBuffer() = default;

bool SaturationProtectorBuffer::operator==(
    const SaturationProtectorBuffer& b) const {
  RTC_DCHECK_LE(size_, buffer_.size());
  RTC_DCHECK_LE(b.size_, b.buffer_.size());
  if (size_ != b.size_) {
    return false;
  }
  for (int i = 0, i0 = FrontIndex(), i1 = b.FrontIndex(); i < size_;
       ++i, ++i0, ++i1) {
    if (buffer_[i0 % buffer_.size()] != b.buffer_[i1 % b.buffer_.size()]) {
      return false;
    }
  }
  return true;
}

int SaturationProtectorBuffer::Capacity() const {
  return buffer_.size();
}

int SaturationProtectorBuffer::Size() const {
  return size_;
}

void SaturationProtectorBuffer::Reset() {
  next_ = 0;
  size_ = 0;
}

void SaturationProtectorBuffer::PushBack(float v) {
  RTC_DCHECK_GE(next_, 0);
  RTC_DCHECK_GE(size_, 0);
  RTC_DCHECK_LT(next_, buffer_.size());
  RTC_DCHECK_LE(size_, buffer_.size());
  buffer_[next_++] = v;
  if (rtc::SafeEq(next_, buffer_.size())) {
    next_ = 0;
  }
  if (rtc::SafeLt(size_, buffer_.size())) {
    size_++;
  }
}

absl::optional<float> SaturationProtectorBuffer::Front() const {
  if (size_ == 0) {
    return absl::nullopt;
  }
  RTC_DCHECK_LT(FrontIndex(), buffer_.size());
  return buffer_[FrontIndex()];
}

int SaturationProtectorBuffer::FrontIndex() const {
  return rtc::SafeEq(size_, buffer_.size()) ? next_ : 0;
}

}  // namespace webrtc
