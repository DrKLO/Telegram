/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/histogram.h"

#include <algorithm>

#include "rtc_base/checks.h"

namespace webrtc {
namespace video_coding {
Histogram::Histogram(size_t num_buckets, size_t max_num_values) {
  RTC_DCHECK_GT(num_buckets, 0);
  RTC_DCHECK_GT(max_num_values, 0);
  buckets_.resize(num_buckets);
  values_.reserve(max_num_values);
  index_ = 0;
}

void Histogram::Add(size_t value) {
  value = std::min<size_t>(value, buckets_.size() - 1);
  if (index_ < values_.size()) {
    --buckets_[values_[index_]];
    RTC_DCHECK_LT(values_[index_], buckets_.size());
    values_[index_] = value;
  } else {
    values_.emplace_back(value);
  }

  ++buckets_[value];
  index_ = (index_ + 1) % values_.capacity();
}

size_t Histogram::InverseCdf(float probability) const {
  RTC_DCHECK_GE(probability, 0.f);
  RTC_DCHECK_LE(probability, 1.f);
  RTC_DCHECK_GT(values_.size(), 0ul);

  size_t bucket = 0;
  float accumulated_probability = 0;
  while (accumulated_probability < probability && bucket < buckets_.size()) {
    accumulated_probability +=
        static_cast<float>(buckets_[bucket]) / values_.size();
    ++bucket;
  }
  return bucket;
}

size_t Histogram::NumValues() const {
  return values_.size();
}

}  // namespace video_coding
}  // namespace webrtc
