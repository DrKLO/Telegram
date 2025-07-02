/*
 *  Copyright (c) 2023 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/bitrate_tracker.h"

#include "absl/types/optional.h"
#include "api/units/data_rate.h"
#include "api/units/timestamp.h"
#include "rtc_base/rate_statistics.h"

namespace webrtc {

BitrateTracker::BitrateTracker(TimeDelta max_window_size)
    : impl_(max_window_size.ms(), RateStatistics::kBpsScale) {}

absl::optional<DataRate> BitrateTracker::Rate(Timestamp now) const {
  if (absl::optional<int64_t> rate = impl_.Rate(now.ms())) {
    return DataRate::BitsPerSec(*rate);
  }
  return absl::nullopt;
}

bool BitrateTracker::SetWindowSize(TimeDelta window_size, Timestamp now) {
  return impl_.SetWindowSize(window_size.ms(), now.ms());
}

void BitrateTracker::Update(int64_t bytes, Timestamp now) {
  impl_.Update(bytes, now.ms());
}

}  // namespace webrtc
