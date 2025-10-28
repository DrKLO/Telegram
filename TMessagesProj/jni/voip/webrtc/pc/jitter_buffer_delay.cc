/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/jitter_buffer_delay.h"

#include "api/sequence_checker.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/numerics/safe_minmax.h"

namespace {
constexpr int kDefaultDelay = 0;
constexpr int kMaximumDelayMs = 10000;
}  // namespace

namespace webrtc {

void JitterBufferDelay::Set(absl::optional<double> delay_seconds) {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  cached_delay_seconds_ = delay_seconds;
}

int JitterBufferDelay::GetMs() const {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  return rtc::SafeClamp(
      rtc::saturated_cast<int>(cached_delay_seconds_.value_or(kDefaultDelay) *
                               1000),
      0, kMaximumDelayMs);
}

}  // namespace webrtc
