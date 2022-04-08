/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc/clipping_predictor_level_buffer.h"

#include <algorithm>
#include <cmath>

#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

bool ClippingPredictorLevelBuffer::Level::operator==(const Level& level) const {
  constexpr float kEpsilon = 1e-6f;
  return std::fabs(average - level.average) < kEpsilon &&
         std::fabs(max - level.max) < kEpsilon;
}

ClippingPredictorLevelBuffer::ClippingPredictorLevelBuffer(int capacity)
    : tail_(-1), size_(0), data_(std::max(1, capacity)) {
  if (capacity > kMaxCapacity) {
    RTC_LOG(LS_WARNING) << "[agc]: ClippingPredictorLevelBuffer exceeds the "
                        << "maximum allowed capacity. Capacity: " << capacity;
  }
  RTC_DCHECK(!data_.empty());
}

void ClippingPredictorLevelBuffer::Reset() {
  tail_ = -1;
  size_ = 0;
}

void ClippingPredictorLevelBuffer::Push(Level level) {
  ++tail_;
  if (tail_ == Capacity()) {
    tail_ = 0;
  }
  if (size_ < Capacity()) {
    size_++;
  }
  data_[tail_] = level;
}

// TODO(bugs.webrtc.org/12774): Optimize partial computation for long buffers.
absl::optional<ClippingPredictorLevelBuffer::Level>
ClippingPredictorLevelBuffer::ComputePartialMetrics(int delay,
                                                    int num_items) const {
  RTC_DCHECK_GE(delay, 0);
  RTC_DCHECK_LT(delay, Capacity());
  RTC_DCHECK_GT(num_items, 0);
  RTC_DCHECK_LE(num_items, Capacity());
  RTC_DCHECK_LE(delay + num_items, Capacity());
  if (delay + num_items > Size()) {
    return absl::nullopt;
  }
  float sum = 0.0f;
  float max = 0.0f;
  for (int i = 0; i < num_items && i < Size(); ++i) {
    int idx = tail_ - delay - i;
    if (idx < 0) {
      idx += Capacity();
    }
    sum += data_[idx].average;
    max = std::fmax(data_[idx].max, max);
  }
  return absl::optional<Level>({sum / static_cast<float>(num_items), max});
}

}  // namespace webrtc
