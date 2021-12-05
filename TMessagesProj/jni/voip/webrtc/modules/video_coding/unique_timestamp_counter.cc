/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/unique_timestamp_counter.h"

#include <cstdint>
#include <memory>
#include <set>

namespace webrtc {
namespace {

constexpr int kMaxHistory = 1000;

}  // namespace

UniqueTimestampCounter::UniqueTimestampCounter()
    : latest_(std::make_unique<uint32_t[]>(kMaxHistory)) {}

void UniqueTimestampCounter::Add(uint32_t value) {
  if (value == last_ || !search_index_.insert(value).second) {
    // Already known.
    return;
  }
  int index = unique_seen_ % kMaxHistory;
  if (unique_seen_ >= kMaxHistory) {
    search_index_.erase(latest_[index]);
  }
  latest_[index] = value;
  last_ = value;
  ++unique_seen_;
}

}  // namespace webrtc
