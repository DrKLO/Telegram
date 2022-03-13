/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/codec_timer.h"

#include <cstdint>

namespace webrtc {

namespace {

// The first kIgnoredSampleCount samples will be ignored.
const int kIgnoredSampleCount = 5;
// Return the `kPercentile` value in RequiredDecodeTimeMs().
const float kPercentile = 0.95f;
// The window size in ms.
const int64_t kTimeLimitMs = 10000;

}  // anonymous namespace

VCMCodecTimer::VCMCodecTimer()
    : ignored_sample_count_(0), filter_(kPercentile) {}
VCMCodecTimer::~VCMCodecTimer() = default;

void VCMCodecTimer::AddTiming(int64_t decode_time_ms, int64_t now_ms) {
  // Ignore the first `kIgnoredSampleCount` samples.
  if (ignored_sample_count_ < kIgnoredSampleCount) {
    ++ignored_sample_count_;
    return;
  }

  // Insert new decode time value.
  filter_.Insert(decode_time_ms);
  history_.emplace(decode_time_ms, now_ms);

  // Pop old decode time values.
  while (!history_.empty() &&
         now_ms - history_.front().sample_time_ms > kTimeLimitMs) {
    filter_.Erase(history_.front().decode_time_ms);
    history_.pop();
  }
}

// Get the 95th percentile observed decode time within a time window.
int64_t VCMCodecTimer::RequiredDecodeTimeMs() const {
  return filter_.GetPercentileValue();
}

VCMCodecTimer::Sample::Sample(int64_t decode_time_ms, int64_t sample_time_ms)
    : decode_time_ms(decode_time_ms), sample_time_ms(sample_time_ms) {}

}  // namespace webrtc
