/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/utility/block_mean_calculator.h"

#include "rtc_base/checks.h"

namespace webrtc {

BlockMeanCalculator::BlockMeanCalculator(size_t block_length)
    : block_length_(block_length), count_(0), sum_(0.0), mean_(0.0) {
  RTC_DCHECK(block_length_ != 0);
}

void BlockMeanCalculator::Reset() {
  Clear();
  mean_ = 0.0;
}

void BlockMeanCalculator::AddValue(float value) {
  sum_ += value;
  ++count_;
  if (count_ == block_length_) {
    mean_ = sum_ / block_length_;
    Clear();
  }
}

bool BlockMeanCalculator::EndOfBlock() const {
  return count_ == 0;
}

float BlockMeanCalculator::GetLatestMean() const {
  return mean_;
}

// Flush all samples added.
void BlockMeanCalculator::Clear() {
  count_ = 0;
  sum_ = 0.0;
}

}  // namespace webrtc
