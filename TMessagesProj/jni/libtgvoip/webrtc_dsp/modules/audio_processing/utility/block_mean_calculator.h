/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_UTILITY_BLOCK_MEAN_CALCULATOR_H_
#define MODULES_AUDIO_PROCESSING_UTILITY_BLOCK_MEAN_CALCULATOR_H_

#include <stddef.h>

#include "rtc_base/constructormagic.h"

namespace webrtc {

// BlockMeanCalculator calculates the mean of a block of values. Values are
// added one after another, and the mean is updated at the end of every block.
class BlockMeanCalculator {
 public:
  explicit BlockMeanCalculator(size_t block_length);

  // Reset.
  void Reset();

  // Add one value to the sequence.
  void AddValue(float value);

  // Return whether the latest added value was at the end of a block.
  bool EndOfBlock() const;

  // Return the latest mean.
  float GetLatestMean() const;

 private:
  // Clear all values added.
  void Clear();

  const size_t block_length_;
  size_t count_;
  float sum_;
  float mean_;

  RTC_DISALLOW_COPY_AND_ASSIGN(BlockMeanCalculator);
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_UTILITY_BLOCK_MEAN_CALCULATOR_H_
