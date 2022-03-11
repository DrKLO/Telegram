/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/relative_arrival_delay_tracker.h"

#include <algorithm>

#include "modules/include/module_common_types_public.h"

namespace webrtc {

absl::optional<int> RelativeArrivalDelayTracker::Update(uint32_t timestamp,
                                                        int sample_rate_hz) {
  if (sample_rate_hz <= 0) {
    return absl::nullopt;
  }
  if (!last_timestamp_) {
    // Restart relative delay esimation from this packet.
    delay_history_.clear();
    packet_iat_stopwatch_ = tick_timer_->GetNewStopwatch();
    newest_timestamp_ = timestamp;
    last_timestamp_ = timestamp;
    return absl::nullopt;
  }

  const int expected_iat_ms =
      1000ll * static_cast<int32_t>(timestamp - *last_timestamp_) /
      sample_rate_hz;
  const int iat_ms = packet_iat_stopwatch_->ElapsedMs();
  const int iat_delay_ms = iat_ms - expected_iat_ms;
  UpdateDelayHistory(iat_delay_ms, timestamp, sample_rate_hz);
  int relative_delay = CalculateRelativePacketArrivalDelay();

  packet_iat_stopwatch_ = tick_timer_->GetNewStopwatch();
  last_timestamp_ = timestamp;
  if (IsNewerTimestamp(timestamp, *newest_timestamp_)) {
    newest_timestamp_ = timestamp;
  }

  return relative_delay;
}

void RelativeArrivalDelayTracker::Reset() {
  delay_history_.clear();
  packet_iat_stopwatch_.reset();
  newest_timestamp_ = absl::nullopt;
  last_timestamp_ = absl::nullopt;
}

void RelativeArrivalDelayTracker::UpdateDelayHistory(int iat_delay_ms,
                                                     uint32_t timestamp,
                                                     int sample_rate_hz) {
  PacketDelay delay;
  delay.iat_delay_ms = iat_delay_ms;
  delay.timestamp = timestamp;
  delay_history_.push_back(delay);
  while (static_cast<int32_t>(timestamp - delay_history_.front().timestamp) >
         max_history_ms_ * sample_rate_hz / 1000) {
    delay_history_.pop_front();
  }
}

int RelativeArrivalDelayTracker::CalculateRelativePacketArrivalDelay() const {
  // This effectively calculates arrival delay of a packet relative to the
  // packet preceding the history window. If the arrival delay ever becomes
  // smaller than zero, it means the reference packet is invalid, and we
  // move the reference.
  int relative_delay = 0;
  for (const PacketDelay& delay : delay_history_) {
    relative_delay += delay.iat_delay_ms;
    relative_delay = std::max(relative_delay, 0);
  }
  return relative_delay;
}

}  // namespace webrtc
