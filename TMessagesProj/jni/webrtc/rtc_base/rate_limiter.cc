/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/rate_limiter.h"

#include <limits>

#include "absl/types/optional.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {

RateLimiter::RateLimiter(Clock* clock, int64_t max_window_ms)
    : clock_(clock),
      current_rate_(max_window_ms, RateStatistics::kBpsScale),
      window_size_ms_(max_window_ms),
      max_rate_bps_(std::numeric_limits<uint32_t>::max()) {}

RateLimiter::~RateLimiter() {}

// Usage note: This class is intended be usable in a scenario where different
// threads may call each of the the different method. For instance, a network
// thread trying to send data calling TryUseRate(), the bandwidth estimator
// calling SetMaxRate() and a timed maintenance thread periodically updating
// the RTT.
bool RateLimiter::TryUseRate(size_t packet_size_bytes) {
  MutexLock lock(&lock_);
  int64_t now_ms = clock_->TimeInMilliseconds();
  absl::optional<uint32_t> current_rate = current_rate_.Rate(now_ms);
  if (current_rate) {
    // If there is a current rate, check if adding bytes would cause maximum
    // bitrate target to be exceeded. If there is NOT a valid current rate,
    // allow allocating rate even if target is exceeded. This prevents
    // problems
    // at very low rates, where for instance retransmissions would never be
    // allowed due to too high bitrate caused by a single packet.

    size_t bitrate_addition_bps =
        (packet_size_bytes * 8 * 1000) / window_size_ms_;
    if (*current_rate + bitrate_addition_bps > max_rate_bps_)
      return false;
  }

  current_rate_.Update(packet_size_bytes, now_ms);
  return true;
}

void RateLimiter::SetMaxRate(uint32_t max_rate_bps) {
  MutexLock lock(&lock_);
  max_rate_bps_ = max_rate_bps;
}

// Set the window size over which to measure the current bitrate.
// For retransmissions, this is typically the RTT.
bool RateLimiter::SetWindowSize(int64_t window_size_ms) {
  MutexLock lock(&lock_);
  window_size_ms_ = window_size_ms;
  return current_rate_.SetWindowSize(window_size_ms,
                                     clock_->TimeInMilliseconds());
}

}  // namespace webrtc
