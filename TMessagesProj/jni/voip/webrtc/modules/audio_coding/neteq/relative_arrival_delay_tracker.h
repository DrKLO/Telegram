/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_CODING_NETEQ_RELATIVE_ARRIVAL_DELAY_TRACKER_H_
#define MODULES_AUDIO_CODING_NETEQ_RELATIVE_ARRIVAL_DELAY_TRACKER_H_

#include <deque>
#include <memory>

#include "absl/types/optional.h"
#include "api/neteq/tick_timer.h"

namespace webrtc {

class RelativeArrivalDelayTracker {
 public:
  RelativeArrivalDelayTracker(const TickTimer* tick_timer, int max_history_ms)
      : tick_timer_(tick_timer), max_history_ms_(max_history_ms) {}

  absl::optional<int> Update(uint32_t timestamp, int sample_rate_hz);

  void Reset();

  absl::optional<uint32_t> newest_timestamp() const {
    return newest_timestamp_;
  }

 private:
  // Updates `delay_history_`.
  void UpdateDelayHistory(int iat_delay_ms,
                          uint32_t timestamp,
                          int sample_rate_hz);

  // Calculate relative packet arrival delay from `delay_history_`.
  int CalculateRelativePacketArrivalDelay() const;

  const TickTimer* tick_timer_;
  const int max_history_ms_;

  struct PacketDelay {
    int iat_delay_ms;
    uint32_t timestamp;
  };
  std::deque<PacketDelay> delay_history_;

  absl::optional<uint32_t> newest_timestamp_;
  absl::optional<uint32_t> last_timestamp_;

  std::unique_ptr<TickTimer::Stopwatch>
      packet_iat_stopwatch_;  // Time elapsed since last packet.
};

}  // namespace webrtc
#endif  // MODULES_AUDIO_CODING_NETEQ_RELATIVE_ARRIVAL_DELAY_TRACKER_H_
